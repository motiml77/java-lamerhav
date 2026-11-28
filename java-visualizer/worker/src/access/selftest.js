// בדיקת מערכת (מסוף ההנהלה): השרת האמיתי בודק את עצמו מול Firebase / Google / Cloudflare — מה עובד, ומה חסר בהגדרות.
// האמולטור שבבדיקות לא תופס הכול (אינדקסים, הרשאות של חשבון השירות, כללי האבטחה), ולכן אחרי כל פריסה לוחצים על
// "בדיקת מערכת" במסוף: כל שורה ירוקה — או הסבר מה לתקן. לא משנה שום נתון אמיתי (כותבת ומוחקת רק מסמך בדיקה ב-selftest/).
//
// שני חלקים (part=1 / part=2), כדי להישאר בתוך מגבלת הפניות של Cloudflare לבקשה אחת.

import { db, indexMisses } from './firestore.js';
import { firestoreEmulator, authEmulator, projectId, serviceToken } from './google.js';
import { importRsaPrivate, signRs256 } from './crypto.js';
import { payProvider } from './pay.js';
import { mailReady } from './alerts.js';

const ok = (id, title, detail) => ({ id, title, state: 'ok', detail: detail || '' });
const warn = (id, title, detail) => ({ id, title, state: 'warn', detail: detail || '' });
const fail = (id, title, detail) => ({ id, title, state: 'fail', detail: detail || '' });
const short = e => String(e && e.message || e).slice(0, 300);

/** כל צורות השאילתה שהשרת משתמש בהן (עם ערך שלא קיים — בלי תוצאות). שאילתה שדורשת אינדקס שלא קיים — מתגלה כאן */
const QUERIES = [
  ['classes', { where: [['teacher', '==', '-']] }],
  ['members', { where: [['teacher', '==', '-']] }],
  ['members', { where: [['teacher', '==', '-'], ['classId', '==', '-']] }],
  ['stations', { where: [['teacher', '==', '-']] }],
  ['stations', { where: [['active', '==', true]], limit: 1 }],
  ['submissions', { where: [['teacher', '==', '-'], ['from', '==', 'station']] }],
  ['submissions', { where: [['teacher', '==', '-'], ['student', '==', '-']], limit: 1 }],
  ['submissions', { where: [['teacher', '==', '-'], ['station', '==', '-']] }],
  ['submissions', { where: [['expiresAt', '<', new Date(0)]], limit: 1 }],
  ['homework', { where: [['teacher', '==', '-']] }],
  ['sessions', { where: [['email', '==', '-']] }],
  ['requests', { where: [['email', '==', '-']] }],
  ['requests', { where: [['status', '==', '-']] }],
  ['requests', { orderBy: ['createdAt', 'desc'], limit: 1 }],
  ['payments', { where: [['email', '==', '-']] }],
  ['payments', { where: [['createdAt', '>=', '9999']] }],
  ['payments', { orderBy: ['createdAt', 'desc'], limit: 1 }],
  ['students', { where: [['accessUntil', '>=', '9999']] }],
  ['log', { orderBy: ['at', 'desc'], limit: 1 }],
  ['log', { where: [['at', '<', '0000']], orderBy: ['at', 'desc'], limit: 1 }],
  ['alerts', { where: [['seen', '==', false]], limit: 1 }],
  ['alerts', { orderBy: ['at', 'desc'], limit: 1 }],
  ['reports', { where: [['status', '==', '-']] }],
  ['reports', { where: [['sig', '==', '-']] }],
];

