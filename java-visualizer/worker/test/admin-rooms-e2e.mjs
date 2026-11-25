// בדיקה בדפדפן אמיתי (Playwright + Chromium) של מסוף ההנהלה (/admin): "עמדות ותלמידי מורים" בשני טאבים נפרדים
// (עמדות מעבדה / תלמידים), טאבים באותו מבנה בכרטיס המורה, תלמידים משויכים לא ברשימת "תלמידים ותשלומים", והחיפוש
// שמוצא כיתות, מורים, מחשבים ותלמידים. צילומי מסך: SHOTS (ברירת מחדל test/shots).
//   npm run test:admin-rooms
import assert from 'node:assert/strict';
import { spawn } from 'node:child_process';
import { createHash, randomBytes } from 'node:crypto';
import { createRequire } from 'node:module';
import { mkdirSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const require = createRequire(import.meta.url);
const { chromium } = require('playwright');
const PORT = 8789, BASE = `http://127.0.0.1:${PORT}`;
const AUTH = process.env.FIREBASE_AUTH_EMULATOR_HOST || '127.0.0.1:9099';
const FS = process.env.FIRESTORE_EMULATOR_HOST || '127.0.0.1:8181';
const SHOTS = process.env.SHOTS || join(here, 'shots');
mkdirSync(SHOTS, { recursive: true });

await fetch(`http://${FS}/emulator/v1/projects/demo-dsviz/databases/(default)/documents`, { method: 'DELETE' });
await fetch(`http://${AUTH}/emulator/v1/projects/demo-dsviz/accounts`, { method: 'DELETE' });
const server = spawn(process.execPath, [join(here, '../tools/dev-server.mjs')], { env: Object.assign({}, process.env, { PORT: String(PORT), ADMIN_EMAILS: 'admin@test.dev' }), stdio: ['ignore', 'pipe', 'inherit'] });
await new Promise(res => server.stdout.on('data', d => { if (String(d).includes('dev server')) res(); }));

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

// ------------------------------------------------------------------ נתוני דוגמה
const admin = await idToken('admin@test.dev', 'מנהל');
const TEACHERS = [['sara.cohen@ort.org.il', 'שרה כהן', 'אורט רחובות'], ['yossi.m@gmail.com', 'יוסי מזרחי', 'מקיף ג׳ באר שבע'], ['empty.t@gmail.com', 'מורה בלי תלמידים', 'תיכון ריק']];
const codes = {};
for (const [email, name, school] of TEACHERS) {
  await api('POST', '/v1/admin/teacher', { create: true, email, name, school, seats: 20 }, bearer(admin));
  codes[email] = (await api('GET', '/v1/admin/teacher?email=' + email, null, bearer(admin))).teacher.code;
}
for (const [email, names] of [['sara.cohen@ort.org.il', ['מעבדה 2 · עמדה 1', 'מעבדה 2 · עמדה 2']], ['yossi.m@gmail.com', ['ספרייה · מחשב 7']]]) {
  for (const name of names) await api('POST', '/v1/station/claim', { code: codes[email], name, machine: mid(), client: 'test', caps: ['classroom'] });
}
const sara = bearer(await idToken('sara.cohen@ort.org.il', 'שרה כהן')), yossi = bearer(await idToken('yossi.m@gmail.com', 'יוסי מזרחי'));
const cYa3 = (await api('POST', '/v1/me/class', { op: 'create', name: 'יא 3' }, sara)).id;
const cYb1 = (await api('POST', '/v1/me/class', { op: 'create', name: 'יב 1' }, sara)).id;
const cT2 = (await api('POST', '/v1/me/class', { op: 'create', name: 'ט 2' }, yossi)).id;
await api('POST', '/v1/me/class', { op: 'create', name: 'כיתה ריקה' }, sara);
const PUPILS = [
  ['noam.levi@gmail.com', 'נועם לוי', 'sara.cohen@ort.org.il', cYa3], ['ariel.cohen@gmail.com', 'אריאל כהן', 'sara.cohen@ort.org.il', cYa3],
  ['ido.s@gmail.com', 'עידו שפירא', 'sara.cohen@ort.org.il', cYb1], ['dana.k@gmail.com', 'דנה כהן', 'sara.cohen@ort.org.il', ''],
  ['omer.b@gmail.com', 'עומר ברק', 'yossi.m@gmail.com', cT2],
];
const plug = {};
for (const [email, name, teacher, cls] of PUPILS) {
  plug[email] = await studentPlugin(email, name);
  await api('POST', '/v1/student/register', { fullName: name, phone: '05' + String(Math.floor(2e7 + Math.random() * 7e7)) }, plug[email]);
  await api('POST', '/v1/student/join', { code: codes[teacher] }, plug[email]);
  if (cls) await api('POST', '/v1/me/member', { op: 'move', student: email, classId: cls }, teacher === 'sara.cohen@ort.org.il' ? sara : yossi);
}
await api('POST', '/v1/submit', { exercise: { source: 'bank', bankId: 'queue-sum-even', topic: 'תור', title: 'סכום תור וזוגיים', level: 'קל' }, app: 'Eclipse', files: [{ path: 'QueueTargil1.java', text: 'class A {}\n' }], check: { ran: true, compiles: true, output: 'x', expected: 'x', outputMatch: true } }, plug['noam.levi@gmail.com']);
// תלמיד עצמאי (לא משויך): נשאר ב"תלמידים ותשלומים"
const tamar = await studentPlugin('tamar.bd@gmail.com', 'תמר בן-דוד');
await api('POST', '/v1/student/mode', { mode: 'independent' }, tamar);
console.log('נתוני דוגמה מוכנים');

// ------------------------------------------------------------------ דפדפן
const browser = await chromium.launch();
const ctx = await browser.newContext({ viewport: { width: 1440, height: 1000 }, deviceScaleFactor: 2, locale: 'he-IL' });
const errors = [];
ctx.on('page', p => p.on('pageerror', e => errors.push(String(e))));
let passed = 0;
async function step(name, fn) {
  try { await fn(); passed++; console.log('✓ ' + name); } catch (e) { console.error('✗ ' + name); console.error(e); process.exitCode = 1; }
}
const shot = (page, name) => page.screenshot({ path: join(SHOTS, name + '.png'), fullPage: true });
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
const tab = (page, id) => page.locator('#rt-' + id);
const selected = async (page, id) => (await tab(page, id).getAttribute('aria-selected')) === 'true';

const page = await ctx.newPage();
await step('מסוף ההנהלה: יש ערך בתפריט "עמדות ותלמידי מורים", והוא נפתח על טאב עמדות המעבדה — 3 עמדות, מקובצות לפי מורה', async () => {
  await page.goto(BASE + '/admin');
  await googlePopup(page, '.gbtn', 'admin@test.dev', 'מנהל');
  await page.waitForSelector('.kpis');
  await page.click('.nav a[href="#rooms"]');
  await page.waitForSelector('#rt-stations');
  assert.ok(await selected(page, 'stations'));
  assert.ok(!(await selected(page, 'students')));
  assert.match(await tab(page, 'stations').textContent(), /עמדות מעבדה · 3/);
  assert.match(await tab(page, 'students').textContent(), /תלמידים · 5/);
  assert.equal(await page.locator('#content section.card').count(), 2, 'שני מורים עם עמדות; מורה בלי עמדות ובלי תלמידים לא מוצג');
  assert.equal(await page.locator('#content .item[data-station]').count(), 3);
  assert.equal(await page.locator('#content tr[data-student]').count(), 0, 'בטאב העמדות אין תלמידים');
  await shot(page, 'r01-admin-rooms-stations');
});

await step('הסקירה: כרטיס "תלמידים" סופר רק עצמאיים, ומציין כמה משויכים למורים', async () => {
  await page.click('.nav a[href="#overview"]');
  await page.waitForSelector('.kpis');
  const card = page.locator('.kpis a', { hasText: 'תלמידים עצמאיים' });
  assert.equal((await card.locator('.v').textContent()).trim(), '1');
  assert.match(await card.textContent(), /ועוד 5 משויכים למורים/);
});

await step('טאב "תלמידים": כל התלמידים המשויכים, לפי מורה, עם הכיתה, ההגשות וההגשה האחרונה — ובלי העמדות', async () => {
  await page.click('.nav a[href="#rooms"]');
  await page.waitForSelector('#rt-students');
  await tab(page, 'students').click();
  await page.waitForSelector('tr[data-student]');
  assert.ok(await selected(page, 'students'));
  assert.equal(await page.locator('tr[data-student]').count(), 5);
  assert.equal(await page.locator('#content .item[data-station]').count(), 0, 'בטאב התלמידים אין עמדות');
  const noam = page.locator('tr[data-student="noam.levi@gmail.com"]');
  const t = await noam.textContent();
  assert.match(t, /נועם לוי/);
  assert.match(t, /יא 3/);
  assert.match(t, /סכום תור וזוגיים/);
  assert.match(await page.locator('tr[data-student="dana.k@gmail.com"]').textContent(), /בלי כיתה/);
  assert.match(await page.locator('tr[data-student="omer.b@gmail.com"]').textContent(), /ט 2/);
  assert.ok(!(await page.textContent('#content')).includes('tamar.bd@gmail.com'), 'עצמאי — לא כאן');
  await shot(page, 'r02-admin-rooms-students');
});

await step('סינון: לפי מורה (רק שלו), ואז לפי כיתה, או "בלי כיתה"; המונים בטאבים לפי המורה', async () => {
  await page.selectOption('select[aria-label="מורה"]', 'sara.cohen@ort.org.il');
  await page.waitForFunction(() => document.querySelectorAll('tr[data-student]').length === 4);
  assert.match(await tab(page, 'stations').textContent(), /עמדות מעבדה · 2/);
  assert.match(await tab(page, 'students').textContent(), /תלמידים · 4/);
  await page.selectOption('select[aria-label="כיתה"]', { label: 'יא 3 · 2' });
  await page.waitForFunction(() => document.querySelectorAll('tr[data-student]').length === 2);
  assert.deepEqual((await page.locator('tr[data-student]').evaluateAll(rows => rows.map(r => r.dataset.student))).sort(), ['ariel.cohen@gmail.com', 'noam.levi@gmail.com']);
  await page.selectOption('select[aria-label="כיתה"]', '-');
  await page.waitForFunction(() => document.querySelectorAll('tr[data-student]').length === 1);
  assert.equal(await page.locator('tr[data-student]').getAttribute('data-student'), 'dana.k@gmail.com');
  await page.selectOption('select[aria-label="כיתה"]', { label: 'כיתה ריקה · 0' });
  await page.waitForSelector('.empty:has-text("אין תלמידים בכיתה הזו")');
  await shot(page, 'r03-admin-rooms-filter');
});

await step('"תלמידים ותשלומים": תלמידים שמשויכים למורים לא מוצגים (רק עצמאיים), עם הסבר וקישור לעמוד החדש', async () => {
  await page.click('.nav a[href="#students"]');
  await page.waitForSelector('table.t tbody tr');
  const text = await page.textContent('#content');
  assert.ok(text.includes('tamar.bd@gmail.com'), 'התלמיד העצמאי כאן');
  for (const [email] of PUPILS) assert.ok(!text.includes(email), email + ' משויך למורה — לא בקטגוריה "תלמידים"');
  assert.match(text, /5 תלמידים משויכים למורים ולכן לא מופיעים כאן/);
  assert.match(await page.locator('.filters').textContent(), /כולם · 1/);
  assert.ok(!(await page.locator('.filters').textContent()).includes('עם מורה'), 'הקבוצה "עם מורה" הוסרה');
  await shot(page, 'r04-admin-students-independent-only');
  await page.click('#content a:has-text("עמדות ותלמידי מורים")');
  await page.waitForSelector('#rt-students[aria-selected="true"]');
  assert.equal(await page.locator('tr[data-student]').count(), 5);
});

await step('כרטיס המורה: שני טאבים — עמדות מעבדה ותלמידים (עם הכיתה, סינון לפי כיתה) — ולחיצה על "פרטים" פותחת את התלמיד', async () => {
  await page.click('.nav a[href="#teachers"]');
  await page.waitForSelector('table.t tbody tr.click');
  await page.click('text=שרה כהן');
  await page.waitForSelector('.drawer #rt-stations');
  assert.ok(await selected(page, 'stations'));
  assert.match(await page.locator('.drawer #rt-stations').textContent(), /עמדות מעבדה · 2/);
  assert.match(await page.locator('.drawer #rt-students').textContent(), /תלמידים · 4/);
  assert.equal(await page.locator('.drawer .item[data-seen]').count(), 2);
  assert.equal(await page.locator('.drawer .item[data-student]').count(), 0, 'בטאב העמדות אין תלמידים');
  await shot(page, 'r05-admin-teacher-stations');
  await page.click('.drawer #rt-students');
  await page.waitForSelector('.drawer .item[data-student]');
  assert.equal(await page.locator('.drawer .item[data-student]').count(), 4);
  assert.equal(await page.locator('.drawer .item[data-seen]').count(), 0, 'בטאב התלמידים אין עמדות');
  assert.match(await page.locator('.drawer .item[data-student="noam.levi@gmail.com"]').textContent(), /יא 3 · 1 הגשות/);
  await page.selectOption('.drawer select[aria-label="כיתה"]', { label: 'יב 1 · 1' });
  await page.waitForFunction(() => document.querySelectorAll('.drawer .item[data-student]').length === 1);
  await shot(page, 'r06-admin-teacher-students');
  await page.selectOption('.drawer select[aria-label="כיתה"]', '');
  await page.locator('.drawer .item[data-student="ido.s@gmail.com"] button:has-text("פרטים")').click();
  await page.waitForSelector('dialog.modal[open]');
  assert.match(await page.textContent('dialog.modal'), /עידו שפירא/);
  await page.keyboard.press('Escape');
});

await step('החיפוש במסוף: כיתות, מורים, מחשבים ותלמידים (משויכים — בעמוד המורה עם הכיתה; עצמאי — ברשימת התלמידים)', async () => {
  await page.goto(BASE + '/admin#overview');
  await page.waitForSelector('.kpis');
  const box = page.locator('.search input');
  const hits = async q => { await box.fill(''); await box.fill(q); await page.waitForSelector('.search .results:not([hidden])'); return page.locator('.search .results button, .search .results .empty').allTextContents(); };
  // כיתה (של כל מורה)
  let r = await hits('יב 1');
  assert.ok(r.some(x => /כיתה/.test(x) && /יב 1/.test(x) && /שרה כהן/.test(x) && /1 תלמידים/.test(x)), JSON.stringify(r));
  r = await hits('ט 2');
  assert.ok(r.some(x => /כיתה/.test(x) && /ט 2/.test(x) && /יוסי מזרחי/.test(x)), 'כיתה של מורה אחר: ' + JSON.stringify(r));
  r = await hits('כיתה ריקה');
  assert.ok(r.some(x => /כיתה/.test(x) && /כיתה ריקה/.test(x)), 'גם כיתה בלי תלמידים: ' + JSON.stringify(r));
  // מורה, מחשב (עמדה)
  r = await hits('בלי תלמידים');
  assert.ok(r.some(x => /מורה/.test(x) && /מורה בלי תלמידים/.test(x)), JSON.stringify(r));
  r = await hits('ספרייה');
  assert.ok(r.some(x => /מחשב/.test(x) && /ספרייה/.test(x) && /יוסי מזרחי/.test(x)), JSON.stringify(r));
  // "כהן": המורה שרה כהן + שני תלמידים משויכים (אריאל, דנה) — ולא תלמיד אחר
  r = await hits('כהן');
  assert.ok(r.some(x => /מורה/.test(x) && /שרה כהן/.test(x)), JSON.stringify(r));
  assert.ok(r.some(x => /תלמיד/.test(x) && /אריאל כהן/.test(x) && /שרה כהן/.test(x) && /יא 3/.test(x)), 'תלמיד משויך מוצג עם המורה והכיתה: ' + JSON.stringify(r));
  assert.ok(r.some(x => /תלמיד/.test(x) && /דנה כהן/.test(x)));
  assert.ok(!r.some(x => /נועם לוי/.test(x)));
  await shot(page, 'r07-admin-search');
  // לחיצה על תלמיד משויך → טאב התלמידים של המורה, הכיתה שלו, והשורה מסומנת
  await page.locator('.search .results button', { hasText: 'אריאל כהן' }).click();
  await page.waitForSelector('#rt-students[aria-selected="true"]');
  await page.waitForSelector('tr.sel[data-student="ariel.cohen@gmail.com"]');
  assert.equal(await page.locator('tr[data-student]').count(), 2, 'מסונן לכיתה שלו (יא 3): אריאל ונועם');
  assert.match(await page.locator('select[aria-label="מורה"]').inputValue(), /sara\.cohen/);
  assert.match(await page.locator('select[aria-label="כיתה"] option:checked').textContent(), /יא 3/);
  // לחיצה על כיתה → התלמידים של הכיתה
  await box.fill('יב 1'); await page.waitForSelector('.search .results:not([hidden])');
  await page.locator('.search .results button', { hasText: 'כיתה' }).first().click();
  await page.waitForFunction(() => document.querySelectorAll('tr[data-student]').length === 1);
  assert.equal(await page.locator('tr[data-student]').getAttribute('data-student'), 'ido.s@gmail.com');
  // מחשב → טאב העמדות של המורה
  await box.fill('ספרייה'); await page.waitForSelector('.search .results:not([hidden])');
  await page.locator('.search .results button', { hasText: 'ספרייה' }).first().click();
  await page.waitForSelector('#rt-stations[aria-selected="true"]');
  assert.equal(await page.locator('#content .item[data-station]').count(), 1);
  // תלמיד עצמאי → רשימת "תלמידים ותשלומים" (נפתח הכרטיס שלו)
  await box.fill('tamar.bd'); await page.waitForSelector('.search .results:not([hidden])');
  await page.locator('.search .results button', { hasText: 'תמר בן-דוד' }).click();
  await page.waitForSelector('dialog.modal[open]');
  assert.match(await page.textContent('dialog.modal'), /תמר בן-דוד/);
  await page.keyboard.press('Escape');
  assert.equal(await page.evaluate(() => location.hash.startsWith('#students')), true);
});

await step('"אישור" בלחיצה אחת: תלמיד בלי גישה מקבל גישה עד התאריך שבהגדרות (הענקה ידנית), והכפתור נעלם', async () => {
  await page.goto(BASE + '/admin#students');
  const row = page.locator('tr[data-student="tamar.bd@gmail.com"]');
  await row.waitFor();
  assert.equal(await row.locator('button[data-act="approve"]').count(), 1, 'יש כפתור אישור');
  assert.match(await page.locator('.filters').textContent(), /מחכים לאישור · 1/);
  await row.locator('button[data-act="approve"]').click();
  await page.waitForSelector('.toast:has-text("הגישה אושרה")');
  await page.waitForFunction(() => { const r = document.querySelector('tr[data-student="tamar.bd@gmail.com"]'); return r && !r.querySelector('button[data-act="approve"]') && /הענקה ידנית/.test(r.textContent); });
  const st = (await api('GET', '/v1/admin/students', null, bearer(admin))).students.find(x => x.email === 'tamar.bd@gmail.com');
  assert.ok(st.paid && st.source === 'manual' && st.until, JSON.stringify(st));
  assert.match(await page.locator('.filters').textContent(), /מחכים לאישור · 0/);
});

await step('שיוך תלמיד עצמאי למורה מהשורה שלו (עם כיתה): עובר לעמוד של המורה, והמורה רואה אותו בעמדת הניהול', async () => {
  const row = page.locator('tr[data-student="tamar.bd@gmail.com"]');
  await row.locator('button[data-act="assign"]').click();
  await page.waitForSelector('dialog.modal[open] #as-teacher');
  assert.equal(await page.locator('#as-ok').isDisabled(), true, 'בלי מורה — אי אפשר לשייך');
  await page.selectOption('#as-teacher', 'yossi.m@gmail.com');
  await page.waitForFunction(() => document.querySelectorAll('#as-class option').length === 2);
  await page.selectOption('#as-class', { label: 'ט 2 · 1' });
  await shot(page, 'r08-admin-assign-dialog');
  await page.click('#as-ok');
  await page.waitForSelector('.toast:has-text("שויך ליוסי מזרחי")');
  await page.waitForFunction(() => !document.querySelector('tr[data-student="tamar.bd@gmail.com"]'));
  assert.match(await page.textContent('#content'), /6 תלמידים משויכים למורים/);
  await page.goto(BASE + '/admin#rooms=' + encodeURIComponent('students|yossi.m@gmail.com'));
  await page.waitForSelector('tr[data-student="tamar.bd@gmail.com"]');
  assert.match(await page.locator('tr[data-student="tamar.bd@gmail.com"]').textContent(), /ט 2/);
  const con = await api('GET', '/v1/me/console', null, yossi);
  const mine = con.students.find(x => x.email === 'tamar.bd@gmail.com');
  assert.ok(mine && !mine.left && mine.classId === cT2, 'המורה רואה את התלמיד, בכיתה שנבחרה');
  assert.equal((await api('GET', '/v1/student/state', null, tamar)).teacher.email, 'yossi.m@gmail.com', 'והתלמיד רואה את המורה');
});

await step('כמה תלמידים יחד: מסמנים, "שיוך למורה (2)" — עוברים למורה אחר; ו"בלי מורה" מחזיר תלמיד לעצמאי', async () => {
  await page.goto(BASE + '/admin#rooms=' + encodeURIComponent('students|sara.cohen@ort.org.il'));
  await page.waitForSelector('tr[data-student="dana.k@gmail.com"]');
  assert.equal(await page.locator('#pickbar.on').count(), 0);
  await page.check('tr[data-student="dana.k@gmail.com"] input.pick');
  await page.check('tr[data-student="ido.s@gmail.com"] input.pick');
  await page.waitForSelector('#pickbar.on');
  assert.match(await page.locator('#pick-assign').textContent(), /שיוך למורה \(2\)/);
  assert.equal(await page.locator('#pick-approve').count(), 0, 'אישור גישה — רק בעמוד התלמידים העצמאיים');
  await shot(page, 'r09-admin-rooms-picked');
  await page.click('#pick-assign');
  await page.waitForSelector('dialog.modal[open] #as-teacher');
  assert.equal(await page.inputValue('#as-teacher'), 'sara.cohen@ort.org.il', 'המורה הנוכחי מסומן');
  await page.selectOption('#as-teacher', 'yossi.m@gmail.com');
  await page.click('#as-ok');
  await page.waitForSelector('.toast:has-text("2 תלמידים שויכו ליוסי מזרחי")');
  await page.waitForFunction(() => document.querySelectorAll('tr[data-student]').length === 2);
  const rooms = await api('GET', '/v1/admin/rooms', null, bearer(admin));
  assert.deepEqual(rooms.students.filter(x => x.teacher === 'yossi.m@gmail.com').map(x => x.email).sort(), ['dana.k@gmail.com', 'ido.s@gmail.com', 'omer.b@gmail.com', 'tamar.bd@gmail.com']);
  const saraCon = await api('GET', '/v1/me/console', null, sara);
  assert.ok(saraCon.students.find(x => x.email === 'dana.k@gmail.com').left, 'אצל המורה הקודם — "עזב"');
  // "בלי מורה": חזרה לעצמאי
  await page.goto(BASE + '/admin#rooms=' + encodeURIComponent('students|yossi.m@gmail.com'));
  await page.locator('tr[data-student="tamar.bd@gmail.com"] button[data-act="assign"]').click();
  await page.waitForSelector('dialog.modal[open] #as-teacher');
  await page.selectOption('#as-teacher', '-');
  await page.click('#as-ok');
  await page.waitForSelector('.toast:has-text("השיוך בוטל")');
  await page.goto(BASE + '/admin#students');
  await page.waitForSelector('tr[data-student="tamar.bd@gmail.com"]');
  assert.equal((await api('GET', '/v1/student/state', null, tamar)).mode, 'independent');
});

await step('טלפון (390): תלמידים, תלמידי המורים ומורים — כרטיסים במקום טבלה רחבה, בלי גלילה לצד; אישור ושיוך עובדים גם שם', async () => {
  for (const [email, name] of [['ayala.a@gmail.com', 'איילה אדלר'], ['sarah.s@gmail.com', 'שרה שמעוני'], ['avishag.l@gmail.com', 'Avishag']]) {
    const pl = await studentPlugin(email, name);
    await api('POST', '/v1/student/mode', { mode: 'independent' }, pl);
  }
  const m = await browser.newContext({ viewport: { width: 390, height: 844 }, deviceScaleFactor: 2, locale: 'he-IL', isMobile: true, hasTouch: true });
  const mp = await m.newPage();
  mp.on('pageerror', e => errors.push(String(e)));
  await mp.goto(BASE + '/admin#students');
  await googlePopup(mp, '.gbtn', 'admin@test.dev', 'מנהל');
  await mp.waitForSelector('tr[data-student="ayala.a@gmail.com"]');
  const wide = () => mp.evaluate(() => document.documentElement.scrollWidth - window.innerWidth);
  assert.ok(await wide() <= 1, 'תלמידים — גלילה לצד: ' + await wide());
  const card = await mp.evaluate(() => {
    const tr = document.querySelector('tr[data-student="ayala.a@gmail.com"]'), r = tr.getBoundingClientRect();
    const lbl = getComputedStyle(tr.querySelector('td[data-label="גישה עד"]'), '::before').content;
    const btns = [...tr.querySelectorAll('td.actions button')].map(b => b.getBoundingClientRect());
    return { display: getComputedStyle(tr).display, head: getComputedStyle(document.querySelector('table.t.cards thead')).display, left: r.left, right: r.right, lbl,
      btnsIn: btns.every(b => b.left >= 0 && b.right <= innerWidth), btnH: Math.min(...btns.map(b => b.height)), n: btns.length };
  });
  assert.equal(card.display, 'flex', 'כל תלמיד — כרטיס');
  assert.equal(card.head, 'none', 'בלי שורת כותרות של טבלה');
  assert.ok(card.left >= 0 && card.right <= 390, 'הכרטיס בתוך המסך');
  assert.match(card.lbl, /גישה עד/, 'לכל ערך התווית שלו');
  assert.ok(card.btnsIn && card.n === 3 && card.btnH >= 38, 'אישור / שיוך למורה / פרטים — בתוך המסך, בגודל שנוח ללחוץ: ' + JSON.stringify(card));
  await shot(mp, 'm01-phone-students');
  // אישור של כמה יחד מהטלפון
  await mp.click('#pick-all');
  await mp.waitForSelector('#pickbar.on');
  assert.match(await mp.locator('#pick-approve').textContent(), /אישור גישה \(4\)/, '"סימון הכול" מסמן את כל מי שבתצוגה (3 החדשים + מי שכבר אושר)');
  await shot(mp, 'm02-phone-students-picked');
  await mp.click('#pick-approve');
  await mp.waitForSelector('.toast:has-text("הגישה אושרה ל-")');
  await mp.waitForFunction(() => !document.querySelector('button[data-act="approve"]'));
  // שיוך מהטלפון
  await mp.locator('tr[data-student="ayala.a@gmail.com"] button[data-act="assign"]').click();
  await mp.waitForSelector('dialog.modal[open] #as-teacher');
  await mp.selectOption('#as-teacher', 'sara.cohen@ort.org.il');
  const dlg = await mp.evaluate(() => { const r = document.querySelector('dialog.modal[open]').getBoundingClientRect(); return { l: r.left, r: r.right }; });
  assert.ok(dlg.l >= 0 && dlg.r <= 390, 'חלון השיוך בתוך המסך');
  await shot(mp, 'm03-phone-assign');
  await mp.click('#as-ok');
  await mp.waitForSelector('.toast:has-text("שויך לשרה כהן")');
  // תלמידי המורים, ומורים
  await mp.goto(BASE + '/admin#rooms=' + encodeURIComponent('students'));
  await mp.waitForSelector('tr[data-student="ayala.a@gmail.com"]');
  assert.ok(await wide() <= 1, 'תלמידי המורים — גלילה לצד: ' + await wide());
  assert.equal(await mp.evaluate(() => getComputedStyle(document.querySelector('tr[data-student]')).display), 'flex');
  await shot(mp, 'm04-phone-rooms-students');
  await mp.goto(BASE + '/admin#rooms=stations');
  await mp.waitForSelector('.item[data-station]');
  assert.ok(await wide() <= 1, 'עמדות — גלילה לצד: ' + await wide());
  await mp.goto(BASE + '/admin#teachers');
  await mp.waitForSelector('table.t.cards tbody tr.click');
  assert.ok(await wide() <= 1, 'מורים — גלילה לצד: ' + await wide());
  await shot(mp, 'm05-phone-teachers');
  await mp.locator('table.t.cards tbody tr.click', { hasText: 'שרה כהן' }).click();
  await mp.waitForSelector('.drawer #rt-students');
  assert.ok(await wide() <= 1, 'כרטיס המורה — גלילה לצד: ' + await wide());
  await m.close();
});

await step('אין שגיאות JS בדפים', async () => { assert.deepEqual(errors, []); });

await browser.close();
server.kill();
console.log(`\n${passed} שלבים עברו${process.exitCode ? ' — יש כישלונות' : ''}`);
process.exit(process.exitCode || 0);
