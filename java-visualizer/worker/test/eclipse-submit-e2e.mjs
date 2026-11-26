// "הגשה למורה" בחלון הצף "השאלה" של Eclipse אמיתי (xvfb), מול השרת המקומי ואמולטור Firebase:
// מורה (שרה כהן) → הקוד שלה בקובץ → Eclipse מתחבר כעמדת מחשב, יוצר תרגיל מהמאגר, מגיש עם "שם המגיש", ואז "סיימתי" מגיש לפני המחיקה
// (הצד של Eclipse: tools/test/eclipse-it/src/.../TeacherSubmitIt.java, דרך gate.sh עם GATE_APP=teachersubmit). כאן — מה שהמורה קיבל.
//   cd worker && npm run test:eclipse-submit        משתנים: SHOTS (ברירת מחדל /tmp/eclipse-submit-shots)
import assert from 'node:assert/strict';
import { spawn } from 'node:child_process';
import { createHash } from 'node:crypto';
import { existsSync, mkdirSync, readFileSync, rmSync, writeFileSync, createWriteStream } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const PORT = 8787, BASE = `http://127.0.0.1:${PORT}`;
const AUTH = process.env.FIREBASE_AUTH_EMULATOR_HOST || '127.0.0.1:9099';
const FS = process.env.FIRESTORE_EMULATOR_HOST || '127.0.0.1:8181';
const SHOTS = process.env.SHOTS || '/tmp/eclipse-submit-shots';
rmSync(SHOTS, { recursive: true, force: true });
mkdirSync(SHOTS, { recursive: true });

await fetch(`http://${FS}/emulator/v1/projects/demo-dsviz/databases/(default)/documents`, { method: 'DELETE' });
await fetch(`http://${AUTH}/emulator/v1/projects/demo-dsviz/accounts`, { method: 'DELETE' });
const server = spawn(process.execPath, [join(here, '../tools/dev-server.mjs')], { env: Object.assign({}, process.env, { PORT: String(PORT), ADMIN_EMAILS: 'motiml77@gmail.com' }), stdio: ['ignore', 'pipe', 'inherit'] });
await new Promise(res => server.stdout.on('data', d => { if (String(d).includes('dev server')) res(); }));
let eclipse;
process.on('exit', () => { try { eclipse && eclipse.kill(); } catch (e) { /* */ } try { server.kill(); } catch (e) { /* */ } });

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
const sleep = ms => new Promise(r => setTimeout(r, ms));
const outFile = join(SHOTS, 'out.txt');
const outText = () => existsSync(outFile) ? readFileSync(outFile, 'utf8') : '';
const line = key => (outText().split('\n').find(l => l.startsWith(key + ' ')) || '').slice(key.length + 1);

const admin = await idToken('motiml77@gmail.com', 'Moti');
const spki = (await api('GET', '/v1/access/key')).spki;
await api('POST', '/v1/admin/teacher', { create: true, email: 'sara.e@test.dev', name: 'שרה כהן', school: 'תיכון לדוגמה', seats: 3 }, admin);
const code = (await api('GET', '/v1/admin/teacher?email=sara.e@test.dev', null, admin)).teacher.code;
writeFileSync(join(SHOTS, 'code.txt'), code);
const sara = await idToken('sara.e@test.dev', 'שרה כהן');

const log = createWriteStream(join(SHOTS, 'gate-sh.log'));
eclipse = spawn('sh', [join(here, '../../tools/test/eclipse-it/gate.sh')], {
  env: Object.assign({}, process.env, { GATE_WORKER: BASE, GATE_PASSKEY: spki, GATE_SHOTS: SHOTS, GATE_APP: 'teachersubmit' }), stdio: ['ignore', 'pipe', 'pipe'],
});
eclipse.stdout.pipe(log, { end: false });
eclipse.stderr.pipe(log, { end: false });
const exited = new Promise(res => eclipse.on('exit', code => res(code)));
const end = Date.now() + (Number(process.env.GATE_WAIT_MIN) || 50) * 60 * 1000;   // כולל בניית ההרחבה ובדיקת כל המאגר (Java ו-C#) — במחשב עמוס יותר מרבע שעה
while (Date.now() < end && !/^DONE$/m.test(outText())) {
  if (await Promise.race([exited.then(() => true), sleep(1000).then(() => false)])) break;
}
await Promise.race([exited, sleep(30000)]);
console.log(outText().split('\n').filter(l => !/^\s+at /.test(l)).join('\n'));

