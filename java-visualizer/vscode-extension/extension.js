'use strict';
const vscode = require('vscode');
const fs = require('fs');
const path = require('path');
const templates = require('./templates');
const project = require('./project');

const PENDING_KEY = 'dsviz.pendingOpen';
const JAVA_PACK = 'vscjava.vscode-java-pack';

let ctx;

function activate(context) {
    ctx = context;
    const reg = (id, fn) => context.subscriptions.push(vscode.commands.registerCommand(id, fn));
    reg('dsviz.newExercise', () => newExercise());
    reg('dsviz.newStack', () => newExercise('stack'));
    reg('dsviz.newQueue', () => newExercise('queue'));
    reg('dsviz.newList', () => newExercise('list'));
    reg('dsviz.newTree', () => newExercise('tree'));
    reg('dsviz.newBst', () => newExercise('bst'));
    reg('dsviz.run', () => run(false));
    reg('dsviz.runLines', () => run(true));
    reg('dsviz.examples', () => examples());

    // תצוגה בסרגל הצד — הכפתורים עצמם מוגדרים ב-package.json (viewsWelcome)
    context.subscriptions.push(vscode.window.registerTreeDataProvider('dsviz.home', {
        getChildren: () => [], getTreeItem: e => e
    }));

    // כפתור "הרצה ויזואלית" בשורת המצב, כשפתוח קובץ Java
    const bar = vscode.window.createStatusBarItem(vscode.StatusBarAlignment.Left, 100);
    bar.text = '$(play-circle) הרצה ויזואלית';
    bar.tooltip = 'הרצת הקובץ עם תצוגה ויזואלית של מבני הנתונים';
    bar.command = 'dsviz.run';
    const lines = vscode.window.createStatusBarItem(vscode.StatusBarAlignment.Left, 99);
    lines.text = '$(debug-step-over) שורה אחר שורה';
    lines.tooltip = 'הרצה שבה כל שורה בקוד מוצגת כצעד, עם המשתנים';
    lines.command = 'dsviz.runLines';
    const refresh = () => {
        const ed = vscode.window.activeTextEditor;
        const show = ed && ed.document.languageId === 'java';
        for (const b of [bar, lines]) if (show) b.show(); else b.hide();
    };
    context.subscriptions.push(bar, lines, vscode.window.onDidChangeActiveTextEditor(refresh));
    refresh();

    openPending();
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

    const typePick = await vscode.window.showQuickPick([
        { label: 'Integer', description: 'מספרים שלמים' },
        { label: 'String', description: 'מחרוזות' },
        { label: 'Character', description: 'תווים' },
        { label: 'Double', description: 'מספרים עשרוניים' }
    ], { title: `${st.label} — (1/${total}) טיפוס הערכים`, placeHolder: `${st.cls}<?>` });
    if (!typePick) return;
    const type = typePick.label;
    const tp = templates.TYPES[type];

    const retItems = [
        { label: 'void', description: 'לא מחזירה ערך (למשל משנה את המבנה)', id: 'void' },
        { label: 'int', description: 'מחזירה מספר (למשל סכום, כמות)', id: 'int' },
        { label: 'boolean', description: 'מחזירה אמת/שקר (למשל "האם קיים")', id: 'boolean' }
    ];
    if (type !== 'Integer') retItems.push({ label: tp.primitive, description: 'מחזירה ערך מהמבנה', id: 'element' });
    retItems.push({ label: `${st.cls}<${type}>`, description: 'מחזירה מבנה חדש', id: 'structure' });
    const retPick = await vscode.window.showQuickPick(retItems,
        { title: `${st.label} — (2/${total}) מה הפעולה מחזירה?` });
    if (!retPick) return;

    const extraItems = [
        { label: `שני מבנים`, description: `הפעולה מקבלת שני ${st.cls} (למשל מיזוג, השוואה)`, id: 'second' },
        { label: `פרמטר נוסף x`, description: `${tp.primitive} x (למשל "מחקו את כל המופעים של x")`, id: 'extra' }
    ];
    if (type === 'Integer') extraItems.push({ label: 'ערכים אקראיים', description: 'בכל הרצה מספרים אחרים', id: 'random' });
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
        const v = await askValues(`${st.label} — (4/${total}) ערכים ל-${st.varName}` +
            (isTree ? '  (לפי רמות, null = אין צומת)' : ''), templates.defaultValues(structure, type, false), type, isTree);
        if (v === undefined) return;
        opts.values = v;
        if (opts.second) {
            const v2 = await askValues(`${st.label} — ערכים ל-${st.varName}2`,
                templates.defaultValues(structure, type, true), type, isTree);
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

async function askValues(title, value, type, allowNull) {
    return vscode.window.showInputBox({
        title, value, prompt: 'ערכים מופרדים בפסיקים',
        validateInput: s => {
            try { templates.literals(s, type, allowNull); return null; } catch (e) { return e.message; }
        }
    });
}

// ------------------------------------------------------------ הרצה

/** lineByLine = true: כל שורה בקוד היא צעד (דרך -javaagent של dsviz.jar, שמריץ את התוכנית תחת JDI) */
async function run(lineByLine) {
    const ed = vscode.window.activeTextEditor;
    if (!ed || !ed.document.fileName.endsWith('.java')) {
        vscode.window.showInformationMessage('פתחו קובץ Java (תרגיל) ואז לחצו "הרצה ויזואלית".');
        return;
    }
    if (!(await ensureJavaTools())) return;
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
    const conflicts = project.findConflicts(folder.uri.fsPath);
    if (conflicts.length) await warnConflicts(folder.uri.fsPath);
    const mainClass = project.mainClassOf(ed.document.fileName, text);
    const vmArgs = ['-Dfile.encoding=UTF-8', '-Dstdout.encoding=UTF-8'];
    if (lineByLine) vmArgs.push('-javaagent:' + project.agentJar(folder.uri.fsPath, path.join(ctx.extensionPath, 'resources', project.JAR_NAME)) + '=' + mainClass);
    await vscode.debug.startDebugging(folder, {
        type: 'java',
        name: lineByLine ? 'הרצה שורה אחר שורה' : 'הרצה ויזואלית',
        request: 'launch',
        mainClass,
        cwd: folder.uri.fsPath,
        vmArgs
    }, { noDebug: true });
}

async function ensureJavaTools() {
    const ok = vscode.extensions.getExtension('redhat.java') && vscode.extensions.getExtension('vscjava.vscode-java-debug');
    if (ok) return true;
    const btn = 'התקנה';
    const ans = await vscode.window.showWarningMessage(
        'כדי להריץ Java צריך את התוסף "Extension Pack for Java" (חינמי, של Microsoft).', btn);
    if (ans === btn) await vscode.commands.executeCommand('workbench.extensions.installExtension', JAVA_PACK);
    return false;
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
    const names = { StackExamples: 'מחסנית', QueueExamples: 'תור', ListExamples: 'רשימה מקושרת', TreeExamples: 'עצים' };
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
