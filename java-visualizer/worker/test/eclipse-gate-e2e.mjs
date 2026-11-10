// מסך הכניסה האמיתי של DSViz (Swing, AccessGate.Dialog) בתוך Eclipse אמיתי (xvfb), מול השרת המקומי ואמולטור Firebase:
// הכפתור "מאגר תרגילים" → מסך הכניסה → "כניסה עם Google" של המורה → (דפדפן Playwright) חשבון חדש, "לא מורה", טלפון לא תקין ואז תקין
// → החלון ב-Eclipse: "החשבון עוד לא מוגדר כמורה" → ההנהלה מאשרת → כניסה שוב → "נכנסת בהצלחה" → המאגר נפתח. + צילומי מסך של כל שלב.
// Eclipse עצמו ב-build/eclipse-it/eclipse (bank.sh מוריד), והצד של Eclipse ב-tools/test/eclipse-it/{gate.sh,src/.../GateIt.java}.
//   cd worker/test && npx --yes firebase-tools@13.29.1 emulators:exec --config firebase.json --only firestore,auth --project demo-dsviz "node eclipse-gate-e2e.mjs"
//   (או: cd worker && npm run test:eclipse-gate)       משתנים: SHOTS (ברירת מחדל /tmp/eclipse-gate-shots)
import assert from 'node:assert/strict';
import { spawn } from 'node:child_process';
import { createHash } from 'node:crypto';
import { createRequire } from 'node:module';
import { existsSync, mkdirSync, readFileSync, rmSync, writeFileSync, createWriteStream } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { db } from '../src/access/firestore.js';

const here = dirname(fileURLToPath(import.meta.url));
const require = createRequire(import.meta.url);
const { chromium } = require('playwright');
const PORT = 8787, BASE = `http://127.0.0.1:${PORT}`;
const AUTH = process.env.FIREBASE_AUTH_EMULATOR_HOST || '127.0.0.1:9099';
const FS = process.env.FIRESTORE_EMULATOR_HOST || '127.0.0.1:8181';
const SHOTS = process.env.SHOTS || '/tmp/eclipse-gate-shots';
rmSync(SHOTS, { recursive: true, force: true });
mkdirSync(SHOTS, { recursive: true });
const EMAIL = 'gate.teacher.' + Date.now() + '@gmail.com';
const store = db({ FIREBASE_PROJECT_ID: 'demo-dsviz', FIRESTORE_EMULATOR_HOST: FS });

await fetch(`http://${FS}/emulator/v1/projects/demo-dsviz/databases/(default)/documents`, { method: 'DELETE' });
await fetch(`http://${AUTH}/emulator/v1/projects/demo-dsviz/accounts`, { method: 'DELETE' });
const server = spawn(process.execPath, [join(here, '../tools/dev-server.mjs')], { env: Object.assign({}, process.env, { PORT: String(PORT), ADMIN_EMAILS: 'motiml77@gmail.com' }), stdio: ['ignore', 'pipe', 'inherit'] });
await new Promise(res => server.stdout.on('data', d => { if (String(d).includes('dev server')) res(); }));
let eclipse, browser;
const cleanup = () => { try { eclipse && eclipse.kill(); } catch (e) { /* */ } try { server.kill(); } catch (e) { /* */ } };
process.on('exit', cleanup);

