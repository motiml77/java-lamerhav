'use strict';
const vscode = require('vscode');
const fs = require('fs');
const path = require('path');
const templates = require('./templates');
const project = require('./project');
const runner = require('./runner');

const PENDING_KEY = 'dsviz.pendingOpen';

let ctx;
let diagnostics, output, terminal;

function activate(context) {
    ctx = context;
    diagnostics = vscode.languages.createDiagnosticCollection('dsviz');
    output = vscode.window.createOutputChannel('מבני נתונים');
    context.subscriptions.push(diagnostics, output);
    const reg = (id, fn) => context.subscriptions.push(vscode.commands.registerCommand(id, fn));
    reg('dsviz.newExercise', () => newExercise());
    reg('dsviz.newStack', () => newExercise('stack'));
    reg('dsviz.newQueue', () => newExercise('queue'));
    reg('dsviz.newList', () => newExercise('list'));
    reg('dsviz.newTree', () => newExercise('tree'));
    reg('dsviz.newBst', () => newExercise('bst'));
    reg('dsviz.newArray', () => newExercise('array'));
    reg('dsviz.newMatrix', () => newExercise('matrix'));
    reg('dsviz.insert', () => insertSnippet());
    for (const id of Object.keys(templates.SNIPPETS)) {
        reg('dsviz.insert.' + id, () => insertSnippet(id));
    }
    reg('dsviz.run', () => run());
    reg('dsviz.runLines', () => run());   // שם ישן (מגרסה קודמת) — אותה הרצה
    reg('dsviz.examples', () => examples());
    reg('dsviz.openProject', () => openProject());

    // תצוגה בסרגל הצד — הכפתורים עצמם מוגדרים ב-package.json (viewsWelcome)
    for (const view of ['dsviz.home', 'dsviz.insertView', 'dsviz.runView']) {
        context.subscriptions.push(vscode.window.registerTreeDataProvider(view, { getChildren: () => [], getTreeItem: e => e }));
    }

    // כפתור "הרצה ויזואלית" בשורת המצב, כשפתוח קובץ Java
    const bar = vscode.window.createStatusBarItem(vscode.StatusBarAlignment.Left, 100);
    bar.text = '$(play-circle) הרצה ויזואלית';
    bar.tooltip = 'הרצה שבה רואים כל שורה בקוד, את המשתנים ואת מבני הנתונים — צעד אחר צעד';
    bar.command = 'dsviz.run';
    const refresh = () => {
        const ed = vscode.window.activeTextEditor;
        if (ed && ed.document.languageId === 'java') bar.show(); else bar.hide();
    };
    context.subscriptions.push(bar, vscode.window.onDidChangeActiveTextEditor(refresh));
    refresh();

    openPending();
    firstRun();
}

/** פתיחת תיקיית התרגילים שהותקנה (במסמכים) */
async function openProject() {
    const p = project.installedProject();
    if (p) {
        await ctx.globalState.update(PENDING_KEY, { file: path.join(p, 'src', 'Main.java'), line: 0 });
        await vscode.commands.executeCommand('vscode.openFolder', vscode.Uri.file(p), false);
    } else {
        const root = await chooseRoot();
        if (root) await examples();
    }
}

/** פעם ראשונה אחרי ההתקנה, בלי תיקייה פתוחה: מציעים לפתוח את תיקיית התרגילים */
async function firstRun() {
    if (ctx.globalState.get('dsviz.welcomed') || vscode.workspace.workspaceFolders) return;
    const p = project.installedProject();
    if (!p) return;
    await ctx.globalState.update('dsviz.welcomed', true);
    const open = 'פתיחת תיקיית התרגילים';
    const ans = await vscode.window.showInformationMessage(
        'מבני נתונים — הכול מוכן! לפתוח את תיקיית התרגילים (עם תרגיל לדוגמה ודוגמאות פתורות)?', open);
    if (ans === open) await openProject();
}

function deactivate() {}

// ------------------------------------------------------------ תרגיל חדש

