// בדיקות לעמדת הניהול של המורה (src/access/classroom.js) מול Firestore ו-Auth אמיתיים — באמולטור:
// תלמיד עצמאי / הצטרפות בקוד מורה, הגשה מתלמיד ומעמדה ("שם המגיש" חובה, בדיקת ההרצה), עמדת הניהול (כיתות, תלמידים, הגשות),
// מחיקות, שחרור עמדה עד 5 בחודש, הגשות של עמדה שפג תוקפן, ובידוד בין מורים.
//   npm run test:classroom     (מפעיל את האמולטורים ומריץ את הקובץ הזה; צריך Java 11+)
import assert from 'node:assert/strict';
import { generateKeyPairSync, createHash, randomBytes } from 'node:crypto';
import worker from '../src/index.js';
import { _test as accessTest } from '../src/access/index.js';
import { purgeExpired } from '../src/access/classroom.js';

const FS = process.env.FIRESTORE_EMULATOR_HOST || '127.0.0.1:8181';
const AUTH = process.env.FIREBASE_AUTH_EMULATOR_HOST || '127.0.0.1:9099';
const PROJECT = 'demo-dsviz';
const { privateKey } = generateKeyPairSync('rsa', { modulusLength: 2048 });
const ENV = {
  ACCESS: 'on', FIREBASE_PROJECT_ID: PROJECT, FIRESTORE_EMULATOR_HOST: FS, FIREBASE_AUTH_EMULATOR_HOST: AUTH,
  ADMIN_EMAILS: 'admin@test.dev', PASS_KEY: privateKey.export({ type: 'pkcs8', format: 'pem' }),
};
const realFetch = globalThis.fetch;

// כל פנייה של השרת החוצה (Firestore, Auth) נספרת: ב-Cloudflare החינמי מותרות עד 50 בבקשה אחת.
// failIndex: מדמה את Firestore האמיתי כשחסר אינדקס מורכב (האמולטור אף פעם לא דוחה שאילתה בגלל אינדקס)
const sub = { n: 0, max: 0, route: '' };
let failIndex = false;
globalThis.fetch = async (input, init) => {
  sub.n++;
  const url = typeof input === 'string' ? input : input.url;
  if (failIndex && /:runQuery$/.test(url) && JSON.parse(init.body).structuredQuery.where && JSON.parse(init.body).structuredQuery.where.compositeFilter) {
    return new Response(JSON.stringify({ error: { code: 400, status: 'FAILED_PRECONDITION', message: 'The query requires an index. You can create it here: https://console.firebase.google.com/v1/r/project/demo-dsviz/firestore/indexes?create_composite=abc' } }),
      { status: 400, headers: { 'content-type': 'application/json' } });
  }
  return realFetch(input, init);
};

const BASE = 'https://w.example';
async function call(method, path, { body, token, headers = {} } = {}) {
  const h = Object.assign({}, headers);
  if (body !== undefined) h['content-type'] = 'application/json';
  if (token) h.authorization = 'Bearer ' + token;
  const before = sub.n;
  const res = await worker.fetch(new Request(BASE + path, { method, headers: h, body: body === undefined ? undefined : JSON.stringify(body) }), ENV, {});
  const used = sub.n - before;
  if (used > sub.max) { sub.max = used; sub.route = method + ' ' + path.split('?')[0]; }
  const type = res.headers.get('content-type') || '';
  return { status: res.status, used, body: type.includes('json') ? await res.json() : await res.text() };
}
async function idToken(email, name = '') {
  const claims = { sub: 'uid-' + createHash('sha256').update(email).digest('hex').slice(0, 16), email, email_verified: true, name };
  const res = await realFetch(`http://${AUTH}/identitytoolkit.googleapis.com/v1/accounts:signInWithIdp?key=fake-api-key`, {
    method: 'POST', headers: { 'content-type': 'application/json' },
    body: JSON.stringify({ postBody: 'id_token=' + encodeURIComponent(JSON.stringify(claims)) + '&providerId=google.com', requestUri: 'http://localhost', returnSecureToken: true }),
  });
  const j = await res.json();
  assert.ok(j.idToken, 'auth emulator: ' + JSON.stringify(j).slice(0, 200));
  return j.idToken;
}
const machine = () => randomBytes(16).toString('hex');
// ברירת המחדל: הגרסה עם מרכז הבקרה (caps: classroom). לקוח רגיל (1.0.2 ומטה) — caps: []
async function signInPlugin(email, role, name, caps = ['classroom']) {
  const verifier = randomBytes(32).toString('base64url');
  const challenge = createHash('sha256').update(verifier).digest('base64url');
  const ex = await call('POST', '/v1/auth/exchange', { body: { idToken: await idToken(email, name || email.split('@')[0]), role, challenge } });
  assert.equal(ex.status, 200, JSON.stringify(ex.body));
  const m = machine();
  const r = await call('POST', '/v1/auth/redeem', { body: { code: ex.body.code, verifier, machine: m, machineName: 'מחשב בבית', client: 'test', caps } });
  assert.equal(r.status, 200, JSON.stringify(r.body));
  return Object.assign(r.body, { machine: m });
}
const plugin = tok => ({ 'x-dsviz-access': tok.token, 'x-dsviz-machine': tok.machine });

let passed = 0;
async function test(name, fn) {
  try { await fn(); passed++; console.log('✓ ' + name); } catch (e) { console.error('✗ ' + name); console.error(e); process.exitCode = 1; }
}

await realFetch(`http://${FS}/emulator/v1/projects/${PROJECT}/databases/(default)/documents`, { method: 'DELETE' });
accessTest.reset();
const admin = await idToken('admin@test.dev', 'מנהל');

// שני מורים: שרה (עמדות + תלמידים) ודוד (לבידוד)
const codes = {};
for (const [email, name, seats] of [['sara@test.dev', 'שרה כהן', 8], ['david@test.dev', 'דוד לוי', 2]]) {
  const r = await call('POST', '/v1/admin/teacher', { token: admin, body: { create: true, email, name, school: 'תיכון לדוגמה', seats } });
  assert.equal(r.status, 200, JSON.stringify(r.body));
  codes[email] = (await call('GET', '/v1/admin/teacher?email=' + email, { token: admin })).body.teacher.code;
}
const sara = await idToken('sara@test.dev', 'שרה כהן');
const david = await idToken('david@test.dev', 'דוד לוי');

let noam, ariel;
await test('תלמיד חדש: המאגר פתוח בחינם (studentsFree), ועוד לא בחר — mode ריק', async () => {
  noam = await signInPlugin('noam@test.dev', 'student', 'נועם לוי');
  assert.equal(noam.status, 'ok', JSON.stringify(noam));
  assert.ok(noam.pass, 'אישור חתום');
  assert.deepEqual(noam.student, { mode: '', teacher: null, registered: false, fullName: '' });
});

await test('תלמיד עצמאי: הבחירה נשמרת, ו"הגשה למורה" לא אפשרית', async () => {
  const r = await call('POST', '/v1/student/mode', { headers: plugin(noam), body: { mode: 'independent' } });
  assert.equal(r.body.mode, 'independent');
  const chk = await call('POST', '/v1/access/check', { body: { token: noam.token, machine: noam.machine } });
  assert.equal(chk.body.student.mode, 'independent');
  const s = await call('POST', '/v1/submit', { headers: plugin(noam), body: { exercise: { source: 'bank', bankId: 'queue-sum-even', title: 'סכום תור' }, files: [{ path: 'Q.java', text: 'class Q {}' }] } });
  assert.equal(s.status, 403);
  assert.equal(s.body.code, 'no-teacher');
});

await test('הצטרפות בקוד מורה: קוד שגוי → bad-code; קוד נכון (גם באותיות קטנות) → מופיע אצל המורה ב"בלי כיתה"', async () => {
  assert.equal((await call('POST', '/v1/student/join', { headers: plugin(noam), body: { code: 'ZZZZ' } })).body.status, 'bad-code');
  const r = await call('POST', '/v1/student/join', { headers: plugin(noam), body: { code: codes['sara@test.dev'].toLowerCase() } });
  assert.equal(r.body.status, 'ok', JSON.stringify(r.body));
  assert.equal(r.body.teacher.name, 'שרה כהן');
  const again = await call('POST', '/v1/student/join', { headers: plugin(noam), body: { code: codes['sara@test.dev'] } });
  assert.equal(again.body.already, true);
  const c = await call('GET', '/v1/me/console', { token: sara });
  assert.equal(c.status, 200, JSON.stringify(c.body));
  const s = c.body.students.find(x => x.email === 'noam@test.dev');
  assert.ok(s, JSON.stringify(c.body.students));
  assert.equal(s.classId, '');
  assert.equal(s.name, 'נועם לוי');
  const chk = await call('POST', '/v1/access/check', { body: { token: noam.token, machine: noam.machine } });
  assert.equal(chk.body.student.mode, 'teacher');
  assert.equal(chk.body.student.teacher.name, 'שרה כהן');
});

await test('הצטרפות מהדף בדפדפן (Google) עובדת גם בלי התוסף', async () => {
  ariel = await signInPlugin('ariel@test.dev', 'student', 'אריאל כהן');
  const r = await call('POST', '/v1/student/join', { token: await idToken('ariel@test.dev', 'אריאל כהן'), body: { code: codes['sara@test.dev'] } });
  assert.equal(r.body.status, 'ok', JSON.stringify(r.body));
  const st = await call('GET', '/v1/student/state', { token: await idToken('ariel@test.dev', 'אריאל כהן') });
  assert.equal(st.body.teacher.name, 'שרה כהן');
});

const bankSubmit = (over = {}) => Object.assign({
  exercise: { source: 'bank', bankId: 'queue-sum-even', topic: 'queue', title: 'סכום תור וזוגיים', level: 'קל' }, app: 'Eclipse',
  files: [{ path: 'src/QueueTargil1.java', text: 'public class QueueTargil1 {\n\tint x;\n}\n' }], main: 'src/QueueTargil1.java',
  check: { ran: true, compiles: true, output: 'סעיף א: 18\nהתור אחרי: []', expected: 'סעיף א: 18\nהתור אחרי: [3,8,1,6]', outputMatch: false },
}, over);

await test('הגשה מתלמיד: נשמרת אצל המורה עם תוצאת בדיקת ההרצה; הגשה חוזרת של אותו תרגיל מחליפה (n=2)', async () => {
  const r = await call('POST', '/v1/submit', { headers: plugin(noam), body: bankSubmit() });
  assert.equal(r.status, 200, JSON.stringify(r.body));
  assert.equal(r.body.replaced, false);
  const r2 = await call('POST', '/v1/submit', { headers: plugin(noam), body: bankSubmit({ check: { ran: true, compiles: true, output: 'סעיף א: 18\nהתור אחרי: [3,8,1,6]', expected: 'סעיף א: 18\nהתור אחרי: [3,8,1,6]', outputMatch: true } }) });
  assert.equal(r2.body.replaced, true);
  assert.equal(r2.body.id, r.body.id);
  const list = await call('GET', '/v1/me/submissions?student=noam@test.dev', { token: sara });
  assert.equal(list.body.submissions.length, 1);
  const x = list.body.submissions[0];
  assert.equal(x.n, 2);
  assert.equal(x.check.outputMatch, true);
  assert.equal(x.seen, false);
  assert.equal(x.expiresAt, '', 'הגשה של תלמיד לא נמחקת לבד');
  const one = await call('GET', '/v1/me/submission?id=' + x.id, { token: sara });
  assert.equal(one.body.files[0].text, 'public class QueueTargil1 {\n\tint x;\n}\n', 'הקוד נשמר כמו שהוא (טאבים, ירידות שורה)');
  assert.equal(one.body.main, 'src/QueueTargil1.java');
  const after = await call('GET', '/v1/me/submissions?student=noam@test.dev', { token: sara });
  assert.equal(after.body.submissions[0].seen, true, 'נפתח — מסומן');
  const c = await call('GET', '/v1/me/console', { token: sara });
  const s = c.body.students.find(y => y.email === 'noam@test.dev');
  assert.equal(s.submits, 1);
  assert.equal(s.lastTitle, 'סכום תור וזוגיים');
});

