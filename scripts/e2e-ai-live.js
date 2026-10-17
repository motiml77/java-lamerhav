/**
 * בדיקת ה-AI דרך בית הספר 'למרחב' באתר החי: תלמידה מחוברת, קריאות מתוך הדף.
 * שלב 1: קריאות ישירות מהדף (מוכיח CORS + מודל + פורמט תשובה).
 * שלב 2: דרך הממשק האמיתי — פותחת משימה ולוחצת על בדיקת AI.
 */
const { chromium } = require('playwright');
const https = require('https'), fs = require('fs'), os = require('os'), path = require('path');

const PROJECT = 'exams-a93fb', SCHOOL = 'lamerhav';
const BASE = 'https://java-bagrut.vercel.app';
const KEY = (fs.readFileSync('C:/Users/Moti Levi/Desktop/AI/java Bagrut/app.html', 'utf8').match(/apiKey:\s*"([^"]+)"/) || [])[1];
const FSH = 'firestore.googleapis.com', DOCS = '/v1/projects/' + PROJECT + '/databases/(default)/documents';
const safe = e => e.replace(/[.@]/g, '_');
const STUDENT = { email: 'ai-probe@example.com', pass: 'A1Probe!2026' };

function oauth() { return new Promise(res => { const c = JSON.parse(fs.readFileSync(path.join(os.homedir(), '.config', 'configstore', 'firebase-tools.json'), 'utf8'));
  const b = 'grant_type=refresh_token&refresh_token=' + encodeURIComponent(c.tokens.refresh_token) + '&client_id=563584335869-fgrhgmd47bqnekij5i8b5pr03ho849e6.apps.googleusercontent.com&client_secret=j9iVZfS8kkCEFUPaAeJV0sAi';
  const q = https.request({ hostname: 'oauth2.googleapis.com', path: '/token', method: 'POST', headers: { 'Content-Type': 'application/x-www-form-urlencoded' } }, s => { s.setEncoding('utf8'); let d = ''; s.on('data', x => d += x); s.on('end', () => res(JSON.parse(d).access_token)); }); q.write(b); q.end(); }); }
function call(host, p, method, headers, payload) { return new Promise(res => { const data = payload == null ? null : JSON.stringify(payload); const h = Object.assign({}, headers); if (data) h['Content-Length'] = Buffer.byteLength(data);
  const r = https.request({ hostname: host, path: p, method, headers: h }, s => { s.setEncoding('utf8'); let d = ''; s.on('data', c => d += c); s.on('end', () => res({ code: s.statusCode, body: d })); });
  r.on('error', e => res({ code: 0, body: String(e) })); if (data) r.write(data); r.end(); }); }
const J = { 'Content-Type': 'application/json' };
const adm = t => ({ Authorization: 'Bearer ' + t, 'Content-Type': 'application/json' });
const S = v => ({ stringValue: v });

let pass = 0, fail = 0;
const t = (n, c, d) => { c ? pass++ : fail++; console.log((c ? 'PASS ' : 'FAIL ') + n + (c ? '' : ' — ' + d)); };

