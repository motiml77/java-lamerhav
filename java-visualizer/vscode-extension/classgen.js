'use strict';
const fs = require('fs');
const path = require('path');
/**
 * "צור מחלקה" (בתוך "הוסף מבנה נתונים"): מחלקה בקובץ חדש — שדות private, בנאי שמקבל את כל השדות, get ו-set לכל שדה
 * (בסגנון ספריית הקורס: getValue / setValue — פעולות, לא properties), ו-toString אם מבקשים. Java ו-C#.
 *
 * עותק זהה של eclipse-plugin/src/il/lamerhav/dsviz/ClassGen.java (Eclipse, IntelliJ והכלי של Visual Studio) —
 * tools/test/classgen-tests.sh משווה את שניהם על אותם מקרים (קוד והודעות). שינוי כאן צריך להיעשות גם שם.
 */

const JAVA = 'java', CS = 'cs';
const TITLE = 'צור מחלקה';

function subtitle(lang) {
    return lang === CS ? 'כותבים שם ושדות — והמחלקה נוצרת בקובץ חדש, עם בנאי ועם Get ו-Set לכל שדה.'
        : 'כותבים שם ושדות — והמחלקה נוצרת בקובץ חדש, עם בנאי ועם get ו-set לכל שדה.';
}

/** הכיתוב של תיבת הסימון toString (עם LRM אחרי הסוגריים — שלא יתהפכו בשורה מימין לשמאל) */
const toStringLabel = lang => 'תיאור העצם כמחרוזת — ' + (lang === CS ? 'ToString()' : 'toString()') + '\u200E';
const KEYS = 'מקשים: Enter בשדה האחרון — עוד שדה · Ctrl+Enter — יצירה · Esc — סגירה';

const JAVA_KEYWORDS = new Set(('abstract assert boolean break byte case catch char class const continue default do double ' +
    'else enum extends final finally float for goto if implements import instanceof int interface long native new ' +
    'package private protected public return short static strictfp super switch synchronized this throw throws ' +
    'transient try void volatile while true false null var record').split(' '));

const CS_KEYWORDS = new Set(('abstract as base bool break byte case catch char checked class const continue decimal default delegate do ' +
    'double else enum event explicit extern false finally fixed float for foreach goto if implicit in int interface internal is lock long ' +
    'namespace new null object operator out override params private protected public readonly ref return sbyte sealed short sizeof ' +
    'stackalloc static string struct switch this throw true try typeof uint ulong unchecked unsafe ushort using virtual void volatile while').split(' '));

const JAVA_PRIMITIVES = new Set(['int', 'double', 'boolean', 'char', 'long', 'float', 'short', 'byte']);
const CS_PRIMITIVES = new Set(['int', 'double', 'bool', 'char', 'string', 'long', 'float', 'short', 'byte', 'decimal', 'object', 'uint', 'ulong', 'ushort', 'sbyte']);

/** המבנים של ספריית הקורס — שדה מהטיפוסים האלה צריך import unit4.collectionsLib.*; / using Unit4.CollectionsLib; */
const COURSE = ['Queue', 'Stack', 'Node', 'BinNode'];
const BUILTIN = ['Stack', 'Queue', 'Node', 'BinNode', 'String', 'Object', 'Math', 'System', 'Integer', 'Double', 'Character', 'Boolean', 'Console', 'Scanner'];
const LIBRARY_CLASSES = ['Student', 'Car'];

const IDENT = /^[A-Za-z_][A-Za-z0-9_]*$/;
const isCs = lang => lang === CS;
/** כמו String.trim של Java (תווים עד רווח) — כדי ששתי הגרסאות יחזירו בדיוק אותו דבר */
const jtrim = s => String(s == null ? '' : s).replace(/^[\x00-\x20]+|[\x00-\x20]+$/g, '');
const isWs = c => c === ' ' || c === '\t' || c === '\n' || c === '\r' || c === '\f' || c === '\u000B';

function isIdentifier(s, lang) {
    return typeof s === 'string' && IDENT.test(s) && !(isCs(lang) ? CS_KEYWORDS : JAVA_KEYWORDS).has(s);
}

