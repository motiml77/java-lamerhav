'use strict';
/**
 * "הוסף מבנה נתונים" ב-VS Code: חלון (Webview) שבו בוחרים מבנה, טיפוס, ערכים (או אקראיים) ושם — עם ציור חי של המבנה.
 *
 * שני חלקים:
 *   Session — ההיגיון (רץ בתוסף): מקבל את מצב החלון, ומחזיר מה להציג (ציור, ערכים, שגיאה, הצעת שם...) ואת הקוד.
 *             משתמש בתבניות של Java או של C# (templates.js / templates-cs.js) ובבדיקת השמות (names.js).
 *   html()  — הדף עצמו (HTML + JS + SVG), שמדבר עם Session בהודעות. אותו עיצוב כמו החלונות של Eclipse ושל Visual Studio.
 */

const MAX_INNER = 12;

class Session {
    /**
     * tpl — templates.js או templates-cs.js; text — כל הקובץ; offset — מיקום ההכנסה בקובץ; indent — הזחה לשורות החדשות
     */
    constructor({ tpl, text, offset, indent, preset }) {
        this.tpl = tpl;
        this.text = text || '';
        this.offset = typeof offset === 'number' ? offset : -1;
        this.indent = indent || '';
        this.preset = preset || 'queue';
        this.lastKey = '';
        this.seed = Math.floor(Math.random() * 2147483647);
    }

    /** מעדכן את הקובץ ואת מקום ההכנסה (אם הקובץ השתנה בזמן שהחלון פתוח) */
    update(text, offset, indent) {
        this.text = text;
        this.offset = offset;
        if (indent !== undefined) this.indent = indent;
    }

    /** המצב ההתחלתי של החלון */
    initial() {
        const [outer, inside] = String(this.preset).split('_');
        return { structure: outer, inside: inside || '', type: 'Integer', random: false, count: 6, values: null, name: '' };
    }

    /**
     * מה להציג לפי מצב החלון s = { structure, inside, type, random, count, values, name }.
     * reason: מה השתנה ('values' — המשתמש מקליד בשדה הערכים, ולכן לא דורסים אותו; 'regen' — הגרלה מחדש)
     */
    view(s, reason) {
        const T = this.tpl;
        const outer = s.structure;
        const card = outer === 'student' || outer === 'car';
        const nestable = !card && T.canNest(outer);
        const inside = nestable ? (s.inside || '') : '';
        const st = T.comboId(outer, inside);
        const combo = !card && !!T.STRUCTURES[st].combo;
        const noObjects = combo || outer === 'matrix';
        let type = s.type || 'Integer';
        if (noObjects && (type === 'Student' || type === 'Car')) type = 'Integer';
        const objects = !card && (type === 'Student' || type === 'Car');
        const randomEnabled = !objects && !card;
        const random = randomEnabled && !!s.random;
        const maxCount = combo ? MAX_INNER : 20;
        const count = Math.max(1, Math.min(parseInt(s.count, 10) || 6, maxCount));

        // ערכים: כשמשהו שמשפיע עליהם משתנה (מבנה / טיפוס / אקראי / כמה) — ממלאים מחדש; אחרת — מה שהמשתמש כתב
        let values = s.values, setValues;
        if (card) {
            values = null;
        } else {
            const key = [st, type, random, random ? count : ''].join('|');
            if (key !== this.lastKey || reason === 'regen') {
                if (random) this.seed = Math.floor(Math.random() * 2147483647);
                values = random ? T.randomValues(st, type, count, this.seed) : T.defaultValues(st, type, false);
                setValues = values;
            }
            this.lastKey = key;
            if (values === null || values === undefined) { values = T.defaultValues(st, type, false); setValues = values; }
        }

        const shownType = card ? (st === 'student' ? 'Student' : 'Car') : type;
        let error = null;
        const suggestion = T.suggestName(st, this.text, this.offset);
        const typed = String(s.name || '').trim();
        if (typed) error = T.nameError(typed, this.text, this.offset);
        const name = typed || suggestion;

        let preview = null;
        try { preview = T.preview(st, shownType, values); } catch (e) { if (!error) error = e.message; }
        let code = null;
        if (!error) {
            try {
                code = T.snippet({ structure: st, type: shownType, name, values: card ? undefined : values, indent: this.indent });
            } catch (e) { error = e.message; }
        }
        const label = outer === 'matrix' ? 'כמה שורות אקראיות:' : combo ? 'כמה מבנים פנימיים אקראיים:' : 'כמה ערכים אקראיים:';
        const valuesLabel = card ? '' : type === 'Student' ? 'כמה תלמידים:' : type === 'Car' ? 'כמה מכוניות:'
            : combo ? (outer === 'tree' ? 'מבנים פנימיים (לפי רמות, ;):' : 'מבנים פנימיים (ב-;):') : outer === 'matrix' ? 'ערכים (שורות ב-;):' : 'ערכים:';
        return {
            structure: outer, inside, type, st, card, combo, random, randomEnabled, maxCount, count, randomLabel: label,
            insideEnabled: nestable, typeEnabled: { Student: !card && !noObjects, Car: !card && !noObjects, Integer: !card, String: !card, Character: !card, Double: !card },
            typeSection: combo ? 'טיפוס הערכים (בתוך המבנים הפנימיים)' : 'טיפוס הערכים',
            valuesLabel, valuesEnabled: !card, values, setValues, preview, hint: suggestion, error, ok: !error && code !== null,
            code, chosenClass: card ? shownType : (objects && outer !== 'matrix' && !combo) ? shownType : null
        };
    }
}

