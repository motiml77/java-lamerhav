// תמונות הדרכה למורים: Eclipse אמיתי (xvfb, tools/test/eclipse-it/GuideIt.java דרך gate.sh) + הדפים בדפדפן, מול השרת המקומי ואמולטור Firebase.
// מורה נכנס (Google) → תלמיד נכנס עם Google וקוד המורה → מחשב בכיתה מתחבר כ"עמדת מחשב" → עמדת הניהול: המחשבים והתלמידים בנפרד
// → המורה משנה את שם העמדה → במחשב עצמו, בפתיחה הבאה של המאגר, השם החדש בתחתית.
//   cd worker/test && npx --yes firebase-tools@13.29.1 emulators:exec --config firebase.json --only firestore,auth --project demo-dsviz "node guide-e2e.mjs"
//   משתנים: SHOTS (ברירת מחדל /tmp/guide-shots), ECLIPSE_DIR (ל-gate.sh)
import assert from 'node:assert/strict';
import { spawn } from 'node:child_process';
import { createHash, randomBytes } from 'node:crypto';
import { createRequire } from 'node:module';
import { existsSync, mkdirSync, readFileSync, rmSync, writeFileSync, createWriteStream } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const require = createRequire(import.meta.url);
const { chromium } = require('playwright');
const PORT = 8787, BASE = `http://127.0.0.1:${PORT}`;
const AUTH = process.env.FIREBASE_AUTH_EMULATOR_HOST || '127.0.0.1:9099';
const FS = process.env.FIRESTORE_EMULATOR_HOST || '127.0.0.1:8181';
const SHOTS = process.env.SHOTS || '/tmp/guide-shots';
rmSync(SHOTS, { recursive: true, force: true });
mkdirSync(SHOTS, { recursive: true });
const TEACHER = 'sara.demo@example.com', STUDENT = 'yonatan.demo@example.com';

