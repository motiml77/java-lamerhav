/**
 * שחזור טקסט עברי שבור (U+FFFD) בשאלות מהעותק הישן באוספי השורש.
 *
 * בטיחות:
 *  - משחזר שדה רק אם הטקסט הקיים תואם את המקור תו-בתו, חוץ מהאותיות החסרות.
 *    שדה שנערך מאז לא ייגע.
 *  - כל שדה מגובה לקובץ JSON לפני הכתיבה (BACKUP_DIR), עם הערך הישן המלא.
 *  - הכתיבה מעדכנת שדה בודד (updateMask), לא את כל המסמך.
 *  - אחרי הכתיבה קוראים בחזרה ומוודאים שהערך הוא בדיוק מה שנכתב.
 *
 * הרצה: node scripts/restore-broken-text.js <school> [--apply]
 * בלי --apply זו הרצה יבשה.
 */
const https = require('https'), fs = require('fs'), path = require('path');
const { oauth, get, all, matches, DOCS } = require('./analyze-broken-text');

const school = process.argv[2];
const APPLY = process.argv.includes('--apply');
if (!school) { console.log('חסר שם בית ספר'); process.exit(1); }
const BACKUP_DIR = process.env.BACKUP_DIR || path.join(__dirname, '..', '.backups');
fs.mkdirSync(BACKUP_DIR, { recursive: true });

function patch(docPath, field, value, tok) {
  const body = JSON.stringify({ fields: { [field]: { stringValue: value } } });
  return new Promise(r => {
    const q = https.request({ hostname: 'firestore.googleapis.com', method: 'PATCH',
      path: '/v1/' + docPath + '?updateMask.fieldPaths=' + encodeURIComponent(field),
      headers: { Authorization: 'Bearer ' + tok, 'Content-Type': 'application/json; charset=utf-8', 'Content-Length': Buffer.byteLength(body) } }, s => {
      s.setEncoding('utf8'); // מפענח UTF-8 נכון גם כשאות נחתכת בין חתיכות
      let x = ''; s.on('data', c => x += c); s.on('end', () => r({ code: s.statusCode, body: x }));
    });
    q.write(body); q.end();
  });
}

(async () => {
  const tok = await oauth();
  const root = Object.fromEntries((await all('questions', tok)).map(d => [d.name.split('/').pop(), d]));
  const mine = await all('schools/' + school + '/questions', tok);
  const plan = [];
  for (const d of mine) {
    const id = d.name.split('/').pop(), f = d.fields || {}, r = root[id];
    if (!r) continue;
    for (const k of Object.keys(f)) {
      const cur = f[k].stringValue;
      if (!cur || !cur.includes('�')) continue;
      const orig = ((r.fields || {})[k] || {}).stringValue;
      if (orig && !orig.includes('�') && matches(cur, orig)) plan.push({ docPath: d.name, id, field: k, old: cur, next: orig });
    }
  }
  console.log(school + ': ' + plan.length + ' שדות לשחזור' + (APPLY ? '' : ' (הרצה יבשה)'));
  if (!APPLY) { plan.slice(0, 3).forEach(p => console.log('   ' + p.id + '.' + p.field)); return; }

  const bk = path.join(BACKUP_DIR, 'broken-text-' + school + '-' + new Date().toISOString().replace(/[:.]/g, '-') + '.json');
  fs.writeFileSync(bk, JSON.stringify(plan, null, 1), 'utf8');
  console.log('גיבוי נכתב: ' + bk);

  let ok = 0, bad = 0;
  for (const p of plan) {
    const w = await patch(p.docPath, p.field, p.next, tok);
    if (w.code !== 200) { bad++; console.log('   כשל כתיבה ' + p.id + '.' + p.field + ' ' + w.code); continue; }
    const back = await get('/v1/' + p.docPath, tok);
    const val = ((back.fields || {})[p.field] || {}).stringValue;
    if (val === p.next) ok++; else { bad++; console.log('   אימות נכשל ' + p.id + '.' + p.field); }
  }
  console.log('שוחזרו ואומתו: ' + ok + ' · כשלים: ' + bad);
  process.exit(bad ? 1 : 0);
})();
