// "מאגר תרגילים" ב-VS Code (vscode-extension/bank.js) — בדיקה מקצה לקצה בלי VS Code:
//   1. הגשר: token אקראי (בלי — נדחה), OPEN פותח בעורך ובשורה, SAVE שומר מסמך עם שינויים, REFRESH סוגר לשוניות של קבצים שנמחקו
//   2. הכפתור: הפקודה שנוצרת (javaw, תיקיית ההתקנה, classpath יחסי, הארגומנטים ב-DSVIZ_ARGS — גם נתיב בעברית עם רווחים)
//   3. Java ו-C#: הכלי האמיתי (dsviz-bank-vs.jar) מופעל באותה פקודה מול הגשר האמיתי של bank.js — התרגיל נוצר בתיקייה הפתוחה,
//      הקובץ נפתח בשורה של הפעולה, "הכן סעיף ב" שומר קודם, החלון במצב VS Code (בלי IntelliJ / Visual Studio), "סיימתי" מוחק וסוגר
// שימוש: node vscode-bank-test.js <תיקיית עבודה>   (אחרי tools/build-lib.sh ו-tools/build-bank-vs.sh; C# — אם יש dotnet ו-dsviz-cs)
'use strict';
const fs = require('fs');
const path = require('path');
const net = require('net');
const cp = require('child_process');

const ROOT = path.resolve(__dirname, '../..');
const EXT = path.join(ROOT, 'vscode-extension');
const W = path.resolve(process.argv[2] || path.join(ROOT, 'build/test/vscode-bank'));
const LIB = path.join(ROOT, 'release/dsviz.jar');
const BANK = path.join(ROOT, 'build/lab-vs/dsviz-bank-vs.jar');
const JAVA_HOME = process.env.JAVA_HOME && fs.existsSync(path.join(process.env.JAVA_HOME, 'bin/javac')) ? process.env.JAVA_HOME : null;
const JAVA = JAVA_HOME ? path.join(JAVA_HOME, 'bin/java') : 'java';
const JAVAC = JAVA_HOME ? path.join(JAVA_HOME, 'bin/javac') : 'javac';

let oks = 0, fails = 0;
function check(id, ok, what, why) {
    if (ok) { oks++; console.log('  ok   ' + id + '  ' + what); }
    else { fails++; console.log('  FAIL ' + id + '  ' + what + (why ? ' — ' + why : '')); }
}

// ------------------------------------------------------------ VS Code מדומה (רק מה ש-bank.js צריך)
const opened = [];   // { file, line, col }
const docs = new Map();
const closedTabs = [];
const settings = {};
function mockDoc(file) {
    const text = fs.readFileSync(file, 'utf8').split('\n');
    return {
        uri: { fsPath: file, scheme: 'file' }, lineCount: text.length, isDirty: false, saved: 0,
        lineAt: i => ({ text: text[i] || '' }),
        save() { this.saved++; this.isDirty = false; return Promise.resolve(true); }
    };
}
const vscode = {
    Position: class { constructor(l, c) { this.line = l; this.character = c; } },
    Selection: class { constructor(a, b) { this.anchor = a; this.active = b; } },
    Range: class { constructor(a, b) { this.start = a; this.end = b; } },
    TextEditorRevealType: { InCenter: 2 },
    workspace: {
        openTextDocument: async f => { const d = mockDoc(f); docs.set(path.resolve(f), d); return d; },
        get textDocuments() { return [...docs.values()]; },
        getConfiguration: () => ({ get: k => settings[k.replace(/^dsviz\./, '')] })
    },
    window: {
        showTextDocument: async doc => {
            const ed = {};
            Object.defineProperty(ed, 'selection', { set: s => opened.push({ file: doc.uri.fsPath, line: s.active.line, col: s.active.character }) });
            ed.revealRange = () => {};
            return ed;
        },
        tabGroups: {
            get all() { return [{ tabs: [...docs.values()].map(d => ({ input: { uri: d.uri }, isDirty: d.isDirty, label: path.basename(d.uri.fsPath) })) }]; },
            close: async (tabs) => { for (const t of tabs) { closedTabs.push(t.input.uri.fsPath); docs.delete(path.resolve(t.input.uri.fsPath)); } return true; }
        }
    }
};
const messages = [];
const ui = { error: (v, m) => { messages.push('E ' + m); }, warn: (v, m) => { messages.push('W ' + m); }, info: (v, m) => { messages.push('I ' + m); } };

