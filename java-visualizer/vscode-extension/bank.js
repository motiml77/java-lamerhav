'use strict';
// "מאגר תרגילים" ב-VS Code — אותו כלי כמו ב-Visual Studio וב-IntelliJ (dsviz-bank-vs.jar: חלון המאגר, החלון הצף "השאלה", הכניסה —
// Google / קוד מורה / עמדת מחשב — ו"עמדת ניהול"), בתהליך Java נפרד, במצב --vscode. התרגיל נוצר בתיקייה הפתוחה: Java — חבילה
// ex01_... בתיקיית הקוד (כמו "שליחת תרגיל"), C# — פרויקט Exercises/Ex01_... (עם --cs). הכלי מבקש מכאן דברים דרך גשר מקומי:
//   127.0.0.1, פורט אקראי, ו-token אקראי לכל פתיחה — רק הכלי שהופעל מכאן יכול לבקש. שורה אחת: "token\tפקודה\tארגומנטים", תשובה בשורה:
//   OPEN <קובץ> <שורה>  — פותח את הקובץ בעורך, הסמן בשורה (מ-1; 0 — בהתחלה)
//   SAVE <קובץ>         — שומר את הקובץ אם יש בו שינויים שלא נשמרו (לפני "הכן סעיף ב" — שההוספה תהיה על הקוד העדכני)
//   REFRESH <תיקייה>    — VS Code רואה קבצים חדשים לבד; כאן נסגרות הלשוניות של קבצים שנמחקו ("סיימתי את התרגיל")
//   DIALOG              — "0" (ב-VS Code אין חלון דו-שיח ש"השאלה" צריך לרדת מתחתיו)
// ההצעות לפתרון נשמרות באותו קובץ של "שליחת תרגיל" (solutions.json) — ה-💡 מעל הפעולות עובד גם לתרגילים מהמאגר.
// הקובץ dsviz-bank-vs.jar מגיע עם ההתקנה (‎%LOCALAPPDATA%\DSViz\bank). בלי vscode כאן — כדי שאפשר לבדוק בלי VS Code (vscode-test.js).
const fs = require('fs');
const path = require('path');
const os = require('os');
const net = require('net');
const crypto = require('crypto');
const cp = require('child_process');

const WIN = process.platform === 'win32';
const MAIN = 'il.lamerhav.dsviz.BankVs';
const BANK_JAR = 'dsviz-bank-vs.jar';

/** תיקיית ההתקנה של DSViz: Windows ‎%LOCALAPPDATA%\DSViz‎ (או מעל ה-Java שההתקנה רשמה), Mac ‎~/Library/Application Support/DSViz‎ */
function installDirs(runner) {
    const out = [];
    if (WIN && process.env.LOCALAPPDATA) out.push(path.join(process.env.LOCALAPPDATA, 'DSViz'));
    const jdk = runner.readDsvizFile && runner.readDsvizFile('jdk-path.txt');
    if (jdk) out.push(path.dirname(jdk));
    if (runner.MAC_DIR) out.push(runner.MAC_DIR);
    return out.filter((d, i) => out.indexOf(d) === i);
}

/**
 * איפה הכלי: { dir, cp: [...] } — dir = תיקייה שממנה מריצים, cp = הנתיבים ל-classpath ביחס אליה (dsviz.jar של ההתקנה +
 * dsviz-bank-vs.jar, מאותה התקנה — אותה גרסה). נתיב יחסי: ב-Windows שורת הפקודה של java מפוענחת בקידוד של המערכת, ושם משתמש
 * בעברית (בתוך ‎%LOCALAPPDATA%‎) היה הופך ל-"?" — כמו בכפתור "מאגר שאלות" של Visual Studio. configured — ההגדרה dsviz.bankJar (בדיקות).
 * null — אין (DSViz לא מותקן במחשב, או Mac — שם אין עדיין מאגר).
 */
function findTool(runner, configured, extensionLib) {
    if (configured && fs.existsSync(configured)) return { dir: path.dirname(configured), cp: [extensionLib, path.basename(configured)] };
    for (const d of installDirs(runner)) {
        if (!fs.existsSync(path.join(d, 'dsviz.jar'))) continue;
        for (const sub of ['bank', 'lab']) {
            if (fs.existsSync(path.join(d, sub, BANK_JAR))) return { dir: d, cp: ['dsviz.jar', sub + '/' + BANK_JAR] };
        }
    }
    return null;
}

/** javaw (בלי חלון קונסול שחור ב-Windows) ליד ה-java שנמצא */
function windowless(java) {
    if (!WIN) return java;
    const w = path.join(path.dirname(java), 'javaw.exe');
    return fs.existsSync(w) ? w : java;
}

/**
 * הארגומנטים לכלי (אחרי שם המחלקה): mode = bank / question. Java: target = תיקיית הקוד, ועוד --lib (dsviz.jar של התיקייה, לקומפילציה
 * של התרגיל), --java, --out (תיקיית הפלט של ההרצה הוויזואלית — שם נמחקים ה-class של תרגיל שסיימו). C#: target = התיקייה, ו---cs.
 */
