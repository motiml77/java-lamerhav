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
let testLang = 'java';

const vscode = {
    workspace: {
        workspaceFolders: [{ uri: { fsPath: ws }, name: 'ws' }],
        openTextDocument: async f => { const t = fs.readFileSync(f, 'utf8').split('\n');
            return { fileName: f, lineCount: t.length, lineAt: i => ({ text: t[i] }), languageId: 'java' }; },
        getWorkspaceFolder: () => vscode.workspace.workspaceFolders[0],
        getConfiguration: () => ({ get: k => (k === 'language' ? testLang : undefined) }),
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
    answers.push(byId(''), byId('Integer'), byId('int'), byIds(['extra']), keep, keep, value('solve'));
    await commands['dsviz.newStack']();
    const f1 = path.join(ws, 'src', 'Targil1.java');
    assert(fs.existsSync(f1), 'Targil1.java not created');
    assert.strictEqual(opened, f1);
    assert(fs.existsSync(path.join(ws, 'lib', 'dsviz.jar')), 'jar not copied');
    const settings = JSON.parse(fs.readFileSync(path.join(ws, '.vscode', 'settings.json'), 'utf8'));
    assert.deepStrictEqual(settings['java.project.referencedLibraries'], ['lib/**/*.jar']);
    assert.deepStrictEqual(settings['java.project.sourcePaths'], ['src']);

    // --- תרגיל 2: דרך "תרגיל חדש" — רשימה של String, מחזירה מבנה, שני מבנים, ערכים משלנו, שם משלנו
    answers.push(byId('list'), byId(''), byId('String'), byId('structure'), byIds(['second']),
                 value('hello, "world", a\\b'), keep, value('Merge'), value('merge'));
    await commands['dsviz.newExercise']();
    const f2 = path.join(ws, 'src', 'Merge.java');
    assert(fs.existsSync(f2), 'Merge.java not created');

    // --- קומבינציה: מחסנית של מחסניות (דרך הכפתור "מחסנית" ← "מה יש בכל תא?" ← מחסניות), ורשימה מקושרת של תורים (דרך "תרגיל חדש")
    answers.push(byId('stack'), byId('Integer'), byId('int'), byIds([]), keep, value('StackOfStacks'), value('solve'));
    await commands['dsviz.newStack']();
    const fc = fs.readFileSync(path.join(ws, 'src', 'StackOfStacks.java'), 'utf8');
    assert(fc.includes('public static int solve(Stack<Stack<Integer>> stk)') && fc.includes('stk.push(inner);') && fc.includes('// תרגיל: מחסנית של מחסניות של Integer'), fc.slice(0, 600));
    answers.push(byId('list'), byId('queue'), byId('String'), byId('void'), byIds([]), keep, value('ListOfQueues'), value('solve'));
    await commands['dsviz.newExercise']();
    const fl = fs.readFileSync(path.join(ws, 'src', 'ListOfQueues.java'), 'utf8');
    assert(fl.includes('Node<Queue<String>> lst') && fl.includes('lst = new Node<Queue<String>>(inner, lst);'), fl.slice(0, 600));
    // "מה יש בכל תא?" מציע ערכים רגילים ראשון (Enter), ולא מוצע במטריצה ובעץ חיפוש
    let insideItems = null;
    answers.push((items) => { insideItems = items; return items[0]; }, byId('Integer'), byId('void'), byIds([]), keep, value('Plain1'), value('solve'));
    await commands['dsviz.newQueue']();
    assert.deepStrictEqual(insideItems.map(i => i.id), ['', 'stack', 'queue', 'list']);
    answers.length = 0;

    // --- תרגיל 3: שם תפוס / ערך שגוי — validateInput חייב לחסום
    answers.push(byId(''), byId('Integer'), byId('void'), byIds([]), value('1, x, 3'));
    await assert.rejects(commands['dsviz.newQueue'](), /validation/);
    answers.length = 0;
    answers.push(byId(''), byId('Integer'), byId('void'), byIds([]), keep, value('Targil1'));
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
    answers.push(byId(''), byId('String'), value('4'), keep);
    await commands['dsviz.insert.queue']();
    assert.strictEqual(edits[0][0], 'replace');
    assert(/^        Queue<String> q1 = new Queue<String>\(\);\n(        q1\.insert\("\w+"\);.*\n){3}        q1\.insert\("\w+"\);$/.test(edits[0][2]), edits[0][2]);
    assert.strictEqual(edits[1][2], 'import unit4.collectionsLib.*;\n\n');
    edits.length = 0;
    answers.push(byId('stack'), byId('Integer'), value('2'), keep);
    await commands['dsviz.insert.queue']();
    assert(edits[0][2].includes('Queue<Stack<Integer>> q1 = new Queue<Stack<Integer>>();') && edits[0][2].includes('q1.insert(q1Inner);'), edits[0][2]);
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

    // ===================== C# =====================
    testLang = 'csharp';
    const ws2 = fs.mkdtempSync(path.join(os.tmpdir(), 'dsviz-ws-cs-'));
    vscode.workspace.workspaceFolders = [{ uri: { fsPath: ws2 }, name: 'ws2' }];
    for (const k of Object.keys(require.cache)) if (k.startsWith(ext)) delete require.cache[k];   // התוסף נטען מחדש עם ה-mock
    const csExt = require(path.join(ext, 'extension.js'));
    const csState = new Map();
    csExt.activate({ subscriptions: [], extensionPath: ext, globalState: { get: k => csState.get(k), update: async (k, v) => csState.set(k, v) } });

    // --- תרגיל חדש ב-C#: מחסנית של Integer, מחזירה int, עם פרמטר x — נוצר פרויקט שלם
    answers.push(byId(''), byId('Integer'), byId('int'), byIds(['extra']), keep, keep, value('solve'));
    await commands['dsviz.newStack']();
    const cs1 = path.join(ws2, 'Exercises', 'Targil1', 'Program.cs');
    assert(fs.existsSync(cs1), 'C# Program.cs not created');
    assert.strictEqual(opened, cs1);
    assert(fs.existsSync(path.join(ws2, 'Exercises', 'Targil1', 'Targil1.csproj')), 'csproj missing');
    assert(/\$\(DsVizLib\)/.test(fs.readFileSync(path.join(ws2, 'Exercises', 'Targil1', 'Targil1.csproj'), 'utf8')));
    const launch = JSON.parse(fs.readFileSync(path.join(ws2, 'Exercises', 'Targil1', 'Properties', 'launchSettings.json'), 'utf8'));
    assert(launch.profiles['הרצה ויזואלית'].executablePath.endsWith('dsviz-cs.exe'));
    assert(fs.existsSync(path.join(ws2, 'lib', 'Unit4.CollectionsLib.dll')), 'C# library not copied');
    assert(/DsVizLib/.test(fs.readFileSync(path.join(ws2, 'Directory.Build.props'), 'utf8')));
    const code1 = fs.readFileSync(cs1, 'utf8');
    assert(code1.includes('using Unit4.CollectionsLib;') && code1.includes('public static void Main(string[] args)') && code1.includes('stk.Push('), code1.slice(0, 400));
    assert(!code1.includes('System.out') && !code1.includes('import '));

    // --- שם פרויקט תפוס נחסם; מילה שמורה של C# נחסמת
    answers.length = 0;
    answers.push(byId(''), byId('Integer'), byId('void'), byIds([]), keep, value('Targil1'));
    await assert.rejects(commands['dsviz.newQueue'](), /validation: כבר קיים/);
    answers.length = 0;
    answers.push(byId(''), byId('Integer'), byId('void'), byIds([]), keep, keep, value('string'));
    await assert.rejects(commands['dsviz.newQueue'](), /validation/);
    answers.length = 0;

    // --- דוגמאות C#
    answers.push(items => items[0]);
    await commands['dsviz.examples']();
    assert(fs.readdirSync(path.join(ws2, 'Examples')).length >= 8, 'C# examples missing');

    // --- הרצה ויזואלית של קובץ .cs: מריצים את dsviz-cs על תיקיית הפרויקט (כאן — סקריפט מדומה)
    const fake = path.join(ws2, 'fake-dsviz-cs');
    fs.writeFileSync(fake, '#!/bin/sh\necho "$@"\n', { mode: 0o755 });
    process.env.DSVIZ_CS = fake;
    terminalOpts = null;
    vscode.window.activeTextEditor = { document: { fileName: cs1, uri: {}, languageId: 'csharp', getText: () => code1, save: async () => true } };
    await commands['dsviz.run']();
    assert(terminalOpts, 'C# run did not open a terminal');
    assert.strictEqual(terminalOpts.shellPath, fake);
    assert.deepStrictEqual(terminalOpts.shellArgs, [path.join(ws2, 'Exercises', 'Targil1')]);
    delete process.env.DSVIZ_CS;

    // --- הוספה לקוד ב-C#: מכונית; מוסיפים using
    {
        const ls = ['class X', '{', '    static void Main(string[] args)', '    {', '        ', '    }', '}'];
        const ed3 = [];
        vscode.window.activeTextEditor = { selection: { active: { line: 4 } },
            document: { languageId: 'csharp', getText: () => ls.join('\n'), lineAt: i => ({ text: ls[i], range: { line: i } }), positionAt: o => ({ offset: o }) },
            edit: async cb => { cb({ replace: (r, t) => ed3.push(t), insert: (p, t) => ed3.push(t) }); return true; } };
        answers.push(keep);
        await commands['dsviz.insert.car']();
        assert(/Car c1 = Car\.Random\(\);/.test(ed3[0]), ed3[0]);
        assert(ed3[1].includes('using Unit4.CollectionsLib;') && ed3[1].includes('using System;'), ed3[1]);
    }
    const cm = require(path.join(ext, 'cs.js'));
    assert.strictEqual(cm.detectLanguage(ws2), 'csharp');
    assert.strictEqual(cm.projectDirOf(cs1, ws2), path.join(ws2, 'Exercises', 'Targil1'));

    console.log(log.join('\n'));
    console.log('\nALL VS CODE TESTS PASSED\nworkspace: ' + ws);
})().catch(e => { console.error(log.join('\n')); console.error(e); process.exit(1); });
