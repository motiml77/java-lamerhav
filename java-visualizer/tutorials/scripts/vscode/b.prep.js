// הדרכה ב ב-VS Code: דוגמאות פתורות — רשימת הבחירה של VS Code (QuickPick), ואז ההרצה הוויזואלית של StackExamples
const fs = require('fs'), path = require('path');
const qp = require('../../harness/quickpick');
const ITEMS = [['מערכים (חד ודו ממדיים)', 'ArrayExamples.java'], ['מכוניות (Car)', 'CarExamples.java'], ['רשימה מקושרת', 'ListExamples.java'],
  ['מבנה בתוך מבנה (מחסנית של מחסניות...)', 'NestedExamples.java'], ['תור', 'QueueExamples.java'], ['רקורסיה (עצרת, מערך, מחסנית)', 'RecursionExamples.java'],
  ['מחסנית', 'StackExamples.java'], ['תלמידים (Student)', 'StudentExamples.java'], ['עצים', 'TreeExamples.java']].map(([label, description]) => ({ label, description }));
module.exports = async ctx => {
  const src = fs.readFileSync(path.join(__dirname, '..', '..', '..', 'src', 'examples', 'StackExamples.java'), 'utf8');
  const h = qp.size({ items: ITEMS })[1];
  for (const [n, active] of [['q1', 0], ['q2', 6]])
    await ctx.web(n, qp.html({ title: 'איזו דוגמה לפתוח?', placeholder: 'הקלידו כדי לחפש', items: ITEMS, active }), null, [622, h], [{ wait: 200 }, { shot: n }]);
  const frames = ctx.viewer('run', { 'src/examples/StackExamples.java': src }, 'examples.StackExamples', [1, 6, 12, 18, 26, 36, 48, 62, 80, 100]);
  return { code: src.split('\n'), frames };
};
