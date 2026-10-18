'use strict';
// יוצר את קוד התרגיל (קובץ Java) לפי הבחירות של התלמיד.
// שימו לב: יש עותק זהה בג'אווה בתוסף של Eclipse (Scaffold.java) — שינוי כאן צריך להיעשות גם שם.

const STRUCTURES = {
    stack: { label: 'מחסנית', cls: 'Stack', varName: 's', build: 'stack', random: 'randomStack',
             note: 'האיבר האחרון בראש המחסנית',
             hint: '{v}.push(x)   {v}.pop()   {v}.top()   {v}.isEmpty()' },
    queue: { label: 'תור', cls: 'Queue', varName: 'q', build: 'queue', random: 'randomQueue',
             note: 'האיבר הראשון בראש התור',
             hint: '{v}.insert(x)   {v}.remove()   {v}.head()   {v}.isEmpty()' },
    list:  { label: 'רשימה מקושרת', cls: 'Node', varName: 'lst', build: 'list', random: 'randomList',
             note: 'החוליה הראשונה היא ' + 'lst',
             hint: '{v}.getValue()   {v}.getNext()   {v}.setNext(n)   {v}.hasNext()   new Node<{T}>(x)' },
    tree:  { label: 'עץ בינארי', cls: 'BinNode', varName: 't', build: 'tree', random: 'randomBst',
             note: 'לפי רמות: שורש, ילדים, נכדים... (null = אין צומת)',
             hint: '{v}.getValue()   {v}.getLeft()   {v}.getRight()   {v}.hasLeft()   {v}.hasRight()   {v}.setLeft(n)' },
    bst:   { label: 'עץ חיפוש בינארי', cls: 'BinNode', varName: 't', build: 'bst', random: 'randomBst',
             note: 'הכנסה לעץ חיפוש לפי הסדר',
             hint: '{v}.getValue()   {v}.getLeft()   {v}.getRight()   {v}.hasLeft()   {v}.hasRight()   {v}.setLeft(n)' }
};

const TYPES = {
    Integer:   { primitive: 'int',    zero: '0' },
    String:    { primitive: 'String', zero: 'null' },
    Character: { primitive: 'char',   zero: "' '" },
    Double:    { primitive: 'double', zero: '0.0' }
};

const DEFAULT_VALUES = {
    Integer:   { first: '5, 2, 8, 1, 9', second: '3, 7, 4', tree: '5, 3, 8, 1, 4, null, 9', bst: '5, 3, 8, 1, 4, 9' },
    String:    { first: 'dog, cat, bird, fish', second: 'sun, moon', tree: 'm, c, t, a, f, null, z', bst: 'm, c, t, a, f, z' },
    Character: { first: 'a, b, c, d', second: 'x, y', tree: 'm, c, t, a, f, null, z', bst: 'm, c, t, a, f, z' },
    Double:    { first: '1.5, 2.0, 3.25, 0.5', second: '4.5, 1.0', tree: '5.0, 3.5, 8.25, 1.0, 4.0, null, 9.5', bst: '5.0, 3.5, 8.25, 1.0, 4.0, 9.5' }
};

const RETURNS = ['void', 'int', 'boolean', 'element', 'structure'];

function defaultValues(structure, type, second) {
    const d = DEFAULT_VALUES[type];
    if (structure === 'tree') return d.tree;
    if (structure === 'bst') return d.bst;
    return second ? d.second : d.first;
}

function isIdentifier(s) {
    return /^[A-Za-z_][A-Za-z0-9_]*$/.test(s) && !JAVA_KEYWORDS.has(s);
}

const JAVA_KEYWORDS = new Set(('abstract assert boolean break byte case catch char class const continue default do double ' +
    'else enum extends final finally float for goto if implements import instanceof int interface long native new ' +
    'package private protected public return short static strictfp super switch synchronized this throw throws ' +
    'transient try void volatile while true false null var record').split(' '));

