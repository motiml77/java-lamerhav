/*
 * guide.js - ההדרכה: כרטיס "שלושה צעדים" על הקנבס, ורשימת "איך בונים" בפאנל הצד.
 * הכרטיס מופיע רק בפעם הראשונה שנכנסים לכלי (ומיד נרשם שראו אותו), ומכפתור "?" על הקנבס. כשהקנבס ריק הוא במרכז, אחרת בצד.
 * הוא יושב ב-body (position: fixed) מעל כל הפאנלים, ונגלל בתוכו אם החלון נמוך (סבב 11).
 * "איך מתחילים?" (סבב 15): חלון עצמאי במרכז האפליקציה (לא בתוך הקנבס), עם שורת כותרת שאפשר לגרור,
 * ורק בפתיחה הראשונה של התוכנה. אחר כך לא חוזר לבד (גם לא ב"חדש" או בלשונית ריקה).
 */
(function (root) {
  'use strict';
  const I = root.Icons, D = root.Dialogs;
  const KEY = 'dsviz.automata.guide';
  const $ = (s) => document.querySelector(s);
  const store = { get() { try { return localStorage.getItem(KEY); } catch (e) { return 'seen'; } }, set(v) { try { localStorage.setItem(KEY, v); } catch (e) { /* פרטי */ } } };

  // איורים קטנים (72×44), בצבעי הממשק
  const ART = {
    dbl: '<svg viewBox="0 0 72 44" aria-hidden="true"><circle class="ga-st dash" cx="46" cy="22" r="13"/><text class="ga-t" x="46" y="26">q0</text>' +
      '<path class="ga-cur" d="M14 12l10 6-4.4 1 2.6 4.6-1.8 1-2.6-4.6-3.1 3.1z"/><path class="ga-tap" d="M9 7a7 7 0 0 1 9-2M7 13a12 12 0 0 1 3-8"/></svg>',
    link: '<svg viewBox="0 0 72 44" aria-hidden="true"><circle class="ga-st sel" cx="14" cy="22" r="10"/><circle class="ga-st hot" cx="58" cy="22" r="10"/><path class="ga-arrow dash" d="M25 22h18"/><path class="ga-head" d="M47 22l-6-3.4v6.8z"/>' +
      '<path class="ga-cur" d="M56 26l8 5-3.6.9 2.1 3.7-1.5.9-2.1-3.7-2.5 2.5z"/></svg>',
    trash: '<svg viewBox="0 0 72 44" aria-hidden="true"><circle class="ga-st" cx="32" cy="24" r="13"/><text class="ga-t" x="32" y="28">q1</text><circle class="ga-trash" cx="44" cy="12" r="7"/><path class="ga-trash-ic" d="M41 11h6M42.3 11l.4 4h2.6l.4-4M43 11v-1h2v1"/>' +
      '<path class="ga-cur" d="M50 16l8 5-3.6.9 2.1 3.7-1.5.9-2.1-3.7-2.5 2.5z"/></svg>',
  };

  const STEPS = [
    ['dbl', 'לחיצה כפולה על הקנבס', 'מוסיפה מצב חדש. הראשון הוא המצב ההתחלתי.'],
    ['link', 'לוחצים על מצב, ואז על מצב אחר', 'נוצר מעבר, ובוחרים לו אותיות. לחיצה שנייה על אותו מצב: לולאה.'],
    ['trash', 'מעבר עם העכבר מעל מצב או קשת', 'מופיע פח אדום. לחיצה עליו מוחקת (ואפשר לבטל).'],
  ];

  const Guide = {
    init(app) {
      this.app = app;
      this.el = $('#guide');
      document.body.appendChild(this.el);         // מעל חלון ההרצה ופאנל הצד, לא בתוך הקנבס
      this.first = store.get() !== 'seen';        // "איך מתחילים?" רק בפתיחה הראשונה של התוכנה
      this.pinned = false;                        // "איך בונים" נפתח רק מה-?
      if (this.first) store.set('seen');
      this.el.addEventListener('click', (e) => {
        const b = e.target.closest('[data-g]');
        if (!b) return;
        const a = b.dataset.g;
        if (a === 'close' || a === 'free') { this.first = false; this.hide(); }
        else if (a === 'auto' || a === 'practice') { this.first = false; this.pinned = false; this.update(); app.langPanel.open(a, { fresh: true }); }
        else if (a === 'bank') { this.first = false; this.pinned = false; this.update(); app.bank.show(); }
        else if (a === 'add') app.addStateCenter();
        else if (a === 'example') app.loadFirstExample();
        else if (a === 'keys') app.showHelp();
      });
      this.el.addEventListener('pointerdown', (e) => e.stopPropagation());
      this.bindDrag();
      document.addEventListener('keydown', (e) => {   // Esc סוגר את חלון הפתיחה
        if (e.key === 'Escape' && !this.el.hidden && this.el.dataset.mode === 'start') { e.stopImmediatePropagation(); this.first = false; this.hide(); }
      }, true);
      $('#guide-btn').addEventListener('click', () => { this.pinned = !this.pinned || this.el.hidden; this.update(); });
    },

    /** כרטיס "איך בונים" נפתח לבד רק בכניסה הראשונה לכלי. כל פעולה שמחליפה מכונה (שאלה מהמאגר, משימה, דוגמה, קובץ, "חדש", לשונית אחרת) מבטלת את זה */
    unpin() { if (this.first) { this.first = false; this.update(); } if (!this.pinned) return; this.pinned = false; store.set('seen'); if (this.el && this.el.dataset.mode !== 'start') this.update(); },

    hide() {
      this.pinned = false;
      store.set('seen');
      this.update();
      if (this.shifted) { this.shifted = false; this.app.canvas.fit(); this.app.autosave(); }   // מחזירים את המכונה למרכז
    },

    update() {
      const app = this.app, m = app.machine, empty = !m.states.length;
      const bank = app.bankFloat && app.bankFloat.isOpen();   // החלון "השאלה" יושב באותו מקום כמו הכרטיס הצף
      // חלון הפתיחה (סבב 14, 15): רק בפתיחה הראשונה, קנבס ריק ובלי משימה: ציור חופשי / התוכנה בונה / אני בונה
      const start = this.first && empty && !bank && !app.viewOnly && !root.AutomataTask.of(m) && !(app.langPanel && app.langPanel.isOpen());
      const show = start || (this.pinned && !bank && !app.viewOnly);
      this.el.hidden = !show;
      if (!show) { this.el.dataset.mode = ''; this.el.style.left = this.el.style.top = this.el.style.transform = ''; }
      $('#guide-btn').setAttribute('aria-expanded', String(show && !start));
      if (!show) return;
      const mode = start ? 'start' : empty ? 'empty' : 'float';
      if (this.el.dataset.mode === mode) return;
      if (this.el.dataset.mode === 'start') this.el.style.left = this.el.style.top = this.el.style.transform = '';
      this.el.dataset.mode = mode;
      if (start) { this.el.innerHTML = this.startHTML(); return; }
      // הכרטיס הצף יושב בצד שמאל: מזיזים את המכונה כדי שלא תוסתר
      if (mode === 'float') requestAnimationFrame(() => { this.shifted = true; this.app.canvas.fit(this.el.offsetWidth + 24); });
      this.el.innerHTML =
        '<div class="guide-head"><h2>' + (empty ? 'בונים מכונה בשלושה צעדים' : 'איך בונים') + '</h2>' +
        '<button type="button" class="pop-x" data-g="close" aria-label="סגירת ההדרכה">' + I.get('x', 16) + '</button></div>' +
        '<ol class="guide-steps">' + STEPS.map(([art, t, s], i) => '<li><span class="gs-num">' + (i + 1) + '</span><span class="gs-art">' + ART[art] + '</span><span class="gs-text"><b>' + t + '</b><span>' + s + '</span></span></li>').join('') + '</ol>' +
        '<div class="guide-foot">' + (empty
          ? '<button type="button" class="btn primary" data-g="add">' + I.get('state', 18) + '<span>מצב חדש</span></button><button type="button" class="btn secondary" data-g="example">' + I.get('book', 18) + '<span>טעינת דוגמה</span></button>'
          : '<button type="button" class="btn primary sm" data-g="close">הבנתי</button><button type="button" class="link" data-g="keys">כל קיצורי המקלדת</button>') + '</div>';
    },

    /** גרירת חלון הפתיחה בשורת הכותרת (תמיד כולו בתוך חלון הדפדפן) */
    bindDrag() {
      const el = this.el;
      let d = null;
      const zoom = () => parseFloat(getComputedStyle(document.documentElement).getPropertyValue('--ui-zoom')) || 1;
      el.addEventListener('pointerdown', (e) => {
        if (el.dataset.mode !== 'start' || !e.target.closest('.gw-head') || e.target.closest('button')) return;
        const z = zoom(), r = el.getBoundingClientRect();
        d = { x: e.clientX, y: e.clientY, l: r.left / z, t: r.top / z, w: r.width / z, h: r.height / z, z };
        el.style.left = d.l + 'px'; el.style.top = d.t + 'px'; el.style.transform = 'none';
        el.setPointerCapture(e.pointerId);
        el.classList.add('dragging');
        e.preventDefault();
      });
      el.addEventListener('pointermove', (e) => {
        if (!d) return;
        const vw = innerWidth / d.z, vh = innerHeight / d.z;
        el.style.left = Math.max(0, Math.min(vw - d.w, d.l + (e.clientX - d.x) / d.z)) + 'px';
        el.style.top = Math.max(0, Math.min(vh - Math.min(d.h, 44), d.t + (e.clientY - d.y) / d.z)) + 'px';
      });
      const end = () => { if (!d) return; d = null; el.classList.remove('dragging'); };
      el.addEventListener('pointerup', end);
      el.addEventListener('pointercancel', end);
    },

    /** חלון הפתיחה: שלוש דרכים להתחיל, וקישור למאגר */
    startHTML() {
      const card = (k, icon, t, s) => '<button type="button" class="gs-card" data-g="' + k + '"><span class="gs-ic">' + I.get(icon, 22) + '</span><b>' + t + '</b><span>' + s + '</span></button>';
      return '<div class="gw-head"><span class="gw-ic">' + I.get('state', 16) + '</span><b>איך מתחילים?</b><button type="button" class="pop-x" data-g="close" aria-label="סגירה">' + I.get('x', 16) + '</button></div>' +
        '<div class="gw-body"><div class="gs-cards">' + card('free', 'state', 'ציור חופשי', 'לחיצה כפולה על המשבצות מוסיפה מצב') +
        card('auto', 'transition', 'התוכנה בונה', 'כותבים נוסחה של שפה, ומקבלים אוטומט מוכן') +
        card('practice', 'check', 'אני בונה, התוכנה בודקת', 'כותבים נוסחה, בונים לבד, ובודקים אם זה נכון') + '</div>' +
        '<div class="guide-foot"><button type="button" class="link" data-g="bank">' + I.get('bank', 15) + '<span>מאגר שאלות</span></button></div></div>';
    },

    /** רשימת "איך בונים" בפאנל הצד: פעולה אחת בכל שורה. */
    sideHTML() {
      const k = D.kbd;
      const items = [
        ['state', 'לחיצה כפולה על הקנבס מוסיפה מצב.'],
        ['transition', 'לוחצים על מצב ואז על מצב אחר, ובוחרים אותיות למעבר.'],
        ['loop', 'לוחצים על מצב ואז שוב עליו: לולאה.'],
        ['edit', 'לחיצה על קשת פותחת שוב את האותיות שלה.'],
        ['accept', 'בסרגל שליד מצב נבחר: מקבל, התחלתי, לולאה.'],
        ['trash', 'ריחוף מעל מצב או קשת מראה פח אדום למחיקה.'],
        ['undo', k('Ctrl+Z') + ' מבטל כל פעולה.'],
      ];
      return '<section class="sec how"><h5>איך בונים</h5><ul class="how-list">' +
        items.map(([ic, t]) => '<li><span class="how-ic">' + I.get(ic, 18) + '</span><span class="how-t">' + t + '</span></li>').join('') +
        '</ul><button type="button" class="link" data-act="keys">כל קיצורי המקלדת</button></section>';
    },
  };

  root.Guide = Guide;
})(typeof globalThis !== 'undefined' ? globalThis : this);
