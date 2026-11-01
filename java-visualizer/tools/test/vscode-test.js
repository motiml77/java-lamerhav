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

const vscode = {
    workspace: {
        workspaceFolders: [{ uri: { fsPath: ws }, name: 'ws' }],
        openTextDocument: async f => { const t = fs.readFileSync(f, 'utf8').split('\n');
            return { fileName: f, lineCount: t.length, lineAt: i => ({ text: t[i] }), languageId: 'java' }; },
        getWorkspaceFolder: () => vscode.workspace.workspaceFolders[0],
        getConfiguration: () => ({ get: k => (k === 'language' ? testLang : undefined) }),
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
        showInformationMessage: async (m, o) => { log.push('info: ' + m + (o && o.detail ? '\n' + o.detail : '')); },
        showWarningMessage: async (m, o) => { log.push('warn: ' + m + (o && o.detail ? '\n' + o.detail : '')); },
        showErrorMessage: async (m) => { log.push('error: ' + m); },
        showTextDocument: async doc => { opened = doc.fileName; return vscode.window.activeTextEditor && vscode.window.activeTextEditor.document === doc ? vscode.window.activeTextEditor : { selection: null, revealRange() {} }; },
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
    StatusBarAlignment: { Left: 1 }, TextEditorRevealType: { InCenter: 2 }, ViewColumn: { Beside: -2, Active: -1 },
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
        try {
            const api = sub.register({ vscode, ctx: { subscriptions: [], extensionPath: ext, globalStorageUri: { fsPath: storeDir } },
                chooseRoot: async () => wsS, detectLanguage: () => 'java', project: require(path.join(ext, 'project.js')),
                cs: require(path.join(ext, 'cs.js')), runner: require(path.join(ext, 'runner.js')),
                openFiles: async (root, files, focus, line) => openedFiles.push({ files, focus, line }) });
            const before = panels.length;
            const done = api.submit();
            for (let i = 0; i < 50 && panels.length === before; i++) await new Promise(r => setTimeout(r, 100));
            const p = panels.slice(before).find(x => x.id === 'dsviz.submit');
            assert(p && p.html.includes('שליחה ויצירת התרגיל') && p.html.includes('הדבקה (Ctrl+V)'), 'חלון שליחת תרגיל');
            const page = 'photo.jpg';   // צילום של דף (כמו Ctrl+V)
            await p.handler({ type: 'data', name: '', kind: 'image', data: fs.readFileSync(path.join(__dirname, 'ai', 'files', page)).toString('base64') });
            await p.handler({ type: 'text', text: 'שאלה 1 — מחסניות: כתבו פעולה שסופרת את הזוגיים' });
            const items = p.posted.filter(m => m.type === 'items').pop().items;
            assert.strictEqual(items.length, 2, JSON.stringify(items));
            assert(items[0].name.startsWith('תמונה מודבקת 1') || items[0].name === page, items[0].name);
            await p.handler({ type: 'send', note: 'רק שאלה 1', lang: 'java' });
            await done;
            assert(p.disposed, 'החלון נסגר אחרי השליחה: ' + JSON.stringify(p.posted.filter(m => m.type === 'status')));
            const f = path.join(wsS, 'src', 'ex01_stacks', 'StackTargil.java');
            assert(fs.existsSync(f), 'נוצר ' + f);
            assert(openedFiles[0] && openedFiles[0].focus === f && openedFiles[0].line > 0, JSON.stringify(openedFiles));
            const text = fs.readFileSync(f, 'utf8');
            assert(!text.includes('count++'), 'הפתרון לא נכנס לקוד של התלמיד');
            // סעיף ג' (סיבוכיות — במילים, בלי קוד): לא בקוד, אבל בהודעה שאחרי השליחה (SubmitCli → others)
            const other = 'ג — מהי סיבוכיות זמן הריצה של הפעולה שכתבתם בסעיף ב? נמקו.';
            assert(!text.includes('מהי סיבוכיות'), 'הסעיף במילים לא נכנס לקוד');
            const created = log.filter(l => l.startsWith('info: התרגיל נוצר')).pop() || '';
            assert(created.startsWith('info: התרגיל נוצר — 2 סעיפים בקוד (בתיקייה ex01_stacks)\n') && created.includes('סעיפים שעונים עליהם במילים (בלי קוד):\n' + other)
                && created.includes('💡 הצעה לפתרון') && !created.includes('בראש הקובץ'), 'ההודעה אחרי השליחה: ' + created);
            const lenses = await lensProviders[lensProviders.length - 1].provideCodeLenses({ uri: { fsPath: f }, getText: () => text });
            assert.strictEqual(lenses.length, 2, 'שני 💡 — אחד לכל סעיף');
            assert(text.split('\n')[lenses[0].range.line].includes('countEven('), 'ה-💡 מעל הכותרת של countEven');
            const sp = panels.length;
            commands['dsviz.solution'] ? await api.showSolution(lenses[0].command.arguments[0]) : null;
            const sol = panels.slice(sp).find(x => x.id === 'dsviz.solution');
            assert(sol && sol.html.includes('הצעה לפתרון') && sol.html.includes('נבדק: הרצנו את main') && sol.html.includes('count++'), 'חלון ההצעה');
            await sol.handler({ type: 'copy', text: null });
            assert(clipboard && clipboard.includes('countEven'), 'העתקה');
            log.push('submit: ex01_stacks + 2 × 💡 + הצעה שנבדקה + סעיף ג (במילים) בהודעה');
            // ההודעה עצמה (doneMessage): בלי סעיפים במילים — הודעה רגילה בפינה; בלי סעיפים בקוד — אומרים את זה; טקסט ארוך מתקצר
            const plain = sub.doneMessage({ done: true, folder: 'ex02_queues', tasks: 3, errors: [], others: [] });
            assert(!plain.options && plain.message === 'התרגיל נוצר בתיקייה ex02_queues. כותבים את הפתרון במקום המסומן — ומעל כל סעיף יש 💡 הצעה לפתרון.', JSON.stringify(plain));
            const none = sub.doneMessage({ done: true, folder: 'ex03_trees', tasks: 0, errors: [], others: [{ label: 'א', text: 'הסבירו\nבמילים' }] });
            assert(none.message === 'התרגיל נוצר (בתיקייה ex03_trees)' && none.options.modal
                && none.options.detail === 'בתרגיל אין סעיפים שכותבים בהם קוד.\n\nסעיפים שעונים עליהם במילים (בלי קוד):\nא — הסבירו במילים', JSON.stringify(none));
            assert.strictEqual(sub.othersText(0, []), 'בתרגיל אין סעיפים שכותבים בהם קוד.');
            assert.strictEqual(sub.othersText(1, undefined), '');
            const one = sub.doneMessage({ done: true, folder: 'ex04_x', tasks: 1, errors: [], others: [{ label: 'ב', text: 'נמקו' }] });
            assert(one.message === 'התרגיל נוצר — סעיף אחד בקוד (בתיקייה ex04_x)', one.message);
            const withErr = sub.doneMessage({ done: true, folder: 'ex05_y', tasks: 2, errors: ['A.java:3: x'], others: [{ label: 'ג', text: 'נמקו' }] });
            assert(withErr.warn && withErr.message.endsWith('או לשלוח שוב.\n\nסעיפים שעונים עליהם במילים (בלי קוד):\nג — נמקו'), withErr.message);
            const long = sub.otherLine('ד', Array.from({ length: 60 }, (x, i) => 'מילה' + i).join(' '));
            assert(long.startsWith('ד — ') && long.endsWith('…') && long.length - 4 <= 201 && !long.includes(' …'), long);
        } finally {
            mock.kill();
            delete process.env.DSVIZ_WORKER_URL;
        }
    }

    console.log(log.join('\n'));
    console.log('\nALL VS CODE TESTS PASSED\nworkspace: ' + ws);
})().catch(e => { console.error(log.join('\n')); console.error(e); process.exit(1); });
