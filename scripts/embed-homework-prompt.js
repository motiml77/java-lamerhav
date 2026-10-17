/**
 * מטמיע בלוק הנחיות (כיול ציונים + הסבר למתחילות) ב-BASE_SYSTEM_PROMPT שב-app.html,
 * לפני "# OUTPUT". מסרב להריץ פעמיים (מזהה לפי כותרת הבלוק).
 *
 * הרצה: node scripts/embed-homework-prompt.js <insert.txt>
 */
const fs = require('fs'), path = require('path');
const file = path.join(__dirname, '..', 'app.html');
let s = fs.readFileSync(file, 'utf8');
const crlf = s.includes('\r\n');

let ins = fs.readFileSync(process.argv[2], 'utf8').replace(/\r\n/g, '\n');
const fem = '- Address her in the FEMININE form in every verb and command: שני, תקני, הוסיפי, בדקי, שימי לב — never the masculine שנה, תקן, הוסף, בדוק.\n';
if (!ins.includes('FEMININE')) ins = ins.replace('- Stay short and specific:', fem + '- Stay short and specific:');

const marker = '# OUTPUT — STRUCTURED & CONCISE';
if (s.split(marker).length !== 2) { console.log('נקודת ההכנסה לא נמצאה או כפולה'); process.exit(1); }
if (s.includes('# SCORING CALIBRATION')) { console.log('כבר מוטמע — לא נוגע'); process.exit(0); }
// הנוסח נכנס בתוך template literal: גרש הפוך או ${ ישברו אותו
if (ins.includes('`') || ins.includes('${')) { console.log('הבלוק מכיל ` או ${ — אסור בתוך template literal'); process.exit(1); }

s = s.replace(marker, (crlf ? ins.replace(/\n/g, '\r\n') : ins) + marker);
fs.writeFileSync(file, s, 'utf8');
console.log('הוטמע. אורך הבלוק: ' + ins.length);
