// כמו combos.js, אבל מפיק קוד C# (templates-cs.js) — תרגילים מלאים וקטעי "הוספה לקוד", לתיקייה out-cs
const fs = require('fs'), path = require('path');
const t = require('../../vscode-extension/templates-cs');
const out = process.argv[2];
fs.mkdirSync(out, { recursive: true });
let i = 0, errors = 0, written = 0;
const write = (name, f) => {
    let text;
    try { text = f(); } catch (e) { errors++; return; }
    fs.writeFileSync(path.join(out, name), text + '\n');
    written++;
};
for (const structure of Object.keys(t.STRUCTURES))
  for (const type of Object.keys(t.TYPES))
    for (const returns of t.RETURNS)
      for (const flags of [0, 1, 2, 3, 4, 5, 6, 7, 8, 11, 17]) {
        if (returns === 'element' && type === 'Integer') continue;
        // קומבינציות (מבנה בתוך מבנה): רק טיפוסים רגילים, מבנה אחד, בלי ערכים אקראיים
        if (t.STRUCTURES[structure].combo && ((type === 'Student' || type === 'Car') || ![0, 2, 8].includes(flags))) continue;
        const o = { structure, type, returns, second: !!(flags & 1), extra: !!(flags & 2), random: !!(flags & 4), recursive: !!(flags & 8), count: flags & 16 ? 3 : flags & 1 ? 2 : 1,
                    values: t.defaultValues(structure, type, false), values2: t.defaultValues(structure, type, true), values3: t.defaultValues(structure, type, true),
                    className: 'C' + (++i), functionName: 'solve' };
        write(o.className + '.cs', () => t.generate(o).code);
      }
// קומבינציות עם ערכים אקראיים (אותו סדר ומספור כמו ב-combos.js)
for (const structure of Object.keys(t.STRUCTURES).filter(k => t.STRUCTURES[k].combo))
  for (const type of ['Integer', 'String', 'Character', 'Double'])
    for (const count of [1, 5, 12])
      for (const seed of [3, 99, 2024]) {
        if (seed !== 3) continue;
        const o = { structure, type, returns: 'void', second: false, extra: false, random: false, recursive: false, count: 1,
                    values: t.randomValues(structure, type, count, seed), values2: '', values3: '', className: 'C' + (++i), functionName: 'solve' };
        write(o.className + '.cs', () => t.generate(o).code);
      }
let k = 0;
for (const structure of Object.keys(t.SNIPPETS).concat(Object.keys(t.STRUCTURES).filter(k => t.STRUCTURES[k].combo)))
  for (const type of Object.keys(t.TYPES))
    for (const seed of [1, 7, 12345, -99]) {
      const name = 'S' + (++k);
      const o = { structure, type, name: t.snippetName(structure), size: 3 + (seed & 3), rows: 2 + (seed & 1), cols: 4, seed, indent: '        ' };
      write(name + '.snippet', () => t.snippet(o));
    }
console.log(written + ' קבצי C# (' + errors + ' שגיאות צפויות)');
