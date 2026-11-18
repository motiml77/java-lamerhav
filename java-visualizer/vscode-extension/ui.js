'use strict';
/**
 * ההודעות והחלונות הקטנים של התוסף, בעיצוב של האתר (site/DESIGN.md): לוחות שטוחים, קו של 1px, פינות קטנות, כחול אחד.
 *   - הודעה של VS Code (בפינה) לא יכולה להיות כולה מימין לשמאל, אז היא קצרה ומתחילה ב-RLM (U+200F), וכך השורה מוצגת מימין.
 *     כל ההודעות של התוסף עוברות דרך info / warn / error כאן (הבדיקה vscode-test.js נכשלת על קריאה ישירה).
 *   - מה שארוך יותר (רשימת שגיאות, סעיפים שעונים עליהם במילים) נפתח בחלון Webview קטן עם dir="rtl", והקוד בתוכו משמאל לימין.
 * בלי תלות ב-vscode (חוץ מהפונקציות שמקבלות אותו), כדי שאפשר יהיה לבדוק את זה בנפרד.
 */

const RLM = '\u200F';
/** מוסיף RLM בתחילת הטקסט (פעם אחת) */
const rtl = s => { const t = String(s == null ? '' : s); return t.startsWith(RLM) ? t : RLM + t; };

const info = (vscode, text, ...rest) => vscode.window.showInformationMessage(rtl(text), ...rest);
const warn = (vscode, text, ...rest) => vscode.window.showWarningMessage(rtl(text), ...rest);
const error = (vscode, text, ...rest) => vscode.window.showErrorMessage(rtl(text), ...rest);

const esc = s => String(s == null ? '' : s).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');

// ------------------------------------------------------------ הודעות הקומפיילר בעברית

/** javac — לפי הטקסט (כמו JavacHebrew של IntelliJ); הודעה שלא מוכרת נשארת באנגלית */
const JAVAC = [
    [/^missing return statement$/, 'חסרה הוראת return: הפעולה צריכה להחזיר ערך בכל מסלול (גם אחרי לולאה או if)'],
    [/^';' expected$/, 'חסר ; בסוף הפקודה'],
    [/^'(.+)' expected$/, 'חסר $1'],
    [/^cannot find symbol$/, 'השם לא מוכר. בדקו את האיות (אותיות גדולות וקטנות), שהמשתנה הוגדר, ושיש import'],
    [/^incompatible types: possible lossy conversion from (.+) to (.+)$/, 'המרה מ-$1 ל-$2 עלולה לאבד מידע (צריך המרה מפורשת)'],
    [/^incompatible types: (.+) cannot be converted to (.+)$/, 'אי התאמה בטיפוסים: אי אפשר להכניס $1 למשתנה מטיפוס $2'],
    [/^variable (\w+) might not have been initialized$/, 'המשתנה $1 עוד לא קיבל ערך. צריך לתת לו ערך התחלתי'],
    [/^variable (\w+) is already defined in method (.+)$/, 'המשתנה $1 כבר מוגדר בפעולה $2. בחרו שם אחר'],
    [/^unreachable statement$/, 'הפקודה הזאת אף פעם לא תתבצע (היא אחרי return או break)'],
    [/^class, interface, enum, or record expected$/, 'יש סוגר } מיותר, או קוד מחוץ למחלקה'],
    [/^reached end of file while parsing$/, 'חסר סוגר } בסוף הקובץ'],
    [/^illegal start of expression$/, 'ביטוי לא חוקי. בדרך כלל חסר סוגר או ; בשורה הקודמת'],
    [/^not a statement$/, 'זו לא פקודה. אולי חסר = או קריאה לפעולה'],
    [/^non-static method (.+) cannot be referenced from a static context$/, 'אי אפשר לקרוא לפעולה $1 (שאינה static) מתוך פעולה static. הוסיפו static לכותרת שלה'],
    [/^non-static variable (\w+) cannot be referenced from a static context$/, 'אי אפשר להשתמש במשתנה $1 (שאינו static) מתוך פעולה static'],
    [/^bad operand types for binary operator '(.+)'$/, 'אי אפשר להפעיל את $1 על הטיפוסים האלה'],
    [/^unclosed string literal$/, 'חסרות מירכאות " בסוף המחרוזת'],
    [/^method (\w+) in class (\w+) cannot be applied to given types;?$/, 'הפעולה $1 במחלקה $2 נקראה עם פרמטרים לא מתאימים (מספר או טיפוס)'],
    [/^(.+) has private access in (.+)$/, '$1 פרטי (private) ב-$2, וגם מחלקה שיורשת ממנה לא רואה אותו. ניגשים אליו דרך פעולה (get...), או משנים ל-protected']
];

