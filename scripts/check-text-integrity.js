/**
 * סריקת תקינות טקסט: כל מסמך בכל בית ספר, מחפשת U+FFFD.
 * יוצאת עם קוד 1 אם נמצא פגם — מתאימה להרצה לפני פריסה.
 *
 * הרצה: node scripts/check-text-integrity.js
 */
const { oauth, all } = require('./analyze-broken-text');
const COLS = ['questions', 'classes', 'exam_settings', 'media', 'practice', 'notifications', 'users', 'grading_rubrics'];
(async () => {
  const t = await oauth();
  const schools = (await all('schools', t)).map(d => d.name.split('/').pop());
  let bad = 0, docs = 0;
  for (const sch of schools) for (const col of COLS) {
    let list = []; try { list = await all('schools/' + sch + '/' + col, t); } catch (e) {}
    docs += list.length;
    for (const d of list) if (JSON.stringify(d.fields || {}).includes('\uFFFD')) { bad++; console.log('פגום: ' + sch + '/' + col + '/' + d.name.split('/').pop()); }
  }
  console.log('נסרקו ' + docs + ' מסמכים ב-' + schools.length + ' בתי ספר · פגומים: ' + bad);
  process.exit(bad ? 1 : 0);
})();
