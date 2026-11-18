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
 *       pkg, namespace, fileScoped, taken: Map | {שם: קובץ}, known: [{name, pkg, file, sup?, isAbstract?, isFinal?, isInterface?, fields?, ctor?, methods?}],
 *       superName ("יורשת מ-"), superPkg, isAbstract, overrides: [מפתח / שם], methods: [{ret, name, params, isAbstract, body}], overrideBodies: {שם: גוף} }
 *     (לא "constructor" / "toString" — לכל אובייקט ב-JS יש כאלה מהאב)
 * מחזיר { errors, warnings, className (מתוקן, או null), fields (התקינים, עם טיפוסים מתוקנים), ok, sup, chain, inherited, superParams, overridable,
 *         overrides, edits, methods, superToString } — כמו ClassGen.Check
 */
function check(o) {
    const lang = isCs(o.lang) ? CS : JAVA;
    const langName = isCs(lang) ? 'C#' : 'Java';
    const c = { errors: [], warnings: [], className: null, fields: [], imports: [], course: false, sup: null, chain: [], inherited: [], superParams: [],
        overridable: [], edits: [], methods: [], overrides: [], superToString: false };
    const known = (o.known || []).map(kn);
    const kw = isCs(lang) ? CS_KEYWORDS : JAVA_KEYWORDS;
    checkName(o.className, o, known, c, langName);
    inheritance(o, known, c);
    const inheritedNames = new Set();
    for (const h of c.inherited) for (const f of h.fields) inheritedNames.add(f.name);
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
        else if (inheritedNames.has(name)) err = 'השדה ' + name + ' כבר קיים במחלקה ' + ownerOf(c, name) + ' — המחלקה יורשת אותו. בוחרים שם אחר';
        else for (const p of c.superParams) if (p.name === name) err = 'השם ' + name + ' כבר משמש פרמטר בבנאי של ' + c.sup.name + ' — בוחרים שם אחר לשדה';
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
    methods(o, c, accessors);
    const plain = !jtrim(o.superName) && !c.methods.length && !o.isAbstract;
    if (filled === 0 && plain) c.errors.push('מוסיפים לפחות שדה אחד (טיפוס ושם)');
    if (c.sup !== null && c.superParams.length && o.ctor === false)
        c.warnings.push('לבנאי של ' + c.sup.name + ' יש פרמטרים — לכן נוצר בנאי שמעביר אותם (' + (isCs(lang) ? 'base(...)' : 'super(...)') + ')');
    resolve(o, c, known);
    c.ok = c.errors.length === 0;
    return c;
}

/** שמות שאסור לתת למחלקה חדשה כי הם בספריית הקורס (Unit4) — ב-C# הם מתנגשים ב-using Unit4.CollectionsLib; */
const UNIT4 = ['StackInt', 'BinTreeNode', 'BinTreeUtils', 'TreeCanvas', 'Helpers', 'VizHooks', 'AnimationSpeed',
    'List', 'Viz', 'Theme', 'Build', 'LineByLine', 'TraceViewer', 'LineAgent'];

/** מחלקה מהפרויקט עם כל השדות (גם כשנכתבה בלי models: רק name / pkg / file) */
function kn(k) {
    if (k && k.__kn) return k;
    const out = Object.assign({ sup: '', isAbstract: false, isFinal: false, isInterface: false, fields: [], ctor: null, methods: [] }, k, { pkg: (k && k.pkg) || '', file: (k && k.file) || '' });
    if (out.sup == null) out.sup = '';
    if (out.ctor === undefined) out.ctor = null;
    Object.defineProperty(out, '__kn', { value: true, enumerable: false });
    return out;
}

