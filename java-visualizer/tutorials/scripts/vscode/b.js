// הדרכה ב ב-VS Code: דוגמאות פתורות
window.META = {
  num: '02', title: 'דוגמאות פתורות', sub: '', goal: 'איך פותחים תרגיל פתור מוכן, קוראים אותו ומריצים אותו בחלון ההדמיה',
  pal: ['#059669', '#34d399', '#0ea5e9'], poster: 30,
  steps: [
    ['פותחים את הלוח "מבני נתונים"', 'בסרגל הצד של VS Code — הסמל הסגול. שם כל הכלים.', 'פותחים את הלוח "מבני נתונים"'],
    ['לוחצים "דוגמאות פתורות"', 'נפתחת רשימה של דוגמאות מוכנות.', 'לוחצים "דוגמאות פתורות"'],
    ['בוחרים דוגמה', 'לכל דוגמה כתוב מה יש בה. לוחצים עליה — והיא נפתחת.', 'בוחרים דוגמה'],
    ['קוראים את הקוד', 'כל פעולה פתורה, עם הערות. אפשר לקרוא, לשנות ולנסות.', 'קוראים את הקוד'],
    ['"הרצה ויזואלית"', 'לוחצים "הבא" ורואים כל שורה — וכל מה שקורה למחסנית.', 'מריצים ויזואלית'],
  ],
};

window.SCRIPT = B => {
  const D = window.DATA;
  const tree = sel => [[0, 'src'], [1, 'Main.java'], [1, 'examples'], [2, 'StackExamples.java', sel], [2, 'QueueExamples.java'], [2, 'ListExamples.java'], [0, 'lib']];
  B.files([[0, 'src'], [1, 'Main.java', true], [0, 'lib']]);
  B.code(['import unit4.collectionsLib.*;', '', 'public class Main {', '', '    public static void main(String[] args) {', '', '    }', '}'], { file: 'Main.java' });
  B.intro(2.8);

  B.step(0).wait(1.0);
  B.press(B.el('#actDs'), 'הלוח "מבני נתונים"', { hold: 1.6, side: 'right' }).side('dsviz').wait(.8);
  B.explain('el:#dsview', 'כל הכלים של התוסף נמצאים כאן', { side: 'right', dur: 2.8 });

  B.step(1);
  B.press('ds:examples', 'דוגמאות פתורות', { hold: 1.6, side: 'right' });
  const qx = 308 + Math.round((992 - 622) / 2);
  B.dialog('q1/q1', '', { bare: true, x: qx, y: 40, wait: .8 });
  B.zoom({ x: qx - 30, y: 0, w: 682, h: 680 }, .6, 1.3).wait(.3);
  B.explain(B.area(['label:איזו דוגמה', 'round:עציםTreeExamples.java']), '9 דוגמאות מוכנות — לכל נושא', { side: 'bottom', dur: 3.0 });

  B.step(2);
  B.swap('q2/q2').wait(.3);
  B.press('dlg:round:מחסניתStackExamples.java', 'בוחרים "מחסנית"', { hold: 1.4 }).closeDialog(.2).unzoom(.5);

  B.step(3);
  B.side('explorer').files(tree(true));
  B.code(D.code, { file: 'StackExamples.java', crumbs: 'src › examples', newFrom: 1, newTo: D.code.length }).wait(1.0);
  B.explain('lines:8-21', 'פעולה פתורה: היפוך מחסנית — עם הערות', { kind: 'ok', side: 'left', dur: 3.2 });
  B.scrollTo(21, .9).wait(.4);
  B.explain('lines:34-45', 'main — בונה מחסנית וקורא לפעולות', { side: 'left', dur: 3.0 });
  B.scrollTo(0, .7).wait(.3);

  B.step(4).wait(.4);
  B.press('sb:run', 'הרצה ויזואלית', { hold: 1.6 });
  B.viewer(D.frames[0], 'הרצה ויזואלית — StackExamples', { wait: 1.2 });
  B.press('view:next', 'כל לחיצה = צעד אחד', { hold: 1.6 }).frame(D.frames[1]).wait(.6);
  B.steps(D.frames.slice(2), .95);
  B.explain('view:900,180,400,300', 'רואים את ההיפוך קורה', { kind: 'ok', side: 'left', dur: 3.0 });
  B.outro();
};
