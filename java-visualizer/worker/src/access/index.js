// כניסה למאגר התרגילים ולשליחת תרגיל: מורים, עמדות מחשב, תלמידים, ומסוף ההנהלה.
//
// מי מחליט: רק השרת. Firestore סגור לכל מחשב (כללי האבטחה: הכול חסום) — השרת ניגש בשם חשבון השירות.
//   • מורה      — חשבון Google שההנהלה הגדירה (teachers/{email}). קוד קבוע. רואה את העמדות; מוסיף עמדות רק בקניית חבילה.
//   • עמדת מחשב — קוד המורה + שם למחשב. היתר אחד יורד מהמורה, בטרנזקציה. אותו מחשב שמתחבר שוב — אותו היתר.
//   • תלמיד     — חשבון Google + תשלום (או הענקה ידנית מההנהלה): students/{email}.accessUntil.
//   • הנהלה     — רק כתובות שב-ADMIN_EMAILS (בהגדרות השרת, לא ב-Firestore — אף אחד לא יכול "להוסיף את עצמו").
//
// הכניסה במחשב: "כרטיס" (token) ששמור במחשב, ו"אישור" (pass) חתום — אחרי הכניסה הראשונה (עם אינטרנט) המאגר נפתח
// בלי אינטרנט (offlineDays = 0: בלי הגבלה). כשיש אינטרנט התוסף בודק שוב, וכך עמדה ששוחררה / מורה שהושבת ננעלים. החתימה: RSA (PASS_KEY — סוד בשרת); המפתח הציבורי בתוך התוספים.
//
// כבוי כל עוד ACCESS לא "on" (וכל עוד אין FIREBASE_PROJECT_ID) — אז השרת עובד בדיוק כמו קודם.

import { Fail, reply } from '../http.js';
import { db, timeId } from './firestore.js';
import { verifyIdToken, AuthError, projectId, firestoreEmulator } from './google.js';
import { sha256, sha256Hex, randomToken, teacherCode, normalizeCode, showCode, sameText, b64url, b64urlJson, importRsaPrivate, signRs256, publicSpki } from './crypto.js';
import { payProvider, PAY_PROVIDERS } from './pay.js';
import { servePage } from './pages.js';

export const DEFAULTS = {
  price: 0,                     // ₪ — 0 = עוד לא נקבע (אי אפשר לשלם)
  accessUntil: '2027-08-31',    // תשלום פותח את המאגר עד התאריך הזה
  offlineDays: 0,               // ימים שהמאגר נפתח בלי אינטרנט — 0 = בלי הגבלה (רק הכניסה הראשונה צריכה אינטרנט)
  studentDevices: 3,
  teacherDevices: 5,
  defaultSeats: 0,              // מורה חדש מקבל — ואת השאר קונה בחבילות
  packages: [{ seats: 10, price: 0 }, { seats: 20, price: 0 }, { seats: 30, price: 0 }],   // חבילות עמדות למורים (מחיר 0 = לא נמכרת)
  requireLoginForGemini: false, // שליחת תרגיל ל-Gemini רק למחוברים
  monthlyPerSeat: 4,            // שליחות בחודש לכל עמדה של המורה (מנה משותפת: המורה + כל העמדות שלו)
  monthlyStudent: 30,           // שליחות בחודש לתלמיד רשום (מייל)
};
const SETTING_LIMITS = {
  price: [0, 5000], offlineDays: [0, 365], studentDevices: [1, 20], teacherDevices: [1, 50], defaultSeats: [0, 2000],
  monthlyPerSeat: [0, 1000], monthlyStudent: [0, 5000],
};

const SESSION_TOUCH_MS = 6 * 3600 * 1000;   // מעדכנים "נראה לאחרונה" לכל היותר פעם ב-6 שעות (חוסך כתיבות)
let settingsCache = { at: 0, pid: '', value: null };
let keyCache = { pem: '', key: null, spki: '' };

export function accessEnabled(env) {
  return String(env.ACCESS || '').toLowerCase() === 'on' && !!projectId(env);
}

// ------------------------------------------------------------------ עזרים

const nowIso = () => new Date().toISOString();
const today = () => nowIso().slice(0, 10);
const month = () => nowIso().slice(0, 7);
const clip = (s, n) => String(s == null ? '' : s).replace(/[\u0000-\u001f\u007f]/g, ' ').trim().slice(0, n);
const normEmail = e => String(e || '').trim().toLowerCase();
const emailOk = e => /^[^\s@/]{1,64}@[^\s@/]{1,190}\.[^\s@/.]{2,}$/.test(e);
const dateOk = d => /^\d{4}-\d{2}-\d{2}$/.test(String(d || '')) && !isNaN(Date.parse(d));
/** טלפון נייד ישראלי תקני: 10 ספרות שמתחילות ב-05 (מקבל גם רווחים, מקפים, סוגריים ו-‎+972). מחזיר את 10 הספרות או '' */
export function phoneNorm(p) {
  let d = String(p == null ? '' : p).replace(/[\s\-().]/g, '');
  if (/^\+?972/.test(d)) d = '0' + d.replace(/^\+?972/, '');
  return /^05\d{8}$/.test(d) ? d : '';
}
const machineOk = m => /^[a-f0-9]{16,64}$/.test(String(m || ''));
const intIn = (v, lo, hi) => Number.isInteger(+v) && +v >= lo && +v <= hi;

async function readJson(request) {
  const text = await request.text();
  if (text.length > 200000) throw new Fail(413, 'too_big', 'הבקשה גדולה מדי.');
  try { return text ? JSON.parse(text) || {} : {}; } catch (e) { throw new Fail(400, 'format', 'לא הצלחתי לקרוא את הבקשה'); }
}

export function admins(env) {
  return String(env.ADMIN_EMAILS || '').split(',').map(normEmail).filter(Boolean);
}

export async function settings(env, fresh) {
  const pid = projectId(env);
  if (!fresh && settingsCache.value && settingsCache.pid === pid && Date.now() - settingsCache.at < 30000) return settingsCache.value;
  const d = await db(env).get('config/settings');
  const value = Object.assign({}, DEFAULTS, d || {});
  settingsCache = { at: Date.now(), pid, value };
  return value;
}

async function passKey(env) {
  const pem = env.PASS_KEY || '';
  if (!pem) return null;
  if (keyCache.pem !== pem) keyCache = { pem, key: await importRsaPrivate(pem), spki: '' };
  return keyCache.key;
}

/** התוכנה ששלחה (לא חובה; לקוחות ישנים לא שולחים): dsviz — Eclipse / IntelliJ / Visual Studio / VS Code, automata — "מודלים חישוביים" */
const APPS = ['dsviz', 'automata'];
function appOf(v) {
  const a = String(v || '').trim().toLowerCase();
  return APPS.includes(a) ? a : '';
}
/** ביומן הפעילות: "app=automata" (ריק כשהלקוח לא שלח) */
function appDetail(app, more) {
  return [app ? 'app=' + app : '', more || ''].filter(Boolean).join(' · ');
}

async function log(store, actor, action, target, detail) {
  try {
    await store.set('log/' + timeId(), { at: nowIso(), actor: clip(actor, 120), action, target: clip(target, 200), detail: clip(detail, 400) });
  } catch (e) {
    console.error('log', e && e.message);
  }
}

async function webUser(request, env) {
  const m = /^Bearer\s+(\S+)$/.exec(request.headers.get('authorization') || '');
  if (!m) throw new Fail(401, 'login', 'צריך להתחבר עם Google.');
  try {
    return await verifyIdToken(env, m[1]);
  } catch (e) {
    if (e instanceof AuthError) throw new Fail(401, 'login', 'הכניסה פגה — מתחברים שוב.');
    throw e;
  }
}

async function adminUser(request, env) {
  const u = await webUser(request, env);
  if (!admins(env).includes(u.email)) throw new Fail(403, 'admin', 'החשבון הזה לא מוגדר כמנהל.');
  return u;
}

// ------------------------------------------------------------------ אישור חתום (pass)

/**
 * האישור שהמחשב שומר: base64url(JSON).base64url(RSA-SHA256). התוסף בודק את החתימה עם המפתח הציבורי,
 * שהמחשב הוא אותו מחשב (mid), ושהתאריך לא עבר (exp — לפי השעון של השרת ביום האישור).
 */
async function makePass(env, s, p) {
  const key = await passKey(env);
  if (!key) return '';
  const now = Math.floor(Date.now() / 1000);
  let exp = now + (s.offlineDays > 0 ? s.offlineDays : 3650) * 86400;   // 0 = בלי הגבלה (10 שנים); הנעילה מגיעה בבדיקה הבאה עם אינטרנט
  if (p.until) exp = Math.min(exp, Math.floor(Date.parse(p.until + 'T23:59:59Z') / 1000));
  // apps: באילו תוכנות האישור פותח (בינתיים תמיד שתיהן). לקוחות ישנים מתעלמים מהשדה
  const apps = Array.isArray(p.apps) && p.apps.length ? p.apps.filter(a => APPS.includes(a)) : APPS.slice();
  const payload = { v: 1, kind: p.kind, sub: p.sub, name: p.name || '', email: p.email || '', until: p.until || '', mid: p.machine || '', apps, iat: now, exp };
  if (p.teacher) payload.teacher = p.teacher;
  const text = b64urlJson(payload);
  return text + '.' + b64url(await signRs256(key, text));
}

