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
        showTextDocument: async doc => { opened = doc.fileName; return vscode.window.activeTextEditor && vscode.window.activeTextEditor.document === doc ? vscode.window.activeTextEditor : { selection: null, revealRange() {} }; },
        createStatusBarItem: () => ({ show() {}, hide() {}, dispose() {} }),
        registerTreeDataProvider: () => ({ dispose() {} }),
        onDidChangeActiveTextEditor: () => ({ dispose() {} }),
        activeTextEditor: null,
        createOutputChannel: () => ({ clear() {}, appendLine() {}, show() {}, dispose() {} }),
        withProgress: async (o, f) => f(),
        createTerminal: o => { terminalOpts = o; return { show() {}, dispose() {} }; },
        createWebviewPanel: (id, title) => {
            const panel = { id, title, html: '', posted: [], handler: null, disposed: false };
            panel.webview = { cspSource: 'vscode-resource:', asWebviewUri: u => ({ toString: () => 'vscode-resource:' + u.fsPath }), set html(v) { panel.html = v; }, get html() { return panel.html; }, postMessage: async m => { panel.posted.push(m); return true; },
                              onDidReceiveMessage: fn => { panel.handler = fn; return { dispose() {} }; } };
            panel.dispose = () => { panel.disposed = true; };
            panel.onDidDispose = () => ({ dispose() {} });
            panels.push(panel);
            return panel;
        }
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

/** "הוסף מבנה נתונים": פותח את הפקודה, ומתנהג כמו הדף בחלון — שולח מצב, מקבל מה להציג, ובסוף לוחץ אישור */
async function addDialog(command, state, steps) {
    const before = panels.length;
    await command();
    const p = panels.slice(before).find(x => x.id === 'dsviz.addStructure');
    assert(p && p.handler, 'חלון "הוסף מבנה נתונים" לא נפתח');
    const initial = JSON.parse(/const OPT = (.*);\n/.exec(p.html)[1]).initial;
    let st = Object.assign({}, initial, state), view = null;
    const send = async (reason, patch) => { st = Object.assign({}, st, patch || {}); await p.handler({ type: 'state', state: st, reason }); view = p.posted[p.posted.length - 1].view;
        st = Object.assign(st, { structure: view.structure, inside: view.inside, type: view.type, random: view.random, count: view.count });
        if (view.setValues !== undefined) st.values = view.setValues; return view; };
    await send('init');
    for (const [reason, patch] of steps || []) await send(reason, patch);
    return { panel: p, set: send, state: () => st, view: () => view, ok: async () => { await p.handler({ type: 'ok', state: st }); return view; } };
}

