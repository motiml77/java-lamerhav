/**
 * שחזור: תלמידה שנרשמת ל-lamerhav ולא רואה נושאים.
 * בודק כל שכבה בנפרד מול הנתונים האמיתיים, כדי לדעת אילו שכבות שבורות.
 */
const { chromium } = require('playwright');
const https = require('https'), fs = require('fs'), os = require('os'), path = require('path');

const PROJECT = 'exams-a93fb';
const SCHOOL = 'lamerhav';
const BASE = process.env.BASE || 'https://java-bagrut.vercel.app';
const KEY = (fs.readFileSync('C:/Users/Moti Levi/Desktop/AI/java Bagrut/app.html', 'utf8').match(/apiKey:\s*"([^"]+)"/) || [])[1];
const FSH = 'firestore.googleapis.com';
const DOCS = '/v1/projects/' + PROJECT + '/databases/(default)/documents';
const safe = e => e.replace(/[.@]/g, '_');
const STUDENT = { email: 'grade-probe@example.com', pass: 'Gr4de!2026pp' };

function oauth() {
  return new Promise(res => {
    const cfg = JSON.parse(fs.readFileSync(path.join(os.homedir(), '.config', 'configstore', 'firebase-tools.json'), 'utf8'));
    const b = 'grant_type=refresh_token&refresh_token=' + encodeURIComponent(cfg.tokens.refresh_token) +
      '&client_id=563584335869-fgrhgmd47bqnekij5i8b5pr03ho849e6.apps.googleusercontent.com&client_secret=j9iVZfS8kkCEFUPaAeJV0sAi';
    const r = https.request({ hostname: 'oauth2.googleapis.com', path: '/token', method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' } }, s => {
      let d = ''; s.on('data', c => d += c); s.on('end', () => res(JSON.parse(d).access_token));
    }); r.write(b); r.end();
  });
}
function call(host, p, method, headers, payload) {
  return new Promise(res => {
    const data = payload == null ? null : JSON.stringify(payload);
    const h = Object.assign({}, headers);
    if (data) h['Content-Length'] = Buffer.byteLength(data);
    const r = https.request({ hostname: host, path: p, method, headers: h }, s => {
      let d = ''; s.on('data', c => d += c); s.on('end', () => res({ code: s.statusCode, body: d }));
    });
    r.on('error', e => res({ code: 0, body: String(e) }));
    if (data) r.write(data); r.end();
  });
}
const J = { 'Content-Type': 'application/json' };
const adm = t => ({ Authorization: 'Bearer ' + t, 'Content-Type': 'application/json' });
const S = v => ({ stringValue: v });

(async () => {
  const tok = await oauth();
  const cfg = JSON.parse((await call('identitytoolkit.googleapis.com', '/admin/v2/projects/' + PROJECT + '/config', 'GET', adm(tok))).body);
  const wasEnabled = !!(cfg.signIn && cfg.signIn.email && cfg.signIn.email.enabled);
  if (!wasEnabled) await call('identitytoolkit.googleapis.com',
    '/admin/v2/projects/' + PROJECT + '/config?updateMask=signIn.email.enabled,signIn.email.passwordRequired',
    'PATCH', adm(tok), { signIn: { email: { enabled: true, passwordRequired: true } } });

  let b;
  try {
    let r = await call('identitytoolkit.googleapis.com', '/v1/accounts:signUp?key=' + KEY, 'POST', J,
      { email: STUDENT.email, password: STUDENT.pass, returnSecureToken: true });
    if (r.code !== 200) r = await call('identitytoolkit.googleapis.com', '/v1/accounts:signInWithPassword?key=' + KEY, 'POST', J,
      { email: STUDENT.email, password: STUDENT.pass, returnSecureToken: true });
    await call('identitytoolkit.googleapis.com', '/v1/projects/' + PROJECT + '/accounts:update', 'POST', adm(tok),
      { localId: JSON.parse(r.body).localId, emailVerified: true });

    try { b = await chromium.launch({ channel: 'chrome' }); } catch (e) { b = await chromium.launch({ channel: 'msedge' }); }

    for (const grade of ['י', 'יא', 'יב']) {
      // תלמידה מאושרת בשכבה הזו — בדיוק מה שנוצר בהרשמה + אישור המורה
      await call(FSH, DOCS + '/schools/' + SCHOOL + '/users/' + safe(STUDENT.email) + '?updateMask.fieldPaths=email&updateMask.fieldPaths=name&updateMask.fieldPaths=grade',
        'PATCH', adm(tok), { fields: {
          email: S(STUDENT.email), name: S('תלמידת בדיקה'), grade: S(grade) } });

      const pg = await b.newPage({ viewport: { width: 420, height: 900 } });
      await pg.goto(BASE + '/' + SCHOOL, { waitUntil: 'domcontentloaded', timeout: 60000 });
      await pg.waitForSelector('text=התחברות עם Google', { timeout: 45000 });
      await pg.evaluate(async ([e, p]) => { await firebase.auth().signInWithEmailAndPassword(e, p); },
        [STUDENT.email, STUDENT.pass]);
      await pg.waitForTimeout(11000);
      const body = await pg.textContent('body').catch(() => '');
      const topics = await pg.evaluate(() => {
        const names = ['משתנים', 'תנאים', 'לולאות', 'מערכים', 'מחרוזות', 'מחלקות', 'רשימות', 'תור ומחסנית', 'רקורסיה', 'עצים', 'בגרויות'];
        return names.filter(n => document.body.innerText.includes(n));
      });
      await pg.screenshot({ path: 'shots/grade-' + grade + '.png' });
      const pending = /ממתינה לאישור|בהמתנה/.test(body);
      console.log('שכבה ' + grade.padEnd(3) + ' → נושאים גלויים: ' + (topics.length ? topics.length + ' (' + topics.slice(0, 4).join(', ') + ')' : '*** אפס ***') + (pending ? '  [מסך המתנה]' : ''));
      await pg.close();
    }
  } catch (e) {
    console.log('שגיאה: ' + String(e && e.message).slice(0, 220));
  } finally {
    if (b) await b.close();
    await call(FSH, DOCS + '/schools/' + SCHOOL + '/users/' + safe(STUDENT.email), 'DELETE', adm(tok));
    const l = JSON.parse((await call('identitytoolkit.googleapis.com', '/v1/projects/' + PROJECT + '/accounts:lookup', 'POST', adm(tok), { email: [STUDENT.email] })).body);
    if (l.users && l.users[0]) await call('identitytoolkit.googleapis.com', '/v1/projects/' + PROJECT + '/accounts:delete', 'POST', adm(tok), { localId: l.users[0].localId });
    if (!wasEnabled) await call('identitytoolkit.googleapis.com',
      '/admin/v2/projects/' + PROJECT + '/config?updateMask=signIn.email.enabled', 'PATCH', adm(tok), { signIn: { email: { enabled: false } } });
    console.log('נוקה.');
    process.exit(0);
  }
})();