// ------------------------------------------------------------------ כרטיסי כניסה (token)

/** u.<sid>.<secret> — מורה / תלמיד (נשמר אצל המשתמש);  s.<stationId>.<secret> — עמדת מחשב (לכל המחשב) */
function parseToken(token) {
  const m = /^([us])\.([A-Za-z0-9_-]{8,64})\.([A-Za-z0-9_-]{20,100})$/.exec(String(token || '').trim());
  return m ? { kind: m[1], id: m[2], secret: m[3] } : null;
}

async function teacherView(t, stations) {
  return {
    email: t.email, name: t.name || '', school: t.school || '', code: showCode(t.code), seats: t.seats || 0, used: t.used || 0, active: !!t.active,
    stations: stations ? stations.map(x => ({ id: x._id, name: x.name, claimedAt: x.claimedAt, lastSeen: x.lastSeen })) : undefined,
  };
}

/*
 * עמדה אחת = מחשב אחד, אבל במחשב אחד יש כמה חשבונות Windows וכמה תוכנות. כל אחד מהם יכול להתחבר שוב עם הקוד (למשל כשהקובץ
 * השמור נמצא אצל משתמש אחר), ולפעמים עם מזהה מחשב אחר (המזהה האקראי נשמר אצל כל משתמש בנפרד כשאין הרשאה לתיקייה המשותפת).
 * פעם כל התחברות כזו החליפה את הכרטיס של העמדה, והכרטיס של המשתמש הקודם נדחה ("העמדה השתחררה לבד"). עכשיו העמדה מחזיקה
 * את הכרטיסים האחרונים (tokens) ואת המזהים האחרונים (machines), וכולם תקפים עד שהמורה או ההנהלה משחררים אותה.
 */
const STATION_TOKENS = 8, STATION_MACHINES = 8;
const stationTokens = st => [st.tokenHash].concat(Array.isArray(st.tokens) ? st.tokens : []).filter(Boolean);
const stationMachines = st => [st.machine].concat(Array.isArray(st.machines) ? st.machines : []).filter(Boolean);
const uniq = a => Array.from(new Set(a));
/** למה המחשב מתחבר שוב (התוכנה שולחת; נרשם ביומן, כדי שיהיה אפשר לראות למה עמדה ביקשה קוד מחדש) */
const CLAIM_WHY = {
  none: 'אין במחשב כניסה שמורה (התקנה חדשה, משתמש Windows אחר, או מחשב ששוחזר)',
  unreadable: 'הכניסה השמורה לא נפתחה (מזהה המחשב השתנה, או שהקובץ פגום)',
  pass: 'האישור השמור לא אומת (תאריך המחשב, או גרסה ישנה)',
  'server-invalid': 'השרת דחה את הכרטיס השמור',
  'server-revoked': 'העמדה שוחררה (מהמורה או מההנהלה)',
};
const REJECT_WHY = { gone: 'אין עמדה כזו', released: 'העמדה שוחררה', token: 'הכרטיס לא שייך לעמדה (התחברות חדשה החליפה אותו)', machine: 'מזהה המחשב שונה מזה של העמדה' };

/** בודק כרטיס: מי זה, והאם מותר לו עכשיו. status: ok / pay / blocked / not-teacher / revoked / invalid */
export async function resolveToken(env, token, machine) {
  const store = db(env);
  const p = parseToken(token);
  if (!p) return { status: 'invalid' };
  const hash = await sha256Hex(p.secret);
  if (p.kind === 's') {
    const st = await store.get('stations/' + p.id);
    // why (ליומן ולתוכנה): gone — אין עמדה כזו; released — שוחררה; token — הכרטיס לא של העמדה; machine — מחשב אחר
    if (!st) return { status: 'invalid', why: 'gone', stationId: p.id };
    if (!st.active) return { status: 'revoked', why: 'released', stationId: p.id, stationDoc: st };
    if (!stationTokens(st).some(h => sameText(h, hash))) return { status: 'invalid', why: 'token', stationId: p.id, stationDoc: st };
    const machines = stationMachines(st);
    if (machine && machines.length && !machines.includes(machine)) return { status: 'invalid', why: 'machine', stationId: p.id, stationDoc: st };
    const t = await store.get('teachers/' + st.teacher);
    // האישור נחתם למזהה שהמחשב שלח (אחד מהמזהים של העמדה); לקוח שלא שלח — המזהה האחרון
    const base = { kind: 'station', subject: 'station:' + p.id, station: { id: p.id, name: st.name, teacher: st.teacher }, machine: machine && machines.includes(machine) ? machine : st.machine,
      profile: { role: 'station', name: st.name, email: '' }, teacher: t ? { name: t.name || '', school: t.school || '' } : null };
    if (!t || !t.active) return Object.assign(base, { status: 'blocked' });
    if (!st.lastSeen || Date.now() - Date.parse(st.lastSeen) > SESSION_TOUCH_MS) {
      await store.merge('stations/' + p.id, { lastSeen: nowIso() }).catch(() => {});
    }
    return Object.assign(base, { status: 'ok' });
  }
  const se = await store.get('sessions/' + p.id);
  if (!se || se.revoked || !sameText(se.tokenHash, hash)) return { status: se && se.revoked ? 'revoked' : 'invalid' };
  if (machine && se.machine && se.machine !== machine) return { status: 'invalid' };
  const profile = { role: se.role, email: se.email, name: se.name || '' };
  const touch = () => (!se.lastSeen || Date.now() - Date.parse(se.lastSeen) > SESSION_TOUCH_MS
    ? store.merge('sessions/' + p.id, { lastSeen: nowIso() }).catch(() => {}) : null);
  if (se.role === 'teacher') {
    const t = await store.get('teachers/' + se.email);
    const base = { kind: 'teacher', subject: 'teacher:' + se.email, session: p.id, machine: se.machine, profile };
    if (!t) return Object.assign(base, { status: 'not-teacher' });
    if (!t.active) return Object.assign(base, { status: 'blocked' });
    await touch();
    return Object.assign(base, { status: 'ok', teacher: await teacherView(t) });
  }
  const st = await store.get('students/' + se.email);
  const until = st && st.accessUntil || '';
  await touch();
  return { kind: 'student', subject: 'student:' + se.email, session: p.id, machine: se.machine, profile, until,
    status: until && until >= today() ? 'ok' : 'pay' };
}

async function accessReply(env, r, extra) {
  const s = await settings(env);
  const out = { ok: true, status: r.status, profile: r.profile || null };
  if (r.teacher) out.teacher = r.teacher;
  if (r.station) out.station = { id: r.station.id, name: r.station.name };
  if (r.until) out.until = r.until;
  if (r.why) out.why = r.why;
  Object.assign(out, extra || {});
  if (r.status === 'ok') {
    out.pass = await makePass(env, s, {
      kind: r.kind, sub: r.subject, name: r.profile && r.profile.name, email: r.profile && r.profile.email, machine: r.machine,
      until: r.kind === 'student' ? r.until : '', teacher: r.kind === 'station' && r.teacher ? r.teacher.name : undefined,
    });
  }
  if (r.status === 'pay') Object.assign(out, { price: s.price, accessUntil: s.accessUntil, canPay: s.price > 0 && !!payProvider(env) });
  return out;
}

// ------------------------------------------------------------------ כניסה עם Google (מהדפדפן) → קוד חד-פעמי → התוסף

/**
 * הדף /signin (בדפדפן) שולח לכאן את הכניסה של Google ואת ה-challenge של התוסף, ומקבל קוד חד-פעמי לחמש דקות.
 * הדף מעביר את הקוד לתוסף (http://127.0.0.1:port), והתוסף מממש אותו עם ה-verifier — רק מי שיצר את ה-challenge יכול.
 */
async function exchange(request, env) {
  const b = await readJson(request);
  const user = await verifyIdToken(env, b.idToken).catch(e => { throw new Fail(401, 'login', 'הכניסה עם Google לא הצליחה. נסו שוב.'); });
  const role = b.role === 'teacher' ? 'teacher' : 'student';
  if (!/^[A-Za-z0-9_-]{43}$/.test(String(b.challenge || ''))) throw new Fail(400, 'format', 'חסר קוד מהתוכנה — פותחים את הכניסה מתוך DSViz.');
  const code = randomToken(24);
  await db(env).set('authCodes/' + code, {
    uid: user.uid, email: user.email, name: user.name, picture: user.picture, role, challenge: b.challenge,
    exp: Date.now() + 5 * 60 * 1000, createdAt: nowIso(),
  });
  return { ok: true, code };
}

async function newSession(env, s, who, role, machine, machineName, client) {
  const store = db(env);
  const limit = role === 'teacher' ? s.teacherDevices : s.studentDevices;
  const mine = (await store.query('sessions', { where: [['email', '==', who.email]] })).filter(x => !x.revoked);
  const same = mine.filter(x => x.machine === machine);
  const others = mine.filter(x => x.machine !== machine);
  if (others.length >= limit) {
    return { status: 'devices', limit, devices: others.map(x => ({ name: x.machineName || '', lastSeen: x.lastSeen || x.createdAt })) };
  }
  const sid = randomToken(12), secret = randomToken(32);
  const at = nowIso();
  await store.commit(async w => {
    for (const x of same) w.update('sessions/' + x._id, { revoked: true, revokedAt: at, revokedBy: 'same-machine' });
    w.create('sessions/' + sid, {
      email: who.email, uid: who.uid || '', name: who.name || '', role, machine, machineName: clip(machineName, 60), client: clip(client, 60),
      tokenHash: await sha256Hex(secret), revoked: false, createdAt: at, lastSeen: at,
    });
  });
  return { status: 'new', token: 'u.' + sid + '.' + secret, sid };
}