(async () => {
    const extension = require(path.join(ext, 'extension.js'));
    const globalState = new Map();
    extension.activate({ subscriptions: [], extensionPath: ext,
        globalState: { get: k => globalState.get(k), update: async (k, v) => globalState.set(k, v) } });

    // --- תרגיל 1: מחסנית של Integer, מחזירה int, תרגיל ברקורסיה — דרך הכפתור "מחסנית"
    answers.push(byId(''), byId('Integer'), byId('int'), byIds(['recursive']), keep, keep, value('solve'));
    await commands['dsviz.newStack']();
    const f1 = path.join(ws, 'src', 'Targil1.java');
    assert(fs.existsSync(f1), 'Targil1.java not created');
    assert.strictEqual(opened, f1);
    assert(fs.existsSync(path.join(ws, 'lib', 'dsviz.jar')), 'jar not copied');
    const settings = JSON.parse(fs.readFileSync(path.join(ws, '.vscode', 'settings.json'), 'utf8'));
    assert.deepStrictEqual(settings['java.project.referencedLibraries'], ['lib/**/*.jar']);
    assert.deepStrictEqual(settings['java.project.sourcePaths'], ['src']);

    // --- תרגיל 2: דרך "תרגיל חדש" — רשימה של String, מחזירה מבנה, ערכים משלנו, שם משלנו
    answers.push(byId('list'), byId(''), byId('String'), byId('structure'), byIds([]),
                 value('hello, "world", a\\b'), value('Merge'), value('merge'));
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
    // ערכים אקראיים גם במבנה בתוך מבנה: "4" = 4 תורים אקראיים בתוך המחסנית (ועד 12)
    answers.push(byId('stack'), byId('queue'), byId('Integer'), byId('void'), byIds(['random']), value('4'), keep, value('RandomQueues'), value('solve'));
    await commands['dsviz.newExercise']();
    const fr = fs.readFileSync(path.join(ws, 'src', 'RandomQueues.java'), 'utf8');
    assert.strictEqual((fr.match(/stk\.push\(inner\);/g) || []).length, 4, fr.slice(0, 900));
    answers.length = 0;
    answers.push(byId('stack'), byId('queue'), byId('Integer'), byId('void'), byIds(['random']), value('13'));
    await assert.rejects(commands['dsviz.newExercise'](), /validation/);
    answers.length = 0;
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

    // --- הוסף מבנה נתונים: "מכונית" — נפתח חלון, ואחרי אישור נכנס הקוד ונפתח חלון "מה יש במחלקה" (Car)
    const mkEditor = (ls, line, lang, edits) => ({ selection: { active: { line } }, viewColumn: 1,
        document: { languageId: lang, lineCount: ls.length, getText: () => ls.join('\n'),
                    lineAt: i => ({ text: ls[i], range: { line: i, start: { line: i, c: 0 }, end: { line: i, c: 1 } } }),
                    offsetAt: pos => ls.slice(0, pos.line).join('\n').length + (pos.line ? 1 : 0) + (pos.c ? ls[pos.line].length : 0), positionAt: o => ({ offset: o }) },
        edit: async cb => { cb({ replace: (r, t) => edits.push(['replace', r.line, t]), insert: (p, t) => edits.push(['insert', p, t]) }); return true; } });
    {
        const ls = ['public class X {', '    public static void main(String[] args) {', '        ', '    }', '}'];
        const ed2 = [];
        vscode.window.activeTextEditor = mkEditor(ls, 2, 'java', ed2);
        const d = await addDialog(commands['dsviz.insert.car'], {});
        assert.strictEqual(d.view().structure, 'car');
        assert(d.view().preview.outer === 'card' && d.view().hint === 'c1', JSON.stringify(d.view()).slice(0, 300));
        const before = panels.length;
        await d.ok();
        assert(/Car c1 = Car\.random\(\);/.test(ed2[0][2]), JSON.stringify(ed2));
        assert(d.panel.disposed, 'החלון לא נסגר אחרי האישור');
        assert(panels.slice(before).some(p => p.id === 'dsviz.classInfo' && p.title.includes('Car')), JSON.stringify(panels.map(p => p.id + ':' + p.title)));
    }

    // --- הוסף מבנה נתונים: תור של String (4 ערכים) בשורה ריקה בתוך main — בלי import בקובץ
    const lines = ['public class X {', '    public static void main(String[] args) {', '        ', '    }', '}'];
    const edits = [];
    vscode.window.activeTextEditor = mkEditor(lines, 2, 'java', edits);
    let d = await addDialog(commands['dsviz.insert.queue'], {}, [['type', { type: 'String' }], ['values', { values: 'dog, cat, bird, fish' }]]);
    await d.ok();
    assert.strictEqual(edits[0][0], 'replace');
    // מעל המבנה — הערה קצרה על פעולות העזר מהספרייה
    assert(/^        \/\/ עזר מהספרייה: size\(q1\)[^\n]*\n        Queue<String> q1 = new Queue<String>\(\);\n(        q1\.insert\("\w+"\);.*\n){3}        q1\.insert\("\w+"\);$/.test(edits[0][2]), edits[0][2]);
    assert.strictEqual(edits[1][2], 'import unit4.collectionsLib.*;\nimport static unit4.collectionsLib.Helpers.*;\n\n');   // + פעולות העזר: size(x) / duplicate(x)
    // מבנה בתוך מבנה, עם 2 מבנים אקראיים
    edits.length = 0;
    d = await addDialog(commands['dsviz.insert.queue'], {}, [['inside', { inside: 'stack' }], ['random', { random: true }], ['count', { count: 2 }]]);
    assert.strictEqual(d.view().setValues === undefined ? d.state().values.split(';').length : d.view().setValues.split(';').length, 2);
    await d.ok();
    assert(edits[0][2].includes('Queue<Stack<Integer>> q1 = new Queue<Stack<Integer>>();') && edits[0][2].includes('q1.insert(q1Inner);'), edits[0][2]);
    // מערך דו-ממדי עם ערכים ידניים
    edits.length = 0;
    d = await addDialog(commands['dsviz.insert.matrix'], {}, [['values', { values: '1, 2, 3; 4, 5, 6' }]]);
    await d.ok();
    assert(edits[0][2].includes('int[][] mat = {') && edits[0][2].includes('{4, 5, 6}'), edits[0][2]);
    // "?" — הדרכה לשימוש: נפתח חלון עם ארבע ההדרכות
    {
        fs.mkdirSync(path.join(ext, 'resources', 'help'), { recursive: true });   // (בהתקנה אמיתית יש שם את הסרטונים)
        const before = panels.length;
        let offered;
        answers.push(items => { offered = items.map(i => i.label); return items[2]; });   // בוחרים את ההדרכה השלישית
        await commands['dsviz.help']();
        for (const t of ['יצירת מבנה נתונים חדש', 'דוגמאות פתורות', 'מבנה בתוך מבנה', 'מוסיפים התאמות']) assert(offered.some(l => l.includes(t)), t);
        const hp = panels.slice(before).find(x => x.id === 'dsviz.help');
        assert(hp, 'חלון ההדרכה לא נפתח');
        assert(hp.html.includes('"id":"c"') && /var id = "c"/.test(hp.html), 'צריך להיפתח הנגן של ההדרכה שנבחרה');
        assert(/vscode-resource:.*help[\\/]c\.mp4/.test(hp.html), 'כתובת הסרטון חסרה');
        assert(hp.html.includes('1.25') && hp.html.includes('1.5'), 'בחירת מהירות חסרה');
    }
    // שכבת הגנה: הסמן מחוץ לפעולה (בגוף המחלקה) — אזהרה, והחלון לא נפתח
    {
        const outside = ['public class X {', '    ', '    public static void main(String[] args) {', '    }', '}'];
        vscode.window.activeTextEditor = mkEditor(outside, 1, 'java', edits);
        const before = panels.length, warns = log.length;
        await commands['dsviz.insert.stack']();
        assert.strictEqual(panels.length, before, 'החלון לא אמור להיפתח מחוץ לפעולה');
        assert(log.slice(warns).some(l => l.startsWith('warn:') && l.includes('מחוץ לפעולה')), JSON.stringify(log.slice(warns)));
    }
    // שם תפוס (stk כבר מוגדר בקובץ) נחסם, וההצעה מדלגת עליו
    const taken = ['public class X {', '    public static void main(String[] args) {', '        Stack<Integer> stk = new Stack<Integer>();', '        ', '    }', '}'];
    vscode.window.activeTextEditor = mkEditor(taken, 3, 'java', edits);
    d = await addDialog(commands['dsviz.insert.stack'], {}, [['name', { name: 'stk' }]]);
    assert(d.view().error && d.view().error.includes('כבר יש בקוד') && !d.view().ok, JSON.stringify(d.view().error));
    edits.length = 0;
    const blocked = await d.ok();
    assert.strictEqual(edits.length, 0, 'שם תפוס לא אמור להיכנס לקוד');
    assert.strictEqual((await d.set('name', { name: '' })).hint, 'stk2');

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

    // --- תרגיל חדש ב-C#: מחסנית של Integer, מחזירה int, ברקורסיה — נוצר פרויקט שלם
    answers.push(byId(''), byId('Integer'), byId('int'), byIds(['recursive']), keep, keep, value('solve'));
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

    // --- הוסף מבנה נתונים ב-C#: מכונית; מוסיפים using
    {
        const ls = ['class X', '{', '    static void Main(string[] args)', '    {', '        ', '    }', '}'];
        const ed3 = [];
        vscode.window.activeTextEditor = mkEditor(ls, 4, 'csharp', ed3);
        const dc = await addDialog(commands['dsviz.insert.car'], {});
        await dc.ok();
        assert(/Car c1 = Car\.Random\(\);/.test(ed3[0][2]), JSON.stringify(ed3));
        assert(ed3[1], JSON.stringify(ed3));
        assert(ed3[1][2].includes('using Unit4.CollectionsLib;') && ed3[1][2].includes('using System;'), ed3[1][2]);
        // רשימה של מחסניות ב-C# עם ערכים ידניים; והשם "string" נחסם (מילה שמורה)
        ed3.length = 0;
        const dl = await addDialog(commands['dsviz.insert.list'], {}, [['inside', { inside: 'stack' }], ['values', { values: '1, 2; 3' }], ['name', { name: 'string' }]]);
        assert(dl.view().error && dl.view().error.includes('שמורה'), dl.view().error);
        await dl.set('name', { name: 'lst' });
        await dl.ok();
        assert(ed3[0][2].includes('Node<Stack<int>> lst = null;') && ed3[0][2].includes('lstInner.Push(3);'), ed3[0][2]);
    }
    const cm = require(path.join(ext, 'cs.js'));
    assert.strictEqual(cm.detectLanguage(ws2), 'csharp');
    assert.strictEqual(cm.projectDirOf(cs1, ws2), path.join(ws2, 'Exercises', 'Targil1'));

    console.log(log.join('\n'));
    console.log('\nALL VS CODE TESTS PASSED\nworkspace: ' + ws);
})().catch(e => { console.error(log.join('\n')); console.error(e); process.exit(1); });
