// בדיקת התוסף של VS Code מול חיקוי (mock) של ה-API: מריצים את הפקודות האמיתיות של extension.js
// עם תשובות מתוסרטות של "תלמיד", ובודקים את הקבצים שנוצרו ואת תצורת ההרצה.
const Module = require('module');
const fs = require('fs'), path = require('path'), os = require('os'), assert = require('assert');

const ws = fs.mkdtempSync(path.join(os.tmpdir(), 'dsviz-ws-'));
const ext = path.resolve(__dirname, '../../vscode-extension');
const answers = [];   // תור תשובות: לכל שאלה פונקציה שמקבלת את הפריטים/האפשרויות
const log = [];
let debugConfig = null, opened = null;

const vscode = {
    workspace: {
        workspaceFolders: [{ uri: { fsPath: ws }, name: 'ws' }],
        openTextDocument: async f => { const t = fs.readFileSync(f, 'utf8').split('\n');
            return { fileName: f, lineCount: t.length, lineAt: i => ({ text: t[i] }), languageId: 'java' }; },
        getWorkspaceFolder: () => vscode.workspace.workspaceFolders[0]
    },
    window: {
        showQuickPick: async (items, opts) => { const a = answers.shift(); log.push('pick: ' + opts.title);
            return a(items, opts); },
        showInputBox: async opts => { const a = answers.shift(); const v = a(opts); log.push('input: ' + opts.title + ' = ' + v);
            const err = opts.validateInput && opts.validateInput(v); if (err) throw new Error('validation: ' + err); return v; },
        showInformationMessage: async (m) => { log.push('info: ' + m); },
        showWarningMessage: async (m) => { log.push('warn: ' + m); },
        showErrorMessage: async (m) => { log.push('error: ' + m); },
        showTextDocument: async doc => { opened = doc.fileName; return { selection: null, revealRange() {} }; },
        createStatusBarItem: () => ({ show() {}, hide() {}, dispose() {} }),
        registerTreeDataProvider: () => ({ dispose() {} }),
        onDidChangeActiveTextEditor: () => ({ dispose() {} }),
        activeTextEditor: null
    },
    commands: { registerCommand: (id, fn) => { commands[id] = fn; return { dispose() {} }; }, executeCommand: async () => {} },
    extensions: { getExtension: () => ({}) },
    debug: { startDebugging: async (folder, cfg, o) => { debugConfig = { cfg, o }; return true; } },
    StatusBarAlignment: { Left: 1 }, TextEditorRevealType: { InCenter: 2 },
    Position: class { constructor(l, c) { this.line = l; this.character = c; } },
    Selection: class { constructor(a, b) { this.a = a; this.b = b; } },
    Range: class { constructor(a, b) { this.a = a; this.b = b; } },
    Uri: { file: f => ({ fsPath: f }) }
};
const commands = {};
const origLoad = Module._load;
Module._load = function (req, ...rest) { return req === 'vscode' ? vscode : origLoad.call(this, req, ...rest); };

const byId = id => items => items.find(i => i.id === id || i.label === id);
const byIds = ids => items => items.filter(i => ids.includes(i.id));
const keep = opts => opts.value;           // מקבלים את ברירת המחדל
const value = v => () => v;

