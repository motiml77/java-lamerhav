/**
 * gemini-proxy — Cloudflare Worker
 * ================================
 * שרת ה-AI שמאחורי gemini-proxy.motiml77.workers.dev. מקבל בקשות מ-app.html
 * (ראי AIService ב-app.html, סביב שורה 1918), קורא ל-Gemini, ומחזיר תשובה
 * במבנה שהלקוח כבר יודע לקרוא.
 *
 * למה נכתב מחדש: הריצה מול ה-Worker הקיים החזירה "כל המודלים נכשלו" בכל
 * קריאה (גם gradeExam וגם checkHomework), ו-"Invalid action" על action:'chat'
 * — כלומר הפעולה שמפעילה את "הרצה" בעמוד הלמידה ואת עוזרת ה-AI מעולם לא
 * הייתה נתמכת. אין גישה לקוד המקור הקודם או לחשבון ה-Cloudflare — זה קובץ
 * חלופי מלא, מוכן להדבקה.
 *
 * החלטה מכוונת: משתמש ב-REST endpoint הקלאסי (v1beta/models/{model}:generateContent)
 * ולא ב-API "interactions" החדש יותר של גוגל. הקלאסי מתועד ונבדק היטב ואי
 * אפשר לבדוק כאן מול Gemini אמיתי לפני הפריסה (אין מפתח API בסביבה הזו) —
 * ולכן זו הבחירה הבטוחה. מה שכן עדכני: המודל עצמו.
 *
 * חשוב: הפרומפטים המפורטים (BASE_SYSTEM_PROMPT, DEFAULT_GRADING_PROMPT,
 * DEFAULT_HOMEWORK_PROMPT, DEFAULT_AUTOMATA_PROMPT — כולם ב-app.html סביב
 * שורה 560) כבר בנויים היטב בצד הלקוח ונשלחים מוכנים ב-gradingInstructions
 * וב-homeworkPrompt. ה-Worker לא בונה אותם מחדש — הוא רק מעביר אותם ל-Gemini
 * ומפרש את התשובה. הפרומפט היחיד שנבנה כאן מאפס הוא זה של action:'chat',
 * כי ללקוח אין לו מקבילה.
 */

// ---------------------------------------------------------------------------
// מודלים — מהעדכני לישן, כולם בחינם. גם gemini-3.7-flash וגם 2.5-flash
// דורשים רק מפתח API רגיל, בלי כרטיס אשראי מחובר לפרויקט.
// אם המודל הראשון נכשל (עומס, שינוי זמני בזמינות) — עוברים לבא בתור,
// ולא מחזירים שגיאה לתלמידה רק כי מודל ספציפי אחד תקוע.
// ---------------------------------------------------------------------------
const MODELS = ['gemini-3.7-flash', 'gemini-2.5-flash', 'gemini-2.0-flash'];

// דומיינים שמורשים לקרוא ל-Worker — כל האתרים של הפרויקט הזה (חי, דמו, תצוגות
// מקדימות) ופיתוח מקומי. בלי זה, שינוי בחוקי ה-CORS יעצור את כל האתרים בבת אחת.
const ALLOWED_ORIGIN_PATTERNS = [
  /^https:\/\/java-lamerhav(-[a-z0-9-]+)?\.vercel\.app$/,
  /^https:\/\/java-manager-preview(-[a-z0-9-]+)?\.vercel\.app$/,
  /^https:\/\/java-bagrut-example(-[a-z0-9-]+)?\.vercel\.app$/,
  /^https:\/\/java-example-two(-[a-z0-9-]+)?\.vercel\.app$/,
  /^https:\/\/java-lamerhav-promo(-[a-z0-9-]+)?\.vercel\.app$/,
  /^http:\/\/localhost(:\d+)?$/,
];

function corsHeaders(origin) {
  const allowed = origin && ALLOWED_ORIGIN_PATTERNS.some((re) => re.test(origin));
  return {
    'Access-Control-Allow-Origin': allowed ? origin : 'null',
    'Access-Control-Allow-Methods': 'POST, OPTIONS',
    'Access-Control-Allow-Headers': 'Content-Type',
    Vary: 'Origin',
  };
}

function json(data, status, origin) {
  return new Response(JSON.stringify(data), {
    status,
    headers: { 'Content-Type': 'application/json; charset=utf-8', ...corsHeaders(origin) },
  });
}

