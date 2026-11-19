// בדיקה בדפדפן אמיתי (Playwright + Chromium) של עמדת הניהול של המורה (/account → console.js) ושל דף התלמיד (עצמאי / קוד מורה).
// רץ מול אמולטור Firebase ושרת מקומי (tools/dev-server.mjs), עם נתוני דוגמה: עמדות עם הגשות, תלמידים שהצטרפו, כיתות.
// צילומי מסך: SHOTS (ברירת מחדל test/shots).
//   npm run test:console
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
const PORT = 8788, BASE = `http://127.0.0.1:${PORT}`;
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
  const r = await api('POST', '/v1/auth/redeem', { code: ex.code, verifier, machine: m, machineName: 'מחשב בבית', client: 'test' });
  return { 'x-dsviz-access': r.token, 'x-dsviz-machine': m };
}

// ------------------------------------------------------------------ נתוני דוגמה
const admin = await idToken('admin@test.dev', 'מנהל');
await api('POST', '/v1/admin/teacher', { create: true, email: 'sara.cohen@ort.org.il', name: 'שרה כהן', school: 'אורט רחובות', seats: 20 }, bearer(admin));
const code = (await api('GET', '/v1/admin/teacher?email=sara.cohen@ort.org.il', null, bearer(admin))).teacher.code;
const stations = {};
for (const n of [1, 2, 3, 10, 11, 12]) {
  const m = mid();
  const r = await api('POST', '/v1/station/claim', { code, name: 'עמדה ' + n, machine: m, client: n > 10 ? 'dsviz-eclipse/1.0.2' : 'dsviz-vs/1.0.2' });
  stations[n] = { 'x-dsviz-access': r.token, 'x-dsviz-machine': m };
}
const Q_EXP = 'סעיף א: 18\nסעיף ב: 2\nהתור אחרי: [3,8,1,6]';
const QUEUE_CS = 'public static int SumQueue(Queue<int> q)\n{\n    int sum = 0;\n    while (!q.IsEmpty())\n        sum += q.Remove();\n    return sum;\n}\n';
const QUEUE_JAVA = 'public static int sumQueue(Queue<Integer> q) {\n    Queue<Integer> temp = new Queue<Integer>();\n    int sum = 0;\n    while (!q.isEmpty()) {\n        int x = q.remove();\n        sum += x;\n        temp.insert(x);\n    }\n    while (!temp.isEmpty())\n        q.insert(temp.remove());\n    return sum;\n}\n';
const bank = (over) => Object.assign({ exercise: { source: 'bank', bankId: 'queue-sum-even', topic: 'תור', title: 'סכום תור וזוגיים', level: 'קל' }, app: 'Visual Studio 2022' }, over);
await api('POST', '/v1/submit', bank({ who: 'נועם', files: [{ path: 'Exercises/Ex01_Queues/QueueTargil1.cs', text: QUEUE_CS }, { path: 'Exercises/Ex01_Queues/Program.cs', text: 'class Program { }\n' }], main: 'Exercises/Ex01_Queues/QueueTargil1.cs',
  check: { ran: true, compiles: true, output: 'סעיף א: 18\nסעיף ב: 2\nהתור אחרי: []', expected: Q_EXP, outputMatch: false } }), stations[1]);
await api('POST', '/v1/submit', bank({ who: 'אריאל', exercise: { source: 'bank', bankId: 'list-evens-remove', topic: 'רשימה מקושרת', title: 'מחיקת זוגיים מרשימה', level: 'בינוני' },
  files: [{ path: 'ListTargil4.cs', text: 'static Node<int> RemoveEvens(Node<int> head)\n{\n    return head\n}\n' }], check: { ran: true, compiles: false, error: 'ListTargil4.cs, שורה 3: חסר ; בסוף השורה' } }), stations[1]);
