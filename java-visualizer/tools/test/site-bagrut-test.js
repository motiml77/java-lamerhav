// בודק את האתר בדפדפן אמיתי (Chromium): "שאלת בגרות לקוד" (bagrut.html) ומונה ההורדות (index.html).
// הבקשה מהדפדפן עוברת לקוד האמיתי של השרת (worker/src/index.js) — רק Gemini מדומה — כך שנבדק גם שהשרת מקבל את מה שהדף שולח.
//   node site-bagrut-test.js [out-dir]   (צילומי מסך: bagrut-*.png)
const fs = require('fs'), path = require('path'), http = require('http'), assert = require('assert');
const ROOT = path.join(__dirname, '..', '..');
const SITE = path.join(ROOT, 'site');
let playwright, JSZip;
for (const p of ['playwright', '/opt/node-tools/node_modules/playwright']) { try { playwright = require(p); break; } catch (e) { /* הבא */ } }
for (const p of ['jszip', '/opt/node-tools/node_modules/jszip']) { try { JSZip = require(p); break; } catch (e) { /* הבא */ } }
if (!playwright || !JSZip) { console.log('playwright / jszip לא מותקנים — מדלגים על בדיקת האתר'); process.exit(0); }
const JSZIP_FILE = path.join(path.dirname(require.resolve(fs.existsSync('/opt/node-tools/node_modules/jszip') ? '/opt/node-tools/node_modules/jszip' : 'jszip')), '..', 'dist', 'jszip.min.js');
const out = process.argv[2] || path.join(ROOT, 'build', 'test');
fs.mkdirSync(out, { recursive: true });
const SERVER = 'https://dsviz-exercise.motiml77.workers.dev';
const S = require(path.join(SITE, 'assets', 'scaffold.js'));
const SAMPLE = JSON.parse(fs.readFileSync(path.join(__dirname, 'ai', '1-stack.json'), 'utf8'));

let ok = 0;
function check(c, msg) { assert.ok(c, msg); }
function pass(msg) { ok++; console.log('  ok  ' + msg); }

// שרת קטן לתיקיית האתר (כמו GitHub Pages)
function serve() {
  const types = { '.html': 'text/html; charset=utf-8', '.css': 'text/css', '.js': 'text/javascript', '.svg': 'image/svg+xml', '.png': 'image/png', '.webp': 'image/webp', '.mp4': 'video/mp4' };
  return new Promise(res => {
    const s = http.createServer((q, r) => {
      const f = path.join(SITE, decodeURIComponent(q.url.split('?')[0]).replace(/^\/+/, '') || 'index.html');
      if (!f.startsWith(SITE) || !fs.existsSync(f) || fs.statSync(f).isDirectory()) { r.writeHead(404); r.end(); return; }
      r.writeHead(200, { 'content-type': types[path.extname(f)] || 'application/octet-stream' });
      fs.createReadStream(f).pipe(r);
    }).listen(0, '127.0.0.1', () => res(s));
  });
}

async function docx() {
  const z = new JSZip();
  z.file('[Content_Types].xml', '<?xml version="1.0"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"/>');
  z.file('word/document.xml', '<?xml version="1.0" encoding="UTF-8"?><w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:body>'
    + '<w:p><w:r><w:t>שאלה 2. כתבו פעולה המקבלת מחסנית</w:t></w:r></w:p>'
    + '<w:tbl><w:tr><w:tc><w:p><w:r><w:t>קלט</w:t></w:r></w:p></w:tc><w:tc><w:p><w:r><w:t>פלט</w:t></w:r></w:p></w:tc></w:tr></w:tbl>'
    + '</w:body></w:document>');
  z.file('word/media/image1.png', fs.readFileSync(path.join(__dirname, 'ai', 'files', 'big.png')));
  return z.generateAsync({ type: 'nodebuffer' });
}

