/**
 * אימות הבידוד מול Firestore החי, עם משתמשים מחוברים אמיתיים.
 *
 * למה לא רק scripts/rules-isolation-test.js: ה-Rules Test API הוא מדמה.
 * בבדיקת חוקי Storage הוא החזיר תוצאות שגויות לשני הכיוונים, ולכן כלל אצבע —
 * חוק שמגן על נתונים אמיתיים נבדק מול השרת, לא מול המדמה.
 *
 * הסקריפט יוצר בית ספר בדיקה, נושא, ושני משתמשים (אחד חבר ואחד זר),
 * מנסה קריאות אמיתיות, ומנקה הכול בסוף.
 *
 * הרצה: node scripts/e2e-isolation-check.js
 */
const https = require('https'), fs = require('fs'), os = require('os'), path = require('path');

const PROJECT = 'exams-a93fb';
const SLUG = 'zz-iso-probe';
const MEMBER = { email: 'iso-member@example.com', pass: 'Pr0be!2026m' };
const OUTSIDER = { email: 'iso-outsider@example.com', pass: 'Pr0be!2026o' };
const KEY = (fs.readFileSync(path.join(__dirname, '..', 'app.html'), 'utf8').match(/apiKey:\s*"([^"]+)"/) || [])[1];
const safe = e => e.replace(/[.@]/g, '_');

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
    if (data) r.write(data);
    r.end();
  });
}
const J = { 'Content-Type': 'application/json' };
const adm = t => ({ Authorization: 'Bearer ' + t, 'Content-Type': 'application/json' });
const asUser = t => ({ Authorization: 'Bearer ' + t, 'Content-Type': 'application/json' });
const FS = 'firestore.googleapis.com';
const DOCS = `/v1/projects/${PROJECT}/databases/(default)/documents`;

let pass = 0, fail = 0;
function check(label, got, expect, body) {
  const ok = got === expect; ok ? pass++ : fail++;
  console.log((ok ? 'PASS ' : 'FAIL ') + label + ' → ' + got + (ok ? '' : ' (ציפינו ' + expect + ')'));
  if (!ok && body) console.log('      ' + String(body).replace(/\s+/g, ' ').slice(0, 180));
}

