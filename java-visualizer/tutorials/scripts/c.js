// הדרכה ג: מבנה בתוך מבנה — שלוש דוגמאות
window.META = {
  num: '03', title: 'מבנה בתוך מבנה', sub: 'מחסנית של תורים · רשימה של מחסניות · עץ של תורים',
  pal: ['#db2777', '#fb923c', '#8b5cf6'], poster: 40,
  outro: 'בכל תא — מבנה שלם · "תרגיל חדש" · "מה יש בכל תא?"',
  steps: [
    ['"תרגיל חדש"', 'לוחצים על הכפתור בסרגל הכלים — נפתח חלון בחירה.', 'פותחים "תרגיל חדש"'],
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
  B.intro(3.4);

  const example = (k, first) => {
    const E = D['c' + k];
    if (!first) { B.step(3 + k - 1 + (k > 1 ? 0 : 0)); }
    B.wait(.4);
    B.move('tb:new', .9).hot('#tbNew', .9).wait(.25).click(.3);
    B.dialog(`c${k}/s0`, 'תרגיל חדש', { wait: 1.0 });
    if (first) B.step(1).wait(.4);
    if (k === 2) { B.moveClick('dlg:chip:רשימה מקושרת', .8, .1).wait(.4); }
    if (k === 3) { B.moveClick('dlg:chip:עץ בינארי', .8, .1).wait(.4); }
    B.hl(B.area(['label:מה יש בכל תא', 'chip:רשימות מקושרות', 'chip:ערכים רגילים']), 2.0).wait(.4);
    const inside = k === 2 ? 'מחסניות' : 'תורים';
    B.moveClick(`dlg:chip:${inside}`, .8, .1).swap(`c${k}/s1`).wait(.8);
    if (first) {
      B.step(2);
      B.hl(B.area(['label:מבנים פנימיים'], 6), 2.2);
      B.bubble('כל קבוצה (עד ;) — מבנה פנימי אחד', 'dlg:label:מבנים פנימיים', { side: 'top', dur: 2.4, wait: 2.6 });
    }
    const dg = B.area(['label:כך ייבנה'], 10, 190);
    B.zoom(dg, .7, 1.4);
    B.bubble(`${E.label} — בכל תא מבנה שלם`, { x: dg.x + dg.w / 2, y: dg.y + 12 }, { kind: 'ok', side: 'top', dur: 2.4, wait: 2.7 });
    B.unzoom(.6);
    B.moveClick('dlg:round:יצירת התרגיל', .9, .25).closeDialog(.3);
    B.code(E.code, { file: 'Targil1.java' });
    B.scrollTo(Math.max(0, E.from - 6), .8).wait(.3);
    B.hl(`lines:${E.from}-${E.to}`, 2.4);
    B.bubble('בונים מבנה פנימי — ומכניסים אותו לחיצוני', `lines:${E.from}-${E.to}`, { side: 'left', dur: 2.6, wait: 3.0 });
    B.scrollTo(0, .5);
    B.move('tb:run', .9).hot('#tbRun', .7).wait(.25).click(.3);
    B.viewer(E.frames[0], `הרצה ויזואלית — ${E.label}`, { wait: .9 });
    B.steps(E.frames.slice(1), .7);
    B.hl('view:700,120,700,400', 2.4);
    B.bubble('רואים כל מבנה פנימי בתוך התא שלו', 'view:700,120,700,400', { kind: 'ok', side: 'left', dur: 2.4, wait: 2.8 });
    B.closeViewer(.4);
  };
  B.step(0).wait(.6);
  example(1, true);
  B.step(4); example(2, false);
  B.step(5); example(3, false);
  B.outro();
};
