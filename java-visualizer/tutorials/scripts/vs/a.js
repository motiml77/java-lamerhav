// הדרכה א (Visual Studio): יצירת מבנה נתונים חדש — "הוסף מבנה נתונים ל-C#", והקוד נכנס במקום הסמן (Ctrl+V)
window.META = {
  num: '01', title: 'יצירת מבנה נתונים חדש', sub: '', goal: 'איך מוסיפים לקוד מבנה נתונים מוכן (מחסנית, תור, רשימה...) — בדיוק במקום שרוצים',
  pal: ['#2563eb', '#22d3ee', '#7c3aed'], poster: 30,
  steps: [
    ['שמים את הסמן במקום הנכון', 'המבנה ייכנס <b>בדיוק בשורה שבה הסמן</b> — בתוך <b>Main</b> (או בתוך פעולה), לא מחוץ להן.', 'שמים את הסמן בתוך Main'],
    ['פותחים "הוסף מבנה נתונים"', 'בתפריט התחל ← "מבני נתונים" (או הקיצור בשולחן העבודה). נפתח חלון בחירה.', 'פותחים "הוסף מבנה נתונים"'],
    ['בוחרים מבנה', 'מחסנית, תור, רשימה, עץ, מערך, תלמיד... ואפשר גם מבנה בתוך מבנה.', 'בוחרים מבנה'],
    ['בוחרים ערכים', 'ערכים אקראיים כבר מסומנים — בוחרים כמה, או כותבים בעצמכם. למטה רואים איך המבנה ייראה.', 'בוחרים ערכים'],
    ['נותנים שם', 'השם המוצע מופיע <b>באפור</b>. אפשר להשאיר אותו או לכתוב שם אחר.', 'נותנים שם'],
    ['"העתקה ללוח" ואז Ctrl+V', 'חוזרים ל-Visual Studio ומדביקים — המבנה נבנה בקוד בדיוק במקום הסמן.', 'מעתיקים ומדביקים'],
    ['"הרצה ויזואלית"', 'ליד כפתור ההפעלה הירוק בוחרים "הרצה ויזואלית" — ורואים את המבנה נבנה צעד אחרי צעד.', 'מריצים ויזואלית'],
  ],
};

window.SCRIPT = B => {
  const D = window.DATA;
  B.files([[0, "Solution 'DataStructures-CS' (10 of 10 projects)"], [1, 'Examples'], [1, 'Exercise'], [2, 'Program.cs', true]]);
  B.code(D.MAIN, { file: 'Program.cs' });
  B.intro(2.8);

  // 1. הסמן במקום הנכון
  B.step(0).wait(1.2);
  B.place('line:12:2', 'מחוץ ל-Main — שם הקוד לא יעבוד', { verb: 'לא כאן ✗', kind: 'bad', hold: 2.0, bx: .25 }).caret(12, 1);
  B.place('line:9:8', 'בתוך Main, בשורה הריקה — כאן ייכנס המבנה', { verb: 'שים את הסמן כאן ✓', kind: 'ok', hold: 2.6, bx: .25 }).caret(9, 8);

  // 2. פותחים את החלון — מתפריט התחל
  B.step(1).wait(.5);
  B.press('menu', 'תפריט התחל ← "מבני נתונים"', { hold: 1.4 }).menuOpen().wait(.5);
  B.menuHot('הוסף מבנה נתונים');
  B.press('menuitem:הוסף מבנה נתונים', 'הוסף מבנה נתונים ל-C#', { hold: 1.6 }).menuClose(.2);
  B.dialog('add/a1', 'הוסף מבנה נתונים — C#', { wait: 1.6 });

  // 3. בוחרים מבנה
  B.step(2).wait(.4);
  B.press('dlg:chip:מחסנית', 'בוחרים מחסנית', { hold: 1.6 }).swap('add/a2').wait(.8);
  B.explain(B.area(['label:מה יש בכל תא', 'chip:רשימות מקושרות', 'chip:ערכים רגילים']), 'אפשר גם מבנה בתוך מבנה (הדרכה 03)', { side: 'left', dur: 3.0 });

  // 4. ערכים
  B.step(3).wait(.4);
  B.explain('dlg:check:אקראיים', 'ערכים אקראיים — כבר מסומן', { side: 'top', dur: 2.4 });
  B.press('dlg:spin@0.42,0.5', 'בוחרים כמה ערכים', { hold: 1.4 }).swap('add/a3').wait(.6);
  B.explain('dlg:text:0', 'השדה התמלא בערכים — אפשר גם לכתוב בעצמך', { side: 'bottom', dur: 3.2 });
  const pv = B.area(['label:כך ייראה', 'label:Stack<int>'], 10, 150);
  B.explain(pv, 'כך ייראה המבנה — האחרון שנכנס נמצא למעלה', { side: 'right', dur: 3.4 });

  // 5. שם
  B.step(4).wait(.4);
  B.explain('dlg:text:1', 'השם המוצע (באפור). אפשר להשאיר, או ללחוץ כאן ולכתוב שם אחר', { side: 'top', dur: 3.4 });

  // 6. העתקה ללוח, ואז Ctrl+V בקוד
  B.step(5).wait(.4);
  B.press('dlg:round:העתקה ללוח', 'הקוד מועתק ללוח', { hold: 1.6 }).swap('add/a4').wait(.3);
  B.closeDialog(.2);
  B.dialog('add/a4', 'הוסף מבנה נתונים — C#', { wait: 2.6 });
  B.press('dlg:round:OK', 'חוזרים ל-Visual Studio', { hold: 1.2 }).closeDialog(.4);
  B.bubble('<span class="key">Ctrl</span> + <span class="key">V</span> — מדביקים במקום הסמן', 'line:9:8', { side: 'bottom', kind: 'ok', dur: 2.4, wait: 2.0 });
  B.code(D.code, { newFrom: D.newFrom, newTo: D.newTo }).noCaret().wait(1.0);
  B.explain(`lines:${D.newFrom}-${D.newTo}`, 'הקוד נכנס בדיוק במקום שבו היה הסמן', { kind: 'ok', side: 'bottom', dur: 3.4 });

  // 7. הרצה ויזואלית
  B.step(6).wait(.4);
  B.explain('tb:run', 'הפרופיל "הרצה ויזואלית" — ליד כפתור ההפעלה הירוק', { side: 'bottom', dur: 2.6 });
  B.press('tb:run', 'הרצה ויזואלית', { hold: 1.4 });
  B.viewer(D.frames[0], 'הרצה ויזואלית — Program.cs', { wait: 1.2 });
  B.press('view:next', 'כל לחיצה = שורה אחת בקוד', { hold: 1.6 }).frame(D.frames[1]).wait(.6);
  B.steps(D.frames.slice(2), .9);
  B.explain('view:900,120,360,300', 'המחסנית שבנינו', { kind: 'ok', side: 'left', dur: 3.0 });
  B.outro();
};
