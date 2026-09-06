/**
 * בית ספר חדש: האם תלמידה שנרשמת אליו מקבלת נושאים?
 *
 * הבדיקות הקודמות נעצרו בשלב הבקשה או יצרו תלמידה מאושרת מראש. כאן נבדק
 * המסלול שהמורה החדש יחווה בפועל: בית ספר שנוצר בזרימת האישור, תלמידה
 * שנרשמת דרך הטופס באתר, מסך ההמתנה, אישור המורה, ואז הנושאים.
 *
 * הרצה: node scripts/e2e-new-school-student.js
 */
const { chromium } = require('playwright');
const https = require('https'), fs = require('fs'), os = require('os'), path = require('path');

const PROJECT = 'exams-a93fb';
const SLUG = 'zz-newschool';
const BASE = process.env.BASE || 'https://java-bagrut.vercel.app';
const APP = fs.readFileSync(path.join(__dirname, '..', 'app.html'), 'utf8');
const KEY = (APP.match(/apiKey:\s*"([^"]+)"/) || [])[1];
const FSH = 'firestore.googleapis.com';
const DOCS = '/v1/projects/' + PROJECT + '/databases/(default)/documents';
const safe = e => e.replace(/[.@]/g, '_');
const TEACHER_EMAIL = 'newschool-teacher@example.com';
const STUDENT = { email: 'newschool-student@example.com', pass: 'N3wSch!2026' };
const SHOTS = process.env.SHOTS || path.join(__dirname, '..', '.shots');
fs.mkdirSync(SHOTS, { recursive: true });
const shot = n => path.join(SHOTS, n);

// אותם נושאים שהאישור זורע — נלקחים מ-LEARN_LIBRARY שבאפליקציה, כדי
// שהבדיקה תשקף את מה שבית ספר חדש באמת מקבל ולא רשימה שהמצאתי.
function seededTopics() {
  // חיתוך לפי איזון סוגריים ולא ביטוי רגולרי: הנושאים מכילים טקסטים ארוכים
  // עם סוגריים משלהם, ורגקס קצר החזיר רשימה ריקה — והבדיקה "עברה" על אפס
  // נושאים בלי לבדוק כלום.
  const i = APP.indexOf('const LEARN_LIBRARY = [');
  if (i === -1) return [];
  let depth = 0, j = APP.indexOf('[', i), end = j;
  for (let k = j; k < APP.length; k++) {
    if (APP[k] === '[') depth++;
    else if (APP[k] === ']') { depth--; if (depth === 0) { end = k; break; } }
  }
  const titles = [];
  for (const m of APP.slice(j, end).matchAll(/^\s{16}title: '([^']+)'/gm)) titles.push(m[1]);
  return titles;
}

let pass = 0, fail = 0;
const t = (n, c, d) => { c ? pass++ : fail++; console.log((c ? 'PASS ' : 'FAIL ') + n + (c ? '' : ' — ' + d)); };

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
const adm = t2 => ({ Authorization: 'Bearer ' + t2, 'Content-Type': 'application/json' });
const S = v => ({ stringValue: v });

const topicsOnScreen = (pg, titles) => pg.evaluate((ts) => {
  const txt = document.body.innerText;
  return ts.filter(x => txt.includes(x.slice(0, 8)));
}, titles);