/** הדף של החלון. options: { structures: [[id, label]...], inside: [{id, label, description}], types: [...] } */
function html(options) {
    const data = JSON.stringify(options).replace(/</g, '\\u003c');
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
.right { width: 300px; flex: 0 0 auto; } .left { flex: 1 1 340px; min-width: 300px; }
h2 { font-size: 13px; margin: 12px 0 6px; } h2:first-child { margin-top: 0; }
.grid { display: grid; gap: 6px; } .g2 { grid-template-columns: 1fr 1fr; } .g3 { grid-template-columns: 1fr 1fr 1fr; }
.chip { font: inherit; color: inherit; background: transparent; border: 1px solid var(--line); border-radius: 9px; padding: 4px 6px; cursor: pointer; min-height: 28px; }
.chip:hover:not(:disabled) { background: var(--soft); border-color: var(--accent); }
.chip.on, .chip.on:hover:not(:disabled) { background: var(--accent); color: var(--accentText); border-color: var(--accent); font-weight: 600; }
.chip:disabled { opacity: .4; cursor: default; }
.row { display: flex; align-items: center; gap: 8px; margin: 4px 0; }
input[type=text], input[type=number] { font: inherit; color: var(--vscode-input-foreground); background: var(--vscode-input-background); border: 1px solid var(--vscode-input-border, var(--line)); border-radius: 8px; padding: 5px 8px; }
input[type=text] { flex: 1; min-width: 0; direction: ltr; text-align: left; font-family: var(--vscode-editor-font-family, monospace); }
input[type=number] { width: 64px; }
input:disabled { opacity: .5; } input::placeholder { color: var(--muted); opacity: .8; }
.typeLabel { direction: ltr; text-align: left; font-weight: 600; font-family: var(--vscode-editor-font-family, monospace); margin: 2px 0 4px; min-height: 18px; }
#pv { direction: ltr; overflow-x: auto; min-height: 90px; border-radius: 10px; }
.err { color: var(--vscode-errorForeground, #c62828); min-height: 20px; margin: 10px 2px 0; }
.btns { display: flex; gap: 8px; margin-top: 8px; }
.btn { font: inherit; border-radius: 9px; padding: 7px 18px; cursor: pointer; border: 1px solid var(--line); background: transparent; color: inherit; }
.btn.primary { background: var(--accent); color: var(--accentText); border-color: var(--accent); font-weight: 600; min-width: 130px; }
.btn:disabled { opacity: .45; cursor: default; }
.link { background: none; border: none; color: var(--vscode-textLink-foreground); cursor: pointer; font: inherit; padding: 0 4px; }
</style></head><body>
<h1>הוסף מבנה נתונים</h1>
<div class="sub">המבנה נבנה בקוד במקום הסמן, עם הערכים שבחרתם — רואים כאן איך הוא ייראה.</div>
<div class="cols">
  <div class="card right">
    <h2>איזה מבנה נתונים?</h2><div class="grid g2" id="structs"></div>
    <h2>מה יש בכל תא?</h2><div class="grid g2" id="inside"></div>
    <h2 id="typeSection">טיפוס הערכים</h2><div class="grid g3" id="types"></div>
  </div>
  <div class="card left">
    <h2>מילוי מבנה-נתונים</h2>
    <div class="row"><label><input type="checkbox" id="random"> <span id="randomLabel">כמה ערכים אקראיים:</span></label>
      <input type="number" id="count" min="1" max="20" value="6"><button class="link" id="regen" title="הגרלה מחדש">🎲 הגרל שוב</button></div>
    <div class="row"><label id="valuesLabel" for="values">ערכים:</label><input type="text" id="values" spellcheck="false"></div>
    <h2>כך ייראה המבנה</h2>
    <div class="typeLabel" id="ptype"></div><div id="pv"></div>
    <h2>שם המשתנה</h2>
    <div class="row"><label for="name">שם:</label><input type="text" id="name" spellcheck="false" autocomplete="off"></div>
  </div>
</div>
<div class="err" id="err"></div>
<div class="btns"><button class="btn primary" id="ok">הוספה לקוד</button><button class="btn" id="cancel">ביטול</button></div>
<script>
const vscode = acquireVsCodeApi();
const OPT = ${data};
const $ = id => document.getElementById(id);
let state = OPT.initial, timer = null, lastView = null;

function chips(box, items, key, onPick) {
  box.innerHTML = '';
  for (const [id, label, tip] of items) {
    const b = document.createElement('button');
    b.className = 'chip'; b.textContent = label; b.dataset.id = id; if (tip) b.title = tip;
    b.onclick = () => onPick(id);
    box.appendChild(b);
  }
}
chips($('structs'), OPT.structures, 'structure', id => { state.structure = id; send('structure'); });
chips($('inside'), OPT.inside, 'inside', id => { state.inside = id; send('inside'); });
chips($('types'), OPT.types.map(t => [t, t]), 'type', id => { state.type = id; send('type'); });
$('random').onchange = () => { state.random = $('random').checked; send('random'); };
$('count').oninput = () => { state.count = $('count').value; send('count'); };
$('regen').onclick = () => send('regen');
$('values').oninput = () => { state.values = $('values').value; send('values'); };
$('name').oninput = () => { state.name = $('name').value; send('name'); };
$('ok').onclick = () => vscode.postMessage({ type: 'ok', state });
$('cancel').onclick = () => vscode.postMessage({ type: 'cancel' });
document.addEventListener('keydown', e => {
  if (e.key === 'Enter' && e.target.tagName === 'INPUT' && e.target.type === 'text' && !$('ok').disabled) $('ok').click();
  if (e.key === 'Escape') $('cancel').click();
});
function send(reason) {
  clearTimeout(timer);
  timer = setTimeout(() => vscode.postMessage({ type: 'state', state, reason }), reason === 'values' || reason === 'name' ? 120 : 0);
}

window.addEventListener('message', e => { if (e.data.type === 'view') render(e.data.view); });
function mark(box, id) { for (const b of box.children) b.classList.toggle('on', b.dataset.id === id); }
function render(v) {
  lastView = v;
  state.structure = v.structure; state.inside = v.inside; state.type = v.type; state.random = v.random; state.count = v.count;
  mark($('structs'), v.structure); mark($('inside'), v.inside); mark($('types'), v.type);
  for (const b of $('inside').children) b.disabled = !v.insideEnabled && b.dataset.id !== '';
  for (const b of $('types').children) b.disabled = v.typeEnabled[b.dataset.id] === false;
  $('typeSection').textContent = v.typeSection;
  $('random').checked = v.random; $('random').disabled = !v.randomEnabled; $('randomLabel').textContent = v.randomLabel;
  $('count').disabled = !v.random; $('count').max = v.maxCount; $('count').value = v.count; $('regen').style.display = v.random ? '' : 'none';
  $('valuesLabel').textContent = v.valuesLabel; $('values').disabled = !v.valuesEnabled;
  if (v.setValues !== undefined) { $('values').value = v.setValues; state.values = v.setValues; }
  if (v.card) { $('values').value = ''; state.values = null; }
  $('name').placeholder = v.hint;
  $('ptype').textContent = v.preview ? v.preview.type : '';
  $('pv').innerHTML = v.preview ? draw(v.preview) : '';
  $('err').textContent = v.error || '';
  $('ok').disabled = !v.ok;
}

// ---------------------------------------------------------------- ציור (אותו תרשים כמו בחלונות של Eclipse ושל Visual Studio)
const CW0 = 28, CH = 22, PAD = 6, ARROW = 12, QA = 10, OA = 18;   // QA/OA — מקום לחיצים בצדי התור
const C = { fill: '#E8F0FE', line: '#8FA1C4', cell: '#FFFFFF', ink: '#1D2433', muted: '#6B7485', accent: '#3B4A63' };
function draw(m) {
  let cw = CW0, longest = 0;
  for (const g of m.groups) if (g) for (const x of g) longest = Math.max(longest, Math.min(String(x).length, 9));
  cw = Math.max(CW0, longest * 7 + 12);
  const out = [];
  const esc = s => String(s).replace(/&/g, '&amp;').replace(/</g, '&lt;');
  const text = (s, x, y, color, bold) => out.push('<text x="' + x + '" y="' + (y + 12) + '" fill="' + (color || C.ink) + '" font-size="12"' + (bold ? ' font-weight="600"' : '') + '>' + esc(s) + '</text>');
  const rect = (x, y, w, h, fill, line, r, sw) => out.push('<rect x="' + x + '" y="' + y + '" width="' + w + '" height="' + h + '" rx="' + (r || 0) + '" fill="' + fill + '" stroke="' + line + '" stroke-width="' + (sw || 1) + '"/>');
  const line = (x1, y1, x2, y2, w) => out.push('<line x1="' + x1 + '" y1="' + y1 + '" x2="' + x2 + '" y2="' + y2 + '" stroke="' + C.accent + '" stroke-width="' + (w || 2) + '"/>');
  const cell = (x, y, s) => {
    rect(x, y, cw, CH, C.cell, C.line);
    const max = cw > CW0 ? 9 : 4, t = String(s).length > max ? String(s).slice(0, max - 1) + '…' : String(s);
    out.push('<text x="' + (x + cw / 2) + '" y="' + (y + 15) + '" text-anchor="middle" fill="' + C.ink + '" font-size="12">' + esc(t) + '</text>');
  };
  const inner = m.inner, n = m.groups.length;
  const isize = g => {
    const k = g ? g.length : 0;
    if (inner === 'cell') return [cw, CH];
    if (inner === 'stack') return [cw + 2 * PAD, Math.max(k, 1) * CH + 2 * PAD];
    if (inner === 'queue') return [Math.max(k, 1) * cw + 2 * PAD + 2 * QA, CH + 2 * PAD + 4];
    return [Math.max(k, 1) * cw + Math.max(0, k - 1) * ARROW + 2 * PAD + ARROW + 22, CH + 2 * PAD];
  };
  // תור: קווים למעלה ולמטה, צדדים פתוחים; משמאל חץ החוצה (הראש — מוציאים), מימין חץ פנימה (הזנב — מכניסים)
  const arrowL = (fromX, toX, y, sw) => { line(fromX, y, toX + 4, y, sw); out.push('<polygon points="' + [toX, y, toX + 6, y - 4, toX + 6, y + 4].join(',') + '" fill="' + C.accent + '"/>'); };
  const tube = (x, y, len, h, outX, inX, sw) => {
    line(x - 3, y - 2, x + len + 3, y - 2, sw); line(x - 3, y + h + 2, x + len + 3, y + h + 2, sw);
    const mid = y + h / 2; arrowL(x - 2, outX, mid, sw > 2 ? 2 : 1.5); arrowL(inX, x + len + 2, mid, sw > 2 ? 2 : 1.5);
  };
  const drawInner = (x, y, g, w, h) => {
    if (inner === 'cell') { cell(x, y, g && g.length ? g[0] : ''); return; }
    rect(x, y, w, h, C.fill, C.line, 8);
    const k = g ? g.length : 0;
    if (k === 0) text(g ? 'ריק' : 'null', x + PAD + 2, y + h / 2 - 7, C.muted);
    if (inner === 'stack') { for (let i = 0; i < k; i++) cell(x + PAD, y + PAD + i * CH, g[k - 1 - i]); if (k) text('▼', x + w - 11, y + PAD + 3, C.accent); }
    else if (inner === 'queue') { const cx0 = x + PAD + QA, cy = y + PAD + 2; for (let i = 0; i < k; i++) cell(cx0 + i * cw, cy, g[i]); tube(cx0, cy, Math.max(k, 1) * cw, CH, x + 2, cx0 + Math.max(k, 1) * cw + QA - 2, 2); }
    else { let cx = x + PAD; for (let i = 0; i < k; i++) { cell(cx, y + PAD, g[i]); text(i < k - 1 ? '→' : '→ null', cx + cw + 1, y + PAD + 4, C.accent); cx += cw + (i < k - 1 ? ARROW : 0); } }
  };
  let W = 40, H = 20;
  if (m.outer === 'card') {
    const student = inner === 'Student';
    const fields = student ? ['name: "Noa"', 'classRoom: "11B"', 'age: 17', 'grades: [90, 85, 77]'] : ['manufacturer: "Mazda"', 'model: "3"', 'year: 2019', 'km, fuel...'];
    W = 180; H = 34 + fields.length * 17;
    rect(4, 2, 170, H - 4, C.fill, C.line, 10);
    text(student ? 'Student' : 'Car', 14, 8, C.accent, true);
    fields.forEach((f, i) => text(f, 14, 28 + i * 17));
  } else if (n === 0) {
    text('ריק — אין איברים', 6, 2, C.muted); W = 130;
  } else if (m.outer === 'matrix') {
    let cols = 0; for (const r of m.groups) cols = Math.max(cols, r ? r.length : 0);
    const left = 26, top = 16;
    for (let j = 0; j < cols; j++) text('[' + j + ']', left + j * cw + cw / 2 - 8, 0, C.muted);
    m.groups.forEach((r, i) => { text('[' + i + ']', 2, top + i * CH + 4, C.muted); (r || []).forEach((x, j) => cell(left + j * cw, top + i * CH, x)); });
    W = left + cols * cw + 8; H = top + n * CH + 6;
  } else if (m.outer === 'stack') {
    let w = 0, h = 0; for (const g of m.groups) { const s = isize(g); w = Math.max(w, s[0]); h += s[1] + 4; }
    const bx = 6, by = 4; let cy = by + 4;
    for (let i = n - 1; i >= 0; i--) { const s = isize(m.groups[i]); drawInner(bx + 4, cy, m.groups[i], w, s[1]); cy += s[1] + 4; }
    out.push('<polyline points="' + [bx, by, bx, by + h + 4, bx + w + 8, by + h + 4, bx + w + 8, by].join(',') + '" fill="none" stroke="' + C.accent + '" stroke-width="3"/>');
    text('← top', bx + w + 14, by + 4 + isize(m.groups[n - 1])[1] / 2 - 7, C.accent);
    W = w + 70; H = h + 14;
  } else if (m.outer === 'tree') {
    let levelH = 0, cellW = 0;
    for (const g of m.groups) if (g) { const s = isize(g); cellW = Math.max(cellW, s[0]); levelH = Math.max(levelH, s[1]); }
    const present = new Array(n).fill(false);
    const mark2 = i => { if (i >= n || !m.groups[i]) return; present[i] = true; mark2(2 * i + 1); mark2(2 * i + 2); };
    mark2(0);
    const depth = new Array(n).fill(0), order = new Array(n).fill(0); let counter = 0, maxDepth = 0;
    const num = (i, d) => { if (i >= n || !present[i]) return; num(2 * i + 1, d + 1); depth[i] = d; order[i] = counter++; maxDepth = Math.max(maxDepth, d); num(2 * i + 2, d + 1); };
    num(0, 0);
    const stepX = cellW + 10, stepY = levelH + 22;
    for (let i = 0; i < n; i++) if (present[i]) for (const c of [2 * i + 1, 2 * i + 2]) if (c < n && present[c])
      line(4 + order[i] * stepX + cellW / 2, depth[i] * stepY + levelH, 4 + order[c] * stepX + cellW / 2, depth[c] * stepY);
    for (let i = 0; i < n; i++) if (present[i]) { const s = isize(m.groups[i]); drawInner(4 + order[i] * stepX + (cellW - s[0]) / 2, depth[i] * stepY + (levelH - s[1]) / 2, m.groups[i], s[0], s[1]); }
    if (present[0]) text('root', 4 + order[0] * stepX + cellW + 2, 0, C.muted);
    W = counter * stepX + 38; H = (maxDepth + 1) * stepY;
  } else {   // תור / רשימה / מערך
    const queue = m.outer === 'queue', arrows = m.outer === 'list', gap = arrows ? 22 : 6;
    let w = 8, h = 0; for (const g of m.groups) { const s = isize(g); w += s[0] + gap; h = Math.max(h, s[1]); }
    const top = queue ? 6 : 4; let cx = 6 + (queue ? OA : 0);
    if (queue) tube(OA + 4, top - 2, w - 8, h + 6, 2, w + 2 * OA - 2, 3);
    for (let i = 0; i < n; i++) {
      const s = isize(m.groups[i]);
      drawInner(cx, top + (h - s[1]) / 2 + (queue ? 1 : 0), m.groups[i], s[0], s[1]);
      if (arrows) text(i < n - 1 ? '→' : '→ null', cx + s[0] + 3, top + h / 2 - 7, C.accent);
      if (!arrows && !queue) text('[' + i + ']', cx + s[0] / 2 - 8, top + h + 4, C.muted);
      cx += s[0] + gap;
    }
    if (queue) { text('head', OA + 6, top + h + 8, C.accent); if (n > 1) text('tail', cx - gap - 22, top + h + 8, C.accent); }
    W = w + (arrows ? 28 : 0) + (queue ? 2 * OA : 0); H = h + (queue ? 30 : arrows ? 12 : 26);
  }
  return '<svg xmlns="http://www.w3.org/2000/svg" width="' + Math.ceil(W) + '" height="' + Math.ceil(H) + '" viewBox="0 0 ' + Math.ceil(W) + ' ' + Math.ceil(H) + '" style="max-width:100%;height:auto" font-family="inherit">' + out.join('') + '</svg>';
}
vscode.postMessage({ type: 'state', state, reason: 'init' });
</script></body></html>`;
}

/** הפריטים של החלון (מבנים, "מה יש בכל תא?", טיפוסים) — לפי התבניות של השפה */
function options(tpl, initial) {
    return {
        structures: Object.entries(tpl.SNIPPETS).map(([id, label]) => [id, label]),
        inside: tpl.INSIDE.map(i => [i.id, i.label, i.description]),
        types: Object.keys(tpl.TYPES),
        initial
    };
}

module.exports = { Session, html, options, MAX_INNER };
