// הדרכה ה (Visual Studio): שליחת תרגיל והצעה לפתרון — "שליחת תרגיל ב-C#" ו"הצעה לפתרון ב-C#" מתפריט התחל
window.META = {
  num: '05', title: 'שליחת תרגיל והצעה לפתרון', sub: '', goal: 'מעלים את דף התרגיל (צילום, PDF או Word) — ומקבלים קוד מוכן, עם הצעה לפתרון שנבדקה לכל סעיף',
  pal: ['#7c5cf0', '#a78bfa', '#22d3ee'], poster: 40,
  steps: [
    ['מצלמים את דף התרגיל', 'צילום מסך (Win+Shift+S), קובץ PDF או Word — מה שיש.', 'מצלמים את התרגיל'],
    ['לוחצים "שליחת תרגיל"', 'נפתח חלון שבו מצרפים את הדף.', 'לוחצים "שליחת תרגיל"'],
    ['מצרפים את הדף', '<b>Ctrl+V</b> מדביק את הצילום. אפשר גם לגרור קבצים, ולכתוב הערה.', 'מצרפים את הדף'],
    ['"שליחה ויצירת התרגיל"', 'תוך כמה שניות נבנה הקוד, ונבדק שהוא מתקמפל.', 'שולחים'],
    ['הקובץ מוכן', 'פעולה לכל סעיף עם מקום לפתרון — והסמן כבר מחכה. מעל Main: מה צריך להיות מודפס.', 'הקובץ מוכן'],
    ['💡 הצעה לפתרון', 'בקיצור "הצעה לפתרון ב-C#": בוחרים סעיף. לא נכנסת לקוד — ונבדקה שהיא נותנת את הפלט הנכון.', 'הצעה לפתרון'],
  ],
};