/** התוסף מממש את הקוד: מקבל כרטיס (אם מותר) ואת המצב — ok / pay / not-teacher / blocked / devices */
async function redeem(request, env) {
  const b = await readJson(request);
  const store = db(env);
  const code = String(b.code || '');
  if (!/^[A-Za-z0-9_-]{20,64}$/.test(code)) throw new Fail(400, 'format', 'קוד כניסה לא תקין');
  if (!machineOk(b.machine)) throw new Fail(400, 'format', 'חסר מזהה מחשב');
  const doc = await store.tx(async t => {
    const d = await t.get('authCodes/' + code);
    if (d) t.remove('authCodes/' + code);   // פעם אחת בלבד
    return d;
  });
  if (!doc || doc.exp < Date.now()) throw new Fail(400, 'expired', 'הכניסה פגה. בחלון של DSViz לוחצים "חזרה" ושוב "כניסה עם Google".');
  const want = b64url(await sha256(String(b.verifier || '')));
  if (!sameText(want, doc.challenge)) throw new Fail(400, 'expired', 'הכניסה לא תואמת לחלון הזה. בחלון של DSViz לוחצים "חזרה" ושוב "כניסה עם Google".');
  const s = await settings(env);
  const who = { email: doc.email, uid: doc.uid, name: doc.name };
  const profile = { role: doc.role, email: doc.email, name: doc.name || '', picture: doc.picture || '' };

  if (doc.role === 'teacher') {
    const t = await store.get('teachers/' + doc.email);
    if (!t) return { ok: true, status: 'not-teacher', profile };
    if (!t.active) return { ok: true, status: 'blocked', profile };
  }
  const ses = await newSession(env, s, who, doc.role, b.machine, b.machineName, b.client);
  if (ses.status === 'devices') return { ok: true, status: 'devices', profile, limit: ses.limit, devices: ses.devices };
  if (doc.role === 'student') {
    await store.merge('students/' + doc.email, { email: doc.email, uid: doc.uid, name: doc.name || '', lastSignIn: nowIso() });
  } else {
    await store.merge('teachers/' + doc.email, { lastSignIn: nowIso(), uid: doc.uid });
  }
  await log(store, doc.email, doc.role === 'teacher' ? 'signin.teacher' : 'signin.student', clip(b.machineName, 60), appDetail(appOf(b.app)));
  const r = await resolveToken(env, ses.token, b.machine);
  return accessReply(env, r, { token: ses.token });
}

async function check(request, env) {
  const b = await readJson(request);
  const r = await resolveToken(env, b.token, machineOk(b.machine) ? b.machine : '');
  if (r.stationDoc && r.why) await logStationReject(env, r).catch(() => {});
  return accessReply(env, r);
}

/** עמדה קיימת שהכרטיס שלה נדחה: נרשם ביומן (פעם ביום לכל עמדה וסיבה), כדי שיהיה אפשר לראות למה מחשב ביקש קוד מחדש */
async function logStationReject(env, r) {
  const st = r.stationDoc, mark = today() + ':' + r.why;
  if (st.rejectLog === mark) return;
  const store = db(env);
  await store.merge('stations/' + r.stationId, { rejectLog: mark });
  await log(store, 'מחשב: ' + (st.name || ''), 'station.rejected', st.teacher || '', REJECT_WHY[r.why] || r.why);
}

async function logout(request, env) {
  const b = await readJson(request);
  const p = parseToken(b.token);
  if (p && p.kind === 'u') {
    const store = db(env);
    const se = await store.get('sessions/' + p.id);
    if (se && sameText(se.tokenHash, await sha256Hex(p.secret)) && !se.revoked) {
      await store.update('sessions/' + p.id, { revoked: true, revokedAt: nowIso(), revokedBy: 'logout' });
    }
  }
  return { ok: true };
}

// ------------------------------------------------------------------ עמדת מחשב: קוד המורה

async function claim(request, env) {
  const b = await readJson(request);
  const store = db(env);
  const code = normalizeCode(b.code);
  const name = clip(b.name, 60);
  if (!machineOk(b.machine)) throw new Fail(400, 'format', 'חסר מזהה מחשב');
  if (!name) throw new Fail(400, 'name', 'כתבו שם למחשב — למשל "מעבדה 2 · עמדה 14".');
  if (!code) {
    // קוד שלא בפורמט (לא 4 אותיות/ספרות מהקוד של המורה) — נרשם ונספר כמו קוד שגוי
    const lim = await badCodeLimit(env, request, true);
    await log(store, 'מחשב: ' + name, 'station.bad-code', '', badCodeDetail(b.code, '', lim));
    return { ok: true, status: 'bad-code' };
  }
  await badCodeLimit(env, request, false);
  const secret = randomToken(32);
  const hash = await sha256Hex(secret);
  const at = nowIso();
  // stable: מזהה קבוע של המחשב עצמו (בלי החלק האקראי שנשמר בקובץ). לפיו מזהים שזה אותו מחשב גם כשהמזהה הרגיל התחלף
  // (מחשב ששוחזר, קובץ שנמחק, משתמש Windows אחר) — ואז זו אותה עמדה, ולא נלקח עוד מקום מהמורה
  const stable = machineOk(b.stable) && b.stable !== b.machine ? b.stable : '';
  const res = await store.tx(async t => {
    const c = await t.get('teacherCodes/' + code);
    if (!c) return { status: 'bad-code' };
    let stationId = (await sha256Hex(c.teacher + '|' + b.machine)).slice(0, 32);
    const keyId = stable ? (await sha256Hex(c.teacher + '|stable|' + stable)).slice(0, 32) : '';
    let [teacher, st, key] = await t.getMany(['teachers/' + c.teacher, 'stations/' + stationId].concat(keyId ? ['stationKeys/' + keyId] : []));
    if (!teacher || teacher.code !== code) return { status: 'bad-code' };
    if (!teacher.active) return { status: 'blocked', teacher };
    let same = '';
    if (!(st && st.active) && key && key.station && key.station !== stationId) {
      const other = await t.get('stations/' + key.station);
      if (other && other.active && other.teacher === c.teacher) { st = other; stationId = key.station; same = 'stable'; }
    }
    if (keyId && !(key && key.station === stationId)) t.set('stationKeys/' + keyId, { station: stationId, teacher: c.teacher, at });
    if (st && st.active) {   // אותו מחשב מתחבר שוב (התקנה מחדש, משתמש אחר, תוכנה אחרת) — אותו היתר; הכרטיסים הקודמים נשארים תקפים
      t.update('stations/' + stationId, {
        tokenHash: hash, tokens: uniq(stationTokens(st)).slice(0, STATION_TOKENS - 1),
        machine: b.machine, machines: uniq([b.machine].concat(stationMachines(st))).slice(0, STATION_MACHINES),
        name, lastSeen: at, client: clip(b.client, 60), againAt: at, againCount: (st.againCount || 0) + 1,
      });
      return { status: 'ok', stationId, teacher, again: true, same };
    }
    if ((teacher.used || 0) >= (teacher.seats || 0)) return { status: 'full', teacher };
    t.set('stations/' + stationId, {
      teacher: c.teacher, teacherName: teacher.name || '', name, machine: b.machine, machines: [b.machine], tokenHash: hash, tokens: [], active: true,
      claimedAt: at, lastSeen: at, client: clip(b.client, 60),
    });
    t.update('teachers/' + c.teacher, { updatedAt: at }, { used: 1 });
    return { status: 'ok', stationId, teacher };
  });
  if (res.status === 'bad-code') {
    const lim = await badCodeLimit(env, request, true);
    // לאיזה מורה זה כנראה שייך: קוד של מורה ששונה בתו אחד (או שני תווים סמוכים שהתחלפו) — כמעט תמיד טעות הקלדה
    const near = await nearTeacher(store, code);
    await log(store, 'מחשב: ' + name, 'station.bad-code', near.join(', '),
      badCodeDetail(b.code, code, lim) + (near.length ? ' · כנראה טעות הקלדה בקוד של ' + (near.length > 1 ? 'אחד המורים' : 'המורה') : ''));
    return { ok: true, status: 'bad-code' };
  }
  const tv = res.teacher ? { name: res.teacher.name || '', school: res.teacher.school || '', seats: res.teacher.seats || 0, used: (res.teacher.used || 0) + (res.status === 'ok' && !res.again ? 1 : 0) } : null;
  if (res.status !== 'ok') return { ok: true, status: res.status, teacher: tv };
  const why = CLAIM_WHY[String(b.why || '')] || '';
  await log(store, 'מחשב: ' + name, res.again ? 'station.again' : 'station.claim', res.teacher.email,
    appDetail(appOf(b.app), `${tv.used}/${tv.seats}`) + (res.same === 'stable' ? ' · אותו מחשב עם מזהה חדש' : '') + (res.again && why ? ' · ' + why : ''));
  const token = 's.' + res.stationId + '.' + secret;
  const r = await resolveToken(env, token, b.machine);
  return accessReply(env, r, { token, teacher: tv });
}