await api('POST', '/v1/submit', bank({ who: 'יובל', files: [{ path: 'QueueTargil1.cs', text: QUEUE_CS }], check: { ran: true, compiles: true, output: Q_EXP, expected: Q_EXP, outputMatch: true } }), stations[2]);
const kids = [['noam.levi@gmail.com', 'נועם לוי'], ['ariel.cohen@gmail.com', 'אריאל כהן'], ['yuval.m@gmail.com', 'יובל מזרחי'], ['ido.s@gmail.com', 'עידו שפירא'], ['itamar.p@gmail.com', 'איתמר פרץ']];
const plug = {};
for (const [email, name] of kids) {
  plug[email] = await studentPlugin(email, name);
  await api('POST', '/v1/student/join', { code }, plug[email]);
}
const teacherTok = await idToken('sara.cohen@ort.org.il', 'שרה כהן');
const c1 = (await api('POST', '/v1/me/class', { op: 'create', name: 'יא 3' }, bearer(teacherTok))).id;
const c2 = (await api('POST', '/v1/me/class', { op: 'create', name: 'יב 1' }, bearer(teacherTok))).id;
for (const [email, cls] of [['noam.levi@gmail.com', c1], ['ariel.cohen@gmail.com', c1], ['yuval.m@gmail.com', c1], ['ido.s@gmail.com', c2]]) {
  await api('POST', '/v1/me/member', { op: 'move', student: email, classId: cls }, bearer(teacherTok));
}
await api('POST', '/v1/submit', bank({ files: [{ path: 'src/ex01_queues/QueueTargil1.java', text: QUEUE_JAVA }], app: 'Eclipse', check: { ran: true, compiles: true, output: Q_EXP, expected: Q_EXP, outputMatch: true } }), plug['noam.levi@gmail.com']);
await api('POST', '/v1/submit', bank({ exercise: { source: 'own', title: 'מחסנית מדף עבודה' }, files: [{ path: 'src/Ex03_Stack.java', text: 'public class Ex03_Stack {\n    public static void main(String[] args) {\n        System.out.println("המחסנית: [5,3,1]");\n    }\n}\n' }], app: 'IntelliJ IDEA',
  check: { ran: true, compiles: true, output: 'המחסנית: [5,3,1]' } }), plug['noam.levi@gmail.com']);
await api('POST', '/v1/submit', bank({ files: [{ path: 'QueueTargil1.java', text: QUEUE_JAVA.replace('q.insert(temp.remove());', '') }], check: { ran: true, compiles: true, output: 'סעיף א: 18\nסעיף ב: 2\nהתור אחרי: []', expected: Q_EXP, outputMatch: false } }), plug['ariel.cohen@gmail.com']);
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

const page = await ctx.newPage();
await step('המורה נכנס ל-/account → עמדת הניהול: הקוד, ההיתרים, 6 מחשבים ממוינים לפי השם (2 לפני 10)', async () => {
  await page.goto(BASE + '/account');
  await googlePopup(page, '.gbtn', 'sara.cohen@ort.org.il', 'שרה כהן');
  await page.waitForSelector('.con-tab');
  assert.equal(await page.locator('.con-code').textContent(), code);
  const names = await page.locator('.con-table tbody tr td:first-child b').allTextContents();
  assert.deepEqual(names, ['עמדה 1', 'עמדה 2', 'עמדה 3', 'עמדה 10', 'עמדה 11', 'עמדה 12']);
  assert.ok(await page.locator('text=2 הגשות ממתינות').count());
  // "הגשות חדשות": 3 מעמדות + 3 מתלמידים, ועיגול אדום עם המספר על כל טאב
  assert.equal(await page.locator('#stat-new b').textContent(), '6');
  assert.ok(await page.locator('#stat-new:has-text("3 מתלמידים · 3 ממחשבי המעבדה")').count());
  assert.equal(await page.locator('#tab-st .newdot').textContent(), '3');
  assert.equal(await page.locator('#tab-su .newdot').textContent(), '3');
  // הסרגל העליון נשאר שורה אחת (המייל ליד העיגול) — שום עיצוב של עמדת הניהול לא נוגע בו
  assert.equal(await page.evaluate(() => getComputedStyle(document.querySelector('.top .who')).flexDirection), 'row');
  await shot(page, 'c01-console-stations');
});

