'use strict';
/**
 * בדיקת שם למשתנה חדש — האם הוא לא מתנגש בשום דבר שכבר מוגדר במקום שבו הסמן (Java ו-C#).
 *
 * מנתחים את הקוד (בלי הערות ומחרוזות) ומחפשים הגדרות: משתנים, פרמטרים, שדות, פעולות ומחלקות.
 * שם מתנגש אם הוא מוגדר באחד מהבלוקים שהסמן נמצא בתוכם (הפעולה, המחלקה...), או בבלוק פנימי שבא אחרי הסמן
 * (ב-Java וב-C# אסור להגדיר שוב בבלוק פנימי שם שכבר קיים בבלוק החיצוני). משתנה בפעולה אחרת — לא מתנגש.
 *
 * שימו לב: יש עותק זהה בג'אווה בתוסף של Eclipse (NameCheck.java) — שינוי כאן צריך להיעשות גם שם
 * (tools/test/names-cases.txt בודק ששניהם נותנים אותן תשובות).
 */

/** מילים שאחריהן לא מגיע "טיפוס" (כדי לא לטעות ולחשוב ש-"return x" היא הגדרה של x) */
const NOT_A_TYPE = new Set(('return new else throw case goto in is as out ref using await yield break continue do if while for foreach switch ' +
    'lock try catch finally typeof sizeof default delete not and or namespace import package extends implements instanceof throws where ' +
    'get set add remove').split(' '));

/** מילים שאחריהן בא סוגריים בלי שזו פעולה (לא "type name(") */
const CONTROL = new Set('if for foreach while switch catch using lock synchronized return throw new typeof sizeof when fixed'.split(' '));

/** שמות של מחלקות מוכנות / של השפה — לא משתמשים בהם כשם משתנה */
const RESERVED_NAMES = new Set(('Stack Queue Node BinNode Student Car String Math System Arrays Integer Double Character Object Random Console ' +
    'List Map Scanner Build Main').split(' '));

/** מחליף הערות ומחרוזות ברווחים (שומר על אותו אורך ועל מעברי שורה — כדי שהמיקומים יישארו נכונים) */
function stripCode(text) {
    const out = text.split('');
    const n = text.length;
    const blank = (a, b) => { for (let k = a; k < b; k++) if (out[k] !== '\n' && out[k] !== '\r') out[k] = ' '; };
    let i = 0;
    while (i < n) {
        const c = text[i], d = text[i + 1];
        if (c === '/' && d === '/') { let j = i; while (j < n && text[j] !== '\n') j++; blank(i, j); i = j; }
        else if (c === '/' && d === '*') { let j = text.indexOf('*/', i + 2); j = j < 0 ? n : j + 2; blank(i, j); i = j; }
        else if (c === '"' && text.startsWith('"""', i)) { let j = text.indexOf('"""', i + 3); j = j < 0 ? n : j + 3; blank(i, j); i = j; }
        else if (c === '"' || c === "'") {
            let j = i + 1;
            while (j < n && text[j] !== c && text[j] !== '\n') { if (text[j] === '\\') j++; j++; }
            blank(i, Math.min(j + 1, n));
            i = Math.min(j + 1, n);
        } else i++;
    }
    return out.join('');
}

/** "a, Map<K, V> b, int c = 5" → ["a", "b", "c"] — השם האחרון בכל פרמטר */
function paramNames(list) {
    const parts = [];
    let depth = 0, cur = '';
    for (const ch of list) {
        if (ch === '<' || ch === '(' || ch === '[') depth++;
        else if (ch === '>' || ch === ')' || ch === ']') depth--;
        if (ch === ',' && depth === 0) { parts.push(cur); cur = ''; } else cur += ch;
    }
    parts.push(cur);
    const names = [];
    for (let p of parts) {
        p = p.replace(/=.*$/s, '').replace(/\[[\s,]*\]/g, ' ').trim();
        const m = /([A-Za-z_]\w*)\s*$/.exec(p);
        if (m && /\s/.test(p) && !NOT_A_TYPE.has(m[1])) names.push(m[1]);
    }
    return names;
}