/** קוד שגוי שוב ושוב מאותה כתובת: עד 30 בשעה (KV בשם USAGE, ואם אין — STATS) */
/** מגבלת ניסיונות עם קוד שגוי (30 בשעה מאותה כתובת). מחזיר {count, src}: כמה ניסיונות שגויים מהכתובת הזו בשעה הזו,
 *  ו"מקור" — 4 תווים מטביעה של הכתובת (בלי לשמור את הכתובת עצמה), כדי לראות ביומן אם הניסיונות באים מאותו מקום */
async function badCodeLimit(env, request, failed) {
  const ip = request.headers.get('cf-connecting-ip') || 'local';
  const hour = new Date().toISOString().slice(0, 13);
  const src = (await sha256Hex('badcode-src|' + hour.slice(0, 10) + '|' + ip)).slice(0, 4);
  const kv = env.USAGE && typeof env.USAGE.get === 'function' ? env.USAGE : env.STATS && typeof env.STATS.get === 'function' ? env.STATS : null;
  if (!kv) return { count: 0, src };
  const key = 'badcode:' + hour + ':' + ip;
  const n = +(await kv.get(key)) || 0;
  if (n >= 30) throw new Fail(429, 'rate', 'יותר מדי ניסיונות עם קוד שגוי. נסו שוב בעוד שעה.');
  if (failed) await kv.put(key, String(n + 1), { expirationTtl: 7200 });
  return { count: failed ? n + 1 : n, src };
}

/** מורים שהקוד שלהם קרוב לקוד שהוקלד: תו אחד שונה, או שני תווים סמוכים שהתחלפו (עד 3 מיילים) */
async function nearTeacher(store, code) {
  if (!code) return [];
  try {
    const all = await store.query('teacherCodes', { limit: 5000 });
    const close = c => {
      if (!c || c.length !== code.length || c === code) return false;
      const diff = [];
      for (let i = 0; i < c.length; i++) if (c[i] !== code[i]) diff.push(i);
      return diff.length === 1 || (diff.length === 2 && diff[1] === diff[0] + 1 && c[diff[0]] === code[diff[1]] && c[diff[1]] === code[diff[0]]);
    };
    return all.filter(d => close(d._id)).map(d => String(d.teacher || '')).filter(Boolean).slice(0, 3);
  } catch (e) {
    return [];
  }
}

/** מה נרשם ביומן על ניסיון עם קוד שגוי: הקוד שהוקלד, מוסתר בחלקו (3 תווים ראשונים ו-?), כמה ניסיונות מאותו מקור בשעה הזו */
function badCodeDetail(typed, code, lim) {
  const raw = String(typed || '').trim();
  const shown = code ? code.slice(0, 3) + '?'
    : raw ? '"' + Array.from(raw).slice(0, 3).join('') + (Array.from(raw).length > 3 ? '…' : '') + '" — לא בפורמט של קוד מורה' : 'ריק';
  const parts = ['קוד: ' + shown];
  if (lim && lim.count) parts.push(`ניסיון ${lim.count} בשעה הזו מאותו מקור`);
  if (lim && lim.src) parts.push('מקור #' + lim.src);
  return parts.join(' · ');
}

// ------------------------------------------------------------------ שליחת תרגיל ל-Gemini: רק למחוברים (אם המתג דולק)

/**
 * נקרא לפני כל שליחה ל-Gemini מהתוספים. מחזיר null (פתוח לכולם — כמו קודם) או {subject} — המפתח להגבלת הקצב,
 * כדי שכיתה שלמה מאחורי אותה כתובת IP לא תיחסם כמו "מחשב אחד".
 */
export async function geminiGate(request, env) {
  if (!accessEnabled(env)) return null;
  const s = await settings(env);
  const token = request.headers.get('x-dsviz-access') || '';
  if (!s.requireLoginForGemini && !token) return null;
  if (!token) throw new Fail(401, 'login', 'שליחת תרגיל פתוחה למורים, לעמדות מחשב בכיתה ולתלמידים רשומים — צריך להתחבר פעם אחת במחשב: בסרגל של DSViz לוחצים על מאגר התרגילים ("מאגר תרגילים" / "מאגר שאלות") ונכנסים. אם אין כפתור כזה — מעדכנים את DSViz.');
  const r = await resolveToken(env, token, request.headers.get('x-dsviz-machine') || '');
  if (r.status !== 'ok') {
    if (!s.requireLoginForGemini) return null;   // מתג כבוי: כרטיס שפג לא חוסם (רק לא נספר לפי משתמש)
    if (r.status === 'pay') throw new Fail(403, 'pay', 'שליחת תרגיל פתוחה לתלמידים רשומים — צריך לפתוח את המאגר (תשלום).');
    if (r.status === 'blocked') throw new Fail(403, 'blocked', 'הגישה של המורה הזה הושבתה. פונים למורה או להנהלה.');
    throw new Fail(401, 'login', 'הכניסה במחשב הזה כבר לא בתוקף — מתחברים שוב.');
  }
  // מונה חודשי (Firestore, מוסיפים באטומיות): מורה ועמדות שלו חולקים מנה אחת = שליחות-לעמדה × מספר העמדות (לפחות עמדה אחת, כדי שמורה בלי עמדות יוכל לנסות).
  // תלמיד רשום — מנה אישית. עמדת מחשב נספרת על המורה שהפעיל אותה. המונה נספר תמיד (להנהלה), והחסימה — רק כשהמתג "רק למחוברים" דולק.
  const owner = r.kind === 'station' ? r.station.teacher : r.kind === 'teacher' ? r.profile.email : r.profile.email;
  const store = db(env);
  let limit = s.monthlyStudent;
  if (r.kind !== 'student') {
    const t = await store.get('teachers/' + owner);
    limit = teacherLimit(s, t);
  }
  const key = await usageKey(r.kind === 'student' ? 'student' : 'teacher', owner);
  const u = await store.get(key);
  if (s.requireLoginForGemini && u && u.n >= limit) {
    throw new Fail(429, 'monthly', r.kind === 'student'
      ? `ניצלתם את ${limit} השליחות של החודש. המנה מתחדשת בתחילת החודש הבא.`
      : `המורה ניצל את ${limit} השליחות של החודש. המנה מתחדשת בתחילת החודש הבא, או אפשר לפנות להנהלה / לקנות עוד עמדות.`);
  }
  await store.merge(key, { owner, month: month(), kind: r.kind === 'student' ? 'student' : 'teacher', limit }, { n: 1 });
  const refund = () => store.merge(key, {}, { n: -1 }).catch(() => {});   // שליחה שנכשלה בגלל השרת — לא נספרת
  return { subject: r.subject, kind: r.kind, refund };
}

// ------------------------------------------------------------------ דפי הדפדפן: החשבון שלי (מורה / תלמיד)

async function me(request, env) {
  const u = await webUser(request, env);
  const store = db(env);
  const s = await settings(env);
  const [t, st] = await store.getMany(['teachers/' + u.email, 'students/' + u.email]);
  const [stations, requests, sessions, payments] = await Promise.all([
    t ? store.query('stations', { where: [['teacher', '==', u.email]] }) : [],
    store.query('requests', { where: [['email', '==', u.email]] }),
    store.query('sessions', { where: [['email', '==', u.email]] }),
    store.query('payments', { where: [['email', '==', u.email]] }),
  ]);
  const until = st && st.accessUntil || '';
  return {
    ok: true, email: u.email, name: u.name, picture: u.picture, admin: admins(env).includes(u.email),
    teacher: t ? Object.assign(await teacherView(t, stations.filter(x => x.active).sort((a, b) => String(a.name).localeCompare(String(b.name), 'he'))), { sends: (await monthlySends(env, [t]))[0], phone: t.phone || '' }) : null,
    student: { until, open: !!until && until >= today(), source: st && st.source || '', sends: (await monthlySends(env, [{ email: u.email, kind: 'student' }]))[0] },
    requests: requests.sort((a, b) => String(b.createdAt).localeCompare(String(a.createdAt))).slice(0, 12).map(r => ({
      id: r._id, kind: r.kind, amount: r.amount || 0, stationName: r.stationName || '', status: r.status, createdAt: r.createdAt, decidedAt: r.decidedAt || '', reason: r.reason || '',
    })),
    devices: sessions.filter(x => !x.revoked).map(x => ({ id: x._id, name: x.machineName || '', role: x.role, lastSeen: x.lastSeen || x.createdAt })),
    payments: payments.filter(p => p.status !== 'pending').sort((a, b) => String(b.createdAt).localeCompare(String(a.createdAt))).map(p => ({ kind: p.kind || 'student', seats: p.seats || 0, amount: p.amount, status: p.status, createdAt: p.createdAt, paidAt: p.paidAt || '' })),
    price: s.price, accessUntil: s.accessUntil, canPay: s.price > 0 && !!payProvider(env), payBy: payProvider(env) ? payProvider(env).id : '', offlineDays: s.offlineDays,
    packages: t && payProvider(env) ? (s.packages || []).map((p, i) => ({ index: i, seats: p.seats, price: p.price })).filter(p => p.price > 0) : [],
  };
}

