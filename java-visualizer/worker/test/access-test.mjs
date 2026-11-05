// בדיקות לכניסה למאגר (מורים, עמדות, תלמידים, הנהלה, תשלום, נעילת Gemini) מול Firestore ו-Auth אמיתיים — באמולטור.
//   npm run test:access     (מפעיל את האמולטורים ומריץ את הקובץ הזה; צריך Java 11+)
import assert from 'node:assert/strict';
import { generateKeyPairSync, createVerify, createHash, randomBytes } from 'node:crypto';
import worker from '../src/index.js';
import { _test as accessTest } from '../src/access/index.js';

const FS = process.env.FIRESTORE_EMULATOR_HOST || '127.0.0.1:8181';
const AUTH = process.env.FIREBASE_AUTH_EMULATOR_HOST || '127.0.0.1:9099';
const PROJECT = 'demo-dsviz';
const { privateKey, publicKey } = generateKeyPairSync('rsa', { modulusLength: 2048 });
const PEM = privateKey.export({ type: 'pkcs8', format: 'pem' });

const ENV = {
  ACCESS: 'on', FIREBASE_PROJECT_ID: PROJECT, FIRESTORE_EMULATOR_HOST: FS, FIREBASE_AUTH_EMULATOR_HOST: AUTH,
  ADMIN_EMAILS: 'admin@test.dev', PASS_KEY: PEM, PAY_PROVIDER: 'demo', GEMINI_API_KEY: 'k', MODELS: 'model-a',
};

// Gemini — מדומה; כל השאר (האמולטורים) — אמיתי
const realFetch = globalThis.fetch;
let geminiCalls = 0;
globalThis.fetch = async (url, init) => {
  if (String(url).includes('generativelanguage')) {
    geminiCalls++;
    const ex = { isExercise: true, title: 't', summary: '', topic: 'stacks', mainClass: 'M', imports: [], classes: [], givenCode: [], otherQuestions: [], mainCode: '',
      tasks: [{ label: 'א', kind: 'method', className: 'M', isStatic: true, returnType: 'int', methodName: 'f', parameters: [], instructions: 'x' }] };
    return new Response(JSON.stringify({ candidates: [{ finishReason: 'STOP', content: { parts: [{ text: JSON.stringify(ex) }] } }] }), { status: 200 });
  }
  return realFetch(url, init);
};