await test('התקציר של התלמיד אצל המורה: שורה לכל תרגיל עם התוצאה; "חדש" עד שהמורה פותח; הגשה חוזרת — שוב חדש', async () => {
  let s = (await call('GET', '/v1/me/console', { token: sara })).body.students.find(y => y.email === 'noam@test.dev');
  assert.equal(s.work.length, 1, JSON.stringify(s.work));
  assert.deepEqual({ title: s.work[0].title, bankId: s.work[0].bankId, topic: s.work[0].topic, result: s.work[0].result, n: s.work[0].n, seen: s.work[0].seen },
    { title: 'סכום תור וזוגיים', bankId: 'queue-sum-even', topic: 'queue', result: 'ok', n: 2, seen: true });
  assert.equal(s.unseen, 0, 'המורה כבר פתח את ההגשה');
  // תרגיל שני (לא מתקמפל) ותרגיל שלישי (נעצר אחרי 10 שניות) — שתי שורות חדשות
  await call('POST', '/v1/submit', { headers: plugin(noam), body: bankSubmit({ exercise: { source: 'bank', bankId: 'stack-size', topic: 'stack', title: 'גודל מחסנית', level: 'קל' }, check: { ran: true, compiles: false, error: 'חסר ;' } }) });
  await call('POST', '/v1/submit', { headers: plugin(noam), body: bankSubmit({ exercise: { source: 'own', title: 'תרגיל שלי' }, check: { ran: true, compiles: true, timedOut: true, output: '' } }) });
  s = (await call('GET', '/v1/me/console', { token: sara })).body.students.find(y => y.email === 'noam@test.dev');
  assert.equal(s.work.length, 3);
  assert.equal(s.unseen, 2);
  assert.deepEqual(s.work.map(x => x.result).sort(), ['err', 'ok', 'time']);
  assert.equal(s.submits, 3);
  // הגשה חוזרת של התרגיל שכבר נפתח — שוב "חדש", ועדיין שורה אחת לתרגיל
  await call('POST', '/v1/submit', { headers: plugin(noam), body: bankSubmit() });
  s = (await call('GET', '/v1/me/console', { token: sara })).body.students.find(y => y.email === 'noam@test.dev');
  assert.equal(s.work.length, 3);
  assert.equal(s.unseen, 3);
  const row = s.work.find(x => x.bankId === 'queue-sum-even');
  assert.deepEqual({ n: row.n, seen: row.seen, result: row.result }, { n: 3, seen: false, result: 'diff' });
  assert.equal(s.work[0].id, row.id, 'מהחדש לישן');
  // המורה פותח אחת — היא יורדת מהספירה, והשאר נשארות
  assert.equal((await call('GET', '/v1/me/submission?id=' + row.id, { token: sara })).status, 200);
  s = (await call('GET', '/v1/me/console', { token: sara })).body.students.find(y => y.email === 'noam@test.dev');
  assert.equal(s.unseen, 2);
  assert.equal(s.work.find(x => x.id === row.id).seen, true);
  assert.equal(s.work.length, 3, 'סימון "נצפה" לא מוחק שורות אחרות מהתקציר');
});

await test('ציון להגשה של תלמיד: 0–100, נשמר בהגשה ובתקציר; המורה מעדכן ומסיר בכל זמן; הגשה חוזרת — הציון נשאר רשום', async () => {
  const sub = (await call('GET', '/v1/me/submissions?student=noam@test.dev', { token: sara })).body.submissions.find(x => x.exercise.bankId === 'stack-size');
  assert.equal(sub.grade, null, 'עוד בלי ציון');
  assert.equal(sub.seen, false);
  const g = await call('POST', '/v1/me/submission/grade', { token: sara, body: { id: sub.id, grade: 85 } });
  assert.equal(g.status, 200, JSON.stringify(g.body));
  assert.deepEqual({ grade: g.body.grade, gradedN: g.body.gradedN }, { grade: 85, gradedN: 1 });
  let list = (await call('GET', '/v1/me/submissions?student=noam@test.dev', { token: sara })).body.submissions;
  let x = list.find(y => y.id === sub.id);
  assert.equal(x.grade, 85);
  assert.ok(x.gradedAt);
  assert.equal(x.seen, true, 'מי שנתן ציון ראה את ההגשה');
  let s = (await call('GET', '/v1/me/console', { token: sara })).body.students.find(y => y.email === 'noam@test.dev');
  assert.equal(s.work.find(w => w.id === sub.id).grade, 85, 'גם בתקציר (הרשימות וטבלת ההגשות)');
  assert.equal(s.work.filter(w => w.grade !== null).length, 1, 'שאר התרגילים בלי ציון');
  assert.equal(s.work.length, 3, 'הציון לא מוחק שורות אחרות מהתקציר');
  // עריכה: המורה תמיד יכול לשנות (גם כמחרוזת מהטופס)
  assert.equal((await call('POST', '/v1/me/submission/grade', { token: sara, body: { id: sub.id, grade: '92' } })).body.grade, 92);
  s = (await call('GET', '/v1/me/console', { token: sara })).body.students.find(y => y.email === 'noam@test.dev');
  assert.equal(s.work.find(w => w.id === sub.id).grade, 92);
  // ציון לא תקין — נדחה, והקודם נשאר
  for (const bad of [101, -1, 85.5, 'תשעים', '9 0']) {
    const r = await call('POST', '/v1/me/submission/grade', { token: sara, body: { id: sub.id, grade: bad } });
    assert.equal(r.status, 400, String(bad));
    assert.equal(r.body.code, 'grade');
  }
  assert.equal((await call('GET', '/v1/me/submission?id=' + sub.id, { token: sara })).body.submission.grade, 92);
  // 0 ו-100 הם ציונים תקינים (0 הוא ציון, לא "בלי ציון")
  assert.equal((await call('POST', '/v1/me/submission/grade', { token: sara, body: { id: sub.id, grade: 0 } })).body.grade, 0);
  assert.equal((await call('GET', '/v1/me/submission?id=' + sub.id, { token: sara })).body.submission.grade, 0);
  assert.equal((await call('POST', '/v1/me/submission/grade', { token: sara, body: { id: sub.id, grade: 100 } })).body.grade, 100);
  // התלמיד מגיש שוב את אותו תרגיל: הציון נשאר רשום, וההגשה מסומנת "הוגש שוב מאז הציון" (n > gradedN) ושוב חדשה
  await call('POST', '/v1/submit', { headers: plugin(noam), body: bankSubmit({ exercise: { source: 'bank', bankId: 'stack-size', topic: 'stack', title: 'גודל מחסנית', level: 'קל' }, check: { ran: true, compiles: true, output: '4', expected: '4', outputMatch: true } }) });
  x = (await call('GET', '/v1/me/submissions?student=noam@test.dev', { token: sara })).body.submissions.find(y => y.id === sub.id);
  assert.deepEqual({ grade: x.grade, n: x.n, gradedN: x.gradedN, seen: x.seen }, { grade: 100, n: 2, gradedN: 1, seen: false });
  s = (await call('GET', '/v1/me/console', { token: sara })).body.students.find(y => y.email === 'noam@test.dev');
  const w = s.work.find(y => y.id === sub.id);
  assert.deepEqual({ grade: w.grade, n: w.n, gradedN: w.gradedN, seen: w.seen, result: w.result }, { grade: 100, n: 2, gradedN: 1, seen: false, result: 'ok' });
  // המורה מעדכן אחרי ההגשה החדשה — הציון שייך עכשיו להגשה האחרונה
  const g2 = await call('POST', '/v1/me/submission/grade', { token: sara, body: { id: sub.id, grade: 95 } });
  assert.deepEqual({ grade: g2.body.grade, gradedN: g2.body.gradedN }, { grade: 95, gradedN: 2 });
  // הסרת הציון
  assert.equal((await call('POST', '/v1/me/submission/grade', { token: sara, body: { id: sub.id, grade: null } })).body.grade, null);
  s = (await call('GET', '/v1/me/console', { token: sara })).body.students.find(y => y.email === 'noam@test.dev');
  assert.equal(s.work.find(y => y.id === sub.id).grade, null);
  assert.equal((await call('POST', '/v1/me/submission/grade', { token: sara, body: { id: sub.id, grade: 88 } })).body.grade, 88);
});

await test('מורה מהתוסף (הכרטיס של המחשב): כמה הגשות חדשות מחכות — תלמידים ומחשבי המעבדה; תלמיד / עמדה / בלי כרטיס — נדחים', async () => {
  const t = await signInPlugin('sara@test.dev', 'teacher', 'שרה כהן');
  assert.equal(t.status, 'ok', JSON.stringify(t));
  assert.ok(t.features.includes('summary') && t.features.includes('submit'), JSON.stringify(t.features));
  const c = (await call('GET', '/v1/me/console', { token: sara })).body;
  const r = await call('POST', '/v1/teacher/summary', { headers: plugin(t), body: {} });
  assert.equal(r.status, 200, JSON.stringify(r.body));
  assert.equal(r.body.newStudents, c.students.reduce((n, x) => n + x.unseen, 0));
  assert.ok(r.body.newStudents >= 1, 'יש לפחות הגשה חדשה אחת של תלמיד');
  assert.equal(r.body.newStations, c.stations.reduce((n, x) => n + x.unseen, 0));
  assert.equal(r.body.students, c.students.filter(x => !x.left).length);
  assert.equal((await call('POST', '/v1/teacher/summary', { headers: plugin(noam), body: {} })).status, 403);
  assert.equal((await call('POST', '/v1/teacher/summary', { body: {} })).status, 401);
});

await test('הגשה: קבצים שאינם קוד / גדולים מדי / יותר מ-12 — נדחים; מורה לא מגיש', async () => {
  assert.equal((await call('POST', '/v1/submit', { headers: plugin(noam), body: bankSubmit({ files: [{ path: 'a.exe', text: 'x' }] }) })).status, 400);
  assert.equal((await call('POST', '/v1/submit', { headers: plugin(noam), body: bankSubmit({ files: [{ path: 'A.java', text: 'x'.repeat(210 * 1024) }] }) })).status, 400);
  assert.equal((await call('POST', '/v1/submit', { headers: plugin(noam), body: bankSubmit({ files: Array.from({ length: 13 }, (_, i) => ({ path: `A${i}.java`, text: 'x' })) }) })).status, 400);
  const t = await signInPlugin('sara@test.dev', 'teacher', 'שרה כהן');
  assert.equal((await call('POST', '/v1/submit', { headers: plugin(t), body: bankSubmit() })).status, 403);
  assert.equal((await call('POST', '/v1/submit', { body: bankSubmit() })).status, 401);
});