async function partData(env) {
  const out = [];
  const store = db(env);
  const id = 'check-' + Date.now().toString(36);
  const path = 'selftest/' + id;
  // 1. חשבון השירות + קריאה / כתיבה / מחיקה
  try {
    const t0 = Date.now();
    await serviceToken(env);
    await store.set(path, { text: 'בדיקה', n: 1, nested: { a: 1 }, when: new Date(), list: ['x'] });
    const d = await store.get(path);
    if (!d || d.text !== 'בדיקה' || d.n !== 1 || !d.nested || d.nested.a !== 1) throw new Error('המסמך נקרא חזרה אחרת ממה שנכתב');
    out.push(ok('firestore', 'Firestore: כתיבה וקריאה', `פרויקט ${projectId(env)} · ${Date.now() - t0} אלפיות שנייה`));
  } catch (e) {
    out.push(fail('firestore', 'Firestore: כתיבה וקריאה', 'השרת לא מצליח לכתוב ל-Firestore: ' + short(e)
      + '. בודקים ב-Cloudflare את הסוד FIREBASE_SERVICE_ACCOUNT (קובץ ה-JSON של חשבון השירות, עם התפקיד Cloud Datastore User) ואת FIREBASE_PROJECT_ID.'));
    return out;   // בלי Firestore אין טעם בשאר
  }
  // 2. שינוי חלקי של שדה בתוך מפה + מונה (התקציר של התלמיד, מונה ההגשות)
  try {
    await store.update(path, { 'nested.b': { t: 'x' } }, { n: 2 });
    const d = await store.get(path);
    if (!d || d.n !== 3 || !d.nested || d.nested.a !== 1 || !d.nested.b || d.nested.b.t !== 'x') throw new Error(JSON.stringify(d));
    out.push(ok('merge', 'Firestore: עדכון חלקי ומונים'));
  } catch (e) {
    out.push(fail('merge', 'Firestore: עדכון חלקי ומונים', short(e)));
  }
  // 3. טרנזקציה (חיבור עמדה, הצטרפות למורה, שחרור עמדה)
  try {
    const v = await store.tx(async t => { const d = await t.get(path); t.update(path, { tx: true }); return d && d.n; });
    const d = await store.get(path);
    if (v !== 3 || !d || d.tx !== true) throw new Error('הטרנזקציה לא נשמרה');
    out.push(ok('tx', 'Firestore: טרנזקציות'));
  } catch (e) {
    out.push(fail('tx', 'Firestore: טרנזקציות', short(e)));
  }
  await store.remove(path).catch(() => {});
  // 4. כללי האבטחה: בלי כניסה אסור לקרוא כלום (רק השרת ניגש לנתונים)
  if (firestoreEmulator(env)) {
    out.push(ok('rules', 'כללי האבטחה של Firestore', 'אמולטור (בבדיקות) — לא נבדק'));
  } else {
    try {
      const res = await fetch(`https://firestore.googleapis.com/v1/projects/${projectId(env)}/databases/(default)/documents/config/settings`);
      if (res.status === 403 || res.status === 401) out.push(ok('rules', 'כללי האבטחה של Firestore', 'סגור מבחוץ — רק השרת ניגש לנתונים'));
      else out.push(fail('rules', 'כללי האבטחה של Firestore', `קריאה בלי כניסה לא נחסמה (תשובה ${res.status}). ב-Firebase Console → Firestore → Rules צריך להיות: allow read, write: if false;`));
    } catch (e) {
      out.push(warn('rules', 'כללי האבטחה של Firestore', 'לא הצלחתי לבדוק: ' + short(e)));
    }
  }
  // 5. האישור החתום (המאגר נפתח גם בלי אינטרנט)
  try {
    if (!env.PASS_KEY) throw new Error('הסוד PASS_KEY לא מוגדר');
    const sig = await signRs256(await importRsaPrivate(env.PASS_KEY), 'selftest');
    if (!sig || !sig.byteLength) throw new Error('החתימה ריקה');
    out.push(ok('pass', 'האישור החתום (עבודה בלי אינטרנט)'));
  } catch (e) {
    out.push(fail('pass', 'האישור החתום (עבודה בלי אינטרנט)', short(e) + '. בלי PASS_KEY תקין התוספים לא פותחים את המאגר.'));
  }
  // 6. כניסה עם Google בדפים
  if (authEmulator(env)) out.push(ok('google', 'כניסה עם Google', 'אמולטור (בבדיקות)'));
  else if (env.FIREBASE_API_KEY && env.FIREBASE_AUTH_DOMAIN) out.push(ok('google', 'כניסה עם Google', 'הכניסה שלך למסוף עברה אימות מול Google · ' + env.FIREBASE_AUTH_DOMAIN));
  else out.push(fail('google', 'כניסה עם Google', 'חסר FIREBASE_API_KEY / FIREBASE_AUTH_DOMAIN בהגדרות השרת — הדפים לא יוכלו לפתוח את הכניסה של Google.'));
  // 7. הגבלות קצב (קוד מורה שגוי, הגשות)
  const kv = env.USAGE && typeof env.USAGE.get === 'function' ? env.USAGE : env.STATS && typeof env.STATS.get === 'function' ? env.STATS : null;
  if (!kv) {
    out.push(warn('kv', 'הגבלות קצב', 'אין KV (USAGE / STATS) — אין הגבלה על ניסיונות קוד שגויים ועל מספר ההגשות בשעה. מומלץ להוסיף.'));
  } else {
    try {
      await kv.put('selftest', '1', { expirationTtl: 120 });
      out.push(ok('kv', 'הגבלות קצב', 'קוד שגוי: עד 30 בשעה · הגשות: עד 30 בשעה מכל מחשב'));
    } catch (e) {
      out.push(warn('kv', 'הגבלות קצב', 'ה-KV לא נכתב: ' + short(e)));
    }
  }
  // 8. לא חובה: מייל התראות, תשלום
  out.push(mailReady(env) ? ok('mail', 'התראות במייל') : warn('mail', 'התראות במייל', 'לא מוגדר (RESEND_API_KEY) — ההתראות מופיעות רק בפעמון שבמסוף. לא חובה.'));
  out.push(payProvider(env) ? ok('pay', 'שירות תשלום') : warn('pay', 'שירות תשלום', 'לא מוגדר — בסדר כל עוד התלמידים בחינם ואין מכירת חבילות עמדות.'));
  return out;
}