async function idToken(email, name) {
  const claims = { sub: 'uid-' + createHash('sha256').update(email).digest('hex').slice(0, 16), email, email_verified: true, name };
  const r = await fetch(`http://${AUTH}/identitytoolkit.googleapis.com/v1/accounts:signInWithIdp?key=fake-api-key`, {
    method: 'POST', headers: { 'content-type': 'application/json' },
    body: JSON.stringify({ postBody: 'id_token=' + encodeURIComponent(JSON.stringify(claims)) + '&providerId=google.com', requestUri: 'http://localhost', returnSecureToken: true }),
  });
  return (await r.json()).idToken;
}
async function api(method, path, body, token) {
  const r = await fetch(BASE + path, { method, headers: Object.assign({ 'content-type': 'application/json' }, token ? { authorization: 'Bearer ' + token } : {}), body: body ? JSON.stringify(body) : undefined });
  const j = await r.json();
  if (!r.ok) throw new Error(path + ' ' + JSON.stringify(j));
  return j;
}
async function googlePopup(page, clickSel, email, name) {
  const [popup] = await Promise.all([page.waitForEvent('popup'), page.click(clickSel)]);
  await popup.waitForLoadState();
  const existing = popup.locator('li', { hasText: email });
  if (await existing.count()) await existing.first().click();
  else {
    await popup.getByText('Add new account').click();
    await popup.fill('#email-input', email);
    await popup.fill('#display-name-input', name);
    await popup.click('#sign-in');
  }
  await popup.waitForEvent('close', { timeout: 15000 }).catch(() => {});
}
const sleep = ms => new Promise(r => setTimeout(r, ms));
async function waitFor(what, fn, ms) {
  const end = Date.now() + ms;
  while (Date.now() < end) { const v = fn(); if (v) return v; if (eclipseDone) throw new Error('Eclipse הסתיים לפני: ' + what); await sleep(300); }
  throw new Error('timeout: ' + what);
}
const fileText = f => existsSync(join(SHOTS, f)) ? readFileSync(join(SHOTS, f), 'utf8').trim() : '';
const shot = (page, name) => page.screenshot({ path: join(SHOTS, name + '.png') });

const admin = await idToken('motiml77@gmail.com', 'Moti');
const spki = (await api('GET', '/v1/access/key')).spki;

// ------------------------------------------------------------------ Eclipse אמיתי
let eclipseDone = false;
const log = createWriteStream(join(SHOTS, 'gate-sh.log'));
eclipse = spawn('sh', [join(here, '../../tools/test/eclipse-it/gate.sh')], {
  env: Object.assign({}, process.env, { GATE_WORKER: BASE, GATE_PASSKEY: spki, GATE_SHOTS: SHOTS }), stdio: ['ignore', 'pipe', 'pipe'],
});
eclipse.stdout.pipe(log, { end: false });
eclipse.stderr.pipe(log, { end: false });
const exited = new Promise(res => eclipse.on('exit', code => { eclipseDone = true; res(code); }));

let passed = 0;
async function step(name, fn) {
  try { await fn(); passed++; console.log('✓ ' + name); } catch (e) { console.error('✗ ' + name); console.error(e); process.exitCode = 1; throw e; }
}

