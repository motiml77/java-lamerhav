// הדרכה ג (Visual Studio): מבנה בתוך מבנה — שלוש דוגמאות עם "תרגיל חדש ב-C#"
window.META = {
  num: '03', title: 'מבנה בתוך מבנה', sub: '', goal: 'איך יוצרים תרגיל שבו בכל תא של מבנה יש מבנה שלם — שלוש דוגמאות',
  pal: ['#db2777', '#fb923c', '#8b5cf6'], poster: 40,
  outro: 'בכל תא — מבנה שלם · "תרגיל חדש" · "מה יש בכל תא?"',
  steps: [
    ['פותחים "תרגיל חדש"', 'בתפריט התחל ← "מבני נתונים" ← "תרגיל חדש ב-C#" — נפתח חלון בחירה.', 'פותחים "תרגיל חדש"'],
    ['"מה יש בכל תא?"', 'ברירת המחדל: ערכים רגילים. כאן בוחרים <b>מבנה שלם בכל תא</b> — תורים, מחסניות או רשימות.', 'בוחרים מה יש בכל תא'],
    ['כל קבוצה — מבנה פנימי', 'בשדה הערכים, כל קבוצה (עד <b>;</b>) היא מבנה פנימי אחד. הציור מראה את התוצאה.', 'כותבים את המבנים הפנימיים'],
    ['דוגמה 1 — מחסנית של תורים', 'הפרויקט נוצר בפתרון, וההרצה הוויזואלית מראה כל תור בתוך המחסנית.', 'דוגמה 1: מחסנית של תורים'],
    ['דוגמה 2 — רשימה של מחסניות', 'אותו דבר — רק שבוחרים "רשימה מקושרת" ו"מחסניות".', 'דוגמה 2: רשימה של מחסניות'],
    ['דוגמה 3 — עץ של תורים', 'גם עץ בינארי: בכל צומת — תור שלם.', 'דוגמה 3: עץ של תורים'],
  ],
};

window.SCRIPT = B => {
  const D = window.DATA;
  const SLN = "Solution 'DataStructures-CS' (10 of 10 projects)";
  const tree = (n, open) => {
    const rows = [[0, SLN], [1, 'Examples'], [1, 'Exercise'], [1, 'Exercises']];
    for (let i = 1; i <= n; i++) { rows.push([2, 'Targil' + i]); if (i === open) rows.push([3, 'Program.cs', true]); }
    return rows;
  };
  B.files(tree(0));
  B.code(['using System;', 'using Unit4.CollectionsLib;', '', 'class Program', '{', '    static void Main(string[] args)', '    {', '', '    }', '}'], { file: 'Program.cs' });
  B.intro(2.8);

  const example = (k, first) => {
    const E = D['c' + k];
    B.wait(.4);
    B.press('menu', 'תפריט התחל ← "מבני נתונים"', { hold: first ? 1.4 : .9 }).menuOpen().wait(.4);
    B.menuHot('תרגיל חדש');
    B.press('menuitem:תרגיל חדש', 'תרגיל חדש ב-C#', { hold: first ? 1.5 : 1.0 }).menuClose(.2);
    const hMax = Math.max(ASSETS[`c${k}/s0`].h, ASSETS[`c${k}/s1`].h);   // החלון גדל אחרי הבחירה — ממקמים לפי הגדול
    B.dialog(`c${k}/s0`, 'תרגיל חדש — C#', { wait: 1.2, y: Math.max(36, Math.round((880 - hMax - 32) / 2)) });
    if (first) B.step(1).wait(.3);
    if (k === 2) B.press('dlg:chip:רשימה מקושרת', 'בוחרים רשימה מקושרת', { hold: 1.4 }).wait(.3);
    if (k === 3) B.press('dlg:chip:עץ בינארי', 'בוחרים עץ בינארי', { hold: 1.4 }).wait(.3);
    if (first) B.explain(B.area(['label:מה יש בכל תא', 'chip:רשימות מקושרות', 'chip:ערכים רגילים']), 'ברירת המחדל: ערכים רגילים בכל תא', { side: 'left', dur: 2.8 });
    B.press(`dlg:chip:${E.inside}`, `בוחרים: ${E.inside} בכל תא`, { hold: 1.6 }).swap(`c${k}/s1`).wait(.8);
    if (first) {
      B.step(2);
      B.explain('dlg:text:0', 'כל קבוצה (עד ;) היא מבנה פנימי אחד — אפשר לשנות את הערכים', { side: 'bottom', dur: 3.2 });
    }
    B.press('dlg:round:יצירת התרגיל', 'יוצרים את התרגיל', { hold: 1.4 }).swap(`c${k}/s2`).closeDialog(.2);
    B.dialog(`c${k}/s2`, 'תרגיל חדש — C#', { wait: first ? 3.2 : 1.6 });
    B.press('dlg:round:OK', 'הפרויקט נוצר בפתרון', { hold: 1.0 }).closeDialog(.3);
    if (first) B.step(3);
    B.files(tree(k, k));
    B.code(E.code, { file: 'Program.cs' });
    if (first) B.place(`tree:${E.name}`, 'Set as Startup Project — כדי שהתרגיל הזה ירוץ', { verb: 'קליק ימני', kind: 'place', hold: 2.4 });
    B.scrollTo(Math.max(0, E.from - 6), .8).wait(.3);
    B.explain(`lines:${E.from}-${E.to}`, 'בונים מבנה פנימי — ומכניסים אותו לחיצוני', { side: 'right', dur: 3.2 });
    B.scrollTo(0, .5);
    B.press('tb:run', 'הרצה ויזואלית', { hold: 1.4 });
    B.viewer(E.frames[0], `הרצה ויזואלית — ${E.label}`, { wait: 1.0 });
    B.press('view:next', 'כל לחיצה = צעד', { hold: 1.4 }).frame(E.frames[1]).wait(.5);
    B.steps(E.frames.slice(2), .8);
    B.explain('view:700,120,700,400', 'כל מבנה פנימי בתוך התא שלו', { kind: 'ok', side: 'left', dur: 2.8 });
    B.closeViewer(.4);
  };
  B.step(0).wait(.6);
  example(1, true);
  B.step(4); example(2, false);
  B.step(5); example(3, false);
  B.outro();
};
