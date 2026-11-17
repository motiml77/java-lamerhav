// בדיקות לדיווחים על בעיות מהתוספים (src/access/reports.js) מול Firestore ו-Auth אמיתיים — באמולטור:
// מי שלח (מורה / עמדה / תלמיד / לא מחובר) והפרטים של המורה, דיווח שנשלח אחרי ניתוק (פעם אחת), הבדיקה המעמיקה, ומסוף ההנהלה.
//   npm run test:reports
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
async function signInPlugin(email, role, name) {
  const verifier = randomBytes(32).toString('base64url');
  const challenge = createHash('sha256').update(verifier).digest('base64url');
  const ex = await call('POST', '/v1/auth/exchange', { body: { idToken: await idToken(email, name || email.split('@')[0]), role, challenge } });
  assert.equal(ex.status, 200, JSON.stringify(ex.body));
  const m = machine();
  const r = await call('POST', '/v1/auth/redeem', { body: { code: ex.body.code, verifier, machine: m, machineName: 'מחשב בבית', client: 'test' } });
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
const r0 = await call('POST', '/v1/admin/teacher', { token: admin, body: { create: true, email: 'sara@test.dev', name: 'שרה כהן', school: 'אורט רחובות', seats: 5, phone: '050-1234567' } });
assert.equal(r0.status, 200, JSON.stringify(r0.body));
const code = (await call('GET', '/v1/admin/teacher?email=sara@test.dev', { token: admin })).body.teacher.code;
const rid = () => randomBytes(12).toString('base64url');
const base = (over = {}) => Object.assign({
  id: rid(), kind: 'visual-run', auto: true, title: 'ההרצה הוויזואלית לא התחילה: CS0234', message: "Program.cs(5,13): error CS0234: The type or namespace name 'GridLib' does not exist in the namespace 'Unit4'",
  app: 'Visual Studio 2022', appVersion: '17.14.45', dsvizVersion: '1.0.2', os: 'Windows 11 (10.0.26100)', context: { lang: 'csharp', project: 'classic', schoolLib: 'Unit4' },
  diag: '== DSViz-Diagnose ==\nVS 2022 Enterprise 17.14.45\nC:\\Users\\%USERNAME%\\source\\repos\\List\n', occurredAt: new Date().toISOString(),
}, over);

let fromNone, fromTeacher, fromStation, fromStudent;
await test('בלי כניסה: הדיווח מתקבל כ"לא מחובר"', async () => {
  const b = base();
  const r = await call('POST', '/v1/report', { body: b });
  assert.equal(r.status, 200, JSON.stringify(r.body));
  fromNone = b.id;
  const d = await call('GET', '/v1/admin/report?id=' + b.id, { token: admin });
  assert.equal(d.body.report.who.kind, 'none');
  assert.equal(d.body.report.teacher, null);
  assert.match(d.body.diag, /DSViz-Diagnose/);
});

await test('ממורה מחובר: השם, בית הספר, הטלפון והמייל של המורה', async () => {
  const t = await signInPlugin('sara@test.dev', 'teacher', 'שרה כהן');
  const b = base({ kind: 'manual', auto: false, title: 'הכפתור "מאגר שאלות" לא נפתח', note: 'לחצתי פעמיים ולא קרה כלום', diag: '' });
  assert.equal((await call('POST', '/v1/report', { headers: plugin(t), body: b })).status, 200);
  fromTeacher = b.id;
  const d = (await call('GET', '/v1/admin/report?id=' + b.id, { token: admin })).body;
  assert.equal(d.report.who.kind, 'teacher');
  assert.deepEqual(d.report.teacher, { email: 'sara@test.dev', name: 'שרה כהן', school: 'אורט רחובות', phone: '0501234567' });
  assert.equal(d.report.note, 'לחצתי פעמיים ולא קרה כלום');
  assert.equal(d.report.auto, false);
  assert.equal(d.diag, '');
});

await test('מעמדת מחשב: שם העמדה, ושל איזה מורה (עם הפרטים)', async () => {
  const m = machine();
  const cl = await call('POST', '/v1/station/claim', { body: { code, name: 'עמדה 7', machine: m } });
  const b = base();
  await call('POST', '/v1/report', { headers: { 'x-dsviz-access': cl.body.token, 'x-dsviz-machine': m }, body: b });
  fromStation = b.id;
  const d = (await call('GET', '/v1/admin/report?id=' + b.id, { token: admin })).body;
  assert.equal(d.report.who.kind, 'station');
  assert.equal(d.report.who.stationName, 'עמדה 7');
  assert.equal(d.report.teacher.name, 'שרה כהן');
  assert.equal(d.report.teacher.phone, '0501234567');
});

await test('מתלמיד שמשויך למורה: התלמיד, ולאיזה מורה הוא שייך', async () => {
  const s = await signInPlugin('noam@test.dev', 'student', 'נועם לוי');
  await call('POST', '/v1/student/join', { headers: plugin(s), body: { code } });
  const b = base({ kind: 'crash', title: 'שגיאה פנימית בהרצה הוויזואלית' });
  await call('POST', '/v1/report', { headers: plugin(s), body: b });
  fromStudent = b.id;
  const d = (await call('GET', '/v1/admin/report?id=' + b.id, { token: admin })).body;
  assert.deepEqual([d.report.who.kind, d.report.who.email, d.report.who.name], ['student', 'noam@test.dev', 'נועם לוי']);
  assert.equal(d.report.teacher.email, 'sara@test.dev');
});

await test('נשלח אחרי ניתוק: מתי קרה (מהמחשב) נשמר; שליחה חוזרת של אותו דיווח — נשמר פעם אחת', async () => {
  const when = new Date(Date.now() - 3 * 86400000).toISOString();
  const b = base({ occurredAt: when });
  assert.equal((await call('POST', '/v1/report', { body: b })).body.already, undefined);
  assert.equal((await call('POST', '/v1/report', { body: b })).body.already, true);
  const d = (await call('GET', '/v1/admin/report?id=' + b.id, { token: admin })).body;
  assert.equal(d.report.occurredAt, when);
  assert.ok(Date.parse(d.report.at) - Date.parse(when) > 2 * 86400000);
  // תאריך מהעתיד (שעון מחשב שגוי) — לא אחרי עכשיו
  const f = base({ occurredAt: new Date(Date.now() + 5 * 86400000).toISOString() });
  await call('POST', '/v1/report', { body: f });
  assert.ok(Date.parse((await call('GET', '/v1/admin/report?id=' + f.id, { token: admin })).body.report.occurredAt) <= Date.now());
});

await test('גבולות: מזהה לא תקין נדחה; בדיקה מעמיקה גדולה נחתכת ל-300KB; בלי כותרת — מההודעה', async () => {
  assert.equal((await call('POST', '/v1/report', { body: base({ id: 'x' }) })).status, 400);
  const big = base({ diag: 'א'.repeat(290 * 1024) + 'ב'.repeat(20 * 1024) });
  assert.equal((await call('POST', '/v1/report', { body: big })).status, 200);
  assert.equal((await call('GET', '/v1/admin/report?id=' + big.id, { token: admin })).body.diag.length, 300 * 1024);
  const nt = base({ title: '', message: 'NullReferenceException: Object reference not set' });
  await call('POST', '/v1/report', { body: nt });
  assert.match((await call('GET', '/v1/admin/report?id=' + nt.id, { token: admin })).body.report.title, /^NullReferenceException/);
});

await test('מסוף ההנהלה: רשימת חדשים, דיווחים דומים, פעמון; טופל ← עובר ל"טופלו"; רק מנהל', async () => {
  const list = (await call('GET', '/v1/admin/reports', { token: admin })).body;
  assert.ok(list.reports.length >= 7);
  assert.equal(list.open, list.reports.length);
  const st = list.reports.find(x => x.id === fromStation);
  assert.ok(st.similar >= 2, 'אותה בעיה (CS0234 ב-Visual Studio) מכמה מחשבים: ' + st.similar);
  assert.ok(list.reports.find(x => x.id === fromTeacher).similar === 0);
  const alerts = (await call('GET', '/v1/admin/alerts', { token: admin })).body.alerts.filter(a => a.kind === 'report.new');
  assert.ok(alerts.length >= 7);
  assert.ok(alerts.some(a => a.go === '#report=' + encodeURIComponent(fromStation)));
  assert.equal((await call('POST', '/v1/admin/report', { token: admin, body: { id: fromTeacher, status: 'done', adminNote: 'תוקן בגרסה 1.0.3' } })).status, 200);
  const done = (await call('GET', '/v1/admin/reports?status=done', { token: admin })).body;
  assert.deepEqual(done.reports.map(x => x.id), [fromTeacher]);
  assert.equal(done.open, list.open - 1);
  assert.equal((await call('GET', '/v1/admin/report?id=' + fromTeacher, { token: admin })).body.report.adminNote, 'תוקן בגרסה 1.0.3');
  const sim = (await call('GET', '/v1/admin/report?id=' + fromStation, { token: admin })).body.similar;
  assert.ok(sim.some(x => x.id === fromNone));
  const teacherTok = await idToken('sara@test.dev', 'שרה כהן');
  assert.equal((await call('GET', '/v1/admin/reports', { token: teacherTok })).status, 403);
  assert.equal((await call('GET', '/v1/admin/report?id=' + fromStudent, { token: teacherTok })).status, 403);
});

console.log(`\n${passed} בדיקות עברו` + (process.exitCode ? ' — יש כישלונות' : ''));
