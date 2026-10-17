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
 * הייתה נתמכת.
 *
 * עדכון: יש כעת גישה ל-Cloudflare (wrangler מחובר לאותו חשבון), ומשכה
 * הקוד הקודם נמשך ונקרא לפני שהוחלף — ואז נמצאו הסיבות המדויקות: המודלים
 * הישנים היו gemini-1.5-pro-002 / gemini-1.5-flash (משפחת 1.5 יצאה
 * משימוש), וה-CORS היה נעול לדומיין ישן לגמרי (java-exams.motiml77...)
 * שאינו אחד מהאתרים הפעילים היום. שני אלה תוקנו כאן.
 * נמצאו גם שתי פעולות ישנות ש-app.html אינו קורא להן כלל היום —
 * gradeHandwritten (בדיקת מבחן בכתב יד מצולם) ו-suggestAnswer (תיקון קוד
 * תוך שמירה על הגישה של התלמידה). הן לא נמחקו, רק עודכנו לאותה שרשרת
 * מודלים — כדי לא לאבד יכולת קיימת בלי סיבה, גם אם היא לא בשימוש כרגע.
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
// מודלים — לפי איכות, מהגבוהה לנמוכה. כולם בחינם לפי דף התמחור של גוגל
// (2026-10-17: "Free of charge" לקלט ולפלט), מפתח API רגיל בלי כרטיס אשראי.
//
// נמדד בפועל באותו יום, והוא מה שמסביר את הסדר:
//   gemini-3.8-flash, gemini-3.6-flash — כ-20 בקשות ליום כל אחד, ואז 429.
//   gemini-3.5-flash-lite — 13-15 בקשות בדקה, וממשיך הרבה אחרי השאר.
// כלומר המודלים החזקים מספיקים לכמה עשרות בדיקות ביום, ומשם השרשרת יורדת
// בהדרגה למודלים הקלים. כל מודל הוא "דלי" מכסה נפרד, ולכן יותר מודלים בשרשרת
// = יותר קיבולת כוללת. 2.5-flash לפני ה-lite כי האיכות שלו גבוהה יותר.
// מכסה נקבעת לפרויקט ביום (מתאפסת בחצות שעון מערב ארה"ב), ולא לפי בית ספר.
//
// הוסרו: gemini-2.0-flash (404 — כבוי), gemini-3.7-flash (לא מופיע יותר בדפי
// גוגל). gemini-4-argon חזק יותר אבל אין לו מכסה חינמית ($2/$10 למיליון טוקנים).
// במסלול החינמי גוגל רשאית להשתמש בתוכן הבקשות לשיפור המוצרים שלה.
// נבדק ישירות מול המפתח (probeModels, 2026-10-17). לא נכנסו: gemma-4-26b-a4b-it מדפיס את
// החשיבה הפנימית שלו בתוך התשובה ("User asks: ..."), gemma-4-31b-it מחזיר 500,
// ו-gemini-4-argon בתשלום. gemini-3-flash-preview ו-2.5-flash-lite עובדים, כל אחד עם מכסה נפרדת.
// מודל תצוגה-מקדימה עלול להיעלם — 404 חוסם אותו אוטומטית עד חצות.
const MODELS = ['gemini-3.8-flash', 'gemini-3.6-flash', 'gemini-3-flash-preview', 'gemini-2.5-pro', 'gemini-2.5-flash',
  'gemini-3.5-flash-lite', 'gemini-3.1-flash-lite', 'gemini-2.5-flash-lite'];

// מודלים שלא תומכים ב-responseMimeType (מצב JSON) — לא שולחים להם את השדה, והפענוח
// הסלחני (tryParseJson) מחלץ את ה-JSON מהטקסט. נלמד מהתיעוד של Gemma ב-Gemini API.
const MODEL_TRAITS = {
  'gemma-4-31b-it': { noJsonMode: true },
  'gemma-4-26b-a4b-it': { noJsonMode: true },
};
// כל המודלים החינמיים שנמצאו בדף התמחור (2026-10-17) — לבדיקת זמינות בלבד (probeModels)
const CANDIDATE_MODELS = ['gemini-3.8-flash', 'gemini-3.6-flash', 'gemini-3-flash-preview', 'gemini-2.5-pro', 'gemini-2.5-flash',
  'gemma-4-31b-it', 'gemma-4-26b-a4b-it', 'gemini-3.5-flash-lite', 'gemini-3.1-flash-lite', 'gemini-2.5-flash-lite'];

