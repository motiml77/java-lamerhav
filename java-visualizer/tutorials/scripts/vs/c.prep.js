// הדרכה ג (Visual Studio): מבנה בתוך מבנה — "תרגיל חדש ב-C#" (CsTool new) שלוש פעמים: מחסנית של תורים, רשימה של מחסניות, עץ של תורים
const fs = require('fs'), path = require('path');
const { freshRoot } = require('./common.prep');
module.exports = async ctx => {
  const root = freshRoot();
  const EX = [
    { id: 'c1', name: 'Targil1', label: 'מחסנית של תורים', pick: [], inside: 'תורים' },
    { id: 'c2', name: 'Targil2', label: 'רשימה מקושרת של מחסניות', pick: ['chip רשימה מקושרת'], inside: 'מחסניות' },
    { id: 'c3', name: 'Targil3', label: 'עץ בינארי של תורים', pick: ['chip עץ בינארי'], inside: 'תורים' },
  ];
  const out = {};
  for (const e of EX) {
    ctx.swing(e.id, ['new', root, '--preset', 'stack', '--no-open'],
      ['shot s0', ...e.pick, `chip ${e.inside}`, 'shot s1', 'click יצירת התרגיל', 'waitwin תרגיל חדש — C#', 'shot s2']);
    const code = fs.readFileSync(path.join(root, 'Exercises', e.name, 'Program.cs'), 'utf8').replace(/^﻿/, '').replace(/\r/g, '');
    const lines = code.split('\n');
    const from = lines.findIndex(l => l.includes('בונים את המבנה')) + 2;
    let to = lines.findIndex(l => l.includes('מדפיסים את המבנה לפני')) - 1;
    while (to > from && lines[to - 1].trim() === '') to--;
    const frames = ctx.viewerCs(e.id, { 'Program.cs': code }, [2, 6, 10, 14, 18, 22, 26, 30, 36], true);
    out[e.id] = { code: lines, from, to, frames, label: e.label, name: e.name, inside: e.inside };
  }
  return out;
};
