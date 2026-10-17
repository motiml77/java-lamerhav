// בדיקת לוגיקה של gemini-proxy.js בלי Gemini/Cloudflare אמיתיים — fetch מדומה.
// בודק: fallback בין מודלים, פענוח JSON עטוף ב-markdown, מיפוי שדות לשני
// ה-actions, CORS, ו-action:'chat' בשני המצבים (run-code + עוזרת).

import assert from 'node:assert';

let fetchCalls = [];
let extraEnv = {}; // QUOTA מדומה לבדיקות המונה
let fetchScript = [];
global.fetch = async (url, opts) => {
  fetchCalls.push({ url, body: JSON.parse(opts.body) });
  const next = fetchScript.shift();
  if (!next) throw new Error('fetch called more times than scripted');
  return {
    ok: next.ok,
    status: next.status || (next.ok ? 200 : 500),
    json: async () => next.body,
  };
};

const mod = await import(new URL('./gemini-proxy.js', import.meta.url).href);
const worker = mod.default;

async function req(payload, origin = 'https://java-lamerhav.vercel.app') {
  fetchCalls = [];
  const request = {
    method: 'POST',
    headers: { get: (h) => (h === 'Origin' ? origin : null) },
    json: async () => payload,
  };
  const res = await worker.fetch(request, { GEMINI_API_KEY: 'fake-key-for-test', ...extraEnv });
  const body = await res.json();
  return { status: res.status, body, headers: res.headers, calls: fetchCalls };
}

let pass = 0, fail = 0;
function check(name, cond, detail) {
  if (cond) { pass++; console.log('PASS ' + name); }
  else { fail++; console.log('FAIL ' + name + (detail ? ' — ' + detail : '')); }
}

// ===== 1. gradeExam: תשובה תקינה, מודל ראשון מצליח =====
fetchScript = [{ ok: true, body: { candidates: [{ content: { parts: [{ text: JSON.stringify({
  score: 8, grade: 'טוב', feedback: 'כמעט מושלם', errors: ['שורה 3: off-by-one'], suggestions: [], encouragement: 'כל הכבוד' }) }] } }] } }];
{
  const r = await req({ action: 'gradeExam', school: 'lamerhav', gradingData: {
    code: 'int x=1;', questionText: 'חשבי סכום', gradingInstructions: 'בדקי בקפדנות', questionPoints: 10 } });
  check('gradeExam: status 200', r.status === 200, JSON.stringify(r.body));
  check('gradeExam: aiScore ממופה מ-score', r.body.aiScore === 8, JSON.stringify(r.body));
  check('gradeExam: maxPoints מהבקשה', r.body.maxPoints === 10, JSON.stringify(r.body));
  check('gradeExam: errors מועבר', Array.isArray(r.body.errors) && r.body.errors.length === 1);
  check('gradeExam: modelUsed = המודל הראשון', r.body.modelUsed === 'gemini-3.8-flash', r.body.modelUsed);
  check('gradeExam: gradingInstructions הועבר כ-systemInstruction', r.calls[0].body.systemInstruction.parts[0].text === 'בדקי בקפדנות');
  check('gradeExam: קריאה אחת בלבד (לא נפל למודל הבא)', r.calls.length === 1, String(r.calls.length));
}

// ===== 2. gradeExam: JSON עטוף ב-markdown fence =====
fetchScript = [{ ok: true, body: { candidates: [{ content: { parts: [{ text: '```json\n' + JSON.stringify({ score: 5, feedback: 'חלקי', errors: [], suggestions: [], encouragement: '' }) + '\n```' }] } }] } }];
{
  const r = await req({ action: 'gradeExam', gradingData: { code: 'x', questionPoints: 10 } });
  check('markdown fence: פוענח נכון', r.body.aiScore === 5, JSON.stringify(r.body));
}

