'use strict';
// קוד תרגיל בשפת C# — מופק מאותו מחולל של Java (templates.js) בהמרה מכנית, כך שיש מקור אחד לתבניות.
// הבדיקות (tools/test/cs-tests.sh) מקמפלות מול הספרייה כל תרגיל וכל קטע קוד שנוצר.
const templates = require('./templates');

const CS_KEYWORDS = new Set(('abstract as base bool break byte case catch char checked class const continue decimal default delegate do ' +
    'double else enum event explicit extern false finally fixed float for foreach goto if implicit in int interface internal is lock long ' +
    'namespace new null object operator out override params private protected public readonly ref return sbyte sealed short sizeof ' +
    'stackalloc static string struct switch this throw true try typeof uint ulong unchecked unsafe ushort using virtual void volatile while').split(' '));

/** שם חוקי גם ב-C# (לא מילה שמורה) */
function isIdentifier(s) {
    return templates.isIdentifier(s) && !CS_KEYWORDS.has(s);
}

const RANDOM_JAVA = '(int) (Math.random() * 20) + 1';
const ELEMENT = { int: 'int', String: 'string', char: 'char', double: 'double' };

const cap = w => w.charAt(0).toUpperCase() + w.slice(1);

/** מחיל המרה רק על מה שמחוץ למחרוזות ("...", '...') */
function outsideStrings(s, f) {
    return s.split(/("(?:[^"\\]|\\.)*"|'(?:[^'\\]|\\.)*')/).map((part, i) => (i % 2 ? part : f(part))).join('');
}

