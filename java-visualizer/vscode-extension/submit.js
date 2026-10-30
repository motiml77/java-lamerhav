'use strict';
/**
 * "שליחת תרגיל" ו"הצעה לפתרון" ב-VS Code — אותו מנוע כמו ב-Eclipse: ExercisePipeline שבתוך dsviz.jar, דרך SubmitCli.
 *   - החלון (Webview): מצרפים את דף התרגיל — PDF, Word, תמונות, צילום מסך עם Ctrl+V, גרירה של קבצים — הערה, ושפה (Java / C#).
 *   - SubmitCli שולח לשרת, בונה את הקבצים (Java: src/ex01_..., C#: Exercises/Ex01_...), בודק שהם מתקמפלים, בודק את ההצעות לפתרון
 *     (מריץ את main עם ההצעות ומשווה לפלט הצפוי) ושומר אותן בצד — לא בקוד של התלמיד.
 *   - מעל הכותרת של כל פעולה של סעיף: "💡 הצעה לפתרון" (CodeLens) — חלון עם הפתרון, האם נבדק, והעתקה.
 * החלקים בלי vscode (קריאת ההצעות, מציאת הפעולות בקובץ, ה-HTML) — מיוצאים לבדיקות.
 */
const fs = require('fs');
const os = require('os');
const path = require('path');
const cp = require('child_process');

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

// ------------------------------------------------------------ החלונות (HTML)

const esc = s => String(s).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');

const BASE_CSS = `
:root { --accent: #2563eb; }
body { margin: 0; padding: 18px 22px; font: 13px/1.45 var(--vscode-font-family); color: var(--vscode-foreground); background: var(--vscode-editor-background); }
h1 { font-size: 19px; margin: 0 0 2px; }
.sub { color: var(--vscode-descriptionForeground); margin: 0 0 14px; }
.card { border: 1px solid var(--vscode-widget-border, rgba(128,128,128,.35)); border-radius: 10px; padding: 12px 14px; margin-bottom: 12px; background: var(--vscode-sideBar-background, transparent); }
.card h2 { font-size: 13px; margin: 0 0 8px; }
button { font: inherit; border-radius: 8px; padding: 6px 16px; cursor: pointer; border: 1px solid var(--vscode-button-border, rgba(128,128,128,.5));
  background: var(--vscode-button-secondaryBackground, transparent); color: var(--vscode-button-secondaryForeground, inherit); }
button.primary { background: var(--accent); border-color: var(--accent); color: #fff; font-weight: 600; }
button:disabled { opacity: .55; cursor: default; }
.foot { display: flex; align-items: center; gap: 10px; margin-top: 14px; }
.foot .copy { margin-inline-start: auto; color: var(--vscode-descriptionForeground); font-size: 11px; }
`;

