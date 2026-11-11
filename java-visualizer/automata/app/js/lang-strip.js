/*
 * lang-strip.js - פס המשימה שמעל הקנבס (סבב 14): המקום היחיד שמראה את מצב המשימה. מימין לשמאל: תג המצב (נבנה מהנוסחה / משימה),
 * הנוסחה בכתיבה יפה, סטטוס (עוד לא נבדק / נכון / לא נכון / השתנה מאז הבדיקה), והפעולה הראשית. מתחת: התיאור ומילים להרצה,
 * וכרטיס התוצאה של הבדיקה (נסגר; לחיצה על הסטטוס פותחת אותו שוב). בלי משימה (ציור חופשי) אין פס.
 * הפעולות עצמן ב-task-ui.js (בדיקה, רמז, פתרון, סיום) ו-lang-panel.js (עורך הנוסחה). אפשר לקפל לשורה דקה (ההעדפה נשמרת).
 */
(function (root) {
  'use strict';
  const L = root.AutomataLang, M = root.AutomataModel, I = root.Icons, T = root.AutomataTask;
  const KEY = 'dsviz.automata.strip';
  const esc = (s) => String(s == null ? '' : s).replace(/[&<>"]/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' })[c]);
  const store = {
    get(k) { try { return localStorage.getItem(k); } catch (e) { return null; } },
    set(k, v) { try { localStorage.setItem(k, v); } catch (e) { /* אין אחסון: ההעדפה לא נשמרת */ } },
  };
  const chip = (w, cls, title) => '<button type="button" class="w-chip ' + cls + '" data-word="' + esc(w) + '" title="' + esc(title) + '">' + (w === '' ? 'ε' : esc(w)) + '</button>';

  class LangStrip {
    constructor(app, el) {
      this.app = app;
      this.el = el;
      this.collapsed = store.get(KEY) === '1';
      this.sig = null;
      this.card = { open: false, note: null };     // note: { text, kind, busy } (הודעה במקום תוצאה, למשל "בודק...")
      el.addEventListener('click', (e) => this.onClick(e));
    }

    /** המילים להרצה מהירה: [{ word, inLang }], עד 9. */
    words(ast) {
      const m = this.app.machine;
      let list = Array.from(new Set(m.tests.map((t) => t.word)));
      if (!list.length) { const s = L.sample(ast, { nIn: 4, nOut: 3 }); list = s.ins.concat(s.outs); }
      return list.slice(0, 9).map((word) => ({ word, inLang: L.accepts(ast, word) }));
    }
    showCard(note) { this.card = { open: true, note: note || null }; this.render(); }
    hideCard() { this.card = { open: false, note: null }; this.render(); }

    render() {
      const app = this.app, m = app.machine, task = T.of(m), el = this.el;
      let ast = null;
      if (task) { try { ast = L.parse(task.formula, { alphabet: M.inputAlphabet(m) }); } catch (e) { ast = null; } }
      const words = ast ? this.words(ast) : [];
      const st = task ? T.status(m) : 'none', pill = task ? T.pill(m) : null;
      const bank = !!(app.bankFloat && app.bankFloat.q), qOpen = bank && app.bankFloat.isOpen();   // תרגיל מהמאגר (החלון "השאלה" פתוח או סגור)
      const sig = task ? JSON.stringify([task, st, this.collapsed, words, this.card, !!ast, bank, qOpen, m.type]) : '';
      const same = this.machine === m;
      this.machine = m;
      if (sig === this.sig) return;
      const first = this.sig === null, before = el.hidden ? 0 : el.offsetHeight;
      this.sig = sig;
      el.hidden = !task;
      el.classList.toggle('collapsed', this.collapsed);
      if (task) {
        const f = ast ? L.pretty(ast).html : esc(task.formula);
        const mode = task.mode, typeName = M.TYPE_INFO[m.type].short;
        const lead = mode === 'practice' ? '<span class="ts-lead">בנו ' + esc(typeName) + ' לשפה</span>' : '';
        const checkBtn = '<button type="button" class="btn primary sm" data-t="check" title="בדיקה (Ctrl+Enter)">' + I.get('check', 15) + '<span>בדיקה</span></button>';
        let acts = '';
        if (mode === 'practice' || mode === 'solution') acts = checkBtn;
        else {
          if (st !== 'ok') acts += checkBtn;
          acts += '<button type="button" class="btn ghost sm" data-t="edit">' + I.get('edit', 15) + '<span>עריכת הנוסחה</span></button>' +
            '<button type="button" class="btn ghost sm" data-t="rebuild">' + I.get('transition', 15) + '<span>בנייה מחדש</span></button>';
        }
        if (bank && !qOpen) acts = '<button type="button" class="btn secondary sm" data-t="question" title="פתיחה של חלון השאלה">' + I.get('bank', 15) + '<span>השאלה</span></button>' + acts;
        if (mode === 'practice') {
          const items = [['hint', 'help', 'רמז', 'עוד מילה שנכשלת, או כמה מצבים צריך'], ['solution', 'book', 'הצגת פתרון', 'נפתח בחלון נפרד. המכונה שלך נשארת']];
          if (!task.locked) items.push(['edit', 'edit', 'עריכת הנוסחה', '']);
          if (!bank) items.push(['finish', 'x', 'סיום המשימה', 'המשימה יורדת, המכונה נשארת']);
          acts += '<div class="menu-wrap"><button type="button" class="btn icon ghost sm" data-t="more" aria-haspopup="menu" aria-expanded="false" aria-label="עוד פעולות" title="עוד פעולות">' + I.get('more', 16) + '</button>' +
            '<div class="menu ts-menu" id="ts-menu" role="menu" hidden>' + items.map(([k, ic, t, s]) => '<button type="button" role="menuitem" data-t="' + k + '">' + I.get(ic, 18) + '<span class="mi-text"><b>' + t + '</b>' + (s ? '<small>' + s + '</small>' : '') + '</span></button>').join('') + '</div></div>';
        }
        const group = (inLang) => {
          const list = words.filter((w) => w.inLang === inLang);
          if (!list.length) return '';
          const what = inLang ? 'בשפה' : 'לא בשפה';
          return '<span class="ls-group ' + (inLang ? 'in' : 'out') + '" role="group" aria-label="מילים ש' + (inLang ? '' : 'אינן ') + 'בשפה" title="' + what + '">' + I.get(inLang ? 'check' : 'x', 14) +
            list.map((w) => chip(w.word, inLang ? 'in' : 'out', what + '. לחיצה מריצה את המילה')).join('') + '</span>';
        };
        const busy = this.card.note && this.card.note.busy;
        el.innerHTML = '<div class="ls-main"><span class="ts-chip ' + mode + '">' + T.CHIP[mode] + '</span>' +
          '<button type="button" class="ls-formula" data-s="open" title="לחיצה פותחת את לשונית השפה">' + lead + '<bdi class="ls-f" dir="ltr" id="ls-formula">' + f + '</bdi></button>' +
          '<button type="button" class="ts-pill ' + (busy ? 'busy' : pill.cls) + '" data-t="card" id="ts-pill" title="תוצאת הבדיקה">' + esc(busy ? this.card.note.text : pill.text) + '</button>' +
          '<span class="ts-acts">' + acts + '</span>' +
          '<button type="button" class="btn icon ghost sm ls-toggle" data-s="toggle" aria-expanded="' + !this.collapsed + '" aria-controls="ls-sub" title="' + (this.collapsed ? 'הצגת התיאור והמילים' : 'קיפול הפס') + '" aria-label="' + (this.collapsed ? 'הצגת התיאור והמילים' : 'קיפול הפס') + '">' + I.get('chevronDown', 16) + '</button></div>' +
          '<div class="ls-sub" id="ls-sub">' + (task.desc ? '<p class="ls-desc">' + esc(task.desc) + '</p>' : '') +
          (words.length ? '<div class="ls-words"><span class="ls-wlabel">מילים להרצה</span><span class="ls-chips" dir="ltr">' + group(true) + group(false) + '</span></div>' : '') + '</div>' +
          this.cardHTML(m, st);
      } else { el.innerHTML = ''; this.card = { open: false, note: null }; }
      // הפס שינה גובה באותה מכונה: הקנבס קטן או גדל, ומזיזים את התצוגה כדי שהשרטוט יישאר במרכזו
      const after = el.hidden ? 0 : el.offsetHeight;
      if (!first && same && after !== before && app.canvas) { m.view.y -= (after - before) / 2; app.canvas.applyView(); }
    }

    /** כרטיס התוצאה מתחת לפס: מה נבדק, או שתי קבוצות של מילים שנכשלו (עד 5 בכל אחת) */
    cardHTML(m, st) {
      if (!this.card.open) return '';
      const n = (x) => (x == null ? '' : x.toLocaleString('he'));
      const wrap = (cls, body) => '<div class="task-card ' + cls + '" id="task-card" role="status"><button type="button" class="pop-x" data-t="close-card" aria-label="סגירה">' + I.get('x', 14) + '</button>' + body + '</div>';
      const note = this.card.note;
      if (note) return wrap(note.kind || 'info', '<div class="tc-head">' + I.get(note.kind === 'err' ? 'warn' : 'info', 16) + '<b>' + esc(note.text) + '</b></div>' + (note.html || ''));
      const c = (m.language && m.language.check) || {};
      if (st === 'none') return wrap('info', '<div class="tc-head">' + I.get('info', 16) + '<b>עוד לא נבדק. "בדיקה" משווה את המכונה לנוסחה.</b></div>');
      if (st === 'stale') return wrap('stale', '<div class="tc-head">' + I.get('warn', 16) + '<b>המכונה השתנתה מאז הבדיקה. אפשר לבדוק שוב.</b></div>');
      if (st === 'ok') {
        return wrap('ok', '<div class="tc-head">' + I.get('check', 16) + '<b>' + (c.kind === 'proof' ? 'המכונה מקבלת בדיוק את השפה' : 'נבדקו ' + (c.checkedUpTo > 0 ? 'כל ' : '') + n(c.count) + ' המילים עד אורך ' + c.checkedUpTo + '. לא נמצאה טעות') + '</b></div>' +
          '<p class="tc-sub">' + (c.kind === 'proof' ? 'זו הוכחה: המכונה הושוותה במלואה לאס"ד המינימלי של השפה.' : 'זו בדיקה, לא הוכחה: ייתכן שיש טעות במילה ארוכה יותר.') + (c.undecided ? ' ב-' + n(c.undecided) + ' מילים ההרצה לא הוכרעה.' : '') + '</p>');
      }
      const w = c.words || { shouldAccept: [], shouldReject: [] };
      const list = (title, ws, cls) => (ws.length ? '<div class="tc-group"><div class="lr-title ' + cls + '">' + title + '</div><div class="ll-words" dir="ltr">' + ws.map((x) => chip(x, cls, 'לחיצה מריצה את המילה על הקנבס')).join('') + '</div></div>' : '');
      return wrap('fail', '<div class="tc-head">' + I.get('x', 16) + '<b>לא נכון: ' + T.words(c.total) + '</b></div>' + (c.hint ? '<p class="tc-sub">' + esc(c.hint) + '</p>' : '') +
        '<div class="tc-groups">' + list('אמורות להתקבל, ונדחו', w.shouldAccept, 'in') + list('אמורות להידחות, והתקבלו', w.shouldReject, 'out') + '</div>' +
        '<div class="tc-foot"><span class="muted small">' + (c.kind === 'proof' ? 'אלה המילים הקצרות ביותר.' : 'נבדק עד אורך ' + c.checkedUpTo + '.') + ' לחיצה על מילה מריצה אותה.</span>' +
        '<button type="button" class="btn secondary sm" data-t="add-tests">' + I.get('beaker', 15) + '<span>הוספה לטבלת הבדיקה</span></button></div>');
    }

    onClick(e) {
      const app = this.app, ui = app.taskUI;
      const w = e.target.closest('[data-word]');
      if (w) { app.runWord(w.dataset.word); return; }
      const t = e.target.closest('[data-t]');
      if (t) {
        const k = t.dataset.t;
        if (k === 'more') { app.toggleMenu('ts-menu', t); return; }
        if (t.closest('#ts-menu')) app.toggleMenu('ts-menu', null, false);
        if (k === 'check') ui.check();
        else if (k === 'card') { if (this.card.open && !this.card.note) this.hideCard(); else this.showCard(); }
        else if (k === 'close-card') this.hideCard();
        else if (k === 'edit') app.langPanel.open();
        else if (k === 'rebuild') ui.rebuild();
        else if (k === 'hint') ui.hint();
        else if (k === 'solution') ui.solution();
        else if (k === 'finish') ui.finish();
        else if (k === 'add-tests') ui.addTests();
        else if (k === 'question') app.bankFloat.show();
        return;
      }
      const b = e.target.closest('[data-s]');
      if (!b) return;
      if (b.dataset.s === 'open') app.side.show('lang');
      else if (b.dataset.s === 'toggle') {
        this.collapsed = !this.collapsed;
        store.set(KEY, this.collapsed ? '1' : '0');
        this.render();
        const tg = this.el.querySelector('.ls-toggle');
        if (tg) tg.focus();
      }
    }
  }

  root.LangStrip = LangStrip;
})(window);
