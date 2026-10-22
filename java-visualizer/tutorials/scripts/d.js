// הדרכה ד: התאמות ידניות אחרי יצירת התרגיל
window.META = {
  num: '04', title: 'מוסיפים התאמות', sub: '', goal: 'איך מוסיפים ידנית לפעולה עוד מבנה, משתנה x ומערך — ורואים שגם הם מופיעים בהדמיה',
  pal: ['#f59e0b', '#fb7185', '#8b5cf6'], poster: 40,
  outro: 'הפעולה שלכם · הפרמטרים שלכם · הכול בחלון ההרצה',
  steps: [
    ['התרגיל שנוצר', 'הפעולה <b>solve</b> מקבלת מבנה אחד — כמו שבחרתם בחלון.', 'התרגיל שנוצר'],
    ['מוסיפים פרמטרים', 'רוצים עוד מבנה, משתנה x ומערך? פשוט כותבים אותם <b>בסוגריים של הפעולה</b>.', 'מוסיפים פרמטרים לפעולה'],
    ['יוצרים אותם ב-main', 'ב-<b>main</b> בונים את הערכים שהפעולה תקבל — מתחת למבנה הקיים.', 'יוצרים ערכים ב-main'],
    ['מעדכנים את הקריאה', 'הקריאה לפעולה צריכה להעביר גם אותם — באותו סדר.', 'מעדכנים את הקריאה'],
    ['משתמשים בהם בפעולה', 'עכשיו אפשר להשתמש בהם בתוך הפעולה — x, arr ו-q.', 'משתמשים בפעולה'],
    ['"הרצה ויזואלית"', 'גם המבנה החדש והמערך מופיעים בחלון — כמו כל מבנה.', 'רואים בהדמיה'],
  ],
};

window.SCRIPT = B => {
  const D = window.DATA;
  B.files([[0, 'DataStructures'], [1, 'src'], [2, '(default package)'], [3, 'Targil1.java', true], [3, 'Main.java']]);
  B.code(D.v0, { file: 'Targil1.java' });
  B.intro(2.8);

  B.step(0).wait(.8);
  B.explain(`lines:${D.lSig}-${D.lSig}`, 'הפעולה solve מקבלת מבנה אחד — כמו שבחרתם בחלון', { side: 'bottom', dur: 3.2 });

  B.step(1).wait(.4);
  B.place(`line:${D.lSig}:50`, 'לוחצים בתוך הסוגריים של הפעולה (אחרי stk)', { hold: 2.0 }).caret(D.lSig, 50);
  B.code(D.a, { newFrom: D.lSig, newTo: D.lSig }).wait(1.0);
  B.explain(`lines:${D.lSig}-${D.lSig}`, 'כותבים עוד פרמטרים: מבנה נוסף, משתנה x ומערך', { kind: 'ok', side: 'bottom', dur: 3.4 });
  B.noCaret();

  B.step(2).wait(.4);
  B.scrollTo(Math.max(0, D.addFrom - 14), .9).wait(.3);
  B.place(`line:${D.addFrom}:8`, 'שמים את הסמן בשורה ריקה מתחת למבנה הקיים (ב-main)', { hold: 2.2 }).caret(D.addFrom, 8);
  B.code(D.b, { newFrom: D.addFrom, newTo: D.addTo }).wait(1.2);
  B.explain(`lines:${D.addFrom}-${D.addTo}`, 'כותבים כאן את המבנה, המשתנה והמערך שהפעולה תקבל', { kind: 'ok', side: 'right', dur: 3.4 });
  B.noCaret();

  B.step(3).wait(.4);
  B.place(`line:${D.cCall}:30`, 'לוחצים בתוך הסוגריים של הקריאה לפעולה', { hold: 2.0 }).caret(D.cCall, 30);
  B.code(D.c, { newFrom: D.cCall, newTo: D.cCall }).wait(1.0);
  B.explain(`lines:${D.cCall}-${D.cCall}`, 'מעבירים גם אותם לפעולה — באותו סדר', { kind: 'ok', side: 'top', dur: 3.4 });
  B.noCaret();

  B.step(4).wait(.4);
  B.scrollTo(0, .8).wait(.3);
  B.code(D.d, { newFrom: D.lTodo + 1, newTo: D.lTodo + 1 }).wait(1.0);
  B.explain(`lines:${D.lTodo + 1}-${D.lTodo + 1}`, 'עכשיו אפשר להשתמש בהם בתוך הפעולה: x, arr ו-q', { kind: 'ok', side: 'bottom', dur: 3.4 });

  B.step(5).wait(.4);
  B.press('tb:run', 'הרצה ויזואלית', { hold: 1.6 });
  B.viewer(D.frames[0], 'הרצה ויזואלית — Targil1', { wait: 1.2 });
  B.press('view:next', 'כל לחיצה = צעד אחד', { hold: 1.4 }).frame(D.frames[1]).wait(.5);
  B.steps(D.frames.slice(2), .9);
  B.explain('view:700,100,720,420', 'המחסנית, התור והמערך — כולם מופיעים בהדמיה', { kind: 'ok', side: 'left', dur: 3.2 });
  B.outro();
};
