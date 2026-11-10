/*
 * side-panel.js - פאנל הצד: מאפיינים (מכונה / מצב / מעבר), אלפבית (Σ, Γ), בעיות.
 */
(function (root) {
  'use strict';
  const M = root.AutomataModel, I = root.Icons;
  const esc = (s) => String(s == null ? '' : s).replace(/[&<>"]/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' })[c]);
  const sym = (s) => '<bdi class="sym">' + esc(s === '' ? 'ε' : s) + '</bdi>';
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
      badge.hidden = !app.issues.length || (app.issues.length === 1 && app.issues[0].code === 'NO_STATES');   // קנבס ריק אינו "בעיה"
      badge.textContent = app.issues.length;
      badge.className = 'badge' + (errs ? ' err' : warns ? ' warn' : '');
      // לא מציירים מחדש בזמן שהמשתמש מקליד בשדה של הפאנל
      if (this.body.contains(document.activeElement) && document.activeElement.matches('input[type=text],textarea')) return;
      if (this.tab === 'lang') { this.body.innerHTML = app.langPanel.html(); app.langPanel.mount(this.body); return; }
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
      h += root.Guide.sideHTML();
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
      h += '<section class="sec"><h5>מעברים יוצאים <span class="count">' + out.length + '</span></h5>' + (out.length ? '<ul class="edge-list">' + out.map((t) => this.edgeRow(t, 'out')).join('') + '</ul>' : '<p class="muted small">אין. לוחצים על המצב בקנבס, ואז על מצב אחר.</p>') + '</section>';
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
        (this.app.machine.type === 'dfa' && is.some((x) => x.code === 'MISSING_TRANSITIONS')
          ? '<div class="trap-box"><p>במקום מעברים חסרים אפשר להוסיף <b>מצב מלכודת</b>: מצב לא מקבל שכל מעבר חסר מוביל אליו, ומשם אין יציאה. השפה לא משתנה.</p><button type="button" class="btn secondary sm" data-act="trap">' + I.get('plus', 16) + '<span>השלמה עם מצב מלכודת</span></button></div>' : '') +
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
        case 'keys': app.showHelp(); break;
        case 'trap': app.completeTrap(); break;
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


  root.SidePanel = SidePanel;
})(typeof globalThis !== 'undefined' ? globalThis : this);
