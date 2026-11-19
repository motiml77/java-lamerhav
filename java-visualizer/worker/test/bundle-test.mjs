// הקובץ שמדביקים ב-Cloudflare (worker-bundle.js) — בדיקה שהוא עצמו עובד, ולא רק הקוד שב-src/:
// נבנה מחדש מהקוד (אחרת הבדיקה נכשלת: "הקובץ לא מעודכן"), ואז מסלול קצר מקצה לקצה מול אמולטור Firebase:
// מורה → עמדה → הגשה → עמדת הניהול → תלמיד שמצטרף ומגיש → בדיקת המערכת.
//   npm run test:bundle     (מפעיל את האמולטורים; צריך Java 11+)
import assert from 'node:assert/strict';
import { execFileSync } from 'node:child_process';
import { generateKeyPairSync, createHash, randomBytes } from 'node:crypto';
import { readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const root = join(here, '..');
const FS = process.env.FIRESTORE_EMULATOR_HOST || '127.0.0.1:8181';
const AUTH = process.env.FIREBASE_AUTH_EMULATOR_HOST || '127.0.0.1:9099';
const PROJECT = 'demo-dsviz';

let passed = 0;
async function test(name, fn) {
  try { await fn(); passed++; console.log('✓ ' + name); } catch (e) { console.error('✗ ' + name); console.error(e); process.exitCode = 1; }
}

// הקובץ שבמאגר זהה למה שנבנה עכשיו מהקוד? (מי ששינה את src/ או public/ ושכח npm run bundle — נתפס כאן)
const committed = readFileSync(join(root, 'worker-bundle.js'), 'utf8');
await test('worker-bundle.js מעודכן: זהה לבנייה מחדש מ-src/ ומ-public/', async () => {
  execFileSync('npm', ['run', '-s', 'bundle'], { cwd: root, stdio: 'pipe' });
  const fresh = readFileSync(join(root, 'worker-bundle.js'), 'utf8');
  assert.ok(fresh === committed, 'הקובץ לא מעודכן — מריצים npm run bundle ושומרים');
});

const worker = (await import('../worker-bundle.js')).default;
const { privateKey } = generateKeyPairSync('rsa', { modulusLength: 2048 });
const ENV = {
  ACCESS: 'on', FIREBASE_PROJECT_ID: PROJECT, FIRESTORE_EMULATOR_HOST: FS, FIREBASE_AUTH_EMULATOR_HOST: AUTH,
  ADMIN_EMAILS: 'admin@test.dev', PASS_KEY: privateKey.export({ type: 'pkcs8', format: 'pem' }),
};
const BASE = 'https://w.example';
async function call(method, path, { body, token, headers = {} } = {}) {
  const h = Object.assign({}, headers);
  if (body !== undefined) h['content-type'] = 'application/json';
  if (token) h.authorization = 'Bearer ' + token;
  const res = await worker.fetch(new Request(BASE + path, { method, headers: h, body: body === undefined ? undefined : JSON.stringify(body) }), ENV, { waitUntil() {} });
  const type = res.headers.get('content-type') || '';
  return { status: res.status, body: type.includes('json') ? await res.json() : await res.text() };
}
async function idToken(email, name = '') {
  const claims = { sub: 'uid-' + createHash('sha256').update(email).digest('hex').slice(0, 16), email, email_verified: true, name };
  const res = await fetch(`http://${AUTH}/identitytoolkit.googleapis.com/v1/accounts:signInWithIdp?key=fake-api-key`, {
    method: 'POST', headers: { 'content-type': 'application/json' },
    body: JSON.stringify({ postBody: 'id_token=' + encodeURIComponent(JSON.stringify(claims)) + '&providerId=google.com', requestUri: 'http://localhost', returnSecureToken: true }),
  });
  return (await res.json()).idToken;
}
const machine = () => randomBytes(16).toString('hex');
const plugin = t => ({ 'x-dsviz-access': t.token, 'x-dsviz-machine': t.machine });

await fetch(`http://${FS}/emulator/v1/projects/${PROJECT}/databases/(default)/documents`, { method: 'DELETE' });
const admin = await idToken('admin@test.dev', 'מנהל');
const sara = await idToken('sara@test.dev', 'שרה כהן');
let code, station, pupil;

await test('הקובץ עונה: /v1/health עם הגרסה, והדפים (/account, /admin, console.js) בתוכו', async () => {
  const h = await call('GET', '/v1/health');
  assert.equal(h.status, 200);
  assert.match(h.body.version, /^\d{4}-\d{2}-\d{2}\.\d+$/);
  for (const p of ['/account', '/admin', '/signin', '/welcome', '/static/console.js', '/static/register.js', '/static/app.css']) {
    const r = await call('GET', p);
    assert.equal(r.status, 200, p);
    assert.ok(String(r.body).length > 300, p);
  }
  assert.match((await call('GET', '/static/console.js')).body, /gridt/, 'טבלת ההגשות בדף של המורה');
});

await test('מורה חדש (ההנהלה), עמדת מחשב בקוד שלו, והגשה מהעמדה עם "שם המגיש"', async () => {
  assert.equal((await call('POST', '/v1/admin/teacher', { token: admin, body: { create: true, email: 'sara@test.dev', name: 'שרה כהן', school: 'תיכון', seats: 3 } })).status, 200);
  code = (await call('GET', '/v1/admin/teacher?email=sara@test.dev', { token: admin })).body.teacher.code;
  const m = machine();
  const cl = await call('POST', '/v1/station/claim', { body: { code, name: 'עמדה 1', machine: m, client: 'bundle-test' } });
  assert.equal(cl.body.status, 'ok', JSON.stringify(cl.body));
  assert.ok(cl.body.features.includes('submit'));
  station = { token: cl.body.token, machine: m };
  const s = await call('POST', '/v1/submit', { headers: plugin(station), body: { who: 'נועם', exercise: { source: 'bank', bankId: 'q1', title: 'תרגיל 1' }, files: [{ path: 'A.java', text: 'class A {}' }],
    check: { ran: true, compiles: false, error: 'A.java, שורה 1: חסר ;\nA.java, שורה 2: חסר }', errors: 2 } } });
  assert.equal(s.status, 200, JSON.stringify(s.body));
});

await test('תלמיד: כניסה, הרשמה, הצטרפות בקוד המורה, והגשה — מופיע אצל המורה עם "חדש" ובטבלת התרגילים; המורה נותן ציון', async () => {
  const verifier = randomBytes(32).toString('base64url');
  const ex = await call('POST', '/v1/auth/exchange', { body: { idToken: await idToken('noa@test.dev', 'נועה'), role: 'student', challenge: createHash('sha256').update(verifier).digest('base64url') } });
  const m = machine();
  const r = await call('POST', '/v1/auth/redeem', { body: { code: ex.body.code, verifier, machine: m, machineName: 'בית', client: 'bundle-test', caps: ['register'] } });
  assert.equal(r.body.status, 'register', JSON.stringify(r.body));
  pupil = { token: r.body.token, machine: m };
  assert.equal((await call('POST', '/v1/student/register', { headers: plugin(pupil), body: { fullName: 'נועה לוי', phone: '050-1234567' } })).status, 200);
  assert.equal((await call('POST', '/v1/student/join', { headers: plugin(pupil), body: { code } })).body.status, 'ok');
  const s = await call('POST', '/v1/submit', { headers: plugin(pupil), body: { exercise: { source: 'bank', bankId: 'q1', title: 'תרגיל 1', topic: 'תור' }, files: [{ path: 'B.java', text: 'class B {}' }],
    check: { ran: true, compiles: true, output: '1', expected: '1', outputMatch: true } } });
  assert.equal(s.status, 200, JSON.stringify(s.body));
  const c = (await call('GET', '/v1/me/console', { token: sara })).body;
  const st = c.students.find(x => x.email === 'noa@test.dev');
  assert.equal(st.name, 'נועה לוי');
  assert.equal(st.unseen, 1);
  assert.deepEqual(st.work.map(w => [w.title, w.result, w.seen]), [['תרגיל 1', 'ok', false]]);
  assert.equal(c.stations[0].unseen, 1);
  const one = await call('GET', '/v1/me/submission?id=' + st.work[0].id, { token: sara });
  assert.equal(one.body.files[0].text, 'class B {}');
  assert.equal((await call('GET', '/v1/me/console', { token: sara })).body.students[0].unseen, 0, 'נפתחה — כבר לא חדשה');
  // ציון להגשה של התלמיד — נשמר, ומופיע בתקציר
  assert.equal((await call('POST', '/v1/me/submission/grade', { token: sara, body: { id: st.work[0].id, grade: 90 } })).body.grade, 90);
  assert.equal((await call('GET', '/v1/me/console', { token: sara })).body.students[0].work[0].grade, 90);
  const sub = (await call('GET', '/v1/me/submissions?station=' + c.stations[0].id, { token: sara })).body.submissions[0];
  assert.equal((await call('POST', '/v1/me/submission/grade', { token: sara, body: { id: sub.id, grade: 90 } })).status, 400, 'הגשה מעמדה — בלי ציון');
  assert.equal(sub.check.errors, 2, 'מספר השגיאות נשמר');
  assert.equal(sub.check.error.split('\n').length, 2, 'כל שגיאה בשורה שלה');
});

await test('בדיקת המערכת מהקובץ: שני החלקים בלי שורה אדומה', async () => {
  for (const part of [1, 2]) {
    const r = await call('GET', '/v1/admin/selftest?part=' + part, { token: admin });
    assert.equal(r.status, 200, JSON.stringify(r.body));
    assert.deepEqual(r.body.checks.filter(x => x.state === 'fail'), []);
  }
});

console.log(`\n${passed} בדיקות עברו` + (process.exitCode ? ' — יש כישלונות' : ''));