let station, stationId;
await test('הגשה מעמדת מחשב: "שם המגיש" חובה; נשמרת רק לאותו יום (עד חצות, שעון ישראל); שני תלמידים באותה עמדה — שתי הגשות', async () => {
  const m = machine();
  const cl = await call('POST', '/v1/station/claim', { body: { code: codes['sara@test.dev'], name: 'עמדה 2', machine: m, client: 'test' } });
  assert.equal(cl.body.status, 'ok', JSON.stringify(cl.body));
  station = { token: cl.body.token, machine: m };
  stationId = cl.body.token.split('.')[1];
  const noName = await call('POST', '/v1/submit', { headers: plugin(station), body: bankSubmit() });
  assert.equal(noName.status, 400);
  assert.equal(noName.body.code, 'who');
  assert.equal((await call('POST', '/v1/submit', { headers: plugin(station), body: bankSubmit({ who: ' א ' }) })).body.code, 'who', 'אות אחת — לא מספיק');
  const a = await call('POST', '/v1/submit', { headers: plugin(station), body: bankSubmit({ who: 'עידו שפירא' }) });
  assert.equal(a.status, 200, JSON.stringify(a.body));
  const b = await call('POST', '/v1/submit', { headers: plugin(station), body: bankSubmit({ who: 'הלל ברק', check: { ran: true, compiles: false, error: 'QueueTargil1.java, שורה 3: חסר ;' } }) });
  assert.notEqual(a.body.id, b.body.id);
  const c = await call('GET', '/v1/me/console', { token: sara });
  const st = c.body.stations.find(x => x.id === stationId);
  assert.equal(st.pending, 2);
  assert.equal(st.unseen, 2);
  const list = await call('GET', '/v1/me/submissions?station=' + stationId, { token: sara });
  assert.deepEqual(list.body.submissions.map(x => x.who).sort(), ['הלל ברק', 'עידו שפירא']);
  const exp = Date.parse(list.body.submissions[0].expiresAt);
  const hm = new Intl.DateTimeFormat('en-GB', { timeZone: 'Asia/Jerusalem', hour: '2-digit', minute: '2-digit', hourCycle: 'h23' }).format(new Date(exp));
  assert.equal(hm, '00:00', 'התפוגה בחצות שעון ישראל: ' + new Date(exp).toISOString());
  assert.ok(exp > Date.now() && exp - Date.now() <= 86400000, 'עד סוף היום הזה: ' + new Date(exp).toISOString());
  assert.equal(list.body.submissions.find(x => x.who === 'הלל ברק').check.compiles, false);
});

await test('התלמיד רואה את הציונים שלו — מהתוסף ומהדף; רק את שלו; "הגשת שוב אחרי הציון"', async () => {
  const viaPlugin = await call('POST', '/v1/student/work', { headers: plugin(noam), body: {} });
  assert.equal(viaPlugin.status, 200, JSON.stringify(viaPlugin.body));
  assert.equal(viaPlugin.body.teacher.name, 'שרה כהן');
  const row = viaPlugin.body.work.find(w => w.bankId === 'stack-size');
  assert.deepEqual({ grade: row.grade, again: row.again }, { grade: 88, again: false });
  assert.ok(row.gradedAt);
  assert.ok(!('seen' in row), 'בלי מה שפנימי למורה');
  assert.ok(viaPlugin.body.work.some(w => w.grade === null), 'תרגיל בלי ציון — null');
  const noamWeb = await idToken('noam@test.dev', 'נועם');
  assert.deepEqual((await call('GET', '/v1/student/work', { token: noamWeb })).body.work, viaPlugin.body.work);
  const me = await call('GET', '/v1/me', { token: noamWeb });
  assert.equal(me.body.classroom, true, 'משתמש בגרסה עם מרכז הבקרה');
  assert.equal(me.body.student.state.work.find(w => w.bankId === 'stack-size').grade, 88, 'הדף "החשבון שלי" — אותו ציון');
  await call('POST', '/v1/submit', { headers: plugin(noam), body: bankSubmit({ exercise: { source: 'bank', bankId: 'stack-size', topic: 'stack', title: 'גודל מחסנית', level: 'קל' } }) });
  const again = (await call('POST', '/v1/student/work', { headers: plugin(noam), body: {} })).body.work.find(w => w.bankId === 'stack-size');
  assert.deepEqual({ grade: again.grade, again: again.again, n: again.n }, { grade: 88, again: true, n: 3 });
  const other = (await call('POST', '/v1/student/work', { headers: plugin(ariel), body: {} })).body;
  assert.ok(!other.work.some(w => w.bankId === 'stack-size'), 'תלמיד אחר לא רואה את הציונים של נועם');
  const t = await signInPlugin('sara@test.dev', 'teacher', 'שרה כהן');
  assert.equal((await call('POST', '/v1/student/work', { headers: plugin(t), body: {} })).status, 403);
  assert.equal((await call('POST', '/v1/student/work', { body: {} })).status, 401);
});

await test('ציון: רק להגשה של תלמיד (לא מעמדת מחשב), רק המורה של ההגשה, ורק מורה', async () => {
  const st = (await call('GET', '/v1/me/submissions?station=' + stationId, { token: sara })).body.submissions[0];
  const r = await call('POST', '/v1/me/submission/grade', { token: sara, body: { id: st.id, grade: 90 } });
  assert.equal(r.status, 400);
  assert.equal(r.body.code, 'station');
  assert.equal((await call('GET', '/v1/me/submissions?station=' + stationId, { token: sara })).body.submissions.find(x => x.id === st.id).grade, null);
  const sub = (await call('GET', '/v1/me/submissions?student=noam@test.dev', { token: sara })).body.submissions[0];
  assert.equal((await call('POST', '/v1/me/submission/grade', { token: david, body: { id: sub.id, grade: 10 } })).status, 404, 'מורה אחר');
  assert.equal((await call('POST', '/v1/me/submission/grade', { token: await idToken('noam@test.dev', 'נועם'), body: { id: sub.id, grade: 100 } })).status, 403, 'תלמיד לא נותן ציון לעצמו');
  assert.equal((await call('POST', '/v1/me/submission/grade', { body: { id: sub.id, grade: 100 } })).status, 401);
  assert.equal((await call('POST', '/v1/me/submission/grade', { token: sara, body: { id: 'no-such', grade: 100 } })).status, 404);
});

await test('הגשה מעמדה שפג תוקפה — לא מופיעה ונמחקת (גם בלי TTL)', async () => {
  // מקצרים את התפוגה ישירות באמולטור (כמו שעבר זמן)
  const list = await call('GET', '/v1/me/submissions?station=' + stationId, { token: sara });
  const id = list.body.submissions.find(x => x.who === 'הלל ברק').id;
  const url = `http://${FS}/v1/projects/${PROJECT}/databases/(default)/documents/submissions/${id}?updateMask.fieldPaths=expiresAt`;
  const res = await realFetch(url, { method: 'PATCH', headers: { 'content-type': 'application/json', authorization: 'Bearer owner' }, body: JSON.stringify({ fields: { expiresAt: { timestampValue: new Date(Date.now() - 1000).toISOString() } } }) });
  assert.equal(res.status, 200, await res.text());
  const c = await call('GET', '/v1/me/console', { token: sara });
  assert.equal(c.body.stations.find(x => x.id === stationId).pending, 1);
  assert.equal((await call('GET', '/v1/me/submission?id=' + id, { token: sara })).status, 404);
});

await test('המורה מוחק הגשה של עמדה; הגשה של תלמיד לא נמחקת כך (רק עם התלמיד)', async () => {
  const list = await call('GET', '/v1/me/submissions?station=' + stationId, { token: sara });
  const d = await call('POST', '/v1/me/submission/delete', { token: sara, body: { id: list.body.submissions[0].id } });
  assert.equal(d.body.deleted, 1);
  assert.equal((await call('GET', '/v1/me/submissions?station=' + stationId, { token: sara })).body.submissions.length, 0);
  const sid = (await call('GET', '/v1/me/submissions?student=noam@test.dev', { token: sara })).body.submissions[0].id;
  assert.equal((await call('POST', '/v1/me/submission/delete', { token: sara, body: { id: sid } })).status, 400);
  // "מחיקת כל ההגשות" של עמדה
  await call('POST', '/v1/submit', { headers: plugin(station), body: bankSubmit({ who: 'תלמיד א' }) });
  await call('POST', '/v1/submit', { headers: plugin(station), body: bankSubmit({ who: 'תלמיד ב' }) });
  assert.equal((await call('POST', '/v1/me/submission/delete', { token: sara, body: { station: stationId, all: true } })).body.deleted, 2);
});

let classA, classB;
await test('כיתות: יצירה, שם כפול נדחה, שינוי שם, העברת תלמיד לכיתה', async () => {
  classA = (await call('POST', '/v1/me/class', { token: sara, body: { op: 'create', name: 'יא 3' } })).body.id;
  classB = (await call('POST', '/v1/me/class', { token: sara, body: { op: 'create', name: 'יב 1' } })).body.id;
  assert.ok(classA && classB);
  assert.equal((await call('POST', '/v1/me/class', { token: sara, body: { op: 'create', name: 'יא 3' } })).status, 400);
  assert.equal((await call('POST', '/v1/me/class', { token: sara, body: { op: 'rename', id: classB, name: 'יב 2' } })).status, 200);
  assert.equal((await call('POST', '/v1/me/member', { token: sara, body: { op: 'move', student: 'noam@test.dev', classId: classA } })).status, 200);
  assert.equal((await call('POST', '/v1/me/member', { token: sara, body: { op: 'move', student: 'ariel@test.dev', classId: classB } })).status, 200);
  const c = await call('GET', '/v1/me/console', { token: sara });
  assert.deepEqual(c.body.classes.map(x => x.name), ['יא 3', 'יב 2']);
  assert.equal(c.body.students.find(x => x.email === 'noam@test.dev').classId, classA);
});

// ------------------------------------------------------------------ שיעורי בית
const FSDOCS = `http://${FS}/v1/projects/${PROJECT}/databases/(default)/documents`;
const rawDoc = async path => (await (await realFetch(`${FSDOCS}/${path}`, { headers: { authorization: 'Bearer owner' } })).json());
const hwSave = (token, body) => call('POST', '/v1/me/homework', { token, body: Object.assign({ op: 'save' }, body) });
let hwA, hwAll;
await test('שיעורי בית: המורה נותן לכיתה / לכל התלמידים — נשמרים רק המזהים; שאלה שלא במאגר, יותר מ-10, תאריך לא תקין, כיתה של מורה אחר — נדחים', async () => {
  assert.equal((await hwSave(sara, { items: [] })).status, 400);
  const unknown = await hwSave(sara, { items: ['queue-sum-even', 'no-such-question'] });
  assert.equal(unknown.status, 400);
  assert.match(unknown.body.error, /לא נמצאה במאגר: no-such-question/);
  const eleven = ['queue-sum-even', 'stack-sum-size', 'tree-count-sum', 'list-evens-remove', 'queue-two-sum', 'stack-brackets', 'tree-path-sum', 'arrays-counters', 'loops-collatz', 'strings-anagram', 'recursion-digits'];
  assert.equal((await hwSave(sara, { items: eleven })).body.code, 'items', 'עד 10 שאלות');
  assert.equal((await hwSave(sara, { items: ['queue-sum-even'], due: '2026-02-30' })).body.code, 'due');
  assert.equal((await hwSave(sara, { items: ['queue-sum-even'], due: '3.12' })).body.code, 'due');
  assert.equal((await hwSave(david, { items: ['queue-sum-even'], classId: classA })).status, 404, 'כיתה של מורה אחר');
  assert.equal((await call('POST', '/v1/me/homework', { token: await idToken('noam@test.dev'), body: { op: 'save', items: ['queue-sum-even'] } })).status, 403, 'תלמיד');
  const a = await hwSave(sara, { classId: classA, items: ['queue-sum-even', 'stack-sum-size', 'queue-sum-even'], title: '  חזרה   על תור ', due: '2026-12-03' });
  assert.equal(a.status, 200, JSON.stringify(a.body));
  hwA = a.body.id;
  const all = await hwSave(sara, { items: ['tree-count-sum'] });
  assert.equal(all.status, 200, JSON.stringify(all.body));
  hwAll = all.body.id;
  // במסמך: רק המזהים (בלי שמות / נוסח / מה הוגש)
  const doc = await rawDoc('homework/' + hwA);
  assert.deepEqual(Object.keys(doc.fields).sort(), ['classId', 'createdAt', 'due', 'items', 'teacher', 'title', 'updatedAt']);
  assert.deepEqual(doc.fields.items.arrayValue.values.map(v => v.stringValue), ['queue-sum-even', 'stack-sum-size'], 'בלי כפילות');
  const c = await call('GET', '/v1/me/console', { token: sara });
  assert.deepEqual(c.body.homework.map(h => h.id), [hwAll, hwA], 'מהחדש לישן');
  const h = c.body.homework.find(x => x.id === hwA);
  assert.deepEqual({ title: h.title, classId: h.classId, items: h.items, due: h.due }, { title: 'חזרה על תור', classId: classA, items: ['queue-sum-even', 'stack-sum-size'], due: '2026-12-03' });
  assert.ok(h.createdAt);
  assert.deepEqual(Object.keys(h).sort(), ['classId', 'createdAt', 'due', 'id', 'items', 'title']);
  assert.equal(c.body.homework.find(x => x.id === hwAll).classId, '');
  assert.equal((await call('GET', '/v1/me/console', { token: david })).body.homework.length, 0, 'בידוד: מורה אחר לא רואה');
});