// ---------------------------------------------------------------------------
// מונה מכסות יומי. בלי זה כל בקשה ניסתה קודם את המודלים המלאים, קיבלה 429, ורק
// אז עברה הלאה — בזבוז של זמן והמתנה לתלמידה, על כל בדיקה, כל היום.
//
// המונה חי ב-Durable Object אחד (מופע יחיד), כי KV בחינם מוגבל ל-1,000 כתיבות
// ביום והספירה חייבת להיות אטומית. מכסת גוגל מתאפסת בחצות שעון החוף המערבי של
// ארה"ב, ולכן "יום" כאן הוא לפי אותו אזור זמן.
//
// שני מקורות לדילוג על מודל:
//  1. תקרה יומית ידועה (DAILY_LIMITS) — דילוג מראש, לפני שנשלחת בקשה כושלת.
//  2. למידה מ-429: אם מודל נדחה אחרי שכבר ענה 10 פעמים ומעלה — זו כנראה מכסה
//     יומית, וחוסמים עד חצות. אם נדחה מוקדם יותר — זה קצב לדקה, וקורים 60 שניות.
// כך הוורקר מסתדר גם כשגוגל משנה את המכסות, בלי עדכון קוד.
//
// כשל במונה אינו כשל ב-AI: אם ה-Durable Object לא זמין, מתנהגים כמו קודם.
// ---------------------------------------------------------------------------
// מדד ב-2026-10-17: 3.8-flash ו-3.6-flash נעצרו אחרי כ-20 בקשות כל אחד. תקרה
// נמוכה במעט משאירה מרווח. מודלים בלי רשומה כאן נלמדים מ-429 בלבד.
const DAILY_LIMITS = { 'gemini-3.8-flash': 18, 'gemini-3.6-flash': 18 };
const DAILY_BLOCK_AFTER = 10;   // 429 אחרי כמות כזו של הצלחות = מכסה יומית, לא קצב
const RATE_COOLDOWN_MS = 60 * 1000;
const DAILY_STRIKES = 3;         // שלוש דחיות רצופות בלי הצלחה באמצע = מכסה יומית

function pacificDay(now = Date.now()) {
  return new Date(now).toLocaleDateString('en-CA', { timeZone: 'America/Los_Angeles' });
}

export class QuotaTracker {
  constructor(state) { this.state = state; }

  async load() {
    const today = pacificDay();
    let st = await this.state.storage.get('st');
    if (!st || st.day !== today) st = { day: today, counts: {}, blocked: {} }; // יום חדש = מתחילים נקי
    return st;
  }

  async fetch(request) {
    const url = new URL(request.url);
    const body = await request.json().catch(() => ({}));
    const st = await this.load();
    const now = Date.now();

    if (url.pathname === '/available') {
      const limits = body.limits || {};
      const models = [], skipped = {};
      for (const m of body.models || []) {
        const used = st.counts[m] || 0;
        if (limits[m] && used >= limits[m]) skipped[m] = 'daily-limit';
        else if (st.blocked[m] === 'day') skipped[m] = 'blocked-today';
        else if (st.blocked[m] > now) skipped[m] = 'cooling';
        else models.push(m);
      }
      return Response.json({ models, skipped, day: st.day });
    }

    if (url.pathname === '/record') {
      const { model, ok, status } = body;
      st.strikes = st.strikes || {};
      if (ok) { st.counts[model] = (st.counts[model] || 0) + 1; st.strikes[model] = 0; }
      else if (status === 404 || status === 403) {
        // המודל לא קיים / לא מורשה למפתח הזה — אין טעם לנסות שוב היום
        st.blocked[model] = 'day';
      } else if (status === 429) {
        // מכסה יומית ולא קצב: או שהמודל כבר ענה הרבה היום, או שנדחה שוב ושוב — קצב לדקה
        // היה מתאפס בין הניסיונות. חשוב בעיקר למונה חדש שעוד לא יודע כמה כבר נוצל היום.
        st.strikes[model] = (st.strikes[model] || 0) + 1;
        const daily = (st.counts[model] || 0) >= DAILY_BLOCK_AFTER || st.strikes[model] >= DAILY_STRIKES;
        st.blocked[model] = daily ? 'day' : now + RATE_COOLDOWN_MS;
      }
      await this.state.storage.put('st', st);
      return Response.json({ ok: true });
    }

    return Response.json({ day: st.day, counts: st.counts, blocked: st.blocked, limits: DAILY_LIMITS });
  }
}

