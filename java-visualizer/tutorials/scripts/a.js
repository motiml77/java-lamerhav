// הדרכה א: יצירת מבנה נתונים חדש — בדיוק במקום שבו הסמן
window.META = {
  num: '01', title: 'מבנה נתונים חדש', sub: 'הוסף מבנה נתונים — בדיוק במקום שבו הסמן',
  pal: ['#2563eb', '#22d3ee', '#7c3aed'], poster: 21.5,
  outro: 'סמן במקום הנכון · הוסף מבנה נתונים · הרצה ויזואלית',
  steps: [
    ['קודם — מקליקים במקום המדויק', 'המבנה ייכנס <b>בדיוק</b> בשורה שבה הסמן: בתוך <b>main</b> (או בתוך פעולה) — לא מחוץ לה!', 'הסמן במקום הנכון'],
    ['לוחצים "הוסף מבנה נתונים"', 'הכפתור בסרגל הכלים למעלה. נפתח חלון בחירה.', 'פותחים את החלון'],
    ['בוחרים מבנה', 'מחסנית, תור, רשימה, עץ, מערך, תלמיד... ואפשר גם מבנה בתוך מבנה.', 'בוחרים מבנה'],
    ['ערכים — או אקראיים', 'מסמנים "כמה ערכים אקראיים" ובוחרים כמה. הציור מראה בדיוק איך המבנה ייראה.', 'ממלאים ערכים'],
    ['שם המשתנה', 'השם המוצע מופיע <b>באפור</b>. אפשר להשאיר או לכתוב שם אחר — נבדק שהוא חוקי ולא תפוס.', 'נותנים שם'],
    ['"הוספה לקוד"', 'המבנה נבנה בקוד — בדיוק בשורה שבה היה הסמן.', 'הקוד נכנס'],
    ['"הרצה ויזואלית"', 'לוחצים "הבא" ורואים את המחסנית נבנית — push אחרי push.', 'רואים בריצה'],
  ],
};

window.SCRIPT = B => {
  const D = window.DATA;
  B.files([[0, 'DataStructures'], [1, 'src'], [2, '(default package)'], [3, 'Main.java', true], [3, 'Targil1.java'], [2, 'examples']]);
  B.code(D.MAIN, { file: 'Main.java' });
  B.intro(3.4);

  // 1. הסמן במקום הנכון
  B.step(0).wait(1.6);
  B.move('line:10:2', 1.0).click(.2).caret(10, 1);
  B.bubble('✗ מחוץ ל-main — תופיע אזהרה', 'line:10:2', { kind: 'bad', side: 'right', dur: 1.9, wait: 2.2 });
  B.move('line:7:8', .9).click(.2).caret(7, 8);
  B.bubble('✓ כאן! בתוך main — הקוד ייכנס בשורה הזו', 'line:7:8', { kind: 'ok', side: 'right', dur: 2.8, wait: 3.0 });

  // 2. פותחים את החלון
  B.step(1).wait(.8);
  B.move('tb:add', 1.0).hot('#tbAdd', 1.0).wait(.35).click(.3);
  B.dialog('add/a1', 'הוסף מבנה נתונים', { wait: 1.2 });

  // 3. בוחרים מבנה
  B.step(2).wait(.6);
  B.hl(B.area(['label:איזה מבנה', 'chip:מכונית', 'chip:תור']), 1.8).wait(1.0);
  B.moveClick('dlg:chip:מחסנית', .8, .1).swap('add/a2').wait(.9);
  B.hl(B.area(['label:מה יש בכל תא', 'chip:רשימות מקושרות', 'chip:ערכים רגילים']), 2.4);
  B.bubble('אפשר גם מבנה בתוך מבנה (הדרכה 03)', 'dlg:label:מה יש בכל תא', { side: 'left', dur: 2.4, wait: 2.8 });

  // 4. ערכים אקראיים
  B.step(3).wait(.6);
  B.moveClick('dlg:check:אקראיים@0.92,0.5', .9, .1).swap('add/a3').wait(.9);
  B.moveClick('dlg:spin@0.42,0.5', .6, .1).swap('add/a4').wait(.6);
  B.bubble('השדה התמלא ב-5 ערכים אקראיים', 'dlg:text:0', { side: 'bottom', dur: 2.2, wait: 2.4 });
  const pv = B.area(['label:כך ייראה', 'label:Stack<Integer>'], 10, 150);
  B.zoom(pv, .8, 1.5);
  B.bubble('כך ייראה המבנה — האחרון שנכנס למעלה', pv.x + pv.w / 2 > 0 ? { x: pv.x + pv.w / 2, y: pv.y + 10 } : 'dlg:text:0', { side: 'top', dur: 2.6, wait: 2.9 });
  B.unzoom(.7);

  // 5. השם
  B.step(4).wait(.5);
  B.move('dlg:text:1@0.15,0.5', .8);
  B.hl('dlg:text:1', 2.6);
  B.bubble('stk — השם המוצע, באפור', 'dlg:text:1', { side: 'top', dur: 2.8, wait: 3.1 });

  // 6. הוספה לקוד
  B.step(5).wait(.5);
  B.moveClick('dlg:round:הוספה לקוד', .9, .25).closeDialog(.35);
  B.code(D.code, { newFrom: D.newFrom, newTo: D.newTo }).noCaret().wait(.9);
  B.hl(`lines:${D.newFrom}-${D.newTo}`, 2.8);
  B.bubble('✓ הקוד נכנס בדיוק במקום הסמן', `lines:${D.newFrom}-${D.newTo}`, { kind: 'ok', side: 'bottom', dur: 2.8, wait: 3.2 });

  // 7. הרצה ויזואלית
  B.step(6).wait(.5);
  B.move('tb:run', 1.0).hot('#tbRun', .8).wait(.3).click(.3);
  B.viewer(D.frames[0], 'הרצה ויזואלית — Main', { wait: 1.0 });
  B.bubble('"הבא" — שורה אחר שורה', 'view:next', { side: 'bottom', dur: 2.0, wait: 1.6 });
  B.steps(D.frames.slice(1), .75);
  B.hl('view:960,120,280,300', 2.6);
  B.bubble('המחסנית שבנינו ✓', 'view:960,120,280,300', { kind: 'ok', side: 'left', dur: 2.6, wait: 3.0 });
  B.outro();
};
