// יוצר את כל צירופי האפשרויות, ומפיק את הקוד בעזרת templates.js לתיקייה out-js
// (תרגילים מלאים + "הוספה לקוד"). ScaffoldAll.java מפיק את אותו הדבר מהגרסה של Eclipse, ומשווים.
const fs = require('fs'), path = require('path');
const t = require('../../vscode-extension/templates');
const out = process.argv[2];
fs.mkdirSync(out, { recursive: true });
const combos = [];
let i = 0, errors = 0;
const write = (name, f) => {
    let text;
    try { text = f(); } catch (e) { text = 'ERROR: ' + e.message; errors++; }
    fs.writeFileSync(path.join(out, name), text + '\n');
};
for (const structure of Object.keys(t.STRUCTURES))
  for (const type of Object.keys(t.TYPES))
    for (const returns of t.RETURNS)
      for (const flags of [0, 1, 2, 3, 4, 5, 6, 7]) {
        if (returns === 'element' && type === 'Integer') continue;
        const o = { structure, type, returns, second: !!(flags & 1), extra: !!(flags & 2), random: !!(flags & 4),
                    values: t.defaultValues(structure, type, false), values2: t.defaultValues(structure, type, true),
                    className: 'C' + (++i), functionName: 'solve' };
        combos.push(['T', o.className, structure, type, returns, o.second, o.extra, o.random, o.values, o.values2].join('|'));
        write(o.className + '.java', () => { const g = t.generate(o); return g.code + '//cursor=' + g.cursorLine; });
      }
let k = 0;
for (const structure of Object.keys(t.SNIPPETS))
  for (const type of Object.keys(t.TYPES))
    for (const seed of [1, 7, 12345, -99]) {
      const name = 'S' + (++k);
      const o = { structure, type, name: t.snippetName(structure), size: 3 + (seed & 3), rows: 2 + (seed & 1), cols: 4, seed, indent: '        ' };
      combos.push(['S', name, structure, type, o.name, o.size, o.rows, o.cols, seed].join('|'));
      write(name + '.snippet', () => t.snippet(o));
    }
fs.writeFileSync(path.join(out, '..', 'combos.txt'), combos.join('\n') + '\n');
// בדיקת שגיאות קלט
const bad = [['x, 2', 'Integer'], ['ab', 'Character'], ['1.2.3', 'Double'], ['null', 'Integer'], ['abc', 'Student'], ['50', 'Student'], ['abc', 'Car']];
for (const [v, ty] of bad) { try { t.literals(v, ty, false); console.log('MISSING ERROR', v); } catch (e) { console.log('ok error:', e.message); } }
console.log(combos.length + ' combos (' + errors + ' expected errors)');
