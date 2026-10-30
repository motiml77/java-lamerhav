'use strict';
const vscode = require('vscode');
const fs = require('fs');
const path = require('path');
const javaTemplates = require('./templates');
const csTemplates = require('./templates-cs');
const project = require('./project');
const runner = require('./runner');
const cs = require('./cs');
const addstructure = require('./addstructure');
const names = require('./names');
const help = require('./help');
const submitModule = require('./submit');

/** התבניות של השפה שעובדים בה עכשיו (Java / C#) — נקבע בתחילת כל פעולה לפי השפה */
let templates = javaTemplates;
let currentLang = 'java';
function useLanguage(lang) {
    currentLang = lang;
    templates = lang === 'csharp' ? csTemplates : javaTemplates;
}

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
    reg('dsviz.help', () => openHelp());
    reg('dsviz.run', () => run());
    reg('dsviz.runLines', () => run());   // שם ישן (מגרסה קודמת) — אותה הרצה
    reg('dsviz.examples', () => examples());
    reg('dsviz.openProject', () => openProject());
    // "שליחת תרגיל" ו"הצעה לפתרון" — אותו מנוע כמו ב-Eclipse (submit.js → SubmitCli שבתוך dsviz.jar)
    const sub = submitModule.register({ vscode, ctx: context, chooseRoot, detectLanguage: root => detectLanguage(root),
        project, cs, runner, openFiles });
    reg('dsviz.submit', () => sub.submit());
    reg('dsviz.solution', entry => sub.showSolution(entry));

    // תצוגה בסרגל הצד — הכפתורים עצמם מוגדרים ב-package.json (viewsWelcome)
    for (const view of ['dsviz.home', 'dsviz.insertView', 'dsviz.runView']) {
        context.subscriptions.push(vscode.window.registerTreeDataProvider(view, { getChildren: () => [], getTreeItem: e => e }));
    }

    // כפתור "הרצה ויזואלית" בשורת המצב, כשפתוח קובץ Java או C#
    const bar = vscode.window.createStatusBarItem(vscode.StatusBarAlignment.Left, 100);
    bar.text = '$(play-circle) הרצה ויזואלית';
    bar.tooltip = 'הרצה שבה רואים כל שורה בקוד, את המשתנים ואת מבני הנתונים — צעד אחר צעד';
    bar.command = 'dsviz.run';
    // וכפתור "הוסף מבנה נתונים" לידו: בוחרים מבנה וערכים, והוא נבנה בקוד במקום הסמן
    const addBar = vscode.window.createStatusBarItem(vscode.StatusBarAlignment.Left, 99);
    addBar.text = '$(add) הוסף מבנה נתונים';
    addBar.tooltip = 'בוחרים מבנה נתונים, ערכים ושם — והוא נבנה בקוד במקום הסמן';
    addBar.command = 'dsviz.insert';
    // וכפתור "שליחת תרגיל": מצרפים את דף התרגיל ומקבלים קוד מוכן (עם 💡 הצעה לפתרון לכל סעיף)
    const submitBar = vscode.window.createStatusBarItem(vscode.StatusBarAlignment.Left, 101);
    submitBar.text = '$(send) שליחת תרגיל';
    submitBar.tooltip = 'מצרפים את דף התרגיל (PDF, Word או צילום מסך) — ומקבלים קובץ מוכן עם מקום לכתוב את הפתרון';
    submitBar.command = 'dsviz.submit';
    context.subscriptions.push(submitBar);
    const refresh = () => {
        const ed = vscode.window.activeTextEditor;
        const on = ed && (ed.document.languageId === 'java' || ed.document.languageId === 'csharp');
        if (on) { bar.show(); addBar.show(); submitBar.show(); } else { bar.hide(); addBar.hide(); submitBar.hide(); }
    };
    // וכפתור "?" — הדרכה לשימוש (סרטונים קצרים)
    const helpBar = vscode.window.createStatusBarItem(vscode.StatusBarAlignment.Left, 98);
    helpBar.text = '$(question)';
    helpBar.tooltip = 'הדרכה לשימוש';
    helpBar.command = 'dsviz.help';
    helpBar.show();
    context.subscriptions.push(bar, addBar, helpBar, vscode.window.onDidChangeActiveTextEditor(refresh));
    // פעולות העזר מהספרייה: כותבים size(q) / duplicate(q) בקובץ Java — ושורת ה-import static נוספת לבד
    if (vscode.workspace.onDidChangeTextDocument) context.subscriptions.push(vscode.workspace.onDidChangeTextDocument(e => helperImportSoon(e.document)));
    refresh();

    openPending();
    firstRun();
}