(async () => {
  const tok = await oauth();
  const cfg = JSON.parse((await call('identitytoolkit.googleapis.com', '/admin/v2/projects/' + PROJECT + '/config', 'GET', adm(tok))).body);
  const wasEnabled = !!(cfg.signIn && cfg.signIn.email && cfg.signIn.email.enabled);
  if (!wasEnabled) await call('identitytoolkit.googleapis.com',
    '/admin/v2/projects/' + PROJECT + '/config?updateMask=signIn.email.enabled,signIn.email.passwordRequired',
    'PATCH', adm(tok), { signIn: { email: { enabled: true, passwordRequired: true } } });

  const titles = seededTopics();
  console.log('נושאים שספריית הלימוד זורעת: ' + titles.length + (titles.length ? ' (' + titles.slice(0, 3).join(', ') + '...)' : ''));

  let b;
  try {
    // ---------- בית ספר חדש, בדיוק כמו באישור בקשה ----------
    await call(FSH, DOCS + '/schools/' + SLUG, 'DELETE', adm(tok));
    for (let i = 0; i < titles.length; i++) {
      // השכבה נקבעת ב-topicGradeFor: הנושאים הראשונים יא, המתקדמים יב
      const grade = i < 6 ? 'יא' : 'יב';
      await call(FSH, DOCS + '/schools/' + SLUG + '/classes?documentId=class_' + SLUG + '_' + i, 'POST', adm(tok), { fields: {
        id: S('class_' + SLUG + '_' + i), title: S(titles[i]), grade: S(grade),
        order: { integerValue: i }, archived: { booleanValue: false },
        exams: { arrayValue: { values: [] } } } });
    }
    await call(FSH, DOCS + '/schools?documentId=' + SLUG, 'POST', adm(tok), { fields: {
      slug: S(SLUG), name: S('תיכון חדש לבדיקה'), teacherEmail: S(TEACHER_EMAIL), status: S('active'),
      settings: { mapValue: { fields: { grades: { arrayValue: { values: [S('י'), S('יא'), S('יב')] } },
        subtitle: S('תיכון חדש לבדיקה') } } } } });
    const cls = JSON.parse((await call(FSH, DOCS + '/schools/' + SLUG + '/classes?pageSize=100', 'GET', adm(tok))).body);
    t('בית הספר החדש נוצר עם נושאים', (cls.documents || []).length === titles.length,
      (cls.documents || []).length + ' מתוך ' + titles.length);

    // ---------- תלמידה חדשה נרשמת ----------
    let r = await call('identitytoolkit.googleapis.com', '/v1/accounts:signUp?key=' + KEY, 'POST', J,
      { email: STUDENT.email, password: STUDENT.pass, returnSecureToken: true });
    if (r.code !== 200) r = await call('identitytoolkit.googleapis.com', '/v1/accounts:signInWithPassword?key=' + KEY, 'POST', J,
      { email: STUDENT.email, password: STUDENT.pass, returnSecureToken: true });
    await call('identitytoolkit.googleapis.com', '/v1/projects/' + PROJECT + '/accounts:update', 'POST', adm(tok),
      { localId: JSON.parse(r.body).localId, emailVerified: true });

    try { b = await chromium.launch({ channel: 'chrome' }); } catch (e) { b = await chromium.launch({ channel: 'msedge' }); }
    const pg = await b.newPage({ viewport: { width: 430, height: 950 } });
    const errs = []; pg.on('pageerror', e => errs.push(String(e).slice(0, 150)));

    await pg.goto(BASE + '/' + SLUG, { waitUntil: 'domcontentloaded', timeout: 60000 });
    await pg.waitForSelector('text=התחברות עם Google', { timeout: 45000 });
    t('שם בית הספר בכותרת', /תיכון חדש לבדיקה/.test(await pg.textContent('body')));

    await pg.evaluate(async ([e, p]) => { await firebase.auth().signInWithEmailAndPassword(e, p); },
      [STUDENT.email, STUDENT.pass]);
    await pg.waitForTimeout(10000);
    await pg.screenshot({ path: shot('ns-1-register.png') });

    // טופס ההרשמה: שם + שכבה
    const inputs = await pg.$$('input');
    if (inputs.length) await inputs[0].fill('תלמידה חדשה');
    const gradeBtn = await pg.$('button:has-text("כיתה יא")');
    t('בורר השכבה מוצג בהרשמה', !!gradeBtn);
    if (gradeBtn) await gradeBtn.click({ force: true }).catch(() => {});
    await pg.waitForTimeout(700);
    const submit = await pg.$('button:has-text("שליחה לאישור המורה")');
    t('כפתור שליחת ההרשמה קיים', !!submit);
    if (submit) { await submit.click({ force: true }); await pg.waitForTimeout(8000); }
    await pg.screenshot({ path: shot('ns-2-pending.png') });

    let doc = await call(FSH, DOCS + '/schools/' + SLUG + '/users/' + safe(STUDENT.email), 'GET', adm(tok));
    const f = doc.code === 200 ? (JSON.parse(doc.body).fields || {}) : {};
    t('מסמך התלמידה נוצר בהרשמה', doc.code === 200, doc.body.slice(0, 130));
    t('נרשמה כלא-מאושרת', f.approved && f.approved.booleanValue === false,
      JSON.stringify(f.approved || 'חסר'));

    const pendingTxt = await pg.textContent('body');
    t('מוצג מסך המתנה לאישור', /ממתינ|אישור המורה|בהמתנה/.test(pendingTxt),
      pendingTxt.replace(/\s+/g, ' ').slice(0, 150));
    const beforeApproval = await topicsOnScreen(pg, titles);
    t('לפני אישור אינה רואה נושאים', beforeApproval.length === 0, beforeApproval.join(', '));

    // ---------- המורה מאשרת ----------
    await call(FSH, DOCS + '/schools/' + SLUG + '/users/' + safe(STUDENT.email) + '?updateMask.fieldPaths=approved',
      'PATCH', adm(tok), { fields: { approved: { booleanValue: true } } });

    await pg.reload({ waitUntil: 'domcontentloaded' });
    await pg.waitForTimeout(12000);
    await pg.screenshot({ path: shot('ns-3-topics.png') });
    const seen = await topicsOnScreen(pg, titles);
    t('אחרי אישור רואה נושאים', seen.length > 0,
      (await pg.textContent('body')).replace(/\s+/g, ' ').slice(0, 160));
    console.log('   נושאים גלויים לתלמידה: ' + seen.length + ' מתוך ' + titles.length +
      (seen.length ? ' — ' + seen.slice(0, 4).join(', ') : ''));

    t('אין שגיאות JavaScript', errs.filter(e => !/BABEL|deoptimised/i.test(e)).length === 0,
      JSON.stringify(errs.slice(0, 2)));

    console.log('\n===== ' + pass + '/' + (pass + fail) + ' =====');
  } catch (e) {
    fail++; console.log('שגיאה: ' + String(e && e.message).slice(0, 250));
  } finally {
    if (b) await b.close();
    const cl = await call(FSH, DOCS + '/schools/' + SLUG + '/classes?pageSize=100', 'GET', adm(tok));
    for (const d of (cl.code === 200 ? (JSON.parse(cl.body).documents || []) : [])) {
      await call(FSH, '/v1/' + d.name, 'DELETE', adm(tok));
    }
    await call(FSH, DOCS + '/schools/' + SLUG + '/users/' + safe(STUDENT.email), 'DELETE', adm(tok));
    await call(FSH, DOCS + '/schools/' + SLUG, 'DELETE', adm(tok));
    const l = JSON.parse((await call('identitytoolkit.googleapis.com', '/v1/projects/' + PROJECT + '/accounts:lookup', 'POST', adm(tok), { email: [STUDENT.email] })).body);
    if (l.users && l.users[0]) await call('identitytoolkit.googleapis.com', '/v1/projects/' + PROJECT + '/accounts:delete', 'POST', adm(tok), { localId: l.users[0].localId });
    if (!wasEnabled) await call('identitytoolkit.googleapis.com',
      '/admin/v2/projects/' + PROJECT + '/config?updateMask=signIn.email.enabled', 'PATCH', adm(tok), { signIn: { email: { enabled: false } } });
    console.log('נוקה.');
    process.exit(fail > 0 ? 1 : 0);
  }
})();