function defaultType() { return 'int'; }

const basicTypes = lang => isCs(lang) ? ['int', 'double', 'bool', 'char', 'string'] : ['int', 'double', 'boolean', 'char', 'String'];
const elementTypes = lang => isCs(lang) ? ['int', 'double', 'bool', 'char', 'string'] : ['Integer', 'Double', 'Boolean', 'Character', 'String'];
const defaultElement = lang => isCs(lang) ? 'int' : 'Integer';
const GROUP_BASIC = 'סוגים בסיסיים', GROUP_COURSE = 'מבני נתונים של הקורס', GROUP_CLASSES = 'מחלקות שכבר קיימות בפרויקט', GROUP_ARRAYS = 'מערכים',
    NO_CLASSES = 'אין עדיין מחלקות אחרות בפרויקט';

/** מבנה של הקורס שצריך טיפוס בפנים (Queue / Stack / Node / BinNode, בלי <>) */
const isCourse = base => base != null && COURSE.includes(jtrim(base));

/** הטיפוס המלא של שורה: Queue + Student ← Queue<Student>; כל דבר אחר — כמו שנכתב */
function fullType(base, elem, lang) {
    const b = jtrim(base), e = jtrim(elem);
    return isCourse(b) ? b + '<' + (e || defaultElement(lang)) + '>' : b;
}

/** known: [{ name, pkg, file }] — ממוין לפי שם ואז package */
function sorted(known) {
    return (known || []).slice().sort((a, b) => a.name === b.name ? cmp(a.pkg, b.pkg) : cmp(a.name, b.name));
}
/** השוואה כמו String.compareTo של Java (לפי קוד התו) */
const cmp = (a, b) => (a < b ? -1 : a > b ? 1 : 0);

function classItems(known, lang, suffix) {
    return known.map(k => {
        const same = known.filter(o => o.name === k.name).length;
        const hint = same > 1 ? (k.pkg || (isCs(lang) ? '(בלי namespace)' : '(בלי package)')) : '';
        return { type: k.name + suffix, hint, pkg: k.pkg };
    });
}

/** הרשימה של טיפוס השדה: [{ title, empty, items: [{ type, hint, pkg }] }] */
function typeGroups(lang, known) {
    const k = sorted(known);
    return [
        { title: GROUP_BASIC, empty: '', items: basicTypes(lang).map(t => ({ type: t, hint: '', pkg: null })) },
        { title: GROUP_COURSE, empty: '', items: COURSE.map(t => ({ type: t, hint: '', pkg: null })) },
        { title: GROUP_CLASSES, empty: NO_CLASSES, items: classItems(k, lang, '') },
        { title: GROUP_ARRAYS, empty: '', items: (isCs(lang) ? ['int[]', 'double[]', 'string[]'] : ['int[]', 'double[]', 'String[]']).map(t => ({ type: t, hint: '', pkg: null }))
            .concat(classItems(k, lang, '[]')) }
    ];
}

/** הרשימה הקטנה (מה יש בתוך Queue / Stack / Node / BinNode) */
function elementGroups(lang, known) {
    return [
        { title: GROUP_BASIC, empty: '', items: elementTypes(lang).map(t => ({ type: t, hint: '', pkg: null })) },
        { title: GROUP_CLASSES, empty: NO_CLASSES, items: classItems(sorted(known), lang, '') }
    ];
}

const cap = s => s ? s.charAt(0).toUpperCase() + s.slice(1) : s;
const fileName = (className, lang) => className + (isCs(lang) ? '.cs' : '.java');
const getter = (field, lang) => (isCs(lang) ? 'Get' : 'get') + cap(field);
const setter = (field, lang) => (isCs(lang) ? 'Set' : 'set') + cap(field);

// ------------------------------------------------------------------ טיפוסים

function normalizeType(type, lang) {
    if (type == null) return null;
    const s = jtrim(type);
    if (!s) return null;
    const pos = { i: 0 };
    const r = parseType(s, pos, lang, false);
    skip(s, pos);
    return r === null || pos.i !== s.length ? null : r;
}

