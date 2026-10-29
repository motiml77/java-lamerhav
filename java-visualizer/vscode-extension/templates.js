'use strict';
const names = require('./names');
// יוצר את קוד התרגיל (קובץ Java) לפי הבחירות של התלמיד.
// שימו לב: יש עותק זהה בג'אווה בתוסף של Eclipse (Scaffold.java) — שינוי כאן צריך להיעשות גם שם.

const STRUCTURES = {
    stack: { label: 'מחסנית', cls: 'Stack', varName: 'stk', build: 'stack', random: 'randomStack',
             note: 'האיבר האחרון בראש המחסנית',
             hint: '{v}.push(x)   {v}.pop()   {v}.top()   {v}.isEmpty()' },
    queue: { label: 'תור', cls: 'Queue', varName: 'q1', build: 'queue', random: 'randomQueue',
             note: 'האיבר הראשון בראש התור',
             hint: '{v}.insert(x)   {v}.remove()   {v}.head()   {v}.isEmpty()' },
    list:  { label: 'רשימה מקושרת', cls: 'Node', varName: 'lst', build: 'list', random: 'randomList',
             note: 'החוליה הראשונה היא {v}',
             hint: '{v}.getValue()   {v}.getNext()   {v}.setNext(n)   {v}.hasNext()   new Node<{T}>(x)' },
    tree:  { label: 'עץ בינארי', cls: 'BinNode', varName: 't', build: 'tree', random: 'randomBst',
             note: 'לפי רמות: שורש, ילדים, נכדים... (null = אין צומת)',
             hint: '{v}.getValue()   {v}.getLeft()   {v}.getRight()   {v}.hasLeft()   {v}.hasRight()   {v}.setLeft(n)' },
    bst:   { label: 'עץ חיפוש בינארי', cls: 'BinNode', varName: 't', build: 'bst', random: 'randomBst',
             note: 'הכנסה לעץ חיפוש לפי הסדר',
             hint: '{v}.getValue()   {v}.getLeft()   {v}.getRight()   {v}.hasLeft()   {v}.hasRight()   {v}.setLeft(n)' },
    array: { label: 'מערך', cls: null, dims: 1, varName: 'arr', build: 'array', random: 'randomArray',
             note: 'מערך — מוצג בחלון',
             hint: '{v}.length   {v}[i]   for (int i = 0; i < {v}.length; i++)' },
    matrix: { label: 'מערך דו-ממדי', cls: null, dims: 2, varName: 'mat', build: 'matrix', random: 'randomMatrix',
             note: '{v}[שורה][עמודה]',
             hint: '{v}.length (שורות)   {v}[0].length (עמודות)   {v}[i][j]' }
};

// ------------------------------------------------------------ קומבינציות: מבנה בתוך מבנה
// מחסנית של מחסניות, תור של מחסניות, רשימה מקושרת של תורים, עץ בינארי של מחסניות, מערך של תורים... (5 חיצוניים × 3 פנימיים)
const COMBO_OUTERS = ['stack', 'queue', 'list', 'tree', 'array'];
const COMBO_INNERS = ['stack', 'queue', 'list'];
const INNER_PLURAL = { stack: 'מחסניות', queue: 'תורים', list: 'רשימות מקושרות' };
for (const out of COMBO_OUTERS) {
    for (const inn of COMBO_INNERS) {
        const o = STRUCTURES[out];
        STRUCTURES[out + '_' + inn] = {
            label: `${o.label} של ${INNER_PLURAL[inn]}`, cls: o.cls, dims: o.dims, varName: o.varName,
            combo: { outer: out, inner: inn },
            note: 'כל איבר הוא מבנה פנימי — הוא מוצג בתוך התא של המבנה החיצוני',
            hint: o.hint
        };
    }
}

const TYPES = {
    Integer:   { primitive: 'int',    zero: '0' },
    String:    { primitive: 'String', zero: '""' },
    Character: { primitive: 'char',   zero: "' '" },
    Double:    { primitive: 'double', zero: '0.0' },
    Student:   { primitive: 'Student', zero: 'null' },
    Car:       { primitive: 'Car',     zero: 'null' }
};

const DEFAULT_VALUES = {
    Integer:   { first: '5, 2, 8, 1, 9', second: '3, 7, 4', tree: '5, 3, 8, 1, 4, null, 9', bst: '5, 3, 8, 1, 4, 9' },
    String:    { first: 'dog, cat, bird, fish', second: 'sun, moon', tree: 'm, c, t, a, f, null, z', bst: 'm, c, t, a, f, z' },
    Character: { first: 'a, b, c, d', second: 'x, y', tree: 'm, c, t, a, f, null, z', bst: 'm, c, t, a, f, z' },
    Double:    { first: '1.5, 2.0, 3.25, 0.5', second: '4.5, 1.0', tree: '5.0, 3.5, 8.25, 1.0, 4.0, null, 9.5', bst: '5.0, 3.5, 8.25, 1.0, 4.0, 9.5' },
    Student:   { first: '4', second: '3', tree: '5', bst: '5' },
    Car:       { first: '4', second: '3', tree: '5', bst: '5' }
};

const DEFAULT_MATRIX = {
    Integer: '1, 2, 3; 4, 5, 6; 7, 8, 9', String: 'a, b; c, d', Character: 'a, b, c; d, e, f', Double: '1.5, 2.0; 3.5, 4.0'
};

const DEFAULT_COMBO = {
    Integer: '5, 2; 8, 1, 9; 3, 7', String: 'dog, cat; bird; fish, sun, moon', Character: 'a, b; c; d, e', Double: '1.5, 2.0; 3.25; 0.5, 4.5'
};

const RETURNS = ['void', 'int', 'boolean', 'element', 'structure'];