(async () => {
  const worker = (await import(path.join(ROOT, 'worker', 'src', 'index.js'))).default;
  let gemini = { exercise: SAMPLE }, seen = null, asked = null;
  // Gemini המדומה: מחזיר את התרגיל לדוגמה (ושומר את הבקשה שהשרת שלח אליו)
  globalThis.fetch = async (url, init) => (asked = init && init.body, null) || new Response(JSON.stringify({ candidates: [{ finishReason: 'STOP', content: { parts: [{ text: JSON.stringify(Object.assign({ isExercise: true }, gemini.exercise)) }] } }] }), { status: 200 });
  const server = await serve();
  const base = 'http://127.0.0.1:' + server.address().port;
  const ENV = { GEMINI_API_KEY: 'k', MODELS: 'm', SITE_ORIGINS: base, APP_KEY: 'plugin-only', STATS: { get: async k => (k === 'dl:windows' ? '1230' : '4'), put: async () => {} } };

  const browser = await playwright.chromium.launch();
  const ctx = await browser.newContext({ viewport: { width: 1280, height: 900 }, acceptDownloads: true });
  await ctx.route('https://cdnjs.cloudflare.com/ajax/libs/jszip/**', r => r.fulfill({ path: JSZIP_FILE, contentType: 'text/javascript' }));
  await ctx.route('https://fonts.googleapis.com/**', r => r.fulfill({ body: '', contentType: 'text/css' }));
  await ctx.route(SERVER + '/**', async r => {
    const q = r.request();
    const headers = Object.assign({}, await q.allHeaders());
    const body = q.method() === 'POST' ? q.postDataBuffer() : undefined;
    if (q.url().endsWith('/v1/site/exercise') && q.method() === 'POST') seen = { headers, body };
    const res = await worker.fetch(new Request(q.url(), { method: q.method(), headers, body }), ENV, {});
    const h = {}, buf = Buffer.from(await res.arrayBuffer());
    res.headers.forEach((v, k) => { h[k] = v; });
    if (seen && res.status === 200 && q.url().endsWith('/v1/site/exercise')) seen.exercise = JSON.parse(buf.toString('utf8')).exercise;
    await r.fulfill({ status: res.status, headers: h, body: buf });
  });

  // ---- מונה ההורדות בעמוד הראשי
  const page = await ctx.newPage();
  await page.goto(base + '/index.html');
  await page.waitForFunction(() => !document.querySelector('[data-downloads]').hidden, null, { timeout: 5000 });
  // המונה עולה עד המספר האמיתי כשהוא על המסך — מחכים שיגיע אליו
  await page.waitForFunction(() => /1,238/.test(document.querySelector('[data-downloads]').textContent), null, { timeout: 3000 }).catch(() => {});
  const count = await page.textContent('[data-downloads]');
  check(/1,238/.test(count) && /הורדות/.test(count), 'מונה: ' + count);
  // ההורדה ישירות מ-GitHub (עובדת גם כשהשרת לא זמין), והמונה מקבל "נספר" ברקע
  check(/^https:\/\/raw\.githubusercontent\.com\/.*\/release\/DSViz-Setup\.exe$/.test(await page.getAttribute('.hero .btn', 'href')), 'כפתור ההורדה ישירות מ-GitHub');
  let counted = null;
  await ctx.route('https://raw.githubusercontent.com/**', r => r.fulfill({ status: 200, contentType: 'application/octet-stream', body: 'x' }));
  const beacon = page.waitForRequest(q => q.url() === SERVER + '/v1/count/windows' && q.method() === 'POST', { timeout: 5000 }).then(() => { counted = true; });
  await Promise.all([page.waitForEvent('download', { timeout: 5000 }).catch(() => null), page.click('.hero .btn')]);
  await beacon.catch(() => {});
  check(counted, 'ספירה ברקע אחרי לחיצה על הורדה');
  check(await page.$('footer .donate[href="https://www.paypal.com/ncp/payment/DJYTDJ6F57KA2"][target=_blank][rel~=noopener]'), 'תמכו בפרויקט בתחתית');
  pass('עמוד ראשי: מונה הורדות (' + count + '), הורדה ישירה מ-GitHub עם ספירה ברקע, ו"תמכו בפרויקט"');

  // ---- שאלת בגרות: Word + תמונה + PDF
  await page.goto(base + '/bagrut.html');
  check(await page.isDisabled('[data-send]'), 'בלי קבצים — אי אפשר לשלוח');
  // שם בעברית — כקובץ בזיכרון (מנתיב בעברית Playwright לא תמיד מעלה)
  const word = { name: 'שאלה.docx', mimeType: 'application/vnd.openxmlformats-officedocument.wordprocessingml.document', buffer: await docx() };
  const fileOf = f => ({ name: f, mimeType: f.endsWith('.pdf') ? 'application/pdf' : 'image/jpeg', buffer: fs.readFileSync(path.join(__dirname, 'ai', 'files', f)) });
  await page.setInputFiles('[data-input]', [word, fileOf('photo.jpg'), fileOf('exam.pdf')]);
  page.on('pageerror', e => console.log('    [pageerror] ' + e.message));
  await page.waitForFunction(() => document.querySelectorAll('[data-chosen] li').length === 3, null, { timeout: 15000 })
    .catch(async e => { throw new Error('קבצים: ' + (await page.textContent('[data-chosen]')) + ' | סטטוס: ' + (await page.textContent('[data-status]'))); });
  await page.setInputFiles('[data-input]', [path.join(__dirname, 'ai', 'files', 'old.doc')]);
  await page.waitForSelector('[data-status].bad');
  check(/docx/.test(await page.textContent('[data-status]')), 'Word ישן — הודעה');
  check(await page.$$eval('[data-chosen] li', l => l.length) === 3, 'הקובץ הישן לא נוסף');
  pass('קבצים: Word, צילום ו-PDF נוספו; Word ישן (doc) נדחה עם הסבר');
  await page.screenshot({ path: path.join(out, 'bagrut-form.png'), fullPage: true });

  await page.fill('textarea[name=note]', 'רק שאלה 2');
  await page.click('[data-send]');
  await page.waitForSelector('[data-result]:not([hidden])', { timeout: 20000 });
  // מה הגיע לשרת
  const form = await new Request('http://x/', { method: 'POST', headers: { 'content-type': seen.headers['content-type'] }, body: seen.body }).formData();
  const meta = JSON.parse(await form.get('meta').text());
  check(meta.language === 'java' && meta.note === 'רק שאלה 2', 'meta: ' + JSON.stringify(meta).slice(0, 120));
  check(meta.texts.length === 1 && /שאלה 2\. כתבו/.test(meta.texts[0].text) && /קלט \| פלט/.test(meta.texts[0].text), 'הטקסט מה-Word: ' + JSON.stringify(meta.texts));
  const files = form.getAll('file');
  check(files.length === 3 && meta.names.length === 3, 'קבצים: ' + files.length + ' שמות: ' + meta.names);
  check(files.map(f => f.type).join() === 'image/png,image/jpeg,application/pdf', 'סוגים: ' + files.map(f => f.type));
  check(meta.names[0] === 'שאלה.docx (1)' && meta.names[1] === 'photo.jpg', 'השמות בעברית ב-meta (כמו ב-Eclipse): ' + meta.names);
  pass('הבקשה: טקסט ה-Word (כולל טבלה), התמונה שבתוכו, הצילום וה-PDF, הערה ושפה — והשרת קיבל אותה');
  // מה השרת שלח ל-Gemini: אזהרה שהקבצים לא בהכרח לפי הסדר, וכל חלק ממוספר (גם הטקסט מה-Word)
  const gparts = JSON.parse(asked).contents[0].parts.filter(p => typeof p.text === 'string').map(p => p.text);
  check(/4 חלקים/.test(gparts[0]) && /לא בהכרח צורפו לפי הסדר/.test(gparts[0]) && /readingOrder/.test(gparts[0]), 'אזהרת הסדר: ' + gparts[0].slice(0, 80));
  const labels = gparts.filter(t => t.startsWith('[קובץ ')).map(t => t.split('\n')[0]);
  check(labels.join('|') === '[קובץ 1 מתוך 4: שאלה.docx (1)]|[קובץ 2 מתוך 4: photo.jpg]|[קובץ 3 מתוך 4: exam.pdf]|[קובץ 4 מתוך 4: טקסט מתוך שאלה.docx]', 'מספור: ' + labels.join(' | '));
  // meta מהאתר (Blob) נקרא בשרת: הטקסט מה-Word וההערה הגיעו ל-Gemini
  check(gparts.some(t => /^\[קובץ 4 מתוך 4: טקסט מתוך שאלה\.docx\]\nשאלה 2\. כתבו/.test(t)) && gparts.some(t => /^הערה מהתלמיד.*רק שאלה 2$/.test(t)), 'הטקסט וההערה ל-Gemini');
  // אחרי כל החלקים, לפני ההערה — תזכורת לקבוע קודם את הסדר
  check(/^סוף החלקים \(4\)/.test(gparts[gparts.length - 2]) && /^הערה מהתלמיד/.test(gparts[gparts.length - 1]), 'התזכורת אחרי החלקים: ' + gparts.slice(-2).join(' | ').slice(0, 120));
  check(JSON.stringify(seen.exercise.readingOrder) === JSON.stringify(SAMPLE.readingOrder) && seen.exercise.pageNotes === SAMPLE.pageNotes, 'readingOrder בתשובה: ' + JSON.stringify(seen.exercise.readingOrder));
  pass('ל-Gemini: "הקבצים לא בהכרח לפי הסדר", ' + labels.length + ' חלקים ממוספרים ותזכורת אחריהם; readingOrder חוזר בתשובה והדף מתעלם ממנו');

  // התוצאה: אותם קבצים כמו ב-Eclipse
  const e = S.read(seen.exercise), pkg = S.folderName(e.topic, new Set(), false);
  const expect = S.build(e, pkg, ['שאלה.docx', 'photo.jpg', 'exam.pdf'], false);
  check((await page.textContent('[data-title]')) === SAMPLE.title, 'כותרת');
  const tabs = await page.$$eval('[data-tabs] button', b => b.map(x => x.textContent));
  check(tabs.length === expect.length && tabs[0] === expect[0].className + '.java', 'לשוניות: ' + tabs);
  const code = await page.textContent('[data-code]');
  check(code === expect[0].code, 'הקוד בדף = הקוד שבונה scaffold.js');
  check(code.includes('package ' + pkg + ';'), 'חבילה ' + pkg);
  check(!code.includes(e.tasks[0].solution.split('\n')[1].trim()), 'בלי פתרון בקוד');
  pass('התוצאה: ' + tabs.join(', ') + ' — חבילה ' + pkg + ', פעולות ריקות, בלי הפתרון');

  check(!(await page.isHidden('[data-expected-box]')) && (await page.textContent('[data-expected]')) === SAMPLE.expectedOutput, 'פלט צפוי');
  const sols = await page.$$('[data-sols] details');
  check(sols.length === 2, 'שתי הצעות');
  check(await page.$$eval('[data-sols] details', d => d.every(x => !x.open)), 'ההצעות סגורות');
  check(!(await page.isVisible('[data-sols] details pre')), 'הפתרון לא נראה לפני לחיצה');
  await page.click('[data-sols] details summary');
  check(await page.isVisible('[data-sols] details pre'), 'נפתח בלחיצה');
  check(await page.textContent('[data-others]') === 'ג. ' + SAMPLE.otherQuestions[0].text, 'סעיף בלי קוד');
  pass('פלט צפוי, הצעות לפתרון סגורות עד לחיצה, וסעיף ג (בלי קוד)');

  await page.check('[data-with-sol]');
  const solved = await page.textContent('[data-code]');
  check(solved === S.build(e, pkg, ['שאלה.docx', 'photo.jpg', 'exam.pdf'], true)[0].code && solved !== code, 'עם פתרונות');
  const [dl] = await Promise.all([page.waitForEvent('download'), page.click('[data-zip]')]);
  check(dl.suggestedFilename() === pkg + '-solution.zip', 'שם: ' + dl.suggestedFilename());
  const zip = await JSZip.loadAsync(fs.readFileSync(await dl.path()));
  const names = Object.keys(zip.files).filter(n => !zip.files[n].dir).sort();
  check(names.join() === expect.map(f => pkg + '/' + f.className + '.java').sort().join(), 'בתוך ה-ZIP: ' + names);
  check((await zip.file(pkg + '/' + expect[0].className + '.java').async('string')) === solved, 'תוכן הקובץ ב-ZIP');
  pass('ZIP: ' + names.join(', ') + ' (עם הפתרונות כשמסמנים)');
  await page.uncheck('[data-with-sol]');
  await page.screenshot({ path: path.join(out, 'bagrut-result.png'), fullPage: true });

  // טלפון: בלי גלילה הצידה
  const phone = await ctx.newPage();
  await phone.setViewportSize({ width: 390, height: 800 });
  await phone.goto(base + '/bagrut.html');
  check(await phone.evaluate(() => document.documentElement.scrollWidth <= innerWidth), 'גלילה הצידה בטלפון (טופס)');
  await phone.screenshot({ path: path.join(out, 'bagrut-phone.png'), fullPage: true });
  pass('טלפון: הטופס בלי גלילה הצידה');

  // שגיאה מהשרת (לא תרגיל) — מוצגת, והטופס נשאר
  gemini = { exercise: { isExercise: false, problem: 'זה מתכון לעוגה, לא תרגיל.', title: '', summary: '', topic: '', mainClass: '', imports: [], classes: [], tasks: [], givenCode: [], otherQuestions: [], mainCode: '' } };
  await page.click('[data-again]');
  await page.setInputFiles('[data-input]', [path.join(__dirname, 'ai', 'files', 'exam.pdf')]);
  await page.waitForFunction(() => document.querySelectorAll('[data-chosen] li').length === 1);
  await page.click('[data-send]');
  await page.waitForSelector('[data-status].bad', { timeout: 15000 });
  const err = await page.textContent('[data-status]');
  check(/מתכון|תרגיל/.test(err) && await page.isHidden('[data-result]'), 'שגיאה: ' + err);
  check(!(await page.isDisabled('[data-send]')), 'אפשר לשלוח שוב');
  pass('השרת אומר שזה לא תרגיל: ההסבר מוצג, ואפשר לנסות שוב');

  // C#: הבחירה ב-C# מגיעה לשרת (meta כ-Blob) ול-Gemini, והדף בונה קובצי ‎.cs ל-Visual Studio — כמו ב-Visual Studio עצמו
  const CS = JSON.parse(fs.readFileSync(path.join(__dirname, 'ai', 'cs', '2-classes.json'), 'utf8'));
  gemini = { exercise: CS };
  asked = null; seen = null;
  check(await page.$$eval('[data-chosen] li', l => l.length) === 1, 'נשאר קובץ אחד (exam.pdf) אחרי השגיאה');
  await page.check('input[name=lang][value=csharp]');
  await page.click('[data-send]');
  await page.waitForSelector('[data-result]:not([hidden])', { timeout: 20000 });
  const csReq = JSON.parse(asked);
  check(/The student works in C#\./.test(csReq.systemInstruction.parts[0].text) && /void Push\(T x\)/.test(csReq.systemInstruction.parts[0].text), 'ההנחיות ל-Gemini ב-C#');
  const csParts = csReq.contents[0].parts.filter(p => typeof p.text === 'string').map(p => p.text);
  check(/חלק אחד/.test(csParts[0]) && !/לא בהכרח צורפו לפי הסדר/.test(csParts[0]) && !csParts.some(t => /^סוף החלקים/.test(t)), 'קובץ אחד — בלי אזהרת סדר ובלי תזכורת: ' + csParts[0].slice(0, 60));
  check(seen.exercise.language === 'csharp', 'השפה בתשובה: ' + seen.exercise.language);
  const ce = S.read(seen.exercise), cpkg = S.folderName(ce.topic, new Set(), true);
  check(ce.cs, 'scaffold.js קרא את התשובה כ-C#');
  const cexpect = S.build(ce, cpkg, ['exam.pdf'], false);
  const ctabs = await page.$$eval('[data-tabs] button', b => b.map(x => x.textContent));
  check(ctabs.join() === cexpect.map(f => f.className + '.cs').join() && ctabs[0] === 'Program.cs' && ctabs.includes('Coach.cs') && ctabs.includes('Referee.cs'), 'לשוניות C#: ' + ctabs);
  check((await page.textContent('[data-code]')) === cexpect[0].code, 'הקוד ב-C# = מה שבונה scaffold.js');
  check((await page.textContent('[data-code]')).includes('public static void Main(string[] args)'), 'Main של C#');
  check(/Visual Studio/.test(await page.textContent('[data-where]')) && (await page.textContent('[data-where]')).includes(cpkg), 'הסבר ל-Visual Studio: ' + (await page.textContent('[data-where]')));
  // Coach — סעיף במחלקה שלא תוארה, Referee — מחלקה שתוארה ריקה: שתיהן נוצרות עם הערת התכונות
  for (const name of ['Coach', 'Referee']) {
    await page.$$eval('[data-tabs] button', (b, n) => b.find(x => x.textContent === n + '.cs').click(), name);
    const txt = await page.textContent('[data-code]');
    check(txt.includes('public class ' + name) && txt.includes(S.ADD_FIELDS) && txt === cexpect.find(f => f.className === name).code, name + '.cs עם הערת התכונות');
  }
  const [cdl] = await Promise.all([page.waitForEvent('download'), page.click('[data-zip]')]);
  check(cdl.suggestedFilename() === cpkg + '.zip', 'שם ה-ZIP: ' + cdl.suggestedFilename());
  const czip = await JSZip.loadAsync(fs.readFileSync(await cdl.path()));
  const cnames = Object.keys(czip.files).filter(n => !czip.files[n].dir).sort();
  check(cnames.join() === cexpect.map(f => cpkg + '/' + f.className + '.cs').sort().join(), 'בתוך ה-ZIP (C#): ' + cnames);
  await page.screenshot({ path: path.join(out, 'bagrut-result-cs.png'), fullPage: true });
  pass('C#: ההנחיות ב-C#, language=csharp בתשובה, ' + ctabs.join(', ') + ' (Coach / Referee עם הערת התכונות) ו-ZIP ' + cpkg);

  // מאתר אחר — השרת חוסם
  const evil = await worker.fetch(new Request(SERVER + '/v1/site/exercise', { method: 'POST', headers: { origin: 'https://evil.example' } }), ENV, {});
  check(evil.status === 403, 'מאתר אחר: ' + evil.status);
  pass('שליחה מאתר אחר נחסמת (403)');

  await browser.close();
  server.close();
  console.log(ok + ' בדיקות של האתר עברו');
})().catch(e => { console.error('  FAIL', e && e.message || e); process.exit(1); });
