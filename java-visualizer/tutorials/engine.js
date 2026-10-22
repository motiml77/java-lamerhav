/*
 * מנוע סרטוני ההדרכה של DSViz. כל סרטון הוא תסריט (scripts/<id>.js) שמתאר מה קורה ומתי:
 * העכבר זז ולוחץ, נפתח חלון (צילום אמיתי של החלון של התוסף), נכנס קוד לעורך, חלון ההרצה מתקדם צעד אחר צעד...
 * render(t) מצייר את המצב בזמן t (בלי מצב פנימי) — וכך shoot.js מצלם כל פריים בנפרד, כמו בסרטון הפרומו.
 */
(function () {
  const $ = id => document.getElementById(id);
  const clamp = (x, a = 0, b = 1) => Math.min(b, Math.max(a, x));
  const p = (t, a, b) => clamp((t - a) / (b - a));
  const lerp = (a, b, x) => a + (b - a) * x;
  const eo = x => 1 - Math.pow(1 - x, 3);
  const eo5 = x => 1 - Math.pow(1 - x, 5);
  const eio = x => x < .5 ? 4 * x * x * x : 1 - Math.pow(-2 * x + 2, 3) / 2;
  const back = x => { const c1 = 1.8, c3 = c1 + 1; return 1 + c3 * Math.pow(x - 1, 3) + c1 * Math.pow(x - 1, 2); };
  function css(el, op, tf, blur) {
    el.style.opacity = op;
    el.style.transform = tf || 'none';
    el.style.filter = blur > 0.05 ? `blur(${blur}px)` : 'none';
  }
  function slam(t, t0, from = 2.4, dur = .42) { const x = p(t, t0, t0 + dur); return { op: p(t, t0, t0 + .1), s: lerp(from, 1, eo5(x)), blur: (1 - eo(x)) * 24 }; }
  function burst(t, t1, dur = .3) { const x = p(t, t1, t1 + dur); return { op: 1 - x, s: 1 + eio(x) * .9, blur: x * 22 }; }
  function show(el, t, t0, t1, opt = {}) {
    if (t < t0 - .01 || t > t1 + .4) { el.style.opacity = 0; return; }
    const a = slam(t, t0, opt.from ?? 2.4, opt.dur ?? .42), b = burst(t, t1);
    css(el, a.op * b.op, `scale(${a.s * b.s}) ${opt.extra || ''}`, a.blur + b.blur);
  }

  const APP_W = 1300, APP_H = 880, LINE_H = 28, GUTTER = 64;
  const DLG_TITLE = 32;
  const VIEW = { w: 1440, h: 828, s: 1240 / 1440, next: [1280, 30] };   // חלון ההרצה (צילום 1440x828), הכפתור "הבא"

  // ------------------------------------------------------------ הדגשת תחביר (בערך כמו ב-Eclipse)
  const KW = new Set('public private protected static void int boolean char double new return if else while for class import null true false this final do break continue'.split(' '));
  const TY = new Set('Stack Queue Node BinNode Student Car String Integer Double Character System Math Arrays'.split(' '));
  function esc(s) { return s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;'); }
  function highlight(line) {
    let out = '', i = 0;
    const ci = line.indexOf('//');
    let code = line, cm = '';
    // הערה: מה-// הראשון שלא בתוך מחרוזת
    let inStr = false;
    for (let k = 0; k < line.length - 1; k++) {
      if (line[k] === '"' && line[k - 1] !== '\\') inStr = !inStr;
      if (!inStr && line[k] === '/' && line[k + 1] === '/') { code = line.slice(0, k); cm = line.slice(k); break; }
    }
    const re = /("(?:[^"\\]|\\.)*"|'(?:[^'\\]|\\.)*'|\b[A-Za-z_]\w*\b|\d+(?:\.\d+)?|\s+|.)/g;
    let m;
    while ((m = re.exec(code))) {
      const w = m[0];
      if (w[0] === '"' || w[0] === "'") out += `<span class="st">${esc(w)}</span>`;
      else if (KW.has(w)) out += `<span class="kw">${w}</span>`;
      else if (TY.has(w)) out += `<span class="ty">${w}</span>`;
      else out += esc(w);
    }
    if (cm) out += /[֐-׿]/.test(cm) ? `<span class="cm">//</span><span class="cm hebcm">${esc(cm.slice(2))}</span>` : `<span class="cm">${esc(cm)}</span>`;
    return out;
  }

  // ------------------------------------------------------------ בניית התסריט
  class Builder {
    constructor(meta) {
      this.meta = meta;
      this.ev = [];
      this.now = 0;
      this.cur = { x: 760, y: 560 };
      this.scroll = 0;
      this.dlg = null;      // { asset, x, y }
      this.view = null;     // { x, y, s }
      this.introEnd = 0;
      this.outroAt = null;
    }
    add(e) { e.t = e.t ?? this.now; this.ev.push(e); return e; }
    wait(s) { this.now += s; return this; }
    at(t) { this.now = t; return this; }

    // --- מטרות (נקודות במסך של האפליקציה)
    pt(target) {
      if (typeof target === 'object') return target;
      const [kind, ...rest] = target.split(':');
      const arg = rest.join(':');
      if (kind === 'tb') {
        const el = $({ add: 'tbAdd', new: 'tbNew', run: 'tbRun', help: 'tbHelp' }[arg]);
        return this.rectOf(el, 'center');
      }
      if (kind === 'menu') {   // התפריט "מבני נתונים"
        return this.rectOf(document.querySelector('#ide .menubar .ds'), 'center');
      }
      if (kind === 'line') {   // line:L או line:L:C (C = עמודה, מ-0)
        const [l, c] = arg.split(':').map(Number);
        const r = this.codeRect();
        return { x: r.x + GUTTER + (c || 0) * this.charW + (c === undefined ? 120 : 0), y: r.y + (l - 1 - this.scroll) * LINE_H + LINE_H / 2 };
      }
      if (kind === 'menuitem') {   // menuitem:טקסט (חלק מהטקסט)
        const m = this.menuGeom();
        const i = MENU_ITEMS.findIndex(t => t !== '---' && t.includes(arg));
        if (i < 0) throw new Error('menu item not found: ' + arg);
        return { x: m.left + MENU_W / 2, y: m.top + m.ys[i] + MI_H / 2 };
      }
      if (kind === 'tree') {   // tree:שם הקובץ
        for (const row of document.querySelectorAll('#tree .row')) if (row.textContent.trim() === arg) return this.rectOf(row.querySelector('span:last-child') || row, 'center');
        throw new Error('tree row not found: ' + arg);
      }
      if (kind === 'view') {
        const v = this.view;
        if (arg === 'next') return { x: v.x + VIEW.next[0] * v.s, y: v.y + DLG_TITLE + VIEW.next[1] * v.s };
        const [xx, yy] = arg.split(',').map(Number);   // view:x,y — נקודה בצילום של חלון ההרצה
        return { x: v.x + xx * v.s, y: v.y + DLG_TITLE + yy * v.s };
      }
      if (kind === 'dlg') {   // dlg:chip:טקסט  או  dlg:spin@0.4,0.5 (נקודה יחסית בתוך הרכיב)
        const [sel, frac] = arg.split('@');
        const r = this.dlgRect(sel);
        const [fx, fy] = frac ? frac.split(',').map(Number) : [.5, .5];
        return { x: r.x + r.w * fx, y: r.y + r.h * fy };
      }
      throw new Error('bad target ' + target);
    }
    /** מלבן של רכיב בחלון: chip:טקסט · round:טקסט · check:חלק · text:אינדקס · label:חלק · spin */
    dlgRect(arg) {
      const d = this.dlg, a = ASSETS[d.asset];
      const [k, ...rr] = arg.split(':');
      const v = rr.join(':');
      const types = { chip: ['Chip'], round: ['Round'], check: ['Button'], text: ['Text'], label: ['Label'], spin: ['Spinner'], combo: ['Combo'] }[k];
      const c = a.controls.find(c => types.includes(c.type) && (k === 'text' ? String(c.index) === v : k === 'spin' || k === 'combo' ? true : k === 'chip' || k === 'round' ? c.text === v : (c.text || '').includes(v)));
      if (!c) throw new Error('control not found ' + arg + ' in ' + d.asset);
      return { x: d.x + c.x, y: d.y + DLG_TITLE + c.y, w: c.w, h: c.h };
    }
    /** מלבן שמכיל כמה רכיבים בחלון (להדגשה / זום) */
    area(sels, pad = 8, growH = 0) {
      const rs = sels.map(x => this.dlgRect(x));
      const x0 = Math.min(...rs.map(r => r.x)), y0 = Math.min(...rs.map(r => r.y));
      const x1 = Math.max(...rs.map(r => r.x + r.w)), y1 = Math.max(...rs.map(r => r.y + r.h));
      return { x: x0 - pad, y: y0 - pad, w: x1 - x0 + 2 * pad, h: y1 - y0 + 2 * pad + growH };
    }
        rectOf(el, mode) {
      const app = $('cam').getBoundingClientRect(), r = el.getBoundingClientRect();
      const x = r.left - app.left, y = r.top - app.top;
      return mode === 'center' ? { x: x + r.width / 2, y: y + r.height / 2 } : { x, y, w: r.width, h: r.height };
    }
    menuGeom() {
      const ds = this.rectOf(document.querySelector('#ide .menubar .ds'), 'rect');
      const ys = []; let y = MENU_PAD;
      for (const t of MENU_ITEMS) { ys.push(y); y += t === '---' ? SEP_H : MI_H; }
      return { left: Math.max(10, ds.x + ds.w - MENU_W + 40), top: ds.y + ds.h + 2, ys, h: y + MENU_PAD };
    }
    menuOpen() { this.add({ type: 'menu', geom: this.menuGeom() }); this.now += .35; return this; }
    menuHot(text) { const i = MENU_ITEMS.findIndex(t => t !== '---' && t.includes(text)); this.add({ type: 'menuHot', idx: i }); return this; }
    menuClose(after = .3) { this.add({ type: 'menuClose' }); this.now += after; return this; }
    codeRect() { return this.rectOf($('code'), 'rect'); }

    // --- פעולות
    intro(dur = 3.4) { this.introEnd = dur; this.now = dur + .9; return this; }
    step(i, extra = 0) { this.add({ type: 'step', idx: i }); this.now += extra; return this; }
    files(rows) { this.add({ type: 'tree', rows }); return this; }
    title(text) { this.add({ type: 'title', text }); return this; }
    code(lines, opt = {}) {
      this.add({ type: 'code', lines: lines.slice(), file: opt.file, newFrom: opt.newFrom, newTo: opt.newTo, edit: opt.edit });
      if (opt.file) this.add({ type: 'tab', name: opt.file });
      return this;
    }
    caret(line, col) { this.add({ type: 'caret', line, col }); return this; }
    noCaret() { this.add({ type: 'caret', line: null }); return this; }
    scrollTo(line, dur = .6) { this.add({ type: 'scroll', from: this.scroll, to: line, dur }); this.scroll = line; this.now += dur; return this; }
    move(target, dur = .8) {
      const to = this.pt(target);
      this.add({ type: 'move', from: { ...this.cur }, to, dur });
      this.cur = { ...to };
      this.now += dur;
      return this;
    }
    click(after = .35) { this.add({ type: 'click', x: this.cur.x, y: this.cur.y }); CLICKS.push(this.now); this.now += after; return this; }
    moveClick(target, dur = .8, after = .35) { return this.move(target, dur).wait(.12).click(after); }
    hot(el, dur = .5) { this.add({ type: 'hot', el, dur }); return this; }
    dialog(asset, title, opt = {}) {
      const a = ASSETS[asset];
      const x = opt.x ?? Math.round((APP_W - a.w) / 2), y = opt.y ?? Math.max(40, Math.round((APP_H - a.h - DLG_TITLE) / 2) + 20);
      this.dlg = { asset, x, y };
      this.add({ type: 'dialog', asset, x, y, title, w: a.w, h: a.h });
      this.now += opt.wait ?? .7;
      return this;
    }
    swap(asset) { this.dlg = { ...this.dlg, asset }; this.add({ type: 'swap', asset }); return this; }
    closeDialog(after = .5) { this.add({ type: 'dialogClose' }); this.now += after; return this; }
    type(field, text, dur, after) {   // field: text:אינדקס
      const r = this.dlgRect(field);
      dur = dur ?? Math.max(.6, text.length * .07);
      this.add({ type: 'typing', rect: r, text, dur });
      this.now += dur;
      if (after) this.swap(after);
      return this;
    }
    viewer(img, title, opt = {}) {
      const s = opt.s ?? VIEW.s;
      const x = opt.x ?? Math.round((APP_W - VIEW.w * s) / 2), y = opt.y ?? 46;
      this.view = { x, y, s };
      this.add({ type: 'viewer', img, title, x, y, s });
      this.now += opt.wait ?? .8;
      return this;
    }
    frame(img) { this.add({ type: 'vframe', img }); return this; }
    /** לוחצים "הבא" כמה פעמים ומחליפים צילומים */
    steps(imgs, gap = .55) {
      for (const im of imgs) { this.move('view:next', .25).click(.05).frame(im).wait(gap); }
      return this;
    }
    closeViewer(after = .5) { this.add({ type: 'viewerClose' }); this.now += after; return this; }
    hl(rect, dur = 1.6, label) { this.add({ type: 'hl', rect: typeof rect === 'string' ? this.dlgRectOrEl(rect) : rect, dur }); return this; }
    dlgRectOrEl(s) {
      if (s.startsWith('dlg:')) { const r = this.dlgRect(s.slice(4)); return { x: r.x - 8, y: r.y - 6, w: r.w + 16, h: r.h + 12 }; }
      if (s.startsWith('el:')) { const r = this.rectOf(document.querySelector(s.slice(3)), 'rect'); return { x: r.x - 6, y: r.y - 4, w: r.w + 12, h: r.h + 8 }; }
      if (s.startsWith('lines:')) {
        const [a, b] = s.slice(6).split('-').map(Number), r = this.codeRect();
        return { x: r.x + GUTTER - 6, y: r.y + (a - 1 - this.scroll) * LINE_H - 2, w: r.w - GUTTER - 10, h: (b - a + 1) * LINE_H + 4 };
      }
      if (s.startsWith('view:')) {
        const [x, y, w, h] = s.slice(5).split(',').map(Number), v = this.view;
        return { x: v.x + x * v.s, y: v.y + DLG_TITLE + y * v.s, w: w * v.s, h: h * v.s };
      }
      throw new Error('bad rect ' + s);
    }
    bubble(text, target, opt = {}) {
      let at;
      if (typeof target === 'string' && /^(dlg:|el:|lines:|view:\d)/.test(target)) {
        const r = this.dlgRectOrEl(target);
        const side = opt.side || 'top';
        at = side === 'top' ? { x: r.x + r.w / 2, y: r.y } : side === 'bottom' ? { x: r.x + r.w / 2, y: r.y + r.h } : side === 'left' ? { x: r.x, y: r.y + r.h / 2 } : { x: r.x + r.w, y: r.y + r.h / 2 };
      } else at = this.pt(target);
      this.add({ type: 'bubble', text, at, side: opt.side || 'top', kind: opt.kind || 'info', dur: opt.dur ?? 2.2 });
      if (opt.wait) this.now += opt.wait;
      return this;
    }
    zoom(rect, dur = .8, maxS = 2) {
      const r = typeof rect === 'string' ? this.dlgRectOrEl(rect) : rect;
      const s = Math.min(maxS, APP_W / r.w * .92, APP_H / r.h * .92);
      this.add({ type: 'zoom', to: { s, cx: r.x + r.w / 2, cy: r.y + r.h / 2 }, dur });
      this.now += dur;
      return this;
    }
    unzoom(dur = .7) { this.add({ type: 'zoom', to: { s: 1, cx: APP_W / 2, cy: APP_H / 2 }, dur }); this.now += dur; return this; }
    outro(sub, dur = 4.2) { this.outroAt = this.now + .3; this.now = this.outroAt + dur; return this; }
  }
  const CLICKS = [];
  const MENU_ITEMS = ['תרגיל חדש: מחסנית (Stack)...', 'תרגיל חדש: תור (Queue)...', 'תרגיל חדש: רשימה מקושרת (Node)...', 'תרגיל חדש: עץ בינארי (BinNode)...',
    'תרגיל חדש: עץ חיפוש בינארי...', 'תרגיל חדש: מערך...', 'תרגיל חדש: מערך דו-ממדי...', '---', 'הוסף מבנה נתונים...', '---', 'הרצה ויזואלית', '---', 'דוגמאות פתורות...', 'הדרכה (?)...'];
  const MI_H = 32, SEP_H = 10, MENU_PAD = 4, MENU_W = 360;

  // ------------------------------------------------------------ ציור
  let B, META, EV, DUR;
  const imgCache = {};
  function imgEl(src) {
    if (!imgCache[src]) { const im = new Image(); im.src = src; imgCache[src] = im; }
    return imgCache[src];
  }

  function last(type, t, pred) {
    let r = null;
    for (const e of EV) if (e.t <= t + 1e-6 && e.type === type && (!pred || pred(e))) r = e;
    return r;
  }

  // רקע — כמו בסרטון הפרומו
  let DOTS = [];
  function bgInit() {
    let seed = 11; const rnd = () => (seed = (seed * 16807) % 2147483647) / 2147483647;
    for (let i = 0; i < 46; i++) {
      const d = document.createElement('div'); d.className = 'dot';
      DOTS.push({ el: d, x: rnd() * 1920, y: rnd() * 1080, v: 10 + rnd() * 40, r: .4 + rnd() * 1.4, ph: rnd() * 6 });
      $('dots').appendChild(d);
    }
    for (let i = 0; i < 7; i++) $('bars').appendChild(document.createElement('div'));
  }
  function renderBg(t) {
    const [c1, c2, c3] = META.pal;
    [[$('b1'), c1, 260, -300, .23], [$('b2'), c2, 1250, 300, .17], [$('b3'), c3, 500, 500, .29]].forEach(([el, c, x, y, w], i) => {
      el.style.background = `radial-gradient(circle, ${c}cc 0%, ${c}55 35%, transparent 68%)`;
      el.style.left = (x + Math.sin(t * w + i * 2) * 260) + 'px';
      el.style.top = (y + Math.cos(t * w * 1.3 + i) * 200) + 'px';
      el.style.opacity = .5;
    });
    $('grid').style.transform = 'perspective(900px) rotateX(72deg)';
    $('grid').style.backgroundPosition = `0 ${(t * 60) % 120}px`;
    for (const d of DOTS) {
      const y = (d.y - t * d.v + 1080 * 40) % 1080;
      d.el.style.transform = `translate(${d.x + Math.sin(t + d.ph) * 20}px, ${y}px) scale(${d.r})`;
      d.el.style.opacity = .2 + .3 * Math.sin(t * 2 + d.ph) ** 2;
    }
  }

  function renderIntro(t) {
    const end = B.introEnd;
    $('intro').style.display = t < end + .6 ? 'block' : 'none';
    if (t >= end + .6) return;
    $('inum').textContent = META.num;
    $('ititle').textContent = META.title;
    $('isub').textContent = META.sub;
    const nIn = p(t, .05, .6), nOut = p(t, end - .5, end);
    css($('inum'), nIn * (1 - nOut), `translateX(${lerp(-300, 0, eo(nIn)) - nOut * 500}px) scale(${1 + t * .04})`, nOut * 20);
    show($('ikick'), t, .3, end - .35, { from: .5, dur: .3 });
    show($('ititle'), t, .45, end - .35, { from: 3 });
    show($('isub'), t, .85, end - .35, { from: .6, dur: .35 });
  }

  function renderTop(t) {
    const x = eo5(p(t, B.introEnd + .2, B.introEnd + .8));
    const out = B.outroAt != null ? p(t, B.outroAt, B.outroAt + .4) : 0;
    $('tbadge').textContent = 'הדרכה ' + META.num;
    $('tttl').textContent = META.title;
    css($('top'), x * (1 - out), `translateY(${lerp(-40, 0, x)}px)`, (1 - x) * 8);
  }

  function renderApp(t) {
    const t0 = B.introEnd + .15;
    const w = eo5(p(t, t0, t0 + 1.0));
    const out = B.outroAt != null ? eio(p(t, B.outroAt - .1, B.outroAt + .45)) : 0;
    const rotY = lerp(-30, 0, w), rotX = lerp(24, 0, w);
    $('app3d').style.opacity = p(t, t0, t0 + .25) * (1 - out);
    $('app').style.transform = `translateY(${lerp(700, 0, w) + out * 60}px) rotateY(${rotY}deg) rotateX(${rotX}deg) scale(${lerp(.75, 1, w) * (1 - out * .15)})`;
    $('app').style.filter = out > .02 ? `blur(${out * 16}px)` : 'none';

    // מצלמה (זום)
    let cam = { s: 1, cx: APP_W / 2, cy: APP_H / 2 }, prev = { ...cam };
    for (const e of EV) {
      if (e.type !== 'zoom' || e.t > t) continue;
      const k = eio(p(t, e.t, e.t + e.dur));
      prev = { ...cam };
      cam = { s: lerp(prev.s, e.to.s, k), cx: lerp(prev.cx, e.to.cx, k), cy: lerp(prev.cy, e.to.cy, k) };
      if (k >= 1) cam = { ...e.to };
    }
    $('cam').style.transform = `translate(${APP_W / 2 - cam.cx * cam.s}px, ${APP_H / 2 - cam.cy * cam.s}px) scale(${cam.s})`;

    renderIde(t);
    renderLayers(t);
    renderFx(t);
  }

  let lastCodeKey = null;
  function renderIde(t) {
    const tr = last('tree', t);
    if (tr && $('tree').dataset.k !== String(EV.indexOf(tr))) {
      $('tree').dataset.k = String(EV.indexOf(tr));
      $('tree').innerHTML = tr.rows.map(r => {
        const [depth, name, sel] = r;
        const icon = name.endsWith('.java') ? '<span style="display:inline-block;width:14px;height:14px;border-radius:3px;background:#3d7be0;margin-right:6px"></span>'
                   : '<span style="display:inline-block;width:14px;height:12px;border-radius:2px;background:#e5b84b;margin-right:6px"></span>';
        return `<div class="row${sel ? ' sel' : ''}" style="padding-left:${12 + depth * 16}px">${name.endsWith('.java') ? '' : '▾ '}${icon}<span>${name}</span></div>`;
      }).join('');
    }
    const tab = last('tab', t);
    if (tab) { $('tabName').textContent = tab.name; $('ideTitle').textContent = `DataStructures - ${tab.name} - Eclipse IDE`; }

    const ce = last('code', t);
    if (ce) {
      const key = EV.indexOf(ce);
      if (key !== lastCodeKey) {
        lastCodeKey = key;
        $('lines').innerHTML = ce.lines.map((l, i) => `<div class="ln" style="top:${i * LINE_H}px"><span class="newbg" style="opacity:0"></span><span class="curbg" style="opacity:0"></span><span class="no" style="position:relative">${i + 1}</span><span class="tx" style="position:relative;display:inline-block">${highlight(l)}</span></div>`).join('');
      }
      const rows = $('lines').children;
      for (let i = 0; i < rows.length; i++) {
        const ln = i + 1, isNew = ce.newFrom && ln >= ce.newFrom && ln <= ce.newTo;
        const nb = rows[i].children[0], tx = rows[i].children[3];
        if (isNew) {
          const k = i - ce.newFrom + 1;
          const rev = eo(p(t, ce.t + k * .07, ce.t + k * .07 + .35));
          tx.style.clipPath = `inset(0 ${100 - rev * 100}% 0 0)`;
          nb.style.opacity = rev * (1 - p(t, ce.t + 2.2, ce.t + 3.6)) * .9;
        } else { tx.style.clipPath = 'none'; nb.style.opacity = 0; }
      }
    }
    // גלילה
    let sc = 0;
    for (const e of EV) if (e.type === 'scroll' && e.t <= t) sc = lerp(e.from, e.to, eio(p(t, e.t, e.t + e.dur)));
    $('lines').style.transform = `translateY(${-sc * LINE_H}px)`;
    // סמן כתיבה
    const ca = last('caret', t);
    const caret = $('caret');
    if (ca && ca.line != null) {
      caret.style.display = 'block';
      caret.style.left = (GUTTER + ca.col * CHAR_W) + 'px';
      caret.style.top = ((ca.line - 1 - sc) * LINE_H + 2) + 'px';
      caret.style.opacity = Math.floor((t - ca.t) * 2.2) % 2 === 0 ? 1 : 0;
      // השורה של הסמן — רקע כחול בהיר
      const rows = $('lines').children;
      for (let i = 0; i < rows.length; i++) rows[i].children[1].style.opacity = i === ca.line - 1 ? 1 : 0;
    } else {
      caret.style.display = 'none';
      for (const r of $('lines').children) r.children[1].style.opacity = 0;
    }
    // תפריט נפתח
    const mo = last('menu', t), mc = last('menuClose', t), menu = $('menu');
    if (mo && (!mc || mc.t < mo.t)) {
      const g = mo.geom, k = eo(p(t, mo.t, mo.t + .18));
      if (menu.dataset.k !== String(EV.indexOf(mo))) {
        menu.dataset.k = String(EV.indexOf(mo));
        menu.innerHTML = MENU_ITEMS.map(x => x === '---' ? '<div class="sepl"></div>' : `<div class="mi">${x}</div>`).join('');
      }
      menu.style.display = 'block'; menu.style.left = g.left + 'px'; menu.style.top = g.top + 'px'; menu.style.width = MENU_W + 'px';
      menu.style.opacity = k; menu.style.transform = `translateY(${(1 - k) * -8}px)`;
      const hot = last('menuHot', t, e => e.t >= mo.t);
      const items = menu.querySelectorAll('.mi');
      let n = 0;
      MENU_ITEMS.forEach((x, i) => { if (x !== '---') { items[n].classList.toggle('hot', !!hot && hot.idx === i); n++; } });
    } else menu.style.display = 'none';
    // כפתורים "חמים" בסרגל
    for (const id of ['tbNew', 'tbAdd', 'tbRun', 'tbHelp']) $(id).classList.remove('hot');
    for (const e of EV) if (e.type === 'hot' && t >= e.t && t <= e.t + e.dur) document.querySelector(e.el).classList.add('hot');
  }

  // חלון קופץ וחלון ההרצה
  let layersBuilt = false;
  const DLG = {}, VW = {};
  function buildLayers() {
    const L = $('layers');
    L.innerHTML = `<div class="win" id="dlgWin"><div class="wt"><span id="dlgTitle"></span><div class="x">✕</div></div><div id="dlgImgs" style="position:relative"></div><div id="dlgType"></div></div>
                   <div class="win" id="viewWin"><div class="wt"><span id="viewTitle"></span><div class="x">✕</div></div><div id="viewImgs" style="position:relative"></div></div>`;
    const assets = new Set(EV.filter(e => e.type === 'dialog' || e.type === 'swap').map(e => e.asset));
    for (const a of assets) { const im = document.createElement('img'); im.src = ASSETS[a].src; im.style.display = 'none'; $('dlgImgs').appendChild(im); DLG[a] = im; }
    const views = new Set(EV.filter(e => e.type === 'viewer' || e.type === 'vframe').map(e => e.img));
    for (const v of views) { const im = document.createElement('img'); im.src = v; im.style.display = 'none'; $('viewImgs').appendChild(im); VW[v] = im; }
    layersBuilt = true;
  }
  function renderLayers(t) {
    if (!layersBuilt) buildLayers();
    // דיאלוג
    const open = last('dialog', t), close = last('dialogClose', t);
    const win = $('dlgWin');
    if (open && (!close || close.t < open.t || t < close.t + .3)) {
      const closing = close && close.t >= open.t ? p(t, close.t, close.t + .25) : 0;
      const k = back(p(t, open.t, open.t + .38));
      win.style.left = open.x + 'px'; win.style.top = open.y + 'px';
      $('dlgTitle').textContent = open.title;
      css(win, p(t, open.t, open.t + .12) * (1 - closing), `scale(${lerp(.82, 1, k) * (1 - closing * .08)})`, 0);
      let asset = open.asset;
      for (const e of EV) if (e.type === 'swap' && e.t >= open.t && e.t <= t) asset = e.asset;
      for (const a in DLG) DLG[a].style.display = a === asset ? 'block' : 'none';
      // הקלדה
      const ty = EV.filter(e => e.type === 'typing' && e.t <= t && e.t >= open.t).pop();
      const tl = $('dlgType');
      const swappedAfter = ty && EV.some(e => e.type === 'swap' && e.t >= ty.t + ty.dur - 1e-6 && e.t <= t);
      if (ty && !swappedAfter) {
        const n = Math.round(p(t, ty.t, ty.t + ty.dur) * ty.text.length);
        const r = ty.rect;
        tl.innerHTML = `<div class="typing" style="left:${r.x - open.x + 2}px;top:${r.y - open.y + 2}px;width:${r.w - 4}px;height:${r.h - 4}px">${esc(ty.text.slice(0, n))}<span style="display:inline-block;width:1.5px;height:16px;background:#111;margin-left:1px;opacity:${Math.floor(t * 3) % 2 ? 1 : .2}"></span></div>`;
      } else tl.innerHTML = '';
    } else win.style.opacity = 0;

    // חלון ההרצה
    const vo = last('viewer', t), vc = last('viewerClose', t);
    const vw = $('viewWin');
    if (vo && (!vc || vc.t < vo.t || t < vc.t + .3)) {
      const closing = vc && vc.t >= vo.t ? p(t, vc.t, vc.t + .25) : 0;
      const k = eo5(p(t, vo.t, vo.t + .5));
      vw.style.left = vo.x + 'px'; vw.style.top = vo.y + 'px';
      $('viewTitle').textContent = vo.title;
      let img = vo.img;
      for (const e of EV) if (e.type === 'vframe' && e.t >= vo.t && e.t <= t) img = e.img;
      for (const v in VW) { VW[v].style.display = v === img ? 'block' : 'none'; VW[v].style.width = (VIEW.w * vo.s) + 'px'; }
      css(vw, p(t, vo.t, vo.t + .15) * (1 - closing), `translateY(${lerp(60, 0, k)}px) scale(${lerp(.9, 1, k)})`, (1 - k) * 6);
    } else vw.style.opacity = 0;
  }

  function renderFx(t) {
    const fx = [];
    // הדגשות
    for (const e of EV) if (e.type === 'hl' && t >= e.t && t <= e.t + e.dur + .3) {
      const a = p(t, e.t, e.t + .25) * (1 - p(t, e.t + e.dur, e.t + e.dur + .3));
      const pulse = 1 + Math.sin((t - e.t) * 7) * .012;
      fx.push(`<div class="hl" style="left:${e.rect.x}px;top:${e.rect.y}px;width:${e.rect.w}px;height:${e.rect.h}px;opacity:${a};transform:scale(${pulse})"></div>`);
    }
    // בועות
    for (const e of EV) if (e.type === 'bubble' && t >= e.t && t <= e.t + e.dur + .3) {
      const a = p(t, e.t, e.t + .2) * (1 - p(t, e.t + e.dur, e.t + e.dur + .3));
      const k = back(p(t, e.t, e.t + .35));
      const off = 22;
      const pos = { top: `left:${e.at.x}px;top:${e.at.y - off}px;transform:translate(-50%,-100%) scale(${k})`,
                    bottom: `left:${e.at.x}px;top:${e.at.y + off}px;transform:translate(-50%,0) scale(${k})`,
                    left: `left:${e.at.x - off}px;top:${e.at.y}px;transform:translate(-100%,-50%) scale(${k})`,
                    right: `left:${e.at.x + off}px;top:${e.at.y}px;transform:translate(0,-50%) scale(${k})` }[e.side];
      const tip = { top: 'left:calc(50% - 9px);bottom:-8px', bottom: 'left:calc(50% - 9px);top:-8px', left: 'right:-8px;top:calc(50% - 9px)', right: 'left:-8px;top:calc(50% - 9px)' }[e.side];
      fx.push(`<div class="bubble ${e.kind}" style="${pos};opacity:${a};transform-origin:${e.side === 'top' ? '50% 100%' : e.side === 'bottom' ? '50% 0' : e.side === 'left' ? '100% 50%' : '0 50%'}"><div class="tip" style="${tip}"></div>${e.text}</div>`);
    }
    // לחיצות
    for (const e of EV) if (e.type === 'click' && t >= e.t && t <= e.t + .55) {
      const k = p(t, e.t, e.t + .55), r = lerp(8, 46, eo(k));
      fx.push(`<div class="ripple" style="left:${e.x - r}px;top:${e.y - r}px;width:${r * 2}px;height:${r * 2}px;opacity:${1 - k}"></div>`);
    }
    $('fx').innerHTML = fx.join('');
    // העכבר
    let pos = { x: 760, y: 560 };
    for (const e of EV) {
      if (e.type !== 'move' || e.t > t) continue;
      const k = eio(p(t, e.t, e.t + e.dur));
      // מסלול מעט מעוגל — כמו יד אמיתית
      const mx = (e.from.x + e.to.x) / 2, my = (e.from.y + e.to.y) / 2 - Math.min(80, Math.hypot(e.to.x - e.from.x, e.to.y - e.from.y) * .12);
      pos = { x: (1 - k) * (1 - k) * e.from.x + 2 * (1 - k) * k * mx + k * k * e.to.x, y: (1 - k) * (1 - k) * e.from.y + 2 * (1 - k) * k * my + k * k * e.to.y };
    }
    let press = 0;
    for (const e of EV) if (e.type === 'click' && t >= e.t - .06 && t <= e.t + .16) press = 1;
    const cur = $('cursor');
    cur.style.transform = `translate(${pos.x - 3}px, ${pos.y - 3}px) scale(${press ? .86 : 1})`;
    cur.style.opacity = p(t, B.introEnd + 1.0, B.introEnd + 1.3);
  }

  function renderSteps(t) {
    const st = last('step', t);
    const box = $('steps');
    const x = eo5(p(t, B.introEnd + .5, B.introEnd + 1.1));
    const out = B.outroAt != null ? p(t, B.outroAt, B.outroAt + .4) : 0;
    css(box, x * (1 - out), `translateX(${lerp(-80, 0, x)}px)`, 0);
    const steps = META.steps;
    const idx = st ? st.idx : 0;
    const k = st ? eo5(p(t, st.t, st.t + .45)) : 1;
    $('stepN').textContent = `שלב ${idx + 1} מתוך ${steps.length}`;
    $('stepT').innerHTML = steps[idx][0];
    $('stepS').innerHTML = steps[idx][1];
    css($('stepCard'), k, `translateX(${lerp(-60, 0, k)}px)`, (1 - k) * 10);
    $('stepList').innerHTML = steps.map((s, i) => `<div class="it ${i < idx ? 'done' : i === idx ? 'cur' : ''}"><div class="c">${i < idx ? '✓' : i + 1}</div><div>${s[2] || s[0]}</div></div>`).join('');
  }

  function renderOutro(t) {
    const u = B.outroAt != null ? t - B.outroAt : -1;
    $('outro').style.display = u > -.2 ? 'block' : 'none';
    if (u <= -.2) return;
    $('o2').textContent = META.outro || '';
    show($('o1'), u, .35, 99, { from: 3 });
    show($('o2'), u, .7, 99, { from: .6, dur: .35 });
    show($('o3'), u, 1.0, 99, { from: 1.8, dur: .4 });
    show($('o4'), u, 1.4, 99, { from: .6, dur: .35 });
  }

  function renderBars(t) {
    const cuts = [B.introEnd + .05].concat(B.outroAt != null ? [B.outroAt] : []);
    let vis = false;
    const bars = $('bars').children;
    for (const cut of cuts) {
      const d = t - cut;
      if (d < -.45 || d > .45) continue;
      vis = true;
      [...bars].forEach((b, i) => {
        const lag = i * .035;
        const inn = eio(p(d, -.45 + lag, -.05 + lag)), out = eio(p(d, .02 + lag, .42 + lag));
        b.style.left = (i * 300 - 200) + 'px';
        b.style.background = `linear-gradient(180deg, ${META.pal[i % 3]}, ${META.pal[(i + 1) % 3]})`;
        b.style.transform = `translateX(${lerp(2300, 0, inn) - out * 2600}px) skewX(-14deg)`;
      });
    }
    $('bars').style.display = vis ? 'block' : 'none';
    $('fade').style.opacity = (DUR ? p(t, DUR - .5, DUR) : 0) + (1 - p(t, 0, .3));
  }

  function render(t) {
    renderBg(t); renderIntro(t); renderTop(t); renderApp(t); renderSteps(t); renderOutro(t); renderBars(t);
  }

  let CHAR_W = 10.2;
  window.Engine = {
    async start() {
      await document.fonts.ready;
      const probe = document.createElement('span');
      probe.style.cssText = 'font-family:JBMono;font-size:17px;position:absolute;visibility:hidden;white-space:pre';
      probe.textContent = 'x'.repeat(100);
      document.body.appendChild(probe);
      CHAR_W = probe.getBoundingClientRect().width / 100;
      probe.remove();
      META = window.META;
      document.getElementById('stage').style.setProperty('--c1', META.pal[0]);
      document.getElementById('stage').style.setProperty('--c2', META.pal[1]);
      document.getElementById('stage').style.setProperty('--c3', META.pal[2]);
      document.getElementById('stage').style.setProperty('--glow', META.pal[0] + '88');
      bgInit();
      B = new Builder(META);
      B.charW = CHAR_W;
      // התסריט צריך מדידות של העורך — מציגים אותו בלי טרנספורמציות בזמן הבנייה
      $('app').style.transform = 'none';
      window.SCRIPT(B);
      EV = B.ev.slice().sort((a, b) => a.t - b.t);
      DUR = B.now;
      window.DURATION = DUR;
      window.CLICK_TIMES = CLICKS.slice();
      window.CUT_TIMES = [B.introEnd + .05].concat(B.outroAt != null ? [B.outroAt] : []);
      await Promise.all([...document.images].map(i => i.decode().catch(() => 0)));
      buildLayers();
      await Promise.all([...document.images].map(i => i.decode().catch(() => 0)));
      render(0);
      window.READY = true;
    },
    render: t => render(t),
    Builder
  };
  window.render = t => render(t);
})();
