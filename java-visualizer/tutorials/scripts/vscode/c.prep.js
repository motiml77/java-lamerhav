// הדרכה ג ב-VS Code: מבנה בתוך מבנה — "תרגיל חדש" של VS Code (רשימות בחירה של VS Code), שלוש דוגמאות, והקוד וההרצה האמיתיים
const qp = require('../../harness/quickpick');
const EX = [
  { id: 'c1', outer: 'stack', inner: 'queue', st: 'stack_queue', seed: 7 },
  { id: 'c2', outer: 'list', inner: 'stack', st: 'list_stack', seed: 11 },
  { id: 'c3', outer: 'tree', inner: 'queue', st: 'tree_queue', seed: 5 },
];
module.exports = async ctx => {
  const t = ctx.templates;
  const outerItems = Object.entries(t.STRUCTURES).filter(([, s]) => !s.combo).map(([id, s]) => ({ id, label: s.label, description: s.cls || '' }));
  const out = {};
  for (const e of EX) {
    const o = t.STRUCTURES[e.outer], st = t.STRUCTURES[e.st];
    const insideItems = t.INSIDE.map(i => ({ id: i.id, label: i.label, description: i.id ? `${o.label} של ${i.label} — ${t.typeOf(t.STRUCTURES[t.comboId(e.outer, i.id)], 'Integer')}` : i.description }));
    const values = t.randomValues(e.st, 'Integer', 3, e.seed);
    const isTree = e.outer === 'tree';
    const hint = `  (כל קבוצה, עד ;, היא מבנה פנימי אחד${isTree ? ' — לפי רמות, null = אין צומת' : ''})`;
    const W = [
      ['w1', { title: 'תרגיל חדש — איזה מבנה נתונים?', placeholder: 'בחרו מבנה נתונים', items: outerItems, active: outerItems.findIndex(x => x.id === e.outer) }],
      ['w2', { title: `${o.label} — מה יש בכל תא?`, placeholder: 'Enter = ערכים רגילים. או בחרו מבנה שלם בכל תא (מחסנית של מחסניות...)', items: insideItems, active: insideItems.findIndex(x => x.id === e.inner) }],
      ['w3', { title: `${st.label} — (1/6) טיפוס הערכים`, placeholder: t.typeOf(st, '?'), items: [{ label: 'Integer', description: 'מספרים שלמים' }, { label: 'String', description: 'מחרוזות' }, { label: 'Character', description: 'תווים' }, { label: 'Double', description: 'מספרים עשרוניים' }], active: 0 }],
      ['w4', { title: `${st.label} — (2/6) מה הפעולה מחזירה?`, items: [{ label: 'void', description: 'לא מחזירה ערך (למשל משנה את המבנה)' }, { label: 'int', description: 'מחזירה מספר (למשל סכום, כמות)' }, { label: 'boolean', description: 'מחזירה אמת/שקר (למשל "האם קיים")' }, { label: t.typeOf(st, 'Integer'), description: 'מחזירה מבנה חדש' }], active: 0 }],
      ['w5', { title: `${st.label} — (3/6) רקורסיה ומילוי המבנה (אפשר לדלג עם Enter)`, many: true, items: [{ label: 'תרגיל ברקורסיה', description: 'בחלון ההרצה תופיע גם לשונית "רקורסיה" — כרטיס לכל קריאה' }, { label: 'כמה מבנים פנימיים אקראיים', description: 'מילוי המבנה: בוחרים כמה — ונוצרים מבנים פנימיים עם ערכים אקראיים', picked: true }], active: 1 }],
      ['w6', { title: `${st.label} — כמה מבנים פנימיים אקראיים (2–4 ערכים בכל אחד)?`, value: '3', prompt: "Press 'Enter' to confirm or 'Escape' to cancel" }],
      ['w7', { title: `${st.label} — (4/6) מילוי מבנה-נתונים — ערכים ל-${st.varName}${hint}`, value: values, prompt: 'ערכים מופרדים בפסיקים; מבנים פנימיים מופרדים ב-; (למשל 5, 2; 8, 1, 9; 3)', message: t.comboPreview(e.st, 'Integer', values).replace('\n', '   ←   ') }],
      ['w8', { title: `${st.label} — (5/6) שם המחלקה (הקובץ)`, value: 'Targil1', prompt: "Press 'Enter' to confirm or 'Escape' to cancel" }],
      ['w9', { title: `${st.label} — (6/6) שם הפעולה שכותבים`, value: 'solve', prompt: "Press 'Enter' to confirm or 'Escape' to cancel" }],
    ];
    for (const [n, w] of W) await ctx.web(`${e.id}${n}`, qp.html(w), null, qp.size(w), [{ wait: 150 }, { shot: n }]);
    const g = t.generate({ structure: e.st, type: 'Integer', returns: 'void', second: false, extra: false, random: false, recursive: false, count: 1,
      values, values2: '', className: 'Targil1', functionName: 'solve' });
    const lines = g.code.split('\n');
    const from = lines.findIndex(l => l.includes('בונים את המבנה')) + 2;
    let to = lines.findIndex(l => l.includes('מדפיסים את המבנה לפני')) - 1;
    while (to > from && lines[to - 1].trim() === '') to--;
    const frames = ctx.viewer(e.id, { 'Targil1.java': g.code }, 'Targil1', [2, 5, 8, 11, 14, 17, 20, 24], true);
    out[e.id] = { code: lines, from, to, frames, label: st.label, outerLabel: o.label, innerLabel: t.INSIDE.find(i => i.id === e.inner).label, cursor: g.cursorLine };
  }
  return out;
};
