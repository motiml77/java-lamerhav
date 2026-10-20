/**
 * "מצב תלמידים": מורה מחוברת לבית ספר בדיקה, עורכת שם וכיתה, ומעבירה תלמידה שכבה.
 * נבדק מול Firestore החי: מה נכתב בפועל, שההיסטוריה נשמרה, ושהתשובות של התלמידה
 * לא זזו ולא נמחקו. כולל ביטול העלאת שכבה (undo) — המקרה של שירה טורג'מן.
 *
 * הרצה: node scripts/e2e-students-manager.js
 */
const { chromium } = require('playwright');
const https = require('https'), fs = require('fs'), os = require('os'), path = require('path');

const PROJECT = 'exams-a93fb';
const SLUG = 'zz-students-mgr';
const BASE = process.env.BASE || 'https://java-bagrut.vercel.app';
const APP = fs.readFileSync(path.join(__dirname, '..', 'app.html'), 'utf8');
const KEY = (APP.match(/apiKey:\s*"([^"]+)"/) || [])[1];
const FSH = 'firestore.googleapis.com';
const DOCS = '/v1/projects/' + PROJECT + '/databases/(default)/documents';
const safe = e => e.replace(/[.@]/g, '_');
const TEACHER = { email: 'mgr-teacher@example.com', pass: 'Mgr!2026tt' };
const SHOTS = process.env.SHOTS || path.join(__dirname, '..', '.shots');
fs.mkdirSync(SHOTS, { recursive: true });
const shot = n => path.join(SHOTS, n);

const YEAR = (() => { const d = new Date(); const y = d.getMonth() >= 7 ? d.getFullYear() : d.getFullYear() - 1; return y + '/' + String(y + 1).slice(2); })();
const STUDENTS = [
  { email: 'mgr-a@example.com', name: 'אלה בדיקה', grade: 'יא', classNum: '1' },
  { email: 'mgr-b@example.com', name: 'בתיה בדיקה', grade: 'יא', classNum: '2' },
  // הועלתה השנה מיא ליב — כמו שירה. החזרתה ליא צריכה להיות undo
  { email: 'mgr-c@example.com', name: 'גילה בדיקה', grade: 'יב', classNum: '2', promotedFrom: 'יא', promotionYear: YEAR },
];

let pass = 0, fail = 0;
const t = (n, c, d) => { c ? pass++ : fail++; console.log((c ? 'PASS ' : 'FAIL ') + n + (c ? '' : ' — ' + d)); };

function oauth() {
  return new Promise(res => {
    const cfg = JSON.parse(fs.readFileSync(path.join(os.homedir(), '.config', 'configstore', 'firebase-tools.json'), 'utf8'));
    const b = 'grant_type=refresh_token&refresh_token=' + encodeURIComponent(cfg.tokens.refresh_token) +
      '&client_id=563584335869-fgrhgmd47bqnekij5i8b5pr03ho849e6.apps.googleusercontent.com&client_secret=j9iVZfS8kkCEFUPaAeJV0sAi';
    const r = https.request({ hostname: 'oauth2.googleapis.com', path: '/token', method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' } }, s => {
      s.setEncoding('utf8'); let d = ''; s.on('data', c => d += c); s.on('end', () => res(JSON.parse(d).access_token));
    }); r.write(b); r.end();
  });
}
function call(host, p, method, headers, payload) {
  return new Promise(res => {
    const data = payload == null ? null : JSON.stringify(payload);
    const h = Object.assign({}, headers);
    if (data) h['Content-Length'] = Buffer.byteLength(data);
    const r = https.request({ hostname: host, path: p, method, headers: h }, s => {
      s.setEncoding('utf8'); let d = ''; s.on('data', c => d += c); s.on('end', () => res({ code: s.statusCode, body: d }));
    });
    r.on('error', e => res({ code: 0, body: String(e) }));
    if (data) r.write(data); r.end();
  });
}
const J = { 'Content-Type': 'application/json' };
const adm = t2 => ({ Authorization: 'Bearer ' + t2, 'Content-Type': 'application/json' });
const S = v => ({ stringValue: v });
const field = (doc, k) => { const f = (doc.fields || {})[k]; return f ? (f.stringValue ?? f.booleanValue ?? f) : undefined; };

