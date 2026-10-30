// הדרכה א (Visual Studio): הוסף מבנה נתונים ל-C# — החלון האמיתי (CsTool insert), הדבקה בקוד, וההרצה הוויזואלית (dsviz-cs)
const fs = require('fs'), path = require('path');
const { freshRoot, csTemplates } = require('./common.prep');
const MAIN = [
  'using System;',
  'using Unit4.CollectionsLib;',
  '',
  'class Program',
  '{',
  '    static void Main(string[] args)',
  '    {',
  '        // כאן יוצרים את המבנה ועובדים איתו',
  '        ',
  '',
  '    }',
  '}',
];
module.exports = async ctx => {
  const root = freshRoot();
  fs.writeFileSync(path.join(root, 'Exercise', 'Program.cs'), MAIN.join('\n') + '\n');   // הקובץ הפתוח — בודקים מולו שהשם פנוי
  const shots = ctx.swing('add', ['insert', root, '--preset', 'queue'],
    ['shot a1', 'chip מחסנית', 'shot a2', 'spin 5', 'shot a3', 'click העתקה ללוח', 'waitwin הוסף מבנה נתונים — C#', 'shot a4']);
  const texts = shots.a3.controls.filter(c => c.type === 'Text');
  const values = texts[0].text, name = texts[1].text || 'stk';
  const snippet = csTemplates.snippet({ structure: 'stack', type: 'Integer', name, values, indent: '        ' }).split('\n');
  const code = MAIN.slice(0, 8).concat(snippet, MAIN.slice(9));
  const frames = ctx.viewerCs('run', { 'Program.cs': code.join('\n') + '\n' }, [1, 2, 4, 6, 8, 10, 12, 14]);
  return { MAIN, code, newFrom: 9, newTo: 8 + snippet.length, values, name, frames };
};