(async () => {
  const tok = await oauth();
  const cfg = JSON.parse((await call('identitytoolkit.googleapis.com', `/admin/v2/projects/${PROJECT}/config`, 'GET', adm(tok))).body);
  const wasEnabled = !!(cfg.signIn && cfg.signIn.email && cfg.signIn.email.enabled);
  if (!wasEnabled) {
    await call('identitytoolkit.googleapis.com',
      `/admin/v2/projects/${PROJECT}/config?updateMask=signIn.email.enabled,signIn.email.passwordRequired`,
      'PATCH', adm(tok), { signIn: { email: { enabled: true, passwordRequired: true } } });
  }
  try {
    // משתמשים
    const users = {};
    for (const u of [MEMBER, OUTSIDER]) {
      let r = await call('identitytoolkit.googleapis.com', `/v1/accounts:signUp?key=${KEY}`, 'POST', J,
        { email: u.email, password: u.pass, returnSecureToken: true });
      if (r.code !== 200) r = await call('identitytoolkit.googleapis.com', `/v1/accounts:signInWithPassword?key=${KEY}`, 'POST', J,
        { email: u.email, password: u.pass, returnSecureToken: true });
      const j = JSON.parse(r.body);
      if (!j.idToken) { console.log('אין idToken: ' + r.body.slice(0, 160)); return; }
      await call('identitytoolkit.googleapis.com', `/v1/projects/${PROJECT}/accounts:update`, 'POST', adm(tok),
        { localId: j.localId, emailVerified: true });
      const re = JSON.parse((await call('identitytoolkit.googleapis.com', `/v1/accounts:signInWithPassword?key=${KEY}`, 'POST', J,
        { email: u.email, password: u.pass, returnSecureToken: true })).body);
      users[u.email] = re.idToken;
    }
    console.log('שני משתמשים מחוברים ומאומתים\n');

    // בית ספר בדיקה + נושא + חברות רק ל-MEMBER
    await call(FS, `${DOCS}/schools?documentId=${SLUG}`, 'POST', adm(tok), { fields: {
      slug: { stringValue: SLUG }, name: { stringValue: 'בדיקת בידוד' },
      teacherEmail: { stringValue: 'teacher@iso.example' }, status: { stringValue: 'active' } } });
    await call(FS, `${DOCS}/schools/${SLUG}/classes?documentId=c1`, 'POST', adm(tok), { fields: {
      title: { stringValue: 'לולאות' }, topicKey: { stringValue: 'loops' } } });
    await call(FS, `${DOCS}/schools/${SLUG}/users?documentId=${safe(MEMBER.email)}`, 'POST', adm(tok), { fields: {
      email: { stringValue: MEMBER.email }, name: { stringValue: 'חברה' }, approved: { booleanValue: true } } });

    const readClass = (tokenStr) => call(FS, `${DOCS}/schools/${SLUG}/classes/c1`, 'GET', asUser(tokenStr));

    let r = await readClass(users[MEMBER.email]);
    check('תלמידה של בית הספר קוראת נושא          ', r.code, 200, r.body);
    r = await readClass(users[OUTSIDER.email]);
    check('משתמשת שאינה שייכת קוראת נושא          ', r.code, 403, r.body);

    // הזרה מנסה לקרוא מדיה, שאלות והגדרות מבחן
    for (const [c, label] of [['questions/q1', 'שאלת מבחן'], ['exam_settings/e1', 'הגדרות מבחן'], ['media/m1', 'מדיה']]) {
      r = await call(FS, `${DOCS}/schools/${SLUG}/${c}`, 'GET', asUser(users[OUTSIDER.email]));
      check(('זרה קוראת ' + label).padEnd(38), r.code, 403, r.body);
    }

    // הרשמה: הזרה כן יוצרת את מסמך עצמה
    r = await call(FS, `${DOCS}/schools/${SLUG}/users?documentId=${safe(OUTSIDER.email)}`, 'POST', asUser(users[OUTSIDER.email]),
      { fields: { email: { stringValue: OUTSIDER.email }, name: { stringValue: 'חדשה' }, approved: { booleanValue: false } } });
    check('משתמשת חדשה נרשמת                      ', r.code, 200, r.body);

    // אחרי ההרשמה — עדיין DENY! נרשמה אבל לא אושרה. אם היו מקבלות ALLOW כאן,
    // כל אחת הייתה יכולה להעניק לעצמה חברות בכל בית ספר בלי אישור מורה כלל —
    // בדיוק זו הייתה הבדיקה השגויה שנעלה כאן לפני התיקון.
    r = await readClass(users[OUTSIDER.email]);
    check('אחרי הרשמה בלי אישור עדיין לא קוראת    ', r.code, 403, r.body);

    // המורה מאשרת — ורק אז יש קריאה
    await call(FS, `${DOCS}/schools/${SLUG}/users/${safe(OUTSIDER.email)}?updateMask.fieldPaths=approved`,
      'PATCH', adm(tok), { fields: { approved: { booleanValue: true } } });
    r = await readClass(users[OUTSIDER.email]);
    check('אחרי אישור המורה כן קוראת              ', r.code, 200, r.body);

    console.log('\n===== ' + pass + '/' + (pass + fail) + ' =====');
  } finally {
    for (const p2 of [`schools/${SLUG}/classes/c1`, `schools/${SLUG}/users/${safe(MEMBER.email)}`,
                      `schools/${SLUG}/users/${safe(OUTSIDER.email)}`, `schools/${SLUG}`]) {
      await call(FS, `${DOCS}/${p2}`, 'DELETE', adm(tok));
    }
    for (const u of [MEMBER, OUTSIDER]) {
      const l = JSON.parse((await call('identitytoolkit.googleapis.com', `/v1/projects/${PROJECT}/accounts:lookup`, 'POST', adm(tok), { email: [u.email] })).body);
      if (l.users && l.users[0]) await call('identitytoolkit.googleapis.com', `/v1/projects/${PROJECT}/accounts:delete`, 'POST', adm(tok), { localId: l.users[0].localId });
    }
    if (!wasEnabled) await call('identitytoolkit.googleapis.com', `/admin/v2/projects/${PROJECT}/config?updateMask=signIn.email.enabled`,
      'PATCH', adm(tok), { signIn: { email: { enabled: false } } });
    console.log('ניקוי הושלם.');
  }
})();
