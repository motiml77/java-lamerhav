'use strict';
/**
 * "שליחת תרגיל" ו"הצעה לפתרון" ב-VS Code — אותו מנוע כמו ב-Eclipse: ExercisePipeline שבתוך dsviz.jar, דרך SubmitCli.
 *   - החלון (Webview): מצרפים את דף התרגיל — PDF, Word, תמונות, צילום מסך עם Ctrl+V, גרירה של קבצים — הערה, ושפה (Java / C#).
 *   - SubmitCli שולח לשרת, בונה את הקבצים (Java: src/ex01_..., C#: Exercises/Ex01_...), בודק שהם מתקמפלים, בודק את ההצעות לפתרון
 *     (מריץ את main עם ההצעות ומשווה לפלט הצפוי) ושומר אותן בצד — לא בקוד של התלמיד.
 *   - אחרי "שליחה" אותו חלון עובר לתצוגת הבנייה: השלבים לפי השורות {"progress"} של SubmitCli. בסוף החלון נסגר, הקובץ נפתח בסעיף א
 *     (והקובץ עם main בלשונית לידו), ובפינה הודעה קצרה ("✓ התרגיל נוצר · 3 סעיפים · הסמן בסעיף א"); סעיפים במילים — בכפתור "הצגה".
 *   - פעולה של סעיף שעוד לא כתבו בה כלום מודגשת בעורך (רקע צהוב ופס בצד), רק בקבצים שנוצרו כאן (workspaceState).
 *   - מעל הכותרת של כל פעולה של סעיף: "💡 הצעה לפתרון" (CodeLens) — חלון עם הפתרון, האם נבדק, והעתקה.
 * החלקים בלי vscode (קריאת ההצעות, מציאת הפעולות בקובץ, ה-HTML) — מיוצאים לבדיקות.
 */
const fs = require('fs');
const os = require('os');
const path = require('path');
const cp = require('child_process');
const ui = require('./ui');

// ------------------------------------------------------------ ההצעות שנשמרו (solutions.json — אותו פורמט כמו ב-Eclipse)

function readStore(file) {
    try {
        const j = JSON.parse(fs.readFileSync(file, 'utf8'));
        return (j.solutions || []).filter(e => e && e.code);
    } catch (e) {
        return [];
    }
}

const norm = p => String(p || '').replace(/\\/g, '/').toLowerCase();
/** התיקייה והקובץ: ex01_stacks/StackTargil.java — אם התיקייה הועברה, מוצאים לפי זה */
const tail = p => norm(p).split('/').slice(-2).join('/');

/** ההצעות לקובץ: לפי הנתיב המלא, ואם אין — לפי התיקייה והקובץ (ההצעה האחרונה) */
function forFile(entries, file) {
    const exact = entries.filter(e => norm(e.path) === norm(file));
    if (exact.length) return exact;
    const t = tail(file);
    return entries.filter(e => tail(e.path) === t);
}

/** מספר הפרמטרים ברשימה "Stack<Integer> s, int x" (פסיקים בתוך <> לא נספרים) */
function paramCount(list) {
    const s = String(list || '').trim();
    if (!s) return 0;
    let depth = 0, n = 1;
    for (const ch of s) {
        if (ch === '<' || ch === '(' || ch === '[') depth++;
        else if (ch === '>' || ch === ')' || ch === ']') depth--;
        else if (ch === ',' && depth === 0) n++;
    }
    return n;
}

const DECL = /^\s*(?:(?:public|private|protected|internal|static|final|override|virtual|abstract|synchronized|sealed|new)\s+)*[\w.<>\[\],?]+(?:\s*<[^()]*>)?(?:\[\s*,?\s*\])*\s+([A-Za-z_]\w*)\s*\(([^)]*)\)?/;
const CLASS = /\b(?:class|struct)\s+([A-Za-z_]\w*)/;

/** הכותרות של הפעולות בקובץ: [{line, cls, method, params}] (line מ-0) */
function methods(text) {
    const out = [];
    let cls = '';
    const lines = String(text).split(/\r?\n/);
    for (let i = 0; i < lines.length; i++) {
        const l = lines[i].replace(/\/\/.*$/, '');
        const c = CLASS.exec(l);
        if (c) cls = c[1];
        if (/;\s*$/.test(l) || /\b(?:return|new|if|while|for|switch|catch)\b\s*\(/.test(l.split('(')[0] + '(')) continue;
        const m = DECL.exec(l);
        if (!m || ['if', 'while', 'for', 'switch', 'catch', 'return'].includes(m[1])) continue;
        out.push({ line: i, cls, method: m[1], params: paramCount(m[2]) });
    }
    return out;
}

/** לכל הצעה — השורה של הכותרת בקובץ (או -1) */
function lensesFor(entries, file, text) {
    const ms = methods(text);
    const res = [];
    for (const e of forFile(entries, file)) {
        const n = (e.params || []).length;
        const m = ms.find(x => x.method === e.method && x.params === n && (!e.class || !x.cls || x.cls === e.class))
               || ms.find(x => x.method === e.method && x.params === n);
        if (m) res.push({ line: m.line, entry: e });
    }
    return res;
}

/** ההצעה לפעולה שהסמן בתוכה (השורה מ-0) */
function entryAt(entries, file, text, line) {
    const ls = lensesFor(entries, file, text).sort((a, b) => a.line - b.line);
    let best = null;
    for (const l of ls) if (l.line <= line) best = l;
    return best && best.entry;
}

// ------------------------------------------------------------ SubmitCli

/** מריץ את SubmitCli; כל שורת JSON → onMessage. מחזיר { proc, done: Promise<{last, code}> } */
function runCli(java, jar, args, onMessage, env) {
    const proc = cp.spawn(java, ['-Dfile.encoding=UTF-8', '-Dstdout.encoding=UTF-8', '-Djava.awt.headless=true', '-cp', jar, 'il.lamerhav.dsviz.SubmitCli', ...args],
        { env: Object.assign({}, process.env, env || {}) });
    let buf = '', last = null, err = '';
    proc.stdout.setEncoding('utf8');
    proc.stdout.on('data', d => {
        buf += d;
        let k;
        while ((k = buf.indexOf('\n')) >= 0) {
            const line = buf.slice(0, k).trim();
            buf = buf.slice(k + 1);
            if (!line) continue;
            try {
                const j = JSON.parse(line);
                last = j;
                onMessage && onMessage(j);
            } catch (e) {
                if (line === 'online' || line === 'offline') last = { online: line === 'online' };
            }
        }
    });
    proc.stderr.setEncoding('utf8');
    proc.stderr.on('data', d => { err += d; });
    const done = new Promise(resolve => {
        proc.on('error', e => resolve({ last: { error: 'לא הצלחתי להפעיל את Java: ' + e.message }, code: -1 }));
        proc.on('close', code => resolve({ last: last || (err.trim() ? { error: err.trim().split('\n').filter(l => !/JAVA_TOOL_OPTIONS/.test(l)).slice(-3).join('\n') } : null), code }));
    });
    return { proc, done };
}

// ------------------------------------------------------------ תוך כדי הבנייה: השלבים (מהשורות {"progress": ...} של SubmitCli)

/** השלבים שבחלון, לפי הסדר */
const STAGES = ['מכין את הקבצים', 'שולח ל-Gemini', 'בונה את התרגיל', 'בודק שהקוד מתקמפל', 'בודק את ההצעות לפתרון'];

/**
 * שורת progress של SubmitCli → { stage } (שלב חדש), או { note } (שורה אפורה קטנה מתחת לשלב הנוכחי: "מבקש תיקון…", "בודק שוב…").
 * הפונקציה נכנסת כמו שהיא גם לסקריפט של החלון (toString), אז היא בלי תלויות.
 */
function stageFor(text) {
    const t = String(text || '').trim();
    if (/^מכין את הקבצים/.test(t)) return { stage: 0 };
    if (/^שולח/.test(t)) return { stage: 1 };
    if (/^בונה את התרגיל/.test(t)) return { stage: 2 };
    if (/^בודק שהקוד מתקמפל/.test(t)) return { stage: 3 };
    if (/^בודק שההצעות/.test(t)) return { stage: 4 };
    return { note: t.replace(/\s+—\s+/g, ' · ').replace(/\s*\(בדרך כלל[^)]*\)/, '') };
}

