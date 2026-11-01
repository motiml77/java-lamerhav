'use strict';
// הרצת תוכניות Java בלי התוספים של Java ל-VS Code: מוצאים Java, מקמפלים עם javac, ובונים את פקודת ההרצה.
// בלי תלות ב-vscode — כדי שאפשר יהיה לבדוק את זה בנפרד.
const fs = require('fs');
const os = require('os');
const path = require('path');
const cp = require('child_process');

const WIN = process.platform === 'win32';
const MAC = process.platform === 'darwin';
const EXE = WIN ? '.exe' : '';
/** תיקיית ההתקנה ב-Mac (installer/mac/install.sh): Java 21, dsviz-cs, update.sh */
const MAC_DIR = MAC ? path.join(os.homedir(), 'Library', 'Application Support', 'DSViz') : null;
/** איך מתקינים (להודעות שגיאה) */
const INSTALL_HINT = MAC ? 'מריצים שוב בטרמינל את פקודת ההתקנה ל-Mac (curl ... install.sh | bash)' : 'יש להתקין מחדש את DSViz-Setup.exe';

/** קובץ שתוכנית ההתקנה כתבה ב-~/.dsviz (ב-Windows: UTF-16 עם BOM) */
function readDsvizFile(name) {
    try {
        const b = fs.readFileSync(path.join(os.homedir(), '.dsviz', name));
        const utf16 = b.length >= 2 && b[0] === 0xFF && b[1] === 0xFE;
        return (utf16 ? b.slice(2).toString('utf16le') : b.toString('utf8')).replace(/^\uFEFF/, '').trim() || null;
    } catch (e) {
        return null;
    }
}

function hasJdk(home) {
    return !!home && fs.existsSync(path.join(home, 'bin', 'java' + EXE)) && fs.existsSync(path.join(home, 'bin', 'javac' + EXE));
}

/**
 * מוצא JDK (צריך גם javac). סדר: הגדרה dsviz.javaHome → ה-Java שבא עם ההתקנה → JAVA_HOME → PATH.
 * מחזיר { java, javac, source } או null.
 */
function findJava(configured) {
    const candidates = [
        [configured, 'הגדרה dsviz.javaHome'],
        [readDsvizFile('jdk-path.txt'), 'ה-Java שהותקנה עם מבני נתונים'],
        [MAC_DIR && path.join(MAC_DIR, 'jdk'), 'ה-Java שהותקנה עם מבני נתונים'],
        [process.env.JAVA_HOME, 'JAVA_HOME']
    ];
    for (const [home, source] of candidates) {
        if (hasJdk(home)) return { java: path.join(home, 'bin', 'java' + EXE), javac: path.join(home, 'bin', 'javac' + EXE), source };
    }
    const r = cp.spawnSync('javac', ['-version'], { encoding: 'utf8', shell: WIN });
    if (r.status === 0) return { java: 'java', javac: 'javac', source: 'PATH' };
    return null;
}

/** כל קובצי ה-Java בתיקייה (בלי bin / תיקיות נסתרות) */
function javaFiles(dir) {
    const res = [];
    const walk = d => {
        for (const e of fs.readdirSync(d, { withFileTypes: true })) {
            if (e.name.startsWith('.') || e.name === 'bin' || e.name === 'out' || e.name === 'node_modules') continue;
            const p = path.join(d, e.name);
            if (e.isDirectory()) walk(p);
            else if (e.name.endsWith('.java')) res.push(p);
        }
    };
    walk(dir);
    return res;
}

/**
 * מקמפל את כל הקוד שבתיקיית הקוד ל-root/bin (עם -g — בשביל המשתנים בהרצה שורה-שורה).
 * מחזיר Promise של { ok, output, errors: [{ file, line, message, severity }] }
 */