async function myRequest(request, env) {
  const u = await webUser(request, env);
  const b = await readJson(request);
  const store = db(env);
  const kind = ['teacher', 'release'].includes(b.kind) ? b.kind : '';   // עמדות נוספות — רק בקניית חבילה
  if (!kind) throw new Fail(400, 'format', 'סוג בקשה לא מוכר');
  const t = await store.get('teachers/' + u.email);
  const open = (await store.query('requests', { where: [['email', '==', u.email]] })).filter(r => r.status === 'open' && r.kind === kind);
  const doc = { kind, email: u.email, name: u.name || '', note: clip(b.note, 300), status: 'open', createdAt: nowIso() };
  if (kind === 'teacher') {
    if (t) throw new Fail(400, 'exists', 'החשבון כבר מוגדר כמורה — נכנסים שוב.');
    if (open.length) return { ok: true, id: open[0]._id, already: true };
    const phone = phoneNorm(b.phone);
    if (!phone) throw new Fail(400, 'phone', 'צריך מספר טלפון נייד תקני — 10 ספרות (למשל 050-1234567).');
    Object.assign(doc, { school: clip(b.school, 120), phone, computers: intIn(b.computers, 0, 2000) ? +b.computers : 0 });
  } else {
    if (!t || !t.active) throw new Fail(403, 'teacher', 'רק מורה פעיל יכול לשלוח בקשה כזו.');
    doc.school = t.school || '';
    {
      const st = await store.get('stations/' + clip(b.stationId, 64));
      if (!st || st.teacher !== u.email || !st.active) throw new Fail(400, 'station', 'המחשב לא נמצא ברשימה שלך.');
      if (open.some(r => r.stationId === st._id)) return { ok: true, id: open.find(r => r.stationId === st._id)._id, already: true };
      Object.assign(doc, { stationId: st._id, stationName: st.name || '' });
    }
  }
  const id = timeId();
  await store.set('requests/' + id, doc);
  await log(store, u.email, 'request.' + kind, doc.stationName || '', doc.amount ? String(doc.amount) : doc.school || '');
  return { ok: true, id };
}

async function myRevokeDevice(request, env) {
  const u = await webUser(request, env);
  const b = await readJson(request);
  const store = db(env);
  const se = await store.get('sessions/' + clip(b.id, 64));
  if (!se || se.email !== u.email) throw new Fail(404, 'not_found', 'המחשב לא נמצא.');
  if (!se.revoked) await store.update('sessions/' + se._id, { revoked: true, revokedAt: nowIso(), revokedBy: 'self' });
  return { ok: true };
}

async function assignCode(store, email) {
  for (let i = 0; i < 8; i++) {
    const c = teacherCode();
    if (await store.create('teacherCodes/' + c, { teacher: email, createdAt: nowIso() })) return c;
  }
  throw new Fail(500, 'internal', 'לא הצלחתי ליצור קוד מורה. נסו שוב.');
}

// ------------------------------------------------------------------ תשלום (שירות התשלום נבחר בהגדרות — pay.js)

/** תשלום: תלמיד (פתיחת המאגר עד accessUntil) או מורה (חבילת עמדות — מתווספת מיד אחרי שהתשלום מאושר) */
async function payStart(request, env) {
  const u = await webUser(request, env);
  const b = await readJson(request);
  const s = await settings(env);
  const provider = payProvider(env);
  if (!provider) throw new Fail(503, 'pay_off', 'התשלום עוד לא פתוח. נסו שוב בקרוב.');
  const store = db(env);
  const pid = timeId() + randomToken(4).replace(/[-_]/g, 'x');
  let pay;
  if (b.kind === 'seats') {
    const t = await store.get('teachers/' + u.email);
    if (!t || !t.active) throw new Fail(403, 'teacher', 'רק מורה פעיל יכול לקנות עמדות.');
    const pk = (s.packages || [])[+b.package];
    if (!pk || !(pk.price > 0)) throw new Fail(400, 'package', 'החבילה הזו לא נמכרת כרגע.');
    pay = { kind: 'seats', seats: pk.seats, amount: pk.price, title: `חבילת ${pk.seats} עמדות` };
  } else {
    if (!(s.price > 0)) throw new Fail(503, 'pay_off', 'התשלום עוד לא פתוח. נסו שוב בקרוב.');
    const st = await store.get('students/' + u.email);
    if (st && st.accessUntil && st.accessUntil >= s.accessUntil) return { ok: true, already: true };
    pay = { kind: 'student', amount: s.price, accessUntil: s.accessUntil, title: 'מאגר התרגילים לשנת הלימודים' };
  }
  // לא יותר מ-10 תשלומים שהתחילו ולא הושלמו בשעה לכל חשבון (כל אחד פותח הזמנה אצל שירות התשלום)
  const hourAgo = new Date(Date.now() - 3600 * 1000).toISOString();
  const open = (await store.query('payments', { where: [['email', '==', u.email]] })).filter(x => x.status === 'pending' && String(x.createdAt) > hourAgo);
  if (open.length >= 10) throw new Fail(429, 'rate', 'יותר מדי ניסיונות תשלום. נסו שוב בעוד שעה.');
  Object.assign(pay, { email: u.email, uid: u.uid, name: u.name || '', currency: 'ILS', provider: provider.id, status: 'pending', createdAt: nowIso() });
  await store.set('payments/' + pid, pay);
  const origin = new URL(request.url).origin;
  let made;
  try {
    made = await provider.create(env, Object.assign({ id: pid }, pay), origin);
  } catch (e) {
    console.error('pay create', e && e.message);
    await store.merge('payments/' + pid, { status: 'failed', note: 'שירות התשלום לא ענה', updatedAt: nowIso() }).catch(() => {});
    throw new Fail(502, 'pay_down', 'שירות התשלום לא זמין כרגע. נסו שוב בעוד כמה דקות.');
  }
  // providerOrder: ההזמנה שהשרת יצר לתשלום הזה — רק אותה אפשר לגבות עבורו (providerRef מתחלף אחר כך באסמכתה של הגבייה)
  await store.merge('payments/' + pid, { providerRef: clip(made.ref, 120), providerOrder: clip(made.order || made.ref, 120) });
  return { ok: true, url: made.url, id: pid };
}

async function payNotify(request, env, providerId) {
  const provider = payProvider(env);
  if (!provider || provider.id !== providerId) throw new Fail(404, 'not_found', 'כתובת לא מוכרת');
  const store = db(env);
  let n;
  try {
    n = await provider.verify(env, request, store);   // {id, status: paid|refunded|failed, ref, amount, currency}
  } catch (e) {
    // הודעה שלא אומתה (חתימה שגויה / לא מוגדר): נדחית, ושירות התשלום ינסה שוב
    console.error('pay notify', e && e.message);
    throw new Fail(400, 'notify', 'ההודעה לא אומתה.');
  }
  if (!n || !n.id) return { ok: true, ignored: true };
  await settlePayment(env, n);
  return provider.ack ? provider.ack() : { ok: true };
}

/** תשלום שאושר / הוחזר — פעם אחת לכל עסקה (גם אם ההודעה מגיעה כמה פעמים) */
export async function settlePayment(env, n) {
  const store = db(env);
  const result = await store.tx(async t => {
    const p = await t.get('payments/' + n.id);
    if (!p) return { skip: 'unknown' };
    // תשלום נפתח רק אם שולם בדיוק הסכום שנקבע, באותו מטבע (ספק אמיתי חייב לדווח סכום; בלי סכום — רק ספק הבדיקות)
    if (n.status === 'paid' && p.status !== 'paid') {
      const noAmount = n.amount === undefined && p.provider !== 'demo';
      const wrongAmount = n.amount !== undefined && Math.round(+n.amount * 100) !== Math.round(+p.amount * 100);
      const wrongCurrency = !!n.currency && !!p.currency && String(n.currency).toUpperCase() !== String(p.currency).toUpperCase();
      if (noAmount || wrongAmount || wrongCurrency) {
        t.update('payments/' + n.id, { status: 'mismatch', note: `שולם ${n.amount === undefined ? '?' : n.amount} ${n.currency || ''} במקום ${p.amount} ${p.currency || ''}`.replace(/\s+/g, ' '), updatedAt: nowIso() });
        return { skip: 'mismatch', p };
      }
    }
    if (p.status === n.status) return { skip: 'same', p };
    const seats = p.kind === 'seats';
    const at = nowIso();
    // פרטי המשלם כמו שהשירות החזיר (שם, מייל, טלפון) — מוצגים במסוף ההנהלה
    const payer = n.payer ? { payerName: clip(n.payer.name, 80), payerEmail: clip(n.payer.email, 120), payerPhone: clip(n.payer.phone, 30) } : {};
    if (seats) {
      const teacher = await t.get('teachers/' + p.email);
      if (n.status === 'paid' && (p.status === 'pending' || p.status === 'failed')) {
        t.update('payments/' + n.id, Object.assign({ status: 'paid', paidAt: at, providerRef: clip(n.ref || p.providerRef, 120) }, payer));
        if (teacher) t.update('teachers/' + p.email, { updatedAt: at }, { seats: p.seats });
        return { done: 'seats', p, before: teacher ? teacher.seats || 0 : 0 };
      }
      if (n.status === 'refunded' && p.status === 'paid') {
        t.update('payments/' + n.id, { status: 'refunded', refundedAt: at });
        if (teacher) t.update('teachers/' + p.email, { seats: Math.max(0, (teacher.seats || 0) - p.seats), updatedAt: at });
        return { done: 'refunded', p };
      }
    } else {
      const st = await t.get('students/' + p.email);
      if (n.status === 'paid' && (p.status === 'pending' || p.status === 'failed')) {
        t.update('payments/' + n.id, Object.assign({ status: 'paid', paidAt: at, providerRef: clip(n.ref || p.providerRef, 120) }, payer));
        const until = st && st.accessUntil && st.accessUntil > p.accessUntil ? st.accessUntil : p.accessUntil;
        t.merge('students/' + p.email, { email: p.email, accessUntil: until, source: 'payment', lastPayment: n.id, paidAt: at, phone: payer.payerPhone || (st && st.phone) || '' });
        return { done: 'paid', p };
      }
      if (n.status === 'refunded' && p.status === 'paid') {
        t.update('payments/' + n.id, { status: 'refunded', refundedAt: at });
        if (st && st.lastPayment === n.id) t.merge('students/' + p.email, { accessUntil: null, source: 'refunded' });
        return { done: 'refunded', p };
      }
    }
    if (n.status === 'failed' && p.status === 'pending') {
      t.update('payments/' + n.id, { status: 'failed', updatedAt: at });
      return { done: 'failed', p };
    }
    return { skip: 'state', p };
  });
  if (result.done) {
    const what = result.done === 'seats' ? `חבילת ${result.p.seats} עמדות · ${result.before} ← ${result.before + result.p.seats}` : '';
    await log(store, 'תשלום', 'payment.' + result.done, result.p.email, [`${result.p.amount} ₪`, what].filter(Boolean).join(' · '));
  }
  return result;
}