try {
  browser = await chromium.launch(process.env.HTTPS_PROXY ? { args: ['--proxy-server=' + process.env.HTTPS_PROXY, '--proxy-bypass-list=<loopback>;127.0.0.1;localhost'] } : {});
  const ctx = await browser.newContext({ viewport: { width: 1280, height: 900 }, deviceScaleFactor: 1, locale: 'he-IL', ignoreHTTPSErrors: true });
  const page = await ctx.newPage();
  const errors = [];
  page.on('pageerror', e => errors.push(String(e)));

  await step('Eclipse: הכפתור "מאגר תרגילים" פתח את מסך הכניסה, ו"כניסה עם Google" של המורה פתחה (בקובץ) כתובת כניסה', async () => {
    const url = await waitFor('url-1.txt (Eclipse עולה, ההרחבה נבנית, החלון נפתח)', () => fileText('url-1.txt'), 15 * 60 * 1000);
    assert.match(url, /\/signin\?/);
    assert.match(url, /role=teacher/);
    await page.goto(url);
    await page.waitForSelector('.gbtn');
    await shot(page, 'b1-browser-signin');
  });

  await step('דפדפן: Google כחשבון חדש → "לא מורה"; טלפון לא תקין נדחה; טלפון תקין + בית ספר נשלחים', async () => {
    await googlePopup(page, '.gbtn', EMAIL, 'מורה חדשה מהחלון');
    await page.waitForURL(/r=not-teacher/);
    await shot(page, 'b2-browser-not-teacher');
    await page.fill('#school', 'תיכון בדיקה, חולון');
    await page.fill('#phone', '12345');
    await page.click('button:has-text("שליחת בקשה להנהלה")');
    await page.waitForSelector('.toast:has-text("10 ספרות")');
    await shot(page, 'b3-browser-bad-phone');
    await page.fill('#phone', '04-1234567');   // טלפון קווי — לא נייד ישראלי
    await page.click('button:has-text("שליחת בקשה להנהלה")');
    await page.waitForSelector('.toast');
    assert.ok(!(await api('GET', '/v1/admin/requests', null, admin)).requests.some(r => r.email === EMAIL), 'טלפון לא תקין לא נשלח');
    await page.fill('#phone', '052-345 6789');
    await page.click('button:has-text("שליחת בקשה להנהלה")');
    await page.waitForSelector('h1:has-text("הבקשה נשלחה")');
    await shot(page, 'b4-browser-request-sent');
    const req = (await api('GET', '/v1/admin/requests', null, admin)).requests.find(r => r.email === EMAIL);
    assert.ok(req && req.kind === 'teacher', 'הבקשה אצל ההנהלה');
    assert.equal(req.phone, '0523456789');
    assert.equal(req.school, 'תיכון בדיקה, חולון');
  });

  await step('Eclipse: החלון מראה "החשבון עוד לא מוגדר כמורה" → ההנהלה מאשרת', async () => {
    await waitFor('not-teacher.flag', () => fileText('not-teacher.flag'), 150000);
    const req = (await api('GET', '/v1/admin/requests', null, admin)).requests.find(r => r.email === EMAIL);
    await api('POST', '/v1/admin/request', { id: req.id, decision: 'approve', seats: 3 }, admin);
    writeFileSync(join(SHOTS, 'approved.flag'), '1');
  });

  await step('כניסה שנייה מהחלון ב-Eclipse (אחרי האישור) → הדפדפן נכנס כמורה → "נכנסת בהצלחה" והמאגר נפתח', async () => {
    const url = await waitFor('url-2.txt', () => fileText('url-2.txt'), 120000);
    await page.goto(url);
    await googlePopup(page, '.gbtn', EMAIL, 'מורה חדשה מהחלון');
    await page.waitForURL(/r=ok&role=teacher/, { timeout: 30000 });
    await shot(page, 'b5-browser-welcome');
    const row = (await api('GET', '/v1/admin/teachers', null, admin)).teachers.find(t => t.email === EMAIL);
    assert.deepEqual([row.phone, row.seats], ['0523456789', 3]);
  });

  const code = await Promise.race([exited, sleep(180000).then(() => 'timeout')]);
  assert.equal(code, 0, 'gate.sh הסתיים בשגיאה / בזמן ' + code);
  assert.deepEqual(errors, [], 'שגיאות JavaScript בדפים');

  // ------------------------------------------------------------------ מה Eclipse ראה (out.txt)
  const out = readFileSync(join(SHOTS, 'out.txt'), 'utf8');
  const has = (re, name) => { assert.match(out, re, name); passed++; console.log('✓ ' + name); };
  has(/^ACCESS-BEFORE none$/m, 'אין כניסה שמורה לפני');
  has(/^TOOLBAR-COMMAND true$/m, 'הפקודה "מאגר תרגילים" קיימת ויש לה מטפל');
  has(/^GATE-OPEN true$/m, 'לחיצה על "מאגר תרגילים" פותחת את מסך הכניסה האמיתי');
  has(/^GATE-RTL title-right=true teacher-card-right=true /m, 'החלון מימין לשמאל');
  has(/^GATE-TEXT .*מורה.*עמדת מחשב בכיתה.*תלמיד/m, 'המסך מציג את שלוש האפשרויות');
  has(/^CLICK כניסה עם Google$/m, 'נלחץ הכפתור "כניסה עם Google"');
  has(/^URL1 teacher=true signin=true$/m, 'התוכנה ביקשה כניסת מורה בדפדפן');
  has(/^WAITING-TEXT true .*דפדפן/m, 'החלון מסביר: נפתח דפדפן');
  has(/^NOT-TEACHER true$/m, 'החלון: "החשבון עוד לא מוגדר כמורה"');
  has(/^NOT-TEACHER-TEXT .*בדפדפן אפשר לשלוח בקשה להנהלה/m, 'החלון מסביר מה עושים');
  has(/^NOT-TEACHER-ACCESS none$/m, 'עדיין אין כניסה שמורה');
  has(/^CLICK חזרה$/m, 'נלחץ "חזרה"');
  has(/^URL2 teacher=true$/m, 'כניסה שנייה של מורה');
  has(/^SUCCESS true$/m, 'החלון: "נכנסת בהצלחה"');
  has(/^ACCESS-AFTER מורה: .* teacher=true$/m, 'התוכנה זוכרת מורה');
  has(/^BANK-OPEN true$/m, 'המאגר נפתח אחרי הכניסה');
  has(/^TEACHER-BTN-BEFORE defined=true handled=true enabled=false$/m, '"עמדת ניהול" בסרגל: קיים ואפור לפני כניסה כמורה');
  has(/^TEACHER-BTN-AFTER defined=true handled=true enabled=true$/m, '"עמדת ניהול" בסרגל: פעיל אחרי כניסה כמורה');
  has(/^TEACHER-PANEL-OPEN true$/m, 'לחיצה על "עמדת ניהול" פותחת את חלון המורה');
  // ב-xvfb בלי מנהל חלונות הפוקוס לא דטרמיניסטי (אצל משתמש ב-Windows ההקדמה עובדת) — לכן בודקים שהחלון נראה ובתוך המסך
  has(/^TEACHER-PANEL-FRONT active=(true|false) focused=(true|false) visible=true onscreen=true$/m, 'חלון המורה נפתח, נראה ובתוך המסך (הפוקוס עצמו לא נבדק ב-xvfb)');
  has(/^TEACHER-PANEL-COUNT 1$/m, 'לחיצה נוספת לא פותחת עוד חלון');
  has(/^BANK-RTL true /m, 'חלון המאגר מימין לשמאל');
  has(/^BANK-DIAG rows=9 first=Rectangle \{0, 0, /m, 'המאגר: כל 9 הנושאים מצוירים בתוך הסרגל (לא מחוץ לתצוגה)');
  has(/^BANK2-DIAG rows=9 first=Rectangle \{0, 0, /m, 'פתיחה שנייה של המאגר (בלי מסך כניסה) — גם כן');
  has(/^GATE-CLOSED true$/m, 'מסך הכניסה נסגר');
  const teacher = (await api('GET', '/v1/admin/teacher?email=' + EMAIL, null, admin)).teacher;
  has(/^PANEL-OPEN true$/m, '"עמדת ניהול" נפתחת שוב');
  has(new RegExp('^PANEL-TEXT .*' + EMAIL.replace(/[.]/g, '\\.') + '.*' + teacher.code + '.*עמדות מחשב בשימוש: 0 מתוך 3', 'm'), 'עמדת הניהול: המייל, הקוד (כמו בשרת) ו-0 מתוך 3');
  has(/^SIGNOUT-ASK .*להתנתק.*במחשב הזה/m, '"התנתקות" — שואלים בעברית');
  has(/^SIGNOUT-TOLD .*התנתקת/m, 'הודעה אחרי ההתנתקות');
  has(/^SIGNOUT-PANEL-CLOSED true$/m, 'עמדת הניהול נסגרת');
  has(/^SIGNOUT-ACCESS none$/m, 'הכניסה נמחקה מהמחשב');
  has(/^TEACHER-BTN-SIGNEDOUT defined=true handled=true enabled=false$/m, '"עמדת ניהול" בסרגל נכבה מיד אחרי ההתנתקות');
  has(/^GATE-AGAIN true$/m, '"מאגר תרגילים" אחרי ההתנתקות — מסך הכניסה');
  const ses = (await store.query('sessions', { where: [['email', '==', EMAIL]] })).filter(x => !x.revoked);
  assert.equal(ses.length, 0, 'הכרטיס בוטל בשרת');
  passed++; console.log('✓ הכרטיס של המורה בוטל בשרת');
  assert.doesNotMatch(out, /^ERROR /m, 'שגיאות בזמן הבדיקה ב-Eclipse');
  assert.match(out, /^DONE$/m);
  console.log(`\n${passed} בדיקות עברו · צילומים: ${SHOTS}`);
} catch (e) {
  console.error(e);
  process.exitCode = 1;
} finally {
  try { await browser.close(); } catch (e) { /* */ }
  cleanup();
}