await fetch(`http://${FS}/emulator/v1/projects/demo-dsviz/databases/(default)/documents`, { method: 'DELETE' });
await fetch(`http://${AUTH}/emulator/v1/projects/demo-dsviz/accounts`, { method: 'DELETE' });
const server = spawn(process.execPath, [join(here, '../tools/dev-server.mjs')], { env: Object.assign({}, process.env, { PORT: String(PORT), ADMIN_EMAILS: 'admin@test.dev' }), stdio: ['ignore', 'pipe', 'inherit'] });
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
async function api(method, path, body, headers = {}) {
  const r = await fetch(BASE + path, { method, headers: Object.assign({ 'content-type': 'application/json' }, headers), body: body ? JSON.stringify(body) : undefined });
  const j = await r.json();
  if (!r.ok) throw new Error(path + ' ' + JSON.stringify(j));
  return j;
}
const bearer = t => ({ authorization: 'Bearer ' + t });
const mid = () => randomBytes(16).toString('hex');
async function studentPlugin(email, name) {
  const verifier = randomBytes(32).toString('base64url');
  const challenge = createHash('sha256').update(verifier).digest('base64url');
  const ex = await api('POST', '/v1/auth/exchange', { idToken: await idToken(email, name), role: 'student', challenge });
  const m = mid();
  const r = await api('POST', '/v1/auth/redeem', { code: ex.code, verifier, machine: m, machineName: 'מחשב בבית', client: 'test', caps: ['classroom'] });
  return { 'x-dsviz-access': r.token, 'x-dsviz-machine': m };
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
let eclipseDone = false;
async function waitFor(what, fn, ms) {
  const end = Date.now() + ms;
  while (Date.now() < end) { const v = fn(); if (v) return v; if (eclipseDone) throw new Error('Eclipse הסתיים לפני: ' + what); await sleep(300); }
  throw new Error('timeout: ' + what);
}
const fileText = f => existsSync(join(SHOTS, f)) ? readFileSync(join(SHOTS, f), 'utf8').trim() : '';
const shot = (page, name) => page.screenshot({ path: join(SHOTS, name + '.png') });
/** צילום של אזור בדף (rootSel) + מקומות הלחיצה בתוכו (marks/<name>.json: id → [x, y, w, h] בפיקסלים של התמונה, פי 2) */
async function snap(page, name, rootSel, targets = {}, pad = 24) {
  await page.evaluate(() => document.activeElement && document.activeElement.blur && document.activeElement.blur());
  const sc = await page.evaluate(() => [scrollX, scrollY]);
  const rb = await page.locator(rootSel).first().boundingBox();
  const clip = { x: Math.max(0, rb.x + sc[0] - pad), y: Math.max(0, rb.y + sc[1] - pad), width: rb.width + 2 * pad, height: rb.height + 2 * pad };
  const marks = {};
  for (const [id, sel] of Object.entries(targets)) {
    const b = await page.locator(sel).first().boundingBox();
    assert.ok(b, 'אין ' + sel + ' ב-' + name);
    marks[id] = [b.x + sc[0] - clip.x, b.y + sc[1] - clip.y, b.width, b.height].map(v => Math.round(v * 2));
  }
  await page.screenshot({ path: join(SHOTS, name + '.png'), clip, fullPage: true });
  mkdirSync(join(SHOTS, 'marks'), { recursive: true });
  writeFileSync(join(SHOTS, 'marks', name + '.json'), JSON.stringify(marks));
}

// ------------------------------------------------------------------ נתוני דוגמה: המורה (ההנהלה מגדירה), עוד מחשבים ותלמידים עם הגשות
const admin = await idToken('admin@test.dev', 'מנהל');
await api('POST', '/v1/admin/teacher', { create: true, email: TEACHER, name: 'שרה כהן', school: 'תיכון לדוגמה', seats: 20 }, bearer(admin));
const code = (await api('GET', '/v1/admin/teacher?email=' + TEACHER, null, bearer(admin))).teacher.code;
writeFileSync(join(SHOTS, 'code.txt'), code);
const stations = {};
for (const n of [3, 5]) {
  const m = mid();
  const r = await api('POST', '/v1/station/claim', { code, name: 'מעבדה 2 · עמדה ' + n, machine: m, client: 'dsviz-eclipse/1.0.4', caps: ['register', 'classroom'] });
  assert.equal(r.status, 'ok', JSON.stringify(r));
  stations[n] = { 'x-dsviz-access': r.token, 'x-dsviz-machine': m };
}
const Q_EXP = 'סעיף א: 18\nסעיף ב: 2\nהתור אחרי: [3,8,1,6]';
const QUEUE_CS = 'public static int SumQueue(Queue<int> q)\n{\n    int sum = 0;\n    while (!q.IsEmpty())\n        sum += q.Remove();\n    return sum;\n}\n';
const QUEUE_JAVA = 'public static int sumQueue(Queue<Integer> q) {\n    Queue<Integer> temp = new Queue<Integer>();\n    int sum = 0;\n    while (!q.isEmpty()) {\n        int x = q.remove();\n        sum += x;\n        temp.insert(x);\n    }\n    while (!temp.isEmpty())\n        q.insert(temp.remove());\n    return sum;\n}\n';
const bank = (over) => Object.assign({ exercise: { source: 'bank', bankId: 'queue-sum-even', topic: 'תור', title: 'סכום תור וזוגיים', level: 'קל' }, app: 'Visual Studio 2022' }, over);
await api('POST', '/v1/submit', bank({ who: 'נועם ואריאל', files: [{ path: 'src/ex01_queues/QueueTargil1.java', text: QUEUE_JAVA }], app: 'Eclipse',
  check: { ran: true, compiles: true, output: Q_EXP, expected: Q_EXP, outputMatch: true } }), stations[3]);
await api('POST', '/v1/submit', bank({ who: 'הלל', files: [{ path: 'src/ex01_queues/QueueTargil1.java', text: QUEUE_JAVA.replace('q.insert(temp.remove());', '') }], app: 'Eclipse',
  check: { ran: true, compiles: true, output: 'סעיף א: 18\nסעיף ב: 2\nהתור אחרי: []', expected: Q_EXP, outputMatch: false } }), stations[5]);
const kids = [['noam.demo@example.com', 'נועם לוי'], ['ariel.demo@example.com', 'אריאל כהן'], ['ido.demo@example.com', 'עידו שפירא']];
const plug = {};
for (const [email, name] of kids) {
  plug[email] = await studentPlugin(email, name);
  await api('POST', '/v1/student/join', { code }, plug[email]);
}
const teacherTok = await idToken(TEACHER, 'שרה כהן');
const c1 = (await api('POST', '/v1/me/class', { op: 'create', name: 'יא 3' }, bearer(teacherTok))).id;
for (const [email] of kids) await api('POST', '/v1/me/member', { op: 'move', student: email, classId: c1 }, bearer(teacherTok));
await api('POST', '/v1/submit', bank({ files: [{ path: 'src/ex01_queues/QueueTargil1.java', text: QUEUE_JAVA }], app: 'Eclipse', check: { ran: true, compiles: true, output: Q_EXP, expected: Q_EXP, outputMatch: true } }), plug['noam.demo@example.com']);
await api('POST', '/v1/submit', bank({ files: [{ path: 'QueueTargil1.java', text: QUEUE_JAVA.replace('q.insert(temp.remove());', '') }], app: 'Eclipse', check: { ran: true, compiles: true, output: 'סעיף א: 18\nסעיף ב: 2\nהתור אחרי: []', expected: Q_EXP, outputMatch: false } }), plug['ariel.demo@example.com']);
console.log('נתוני דוגמה מוכנים · קוד המורה ' + code);

// ------------------------------------------------------------------ Eclipse אמיתי
const spki = (await api('GET', '/v1/access/key')).spki;
const log = createWriteStream(join(SHOTS, 'gate-sh.log'));
eclipse = spawn('sh', [join(here, '../../tools/test/eclipse-it/gate.sh')], {
  env: Object.assign({}, process.env, { GATE_WORKER: BASE, GATE_PASSKEY: spki, GATE_SHOTS: SHOTS, GATE_APP: 'guide' }), stdio: ['ignore', 'pipe', 'pipe'],
});
eclipse.stdout.pipe(log, { end: false });
eclipse.stderr.pipe(log, { end: false });
const exited = new Promise(res => eclipse.on('exit', c => { eclipseDone = true; res(c); }));

let passed = 0;
async function step(name, fn) {
  try { await fn(); passed++; console.log('✓ ' + name); } catch (e) { console.error('✗ ' + name); console.error(e); process.exitCode = 1; throw e; }
}

try {
  browser = await chromium.launch(process.env.HTTPS_PROXY ? { args: ['--proxy-server=' + process.env.HTTPS_PROXY, '--proxy-bypass-list=<loopback>;127.0.0.1;localhost'] } : {});
  const newPage = async () => (await browser.newContext({ viewport: { width: 1280, height: 860 }, deviceScaleFactor: 2, locale: 'he-IL' })).newPage();
  const errors = [];

  await step('מורה: Eclipse ביקש כניסת מורה → בדפדפן Google → "נכנסת בהצלחה"', async () => {
    const url = await waitFor('url-1.txt', () => fileText('url-1.txt'), 15 * 60 * 1000);
    assert.match(url, /role=teacher/);
    const p = await newPage();
    p.on('pageerror', e => errors.push(String(e)));
    await p.goto(url);
    await p.waitForSelector('.gbtn');
    await snap(p, 'b01-teacher-signin', 'main .panel', { google: '.gbtn' });
    await googlePopup(p, '.gbtn', TEACHER, 'שרה כהן');
    await p.waitForURL(/r=ok&role=teacher/, { timeout: 30000 });
    await p.waitForSelector('h1:has-text("נכנסת בהצלחה")');
    await sleep(500);
    await snap(p, 'b02-teacher-welcome', 'main .panel', {});
  });

  await step('תלמיד: Eclipse ביקש כניסת תלמיד → Google → הרשמה עם קוד המורה → חזרה לתוכנה', async () => {
    const url = await waitFor('url-2.txt', () => fileText('url-2.txt'), 5 * 60 * 1000);
    assert.match(url, /role=student/);
    const p = await newPage();
    p.on('pageerror', e => errors.push(String(e)));
    await p.goto(url);
    await p.waitForSelector('.gbtn');
    await snap(p, 'b03-student-signin', 'main .panel', { google: '.gbtn' });
    await googlePopup(p, '.gbtn', STUDENT, 'יונתן לוי');
    await p.waitForSelector('#reg-name');
    await p.fill('#reg-name', 'יונתן לוי');
    await p.fill('#reg-phone', '050-123 4567');
    await p.check('#reg-mode-teacher');
    await p.fill('#reg-code', code);
    await snap(p, 'b04-student-register', 'main .panel', { name: '#reg-name', phone: '#reg-phone', mode: 'label[for=reg-mode-teacher]', code: '#reg-code', submit: 'form button[type=submit]' });
    await p.click('form button[type=submit]');
    await p.waitForURL(/\/welcome\?r=ok/, { timeout: 30000 });
    await sleep(800);
    await snap(p, 'b05-student-welcome', 'main .panel', {});
  });

  const tp = await newPage();
  tp.on('pageerror', e => errors.push(String(e)));
  await step('עמדת הניהול: "מחשבי המעבדה" — גם המחשב שהתחבר עכשיו מ-Eclipse', async () => {
    await waitFor('station.done', () => fileText('station.done'), 5 * 60 * 1000);
    await tp.goto(BASE + '/account');
    await googlePopup(tp, '.gbtn', TEACHER, 'שרה כהן');
    await tp.waitForSelector('#tab-st');
    await tp.waitForSelector('.con-table tbody tr:has-text("מחשב 14")');
    const r14 = 'tr:has-text("מחשב 14")';
    await snap(tp, 'b06-console-stations', 'section.con', { tab: '#tab-st', subs: r14 + ' button:has-text("הגשות")', rename: r14 + ' button:has-text("שינוי שם")', release: r14 + ' button:has-text("שחרור")' }, 0);
  });

  await step('שינוי שם של המחשב בעמדת הניהול', async () => {
    const row = tp.locator('.con-table tbody tr', { hasText: 'מחשב 14' });
    await row.locator('button:has-text("שינוי שם")').click();
    await tp.fill('#rename-st', 'מעבדה 2 · עמדה 14');
    await snap(tp, 'b07-console-rename', 'section.con', { rename: 'tr:has-text("מחשב 14") button:has-text("שינוי שם")', input: '#rename-st', save: '.confirm button:has-text("שמירה")' }, 0);
    await tp.press('#rename-st', 'Enter');
    await tp.waitForSelector('.toast:has-text("השם נשמר")');
    await tp.waitForSelector('.con-table tbody tr:has-text("מעבדה 2 · עמדה 14")');
    writeFileSync(join(SHOTS, 'renamed.flag'), 'מעבדה 2 · עמדה 14');
    await tp.waitForSelector('.toast', { state: 'hidden', timeout: 15000 }).catch(() => {});
    await sleep(800);
  });

  await step('הגשות ממחשב בכיתה: שם המגיש, הקוד ובדיקת ההרצה', async () => {
    await tp.click('tr:has-text("מעבדה 2 · עמדה 5") button:has-text("הגשות")');
    await tp.waitForSelector('text=מגיש: הלל');
    await sleep(500);
    await snap(tp, 'b08-console-station-submission', 'section.con', { subs: 'tr:has-text("מעבדה 2 · עמדה 5") button:has-text("הגשות")', check: 'text=בדיקת ההרצה', del: '.d-code .d-top button:has-text("מחיקה")' }, 0);
  });

  await step('עמדת הניהול: "תלמידים" — כיתה, ויונתן שהצטרף עכשיו מ-Eclipse ב"בלי כיתה"', async () => {
    await tp.click('#tab-su');
    await tp.waitForSelector('.folder');
    await snap(tp, 'b09-console-students', 'section.con', { tab: '#tab-su', folder: '.folder:has-text("יא 3")', move: 'tr:has-text("יונתן לוי") select', join: '#join-open' }, 0);
    const all = await tp.textContent('section.con');
    assert.match(all, /יונתן לוי/);
    await tp.click('.folder:has-text("יא 3")');
    await sleep(500);
    await tp.click('tr:has-text("אריאל כהן") button:has-text("תרגילים")');
    await tp.waitForSelector('text=הפלט שונה');
    await sleep(1200);
    await snap(tp, 'b10-console-student-work', 'section.con', { work: 'tr:has-text("אריאל כהן") button:has-text("תרגילים")', grade: '#grade-in', save: '#grade-save' }, 0);
  });

  const rc = await Promise.race([exited, sleep(240000).then(() => 'timeout')]);
  assert.equal(rc, 0, 'gate.sh: ' + rc);
  assert.deepEqual(errors, [], 'שגיאות JavaScript בדפים');
  const out = readFileSync(join(SHOTS, 'out.txt'), 'utf8');
  const has = (re, name) => { assert.match(out, re, name); passed++; console.log('✓ ' + name); };
  has(/^TEACHER-SUCCESS true$/m, 'Eclipse: המורה נכנס');
  has(/^TEACHER-PANEL true$/m, 'Eclipse: "עמדת ניהול" נפתחת');
  has(/^TEACHER-SIGNOUT ok now=none$/m, 'המורה התנתק');
  has(/^STUDENT-SUCCESS true$/m, 'Eclipse: התלמיד נכנס');
  has(/^STUDENT-BANK true יונתן לוי · המורה: שרה כהן student=true$/m, 'התלמיד משויך למורה');
  has(/^STUDENT-SIGNOUT ok now=none$/m, 'התלמיד התנתק');
  has(/^STATION-SUCCESS true$/m, 'Eclipse: המחשב התחבר כעמדה');
  has(/^STATION-LINE 🖥\s+עמדת מחשב: מחשב 14 · המורה: שרה כהן$/m, 'בתחתית המאגר: שם העמדה והמורה');
  has(/^STATION-LINE-RENAMED 🖥\s+עמדת מחשב: מעבדה 2 · עמדה 14 · המורה: שרה כהן$/m, 'אחרי שהמורה שינה את השם — המחשב מראה את השם החדש');
  assert.doesNotMatch(out, /^ERROR /m, 'שגיאות ב-Eclipse');
  console.log(`\n${passed} בדיקות עברו · צילומים: ${SHOTS}`);
} catch (e) {
  console.error(e);
  process.exitCode = 1;
} finally {
  try { await browser.close(); } catch (e) { /* */ }
  cleanup();
  setTimeout(() => process.exit(process.exitCode || 0), 500);   // הצינורות של Eclipse (gate-sh.log) לא משאירים את התהליך פתוח
}