// ===== 3. fallback: מודל ראשון נכשל (500), שני מצליח =====
fetchScript = [
  { ok: false, status: 503, body: { error: { message: 'overloaded' } } },
  { ok: true, body: { candidates: [{ content: { parts: [{ text: JSON.stringify({ score: 7, feedback: 'ok', errors: [], suggestions: [], encouragement: '' }) }] } }] } },
];
{
  const r = await req({ action: 'gradeExam', gradingData: { code: 'x', questionPoints: 10 } });
  check('fallback: 2 קריאות (ראשון נכשל)', r.calls.length === 2, String(r.calls.length));
  check('fallback: modelUsed הוא השני', r.body.modelUsed === 'gemini-3.6-flash', r.body.modelUsed);
  check('fallback: התוצאה מהמודל השני', r.body.aiScore === 7);
}

// ===== 4. כל המודלים נכשלים → שגיאה ברורה, לא קריסה =====
fetchScript = [
  { ok: false, status: 503, body: {} },
  { ok: false, status: 503, body: {} },
  { ok: false, status: 503, body: {} },
];
{
  const r = await req({ action: 'gradeExam', gradingData: { code: 'x', questionPoints: 10 } });
  check('כל המודלים נכשלים: status 500', r.status === 500, String(r.status));
  check('כל המודלים נכשלים: הודעת שגיאה מכילה "כל המודלים נכשלו"', /כל המודלים נכשלו/.test(r.body.error), r.body.error);
}

// ===== 5. checkHomework: מיפוי שדות מלא, כולל אוטומטים (errors כאובייקטים) =====
fetchScript = [{ ok: true, body: { candidates: [{ content: { parts: [{ text: JSON.stringify({
  correct: false, score: 6, feedback: 'כמעט', errors: [{ location: 'q1', problem: 'חסר מעבר', failingWord: 'ab', hint: 'הוסיפי מעבר' }],
  suggestions: ['נסי שוב'], languageExamples: ['aab', 'b'], encouragement: 'המשיכי' }) }] } }] } }];
{
  const r = await req({ action: 'checkHomework', code: 'automaton', questionPoints: 10, homeworkPrompt: 'בדקי אוטומט' });
  check('checkHomework: score/correct/feedback', r.body.score === 6 && r.body.correct === false && r.body.feedback === 'כמעט');
  check('checkHomework: errors כאובייקטים עוברים כמו שהם', r.body.errors[0].location === 'q1', JSON.stringify(r.body.errors));
  check('checkHomework: languageExamples מועבר', r.body.languageExamples.length === 2);
}

// ===== 6. checkHomework: ניקוד גבוה מהמקסימום נחסם (הגנה מפני מודל שמזה) =====
fetchScript = [{ ok: true, body: { candidates: [{ content: { parts: [{ text: JSON.stringify({ correct: true, score: 999, feedback: 'מצוין', errors: [] }) }] } }] } }];
{
  const r = await req({ action: 'checkHomework', code: 'x', questionPoints: 10, homeworkPrompt: 'p' });
  check('checkHomework: score נחתך למקסימום', r.body.score === 10, r.body.score);
}

// ===== 7. chat: run-code — טמפרטורה 0, systemInstruction נכון =====
fetchScript = [{ ok: true, body: { candidates: [{ content: { parts: [{ text: '55' }] } }] } }];
{
  const r = await req({ action: 'chat', messages: [{ role: 'user', content: 'System.out.println(55);' }], context: { mode: 'run-code' } });
  check('chat run-code: מחזיר response', r.body.response === '55', JSON.stringify(r.body));
  check('chat run-code: temperature=0', r.calls[0].body.generationConfig.temperature === 0);
  check('chat run-code: systemInstruction הוא הוראת "מדמה מהדר"', /מדמה מהדר/.test(r.calls[0].body.systemInstruction.parts[0].text));
}

// ===== 8. chat: עוזרת AI — role mapping assistant→model, הקשר בפרומפט =====
fetchScript = [{ ok: true, body: { candidates: [{ content: { parts: [{ text: 'נסי לבדוק מה קורה בלולאה' }] } }] } }];
{
  const r = await req({ action: 'chat', messages: [
    { role: 'assistant', content: 'שלום, איך אפשר לעזור?' },
    { role: 'user', content: 'הלולאה שלי לא עוצרת' },
  ], context: { questionText: 'כתבי לולאה', currentCode: 'while(true){}', questionType: 'code' } });
  check('chat helper: מחזיר תשובה', r.body.response.length > 0);
  check('chat helper: role assistant→model', r.calls[0].body.contents[0].role === 'model');
  check('chat helper: role user נשאר user', r.calls[0].body.contents[1].role === 'user');
  check('chat helper: ההקשר (currentCode) מוזרק לפרומפט', r.calls[0].body.systemInstruction.parts[0].text.includes('while(true)'));
  check('chat helper: לא run-code prompt', !r.calls[0].body.systemInstruction.parts[0].text.includes('מדמה מהדר'));
}