/** החלון "שליחת תרגיל" — כמו SubmitDialog של Eclipse */
function submitHtml(opts) {
    const lang = opts.lang === 'csharp' ? 'csharp' : 'java';
    return `<!DOCTYPE html><html lang="he" dir="rtl"><head><meta charset="UTF-8">
<meta http-equiv="Content-Security-Policy" content="default-src 'none'; img-src data: ${opts.cspSource || ''}; style-src 'unsafe-inline'; script-src 'unsafe-inline';">
<style>${BASE_CSS}
.drop { border: 2px dashed rgba(128,128,128,.45); border-radius: 10px; padding: 12px; min-height: 120px; display: flex; flex-direction: column; gap: 10px; }
.drop.over { border-color: var(--accent); background: rgba(37,99,235,.06); }
.items { display: flex; flex-wrap: wrap; gap: 10px; }
.item { position: relative; width: 118px; height: 108px; border: 1px solid rgba(128,128,128,.35); border-radius: 8px; background: var(--vscode-editor-background);
  display: flex; flex-direction: column; align-items: center; justify-content: flex-end; padding: 6px; box-sizing: border-box; }
.item .thumb { flex: 1; width: 100%; display: flex; align-items: center; justify-content: center; overflow: hidden; }
.item .thumb img { max-width: 100%; max-height: 66px; }
.item .badge { color: #fff; font-weight: 700; border-radius: 6px; padding: 8px 12px; font-size: 14px; }
.item .name { font-size: 11px; max-width: 100%; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; margin-top: 4px; }
.item .x { position: absolute; top: 4px; left: 4px; width: 20px; height: 20px; border-radius: 50%; border: 0; padding: 0; background: #9ca3af; color: #fff; font-size: 13px; line-height: 20px; }
.empty { color: var(--vscode-descriptionForeground); text-align: center; padding: 14px 0 4px; }
.row { display: flex; gap: 10px; justify-content: center; }
.summary { color: var(--vscode-descriptionForeground); font-size: 12px; margin-top: 6px; }
textarea { width: 100%; box-sizing: border-box; min-height: 48px; font: inherit; color: var(--vscode-input-foreground); background: var(--vscode-input-background);
  border: 1px solid var(--vscode-input-border, rgba(128,128,128,.4)); border-radius: 6px; padding: 6px 8px; resize: vertical; }
.hint { color: var(--vscode-descriptionForeground); font-size: 12px; margin: 0 0 6px; }
select { font: inherit; padding: 4px 8px; border-radius: 6px; color: var(--vscode-dropdown-foreground); background: var(--vscode-dropdown-background); border: 1px solid var(--vscode-dropdown-border, rgba(128,128,128,.4)); }
.lang { display: flex; align-items: center; gap: 8px; margin-top: 10px; }
.privacy { color: var(--vscode-descriptionForeground); font-size: 12px; margin-top: 10px; }
.status { margin-top: 10px; padding: 8px 12px; border-radius: 8px; display: none; }
.status.busy { display: block; background: rgba(37,99,235,.1); color: var(--vscode-foreground); }
.status.bad { display: block; background: rgba(220,38,38,.12); color: #dc2626; }
</style></head><body>
<h1>שליחת תרגיל</h1>
<p class="sub">מצרפים את דף התרגיל — ומקבלים קובץ מוכן: המחלקות, main, ופעולה לכל סעיף עם מקום לכתוב את הפתרון.</p>
<div class="card">
  <h2>דף התרגיל</h2>
  <div class="drop" id="drop">
    <div class="items" id="items"></div>
    <div class="empty" id="empty">גוררים לכאן קבצים, מדביקים צילום מסך (Ctrl+V), או בוחרים קבצים — PDF, Word (docx) או תמונות</div>
    <div class="row"><button id="pick">בחירת קבצים…</button><button id="paste">הדבקה (Ctrl+V)</button></div>
  </div>
  <div class="summary" id="summary"></div>
</div>
<div class="card">
  <h2>הערה (לא חובה)</h2>
  <p class="hint">למשל: "רק שאלה 2", או: "הפעולה בסעיף ב צריכה להיות רקורסיבית"</p>
  <textarea id="note"></textarea>
  <div class="lang"><label for="lang">שפה:</label>
    <select id="lang"><option value="java"${lang === 'java' ? ' selected' : ''}>Java</option><option value="csharp"${lang === 'csharp' ? ' selected' : ''}>C#</option></select>
    <span class="hint" style="margin:0">${esc(opts.where || '')}</span></div>
  <div class="privacy">הקבצים נשלחים לשרת של DSViz ומשם ל-Gemini (של Google) כדי לזהות את התרגיל. לא לשלוח דפים עם פרטים אישיים.</div>
</div>
<div class="status" id="status"></div>
<div class="foot"><button class="primary" id="send">שליחה ויצירת התרגיל</button><button id="cancel">ביטול</button><span class="copy">© 2026 Moti Levi · motiml77@gmail.com</span></div>
<script>
const vscode = acquireVsCodeApi();
let items = [], busy = false;
const $ = id => document.getElementById(id);
const COLORS = { pdf: '#dc2626', word: '#2563eb', text: '#6b7280' };
function size(n) { return n < 1024 * 1024 ? Math.max(1, Math.round(n / 1024)) + 'KB' : (n / 1024 / 1024).toFixed(1) + 'MB'; }
function render() {
  $('items').innerHTML = '';
  for (const it of items) {
    const d = document.createElement('div'); d.className = 'item';
    const th = document.createElement('div'); th.className = 'thumb';
    if (it.thumb) { const im = document.createElement('img'); im.src = it.thumb; th.appendChild(im); }
    else { const b = document.createElement('span'); b.className = 'badge'; b.style.background = COLORS[it.kind] || '#6b7280'; b.textContent = it.kind === 'pdf' ? 'PDF' : it.kind === 'word' ? 'Word' : 'טקסט'; th.appendChild(b); }
    const n = document.createElement('div'); n.className = 'name'; n.textContent = it.name; n.title = it.name;
    const x = document.createElement('button'); x.className = 'x'; x.textContent = '×'; x.title = 'הסרה';
    x.onclick = () => vscode.postMessage({ type: 'remove', id: it.id });
    d.append(th, n, x); $('items').appendChild(d);
  }
  $('empty').style.display = items.length ? 'none' : 'block';
  const files = items.filter(i => i.kind !== 'image').length, imgs = items.length - files;
  const total = items.reduce((a, i) => a + (i.size || 0), 0);
  $('summary').textContent = items.length ? [files ? (files === 1 ? 'קובץ אחד' : files + ' קבצים') : '', imgs ? (imgs === 1 ? 'תמונה אחת' : imgs + ' תמונות') : ''].filter(Boolean).join(' ו-') + ' · ' + size(total) + '   ·   אפשר להוסיף עוד, או להסיר עם ×' : '';
  $('send').disabled = busy || !items.length;
}
function readAs(blob) { return new Promise(r => { const f = new FileReader(); f.onload = () => r(String(f.result).split(',')[1]); f.readAsDataURL(blob); }); }
async function addBlob(blob, name, kind) { vscode.postMessage({ type: 'data', name, kind, data: await readAs(blob) }); }
document.addEventListener('paste', async e => {
  if (busy) return;
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
    vscode.postMessage({ type: 'say', text: 'בלוח אין תמונה. מצלמים את התרגיל (Win+Shift+S) ואז Ctrl+V כאן בחלון.' });
  } catch (err) { vscode.postMessage({ type: 'say', text: 'לחצו Ctrl+V בתוך החלון כדי להדביק צילום מסך.' }); }
};
const drop = $('drop');
drop.addEventListener('dragover', e => { e.preventDefault(); drop.classList.add('over'); });
drop.addEventListener('dragleave', () => drop.classList.remove('over'));
drop.addEventListener('drop', async e => {
  e.preventDefault(); drop.classList.remove('over'); if (busy) return;
  const uris = e.dataTransfer.getData('text/uri-list');
  if (uris && !e.dataTransfer.files.length) { vscode.postMessage({ type: 'uris', uris }); return; }
  for (const f of e.dataTransfer.files) await addBlob(f, f.name, '');
});
$('pick').onclick = () => vscode.postMessage({ type: 'pick' });
$('cancel').onclick = () => vscode.postMessage({ type: 'cancel' });
$('send').onclick = () => vscode.postMessage({ type: 'send', note: $('note').value, lang: $('lang').value });
window.addEventListener('message', e => {
  const m = e.data;
  if (m.type === 'items') { items = m.items; render(); }
  if (m.type === 'status') { const s = $('status'); s.className = 'status ' + (m.bad ? 'bad' : m.text ? 'busy' : ''); s.textContent = m.text || ''; }
  if (m.type === 'busy') { busy = m.busy; for (const id of ['pick', 'paste', 'note', 'lang']) $(id).disabled = busy; render(); }
});
render();
vscode.postMessage({ type: 'ready' });
</script></body></html>`;
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
    ok: ['✓  נבדק: הרצנו את main עם ההצעה הזו — והודפס בדיוק מה שצריך להיות מודפס.', '#e7f6ec', '#1e6b34'],
    ran: ['✓  ההצעה התקמפלה ורצה בלי שגיאות (בתרגיל לא היה פלט צפוי להשוואה).', '#e8f0fe', '#24489b'],
    failed: ['⚠  שימו לב: לא הצלחנו לוודא שההצעה הזו נותנת בדיוק את הפלט הצפוי — בדקו אותה בעצמכם.', '#fff4de', '#8a5300'],
    unchecked: ['ההצעה לא נבדקה במחשב — כדאי להריץ ולבדוק.', '#f1f3f7', '#6b7280']
};

