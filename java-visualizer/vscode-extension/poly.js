'use strict';
/**
 * הורשה ופולימורפיזם ב-VS Code: "היררכיה מוכנה" (dsviz.createTree — כל המחלקות של עץ + main לדוגמה, ליד הקובץ הנוכחי) ו"הוסף עצם"
 * (dsviz.insertObject — Animal a1 = new Boxer(...); או מערך של מחלקת העל, במקום הסמן).
 *   TreeSession / ObjectSession — ההיגיון (רץ בתוסף): מצב החלון → מה להציג. המחולל: classgen.js (עותק של ClassGen.java).
 *   treeHtml / objectHtml — הדפים (Webview), באותו עיצוב כמו "צור מחלקה". הצבעים — כמו בהרצה הוויזואלית, והשם תמיד כתוב על הצבע.
 */
const fs = require('fs');
const path = require('path');
const G = require('./classgen');
const names = require('./names');

const sorted = known => (known || []).slice().sort((a, b) => a.name === b.name ? (a.pkg < b.pkg ? -1 : a.pkg > b.pkg ? 1 : 0) : (a.name < b.name ? -1 : 1));
const hexOf = (map, n) => G.hex(G.ink(map.has(n) ? map.get(n) : -1));
const bgOf = (map, n) => G.hex(G.bg(map.has(n) ? map.get(n) : -1));

