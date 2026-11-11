/*
 * bank/float.js - חלון "השאלה" שצף מעל הקנבס בזמן תרגיל מהמאגר (כמו BankFloat בתוכנות של Java ו-C#).
 * בו: השם והרמה, מה בונים, הנוסח, מעבר בין סעיפים, ולכל סעיף "בדיקה" ו-💡 רמז; לסעיף הבא "הכן סעיף ב'" (מכונה חדשה על הקנבס,
 * והמכונה של הסעיף הקודם נשמרת); ובסוף "סיימתי את התרגיל": האישור בכרטיס בתוך החלון (לשמור את המכונה, או להתחיל נקי), בלי חלון שמכסה.
 * המכונה של כל סעיף נשמרת בהתקדמות (bank/core.js) בכל שינוי, דרך app.autosave.
 */
(function (root) {
  'use strict';
  const B = root.AutomataBank, M = root.AutomataModel, L = root.AutomataLang, I = root.Icons, D = root.Dialogs, V = root.BankView;
  const { esc, rtl, word } = V;
  const TYPE = (t) => M.TYPE_INFO[t];

  class BankFloat {
    constructor(app, host) {
      this.app = app;
      this.progress = app.bankProgress;
      this.el = document.createElement('div');
      this.el.className = 'bf';
      this.el.id = 'bank-float';
      this.el.hidden = true;
      this.el.setAttribute('role', 'complementary');
      this.el.setAttribute('aria-label', 'השאלה');
      document.body.appendChild(this.el);          // סבב 14: חלון מעל כל האפליקציה, לא בתוך הקנבס (host לא בשימוש)
      this.q = null; this.label = null; this.results = {}; this.hints = new Set(); this.confirm = null; this.loading = false; this.busy = null;
      this.el.addEventListener('click', (e) => this.onClick(e));
      this.el.addEventListener('input', (e) => this.onInput(e));
      this.el.addEventListener('change', (e) => { if (e.target.dataset.bfDone) { this.progress.setSectionDone(this.q.id, e.target.dataset.bfDone, e.target.checked); this.render(); } if (e.target.name === 'bf-keep') { this.confirm.keep = e.target.value === 'keep'; this.render(); } });
      this.el.addEventListener('keydown', (e) => { if (e.target.closest('input, textarea')) e.stopPropagation(); });
      this.win = new root.FloatWin(this.el, '.bf-head');   // גרירה, גודל, מזעור, ושמירה של המקום (float-win.js)
      this.progress.listen(() => { if (this.q && !this.el.hidden && !this.rendering) this.renderSoon(); });
      // בדיקה שהסתיימה בפס המשימה (מהכפתור כאן, מהפס או מ-Ctrl+Enter): סעיף "בונים" שעבר מקבל V
      app.taskUI.onResult((res, m) => {
        const s = this.section;
        if (this.q && this.isOpen() && s && s.kind === 'build' && m === this.app.machine && res.ok && !this.progress.sectionDone(this.q.id, this.label)) { this.passed(this.label); this.render(); }
      });
    }

    get section() { return this.q && B.section(this.q, this.label); }
    isOpen() { return !this.el.hidden; }

    /** "צור תרגיל" / "פתח את התרגיל": המכונה של הסעיף הראשון שעוד לא נפתר (או של הסעיף שהיה פתוח) על הקנבס, והחלון לידה */
    start(id) {
      const q = B.question(id);
      const ms = B.machineSections(q), act = this.progress.active();
      const pick = act && act.id === id ? act.label : (ms.find((s) => this.progress.work(id, s.label) && !this.progress.sectionDone(id, s.label)) || ms.find((s) => !this.progress.sectionDone(id, s.label)) || ms[0]).label;
      this.persist();                              // העבודה של תרגיל קודם שהיה פתוח
      this.q = q; this.label = null; this.results = {}; this.hints.clear(); this.confirm = null;
      this.app.bank.remember(id);
      this.openSection(pick);
      this.show();
    }
    /** אחרי טעינה של הדף: אם היה תרגיל פתוח, החלון חוזר (המכונה עצמה כבר נשמרה עם שאר הלשוניות) */
    restore() {
      const a = this.progress.active(), q = a && B.question(a.id);
      if (!q || !B.section(q, a.label)) return;
      this.q = q; this.label = a.label;
      this.app.bank.remember(q.id);
      const t = B.machineType(this.section);
      if (this.app.active !== t) this.app.switchType(t);
      this.show();
    }
    show() { this.setOpen(true); this.render(); this.win.apply(); }
    hide() { this.setOpen(false); }
    setOpen(on) { this.el.hidden = !on; root.Guide.update(); if (this.app.strip) this.app.strip.render(); }

    /** המכונה של הסעיף על הקנבס: מה שנשמר, או מכונה ריקה מהסוג הנכון */
    openSection(label) {
      this.persist();
      const s = B.section(this.q, label), saved = this.progress.work(this.q.id, label);
      const m = (saved && M.deserialize(saved).machine) || B.emptyMachine(this.q, s);
      this.label = label;
      this.loading = true;
      try { this.app.loadMachine(m, 'תרגיל מהמאגר: ' + this.q.title + ', סעיף ' + label, ''); } finally { this.loading = false; }
      this.progress.setActive({ id: this.q.id, label });
      this.lastSaved = null;
      this.persist();
    }
    /** נקרא מ-app.autosave: שומר את המכונה שעל הקנבס כמכונה של הסעיף הפעיל */
    persist() {
      if (!this.q || !this.label) return;      // גם כשהחלון סגור: התרגיל עדיין פתוח (נפתח שוב מהפס)
      const s = this.section;
      if (!s || this.app.machine.type !== B.machineType(s)) return;
      const json = M.serialize(this.app.machine);
      if (json === this.lastSaved) return;
      this.lastSaved = json;
      this.progress.setWork(this.q.id, this.label, json);
    }
    /** קוד אחר טוען מכונה (דוגמה, קובץ, "חדש"): התרגיל נסגר, והעבודה שלו נשמרת */
    detach() {
      if (!this.q) return;
      this.persist();
      this.progress.setActive(null);
      this.q = null; this.label = null;
      this.setOpen(false);
    }

    renderSoon() { cancelAnimationFrame(this.raf); this.raf = requestAnimationFrame(() => this.render()); }

    render() {
      if (!this.q) return;
      this.rendering = true;
      const q = this.q, p = this.progress, topic = B.topics.find((t) => t.id === q.topic);
      const scroll = this.el.querySelector('.bf-body') ? this.el.querySelector('.bf-body').scrollTop : 0;
      const ms = B.machineSections(q), s0 = this.section, done = p.done(q.id);
      const head = '<header class="bf-head" title="גרירה מזיזה את החלון"><span class="bf-hicon">' + I.get('bank', 15) + '</span><b>השאלה: ' + rtl(q.title) + '</b>' +
        '<button type="button" class="btn icon ghost sm" data-bf="bank" title="מאגר שאלות" aria-label="מאגר שאלות">' + I.get('list', 15) + '</button>' +
        '<button type="button" class="btn icon ghost sm" data-bf="min" title="מזעור" aria-label="מזעור">' + I.get('zoomOut', 15) + '</button>' +
        '<button type="button" class="btn icon ghost sm" data-bf="close" title="סגירת החלון (העבודה נשמרת)" aria-label="סגירה">' + I.get('x', 15) + '</button></header>';
      let body;
      if (this.confirm) body = this.confirmHTML();
      else {
        body = '<div class="bf-title">' + V.dot(q.level) + '<h3>' + rtl(q.title) + '</h3>' + V.vMark(done, 'סיימתי את השאלה') + '</div>' +
          '<p class="bk-qsub ' + q.level + '">' + esc(topic.title) + ' · רמה: ' + B.levelTitle(q.level) + '</p>' +
          '<div class="bf-info"><b>על הקנבס: ' + esc(TYPE(B.machineType(s0)).long) + (q.question.sections.length > 1 ? ' של סעיף ' + this.label + '\'' : '') + '</b><span>בונים את המכונה, ובודקים כל סעיף בכפתור "בדיקה".</span></div>' +
          (q.question.sections.length > 1 ? '<nav class="bf-nav" aria-label="סעיפים">' + q.question.sections.map((s) => '<button type="button" class="bf-jump' + (s.label === this.label ? ' on' : '') + '" data-bf-jump="' + s.label + '">סעיף ' + s.label + '\'' + V.vMark(p.sectionDone(q.id, s.label)) + '</button>').join('') + '</nav>' : '') +
          '<div class="bk-card bk-intro">' + rtl(q.question.intro) + '</div>' + q.question.sections.map((s) => this.sectionHTML(s)).join('');
      }
      const foot = this.confirm ? '' : '<footer class="bf-foot"><button type="button" class="bf-finish' + (done ? ' done' : '') + '" data-bf="finish" title="' + (done ? 'לחיצה מבטלת את הסימון' : 'סימון ירוק לשאלה במאגר') + '">' + I.get('check', 18) + '<span>' + (done ? 'עשיתי את התרגיל' : 'סיימתי את התרגיל') + '</span></button></footer>';
      this.el.innerHTML = head + '<div class="bf-body">' + body + '</div>' + foot;
      this.el.querySelector('.bf-body').scrollTop = scroll;
      const res = this.scrollTo && this.el.querySelector('#bf-sec-' + this.scrollTo + ' .bf-result');
      if (res) { res.scrollIntoView({ block: 'nearest' }); this.scrollTo = null; }
      this.rendering = false;
    }

    sectionHTML(s) {
      const q = this.q, p = this.progress, L0 = s.label, active = L0 === this.label, isM = s.kind === 'build' || s.kind === 'tm-output';
      let act = '';
      if (isM && active) act = '<button type="button" class="btn primary sm" data-bf-check="' + L0 + '">' + I.get('beaker', 15) + '<span>בדיקה</span></button>';
      else if (isM && (p.work(q.id, L0) || p.sectionDone(q.id, L0))) act = '<button type="button" class="btn secondary sm" data-bf-open="' + L0 + '">לסעיף ' + L0 + '\'</button>';
      else if (isM) act = '<button type="button" class="btn primary sm" data-bf-open="' + L0 + '">הכן סעיף ' + L0 + '\'</button>';
      if (s.kind === 'words') act = '<button type="button" class="btn primary sm" data-bf-check="' + L0 + '">' + I.get('beaker', 15) + '<span>בדיקה</span></button>';
      act += '<button type="button" class="btn secondary sm" data-bf-hint="' + L0 + '">💡 ' + (s.kind === 'explain' ? 'תשובה לדוגמה' : 'רמז') + '</button>';
      let extra = '';
      if (isM && !active && !p.work(q.id, L0) && !p.sectionDone(q.id, L0)) extra = '<p class="bf-note">' + esc(TYPE(B.machineType(s)).long) + ' חדש לסעיף הזה נפתח על הקנבס. המכונה של סעיף ' + this.label + '\' נשמרת, ואפשר לחזור אליה.</p>';
      if (s.kind === 'words') {
        const w = p.words(q.id, L0), field = (k, i, ph) => '<input type="text" dir="ltr" class="bf-word" data-bf-word="' + L0 + '" data-k="' + k + '" data-i="' + i + '" value="' + esc(w[k][i]) + '" placeholder="' + ph + '" spellcheck="false" autocomplete="off">';
        extra = '<div class="bf-words"><div><span class="bk-ex-t in">מתקבלות</span>' + [0, 1, 2].map((i) => field('in', i, 'מילה')).join('') + '</div><div><span class="bk-ex-t out">נדחות</span>' + [0, 1, 2].map((i) => field('out', i, 'מילה')).join('') + '</div><small>למילה הריקה כותבים ε</small></div>';
      }
      if (s.kind === 'explain') {
        extra = '<textarea class="bf-text" data-bf-text="' + L0 + '" rows="3" placeholder="כותבים כאן את ההסבר">' + esc(p.text(q.id, L0)) + '</textarea>' +
          '<label class="check"><input type="checkbox" data-bf-done="' + L0 + '"' + (p.sectionDone(q.id, L0) ? ' checked' : '') + '><span>סיימתי את הסעיף</span></label>';
      }
      const hint = this.hints.has(L0) ? '<div class="bf-hint"><div>' + rtl(s.kind === 'explain' ? s.answer : s.hint) + '</div>' + (isM ? '<button type="button" class="link" data-bf-solution="' + L0 + '">הצגת פתרון לדוגמה (בחלון נפרד)</button>' : '') + '</div>' : '';
      return '<section class="bk-card bf-sec' + (active ? ' active' : '') + '" id="bf-sec-' + L0 + '"><h3>סעיף ' + L0 + '\'' + V.vMark(p.sectionDone(q.id, L0), 'הסעיף נפתר') + (active && isM ? '<span class="bf-oncanvas">על הקנבס</span>' : '') + '</h3>' +
        V.sectionBody(q, s) + extra + '<div class="bf-acts">' + act + '</div>' + hint + this.resultHTML(s) + '</section>';
    }

    /* ---------- בדיקות ---------- */
    check(label) {
      const s = B.section(this.q, label), app = this.app;
      if (s.kind === 'words') {
        const w = this.progress.words(this.q.id, label), r = B.checkWords(this.q, s, w.in, w.out);
        this.results[label] = { kind: 'words', r };
        this.scrollTo = label;
        if (r.ok) this.passed(label);
        this.render();
        return;
      }
      const t = B.machineType(s);
      if (app.active !== t) { this.results[label] = { note: 'הבדיקה היא של ' + TYPE(t).long + ', ועכשיו פתוחה לשונית אחרת.', go: t }; this.render(); return; }
      // סעיף "בונים": אותה בדיקה ואותו כרטיס תוצאה כמו בפס המשימה שמעל הקנבס (סבב 14). כאן רק מסמנים V כשהיא עוברת
      if (s.kind === 'build') { delete this.results[label]; this.render(); app.taskUI.check(); return; }
      const m = app.machine;
      if (!m.states.length) { this.results[label] = { note: 'אין עדיין מכונה על הקנבס. לחיצה כפולה מוסיפה מצב.' }; this.render(); return; }
      const errs = app.issues.filter((i) => i.level === 'error');
      if (errs.length) { this.results[label] = { note: 'קודם צריך לתקן את המכונה: ' + errs[0].text, err: true }; this.render(); return; }
      const sig = app.machineSig();
      if (s.kind === 'tm-output') {
        this.results[label] = { note: 'בודק...', busy: true };
        this.render();
        setTimeout(() => { const r = B.checkTmOutput(m, this.q, s); this.results[label] = { kind: 'tm-output', r, sig }; this.scrollTo = label; if (r.ok) this.passed(label); this.render(); }, 20);
        return;
      }
    }
    passed(label) { this.progress.setSectionDone(this.q.id, label, true); this.app.toast('סעיף ' + label + '\' נפתר', 'ok'); }

    resultHTML(s) {
      const r = this.results[s.label];
      if (!r) return '';
      const box = (cls, icon, b, small) => '<div class="lr-box ' + cls + (small ? ' big' : '') + '">' + I.get(icon, 18) + '<span>' + (small ? '<b>' + b + '</b><small>' + small + '</small>' : b) + '</span></div>';
      if (r.note) return '<div class="bf-result">' + box(r.err ? 'err' : r.busy ? 'busy' : 'info', r.err ? 'warn' : 'info', esc(r.note)) + (r.go ? '<button type="button" class="btn secondary sm" data-bf-go="' + r.go + '">מעבר ל' + esc(TYPE(r.go).long) + '</button>' : '') + '</div>';
      if (r.kind === 'words') {
        return '<div class="bf-result">' + (r.r.ok ? box('ok', 'check', 'כל שש המילים נכונות') : box('err', 'x', 'יש מילים שצריך לתקן')) +
          '<ul class="bf-items">' + r.r.items.map((x) => '<li class="' + (x.ok ? 'ok' : 'err') + '">' + I.get(x.ok ? 'check' : 'x', 13) + '<bdi dir="ltr" class="bk-sym">' + (x.word === '' && x.why === 'חסרה מילה' ? '...' : word(x.word)) + '</bdi><span>' + (x.ok ? (x.want ? 'בשפה' : 'לא בשפה') : esc(x.why)) + '</span></li>').join('') + '</ul></div>';
      }
      if (r.sig !== this.app.machineSig() || s.label !== this.label) return '<div class="bf-result">' + box('info', 'info', 'המכונה השתנתה מאז הבדיקה. אפשר לבדוק שוב.') + '</div>';
      if (r.kind === 'tm-output') {
        const res = r.r;
        if (res.ok) return '<div class="bf-result">' + box('ok', 'check', 'הפלט נכון בכל ' + res.count + ' הקלטים שנבדקו', 'הקלטים: הדוגמאות, ועוד כל הקלטים החוקיים עד אורך 7. זו בדיקה, לא הוכחה.') + '</div>';
        return '<div class="bf-result">' + box('err', 'x', 'המכונה טועה ב-' + res.failing.length + ' מתוך ' + res.count + ' קלטים', 'לחיצה על קלט מריצה אותו על הקנבס.') +
          '<table class="bk-cases bf-fail"><thead><tr><th>קלט</th><th>צריך</th><th>התקבל</th></tr></thead><tbody>' + res.failing.slice(0, 6).map((f) =>
            '<tr><td dir="ltr"><button type="button" class="w-chip out" data-bf-run="' + esc(f.input) + '">' + word(f.input) + '</button></td><td dir="ltr">' + word(f.expected) + '</td><td>' + (f.got == null ? esc(f.why) : '<bdi dir="ltr">' + word(f.got) + '</bdi>') + '</td></tr>').join('') + '</tbody></table></div>';
      }
      return '';
    }

    /* ---------- "סיימתי" ---------- */
    confirmHTML() {
      const k = this.confirm.keep;
      return '<div class="bk-card bf-confirm"><h3>סיימתי את התרגיל</h3><p>השאלה תסומן בסימן ירוק. מה לעשות עם המכונה שעל הקנבס?</p>' +
        '<label class="bf-radio"><input type="radio" name="bf-keep" value="clean"' + (k ? '' : ' checked') + '><b>להתחיל נקי (מומלץ)</b></label>' +
        '<label class="bf-radio"><input type="radio" name="bf-keep" value="keep"' + (k ? ' checked' : '') + '><b>לשמור את המכונה</b></label>' +
        '<p class="muted small">' + (k ? 'המכונה נשארת על הקנבס כמו שהיא, ואפשר להמשיך לעבוד עליה. מ"מאגר שאלות" אפשר לפתוח אותה שוב ("פתח את התרגיל").' : 'הקנבס מתרוקן, והמכונות, המילים וההסברים של השאלה נמחקים מהדפדפן. אפשר לבטל עם Ctrl+Z.') + '</p>' +
        '<div class="bf-acts"><button type="button" class="btn primary sm" data-bf="finish-yes">' + (k ? 'סיום ושמירת המכונה' : 'סיום וניקוי') + '</button><button type="button" class="btn secondary sm" data-bf="finish-no">ביטול</button></div></div>';
    }
    finish() {
      const q = this.q, keep = this.confirm.keep, type = this.app.active;
      this.app.bank.remember(q.id);
      this.persist();
      this.confirm = null;
      this.progress.setDone(q.id, true);
      if (!keep) {
        this.progress.clearWork(q.id);
        this.loading = true;
        try { this.app.loadMachine(M.createMachine(type, { name: 'מכונה חדשה' }), 'סיום תרגיל מהמאגר', ''); } finally { this.loading = false; }
      }
      this.progress.setActive(null);
      this.q = null; this.label = null;
      this.hide();
      this.app.toast('השאלה "' + q.title + '" סומנה בסימן ירוק', 'ok');
    }

    /* ---------- אירועים ---------- */
    onInput(e) {
      const t = e.target;
      if (t.dataset.bfWord) { const L0 = t.dataset.bfWord, w = this.progress.words(this.q.id, L0); w[t.dataset.k][+t.dataset.i] = t.value; this.rendering = true; this.progress.setWords(this.q.id, L0, w); this.rendering = false; }
      if (t.dataset.bfText) { this.rendering = true; this.progress.setText(this.q.id, t.dataset.bfText, t.value); this.rendering = false; }
    }
    async onClick(e) {
      const app = this.app, d = (k) => { const x = e.target.closest('[data-' + k + ']'); return x && x.getAttribute('data-' + k); };
      let v;
      if ((v = d('bf-check'))) return this.check(v);
      if ((v = d('bf-open'))) { this.openSection(v); this.render(); return; }
      if ((v = d('bf-hint'))) { if (!this.hints.delete(v)) this.hints.add(v); this.render(); return; }
      if ((v = d('bf-jump'))) { const sec = this.el.querySelector('#bf-sec-' + v); if (sec) sec.scrollIntoView({ block: 'start', behavior: 'smooth' }); return; }
      if ((v = d('bf-run')) != null) { app.runWord(v); return; }
      if ((v = d('bf-go'))) { app.switchType(v); delete this.results[this.label]; this.render(); return; }
      if ((v = d('bf-solution'))) {          // הפתרון נפתח בחלון נפרד, והמכונה של התלמיד נשארת (כמו "הצגת פתרון" בפס המשימה)
        if (v !== this.label) this.openSection(v);
        app.taskUI.solution();
        return;
      }
      const b = d('bf');
      if (b === 'close') { this.persist(); this.hide(); }           // התרגיל נשאר: "השאלה" בפס המשימה פותח שוב
      else if (b === 'min') this.win.setMin(!this.el.classList.contains('min'));
      else if (b === 'bank') app.bank.show(this.q.id);
      else if (b === 'finish') { if (this.progress.done(this.q.id)) { this.progress.setDone(this.q.id, false); this.render(); } else { this.confirm = { keep: false }; this.render(); } }
      else if (b === 'finish-no') { this.confirm = null; this.render(); }
      else if (b === 'finish-yes') this.finish();
    }
  }

  root.BankFloat = BankFloat;
})(typeof globalThis !== 'undefined' ? globalThis : this);
