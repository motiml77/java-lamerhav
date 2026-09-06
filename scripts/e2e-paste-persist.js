/**
 * מסלול מלא של הדבקת צילום מסך: הדבקה → עריכה → שמירה → אחסון → הצגה.
 *
 * למה זה קיים בנפרד מ-e2e-paste-image.js: הבדיקה ההיא רצה במצב דמו ומאמתת
 * רק שעורך התמונה נפתח. שום דבר לא נשמר שם. כאן נבדק מה שבאמת חשוב למורה —
 * שהתמונה שורדת: מגיעה ל-Storage, הכתובת נשמרת ב-Firestore, הקובץ באמת
 * ניתן להורדה, והתמונה מוצגת בשאלה גם אחרי רענון הדף.
 *
 * מקים בית ספר בדיקה עם מורה אמיתי מחובר, ומנקה הכול בסוף.
 *
 * הרצה: node scripts/e2e-paste-persist.js
 */
const { chromium } = require('playwright');
const https = require('https'), fs = require('fs'), os = require('os'), path = require('path');

const PROJECT = 'exams-a93fb';
const BUCKET = 'exams-a93fb.firebasestorage.app';
const SLUG = 'zz-paste-live';
const TEACHER = { email: 'paste-teacher@example.com', pass: 'P4ste!2026tt' };
const APP = fs.readFileSync(path.join(__dirname, '..', 'app.html'), 'utf8');
const KEY = (APP.match(/apiKey:\s*"([^"]+)"/) || [])[1];
const FSH = 'firestore.googleapis.com';
const DOCS = '/v1/projects/' + PROJECT + '/databases/(default)/documents';
const BASE = process.env.PASTE_BASE || 'https://java-bagrut.vercel.app';
// תיקיית הצילומים — ניתנת להפניה החוצה כדי לא ללכלך את הריפו
const SHOTS = process.env.SHOTS || path.join(__dirname, '..', '.shots');
fs.mkdirSync(SHOTS, { recursive: true });
const shot = (n) => path.join(SHOTS, n);

const CLASS_ID = 'c_' + SLUG;
const EXAM_ID = 'e_' + SLUG;
const Q_ID = EXAM_ID + '_q_1';   // number של השאלה, לא אינדקס

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

