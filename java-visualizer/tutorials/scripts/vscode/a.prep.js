// הדרכה א ב-VS Code: "הוסף מבנה נתונים" — הדף האמיתי של התוסף (Webview) והקוד שנכנס, וחלון ההרצה
const path = require('path');
const ROOT = path.join(__dirname, '..', '..', '..');
const addstructure = require(path.join(ROOT, 'vscode-extension', 'addstructure'));
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
  const text = MAIN.join('\n') + '\n';
  const offset = MAIN.slice(0, 6).join('\n').length + 1 + 8;
  const session = new addstructure.Session({ tpl: ctx.templates, text, offset, indent: '        ', preset: 'queue' });
  let code = null;
  const host = msg => {
    if (msg.type === 'ok') { const v = session.view(msg.state); if (v.ok) code = v.code; return { type: 'view', view: v }; }
    if (msg.type === 'state') return { type: 'view', view: session.view(msg.state, msg.reason) };
    return null;
  };
  const html = addstructure.html(addstructure.options(ctx.templates, session.initial()));
  const shots = await ctx.web('add', html, host, [992, 781], [
    { wait: 400 }, { shot: 'a1' }, { clickText: ['#structs .chip', 'מחסנית'] }, { wait: 300 }, { shot: 'a2' },
    { fill: ['#count', '5'] }, { wait: 400 }, { shot: 'a4' }, { click: '#ok' }, { wait: 400 }]);
  const snippet = code.split('\n');
  const full = MAIN.slice(0, 6).concat(snippet, MAIN.slice(7));
  const frames = ctx.viewer('run', { 'Main.java': full.join('\n') + '\n' }, 'Main', [1, 2, 3, 4, 5, 6, 7, 8, 9]);
  return { MAIN, code: full, newFrom: 7, newTo: 6 + snippet.length, frames };
};
