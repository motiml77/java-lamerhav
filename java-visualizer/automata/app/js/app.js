/*
 * app.js - הבקר: מצב האפליקציה, היסטוריה (ביטול/חזרה), שמירה אוטומטית, סרגל עליון, מקלדת והרצה.
 */
(function (root) {
  'use strict';
  const M = root.AutomataModel, S = root.AutomataSim, I = root.Icons, EX = root.AutomataExamples;
  const STORE_KEY = 'dsviz.automata.v1';
  const THEME_KEY = 'dsviz.automata.theme';
  const HISTORY_MAX = 100;
  const $ = (s, el) => (el || document).querySelector(s);
  const $$ = (s, el) => Array.from((el || document).querySelectorAll(s));
  const esc = (s) => String(s == null ? '' : s).replace(/[&<>"]/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' })[c]);

  const store = {
    get(k) { try { return localStorage.getItem(k); } catch (e) { return null; } },
    set(k, v) { try { localStorage.setItem(k, v); return true; } catch (e) { return false; } },
  };

  const app = {
    machines: {},          // type -> machine
    active: 'dfa',
    history: {},           // type -> { past: [{snap,label}], future: [...] }
    sel: { states: new Set(), trans: new Set() },
    tool: 'select',
    issues: [],
    run: null,             // { trace, index, playing, timer, error }
    runView: null,
    word: '',
    words: {},             // type -> המילה האחרונה
    speed: 3,

    get machine() { return this.machines[this.active]; },

    /* ---------- אתחול ---------- */
    init() {
      $$('[data-icon]').forEach((el) => { el.outerHTML = I.get(el.dataset.icon, el.classList.contains('chev') ? 14 : 18).replace('class="icon"', 'class="icon' + (el.classList.contains('chev') ? ' chev' : '') + '"'); });
      $('[data-cmd=undo]').innerHTML = I.get('undo');
      $('[data-cmd=redo]').innerHTML = I.get('redo');
      $('[data-cmd=help]').innerHTML = I.get('help');
      this.load();
      this.canvas = new root.CanvasView(this, $('#stage'));
      this.langPanel = new root.LangPanel(this);
      this.strip = new root.LangStrip(this, $('#lang-strip'));
      this.showTrap = store.get('dsviz.automata.trap') !== '0';
      $('#trap-toggle input').addEventListener('change', (e) => this.setShowTrap(e.target.checked));
      this.side = new root.SidePanel(this, $('.side'));
      this.runPanel = new root.RunPanel(this, $('.runpanel'));
      this.popover = new root.TransitionPopover(this, $('#tpop'));
      root.Guide.init(this);
      this.bindTop();
      this.bindKeys();
      this.renderExamplesMenu();
      this.switchType(this.active, true);
      requestAnimationFrame(() => { if (!this.machine.view.placed) { const g = root.Guide.el; this.canvas.fit(g.hidden || g.dataset.mode !== 'float' ? 0 : g.offsetWidth + 24); this.machine.view.placed = true; } });
    },

    load() {
      let data = null;
      try { data = JSON.parse(store.get(STORE_KEY) || 'null'); } catch (e) { data = null; }
      for (const type of M.TYPES) {
        let m = null;
        if (data && data.machines && data.machines[type]) m = M.deserialize(data.machines[type]).machine;
        if (!m) {
          const ex = EX.EXAMPLES.find((e) => e.type === type);
          m = M.deserialize(ex).machine;
          this.words[type] = ex.sampleWord || '';
        }
        this.machines[type] = m;
        this.history[type] = { past: [], future: [] };
      }
      if (data) {
        if (M.TYPES.includes(data.active)) this.active = data.active;
        if (data.words) Object.assign(this.words, data.words);
        if (data.speed) this.speed = data.speed;
        for (const type of M.TYPES) if (data.machines && data.machines[type] && data.machines[type].view) this.machines[type].view.placed = true;
      }
    },

    autosave() {
      clearTimeout(this.saveT);
      this.saveT = setTimeout(() => {
        this.words[this.active] = this.word;
        const ok = store.set(STORE_KEY, JSON.stringify({ v: 1, active: this.active, machines: this.machines, words: this.words, speed: this.speed }));
        if (!ok && !this.warnedStore) { this.warnedStore = true; this.toast('לא ניתן לשמור בדפדפן הזה. שמרו לקובץ כדי לא לאבד עבודה.', 'err'); }
      }, 250);
    },

    /* ---------- היסטוריה ---------- */
    snapshot() { const m = this.machine; const v = m.view; m.view = undefined; const s = JSON.stringify(m); m.view = v; return s; },
    machineSig() { const m = this.machine; return JSON.stringify([m.type, m.states.map((s) => [s.id, s.accepting]), m.start, m.transitions, m.alphabet, m.acceptBy, m.stackStart, m.blank]); },

    /**
     * שינוי במכונה: snapshot לפני, fn משנה, ואם משהו השתנה נכנס להיסטוריה.
     * snap: אם כבר צולם קודם (למשל בתחילת גרירה). keepRun: לא לעצור הרצה (שינויים בטבלת הבדיקות).
     */
    change(label, fn, snap, keepRun) {
      snap = snap || this.snapshot();
      if (fn) fn();
      const after = this.snapshot();
      if (after !== snap) {
        const h = this.history[this.active];
        h.past.push({ snap, label });
        if (h.past.length > HISTORY_MAX) h.past.shift();
        h.future = [];
      }
      if (!keepRun) this.stopRun(true);
      this.pruneSelection();
      this.refresh();
      this.autosave();
    },

    /** חזרה למצב שמור בלי רשומה בהיסטוריה (ביטול עריכה של מעבר חדש). */
    restore(snap) {
      if (!snap) return;
      const view = this.machine.view;
      this.machines[this.active] = Object.assign(JSON.parse(snap), { view });
      this.pruneSelection();
      this.refresh();
      this.autosave();
    },

    undo() { this.travel('past', 'future', 'בוטל'); },
    redo() { this.travel('future', 'past', 'שוחזר'); },
    travel(from, to, verb) {
      const h = this.history[this.active];
      if (!h[from].length) { this.toast(from === 'past' ? 'אין מה לבטל' : 'אין מה לשחזר'); return; }
      const item = h[from].pop();
      h[to].push({ snap: this.snapshot(), label: item.label });
      const view = this.machine.view;
      this.machines[this.active] = Object.assign(JSON.parse(item.snap), { view });
      this.stopRun(true);
      this.pruneSelection();
      this.refresh();
      this.autosave();
      this.toast(verb + ': ' + item.label);
    },

    /* ---------- בחירה ---------- */
    /** keepArm: לא לבטל מעבר שהתחיל (החץ המקווקו). */
    select(states, trans, keepArm) {
      const quiet = keepArm;
      this.sel.states = new Set(states || []);
      this.sel.trans = new Set(trans || []);
      if (!keepArm && this.canvas.source) { this.canvas.source = null; this.coach(); }
      if (this.side.tab !== 'props' && (states.length || trans.length) && !quiet && this.side.tab !== 'issues') this.side.tab = 'props';
      this.canvas.render();
      this.side.render();
      this.renderTop();
    },
    toggleSelect(kind, id) {
      const set = kind === 'state' ? this.sel.states : this.sel.trans;
      if (set.has(id)) set.delete(id); else set.add(id);
      this.select(Array.from(this.sel.states), Array.from(this.sel.trans));
    },
    pruneSelection() {
      const m = this.machine;
      this.sel.states = new Set(Array.from(this.sel.states).filter((id) => M.getState(m, id)));
      this.sel.trans = new Set(Array.from(this.sel.trans).filter((id) => M.getTransition(m, id)));
    },

    /* ---------- פעולות עריכה ---------- */
    addStateAt(p, opts) {
      const spot = opts && opts.exact ? p : this.canvas.freeSpot(p);
      let s;
      this.change('הוספת מצב', () => { s = M.addState(this.machine, spot); });
      this.select([s.id], []);
      const el = this.canvas.gStates.querySelector('[data-state="' + s.id + '"]');
      if (el) el.classList.add('pop');
      return s;
    },
    addStateCenter() { return this.addStateAt(this.canvas.centerWorld()); },

    /** מעבר מ-from אל to: אם כבר יש קשת כזו עורכים אותה, אחרת חלון צף למעבר חדש (נוצר רק באישור). */
    openTransition(from, to) {
      if (this.isRunning()) return;
      const t = M.findTransition(this.machine, from, to);
      this.canvas.closeEditor(true);
      this.popover.open(t ? { tid: t.id } : { from, to });
    },
    openTransitionEdge(tid) { if (!this.isRunning() && M.getTransition(this.machine, tid)) this.popover.open({ tid }); },

    stateAction(act, id) {
      const m = this.machine;
      if (!id || !M.getState(m, id)) return;
      if (act === 'start') this.change('מצב התחלתי', () => M.setStart(m, id));
      else if (act === 'accept') this.change('מצב מקבל', () => M.setAccepting(m, id));
      else if (act === 'loop') { this.canvas.source = null; this.hint(null); this.openTransition(id, id); }
      else if (act === 'connect') { this.select([id], [], true); this.canvas.arm(id); }
      else if (act === 'rename') this.canvas.openRenameEditor(id);
      else if (act === 'delete') this.confirmDelete([id], []);
    },

    deleteSelection() {
      const { states, trans } = this.sel;
      if (!states.size && !trans.size) { this.toast('לא נבחר כלום למחיקה'); return; }
      this.confirmDelete(Array.from(states), Array.from(trans));
    },

    /** שואל (חלון אישור) ומוחק. אחרי המחיקה אפשר לבטל עם Ctrl+Z. */
    async confirmDelete(states, trans) {
      const m = this.machine;
      const bdi = (x) => '<bdi class="sym" dir="ltr">' + esc(x) + '</bdi>';
      const edgeName = (t) => bdi(M.stateName(m, t.from) + ' → ' + M.stateName(m, t.to)) + ' ' + bdi('(' + M.formatLabels(m.type, t.labels).split('\n').join(' | ') + ')');
      const touching = m.transitions.filter((t) => states.includes(t.from) || states.includes(t.to));
      const extraTrans = trans.filter((id) => !touching.some((t) => t.id === id));
      let title, label;
      if (states.length === 1 && !extraTrans.length) {
        const n = touching.length;
        title = 'למחוק את המצב ' + bdi(M.stateName(m, states[0])) + (n === 0 ? '?' : n === 1 ? ' ואת המעבר שלו?' : ' ואת ' + n + ' המעברים שלו?');
        label = 'מחיקת מצב';
      } else if (!states.length && trans.length === 1) {
        const t = M.getTransition(m, trans[0]);
        if (!t) return;
        title = 'למחוק את המעבר ' + edgeName(t) + '?';
        label = 'מחיקת מעבר';
      } else {
        title = 'למחוק ' + (states.length ? states.length + ' מצבים' : '') + (states.length && trans.length ? ' ו-' : '') + (trans.length ? trans.length + ' מעברים' : '') + '?';
        label = 'מחיקה (' + (states.length + trans.length) + ')';
      }
      this.canvas.setHover(null);
      this.canvas.arm(null);
      const ok = await root.Dialogs.confirm({ title, html: 'אפשר לבטל אחר כך עם ' + root.Dialogs.kbd('Ctrl+Z') + '.', ok: 'מחיקה', danger: true });
      if (!ok) return;
      this.change(label, () => { M.removeTransitions(m, trans); M.removeStates(m, states); });
      this.select([], []);
      this.toast(label + '. Ctrl+Z מחזיר.');
    },

    completeTrap() {
      let trap = null;
      this.change('מצב מלכודת', () => { trap = M.completeWithTrap(this.machine); });
      if (trap) { this.select([trap.id], []); this.canvas.fit(); this.toast('נוסף מצב מלכודת, והאס"ד מלא עכשיו', 'ok'); }
      else this.toast('אין מעברים חסרים');
    },

    autoLayout() {
      if (!this.machine.states.length) return;
      const c = this.canvas.centerWorld();
      this.change('סידור אוטומטי', () => M.autoLayout(this.machine, { cx: c.x, cy: c.y }));
      this.canvas.fit();
      this.autosave();
    },

    setTool(t) {
      this.tool = t;
      this.canvas.source = null;
      $$('.rail [data-tool]').forEach((b) => b.setAttribute('aria-pressed', String(b.dataset.tool === t)));
      this.canvas.host.dataset.activeTool = t;
      this.hint(null);
    },

    /* ---------- סוג מכונה ---------- */
    switchType(type, initial) {
      if (!initial) { this.words[this.active] = this.word; this.stopRun(true); }
      this.active = type;
      this.word = this.words[type] || '';
      this.sel = { states: new Set(), trans: new Set() };
      this.canvas.source = null;
      this.hint(null);
      this.popover.close();
      this.canvas.closeEditor();
      $('#app').dataset.type = type;
      $$('#type-tabs [data-type]').forEach((b) => { const on = b.dataset.type === type; b.setAttribute('aria-selected', String(on)); b.tabIndex = on ? 0 : -1; });
      this.runPanel.testResults = null;
      this.langPanel.result = null;
      this.langPanel.busy = null;
      $('.tests-table-wrap').scrollTop = 0;
      this.refresh();
      if (!initial) { if (!this.machine.view.placed) { this.canvas.fit(); this.machine.view.placed = true; } this.autosave(); }
    },

    loadMachine(m, label, word) {
      const type = m.type;
      if (type !== this.active) this.switchType(type);
      const snap = this.snapshot();
      const view = this.machine.view;
      this.machines[type] = m;
      m.view = view;
      const h = this.history[type];
      h.past.push({ snap, label }); h.future = [];
      if (word != null) this.word = word;
      this.stopRun(true);
      this.sel = { states: new Set(), trans: new Set() };
      this.runPanel.testResults = null;
      this.langPanel.result = null;
      if (m.language && m.language.locked) this.side.tab = 'lang';      // קובץ תרגיל: מציגים את השפה
      $('.tests-table-wrap').scrollTop = 0;
      this.refresh();
      this.canvas.fit();
      this.autosave();
    },

    loadExample(id) {
      const ex = EX.get(id);
      if (!ex) return;
      const m = M.deserialize(ex).machine;
      this.loadMachine(m, 'טעינת דוגמה', ex.sampleWord || '');
      this.toast('נטענה הדוגמה "' + ex.name + '"');
    },

    loadFirstExample() { const ex = EX.EXAMPLES.find((e) => e.type === this.active); if (ex) this.loadExample(ex.id); },

    newMachine() {
      const type = this.active;
      const m = M.createMachine(type, { name: 'מכונה חדשה' });
      this.loadMachine(m, 'מכונה חדשה', '');
    },

    /* ---------- רענון ---------- */
    refresh() {
      this.issues = M.validate(this.machine);
      this.updateRunView();
      this.canvas.render();
      this.side.render();
      this.runPanel.render();
      this.renderTop();
      root.Guide.update();
      this.strip.render();
      this.renderTrapToggle();
      this.coach();
    },

    /** המצבים שלא מציירים: מצבי מלכודת, כשהאפשרות "הצג מצב מלכודת" כבויה. */
    hiddenStates() { return this.showTrap === false ? M.trapStates(this.machine) : new Set(); },
    /** תיבת הסימון "הצג מצב מלכודת": מופיעה רק כשיש במכונה מצב מלכודת. */
    renderTrapToggle() {
      const el = $('#trap-toggle');
      el.hidden = !M.trapStates(this.machine).size;
      el.querySelector('input').checked = this.showTrap !== false;
    },
    setShowTrap(on) {
      this.showTrap = on;
      store.set('dsviz.automata.trap', on ? '1' : '0');
      if (!on) { const t = M.trapStates(this.machine); this.sel.states.forEach((id) => { if (t.has(id)) this.sel.states.delete(id); }); this.sel.trans.forEach((id) => { const x = this.machine.transitions.find((y) => y.id === id); if (x && (t.has(x.to) || t.has(x.from))) this.sel.trans.delete(id); }); }
      this.canvas.render();
      this.renderTrapToggle();
    },

    /** מריץ מילה מההתחלה (לחיצה על מילה בפס השפה, בלשונית "שפה" או בתוצאת הבדיקה). */
    runWord(word) { this.word = word; this.stopRun(true); this.runPanel.render(); this.runCmd('play-restart'); },

    /** רמז קצר למעלה, לפי מה שחסר עכשיו (למי שבונה מכונה בפעם הראשונה). */
    coach() {
      if (!this.canvas || this.canvas.source || this.isRunning() || (this.popover && this.popover.isOpen())) return;
      const m = this.machine;
      let t = null;
      if (m.states.length === 1) t = 'עוד לחיצה כפולה במקום אחר על הקנבס מוסיפה מצב שני';
      else if (m.states.length >= 2 && !m.transitions.length) t = 'עכשיו מעבר: לוחצים על מצב, ואז על מצב אחר';
      else if (m.transitions.length && !m.states.some((s) => s.accepting) && !(m.type === 'pda' && m.acceptBy === 'empty')) t = 'אין מצב מקבל: לוחצים על מצב, ובסרגל השחור בוחרים "מקבל"';
      this.hint(t);
    },

    renderTop() {
      const m = this.machine;
      const name = $('#machine-name');
      if (document.activeElement !== name) name.value = m.name;
      const h = this.history[this.active];
      $('[data-cmd=undo]').disabled = !h.past.length;
      $('[data-cmd=redo]').disabled = !h.future.length;
      $('[data-cmd=undo]').title = h.past.length ? 'ביטול: ' + h.past[h.past.length - 1].label + ' (Ctrl+Z)' : 'ביטול (Ctrl+Z)';
      $('[data-cmd=redo]').title = h.future.length ? 'חזרה: ' + h.future[h.future.length - 1].label + ' (Ctrl+Shift+Z)' : 'חזרה (Ctrl+Shift+Z)';
      $('.rail [data-cmd=delete]').disabled = !(this.sel.states.size || this.sel.trans.size);
      $('.rail [data-cmd=layout]').disabled = !m.states.length;
      const dark = document.documentElement.dataset.theme === 'dark';
      const tb = $('[data-cmd=theme]');
      tb.innerHTML = I.get(dark ? 'sun' : 'moon');
      tb.title = dark ? 'מצב בהיר' : 'מצב כהה';
      $$('.rail [data-tool]').forEach((b) => b.setAttribute('aria-pressed', String(b.dataset.tool === this.tool)));
      // תג תקינות בפינת הקנבס
      const errs = this.issues.filter((i) => i.level === 'error').length;
      const warns = this.issues.length - errs;
      const cs = $('#canvas-status');
      if (!m.states.length) { cs.hidden = true; }
      else {
        cs.hidden = false;
        cs.className = 'canvas-status ' + (errs ? 'err' : warns ? 'warn' : 'ok');
        cs.innerHTML = I.get(errs ? 'x' : warns ? 'warn' : 'check', 15) + '<span>' + (errs ? (errs === 1 ? 'שגיאה אחת' : errs + ' שגיאות') : warns ? (warns === 1 ? 'אזהרה אחת' : warns + ' אזהרות') : 'המכונה תקינה') + '</span>';
        cs.title = errs || warns ? 'לחיצה פותחת את רשימת הבעיות' : '';
      }
      this.onViewChange();
    },
    onViewChange() { const z = $('#zoom-val'); if (z && this.machine) z.textContent = Math.round(this.machine.view.zoom * 100) + '%'; },

    /* ---------- הודעות ---------- */
    toast(text, kind) {
      const box = $('#toasts');
      const el = document.createElement('div');
      el.className = 'toast ' + (kind || '');
      el.innerHTML = (kind ? I.get(kind === 'ok' ? 'check' : 'warn', 16) : '') + '<span>' + esc(text) + '</span>';
      box.appendChild(el);
      while (box.children.length > 3) box.firstChild.remove();
      setTimeout(() => { el.classList.add('out'); setTimeout(() => el.remove(), 250); }, kind === 'err' ? 4200 : 2400);
    },
    hint(text) {
      const h = $('#hint');
      if (!text) { h.hidden = true; return; }
      h.hidden = false;
      h.textContent = text;
    },

  };

  root.App = app;
})(typeof globalThis !== 'undefined' ? globalThis : this);