function skip(s, pos) { while (pos.i < s.length && isWs(s.charAt(pos.i))) pos.i++; }

function parseType(s, pos, lang, arg) {
    const cs = isCs(lang);
    skip(s, pos);
    let name = '';
    for (;;) {
        const re = /[A-Za-z_][A-Za-z0-9_]*/y;
        re.lastIndex = pos.i;
        const m = re.exec(s);
        if (!m) return null;
        name += m[0];
        pos.i = re.lastIndex;
        skip(s, pos);
        if (pos.i < s.length && s.charAt(pos.i) === '.') { name += '.'; pos.i++; skip(s, pos); continue; }
        break;
    }
    let n = name;
    if (n.indexOf('.') < 0) {
        n = mapName(n, lang, arg);
        const kw = cs ? CS_KEYWORDS : JAVA_KEYWORDS, prim = cs ? CS_PRIMITIVES : JAVA_PRIMITIVES;
        if (kw.has(n) && !prim.has(n)) return null;
    }
    let out = n;
    if (pos.i < s.length && s.charAt(pos.i) === '<') {
        pos.i++;
        const args = [];
        for (;;) {
            const a = parseType(s, pos, lang, true);
            if (a === null) return null;
            args.push(a);
            skip(s, pos);
            if (pos.i < s.length && s.charAt(pos.i) === ',') { pos.i++; continue; }
            if (pos.i < s.length && s.charAt(pos.i) === '>') { pos.i++; break; }
            return null;
        }
        out += '<' + args.join(', ') + '>';
        skip(s, pos);
    }
    if (cs && pos.i < s.length && s.charAt(pos.i) === '?') { out += '?'; pos.i++; skip(s, pos); }
    while (pos.i < s.length && s.charAt(pos.i) === '[') {
        pos.i++;
        skip(s, pos);
        let dims = '[';
        while (cs && pos.i < s.length && s.charAt(pos.i) === ',') { dims += ','; pos.i++; skip(s, pos); }
        if (pos.i >= s.length || s.charAt(pos.i) !== ']') return null;
        pos.i++;
        out += dims + ']';
        skip(s, pos);
    }
    return out;
}

const CS_MAP = { boolean: 'bool', Boolean: 'bool', String: 'string', Integer: 'int', Double: 'double', Character: 'char',
    Long: 'long', Float: 'float', Short: 'short', Byte: 'byte', Object: 'object' };
const JAVA_BOXED = { int: 'Integer', double: 'Double', char: 'Character', boolean: 'Boolean', long: 'Long', float: 'Float', short: 'Short', byte: 'Byte' };

function mapName(n, lang, arg) {
    if (isCs(lang)) return Object.prototype.hasOwnProperty.call(CS_MAP, n) ? CS_MAP[n] : n;
    if (n === 'string') return 'String';
    if (n === 'bool') n = 'boolean';
    if (n === 'object') return 'Object';
    if (!arg) return n;
    return Object.prototype.hasOwnProperty.call(JAVA_BOXED, n) ? JAVA_BOXED[n] : n;
}

/** השמות הפשוטים (בלי שם מלא עם נקודות) בטיפוס: Queue<Student>[] ← Queue, Student */
function simpleNames(type) {
    const out = [], re = /[A-Za-z_][\w.]*/g;
    let m;
    while ((m = re.exec(type))) if (m[0].indexOf('.') < 0 && !out.includes(m[0])) out.push(m[0]);
    return out;
}

function usesCourse(type) {
    return simpleNames(type).some(n => COURSE.includes(n));
}

// ------------------------------------------------------------------ בדיקה

/**
 * o = { lang, className, fields: [{type, name, refs?: {שם: package}}], ctor (בנאי — ברירת מחדל true), toStr (toString — ברירת מחדל false),
 *       pkg, namespace, fileScoped, taken: Map | {שם: קובץ}, known: [{name, pkg, file}] }
 *     (לא "constructor" / "toString" — לכל אובייקט ב-JS יש כאלה מהאב)
 * מחזיר { errors, warnings, className (מתוקן, או null), fields (התקינים, עם טיפוסים מתוקנים), ok }
 */