async function newExercise(preset) {
    const root = await chooseRoot();
    if (!root) return;

    const total = 6;
    let structure = preset;
    if (!structure) {
        const pick = await vscode.window.showQuickPick(
            Object.entries(templates.STRUCTURES).map(([id, s]) => ({ label: s.label, description: s.cls, id })),
            { title: 'תרגיל חדש — איזה מבנה נתונים?', placeHolder: 'בחרו מבנה נתונים' });
        if (!pick) return;
        structure = pick.id;
    }
    const st = templates.STRUCTURES[structure];

    const typePick = await vscode.window.showQuickPick(typeItems(structure),
        { title: `${st.label} — (1/${total}) טיפוס הערכים`, placeHolder: templates.typeOf(st, '?') });
    if (!typePick) return;
    const type = typePick.label;
    const tp = templates.TYPES[type];

    const retItems = [
        { label: 'void', description: 'לא מחזירה ערך (למשל משנה את המבנה)', id: 'void' },
        { label: 'int', description: 'מחזירה מספר (למשל סכום, כמות)', id: 'int' },
        { label: 'boolean', description: 'מחזירה אמת/שקר (למשל "האם קיים")', id: 'boolean' }
    ];
    if (type !== 'Integer') retItems.push({ label: tp.primitive, description: 'מחזירה ערך מהמבנה', id: 'element' });
    retItems.push({ label: templates.typeOf(st, type), description: 'מחזירה מבנה חדש', id: 'structure' });
    const retPick = await vscode.window.showQuickPick(retItems,
        { title: `${st.label} — (2/${total}) מה הפעולה מחזירה?` });
    if (!retPick) return;

    const extraItems = [
        { label: `שני מבנים`, description: `הפעולה מקבלת שניים (למשל מיזוג, השוואה)`, id: 'second' },
        type === 'Student'
            ? { label: `פרמטר נוסף x`, description: `int x (למשל "כמה תלמידים עם ממוצע מעל x")`, id: 'extra' }
            : type === 'Car'
            ? { label: `פרמטר נוסף x`, description: `int x (למשל "כמה מכוניות חדשות משנה x")`, id: 'extra' }
            : { label: `פרמטר נוסף x`, description: `${tp.primitive} x (למשל "מחקו את כל המופעים של x")`, id: 'extra' }
    ];
    if (type === 'Integer' && structure !== 'tree' && structure !== 'bst') extraItems.push({ label: 'ערכים אקראיים', description: 'בכל הרצה מספרים אחרים', id: 'random' });
    const extras = await vscode.window.showQuickPick(extraItems,
        { title: `${st.label} — (3/${total}) תוספות (אפשר לדלג עם Enter)`, canPickMany: true });
    if (!extras) return;
    const has = id => extras.some(e => e.id === id);

    const opts = {
        structure, type, returns: retPick.id,
        second: has('second'), extra: has('extra'), random: has('random'),
        values: '', values2: '', className: '', functionName: ''
    };

    if (!opts.random) {
        const isTree = structure === 'tree';
        const hint = type === 'Student' ? '  (כמה תלמידים — נוצרים עם נתוני דמה)' : type === 'Car' ? '  (כמה מכוניות — נוצרות עם נתוני דמה)'
            : structure === 'matrix' ? '  (שורות מופרדות ב-;)' : isTree ? '  (לפי רמות, null = אין צומת)' : '';
        const v = await askValues(`${st.label} — (4/${total}) ערכים ל-${st.varName}${hint}`,
            templates.defaultValues(structure, type, false), type, isTree, structure);
        if (v === undefined) return;
        opts.values = v;
        if (opts.second) {
            const v2 = await askValues(`${st.label} — ערכים ל-${st.varName}2${hint}`,
                templates.defaultValues(structure, type, true), type, isTree, structure);
            if (v2 === undefined) return;
            opts.values2 = v2;
        }
    }

    const { srcDir, warnings } = project.setup(root, path.join(ctx.extensionPath, 'resources', project.JAR_NAME));
    await warnConflicts(root);
    for (const w of warnings) vscode.window.showWarningMessage(w);

    const className = await vscode.window.showInputBox({
        title: `${st.label} — (5/${total}) שם המחלקה (הקובץ)`,
        value: project.nextClassName(srcDir, 'Targil'),
        validateInput: s => !templates.isIdentifier(s) ? 'אותיות באנגלית וספרות בלבד, מתחיל באות'
            : fs.existsSync(path.join(srcDir, s + '.java')) ? 'כבר קיים קובץ בשם הזה' : null
    });
    if (!className) return;
    const functionName = await vscode.window.showInputBox({
        title: `${st.label} — (6/${total}) שם הפעולה שכותבים`,
        value: 'solve',
        validateInput: s => templates.isIdentifier(s) ? null : 'אותיות באנגלית וספרות בלבד, מתחיל באות'
    });
    if (!functionName) return;
    opts.className = className;
    opts.functionName = functionName;

    let gen;
    try { gen = templates.generate(opts); }
    catch (e) { vscode.window.showErrorMessage(e.message); return; }
    const file = path.join(srcDir, className + '.java');
    fs.writeFileSync(file, gen.code, 'utf8');
    await openAt(root, file, gen.cursorLine);
}