/** פתיחת תיקיית התרגילים שהותקנה (במסמכים) */
async function openProject() {
    const options = [];
    const j = project.installedProject(), c = cs.installedProject();
    if (j) options.push({ label: 'Java', description: j, dir: j, file: path.join(j, 'src', 'Main.java') });
    if (c) options.push({ label: 'C#', description: c, dir: c, file: path.join(c, 'Exercise', 'Program.cs') });
    let pick = options[0];
    if (options.length > 1) pick = await vscode.window.showQuickPick(options, { title: 'איזו תיקיית תרגילים לפתוח?' });
    if (pick) {
        await ctx.globalState.update(PENDING_KEY, { file: pick.file, line: 0 });
        await vscode.commands.executeCommand('vscode.openFolder', vscode.Uri.file(pick.dir), false);
    } else {
        const root = await chooseRoot();
        if (root) await examples();
    }
}

/** פעם ראשונה אחרי ההתקנה, בלי תיקייה פתוחה: מציעים לפתוח את תיקיית התרגילים */
async function firstRun() {
    if (ctx.globalState.get('dsviz.welcomed') || vscode.workspace.workspaceFolders) return;
    if (!project.installedProject() && !cs.installedProject()) return;
    await ctx.globalState.update('dsviz.welcomed', true);
    const open = 'פתיחת תיקיית התרגילים';
    const ans = await vscode.window.showInformationMessage(
        'מבני נתונים — הכול מוכן! לפתוח את תיקיית התרגילים (עם תרגיל לדוגמה ודוגמאות פתורות)?', open);
    if (ans === open) await openProject();
}

function deactivate() {}

// ------------------------------------------------------------ תרגיל חדש

/** באיזו שפה כותבים: הגדרה dsviz.language → הקובץ הפתוח → מה שיש בתיקייה → שואלים */
async function chooseLanguage(root) {
    const setting = vscode.workspace.getConfiguration('dsviz').get('language');
    if (setting === 'java' || setting === 'csharp') return setting;
    const ed = vscode.window.activeTextEditor;
    if (ed && ed.document.languageId === 'csharp') return 'csharp';
    if (ed && ed.document.languageId === 'java') return 'java';
    const detected = cs.detectLanguage(root);
    if (detected) return detected;
    const pick = await vscode.window.showQuickPick(
        [{ label: 'Java', id: 'java' }, { label: 'C#', id: 'csharp' }],
        { title: 'תרגיל חדש — באיזו שפה?', placeHolder: 'בחרו שפה' });
    return pick && pick.id;
}

