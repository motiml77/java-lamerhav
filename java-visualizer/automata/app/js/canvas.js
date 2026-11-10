/*
 * canvas.js - הקנבס: ציור המכונה ב-SVG והאינטראקציה עם העכבר.
 * הדיאגרמה עצמה LTR (שרטוט מתמטי), גם כשכל הממשק מסביב RTL.
 *
 * אינטראקציה (כלי "בחירה"):
 *   לחיצה על מצב: בחירה. Shift: הוספה/הסרה מהבחירה. גרירה: הזזה של כל הנבחרים.
 *   לחיצה על השפה של מצב (הטבעת החיצונית) וגרירה אל מצב: מעבר חדש. שחרור על אותו מצב: לולאה.
 *   לחיצה כפולה על הקנבס: מצב חדש. על מצב: שינוי שם. על קשת: עריכת הסמלים.
 *   גרירה של רקע: הזזת התצוגה. Shift + גרירה של רקע: בחירה במלבן. גלגלת: זום סביב הסמן.
 */
(function (root) {
  'use strict';
  const M = root.AutomataModel, G = root.AutomataGeometry, I = root.Icons;
  const R = G.R;
  const SVGNS = 'http://www.w3.org/2000/svg';
  const esc = (s) => String(s).replace(/[&<>"]/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' })[c]);

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
      this.drag = null;
      this.hover = null;
      this.spaceDown = false;
      this.pendingFrom = null;     // כלי "מעבר": המצב שנבחר כמקור
      this.bind();
    }

    get m() { return this.app.machine; }
    get view() { return this.m.view; }

    /* ---------- המרות קואורדינטות ---------- */
    toWorld(e) {
      const r = this.svg.getBoundingClientRect();
      return { x: (e.clientX - r.left - this.view.x) / this.view.zoom, y: (e.clientY - r.top - this.view.y) / this.view.zoom };
    }
    toScreen(p) { return { x: p.x * this.view.zoom + this.view.x, y: p.y * this.view.zoom + this.view.y }; }
    size() { const r = this.svg.getBoundingClientRect(); return { w: r.width, h: r.height }; }

    /* ---------- תצוגה: זום, הזזה, התאמה ---------- */
    applyView() {
      const v = this.view;
      this.vp.setAttribute('transform', 'translate(' + v.x + ' ' + v.y + ') scale(' + v.zoom + ')');
      const t = 'translate(' + v.x + ' ' + v.y + ') scale(' + v.zoom + ')';
      this.gridMinor.setAttribute('patternTransform', t);
      this.gridMajor.setAttribute('patternTransform', t);
      this.app.onViewChange && this.app.onViewChange();
      this.positionToolbar();
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
    fit() {
      const st = this.m.states;
      const s = this.size();
      if (!st.length) { this.view.x = s.w / 2; this.view.y = s.h / 2; this.view.zoom = 1; this.applyView(); return; }
      const xs = st.map((q) => q.x), ys = st.map((q) => q.y);
      const pad = 90;
      const minX = Math.min(...xs) - pad, maxX = Math.max(...xs) + pad, minY = Math.min(...ys) - pad - 30, maxY = Math.max(...ys) + pad;
      const z = Math.min(1.4, Math.max(0.4, Math.min(s.w / (maxX - minX), s.h / (maxY - minY))));
      this.view.zoom = Math.round(z * 1000) / 1000;
      this.view.x = s.w / 2 - ((minX + maxX) / 2) * z;
      this.view.y = s.h / 2 - ((minY + maxY) / 2) * z;
      this.applyView();
    }
    centerWorld() { const s = this.size(); return { x: (s.w / 2 - this.view.x) / this.view.zoom, y: (s.h / 2 - this.view.y) / this.view.zoom }; }

    /** מקום פנוי למצב חדש ליד point (לא על מצב קיים). */
    freeSpot(p) {
      const st = this.m.states;
      const ok = (q) => st.every((s) => Math.hypot(s.x - q.x, s.y - q.y) > R * 3.2);
      if (ok(p)) return p;
      for (let ring = 1; ring < 8; ring++) for (let k = 0; k < 8 * ring; k++) {
        const a = (k / (8 * ring)) * Math.PI * 2;
        const q = { x: p.x + Math.cos(a) * ring * 90, y: p.y + Math.sin(a) * ring * 70 };
        if (ok(q)) return q;
      }
      return p;
    }

    /* ---------- ציור ---------- */
    render() {
      const m = this.m, app = this.app, sel = app.sel, rv = app.runView || {};
      const states = new Map(m.states.map((s) => [s.id, s]));
      const issueStates = new Set(), issueEdges = new Set();
      for (const is of app.issues || []) if (is.level === 'error') { is.states.forEach((x) => issueStates.add(x)); is.transitions.forEach((x) => issueEdges.add(x)); }
      const pairs = new Set(m.transitions.map((t) => t.from + '>' + t.to));
      const neighbors = new Map(m.states.map((s) => [s.id, []]));
      for (const t of m.transitions) if (t.from !== t.to) {
        neighbors.get(t.from) && neighbors.get(t.from).push(states.get(t.to));
        neighbors.get(t.to) && neighbors.get(t.to).push(states.get(t.from));
      }
      // חץ הכניסה של המצב ההתחלתי בא משמאל, אז הלולאה לא תצויר שם
      if (states.get(m.start) && neighbors.get(m.start)) { const s0 = states.get(m.start); neighbors.get(m.start).push({ x: s0.x - 100, y: s0.y }); }
      const takenEdges = new Map();  // id -> Set(label index)
      for (const e of rv.edges || []) { if (!takenEdges.has(e.t)) takenEdges.set(e.t, new Set()); takenEdges.get(e.t).add(e.l); }

      let edges = '', labels = '', travel = '';
      const start = states.get(m.start);
      if (start) {
        const sa = G.startArrow(start);
        edges += '<g class="ed start-arrow"><path class="ed-line" d="' + sa.d + '"/><polygon class="ed-arrow" points="' + sa.arrow + '"/></g>';
      }
      this.shapes = new Map();
      for (const t of m.transitions) {
        const a = states.get(t.from), b = states.get(t.to);
        if (!a || !b) continue;
        const loop = t.from === t.to;
        const ang = loop ? (a.loopAngle != null ? a.loopAngle * Math.PI / 180 : G.bestLoopAngle(a, neighbors.get(a.id) || [])) : 0;
        // קשת ישרה שעוברת דרך מצב אחר מתעקלת, כדי שהתווית והקו לא יסתירו אותו
        const blocked = !loop && m.states.some((q) => q !== a && q !== b && G.segmentDistance(q, a, b) < R + 8);
        const shape = loop ? G.loopPath(a, ang) : G.edgePath(a, b, blocked || pairs.has(t.to + '>' + t.from));
        const lines = this.labelLines(t);
        const box = G.labelBox(lines);
        const pl = G.placeLabel(shape, box, loop);
        this.shapes.set(t.id, { shape, pl, loop });
        const cls = ['ed'];
        if (sel.trans.has(t.id)) cls.push('selected');
        if (takenEdges.has(t.id)) cls.push('taken');
        if (issueEdges.has(t.id)) cls.push('issue');
        if (!t.labels.length) cls.push('empty');
        edges += '<g class="' + cls.join(' ') + '" data-edge="' + t.id + '"><path class="ed-hit" d="' + shape.d + '"/><path class="ed-line" d="' + shape.d + '"/><polygon class="ed-arrow" points="' + shape.arrow + '"/></g>';
        const used = takenEdges.get(t.id);
        let lb = '<g class="' + cls.join(' ').replace('ed', 'lb') + '" data-edge="' + t.id + '" transform="translate(' + pl.x.toFixed(1) + ' ' + pl.y.toFixed(1) + ')">';
        lb += '<rect class="lb-bg" width="' + pl.w.toFixed(1) + '" height="' + pl.h + '" rx="5"/>';
        const perLine = (m.type === 'pda' || m.type === 'tm');
        lines.forEach((ln, i) => {
          const hot = used && (perLine ? used.has(i) : true);
          if (hot && perLine && lines.length > 1) lb += '<rect class="lb-hot" x="2" y="' + (3 + i * 16) + '" width="' + (pl.w - 4).toFixed(1) + '" height="16" rx="3"/>';
          lb += '<text class="lb-text' + (hot ? ' hot' : '') + '" x="' + (pl.w / 2).toFixed(1) + '" y="' + (3 + i * 16 + 12.2) + '">' + esc(ln) + '</text>';
        });
        if (!lines.length || (lines.length === 1 && lines[0] === '')) lb += '<text class="lb-text placeholder" x="' + (pl.w / 2) + '" y="15.2">?</text>';
        lb += '</g>';
        labels += lb;
        if (used && rv.animate) travel += '<circle class="travel" r="4.2"><animateMotion dur="' + (rv.travelMs || 420) + 'ms" fill="freeze" path="' + shape.d + '"/><animate attributeName="opacity" values="1;1;0" keyTimes="0;0.8;1" dur="' + Math.round((rv.travelMs || 420) * 1.4) + 'ms" fill="freeze"/></circle>';
      }

      let sts = '';
      const active = new Set(rv.active || []);
      const accepted = new Set(rv.accepted || []);
      const rejected = new Set(rv.rejected || []);
      for (const s of m.states) {
        const cls = ['st'];
        if (sel.states.has(s.id)) cls.push('selected');
        if (s.accepting) cls.push('accepting');
        if (s.id === m.start) cls.push('start');
        if (active.has(s.id)) cls.push('active');
        if (accepted.has(s.id)) cls.push('accepted');
        if (rejected.has(s.id)) cls.push('rejected');
        if (issueStates.has(s.id)) cls.push('issue');
        if (this.pendingFrom === s.id) cls.push('pending');
        if (this.drag && this.drag.kind === 'connect' && this.drag.target === s.id) cls.push('drop-target');
        const nm = s.name.length > 4 ? (s.name.length > 6 ? ' small' : ' mid') : '';
        sts += '<g class="' + cls.join(' ') + '" data-state="' + s.id + '" transform="translate(' + s.x + ' ' + s.y + ')">' +
          '<circle class="st-halo" r="' + (R + 7) + '"/>' +
          '<circle class="st-hit" r="' + (R + 7) + '"/>' +
          '<circle class="st-body" r="' + R + '"/>' +
          (s.accepting ? '<circle class="st-inner" r="' + (R - 4.5) + '"/>' : '') +
          '<text class="st-name' + nm + '" y="0.5">' + esc(s.name) + '</text></g>';
      }
      this.gEdges.innerHTML = edges;
      this.gLabels.innerHTML = labels + travel;
      this.gStates.innerHTML = sts;
      if (rv.animate) rv.animate = false;
      this.renderOverlay();
      this.applyView();
      this.host.classList.toggle('is-empty', !m.states.length);
      this.host.dataset.tool = this.app.tool;
    }

    labelLines(t) {
      const type = this.m.type;
      if (!t.labels.length) return [''];
      if (type === 'pda' || type === 'tm') return t.labels.map((l) => M.formatLabel(type, l));
      // FA: מפצלים לשורות אם התווית ארוכה (יותר מ-5 סמלים בשורה)
      const parts = t.labels.map((l) => M.formatLabel(type, l));
      const lines = [];
      for (let i = 0; i < parts.length; i += 5) lines.push(parts.slice(i, i + 5).join(', '));
      return lines;
    }

    renderOverlay() {
      let html = '';
      const d = this.drag;
      if (d && d.kind === 'connect') {
        const a = M.getState(this.m, d.from);
        const tgt = d.target ? M.getState(this.m, d.target) : null;
        if (tgt && tgt.id === a.id) {
          const lp = G.loopPath(a, -Math.PI / 2);
          html += '<g class="rubber"><path d="' + lp.d + '"/><polygon points="' + lp.arrow + '"/></g>';
        } else {
          const end = tgt ? G.rimPoint(tgt, a) : d.pointer;
          const rb = G.rubberBand(a, end);
          html += '<g class="rubber"><path d="' + rb.d + '"/><polygon points="' + rb.arrow + '"/></g>';
        }
      } else if (d && d.kind === 'marquee') {
        const x = Math.min(d.a.x, d.b.x), y = Math.min(d.a.y, d.b.y);
        html += '<rect class="marquee" x="' + x + '" y="' + y + '" width="' + Math.abs(d.a.x - d.b.x) + '" height="' + Math.abs(d.a.y - d.b.y) + '"/>';
      } else if (this.hover && this.hover.band && this.app.tool === 'select' && !this.app.isRunning()) {
        const s = M.getState(this.m, this.hover.id);
        if (s) {
          const p = G.rimPoint(s, this.hover.p);
          html += '<circle class="handle-ring" cx="' + s.x + '" cy="' + s.y + '" r="' + R + '"/><circle class="handle" cx="' + p.x.toFixed(1) + '" cy="' + p.y.toFixed(1) + '" r="5"/>';
        }
      }
      this.gOverlay.innerHTML = html;
    }

    /* ---------- סרגל צף ליד מצב נבחר ---------- */
    positionToolbar() {
      const tb = this.toolbar;
      const app = this.app;
      const one = app.sel.states.size === 1 && !app.sel.trans.size ? M.getState(this.m, Array.from(app.sel.states)[0]) : null;
      if (!one || (this.drag && this.drag.kind !== 'none') || this.editor.dataset.open === '1' || app.isRunning()) { tb.hidden = true; return; }
      const p = this.toScreen(one);
      tb.hidden = false;
      if (tb.dataset.for !== one.id || tb.dataset.sig !== this.toolbarSig(one)) this.fillToolbar(one);
      const w = tb.offsetWidth, h = tb.offsetHeight, s = this.size();
      let x = p.x - w / 2, y = p.y - (R + 12) * this.view.zoom - h - 6;
      if (y < 8) y = p.y + (R + 12) * this.view.zoom + 6;
      x = Math.max(8, Math.min(s.w - w - 8, x));
      tb.style.transform = 'translate(' + Math.round(x) + 'px,' + Math.round(y) + 'px)';
    }
    toolbarSig(s) { return [s.name, s.accepting, this.m.start === s.id].join('|'); }
    fillToolbar(s) {
      const tb = this.toolbar;
      const isStart = this.m.start === s.id;
      tb.dataset.for = s.id;
      tb.dataset.sig = this.toolbarSig(s);
      tb.innerHTML =
        '<span class="tb-name" dir="ltr">' + esc(s.name) + '</span>' +
        '<button type="button" class="tb-btn' + (isStart ? ' on' : '') + '" data-act="start" aria-pressed="' + isStart + '" title="מצב התחלתי (I)">' + I.get('start', 16) + '<span>התחלתי</span></button>' +
        '<button type="button" class="tb-btn' + (s.accepting ? ' on' : '') + '" data-act="accept" aria-pressed="' + s.accepting + '" title="מצב מקבל (A)">' + I.get('accept', 16) + '<span>מקבל</span></button>' +
        '<button type="button" class="tb-btn" data-act="loop" title="לולאה עצמית">' + I.get('loop', 16) + '<span>לולאה</span></button>' +
        '<button type="button" class="tb-btn" data-act="connect" title="מעבר מהמצב הזה: לחצו אחר כך על מצב היעד (T)">' + I.get('transition', 16) + '<span>מעבר</span></button>' +
        '<span class="tb-sep"></span>' +
        '<button type="button" class="tb-btn icon" data-act="rename" title="שינוי שם (F2)" aria-label="שינוי שם">' + I.get('edit', 16) + '</button>' +
        '<button type="button" class="tb-btn icon danger" data-act="delete" title="מחיקת המצב (Delete)" aria-label="מחיקת המצב">' + I.get('trash', 16) + '</button>';
    }

    /* ---------- עורך בתוך הקנבס (סמלים של קשת / שם של מצב) ---------- */
    openLabelEditor(tid, opts) {
      opts = opts || {};
      const t = M.getTransition(this.m, tid);
      if (!t) return;
      this.render();
      const info = this.shapes.get(tid);
      const p = this.toScreen({ x: info.pl.cx, y: info.pl.cy });
      const type = this.m.type;
      const multi = type === 'pda' || type === 'tm';
      const hint = { dfa: 'סמלים מופרדים בפסיק: a, b', nfa: 'סמלים מופרדים בפסיק. ε = מעבר ריק', pda: 'כלל בכל שורה: קלט,שליפה/דחיפה  (a,Z/AZ)', tm: 'כלל בכל שורה: קורא/כותב,כיוון  (1/0,R)' }[type];
      const value = multi ? t.labels.map((l) => M.formatLabel(type, l)).join('\n') : t.labels.map((l) => M.formatLabel(type, l)).join(', ');
      const ed = this.editor;
      ed.innerHTML =
        '<div class="ie-row">' + (multi ? '<textarea class="ie-input" dir="ltr" rows="' + Math.max(2, t.labels.length + 1) + '" spellcheck="false"></textarea>' : '<input class="ie-input" dir="ltr" spellcheck="false" autocomplete="off">') +
        (type !== 'dfa' ? '<button type="button" class="ie-eps" title="הוספת ε" tabindex="-1">ε</button>' : '') + '</div>' +
        '<div class="ie-err" role="alert"></div><div class="ie-hint">' + esc(hint) + '</div><div class="ie-keys"><kbd>Enter</kbd> אישור · <kbd>Esc</kbd> ביטול' + (multi ? ' · <kbd>Shift+Enter</kbd> שורה חדשה' : '') + '</div>';
      const input = ed.querySelector('.ie-input');
      input.value = value;
      ed.dataset.open = '1';
      ed.dataset.kind = 'edge';
      ed.hidden = false;
      const s = this.size();
      const w = multi ? 260 : 230;
      ed.style.width = w + 'px';
      const x = Math.max(8, Math.min(s.w - w - 8, p.x - w / 2));
      const y = Math.max(8, Math.min(s.h - 150, p.y + 16));
      ed.style.transform = 'translate(' + Math.round(x) + 'px,' + Math.round(y) + 'px)';
      this.toolbar.hidden = true;
      const commit = () => {
        const r = M.parseLabels(type, input.value);
        if (r.errors.length) { ed.querySelector('.ie-err').textContent = r.errors[0]; input.classList.add('bad'); return; }
        this.closeEditor();
        this.app.change('עריכת מעבר', () => M.setTransitionLabels(this.m, tid, r.labels), opts.snapshot);
      };
      const cancel = () => {
        this.closeEditor();
        if (opts.isNew) this.app.restore(opts.snapshot);
      };
      this.editorHandlers = { commit, cancel };
      input.addEventListener('keydown', (e) => {
        e.stopPropagation();
        if (e.key === 'Enter' && !(multi && e.shiftKey)) { e.preventDefault(); commit(); }
        else if (e.key === 'Escape') { e.preventDefault(); cancel(); }
      });
      input.addEventListener('input', () => { input.classList.remove('bad'); ed.querySelector('.ie-err').textContent = ''; });
      const eps = ed.querySelector('.ie-eps');
      if (eps) eps.addEventListener('mousedown', (e) => {
        e.preventDefault();
        const a = input.selectionStart, b = input.selectionEnd;
        input.value = input.value.slice(0, a) + 'ε' + input.value.slice(b);
        input.selectionStart = input.selectionEnd = a + 1;
        input.dispatchEvent(new Event('input'));
      });
      input.focus(); input.select();
    }

    openRenameEditor(sid) {
      const s = M.getState(this.m, sid);
      if (!s) return;
      const p = this.toScreen(s);
      const ed = this.editor;
      ed.innerHTML = '<div class="ie-row"><input class="ie-input center" dir="ltr" spellcheck="false" autocomplete="off" maxlength="12"></div><div class="ie-err" role="alert"></div>';
      const input = ed.querySelector('input');
      input.value = s.name;
      ed.dataset.open = '1'; ed.dataset.kind = 'state'; ed.hidden = false;
      ed.style.width = '120px';
      ed.style.transform = 'translate(' + Math.round(p.x - 60) + 'px,' + Math.round(p.y - 18) + 'px)';
      this.toolbar.hidden = true;
      const commit = () => {
        const snap = this.app.snapshot();
        const err = M.renameState(M.clone(this.m), sid, input.value);
        if (err) { ed.querySelector('.ie-err').textContent = err; input.classList.add('bad'); return; }
        this.closeEditor();
        this.app.change('שינוי שם', () => M.renameState(this.m, sid, input.value), snap);
      };
      const cancel = () => this.closeEditor();
      this.editorHandlers = { commit, cancel };
      input.addEventListener('keydown', (e) => {
        e.stopPropagation();
        if (e.key === 'Enter') { e.preventDefault(); commit(); }
        else if (e.key === 'Escape') { e.preventDefault(); cancel(); }
      });
      input.focus(); input.select();
    }

    /** סוגר את העורך. commitIfOpen: לשמור (כשלוחצים מחוץ לעורך). */
    closeEditor(commitIfOpen) {
      const ed = this.editor;
      if (ed.dataset.open !== '1') return;
      if (commitIfOpen && this.editorHandlers) { const h = this.editorHandlers; this.editorHandlers = null; h.commit(); if (ed.dataset.open === '1') { ed.dataset.open = ''; ed.hidden = true; } return; }
      ed.dataset.open = '';
      ed.hidden = true;
      ed.innerHTML = '';
      this.editorHandlers = null;
      this.positionToolbar();
    }

    /* ---------- אירועים ---------- */
    bind() {
      const svg = this.svg;
      svg.addEventListener('pointerdown', (e) => this.onDown(e));
      svg.addEventListener('pointermove', (e) => this.onMove(e));
      svg.addEventListener('pointerup', (e) => this.onUp(e));
      svg.addEventListener('pointercancel', () => { this.drag = null; this.render(); });
      svg.addEventListener('pointerleave', () => { if (!this.drag && this.hover) { this.hover = null; this.renderOverlay(); } });
      // לחיצה כפולה מזוהה ידנית ב-onDown: הקנבס מצויר מחדש אחרי כל לחיצה, ולכן הדפדפן לא תמיד שולח dblclick
      svg.addEventListener('wheel', (e) => {
        e.preventDefault();
        const r = svg.getBoundingClientRect();
        const f = Math.exp(-e.deltaY * (e.ctrlKey ? 0.01 : 0.0015));
        this.zoomAt(e.clientX - r.left, e.clientY - r.top, f);
        clearTimeout(this.wheelT);
        this.wheelT = setTimeout(() => this.app.autosave(), 300);
      }, { passive: false });
      svg.addEventListener('contextmenu', (e) => e.preventDefault());
      this.toolbar.addEventListener('pointerdown', (e) => e.stopPropagation());
      this.toolbar.addEventListener('click', (e) => {
        const b = e.target.closest('[data-act]');
        if (!b) return;
        const id = this.toolbar.dataset.for;
        this.app.stateAction(b.dataset.act, id);
      });
      this.editor.addEventListener('pointerdown', (e) => e.stopPropagation());
      window.addEventListener('resize', () => this.positionToolbar());
    }

    hit(e) {
      // בזמן גרירה ה-SVG "תופס" את המצביע, ולכן e.target הוא תמיד ה-SVG: בודקים מה באמת מתחת לסמן
      const el = (e.clientX != null && document.elementFromPoint(e.clientX, e.clientY)) || e.target;
      const st = el.closest && el.closest('[data-state]');
      if (st) {
        const id = st.dataset.state;
        const s = M.getState(this.m, id);
        const p = this.toWorld(e);
        const d = Math.hypot(p.x - s.x, p.y - s.y);
        return { kind: 'state', id, band: d > R - 6, p };
      }
      const ed = el.closest && el.closest('[data-edge]');
      if (ed) return { kind: 'edge', id: ed.dataset.edge };
      return { kind: 'empty' };
    }

    onDown(e) {
      if (this.editor.dataset.open === '1') { this.closeEditor(true); }
      const app = this.app;
      const h = this.hit(e);
      const p = this.toWorld(e);
      const now = performance.now();
      const last = this.lastDown;
      this.lastDown = { t: now, x: e.clientX, y: e.clientY, key: h.kind + (h.id || '') };
      if (e.button === 0 && last && now - last.t < 400 && Math.hypot(e.clientX - last.x, e.clientY - last.y) < 6 && last.key === this.lastDown.key) {
        this.lastDown = null;
        this.drag = null;
        e.preventDefault();
        this.onDbl(e, h);
        return;
      }
      this.svg.setPointerCapture(e.pointerId);
      this.downAt = { x: e.clientX, y: e.clientY };
      const panGesture = e.button === 1 || this.spaceDown || app.tool === 'hand' || (e.button === 0 && h.kind === 'empty' && !e.shiftKey && app.tool === 'select');
      if (e.button === 2) { this.drag = null; return; }

      if (app.tool === 'state' && h.kind === 'empty' && e.button === 0) {
        this.drag = null;
        app.addStateAt(p);
        return;
      }
      if ((app.tool === 'transition' || this.pendingFrom) && h.kind === 'state' && e.button === 0) {
        this.drag = { kind: 'connect', from: h.id, pointer: p, target: h.id, viaTool: true };
        this.render();
        return;
      }
      if (h.kind === 'state' && e.button === 0 && app.tool !== 'hand' && !this.spaceDown) {
        if (h.band && !e.shiftKey && !app.isRunning()) {
          this.drag = { kind: 'connect', from: h.id, pointer: p, target: null };
          this.render();
          return;
        }
        if (e.shiftKey) app.toggleSelect('state', h.id);
        else if (!app.sel.states.has(h.id)) app.select([h.id], []);
        const ids = Array.from(app.sel.states);
        this.drag = { kind: 'move', ids, start: p, orig: ids.map((id) => { const s = M.getState(this.m, id); return { id, x: s.x, y: s.y }; }), moved: false, snap: app.snapshot() };
        return;
      }
      if (h.kind === 'edge' && e.button === 0 && !panGesture) {
        if (e.shiftKey) app.toggleSelect('edge', h.id); else app.select([], [h.id]);
        this.drag = null;
        return;
      }
      if (h.kind === 'edge' && e.button === 0) {
        if (e.shiftKey) app.toggleSelect('edge', h.id); else app.select([], [h.id]);
        this.drag = null;
        return;
      }
      if (panGesture) {
        this.drag = { kind: 'pan', sx: e.clientX, sy: e.clientY, vx: this.view.x, vy: this.view.y, moved: false };
        this.host.classList.add('panning');
        return;
      }
      if (h.kind === 'empty' && e.button === 0 && e.shiftKey) {
        this.drag = { kind: 'marquee', a: p, b: p, base: new Set(app.sel.states) };
      }
    }

    onMove(e) {
      const d = this.drag;
      const p = this.toWorld(e);
      if (!d) {
        const h = this.hit(e);
        const hv = h.kind === 'state' ? { id: h.id, band: h.band, p } : null;
        const changed = JSON.stringify(hv && [hv.id, hv.band]) !== JSON.stringify(this.hover && [this.hover.id, this.hover.band]);
        this.hover = hv;
        this.svg.classList.toggle('over-band', !!(hv && hv.band && this.app.tool === 'select'));
        if (hv && hv.band) this.renderOverlay(); else if (changed) this.renderOverlay();
        return;
      }
      if (d.kind === 'move') {
        const dx = p.x - d.start.x, dy = p.y - d.start.y;
        if (!d.moved && Math.hypot(e.clientX - this.downAt.x, e.clientY - this.downAt.y) < 3) return;
        d.moved = true;
        const snap = e.altKey ? 1 : 10;
        for (const o of d.orig) M.moveState(this.m, o.id, Math.round((o.x + dx) / snap) * snap, Math.round((o.y + dy) / snap) * snap);
        this.toolbar.hidden = true;
        this.render();
      } else if (d.kind === 'connect') {
        d.pointer = p;
        const h = this.hit(e);
        const tgt = h.kind === 'state' ? h.id : null;
        if (tgt !== d.target) { d.target = tgt; this.render(); } else this.renderOverlay();
      } else if (d.kind === 'pan') {
        this.view.x = d.vx + (e.clientX - d.sx);
        this.view.y = d.vy + (e.clientY - d.sy);
        d.moved = d.moved || Math.hypot(e.clientX - d.sx, e.clientY - d.sy) > 3;
        this.applyView();
      } else if (d.kind === 'marquee') {
        d.b = p;
        const x1 = Math.min(d.a.x, d.b.x), x2 = Math.max(d.a.x, d.b.x), y1 = Math.min(d.a.y, d.b.y), y2 = Math.max(d.a.y, d.b.y);
        const inside = this.m.states.filter((s) => s.x >= x1 && s.x <= x2 && s.y >= y1 && s.y <= y2).map((s) => s.id);
        this.app.select(Array.from(new Set([...d.base, ...inside])), [], true);
        this.renderOverlay();
      }
    }

    onUp(e) {
      const d = this.drag;
      this.drag = null;
      this.host.classList.remove('panning');
      if (!d) return;
      const app = this.app;
      if (d.kind === 'move') {
        if (d.moved) app.change('הזזה', null, d.snap);
        else this.render();
      } else if (d.kind === 'connect') {
        const h = this.hit(e);
        const moved = Math.hypot(e.clientX - this.downAt.x, e.clientY - this.downAt.y) > 4;
        if (d.viaTool && !moved) {
          // כלי מעבר / "מעבר מכאן": לחיצה ראשונה בוחרת מקור, לחיצה שנייה בוחרת יעד
          if (!this.pendingFrom) { this.pendingFrom = d.from; app.select([d.from], [], true); app.hint('עכשיו לחצו על מצב היעד (או על אותו מצב, ללולאה)'); this.render(); return; }
          const from = this.pendingFrom; this.pendingFrom = null;
          app.connect(from, d.from);
          return;
        }
        if (h.kind === 'state') { this.pendingFrom = null; app.connect(d.from, h.id); }
        else if (!moved) { app.select([d.from], []); }
        this.render();
      } else if (d.kind === 'pan') {
        if (!d.moved) { app.select([], []); this.pendingFrom = null; }
        app.autosave();
      } else if (d.kind === 'marquee') {
        this.render();
      }
    }

    onDbl(e, h) {
      h = h || this.hit(e);
      if (this.app.isRunning()) return;
      if (h.kind === 'empty' && this.app.tool !== 'hand') this.app.addStateAt(this.toWorld(e));
      else if (h.kind === 'state') this.openRenameEditor(h.id);
      else if (h.kind === 'edge') { this.app.select([], [h.id]); this.openLabelEditor(h.id); }
    }

    /** תמונת SVG עצמאית של הדיאגרמה (לייצוא). */
    exportSVG() {
      const css = getComputedStyle(document.documentElement);
      const v = (n) => css.getPropertyValue(n).trim();
      const st = this.m.states;
      if (!st.length) return null;
      const xs = st.map((s) => s.x), ys = st.map((s) => s.y);
      const pad = 80;
      const x0 = Math.min(...xs) - pad, y0 = Math.min(...ys) - pad - 30, w = Math.max(...xs) - x0 + pad, h = Math.max(...ys) - y0 + pad;
      const clone = this.vp.cloneNode(true);
      clone.removeAttribute('transform');
      clone.querySelector('.overlay').remove();
      const style = '<style>' +
        '.ed-line{fill:none;stroke:' + v('--edge') + ';stroke-width:1.6}.ed-arrow{fill:' + v('--edge') + '}.ed-hit,.st-hit,.st-halo,.travel,.lb-hot{display:none}' +
        '.lb-bg{fill:' + v('--label-bg') + ';stroke:' + v('--label-line') + '}.lb-text{font:500 12.5px ' + v('--mono') + ';fill:' + v('--label-text') + ';text-anchor:middle}' +
        '.st-body{fill:' + v('--state-fill') + ';stroke:' + v('--state-stroke') + ';stroke-width:1.6}.st-inner{fill:none;stroke:' + v('--state-stroke') + ';stroke-width:1.4}' +
        '.st-name{font:500 14px ' + v('--mono') + ';fill:' + v('--state-text') + ';text-anchor:middle;dominant-baseline:central}</style>';
      return '<svg xmlns="' + SVGNS + '" viewBox="' + [x0, y0, w, h].join(' ') + '" width="' + w + '" height="' + h + '"><rect x="' + x0 + '" y="' + y0 + '" width="' + w + '" height="' + h + '" fill="' + v('--canvas') + '"/>' + style + clone.innerHTML + '</svg>';
    }
  }

  root.CanvasView = CanvasView;
})(typeof globalThis !== 'undefined' ? globalThis : this);
