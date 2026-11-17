'use strict';
/**
 * "צור מחלקה" ב-VS Code (נפתח מהחלון "הוסף מבנה נתונים", או מהפקודה dsviz.createClass): חלון (Webview) שבו כותבים שם
 * למחלקה ואת השדות שלה (טיפוס + שם), ורואים את הקוד שייווצר — שדות private, בנאי, get ו-set לכל שדה, ו-toString אם רוצים.
 * הטיפוס נבחר מרשימה בקבוצות (סוגים בסיסיים · מבני נתונים של הקורס · מחלקות שכבר קיימות בפרויקט · מערכים) או נכתב חופשי;
 * ליד Queue / Stack / Node / BinNode — רשימה קטנה של מה שבתוכם (Queue<Student>).
 *
 *   Session — ההיגיון (רץ בתוסף): מצב החלון → מה להציג (שגיאה, אזהרות, הקוד ושם הקובץ). המחולל עצמו: classgen.js.
 *   html()  — הדף (HTML + JS), שמדבר עם Session בהודעות. אותו עיצוב כמו "הוסף מבנה נתונים".
 *   context() — מה כבר יש: package / namespace של הקובץ, הקבצים בתיקייה, וכל המחלקות של הפרויקט (classgen.scanProject).
 */
const fs = require('fs');
const path = require('path');
const G = require('./classgen');

class Session {
    /** { lang: 'java' | 'cs', pkg, namespace, fileScoped, taken: Map | {שם: קובץ}, known: [{name, pkg, file}], dir } */
    constructor(o) {
        this.lang = o.lang === 'cs' ? 'cs' : 'java';
        this.pkg = o.pkg || '';
        this.namespace = o.namespace || '';
        this.fileScoped = !!o.fileScoped;
        this.taken = o.taken || new Map();
        this.known = o.known || [];
        this.dir = o.dir || '';
    }

    initial() {
        return { className: '', fields: [row(this.lang)], ctor: true, toStr: false };
    }

    /** שורה בחלון: { base, elem, name, refs } (או { type, name } — טיפוס מלא) → שדה של המחולל */
    options(s) {
        const fields = (s.fields || []).map(f => ({
            type: f.base !== undefined ? G.fullType(f.base, f.elem, this.lang) : (f.type || ''), name: f.name || '', refs: Object.assign({}, f.refs || {})
        }));
        return {
            lang: this.lang, className: s.className || '', fields, ctor: s.ctor !== false, toStr: !!s.toStr, pkg: this.pkg, namespace: this.namespace,
            fileScoped: this.fileScoped, taken: this.taken, known: this.known
        };
    }

    /** מה להציג: { ok, error (הראשונה), errors, warnings, code, file, className } */
    view(s) {
        const o = this.options(s);
        const c = G.check(o);
        return {
            ok: c.ok, error: c.errors[0] || null, errors: c.errors, warnings: c.warnings,
            code: c.ok ? G.generate(o) : G.preview(o), file: c.className ? G.fileName(c.className, this.lang) : '', className: c.className
        };
    }
}

const row = lang => ({ base: G.defaultType(lang), elem: '', name: '', refs: {} });

/** תיקיית הפרויקט של C#: התיקייה הקרובה (עד 4 רמות למעלה) שיש בה ‎.csproj‎; אחרת — התיקייה של הקובץ */
function csProjectDir(dir) {
    let d = dir;
    for (let i = 0; i < 5 && d; i++) {
        try { if (fs.readdirSync(d).some(f => f.endsWith('.csproj'))) return d; } catch (e) { /* הבא */ }
        const up = path.dirname(d);
        if (up === d) break;
        d = up;
    }
    return dir;
}

/** שורש הקוד של Java בלי תיקיית עבודה: התיקייה של הקובץ בלי תיקיות ה-package (src/school/model ← src) */
function javaSourceRoot(dir, pkg) {
    if (!pkg) return dir;
    const parts = pkg.split('.');
    let d = dir;
    for (let i = parts.length - 1; i >= 0; i--) {
        if (path.basename(d) !== parts[i]) return dir;
        d = path.dirname(d);
    }
    return d;
}

