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
    [/^(.+) has private access in (.+)$/, '$1 פרטי במחלקה $2. ניגשים אליו דרך פעולה (get...)']
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

/** ההסבר בעברית להודעה של הקומפיילר, או '' אם אין */
function explain(message, code) {
    if (code && CSC[code]) return CSC[code];
    const m = String(message || '').trim().replace(/^(java|javac):\s*/, '').split('\n')[0].trim();
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
        const he = explain(e.message, e.code);
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

module.exports = { RLM, rtl, info, warn, error, esc, explain, parseError, inlineHtml, richHtml, isCodeLine, sectionName, CSS, page, othersHtml, errorsHtml };