function check(o) {
    const lang = isCs(o.lang) ? CS : JAVA;
    const langName = isCs(lang) ? 'C#' : 'Java';
    const c = { errors: [], warnings: [], className: null, fields: [], imports: [], course: false };
    const known = o.known || [];
    const kw = isCs(lang) ? CS_KEYWORDS : JAVA_KEYWORDS;
    const raw = jtrim(o.className);
    if (!raw) c.errors.push('כותבים שם למחלקה');
    else if (!IDENT.test(raw)) c.errors.push('שם המחלקה חייב להיות באנגלית, בלי רווחים, ולהתחיל באות');
    else {
        const fixed = cap(raw);
        if (kw.has(raw) || kw.has(fixed)) c.errors.push(`"${kw.has(raw) ? raw : fixed}" היא מילה שמורה של ${langName} — בוחרים שם אחר למחלקה`);
        else {
            c.className = fixed;
            if (fixed !== raw) c.warnings.push('שם של מחלקה מתחיל באות גדולה — המחלקה תיקרא ' + fixed);
            if (BUILTIN.includes(fixed)) c.errors.push(`"${fixed}" הוא שם של מחלקה מוכנה — בוחרים שם אחר למחלקה`);
            else {
                let found = null, where = null;
                for (const [k, v] of takenEntries(o.taken)) if (k.toLowerCase() === fixed.toLowerCase()) { found = k; where = v; break; }
                const tgt = target(o);
                if (found === null) for (const k of known) if (k.pkg === tgt && k.name.toLowerCase() === fixed.toLowerCase()) { found = k.name; where = k.file; break; }
                if (found !== null) c.errors.push('כבר יש מחלקה בשם ' + found + (where ? ' (בקובץ ' + where + ')' : '') + ' — בוחרים שם אחר');
                else if (LIBRARY_CLASSES.includes(fixed)) c.warnings.push('בספריית הקורס כבר יש מחלקה ' + fixed + ' — בפרויקט הזה המחלקה החדשה תחליף אותה');
            }
        }
    }
    const names = new Set(), accessors = new Map();
    let filled = 0;
    for (const f of o.fields || []) {
        const name = jtrim(f.name), type = jtrim(f.type);
        if (!name) continue;
        filled++;
        let err = null;
        if (!IDENT.test(name)) err = `השדה "${name}": שם חייב להיות באנגלית, בלי רווחים, ולהתחיל באות`;
        else if (kw.has(name)) err = `השדה "${name}": זו מילה שמורה של ${langName} — בוחרים שם אחר`;
        else if (names.has(name)) err = 'יש שני שדות בשם ' + name;
        else if (c.className !== null && name === c.className) err = 'שדה לא יכול להיקרא כמו המחלקה (' + name + ')';
        else if (accessors.has(getter(name, lang)))
            err = 'השדות ' + accessors.get(getter(name, lang)) + ' ו-' + name + ' יוצרים את אותה פעולה (' + getter(name, lang) + ') — משנים שם לאחד מהם';
        let norm = null;
        if (err === null) {
            if (!type) err = 'השדה ' + name + ': חסר טיפוס';
            else if ((norm = normalizeType(type, lang)) === null)
                err = 'השדה ' + name + ': הטיפוס "' + type + '" לא תקין (למשל ' + (isCs(lang) ? 'int, string, Queue<int>' : 'int, String, Queue<Integer>') + ')';
        }
        if (err !== null) { c.errors.push(err); continue; }
        names.add(name);
        accessors.set(getter(name, lang), name);
        c.fields.push({ type: norm, name, refs: Object.assign({}, f.refs || {}) });
    }
    if (filled === 0) c.errors.push('מוסיפים לפחות שדה אחד (טיפוס ושם)');
    resolve(o, c, known);
    c.ok = c.errors.length === 0;
    return c;
}

const target = o => jtrim(isCs(o.lang) ? o.namespace : o.pkg);

