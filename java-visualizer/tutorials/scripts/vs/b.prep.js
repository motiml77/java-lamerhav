// הדרכה ב (Visual Studio): דוגמאות פתורות — הפתרון DataStructures-CS כבר מכיל אותן (Examples\...), וההרצה הוויזואלית של StackExamples
const fs = require('fs'), path = require('path');
const { ROOT } = require('./common.prep');
module.exports = async ctx => {
  const src = fs.readFileSync(path.join(ROOT, 'csharp', 'project', 'Examples', 'StackExamples', 'Program.cs'), 'utf8');
  const frames = ctx.viewerCs('run', { 'Program.cs': src }, [1, 4, 8, 12, 16, 20, 24, 28, 32, 36], true);
  return { code: src.replace(/\r/g, '').split('\n'), frames };
};