// ---------------------------------------------------------------------------
// קריאה ל-Gemini עם fallback בין מודלים. contents הוא כבר במבנה REST הרשמי
// (מערך {role, parts}). systemInstruction — טקסט חופשי, לפי חוזה ה-API.
// ---------------------------------------------------------------------------
async function callGemini(apiKey, systemInstruction, contents, { jsonMode = false, temperature = 0.3 } = {}) {
  const errors = [];
  for (const model of MODELS) {
    const url = `https://generativelanguage.googleapis.com/v1beta/models/${model}:generateContent`;
    const body = {
      contents,
      systemInstruction: { parts: [{ text: systemInstruction }] },
      generationConfig: {
        temperature,
        ...(jsonMode ? { responseMimeType: 'application/json' } : {}),
      },
      safetySettings: [
        // תוכן חינוכי לנוער — לא מגבילים מעבר לברירת המחדל של גוגל.
      ],
    };
    try {
      const res = await fetch(url, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', 'x-goog-api-key': apiKey },
        body: JSON.stringify(body),
      });
      const data = await res.json().catch(() => null);
      if (!res.ok) {
        errors.push(`${model}: HTTP ${res.status} — ${data?.error?.message || 'unknown'}`);
        continue; // המודל הזה נכשל — עוברים לבא בתור
      }
      const text = data?.candidates?.[0]?.content?.parts?.map((p) => p.text || '').join('') || '';
      if (!text) {
        errors.push(`${model}: תשובה ריקה (ייתכן חסימת בטיחות — finishReason=${data?.candidates?.[0]?.finishReason})`);
        continue;
      }
      return { text, modelUsed: model };
    } catch (e) {
      errors.push(`${model}: ${String(e && e.message ? e.message : e)}`);
    }
  }
  throw new Error('כל המודלים נכשלו: ' + errors.join(' | '));
}

// Gemini לפעמים עוטף JSON בגדר markdown (```json ... ```) למרות responseMimeType.
// מנקים לפני הפענוח, ונופלים בחזרה למבנה בטוח אם הפענוח עדיין נכשל.
function parseJsonLoose(text, fallback) {
  const cleaned = String(text || '')
    .trim()
    .replace(/^```(?:json)?\s*/i, '')
    .replace(/```\s*$/i, '');
  try {
    return JSON.parse(cleaned);
  } catch (e) {
    return { ...fallback, feedback: 'לא הצלחנו לפענח את תשובת המודל. נסי שוב.', _raw: cleaned.slice(0, 500) };
  }
}

function imagePart(dataUri) {
  if (!dataUri || typeof dataUri !== 'string' || !dataUri.startsWith('data:')) return null;
  const m = dataUri.match(/^data:([^;]+);base64,(.+)$/s);
  if (!m) return null;
  return { inlineData: { mimeType: m[1], data: m[2] } };
}

// ---------------------------------------------------------------------------
// action: gradeExam — בדיקת שאלת מבחן ע"י המורה.
// gradingInstructions כבר מכיל את כל הפרומפט המלא (BASE_SYSTEM_PROMPT +
// הנחיות המבחן + דגשי השאלה) — הוא ה-systemInstruction. אנחנו רק שולחים
// אותו יחד עם קוד התלמידה, ומעצבים את התשובה לשמות השדות שהלקוח קורא
// (aiScore, maxPoints — ראי app.html, חיפוש "aiFeedback\.").
// ---------------------------------------------------------------------------
async function handleGradeExam(apiKey, gradingData) {
  const { code, questionText, questionImage, gradingInstructions, questionPoints } = gradingData || {};
  const maxPoints = Number(questionPoints) > 0 ? Number(questionPoints) : 10;

  const parts = [
    { text: `# שאלת המבחן\n${questionText || '(לא סופקה כותרת שאלה)'}\n\n# קוד התלמידה להערכה\n\`\`\`java\n${code || ''}\n\`\`\`` },
  ];
  const img = imagePart(questionImage);
  if (img) parts.unshift({ text: '# תמונת השאלה מצורפת:' }, img);

  const { text, modelUsed } = await callGemini(
    apiKey,
    gradingInstructions || 'Grade this Java answer strictly but fairly. Respond in Hebrew JSON: {score, grade, feedback, errors, suggestions, encouragement}.',
    [{ role: 'user', parts }],
    { jsonMode: true, temperature: 0.2 },
  );

  const parsed = parseJsonLoose(text, { score: 0, grade: 'טעון בדיקה חוזרת', feedback: '', errors: [], suggestions: [], encouragement: '' });
  const rawScore = Number(parsed.score);
  const aiScore = Number.isFinite(rawScore) ? Math.max(0, Math.min(maxPoints, rawScore)) : 0;

  return {
    aiScore,
    maxPoints,
    grade: parsed.grade || '',
    feedback: parsed.feedback || '',
    errors: Array.isArray(parsed.errors) ? parsed.errors : [],
    suggestions: Array.isArray(parsed.suggestions) ? parsed.suggestions : [],
    encouragement: parsed.encouragement || '',
    modelUsed,
  };
}

