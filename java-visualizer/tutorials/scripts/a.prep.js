// הדרכה א: יצירת מבנה נתונים חדש — צילומי החלון "הוסף מבנה נתונים" וחלון ההרצה
const MAIN = [
  'import unit4.collectionsLib.*;',
  '',
  'public class Main {',
  '',
  '    public static void main(String[] args) {',
  '        // כאן יוצרים את המבנה ועובדים איתו',
  '        ',
  '',
  '    }',
  '}',
];
module.exports = async ctx => {
  const doc = MAIN.map((l, i) => i === 6 ? '        §' : l).join('\n') + '\n';
  const shots = ctx.dlg('add', 'ins', 'queue',
    ['shot a1', 'chip מחסנית', 'shot a2', 'check אקראיים', 'shot a3', 'spin 5', 'shot a4'], doc, '§');
  const texts = shots.a4.controls.filter(c => c.type === 'Text');
  const values = texts[0].text, name = texts[1].text || 'stk';
  const snippet = ctx.templates.snippet({ structure: 'stack', type: 'Integer', name, values, indent: '        ' }).split('\n');
  const code = MAIN.slice(0, 6).concat(snippet, MAIN.slice(7));
  const frames = ctx.viewer('run', { 'Main.java': code.join('\n') + '\n' }, 'Main', [1, 2, 3, 4, 5, 6, 7, 8, 9]);
  return { MAIN, code, newFrom: 7, newTo: 6 + snippet.length, values, name, frames };
};
