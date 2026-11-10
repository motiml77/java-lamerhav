/*
 * panels.js - פאנל הצד (מאפיינים / אלפבית / בעיות) וחלון ההרצה (מילה, בקרה, סרט, מחסנית, בדיקות).
 */
(function (root) {
  'use strict';
  const M = root.AutomataModel, I = root.Icons;
  const esc = (s) => String(s == null ? '' : s).replace(/[&<>"]/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' })[c]);
  const sym = (s) => '<bdi class="sym">' + esc(s === '' ? 'ε' : s) + '</bdi>';

  /* =================== פאנל צד =================== */
  class SidePanel {
    constructor(app, el) {
      this.app = app;
      this.el = el;
      this.body = el.querySelector('#side-body');
      this.tab = 'props';
      el.querySelector('.side-tabs').addEventListener('click', (e) => {
        const b = e.target.closest('[data-tab]');
        if (b) this.show(b.dataset.tab);
      });
      this.body.addEventListener('click', (e) => this.onClick(e));
      this.body.addEventListener('change', (e) => this.onChange(e));
      this.body.addEventListener('keydown', (e) => this.onKey(e));
    }
    show(tab) { this.tab = tab; this.render(); }

    render() {
      const app = this.app;
      this.el.querySelectorAll('[data-tab]').forEach((b) => b.setAttribute('aria-selected', String(b.dataset.tab === this.tab)));
      const errs = app.issues.filter((i) => i.level === 'error').length, warns = app.issues.length - errs;
      const badge = this.el.querySelector('#issues-badge');
      badge.hidden = !app.issues.length;
      badge.textContent = app.issues.length;
      badge.className = 'badge' + (errs ? ' err' : warns ? ' warn' : '');
      // לא מציירים מחדש בזמן שהמשתמש מקליד בשדה של הפאנל
      if (this.body.contains(document.activeElement) && document.activeElement.matches('input[type=text],textarea')) return;
      this.body.innerHTML = this.tab === 'alpha' ? this.alphaHTML() : this.tab === 'issues' ? this.issuesHTML() : this.propsHTML();
    }

    /* ---------- מאפיינים ---------- */
    propsHTML() {
      const app = this.app, m = app.machine, sel = app.sel;
      if (sel.states.size === 1 && !sel.trans.size) return this.stateHTML(M.getState(m, Array.from(sel.states)[0]));
      if (sel.trans.size === 1 && !sel.states.size) return this.edgeHTML(M.getTransition(m, Array.from(sel.trans)[0]));
      if (sel.states.size + sel.trans.size > 1) {
        return '<section class="sec"><h4>בחירה מרובה</h4><p class="muted">נבחרו ' + sel.states.size + ' מצבים ו-' + sel.trans.size + ' מעברים.</p>' +
          '<div class="btn-row">' + (sel.states.size ? '<button type="button" class="btn secondary sm" data-act="accept-all">' + I.get('accept', 16) + '<span>כולם מקבלים</span></button><button type="button" class="btn secondary sm" data-act="accept-none"><span>אף אחד לא מקבל</span></button>' : '') + '</div>' +
          '<button type="button" class="btn danger-ghost sm block" data-act="delete">' + I.get('trash', 16) + '<span>מחיקת הבחירה</span></button></section>';
      }
      const info = M.TYPE_INFO[m.type];
      const acc = m.states.filter((s) => s.accepting).length;
      let h = '<section class="sec"><div class="sec-kicker">' + esc(info.en) + '</div><h4>' + esc(info.long) + '</h4>' +
        '<div class="stats"><div><b>' + m.states.length + '</b><span>מצבים</span></div><div><b>' + m.transitions.length + '</b><span>מעברים</span></div><div><b>' + acc + '</b><span>מקבלים</span></div></div></section>';
      h += '<section class="sec"><label class="field"><span>מצב התחלתי</span><select data-field="start">' +
        (m.states.length ? '' : '<option>אין מצבים</option>') +
        m.states.map((s) => '<option value="' + s.id + '"' + (s.id === m.start ? ' selected' : '') + '>' + esc(s.name) + '</option>').join('') + '</select></label>';
      if (m.type === 'pda') {
        h += '<div class="field"><span>קבלה לפי</span><div class="seg" role="radiogroup">' +
          '<button type="button" role="radio" data-act="accept-by" data-v="final" aria-checked="' + (m.acceptBy !== 'empty') + '">מצב מקבל</button>' +
          '<button type="button" role="radio" data-act="accept-by" data-v="empty" aria-checked="' + (m.acceptBy === 'empty') + '">מחסנית ריקה</button></div></div>' +
          '<label class="field"><span>סמל תחתית המחסנית</span><input type="text" dir="ltr" class="mono short" maxlength="2" data-field="stackStart" value="' + esc(m.stackStart) + '"></label>';
      }
      if (m.type === 'tm') {
        h += '<label class="field"><span>סמל ריק</span><input type="text" dir="ltr" class="mono short" maxlength="2" data-field="blank" value="' + esc(m.blank) + '"></label>' +
          '<p class="muted small">המכונה עוצרת כשאין כלל מתאים, או מיד כשהיא נכנסת למצב מקבל. מגבלה: ' + root.AutomataSim.LIMITS.tmSteps.toLocaleString('he') + ' צעדים.</p>';
      }
      h += '</section>';
      h += '<section class="sec tips"><h5>איך בונים</h5><ul>' +
        '<li><kbd>לחיצה כפולה</kbd> על הקנבס: מצב חדש</li>' +
        '<li>גרירה <b>מהשפה</b> של מצב אל מצב: מעבר</li>' +
        '<li><kbd>לחיצה כפולה</kbd> על תווית: עריכת הסמלים</li>' +
        '<li><kbd>A</kbd> מקבל · <kbd>I</kbd> התחלתי · <kbd>Delete</kbd> מחיקה</li>' +
        '<li><kbd>Ctrl+Z</kbd> ביטול · <kbd>Shift</kbd>+גרירה: בחירה במלבן</li></ul></section>';
      return h;
    }

    stateHTML(s) {
      const m = this.app.machine;
      const out = m.transitions.filter((t) => t.from === s.id);
      const inc = m.transitions.filter((t) => t.to === s.id && t.from !== s.id);
      const isStart = m.start === s.id;
      let h = '<section class="sec"><div class="sec-kicker">מצב</div>' +
        '<label class="field"><span>שם</span><input type="text" dir="ltr" class="mono" maxlength="12" data-field="name" value="' + esc(s.name) + '"></label>' +
        '<div class="toggles">' +
        '<button type="button" class="toggle' + (isStart ? ' on' : '') + '" data-act="start" aria-pressed="' + isStart + '"' + (isStart ? ' disabled' : '') + '>' + I.get('start', 18) + '<span><b>מצב התחלתי</b><small>' + (isStart ? 'זה המצב ההתחלתי' : 'יש רק אחד, ולחיצה מעבירה אותו לכאן') + '</small></span><kbd>I</kbd></button>' +
        '<button type="button" class="toggle' + (s.accepting ? ' on' : '') + '" data-act="accept" aria-pressed="' + s.accepting + '">' + I.get('accept', 18) + '<span><b>מצב מקבל</b><small>' + (s.accepting ? 'מילה שנגמרת כאן מתקבלת' : 'לא מקבל') + '</small></span><kbd>A</kbd></button>' +
        '</div></section>';
      h += '<section class="sec"><h5>מעברים יוצאים <span class="count">' + out.length + '</span></h5>' + (out.length ? '<ul class="edge-list">' + out.map((t) => this.edgeRow(t, 'out')).join('') + '</ul>' : '<p class="muted small">אין. גוררים מהשפה של המצב אל מצב אחר.</p>') + '</section>';
      if (inc.length) h += '<section class="sec"><h5>מעברים נכנסים <span class="count">' + inc.length + '</span></h5><ul class="edge-list">' + inc.map((t) => this.edgeRow(t, 'in')).join('') + '</ul></section>';
      h += '<section class="sec"><button type="button" class="btn danger-ghost sm block" data-act="delete">' + I.get('trash', 16) + '<span>מחיקת המצב</span></button></section>';
      return h;
    }

    edgeRow(t, dirn) {
      const m = this.app.machine;
      const other = dirn === 'out' ? t.to : t.from;
      const loop = t.from === t.to;
      return '<li><button type="button" data-act="pick-edge" data-id="' + t.id + '"><span class="er-dir" dir="ltr">' + (loop ? '↻' : dirn === 'out' ? '→ ' + esc(M.stateName(m, other)) : esc(M.stateName(m, other)) + ' →') + '</span><span class="er-label" dir="ltr">' + esc(M.formatLabels(m.type, t.labels).replace(/\n/g, '  ·  ')) + '</span></button></li>';
    }

    edgeHTML(t) {
      const m = this.app.machine;
      const type = m.type;
      const multi = type === 'pda' || type === 'tm';
      const value = multi ? t.labels.map((l) => M.formatLabel(type, l)).join('\n') : t.labels.map((l) => M.formatLabel(type, l)).join(', ');
      const help = {
        dfa: 'סמלים מופרדים בפסיק. באס"ד אין ε, ולכל אות מותר מעבר אחד מכל מצב.',
        nfa: 'סמלים מופרדים בפסיק. ε (או eps) הוא מעבר בלי לקרוא אות.',
        pda: 'כלל בכל שורה: <code dir="ltr">קלט,שליפה/דחיפה</code>. למשל <code dir="ltr">a,Z/AZ</code>: קוראים a, שולפים Z ודוחפים AZ (A למעלה). ε בכל מקום = כלום.',
        tm: 'כלל בכל שורה: <code dir="ltr">קורא/כותב,כיוון</code>. למשל <code dir="ltr">1/0,L</code>. כיוון: R ימינה, L שמאלה, S נשאר.',
      }[type];
      return '<section class="sec"><div class="sec-kicker">מעבר</div><h4 class="edge-title" dir="ltr">' + esc(M.stateName(m, t.from)) + ' <span>→</span> ' + esc(M.stateName(m, t.to)) + '</h4>' +
        '<label class="field"><span>' + (multi ? 'כללים' : 'סמלים') + '</span>' +
        (multi ? '<textarea dir="ltr" class="mono" rows="' + Math.max(3, t.labels.length + 1) + '" data-field="labels" spellcheck="false">' + esc(value) + '</textarea>' : '<input type="text" dir="ltr" class="mono" data-field="labels" spellcheck="false" value="' + esc(value) + '">') +
        '</label><div class="field-err" id="labels-err" role="alert"></div>' +
        (type !== 'dfa' ? '<div class="btn-row"><button type="button" class="chip-btn" data-act="insert" data-v="ε">הוספת ε</button>' + (type === 'pda' ? '<button type="button" class="chip-btn" data-act="insert" data-v="' + esc(m.stackStart) + '">' + esc(m.stackStart) + ' תחתית</button>' : '') + (type === 'tm' ? '<button type="button" class="chip-btn" data-act="insert" data-v="' + esc(m.blank) + '">' + esc(m.blank) + ' ריק</button>' : '') + '</div>' : '') +
        '<p class="muted small help">' + help + '</p></section>' +
        '<section class="sec"><button type="button" class="btn danger-ghost sm block" data-act="delete">' + I.get('trash', 16) + '<span>מחיקת המעבר</span></button></section>';
    }

    /* ---------- אלפבית ---------- */
    alphaHTML() {
      const m = this.app.machine;
      const declared = m.alphabet && m.alphabet.length > 0;
      const used = M.usedInputSymbols(m).filter((s) => m.type !== 'tm' || (s !== m.blank));
      const sigma = M.inputAlphabet(m);
      const outside = declared ? used.filter((s) => !m.alphabet.includes(s) && !(m.type === 'tm' && (m.tapeAlphabet || []).includes(s))) : [];
      let h = '<section class="sec"><div class="alpha-head"><h4><span class="greek">Σ</span> אלפבית הקלט</h4><span class="pill ' + (declared ? 'solid' : 'auto') + '">' + (declared ? 'מוגדר' : 'אוטומטי') + '</span></div>' +
        '<p class="muted small">' + (declared ? 'האותיות שמותר להשתמש בהן במילים ובמעברים.' : 'נגזר מהמעברים. אפשר לקבע אותו, ואז הכלי יתריע על כל אות אחרת.') + '</p>' +
        '<div class="chips" dir="ltr">' + (sigma.length ? sigma.map((s) => '<span class="chip' + (declared ? '' : ' derived') + '">' + sym(s) + (declared ? '<button type="button" data-act="sigma-del" data-v="' + esc(s) + '" aria-label="הסרת ' + esc(s) + '">×</button>' : '') + '</span>').join('') : '<span class="muted small">אין עדיין אותיות</span>') + '</div>' +
        (outside.length ? '<div class="note err">' + I.get('warn', 16) + '<span>במעברים יש אותיות שאינן ב-Σ: ' + outside.map(sym).join(' ') + '</span></div>' : '') +
        '<div class="add-row"><input type="text" dir="ltr" class="mono" data-field="sigma-add" placeholder="a b c" aria-label="הוספת אותיות ל-Σ"><button type="button" class="btn secondary sm" data-act="sigma-add">הוספה</button></div>' +
        '<div class="btn-row">' + (declared ? '<button type="button" class="link" data-act="sigma-auto">חזרה לאוטומטי</button>' : (sigma.length ? '<button type="button" class="link" data-act="sigma-freeze">קיבוע האלפבית</button>' : '')) + '</div></section>';
      if (m.type === 'pda') {
        const g = m.stackAlphabet || [];
        const gUsed = M.usedStackSymbols(m).filter((s) => !g.includes(s));
        h += '<section class="sec"><div class="alpha-head"><h4><span class="greek">Γ</span> אלפבית המחסנית</h4></div>' +
          '<div class="chips" dir="ltr">' + g.map((s) => '<span class="chip' + (s === m.stackStart ? ' special' : '') + '">' + sym(s) + (s === m.stackStart ? '<small>תחתית</small>' : '<button type="button" data-act="gamma-del" data-v="' + esc(s) + '" aria-label="הסרה">×</button>') + '</span>').join('') + '</div>' +
          (gUsed.length ? '<div class="note err">' + I.get('warn', 16) + '<span>במעברים יש סמלי מחסנית שאינם ב-Γ: ' + gUsed.map(sym).join(' ') + '</span></div>' : '') +
          '<div class="add-row"><input type="text" dir="ltr" class="mono" data-field="gamma-add" placeholder="A B" aria-label="הוספה ל-Γ"><button type="button" class="btn secondary sm" data-act="gamma-add">הוספה</button></div></section>';
      }
      if (m.type === 'tm') {
        const g = m.tapeAlphabet || [];
        h += '<section class="sec"><div class="alpha-head"><h4><span class="greek">Γ</span> אלפבית הסרט</h4></div><p class="muted small">כל מה שאפשר לכתוב על הסרט: Σ, הריק, וסמני עזר.</p>' +
          '<div class="chips" dir="ltr">' + g.map((s) => '<span class="chip' + (s === m.blank ? ' special' : '') + '">' + sym(s) + (s === m.blank ? '<small>ריק</small>' : '<button type="button" data-act="gamma-del" data-v="' + esc(s) + '" aria-label="הסרה">×</button>') + '</span>').join('') + '</div>' +
          '<div class="add-row"><input type="text" dir="ltr" class="mono" data-field="gamma-add" placeholder="X Y" aria-label="הוספה ל-Γ"><button type="button" class="btn secondary sm" data-act="gamma-add">הוספה</button></div></section>';
      }
      return h;
    }

    /* ---------- בעיות ---------- */
    issuesHTML() {
      const is = this.app.issues;
      if (!is.length) return '<div class="all-good">' + I.get('check', 28) + '<b>אין בעיות</b><span>אפשר להריץ.</span></div>';
      const order = is.slice().sort((a, b) => (a.level === b.level ? 0 : a.level === 'error' ? -1 : 1));
      return '<ul class="issues">' + order.map((x, i) => '<li class="' + x.level + '"><button type="button" data-act="pick-issue" data-i="' + is.indexOf(x) + '">' + I.get(x.level === 'error' ? 'x' : 'warn', 16) + '<span>' + esc(x.text) + '</span></button></li>').join('') + '</ul>' +
        '<p class="muted small pad">שגיאה (אדום) עוצרת את ההרצה. אזהרה (כתום) לא.</p>';
    }

    /* ---------- אירועים ---------- */
    onClick(e) {
      const b = e.target.closest('[data-act]');
      if (!b) return;
      const app = this.app, m = app.machine, act = b.dataset.act;
      const selState = Array.from(app.sel.states)[0];
      switch (act) {
        case 'start': app.stateAction('start', selState); break;
        case 'accept': app.stateAction('accept', selState); break;
        case 'delete': app.deleteSelection(); break;
        case 'accept-all': app.change('מצבים מקבלים', () => app.sel.states.forEach((id) => M.setAccepting(m, id, true))); break;
        case 'accept-none': app.change('ביטול קבלה', () => app.sel.states.forEach((id) => M.setAccepting(m, id, false))); break;
        case 'pick-edge': app.select([], [b.dataset.id]); break;
        case 'accept-by': app.change('קבלה לפי', () => { m.acceptBy = b.dataset.v; }); break;
        case 'insert': {
          const f = this.body.querySelector('[data-field=labels]');
          const a = f.selectionStart, z = f.selectionEnd;
          f.value = f.value.slice(0, a) + b.dataset.v + f.value.slice(z);
          f.focus(); f.selectionStart = f.selectionEnd = a + b.dataset.v.length;
          break;
        }
        case 'sigma-add': case 'gamma-add': this.addSymbols(act === 'sigma-add' ? 'sigma' : 'gamma'); break;
        case 'sigma-del': app.change('הסרה מ-Σ', () => { m.alphabet = m.alphabet.filter((s) => s !== b.dataset.v); }); break;
        case 'gamma-del': app.change('הסרה מ-Γ', () => { const k = m.type === 'pda' ? 'stackAlphabet' : 'tapeAlphabet'; m[k] = m[k].filter((s) => s !== b.dataset.v); }); break;
        case 'sigma-auto': app.change('אלפבית אוטומטי', () => { m.alphabet = []; }); break;
        case 'sigma-freeze': app.change('קיבוע האלפבית', () => { m.alphabet = M.inputAlphabet(m); }); break;
        case 'pick-issue': { const x = app.issues[+b.dataset.i]; if (x) app.select(x.states, x.transitions); break; }
      }
    }

    addSymbols(which) {
      const app = this.app, m = app.machine;
      const f = this.body.querySelector('[data-field=' + which + '-add]');
      const syms = Array.from(new Set(Array.from(f.value.replace(/[\s,]+/g, ''))));
      if (!syms.length) return;
      f.value = '';
      app.change('הוספת סמלים', () => {
        if (which === 'sigma') { if (!m.alphabet.length) m.alphabet = M.inputAlphabet(m); for (const s of syms) if (!m.alphabet.includes(s) && s !== 'ε') m.alphabet.push(s); }
        else { const k = m.type === 'pda' ? 'stackAlphabet' : 'tapeAlphabet'; m[k] = m[k] || []; for (const s of syms) if (!m[k].includes(s) && s !== 'ε') m[k].push(s); }
      });
    }

    onKey(e) {
      const f = e.target.dataset && e.target.dataset.field;
      if (!f) return;
      e.stopPropagation();
      if (e.key === 'Enter' && !(f === 'labels' && e.target.tagName === 'TEXTAREA' && e.shiftKey)) {
        e.preventDefault();
        if (f === 'sigma-add' || f === 'gamma-add') this.addSymbols(f.split('-')[0]);
        else e.target.blur();
      }
      if (e.key === 'Escape') { e.target.blur(); }
    }

    onChange(e) {
      const app = this.app, m = app.machine;
      const f = e.target.dataset.field;
      const v = e.target.value;
      if (f === 'start') app.change('מצב התחלתי', () => M.setStart(m, v));
      else if (f === 'name') {
        const id = Array.from(app.sel.states)[0];
        const err = M.renameState(M.clone(m), id, v);
        if (err) { app.toast(err, 'err'); e.target.value = M.stateName(m, id); return; }
        app.change('שינוי שם', () => M.renameState(m, id, v));
      } else if (f === 'labels') {
        const id = Array.from(app.sel.trans)[0];
        const r = M.parseLabels(m.type, v);
        const err = this.body.querySelector('#labels-err');
        if (r.errors.length) { err.textContent = r.errors[0]; e.target.classList.add('bad'); return; }
        err.textContent = ''; e.target.classList.remove('bad');
        app.change('עריכת מעבר', () => M.setTransitionLabels(m, id, r.labels));
      } else if (f === 'stackStart' || f === 'blank') {
        const s = Array.from(v.trim())[0];
        if (!s) { e.target.value = m[f]; return; }
        app.change(f === 'blank' ? 'סמל ריק' : 'תחתית המחסנית', () => {
          const k = f === 'blank' ? 'tapeAlphabet' : 'stackAlphabet';
          const old = m[f];
          m[f] = s;
          m[k] = (m[k] || []).map((x) => (x === old ? s : x));
          if (!m[k].includes(s)) m[k].unshift(s);
        });
      }
    }
  }

  /* =================== חלון ההרצה =================== */
  class RunPanel {
    constructor(app, el) {
      this.app = app;
      this.el = el;
      this.word = el.querySelector('#word-input');
      this.status = el.querySelector('#run-status');
      this.note = el.querySelector('#run-note');
      this.view = el.querySelector('#machine-view');
      this.count = el.querySelector('#step-count');
      this.speed = el.querySelector('#speed');
      this.tbody = el.querySelector('#tests-table tbody');
      this.summary = el.querySelector('#tests-summary');
      this.addForm = el.querySelector('#tests-add');
      this.addWord = el.querySelector('#test-word');
      this.addExp = el.querySelector('#test-exp');
      this.testResults = null;

      el.querySelector('.controls').addEventListener('click', (e) => { const b = e.target.closest('[data-run]'); if (b) app.runCmd(b.dataset.run); });
      this.word.addEventListener('input', () => { app.stopRun(); app.word = this.word.value; app.autosave(); this.render(); });
      this.word.addEventListener('keydown', (e) => {
        e.stopPropagation();
        if (e.key === 'Enter') { e.preventDefault(); app.runCmd('play-restart'); }
        if (e.key === 'Escape') { this.word.blur(); app.stopRun(); }
      });
      this.speed.addEventListener('input', () => { app.speed = +this.speed.value; app.autosave(); });
      this.addExp.addEventListener('click', () => { const y = this.addExp.getAttribute('aria-pressed') !== 'true'; this.setExp(y); });
      this.addForm.addEventListener('submit', (e) => {
        e.preventDefault();
        const w = this.addWord.value.trim();
        const yes = this.addExp.getAttribute('aria-pressed') === 'true';
        app.change('הוספת בדיקה', () => app.machine.tests.push({ word: w === 'ε' ? '' : w, expect: yes }));
        this.addWord.value = '';
        this.addWord.focus();
      });
      this.addWord.addEventListener('keydown', (e) => e.stopPropagation());
      this.tbody.addEventListener('click', (e) => this.onTableClick(e));
    }

    setExp(yes) {
      this.addExp.setAttribute('aria-pressed', String(yes));
      this.addExp.textContent = yes ? 'מתקבלת' : 'נדחית';
      this.addExp.className = 'exp-toggle ' + (yes ? 'yes' : 'no');
    }

    render() {
      const app = this.app, run = app.run, m = app.machine;
      if (document.activeElement !== this.word && this.word.value !== app.word) this.word.value = app.word;
      this.speed.value = app.speed;
      const tr = run && run.trace;
      const i = run ? run.index : 0;
      const last = tr ? tr.steps.length - 1 : 0;
      const atEnd = tr && i >= last;
      // כפתורים
      const q = (n) => this.el.querySelector('[data-run=' + n + ']');
      q('prev').disabled = !tr || i === 0;
      q('rewind').disabled = !tr || i === 0;
      q('next').disabled = !!(tr && atEnd) || !!(run && run.error);
      q('end').disabled = !!(tr && atEnd) || !!(run && run.error);
      const play = q('play');
      play.innerHTML = I.get(run && run.playing ? 'pause' : 'play');
      play.setAttribute('aria-label', run && run.playing ? 'עצירה' : 'הרצה');
      play.title = run && run.playing ? 'עצירה (P)' : 'הרצה (Enter במילה, P)';
      // מצב
      let cls = 'idle', text = 'מוכן', icon = 'info';
      if (run && run.error) { cls = 'err'; text = run.error.title; icon = 'x'; }
      else if (tr && atEnd) {
        if (tr.result === 'accept') { cls = 'ok'; text = m.type === 'tm' ? 'המכונה קיבלה' : 'המילה מתקבלת'; icon = 'check'; }
        else if (tr.result === 'reject') { cls = 'err'; text = m.type === 'tm' ? 'המכונה דחתה' : 'המילה נדחית'; icon = 'x'; }
        else { cls = 'warn'; text = 'לא הוכרע'; icon = 'warn'; }
      } else if (tr) { cls = 'run'; text = run.playing ? 'רץ' : 'בהרצה'; icon = 'play'; }
      this.status.className = 'status ' + cls + (tr && atEnd ? ' pop' : '');
      this.status.innerHTML = I.get(icon, 16) + '<span>' + esc(text) + '</span>';
      this.count.textContent = tr ? 'צעד ' + i + ' מתוך ' + last : '';
      // הערה
      let note = '';
      if (run && run.error) note = '<span class="n-err">' + esc(run.error.text) + '</span>';
      else if (tr) {
        const st = tr.steps[i] || {};
        note = esc(st.note || '');
        if (atEnd) note = '<span class="n-' + (tr.result === 'accept' ? 'ok' : tr.result === 'reject' ? 'err' : 'warn') + '">' + esc(tr.reason.text) + '</span>' + (tr.output != null && m.type === 'tm' ? ' <span class="n-out">פלט על הסרט: <bdi class="sym">' + esc(tr.output || 'ε') + '</bdi></span>' : '');
      } else note = '<span class="muted">' + ({ dfa: 'מקלידים מילה ולוחצים Enter, או צועדים אות אחרי אות.', nfa: 'בכל צעד יודגשו כל המצבים שבהם האוטומט יכול להיות.', pda: 'כל ענף פתוח מקבל מחסנית משלו.', tm: 'הראש מתחיל על התו הראשון של המילה.' })[m.type] + '</span>';
      this.note.innerHTML = note;
      this.view.innerHTML = this.machineViewHTML();
      this.renderTests();
    }

    /* ---------- סרט הקלט / מחסנית / סרט טיורינג ---------- */
    machineViewHTML() {
      const app = this.app, m = app.machine, run = app.run;
      const tr = run && run.trace;
      const st = tr ? tr.steps[run.index] : null;
      const word = M.symbols(app.word);
      if (m.type === 'tm') return this.tmHTML(word, st, tr);
      const pos = st ? st.pos : -1;
      const badIdx = run && run.error && run.error.index != null ? run.error.index : -1;
      let cells = '';
      if (!word.length) cells = '<span class="cell eps' + (st ? ' done' : '') + '">ε</span>';
      word.forEach((c, k) => {
        const cl = ['cell'];
        if (st && k < pos) cl.push('done');
        if (st && k === pos) cl.push('now');
        if (k === badIdx) cl.push('bad');
        cells += '<span class="' + cl.join(' ') + '">' + esc(c) + '</span>';
      });
      const fin = tr && run.index === tr.steps.length - 1 ? (tr.result === 'accept' ? ' ok' : ' err') : '';
      const endCls = st && pos >= word.length ? ' now' + fin : '';
      cells += '<span class="cell end' + endCls + '" title="סוף המילה">⊣</span>';
      let h = '<div class="mv-block mv-input"><div class="mv-label">קלט</div><div class="tape">' + cells + '</div></div>';
      if (m.type === 'nfa' || m.type === 'dfa') {
        const act = st ? st.active : [];
        const label = m.type === 'nfa' ? 'מצבים פעילים' : 'מצב נוכחי';
        h += '<div class="mv-block mv-active"><div class="mv-label">' + label + (m.type === 'nfa' && st ? ' <span class="cnt">' + act.length + '</span>' : '') + '</div><div class="act-chips">' +
          (st ? (act.length ? act.map((id) => '<span class="act-chip' + (tr.acceptedStates.includes(id) && run.index === tr.steps.length - 1 ? ' ok' : '') + '">' + esc(M.stateName(m, id)) + '</span>').join('') : '<span class="act-none">∅ אין</span>') : '<span class="act-none">—</span>') + '</div></div>';
      }
      if (m.type === 'pda') h += this.stacksHTML(st, tr);
      return h;
    }

    stacksHTML(st, tr) {
      const m = this.app.machine;
      const configs = st ? st.configs : [{ state: m.start, stack: m.stackStart || '' }];
      const shown = configs.slice(0, 6);
      const cols = shown.map((c) => {
        const syms = Array.from(c.stack);
        const vis = syms.slice(0, 4);
        const more = syms.length - vis.length;
        const won = tr && this.app.run.index === tr.steps.length - 1 && tr.result === 'accept' && tr.acceptedStates.includes(c.state);
        return '<div class="stack-col' + (won ? ' ok' : '') + '"><div class="stack">' + (more > 0 ? '<span class="scell more">+' + more + '</span>' : '') +
          (vis.length ? vis.map((s, k) => '<span class="scell' + (k === 0 ? ' top' : '') + '">' + esc(s) + '</span>').join('') : '<span class="scell empty">ריקה</span>') +
          '</div><div class="stack-state">' + esc(c.state ? M.stateName(m, c.state) : '') + '</div></div>';
      }).join('');
      return '<div class="mv-block mv-stacks"><div class="mv-label">מחסנית' + (configs.length > 1 ? ' <span class="cnt">' + configs.length + ' ענפים</span>' : '') + '</div><div class="stacks">' + cols + (configs.length > 6 ? '<span class="stack-more">+' + (configs.length - 6) + '</span>' : '') + '</div></div>';
    }

    tmHTML(word, st, tr) {
      const m = this.app.machine;
      const blank = m.blank || '_';
      let tape, head, state;
      if (st) { tape = st.tm.tape; head = st.tm.head; state = st.tm.state; }
      else { tape = { start: 0, cells: word }; head = 0; state = m.start; }
      const lo = Math.min(tape.start, head) - 4, hi = Math.max(tape.start + tape.cells.length - 1, head) + 4;
      const from = Math.max(lo, head - 9), to = Math.min(hi, from + 18);
      let cells = '';
      for (let k = from; k <= to; k++) {
        const c = k >= tape.start && k < tape.start + tape.cells.length ? tape.cells[k - tape.start] : blank;
        const cl = ['cell'];
        if (c === blank) cl.push('blank');
        if (k === head) cl.push('now');
        cells += '<span class="' + cl.join(' ') + '" data-i="' + k + '">' + esc(c) + (k === head ? '<span class="head-tag">' + esc(state ? M.stateName(m, state) : '') + '</span>' : '') + '</span>';
      }
      return '<div class="mv-block mv-tm"><div class="mv-label">סרט <span class="cnt">ראש במקום ' + head + '</span></div><div class="tape tm-tape"><span class="fade l"></span>' + cells + '<span class="fade r"></span></div></div>';
    }

    /* ---------- טבלת בדיקות ---------- */
    renderTests() {
      const app = this.app, m = app.machine;
      const res = this.testResults && this.testResults.sig === app.machineSig() ? this.testResults.byWord : null;
      if (!m.tests.length) {
        this.tbody.innerHTML = '<tr class="empty-row"><td colspan="4">אין עדיין בדיקות. מוסיפים מילה למטה, ובוחרים אם היא אמורה להתקבל.</td></tr>';
        this.summary.textContent = '';
        return;
      }
      this.tbody.innerHTML = m.tests.map((t, k) => {
        const r0 = res && res.get(t.word);
        const r = r0 && Object.assign({}, r0, { pass: r0.got !== 'limit' && (r0.got === 'accept') === !!t.expect });
        const got = r ? (r.got === 'limit' ? '<span class="got warn" title="' + esc(r.reason.text) + '">' + I.get('warn', 14) + 'לא הוכרע</span>' : '<span class="got ' + (r.pass ? 'pass' : 'fail') + '" title="' + esc(r.reason.text) + '">' + I.get(r.pass ? 'check' : 'x', 14) + (r.got === 'accept' ? 'מתקבלת' : 'נדחית') + (m.type === 'tm' && r.got === 'accept' ? ' <bdi class="sym out">' + esc(r.output || 'ε') + '</bdi>' : '') + '</span>') : '<span class="got none">—</span>';
        return '<tr class="' + (r ? (r.pass ? 'pass' : 'fail') : '') + '" data-k="' + k + '"><td class="c-word"><button type="button" class="word-btn" data-act="load" title="טעינה לחלון ההרצה" dir="ltr">' + (t.word === '' ? '<span class="eps">ε</span>' : esc(t.word)) + '</button></td>' +
          '<td class="c-exp"><button type="button" class="exp-toggle sm ' + (t.expect ? 'yes' : 'no') + '" data-act="flip" title="לחיצה מחליפה">' + (t.expect ? 'מתקבלת' : 'נדחית') + '</button></td>' +
          '<td class="c-got">' + got + '</td>' +
          '<td class="c-act"><button type="button" class="row-del" data-act="del" aria-label="מחיקת הבדיקה" title="מחיקה">' + I.get('x', 14) + '</button></td></tr>';
      }).join('');
      const done = res ? m.tests.filter((t) => res.has(t.word)) : [];
      if (res && done.length) {
        const pass = done.filter((t) => { const r = res.get(t.word); return r.got !== 'limit' && (r.got === 'accept') === !!t.expect; }).length;
        this.summary.className = 'tests-summary ' + (pass === done.length ? 'all' : 'some');
        this.summary.innerHTML = '<b>' + pass + '/' + done.length + '</b> עברו';
      } else { this.summary.className = 'tests-summary'; this.summary.textContent = m.tests.length + ' מילים'; }
    }

    onTableClick(e) {
      const b = e.target.closest('[data-act]');
      if (!b) return;
      const k = +b.closest('tr').dataset.k;
      const app = this.app, m = app.machine;
      if (b.dataset.act === 'load') { app.word = m.tests[k].word; app.stopRun(); this.render(); app.runCmd('play-restart'); }
      else if (b.dataset.act === 'flip') app.change('צפוי', () => { m.tests[k].expect = !m.tests[k].expect; }, null, true);
      else if (b.dataset.act === 'del') app.change('מחיקת בדיקה', () => m.tests.splice(k, 1), null, true);
    }

    runTests() {
      const app = this.app, m = app.machine;
      const blocking = app.issues.filter((i) => i.level === 'error');
      if (blocking.length) { app.toast('יש לתקן את המכונה לפני הבדיקות: ' + blocking[0].text, 'err'); app.side.show('issues'); return; }
      if (!m.tests.length) { app.toast('אין בדיקות. מוסיפים מילה בשדה שמתחת לטבלה.'); this.addWord.focus(); return; }
      const rows = root.AutomataSim.runTests(m, m.tests);
      this.testResults = { sig: app.machineSig(), byWord: new Map(rows.map((r) => [r.word, r])) };
      this.renderTests();
      const firstFail = this.tbody.querySelector('tr.fail');
      if (firstFail) firstFail.scrollIntoView({ block: 'nearest' });
      const pass = rows.filter((r) => r.pass).length;
      app.toast(pass === rows.length ? 'כל ' + rows.length + ' הבדיקות עברו' : pass + ' מתוך ' + rows.length + ' בדיקות עברו', pass === rows.length ? 'ok' : 'err');
    }
  }

  root.SidePanel = SidePanel;
  root.RunPanel = RunPanel;
})(typeof globalThis !== 'undefined' ? globalThis : this);