/** C# — לפי מספר השגיאה (CS1002 ...) */
const CSC = {
    CS1002: 'חסר ; בסוף הפקודה', CS1513: 'חסר סוגר }', CS1514: 'חסר סוגר {', CS1026: 'חסר סוגר )', CS1003: 'חסר סימן בשורה (פסיק, סוגר או ;)',
    CS1525: 'ביטוי לא חוקי. בדרך כלל חסר סוגר או ; בשורה הקודמת', CS1010: 'חסרות מירכאות " בסוף המחרוזת',
    CS0103: 'השם לא מוכר. בדקו את האיות (אותיות גדולות וקטנות) ושהמשתנה הוגדר',
    CS0246: 'הטיפוס לא מוכר. בדקו את האיות, ושיש using מתאים',
    CS0029: 'אי התאמה בטיפוסים: אי אפשר להכניס את הערך הזה למשתנה מהטיפוס הזה',
    CS0266: 'המרה שעלולה לאבד מידע. צריך המרה מפורשת, למשל (int)',
    CS0161: 'לא בכל המסלולים יש return. הפעולה צריכה להחזיר ערך בכל מסלול',
    CS0165: 'המשתנה עוד לא קיבל ערך. צריך לתת לו ערך התחלתי',
    CS0128: 'המשתנה כבר מוגדר בפעולה הזאת. בחרו שם אחר',
    CS0120: 'אי אפשר לקרוא לפעולה (שאינה static) מתוך פעולה static. הוסיפו static לכותרת שלה',
    CS1061: 'לטיפוס הזה אין פעולה או מאפיין בשם הזה. בדקו את האיות (למשל Push, Pop, IsEmpty)',
    CS1501: 'הפעולה נקראה עם מספר פרמטרים לא מתאים', CS1503: 'הפעולה נקראה עם פרמטר מטיפוס לא מתאים',
    CS0122: 'אין גישה: הוא private במחלקה. ניגשים דרך פעולה (Get...)',
    CS0019: 'אי אפשר להפעיל את הפעולה הזאת על הטיפוסים האלה', CS0017: 'יש בפרויקט יותר מ-Main אחד',
    CS0136: 'כבר יש משתנה בשם הזה בפעולה. בחרו שם אחר', CS0201: 'זו לא פקודה. אולי חסר = או קריאה לפעולה'
};

// ------------------------------------------------------------ הורשה ופולימורפיזם (כמו "המורה בעברית" של Eclipse — HebrewErrors)

/** טיפוסים של Java / של הספרייה — עליהם לא מסבירים על ירושה */
const LIB_TYPES = /^(Stack|Queue|Node|BinNode|String|Integer|Double|Character|Boolean|Object|Math|System|Scanner|ArrayList|List|int|double|char|boolean|long)\b/;

/**
 * עץ ההורשה של הפרויקט, מקבצי המקור (סריקה פשוטה — רק כדי לתת שם למחלקה בהסבר):
 * Map שם-מחלקה → { sup, ifaces, members: Set של שמות פעולות ותכונות שהיא מגדירה, abstract }
 */
