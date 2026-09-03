/**
 * אימות חוקי school_requests מול Firestore החי, עם משתמשים מחוברים אמיתיים.
 *
 * הבדיקה המרכזית: מורה לא יכול לאשר את הבקשה של עצמו. הגייט שבממשק הוא
 * תצוגה בלבד — מי שיודע לפתוח קונסולה יכול לקרוא ל-SDK ישירות, ולכן
 * ההסלמה נבדקת כאן מול השרת ולא מול מדמה.
 *
 * הרצה: node scripts/e2e-school-requests.js
 */
const https = require('https'), fs = require('fs'), os = require('os'), path = require('path');
const PROJECT = 'exams-a93fb';
const KEY = (fs.readFileSync(path.join(__dirname, '..', 'app.html'), 'utf8').match(/apiKey:\s*"([^"]+)"/) || [])[1];
const FS = 'firestore.googleapis.com';
const DOCS = '/v1/projects/' + PROJECT + '/databases/(default)/documents';
const safe = e => e.replace(/[.@]/g, '_');

const ASKER = { email: 'req-teacher@example.com', pass: 'Pr0be!2026rq' };
const OTHER = { email: 'req-outsider@example.com', pass: 'Pr0be!2026ot' };

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
const S = v => ({ stringValue: v });

let pass = 0, fail = 0;
function check(label, got, expect, body) {
  const ok = got === expect; ok ? pass++ : fail++;
  console.log((ok ? 'PASS ' : 'FAIL ') + label + ' -> ' + got + (ok ? '' : ' (ציפינו ' + expect + ')'));
  if (!ok && body) console.log('      ' + String(body).replace(/\s+/g, ' ').slice(0, 200));
}