(async () => {
  const tok = await oauth();
  const cfg = JSON.parse((await call('identitytoolkit.googleapis.com', '/admin/v2/projects/' + PROJECT + '/config', 'GET', adm(tok))).body);
  const wasEnabled = !!(cfg.signIn && cfg.signIn.email && cfg.signIn.email.enabled);
  if (!wasEnabled) await call('identitytoolkit.googleapis.com', '/admin/v2/projects/' + PROJECT + '/config?updateMask=signIn.email.enabled,signIn.email.passwordRequired', 'PATCH', adm(tok), { signIn: { email: { enabled: true, passwordRequired: true } } });
  let b;
  try {
    let r = await call('identitytoolkit.googleapis.com', '/v1/accounts:signUp?key=' + KEY, 'POST', J, { email: STUDENT.email, password: STUDENT.pass, returnSecureToken: true });
    if (r.code !== 200) r = await call('identitytoolkit.googleapis.com', '/v1/accounts:signInWithPassword?key=' + KEY, 'POST', J, { email: STUDENT.email, password: STUDENT.pass, returnSecureToken: true });
    await call('identitytoolkit.googleapis.com', '/v1/projects/' + PROJECT + '/accounts:update', 'POST', adm(tok), { localId: JSON.parse(r.body).localId, emailVerified: true });
    // תלמידה ותיקה: בלי שדה approved, בדיוק כמו התלמידות האמיתיות של למרחב
    await call(FSH, DOCS + '/schools/' + SCHOOL + '/users?documentId=' + safe(STUDENT.email), 'POST', adm(tok), { fields: { email: S(STUDENT.email), name: S('תלמידת בדיקת AI'), grade: S('יא') } });

    try { b = await chromium.launch({ channel: 'chrome' }); } catch (e) { b = await chromium.launch({ channel: 'msedge' }); }
    const pg = await b.newPage({ viewport: { width: 430, height: 950 } });
    const workerCalls = [];
    pg.on('response', async resp => { if (resp.url().includes('gemini-proxy')) { let body = ''; try { body = await resp.text(); } catch (e) {} workerCalls.push({ status: resp.status(), body }); } });
    const consoleErrs = []; pg.on('console', m => { if (m.type() === 'error') consoleErrs.push(m.text().slice(0, 140)); });

    await pg.goto(BASE + '/' + SCHOOL, { waitUntil: 'domcontentloaded', timeout: 60000 });
    await pg.waitForSelector('text=התחברות עם Google', { timeout: 45000 });
    await pg.evaluate(async ([e, p]) => { await firebase.auth().signInWithEmailAndPassword(e, p); }, [STUDENT.email, STUDENT.pass]);
    await pg.waitForTimeout(11000);
    t('התלמידה נכנסה ל-למרחב ורואה נושאים', /מערכים|לולאות/.test(await pg.textContent('body')));

    // ---- שלב 1: קריאות מתוך הדף של האתר החי ----
    const direct = await pg.evaluate(async () => {
      const W = 'https://gemini-proxy.motiml77.workers.dev';
      const post = async (body) => { try { const r = await fetch(W, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) }); return { ok: r.ok, status: r.status, json: await r.json() }; } catch (e) { return { err: String(e) }; } };
      const chat = await post({ school: 'lamerhav', action: 'chat', messages: [{ role: 'user', content: 'מה ההבדל בין == ל-equals בג׳אווה? ענה בשני משפטים.' }], context: { questionText: 'השוואת מחרוזות' } });
      const hw = await post({ school: 'lamerhav', action: 'checkHomework', code: 'int sum = 0;\nfor (int i = 1; i <= n; i++) {\n    sum += i;\n}\nSystem.out.println(sum);', questionText: 'כתבי תוכנית שמחשבת ומדפיסה את סכום המספרים מ-1 עד n. עבור n = 10 הפלט צריך להיות 55.', questionPoints: 40, checkCount: 0 });
      const wrong = await post({ school: 'lamerhav', action: 'checkHomework', code: 'int sum = 0;\nfor (int i = 1; i < n; i++) {\n    sum += i;\n}\nSystem.out.println(sum);', questionText: 'כתבי תוכנית שמחשבת ומדפיסה את סכום המספרים מ-1 עד n. עבור n = 10 הפלט צריך להיות 55.', questionPoints: 40, checkCount: 0 });
      return { chat, hw, wrong };
    });
    const c = direct.chat, h = direct.hw, w = direct.wrong;
    t('chat: הדפדפן לא חסם (CORS)', !c.err, c.err);
    t('chat: ענה', c.ok && c.json && c.json.response, JSON.stringify(c).slice(0, 160));
    console.log('   chat → מודל:', c.json && c.json.modelUsed, '· "' + String(c.json && c.json.response).replace(/\s+/g, ' ').slice(0, 110) + '"');
    t('checkHomework (פתרון נכון): הצליח', h.ok, JSON.stringify(h).slice(0, 200));
    console.log('   נכון → מודל:', h.json && h.json.modelUsed, '· ציון:', JSON.stringify((h.json || {}).score ?? (h.json || {}).grade ?? Object.keys(h.json || {})));
    console.log('   טעות off-by-one → ציון:', JSON.stringify((w.json || {}).score ?? (w.json || {}).grade ?? Object.keys(w.json || {})), '· מודל:', w.json && w.json.modelUsed);
    console.log('   תשובת בדיקה (חלק):', JSON.stringify(h.json).slice(0, 380));
    t('המודל שענה הוא הדור החדש (3.8)', c.json && c.json.modelUsed === 'gemini-3.8-flash', c.json && c.json.modelUsed);

    console.log('\n===== ' + pass + '/' + (pass + fail) + ' =====');
    console.log('קריאות לוורקר שנצפו ברשת:', workerCalls.length, '· סטטוסים:', workerCalls.map(x => x.status).join(','));
    console.log('שגיאות CORS בקונסול:', consoleErrs.filter(e => /CORS|blocked|Access-Control/i.test(e)).length);
  } catch (e) { fail++; console.log('שגיאה: ' + String(e && e.message).slice(0, 250)); }
  finally {
    if (b) await b.close();
    await call(FSH, DOCS + '/schools/' + SCHOOL + '/users/' + safe(STUDENT.email), 'DELETE', adm(tok));
    const l = JSON.parse((await call('identitytoolkit.googleapis.com', '/v1/projects/' + PROJECT + '/accounts:lookup', 'POST', adm(tok), { email: [STUDENT.email] })).body);
    if (l.users && l.users[0]) await call('identitytoolkit.googleapis.com', '/v1/projects/' + PROJECT + '/accounts:delete', 'POST', adm(tok), { localId: l.users[0].localId });
    if (!wasEnabled) await call('identitytoolkit.googleapis.com', '/admin/v2/projects/' + PROJECT + '/config?updateMask=signIn.email.enabled', 'PATCH', adm(tok), { signIn: { email: { enabled: false } } });
    console.log('נוקה.'); process.exit(fail ? 1 : 0);
  }
})();
