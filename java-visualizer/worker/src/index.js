// DSViz — שרת ביניים ל"שליחת תרגיל" (Cloudflare Worker).
//
// התוסף של Eclipse שולח לכאן את דף התרגיל (PDF / תמונות / טקסט מ-Word), והשרת שולח אותו ל-Gemini
// עם ההנחיות והתבנית שמוגדרות כאן (src/prompt.js), ומחזיר JSON מסודר שממנו התוסף בונה את הקוד.
//
// אבטחה:
//   • המפתח של Gemini נשמר רק כאן, כסוד של ה-Worker (GEMINI_API_KEY) — הוא אף פעם לא מגיע למחשב של התלמיד.
//   • ההנחיות והתבנית קבועות בשרת, והתשובה חייבת להתאים לתבנית — אי אפשר להשתמש בשרת כ"צ'אט" כללי.
//   • רק סוגי קבצים מותרים (נבדק גם לפי תחילת הקובץ), הגבלת גודל ומספר קבצים, מפתח-אפליקציה (APP_KEY, לא חובה)
//     והגבלת קצב לפי כתובת IP (LIMITER — לא חובה; USAGE + DAILY_LIMIT — מכסה יומית, לא חובה).
//
// נקודות קצה:
//   POST /v1/exercise   multipart/form-data: meta (JSON: {client, language, note, texts:[{name,text}], names}) + file (כמה שצריך)
//   POST /v1/repair     application/json: {client, language, exercise, files:{שם: קוד}, errors:[...], mode?}
//                       mode: "solution" — ההצעה לפתרון (tasks[].solution) לא נתנה את expectedOutput: מתקנים אותה (או את expectedOutput)
//   language: "java" (ברירת מחדל) או "csharp"
//   GET  /v1/health
//   GET  /dl/windows, /dl/zip   הורדה דרך השרת: סופרים (KV בשם STATS, לא חובה) ומפנים לקובץ ב-GitHub
//   POST /v1/count/windows|zip  האתר מוריד ישירות מ-GitHub ושולח לכאן "נספר" ברקע (sendBeacon) — פתוח לכל אתר
//   GET  /v1/stats              כמה הורדות היו — {downloads: {windows, zip, total}} (פתוח לכל אתר)
//   POST /v1/site/exercise      כמו /v1/exercise, מהאתר (העלאת שאלת בגרות): רק מהכתובות שב-SITE_ORIGINS, בלי מפתח-אפליקציה,
//                               ועם מכסה יומית קטנה יותר (SITE_DAILY_LIMIT, ברירת מחדל 8)
//   POST /v1/license    application/json: {client, machine, code} → {ok, locked, licensed, message, payUrl}
//                       נעילה לתשלום של התצוגה הוויזואלית: כבויה כל עוד PAYWALL לא "on" (ראו README.md — "נעילה לתשלום")

import { Buffer } from 'node:buffer';
import { systemPrompt, repairPrompt, solutionPrompt, language, RESPONSE_SCHEMA } from './prompt.js';

const LIMITS = {
  files: 10,
  fileBytes: 10 * 1024 * 1024,
  totalBytes: 14 * 1024 * 1024,   // ב-base64 זה כ-19MB — מתחת למגבלת 20MB של בקשה ל-Gemini
  textChars: 60000,
  noteChars: 600,
  repairChars: 120000,
};

const TYPES = {
  'application/pdf': b => b[0] === 0x25 && b[1] === 0x50 && b[2] === 0x44 && b[3] === 0x46,            // %PDF
  'image/png': b => b[0] === 0x89 && b[1] === 0x50 && b[2] === 0x4e && b[3] === 0x47,
  'image/jpeg': b => b[0] === 0xff && b[1] === 0xd8 && b[2] === 0xff,
  'image/webp': b => b[0] === 0x52 && b[1] === 0x49 && b[2] === 0x46 && b[3] === 0x46 && b[8] === 0x57 && b[9] === 0x45,   // RIFF....WE
};

const DEFAULT_MODELS = 'gemini-flash-latest,gemini-flash-lite-latest';
const GEMINI_BASE = 'https://generativelanguage.googleapis.com';