async function newExercise(preset) {
    const root = await chooseRoot();
    if (!root) return;
    const lang = await chooseLanguage(root);
    if (!lang) return;
    useLanguage(lang);
    const csharp = lang === 'csharp';

    const total = 6;
    let structure = preset;
    if (!structure) {
        const pick = await vscode.window.showQuickPick(
            Object.entries(templates.STRUCTURES).filter(([, s]) => !s.combo).map(([id, s]) => ({ label: s.label, description: s.cls, id })),
            { title: 'תרגיל חדש — איזה מבנה נתונים?', placeHolder: 'בחרו מבנה נתונים' });
        if (!pick) return;
        structure = pick.id;
    }
    // מה יש בכל תא: ערכים רגילים (ברירת מחדל — Enter), או מבנה שלם — מחסנית של מחסניות, רשימה של תורים...
    const inside = await askInside(structure, templates.STRUCTURES[structure].label);
    if (inside === undefined) return;
    structure = templates.comboId(structure, inside);
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

    // בחירה נפרדת לרקורסיה (מפעילה את לשונית הכרטיסים) ולמילוי אקראי (מסומן מראש); הפעולה מקבלת מבנה אחד — עוד אפשר להוסיף בקוד
    const extraItems = [{ label: 'תרגיל ברקורסיה', description: 'בחלון ההרצה תופיע גם לשונית "רקורסיה" — כרטיס לכל קריאה', id: 'recursive' }];
    if (type !== 'Student' && type !== 'Car') extraItems.push({ label: st.combo ? 'כמה מבנים פנימיים אקראיים' : 'כמה ערכים אקראיים',
        description: st.combo ? 'מילוי המבנה: בוחרים כמה — ונוצרים מבנים פנימיים עם ערכים אקראיים' : 'מילוי המבנה: בוחרים כמה — והערכים נוצרים אקראית',
        id: 'random', picked: true });
    const extras = await vscode.window.showQuickPick(extraItems,
        { title: `${st.label} — (3/${total}) רקורסיה ומילוי המבנה (אפשר לדלג עם Enter)`, canPickMany: true });
    if (!extras) return;
    const has = id => extras.some(e => e.id === id);

    const opts = {
        structure, type, returns: retPick.id,
        count: 1, second: false, extra: false, random: false, recursive: has('recursive'),
        values: '', values2: '', className: '', functionName: ''
    };

    {
        const isTree = structure === 'tree' || structure.startsWith('tree_');
        const hint = st.combo ? `  (כל קבוצה, עד ;, היא מבנה פנימי אחד${isTree ? ' — לפי רמות, null = אין צומת' : ''})`
            : type === 'Student' ? '  (כמה תלמידים — נוצרים עם נתוני דמה)' : type === 'Car' ? '  (כמה מכוניות — נוצרות עם נתוני דמה)'
            : structure === 'matrix' ? '  (שורות מופרדות ב-;)' : isTree ? '  (לפי רמות, null = אין צומת)' : '';
        let first = templates.defaultValues(structure, type, false), second = templates.defaultValues(structure, type, true);
        if (has('random')) {
            // ערכים אקראיים: בוחרים כמה — והשדה מתמלא בערכים אקראיים (אפשר לשנות לפני היצירה)
            const maxCount = st.combo ? 12 : 20;
            const count = await vscode.window.showInputBox({
                title: `${st.label} — ${st.combo ? 'כמה מבנים פנימיים אקראיים (2–4 ערכים בכל אחד)?' : structure === 'matrix' ? 'כמה שורות אקראיות (4 עמודות)?' : 'כמה ערכים אקראיים?'}`,
                value: st.combo ? '3' : '6',
                validateInput: s => /^\d{1,2}$/.test(s.trim()) && +s > 0 && +s <= maxCount ? null : `מספר בין 1 ל-${maxCount}` });
            if (count === undefined) return;
            const seed = Math.floor(Math.random() * 2147483647);
            first = templates.randomValues(structure, type, +count, seed);
            second = templates.randomValues(structure, type, Math.max(1, +count - 2), seed + 1);
        }
        const v = await askValues(`${st.label} — (4/${total}) מילוי מבנה-נתונים — ערכים ל-${st.varName}${hint}`, first, type, isTree, structure);
        if (v === undefined) return;
        opts.values = v;
        if (opts.count >= 2) {
            const v2 = await askValues(`${st.label} — ערכים ל-${templates.nameN(st.varName, 2)}${hint}`, second, type, isTree, structure);
            if (v2 === undefined) return;
            opts.values2 = v2;
        }
        if (opts.count >= 3) {
            const v3 = await askValues(`${st.label} — ערכים ל-${templates.nameN(st.varName, 3)}${hint}`, second, type, isTree, structure);
            if (v3 === undefined) return;
            opts.values3 = v3;
        }
    }

    let srcDir, warnings;
    if (csharp) {
        ({ warnings } = cs.setup(root, path.join(ctx.extensionPath, 'resources', 'cs', 'lib', cs.LIB)));
        srcDir = cs.exerciseDir(root);
        fs.mkdirSync(srcDir, { recursive: true });
    } else {
        ({ srcDir, warnings } = project.setup(root, path.join(ctx.extensionPath, 'resources', project.JAR_NAME)));
        await warnConflicts(root);
    }
    for (const w of warnings) vscode.window.showWarningMessage(w);

    const className = await vscode.window.showInputBox({
        title: `${st.label} — (5/${total}) ${csharp ? 'שם הפרויקט (והמחלקה)' : 'שם המחלקה (הקובץ)'}`,
        value: csharp ? cs.nextProjectName(srcDir, 'Targil') : project.nextClassName(srcDir, 'Targil'),
        validateInput: s => !templates.isIdentifier(s) ? 'אותיות באנגלית וספרות בלבד, מתחיל באות'
            : fs.existsSync(path.join(srcDir, s + (csharp ? '' : '.java'))) ? 'כבר קיים בשם הזה' : null
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
    let file;
    if (csharp) {
        file = cs.writeProject(srcDir, className, gen.code);   // תיקייה לתרגיל: .csproj + Program.cs
    } else {
        file = path.join(srcDir, className + '.java');
        fs.writeFileSync(file, gen.code, 'utf8');
    }
    await openAt(root, file, gen.cursorLine);
    showClassInfo(type);
}

async function askValues(title, value, type, allowNull, structure) {
    return vscode.window.showInputBox({
        title, value, prompt: type === 'Student' || type === 'Car' ? 'מספר'
            : templates.STRUCTURES[structure].combo ? 'ערכים מופרדים בפסיקים; מבנים פנימיים מופרדים ב-; (למשל 5, 2; 8, 1, 9; 3)' : 'ערכים מופרדים בפסיקים',
        validateInput: s => {
            try {
                templates.generate({ structure, type, returns: 'void', values: s, className: 'A', functionName: 'f' });
                const preview = templates.comboPreview(structure, type, s);   // מבנה בתוך מבנה: מראים מה ייבנה
                return preview && vscode.InputBoxValidationSeverity
                    ? { message: preview.replace('\n', '   ←   '), severity: vscode.InputBoxValidationSeverity.Info } : null;
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
    const noObjects = structure === 'matrix' || (templates.STRUCTURES[structure] && templates.STRUCTURES[structure].combo);
    return noObjects ? items.filter(i => i.label !== 'Student' && i.label !== 'Car') : items;
}

/**
 * "מה יש בכל תא?" — ערכים רגילים (ברירת המחדל), או מחסניות / תורים / רשימות מקושרות (מבנה בתוך מבנה).
 * מחזיר '' / 'stack' / 'queue' / 'list', או undefined אם ביטלו. למבנים שאי אפשר בהם (מטריצה, עץ חיפוש) — '' בלי לשאול.
 */
async function askInside(outer, label) {
    if (!templates.canNest(outer)) return '';
    const items = templates.INSIDE.map(i => ({
        label: i.label,
        description: i.id ? `${label} של ${i.label} — ${templates.typeOf(templates.STRUCTURES[templates.comboId(outer, i.id)], 'Integer')}` : i.description,
        id: i.id
    }));
    const pick = await vscode.window.showQuickPick(items,
        { title: `${label} — מה יש בכל תא?`, placeHolder: 'Enter = ערכים רגילים. או בחרו מבנה שלם בכל תא (מחסנית של מחסניות...)' });
    return pick ? pick.id : undefined;
}

// ------------------------------------------------------------ הוסף מבנה נתונים: חלון בחירה, ואז המבנה נבנה בקוד במקום הסמן

let addPanel;

/** השורה שבה ייכנס הקוד: שורה ריקה — מחליפים אותה; אחרת — מתחת לשורה הנוכחית (באותה הזחה) */
function insertionPoint(doc, lineNo) {
    const line = doc.lineAt(Math.min(lineNo, doc.lineCount - 1));
    const blank = line.text.trim() === '';
    return { line, blank, indent: blank ? (line.text || '        ') : line.text.match(/^\s*/)[0], offset: doc.offsetAt(blank ? line.range.start : line.range.end) };
}

async function insertSnippet(structure) {
    const ed = vscode.window.activeTextEditor;
    if (!ed || (ed.document.languageId !== 'java' && ed.document.languageId !== 'csharp')) {
        vscode.window.showInformationMessage('פתחו קובץ Java או C# ושימו את הסמן במקום שבו רוצים את המבנה (בתוך main או בתוך פעולה).');
        return;
    }
    const csharp = ed.document.languageId === 'csharp';
    useLanguage(csharp ? 'csharp' : 'java');
    const lineNo = ed.selection.active.line;
    const at = insertionPoint(ed.document, lineNo);
    // שכבת הגנה: אי אפשר להכניס משתנה / פקודות מחוץ לפעולה (בגוף המחלקה או אחריה) — מזהירים ולא פותחים את החלון
    if (!names.insideFunction(ed.document.getText(), at.offset)) {
        vscode.window.showWarningMessage('הסמן נמצא מחוץ לפעולה (בגוף המחלקה, או לפני / אחרי המחלקה), ושם אי אפשר לכתוב פקודות. '
            + 'לחצו בתוך הפעולה main (או בכל פעולה אחרת), בשורה שבה רוצים את המבנה — ואז לחצו שוב על "הוסף מבנה נתונים".', { modal: true });
        return;
    }
    const session = new addstructure.Session({ tpl: templates, text: ed.document.getText(), offset: at.offset, indent: at.indent, preset: structure });
    if (addPanel) { try { addPanel.dispose(); } catch (e) { /* כבר נסגר */ } }
    const panel = vscode.window.createWebviewPanel('dsviz.addStructure', 'הוסף מבנה נתונים',
        { viewColumn: vscode.ViewColumn.Beside, preserveFocus: false }, { enableScripts: true, retainContextWhenHidden: true });
    addPanel = panel;
    panel.webview.html = addstructure.html(addstructure.options(templates, session.initial()));
    panel.onDidDispose(() => { if (addPanel === panel) addPanel = undefined; });
    panel.webview.onDidReceiveMessage(async msg => {
        if (msg.type === 'cancel') { panel.dispose(); return; }
        // הקובץ יכול להשתנות כשהחלון פתוח — בודקים את השם מול הקוד העדכני
        const now = insertionPoint(ed.document, lineNo);
        session.update(ed.document.getText(), now.offset, now.indent);
        const view = session.view(msg.state, msg.reason);
        if (msg.type === 'ok' && view.ok) {
            await insertCode(ed, lineNo, view.code, csharp);
            panel.dispose();
            if (view.chosenClass) showClassInfo(view.chosenClass);
            return;
        }
        panel.webview.postMessage({ type: 'view', view });
    });
}

// ------------------------------------------------------------ פעולות העזר: import static אוטומטי (כמו AutoImport ב-Eclipse)

const helperTimers = new Map();

function helperImportSoon(doc) {
    if (!doc || doc.languageId !== 'java') return;
    const key = doc.uri.toString();
    clearTimeout(helperTimers.get(key));
    helperTimers.set(key, setTimeout(() => { helperTimers.delete(key); addHelperImport(doc); }, 800));
}

/** הטקסט צריך את שורת ה-import static? — קריאה "ערומה" ל-size( / duplicate( (לא s.size()), בלי פעולה כזו בקובץ, בלי השורה */
function needsHelperImport(text) {
    const code = text.replace(/\/\*[\s\S]*?\*\/|\/\/[^\n]*|"(?:[^"\\\n]|\\.)*"|'(?:[^'\\\n]|\\.)*'/g, ' ')   // בלי הערות ומחרוזות
        .replace(/\.\s*(size|duplicate)\s*\(/g, '.x(');                                                                  // בלי s.size()
    if (!/\b(size|duplicate)\s*\(/.test(code)) return false;
    if (/[\w>\]]\s+(size|duplicate)\s*\([^)]*\)\s*(throws\s+[\w.,\s]+)?\{/.test(code)) return false;   // התלמיד כתב פעולה כזו בעצמו
    if (/import\s+static\s+unit4\.collectionsLib\.Helpers\.(\*|size|duplicate)\s*;/.test(text)) return false;
    return /import\s+unit4\.collectionsLib\./.test(text) || /\b(Stack|Queue|Node|BinNode)\s*</.test(code);
}

async function addHelperImport(doc) {
    if (doc.isClosed || !needsHelperImport(doc.getText())) return;
    const text = doc.getText();
    const lib = /^\s*import\s+unit4\.collectionsLib\.\*\s*;.*$/m.exec(text);
    const pkg = /^\s*package\s+[\w.]+\s*;.*$/m.exec(text);
    const edit = new vscode.WorkspaceEdit();
    if (lib) edit.insert(doc.uri, doc.positionAt(lib.index + lib[0].length), '\n' + javaTemplates.HELPERS_IMPORT);
    else if (pkg) edit.insert(doc.uri, doc.positionAt(pkg.index + pkg[0].length), '\n\nimport unit4.collectionsLib.*;\n' + javaTemplates.HELPERS_IMPORT);
    else edit.insert(doc.uri, new vscode.Position(0, 0), 'import unit4.collectionsLib.*;\n' + javaTemplates.HELPERS_IMPORT + '\n\n');
    await vscode.workspace.applyEdit(edit);
}

async function insertCode(ed, lineNo, code, csharp) {
    const editor = await vscode.window.showTextDocument(ed.document, { viewColumn: ed.viewColumn, preserveFocus: false });
    const text = editor.document.getText();
    const at = insertionPoint(editor.document, lineNo);
    await editor.edit(b => {
        if (at.blank) b.replace(at.line.range, code);
        else b.insert(at.line.range.end, '\n' + code);
        if (csharp) {
            const need = ['using System;', 'using Unit4.CollectionsLib;'].filter(u => !text.includes(u));   // פעולות העזר — global using בפרויקט
            if (need.length) b.insert(new vscode.Position(0, 0), need.join('\n') + '\n');
        } else if (!/import\s+unit4\.collectionsLib\.\*\s*;/.test(text)) {
            const pkg = /^\s*package\s+[\w.]+\s*;.*$/m.exec(text);
            const pos = pkg ? editor.document.positionAt(pkg.index + pkg[0].length) : new vscode.Position(0, 0);
            b.insert(pos, pkg ? '\n\nimport unit4.collectionsLib.*;' : 'import unit4.collectionsLib.*;\n\n');
        }
    });
}

// ------------------------------------------------------------ "?" — הדרכה לשימוש

let helpPanel;

/** בוחרים הדרכה (חלון בחירה) — ונפתח חלון נגן עם הסרטון שלה בלבד, עם בחירת מהירות (help/<id>.mp4 בתוך התוסף) */
async function openHelp(id) {
    let dir = path.join(ctx.extensionPath, 'resources', 'help');
    if (!fs.existsSync(dir)) {   // התוסף שההתקנה שמה — הסרטונים שמורים פעם אחת בתיקייה help של ההתקנה
        const base = process.env.LOCALAPPDATA ? path.join(process.env.LOCALAPPDATA, 'DSViz') : path.join(require('os').homedir(), 'DSViz');
        if (fs.existsSync(path.join(base, 'help', 'index.html'))) dir = path.join(base, 'help');
    }
    if (!fs.existsSync(dir) || !vscode.window.createWebviewPanel) {
        vscode.window.showWarningMessage('סרטוני ההדרכה לא נמצאו בהתקנה הזאת.');
        return;
    }
    let durations = {};
    try { durations = JSON.parse(fs.readFileSync(path.join(dir, 'tutorials.json'), 'utf8')); } catch (e) { /* בלי משכים */ }
    if (!id) {
        const pick = await vscode.window.showQuickPick(
            help.TUTORIALS.map(t => ({ label: t.num + '  ·  ' + t.title, description: durations[t.id] || '', detail: t.desc, id: t.id })),
            { title: 'הדרכה לשימוש', placeHolder: 'איזו הדרכה לצפות?', matchOnDetail: true });
        if (!pick) return;
        id = pick.id;
    }
    const tut = help.TUTORIALS.find(t => t.id === id) || help.TUTORIALS[0];
    if (helpPanel) { try { helpPanel.dispose(); } catch (e) { /* כבר נסגר */ } }
    const panel = vscode.window.createWebviewPanel('dsviz.help', 'הדרכה: ' + tut.title, vscode.ViewColumn.Active,
        { enableScripts: true, retainContextWhenHidden: true, localResourceRoots: [vscode.Uri.file(dir)] });
    helpPanel = panel;
    panel.onDidDispose(() => { if (helpPanel === panel) helpPanel = undefined; });
    const src = panel.webview.cspSource;
    panel.webview.html = help.playerPage({
        id: tut.id,
        urlOf: f => panel.webview.asWebviewUri(vscode.Uri.file(path.join(dir, f))).toString(),
        csp: `default-src 'none'; img-src ${src} data:; media-src ${src}; style-src 'unsafe-inline'; script-src 'unsafe-inline';`
    });
    panel.webview.onDidReceiveMessage(m => {
        if (m && m.cmd === 'list') openHelp();
        else if (m && m.cmd === 'open' && /^https:\/\/www\.paypal\.com\//.test(String(m.url))) vscode.env.openExternal(vscode.Uri.parse(m.url));   // "תרום לנו"
        else if (m && m.cmd === 'fail') {   // הנגן של החלון לא מצליח לנגן (קודק) — פותחים אותו דף בדפדפן הרגיל
            try { panel.dispose(); } catch (e) { /* כבר נסגר */ }
            const f = path.join(dir, 'player.html');
            vscode.env.openExternal(vscode.Uri.file(f).with ? vscode.Uri.file(f).with({ fragment: tut.id }) : vscode.Uri.file(f));
        }
    });
}

// ------------------------------------------------------------ "מה יש במחלקה" — חלון קטן בצד (Student / Car)

let infoPanel;

const esc = s => String(s).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');

function showClassInfo(className) {
    const info = currentLang === 'csharp' ? csTemplates.classInfo(className) : javaTemplates.CLASS_INFO[className];
    if (!info || !vscode.window.createWebviewPanel) return;
    const table = (heads, rows) => '<table><tr>' + heads.map(h => `<th>${h}</th>`).join('') + '</tr>'
        + rows.map(r => '<tr>' + r.map((c, i) => `<td${i === 0 ? ' class="code"' : ''}>${esc(c)}</td>`).join('') + '</tr>').join('') + '</table>';
    const html = `<!doctype html><html lang="he" dir="rtl"><head><meta charset="utf-8"><style>
        body { font: 13px/1.5 var(--vscode-font-family); padding: 10px 14px; color: var(--vscode-foreground); }
        h1 { font-size: 17px; margin: 0 0 2px; } .intro { opacity: .75; margin-bottom: 8px; }
        h2 { font-size: 13px; margin: 14px 0 4px; } table { width: 100%; border-collapse: collapse; }
        th { text-align: right; font-weight: 600; opacity: .7; border-bottom: 1px solid var(--vscode-panel-border); padding: 3px 6px; }
        td { padding: 3px 6px; border-bottom: 1px solid var(--vscode-panel-border); vertical-align: top; }
        td.code { direction: ltr; text-align: left; font-family: var(--vscode-editor-font-family); color: var(--vscode-textLink-foreground); white-space: nowrap; }
    </style></head><body>
        <h1>${esc(info.title)}</h1><div class="intro">${esc(info.intro)}</div>
        <h2>איך יוצרים</h2>${table(['קוד', 'מה זה'], info.creators)}
        <h2>מאפיינים</h2>${table(['שם', 'טיפוס', 'מה זה'], info.fields)}
        <h2>פעולות</h2>${table(['פעולה', 'מה היא עושה'], info.methods)}
    </body></html>`;
    if (infoPanel) { try { infoPanel.dispose(); } catch (e) { /* כבר נסגר */ } }
    infoPanel = vscode.window.createWebviewPanel('dsviz.classInfo', `מה יש ב-${info.name}`,
        { viewColumn: vscode.ViewColumn.Beside, preserveFocus: true }, {});
    infoPanel.webview.html = html;
    infoPanel.onDidDispose(() => { infoPanel = undefined; });
}

// ------------------------------------------------------------ הרצה

/**
 * "הרצה ויזואלית": מקמפל ומריץ את הקובץ הפתוח — בעצמנו (javac + java בטרמינל), בלי צורך בתוספים של Java
 * ל-VS Code. מריצים דרך LineByLine שבספרייה, וכל שורה בקוד היא צעד בחלון.
 */
async function run() {
    const ed = vscode.window.activeTextEditor;
    if (ed && ed.document.fileName.endsWith('.cs')) return runCs(ed);
    if (!ed || !ed.document.fileName.endsWith('.java')) {
        vscode.window.showInformationMessage('פתחו קובץ Java או C# (תרגיל) ואז לחצו "הרצה ויזואלית".');
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

/**
 * הרצה ויזואלית של תרגיל C#: dsviz-cs (מההתקנה) מקמפל את הפרויקט, מכניס דיווח לפני כל שורה, מריץ,
 * ופותח את אותו חלון ויזואלי. שגיאות קומפילציה מודפסות בטרמינל, באותו פורמט כמו ב-Visual Studio.
 */
async function runCs(ed) {
    await ed.document.save();
    await vscode.workspace.saveAll(false);
    const folder = vscode.workspace.getWorkspaceFolder(ed.document.uri);
    const root = folder ? folder.uri.fsPath : path.dirname(ed.document.fileName);
    const exe = cs.findRunner(vscode.workspace.getConfiguration('dsviz').get('csRunner'));
    if (!exe) {
        vscode.window.showErrorMessage('לא נמצא dsviz-cs (ההרצה הוויזואלית של C#). יש להתקין מחדש את DSViz-Setup.exe ולבחור את חבילת C#.');
        return;
    }
    const target = cs.projectDirOf(ed.document.fileName, root) || ed.document.fileName;
    if (terminal) terminal.dispose();
    const name = 'מבני נתונים: הרצה ויזואלית (C#)';
    const args = exe.prefix.concat([target]);
    terminal = runner.WIN
        ? vscode.window.createTerminal({ name, cwd: root, shellPath: process.env.ComSpec || 'cmd.exe',
                                         shellArgs: ['/d', '/c', runner.windowsScript(require('os').tmpdir(), exe.command, args)] })
        : vscode.window.createTerminal({ name, cwd: root, shellPath: exe.command, shellArgs: args });
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
    const lang = await chooseLanguage(root);
    if (!lang) return;
    if (lang === 'csharp') return examplesCs(root);
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
                    ArrayExamples: 'מערכים (חד ודו ממדיים)', StudentExamples: 'תלמידים (Student)', CarExamples: 'מכוניות (Car)', RecursionExamples: 'רקורסיה (עצרת, מערך, מחסנית)', NestedExamples: 'מבנה בתוך מבנה (מחסנית של מחסניות...)' };
    const pick = await vscode.window.showQuickPick(
        files.map(f => ({ label: names[path.basename(f, '.java')] || f, description: f, file: f })),
        { title: 'איזו דוגמה לפתוח?' });
    if (pick) await openAt(root, path.join(to, pick.file), 0);
}

/** דוגמאות C#: כל דוגמה היא פרויקט (Examples/<שם>) עם .csproj ו-Program.cs */
async function examplesCs(root) {
    const { warnings } = cs.setup(root, path.join(ctx.extensionPath, 'resources', 'cs', 'lib', cs.LIB));
    for (const w of warnings) vscode.window.showWarningMessage(w);
    const from = path.join(ctx.extensionPath, 'resources', 'cs', 'Examples');
    const to = path.join(root, 'Examples');
    const names = { StackExamples: 'מחסנית', QueueExamples: 'תור', ListExamples: 'רשימה מקושרת', TreeExamples: 'עצים',
                    ArrayExamples: 'מערכים (חד ודו ממדיים)', StudentExamples: 'תלמידים (Student)', CarExamples: 'מכוניות (Car)', RecursionExamples: 'רקורסיה (עצרת, מחסנית)', NestedExamples: 'מבנה בתוך מבנה (מחסנית של מחסניות...)' };
    for (const d of fs.readdirSync(from)) {
        const dest = path.join(to, d);
        if (fs.existsSync(dest)) continue;
        fs.mkdirSync(path.join(dest, 'Properties'), { recursive: true });
        for (const f of ['Program.cs']) fs.copyFileSync(path.join(from, d, f), path.join(dest, f));
        fs.writeFileSync(path.join(dest, d + '.csproj'), cs.csproj(d), 'utf8');
        fs.writeFileSync(path.join(dest, 'Properties', 'launchSettings.json'), JSON.stringify(cs.LAUNCH, null, 2) + '\n', 'utf8');
    }
    const pick = await vscode.window.showQuickPick(
        fs.readdirSync(from).map(d => ({ label: names[d] || d, description: d, dir: d })), { title: 'איזו דוגמה לפתוח?' });
    if (pick) await openAt(root, path.join(to, pick.dir, 'Program.cs'), 0);
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

/** באיזו שפה התיקייה (בלי לשאול): הגדרה → הקובץ הפתוח → מה שיש בתיקייה */
function detectLanguage(root) {
    const setting = vscode.workspace.getConfiguration('dsviz').get('language');
    if (setting === 'java' || setting === 'csharp') return setting;
    const ed = vscode.window.activeTextEditor;
    if (ed && (ed.document.languageId === 'csharp' || ed.document.languageId === 'java')) return ed.document.languageId;
    return cs.detectLanguage(root);
}

/** "שליחת תרגיל": פותח את הקבצים שנוצרו — קודם המחלקות, ובסוף הקובץ עם הסעיף הראשון, והסמן מחכה במקום הפתרון */
async function openFiles(root, files, focus, line) {
    for (const f of files) {
        if (f === focus || !fs.existsSync(f)) continue;
        await vscode.window.showTextDocument(await vscode.workspace.openTextDocument(f), { preview: false, preserveFocus: true });
    }
    if (focus && fs.existsSync(focus)) await openAt(root, focus, line);
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

module.exports = { needsHelperImport, activate, deactivate };
