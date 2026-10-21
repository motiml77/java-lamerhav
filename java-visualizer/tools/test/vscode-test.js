// בדיקת התוסף של VS Code מול חיקוי (mock) של ה-API: מריצים את הפקודות האמיתיות של extension.js
// עם תשובות מתוסרטות של "תלמיד", ובודקים את הקבצים שנוצרו ואת תצורת ההרצה.
const Module = require('module');
const fs = require('fs'), path = require('path'), os = require('os'), assert = require('assert');

const ws = fs.mkdtempSync(path.join(os.tmpdir(), 'dsviz-ws-'));
const ext = path.resolve(__dirname, '../../vscode-extension');
const answers = [];   // תור תשובות: לכל שאלה פונקציה שמקבלת את הפריטים/האפשרויות
const log = [];
let opened = null, terminalOpts = null, diag = new Map();
const panels = [];

const vscode = {
    workspace: {
        workspaceFolders: [{ uri: { fsPath: ws }, name: 'ws' }],
        openTextDocument: async f => { const t = fs.readFileSync(f, 'utf8').split('\n');
            return { fileName: f, lineCount: t.length, lineAt: i => ({ text: t[i] }), languageId: 'java' }; },
        getWorkspaceFolder: () => vscode.workspace.workspaceFolders[0],
        getConfiguration: () => ({ get: () => undefined }),
        saveAll: async () => true
    },
    languages: { createDiagnosticCollection: () => ({ clear: () => diag.clear(), set: (u, l) => diag.set(u.fsPath, l), dispose() {} }) },
    env: { openExternal: () => {} },
    ProgressLocation: { Window: 10 },
    DiagnosticSeverity: { Error: 0, Warning: 1 },
    Diagnostic: class { constructor(r, m, s) { this.range = r; this.message = m; this.severity = s; } },
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
        activeTextEditor: null,
        createOutputChannel: () => ({ clear() {}, appendLine() {}, show() {}, dispose() {} }),
        withProgress: async (o, f) => f(),
        createTerminal: o => { terminalOpts = o; return { show() {}, dispose() {} }; },
        createWebviewPanel: (id, title) => { panels.push({ id, title }); return { webview: {}, dispose() {}, onDidDispose() {} }; }
    },
    commands: { registerCommand: (id, fn) => { commands[id] = fn; return { dispose() {} }; }, executeCommand: async () => {} },
    extensions: { getExtension: () => ({}) },
    StatusBarAlignment: { Left: 1 }, TextEditorRevealType: { InCenter: 2 }, ViewColumn: { Beside: -2 },
    Position: class { constructor(l, c) { this.line = l; this.character = c; } },
    Selection: class { constructor(a, b) { this.a = a; this.b = b; } },
    Range: class { constructor(a, b, c, d) { this.a = a; this.b = b; this.line = a; } },
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

    // --- הרצה ויזואלית של Merge.java: קומפילציה אמיתית עם javac ל-bin, והרצה בטרמינל עם java
    const text = fs.readFileSync(f2, 'utf8');
    vscode.window.activeTextEditor = { document: { fileName: f2, uri: {}, languageId: 'java', getText: () => text, save: async () => true } };
    await commands['dsviz.run']();
    assert(fs.existsSync(path.join(ws, 'bin', 'Merge.class')), 'not compiled');
    assert(fs.existsSync(path.join(ws, 'bin', 'examples', 'ListExamples.class')), 'examples not compiled');
    assert.strictEqual(terminalOpts.cwd, ws);
    // הרצה ויזואלית אחת: תמיד דרך LineByLine של הספרייה (כל שורה היא צעד בחלון)
    assert.deepStrictEqual(terminalOpts.shellArgs.slice(-2), ['unit4.collectionsLib.LineByLine', 'Merge']);
    assert(terminalOpts.shellArgs.includes('-Ddsviz.visual=true'));
    assert(terminalOpts.shellArgs.includes(path.join(ws, 'bin') + ':' + path.join(ws, 'lib', 'dsviz.jar')));
    assert.strictEqual(diag.size, 0);

    // --- השם הישן dsviz.runLines עדיין עובד (קיצורים מגרסה קודמת) — אותה הרצה
    terminalOpts = null;
    await commands['dsviz.runLines']();
    assert.deepStrictEqual(terminalOpts.shellArgs.slice(-2), ['unit4.collectionsLib.LineByLine', 'Merge']);

    // --- שגיאת קומפילציה: לא מריצים, ומסמנים את השורה באדום
    terminalOpts = null;
    fs.writeFileSync(path.join(ws, 'src', 'Broken.java'), 'public class Broken {\n    int x = ;\n}\n');
    await commands['dsviz.run']();
    assert.strictEqual(terminalOpts, null, 'must not run with errors');
    const bd = diag.get(path.join(ws, 'src', 'Broken.java'));
    assert(bd && bd.length >= 1 && bd[0].range.line === 1, 'error not on line 2');
    fs.unlinkSync(path.join(ws, 'src', 'Broken.java'));

    // --- הוספה לקוד: "צור מכונית" — נפתח חלון "מה יש במחלקה" (Car)
    {
        const ls = ['public class X {', '    public static void main(String[] args) {', '        ', '    }', '}'];
        const ed2 = [];
        vscode.window.activeTextEditor = { selection: { active: { line: 2 } },
            document: { languageId: 'java', getText: () => ls.join('\n'), lineAt: i => ({ text: ls[i], range: { line: i } }), positionAt: o => ({ offset: o }) },
            edit: async cb => { cb({ replace: (r, t) => ed2.push(t), insert: (p, t) => ed2.push(t) }); return true; } };
        answers.push(keep);
        await commands['dsviz.insert.car']();
        assert(/Car c1 = Car\.random\(\);/.test(ed2[0]), ed2[0]);
        assert(panels.length === 1 && panels[0].title.includes('Car'), JSON.stringify(panels));
    }

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
    assert(/^        Queue<String> q1 = new Queue<String>\(\);\n(        q1\.insert\("\w+"\);.*\n){3}        q1\.insert\("\w+"\);$/.test(edits[0][2]), edits[0][2]);
    assert.strictEqual(edits[1][2], 'import unit4.collectionsLib.*;\n\n');
    edits.length = 0;
    answers.push(value('2x3'), keep);
    answers.unshift(byId('Integer'));
    await commands['dsviz.insert.matrix']();
    assert(edits[0][2].includes('int[][] mat = {   // 2 שורות, 3 עמודות'), edits[0][2]);
    assert.strictEqual(edits[0][2].split('\n').length, 4);

    // --- ספרייה מתנגשת (jar מקורי של unit4) מזוהה
    fs.writeFileSync(path.join(ws, 'lib', 'unit4.jar'), Buffer.concat([Buffer.from('PK...'), Buffer.from('unit4/collectionsLib/Stack.class')]));
    assert.deepStrictEqual(require(path.join(ext, 'project.js')).findConflicts(ws).map(f => path.basename(f)), ['unit4.jar']);
    fs.unlinkSync(path.join(ws, 'lib', 'unit4.jar'));

    console.log(log.join('\n'));
    console.log('\nALL VS CODE TESTS PASSED\nworkspace: ' + ws);
})().catch(e => { console.error(log.join('\n')); console.error(e); process.exit(1); });