await test('שיעורי בית אצל התלמיד (/v1/student/work): של המורה שלו, לכולם או לכיתה שלו; שאלה שהוגשה (גם לפני), מתי והציון; השדות הקודמים בלי שינוי', async () => {
  const sub = (await call('GET', '/v1/me/submissions?student=noam@test.dev', { token: sara })).body.submissions.find(x => x.exercise.bankId === 'queue-sum-even');
  await call('POST', '/v1/me/submission/grade', { token: sara, body: { id: sub.id, grade: 92 } });
  const r = await call('POST', '/v1/student/work', { headers: plugin(noam), body: {} });
  assert.equal(r.status, 200, JSON.stringify(r.body));
  assert.ok(Array.isArray(r.body.work) && r.body.teacher.name === 'שרה כהן', 'השדות הקודמים נשארו');
  assert.deepEqual(r.body.homework.map(x => x.id), [hwAll, hwA], 'כיתה יא 3 + לכל התלמידים, מהחדש לישן');
  const a = r.body.homework[1];
  assert.equal(a.title, 'חזרה על תור');
  assert.equal(a.due, '2026-12-03');
  assert.deepEqual(a.items.map(x => [x.bankId, x.title, x.topic, x.level, x.done, x.grade]),
    [['queue-sum-even', 'סכום תור וזוגיים', 'תור', 'קל', true, 92], ['stack-sum-size', 'סכום וגודל של מחסנית', 'מחסנית', 'קל', false, null]]);
  assert.ok(a.items[0].at && a.items[1].at === '', 'מתי הוגש');
  assert.match(r.body.homework[0].title, /^שיעורי בית \d{1,2}\.\d{1,2}$/, 'בלי כותרת — "שיעורי בית <d.M>"');
  assert.equal(r.body.homework[0].due, '');
  // כיתה אחרת: רק מה שלכל התלמידים
  const other = (await call('POST', '/v1/student/work', { headers: plugin(ariel), body: {} })).body;
  assert.deepEqual(other.homework.map(x => x.id), [hwAll]);
  // התלמיד מגיש — מסומן "הוגש" בתשובה הבאה
  await call('POST', '/v1/submit', { headers: plugin(noam), body: bankSubmit({ exercise: { source: 'bank', bankId: 'stack-sum-size', topic: 'מחסנית', title: 'סכום וגודל של מחסנית', level: 'קל' } }) });
  const after = (await call('POST', '/v1/student/work', { headers: plugin(noam), body: {} })).body.homework.find(x => x.id === hwA);
  assert.deepEqual(after.items.map(x => x.done), [true, true]);
  // מהדף בדפדפן — אותה תשובה
  assert.deepEqual((await call('GET', '/v1/student/work', { token: await idToken('noam@test.dev') })).body.homework.map(x => x.id), [hwAll, hwA]);
  // תלמיד עצמאי — כלום; עמדת מחשב — לא תלמיד (403, כמו קודם)
  const solo = await signInPlugin('solo.hw@test.dev', 'student', 'עצמאי');
  await call('POST', '/v1/student/mode', { headers: plugin(solo), body: { mode: 'independent' } });
  assert.deepEqual((await call('POST', '/v1/student/work', { headers: plugin(solo), body: {} })).body.homework, []);
  assert.equal((await call('POST', '/v1/student/work', { headers: plugin(station), body: {} })).status, 403);
});

await test('שיעורי בית: עריכה ומחיקה — רק המורה שנתן; המחיקה לא נוגעת בהגשות ובציונים; עד 60 בכל פעם', async () => {
  assert.equal((await hwSave(david, { id: hwA, items: ['tree-count-sum'] })).status, 404, 'מורה אחר לא עורך');
  assert.equal((await call('POST', '/v1/me/homework', { token: david, body: { op: 'delete', id: hwA } })).status, 404, 'מורה אחר לא מוחק');
  const e = await hwSave(sara, { id: hwA, classId: classA, items: ['stack-sum-size', 'list-evens-remove'], title: '', due: '' });
  assert.equal(e.status, 200, JSON.stringify(e.body));
  let h = (await call('GET', '/v1/me/console', { token: sara })).body.homework.find(x => x.id === hwA);
  assert.deepEqual({ items: h.items, title: h.title, due: h.due }, { items: ['stack-sum-size', 'list-evens-remove'], title: '', due: '' });
  const before = (await call('GET', '/v1/me/submissions?student=noam@test.dev', { token: sara })).body.submissions.length;
  assert.equal((await call('POST', '/v1/me/homework', { token: sara, body: { op: 'delete', id: hwAll } })).status, 200);
  const c = await call('GET', '/v1/me/console', { token: sara });
  assert.deepEqual(c.body.homework.map(x => x.id), [hwA]);
  assert.equal((await call('GET', '/v1/me/submissions?student=noam@test.dev', { token: sara })).body.submissions.length, before, 'ההגשות נשארו');
  assert.equal(c.body.students.find(x => x.email === 'noam@test.dev').work.find(w => w.bankId === 'queue-sum-even').grade, 92, 'הציון נשאר');
  assert.deepEqual((await call('POST', '/v1/student/work', { headers: plugin(noam), body: {} })).body.homework.map(x => x.id), [hwA]);
  assert.equal((await call('POST', '/v1/me/homework', { token: sara, body: { op: 'delete', id: hwAll } })).status, 404, 'כבר נמחקו');
  // עד 60: 59 נוספים ישירות באמולטור (מהר), השישים ואחד נדחה עם הסבר
  const writes = [];
  for (let i = 0; i < 59; i++) {
    writes.push({ update: { name: `projects/${PROJECT}/databases/(default)/documents/homework/filler${i}`, fields: { teacher: { stringValue: 'sara@test.dev' }, classId: { stringValue: '' }, items: { arrayValue: { values: [{ stringValue: 'tree-count-sum' }] } }, title: { stringValue: '' }, due: { stringValue: '' }, createdAt: { stringValue: '2026-01-01T00:00:00.000Z' } } } });
  }
  const res = await realFetch(FSDOCS + ':commit', { method: 'POST', headers: { 'content-type': 'application/json', authorization: 'Bearer owner' }, body: JSON.stringify({ writes }) });
  assert.equal(res.status, 200, await res.text());
  const over = await hwSave(sara, { items: ['tree-count-sum'] });
  assert.equal(over.status, 400);
  assert.equal(over.body.code, 'limit');
  assert.match(over.body.error, /עד 60 שיעורי בית/);
  const del = writes.map(w => ({ delete: w.update.name }));
  assert.equal((await realFetch(FSDOCS + ':commit', { method: 'POST', headers: { 'content-type': 'application/json', authorization: 'Bearer owner' }, body: JSON.stringify({ writes: del }) })).status, 200);
  assert.equal((await hwSave(sara, { items: ['tree-count-sum'] })).status, 200, 'אחרי מחיקה — אפשר שוב');
});

await test('בידוד: מורה אחר לא רואה, לא פותח ולא מוחק את הנתונים של שרה; תלמיד לא נכנס לעמדת הניהול', async () => {
  const sid = (await call('GET', '/v1/me/submissions?student=noam@test.dev', { token: sara })).body.submissions[0].id;
  assert.equal((await call('GET', '/v1/me/submission?id=' + sid, { token: david })).status, 404);
  assert.equal((await call('GET', '/v1/me/submissions?student=noam@test.dev', { token: david })).body.submissions.length, 0);
  assert.equal((await call('POST', '/v1/me/class', { token: david, body: { op: 'rename', id: classA, name: 'x' } })).status, 404);
  assert.equal((await call('POST', '/v1/me/member', { token: david, body: { op: 'delete', student: 'noam@test.dev' } })).status, 404);
  assert.equal((await call('POST', '/v1/me/station/release', { token: david, body: { id: stationId } })).status, 404);
  const dc = await call('GET', '/v1/me/console', { token: david });
  assert.equal(dc.body.students.length, 0);
  assert.equal(dc.body.classes.length, 0);
  assert.equal((await call('GET', '/v1/me/console', { token: await idToken('noam@test.dev') })).status, 403);
});

await test('שינוי שם של מחשב במעבדה: המורה משנה; השם מגיע למחשב (בבדיקה מול השרת) ונשאר גם כשהמחשב מתחבר שוב עם השם הישן', async () => {
  assert.equal((await call('POST', '/v1/me/station/rename', { token: david, body: { id: stationId, name: 'x' } })).status, 404, 'מורה אחר');
  assert.equal((await call('POST', '/v1/me/station/rename', { token: sara, body: { id: stationId, name: '   ' } })).status, 400, 'שם ריק');
  const r = await call('POST', '/v1/me/station/rename', { token: sara, body: { id: stationId, name: '  מעבדה 3 ·  עמדה 7 ' } });
  assert.equal(r.status, 200, JSON.stringify(r.body));
  assert.equal(r.body.name, 'מעבדה 3 · עמדה 7');
  const c = await call('GET', '/v1/me/console', { token: sara });
  assert.equal(c.body.stations.find(x => x.id === stationId).name, 'מעבדה 3 · עמדה 7');
  const chk = await call('POST', '/v1/access/check', { body: { token: station.token, machine: station.machine } });
  assert.equal(chk.body.station.name, 'מעבדה 3 · עמדה 7');
  assert.equal(chk.body.profile.name, 'מעבדה 3 · עמדה 7');
  const again = await call('POST', '/v1/station/claim', { body: { code: codes['sara@test.dev'], name: 'עמדה 2', machine: station.machine, client: 'test' } });
  assert.equal(again.body.status, 'ok');
  assert.equal(again.body.station.name, 'מעבדה 3 · עמדה 7', 'השם של המורה נשאר');
  station.token = again.body.token;
});