// ------------------------------------------------------------------ מסוף ההנהלה

/** מנת השליחות החודשית של מורה: מה שההנהלה קבעה לו ידנית (monthlyLimit), ואם לא — לפי מספר העמדות × המנה לעמדה */
export function teacherLimit(s, t) {
  if (t && Number.isInteger(t.monthlyLimit) && t.monthlyLimit >= 0) return t.monthlyLimit;
  return s.monthlyPerSeat * Math.max(1, t && t.seats || 0);
}

/** מפתח המונה החודשי של מורה (כולל העמדות שלו) או של תלמיד */
async function usageKey(kind, email) {
  return 'usage/' + month() + '_' + (await sha256Hex(kind + ':' + normEmail(email))).slice(0, 24);
}

function teacherRow(t) {
  return {
    email: t.email || t._id, name: t.name || '', school: t.school || '', code: showCode(t.code), seats: t.seats || 0, used: t.used || 0, phone: t.phone || '', monthlyLimit: Number.isInteger(t.monthlyLimit) ? t.monthlyLimit : null,
    active: !!t.active, note: t.note || '', createdAt: t.createdAt || '', lastSignIn: t.lastSignIn || '',
  };
}

async function adminOverview(env) {
  const store = db(env);
  const monthStart = today().slice(0, 8) + '01';
  const [teachers, requests, students, payments, recent] = await Promise.all([
    store.query('teachers'),
    store.query('requests', { where: [['status', '==', 'open']] }),
    store.query('students', { where: [['accessUntil', '>=', today()]] }),
    store.query('payments', { where: [['createdAt', '>=', monthStart]] }),
    store.query('log', { orderBy: ['at', 'desc'], limit: 15 }),
  ]);
  const active = teachers.filter(t => t.active);
  const full = active.filter(t => (t.seats || 0) > 0 && (t.used || 0) >= (t.seats || 0));
  return {
    ok: true,
    counts: {
      teachers: active.length, teachersBlocked: teachers.length - active.length,
      seats: active.reduce((n, t) => n + (t.seats || 0), 0), used: active.reduce((n, t) => n + (t.used || 0), 0),
      students: students.length, studentsPaid: students.filter(s => s.source === 'payment').length, studentsManual: students.filter(s => s.source === 'manual').length,
      paymentsMonth: payments.filter(p => p.status === 'paid' || p.status === 'refunded').length, refundsMonth: payments.filter(p => p.status === 'refunded').length,
      openRequests: requests.length,
    },
    requests: requests.sort((a, b) => String(a.createdAt).localeCompare(String(b.createdAt))).map(requestRow),
    full: full.map(teacherRow),
    recent: recent.map(l => ({ at: l.at, actor: l.actor, action: l.action, target: l.target, detail: l.detail })),
  };
}

function requestRow(r) {
  return {
    id: r._id, kind: r.kind, email: r.email, name: r.name || '', school: r.school || '', phone: r.phone || '', amount: r.amount || 0, computers: r.computers || 0,
    stationId: r.stationId || '', stationName: r.stationName || '', note: r.note || '', status: r.status, createdAt: r.createdAt,
    decidedAt: r.decidedAt || '', decidedBy: r.decidedBy || '', reason: r.reason || '', result: r.result || '', before: r.before || null,
  };
}

/** מנת השליחות של החודש לכל מורה (מורה + העמדות שלו): n = כמה נשלחו, limit = מספר העמדות שמותר לו × המנה לעמדה — לפי ההגדרה הנוכחית, ולכן משתנה מיד כשמעדכנים עמדות */
async function monthlySends(env, rows) {
  const store = db(env), s = await settings(env);
  const keys = await Promise.all(rows.map(r => usageKey(r.kind || 'teacher', r.email || r._id)));
  const docs = keys.length ? await store.getMany(keys) : [];
  return rows.map((r, i) => ({ n: docs[i] && docs[i].n || 0, limit: (r.kind || 'teacher') === 'student' ? s.monthlyStudent : teacherLimit(s, r), manual: (r.kind || 'teacher') !== 'student' && Number.isInteger(r.monthlyLimit) }));
}

async function adminTeachers(env) {
  const store = db(env);
  const teachers = await store.query('teachers');
  const sends = await monthlySends(env, teachers);
  return { ok: true, teachers: teachers.map((t, i) => Object.assign(teacherRow(t), { sends: sends[i] })).sort((a, b) => a.name.localeCompare(b.name, 'he') || a.email.localeCompare(b.email)) };
}

async function adminTeacher(env, url) {
  const store = db(env);
  const email = normEmail(url.searchParams.get('email'));
  const t = await store.get('teachers/' + email);
  if (!t) throw new Fail(404, 'not_found', 'המורה לא נמצא.');
  const stations = await store.query('stations', { where: [['teacher', '==', email]] });
  return {
    ok: true, teacher: Object.assign(teacherRow(t), { sends: (await monthlySends(env, [t]))[0] }),
    stations: stations.filter(x => x.active).map(x => ({ id: x._id, name: x.name, claimedAt: x.claimedAt, lastSeen: x.lastSeen }))
      .sort((a, b) => String(a.name).localeCompare(String(b.name), 'he')),
  };
}

/** הוספת מורה או שינוי: מכסה, השבתה, שם, בית ספר, הערה. מחזיר מה השתנה (לרישום ביומן) */
async function adminSaveTeacher(env, u, b) {
  const store = db(env);
  const s = await settings(env);
  const email = normEmail(b.email);
  if (!emailOk(email)) throw new Fail(400, 'email', 'כתובת המייל לא תקינה.');
  if (b.seats !== undefined && !intIn(b.seats, 0, 10000)) throw new Fail(400, 'seats', 'מספר העמדות: בין 0 ל-10000.');
  if (b.phone !== undefined && String(b.phone).trim() !== '') {
    b = Object.assign({}, b, { phone: phoneNorm(b.phone) });
    if (!b.phone) throw new Fail(400, 'phone', 'מספר טלפון לא תקין — נייד ישראלי של 10 ספרות (למשל 050-1234567).');
  } else if (b.phone !== undefined) b = Object.assign({}, b, { phone: '' });
  if (b.monthlyLimit !== undefined) {   // מנת שליחות חודשית ידנית; ריק / null = חזרה לברירת המחדל (עמדות × מנה לעמדה)
    if (b.monthlyLimit === null || String(b.monthlyLimit).trim() === '') b = Object.assign({}, b, { monthlyLimit: null });
    else if (!intIn(b.monthlyLimit, 0, 100000)) throw new Fail(400, 'monthlyLimit', 'מנת השליחות החודשית: מספר שלם בין 0 ל-100000 (או ריק לברירת המחדל).');
    else b = Object.assign({}, b, { monthlyLimit: +b.monthlyLimit });
  }
  const at = nowIso();
  if (b.create) {
    const exists = await store.get('teachers/' + email);
    if (exists) throw new Fail(409, 'exists', 'המורה כבר ברשימה.');
    const code = await assignCode(store, email);
    const seats = b.seats !== undefined ? +b.seats : s.defaultSeats;
    const ok = await store.create('teachers/' + email, {
      email, name: clip(b.name, 80), school: clip(b.school, 120), phone: b.phone || '', seats, used: 0, code, active: true, note: clip(b.note, 300),
      createdAt: at, updatedAt: at, createdBy: u.email,
    });
    if (!ok) { await store.remove('teacherCodes/' + code); throw new Fail(409, 'exists', 'המורה כבר ברשימה.'); }
    await log(store, u.email, 'teacher.add', email, `${seats} עמדות`);
    return { created: true, seats };
  }
  const changes = [];
  await store.tx(async t => {
    const cur = await t.get('teachers/' + email);
    if (!cur) throw new Fail(404, 'not_found', 'המורה לא נמצא.');
    const upd = { updatedAt: at };
    changes.length = 0;
    if (b.seats !== undefined && +b.seats !== cur.seats) { upd.seats = +b.seats; changes.push(`מכסה ${cur.seats || 0} ← ${+b.seats}`); }
    if (b.monthlyLimit !== undefined) {
      const before = Number.isInteger(cur.monthlyLimit) ? cur.monthlyLimit : null;
      if (b.monthlyLimit !== before) { upd.monthlyLimit = b.monthlyLimit; changes.push(`שליחות בחודש ${before === null ? 'ברירת מחדל' : before} ← ${b.monthlyLimit === null ? 'ברירת מחדל' : b.monthlyLimit}`); }
    }
    if (b.active !== undefined && !!b.active !== !!cur.active) { upd.active = !!b.active; changes.push(b.active ? 'הופעל' : 'הושבת'); }
    for (const [k, n] of [['name', 80], ['school', 120], ['note', 300], ['phone', 20]]) {
      if (b[k] !== undefined && clip(b[k], n) !== (cur[k] || '')) { upd[k] = clip(b[k], n); changes.push(k); }
    }
    t.update('teachers/' + email, upd);
  });
  if (changes.length) await log(store, u.email, 'teacher.update', email, changes.join(', '));
  return { changes };
}