function defaultValues(structure, type, second) {
    if (STRUCTURES[structure].combo) return DEFAULT_COMBO[type] || '';
    const d = DEFAULT_VALUES[type];
    if (structure === 'matrix') return DEFAULT_MATRIX[type] || '';
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

/** טיפוסים של אובייקטים מוכנים (Student, Car): כותבים כמה ליצור — הם נוצרים בהרצה עם נתוני דמה */
const OBJECTS = {
    Student: { fresh: 'Student.random()', note: 'תלמיד עם נתוני דמה' },
    Car:     { fresh: 'Car.random()',     note: 'מכונית עם נתוני דמה' }
};
const isObj = T => Object.prototype.hasOwnProperty.call(OBJECTS, T);

/** כמה אובייקטים ליצור (מספר) */
function studentCount(text) {
    const t = String(text || '').trim();
    if (!/^\d{1,2}$/.test(t) || +t < 1 || +t > 20) throw new Error('כאן כותבים כמה ליצור — מספר בין 1 ל-20');
    return +t;
}

/** "1, 2; 3, 4" → [["1","2"],["3","4"]] */
function matrixLiterals(text, type) {
    if (isObj(type)) throw new Error('אין מערך דו-ממדי של תלמידים או מכוניות — בחרו מערך רגיל');
    const rows = String(text || '').split(';').map(r => r.trim()).filter(r => r.length > 0).map(r => literals(r, type, false));
    if (rows.some(r => r.length === 0)) throw new Error('שורה ריקה במערך הדו-ממדי');
    return rows;
}

/** "5, 2, 8" → ["5", "2", "8"] ; מחזיר שגיאה בעברית אם ערך לא מתאים לטיפוס */
function literals(text, type, allowNull) {
    if (isObj(type)) { studentCount(text); return []; }
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
// ------------------------------------------------------------ פעולות עזר (size / duplicate) — תיבות V בחלון
// אותן פעולות בדיוק ב-Scaffold.java (Eclipse); ב-C# הן מומרות (Size<T> / Duplicate<T>)
const HELPERS = {
    "Stack.size": [
        "    // פעולת עזר: מחזירה כמה איברים יש במחסנית — המחסנית נשארת כמו שהייתה",
        "    public static <T> int size(Stack<T> s) {",
        "        Stack<T> temp = new Stack<T>();",
        "        int count = 0;",
        "        while (!s.isEmpty()) {",
        "            temp.push(s.pop());",
        "            count++;",
        "        }",
        "        while (!temp.isEmpty()) {",
        "            s.push(temp.pop());",
        "        }",
        "        return count;",
        "    }",
    ],
    "Stack.duplicate": [
        "    // פעולת עזר: מחזירה עותק של המחסנית (באותו סדר) — המחסנית המקורית נשארת כמו שהייתה",
        "    public static <T> Stack<T> duplicate(Stack<T> s) {",
        "        Stack<T> temp = new Stack<T>();",
        "        Stack<T> copy = new Stack<T>();",
        "        while (!s.isEmpty()) {",
        "            temp.push(s.pop());",
        "        }",
        "        while (!temp.isEmpty()) {",
        "            T x = temp.pop();",
        "            s.push(x);",
        "            copy.push(x);",
        "        }",
        "        return copy;",
        "    }",
    ],
    "Queue.size": [
        "    // פעולת עזר: מחזירה כמה איברים יש בתור — התור נשאר כמו שהיה",
        "    public static <T> int size(Queue<T> q) {",
        "        Queue<T> temp = new Queue<T>();",
        "        int count = 0;",
        "        while (!q.isEmpty()) {",
        "            temp.insert(q.remove());",
        "            count++;",
        "        }",
        "        while (!temp.isEmpty()) {",
        "            q.insert(temp.remove());",
        "        }",
        "        return count;",
        "    }",
    ],
    "Queue.duplicate": [
        "    // פעולת עזר: מחזירה עותק של התור (באותו סדר) — התור המקורי נשאר כמו שהיה",
        "    public static <T> Queue<T> duplicate(Queue<T> q) {",
        "        Queue<T> temp = new Queue<T>();",
        "        Queue<T> copy = new Queue<T>();",
        "        while (!q.isEmpty()) {",
        "            T x = q.remove();",
        "            temp.insert(x);",
        "            copy.insert(x);",
        "        }",
        "        while (!temp.isEmpty()) {",
        "            q.insert(temp.remove());",
        "        }",
        "        return copy;",
        "    }",
    ],
    "Node.size": [
        "    // פעולת עזר: מחזירה כמה חוליות יש ברשימה — הרשימה נשארת כמו שהייתה",
        "    public static <T> int size(Node<T> lst) {",
        "        int count = 0;",
        "        Node<T> p = lst;",
        "        while (p != null) {",
        "            count++;",
        "            p = p.getNext();",
        "        }",
        "        return count;",
        "    }",
    ],
    "Node.duplicate": [
        "    // פעולת עזר: מחזירה עותק של הרשימה (חוליות חדשות, באותו סדר) — הרשימה המקורית נשארת כמו שהייתה",
        "    public static <T> Node<T> duplicate(Node<T> lst) {",
        "        if (lst == null) {",
        "            return null;",
        "        }",
        "        Node<T> first = new Node<T>(lst.getValue());",
        "        Node<T> last = first;",
        "        Node<T> p = lst.getNext();",
        "        while (p != null) {",
        "            last.setNext(new Node<T>(p.getValue()));",
        "            last = last.getNext();",
        "            p = p.getNext();",
        "        }",
        "        return first;",
        "    }",
    ],
    "BinNode.size": [
        "    // פעולת עזר: מחזירה כמה צמתים יש בעץ — העץ נשאר כמו שהיה",
        "    public static <T> int size(BinNode<T> t) {",
        "        if (t == null) {",
        "            return 0;",
        "        }",
        "        return 1 + size(t.getLeft()) + size(t.getRight());",
        "    }",
    ],
    "BinNode.duplicate": [
        "    // פעולת עזר: מחזירה עותק של העץ (צמתים חדשים, באותו מבנה) — העץ המקורי נשאר כמו שהיה",
        "    public static <T> BinNode<T> duplicate(BinNode<T> t) {",
        "        if (t == null) {",
        "            return null;",
        "        }",
        "        return new BinNode<T>(duplicate(t.getLeft()), t.getValue(), duplicate(t.getRight()));",
        "    }",
    ],
};

/** המחלקות (Stack / Queue / Node / BinNode) שיש להן פעולות עזר במבנה הזה: המבנה עצמו, ובקומבינציה — החיצוני והפנימי */
function helperClasses(structure) {
    const st = STRUCTURES[structure];
    if (!st) return [];
    const res = [];
    for (const id of st.combo ? [st.combo.outer, st.combo.inner] : [structure]) {
        const s = STRUCTURES[id];
        if (s && s.cls && !res.includes(s.cls)) res.push(s.cls);
    }
    return res;
}

/** האם אפשר להוסיף למבנה הזה פעולות עזר (מחסנית, תור, רשימה, עץ — לא מערך) */
function hasHelpers(structure) {
    return helperClasses(structure).length > 0;
}

/** הפעולות עצמן (מוזחות ב-4), כל אחת אחרי שורה ריקה; ריק אם לא נבחר כלום */
function helperLines(structure, size, copy) {
    const L = [];
    for (const c of helperClasses(structure))
        for (const kind of ['size', 'duplicate']) {
            if (kind === 'size' ? !size : !copy) continue;
            L.push('', ...HELPERS[c + '.' + kind]);
        }
    return L;
}

function checkHelperName(o) {
    if ((o.helperSize && o.functionName === 'size') || (o.helperCopy && o.functionName === 'duplicate'))
        throw new Error('השם ' + o.functionName + ' תפוס על ידי פעולת העזר — בחרו שם אחר לפעולה');
}

/**
 * "הוסף מבנה נתונים" עם פעולות עזר: איפה ומה להוסיף לקוד — לפני הסוגר של המחלקה שבה נמצא הסמן.
 * פעולה שכבר קיימת בקובץ (לאותו סוג מבנה) לא נוספת שוב. מחזיר { offset, code } או null (אין מה להוסיף / לא נמצאה מחלקה).
 */
function helperInsertion(text, offset, structure, size, copy) {
    const L = [];
    for (const c of helperClasses(structure))
        for (const kind of ['size', 'duplicate']) {
            if (kind === 'size' ? !size : !copy) continue;
            const exists = new RegExp('\\b[' + kind[0] + kind[0].toUpperCase() + ']' + kind.slice(1) + '\\s*(<T>)?\\s*\\(\\s*' + c + '<').test(text);
            if (!exists) L.push('', ...HELPERS[c + '.' + kind]);
        }
    if (!L.length) return null;
    const close = classEnd(text, offset);
    if (close < 0) return null;
    const lineStart = text.lastIndexOf('\n', close - 1) + 1;
    return { offset: lineStart, code: L.join('\n') + '\n' };
}

/** המיקום של הסוגר } שסוגר את המחלקה הפנימית ביותר שמכילה את offset (בלי הערות ומחרוזות), או -1 */
function classEnd(text, offset) {
    const open = [];   // { pos, isClass }
    let best = -1, bestPos = -1, i = 0, last = 0;
    while (i < text.length) {
        const ch = text[i], nx = text[i + 1];
        if (ch === '/' && nx === '/') { const e = text.indexOf('\n', i); i = e < 0 ? text.length : e; continue; }
        if (ch === '/' && nx === '*') { const e = text.indexOf('*/', i + 2); i = e < 0 ? text.length : e + 2; continue; }
        if (ch === '"' || ch === "'") {
            let j = i + 1;
            while (j < text.length && text[j] !== ch && text[j] !== '\n') j += text[j] === '\\' ? 2 : 1;
            i = j + 1;
            continue;
        }
        if (ch === '{') {
            open.push({ pos: i, isClass: /\bclass\s+\w+[^;{}]*$/.test(text.slice(last, i)) });
            last = i + 1;
        } else if (ch === '}') {
            const b = open.pop();
            if (b && b.isClass && b.pos < offset && offset <= i && b.pos > bestPos) { best = i; bestPos = b.pos; }
            last = i + 1;
        } else if (ch === ';') last = i + 1;
        i++;
    }
    return best;
}

function generate(o) {
    const st = STRUCTURES[o.structure];
    const tp = TYPES[o.type];
    if (!st) throw new Error('מבנה לא מוכר: ' + o.structure);
    if (!tp) throw new Error('טיפוס לא מוכר: ' + o.type);
    if (!isIdentifier(o.className)) throw new Error('שם המחלקה חייב להיות באנגלית, בלי רווחים, ולהתחיל באות');
    if (!isIdentifier(o.functionName)) throw new Error('שם הפעולה חייב להיות באנגלית, בלי רווחים, ולהתחיל באות');
    checkHelperName(o);
    if (st.combo) return generateCombo(o, st, tp);
    const random = !!o.random && o.type === 'Integer' && o.structure !== 'tree' && o.structure !== 'bst';
    const T = o.type;
    const full = typeOf(st, T);
    const count = Math.max(1, Math.min(3, o.count || (o.second ? 2 : 1)));
    const names = [1, 2, 3].map(i => nameN(st.varName, i));
    const v1 = names[0];
    const valuesOf = [o.values, o.values2, o.values3 || o.values2];

    // retStart = הערך ההתחלתי של המשתנה result (רק בפעולה שמחזירה ערך)
    let retType, retStart;
    switch (o.returns) {
        case 'void': retType = 'void'; break;
        case 'int': retType = 'int'; retStart = '0'; break;
        case 'boolean': retType = 'boolean'; retStart = 'false'; break;
        case 'element': retType = tp.primitive; retStart = tp.zero; break;
        case 'structure': retType = full; retStart = emptyOf(st, T, v1); break;
        default: throw new Error('ערך החזרה לא מוכר: ' + o.returns);
    }

    const params = [`${full} ${v1}`];
    const args = [v1];
    for (let i = 1; i < count; i++) { params.push(`${full} ${names[i]}`); args.push(names[i]); }
    if (o.extra) { params.push(`${extraType(T)} x`); args.push('x'); }

    const L = [];
    L.push('import unit4.collectionsLib.*;');
    if (st.dims > 0) L.push('import java.util.Arrays;');
    L.push('');
    L.push(`// תרגיל${o.recursive ? ' ברקורסיה' : ''}: ${st.label} של ${T}`);
    L.push(`public class ${o.className} {`);
    L.push('');
    L.push(`    public static ${retType} ${o.functionName}(${params.join(', ')}) {`);
    L.push(`        // תזכורת: ${st.hint.replace(/\{v\}/g, v1).replace(/\{T\}/g, T)}`);
    const helper = helperHint(st, T, v1);
    if (helper) L.push(`        // ${helper}`);
    if (T === 'Student') L.push('        // Student: getName()   getAverage()   getGrade(i)   getNumGrades()   getClassRoom()   isExcellent()');
    if (T === 'Car') L.push('        // Car: getManufacturer()   getYear()   getKm()   getFuel()   drive(km)   refuel(l)   isOld()   needsFuel()');
    if (retStart !== undefined) L.push(`        ${retType} result = ${retStart};   // כאן אוספים את מה שהפעולה מחזירה`);
    L.push('');
    if (o.recursive) {
        L.push(`        // רקורסיה: 1. מקרה בסיס — מתי עוצרים?  2. קריאה ל-${o.functionName}(...) עם מבנה קטן יותר`);
    }
    L.push('        // ========== TODO: כאן כותבים את הפתרון ==========');
    const cursorLine = L.length;
    L.push('        ');
    if (retStart !== undefined) {
        L.push('');
        L.push('        return result;');
    }
    L.push('    }');
    L.push(...helperLines(o.structure, o.helperSize, o.helperCopy));
    L.push('');
    L.push('');
    L.push('');
    L.push('    // ==================== מכאן והלאה: מעטפת מוכנה (בדיקה של הפעולה) ====================');
    L.push('    // אין צורך לשנות — אבל אפשר לשנות את הערכים כדי לבדוק מקרים נוספים');
    L.push('');
    L.push('    public static void main(String[] args) {');
    // עצים אין בהם "ערכים אקראיים" — אם סימנו, משתמשים בערכי ברירת המחדל
    const vals = (values, second) => values || (o.random ? defaultValues(o.structure, T, second) : values);
    L.push('        // בונים את המבנה לבדיקה — אפשר לשנות את הערכים');
    for (let i = 0; i < count; i++) L.push(...buildLines(st, T, names[i], vals(valuesOf[i], i > 0), i > 0, random, o.structure));
    if (o.extra) L.push(`        ${extraType(T)} x = ${extraValue(o)};`);
    L.push('');
    const show = (title, v) => {
        const p = printable(st, v);
        if (p) L.push(`        System.out.println("${title}: ${count > 1 ? ' ' + v + ' = ' : ''}" + ${p});`);
    };
    if (printable(st, v1)) L.push('        // מדפיסים את המבנה לפני הקריאה לפעולה');
    for (let i = 0; i < count; i++) show('לפני', names[i]);
    L.push('        // קוראים לפעולה שכתבתם');
    const call = `${o.functionName}(${args.join(', ')})`;
    if (o.returns === 'void') {
        L.push(`        ${call};`);
    } else {
        L.push(`        ${retType} result = ${call};`);
        const p = o.returns === 'structure' ? printable(st, 'result') : 'result';
        if (p) L.push(`        System.out.println("התוצאה: " + ${p});`);
    }
    if (printable(st, v1)) L.push('        // מדפיסים את המבנה אחרי — מה השתנה?');
    for (let i = 0; i < count; i++) show('אחרי', names[i]);
    L.push('    }');
    L.push('}');
    L.push('');
    return { code: L.join('\n'), cursorLine };
}

/** השורות ב-main שבונות את המבנה — בסגנון שלומדים בכיתה (new + push / insert, מערך עם {...}) */
function buildLines(st, T, v, values, isSecond, random, structure) {
    const full = typeOf(st, T), prim = TYPES[T].primitive;
    const add = st.cls === 'Stack' ? 'push' : 'insert';
    const n = isSecond ? 4 : 6;
    const RANDOM = '(int) (Math.random() * 20) + 1';
    if (st.cls === 'Stack' || st.cls === 'Queue') {
        const L = [`        ${full} ${v} = new ${full}();`];
        if (random || isObj(T)) {
            const count = random ? n : studentCount(values);
            L.push(`        for (int i = 0; i < ${count}; i++) {`);
            L.push(random ? `            ${v}.${add}(${RANDOM});   // מספר אקראי בין 1 ל-20`
                          : `            ${v}.${add}(${OBJECTS[T].fresh});   // ${OBJECTS[T].note}`);
            L.push('        }');
            return L;
        }
        const lits = literals(values, T, false);
        lits.forEach((x, i) => {
            const note = st.cls === 'Stack' ? (i === lits.length - 1 ? '   // האחרון שנכנס — בראש המחסנית' : '')
                                            : (i === 0 ? '   // הראשון שנכנס — בראש התור' : '');
            L.push(`        ${v}.${add}(${x});${note}`);
        });
        return L;
    }
    if (st.dims === 1) {
        if (random || isObj(T)) {
            const count = random ? n : studentCount(values);
            return [`        ${full} ${v} = new ${prim}[${count}];`,
                    `        for (int i = 0; i < ${v}.length; i++) {`,
                    random ? `            ${v}[i] = ${RANDOM};   // מספר אקראי בין 1 ל-20`
                           : `            ${v}[i] = ${OBJECTS[T].fresh};   // ${OBJECTS[T].note}`,
                    '        }'];
        }
        const lits = literals(values, T, false);
        if (lits.length === 0) return [`        ${full} ${v} = new ${prim}[0];`];
        return [`        ${full} ${v} = {${lits.join(', ')}};`];
    }
    if (st.dims === 2) {
        if (random) {
            return [`        ${full} ${v} = new ${prim}[3][4];   // 3 שורות, 4 עמודות`,
                    `        for (int i = 0; i < ${v}.length; i++) {`,
                    `            for (int j = 0; j < ${v}[i].length; j++) {`,
                    `                ${v}[i][j] = ${RANDOM};   // מספר אקראי בין 1 ל-20`,
                    '            }',
                    '        }'];
        }
        const rows = matrixLiterals(values, T);
        if (rows.length === 0) return [`        ${full} ${v} = new ${prim}[0][0];`];
        return [`        ${full} ${v} = {`]
            .concat(rows.map((r, i) => `            {${r.join(', ')}}${i < rows.length - 1 ? ',' : ''}`))
            .concat(['        };']);
    }
    // רשימה מקושרת ועצים: new + setNext / setLeft / setRight
    if (st.cls === 'Node' && random) {
        return [`        ${full} ${v} = null;`,
                `        for (int i = 0; i < ${n}; i++) {`,
                `            ${v} = new ${full}(${RANDOM}, ${v});   // מוסיף בתחילת הרשימה, מספר אקראי בין 1 ל-20`,
                '        }'];
    }
    const elems = isObj(T) ? Array(studentCount(values)).fill(OBJECTS[T].fresh)
        : literals(values, T, structure === 'tree');
    return nodeLines(structure, T, v, elems).map(l => '        ' + l);
}

/**
 * רשימה מקושרת ועצים בסגנון שלומדים בכיתה, בלי Build:
 *   רשימה — מוסיפים כל פעם בתחילת הרשימה (לכן מתחילים מהאיבר האחרון)
 *   עץ — צומת שורש, ואז setLeft / setRight לכל צומת (עץ חיפוש: לפי סדר ההכנסה)
 * elems = ביטויים (ערכים, או Student.random()); בעץ לפי רמות, 'null' = אין צומת. מחזיר שורות בלי הזחה.
 */
function nodeLines(structure, T, v, elems) {
    if (structure === 'list') {
        const type = `Node<${T}>`;
        if (elems.length === 0) return [`${type} ${v} = null;   // רשימה ריקה`];
        const L = [];
        for (let i = elems.length - 1; i >= 0; i--) {
            if (i === elems.length - 1) L.push(`${type} ${v} = new ${type}(${elems[i]});   // מתחילים מהחוליה האחרונה`);
            else L.push(`${v} = new ${type}(${elems[i]}, ${v});` + (i === 0 ? '   // החוליה הראשונה' : ''));
        }
        return L;
    }
    const type = `BinNode<${T}>`;
    const numeric = T === 'Integer' || T === 'Double';
    const cmp = (a, b) => numeric ? parseFloat(a) - parseFloat(b) : (a < b ? -1 : a > b ? 1 : 0);
    let root = null;
    if (structure === 'bst' && !isObj(T)) {
        const ins = (t, x) => {
            if (!t) return { v: x, l: null, r: null };
            if (cmp(x, t.v) < 0) t.l = ins(t.l, x); else t.r = ins(t.r, x);
            return t;
        };
        for (const x of elems) root = ins(root, x);
    } else {
        const at = i => (i >= elems.length || elems[i] === 'null') ? null : { v: elems[i], l: at(2 * i + 1), r: at(2 * i + 2) };
        root = at(0);
    }
    if (!root) return [`${type} ${v} = null;   // עץ ריק`];
    const L = [`${type} ${v} = new ${type}(${root.v});   // השורש`];
    const walk = (n, path) => {
        if (n.l) { L.push(`${path}.setLeft(new ${type}(${n.l.v}));`); walk(n.l, path + '.getLeft()'); }
        if (n.r) { L.push(`${path}.setRight(new ${type}(${n.r.v}));`); walk(n.r, path + '.getRight()'); }
    };
    walk(root, v);
    return L;
}


// ------------------------------------------------------------ קומבינציות (מבנה בתוך מבנה)

/** Stack<Integer> / Queue<Integer> / Node<Integer> — הטיפוס של כל איבר במבנה החיצוני */
function innerType(st, T) {
    return `${STRUCTURES[st.combo.inner].cls}<${T}>`;
}

/** Stack<Stack<Integer>>, Node<Queue<Integer>>, Stack<Integer>[] ... */
function comboType(st, T) {
    const it = innerType(st, T);
    return st.combo.outer === 'array' ? `${it}[]` : `${STRUCTURES[st.combo.outer].cls}<${it}>`;
}

/**
 * "1, 2; 3; 4, 5" → [["1","2"],["3"],["4","5"]]: כל קבוצה (עד ';') היא מבנה פנימי אחד.
 * בעץ — הקבוצות לפי רמות (שורש, ילדים...), והקבוצה null = אין צומת.
 */
function groupLiterals(text, type, allowNull) {
    if (isObj(type)) throw new Error('קומבינציות של מבנים הן רק של מספרים, מחרוזות, תווים או מספרים עשרוניים — בחרו טיפוס אחר');
    const raw = String(text || '').split(';').map(g => g.trim());
    while (raw.length > 0 && raw[raw.length - 1] === '') raw.pop();
    if (raw.length === 0) throw new Error('כתבו לפחות מבנה פנימי אחד (קבוצות מופרדות ב-;)');
    if (raw.length > 12) throw new Error('עד 12 מבנים פנימיים');
    return raw.map(g => allowNull && g === 'null' ? null : literals(g, type, false));
}

/** השורות שבונות את המבנה הפנימי במשתנה inner (בפעם הראשונה עם הצהרה על הטיפוס) */
function innerLines(st, T, lits, declared, iv) {
    const inn = STRUCTURES[st.combo.inner], full = innerType(st, T);
    if (inn.cls === 'Node') {
        const L = nodeLines('list', T, iv, lits);
        return declared ? L.map((l, i) => i === 0 ? l.replace(`${full} ${iv} =`, `${iv} =`) : l) : L;
    }
    const add = inn.cls === 'Stack' ? 'push' : 'insert';
    return [(declared ? '' : full + ' ') + `${iv} = new ${full}();`].concat(lits.map(x => `${iv}.${add}(${x});`));
}

/** שורות הבנייה ב-main: קודם בונים כל מבנה פנימי (במשתנה inner), ואז מכניסים אותו למבנה החיצוני */
function comboBuildLines(st, T, v, groups, iv) {
    iv = iv || 'inner';
    const outer = STRUCTURES[st.combo.outer], it = innerType(st, T), full = comboType(st, T);
    let declared = false;
    const inner = lits => { const L = innerLines(st, T, lits, declared, iv); declared = true; return L; };
    const L = [];
    if (outer.cls === 'Stack' || outer.cls === 'Queue') {
        const add = outer.cls === 'Stack' ? 'push' : 'insert';
        L.push(`${full} ${v} = new ${full}();`);
        groups.forEach((g, i) => { L.push(...inner(g), `${v}.${add}(${iv});` + (i === groups.length - 1 && add === 'push' ? '   // האחרון שנכנס — בראש המחסנית' : i === 0 && add === 'insert' ? '   // הראשון שנכנס — בראש התור' : '')); });
    } else if (outer.cls === 'Node') {
        L.push(`${full} ${v} = null;   // מוסיפים בתחילת הרשימה — לכן מתחילים מהמבנה האחרון`);
        for (let i = groups.length - 1; i >= 0; i--)
            L.push(...inner(groups[i]), `${v} = new ${outer.cls}<${it}>(${iv}, ${v});` + (i === 0 ? '   // החוליה הראשונה' : ''));
    } else if (outer.cls === 'BinNode') {
        const at = i => (i >= groups.length || groups[i] === null) ? null : { g: groups[i], l: at(2 * i + 1), r: at(2 * i + 2) };
        const root = at(0);
        if (!root) { L.push(`${full} ${v} = null;   // עץ ריק`); return L; }
        L.push(...inner(root.g), `${full} ${v} = new ${outer.cls}<${it}>(${iv});   // השורש`);
        const walk = (n, path) => {
            if (n.l) { L.push(...inner(n.l.g), `${path}.setLeft(new ${outer.cls}<${it}>(${iv}));`); walk(n.l, path + '.getLeft()'); }
            if (n.r) { L.push(...inner(n.r.g), `${path}.setRight(new ${outer.cls}<${it}>(${iv}));`); walk(n.r, path + '.getRight()'); }
        };
        walk(root, v);
    } else {
        L.push(`${full} ${v} = new ${STRUCTURES[st.combo.inner].cls}[${groups.length}];   // מערך של מבנים — כל תא מכיל מבנה`);
        groups.forEach((g, i) => L.push(...inner(g), `${v}[${i}] = ${iv};`));
    }
    return L;
}

/** איך ניגשים לאיבר (המבנה הפנימי) במבנה החיצוני */
function comboAccess(outerKey, v) {
    return { stack: `${v}.top()`, queue: `${v}.head()`, list: `${v}.getValue()`, tree: `${v}.getValue()`, array: `${v}[i]` }[outerKey];
}

function generateCombo(o, st, tp) {
    const T = o.type;
    if (isObj(T)) throw new Error('קומבינציות של מבנים הן רק של מספרים, מחרוזות, תווים או מספרים עשרוניים — בחרו טיפוס אחר');
    const outer = STRUCTURES[st.combo.outer], inn = STRUCTURES[st.combo.inner];
    const full = comboType(st, T), it = innerType(st, T), v1 = st.varName;
    const isArr = outer.dims === 1;
    const groups = groupLiterals(o.values || DEFAULT_COMBO[T], T, st.combo.outer === 'tree');
    let retType, retStart;
    switch (o.returns) {
        case 'void': retType = 'void'; break;
        case 'int': retType = 'int'; retStart = '0'; break;
        case 'boolean': retType = 'boolean'; retStart = 'false'; break;
        case 'element': retType = tp.primitive; retStart = tp.zero; break;
        case 'structure':
            retType = full;
            retStart = outer.cls === 'Stack' || outer.cls === 'Queue' ? `new ${full}()` : isArr ? `new ${inn.cls}[${v1}.length]` : 'null';
            break;
        default: throw new Error('ערך החזרה לא מוכר: ' + o.returns);
    }
    const params = [`${full} ${v1}`];
    if (o.extra) params.push(`${extraType(T)} x`);
    const L = [];
    L.push('import unit4.collectionsLib.*;');
    if (isArr) L.push('import java.util.Arrays;');
    L.push('');
    L.push(`// תרגיל${o.recursive ? ' ברקורסיה' : ''}: ${st.label} של ${T}`);
    L.push(`public class ${o.className} {`);
    L.push('');
    L.push(`    public static ${retType} ${o.functionName}(${params.join(', ')}) {`);
    L.push(`        // תזכורת — המבנה החיצוני: ${outer.hint.replace(/\{v\}/g, v1).replace(/\{T\}/g, it)}`);
    L.push(`        // תזכורת — כל איבר הוא מבנה פנימי (${it}):   ${it} inner = ${comboAccess(st.combo.outer, v1)};   ${inn.hint.replace(/\{v\}/g, 'inner').replace(/\{T\}/g, T)}`);
    if (outer.cls === 'Stack' || outer.cls === 'Queue') L.push(`        // עזר: ${outer.cls}<${it}> tmp = new ${outer.cls}<${it}>();   (${outer.label} עזר)`);
    if (outer.cls === 'Node') L.push(`        // עזר: Node<${it}> curr = ${v1};   (מצביע שעובר על הרשימה — ${v1} נשאר בראש)`);
    if (retStart !== undefined) L.push(`        ${retType} result = ${retStart};   // כאן אוספים את מה שהפעולה מחזירה`);
    L.push('');
    if (o.recursive) L.push(`        // רקורסיה: 1. מקרה בסיס — מתי עוצרים?  2. קריאה ל-${o.functionName}(...) עם מבנה קטן יותר`);
    L.push('        // ========== TODO: כאן כותבים את הפתרון ==========');
    const cursorLine = L.length;
    L.push('        ');
    if (retStart !== undefined) {
        L.push('');
        L.push('        return result;');
    }
    L.push('    }');
    L.push(...helperLines(o.structure, o.helperSize, o.helperCopy));
    L.push('');
    L.push('');
    L.push('');
    L.push('    // ==================== מכאן והלאה: מעטפת מוכנה (בדיקה של הפעולה) ====================');
    L.push('    // אין צורך לשנות — אבל אפשר לשנות את הערכים כדי לבדוק מקרים נוספים');
    L.push('');
    if (isArr) L.push('    @SuppressWarnings("unchecked")');
    L.push('    public static void main(String[] args) {');
    L.push('        // בונים את המבנה לבדיקה: כל קבוצה (מופרדת ב-;) היא מבנה פנימי אחד — בחלון רואים אותו בתוך התא');
    for (const l of comboBuildLines(st, T, v1, groups)) L.push('        ' + l);
    if (o.extra) {
        const first = (groups.find(g => g && g.length) || [TP_ZERO(T)]);
        L.push(`        ${extraType(T)} x = ${T === 'Integer' ? (first.length > 2 ? first[2] : first[0]) : first[0]};`);
    }
    L.push('');
    const p = printable(outer, v1);
    if (p) L.push('        // מדפיסים את המבנה לפני הקריאה לפעולה');
    if (p) L.push(`        System.out.println("לפני: " + ${p});`);
    L.push('        // קוראים לפעולה שכתבתם');
    const call = `${o.functionName}(${v1}${o.extra ? ', x' : ''})`;
    if (o.returns === 'void') {
        L.push(`        ${call};`);
    } else {
        L.push(`        ${retType} result = ${call};`);
        const pr = o.returns === 'structure' ? printable(outer, 'result') : 'result';
        if (pr) L.push(`        System.out.println("התוצאה: " + ${pr});`);
    }
    if (p) L.push('        // מדפיסים את המבנה אחרי — מה השתנה?');
    if (p) L.push(`        System.out.println("אחרי: " + ${p});`);
    L.push('    }');
    L.push('}');
    L.push('');
    return { code: L.join('\n'), cursorLine };
}

const TP_ZERO = T => TYPES[T].zero;

/** איך מדפיסים את המבנה ב-main (null = לא מדפיסים — עץ רואים בחלון) */
function printable(st, v) {
    if (st.dims === 1) return `Arrays.toString(${v})`;
    if (st.dims === 2) return `Arrays.deepToString(${v})`;
    return st.cls === 'BinNode' ? null : v;
}

/** הערך ההתחלתי של result בפעולה שמחזירה מבנה */
function emptyOf(st, T, v) {
    const prim = TYPES[T].primitive;
    if (st.cls === 'Stack' || st.cls === 'Queue') return `new ${typeOf(st, T)}()`;
    if (st.dims === 1) return `new ${prim}[${v}.length]`;
    if (st.dims === 2) return `new ${prim}[${v}.length][${v}[0].length]`;
    return 'null';
}

/** רמז לעזר שאפשר לפתוח בפעולה: tmp (מחסנית / תור עזר), curr (מצביע עזר ברשימה) */
function helperHint(st, T, v) {
    if (st.cls === 'Stack' || st.cls === 'Queue') return `עזר: ${st.cls}<${T}> tmp = new ${st.cls}<${T}>();   (${st.label} עזר)`;
    if (st.cls === 'Node') return `עזר: Node<${T}> curr = ${v};   (מצביע שעובר על הרשימה — ${v} נשאר בראש)`;
    return null;
}

/** שם המבנה ה-n: stk, stk2, stk3 — ואם השם נגמר בספרה (q1): q1, q2, q3 */
function nameN(base, n) {
    if (n === 1) return base;
    return /\d$/.test(base) ? base.replace(/\d+$/, '') + n : base + n;
}

/** השם הפנוי הבא לפי מה שכבר כתוב בקוד: stk, stk2... או q1, q2... */
function freeName(base, text) {
    const used = n => new RegExp('\\b' + n + '\\s*=').test(text);
    for (let i = 1; i < 100; i++) {
        const n = nameN(base, i);
        if (!used(n)) return n;
    }
    return base;
}

/** טיפוס המבנה: Stack<Integer>, int[], int[][] */
function typeOf(st, T) {
    if (st.combo) return comboType(st, T);
    if (st.dims === 1) return `${TYPES[T].primitive}[]`;
    if (st.dims === 2) return `${TYPES[T].primitive}[][]`;
    return `${st.cls}<${T}>`;
}

/** הפרמטר הנוסף x: מסוג הערכים, ולתלמידים — ציון (int) */
function extraType(T) {
    return isObj(T) ? 'int' : TYPES[T].primitive;   // תלמיד: ציון, מכונית: שנה
}

function extraValue(o) {
    if (o.type === 'Student') return '80';
    if (o.type === 'Car') return '2015';
    if (o.structure === 'matrix') {
        const cells = [].concat(...matrixLiterals(o.values, o.type));
        return cells.length ? cells[0] : TYPES[o.type].zero;
    }
    if (o.type === 'Integer') {
        if (o.random) return '8';
        const lits = literals(o.values, 'Integer', true).filter(x => x !== 'null');
        return lits.length > 2 ? lits[2] : lits.length ? lits[0] : '8';
    }
    const lits = literals(o.values, o.type, true).filter(x => x !== 'null');
    return lits.length ? lits[0] : TYPES[o.type].zero;
}

// ------------------------------------------------------------ "הוספה לקוד": מבנה עם נתוני דמה

/** מחולל אקראי פשוט וזהה ב-JS וב-Java (כדי שאפשר לבדוק ששני התוספים מפיקים אותו קוד) */
function rng(seed) {
    let s = seed | 0;
    return n => {
        s = (Math.imul(s, 1103515245) + 12345) | 0;
        return (s >>> 1) % n;
    };
}

const WORDS = ['apple', 'sun', 'dog', 'cat', 'tree', 'book', 'star', 'moon', 'fish', 'bird',
               'rain', 'milk', 'car', 'ball', 'cake', 'lion', 'rose', 'snow', 'wind', 'gold'];

const SNIPPETS = {
    stack: 'מחסנית', queue: 'תור', list: 'רשימה מקושרת', tree: 'עץ בינארי', bst: 'עץ חיפוש בינארי',
    array: 'מערך', matrix: 'מערך דו-ממדי', student: 'תלמיד', car: 'מכונית'
};

function randomValue(type, next) {
    switch (type) {
        case 'Integer': return String(1 + next(99));
        case 'Double': { const t = 5 + next(990); return Math.floor(t / 10) + '.' + (t % 10); }
        case 'Character': return "'" + String.fromCharCode(97 + next(26)) + "'";
        default: return '"' + WORDS[next(WORDS.length)] + '"';
    }
}

/**
 * ערכים אקראיים לשדה "ערכים" (count איברים; במערך דו-ממדי — count שורות של 4).
 * בעץ חיפוש — בלי כפילויות. התוצאה היא טקסט כמו "5, 23, 8" שהתלמיד יכול לשנות.
 */
function randomValues(structure, type, count, seed) {
    const next = rng(seed);
    const plain = lit => lit.length >= 2 && (lit[0] === "'" || lit[0] === '"') ? lit.slice(1, -1) : lit;
    if (STRUCTURES[structure].combo) {   // מבנה בתוך מבנה: count מבנים פנימיים, בכל אחד 2–4 ערכים אקראיים
        const groups = Math.max(1, Math.min(count | 0, 12));
        const parts = [];
        for (let g = 0; g < groups; g++) {
            const vals = [];
            for (let k = 2 + next(3); k > 0; k--) vals.push(plain(randomValue(type, next)));
            parts.push(vals.join(', '));
        }
        return parts.join('; ');
    }
    const n = Math.max(1, Math.min(count | 0, 20));
    if (structure === 'matrix') {
        const rows = [];
        for (let i = 0; i < n; i++) {
            const row = [];
            for (let c = 0; c < 4; c++) row.push(plain(randomValue(type, next)));
            rows.push(row.join(', '));
        }
        return rows.join('; ');
    }
    const values = [];
    const used = new Set();
    for (let tries = 0; values.length < n && tries < 1000; tries++) {
        const v = plain(randomValue(type, next));
        if (structure === 'bst' && used.has(v)) continue;   // בעץ חיפוש — בלי כפילויות
        used.add(v);
        values.push(v);
    }
    return values.join(', ');
}

/**
 * פקודה אחת שיוצרת מבנה עם נתוני דמה, להכנסה במקום הסמן.
 * o = { structure, type, name, size, rows, cols, seed, indent }
 */
function snippet(o) {
    const indent = o.indent || '';
    const next = rng(o.seed);
    const name = o.name;
    if (!isIdentifier(name)) throw new Error('שם המשתנה חייב להיות באנגלית, בלי רווחים, ולהתחיל באות');
    if (o.structure === 'student') return `${indent}Student ${name} = Student.random();   // תלמיד עם נתוני דמה (שם, כיתה, ציונים)`;
    if (o.structure === 'car') return `${indent}Car ${name} = Car.random();   // מכונית עם נתוני דמה (יצרן, דגם, שנה, דלק)`;
    const st = STRUCTURES[o.structure];
    if (!st) throw new Error('מבנה לא מוכר: ' + o.structure);
    const T = o.type;
    if (!TYPES[T]) throw new Error('טיפוס לא מוכר: ' + T);
    if (o.values !== undefined && o.values !== null) return valuesSnippet(o, st, T, name, indent);
    if (st.combo) return comboSnippet(o, st, next, indent);
    const full = typeOf(st, T), prim = TYPES[T].primitive;
    const size = Math.max(1, Math.min(o.size || 5, 30));
    const lines = L => L.map(l => indent + l).join('\n');
    const add = st.cls === 'Stack' ? 'push' : 'insert';
    if (isObj(T)) {
        if (st.dims === 2) throw new Error('אין מערך דו-ממדי של תלמידים או מכוניות — בחרו מערך רגיל');
        if (st.cls === 'Stack' || st.cls === 'Queue') {
            return lines([`${full} ${name} = new ${full}();`,
                          `for (int i = 0; i < ${size}; i++) {`,
                          `    ${name}.${add}(${OBJECTS[T].fresh});   // ${OBJECTS[T].note}`,
                          '}']);
        }
        if (st.dims === 1) {
            return lines([`${full} ${name} = new ${T}[${size}];`,
                          `for (int i = 0; i < ${name}.length; i++) {`,
                          `    ${name}[i] = ${OBJECTS[T].fresh};   // ${OBJECTS[T].note}`,
                          '}']);
        }
        return lines(nodeLines(o.structure, T, name, Array(size).fill(OBJECTS[T].fresh)));
    }
    if (st.dims === 2) {
        const rows = Math.max(1, Math.min(o.rows || 3, 12)), cols = Math.max(1, Math.min(o.cols || 4, 12));
        const L = [`${full} ${name} = {   // ${rows} שורות, ${cols} עמודות`];
        for (let r = 0; r < rows; r++) {
            const cells = [];
            for (let c = 0; c < cols; c++) cells.push(randomValue(T, next));
            L.push(`    {${cells.join(', ')}}${r < rows - 1 ? ',' : ''}`);
        }
        L.push('};');
        return lines(L);
    }
    const values = [];
    const used = new Set();
    while (values.length < size) {
        const v = randomValue(T, next);
        if (o.structure === 'bst' && used.has(v) && used.size < 90) continue;   // בעץ חיפוש — בלי כפילויות
        used.add(v);
        values.push(v);
    }
    if (st.cls === 'Stack' || st.cls === 'Queue') {
        const L = [`${full} ${name} = new ${full}();`];
        values.forEach((x, i) => {
            const note = st.cls === 'Stack' ? (i === values.length - 1 ? '   // האחרון שנכנס — בראש המחסנית' : '')
                                            : (i === 0 ? '   // הראשון שנכנס — בראש התור' : '');
            L.push(`${name}.${add}(${x});${note}`);
        });
        return lines(L);
    }
    if (st.dims === 1) return `${indent}${full} ${name} = {${values.join(', ')}};`;
    return lines(nodeLines(o.structure, T, name, values));
}


/**
 * "הוסף מבנה נתונים": המבנה נבנה מהערכים שהמשתמש בחר (או שהחלון מילא אקראית) — אותה כתיבה כמו בתרגיל.
 * o.values: "5, 2, 8" ; מטריצה — שורות ב-; ; קומבינציה — מבנים פנימיים ב-; ; תלמידים / מכוניות — כמה ליצור (מספר)
 */
function valuesSnippet(o, st, T, name, indent) {
    if (st.combo) {
        const groups = groupLiterals(o.values, T, st.combo.outer === 'tree');
        const L = [`// ${st.label} — כל קבוצת ערכים היא מבנה פנימי אחד`].concat(comboBuildLines(st, T, name, groups, name + 'Inner'));
        return L.map(l => indent + l).join('\n');
    }
    return buildLines(st, T, name, o.values, false, false, o.structure).map(l => indent + l.slice(8)).join('\n');
}

/** "הוספה לקוד" של קומבינציה: כמה מבנים פנימיים (size) עם 2–4 ערכים אקראיים בכל אחד */
function comboSnippet(o, st, next, indent) {
    const T = o.type;
    if (isObj(T)) throw new Error('קומבינציות של מבנים הן רק של מספרים, מחרוזות, תווים או מספרים עשרוניים — בחרו טיפוס אחר');
    const count = Math.max(1, Math.min(o.size || 3, 6));
    const groups = [];
    for (let i = 0; i < count; i++) {
        const g = [];
        const n = 2 + next(3);
        for (let k = 0; k < n; k++) g.push(randomValue(T, next));
        groups.push(g);
    }
    const L = [`// ${st.label} — כל קבוצת ערכים היא מבנה פנימי אחד`].concat(comboBuildLines(st, T, o.name, groups, o.name + 'Inner'));
    return L.map(l => indent + l).join('\n');
}

/** מה יש בכל תא? — האפשרויות בחלון (ריק = ערכים רגילים) */
const INSIDE = [
    { id: '', label: 'ערכים רגילים', description: 'מספרים, מילים, תווים, תלמידים...' },
    { id: 'stack', label: 'מחסניות', description: 'בכל תא — מחסנית שלמה' },
    { id: 'queue', label: 'תורים', description: 'בכל תא — תור שלם' },
    { id: 'list', label: 'רשימות מקושרות', description: 'בכל תא — רשימה מקושרת' }
];

/** האם אפשר מבנים בתוך התאים של המבנה הזה (מחסנית, תור, רשימה, עץ, מערך) */
function canNest(outer) {
    return COMBO_OUTERS.includes(outer);
}

/** מזהה המבנה: מחסנית + מחסניות → stack_stack ; בלי פנימי → stack */
function comboId(outer, inside) {
    return inside && canNest(outer) ? outer + '_' + inside : outer;
}

/**
 * מה לצייר בחלון "הוסף מבנה נתונים": { outer, inner, groups, type } — המבנה החיצוני והתוכן של כל תא.
 * inner = 'cell' — בכל תא ערך אחד (המבנה הרגיל); stack / queue / list — בכל תא מבנה שלם (קומבינציה).
 * groups: מחסנית/תור/רשימה/מערך — תא אחד לכל איבר; עץ — לפי רמות (null = אין צומת); מטריצה — שורה לכל קבוצה.
 * outer = 'card' — תלמיד / מכונית בודד. זורק Error (בעברית) אם הערכים לא תקינים.
 * שימו לב: יש עותק זהה בג'אווה (Scaffold.preview).
 */
function preview(structure, type, values) {
    if (structure === 'student' || structure === 'car') {
        const c = structure === 'student' ? 'Student' : 'Car';
        return { outer: 'card', inner: c, groups: [], type: c };
    }
    const st = STRUCTURES[structure];
    if (!st) throw new Error('מבנה לא מוכר: ' + structure);
    const plain = lit => lit.length >= 2 && (lit[0] === "'" || lit[0] === '"') ? lit.slice(1, -1) : lit;
    const shown = typeOf(st, type);
    if (st.combo) {
        const groups = groupLiterals(values, type, st.combo.outer === 'tree').map(g => g === null ? null : g.map(plain));
        return { outer: st.combo.outer, inner: st.combo.inner, groups, type: shown };
    }
    if (st.dims === 2) return { outer: 'matrix', inner: 'cell', groups: matrixLiterals(values, type).map(r => r.map(plain)), type: shown };
    const obj = isObj(type);
    const items = obj ? Array.from({ length: studentCount(values) }, (_, i) => (type === 'Student' ? 'S' : 'C') + (i + 1))
        : literals(values, type, structure === 'tree').map(x => x === 'null' ? x : plain(x));
    const groups = [];
    if (structure === 'bst' && !obj) {
        const numeric = type === 'Integer' || type === 'Double';
        const cmp = (a, b) => numeric ? parseFloat(a) - parseFloat(b) : (a < b ? -1 : a > b ? 1 : 0);
        const ins = (t, x) => { if (!t) return { v: x, l: null, r: null }; if (cmp(x, t.v) < 0) t.l = ins(t.l, x); else t.r = ins(t.r, x); return t; };
        let root = null;
        for (const x of literals(values, type, false)) root = ins(root, x);
        const heap = (n, i, depth) => {
            if (!n || depth > 5) return;
            while (groups.length <= i) groups.push(null);
            groups[i] = [plain(n.v)];
            heap(n.l, 2 * i + 1, depth + 1);
            heap(n.r, 2 * i + 2, depth + 1);
        };
        heap(root, 0, 0);
    } else if (structure === 'tree' || structure === 'bst') {
        for (const x of items) groups.push(x === 'null' ? null : [x]);
    } else {
        for (const x of items) groups.push([x]);
    }
    return { outer: structure === 'bst' ? 'tree' : structure, inner: 'cell', groups, type: shown };
}

/** תצוגה מקדימה קצרה (טקסט) של מה שייבנה: הטיפוס, והמבנים הפנימיים לפי סדרם במבנה החיצוני */
function comboPreview(structure, type, text) {
    const st = STRUCTURES[structure];
    if (!st || !st.combo) return '';
    let groups;
    try { groups = groupLiterals(text, type, st.combo.outer === 'tree'); } catch (e) { return ''; }
    const g = x => x === null ? 'null' : '[' + x.join(', ') + ']';
    const head = comboType(st, type);
    switch (st.combo.outer) {
        case 'stack': return `${head}\ntop → ${groups.slice().reverse().map(g).join('   ')}`;
        case 'queue': return `${head}\nhead → ${groups.map(g).join('   ')} ← tail`;
        case 'list': return `${head}\n${groups.map(g).join(' → ')} → null`;
        case 'tree': return `${head}\n` + (groups.length <= 3 ? ['root', 'left', 'right'].slice(0, groups.length).map((n, i) => `${n} = ${g(groups[i])}`).join(',  ')
            : 'לפי רמות: ' + groups.map(g).join('  '));
        default: return `${head}\n${groups.map((x, i) => `[${i}] = ${g(x)}`).join('   ')}`;
    }
}

/** שגיאה (בעברית) אם השם לא חוקי או מתנגש במשהו שכבר מוגדר במקום הסמן; null אם הכול בסדר. offset = מיקום הסמן בטקסט (-1 = לא ידוע) */
function nameError(name, text, offset) {
    return names.checkName(name, text, offset, module.exports.isIdentifier);
}

/** שם מוצע למשתנה חדש (stk, stk2... / q1, q2...) שלא מתנגש בקוד */
function suggestName(structure, text, offset) {
    return names.suggestName(snippetName(structure), text, offset, module.exports.isIdentifier);
}

/** שם ברירת המחדל למשתנה */
function snippetName(structure) {
    return structure === 'student' ? 's1' : structure === 'car' ? 'c1' : STRUCTURES[structure].varName;
}

// ------------------------------------------------------------ "מה יש במחלקה" (Student / Car)
// שימו לב: יש עותק זהה בג'אווה בתוסף של Eclipse (ClassInfo.java) — שינוי כאן צריך להיעשות גם שם.
const CLASS_INFO = {
    "Student": {
        "name": "Student",
        "title": "תלמיד (Student)",
        "intro": "המאפיינים פרטיים — ניגשים אליהם דרך הפעולות (למשל s1.getName()).",
        "creators": [
            [
                "new Student(\"נועה\", \"יא2\", 90, 85, 77)",
                "שם, כיתה וציונים (כמה שרוצים)"
            ],
            [
                "Student.random()",
                "תלמיד אקראי עם נתוני דמה"
            ]
        ],
        "fields": [
            [
                "name",
                "String",
                "שם התלמיד"
            ],
            [
                "classRoom",
                "String",
                "הכיתה, למשל \"יא2\""
            ],
            [
                "id",
                "String",
                "תעודת זהות (9 ספרות)"
            ],
            [
                "age",
                "int",
                "גיל"
            ],
            [
                "grades",
                "int[]",
                "הציונים"
            ]
        ],
        "methods": [
            [
                "getName()  getClassRoom()  getId()  getAge()",
                "קריאת הפרטים"
            ],
            [
                "getNumGrades()",
                "כמה ציונים יש"
            ],
            [
                "getGrade(i)",
                "הציון במקום i (מ-0)"
            ],
            [
                "setGrade(i, g)  addGrade(g)",
                "שינוי ציון / הוספת ציון בסוף"
            ],
            [
                "getAverage()",
                "ממוצע הציונים (double)"
            ],
            [
                "getMaxGrade()  getMinGrade()",
                "הציון הגבוה / הנמוך"
            ],
            [
                "isExcellent()  isPassing()",
                "ממוצע 90 ומעלה / 55 ומעלה"
            ],
            [
                "compareTo(other)  equals(other)",
                "השוואה לפי ממוצע / שווים אם אותה ת\"ז"
            ]
        ]
    },
    "Car": {
        "name": "Car",
        "title": "מכונית (Car)",
        "intro": "המאפיינים פרטיים — ניגשים אליהם דרך הפעולות (למשל c1.getKm()).",
        "creators": [
            [
                "new Car(\"Mazda\", \"3\", 2019)",
                "יצרן, דגם ושנת ייצור"
            ],
            [
                "Car.random()",
                "מכונית אקראית עם נתוני דמה"
            ]
        ],
        "fields": [
            [
                "manufacturer",
                "String",
                "יצרן, למשל \"Mazda\""
            ],
            [
                "model",
                "String",
                "דגם"
            ],
            [
                "year",
                "int",
                "שנת ייצור"
            ],
            [
                "color",
                "String",
                "צבע"
            ],
            [
                "plate",
                "String",
                "מספר רישוי"
            ],
            [
                "km",
                "int",
                "קילומטראז' (כמה ק\"מ נסעה)"
            ],
            [
                "fuel",
                "double",
                "ליטרים במיכל עכשיו"
            ],
            [
                "tankSize",
                "double",
                "גודל המיכל בליטרים"
            ],
            [
                "speed",
                "int",
                "מהירות עכשיו (קמ\"ש)"
            ]
        ],
        "methods": [
            [
                "getManufacturer()  getModel()  getYear()  getColor()  getPlate()",
                "קריאת הפרטים"
            ],
            [
                "getKm()  getFuel()  getTankSize()  getSpeed()",
                "קריאת מצב המכונית"
            ],
            [
                "drive(km)",
                "נוסעת עד km ק\"מ (עד שנגמר הדלק) — מחזירה כמה נסעה"
            ],
            [
                "refuel(l)",
                "מתדלקת l ליטרים (עד שהמיכל מלא)"
            ],
            [
                "accelerate(d)  brake(d)",
                "מוסיפה / מורידה מהירות"
            ],
            [
                "getAge()  isOld()",
                "גיל המכונית / 10 שנים ומעלה"
            ],
            [
                "needsFuel()  isMoving()",
                "פחות מעשירית מיכל / המהירות גדולה מ-0"
            ],
            [
                "compareTo(other)  equals(other)",
                "השוואה לפי שנה / שוות אם אותו מספר רישוי"
            ]
        ]
    }
};

module.exports = { helperClasses, hasHelpers, helperLines, helperInsertion, classEnd, preview, nameError, suggestName, STRUCTURES, TYPES, RETURNS, SNIPPETS, INSIDE, canNest, comboId, comboPreview, groupLiterals, defaultValues, isIdentifier, literals, generate, snippet, snippetName, typeOf, randomValues, nameN, freeName, CLASS_INFO };