/** הגדרות משתנים בתוך הצהרה אחת (טקסט בלי הערות): "int a = 1, b;" / "Stack<Integer> s = new ..." / "foreach (var x in xs)" */
function statementNames(stmt) {
    let s = stmt;
    // מקצרים סוגריים פנימיים (קריאות, ביטויים) כדי שלא יפריעו
    for (let k = 0; k < 6; k++) s = s.replace(/\([^()]*\)/g, '()');
    s = s.replace(/\{[^{}]*\}/g, '{}');
    // בקרה בתחילת ההצהרה: for (...) / if (...) / while (...) — משתני הלולאה מוגדרים רק בתוכה, לא כאן
    s = s.replace(/^\s*(?:else\s+)?(?:for|foreach|while|if|switch|catch|using|lock|synchronized)\s*\(\)\s*/, '');
    const names = [];
    const re = /(?:^|[\s(,;{}])(?:(?:final|readonly|const|static|volatile|private|public|protected|internal|new|ref|out)\s+)*([A-Za-z_]\w*(?:\s*\.\s*[A-Za-z_]\w*)*(?:\s*<[^;(){}=]*?>)?(?:\s*\[[\s,]*\])*\??)\s+([A-Za-z_]\w*)\s*(?=[=;,):\[]|\bin\b|$)/g;
    let m, first = true;
    while ((m = re.exec(s))) {
        const type = m[1].split(/[\s<\[.?]/)[0];
        if (NOT_A_TYPE.has(type) || NOT_A_TYPE.has(m[2])) continue;
        names.push(m[2]);
        if (first) {
            first = false;
            // הצהרה עם כמה משתנים: int a = 1, b = 2;
            const rest = s.slice(m.index + m[0].length);
            const more = /,\s*([A-Za-z_]\w*)\s*(?==[^=]|,|;|$)/g;
            let r;
            while ((r = more.exec(rest))) names.push(r[1]);
        }
    }
    return names;
}

/**
 * כל ההגדרות בקוד: [{ name, block }], ואילו בלוקים עוטפים את offset (הסמן).
 * blocks[id] = { parent, start }
 */
function analyze(text, offset) {
    const code = stripCode(text);
    const blocks = [{ parent: -1, start: 0, kind: 'file' }];
    let hasClass = false;
    const decls = [];
    const stack = [0];
    let chain = null;
    let seg = 0, paren = 0;
    const n = code.length;
    const add = (names, block) => names.forEach(nm => decls.push({ name: nm, block }));
    const snap = i => { if (chain === null && offset >= 0 && i >= offset) chain = stack.slice(); };
    for (let i = 0; i < n; i++) {
        snap(i);
        const c = code[i];
        if (c === '(') paren++;
        else if (c === ')') paren = Math.max(0, paren - 1);
        else if (paren === 0 && c === ';') {
            add(statementNames(code.slice(seg, i)), stack[stack.length - 1]);
            seg = i + 1;
        } else if (paren === 0 && c === '{') {
            const header = code.slice(seg, i);
            const t = header.trim();
            if (/(?:=|\]|\bnew|,|\breturn)$/.test(t)) {
                // אתחול מערך / אובייקט: { ... } בתוך הצהרה — מדלגים עד הסוגר המתאים
                let depth = 1, j = i + 1;
                while (j < n && depth > 0) { if (code[j] === '{') depth++; else if (code[j] === '}') depth--; j++; }
                for (let k = i; k < j; k++) snap(k);
                i = j - 1;
                continue;
            }
            const parent = stack[stack.length - 1];
            const id = blocks.length;
            const cls = /\b(?:class|interface|enum|record|struct)\s+([A-Za-z_]\w*)/.exec(header);
            blocks.push({ parent, start: i, kind: cls ? 'class' : 'code' });
            if (cls) { add([cls[1]], parent); hasClass = true; }
            const call = /([A-Za-z_]\w*)\s*(?:<[^()]*>)?\s*\(([^]*)\)\s*(?:throws[^{]*|where[^{]*|:\s*(?:base|this)\s*\([^)]*\))?\s*$/.exec(header);
            if (call) {
                const head = header.slice(0, call.index).trim();
                const isControl = CONTROL.has(call[1]);
                if (!isControl && /[\w>\]]\s*$/.test(head) && !/(?:^|[\s;{}])new\s*$/.test(head) && !/\b(?:else|do|try|finally)\s*$/.test(head)) add([call[1]], parent);   // פעולה: type name(...)
                if (isControl) add(statementNames(call[2]), id);                                   // for (int i...) / catch (Exception e)
                else add(paramNames(call[2]), id);                                                 // פרמטרים של פעולה
            }
            stack.push(id);
            seg = i + 1;
        } else if (paren === 0 && c === '}') {
            if (stack.length > 1) stack.pop();
            seg = i + 1;
        }
    }
    if (chain === null) chain = stack.slice();
    return { decls, blocks, chain, hasClass };
}

function isAncestor(blocks, ancestor, id) {
    for (let b = id; b >= 0; b = blocks[b].parent) if (b === ancestor) return true;
    return false;
}

/**
 * האם הנקודה offset נמצאת בתוך פעולה (או כל בלוק קוד אחר) — ולא בגוף המחלקה או מחוץ לה. שם אי אפשר להכניס משתנה / פקודות.
 * קובץ בלי מחלקה בכלל (C# עם פקודות ברמה העליונה) — מותר.
 */
function insideFunction(text, offset) {
    if (offset < 0) return true;
    const { blocks, chain, hasClass } = analyze(text, offset);
    if (!hasClass) return true;
    return chain.some(id => id > 0 && blocks[id].kind !== 'class');
}

/** כל השמות שאסור להגדיר בנקודה offset (offset < 0 = לא ידוע: כל מה שמוגדר בקובץ) */
function takenNames(text, offset) {
    const { decls, blocks, chain } = analyze(text, offset);
    const taken = new Set();
    const inner = chain[chain.length - 1];
    for (const d of decls) {
        if (offset < 0 || chain.includes(d.block)) { taken.add(d.name); continue; }
        // בלוק פנימי שבא אחרי הסמן (בתוך הבלוק שבו מכניסים) — הגדרה חוזרת שם אסורה
        if (blocks[d.block].start >= offset && isAncestor(blocks, inner, d.block)) taken.add(d.name);
    }
    return taken;
}

/**
 * null אם השם תקין ופנוי, אחרת הודעת שגיאה בעברית.
 * isIdentifier — פונקציה שבודקת שם חוקי בשפה (Java / C#); offset — מיקום הסמן בטקסט (-1 = לא ידוע)
 */
function checkName(name, text, offset, isIdentifier) {
    if (!isIdentifier(name)) return 'השם חייב להיות באנגלית, בלי רווחים, להתחיל באות, ולא להיות מילה שמורה של השפה';
    if (RESERVED_NAMES.has(name)) return `"${name}" הוא שם של מחלקה מוכנה — בחרו שם אחר`;
    if (takenNames(text, offset).has(name)) return `כבר יש בקוד משתנה, פעולה או מחלקה בשם "${name}" — בחרו שם אחר`;
    return null;
}

/** שם מוצע: base, base2, base3... הראשון שפנוי (stk, stk2... או q1, q2...) */
function suggestName(base, text, offset, isIdentifier) {
    const taken = takenNames(text, offset);
    const nameN = k => k === 1 ? base : /\d$/.test(base) ? base.replace(/\d+$/, '') + k : base + k;
    for (let k = 1; k < 100; k++) {
        const nm = nameN(k);
        if (!taken.has(nm) && !RESERVED_NAMES.has(nm) && isIdentifier(nm)) return nm;
    }
    return base;
}

module.exports = { insideFunction, checkName, suggestName, takenNames, stripCode, RESERVED_NAMES };