function toolArgs(o) {
    const a = [o.mode, o.lang === 'csharp' ? o.root : o.srcDir, '--vscode'];
    if (o.lang === 'csharp') a.push('--cs');
    else a.push('--lib', o.lib, '--java', o.java, '--out', o.outDir);
    a.push('--store', o.store, '--port', String(o.port), '--token', o.token, '--project', o.project);
    return a;
}

/**
 * הפקודה המלאה: { command, args, cwd, env }. הארגומנטים עוברים במשתנה הסביבה DSVIZ_ARGS (שורה לכל ארגומנט; BankVs.envArgs) — Java
 * קוראת את הסביבה ב-Unicode, כך שנתיב בעברית או עם רווחים עובר כמו שהוא. csRunner — dsviz-cs (C#), ל-DSVIZ_CS.
 */
function command(o, tool) {
    const env = Object.assign({}, process.env, { DSVIZ_ARGS: toolArgs(o).join('\n') });
    delete env.JAVA_TOOL_OPTIONS;
    if (o.csRunner) env.DSVIZ_CS = o.csRunner;
    return {
        command: windowless(o.javaExe),
        args: ['-Dfile.encoding=UTF-8', '-cp', tool.cp.join(path.delimiter), MAIN, '--args-env'],
        cwd: tool.dir,
        env
    };
}

/**
 * הגשר: שרת מקומי שמקבל שורה אחת ומחזיר תשובה. handlers: open(file, line), save(file) → Promise, refresh(dir). מחזיר
 * Promise<{ port, token, close, received }> (received — כל בקשה, לבדיקות).
 */
function startBridge(handlers) {
    const token = crypto.randomBytes(16).toString('hex');
    const received = [];
    const handle = async line => {
        const p = String(line || '').split('\t');
        if (p.length < 2 || p[0] !== token) return 'DENIED';
        const cmd = p[1];
        if (cmd !== 'DIALOG') received.push(p.slice(1).join('\t'));
        if (cmd === 'DIALOG') return '0';
        if (cmd === 'OPEN') {
            const line1 = parseInt(p[3] || '0', 10) || 0;
            // בלי לחכות: הכלי (חלון Swing) לא נתקע עד שהעורך נפתח
            Promise.resolve().then(() => handlers.open(p[2] || '', line1)).catch(() => {});
            return 'OK';
        }
        if (cmd === 'SAVE') {
            // מחכים לשמירה (עד 5 שניות) — הכלי קורא את הקובץ מיד אחר כך
            await Promise.race([Promise.resolve().then(() => handlers.save(p[2] || '')).catch(() => {}), new Promise(r => setTimeout(r, 5000))]);
            return 'OK';
        }
        if (cmd === 'REFRESH') {
            Promise.resolve().then(() => handlers.refresh(p[2] || '')).catch(() => {});
            return 'OK';
        }
        return 'UNKNOWN';
    };
    const server = net.createServer(sock => {
        let buf = '';
        sock.setEncoding('utf8');
        sock.setTimeout(15000, () => sock.destroy());
        sock.on('error', () => {});
        sock.on('data', d => {
            buf += d;
            const i = buf.indexOf('\n');
            if (i < 0) {
                if (buf.length > 65536) sock.destroy();
                return;
            }
            const line = buf.slice(0, i).replace(/\r$/, '');
            buf = '';
            handle(line).then(r => sock.end(r + '\n'), () => sock.end('ERROR\n'));
        });
    });
    return new Promise((resolve, reject) => {
        server.once('error', reject);
        server.listen(0, '127.0.0.1', () => {
            server.unref();
            resolve({ port: server.address().port, token, received, close: () => server.close() });
        });
    });
}

/** לשוניות של קבצים שכבר לא קיימים בדיסק (ובלי שינויים שלא נשמרו) — לסגירה אחרי "סיימתי את התרגיל" */
function deletedTabs(tabGroups, under) {
    const out = [];
    const base = under ? path.resolve(under) + path.sep : '';
    for (const g of (tabGroups && tabGroups.all) || []) {
        for (const t of g.tabs || []) {
            const uri = t.input && t.input.uri;
            if (!uri || uri.scheme !== 'file' || t.isDirty) continue;
            const f = path.resolve(uri.fsPath);
            if (base && !f.startsWith(base)) continue;
            if (!fs.existsSync(f)) out.push(t);
        }
    }
    return out;
}

/**
 * רושם את הפקודות dsviz.bank ("מאגר תרגילים") ו-dsviz.bankQuestion ("השאלה"). deps: vscode, ctx, chooseRoot, detectLanguage, project,
 * cs, runner, ui, store (הנתיב של solutions.json), reg (רישום פקודה שמכבדת "כיבוי DSViz").
 */
