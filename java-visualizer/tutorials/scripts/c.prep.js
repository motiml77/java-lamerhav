// הדרכה ג: תרגיל עם מבנה בתוך מבנה — שלוש דוגמאות (מחסנית של תורים, רשימה של מחסניות, עץ של תורים)
module.exports = async ctx => {
  const t = ctx.templates;
  const EX = [
    { id: 'c1', st: 'stack_queue', script: ['shot s0', 'chip תורים', 'shot s1'] },
    { id: 'c2', st: 'list_stack', script: ['shot s0', 'chip רשימה מקושרת', 'chip מחסניות', 'shot s1'] },
    { id: 'c3', st: 'tree_queue', script: ['shot s0', 'chip עץ בינארי', 'chip תורים', 'shot s1'] },
  ];
  const out = {};
  for (const e of EX) {
    ctx.dlg(e.id, 'ex', 'stack', e.script);
    const g = t.generate({ structure: e.st, type: 'Integer', returns: 'void', second: false, extra: false, random: false, recursive: false, count: 1,
      values: t.defaultValues(e.st, 'Integer', false), values2: '', className: 'Targil1', functionName: 'solve' });
    const lines = g.code.split('\n');
    const from = lines.findIndex(l => l.includes('בונים את המבנה')) + 2;   // שורה (מ-1) אחרי ההערה
    let to = lines.findIndex(l => l.includes('מדפיסים את המבנה לפני')) - 1;
    while (to > from && lines[to - 1].trim() === '') to--;
    const frames = ctx.viewer(e.id, { 'Targil1.java': g.code }, 'Targil1', [2, 5, 8, 11, 14, 17, 20, 24], true);
    out[e.id] = { code: lines, from, to, frames, st: e.st, label: t.STRUCTURES[e.st].label };
  }
  return out;
};
