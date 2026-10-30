// משותף להדרכות של Visual Studio: תיקיית תרגילים נקייה (כמו זו שההתקנה יוצרת) לצילום החלונות של C#
const fs = require('fs'), path = require('path');
const ROOT = path.join(__dirname, '..', '..', '..');
const VSROOT = '/home/user/Documents/DataStructures-CS';   // נתיב קצר וקריא — מופיע בחלון "תרגיל חדש"
function freshRoot() {
  fs.rmSync(VSROOT, { recursive: true, force: true });
  fs.mkdirSync(path.dirname(VSROOT), { recursive: true });
  fs.cpSync(path.join(ROOT, 'csharp', 'project'), VSROOT, { recursive: true, filter: s => !/[\\/](bin|obj)$/.test(s) });
  return VSROOT;
}
const csTemplates = require(path.join(ROOT, 'vscode-extension', 'templates-cs'));
module.exports = { freshRoot, VSROOT, ROOT, csTemplates };
