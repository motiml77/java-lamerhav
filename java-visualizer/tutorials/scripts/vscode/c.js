// הדרכה ג ב-VS Code: מבנה בתוך מבנה — שלוש דוגמאות
window.META = {
  num: '03', title: 'מבנה בתוך מבנה', sub: '', goal: 'איך יוצרים תרגיל שבו בכל תא של מבנה יש מבנה שלם — שלוש דוגמאות',
  pal: ['#db2777', '#fb923c', '#8b5cf6'], poster: 40,
  steps: [
    ['לוחצים "תרגיל חדש"', 'הכפתור + למעלה (או בלוח "מבני נתונים") — ובוחרים מבנה מהרשימה.', 'לוחצים "תרגיל חדש"'],
    ['"מה יש בכל תא?"', 'ברירת המחדל: ערכים רגילים. כאן בוחרים <b>מבנה שלם בכל תא</b> — תורים, מחסניות או רשימות.', 'בוחרים מה יש בכל תא'],
    ['כל קבוצה — מבנה פנימי', 'בשדה הערכים, כל קבוצה (עד <b>;</b>) היא מבנה פנימי אחד. מתחת לשדה רואים מה ייבנה.', 'כותבים את המבנים הפנימיים'],
    ['דוגמה 1 — מחסנית של תורים', 'התרגיל נוצר, וההרצה הוויזואלית מראה כל תור בתוך המחסנית.', 'דוגמה 1: מחסנית של תורים'],
    ['דוגמה 2 — רשימה של מחסניות', 'אותו דבר — רק שבוחרים "רשימה מקושרת" ו"מחסניות".', 'דוגמה 2: רשימה של מחסניות'],
    ['דוגמה 3 — עץ של תורים', 'גם עץ בינארי: בכל צומת — תור שלם.', 'דוגמה 3: עץ של תורים'],
  ],
};

window.SCRIPT = B => {
  const D = window.DATA;
  B.files([[0, 'src'], [1, 'Main.java', true], [0, 'lib']]);
  B.code(['import unit4.collectionsLib.*;', '', 'public class Main {', '', '    public static void main(String[] args) {', '', '    }', '}'], { file: 'Main.java' });
  B.intro(2.8);
  const QX = 308 + Math.round((992 - 622) / 2), QY = 40;
  // שורה ברשימה של VS Code (לפי ההתחלה של הטקסט שלה)
  const row = (asset, starts) => 'dlg:round:' + window.ASSETS[asset].controls.find(c => c.type === 'Round' && c.text.replace('✓', '').startsWith(starts)).text;
  const enter = (text, dur = 1.6) => B.bubble(`<span class="key">Enter</span> ${text}`, { x: QX + 311, y: QY + 74 }, { side: 'bottom', kind: 'click', dur, wait: dur });

  const example = (k, first) => {
    const E = D['c' + k], id = 'c' + k;
    B.wait(.4);
    B.press('tb:new', 'תרגיל חדש', { hold: 1.4 });
    B.dialog(`${id}w1/w1`, '', { bare: true, x: QX, y: QY, wait: .6 });
    B.zoom({ x: QX - 40, y: 0, w: 702, h: 680 }, .6, 1.3).wait(.2);
    B.press(row(`${id}w1/w1`, E.outerLabel), `בוחרים: ${E.outerLabel}`, { hold: 1.2 });
    B.wait(.35).swap(`${id}w2/w2`).wait(.5);
    if (first) {
      B.step(1).wait(.3);
      B.explain(row(`${id}w2/w2`, 'ערכים רגילים'), 'ברירת המחדל: ערכים רגילים בכל תא', { side: 'bottom', dur: 2.6 });
    }
    B.press(row(`${id}w2/w2`, E.innerLabel), `בוחרים: ${E.innerLabel} בכל תא`, { hold: 1.4 });
    B.wait(.35);
    for (const n of ['w3', 'w4', 'w5', 'w6']) { B.swap(`${id}${n}/${n}`).wait(first ? .5 : .25); if (first && n === 'w5')  B.explain(row(`${id}w5/w5`, 'כמה מבנים'), 'מבנים פנימיים אקראיים — כבר מסומן', { side: 'bottom', dur: 2.4 }); enter(first ? 'ברירת המחדל' : '', first ? 1.2 : .6); }
    B.swap(`${id}w7/w7`).wait(.4);
    if (first) {
      B.step(2);
      B.explain('dlg:label:ערכים מופרדים', 'כל קבוצה (עד ;) היא מבנה פנימי אחד — אפשר לשנות את הערכים', { side: 'bottom', dur: 3.2 });
    }
    B.explain(window.ASSETS[`${id}w7/w7`].controls.find(c => c.type === 'Label' && /←/.test(c.text)) ? `dlg:label:${window.ASSETS[`${id}w7/w7`].controls.find(c => c.type === 'Label' && /←/.test(c.text)).text.slice(0, 6)}` : 'dlg:text:0',
      `כך ייבנה: ${E.label}`, { kind: 'ok', side: 'bottom', dur: 2.8 });
    enter('', .7);
    B.swap(`${id}w8/w8`).wait(.3); enter(first ? 'שם המחלקה — Targil1' : '', first ? 1.2 : .6);
    B.swap(`${id}w9/w9`).wait(.3); enter(first ? 'שם הפעולה — solve' : '', first ? 1.2 : .6);
    B.closeDialog(.2).unzoom(.5);
    if (first) B.step(3);
    B.files([[0, 'src'], [1, 'Main.java'], [1, 'Targil1.java', true], [0, 'lib']]);
    B.code(E.code, { file: 'Targil1.java' });
    B.scrollTo(Math.max(0, E.from - 6), .8).wait(.3);
    B.explain(`lines:${E.from}-${E.to}`, 'בונים מבנה פנימי — ומכניסים אותו לחיצוני', { side: 'left', dur: 3.2 });
    B.scrollTo(0, .5);
    B.press('sb:run', 'הרצה ויזואלית', { hold: 1.4 });
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
