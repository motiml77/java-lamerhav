/**
 * השוואת הנחיית בדיקת התרגילים: הנוסח הנוכחי ב-app.html מול נוסח משופר,
 * על אותם פתרונות של תלמידות, דרך הוורקר החי.
 *
 * הערה: מכסת המודלים החזקים בחינם מוגבלת (~20 בקשות ביום לכל אחד), ולכן
 * ההשוואה רצה על מה שהוורקר נתן באותו רגע — modelUsed מודפס לכל בקשה.
 * השוואה הוגנת רק בין שני נוסחים שנענו על ידי אותו מודל.
 *
 * הרצה: node scripts/ab-homework-prompt.js
 */
const fs = require('fs'), path = require('path'), vm = require('vm');
const APP = fs.readFileSync(path.join(__dirname, '..', 'app.html'), 'utf8');
const W = 'https://gemini-proxy.motiml77.workers.dev';

function grab(name) {
  const i = APP.indexOf('const ' + name + ' = `');
  const s = i + ('const ' + name + ' = `').length;
  let e = s; while (!(APP[e] === '`' && APP[e - 1] !== '\\')) e++;
  return vm.runInNewContext('`' + APP.slice(s, e) + '`');
}
const BASE = grab('BASE_SYSTEM_PROMPT'), TASK = grab('DEFAULT_HOMEWORK_PROMPT');
const OLD = BASE + TASK;
// הנוסח המשופר נקרא מהקובץ אם כבר הוטמע, אחרת מוזרק כאן להשוואה
const NEW = process.env.NEW_PROMPT_FILE ? fs.readFileSync(process.env.NEW_PROMPT_FILE, 'utf8') : OLD;

const Q = 'כתבי תוכנית שמחשבת ומדפיסה את סכום המספרים מ-1 עד n. עבור n = 10 הפלט צריך להיות 55.';
const CASES = [
  { name: 'נכון', pts: 40, q: Q, code: 'int n = 10;\nint sum = 0;\nfor (int i = 1; i <= n; i++) {\n    sum += i;\n}\nSystem.out.println(sum);' },
  { name: 'טעות <', pts: 40, q: Q, code: 'int n = 10;\nint sum = 0;\nfor (int i = 1; i < n; i++) {\n    sum += i;\n}\nSystem.out.println(sum);' },
  { name: 'השוואת מחרוזות', pts: 20, q: 'קלטי מחרוזת מהמשתמש. אם היא שווה ל-"yes" הדפיסי "אושר", אחרת הדפיסי "נדחה".',
    code: 'String s = in.next();\nif (s == "yes") {\n    System.out.println("אושר");\n} else {\n    System.out.println("נדחה");\n}' },
  { name: 'גישה שגויה', pts: 30, q: 'כתבי פעולה המקבלת מערך מספרים שלמים ומחזירה את הערך הגדול ביותר בו.',
    code: 'public static int max(int[] a) {\n    int m = 0;\n    for (int i = 0; i < a.length; i++) {\n        m = a[i];\n    }\n    return m;\n}' },
];

async function check(prompt, c) {
  const r = await fetch(W, { method: 'POST', headers: { 'Content-Type': 'application/json', Origin: 'https://java-bagrut.vercel.app' },
    body: JSON.stringify({ school: 'lamerhav', action: 'checkHomework', code: c.code, questionText: c.q, homeworkPrompt: prompt, questionPoints: c.pts }) });
  return r.json();
}

(async () => {
  if (OLD === NEW) console.log('(אין נוסח חדש — מריץ את הנוכחי פעמיים)');
  for (const c of CASES) {
    for (const [label, prompt] of [['נוכחי', OLD], ['חדש  ', NEW]]) {
      const j = await check(prompt, c);
      const words = s => String(s || '').split(/\s+/).filter(Boolean).length;
      const errs = (j.errors || []).map(e => typeof e === 'string' ? e : JSON.stringify(e));
      console.log('\n[' + c.name + ' | ' + label + '] ציון ' + (j.score ?? j.aiScore) + '/' + (j.maxPoints ?? c.pts) + ' · ' + (j.modelUsed || j.error) +
        ' · מילים בשאלה: משוב ' + words(j.feedback) + ', שגיאות ' + errs.reduce((a, e) => a + words(e), 0));
      console.log('   משוב: ' + j.feedback);
      errs.forEach(e => console.log('   • ' + e));
    }
  }
})();
