/*
 * bank/float-win.js - המסגרת של חלון "השאלה" (סבב 14): חלון אמיתי מעל כל האפליקציה (ב-body, position: fixed), לא בתוך הקנבס.
 * גרירה בשורת הכותרת לכל מקום בחלון הדפדפן (תמיד כולו בפנים), שינוי גודל מהפינה (מינימום 300×220), מזעור לגלולה עם הכותרת.
 * המקום, הגודל והמזעור נשמרים בדפדפן (try/catch). ברירת מחדל: למעלה משמאל, מעל קצה הלוח, כדי לא לכסות את השרטוט.
 */
(function (root) {
  'use strict';
  const KEY = 'dsviz.automata.bf-win';
  const MIN_W = 300, MIN_H = 220;
  const store = {
    get() { try { return JSON.parse(localStorage.getItem(KEY) || 'null'); } catch (e) { return null; } },
    set(v) { try { localStorage.setItem(KEY, JSON.stringify(v)); } catch (e) { /* בלי אחסון: נשאר עד הטעינה הבאה */ } },
  };
  const zoom = () => parseFloat(getComputedStyle(document.documentElement).getPropertyValue('--ui-zoom')) || 1;
  /** גודל החלון של הדפדפן בפיקסלים של הממשק (אחרי ה-90%) */
  const view = () => ({ w: innerWidth / zoom(), h: innerHeight / zoom() });

  class FloatWin {
    /** el: החלון; head: בורר של שורת הכותרת (גרירה) */
    constructor(el, head) {
      this.el = el;
      this.head = head;
      const s = store.get();
      this.s = s && typeof s.x === 'number' ? s : this.defaults();
      this.bindDrag();
      this.ro = new ResizeObserver(() => {
        if (this.applying || this.s.min || el.hidden) return;
        const w = el.offsetWidth, h = el.offsetHeight;
        if (w && h && (Math.abs(w - this.s.w) > 1 || Math.abs(h - this.s.h) > 1)) { this.s.w = w; this.s.h = h; clearTimeout(this.t); this.t = setTimeout(() => store.set(this.s), 200); }
      });
      this.ro.observe(el);
      addEventListener('resize', () => this.apply());
    }
    defaults() {
      const v = view(), small = v.w < 1240 || v.h < 800;
      const w = small ? 320 : 384, h = Math.round(Math.min(small ? 470 : 640, v.h - 52 - 24));
      return { x: 12, y: 52 + 10, w, h, min: false };
    }
    /** מציב את החלון (ותמיד כולו בתוך חלון הדפדפן) */
    apply() {
      const el = this.el, s = this.s, v = view();
      this.applying = true;
      el.classList.toggle('min', !!s.min);
      const w = Math.max(MIN_W, Math.min(s.w, v.w - 8)), h = Math.max(MIN_H, Math.min(s.h, v.h - 8));
      el.style.width = (s.min ? '' : w + 'px');
      el.style.height = (s.min ? '' : h + 'px');
      const ew = el.offsetWidth || w, eh = el.offsetHeight || h;
      s.x = Math.max(0, Math.min(s.x, v.w - ew));
      s.y = Math.max(0, Math.min(s.y, v.h - Math.min(eh, 44)));
      if (!s.min) s.y = Math.max(0, Math.min(s.y, v.h - eh));
      el.style.left = s.x + 'px';
      el.style.top = s.y + 'px';
      requestAnimationFrame(() => { this.applying = false; });
    }
    setMin(on) { this.s.min = !!on; this.apply(); store.set(this.s); }
    reset() { this.s = this.defaults(); this.apply(); store.set(this.s); }

    bindDrag() {
      let d = null;
      const el = this.el;
      el.addEventListener('pointerdown', (e) => {
        if (!e.target.closest(this.head) || e.target.closest('button')) return;
        const z = zoom();
        d = { x: e.clientX, y: e.clientY, sx: this.s.x, sy: this.s.y, z };
        el.setPointerCapture(e.pointerId);
        el.classList.add('dragging');
        e.preventDefault();
      });
      el.addEventListener('pointermove', (e) => {
        if (!d) return;
        this.s.x = d.sx + (e.clientX - d.x) / d.z;
        this.s.y = d.sy + (e.clientY - d.y) / d.z;
        this.apply();
      });
      const end = () => { if (!d) return; d = null; el.classList.remove('dragging'); store.set(this.s); };
      el.addEventListener('pointerup', end);
      el.addEventListener('pointercancel', end);
      el.addEventListener('dblclick', (e) => { if (e.target.closest(this.head) && !e.target.closest('button')) this.setMin(!this.s.min); });
    }
  }

  root.FloatWin = FloatWin;
})(typeof globalThis !== 'undefined' ? globalThis : this);