(async () => {
  const tok = await oauth();
  const cfg = JSON.parse((await call('identitytoolkit.googleapis.com', '/admin/v2/projects/' + PROJECT + '/config', 'GET', adm(tok))).body);
  const wasEnabled = !!(cfg.signIn && cfg.signIn.email && cfg.signIn.email.enabled);
  if (!wasEnabled) await call('identitytoolkit.googleapis.com',
    '/admin/v2/projects/' + PROJECT + '/config?updateMask=signIn.email.enabled,signIn.email.passwordRequired',
    'PATCH', adm(tok), { signIn: { email: { enabled: true, passwordRequired: true } } });

  let b;
  try {
    // ---------- הקמה ----------
    let r = await call('identitytoolkit.googleapis.com', '/v1/accounts:signUp?key=' + KEY, 'POST', J,
      { email: TEACHER.email, password: TEACHER.pass, returnSecureToken: true });
    if (r.code !== 200) r = await call('identitytoolkit.googleapis.com', '/v1/accounts:signInWithPassword?key=' + KEY, 'POST', J,
      { email: TEACHER.email, password: TEACHER.pass, returnSecureToken: true });
    await call('identitytoolkit.googleapis.com', '/v1/projects/' + PROJECT + '/accounts:update', 'POST', adm(tok),
      { localId: JSON.parse(r.body).localId, emailVerified: true });

    await call(FSH, DOCS + '/schools?documentId=' + SLUG, 'POST', adm(tok), { fields: {
      slug: S(SLUG), name: S('בדיקת מצב תלמידים'), teacherEmail: S(TEACHER.email), status: S('active'),
      settings: { mapValue: { fields: { grades: { arrayValue: { values: [S('י'), S('יא'), S('יב')] } } } } } } });
    for (const st of STUDENTS) {
      const f = { email: S(st.email), name: S(st.name), grade: S(st.grade), classNum: S(st.classNum), approved: { booleanValue: true } };
      if (st.promotedFrom) {
        f.promotedFrom = S(st.promotedFrom); f.promotionYear = S(st.promotionYear); f.promotedAt = S(new Date().toISOString());
        f.gradeHistory = { arrayValue: { values: [{ mapValue: { fields: { from: S('יא'), to: S('יב'), year: S(YEAR), at: S(new Date().toISOString()), classNum: S('2') } } }] } };
      }
      await call(FSH, DOCS + '/schools/' + SLUG + '/users?documentId=' + safe(st.email), 'POST', adm(tok), { fields: f });
    }
    // תשובה שמורה של תלמידה ב' — לוודא שמעבר שכבה לא נוגע בה
    await call(FSH, DOCS + '/schools/' + SLUG + '/exam_responses/zz-ex1/students?documentId=' + safe('mgr-b@example.com'), 'POST', adm(tok),
      { fields: { email: S('mgr-b@example.com'), code: S('int x = 5;'), grade: { integerValue: 87 } } });

    // ---------- הדפדפן ----------
    try { b = await chromium.launch({ channel: 'chrome' }); } catch (e) { b = await chromium.launch({ channel: 'msedge' }); }
    const pg = await b.newPage({ viewport: { width: 1300, height: 950 } });
    const errs = []; pg.on('pageerror', e => errs.push(String(e).slice(0, 160)));
    pg.on('dialog', d => d.accept()); // אישור ה-confirm של מעבר שכבה

    await pg.goto(BASE + '/' + SLUG, { waitUntil: 'domcontentloaded', timeout: 60000 });
    await pg.waitForSelector('text=התחברות עם Google', { timeout: 45000 });
    await pg.evaluate(async ([e, p]) => { await firebase.auth().signInWithEmailAndPassword(e, p); }, [TEACHER.email, TEACHER.pass]);
    await pg.waitForTimeout(10000);

    const btn = await pg.$('button:has-text("מצב תלמידים")');
    t('כפתור "מצב תלמידים" מוצג למורה', !!btn);
    if (!btn) throw new Error('אין כפתור');
    await btn.click({ force: true });
    await pg.waitForSelector('text=בתיה בדיקה', { timeout: 20000 });
    await pg.screenshot({ path: shot('mgr-1-all.png') });

    let body = await pg.textContent('body');
    t('כל שלוש התלמידות ברשימה', /אלה בדיקה/.test(body) && /בתיה בדיקה/.test(body) && /גילה בדיקה/.test(body));

    // סינון שכבה
    await pg.click('[data-grade-tab="יב"]', { force: true });
    await pg.waitForTimeout(500);
    body = await pg.evaluate(() => document.querySelector('.modal-content, [role=dialog]') ? document.querySelector('.modal-content, [role=dialog]').innerText : document.body.innerText);
    const onlyC = /גילה בדיקה/.test(body) && !/אלה בדיקה/.test(body) && !/בתיה בדיקה/.test(body);
    t('לשונית שכבה יב מציגה רק את תלמידות יב', onlyC, body.slice(0, 160));

    // ---- עריכה: תלמידה ב' — שם, כיתה, ומעבר יא→יב ----
    await pg.click('[data-grade-tab="הכל"]', { force: true });
    await pg.waitForTimeout(400);
    const rowEdit = async (email) => {
      const bt = await pg.$('[data-student="' + email + '"] button:has-text("עריכה")');
      if (!bt) return false;
      await bt.click({ force: true }); return true;
    };
    const row = (email) => '[data-student="' + email + '"]';
    t('נפתחה עריכה לתלמידה', await rowEdit('mgr-b@example.com'));
    await pg.waitForTimeout(400);
    const inputs = await pg.$$(row('mgr-b@example.com') + ' input');
    await inputs[0].fill('בתיה כהן');   // שם
    await inputs[1].fill('4');          // מספר כיתה
    await pg.selectOption(row('mgr-b@example.com') + ' select', 'יב');
    await pg.waitForTimeout(300);
    t('מוצגת הודעה שהנתונים נשארים איתה', /נשארים איתה/.test(await pg.textContent('body')));
    await pg.screenshot({ path: shot('mgr-2-edit.png') });
    await pg.click('button:has-text("שמירה")', { force: true });
    await pg.waitForTimeout(3000);
    t('הודעת הצלחה מוצגת', /הועברה לשכבה יב/.test(await pg.textContent('body')), (await pg.textContent('body')).slice(0, 120));

    const docB = JSON.parse((await call(FSH, DOCS + '/schools/' + SLUG + '/users/' + safe('mgr-b@example.com'), 'GET', adm(tok))).body);
    t('במסד: שם עודכן', field(docB, 'name') === 'בתיה כהן', field(docB, 'name'));
    t('במסד: מספר כיתה עודכן', field(docB, 'classNum') === '4', field(docB, 'classNum'));
    t('במסד: שכבה יב', field(docB, 'grade') === 'יב', field(docB, 'grade'));
    const histB = ((docB.fields.gradeHistory || {}).arrayValue || {}).values || [];
    const lastB = histB.length ? histB[histB.length - 1].mapValue.fields : {};
    t('במסד: נרשם בהיסטוריה (מיא ליב, ידני)', lastB.from && lastB.from.stringValue === 'יא' && lastB.to.stringValue === 'יב' && lastB.manual && lastB.manual.booleanValue === true, JSON.stringify(lastB).slice(0, 160));
    t('במסד: האישור לא נפגע', field(docB, 'approved') === true);
    const resp = await call(FSH, DOCS + '/schools/' + SLUG + '/exam_responses/zz-ex1/students/' + safe('mgr-b@example.com'), 'GET', adm(tok));
    t('התשובה השמורה שלה לא זזה ולא נמחקה', resp.code === 200 && /87/.test(resp.body), resp.code);

    // ---- undo: תלמידה ג' (הועלתה השנה מיא) חוזרת ליא ----
    t('נפתחה עריכה לתלמידה שהועלתה', await rowEdit('mgr-c@example.com'));
    await pg.waitForTimeout(400);
    await pg.selectOption(row('mgr-c@example.com') + ' select', 'יא');
    await pg.click('button:has-text("שמירה")', { force: true });
    await pg.waitForTimeout(3000);
    t('הודעה: בוטלה העלאת השכבה', /בוטלה העלאת השכבה/.test(await pg.textContent('body')));
    const docC = JSON.parse((await call(FSH, DOCS + '/schools/' + SLUG + '/users/' + safe('mgr-c@example.com'), 'GET', adm(tok))).body);
    const histC = ((docC.fields.gradeHistory || {}).arrayValue || {}).values || [];
    const lastC = histC.length ? histC[histC.length - 1].mapValue.fields : {};
    t('undo: שכבה יא', field(docC, 'grade') === 'יא');
    t('undo: סימוני ההעלאה נמחקו', !docC.fields.promotionYear && !docC.fields.promotedFrom && !docC.fields.promotedAt, Object.keys(docC.fields).join(','));
    t('undo: ההיסטוריה נשמרה + רשומת undo', histC.length === 2 && lastC.undo && lastC.undo.booleanValue === true, histC.length + ' ' + JSON.stringify(lastC).slice(0, 120));
    await pg.screenshot({ path: shot('mgr-3-after.png') });

    // תלמידה א' לא נגעו בה
    const docA = JSON.parse((await call(FSH, DOCS + '/schools/' + SLUG + '/users/' + safe('mgr-a@example.com'), 'GET', adm(tok))).body);
    t('תלמידה שלא נערכה — ללא שינוי', field(docA, 'grade') === 'יא' && field(docA, 'name') === 'אלה בדיקה' && !docA.fields.gradeHistory);

    t('אין שגיאות JavaScript', errs.filter(e => !/BABEL|deoptimised/i.test(e)).length === 0, JSON.stringify(errs.slice(0, 2)));
    console.log('\n===== ' + pass + '/' + (pass + fail) + ' =====');
  } catch (e) {
    fail++; console.log('שגיאה: ' + String(e && e.message).slice(0, 250));
  } finally {
    if (b) await b.close();
    for (const st of STUDENTS) await call(FSH, DOCS + '/schools/' + SLUG + '/users/' + safe(st.email), 'DELETE', adm(tok));
    await call(FSH, DOCS + '/schools/' + SLUG + '/exam_responses/zz-ex1/students/' + safe('mgr-b@example.com'), 'DELETE', adm(tok));
    await call(FSH, DOCS + '/schools/' + SLUG, 'DELETE', adm(tok));
    const l = JSON.parse((await call('identitytoolkit.googleapis.com', '/v1/projects/' + PROJECT + '/accounts:lookup', 'POST', adm(tok), { email: [TEACHER.email] })).body);
    if (l.users && l.users[0]) await call('identitytoolkit.googleapis.com', '/v1/projects/' + PROJECT + '/accounts:delete', 'POST', adm(tok), { localId: l.users[0].localId });
    if (!wasEnabled) await call('identitytoolkit.googleapis.com',
      '/admin/v2/projects/' + PROJECT + '/config?updateMask=signIn.email.enabled', 'PATCH', adm(tok), { signIn: { email: { enabled: false } } });
    console.log('נוקה.');
    process.exit(fail > 0 ? 1 : 0);
  }
})();
