// בדיקת התאמה: site/assets/scaffold.js (באתר) מול AiScaffold.java (בתוסף) — אותו קלט, אותם קבצים, תו בתו.
// מקמפל ומריץ את ScaffoldDump.java על כל ה-JSON ב-ai, ai/cs, ai/parity (ועל ai/repair — סבב תיקון),
// מריץ את אותו הדבר ב-JS, ומשווה: read, build בלי הצעות / עם הצעות / עם return לבדיקות, ושמות תיקיות.
//   node tools/test/scaffold-parity.js [תיקיית-עבודה]
const fs = require('fs'), path = require('path'), os = require('os');
const { execFileSync } = require('child_process');
const S = require('../../site/assets/scaffold.js');

const here = __dirname, repo = path.join(here, '..', '..');
const work = path.resolve(process.argv[2] || fs.mkdtempSync(path.join(os.tmpdir(), 'scaffold-parity-')));
const cls = path.join(work, 'cls'), out = path.join(work, 'java');
for (const d of [cls, out]) fs.rmSync(d, { recursive: true, force: true });
fs.mkdirSync(cls, { recursive: true });
const src = path.join(repo, 'eclipse-plugin', 'src', 'il', 'lamerhav', 'dsviz');
const ai = path.join(here, 'ai');
const dirs = [ai, path.join(ai, 'cs'), path.join(ai, 'parity')];
const folderCases = path.join(ai, 'parity', 'folder-names.cases');   // JSON: [[topic, [תפוסים], cs], ...]
execFileSync('javac', ['-encoding', 'UTF-8', '-d', cls, path.join(src, 'Json.java'), path.join(src, 'AiScaffold.java'), path.join(here, 'ScaffoldDump.java')], { stdio: 'inherit' });
execFileSync('java', ['-cp', cls, 'il.lamerhav.dsviz.ScaffoldDump', out, folderCases, ...dirs], { stdio: 'inherit' });

const field = (name, value) => {
    if (value == null) value = '<null>';
    return name + ' ' + value.length + '\n' + value + '\n';
};
const readJson = f => JSON.parse(fs.readFileSync(f, 'utf8'));
let fails = 0, n = 0;
const compare = (file, label, mine) => {
    n++;
    const p = path.join(out, file);
    const java = fs.existsSync(p) ? fs.readFileSync(p) : null;
    const js = Buffer.from(mine, 'utf8');
    if (java && java.equals(js)) { console.log('  ok   ' + label); return; }
    fails++;
    console.log('  FAIL ' + label);
    if (!java) { console.log('       (אין פלט מ-Java: ' + p + ')'); return; }
    const a = java.toString('utf8').split('\n'), b = mine.split('\n');
    for (let i = 0; i < Math.max(a.length, b.length); i++) if (a[i] !== b[i]) {
        console.log('       שורה ' + (i + 1) + '\n       java: ' + JSON.stringify(a[i]) + '\n       js:   ' + JSON.stringify(b[i]));
        break;
    }
    fs.writeFileSync(path.join(work, file + '.js.txt'), mine);
};

function dumpCase(id, e) {
    const cs = e.cs;
    const pkg = S.folderName(e.topic, new Set(cs ? ['Targil1'] : ['examples', 'ex07_old']), cs);
    let r = field('folder', pkg) + field('cs', String(e.cs)) + field('title', e.title) + field('summary', e.summary) + field('topic', e.topic)
          + field('mainClass', e.mainClass) + field('mainCode', e.mainCode) + field('expectedOutput', e.expectedOutput);
    for (const s of e.imports) r += field('import', s);
    for (const [k, v] of e.classes) r += field('class', k) + field('code', v);
    for (const t of e.tasks) {
        r += field('task.label', t.label) + field('task.kind', t.kind) + field('task.className', t.className) + field('task.returnType', t.returnType)
           + field('task.methodName', t.methodName) + field('task.isStatic', String(t.isStatic)) + field('task.instructions', t.instructions);
        for (const p of t.params) r += field('task.param', p.type + ' ' + p.name);
        r += field('task.header', S.header(t)) + field('task.mark', S.markFor(t, e.tasks.length > 1)) + field('task.solution', t.solution);
    }
    for (const g of e.given) r += field('given.className', g.className) + field('given.code', g.code);
    for (const q of e.others) r += field('other.label', q.label) + field('other.text', q.text);
    compare(id + '.read.txt', id + '  read', r);
    for (const mode of ['stub', 'solutions', 'testReturns']) {
        S.testReturns = mode === 'testReturns';
        let b = '';
        for (const g of S.build(e, pkg, [id, 'צילום מסך 1.png'], mode === 'solutions'))
            b += field('className', g.className) + field('cursorLine', String(g.cursorLine)) + field('cs', String(g.cs)) + field('fileName', g.fileName) + field('code', g.code);
        S.testReturns = false;
        compare(id + '.' + mode + '.txt', id + '  build ' + (mode === 'solutions' ? 'withSolutions=true' : mode === 'stub' ? 'withSolutions=false' : 'testReturns'), b);
    }
}

let f = '';
for (const c of readJson(folderCases)) {
    let name;
    try { name = S.folderName(c[0], new Set(c[1]), c[2] === true); } catch (e) { name = 'ERROR'; }
    f += field('folder', name);
}
compare('folders.txt', 'folderName (' + readJson(folderCases).length + ' מקרים)', f);
for (const d of dirs)
    for (const name of fs.readdirSync(d).filter(x => x.endsWith('.json')).sort())
        dumpCase(path.basename(d) + '_' + name, S.read(readJson(path.join(d, name))));
const broken = S.read(readJson(path.join(ai, 'repair', 'broken.json'))), fixed = S.read(readJson(path.join(ai, 'repair', 'fixed.json')));
const changed = S.takeSolutions(broken, fixed);
broken.title = broken.title + ' changed=' + changed;
dumpCase('repair_takeSolutions', broken);

console.log(fails ? fails + ' מתוך ' + n + ' שונים בין Java ל-JS' : 'זהה: ' + n + ' השוואות (Java = JS)');
process.exit(fails ? 1 : 0);
