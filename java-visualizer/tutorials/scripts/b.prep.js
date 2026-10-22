// הדרכה ב: דוגמאות פתורות — החלון האמיתי, ואז ההרצה הוויזואלית של StackExamples
const fs = require('fs'), path = require('path');
module.exports = async ctx => {
  const src = fs.readFileSync(path.join(__dirname, '..', '..', 'src', 'examples', 'StackExamples.java'), 'utf8');
  ctx.dlg('ex', 'examples', '', ['shot b1', 'chip תור', 'shot b2', 'chip מחסנית', 'shot b3']);
  const frames = ctx.viewer('run', { 'src/examples/StackExamples.java': src }, 'examples.StackExamples', [1, 6, 12, 18, 26, 36, 48, 62, 80, 100]);
  return { code: src.split('\n'), frames };
};
