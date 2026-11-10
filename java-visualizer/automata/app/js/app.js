/*
 * app.js - הבקר: מצב האפליקציה, היסטוריה (ביטול/חזרה), שמירה אוטומטית, סרגל עליון, מקלדת והרצה.
 */
(function (root) {
  'use strict';
  const M = root.AutomataModel, S = root.AutomataSim, I = root.Icons, EX = root.AutomataExamples;
  const STORE_KEY = 'dsviz.automata.v1';
  const THEME_KEY = 'dsviz.automata.theme';
  const SPEEDS = [0, 1400, 900, 600, 340, 160];   // אלפיות שנייה בין צעדים, לפי המהירות 1..5
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
      this.side = new root.SidePanel(this, $('.side'));
      this.runPanel = new root.RunPanel(this, $('.runpanel'));
      this.bindTop();
      this.bindKeys();
      this.renderExamplesMenu();
      this.switchType(this.active, true);
      requestAnimationFrame(() => { if (!this.machine.view.placed) { this.canvas.fit(); this.machine.view.placed = true; } });
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
    select(states, trans, quiet) {
      this.sel.states = new Set(states || []);
      this.sel.trans = new Set(trans || []);
      if (!quiet) this.canvas.pendingFrom = null;
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
      const spot = opts && opts.exact ? p : this.canvas.freeSpot({ x: Math.round(p.x / 10) * 10, y: Math.round(p.y / 10) * 10 });
      let s;
      this.change('הוספת מצב', () => { s = M.addState(this.machine, spot); });
      this.select([s.id], []);
      const el = this.canvas.gStates.querySelector('[data-state="' + s.id + '"]');
      if (el) el.classList.add('pop');
      return s;
    },
    addStateCenter() { return this.addStateAt(this.canvas.centerWorld()); },

    connect(from, to) {
      const snap = this.snapshot();
      const existing = M.findTransition(this.machine, from, to);
      let t;
      if (existing) t = existing;
      else { t = M.addTransition(this.machine, from, to, []); }
      this.stopRun(true);
      this.sel.states = new Set(); this.sel.trans = new Set([t.id]);
      this.refresh();
      this.canvas.openLabelEditor(t.id, { snapshot: snap, isNew: !existing });
    },

    stateAction(act, id) {
      const m = this.machine;
      if (!id || !M.getState(m, id)) return;
      if (act === 'start') this.change('מצב התחלתי', () => M.setStart(m, id));
      else if (act === 'accept') this.change('מצב מקבל', () => M.setAccepting(m, id));
      else if (act === 'loop') this.connect(id, id);
      else if (act === 'connect') { this.canvas.pendingFrom = id; this.hint('לחצו על מצב היעד (או על אותו מצב, ללולאה). Esc לביטול.'); this.canvas.render(); }
      else if (act === 'rename') this.canvas.openRenameEditor(id);
      else if (act === 'delete') { this.select([id], []); this.deleteSelection(); }
    },

    deleteSelection() {
      const { states, trans } = this.sel;
      if (!states.size && !trans.size) { this.toast('לא נבחר כלום למחיקה'); return; }
      const n = states.size + trans.size;
      this.change(n > 1 ? 'מחיקה (' + n + ')' : states.size ? 'מחיקת מצב' : 'מחיקת מעבר', () => {
        M.removeTransitions(this.machine, Array.from(trans));
        M.removeStates(this.machine, Array.from(states));
      });
      this.select([], []);
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
      this.canvas.pendingFrom = null;
      $$('.rail [data-tool]').forEach((b) => b.setAttribute('aria-pressed', String(b.dataset.tool === t)));
      this.canvas.host.dataset.tool = t;
      if (t === 'transition') this.hint('לחצו על מצב המקור, ואז על מצב היעד');
      else if (t === 'state') this.hint('לחיצה על הקנבס מוסיפה מצב');
      else this.hint(null);
    },

    /* ---------- סוג מכונה ---------- */
    switchType(type, initial) {
      if (!initial) { this.words[this.active] = this.word; this.stopRun(true); }
      this.active = type;
      this.word = this.words[type] || '';
      this.sel = { states: new Set(), trans: new Set() };
      this.canvas.pendingFrom = null;
      this.canvas.closeEditor();
      $('#app').dataset.type = type;
      $$('#type-tabs [data-type]').forEach((b) => { const on = b.dataset.type === type; b.setAttribute('aria-selected', String(on)); b.tabIndex = on ? 0 : -1; });
      this.runPanel.testResults = null;
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

    /* ---------- הרצה ---------- */
    isRunning() { return !!(this.run && this.run.trace); },

    startRun() {
      const m = this.machine;
      const errs = this.issues.filter((i) => i.level === 'error');
      if (errs.length) {
        this.run = { trace: null, index: 0, error: { title: 'יש לתקן את המכונה', text: errs[0].text + (errs.length > 1 ? ' (ועוד ' + (errs.length - 1) + ')' : '') } };
        this.side.show('issues');
        this.select(errs[0].states, errs[0].transitions, true);
        this.runPanel.render();
        return false;
      }
      const bad = M.checkWord(m, this.word);
      if (bad) {
        this.run = { trace: null, index: 0, error: { title: 'אות לא באלפבית', text: bad.text, index: bad.index } };
        this.runPanel.render();
        return false;
      }
      const trace = S.run(m, this.word);
      this.run = { trace, index: 0, playing: false };
      this.sel = { states: new Set(), trans: new Set() };
      this.canvas.closeEditor();
      return true;
    },

    stopRun(silent) {
      if (this.run && this.run.timer) clearTimeout(this.run.timer);
      const had = !!this.run;
      this.run = null;
      this.runView = null;
      if (had && !silent) this.refresh();
    },

    goto(i) {
      const r = this.run;
      if (!r || !r.trace) return;
      const last = r.trace.steps.length - 1;
      const ni = Math.max(0, Math.min(last, i));
      const forward = ni === r.index + 1;
      r.index = ni;
      this.updateRunView(forward);
      this.canvas.render();
      this.runPanel.render();
      this.canvas.positionToolbar();
    },

    updateRunView(animate) {
      const r = this.run;
      if (!r || !r.trace) { this.runView = null; return; }
      const tr = r.trace, st = tr.steps[r.index];
      const last = r.index === tr.steps.length - 1;
      const interval = SPEEDS[this.speed] || 600;
      this.runView = {
        active: st ? st.active : [],
        edges: st && r.index > 0 ? st.edges : (st ? st.edges : []),
        accepted: last && tr.result === 'accept' ? tr.acceptedStates : [],
        rejected: last && tr.result !== 'accept' ? (st ? st.active : []) : [],
        animate: !!animate,
        travelMs: Math.min(420, Math.round(interval * 0.7)),
      };
    },

    runCmd(cmd) {
      const r = this.run;
      if (cmd === 'play-restart') { this.stopRun(true); if (this.startRun()) { this.goto(0); this.play(); } return; }
      if (cmd === 'play') {
        if (r && r.playing) { this.pause(); return; }
        if (!r || !r.trace) { if (!this.startRun()) return; this.goto(0); }
        else if (r.index >= r.trace.steps.length - 1) this.goto(0);
        this.play();
        return;
      }
      if (cmd === 'next') { if (!r || !r.trace) { if (this.startRun()) this.goto(0); return; } this.pause(); this.goto(r.index + 1); return; }
      if (cmd === 'end') { if (!r || !r.trace) { if (!this.startRun()) return; } this.pause(); this.goto(this.run.trace.steps.length - 1); return; }
      if (!r || !r.trace) return;
      if (cmd === 'prev') { this.pause(); this.goto(r.index - 1); }
      else if (cmd === 'rewind') { this.pause(); this.goto(0); }
    },

    play() {
      const r = this.run;
      if (!r || !r.trace) return;
      r.playing = true;
      this.runPanel.render();
      const tick = () => {
        if (!this.run || !this.run.playing) return;
        if (this.run.index >= this.run.trace.steps.length - 1) { this.run.playing = false; this.runPanel.render(); return; }
        this.goto(this.run.index + 1);
        this.run.timer = setTimeout(tick, SPEEDS[this.speed] || 600);
      };
      r.timer = setTimeout(tick, Math.min(500, SPEEDS[this.speed] || 600));
    },
    pause() { const r = this.run; if (r && r.playing) { r.playing = false; clearTimeout(r.timer); this.runPanel.render(); } },

    /* ---------- קבצים ---------- */
    saveFile() {
      const m = this.machine;
      const blob = new Blob([M.serialize(m)], { type: 'application/json' });
      const name = (m.name || 'מכונה').replace(/[\\/:*?"<>|]+/g, '_') + '.automaton.json';
      this.download(blob, name);
      this.toast('נשמר: ' + name, 'ok');
    },
    exportSVG() {
      const svg = this.canvas.exportSVG();
      if (!svg) { this.toast('אין מה לייצא: הקנבס ריק'); return; }
      const name = (this.machine.name || 'מכונה').replace(/[\\/:*?"<>|]+/g, '_') + '.svg';
      this.download(new Blob([svg], { type: 'image/svg+xml' }), name);
      this.toast('יוצא: ' + name, 'ok');
    },
    download(blob, name) {
      const a = document.createElement('a');
      a.href = URL.createObjectURL(blob);
      a.download = name;
      document.body.appendChild(a);
      a.click();
      setTimeout(() => { URL.revokeObjectURL(a.href); a.remove(); }, 1000);
    },
    openFile(file) {
      const fr = new FileReader();
      fr.onload = () => {
        const { machine, errors } = M.deserialize(String(fr.result));
        if (!machine) { this.toast('לא ניתן לפתוח: ' + errors[0], 'err'); return; }
        this.loadMachine(machine, 'פתיחת קובץ');
        this.toast(errors.length ? 'נפתח, עם ' + errors.length + ' פריטים שדולגו' : 'נפתח: ' + machine.name, errors.length ? 'err' : 'ok');
      };
      fr.onerror = () => this.toast('לא ניתן לקרוא את הקובץ', 'err');
      fr.readAsText(file);
    },

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

    /* ---------- סרגל עליון ותפריטים ---------- */
    renderExamplesMenu() {
      const menu = $('#examples-menu');
      menu.innerHTML = M.TYPES.map((type) => {
        const list = EX.EXAMPLES.filter((e) => e.type === type);
        if (!list.length) return '';
        return '<div class="menu-group"><div class="menu-title">' + esc(M.TYPE_INFO[type].short) + '</div>' + list.map((e) =>
          '<button type="button" role="menuitem" data-example="' + e.id + '"><span class="type-tag ' + type + '">' + M.TYPE_INFO[type].en + '</span><span class="mi-text"><b>' + esc(e.name) + '</b><small>' + esc(e.description) + '</small></span></button>').join('') + '</div>';
      }).join('');
    },

    toggleMenu(id, btn, force) {
      const menu = $('#' + id);
      const open = force != null ? force : menu.hidden;
      $$('.menu').forEach((x) => { x.hidden = true; });
      $$('[aria-haspopup]').forEach((b) => b.setAttribute('aria-expanded', 'false'));
      menu.hidden = !open;
      if (btn) btn.setAttribute('aria-expanded', String(open));
      if (open) { const first = menu.querySelector('[role=menuitem]'); if (first) first.focus(); }
    },

    showModal(title, html) {
      $('#modal-title').textContent = title;
      $('#modal-body').innerHTML = html;
      $('#modal').hidden = false;
      $('#modal [data-cmd=close-modal]').focus();
    },
    closeModal() { $('#modal').hidden = true; },

    helpHTML() {
      const rows = [
        ['עריכה', [['לחיצה כפולה על הקנבס', 'מצב חדש'], ['גרירה מהשפה של מצב אל מצב', 'מעבר (שחרור על אותו מצב: לולאה)'], ['לחיצה כפולה על מצב / על תווית', 'שינוי שם / עריכת סמלים'], ['A', 'מצב מקבל (הפעלה/כיבוי)'], ['I', 'מצב התחלתי'], ['F2 או Enter', 'שינוי שם / עריכת המעבר הנבחר'], ['Delete', 'מחיקת הבחירה'], ['Ctrl+Z / Ctrl+Shift+Z', 'ביטול / חזרה'], ['Ctrl+A', 'בחירת כל המצבים'], ['חצים', 'הזזת המצבים הנבחרים (Shift: צעד גדול)']]],
        ['כלים ותצוגה', [['V / S / T / H', 'בחירה / מצב / מעבר / הזזת תצוגה'], ['Shift + גרירה', 'בחירה במלבן'], ['גרירה של רקע, רווח + גרירה', 'הזזת התצוגה'], ['גלגלת, + / −, 0', 'זום, 100%'], ['F', 'התאמה למסך'], ['L', 'סידור אוטומטי'], ['Alt+1 … Alt+4', 'מעבר בין סוגי המכונות']]],
        ['הרצה', [['Enter (בשדה המילה)', 'הרצה מההתחלה'], ['P', 'הרצה / עצירה'], ['→ / ←', 'צעד קדימה / אחורה (בזמן הרצה)'], ['Home / End', 'להתחלה / לסוף'], ['Esc', 'עצירת ההרצה, ביטול בחירה']]],
        ['קבצים', [['Ctrl+S', 'שמירה לקובץ JSON'], ['Ctrl+O', 'פתיחת קובץ']]],
      ];
      return '<div class="help-grid">' + rows.map(([t, list]) => '<section><h3>' + t + '</h3><dl>' + list.map(([k, v]) => '<div><dt>' + k.split(' / ').map((x) => /^[A-Za-z0-9+←→\-−… ]+$/.test(x) ? '<kbd dir="ltr">' + esc(x) + '</kbd>' : esc(x)).join(' / ') + '</dt><dd>' + esc(v) + '</dd></div>').join('') + '</dl></section>').join('') + '</div>' +
        '<p class="muted small">הכול נשמר אוטומטית בדפדפן הזה. כדי להעביר למחשב אחר: שמירה לקובץ.</p>';
    },

    bindTop() {
      const cmd = (name, el) => {
        switch (name) {
          case 'undo': this.undo(); break;
          case 'redo': this.redo(); break;
          case 'examples': this.toggleMenu('examples-menu', el); break;
          case 'save-menu': this.toggleMenu('save-menu', el); break;
          case 'save': this.toggleMenu('save-menu', null, false); this.saveFile(); break;
          case 'export-svg': this.toggleMenu('save-menu', null, false); this.exportSVG(); break;
          case 'open': $('#file-input').click(); break;
          case 'new': this.newMachine(); break;
          case 'theme': this.setTheme(document.documentElement.dataset.theme === 'dark' ? 'light' : 'dark'); break;
          case 'help': this.showModal('קיצורי מקלדת', this.helpHTML()); break;
          case 'close-modal': this.closeModal(); break;
          case 'layout': this.autoLayout(); break;
          case 'fit': this.canvas.fit(); this.autosave(); break;
          case 'delete': this.deleteSelection(); break;
          case 'zoom-in': this.canvas.zoomBy(1.2); break;
          case 'zoom-out': this.canvas.zoomBy(1 / 1.2); break;
          case 'zoom-reset': this.canvas.zoomBy(1 / this.machine.view.zoom); break;
          case 'add-state': this.addStateCenter(); break;
          case 'load-example': { const ex = EX.EXAMPLES.find((e) => e.type === this.active); if (ex) this.loadExample(ex.id); break; }
          case 'run-tests': this.runPanel.runTests(); break;
        }
      };
      document.addEventListener('click', (e) => {
        const ex = e.target.closest('[data-example]');
        if (ex) { this.toggleMenu('examples-menu', null, false); this.loadExample(ex.dataset.example); return; }
        const b = e.target.closest('[data-cmd]');
        if (b && !b.disabled) { cmd(b.dataset.cmd, b); return; }
        const tool = e.target.closest('[data-tool]');
        if (tool) { this.setTool(tool.dataset.tool); return; }
        const tab = e.target.closest('#type-tabs [data-type]');
        if (tab) { this.switchType(tab.dataset.type); return; }
        if (!e.target.closest('.menu-wrap')) { $$('.menu').forEach((x) => { x.hidden = true; }); $$('[aria-haspopup]').forEach((x) => x.setAttribute('aria-expanded', 'false')); }
        if (e.target.id === 'modal') this.closeModal();
      });
      $('#canvas-status').addEventListener('click', () => this.side.show('issues'));
      $('#canvas-status').addEventListener('keydown', (e) => { if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); this.side.show('issues'); } });
      $('#file-input').addEventListener('change', (e) => { const f = e.target.files[0]; if (f) this.openFile(f); e.target.value = ''; });
      const name = $('#machine-name');
      name.addEventListener('change', () => { const v = name.value.trim() || 'מכונה ללא שם'; this.change('שם המכונה', () => { this.machine.name = v; }, null, true); });
      name.addEventListener('keydown', (e) => { e.stopPropagation(); if (e.key === 'Enter' || e.key === 'Escape') name.blur(); });
      // מקשי חצים בין הלשוניות (role=tablist). ב-RTL: שמאלה = הבא.
      $('#type-tabs').addEventListener('keydown', (e) => {
        if (e.key !== 'ArrowLeft' && e.key !== 'ArrowRight') return;
        e.preventDefault(); e.stopPropagation();
        const i = M.TYPES.indexOf(this.active);
        const ni = (i + (e.key === 'ArrowLeft' ? 1 : -1) + M.TYPES.length) % M.TYPES.length;
        this.switchType(M.TYPES[ni]);
        $('#type-tabs [data-type=' + M.TYPES[ni] + ']').focus();
      });
      // גרירת קובץ JSON אל החלון
      document.addEventListener('dragover', (e) => { if (e.dataTransfer && Array.from(e.dataTransfer.types).includes('Files')) e.preventDefault(); });
      document.addEventListener('drop', (e) => { const f = e.dataTransfer && e.dataTransfer.files[0]; if (f) { e.preventDefault(); this.openFile(f); } });
    },

    setTheme(t) {
      document.documentElement.dataset.theme = t;
      store.set(THEME_KEY, t);
      this.renderTop();
      this.canvas.render();
    },

    bindKeys() {
      document.addEventListener('keydown', (e) => {
        if (e.target.closest && e.target.closest('input, textarea, select, [contenteditable]')) return;
        if (!$('#modal').hidden) { if (e.key === 'Escape') this.closeModal(); return; }
        const mod = e.ctrlKey || e.metaKey;
        const code = e.code;
        const running = this.isRunning();
        if (mod && code === 'KeyZ') { e.preventDefault(); e.shiftKey ? this.redo() : this.undo(); return; }
        if (mod && code === 'KeyY') { e.preventDefault(); this.redo(); return; }
        if (mod && code === 'KeyS') { e.preventDefault(); this.saveFile(); return; }
        if (mod && code === 'KeyO') { e.preventDefault(); $('#file-input').click(); return; }
        if (mod && code === 'KeyA') { e.preventDefault(); this.select(this.machine.states.map((s) => s.id), []); return; }
        if (e.altKey && /^Digit[1-4]$/.test(code)) { e.preventDefault(); this.switchType(M.TYPES[+code.slice(5) - 1]); return; }
        if (mod || e.altKey) return;
        if (code === 'Space') { if (!this.canvas.spaceDown) { this.canvas.spaceDown = true; this.canvas.host.classList.add('space'); } e.preventDefault(); return; }
        if (e.key === 'Escape') {
          if ($$('.menu').some((x) => !x.hidden)) { $$('.menu').forEach((x) => { x.hidden = true; }); return; }
          if (running || (this.run && this.run.error)) { this.stopRun(); return; }
          if (this.canvas.pendingFrom) { this.canvas.pendingFrom = null; this.hint(null); this.canvas.render(); return; }
          if (this.tool !== 'select') { this.setTool('select'); return; }
          this.select([], []);
          return;
        }
        if (running) {
          if (e.key === 'ArrowRight') { e.preventDefault(); this.runCmd('next'); return; }
          if (e.key === 'ArrowLeft') { e.preventDefault(); this.runCmd('prev'); return; }
          if (e.key === 'Home') { e.preventDefault(); this.runCmd('rewind'); return; }
          if (e.key === 'End') { e.preventDefault(); this.runCmd('end'); return; }
        }
        if (code === 'KeyP') { e.preventDefault(); this.runCmd('play'); return; }
        if (e.key === 'Delete' || e.key === 'Backspace') { e.preventDefault(); this.deleteSelection(); return; }
        const one = this.sel.states.size === 1 ? Array.from(this.sel.states)[0] : null;
        if (code === 'KeyA' && this.sel.states.size) { this.change('מצב מקבל', () => { const all = Array.from(this.sel.states).every((id) => M.getState(this.machine, id).accepting); this.sel.states.forEach((id) => M.setAccepting(this.machine, id, !all)); }); return; }
        if (code === 'KeyI' && one) { this.stateAction('start', one); return; }
        if ((e.key === 'F2' || e.key === 'Enter') && one) { e.preventDefault(); this.canvas.openRenameEditor(one); return; }
        if ((e.key === 'F2' || e.key === 'Enter') && this.sel.trans.size === 1) { e.preventDefault(); this.canvas.openLabelEditor(Array.from(this.sel.trans)[0]); return; }
        if (code === 'KeyV') return this.setTool('select');
        if (code === 'KeyS') return this.setTool('state');
        if (code === 'KeyT') { if (one) { this.stateAction('connect', one); return; } return this.setTool('transition'); }
        if (code === 'KeyH') return this.setTool('hand');
        if (code === 'KeyL') return this.autoLayout();
        if (code === 'KeyF') { this.canvas.fit(); this.autosave(); return; }
        if (e.key === '+' || e.key === '=') return this.canvas.zoomBy(1.2);
        if (e.key === '-' || e.key === '_') return this.canvas.zoomBy(1 / 1.2);
        if (e.key === '0') return this.canvas.zoomBy(1 / this.machine.view.zoom);
        if (e.key === '?') return this.showModal('קיצורי מקלדת', this.helpHTML());
        if (e.key.startsWith('Arrow') && this.sel.states.size && !running) {
          e.preventDefault();
          const d = e.shiftKey ? 20 : 5;
          const dx = e.key === 'ArrowLeft' ? -d : e.key === 'ArrowRight' ? d : 0;
          const dy = e.key === 'ArrowUp' ? -d : e.key === 'ArrowDown' ? d : 0;
          const snap = this.nudgeSnap || this.snapshot();
          this.sel.states.forEach((id) => { const s = M.getState(this.machine, id); M.moveState(this.machine, id, s.x + dx, s.y + dy); });
          this.canvas.render();
          this.nudgeSnap = snap;
          clearTimeout(this.nudgeT);
          this.nudgeT = setTimeout(() => { this.change('הזזה', null, this.nudgeSnap); this.nudgeSnap = null; }, 400);
        }
      });
      document.addEventListener('keyup', (e) => { if (e.code === 'Space') { this.canvas.spaceDown = false; this.canvas.host.classList.remove('space'); } });
      window.addEventListener('blur', () => { this.canvas.spaceDown = false; this.canvas.host.classList.remove('space'); });
    },
  };

  root.App = app;
  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', () => app.init());
  else app.init();
})(typeof globalThis !== 'undefined' ? globalThis : this);
