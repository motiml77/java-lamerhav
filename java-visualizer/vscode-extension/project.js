'use strict';
// הכנת תיקיית העבודה: ספריית dsviz.jar, הגדרות Java, ומיקום קובצי התרגילים.
// בלי תלות ב-vscode — כדי שאפשר יהיה לבדוק את זה בנפרד.
const fs = require('fs');
const path = require('path');

const JAR_NAME = 'dsviz.jar';
const MARKER = Buffer.from('unit4/collectionsLib/Stack.class');

/** תיקיית הקוד: לפי .classpath של Eclipse אם יש, אחרת src */
function sourceDir(root) {
    const cp = path.join(root, '.classpath');
    if (fs.existsSync(cp)) {
        const m = /<classpathentry[^>]*kind="src"[^>]*path="([^"]+)"/.exec(fs.readFileSync(cp, 'utf8'))
              || /<classpathentry[^>]*path="([^"]+)"[^>]*kind="src"/.exec(fs.readFileSync(cp, 'utf8'));
        if (m && !m[1].startsWith('/')) return path.join(root, m[1]);
    }
    try {
        const s = JSON.parse(fs.readFileSync(path.join(root, '.vscode', 'settings.json'), 'utf8'));
        const sp = s['java.project.sourcePaths'];
        if (Array.isArray(sp) && sp.length && typeof sp[0] === 'string') return path.join(root, sp[0]);
    } catch (e) { /* אין הגדרות — ברירת מחדל */ }
    return path.join(root, 'src');
}

/** ספריות unit4 אחרות (למשל ה-jar המקורי של הבגרות) שיתנגשו עם המעטפת */
function findConflicts(root) {
    const res = [];
    const libDir = path.join(root, 'lib');
    if (!fs.existsSync(libDir)) return res;
    for (const f of fs.readdirSync(libDir)) {
        if (!f.toLowerCase().endsWith('.jar') || f === JAR_NAME) continue;
        try {
            if (fs.readFileSync(path.join(libDir, f)).includes(MARKER)) res.push(path.join(libDir, f));
        } catch (e) { /* לא קריא — מתעלמים */ }
    }
    return res;
}

/**
 * מכין את התיקייה. מחזיר { srcDir, warnings[] }.
 * jarSource = הנתיב ל-dsviz.jar שבתוך התוסף
 */
function setup(root, jarSource) {
    const warnings = [];
    const srcDir = sourceDir(root);
    fs.mkdirSync(srcDir, { recursive: true });

    const libDir = path.join(root, 'lib');
    fs.mkdirSync(libDir, { recursive: true });
    const jarTarget = path.join(libDir, JAR_NAME);
    const fresh = fs.readFileSync(jarSource);
    if (!fs.existsSync(jarTarget) || !fs.readFileSync(jarTarget).equals(fresh)) fs.writeFileSync(jarTarget, fresh);

    const cp = path.join(root, '.classpath');
    if (fs.existsSync(cp)) {
        // פרויקט Eclipse — VS Code קורא את .classpath, אז מוסיפים שם את הספרייה
        let xml = fs.readFileSync(cp, 'utf8');
        if (!xml.includes('lib/' + JAR_NAME)) {
            xml = xml.replace('</classpath>',
                `\t<classpathentry kind="lib" path="lib/${JAR_NAME}" sourcepath="lib/${JAR_NAME}" rootpath="src"/>\n</classpath>`);
            fs.writeFileSync(cp, xml);
        }
    } else if (fs.existsSync(path.join(root, 'pom.xml')) || fs.existsSync(path.join(root, 'build.gradle'))) {
        warnings.push('זה פרויקט Maven/Gradle — יש להוסיף ידנית את lib/' + JAR_NAME + ' לתלויות.');
    } else {
        const settingsFile = path.join(root, '.vscode', 'settings.json');
        let settings = {};
        let ok = true;
        if (fs.existsSync(settingsFile)) {
            try { settings = JSON.parse(fs.readFileSync(settingsFile, 'utf8')); }
            catch (e) { ok = false; warnings.push('לא הצלחתי לקרוא את .vscode/settings.json (יש בו הערות?). ודאו שיש בו: "java.project.referencedLibraries": ["lib/**/*.jar"]'); }
        }
        if (ok) {
            let changed = false;
            if (!settings['java.project.sourcePaths']) {
                settings['java.project.sourcePaths'] = [path.relative(root, srcDir).split(path.sep).join('/') || '.'];
                changed = true;
            }
            const libs = settings['java.project.referencedLibraries'];
            if (Array.isArray(libs)) {
                if (!libs.some(l => l === 'lib/**/*.jar' || l === 'lib/' + JAR_NAME)) { libs.push('lib/' + JAR_NAME); changed = true; }
            } else if (libs === undefined) {
                settings['java.project.referencedLibraries'] = ['lib/**/*.jar'];
                changed = true;
            }
            if (!settings['java.project.outputPath']) { settings['java.project.outputPath'] = 'bin'; changed = true; }
            if (!settings['files.encoding']) { settings['files.encoding'] = 'utf8'; changed = true; }
            if (changed) {
                fs.mkdirSync(path.dirname(settingsFile), { recursive: true });
                fs.writeFileSync(settingsFile, JSON.stringify(settings, null, 4) + '\n');
            }
        }
    }
    return { srcDir, warnings };
}

/** Targil1, Targil2 ... — השם הפנוי הבא */
function nextClassName(srcDir, prefix) {
    for (let i = 1; ; i++) {
        if (!fs.existsSync(path.join(srcDir, prefix + i + '.java'))) return prefix + i;
    }
}

/**
 * ה-jar שמשמש כ-javaagent להרצה שורה-שורה: עדיף זה שבפרויקט (lib/dsviz.jar, מעודכן לגרסת התוסף),
 * כדי שלא יהיו שתי גרסאות שונות של הספרייה באותה הרצה.
 */
function agentJar(root, jarSource) {
    const local = path.join(root, 'lib', JAR_NAME);
    if (!fs.existsSync(local)) return jarSource;
    const fresh = fs.readFileSync(jarSource);
    if (!fs.readFileSync(local).equals(fresh)) fs.writeFileSync(local, fresh);
    return local;
}

/** תיקיית התרגילים שתוכנית ההתקנה הכינה (~/.dsviz/project-path.txt), או null */
function installedProject() {
    try {
        const b = fs.readFileSync(path.join(require('os').homedir(), '.dsviz', 'project-path.txt'));
        const utf16 = b.length >= 2 && b[0] === 0xFF && b[1] === 0xFE;
        const p = (utf16 ? b.slice(2).toString('utf16le') : b.toString('utf8')).replace(/^\uFEFF/, '').trim();
        return fs.existsSync(path.join(p, 'src')) ? p : null;
    } catch (e) {
        return null;
    }
}

/** שם המחלקה המלא (עם package) של קובץ Java */
function mainClassOf(filePath, text) {
    const m = /^\s*package\s+([\w.]+)\s*;/m.exec(text);
    const cls = path.basename(filePath, '.java');
    return m ? m[1] + '.' + cls : cls;
}

module.exports = { JAR_NAME, sourceDir, findConflicts, setup, nextClassName, mainClassOf, agentJar, installedProject };
