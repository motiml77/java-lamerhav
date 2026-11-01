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
const lensProviders = [];
let clipboard = null;
// ההודעות של VS Code (בפינה): כל אחת חייבת להתחיל ב-RLM (U+200F); onMessage — "לוחץ" על כפתור (או לא)
const shown = [];
let onMessage = null;
const notify = kind => async (m, ...rest) => {
    assert(typeof m === 'string' && m.startsWith('\u200F'), kind + ' — הודעה בלי RLM בהתחלה: ' + JSON.stringify(m));
    const o = rest.find(x => x && typeof x === 'object');
    const buttons = rest.filter(x => typeof x === 'string');
    const text = m.slice(1);
    shown.push({ kind, text, buttons, modal: !!(o && o.modal) });
    log.push(kind + ': ' + text + (o && o.detail ? '\n' + o.detail : ''));
    return onMessage ? onMessage(text, buttons) : undefined;
};
const config = {};   // הגדרות: 'java.project.sourcePaths' → [...]
const decorations = [];   // { type, ranges } — setDecorations של עורכים מדומים
let docChange = null;
const openedSeq = [];

const vscode = {
    workspace: {
        workspaceFolders: [{ uri: { fsPath: ws }, name: 'ws' }],
        openTextDocument: async f => { const t = fs.readFileSync(f, 'utf8').split('\n');
            return { fileName: f, lineCount: t.length, lineAt: i => ({ text: t[i] }), languageId: 'java' }; },
        getWorkspaceFolder: () => vscode.workspace.workspaceFolders[0],
        getConfiguration: sec => ({ get: k => (sec === 'dsviz' && k === 'language' ? testLang : config[sec + '.' + k]) }),
        onDidChangeTextDocument: fn => { docChange = fn; return { dispose() {} }; },
        saveAll: async () => true
    },
    languages: { createDiagnosticCollection: () => ({ clear: () => diag.clear(), set: (u, l) => diag.set(u.fsPath, l), dispose() {} }),
                 registerCodeLensProvider: (sel, provider) => { lensProviders.push(provider); return { dispose() {} }; } },
    env: { openExternal: () => {}, clipboard: { writeText: async t => { clipboard = t; } } },
    ProgressLocation: { Window: 10, Notification: 15 },
    EventEmitter: class { constructor() { this.event = () => ({ dispose() {} }); } fire() {} },
    CodeLens: class { constructor(range, command) { this.range = range; this.command = command; } },
    DiagnosticSeverity: { Error: 0, Warning: 1 },
    Diagnostic: class { constructor(r, m, s) { this.range = r; this.message = m; this.severity = s; } },
    window: {
        showQuickPick: async (items, opts) => { const a = answers.shift(); log.push('pick: ' + opts.title);
            return a(items, opts); },
        showInputBox: async opts => { const a = answers.shift(); const v = a(opts); log.push('input: ' + opts.title + ' = ' + v);
            const err = opts.validateInput && opts.validateInput(v); if (err) throw new Error('validation: ' + err); return v; },
        showInformationMessage: notify('info'),
        showWarningMessage: notify('warn'),
        showErrorMessage: notify('error'),
        createTextEditorDecorationType: o => ({ options: o, dispose() {} }),
        visibleTextEditors: [],
        onDidChangeVisibleTextEditors: () => ({ dispose() {} }),
        showTextDocument: async (doc, o) => { opened = doc.fileName; openedSeq.push({ file: doc.fileName, preview: o && o.preview, preserveFocus: !!(o && o.preserveFocus), line: o && o.selection ? o.selection.line : undefined }); return vscode.window.activeTextEditor && vscode.window.activeTextEditor.document === doc ? vscode.window.activeTextEditor : { selection: null, revealRange() {} }; },
        createStatusBarItem: () => ({ show() {}, hide() {}, dispose() {} }),
        setStatusBarMessage: m => { log.push("status: " + m); return { dispose() {} }; },
        showOpenDialog: async () => [],
        registerTreeDataProvider: () => ({ dispose() {} }),
        onDidChangeActiveTextEditor: () => ({ dispose() {} }),
        activeTextEditor: null,
        createOutputChannel: () => ({ clear() {}, appendLine() {}, show() {}, dispose() {} }),
        withProgress: async (o, f) => f({ report() {} }, { onCancellationRequested() {} }),
        createTerminal: o => { terminalOpts = o; return { show() {}, dispose() {} }; },
        createWebviewPanel: (id, title) => {
            const panel = { id, title, html: '', posted: [], handler: null, disposed: false };
            panel.webview = { cspSource: 'vscode-resource:', asWebviewUri: u => ({ toString: () => 'vscode-resource:' + u.fsPath }), set html(v) { panel.html = v; }, get html() { return panel.html; }, postMessage: async m => { panel.posted.push(m); return true; },
                              onDidReceiveMessage: fn => { panel.handler = fn; return { dispose() {} }; } };
            panel.dispose = () => { panel.disposed = true; };
            panel.reveal = () => {};
            panel.onDidDispose = () => ({ dispose() {} });
            panels.push(panel);
            return panel;
        }
    },
    commands: { registerCommand: (id, fn) => { commands[id] = fn; return { dispose() {} }; }, executeCommand: async () => {} },
    extensions: { getExtension: () => ({}) },
    StatusBarAlignment: { Left: 1 }, TextEditorRevealType: { InCenter: 2 }, ViewColumn: { Beside: -2, Active: -1 }, OverviewRulerLane: { Left: 1 },
    Position: class { constructor(l, c) { this.line = l; this.character = c; } },
    Selection: class { constructor(a, b) { this.a = a; this.b = b; } },
    Range: class { constructor(a, b, c, d) { this.a = a; this.b = b; this.line = a; this.start = { line: a, character: b }; this.end = { line: c, character: d }; } },
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
    // --- כל ההודעות של התוסף עוברות דרך ui.js, שמוסיף RLM בהתחלה (ההודעה מוצגת מימין לשמאל): אין קריאה ישירה ל-window.show*Message
    for (const f of fs.readdirSync(ext).filter(f => f.endsWith('.js') && f !== 'ui.js')) {
        const t = fs.readFileSync(path.join(ext, f), 'utf8');
        assert(!/\.show(?:Information|Warning|Error)Message\s*\(/.test(t), f + ': הודעה ישירה (בלי ui.info / ui.warn / ui.error, ובלי RLM)');
    }
    assert.strictEqual((fs.readFileSync(path.join(ext, 'ui.js'), 'utf8').match(/\.show(?:Information|Warning|Error)Message\(rtl\(text\)/g) || []).length, 3, 'ui.js: RLM בכל ההודעות');
    {
        const ui = require(path.join(ext, 'ui.js'));
        assert.strictEqual(ui.rtl('שלום'), '\u200Fשלום');
        assert.strictEqual(ui.rtl('\u200Fשלום'), '\u200Fשלום');
        assert.strictEqual(ui.explain("';' expected"), 'חסר ; בסוף הפקודה');
        assert.strictEqual(ui.explain('incompatible types: String cannot be converted to int'), 'אי התאמה בטיפוסים: אי אפשר להכניס String למשתנה מטיפוס int');
        assert.strictEqual(ui.explain('; expected', 'CS1002'), 'חסר ; בסוף הפקודה');
        assert.strictEqual(ui.explain('something new'), '');
        assert.deepStrictEqual(ui.parseError('StackTargil.cs:12: ; expected   [CS1002]'), { file: 'StackTargil.cs', line: 12, message: '; expected', code: 'CS1002' });
        assert.strictEqual(ui.inlineHtml('כתבו פעולה count(Stack<Integer> s) המחזירה.'), 'כתבו פעולה <code dir="ltr">count(Stack&lt;Integer&gt; s)</code> המחזירה.');
        assert.strictEqual(ui.inlineHtml('עבור המחסנית [1, 2, 3]? הסבירו.'), 'עבור המחסנית <bdi dir="ltr">[1, 2, 3]</bdi>? הסבירו.');
        assert.strictEqual(ui.inlineHtml('יש 3 סעיפים, 2 בקוד.'), 'יש 3 סעיפים, 2 בקוד.');
        const rich = ui.richHtml('מה יודפס?\npublic static int f(int n) {\n    return n;\n}\nנמקו.');
        assert(rich.includes('<pre dir="ltr">public static int f(int n) {\n    return n;\n}</pre>') && rich.includes('<p>נמקו.</p>'), rich);
    }
    // --- הפעולות של הסעיפים שעוד לא כתבו בהן (Java: "{" בסוף הכותרת; C#: "{" בשורה משלו), והשמות התפוסים ב-C#
    {
        const sub = require(path.join(ext, 'submit.js'));
        const M = n => '        // ====\u202Bכאן עונים סעיף ' + n + "'\u202C====";
        const java = ['class A {', '    static int f(Stack<Integer> s) {', M('א'), '        ', '    }', '', '    static void g() {', M('ב'), '        g();', '    }', '}'].join('\n');
        assert.deepStrictEqual(sub.emptyAnswers(java), [{ start: 2, end: 3 }]);
        const cs = ['class A', '{', '    static int F()', '    {', M('א'), '', '    }', '    static void G()', '    {', '        int x = 1;', M('ב'), '    }', '}'].join('\n');
        assert.deepStrictEqual(sub.emptyAnswers(cs), [{ start: 4, end: 5 }], 'C#: הסעיף השני כבר לא ריק');
        assert.deepStrictEqual(sub.emptyAnswers('void f() {\n        // ====\u202Bכאן כותבים את התשובה\u202C====\n    }'), [{ start: 1, end: 1 }]);
        const cr = fs.mkdtempSync(path.join(os.tmpdir(), 'dsviz-cs-taken-'));
        fs.writeFileSync(path.join(cr, 'DataStructures-CS.sln'), 'Microsoft Visual Studio Solution File\nProject("{FAE04EC0-301F-11D3-BF4B-00C04F79EFBC}") = "Exercise", "Exercise\\Exercise.csproj", "{1}"\nEndProject\n'
            + 'Project("{9A19103F-16F7-4668-BE54-9A1E7A4F7556}") = "Ex01_Stacks", "Old\\Ex01_Stacks\\Ex01_Stacks.csproj", "{2}"\nEndProject\n');
        fs.mkdirSync(path.join(cr, 'Exercises', 'Ex02_Queues'), { recursive: true });
        assert.deepStrictEqual(sub.takenNames('csharp', [cr], { exercisesDir: r => path.join(r, 'Exercises') }), ['Ex01_Stacks', 'Ex02_Queues', 'Exercise']);
        fs.rmSync(cr, { recursive: true, force: true });
        assert.strictEqual(sub.mainFile([]), null);
    }

    const extension = require(path.join(ext, 'extension.js'));
    const globalState = new Map();
    // --- ספריית הקורס בכל תיקייה (בלי שהתלמיד מבקש): תיקייה שהוא פתח בעצמו, עם קוד שמשתמש ב-Queue של הקורס,
    //     מקבלת lib/dsviz.jar וההפניה — בלי src חדש ובלי לשנות את תיקיית הקוד. Maven, java.util ו-jar מקורי של unit4 — לא נוגעים
    const tmpDir = n => fs.mkdtempSync(path.join(os.tmpdir(), 'dsviz-' + n + '-'));
    const own = tmpDir('own'), eclipseProj = tmpDir('ecl'), mvn = tmpDir('mvn'), util = tmpDir('util'), orig = tmpDir('orig'), saved = tmpDir('saved');
    fs.writeFileSync(path.join(own, 'Ex.java'), 'import unit4.collectionsLib.*;\npublic class Ex { Queue<Integer> q = new Queue<Integer>(); }\n');
    fs.mkdirSync(path.join(eclipseProj, 'src', 'a'), { recursive: true });
    fs.writeFileSync(path.join(eclipseProj, 'src', 'a', 'T.java'), 'package a;\npublic class T { BinNode<Integer> t; }\n');
    fs.writeFileSync(path.join(eclipseProj, '.classpath'), '<?xml version="1.0"?>\n<classpath>\n\t<classpathentry kind="src" path="src"/>\n</classpath>\n');
    fs.writeFileSync(path.join(mvn, 'pom.xml'), '<project/>');
    fs.writeFileSync(path.join(mvn, 'A.java'), 'import unit4.collectionsLib.*;\nclass A {}\n');
    fs.writeFileSync(path.join(util, 'U.java'), 'import java.util.Stack;\nclass U { Stack<Integer> s; }\n');
    fs.mkdirSync(path.join(orig, 'lib'));
    fs.writeFileSync(path.join(orig, 'lib', 'unit4.jar'), Buffer.concat([Buffer.from('PK'), Buffer.from('unit4/collectionsLib/Stack.class')]));
    fs.writeFileSync(path.join(orig, 'O.java'), 'import unit4.collectionsLib.*;\nclass O {}\n');
    const projectMod = require(path.join(ext, 'project.js'));
    assert(projectMod.usesCourseLib('Node<String> p;') && !projectMod.usesCourseLib('import java.util.Queue;\nQueue<Integer> q;') && projectMod.usesCourseLib('import unit4.collectionsLib.Queue;'));
    let onSave = null;
    const folder = d => ({ uri: { fsPath: d }, name: path.basename(d) });
    vscode.workspace.workspaceFolders = [ws, own, eclipseProj, mvn, util, orig, saved].map(d => d === ws ? vscode.workspace.workspaceFolders[0] : folder(d));
    vscode.workspace.onDidSaveTextDocument = fn => { onSave = fn; return { dispose() {} }; };
    const realGetWs = vscode.workspace.getWorkspaceFolder;
    vscode.workspace.getWorkspaceFolder = u => folder(path.dirname(u.fsPath));
    extension.activate({ subscriptions: [], extensionPath: ext,
        globalState: { get: k => globalState.get(k), update: async (k, v) => globalState.set(k, v) } });
    assert(fs.existsSync(path.join(own, 'lib', 'dsviz.jar')), 'ספריית הקורס לא נוספה לתיקייה של התלמיד');
    const ownSettings = JSON.parse(fs.readFileSync(path.join(own, '.vscode', 'settings.json'), 'utf8'));
    assert.deepStrictEqual(ownSettings, { 'java.project.referencedLibraries': ['lib/**/*.jar'] }, JSON.stringify(ownSettings));
    assert(!fs.existsSync(path.join(own, 'src')), 'לא נוצרת תיקיית src בתיקייה של התלמיד');
    assert(fs.readFileSync(path.join(eclipseProj, '.classpath'), 'utf8').includes('path="lib/dsviz.jar"') && fs.existsSync(path.join(eclipseProj, 'lib', 'dsviz.jar')), 'פרויקט Eclipse: ההפניה ב-.classpath');
    for (const d of [mvn, util, orig]) assert(!fs.existsSync(path.join(d, 'lib', 'dsviz.jar')), 'נגעו ב-' + d);
    assert(!projectMod.needsLibrary(own), 'בפעם השנייה — כבר מוגדר');
    // שמירה של קובץ Java בתיקייה שעד עכשיו לא היה בה קוד כזה
    assert(!fs.existsSync(path.join(saved, 'lib')));
    fs.writeFileSync(path.join(saved, 'S.java'), 'public class S { Stack<String> s; }\n');
    onSave({ languageId: 'java', fileName: path.join(saved, 'S.java'), uri: { fsPath: path.join(saved, 'S.java') } });
    assert(fs.existsSync(path.join(saved, 'lib', 'dsviz.jar')), 'אחרי שמירה — הספרייה נוספה');
    vscode.workspace.workspaceFolders = [vscode.workspace.workspaceFolders[0]];
    vscode.workspace.getWorkspaceFolder = realGetWs;
    delete vscode.workspace.onDidSaveTextDocument;
    log.push('ספריית הקורס בכל תיקייה: תיקייה של התלמיד, פרויקט Eclipse ושמירה — כן; Maven, java.util, jar מקורי — לא');

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

    // --- שגיאת קומפילציה: לא מריצים, מסמנים את השורה באדום (ההסבר בעברית, בחלון Problems), והודעה קצרה עם "מעבר לשגיאה" בלבד
    terminalOpts = null;
    const broken = path.join(ws, 'src', 'Broken.java');
    fs.writeFileSync(broken, 'public class Broken {\n    int x = ;\n}\n');
    const pBefore = panels.length;
    await commands['dsviz.run']();
    await new Promise(r => setTimeout(r, 50));
    assert.strictEqual(terminalOpts, null, 'must not run with errors');
    const bd = diag.get(broken);
    assert(bd && bd.length >= 1 && bd[0].range.line === 1, 'error not on line 2');
    assert(bd[0].message.startsWith('\u200Fביטוי לא חוקי') && bd[0].message.includes('(illegal start of expression)'), bd[0].message);
    const em = shown[shown.length - 1];
    assert(em.kind === 'error' && !em.modal && em.text === 'יש שגיאה בקוד — מתקנים ומריצים שוב'
        && JSON.stringify(em.buttons) === JSON.stringify(['מעבר לשגיאה']), JSON.stringify(em));
    assert(!panels.slice(pBefore).some(x => x.id === 'dsviz.errors'), 'בלי חלון פירוט קופץ — הפירוט בחלון Problems');
    onMessage = (t, b) => (b.includes('מעבר לשגיאה') ? 'מעבר לשגיאה' : undefined);
    openedSeq.length = 0;
    await commands['dsviz.run']();
    await new Promise(r => setTimeout(r, 50));
    onMessage = null;
    assert(openedSeq.some(o => o.file === broken && o.line === 1), '"מעבר לשגיאה": ' + JSON.stringify(openedSeq));
    fs.unlinkSync(broken);

    // --- "הרצה ויזואלית" מקובץ של סעיף בלי main: מריצים את ה-main של התרגיל (הקובץ שלידו, באותה חבילה)
    fs.mkdirSync(path.join(ws, 'src', 'ex09_x'), { recursive: true });
    const helper = path.join(ws, 'src', 'ex09_x', 'Node2.java');
    fs.writeFileSync(helper, 'package ex09_x;\n\npublic class Node2 {\n    public static int twice(int x) {\n        return 2 * x;\n    }\n}\n');
    fs.writeFileSync(path.join(ws, 'src', 'ex09_x', 'Targil9.java'), 'package ex09_x;\n\npublic class Targil9 {\n    public static void main(String[] args) {\n        System.out.println(Node2.twice(4));\n    }\n}\n');
    terminalOpts = null;
    const helperText = fs.readFileSync(helper, 'utf8');
    vscode.window.activeTextEditor = { document: { fileName: helper, uri: {}, languageId: 'java', getText: () => helperText, save: async () => true } };
    await commands['dsviz.run']();
    assert(terminalOpts && terminalOpts.shellArgs.slice(-1)[0] === 'ex09_x.Targil9', 'ה-main של התרגיל: ' + JSON.stringify(terminalOpts && terminalOpts.shellArgs.slice(-2)));
    assert.strictEqual(extension.siblingMain(helper), path.join(ws, 'src', 'ex09_x', 'Targil9.java'));
    fs.rmSync(path.join(ws, 'src', 'ex09_x'), { recursive: true, force: true });

    // --- "שליחת תרגיל" פותח את הקבצים: המחלקות, אחר כך הקובץ עם main (לשונית ליד הסעיף), ובסוף הסעיף — עם המיקוד
    {
        const od = path.join(ws, 'src', 'ex08_open');
        fs.mkdirSync(od, { recursive: true });
        const [A, M, B, T] = ['Node3', 'Targil8', 'Tree3', 'Task8'].map(n => path.join(od, n + '.java'));
        fs.writeFileSync(A, 'class Node3 {}\n'); fs.writeFileSync(B, 'class Tree3 {}\n');
        fs.writeFileSync(M, 'class Targil8 {\n    public static void main(String[] a) {}\n}\n');
        fs.writeFileSync(T, 'class Task8 {\n    int f() {\n        \n    }\n}\n');
        openedSeq.length = 0;
        shown.length = 0;
        await extension.openFiles(ws, [M, A, B, T], T, 2, M);
        assert.deepStrictEqual(openedSeq.map(o => path.basename(o.file)), ['Node3.java', 'Tree3.java', 'Targil8.java', 'Task8.java'], JSON.stringify(openedSeq));
        assert(openedSeq.slice(0, 3).every(o => o.preserveFocus && o.preview === false) && !openedSeq[3].preserveFocus && openedSeq[3].preview === false, JSON.stringify(openedSeq));
        assert.strictEqual(shown.length, 0, 'בלי הודעה נוספת אחרי שליחת תרגיל');
        fs.rmSync(od, { recursive: true, force: true });
    }

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
    assert.strictEqual(edits[1][2], 'import unit4.collectionsLib.*;\n\n');
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

    // פעולות העזר: מתי מוסיפים לבד את import static unit4.collectionsLib.Helpers.*; (רק כשיש קריאה ערומה ל-size( / duplicate()
    const need = csExt.needsHelperImport, H = 'import unit4.collectionsLib.*;\n';
    assert(need(H + 'class A{ void f(){ Stack<Integer> s=null; int n = size(s);} }'), 'size(s) — צריך import');
    assert(need(H + 'class A{ void f(){ Queue<Integer> c = duplicate(q);} }'), 'duplicate(q) — צריך import');
    assert(!need(H + 'class A{ void f(){ int n = s.size();} }'), 's.size() — לא');
    assert(!need(H + 'import static unit4.collectionsLib.Helpers.*;\nclass A{ void f(){ int n=size(s);} }'), 'כבר יש');
    assert(!need(H + 'class A{ static int size(Stack<Integer> s){ return 0;} void f(){ int n=size(s);} }'), 'פעולה של התלמיד');
    assert(!need(H + 'class A{ void f(){ // size(s)\n String t="size(s)"; } }'), 'הערה / מחרוזת');
    assert(!need('class A{ void f(){ java.util.List<Integer> l=null; int n=size(l);} }'), 'בלי ספריית הקורס');

    // "שליחת תרגיל" + "הצעה לפתרון": החלון (הודעות כמו מהדף), SubmitCli מול שרת מדומה, הקבצים, 💡 מעל הסעיפים, והחלון של ההצעה
    assert(commands['dsviz.submit'] && commands['dsviz.solution'], 'פקודות שליחת תרגיל / הצעה לפתרון');
    {
        const sub = require(path.join(ext, 'submit.js'));
        const mock = require('child_process').spawn('node', [path.join(__dirname, 'submit', 'mock-worker.js'), '8794', path.join(__dirname, 'ai', '1-stack.json')]);
        await new Promise(r => setTimeout(r, 700));
        process.env.DSVIZ_WORKER_URL = 'http://127.0.0.1:8794';
        const wsS = fs.mkdtempSync(path.join(os.tmpdir(), 'dsviz-submit-ws-')), storeDir = fs.mkdtempSync(path.join(os.tmpdir(), 'dsviz-store-'));
        const openedFiles = [];
        // השמות התפוסים: תיקייה פתוחה נוספת עם חבילות ב-src ובתיקייה שב-java.project.sourcePaths
        const other = fs.mkdtempSync(path.join(os.tmpdir(), 'dsviz-other-'));
        fs.mkdirSync(path.join(other, 'src', 'ex01_queues'), { recursive: true });
        fs.mkdirSync(path.join(other, 'code', 'ex02_trees'), { recursive: true });
        fs.mkdirSync(path.join(wsS, 'src', 'mywork'), { recursive: true });
        const savedFolders = vscode.workspace.workspaceFolders;
        vscode.workspace.workspaceFolders = [{ uri: { fsPath: wsS }, name: 'wsS' }, { uri: { fsPath: other }, name: 'other' }];
        config['java.project.sourcePaths'] = ['code'];
        const wsState = new Map();
        let manifest = null;
        try {
            const api = sub.register({ vscode, ctx: { subscriptions: [], extensionPath: ext, globalStorageUri: { fsPath: storeDir },
                    workspaceState: { get: k => wsState.get(k), update: async (k, v) => wsState.set(k, v) } },
                chooseRoot: async () => wsS, detectLanguage: () => 'java', project: require(path.join(ext, 'project.js')),
                cs: require(path.join(ext, 'cs.js')), runner: require(path.join(ext, 'runner.js')),
                openFiles: async (root, files, focus, line, main) => openedFiles.push({ files, focus, line, main }),
                onManifest: m => { manifest = m; } });
            const before = panels.length;
            const done = api.submit();
            for (let i = 0; i < 50 && panels.length === before; i++) await new Promise(r => setTimeout(r, 100));
            const p = panels.slice(before).find(x => x.id === 'dsviz.submit');
            assert(p && p.html.includes('שליחה ויצירת התרגיל') && p.html.includes('הדבקה (Ctrl+V)') && /<html lang="he" dir="rtl">/.test(p.html), 'חלון שליחת תרגיל');
            const page = 'photo.jpg';   // צילום של דף (כמו Ctrl+V)
            await p.handler({ type: 'data', name: '', kind: 'image', data: fs.readFileSync(path.join(__dirname, 'ai', 'files', page)).toString('base64') });
            await p.handler({ type: 'text', text: 'שאלה 1 — מחסניות: כתבו פעולה שסופרת את הזוגיים' });
            const items = p.posted.filter(m => m.type === 'items').pop().items;
            assert.strictEqual(items.length, 2, JSON.stringify(items));
            assert(items[0].name.startsWith('תמונה מודבקת 1') || items[0].name === page, items[0].name);
            shown.length = 0;
            onMessage = (text, buttons) => (buttons.includes(sub.SHOW) ? sub.SHOW : undefined);   // לוחצים "הצגה"
            await p.handler({ type: 'send', note: 'רק שאלה 1', lang: 'java' });
            await done;
            assert(p.disposed, 'החלון נסגר אחרי השליחה: ' + JSON.stringify(p.posted.filter(m => m.type === 'failed' || m.type === 'status')));
            // תצוגת הבנייה באותו חלון: "build" ואז השלבים לפי הסדר, מהשורות progress של SubmitCli
            const posted = p.posted.map(m => m.type);
            assert(posted.indexOf('build') >= 0 && posted.indexOf('build') < posted.indexOf('progress'), JSON.stringify(posted));
            const stages = p.posted.filter(m => m.type === 'progress').map(m => sub.stageFor(m.text).stage).filter(x => x != null);
            assert.deepStrictEqual(stages, [0, 1, 2, 3, 4], 'השלבים: ' + JSON.stringify(p.posted.filter(m => m.type === 'progress')));
            // השמות התפוסים במניפסט: התיקיות שבראש כל תיקיית קוד (src, java.project.sourcePaths, התיקייה הפתוחה עצמה)
            assert(manifest && ['ex01_queues', 'ex02_trees', 'mywork', 'src', 'code'].every(n => manifest.taken.includes(n)), JSON.stringify(manifest && manifest.taken));
            const f = path.join(wsS, 'src', 'ex01_stacks', 'StackTargil.java');
            assert(fs.existsSync(f), 'נוצר ' + f);
            assert(openedFiles[0] && openedFiles[0].focus === f && openedFiles[0].line > 0 && openedFiles[0].main === f, JSON.stringify(openedFiles));
            const text = fs.readFileSync(f, 'utf8');
            assert(!text.includes('count++'), 'הפתרון לא נכנס לקוד של התלמיד');
            // סעיף ג' (סיבוכיות — במילים, בלי קוד): לא בקוד; בהודעה הקצרה רק "סעיף ג: עונים במילים", והטקסט עצמו ב"הצגה"
            assert(!text.includes('מהי סיבוכיות'), 'הסעיף במילים לא נכנס לקוד');
            await new Promise(r => setTimeout(r, 50));
            const created = shown.filter(x => x.text.includes('התרגיל נוצר')).pop();
            assert(created && created.kind === 'info' && !created.modal && created.text === '✓ התרגיל נוצר · 2 סעיפים · הסמן בסעיף א · סעיף ג: עונים במילים'
                && JSON.stringify(created.buttons) === JSON.stringify([sub.SHOW]), 'ההודעה אחרי השליחה: ' + JSON.stringify(created));
            assert(!shown.some(x => x.text.includes('כותבים את הפעולה')), 'בלי הודעה נוספת');
            const op = panels.filter(x => x.id === 'dsviz.others').pop();
            assert(op && op.html.includes('מהי סיבוכיות זמן הריצה') && /<html lang="he" dir="rtl">/.test(op.html) && op.html.includes('סעיף ג\''), 'החלון "הצגה"');
            onMessage = null;
            const lenses = await lensProviders[lensProviders.length - 1].provideCodeLenses({ uri: { fsPath: f }, getText: () => text });
            assert.strictEqual(lenses.length, 2, 'שני 💡 — אחד לכל סעיף');
            assert(text.split('\n')[lenses[0].range.line].includes('countEven('), 'ה-💡 מעל הכותרת של countEven');
            const sp = panels.length;
            commands['dsviz.solution'] ? await api.showSolution(lenses[0].command.arguments[0]) : null;
            const sol = panels.slice(sp).find(x => x.id === 'dsviz.solution');
            assert(sol && sol.html.includes('הצעה לפתרון') && sol.html.includes('נבדק: הרצנו את main') && sol.html.includes('count++'), 'חלון ההצעה');
            await sol.handler({ type: 'copy', text: null });
            assert(clipboard && clipboard.includes('countEven'), 'העתקה');
            // ההדגשה של הסעיפים שעוד לא ענו עליהם: הקובץ נזכר (workspaceState), שני גופים מודגשים; כותבים קוד — נשאר אחד
            assert((wsState.get('dsviz.submittedFiles') || []).some(x => x.endsWith('ex01_stacks/stacktargil.java')), JSON.stringify([...wsState]));
            let cur = text;
            const set = [];
            const doc = { uri: { fsPath: f }, getText: () => cur };
            const ed = { document: doc, setDecorations: (t, r) => set.push(r) };
            vscode.window.visibleTextEditors = [ed];
            api.decorate(ed);
            const r0 = set.pop();
            const lines = text.split('\n');
            assert(r0 && r0.length === 2 && lines[r0[0].start.line].includes('כאן עונים סעיף א') && lines[r0[1].start.line].includes('כאן עונים סעיף ב'), JSON.stringify(r0));
            cur = text.replace(/(כאן עונים סעיף א[^\n]*\n)/, '$1        int count = 0;\n');
            docChange({ document: doc });
            await new Promise(r => setTimeout(r, 400));
            const r1 = set.pop();
            assert(r1 && r1.length === 1 && cur.split('\n')[r1[0].start.line].includes('כאן עונים סעיף ב'), 'אחרי שכותבים בסעיף א: ' + JSON.stringify(r1));
            const notMine = [];
            api.decorate({ document: { uri: { fsPath: path.join(wsS, 'Other.java') }, getText: () => text }, setDecorations: (t, r) => notMine.push(r) });
            assert.strictEqual(notMine.length, 0, 'רק בקבצים שנוצרו ב"שליחת תרגיל"');
            vscode.window.visibleTextEditors = [];
            log.push('submit: ex01_stacks + שלבים 0-4 + 2 × 💡 + הודעה קצרה + "הצגה" לסעיף ג + הדגשה של הסעיפים הריקים');

            // ההודעה עצמה (doneMessage) והתוויות של הסעיפים במילים — יחיד / רבים
            assert.strictEqual(sub.wordLabels([]), '');
            assert.strictEqual(sub.wordLabels([{ label: 'ד', text: 'נמקו' }]), 'סעיף ד: עונים במילים');
            assert.strictEqual(sub.wordLabels([{ label: 'ג', text: 'א' }, { label: "ד'", text: 'ב' }]), 'סעיפים ג, ד: עונים במילים');
            assert.strictEqual(sub.wordLabels([{ label: '', text: 'א' }, { label: 'ב', text: 'ב' }]), '2 סעיפים: עונים במילים');
            assert.strictEqual(sub.wordLabels([{ label: 'ה', text: '  ' }]), '', 'סעיף בלי טקסט');
            const plain = sub.doneMessage({ done: true, folder: 'ex02_queues', tasks: 3, errors: [], others: [] }, 'א');
            assert(!plain.warn && plain.text === '✓ התרגיל נוצר · 3 סעיפים · הסמן בסעיף א' && plain.buttons.length === 0, JSON.stringify(plain));
            const one = sub.doneMessage({ tasks: 1, errors: [], others: [{ label: 'ב', text: 'נמקו' }] }, '');
            assert(one.text === '✓ התרגיל נוצר · סעיף אחד · הסמן במקום התשובה · סעיף ב: עונים במילים' && one.buttons[0] === sub.SHOW, JSON.stringify(one));
            const none = sub.doneMessage({ tasks: 0, errors: [], others: [{ label: 'א', text: 'הסבירו\nבמילים' }] });
            assert(none.text === '✓ התרגיל נוצר · אין בו סעיפים בקוד · סעיף א: עונים במילים', JSON.stringify(none));
            const withErr = sub.doneMessage({ tasks: 2, errors: ['A.java:3: x', 'A.java:5: y'], others: [{ label: 'ג', text: 'נמקו' }] }, 'א');
            assert(withErr.warn && withErr.text === 'התרגיל נוצר · יש 2 שגיאות קומפילציה · סעיף ג: עונים במילים'
                && JSON.stringify(withErr.buttons) === JSON.stringify([sub.JUMP, sub.SHOW]), JSON.stringify(withErr));
            assert.strictEqual(sub.doneMessage({ tasks: 1, errors: ['A.java:3: x'] }).text, 'התרגיל נוצר · יש שגיאת קומפילציה אחת');
            // "מעבר לשגיאה": נפתח הקובץ בשורה של השגיאה הראשונה
            const errFile = path.join(wsS, 'src', 'ex01_stacks', 'StackTargil.java');
            openedSeq.length = 0;
            onMessage = (t, b) => (b.includes(sub.JUMP) ? sub.JUMP : undefined);
            await api.showDone({ focus: errFile, line: 8, tasks: 2, files: [errFile], errors: ["StackTargil.java:12: ';' expected"], others: [] });
            onMessage = null;
            assert(openedSeq.some(o => o.file === errFile && o.line === 11), JSON.stringify(openedSeq));
            assert(shown[shown.length - 1].kind === 'warn' && shown[shown.length - 1].text === 'התרגיל נוצר · יש שגיאת קומפילציה אחת', JSON.stringify(shown[shown.length - 1]));
            // השלבים מהשורות של SubmitCli; שורות "מבקש תיקון" / "בודק שוב" — שורה אפורה מתחת לשלב
            assert.deepStrictEqual(['מכין את הקבצים…', 'שולח את התרגיל… (בדרך כלל עד דקה)', 'בונה את התרגיל…', 'בודק שהקוד מתקמפל…',
                'בודק שההצעות לפתרון נותנות את התוצאה הנכונה…'].map(t => sub.stageFor(t).stage), [0, 1, 2, 3, 4]);
            assert.deepStrictEqual(sub.stageFor('יש שגיאות קומפילציה — מבקש תיקון…'), { note: 'יש שגיאות קומפילציה · מבקש תיקון…' });
            assert.deepStrictEqual(sub.stageFor('בודק שוב…'), { note: 'בודק שוב…' });
            assert.strictEqual(sub.STAGES.length, 5);
            const se = sub.shortError('השרת לא ענה — נסו שוב בעוד דקה.\nפרטים: 504 Gateway Timeout\nat x.y');
            assert(se === 'השרת לא ענה, נסו שוב בעוד דקה.', se);
            assert(sub.shortError('x '.repeat(200)).length <= 151);
            assert.strictEqual(sub.labelAt(text, openedFiles[0].line), 'א');
        } finally {
            vscode.workspace.workspaceFolders = savedFolders;
            delete config['java.project.sourcePaths'];
            mock.kill();
            delete process.env.DSVIZ_WORKER_URL;
        }
    }

    // ===================== החלונות בדפדפן אמיתי (Chromium): תצוגת הבנייה, "הצגה", "כל השגיאות" — בערכה בהירה וכהה =====================
    await webviewShots();

    console.log(log.join('\n'));
    console.log('\nALL VS CODE TESTS PASSED\nworkspace: ' + ws);
})().catch(e => { console.error(log.join('\n')); console.error(e); process.exit(1); });

/**
 * החלונות (Webview) בדפדפן אמיתי, עם המשתנים של VS Code (Light Modern / Dark Modern): הדף של התוסף כמו שהוא,
 * ו-acquireVsCodeApi מדומה. שומר צילומים ב-DSVIZ_SHOTS (או build/test/vscode). בלי playwright — מדלגים.
 */
async function webviewShots() {
    let pw = null;
    for (const p of ['playwright', '/opt/node-tools/node_modules/playwright']) { try { pw = require(p); break; } catch (e) { /* הבא */ } }
    if (!pw) { log.push('playwright לא מותקן — מדלגים על הצילומים של החלונות'); return; }
    const sub = require(path.join(ext, 'submit.js')), ui = require(path.join(ext, 'ui.js'));
    const out = process.env.DSVIZ_SHOTS || path.join(__dirname, '..', '..', 'build', 'test', 'vscode');
    fs.mkdirSync(out, { recursive: true });
    // הגופנים של הסרטונים (Heebo, JetBrains Mono — tutorials/fonts, או DSVIZ_FONTS_DIR), ואם אין — גופני המערכת
    const fontsDir = process.env.DSVIZ_FONTS_DIR || path.join(__dirname, '..', '..', 'tutorials', 'fonts', 'node_modules', '@expo-google-fonts');
    const font = (pkg, file) => { const p = path.join(fontsDir, pkg, file); return fs.existsSync(p) ? fs.readFileSync(p).toString('base64') : null; };
    const face = (fam, data, w) => (data ? `@font-face { font-family: '${fam}'; font-weight: ${w}; src: url(data:font/ttf;base64,${data}); }` : '');
    const FONTS = face('Heebo', font('heebo', '400Regular/Heebo_400Regular.ttf'), 400) + face('Heebo', font('heebo', '600SemiBold/Heebo_600SemiBold.ttf'), '600 700')
        + face('JBMono', font('jetbrains-mono', '400Regular/JetBrainsMono_400Regular.ttf'), 400);
    const THEMES = {
        light: `--vscode-font-family: 'Heebo', 'DejaVu Sans', sans-serif; --vscode-editor-font-family: 'JBMono', 'DejaVu Sans Mono', monospace;
  --vscode-foreground: #3b3b3b; --vscode-editor-background: #ffffff; --vscode-descriptionForeground: #717171; --vscode-widget-border: #e5e5e5;
  --vscode-input-background: #ffffff; --vscode-input-foreground: #3b3b3b; --vscode-input-border: #cecece; --vscode-dropdown-background: #ffffff;
  --vscode-dropdown-foreground: #3b3b3b; --vscode-dropdown-border: #cecece; --vscode-textCodeBlock-background: #f8f8f8;`,
        dark: `--vscode-font-family: 'Heebo', 'DejaVu Sans', sans-serif; --vscode-editor-font-family: 'JBMono', 'DejaVu Sans Mono', monospace;
  --vscode-foreground: #cccccc; --vscode-editor-background: #1f1f1f; --vscode-descriptionForeground: #9d9d9d; --vscode-widget-border: #313131;
  --vscode-input-background: #313131; --vscode-input-foreground: #cccccc; --vscode-input-border: #3c3c3c; --vscode-dropdown-background: #313131;
  --vscode-dropdown-foreground: #cccccc; --vscode-dropdown-border: #3c3c3c; --vscode-textCodeBlock-background: #2b2b2b;`
    };
    const browser = await pw.chromium.launch({ args: ['--no-sandbox'] });
    const hostLog = [];
    const open = async (html, theme, size) => {
        const page = await browser.newPage({ viewport: { width: size[0], height: size[1] } });
        await page.exposeFunction('hostMsg', m => { hostLog.push(m); });
        await page.addInitScript(() => { window.acquireVsCodeApi = () => ({ postMessage: m => window.hostMsg(m), setState() {}, getState() { return null; } }); });
        const body = html.replace("default-src 'none';", "default-src 'none'; font-src data:;")
            .replace('</head>', `<style>${FONTS} :root { ${THEMES[theme]} }</style></head>`).replace('<body>', `<body class="vscode-${theme}">`);
        await page.route('http://dsviz.test/page', r => r.fulfill({ contentType: 'text/html; charset=utf-8', body }));
        await page.goto('http://dsviz.test/page');
        assert.strictEqual(await page.evaluate(() => document.documentElement.dir + '/' + getComputedStyle(document.body).direction), 'rtl/rtl', 'החלון מימין לשמאל');
        return page;
    };
    const send = (page, m) => page.evaluate(x => window.postMessage(x, '*'), m);
    const img = f => { const p = path.join(__dirname, '..', '..', 'site', 'assets', 'img', f); return fs.existsSync(p) ? 'data:image/webp;base64,' + fs.readFileSync(p).toString('base64') : null; };
    const items = [{ id: 1, name: 'תמונה מודבקת 1.png', kind: 'image', size: 182000, thumb: img('submit-page-a.webp') },
        { id: 2, name: 'תמונה מודבקת 2.png', kind: 'image', size: 96000, thumb: img('submit-page-b.webp') }, { id: 3, name: 'בגרות 2025 שאלה 3.pdf', kind: 'pdf', size: 412000 }];
    const others = [{ label: 'ד', text: 'מהי סיבוכיות זמן הריצה של הפעולה countEven(s) שכתבתם בסעיף א? נמקו.' },
        { label: 'ה', text: 'נתונה הפעולה:\npublic static int what(Stack<Integer> s) {\n    if (s.isEmpty()) return 0;\n    int x = s.pop();\n    int r = x + what(s);\n    s.push(x);\n    return r;\n}\nמה מחזירה הפעולה what עבור המחסנית [1, 2, 3]? הסבירו במילים.' }];
    const errors = [{ file: 'StackTargil.java', line: 14, message: "';' expected", source: '        int count = 0' },
        { file: 'StackTargil.java', line: 21, message: 'cannot find symbol', source: '        while (!s.isEmty()) {' },
        { file: 'Node.java', line: 7, message: 'incompatible types: String cannot be converted to int', source: '        int v = "5";' }];
    try {
        for (const theme of ['light', 'dark']) {
            // תצוגת הבנייה באמצע: שלושה שלבים הסתיימו, "בודק שהקוד מתקמפל" עכשיו (עם שניות ושורת תיקון), האחרון עוד לא התחיל
            const p = await open(sub.submitHtml({ lang: 'java', where: 'DataStructures' }), theme, [760, 560]);
            await send(p, { type: 'items', items });
            if (theme === 'light') await p.screenshot({ path: path.join(out, 'submit-form-light.png') });
            await send(p, { type: 'build' });
            for (const t of ['מכין את הקבצים…', 'שולח את התרגיל… (בדרך כלל עד דקה)', 'בונה את התרגיל…', 'בודק שהקוד מתקמפל…', 'יש שגיאות קומפילציה — מבקש תיקון…'])
                await send(p, { type: 'progress', text: t });
            await p.waitForTimeout(2300);
            const st = await p.evaluate(() => [...document.querySelectorAll('#stages li')].map(li => [li.className, li.querySelector('.t').textContent, li.querySelector('.sec').textContent, li.querySelector('.note').textContent]));
            assert.deepStrictEqual(st.map(x => x[0]), ['done', 'done', 'done', 'now', ''], JSON.stringify(st));
            assert.deepStrictEqual(st.map(x => x[1]), sub.STAGES);
            assert(/^[1-9]\d* שנ'$/.test(st[3][2]) && st[3][3] === 'יש שגיאות קומפילציה · מבקש תיקון…' && !st[2][2] && !st[4][2], JSON.stringify(st));
            const vis = await p.evaluate(() => ({ form: document.getElementById('form').hidden, build: document.getElementById('build').hidden, top: document.getElementById('topline').hidden,
                bar: document.getElementById('bar').style.width, thumbs: document.querySelectorAll('#thumbs .item').length,
                buttons: [...document.querySelectorAll('#build button')].filter(b => b.offsetParent).map(b => b.textContent) }));
            assert.deepStrictEqual(vis, { form: true, build: false, top: false, bar: '70%', thumbs: 3, buttons: ['ביטול'] }, JSON.stringify(vis));
            await p.screenshot({ path: path.join(out, `build-${theme}.png`) });
            if (theme === 'light') {
                // שגיאה: שורה אדומה קצרה ו"נסו שוב" — אותם קבצים
                await send(p, { type: 'failed', text: 'השרת לא ענה. נסו שוב בעוד דקה.' });
                await p.waitForTimeout(150);
                const f = await p.evaluate(() => ({ bad: document.querySelector('#stages li.bad .t').textContent, text: document.getElementById('ftext').textContent,
                    shown: !document.getElementById('fail').hidden, color: getComputedStyle(document.getElementById('ftext')).color }));
                assert(f.shown && f.bad === 'בודק שהקוד מתקמפל' && f.text === 'השרת לא ענה. נסו שוב בעוד דקה.' && f.color === 'rgb(196, 48, 43)', JSON.stringify(f));
                await p.screenshot({ path: path.join(out, 'build-failed-light.png') });
                hostLog.length = 0;
                await p.click('#retry');
                await p.waitForTimeout(100);
                assert(hostLog.some(m => m.type === 'send' && m.lang === 'java'), 'נסו שוב → send: ' + JSON.stringify(hostLog));
                await send(p, { type: 'form' });
                await p.waitForTimeout(100);
                assert.strictEqual(await p.evaluate(() => document.querySelectorAll('#items .item').length), 3, 'הקבצים נשארו');
            }
            await p.close();
            // "הצגה": הסעיפים במילים, והקוד שבתוכם משמאל לימין
            const o = await open(ui.othersHtml(others), theme, [620, 600]);
            const od = await o.evaluate(() => ({ pre: [...document.querySelectorAll('pre')].map(x => x.dir + ':' + getComputedStyle(x).direction),
                code: [...document.querySelectorAll('p code')].map(x => x.textContent), tags: [...document.querySelectorAll('.tag')].map(x => x.textContent) }));
            assert.deepStrictEqual(od, { pre: ['ltr:ltr'], code: ['countEven(s)', 'what'], tags: ["סעיף ד'", "סעיף ה'"] }, JSON.stringify(od));
            await o.screenshot({ path: path.join(out, `others-${theme}.png`), fullPage: true });
            await o.close();
            // "כל השגיאות": כרטיס לכל שגיאה, "קובץ · שורה", ההסבר בעברית והשורה מהקוד
            const e = await open(ui.errorsHtml(errors), theme, [620, 560]);
            const ed = await e.evaluate(() => [...document.querySelectorAll('.err')].map(c => c.querySelector('.where').textContent + ' | ' + c.querySelector('.he').textContent));
            assert.deepStrictEqual(ed, ['StackTargil.java · שורה 14 | חסר ; בסוף הפקודה', 'StackTargil.java · שורה 21 | השם לא מוכר. בדקו את האיות (אותיות גדולות וקטנות), שהמשתנה הוגדר, ושיש import',
                'Node.java · שורה 7 | אי התאמה בטיפוסים: אי אפשר להכניס String למשתנה מטיפוס int'], JSON.stringify(ed));
            hostLog.length = 0;
            await e.click('.err:nth-of-type(2) .where');
            await e.waitForTimeout(100);
            assert(hostLog.some(m => m.type === 'open' && m.i === 1), JSON.stringify(hostLog));
            await e.screenshot({ path: path.join(out, `errors-${theme}.png`), fullPage: true });
            await e.close();
        }
        log.push('Chromium: תצוגת הבנייה (שלבים, שניות, שגיאה ונסו שוב), "הצגה" ו"כל השגיאות" — RTL, בהיר וכהה → ' + out);
    } finally {
        await browser.close();
    }
}