(async () => {
  const tok = await oauth();
  const cfg = JSON.parse((await call('identitytoolkit.googleapis.com', '/admin/v2/projects/' + PROJECT + '/config', 'GET', adm(tok))).body);
  const wasEnabled = !!(cfg.signIn && cfg.signIn.email && cfg.signIn.email.enabled);
  if (!wasEnabled) await call('identitytoolkit.googleapis.com',
    '/admin/v2/projects/' + PROJECT + '/config?updateMask=signIn.email.enabled,signIn.email.passwordRequired',
    'PATCH', adm(tok), { signIn: { email: { enabled: true, passwordRequired: true } } });

  let b, savedPath = '';
  try {
    // ---------- הקמה ----------
    let r = await call('identitytoolkit.googleapis.com', '/v1/accounts:signUp?key=' + KEY, 'POST', J,
      { email: TEACHER.email, password: TEACHER.pass, returnSecureToken: true });
    if (r.code !== 200) r = await call('identitytoolkit.googleapis.com', '/v1/accounts:signInWithPassword?key=' + KEY, 'POST', J,
      { email: TEACHER.email, password: TEACHER.pass, returnSecureToken: true });
    await call('identitytoolkit.googleapis.com', '/v1/projects/' + PROJECT + '/accounts:update', 'POST', adm(tok),
      { localId: JSON.parse(r.body).localId, emailVerified: true });

    await call(FSH, DOCS + '/schools?documentId=' + SLUG, 'POST', adm(tok), { fields: {
      slug: S(SLUG), name: S('בדיקת שמירת תמונה'), teacherEmail: S(TEACHER.email), status: S('active') } });

    // נושא עם מבחן ושאלה אחת
    await call(FSH, DOCS + '/schools/' + SLUG + '/classes?documentId=' + CLASS_ID, 'POST', adm(tok), { fields: {
      id: S(CLASS_ID), title: S('לולאות'), topicKey: S('loops'), grade: S('יא'),
      order: { integerValue: 0 }, archived: { booleanValue: false },
      exams: { arrayValue: { values: [ { mapValue: { fields: {
        id: S(EXAM_ID), title: S('עבודת בדיקה'), type: S('homework'),
        questions: { arrayValue: { values: [ { mapValue: { fields: {
          number: { integerValue: 1 }, title: S('שאלת בדיקה'), points: { integerValue: 100 } } } } ] } } } } } ] } } } });

    console.log('הוקם בית ספר ' + SLUG + ' עם מורה ' + TEACHER.email + '\n');

    // ---------- הדפדפן ----------
    try { b = await chromium.launch({ channel: 'chrome' }); } catch (e) { b = await chromium.launch({ channel: 'msedge' }); }
    const pg = await b.newPage({ viewport: { width: 1300, height: 950 } });
    const errs = []; pg.on('pageerror', e => errs.push(String(e).slice(0, 160)));

    await pg.goto(BASE + '/' + SLUG, { waitUntil: 'domcontentloaded', timeout: 60000 });
    await pg.waitForSelector('text=התחברות עם Google', { timeout: 45000 });
    t('בית הספר החדש נטען', true);
    t('אין כניסת דמו בבית ספר אמיתי', !/כניסת דמו/.test(await pg.textContent('body')));

    await pg.evaluate(async ([e, p]) => { await firebase.auth().signInWithEmailAndPassword(e, p); },
      [TEACHER.email, TEACHER.pass]);
    await pg.waitForTimeout(9000);
    await pg.screenshot({ path: shot('persist-1-in.png') });
    t('המורה נכנס לבית הספר', !/התחברות עם Google/.test(await pg.textContent('body')),
      (await pg.textContent('body')).replace(/\s+/g, ' ').slice(0, 140));

    // מצב עריכה
    const editBtn = await pg.$('button:has-text("עריכה")');
    if (editBtn) { await editBtn.click({ force: true }); await pg.waitForTimeout(2500); }

    const clickByText = (txt) => pg.evaluate((s2) => {
      const el = [...document.querySelectorAll('span,div,button,a,h3,h4')]
        .find(e => e.textContent.trim() === s2 && e.children.length === 0);
      if (!el) return false;
      let n = el;
      for (let i = 0; i < 6 && n; i++) {
        if (n.tagName === 'BUTTON' || n.tagName === 'A' || n.getAttribute('role') === 'button') { n.click(); return true; }
        n = n.parentElement;
      }
      el.parentElement.click(); return true;
    }, txt);

    await clickByText('לולאות'); await pg.waitForTimeout(2500);
    await clickByText('עבודת בדיקה'); await pg.waitForTimeout(3500);
    await clickByText('שאלה 1'); await pg.waitForTimeout(3000);
    await pg.screenshot({ path: shot('persist-2-question.png') });
    t('עורך השאלה נפתח', /העלה תמונה|Ctrl\+V/.test(await pg.textContent('body')),
      (await pg.textContent('body')).replace(/\s+/g, ' ').slice(0, 140));

    // ---------- הדבקה ----------
    const meta = await pg.evaluate(async () => {
      const c = document.createElement('canvas');
      c.width = 360; c.height = 240;
      const x = c.getContext('2d');
      x.fillStyle = '#1e3a8a'; x.fillRect(0, 0, 360, 240);
      x.fillStyle = '#facc15'; x.fillRect(40, 40, 150, 110);
      x.fillStyle = '#fff'; x.font = 'bold 22px sans-serif'; x.fillText('for(i=0;i<n;i++)', 30, 200);
      const blob = await new Promise(r2 => c.toBlob(r2, 'image/png'));
      const dt = new DataTransfer();
      dt.items.add(new File([blob], 'shot.png', { type: 'image/png' }));
      const ev = new ClipboardEvent('paste', { clipboardData: dt, bubbles: true, cancelable: true });
      document.dispatchEvent(ev);
      return { size: blob.size, prevented: ev.defaultPrevented };
    });
    t('ההדבקה נקלטה', meta.prevented === true);
    await pg.waitForTimeout(3000);
    t('עורך התמונה נפתח', /שחור.לבן|סיבוב/.test(await pg.textContent('body')));

    // שחור-לבן ואז שמירה — בדיוק מה שהמורה עושה
    const bw = await pg.$('button:has-text("שחור-לבן")');
    if (bw) { await bw.click({ force: true }); await pg.waitForTimeout(1000); }
    await pg.screenshot({ path: shot('persist-3-editor.png') });

    const save = await pg.$('button:has-text("שמירת התמונה")');
    t('כפתור השמירה קיים', !!save);
    if (save) await save.click({ force: true });

    // ההעלאה אמיתית — נותנים לה זמן
    await pg.waitForFunction(() => !/שמירת התמונה/.test(document.body.innerText), { timeout: 60000 }).catch(() => {});
    await pg.waitForTimeout(4000);
    await pg.screenshot({ path: shot('persist-4-saved.png') });

    // ---------- מה באמת נשמר ----------
    const q = await call(FSH, DOCS + '/schools/' + SLUG + '/questions/' + Q_ID, 'GET', adm(tok));
    const f = q.code === 200 ? JSON.parse(q.body).fields || {} : {};
    const url = f.imageUrl && f.imageUrl.stringValue;
    savedPath = (f.imagePath && f.imagePath.stringValue) || '';
    t('נשמר מסמך שאלה ב-Firestore', q.code === 200, q.body.slice(0, 120));
    t('נשמרה כתובת תמונה', !!url, JSON.stringify(Object.keys(f)));
    t('נשמר נתיב האחסון', !!savedPath);
    t('לא נשמר גם base64 (בלי כפילות)', !(f.imageBase64 && f.imageBase64.stringValue));

    // הקובץ עצמו קיים וניתן להורדה
    if (url) {
      const got = await new Promise(res => {
        https.get(url, s2 => {
          const chunks = [];
          s2.on('data', c => chunks.push(c));
          s2.on('end', () => res({ code: s2.statusCode, type: s2.headers['content-type'], len: Buffer.concat(chunks).length }));
        }).on('error', () => res({ code: 0 }));
      });
      t('הקובץ ב-Storage ניתן להורדה', got.code === 200, 'HTTP ' + got.code);
      t('הקובץ הוא תמונה אמיתית', /image\//.test(got.type || '') && got.len > 1000,
        got.type + ' · ' + got.len + ' bytes');
      console.log('   נשמר: ' + got.len + ' bytes · ' + got.type);
    }

    // ---------- ההצגה, אחרי רענון מלא ----------
    await pg.reload({ waitUntil: 'domcontentloaded' });
    await pg.waitForTimeout(9000);
    const editBtn2 = await pg.$('button:has-text("עריכה")');
    if (editBtn2) { await editBtn2.click({ force: true }); await pg.waitForTimeout(2000); }
    await clickByText('לולאות'); await pg.waitForTimeout(2000);
    await clickByText('עבודת בדיקה'); await pg.waitForTimeout(3000);
    await clickByText('שאלה 1'); await pg.waitForTimeout(3500);
    await pg.screenshot({ path: shot('persist-5-after-reload.png') });

    const shown = await pg.evaluate((u) => {
      const imgs = [...document.querySelectorAll('img')];
      const m = imgs.find(i => i.src === u);
      return m ? { found: true, w: m.naturalWidth, h: m.naturalHeight } : { found: false, srcs: imgs.map(i => i.src.slice(0, 60)) };
    }, url);
    t('התמונה מוצגת בשאלה אחרי רענון', shown.found, JSON.stringify(shown.srcs || []).slice(0, 160));
    t('התמונה נטענה בפועל (לא שבורה)', shown.found && shown.w > 0 && shown.h > 0,
      shown.found ? shown.w + 'x' + shown.h : '');
    if (shown.found) console.log('   מוצגת בגודל ' + shown.w + 'x' + shown.h);

    console.log('\n===== ' + pass + '/' + (pass + fail) + ' =====');
    console.log('שגיאות JS:', errs.filter(e => !/BABEL|deoptimised/i.test(e)).slice(0, 3));
  } catch (e) {
    fail++; console.log('שגיאה: ' + String(e && e.message).slice(0, 250));
  } finally {
    if (b) await b.close();
    // ניקוי: קובץ האחסון, המסמכים, בית הספר והמשתמש
    if (savedPath) {
      await call('firebasestorage.googleapis.com',
        '/v0/b/' + BUCKET + '/o/' + encodeURIComponent(savedPath), 'DELETE', { Authorization: 'Bearer ' + tok });
    }
    const leftovers = await call(FSH, DOCS + '/schools/' + SLUG + '/questions?pageSize=50', 'GET', adm(tok));
    for (const d of (leftovers.code === 200 ? (JSON.parse(leftovers.body).documents || []) : [])) {
      const pth = ((d.fields || {}).imagePath || {}).stringValue;
      if (pth && pth !== savedPath) await call('firebasestorage.googleapis.com',
        '/v0/b/' + BUCKET + '/o/' + encodeURIComponent(pth), 'DELETE', { Authorization: 'Bearer ' + tok });
      await call(FSH, '/v1/' + d.name, 'DELETE', adm(tok));
    }
    for (const p2 of ['schools/' + SLUG + '/classes/' + CLASS_ID, 'schools/' + SLUG]) {
      await call(FSH, DOCS + '/' + p2, 'DELETE', adm(tok));
    }
    const l = JSON.parse((await call('identitytoolkit.googleapis.com', '/v1/projects/' + PROJECT + '/accounts:lookup', 'POST', adm(tok), { email: [TEACHER.email] })).body);
    if (l.users && l.users[0]) await call('identitytoolkit.googleapis.com', '/v1/projects/' + PROJECT + '/accounts:delete', 'POST', adm(tok), { localId: l.users[0].localId });
    if (!wasEnabled) await call('identitytoolkit.googleapis.com',
      '/admin/v2/projects/' + PROJECT + '/config?updateMask=signIn.email.enabled', 'PATCH', adm(tok), { signIn: { email: { enabled: false } } });
    console.log('נוקה.');
    process.exit(fail > 0 ? 1 : 0);
  }
})();
