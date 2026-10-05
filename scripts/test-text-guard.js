/**
 * הגנת התקינות באפליקציה: hasBrokenText / rejectBrokenText.
 *
 * מחלץ את הקוד כמות שהוא מ-app.html ומריץ אותו, ובודק גם סטטית שכל פעולת
 * שמירה שמקבלת טקסט מהמורה אכן קוראת לו — פעולה חדשה ששוכחת את ההגנה
 * תיתפס כאן.
 *
 * הרצה: node scripts/test-text-guard.js
 */
const fs = require('fs'), path = require('path'), vm = require('vm');
const APP = fs.readFileSync(path.join(__dirname, '..', 'app.html'), 'utf8');

let pass = 0, fail = 0;
const t = (n, c, d) => { c ? pass++ : fail++; console.log((c ? 'PASS ' : 'FAIL ') + n + (c ? '' : ' — ' + d)); };

const a = APP.indexOf('const BROKEN_CHAR');
const b = APP.indexOf('const DataService = {');
if (a < 0 || b < 0 || b < a) { console.log('לא נמצאה הגנת התקינות ב-app.html'); process.exit(1); }

const alerts = [];
const ctx = { console: { error() {} }, alert: m => alerts.push(m) };
vm.createContext(ctx);
vm.runInContext(APP.slice(a, b) + '\nthis.hasBrokenText = hasBrokenText; this.rejectBrokenText = rejectBrokenText;', ctx);

const BAD = String.fromCharCode(0xFFFD);
t('מזהה מחרוזת פגומה', ctx.hasBrokenText('abc' + BAD + BAD + 'def'));
t('לא חוסם עברית תקינה', !ctx.hasBrokenText('כתבי פעולה חיצונית — שגיאה → מערך'));
t('מזהה בתוך אובייקט מקונן', ctx.hasBrokenText({ a: { b: ['ok', { c: 'x' + BAD }] } }));
t('מזהה בתוך מערך מבחנים (כמו classes)', ctx.hasBrokenText([{ exams: [{ title: 'מערך' + BAD }] }]));
t('לא נופל על null/מספרים/undefined', !ctx.hasBrokenText({ a: null, b: 5, c: undefined, d: true }));
t('rejectBrokenText חוסם ומודיע למורה', ctx.rejectBrokenText({ instructions: 'x' + BAD }, 'q') === true && alerts.length === 1);
t('rejectBrokenText מעביר טקסט תקין', ctx.rejectBrokenText({ instructions: 'שלום' }, 'q') === false);

// כל פעולת שמירה מטקסט של מורה חייבת לקרוא להגנה
for (const fn of ['saveClass', 'saveClassesBatch', 'saveQuestionContent']) {
  const i = APP.indexOf('async ' + fn + '(');
  const body = i < 0 ? '' : APP.slice(i, i + 700);
  t(fn + ' קוראת ל-rejectBrokenText', /rejectBrokenText\(/.test(body));
}
t('אין תו פגום ממשי בקוד המקור', !APP.includes(BAD));

console.log('\n===== ' + pass + '/' + (pass + fail) + ' =====');
process.exit(fail ? 1 : 0);
