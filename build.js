/**
 * שלב בנייה: הידור מראש של ה-JSX במקום בדפדפן.
 * ==============================================
 * הבעיה שזה פותר: app.html מכיל תג <script type="text/babel"> אחד בגודל ~1MB,
 * ו-@babel/standalone תרגם אותו **בכל טעינה, אצל כל תלמידה**. נמדד בפועל על
 * האתר החי: כ-20 שניות עד שמסך הכניסה מופיע, כשמגיעים מעמוד הנחיתה (האנימציה
 * שם עוד תופסת את המעבד בזמן שהתרגום רץ).
 *
 * מה זה עושה: מחלץ את הסקריפט, מהדר אותו פעם אחת ל-app.bundle.js, וכותב
 * app.html שטוען אותו כקובץ רגיל — בלי babel/standalone בכלל.
 *
 * רץ אצל Vercel בכל פריסה (buildCommand ב-vercel.json), על עותק טרי של הריפו.
 * לכן app.html שבגיט **לא משתנה** — ואי אפשר שהפלט המהודר יסטה מהמקור.
 * זו הסיבה שזה build ולא קובץ מהודר שמקומט לריפו.
 *
 * הרצה מקומית לבדיקה:  node build.js --out dist
 */
const fs = require('fs');
const path = require('path');
const babel = require('@babel/core');

const ROOT = __dirname;
const argOut = process.argv.indexOf('--out');
const OUT = argOut > -1 ? path.resolve(ROOT, process.argv[argOut + 1]) : path.join(ROOT, 'dist');

// רשימת החרגה ולא רשימת הכללה — בכוונה. עם רשימת הכללה, קובץ סטטי חדש
// שמישהו יוסיף בעתיד פשוט לא היה מגיע לאתר, בשקט. כאן ברירת המחדל היא
// שהכול נפרס, ומוחרג רק מה שבוודאות לא שייך לאתר.
const EXCLUDE = new Set([
  'node_modules', '.git', '.vercel', '.wrangler', 'dist',
  'scripts', 'docs', 'worker', 'firebase-setup',
  'build.js', 'package.json', 'package-lock.json',
  'firestore.rules', 'storage.rules', 'firebase.json', '.firebaserc',
  '.gitignore', '.gitattributes', 'README.md', '.thumbnail',
]);

function copyStatic(srcDir, dstDir) {
  fs.mkdirSync(dstDir, { recursive: true });
  for (const name of fs.readdirSync(srcDir)) {
    if (srcDir === ROOT && EXCLUDE.has(name)) continue;
    const s = path.join(srcDir, name), d = path.join(dstDir, name);
    const st = fs.statSync(s);
    if (st.isDirectory()) copyStatic(s, d);
    else fs.copyFileSync(s, d);
  }
}

const SRC = path.join(ROOT, 'app.html');
let html = fs.readFileSync(SRC, 'utf8');

// ---- 1. איתור תג ה-Babel היחיד ----
const OPEN = '<script type="text/babel">';
const start = html.indexOf(OPEN);
if (start === -1) {
  console.error('build: לא נמצא <script type="text/babel"> ב-app.html — עוצר כדי לא לפרוס משהו שבור.');
  process.exit(1);
}
if (html.indexOf(OPEN, start + 1) !== -1) {
  console.error('build: נמצא יותר מתג babel אחד. הבנייה מניחה אחד בלבד — עוצר.');
  process.exit(1);
}
const bodyStart = start + OPEN.length;
const end = html.indexOf('</script>', bodyStart);
if (end === -1) {
  console.error('build: לא נמצא </script> סוגר לתג ה-babel — עוצר.');
  process.exit(1);
}
const jsx = html.slice(bodyStart, end);

// ---- 2. הידור ----
// preset-react בלבד: הדפדפנים שהאתר תומך בהם מריצים ES2020+ באופן טבעי,
// ואין צורך ב-preset-env (שהיה מנפח את הפלט ומכניס סיכון של פוליפילים).
let code;
try {
  code = babel.transformSync(jsx, {
    presets: [['@babel/preset-react', { runtime: 'classic' }]],
    filename: 'app.jsx',
    compact: false,
    sourceMaps: false,
    babelrc: false,
    configFile: false,
  }).code;
} catch (e) {
  console.error('build: הידור נכשל —', e.message);
  process.exit(1);
}

// ---- 3. כתיבת הפלט ----
// קודם מעתיקים את כל הסטטי (כולל app.html המקורי), ואז דורסים אותו במהודר.
fs.rmSync(OUT, { recursive: true, force: true });
copyStatic(ROOT, OUT);
fs.writeFileSync(path.join(OUT, 'app.bundle.js'), code, 'utf8');

// סדר הפעולות קריטי: קודם החיתוך לפי start/end, ורק אחר כך הסרת
// babel/standalone. הפוך — ההסרה מזיזה את כל האינדקסים שאחריה, והחיתוך
// נעשה במקום שגוי (נתפס בבדיקה: תג ה-babel נשאר בפלט).
// defer: הסקריפט לא חוסם את פענוח ה-HTML ורץ אחרי שה-DOM מוכן — בדיוק
// כמו ההתנהגות של text/babel היום.
html = html.slice(0, start) + '<script src="/app.bundle.js" defer></script>' + html.slice(end + '</script>'.length);

// עכשיו בטוח להסיר — אין יותר תלות באינדקסים
html = html.replace(/\s*<script src="https:\/\/unpkg\.com\/@babel\/standalone@[^"]*"><\/script>/, '');
if (/@babel\/standalone/.test(html)) {
  console.error('build: babel/standalone עדיין בפלט — עוצר.');
  process.exit(1);
}
if (/<script type="text\/babel">/.test(html)) {
  console.error('build: תג text/babel עדיין בפלט — החיתוך נכשל. עוצר.');
  process.exit(1);
}

fs.writeFileSync(path.join(OUT, 'app.html'), html, 'utf8');

const kb = (n) => Math.round(n / 1024) + 'KB';
console.log('build: app.bundle.js  ' + kb(Buffer.byteLength(code)));
console.log('build: app.html       ' + kb(Buffer.byteLength(html)) + '  (היה ' + kb(fs.statSync(SRC).size) + ')');
console.log('build: הושלם — ללא babel בדפדפן.');