async function askValues(title, value, type, allowNull, structure) {
    return vscode.window.showInputBox({
        title, value, prompt: type === 'Student' || type === 'Car' ? 'מספר' : 'ערכים מופרדים בפסיקים',
        validateInput: s => {
            try {
                templates.generate({ structure, type, returns: 'void', values: s, className: 'A', functionName: 'f' });
                return null;
            } catch (e) { return e.message; }
        }
    });
}

function typeItems(structure) {
    const items = [
        { label: 'Integer', description: 'מספרים שלמים' },
        { label: 'String', description: 'מחרוזות' },
        { label: 'Character', description: 'תווים' },
        { label: 'Double', description: 'מספרים עשרוניים' },
        { label: 'Student', description: 'תלמידים — שם, כיתה וציונים (נתוני דמה)' },
        { label: 'Car', description: 'מכוניות — יצרן, דגם, שנה, דלק (נתוני דמה)' }
    ];
    return structure === 'matrix' ? items.filter(i => i.label !== 'Student' && i.label !== 'Car') : items;
}

// ------------------------------------------------------------ הוספה לקוד: מבנה עם נתוני דמה במקום הסמן

async function insertSnippet(structure) {
    const ed = vscode.window.activeTextEditor;
    if (!ed || ed.document.languageId !== 'java') {
        vscode.window.showInformationMessage('פתחו קובץ Java ושימו את הסמן במקום שבו רוצים את המבנה (בתוך main או בתוך פעולה).');
        return;
    }
    if (!structure) {
        const pick = await vscode.window.showQuickPick(
            Object.entries(templates.SNIPPETS).map(([id, label]) => ({ label, id })),
            { title: 'הוספה לקוד — איזה מבנה?' });
        if (!pick) return;
        structure = pick.id;
    }
    const label = templates.SNIPPETS[structure];
    let type = structure === 'car' ? 'Car' : 'Student';
    if (structure !== 'student' && structure !== 'car') {
        const t = await vscode.window.showQuickPick(typeItems(structure), { title: `${label} — טיפוס הערכים` });
        if (!t) return;
        type = t.label;
    }
    const opts = { structure, type, seed: Math.floor(Math.random() * 2147483647), size: 5, rows: 3, cols: 4 };
    if (structure === 'matrix') {
        const dims = await vscode.window.showInputBox({ title: `${label} — גודל (שורות x עמודות)`, value: '3x4',
            validateInput: s => /^\s*\d{1,2}\s*[x×*,]\s*\d{1,2}\s*$/.test(s) ? null : 'למשל 3x4' });
        if (!dims) return;
        [opts.rows, opts.cols] = dims.split(/[x×*,]/).map(x => +x.trim());
    } else if (structure !== 'student' && structure !== 'car') {
        const size = await vscode.window.showInputBox({ title: `${label} — כמה איברים?`, value: type === 'Student' || type === 'Car' ? '4' : '5',
            validateInput: s => /^\d{1,2}$/.test(s.trim()) && +s > 0 && +s <= 30 ? null : 'מספר בין 1 ל-30' });
        if (!size) return;
        opts.size = +size;
    }
    const text = ed.document.getText();
    let name = templates.snippetName(structure);
    for (let i = 2; new RegExp('\\b' + name + '\\s*=').test(text); i++) name = templates.snippetName(structure) + i;
    name = await vscode.window.showInputBox({ title: `${label} — שם המשתנה`, value: name,
        validateInput: s => templates.isIdentifier(s) ? null : 'אותיות באנגלית וספרות בלבד, מתחיל באות' });
    if (!name) return;
    opts.name = name;

    const line = ed.document.lineAt(ed.selection.active.line);
    const blank = line.text.trim() === '';
    opts.indent = blank ? (line.text || '        ') : line.text.match(/^\s*/)[0];
    let code;
    try { code = templates.snippet(opts); } catch (e) { vscode.window.showErrorMessage(e.message); return; }
    await ed.edit(b => {
        if (blank) b.replace(line.range, code);
        else b.insert(line.range.end, '\n' + code);
        if (!/import\s+unit4\.collectionsLib\.\*\s*;/.test(text)) {
            const pkg = /^\s*package\s+[\w.]+\s*;.*$/m.exec(text);
            const at = pkg ? ed.document.positionAt(pkg.index + pkg[0].length) : new vscode.Position(0, 0);
            b.insert(at, pkg ? '\n\nimport unit4.collectionsLib.*;' : 'import unit4.collectionsLib.*;\n\n');
        }
    });
}

