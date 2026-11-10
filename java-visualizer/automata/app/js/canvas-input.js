/*
 * canvas-input.js - העכבר על הקנבס. מרחיב את CanvasView.
 *
 *   לחיצה כפולה על רקע ............ מצב חדש
 *   לחיצה על מצב .................. בוחר אותו, והוא הופך ל"מקור": חץ מקווקו הולך אחרי הסמן
 *   ואז לחיצה על מצב אחר ......... מעבר (נפתח חלון צף לבחירת האותיות). על אותו מצב: לולאה
 *   Esc / לחיצה על רקע ............ ביטול
 *   לחיצה על קשת או תווית ......... עריכת האותיות (אותו חלון צף)
 *   ריחוף על מצב / קשת ........... פח אדום קטן; לחיצה עליו שואלת ומוחקת
 *   גרירת מצב ..................... הזזה (נצמד לרשת 20; Alt: בלי הצמדה)
 *   גרירת רקע ..................... הזזת התצוגה. Shift + גרירה: בחירה במלבן. גלגלת: זום
 */
(function (root) {
  'use strict';
  const M = root.AutomataModel, G = root.AutomataGeometry;
  const CV = root.CanvasView;
  const MOVE_PX = 4;          // מתחת לזה זו לחיצה, לא גרירה
  const DBL_MS = 400;

  Object.assign(CV.prototype, {
    bind() {
      const svg = this.svg;
      svg.addEventListener('pointerdown', (e) => this.onDown(e));
      svg.addEventListener('pointermove', (e) => this.onMove(e));
      svg.addEventListener('pointerup', (e) => this.onUp(e));
      svg.addEventListener('pointercancel', () => { this.drag = null; this.render(); });
      svg.addEventListener('pointerleave', () => { if (!this.drag) { this.setHover(null); this.pointer = null; this.renderOverlay(); } });
      svg.addEventListener('wheel', (e) => {
        e.preventDefault();
        const r = svg.getBoundingClientRect();
        this.zoomAt(e.clientX - r.left, e.clientY - r.top, Math.exp(-e.deltaY * (e.ctrlKey ? 0.01 : 0.0015)));
        clearTimeout(this.wheelT);
        this.wheelT = setTimeout(() => this.app.autosave(), 300);
      }, { passive: false });
      svg.addEventListener('contextmenu', (e) => e.preventDefault());
      this.toolbar.addEventListener('pointerdown', (e) => e.stopPropagation());
      this.toolbar.addEventListener('click', (e) => {
        const b = e.target.closest('[data-act]');
        if (b) this.app.stateAction(b.dataset.act, this.toolbar.dataset.for);
      });
      this.editor.addEventListener('pointerdown', (e) => e.stopPropagation());
      window.addEventListener('resize', () => this.positionToolbar());
    },

    /** מה מתחת לסמן. בזמן גרירה ה-SVG תופס את המצביע, ולכן בודקים לפי מיקום ולא לפי e.target. */
    hit(e) {
      const el = (e.clientX != null && document.elementFromPoint(e.clientX, e.clientY)) || e.target;
      if (!el || !el.closest) return { kind: 'empty' };
      const ts = el.closest('[data-trash-state]');
      if (ts) return { kind: 'trash', state: ts.dataset.trashState };
      const te = el.closest('[data-trash-edge]');
      if (te) return { kind: 'trash', edge: te.dataset.trashEdge };
      const st = el.closest('[data-state]');
      if (st) return { kind: 'state', id: st.dataset.state };
      const ed = el.closest('[data-edge]');
      if (ed && ed.dataset.edge !== '__preview') return { kind: 'edge', id: ed.dataset.edge };
      return { kind: 'empty' };
    },

    /** מתחיל מעבר מהמצב sid (החץ המקווקו), או מבטל (sid = null). */
    arm(sid) {
      this.source = sid || null;
      if (sid) this.app.hint('לחצו על מצב אחר כדי ליצור מעבר. לחיצה על אותו מצב: לולאה. Esc: ביטול');
      else this.app.coach();
      this.render();
    },

    onDown(e) {
      const app = this.app;
      if (this.editor.dataset.open === '1') this.closeEditor(true);
      if (app.popover && app.popover.isOpen()) { app.popover.cancel(); return; }
      const h = this.hit(e);
      const p = this.toWorld(e);
      this.downAt = { x: e.clientX, y: e.clientY };
      this.svg.setPointerCapture(e.pointerId);
      if (e.button === 2) return;

      // לחיצה כפולה על רקע = מצב חדש (מזוהה ידנית, כי הציור מתחדש בין הלחיצות)
      const now = performance.now(), last = this.lastDown;
      this.lastDown = { t: now, x: e.clientX, y: e.clientY, kind: h.kind };
      if (h.kind === 'empty' && e.button === 0 && last && last.kind === 'empty' && now - last.t < DBL_MS && Math.hypot(e.clientX - last.x, e.clientY - last.y) < 6) {
        this.lastDown = null;
        this.drag = null;
        if (!app.isRunning()) { this.arm(null); app.addStateAt(p); }
        return;
      }
      const pan = e.button === 1 || this.spaceDown || app.tool === 'hand';
      if (h.kind === 'trash' && !pan && !app.isRunning()) {
        this.drag = null;
        app.confirmDelete(h.state ? [h.state] : [], h.edge ? [h.edge] : []);
        return;
      }
      if (h.kind === 'state' && !pan) {
        if (e.shiftKey) { app.toggleSelect('state', h.id); this.drag = null; return; }
        if (!app.sel.states.has(h.id) && !this.source) app.select([h.id], [], true);
        const ids = app.sel.states.has(h.id) ? Array.from(app.sel.states) : [h.id];
        this.drag = { kind: 'press', id: h.id, ids, start: p, orig: ids.map((id) => { const s = M.getState(this.m, id); return { id, x: s.x, y: s.y }; }), snap: app.snapshot() };
        return;
      }
      if (h.kind === 'edge' && !pan) { this.drag = { kind: 'press-edge', id: h.id }; return; }
      if (e.button === 0 && e.shiftKey && !pan) { this.drag = { kind: 'marquee', a: p, b: p, base: new Set(app.sel.states) }; return; }
      this.drag = { kind: 'pan', sx: e.clientX, sy: e.clientY, vx: this.view.x, vy: this.view.y, moved: false };
      this.host.classList.add('panning');
    },

    onMove(e) {
      const d = this.drag;
      const p = this.toWorld(e);
      this.pointer = p;
      if (!d || d.kind === 'press-edge') {
        const h = this.hit(e);
        this.setHover(h.kind === 'state' || h.kind === 'edge' ? { kind: h.kind, id: h.id } : h.kind === 'trash' ? (h.state ? { kind: 'state', id: h.state } : { kind: 'edge', id: h.edge }) : null);
        if (this.source) this.renderOverlay();
        return;
      }
      const far = Math.hypot(e.clientX - this.downAt.x, e.clientY - this.downAt.y) >= MOVE_PX;
      if (d.kind === 'press' && far && !this.app.isRunning()) {
        // התחילה גרירה: מזיזים, ומבטלים מעבר שהתחיל
        if (this.source) { this.source = null; this.app.hint(null); }
        if (!this.app.sel.states.has(d.id)) this.app.select([d.id], [], true);
        d.kind = 'move';
      }
      if (d.kind === 'move') {
        const dx = p.x - d.start.x, dy = p.y - d.start.y;
        for (const o of d.orig) {
          const q = e.altKey ? { x: o.x + dx, y: o.y + dy } : this.snap({ x: o.x + dx, y: o.y + dy });
          M.moveState(this.m, o.id, q.x, q.y);
        }
        this.toolbar.hidden = true;
        if (!this.raf) this.raf = requestAnimationFrame(() => { this.raf = null; this.render(); });
      } else if (d.kind === 'pan') {
        this.view.x = d.vx + (e.clientX - d.sx);
        this.view.y = d.vy + (e.clientY - d.sy);
        d.moved = d.moved || far;
        this.applyView();
      } else if (d.kind === 'marquee') {
        d.b = p;
        const x1 = Math.min(d.a.x, d.b.x), x2 = Math.max(d.a.x, d.b.x), y1 = Math.min(d.a.y, d.b.y), y2 = Math.max(d.a.y, d.b.y);
        const inside = this.m.states.filter((s) => s.x >= x1 && s.x <= x2 && s.y >= y1 && s.y <= y2).map((s) => s.id);
        this.app.select(Array.from(new Set([...d.base, ...inside])), [], true);
        this.renderOverlay();
      }
    },

    onUp(e) {
      const d = this.drag;
      this.drag = null;
      this.host.classList.remove('panning');
      if (!d) return;
      const app = this.app;
      if (d.kind === 'move') { if (this.raf) { cancelAnimationFrame(this.raf); this.raf = null; } app.change('הזזה', null, d.snap); return; }
      if (d.kind === 'press') {
        if (app.isRunning()) { app.select([d.id], [], true); return; }
        if (this.source) { const from = this.source; this.arm(null); app.openTransition(from, d.id); return; }
        app.select([d.id], [], true);
        this.arm(d.id);
        return;
      }
      if (d.kind === 'press-edge') {
        if (this.source) this.arm(null);
        if (app.isRunning()) { app.select([], [d.id]); return; }
        app.select([], [d.id], true);
        app.openTransitionEdge(d.id);
        return;
      }
      if (d.kind === 'pan') {
        if (!d.moved) { if (this.source) this.arm(null); app.select([], []); }
        app.autosave();
        return;
      }
      if (d.kind === 'marquee') this.render();
    },
  });

  void G;
})(typeof globalThis !== 'undefined' ? globalThis : this);