await step('הגשות של עמדה: שם המגיש, הקוד, והפלט של התלמיד מול הפלט הרצוי — שורה שונה מסומנת', async () => {
  await page.click('tr:has-text("עמדה 1") button:has-text("הגשות")');
  await page.waitForSelector('pre.code');
  await page.click('.d-item:has-text("סכום תור")');
  await page.waitForSelector('pre.code:has-text("SumQueue")');
  assert.ok((await page.locator('pre.code').textContent()).includes('SumQueue'));
  assert.equal(await page.locator('.outcol').first().locator('.ln.bad').count(), 1, 'רק השורה "התור אחרי" שונה');
  assert.ok(await page.locator('text=מגיש: נועם').count());
  assert.equal(await page.locator('.file').count(), 2, 'שני קבצים — לשוניות');
  await shot(page, 'c02-station-submission');
  await page.click('.d-item:has-text("מחיקת זוגיים")');
  await page.waitForSelector('text=הקוד לא התקמפל');
  assert.ok(await page.locator('.ln.bad:has-text("חסר ;")').count());
  await shot(page, 'c03-station-compile-error');
});

await step('מחיקת הגשה של עמדה, ושחרור עמדה (אישור בתוך השורה) — ההיתר חוזר', async () => {
  await page.click('.d-code .d-top button:has-text("מחיקה")');
  await page.waitForSelector('.toast:has-text("ההגשה נמחקה")');
  await page.waitForSelector('text=הגשות מעמדה 1');
  assert.equal(await page.locator('.d-item').count(), 1);
  await page.click('tr:has-text("עמדה 12") button:has-text("שחרור")');
  await page.waitForSelector('.confirm');
  await shot(page, 'c04-release-confirm');
  await page.click('.confirm button:has-text("שחרור")');
  await page.waitForSelector('.toast:has-text("שוחררה")');
  assert.equal(await page.locator('.con-table tbody tr:has-text("עמדה 12")').count(), 0);
  assert.ok(await page.locator('text=5 מתוך 20').count(), 'היתרים: 5 מתוך 20');
  assert.ok(await page.locator('text=שחרורים החודש: 1 מתוך 5').count());
});

await step('טאב תלמידים: כיתות בצד, "בלי כיתה", ותרגילים של תלמיד עם בדיקת ההרצה', async () => {
  await page.click('#tab-su');
  await page.waitForSelector('.folders');
  assert.deepEqual((await page.locator('.folder span:first-child').allTextContents()).slice(0, 4), ['כל התלמידים', 'יא 3', 'יב 1', 'בלי כיתה']);
  await page.click('.folder:has-text("יא 3")');
  assert.equal(await page.locator('.con-table tbody tr').count(), 3);
  // הגשות חדשות: ליד התלמיד ("2 חדשות"), ועל הכיתה בצד (3)
  assert.ok(await page.locator('tr:has-text("נועם לוי") .pill:has-text("2 חדשות")').count());
  assert.ok(await page.locator('tr:has-text("אריאל כהן") .pill:has-text("חדשה")').count());
  assert.equal(await page.locator('.folder:has-text("יא 3") .newdot').textContent(), '3');
  await page.click('tr:has-text("נועם לוי") button:has-text("תרגילים")');
  await page.waitForSelector('pre.code');
  await page.click('.d-item:has-text("סכום תור")');
  await page.waitForSelector('pre.code:has-text("sumQueue")');
  assert.ok(await page.locator('text=הפלט תואם לפלט הרצוי').count());
  await shot(page, 'c05-students-class');
  await page.click('.d-item:has-text("מחסנית מדף עבודה")');
  await page.waitForSelector('text=אין פלט רצוי');
  // שתי ההגשות של נועם נפתחו — הוא כבר לא "חדש", והמספרים למעלה ירדו בלי טעינה מחדש
  assert.equal(await page.locator('tr:has-text("נועם לוי") .pill:has-text("חדשות")').count(), 0);
  assert.equal(await page.locator('#tab-su .newdot').textContent(), '1');
  assert.equal(await page.locator('.folder:has-text("יא 3") .newdot').textContent(), '1');
});