function checkName(rawName, o, known, c, langName) {
    const lang = isCs(o.lang) ? CS : JAVA;
    const kw = isCs(lang) ? CS_KEYWORDS : JAVA_KEYWORDS;
    const raw = jtrim(rawName);
    if (!raw) c.errors.push('כותבים שם למחלקה');
    else if (!IDENT.test(raw)) c.errors.push('שם המחלקה חייב להיות באנגלית, בלי רווחים, ולהתחיל באות');
    else {
        const fixed = cap(raw);
        if (kw.has(raw) || kw.has(fixed)) c.errors.push(`"${kw.has(raw) ? raw : fixed}" היא מילה שמורה של ${langName} — בוחרים שם אחר למחלקה`);
        else {
            c.className = fixed;
            if (fixed !== raw) c.warnings.push('שם של מחלקה מתחיל באות גדולה — המחלקה תיקרא ' + fixed);
            if (BUILTIN.includes(fixed)) c.errors.push(`"${fixed}" הוא שם של מחלקה מוכנה — בוחרים שם אחר למחלקה`);
            else if (UNIT4.includes(fixed))
                c.errors.push(`"${fixed}" הוא שם של מחלקה בספריית הקורס (Unit4) — מחלקה בשם הזה תתנגש ב-`
                    + (isCs(lang) ? 'using Unit4.CollectionsLib;' : 'import unit4.collectionsLib.*;') + ' בוחרים שם אחר למחלקה');
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
}

function findKnown(known, name, pkg, tgt) {
    let first = null, here = null;
    for (const k of known) {
        if (k.name !== name) continue;
        if (pkg != null && k.pkg === pkg) return k;
        if (first === null) first = k;
        if (here === null && k.pkg === tgt) here = k;
    }
    return here !== null ? here : first;
}

function chainOf(known, start) {
    const out = [];
    let k = start;
    while (k && !out.includes(k) && out.length < 30) {
        out.push(k);
        k = !k.sup ? null : findKnown(known, k.sup, null, k.pkg);
    }
    return out;
}

function ownerOf(c, field) {
    for (const h of c.inherited) for (const f of h.fields) if (f.name === field) return h.from;
    return '';
}

const key = (name, params) => name + '(' + params.map(p => p.type).join(',') + ')';
const hasWord = (mods, w) => (' ' + mods + ' ').includes(' ' + w + ' ');

function inheritance(o, known, c) {
    const sup = jtrim(o.superName);
    if (!sup) return;
    const cs = isCs(o.lang);
    const k = findKnown(known, sup, o.superPkg == null ? null : o.superPkg, target(o));
    if (k === null) { c.errors.push('המחלקה ' + sup + ' לא נמצאה בפרויקט — בוחרים מחלקה מהרשימה של "יורשת מ-"'); return; }
    if (c.className !== null && k.name === c.className) { c.errors.push('מחלקה לא יכולה לרשת מעצמה'); return; }
    if (k.isInterface) { c.errors.push(k.name + ' הוא ממשק (interface) — יורשים רק ממחלקה'); return; }
    if (k.isFinal) { c.errors.push('המחלקה ' + k.name + ' סגורה (' + (cs ? 'sealed' : 'final') + ') — אי אפשר לרשת ממנה'); return; }
    c.sup = k;
    c.chain.push(...chainOf(known, k));
    for (let i = c.chain.length - 1; i >= 0; i--) c.inherited.push({ from: c.chain[i].name, fields: c.chain[i].fields.slice() });
    if (k.ctor !== null) c.superParams.push(...k.ctor);
    const seen = new Set(), ts = cs ? 'ToString' : 'toString';
    for (const x of c.chain)
        for (const m of x.methods) {
            const ps = paramList(m.params), k2 = key(m.name, ps);
            if (seen.has(k2)) continue;
            seen.add(k2);
            if (hasWord(m.mods, 'static') || hasWord(m.mods, 'private')) continue;
            if (m.name === ts && !ps.length) { c.superToString = true; continue; }
            if (cs ? hasWord(m.mods, 'sealed') : hasWord(m.mods, 'final')) continue;
            const virt = m.isAbstract || hasWord(m.mods, 'virtual') || hasWord(m.mods, 'override');
            c.overridable.push({ key: k2, method: m, from: x.name, file: x.file, isAbstract: !!m.isAbstract, needsVirtual: cs && !virt });
        }
    const picks = o.overrides || [];
    for (const ov of c.overridable) {
        const picked = picks.includes(ov.key) || picks.includes(ov.method.name) || (ov.isAbstract && !o.isAbstract);
        if (!picked) continue;
        c.overrides.push(ov);
        if (ov.needsVirtual) {
            c.edits.push({ file: ov.file, className: ov.from, method: ov.method.name, params: ov.method.params });
            c.warnings.push('בקובץ ' + (ov.file || ov.from + '.cs') + ' תתווסף המילה virtual לפעולה ' + ov.method.name + '() של ' + ov.from + ' — ב-C# אפשר לדרוס רק פעולה virtual');
        }
    }
}

function methods(o, c, accessors) {
    const cs = isCs(o.lang), lang = cs ? CS : JAVA, langName = cs ? 'C#' : 'Java';
    const keys = new Set(), kw = cs ? CS_KEYWORDS : JAVA_KEYWORDS;
    for (const m of o.methods || []) {
        const raw = jtrim(m.name);
        if (!raw) continue;
        let err = null, name = raw;
        if (!IDENT.test(raw)) err = `הפעולה "${raw}": שם חייב להיות באנגלית, בלי רווחים, ולהתחיל באות`;
        else {
            if (cs) name = cap(raw);
            if (kw.has(name)) err = `הפעולה "${name}": זו מילה שמורה של ${langName} — בוחרים שם אחר`;
            else if (c.className !== null && name === c.className) err = 'פעולה לא יכולה להיקרא כמו המחלקה (' + name + ') — זה השם של הבנאי';
            else if (name.toLowerCase() === 'tostring') err = 'את ' + (cs ? 'ToString' : 'toString') + ' מסמנים בתיבת הסימון שלה';
            else if (accessors.has(name)) err = 'הפעולה ' + name + ' כבר נוצרת בשביל השדה ' + accessors.get(name);
            else for (const a of accessors.keys()) if (setter(accessors.get(a), lang) === name) err = 'הפעולה ' + name + ' כבר נוצרת בשביל השדה ' + accessors.get(a);
        }
        const ret = jtrim(m.ret);
        let nret = null;
        if (err === null) {
            if (!ret) err = 'הפעולה ' + name + ': חסר טיפוס מוחזר (void — לא מחזירה כלום)';
            else if (ret === 'void') nret = 'void';
            else if ((nret = normalizeType(ret, lang)) === null) err = 'הפעולה ' + name + ': הטיפוס המוחזר "' + ret + '" לא תקין';
        }
        const ps = [];
        if (err === null) {
            const pn = new Set();
            for (const part of splitTop(m.params == null ? '' : m.params)) {
                const t = jtrim(part);
                if (!t) continue;
                const sp = t.lastIndexOf(' ');
                const pt = sp < 0 ? '' : jtrim(t.slice(0, sp)), pname = sp < 0 ? t : t.slice(sp + 1);
                const npt = pt ? normalizeType(pt, lang) : null;
                if (!IDENT.test(pname) || kw.has(pname) || npt === null) {
                    err = 'הפעולה ' + name + ': הפרמטר "' + t + '" לא תקין (כותבים טיפוס ושם, למשל ' + (cs ? 'int steps, string word' : 'int steps, String word') + ')';
                    break;
                }
                if (pn.has(pname)) { err = 'הפעולה ' + name + ': יש שני פרמטרים בשם ' + pname; break; }
                pn.add(pname);
                ps.push({ type: npt, name: pname });
            }
        }
        if (err === null) {
            const k2 = key(name, ps);
            for (const ov of c.overridable) if (ov.key === k2) err = 'הפעולה ' + name + '() כבר קיימת ב-' + ov.from + ' — מסמנים אותה ב"פעולות לדריסה"';
            if (err === null) {
                if (keys.has(k2)) err = 'יש שתי פעולות ' + name + ' עם אותם פרמטרים';
                else keys.add(k2);
            }
            if (err === null && m.isAbstract && !o.isAbstract) err = 'הפעולה ' + name + ': פעולה מופשטת (abstract) רק במחלקה מופשטת — מסמנים "מחלקה מופשטת"';
        }
        if (err !== null) { c.errors.push(err); continue; }
        if (name !== raw) c.warnings.push('ב-C# שם של פעולה מתחיל באות גדולה — הפעולה תיקרא ' + name);
        c.methods.push({ ret: nret, name, params: ps.map(f => f.type + ' ' + f.name).join(', '), mods: '', isAbstract: !!m.isAbstract, body: m.body == null ? null : m.body });
    }
}

function splitTop(list) {
    const parts = [];
    let depth = 0, cur = '';
    for (const ch of String(list)) {
        if (ch === '<' || ch === '(' || ch === '[') depth++;
        else if (ch === '>' || ch === ')' || ch === ']') depth--;
        if (ch === ',' && depth === 0) { parts.push(cur); cur = ''; } else cur += ch;
    }
    parts.push(cur);
    return parts;
}

const PARAM_MODS = new Set(['final', 'ref', 'out', 'in', 'params', 'this', 'readonly']);

function paramList(params) {
    const out = [];
    for (const part of splitTop(params == null ? '' : params)) {
        let t = part.replace(/@\w+/g, ' ');
        const eq = t.indexOf('=');
        if (eq >= 0) t = t.slice(0, eq);
        t = jtrim(t);
        if (!t) continue;
        const m = /([A-Za-z_]\w*)\s*$/.exec(t);
        if (!m) continue;
        let type = jtrim(t.slice(0, m.index));
        for (;;) {
            const sp = type.indexOf(' ');
            if (sp > 0 && PARAM_MODS.has(type.slice(0, sp))) type = jtrim(type.slice(sp + 1));
            else break;
        }
        if (!type) continue;
        out.push({ type: tidy(type), name: m[1] });
    }
    return out;
}

const tidy = type => jtrim(type).replace(/\s+/g, ' ').replace(/ ?([<>\[\],?]) ?/g, '$1').replace(/,(?![\],])/g, ', ');

const target = o => jtrim(isCs(o.lang) ? o.namespace : o.pkg);

/** import / using לכל מחלקה מהפרויקט שבשימוש ונמצאת בחבילה אחרת — כמו ClassGen.resolve */
function resolve(o, c, known) {
    const cs = isCs(o.lang), tgt = target(o), lines = [];
    const none = cs ? '(בלי namespace)' : '(בלי package)';
    const uses = c.fields.slice();
    if (c.sup !== null) uses.push({ type: c.sup.name, name: '', refs: { [c.sup.name]: c.sup.pkg } });
    for (const m of c.methods) {
        uses.push({ type: m.ret, name: '' });
        uses.push(...paramList(m.params));
    }
    for (const f of uses) {
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
    const decl = 'public ' + (o.isAbstract ? 'abstract ' : '') + 'class ' + name + (c.sup === null ? '' : (cs ? ' : ' : ' extends ') + c.sup.name);
    let sb = '';
    if (cs) {
        sb += head;
        const ns = jtrim(o.namespace);
        let base = '';
        if (ns && o.fileScoped) sb += 'namespace ' + ns + ';\n\n';
        else if (ns) { sb += 'namespace ' + ns + '\n{\n'; base = '    '; }
        const inn = base + '    ';
        sb += base + decl + '\n' + base + '{\n';
        sb += body(name, fields, o, c, inn, true);
        sb += base + '}\n';
        if (base) sb += '}\n';
    } else {
        const pkg = jtrim(o.pkg);
        if (pkg) sb += 'package ' + pkg + ';\n\n';
        sb += head;
        sb += decl + ' {\n';
        sb += body(name, fields, o, c, '    ', false);
        sb += '}\n';
    }
    return sb;
}

function open(head, inn, cs) {
    return cs ? inn + head + '\n' + inn + '{\n' : inn + head + ' {\n';
}

/** ערך ברירת מחדל שפעולה מחזירה (String — שם המחלקה) — כמו ClassGen.defaultValue */
function defaultValue(type, cls) {
    switch (jtrim(type)) {
        case 'int': case 'long': case 'short': case 'byte': case 'float': case 'double': case 'decimal': return '0';
        case 'boolean': case 'bool': return 'false';
        case 'char': return "' '";
        case 'String': case 'string': return '"' + cls + '"';
        default: return 'null';
    }
}

const args = params => params.map(f => f.name).join(', ');
/** שורה ריקה בין חברים — אבל לא מיד אחרי ה-{ של המחלקה (כאן body מתחיל ריק) */
const gap = sb => sb === '' ? sb : sb + '\n';
const lines = (bodyText, in2) => bodyText.split('\n').map(l => (l ? in2 + l : '') + '\n').join('');

function body(name, fields, o, c, inn, cs) {
    const lang = cs ? CS : JAVA, in2 = inn + '    ';
    let sb = '';
    for (const f of fields) sb += inn + 'private ' + f.type + ' ' + f.name + ';\n';
    const sp = c.superParams;
    if ((o.ctor !== false || sp.length) && (fields.length || sp.length)) {
        const params = sp.concat(fields).map(f => f.type + ' ' + f.name).join(', ');
        if (fields.length) sb += '\n';
        sb += open('public ' + name + '(' + params + ')' + (cs && sp.length ? ' : base(' + args(sp) + ')' : ''), inn, cs);
        if (!cs && sp.length) sb += in2 + 'super(' + args(sp) + ');\n';
        for (const f of fields) sb += in2 + 'this.' + f.name + ' = ' + f.name + ';\n';
        sb += inn + '}\n';
    }
    for (const f of fields) {
        sb += '\n' + open('public ' + f.type + ' ' + getter(f.name, lang) + '()', inn, cs);
        sb += in2 + 'return this.' + f.name + ';\n' + inn + '}\n\n';
        sb += open('public void ' + setter(f.name, lang) + '(' + f.type + ' ' + f.name + ')', inn, cs);
        sb += in2 + 'this.' + f.name + ' = ' + f.name + ';\n' + inn + '}\n';
    }
    for (const m of c.methods) {
        sb = gap(sb);
        if (m.isAbstract) { sb += inn + 'public abstract ' + m.ret + ' ' + m.name + '(' + m.params + ');\n'; continue; }
        sb += open('public ' + (cs ? 'virtual ' : '') + m.ret + ' ' + m.name + '(' + m.params + ')', inn, cs);
        if (m.body != null) sb += lines(m.body, in2);
        else if (m.ret !== 'void') sb += in2 + 'return ' + defaultValue(m.ret, name) + ';\n';
        sb += inn + '}\n';
    }
    const bodies = o.overrideBodies || {};
    for (const ov of c.overrides) {
        const m = ov.method, ps = paramList(m.params);
        const pt = ps.map(f => f.type + ' ' + f.name).join(', ');
        const access = hasWord(m.mods, 'protected') ? 'protected' : 'public', ret = tidy(m.ret);
        sb = gap(sb);
        if (!cs) sb += inn + '@Override\n';
        sb += open(access + ' ' + (cs ? 'override ' : '') + ret + ' ' + m.name + '(' + pt + ')', inn, cs);
        const lk = m.name.toLowerCase();
        const given = Object.prototype.hasOwnProperty.call(bodies, lk) ? bodies[lk] : null;
        if (given !== null && given !== undefined) sb += lines(given, in2);
        else if (ov.isAbstract) { if (ret !== 'void') sb += in2 + 'return ' + defaultValue(ret, name) + ';\n'; }
        else sb += in2 + (ret === 'void' ? '' : 'return ') + (cs ? 'base.' : 'super.') + m.name + '(' + args(ps) + ');\n';
        sb += inn + '}\n';
    }
    if (o.toStr === true) {
        sb = gap(sb);
        if (!cs && c.sup !== null) sb += inn + '@Override\n';
        sb += open(cs ? 'public override string ToString()' : 'public String toString()', inn, cs);
        const start = c.superToString ? (cs ? 'base.ToString()' : 'super.toString()') : null;
        if (!fields.length) sb += in2 + 'return ' + (start !== null ? start : '"' + name + '"') + ';\n';
        else {
            const parts = start !== null ? [start] : [];
            fields.forEach((f, i) => parts.push('"' + (i === 0 && start === null ? name + ': ' : ', ') + f.name + '=" + ' + show(f, cs)));
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

/**
 * המחלקות ברמה העליונה של קובץ, עם מה שצריך בשביל הורשה — כמו ClassGen.models:
 * [{ name, pkg, file, sup, isAbstract, isFinal, isInterface, fields: [{type, name}], ctor: [{type, name}] | null, methods: [{ret, name, params, mods, isAbstract}] }]
 */
function models(src, file) {
    const text = strip(src), pkg = packageOf(src), out = [], stack = [];
    let fileNs = '', m;
    const re = /\bnamespace\s+([\w.]+)\s*([;{])|\b(class|interface|enum|record|struct)\s+([A-Za-z_]\w*)|[{}]/g;
    while ((m = re.exec(text))) {
        if (m[1] !== undefined) {
            if (m[2] === ';') fileNs = m[1]; else stack.push(m[1]);
        } else if (m[4] !== undefined) {
            if (!stack.every(e => e !== null)) continue;
            const k = kn({ name: m[4], pkg: pkg || [fileNs, ...stack].filter(x => x).join('.'), file: file || '' });
            k.fields = [];
            k.methods = [];
            out.push(k);
            const kind = m[3];
            k.isInterface = kind === 'interface';
            let ms = m.index;
            while (ms > 0 && ';{}'.indexOf(text.charAt(ms - 1)) < 0) ms--;
            const mn = jtrim(text.slice(ms, m.index).replace(/\[[^\]]*\]/g, ' ').replace(/@\w+/g, ' ')).replace(/\s+/g, ' ');
            k.isAbstract = hasWord(mn, 'abstract');
            k.isFinal = hasWord(mn, 'final') || hasWord(mn, 'sealed') || hasWord(mn, 'static') || kind === 'enum' || kind === 'struct' || kind === 'record';
            const end = m.index + m[0].length;
            const open = text.indexOf('{', end), semi = text.indexOf(';', end);
            if (open < 0 || (semi >= 0 && semi < open)) continue;
            const head = text.slice(end, open);
            const ex = /\bextends\s+([A-Za-z_][\w.]*)/.exec(head);
            const co = /^\s*(?:<[^>]*>)?\s*:\s*([A-Za-z_][\w.]*)/.exec(head);
            let sup = '', colon = false;
            if (ex) sup = ex[1];
            else if (co) { sup = co[1]; colon = true; }
            if (sup.indexOf('.') >= 0) sup = sup.slice(sup.lastIndexOf('.') + 1);
            if (k.isInterface || sup === 'Object' || sup === 'object' || (colon && /^I[A-Z]\w*$/.test(sup))) sup = '';
            k.sup = sup;
            const close = matching(text, open);
            members(k, text.slice(open + 1, close));
        } else if (m[0] === '{') stack.push(null);
        else if (stack.length) stack.pop();
    }
    return out;
}

function matching(text, open) {
    let depth = 0;
    for (let i = open; i < text.length; i++) {
        const ch = text.charAt(i);
        if (ch === '{') depth++;
        else if (ch === '}' && --depth === 0) return i;
    }
    return text.length;
}

const MEMBER_MODS = new Set(('public private protected internal static abstract final virtual override '
    + 'sealed synchronized native new extern async unsafe default strictfp partial readonly transient volatile const').split(' '));

function members(k, body) {
    let seg = 0, paren = 0;
    for (let i = 0; i < body.length; i++) {
        const ch = body.charAt(i);
        if (ch === '(') paren++;
        else if (ch === ')') paren = Math.max(0, paren - 1);
        else if (paren === 0 && ch === ';') {
            member(k, body.slice(seg, i), false);
            seg = i + 1;
        } else if (paren === 0 && ch === '{') {
            const head = body.slice(seg, i);
            const end = matching(body, i);
            if (topEquals(head)) { i = end; continue; }
            member(k, head, true);
            i = end;
            seg = i + 1;
        }
    }
}

const topEquals = head => topEq(head) >= 0;

function topEq(head) {
    let paren = 0;
    for (let i = 0; i < head.length; i++) {
        const ch = head.charAt(i);
        if (ch === '(') paren++;
        else if (ch === ')') paren--;
        else if (ch === '=' && paren === 0 && (i + 1 >= head.length || (head.charAt(i + 1) !== '>' && head.charAt(i + 1) !== '='))
            && (i === 0 || '=<>!'.indexOf(head.charAt(i - 1)) < 0)) return i;
    }
    return -1;
}

function member(k, raw, hasBody) {
    let h = jtrim(raw.replace(/@\w+(?:\s*\([^)]*\))?/g, ' ').replace(/^\s*(?:\[[^\]]*\]\s*)+/, ' ')).replace(/\s+/g, ' ');
    if (!h || /\b(?:class|interface|enum|record|struct|delegate|event|operator)\b/.test(h)) return;
    const arrow = h.indexOf('=>');
    if (arrow >= 0) { h = jtrim(h.slice(0, arrow)); hasBody = true; }
    const mods = [];
    for (;;) {
        const sp = h.indexOf(' ');
        const w = sp < 0 ? h : h.slice(0, sp);
        if (!MEMBER_MODS.has(w)) break;
        mods.push(w);
        h = sp < 0 ? '' : h.slice(sp + 1);
    }
    const modText = mods.join(' ');
    const p = h.indexOf('('), eq0 = topEq(h);
    if (p >= 0 && (eq0 < 0 || p < eq0)) {
        let depth = 0, close = -1;
        for (let i = p; i < h.length; i++) {
            if (h.charAt(i) === '(') depth++;
            else if (h.charAt(i) === ')' && --depth === 0) { close = i; break; }
        }
        if (close < 0) return;
        const before = jtrim(h.slice(0, p)), params = jtrim(h.slice(p + 1, close));
        const nm = /([A-Za-z_]\w*)$/.exec(before);
        if (!nm) return;
        const name = nm[1];
        let ret = jtrim(before.slice(0, nm.index));
        if (ret.startsWith('<')) ret = jtrim(ret.slice(ret.indexOf('>') + 1));
        if (!ret) {
            if (name !== k.name || mods.includes('private') || mods.includes('static')) return;
            const ps = paramList(params);
            if (k.ctor === null || ps.length > k.ctor.length) k.ctor = ps;
            return;
        }
        k.methods.push({ ret: tidy(ret), name, params: tidyParams(params), mods: modText, body: null,
            isAbstract: mods.includes('abstract') || (k.isInterface && !hasBody && !mods.includes('static') && !mods.includes('default')) });
        return;
    }
    if (mods.includes('static') || mods.includes('const')) return;
    const parts = splitTop(h);
    let part0 = parts[0];
    if (part0.indexOf('=') >= 0) part0 = part0.slice(0, part0.indexOf('='));
    const first = /^(.*?)\s*([A-Za-z_]\w*)\s*((?:\[\s*\])*)$/.exec(jtrim(part0));
    if (!first || !jtrim(first[1])) return;
    k.fields.push({ type: tidy(first[1] + first[3]), name: first[2] });
    if (hasBody) return;
    for (let i = 1; i < parts.length; i++) {
        let more = jtrim(parts[i]);
        const e2 = more.indexOf('=');
        if (e2 >= 0) more = jtrim(more.slice(0, e2));
        if (IDENT.test(more)) k.fields.push({ type: tidy(first[1]), name: more });
    }
}

const tidyParams = params => paramList(params).map(f => f.type + ' ' + f.name).join(', ');

/** ב-C#: מוסיף virtual לפעולה במחלקה className — כמו ClassGen.addVirtual */
function addVirtual(source, className, method, params) {
    const cls = new RegExp('\\bclass\\s+' + className + '\\b').exec(source);
    let from = cls ? cls.index + cls[0].length : 0;
    const n = paramList(params).length;
    const re = new RegExp('^([ \\t]*)((?:(?:public|protected|internal|new|virtual|override|static|abstract|sealed)\\s+)+)([A-Za-z_][\\w<>\\[\\], .?]*?\\s+' + method + '\\s*\\(([^)]*)\\))', 'gm');
    for (;;) {
        re.lastIndex = from;
        const m = re.exec(source);
        if (!m) break;
        const mods = ' ' + jtrim(m[2]).replace(/\s+/g, ' ') + ' ';
        if (paramList(m[4]).length === n && !mods.includes(' virtual ') && !mods.includes(' override ') && !mods.includes(' static ') && !mods.includes(' abstract ')) {
            const at = m.index + m[1].length + m[2].length;
            return source.slice(0, at) + 'virtual ' + source.slice(at);
        }
        from = m.index + m[0].length;
    }
    return source;
}

// ------------------------------------------------------------------ צבעים — כמו בהרצה הוויזואלית (Poly)

const INK = [0xC0392B, 0x7B3FB0, 0x0C8583, 0x1F6FB5, 0x1F8A4C, 0xC56A12, 0xB02E78];
const BG = [0xFDEEED, 0xF4ECFB, 0xE4F6F5, 0xEAF2FB, 0xE8F6EE, 0xFDF1E4, 0xFBEBF4];
const GRAY = 0x55607A, GRAY_BG = 0xFAFBFD;

/** { שם: מספר צבע (-1 — לא בעץ) } — כמו ClassGen.colors (Map ששומר את הסדר) */
function colors(knownIn) {
    const known = (knownIn || []).map(kn), order = [];
    for (const k of known) define(known, k, order, []);
    // כמו Poly.recolor: אחרי כל מחלקה שמוגדרת — שורשים ואז ילדים, וצבע שכבר ניתן לא משתנה
    const out = new Map(), defined = [];
    let next = 0;
    for (const d of order) {
        defined.push(d);
        const queue = [];
        for (const c of defined) if (!c.isInterface && inTree(defined, c) && (!c.sup || byName(defined, c.sup) === null)) queue.push(c);
        for (let i = 0; i < queue.length; i++)
            for (const k of defined) if (queue[i].name === k.sup && !queue.includes(k) && !k.isInterface) queue.push(k);
        for (const c of queue) if (!out.has(c.name)) out.set(c.name, next++);
    }
    const all = new Map();
    for (const k of order) all.set(k.name, out.has(k.name) ? out.get(k.name) : -1);
    return all;
}

function define(known, k, order, visiting) {
    if (!k || byName(order, k.name) !== null || visiting.includes(k)) return;
    visiting.push(k);
    if (k.sup) define(known, byName(known, k.sup), order, visiting);
    if (byName(order, k.name) === null) order.push(k);
}

function byName(list, name) {
    for (const k of list) if (k.name === name) return k;
    return null;
}

function inTree(all, c) {
    if (c.sup && byName(all, c.sup) !== null) return true;
    return all.some(k => c.name === k.sup && !k.isInterface);
}

const ink = color => color < 0 ? GRAY : INK[color % INK.length];
const bg = color => color < 0 ? GRAY_BG : BG[color % BG.length];
const dashed = color => color >= INK.length;
const hex = rgb => '#' + rgb.toString(16).padStart(6, '0');

function superCandidates(known) {
    return sorted((known || []).map(kn)).filter(k => !k.isInterface && !k.isFinal);
}

function colorsWith(known, name, sup) {
    const all = (known || []).map(kn);
    if (sup && name) all.push(kn({ name, pkg: sup.pkg, file: '', sup: sup.name }));
    return colors(sorted(all));
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
        for (const t of models(text, rel)) {
            const k = t.name + '|' + t.pkg;
            if (!seen.has(k)) { seen.add(k); out.push(t); }
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

// ------------------------------------------------------------------ היררכיה מוכנה / עץ משלכם — כמו ClassGen.trees / treeFiles

const TREES_TITLE = 'היררכיה מוכנה', OBJECT_TITLE = 'הוסף עצם', OWN_TREE = 'עץ משלכם';

function treeClass(name, sup) {
    const c = { name: name == null ? '' : name, sup: sup == null ? '' : sup, isAbstract: false, toStr: false, fields: [], methods: [], overrides: new Map() };
    c.field = (type, n) => { c.fields.push({ type, name: n }); return c; };
    c.method = (ret, n, b, abs) => { c.methods.push({ ret, name: n, params: '', body: b, isAbstract: abs }); return c; };
    c.override = (n, b) => { c.overrides.set(n.toLowerCase(), b); return c; };
    return c;
}

function tree(id, title, about) {
    const t = { id, title, about, main: null, array: null, root: null, print: null, classes: [], objects: [] };
    t.add = (name, sup) => { const c = treeClass(name, sup); t.classes.push(c); return c; };
    return t;
}

function trees() {
    const out = [];
    const a = tree('animals', 'בעלי חיים', 'Animal ← Mammal ← Dog ← Boxer, Labrador (ובתוך Labrador — Person). כל אחת דורסת את say()\u200E');
    a.add('Animal', '').field('String', 'name').field('String', 'gender').method('String', 'say', 'return "I am an Animal";', false);
    a.add('Mammal', 'Animal').field('double', 'milk').override('say', 'return "I am a Mammal";');
    a.add('Dog', 'Mammal').field('int', 'bonesToEat').override('say', 'return "I am a Dog";');
    a.add('Boxer', 'Dog').field('Object', 'guard').override('say', 'return "I am a Boxer";');
    a.add('Person', '').field('String', 'name');
    a.add('Labrador', 'Dog').field('Person', 'person');
    Object.assign(a, { main: 'ZooDemo', array: 'zoo', root: 'Animal', print: '{a}.{m:say}()' });
    a.objects.push(['Animal', '"Bob"', '"male"'], ['Mammal', '"Numa"', '"female"', '2.5'], ['Dog', '"Dasty"', '"female"', '0.5', '3'],
        ['Boxer', '"Rexy"', '"male"', '3.2', '3', '"house"'], ['Labrador', '"Toto"', '"male"', '2.5', '4', 'new Person("Hila")']);
    out.push(a);

    const s = tree('shapes', 'צורות', 'Shape (מופשטת) ← Circle, Rectangle, Triangle. כל צורה מחשבת את השטח שלה — area()\u200E');
    const shape = s.add('Shape', '').field('String', 'color').method('double', 'area', null, true);
    shape.isAbstract = true;
    shape.toStr = true;
    s.add('Circle', 'Shape').field('double', 'radius').override('area', 'return 3.14 * this.radius * this.radius;').toStr = true;
    s.add('Rectangle', 'Shape').field('double', 'width').field('double', 'height').override('area', 'return this.width * this.height;').toStr = true;
    s.add('Triangle', 'Shape').field('double', 'side').field('double', 'height').override('area', 'return this.side * this.height / 2;').toStr = true;
    Object.assign(s, { main: 'ShapesDemo', array: 'shapes', root: 'Shape', print: '{a} + ", area=" + {a}.{m:area}()' });
    s.objects.push(['Circle', '"red"', '2'], ['Rectangle', '"blue"', '3', '4'], ['Triangle', '"green"', '6', '2']);
    out.push(s);

    const p = tree('school', 'אנשים בבית הספר', 'Person ← Pupil, Teacher ← HomeroomTeacher. כל אחד דורס את role()\u200E');
    p.add('Person', '').field('String', 'name').field('int', 'age').method('String', 'role', 'return "Person";', false).toStr = true;
    p.add('Pupil', 'Person').field('int', 'grade').override('role', 'return "Pupil";').toStr = true;
    p.add('Teacher', 'Person').field('String', 'subject').override('role', 'return "Teacher";').toStr = true;
    p.add('HomeroomTeacher', 'Teacher').field('String', 'classroom').override('role', 'return "Homeroom teacher";').toStr = true;
    Object.assign(p, { main: 'SchoolDemo', array: 'people', root: 'Person', print: '{a}.{m:role}() + ": " + {a}' });
    p.objects.push(['Person', '"Dana"', '40'], ['Pupil', '"Noa"', '16', '11'], ['Teacher', '"Avi"', '45', '"Math"'], ['HomeroomTeacher', '"Rina"', '50', '"CS"', '"11-2"']);
    out.push(p);
    return out;
}

/** "String name; int age" → [{type, name}] */
function parseFields(text) {
    const out = [];
    for (const part of splitTop(String(text == null ? '' : text).replace(/;/g, ','))) {
        const t = jtrim(part);
        if (!t) continue;
        const sp = t.lastIndexOf(' ');
        out.push(sp < 0 ? { type: t, name: '' } : { type: jtrim(t.slice(0, sp)), name: t.slice(sp + 1) });
    }
    return out;
}

/** "עץ משלכם": rows = [{name, sup, fields}] → היררכיה; errors — מערך שמתמלא בשגיאות */
function ownTree(rows, knownIn, errors) {
    const known = (knownIn || []).map(kn);
    const t = tree('own', OWN_TREE, '');
    const filled = (rows || []).filter(r => jtrim(r.name) || jtrim(r.fields) || jtrim(r.sup));
    if (!filled.length) { errors.push('כותבים לפחות מחלקה אחת בטבלה'); return t; }
    const byNameMap = new Map();
    for (const r of filled) {
        const n = cap(jtrim(r.name));
        if (!n) { errors.push('יש שורה בלי שם מחלקה'); continue; }
        for (const o of byNameMap.keys()) if (o.toLowerCase() === n.toLowerCase()) errors.push('יש שתי מחלקות בשם ' + n);
        byNameMap.set(n, r);
    }
    const done = [];
    let progress = true;
    while (done.length < byNameMap.size && progress) {
        progress = false;
        for (const [n, r] of byNameMap) {
            if (done.includes(n)) continue;
            let sup = cap(jtrim(r.sup));
            if (sup && byNameMap.has(sup) && !done.includes(sup)) continue;
            if (sup && !byNameMap.has(sup) && findKnown(known, sup, null, '') === null) {
                errors.push(n + ' יורשת מ-' + sup + ', אבל אין מחלקה ' + sup + ' (לא בטבלה ולא בפרויקט)');
                sup = '';
            }
            const c = t.add(n, sup);
            c.toStr = true;
            c.fields.push(...parseFields(r.fields));
            done.push(n);
            progress = true;
        }
    }
    if (done.length < byNameMap.size) {
        const rest = [...byNameMap.keys()].filter(n => !done.includes(n));
        errors.push('יש מעגל בירושה: ' + rest.join(', ') + ' — מחלקה לא יכולה לרשת (גם לא דרך אחרות) מעצמה');
    }
    if (!t.classes.length) return t;
    let root = t.classes[0].name;
    for (const c of t.classes) if (!c.sup || !byNameMap.has(c.sup)) { root = c.name; break; }
    Object.assign(t, { root, main: root + 'Demo', array: 'items', print: '{a}' });
    return t;
}

/** כל הקבצים של היררכיה — כמו ClassGen.treeFiles. base = { lang, pkg, namespace, fileScoped, taken, known } */
function treeFiles(t, base, withMain, run) {
    const r = { errors: [], warnings: [], files: [], known: [], edits: [], ok: false };
    const cs = isCs(base.lang), lang = cs ? CS : JAVA;
    const known = (base.known || []).map(kn);
    const taken = new Map(takenEntries(base.taken));
    for (const tc of t.classes) {
        const o = { lang, className: tc.name, pkg: base.pkg || '', namespace: base.namespace || '', fileScoped: !!base.fileScoped, taken, known,
            toStr: !!tc.toStr, superName: tc.sup, isAbstract: !!tc.isAbstract, fields: tc.fields.map(f => ({ type: f.type, name: f.name })),
            methods: tc.methods.map(m => ({ ret: m.ret, name: m.name, params: m.params, body: m.body, isAbstract: m.isAbstract })), overrides: [], overrideBodies: {} };
        const named = [];
        for (const [k, v] of tc.overrides) {
            named.push(cs ? cap(k) : k);
            if (v !== null && v !== undefined && v !== '') o.overrideBodies[k] = v;
        }
        const pre = check(o);
        for (const ov of pre.overridable) for (const n of named) if (ov.method.name.toLowerCase() === n.toLowerCase()) o.overrides.push(ov.key);
        const c = check(o);
        const label = c.className !== null ? c.className : (jtrim(tc.name) || '(בלי שם)');
        for (const e of c.errors) r.errors.push(label + ': ' + e);
        for (const w of c.warnings) if (!w.startsWith('שם של מחלקה מתחיל') && !w.startsWith('ב-C# שם של פעולה')) r.warnings.push(label + ': ' + w);
        if (!c.ok) {
            known.push(kn({ name: label, pkg: target(o), file: '', sup: tc.sup }));
            continue;
        }
        const src = generate(o), file = fileName(c.className, lang);
        r.files.push([file, src]);
        r.edits.push(...c.edits);
        known.push(...models(src, file));
        taken.set(c.className, file);
    }
    if (withMain && t.main && t.root) {
        const mc = { errors: [], warnings: [], className: null };
        const mo = { lang, pkg: base.pkg || '', namespace: base.namespace || '', taken, known };
        checkName(t.main, mo, known, mc, cs ? 'C#' : 'Java');
        for (const e of mc.errors) r.errors.push(t.main + ': ' + e);
        if (!mc.errors.length) r.files.push([fileName(t.main, lang), mainCode(t, base, known, run)]);
    }
    r.known.push(...known);
    r.ok = r.errors.length === 0;
    return r;
}

function mainCode(t, base, known, run) {
    const cs = isCs(base.lang);
    const objs = t.objects.slice();
    if (!objs.length) {
        for (const k of objectClasses(known, t.root)) {
            const ps = ctorParams(known, k.name);
            const o = [k.name];
            ps.forEach((p, i) => o.push(sample(p.type, k.name, objs.length + i, cs)));
            objs.push(o);
        }
    }
    const a = t.array, inn = '        ', in2 = inn + '    ';
    let b = inn + t.root + '[] ' + a + ' = new ' + t.root + '[' + objs.length + '];\n';
    objs.forEach((o, i) => { b += inn + a + '[' + i + '] = new ' + o[0] + '(' + o.slice(1).join(', ') + ');\n'; });
    const print = t.print.split('{a}').join(a + '[i]').replace(/\{m:(\w+)\}/g, (x, n) => cs ? cap(n) : n);
    if (cs) b += inn + 'for (int i = 0; i < ' + a + '.Length; i++)\n' + inn + '{\n' + in2 + 'System.Console.WriteLine(' + print + ');\n' + inn + '}\n';
    else b += inn + 'for (int i = 0; i < ' + a + '.length; i++) {\n' + in2 + 'System.out.println(' + print + ');\n' + inn + '}\n';
    let sb = '';
    if (cs) {
        const ns = jtrim(base.namespace);
        let pre = '';
        if (ns && base.fileScoped) sb += 'namespace ' + ns + ';\n\n';
        else if (ns) { sb += 'namespace ' + ns + '\n{\n'; pre = '    '; }
        sb += pre + 'public static class ' + t.main + '\n' + pre + '{\n';
        sb += pre + '    public static void ' + (run ? 'Run' : 'Main') + '()\n' + pre + '    {\n';
        for (const l of b.split('\n').filter((x, i, arr) => i < arr.length - 1 || x)) sb += pre + l + '\n';
        sb += pre + '    }\n' + pre + '}\n';
        if (pre) sb += '}\n';
    } else {
        const pkg = jtrim(base.pkg);
        if (pkg) sb += 'package ' + pkg + ';\n\n';
        sb += 'public class ' + t.main + ' {\n    public static void main(String[] args) {\n' + b + '    }\n}\n';
    }
    return sb;
}

function sample(type, cls, n, cs) {
    switch (type) {
        case 'int': case 'long': case 'short': case 'byte': return String(n + 1);
        case 'double': case 'float': case 'decimal': return (n + 1) + '.5' + (type === 'float' ? 'f' : type === 'decimal' ? 'm' : '');
        case 'boolean': case 'bool': return 'true';
        case 'char': return "'a'";
        case 'String': case 'string': return '"' + cls + (n + 1) + '"';
        default: return 'null';
    }
}

/** לציור העץ: [[שם, עומק, מספר צבע, מופשטת]] — כמו ClassGen.treeLayout */
function treeLayout(t, projectKnown) {
    const pk = (projectKnown || []).map(kn);
    const all = t.classes.map(c => kn({ name: cap(jtrim(c.name)), pkg: '', file: '', sup: c.sup, isAbstract: !!c.isAbstract }));
    const roots = [];
    for (const k of all)
        if (k.sup && byName(all, k.sup) === null) {
            const p = findKnown(pk, k.sup, null, '');
            if (p !== null) for (const x of chainOf(pk, p)) if (byName(roots, x.name) === null && byName(all, x.name) === null) roots.unshift(x);
        }
    const list = roots.concat(all);
    const col = colors(sorted(list));
    const out = [], placed = [];
    const place = (k, depth) => {
        if (placed.includes(k) || depth > 20) return;
        placed.push(k);
        const c = col.get(k.name);
        out.push([k.name, depth, c === undefined ? -1 : c, k.isAbstract]);
        for (const x of list) if (k.name === x.sup) place(x, depth + 1);
    };
    for (const k of list) if (!k.sup || byName(list, k.sup) === null) place(k, 0);
    for (const k of list) if (!placed.includes(k)) place(k, 0);
    return out;
}

// ------------------------------------------------------------------ הוסף עצם — כמו ClassGen.objectCode

function objectClasses(knownIn, type) {
    const known = (knownIn || []).map(kn), queue = [];
    const start = findKnown(known, type, null, '');
    if (start === null) return [];
    queue.push(start);
    const all = sorted(known);
    for (let i = 0; i < queue.length && queue.length < 200; i++)
        for (const k of all) if (queue[i].name === k.sup && !queue.includes(k)) queue.push(k);
    return queue.filter(k => !k.isAbstract && !k.isInterface);
}

function ctorParams(knownIn, cls) {
    const k = findKnown((knownIn || []).map(kn), cls, null, '');
    return k === null || k.ctor === null ? [] : k.ctor;
}

function paramOwner(knownIn, cls, param) {
    const known = (knownIn || []).map(kn);
    const k = findKnown(known, cls, null, '');
    if (k === null) return cls;
    for (const x of chainOf(known, k)) for (const f of x.fields) if (f.name === param) return x.name;
    return k.name;
}

/** מפצל ערכים בפסיקים שלא בתוך מרכאות / סוגריים — כמו ClassGen.splitArgs */
function splitArgs(text) {
    const out = [], s = text == null ? '' : String(text);
    if (!jtrim(s)) return out;
    let depth = 0, q = '', cur = '';
    for (let i = 0; i < s.length; i++) {
        const ch = s.charAt(i);
        if (q) {
            if (ch === '\\' && i + 1 < s.length) { cur += ch + s.charAt(++i); continue; }
            if (ch === q) q = '';
        } else if (ch === '"' || ch === "'") q = ch;
        else if (ch === '(' || ch === '[' || ch === '{') depth++;
        else if (ch === ')' || ch === ']' || ch === '}') depth--;
        else if (ch === ',' && depth === 0) { out.push(jtrim(cur)); cur = ''; continue; }
        cur += ch;
    }
    out.push(jtrim(cur));
    return out;
}

const quote = t => '"' + t.replace(/\\/g, '\\\\').replace(/"/g, '\\"') + '"';

function literal(type, text, lang) {
    const t = jtrim(text), ty = jtrim(type);
    const str = ty === 'String' || ty === 'string';
    if (!t) return defaultValue(ty, '');
    if (str) return t === 'null' || (t.length >= 2 && t.startsWith('"') && t.endsWith('"')) ? t : quote(t);
    if (ty === 'char') return t.length === 1 ? "'" + (t === "'" || t === '\\' ? '\\' + t : t) + "'" : t;
    if (ty === 'float' && /^-?\d+(\.\d+)?$/.test(t)) return t + 'f';
    if (ty === 'Object' || ty === 'object') {
        const raw = /^-?\d+(\.\d+)?$/.test(t) || t === 'true' || t === 'false' || t === 'null' || t.startsWith('new ') || t.startsWith('"') || t.startsWith("'");
        return raw ? t : quote(t);
    }
    return t;
}

/** o = { lang, varType, varName, cls, args: [], array, size, cells: [[מחלקה, ערך...]], indent } → { errors, code, ok } */
function objectCode(o, knownIn) {
    const known = (knownIn || []).map(kn);
    const r = { errors: [], code: '', ok: false };
    const lang = isCs(o.lang) ? CS : JAVA;
    const type = jtrim(o.varType), name = jtrim(o.varName);
    if (!type) r.errors.push('בוחרים את טיפוס המשתנה');
    else if (findKnown(known, type, null, '') === null) r.errors.push('אין בפרויקט מחלקה ' + type);
    if (!name) r.errors.push('כותבים שם למשתנה');
    else if (!isIdentifier(name, lang)) r.errors.push('שם המשתנה חייב להיות באנגלית, בלי רווחים, להתחיל באות, ולא להיות מילה שמורה');
    const allowed = objectClasses(known, type);
    const inn = o.indent || '';
    let sb = '';
    if (!o.array) {
        const cls = jtrim(o.cls);
        if (!r.errors.length && !cls) r.errors.push('בוחרים את המחלקה של העצם');
        else if (!r.errors.length && byName(allowed, cls) === null)
            r.errors.push(cls + ' לא יכולה להיכנס למשתנה מסוג ' + type + ' — רק ' + type + ' או מחלקה שיורשת ממנה (ולא מופשטת)');
        sb += inn + type + ' ' + name + ' = new ' + (cls || type) + '(' + argList(known, cls || type, o.args || [], lang) + ');';
    } else {
        const size = o.size == null ? 4 : o.size;
        if (size < 1 || size > 30) r.errors.push('גודל המערך — בין 1 ל-30');
        const n = Math.max(1, Math.min(30, size));
        sb += inn + type + '[] ' + name + ' = new ' + type + '[' + n + '];';
        const cells = o.cells || [];
        for (let i = 0; i < n && i < cells.length; i++) {
            const cell = cells[i];
            const cls = cell.length ? jtrim(cell[0]) : '';
            if (!cls) continue;
            if (byName(allowed, cls) === null) { r.errors.push('בתא ' + i + ': ' + cls + ' לא יכולה להיכנס למערך של ' + type); continue; }
            sb += '\n' + inn + name + '[' + i + '] = new ' + cls + '(' + argList(known, cls, cell.slice(1), lang) + ');';
        }
    }
    r.code = sb;
    r.ok = r.errors.length === 0;
    return r;
}

function argList(known, cls, a, lang) {
    return ctorParams(known, cls).map((p, i) => literal(p.type, i < a.length ? a[i] : '', lang)).join(', ');
}

/**
 * לבדיקות: "lang|Name|ctor|toString|pkg-or-ns|file|type name{Ref=pkg};...|taken1,taken2|Known@pkg,...", ועם הורשה עוד עמודות:
 * "|מחלקת על|abstract|דריסות|פעולות חדשות|קובץ קוד (יחסית ל-baseDir)" — כמו ClassGen.parseCase
 */
function parseCase(line, baseDir) {
    const p = line.split('|');
    const o = { lang: p[0], className: p[1], ctor: p[2] === '1', toStr: p[3] === '1', pkg: '', namespace: '', fileScoped: p[5] === '1', fields: [], taken: new Map(), known: [],
        superName: '', isAbstract: false, overrides: [], methods: [] };
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
    if (baseDir === undefined) return o;
    if (p.length > 9) o.superName = p[9];
    if (p.length > 10) o.isAbstract = p[10] === '1';
    if (p.length > 11) for (const n of p[11].split(',')) if (jtrim(n)) o.overrides.push(jtrim(n));
    if (p.length > 12 && p[12]) for (const m of p[12].split(';')) {
        let t = jtrim(m);
        const abs = t.startsWith('!');
        if (abs) t = jtrim(t.slice(1));
        const par = t.indexOf('('), sp = par < 0 ? -1 : t.lastIndexOf(' ', par);
        const me = par < 0 ? { ret: '', name: t, params: '' }
            : { ret: sp < 0 ? '' : t.slice(0, sp), name: t.slice(sp + 1, par), params: t.slice(par + 1, t.lastIndexOf(')') < par ? t.length : t.lastIndexOf(')')) };
        me.isAbstract = abs;
        o.methods.push(me);
    }
    if (p.length > 13 && p[13] && baseDir) {
        const f = path.join(baseDir, p[13]);
        o.known.push(...models(fs.readFileSync(f, 'utf8'), path.basename(f)));
    }
    return o;
}

function modelText(k0) {
    const k = kn(k0);
    let s = k.name + (k.sup ? ' : ' + k.sup : '') + (k.isAbstract ? ' abstract' : '') + (k.isFinal ? ' final' : '') + (k.isInterface ? ' interface' : '') + ' |';
    for (const f of k.fields) s += ' ' + f.type + ' ' + f.name + ';';
    s += ' | ctor(' + (k.ctor === null ? '-' : k.ctor.map(f => f.type + ' ' + f.name).join(', ')) + ') |';
    for (const m of k.methods) s += ' ' + (m.mods ? '[' + m.mods + '] ' : '') + m.ret + ' ' + m.name + '(' + m.params + ')' + (m.isAbstract ? '!' : '') + ';';
    return s;
}

function describe(o) {
    const c = check(o);
    let s = '';
    for (const e of c.errors) s += 'ERROR ' + e + '\n';
    for (const w of c.warnings) s += 'WARN ' + w + '\n';
    if (c.sup !== null) {
        s += 'SUPER ' + c.sup.name + ' ctor(' + args(c.superParams) + ')' + (c.superToString ? ' toString' : '') + '\n';
        for (const h of c.inherited) s += 'INHERITED ' + h.from + ':' + h.fields.map(f => ' ' + f.type + ' ' + f.name).join('') + '\n';
        for (const ov of c.overridable)
            s += 'OVERRIDABLE ' + ov.key + ' @' + ov.from + (ov.isAbstract ? ' abstract' : '') + (ov.needsVirtual ? ' +virtual' : '') + (c.overrides.includes(ov) ? ' *' : '') + '\n';
        s += 'COLORS';
        for (const [n, v] of colorsWith(o.known, c.className, c.sup)) if (v >= 0) s += ' ' + n + '=' + hex(ink(v));
        s += '\n';
    }
    s += c.ok ? 'FILE ' + fileName(c.className, o.lang) + '\n' + generate(o) : 'PREVIEW\n' + preview(o);
    return s;
}

module.exports = { JAVA, CS, TITLE, subtitle, toStringLabel, KEYS, COURSE, UNIT4, isIdentifier, defaultType, basicTypes, elementTypes, defaultElement, isCourse, fullType,
    typeGroups, elementGroups, GROUP_BASIC, GROUP_COURSE, GROUP_CLASSES, GROUP_ARRAYS, NO_CLASSES, fileName, getter, setter, normalizeType, usesCourse,
    check, generate, preview, packageOf, namespaceOf, declaredTypes, topTypes, scanProject, classLine, parseCase, describe, strip,
    models, modelText, addVirtual, paramList, key, chainOf, findKnown, kn, defaultValue, literal,
    INK, BG, GRAY, GRAY_BG, colors, colorsWith, ink, bg, dashed, hex, superCandidates,
    TREES_TITLE, OBJECT_TITLE, OWN_TREE, trees, ownTree, parseFields, treeFiles, treeLayout, objectClasses, ctorParams, paramOwner, objectCode, splitArgs };