function register(deps) {
    const { vscode, ctx } = deps;
    const sessions = new Map();   // תיקייה → { bridge, proc }
    const log = path.join(os.tmpdir(), 'dsviz-bank-vscode.log');

    const handlers = {
        open: async (file, line) => {
            if (!file || !fs.existsSync(file)) return;
            const doc = await vscode.workspace.openTextDocument(file);
            const ed = await vscode.window.showTextDocument(doc, { preview: false });
            const ln = Math.max(0, Math.min(line > 0 ? line - 1 : 0, doc.lineCount - 1));
            const pos = new vscode.Position(ln, line > 0 ? doc.lineAt(ln).text.length : 0);
            ed.selection = new vscode.Selection(pos, pos);
            ed.revealRange(new vscode.Range(pos, pos), vscode.TextEditorRevealType.InCenter);
        },
        save: async file => {
            const f = path.resolve(file);
            for (const d of vscode.workspace.textDocuments || []) {
                if (d.uri && d.uri.scheme === 'file' && path.resolve(d.uri.fsPath) === f && d.isDirty) await d.save();
            }
        },
        refresh: async dir => {
            const tabs = deletedTabs(vscode.window.tabGroups, dir);
            if (tabs.length) await vscode.window.tabGroups.close(tabs, true);
        }
    };

    const fail = (msg) => { deps.ui.error(vscode, msg); return false; };

    /** פותח את הכלי במצב mode (bank / question) לתיקייה. מחזיר true אם הופעל */
    const open = async mode => {
        const root = await deps.chooseRoot();
        if (!root) return false;
        const lang = deps.detectLanguage(root);
        const settings = vscode.workspace.getConfiguration('dsviz');
        const extLib = path.join(ctx.extensionPath, 'resources', 'dsviz.jar');
        const tool = findTool(deps.runner, settings.get('bankJar'), extLib);
        if (!tool) {
            return fail(deps.runner.MAC ? 'מאגר התרגילים עוד לא זמין ב-Mac. אפשר להשתמש ב"שליחת תרגיל" וב"תרגיל חדש".'
                : 'מאגר התרגילים לא נמצא במחשב (dsviz-bank-vs.jar). ' + deps.runner.INSTALL_HINT + '.');
        }
        const jdk = deps.runner.findJava(settings.get('javaHome'));
        if (!jdk) return fail('לא נמצאה Java (JDK) במחשב. ' + deps.runner.INSTALL_HINT + ', או מגדירים dsviz.javaHome.');
        const o = { mode, lang, root, project: path.basename(root), store: deps.store(), javaExe: jdk.java };
        if (lang === 'csharp') {
            const r = deps.cs.findRunner(settings.get('csRunner'));
            if (!r) return fail('לא נמצא dsviz-cs (ההרצה של C#). מתקינים את DSViz-Setup.exe עם C#.');
            o.csRunner = r.prefix.length ? r.prefix[0] : r.command;
            const { warnings } = deps.cs.setup(root, path.join(ctx.extensionPath, 'resources', 'cs', 'lib', deps.cs.LIB));
            for (const w of warnings) deps.ui.warn(vscode, w);
        } else {
            const { srcDir, warnings } = deps.project.setup(root, extLib);
            for (const w of warnings || []) deps.ui.warn(vscode, w);
            Object.assign(o, { srcDir, lib: path.join(root, 'lib', 'dsviz.jar'), java: jdk.java, outDir: path.join(root, 'bin') });
        }
        let s = sessions.get(root);
        if (!s) {
            s = { bridge: await startBridge(handlers), proc: null };
            sessions.set(root, s);
        }
        if (s.proc && s.proc.exitCode === null) {   // חלון אחד בכל פעם: פותחים מחדש במצב שנבחר
            try { s.proc.kill(); } catch (e) { /* כבר נסגר */ }
        }
        Object.assign(o, { port: s.bridge.port, token: s.bridge.token });
        const c = command(o, tool);
        let out;
        try { out = fs.openSync(log, 'a'); } catch (e) { out = 'ignore'; }
        try {
            s.proc = cp.spawn(c.command, c.args, { cwd: c.cwd, env: c.env, stdio: ['ignore', out, out], windowsHide: true });
            s.proc.on('error', e => deps.ui.error(vscode, 'לא הצלחתי לפתוח את מאגר התרגילים: ' + e.message));
        } finally {
            if (typeof out === 'number') fs.closeSync(out);
        }
        return true;
    };

    deps.reg('dsviz.bank', () => open('bank'));
    deps.reg('dsviz.bankQuestion', () => open('question'));
    ctx.subscriptions.push({
        dispose: () => {
            for (const s of sessions.values()) {
                if (s.proc && s.proc.exitCode === null) try { s.proc.kill(); } catch (e) { /* נסגר */ }
                s.bridge.close();
            }
            sessions.clear();
        }
    });
    return { open, sessions, handlers, log };
}

module.exports = { register, startBridge, toolArgs, command, findTool, deletedTabs, installDirs, MAIN, BANK_JAR };
