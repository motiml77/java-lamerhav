// הדרכה ב: דוגמאות פתורות
window.META = {
  num: '02', title: 'דוגמאות פתורות', sub: '', goal: 'איך פותחים תרגיל פתור מוכן, קוראים אותו ומריצים אותו בחלון ההדמיה',
  pal: ['#059669', '#34d399', '#0ea5e9'], poster: 30,
  steps: [
    ['פותחים את התפריט', 'בתפריט העליון <b>מבני נתונים</b> יש את כל הכלים.', 'פותחים את התפריט "מבני נתונים"'],
    ['לוחצים "דוגמאות פתורות"', 'נפתח חלון עם רשימת דוגמאות מוכנות.', 'לוחצים "דוגמאות פתורות"'],
    ['בוחרים דוגמה', 'לכל דוגמה כתוב מה יש בה. אחר כך לוחצים "פתיחת הדוגמה".', 'בוחרים דוגמה ופותחים'],
    ['קוראים את הקוד', 'כל פעולה פתורה, עם הערות. אפשר לקרוא, לשנות ולנסות.', 'קוראים את הקוד'],
    ['"הרצה ויזואלית"', 'לוחצים "הבא" ורואים כל שורה — וכל מה שקורה למחסנית.', 'מריצים ויזואלית'],
  ],
};

window.SCRIPT = B => {
  const D = window.DATA;
  B.files([[0, 'DataStructures'], [1, 'src'], [2, '(default package)'], [3, 'Main.java'], [2, 'examples'], [3, 'StackExamples.java'], [3, 'QueueExamples.java'], [3, 'ListExamples.java']]);
  B.code(['import unit4.collectionsLib.*;', '', 'public class Main {', '', '    public static void main(String[] args) {', '', '    }', '}'], { file: 'Main.java' });
  B.intro(2.8);

  B.step(0).wait(1.0);
  B.press('menu', 'התפריט "מבני נתונים"', { hold: 1.6 }).menuOpen().wait(.8);
  B.explain('menuitem:הוסף מבנה', 'כל הכלים של התוסף נמצאים כאן', { side: 'right', dur: 2.8 });

  B.step(1);
  B.menuHot('דוגמאות פתורות');
  B.press('menuitem:דוגמאות פתורות', 'דוגמאות פתורות', { hold: 1.6 }).menuClose(.2);
  B.dialog('ex/b1', 'דוגמאות פתורות', { wait: 1.4 });
  B.explain(B.area(['label:איזו דוגמה', 'chip:מבנה בתוך מבנה', 'chip:מחסנית']), '9 דוגמאות מוכנות — לכל נושא', { side: 'top', dur: 3.0 });

  B.step(2);
  B.press('dlg:chip:מחסנית', 'בוחרים דוגמה', { hold: 1.4 }).swap('ex/b3').wait(.6);
  B.press('dlg:round:פתיחת הדוגמה', 'פותחים את הדוגמה', { hold: 1.4 }).closeDialog(.3);

  B.step(3);
  B.files([[0, 'DataStructures'], [1, 'src'], [2, '(default package)'], [3, 'Main.java'], [2, 'examples'], [3, 'StackExamples.java', true], [3, 'QueueExamples.java'], [3, 'ListExamples.java']]);
  B.code(D.code, { file: 'StackExamples.java', newFrom: 1, newTo: D.code.length }).wait(1.0);
  B.explain('lines:8-21', 'פעולה פתורה: היפוך מחסנית — עם הערות', { kind: 'ok', side: 'left', dur: 3.2 });
  B.scrollTo(21, .9).wait(.4);
  B.explain('lines:34-45', 'main — בונה מחסנית וקורא לפעולות', { side: 'left', dur: 3.0 });
  B.scrollTo(0, .7).wait(.3);

  B.step(4).wait(.4);
  B.press('tb:run', 'הרצה ויזואלית', { hold: 1.6 });
  B.viewer(D.frames[0], 'הרצה ויזואלית — StackExamples', { wait: 1.2 });
  B.press('view:next', 'כל לחיצה = צעד אחד', { hold: 1.6 }).frame(D.frames[1]).wait(.6);
  B.steps(D.frames.slice(2), .95);
  B.explain('view:900,180,400,300', 'רואים את ההיפוך קורה', { kind: 'ok', side: 'left', dur: 3.0 });
  B.outro();
};