/** האם יש כבר Main בפרויקט של C# (אז ה-main לדוגמה נוצר כפעולה Run()) — כמו CsClassFile.hasMain */
function hasMain(root) {
    const files = [];
    const walk = (d, depth) => {
        if (depth < 0) return;
        let list;
        try { list = fs.readdirSync(d, { withFileTypes: true }); } catch (e) { return; }
        for (const e of list) {
            if (e.name.startsWith('.') || e.name === 'bin' || e.name === 'obj') continue;
            const p = path.join(d, e.name);
            if (e.isDirectory()) walk(p, depth - 1); else if (e.name.endsWith('.cs')) files.push(p);
        }
    };
    walk(root, 6);
    for (const f of files) {
        let t;
        try { t = fs.readFileSync(f, 'utf8'); } catch (e) { continue; }
        if (/\bstatic\s+(?:async\s+)?(?:void|int|Task|Task<int>)\s+Main\s*\(/.test(t)) return true;
        if (!G.declaredTypes(t).length && t.replace(/\/\*[\s\S]*?\*\/|\/\/[^\n]*|\busing\s+[\w.=\s]+;/g, '').trim()) return true;
    }
    return false;
}

class TreeSession {
    /** cx — createclass.context(...); run — ב-C# כשכבר יש Main */
    constructor(cx, run) {
        this.cx = cx;
        this.run = !!run;
        this.trees = G.trees();
    }

    initial() {
        return { choice: 0, withMain: true, rows: [{ name: 'Vehicle', sup: '', fields: 'String model; int year' }, { name: 'Bus', sup: 'Vehicle', fields: 'int seats' },
            { name: 'Taxi', sup: 'Vehicle', fields: 'String company' }] };
    }

    base() {
        const cx = this.cx;
        return { lang: cx.lang, pkg: cx.pkg || '', namespace: cx.namespace || '', fileScoped: !!cx.fileScoped, taken: cx.taken, known: cx.known || [] };
    }

    tree(s, errors) {
        const k = s.choice || 0;
        if (k < this.trees.length) return this.trees[k];
        return G.ownTree((s.rows || []).map(r => ({ name: r.name || '', sup: r.sup || '', fields: r.fields || '' })), this.cx.known || [], errors);
    }

    /** מה להציג: { ok, error, warnings, files, preview, layout, about, supOptions, result } */
    view(s) {
        const errors = [];
        const t = this.tree(s, errors);
        const r = G.treeFiles(t, this.base(), s.withMain !== false, this.run);
        r.errors.unshift(...errors);
        r.ok = r.errors.length === 0;
        const layout = G.treeLayout(t, this.cx.known || []).map(l => ({ name: l[0], depth: l[1], color: G.hex(G.ink(l[2])), bg: G.hex(G.bg(l[2])), abstract: l[3] }));
        const main = t.main ? r.files.find(f => f[0].startsWith(t.main + '.')) : null;
        const warnings = r.warnings.slice();
        if (this.run && t.main && s.withMain !== false) warnings.push('בפרויקט כבר יש Main — ה-main לדוגמה נוצר כפעולה ' + t.main + '.Run()‎');
        const rowNames = (s.rows || []).map(x => (x.name || '').trim()).filter(x => x).map(x => x.charAt(0).toUpperCase() + x.slice(1));
        const supOptions = rowNames.concat(G.superCandidates(this.cx.known || []).map(k => k.name).filter(n => !rowNames.includes(n)));
        return {
            ok: r.ok && r.files.length > 0, error: r.errors[0] || null, errors: r.errors, warnings, files: r.files.map(f => f[0]),
            preview: main ? main[1] : (r.files[0] ? r.files[0][1] : ''), layout, main: t.main,
            about: (s.choice || 0) < this.trees.length ? this.trees[s.choice || 0].about : 'כותבים בטבלה את המחלקות — העץ מצויר תוך כדי.',
            supOptions, result: r
        };
    }
}

class ObjectSession {
    /** { lang, known, text, offset, indent } */
    constructor(o) {
        this.lang = o.lang === 'cs' ? 'cs' : 'java';
        this.known = o.known || [];
        this.text = o.text || '';
        this.offset = o.offset == null ? -1 : o.offset;
        this.indent = o.indent || '';
        this.colors = G.colors(sorted(this.known));
    }

    initial() {
        const types = sorted(this.known).filter(k => !k.isInterface);
        const t = types[0] ? types[0].name : '';
        const cl = G.objectClasses(this.known, t);
        return { array: false, varType: t, varName: t ? t.charAt(0).toLowerCase() + '1' : 'a1', cls: cl.length > 1 ? cl[1].name : cl[0] ? cl[0].name : '',
            args: [], size: 4, cells: [] };
    }

    tag(n) {
        return { name: n, color: hexOf(this.colors, n), bg: bgOf(this.colors, n) };
    }

    /** מה להציג: { ok, error, code, types, classes, params, cellClasses } */
    view(s) {
        const types = sorted(this.known).filter(k => !k.isInterface).map(k => Object.assign(this.tag(k.name), { abstract: !!k.isAbstract }));
        const classes = G.objectClasses(this.known, s.varType || '').map(k => this.tag(k.name));
        const params = G.ctorParams(this.known, s.cls || '').map(p => {
            const owner = G.paramOwner(this.known, s.cls, p.name);
            return { type: p.type, name: p.name, owner, color: hexOf(this.colors, owner), bg: bgOf(this.colors, owner) };
        });
        const cells = (s.cells || []).slice(0, s.size || 0).map(c => (c && c.cls ? [c.cls].concat(G.splitArgs(c.values || '')) : []));
        const cellParams = (s.cells || []).slice(0, s.size || 0).map(c => (c && c.cls ? G.ctorParams(this.known, c.cls).map(p => p.name).join(', ') : ''));
        const r = G.objectCode({ lang: this.lang, varType: s.varType, varName: s.varName, cls: s.cls, args: s.args || [], array: !!s.array, size: s.size || 4, cells, indent: this.indent },
            this.known);
        if (r.ok) {
            const bad = names.checkName((s.varName || '').trim(), this.text, this.offset, n => G.isIdentifier(n, this.lang));
            if (bad) r.errors.push(bad);
        }
        if (!this.known.length) r.errors.unshift('אין עדיין מחלקות בפרויקט — קודם "צור מחלקה" או "היררכיה מוכנה"');
        return { ok: r.errors.length === 0, error: r.errors[0] || null, code: r.code, types, classes, params, cellParams };
    }
}

const STYLE = `
:root { --card: var(--vscode-editorWidget-background, #fff); --line: var(--vscode-editorWidget-border, var(--vscode-panel-border, #dfe3ea));
        --accent: var(--vscode-button-background, #2f6fed); --accentText: var(--vscode-button-foreground, #fff);
        --muted: var(--vscode-descriptionForeground, #667085); --soft: var(--vscode-list-hoverBackground, #f4f7fd); }
* { box-sizing: border-box; }
body { margin: 0; padding: 14px 18px; font: 13px/1.4 var(--vscode-font-family); color: var(--vscode-foreground); background: var(--vscode-editor-background); }
h1 { font-size: 18px; margin: 0 0 2px; } .sub { color: var(--muted); margin-bottom: 12px; }
.cols { display: flex; gap: 14px; align-items: flex-start; flex-wrap: wrap; }
.card { background: var(--card); border: 1px solid var(--line); border-radius: 14px; padding: 12px 14px; }
.right { flex: 1 1 420px; min-width: 320px; max-width: 600px; } .left { flex: 1 1 360px; min-width: 300px; }
h2 { font-size: 13px; margin: 14px 0 6px; } h2:first-child { margin-top: 0; }
input[type=text], input[type=number] { font: inherit; color: var(--vscode-input-foreground); background: var(--vscode-input-background); border: 1px solid var(--vscode-input-border, var(--line));
        border-radius: 8px; padding: 4px 8px; min-width: 0; direction: ltr; text-align: left; font-family: var(--vscode-editor-font-family, monospace); }
.muted { color: var(--muted); font-size: 12px; } .hidden { display: none !important; }
.chips { display: flex; gap: 6px; flex-wrap: wrap; } .chip { font: inherit; border: 1px solid var(--line); border-radius: 10px; padding: 5px 12px; background: transparent; color: inherit; cursor: pointer; }
.chip.on { border: 2px solid var(--accent); font-weight: 600; }
.tags { display: flex; flex-wrap: wrap; gap: 6px; direction: ltr; justify-content: flex-end; }
.tag { font: 12.5px var(--vscode-editor-font-family, monospace); border: 1.5px solid; border-radius: 8px; padding: 2px 9px; background: #fff; display: inline-block; }
.tags .tag { cursor: pointer; } .tag.on { border-width: 3px; padding: 1px 8px; }
.row { display: flex; gap: 6px; align-items: center; direction: ltr; margin: 4px 0; }
.row input[type=text] { flex: 1 1 0; }
.tree { border: 1px solid var(--line); border-radius: 10px; padding: 8px; min-height: 80px; direction: ltr; background: var(--vscode-textCodeBlock-background, #f7f8fa); }
.tree .node { margin: 3px 0; }
pre { direction: ltr; text-align: left; margin: 0; padding: 10px 12px; border-radius: 10px; border: 1px solid var(--line); background: var(--vscode-textCodeBlock-background, #f7f8fa);
      font: 12.5px/1.45 var(--vscode-editor-font-family, monospace); overflow: auto; max-height: 60vh; min-height: 120px; white-space: pre; }
.warn { color: var(--vscode-editorWarning-foreground, #9a6700); margin: 10px 2px 0; white-space: pre-line; }
.err { color: var(--vscode-errorForeground, #c62828); min-height: 20px; margin: 6px 2px 0; }
.btns { display: flex; gap: 8px; margin-top: 8px; }
.btn { font: inherit; border-radius: 9px; padding: 7px 18px; cursor: pointer; border: 1px solid var(--line); background: transparent; color: inherit; }
.btn.primary { background: var(--accent); color: var(--accentText); border-color: var(--accent); font-weight: 600; min-width: 130px; }
.btn:disabled { opacity: .45; cursor: default; }
.add { font: inherit; margin-top: 2px; border-radius: 9px; padding: 5px 12px; cursor: pointer; border: 1px dashed var(--accent); background: transparent; color: var(--vscode-textLink-foreground, var(--accent)); }
.x { font: inherit; width: 28px; height: 28px; border-radius: 8px; border: 1px solid var(--line); background: transparent; color: var(--muted); cursor: pointer; }`;

const COMMON_JS = `
const vscode = acquireVsCodeApi();
const $ = id => document.getElementById(id);
let timer = null;
function send(now) { clearTimeout(timer); timer = setTimeout(() => vscode.postMessage({ type: 'state', state }), now ? 0 : 100); }
function tagEl(t, on, pick) { const e = document.createElement('span'); e.className = 'tag' + (on ? ' on' : ''); e.textContent = t.name + (t.abstract ? ' (abstract)' : '');
  e.style.color = t.color; e.style.borderColor = t.color; e.style.background = t.bg; if (pick) e.onclick = pick; return e; }
$('ok').onclick = () => { if (!$('ok').disabled) vscode.postMessage({ type: 'ok', state }); };
$('cancel').onclick = () => vscode.postMessage({ type: 'cancel' });
document.addEventListener('keydown', e => { if (e.key === 'Escape') $('cancel').click(); if (e.key === 'Enter' && e.ctrlKey) { e.preventDefault(); $('ok').click(); } });
window.addEventListener('message', e => { if (e.data.type === 'view') render(e.data.view); });`;

function page(title, sub, body, script, data) {
    return `<!doctype html>
<html lang="he" dir="rtl"><head><meta charset="utf-8">
<meta http-equiv="Content-Security-Policy" content="default-src 'none'; style-src 'unsafe-inline'; script-src 'unsafe-inline';">
<style>${STYLE}</style></head><body>
<h1>${title}</h1><div class="sub">${sub}</div>
${body}
<div class="warn" id="warn"></div>
<div class="err" id="err"></div>
<script>
const OPT = ${JSON.stringify(data).replace(/</g, '\\u003c')};
${COMMON_JS}
${script}
</script></body></html>`;
}

function treeHtml(session) {
    const trees = session.trees.map(t => t.title).concat([G.OWN_TREE]);
    return page(G.TREES_TITLE, 'כל המחלקות של עץ בלחיצה אחת — כל מחלקה בקובץ משלה, עם בנאי שמעביר למחלקת העל, ו-main לדוגמה עם מערך', `
<div class="cols">
  <div class="card right">
    <h2>בוחרים היררכיה</h2>
    <div class="chips" id="chips"></div>
    <div class="muted" id="about" style="margin-top:6px"></div>
    <div id="own" class="hidden">
      <h2>המחלקות</h2>
      <div class="muted">שם המחלקה · יורשת מ- (ריק — אף אחת) · השדות (String name; int age)</div>
      <div id="rows"></div>
      <button class="add" id="addRow">+ מחלקה</button>
    </div>
    <h2>העץ</h2>
    <div class="tree" id="tree"></div>
    <label style="display:block;margin-top:10px"><input type="checkbox" id="withMain"> גם main לדוגמה — מערך של מחלקת העל, ועצם מכל מחלקה</label>
    <div class="muted" id="files" style="margin-top:6px"></div>
  </div>
  <div class="card left"><h2>כך ייראה ה-main</h2><pre id="code"></pre></div>
</div>
<div class="btns"><button class="btn primary" id="ok">יצירת המחלקות</button><button class="btn" id="cancel">ביטול</button></div>`, `
let state = JSON.parse(JSON.stringify(OPT.initial));
$('withMain').checked = state.withMain !== false;
$('withMain').onchange = () => { state.withMain = $('withMain').checked; send(true); };
function chips() {
  const box = $('chips'); box.innerHTML = '';
  OPT.trees.forEach((t, i) => { const b = document.createElement('button'); b.className = 'chip' + (state.choice === i ? ' on' : ''); b.textContent = t;
    b.onclick = () => { state.choice = i; chips(); $('own').classList.toggle('hidden', i !== OPT.trees.length - 1); send(true); }; box.appendChild(b); });
}
function rows() {
  const box = $('rows'); box.innerHTML = '';
  state.rows.forEach((r, i) => {
    const d = document.createElement('div'); d.className = 'row';
    d.innerHTML = '<input type="text" class="n" placeholder="Vehicle" spellcheck="false"><span>extends</span><input type="text" class="s" list="sups" placeholder="" spellcheck="false">'
      + '<input type="text" class="f" placeholder="String name; int age" spellcheck="false" style="flex:2 1 0"><button class="x" title="הסרת המחלקה">✕</button>';
    const n = d.querySelector('.n'), s = d.querySelector('.s'), f = d.querySelector('.f');
    n.value = r.name; s.value = r.sup; f.value = r.fields;
    n.oninput = () => { r.name = n.value; send(); }; s.oninput = () => { r.sup = s.value; send(); }; f.oninput = () => { r.fields = f.value; send(); };
    d.querySelector('.x').onclick = () => { state.rows.splice(i, 1); rows(); send(true); };
    box.appendChild(d);
  });
}
$('addRow').onclick = () => { state.rows.push({ name: '', sup: '', fields: '' }); rows(); send(true); };
const dl = document.createElement('datalist'); dl.id = 'sups'; document.body.appendChild(dl);
function render(v) {
  $('about').textContent = v.about;
  const tr = $('tree'); tr.innerHTML = '';
  if (!v.layout.length) tr.textContent = '(העץ יצויר כאן)';
  for (const l of v.layout) { const d = document.createElement('div'); d.className = 'node'; d.style.paddingLeft = (l.depth * 26) + 'px';
    d.appendChild(document.createTextNode(l.depth ? '└ ' : '')); d.appendChild(tagEl(l, false)); tr.appendChild(d); }
  $('code').textContent = v.preview;
  $('files').textContent = v.files.length ? 'ייווצרו: ' + v.files.join(', ') : '';
  $('warn').textContent = (v.warnings || []).join('\\n');
  $('err').textContent = v.error || '';
  $('ok').disabled = !v.ok;
  dl.innerHTML = ''; for (const n of v.supOptions || []) { const o = document.createElement('option'); o.value = n; dl.appendChild(o); }
}
chips(); rows();
send(true);`, { initial: session.initial(), trees });
}

function objectHtml(session, lang) {
    return page(G.OBJECT_TITLE, 'משתנה מסוג מחלקת העל שמצביע על עצם של מחלקה יורשת — או מערך שבכל תא מחלקה אחרת', `
<div class="card">
  <label><input type="radio" name="mode" id="one" checked> עצם אחד</label> &nbsp;
  <label><input type="radio" name="mode" id="many"> מערך של מחלקת העל</label>
  <h2>טיפוס המשתנה</h2>
  <div class="tags" id="types"></div>
  <div class="row" style="justify-content:flex-end"><input type="number" id="size" min="1" max="12" value="4" style="width:70px"><span dir="rtl">גודל המערך:</span>
    <input type="text" id="name" spellcheck="false" style="flex:0 0 140px"><span dir="rtl">שם:</span></div>
  <div id="oneBox"><h2>המחלקה של העצם</h2><div class="tags" id="classes"></div><h2>ערך לכל פרמטר של הבנאי</h2><div id="params"></div></div>
  <div id="manyBox" class="hidden"><h2>התאים (מחלקה וערכים בפסיקים)</h2><div id="cells"></div></div>
  <h2>הקוד שייכנס במקום הסמן</h2><pre id="code"></pre>
</div>
<div class="btns"><button class="btn primary" id="ok">הוספה לקוד</button><button class="btn" id="cancel">ביטול</button></div>`, `
let state = JSON.parse(JSON.stringify(OPT.initial));
let last = null, shownParams = '';
$('name').value = state.varName;
$('name').oninput = () => { state.varName = $('name').value; send(); };
$('size').oninput = () => { state.size = Math.max(1, Math.min(12, parseInt($('size').value, 10) || 1)); send(true); };
$('one').onchange = $('many').onchange = () => { state.array = $('many').checked; send(true); };
function render(v) {
  last = v;
  $('oneBox').classList.toggle('hidden', !!state.array); $('manyBox').classList.toggle('hidden', !state.array); $('size').disabled = !state.array;
  const tb = $('types'); tb.innerHTML = '';
  for (const t of v.types) tb.appendChild(tagEl(t, t.name === state.varType, () => { state.varType = t.name; state.cls = ''; send(true); }));
  if (!state.cls && v.classes.length) { state.cls = v.classes[v.classes.length > 1 ? 1 : 0].name; send(true); }
  const cb = $('classes'); cb.innerHTML = '';
  for (const c of v.classes) cb.appendChild(tagEl(c, c.name === state.cls, () => { state.cls = c.name; send(true); }));
  const key = state.cls + '|' + v.params.map(p => p.type + ' ' + p.name).join(',');
  if (key !== shownParams) {
    shownParams = key;
    const pb = $('params'); pb.innerHTML = '';
    if (!v.params.length) pb.innerHTML = '<div class="muted">(לבנאי אין פרמטרים)</div>';
    v.params.forEach((p, i) => { const d = document.createElement('div'); d.className = 'row';
      d.appendChild(tagEl({ name: p.owner, color: p.color, bg: p.bg }, false));
      const l = document.createElement('span'); l.textContent = p.type + ' ' + p.name; l.style.fontFamily = 'var(--vscode-editor-font-family, monospace)'; d.appendChild(l);
      const t = document.createElement('input'); t.type = 'text'; t.spellcheck = false; t.value = state.args[i] || '';
      t.oninput = () => { state.args[i] = t.value; send(); }; d.appendChild(t); pb.appendChild(d); });
  }
  const cells = $('cells');
  while (state.cells.length < state.size) state.cells.push({ cls: '', values: '' });
  if (cells.children.length !== state.size || cells.dataset.type !== state.varType) {
    cells.dataset.type = state.varType; cells.innerHTML = '';
    for (let i = 0; i < state.size; i++) {
      const c = state.cells[i];
      const d = document.createElement('div'); d.className = 'row';
      const idx = document.createElement('span'); idx.textContent = '[' + i + ']'; d.appendChild(idx);
      const sel = document.createElement('select'); sel.innerHTML = '<option value="">(ריק — null)</option>' + v.classes.map(k => '<option>' + k.name + '</option>').join('');
      sel.value = c.cls; sel.onchange = () => { c.cls = sel.value; send(true); }; d.appendChild(sel);
      const t = document.createElement('input'); t.type = 'text'; t.className = 'v'; t.spellcheck = false; t.value = c.values; t.oninput = () => { c.values = t.value; send(); }; d.appendChild(t);
      cells.appendChild(d);
    }
  }
  [...cells.querySelectorAll('.v')].forEach((t, i) => { t.placeholder = v.cellParams[i] || ''; t.disabled = !state.cells[i].cls; });
  $('code').textContent = v.code;
  $('err').textContent = v.error || '';
  $('ok').disabled = !v.ok;
}
send(true);
setTimeout(() => $('name').focus(), 50);`, { initial: session.initial(), lang });
}

module.exports = { TreeSession, ObjectSession, treeHtml, objectHtml, hasMain };