const bank = require(path.join(EXT, 'bank.js'));
const project = require(path.join(EXT, 'project.js'));
const cs = require(path.join(EXT, 'cs.js'));

function send(port, line) {
    return new Promise(resolve => {
        const s = net.connect(port, '127.0.0.1', () => s.write(line + '\n'));
        let out = '';
        s.setEncoding('utf8');
        s.on('data', d => { out += d; });
        s.on('end', () => resolve(out.trim()));
        s.on('error', () => resolve(null));
    });
}
const wait = ms => new Promise(r => setTimeout(r, ms));

async function main() {
    fs.rmSync(W, { recursive: true, force: true });
    fs.mkdirSync(W, { recursive: true });
    for (const f of [LIB, BANK]) if (!fs.existsSync(f)) { console.log('חסר ' + f + ' — קודם tools/build-lib.sh ו-tools/build-bank-vs.sh'); process.exit(1); }

    // ההתקנה המדומה: <dir>/dsviz.jar + <dir>/bank/dsviz-bank-vs.jar, וה-Java שנרשמה (jdk-path.txt) מצביעה לתוכה
    const home = path.join(W, 'home');
    const inst = path.join(W, 'install dir', 'DSViz');
    fs.mkdirSync(path.join(inst, 'bank'), { recursive: true });
    fs.copyFileSync(LIB, path.join(inst, 'dsviz.jar'));
    fs.copyFileSync(BANK, path.join(inst, 'bank', bank.BANK_JAR));
    const fakeRunner = { readDsvizFile: n => (n === 'jdk-path.txt' ? path.join(inst, 'jdk') : null), MAC_DIR: null, MAC: false,
        INSTALL_HINT: 'יש להתקין מחדש', findJava: () => ({ java: JAVA, javac: JAVAC, source: 'test' }) };
    const tool = bank.findTool(fakeRunner, '', path.join(EXT, 'resources/dsviz.jar'));
    check('find-tool', tool && tool.dir === inst && tool.cp.join(',') === 'dsviz.jar,bank/' + bank.BANK_JAR,
        'הכלי נמצא בתיקיית ההתקנה, וה-classpath יחסי אליה (dsviz.jar + bank/dsviz-bank-vs.jar — אותה גרסה)', JSON.stringify(tool));
    check('find-none', bank.findTool({ readDsvizFile: () => null, MAC_DIR: null }, '', '') === null, 'בלי התקנה — null (והכפתור מסביר מה חסר)', '');

    // ---- 1. הגשר
    const cmds = {};
    let chosenRoot, chosenLang;
    const api = bank.register({ vscode, ctx: { extensionPath: EXT, subscriptions: [] }, reg: (id, fn) => { cmds[id] = fn; },
        chooseRoot: async () => chosenRoot, detectLanguage: () => chosenLang, project, cs, runner: fakeRunner, ui, store: () => path.join(W, 'solutions.json') });
    const br = await bank.startBridge(api.handlers);
    const probe = path.join(W, 'probe.txt');
    fs.writeFileSync(probe, 'a\nbb\nccc\n');
    check('bridge-denied', (await send(br.port, 'wrong\tOPEN\t' + probe + '\t2')) === 'DENIED' && (await send(br.port, 'OPEN\t' + probe)) === 'DENIED',
        'בקשה בלי ה-token הנכון נדחית (תוכנה אחרת במחשב לא יכולה לשלוט ב-VS Code)', '');
    check('bridge-dialog', (await send(br.port, br.token + '\tDIALOG')) === '0', 'DIALOG → 0', '');
    const r1 = await send(br.port, br.token + '\tOPEN\t' + probe + '\t2');
    await wait(200);
    check('bridge-open', r1 === 'OK' && opened.some(o => o.file === probe && o.line === 1 && o.col === 2), 'OPEN פותח את הקובץ, והסמן בסוף שורה 2', JSON.stringify(opened));
    docs.get(path.resolve(probe)).isDirty = true;
    const r2 = await send(br.port, br.token + '\tSAVE\t' + probe);
    check('bridge-save', r2 === 'OK' && docs.get(path.resolve(probe)).saved === 1, 'SAVE שומר מסמך עם שינויים שלא נשמרו (לפני "הכן סעיף ב")', '');
    fs.unlinkSync(probe);
    docs.get(path.resolve(probe)).isDirty = false;
    await send(br.port, br.token + '\tREFRESH\t' + W);
    await wait(200);
    check('bridge-refresh', closedTabs.includes(probe), 'REFRESH סוגר את הלשונית של קובץ שנמחק', JSON.stringify(closedTabs));
    check('bridge-unknown', (await send(br.port, br.token + '\tRUN\tx')) === 'UNKNOWN', 'פקודה לא מוכרת — UNKNOWN (לא עושה כלום)', '');
    br.close();

    // ---- 2. הכפתור (הפקודה שנוצרת; spawn מדומה)
    const spawned = [];
    const realSpawn = cp.spawn;
    cp.spawn = (c, a, o) => { const h = {}; spawned.push({ c, a, o, h }); return { exitCode: null, on: (ev, fn) => { h[ev] = fn; }, kill: () => {} }; };
    const api2 = bank.register({ vscode, ctx: { extensionPath: EXT, subscriptions: [] }, reg: (id, fn) => { cmds[id] = fn; },
        chooseRoot: async () => chosenRoot, detectLanguage: () => chosenLang, project, cs, runner: fakeRunner, ui, store: () => path.join(W, 'solutions.json') });
    fs.mkdirSync(path.join(EXT, 'resources'), { recursive: true });
    const extJar = path.join(EXT, 'resources', 'dsviz.jar');
    const hadJar = fs.existsSync(extJar);
    if (!hadJar) fs.copyFileSync(LIB, extJar);
    chosenRoot = path.join(W, 'תרגילים שלי', 'Students');
    fs.mkdirSync(chosenRoot, { recursive: true });
    chosenLang = 'java';
    const okOpen = await cmds['dsviz.bank']();
    const s0 = spawned[0] || { a: [], o: {} };
    const envArgs = (s0.o.env && s0.o.env.DSVIZ_ARGS || '').split('\n');
    check('cmd-java', okOpen && s0.o.cwd === inst && s0.a.join(' ') === '-Dfile.encoding=UTF-8 -cp dsviz.jar' + path.delimiter + 'bank/' + bank.BANK_JAR + ' ' + bank.MAIN + ' --args-env'
        && envArgs[0] === 'bank' && envArgs[1] === path.join(chosenRoot, 'src') && envArgs.includes('--vscode') && !envArgs.includes('--cs')
        && envArgs[envArgs.indexOf('--lib') + 1] === path.join(chosenRoot, 'lib', 'dsviz.jar') && envArgs[envArgs.indexOf('--project') + 1] === 'Students'
        && envArgs[envArgs.indexOf('--store') + 1] === path.join(W, 'solutions.json') && /^[0-9a-f]{32}$/.test(envArgs[envArgs.indexOf('--token') + 1])
        && !('JAVA_TOOL_OPTIONS' in s0.o.env) && fs.existsSync(path.join(chosenRoot, 'lib', 'dsviz.jar')),
        '"מאגר תרגילים" (Java): מתיקיית ההתקנה, classpath יחסי, הארגומנטים ב-DSVIZ_ARGS (נתיב בעברית עם רווחים עובר כמו שהוא), והתיקייה הוכנה (lib/dsviz.jar)',
        JSON.stringify({ a: s0.a, cwd: s0.o.cwd, envArgs }));
    // Windows: windowsHide מעביר SW_HIDE ו-Windows מסתיר את החלון הראשון של הכלי (מסך הכניסה / המאגר) — "לחצתי ולא קרה כלום"
    check('cmd-visible', s0.o.windowsHide === false, 'הכלי נפתח עם חלון גלוי (בלי windowsHide — ב-Windows הוא מסתיר את מסך הכניסה)', JSON.stringify({ windowsHide: s0.o.windowsHide }));
    messages.length = 0;
    if (s0.h && s0.h.exit) s0.h.exit(1, null);
    check('cmd-early-exit', messages.some(m => m.includes('מאגר התרגילים נסגר מיד')), 'הכלי נסגר מיד עם שגיאה — הודעה (ולא "לא קרה כלום")', messages.join(' | '));
    messages.length = 0;
    if (s0.h && s0.h.exit) s0.h.exit(0, null);
    check('cmd-normal-exit', messages.length === 0, 'סגירה רגילה של הכלי — בלי הודעה', messages.join(' | '));
    await cmds['dsviz.bankQuestion']();
    const s1 = spawned[1] || { o: {} };
    check('cmd-question', (s1.o.env && s1.o.env.DSVIZ_ARGS || '').split('\n')[0] === 'question' && api2.sessions.size === 1,
        '"השאלה": אותו כלי במצב question, ואותו גשר (אחד לכל תיקייה)', '');
    chosenLang = 'csharp';
    settings.csRunner = path.join(W, 'fake-dsviz-cs.dll');
    fs.writeFileSync(settings.csRunner, '');
    const csLib = path.join(EXT, 'resources', 'cs', 'lib', cs.LIB);
    const hadCsLib = fs.existsSync(csLib);
    if (!hadCsLib) { fs.mkdirSync(path.dirname(csLib), { recursive: true }); fs.writeFileSync(csLib, 'x'); }
    const csRoot = path.join(W, 'cs ws', 'DataStructures-CS');
    fs.mkdirSync(csRoot, { recursive: true });
    chosenRoot = csRoot;
    await cmds['dsviz.bank']();
    const s2 = spawned[2] || { o: {} };
    const ea2 = (s2.o.env && s2.o.env.DSVIZ_ARGS || '').split('\n');
    check('cmd-cs', ea2[1] === csRoot && ea2.includes('--vscode') && ea2.includes('--cs') && !ea2.includes('--lib') && s2.o.env.DSVIZ_CS === settings.csRunner
        && fs.existsSync(path.join(csRoot, 'Directory.Build.props')),
        '"מאגר תרגילים" (C#): --cs, התיקייה עצמה (Exercises בתוכה), dsviz-cs ב-DSVIZ_CS, והתיקייה הוכנה (Directory.Build.props)', JSON.stringify(ea2));
    if (!hadCsLib) fs.rmSync(path.join(EXT, 'resources', 'cs'), { recursive: true, force: true });
    delete settings.csRunner;
    chosenRoot = path.join(W, 'nothing');
    chosenLang = 'java';
    const api3 = bank.register({ vscode, ctx: { extensionPath: EXT, subscriptions: [] }, reg: (id, fn) => { cmds[id] = fn; },
        chooseRoot: async () => chosenRoot, detectLanguage: () => chosenLang, project, cs, runner: { readDsvizFile: () => null, MAC_DIR: null, MAC: false, INSTALL_HINT: 'יש להתקין מחדש את DSViz-Setup.exe', findJava: () => null }, ui, store: () => '' });
    messages.length = 0;
    const no = await cmds['dsviz.bank']();
    check('cmd-missing', no === false && messages.some(m => m.includes('dsviz-bank-vs.jar') && m.includes('DSViz-Setup.exe')), 'בלי מאגר מותקן: הודעה ברורה מה חסר ואיך מתקנים', messages.join(' | '));
    cp.spawn = realSpawn;
    if (!hadJar) fs.unlinkSync(extJar);
    void api3;

    // ---- 3. הכלי האמיתי מול הגשר האמיתי
    const cls = path.join(W, 'cls');
    fs.mkdirSync(cls, { recursive: true });
    const jc = cp.spawnSync(JAVAC, ['-encoding', 'UTF-8', '-nowarn', '-cp', LIB + path.delimiter + BANK, '-d', cls,
        path.join(__dirname, 'vscode-bank/BankVsCodeDriver.java'), path.join(__dirname, 'windows-vs/BankVsIt.java')], { encoding: 'utf8' });
    if (jc.status !== 0) { console.log(jc.stdout + jc.stderr); process.exit(1); }

    const runReal = async (lang, root, q, extra) => {
        opened.length = 0; closedTabs.length = 0; docs.clear();
        const b = await bank.startBridge(api.handlers);
        const o = { mode: 'bank', lang, root, project: path.basename(root), store: path.join(W, lang + '-solutions.json'), javaExe: JAVA, port: b.port, token: b.token };
        if (lang === 'csharp') Object.assign(o, extra);
        else {
            const { srcDir } = project.setup(root, LIB);
            Object.assign(o, { srcDir, lib: path.join(root, 'lib', 'dsviz.jar'), java: JAVA, outDir: path.join(root, 'bin') });
        }
        const c = bank.command(o, { dir: inst, cp: ['dsviz.jar', 'bank/' + bank.BANK_JAR, cls] });
        const args = c.args.map(x => (x === bank.MAIN ? 'il.lamerhav.dsviz.BankVsCodeDriver' : x));
        args.splice(0, 0, '-Duser.home=' + home, '-Dq=' + q, '-DwithWindow=' + (process.env.DISPLAY ? 'true' : 'false'), '-Dstdout.encoding=UTF-8');
        const r = await new Promise(res => {
            const p = realSpawn(c.command, args, { cwd: c.cwd, env: c.env });
            let out = '';
            p.stdout.on('data', d => { out += d; });
            p.stderr.on('data', d => { out += d; });
            const t = setTimeout(() => p.kill(), 240000);
            p.on('close', code => { clearTimeout(t); res({ code, out }); });
        });
        await wait(400);
        b.close();
        const lines = r.out.split('\n');
        const get = k => (lines.find(l => l.startsWith(k + ' ')) || '').slice(k.length + 1);
        return { r, lines, get, received: b.received, o };
    };

    // Java
    const jRoot = path.join(W, 'תיקייה עם רווח', 'Students');
    fs.mkdirSync(jRoot, { recursive: true });
    const j = await runReal('java', jRoot, 'stack-sum-size');
    const jSrc = path.join(jRoot, 'src');
    const jFiles = j.lines.filter(l => l.startsWith('FILE ')).map(l => l.slice(5));
    const jump = j.get('JUMP').split(' ');
    const jumpFile = jump.slice(0, -1).join(' '), jumpLine = parseInt(jump[jump.length - 1], 10);
    check('java-run', j.r.code === 0, 'הכלי רץ עד הסוף (Java)', j.r.out.slice(-1500));
    check('java-mode', j.get('MODE').startsWith('vscode=true ij=true cs=false bridged=true key=vscode progress=bank-progress-vscode.json')
        && j.get('TITLE') === 'מאגר תרגילים — VS Code' && j.get('WHERE') === 'בתיקייה Students ב-VS Code' && j.get('IDE') === 'VS Code' && j.get('LANG') === 'java',
        'מצב VS Code (Java): הכותרת, "בתיקייה Students ב-VS Code", התקדמות בקובץ נפרד (bank-progress-vscode.json)', j.get('MODE') + ' / ' + j.get('TITLE') + ' / ' + j.get('WHERE'));
    check('java-no-bidi', j.get('BIDI') === '0', 'בקוד שנוצר ל-VS Code (Java, אחרי "הכן סעיף ב") אין סימוני כיוון נסתרים — VS Code מציג אותם כ-[U+202B] באדום', 'BIDI ' + j.get('BIDI'));
    check('java-create', / errors=0 /.test(j.get('CREATED')) && jFiles.length >= 1 && jFiles.every(f => f.startsWith(jSrc + path.sep + 'ex01_')) && j.get('PROJ').endsWith('csproj=false'),
        'התרגיל נוצר בתיקיית הקוד של התיקייה הפתוחה (src/ex01_...)', j.get('CREATED') + ' ' + jFiles.join(','));
    const jr = j.received.join(' || ');
    const iOpen = j.received.findIndex(x => x === 'OPEN\t' + jumpFile + '\t' + jumpLine);
    check('java-open', jumpLine > 0 && iOpen >= 0 && opened.some(o => o.file === jumpFile && o.line === jumpLine - 1),
        '"לפעולה של סעיף א": VS Code פותח את ' + path.basename(jumpFile) + ' בשורה ' + jumpLine + ' (של הפעולה)', jr);
    const iSave = j.received.findIndex((x, i) => i > iOpen && x.startsWith('SAVE\t'));
    check('java-prepare', /^ok prepared=2$/.test(j.get('PREPARE')) && iSave > iOpen && j.received.slice(iSave).some(x => x.startsWith('REFRESH\t')),
        '"הכן סעיף ב": קודם SAVE (העורך שומר), אחר כך ההוספה, ורענון', j.get('PREPARE') + ' — ' + jr);
    if (process.env.DISPLAY) {
        const w = j.get('WINDOW');
        const bad = ['IntelliJ', 'Visual Studio'].filter(x => w.includes(x)).map(x => x + ': …' + w.slice(Math.max(0, w.indexOf(x) - 60), w.indexOf(x) + 40) + '…');
        check('java-window', w.replace(/^[\u200e\u200f\u202a-\u202e]+/, '').startsWith('מאגר תרגילים — VS Code') && w.includes('בתיקייה Students ב-VS Code') && !bad.length,
            'חלון המאגר במצב VS Code: "מאגר תרגילים — VS Code", "בתיקייה Students ב-VS Code", בלי IntelliJ / Visual Studio', bad.join(' | ') || w.slice(0, 200));
    }
    check('java-done', /^DONE projects=0 left=0/.test('DONE ' + j.get('DONE')) && jFiles.every(f => !fs.existsSync(f)) && closedTabs.some(f => jFiles.includes(f))
        && /הקבצים שיימחקו|הקובץ שיימחק/.test(j.get('DONETEXT')),
        '"סיימתי את התרגיל": קבצי התרגיל נמחקו, והלשוניות שלהם נסגרו ב-VS Code (REFRESH)', j.get('DONE') + ' closed=' + closedTabs.join(','));
    check('java-progress', fs.existsSync(path.join(home, '.dsviz', 'bank-progress-vscode.json')) && !fs.existsSync(path.join(home, '.dsviz', 'bank-progress-ij.json')),
        'ההתקדמות של VS Code בקובץ משלה (לא מתערבבת עם IntelliJ / Visual Studio)', '');

    // C# — רק אם יש dotnet ו-dsviz-cs
    const runner = path.join(ROOT, 'csharp/DsViz.Runner/bin/Release/net8.0/dsviz-cs.dll');
    const csDll = path.join(ROOT, 'csharp/DsViz.Runner/bin/Release/net8.0', cs.LIB);
    const hasDotnet = cp.spawnSync('dotnet', ['--version']).status === 0;
    if (hasDotnet && fs.existsSync(runner) && fs.existsSync(csDll)) {
        const cRoot = path.join(W, 'cs ws 2', 'DataStructures-CS');
        fs.mkdirSync(cRoot, { recursive: true });
        cs.setup(cRoot, csDll);
        const c = await runReal('csharp', cRoot, 'queue-sum-even', { csRunner: runner });
        const cFiles = c.lines.filter(l => l.startsWith('FILE ')).map(l => l.slice(5));
        const cj = c.get('JUMP').split(' ');
        const cjFile = cj.slice(0, -1).join(' '), cjLine = parseInt(cj[cj.length - 1], 10);
        check('cs-run', c.r.code === 0, 'הכלי רץ עד הסוף (C#)', c.r.out.slice(-1500));
        check('cs-mode', c.get('MODE').startsWith('vscode=true ij=false cs=true bridged=true key=vscode-cs progress=bank-progress-vscode-cs.json')
            && c.get('TITLE') === 'מאגר תרגילים — C# · VS Code' && c.get('LANG') === 'csharp',
            'מצב VS Code (C#): הכותרת, המאגר בגרסת C#, התקדמות בקובץ נפרד', c.get('MODE') + ' / ' + c.get('TITLE'));
        check('cs-no-bidi', c.get('BIDI') === '0', 'בקוד שנוצר ל-VS Code (C#) אין סימוני כיוון נסתרים', 'BIDI ' + c.get('BIDI'));
        check('cs-create', / errors=0 /.test(c.get('CREATED')) && cFiles.length >= 1 && cFiles.every(f => f.startsWith(path.join(cRoot, 'Exercises') + path.sep))
            && c.get('PROJ').endsWith('csproj=true'),
            'התרגיל נוצר כפרויקט ב-Exercises (עם .csproj), כמו ב-Visual Studio', c.get('CREATED') + ' ' + cFiles.join(','));
        check('cs-open', cjLine > 0 && c.received.includes('OPEN\t' + cjFile + '\t' + cjLine) && opened.some(o => o.file === cjFile && o.line === cjLine - 1),
            'VS Code פותח את ' + path.basename(cjFile) + ' בשורה ' + cjLine + ', דרך הגשר (בלי Visual Studio)', c.received.join(' || '));
        check('cs-prepare', /^ok prepared=2$/.test(c.get('PREPARE')) && c.received.some(x => x.startsWith('SAVE\t')), '"הכן סעיף ב" (C#): SAVE ואז ההוספה', c.get('PREPARE'));
        if (process.env.DISPLAY) {
            const w = c.get('WINDOW');
            const bad = ['IntelliJ', 'Visual Studio'].filter(x => w.includes(x)).map(x => x + ': …' + w.slice(Math.max(0, w.indexOf(x) - 60), w.indexOf(x) + 40) + '…');
            check('cs-window', w.includes('VS Code') && !bad.length, 'חלון המאגר (C#) במצב VS Code — בלי Visual Studio', bad.join(' | ') || w.slice(0, 200));
        }
        check('cs-done', c.get('DONETEXT').includes('התרגיל שיימחק') && !c.get('DONETEXT').includes('Visual Studio') && cFiles.every(f => !fs.existsSync(f))
            && c.received.slice(-1)[0] && c.received.slice(-1)[0].startsWith('REFRESH\t') && closedTabs.length > 0,
            '"סיימתי" (C#): הפרויקט נמחק, ההודעה בלי Visual Studio, ורענון — הלשוניות נסגרו', c.get('DONETEXT') + ' | ' + c.received.slice(-2).join(' || '));
    } else {
        console.log('  (אין dotnet / dsviz-cs — מדלגים על C#)');
    }

    console.log('SUMMARY ' + oks + ' ok / ' + fails + ' FAIL');
    process.exit(fails ? 1 : 0);
}
main().catch(e => { console.error(e); process.exit(1); });
