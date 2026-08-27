/**
 * אימות ריבוי בתי ספר מול Firestore החי, עם משתמשים מחוברים אמיתיים ובמקביל.
 *
 * בונה שלושה בתי ספר (שניים פעילים, אחד מושהה), חמישה משתמשים, ותוכן בכל אחד.
 * מריץ את כל הבדיקות פעמיים: פעם בטורי ופעם בבת אחת במקביל — כדי לתפוס גם
 * דליפה שתלויה במקביליות. בסוף מנקה הכול.
 *
 * הרצה: node scripts/e2e-multitenant-check.js
 */
const https = require('https'), fs = require('fs'), os = require('os'), path = require('path');

const PROJECT = 'exams-a93fb';
const KEY = (fs.readFileSync(path.join(__dirname, '..', 'app.html'), 'utf8').match(/apiKey:\s*"([^"]+)"/) || [])[1];
const FS = 'firestore.googleapis.com';
const DOCS = `/v1/projects/${PROJECT}/databases/(default)/documents`;
const safe = e => e.replace(/[.@]/g, '_');

const SCHOOLS = [
  { slug: 'zz-alpha', name: 'תיכון אלפא', status: 'active', teacher: 'alpha-teacher@example.com' },
  { slug: 'zz-beta',  name: 'תיכון בטא',  status: 'active', teacher: 'beta-teacher@example.com' },
  { slug: 'zz-susp',  name: 'תיכון מושהה', status: 'suspended', teacher: 'susp-teacher@example.com' },
];
const USERS = [
  { email: 'alpha-student@example.com', pass: 'Pr0be!2026as', school: 'zz-alpha', role: 'student' },
  { email: 'alpha-student2@example.com', pass: 'Pr0be!2026a2', school: 'zz-alpha', role: 'student' },
  { email: 'beta-student@example.com',  pass: 'Pr0be!2026bs', school: 'zz-beta',  role: 'student' },
  { email: 'alpha-teacher@example.com', pass: 'Pr0be!2026at', school: 'zz-alpha', role: 'teacher' },
  { email: 'beta-teacher@example.com',  pass: 'Pr0be!2026bt', school: 'zz-beta',  role: 'teacher' },
  { email: 'susp-student@example.com',  pass: 'Pr0be!2026ss', school: 'zz-susp',  role: 'student' },
  { email: 'susp-teacher@example.com',  pass: 'Pr0be!2026st', school: 'zz-susp',  role: 'teacher' },
];
const CONTENT = ['classes/c1', 'questions/q1', 'exam_settings/e1', 'media/m1', 'practice/p1', 'notifications/n1'];

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
const asU = t => ({ Authorization: 'Bearer ' + t, 'Content-Type': 'application/json' });
const S = v => ({ stringValue: v });