await test('מעבר למורה אחר: צריך אישור (replace); אצל המורה הקודם — "עזב", וההגשות נשארות', async () => {
  const ask = await call('POST', '/v1/student/join', { headers: plugin(ariel), body: { code: codes['david@test.dev'] } });
  assert.equal(ask.body.status, 'replace');
  const ok = await call('POST', '/v1/student/join', { headers: plugin(ariel), body: { code: codes['david@test.dev'], replace: true } });
  assert.equal(ok.body.status, 'ok');
  const c = await call('GET', '/v1/me/console', { token: sara });
  assert.equal(c.body.students.find(x => x.email === 'ariel@test.dev').left, true);
  assert.equal((await call('GET', '/v1/me/console', { token: david })).body.students[0].email, 'ariel@test.dev');
  // חוזר לשרה — אותה כיתה כמו קודם
  await call('POST', '/v1/student/join', { headers: plugin(ariel), body: { code: codes['sara@test.dev'], replace: true } });
  const back = (await call('GET', '/v1/me/console', { token: sara })).body.students.find(x => x.email === 'ariel@test.dev');
  assert.equal(back.left, false);
  assert.equal(back.classId, classB);
});

await test('מחיקת תלמיד: יוצא מהרשימה וכל ההגשות שלו נמחקות; יכול להצטרף שוב מההתחלה (בלי כיתה, בלי עבודות)', async () => {
  const r = await call('POST', '/v1/me/member', { token: sara, body: { op: 'delete', student: 'noam@test.dev' } });
  assert.equal(r.body.done, true);
  const c = await call('GET', '/v1/me/console', { token: sara });
  assert.ok(!c.body.students.some(x => x.email === 'noam@test.dev'));
  assert.equal((await call('GET', '/v1/me/submissions?student=noam@test.dev', { token: sara })).body.submissions.length, 0);
  const chk = await call('POST', '/v1/access/check', { body: { token: noam.token, machine: noam.machine } });
  assert.equal(chk.body.student.mode, '', 'החלון "עצמאי / קוד מורה" מוצג שוב');
  assert.equal((await call('POST', '/v1/student/join', { headers: plugin(noam), body: { code: codes['sara@test.dev'] } })).body.status, 'ok');
  const again = (await call('GET', '/v1/me/console', { token: sara })).body.students.find(x => x.email === 'noam@test.dev');
  assert.equal(again.classId, '');
  assert.equal(again.submits, 0);
});

await test('מחיקת כיתה: רק עם השם המדויק; כל התלמידים שבה וכל ההגשות שלהם נמחקים', async () => {
  for (let i = 0; i < 3; i++) await call('POST', '/v1/submit', { headers: plugin(ariel), body: bankSubmit({ exercise: { source: 'bank', bankId: 'q' + i, title: 'תרגיל ' + i } }) });
  assert.equal((await call('GET', '/v1/me/submissions?student=ariel@test.dev', { token: sara })).body.submissions.length, 3);
  assert.equal((await call('POST', '/v1/me/class', { token: sara, body: { op: 'delete', id: classB, confirm: 'יב 1' } })).status, 400, 'השם הישן — לא');
  let r;
  do { r = await call('POST', '/v1/me/class', { token: sara, body: { op: 'delete', id: classB, confirm: 'יב 2' } }); assert.equal(r.status, 200, JSON.stringify(r.body)); } while (!r.body.done);
  const c = await call('GET', '/v1/me/console', { token: sara });
  assert.ok(!c.body.classes.some(x => x.id === classB));
  assert.ok(!c.body.students.some(x => x.email === 'ariel@test.dev'));
  assert.equal((await call('GET', '/v1/me/submissions?student=ariel@test.dev', { token: sara })).body.submissions.length, 0);
});

await test('הצטרפות סגורה (joinOpen=false): קוד לא מצרף; פתיחה מחדש — מצרף', async () => {
  const yuval = await signInPlugin('yuval@test.dev', 'student', 'יובל');
  await call('POST', '/v1/me/settings', { token: sara, body: { joinOpen: false } });
  assert.equal((await call('POST', '/v1/student/join', { headers: plugin(yuval), body: { code: codes['sara@test.dev'] } })).body.status, 'closed');
  await call('POST', '/v1/me/settings', { token: sara, body: { joinOpen: true } });
  assert.equal((await call('POST', '/v1/student/join', { headers: plugin(yuval), body: { code: codes['sara@test.dev'] } })).body.status, 'ok');
});

await test('שחרור עמדה על ידי המורה: ההיתר חוזר, העמדה ננעלת; עד 5 בחודש — השישי נדחה עם הסבר', async () => {
  const before = (await call('GET', '/v1/me/console', { token: sara })).body;
  const r = await call('POST', '/v1/me/station/release', { token: sara, body: { id: stationId } });
  assert.equal(r.status, 200, JSON.stringify(r.body));
  const after = (await call('GET', '/v1/me/console', { token: sara })).body;
  assert.equal(after.teacher.used, before.teacher.used - 1);
  assert.equal(after.releases.used, 1);
  assert.ok(!after.stations.some(x => x.id === stationId));
  assert.equal((await call('POST', '/v1/access/check', { body: { token: station.token, machine: station.machine } })).body.status, 'revoked');
  for (let i = 0; i < 4; i++) {
    const m = machine();
    const cl = await call('POST', '/v1/station/claim', { body: { code: codes['sara@test.dev'], name: 'עמדה ' + (10 + i), machine: m } });
    assert.equal((await call('POST', '/v1/me/station/release', { token: sara, body: { id: cl.body.token.split('.')[1] } })).status, 200);
  }
  const m = machine();
  const cl = await call('POST', '/v1/station/claim', { body: { code: codes['sara@test.dev'], name: 'עמדה 20', machine: m } });
  const sixth = await call('POST', '/v1/me/station/release', { token: sara, body: { id: cl.body.token.split('.')[1] } });
  assert.equal(sixth.status, 429);
  assert.match(sixth.body.error, /בקשה להנהלה/);
});

await test('ההנהלה מכבה "תלמידים בחינם" — תלמיד בלי תשלום נדרש לשלם (pay), כמו קודם', async () => {
  await call('POST', '/v1/admin/settings', { token: admin, body: { studentsFree: false } });
  accessTest.reset();
  const chk = await call('POST', '/v1/access/check', { body: { token: noam.token, machine: noam.machine } });
  assert.equal(chk.body.status, 'pay');
  await call('POST', '/v1/admin/settings', { token: admin, body: { studentsFree: true } });
  accessTest.reset();
  assert.equal((await call('POST', '/v1/access/check', { body: { token: noam.token, machine: noam.machine } })).body.status, 'ok');
});

// ------------------------------------------------------------------ הרשמת תלמיד: שם מלא וטלפון
const NEW = ['register'];   // לקוח חדש: מכיר את המצב "register"
const checkAs = (tok, caps) => call('POST', '/v1/access/check', { body: { token: tok.token, machine: tok.machine, caps } });

let tamar;
await test('הרשמה: לקוח חדש מקבל register (בלי אישור) עד שהתלמיד כותב שם מלא וטלפון; לקוח ישן ממשיך לקבל ok', async () => {
  tamar = await signInPlugin('tamar@test.dev', 'student', 'tami2011', NEW);
  assert.equal(tamar.status, 'register', JSON.stringify(tamar));
  assert.ok(!tamar.pass, 'בלי הרשמה אין אישור');
  assert.ok(tamar.token, 'הכרטיס נשמר — אחרי ההרשמה אותו מחשב נפתח בלי כניסה נוספת');
  assert.equal(tamar.student.registered, false);
  assert.equal((await checkAs(tamar, NEW)).body.status, 'register');
  const old = await checkAs(tamar);   // לקוח ישן (בלי caps) — כמו קודם
  assert.equal(old.body.status, 'ok');
  assert.ok(old.body.pass);
});

await test('הרשמה: שם לא מלא / טלפון לא תקין נדחים עם הסבר; בלי כניסה — 401', async () => {
  const reg = body => call('POST', '/v1/student/register', { headers: plugin(tamar), body });
  for (const fullName of ['תמר', 'ת כ', 'תמר 123', 'tami2011', '', 'א'.repeat(70) + ' כהן']) {
    const r = await reg({ fullName, phone: '050-1234567' });
    assert.equal(r.status, 400, fullName + ' → ' + JSON.stringify(r.body));
    assert.equal(r.body.code, 'name');
  }
  for (const phone of ['', '12345', '03-5551234', '050-123456', '0501234567 8']) {
    const r = await reg({ fullName: 'תמר כהן', phone });
    assert.equal(r.status, 400, phone + ' → ' + JSON.stringify(r.body));
    assert.equal(r.body.code, 'phone');
  }
  assert.equal((await call('POST', '/v1/student/register', { body: { fullName: 'תמר כהן', phone: '0501234567' } })).status, 401);
  assert.equal((await checkAs(tamar, NEW)).body.status, 'register');
});

await test('הרשמה מהתוסף: נשמרת (שם מסודר, טלפון ב-10 ספרות), והמאגר נפתח — אישור חתום', async () => {
  const r = await call('POST', '/v1/student/register', { headers: plugin(tamar), body: { fullName: '  תמר   בן-דוד ', phone: '+972 50-123 4567' } });
  assert.equal(r.status, 200, JSON.stringify(r.body));
  assert.equal(r.body.first, true);
  assert.equal(r.body.registered, true);
  assert.equal(r.body.fullName, 'תמר בן-דוד');
  const chk = await checkAs(tamar, NEW);
  assert.equal(chk.body.status, 'ok');
  assert.ok(chk.body.pass);
  assert.ok(chk.body.features.includes('submit'), 'השרת אומר שהוא מקבל הגשות — רק אז התוסף מציג "הגשה למורה"');
  assert.deepEqual(chk.body.student, { mode: '', teacher: null, registered: true, fullName: 'תמר בן-דוד' });
  const me = (await call('GET', '/v1/me', { token: await idToken('tamar@test.dev', 'tami2011') })).body;
  assert.equal(me.student.registered, true);
  assert.equal(me.student.fullName, 'תמר בן-דוד');
  assert.equal(me.student.phone, '0501234567');
});

await test('הרשמה מהדף בדפדפן (לפני שחוזרים לתוכנה): התוסף מקבל ok כבר בכניסה; המורה רואה את השם המלא ולא את הכינוי מ-Google', async () => {
  const web = await idToken('yoav@test.dev', 'yoyo_gamer');
  const before = (await call('GET', '/v1/me', { token: web })).body;
  assert.equal(before.student.registered, false);
  const r = await call('POST', '/v1/student/register', { token: web, body: { fullName: "יואב ז'אן לוי", phone: '052-7654321' } });
  assert.equal(r.status, 200, JSON.stringify(r.body));
  assert.equal((await call('POST', '/v1/student/join', { token: web, body: { code: codes['sara@test.dev'] } })).body.status, 'ok');
  const yoav = await signInPlugin('yoav@test.dev', 'student', 'yoyo_gamer', NEW);
  assert.equal(yoav.status, 'ok', JSON.stringify(yoav));
  assert.ok(yoav.pass);
  assert.equal(yoav.student.mode, 'teacher');
  const row = (await call('GET', '/v1/me/console', { token: sara })).body.students.find(x => x.email === 'yoav@test.dev');
  assert.equal(row.name, "יואב ז'אן לוי");
  // עדכון השם ב"החשבון שלי" — מתעדכן גם אצל המורה
  const upd = await call('POST', '/v1/student/register', { token: web, body: { fullName: 'יואב לוי', phone: '0527654321' } });
  assert.equal(upd.body.first, false);
  assert.equal((await call('GET', '/v1/me/console', { token: sara })).body.students.find(x => x.email === 'yoav@test.dev').name, 'יואב לוי');
});

