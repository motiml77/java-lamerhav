/**
 * העברת תלמידה בודדת לשכבה אחרת — בדיוק כמו undoPromotionRun / applyPromotion באפליקציה:
 *  - grade מתעדכן;
 *  - gradeHistory הוא append-only: נוספת רשומה (undo: true כשזה ביטול העלאה של השנה);
 *  - בביטול העלאה נמחקים promotionYear / promotedFrom / promotedAt, כדי שהרצת העלאה עתידית
 *    לא תדלג עליה בטעות ("כבר טופלה השנה");
 *  - שום תשובה/מבחן לא זז — הם שמורים לפי המייל, לא לפי השכבה.
 *
 * הרצה: node scripts/move-student-grade.js <school> <safeEmailId> <toGrade> [--apply]
 */
const https = require('https'), fs = require('fs'), path = require('path');
const { oauth, get } = require('./analyze-broken-text');

const [school, id, toGrade] = process.argv.slice(2);
const APPLY = process.argv.includes('--apply');
if (!school || !id || !toGrade) { console.log('שימוש: <school> <safeEmailId> <toGrade> [--apply]'); process.exit(1); }
const DOC = 'projects/exams-a93fb/databases/(default)/documents/schools/' + school + '/users/' + id;
const BACKUP_DIR = process.env.BACKUP_DIR || path.join(__dirname, '..', '.backups');

function patch(fields, mask, tok) {
  const body = JSON.stringify({ fields });
  const qs = mask.map(f => 'updateMask.fieldPaths=' + encodeURIComponent(f)).join('&');
  return new Promise(r => {
    const q = https.request({ hostname: 'firestore.googleapis.com', method: 'PATCH', path: '/v1/' + DOC + '?' + qs,
      headers: { Authorization: 'Bearer ' + tok, 'Content-Type': 'application/json; charset=utf-8', 'Content-Length': Buffer.byteLength(body) } }, s => {
      s.setEncoding('utf8'); let x = ''; s.on('data', c => x += c); s.on('end', () => r({ code: s.statusCode, body: x }));
    });
    q.write(body); q.end();
  });
}
const S = v => ({ stringValue: v });

(async () => {
  const tok = await oauth();
  const doc = await get('/v1/' + DOC, tok);
  if (!doc.fields) { console.log('לא נמצאה תלמידה: ' + id); process.exit(1); }
  const f = doc.fields;
  const from = (f.grade || {}).stringValue;
  const history = ((f.gradeHistory || {}).arrayValue || {}).values || [];
  const year = (f.promotionYear || {}).stringValue;
  const promotedFrom = (f.promotedFrom || {}).stringValue;
  console.log('תלמידה: ' + ((f.name || {}).stringValue || id) + ' · שכבה נוכחית: ' + from + ' → ' + toGrade);
  if (from === toGrade) { console.log('כבר בשכבה הזו — אין מה לעשות'); return; }

  // אם זו בדיוק החזרה של העלאה מהשנה הנוכחית — רשומת undo, כמו כפתור הביטול באפליקציה
  const isUndo = !!year && promotedFrom === toGrade;
  const at = new Date().toISOString();
  const entry = { mapValue: { fields: {
    from: S(from), to: S(toGrade), at: S(at), year: S(year || ''), ...(isUndo ? { undo: { booleanValue: true } } : { manual: { booleanValue: true } }),
    classNum: S((f.classNum || {}).stringValue || '') } } };

  const fields = { grade: S(toGrade), gradeHistory: { arrayValue: { values: history.concat([entry]) } } };
  const mask = ['grade', 'gradeHistory'];
  if (isUndo) mask.push('promotionYear', 'promotedFrom', 'promotedAt'); // במסכה ולא בגוף = נמחקים
  console.log('סוג: ' + (isUndo ? 'ביטול העלאת השנה (undo)' : 'העברה ידנית') + ' · שדות: ' + mask.join(', '));
  if (!APPLY) { console.log('(הרצה יבשה)'); return; }

  fs.mkdirSync(BACKUP_DIR, { recursive: true });
  const bk = path.join(BACKUP_DIR, 'student-' + school + '-' + id + '-' + Date.now() + '.json');
  fs.writeFileSync(bk, JSON.stringify(doc, null, 1), 'utf8');
  console.log('גיבוי: ' + bk);

  const w = await patch(fields, mask, tok);
  if (w.code !== 200) { console.log('כשל ' + w.code + ': ' + w.body.slice(0, 200)); process.exit(1); }
  const back = (await get('/v1/' + DOC, tok)).fields;
  const ok = back.grade.stringValue === toGrade && (!isUndo || (!back.promotionYear && !back.promotedFrom && !back.promotedAt));
  console.log(ok ? 'בוצע ואומת: שכבה ' + back.grade.stringValue + ', רשומות היסטוריה: ' + back.gradeHistory.arrayValue.values.length : 'האימות נכשל');
  process.exit(ok ? 0 : 1);
})();
