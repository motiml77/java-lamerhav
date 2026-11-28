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
  // הגרסה עם מרכז הבקרה (caps: classroom) — בלי שלב ההרשמה, כדי שנתוני הדוגמה יהיו קצרים
  const r = await api('POST', '/v1/auth/redeem', { code: ex.code, verifier, machine: m, machineName: 'מחשב בבית', client: 'test', caps: ['classroom'] });
  return { 'x-dsviz-access': r.token, 'x-dsviz-machine': m };
}

// ------------------------------------------------------------------ נתוני דוגמה
const admin = await idToken('admin@test.dev', 'מנהל');
await api('POST', '/v1/admin/teacher', { create: true, email: 'sara.cohen@ort.org.il', name: 'שרה כהן', school: 'אורט רחובות', seats: 20 }, bearer(admin));
const code = (await api('GET', '/v1/admin/teacher?email=sara.cohen@ort.org.il', null, bearer(admin))).teacher.code;
const stations = {};
for (const n of [1, 2, 3, 10, 11, 12]) {
  const m = mid();
  const r = await api('POST', '/v1/station/claim', { code, name: 'עמדה ' + n, machine: m, client: n > 10 ? 'dsviz-eclipse/1.0.3' : 'dsviz-vs/1.0.3', caps: ['register', 'classroom'] });
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
  // ברשימה: שם המגיש (מה שהתלמיד כתב בהגשה מהעמדה) בשורה משלו, לפני שם התרגיל
  assert.deepEqual((await page.locator('.d-item .who b').allTextContents()).sort(), ['אריאל', 'נועם']);
  await page.click('.d-item:has-text("סכום תור")');
  await page.waitForSelector('pre.code:has-text("SumQueue")');
  assert.ok((await page.locator('pre.code').textContent()).includes('SumQueue'));
  assert.equal(await page.locator('.outcol').first().locator('.ln.bad').count(), 1, 'רק השורה "התור אחרי" שונה');
  assert.ok(await page.locator('text=מגיש: נועם').count());
  assert.equal(await page.locator('.file').count(), 2, 'שני קבצים — לשוניות');
  await shot(page, 'c02-station-submission');
  // מעבר עם העכבר על שם התרגיל: השאלה מהמאגר כמו שהתלמיד רואה אותה (מהעותק שבשרת — C# כי ההגשה ב-C#), עם הציור של התור
  await page.hover('.d-top b.pv-anchor');
  await page.waitForSelector('#ex-preview .pv-sec');
  const pv = await page.textContent('#ex-preview');
  assert.match(pv, /סעיף א'/);
  assert.match(pv, /C#/);
  assert.ok(await page.locator('#ex-preview .pv-queue .pv-cell').count() > 1, 'הציור של התור');
  await shot(page, 'c02b-exercise-preview');
  await page.mouse.move(5, 5);
  await page.waitForSelector('#ex-preview', { state: 'detached' });
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

await step('שינוי שם של מחשב: עורך בתוך השורה, Enter שומר, והשם החדש ברשימה', async () => {
  const row = page.locator('.con-table tbody tr').first();
  const old = (await row.locator('td b').first().textContent()).trim();
  await row.locator('button:has-text("שינוי שם")').click();
  await page.waitForSelector('#rename-st');
  assert.equal(await page.inputValue('#rename-st'), old);
  await page.fill('#rename-st', 'מעבדה 3 · עמדה 7');
  await shot(page, 'c04b-station-rename');
  await page.press('#rename-st', 'Enter');
  await page.waitForSelector('.toast:has-text("השם נשמר")');
  assert.ok(await page.locator('.con-table tbody tr:has-text("מעבדה 3 · עמדה 7")').count());
  assert.equal(await page.locator('#rename-st').count(), 0);
  await shot(page, 'c04c-station-renamed');
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
  assert.equal(await row('נועם לוי').locator('.gsum:not(.gavg)').textContent(), '2/2');
  assert.equal(await row('אריאל כהן').locator('button.g.diff.new').count(), 1);
  assert.equal(await row('אריאל כהן').locator('.gno').count(), 1);
  assert.equal(await row('יובל מזרחי').locator('.gno').count(), 2, 'לא הגיש כלום');
  assert.equal(await row('יובל מזרחי').locator('.gsum:not(.gavg)').textContent(), '0/2');
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

await step('ציון להגשה של תלמיד: המורה כותב ציון ליד הקוד, הוא נשמר ומופיע ברשימה, בממוצע ובטבלת ההגשות; עריכה והסרה בכל זמן', async () => {
  await page.click('tr:has-text("נועם לוי") button:has-text("תרגילים")');
  await page.waitForSelector('pre.code');
  await page.click('.d-item:has-text("סכום תור")');
  await page.waitForSelector('pre.code:has-text("sumQueue")');
  assert.equal(await page.inputValue('#grade-in'), '', 'עוד בלי ציון');
  assert.ok(await page.locator('#grade-info:has-text("אפשר לשנות אותו בכל זמן")').count());
  // ציון לא תקין — הודעה, ושום דבר לא נשמר
  await page.fill('#grade-in', '150');
  await page.click('#grade-save');
  await page.waitForSelector('.toast.err:has-text("בין 0 ל-100")');
  assert.equal(await page.locator('.d-item .pill:has-text("ציון")').count(), 0);
  // ציון תקין
  await page.fill('#grade-in', '90');
  await page.click('#grade-save');
  await page.waitForSelector('.toast:has-text("הציון 90 נשמר")');
  assert.ok(await page.locator('.d-item:has-text("סכום תור") .pill:has-text("ציון 90")').count(), 'ברשימת התרגילים של התלמיד');
  assert.equal(await page.inputValue('#grade-in'), '90');
  assert.ok(await page.locator('#grade-save:has-text("עדכון הציון")').count());
  assert.equal((await page.locator('tr:has-text("נועם לוי") td.avg b').textContent()).trim(), '90', 'הממוצע בטבלת התלמידים');
  assert.ok(await page.locator('tr:has-text("נועם לוי") td.avg:has-text("ציון אחד")').count());
  // הטבלה לא גולשת לצד: גם כפתור "מחיקה" שבקצה השורה נראה כולו
  const fits = await page.evaluate(() => { const w = document.querySelector('.con-main .tablewrap'); return w.scrollWidth - w.clientWidth; });
  assert.ok(fits <= 1, 'טבלת התלמידים רחבה מדי ב-' + fits + ' פיקסלים');
  await shot(page, 'c17-grade');
  // עריכה (Enter שומר), ואחרי טעינה מחדש הציון עדיין שם
  await page.fill('#grade-in', '75');
  await page.press('#grade-in', 'Enter');
  await page.waitForSelector('.toast:has-text("הציון 75 נשמר")');
  // ציון לתרגיל שני — הממוצע מתעדכן
  await page.click('.d-item:has-text("מחסנית מדף עבודה")');
  await page.waitForSelector('text=אין פלט רצוי');
  assert.equal(await page.inputValue('#grade-in'), '');
  await page.fill('#grade-in', '100');
  await page.click('#grade-save');
  await page.waitForSelector('.toast:has-text("הציון 100 נשמר")');
  assert.equal((await page.locator('tr:has-text("נועם לוי") td.avg b').textContent()).trim(), '88', 'ממוצע של 75 ו-100');
  await page.reload();
  await page.waitForSelector('.con-tab');
  await page.click('#tab-su');
  assert.equal((await page.locator('tr:has-text("נועם לוי") td.avg b').textContent()).trim(), '88');
  assert.ok(await page.locator('tr:has-text("אריאל כהן") td.avg:has-text("—")').count(), 'תלמיד בלי ציונים');
  // בטבלת ההגשות: התא מציג את הציון, ויש עמודת ממוצע
  await page.click('#view-ex');
  await page.waitForSelector('table.gridt');
  const noam = page.locator('.gridt tbody tr', { hasText: 'נועם לוי' });
  assert.deepEqual((await noam.locator('button.g.graded').allTextContents()).sort(), ['100', '75']);
  assert.equal(await noam.locator('.gavg').textContent(), '88');
  assert.equal(await page.locator('.gridt tbody tr', { hasText: 'אריאל כהן' }).locator('.gavg').textContent(), '—');
  assert.match(await noam.locator('button.g.graded', { hasText: '75' }).getAttribute('title'), /נועם לוי · סכום תור וזוגיים · הפלט תואם · ציון 75/);
  await shot(page, 'c18-grades-grid');
  // לחיצה על התא → הקוד עם הציון; הסרת הציון (שדה ריק)
  await noam.locator('button.g.graded', { hasText: '100' }).click();
  await page.waitForSelector('#grade-in');
  assert.equal(await page.inputValue('#grade-in'), '100');
  await page.fill('#grade-in', '');
  await page.click('#grade-save');
  await page.waitForSelector('.toast:has-text("הציון הוסר")');
  assert.equal(await noam.locator('button.g.graded').count(), 1);
  assert.equal(await noam.locator('.gavg').textContent(), '75');
  await page.click('#view-students');
  await page.waitForSelector('th:has-text("ממוצע ציונים")');
  // הגשה מעמדת מחשב — בלי ציון
  await page.click('#tab-st');
  await page.click('tr:has-text("עמדה 2") button:has-text("הגשות")');
  await page.waitForSelector('pre.code');
  assert.equal(await page.locator('#grade-in').count(), 0, 'להגשה מעמדה אין שדה ציון');
  await page.click('#tab-su');
  await page.waitForSelector('.folders');
});

await step('התלמיד רואה בדף "החשבון שלי" את התרגילים שהגיש, עם הציון שהמורה נתן', async () => {
  await api('POST', '/v1/student/register', { fullName: 'נועם לוי', phone: '0501234567' }, plug['noam.levi@gmail.com']);
  const nctx = await browser.newContext({ viewport: { width: 1280, height: 900 }, deviceScaleFactor: 2, locale: 'he-IL' });
  nctx.on('page', p => p.on('pageerror', e => errors.push(String(e))));
  const np = await nctx.newPage();
  await np.goto(BASE + '/account');
  await googlePopup(np, '.gbtn', 'noam.levi@gmail.com', 'נועם לוי');
  await np.waitForSelector('#my-work');
  assert.ok(await np.locator('#my-work:has-text("למורה שרה כהן")').count());
  const q = np.locator('#my-work .item', { hasText: 'סכום תור וזוגיים' });
  assert.equal((await q.locator('.mygrade b').textContent()).trim(), '75');
  assert.ok(await np.locator('#my-work .item', { hasText: 'מחסנית מדף עבודה' }).locator('text=עוד בלי ציון').count(), 'הציון הוסר');
  await shot(np, 'c19-student-grades');
  await nctx.close();
});

await step('מורה שלא השתמש בגרסה עם מרכז הבקרה: הדף כמו בגרסה הרגילה (קוד, עמדות, חבילות) — בלי עמדת הניהול החדשה', async () => {
  await api('POST', '/v1/admin/teacher', { create: true, email: 'dana.old@ort.org.il', name: 'דנה', school: 'אורט', seats: 5 }, bearer(admin));
  const octx = await browser.newContext({ viewport: { width: 1280, height: 900 }, deviceScaleFactor: 2, locale: 'he-IL' });
  octx.on('page', p => p.on('pageerror', e => errors.push(String(e))));
  const op = await octx.newPage();
  await op.goto(BASE + '/account');
  await googlePopup(op, '.gbtn', 'dana.old@ort.org.il', 'דנה');
  await op.waitForSelector('h2:has-text("קוד המורה")');
  assert.ok(await op.locator('h1:has-text("הלוח של המורה")').count());
  assert.ok(await op.locator('h2:has-text("עמדות מחשב")').count());
  assert.equal(await op.locator('.con-tab').count(), 0, 'בלי עמדת הניהול החדשה');
  await shot(op, 'c20-teacher-regular-version');
  await octx.close();
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
  // מרכז הבקרה כבוי, ושירה לא השתמשה בגרסה עם מרכז הבקרה: הדף כמו בגרסה הרגילה — בלי הרשמה ובלי "המורה שלי"
  await sp.waitForSelector('h2:has-text("מאגר התרגילים")');
  assert.equal(await sp.locator('#reg-name').count(), 0);
  assert.ok(await sp.locator('.pill:has-text("פתוח")').count(), 'גם בגרסה הרגילה התלמיד בחינם (המתג "תלמידים בחינם")');
  assert.match(await sp.textContent('main'), /בלי תשלום/);
  await shot(sp, 'c08a-student-regular-version');
  // ההנהלה מוסיפה אותה לחשבונות הבדיקה — ועכשיו מרכז הבקרה פתוח לה
  await api('POST', '/v1/admin/settings', { classroomTesters: ['shira.b@gmail.com'] }, bearer(admin));
  await new Promise(r => setTimeout(r, 31000));   // ההגדרות נשמרות במטמון של השרת עד 30 שניות
  await sp.reload();
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

await step('הדף של המורה בשלוש קטגוריות: עבודות התלמידים (ראשון), קוד ועמדות, החשבון שלי — כל אחת בנפרד', async () => {
  // חלון חדש (הצעדים הקודמים נכנסו בחלון הזה כמנהל): המורה נכנס
  const tctx = await browser.newContext({ viewport: { width: 1440, height: 1000 }, deviceScaleFactor: 2, locale: 'he-IL' });
  tctx.on('page', p => p.on('pageerror', e => errors.push(String(e))));
  const page = await tctx.newPage();
  await page.goto(BASE + '/account');
  await googlePopup(page, '.gbtn', 'sara.cohen@ort.org.il', 'שרה כהן');
  await page.waitForSelector('#acc-work[aria-selected="true"]');
  assert.ok(await page.locator('#pane-work .con-strip .con-code').isVisible(), 'בעבודה: שורה דקה עם הקוד');
  assert.equal(await page.locator('#pane-seats').isVisible(), false);
  assert.equal(await page.locator('.con-stat').count(), 0, 'בלי כרטיסי הפרטים הכלליים בעמוד ההגשות');
  await shot(page, 'c22-account-work');
  await page.click('#acc-seats');
  await page.waitForSelector('#pane-seats .codebox');
  assert.ok(await page.locator('#pane-seats:has-text("עוד עמדות מחשב")').isVisible());
  assert.equal(await page.locator('#pane-work').isVisible(), false);
  assert.match(page.url(), /#seats$/);
  await shot(page, 'c23-account-seats');
  await page.click('#acc-me');
  await page.waitForSelector('#pane-me:has-text("המחשבים שלי")');
  await shot(page, 'c24-account-me');
  await page.reload();
  await page.waitForSelector('#acc-me[aria-selected="true"]');
  assert.ok(await page.locator('#pane-me').isVisible(), 'אחרי רענון — אותה קטגוריה');
  await page.click('#acc-work');
  await page.waitForSelector('#pane-work .con-tab');
  await tctx.close();
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

// ------------------------------------------------------------------ שיעורי בית: מורה עם כיתה של 34 תלמידים, כיתה קטנה, וכמה שיעורי בית
const HW_SHOTS = process.env.HW_SHOTS || SHOTS;
mkdirSync(HW_SHOTS, { recursive: true });
const day = n => new Date(Date.now() + n * 86400000).toLocaleDateString('en-CA', { timeZone: 'Asia/Jerusalem' });
await api('POST', '/v1/admin/teacher', { create: true, email: 'rina.hw@ort.org.il', name: 'רינה אברהם', school: 'אורט רחובות', seats: 5 }, bearer(admin));
const rcode = (await api('GET', '/v1/admin/teacher?email=rina.hw@ort.org.il', null, bearer(admin))).teacher.code;
await api('POST', '/v1/station/claim', { code: rcode, name: 'עמדה 1', machine: mid(), client: 'dsviz-eclipse/1.0.7', caps: ['register', 'classroom'] });
const rinaTok = await idToken('rina.hw@ort.org.il', 'רינה אברהם');
const big = (await api('POST', '/v1/me/class', { op: 'create', name: 'יב 2' }, bearer(rinaTok))).id;
const small = (await api('POST', '/v1/me/class', { op: 'create', name: 'יא 1' }, bearer(rinaTok))).id;
const FIRST = ['נועה', 'איתי', 'מאיה', 'יונתן', 'תמר', 'אורי', 'שירה', 'דניאל', 'הילה', 'עומר', 'רוני', 'אביגיל', 'עידו', 'ליה', 'גיא', 'יעל', 'אלון'];
const LAST = ['כהן', 'לוי', 'מזרחי', 'פרץ', 'ביטון'];
const pupils = [];
for (let i = 0; i < 40; i++) pupils.push({ email: `hw.pupil${i}@gmail.com`, name: FIRST[i % FIRST.length] + ' ' + LAST[Math.floor(i / FIRST.length) + (i % 2 ? 2 : 0)], cls: i < 34 ? big : small });
const H1 = ['stack-sum-size', 'stack-brackets', 'queue-sum-even'], H2 = ['tree-count-sum', 'list-evens-remove'], H3 = ['loops-collatz', 'arrays-counters', 'strings-anagram', 'recursion-digits'];
// שיעורי בית: לכיתה הגדולה עם מועד שעבר (הגשה עכשיו = באיחור), לכולם עם מועד שעוד לא הגיע, ולכיתה הגדולה בלי מועד
const hw1 = (await api('POST', '/v1/me/homework', { op: 'save', classId: big, items: H1, title: 'מחסנית ותור', due: day(-3) }, bearer(rinaTok))).id;
const hw2 = (await api('POST', '/v1/me/homework', { op: 'save', classId: '', items: H2, title: '', due: day(4) }, bearer(rinaTok))).id;
const hw3 = (await api('POST', '/v1/me/homework', { op: 'save', classId: big, items: H3, title: 'חזרה לבגרות', due: '' }, bearer(rinaTok))).id;
const meta = {
  'stack-sum-size': ['מחסנית', 'סכום וגודל של מחסנית'], 'stack-brackets': ['מחסנית', 'סוגריים מאוזנים'], 'queue-sum-even': ['תור', 'סכום תור וזוגיים'],
  'tree-count-sum': ['עץ בינארי', 'מספר הצמתים וסכום העץ'], 'list-evens-remove': ['שרשרת חוליות', 'שרשרת חדשה והוצאת חוליות'],
  'loops-collatz': ['לולאות', 'קולץ'], 'arrays-counters': ['מערכים', 'מערך מונים'], 'strings-anagram': ['מחרוזות', 'אנגרמה'], 'recursion-digits': ['רקורסיה', 'ספרות'],
};
const results = [{ ran: true, compiles: true, output: 'א', expected: 'א', outputMatch: true }, { ran: true, compiles: true, output: 'א', expected: 'ב', outputMatch: false }, { ran: true, compiles: false, error: 'חסר ;' }];
async function seedPupil(p, i) {
  const hdr = await studentPlugin(p.email, p.name);
  await api('POST', '/v1/student/register', { fullName: p.name, phone: '050' + String(1000000 + i) }, hdr);
  await api('POST', '/v1/student/join', { code: rcode }, hdr);
  await api('POST', '/v1/me/member', { op: 'move', student: p.email, classId: p.cls }, bearer(rinaTok));
  // מי מגיש מה: רוב הכיתה הגישה את כל שיעורי הבית הראשונים, חלק רק חלק; השאר לפי תבנית
  const mine = [];
  if (p.cls === big) {
    if (i % 5 !== 4) mine.push(...(i % 3 === 2 ? H1.slice(0, 2) : H1));
    if (i % 4 === 0) mine.push(...H3.slice(0, 1 + (i % 3)));
  }
  if (i % 3 !== 1) mine.push(...H2.slice(0, i % 6 === 0 ? 1 : 2));
  for (const [k, id] of mine.entries()) {
    await api('POST', '/v1/submit', { exercise: { source: 'bank', bankId: id, topic: meta[id][0], title: meta[id][1], level: 'קל' }, app: 'Eclipse',
      files: [{ path: 'src/Targil.java', text: `public class Targil { // ${p.name}\n    public static void main(String[] args) { }\n}\n` }], check: results[(i + k) % 3] }, hdr);
  }
}
for (let i = 0; i < pupils.length; i += 8) await Promise.all(pupils.slice(i, i + 8).map((p, k) => seedPupil(p, i + k)));
// ציונים לחלק מההגשות של שיעורי הבית הראשונים (המורה כבר פתחה אותן)
for (const p of pupils.slice(0, 12)) {
  const subs = (await api('GET', '/v1/me/submissions?student=' + encodeURIComponent(p.email), null, bearer(rinaTok))).submissions;
  for (const s of subs.filter(x => x.exercise.bankId === 'stack-sum-size')) await api('POST', '/v1/me/submission/grade', { id: s.id, grade: 70 + (s.id.charCodeAt(3) % 30) }, bearer(rinaTok));
}
const con0 = await api('GET', '/v1/me/console', null, bearer(rinaTok));
assert.equal(con0.students.length, 40);
assert.deepEqual(con0.homework.map(x => x.id), [hw3, hw2, hw1]);
console.log('נתוני שיעורי הבית מוכנים');

const hctx = await browser.newContext({ viewport: { width: 1440, height: 1000 }, deviceScaleFactor: 2, locale: 'he-IL' });
hctx.on('page', p => p.on('pageerror', e => errors.push(String(e))));
const hp = await hctx.newPage();
// צילום בלי ההודעה הקופצת (שלא תסתיר את הטבלה); tall — חלון גבוה, כדי שכל 34 השורות ייכנסו בטבלה (שנגללת בתוך 70% מהגובה)
async function hwShot(name, tall) {
  await hp.evaluate(() => { const t = document.querySelector('.toast'); if (t) t.hidden = true; });
  if (tall) await hp.setViewportSize({ width: 1440, height: 2000 });
  await hp.screenshot({ path: join(HW_SHOTS, name + '.png'), fullPage: true });
  if (tall) await hp.setViewportSize({ width: 1440, height: 1000 });
}
const expectUnseen = () => {
  // הגשות של שאלות משיעורי הבית שעוד לא נפתחו (כל התלמידים פעילים; שיעורי בית 1 ו-3 — לכיתה הגדולה, 2 — לכולם)
  let n = 0;
  for (const s of con0.students) for (const w of s.work) if (!w.seen && ((s.classId === big && (H1.includes(w.bankId) || H3.includes(w.bankId))) || H2.includes(w.bankId))) n++;
  return n;
};

await step('שיעורי בית: טאב שלישי עם מספר ההגשות שעוד לא נפתחו; רשימה של שורה דקה לכל שיעורי בית — כיתה, שאלות, מועד ("עבר המועד"), "הגישו X מתוך Y", ממתינות לציון', async () => {
  await hp.goto(BASE + '/account');
  await googlePopup(hp, '.gbtn', 'rina.hw@ort.org.il', 'רינה אברהם');
  await hp.waitForSelector('#tab-hw');
  assert.equal(await hp.locator('#tab-hw .n').textContent(), '3');
  assert.equal(await hp.locator('#tab-hw .newdot').textContent(), String(expectUnseen()));
  await hp.click('#tab-hw');
  await hp.waitForSelector('#hw-list');
  assert.equal(await hp.locator('.hw-row').count(), 3);
  assert.deepEqual(await hp.locator('.hw-row .hw-t b').allTextContents(), ['חזרה לבגרות', 'שיעורי בית ' + new Date().toLocaleDateString('he-IL', { timeZone: 'Asia/Jerusalem', day: 'numeric', month: 'numeric' }).replace(/\//g, '.'), 'מחסנית ותור']);
  const r1 = hp.locator(`.hw-row[data-id="${hw1}"]`);
  assert.ok(await r1.locator('.pill:has-text("עבר המועד")').count(), 'מועד שעבר');
  assert.equal(await hp.locator(`.hw-row[data-id="${hw2}"] .pill:has-text("עבר המועד")`).count(), 0, 'מועד שעוד לא הגיע');
  // שיעורי בית 1: לכיתה של 34; מי שלא מדלג (i%5!=4) הגיש — מלא כשלא i%3==2
  const big34 = pupils.map((p, i) => [p, i]).filter(([p]) => p.cls === big);
  const full1 = big34.filter(([, i]) => i % 5 !== 4 && i % 3 !== 2).length, part1 = big34.filter(([, i]) => i % 5 !== 4 && i % 3 === 2).length;
  assert.equal((await r1.locator('.hw-pt').textContent()).trim(), `הגישו ${full1} מתוך 34`);
  assert.equal((await r1.locator('.hw-part').textContent()).trim(), `· ${part1} בחלקן`);
  assert.match(await r1.locator('.hw-w').textContent(), /ממתינות לציון \d+/);
  assert.equal((await hp.locator(`.hw-row[data-id="${hw2}"] .hw-c`).textContent()).trim(), 'כל התלמידים');
  assert.match(await hp.locator(`.hw-row[data-id="${hw2}"] .hw-pt`).textContent(), /מתוך 40$/);
  // שורה דקה: שלוש שורות בפחות מ-170 פיקסלים
  const hgt = await hp.evaluate(() => document.querySelector('#hw-list').getBoundingClientRect().height);
  assert.ok(hgt < 170, 'גובה הרשימה: ' + hgt);
});

await step('שיעורי בית: לחיצה על שורה — הטבלה של 34 התלמידים (לפי הא"ב), עמודה לכל שאלה, סימון "באיחור", "הגיש" וממוצע; לחיצה על תא — הקוד והציון', async () => {
  await hp.click(`.hw-row[data-id="${hw1}"]`);
  await hp.waitForSelector('#hw-grid');
  const names = await hp.locator('#hw-grid tbody th.gname .gn').allTextContents();
  assert.equal(names.length, 34);
  const coll = new Intl.Collator('he', { numeric: true });
  assert.deepEqual(names, names.slice().sort(coll.compare), 'לפי הא"ב');
  assert.equal(await hp.locator('#hw-grid thead th.gcol').count(), 3);
  assert.ok(await hp.locator('#hw-grid button.g.late').count() > 10, 'הוגש אחרי המועד — מסומן');
  assert.ok(await hp.locator('.glegend .gkey:has-text("באיחור")').count());
  assert.ok(await hp.locator('#hw-grid button.g.graded').count() > 0, 'ציונים בתאים');
  const skip = pupils.findIndex((p, i) => p.cls === big && i % 5 === 4);
  const skipRow = hp.locator('#hw-grid tbody tr', { has: hp.locator(`.gn:text-is("${pupils[skip].name}")`) }).first();
  assert.equal(await skipRow.locator('.gno').count(), 3, 'לא הגיש כלום');
  assert.equal((await skipRow.locator('.gsum').first().textContent()).trim(), '0/3');
  // התאים באותו מראה של "לפי תרגיל": אותו גודל
  const sz = await hp.evaluate(() => { const b = document.querySelector('#hw-grid button.g:not(.graded)').getBoundingClientRect(); return [b.width, b.height]; });
  assert.deepEqual(sz, [26, 26]);
  await hwShot('hw-list-grid-30-students', true);
  // מעבר עם העכבר על שם השאלה — השאלה מהמאגר
  await hp.hover('#hw-grid thead th.gcol .gt.pv-anchor >> nth=1');
  await hp.waitForSelector('#ex-preview .pv-head');
  await hp.mouse.move(5, 5);
  await hp.waitForSelector('#ex-preview', { state: 'detached' });
  const before = +(await hp.locator('#tab-hw .newdot').textContent());
  const cell = hp.locator('#hw-grid button.g.new:not(.graded)').first();
  await cell.click();
  await hp.waitForSelector('#hw-panel .detail pre.code');
  assert.ok(await hp.locator('#hw-panel #grade-in').count(), 'שורת הציון — בתוך שיעורי הבית הפתוחים');
  assert.equal(+(await hp.locator('#tab-hw .newdot').textContent()), before - 1, 'נפתחה — יורדת מהמספר על הטאב');
  await hp.fill('#grade-in', '95');
  await hp.click('#grade-save');
  await hp.waitForSelector('.toast:has-text("הציון 95 נשמר")');
  assert.ok(await hp.locator('#hw-grid button.g.graded[aria-current="true"]:has-text("95")').count(), 'הציון בתא');
  await hwShot('hw-grid-cell-code');
});

await step('שיעורי בית חדשים: הטופס — כיתה, מועד, נושאים (כמה נבחרו בכל אחד), שאלות בשורות דקות עם בלון השאלה, תגיות להסרה; שליחה', async () => {
  await hp.click('#hw-new');
  await hp.waitForSelector('#hw-composer .hw-topic');
  assert.equal(await hp.locator('#hw-send').isDisabled(), true, 'בלי שאלות — אי אפשר לשלוח');
  assert.equal(await hp.locator('.hw-topic').count(), 10);
  await hp.selectOption('#hw-class', { label: 'יא 1' });
  await hp.fill('#hw-due', day(7));
  await hp.click('.hw-topic:has-text("מחסנית")');
  await hp.click('.hw-q[data-id="stack-brackets"]');
  await hp.click('.hw-q[data-id="stack-copy-greater"]');
  await hp.click('.hw-topic:has-text("תור")');
  await hp.click('.hw-q[data-id="queue-two-sum"]');
  await hp.click('.hw-q[data-id="queue-min-last"]');
  await hp.click('.hw-q[data-id="queue-min-last"]');   // לחיצה שנייה — מוציאה
  assert.equal(await hp.locator('.hw-topic:has-text("מחסנית") .hw-tn').textContent(), '2');
  assert.equal(await hp.locator('.hw-topic:has-text("תור") .hw-tn').textContent(), '1');
  assert.equal(await hp.locator('.hw-chip').count(), 3);
  assert.equal((await hp.locator('#hw-send').textContent()).trim(), 'שליחה לתלמידים (3 שאלות)');
  await hp.mouse.move(5, 5);
  await hp.waitForTimeout(500);
  await hwShot('hw-composer');
  await hp.hover('.hw-q[data-id="queue-two-sum"] .hw-qt');
  await hp.waitForSelector('#ex-preview .pv-head');
  await hwShot('hw-composer-preview');
  await hp.mouse.move(5, 5);
  await hp.waitForSelector('#ex-preview', { state: 'detached' });
  await hp.locator('.hw-chip[data-id="stack-copy-greater"] .hw-x').click();
  assert.equal((await hp.locator('#hw-send').textContent()).trim(), 'שליחה לתלמידים (2 שאלות)');
  await hp.fill('#hw-title', 'תרגול לפני המבחן');
  await hp.click('#hw-send');
  await hp.waitForSelector('.toast:has-text("שיעורי הבית נשלחו לכיתה יא 1 (2 שאלות)")');
  assert.equal(await hp.locator('.hw-row').count(), 4);
  assert.equal((await hp.locator('.hw-row').first().locator('.hw-t b').textContent()).trim(), 'תרגול לפני המבחן', 'החדשים ראשונים');
  assert.equal(await hp.locator('.hw-row').first().getAttribute('aria-expanded'), 'true', 'נפתחו');
  assert.equal(await hp.locator('#hw-grid tbody tr').count(), 6, 'יא 1: שישה תלמידים');
  // הכיתה האחרונה נזכרת לפעם הבאה
  await hp.click('#hw-new');
  await hp.waitForSelector('#hw-class');
  assert.equal(await hp.locator('#hw-class option:checked').textContent(), 'יא 1');
  await hp.click('#hw-cancel');
});

await step('עריכה ומחיקה של שיעורי בית (אישור בשורה); ההגשות נשארות', async () => {
  await hp.click('.hw-row >> nth=0');   // נסגר
  await hp.click(`.hw-row[data-id="${hw3}"]`);
  await hp.waitForSelector('#hw-edit');
  await hp.click('#hw-edit');
  await hp.waitForSelector('#hw-panel #hw-composer');
  assert.equal(await hp.locator('.hw-chip').count(), 4);
  assert.equal((await hp.locator('#hw-send').textContent()).trim(), 'שמירת השינויים (4 שאלות)');
  await hp.locator('.hw-chip[data-id="recursion-digits"] .hw-x').click();
  await hp.click('#hw-send');
  await hp.waitForSelector('.toast:has-text("השינויים נשמרו")');
  assert.equal((await hp.locator(`.hw-row[data-id="${hw3}"] .hw-n`).textContent()).trim(), '3 שאלות');
  await hp.click('#hw-delete');
  await hp.waitForSelector('#hw-panel .confirm');
  assert.match(await hp.locator('#hw-panel .confirm').textContent(), /ההגשות והציונים של התלמידים נשארים/);
  await hp.click('#hw-delete-yes');
  await hp.waitForSelector('.toast:has-text("שיעורי הבית נמחקו")');
  assert.equal(await hp.locator(`.hw-row[data-id="${hw3}"]`).count(), 0);
  const con = await api('GET', '/v1/me/console', null, bearer(rinaTok));
  assert.ok(con.students.some(s => s.work.some(w => w.bankId === 'loops-collatz')), 'ההגשות נשארו');
});

await step('"סיכום תלמידים": שורה לכל תלמיד — הגיש X מתוך Y, באיחור, ממוצע, ופס של ריבועים (ריבוע לכל שיעורי בית); סינון לפי כיתה; העמודה "שיעורי בית" בטבלת התלמידים', async () => {
  await hp.click('#hw-view-sum');
  await hp.waitForSelector('#hw-sum');
  assert.equal(await hp.locator('#hw-sum tbody tr').count(), 40);
  assert.equal(await hp.locator('#hw-sum tbody tr').first().locator('.hw-sq').count(), 3, 'שלושה שיעורי בית');
  assert.ok(await hp.locator('#hw-sum .hw-sq.na').count() >= 6, 'יא 1 לא קיבלו את שיעורי הבית של יב 2');
  assert.ok(await hp.locator('#hw-sum .hw-late').count() > 0, 'יש באיחור');
  await hwShot('hw-summary-all');
  await hp.selectOption('#hw-folder', { label: 'יב 2 (34)' });
  await hp.waitForSelector('#hw-sum');
  assert.equal(await hp.locator('#hw-sum tbody tr').count(), 34);
  await hwShot('hw-summary-class');
  await hp.click('#hw-view-list');
  assert.equal(await hp.locator('.hw-row').count(), 2, 'ב"יב 2": שלה ושל כולם');
  await hp.click('#tab-su');
  await hp.waitForSelector('th.hwcol:has-text("ש״ב")');
  assert.equal(await hp.locator('.folder[aria-current="true"] span:first-child').textContent(), 'יב 2', 'אותה כיתה כמו בשיעורי בית');
  assert.match(await hp.locator('.con-table tbody tr td.hwcol').first().textContent(), /^\d+\/\d+$/);
  const fits = await hp.evaluate(() => { const w = document.querySelector('.con-main .tablewrap'); return w.scrollWidth - w.clientWidth; });
  assert.ok(fits <= 1, 'טבלת התלמידים רחבה מדי ב-' + fits + ' פיקסלים');
});

await step('שיעורי בית: מצב ריק — מורה בלי שיעורי בית / בלי תלמידים; ורוחב טלפון בלי גלילה לצד', async () => {
  await api('POST', '/v1/admin/teacher', { create: true, email: 'new.hw@ort.org.il', name: 'מורה חדשה', school: 'אורט', seats: 2 }, bearer(admin));
  const ncode = (await api('GET', '/v1/admin/teacher?email=new.hw@ort.org.il', null, bearer(admin))).teacher.code;
  await api('POST', '/v1/station/claim', { code: ncode, name: 'עמדה 1', machine: mid(), client: 'dsviz-eclipse/1.0.7', caps: ['register', 'classroom'] });
  const nctx = await browser.newContext({ viewport: { width: 1280, height: 800 }, deviceScaleFactor: 2, locale: 'he-IL' });
  nctx.on('page', p => p.on('pageerror', e => errors.push(String(e))));
  const np = await nctx.newPage();
  await np.goto(BASE + '/account');
  await googlePopup(np, '.gbtn', 'new.hw@ort.org.il', 'מורה חדשה');
  await np.waitForSelector('#tab-hw');
  await np.click('#tab-hw');
  await np.waitForSelector('#hw-empty');
  assert.match(await np.textContent('#hw-empty'), /עוד אין תלמידים שמחוברים אליך/);
  await nctx.close();
  await hp.click('#tab-hw');
  await hp.selectOption('#hw-folder', { label: 'בלי כיתה (0)' });
  assert.ok(await hp.locator('.hw-row').count() >= 1, 'של כולם — גם למי שבלי כיתה');
  const pctx = await browser.newContext({ viewport: { width: 400, height: 860 }, deviceScaleFactor: 2, locale: 'he-IL' });
  const pp = await pctx.newPage();
  await pp.goto(BASE + '/account');
  await googlePopup(pp, '.gbtn', 'rina.hw@ort.org.il', 'רינה אברהם');
  await pp.waitForSelector('#tab-hw');
  await pp.click('#tab-hw');
  await pp.waitForSelector('#hw-list');
  await pp.click('.hw-row >> nth=1');
  await pp.waitForSelector('#hw-grid');
  const over = await pp.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth);
  assert.ok(over <= 1, 'גלילה לרוחב בשיעורי בית: ' + over);
  await pp.screenshot({ path: join(HW_SHOTS, 'hw-phone.png'), fullPage: false });
  await pctx.close();
  await hctx.close();
});

assert.deepEqual(errors, [], 'שגיאות JavaScript בדפים: ' + errors.join(' | '));
await browser.close();
server.kill();
console.log(`\n${passed} בדיקות בדפדפן עברו` + (process.exitCode ? ' — יש כישלונות' : ''));