// ---------------------------------------------------------------------------
// action: checkHomework — תרגול עצמי / שיעורי בית, ללא מעורבות מורה.
// homeworkPrompt כבר מכיל את הפרומפט המלא (Java או אוטומטים, לפי הסוג) —
// שוב, זה ה-systemInstruction. שם השדות בתשובה חייב להתאים בדיוק למה
// ש-AIService.checkHomework קורא (ראי app.html:1938-1952).
// ---------------------------------------------------------------------------
async function handleCheckHomework(apiKey, payload) {
  const { code, questionText, questionImage, homeworkPrompt, questionPoints } = payload || {};
  const maxPoints = Number(questionPoints) > 0 ? Number(questionPoints) : 10;

  const parts = [
    { text: `# השאלה\n${questionText || '(לא סופקה כותרת שאלה)'}\n\n# תשובת התלמידה\n\`\`\`\n${code || ''}\n\`\`\`\n\nניקוד מקסימלי לשאלה זו: ${maxPoints}` },
  ];
  const img = imagePart(questionImage);
  if (img) parts.unshift({ text: '# תמונת השאלה מצורפת:' }, img);

  const { text, modelUsed } = await callGemini(
    apiKey,
    homeworkPrompt || 'Check this Java homework answer. Respond in Hebrew JSON: {correct, score, feedback, errors, suggestions, encouragement}.',
    [{ role: 'user', parts }],
    { jsonMode: true, temperature: 0.2 },
  );

  const parsed = parseJsonLoose(text, { correct: false, score: 0, feedback: '', errors: [], suggestions: [], encouragement: '' });
  const rawScore = Number(parsed.score);
  const score = Number.isFinite(rawScore) ? Math.max(0, Math.min(maxPoints, rawScore)) : 0;

  return {
    correct: !!parsed.correct,
    score,
    feedback: parsed.feedback || '',
    errors: Array.isArray(parsed.errors) ? parsed.errors : [],
    suggestions: Array.isArray(parsed.suggestions) ? parsed.suggestions : [],
    encouragement: parsed.encouragement || '',
    languageExamples: Array.isArray(parsed.languageExamples) ? parsed.languageExamples : [],
    examples: parsed.examples || null,
    modelUsed,
  };
}

// ---------------------------------------------------------------------------
// action: chat — הפעולה שהייתה חסרה לגמרי ("Invalid action"). משרתת שני
// שימושים שונים ב-app.html, מובחנים לפי context:
//   1. context.mode === 'run-code'  → TryIt (app.html:2340) — "מדמה מהדר":
//      חייב להחזיר אך ורק את הפלט המדויק של התוכנית, בלי שום טקסט נוסף.
//   2. אחרת → עוזרת ה-AI לתלמידה (app.html:7060) — מדריכה בלי לתת פתרון מלא.
// שני המצבים דורשים פרומפט שונה לגמרי, ולכן נבנה כאן מאפס — ללקוח אין
// גרסה מוכנה משלו לפעולה הזו.
// ---------------------------------------------------------------------------
const RUN_CODE_SYSTEM_PROMPT = `את מדמה מהדר ומריצה של Java. תפקידך היחיד: להריץ מנטלית את קוד ה-Java שיישלח, ולהחזיר את הפלט המדויק שהתוכנית הייתה מדפיסה — שורה אחר שורה, בדיוק כפי שהיה נראה בטרמינל.

חוקים מחייבים:
- אל תסבירי, אל תוסיפי הערות, אל תשתמשי בסימוני markdown. רק הפלט הגולמי.
- אם יש שגיאת קומפילציה — שורה אחת בלבד: "❌ שגיאה: <הסבר קצר וברור בעברית מה לתקן>".
- אם יש שגיאת ריצה (למשל חלוקה באפס, אינדקס מחוץ לתחום) — הדפיסי את מה שהודפס לפני השגיאה, ואז שורת שגיאה תואמת ל-stack trace של Java.
- דייקי לחלוטין בעיצוב הפלט של println/print, כולל רווחים ושורות ריקות.`;

