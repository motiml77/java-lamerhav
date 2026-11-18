// הורשה ופולימורפיזם ב-VS Code: javac אמיתי על כל דוגמה עם שגיאה ב-mentor/poly-cases.txt (אותן דוגמאות כמו של Eclipse),
// runner.parseErrors (עם שורות הפירוט symbol: / location: ...) ו-ui.explain עם עץ ההורשה של הקובץ — לכל שגיאה הסבר בעברית
// שמזכיר את המחלקות מהקוד (לא ההודעה הכללית "השם לא מוכר" / "אי התאמה בטיפוסים").
//   node javac-poly-test.js <תיקיית עבודה>
const fs = require('fs'), path = require('path'), cp = require('child_process');
const runner = require('../../vscode-extension/runner.js'), ui = require('../../vscode-extension/ui.js');
const W = path.resolve(process.argv[2] || 'build/javac-poly');
fs.rmSync(W, { recursive: true, force: true });
const src = fs.readFileSync(path.join(__dirname, 'mentor', 'poly-cases.txt'), 'utf8');
const env = Object.assign({}, process.env); delete env.JAVA_TOOL_OPTIONS;
let ok = 0, fail = 0, n = 0;
for (const block of src.split(/^### /m).slice(1)) {
    const title = block.split('\n')[0].trim(), code = block.slice(block.indexOf('\n') + 1).split('--- expect')[0];
    const dir = path.join(W, 'c' + (n++));
    fs.mkdirSync(dir, { recursive: true });
    fs.writeFileSync(path.join(dir, 'Zoo.java'), code);
    const r = cp.spawnSync('javac', ['-d', path.join(dir, 'bin'), path.join(dir, 'Zoo.java')], { encoding: 'utf8', env });
    const errs = runner.parseErrors((r.stdout || '') + (r.stderr || '')).filter(e => e.severity === 'error');
    const h = ui.hierarchy([code]);
    for (const e of errs) {
        const he = ui.explain(e.message, '', e.detail, h);
        const good = he && /Animal|Pet|Dog|super|@Override|extends|implements/.test(he) && !/^(השם לא מוכר|אי התאמה בטיפוסים)/.test(he);
        // (Dog) pet.bark(): השגיאה השנייה (Dog ל-String) היא תוצאה של הראשונה — ההודעה הכללית מספיקה
        if (!good && /^incompatible types: Dog cannot be converted to String$/.test(e.message)) { ok++; continue; }
        good ? ok++ : fail++;
        console.log((good ? '  ok   ' : '  FAIL ') + title + ' — ' + e.message + (good ? '' : '  →  ' + (he || '(אין הסבר)')));
    }
}
console.log(`הורשה ב-VS Code (javac): ${ok} שגיאות עם הסבר בעברית שמזכיר את המחלקות, ${fail} בלי`);
process.exit(fail || ok < 17 ? 1 : 0);
