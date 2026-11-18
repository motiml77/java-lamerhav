// בדיקות לעמדת הניהול של המורה (src/access/classroom.js) מול Firestore ו-Auth אמיתיים — באמולטור:
// תלמיד עצמאי / הצטרפות בקוד מורה, הגשה מתלמיד ומעמדה ("שם המגיש" חובה, בדיקת ההרצה), עמדת הניהול (כיתות, תלמידים, הגשות),
// מחיקות, שחרור עמדה עד 5 בחודש, הגשות של עמדה שפג תוקפן, ובידוד בין מורים.
//   npm run test:classroom     (מפעיל את האמולטורים ומריץ את הקובץ הזה; צריך Java 11+)
import assert from 'node:assert/strict';
import { generateKeyPairSync, createHash, randomBytes } from 'node:crypto';
import worker from '../src/index.js';
import { _test as accessTest } from '../src/access/index.js';

const FS = process.env.FIRESTORE_EMULATOR_HOST || '127.0.0.1:8181';
const AUTH = process.env.FIREBASE_AUTH_EMULATOR_HOST || '127.0.0.1:9099';
const PROJECT = 'demo-dsviz';
const { privateKey } = generateKeyPairSync('rsa', { modulusLength: 2048 });
const ENV = {
  ACCESS: 'on', FIREBASE_PROJECT_ID: PROJECT, FIRESTORE_EMULATOR_HOST: FS, FIREBASE_AUTH_EMULATOR_HOST: AUTH,
  ADMIN_EMAILS: 'admin@test.dev', PASS_KEY: privateKey.export({ type: 'pkcs8', format: 'pem' }),
};
const realFetch = globalThis.fetch;

const BASE = 'https://w.example';
async function call(method, path, { body, token, headers = {} } = {}) {
  const h = Object.assign({}, headers);
  if (body !== undefined) h['content-type'] = 'application/json';
  if (token) h.authorization = 'Bearer ' + token;
  const res = await worker.fetch(new Request(BASE + path, { method, headers: h, body: body === undefined ? undefined : JSON.stringify(body) }), ENV, {});
  const type = res.headers.get('content-type') || '';
  return { status: res.status, body: type.includes('json') ? await res.json() : await res.text() };
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
async function signInPlugin(email, role, name, caps) {
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

await test('הגשה: קבצים שאינם קוד / גדולים מדי / יותר מ-12 — נדחים; מורה לא מגיש', async () => {
  assert.equal((await call('POST', '/v1/submit', { headers: plugin(noam), body: bankSubmit({ files: [{ path: 'a.exe', text: 'x' }] }) })).status, 400);
  assert.equal((await call('POST', '/v1/submit', { headers: plugin(noam), body: bankSubmit({ files: [{ path: 'A.java', text: 'x'.repeat(210 * 1024) }] }) })).status, 400);
  assert.equal((await call('POST', '/v1/submit', { headers: plugin(noam), body: bankSubmit({ files: Array.from({ length: 13 }, (_, i) => ({ path: `A${i}.java`, text: 'x' })) }) })).status, 400);
  const t = await signInPlugin('sara@test.dev', 'teacher', 'שרה כהן');
  assert.equal((await call('POST', '/v1/submit', { headers: plugin(t), body: bankSubmit() })).status, 403);
  assert.equal((await call('POST', '/v1/submit', { body: bankSubmit() })).status, 401);
});

let station, stationId;
await test('הגשה מעמדת מחשב: "שם המגיש" חובה; נשמרת עם תאריך תפוגה (3 ימים); שני תלמידים באותה עמדה — שתי הגשות', async () => {
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
  const days = (Date.parse(list.body.submissions[0].expiresAt) - Date.now()) / 86400000;
  assert.ok(days > 2.9 && days < 3.1, 'תפוגה אחרי 3 ימים: ' + days);
  assert.equal(list.body.submissions.find(x => x.who === 'הלל ברק').check.compiles, false);
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

console.log(`\n${passed} בדיקות עברו` + (process.exitCode ? ' — יש כישלונות' : ''));