// ה-stub של המונה לבקשה הנוכחית. מוגדר ב-fetch לפני כל טיפול. קבוע לכל הבקשות
// של אותה סביבה, ולכן בטוח גם כשכמה בקשות רצות במקביל באותו isolate.
let quotaStub = null;
let quotaCtx = null;

async function availableModels() {
  if (!quotaStub) return MODELS;
  try {
    const r = await quotaStub.fetch('https://quota/available', { method: 'POST', body: JSON.stringify({ models: MODELS, limits: DAILY_LIMITS }) });
    const d = await r.json();
    if (Object.keys(d.skipped || {}).length) console.log('[gemini-proxy] skipped:', JSON.stringify(d.skipped));
    return Array.isArray(d.models) ? d.models : MODELS;
  } catch (e) {
    console.warn('[gemini-proxy] quota tracker unavailable, using full chain:', String(e && e.message ? e.message : e));
    return MODELS;
  }
}

// הדיווח לא מעכב את התשובה לתלמידה — רץ ברקע
function recordUsage(model, ok, status) {
  if (!quotaStub) return;
  const p = quotaStub.fetch('https://quota/record', { method: 'POST', body: JSON.stringify({ model, ok, status }) }).catch(() => {});
  if (quotaCtx && quotaCtx.waitUntil) quotaCtx.waitUntil(p);
}


// דומיינים שמורשים לקרוא ל-Worker — כל האתרים של הפרויקט הזה (חי, דמו, תצוגות
// מקדימות) ופיתוח מקומי. בלי זה, שינוי בחוקי ה-CORS יעצור את כל האתרים בבת אחת.
const ALLOWED_ORIGIN_PATTERNS = [
  // java-bagrut = הכתובת הראשית מ-2026-09. נשכח בהעברה: הוורקר החזיר
  // Access-Control-Allow-Origin: null, והדפדפן חסם בשקט כל קריאת AI באתר החי.
  /^https:\/\/java-bagrut(-[a-z0-9-]+)?\.vercel\.app$/,
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
  let degraded = null;
  const order = await availableModels();
  if (!order.length) {
    // כל המודלים חסומים להיום לפי המונה — לא שולחים בקשה שבטוח תיכשל
    const e = new Error('כל המודלים הגיעו למכסה היומית');
    e.busy = true;
    throw e;
  }
  for (const model of order) {
    const url = `https://generativelanguage.googleapis.com/v1beta/models/${model}:generateContent`;
    const body = {
      contents,
      systemInstruction: { parts: [{ text: systemInstruction }] },
      generationConfig: {
        temperature,
        ...(jsonMode && !(MODEL_TRAITS[model] || {}).noJsonMode ? { responseMimeType: 'application/json' } : {}),
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
        recordUsage(model, false, res.status);
        const msg = `${model}: HTTP ${res.status} — ${data?.error?.message || 'unknown'}`;
        errors.push(msg);
        console.warn('[gemini-proxy] model failed:', msg); // גלוי ב-wrangler tail, גם כשמודל אחר מצליח בהמשך
        continue; // המודל הזה נכשל — עוברים לבא בתור
      }
      const text = data?.candidates?.[0]?.content?.parts?.map((p) => p.text || '').join('') || '';
      if (!text) {
        errors.push(`${model}: תשובה ריקה (ייתכן חסימת בטיחות — finishReason=${data?.candidates?.[0]?.finishReason})`);
        continue;
      }
      // תשובה לא תקינה = כישלון של המודל הזה, לא הצלחה: עוברים לבא בתור במקום
      // להחזיר לתלמידה "לא הצלחנו לפענח" כשיש עוד מודלים שיכלו לענות.
      if (jsonMode && tryParseJson(text) === undefined) {
        errors.push(`${model}: JSON לא תקין`);
        continue;
      }
      if (FOREIGN_SCRIPT.test(text)) {
        errors.push(`${model}: תווים זרים בתשובה`);
        if (!degraded) degraded = { text, modelUsed: model }; // מוצא אחרון אם כל השאר ייכשלו
        continue;
      }
      recordUsage(model, true, 200);
      return { text, modelUsed: model };
    } catch (e) {
      errors.push(`${model}: ${String(e && e.message ? e.message : e)}`);
    }
  }
  if (degraded) return degraded;
  const err = new Error('כל המודלים נכשלו: ' + errors.join(' | '));
  // עומס/מכסה (429/503) ולא תקלה אמיתית — כדי שהתלמידה תקבל הודעה ברורה בעברית
  err.busy = errors.length > 0 && errors.every((m) => /HTTP (429|503)/.test(m));
  throw err;
}