function compile(javac, srcDir, binDir, jar) {
    fs.mkdirSync(binDir, { recursive: true });
    const files = javaFiles(srcDir);
    // קובץ ארגומנטים: נתיבים עם / ובמרכאות (javac מקבל / גם ב-Windows), כדי שרווחים ועברית בנתיב לא יפריעו
    const argfile = path.join(binDir, '.dsviz-sources.txt');
    fs.writeFileSync(argfile, files.map(f => '"' + f.split(path.sep).join('/') + '"').join('\n') + '\n', 'utf8');
    const args = ['-g', '-encoding', 'UTF-8', '-J-Dfile.encoding=UTF-8', '-J-Dstdout.encoding=UTF-8', '-J-Dstderr.encoding=UTF-8',
                  '-d', binDir, '-cp', jar, '@' + argfile];
    return new Promise(resolve => {
        const p = cp.spawn(javac, args, { shell: false });
        let out = '';
        p.stdout.on('data', d => { out += d.toString('utf8'); });
        p.stderr.on('data', d => { out += d.toString('utf8'); });
        p.on('error', e => resolve({ ok: false, output: String(e), errors: [] }));
        p.on('close', code => resolve({ ok: code === 0, output: out, errors: parseErrors(out) }));
    });
}

/** "C:\x\Main.java:12: error: ';' expected" → { file, line, message, severity } */
function parseErrors(out) {
    const res = [];
    for (const line of out.split(/\r?\n/)) {
        const m = /^(.*\.java):(\d+): (error|warning): (.*)$/.exec(line);
        if (m) res.push({ file: m[1], line: +m[2], severity: m[3], message: m[4] });
    }
    return res;
}

/**
 * הארגומנטים ל-java בהרצה ויזואלית: דרך LineByLine שבספרייה — כל שורה בקוד היא צעד בחלון
 * (בלי JDK עם jdk.jdi — LineByLine מריץ בלי שורות, ורק הפעולות על המבנים מוצגות).
 */
function runArgs(mainClass, binDir, jar) {
    const cpSep = WIN ? ';' : ':';
    return ['-Dfile.encoding=UTF-8', '-Dstdout.encoding=UTF-8', '-Dstderr.encoding=UTF-8', '-Ddsviz.visual=true']
        .concat(MAC ? ['-Xdock:name=DSViz'] : [])   // Mac: השם ב-Dock (ולא "java")
        .concat(['-cp', binDir + cpSep + jar, 'unit4.collectionsLib.LineByLine', mainClass]);
}

/**
 * ב-Windows: קובץ cmd קטן שמעביר את הטרמינל ל-UTF-8 (עברית בפלט) ומריץ את java.
 * מחזיר את הנתיב שלו.
 */
function windowsScript(binDir, java, args) {
    const q = a => /[\s"&|<>^()]/.test(a) ? '"' + a.replace(/"/g, '""') + '"' : a;
    const file = path.join(binDir, '.dsviz-run.cmd');
    fs.writeFileSync(file, ['@echo off', 'chcp 65001 >nul', [q(java)].concat(args.map(q)).join(' '), ''].join('\r\n'), 'utf8');
    return file;
}

/**
 * עדכון אוטומטי ב-Mac: פעם ביום מפעילים ברקע את update.sh של ההתקנה — הוא בודק את dsviz-mac.json ומעדכן אם יש בנייה
 * חדשה (ב-Windows העדכון הוא update.ps1, בכניסה ל-Windows — כאן לא עושים כלום). מחזיר true אם הופעל.
 */
function startMacUpdater(now) {
    if (!MAC) return false;
    try {
        const script = path.join(MAC_DIR, 'update.sh');
        if (!fs.existsSync(script)) return false;
        const stamp = path.join(os.homedir(), '.dsviz', 'update-check.txt');
        if (!now && fs.existsSync(stamp) && Date.now() - fs.statSync(stamp).mtimeMs < 20 * 3600 * 1000) return false;
        cp.spawn('/bin/bash', [script], { detached: true, stdio: 'ignore' }).unref();
        return true;
    } catch (e) {
        return false;   // לא נורא — ננסה בפעם הבאה
    }
}

module.exports = { findJava, compile, parseErrors, runArgs, windowsScript, javaFiles, readDsvizFile, startMacUpdater, WIN, MAC, MAC_DIR, INSTALL_HINT };