async function partQueries(env) {
  const out = [];
  const store = db(env);
  const before = indexMisses.length;
  const failed = [];
  await Promise.all(QUERIES.map(async ([c, q]) => {
    try { await store.query(c, q); } catch (e) { failed.push(`${c} [${(q.where || []).map(w => w[0] + w[1]).join(', ')}${q.orderBy ? ' ↓' + q.orderBy[0] : ''}]: ${short(e)}`); }
  }));
  const missed = indexMisses.slice(before);
  if (failed.length) {
    out.push(fail('queries', 'Firestore: השאילתות של המערכת', `${failed.length} מתוך ${QUERIES.length} נכשלו — ` + failed.slice(0, 4).join(' · ')));
  } else if (missed.length) {
    out.push(warn('queries', 'Firestore: השאילתות של המערכת', `כל ${QUERIES.length} עובדות, אבל ${missed.length} רצות בדרך איטית כי חסר אינדקס (${missed.map(m => m.collection + ': ' + m.fields.join('+')).join(' · ')}). `
      + 'הקישור ליצירת האינדקס: ' + ((/https:\/\/console\.firebase\.google\.com\S+/.exec(missed[0].message) || [''])[0] || 'ב-Firebase Console → Firestore → Indexes')));
  } else {
    out.push(ok('queries', 'Firestore: השאילתות של המערכת', `כל ${QUERIES.length} הצורות עובדות — לא חסר שום אינדקס`));
  }
  // מחיקה אוטומטית של הגשות מעמדות: השרת מנקה לבד (purgeExpired); מדיניות TTL ב-Firestore היא תוספת, לא חובה
  out.push(ok('expire', 'מחיקת הגשות ישנות מעמדות', 'השרת מוחק לבד הגשות שפג תוקפן (כשמגישים וכשפותחים את עמדת הניהול)'));
  return out;
}

export async function selfTest(env, url) {
  const part = url && url.searchParams.get('part') === '2' ? 2 : 1;
  const checks = part === 2 ? await partQueries(env) : await partData(env);
  return { ok: true, part, parts: 2, at: new Date().toISOString(), emulator: !!firestoreEmulator(env), checks };
}
