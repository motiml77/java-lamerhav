// הדרכה ה: שליחת תרגיל והצעה לפתרון (Eclipse)
window.META = {
  num: '05', title: 'שליחת תרגיל והצעה לפתרון', sub: '', goal: 'מעלים את דף התרגיל (צילום, PDF או Word) — ומקבלים קוד מוכן, עם הצעה לפתרון שנבדקה לכל סעיף',
  pal: ['#7c5cf0', '#a78bfa', '#22d3ee'], poster: 40,
  steps: [
    ['מצלמים את דף התרגיל', 'צילום מסך (Win+Shift+S), קובץ PDF או Word — מה שיש.', 'מצלמים את התרגיל'],
    ['לוחצים "שליחת תרגיל"', 'נפתח חלון שבו מצרפים את הדף.', 'לוחצים "שליחת תרגיל"'],
    ['מצרפים את הדף', '<b>Ctrl+V</b> מדביק את הצילום. אפשר גם לגרור קבצים, ולכתוב הערה.', 'מצרפים את הדף'],
    ['"שליחה ויצירת התרגיל"', 'תוך כמה שניות נבנה הקוד, ונבדק שהוא מתקמפל.', 'שולחים'],
    ['הקובץ מוכן', 'פעולה לכל סעיף עם מקום לפתרון — והסמן כבר מחכה. מעל main: מה צריך להיות מודפס.', 'הקובץ מוכן'],
    ['💡 הצעה לפתרון', 'מעל כל סעיף. לא נכנסת לקוד — נפתחת רק כשלוחצים, ונבדקה שהיא נותנת את הפלט הנכון.', 'הצעה לפתרון'],
  ],
};

window.SCRIPT = B => {
  const D = window.DATA;
  const R = (x, y, w, h) => ({ x: B.dlg.x + x, y: B.dlg.y + 32 + y, w, h });   // מלבן בתוך החלון הפתוח
  const MAIN = ['import unit4.collectionsLib.*;', '', 'public class Main {', '', '    public static void main(String[] args) {', '', '    }', '}'];
  B.files([[0, 'DataStructures'], [1, 'src'], [2, '(default package)'], [3, 'Main.java', true], [2, 'examples']]);
  B.code(MAIN, { file: 'Main.java' });
  B.intro(2.8);

  // 1. הדף
  B.step(0).wait(.8);
  B.dialog('page/p', 'צילום מסך — שאלה 1', { wait: 1.2 });
  B.explain(R(40, 80, 1000, 280), 'מצלמים את השאלה (Win+Shift+S) — או שומרים את הקובץ (PDF / Word)', { side: 'bottom', dur: 3.4 });
  B.closeDialog(.5);

  // 2. הכפתור
  B.step(1).wait(.4);
  B.press('tb:submit', 'שליחת תרגיל', { hold: 1.8 });
  B.dialog('submit/e1', 'שליחת תרגיל', { wait: 1.2 });
  B.explain(B.area(['label:דף התרגיל', 'round:בחירת קבצים…', 'round:הדבקה (Ctrl+V)'], 10, 0), 'כאן מצרפים את דף התרגיל', { side: 'bottom', dur: 2.8 });

  // 3. מצרפים
  B.step(2).wait(.3);
  B.press('dlg:round:הדבקה (Ctrl+V)', 'Ctrl+V — מדביקים את הצילום', { hold: 1.6 }).swap('submit/e2').wait(.6);
  B.explain(R(505, 92, 145, 125), 'הצילום נוסף. אפשר להוסיף עוד דפים, או להסיר עם ×', { side: 'left', dur: 3.0 });
  B.explain('dlg:round:בחירת קבצים…', 'או: בוחרים קובץ PDF / Word — או גוררים אותו לחלון', { side: 'bottom', dur: 2.8 });
  B.type('text:0', 'רק שאלה 1', 1.0, 'submit/e3').wait(.3);
  B.explain('dlg:text:0', 'הערה (לא חובה) — למשל איזו שאלה מהדף', { side: 'top', dur: 2.6 });

  // 4. שליחה
  B.step(3).wait(.3);
  B.press('dlg:round:שליחה ויצירת התרגיל', 'שליחה ויצירת התרגיל', { hold: 1.6 }).closeDialog(.4);
  const mid = { x: 650, y: 380 };
  for (const t of ['שולח את התרגיל…', 'בונה את התרגיל…', 'בודק שהקוד מתקמפל…', 'בודק שההצעות לפתרון נותנות את התוצאה הנכונה…'])
    B.bubble(t, mid, { kind: 'info', dur: 1.15, wait: 1.25 });

  // 5. הקובץ
  B.step(4);
  B.files([[0, 'DataStructures'], [1, 'src'], [2, '(default package)'], [3, 'Main.java'], [2, D.folder], [3, D.file, true], [2, 'examples']]);
  B.code(D.code, { file: D.file, newFrom: 1, newTo: D.code.length });
  B.lens([{ line: D.hA, text: '💡 הצעה לפתרון' }, { line: D.hB, text: '💡 הצעה לפתרון' }]);
  B.caret(D.line, 8).wait(1.2);
  B.explain(`lines:${D.hA}-${D.hA + 3}`, 'פעולה לכל סעיף — רק הכותרת, והסמן כבר מחכה במקום הפתרון', { kind: 'ok', side: 'bottom', dur: 3.6 });
  B.explain(`lines:${D.exp}-${D.exp + 3}`, 'מה צריך להיות מודפס אם הפתרון נכון', { side: 'bottom', dur: 3.2 });
  B.explain(`lines:${D.main}-${D.main + 8}`, 'main מוכן: בונה נתונים לבדיקה וקורא לכל הסעיפים', { side: 'top', dur: 3.0 });

  // 6. הצעה לפתרון
  B.step(5).wait(.3);
  B.explain(`lens:${D.hA}`, 'נתקעתם? מעל כל סעיף — "הצעה לפתרון"', { side: 'bottom', dur: 2.8 });
  B.press(`lens:${D.hA}`, 'הצעה לפתרון — סעיף א', { hold: 1.6 });
  B.dialog('sol/s1', 'הצעה לפתרון', { wait: 1.2 });
  B.explain('dlg:label:נבדק', 'נבדק: הרצנו את main עם ההצעה — והודפס בדיוק מה שצריך', { kind: 'ok', side: 'bottom', dur: 3.4 });
  B.explain(R(28, 100, 540, 300), 'דרך אחת לפתור, ברמה של תיכון — עם הערות. הקוד שלכם לא משתנה', { side: 'left', dur: 3.4 });
  B.press('dlg:round:העתקה', 'אפשר להעתיק (או רק חלק שמסמנים)', { hold: 1.5 });
  B.closeDialog(.5);
  B.outro();
};