(async () => {
  const tok = await oauth();
  const cfg = JSON.parse((await call('identitytoolkit.googleapis.com', '/admin/v2/projects/' + PROJECT + '/config', 'GET', adm(tok))).body);
  const wasEnabled = !!(cfg.signIn && cfg.signIn.email && cfg.signIn.email.enabled);
  if (!wasEnabled) await call('identitytoolkit.googleapis.com',
    '/admin/v2/projects/' + PROJECT + '/config?updateMask=signIn.email.enabled,signIn.email.passwordRequired',
    'PATCH', adm(tok), { signIn: { email: { enabled: true, passwordRequired: true } } });

  const idTok = {};
  try {
    for (const u of [ASKER, OTHER]) {
      let r = await call('identitytoolkit.googleapis.com', '/v1/accounts:signUp?key=' + KEY, 'POST', J,
        { email: u.email, password: u.pass, returnSecureToken: true });
      if (r.code !== 200) r = await call('identitytoolkit.googleapis.com', '/v1/accounts:signInWithPassword?key=' + KEY, 'POST', J,
        { email: u.email, password: u.pass, returnSecureToken: true });
      const j = JSON.parse(r.body);
      if (!j.idToken) { console.log('אין idToken ל-' + u.email); return; }
      await call('identitytoolkit.googleapis.com', '/v1/projects/' + PROJECT + '/accounts:update', 'POST', adm(tok),
        { localId: j.localId, emailVerified: true });
      const re = JSON.parse((await call('identitytoolkit.googleapis.com', '/v1/accounts:signInWithPassword?key=' + KEY, 'POST', J,
        { email: u.email, password: u.pass, returnSecureToken: true })).body);
      idTok[u.email] = re.idToken;
    }
    const asU = e => ({ Authorization: 'Bearer ' + idTok[e], 'Content-Type': 'application/json' });
    const RID = safe(ASKER.email), OID = safe(OTHER.email);
    const REQ = 'school_requests';
    const reqFields = (status, slug) => ({ fields: {
      email: S(ASKER.email), name: S('מורה מבקש'), schoolName: S('תיכון בדיקה'),
      requestedSlug: S(slug || 'zz-req-school'), status: S(status), createdAt: S(new Date().toISOString()) } });

    console.log('--- יצירה ---');
    let r = await call(FS, DOCS + '/' + REQ + '?documentId=' + RID, 'POST', asU(ASKER.email), reqFields('pending'));
    check('מורה יוצר בקשה ממתינה שלו       ', r.code, 200, r.body);

    r = await call(FS, DOCS + '/' + REQ + '?documentId=zz_forged_id', 'POST', asU(ASKER.email), reqFields('pending'));
    check('יצירה במזהה שאינו שלו           ', r.code, 403, r.body);

    r = await call(FS, DOCS + '/' + REQ + '?documentId=' + OID, 'POST', asU(ASKER.email), reqFields('pending'));
    check('יצירה בשם משתמש אחר             ', r.code, 403, r.body);

    r = await call(FS, DOCS + '/' + REQ + '?documentId=' + RID + '_x', 'POST', asU(ASKER.email),
      { fields: { email: S(OTHER.email), status: S('pending'), schoolName: S('התחזות') } });
    check('בקשה עם אימייל של אחר           ', r.code, 403, r.body);

    console.log('--- ההסלמה: אישור עצמי ---');
    r = await call(FS, DOCS + '/' + REQ + '/' + RID + '?updateMask.fieldPaths=status', 'PATCH', asU(ASKER.email),
      { fields: { status: S('approved') } });
    check('מורה מאשר את הבקשה של עצמו      ', r.code, 403, r.body);

    r = await call(FS, DOCS + '/schools?documentId=zz-req-school', 'POST', asU(ASKER.email),
      { fields: { slug: S('zz-req-school'), name: S('עוקף'), teacherEmail: S(ASKER.email), status: S('active') } });
    check('מורה יוצר בית ספר ישירות        ', r.code, 403, r.body);

    console.log('--- קריאה ---');
    r = await call(FS, DOCS + '/' + REQ + '/' + RID, 'GET', asU(ASKER.email));
    check('מורה קורא את הבקשה שלו          ', r.code, 200, r.body);
    r = await call(FS, DOCS + '/' + REQ + '/' + RID, 'GET', asU(OTHER.email));
    check('משתמש אחר קורא בקשה זרה         ', r.code, 403, r.body);
    r = await call(FS, DOCS + '/' + REQ + '?pageSize=50', 'GET', asU(ASKER.email));
    check('מורה מושך את התור כולו          ', r.code, 403, r.body);
    r = await call(FS, DOCS + '/' + REQ + '?pageSize=50', 'GET', adm(tok));
    check('המנהל מושך את התור              ', r.code, 200, r.body);

    console.log('--- עדכון עצמי לפני החלטה ---');
    r = await call(FS, DOCS + '/' + REQ + '/' + RID + '?updateMask.fieldPaths=schoolName', 'PATCH', asU(ASKER.email),
      { fields: { schoolName: S('תיכון מתוקן') } });
    check('מתקן פרטים כל עוד ממתין         ', r.code, 200, r.body);

    console.log('--- אחרי החלטת המנהל ---');
    await call(FS, DOCS + '/' + REQ + '/' + RID + '?updateMask.fieldPaths=status', 'PATCH', adm(tok), { fields: { status: S('approved') } });
    r = await call(FS, DOCS + '/' + REQ + '/' + RID + '?updateMask.fieldPaths=requestedSlug', 'PATCH', asU(ASKER.email),
      { fields: { requestedSlug: S('zz-hijack') } });
    check('משנה slug אחרי שאושרה           ', r.code, 403, r.body);

    await call(FS, DOCS + '/' + REQ + '/' + RID + '?updateMask.fieldPaths=status', 'PATCH', adm(tok), { fields: { status: S('rejected') } });
    r = await call(FS, DOCS + '/' + REQ + '/' + RID + '?updateMask.fieldPaths=status', 'PATCH', asU(ASKER.email),
      { fields: { status: S('pending') } });
    check('מחייה בקשה שנדחתה               ', r.code, 403, r.body);

    r = await call(FS, DOCS + '/' + REQ + '/' + RID, 'DELETE', asU(ASKER.email));
    check('מוחק את הבקשה שלו               ', r.code, 403, r.body);

    console.log('\n===== ' + pass + '/' + (pass + fail) + ' =====');
  } finally {
    for (const id of [safe(ASKER.email), safe(OTHER.email), 'zz_forged_id', safe(ASKER.email) + '_x']) {
      await call(FS, DOCS + '/school_requests/' + id, 'DELETE', adm(tok));
    }
    await call(FS, DOCS + '/schools/zz-req-school', 'DELETE', adm(tok));
    for (const u of [ASKER, OTHER]) {
      const l = JSON.parse((await call('identitytoolkit.googleapis.com', '/v1/projects/' + PROJECT + '/accounts:lookup', 'POST', adm(tok), { email: [u.email] })).body);
      if (l.users && l.users[0]) await call('identitytoolkit.googleapis.com', '/v1/projects/' + PROJECT + '/accounts:delete', 'POST', adm(tok), { localId: l.users[0].localId });
    }
    if (!wasEnabled) await call('identitytoolkit.googleapis.com',
      '/admin/v2/projects/' + PROJECT + '/config?updateMask=signIn.email.enabled', 'PATCH', adm(tok), { signIn: { email: { enabled: false } } });
    console.log('נוקה.');
    process.exit(fail > 0 ? 1 : 0);
  }
})();
