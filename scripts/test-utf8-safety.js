/**
 * הגנה מפני הרס עברית בקריאות HTTP בסקריפטים.
 *
 * הפגם: `res.on('data', c => body += c)` מפענח כל חתיכה בנפרד, ואות עברית
 * (2 בתים) שנחתכת בין שתי חתיכות הופכת ל-U+FFFD U+FFFD. סקריפט שקורא כך
 * ואז כותב — כמו migrate-lamerhav.js — משכפל טקסט הרוס לתוך המסד. בפועל זה
 * קרה: אפס פגמים בעותק המקורי, עשרות ב-lamerhav וב-demo שהועתקו ממנו.
 *
 * הבדיקה עושה שני דברים:
 *  1. מוכיחה על שרת אמיתי שהדפוס הישן באמת שובר טקסט, ושהתיקון לא — כדי
 *     שהיא לא תעבור "על כלום".
 *  2. סורקת כל סקריפט בריפו וכושלת אם נשאר בו קורא לא מוגן.
 *
 * הרצה: node scripts/test-utf8-safety.js
 */
const http = require('http'), fs = require('fs'), path = require('path');

let pass = 0, fail = 0;
const t = (n, c, d) => { c ? pass++ : fail++; console.log((c ? 'PASS ' : 'FAIL ') + n + (c ? '' : ' — ' + d)); };

const PAYLOAD = JSON.stringify({ text: 'כתבי פעולה חיצונית — הפעולה מחזירה true; לאחר מכן שגיאה → מערך' });

function server() {
  return new Promise(res => {
    const s = http.createServer((req, r) => {
      r.writeHead(200, { 'Content-Type': 'application/json; charset=utf-8' });
      const buf = Buffer.from(PAYLOAD, 'utf8');
      let i = 0;
      // בית אחד בכל חתיכה: מבטיח שכל אות עברית נחתכת באמצע
      (function next() { if (i >= buf.length) return r.end(); r.write(buf.subarray(i, i + 1)); i++; setImmediate(next); })();
    }).listen(0, () => res(s));
  });
}
function read(port, mode) {
  return new Promise(res => {
    http.get({ port }, s => {
      if (mode === 'fixed') s.setEncoding('utf8');
      let d = ''; s.on('data', c => d += c); s.on('end', () => res(d));
    });
  });
}

(async () => {
  const s = await server(); const port = s.address().port;
  const old = await read(port, 'old');
  const fixed = await read(port, 'fixed');
  t('הדפוס הישן באמת שובר עברית (הבדיקה רגישה)', old.includes('�'));
  t('setEncoding("utf8") מחזיר את הטקסט שלם', fixed === PAYLOAD);
  s.close();

  // --- סריקת הריפו ---
  const dirs = ['scripts', 'firebase-setup', 'worker'];
  const offenders = [];
  for (const d of dirs) {
    const dir = path.join(__dirname, '..', d);
    if (!fs.existsSync(dir)) continue;
    for (const f of fs.readdirSync(dir)) {
      if (!/\.(js|mjs)$/.test(f) || f === 'test-utf8-safety.js') continue;
      const lines = fs.readFileSync(path.join(dir, f), 'utf8').split('\n');
      lines.forEach((line, i) => {
        if (!/\.on\('data',\s*\(?\w+\)?\s*=>\s*\w+\s*\+=\s*\w+\)/.test(line)) return;
        const ctx = lines.slice(Math.max(0, i - 3), i + 1).join('\n');
        if (!/setEncoding/.test(ctx)) offenders.push(d + '/' + f + ':' + (i + 1));
      });
    }
  }
  t('אין קורא HTTP לא מוגן בסקריפטים', offenders.length === 0, offenders.join(', '));

  console.log('\n===== ' + pass + '/' + (pass + fail) + ' =====');
  process.exit(fail ? 1 : 0);
})();