/** הקבצים שכבר יש בתיקייה (Student.java ← Student) — קובץ חדש באותו שם נחסם */
function takenNames(dir, lang) {
    const taken = new Map();
    const ext = lang === 'cs' ? '.cs' : '.java';
    let here = [];
    try { here = fs.readdirSync(dir).filter(f => f.endsWith(ext)); } catch (e) { /* תיקייה חסרה */ }
    for (const f of here) taken.set(f.slice(0, -ext.length), f);
    return taken;
}

/**
 * ההקשר של הקובץ הנוכחי: { lang, dir, root, pkg, namespace, fileScoped, taken, known }.
 * root — מאיפה סורקים את המחלקות של הפרויקט: Java — תיקיית העבודה (workspace) של הקובץ, ואם אין — שורש הקוד; C# — תיקיית ה-csproj
 */
function context(lang, file, text, workspaceRoot) {
    const dir = path.dirname(file);
    const cs = lang === 'cs';
    const ns = cs ? G.namespaceOf(text) : null;
    const pkg = cs ? '' : G.packageOf(text);
    const root = cs ? csProjectDir(dir) : (workspaceRoot || javaSourceRoot(dir, pkg));
    return { lang, dir, root, pkg, namespace: ns ? ns.name : '', fileScoped: !!(ns && ns.fileScoped), taken: takenNames(dir, lang), known: G.scanProject(root, lang) };
}

/** הפריטים של החלון לפי השפה והמחלקות של הפרויקט */
function options(lang, initial, known) {
    return {
        lang, initial, groups: G.typeGroups(lang, known || []), elems: G.elementGroups(lang, known || []), course: G.COURSE, defaultElem: G.defaultElement(lang),
        defaultType: G.defaultType(lang), subtitle: G.subtitle(lang), title: G.TITLE,
        toStringLabel: G.toStringLabel(lang), keys: G.KEYS,
        placeholder: 'Student'
    };
}