const RELEASE = 'https://raw.githubusercontent.com/motiml77/java-lamerhav/master/java-visualizer/release/';
const DOWNLOADS = { windows: RELEASE + 'DSViz-Setup.exe', zip: RELEASE + 'dsviz-install.zip' };
const DEFAULT_SITE_ORIGINS = 'https://motiml77.github.io';

export default {
  async fetch(request, env, ctx) {
    env = env || {};
    let res;
    try {
      res = await handle(request, env, ctx);
    } catch (e) {
      if (e instanceof Fail) res = reply(e.status, { ok: false, code: e.code, error: e.message });
      else {
        console.error('unexpected', e && e.stack || e);
        res = reply(500, { ok: false, code: 'internal', error: 'תקלה בשרת. נסו שוב בעוד דקה.' });
      }
    }
    return withCors(request, env, res);
  },
};

/** האתר קורא מהדפדפן: /v1/stats פתוח לכולם, ו-/v1/site/* רק לכתובות שב-SITE_ORIGINS */
function siteOrigin(request, env) {
  const origin = request.headers.get('origin') || '';
  const allowed = String(env.SITE_ORIGINS || DEFAULT_SITE_ORIGINS).split(',').map(x => x.trim().replace(/\/+$/, '')).filter(Boolean);
  return allowed.includes(origin) ? origin : '';
}

function withCors(request, env, res) {
  const path = new URL(request.url).pathname;
  let origin = '';
  if (path === '/v1/stats') origin = '*';
  else if (path.startsWith('/v1/site/')) origin = siteOrigin(request, env);
  if (!origin) return res;
  const h = new Headers(res.headers);
  h.set('access-control-allow-origin', origin);
  h.set('vary', 'origin');
  return new Response(res.body, { status: res.status, headers: h });
}

class Fail extends Error {
  constructor(status, code, message) { super(message); this.status = status; this.code = code; }
}

function reply(status, obj, extra) {
  return new Response(JSON.stringify(obj), { status, headers: Object.assign({ 'content-type': 'application/json; charset=utf-8', 'cache-control': 'no-store' }, extra || {}) });
}

async function handle(request, env, ctx) {
  const url = new URL(request.url);
  if (request.method === 'GET' && (url.pathname === '/v1/health' || url.pathname === '/')) {
    return reply(200, { ok: true, service: 'dsviz-exercise', models: models(env) });
  }
  const dl = /^\/dl\/(windows|zip)$/.exec(url.pathname);
  if (request.method === 'GET' && dl) {   // הורדה מהאתר: סופרים, ומפנים לקובץ (גם אם הספירה נכשלת)
    const counting = count(env, dl[1]).catch(e => console.error('count', e));
    if (ctx && typeof ctx.waitUntil === 'function') ctx.waitUntil(counting); else await counting;
    return new Response(null, { status: 302, headers: { location: DOWNLOADS[dl[1]], 'cache-control': 'no-store' } });
  }
  const hit = /^\/v1\/count\/(windows|zip)$/.exec(url.pathname);
  if (request.method === 'POST' && hit) {
    const counting = count(env, hit[1]).catch(e => console.error('count', e));
    if (ctx && typeof ctx.waitUntil === 'function') ctx.waitUntil(counting); else await counting;
    return new Response(null, { status: 204, headers: { 'access-control-allow-origin': '*', 'cache-control': 'no-store' } });
  }
  if (request.method === 'GET' && url.pathname === '/v1/stats') return reply(200, await stats(env), { 'cache-control': 'public, max-age=60' });
  if (request.method === 'OPTIONS' && url.pathname.startsWith('/v1/site/')) {
    if (!siteOrigin(request, env)) throw new Fail(403, 'origin', 'האתר הזה לא מורשה');
    return new Response(null, { status: 204, headers: { 'access-control-allow-methods': 'POST', 'access-control-allow-headers': 'content-type', 'access-control-max-age': '86400' } });
  }
  if (request.method !== 'POST') throw new Fail(405, 'method', 'רק POST');
  if (url.pathname === '/v1/license') return reply(200, await license(request, env));
  const site = url.pathname === '/v1/site/exercise';
  if (url.pathname !== '/v1/exercise' && url.pathname !== '/v1/repair' && !site) throw new Fail(404, 'not_found', 'כתובת לא מוכרת');

  if (site) {
    if (!siteOrigin(request, env)) throw new Fail(403, 'origin', 'אפשר לשלוח מכאן רק מהאתר של DSViz.');
  } else if (env.APP_KEY && request.headers.get('x-dsviz-key') !== env.APP_KEY) throw new Fail(401, 'key', 'השרת לא מכיר את התוסף הזה (מפתח שגוי). עדכנו את התוסף.');
  if (!env.GEMINI_API_KEY) throw new Fail(503, 'config', 'השרת עדיין לא הוגדר (חסר מפתח Gemini).');

  const ip = request.headers.get('cf-connecting-ip') || 'local';
  await rateLimit(env, ip, site);

  const declared = +(request.headers.get('content-length') || 0);
  if (declared > LIMITS.totalBytes * 1.5) throw new Fail(413, 'too_big', 'הקבצים גדולים מדי (עד 14MB ביחד).');

  let req;
  if (url.pathname === '/v1/exercise' || site) req = await exerciseParts(request);
  else req = await repairParts(request);

  const result = await askGemini(env, req.userParts, req.lang);
  const exercise = check(result.data, req.previous);
  exercise.language = req.lang;
  return reply(200, { ok: true, model: result.model, exercise });
}