let passed = 0;
function check(name, ok, got) {
  if (ok) { passed++; console.log('✓ ' + name); } else { console.error('✗ ' + name + '  ← ' + got); process.exitCode = 1; }
}
check('Eclipse: המחשב התחבר כעמדה בקוד המורה, ו"הגשה למורה" מגישה כעמדה למורה שרה כהן', /^ok mode=station teacher=שרה כהן$/.test(line('CLAIM')), line('CLAIM'));
check('בחלון הצף "השאלה": הכפתור "הגשה למורה שרה כהן"', line('SHOWN').includes('SUBMIT-BUTTON הגשה למורה שרה כהן'), line('SHOWN'));
check('"הגשה למורה" — דף ההגשה, ובדיקת ההרצה הסתיימה', line('OPEN') === 'true' && /SUBMIT-PAGE (bad|warn|good)/.test(line('PAGE')) && /SUBMIT-CHECK/.test(line('PAGE')), line('PAGE'));
check('בלי "שם המגיש" — "הגשה" לא פעיל; עם שם — פעיל', line('YES-BEFORE-NAME') === 'enabled=false' && line('YES-AFTER-NAME') === 'enabled=true' && line('WHO') === 'נועה לוי', [line('YES-BEFORE-NAME'), line('WHO'), line('YES-AFTER-NAME')]);
check('"הגשה" — נשלח, וחוזרים לשאלה עם "הוגש למורה ב-…"', line('RESULT') === 'ok' && /SUBMIT-NOTE הוגש למורה ב-\d{1,2}:\d{2}/.test(line('AFTER')), [line('RESULT'), line('AFTER')]);
check('"סיימתי את התרגיל" — האישור אומר שהתרגיל יוגש לפני הסיום', line('CONFIRM').includes('DONE-SUBMITS'), line('CONFIRM'));
check('"סיום ומחיקה" → דף ההגשה ("הגשה וסיום")', /then-complete/.test(line('PAGE2')), line('PAGE2'));
check('"הגשה וסיום": נשלח, השאלה מסומנת, קבצי התרגיל נמחקו והחלון נסגר', /^ok files-deleted=true done=true float-closed=true$/.test(line('RESULT2')), line('RESULT2'));

const con = await api('GET', '/v1/me/console', null, sara);
const st = con.stations.find(s => s.name === 'מעבדה · עמדה 7');
check('אצל המורה: העמדה "מעבדה · עמדה 7" עם 2 הגשות שממתינות', st && st.pending === 2, JSON.stringify(st));
const subs = st ? (await api('GET', '/v1/me/submissions?station=' + st.id, null, sara)).submissions : [];
const noa = subs.find(s => s.who === 'נועה לוי'), yoav = subs.find(s => s.who === 'יואב כהן');
check('אצל המורה: ההגשה של נועה לוי — מהמאגר (stack-sum-size), מ-Eclipse, עם בדיקת ההרצה ותאריך מחיקה', noa && noa.exercise.bankId === 'stack-sum-size' && noa.app === 'Eclipse'
  && noa.check.ran === true && typeof noa.check.compiles === 'boolean' && !!noa.expiresAt, JSON.stringify(noa));
check('אצל המורה: ההגשה של "סיימתי" — יואב כהן', !!yoav, JSON.stringify(subs.map(s => s.who)));
if (noa) {
  const one = await api('GET', '/v1/me/submission?id=' + noa.id, null, sara);
  check('אצל המורה: הקוד עצמו — StackTargil1.java ראשון', one.files.length >= 1 && one.files[0].path === 'StackTargil1.java' && one.files[0].text.includes('class StackTargil1'), one.files.map(f => f.path));
}
console.log(`\n${passed} בדיקות עברו · צילומים: ${SHOTS}`);
server.kill();
process.exit(process.exitCode || 0);
