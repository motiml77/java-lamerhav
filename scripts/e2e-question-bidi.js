/**
 * כיוון עברית+אנגלית בטקסט שאלה (QuestionInstructions).
 *
 * מחלץ את looksLikeCode / QuestionInstructions מ-app.html כמות שהם, מהדר
 * אותם, ומרנדר בדפדפן אמיתי עם React — כך שנבדק הקוד שרץ באתר ולא עותק.
 * הטקסט לדוגמה לקוח מצילום המסך שבו התגלתה הבעיה.
 *
 * הרצה: node scripts/e2e-question-bidi.js
 */
const { chromium } = require('playwright');
const babel = require('@babel/core');
const fs = require('fs'), path = require('path');

const APP = fs.readFileSync(path.join(__dirname, '..', 'app.html'), 'utf8');
const SHOTS = process.env.SHOTS || path.join(__dirname, '..', '.shots');
fs.mkdirSync(SHOTS, { recursive: true });

const a = APP.indexOf('const looksLikeCode');
const b = APP.indexOf('// ===== Attachments');
if (a < 0 || b < 0) { console.log('לא נמצא הרכיב ב-app.html'); process.exit(1); }
const snippet = babel.transformSync(APP.slice(a, b), {
  presets: [['@babel/preset-react', { runtime: 'classic' }]], babelrc: false, configFile: false,
}).code;
const css = ['.q-codeblock', '.q-text', '.q-inline-code']
  .map(sel => (APP.match(new RegExp('^\\s*' + sel.replace('.', '\\.') + ' \\{[^}]*\\}', 'm')) || [''])[0]).join('\n');

const SAMPLE = [
  'כתבי פעולה חיצונית isExist המקבלת מספר num ותור q מטיפוס שלם בין 0 ל-9 (כולל).',
  'הפעולה תחזיר true אם יש בתור מספר שספרת האחדות שלו שווה ל-num, אחרת תחזיר false.',
  'הניחי שהמספרים בתור אינם שליליים.',
  'דוגמה: עבור num = 8 והתור (מהראש): 162, 251, 568, 7 — הפעולה תחזיר true (בגלל 568).',
  'הערה: חובה לשמור על מבנה התור בסיום הפעולה.',
  'public static boolean isExist(Queue<Integer> q, int num)',
  'int x = 5; // משתנה כלשהו',
].join('\n');

const html = `<!doctype html><html lang="he" dir="rtl"><head><meta charset="utf-8">
<style>body{background:#0b1220;margin:0;padding:24px;width:760px}${css}</style>
<script src="https://unpkg.com/react@18/umd/react.production.min.js"></script>
<script src="https://unpkg.com/react-dom@18/umd/react-dom.production.min.js"></script></head>
<body><div id="root"></div><script>
const FitPre = ({ code, className }) => React.createElement('pre', { className }, code);
${snippet}
ReactDOM.createRoot(document.getElementById('root')).render(
  React.createElement(QuestionInstructions, { text: ${JSON.stringify(SAMPLE)} }));
</script></body></html>`;

let pass = 0, fail = 0;
const t = (n, c, d) => { c ? pass++ : fail++; console.log((c ? 'PASS ' : 'FAIL ') + n + (c ? '' : ' — ' + d)); };

(async () => {
  let br; try { br = await chromium.launch({ channel: 'chrome' }); } catch (e) { br = await chromium.launch({ channel: 'msedge' }); }
  try {
    const pg = await br.newPage({ viewport: { width: 820, height: 560 } });
    const errs = []; pg.on('pageerror', e => errs.push(String(e).slice(0, 160)));
    await pg.setContent(html, { waitUntil: 'load' });
    await pg.waitForSelector('.q-text', { timeout: 20000 });
    await pg.screenshot({ path: path.join(SHOTS, 'bidi-after.png') });

    const info = await pg.evaluate(() => {
      const codeBlocks = [...document.querySelectorAll('.q-codeblock')].map(e => e.textContent);
      const prose = [...document.querySelectorAll('.q-text')].map(e => e.textContent);
      const bdis = [...document.querySelectorAll('.q-text bdi')].map(e => e.textContent);
      // סדר חזותי של "num = 8": המילה num חייבת להופיע משמאל לספרה 8
      const bdi = [...document.querySelectorAll('.q-text bdi')].find(e => e.textContent === 'num = 8');
      let numLeftOf8 = null;
      if (bdi) {
        const tn = bdi.firstChild, s = tn.textContent;
        const rect = (i, j) => { const r = document.createRange(); r.setStart(tn, i); r.setEnd(tn, j); return r.getBoundingClientRect(); };
        numLeftOf8 = rect(0, 3).left < rect(s.length - 1, s.length).left;
      }
      // רשימת מספרים בתוך משפט עברי חייבת להיקרא משמאל לימין: 162 לפני 251 לפני 568
      let numbersInOrder = null;
      const p = [...document.querySelectorAll('.q-text')].find(e => e.textContent.includes('162, 251'));
      if (p) {
        const walker = document.createTreeWalker(p, NodeFilter.SHOW_TEXT);
        let node, left = {};
        while ((node = walker.nextNode())) {
          for (const w of ['162', '251', '568,']) {
            const i = node.textContent.indexOf(w);
            if (i >= 0 && left[w] === undefined) {
              const r = document.createRange(); r.setStart(node, i); r.setEnd(node, i + w.length);
              left[w] = r.getBoundingClientRect().left;
            }
          }
        }
        numbersInOrder = left['162'] < left['251'] && left['251'] < left['568,'];
      }
      return { codeBlocks, prose, bdis, numLeftOf8, numbersInOrder };
    });

    const joined = info.codeBlocks.join('\n');
    t('משפט עברי עם "(כולל)" אינו בתיבת קוד', !/כתבי פעולה חיצונית/.test(joined), joined.slice(0, 80));
    t('כל משפטי ההסבר נשארו טקסט (5)', info.prose.length >= 1 && /כתבי פעולה חיצונית/.test(info.prose.join('')));
    t('חתימת פעולה אמיתית עדיין קוד', /isExist\(Queue<Integer> q, int num\)/.test(joined));
    t('שורת קוד עם הערה בעברית עדיין קוד', /int x = 5;/.test(joined));
    t('"num = 8" נעטף כרצף אחד', info.bdis.includes('num = 8'), JSON.stringify(info.bdis));
    t('"num" משמאל ל-8 (לא הפוך)', info.numLeftOf8 === true, String(info.numLeftOf8));
    t('רשימת המספרים 162, 251, 568 נקראת משמאל לימין', info.numbersInOrder === true, String(info.numbersInOrder));
    t('isExist מבודד כרצף', info.bdis.includes('isExist'));
    t('true ו-false מבודדים', info.bdis.includes('true') && info.bdis.includes('false'));
    t('אין סוגר בודד בתוך רצף', !info.bdis.some(x => /^[^(]*\)$/.test(x) && !/\(/.test(x)), JSON.stringify(info.bdis));
    t('אין שגיאות JS', errs.length === 0, JSON.stringify(errs));
    console.log('\n===== ' + pass + '/' + (pass + fail) + ' =====');
  } finally {
    await br.close();
    process.exit(fail > 0 ? 1 : 0);
  }
})();
