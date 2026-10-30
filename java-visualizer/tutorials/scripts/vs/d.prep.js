// הדרכה ד (Visual Studio): אחרי שהתרגיל נוצר — מוסיפים ידנית עוד פרמטרים (מבנה נוסף, משתנה x, מערך) ורואים אותם בחלון ההרצה
const { csTemplates: t } = require('./common.prep');
module.exports = async ctx => {
  const g = t.generate({ structure: 'stack', type: 'Integer', returns: 'int', second: false, extra: false, random: false, recursive: false, count: 1,
    values: t.defaultValues('stack', 'Integer', false), values2: '', className: 'Targil1', functionName: 'solve' });
  const v0 = g.code.split('\n');
  const idx = s => v0.findIndex(l => l.includes(s));
  const lSig = idx('public static int solve'), lTodo = idx('TODO'), lLast = idx('stk.Push(9)'), lCall = idx('int result = solve(stk)');
  const a = v0.slice();
  a[lSig] = '    public static int solve(Stack<int> stk, Queue<int> q, int x, int[] arr) {';
  const add = ['        Queue<int> q = new Queue<int>();   // מבנה נוסף שהפעולה מקבלת', '        q.Insert(3);', '        q.Insert(4);', '        int x = 7;                         // משתנה רגיל', '        int[] arr = { 6, 1, 4 };           // מערך'];
  const b = a.slice(0, lLast + 1).concat([''], add, a.slice(lLast + 1));
  const c = b.slice();
  const cCall = b.findIndex(l => l.includes('int result = solve(stk)'));
  c[cCall] = '        int result = solve(stk, q, x, arr);';
  const d = c.slice();
  d[lTodo + 1] = '        result = x + arr[1] + q.Head();   // משתמשים במה שהפעולה קיבלה';
  const frames = ctx.viewerCs('run', { 'Program.cs': d.join('\n') }, [3, 6, 9, 12, 15, 18, 21, 24, 27, 30, 34], true);
  return { v0, a, b, c, d, lSig: lSig + 1, lTodo: lTodo + 1, addFrom: lLast + 2, addTo: lLast + 2 + add.length, cCall: cCall + 1, frames };
};
