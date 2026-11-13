/*
 * task-ui.js - הפעולות של המשימה (סבב 14): בדיקה מול הנוסחה (הוכחה לאס"ד / אסל"ד ושפה רגולרית, אחרת בדיקה יסודית בחלקים: lang/check.js),
 * רמז, הצגת פתרון (בחלון נפרד, כך שהמכונה של התלמיד נשארת), בנייה מחדש, סיום המשימה, קובץ משימה לתלמידים,
 * וחלון "פתרון לדוגמה" (#solution=..., לצפייה בלבד: לא שומר כלום בדפדפן).
 * התוצאה נשמרת במכונה (AutomataTask.setCheck) ומוצגת רק בפס המשימה (lang-strip.js).
 */
(function (root) {
  'use strict';
  const L = root.AutomataLang, M = root.AutomataModel, T = root.AutomataTask, D = root.Dialogs, I = root.Icons;
  const esc = (s) => String(s == null ? '' : s).replace(/[&<>"]/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' })[c]);
  const enc = (s) => btoa(unescape(encodeURIComponent(s))).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
  const dec = (s) => decodeURIComponent(escape(atob(s.replace(/-/g, '+').replace(/_/g, '/'))));
  const chip = (w, cls) => '<span class="w-chip ' + cls + '">' + (w === '' ? 'ε' : esc(w)) + '</span>';

  class TaskUI {
    constructor(app) {
      this.app = app;
      this.listeners = [];
      this.run = null;
    }
    get strip() { return this.app.strip; }
    /** cb(res, machine): אחרי כל בדיקה שהסתיימה (חלון "השאלה" של המאגר מסמן ככה סעיף שנפתר) */
    onResult(cb) { this.listeners.push(cb); }
    ast(m) { const t = T.of(m); if (!t) return null; try { return L.parse(t.formula, { alphabet: M.inputAlphabet(m) }); } catch (e) { return null; } }

    /** בדיקה של המכונה שעל הקנבס מול הנוסחה. מחזיר Promise עם התוצאה (או null אם לא נבדק) */
    check() {
      const app = this.app, m = app.machine, task = T.of(m);
      if (!task) return Promise.resolve(null);
      const ast = this.ast(m);
      const note = (text, kind) => { this.strip.showCard({ text, kind: kind || 'info' }); return Promise.resolve(null); };
      if (!ast) return note('הנוסחה של המשימה לא נקראת. פותחים את "עריכת הנוסחה" ומתקנים.', 'err');
      if (!m.states.length) return note('אין עדיין מכונה על הקנבס. לחיצה כפולה מוסיפה מצב.');
      const errs = app.issues.filter((i) => i.level === 'error');
      if (errs.length) return note('קודם צריך לתקן את המכונה: ' + errs[0].text, 'err');
      this.run = null;
      return new Promise((resolve) => {
        const finish = (res) => {
          this.run = null;
          const c = T.setCheck(m, res);
          c.hint = L.hint(res, m);
          app.autosave();
          this.strip.showCard();
          app.toast(res.ok ? 'המכונה עברה את הבדיקה מול הנוסחה' : 'נמצאו מילים שהמכונה טועה בהן', res.ok ? 'ok' : 'err');
          this.listeners.forEach((f) => { try { f(res, m); } catch (e) { /* מאזין שנכשל */ } });
          resolve(res);
        };
        if (L.canExact(m, ast)) { try { finish(L.checkExact(m, ast)); return; } catch (e) { /* גדול מדי: בדיקה חסומה */ } }
        const c = L.checker(m, ast), t0 = performance.now(), run = { c };
        this.run = run;
        const tick = () => {
          if (this.run !== run || app.machine !== m) { resolve(null); return; }
          const t = performance.now();
          while (performance.now() - t < 30 && !c.step(10)) { /* עוד */ }
          // אחרי 5 שניות מסיימים את האורך הנוכחי ועוברים למילים הארוכות; אחרי 12 עוצרים עם מה שנבדק
          if (!c.result.done && !run.hurried && performance.now() - t0 > 5000) { run.hurried = true; c.hurry(); }
          if (!c.result.done && performance.now() - t0 > 12000) c.stop();
          if (c.result.done) finish(c.result);
          else { this.strip.showCard({ text: 'בודק... ' + (c.result.count + (c.result.long ? c.result.long.count : 0)).toLocaleString('he') + ' מילים', kind: 'busy', busy: true }); setTimeout(tick, 0); }
        };
        tick();
      });
    }

    /** "הוספה לטבלת הבדיקה": המילים שנכשלו, עם מה שהן אמורות לעשות */
    addTests() {
      const m = this.app.machine, c = m.language && m.language.check;
      if (!c || !c.words) return;
      const have = new Set(m.tests.map((t) => t.word));
      const add = c.words.shouldAccept.map((w) => ({ word: w, expect: true })).concat(c.words.shouldReject.map((w) => ({ word: w, expect: false }))).filter((t) => !have.has(t.word));
      this.app.change('הוספת בדיקות מהשפה', () => m.tests.push(...add), null, true);
      this.app.toast(add.length ? 'נוספו ' + add.length + ' מילים לטבלת הבדיקה' : 'המילים כבר בטבלה', 'ok');
    }

    /** רמז: לשפה רגולרית כמה מצבים יש באס"ד המינימלי; אחרת עוד מילה שהמכונה טועה בה */
    async hint() {
      const app = this.app, m = app.machine, ast = this.ast(m);
      if (!ast) return;
      const cls = L.analyze(ast).cls;
      this.hinted = this.hinted || new WeakSet();
      if (cls === 'regular' && !this.hinted.has(m)) {
        const r = L.build(ast);
        if (r.ok && r.kind === 'dfa') {
          this.hinted.add(m);
          const n = r.machine.states.length;
          this.strip.showCard({ text: 'רמז: באס"ד המינימלי של השפה יש ' + n + ' מצבים' + (r.machine.states.some((s) => /מלכודת/.test(s.name)) ? ' (כולל מצב מלכודת)' : '') + '.', kind: 'info' });
          return;
        }
      }
      if (T.status(m) !== 'fail') { const res = await this.check(); if (!res) return; if (res.ok) { this.strip.showCard({ text: 'אין מה לרמוז: המכונה כבר נכונה.', kind: 'ok' }); return; } }
      const c = m.language.check, all = (c.words.shouldAccept.map((w) => [w, true])).concat(c.words.shouldReject.map((w) => [w, false])).sort((a, b) => a[0].length - b[0].length);
      this.hintIdx = ((this.hintIdx || 0) % Math.max(1, all.length));
      const [w, want] = all[this.hintIdx++] || [];
      if (w == null) return;
      this.strip.showCard({ text: 'רמז: המילה ' + (w === '' ? 'ε' : w) + (want ? ' אמורה להתקבל, והמכונה דוחה אותה.' : ' אמורה להידחות, והמכונה מקבלת אותה.'), kind: 'info',
        html: '<div class="tc-groups"><div class="ll-words" dir="ltr"><button type="button" class="w-chip ' + (want ? 'in' : 'out') + '" data-word="' + esc(w) + '">' + (w === '' ? 'ε' : esc(w)) + '</button></div></div>' });
    }

    /** "בנייה מחדש" (התוכנה בונה): אותה נוסחה, מכונה חדשה במקום הנוכחית */
    rebuild() { this.app.langPanel.open('auto', { autoBuild: true }); }

    /** "סיום המשימה": המשימה יורדת, המכונה נשארת */
    finish() {
      const app = this.app;
      app.change('סיום המשימה', () => T.finish(app.machine), null, true);
      app.side.render();
      app.toast('המשימה הסתיימה. המכונה נשארה על הקנבס.');
    }

    /* ---------- פתרון לדוגמה ---------- */
    /** המכונה של הפתרון: במאגר הפתרון השמור של הסעיף, אחרת מכונה שנבנית מהנוסחה. null + הסבר אם אין */
    solutionMachine() {
      const app = this.app, m = app.machine, bf = app.bankFloat, B = root.AutomataBank;
      if (bf && bf.q && bf.isOpen()) { const s = B.section(bf.q, bf.label); if (s && s.reference) { const r = B.referenceMachine(bf.q, s); r.language = Object.assign({}, m.language, { mode: 'solution', locked: true }); delete r.language.check; return { machine: r }; } }
      const ast = this.ast(m);
      if (!ast) return { reason: 'הנוסחה לא נקראת.' };
      const r = L.build(ast);
      if (!r.ok) return { reason: r.reason, sample: L.sample(ast) };
      r.machine.language = { text: T.of(m).formula, mode: 'solution', locked: true, allowBuild: false };
      return { machine: r.machine };
    }
    async solution() {
      const ok = await D.confirm({ title: 'להציג פתרון לדוגמה?', html: 'הפתרון נפתח בחלון נפרד, לצפייה. המכונה שלך נשארת כאן כמו שהיא.', ok: 'הצגת הפתרון' });
      if (!ok) return null;
      const s = this.solutionMachine();
      if (!s.machine) {
        const words = s.sample ? '<div class="ll-row"><span class="ll-title in">בשפה</span><span class="ll-words" dir="ltr">' + s.sample.ins.map((w) => chip(w, 'in')).join('') + '</span></div>' +
          '<div class="ll-row"><span class="ll-title out">לא בשפה</span><span class="ll-words" dir="ltr">' + s.sample.outs.map((w) => chip(w, 'out')).join('') + '</span></div>' : '';
        this.strip.showCard({ text: 'אין פתרון מוכן לשפה הזו: ' + s.reason, kind: 'info', html: words });
        return null;
      }
      const url = location.href.split('#')[0] + '#solution=' + enc(M.serialize(s.machine));
      const w = window.open(url, '_blank');
      if (!w) this.app.toast('הדפדפן חסם את החלון החדש. מאשרים חלונות קופצים לדף הזה ומנסים שוב.', 'err');
      return w;
    }
    /** בטעינה: #solution=... -> המכונה לצפייה (או null) */
    static fromHash() {
      const h = /^#solution=([A-Za-z0-9_-]+)$/.exec(location.hash || '');
      if (!h) return null;
      try { return M.deserialize(dec(h[1])).machine; } catch (e) { return null; }
    }
    showViewBanner() {
      const el = document.createElement('div');
      el.className = 'view-banner';
      el.setAttribute('role', 'status');
      el.innerHTML = I.get('book', 16) + '<span><b>פתרון לדוגמה, לצפייה.</b> המכונה שלך נשארה בחלון הקודם, ושום שינוי כאן לא נשמר.</span><button type="button" class="btn secondary sm" data-v="close">סגירת החלון</button>';
      el.addEventListener('click', (e) => { if (e.target.closest('[data-v=close]')) window.close(); });
      document.querySelector('.stage-col').prepend(el);
    }

    /* ---------- קובץ משימה ---------- */
    saveTaskFile() {
      const app = this.app, m = app.machine, json = T.toFile(m);
      if (!json) { app.toast('אין משימה בלשונית הזו. כותבים נוסחה ("שפה פורמלית"), ואז שומרים.', 'err'); return; }
      const name = (this.ast(m) ? L.pretty(this.ast(m)).text : 'משימה').replace(/^L = /, '').replace(/[\\/:*?"<>|]+/g, '_').slice(0, 60);
      app.download(new Blob([json], { type: 'application/json' }), 'משימה - ' + name + '.task.json');
      app.toast('נשמר קובץ משימה (בלי המכונה)', 'ok');
    }
    /** פתיחת קובץ משימה: מכונה ריקה מהסוג הנכון, הנוסחה נעולה, ומילים לדוגמה בטבלת הבדיקה */
    openTaskFile(obj) {
      const app = this.app, f = T.fromFile(obj), m = M.createMachine(f.type, { name: 'משימה', alphabet: f.alphabet });
      m.language = f.language;
      const ast = this.ast(m);
      if (ast) { const s = L.sample(ast, { nIn: 3, nOut: 3 }); m.tests = s.ins.map((w) => ({ word: w, expect: true })).concat(s.outs.map((w) => ({ word: w, expect: false }))); if (!m.alphabet.length) m.alphabet = ast.alphabet.slice(); }
      app.loadMachine(m, 'פתיחת קובץ משימה', '');
      app.toast('נפתחה משימה: בונים ' + M.TYPE_INFO[f.type].short + ' לשפה, ובודקים', 'ok');
    }
  }

  root.TaskUI = TaskUI;
})(typeof globalThis !== 'undefined' ? globalThis : this);
