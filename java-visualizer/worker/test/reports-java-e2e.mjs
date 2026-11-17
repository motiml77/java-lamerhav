// הלקוח האמיתי של הדיווחים (access/…/Reports.java — אותו קוד ב-Eclipse, IntelliJ וב-Visual Studio) מול שרת מקומי ואמולטור:
// בלי אינטרנט — הדיווח נשאר בתיבת היוצא; השרת חוזר — נשלח ונמחק; עמדת מחשב — מופיע מי שלח ושל איזה מורה; תיקיית הבית ושם
// המשתמש מוחלפים; שליחה כפולה של אותו קובץ — נשמר פעם אחת.
//   npm run test:reports-java     (צריך Java; בונה את tools/test/access/ReportsDriver.java מול build/access/dsviz-access.jar)
import assert from 'node:assert/strict';
import { spawn, execFileSync } from 'node:child_process';
import { createHash } from 'node:crypto';
import { mkdtempSync, mkdirSync, readdirSync, copyFileSync, writeFileSync, readFileSync, existsSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const root = join(here, '../..');
const PORT = 8789, BASE = `http://127.0.0.1:${PORT}`;
const AUTH = process.env.FIREBASE_AUTH_EMULATOR_HOST || '127.0.0.1:9099';
const FS = process.env.FIRESTORE_EMULATOR_HOST || '127.0.0.1:8181';
await fetch(`http://${FS}/emulator/v1/projects/demo-dsviz/databases/(default)/documents`, { method: 'DELETE' });

// ReportsDriver מול ה-jar המשותף
/** בלי JAVA_TOOL_OPTIONS (אחרת Java מדפיס "Picked up…" בכל הרצה) */
const javaEnv = extra => { const e = Object.assign({}, process.env, extra || {}); delete e.JAVA_TOOL_OPTIONS; return e; };
const work = mkdtempSync(join(tmpdir(), 'reports-java-'));
const cls = join(work, 'cls');
mkdirSync(cls);
execFileSync('sh', [join(root, 'tools/build-access.sh')], { cwd: root, stdio: 'ignore', env: javaEnv() });
const jar = join(root, 'build/access/dsviz-access.jar');
execFileSync('javac', ['-encoding', 'UTF-8', '-nowarn', '-cp', jar, '-d', cls, join(root, 'tools/test/access/ReportsDriver.java')], { env: javaEnv() });
const home = join(work, 'home', 'דנה כהן');   // תיקיית בית עם רווח ועברית — כמו במחשבים של תלמידים
mkdirSync(home, { recursive: true });
const outbox = join(work, 'outbox');
const pubFile = join(work, 'pass.pub'), keyFile = join(work, 'pass.key');
function java(server, ...args) {
  return new Promise((ok, bad) => {
    const pub = existsSync(pubFile) ? readFileSync(pubFile, 'utf8').trim() : '';   // המפתח הציבורי של השרת המקומי — כדי שהאישור החתום יאומת
    const p = spawn('java', [`-Ddsviz.worker=${server}`, `-Ddsviz.passKey=${pub}`, `-Ddsviz.reports=${outbox}`, `-Duser.home=${home}`, `-Ddsviz.home=${join(home, '.dsviz')}`, '-Duser.name=dana.cohen',
      '-Dfile.encoding=UTF-8', '-cp', cls + ':' + jar, 'il.lamerhav.dsviz.ReportsDriver', ...args], { env: javaEnv({ HOME: home, LC_ALL: 'C.UTF-8', LANG: 'C.UTF-8' }) });   // נתיבים בעברית — כמו ב-Windows
    let out = '';
    p.stdout.on('data', d => { out += d; });
    p.stderr.on('data', d => { out += d; });
    const t = setTimeout(() => { p.kill(); bad(new Error('java: ' + out)); }, 60000);
    p.on('exit', () => { clearTimeout(t); ok(out.trim()); });
  });
}
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

let passed = 0;
async function step(name, fn) {
  try { await fn(); passed++; console.log('✓ ' + name); } catch (e) { console.error('✗ ' + name); console.error(e); process.exitCode = 1; }
}

let server = null;
const startServer = async () => {
  server = spawn(process.execPath, [join(here, '../tools/dev-server.mjs')], { env: Object.assign({}, process.env, { PORT: String(PORT), ADMIN_EMAILS: 'admin@test.dev', PASS_KEY_FILE: keyFile, PUBLIC_KEY_FILE: pubFile }), stdio: ['ignore', 'pipe', 'inherit'] });
  await new Promise(res => server.stdout.on('data', d => { if (String(d).includes('dev server')) res(); }));
};

let firstId;
await step('בלי אינטרנט (השרת כבוי): הדיווח נשמר בתיבת היוצא ולא הולך לאיבוד', async () => {
  const q = await java(BASE, 'queue', 'ההרצה הוויזואלית לא התחילה', `error in ${home}/eclipse-workspace/Targil1/src/Main.java (user dana.cohen)`);
  firstId = /QUEUED (\S+)/.exec(q)[1];
  assert.equal(await java(BASE, 'flush'), 'SENT 0 LEFT 1');
  assert.equal(readdirSync(outbox).filter(f => f.endsWith('.json')).length, 1);
});

await startServer();
const admin = await idToken('admin@test.dev', 'מנהל');
await api('POST', '/v1/admin/teacher', { create: true, email: 'sara@test.dev', name: 'שרה כהן', school: 'אורט רחובות', seats: 5, phone: '0521234567' }, admin);
const code = (await api('GET', '/v1/admin/teacher?email=sara@test.dev', null, admin)).teacher.code;

await step('השרת חזר: הדיווח נשלח ונמחק מהמחשב; תיקיית הבית ושם המשתמש הוחלפו', async () => {
  assert.equal(await java(BASE, 'flush'), 'SENT 1 LEFT 0');
  const r = (await api('GET', '/v1/admin/report?id=' + firstId, null, admin));
  assert.equal(r.report.who.kind, 'none');
  assert.equal(r.report.app, 'Eclipse 2024-09');
  assert.ok(!r.report.message.includes('דנה כהן') && !r.report.message.includes('dana.cohen'), r.report.message);
  assert.match(r.report.message, /%USERPROFILE%\/eclipse-workspace\/Targil1/);
  assert.match(r.report.message, /\(user %USERNAME%\)/);
  assert.equal(r.report.context.project, '%USERPROFILE%/eclipse-workspace/Targil1');
  assert.match(r.diag, /home=%USERPROFILE%\nuser=%USERNAME%/);
  assert.match(r.report.os, /Java/);
});

await step('עמדת מחשב: הדיווח מגיע עם שם העמדה ופרטי המורה (טלפון, בית ספר)', async () => {
  assert.equal(await java(BASE, 'claim', code, 'עמדה 4'), 'CLAIM ok');
  const id = /QUEUED (\S+)/.exec(await java(BASE, 'queue', 'שגיאה פנימית', 'x'))[1];
  assert.equal(await java(BASE, 'flush'), 'SENT 1 LEFT 0');
  const r = await api('GET', '/v1/admin/report?id=' + id, null, admin);
  assert.equal(r.report.who.kind, 'station');
  assert.equal(r.report.who.stationName, 'עמדה 4');
  assert.deepEqual([r.report.teacher.name, r.report.teacher.school, r.report.teacher.phone], ['שרה כהן', 'אורט רחובות', '0521234567']);
});

await step('אותו דיווח נשלח פעמיים (נשאר עותק אחרי ניתוק באמצע) — נשמר פעם אחת', async () => {
  const id = /QUEUED (\S+)/.exec(await java(BASE, 'queue', 'כפול', 'x'))[1];
  const f = readdirSync(outbox).find(n => n.includes(id));
  copyFileSync(join(outbox, f), join(outbox, '0' + f));
  assert.equal(await java(BASE, 'flush'), 'SENT 2 LEFT 0');
  const list = (await api('GET', '/v1/admin/reports', null, admin)).reports.filter(x => x.title === 'כפול');
  assert.equal(list.length, 1);
});

await step('קובץ פגום בתיבת היוצא לא תוקע את השאר', async () => {
  writeFileSync(join(outbox, '00000000000000000-broken.json'), '{"id":"x"}');
  await java(BASE, 'queue', 'אחרי קובץ פגום', 'x');
  assert.equal(await java(BASE, 'flush'), 'SENT 1 LEFT 0');
});

server.kill();
console.log(`\n${passed} בדיקות עברו` + (process.exitCode ? ' — יש כישלונות' : ''));