const BASE = 'https://w.example';
async function call(method, path, { body, token, headers = {}, env = ENV } = {}) {
  const h = Object.assign({}, headers);
  if (body !== undefined) h['content-type'] = 'application/json';
  if (token) h.authorization = 'Bearer ' + token;
  const res = await worker.fetch(new Request(BASE + path, { method, headers: h, body: body === undefined ? undefined : JSON.stringify(body) }), env, {});
  const type = res.headers.get('content-type') || '';
  return { status: res.status, body: type.includes('json') ? await res.json() : await res.text(), headers: res.headers };
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
const pkce = () => {
  const verifier = randomBytes(32).toString('base64url');
  return { verifier, challenge: createHash('sha256').update(verifier).digest('base64url') };
};

function readPass(pass) {
  const [text, sig] = pass.split('.');
  const v = createVerify('RSA-SHA256');
  v.update(text);
  assert.ok(v.verify(publicKey, Buffer.from(sig, 'base64url')), 'pass signature');
  return JSON.parse(Buffer.from(text, 'base64url').toString('utf8'));
}

/** כניסה עם Google מתוך התוכנה: exchange (הדף בדפדפן) ואז redeem (התוסף) */
async function signIn(email, role, m = machine(), name = 'מחשב בדיקה') {
  const p = pkce();
  const ex = await call('POST', '/v1/auth/exchange', { body: { idToken: await idToken(email, email.split('@')[0]), role, challenge: p.challenge } });
  assert.equal(ex.status, 200, JSON.stringify(ex.body));
  const r = await call('POST', '/v1/auth/redeem', { body: { code: ex.body.code, verifier: p.verifier, machine: m, machineName: name, client: 'test' } });
  assert.equal(r.status, 200, JSON.stringify(r.body));
  return Object.assign(r.body, { machine: m });
}

let passed = 0;
async function test(name, fn) {
  try { await fn(); passed++; console.log('✓ ' + name); } catch (e) { console.error('✗ ' + name); console.error(e); process.exitCode = 1; }
}

await realFetch(`http://${FS}/emulator/v1/projects/${PROJECT}/databases/(default)/documents`, { method: 'DELETE' });
accessTest.reset();
const admin = await idToken('admin@test.dev', 'מנהל');

await test('כבוי (ACCESS לא on): config אומר enabled:false, והנתיבים לא קיימים', async () => {
  const env = Object.assign({}, ENV, { ACCESS: '' });
  assert.equal((await call('GET', '/v1/access/config', { env })).body.enabled, false);
  assert.equal((await call('POST', '/v1/station/claim', { env, body: {} })).status, 404);
  assert.equal((await call('GET', '/admin', { env })).status, 404);
});

await test('הנהלה: רק כתובת שב-ADMIN_EMAILS; בלי כניסה — 401', async () => {
  assert.equal((await call('GET', '/v1/admin/overview')).status, 401);
  const other = await idToken('teacher1@test.dev');
  const r = await call('GET', '/v1/admin/overview', { token: other });
  assert.equal(r.status, 403);
  assert.equal(r.body.code, 'admin');
  assert.equal((await call('GET', '/v1/admin/overview', { token: admin })).status, 200);
});

let code1;
await test('הנהלה מוסיפה מורה עם 2 עמדות → קוד מורה; אותו מורה פעמיים — 409', async () => {
  const r = await call('POST', '/v1/admin/teacher', { token: admin, body: { create: true, email: 'Teacher1@Test.dev', name: 'שרה כהן', school: 'אורט', seats: 2 } });
  assert.equal(r.status, 200, JSON.stringify(r.body));
  const again = await call('POST', '/v1/admin/teacher', { token: admin, body: { create: true, email: 'teacher1@test.dev', seats: 2 } });
  assert.equal(again.status, 409);
  const t = await call('GET', '/v1/admin/teacher?email=teacher1@test.dev', { token: admin });
  assert.equal(t.body.teacher.seats, 2);
  assert.match(t.body.teacher.code, /^[2-9A-HJKMNP-Z]{4}$/);
  code1 = t.body.teacher.code;
});

const m1 = machine(), m2 = machine(), m3 = machine();
let st1;
await test('עמדת מחשב: קוד שגוי / בלי שם → הודעה; קוד נכון (גם באותיות קטנות ובלי מקף) → כרטיס + אישור חתום', async () => {
  assert.equal((await call('POST', '/v1/station/claim', { body: { code: 'AAAA', name: 'x', machine: m1 } })).body.status, 'bad-code');
  assert.equal((await call('POST', '/v1/station/claim', { body: { code: code1, name: '', machine: m1 } })).status, 400);
  const r = await call('POST', '/v1/station/claim', { body: { code: code1.replace('-', '').toLowerCase(), name: 'מעבדה 2 · עמדה 1', machine: m1, client: 'test' } });
  assert.equal(r.body.status, 'ok', JSON.stringify(r.body));
  assert.match(r.body.token, /^s\./);
  const p = readPass(r.body.pass);
  assert.equal(p.kind, 'station');
  assert.equal(p.mid, m1);
  assert.equal(p.teacher, 'שרה כהן');
  assert.ok(p.exp - p.iat > 5 * 365 * 86400, 'אחרי הכניסה הראשונה — בלי הגבלה בלי אינטרנט');
  assert.deepEqual([r.body.teacher.used, r.body.teacher.seats], [1, 2]);
  st1 = r.body.token;
});

await test('עמדות: השנייה נכנסת (2/2), השלישית — "אין עוד היתרים"; אותו מחשב שמתחבר שוב — בלי היתר נוסף', async () => {
  assert.equal((await call('POST', '/v1/station/claim', { body: { code: code1, name: 'עמדה 2', machine: m2 } })).body.status, 'ok');
  const full = await call('POST', '/v1/station/claim', { body: { code: code1, name: 'עמדה 3', machine: m3 } });
  assert.equal(full.body.status, 'full');
  assert.deepEqual([full.body.teacher.used, full.body.teacher.seats], [2, 2]);
  const again = await call('POST', '/v1/station/claim', { body: { code: code1, name: 'עמדה 1 (מחדש)', machine: m1 } });
  assert.equal(again.body.status, 'ok');
  assert.notEqual(again.body.token, st1);
  assert.equal((await call('POST', '/v1/access/check', { body: { token: st1, machine: m1 } })).body.status, 'invalid', 'הכרטיס הישן הוחלף');
  st1 = again.body.token;
  const t = await call('GET', '/v1/admin/teacher?email=teacher1@test.dev', { token: admin });
  assert.equal(t.body.teacher.used, 2);
  assert.equal(t.body.stations.length, 2);
});

await test('שתי עמדות על ההיתר האחרון באותו רגע — רק אחת נכנסת (טרנזקציה)', async () => {
  await call('POST', '/v1/admin/teacher', { token: admin, body: { create: true, email: 'race@test.dev', name: 'מירוץ', seats: 1 } });
  const code = (await call('GET', '/v1/admin/teacher?email=race@test.dev', { token: admin })).body.teacher.code;
  const results = await Promise.all(Array.from({ length: 6 }, (_, i) => call('POST', '/v1/station/claim', { body: { code, name: 'עמדה ' + i, machine: machine() } })));
  const statuses = results.map(r => r.body.status);
  assert.equal(statuses.filter(s => s === 'ok').length, 1, statuses.join(','));
  assert.equal(statuses.filter(s => s === 'full').length, 5, statuses.join(','));
  assert.equal((await call('GET', '/v1/admin/teacher?email=race@test.dev', { token: admin })).body.teacher.used, 1);
});

await test('בדיקת כרטיס: אותו מחשב — ok; כרטיס שהועתק למחשב אחר — לא תקף', async () => {
  const ok = await call('POST', '/v1/access/check', { body: { token: st1, machine: m1 } });
  assert.equal(ok.body.status, 'ok');
  assert.equal(readPass(ok.body.pass).mid, m1);
  assert.equal((await call('POST', '/v1/access/check', { body: { token: st1, machine: m3 } })).body.status, 'invalid');
  assert.equal((await call('POST', '/v1/access/check', { body: { token: 'garbage' } })).body.status, 'invalid');
});

await test('הנהלה משחררת עמדה → העמדה ננעלת, ההיתר חוזר, ומחשב חדש נכנס', async () => {
  const list = (await call('GET', '/v1/admin/teacher?email=teacher1@test.dev', { token: admin })).body.stations;
  const s2 = list.find(s => s.name === 'עמדה 2');
  assert.equal((await call('POST', '/v1/admin/station/release', { token: admin, body: { id: s2.id } })).status, 200);
  const t = (await call('GET', '/v1/admin/teacher?email=teacher1@test.dev', { token: admin })).body;
  assert.equal(t.teacher.used, 1);
  assert.equal(t.stations.length, 1);
  assert.equal((await call('POST', '/v1/station/claim', { body: { code: code1, name: 'עמדה 3', machine: m3 } })).body.status, 'ok');
});

let teacherTok;
await test('מורה נכנס עם Google מתוך התוכנה: exchange → redeem עם ה-verifier → כרטיס + אישור + הקוד והעמדות', async () => {
  const r = await signIn('teacher1@test.dev', 'teacher');
  assert.equal(r.status, 'ok', JSON.stringify(r));
  assert.match(r.token, /^u\./);
  assert.equal(readPass(r.pass).kind, 'teacher');
  assert.equal(r.teacher.code, code1);
  assert.deepEqual([r.teacher.used, r.teacher.seats], [2, 2]);
  teacherTok = r;
});

await test('קוד כניסה: verifier שגוי / שימוש חוזר → נדחה', async () => {
  const p = pkce();
  const ex = await call('POST', '/v1/auth/exchange', { body: { idToken: await idToken('teacher1@test.dev'), role: 'teacher', challenge: p.challenge } });
  const bad = await call('POST', '/v1/auth/redeem', { body: { code: ex.body.code, verifier: 'wrong', machine: machine() } });
  assert.equal(bad.status, 400);
  const reuse = await call('POST', '/v1/auth/redeem', { body: { code: ex.body.code, verifier: p.verifier, machine: machine() } });
  assert.equal(reuse.status, 400, 'הקוד נמחק אחרי הניסיון הראשון');
  assert.equal((await call('POST', '/v1/auth/exchange', { body: { idToken: 'x.y.z', role: 'teacher', challenge: p.challenge } })).status, 401);
});

await test('חשבון שעוד לא מורה: not-teacher → בקשת הרשאה → ההנהלה מאשרת → נכנס כמורה', async () => {
  const r = await signIn('newteacher@test.dev', 'teacher');
  assert.equal(r.status, 'not-teacher');
  assert.equal(r.token, undefined);
  const tok = await idToken('newteacher@test.dev', 'דוד');
  const req = await call('POST', '/v1/me/request', { token: tok, body: { kind: 'teacher', school: 'תיכון עירוני ד׳', computers: 25 } });
  assert.equal(req.status, 200, JSON.stringify(req.body));
  const dup = await call('POST', '/v1/me/request', { token: tok, body: { kind: 'teacher', school: 'x' } });
  assert.equal(dup.body.already, true);
  const ov = (await call('GET', '/v1/admin/overview', { token: admin })).body;
  const open = ov.requests.find(x => x.email === 'newteacher@test.dev');
  assert.equal(open.kind, 'teacher');
  assert.equal(open.school, 'תיכון עירוני ד׳');
  const ap = await call('POST', '/v1/admin/request', { token: admin, body: { id: open.id, decision: 'approve', seats: 25 } });
  assert.equal(ap.status, 200, JSON.stringify(ap.body));
  const again = await signIn('newteacher@test.dev', 'teacher');
  assert.equal(again.status, 'ok');
  assert.equal(again.teacher.seats, 25);
});

await test('מורה: אין "בקשת עמדות" — קונה חבילה; אחרי התשלום העמדות מתווספות לבד, ופרטי המשלם נשמרים; שחרור מחשב — בבקשה להנהלה', async () => {
  const tok = await idToken('teacher1@test.dev');
  const me = (await call('GET', '/v1/me', { token: tok })).body;
  assert.equal(me.teacher.code, code1);
  assert.equal(me.teacher.stations.length, 2);
  assert.deepEqual(me.packages, [], 'אין חבילות למכירה עד שההנהלה קובעת מחיר');
  assert.equal((await call('POST', '/v1/me/request', { token: tok, body: { kind: 'seats', amount: 3 } })).status, 400);
  assert.equal((await call('POST', '/v1/pay/start', { token: tok, body: { kind: 'seats', package: 0 } })).status, 400);
  await call('POST', '/v1/admin/settings', { token: admin, body: { packages: [{ seats: 10, price: 300 }, { seats: 20, price: 500 }, { seats: 30, price: 0 }] } });
  const packs = (await call('GET', '/v1/me', { token: tok })).body.packages;
  assert.deepEqual(packs.map(p => [p.seats, p.price]), [[10, 300], [20, 500]]);
  // תלמיד לא קונה עמדות
  assert.equal((await call('POST', '/v1/pay/start', { token: await idToken('pupil@test.dev'), body: { kind: 'seats', package: 0 } })).status, 403);
  const start = await call('POST', '/v1/pay/start', { token: tok, body: { kind: 'seats', package: 1 } });
  assert.equal(start.status, 200, JSON.stringify(start.body));
  await call('POST', '/v1/pay/notify/demo', { body: { id: start.body.id, status: 'paid', amount: 500, payer: { name: 'שרה כהן', phone: '050-1234567', email: 'sara@x.com' } } });
  await call('POST', '/v1/pay/notify/demo', { body: { id: start.body.id, status: 'paid', amount: 500 } });   // הודעה כפולה
  let t = (await call('GET', '/v1/admin/teacher?email=teacher1@test.dev', { token: admin })).body;
  assert.equal(t.teacher.seats, 22, 'נוספו 20 עמדות, פעם אחת');
  const pay = (await call('GET', '/v1/admin/payments', { token: admin })).body.payments.find(p => p.id === start.body.id);
  assert.deepEqual([pay.kind, pay.seats, pay.payerName, pay.payerPhone, pay.payerEmail, pay.status], ['seats', 20, 'שרה כהן', '050-1234567', 'sara@x.com', 'paid']);
  assert.ok((await call('GET', '/v1/admin/log', { token: admin })).body.log.some(l => l.action === 'payment.seats' && /2 ← 22/.test(l.detail)));
  // החזר → העמדות יורדות
  await call('POST', '/v1/pay/notify/demo', { body: { id: start.body.id, status: 'refunded' } });
  t = (await call('GET', '/v1/admin/teacher?email=teacher1@test.dev', { token: admin })).body;
  assert.equal(t.teacher.seats, 2);
  // שחרור מחשב — רק ההנהלה; המורה מבקש
  const other = me.teacher.stations.find(s => s.name === 'עמדה 3');
  assert.equal((await call('POST', '/v1/me/request', { token: tok, body: { kind: 'release', stationId: other.id } })).status, 200);
  assert.equal((await call('POST', '/v1/admin/teacher', { token: tok, body: { email: 'teacher1@test.dev', seats: 99 } })).status, 403);
  assert.equal((await call('POST', '/v1/admin/station/release', { token: tok, body: { id: other.id } })).status, 403);
  const reqs = (await call('GET', '/v1/admin/requests', { token: admin })).body.requests.filter(r => r.email === 'teacher1@test.dev');
  for (const r of reqs) assert.equal((await call('POST', '/v1/admin/request', { token: admin, body: { id: r.id, decision: 'approve' } })).status, 200);
  t = (await call('GET', '/v1/admin/teacher?email=teacher1@test.dev', { token: admin })).body;
  assert.equal(t.teacher.used, 1);
  assert.ok((await call('GET', '/v1/me', { token: tok })).body.requests.every(r => r.status === 'approved'));
});

await test('קוד המורה קבוע: אין "קוד חדש" (לא למורה ולא בהנהלה)', async () => {
  const tok = await idToken('teacher1@test.dev');
  assert.equal((await call('POST', '/v1/me/new-code', { token: tok, body: {} })).status, 404);
  assert.equal((await call('POST', '/v1/admin/teacher/new-code', { token: admin, body: { email: 'teacher1@test.dev' } })).status, 404);
  assert.equal((await call('GET', '/v1/me', { token: tok })).body.teacher.code, code1);
  assert.equal((await call('POST', '/v1/access/check', { body: { token: st1, machine: m1 } })).body.status, 'ok');
});

await test('השבתת מורה: העמדות שלו והכניסה שלו ננעלות; הפעלה מחדש פותחת', async () => {
  await call('POST', '/v1/admin/teacher', { token: admin, body: { email: 'teacher1@test.dev', active: false } });
  assert.equal((await call('POST', '/v1/access/check', { body: { token: st1, machine: m1 } })).body.status, 'blocked');
  assert.equal((await call('POST', '/v1/access/check', { body: { token: teacherTok.token, machine: teacherTok.machine } })).body.status, 'blocked');
  assert.equal((await call('POST', '/v1/station/claim', { body: { code: code1, name: 'x', machine: machine() } })).body.status, 'blocked');
  await call('POST', '/v1/admin/teacher', { token: admin, body: { email: 'teacher1@test.dev', active: true } });
  assert.equal((await call('POST', '/v1/access/check', { body: { token: st1, machine: m1 } })).body.status, 'ok');
});

let student;
await test('תלמיד: נכנס → "צריך לשלם" (עם כרטיס); המחיר נקבע; תשלום → המאגר פתוח עד סוף השנה; הודעה כפולה — פעם אחת', async () => {
  student = await signIn('pupil@test.dev', 'student');
  assert.equal(student.status, 'pay');
  assert.match(student.token, /^u\./);
  assert.equal(student.pass, undefined);
  assert.equal(student.canPay, false, 'אין מחיר עדיין');
  const tok = await idToken('pupil@test.dev');
  assert.equal((await call('POST', '/v1/pay/start', { token: tok, body: {} })).status, 503);
  await call('POST', '/v1/admin/settings', { token: admin, body: { price: 60, accessUntil: '2027-08-31' } });
  const start = await call('POST', '/v1/pay/start', { token: tok, body: {} });
  assert.equal(start.status, 200, JSON.stringify(start.body));
  assert.match(start.body.url, /\/pay\/demo\?id=/);
  const pageRes = await call('GET', new URL(start.body.url).pathname + new URL(start.body.url).search);
  assert.match(pageRes.body, /60 ₪/);
  assert.equal((await call('POST', '/v1/access/check', { body: { token: student.token, machine: student.machine } })).body.status, 'pay');
  const bad = await call('POST', '/v1/pay/notify/demo', { body: { id: start.body.id, status: 'paid', amount: 1 } });
  assert.equal(bad.status, 200);
  assert.equal((await call('POST', '/v1/access/check', { body: { token: student.token, machine: student.machine } })).body.status, 'pay', 'סכום לא נכון לא פותח');
  const start2 = await call('POST', '/v1/pay/start', { token: tok, body: {} });
  for (let i = 0; i < 2; i++) await call('POST', '/v1/pay/notify/demo', { body: { id: start2.body.id, status: 'paid', amount: 60 } });
  const ok = await call('POST', '/v1/access/check', { body: { token: student.token, machine: student.machine } });
  assert.equal(ok.body.status, 'ok');
  const p = readPass(ok.body.pass);
  assert.equal(p.kind, 'student');
  assert.equal(p.until, '2027-08-31');
  const pays = (await call('GET', '/v1/admin/payments', { token: admin })).body.payments.filter(x => x.email === 'pupil@test.dev');
  assert.equal(pays.filter(x => x.status === 'paid').length, 1);
  assert.equal(pays.filter(x => x.status === 'mismatch').length, 1);
  const log = (await call('GET', '/v1/admin/log', { token: admin })).body.log;
  assert.equal(log.filter(l => l.action === 'payment.paid').length, 1);
  // החזר כספי → נסגר
  await call('POST', '/v1/pay/notify/demo', { body: { id: start2.body.id, status: 'refunded' } });
  assert.equal((await call('POST', '/v1/access/check', { body: { token: student.token, machine: student.machine } })).body.status, 'pay');
});

await test('הענקה ידנית מההנהלה (רשימה) → פתוח; ביטול → נסגר', async () => {
  const g = await call('POST', '/v1/admin/student', { token: admin, body: { email: 'pupil@test.dev, other@test.dev', until: '2027-08-31', note: 'הסכם' } });
  assert.equal(g.body.count, 2);
  assert.equal((await call('POST', '/v1/access/check', { body: { token: student.token, machine: student.machine } })).body.status, 'ok');
  const list = (await call('GET', '/v1/admin/students', { token: admin })).body.students;
  assert.equal(list.find(s => s.email === 'other@test.dev').source, 'manual');
  await call('POST', '/v1/admin/student', { token: admin, body: { email: 'pupil@test.dev', mode: 'revoke' } });
  assert.equal((await call('POST', '/v1/access/check', { body: { token: student.token, machine: student.machine } })).body.status, 'pay');
  await call('POST', '/v1/admin/student', { token: admin, body: { email: 'pupil@test.dev', until: '2027-08-31' } });
});

await test('מגבלת מחשבים לתלמיד: מחשב רביעי → "devices"; אותו מחשב שוב — מחליף את הכרטיס הישן; ניתוק עצמי פותח מקום', async () => {
  const a = await signIn('limit@test.dev', 'student');
  await signIn('limit@test.dev', 'student');
  await signIn('limit@test.dev', 'student');
  const fourth = await signIn('limit@test.dev', 'student');
  assert.equal(fourth.status, 'devices');
  assert.equal(fourth.devices.length, 3);
  const again = await signIn('limit@test.dev', 'student', a.machine);
  assert.notEqual(again.status, 'devices');
  assert.equal((await call('POST', '/v1/access/check', { body: { token: a.token, machine: a.machine } })).body.status, 'revoked');
  const tok = await idToken('limit@test.dev');
  const me = (await call('GET', '/v1/me', { token: tok })).body;
  assert.equal(me.devices.length, 3);
  await call('POST', '/v1/me/devices/revoke', { token: tok, body: { id: me.devices[0].id } });
  assert.notEqual((await signIn('limit@test.dev', 'student')).status, 'devices');
});

function exercise(headers = {}) {
  const fd = new FormData();
  fd.append('meta', JSON.stringify({ texts: [{ name: 'a.txt', text: 'כתבו פעולה' }] }));
  return worker.fetch(new Request(BASE + '/v1/exercise', { method: 'POST', body: fd, headers }), ENV, {});
}

await test('שליחה ל-Gemini: מתג כבוי — פתוח לכולם; דולק — רק מחוברים, תלמיד שלא שילם נחסם, ומנה חודשית לפי מורה', async () => {
  let r = await exercise();
  assert.equal(r.status, 200, 'מתג כבוי');
  await call('POST', '/v1/admin/settings', { token: admin, body: { requireLoginForGemini: true, monthlyPerSeat: 1 } });
  r = await exercise();
  assert.equal(r.status, 401);
  assert.equal((await r.json()).code, 'login');
  r = await exercise({ 'x-dsviz-access': st1, 'x-dsviz-machine': m1 });
  assert.equal(r.status, 200, await r.clone().text());
  r = await exercise({ 'x-dsviz-access': st1, 'x-dsviz-machine': m1 });
  assert.equal(r.status, 200);
  r = await exercise({ 'x-dsviz-access': st1, 'x-dsviz-machine': m1 });
  assert.equal(r.status, 429);
  assert.equal((await r.json()).code, 'monthly');
  const unpaid = await signIn('unpaid@test.dev', 'student');
  r = await exercise({ 'x-dsviz-access': unpaid.token, 'x-dsviz-machine': unpaid.machine });
  assert.equal(r.status, 403);
  assert.equal((await r.json()).code, 'pay');
  r = await exercise({ 'x-dsviz-access': student.token, 'x-dsviz-machine': student.machine });
  assert.equal(r.status, 200);
  await call('POST', '/v1/admin/settings', { token: admin, body: { requireLoginForGemini: false } });
});

await test('התנתקות: הכרטיס מבוטל בשרת', async () => {
  const s = await signIn('bye@test.dev', 'student');
  await call('POST', '/v1/access/logout', { body: { token: s.token } });
  assert.equal((await call('POST', '/v1/access/check', { body: { token: s.token, machine: s.machine } })).body.status, 'revoked');
});

await test('סקירה, יומן והגדרות בהנהלה', async () => {
  const ov = (await call('GET', '/v1/admin/overview', { token: admin })).body;
  assert.ok(ov.counts.teachers >= 3);
  assert.ok(ov.counts.used >= 2);
  assert.ok(ov.full.some(t => t.email === 'race@test.dev'), 'מורה שכל העמדות שלו בשימוש');
  assert.ok(ov.recent.length > 5);
  const st = (await call('GET', '/v1/admin/settings', { token: admin })).body;
  assert.equal(st.settings.price, 60);
  assert.equal(st.passKey, true);
  assert.deepEqual(st.admins, ['admin@test.dev']);
  assert.equal((await call('POST', '/v1/admin/settings', { token: admin, body: { offlineDays: 999 } })).status, 400);
  const key = (await call('GET', '/v1/access/key')).body.spki;
  assert.equal(key, publicKey.export({ type: 'spki', format: 'der' }).toString('base64'));
});

await test('הדפים בדפדפן נטענים (signin / admin) עם כותרות אבטחה', async () => {
  const r = await call('GET', '/signin');
  assert.equal(r.status, 200);
  assert.match(r.headers.get('content-type'), /text\/html/);
  assert.equal(r.headers.get('x-frame-options'), 'DENY');
});

console.log(`\n${passed} בדיקות עברו`);
