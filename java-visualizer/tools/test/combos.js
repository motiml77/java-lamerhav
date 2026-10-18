// יוצר את כל צירופי האפשרויות, ומפיק את הקוד בעזרת templates.js לתיקייה out-js
const fs = require('fs'), path = require('path');
const t = require('../../vscode-extension/templates');
const out = process.argv[2];
fs.mkdirSync(out, { recursive: true });
const combos = [];
let i = 0;
for (const structure of Object.keys(t.STRUCTURES))
  for (const type of Object.keys(t.TYPES))
    for (const returns of t.RETURNS)
      for (const flags of [0, 1, 2, 3, 4, 5, 6, 7]) {
        if (returns === 'element' && type === 'Integer') continue;
        const o = { structure, type, returns, second: !!(flags & 1), extra: !!(flags & 2), random: !!(flags & 4),
                    values: t.defaultValues(structure, type, false), values2: t.defaultValues(structure, type, true),
                    className: 'C' + (++i), functionName: 'solve' };
        combos.push(o);
        const g = t.generate(o);
        fs.writeFileSync(path.join(out, o.className + '.java'), g.code + '//cursor=' + g.cursorLine + '\n');
      }
fs.writeFileSync(path.join(out, '..', 'combos.txt'), combos.map(o =>
  [o.className, o.structure, o.type, o.returns, o.second, o.extra, o.random, o.values, o.values2].join('|')).join('\n') + '\n');
// בדיקת שגיאות קלט
const bad = [['x, 2', 'Integer'], ['ab', 'Character'], ['1.2.3', 'Double'], ['null', 'Integer']];
for (const [v, ty] of bad) { try { t.literals(v, ty, false); console.log('MISSING ERROR', v); } catch (e) { console.log('ok error:', e.message); } }
console.log(t.literals('a\\b, "q"', 'String', false).join(' '), t.literals("', \\", 'Character', false).join(' '));
console.log(combos.length + ' combos');