/** שורת קוד / הערה: סוגים, שמות פעולות באות גדולה, length, מערכים דו-ממדיים */
function convertText(s, mats, arrs) {
    return outsideStrings(s, c => {
        c = c.replace(/System\.out\.println\(/g, '§WL§(')
             .replace(/Arrays\.toString\(/g, '§ATS§(')
             .replace(/Arrays\.deepToString\(/g, '§ADS§(')
             .split(RANDOM_JAVA).join('§RND§');
        // מערכים דו-ממדיים: int[][] → int[,],  mat[i][j] → mat[i, j],  mat.length → mat.GetLength(0)
        for (const m of Object.keys(mats)) {
            c = c.replace(new RegExp('\\b' + m + '\\[[^\\[\\]]+\\]\\.length', 'g'), m + '.GetLength(1)')
                 .replace(new RegExp('\\b' + m + '\\.length\\b', 'g'), m + '.GetLength(0)')
                 .replace(new RegExp('\\b' + m + '\\[([^\\[\\]]+)\\]\\[([^\\[\\]]+)\\]', 'g'), m + '[$1, $2]');
        }
        c = c.replace(/\bnew (\w+)\[([^\[\]]+)\]\[([^\[\]]+)\]/g, 'new $1[$2, $3]')
             .replace(/\[\]\[\]/g, '[,]');
        // סוגים
        c = c.replace(/\bboolean\b/g, 'bool').replace(/\bString\b/g, 'string')
             .replace(/<Integer>/g, '<int>').replace(/<Double>/g, '<double>').replace(/<Character>/g, '<char>')
             .replace(/\bmain\(/g, 'Main(')
             .replace(/\.length\b/g, '.Length');
        // שמות פעולות: .push( → .Push(
        c = c.replace(/\.([a-z]\w*)\(/g, (m, w) => '.' + cap(w) + '(');
        // טוקנים
        return c.replace(/§WL§/g, 'Console.WriteLine')
                .replace(/§RND§/g, 'Random.Shared.Next(1, 21)')
                .replace(/§ATS§\((\w+)\)/g, (m, v) => arrs[v] ? '"[" + string.Join<' + arrs[v] + '>(", ", ' + v + ') + "]"' : '"[" + string.Join(", ", ' + v + ') + "]"')
                .replace(/§ADS§\((\w+)\)/g, (m, v) => '"[" + string.Join(", ", ' + v + '.Cast<' + (mats[v] || 'int') + '>()) + "]"');
    });
}

/** Java → C#. מזהה לבד איזה משתנים הם מערכים דו-ממדיים (T[][] name) */
function toCs(java) {
    // פעולות עזר גנריות: public static <T> int size(Stack<T> s)  →  public static int Size<T>(Stack<T> s), והקריאות size(...) → Size(...)
    if (/public static <T> /.test(java)) {
        java = java.replace(/public static <T> (.+?) (size|duplicate)\(/g, (m, r, n) => `public static ${r} ${cap(n)}<T>(`)
                   .replace(/(^|[^.\w])(size|duplicate)\(/gm, (m, pre, n) => pre + cap(n) + '(');
    }
    const mats = {};
    for (const m of java.matchAll(/\b(\w+)\[\]\[\]\s+(\w+)/g)) mats[m[2]] = ELEMENT[m[1]] || m[1];
    const arrs = {};   // מערכים חד-ממדיים: שם → טיפוס האיברים (בשביל string.Join<T>)
    for (const m of java.matchAll(/\b(\w+)\[\]\s+(\w+)/g)) arrs[m[2]] = ELEMENT[m[1]] || m[1];
    // מערך של מבנים: Stack<Integer>[] arr → string.Join<Stack<int>>
    for (const m of java.matchAll(/\b(\w+)<(\w+)>\[\]\s+(\w+)/g)) {
        const t = { Integer: 'int', Double: 'double', Character: 'char', String: 'string' }[m[2]] || m[2];
        arrs[m[3]] = `${m[1]}<${t}>`;
    }
    const out = [];
    for (const rawLine of java.split('\n')) {
        let line = rawLine;
        if (/^import java\.util\.Arrays;/.test(line)) continue;
        if (/^\s*@SuppressWarnings/.test(line)) continue;
        if (/^import unit4\.collectionsLib\.\*;/.test(line)) { out.push('using System;', 'using Unit4.CollectionsLib;'); continue; }
        // מערך של מבנים: Stack<Integer>[] arr = new Stack[3];  →  new Stack<Integer>[3]
        line = line.replace(/\b(\w+)<(\w+)>\[\]\s+(\w+)\s*=\s*new \1\[/, (m, c, t, n) => `${c}<${t}>[] ${n} = new ${c}<${t}>[`);
        let code = line, comment = '';
        // הערה: מהראשון // שמחוץ למחרוזת
        const parts = line.split(/("(?:[^"\\]|\\.)*"|'(?:[^'\\]|\\.)*')/);
        let acc = '', cut = -1;
        for (let i = 0; i < parts.length; i++) {
            if (i % 2 === 0) { const k = parts[i].indexOf('//'); if (k >= 0) { cut = acc.length + k; break; } }
            acc += parts[i];
        }
        if (cut >= 0) { code = line.slice(0, cut); comment = line.slice(cut); }
        let c = convertText(code, mats, arrs);
        if (comment) {
            // שורת רמז עם שמות פעולות בלי נקודה: // Student: getName()  getAverage() ...
            if (/^\s*\/\/ (Student|Car):/.test(line) && !code.trim()) comment = comment.replace(/\b([a-z]\w*)\(/g, (m, w) => cap(w) + '(');
            c += convertText(comment, mats, arrs);
        }
        out.push(c);
    }
    let text = out.join('\n');
    if (text.includes('.Cast<') && !/using System\.Linq;/.test(text)) text = text.replace('using Unit4.CollectionsLib;', 'using System.Linq;\nusing Unit4.CollectionsLib;');
    return text;
}

/** כמו templates.generate, אבל קוד C#. מחזיר { code, cursorLine } */
function generate(o) {
    if (!isIdentifier(o.className)) throw new Error('שם המחלקה חייב להיות באנגלית, בלי רווחים, להתחיל באות, ולא להיות מילה שמורה של C#');
    if (!isIdentifier(o.functionName)) throw new Error('שם הפעולה חייב להיות באנגלית, בלי רווחים, להתחיל באות, ולא להיות מילה שמורה של C#');
    if (o.functionName === 'Main') throw new Error('Main כבר קיימת בקובץ — בחרו שם אחר לפעולה');
    if (o.functionName === o.className) throw new Error('שם הפעולה לא יכול להיות זהה לשם המחלקה');
    if ((o.helperSize && o.functionName === 'Size') || (o.helperCopy && o.functionName === 'Duplicate'))
        throw new Error('השם ' + o.functionName + ' תפוס על ידי פעולת העזר — בחרו שם אחר לפעולה');
    const g = templates.generate(o);
    const code = toCs(g.code);
    const lines = code.split('\n');
    const todo = lines.findIndex(l => l.includes('TODO:'));
    return { code, cursorLine: todo >= 0 ? todo + 1 : g.cursorLine };
}

/** קטע קוד להכנסה (בלי using — התוסף מוסיף לבד) */
function snippet(o) {
    return toCs(templates.snippet(o));
}

/** "מה יש במחלקה" ב-C#: שמות פעולות באות גדולה, string / bool */
function classInfo(name) {
    const info = templates.CLASS_INFO[name];
    if (!info) return null;
    const conv = s => String(s).replace(/\bString\b/g, 'string').replace(/\bboolean\b/g, 'bool')
        .replace(/\b([a-z]\w*)\(/g, (m, w) => cap(w) + '(');
    const deep = v => Array.isArray(v) ? v.map(deep) : typeof v === 'string' ? conv(v) : v;
    return deep(info);
}

const names = require('./names');

/** כמו templates.preview, אבל הטיפוס בכתיבה של C# (Stack<Queue<int>>, int[,]) */
function preview(structure, type, values) {
    const p = templates.preview(structure, type, values);
    p.type = toCs(p.type);
    return p;
}

/** שגיאה (בעברית) אם השם לא חוקי ב-C# או מתנגש במשהו שכבר מוגדר במקום הסמן; אחרת null */
function nameError(name, text, offset) {
    return names.checkName(name, text, offset, isIdentifier);
}

/** שם מוצע למשתנה חדש (stk, stk2... / q1, q2...) שלא מתנגש בקוד */
function suggestName(structure, text, offset) {
    return names.suggestName(templates.snippetName(structure), text, offset, isIdentifier);
}

/** כמו templates.helperInsertion, אבל הפעולות ב-C# (Size<T> / Duplicate<T>) */
function helperInsertion(text, offset, structure, size, copy) {
    const h = templates.helperInsertion(text, offset, structure, size, copy);
    return h && { offset: h.offset, code: toCs(h.code) };
}

module.exports = Object.assign({}, templates, { helperInsertion, preview, nameError, suggestName, isIdentifier, generate, snippet, classInfo, toCs, CS_KEYWORDS });
