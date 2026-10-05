/**
 * שחזור תווים שבורים בתוך מסמכי נושאים (classes) — כותרות מבחנים במערך exams.
 *
 * שונה מ-restore-broken-text.js: כאן הטקסט שבור בתוך מבנה מקונן. עוברים על
 * שני העצים במקביל, ומחליפים רק מחרוזת שבורה שהמקבילה לה בשורש תואמת תו-בתו
 * (חוץ מהתווים החסרים). שאר המערך — נראות, סדר, עריכות מאז — נשאר כפי שהוא.
 *
 * הרצה: node scripts/restore-broken-classes.js <school> [--apply]
 */
const https = require('https'), fs = require('fs'), path = require('path');
const { oauth, get, all, matches } = require('./analyze-broken-text');

const school = process.argv[2], APPLY = process.argv.includes('--apply');
if (!school) { console.log('חסר שם בית ספר'); process.exit(1); }
const BACKUP_DIR = process.env.BACKUP_DIR || path.join(__dirname, '..', '.backups');
fs.mkdirSync(BACKUP_DIR, { recursive: true });

// מחליף במקום ומחזיר כמה מחרוזות תוקנו; כל מבנה שאינו זהה בצורתו נשאר בלי שינוי
function heal(cur, ref, log, p) {
  if (cur && typeof cur === 'object' && ref && typeof ref === 'object') {
    if (typeof cur.stringValue === 'string' && typeof ref.stringValue === 'string') {
      if (cur.stringValue.includes('�') && !ref.stringValue.includes('�') && matches(cur.stringValue, ref.stringValue)) {
        log.push({ path: p, old: cur.stringValue, next: ref.stringValue });
        cur.stringValue = ref.stringValue;
      }
      return;
    }
    if (Array.isArray(cur)) { if (cur.length === ref.length) cur.forEach((x, i) => heal(x, ref[i], log, p + '[' + i + ']')); return; }
    for (const k of Object.keys(cur)) if (k in ref) heal(cur[k], ref[k], log, p + '.' + k);
  }
}

function patch(docPath, field, valueObj, tok) {
  const body = JSON.stringify({ fields: { [field]: valueObj } });
  return new Promise(r => {
    const q = https.request({ hostname: 'firestore.googleapis.com', method: 'PATCH',
      path: '/v1/' + docPath + '?updateMask.fieldPaths=' + encodeURIComponent(field),
      headers: { Authorization: 'Bearer ' + tok, 'Content-Type': 'application/json; charset=utf-8', 'Content-Length': Buffer.byteLength(body) } }, s => {
      const ch = []; s.on('data', c => ch.push(c)); s.on('end', () => r({ code: s.statusCode, body: Buffer.concat(ch).toString('utf8') }));
    });
    q.write(body); q.end();
  });
}

(async () => {
  const tok = await oauth();
  const root = Object.fromEntries((await all('classes', tok)).map(d => [d.name.split('/').pop(), d]));
  const mine = await all('schools/' + school + '/classes', tok);
  const backup = []; let fixed = 0, bad = 0;
  const bkFile = path.join(BACKUP_DIR, 'broken-classes-' + school + '-' + Date.now() + '.json');
  for (const d of mine) {
    const id = d.name.split('/').pop(), r = root[id];
    if (!r) continue;
    for (const field of Object.keys(d.fields || {})) {
      if (!JSON.stringify(d.fields[field]).includes('�')) continue;
      const before = JSON.parse(JSON.stringify(d.fields[field]));
      const next = JSON.parse(JSON.stringify(d.fields[field]));
      const log = [];
      heal(next, (r.fields || {})[field], log, field);
      if (!log.length) continue;
      console.log(id + '.' + field + ': ' + log.length + ' מחרוזות');
      log.forEach(l => console.log('   ' + JSON.stringify(l.old.slice(-22)) + ' → ' + JSON.stringify(l.next.slice(-22))));
      backup.push({ docPath: d.name, field, before, log });
      if (!APPLY) continue;
      // הגיבוי נכתב לפני כל כתיבה, לא בסוף — כשל באמצע לא משאיר שינוי בלי גיבוי
      fs.writeFileSync(bkFile, JSON.stringify(backup, null, 1), 'utf8');
      const w = await patch(d.name, field, next, tok);
      const back = (await get('/v1/' + d.name, tok)).fields[field];
      if (w.code === 200 && JSON.stringify(back) === JSON.stringify(next)) fixed += log.length;
      else { bad++; console.log('   כשל ' + w.code); }
    }
  }
  if (APPLY && backup.length) console.log('גיבוי: ' + bkFile);
  console.log(school + ': ' + (APPLY ? 'שוחזרו ' + fixed + ' · כשלים ' + bad : 'הרצה יבשה'));
  process.exit(bad ? 1 : 0);
})();
