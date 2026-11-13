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
  if (/paypal\.com/.test(String(url))) return fakePayPal(String(url), init || {});
  if (String(url) === 'https://api.resend.com/emails') { MAILS.push({ auth: (init.headers || {}).authorization, body: JSON.parse(init.body) }); return new Response('{"id":"m1"}', { status: 200 }); }
  return realFetch(url, init);
};

// PayPal — מדומה: הזמנות, אישור של המשלם (approve), גבייה, שאילתות, ואימות חתימה של webhook (חתימה "good" בלבד)
const MAILS = [];
const PP = { orders: new Map(), captures: new Map(), seq: 0, calls: [], tamper: null };
function fakePayPal(url, init) {
  const u = new URL(url), path = u.pathname, method = (init.method || 'GET').toUpperCase();
  const body = init.body ? (() => { try { return JSON.parse(init.body); } catch (e) { return null; } })() : null;
  const json = (status, o) => new Response(JSON.stringify(o), { status, headers: { 'content-type': 'application/json' } });
  PP.calls.push(method + ' ' + path);
  const auth = String((init.headers || {}).authorization || '');
  if (path === '/v1/oauth2/token') return auth === 'Basic ' + btoa('cid:sec') ? json(200, { access_token: 'pp-token', expires_in: 3600 }) : json(401, {});
  if (auth !== 'Bearer pp-token') return json(401, {});
  if (method === 'POST' && path === '/v2/checkout/orders') {
    const id = 'ORDER' + String(++PP.seq).padStart(6, '0') + 'XY', unit = body.purchase_units[0];
    PP.orders.set(id, { id, unit, approved: false, captured: null, ctx: body.payment_source.paypal.experience_context });
    return json(201, { id, status: 'PAYER_ACTION_REQUIRED', links: [{ rel: 'payer-action', href: (u.host.includes('sandbox') ? 'https://www.sandbox.paypal.com' : 'https://www.paypal.com') + '/checkoutnow?token=' + id }] });
  }
  const view = o => {
    const cap = PP.captures.get(o.captured);
    return { id: o.id, status: 'COMPLETED', payer: { name: { given_name: 'דנה', surname: 'לוי' }, email_address: 'dana@paypal.test', phone: { phone_number: { national_number: '0501234567' } } },
      purchase_units: [{ reference_id: o.unit.reference_id, payments: { captures: [cap] } }] };
  };
  let m = /^\/v2\/checkout\/orders\/([A-Z0-9]+)\/capture$/.exec(path);
  if (m && method === 'POST') {
    const o = PP.orders.get(m[1]);
    if (!o) return json(404, { name: 'RESOURCE_NOT_FOUND' });
    if (o.captured) return json(422, { name: 'UNPROCESSABLE_ENTITY', details: [{ issue: 'ORDER_ALREADY_CAPTURED' }] });
    if (!o.approved) return json(422, { name: 'UNPROCESSABLE_ENTITY', details: [{ issue: 'ORDER_NOT_APPROVED' }] });
    const capId = 'CAP' + String(++PP.seq).padStart(8, '0') + 'Z';
    const cap = Object.assign({ id: capId, status: 'COMPLETED', amount: Object.assign({}, o.unit.amount), custom_id: o.unit.custom_id, supplementary_data: { related_ids: { order_id: o.id } } }, PP.tamper || {});
    PP.captures.set(capId, cap);
    o.captured = capId;
    return json(201, view(o));
  }
  m = /^\/v2\/checkout\/orders\/([A-Z0-9]+)$/.exec(path);
  if (m && method === 'GET') { const o = PP.orders.get(m[1]); return o && o.captured ? json(200, view(o)) : json(404, {}); }
  m = /^\/v2\/payments\/captures\/([A-Z0-9]+)$/.exec(path);
  if (m && method === 'GET') { const c = PP.captures.get(m[1]); return c ? json(200, c) : json(404, {}); }
  if (path === '/v1/notifications/verify-webhook-signature') return json(200, { verification_status: body && body.transmission_sig === 'good' && body.webhook_id === 'WH-1' ? 'SUCCESS' : 'FAILURE' });
  return json(404, {});
}

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

