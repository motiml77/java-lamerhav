// הדרכה ג: מבנה בתוך מבנה — שלוש דוגמאות
window.META = {
  num: '03', title: 'מבנה בתוך מבנה', sub: '', goal: 'איך יוצרים תרגיל שבו בכל תא של מבנה יש מבנה שלם — שלוש דוגמאות',
  pal: ['#db2777', '#fb923c', '#8b5cf6'], poster: 40,
  outro: 'בכל תא — מבנה שלם · "תרגיל חדש" · "מה יש בכל תא?"',
  steps: [
    ['לוחצים "תרגיל חדש"', 'הכפתור בסרגל הכלים — נפתח חלון בחירה.', 'לוחצים "תרגיל חדש"'],
    ['"מה יש בכל תא?"', 'ברירת המחדל: ערכים רגילים. כאן בוחרים <b>מבנה שלם בכל תא</b> — תורים, מחסניות או רשימות.', 'בוחרים מה יש בכל תא'],
    ['כל קבוצה — מבנה פנימי', 'בשדה הערכים, כל קבוצה (עד <b>;</b>) היא מבנה פנימי אחד. הציור מראה את התוצאה.', 'כותבים את המבנים הפנימיים'],
    ['דוגמה 1 — מחסנית של תורים', 'התרגיל נוצר, וההרצה הוויזואלית מראה כל תור בתוך המחסנית.', 'דוגמה 1: מחסנית של תורים'],
    ['דוגמה 2 — רשימה של מחסניות', 'אותו דבר — רק שבוחרים "רשימה מקושרת" ו"מחסניות".', 'דוגמה 2: רשימה של מחסניות'],
    ['דוגמה 3 — עץ של תורים', 'גם עץ בינארי: בכל צומת — תור שלם.', 'דוגמה 3: עץ של תורים'],
  ],
};

window.SCRIPT = B => {
  const D = window.DATA;
  B.files([[0, 'DataStructures'], [1, 'src'], [2, '(default package)'], [3, 'Main.java']]);
  B.code(['import unit4.collectionsLib.*;', '', 'public class Main {', '', '    public static void main(String[] args) {', '', '    }', '}'], { file: 'Main.java' });
  B.intro(2.8);

  const example = (k, first) => {
    const E = D['c' + k];
    B.wait(.4);
    B.press('tb:new', 'תרגיל חדש', { hold: 1.6 });
    B.dialog(`c${k}/s0`, 'תרגיל חדש', { wait: 1.2 });
    if (first) B.step(1).wait(.3);
    if (k === 2) B.press('dlg:chip:רשימה מקושרת', 'בוחרים רשימה מקושרת', { hold: 1.4 }).wait(.3);
    if (k === 3) B.press('dlg:chip:עץ בינארי', 'בוחרים עץ בינארי', { hold: 1.4 }).wait(.3);
    if (first) B.explain(B.area(['label:מה יש בכל תא', 'chip:רשימות מקושרות', 'chip:ערכים רגילים']), 'ברירת המחדל: ערכים רגילים בכל תא', { side: 'left', dur: 2.8 });
    const inside = k === 2 ? 'מחסניות' : 'תורים';
    B.press(`dlg:chip:${inside}`, `בוחרים: ${inside} בכל תא`, { hold: 1.6 }).swap(`c${k}/s1`).wait(.8);
    if (first) {
      B.step(2);
      B.explain('dlg:label:מבנים פנימיים', 'כל קבוצה (עד ;) היא מבנה פנימי אחד — אפשר לשנות את הערכים', { side: 'top', dur: 3.2 });
    }
    const dg = B.area(['label:כך ייבנה'], 10, 190);
    B.explain({ x: dg.x + dg.w / 2, y: dg.y + 90 }, `כך ייראה: ${E.label}`, { kind: 'ok', side: 'bottom', dur: 3.0 });
    B.press('dlg:round:יצירת התרגיל', 'יוצרים את התרגיל', { hold: 1.4 }).closeDialog(.3);
    B.code(E.code, { file: 'Targil1.java' });
    B.scrollTo(Math.max(0, E.from - 6), .8).wait(.3);
    B.explain(`lines:${E.from}-${E.to}`, 'בונים מבנה פנימי — ומכניסים אותו לחיצוני', { side: 'left', dur: 3.2 });
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