await test('מסוף ההנהלה: לכל תלמיד — שם מלא, טלפון, נרשם / לא, והמורה שלו', async () => {
  const list = (await call('GET', '/v1/admin/students', { token: admin })).body.students;
  const t = list.find(x => x.email === 'tamar@test.dev'), y = list.find(x => x.email === 'yoav@test.dev'), n = list.find(x => x.email === 'noam@test.dev');
  assert.equal(t.name, 'תמר בן-דוד');
  assert.equal(t.googleName, 'tami2011');
  assert.equal(t.phone, '0501234567');
  assert.equal(t.registered, true);
  assert.ok(t.registeredAt);
  assert.equal(y.teacher, 'sara@test.dev');
  assert.equal(n.registered, false);
  const one = (await call('GET', '/v1/admin/student?email=tamar@test.dev', { token: admin })).body.student;
  assert.equal(one.name, 'תמר בן-דוד');
  assert.equal(one.phone, '0501234567');
  assert.equal(one.registered, true);
});

await test('מסוף ההנהלה — עמדות ותלמידי מורים: כל התלמידים המשויכים (עם הכיתה), בנפרד מעמדות המעבדה; עצמאי לא ברשימה; אפשר לצמצם למורה', async () => {
  const cls = (await call('POST', '/v1/me/class', { token: sara, body: { op: 'create', name: 'י״א 3' } })).body.id;
  assert.equal((await call('POST', '/v1/me/member', { token: sara, body: { op: 'move', student: 'yoav@test.dev', classId: cls } })).status, 200);
  const all = (await call('GET', '/v1/admin/rooms', { token: admin })).body;
  const y = all.students.find(x => x.email === 'yoav@test.dev');
  assert.equal(y.teacher, 'sara@test.dev');
  assert.equal(y.classId, cls, 'הכיתה של התלמיד');
  assert.equal(y.name, 'יואב לוי');
  assert.ok(y.teacherName, 'שם המורה');
  assert.ok(!all.students.some(x => x.email === 'tamar@test.dev'), 'תלמיד עצמאי לא ברשימת המשויכים');
  const c = all.classes.find(x => x.id === cls);
  assert.equal(c.name, 'י״א 3');
  assert.equal(c.teacher, 'sara@test.dev');
  assert.ok(c.students >= 1, 'מספר התלמידים בכיתה');
  assert.ok(all.teachers.some(t => t.email === 'sara@test.dev'));
  const st = all.stations.find(x => x.name === 'עמדה 20');   // העמדה שנשארה מחוברת מהבדיקה הקודמת (השחרור שלה נחסם במכסה)
  assert.ok(st && st.teacher === 'sara@test.dev' && st.teacherName, 'עמדת המעבדה ברשימת העמדות, עם שם המורה');
  assert.ok(!all.students.some(x => x.name === 'עמדה 20' || x.email === st.id), 'עמדה היא לא תלמיד');
  // צמצום למורה אחד: רק שלו
  const one = (await call('GET', '/v1/admin/rooms?teacher=sara@test.dev', { token: admin })).body;
  assert.ok(one.students.length >= 1 && one.students.every(x => x.teacher === 'sara@test.dev'));
  assert.ok(one.classes.every(x => x.teacher === 'sara@test.dev') && one.stations.every(x => x.teacher === 'sara@test.dev'));
  const other = (await call('GET', '/v1/admin/rooms?teacher=david@test.dev', { token: admin })).body;
  assert.ok(!other.students.some(x => x.email === 'yoav@test.dev'));
  // רק מנהל
  assert.equal((await call('GET', '/v1/admin/rooms', { token: sara })).status, 403);
  assert.equal((await call('GET', '/v1/admin/rooms')).status, 401);
  // מוציאים את התלמיד מהכיתה: נשאר משויך למורה, בלי כיתה, והכיתה ריקה
  assert.equal((await call('POST', '/v1/me/member', { token: sara, body: { op: 'move', student: 'yoav@test.dev', classId: '' } })).status, 200);
  const after = (await call('GET', '/v1/admin/rooms', { token: admin })).body;
  assert.equal(after.students.find(x => x.email === 'yoav@test.dev').classId, '');
  assert.equal(after.classes.find(x => x.id === cls).students, 0);
});

await test('ההנהלה משייכת תלמיד למורה (גם עצמאי / שעוד לא נכנס), מעבירה למורה אחר עם כיתה, ומבטלת שיוך — והמורה והתלמיד רואים מיד', async () => {
  const pupil = await signInPlugin('assign.me@test.dev', 'student', 'gamer77', NEW);
  await call('POST', '/v1/student/register', { headers: plugin(pupil), body: { fullName: 'נגה שלום', phone: '0531112233' } });
  await call('POST', '/v1/student/mode', { headers: plugin(pupil), body: { mode: 'independent' } });
  assert.equal((await checkAs(pupil, NEW)).body.student.mode, 'independent');
  // שיוך: תלמיד עצמאי + כתובת שעוד לא נכנסה אף פעם
  const r = await call('POST', '/v1/admin/student/assign', { token: admin, body: { emails: ['assign.me@test.dev', ' Never.In@Test.dev '], teacher: 'david@test.dev' } });
  assert.equal(r.status, 200, JSON.stringify(r.body));
  assert.equal(r.body.changed, 2);
  assert.deepEqual(r.body.results.map(x => x.status), ['assigned', 'assigned']);
  const dc = (await call('GET', '/v1/me/console', { token: david })).body;
  const row = dc.students.find(x => x.email === 'assign.me@test.dev');
  assert.ok(row && !row.left, 'המורה רואה את התלמיד בעמדת הניהול');
  assert.equal(row.name, 'נגה שלום', 'עם השם המלא מההרשמה');
  assert.ok(dc.students.some(x => x.email === 'never.in@test.dev'), 'גם מי שעוד לא נכנס — מחכה אצל המורה');
  const st = (await checkAs(pupil, NEW)).body.student;
  assert.equal(st.mode, 'teacher');
  assert.equal(st.teacher.email, 'david@test.dev', 'התלמיד רואה את המורה בתוכנה');
  const rooms = (await call('GET', '/v1/admin/rooms?teacher=david@test.dev', { token: admin })).body;
  assert.ok(rooms.students.some(x => x.email === 'assign.me@test.dev'));
  assert.ok(!(await call('GET', '/v1/admin/students', { token: admin })).body.students.find(x => x.email === 'assign.me@test.dev' && !x.teacher), 'כבר לא ברשימת העצמאיים');
  // שוב אותו שיוך — בלי שינוי, והמונה של המורה לא קופץ
  const membersBefore = (await call('GET', '/v1/admin/teacher?email=david@test.dev', { token: admin })).body.teacher;
  const again = await call('POST', '/v1/admin/student/assign', { token: admin, body: { email: 'assign.me@test.dev', teacher: 'david@test.dev' } });
  assert.equal(again.body.changed, 0);
  assert.equal(again.body.results[0].status, 'same');
  assert.deepEqual((await call('GET', '/v1/admin/teacher?email=david@test.dev', { token: admin })).body.teacher, membersBefore);
  // העברה למורה אחר, ישר לכיתה שלו
  const cls = (await call('POST', '/v1/me/class', { token: sara, body: { op: 'create', name: 'שיוך מההנהלה' } })).body.id;
  assert.equal((await call('POST', '/v1/admin/student/assign', { token: admin, body: { email: 'assign.me@test.dev', teacher: 'sara@test.dev', classId: 'nope' } })).status, 400, 'כיתה שלא קיימת');
  const mv = await call('POST', '/v1/admin/student/assign', { token: admin, body: { email: 'assign.me@test.dev', teacher: 'sara@test.dev', classId: cls } });
  assert.equal(mv.body.results[0].status, 'moved');
  const sc = (await call('GET', '/v1/me/console', { token: sara })).body.students.find(x => x.email === 'assign.me@test.dev');
  assert.ok(sc && !sc.left);
  assert.equal(sc.classId, cls, 'בכיתה שנבחרה');
  assert.ok((await call('GET', '/v1/me/console', { token: david })).body.students.find(x => x.email === 'assign.me@test.dev').left, 'אצל המורה הקודם — "עזב"');
  assert.equal((await checkAs(pupil, NEW)).body.student.teacher.email, 'sara@test.dev');
  // ביטול שיוך — עצמאי
  const un = await call('POST', '/v1/admin/student/assign', { token: admin, body: { emails: ['assign.me@test.dev', 'never.in@test.dev'], teacher: '' } });
  assert.deepEqual(un.body.results.map(x => x.status), ['unassigned', 'unassigned']);
  assert.equal((await checkAs(pupil, NEW)).body.student.mode, 'independent');
  assert.ok((await call('GET', '/v1/me/console', { token: sara })).body.students.find(x => x.email === 'assign.me@test.dev').left);
  assert.ok((await call('GET', '/v1/admin/students', { token: admin })).body.students.some(x => x.email === 'assign.me@test.dev' && !x.teacher), 'חזר לרשימת העצמאיים');
  // שגיאות והרשאות
  assert.equal((await call('POST', '/v1/admin/student/assign', { token: admin, body: { email: 'assign.me@test.dev', teacher: 'nobody@test.dev' } })).status, 404);
  assert.equal((await call('POST', '/v1/admin/student/assign', { token: admin, body: { email: 'not-an-email', teacher: 'sara@test.dev' } })).status, 400);
  assert.equal((await call('POST', '/v1/admin/student/assign', { token: admin, body: { email: 'sara@test.dev', teacher: 'sara@test.dev' } })).status, 400, 'מורה לעצמו');
  assert.equal((await call('POST', '/v1/admin/student/assign', { token: sara, body: { email: 'assign.me@test.dev', teacher: 'sara@test.dev' } })).status, 403, 'רק מנהל');
  const log = (await call('GET', '/v1/admin/log?limit=40', { token: admin })).body.log;
  assert.ok(log.some(l => l.action === 'student.assign' && l.target === 'assign.me@test.dev') && log.some(l => l.action === 'student.unassign'), 'נרשם ביומן');
  await call('POST', '/v1/me/class', { token: sara, body: { op: 'delete', id: cls, confirm: 'שיוך מההנהלה' } });
});

