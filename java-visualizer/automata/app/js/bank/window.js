/*
 * bank/window.js - חלון "מאגר שאלות", באותו מבנה כמו במאגר של התוכנות ל-Visual Studio, Eclipse ו-IntelliJ:
 * מימין: הכותרת, "ההתקדמות שלי" (X מתוך Y, ופס לכל רמה), חיפוש, סינון לפי רמה, ועץ: נושא (סמל, שם, כמה סומנו מתוך כמה)
 * שנפתח לשאלות (נקודה בצבע הקושי, שם, V למי שסיים). במרכז: הנוסח של השאלה שנבחרה, סעיף אחרי סעיף.
 * למטה: "צור תרגיל" (או "פתח את התרגיל" אם כבר התחלתם), "שאלה אקראית", "סגירה".
 * גם עוזרים לכתיבה מימין לשמאל, שמשותפים לחלון הצף של השאלה (BankView).
 */
(function (root) {
  'use strict';
  const B = root.AutomataBank, M = root.AutomataModel, L = root.AutomataLang, I = root.Icons;
  const esc = (s) => String(s == null ? '' : s).replace(/[&<>"]/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' })[c]);
  /** טקסט עברי עם קטעים לטיניים / מתמטיים: כל קטע כזה יחידה אחת משמאל לימין (כמו RtlText ב-Java). שורה חדשה נשמרת. */
  const RUN = /([A-Za-z0-9Σεᴿⁿᵐᵏ²⁺_#({[∪∩∈|=≥≤<>+*/·−'"][A-Za-z0-9Σεᴿⁿᵐᵏ²⁺_#(){}[\]∪∩∈|=≥≤<>+*/·−'", ]*[A-Za-z0-9Σεᴿⁿᵐᵏ²⁺_#)}\]∪∩'"]|[A-Za-z0-9Σεᴿⁿᵐᵏ²_#])/;
  const rtl = (t) => String(t || '').split('\n').map((line) => line.split(RUN).map((x, i) => (i % 2 ? '<bdi dir="ltr" class="bk-sym">' + esc(x) + '</bdi>' : esc(x))).join('')).join('<br>');
  const formula = (s) => { try { return L.pretty(L.parse(s)).html; } catch (e) { return esc(s); } };
  const word = (w) => (w === '' ? 'ε' : esc(w));
  const chips = (ws, cls) => '<span class="bk-words" dir="ltr">' + ws.map((w) => '<span class="w-chip ' + cls + '">' + word(w) + '</span>').join('') + '</span>';
  const TYPE = (t) => M.TYPE_INFO[t];
  const typeTag = (t) => '<span class="type-tag ' + t + '">' + TYPE(t).en + '</span>';
  const dot = (level) => '<span class="bk-dot ' + level + '" aria-hidden="true"></span>';
  const vMark = (on, title) => (on ? '<span class="bk-v" title="' + esc(title || 'סיימתי') + '">' + I.get('check', 12) + '</span>' : '');

  /** הכרטיס של סעיף (בחלון המאגר ובחלון הצף). extra: מה שבא אחרי הנוסח (כפתורים, בדיקה) */
  function sectionBody(q, s) {
    let h = '<div class="bk-text">' + rtl(s.text) + '</div>';
    if (s.language) {
      h += '<div class="bk-lang"><div class="bk-formula" dir="ltr">' + formula(s.language) + '</div>' +
        '<div class="bk-meta"><span>הא"ב: <bdi dir="ltr" class="bk-sym">Σ = {' + esc(q.alphabet.join(', ')) + '}</bdi></span>' +
        (s.kind === 'build' ? '<span class="bk-need">' + typeTag(s.machine) + 'נדרש: ' + esc(TYPE(s.machine).long) + '</span>' : '') + '</div></div>';
    }
    if (s.kind === 'tm-output') {
      h += '<div class="bk-meta"><span>הא"ב: <bdi dir="ltr" class="bk-sym">Σ = {' + esc(q.alphabet.join(', ')) + '}</bdi></span><span class="bk-need">' + typeTag('tm') + 'נדרש: מכונת טיורינג שמחשבת</span></div>' +
        '<table class="bk-cases"><thead><tr><th>קלט</th><th>הפלט על הסרט</th></tr></thead><tbody>' +
        s.cases.map(([i, o]) => '<tr><td dir="ltr">' + word(i) + '</td><td dir="ltr">' + word(o) + '</td></tr>').join('') + '</tbody></table>';
    }
    if (s.examples) {
      h += '<div class="bk-ex"><span class="bk-ex-t in">בשפה</span>' + chips(s.examples.in, 'in') + '</div>' +
        '<div class="bk-ex"><span class="bk-ex-t out">לא בשפה</span>' + chips(s.examples.out, 'out') + '</div>';
    }
    return h;
  }

  root.BankView = { esc, rtl, formula, word, chips, typeTag, dot, vMark, sectionBody };

  class BankWindow {
    constructor(app) {
      this.app = app;
      this.progress = app.bankProgress;
      this.open = new Set();
      this.level = '';
      this.search = '';
      this.selected = null;
      this.el = null;
      this.progress.listen(() => { if (this.el) this.render(); });
    }

    /** השאלה האחרונה שעבדו עליה נבחרת בפעם הבאה שהחלון נפתח (כמו במאגר של Java) */
    remember(id) { const q = B.question(id); if (q) { this.selected = id; this.open.add(q.topic); } }
    show(id) {
      root.Guide.unpin();
      if (!B.access('open')) { if (!B.denied('open', null, () => this.show(id))) this.app.toast('אין גישה למאגר השאלות', 'err'); return; }
      if (id) this.remember(id);
      if (!this.el) {
        this.el = document.createElement('div');
        this.el.className = 'modal-backdrop bk-backdrop';
        this.el.innerHTML = '<div class="bk-window" role="dialog" aria-modal="true" aria-label="מאגר שאלות"></div>';
        document.body.appendChild(this.el);
        this.el.addEventListener('click', (e) => this.onClick(e));
        this.el.addEventListener('input', (e) => { if (e.target.id === 'bk-search') { this.search = e.target.value; this.renderTree(); } });
        this.el.addEventListener('keydown', (e) => { e.stopPropagation(); if (e.key === 'Escape') this.close(); });
      }
      this.render();
      const s = this.el.querySelector('#bk-search');
      if (s) s.focus();
    }
    close() { if (this.el) { this.el.remove(); this.el = null; } }
    isOpen() { return !!this.el; }

    render() {
      const w = this.el.querySelector('.bk-window');
      w.innerHTML = '<header class="bk-titlebar"><span class="bk-tb-icon">' + I.get('bank', 16) + '</span><b>מאגר שאלות: מודלים חישוביים</b>' +
        '<button type="button" class="btn icon ghost sm" data-bk="close" title="סגירה (Esc)" aria-label="סגירה">' + I.get('x', 16) + '</button></header>' +
        '<div class="bk-body"><aside class="bk-side">' + this.sideHTML() + '</aside><main class="bk-main">' + this.mainHTML() + '</main></div>';
      const s = w.querySelector('#bk-search');
      if (s) { s.value = this.search; }
    }
    renderTree() { const t = this.el.querySelector('#bk-tree'); if (t) t.innerHTML = this.treeHTML(); }

    sideHTML() {
      const p = this.progress, all = B.questions;
      const bar = (done, n) => '<span class="bk-bar"><i style="width:' + (n ? Math.round((100 * done) / n) : 0) + '%"></i></span>';
      const levels = B.LEVELS.map((l) => { const qs = all.filter((q) => q.level === l.id), d = p.doneCount(qs); return '<div class="bk-lv"><span class="bk-lv-t ' + l.id + '">' + l.title + '</span>' + bar(d, qs.length) + '<span class="bk-lv-n">' + d + '/' + qs.length + '</span></div>'; }).join('');
      return '<div class="bk-side-head"><h2>מאגר שאלות</h2><p>מודלים חישוביים</p></div>' +
        '<div class="bk-progress"><div class="bk-pr-head"><b>ההתקדמות שלי</b><span>' + p.doneCount(all) + ' מתוך ' + all.length + '</span></div>' + levels + '</div>' +
        '<input type="search" id="bk-search" class="bk-search" placeholder="חיפוש שאלה..." aria-label="חיפוש שאלה" autocomplete="off">' +
        '<div class="bk-chips" role="group" aria-label="רמה">' + [{ id: '', title: 'הכול' }].concat(B.LEVELS).map((l) => '<button type="button" class="bk-chip' + (this.level === l.id ? ' on' : '') + '" data-bk-level="' + l.id + '">' + l.title + '</button>').join('') + '</div>' +
        '<div class="bk-tree" id="bk-tree">' + this.treeHTML() + '</div>';
    }

    treeHTML() {
      const p = this.progress, filtering = !!(this.search.trim() || this.level);
      return B.topics.map((t) => {
        const all = B.ofTopic(t.id), qs = all.filter((q) => B.matches(q, this.search, this.level));
        if (filtering && !qs.length) return '';
        const open = filtering || this.open.has(t.id), d = p.doneCount(all);
        let h = '<button type="button" class="bk-topic' + (open ? ' open' : '') + '" data-bk-topic="' + t.id + '" aria-expanded="' + open + '">' +
          '<span class="bk-chev">' + I.get('chevronDown', 14) + '</span><span class="bk-ticon ' + t.id + '">' + t.en + '</span><span class="bk-tname">' + esc(t.title) + '</span>' +
          '<span class="bk-tcount">' + d + '/' + all.length + '</span><span class="bk-bar sm"><i style="width:' + Math.round((100 * d) / all.length) + '%"></i></span></button>';
        if (open) {
          h += '<div class="bk-qs">' + qs.map((q) => '<button type="button" class="bk-q' + (this.selected === q.id ? ' sel' : '') + '" data-bk-q="' + q.id + '">' + dot(q.level) +
            '<span class="bk-qname">' + rtl(q.title) + '</span>' + vMark(p.done(q.id), 'סיימתי את השאלה') + '</button>').join('') + '</div>';
        }
        return h;
      }).join('') || '<p class="bk-none">אין שאלות שמתאימות לחיפוש.</p>';
    }

    mainHTML() {
      const head = '<p class="bk-lead">בוחרים שאלה מהרשימה, ונוצר תרגיל מוכן: מכונה ריקה מהסוג הנכון על הקנבס, השאלה בחלון צף לידה, ובדיקה לכל סעיף.</p>';
      const q = this.selected && B.question(this.selected);
      if (!q) return head + '<div class="bk-empty">' + I.get('bank', 40) + '<p>בחרו שאלה מהרשימה שמימין.</p></div><footer class="bk-foot">' + this.buttons(null) + '</footer>';
      const p = this.progress, topic = B.topics.find((t) => t.id === q.topic);
      const cards = q.question.sections.map((s) => '<section class="bk-card"><h3>סעיף ' + s.label + '\'' + vMark(p.sectionDone(q.id, s.label), 'הסעיף נפתר') + '</h3>' + sectionBody(q, s) + '</section>').join('');
      return head + '<div class="bk-scroll"><div class="bk-qtitle">' + dot(q.level) + '<h2>' + rtl(q.title) + '</h2>' + vMark(p.done(q.id), 'סיימתי את השאלה') + '</div>' +
        '<p class="bk-qsub ' + q.level + '">' + esc(topic.title) + ' · רמה: ' + B.levelTitle(q.level) + '</p>' +
        '<div class="bk-card bk-intro">' + rtl(q.question.intro) + '</div>' + cards + '</div><footer class="bk-foot">' + this.buttons(q) + '</footer>';
    }

    buttons(q) {
      const has = q && this.progress.hasWork(q.id);
      return (q ? '<button type="button" class="btn primary" data-bk="create">' + (has ? 'פתח את התרגיל' : 'צור תרגיל') + '</button>' : '<button type="button" class="btn primary" disabled>צור תרגיל</button>') +
        (has ? '<button type="button" class="btn secondary" data-bk="fresh">התחלה מחדש</button>' : '') +
        '<button type="button" class="btn secondary" data-bk="random">שאלה אקראית</button><button type="button" class="btn secondary" data-bk="close">סגירה</button>';
    }

    onClick(e) {
      if (e.target === this.el) { this.close(); return; }
      const t = e.target.closest('[data-bk-topic]');
      if (t) { const id = t.dataset.bkTopic; if (!this.open.delete(id)) this.open.add(id); this.renderTree(); return; }
      const q = e.target.closest('[data-bk-q]');
      if (q) { this.selected = q.dataset.bkQ; this.render(); return; }
      const lv = e.target.closest('[data-bk-level]');
      if (lv) { this.level = lv.dataset.bkLevel; this.render(); return; }
      const b = e.target.closest('[data-bk]');
      if (!b) return;
      const act = b.dataset.bk, sel = this.selected && B.question(this.selected);
      if (act === 'close') this.close();
      else if (act === 'random') {
        const pool = B.questions.filter((x) => B.matches(x, this.search, this.level) && x.id !== this.selected);
        const pick = pool[Math.floor(Math.random() * pool.length)];
        if (pick) { this.selected = pick.id; this.open.add(pick.topic); this.render(); }
      } else if ((act === 'create' || act === 'fresh') && sel) {
        if (!B.access('create', sel)) { this.close(); if (!B.denied('create', sel, () => this.show(sel.id))) this.app.toast('אין גישה ליצירת תרגיל', 'err'); return; }
        if (act === 'fresh') this.progress.clearWork(sel.id);
        this.close();
        this.app.bankFloat.start(sel.id);
      }
    }
  }

  root.BankWindow = BankWindow;
})(typeof globalThis !== 'undefined' ? globalThis : this);
