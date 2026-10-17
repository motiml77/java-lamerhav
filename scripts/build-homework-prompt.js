/**
 * בונה נוסח משופר של הנחיית בדיקת התרגילים (כיול ציונים + הסבר למתחילות) מתוך
 * הנוסח הנוכחי ב-app.html, ומדפיס אותו לקובץ — להשוואה מול הנוכחי בלבד.
 *
 * הרצה: node scripts/build-homework-prompt.js <insert.txt> <out.txt>
 */
const fs = require('fs'), path = require('path'), vm = require('vm');
const APP = fs.readFileSync(path.join(__dirname, '..', 'app.html'), 'utf8');

function grab(name) {
  const key = 'const ' + name + ' = `';
  const s = APP.indexOf(key) + key.length;
  let e = s; while (!(APP[e] === '`' && APP[e - 1] !== '\\')) e++;
  return vm.runInNewContext('`' + APP.slice(s, e) + '`');
}
const old = grab('BASE_SYSTEM_PROMPT') + grab('DEFAULT_HOMEWORK_PROMPT');
const insert = fs.readFileSync(process.argv[2], 'utf8');
const marker = '# OUTPUT — STRUCTURED & CONCISE';
if (old.split(marker).length !== 2) { console.log('נקודת ההכנסה לא נמצאה'); process.exit(1); }
fs.writeFileSync(process.argv[3], old.replace(marker, insert + marker), 'utf8');
console.log('נוצר · אורך נוכחי ' + old.length + ' → חדש ' + (old.length + insert.length));
