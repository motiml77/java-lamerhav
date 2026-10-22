// הדרכה ד: התאמות ידניות אחרי יצירת התרגיל
window.META = {
  num: '04', title: 'מוסיפים התאמות', sub: 'עוד מבנה, משתנה x, מערך — ידנית בקוד, ורואים הכול בהדמיה',
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
  B.intro(3.4);

  // 1. התרגיל שנוצר
  B.step(0).wait(.8);
  B.hl(`lines:${D.lSig}-${D.lSig}`, 2.6);
  B.bubble('הפעולה מקבלת מבנה אחד', `lines:${D.lSig}-${D.lSig}`, { side: 'bottom', dur: 2.8, wait: 3.2 });

  // 2. פרמטרים
  B.step(1).wait(.4);
  B.move(`line:${D.lSig}:50`, 1.0).click(.2).caret(D.lSig, 50);
  B.code(D.a, { newFrom: D.lSig, newTo: D.lSig }).wait(1.0);
  B.hl(`lines:${D.lSig}-${D.lSig}`, 2.8);
  B.bubble('מבנה נוסף, משתנה x ומערך — בסוגריים', `lines:${D.lSig}-${D.lSig}`, { kind: 'ok', side: 'bottom', dur: 3.0, wait: 3.4 });
  B.noCaret();

  // 3. ב-main
  B.step(2).wait(.4);
  B.scrollTo(Math.max(0, D.addFrom - 14), .9).wait(.3);
  B.code(D.b, { newFrom: D.addFrom, newTo: D.addTo }).wait(1.2);
  B.hl(`lines:${D.addFrom}-${D.addTo}`, 2.8);
  B.bubble('יוצרים אותם ב-main — כמו כל משתנה', `lines:${D.addFrom}-${D.addTo}`, { kind: 'ok', side: 'right', dur: 3.0, wait: 3.4 });

  // 4. הקריאה
  B.step(3).wait(.4);
  B.code(D.c, { newFrom: D.cCall, newTo: D.cCall }).wait(1.0);
  B.hl(`lines:${D.cCall}-${D.cCall}`, 2.8);
  B.bubble('מעבירים אותם לפעולה — באותו סדר', `lines:${D.cCall}-${D.cCall}`, { kind: 'ok', side: 'top', dur: 3.0, wait: 3.4 });

  // 5. שימוש בפעולה
  B.step(4).wait(.4);
  B.scrollTo(0, .8).wait(.3);
  B.code(D.d, { newFrom: D.lTodo + 1, newTo: D.lTodo + 1 }).wait(1.0);
  B.hl(`lines:${D.lTodo + 1}-${D.lTodo + 1}`, 2.8);
  B.bubble('אפשר להשתמש בהם: x, arr ו-q', `lines:${D.lTodo + 1}-${D.lTodo + 1}`, { kind: 'ok', side: 'bottom', dur: 3.0, wait: 3.4 });

  // 6. הרצה ויזואלית
  B.step(5).wait(.4);
  B.move('tb:run', 1.0).hot('#tbRun', .8).wait(.3).click(.3);
  B.viewer(D.frames[0], 'הרצה ויזואלית — Targil1', { wait: 1.0 });
  B.bubble('"הבא" — שורה אחרי שורה', 'view:next', { side: 'bottom', dur: 2.0, wait: 1.6 });
  B.steps(D.frames.slice(1), .75);
  B.hl('view:700,100,720,420', 2.8);
  B.bubble('המחסנית, התור והמערך — כולם בהדמיה', 'view:700,100,720,420', { kind: 'ok', side: 'left', dur: 3.0, wait: 3.4 });
  B.outro();
};