window.SCRIPT = B => {
  const D = window.DATA;
  const R = (x, y, w, h) => ({ x: B.dlg.x + x, y: B.dlg.y + 32 + y, w, h });   // מלבן בתוך החלון הפתוח
  const MAIN = ['using System;', 'using Unit4.CollectionsLib;', '', 'class Program', '{', '    static void Main(string[] args)', '    {', '', '    }', '}'];
  B.files([[0, "Solution 'DataStructures-CS' (10 of 10 projects)"], [1, 'Examples'], [1, 'Exercise'], [2, 'Program.cs', true]]);
  B.code(MAIN, { file: 'Program.cs' });
  B.intro(2.8);

  // 1. הדף
  B.step(0).wait(.8);
  B.dialog('page/p', 'צילום מסך — שאלה 1', { wait: 1.2 });
  B.explain(R(40, 80, 1000, 280), 'מצלמים את השאלה (Win+Shift+S) — או שומרים את הקובץ (PDF / Word)', { side: 'bottom', dur: 3.4 });
  B.closeDialog(.5);

  // 2. מתפריט התחל
  B.step(1).wait(.4);
  B.press('menu', 'תפריט התחל ← "מבני נתונים"', { hold: 1.4 }).menuOpen().wait(.5);
  B.menuHot('שליחת תרגיל');
  B.press('menuitem:שליחת תרגיל', 'שליחת תרגיל ב-C#', { hold: 1.6 }).menuClose(.2);
  B.dialog('sub/e1', 'שליחת תרגיל ב-C#', { wait: 1.2 });
  B.explain(B.area(['label:דף התרגיל', 'round:בחירת קבצים…', 'round:הדבקה (Ctrl+V)'], 10, 0), 'כאן מצרפים את דף התרגיל', { side: 'bottom', dur: 2.8 });

  // 3. מצרפים
  B.step(2).wait(.3);
  B.press('dlg:round:הדבקה (Ctrl+V)', 'Ctrl+V — מדביקים את הצילום', { hold: 1.6 }).swap('sub/e2').wait(.6);
  B.explain(R(522, 128, 132, 120), 'הצילום נוסף. אפשר להוסיף עוד דפים, או להסיר עם ×', { side: 'left', dur: 3.0 });
  B.explain('dlg:round:בחירת קבצים…', 'או: בוחרים קובץ PDF / Word — או גוררים אותו לחלון', { side: 'bottom', dur: 2.8 });
  B.type('text:0', 'רק שאלה 1', 1.0, 'sub/e3').wait(.3);
  B.explain('dlg:text:0', 'הערה (לא חובה) — למשל איזו שאלה מהדף', { side: 'top', dur: 2.6 });

  // 4. שליחה
  B.step(3).wait(.3);
  B.press('dlg:round:שליחה ויצירת התרגיל', 'שליחה ויצירת התרגיל', { hold: 1.6 }).closeDialog(.4);
  const mid = { x: 650, y: 380 };
  for (const t of ['שולח את התרגיל…', 'בונה את התרגיל…', 'בודק שהקוד מתקמפל…', 'בודק שההצעות לפתרון נותנות את התוצאה הנכונה…'])
    B.bubble(t, mid, { kind: 'info', dur: 1.15, wait: 1.25 });
  B.dialog('sub/e4', 'שליחת תרגיל — C#', { wait: 3.0 });
  B.press('dlg:round:OK', 'הפרויקט נוצר בפתרון', { hold: 1.0 }).closeDialog(.3);

  // 5. הקובץ
  B.step(4);
  B.files([[0, "Solution 'DataStructures-CS' (11 of 11 projects)"], [1, 'Examples'], [1, 'Exercise'], [1, 'Exercises'], [2, 'Ex01_Stacks'], ...D.files.map(f => [3, f, true])]);
  B.code(D.code, { file: D.files[0], newFrom: 1, newTo: D.code.length });
  B.caret(D.marks[0] + 1, 8).wait(1.2);
  B.place('tree:Ex01_Stacks', 'Set as Startup Project — כדי שהתרגיל הזה ירוץ', { verb: 'קליק ימני', kind: 'place', hold: 2.4, side: 'left' });
  B.explain(`lines:${D.marks[0] - 2}-${D.marks[0] + 2}`, 'פעולה לכל סעיף — רק הכותרת, והסמן כבר מחכה במקום הפתרון', { kind: 'ok', side: 'bottom', dur: 3.6 });
  B.scrollTo(Math.max(0, D.expFrom - 5), .8).wait(.3);
  B.explain(`lines:${D.expFrom}-${D.expFrom + 4}`, 'מה צריך להיות מודפס אם הפתרון נכון', { side: 'bottom', dur: 3.2 });
  B.explain(`lines:${D.mainLine}-${D.mainLine + 8}`, 'Main מוכן: בונה נתונים לבדיקה וקורא לכל הסעיפים', { side: 'top', dur: 3.0 });
  B.scrollTo(0, .5);

  // 6. הצעה לפתרון
  B.step(5).wait(.3);
  B.press('menu', 'נתקעתם? תפריט התחל ← "מבני נתונים"', { hold: 1.4 }).menuOpen().wait(.5);
  B.menuHot('הצעה לפתרון');
  B.press('menuitem:הצעה לפתרון', 'הצעה לפתרון ב-C#', { hold: 1.6 }).menuClose(.2);
  B.dialog('sol/l0', 'הצעה לפתרון — C#', { wait: 1.2 });
  B.explain(R(591, 74, 248, 416), 'כל התרגילים שנשלחו — וכל הסעיפים שלהם', { side: 'left', dur: 2.8 });
  B.press(R(600, 108, 230, 30), 'בוחרים סעיף א\'', { hold: 1.4 }).swap('sol/l1').wait(.6);
  B.explain('dlg:label:נבדק', 'נבדק: הרצנו את Main עם ההצעה — והודפס בדיוק מה שצריך', { kind: 'ok', side: 'bottom', dur: 3.4 });
  B.explain(R(20, 152, 556, 336), 'דרך אחת לפתור, ברמה של תיכון — עם הערות. הקוד שלכם לא משתנה', { side: 'bottom', dur: 3.4 });
  B.press(R(600, 140, 230, 30), 'וכך לכל סעיף', { hold: 1.2 }).swap('sol/l2').wait(1.4);
  B.press('dlg:round:העתקה', 'אפשר להעתיק (או רק חלק שמסמנים)', { hold: 1.5 });
  B.closeDialog(.5);
  B.outro();
};