// ------------------------------------------------------------------ נעילה לתשלום (התצוגה הוויזואלית)
//
// PAYWALL = "on" — נועלים; כל ערך אחר (או בלי) — פתוח לכולם (ברירת המחדל).
// קוד רישיון: DSV-<מזהה>-<חתימה>; החתימה = 16 התווים הראשונים (hex) של HMAC-SHA256(LICENSE_SECRET, מזהה).
//   יוצרים קוד: LICENSE_SECRET=... node tools/license.mjs <מזהה, למשל מספר קבלה או כתובת מייל>
// REVOKED = מזהים מבוטלים, מופרדים בפסיק. PAYWALL_MESSAGE / PAY_URL — ההודעה והקישור לתשלום שמוצגים לתלמיד.

const DEFAULT_LOCK_MESSAGE = 'התצוגה הוויזואלית דורשת רישיון. אפשר להמשיך לעבוד ולהריץ כרגיל ב-Eclipse — רק החלון הוויזואלי נעול.';

export async function licenseSignature(secret, id) {
  const key = await crypto.subtle.importKey('raw', new TextEncoder().encode(secret), { name: 'HMAC', hash: 'SHA-256' }, false, ['sign']);
  const sig = new Uint8Array(await crypto.subtle.sign('HMAC', key, new TextEncoder().encode(id)));
  return Array.from(sig.slice(0, 8), b => b.toString(16).padStart(2, '0')).join('');
}

/** בודק קוד רישיון: מחזיר את המזהה אם הוא תקין (ולא בוטל), אחרת null */
export async function verifyLicense(env, code) {
  const m = /^DSV-([A-Za-z0-9._@+-]{1,80})-([0-9a-f]{16})$/.exec(String(code || '').trim());
  if (!m || !env.LICENSE_SECRET) return null;
  const revoked = String(env.REVOKED || '').split(',').map(x => x.trim()).filter(Boolean);
  if (revoked.includes(m[1])) return null;
  return (await licenseSignature(env.LICENSE_SECRET, m[1])) === m[2] ? m[1] : null;
}

async function license(request, env) {
  let body = {};
  try { body = await request.json() || {}; } catch (e) { body = {}; }
  if (String(env.PAYWALL || '').toLowerCase() !== 'on') return { ok: true, locked: false, licensed: false };
  const id = await verifyLicense(env, body.code);
  if (id) return { ok: true, locked: false, licensed: true };
  return { ok: true, locked: true, licensed: false, message: env.PAYWALL_MESSAGE || DEFAULT_LOCK_MESSAGE, payUrl: env.PAY_URL || '' };
}

// ------------------------------------------------------------------ בקשת תרגיל: קבצים + טקסט