/** "סעיף א'" מהתווית */
function section(label) {
    const l = String(label || '').trim();
    if (!l) return '';
    return /^[א-ת]$/.test(l) ? `סעיף ${l}'` : /^\d+$/.test(l) ? `סעיף ${l}` : l;
}

/** החלון "הצעה לפתרון" — כמו SolutionDialog של Eclipse */
function solutionHtml(entry, bulbUri) {
    const [text, bg, fg] = STATUS[entry.status] || STATUS.unchecked;
    const s = section(entry.label);
    return `<!DOCTYPE html><html lang="he" dir="rtl"><head><meta charset="UTF-8">
<meta http-equiv="Content-Security-Policy" content="default-src 'none'; img-src data: vscode-resource: https:; style-src 'unsafe-inline'; script-src 'unsafe-inline';">
<style>${BASE_CSS}
.head { display: flex; gap: 12px; align-items: flex-start; margin-bottom: 10px; }
.bulb { font-size: 30px; line-height: 1; }
.pill { border-radius: 8px; padding: 7px 12px; margin-bottom: 10px; }
pre { direction: ltr; text-align: left; margin: 0; padding: 12px 14px; border-radius: 10px; overflow: auto; max-height: 62vh;
  font: 13.5px/1.5 var(--vscode-editor-font-family, monospace); background: var(--vscode-textCodeBlock-background, rgba(128,128,128,.08));
  border: 1px solid var(--vscode-widget-border, rgba(128,128,128,.3)); user-select: text; }
.kw { color: #7f0055; font-weight: 600; } .st { color: #2a00ff; } .cm { color: #3f7f5f; } .nu { color: #098658; } .ty { color: #267f99; }
body.vscode-dark .kw { color: #c586c0; } body.vscode-dark .st { color: #ce9178; } body.vscode-dark .cm { color: #6a9955; } body.vscode-dark .nu { color: #b5cea8; } body.vscode-dark .ty { color: #4ec9b0; }
.cm { unicode-bidi: plaintext; }
</style></head><body>
<div class="head"><div class="bulb">💡</div><div><h1>הצעה לפתרון${s ? ' — ' + esc(s) : ''}</h1>
<p class="sub">כדאי לנסות קודם לבד. נתקעתם? הנה דרך אחת לפתור — יש גם דרכים נכונות אחרות.</p></div></div>
<div class="pill" style="background:${bg};color:${fg}">${esc(text)}</div>
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

/**
 * deps: { vscode, ctx, chooseRoot, chooseLanguage, project, cs, runner, openFiles }
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
                { title: '💡 הצעה לפתרון', tooltip: 'הפתרון שהוצע לסעיף — נפתח בחלון נפרד (לא נכנס לקוד)', command: 'dsviz.solution', arguments: [l.entry] }));
        }
    }));

    let solutionPanel = null;
    const showSolution = entry => {
        if (!entry) {
            const ed = vscode.window.activeTextEditor;
            if (ed) entry = entryAt(entries(), ed.document.uri.fsPath, ed.document.getText(), ed.selection.active.line);
        }
        if (!entry) {
            vscode.window.showInformationMessage('הצעה לפתרון יש רק לפעולות של סעיפים בתרגיל שנוצר ב"שליחת תרגיל". שמים את הסמן בתוך הפעולה, או לוחצים על 💡 שמעליה.');
            return;
        }
        const s = section(entry.label);
        if (!solutionPanel) {
            solutionPanel = vscode.window.createWebviewPanel('dsviz.solution', 'הצעה לפתרון', vscode.ViewColumn.Beside, { enableScripts: true });
            solutionPanel.onDidDispose(() => { solutionPanel = null; });
            solutionPanel.webview.onDidReceiveMessage(m => {
                if (m.type === 'copy') {
                    vscode.env.clipboard.writeText(m.text || solutionPanel.entry.code);
                    vscode.window.setStatusBarMessage(m.text ? 'הקטע שסומן הועתק' : 'ההצעה הועתקה — אפשר להדביק (Ctrl+V)', 3000);
                } else if (m.type === 'close') solutionPanel.dispose();
            });
        }
        solutionPanel.entry = entry;
        solutionPanel.title = 'הצעה לפתרון' + (s ? ' — ' + s : '');
        solutionPanel.webview.html = solutionHtml(entry);
        solutionPanel.reveal(vscode.ViewColumn.Beside);
    };

    // --- שליחת תרגיל
    const submit = async () => {
        const root = await deps.chooseRoot();
        if (!root) return;
        // בלי אינטרנט אין טעם לפתוח את החלון — אומרים את זה מיד
        const online = await vscode.window.withProgress({ location: vscode.ProgressLocation.Window, title: 'בודק חיבור לאינטרנט…' },
            () => runCli(javaExe(), jar(), ['online'], null).done);
        if (online.code === 1) {
            vscode.window.showWarningMessage('שליחת תרגיל — אין חיבור לאינטרנט', {
                modal: true, detail: 'כדי לשלוח תרגיל צריך חיבור לאינטרנט, וכרגע אין חיבור.\n\nבדקו את החיבור (Wi-Fi או כבל) ונסו שוב.\n'
                    + 'כל שאר הכלים — הרצה ויזואלית, הוספת מבנה נתונים, דוגמאות פתורות — עובדים גם בלי אינטרנט.' });
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
        let child = null, sending = false;
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
            } else if (m.type === 'send' && !sending) {
                if (!items.length) return;
                sending = true;
                lang = m.lang === 'csharp' ? 'csharp' : 'java';
                post({ type: 'busy', busy: true });
                const ok = await send(lang, m.note || '');
                sending = false;
                if (ok) panel.dispose();
                else post({ type: 'busy', busy: false });
            }
        });

        const send = async (lang, note) => {
            // כל הפרטים בקובץ JSON (ולא בשורת הפקודה) — כך שמות בעברית עוברים גם ב-Windows באנגלית
            const man = { lang, store: store(), note, client: 'dsviz-vscode/1.0.1', items: items.map(i => ({ path: i.file, name: i.name })) };
            let java = javaExe();
            if (lang === 'csharp') {
                const runner = deps.cs.findRunner(vscode.workspace.getConfiguration('dsviz').get('csRunner'));
                if (!runner) {
                    post({ type: 'status', bad: true, text: 'לא נמצא dsviz-cs (ההרצה של C#). מתקינים את DSViz-Setup.exe עם C#.' });
                    return false;
                }
                deps.cs.setup(root, path.join(ctx.extensionPath, 'resources', 'cs', 'lib', deps.cs.LIB));
                man.out = root;
                man.cs = [runner.command, ...runner.prefix];
            } else {
                const jdk = deps.runner.findJava(vscode.workspace.getConfiguration('dsviz').get('javaHome'));
                if (!jdk) {
                    post({ type: 'status', bad: true, text: 'לא נמצא JDK (צריך גם javac). מתקינים את DSViz-Setup.exe, או מגדירים dsviz.javaHome.' });
                    return false;
                }
                java = jdk.java;
                const { srcDir } = deps.project.setup(root, jar());
                Object.assign(man, { out: srcDir, lib: path.join(root, 'lib', 'dsviz.jar'), java: jdk.java });
            }
            const manifest = path.join(tmp, 'submit.json');
            fs.writeFileSync(manifest, JSON.stringify(man), 'utf8');
            const args = ['submit', '--manifest', manifest];
            const result = await vscode.window.withProgress({ location: vscode.ProgressLocation.Notification, title: 'שליחת תרגיל', cancellable: true }, (progress, token) => {
                const run = runCli(java, jar(), args, j => {
                    if (j.progress) { progress.report({ message: j.progress }); post({ type: 'status', text: j.progress }); }
                });
                child = run.proc;
                token.onCancellationRequested(() => run.proc.kill());
                return run.done;
            });
            child = null;
            const r = result.last || {};
            if (!r.done) {
                post({ type: 'status', bad: true, text: r.error || 'לא הצלחתי ליצור את התרגיל.' });
                return false;
            }
            changed.fire();
            await deps.openFiles(root, r.files || [], r.focus, r.line || 0);
            if ((r.errors || []).length) {
                vscode.window.showWarningMessage('התרגיל נוצר (בתיקייה ' + r.folder + '), אבל יש בו ' + r.errors.length + ' שגיאות קומפילציה:\n\n'
                    + r.errors.slice(0, 4).join('\n') + '\n\nבדרך כלל זה ב-main (הנתונים לדוגמה) — אפשר לתקן, או לשלוח שוב.', { modal: true });
            } else if (!r.tasks) {
                vscode.window.showInformationMessage('בתרגיל לא נמצאו סעיפים שצריך לכתוב בהם קוד — השאלות מופיעות בראש הקובץ.');
            } else {
                vscode.window.showInformationMessage('התרגיל נוצר בתיקייה ' + r.folder + '. כותבים את הפתרון במקום המסומן — ומעל כל סעיף יש 💡 הצעה לפתרון.');
            }
            return true;
        };
    };

    return { submit, showSolution };
}

module.exports = { register, readStore, forFile, methods, lensesFor, entryAt, paramCount, submitHtml, solutionHtml, highlight, section, runCli };
