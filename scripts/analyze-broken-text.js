/**
 * ניתוח (קריאה בלבד): טקסט עברי שבור (U+FFFD) בשאלות, והאם אפשר לשחזר אותו
 * מהעותק הישן באוספי השורש. רצף תווים שבורים מייצג אות עברית אחת או שתיים.
 *
 * הרצה: node scripts/analyze-broken-text.js [school]
 */
const https = require('https'), fs = require('fs'), os = require('os'), path = require('path');
const P = 'exams-a93fb', DOCS = '/v1/projects/' + P + '/databases/(default)/documents';

function oauth() {
  return new Promise(r => {
    const c = JSON.parse(fs.readFileSync(path.join(os.homedir(), '.config', 'configstore', 'firebase-tools.json'), 'utf8'));
    const b = 'grant_type=refresh_token&refresh_token=' + encodeURIComponent(c.tokens.refresh_token) +
      '&client_id=563584335869-fgrhgmd47bqnekij5i8b5pr03ho849e6.apps.googleusercontent.com&client_secret=j9iVZfS8kkCEFUPaAeJV0sAi';
    const q = https.request({ hostname: 'oauth2.googleapis.com', path: '/token', method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' } }, s => {
      s.setEncoding('utf8'); // מפענח UTF-8 נכון גם כשאות נחתכת בין חתיכות
      let d = ''; s.on('data', x => d += x); s.on('end', () => r(JSON.parse(d).access_token));
    });
    q.write(b); q.end();
  });
}
function get(p, t) {
  return new Promise(r => {
    const q = https.request({ hostname: 'firestore.googleapis.com', path: p, method: 'GET', headers: { Authorization: 'Bearer ' + t } }, s => {
      // מחברים Buffers ורק אז מפענחים. חיבור מחרוזות לכל חתיכה מפענח כל חתיכה לבד,
      // ואות עברית (2 בתים) שנחתכת בין שתי חתיכות הופכת ל-�� — פגם שנוצר
      // בקריאה ולא קיים בנתונים, והציג ספירות שבורות מנופחות.
      const chunks = []; s.on('data', c => chunks.push(c));
      s.on('end', () => r(JSON.parse(Buffer.concat(chunks).toString('utf8') || '{}')));
    });
    q.end();
  });
}
async function all(col, t) {
  let out = [], tk = '';
  do {
    const q = await get(DOCS + '/' + col + '?pageSize=300' + (tk ? '&pageToken=' + tk : ''), t);
    out = out.concat(q.documents || []);
    tk = q.nextPageToken || '';
  } while (tk);
  return out;
}

// מחרוזת שבורה מול מחרוזת תקינה: כל רצף FFFD מתאים לאות עברית אחת או שתיים,
// וכל השאר חייב להיות זהה תו-בתו. אם הטקסט נערך מאז — לא תהיה התאמה.
function matches(broken, clean) {
  const esc = broken.split(/�+/).map(x => x.replace(/[.*+?^${}()|[\]\\]/g, '\\$&'));
  return new RegExp('^' + esc.join('[^\\x00-\\x7F]{1,3}') + '$').test(clean);
}
module.exports = { oauth, get, all, matches, DOCS };

if (require.main === module) (async () => {
  const school = process.argv[2] || 'lamerhav';
  const t = await oauth();
  const root = Object.fromEntries((await all('questions', t)).map(d => [d.name.split('/').pop(), d]));
  const mine = await all('schools/' + school + '/questions', t);
  let ok = 0, rootBroken = 0, mismatch = 0, noRoot = 0, fields = 0, cleanAlready = 0;
  const unrecoverable = [];
  for (const d of mine) {
    const id = d.name.split('/').pop(); const f = d.fields || {};
    const brokenKeys = Object.keys(f).filter(k => f[k].stringValue && f[k].stringValue.includes('�'));
    if (!brokenKeys.length) { cleanAlready++; continue; }
    const r = root[id];
    if (!r) { noRoot++; unrecoverable.push(id + ' (אין בשורש)'); continue; }
    for (const k of brokenKeys) {
      fields++;
      const rv = ((r.fields || {})[k] || {}).stringValue;
      if (rv === undefined) { mismatch++; unrecoverable.push(id + '.' + k + ' (שדה חסר בשורש)'); }
      else if (rv.includes('�')) { rootBroken++; unrecoverable.push(id + '.' + k + ' (שבור גם בשורש)'); }
      else if (!matches(f[k].stringValue, rv)) { mismatch++; unrecoverable.push(id + '.' + k + ' (נערך מאז)'); }
      else ok++;
    }
  }
  console.log(school + ': ' + mine.length + ' שאלות · תקינות: ' + cleanAlready + ' · שדות שבורים: ' + fields);
  console.log('   ניתנים לשחזור בטוח מהשורש: ' + ok);
  console.log('   שבורים גם בשורש: ' + rootBroken + ' · לא תואמים/נערכו: ' + mismatch + ' · אין בשורש: ' + noRoot);
  unrecoverable.slice(0, 12).forEach(x => console.log('   ✗ ' + x));
})();