/** import / using לכל מחלקה מהפרויקט שבשימוש ונמצאת בחבילה אחרת — כמו ClassGen.resolve */
function resolve(o, c, known) {
    const cs = isCs(o.lang), tgt = target(o), lines = [];
    const none = cs ? '(בלי namespace)' : '(בלי package)';
    for (const f of c.fields) {
        for (const n of simpleNames(f.type)) {
            if (COURSE.includes(n)) { c.course = true; continue; }
            const cand = known.filter(k => k.name === n);
            if (!cand.length) { if (LIBRARY_CLASSES.includes(n)) c.course = true; continue; }
            let pkg = null;
            const ref = f.refs && Object.prototype.hasOwnProperty.call(f.refs, n) ? f.refs[n] : null;
            if (ref !== null && cand.some(k => k.pkg === ref)) pkg = ref;
            if (pkg === null) {
                if (cand.some(k => k.pkg === tgt)) continue;
                pkg = cand[0].pkg;
                if (cand.length > 1) {
                    const w = 'יש כמה מחלקות בשם ' + n + ' (' + cand.map(k => k.pkg || none).join(', ') + ') — נבחרה הראשונה. אפשר לבחור אחרת מהרשימה';
                    if (!c.warnings.includes(w)) c.warnings.push(w);
                }
            }
            if (pkg === tgt) continue;
            let line;
            if (cs) {
                if (!pkg || tgt.startsWith(pkg + '.')) continue;
                line = LIBRARY_CLASSES.includes(n) ? 'using ' + n + ' = ' + pkg + '.' + n + ';' : 'using ' + pkg + ';';
            } else {
                if (!pkg) {
                    const w = 'המחלקה ' + n + ' בלי package — אי אפשר להשתמש בה מתוך package ' + tgt;
                    if (!c.warnings.includes(w)) c.warnings.push(w);
                    continue;
                }
                line = 'import ' + pkg + '.' + n + ';';
            }
            if (!lines.includes(line)) lines.push(line);
        }
    }
    lines.sort(cmp);
    c.imports.push(...lines);
}

function takenEntries(t) {
    if (!t) return [];
    if (t instanceof Map) return [...t.entries()];
    return Object.entries(t);
}

// ------------------------------------------------------------------ הקוד

function generate(o) {
    const c = check(o);
    if (!c.ok) throw new Error(c.errors[0]);
    return code(o, c.className, c);
}

function preview(o) {
    const c = check(o);
    return code(o, c.className !== null ? c.className : 'MyClass', c);
}

function code(o, name, c) {
    const cs = isCs(o.lang), fields = c.fields;
    const head = (c.course ? (cs ? 'using Unit4.CollectionsLib;\n' : 'import unit4.collectionsLib.*;\n') : '') + c.imports.map(i => i + '\n').join('')
        + (c.course || c.imports.length ? '\n' : '');
    let sb = '';
    if (cs) {
        sb += head;
        const ns = jtrim(o.namespace);
        let base = '';
        if (ns && o.fileScoped) sb += 'namespace ' + ns + ';\n\n';
        else if (ns) { sb += 'namespace ' + ns + '\n{\n'; base = '    '; }
        const inn = base + '    ';
        sb += base + 'public class ' + name + '\n' + base + '{\n';
        sb += body(name, fields, o, inn, true);
        sb += base + '}\n';
        if (base) sb += '}\n';
    } else {
        const pkg = jtrim(o.pkg);
        if (pkg) sb += 'package ' + pkg + ';\n\n';
        sb += head;
        sb += 'public class ' + name + ' {\n';
        sb += body(name, fields, o, '    ', false);
        sb += '}\n';
    }
    return sb;
}

function open(head, inn, cs) {
    return cs ? inn + head + '\n' + inn + '{\n' : inn + head + ' {\n';
}