async function exerciseParts(request) {
  const type = request.headers.get('content-type') || '';
  if (!type.startsWith('multipart/form-data')) throw new Fail(415, 'format', 'פורמט בקשה לא נכון');
  let form;
  try { form = await request.formData(); } catch (e) { throw new Fail(400, 'format', 'לא הצלחתי לקרוא את הבקשה'); }

  let meta = {};
  const rawMeta = form.get('meta');
  if (typeof rawMeta === 'string' && rawMeta.length < LIMITS.textChars * 4) {
    try { meta = JSON.parse(rawMeta) || {}; } catch (e) { throw new Fail(400, 'format', 'לא הצלחתי לקרוא את הבקשה'); }
  }
  const texts = Array.isArray(meta.texts) ? meta.texts.filter(t => t && typeof t.text === 'string' && t.text.trim()) : [];
  const textChars = texts.reduce((n, t) => n + t.text.length, 0);
  if (textChars > LIMITS.textChars) throw new Fail(413, 'too_big', 'הטקסט ארוך מדי — שלחו רק את התרגיל עצמו.');
  const note = typeof meta.note === 'string' ? meta.note.trim().slice(0, LIMITS.noteChars) : '';
  const names = Array.isArray(meta.names) ? meta.names : [];   // השמות האמיתיים (בעברית) של הקבצים, לפי הסדר
  const lang = language(meta.language);                          // java / csharp — לפי מה שהתלמיד בחר

  const files = form.getAll('file').filter(f => f && typeof f === 'object' && typeof f.arrayBuffer === 'function');
  if (files.length + texts.length === 0) throw new Fail(400, 'empty', 'לא צורף שום קובץ.');
  if (files.length > LIMITS.files) throw new Fail(413, 'too_many', `אפשר לשלוח עד ${LIMITS.files} קבצים.`);
  let total = 0;
  for (const f of files) {
    if (f.size > LIMITS.fileBytes) throw new Fail(413, 'too_big', 'אחד הקבצים גדול מדי (עד 10MB).');
    total += f.size;
  }
  if (total > LIMITS.totalBytes) throw new Fail(413, 'too_big', 'הקבצים גדולים מדי (עד 14MB ביחד).');

  // החלקים לבקשה ל-Gemini — כמחרוזות JSON מוכנות. הקבצים עצמם לא עוברים JSON.parse/stringify (זה איטי מאוד
  // בקבצים גדולים, ול-Worker יש מגבלת זמן מעבד) — ה-base64 נוצר בקוד מקומי ומשורשר כמו שהוא.
  const parts = [JSON.stringify({ text: 'זה דף התרגיל ששלח התלמיד. בנה את המעטפת לפי ההנחיות והתבנית.' })];
  for (const [i, f] of files.entries()) {
    const label = typeof names[i] === 'string' && names[i] ? names[i] : f.name || 'קובץ';
    const buf = new Uint8Array(await f.arrayBuffer());
    const mime = String(f.type || '').toLowerCase().split(';')[0].trim();
    const ok = TYPES[mime];
    if (!ok || buf.length < 12 || !ok(buf)) throw new Fail(415, 'file_type', `סוג הקובץ ${clean(label)} לא נתמך — שלחו PDF, תמונה (PNG/JPG) או Word.`);
    parts.push(JSON.stringify({ text: `[קובץ: ${clean(label)}]` }));
    parts.push('{"inlineData":{"mimeType":"' + mime + '","data":"' + toBase64(buf) + '"}}');
  }
  for (const t of texts) parts.push(JSON.stringify({ text: `[טקסט מתוך ${clean(t.name || 'קובץ')}]\n${t.text}` }));
  if (note) parts.push(JSON.stringify({ text: `הערה מהתלמיד (לגבי מה לבנות, לא הוראות כלליות): ${note}` }));
  return { userParts: parts, lang, previous: null };
}

// ------------------------------------------------------------------ תיקון: הקוד שנבנה לא התקמפל

async function repairParts(request) {
  const text = await request.text();
  if (text.length > LIMITS.repairChars) throw new Fail(413, 'too_big', 'הבקשה גדולה מדי.');
  let body;
  try { body = JSON.parse(text); } catch (e) { throw new Fail(400, 'format', 'לא הצלחתי לקרוא את הבקשה'); }
  const previous = body && body.exercise;
  const errors = Array.isArray(body && body.errors) ? body.errors.map(String).slice(0, 40) : [];
  const files = body && body.files && typeof body.files === 'object' ? body.files : {};
  if (!previous || typeof previous !== 'object' || !errors.length) throw new Fail(400, 'format', 'חסרים פרטים לתיקון');
  const lang = language(body.language || (previous && previous.language));
  const solution = body.mode === 'solution';
  let msg = (solution ? solutionPrompt(lang) : repairPrompt(lang)) + '\n\nPrevious JSON:\n' + JSON.stringify(previous) + '\n';
  for (const [name, code] of Object.entries(files).slice(0, 12)) msg += `\n----- ${clean(name)} -----\n${String(code)}\n`;
  msg += (solution ? '\nWhat happened:\n' : '\nCompiler errors:\n') + errors.map(e => '- ' + e).join('\n');
  return { userParts: [JSON.stringify({ text: msg })], previous, lang };
}

