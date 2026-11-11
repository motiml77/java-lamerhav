/*
 * layout.js - גדלים שהמשתמש קובע (סבב 12): גרירה של הקצה העליון של אזור ההרצה (גובה) ושל הקצה הפנימי של פאנל הצד (רוחב),
 * לחיצה כפולה מחזירה לברירת המחדל, מקלדת (חצים, Shift = צעד גדול, Home / End, Enter = ברירת מחדל), וכפתור קיפול של פאנל הצד
 * (גם Ctrl+B). הגדלים נשמרים בדפדפן. הקנבס מתעדכן תוך כדי, והשרטוט לא זז על המסך (ResizeObserver מתקן את view.x כשהקצה השמאלי זז).
 * הגדלים הם משתנים על :root (--run-h, --side-w), כך שגם כרטיס ההדרכה (ב-body) רואה אותם. הפריסה עצמה ב-css/base.css.
 */
(function (root) {
  'use strict';
  const I = root.Icons;
  const KEY = 'dsviz.automata.layout';
  const $ = (s) => document.querySelector(s);
  const store = {
    get() { try { return JSON.parse(localStorage.getItem(KEY) || 'null') || {}; } catch (e) { return {}; } },
    set(v) { try { localStorage.setItem(KEY, JSON.stringify(v)); } catch (e) { /* פרטי: נשאר רק עד הטעינה הבאה */ } },
  };
  const ZOOM = () => parseFloat(getComputedStyle(document.documentElement).getPropertyValue('--ui-zoom')) || 1;
  /** גבולות בפיקסלים של CSS בתוך הממשק (החלון מחולק ב-zoom) */
  const LIMITS = {
    run: () => ({ min: 140, max: Math.max(140, Math.round((innerHeight / ZOOM()) * 0.6)) }),
    side: () => ({ min: 220, max: Math.max(220, Math.round((innerWidth / ZOOM()) * 0.45)) }),
  };
  const clamp = (v, l) => Math.round(Math.min(l.max, Math.max(l.min, v)));

  const Layout = {
    init(app) {
      this.app = app;
      this.html = document.documentElement;
      this.state = store.get();             // { run, side, collapsed }
      this.bind($('#split-run'), 'run');
      this.bind($('#split-side'), 'side');
      this.toggle = $('#side-toggle');
      this.toggle.addEventListener('click', () => this.setCollapsed(!this.state.collapsed));
      addEventListener('resize', () => this.apply(false));
      const app$ = $('#app');
      app$.classList.add('no-anim');               // בטעינה: בלי תנועה מברירת המחדל לגודל השמור
      this.apply(false);
      requestAnimationFrame(() => requestAnimationFrame(() => app$.classList.remove('no-anim')));
      this.watchStage();
    },

    /** ברירת המחדל מה-CSS (tokens.css, ומסכים צרים ב-base.css), בלי מה שהמשתמש קבע */
    defaults() {
      const st = this.html.style, keep = [st.getPropertyValue('--run-h'), st.getPropertyValue('--side-w')];
      st.removeProperty('--run-h'); st.removeProperty('--side-w');
      const cs = getComputedStyle(this.html), d = { run: parseFloat(cs.getPropertyValue('--run-h')), side: parseFloat(cs.getPropertyValue('--side-w')) };
      if (keep[0]) st.setProperty('--run-h', keep[0]);
      if (keep[1]) st.setProperty('--side-w', keep[1]);
      return d;
    },
    size(kind) { const d = this.defaults(); return clamp(this.state[kind] || d[kind], LIMITS[kind]()); },

    /** מחיל את הגדלים (save: גם שומר) */
    apply(save) {
      const st = this.html.style, s = this.state;
      if (s.run) st.setProperty('--run-h', this.size('run') + 'px'); else st.removeProperty('--run-h');
      if (s.collapsed) st.setProperty('--side-w', '0px');
      else if (s.side) st.setProperty('--side-w', this.size('side') + 'px');
      else st.removeProperty('--side-w');
      $('#app').classList.toggle('side-collapsed', !!s.collapsed);
      $('.side').inert = !!s.collapsed;
      const t = this.toggle, label = s.collapsed ? 'פתיחת הלוח' : 'קיפול הלוח';
      t.title = label + ' (Ctrl+B)'; t.setAttribute('aria-label', label); t.setAttribute('aria-expanded', String(!s.collapsed));
      t.innerHTML = I.get(s.collapsed ? 'chevronRight' : 'chevronLeft', 14);
      for (const kind of ['run', 'side']) {
        const el = $('#split-' + kind), l = LIMITS[kind]();
        el.setAttribute('aria-valuemin', l.min); el.setAttribute('aria-valuemax', l.max); el.setAttribute('aria-valuenow', this.size(kind));
      }
      $('#split-side').hidden = !!s.collapsed;
      if (save) store.set(s);
    },
    set(kind, v) { this.state[kind] = clamp(v, LIMITS[kind]()); this.apply(true); },
    reset(kind) { delete this.state[kind]; this.apply(true); this.app.toast(kind === 'run' ? 'אזור ההרצה חזר לגובה הרגיל' : 'הלוח חזר לרוחב הרגיל'); },
    setCollapsed(on) {
      this.state.collapsed = !!on;
      if (on && this.app.side && document.activeElement && $('.side').contains(document.activeElement)) this.toggle.focus();
      this.apply(true);
    },

    /** גרירה, לחיצה כפולה ומקלדת על קו ההפרדה */
    bind(el, kind) {
      let d = null;
      el.addEventListener('pointerdown', (e) => {
        if (e.button !== 0) return;
        e.preventDefault();
        const z = this.app.canvas.uiScale();
        d = { x: e.clientX, y: e.clientY, start: this.size(kind), z };
        el.setPointerCapture(e.pointerId);
        el.classList.add('dragging');
        document.body.classList.add(kind === 'run' ? 'resizing-row' : 'resizing-col');
      });
      el.addEventListener('pointermove', (e) => {
        if (!d) return;
        // אזור ההרצה גדל כשגוררים למעלה; פאנל הצד (משמאל לקנבס) גדל כשגוררים ימינה
        const v = kind === 'run' ? d.start - (e.clientY - d.y) / d.z : d.start + (e.clientX - d.x) / d.z;
        this.state[kind] = clamp(v, LIMITS[kind]());
        this.apply(false);
      });
      const end = () => {
        if (!d) return;
        d = null;
        el.classList.remove('dragging');
        document.body.classList.remove('resizing-row', 'resizing-col');
        store.set(this.state);
        this.app.autosave();
      };
      el.addEventListener('pointerup', end);
      el.addEventListener('pointercancel', end);
      el.addEventListener('dblclick', () => this.reset(kind));
      el.addEventListener('keydown', (e) => {
        const step = e.shiftKey ? 64 : 16, cur = this.size(kind), l = LIMITS[kind]();
        const keys = kind === 'run' ? { ArrowUp: cur + step, ArrowDown: cur - step } : { ArrowRight: cur + step, ArrowLeft: cur - step };
        let v = keys[e.key];
        if (e.key === 'Home') v = l.min;
        if (e.key === 'End') v = l.max;
        if (e.key === 'Enter') { e.preventDefault(); e.stopPropagation(); this.reset(kind); return; }
        if (v == null) return;
        e.preventDefault(); e.stopPropagation();
        this.set(kind, v);
      });
    },

    /** הקצה השמאלי של הקנבס זז (פאנל הצד גדל / קטן / מתקפל): מזיזים את התצוגה כך שהשרטוט נשאר באותו מקום על המסך */
    watchStage() {
      const cv = this.app.canvas;
      let left = cv.svg.getBoundingClientRect().left;
      new ResizeObserver(() => {
        const now = cv.svg.getBoundingClientRect().left, z = cv.uiScale();
        if (now !== left && z) {
          const dx = (left - now) / z;
          for (const m of Object.values(this.app.machines)) if (m && m.view) m.view.x += dx;
          cv.applyView();
        }
        left = now;
      }).observe(cv.host);
    },
  };

  root.Layout = Layout;
})(typeof globalThis !== 'undefined' ? globalThis : this);
