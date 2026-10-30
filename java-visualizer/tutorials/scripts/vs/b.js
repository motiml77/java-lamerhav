// הדרכה ב (Visual Studio): דוגמאות פתורות — בפתרון "תרגילי C#" יש תיקייה Examples עם 9 דוגמאות
window.META = {
  num: '02', title: 'דוגמאות פתורות', sub: '', goal: 'איך פותחים תרגיל פתור מוכן, קוראים אותו ומריצים אותו בחלון ההדמיה',
  pal: ['#059669', '#34d399', '#0ea5e9'], poster: 30,
  steps: [
    ['פותחים את תיקיית התרגילים', 'בתפריט התחל ← <b>מבני נתונים</b> ← "תרגילי C#" — הפתרון נפתח ב-Visual Studio.', 'פותחים את "תרגילי C#"'],
    ['התיקייה "Examples"', 'ב-Solution Explorer יש 9 דוגמאות מוכנות — לכל נושא.', 'מוצאים את הדוגמאות'],
    ['בוחרים דוגמה', 'פותחים את Program.cs שלה, ובקליק ימני על הדוגמה: <b>Set as Startup Project</b>.', 'בוחרים דוגמה ופותחים'],
    ['קוראים את הקוד', 'כל פעולה פתורה, עם הערות. אפשר לקרוא, לשנות ולנסות.', 'קוראים את הקוד'],
    ['"הרצה ויזואלית"', 'לוחצים "הבא" ורואים כל שורה — וכל מה שקורה למחסנית.', 'מריצים ויזואלית'],
  ],
};

window.SCRIPT = B => {
  const D = window.DATA;
  const EX = ['ArrayExamples', 'CarExamples', 'ListExamples', 'NestedExamples', 'QueueExamples', 'RecursionExamples', 'StackExamples', 'StudentExamples', 'TreeExamples'];
  const SLN = "Solution 'DataStructures-CS' (10 of 10 projects)";
  B.files([[0, SLN], [1, 'Examples'], [1, 'Exercise'], [2, 'Program.cs']]);
  B.code(['using System;', 'using Unit4.CollectionsLib;', '', 'class Program', '{', '    static void Main(string[] args)', '    {', '', '    }', '}'], { file: 'Program.cs' });
  B.intro(2.8);

  B.step(0).wait(1.0);
  B.press('menu', 'תפריט התחל ← "מבני נתונים"', { hold: 1.4 }).menuOpen().wait(.5);
  B.menuHot('תרגילי C#');
  B.press('menuitem:תרגילי C#', 'פותחים את תיקיית התרגילים', { hold: 1.6 }).menuClose(.3);
  B.explain('el:#ide .explorer', 'הפתרון נפתח: התרגיל — וכל הדוגמאות', { side: 'left', dur: 2.8 });

  B.step(1).wait(.3);
  B.press('tree:Examples', 'פותחים את התיקייה Examples', { hold: 1.4, side: 'left' });
  B.files([[0, SLN], [1, 'Examples'], ...EX.map(e => [2, e]), [1, 'Exercise']]);
  B.wait(.6);
  B.explain('el:#tree', '9 דוגמאות פתורות — מחסנית, תור, רשימה, עץ, רקורסיה, מבנה בתוך מבנה...', { side: 'left', dur: 3.2 });

  B.step(2).wait(.3);
  B.press('tree:StackExamples', 'בוחרים דוגמה: מחסנית', { hold: 1.4, side: 'left' });
  const open = EX.flatMap(e => e === 'StackExamples' ? [[2, e], [3, 'Program.cs', true]] : [[2, e]]);
  B.files([[0, SLN], [1, 'Examples'], ...open, [1, 'Exercise']]);
  B.code(D.code, { file: 'Program.cs', newFrom: 1, newTo: D.code.length }).wait(1.0);
  B.place('tree:StackExamples', 'Set as Startup Project — כדי שזו הדוגמה שתרוץ', { verb: 'קליק ימני', kind: 'place', hold: 2.6 });

  B.step(3);
  B.explain('lines:7-23', 'פעולה פתורה: סכום בלי להרוס את המחסנית — עם הערות', { kind: 'ok', side: 'right', dur: 3.2 });
  B.scrollTo(24, .9).wait(.4);
  B.explain('lines:46-57', 'Main — בונה מחסנית וקורא לפעולות', { side: 'right', dur: 3.0 });
  B.scrollTo(0, .7).wait(.3);

  B.step(4).wait(.4);
  B.press('tb:run', 'הרצה ויזואלית', { hold: 1.6 });
  B.viewer(D.frames[0], 'הרצה ויזואלית — StackExamples', { wait: 1.2 });
  B.press('view:next', 'כל לחיצה = צעד אחד', { hold: 1.6 }).frame(D.frames[1]).wait(.6);
  B.steps(D.frames.slice(2), .95);
  B.explain('view:900,180,400,300', 'רואים את המחסנית — ואת מחסנית העזר', { kind: 'ok', side: 'left', dur: 3.0 });
  B.outro();
};