/** שגיאה מ-SubmitCli → שורה אחת קצרה (הטקסט המלא — בריחוף) */
function shortError(s) {
    const first = String(s || '').split(/\r?\n/).map(l => l.trim()).filter(Boolean)[0] || 'לא הצלחתי ליצור את התרגיל.';
    const t = first.replace(/^לא הצלחתי ליצור את התרגיל:\s*/, 'לא הצלחתי ליצור את התרגיל: ').replace(/\s+—\s+/g, ', ');
    return t.length > 150 ? t.slice(0, t.lastIndexOf(' ', 148) > 100 ? t.lastIndexOf(' ', 148) : 148) + '…' : t;
}

// ------------------------------------------------------------ ההודעה אחרי שהתרגיל נוצר

const SHOW = 'הצגה', JUMP = 'מעבר לשגיאה';

/** "סעיף ד: עונים במילים" / "סעיפים ג, ד: עונים במילים" ('' — אין) */
function wordLabels(others) {
    const list = (others || []).filter(o => o && String(o.text || '').trim());
    if (!list.length) return '';
    const labels = list.map(o => String(o.label || '').trim().replace(/'$/, ''));
    if (labels.every(Boolean)) return (labels.length === 1 ? 'סעיף ' : 'סעיפים ') + labels.join(', ') + ': עונים במילים';
    return (list.length === 1 ? 'סעיף אחד' : list.length + ' סעיפים') + ': עונים במילים';
}

/** התווית של הסעיף שהסמן מחכה בו (מתוך שורת הסימון "====כאן עונים סעיף א'====" שמעליו), או '' */
function labelAt(text, line) {
    const ls = String(text || '').split(/\r?\n/);
    for (let i = Math.min(line, ls.length - 1); i >= 0 && i >= line - 4; i--) {
        const m = /כאן עונים סעיף\s*([^=\u202C]+?)\s*\u202C?\s*====/.exec(ls[i]);
        if (m) return m[1].trim().replace(/'$/, '');
    }
    return '';
}

/**
 * מה מציגים אחרי ש-SubmitCli סיים ({done: true, ...}): הודעה קצרה בפינה (לא חלון שחוסם), למשל
 * "✓ התרגיל נוצר · 3 סעיפים · הסמן בסעיף א · סעיף ד: עונים במילים" עם הכפתור "הצגה".
 * cursor — התווית של הסעיף שהסמן בו. מחזיר { warn, text, buttons }
 */
function doneMessage(r, cursor) {
    const errors = r.errors || [], tasks = r.tasks || 0, words = wordLabels(r.others);
    const parts = [];
    if (errors.length) parts.push('התרגיל נוצר', errors.length === 1 ? 'יש שגיאת קומפילציה אחת' : 'יש ' + errors.length + ' שגיאות קומפילציה');
    else if (!tasks) parts.push('✓ התרגיל נוצר', 'אין בו סעיפים בקוד');
    else parts.push('✓ התרגיל נוצר', tasks === 1 ? 'סעיף אחד' : tasks + ' סעיפים', cursor ? 'הסמן בסעיף ' + cursor : 'הסמן במקום התשובה');
    if (words) parts.push(words);
    const buttons = [];
    if (errors.length) buttons.push(JUMP);
    if (words) buttons.push(SHOW);
    return { warn: errors.length > 0, text: parts.join(' · '), buttons };
}

// ------------------------------------------------------------ הסעיפים שעוד לא ענו עליהם (הדגשה בעורך)

const MARKER = /^\s*\/\/\s*====\u202B?\s*כאן (?:עונים סעיף [^=]*|כותבים את התשובה)\s*\u202C?====\s*$/;

/**
 * הפעולות של הסעיפים שבגוף שלהן יש רק את שורת הסימון (ושורות ריקות): [{start, end}] — השורות שבתוך הגוף (מ-0).
 * ברגע שהתלמיד כותב שם משהו, הפעולה כבר לא ברשימה.
 */
function emptyAnswers(text) {
    const ls = String(text || '').split(/\r?\n/), out = [];
    for (let i = 0; i < ls.length; i++) {
        if (!MARKER.test(ls[i])) continue;
        let a = i - 1;
        while (a >= 0 && !ls[a].trim()) a--;
        if (a < 0 || !/\{\s*$/.test(ls[a])) continue;
        let b = i + 1;
        while (b < ls.length && !ls[b].trim()) b++;
        if (b >= ls.length || !/^\s*\}/.test(ls[b])) continue;
        out.push({ start: a + 1, end: b - 1 });
    }
    return out;
}

/** הקובץ עם main / Main מבין הקבצים (או null) */
const MAIN_RE = /\bstatic\s+(?:void|int|async\s+Task)\s+[Mm]ain\s*\(/;
function mainFile(files) {
    for (const f of files || []) {
        try { if (MAIN_RE.test(fs.readFileSync(f, 'utf8'))) return f; } catch (e) { /* אין קובץ */ }
    }
    return null;
}

// ------------------------------------------------------------ השמות שכבר תפוסים (כדי שהחבילה / הפרויקט החדש לא יתנגשו)

/**
 * Java: שמות התיקיות שבראש כל תיקיית קוד בסביבה — התיקיות הפתוחות עצמן, src, תיקיית הקוד של הפרויקט (sourceDir) ו-java.project.sourcePaths.
 * C#: שמות הפרויקטים בכל .sln, והתיקיות שבתיקיית התרגילים.
 * opts: { sourceDir(root), exercisesDir(root), sourcePaths: [] }
 */
function takenNames(lang, roots, opts) {
    const o = opts || {}, out = new Set();
    const dirsOf = d => {
        try { for (const e of fs.readdirSync(d, { withFileTypes: true })) if (e.isDirectory() && !e.name.startsWith('.')) out.add(e.name); } catch (e) { /* אין תיקייה */ }
    };
    for (const root of new Set((roots || []).filter(Boolean))) {
        if (lang === 'csharp') {
            let files = [];
            try { files = fs.readdirSync(root).filter(f => /\.sln$/i.test(f)); } catch (e) { /* אין */ }
            for (const f of files) {
                try {
                    const re = /^\s*Project\("[^"]*"\)\s*=\s*"([^"]+)"/gm;
                    const t = fs.readFileSync(path.join(root, f), 'utf8');
                    for (let m; (m = re.exec(t));) out.add(m[1]);
                } catch (e) { /* לא נקרא */ }
            }
            dirsOf(o.exercisesDir ? o.exercisesDir(root) : path.join(root, 'Exercises'));
        } else {
            const src = new Set([root, path.join(root, 'src')]);
            if (o.sourceDir) { try { src.add(o.sourceDir(root)); } catch (e) { /* בלי */ } }
            for (const sp of o.sourcePaths || []) if (typeof sp === 'string' && sp) src.add(path.resolve(root, sp));
            for (const d of src) dirsOf(d);
        }
    }
    return [...out].sort();
}

// ------------------------------------------------------------ החלונות (HTML)

const esc = ui.esc;

/** הבסיס של החלון "הצעה לפתרון" (העיצוב המשותף מ-ui.js) */
const BASE_CSS = ui.CSS + `
.foot { display: flex; align-items: center; gap: 8px; margin-top: 16px; }
.foot .copy { margin-inline-start: auto; color: var(--muted); font-size: 11px; }
`;

/**
 * החלון "שליחת תרגיל": הטופס (דף התרגיל, הערה, שפה), ואחרי "שליחה" — באותו חלון — תצוגת הבנייה:
 * התמונות של הקבצים, רשימת השלבים (✓ ירוק / נקודה כחולה עם שניות / אפור), קו כחול דק למעלה, ורק "ביטול".
 */
function submitHtml(opts) {
    const lang = opts.lang === 'csharp' ? 'csharp' : 'java';
    const css = `
.topline { position: fixed; top: 0; right: 0; left: 0; height: 2px; }
.topline i { display: block; height: 100%; width: 0; background: var(--accent); transition: width .4s cubic-bezier(.22,1,.36,1); }
main { max-width: 760px; }
.drop { padding: 12px; border: 1px dashed var(--line-2); border-radius: 6px; display: grid; gap: 10px; }
.drop.over { border-color: var(--accent); background: rgba(47,111,237,.06); }
.items { display: flex; flex-wrap: wrap; gap: 8px; }
.items:empty { display: none; }
.item { position: relative; width: 104px; border: 1px solid var(--line); border-radius: 6px; background: var(--panel); padding: 6px; display: grid; gap: 4px; }
.item .thumb { height: 64px; display: flex; align-items: center; justify-content: center; overflow: hidden; background: var(--sunk); border-radius: 4px; }
.item .thumb img { max-width: 100%; max-height: 64px; display: block; }
.item .badge { font: 600 11px/1 var(--mono); color: #fff; border-radius: 4px; padding: 6px 8px; }
.item .name { font-size: 11px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; color: var(--muted); text-align: center; }
.item .x { position: absolute; top: 3px; left: 3px; width: 20px; height: 20px; padding: 0; line-height: 18px; font-size: 14px; border-radius: 4px; }
.empty { text-align: center; padding: 14px 0 4px; }
.empty b { display: block; font-weight: 600; }
.empty span { color: var(--muted); font-size: 12px; }
.row { display: flex; gap: 8px; align-items: center; justify-content: center; flex-wrap: wrap; }
.summary { color: var(--muted); font-size: 12px; text-align: center; }
.summary:empty { display: none; }
.field { display: grid; gap: 4px; margin-top: 14px; }
.field > span { font-weight: 600; }
textarea { width: 100%; min-height: 52px; font: inherit; color: var(--vscode-input-foreground, var(--fg)); background: var(--vscode-input-background, var(--panel));
  border: 1px solid var(--line-2); border-radius: 6px; padding: 6px 8px; resize: vertical; }
textarea:focus, select:focus { outline: 1px solid var(--accent); outline-offset: -1px; }
.opts { display: flex; align-items: center; gap: 8px; margin-top: 10px; }
.opts label { font-weight: 600; }
select { font: inherit; padding: 3px 8px; border-radius: 6px; color: var(--vscode-dropdown-foreground, var(--fg)); background: var(--vscode-dropdown-background, var(--panel)); border: 1px solid var(--line-2); }
.where { color: var(--muted); font-size: 12px; }
.quiet { margin: 12px 0 0; }
.ferr { color: var(--err); margin: 10px 0 0; }
.ferr:empty { display: none; }
.ferr.info { color: var(--muted); }
footer { display: flex; align-items: center; gap: 8px; margin-top: 16px; }
footer .copy { margin-inline-start: auto; color: var(--muted); font-size: 11px; }
#build .thumbs { display: flex; gap: 8px; flex-wrap: wrap; margin-bottom: 12px; }
#build .item { width: 84px; }
#build .item .thumb { height: 52px; }
#build .item .x { display: none; }
.stages { list-style: none; margin: 0; padding: 12px 14px; display: grid; gap: 6px; }
.stages li { display: grid; grid-template-columns: 16px auto 1fr; column-gap: 10px; align-items: baseline; color: var(--muted); }
.stages .ic { display: inline-flex; width: 16px; justify-content: center; }
.stages .ic::before { content: ""; width: 7px; height: 7px; border-radius: 50%; border: 1.5px solid currentColor; opacity: .55; transform: translateY(-1px); }
.stages li.done { color: var(--fg); }
.stages li.done .ic::before { content: "✓"; width: auto; height: auto; border: 0; border-radius: 0; color: var(--ok); font-weight: 700; opacity: 1; transform: none; }
.stages li.now { color: var(--fg); font-weight: 600; }
.stages li.now .ic::before { background: var(--accent); border-color: var(--accent); opacity: 1; animation: pulse 1.4s ease-in-out infinite alternate; }
.stages li.bad { color: var(--fg); }
.stages li.bad .ic::before { content: "✕"; width: auto; height: auto; border: 0; color: var(--err); font-weight: 700; opacity: 1; transform: none; animation: none; }
.stages .sec { font-size: 12px; font-weight: 400; color: var(--muted); font-variant-numeric: tabular-nums; unicode-bidi: isolate; }
.stages .note { grid-column: 2 / 4; font-size: 12px; font-weight: 400; color: var(--muted); }
.stages .note:empty { display: none; }
@keyframes pulse { from { opacity: 1; } to { opacity: .45; } }
@media (prefers-reduced-motion: reduce) { .stages li.now .ic::before { animation: none; } .topline i { transition: none; } }
.fail { display: flex; align-items: center; gap: 12px; margin-top: 12px; }
.fail p { margin: 0; color: var(--err); }
.fail[hidden] { display: none; }
`;
    const html = `<div class="topline" id="topline" hidden><i id="bar"></i></div>
<main>
<section id="form">
  <h1>שליחת תרגיל</h1>
  <p class="sub">מצרפים את דף התרגיל, ומקבלים קובץ מוכן: פעולה ריקה לכל סעיף ו-main לבדיקה.</p>
  <div class="drop" id="drop">
    <div class="items" id="items"></div>
    <div class="empty" id="empty"><b>גוררים לכאן את דף התרגיל</b><span>PDF, Word או תמונה. צילום מסך: Ctrl+V</span></div>
    <div class="row"><button id="pick">בחירת קבצים…</button><button id="paste">הדבקה (Ctrl+V)</button></div>
    <div class="summary" id="summary"></div>
  </div>
  <label class="field"><span>הערה (לא חובה)</span><textarea id="note" placeholder='למשל: "רק שאלה 2"'></textarea></label>
  <div class="opts"><label for="lang">שפה</label>
    <select id="lang"><option value="java"${lang === 'java' ? ' selected' : ''}>Java</option><option value="csharp"${lang === 'csharp' ? ' selected' : ''}>C#</option></select>
    <span class="where">${esc(opts.where || '')}</span></div>
  <p class="quiet">הדף עובר דרך השרת של DSViz ל-Gemini של Google. לא לשלוח דפים עם פרטים אישיים.</p>
  <p class="ferr" id="status"></p>
  <footer><button class="primary" id="send">שליחה ויצירת התרגיל</button><button id="cancel">ביטול</button><span class="copy">© 2026 Moti Levi · motiml77@gmail.com</span></footer>
</section>
<section id="build" hidden>
  <h1>בונה את התרגיל</h1>
  <p class="sub">בדרך כלל עד דקה. בסוף הקובץ נפתח בסעיף הראשון.</p>
  <div class="thumbs" id="thumbs"></div>
  <ol class="stages panel" id="stages">${STAGES.map(s => `<li><span class="ic"></span><span class="t">${esc(s)}</span><span class="sec"></span><span class="note"></span></li>`).join('')}</ol>
  <div class="fail" id="fail" hidden><p id="ftext"></p><button class="primary" id="retry">נסו שוב</button></div>
  <footer><button id="stop">ביטול</button></footer>
</section>
</main>`;
    const script = `
const vscode = acquireVsCodeApi();
let items = [], cur = -1, t0 = 0, timer = null;
const $ = id => document.getElementById(id);
const lis = [...document.querySelectorAll('#stages li')];
const COLORS = { pdf: '#C4302B', word: '#2F6FED', text: '#646C7A' };
${stageFor.toString()}
function size(n) { return n < 1024 * 1024 ? Math.max(1, Math.round(n / 1024)) + 'KB' : (n / 1024 / 1024).toFixed(1) + 'MB'; }
function itemEl(it) {
  const d = document.createElement('div'); d.className = 'item';
  const th = document.createElement('div'); th.className = 'thumb';
  if (it.thumb) { const im = document.createElement('img'); im.src = it.thumb; im.alt = ''; th.appendChild(im); }
  else { const b = document.createElement('span'); b.className = 'badge'; b.style.background = COLORS[it.kind] || COLORS.text; b.textContent = it.kind === 'pdf' ? 'PDF' : it.kind === 'word' ? 'Word' : 'TXT'; th.appendChild(b); }
  const n = document.createElement('div'); n.className = 'name'; n.textContent = it.name; n.title = it.name;
  const x = document.createElement('button'); x.className = 'x'; x.textContent = '×'; x.title = 'הסרה'; x.setAttribute('aria-label', 'הסרה');
  x.onclick = () => vscode.postMessage({ type: 'remove', id: it.id });
  d.append(th, n, x);
  return d;
}
function render() {
  $('items').replaceChildren(...items.map(itemEl));
  $('empty').style.display = items.length ? 'none' : 'block';
  const files = items.filter(i => i.kind !== 'image').length, imgs = items.length - files;
  const total = items.reduce((a, i) => a + (i.size || 0), 0);
  $('summary').textContent = items.length ? [files ? (files === 1 ? 'קובץ אחד' : files + ' קבצים') : '', imgs ? (imgs === 1 ? 'תמונה אחת' : imgs + ' תמונות') : ''].filter(Boolean).join(' ו-') + ' · ' + size(total) : '';
  $('send').disabled = !items.length;
}
function view(name) {
  $('form').hidden = name !== 'form'; $('build').hidden = name !== 'build'; $('topline').hidden = name !== 'build';
  if (name !== 'build') { clearInterval(timer); timer = null; }
}
function tick() { const li = lis[cur]; if (!li || !li.classList.contains('now')) return; const s = Math.floor((Date.now() - t0) / 1000); li.querySelector('.sec').textContent = s >= 1 ? s + " שנ'" : ''; }
function setStage(i) {
  if (i < cur) return;
  cur = i; t0 = Date.now();
  lis.forEach((li, k) => { li.className = k < i ? 'done' : k === i ? 'now' : ''; li.querySelector('.sec').textContent = ''; if (k <= i) li.querySelector('.note').textContent = ''; });
  $('bar').style.width = Math.round((i + 0.5) / lis.length * 100) + '%';
}
function build() {
  cur = -1; $('thumbs').replaceChildren(...items.map(itemEl)); $('fail').hidden = true; $('ftext').textContent = '';
  lis.forEach(li => { li.className = ''; li.querySelector('.sec').textContent = ''; li.querySelector('.note').textContent = ''; });
  view('build'); setStage(0);
  clearInterval(timer); timer = setInterval(tick, 500);
}
function progress(text) {
  const r = stageFor(text);
  if (r.stage != null) setStage(r.stage);
  else if (r.note && lis[Math.max(cur, 0)]) lis[Math.max(cur, 0)].querySelector('.note').textContent = r.note;
}
function failed(text) {
  clearInterval(timer); timer = null;
  const li = lis[Math.max(cur, 0)]; li.className = 'bad'; li.querySelector('.sec').textContent = '';
  $('ftext').textContent = text; $('ftext').title = text; $('fail').hidden = false; $('retry').focus();
}
function readAs(blob) { return new Promise(r => { const f = new FileReader(); f.onload = () => r(String(f.result).split(',')[1]); f.readAsDataURL(blob); }); }
async function addBlob(blob, name, kind) { vscode.postMessage({ type: 'data', name, kind, data: await readAs(blob) }); }
const formOn = () => !$('form').hidden;
document.addEventListener('paste', async e => {
  if (!formOn()) return;
  const cd = e.clipboardData; if (!cd) return;
  let any = false;
  for (const it of cd.items) {
    if (it.kind === 'file') { const f = it.getAsFile(); if (f) { any = true; await addBlob(f, f.name && !/^image\\.png$/i.test(f.name) ? f.name : '', /^image\\//.test(f.type) ? 'image' : ''); } }
  }
  if (!any && document.activeElement !== $('note')) {
    const t = cd.getData('text/plain'); if (t && t.trim()) { any = true; vscode.postMessage({ type: 'text', text: t }); }
  }
  if (any) e.preventDefault();
});
$('paste').onclick = async () => {
  try {
    const list = await navigator.clipboard.read();
    for (const ci of list) for (const t of ci.types) if (t.startsWith('image/')) { await addBlob(await ci.getType(t), '', 'image'); return; }
    const t = await navigator.clipboard.readText(); if (t && t.trim()) { vscode.postMessage({ type: 'text', text: t }); return; }
    vscode.postMessage({ type: 'say', text: 'בלוח אין תמונה. מצלמים את התרגיל (Win+Shift+S) ואז Ctrl+V כאן.' });
  } catch (err) { vscode.postMessage({ type: 'say', text: 'לוחצים Ctrl+V בתוך החלון כדי להדביק צילום מסך.' }); }
};
const drop = $('drop');
drop.addEventListener('dragover', e => { e.preventDefault(); drop.classList.add('over'); });
drop.addEventListener('dragleave', () => drop.classList.remove('over'));
drop.addEventListener('drop', async e => {
  e.preventDefault(); drop.classList.remove('over'); if (!formOn()) return;
  const uris = e.dataTransfer.getData('text/uri-list');
  if (uris && !e.dataTransfer.files.length) { vscode.postMessage({ type: 'uris', uris }); return; }
  for (const f of e.dataTransfer.files) await addBlob(f, f.name, '');
});
const sendNow = () => vscode.postMessage({ type: 'send', note: $('note').value, lang: $('lang').value });
$('pick').onclick = () => vscode.postMessage({ type: 'pick' });
$('cancel').onclick = () => vscode.postMessage({ type: 'cancel' });
$('send').onclick = sendNow;
$('retry').onclick = sendNow;
$('stop').onclick = () => vscode.postMessage({ type: 'stop' });
window.addEventListener('message', e => {
  const m = e.data;
  if (m.type === 'items') { items = m.items; render(); }
  else if (m.type === 'status') { const s = $('status'); s.className = 'ferr' + (m.bad ? '' : ' info'); s.textContent = m.text || ''; }
  else if (m.type === 'build') build();
  else if (m.type === 'progress') progress(m.text);
  else if (m.type === 'failed') failed(m.text);
  else if (m.type === 'form') view('form');
});
render();
vscode.postMessage({ type: 'ready' });
`;
    return ui.page('שליחת תרגיל', { css, html }, script, opts.cspSource);
}

// הדגשה פשוטה של קוד Java / C# (כמו בעורך)
const KW = new Set(('public private protected internal static void int long double float boolean bool char string new return if else while for foreach in do break continue '
    + 'class null true false this final var out ref const switch case default try catch throw').split(' '));
function highlight(code) {
    const re = /("(?:[^"\\]|\\.)*"|'(?:[^'\\]|\\.)*'|\/\/[^\n]*|\b[A-Za-z_]\w*\b|\d+(?:\.\d+)?|\s+|.)/g;
    let out = '', m;
    while ((m = re.exec(code))) {
        const w = m[0];
        if (w.startsWith('//')) out += `<span class="cm">${esc(w)}</span>`;
        else if (w[0] === '"' || w[0] === "'") out += `<span class="st">${esc(w)}</span>`;
        else if (KW.has(w)) out += `<span class="kw">${w}</span>`;
        else if (/^\d/.test(w)) out += `<span class="nu">${w}</span>`;
        else if (/^[A-Z]/.test(w)) out += `<span class="ty">${w}</span>`;
        else out += esc(w);
    }
    return out;
}

const STATUS = {
    ok: ['✓  נבדק: הרצנו את main עם ההצעה הזו, והודפס בדיוק מה שצריך להיות מודפס.', 'ok'],
    ran: ['✓  ההצעה התקמפלה ורצה בלי שגיאות (בתרגיל לא היה פלט צפוי להשוואה).', 'ran'],
    failed: ['⚠  לא הצלחנו לוודא שההצעה הזו נותנת בדיוק את הפלט הצפוי. בדקו אותה בעצמכם.', 'failed'],
    unchecked: ['ההצעה לא נבדקה במחשב. כדאי להריץ ולבדוק.', 'unchecked']
};

/** "סעיף א'" מהתווית */
function section(label) {
    const l = String(label || '').trim();
    if (!l) return '';
    return /^[א-ת]$/.test(l) ? `סעיף ${l}'` : /^\d+$/.test(l) ? `סעיף ${l}` : l;
}

/** החלון "הצעה לפתרון" — כמו SolutionDialog של Eclipse */
function solutionHtml(entry, bulbUri) {
    const [text, kind] = STATUS[entry.status] || STATUS.unchecked;
    const s = section(entry.label);
    return `<!DOCTYPE html><html lang="he" dir="rtl"><head><meta charset="UTF-8">
<meta http-equiv="Content-Security-Policy" content="default-src 'none'; img-src data: vscode-resource: https:; style-src 'unsafe-inline'; script-src 'unsafe-inline';">
<style>${BASE_CSS}
.head { display: flex; gap: 12px; align-items: flex-start; margin-bottom: 10px; }
.bulb { font-size: 26px; line-height: 1.1; }
.pill { border: 1px solid var(--line); border-radius: 6px; padding: 6px 10px; margin-bottom: 10px; }
.pill.ok { color: var(--ok); } .pill.failed { color: #B26A00; } body.vscode-dark .pill.failed { color: #E8A100; } .pill.unchecked { color: var(--muted); }
pre { margin: 0; padding: 12px 14px; max-height: 62vh; font-size: 13px; user-select: text; }
.kw { color: #7f0055; font-weight: 600; } .st { color: #2a00ff; } .cm { color: #3f7f5f; } .nu { color: #098658; } .ty { color: #267f99; }
body.vscode-dark .kw { color: #c586c0; } body.vscode-dark .st { color: #ce9178; } body.vscode-dark .cm { color: #6a9955; } body.vscode-dark .nu { color: #b5cea8; } body.vscode-dark .ty { color: #4ec9b0; }
.cm { unicode-bidi: plaintext; }
</style></head><body>
<div class="head"><div class="bulb">💡</div><div><h1>הצעה לפתרון${s ? ' · ' + esc(s) : ''}</h1>
<p class="sub">כדאי לנסות קודם לבד. נתקעתם? הנה דרך אחת לפתור, ויש גם דרכים נכונות אחרות.</p></div></div>
<div class="pill ${kind}">${esc(text)}</div>
<pre id="code">${highlight(entry.code || '')}</pre>
<div class="foot"><button class="primary" id="copy">העתקה</button><button id="close">סגירה</button></div>
<script>
const vscode = acquireVsCodeApi();
document.getElementById('copy').onclick = () => {
  const sel = String(window.getSelection() || '');
  vscode.postMessage({ type: 'copy', text: sel.trim() ? sel : null });
};
document.getElementById('close').onclick = () => vscode.postMessage({ type: 'close' });
</script></body></html>`;
}

// ------------------------------------------------------------ בתוך VS Code

const DECO_KEY = 'dsviz.submittedFiles';

/**
 * deps: { vscode, ctx, chooseRoot, detectLanguage, project, cs, runner, openFiles, onManifest? (לבדיקות) }
 */
function register(deps) {
    const { vscode, ctx } = deps;
    const store = () => path.join(ctx.globalStorageUri ? ctx.globalStorageUri.fsPath : path.join(os.homedir(), '.dsviz'), 'solutions.json');
    const jar = () => path.join(ctx.extensionPath, 'resources', 'dsviz.jar');
    const javaExe = () => {
        const j = deps.runner.findJava(vscode.workspace.getConfiguration('dsviz').get('javaHome'));
        return j ? j.java : 'java';
    };

    // --- 💡 מעל כל פעולה של סעיף
    const changed = new vscode.EventEmitter();
    let cache = { at: -1, entries: [] };
    const entries = () => {
        let m = 0;
        try { m = fs.statSync(store()).mtimeMs; } catch (e) { m = 0; }
        if (m !== cache.at) cache = { at: m, entries: m ? readStore(store()) : [] };
        return cache.entries;
    };
    ctx.subscriptions.push(vscode.languages.registerCodeLensProvider([{ language: 'java' }, { language: 'csharp' }], {
        onDidChangeCodeLenses: changed.event,
        provideCodeLenses(doc) {
            const all = entries();
            if (!all.length) return [];
            return lensesFor(all, doc.uri.fsPath, doc.getText()).map(l => new vscode.CodeLens(new vscode.Range(l.line, 0, l.line, 0),
                { title: '💡 הצעה לפתרון', tooltip: 'הפתרון שהוצע לסעיף. נפתח בחלון נפרד (לא נכנס לקוד)', command: 'dsviz.solution', arguments: [l.entry] }));
        }
    }));

    let solutionPanel = null;
    const showSolution = entry => {
        if (!entry) {
            const ed = vscode.window.activeTextEditor;
            if (ed) entry = entryAt(entries(), ed.document.uri.fsPath, ed.document.getText(), ed.selection.active.line);
        }
        if (!entry) {
            ui.info(vscode, 'הצעה לפתרון יש לסעיפים של תרגיל מ"שליחת תרגיל". שמים את הסמן בתוך הפעולה, או לוחצים על 💡 שמעליה.');
            return;
        }
        const s = section(entry.label);
        if (!solutionPanel) {
            solutionPanel = vscode.window.createWebviewPanel('dsviz.solution', 'הצעה לפתרון', vscode.ViewColumn.Beside, { enableScripts: true });
            solutionPanel.onDidDispose(() => { solutionPanel = null; });
            solutionPanel.webview.onDidReceiveMessage(m => {
                if (m.type === 'copy') {
                    vscode.env.clipboard.writeText(m.text || solutionPanel.entry.code);
                    vscode.window.setStatusBarMessage(m.text ? 'הקטע שסומן הועתק' : 'ההצעה הועתקה. אפשר להדביק (Ctrl+V)', 3000);
                } else if (m.type === 'close') solutionPanel.dispose();
            });
        }
        solutionPanel.entry = entry;
        solutionPanel.title = 'הצעה לפתרון' + (s ? ' · ' + s : '');
        solutionPanel.webview.html = solutionHtml(entry);
        solutionPanel.reveal(vscode.ViewColumn.Beside);
    };

    // --- הסעיפים שעוד לא ענו עליהם: רקע צהוב ופס בצד (רק בקבצים שנוצרו ב"שליחת תרגיל")
    const tracked = new Set(((ctx.workspaceState && ctx.workspaceState.get(DECO_KEY)) || []).map(norm));
    const deco = vscode.window.createTextEditorDecorationType ? vscode.window.createTextEditorDecorationType({
        isWholeLine: true,
        backgroundColor: 'rgba(232,161,0,.12)',
        borderColor: '#E8A100', borderStyle: 'solid', borderWidth: '0 0 0 3px',
        overviewRulerColor: '#E8A100',
        overviewRulerLane: vscode.OverviewRulerLane ? vscode.OverviewRulerLane.Left : 1,
        dark: { backgroundColor: 'rgba(232,161,0,.16)' }
    }) : null;
    if (deco) ctx.subscriptions.push(deco);
    const decorate = ed => {
        if (!deco || !ed || !ed.document || !ed.setDecorations || !ed.document.uri || !tracked.has(norm(ed.document.uri.fsPath))) return;
        ed.setDecorations(deco, emptyAnswers(ed.document.getText()).map(r => new vscode.Range(r.start, 0, r.end, 0)));
    };
    const decorateAll = () => (vscode.window.visibleTextEditors || [vscode.window.activeTextEditor]).forEach(decorate);
    const timers = new Map();
    const soon = doc => {
        if (!doc || !doc.uri || !tracked.has(norm(doc.uri.fsPath))) return;
        const k = norm(doc.uri.fsPath);
        clearTimeout(timers.get(k));
        timers.set(k, setTimeout(() => {
            timers.delete(k);
            (vscode.window.visibleTextEditors || [vscode.window.activeTextEditor]).filter(e => e && e.document === doc).forEach(decorate);
        }, 250));
    };
    const w = vscode.window, wsp = vscode.workspace;
    if (w.onDidChangeActiveTextEditor) ctx.subscriptions.push(w.onDidChangeActiveTextEditor(decorate));
    if (w.onDidChangeVisibleTextEditors) ctx.subscriptions.push(w.onDidChangeVisibleTextEditors(eds => (eds || []).forEach(decorate)));
    if (wsp.onDidChangeTextDocument) ctx.subscriptions.push(wsp.onDidChangeTextDocument(e => soon(e.document)));
    decorateAll();
    const remember = files => {
        for (const f of files || []) tracked.add(norm(f));
        if (ctx.workspaceState) ctx.workspaceState.update(DECO_KEY, [...tracked]);
    };

    // --- אחרי שהתרגיל נוצר: הודעה קצרה; "הצגה" — הסעיפים במילים; "מעבר לשגיאה" — השגיאה הראשונה
    const problems = vscode.languages.createDiagnosticCollection ? vscode.languages.createDiagnosticCollection('dsviz-submit') : null;
    if (problems) ctx.subscriptions.push(problems);
    let othersPanel = null;
    const showOthers = others => {
        if (othersPanel) { try { othersPanel.dispose(); } catch (e) { /* כבר נסגר */ } }
        othersPanel = vscode.window.createWebviewPanel('dsviz.others', 'סעיפים במילים', { viewColumn: vscode.ViewColumn.Beside, preserveFocus: true }, { enableScripts: false });
        othersPanel.webview.html = ui.othersHtml(others);
        othersPanel.onDidDispose(() => { othersPanel = null; });
        return othersPanel;
    };
    const fileOf = (files, name) => (files || []).find(f => path.basename(f) === name) || (files || []).find(f => norm(f).endsWith('/' + norm(name)));
    const openAtLine = async (file, line) => {
        const doc = await vscode.workspace.openTextDocument(file);
        const pos = new vscode.Position(Math.max(0, line - 1), 0);
        return vscode.window.showTextDocument(doc, { preview: false, selection: new vscode.Range(pos.line, 0, pos.line, 0) });
    };
    const showDone = r => {
        let text = '';
        try { text = fs.readFileSync(r.focus, 'utf8'); } catch (e) { /* בלי */ }
        const errs = (r.errors || []).map(ui.parseError).filter(Boolean);
        if (problems) {
            problems.clear();
            const by = new Map();
            for (const e of errs) {
                const f = fileOf(r.files, e.file);
                if (!f) continue;
                const d = new vscode.Diagnostic(new vscode.Range(e.line - 1, 0, e.line - 1, 1000), ui.rtl(ui.explain(e.message, e.code) || e.message),
                    vscode.DiagnosticSeverity.Error);
                d.source = 'DSViz';
                if (!by.has(f)) by.set(f, []);
                by.get(f).push(d);
            }
            for (const [f, l] of by) problems.set(vscode.Uri.file(f), l);
        }
        const msg = doneMessage(r, labelAt(text, r.line || 0));
        return (msg.warn ? ui.warn : ui.info)(vscode, msg.text, ...msg.buttons).then(pick => {
            if (pick === SHOW) return showOthers(r.others);
            if (pick === JUMP && errs.length) {
                const f = fileOf(r.files, errs[0].file);
                if (f) return openAtLine(f, errs[0].line);
            }
            return null;
        });
    };

    // --- שליחת תרגיל
    const submit = async () => {
        const root = await deps.chooseRoot();
        if (!root) return;
        // בלי אינטרנט אין טעם לפתוח את החלון: אומרים את זה מיד, בשורה אחת
        const online = await vscode.window.withProgress({ location: vscode.ProgressLocation.Window, title: 'בודק חיבור לאינטרנט…' },
            () => runCli(javaExe(), jar(), ['online'], null).done);
        if (online.code === 1) {
            const again = 'נסו שוב';
            const pick = await ui.warn(vscode, 'אין חיבור לאינטרנט. שליחת תרגיל צריכה אינטרנט (שאר הכלים עובדים גם בלי).', again);
            if (pick === again) return submit();
            return;
        }
        let lang = deps.detectLanguage(root) || 'java';
        const tmp = fs.mkdtempSync(path.join(os.tmpdir(), 'dsviz-submit-'));
        const items = [];
        let nextId = 1, pastedImages = 0, pastedTexts = 0;
        const panel = vscode.window.createWebviewPanel('dsviz.submit', 'שליחת תרגיל', vscode.ViewColumn.Active, { enableScripts: true, retainContextWhenHidden: true });
        panel.webview.html = submitHtml({ lang, where: path.basename(root), cspSource: panel.webview.cspSource });
        const post = m => panel.webview.postMessage(m);
        const kindOf = name => /\.pdf$/i.test(name) ? 'pdf' : /\.docx?m?$/i.test(name) ? 'word' : /\.(txt|java)$/i.test(name) ? 'text' : 'image';
        const add = (file, name) => {
            const kind = kindOf(name || file);
            const it = { id: nextId++, file, name: name || path.basename(file), kind, size: fs.statSync(file).size };
            if (kind === 'image' && it.size < 6 * 1024 * 1024) {
                const ext = path.extname(file).slice(1).toLowerCase();
                it.thumb = 'data:image/' + (ext === 'jpg' ? 'jpeg' : ext || 'png') + ';base64,' + fs.readFileSync(file).toString('base64');
            }
            items.push(it);
        };
        const refresh = () => post({ type: 'items', items: items.map(({ id, name, kind, size, thumb }) => ({ id, name, kind, size, thumb })) });
        let child = null, sending = false, stopped = false;
        panel.onDidDispose(() => { if (child) child.kill(); try { fs.rmSync(tmp, { recursive: true, force: true }); } catch (e) { /* בסדר */ } });
        panel.webview.onDidReceiveMessage(async m => {
            if (m.type === 'pick') {
                const uris = await vscode.window.showOpenDialog({ canSelectMany: true, openLabel: 'צירוף', filters: { 'דף התרגיל': ['pdf', 'docx', 'png', 'jpg', 'jpeg', 'webp', 'gif', 'bmp', 'txt'], 'הכול': ['*'] } });
                for (const u of uris || []) add(u.fsPath);
                refresh();
            } else if (m.type === 'uris') {
                for (const u of String(m.uris).split(/\r?\n/)) if (u.startsWith('file:')) try { add(vscode.Uri.parse(u).fsPath); } catch (e) { /* לא קובץ */ }
                refresh();
            } else if (m.type === 'data') {
                const name = m.name || ('תמונה מודבקת ' + (++pastedImages) + '.png');
                const file = path.join(tmp, 'item' + nextId + path.extname(name).replace(/[^.\w]/g, ''));   // השם האמיתי — בנפרד (submit.json)
                fs.writeFileSync(file, Buffer.from(m.data, 'base64'));
                add(file, name);
                refresh();
            } else if (m.type === 'text') {
                const name = 'טקסט מודבק ' + (++pastedTexts) + '.txt';
                const file = path.join(tmp, 'item' + nextId + '.txt');
                fs.writeFileSync(file, m.text, 'utf8');
                add(file, name);
                refresh();
            } else if (m.type === 'remove') {
                const k = items.findIndex(i => i.id === m.id);
                if (k >= 0) items.splice(k, 1);
                refresh();
            } else if (m.type === 'say') {
                post({ type: 'status', text: m.text });
            } else if (m.type === 'cancel') {
                panel.dispose();
            } else if (m.type === 'stop') {   // "ביטול" בזמן הבנייה (או אחרי שגיאה): עוצרים, וחוזרים לטופס עם אותם קבצים
                stopped = true;
                if (child) child.kill();
                post({ type: 'form' });
            } else if (m.type === 'send' && !sending) {
                if (!items.length) return;
                sending = true;
                lang = m.lang === 'csharp' ? 'csharp' : 'java';
                post({ type: 'status', text: '' });
                await send(lang, m.note || '');
                sending = false;
            }
        });

        const send = async (lang, note) => {
            // כל הפרטים בקובץ JSON (ולא בשורת הפקודה) — כך שמות בעברית עוברים גם ב-Windows באנגלית
            const man = { lang, store: store(), note, client: 'dsviz-vscode/1.0.1', items: items.map(i => ({ path: i.file, name: i.name })) };
            let java = javaExe();
            if (lang === 'csharp') {
                const runner = deps.cs.findRunner(vscode.workspace.getConfiguration('dsviz').get('csRunner'));
                if (!runner) {
                    post({ type: 'status', bad: true, text: process.platform === 'darwin' ? 'לא נמצא dsviz-cs (ההרצה של C#). מריצים שוב את פקודת ההתקנה ל-Mac.' : 'לא נמצא dsviz-cs (ההרצה של C#). מתקינים את DSViz-Setup.exe עם C#.' });
                    return false;
                }
                deps.cs.setup(root, path.join(ctx.extensionPath, 'resources', 'cs', 'lib', deps.cs.LIB));
                man.out = root;
                man.cs = [runner.command, ...runner.prefix];
            } else {
                const jdk = deps.runner.findJava(vscode.workspace.getConfiguration('dsviz').get('javaHome'));
                if (!jdk) {
                    post({ type: 'status', bad: true, text: process.platform === 'darwin' ? 'לא נמצא JDK (צריך גם javac). מריצים שוב את פקודת ההתקנה ל-Mac, או מגדירים dsviz.javaHome.' : 'לא נמצא JDK (צריך גם javac). מתקינים את DSViz-Setup.exe, או מגדירים dsviz.javaHome.' });
                    return false;
                }
                java = jdk.java;
                const { srcDir } = deps.project.setup(root, jar());
                Object.assign(man, { out: srcDir, lib: path.join(root, 'lib', 'dsviz.jar'), java: jdk.java });
            }
            // השמות שכבר תפוסים בסביבה — החבילה / הפרויקט החדש יקבלו שם קצת אחר
            const roots = [root, ...(vscode.workspace.workspaceFolders || []).map(f => f.uri.fsPath)];
            const sp = (vscode.workspace.getConfiguration('java') || { get: () => null }).get('project.sourcePaths');
            man.taken = takenNames(lang, roots, { sourceDir: deps.project.sourceDir, exercisesDir: deps.cs.exerciseDir, sourcePaths: Array.isArray(sp) ? sp : [] });
            if (deps.onManifest) deps.onManifest(man);
            const manifest = path.join(tmp, 'submit.json');
            fs.writeFileSync(manifest, JSON.stringify(man), 'utf8');
            post({ type: 'build' });
            stopped = false;
            const run = runCli(java, jar(), ['submit', '--manifest', manifest], j => {
                if (j.progress && !stopped) post({ type: 'progress', text: j.progress });
            });
            child = run.proc;
            const result = await run.done;
            child = null;
            if (stopped) return false;   // החלון כבר חזר לטופס
            const r = result.last || {};
            if (!r.done) {
                post({ type: 'failed', text: shortError(r.error) });
                return false;
            }
            changed.fire();
            remember(r.files);
            panel.dispose();   // החלון נסגר, ובמקומו הקבצים: הקובץ עם main בלשונית שליד הקובץ של סעיף א, והסמן בסעיף א
            await deps.openFiles(root, r.files || [], r.focus, r.line || 0, mainFile(r.files));
            decorateAll();
            showDone(r);
            return true;
        };
    };

    return { submit, showSolution, showDone, decorate };
}

module.exports = { register, readStore, forFile, methods, lensesFor, entryAt, paramCount, submitHtml, solutionHtml, highlight, section, runCli,
    STAGES, stageFor, shortError, wordLabels, labelAt, doneMessage, emptyAnswers, mainFile, takenNames, SHOW, JUMP };