await step('"לפי תרגיל" — טבלת הגשות: שורה לכל תלמיד, עמודה לכל תרגיל, סימן קטן בכל תא; לחיצה על תא פותחת את הקוד של התלמיד', async () => {
  await page.click('#view-ex');
  await page.waitForSelector('table.gridt');
  // כיתה יא 3: שלושה תלמידים, שני תרגילים (החדש ביותר ראשון)
  assert.deepEqual(await page.locator('.gridt tbody th.gname .gn').allTextContents(), ['אריאל כהן', 'יובל מזרחי', 'נועם לוי']);
  assert.equal(await page.locator('.gridt thead th.gcol').count(), 2);
  const col = page.locator('.gridt thead th.gcol', { hasText: 'סכום תור וזוגיים' });
  assert.equal((await col.locator('.gc').textContent()).trim().slice(0, 3), '2/3', 'נועם ואריאל הגישו, יובל לא');
  assert.equal(await col.locator('.newdot').textContent(), '1', 'ההגשה של אריאל עוד לא נפתחה');
  const row = name => page.locator('.gridt tbody tr', { hasText: name });
  assert.equal(await row('נועם לוי').locator('button.g.ok').count(), 1);
  assert.equal(await row('נועם לוי').locator('button.g.none').count(), 1, 'התרגיל האישי — בלי בדיקת הרצה');
  assert.equal(await row('נועם לוי').locator('.gsum').textContent(), '2/2');
  assert.equal(await row('אריאל כהן').locator('button.g.diff.new').count(), 1);
  assert.equal(await row('אריאל כהן').locator('.gno').count(), 1);
  assert.equal(await row('יובל מזרחי').locator('.gno').count(), 2, 'לא הגיש כלום');
  assert.equal(await row('יובל מזרחי').locator('.gsum').textContent(), '0/2');
  assert.match(await row('אריאל כהן').locator('button.g').getAttribute('title'), /אריאל כהן · סכום תור וזוגיים · הפלט שונה/);
  // הטבלה קומפקטית: כל שלושת התלמידים עם הכותרת והמקרא בפחות מ-230 פיקסלים גובה
  const hgt = await page.evaluate(() => document.querySelector('.gwrap').getBoundingClientRect().height);
  assert.ok(hgt < 190, 'גובה הטבלה: ' + hgt);
  await shot(page, 'c13-by-exercise');
  await row('אריאל כהן').locator('button.g').click();
  await page.waitForSelector('pre.code:has-text("sumQueue")');
  assert.ok(await page.locator('.d-head:has-text("התרגילים של אריאל כהן")').count());
  assert.ok(await page.locator('text=הפלט שונה מהפלט הרצוי').count());
  await page.waitForSelector('#tab-su:not(:has(.newdot))');   // זו הייתה ההגשה החדשה האחרונה של תלמידים
  assert.equal(await row('אריאל כהן').locator('button.g.new').count(), 0, 'נפתחה — כבר לא "חדש"');
  assert.equal(await row('אריאל כהן').locator('button.g[aria-current="true"]').count(), 1, 'התא הפתוח מסומן');
  await shot(page, 'c14-by-exercise-code');
  // כל התלמידים: 5 שורות; "סכום תור" — 2 מתוך 5
  await page.click('.folder:has-text("כל התלמידים")');
  assert.equal(await page.locator('.gridt tbody tr').count(), 5);
  assert.equal((await page.locator('.gridt thead th.gcol', { hasText: 'סכום תור וזוגיים' }).locator('.gc').textContent()).trim(), '2/5');
  await page.click('#view-students');
  await page.waitForSelector('th:has-text("ההגשה האחרונה")');
});