(async () => {
  const tok = await oauth();
  const cfg = JSON.parse((await call('identitytoolkit.googleapis.com', `/admin/v2/projects/${PROJECT}/config`, 'GET', adm(tok))).body);
  const wasEnabled = !!(cfg.signIn && cfg.signIn.email && cfg.signIn.email.enabled);
  if (!wasEnabled) await call('identitytoolkit.googleapis.com',
    `/admin/v2/projects/${PROJECT}/config?updateMask=signIn.email.enabled,signIn.email.passwordRequired`,
    'PATCH', adm(tok), { signIn: { email: { enabled: true, passwordRequired: true } } });

  const idTok = {};
  try {
    // ---------- הקמה ----------
    for (const u of USERS) {
      let r = await call('identitytoolkit.googleapis.com', `/v1/accounts:signUp?key=${KEY}`, 'POST', J,
        { email: u.email, password: u.pass, returnSecureToken: true });
      if (r.code !== 200) r = await call('identitytoolkit.googleapis.com', `/v1/accounts:signInWithPassword?key=${KEY}`, 'POST', J,
        { email: u.email, password: u.pass, returnSecureToken: true });
      const j = JSON.parse(r.body);
      if (!j.idToken) { console.log('אין idToken ל-' + u.email); return; }
      await call('identitytoolkit.googleapis.com', `/v1/projects/${PROJECT}/accounts:update`, 'POST', adm(tok),
        { localId: j.localId, emailVerified: true });
      const re = JSON.parse((await call('identitytoolkit.googleapis.com', `/v1/accounts:signInWithPassword?key=${KEY}`, 'POST', J,
        { email: u.email, password: u.pass, returnSecureToken: true })).body);
      idTok[u.email] = re.idToken;
    }
    for (const sc of SCHOOLS) {
      await call(FS, `${DOCS}/schools?documentId=${sc.slug}`, 'POST', adm(tok), { fields: {
        slug: S(sc.slug), name: S(sc.name), teacherEmail: S(sc.teacher), status: S(sc.status) } });
      for (const c of CONTENT) {
        const [coll, id] = c.split('/');
        await call(FS, `${DOCS}/schools/${sc.slug}/${coll}?documentId=${id}`, 'POST', adm(tok),
          { fields: { title: S('סוד של ' + sc.slug), owner: S(sc.slug) } });
      }
      // תשובת תלמידה — נתון אישי
      await call(FS, `${DOCS}/schools/${sc.slug}/exam_responses?documentId=e1`, 'POST', adm(tok), { fields: { owner: S(sc.slug) } });
    }
    for (const u of USERS.filter(x => x.role === 'student')) {
      await call(FS, `${DOCS}/schools/${u.school}/users?documentId=${safe(u.email)}`, 'POST', adm(tok),
        { fields: { email: S(u.email), name: S('תלמידה'), approved: { booleanValue: true } } });
      await call(FS, `${DOCS}/schools/${u.school}/exam_responses/e1/students?documentId=${safe(u.email)}`, 'POST', adm(tok),
        { fields: { email: S(u.email), code: S('סוד אישי של ' + u.email) } });
    }
    console.log('הוקמו ' + SCHOOLS.length + ' בתי ספר ו-' + USERS.length + ' משתמשים\n');

    // ---------- הבדיקות ----------
    const T = [];
    const add = (name, fn, expect) => T.push({ name, fn, expect });
    const read = (email, p) => () => call(FS, `${DOCS}/${p}`, 'GET', asU(idTok[email]));
    const write = (email, p, fields) => () => call(FS, `${DOCS}/${p}`, 'PATCH', asU(idTok[email]), { fields });
    const list = (email, p) => () => call(FS, `${DOCS}/${p}?pageSize=50`, 'GET', asU(idTok[email]));

    for (const c of CONTENT) {
      add(`תלמידת אלפא קוראת ${c} של אלפא`, read('alpha-student@example.com', `schools/zz-alpha/${c}`), 200);
      add(`תלמידת אלפא קוראת ${c} של בטא`,  read('alpha-student@example.com', `schools/zz-beta/${c}`), 403);
      add(`תלמידת בטא קוראת ${c} של אלפא`,  read('beta-student@example.com', `schools/zz-alpha/${c}`), 403);
    }
    add('מורת אלפא קוראת נושא של אלפא', read('alpha-teacher@example.com', 'schools/zz-alpha/classes/c1'), 200);
    add('מורת אלפא קוראת נושא של בטא',  read('alpha-teacher@example.com', 'schools/zz-beta/classes/c1'), 403);
    add('מורת אלפא עורכת נושא של אלפא', write('alpha-teacher@example.com', 'schools/zz-alpha/classes/c1', { title: S('עודכן') }), 200);
    add('מורת אלפא עורכת נושא של בטא',  write('alpha-teacher@example.com', 'schools/zz-beta/classes/c1', { title: S('פריצה') }), 403);
    add('תלמידת אלפא עורכת נושא של אלפא', write('alpha-student@example.com', 'schools/zz-alpha/classes/c1', { title: S('פריצה') }), 403);
    add('מורת אלפא רואה רשימת תלמידות של אלפא', list('alpha-teacher@example.com', 'schools/zz-alpha/users'), 200);
    add('מורת אלפא רואה רשימת תלמידות של בטא',  list('alpha-teacher@example.com', 'schools/zz-beta/users'), 403);
    add('תלמידת אלפא רואה רשימת תלמידות של אלפא', list('alpha-student@example.com', 'schools/zz-alpha/users'), 403);
    add('תלמידת אלפא קוראת את התשובה של עצמה',
      read('alpha-student@example.com', `schools/zz-alpha/exam_responses/e1/students/${safe('alpha-student@example.com')}`), 200);
    add('תלמידת אלפא קוראת תשובה של תלמידה אחרת באלפא',
      read('alpha-student@example.com', `schools/zz-alpha/exam_responses/e1/students/${safe('alpha-student2@example.com')}`), 403);
    add('תלמידת אלפא קוראת תשובה של תלמידה בבטא',
      read('alpha-student@example.com', `schools/zz-beta/exam_responses/e1/students/${safe('beta-student@example.com')}`), 403);
    add('מורת אלפא קוראת תשובה של תלמידה שלה',
      read('alpha-teacher@example.com', `schools/zz-alpha/exam_responses/e1/students/${safe('alpha-student@example.com')}`), 200);
    add('מורת בטא קוראת תשובה של תלמידה באלפא',
      read('beta-teacher@example.com', `schools/zz-alpha/exam_responses/e1/students/${safe('alpha-student@example.com')}`), 403);
    add('תלמידה בבי"ס מושהה קוראת נושא', read('susp-student@example.com', 'schools/zz-susp/classes/c1'), 403);
    add('מורה של בי"ס מושהה עורכת נושא', write('susp-teacher@example.com', 'schools/zz-susp/classes/c1', { title: S('x') }), 403);
    add('תלמידת אלפא מוחקת נושא של בטא',
      () => call(FS, `${DOCS}/schools/zz-beta/classes/c1`, 'DELETE', asU(idTok['alpha-student@example.com'])), 403);
    add('מסמך בית ספר נקרא ללא התחברות (מסך הכניסה)',
      () => call(FS, `${DOCS}/schools/zz-alpha`, 'GET', {}), 200);
    add('תוכן בית ספר לא נקרא ללא התחברות',
      () => call(FS, `${DOCS}/schools/zz-alpha/classes/c1`, 'GET', {}), 403);

    const runAll = async (parallel) => {
      const results = parallel
        ? await Promise.all(T.map(t => t.fn()))
        : await (async () => { const o = []; for (const t of T) o.push(await t.fn()); return o; })();
      let pass = 0; const fails = [];
      results.forEach((r, i) => {
        if (r.code === T[i].expect) pass++; else fails.push(`${T[i].name} → ${r.code} (ציפינו ${T[i].expect})`);
      });
      return { pass, total: T.length, fails };
    };

    const seq = await runAll(false);
    console.log('טורי:   ' + seq.pass + '/' + seq.total);
    seq.fails.forEach(f => console.log('   כשל: ' + f));
    const par = await runAll(true);
    console.log('במקביל: ' + par.pass + '/' + par.total + '   (כל ' + T.length + ' הבקשות בבת אחת, 6 משתמשים מחוברים)');
    par.fails.forEach(f => console.log('   כשל: ' + f));

    const same = seq.pass === par.pass && seq.fails.length === par.fails.length;
    console.log('\nזהות בין טורי למקביל: ' + (same ? 'כן — אין דליפה תלוית מקביליות' : 'לא! יש הבדל'));
    console.log(seq.fails.length === 0 && par.fails.length === 0 ? '\n===== הכול עבר =====' : '\n===== יש כשלים =====');
  } finally {
    for (const sc of SCHOOLS) {
      for (const u of USERS.filter(x => x.school === sc.slug && x.role === 'student')) {
        await call(FS, `${DOCS}/schools/${sc.slug}/exam_responses/e1/students/${safe(u.email)}`, 'DELETE', adm(tok));
        await call(FS, `${DOCS}/schools/${sc.slug}/users/${safe(u.email)}`, 'DELETE', adm(tok));
      }
      await call(FS, `${DOCS}/schools/${sc.slug}/exam_responses/e1`, 'DELETE', adm(tok));
      for (const c of CONTENT) await call(FS, `${DOCS}/schools/${sc.slug}/${c}`, 'DELETE', adm(tok));
      await call(FS, `${DOCS}/schools/${sc.slug}`, 'DELETE', adm(tok));
    }
    for (const u of USERS) {
      const l = JSON.parse((await call('identitytoolkit.googleapis.com', `/v1/projects/${PROJECT}/accounts:lookup`, 'POST', adm(tok), { email: [u.email] })).body);
      if (l.users && l.users[0]) await call('identitytoolkit.googleapis.com', `/v1/projects/${PROJECT}/accounts:delete`, 'POST', adm(tok), { localId: l.users[0].localId });
    }
    if (!wasEnabled) await call('identitytoolkit.googleapis.com', `/admin/v2/projects/${PROJECT}/config?updateMask=signIn.email.enabled`,
      'PATCH', adm(tok), { signIn: { email: { enabled: false } } });
    console.log('ניקוי הושלם.');
  }
})();