await test('קוד שגוי ביומן: הקוד מוסתר בחלקו, מספר הניסיונות ומקור; קוד שהוקלד במקלדת עברית — מתקבל', async () => {
  assert.equal((await call('POST', '/v1/station/claim', { body: { code: 'ZZZZ', name: 'מעבדה 9 · עמדה 3', machine: m1 } })).body.status, 'bad-code');
  assert.equal((await call('POST', '/v1/station/claim', { body: { code: 'שדגכ', name: 'מעבדה 9 · עמדה 3', machine: m1 } })).body.status, 'bad-code');
  const log = (await call('GET', '/v1/admin/log?limit=50', { token: admin })).body.log.filter(l => l.action === 'station.bad-code');
  const zz = log.find(l => /ZZZ\?/.test(l.detail));
  assert.ok(zz, 'הקוד מוסתר בחלקו: ' + JSON.stringify(log.map(l => l.detail)));
  assert.ok(!/ZZZZ/.test(zz.detail), 'הקוד המלא לא נשמר');
  assert.match(zz.detail, /מקור #[0-9a-f]{4}/);
  assert.equal(zz.actor, 'מחשב: מעבדה 9 · עמדה 3');
  // "שדגכ" במקלדת עברית = ASDF — 4 אותיות חוקיות אבל לא קוד של מורה
  assert.ok(log.some(l => /ASD\?/.test(l.detail)), 'מקלדת עברית מתורגמת לפני הבדיקה');
  // משהו שלא נראה כמו קוד בכלל — נרשם כמו שהוקלד (3 תווים ראשונים)
  assert.equal((await call('POST', '/v1/station/claim', { body: { code: 'שלום עולם', name: 'מעבדה 9 · עמדה 3', machine: m1 } })).body.status, 'bad-code');
  const log2 = (await call('GET', '/v1/admin/log?limit=50', { token: admin })).body.log.filter(l => l.action === 'station.bad-code');
  assert.ok(log2.some(l => /"שלו…" — לא בפורמט/.test(l.detail)), JSON.stringify(log2.map(l => l.detail)));
  // טעות של תו אחד בקוד של המורה — ביומן: לאיזה מורה זה כנראה שייך
  const ABC = '23456789ABCDEFGHJKMNPQRSTUVWXYZ';
  const typo = code1.slice(0, 3) + ABC[(ABC.indexOf(code1[3]) + 1) % ABC.length];
  const swap = code1[1] + code1[0] + code1.slice(2);
  assert.equal((await call('POST', '/v1/station/claim', { body: { code: typo, name: 'מעבדה 9 · עמדה 4', machine: m1 } })).body.status, 'bad-code');
  if (swap !== code1) assert.equal((await call('POST', '/v1/station/claim', { body: { code: swap, name: 'מעבדה 9 · עמדה 5', machine: m1 } })).body.status, 'bad-code');
  const log3 = (await call('GET', '/v1/admin/log?limit=50', { token: admin })).body.log.filter(l => l.action === 'station.bad-code');
  const t1 = log3.find(l => l.actor === 'מחשב: מעבדה 9 · עמדה 4');
  assert.equal(t1.target, 'teacher1@test.dev', JSON.stringify(t1));
  assert.match(t1.detail, /טעות הקלדה/);
  if (swap !== code1) assert.equal(log3.find(l => l.actor === 'מחשב: מעבדה 9 · עמדה 5').target, 'teacher1@test.dev');
  assert.equal(log3.find(l => /ASD\?/.test(l.detail)).target || '', '', 'קוד רחוק — בלי מורה');
  // הקוד הנכון של המורה, מוקלד כשהמקלדת בעברית
  const heb = { Q: '/', W: "'", E: 'ק', R: 'ר', T: 'א', Y: 'ט', U: 'ו', I: 'ן', O: 'ם', P: 'פ', A: 'ש', S: 'ד', D: 'ג', F: 'כ', G: 'ע', H: 'י', J: 'ח', K: 'ל', L: 'ך', Z: 'ז', X: 'ס', C: 'ב', V: 'ה', B: 'נ', N: 'מ', M: 'צ' };
  const typed = Array.from(code1).map(ch => heb[ch] || ch).join('');
  const r = await call('POST', '/v1/station/claim', { body: { code: typed, name: 'מעבדה 2 · עמדה 1', machine: m1, client: 'test' } });
  assert.equal(r.body.status, 'ok', typed + ' → ' + JSON.stringify(r.body));
});

await test('עמדות: השנייה נכנסת (2/2), השלישית — "אין עוד היתרים"; אותו מחשב שמתחבר שוב — בלי היתר נוסף', async () => {
  assert.equal((await call('POST', '/v1/station/claim', { body: { code: code1, name: 'עמדה 2', machine: m2 } })).body.status, 'ok');
  const full = await call('POST', '/v1/station/claim', { body: { code: code1, name: 'עמדה 3', machine: m3 } });
  assert.equal(full.body.status, 'full');
  assert.deepEqual([full.body.teacher.used, full.body.teacher.seats], [2, 2]);
  const again = await call('POST', '/v1/station/claim', { body: { code: code1, name: 'עמדה 1 (מחדש)', machine: m1 } });
  assert.equal(again.body.status, 'ok');
  assert.notEqual(again.body.token, st1);
  assert.equal((await call('POST', '/v1/access/check', { body: { token: st1, machine: m1 } })).body.status, 'ok', 'הכרטיס הקודם של אותה עמדה נשאר תקף (משתמש Windows אחר / תוכנה אחרת באותו מחשב)');
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
  // ההודעות מפנות לכפתורים שקיימים בחלון של DSViz ("חזרה", "כניסה עם Google") — לא ל"התחברות" שאין
  for (const m of [bad.body.error, reuse.body.error]) {
    assert.match(m, /כניסה עם Google/, m);
    assert.doesNotMatch(m, /"התחברות"/, m);
  }
  assert.equal((await call('POST', '/v1/auth/exchange', { body: { idToken: 'x.y.z', role: 'teacher', challenge: p.challenge } })).status, 401);
});

await test('חשבון שעוד לא מורה: not-teacher → בקשת הרשאה → ההנהלה מאשרת → נכנס כמורה', async () => {
  const r = await signIn('newteacher@test.dev', 'teacher');
  assert.equal(r.status, 'not-teacher');
  assert.equal(r.token, undefined);
  const tok = await idToken('newteacher@test.dev', 'דוד');
  // טלפון חובה: נייד ישראלי של 10 ספרות
  for (const bad of [undefined, '', '123', '0212345678', '050123456', '05012345678', 'abc']) {
    const r0 = await call('POST', '/v1/me/request', { token: tok, body: { kind: 'teacher', school: 'תיכון', phone: bad } });
    assert.equal(r0.status, 400, String(bad));
    assert.equal(r0.body.code, 'phone');
  }
  const req = await call('POST', '/v1/me/request', { token: tok, body: { kind: 'teacher', school: 'תיכון עירוני ד׳', phone: '+972 50-123 4567', computers: 25 } });
  assert.equal(req.status, 200, JSON.stringify(req.body));
  const dup = await call('POST', '/v1/me/request', { token: tok, body: { kind: 'teacher', school: 'x' } });
  assert.equal(dup.body.already, true);
  const ov = (await call('GET', '/v1/admin/overview', { token: admin })).body;
  const open = ov.requests.find(x => x.email === 'newteacher@test.dev');
  assert.equal(open.kind, 'teacher');
  assert.equal(open.school, 'תיכון עירוני ד׳');
  assert.equal(open.phone, '0501234567');
  const ap = await call('POST', '/v1/admin/request', { token: admin, body: { id: open.id, decision: 'approve', seats: 25 } });
  assert.equal(ap.status, 200, JSON.stringify(ap.body));
  const again = await signIn('newteacher@test.dev', 'teacher');
  assert.equal(again.status, 'ok');
  assert.equal(again.teacher.seats, 25);
  const tl = (await call('GET', '/v1/admin/teachers', { token: admin })).body.teachers.find(x => x.email === 'newteacher@test.dev');
  assert.equal(tl.phone, '0501234567', 'הטלפון עובר מהבקשה אל המורה ומוצג בהנהלה');
  // עריכת טלפון בהנהלה: לא תקין נדחה
  assert.equal((await call('POST', '/v1/admin/teacher', { token: admin, body: { email: 'newteacher@test.dev', phone: '12345' } })).status, 400);
  assert.equal((await call('POST', '/v1/admin/teacher', { token: admin, body: { email: 'newteacher@test.dev', phone: '052-9876543' } })).status, 200);
  assert.equal((await call('GET', '/v1/admin/teachers', { token: admin })).body.teachers.find(x => x.email === 'newteacher@test.dev').phone, '0529876543');
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

await test('יומן פעולות: מהחדש לישן ומהישן לחדש, עם המשך עמוד בשני הכיוונים', async () => {
  const sorted = (l, dir) => l.every((x, i) => i === 0 || (dir === 'asc' ? l[i - 1].at <= x.at : l[i - 1].at >= x.at));
  const all = (await call('GET', '/v1/admin/log?limit=500', { token: admin })).body.log;
  assert.ok(all.length >= 12, 'יש מספיק רשומות לבדיקה');
  assert.ok(sorted(all, 'desc'), 'ברירת מחדל: מהחדש לישן');
  const asc = (await call('GET', '/v1/admin/log?limit=500&order=asc', { token: admin })).body.log;
  assert.ok(sorted(asc, 'asc'), 'order=asc: מהישן לחדש');
  assert.equal(asc.length, all.length);
  assert.equal(asc[0].at, all[all.length - 1].at);
  const p1 = (await call('GET', '/v1/admin/log?limit=10&order=asc', { token: admin })).body.log;
  const p2 = (await call('GET', '/v1/admin/log?limit=10&order=asc&after=' + encodeURIComponent(p1[p1.length - 1].at), { token: admin })).body.log;
  assert.ok(p2.length > 0 && p2[0].at > p1[p1.length - 1].at, 'ההמשך מתחיל אחרי העמוד הראשון');
  const d1 = (await call('GET', '/v1/admin/log?limit=10', { token: admin })).body.log;
  const d2 = (await call('GET', '/v1/admin/log?limit=10&before=' + encodeURIComponent(d1[d1.length - 1].at), { token: admin })).body.log;
  assert.ok(d2.length > 0 && d2[0].at < d1[d1.length - 1].at);
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

const TEACHER = () => ({ 'x-dsviz-access': teacherTok.token, 'x-dsviz-machine': teacherTok.machine });

await test('שליחה ל-Gemini: מתג כבוי — פתוח לכולם; דולק — רק מחוברים, תלמיד שלא שילם נחסם, ומנה חודשית לפי מורה', async () => {
  let r = await exercise();
  assert.equal(r.status, 200, 'מתג כבוי');
  // עמדת מחשב: אף פעם לא שולחת ל-AI — רק מאגר השאלות (גם כשהמתג כבוי), ולא נספרת במנה של המורה
  r = await exercise({ 'x-dsviz-access': st1, 'x-dsviz-machine': m1 });
  assert.equal(r.status, 403, 'עמדה, מתג כבוי');
  let j = await r.json();
  assert.equal(j.code, 'station');
  assert.match(j.error, /עמדת מחשב.*מאגר השאלות.*מורים ולתלמידים שמחוברים עם Google/);
  await call('POST', '/v1/admin/settings', { token: admin, body: { requireLoginForGemini: true, monthlyPerSeat: 1 } });
  r = await exercise();
  assert.equal(r.status, 401);
  assert.equal((await r.json()).code, 'login');
  r = await exercise({ 'x-dsviz-access': st1, 'x-dsviz-machine': m1 });
  assert.deepEqual([r.status, (await r.json()).code], [403, 'station'], 'עמדה, מתג דולק');
  // גם בבקשת תיקון (הצעה לפתרון שלא עבדה)
  r = await worker.fetch(new Request(BASE + '/v1/repair', { method: 'POST', headers: { 'content-type': 'application/json', 'x-dsviz-access': st1, 'x-dsviz-machine': m1 }, body: '{}' }), ENV, {});
  assert.deepEqual([r.status, (await r.json()).code], [403, 'station'], 'עמדה, תיקון');
  r = await exercise(TEACHER());
  assert.equal(r.status, 200, await r.clone().text());
  r = await exercise(TEACHER());
  assert.equal(r.status, 200);
  r = await exercise(TEACHER());
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

await test('מונה חודשי בהנהלה: מוצג לכל מורה, והמנה משתנה מיד כשמעדכנים את מספר העמדות', async () => {
  let list = (await call('GET', '/v1/admin/teachers', { token: admin })).body.teachers;
  const t1 = list.find(t => t.email === 'teacher1@test.dev');
  assert.ok(t1.sends && t1.sends.n >= 2, JSON.stringify(t1.sends));   // השליחות מהבדיקה הקודמת (עמדה אחת של המורה)
  const before = t1.sends.limit;
  await call('POST', '/v1/admin/teacher', { token: admin, body: { email: 'teacher1@test.dev', seats: 5 } });
  list = (await call('GET', '/v1/admin/teachers', { token: admin })).body.teachers;
  const after = list.find(t => t.email === 'teacher1@test.dev').sends;
  assert.equal(after.limit, (before / 2) * 5);
  assert.equal(after.n, t1.sends.n);
  const one = (await call('GET', '/v1/admin/teacher?email=teacher1@test.dev', { token: admin })).body.teacher;
  assert.equal(one.sends.limit, after.limit);
  await call('POST', '/v1/admin/teacher', { token: admin, body: { email: 'teacher1@test.dev', seats: 2 } });
});

await test('מנת שליחות ידנית למורה: קובעים בהנהלה, נאכפת מיד (גם על העמדות שלו), וחוזרים לברירת המחדל', async () => {
  const get = async () => (await call('GET', '/v1/admin/teachers', { token: admin })).body.teachers.find(t => t.email === 'teacher1@test.dev');
  const before = await get();
  assert.equal(before.monthlyLimit, null, 'ברירת מחדל');
  const n = before.sends.n;
  assert.equal((await call('POST', '/v1/admin/teacher', { token: admin, body: { email: 'teacher1@test.dev', monthlyLimit: -1 } })).status, 400);
  assert.equal((await call('POST', '/v1/admin/teacher', { token: admin, body: { email: 'teacher1@test.dev', monthlyLimit: 'abc' } })).status, 400);
  await call('POST', '/v1/admin/settings', { token: admin, body: { requireLoginForGemini: true, monthlyPerSeat: 4 } });
  assert.equal((await call('POST', '/v1/admin/teacher', { token: admin, body: { email: 'teacher1@test.dev', monthlyLimit: n + 1 } })).status, 200);
  const manual = await get();
  assert.deepEqual([manual.monthlyLimit, manual.sends.limit], [n + 1, n + 1], 'מוצג בהנהלה');
  let r = await exercise(TEACHER());
  assert.equal(r.status, 200, 'שליחה אחת נוספת מותרת: ' + await r.clone().text());
  r = await exercise(TEACHER());
  assert.equal(r.status, 429);
  assert.equal((await r.json()).code, 'monthly');
  // העלאה ידנית — נפתח מיד
  await call('POST', '/v1/admin/teacher', { token: admin, body: { email: 'teacher1@test.dev', monthlyLimit: n + 5 } });
  r = await exercise(TEACHER());
  assert.equal(r.status, 200);
  // 0 = חסום לגמרי
  await call('POST', '/v1/admin/teacher', { token: admin, body: { email: 'teacher1@test.dev', monthlyLimit: 0 } });
  assert.equal((await exercise(TEACHER())).status, 429);
  // חזרה לברירת המחדל (ריק): 4 × עמדות
  assert.equal((await call('POST', '/v1/admin/teacher', { token: admin, body: { email: 'teacher1@test.dev', monthlyLimit: null } })).status, 200);
  const back = await get();
  assert.deepEqual([back.monthlyLimit, back.sends.limit], [null, 4 * Math.max(1, back.seats)]);
  await call('POST', '/v1/admin/settings', { token: admin, body: { requireLoginForGemini: false } });
});

await test('תלמיד מרשימת ההיתרים (הענקה ידנית) = כמו תלמיד ששילם: אותה מנה חודשית, והמונה מוצג בהנהלה ובחשבון שלו', async () => {
  await call('POST', '/v1/admin/student', { token: admin, body: { email: 'listed@test.dev', until: '2027-08-31', note: 'רשימת בית ספר' } });
  const s = await signIn('listed@test.dev', 'student');
  assert.equal(s.status, 'ok', 'נכנס בלי תשלום');
  await call('POST', '/v1/admin/settings', { token: admin, body: { requireLoginForGemini: true, monthlyStudent: 2 } });
  for (const want of [200, 200, 429]) {
    const r = await exercise({ 'x-dsviz-access': s.token, 'x-dsviz-machine': s.machine });
    assert.equal(r.status, want, await r.clone().text());
  }
  const row = (await call('GET', '/v1/admin/students', { token: admin })).body.students.find(x => x.email === 'listed@test.dev');
  assert.deepEqual([row.sends.n, row.sends.limit], [2, 2], 'המונה בהנהלה');
  const one = (await call('GET', '/v1/admin/student?email=listed@test.dev', { token: admin })).body;
  assert.equal(one.sends.n, 2);
  assert.deepEqual([(await call('GET', '/v1/me', { token: await idToken('listed@test.dev', 'listed') })).body.student?.sends?.n], [2], 'המונה בחשבון של התלמיד');
  await call('POST', '/v1/admin/settings', { token: admin, body: { requireLoginForGemini: false, monthlyStudent: 30 } });
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

await test('התוכנה ששלחה (app: dsviz / automata): נרשמת ביומן; באישור — apps (ברירת מחדל: שתי התוכנות); לקוח ישן / ערך לא מוכר — כמו קודם', async () => {
  await call('POST', '/v1/admin/teacher', { token: admin, body: { create: true, email: 'apps@test.dev', name: 'מורה אוטומטים', seats: 2 } });
  const code = (await call('GET', '/v1/admin/teacher?email=apps@test.dev', { token: admin })).body.teacher.code;
  // מורה מ"מודלים חישוביים"
  const p = pkce();
  const ex = await call('POST', '/v1/auth/exchange', { body: { idToken: await idToken('apps@test.dev', 'מורה אוטומטים'), role: 'teacher', challenge: p.challenge } });
  const r = await call('POST', '/v1/auth/redeem', { body: { code: ex.body.code, verifier: p.verifier, machine: machine(), machineName: 'מחשב אוטומטים', client: 'dsviz-automata/1.0', app: 'automata' } });
  assert.equal(r.body.status, 'ok', JSON.stringify(r.body));
  assert.deepEqual(readPass(r.body.pass).apps, ['dsviz', 'automata']);
  // עמדה מ-Eclipse (app=dsviz), ועמדה מלקוח ישן (בלי app) / עם ערך לא מוכר
  const s1 = await call('POST', '/v1/station/claim', { body: { code, name: 'עמדת אוטומטים 1', machine: machine(), client: 'test', app: 'dsviz' } });
  assert.equal(s1.body.status, 'ok');
  assert.deepEqual(readPass(s1.body.pass).apps, ['dsviz', 'automata']);
  const s2 = await call('POST', '/v1/station/claim', { body: { code, name: 'עמדת אוטומטים 2', machine: machine(), client: 'test', app: '<script>' } });
  assert.equal(s2.body.status, 'ok');
  const log = (await call('GET', '/v1/admin/log?limit=50', { token: admin })).body.log;
  const signin = log.find(l => l.action === 'signin.teacher' && l.actor === 'apps@test.dev');
  assert.equal(signin && signin.detail, 'app=automata');
  assert.equal(log.find(l => l.action === 'station.claim' && l.actor === 'מחשב: עמדת אוטומטים 1').detail, 'app=dsviz · 1/2');
  assert.equal(log.find(l => l.action === 'station.claim' && l.actor === 'מחשב: עמדת אוטומטים 2').detail, '2/2');
  // בלי app (כמו כל הבדיקות האחרות): היומן כמו קודם
  assert.ok(log.filter(l => l.action === 'signin.teacher' && l.actor !== 'apps@test.dev').every(l => !/app=/.test(l.detail)));
});

await test('עמדה לא נפלטת: כמה התחברויות מאותו מחשב (משתמשים / תוכנות) — כל הכרטיסים תקפים עד שהמורה משחרר', async () => {
  await call('POST', '/v1/admin/teacher', { token: admin, body: { create: true, email: 'keep@test.dev', name: 'יציבות', seats: 2 } });
  const code = (await call('GET', '/v1/admin/teacher?email=keep@test.dev', { token: admin })).body.teacher.code;
  const m = machine();
  const claim = (extra) => call('POST', '/v1/station/claim', { body: Object.assign({ code, name: 'מעבדה 1 · עמדה 7', machine: m, client: 'test' }, extra) });
  const check = (token, mach) => call('POST', '/v1/access/check', { body: { token, machine: mach || m } });
  const a = (await claim()).body, b = (await claim({ why: 'none' })).body, c = (await claim({ why: 'unreadable' })).body;
  assert.deepEqual([a.status, b.status, c.status], ['ok', 'ok', 'ok']);
  assert.equal(new Set([a.token, b.token, c.token]).size, 3);
  for (const t of [a.token, b.token, c.token]) assert.equal((await check(t)).body.status, 'ok', 'כל שלושת הכרטיסים של אותה עמדה תקפים');
  let t = (await call('GET', '/v1/admin/teacher?email=keep@test.dev', { token: admin })).body;
  assert.deepEqual([t.teacher.used, t.stations.length], [1, 1], 'מקום אחד בלבד');
  // ביומן: למה המחשב התחבר שוב
  const log = (await call('GET', '/v1/admin/log?limit=80', { token: admin })).body.log.filter(l => l.target === 'keep@test.dev');
  assert.equal(log.filter(l => l.action === 'station.claim').length, 1);
  const again = log.filter(l => l.action === 'station.again').map(l => l.detail);
  assert.equal(again.length, 2);
  assert.ok(again.some(d => /אין במחשב כניסה שמורה/.test(d)) && again.some(d => /הכניסה השמורה לא נפתחה/.test(d)), again.join(' | '));
  // שחרור מההנהלה: כל הכרטיסים ננעלים, עם why
  await call('POST', '/v1/admin/station/release', { token: admin, body: { id: t.stations[0].id } });
  for (const tok of [a.token, b.token, c.token]) {
    const r = (await check(tok)).body;
    assert.deepEqual([r.status, r.why], ['revoked', 'released']);
  }
  const rej = (await call('GET', '/v1/admin/log?limit=80', { token: admin })).body.log.filter(l => l.action === 'station.rejected' && l.target === 'keep@test.dev');
  assert.equal(rej.length, 1, 'נרשם פעם אחת ביום לכל עמדה וסיבה');
  assert.match(rej[0].detail, /העמדה שוחררה/);
});

await test('אותו מחשב עם מזהה חדש (שוחזר / קובץ שנמחק / משתמש אחר): לפי המזהה הקבוע — אותה עמדה, בלי מקום נוסף, ושני המזהים תקפים', async () => {
  await call('POST', '/v1/admin/teacher', { token: admin, body: { create: true, email: 'stable@test.dev', name: 'קבוע', seats: 2 } });
  const code = (await call('GET', '/v1/admin/teacher?email=stable@test.dev', { token: admin })).body.teacher.code;
  const stable = machine(), mA = machine(), mB = machine(), other = machine();
  const claim = (m, st, name) => call('POST', '/v1/station/claim', { body: { code, name: name || 'עמדה 1', machine: m, stable: st, client: 'test' } });
  const check = (token, m) => call('POST', '/v1/access/check', { body: { token, machine: m } });
  const a = (await claim(mA, stable)).body;
  const b = (await claim(mB, stable)).body;          // אותו מחשב (אותו stable), מזהה רגיל אחר
  assert.deepEqual([a.status, b.status], ['ok', 'ok']);
  let t = (await call('GET', '/v1/admin/teacher?email=stable@test.dev', { token: admin })).body;
  assert.deepEqual([t.teacher.used, t.stations.length], [1, 1], 'אותה עמדה: לא נלקח מקום נוסף');
  const ra = (await check(a.token, mA)).body, rb = (await check(b.token, mB)).body;
  assert.deepEqual([ra.status, rb.status], ['ok', 'ok'], 'שני המזהים של אותו מחשב תקפים');
  assert.deepEqual([readPass(ra.pass).mid, readPass(rb.pass).mid], [mA, mB], 'כל אישור חתום למזהה שהמחשב שלח');
  // מחשב אחר (stable אחר): עמדה נפרדת; כרטיס שהועתק למחשב אחר נדחה
  const c = (await claim(other, machine(), 'עמדה 2')).body;
  assert.equal(c.status, 'ok');
  t = (await call('GET', '/v1/admin/teacher?email=stable@test.dev', { token: admin })).body;
  assert.deepEqual([t.teacher.used, t.stations.length], [2, 2]);
  const copied = (await check(a.token, other)).body;
  assert.deepEqual([copied.status, copied.why], ['invalid', 'machine']);
  // מחשב שלישי בלי מקום: גם עם stable של מחשב אחר (לא מוכר) — full
  assert.equal((await claim(machine(), machine(), 'עמדה 3')).body.status, 'full');
  // העמדה שוחררה, והמחשב מתחבר שוב עם מזהה שלישי ואותו stable: עמדה חדשה (המקום חזר ונלקח), לא "אותה עמדה" שהמורה שחרר
  const st1 = t.stations.find(s => s.name === 'עמדה 1');
  await call('POST', '/v1/admin/station/release', { token: admin, body: { id: st1.id } });
  assert.equal((await check(b.token, mB)).body.status, 'revoked');
  const d = (await claim(machine(), stable)).body;
  assert.equal(d.status, 'ok');
  assert.equal((await check(b.token, mB)).body.status, 'revoked', 'כרטיס של עמדה ששוחררה לא חוזר לחיים');
  t = (await call('GET', '/v1/admin/teacher?email=stable@test.dev', { token: admin })).body;
  assert.deepEqual([t.teacher.used, t.stations.length], [2, 2]);
});

await test('עמדה: אחרי הרבה התחברויות נשמרים רק הכרטיסים האחרונים; כרטיס לא מוכר — invalid עם why', async () => {
  await call('POST', '/v1/admin/teacher', { token: admin, body: { create: true, email: 'many@test.dev', name: 'הרבה', seats: 1 } });
  const code = (await call('GET', '/v1/admin/teacher?email=many@test.dev', { token: admin })).body.teacher.code;
  const m = machine(), toks = [];
  for (let i = 0; i < 10; i++) toks.push((await call('POST', '/v1/station/claim', { body: { code, name: 'עמדה', machine: m } })).body.token);
  const st = async (t) => (await call('POST', '/v1/access/check', { body: { token: t, machine: m } })).body;
  assert.equal((await st(toks[9])).status, 'ok');
  assert.equal((await st(toks[2])).status, 'ok', 'שמונת האחרונים תקפים');
  const old = await st(toks[0]);
  assert.deepEqual([old.status, old.why], ['invalid', 'token']);
  const gone = await st('s.' + 'a'.repeat(32) + '.' + 'b'.repeat(43));
  assert.deepEqual([gone.status, gone.why], ['invalid', 'gone']);
  assert.equal((await call('GET', '/v1/admin/teacher?email=many@test.dev', { token: admin })).body.teacher.used, 1);
});

// ------------------------------------------------------------------ תשלום ב-PayPal
const ENV_PP = Object.assign({}, ENV, { PAY_PROVIDER: 'paypal', PAYPAL_CLIENT_ID: 'cid', PAYPAL_SECRET: 'sec', PAYPAL_WEBHOOK_ID: 'WH-1' });
const ppHook = (event, sig = 'good') => call('POST', '/v1/pay/notify/paypal', { env: ENV_PP, body: event,
  headers: { 'paypal-transmission-sig': sig, 'paypal-transmission-id': 't1', 'paypal-transmission-time': '2026-01-01T00:00:00Z', 'paypal-auth-algo': 'SHA256withRSA', 'paypal-cert-url': 'https://api.paypal.com/cert' } });
const ppReturn = (pid, order) => call('GET', '/pay/paypal/return?p=' + pid + '&token=' + order + '&PayerID=P1', { env: ENV_PP });
async function ppStudent(email) {
  const tok = await idToken(email, 'תלמיד PayPal');
  const m = machine(), k = pkce();
  const ex = await call('POST', '/v1/auth/exchange', { body: { idToken: tok, role: 'student', challenge: k.challenge } });
  const red = (await call('POST', '/v1/auth/redeem', { body: { code: ex.body.code, verifier: k.verifier, machine: m, machineName: 'PC' } })).body;
  const status = async () => (await call('POST', '/v1/access/check', { body: { token: red.token, machine: m } })).body.status;
  return { tok, status };
}

await test('PayPal: ההזמנה נוצרת בשרת עם הסכום מההגדרות; חזרה לפני אישור לא פותחת; אחרי אישור — נגבה ונפתח, פעם אחת', async () => {
  const st = await ppStudent('pp1@test.dev');
  assert.equal(await st.status(), 'pay');
  assert.equal((await call('GET', '/v1/me', { token: st.tok, env: ENV_PP })).body.payBy, 'paypal');
  // הדפדפן מנסה לקבוע סכום — השרת מתעלם
  const start = await call('POST', '/v1/pay/start', { token: st.tok, env: ENV_PP, body: { amount: 1, price: 1, currency: 'USD' } });
  assert.equal(start.status, 200, JSON.stringify(start.body));
  assert.match(start.body.url, /^https:\/\/www\.sandbox\.paypal\.com\/checkoutnow\?token=ORDER\d+XY$/);
  const orderId = start.body.url.split('token=')[1], o = PP.orders.get(orderId);
  assert.deepEqual([o.unit.amount.value, o.unit.amount.currency_code, o.unit.custom_id], ['60.00', 'ILS', start.body.id]);
  assert.match(o.ctx.return_url, /^https:\/\/w\.example\/pay\/paypal\/return\?p=/);
  // חזרה בלי שהמשלם אישר: לא נגבה, לא נפתח
  let r = await ppReturn(start.body.id, orderId);
  assert.equal(r.status, 302);
  assert.equal(await st.status(), 'pay');
  // המשלם מאשר וחוזר
  o.approved = true;
  r = await ppReturn(start.body.id, orderId);
  assert.deepEqual([r.status, r.headers.get('location')], [302, '/welcome?r=paid']);
  assert.equal(await st.status(), 'ok');
  // חזרה נוספת (רענון הדף) והודעה מ-PayPal על אותה גבייה: עדיין תשלום אחד
  await ppReturn(start.body.id, orderId);
  const hook = await ppHook({ event_type: 'PAYMENT.CAPTURE.COMPLETED', resource: { id: o.captured } });
  assert.equal(hook.status, 200);
  const pays = (await call('GET', '/v1/admin/payments', { token: admin })).body.payments.filter(p => p.email === 'pp1@test.dev');
  assert.deepEqual(pays.map(p => p.status), ['paid']);
  assert.deepEqual([pays[0].payerName, pays[0].payerEmail, pays[0].payerPhone, pays[0].amount], ['דנה לוי', 'dana@paypal.test', '0501234567', 60]);
  const log = (await call('GET', '/v1/admin/log?limit=60', { token: admin })).body.log.filter(l => l.action === 'payment.paid' && l.target === 'pp1@test.dev');
  assert.equal(log.length, 1);
  assert.equal(PP.calls.filter(c => c === 'POST /v2/checkout/orders/' + orderId + '/capture').length >= 1, true);
});

await test('PayPal: אי אפשר לסגור תשלום עם הזמנה אחרת, עם סכום אחר או עם מטבע אחר', async () => {
  const a = await ppStudent('pp2@test.dev'), b = await ppStudent('pp3@test.dev');
  const sa = (await call('POST', '/v1/pay/start', { token: a.tok, env: ENV_PP, body: {} })).body;
  const sb = (await call('POST', '/v1/pay/start', { token: b.tok, env: ENV_PP, body: {} })).body;
  const oa = sa.url.split('token=')[1], ob = sb.url.split('token=')[1];
  // ב' שילם; א' מנסה לסגור את התשלום שלו עם ההזמנה של ב'
  PP.orders.get(ob).approved = true;
  await ppReturn(sa.id, ob);
  assert.equal(await a.status(), 'pay', 'הזמנה של תשלום אחר לא פותחת');
  await ppReturn(sb.id, ob);
  assert.equal(await b.status(), 'ok');
  // מזהים לא תקינים / תשלום שלא קיים
  assert.equal((await ppReturn('nope-nope-nope', oa)).headers.get('location'), '/welcome?r=error');
  assert.equal((await ppReturn(sa.id, '../../v1/oauth2/token')).status, 302);
  assert.equal(await a.status(), 'pay');
  // PayPal מדווח סכום אחר / מטבע אחר ממה שנקבע: לא נפתח, והתשלום מסומן
  PP.tamper = { amount: { currency_code: 'ILS', value: '1.00' } };
  PP.orders.get(oa).approved = true;
  await ppReturn(sa.id, oa);
  PP.tamper = null;
  assert.equal(await a.status(), 'pay', 'סכום אחר לא פותח');
  const c = await ppStudent('pp4@test.dev');
  const sc = (await call('POST', '/v1/pay/start', { token: c.tok, env: ENV_PP, body: {} })).body, oc = sc.url.split('token=')[1];
  PP.tamper = { amount: { currency_code: 'USD', value: '60.00' } };
  PP.orders.get(oc).approved = true;
  await ppReturn(sc.id, oc);
  PP.tamper = null;
  assert.equal(await c.status(), 'pay', 'מטבע אחר לא פותח');
  const pays = (await call('GET', '/v1/admin/payments', { token: admin })).body.payments;
  assert.equal(pays.find(p => p.id === sa.id).status, 'mismatch');
  assert.equal(pays.find(p => p.id === sc.id).status, 'mismatch');
});

await test('PayPal webhook: בלי חתימה תקפה נדחה; המשלם אישר ולא חזר — נגבה מההודעה; תוכן מזויף לא פותח; החזר כספי סוגר', async () => {
  const st = await ppStudent('pp5@test.dev');
  const s = (await call('POST', '/v1/pay/start', { token: st.tok, env: ENV_PP, body: {} })).body, orderId = s.url.split('token=')[1], o = PP.orders.get(orderId);
  const approved = { event_type: 'CHECKOUT.ORDER.APPROVED', resource: { id: orderId, purchase_units: [{ custom_id: s.id }] } };
  // הודעה מזויפת: בלי חתימה תקפה
  assert.equal((await ppHook(approved, 'forged')).status, 400);
  // חתימה תקפה, אבל המשלם לא באמת אישר (PayPal עצמו אומר שלא): לא נפתח
  await ppHook(approved);
  assert.equal(await st.status(), 'pay');
  // הודעה "שולם" על גבייה שלא קיימת / על תשלום אחר: לא נפתח
  await ppHook({ event_type: 'PAYMENT.CAPTURE.COMPLETED', resource: { id: 'CAP99999999Z', custom_id: s.id, status: 'COMPLETED', amount: { value: '60.00', currency_code: 'ILS' } } });
  assert.equal(await st.status(), 'pay', 'סומכים על PayPal, לא על מה שכתוב בהודעה');
  // המשלם אישר, וסגר את הדפדפן לפני החזרה לאתר
  o.approved = true;
  assert.equal((await ppHook(approved)).status, 200);
  assert.equal(await st.status(), 'ok');
  // בלי PAYPAL_WEBHOOK_ID: הודעות לא מתקבלות בכלל
  assert.equal((await call('POST', '/v1/pay/notify/paypal', { env: Object.assign({}, ENV_PP, { PAYPAL_WEBHOOK_ID: '' }), body: approved, headers: { 'paypal-transmission-sig': 'good' } })).status, 400);
  // החזר כספי מלא (ה-resource הוא ההחזר, והגבייה בקישור up)
  PP.captures.get(o.captured).status = 'PARTIALLY_REFUNDED';
  const refund = { event_type: 'PAYMENT.CAPTURE.REFUNDED', resource: { id: 'REF00000001A', links: [{ rel: 'up', href: 'https://api-m.sandbox.paypal.com/v2/payments/captures/' + o.captured }] } };
  await ppHook(refund);
  assert.equal(await st.status(), 'ok', 'החזר חלקי לא סוגר');
  PP.captures.get(o.captured).status = 'REFUNDED';
  await ppHook(refund);
  assert.equal(await st.status(), 'pay', 'החזר מלא סוגר את המאגר');
});

await test('PayPal: חבילת עמדות למורה; ביטול בדף של PayPal; שירות תשלום שלא עונה; ומגבלת ניסיונות', async () => {
  await call('POST', '/v1/admin/teacher', { token: admin, body: { create: true, email: 'ppteacher@test.dev', name: 'מורה PayPal', seats: 1 } });
  const tok = await idToken('ppteacher@test.dev');
  const s = (await call('POST', '/v1/pay/start', { token: tok, env: ENV_PP, body: { kind: 'seats', package: 1 } })).body, orderId = s.url.split('token=')[1];
  assert.deepEqual([PP.orders.get(orderId).unit.amount.value, PP.orders.get(orderId).unit.amount.currency_code], ['500.00', 'ILS']);
  assert.equal((await call('GET', '/pay/paypal/cancel?p=' + s.id, { env: ENV_PP })).headers.get('location'), '/account');
  assert.equal((await call('GET', '/v1/admin/teacher?email=ppteacher@test.dev', { token: admin })).body.teacher.seats, 1);
  PP.orders.get(orderId).approved = true;
  assert.equal((await ppReturn(s.id, orderId)).headers.get('location'), '/account?r=paid');
  assert.equal((await call('GET', '/v1/admin/teacher?email=ppteacher@test.dev', { token: admin })).body.teacher.seats, 21);
  await ppReturn(s.id, orderId);
  assert.equal((await call('GET', '/v1/admin/teacher?email=ppteacher@test.dev', { token: admin })).body.teacher.seats, 21, 'חזרה כפולה — חבילה אחת');
  // בלי מפתחות PayPal: התשלום סגור
  assert.equal((await call('POST', '/v1/pay/start', { token: tok, env: Object.assign({}, ENV_PP, { PAYPAL_SECRET: '' }), body: { kind: 'seats', package: 1 } })).status, 503);
  // PayPal לא עונה: הודעה ברורה, והתשלום מסומן "נכשל" (לא נשאר "ממתין")
  const st = await ppStudent('pp6@test.dev');
  const down = await call('POST', '/v1/pay/start', { token: st.tok, env: Object.assign({}, ENV_PP, { PAYPAL_CLIENT_ID: 'wrong' }), body: {} });
  assert.deepEqual([down.status, down.body.code], [502, 'pay_down']);
  // יותר מ-10 תשלומים פתוחים בשעה לחשבון
  let last;
  for (let i = 0; i < 11; i++) last = await call('POST', '/v1/pay/start', { token: st.tok, env: ENV_PP, body: {} });
  assert.deepEqual([last.status, last.body.code], [429, 'rate']);
});

// ------------------------------------------------------------------ התראות למנהל
await test('התראות: תלמיד חדש / מורה בפעם הראשונה / בקשת הרשאה / תשלום → עיגול אדום במסוף ומייל; כניסה חוזרת — בלי התראה; פתיחה מנקה', async () => {
  const ENV_M = Object.assign({}, ENV, { RESEND_API_KEY: 're_test', ALERT_EMAIL: 'boss@test.dev' });
  const adm = (method, path, body) => call(method, path, { token: admin, env: ENV_M, body });
  await adm('POST', '/v1/admin/alerts/seen', { all: true });
  const start = (await adm('GET', '/v1/admin/alerts?limit=50')).body;
  assert.equal(start.unseen, 0, 'A1');
  MAILS.length = 0;
  const signIn = async (email, role, m) => {
    const tok = await idToken(email, role === 'teacher' ? 'מורה חדשה' : 'תלמיד חדש');
    const k = pkce();
    const ex = await call('POST', '/v1/auth/exchange', { env: ENV_M, body: { idToken: tok, role, challenge: k.challenge } });
    return (await call('POST', '/v1/auth/redeem', { env: ENV_M, body: { code: ex.body.code, verifier: k.verifier, machine: m || machine(), machineName: 'מחשב בבית' } })).body;
  };
  // תלמיד חדש
  const st = await signIn('newkid@test.dev', 'student');
  assert.equal(st.status, 'pay');
  let a = (await adm('GET', '/v1/admin/alerts?limit=50')).body;
  assert.equal(a.unseen, 1, JSON.stringify(a.alerts.filter(x => !x.seen)));
  assert.deepEqual([a.alerts[0].kind, a.alerts[0].email, a.alerts[0].name, a.alerts[0].go], ['student.new', 'newkid@test.dev', 'תלמיד חדש', '#students=newkid%40test.dev']);
  assert.match(a.alerts[0].detail, /מחשב בבית/);
  assert.equal(MAILS.length, 1, 'A2');
  assert.deepEqual([MAILS[0].auth, MAILS[0].body.to, MAILS[0].body.from], ['Bearer re_test', ['boss@test.dev'], 'DSViz <onboarding@resend.dev>']);
  assert.match(MAILS[0].body.subject, /תלמיד חדש הצטרף — תלמיד חדש/);
  assert.match(MAILS[0].body.html, /newkid@test\.dev/);
  assert.match(MAILS[0].body.text, /\/admin#students=newkid%40test\.dev/);
  // אותו תלמיד נכנס שוב (מחשב אחר) — לא "חדש"
  await signIn('newkid@test.dev', 'student');
  assert.equal((await adm('GET', '/v1/admin/alerts')).body.unseen, 1);
  assert.equal(MAILS.length, 1, 'A3');
  // מורה שההנהלה הוסיפה, נכנס בפעם הראשונה (ואחר כך שוב — בלי התראה)
  await adm('POST', '/v1/admin/teacher', { create: true, email: 'fresh@test.dev', name: 'מורה חדשה', seats: 1 });
  await signIn('fresh@test.dev', 'teacher');
  await signIn('fresh@test.dev', 'teacher');
  a = (await adm('GET', '/v1/admin/alerts')).body;
  assert.equal(a.unseen, 2, 'A4');
  assert.equal(a.alerts.filter(x => x.kind === 'teacher.first' && x.email === 'fresh@test.dev').length, 1, 'A5');
  // בקשה להרשאת מורה (עם בית ספר וטלפון)
  const tok = await idToken('wannabe@test.dev', 'רוצה ללמד');
  await call('POST', '/v1/me/request', { env: ENV_M, token: tok, body: { kind: 'teacher', school: 'תיכון הבדיקה', phone: '050-7654321', computers: 25 } });
  a = (await adm('GET', '/v1/admin/alerts')).body;
  const req = a.alerts.find(x => x.kind === 'teacher.request');
  assert.deepEqual([req.email, req.go], ['wannabe@test.dev', '#requests']);
  assert.match(req.detail, /תיכון הבדיקה · 0507654321 · 25 מחשבים/);
  // תשלום
  const kidTok = await idToken('newkid@test.dev');
  const pay = (await call('POST', '/v1/pay/start', { env: ENV_M, token: kidTok, body: {} })).body;
  await call('POST', '/v1/pay/notify/demo', { env: ENV_M, body: { id: pay.id, status: 'paid', amount: 60 } });
  a = (await adm('GET', '/v1/admin/alerts')).body;
  assert.equal(a.unseen, 4, 'A6');
  assert.match(a.alerts.find(x => x.kind === 'payment.paid').detail, /60 ₪/);
  assert.equal(MAILS.length, 4, 'A7');
  // בסקירה — אותו מספר
  assert.equal((await adm('GET', '/v1/admin/overview')).body.counts.alertsUnseen, 4);
  // פתיחת הפעמון מנקה
  assert.equal((await adm('POST', '/v1/admin/alerts/seen', { all: true })).body.marked, 4);
  assert.equal((await adm('GET', '/v1/admin/alerts')).body.unseen, 0);
  // מייל כבוי בהגדרות: ההתראה במסוף נשארת, מייל — לא
  await adm('POST', '/v1/admin/settings', { alertEmail: false });
  assert.equal((await adm('GET', '/v1/admin/settings')).body.settings.alertEmail, false);
  await signIn('quiet@test.dev', 'student');
  assert.equal((await adm('GET', '/v1/admin/alerts')).body.unseen, 1);
  assert.equal(MAILS.length, 4, 'A8');
  await adm('POST', '/v1/admin/settings', { alertEmail: true });
  // בלי RESEND_API_KEY: רק במסוף; ובהגדרות רואים שהמייל לא מוגדר
  const ENV_N = Object.assign({}, ENV);
  await call('POST', '/v1/auth/exchange', { env: ENV_N, body: {} }).catch(() => {});
  const set = (await call('GET', '/v1/admin/settings', { token: admin, env: ENV_N })).body;
  assert.deepEqual([set.alertMail.ready, set.alertMail.to], [false, 'admin@test.dev']);
  assert.equal((await adm('GET', '/v1/admin/settings')).body.alertMail.ready, true);
  // רק מנהל רואה ומנקה התראות
  assert.equal((await call('GET', '/v1/admin/alerts', { token: tok, env: ENV_M })).status, 403, 'A9');
  assert.equal((await call('POST', '/v1/admin/alerts/seen', { token: tok, env: ENV_M, body: { all: true } })).status, 403, 'A10');
  await adm('POST', '/v1/admin/alerts/seen', { all: true });
});

await test('הדפים בדפדפן נטענים (signin / admin) עם כותרות אבטחה', async () => {
  const r = await call('GET', '/signin');
  assert.equal(r.status, 200);
  assert.match(r.headers.get('content-type'), /text\/html/);
  assert.equal(r.headers.get('x-frame-options'), 'DENY');
});

console.log(`\n${passed} בדיקות עברו`);