await step('"הגשות חדשות" למעלה: לחיצה מובילה לתלמידים שיש להם הגשה חדשה בלבד; המתג מחזיר את כולם', async () => {
  // עידו מגיש עכשיו (מהתוסף) — אחרי טעינה מחדש הוא היחיד עם הגשה חדשה
  await api('POST', '/v1/submit', bank({ files: [{ path: 'QueueTargil1.java', text: QUEUE_JAVA }], app: 'IntelliJ IDEA', check: { ran: true, compiles: true, output: Q_EXP, expected: Q_EXP, outputMatch: true } }), plug['ido.s@gmail.com']);
  await page.reload();
  await page.waitForSelector('.con-tab');
  assert.equal(await page.locator('#tab-su .newdot').textContent(), '1');
  await page.click('#stat-new');
  await page.waitForSelector('#only-new[aria-pressed="true"]');
  assert.deepEqual(await page.locator('.con-table tbody tr td:first-child b').allTextContents(), ['עידו שפירא']);
  await shot(page, 'c15-only-new');
  await page.click('#only-new');
  await page.waitForSelector('#only-new[aria-pressed="false"]');
  assert.equal(await page.locator('.con-table tbody tr').count(), 5);
});

await step('כיתה חדשה, העברת תלמיד אליה, ושינוי שם', async () => {
  await page.click('.folder.add');
  await page.fill('#new-class', 'יב 4');
  await page.click('.newcls button');
  await page.waitForSelector('.folder[aria-current="true"]:has-text("יב 4")');
  await page.click('.folder:has-text("בלי כיתה")');
  await page.selectOption('tr:has-text("איתמר פרץ") select', { label: 'יב 4' });
  await page.waitForTimeout(400);
  await page.click('.folder:has-text("יב 4")');
  assert.equal(await page.locator('.con-table tbody tr').count(), 1);
  await page.click('button:has-text("שינוי שם")');
  await page.fill('#rename-class', 'יב 4 — מגמת מחשבים');
  await page.click('button:has-text("שמירה")');
  await page.waitForSelector('.folder:has-text("יב 4 — מגמת מחשבים")');
});

await step('מחיקת תלמיד: אזהרה עם מספר ההגשות; אחרי האישור — יוצא מהרשימה', async () => {
  await page.click('.folder:has-text("יא 3")');
  await page.click('tr:has-text("אריאל כהן") button:has-text("מחיקה")');
  await page.waitForSelector('.confirm:has-text("ההגשה שלו נמחקת")');
  await shot(page, 'c06-delete-student-confirm');
  await page.click('.confirm button:has-text("מחיקה לצמיתות")');
  await page.waitForSelector('.toast:has-text("נמחק מהרשימה")');
  assert.equal(await page.locator('.con-table tbody tr:has-text("אריאל כהן")').count(), 0);
});

await step('מחיקת כיתה שלמה: הכפתור פעיל רק אחרי הקלדת השם המדויק', async () => {
  await page.click('button:has-text("מחיקת הכיתה")');
  await page.waitForSelector('#confirm-class');
  assert.equal(await page.locator('.confirm button:has-text("מחיקה לצמיתות")').isDisabled(), true);
  await page.fill('#confirm-class', 'יא');
  assert.equal(await page.locator('.confirm button:has-text("מחיקה לצמיתות")').isDisabled(), true);
  await page.fill('#confirm-class', 'יא 3');
  await shot(page, 'c07-delete-class-confirm');
  await page.click('.confirm button:has-text("מחיקה לצמיתות")');
  await page.waitForSelector('.toast:has-text("הכיתה ״יא 3״ נמחקה")');
  assert.equal(await page.locator('.folder:has-text("יא 3")').count(), 0);
  assert.equal(await page.locator('.con-table tbody tr:has-text("נועם לוי")').count(), 0);
});

await step('הצטרפות סגורה (מתג) — נשמר אחרי טעינה מחדש', async () => {
  await page.click('#join-open');
  await page.waitForSelector('text=הקוד לא מצרף תלמידים חדשים');
  await page.reload();
  await page.waitForSelector('.con-tab');
  await page.click('#tab-su');
  assert.equal(await page.locator('#join-open').getAttribute('aria-pressed'), 'false');
  await page.click('#join-open');
  await page.waitForSelector('#join-open[aria-pressed="true"]');
});

