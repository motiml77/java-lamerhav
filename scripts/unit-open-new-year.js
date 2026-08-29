/**
 * בדיקת יחידה ל-examsForView/findExamInClass — נחתך מ-app.html עצמו (שורות
 * 8685-8717 בזמן הכתיבה), לא הועתק ידנית, כדי שלא יסטה מהמימוש האמיתי.
 * studentGradeHistory הוא closure variable בקוד האמיתי (state של App) —
 * כאן הוא let לצורך הבדיקה, מוחלף בין תרחישים.
 *
 * הרצה: node scripts/unit-open-new-year.js
 */
const fs = require('fs');
const src = fs.readFileSync(__dirname + '/../app.html', 'utf8');

function extract(startMarker, endMarker) {
  const i = src.indexOf(startMarker);
  if (i < 0) throw new Error('לא נמצא: ' + startMarker);
  const j = src.indexOf(endMarker, i);
  if (j < 0) throw new Error('לא נמצא סוף: ' + endMarker);
  return src.slice(i, j);
}

let studentGradeHistory = []; // מוחלף בין תרחישים

const code = extract(
  "const GRADE_ORDER = { 'י': 0, 'יא': 1, 'יב': 2 };",
  "// תלמידה רואה: נושאי השכבה הנוכחית"
);
// const בתוך eval ישיר לא דולף לסקופ החיצוני (גם ב-sloppy mode) — var כן.
// מחליפים רק את ההכרזות ברמה העליונה, לא נוגעים בגוף הפונקציות עצמן.
eval(code.replace(/^ {12}const /gm, 'var '));

let pass = 0, fail = 0;
function check(name, cond, detail) {
  cond ? pass++ : fail++;
  console.log((cond ? 'PASS ' : 'FAIL ') + name + (cond ? '' : ' — ' + detail));
}

// ===== 1. נושא נוכחי (לא past) — תמיד exams חי, גם אם יש snapshots =====
{
  const cls = { grade: 'יא', exams: [{ id: 'live1' }], examSnapshots: { '2025/26': [{ id: 'old1' }] } };
  studentGradeHistory = [];
  const r = examsForView(cls, 'יא');
  check('נושא נוכחי מחזיר exams חי', r.length === 1 && r[0].id === 'live1', JSON.stringify(r));
}

// ===== 2. נושא past, gradeHistory תואם, snapshot קיים — התרחיש האדוורסרי =====
{
  const cls = { grade: 'יא', exams: [], examSnapshots: { '2025/26': [{ id: 'exam_123', title: 'מבחן ישן' }] } };
  studentGradeHistory = [{ from: 'יא', to: 'יב', at: '2026-06-15T00:00:00Z', year: '2025/26', classNum: '' }];
  const r = examsForView(cls, 'יב');
  check('נושא past עם snapshot תואם מחזיר את המבחן הישן', r.length === 1 && r[0].id === 'exam_123', JSON.stringify(r));
  const found = findExamInClass(cls, 'exam_123');
  check('findExamInClass מוצא מבחן שאורכב', found && found.title === 'מבחן ישן', JSON.stringify(found));
  const notFound = findExamInClass(cls, 'no_such_id');
  check('findExamInClass מחזיר null למבחן שלא קיים', notFound === null);
}

// ===== 3. נושא past, בלי snapshot כלל (נושא שמעולם לא אופס) — נופל ל-exams החי =====
{
  const cls = { grade: 'יא', exams: [{ id: 'still_live' }] }; // בלי examSnapshots בכלל
  studentGradeHistory = [{ from: 'יא', to: 'יב', year: '2025/26' }];
  const r = examsForView(cls, 'יב');
  check('נושא past בלי snapshot נופל ל-exams החי', r.length === 1 && r[0].id === 'still_live', JSON.stringify(r));
}

// ===== 4. נושא past, gradeHistory ריק (רישום ידני, לא דרך אשף) — נופל ל-exams החי, לא למסך ריק =====
{
  const cls = { grade: 'יא', exams: [{ id: 'fallback' }], examSnapshots: { '2025/26': [{ id: 'exam_123' }] } };
  studentGradeHistory = []; // אין רשומת היסטוריה כלל
  const r = examsForView(cls, 'יב');
  check('נושא past בלי gradeHistory תואם נופל ל-exams החי (לא קורס, לא ריק)', r.length === 1 && r[0].id === 'fallback', JSON.stringify(r));
}

// ===== 5. רשומת undo לא נספרת כרשומת מעבר אמיתית =====
{
  const cls = { grade: 'יא', exams: [{ id: 'live_after_undo' }], examSnapshots: { '2025/26': [{ id: 'exam_123' }] } };
  // מעבר, ואז ביטול (undo:true) — לפי סדר כרונולוגי אמיתי מ-undoPromotionRun
  studentGradeHistory = [
    { from: 'יא', to: 'יב', year: '2025/26' },
    { from: 'יב', to: 'יא', year: '2025/26', undo: true },
  ];
  // אחרי ביטול אמיתי, studentGrade חוזר ל-'יא' בפועל — ואז isPastTopic('יא' מול cls.grade='יא') הוא false ממילא.
  const r = examsForView(cls, 'יא');
  check('אחרי ביטול, studentGrade=יא → לא past כלל → exams חי', r.length === 1 && r[0].id === 'live_after_undo', JSON.stringify(r));
}

// ===== 6. שתי שנות איפוס עוקבות לאותו נושא — כל קוהורט מקבל את השנה הנכונה שלו =====
{
  const cls = {
    grade: 'יא', exams: [],
    examSnapshots: { '2024/25': [{ id: 'exam_old' }], '2025/26': [{ id: 'exam_newer' }] },
  };
  studentGradeHistory = [{ from: 'יא', to: 'יב', year: '2024/25' }];
  const r1 = examsForView(cls, 'יב');
  check('קוהורט 2024/25 מקבל את המבחן שלו, לא את 2025/26', r1[0]?.id === 'exam_old', JSON.stringify(r1));

  studentGradeHistory = [{ from: 'יא', to: 'יב', year: '2025/26' }];
  const r2 = examsForView(cls, 'יב');
  check('קוהורט 2025/26 מקבל את המבחן שלו', r2[0]?.id === 'exam_newer', JSON.stringify(r2));
}

// ===== 7. שכבת "הכל" — אף פעם לא past =====
{
  const cls = { grade: 'הכל', exams: [{ id: 'shared' }] };
  studentGradeHistory = [];
  const r = examsForView(cls, 'יב');
  check('נושא "הכל" תמיד exams חי', r[0]?.id === 'shared');
}

console.log('\n===== ' + pass + '/' + (pass + fail) + ' =====');
process.exit(fail > 0 ? 1 : 0);
