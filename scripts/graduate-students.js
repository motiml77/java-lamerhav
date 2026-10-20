/**
 * העברת תלמידות לארכיון (סיום לימודים) — בדיוק כמו applyPromotion במצב 'graduate':
 *  - השכבה לא משתנה (נשארת יב), כדי שהציונים ימשיכו להיקרא תחת השכבה שבה נצברו;
 *  - graduated: true + graduatedAt;
 *  - promotionYear / promotedFrom / promotedAt — כדי שכפתור "ביטול הפעולה" באשף יוכל לבטל;
 *  - gradeHistory מקבל רשומה (append-only) עם to: 'בוגרת'.
 * שום תשובה, מבחן או ציון לא נמחק.
 *
 * הרצה: node scripts/graduate-students.js <school> <id,id,...> [--apply]
 */
const https = require('https'), fs = require('fs'), path = require('path');
const { oauth, get } = require('./analyze-broken-text');

const [school, idsArg] = process.argv.slice(2);
const APPLY = process.argv.includes('--apply');
if (!school || !idsArg) { console.log('שימוש: <school> <id,id,...> [--apply]'); process.exit(1); }
const ids = idsArg.split(',').map(x => x.trim()).filter(Boolean);
const BACKUP_DIR = process.env.BACKUP_DIR || path.join(__dirname, '..', '.backups');
const GRADUATE_STEP = 'בוגרת'; // זהה ל-GRADUATE_STEP ב-app.html
const now = new Date();
const start = now.getMonth() >= 8 ? now.getFullYear() : now.getFullYear() - 1; // זהה ל-schoolYearKey
const YEAR = start + '/' + String((start + 1) % 100).padStart(2, '0');
const S = v => ({ stringValue: v });

function patch(doc, fields, mask, tok) {
  const body = JSON.stringify({ fields });
  const qs = mask.map(f => 'updateMask.fieldPaths=' + encodeURIComponent(f)).join('&');
  return new Promise(r => {
    const q = https.request({ hostname: 'firestore.googleapis.com', method: 'PATCH', path: '/v1/' + doc + '?' + qs,
      headers: { Authorization: 'Bearer ' + tok, 'Content-Type': 'application/json; charset=utf-8', 'Content-Length': Buffer.byteLength(body) } }, s => {
      s.setEncoding('utf8'); let x = ''; s.on('data', c => x += c); s.on('end', () => r({ code: s.statusCode, body: x }));
    });
    q.write(body); q.end();
  });
}

(async () => {
  const tok = await oauth();
  const at = new Date().toISOString();
  const backup = []; let ok = 0, bad = 0;
  fs.mkdirSync(BACKUP_DIR, { recursive: true });
  const bkFile = path.join(BACKUP_DIR, 'graduate-' + school + '-' + Date.now() + '.json');
  for (const id of ids) {
    const doc = 'projects/exams-a93fb/databases/(default)/documents/schools/' + school + '/users/' + id;
    const cur = await get('/v1/' + doc, tok);
    if (!cur.fields) { console.log('✗ לא נמצאה: ' + id); bad++; continue; }
    const f = cur.fields;
    const name = (f.name || {}).stringValue || id, grade = (f.grade || {}).stringValue || '';
    if ((f.graduated || {}).booleanValue) { console.log('· כבר בארכיון: ' + name); continue; }
    const history = ((f.gradeHistory || {}).arrayValue || {}).values || [];
    const entry = { mapValue: { fields: { from: S(grade), to: S(GRADUATE_STEP), at: S(at), year: S(YEAR), classNum: S((f.classNum || {}).stringValue || '') } } };
    const fields = {
      graduated: { booleanValue: true }, graduatedAt: S(at),
      promotionYear: S(YEAR), promotedFrom: S(grade), promotedAt: S(at),
      gradeHistory: { arrayValue: { values: history.concat([entry]) } },
    };
    const mask = Object.keys(fields);
    console.log((APPLY ? '→ ' : '(יבש) ') + name + ' · שכבה ' + grade + ' → ארכיון (' + YEAR + ')');
    if (!APPLY) continue;
    backup.push(cur);
    fs.writeFileSync(bkFile, JSON.stringify(backup, null, 1), 'utf8'); // גיבוי לפני כל כתיבה
    const w = await patch(doc, fields, mask, tok);
    const back = (await get('/v1/' + doc, tok)).fields || {};
    if (w.code === 200 && (back.graduated || {}).booleanValue === true && (back.grade || {}).stringValue === grade) ok++;
    else { bad++; console.log('   ✗ כשל ' + w.code); }
  }
  if (APPLY) console.log('הועברו לארכיון ואומתו: ' + ok + ' · כשלים: ' + bad + (backup.length ? '\nגיבוי: ' + bkFile : ''));
  process.exit(bad ? 1 : 0);
})();
