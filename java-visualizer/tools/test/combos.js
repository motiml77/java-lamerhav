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
      for (const flags of [0, 1, 2, 3, 4, 5, 6, 7, 8, 11, 17]) {
        if (returns === 'element' && type === 'Integer') continue;
        // קומבינציות (מבנה בתוך מבנה): רק טיפוסים רגילים, מבנה אחד, בלי ערכים אקראיים
        if (t.STRUCTURES[structure].combo && ((type === 'Student' || type === 'Car') || ![0, 2, 8].includes(flags))) continue;
        const o = { structure, type, returns, second: !!(flags & 1), extra: !!(flags & 2), random: !!(flags & 4), recursive: !!(flags & 8), count: flags & 16 ? 3 : flags & 1 ? 2 : 1,
                    values: t.defaultValues(structure, type, false), values2: t.defaultValues(structure, type, true), values3: t.defaultValues(structure, type, true),
                    className: 'C' + (++i), functionName: 'solve' };
        combos.push(['T', o.className, structure, type, returns, o.second, o.extra, o.random, o.values, o.values2, o.recursive, o.count, t.defaultValues(structure, type, true)].join('|'));
        write(o.className + '.java', () => { const g = t.generate(o); return g.code + '//cursor=' + g.cursorLine; });
      }
// קומבינציות עם ערכים אקראיים: "כמה מבנים פנימיים" (1, 5, 12) — כל אחד עם 2–4 ערכים; גם מתקמפל, וגם ההגרלה זהה ב-Java (RandomCheck)
const randoms = [];
for (const structure of Object.keys(t.STRUCTURES).filter(k => t.STRUCTURES[k].combo))
  for (const type of ['Integer', 'String', 'Character', 'Double'])
    for (const count of [1, 5, 12])
      for (const seed of [3, 99, 2024]) {
        const values = t.randomValues(structure, type, count, seed);
        const groups = values.split(';');
        if (groups.length !== count || groups.some(g => g.split(',').length < 2 || g.split(',').length > 4)) { console.log('BAD RANDOM', structure, type, count, seed, values); process.exit(1); }
        randoms.push([structure, type, count, seed, values].join('|'));
        if (seed !== 3) continue;
        const o = { structure, type, returns: 'void', second: false, extra: false, random: false, recursive: false, count: 1,
                    values, values2: '', values3: '', className: 'C' + (++i), functionName: 'solve' };
        combos.push(['T', o.className, structure, type, 'void', false, false, false, values, '', false, 1, ''].join('|'));
        write(o.className + '.java', () => { const g = t.generate(o); return g.code + '//cursor=' + g.cursorLine; });
      }
fs.writeFileSync(path.join(out, '..', 'randoms.txt'), randoms.join('\n') + '\n');
let k = 0;
for (const structure of Object.keys(t.SNIPPETS).concat(Object.keys(t.STRUCTURES).filter(k => t.STRUCTURES[k].combo)))
  for (const type of Object.keys(t.TYPES))
    for (const seed of [1, 7, 12345, -99]) {
      const name = 'S' + (++k);
      const o = { structure, type, name: t.snippetName(structure), size: 3 + (seed & 3), rows: 2 + (seed & 1), cols: 4, seed, indent: '        ' };
      combos.push(['S', name, structure, type, o.name, o.size, o.rows, o.cols, seed].join('|'));
      write(name + '.snippet', () => t.snippet(o));
    }
// "הוסף מבנה נתונים": קטעי קוד לפי ערכים שנבחרו — ברירת המחדל של המבנה, ו-5 ערכים אקראיים (2–4 בכל מבנה פנימי)
for (const structure of Object.keys(t.SNIPPETS).concat(Object.keys(t.STRUCTURES).filter(x => t.STRUCTURES[x].combo)))
  for (const type of Object.keys(t.TYPES))
    for (const mode of ['default', 'random']) {
      if (structure === 'student' || structure === 'car') continue;   // תלמיד / מכונית בודדים — בלי ערכים
      const obj = type === 'Student' || type === 'Car';
      const values = mode === 'default' ? t.defaultValues(structure, type, false) : obj ? '3' : t.randomValues(structure, type, 5, 7);
      const name = 'S' + (++k);
      const o = { structure, type, name: t.snippetName(structure), size: 5, rows: 3, cols: 4, seed: 1, indent: '        ', values };
      combos.push(['S', name, structure, type, o.name, o.size, o.rows, o.cols, o.seed, values].join('|'));
      write(name + '.snippet', () => t.snippet(o));
    }
fs.writeFileSync(path.join(out, '..', 'combos.txt'), combos.join('\n') + '\n');
// בדיקת שגיאות קלט
const bad = [['x, 2', 'Integer'], ['ab', 'Character'], ['1.2.3', 'Double'], ['null', 'Integer'], ['abc', 'Student'], ['50', 'Student'], ['abc', 'Car']];
for (const [v, ty] of bad) { try { t.literals(v, ty, false); console.log('MISSING ERROR', v); } catch (e) { console.log('ok error:', e.message); } }
console.log(combos.length + ' combos (' + errors + ' expected errors)');
