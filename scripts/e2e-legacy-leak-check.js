/**
 * דליפת אוספי השורש (מצב legacy).
 *
 * הרקע: המיגרציה העתיקה את lamerhav אל schools/lamerhav ולא מחקה כלום, ולכן
 * אוספי השורש עדיין מחזיקים עותק מלא של התוכן. בקוד, כל כתובת שאינה slug תקין
 * (למשל /app.html) מאפסת את SCHOOL_SLUG, ואז col() נופל לאוספי השורש.
 * החוקים בשורש התירו קריאה לכל isSignedIn() — כלומר לכל מחוברת מכל בית ספר.
 *
 * הבדיקה: משתמשת שהיא חברה רק בבית ספר בדיקה מנסה לקרוא את אוספי השורש.
 * מריצים אותה לפני ואחרי התיקון.
 *
 * הרצה: node scripts/e2e-legacy-leak-check.js
 */
const https = require('https'), fs = require('fs'), os = require('os'), path = require('path');
const PROJECT = 'exams-a93fb';
const KEY = (fs.readFileSync(path.join(__dirname, '..', 'app.html'), 'utf8').match(/apiKey:\s*"([^"]+)"/) || [])[1];
const FS = 'firestore.googleapis.com';
const DOCS = `/v1/projects/${PROJECT}/databases/(default)/documents`;
const safe = e => e.replace(/[.@]/g, '_');
const OUT = { email: 'legacy-outsider@example.com', pass: 'Pr0be!2026lo' };
const IN  = { email: 'legacy-member@example.com',   pass: 'Pr0be!2026lm' };
const SLUG = 'zz-legacy-probe';
const ROOT_COLLS = ['classes', 'questions', 'exam_settings', 'media', 'practice', 'notifications'];

function oauth() {
  return new Promise(res => {
    const cfg = JSON.parse(fs.readFileSync(path.join(os.homedir(), '.config', 'configstore', 'firebase-tools.json'), 'utf8'));
    const b = 'grant_type=refresh_token&refresh_token=' + encodeURIComponent(cfg.tokens.refresh_token) +
      '&client_id=563584335869-fgrhgmd47bqnekij5i8b5pr03ho849e6.apps.googleusercontent.com&client_secret=j9iVZfS8kkCEFUPaAeJV0sAi';
    const r = https.request({ hostname: 'oauth2.googleapis.com', path: '/token', method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' } }, s => {
      let d = ''; s.on('data', c => d += c); s.on('end', () => res(JSON.parse(d).access_token));
    });
    r.write(b); r.end();
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

(async () => {
  const tok = await oauth();
  const cfg = JSON.parse((await call('identitytoolkit.googleapis.com', `/admin/v2/projects/${PROJECT}/config`, 'GET', adm(tok))).body);
  const wasEnabled = !!(cfg.signIn && cfg.signIn.email && cfg.signIn.email.enabled);
  if (!wasEnabled) await call('identitytoolkit.googleapis.com',
    `/admin/v2/projects/${PROJECT}/config?updateMask=signIn.email.enabled,signIn.email.passwordRequired`,
    'PATCH', adm(tok), { signIn: { email: { enabled: true, passwordRequired: true } } });

  const tokens = {};
  try {
    for (const u of [OUT, IN]) {
      let r = await call('identitytoolkit.googleapis.com', `/v1/accounts:signUp?key=${KEY}`, 'POST', J,
        { email: u.email, password: u.pass, returnSecureToken: true });
      if (r.code !== 200) r = await call('identitytoolkit.googleapis.com', `/v1/accounts:signInWithPassword?key=${KEY}`, 'POST', J,
        { email: u.email, password: u.pass, returnSecureToken: true });
      const j = JSON.parse(r.body);
      await call('identitytoolkit.googleapis.com', `/v1/projects/${PROJECT}/accounts:update`, 'POST', adm(tok),
        { localId: j.localId, emailVerified: true });
      const re = JSON.parse((await call('identitytoolkit.googleapis.com', `/v1/accounts:signInWithPassword?key=${KEY}`, 'POST', J,
        { email: u.email, password: u.pass, returnSecureToken: true })).body);
      tokens[u.email] = re.idToken;
    }
    // הזרה חברה רק בבית ספר בדיקה; החברה קיימת גם באוסף users בשורש (כמו תלמידת lamerhav)
    await call(FS, `${DOCS}/schools?documentId=${SLUG}`, 'POST', adm(tok), { fields: {
      slug: { stringValue: SLUG }, name: { stringValue: 'בדיקה' },
      teacherEmail: { stringValue: 'nobody@example.com' }, status: { stringValue: 'active' } } });
    await call(FS, `${DOCS}/schools/${SLUG}/users?documentId=${safe(OUT.email)}`, 'POST', adm(tok), { fields: {
      email: { stringValue: OUT.email }, approved: { booleanValue: true } } });
    await call(FS, `${DOCS}/users?documentId=${safe(IN.email)}`, 'POST', adm(tok), { fields: {
      email: { stringValue: IN.email }, name: { stringValue: 'תלמידת legacy' }, approved: { booleanValue: true } } });

    console.log('אוספי השורש — מה רואה משתמשת שאינה שייכת ל-lamerhav:\n');
    let leaked = 0;
    for (const c of ROOT_COLLS) {
      const r = await call(FS, `${DOCS}/${c}?pageSize=3`, 'GET', { Authorization: 'Bearer ' + tokens[OUT.email] });
      const n = r.code === 200 ? ((JSON.parse(r.body).documents || []).length) : 0;
      if (r.code === 200) leaked++;
      console.log((r.code === 200 ? '  דולף!  ' : '  חסום   ') + c.padEnd(15) + ' → ' + r.code + (n ? '  (' + n + ' מסמכים)' : ''));
    }
    console.log('\nובדיקה שלא שברנו את האתר החי — תלמידת legacy אמיתית:\n');
    let broke = 0;
    for (const c of ROOT_COLLS) {
      const r = await call(FS, `${DOCS}/${c}?pageSize=3`, 'GET', { Authorization: 'Bearer ' + tokens[IN.email] });
      if (r.code !== 200) broke++;
      console.log((r.code === 200 ? '  תקין   ' : '  נשבר!  ') + c.padEnd(15) + ' → ' + r.code);
    }
    console.log('\n===== דולפים: ' + leaked + '/' + ROOT_COLLS.length + ' · שבורים: ' + broke + '/' + ROOT_COLLS.length + ' =====');
  } finally {
    await call(FS, `${DOCS}/schools/${SLUG}/users/${safe(OUT.email)}`, 'DELETE', adm(tok));
    await call(FS, `${DOCS}/schools/${SLUG}`, 'DELETE', adm(tok));
    await call(FS, `${DOCS}/users/${safe(IN.email)}`, 'DELETE', adm(tok));
    for (const u of [OUT, IN]) {
      const l = JSON.parse((await call('identitytoolkit.googleapis.com', `/v1/projects/${PROJECT}/accounts:lookup`, 'POST', adm(tok), { email: [u.email] })).body);
      if (l.users && l.users[0]) await call('identitytoolkit.googleapis.com', `/v1/projects/${PROJECT}/accounts:delete`, 'POST', adm(tok), { localId: l.users[0].localId });
    }
    if (!wasEnabled) await call('identitytoolkit.googleapis.com', `/admin/v2/projects/${PROJECT}/config?updateMask=signIn.email.enabled`,
      'PATCH', adm(tok), { signIn: { email: { enabled: false } } });
    console.log('ניקוי הושלם.');
  }
})();