// ===== 9. action לא מוכר =====
{
  const r = await req({ action: 'doesNotExist' });
  check('action לא מוכר: 400 Invalid action', r.status === 400 && r.body.error === 'Invalid action');
}

// ===== 9b. תשובה פגומה של מודל = כישלון שלו, והשרשרת ממשיכה =====
const goodJson = JSON.stringify({ score: 8, feedback: 'ok', errors: [], suggestions: [], encouragement: '' });
const wrap = (text) => ({ ok: true, body: { candidates: [{ content: { parts: [{ text }] } }] } });
{
  // JSON שבור במודל הראשון: בעבר הוחזר "לא הצלחנו לפענח" גם כשהמודל הבא היה עונה
  fetchScript = [wrap('{"score": 3, "feedback": "שבור'), wrap(goodJson)];
  const r = await req({ action: 'gradeExam', gradingData: { code: 'x', questionPoints: 10 } });
  check('JSON שבור: עבר למודל הבא', r.calls.length === 2 && r.body.aiScore === 8, JSON.stringify(r.body).slice(0, 120));
  check('JSON שבור: modelUsed הוא השני', r.body.modelUsed === 'gemini-3.6-flash', r.body.modelUsed);

  // JSON עם משפט פתיחה לפניו — עדיין ניתן לפענוח, בלי לבזבז מודל
  fetchScript = [wrap('הנה התשובה: ' + goodJson)];
  const r2 = await req({ action: 'gradeExam', gradingData: { code: 'x', questionPoints: 10 } });
  check('JSON עם טקסט לפניו: פוענח במודל הראשון', r2.calls.length === 1 && r2.body.aiScore === 8, JSON.stringify(r2.body).slice(0, 120));

  // אות קירילית באמצע מילה עברית: נצפה בפועל ב-gemini-3.5-flash-lite
  const cyr = String.fromCharCode(0x440);
  fetchScript = [wrap(JSON.stringify({ score: 5, feedback: 'ש' + cyr + 'וני', errors: [], suggestions: [], encouragement: '' })), wrap(goodJson)];
  const r3 = await req({ action: 'gradeExam', gradingData: { code: 'x', questionPoints: 10 } });
  check('אות זרה בתשובה: עבר למודל הבא', r3.calls.length === 2 && r3.body.aiScore === 8, JSON.stringify(r3.body).slice(0, 120));

  // אם כל המודלים מחזירים אות זרה — מחזירים את הטובה ביותר ולא שגיאה לתלמידה
  const bad = wrap(JSON.stringify({ score: 5, feedback: 'ש' + cyr + 'וני', errors: [], suggestions: [], encouragement: '' }));
  fetchScript = [bad, bad, bad, bad, bad];
  const r4 = await req({ action: 'gradeExam', gradingData: { code: 'x', questionPoints: 10 } });
  check('כולם עם אות זרה: מחזיר תשובה ולא שגיאה', r4.status === 200 && r4.body.aiScore === 5, r4.status + ' ' + JSON.stringify(r4.body).slice(0, 80));
}

