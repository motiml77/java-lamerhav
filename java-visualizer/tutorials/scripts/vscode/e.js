// הדרכה ה ב-VS Code: שליחת תרגיל והצעה לפתרון
window.META = {
  num: '05', title: 'שליחת תרגיל והצעה לפתרון', sub: '', goal: 'מעלים את דף התרגיל (צילום, PDF או Word) — ומקבלים קוד מוכן, עם הצעה לפתרון שנבדקה לכל סעיף',
  pal: ['#7c5cf0', '#a78bfa', '#22d3ee'], poster: 62,
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
  const TX = 1300 - 448 - 12, toastY = h => 880 - 30 - 10 - h;   // ההודעות של VS Code — בפינה הימנית למטה, מעל שורת המצב
  B.files([[0, 'src'], [1, 'Main.java', true], [1, 'examples'], [0, 'lib']]);
  B.code(['import unit4.collectionsLib.*;', '', 'public class Main {', '', '    public static void main(String[] args) {', '', '    }', '}'], { file: 'Main.java' });
  B.intro(2.8);

  // 1. הדף
  B.step(0).wait(.6);
  B.dialog('page/p', 'צילום מסך — דף התרגיל', { x: 220, y: 150, wait: .8 });
  B.explain({ x: 220, y: 182, w: 860, h: 340 }, '<span dir="ltr"><span class="key">Win</span>+<span class="key">Shift</span>+<span class="key">S</span></span> — מסמנים את השאלה, והיא בלוח', { side: 'bottom', dur: 3.6 });
  B.closeDialog(.4);

  // 2. שליחת תרגיל
  B.step(1).wait(.4);
  B.explain('tb:submit', 'גם הכפתור הזה למעלה (וגם הלוח "מבני נתונים")', { side: 'bottom', dur: 2.4 });
  B.press('sb:submit', 'שליחת תרגיל', { hold: 1.6 });
  B.dialog('sub/s1', 'שליחת תרגיל', { dock: true, wait: .8 });
  B.zoom({ x: B.dlg.x, y: B.dlg.y - 30, w: 992, h: 640 }, .7, 1.4).wait(.3);
  B.explain(B.area(['label:דף התרגיל', 'round:הדבקה (Ctrl+V)'], 10, 40), 'כאן מצרפים את הדף', { side: 'bottom', dur: 2.6 });

  // 3. מצרפים
  B.step(2).wait(.3);
  B.bubble('<span dir="ltr"><span class="key">Ctrl</span>+<span class="key">V</span></span> — מדביקים את הצילום', 'dlg:round:הדבקה (Ctrl+V)', { side: 'top', kind: 'click', dur: 2.2, wait: 1.6 });
  B.swap('sub/s2').wait(.6);
  B.explain('dlg:label:תמונה מודבקת 1.png', 'הצילום נוסף — אפשר להוסיף עוד, או לגרור קבצים (PDF, Word)', { kind: 'ok', side: 'bottom', dur: 3.0 });
  B.type('text:0', 'רק שאלה 1', 1.0, 'sub/s3').wait(.4);
  B.explain('dlg:text:0', 'הערה (לא חובה): למשל איזו שאלה', { side: 'bottom', dur: 2.4 });

  // 4. שליחה
  B.step(3).wait(.3);
  B.press('dlg:round:שליחה ויצירת התרגיל', 'שליחה ויצירת התרגיל', { hold: 1.6 }).swap('sub/s4');
  B.wait(.3);
  const toasts = [['t1/t1', 86, 2.2], ['t2/t2', 86, 1.6], ['t3/t3', 86, 2.0]];
  B.closeDialog(.2).unzoom(.5);
  B.dialog(toasts[0][0], '', { bare: true, x: TX, y: toastY(86), wait: toasts[0][2] });
  B.swap(toasts[1][0]).wait(toasts[1][2]);
  B.swap(toasts[2][0]).wait(toasts[2][2]);
  B.closeDialog(.3);

  // 5. הקובץ מוכן
  B.step(4);
  B.files([[0, 'src'], [1, 'ex01_stacks'], [2, D.file, true], [1, 'Main.java'], [1, 'examples'], [0, 'lib']]);
  B.code(D.code, { file: D.file, crumbs: 'src › ' + D.folder, newFrom: 1, newTo: D.code.length }).caret(D.cursor, 8);
  B.dialog('t4/t4', '', { bare: true, x: TX, y: toastY(78), wait: 1.0 });
  B.explain(`lines:${D.lenses[0].line}-${D.lenses[0].line + 3}`, 'פעולה לכל סעיף — והסמן כבר מחכה במקום הפתרון', { kind: 'ok', side: 'right', dur: 3.4 });
  B.closeDialog(.2);
  B.explain(`lines:${D.expectLine}-${D.expectLine + 3}`, 'אם פתרתם נכון — זה מה שיודפס', { side: 'right', dur: 3.2 });
  B.explain(`lines:${D.mainLine}-${Math.min(D.code.length, D.mainLine + 8)}`, 'main כבר בונה נתונים לבדיקה וקורא לסעיפים', { side: 'right', dur: 3.0 });

  // 6. הצעה לפתרון
  B.step(5).wait(.3);
  B.lens(D.lenses.map(l => ({ line: l.line, text: '💡 הצעה לפתרון' }))).wait(.6);
  B.explain(`lens:${D.lenses[1].line}`, 'מעל כל סעיף — הצעה לפתרון (לא נכנסת לקוד)', { side: 'right', dur: 3.0 });
  B.press(`lens:${D.lenses[0].line}`, 'נתקעתם? לוחצים', { hold: 1.6 });
  B.dialog('sol/v1', 'הצעה לפתרון', { bare: true, x: 308 + 496, y: 34 + 35, wait: .6 });
  B.zoom({ x: 308 + 496 - 40, y: 0, w: 536, h: 680 }, .7, 1.3).wait(.3);
  B.explain('dlg:label:נבדק', 'נבדקה: הרצנו את main עם ההצעה — והודפס בדיוק מה שצריך', { kind: 'ok', side: 'bottom', dur: 3.4 });
  B.explain('dlg:round:העתקה', 'אפשר להשוות לקוד שלכם, או להעתיק', { side: 'top', dur: 2.6 });
  B.unzoom(.5).wait(.4);
  B.outro();
};