await step('תלמיד בדפדפן: בוחר "תלמיד עצמאי" → ואז מצטרף בקוד המורה; קוד שגוי — הודעה', async () => {
  const sctx = await browser.newContext({ viewport: { width: 1280, height: 900 }, deviceScaleFactor: 2, locale: 'he-IL' });
  sctx.on('page', p => p.on('pageerror', e => errors.push(String(e))));
  const sp = await sctx.newPage();
  await sp.goto(BASE + '/account');
  await googlePopup(sp, '.gbtn', 'shira.b@gmail.com', 'שירה ברק');
  // פעם ראשונה: ההרשמה (שם מלא, טלפון) יחד עם הבחירה "תלמיד עצמאי / יש לי קוד מורה"
  await sp.waitForSelector('h2:has-text("השלמת ההרשמה")');
  assert.equal(await sp.inputValue('#reg-name'), 'שירה ברק');
  await shot(sp, 'c08-student-choose');
  await sp.fill('#reg-phone', '050-2223344');
  await sp.check('#reg-mode-independent');
  await sp.click('form button[type=submit]');
  await sp.waitForSelector('.pill:has-text("תלמיד עצמאי")');
  await sp.waitForSelector('h2:has-text("הפרטים שלי")');
  await sp.fill('#join-code', 'ZZZZ');
  await sp.click('button:has-text("הצטרפות")');
  await sp.waitForSelector('.toast:has-text("הקוד לא נכון")');
  await sp.fill('#join-code', code.toLowerCase());
  await sp.click('button:has-text("הצטרפות")');
  await sp.waitForSelector('text=משויך');
  assert.ok(await sp.locator('b:has-text("שרה כהן")').count());
  await shot(sp, 'c09-student-joined');
  await page.reload();
  await page.waitForSelector('.con-tab');
  await page.click('#tab-su');
  await page.click('.folder:has-text("בלי כיתה")');
  assert.ok(await page.locator('tr:has-text("שירה ברק")').count(), 'מופיעה אצל המורה ב"בלי כיתה"');
  await sctx.close();
});

await step('מסוף ההנהלה — "דיווחים על בעיות": רשימה עם מי שלח ושל איזה מורה, פתיחה — פרטי המורה, ההודעה והבדיקה המעמיקה; טופל', async () => {
  const rid = () => randomBytes(12).toString('base64url');
  const when = new Date(Date.now() - 2 * 86400000).toISOString();
  const msg = "Program.cs(5,13): error CS0234: The type or namespace name 'GridLib' does not exist in the namespace 'Unit4' (are you missing an assembly reference?)";
  const diag = '== DSViz-Diagnose 1.0.2 ==\nWindows 11 Pro 10.0.26100 · he-IL\nVisual Studio Community 2022 17.14.45 · C:\\Program Files\\Microsoft Visual Studio\\2022\\Community\nDSViz extension 1.0.26321.808 (enabled)\nproject: C:\\Users\\%USERNAME%\\source\\repos\\List\\List.csproj (.NET Framework 4.7.2, Reference Unit4 -> ..\\Unit4.dll)\n';
  await api('POST', '/v1/report', { id: rid(), kind: 'visual-run', auto: true, title: 'ההרצה הוויזואלית לא התחילה: CS0234 (GridLib)', message: msg, app: 'Visual Studio 2022', appVersion: '17.14.45', dsvizVersion: '1.0.2', os: 'Windows 11 (10.0.26100)', context: { lang: 'csharp', project: 'classic', schoolLib: 'Unit4' }, diag, occurredAt: when }, stations[3]);
  await api('POST', '/v1/report', { id: rid(), kind: 'visual-run', auto: true, title: 'ההרצה הוויזואלית לא התחילה: CS0234 (GridLib)', message: msg, app: 'Visual Studio 2022', dsvizVersion: '1.0.2', diag }, plug['ido.s@gmail.com']);
  await api('POST', '/v1/report', { id: rid(), kind: 'manual', auto: false, title: 'החלון של מאגר השאלות לא נפתח', note: 'לוחצים על "מאגר שאלות" ולא קורה כלום', app: 'Eclipse 2024-09', dsvizVersion: '1.0.2' }, {});
  const adm = await ctx.newPage();
  await adm.goto(BASE + '/admin#reports');
  await googlePopup(adm, '.gbtn', 'admin@test.dev', 'מנהל');
  await adm.waitForSelector('h1:has-text("דיווחים על בעיות")');
  assert.equal(await adm.locator('table.t tbody tr').count(), 3);
  assert.ok(await adm.locator('text=עוד 1 דומים').count());
  assert.ok(await adm.locator('td:has-text("של המורה שרה כהן")').count());
  await shot(adm, 'c11-admin-reports');
  await adm.locator('table.t tbody tr', { hasText: 'עמדה 3' }).click();
  await adm.waitForSelector('h2:has-text("הבדיקה המעמיקה")');
  assert.ok(await adm.locator('a:has-text("sara.cohen@ort.org.il")').count());
  assert.ok(await adm.locator('text=המחשב היה בלי אינטרנט').count(), 'נשלח אחרי ניתוק — מופיע');
  assert.ok(await adm.locator('pre.diag:has-text("DSViz-Diagnose")').count());
  await shot(adm, 'c12-admin-report');
  await adm.fill('#r-note', 'תוקן בגרסה 1.0.3 — using לספריות הגרפיות יורד בהרצה הוויזואלית');
  await adm.click('button:has-text("סימון: טופל")');
  await adm.waitForSelector('.toast:has-text("סומן כטופל")');
  await adm.waitForSelector('h1:has-text("דיווחים על בעיות")');
  assert.equal(await adm.locator('table.t tbody tr').count(), 2);
  await adm.close();
});