// ===== 9c. כל המודלים מלאים (429): הודעה בעברית, לא הודעת המכסה של גוגל =====
{
  const quota = { ok: false, status: 429, body: { error: { message: 'You exceeded your current quota, please check your plan and billing details.' } } };
  fetchScript = [quota, quota, quota, quota, quota];
  const r = await req({ action: 'chat', messages: [{ role: 'user', content: 'hi' }] });
  check('מכסה נגמרה: קוד 429 ולא 500', r.status === 429, String(r.status));
  check('מכסה נגמרה: הודעה בעברית עם מה לעשות', /נסי שוב/.test(String(r.body.error)) && !/quota|billing/i.test(String(r.body.error)), String(r.body.error));
  check('מכסה נגמרה: נוסו כל חמשת המודלים', r.calls.length === 5, String(r.calls.length));
  // תקלה אמיתית (לא עומס) נשארת 500 עם הפרטים לאבחון
  const boom = { ok: false, status: 400, body: { error: { message: 'bad request' } } };
  fetchScript = [boom, boom, boom, boom, boom];
  const r2 = await req({ action: 'chat', messages: [{ role: 'user', content: 'hi' }] });
  check('תקלה אמיתית (400) נשארת 500', r2.status === 500, String(r2.status));
}

// ===== 9d. מונה מכסות יומי (QuotaTracker האמיתי, עם אחסון מדומה) =====
{
  const todayPT = new Date().toLocaleDateString('en-CA', { timeZone: 'America/Los_Angeles' });
  const tick = () => new Promise((r) => setTimeout(r, 25)); // הדיווח למונה רץ ברקע
  const okBody = { ok: true, body: { candidates: [{ content: { parts: [{ text: 'שלום' }] } }] } };
  const q429 = { ok: false, status: 429, body: { error: { message: 'quota' } } };
  const chat = () => req({ action: 'chat', messages: [{ role: 'user', content: 'hi' }] });
  function fakeQuota(initial) {
    const store = new Map();
    if (initial) store.set('st', initial);
    const tracker = new mod.QuotaTracker({ storage: { get: async (k) => store.get(k), put: async (k, v) => { store.set(k, JSON.parse(JSON.stringify(v))); } } });
    return { store, env: { QUOTA: { idFromName: () => 'global', get: () => ({ fetch: (u, o) => tracker.fetch(new Request(u, o)) }) } } };
  }

  // תקרה יומית: מודל שהגיע לתקרה מדולג מראש, בלי בקשה כושלת
  let fq = fakeQuota({ day: todayPT, counts: { 'gemini-3.8-flash': 18 }, blocked: {} });
  extraEnv = fq.env; fetchScript = [okBody];
  let r = await chat();
  check('מונה: מודל בתקרה מדולג מראש (בקשה אחת בלבד)', r.calls.length === 1, String(r.calls.length));
  check('מונה: הבקשה הלכה ישר ל-3.6', /gemini-3\.6-flash/.test(r.calls[0].url) && r.body.modelUsed === 'gemini-3.6-flash', r.calls[0] && r.calls[0].url);

  // הצלחה נספרת
  fq = fakeQuota(); extraEnv = fq.env; fetchScript = [okBody];
  await chat(); await tick();
  check('מונה: הצלחה נספרת', (fq.store.get('st').counts['gemini-3.8-flash'] || 0) === 1, JSON.stringify(fq.store.get('st')));

  // 429 מוקדם = קצב לדקה: צינון קצר, ובבקשה הבאה מדלגים
  fq = fakeQuota(); extraEnv = fq.env; fetchScript = [q429, okBody];
  r = await chat(); await tick();
  check('מונה: 429 → עבר למודל הבא', r.calls.length === 2 && r.body.modelUsed === 'gemini-3.6-flash');
  fetchScript = [okBody];
  r = await chat();
  check('מונה: אחרי 429 מוקדם מדלגים על המודל (צינון)', r.calls.length === 1 && /gemini-3\.6-flash/.test(r.calls[0].url), r.calls[0] && r.calls[0].url);

  // 429 אחרי 10+ הצלחות = מכסה יומית: חסום עד חצות
  fq = fakeQuota({ day: todayPT, counts: { 'gemini-3.8-flash': 12 }, blocked: {} });
  extraEnv = fq.env; fetchScript = [q429, okBody];
  await chat(); await tick();
  check('מונה: 429 אחרי 10+ הצלחות = חסום להיום', fq.store.get('st').blocked['gemini-3.8-flash'] === 'day', JSON.stringify(fq.store.get('st').blocked));

  // מונה חדש (ספירה 0) שמקבל 429 שלוש פעמים ברצף: מכסה יומית, לא קצב
  fq = fakeQuota(); extraEnv = fq.env;
  for (let k = 0; k < 3; k++) {
    // מדמים שהצינון עבר בין הניסיונות
    const st0 = fq.store.get('st'); if (st0) { st0.blocked = {}; fq.store.set('st', st0); }
    fetchScript = [q429, okBody]; await chat(); await tick();
  }
  check('מונה: שלוש דחיות רצופות = חסום להיום', fq.store.get('st').blocked['gemini-3.8-flash'] === 'day', JSON.stringify(fq.store.get('st').blocked));

  // יום חדש מאפס
  fq = fakeQuota({ day: '2000-01-01', counts: { 'gemini-3.8-flash': 99 }, blocked: { 'gemini-3.8-flash': 'day' } });
  extraEnv = fq.env; fetchScript = [okBody];
  r = await chat();
  check('מונה: יום חדש מאפס ספירה וחסימות', r.calls.length === 1 && /gemini-3\.8-flash/.test(r.calls[0].url), r.calls[0] && r.calls[0].url);

  // המונה קרס: ממשיכים כמו קודם, לא נופלים
  extraEnv = { QUOTA: { idFromName: () => 'x', get: () => ({ fetch: async () => { throw new Error('DO down'); } }) } };
  fetchScript = [okBody];
  r = await chat();
  check('מונה לא זמין: ה-AI ממשיך לעבוד', r.status === 200 && r.body.modelUsed === 'gemini-3.8-flash', r.status + ' ' + r.body.modelUsed);

  // הכול חסום להיום: הודעה ברורה, בלי לשלוח אף בקשה לגוגל
  const dead = { day: todayPT, counts: { 'gemini-3.8-flash': 18, 'gemini-3.6-flash': 18 },
    blocked: { 'gemini-2.5-flash': 'day', 'gemini-3.5-flash-lite': 'day', 'gemini-3.1-flash-lite': 'day' } };
  fq = fakeQuota(dead); extraEnv = fq.env; fetchScript = [];
  r = await chat();
  check('הכול חסום: 429 בעברית ובלי בקשות לגוגל', r.status === 429 && r.calls.length === 0 && /נסי שוב/.test(String(r.body.error)), r.status + ' calls=' + r.calls.length);

  // quotaStatus
  fq = fakeQuota({ day: todayPT, counts: { 'gemini-3.8-flash': 5 }, blocked: {} });
  extraEnv = fq.env; fetchScript = [];
  r = await req({ action: 'quotaStatus' });
  check('quotaStatus מחזיר את הספירה', r.body.counts && r.body.counts['gemini-3.8-flash'] === 5, JSON.stringify(r.body));

  extraEnv = {}; // לא מדליפים לבדיקות הבאות
}