// ------------------------------------------------------------------ Gemini

function models(env) {
  return String(env.MODELS || DEFAULT_MODELS).split(',').map(s => s.trim()).filter(Boolean);
}

async function askGemini(env, userParts, lang) {
  const cfg = {
    responseMimeType: 'application/json',
    responseSchema: RESPONSE_SCHEMA,
    temperature: 0.2,
    maxOutputTokens: 32768,
  };
  const body = '{"systemInstruction":' + JSON.stringify({ parts: [{ text: systemPrompt(lang) }] })
    + ',"contents":[{"role":"user","parts":[' + userParts.join(',') + ']}]'
    + ',"generationConfig":' + JSON.stringify(cfg) + '}';
  const base = env.GEMINI_BASE || GEMINI_BASE;
  let last = null, busy = false;
  for (const model of models(env)) {
    let res;
    try {
      res = await fetch(`${base}/v1beta/models/${encodeURIComponent(model)}:generateContent`, {
        method: 'POST',
        headers: { 'content-type': 'application/json', 'x-goog-api-key': env.GEMINI_API_KEY },
        body,
        signal: AbortSignal.timeout(+(env.TIMEOUT_MS || 100000)),
      });
    } catch (e) {
      last = `${model}: ${e && e.name === 'TimeoutError' ? 'timeout' : String(e)}`;
      continue;
    }
    const raw = await res.text();
    if (res.status === 429 || res.status >= 500 || res.status === 404) {   // מכסה נגמרה / עומס / מודל לא קיים — מנסים את הבא
      if (res.status === 429 || res.status === 503) busy = true;
      last = `${model}: ${res.status} ${raw.slice(0, 300)}`;
      continue;
    }
    if (!res.ok) {
      console.error('gemini', model, res.status, raw.slice(0, 500));
      throw new Fail(502, 'gemini', 'Gemini לא הצליח לקרוא את הקבצים. נסו לשלוח צילום מסך ברור או PDF.');
    }
    let data = null, finish = '';
    try {
      const j = JSON.parse(raw);
      const cand = j.candidates && j.candidates[0];
      finish = cand && cand.finishReason || (j.promptFeedback && j.promptFeedback.blockReason) || '';
      const text = (cand && cand.content && cand.content.parts || []).filter(p => !p.thought && typeof p.text === 'string').map(p => p.text).join('');
      data = JSON.parse(stripFence(text));
    } catch (e) {
      last = `${model}: bad json (${finish})`;
      continue;
    }
    return { data, model };
  }
  console.error('all models failed', last);
  if (busy) throw new Fail(429, 'quota', 'הגענו למכסה של Gemini לעכשיו. נסו שוב בעוד דקה (או מחר, אם נגמרה המכסה היומית).');
  throw new Fail(502, 'gemini', 'לא התקבלה תשובה תקינה מ-Gemini. נסו שוב.');
}

function stripFence(t) {
  const s = String(t || '').trim();
  const m = /^```[A-Za-z#]*\s*([\s\S]*?)\s*```$/.exec(s);   // גם ```java
  return m ? m[1] : s;
}

// ------------------------------------------------------------------ בדיקה בסיסית של התשובה (הבדיקה המלאה — בתוסף)

