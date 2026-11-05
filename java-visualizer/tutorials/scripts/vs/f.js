// הדרכה ו (Visual Studio): עבודה ב-Visual Studio מההתחלה — פתיחת תיקיית התרגילים עם DSViz, הוספת תרגיל (גם ממאגר השאלות) והרצה ויזואלית
window.META = {
  num: '06', title: 'עבודה ב-Visual Studio — מההתחלה', sub: '', goal: 'פותחים את תיקיית התרגילים, מוסיפים תרגיל מסרגל הכלים של DSViz או ממאגר השאלות — ומריצים ויזואלית',
  pal: ['#2f6fed', '#22d3ee', '#865fc5'], poster: 14,
  outro: 'סרגל הכלים של DSViz · תרגיל חדש · מאגר שאלות · הרצה ויזואלית',
  steps: [
    ['פרויקט חדש מהתבנית של DSViz', 'File ← New ← Project, מחפשים "DSViz" ובוחרים "תרגיל מבני נתונים (DSViz)" — פרויקט קונסול עם הספרייה כבר מחוברת.', 'פרויקט חדש'],
    ['סרגל הכלים של DSViz', 'בראש החלון, בפני עצמו: תרגיל חדש · הוספת מבנה · מאגר שאלות · שליחה · הצעה לפתרון · הרצה ויזואלית.', 'סרגל הכלים'],
    ['"תרגיל חדש"', 'בוחרים מבנה נתונים ולוחצים "יצירת התרגיל" — הפרויקט נוצר בפתרון.', 'תרגיל חדש'],
    ['"מאגר שאלות"', 'שאלה מוכנה מהמאגר: בוחרים (או "שאלה אקראית") ולוחצים "צור תרגיל".', 'מאגר שאלות'],
    ['הרצה ויזואלית', 'לוחצים "הרצה ויזואלית" בסרגל — ובכל לחיצה על "הבא" רואים צעד.', 'הרצה ויזואלית'],
  ],
};