// ------------------------------------------------------------ הרצה

/**
 * "הרצה ויזואלית": מקמפל ומריץ את הקובץ הפתוח — בעצמנו (javac + java בטרמינל), בלי צורך בתוספים של Java
 * ל-VS Code. מריצים דרך LineByLine שבספרייה, וכל שורה בקוד היא צעד בחלון.
 */
async function run() {
    const ed = vscode.window.activeTextEditor;
    if (!ed || !ed.document.fileName.endsWith('.java')) {
        vscode.window.showInformationMessage('פתחו קובץ Java (תרגיל) ואז לחצו "הרצה ויזואלית".');
        return;
    }
    await ed.document.save();
    const text = ed.document.getText();
    if (!/static\s+void\s+main\s*\(/.test(text)) {
        vscode.window.showWarningMessage('בקובץ הזה אין main — אין מה להריץ.');
        return;
    }
    const folder = vscode.workspace.getWorkspaceFolder(ed.document.uri);
    if (!folder) {
        vscode.window.showWarningMessage('הקובץ לא נמצא בתוך תיקייה פתוחה. פתחו את התיקייה (File → Open Folder).');
        return;
    }
    const jdk = runner.findJava(vscode.workspace.getConfiguration('dsviz').get('javaHome'));
    if (!jdk) {
        const btn = 'הורדת Java (JDK 21)';
        const ans = await vscode.window.showErrorMessage(
            'לא נמצאה Java במחשב. תוכנית ההתקנה של מבני נתונים מתקינה Java — או שאפשר להוריד JDK.', btn);
        if (ans === btn) vscode.env.openExternal(vscode.Uri.parse('https://adoptium.net/temurin/releases/?version=21'));
        return;
    }
    await vscode.workspace.saveAll(false);
    const root = folder.uri.fsPath;
    if (project.findConflicts(root).length) await warnConflicts(root);
    let srcDir = project.sourceDir(root);
    if (!ed.document.fileName.startsWith(srcDir + path.sep)) srcDir = path.dirname(ed.document.fileName);   // קובץ מחוץ ל-src
    const jar = project.agentJar(root, path.join(ctx.extensionPath, 'resources', project.JAR_NAME));
    const binDir = path.join(root, 'bin');
    const mainClass = project.mainClassOf(ed.document.fileName, text);

    const result = await vscode.window.withProgress(
        { location: vscode.ProgressLocation.Window, title: 'מבני נתונים: מקמפל...' },
        () => runner.compile(jdk.javac, srcDir, binDir, jar));
    showErrors(result);
    if (!result.ok) {
        output.clear();
        output.appendLine(result.output);
        const btn = 'פרטים';
        const n = result.errors.filter(e => e.severity === 'error').length;
        const ans = await vscode.window.showErrorMessage(
            n ? `יש ${n} שגיאות קומפילציה (מסומנות באדום בקוד). מתקנים ומריצים שוב.` : 'הקומפילציה נכשלה.', btn);
        if (ans === btn) output.show(true);
        return;
    }

    const args = runner.runArgs(mainClass, binDir, jar);
    if (terminal) terminal.dispose();
    const name = 'מבני נתונים: הרצה ויזואלית';
    terminal = runner.WIN
        ? vscode.window.createTerminal({ name, cwd: root, shellPath: process.env.ComSpec || 'cmd.exe',
                                         shellArgs: ['/d', '/c', runner.windowsScript(binDir, jdk.java, args)] })
        : vscode.window.createTerminal({ name, cwd: root, shellPath: jdk.java, shellArgs: args });
    terminal.show(true);
}

/** שגיאות הקומפילציה כקו אדום בקוד (עובד גם בלי התוספים של Java) */
function showErrors(result) {
    diagnostics.clear();
    const byFile = new Map();
    for (const e of result.errors) {
        const d = new vscode.Diagnostic(new vscode.Range(e.line - 1, 0, e.line - 1, 1000), e.message,
            e.severity === 'error' ? vscode.DiagnosticSeverity.Error : vscode.DiagnosticSeverity.Warning);
        d.source = 'javac';
        if (!byFile.has(e.file)) byFile.set(e.file, []);
        byFile.get(e.file).push(d);
    }
    for (const [file, list] of byFile) diagnostics.set(vscode.Uri.file(file), list);
}

// ------------------------------------------------------------ דוגמאות

async function examples() {
    const root = await chooseRoot();
    if (!root) return;
    const { srcDir, warnings } = project.setup(root, path.join(ctx.extensionPath, 'resources', project.JAR_NAME));
    for (const w of warnings) vscode.window.showWarningMessage(w);
    const from = path.join(ctx.extensionPath, 'resources', 'examples');
    const to = path.join(srcDir, 'examples');
    fs.mkdirSync(to, { recursive: true });
    const files = fs.readdirSync(from).filter(f => f.endsWith('.java'));
    for (const f of files) {
        if (!fs.existsSync(path.join(to, f))) fs.copyFileSync(path.join(from, f), path.join(to, f));
    }
    const names = { StackExamples: 'מחסנית', QueueExamples: 'תור', ListExamples: 'רשימה מקושרת', TreeExamples: 'עצים',
                    ArrayExamples: 'מערכים (חד ודו ממדיים)', StudentExamples: 'תלמידים (Student)', CarExamples: 'מכוניות (Car)' };
    const pick = await vscode.window.showQuickPick(
        files.map(f => ({ label: names[path.basename(f, '.java')] || f, description: f, file: f })),
        { title: 'איזו דוגמה לפתוח?' });
    if (pick) await openAt(root, path.join(to, pick.file), 0);
}

// ------------------------------------------------------------ עזרים

/** תיקיית העבודה. אם אין תיקייה פתוחה — מבקשים לבחור ופותחים אותה */
async function chooseRoot() {
    const folders = vscode.workspace.workspaceFolders;
    if (folders && folders.length === 1) return folders[0].uri.fsPath;
    if (folders && folders.length > 1) {
        const f = await vscode.window.showWorkspaceFolderPick({ placeHolder: 'באיזו תיקייה ליצור את התרגיל?' });
        return f && f.uri.fsPath;
    }
    const ans = await vscode.window.showInformationMessage(
        'צריך תיקייה לתרגילים. לבחור (או ליצור) תיקייה עכשיו?', { modal: true }, 'בחירת תיקייה');
    if (!ans) return undefined;
    const uris = await vscode.window.showOpenDialog({ canSelectFolders: true, canSelectFiles: false, openLabel: 'שמירת התרגילים כאן' });
    return uris && uris[0] && uris[0].fsPath;
}

/** פותח קובץ ומציב את הסמן. אם התיקייה עוד לא פתוחה — פותחים אותה, והקובץ ייפתח אחרי הטעינה */
async function openAt(root, file, line) {
    const inWorkspace = (vscode.workspace.workspaceFolders || []).some(f => f.uri.fsPath === root);
    if (!inWorkspace) {
        await ctx.globalState.update(PENDING_KEY, { file, line });
        await vscode.commands.executeCommand('vscode.openFolder', vscode.Uri.file(root), false);
        return;
    }
    const doc = await vscode.workspace.openTextDocument(file);
    const ed = await vscode.window.showTextDocument(doc);
    const pos = new vscode.Position(line, doc.lineAt(Math.min(line, doc.lineCount - 1)).text.length);
    ed.selection = new vscode.Selection(pos, pos);
    ed.revealRange(new vscode.Range(pos, pos), vscode.TextEditorRevealType.InCenter);
    vscode.window.showInformationMessage('כתבו את הפעולה, ואז לחצו "הרצה ויזואלית" (למעלה מימין, או בשורת המצב למטה).');
}

async function openPending() {
    const p = ctx.globalState.get(PENDING_KEY);
    if (!p) return;
    await ctx.globalState.update(PENDING_KEY, undefined);
    if (fs.existsSync(p.file)) {
        const root = (vscode.workspace.workspaceFolders || []).find(f => p.file.startsWith(f.uri.fsPath));
        if (root) await openAt(root.uri.fsPath, p.file, p.line);
    }
}

async function warnConflicts(root) {
    const conflicts = project.findConflicts(root);
    if (!conflicts.length) return;
    const btn = 'השבתה (שינוי שם ל-.bak)';
    const ans = await vscode.window.showWarningMessage(
        'נמצאה ספריית unit4 נוספת שתתנגש עם המעטפת: ' + conflicts.map(c => path.basename(c)).join(', '), btn);
    if (ans === btn) for (const c of conflicts) fs.renameSync(c, c + '.bak');
}

module.exports = { activate, deactivate };