// ===== 10. CORS =====
{
  fetchScript = [{ ok: true, body: { candidates: [{ content: { parts: [{ text: 'hi' }] } }] } }];
  const r1 = await req({ action: 'chat', messages: [{ role: 'user', content: 'hi' }] }, 'https://evil-site.example.com');
  check('CORS: מקור לא מורשה נדחה (Allow-Origin=null)', r1.headers.get('Access-Control-Allow-Origin') === 'null');
  fetchScript = [{ ok: true, body: { candidates: [{ content: { parts: [{ text: 'hi' }] } }] } }];
  const r2 = await req({ action: 'chat', messages: [{ role: 'user', content: 'hi' }] }, 'https://java-lamerhav.vercel.app');
  check('CORS: מקור מורשה מקבל Allow-Origin משלו', r2.headers.get('Access-Control-Allow-Origin') === 'https://java-lamerhav.vercel.app');
  fetchScript = [{ ok: true, body: { candidates: [{ content: { parts: [{ text: 'hi' }] } }] } }];
  const r3 = await req({ action: 'chat', messages: [{ role: 'user', content: 'hi' }] }, 'https://java-lamerhav-abc123xyz.vercel.app');
  check('CORS: תת-דומיין תצוגה מקדימה של vercel מורשה', r3.headers.get('Access-Control-Allow-Origin') === 'https://java-lamerhav-abc123xyz.vercel.app');
  // הכתובת הראשית java-bagrut נשכחה בהעברה, והוורקר החזיר null — כל קריאת AI באתר החי נחסמה בדפדפן
  fetchScript = [{ ok: true, body: { candidates: [{ content: { parts: [{ text: 'hi' }] } }] } }];
  const r4 = await req({ action: 'chat', messages: [{ role: 'user', content: 'hi' }] }, 'https://java-bagrut.vercel.app');
  check('CORS: הכתובת הראשית java-bagrut מורשית', r4.headers.get('Access-Control-Allow-Origin') === 'https://java-bagrut.vercel.app');
  fetchScript = [{ ok: true, body: { candidates: [{ content: { parts: [{ text: 'hi' }] } }] } }];
  const r5 = await req({ action: 'chat', messages: [{ role: 'user', content: 'hi' }] }, 'https://java-bagrut.vercel.app.evil.example');
  check('CORS: דומיין שרק מתחיל ב-java-bagrut נדחה', r5.headers.get('Access-Control-Allow-Origin') === 'null');
}