const CHAT_HELPER_SYSTEM_PROMPT = `את עוזרת AI סבלנית וידידותית לתלמידות תיכון (י"א-י"ב) שלומדות Java לקראת בגרות במדעי המחשב.

התפקיד שלך: להדריך, לא לפתור במקומן.
- מותר: להסביר מושגים, לשאול שאלות מכוונות, להצביע על היכן משהו לא נכון בלי לתת את התיקון המלא, לתת דוגמה קטנה ושונה מהשאלה עצמה.
- אסור: לכתוב עבורה את הפתרון המלא לשאלה שהיא עובדת עליה כרגע. אם היא מבקשת "תני לי את הקוד" — הסבירי בעדינות שהמטרה שהיא תגיע לפתרון בעצמה, והציעי צעד קטן הבא במקום.
- אם היא מציגה קוד שלה עם שגיאה: אל תתקני ישירות — שאלי "מה קורה לדעתך בשורה X כש-Y?" או דומה, כדי שהיא תגלה בעצמה.
- טון: תמציתי, חם, בעברית פשוטה. בלי הרצאות ארוכות.

ההקשר של השאלה שהתלמידה עובדת עליה (למידע בלבד — אל תפתרי אותה):
{{CONTEXT}}`;

async function handleChat(apiKey, messages, context) {
  const isRunCode = context && context.mode === 'run-code';
  const contextBlock = isRunCode
    ? ''
    : [
        context?.questionType === 'automata' ? `סוג שאלה: אוטומטים (${context?.automataType || 'DFA'})` : 'סוג שאלה: קוד Java',
        context?.questionText ? `טקסט השאלה: ${context.questionText}` : '',
        context?.currentCode ? `הקוד הנוכחי של התלמידה:\n\`\`\`\n${context.currentCode}\n\`\`\`` : '',
      ].filter(Boolean).join('\n\n');

  const systemInstruction = isRunCode
    ? RUN_CODE_SYSTEM_PROMPT
    : CHAT_HELPER_SYSTEM_PROMPT.replace('{{CONTEXT}}', contextBlock || '(אין הקשר נוסף)');

  // ממירים היסטוריית {role:'user'|'assistant', content} לפורמט Gemini
  // (role:'model' במקום 'assistant', ו-parts:[{text}] במקום content).
  const contents = (Array.isArray(messages) ? messages : [])
    .filter((m) => m && m.content)
    .map((m) => ({ role: m.role === 'assistant' ? 'model' : 'user', parts: [{ text: String(m.content) }] }));

  if (!contents.length) throw new Error('אין הודעות לשליחה');

  const { text } = await callGemini(apiKey, systemInstruction, contents, {
    jsonMode: false,
    temperature: isRunCode ? 0 : 0.5, // הרצת קוד חייבת להיות דטרמיניסטית ככל האפשר
  });
  return { response: text };
}

// ---------------------------------------------------------------------------
export default {
  async fetch(request, env) {
    const origin = request.headers.get('Origin');

    if (request.method === 'OPTIONS') {
      return new Response(null, { status: 204, headers: corsHeaders(origin) });
    }
    if (request.method !== 'POST') {
      return json({ error: 'Method not allowed' }, 405, origin);
    }
    if (!env.GEMINI_API_KEY) {
      // תקלת תצורה בשרת — לא משהו שהתלמידה/מורה יכולות לתקן.
      return json({ error: 'שרת ה-AI לא מוגדר (חסר מפתח API). פני למנהל המערכת.' }, 500, origin);
    }

    let payload;
    try {
      payload = await request.json();
    } catch (e) {
      return json({ error: 'בקשה לא תקינה (JSON שגוי)' }, 400, origin);
    }

    const action = payload && payload.action;
    try {
      let result;
      switch (action) {
        case 'gradeExam':
          result = await handleGradeExam(env.GEMINI_API_KEY, payload.gradingData);
          break;
        case 'checkHomework':
          result = await handleCheckHomework(env.GEMINI_API_KEY, payload);
          break;
        case 'chat':
          result = await handleChat(env.GEMINI_API_KEY, payload.messages, payload.context);
          break;
        default:
          return json({ error: 'Invalid action' }, 400, origin);
      }
      return json(result, 200, origin);
    } catch (e) {
      console.error(`[gemini-proxy] action=${action} school=${payload?.school || '?'} error:`, e);
      return json({ error: String(e && e.message ? e.message : e) }, 500, origin);
    }
  },
};