function html(opt) {
    const data = JSON.stringify(opt).replace(/</g, '\\u003c');
    return `<!doctype html>
<html lang="he" dir="rtl"><head><meta charset="utf-8">
<meta http-equiv="Content-Security-Policy" content="default-src 'none'; style-src 'unsafe-inline'; script-src 'unsafe-inline';">
<style>
:root { --card: var(--vscode-editorWidget-background, #fff); --line: var(--vscode-editorWidget-border, var(--vscode-panel-border, #dfe3ea));
        --accent: var(--vscode-button-background, #2f6fed); --accentText: var(--vscode-button-foreground, #fff);
        --muted: var(--vscode-descriptionForeground, #667085); --soft: var(--vscode-list-hoverBackground, #f4f7fd); }
* { box-sizing: border-box; }
body { margin: 0; padding: 14px 18px; font: 13px/1.4 var(--vscode-font-family); color: var(--vscode-foreground); background: var(--vscode-editor-background); }
h1 { font-size: 18px; margin: 0 0 2px; } .sub { color: var(--muted); margin-bottom: 12px; }
.cols { display: flex; gap: 14px; align-items: flex-start; flex-wrap: wrap; }
.card { background: var(--card); border: 1px solid var(--line); border-radius: 14px; padding: 12px 14px; }
.right { flex: 1 1 400px; min-width: 320px; max-width: 540px; } .left { flex: 1 1 360px; min-width: 300px; }
h2 { font-size: 13px; margin: 14px 0 6px; } h2:first-child { margin-top: 0; }
input[type=text] { font: inherit; color: var(--vscode-input-foreground); background: var(--vscode-input-background); border: 1px solid var(--vscode-input-border, var(--line));
        border-radius: 8px; padding: 5px 8px; width: 100%; min-width: 0; direction: ltr; text-align: left; font-family: var(--vscode-editor-font-family, monospace); }
input::placeholder { color: var(--muted); opacity: .8; }
input:focus { outline: 1px solid var(--vscode-focusBorder, var(--accent)); outline-offset: -1px; }
table.fields { width: 100%; border-collapse: separate; border-spacing: 0 5px; direction: ltr; }
table.fields th { font-weight: 600; font-size: 12px; color: var(--muted); padding: 0 2px; direction: rtl; text-align: right; }
table.fields td { padding: 0 3px 0 0; vertical-align: middle; } td.t { width: 60%; } td.x { width: 30px; }
.typecell { display: flex; align-items: center; gap: 3px; }
.combo { position: relative; flex: 1 1 auto; min-width: 0; display: flex; }
.combo input { padding-right: 24px; }
.combo .drop { position: absolute; right: 2px; top: 2px; bottom: 2px; width: 20px; border: none; background: transparent; color: var(--muted); cursor: pointer; font-size: 10px; border-radius: 6px; }
.combo .drop:hover { background: var(--soft); }
.elem { display: none; align-items: center; gap: 2px; flex: 0 0 46%; font-family: var(--vscode-editor-font-family, monospace); color: var(--muted); }
.elem.on { display: flex; }
.x button { font: inherit; width: 28px; height: 28px; border-radius: 8px; border: 1px solid var(--line); background: transparent; color: var(--muted); cursor: pointer; }
.x button:hover { border-color: #c4302b; color: #c4302b; }
.add { font: inherit; margin-top: 2px; border-radius: 9px; padding: 5px 12px; cursor: pointer; border: 1px dashed var(--accent); background: transparent; color: var(--vscode-textLink-foreground, var(--accent)); }
.add:hover { background: var(--soft); }
.opts { margin-top: 12px; display: grid; gap: 6px; } .opts label { display: flex; gap: 6px; align-items: center; }
.file { font-family: var(--vscode-editor-font-family, monospace); unicode-bidi: isolate; direction: ltr; color: var(--muted); }
.keys { color: var(--muted); font-size: 12px; margin-top: 10px; }
.head { display: flex; align-items: baseline; gap: 8px; }
pre { direction: ltr; text-align: left; margin: 0; padding: 10px 12px; border-radius: 10px; border: 1px solid var(--line); background: var(--vscode-textCodeBlock-background, #f7f8fa);
      font: 12.5px/1.45 var(--vscode-editor-font-family, monospace); overflow: auto; max-height: 56vh; min-height: 220px; white-space: pre; }
.warn { color: var(--vscode-editorWarning-foreground, #9a6700); margin: 10px 2px 0; white-space: pre-line; }
.err { color: var(--vscode-errorForeground, #c62828); min-height: 20px; margin: 6px 2px 0; }
.btns { display: flex; gap: 8px; margin-top: 8px; }
.btn { font: inherit; border-radius: 9px; padding: 7px 18px; cursor: pointer; border: 1px solid var(--line); background: transparent; color: inherit; }
.btn.primary { background: var(--accent); color: var(--accentText); border-color: var(--accent); font-weight: 600; min-width: 130px; }
.btn:disabled { opacity: .45; cursor: default; }
/* הרשימה הנפתחת של הטיפוסים — בקבוצות */
#pop { position: fixed; z-index: 10; display: none; max-height: 360px; overflow-y: auto; min-width: 230px; padding: 4px 0; border-radius: 10px;
       background: var(--vscode-dropdown-background, var(--card)); color: var(--vscode-dropdown-foreground, inherit); border: 1px solid var(--vscode-dropdown-border, var(--line));
       box-shadow: 0 6px 18px rgba(0,0,0,.14); }
#pop .gh { font-size: 11.5px; font-weight: 600; color: var(--muted); padding: 6px 12px 2px; }
#pop .gh:not(:first-child) { border-top: 1px solid var(--line); margin-top: 4px; padding-top: 7px; }
#pop .it { padding: 3px 14px 3px 12px; cursor: pointer; display: flex; gap: 10px; align-items: baseline; }
#pop .it bdi { font-family: var(--vscode-editor-font-family, monospace); direction: ltr; unicode-bidi: isolate; }
#pop .it .hint { color: var(--muted); font-size: 11.5px; }
#pop .it.on, #pop .it:hover { background: var(--vscode-list-activeSelectionBackground, var(--accent)); color: var(--vscode-list-activeSelectionForeground, var(--accentText)); }
#pop .it.on .hint, #pop .it:hover .hint { color: inherit; opacity: .8; }
#pop .empty { color: var(--muted); padding: 2px 14px 4px 12px; font-size: 12px; }
</style></head><body>
<h1 id="title"></h1>
<div class="sub" id="sub"></div>
<div class="cols">
  <div class="card right">
    <h2><label for="className">שם המחלקה</label></h2>
    <input type="text" id="className" spellcheck="false" autocomplete="off">
    <h2>השדות (private)</h2>
    <table class="fields"><thead><tr><th>טיפוס</th><th>שם</th><th></th></tr></thead><tbody id="rows"></tbody></table>
    <button class="add" id="add">+ שדה</button>
    <div class="opts">
      <label><input type="checkbox" id="ctor"> בנאי שמקבל את כל השדות</label>
      <label><input type="checkbox" id="toStr"> <span id="toStrLabel"></span></label>
    </div>
    <div class="keys" id="keys"></div>
  </div>
  <div class="card left">
    <div class="head"><h2>כך תיראה המחלקה</h2><span class="file" id="file"></span></div>
    <pre id="code"></pre>
  </div>
</div>
<div class="warn" id="warn"></div>
<div class="err" id="err"></div>
<div class="btns"><button class="btn primary" id="ok">יצירת המחלקה</button><button class="btn" id="cancel">ביטול</button></div>
<div id="pop" role="listbox"></div>
<script>
const vscode = acquireVsCodeApi();
const OPT = ${data};
const $ = id => document.getElementById(id);
let state = JSON.parse(JSON.stringify(OPT.initial)), timer = null;
$('title').textContent = OPT.title; $('sub').textContent = OPT.subtitle; $('toStrLabel').textContent = OPT.toStringLabel; $('keys').textContent = OPT.keys;
$('className').placeholder = OPT.placeholder;
$('ctor').checked = state.ctor; $('toStr').checked = state.toStr;
const isCourse = b => OPT.course.includes(String(b || '').trim());

// ---------------------------------------------------------------- הרשימה הנפתחת (אחת לכל החלון)
const pop = $('pop');
let popFor = null, popItems = [], popIndex = -1;
function openPop(input, groups, onPick, all) {
  popFor = input; popItems = []; popIndex = -1;
  const q = all ? '' : input.value.trim().toLowerCase();
  pop.innerHTML = '';
  for (const g of groups) {
    const items = g.items.filter(it => !q || it.type.toLowerCase().includes(q));
    if (q && !items.length) continue;
    const gh = document.createElement('div'); gh.className = 'gh'; gh.textContent = g.title; pop.appendChild(gh);
    if (!items.length && g.empty) { const e = document.createElement('div'); e.className = 'empty'; e.textContent = g.empty; pop.appendChild(e); }
    for (const it of items) {
      const d = document.createElement('div'); d.className = 'it'; d.setAttribute('role', 'option');
      // הטיפוס (באנגלית) ברשימה מימין לשמאל: מבודד ו-LRM משני הצדדים — אחרת int[] מוצג []int
      const b = document.createElement('bdi'); b.dir = 'ltr'; b.textContent = '\u200E' + it.type + '\u200E'; d.appendChild(b);
      if (it.hint) { const h = document.createElement('span'); h.className = 'hint'; h.dir = 'ltr'; h.textContent = it.hint; d.appendChild(h); }
      d.onmousedown = e => { e.preventDefault(); onPick(it); closePop(); };
      popItems.push({ el: d, it, onPick });
      pop.appendChild(d);
    }
  }
  if (!pop.children.length) { closePop(); return; }
  const r = input.getBoundingClientRect();
  pop.style.display = 'block';
  pop.scrollTop = 0;
  pop.style.minWidth = Math.max(230, r.width) + 'px';
  const left = Math.min(r.left, window.innerWidth - pop.offsetWidth - 8);
  pop.style.left = Math.max(8, left) + 'px';
  const below = window.innerHeight - r.bottom - 8;
  pop.style.top = (below >= Math.min(360, pop.scrollHeight) || below > r.top ? r.bottom + 2 : Math.max(8, r.top - 2 - Math.min(360, pop.scrollHeight))) + 'px';
}
function closePop() { pop.style.display = 'none'; popFor = null; popItems = []; popIndex = -1; }
function movePop(d) {
  if (!popItems.length) return;
  if (popIndex >= 0) popItems[popIndex].el.classList.remove('on');
  popIndex = (popIndex + d + popItems.length) % popItems.length;
  popItems[popIndex].el.classList.add('on');
  popItems[popIndex].el.scrollIntoView({ block: 'nearest' });
}
// לחיצה מחוץ לרשימה ומחוץ לשדה שלה (גם לא על ה-▼ שלו) — סוגרת
document.addEventListener('mousedown', e => {
  const own = e.target.closest && e.target.closest('.combo');
  if (popFor && !pop.contains(e.target) && !(own && own.contains(popFor))) closePop();
});
window.addEventListener('resize', closePop);

/** שדה עם רשימה: לחיצה על ▾ / חץ למטה — הרשימה כולה; הקלדה — מסננת; Enter — בוחר; Esc — סוגר את הרשימה */
function combo(input, button, groups, onPick, onType) {
  button.onmousedown = e => { e.preventDefault(); if (popFor === input) closePop(); else { input.focus(); openPop(input, groups(), onPick, true); } };
  input.addEventListener('input', () => { onType(); openPop(input, groups(), onPick, false); });
  input.addEventListener('blur', () => setTimeout(() => { if (popFor === input) closePop(); }, 120));
  input.addEventListener('keydown', e => {
    if (e.key === 'ArrowDown' || e.key === 'ArrowUp') {
      e.preventDefault();
      if (popFor !== input) openPop(input, groups(), onPick, true);
      movePop(e.key === 'ArrowDown' ? 1 : -1);
    } else if (e.key === 'Enter' && popFor === input && popIndex >= 0) {
      e.preventDefault(); e.stopPropagation(); const p = popItems[popIndex]; p.onPick(p.it); closePop();
    } else if (e.key === 'Escape' && popFor === input) { e.preventDefault(); e.stopPropagation(); closePop(); }
  });
}

// ---------------------------------------------------------------- השורות
function rows() {
  const box = $('rows');
  box.innerHTML = '';
  state.fields.forEach((f, i) => {
    const tr = document.createElement('tr');
    tr.innerHTML = '<td class="t"><div class="typecell"><div class="combo"><input type="text" class="base" spellcheck="false" autocomplete="off" aria-label="טיפוס">'
      + '<button class="drop" tabindex="-1" aria-label="רשימת הטיפוסים">▼</button></div>'
      + '<span class="elem">&lt;<span class="combo"><input type="text" class="elemIn" spellcheck="false" autocomplete="off" aria-label="מה יש בפנים">'
      + '<button class="drop" tabindex="-1" aria-label="מה יש בפנים">▼</button></span>&gt;</span></div></td>'
      + '<td class="n"><input type="text" class="name" spellcheck="false" autocomplete="off" aria-label="שם השדה" placeholder="name"></td>'
      + '<td class="x"><button title="הסרת השדה" aria-label="הסרת השדה">✕</button></td>';
    const base = tr.querySelector('.base'), elemIn = tr.querySelector('.elemIn'), name = tr.querySelector('.name'), elem = tr.querySelector('.elem');
    const drops = tr.querySelectorAll('.drop');
    base.value = f.base; elemIn.value = f.elem; name.value = f.name;
    const sync = () => {
      elem.classList.toggle('on', isCourse(f.base));
      if (isCourse(f.base) && !f.elem) { f.elem = OPT.defaultElem; elemIn.value = f.elem; }
    };
    sync();
    const pickRef = it => { if (it.pkg !== null && it.pkg !== undefined) f.refs[it.type.replace(/\\[\\]$/, '')] = it.pkg; };
    combo(base, drops[0], () => OPT.groups, it => {
      f.base = it.type; base.value = it.type; pickRef(it); sync(); send(true);
      (isCourse(f.base) ? elemIn : name).focus();
    }, () => { f.base = base.value; sync(); send(); });
    combo(elemIn, drops[1], () => OPT.elems, it => { f.elem = it.type; elemIn.value = it.type; pickRef(it); send(true); name.focus(); },
      () => { f.elem = elemIn.value; send(); });
    base.addEventListener('keydown', e => { if (e.key === 'Enter' && !e.ctrlKey && !e.defaultPrevented) { e.preventDefault(); (isCourse(f.base) ? elemIn : name).focus(); } });
    elemIn.addEventListener('keydown', e => { if (e.key === 'Enter' && !e.ctrlKey && !e.defaultPrevented) { e.preventDefault(); name.focus(); } });
    name.oninput = () => { f.name = name.value; send(); };
    name.onkeydown = e => {
      if (e.key !== 'Enter' || e.ctrlKey) return;
      e.preventDefault();
      if (i === state.fields.length - 1) addRow(); else box.children[i + 1].querySelector('.base').focus();
    };
    tr.querySelector('.x button').onclick = () => {
      state.fields.splice(i, 1);
      if (!state.fields.length) state.fields.push(newRow());
      rows(); send(true);
    };
    box.appendChild(tr);
  });
}
const newRow = () => ({ base: OPT.defaultType, elem: '', name: '', refs: {} });
function addRow() {
  state.fields.push(newRow());
  rows(); send(true);
  const b = $('rows').lastElementChild.querySelector('.base');
  b.focus(); b.select();
}
rows();
$('add').onclick = addRow;
$('className').oninput = () => { state.className = $('className').value; send(); };
$('className').onkeydown = e => { if (e.key === 'Enter' && !e.ctrlKey) { e.preventDefault(); $('rows').querySelector('.name').focus(); } };
$('ctor').onchange = () => { state.ctor = $('ctor').checked; send(true); };
$('toStr').onchange = () => { state.toStr = $('toStr').checked; send(true); };
$('ok').onclick = () => { if (!$('ok').disabled) vscode.postMessage({ type: 'ok', state }); };
$('cancel').onclick = () => vscode.postMessage({ type: 'cancel' });
document.addEventListener('keydown', e => {
  if (e.defaultPrevented) return;
  if (e.key === 'Escape') $('cancel').click();
  if (e.key === 'Enter' && e.ctrlKey) { e.preventDefault(); $('ok').click(); }
});
function send(now) {
  clearTimeout(timer);
  timer = setTimeout(() => vscode.postMessage({ type: 'state', state }), now ? 0 : 100);
}
window.addEventListener('message', e => { if (e.data.type === 'view') render(e.data.view); });
function render(v) {
  $('code').textContent = v.code;
  $('file').textContent = v.file;
  $('warn').textContent = (v.warnings || []).join('\\n');
  $('err').textContent = v.error || '';
  $('ok').disabled = !v.ok;
}
vscode.postMessage({ type: 'state', state });
setTimeout(() => $('className').focus(), 50);
</script></body></html>`;
}

module.exports = { Session, html, options, context, takenNames, csProjectDir, javaSourceRoot };
