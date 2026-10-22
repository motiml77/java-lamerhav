// בודק את names.js מול names-cases.txt (אותו קובץ נבדק גם בג'אווה: NameCheckTest.java)
const fs = require('fs'), path = require('path');
const names = require('../../vscode-extension/names');
const javaT = require('../../vscode-extension/templates'), csT = require('../../vscode-extension/templates-cs');
const raw = fs.readFileSync(path.join(__dirname, 'names-cases.txt'), 'utf8').split('\n').filter(l => !l.startsWith('#')).join('\n');
let bad = 0, n = 0;
for (const block of raw.split(/^====$/m)) {
    const lines = block.replace(/^\n+/, '').split('\n');
    const [name, expect, why] = lines[0].split('|');
    const code = lines.slice(1).join('\n');
    const offset = code.indexOf('§');
    const text = code.replace('§', '');
    const cs = text.includes('using System');
    const isId = cs ? csT.isIdentifier : javaT.isIdentifier;
    if (name === '@inside') {
        n++;
        const got = names.insideFunction(text, offset) ? 'yes' : 'no';
        if (got !== expect) { bad++; console.log(`נכשל: ${why}: ציפינו ${expect}, קיבלנו ${got}`); }
        continue;
    }
    const err = names.checkName(name, text, offset, isId);
    const got = err === null ? 'ok' : !isId(name) || names.RESERVED_NAMES.has(name) ? 'bad' : 'taken';
    n++;
    if (got !== expect) { bad++; console.log(`נכשל: ${name} — ${why}: ציפינו ${expect}, קיבלנו ${got} (${err})`); }
}
if (bad) { console.log(bad + ' מתוך ' + n + ' נכשלו'); process.exit(1); }
console.log(n + ' בדיקות שם: תקין');