// Gemini לפעמים עוטף JSON בגדר markdown (```json ... ```) למרות responseMimeType.
// מנקים לפני הפענוח, ונופלים בחזרה למבנה בטוח אם הפענוח עדיין נכשל.
// פענוח סלחני: JSON תקין, או התוכן שבין ה-{ הראשון ל-} האחרון (מודלים חלשים
// לפעמים מוסיפים משפט פתיחה). undefined = לא ניתן לפענח.
function tryParseJson(text) {
  const t = String(text || '').trim().replace(/^```(?:json)?\s*/i, '').replace(/```\s*$/i, '');
  try { return JSON.parse(t); } catch (e) { /* ממשיכים */ }
  const a = t.indexOf('{'), b = t.lastIndexOf('}');
  if (a >= 0 && b > a) { try { return JSON.parse(t.slice(a, b + 1)); } catch (e) { /* ממשיכים */ } }
  return undefined;
}

// אותיות קיריליות / ערביות / סיניות בתשובה בעברית הן סימן לתשובה פגומה של מודל
// חלש (נצפה: "שрони" — קירילית באמצע מילה עברית בתשובת gemini-3.5-flash-lite).
// אף פעם לא לגיטימי בשאלה על Java בעברית, ולכן עוברים למודל הבא בשרשרת.
const FOREIGN_SCRIPT = new RegExp('[' + [[0x0400, 0x04FF], [0x0600, 0x06FF], [0x3040, 0x30FF], [0x4E00, 0x9FFF]]
  .map(([a, b]) => String.fromCharCode(a) + '-' + String.fromCharCode(b)).join('') + ']'); // קירילית, ערבית, יפנית, סינית