window.SCRIPT = B => {
  const D = window.DATA, A = window.ASSETS;
  const SLN = "Solution 'DataStructures-CS'";
  // מלבן שמכסה כמה כפתורים בסרגל
  const U = (...ts) => { const rs = ts.map(t => B.rectOfTarget(t)); const x = Math.min(...rs.map(r => r.x)), y = Math.min(...rs.map(r => r.y)); return { x: x - 6, y: y - 4, w: Math.max(...rs.map(r => r.x + r.w)) - x + 12, h: Math.max(...rs.map(r => r.y + r.h)) - y + 8 }; };
  const base = [[0, SLN + ' (10 of 10 projects)'], [1, 'Examples'], [1, 'Exercise']];
  B.files(base);
  B.code(['using System;', 'using Unit4.CollectionsLib;', '', 'class Program', '{', '    static void Main(string[] args)', '    {', '', '    }', '}'], { file: 'Program.cs' });
  B.intro(2.8);

  // 1. פרויקט חדש מהתבנית של DSViz
  B.step(0).wait(.4);
  B.bubble('File ← New ← Project  (Ctrl+Shift+N)', { x: 640, y: 330 }, { kind: 'info', dur: 2.4, wait: 2.6 });
  B.dialog('f_open/p0', 'Create a new project', { wait: 1.2 });
  B.explain('dlg:text:0', 'בחיפוש כותבים DSViz', { side: 'bottom', dur: 2.4 });
  B.press('dlg:chip:' + D.tmpl, 'בוחרים "תרגיל מבני נתונים (DSViz)"', { hold: 1.6 });
  B.press('dlg:round:Next', 'Next', { hold: 1.2 }).swap('f_open/p1').wait(.8);
  B.explain('dlg:text:0', 'שם לפרויקט (אפשר להשאיר Targil1)', { side: 'bottom', dur: 2.4 });
  B.press('dlg:round:Create', 'Create', { hold: 1.4 }).closeDialog(.4);
  B.files([[0, "Solution 'Targil1' (1 of 1 project)"], [1, 'Targil1'], [2, 'Properties'], [2, 'lib'], [2, 'Program.cs', true]]);
  B.code(D.tmplCode, { file: 'Program.cs' });
  B.explain('tree:Targil1', 'נפתח פרויקט עם תרגיל מחסנית מוכן — והספרייה של DSViz כבר מחוברת', { kind: 'ok', side: 'left', dur: 3.2 });

  // 2. סרגל הכלים
  B.step(1).wait(.3);
  B.explain('vb:new', 'סרגל הכלים של DSViz — בראש החלון, בפני עצמו', { side: 'bottom', dur: 2.6 });
  B.explain(U('vb:new', 'vb:insert', 'vb:bank'), 'יוצרים: תרגיל חדש · הוספת מבנה · מאגר שאלות', { side: 'bottom', dur: 3.0 });
  B.explain(U('vb:submit', 'vb:solution'), 'שליחת תרגיל וההצעה לפתרון', { side: 'bottom', dur: 2.6 });
  B.explain('vb:run', 'וכאן מריצים ויזואלית', { side: 'bottom', dur: 2.4 });

  // 3. תרגיל חדש
  B.step(2).wait(.3);
  B.press('vb:new', 'לוחצים "תרגיל חדש"', { hold: 1.4 });
  B.dialog('f_new/n0', 'תרגיל חדש — C#', { wait: 1.2 });
  B.explain(B.area(['label:איזה מבנה נתונים', 'chip:מחסנית', 'chip:עץ בינארי'], 8, 0), 'בוחרים מבנה נתונים', { side: 'left', dur: 2.4 });
  B.press('dlg:chip:תור', 'בוחרים תור', { hold: 1.4 }).swap('f_new/n1').wait(.8);
  B.press('dlg:round:יצירת התרגיל', 'יוצרים את התרגיל', { hold: 1.4 }).swap('f_new/n2').closeDialog(.2);
  B.dialog('f_new/n2', 'תרגיל חדש — C#', { wait: 2.4 });
  B.press('dlg:round:אישור', 'הפרויקט נוצר בפתרון', { hold: 1.0 }).closeDialog(.3);
  B.files(base.concat([[1, 'Exercises'], [2, D.newName], [3, 'Program.cs', true]]));
  B.code(D.newCode, { file: 'Program.cs' });
  B.place(`tree:${D.newName}`, 'Set as Startup Project — כדי שהתרגיל הזה ירוץ', { verb: 'קליק ימני', kind: 'place', hold: 2.4 });

  // 4. מאגר שאלות
  B.step(3).wait(.3);
  B.press('vb:bank', 'לוחצים "מאגר שאלות"', { hold: 1.4 });
  B.dialog('f_bank/b0', 'מאגר תרגילים — C#', { wait: 1.6 });
  B.explain(B.area(['round:שאלה אקראית'], 8, 0), 'בוחרים נושא ושאלה מהרשימה — או "שאלה אקראית"', { side: 'top', dur: 3.0 });
  B.press('dlg:round:שאלה אקראית', 'שאלה אקראית', { hold: 1.4 }).swap('f_bank/b1').wait(1.4);
  B.press('dlg:round:צור תרגיל', 'צור תרגיל', { hold: 1.6 }).wait(.8);
  B.closeDialog(.4);
  B.files(base.concat([[1, 'Exercises'], [2, D.newName], [2, D.bankName], [3, D.bankFile, true]]));
  B.code(D.bankCode, { file: D.bankFile });
  B.explain('tree:' + D.bankName, 'השאלה נוספה כפרויקט בפתרון — עם מעטפת (main) שבודק את הפתרון', { kind: 'ok', side: 'left', dur: 3.2 });

  // 5. הרצה ויזואלית
  B.step(4).wait(.3);
  B.files(base.concat([[1, 'Exercises'], [2, D.newName], [3, 'Program.cs', true]]));
  B.code(D.newCode, { file: 'Program.cs' });
  B.press('vb:run', 'לוחצים "הרצה ויזואלית"', { hold: 1.4 });
  B.viewer(D.frames[0], 'הרצה ויזואלית — התרגיל שיצרנו', { wait: 1.2 });
  B.press('view:next', 'כל לחיצה = צעד', { hold: 1.4 }).frame(D.frames[1]).wait(.5);
  B.steps(D.frames.slice(2), .8);
  B.closeViewer(.4);
  B.outro();
};