function check(d, previous) {
  if (!d || typeof d !== 'object') throw new Fail(502, 'gemini', 'לא התקבלה תשובה תקינה מ-Gemini. נסו שוב.');
  if (d.isExercise === false) {
    throw new Fail(422, 'not_exercise', String(d.problem || 'לא מצאתי בקבצים תרגיל תכנות. שלחו את דף התרגיל (צילום ברור או PDF).').slice(0, 400));
  }
  const arr = k => (Array.isArray(d[k]) ? d[k] : []);
  const out = {
    title: str(d.title, 120), summary: str(d.summary, 600),
    topic: str(d.topic || d.packageName, 60), mainClass: str(d.mainClass, 60),
    imports: arr('imports').map(x => str(x, 80)).slice(0, 20),
    classes: arr('classes').filter(c => c && c.name && c.code).map(c => ({ name: str(c.name, 60), code: str(c.code, 20000) })).slice(0, 12),
    tasks: arr('tasks').filter(t => t && t.methodName).map(t => ({
      label: str(t.label, 20), kind: t.kind === 'constructor' ? 'constructor' : 'method',
      className: str(t.className, 60), isStatic: t.isStatic !== false, returnType: str(t.returnType, 80),
      methodName: str(t.methodName, 60),
      parameters: (Array.isArray(t.parameters) ? t.parameters : []).filter(p => p && p.name).map(p => ({ type: str(p.type, 80), name: str(p.name, 40) })).slice(0, 12),
      instructions: str(t.instructions, 2000),
      solution: stripFence(str(t.solution, 12000)),
    })).slice(0, 25),
    givenCode: arr('givenCode').filter(g => g && g.code).map(g => ({ className: str(g.className, 60), code: str(g.code, 20000) })).slice(0, 20),
    otherQuestions: arr('otherQuestions').filter(q => q && q.text).map(q => ({ label: str(q.label, 20), text: str(q.text, 2000) })).slice(0, 20),
    mainCode: str(d.mainCode, 30000),
    expectedOutput: str(d.expectedOutput, 4000),
  };
  if (!out.tasks.length && !out.otherQuestions.length) {
    throw new Fail(422, 'not_exercise', 'לא מצאתי בתרגיל סעיפים שצריך לכתוב בהם קוד.');
  }
  if (previous && !out.tasks.length) throw new Fail(502, 'gemini', 'התיקון שהתקבל לא תקין.');
  return out;
}

function str(v, max) { return typeof v === 'string' ? v.slice(0, max) : ''; }
function clean(s) { return String(s).replace(/[\u0000-\u001f"\\]/g, ' ').slice(0, 120); }

function toBase64(u8) {
  if (typeof u8.toBase64 === 'function') return u8.toBase64();
  return Buffer.from(u8.buffer, u8.byteOffset, u8.byteLength).toString('base64');
}

// ------------------------------------------------------------------ הגבלת קצב

// ------------------------------------------------------------------ מונה הורדות (KV בשם STATS — לא חובה)

async function count(env, kind) {
  if (!env.STATS || typeof env.STATS.get !== 'function') return;
  const n = +(await env.STATS.get('dl:' + kind)) || 0;   // KV לא אטומי — בעומס גבוה אולי נאבדת ספירה פה ושם, וזה בסדר למונה
  await env.STATS.put('dl:' + kind, String(n + 1));
}

async function stats(env) {
  if (!env.STATS || typeof env.STATS.get !== 'function') return { ok: true, counting: false, downloads: null };
  const windows = +(await env.STATS.get('dl:windows')) || 0, zip = +(await env.STATS.get('dl:zip')) || 0;
  const base = +(env.DOWNLOADS_BEFORE || 0);   // הורדות מלפני שהמונה התחיל (לא חובה)
  return { ok: true, counting: true, downloads: { windows, zip, total: windows + zip + base } };
}

async function rateLimit(env, ip, site) {
  if (env.LIMITER && typeof env.LIMITER.limit === 'function') {   // Cloudflare Rate Limiting (לפי דקה)
    const { success } = await env.LIMITER.limit({ key: ip });
    if (!success) throw new Fail(429, 'rate', 'יותר מדי שליחות בדקה האחרונה. חכו דקה ונסו שוב.');
  }
  if (env.USAGE && typeof env.USAGE.get === 'function') {          // מכסה יומית לכל כתובת (KV)
    const day = new Date().toISOString().slice(0, 10);
    const key = `${site ? 's' : 'd'}:${day}:${ip}`;
    const n = +(await env.USAGE.get(key)) || 0;
    if (n >= +(site ? env.SITE_DAILY_LIMIT || 8 : env.DAILY_LIMIT || 40)) throw new Fail(429, 'daily', 'הגעתם למספר השליחות המרבי להיום מהמחשב הזה. נסו שוב מחר.');
    await env.USAGE.put(key, String(n + 1), { expirationTtl: 60 * 60 * 36 });
  }
}
