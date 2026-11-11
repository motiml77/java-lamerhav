/*
 * lang-summary.js - לשונית "שפה" בפאנל הצד (סבב 14): סיכום של המשימה של הלשונית (הנוסחה, הסיווג, ההסבר, מילים לדוגמה) עם "עריכה",
 * או, בלי משימה, שתי הדרכים להתחיל. העריכה עצמה בעורך (lang-panel.js). מרחיב את LangPanel.
 */
(function (root) {
  'use strict';
  const L = root.AutomataLang, M = root.AutomataModel, I = root.Icons;
  const esc = (s) => String(s == null ? '' : s).replace(/[&<>"]/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' })[c]);
  const wordChip = (w, cls) => '<button type="button" class="w-chip ' + (cls || '') + '" data-word="' + esc(w) + '" title="הרצה של המילה הזו">' + (w === '' ? 'ε' : esc(w)) + '</button>';
  const CLS = { regular: 'ok', cf: 'cf', cfany: 'cf', noncf: 'noncf', unknown: 'unk' };

  Object.assign(root.LangPanel.prototype, {
    /* ---------- לשונית "שפה" בפאנל הצד: סיכום של המשימה ---------- */
    summaryHTML() {
      const m = this.app.machine, T = root.AutomataTask, t = T.of(m);
      if (!t) {
        return '<section class="sec lang"><div class="lang-head"><h4>השפה</h4></div><p class="muted small">בלשונית הזו אין משימה: ציור חופשי. אפשר לכתוב נוסחה של שפה, ואז:</p>' +
          '<div class="ls-modes"><button type="button" class="btn secondary sm" data-ls="auto">' + I.get('transition', 16) + '<span>התוכנה בונה</span></button>' +
          '<button type="button" class="btn secondary sm" data-ls="practice">' + I.get('check', 16) + '<span>אני בונה, התוכנה בודקת</span></button></div></section>';
      }
      let ast = null, err = null;
      try { ast = L.parse(t.formula, { alphabet: M.inputAlphabet(m) }); } catch (e) { err = e; }
      let body = '';
      if (ast) {
        const an = L.analyze(ast), s = L.sample(ast);
        const none = '<span class="muted small">אין (עד האורך שנבדק)</span>';
        body = '<div class="lang-pretty"><div class="lp-math" dir="ltr">' + L.pretty(ast).html + '</div></div>' +
          '<p><span class="lang-class ' + CLS[an.cls] + '" title="' + esc(an.why) + '">' + esc(an.label) + '</span></p>' +
          (t.desc ? '<p class="ls-sum-desc">' + esc(t.desc) + '</p>' : '') +
          '<div class="lang-live"><div class="ll-row"><span class="ll-title in">בשפה</span><span class="ll-words" dir="ltr">' + (s.ins.map((w) => wordChip(w, 'in')).join('') || none) + '</span></div>' +
          '<div class="ll-row"><span class="ll-title out">לא בשפה</span><span class="ll-words" dir="ltr">' + (s.outs.map((w) => wordChip(w, 'out')).join('') || none) + '</span></div><p class="lang-why">' + esc(an.why) + '.</p></div>';
      } else body = '<p class="lr-box err">' + esc(err ? err.message : '') + '</p>';
      return '<section class="sec lang"><div class="lang-head"><h4>השפה</h4><span class="ts-chip ' + t.mode + '">' + T.CHIP[t.mode] + '</span></div>' + body +
        (t.locked ? '<p class="muted small">הנוסחה של המשימה נעולה.</p><button type="button" class="link" data-ls="unlock">ביטול הנעילה (למורים)</button>' :
          '<div class="lang-actions"><button type="button" class="btn secondary sm" data-ls="edit">' + I.get('edit', 16) + '<span>עריכה</span></button></div>') + '</section>' +
        (t.locked ? '' : '<section class="sec"><button type="button" class="btn secondary sm" data-ls="save-task">' + I.get('save', 16) + '<span>שמירת משימה לתלמידים</span></button><p class="muted small">קובץ עם הנוסחה, בלי המכונה. מי שפותח אותו מתחיל לבנות לבד.</p></section>');
    },
    mountSummary(body) {
      if (this.summaryBound === body) return;
      this.summaryBound = body;
      body.addEventListener('click', (e) => {
        const b = e.target.closest('[data-ls]');
        const w = e.target.closest('.lang [data-word]');
        if (w) { this.app.runWord(w.dataset.word); return; }
        if (!b) return;
        const k = b.dataset.ls, app = this.app;
        if (k === 'auto' || k === 'practice') this.open(k, { fresh: true });
        else if (k === 'edit') this.open();
        else if (k === 'save-task') app.taskUI.saveTaskFile();
        else if (k === 'unlock') { app.machine.language.locked = false; app.autosave(); app.refresh(); app.side.render(); }
      });
    }
  });
})(typeof globalThis !== 'undefined' ? globalThis : this);
