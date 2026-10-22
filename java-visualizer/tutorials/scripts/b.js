// הדרכה ב: דוגמאות פתורות — פותחים דוגמה מוכנה ומריצים אותה בחלון ההדמיה
window.META = {
  num: '02', title: 'דוגמאות פתורות', sub: 'תרגילים מוכנים — פותחים, קוראים, ורואים איך הם עובדים',
  pal: ['#059669', '#34d399', '#0ea5e9'], poster: 30,
  outro: 'דוגמאות פתורות · הרצה ויזואלית · לומדים מהקוד',
  steps: [
    ['פותחים את התפריט', 'בתפריט העליון <b>מבני נתונים</b> — יש שם את כל הכלים.', 'תפריט "מבני נתונים"'],
    ['"דוגמאות פתורות"', 'מכל הפעולות — רשימת דוגמאות מוכנות: מחסנית, תור, רשימה, עצים, מערכים, רקורסיה, מבנה בתוך מבנה...', 'דוגמאות פתורות'],
    ['בוחרים דוגמה', 'לכל דוגמה כתוב מה יש בה. לוחצים <b>פתיחת הדוגמה</b>.', 'בוחרים דוגמה'],
    ['הקוד נפתח — עם הסברים', 'כל פעולה פתורה, עם הערות. אפשר לקרוא, לשנות ולנסות.', 'קוראים את הקוד'],
    ['"הרצה ויזואלית"', 'לוחצים "הבא" ורואים כל שורה — וכל מה שקורה למחסנית.', 'מריצים ויזואלית'],
  ],
};

window.SCRIPT = B => {
  const D = window.DATA;
  B.files([[0, 'DataStructures'], [1, 'src'], [2, '(default package)'], [3, 'Main.java'], [2, 'examples'], [3, 'StackExamples.java'], [3, 'QueueExamples.java'], [3, 'ListExamples.java']]);
  B.code(['import unit4.collectionsLib.*;', '', 'public class Main {', '', '    public static void main(String[] args) {', '', '    }', '}'], { file: 'Main.java' });
  B.intro(3.4);

  // 1. התפריט
  B.step(0).wait(1.0);
  B.move('menu', 1.0).hot('#nothing', .1).wait(.2).click(.25).menuOpen().wait(.8);
  B.bubble('כל הכלים במקום אחד', 'menuitem:הוסף מבנה', { side: 'right', dur: 2.4, wait: 2.8 });

  // 2. דוגמאות פתורות
  B.step(1);
  B.move('menuitem:דוגמאות פתורות', .9).menuHot('דוגמאות פתורות').wait(.4);
  B.click(.3).menuClose(.2);
  B.dialog('ex/b1', 'דוגמאות פתורות', { wait: 1.4 });
  B.hl(B.area(['label:איזו דוגמה', 'chip:מבנה בתוך מבנה', 'chip:מחסנית']), 2.4);
  B.bubble('9 דוגמאות מוכנות', 'dlg:label:איזו דוגמה', { side: 'top', dur: 2.4, wait: 3.0 });

  // 3. בוחרים
  B.step(2);
  B.moveClick('dlg:chip:מחסנית', .9, .15).swap('ex/b3').wait(.6);
  B.bubble('היפוך, הכנסה לתחתית (רקורסיה)', 'dlg:label:בדוגמה', { kind: 'ok', side: 'top', dur: 2.6, wait: 3.0 });
  B.moveClick('dlg:round:פתיחת הדוגמה', .9, .25).closeDialog(.3);

  // 4. הקוד נפתח
  B.step(3);
  B.files([[0, 'DataStructures'], [1, 'src'], [2, '(default package)'], [3, 'Main.java'], [2, 'examples'], [3, 'StackExamples.java', true], [3, 'QueueExamples.java'], [3, 'ListExamples.java']]);
  B.code(D.code, { file: 'StackExamples.java', newFrom: 1, newTo: D.code.length }).wait(1.0);
  B.hl('lines:8-21', 2.6);
  B.bubble('פעולה פתורה: היפוך מחסנית', 'lines:8-21', { kind: 'ok', side: 'left', dur: 2.6, wait: 3.0 });
  B.scrollTo(21, .9).wait(.4);
  B.hl('lines:34-45', 2.2);
  B.bubble('main — בונה מחסנית וקורא לפעולות', 'lines:34-45', { side: 'left', dur: 2.4, wait: 2.8 });
  B.scrollTo(0, .7).wait(.3);

  // 5. הרצה ויזואלית
  B.step(4).wait(.4);
  B.move('tb:run', 1.0).hot('#tbRun', .8).wait(.3).click(.3);
  B.viewer(D.frames[0], 'הרצה ויזואלית — StackExamples', { wait: 1.0 });
  B.bubble('"הבא" — צעד אחרי צעד', 'view:next', { side: 'bottom', dur: 2.0, wait: 1.6 });
  B.steps(D.frames.slice(1), .8);
  B.bubble('רואים את ההיפוך קורה', 'view:900,180,400,300', { kind: 'ok', side: 'left', dur: 2.6, wait: 3.0 });
  B.outro();
};