async function adminReleaseStation(env, u, id, why) {
  const store = db(env);
  const out = await store.tx(async t => {
    const st = await t.get('stations/' + id);
    if (!st) throw new Fail(404, 'not_found', 'העמדה לא נמצאה.');
    if (!st.active) return { already: true, st };
    const teacher = await t.get('teachers/' + st.teacher);
    const at = nowIso();
    t.update('stations/' + id, { active: false, releasedAt: at, releasedBy: u.email });
    if (teacher) t.update('teachers/' + st.teacher, { used: Math.max(0, (teacher.used || 0) - 1), updatedAt: at });
    return { st };
  });
  if (!out.already) await log(store, u.email, 'station.release', out.st.teacher, `${out.st.name}${why ? ' · ' + why : ''}`);
  return out;
}

async function adminStations(env, url) {
  const store = db(env);
  const teacher = normEmail(url.searchParams.get('teacher'));
  const list = teacher ? await store.query('stations', { where: [['teacher', '==', teacher]] }) : await store.query('stations', { where: [['active', '==', true]] });
  return {
    ok: true,
    stations: list.filter(x => x.active).map(x => ({ id: x._id, name: x.name, teacher: x.teacher, teacherName: x.teacherName || '', claimedAt: x.claimedAt, lastSeen: x.lastSeen }))
      .sort((a, b) => String(a.lastSeen).localeCompare(String(b.lastSeen))),
  };
}

async function adminStudents(env, url) {
  const store = db(env);
  const all = await store.query('students', { limit: 5000 });
  const t = today();
  const rows = all.map(s => ({
    email: s.email || s._id, name: s.name || '', until: s.accessUntil || '', open: !!s.accessUntil && s.accessUntil >= t, source: s.source || '',
    note: s.note || '', paidAt: s.paidAt || '', lastSignIn: s.lastSignIn || '', phone: s.phone || '',
  }));
  const sends = await monthlySends(env, rows.map(r => ({ email: r.email, kind: 'student' })));
  rows.forEach((r, i) => { r.sends = sends[i]; });
  return { ok: true, students: rows.sort((a, b) => String(b.paidAt || b.lastSignIn).localeCompare(String(a.paidAt || a.lastSignIn))) };
}

async function adminStudent(env, url) {
  const store = db(env);
  const email = normEmail(url.searchParams.get('email'));
  const [st, payments, sessions] = await Promise.all([
    store.get('students/' + email),
    store.query('payments', { where: [['email', '==', email]] }),
    store.query('sessions', { where: [['email', '==', email]] }),
  ]);
  return {
    ok: true, email,
    student: st ? { name: st.name || '', until: st.accessUntil || '', source: st.source || '', note: st.note || '', phone: st.phone || '' } : null,
    sends: (await monthlySends(env, [{ email, kind: 'student' }]))[0],
    payments: payments.sort((a, b) => String(b.createdAt).localeCompare(String(a.createdAt))).map(p => ({ id: p._id, kind: p.kind || 'student', seats: p.seats || 0, amount: p.amount, status: p.status, provider: p.provider, ref: p.providerRef || '', createdAt: p.createdAt, paidAt: p.paidAt || '', payerName: p.payerName || '', payerPhone: p.payerPhone || '', payerEmail: p.payerEmail || '' })),
    devices: sessions.filter(x => !x.revoked).map(x => ({ id: x._id, name: x.machineName || '', role: x.role, lastSeen: x.lastSeen || x.createdAt, createdAt: x.createdAt || '' })),
  };
}

/** הענקה / הארכה / ביטול של גישה לתלמידים (גם רשימה) */
async function adminGrant(env, u, b) {
  const store = db(env);
  const emails = (Array.isArray(b.emails) ? b.emails : String(b.email || '').split(/[\s,;]+/)).map(normEmail).filter(Boolean);
  if (!emails.length) throw new Fail(400, 'email', 'חסרה כתובת מייל.');
  if (emails.length > 500) throw new Fail(400, 'email', 'עד 500 כתובות בבת אחת.');
  const bad = emails.filter(e => !emailOk(e));
  if (bad.length) throw new Fail(400, 'email', 'כתובות לא תקינות: ' + bad.slice(0, 5).join(', '));
  const revoke = b.mode === 'revoke';
  if (!revoke && !dateOk(b.until)) throw new Fail(400, 'until', 'תאריך לא תקין.');
  const at = nowIso();
  for (let i = 0; i < emails.length; i += 200) {
    await store.commit(w => {
      for (const e of emails.slice(i, i + 200)) {
        w.merge('students/' + e, revoke
          ? { email: e, accessUntil: null, source: 'revoked', note: clip(b.note, 200), updatedAt: at, updatedBy: u.email }
          : { email: e, accessUntil: b.until, source: 'manual', note: clip(b.note, 200), updatedAt: at, updatedBy: u.email });
      }
    });
  }
  await log(store, u.email, revoke ? 'student.revoke' : 'student.grant', emails.length === 1 ? emails[0] : `${emails.length} תלמידים`, `${revoke ? '' : 'עד ' + b.until}${b.note ? ' · ' + clip(b.note, 120) : ''}`);
  return { ok: true, count: emails.length };
}

async function adminRequests(env, url) {
  const store = db(env);
  const done = url.searchParams.get('status') === 'done';
  const list = done
    ? (await store.query('requests', { orderBy: ['createdAt', 'desc'], limit: 200 })).filter(r => r.status !== 'open')
    : await store.query('requests', { where: [['status', '==', 'open']] });
  return { ok: true, requests: list.sort((a, b) => done ? String(b.decidedAt).localeCompare(String(a.decidedAt)) : String(a.createdAt).localeCompare(String(b.createdAt))).map(requestRow) };
}

async function adminDecide(env, u, b) {
  const store = db(env);
  const r = await store.get('requests/' + clip(b.id, 64));
  if (!r) throw new Fail(404, 'not_found', 'הבקשה לא נמצאה.');
  if (r.status !== 'open') throw new Fail(409, 'done', 'הבקשה כבר טופלה.');
  const at = nowIso();
  let result = '';
  if (b.decision === 'approve') {
    if (r.kind === 'teacher') {
      const seats = b.seats !== undefined ? +b.seats : (await settings(env)).defaultSeats;
      const exists = await store.get('teachers/' + r.email);
      if (exists) await adminSaveTeacher(env, u, { email: r.email, active: true, seats: b.seats !== undefined ? seats : undefined });
      else await adminSaveTeacher(env, u, { email: r.email, name: r.name, school: r.school, phone: r.phone || '', seats, create: true });
      result = `הוגדר כמורה · ${seats} עמדות`;
    } else if (r.kind === 'seats') {
      const add = b.amount !== undefined ? +b.amount : r.amount;
      if (!intIn(add, 1, 2000)) throw new Fail(400, 'amount', 'כמה להוסיף? מספר בין 1 ל-2000.');
      const t = await store.get('teachers/' + r.email);
      if (!t) throw new Fail(404, 'not_found', 'המורה לא נמצא.');
      await adminSaveTeacher(env, u, { email: r.email, seats: (t.seats || 0) + add });
      result = `מכסה ${t.seats || 0} ← ${(t.seats || 0) + add}`;
    } else if (r.kind === 'release') {
      await adminReleaseStation(env, u, r.stationId, 'לפי בקשת המורה');
      result = `שוחרר: ${r.stationName}`;
    }
    await store.update('requests/' + r._id, { status: 'approved', decidedAt: at, decidedBy: u.email, result });
  } else if (b.decision === 'decline') {
    await store.update('requests/' + r._id, { status: 'declined', decidedAt: at, decidedBy: u.email, reason: clip(b.reason, 300) });
    await log(store, u.email, 'request.decline', r.email, r.kind);
  } else {
    throw new Fail(400, 'format', 'אישור או דחייה?');
  }
  return { ok: true, result };
}

