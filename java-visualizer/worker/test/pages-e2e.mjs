// בדיקה בדפדפן אמיתי (Playwright + Chromium) של הדפים: מסוף ההנהלה, הכניסה מתוך התוכנה, תשלום, הלוח של המורה.
// רץ מול אמולטור Firebase ושרת מקומי (tools/dev-server.mjs), עם נתוני דוגמה. צילומי מסך: SHOTS (ברירת מחדל test/shots).
//   cd test && npx firebase-tools@13.29.1 emulators:exec --config firebase.json --only firestore,auth --project demo-dsviz "node pages-e2e.mjs"
import assert from 'node:assert/strict';
import { spawn } from 'node:child_process';
import http from 'node:http';
import { createHash, randomBytes } from 'node:crypto';
import { createRequire } from 'node:module';
import { mkdirSync, mkdtempSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { db } from '../src/access/firestore.js';

const here = dirname(fileURLToPath(import.meta.url));
const require = createRequire(import.meta.url);
const { chromium } = require('playwright');
const PORT = 8787, BASE = `http://127.0.0.1:${PORT}`;
const AUTH = process.env.FIREBASE_AUTH_EMULATOR_HOST || '127.0.0.1:9099';
const FS = process.env.FIRESTORE_EMULATOR_HOST || '127.0.0.1:8181';
const SHOTS = process.env.SHOTS || join(here, 'shots');
mkdirSync(SHOTS, { recursive: true });
const store = db({ FIREBASE_PROJECT_ID: 'demo-dsviz', FIRESTORE_EMULATOR_HOST: FS });

await fetch(`http://${FS}/emulator/v1/projects/demo-dsviz/databases/(default)/documents`, { method: 'DELETE' });
await fetch(`http://${AUTH}/emulator/v1/projects/demo-dsviz/accounts`, { method: 'DELETE' });
const server = spawn(process.execPath, [join(here, '../tools/dev-server.mjs')], { env: Object.assign({}, process.env, { PORT: String(PORT), ADMIN_EMAILS: 'motiml77@gmail.com' }), stdio: ['ignore', 'pipe', 'inherit'] });
await new Promise(res => server.stdout.on('data', d => { if (String(d).includes('dev server')) res(); }));

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
const mid = () => randomBytes(16).toString('hex');

// ------------------------------------------------------------------ נתוני דוגמה
const admin = await idToken('motiml77@gmail.com', 'Moti');
const T = [
  ['sara.cohen@ort.org.il', 'שרה כהן', 'אורט רחובות', 30, 30],
  ['yossi.m@gmail.com', 'יוסי מזרחי', 'מקיף ג׳ באר שבע', 25, 25],
  ['ronit.a@school.org.il', 'רונית אברהם', 'תיכון הדר', 30, 18],
  ['avi.shalom@gmail.com', 'אבי שלום', 'ישיבה תיכונית נחלים', 20, 0],
];
for (const [email, name, school, seats, used] of T) {
  await api('POST', '/v1/admin/teacher', { create: true, email, name, school, seats }, admin);
  const code = (await api('GET', '/v1/admin/teacher?email=' + email, null, admin)).teacher.code;
  for (let i = 1; i <= used; i++) {
    const lab = i <= 15 ? 'מעבדה 2' : 'מעבדה 3';
    await api('POST', '/v1/station/claim', { code, name: `${lab} · עמדה ${((i - 1) % 15) + 1}`, machine: mid() });
  }
}
await api('POST', '/v1/admin/teacher', { email: 'avi.shalom@gmail.com', active: false }, admin);
const ronitStations = await store.query('stations', { where: [['teacher', '==', 'ronit.a@school.org.il']] });
const old = new Date(Date.now() - 40 * 86400000).toISOString();
await store.merge('stations/' + ronitStations[0]._id, { name: 'ספרייה · מחשב 1', lastSeen: old });
await api('POST', '/v1/me/request', { kind: 'release', stationId: ronitStations[0]._id, note: 'המחשב הוחלף בחדש.' }, await idToken('ronit.a@school.org.il', 'רונית אברהם'));
await api('POST', '/v1/me/request', { kind: 'teacher', school: 'תיכון עירוני ד׳, תל אביב', phone: '0501234567', computers: 28 }, await idToken('david.levi@gmail.com', 'דוד לוי'));
await api('POST', '/v1/admin/settings', { price: 60, accessUntil: '2027-08-31', packages: [{ seats: 10, price: 300 }, { seats: 20, price: 550 }, { seats: 30, price: 750 }] }, admin);
for (const [email, name] of [['noa.k@gmail.com', 'נועה כץ'], ['tamar.r@gmail.com', 'תמר רוזן'], ['michael.d@gmail.com', 'מיכאל דהן']]) {
  const tok = await idToken(email, name);
  await api('GET', '/v1/me', null, tok);
  await store.merge('students/' + email, { email, name, lastSignIn: new Date().toISOString() });
  const s = await api('POST', '/v1/pay/start', {}, tok);
  await api('POST', '/v1/pay/notify/demo', { id: s.id, status: 'paid', amount: 60, payer: { name, phone: '05' + String(Math.floor(2e7 + Math.random() * 7e7)), email } });
  if (email.startsWith('michael')) await api('POST', '/v1/pay/notify/demo', { id: s.id, status: 'refunded' });
}
await api('POST', '/v1/admin/student', { emails: ['itay.sh@gmail.com', 'shira.b@gmail.com'], until: '2027-08-31', note: 'הסכם עם תיכון הדר' }, admin);
await store.merge('students/itay.sh@gmail.com', { name: 'איתי שמעוני' });
await store.merge('students/shira.b@gmail.com', { name: 'שירה ברק' });
console.log('נתוני דוגמה מוכנים');

// ------------------------------------------------------------------ דפדפן
// דרך proxy (אם יש): Google Fonts ו-Firebase JS — אבל השרת המקומי והאמולטור (127.0.0.1) בלי proxy
const browser = await chromium.launch(process.env.HTTPS_PROXY ? { args: ['--proxy-server=' + process.env.HTTPS_PROXY, '--proxy-bypass-list=<loopback>;127.0.0.1;localhost'] } : {});
const ctx = await browser.newContext({ viewport: { width: 1440, height: 900 }, deviceScaleFactor: 2, locale: 'he-IL', ignoreHTTPSErrors: true });
const errors = [];
ctx.on('page', p => p.on('pageerror', e => errors.push(String(e))));
let passed = 0;
async function step(name, fn) {
  try { await fn(); passed++; console.log('✓ ' + name); } catch (e) { console.error('✗ ' + name); console.error(e); process.exitCode = 1; }
}

/** החלון של Google — באמולטור: "Add new account" (או חשבון שכבר נוסף), מייל, שם */
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

// הלקוח האמיתי של DSViz (Account.java, אותו קוד של Eclipse / IntelliJ) — במקום fakeApp המדומה
function realApp(role, dir) {
  const proc = spawn(process.env.ACC_JAVA || 'java', ['-Dstdout.encoding=UTF-8', '-cp', process.env.ACC_CLASSES || '/tmp/acc-cls', 'il.lamerhav.dsviz.TeacherSignIn', BASE, dir, role], { stdio: ['ignore', 'pipe', 'inherit'] });
  let buf = '';
  const waiters = [];
  proc.stdout.on('data', d => { buf += d; waiters.splice(0).forEach(w => w()); });
  const find = re => new Promise((ok, bad) => {
    const t = setTimeout(() => bad(new Error('הלקוח לא ענה: ' + buf)), 90000);
    const tick = () => { const m = re.exec(buf); if (m) { clearTimeout(t); ok(m[1]); } else waiters.push(tick); };
    tick();
  });
  return { url: () => find(/^URL (\S+)/m), result: () => find(/^RESULT (\S+)/m), kill: () => proc.kill() };
}
const shot = (page, name) => page.screenshot({ path: join(SHOTS, name + '.png') });

const page = await ctx.newPage();
await step('מסוף ההנהלה: כניסה עם Google → סקירה עם המספרים, "דורש טיפול" ופעילות', async () => {
  await page.goto(BASE + '/admin');
  await page.waitForSelector('.gbtn');
  await shot(page, '00-admin-signin');
  await googlePopup(page, '.gbtn', 'motiml77@gmail.com', 'Moti');
  await page.waitForSelector('.kpis');
  const text = await page.textContent('.kpis');
  assert.match(text, /3/);
  assert.match(text, /73/);
  assert.match(await page.textContent('#content'), /דורש טיפול/);
  assert.equal(await page.locator('.req').count() >= 4, true);
  await shot(page, '01-admin-overview');
});

await step('מורים ועמדות: רשימה, לחיצה על מורה → מכסה, קוד ומחשבים; +10 ושמירה', async () => {
  await page.click('.nav a[href="#teachers"]');
  await page.waitForSelector('table.t tbody tr.click');
  assert.equal(await page.locator('table.t tbody tr.click').count(), 4);
  await page.click('text=רונית אברהם');
  await page.waitForSelector('.drawer');
  await page.waitForSelector('.item.stale');
  await shot(page, '02-admin-teacher');
  await page.click('.drawer button:has-text("+10")');
  assert.match(await page.textContent('.drawer .note'), /30 ← 40/);
  await page.click('.drawer button:has-text("שמירה")');
  await page.waitForSelector('.toast:has-text("40")');
  const t = await api('GET', '/v1/admin/teacher?email=ronit.a@school.org.il', null, admin);
  assert.equal(t.teacher.seats, 40);
});

await step('הוספת מורה מהטופס', async () => {
  await page.click('button:has-text("הוספת מורה")');
  await page.fill('#tf-e', 'new.teacher@gmail.com');
  await page.fill('#tf-n', 'מורה חדש');
  await page.fill('#tf-s', 'תיכון בדיקה');
  await page.fill('#tf-c', '12');
  await shot(page, '03-admin-add-teacher');
  await page.click('dialog button:has-text("הוספה")');
  await page.waitForSelector('.drawer h2:has-text("מורה חדש")');
  assert.equal((await api('GET', '/v1/admin/teacher?email=new.teacher@gmail.com', null, admin)).teacher.seats, 12);
});

await step('בקשות: הרשאת מורה (עם מספר עמדות) ושחרור מחשב', async () => {
  await page.click('.nav a[href="#requests"]');
  await page.waitForSelector('article.req');
  await shot(page, '04-admin-requests');
  const david = page.locator('article.req', { hasText: 'דוד לוי' });
  await david.locator('input').fill('5');
  await david.locator('button:has-text("הגדרה כמורה")').click();
  await page.waitForSelector('.toast:has-text("הוגדר כמורה")');
  const ronit = page.locator('article.req', { hasText: 'רונית' });
  await ronit.locator('button:has-text("שחרור המחשב")').click();
  await page.waitForSelector('.toast:has-text("שוחרר")');
  assert.equal((await api('GET', '/v1/admin/teacher?email=david.levi@gmail.com', null, admin)).teacher.seats, 5);
  assert.equal((await api('GET', '/v1/admin/teacher?email=ronit.a@school.org.il', null, admin)).teacher.used, 17);
});

await step('תלמידים: רשימה, פרטים, הענקה ידנית; תשלומים', async () => {
  await page.click('.nav a[href="#students"]');
  await page.waitForSelector('table.t tbody tr');
  await shot(page, '05-admin-students');
  await page.fill('#g-e', 'gift1@gmail.com\ngift2@gmail.com');
  await page.fill('#g-n', 'מלגה');
  await page.click('aside button:has-text("הענקת גישה")');
  await page.waitForSelector('.toast:has-text("2 תלמידים")');
  await page.click('tr:has-text("noa.k@gmail.com") button:has-text("פרטים")');
  await page.waitForSelector('dialog h2:has-text("נועה כץ")');
  await shot(page, '06-admin-student');
  await page.keyboard.press('Escape');
  await page.click('button[role=tab]:has-text("תשלומים")');
  await page.waitForSelector('th:has-text("אסמכתה")');
  assert.match(await page.textContent('#content'), /הוחזר/);
  assert.match(await page.textContent('#content'), /05\d{8}/, 'טלפון המשלם מוצג');
  await shot(page, '07-admin-payments');
});

await step('הגדרות: המתג של Gemini נשמר; יומן הפעולות', async () => {
  await page.click('.nav a[href="#settings"]');
  await page.waitForSelector('#s-gem', { state: 'attached' });
  await shot(page, '08-admin-settings');
  await page.click('label.switch');
  await page.fill('#s-monthlyStudent', '25');
  await page.click('button:has-text("שמירת השינויים")');
  await page.click('dialog button:has-text("הדלקה")');
  await page.waitForSelector('.toast:has-text("נשמר")');
  const s = await api('GET', '/v1/admin/settings', null, admin);
  assert.equal(s.settings.requireLoginForGemini, true);
  assert.equal(s.settings.monthlyStudent, 25);
  await api('POST', '/v1/admin/settings', { requireLoginForGemini: false }, admin);
  await page.click('.nav a[href="#log"]');
  await page.waitForSelector('.feed li');
  assert.match(await page.textContent('#content'), /מכסה 30 ← 40/);
  await shot(page, '09-admin-log');
});

await step('חיפוש: קוד מורה / שם מחשב / תלמיד', async () => {
  await page.click('.search input');
  await page.fill('.search input', 'נועה');
  await page.waitForSelector('.results button:has-text("נועה כץ")');
  await page.fill('.search input', 'ספרייה');
  await page.waitForSelector('.results', { state: 'visible' });
  await page.keyboard.press('Escape');
});

/** "התוסף": שרת קטן ב-127.0.0.1 שמקבל את הקוד, מממש אותו עם ה-verifier ומפנה לדף /welcome לפי המצב */
function fakeApp(role) {
  const verifier = randomBytes(32).toString('base64url');
  const challenge = createHash('sha256').update(verifier).digest('base64url');
  const state = randomBytes(16).toString('base64url');
  const machine = mid();
  let got = null;
  const srv = http.createServer(async (req, res) => {
    const u = new URL(req.url, 'http://127.0.0.1');
    if (u.pathname !== '/done' || u.searchParams.get('state') !== state) { res.writeHead(400); return res.end(); }
    got = await api('POST', '/v1/auth/redeem', { code: u.searchParams.get('code'), verifier, machine, machineName: 'המחשב של הבדיקה', client: 'e2e' });
    res.writeHead(302, { location: `${BASE}/welcome?r=${got.status}&role=${role}` });
    res.end();
  });
  return new Promise(res => srv.listen(0, '127.0.0.1', () => res({ port: srv.address().port, state, challenge, machine, result: () => got, close: () => srv.close() })));
}

const student = await ctx.newPage();
await step('תלמיד מתוך התוכנה: /signin → Google → חזרה לתוכנה → "צריך לשלם" → תשלום → המאגר פתוח', async () => {
  const appSrv = await fakeApp('student');
  await student.goto(`${BASE}/signin?role=student&port=${appSrv.port}&state=${appSrv.state}&challenge=${appSrv.challenge}`);
  await student.waitForSelector('.gbtn');
  await shot(student, '10-signin-student');
  await googlePopup(student, '.gbtn', 'pupil.new@gmail.com', 'תלמיד חדש');
  await student.waitForURL(/\/welcome\?r=pay/);
  await student.waitForSelector('.price');
  assert.equal(appSrv.result().status, 'pay');
  assert.match(appSrv.result().token, /^u\./);
  await shot(student, '11-student-pay');
  await student.click('button:has-text("מעבר לתשלום מאובטח")');
  await student.waitForURL(/\/pay\/demo/);
  await student.click('#pay');
  await student.waitForSelector('h1:has-text("המאגר פתוח")', { timeout: 20000 });
  await shot(student, '12-student-open');
  const check = await api('POST', '/v1/access/check', { token: appSrv.result().token, machine: appSrv.machine });
  assert.equal(check.status, 'ok');
  appSrv.close();
});

const teacher = await browser.newContext({ viewport: { width: 1440, height: 900 }, deviceScaleFactor: 2, locale: 'he-IL', ignoreHTTPSErrors: true }).then(c => c.newPage());
await step('מורה מתוך התוכנה: כניסה → הצלחה; הלוח של המורה בדפדפן (קריאה בלבד + בקשה מההנהלה)', async () => {
  const appSrv = await fakeApp('teacher');
  await teacher.goto(`${BASE}/signin?role=teacher&port=${appSrv.port}&state=${appSrv.state}&challenge=${appSrv.challenge}`);
  await shot(teacher, '13-signin-teacher');
  await googlePopup(teacher, '.gbtn', 'sara.cohen@ort.org.il', 'שרה כהן');
  await teacher.waitForURL(/\/welcome\?r=ok&role=teacher/);
  assert.equal(appSrv.result().status, 'ok');
  assert.equal(appSrv.result().teacher.seats, 30);
  await teacher.click('a:has-text("ללוח המורה")');
  await teacher.waitForSelector('.codebox');
  assert.equal(await teacher.locator('button:has-text("שחרור")').count(), 0, 'למורה אין כפתור שחרור');
  await shot(teacher, '14-teacher-panel');
  await shot(teacher, '15-teacher-packages');
  await teacher.click('.pack:has-text("20") button');
  await teacher.waitForURL(/\/pay\/demo/);
  await teacher.fill('#ph', '052-7654321');
  await teacher.click('#pay');
  await teacher.waitForSelector('.toast:has-text("נוספו 20 עמדות")', { timeout: 30000 });
  await teacher.waitForSelector('text=מתוך 50 בשימוש');
  const pay = (await api('GET', '/v1/admin/payments', null, admin)).payments.find(p => p.kind === 'seats');
  assert.deepEqual([pay.seats, pay.amount, pay.payerPhone], [20, 550, '052-7654321']);
  appSrv.close();
});

await step('חשבון שעוד לא מורה: כניסה → בקשת הרשאה מהדפדפן', async () => {
  const p = await browser.newContext({ viewport: { width: 1440, height: 900 }, deviceScaleFactor: 2, locale: 'he-IL', ignoreHTTPSErrors: true }).then(c => c.newPage());
  const appSrv = await fakeApp('teacher');
  await p.goto(`${BASE}/signin?role=teacher&port=${appSrv.port}&state=${appSrv.state}&challenge=${appSrv.challenge}`);
  await googlePopup(p, '.gbtn', 'someone.new@gmail.com', 'מורה מבקש');
  await p.waitForURL(/r=not-teacher/);
  await p.fill('#school', 'תיכון חדש, חיפה');
  await p.fill('#phone', '12345');   // לא תקין — לא נשלח
  await p.click('button:has-text("שליחת בקשה להנהלה")');
  await p.waitForSelector('.toast:has-text("10 ספרות")');
  assert.ok(!(await api('GET', '/v1/admin/requests', null, admin)).requests.some(r => r.email === 'someone.new@gmail.com'));
  await p.fill('#phone', '050-765 4321');
  await shot(p, '16-not-teacher');
  await p.click('button:has-text("שליחת בקשה להנהלה")');
  await p.waitForSelector('h1:has-text("הבקשה נשלחה")');
  const reqs = await api('GET', '/v1/admin/requests', null, admin);
  assert.ok(reqs.requests.some(r => r.email === 'someone.new@gmail.com' && r.kind === 'teacher' && r.phone === '0507654321'));
  appSrv.close();
});

await step('הרשמת מורה דרך הלקוח האמיתי של DSViz: כניסה → בקשה עם טלפון → אישור → כניסה חוזרת', async () => {
  const dir = mkdtempSync(join(tmpdir(), 'realapp-'));
  const p = await browser.newContext({ viewport: { width: 1440, height: 900 }, deviceScaleFactor: 2, locale: 'he-IL', ignoreHTTPSErrors: true }).then(c => c.newPage());
  const first = realApp('teacher', dir);
  await p.goto(await first.url());
  await googlePopup(p, '.gbtn', 'real.app.teacher@gmail.com', 'מורה מהתוכנה');
  await p.waitForURL(/r=not-teacher/);
  assert.equal(await first.result(), 'not-teacher', 'הלקוח קיבל: החשבון עוד לא מורה');
  await p.fill('#school', 'ישיבת בדיקה');
  await p.fill('#phone', '050-111');   // לא תקין
  await p.click('button:has-text("שליחת בקשה להנהלה")');
  await p.waitForSelector('.toast:has-text("10 ספרות")');
  await p.fill('#phone', '054-3219876');
  await p.click('button:has-text("שליחת בקשה להנהלה")');
  await p.waitForSelector('h1:has-text("הבקשה נשלחה")');
  const req = (await api('GET', '/v1/admin/requests', null, admin)).requests.find(r => r.email === 'real.app.teacher@gmail.com');
  assert.equal(req.phone, '0543219876');
  await api('POST', '/v1/admin/request', { id: req.id, decision: 'approve', seats: 3 }, admin);
  const second = realApp('teacher', dir);
  await p.goto(await second.url());
  await googlePopup(p, '.gbtn', 'real.app.teacher@gmail.com', 'מורה מהתוכנה');
  assert.equal(await second.result(), 'ok', 'אחרי האישור הלקוח נכנס כמורה');
  const row = (await api('GET', '/v1/admin/teachers', null, admin)).teachers.find(t => t.email === 'real.app.teacher@gmail.com');
  assert.deepEqual([row.phone, row.seats, row.sends.limit], ['0543219876', 3, 12], 'הטלפון בהנהלה, 3 עמדות, מנה 4×3');
});

await step('טלפון: מסוף ההנהלה ברוחב 390 — בלי גלילה לצד', async () => {
  const m = await browser.newContext({ viewport: { width: 390, height: 844 }, deviceScaleFactor: 2, locale: 'he-IL', ignoreHTTPSErrors: true, isMobile: true, hasTouch: true });
  const mp = await m.newPage();
  await m.addCookies([]);
  await mp.goto(BASE + '/admin');
  await googlePopup(mp, '.gbtn', 'motiml77@gmail.com', 'Moti');
  await mp.waitForSelector('.kpis');
  const wide = await mp.evaluate(() => document.documentElement.scrollWidth - window.innerWidth);
  assert.ok(wide <= 1, 'גלילה לצד: ' + wide);
  await shot(mp, '17-admin-phone');
});

assert.deepEqual(errors, [], 'שגיאות JavaScript בדפים');
console.log(`\n${passed} בדיקות דפדפן עברו · צילומים: ${SHOTS}`);
await browser.close();
server.kill();