function parseJsonLoose(text, fallback) {
  const cleaned = String(text || '')
    .trim()
    .replace(/^```(?:json)?\s*/i, '')
    .replace(/```\s*$/i, '');
  const parsed = tryParseJson(cleaned);
  if (parsed !== undefined) return parsed;
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

// מאז שתמונות השאלה עברו ל-Firebase Storage, questionImage יכול להיות **כתובת
// http** ולא data URI. Gemini לא מקבל כתובת — צריך את הבייטים עצמם. ה-Worker
// מוריד אותם כאן: ל-Worker אין מגבלת CORS על fetch יוצא, בשונה מהדפדפן.
// בלי זה בדיקת AI על שאלה עם תמונה הייתה מתעלמת מהתמונה **בשקט**, בלי שגיאה —
// בדיוק סוג הכשל שלא צועק ולכן מתגלה מאוחר.
async function imagePartAsync(src) {
  if (!src || typeof src !== 'string') return null;
  if (src.startsWith('data:')) return imagePart(src);
  if (!/^https?:\/\//i.test(src)) return null;
  try {
    const res = await fetch(src);
    if (!res.ok) { console.warn('[gemini-proxy] image fetch failed:', res.status, src.slice(0, 120)); return null; }
    const buf = await res.arrayBuffer();
    const bytes = new Uint8Array(buf);
    let bin = '';
    for (let i = 0; i < bytes.length; i += 8192) {
      bin += String.fromCharCode.apply(null, bytes.subarray(i, i + 8192)); // בנתחים — מחרוזת ארגומנטים ענקית מפילה את apply
    }
    return { inlineData: { mimeType: res.headers.get('content-type') || 'image/jpeg', data: btoa(bin) } };
  } catch (e) {
    console.warn('[gemini-proxy] image fetch error:', String(e && e.message ? e.message : e));
    return null;
  }
}

// לבדיקת מבחן בכתב יד — כמה תמונות ברצף אחד
async function imageParts(sources) {
  const list = await Promise.all((Array.isArray(sources) ? sources : []).map(imagePartAsync));
  return list.filter(Boolean);
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
  const img = await imagePartAsync(questionImage);
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
  const img = await imagePartAsync(questionImage);
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
// action: gradeHandwritten — בדיקת מבחן מצולם (כתב יד). app.html אינו קורא
// לפעולה הזו כיום (נבדק — אין אף אזכור), אבל היא יכולת עצמאית ושלמה,
// ולכן הועברה כמו שהיא לשרשרת המודלים הנוכחית ולא נמחקה. הפרומפט המקורי
// נשמר כמעט מילה במילה — הוא כבר מגדיר בדיוק את סדר התמונות (עמודי המבחן
// קודם, אחר כך תשובות התלמידה) ואת מבנה הפלט הנדרש.
// ---------------------------------------------------------------------------
async function handleGradeHandwritten(apiKey, handwrittenData) {
  const { examImages, studentImages, gradingInstructions } = handwrittenData || {};
  if (!studentImages || !studentImages.length) throw new Error('חסרות תמונות תשובות תלמיד');

  const safeExamImages = Array.isArray(examImages) ? examImages : [];
  const examCount = safeExamImages.length;
  const studentCount = studentImages.length;
  const allImages = [...safeExamImages, ...studentImages];

  const examDescription = examCount > 0
    ? `${examCount > 1 ? `${examCount} התמונות הראשונות` : 'התמונה הראשונה'} — דפי המבחן (השאלות).\n${studentCount > 1 ? `${studentCount} התמונות האחרונות` : 'התמונה האחרונה'} — תשובות התלמיד בכתב יד.`
    : `כל ${studentCount > 1 ? `${studentCount} התמונות` : 'התמונה'} — תשובות התלמיד בכתב יד. לא סופקו דפי מבחן נפרדים, זהי את השאלות מתוך התשובות עצמן.`;

  const systemInstruction = `אתה מורה מומחה לבדיקת מבחנים בשפת Java. קיבלת תמונות של תשובות תלמיד בכתב יד${examCount > 0 ? ' יחד עם דפי המבחן' : ''}.

${examDescription}

הנחיות:
- זהי כל שאלה בנפרד${examCount > 0 ? ' מתוך דפי המבחן' : ' מתוך תשובות התלמיד'}
- קראי בקפידה את כתב היד של התלמידה עבור כל שאלה
- אם לא ניתן לקרוא חלק מכתב היד, ציין זאת
- בדקי נכונות הקוד: תחביר, לוגיקה, תנאי קצה
- דרגי כל שאלה בציון מספרי
- כתבי הערות בעברית בלבד
- ציין שגיאות ספציפיות עם הפניה למקום בקוד

החזירי JSON בלבד בפורמט הבא:
{
  "totalScore": מספר — סך הציון הכולל,
  "maxScore": מספר — הציון המקסימלי,
  "questions": [
    { "questionNumber": 1, "title": "תיאור קצר של השאלה", "score": מספר, "maxScore": מספר,
      "feedback": "משוב מפורט", "errors": ["..."], "suggestions": ["..."], "grade": "מצוין/טוב/סביר/צריך שיפור" }
  ],
  "generalFeedback": "סיכום כללי של הביצוע"
}
חשוב: totalScore הוא סכום כל ה-score, maxScore הוא סכום כל ה-maxScore. זהי את כל השאלות גם אם התלמידה לא ענתה על חלקן (ציון 0).`;

  const parts = [{ text: gradingInstructions || '(אין הנחיות נוספות — הניחי תלמידות כיתה יא עם main, Scanner בשם in וכל הפונקציות הבסיסיות)' }, ...(await imageParts(allImages))];
  const { text, modelUsed } = await callGemini(apiKey, systemInstruction, [{ role: 'user', parts }], { jsonMode: true, temperature: 0.2 });

  const parsed = parseJsonLoose(text, { totalScore: 0, maxScore: 100, questions: [], generalFeedback: '' });
  const questions = Array.isArray(parsed.questions)
    ? parsed.questions.map((q, i) => {
        const qMax = typeof q.maxScore === 'number' ? Math.max(1, q.maxScore) : 10;
        // חסימה גם למעלה לפי qMax — לא רק למטה כמו בקוד הקודם. בלעדיה מודל
        // שמזה ציון לשאלה בודדת (למשל 999 על שאלה של 10) עובר בלי בדיקה.
        const qScore = typeof q.score === 'number' ? Math.max(0, Math.min(qMax, q.score)) : 0;
        return {
          questionNumber: q.questionNumber || i + 1,
          title: String(q.title || ''),
          score: qScore,
          maxScore: qMax,
          feedback: String(q.feedback || ''),
          errors: Array.isArray(q.errors) ? q.errors.map(String) : [],
          suggestions: Array.isArray(q.suggestions) ? q.suggestions.map(String) : [],
          grade: String(q.grade || ''),
        };
      })
    : [];
  const totalScore = questions.reduce((sum, q) => sum + q.score, 0);
  const maxScore = questions.reduce((sum, q) => sum + q.maxScore, 0);

  return {
    totalScore: typeof parsed.totalScore === 'number' ? parsed.totalScore : totalScore,
    maxScore: typeof parsed.maxScore === 'number' ? parsed.maxScore : maxScore,
    questions,
    generalFeedback: String(parsed.generalFeedback || ''),
    modelUsed,
  };
}

// ---------------------------------------------------------------------------
// action: suggestAnswer — מתקנת קוד תוך שמירה על הגישה/סגנון של התלמידה,
// ולא כתיבה מחדש. גם היא לא נקראת כיום מ-app.html, הועברה מהקוד הקודם.
// ---------------------------------------------------------------------------
async function handleSuggestAnswer(apiKey, suggestData) {
  const { code, questionText, questionImage } = suggestData || {};
  const systemInstruction = `את מורה מנוסה לג'אווה. קיבלת שאלה ותשובת תלמידה.

המשימה שלך:
1. אם הקוד של התלמידה נכון ועונה על השאלה — החזירי אותו כמו שהוא.
2. אם הקוד לא נכון — כתבי תשובה מתוקנת שמבוססת על הגישה של התלמידה: שמרי על שמות משתנים, סגנון כתיבה ומבנה כמה שאפשר, ותקני רק את מה שצריך תיקון. אל תכתבי מחדש — תקני.

החזירי JSON בלבד:
{ "isCorrect": true/false, "correctedCode": "הקוד המתוקן (או המקורי אם נכון)", "explanation": "הסבר קצר בעברית — מה תוקן ולמה (או 'הקוד נכון')" }`;

  const parts = [{ text: `השאלה: ${questionText || '(לא צוינה)'}\n\nהקוד של התלמידה:\n\`\`\`java\n${code || '// אין קוד'}\n\`\`\`` }];
  const img = await imagePartAsync(questionImage);
  if (img) parts.push(img);

  const { text, modelUsed } = await callGemini(apiKey, systemInstruction, [{ role: 'user', parts }], { jsonMode: true, temperature: 0.2 });
  const parsed = parseJsonLoose(text, { isCorrect: false, correctedCode: code || '', explanation: '' });
  return {
    isCorrect: !!parsed.isCorrect,
    correctedCode: parsed.correctedCode || code || '',
    explanation: parsed.explanation || '',
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

  const { text, modelUsed } = await callGemini(apiKey, systemInstruction, contents, {
    jsonMode: false,
    temperature: isRunCode ? 0 : 0.5, // הרצת קוד חייבת להיות דטרמיניסטית ככל האפשר
  });
  return { response: text, modelUsed };
}

// ---------------------------------------------------------------------------
// בודק כל מודל בשתי קריאות קטנות: רגילה, ואם הצליחה — גם במצב JSON. מחזיר סטטוס,
// זמן ותשובה לדוגמה. לא נספר במונה ולא עובר דרך השרשרת.
async function probeModels(apiKey, models) {
  const out = [];
  for (const model of models) {
    const row = { model };
    for (const mode of ['plain', 'json']) {
      if (mode === 'json' && (MODEL_TRAITS[model] || {}).noJsonMode) { row.json = 'לא נשלח (noJsonMode)'; continue; }
      const t0 = Date.now();
      try {
        const res = await fetch(`https://generativelanguage.googleapis.com/v1beta/models/${model}:generateContent`, {
          method: 'POST', headers: { 'Content-Type': 'application/json', 'x-goog-api-key': apiKey },
          body: JSON.stringify({
            systemInstruction: { parts: [{ text: 'את מורה ל-Java. ענית תמיד בעברית.' }] },
            contents: [{ role: 'user', parts: [{ text: mode === 'json' ? 'החזירי JSON עם שדה "ok" שערכו true ושדה "msg" עם מילה אחת בעברית.' : 'ענה במשפט קצר בעברית: מה זה משתנה בג׳אווה?' }] }],
            generationConfig: mode === 'json' ? { responseMimeType: 'application/json' } : { maxOutputTokens: 200 },
          }),
        });
        const data = await res.json().catch(() => null);
        const text = data && data.candidates && data.candidates[0] && data.candidates[0].content && data.candidates[0].content.parts
          ? data.candidates[0].content.parts.map((p) => p.text || '').join('') : '';
        if (mode === 'plain') {
          row.status = res.status; row.ms = Date.now() - t0;
          row.sample = res.ok ? text.slice(0, 90) : String((data && data.error && data.error.message) || '').slice(0, 140);
          row.foreign = FOREIGN_SCRIPT.test(text);
        } else {
          row.json = res.ok ? (tryParseJson(text) !== undefined ? 'תקין' : 'שבור: ' + text.slice(0, 60)) : 'HTTP ' + res.status;
        }
        if (!res.ok && mode === 'plain') break;
      } catch (e) { row.status = 0; row.sample = String(e && e.message ? e.message : e); break; }
    }
    out.push(row);
  }
  return { models: out };
}

export default {
  async fetch(request, env, ctx) {
    const origin = request.headers.get('Origin');
    quotaCtx = ctx || null;
    quotaStub = env.QUOTA ? env.QUOTA.get(env.QUOTA.idFromName('global')) : null;

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
        case 'probeModels': {
          // אבחון זמינות מודלים: קריאה ישירה לכל מועמד, מחוץ לשרשרת ובלי להשפיע על המונה.
          // נעול בטוקן מנהל — בלי זה כל אחד היה יכול לצרוך את המכסה היומית דרך הוורקר.
          if (!env.ADMIN_TOKEN || payload.token !== env.ADMIN_TOKEN) return json({ error: 'לא מורשה' }, 403, origin);
          result = await probeModels(env.GEMINI_API_KEY, Array.isArray(payload.models) ? payload.models : CANDIDATE_MODELS);
          break;
        }
        case 'quotaStatus':
          // מצב הספירה היומית — לאבחון בלבד, ללא תוכן של תלמידות
          result = quotaStub ? await (await quotaStub.fetch('https://quota/status', { method: 'POST', body: '{}' })).json() : { tracker: 'off' };
          break;
        case 'chat':
          result = await handleChat(env.GEMINI_API_KEY, payload.messages, payload.context);
          break;
        case 'gradeHandwritten':
          result = await handleGradeHandwritten(env.GEMINI_API_KEY, payload.handwrittenData);
          break;
        case 'suggestAnswer':
          result = await handleSuggestAnswer(env.GEMINI_API_KEY, payload.suggestData);
          break;
        default:
          return json({ error: 'Invalid action' }, 400, origin);
      }
      return json(result, 200, origin);
    } catch (e) {
      console.error(`[gemini-proxy] action=${action} school=${payload?.school || '?'} error:`, e);
      if (e && e.busy) {
        // לא מציגים לתלמידה את הודעת המכסה של גוגל (אנגלית, מבלבלת) — רק מה לעשות
        return json({ error: 'ה-AI עמוס כרגע או שהמכסה היומית החינמית נגמרה. נסי שוב בעוד כמה דקות, ואם זה חוזר — פני למורה.', busy: true }, 429, origin);
      }
      return json({ error: String(e && e.message ? e.message : e) }, 500, origin);
    }
  },
};