/** "5, 2, 8" → ["5", "2", "8"] ; מחזיר שגיאה בעברית אם ערך לא מתאים לטיפוס */
function literals(text, type, allowNull) {
    const tokens = String(text || '').split(',').map(t => t.trim()).filter(t => t.length > 0);
    return tokens.map(t => {
        if (t === 'null') {
            if (!allowNull) throw new Error('null מותר רק בעץ בינארי (לציון צומת חסר)');
            return 'null';
        }
        switch (type) {
            case 'Integer':
                if (!/^-?\d{1,9}$/.test(t)) throw new Error(`הערך "${t}" אינו מספר שלם`);
                return String(parseInt(t, 10));
            case 'Double':
                if (!/^-?(\d+\.?\d*|\.\d+)$/.test(t)) throw new Error(`הערך "${t}" אינו מספר`);
                return t.includes('.') ? t : t + '.0';
            case 'Character': {
                let c = t;
                if (c.length >= 3 && c[0] === "'" && c[c.length - 1] === "'") c = c.slice(1, -1);
                if ([...c].length !== 1) throw new Error(`הערך "${t}" אינו תו בודד`);
                return "'" + (c === "'" || c === '\\' ? '\\' + c : c) + "'";
            }
            default: {
                let s = t;
                if (s.length >= 2 && s[0] === '"' && s[s.length - 1] === '"') s = s.slice(1, -1);
                return '"' + s.replace(/\\/g, '\\\\').replace(/"/g, '\\"') + '"';
            }
        }
    });
}

/**
 * options = { structure, type, returns, second, extra, random, values, values2, className, functionName }
 * מחזיר { code, cursorLine } — cursorLine = מספר השורה (מ-0) שבה התלמיד מתחיל לכתוב
 */
function generate(o) {
    const st = STRUCTURES[o.structure];
    const tp = TYPES[o.type];
    if (!st) throw new Error('מבנה לא מוכר: ' + o.structure);
    if (!tp) throw new Error('טיפוס לא מוכר: ' + o.type);
    if (!isIdentifier(o.className)) throw new Error('שם המחלקה חייב להיות באנגלית, בלי רווחים, ולהתחיל באות');
    if (!isIdentifier(o.functionName)) throw new Error('שם הפעולה חייב להיות באנגלית, בלי רווחים, ולהתחיל באות');
    const random = !!o.random && o.type === 'Integer';
    const T = o.type;
    const full = `${st.cls}<${T}>`;
    const v1 = st.varName, v2 = st.varName + '2';

    let retType, retDefault;
    switch (o.returns) {
        case 'void': retType = 'void'; break;
        case 'int': retType = 'int'; retDefault = '0'; break;
        case 'boolean': retType = 'boolean'; retDefault = 'false'; break;
        case 'element': retType = tp.primitive; retDefault = tp.zero; break;
        case 'structure': retType = full; retDefault = 'null'; break;
        default: throw new Error('ערך החזרה לא מוכר: ' + o.returns);
    }

    const params = [`${full} ${v1}`];
    const args = [v1];
    if (o.second) { params.push(`${full} ${v2}`); args.push(v2); }
    if (o.extra) { params.push(`${tp.primitive} x`); args.push('x'); }

    const buildLine = (v, values, isSecond) => {
        if (random) {
            const n = isSecond ? 4 : 6;
            return `        ${full} ${v} = Build.${st.random}(${n}, 1, 20);   // ${n} מספרים אקראיים בין 1 ל-20`;
        }
        const lits = literals(values, T, o.structure === 'tree');
        return `        ${full} ${v} = Build.${st.build}(${lits.join(', ')});   // ${st.note.replace('lst', v)}`;
    };

    const L = [];
    L.push('import unit4.collectionsLib.*;');
    L.push('');
    L.push('/**');
    L.push(` * תרגיל: ${st.label} של ${T}`);
    L.push(` * 1. כותבים את הפעולה ${o.functionName} למטה.`);
    L.push(' * 2. מריצים (הרצה ויזואלית / Run) — ייפתח חלון שמראה כל צעד.');
    L.push(' * אפשר לשנות את הערכים ב-main כדי לבדוק מקרים נוספים.');
    L.push(' */');
    L.push(`public class ${o.className} {`);
    L.push('');
    L.push('    /**');
    L.push('     * TODO: כאן כותבים מה הפעולה צריכה לעשות');
    L.push('     */');
    L.push(`    public static ${retType} ${o.functionName}(${params.join(', ')}) {`);
    L.push(`        // תזכורת: ${st.hint.replace(/\{v\}/g, v1).replace(/\{T\}/g, T)}`);
    const cursorLine = L.length;
    L.push('        ');
    if (retDefault !== undefined) L.push(`        return ${retDefault};`);
    L.push('    }');
    L.push('');
    L.push('    public static void main(String[] args) {');
    L.push(buildLine(v1, o.values, false));
    if (o.second) L.push(buildLine(v2, o.values2, true));
    if (o.extra) L.push(`        ${tp.primitive} x = ${extraValue(o)};`);
    L.push('');
    L.push(`        System.out.println("לפני:  ${v1} = " + Viz.text(${v1}));`);
    if (o.second) L.push(`        System.out.println("לפני:  ${v2} = " + Viz.text(${v2}));`);
    const call = `${o.functionName}(${args.join(', ')})`;
    if (o.returns === 'void') {
        L.push(`        ${call};`);
    } else {
        L.push(`        ${retType} result = ${call};`);
        L.push(o.returns === 'structure'
            ? '        System.out.println("התוצאה: " + Viz.text(result));'
            : '        System.out.println("התוצאה: " + result);');
    }
    L.push(`        System.out.println("אחרי:  ${v1} = " + Viz.text(${v1}));`);
    if (o.second) L.push(`        System.out.println("אחרי:  ${v2} = " + Viz.text(${v2}));`);
    L.push('    }');
    L.push('}');
    L.push('');
    return { code: L.join('\n'), cursorLine };
}

function extraValue(o) {
    if (o.type === 'Integer') {
        if (o.random) return '8';
        const lits = literals(o.values, 'Integer', true).filter(x => x !== 'null');
        return lits.length > 2 ? lits[2] : lits.length ? lits[0] : '8';
    }
    const lits = literals(o.values, o.type, true).filter(x => x !== 'null');
    return lits.length ? lits[0] : TYPES[o.type].zero;
}

module.exports = { STRUCTURES, TYPES, RETURNS, defaultValues, isIdentifier, literals, generate };