(async () => {
    const extension = require(path.join(ext, 'extension.js'));
    const globalState = new Map();
    extension.activate({ subscriptions: [], extensionPath: ext,
        globalState: { get: k => globalState.get(k), update: async (k, v) => globalState.set(k, v) } });

    // --- תרגיל 1: מחסנית של Integer, מחזירה int, עם פרמטר x — דרך הכפתור "מחסנית"
    answers.push(byId('Integer'), byId('int'), byIds(['extra']), keep, keep, value('solve'));
    await commands['dsviz.newStack']();
    const f1 = path.join(ws, 'src', 'Targil1.java');
    assert(fs.existsSync(f1), 'Targil1.java not created');
    assert.strictEqual(opened, f1);
    assert(fs.existsSync(path.join(ws, 'lib', 'dsviz.jar')), 'jar not copied');
    const settings = JSON.parse(fs.readFileSync(path.join(ws, '.vscode', 'settings.json'), 'utf8'));
    assert.deepStrictEqual(settings['java.project.referencedLibraries'], ['lib/**/*.jar']);
    assert.deepStrictEqual(settings['java.project.sourcePaths'], ['src']);

    // --- תרגיל 2: דרך "תרגיל חדש" — רשימה של String, מחזירה מבנה, שני מבנים, ערכים משלנו, שם משלנו
    answers.push(byId('list'), byId('String'), byId('structure'), byIds(['second']),
                 value('hello, "world", a\\b'), keep, value('Merge'), value('merge'));
    await commands['dsviz.newExercise']();
    const f2 = path.join(ws, 'src', 'Merge.java');
    assert(fs.existsSync(f2), 'Merge.java not created');

    // --- תרגיל 3: שם תפוס / ערך שגוי — validateInput חייב לחסום
    answers.push(byId('Integer'), byId('void'), byIds([]), value('1, x, 3'));
    await assert.rejects(commands['dsviz.newQueue'](), /validation/);
    answers.length = 0;
    answers.push(byId('Integer'), byId('void'), byIds([]), keep, value('Targil1'));
    await assert.rejects(commands['dsviz.newQueue'](), /validation: כבר קיים/);
    answers.length = 0;

    // --- דוגמאות
    answers.push(items => items[0]);
    await commands['dsviz.examples']();
    assert(fs.existsSync(path.join(ws, 'src', 'examples', 'ListExamples.java')));

    // --- הרצה ויזואלית של Merge.java
    const text = fs.readFileSync(f2, 'utf8');
    vscode.window.activeTextEditor = { document: { fileName: f2, uri: {}, languageId: 'java', getText: () => text, save: async () => true } };
    await commands['dsviz.run']();
    assert.strictEqual(debugConfig.cfg.mainClass, 'Merge');
    assert.strictEqual(debugConfig.cfg.cwd, ws);
    assert.strictEqual(debugConfig.o.noDebug, true);
    assert(!debugConfig.cfg.vmArgs.some(a => a.startsWith('-javaagent')), 'regular run must not use the agent');

    // --- הרצה שורה אחר שורה: אותה מחלקה, עם -javaagent של ה-jar שבפרויקט ושם המחלקה
    await commands['dsviz.runLines']();
    const agent = debugConfig.cfg.vmArgs.find(a => a.startsWith('-javaagent:'));
    assert.strictEqual(agent, '-javaagent:' + path.join(ws, 'lib', 'dsviz.jar') + '=Merge');
    assert.strictEqual(debugConfig.cfg.mainClass, 'Merge');

    // --- הוספה לקוד: "צור תור" עם String בשורה ריקה בתוך main — בלי import בקובץ
    const lines = ['public class X {', '    public static void main(String[] args) {', '        ', '    }', '}'];
    const edits = [];
    vscode.window.activeTextEditor = {
        selection: { active: { line: 2 } },
        document: {
            languageId: 'java', getText: () => lines.join('\n'),
            lineAt: i => ({ text: lines[i], range: { line: i }, }),
            positionAt: o => ({ offset: o })
        },
        edit: async cb => { cb({ replace: (r, t) => edits.push(['replace', r.line, t]), insert: (p, t) => edits.push(['insert', p, t]) }); return true; }
    };
    answers.push(byId('String'), value('4'), keep);
    await commands['dsviz.insert.queue']();
    assert.strictEqual(edits[0][0], 'replace');
    assert(/^        Queue<String> q = Build\.queue\("\w+", "\w+", "\w+", "\w+"\);/.test(edits[0][2]), edits[0][2]);
    assert.strictEqual(edits[1][2], 'import unit4.collectionsLib.*;\n\n');
    edits.length = 0;
    answers.push(value('2x3'), keep);
    answers.unshift(byId('Integer'));
    await commands['dsviz.insert.matrix']();
    assert(edits[0][2].includes('int[][] mat = Build.matrix(new int[][] {'), edits[0][2]);
    assert.strictEqual(edits[0][2].split('\n').length, 4);

    // --- ספרייה מתנגשת (jar מקורי של unit4) מזוהה
    fs.writeFileSync(path.join(ws, 'lib', 'unit4.jar'), Buffer.concat([Buffer.from('PK...'), Buffer.from('unit4/collectionsLib/Stack.class')]));
    assert.deepStrictEqual(require(path.join(ext, 'project.js')).findConflicts(ws).map(f => path.basename(f)), ['unit4.jar']);
    fs.unlinkSync(path.join(ws, 'lib', 'unit4.jar'));

    console.log(log.join('\n'));
    console.log('\nALL VS CODE TESTS PASSED\nworkspace: ' + ws);
})().catch(e => { console.error(log.join('\n')); console.error(e); process.exit(1); });