function hierarchy(texts) {
    const h = new Map();
    for (const raw of texts || []) {
        const t = String(raw).replace(/\/\*[\s\S]*?\*\//g, ' ').replace(/\/\/[^\n]*/g, ' ').replace(/"(?:\\.|[^"\\])*"/g, '""');
        const re = /\b(abstract\s+)?(class|interface)\s+(\w+)(?:\s*<[^>{]*>)?(?:\s+extends\s+([\w.]+))?(?:\s+implements\s+([\w\s,.<>]+?))?\s*\{/g;
        let m;
        while ((m = re.exec(t))) {
            let depth = 0, i = re.lastIndex - 1, body = '';
            for (; i < t.length; i++) {
                const c = t[i];
                if (c === '{') { depth++; if (depth === 1) continue; }
                if (c === '}') { depth--; if (depth === 0) break; }
                if (depth === 1) body += c;
            }
            const members = new Set();
            for (const x of body.matchAll(/([A-Za-z_]\w*)\s*\(/g)) members.add(x[1]);
            for (const x of body.matchAll(/([A-Za-z_]\w*)\s*(?:=|;|,)/g)) members.add(x[1]);
            h.set(m[3], { sup: m[4] ? m[4].split('.').pop() : (m[2] === 'interface' ? null : null), ifaces: (m[5] || '').split(',').map(s => s.trim().replace(/<.*/, '')).filter(Boolean),
                          members, abstract: !!m[1] || m[2] === 'interface' });
        }
    }
    return h;
}

/** sub יורשת מ-sup (ישירות, דרך מחלקות באמצע, או מממשת) */
function inherits(h, sub, sup) {
    const seen = new Set();
    const up = c => {
        if (!c || seen.has(c)) return false;
        seen.add(c);
        const x = h.get(c);
        if (!x) return false;
        if (x.sup === sup || x.ifaces.includes(sup)) return true;
        return up(x.sup) || x.ifaces.some(up);
    };
    return sub !== sup && up(sub);
}

/** המחלקות שיורשות מ-sup ושמגדירות את member (או כל מחלקה לא מופשטת כש-member ריק) */
function subclassesWith(h, sup, member) {
    const out = [];
    if (!h) return out;
    for (const [name, x] of h) if (inherits(h, name, sup) && (member ? x.members.has(member) : !x.abstract)) out.push(name);
    return out;
}

function commonSuper(h, a, b) {
    for (let x = h.get(a) && h.get(a).sup; x; x = h.get(x) && h.get(x).sup) if (inherits(h, b, x)) return x;
    return null;
}

/** שורות הפירוט של javac שאחרי ההודעה (symbol: / location: / required: / found: ...) → אובייקט */
function details(detail) {
    const d = {};
    for (const l of String(detail || '').split('\n')) {
        const m = /^\s*(symbol|location|required|found|reason)\s*:\s*(.*)$/.exec(l);
        if (m) d[m[1]] = m[2].trim();
        else if (l.trim()) d.extra = (d.extra ? d.extra + ' ' : '') + l.trim();
    }
    return d;
}

/** הסבר להודעה של javac על הורשה ופולימורפיזם, או '' */
function javacPoly(m, detail, h) {
    const d = details(detail);
    let x;
    if (m === 'cannot find symbol' && d.symbol && d.location) {
        const sym = /^(method|variable)\s+(\w+)(\(.*\))?/.exec(d.symbol), loc = /^variable\s+(\w+)\s+of type\s+([\w.]+)/.exec(d.location);
        if (!sym || !loc || LIB_TYPES.test(loc[2])) return '';
        const kind = sym[1] === 'method' ? 'פעולה' : 'תכונה', name = sym[2] + (sym[1] === 'method' ? '()' : ''), v = loc[1], t = loc[2].split('.').pop();
        const sub = subclassesWith(h, t, sym[2])[0];
        if (sub) return `ל-${t} אין ${kind} ${name} — היא רק ב-${sub}. ${v} היא הפניה מסוג ${t}, ודרכה רואים רק את מה שיש ב-${t}. אם היא מצביעה על ${sub} — בודקים עם instanceof וממירים: ((${sub}) ${v}).${sym[2]}${sym[1] === 'method' ? '(...)' : ''}`;
        if (h && h.has(t) || !h) return `ל-${t} אין ${kind} ${name}. בודקים את השם (אותיות גדולות וקטנות). אם היא במחלקה שיורשת מ-${t} — דרך הפניה מסוג ${t} לא רואים אותה; ממירים אחרי בדיקה עם instanceof`;
        return '';
    }
    if ((x = /^incompatible types: ([\w.]+) cannot be converted to ([\w.]+)$/.exec(m)) && h) {
        const f = x[1].split('.').pop(), t = x[2].split('.').pop();
        if (inherits(h, t, f)) return `כל ${t} הוא ${f}, אבל לא כל ${f} הוא ${t}. אם הערך באמת ${t} — ממירים (ואפשר לבדוק קודם עם instanceof): (${t}) ...`;
        const c = commonSuper(h, f, t);
        if (c) return `${f} ו-${t} יורשות שתיהן מ-${c}, אבל ${f} הוא לא ${t}. משתנה מסוג ${c} יכול להצביע על כל אחת מהן`;
        return '';
    }
    if ((x = /^(\w+) is not abstract and does not override abstract method (\w+)\((.*)\) in (\w+)$/.exec(m)))
        return `${x[1]} יורשת מ-${x[4]} את הפעולה המופשטת ${x[2]}(), ולא כתבה אותה. פעולה מופשטת היא הבטחה: כל מחלקה שיורשת חייבת לכתוב אותה (או להיות בעצמה abstract)`;
    if ((x = /^(\w+) is abstract; cannot be instantiated$/.exec(m))) {
        const sub = subclassesWith(h, x[1], '')[0];
        return `${x[1]} מופשטת (abstract) או ממשק — אי אפשר ליצור ממנה עצם. יוצרים עצם של מחלקה שיורשת ממנה, ושומרים אותו בהפניה מסוג ${x[1]}` + (sub ? `: ${x[1]} a = new ${sub}(...);` : '');
    }
    if ((x = /^constructor (\w+) in class (\w+) cannot be applied to given types;?$/.exec(m))) {
        if (/no arguments/.test(d.found || '')) return `ל-${x[1]} אין בנאי בלי ערכים (הבנאי שלה צריך: ${d.required || '...'}). אם זו מחלקה שיורשת מ-${x[1]} — השורה הראשונה בבנאי שלה: super(...) עם הערכים`;
        return `הבנאי של ${x[1]} נקרא עם ערכים לא מתאימים` + (d.required ? ` (הוא צריך: ${d.required})` : '');
    }
    if (/^call to (super|this) must be first statement in constructor$/.test(m))
        return 'הקריאה ל-super(...) חייבת להיות השורה הראשונה בבנאי: קודם נבנה החלק של מחלקת-העל, ורק אחריו החלק של המחלקה שיורשת';
    if ((x = /^(\w+)\((.*)\) in (\w+) cannot override (\w+)\((.*)\) in (\w+)$/.exec(m))) {
        const e = d.extra || '', what = `${x[1]}() ב-${x[3]} דורסת את ${x[4]}() של ${x[6]}`;
        let y;
        if ((y = /weaker access privileges; was (\w+)/.exec(e))) return `${what}, ולכן חייבת להיות ${y[1]} (בדריסה אסור להקטין את ההרשאה)`;
        if ((y = /return type (.+) is not compatible with (.+)/.exec(e))) return `${what}, ולכן חייבת להחזיר ${y[2].trim()} (אם זו פעולה אחרת — תנו לה שם אחר)`;
        if (/overridden method is final/.test(e)) return `אי אפשר לדרוס את ${x[4]}() של ${x[6]}, כי היא final. נותנים לפעולה החדשה שם אחר`;
        return `${x[1]}() ב-${x[3]} לא יכולה לדרוס את ${x[4]}() של ${x[6]}`;
    }
    if ((x = /^cannot inherit from final (\w+)$/.exec(m))) return `אי אפשר לרשת מ-${x[1]}, כי היא final (סופית). אם צריך לרשת — מוחקים את final מהכותרת שלה`;
    if (m === 'method does not override or implement a method from a supertype')
        return 'כתוב @Override, אבל במחלקת-העל אין פעולה כזו. בודקים את השם (אותיות גדולות וקטנות) ואת הפרמטרים, למשל toString()';
    if (m === 'no interface expected here') return 'אחרי extends כותבים מחלקה. לממשק כותבים implements';
    if (m === 'interface expected here') return 'אחרי implements כותבים ממשק. למחלקה כותבים extends';
    return '';
}

/** C# — הודעות של הורשה ופולימורפיזם, עם השמות מההודעה (בלי namespace) */
const N = "'(?:[\\w.]*\\.)?";
const CS_POLY = [
    ['CS0506', new RegExp(`^${N}(\\w+)\\.(\\w+)\\(.*\\)': cannot override inherited member ${N}(\\w+)\\.\\w+\\(`), 'כדי ש-$1 תוכל לדרוס את $2(), צריך לסמן אותה ב-$3 כ-virtual (למשל public virtual)'],
    ['CS0534', new RegExp(`^${N}(\\w+)' does not implement inherited abstract member ${N}(\\w+)\\.(\\w+)\\(`), '$1 יורשת מ-$2 את הפעולה המופשטת $3(), ולא כתבה אותה. מוסיפים ל-$1: public override ... $3(...)'],
    ['CS0535', new RegExp(`^${N}(\\w+)' does not implement interface member ${N}(\\w+)\\.(\\w+)\\(`), '$1 מממשת את $2, ולא כתבה את הפעולה $3()'],
    ['CS0144', new RegExp(`abstract type or interface ${N}(\\w+)'`), '$1 מופשטת (abstract) או ממשק — אי אפשר ליצור ממנה עצם. יוצרים עצם של מחלקה שיורשת ממנה'],
    ['CS0115', new RegExp(`^${N}(\\w+)\\.(\\w+)\\(.*\\)': no suitable method found to override`), 'כתוב override, אבל למחלקת-העל אין פעולה $2() לדרוס. בודקים את השם (אותיות גדולות וקטנות) ואת הפרמטרים'],
    ['CS0507', new RegExp(`^${N}(\\w+)\\.(\\w+)\\(.*\\)': cannot change access modifiers when overriding '(\\w+)'`), '$2() דורסת פעולה של מחלקת-העל, ולכן ההרשאה שלה חייבת להיות $3'],
    ['CS0508', new RegExp(`^${N}(\\w+)\\.(\\w+)\\(.*\\)': return type must be '([^']+)'`), '$2() דורסת פעולה של מחלקת-העל, ולכן חייבת להחזיר $3'],
    ['CS0030', new RegExp(`^Cannot convert type ${N}(\\w+)' to ${N}(\\w+)'`), 'אי אפשר להמיר $1 ל-$2 — אין ביניהן ירושה (למשל מחלקות אחיות). ממירים רק בין מחלקה למחלקה שיורשת ממנה'],
    ['CS7036', new RegExp(`required parameter '(\\w+)' of ${N}(\\w+)\\.\\2\\(`), 'הבנאי של $2 צריך את הערך $1. אם זו מחלקה שיורשת מ-$2 — מוסיפים לבנאי שלה : base(...) עם הערכים'],
    ['CS0122', new RegExp(`^${N}(\\w+)\\.(\\w+)' is inaccessible due to its protection level`), '$2 פרטי (private) ב-$1 — גם מחלקה שיורשת לא רואה אותו. ניגשים דרך מאפיין או פעולה, או משנים ל-protected'],
    ['CS0114', new RegExp(`^${N}(\\w+)\\.(\\w+)\\(.*\\)' hides inherited member ${N}(\\w+)\\.`), '$2() ב-$1 מסתירה את הפעולה של $3 במקום לדרוס אותה: דרך הפניה מסוג $3 תרוץ הגרסה של $3. כדי לדרוס — מוסיפים override'],
    ['CS0108', new RegExp(`^${N}(\\w+)\\.(\\w+)' hides inherited member ${N}(\\w+)\\.`), 'גם ב-$3 יש $2 — לעצם של $1 יהיו שתי תכונות $2 נפרדות. מוחקים את השורה, ואת הערך מעבירים ל-$3 דרך : base(...)'],
    ['CS0513', /is abstract but it is contained in non-abstract type/, 'פעולה מופשטת (abstract) יכולה להיות רק במחלקה מופשטת. מוסיפים abstract לכותרת של המחלקה'],
    ['CS0500', /cannot declare a body because it is marked abstract/, 'לפעולה מופשטת אין גוף — רק כותרת ונקודה-פסיק'],
    ['CS0239', /because it is sealed/, 'אי אפשר לדרוס פעולה sealed (סופית). נותנים לפעולה שם אחר, או מוחקים את sealed'],
    ['CS0509', new RegExp(`cannot derive from sealed type ${N}(\\w+)'`), 'אי אפשר לרשת מ-$1, כי היא sealed (סופית)'],
    ['CS0266', new RegExp(`^Cannot implicitly convert type ${N}([A-Z]\\w*)' to ${N}([A-Z]\\w*)'. An explicit`), 'כל $2 הוא $1, אבל לא כל $1 הוא $2. אם הערך באמת $2 — ממירים (ואפשר לבדוק קודם עם is): ($2)...'],
];

/**
 * ההסבר בעברית להודעה של הקומפיילר, או '' אם אין.
 * detail — שורות הפירוט של javac (symbol: / location: ...); h — עץ ההורשה של הפרויקט (hierarchy), בשביל שם המחלקה בהסבר
 */
function explain(message, code, detail, h) {
    const msg = String(message || '').trim();
    if (code) for (const [c, re, he] of CS_POLY) {
        const x = c === code ? re.exec(msg) : null;
        if (x && !(c === 'CS0266' && /^(Int|Double|Single|Decimal|Char|Byte|Int64|Int16|String)/.test(x[1] + x[2]))) return he.replace(/\$(\d)/g, (s, i) => x[+i] || '');
    }
    if (code && CSC[code]) return CSC[code];
    const m = msg.replace(/^(java|javac):\s*/, '').split('\n')[0].trim();
    const extra = msg.includes('\n') ? msg.split('\n').slice(1).join('\n') : '';
    const poly = javacPoly(m, (detail || '') + (extra ? '\n' + extra : ''), h);
    if (poly) return poly;
    for (const [re, he] of JAVAC) {
        const x = re.exec(m);
        if (x) return he.replace(/\$(\d)/g, (s, i) => x[+i] || '');
    }
    return '';
}

/** "A.java:12: ';' expected" / "Program.cs:7: ; expected   [CS1002]" → { file, line, message, code } (או null) */
function parseError(s) {
    const m = /^(.+?):(\d+):\s*(.*?)(?:\s+\[(CS\d+)\])?\s*$/.exec(String(s || '').trim());
    return m ? { file: m[1], line: +m[2], message: m[3], code: m[4] || '' } : null;
}

// ------------------------------------------------------------ טקסט בעברית עם קוד

const HEB = /[֐-׿]/;
const CODE_LINE = /[;{}=]|\(\s*\)|^\s*(?:public|private|static|int|void|boolean|bool|return|if|while|for|class|new|string|String)\b/;

/** שורה בלי עברית שנראית כמו קוד */
const isCodeLine = l => !HEB.test(l) && /[A-Za-z]/.test(l) && CODE_LINE.test(l);

/**
 * קוד בתוך משפט בעברית: מה שבין `...`, וכל קטע בלי עברית שיש בו אות באנגלית (count(s), Stack<Integer>, x = 5;).
 * מחזיר HTML: הקוד ב-<code dir="ltr">, השאר מוברח.
 */
function inlineHtml(text) {
    const parts = String(text).split(/(`[^`]+`)/);
    let out = '';
    for (const p of parts) {
        if (/^`[^`]+`$/.test(p)) { out += `<code dir="ltr">${esc(p.slice(1, -1))}</code>`; continue; }
        // קטעים בין אותיות עבריות
        const re = /[^֐-׿]+/g;
        let last = 0, m;
        while ((m = re.exec(p))) {
            out += esc(p.slice(last, m.index));
            last = m.index + m[0].length;
            const seg = m[0];
            const list = !/[A-Za-z]/.test(seg) && /\d\s*,\s*\d|[\[(]\s*\d/.test(seg);   // [1, 2, 3] — רשימת מספרים
            if (!/[A-Za-z]/.test(seg) && !list) { out += esc(seg); continue; }
            // בלי רווחים, מקפים ופיסוק בקצוות (ב-Java, "count(s).")
            let a = 0, b = seg.length;
            while (a < b && /[\s\-,.:!?"'(]/.test(seg[a]) && !(seg[a] === '(' && seg.slice(a).includes(')'))) a++;
            while (b > a && /[\s,.:!?"']/.test(seg[b - 1])) b--;
            let core = seg.slice(a, b);
            while (core.endsWith(')') && (core.match(/\(/g) || []).length < (core.match(/\)/g) || []).length) { core = core.slice(0, -1); b--; }
            const wrap = !core ? '' : list ? `<bdi dir="ltr">${esc(core)}</bdi>` : `<code dir="ltr">${esc(core)}</code>`;   // מספרים — רק כיוון, בלי גופן קוד
            out += esc(seg.slice(0, a)) + wrap + esc(seg.slice(b));
        }
        out += esc(p.slice(last));
    }
    return out;
}

/** טקסט של סעיף (יכול להיות בו קוד Java / C#): פסקאות בעברית, ושורות קוד רצופות כ-<pre dir="ltr"> */
function richHtml(text) {
    const lines = String(text || '').replace(/\r/g, '').replace(/```\w*/g, '').split('\n');
    const out = [];
    let code = [], para = [];
    const flushPara = () => { if (para.length) out.push(`<p>${para.map(inlineHtml).join('<br>')}</p>`); para = []; };
    const flushCode = () => {
        while (code.length && !code[code.length - 1].trim()) code.pop();
        if (code.length) {
            const pad = Math.min(...code.filter(l => l.trim()).map(l => l.match(/^\s*/)[0].length));
            out.push(`<pre dir="ltr">${esc(code.map(l => l.slice(pad)).join('\n'))}</pre>`);
        }
        code = [];
    };
    for (const l of lines) {
        if (isCodeLine(l) || (code.length && !HEB.test(l) && (!l.trim() || /^\s/.test(l) || /^[\s{}()\[\];,]*$/.test(l)))) { flushPara(); code.push(l); continue; }
        flushCode();
        if (!l.trim()) { flushPara(); continue; }
        para.push(l.trim());
    }
    flushCode();
    flushPara();
    return out.join('\n');
}

// ------------------------------------------------------------ העיצוב (משותף לכל החלונות)

/**
 * הצבעים מהאתר (site/assets/site.css); בערכת נושא כהה — הצבעים של VS Code עצמו, כדי שהחלון ייראה נכון.
 * פינות: 6px ללוח ולכפתור, 4px לתג. גבול של 1px במקום צל.
 */
const CSS = `
:root { --accent: #2F6FED; --accent-2: #1F56C9; --ok: #2E9E5B; --err: #C4302B; --amber: rgba(232,161,0,.12); --amber-bar: #E8A100;
  --fg: var(--vscode-foreground); --muted: var(--vscode-descriptionForeground); --bg: var(--vscode-editor-background); --panel: var(--vscode-editor-background);
  --line: var(--vscode-widget-border, rgba(128,128,128,.28)); --line-2: var(--vscode-input-border, rgba(128,128,128,.45));
  --sunk: var(--vscode-textCodeBlock-background, rgba(128,128,128,.08)); --link: var(--accent-2);
  --mono: var(--vscode-editor-font-family, ui-monospace, Consolas, monospace); }
body.vscode-light { --line: #DFE3EA; --line-2: #CDD2DA; --sunk: #F2F3F6; --fg: #0E1116; --muted: #646C7A; }
body.vscode-dark, body.vscode-high-contrast { --err: #F2847E; --ok: #4CC17E; --link: #7FA6FF; --amber: rgba(232,161,0,.16); }
* { box-sizing: border-box; }
html { direction: rtl; }
body { margin: 0; padding: 20px 24px; font: 13px/1.6 var(--vscode-font-family); color: var(--fg); background: var(--bg); }
h1 { font-size: 18px; font-weight: 600; margin: 0; letter-spacing: -0.005em; }
.sub { color: var(--muted); margin: 2px 0 16px; }
.panel { background: var(--panel); border: 1px solid var(--line); border-radius: 6px; }
code, pre { font-family: var(--mono); }
code { font-size: 0.92em; background: var(--sunk); border: 1px solid var(--line); border-radius: 4px; padding: 0 4px; unicode-bidi: isolate; }
pre { direction: ltr; text-align: left; unicode-bidi: isolate; margin: 8px 0 0; padding: 10px 12px; background: var(--sunk); border: 1px solid var(--line);
  border-radius: 6px; overflow: auto; font-size: 12.5px; line-height: 1.55; white-space: pre; }
button { font: inherit; border-radius: 6px; padding: 5px 14px; cursor: pointer; border: 1px solid var(--line-2); background: var(--panel); color: var(--fg); }
button:hover { background: var(--sunk); }
button.primary { background: var(--accent); border-color: var(--accent); color: #fff; font-weight: 600; }
button.primary:hover { background: var(--accent-2); border-color: var(--accent-2); }
button:disabled { opacity: .5; cursor: default; }
button:focus-visible, a:focus-visible { outline: 2px solid var(--accent); outline-offset: 2px; }
a, .link { color: var(--link); cursor: pointer; text-decoration: none; }
a:hover, .link:hover { text-decoration: underline; }
.quiet { color: var(--muted); font-size: 12px; }
.tag { display: inline-block; font-size: 12px; font-weight: 600; padding: 0 6px; border-radius: 4px; border: 1px solid var(--line-2); color: var(--fg); }
`;

const page = (title, body, script, cspSource) => `<!DOCTYPE html><html lang="he" dir="rtl"><head><meta charset="UTF-8">
<meta http-equiv="Content-Security-Policy" content="default-src 'none'; img-src data: ${cspSource || ''}; style-src 'unsafe-inline'; script-src 'unsafe-inline';">
<title>${esc(title)}</title><style>${CSS}${body.css || ''}</style></head><body>
${body.html}
${script ? `<script>${script}</script>` : ''}
</body></html>`;

/** "סעיף ד'" / "סעיף 3" מתווית */
const sectionName = l => { const s = String(l || '').trim(); return !s ? 'סעיף' : /^[א-ת]$/.test(s) ? `סעיף ${s}'` : /^\d+$/.test(s) ? `סעיף ${s}` : s; };

/** החלון "הצגה": הסעיפים שעונים עליהם במילים — הטקסט המלא, וקוד שבתוכו משמאל לימין */
function othersHtml(others) {
    const list = (others || []).filter(o => o && String(o.text || '').trim());
    const cards = list.map(o => `<article class="panel part"><div class="tag">${esc(sectionName(o.label))}</div>${richHtml(o.text)}</article>`).join('\n');
    return page('סעיפים במילים', {
        css: `.part { padding: 12px 14px; margin-bottom: 10px; } .part p { margin: 8px 0 0; } .part .tag { margin-bottom: 2px; }`,
        html: `<h1>${list.length === 1 ? 'סעיף שעונים עליו במילים' : 'סעיפים שעונים עליהם במילים'}</h1>
<p class="sub">את התשובה כותבים במילים, לא בקוד.</p>
${cards}`
    });
}

/**
 * החלון "כל השגיאות": כרטיס לכל שגיאה — "קובץ · שורה" (לחיצה פותחת את המקום), ההסבר בעברית, ההודעה המקורית והשורה מהקוד.
 * errors: [{ file, line, message, code?, source? }]
 */
function errorsHtml(errors, title) {
    const list = errors || [];
    const cards = list.map((e, i) => {
        const he = e.he != null ? e.he : explain(e.message, e.code, e.detail);
        return `<article class="panel err">
  <a class="where" data-i="${i}" href="#"><bdi dir="ltr">${esc(e.file)}</bdi> · שורה ${e.line}</a>
  <p class="he">${inlineHtml(he || e.message)}</p>
  ${he ? `<div class="orig" dir="ltr">${esc(e.message)}${e.code ? ' [' + esc(e.code) + ']' : ''}</div>` : ''}
  ${e.source != null && String(e.source).trim() ? `<pre dir="ltr"><span class="n">${e.line}</span>${esc(String(e.source).trim())}</pre>` : ''}
</article>`;
    }).join('\n');
    const n = list.length;
    return page(title || 'שגיאות קומפילציה', {
        css: `.err { padding: 10px 14px 12px; margin-bottom: 10px; border-inline-start: 1px solid var(--line); }
.err .where { font-size: 12px; font-weight: 600; }
.err .he { margin: 4px 0 0; font-weight: 500; }
.err .orig { margin-top: 4px; font: 12px/1.5 var(--mono); color: var(--muted); text-align: left; unicode-bidi: isolate; }
.err pre .n { color: var(--muted); display: inline-block; min-width: 3ch; margin-right: 12px; user-select: none; }
.dot { display: inline-block; width: 8px; height: 8px; border-radius: 50%; background: var(--err); margin-inline-end: 8px; transform: translateY(-1px); }`,
        html: `<h1><span class="dot"></span>${n === 1 ? 'שגיאת קומפילציה אחת' : n + ' שגיאות קומפילציה'}</h1>
<p class="sub">ההרצה לא התחילה. לחיצה על הקובץ פותחת את השורה.</p>
${cards}`
    }, `const vscode = acquireVsCodeApi();
document.querySelectorAll('[data-i]').forEach(a => a.addEventListener('click', e => { e.preventDefault(); vscode.postMessage({ type: 'open', i: +a.dataset.i }); }));`);
}

module.exports = { RLM, rtl, info, warn, error, esc, explain, hierarchy, parseError, inlineHtml, richHtml, isCodeLine, sectionName, CSS, page, othersHtml, errorsHtml };