// ===== 11. חסר מפתח API =====
{
  const request = { method: 'POST', headers: { get: () => 'https://java-lamerhav.vercel.app' }, json: async () => ({ action: 'chat' }) };
  const res = await worker.fetch(request, {});
  const body = await res.json();
  check('בלי GEMINI_API_KEY: 500 עם הודעה ברורה', res.status === 500 && /לא מוגדר/.test(body.error), JSON.stringify(body));
}

// ===== 12. OPTIONS (preflight) =====
{
  const request = { method: 'OPTIONS', headers: { get: () => 'https://java-lamerhav.vercel.app' } };
  const res = await worker.fetch(request, { GEMINI_API_KEY: 'x' });
  check('OPTIONS: 204 עם CORS headers', res.status === 204 && res.headers.get('Access-Control-Allow-Origin') === 'https://java-lamerhav.vercel.app');
}

// ===== 13ב. gradeHandwritten — מבחן בכתב יד, כמה תמונות, נרמול ציונים =====
fetchScript = [{ ok: true, body: { candidates: [{ content: { parts: [{ text: JSON.stringify({
  totalScore: 15, maxScore: 20, generalFeedback: 'סבבה',
  questions: [
    { questionNumber: 1, title: 'לולאה', score: 8, maxScore: 10, feedback: 'טוב', errors: [], suggestions: [], grade: 'טוב' },
    { questionNumber: 2, title: 'תנאי', score: 999, maxScore: 10, feedback: 'חלקי', errors: ['שגיאה'], suggestions: [], grade: 'סביר' },
  ] }) }] } }] } }];
{
  const r = await req({ action: 'gradeHandwritten', handwrittenData: {
    examImages: ['data:image/jpeg;base64,ZXhhbQ=='], studentImages: ['data:image/jpeg;base64,c3R1ZGVudA=='], gradingInstructions: 'בדקי בקפדנות' } });
  check('gradeHandwritten: status 200', r.status === 200, JSON.stringify(r.body));
  check('gradeHandwritten: שתי תמונות נשלחו', r.calls[0].body.contents[0].parts.filter(p => p.inlineData).length === 2);
  check('gradeHandwritten: totalScore/maxScore מהתשובה', r.body.totalScore === 15 && r.body.maxScore === 20);
  check('gradeHandwritten: 2 שאלות', r.body.questions.length === 2);
  check('gradeHandwritten: ציון שאלה בודדת נחתך לפי maxScore (לא 999)', r.body.questions[1].score === 10, r.body.questions[1].score);
}
{
  const r = await req({ action: 'gradeHandwritten', handwrittenData: { studentImages: [] } });
  check('gradeHandwritten: בלי תמונות תלמידה → שגיאה ברורה, לא קריסה', r.status === 500 && /חסרות תמונות/.test(r.body.error), JSON.stringify(r.body));
}