await step('מסוף ההנהלה — "בדיקת מערכת": השרת בודק את עצמו (Firestore, שאילתות, חתימה, Google) — אין שורה אדומה', async () => {
  const adm = await ctx.newPage();
  await adm.goto(BASE + '/admin#check');
  await adm.waitForSelector('h1:has-text("בדיקת מערכת")');
  await adm.waitForSelector('#check-summary[data-done]', { timeout: 30000 });
  assert.equal(await adm.locator('#check-summary').getAttribute('data-done'), 'ok', await adm.locator('#check-list').textContent());
  assert.equal(await adm.locator('.chk.fail').count(), 0);
  for (const id of ['firestore', 'merge', 'tx', 'rules', 'pass', 'google', 'queries', 'expire']) assert.equal(await adm.locator(`.chk[data-id="${id}"]`).getAttribute('data-state'), 'ok', id);
  assert.ok(await adm.locator('.chk[data-id="queries"]:has-text("לא חסר שום אינדקס")').count());
  await shot(adm, 'c16-admin-check');
  await adm.click('#check-run');
  await adm.waitForSelector('#check-summary[data-done="ok"]');
  await adm.close();
});

await step('רוחב טלפון: אין גלילה לרוחב בעמדת הניהול', async () => {
  const pctx = await browser.newContext({ viewport: { width: 400, height: 860 }, deviceScaleFactor: 2, locale: 'he-IL' });
  const pp = await pctx.newPage();
  await pp.goto(BASE + '/account');
  await googlePopup(pp, '.gbtn', 'sara.cohen@ort.org.il', 'שרה כהן');
  await pp.waitForSelector('.con-tab');
  const over = await pp.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth);
  assert.ok(over <= 1, 'גלילה לרוחב: ' + over);
  await shot(pp, 'c10-phone');
  await pp.click('#tab-su');
  await pp.click('#view-ex');
  await pp.waitForSelector('.seg');
  const over2 = await pp.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth);
  assert.ok(over2 <= 1, 'גלילה לרוחב ב"לפי תרגיל": ' + over2);
  await shot(pp, 'c10b-phone-exercises');
  await pctx.close();
});

assert.deepEqual(errors, [], 'שגיאות JavaScript בדפים: ' + errors.join(' | '));
await browser.close();
server.kill();
console.log(`\n${passed} בדיקות בדפדפן עברו` + (process.exitCode ? ' — יש כישלונות' : ''));