await test('עמדת הניהול בתוכנה (מורה מחובר במחשב): מחשבי המעבדה (כינוי, מתי פעלו) והתלמידים המשויכים; שחרור מחשב והסרת תלמיד', async () => {
  const t = await signInPlugin('sara@test.dev', 'teacher', 'שרה כהן');
  const m = machine();
  const cl = await call('POST', '/v1/station/claim', { body: { code: codes['sara@test.dev'], name: 'מעבדה 4 · מחשב 9', machine: m, client: 'test' } });
  assert.equal(cl.body.status, 'ok', JSON.stringify(cl.body));
  const sid = cl.body.token.split('.')[1];
  const kid = await signInPlugin('panel.kid@test.dev', 'student', 'ילד');
  await call('POST', '/v1/student/register', { headers: plugin(kid), body: { fullName: 'רוני פאנל', phone: '0539998877' } });
  assert.equal((await call('POST', '/v1/student/join', { headers: plugin(kid), body: { code: codes['sara@test.dev'] } })).body.status, 'ok');
  const p = await call('POST', '/v1/teacher/panel', { headers: plugin(t), body: {} });
  assert.equal(p.status, 200, JSON.stringify(p.body));
  const st = p.body.stations.find(x => x.id === sid);
  assert.ok(st && st.name === 'מעבדה 4 · מחשב 9' && st.lastSeen && st.claimedAt, 'המחשב עם הכינוי ומתי פעל: ' + JSON.stringify(st));
  const kidRow = p.body.students.find(x => x.email === 'panel.kid@test.dev');
  assert.ok(kidRow && kidRow.name === 'רוני פאנל', 'התלמיד המשויך, עם השם המלא');
  assert.ok(Number.isInteger(p.body.releasesLeft), 'כמה שחרורים נשארו החודש');
  assert.ok(!p.body.students.some(x => x.email === 'tamar@test.dev'), 'תלמיד עצמאי — לא ברשימה');
  // רק מורה מחובר במחשב
  assert.equal((await call('POST', '/v1/teacher/panel', { headers: plugin(kid), body: {} })).status, 403);
  assert.equal((await call('POST', '/v1/teacher/panel', { body: {} })).status, 401);
  // הסרת תלמיד: עצמאי, ההגשות נשארות אצל המורה ("עזב")
  assert.equal((await call('POST', '/v1/teacher/student/release', { headers: plugin(t), body: { email: 'nobody@test.dev' } })).status, 404);
  assert.equal((await call('POST', '/v1/teacher/student/release', { headers: plugin(t), body: { email: 'panel.kid@test.dev' } })).status, 200);
  assert.ok(!(await call('POST', '/v1/teacher/panel', { headers: plugin(t), body: {} })).body.students.some(x => x.email === 'panel.kid@test.dev'), 'יצא מהרשימה');
  assert.ok((await call('GET', '/v1/me/console', { token: sara })).body.students.find(x => x.email === 'panel.kid@test.dev').left, 'בעמדת הניהול בדפדפן — "עזב"');
  const kidState = (await call('GET', '/v1/student/state', { headers: plugin(kid) })).body;
  assert.ok(!kidState.teacher, 'התלמיד כבר לא משויך: ' + JSON.stringify(kidState).slice(0, 200));
  // שחרור מחשב (אם נשארו שחרורים החודש — המכסה נבדקת בבדיקה של עמדת הניהול בדפדפן)
  const rel = await call('POST', '/v1/teacher/station/release', { headers: plugin(t), body: { id: sid } });
  if (p.body.releasesLeft > 0) {
    assert.equal(rel.status, 200, JSON.stringify(rel.body));
    assert.ok(!(await call('POST', '/v1/teacher/panel', { headers: plugin(t), body: {} })).body.stations.some(x => x.id === sid), 'המחשב יצא מהרשימה');
  } else {
    assert.equal(rel.status, 429, 'בלי שחרורים שנשארו — הסבר ובקשה להנהלה');
    assert.match(rel.body.error, /בקשה להנהלה/);
  }
  assert.equal((await call('POST', '/v1/teacher/station/release', { headers: plugin(t), body: { id: 'nope' } })).status, 404);
});

await test('מעבר לתשלום: גם תלמיד רשום שכבר מחובר נדרש לשלם (pay); מי שעוד לא נרשם — קודם ההרשמה (בחינם), ואז התשלום', async () => {
  const fresh = await signInPlugin('gali@test.dev', 'student', 'גלי', NEW);
  assert.equal(fresh.status, 'register');
  assert.equal((await checkAs(tamar, NEW)).body.status, 'ok');
  await call('POST', '/v1/admin/settings', { token: admin, body: { studentsFree: false } });
  accessTest.reset();
  const paid = await checkAs(tamar, NEW);
  assert.equal(paid.body.status, 'pay');
  assert.ok(!paid.body.pass, 'בלי תשלום אין אישור');
  assert.equal((await checkAs(fresh, NEW)).body.status, 'register');
  await call('POST', '/v1/student/register', { headers: plugin(fresh), body: { fullName: 'גלי שחר', phone: '0541112233' } });
  assert.equal((await checkAs(fresh, NEW)).body.status, 'pay');
  // הענקה ידנית מההנהלה פותחת (כמו תשלום)
  await call('POST', '/v1/admin/student', { token: admin, body: { email: 'tamar@test.dev', until: '2099-01-01' } });
  const open = await checkAs(tamar, NEW);
  assert.equal(open.body.status, 'ok');
  assert.ok(open.body.pass);
  await call('POST', '/v1/admin/settings', { token: admin, body: { studentsFree: true } });
  accessTest.reset();
  assert.equal((await checkAs(fresh, NEW)).body.status, 'ok');
});

await test('כיתה גדולה (30 תלמידים, 60 הגשות): המחיקה בשלבים, וכל קריאה בתוך מגבלת הפניות של Cloudflare', async () => {
  const cls = (await call('POST', '/v1/me/class', { token: sara, body: { op: 'create', name: 'שכבה גדולה' } })).body.id;
  const emails = [];
  for (let i = 0; i < 30; i++) {
    const email = `big${i}@test.dev`;
    const tok = await signInPlugin(email, 'student', 'תלמיד ' + i);
    assert.equal((await call('POST', '/v1/student/join', { headers: plugin(tok), body: { code: codes['sara@test.dev'] } })).body.status, 'ok');
    assert.equal((await call('POST', '/v1/me/member', { token: sara, body: { op: 'move', student: email, classId: cls } })).status, 200);
    for (const q of ['a', 'b']) assert.equal((await call('POST', '/v1/submit', { headers: plugin(tok), body: bankSubmit({ exercise: { source: 'bank', bankId: 'big-' + q, title: 'תרגיל ' + q } }) })).status, 200);
    emails.push(email);
  }
  // שיעורי בית לכיתה (נמחקים איתה) ולכל התלמידים (נשארים)
  const hwBig = (await hwSave(sara, { classId: cls, items: ['queue-sum-even', 'stack-sum-size'] })).body.id;
  const hwBig2 = (await hwSave(sara, { classId: cls, items: ['tree-count-sum'], due: '2026-12-01' })).body.id;
  const hwKeep = (await hwSave(sara, { items: ['queue-two-sum'] })).body.id;
  assert.ok(hwBig && hwBig2 && hwKeep);
  const before = (await call('GET', '/v1/me/console', { token: sara })).body;
  assert.equal(before.students.filter(x => x.classId === cls).length, 30);
  assert.ok(before.homework.some(h => h.id === hwBig));
  let r, steps = 0, most = 0;
  do {
    r = await call('POST', '/v1/me/class', { token: sara, body: { op: 'delete', id: cls, confirm: 'שכבה גדולה' } });
    assert.equal(r.status, 200, JSON.stringify(r.body));
    most = Math.max(most, r.used);
    assert.ok(++steps < 20, 'המחיקה לא מתקדמת');
  } while (!r.body.done);
  assert.ok(steps >= 3, 'מחיקה בשלבים: ' + steps);
  assert.ok(most <= 40, 'פניות בקריאה אחת: ' + most);
  const after = (await call('GET', '/v1/me/console', { token: sara })).body;
  assert.ok(!after.classes.some(x => x.id === cls));
  assert.ok(!after.students.some(x => emails.includes(x.email)));
  assert.ok(!after.homework.some(h => h.id === hwBig || h.id === hwBig2), 'שיעורי הבית של הכיתה נמחקו איתה');
  assert.ok(after.homework.some(h => h.id === hwKeep), 'שיעורי בית לכל התלמידים — נשארים');
  for (const e of [emails[0], emails[17], emails[29]]) assert.equal((await call('GET', '/v1/me/submissions?student=' + e, { token: sara })).body.submissions.length, 0);
  // המונה של המורה (תלמידים) ירד בחזרה
  const t = (await call('GET', '/v1/admin/teacher?email=sara@test.dev', { token: admin })).body.teacher;
  assert.ok((t.members || 0) < 30, 'members: ' + t.members);
});

await test('תלמיד עם יותר הגשות ממה שנמחק בקריאה אחת: המחיקה ממשיכה בקריאה הבאה (done=false)', async () => {
  const tok = await signInPlugin('heavy@test.dev', 'student', 'תלמיד חרוץ');
  await call('POST', '/v1/student/join', { headers: plugin(tok), body: { code: codes['sara@test.dev'] } });
  // 210 הגשות ישירות באמולטור (יותר מ-198 שנמחקות בקריאה אחת) — דרך השרת זה היה לוקח דקות
  const base = `http://${FS}/v1/projects/${PROJECT}/databases/(default)/documents`;
  const writes = [];
  for (let i = 0; i < 210; i++) {
    for (const col of ['submissions', 'submissionFiles']) {
      writes.push({ update: { name: `projects/${PROJECT}/databases/(default)/documents/${col}/heavy${i}`, fields: { teacher: { stringValue: 'sara@test.dev' }, student: { stringValue: 'heavy@test.dev' }, from: { stringValue: 'student' } } } });
    }
  }
  const res = await realFetch(base + ':commit', { method: 'POST', headers: { 'content-type': 'application/json', authorization: 'Bearer owner' }, body: JSON.stringify({ writes }) });
  assert.equal(res.status, 200, await res.text());
  const one = await call('POST', '/v1/me/member', { token: sara, body: { op: 'delete', student: 'heavy@test.dev' } });
  assert.equal(one.body.done, false, 'יש עוד הגשות');
  assert.ok(one.used <= 40);
  const two = await call('POST', '/v1/me/member', { token: sara, body: { op: 'delete', student: 'heavy@test.dev' } });
  assert.equal(two.body.done, true);
  assert.equal((await call('GET', '/v1/me/submissions?student=heavy@test.dev', { token: sara })).body.submissions.length, 0);
});

await test('Firestore אמיתי בלי אינדקס מורכב: השאילתות לא נכשלות (סינון בשרת), ועמדת הניהול עובדת כרגיל', async () => {
  const m = machine();
  const cl = await call('POST', '/v1/station/claim', { body: { code: codes['david@test.dev'], name: 'עמדה של דוד', machine: m, client: 'test' } });
  assert.equal(cl.body.status, 'ok', JSON.stringify(cl.body));
  const st = { token: cl.body.token, machine: m };
  await call('POST', '/v1/submit', { headers: plugin(st), body: bankSubmit({ who: 'רוני' }) });
  const normal = (await call('GET', '/v1/me/console', { token: david })).body;
  failIndex = true;
  try {
    const c = await call('GET', '/v1/me/console', { token: david });
    assert.equal(c.status, 200, JSON.stringify(c.body));
    assert.deepEqual(c.body.stations.map(x => [x.name, x.pending]), normal.stations.map(x => [x.name, x.pending]));
    assert.equal(c.body.stations[0].pending, 1);
    const list = await call('GET', '/v1/me/submissions?station=' + cl.body.token.split('.')[1], { token: david });
    assert.equal(list.body.submissions.length, 1);
    assert.equal(list.body.submissions[0].who, 'רוני');
    // בדיקת המערכת מדווחת על האינדקס החסר (אזהרה, לא כישלון) עם הקישור ליצירה
    const t2 = await call('GET', '/v1/admin/selftest?part=2', { token: admin });
    const q = t2.body.checks.find(x => x.id === 'queries');
    assert.equal(q.state, 'warn', JSON.stringify(q));
    assert.match(q.detail, /console\.firebase\.google\.com/);
  } finally {
    failIndex = false;
  }
});

await test('בדיקת מערכת (ההנהלה): כל השורות תקינות; רק מנהל; לא משאירה מסמכים', async () => {
  assert.equal((await call('GET', '/v1/admin/selftest', { token: sara })).status, 403);
  const a = await call('GET', '/v1/admin/selftest?part=1', { token: admin });
  assert.equal(a.status, 200, JSON.stringify(a.body));
  assert.ok(a.used <= 40, 'פניות: ' + a.used);
  const bad = a.body.checks.filter(x => x.state === 'fail');
  assert.deepEqual(bad, []);
  for (const id of ['firestore', 'merge', 'tx', 'rules', 'pass', 'kv', 'mail', 'pay']) assert.ok(a.body.checks.some(x => x.id === id), 'חסרה שורה: ' + id);
  assert.equal(a.body.checks.find(x => x.id === 'merge').state, 'ok');
  assert.equal(a.body.checks.find(x => x.id === 'tx').state, 'ok');
  const b = await call('GET', '/v1/admin/selftest?part=2', { token: admin });
  assert.equal(b.status, 200, JSON.stringify(b.body));
  assert.ok(b.used <= 40, 'פניות: ' + b.used);
  assert.equal(b.body.checks.find(x => x.id === 'queries').state, 'ok', JSON.stringify(b.body.checks));
  const left = await realFetch(`http://${FS}/v1/projects/${PROJECT}/databases/(default)/documents/selftest`, { headers: { authorization: 'Bearer owner' } });
  assert.deepEqual((await left.json()).documents || [], []);
});