function body(name, fields, o, inn, cs) {
    const lang = cs ? CS : JAVA, in2 = inn + '    ';
    let sb = '';
    for (const f of fields) sb += inn + 'private ' + f.type + ' ' + f.name + ';\n';
    if (o.ctor !== false && fields.length) {
        const params = fields.map(f => f.type + ' ' + f.name).join(', ');
        sb += '\n' + open('public ' + name + '(' + params + ')', inn, cs);
        for (const f of fields) sb += in2 + 'this.' + f.name + ' = ' + f.name + ';\n';
        sb += inn + '}\n';
    }
    for (const f of fields) {
        sb += '\n' + open('public ' + f.type + ' ' + getter(f.name, lang) + '()', inn, cs);
        sb += in2 + 'return this.' + f.name + ';\n' + inn + '}\n\n';
        sb += open('public void ' + setter(f.name, lang) + '(' + f.type + ' ' + f.name + ')', inn, cs);
        sb += in2 + 'this.' + f.name + ' = ' + f.name + ';\n' + inn + '}\n';
    }
    if (o.toStr === true) {
        sb += '\n' + open(cs ? 'public override string ToString()' : 'public String toString()', inn, cs);
        if (!fields.length) sb += in2 + 'return "' + name + '";\n';
        else {
            const parts = fields.map((f, i) => '"' + (i === 0 ? name + ': ' : ', ') + f.name + '=" + ' + show(f, cs));
            if (parts.length <= 3) sb += in2 + 'return ' + parts.join(' + ') + ';\n';
            else {
                sb += in2 + 'return ' + parts[0] + '\n';
                for (let i = 1; i < parts.length; i++) sb += in2 + '        + ' + parts[i] + (i === parts.length - 1 ? ';\n' : '\n');
            }
        }
        sb += inn + '}\n';
    }
    return sb;
}