async function adminLog(env, url) {
  const store = db(env);
  const limit = Math.min(500, Math.max(10, +url.searchParams.get('limit') || 100));
  // order=asc: מהישן לחדש (ממשיכים עם after=); ברירת המחדל: מהחדש לישן (ממשיכים עם before=)
  const asc = url.searchParams.get('order') === 'asc';
  const cursor = url.searchParams.get(asc ? 'after' : 'before');
  const list = await store.query('log', {
    ...(cursor ? { where: [['at', asc ? '>' : '<', cursor]] } : {}),
    orderBy: ['at', asc ? 'asc' : 'desc'], limit,
  });
  return { ok: true, log: list.map(l => ({ at: l.at, actor: l.actor, action: l.action, target: l.target, detail: l.detail })) };
}

async function adminPayments(env, url) {
  const store = db(env);
  const list = await store.query('payments', { orderBy: ['createdAt', 'desc'], limit: Math.min(1000, +url.searchParams.get('limit') || 300) });
  return {
    ok: true,
    payments: list.filter(p => p.status !== 'pending' || Date.now() - Date.parse(p.createdAt) < 86400000).map(p => ({
      id: p._id, email: p.email, name: p.name || '', kind: p.kind || 'student', seats: p.seats || 0, amount: p.amount, status: p.status, provider: p.provider, ref: p.providerRef || '',
      createdAt: p.createdAt, paidAt: p.paidAt || '', payerName: p.payerName || '', payerEmail: p.payerEmail || '', payerPhone: p.payerPhone || '',
    })),
  };
}

async function adminSettings(env, u, b) {
  const store = db(env);
  if (b) {
    const upd = {};
    const changes = [];
    const cur = await settings(env, true);
    for (const [k, [lo, hi]] of Object.entries(SETTING_LIMITS)) {
      if (b[k] === undefined) continue;
      if (!intIn(b[k], lo, hi)) throw new Fail(400, 'format', `ערך לא תקין: ${k}`);
      if (+b[k] !== cur[k]) { upd[k] = +b[k]; changes.push(`${k}: ${cur[k]} ← ${+b[k]}`); }
    }
    if (b.accessUntil !== undefined) {
      if (!dateOk(b.accessUntil)) throw new Fail(400, 'format', 'תאריך לא תקין.');
      if (b.accessUntil !== cur.accessUntil) { upd.accessUntil = b.accessUntil; changes.push(`accessUntil: ${cur.accessUntil} ← ${b.accessUntil}`); }
    }
    if (b.packages !== undefined) {
      if (!Array.isArray(b.packages) || b.packages.length > 6 || !b.packages.every(p => p && intIn(p.seats, 1, 500) && intIn(p.price, 0, 20000))) throw new Fail(400, 'format', 'חבילות: עד 6, עמדות 1–500, מחיר 0–20000.');
      const pk = b.packages.map(p => ({ seats: +p.seats, price: +p.price }));
      if (JSON.stringify(pk) !== JSON.stringify(cur.packages)) { upd.packages = pk; changes.push('חבילות: ' + pk.map(p => `${p.seats}=${p.price}₪`).join(', ')); }
    }
    if (b.requireLoginForGemini !== undefined && !!b.requireLoginForGemini !== !!cur.requireLoginForGemini) {
      upd.requireLoginForGemini = !!b.requireLoginForGemini;
      changes.push(b.requireLoginForGemini ? 'Gemini: רק למחוברים' : 'Gemini: פתוח לכולם');
    }
    if (changes.length) {
      upd.updatedAt = nowIso(); upd.updatedBy = u.email;
      await store.merge('config/settings', upd);
      settingsCache.at = 0;
      await log(store, u.email, 'settings', '', changes.join(', '));
    }
  }
  const s = await settings(env, true);
  const key = await passKey(env);
  return {
    ok: true, settings: Object.fromEntries(Object.keys(DEFAULTS).map(k => [k, s[k]])),
    admins: admins(env), provider: payProvider(env) ? payProvider(env).id : '', providers: PAY_PROVIDERS,
    passKey: !!key, signedIn: u.email,
  };
}

// ------------------------------------------------------------------ ניתוב

const JSON_ROUTES = {
  'GET /v1/access/config': async (req, env) => {
    const s = await settings(env);
    return {
      ok: true, enabled: true, requireLogin: !!s.requireLoginForGemini, price: s.price, accessUntil: s.accessUntil, offlineDays: s.offlineDays,
      canPay: s.price > 0 && !!payProvider(env),
      firebase: { apiKey: env.FIREBASE_API_KEY || '', authDomain: env.FIREBASE_AUTH_DOMAIN || (projectId(env) + '.firebaseapp.com'), projectId: projectId(env) },
      emulator: firestoreEmulator(env) ? { auth: env.FIREBASE_AUTH_EMULATOR_HOST || '' } : undefined,
    };
  },
  'GET /v1/access/key': async (req, env) => {
    const key = await passKey(env);
    if (!key) throw new Fail(503, 'config', 'PASS_KEY לא הוגדר');
    if (!keyCache.spki) keyCache.spki = await publicSpki(key);
    return { ok: true, spki: keyCache.spki };
  },
  'POST /v1/auth/exchange': exchange,
  'POST /v1/auth/redeem': redeem,
  'POST /v1/access/check': check,
  'POST /v1/access/logout': logout,
  'POST /v1/station/claim': claim,
  'GET /v1/me': me,
  'POST /v1/me/request': myRequest,
  'POST /v1/me/devices/revoke': myRevokeDevice,
  'POST /v1/pay/start': payStart,
};

const ADMIN_ROUTES = {
  'GET /v1/admin/overview': (env, u, url) => adminOverview(env),
  'GET /v1/admin/teachers': (env, u, url) => adminTeachers(env),
  'GET /v1/admin/teacher': (env, u, url) => adminTeacher(env, url),
  'POST /v1/admin/teacher': async (env, u, url, b) => Object.assign({ ok: true }, await adminSaveTeacher(env, u, b)),
  'GET /v1/admin/stations': (env, u, url) => adminStations(env, url),
  'POST /v1/admin/station/release': async (env, u, url, b) => { await adminReleaseStation(env, u, clip(b.id, 64), clip(b.reason, 120)); return { ok: true }; },
  'GET /v1/admin/students': (env, u, url) => adminStudents(env, url),
  'GET /v1/admin/student': (env, u, url) => adminStudent(env, url),
  'POST /v1/admin/student': (env, u, url, b) => adminGrant(env, u, b),
  'POST /v1/admin/session/revoke': async (env, u, url, b) => {
    const store = db(env);
    const se = await store.get('sessions/' + clip(b.id, 64));
    if (!se) throw new Fail(404, 'not_found', 'לא נמצא.');
    if (!se.revoked) await store.update('sessions/' + se._id, { revoked: true, revokedAt: nowIso(), revokedBy: u.email });
    await log(store, u.email, 'session.revoke', se.email, se.machineName || '');
    return { ok: true };
  },
  'GET /v1/admin/payments': (env, u, url) => adminPayments(env, url),
  'GET /v1/admin/requests': (env, u, url) => adminRequests(env, url),
  'POST /v1/admin/request': (env, u, url, b) => adminDecide(env, u, b),
  'GET /v1/admin/log': (env, u, url) => adminLog(env, url),
  'GET /v1/admin/settings': (env, u) => adminSettings(env, u, null),
  'POST /v1/admin/settings': (env, u, url, b) => adminSettings(env, u, b),
};

/** מחזיר Response לנתיבים של הכניסה, או null אם הנתיב לא שלנו */
export async function handleAccess(request, env, ctx, url) {
  const path = url.pathname;
  const isOurs = path.startsWith('/v1/access/') || path.startsWith('/v1/auth/') || path.startsWith('/v1/station/') || path === '/v1/me' || path.startsWith('/v1/me/')
    || path.startsWith('/v1/admin/') || path.startsWith('/v1/pay/') || path.startsWith('/pay/')
    || ['/signin', '/welcome', '/account', '/admin'].includes(path) || path.startsWith('/static/');
  if (!isOurs) return null;
  if (!accessEnabled(env)) {
    if (path === '/v1/access/config') return reply(200, { ok: true, enabled: false });
    throw new Fail(404, 'not_found', 'כתובת לא מוכרת');
  }
  if (request.method === 'GET' && !path.startsWith('/v1/')) {
    const page = await servePage(path, env, url);
    if (page) return page;
  }
  const notify = /^\/v1\/pay\/notify\/([a-z0-9-]{2,20})$/.exec(path);
  if (notify) return reply(200, await payNotify(request, env, notify[1]));
  const provider = payProvider(env);
  if (provider && provider.routes) {
    const r = await provider.routes(request, env, url, { settlePayment: n => settlePayment(env, n), db: () => db(env) });
    if (r) return r;
  }
  const key = request.method + ' ' + path;
  if (JSON_ROUTES[key]) return reply(200, await JSON_ROUTES[key](request, env, url));
  if (ADMIN_ROUTES[key]) {
    const u = await adminUser(request, env);
    const b = request.method === 'POST' ? await readJson(request) : null;
    return reply(200, await ADMIN_ROUTES[key](env, u, url, b));
  }
  throw new Fail(404, 'not_found', 'כתובת לא מוכרת');
}

export const _test = { reset() { settingsCache = { at: 0, pid: '', value: null }; keyCache = { pem: '', key: null, spki: '' }; } };