await test('בדיקת מערכת: הגדרה חסרה מתגלה עם הסבר (מפתח החתימה, ההגדרות של Google לדפים, Firestore שלא עונה)', async () => {
  const { selfTest } = await import('../src/access/selftest.js');
  const state = (r, id) => (r.checks.find(x => x.id === id) || {}).state;
  // כמו בשרת האמיתי (בלי אמולטור ל-Auth): בלי FIREBASE_API_KEY / FIREBASE_AUTH_DOMAIN — אדום; בלי PASS_KEY — אדום
  const noAuth = Object.assign({}, ENV, { FIREBASE_AUTH_EMULATOR_HOST: '', PASS_KEY: '' });
  const r1 = await selfTest(noAuth, new URL(BASE + '/v1/admin/selftest?part=1'));
  assert.equal(state(r1, 'google'), 'fail');
  assert.equal(state(r1, 'pass'), 'fail');
  assert.equal(state(r1, 'firestore'), 'ok');
  const r2 = await selfTest(Object.assign({}, noAuth, { FIREBASE_API_KEY: 'k', FIREBASE_AUTH_DOMAIN: 'x.firebaseapp.com', PASS_KEY: ENV.PASS_KEY }), new URL(BASE + '/v1/admin/selftest?part=1'));
  assert.equal(state(r2, 'google'), 'ok');
  assert.equal(state(r2, 'pass'), 'ok');
  // Firestore לא עונה (חשבון שירות שגוי / פרויקט שגוי): שורה אדומה אחת עם הסבר מה לבדוק, ולא קריסה
  const hold = globalThis.fetch;
  globalThis.fetch = async (input, init) => (String(typeof input === 'string' ? input : input.url).includes('/documents')
    ? new Response(JSON.stringify({ error: { code: 403, status: 'PERMISSION_DENIED', message: 'Missing or insufficient permissions.' } }), { status: 403, headers: { 'content-type': 'application/json' } })
    : hold(input, init));
  try {
    const r3 = await selfTest(ENV, new URL(BASE + '/v1/admin/selftest?part=1'));
    assert.equal(state(r3, 'firestore'), 'fail');
    assert.match(r3.checks[0].detail, /FIREBASE_SERVICE_ACCOUNT/);
    assert.equal(r3.checks.length, 1, 'בלי Firestore לא ממשיכים לשאר');
  } finally {
    globalThis.fetch = hold;
  }
});

await test('ניקוי כללי: הגשות מעמדות שפג תוקפן נמחקות גם אצל מורה שלא פותח את עמדת הניהול', async () => {
  const m = machine();
  const cl = await call('POST', '/v1/station/claim', { body: { code: codes['david@test.dev'], name: 'עמדה נשכחת', machine: m, client: 'test' } });
  const st = { token: cl.body.token, machine: m };
  const a = await call('POST', '/v1/submit', { headers: plugin(st), body: bankSubmit({ who: 'ישן' }) });
  const b = await call('POST', '/v1/submit', { headers: plugin(st), body: bankSubmit({ who: 'חדש' }) });
  for (const col of ['submissions', 'submissionFiles']) {
    const url = `http://${FS}/v1/projects/${PROJECT}/databases/(default)/documents/${col}/${a.body.id}?updateMask.fieldPaths=expiresAt`;
    const res = await realFetch(url, { method: 'PATCH', headers: { 'content-type': 'application/json', authorization: 'Bearer owner' }, body: JSON.stringify({ fields: { expiresAt: { timestampValue: new Date(Date.now() - 1000).toISOString() } } }) });
    assert.equal(res.status, 200, await res.text());
  }
  assert.equal(await purgeExpired(ENV, true), 1);
  const get = async (col, id) => (await realFetch(`http://${FS}/v1/projects/${PROJECT}/databases/(default)/documents/${col}/${id}`, { headers: { authorization: 'Bearer owner' } })).status;
  assert.equal(await get('submissions', a.body.id), 404);
  assert.equal(await get('submissionFiles', a.body.id), 404);
  assert.equal(await get('submissions', b.body.id), 200, 'הגשה שעוד בתוקף נשארת');
  // הגשות של תלמידים (בלי תפוגה) אף פעם לא נמחקות בניקוי
  const mine = await call('GET', '/v1/me/submissions?student=noam@test.dev', { token: sara });
  assert.equal(await purgeExpired(ENV, true), 0);
  assert.equal((await call('GET', '/v1/me/submissions?student=noam@test.dev', { token: sara })).body.submissions.length, mine.body.submissions.length);
});

// ------------------------------------------------------------------ שתי גרסאות, שרת אחד: מרכז הבקרה כבוי לכולם עד שמדליקים

await test('מרכז הבקרה כבוי (ברירת המחדל): לקוח רגיל (1.0.2 ומטה) מקבל בדיוק מה שקיבל קודם — תלמיד בחינם, בלי הרשמה, הדף הקודם', async () => {
  assert.equal((await call('GET', '/v1/access/config')).body.classroom, false);
  const old = await signInPlugin('plain.pupil@test.dev', 'student', 'תלמיד רגיל', []);
  assert.equal(old.status, 'ok', 'כמו בשרת של היום: תלמיד בחינם (studentsFree), בלי הרשמה');
  assert.ok(old.pass);
  assert.equal((await call('POST', '/v1/access/check', { body: { token: old.token, machine: old.machine } })).body.status, 'ok');
  const web = await idToken('plain.pupil@test.dev', 'תלמיד רגיל');
  const me = (await call('GET', '/v1/me', { token: web })).body;
  assert.deepEqual({ classroom: me.classroom, free: me.student.free, open: me.student.open }, { classroom: false, free: true, open: true });
  assert.ok(!('work' in me.student.state), 'בלי התרגילים והציונים (הדף מציג את הגרסה הקודמת)');
  // ההנהלה מכבה "תלמידים בחינם" — גם בגרסה הרגילה נדרש תשלום / הענקה (כמו היום), ומדליקה חזרה
  await call('POST', '/v1/admin/settings', { token: admin, body: { studentsFree: false } });
  accessTest.reset();
  assert.equal((await call('POST', '/v1/access/check', { body: { token: old.token, machine: old.machine } })).body.status, 'pay');
  await call('POST', '/v1/admin/settings', { token: admin, body: { studentsFree: true } });
  accessTest.reset();
  assert.equal((await call('POST', '/v1/access/check', { body: { token: old.token, machine: old.machine } })).body.status, 'ok');
  // מורה שלא השתמש בגרסה עם מרכז הבקרה: הדף הקודם (קוד, עמדות, חבילות)
  assert.equal((await call('GET', '/v1/me', { token: david })).body.classroom, false);
});

await test('הגרסה עם מרכז הבקרה פותחת אותו לבד: עמדה שהתחברה ממנה → גם המורה שלה רואה את עמדת הניהול', async () => {
  await call('POST', '/v1/admin/teacher', { token: admin, body: { create: true, email: 'rina@test.dev', name: 'רינה', school: 'תיכון', seats: 2 } });
  const rina = await idToken('rina@test.dev', 'רינה');
  const code = (await call('GET', '/v1/admin/teacher?email=rina@test.dev', { token: admin })).body.teacher.code;
  assert.equal((await call('GET', '/v1/me', { token: rina })).body.classroom, false);
  const m1 = machine();
  await call('POST', '/v1/station/claim', { body: { code, name: 'רגילה', machine: m1, client: 'dsviz/1.0.2' } });
  assert.equal((await call('GET', '/v1/me', { token: rina })).body.classroom, false, 'עמדה מהגרסה הרגילה לא משנה כלום');
  const m2 = machine();
  const cl = await call('POST', '/v1/station/claim', { body: { code, name: 'עם מרכז הבקרה', machine: m2, client: 'dsviz/1.0.3', caps: ['register', 'classroom'] } });
  assert.equal(cl.body.status, 'ok');
  assert.equal((await call('GET', '/v1/me', { token: rina })).body.classroom, true);
  // תלמיד מהגרסה עם מרכז הבקרה: בחינם (גם כשהוא בודק אחר כך מגרסה רגילה — הוא כבר במרכז הבקרה)
  const p = await signInPlugin('cc.pupil@test.dev', 'student', 'תלמיד', ['classroom']);
  assert.equal(p.status, 'ok');
  assert.equal((await call('POST', '/v1/access/check', { body: { token: p.token, machine: p.machine } })).body.status, 'ok');
});

await test('חשבונות בדיקה: מורה ברשימה רואה את מרכז הבקרה בדפדפן גם כשהוא כבוי; מחוץ לרשימה — לא', async () => {
  assert.equal((await call('POST', '/v1/admin/settings', { token: admin, body: { classroomTesters: 'DAVID@test.dev, bad-email' } })).status, 400);
  assert.equal((await call('POST', '/v1/admin/settings', { token: admin, body: { classroomTesters: ' DAVID@test.dev \n' } })).status, 200);
  accessTest.reset();
  assert.deepEqual((await call('GET', '/v1/admin/settings', { token: admin })).body.settings.classroomTesters, ['david@test.dev']);
  assert.equal((await call('GET', '/v1/me', { token: david })).body.classroom, true);
  await call('POST', '/v1/admin/settings', { token: admin, body: { classroomTesters: [] } });
  accessTest.reset();
  assert.equal((await call('GET', '/v1/me', { token: david })).body.classroom, false);
});

await test('ההנהלה פותחת את מרכז הבקרה לכולם: גם לקוח רגיל — דף הכניסה מבקש הרשמה, והדף החדש; וסוגרת חזרה', async () => {
  await call('POST', '/v1/admin/settings', { token: admin, body: { classroomOpen: true } });
  accessTest.reset();
  assert.equal((await call('GET', '/v1/access/config')).body.classroom, true, 'דף הכניסה יבקש הרשמה גם מגרסה רגילה');
  const old = await signInPlugin('open.pupil@test.dev', 'student', 'תלמיד', []);
  assert.equal(old.status, 'ok', 'בחינם');
  assert.equal((await call('GET', '/v1/me', { token: david })).body.classroom, true);
  await call('POST', '/v1/admin/settings', { token: admin, body: { classroomOpen: false } });
  accessTest.reset();
  assert.equal((await call('GET', '/v1/me', { token: david })).body.classroom, false);
  assert.equal((await call('POST', '/v1/access/check', { body: { token: old.token, machine: old.machine } })).body.status, 'ok', 'נסגר — חוזר להיות כמו קודם (התלמיד עדיין בחינם)');
});

await test('אף קריאה בכל הבדיקות לא עברה 40 פניות החוצה (המגבלה של Cloudflare החינמי: 50)', async () => {
  assert.ok(sub.max <= 40, `${sub.route}: ${sub.max} פניות`);
  console.log(`  (הכי הרבה: ${sub.max} — ${sub.route})`);
});

console.log(`\n${passed} בדיקות עברו` + (process.exitCode ? ' — יש כישלונות' : ''));
