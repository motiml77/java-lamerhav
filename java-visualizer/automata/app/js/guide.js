/*
 * guide.js - ההדרכה: כרטיס "שלושה צעדים" על הקנבס, ורשימת "איך בונים" בפאנל הצד.
 * הכרטיס מופיע כשאין מצבים, ובפעם הראשונה שנכנסים לכלי. כפתור "?" על הקנבס מחזיר אותו.
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
      this.pinned = store.get() !== 'seen';
      this.el.addEventListener('click', (e) => {
        const b = e.target.closest('[data-g]');
        if (!b) return;
        const a = b.dataset.g;
        if (a === 'close') this.hide();
        else if (a === 'add') app.addStateCenter();
        else if (a === 'example') app.loadFirstExample();
        else if (a === 'keys') app.showHelp();
      });
      this.el.addEventListener('pointerdown', (e) => e.stopPropagation());
      $('#guide-btn').addEventListener('click', () => { this.pinned = !this.pinned || this.el.hidden; this.update(); });
    },

    hide() {
      this.pinned = false;
      store.set('seen');
      this.update();
      if (this.shifted) { this.shifted = false; this.app.canvas.fit(); this.app.autosave(); }   // מחזירים את המכונה למרכז
    },

    update() {
      const empty = !this.app.machine.states.length;
      const bank = this.app.bankFloat && this.app.bankFloat.isOpen();   // החלון "השאלה" יושב באותו מקום כמו הכרטיס הצף
      const show = empty || (this.pinned && !bank);
      this.el.hidden = !show;
      if (!show) this.el.dataset.mode = '';
      $('#guide-btn').setAttribute('aria-expanded', String(show));
      if (!show) return;
      const mode = empty ? 'empty' : 'float';
      if (this.el.dataset.mode === mode) return;
      this.el.dataset.mode = mode;
      // הכרטיס הצף יושב בצד שמאל: מזיזים את המכונה כדי שלא תוסתר
      if (mode === 'float') requestAnimationFrame(() => { this.shifted = true; this.app.canvas.fit(this.el.offsetWidth + 24); });
      this.el.innerHTML =
        '<div class="guide-head"><h2>' + (empty ? 'בונים מכונה בשלושה צעדים' : 'איך בונים') + '</h2>' +
        (empty ? '' : '<button type="button" class="pop-x" data-g="close" aria-label="סגירת ההדרכה">' + I.get('x', 16) + '</button>') + '</div>' +
        '<ol class="guide-steps">' + STEPS.map(([art, t, s], i) => '<li><span class="gs-num">' + (i + 1) + '</span><span class="gs-art">' + ART[art] + '</span><span class="gs-text"><b>' + t + '</b><span>' + s + '</span></span></li>').join('') + '</ol>' +
        '<div class="guide-foot">' + (empty
          ? '<button type="button" class="btn primary" data-g="add">' + I.get('state', 18) + '<span>מצב חדש</span></button><button type="button" class="btn secondary" data-g="example">' + I.get('book', 18) + '<span>טעינת דוגמה</span></button>'
          : '<button type="button" class="btn primary sm" data-g="close">הבנתי</button><button type="button" class="link" data-g="keys">כל קיצורי המקלדת</button>') + '</div>';
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
