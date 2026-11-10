/*
 * canvas.js - הקנבס: תצוגה (זום, הזזה) וציור המכונה ב-SVG. האינטראקציה עם העכבר ב-canvas-input.js.
 * הדיאגרמה עצמה LTR (שרטוט מתמטי), גם כשכל הממשק מסביב RTL.
 * הניתוב של הקשתות והמיקום של התוויות: routing.js.
 */
(function (root) {
  'use strict';
  const M = root.AutomataModel, G = root.AutomataGeometry, RT = root.AutomataRouting, I = root.Icons;
  const R = G.R;
  const SNAP = 20;   // כמו המשבצת הקטנה
  const esc = (s) => String(s).replace(/[&<>"]/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' })[c]);
  // פח קטן (בתוך עיגול אדום), מצויר סביב 0,0
  const TRASH = '<circle class="trash-bg" r="10"/><path class="trash-ic" d="M-4.2-2.6h8.4M-1.6-2.6v-1.6h3.2v1.6M-3.2-2.6l.5 6.6h5.4l.5-6.6"/>';

  class CanvasView {
    constructor(app, host) {
      this.app = app;
      this.host = host;
      this.svg = host.querySelector('svg.canvas');
      this.vp = this.svg.querySelector('.viewport');
      this.gEdges = this.vp.querySelector('.edges');
      this.gLabels = this.vp.querySelector('.labels');
      this.gStates = this.vp.querySelector('.states');
      this.gOverlay = this.vp.querySelector('.overlay');
      this.gridMinor = this.svg.querySelector('#grid-minor');
      this.gridMajor = this.svg.querySelector('#grid-major');
      this.toolbar = host.querySelector('.state-toolbar');
      this.editor = host.querySelector('.inline-editor');
      this.drag = null;        // { kind: 'press' | 'move' | 'pan' | 'marquee', ... }
      this.hover = null;       // { kind: 'state' | 'edge', id }
      this.source = null;      // מצב המקור של מעבר שמתחיל (לחיצה ראשונה)
      this.pointer = null;     // מיקום הסמן בעולם (לחץ הגומי)
      this.preview = null;     // { from, to }: מעבר חדש שעוד לא אושר (החלון הצף פתוח)
      this.spaceDown = false;
      this.routes = new Map();
      this.bind();
    }

    get m() { return this.app.machine; }
    get view() { return this.m.view; }
    static get SNAP() { return SNAP; }

    /* ---------- המרות קואורדינטות ---------- */
    toWorld(e) {
      const r = this.svg.getBoundingClientRect();
      return { x: (e.clientX - r.left - this.view.x) / this.view.zoom, y: (e.clientY - r.top - this.view.y) / this.view.zoom };
    }
    toScreen(p) { return { x: p.x * this.view.zoom + this.view.x, y: p.y * this.view.zoom + this.view.y }; }
    size() { const r = this.svg.getBoundingClientRect(); return { w: r.width, h: r.height }; }

    /* ---------- תצוגה ---------- */
    applyView() {
      const v = this.view;
      const t = 'translate(' + v.x + ' ' + v.y + ') scale(' + v.zoom + ')';
      this.vp.setAttribute('transform', t);
      this.gridMinor.setAttribute('patternTransform', t);
      this.gridMajor.setAttribute('patternTransform', t);
      this.app.onViewChange && this.app.onViewChange();
      this.positionToolbar();
      this.app.popover && this.app.popover.reposition();
    }
    zoomAt(sx, sy, factor) {
      const v = this.view;
      const z = Math.min(3, Math.max(0.3, v.zoom * factor));
      const k = z / v.zoom;
      v.x = sx - (sx - v.x) * k;
      v.y = sy - (sy - v.y) * k;
      v.zoom = Math.round(z * 1000) / 1000;
      this.applyView();
      this.closeEditor(true);
    }
    zoomBy(f) { const s = this.size(); this.zoomAt(s.w / 2, s.h / 2, f); this.app.autosave(); }
    /** inset: פיקסלים בצד שמאל של הקנבס שמכוסים (למשל בכרטיס ההדרכה), כדי שהמכונה תיכנס לידם. */
    fit(inset) {
      const st = this.m.states;
      const s = this.size();
      inset = inset && s.w - inset > 420 ? inset : 0;
      s.w -= inset;
      if (!st.length) { this.view.x = s.w / 2; this.view.y = s.h / 2; this.view.zoom = 1; this.applyView(); return; }
      // התיבה שמכילה את כל השרטוט: המצבים, חץ הכניסה, הקשתות (גם לולאות) והתוויות שלהן
      const xs = [], ys = [];
      for (const q of st) { xs.push(q.x - R - 6, q.x + R + 6); ys.push(q.y - R - 6, q.y + R + 6); }
      const start = M.getState(this.m, this.m.start);
      if (start) xs.push(start.x - R - 40);
      for (const t of this.m.transitions) {
        const r = this.routes.get(t.id);
        if (!r || !r.label) continue;
        xs.push(r.label.x, r.label.x + r.label.w); ys.push(r.label.y, r.label.y + r.label.h);
        for (const p of RT.sample(r.pts, 8)) { xs.push(p.x); ys.push(p.y); }
      }
      const pad = 56, top = 84;       // למעלה יותר מקום: שם מופיעים הרמזים ותוצאת ההרצה
      const minX = Math.min(...xs) - pad, maxX = Math.max(...xs) + pad, minY = Math.min(...ys) - top, maxY = Math.max(...ys) + pad;
      const z = Math.min(1.4, Math.max(0.4, Math.min(s.w / (maxX - minX), s.h / (maxY - minY))));
      this.view.zoom = Math.round(z * 1000) / 1000;
      this.view.x = inset + s.w / 2 - ((minX + maxX) / 2) * z;
      this.view.y = s.h / 2 - ((minY + maxY) / 2) * z;
      this.applyView();
    }
    centerWorld() { const s = this.size(); return { x: (s.w / 2 - this.view.x) / this.view.zoom, y: (s.h / 2 - this.view.y) / this.view.zoom }; }
    snap(p) { return { x: Math.round(p.x / SNAP) * SNAP, y: Math.round(p.y / SNAP) * SNAP }; }

    /** מקום פנוי למצב חדש ליד p (לא על מצב קיים), מיושר לרשת. */
    freeSpot(p) {
      const st = this.m.states;
      const ok = (q) => st.every((s) => Math.hypot(s.x - q.x, s.y - q.y) > R * 3.6);
      p = this.snap(p);
      if (ok(p)) return p;
      for (let ring = 1; ring < 8; ring++) for (let k = 0; k < 8 * ring; k++) {
        const a = (k / (8 * ring)) * Math.PI * 2;
        const q = this.snap({ x: p.x + Math.cos(a) * ring * 100, y: p.y + Math.sin(a) * ring * 80 });
        if (ok(q)) return q;
      }
      return p;
    }

    /* ---------- ציור ---------- */
    labelLines(t) {
      const type = this.m.type;
      if (!t.labels.length) return ['?'];
      if (type === 'pda' || type === 'tm') return t.labels.map((l) => M.formatLabel(type, l));
      const parts = t.labels.map((l) => M.formatLabel(type, l));
      const lines = [];
      for (let i = 0; i < parts.length; i += 5) lines.push(parts.slice(i, i + 5).join(', '));
      return lines;
    }

    render() {
      const m = this.m, app = this.app, sel = app.sel, rv = app.runView || {};
      const states = new Map(m.states.map((s) => [s.id, s]));
      const issueStates = new Set(), issueEdges = new Set();
      for (const is of app.issues || []) if (is.level === 'error') { is.states.forEach((x) => issueStates.add(x)); is.transitions.forEach((x) => issueEdges.add(x)); }
      const taken = new Map();
      for (const e of rv.edges || []) { if (!taken.has(e.t)) taken.set(e.t, new Set()); taken.get(e.t).add(e.l); }

      const list = m.transitions.filter((t) => states.has(t.from) && states.has(t.to)).map((t) => ({ id: t.id, from: t.from, to: t.to, lines: this.labelLines(t) }));
      const pv = this.preview && !M.findTransition(m, this.preview.from, this.preview.to) ? this.preview : null;
      if (pv) list.push({ id: '__preview', from: pv.from, to: pv.to, lines: ['?'] });
      this.routes = RT.route(m.states, list, { startId: m.start, fast: !!(this.drag && this.drag.kind === 'move') });

      let edges = '', labels = '', travel = '';
      const start = states.get(m.start);
      if (start) {
        const sa = G.startArrow(start);
        edges += '<g class="ed start-arrow"><path class="ed-line" d="' + sa.d + '"/><polygon class="ed-arrow" points="' + sa.arrow + '"/></g>';
      }
      const perLine = m.type === 'pda' || m.type === 'tm';
      for (const e of list) {
        const r = this.routes.get(e.id);
        const t = M.getTransition(m, e.id);
        const cls = ['ed'];
        if (e.id === '__preview') cls.push('preview');
        if (sel.trans.has(e.id)) cls.push('selected');
        if (taken.has(e.id)) cls.push('taken');
        if (issueEdges.has(e.id)) cls.push('issue');
        if (this.hover && this.hover.kind === 'edge' && this.hover.id === e.id) cls.push('hover');
        if (app.popover && app.popover.tid === e.id) cls.push('editing');
        const c = cls.join(' ');
        edges += '<g class="' + c + '" data-edge="' + e.id + '"><path class="ed-hit" d="' + r.d + '"/><path class="ed-line" d="' + r.d + '"/><polygon class="ed-arrow" points="' + r.arrow + '"/></g>';
        const pl = r.label, used = taken.get(e.id);
        let lb = '<g class="' + c.replace('ed', 'lb') + '" data-edge="' + e.id + '" transform="translate(' + pl.x.toFixed(1) + ' ' + pl.y.toFixed(1) + ')">';
        lb += '<rect class="lb-bg" width="' + pl.w.toFixed(1) + '" height="' + pl.h + '" rx="5"/>';
        e.lines.forEach((ln, i) => {
          const hot = used && (perLine ? used.has(i) : true);
          if (hot && perLine && e.lines.length > 1) lb += '<rect class="lb-hot" x="2" y="' + (3 + i * 16) + '" width="' + (pl.w - 4).toFixed(1) + '" height="16" rx="3"/>';
          lb += '<text class="lb-text' + (hot ? ' hot' : '') + (!t || !t.labels.length ? ' placeholder' : '') + '" x="' + (pl.w / 2).toFixed(1) + '" y="' + (3 + i * 16 + 12.2) + '">' + esc(ln) + '</text>';
        });
        if (t) lb += '<g class="trash" data-trash-edge="' + e.id + '" transform="translate(' + (pl.w + 8).toFixed(1) + ' ' + (pl.h / 2) + ')"><title>מחיקת המעבר</title>' + TRASH + '</g>';
        labels += lb + '</g>';
        if (used && rv.animate) travel += '<circle class="travel" r="4.2"><animateMotion dur="' + (rv.travelMs || 420) + 'ms" fill="freeze" path="' + r.d + '"/><animate attributeName="opacity" values="1;1;0" keyTimes="0;0.8;1" dur="' + Math.round((rv.travelMs || 420) * 1.4) + 'ms" fill="freeze"/></circle>';
      }

      let sts = '';
      const active = new Set(rv.active || []), accepted = new Set(rv.accepted || []), rejected = new Set(rv.rejected || []);
      for (const s of m.states) {
        const cls = ['st'];
        if (sel.states.has(s.id)) cls.push('selected');
        if (s.accepting) cls.push('accepting');
        if (s.id === m.start) cls.push('start');
        if (active.has(s.id)) cls.push('active');
        if (accepted.has(s.id)) cls.push('accepted');
        if (rejected.has(s.id)) cls.push('rejected');
        if (issueStates.has(s.id)) cls.push('issue');
        if (this.source === s.id) cls.push('source');
        if (this.hover && this.hover.kind === 'state' && this.hover.id === s.id) cls.push('hover');
        const nm = s.name.length > 4 ? (s.name.length > 6 ? ' small' : ' mid') : '';
        sts += '<g class="' + cls.join(' ') + '" data-state="' + s.id + '" transform="translate(' + s.x + ' ' + s.y + ')">' +
          '<circle class="st-halo" r="' + (R + 7) + '"/><circle class="st-hit" r="' + (R + 8) + '"/><circle class="st-body" r="' + R + '"/>' +
          (s.accepting ? '<circle class="st-inner" r="' + (R - 4.5) + '"/>' : '') +
          '<text class="st-name' + nm + '" y="0.5">' + esc(s.name) + '</text>' +
          '<g class="trash" data-trash-state="' + s.id + '" transform="translate(' + (R * 0.78) + ' ' + (-R * 0.78) + ')"><title>מחיקת המצב</title>' + TRASH + '</g></g>';
      }
      this.gEdges.innerHTML = edges;
      this.gLabels.innerHTML = labels + travel;
      this.gStates.innerHTML = sts;
      if (rv.animate) rv.animate = false;
      this.renderOverlay();
      this.applyView();
      this.vp.classList.toggle('hover-edge', !!this.hover && this.hover.kind === 'edge' && m.transitions.some((t) => t.id === this.hover.id));
      this.host.classList.toggle('is-empty', !m.states.length);
      this.host.classList.toggle('arming', !!this.source);
      this.host.classList.toggle('running', app.isRunning());
      this.host.dataset.activeTool = app.tool;
    }

    /** חץ "גומי" מהמצב שנבחר כמקור אל הסמן (או אל המצב שמתחת לסמן). */
    renderOverlay() {
      let html = '';
      const d = this.drag;
      if (d && d.kind === 'marquee') {
        const x = Math.min(d.a.x, d.b.x), y = Math.min(d.a.y, d.b.y);
        html = '<rect class="marquee" x="' + x + '" y="' + y + '" width="' + Math.abs(d.a.x - d.b.x) + '" height="' + Math.abs(d.a.y - d.b.y) + '"/>';
      } else if (this.source && this.pointer && !(d && d.kind === 'move')) {
        const a = M.getState(this.m, this.source);
        const over = this.hover && this.hover.kind === 'state' ? M.getState(this.m, this.hover.id) : null;
        if (a && over && over.id === a.id) {
          const lp = RT.shapeLoop(a, -Math.PI / 2);
          html = '<g class="rubber"><path d="' + lp.d + '"/><polygon points="' + lp.arrow + '"/></g>';
        } else if (a && Math.hypot(this.pointer.x - a.x, this.pointer.y - a.y) > R + 4) {
          const rb = G.rubberBand(a, over ? G.rimPoint(over, a) : this.pointer);
          html = '<g class="rubber"><path d="' + rb.d + '"/><polygon points="' + rb.arrow + '"/></g>';
        }
      }
      this.gOverlay.innerHTML = html;
    }

    /** מסמן ריחוף (פח, יעד) בלי לצייר הכול מחדש. */
    setHover(h) {
      const same = (a, b) => (a && b ? a.kind === b.kind && a.id === b.id : a === b);
      if (same(h, this.hover)) return;
      this.vp.querySelectorAll('.hover').forEach((el) => el.classList.remove('hover'));
      this.hover = h;
      this.vp.classList.toggle('hover-edge', !!h && h.kind === 'edge');
      if (h) this.vp.querySelectorAll(h.kind === 'state' ? '[data-state="' + h.id + '"]' : '[data-edge="' + h.id + '"]').forEach((el) => el.classList.add('hover'));
    }

    /** מלבנים (בפיקסלים של המסך) של המצבים והתוויות, כדי שדברים צפים לא יסתירו אותם. */
    screenObstacles() {
      const z = this.view.zoom, out = [];
      for (const s of this.m.states) { const p = this.toScreen(s); const r = (G.R + 6) * z; out.push({ x: p.x - r, y: p.y - r, w: 2 * r, h: 2 * r, weight: 3 }); }
      for (const r of this.routes.values()) { const p = this.toScreen({ x: r.label.x, y: r.label.y }); out.push({ x: p.x, y: p.y, w: r.label.w * z, h: r.label.h * z, weight: 1 }); }
      return out;
    }

    /** בוחר מבין מיקומים מועמדים ({x,y} פינה שמאלית עליונה) את זה שמכסה הכי מעט, בתוך הקנבס. */
    placeBox(cands, w, h, skip) {
      const s = this.size(), obs = this.screenObstacles().filter((o) => !skip || !skip(o));
      let best = null;
      for (const c of cands) {
        const x = Math.max(8, Math.min(s.w - w - 8, c.x)), y = Math.max(8, Math.min(s.h - h - 8, c.y));
        let cost = (x !== c.x || y !== c.y) ? 2 : 0;
        for (const o of obs) {
          const ix = Math.max(0, Math.min(x + w, o.x + o.w) - Math.max(x, o.x)), iy = Math.max(0, Math.min(y + h, o.y + o.h) - Math.max(y, o.y));
          cost += (ix * iy) / 400 * o.weight;
        }
        if (!best || cost < best.cost - 0.01) best = { x, y, cost };
      }
      return best;
    }

    /* ---------- סרגל צף ליד מצב נבחר ---------- */
    positionToolbar() {
      const tb = this.toolbar, app = this.app;
      const one = app.sel.states.size === 1 && !app.sel.trans.size ? M.getState(this.m, Array.from(app.sel.states)[0]) : null;
      if (!one || (this.drag && this.drag.kind === 'move') || this.editor.dataset.open === '1' || app.isRunning() || (app.popover && app.popover.isOpen())) { tb.hidden = true; return; }
      const p = this.toScreen(one);
      tb.hidden = false;
      if (tb.dataset.for !== one.id || tb.dataset.sig !== this.toolbarSig(one)) this.fillToolbar(one);
      const w = tb.offsetWidth, h = tb.offsetHeight, gap = (R + 14) * this.view.zoom + 8;
      const best = this.placeBox([{ x: p.x - w / 2, y: p.y - gap - h }, { x: p.x - w / 2, y: p.y + gap }], w, h,
        (o) => Math.abs(o.x + o.w / 2 - p.x) < 1 && Math.abs(o.y + o.h / 2 - p.y) < 1);
      tb.style.transform = 'translate(' + Math.round(best.x) + 'px,' + Math.round(best.y) + 'px)';
    }
    toolbarSig(s) { return [s.name, s.accepting, this.m.start === s.id].join('|'); }
    fillToolbar(s) {
      const tb = this.toolbar, isStart = this.m.start === s.id;
      tb.dataset.for = s.id;
      tb.dataset.sig = this.toolbarSig(s);
      const btn = (act, icon, text, title, on) => '<button type="button" class="tb-btn' + (on ? ' on' : '') + '" data-act="' + act + '"' + (on != null ? ' aria-pressed="' + !!on + '"' : '') + ' title="' + title + '">' + I.get(icon, 16) + '<span>' + text + '</span></button>';
      tb.innerHTML = '<span class="tb-name" dir="ltr">' + esc(s.name) + '</span>' +
        btn('accept', 'accept', 'מקבל', 'מצב מקבל (A)', s.accepting) +
        btn('start', 'start', 'התחלתי', 'מצב התחלתי (I)', isStart) +
        btn('loop', 'loop', 'לולאה', 'מעבר מהמצב לעצמו') +
        '<span class="tb-sep"></span>' +
        '<button type="button" class="tb-btn icon" data-act="rename" title="שינוי שם (F2)" aria-label="שינוי שם">' + I.get('edit', 16) + '</button>' +
        '<button type="button" class="tb-btn icon danger" data-act="delete" title="מחיקת המצב (Delete)" aria-label="מחיקת המצב">' + I.get('trash', 16) + '</button>';
    }

    /* ---------- שינוי שם בתוך הקנבס ---------- */
    openRenameEditor(sid) {
      const s = M.getState(this.m, sid);
      if (!s) return;
      const p = this.toScreen(s);
      const ed = this.editor;
      ed.innerHTML = '<div class="ie-row"><input class="ie-input center" dir="ltr" spellcheck="false" autocomplete="off" maxlength="12" aria-label="שם המצב"></div><div class="ie-err" role="alert"></div>';
      const input = ed.querySelector('input');
      input.value = s.name;
      ed.dataset.open = '1'; ed.hidden = false;
      ed.style.width = '128px';
      ed.style.transform = 'translate(' + Math.round(p.x - 64) + 'px,' + Math.round(p.y - 18) + 'px)';
      this.toolbar.hidden = true;
      const commit = () => {
        const err = M.renameState(M.clone(this.m), sid, input.value);
        if (err) { ed.querySelector('.ie-err').textContent = err; input.classList.add('bad'); return; }
        this.closeEditor();
        this.app.change('שינוי שם', () => M.renameState(this.m, sid, input.value));
      };
      this.editorHandlers = { commit, cancel: () => this.closeEditor() };
      input.addEventListener('keydown', (e) => {
        e.stopPropagation();
        if (e.key === 'Enter') { e.preventDefault(); commit(); } else if (e.key === 'Escape') { e.preventDefault(); this.closeEditor(); }
      });
      input.focus(); input.select();
    }
    closeEditor(commitIfOpen) {
      const ed = this.editor;
      if (ed.dataset.open !== '1') return;
      const h = this.editorHandlers;
      this.editorHandlers = null;
      if (commitIfOpen && h) { h.commit(); if (ed.dataset.open !== '1') return; }
      ed.dataset.open = ''; ed.hidden = true; ed.innerHTML = '';
      this.positionToolbar();
    }

    /** תמונת SVG עצמאית של הדיאגרמה (לייצוא). */
    exportSVG() {
      const css = getComputedStyle(document.documentElement);
      const v = (n) => css.getPropertyValue(n).trim();
      const st = this.m.states;
      if (!st.length) return null;
      const xs = st.map((s) => s.x), ys = st.map((s) => s.y);
      const pad = 100;
      const x0 = Math.min(...xs) - pad, y0 = Math.min(...ys) - pad - 30, w = Math.max(...xs) - x0 + pad, h = Math.max(...ys) - y0 + pad;
      const clone = this.vp.cloneNode(true);
      clone.removeAttribute('transform');
      clone.querySelector('.overlay').remove();
      clone.querySelectorAll('.trash, .preview').forEach((x) => x.remove());
      const style = '<style>.ed-line{fill:none;stroke:' + v('--edge') + ';stroke-width:1.6}.ed-arrow{fill:' + v('--edge') + '}.ed-hit,.st-hit,.st-halo,.travel,.lb-hot{display:none}' +
        '.lb-bg{fill:' + v('--label-bg') + ';stroke:' + v('--label-line') + '}.lb-text{font:500 12.5px ' + v('--mono') + ';fill:' + v('--label-text') + ';text-anchor:middle}' +
        '.st-body{fill:' + v('--state-fill') + ';stroke:' + v('--state-stroke') + ';stroke-width:1.6}.st-inner{fill:none;stroke:' + v('--state-stroke') + ';stroke-width:1.4}' +
        '.st-name{font:500 14px ' + v('--mono') + ';fill:' + v('--state-text') + ';text-anchor:middle;dominant-baseline:central}</style>';
      return '<svg xmlns="http://www.w3.org/2000/svg" viewBox="' + [x0, y0, w, h].join(' ') + '" width="' + w + '" height="' + h + '"><rect x="' + x0 + '" y="' + y0 + '" width="' + w + '" height="' + h + '" fill="' + v('--canvas') + '"/>' + style + clone.innerHTML + '</svg>';
    }
  }

  root.CanvasView = CanvasView;
})(typeof globalThis !== 'undefined' ? globalThis : this);