function show(f, cs) {
    const t = f.type, v = 'this.' + f.name;
    if (!t.endsWith(']')) return v;
    const rank = t.length - t.replace(/\[/g, '').length;
    // string.Join<T>: במערך של מחלקות (Car[]) הקריאה בלי <T> דו-משמעית (CS0121)
    if (cs) return rank === 1 && t.endsWith('[]') ? '(' + v + ' == null ? "null" : "[" + string.Join<' + t.slice(0, -2) + '>(", ", ' + v + ') + "]")' : v;
    return (rank === 1 ? 'java.util.Arrays.toString(' : 'java.util.Arrays.deepToString(') + v + ')';
}

// ------------------------------------------------------------------ עזר: מה כבר יש בקובץ / בתיקייה

function strip(text) {
    return String(text == null ? '' : text).replace(/\/\*[\s\S]*?\*\//g, ' ').replace(/\/\/[^\n]*/g, ' ')
        .replace(/"(?:[^"\\\n]|\\.)*"/g, '""').replace(/'(?:[^'\\\n]|\\.)*'/g, "''");
}

function packageOf(src) {
    const m = /^\s*package\s+([\w.]+)\s*;/m.exec(strip(src));
    return m ? m[1] : '';
}

/** { name, fileScoped } או null */
function namespaceOf(src) {
    const m = /^\s*namespace\s+([\w.]+)\s*(;)?/m.exec(strip(src));
    return m ? { name: m[1], fileScoped: !!m[2] } : null;
}

/** שמות המחלקות ברמה העליונה של הקובץ */
function declaredTypes(src) {
    const out = [];
    for (const t of topTypes(src)) if (!out.includes(t.name)) out.push(t.name);
    return out;
}

/** המחלקות ברמה העליונה של קובץ: [{ name, pkg }] — כמו ClassGen.topTypes */
function topTypes(src) {
    const text = strip(src), pkg = packageOf(src), out = [], stack = [];
    let fileNs = '', m;
    const re = /\bnamespace\s+([\w.]+)\s*([;{])|\b(?:class|interface|enum|record|struct)\s+([A-Za-z_]\w*)|[{}]/g;
    while ((m = re.exec(text))) {
        if (m[1] !== undefined) {
            if (m[2] === ';') fileNs = m[1]; else stack.push(m[1]);
        } else if (m[3] !== undefined) {
            if (stack.every(e => e !== null)) out.push({ name: m[3], pkg: pkg || [fileNs, ...stack].filter(x => x).join('.') });
        } else if (m[0] === '{') stack.push(null);
        else if (stack.length) stack.pop();
    }
    return out;
}

const SKIP_DIRS = ['bin', 'obj', 'out', 'build', 'target', 'node_modules'];

/** כל המחלקות בפרויקט מתחת ל-root: [{ name, pkg, file }] — כמו ClassGen.scanProject */
function scanProject(root, lang) {
    const files = [];
    walk(root, isCs(lang) ? '.cs' : '.java', 8, files);
    const out = [], seen = new Set();
    for (const f of files) {
        let text;
        try { text = fs.readFileSync(f, 'utf8'); } catch (e) { continue; }
        const rel = path.relative(root, f).split(path.sep).join('/');
        for (const t of topTypes(text)) {
            const key = t.name + '|' + t.pkg;
            if (!seen.has(key)) { seen.add(key); out.push({ name: t.name, pkg: t.pkg, file: rel }); }
        }
    }
    return sorted(out);
}

function walk(dir, ext, depth, out) {
    if (depth < 0 || out.length >= 2000) return;
    let list;
    try { list = fs.readdirSync(dir, { withFileTypes: true }); } catch (e) { return; }
    list.sort((a, b) => cmp(a.name, b.name));
    for (const e of list) {
        if (out.length >= 2000) return;
        if (e.name.startsWith('.') || SKIP_DIRS.includes(e.name)) continue;
        const p = path.join(dir, e.name);
        if (e.isDirectory()) walk(p, ext, depth - 1, out);
        else if (e.name.endsWith(ext)) out.push(p);
    }
}

function classLine(text) {
    const lines = text.split('\n');
    for (let i = 0; i < lines.length; i++) if (lines[i].trim().startsWith('public class ')) return i;
    return 0;
}

/** לבדיקות: "lang|Name|ctor|toString|pkg-or-ns|file|type name{Ref=pkg};...|taken1,taken2|Known@pkg,..." */
function parseCase(line) {
    const p = line.split('|');
    const o = { lang: p[0], className: p[1], ctor: p[2] === '1', toStr: p[3] === '1', pkg: '', namespace: '', fileScoped: p[5] === '1', fields: [], taken: new Map(), known: [] };
    const ext = isCs(o.lang) ? '.cs' : '.java';
    if (isCs(o.lang)) o.namespace = p[4]; else o.pkg = p[4];
    if (p.length > 6 && p[6]) for (let f of p[6].split(';')) {
        const refs = {};
        const br = f.indexOf('{');
        if (br >= 0 && f.endsWith('}')) {
            for (const r of f.slice(br + 1, -1).split(',')) { const eq = r.indexOf('='); if (eq > 0) refs[r.slice(0, eq)] = r.slice(eq + 1); }
            f = f.slice(0, br);
        }
        const sp = f.lastIndexOf(' ');
        o.fields.push(Object.assign(sp < 0 ? { type: f, name: '' } : { type: f.slice(0, sp), name: f.slice(sp + 1) }, { refs }));
    }
    if (p.length > 7) for (const t of p[7].split(',')) if (t) o.taken.set(t, t + ext);
    if (p.length > 8) for (const k of p[8].split(',')) {
        if (!k) continue;
        const at = k.indexOf('@'), n = at < 0 ? k : k.slice(0, at), pk = at < 0 ? '' : k.slice(at + 1);
        o.known.push({ name: n, pkg: pk, file: (pk ? pk.split('.').join('/') + '/' : '') + n + ext });
    }
    return o;
}

function describe(o) {
    const c = check(o);
    let s = '';
    for (const e of c.errors) s += 'ERROR ' + e + '\n';
    for (const w of c.warnings) s += 'WARN ' + w + '\n';
    s += c.ok ? 'FILE ' + fileName(c.className, o.lang) + '\n' + generate(o) : 'PREVIEW\n' + preview(o);
    return s;
}

module.exports = { JAVA, CS, TITLE, subtitle, toStringLabel, KEYS, COURSE, isIdentifier, defaultType, basicTypes, elementTypes, defaultElement, isCourse, fullType,
    typeGroups, elementGroups, GROUP_BASIC, GROUP_COURSE, GROUP_CLASSES, GROUP_ARRAYS, NO_CLASSES, fileName, getter, setter, normalizeType, usesCourse,
    check, generate, preview, packageOf, namespaceOf, declaredTypes, topTypes, scanProject, classLine, parseCase, describe, strip };
