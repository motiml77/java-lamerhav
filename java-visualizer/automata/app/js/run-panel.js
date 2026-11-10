/*
 * run-panel.js - חלון ההרצה: מילה, בקרה, מצב, סרט הקלט, מחסנית, סרט טיורינג, ובדיקת מילים.
 */
(function (root) {
  'use strict';
  const M = root.AutomataModel, I = root.Icons;
  const esc = (s) => String(s == null ? '' : s).replace(/[&<>"]/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' })[c]);
  const sym = (s) => '<bdi class="sym">' + esc(s === '' ? 'ε' : s) + '</bdi>';
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
      this.renderBanner(tr, atEnd);
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

    /** באנר גדול על הקנבס בסוף ההרצה: מתקבלת / נדחית / לא הוכרע, עם הסיבה. */
    renderBanner(tr, atEnd) {
      const el = document.getElementById('result-banner');
      if (!tr || !atEnd) { el.hidden = true; el.dataset.sig = ''; return; }
      const m = this.app.machine;
      const sig = tr.result + '|' + tr.word.join('') + '|' + tr.reason.text;
      if (el.dataset.sig === sig && !el.hidden) return;
      el.dataset.sig = sig;
      const w = tr.word.length ? '<bdi class="sym" dir="ltr">' + esc(tr.word.join('')) + '</bdi>' : 'הריקה <bdi class="sym">ε</bdi>';
      const kind = tr.result === 'accept' ? 'ok' : tr.result === 'reject' ? 'err' : 'warn';
      let title;
      if (m.type === 'tm') title = kind === 'ok' ? 'המכונה קיבלה את ' + w : kind === 'err' ? 'המכונה דחתה את ' + w : 'לא הוכרע: המכונה לא עצרה';
      else title = kind === 'ok' ? 'המילה ' + w + ' מתקבלת' : kind === 'err' ? 'המילה ' + w + ' נדחית' : 'לא הוכרע';
      el.className = 'result-banner ' + kind;
      el.innerHTML = '<span class="rb-ic">' + I.get(kind === 'ok' ? 'check' : kind === 'err' ? 'x' : 'warn', 20) + '</span><span class="rb-text"><b>' + title + '</b><span>' + esc(tr.reason.text) + (m.type === 'tm' && tr.output != null && kind === 'ok' ? '. פלט: ' + esc(tr.output || 'ε') : '') + '</span></span>';
      el.hidden = false;
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


  root.RunPanel = RunPanel;
})(typeof globalThis !== 'undefined' ? globalThis : this);