// ===== 13ג. suggestAnswer — קוד נכון מוחזר כמו שהוא, קוד שגוי מתוקן =====
fetchScript = [{ ok: true, body: { candidates: [{ content: { parts: [{ text: JSON.stringify({ isCorrect: true, correctedCode: 'int x=1;', explanation: 'הקוד נכון' }) }] } }] } }];
{
  const r = await req({ action: 'suggestAnswer', suggestData: { code: 'int x=1;', questionText: 'הגדירי x' } });
  check('suggestAnswer: status 200', r.status === 200, JSON.stringify(r.body));
  check('suggestAnswer: isCorrect=true עובר', r.body.isCorrect === true);
  check('suggestAnswer: correctedCode מוחזר', r.body.correctedCode === 'int x=1;');
}

// ===== 13ד. תמונת שאלה ככתובת http — ה-Worker מוריד אותה ומטמיע כבייטים =====
// זה המסלול החדש מאז שתמונות עברו ל-Firebase Storage. אם זה נשבר, בדיקת AI
// מתעלמת מהתמונה בשקט.
{
  const realFetch = global.fetch;
  let imageFetched = null;
  global.fetch = async (url, opts) => {
    if (typeof url === 'string' && url.startsWith('https://firebasestorage')) {
      imageFetched = url;
      return { ok: true, status: 200,
        headers: { get: (h) => (h === 'content-type' ? 'image/png' : null) },
        arrayBuffer: async () => new Uint8Array([1, 2, 3, 4, 5]).buffer };
    }
    return realFetch(url, opts);
  };
  fetchScript = [{ ok: true, body: { candidates: [{ content: { parts: [{ text: JSON.stringify({ score: 7, feedback: 'ok', errors: [], suggestions: [], encouragement: '' }) }] } }] } }];
  const r = await req({ action: 'gradeExam', gradingData: {
    code: 'x', questionPoints: 10,
    questionImage: 'https://firebasestorage.googleapis.com/v0/b/exams-a93fb.firebasestorage.app/o/q.jpg?alt=media&token=abc' } });
  check('כתובת http: התמונה נמשכה בפועל', imageFetched !== null, String(imageFetched));
  const imgParts = r.calls[0] ? r.calls[0].body.contents[0].parts.filter(p => p.inlineData) : [];
  check('כתובת http: הומרה ל-inlineData עם mimeType נכון', imgParts.length === 1 && imgParts[0].inlineData.mimeType === 'image/png', JSON.stringify(imgParts));
  check('כתובת http: הבקשה הצליחה', r.status === 200, JSON.stringify(r.body).slice(0, 120));
  global.fetch = realFetch;
}
{
  // כתובת שבורה → לא קורס, פשוט ממשיך בלי התמונה
  const realFetch = global.fetch;
  global.fetch = async (url, opts) => {
    if (typeof url === 'string' && url.startsWith('https://broken')) return { ok: false, status: 404, headers: { get: () => null } };
    return realFetch(url, opts);
  };
  fetchScript = [{ ok: true, body: { candidates: [{ content: { parts: [{ text: JSON.stringify({ score: 5, feedback: 'ok', errors: [] }) }] } }] } }];
  const r = await req({ action: 'gradeExam', gradingData: { code: 'x', questionPoints: 10, questionImage: 'https://broken.example/x.jpg' } });
  check('כתובת תמונה שבורה: לא קורס, ממשיך בלי תמונה', r.status === 200 && r.body.aiScore === 5, JSON.stringify(r.body).slice(0, 120));
  global.fetch = realFetch;
}

// ===== 13. תמונה מוטמעת (data URI) מטופלת בלי לקרוס =====
fetchScript = [{ ok: true, body: { candidates: [{ content: { parts: [{ text: JSON.stringify({ score: 4, feedback: 'x', errors: [], suggestions: [], encouragement: '' }) }] } }] } }];
{
  const r = await req({ action: 'gradeExam', gradingData: { code: 'x', questionImage: 'data:image/png;base64,aGVsbG8=', questionPoints: 10 } });
  check('תמונה: לא קרס, ותוכן ה-parts כולל inlineData', r.status === 200 && r.calls[0].body.contents[0].parts.some(p => p.inlineData));
}

console.log('\n===== ' + pass + '/' + (pass + fail) + ' =====');
process.exit(fail > 0 ? 1 : 0);
