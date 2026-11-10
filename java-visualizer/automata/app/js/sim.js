/*
 * sim.js - הרצת מילה על מכונה. לוגיקה טהורה, בלי DOM.
 *
 * כל הפונקציות מחזירות "מעקב" (trace) באותו מבנה, כדי שחלון ההרצה יעבוד אותו דבר לכל סוגי המכונות:
 *   {
 *     type, word: [סמלים],
 *     steps: [ { pos, active: [stateIds], edges: [{ t, l }], read, configs?, tm? , note } ],
 *     result: 'accept' | 'reject' | 'limit',
 *     reason: { code, text, ... },
 *     acceptedStates: [stateIds]          // המצבים המקבלים שבהם הסתיימה הריצה (לצביעה בירוק)
 *   }
 * steps[0] הוא מצב ההתחלה (לפני שנקראה אות). edges הם המעברים שנלקחו כדי להגיע לצעד (t = id של קשת, l = אינדקס התווית).
 */
(function (root, factory) {
  const api = factory(root.AutomataModel || (typeof require === 'function' ? require('./model.js') : null));
  if (typeof module === 'object' && module.exports) module.exports = api;
  else root.AutomataSim = api;
})(typeof globalThis !== 'undefined' ? globalThis : this, function (Model) {
  'use strict';

  const EPS = '';
  const LIMITS = { pdaSteps: 500, pdaConfigs: 2000, pdaStack: 200, tmSteps: 5000, tmTape: 10000 };

  const nameOf = (m, id) => Model.stateName(m, id);
  const listNames = (m, ids) => ids.map((id) => nameOf(m, id)).join(', ');

  function outgoing(m) {
    const map = new Map();
    for (const s of m.states) map.set(s.id, []);
    for (const t of m.transitions) if (map.has(t.from)) map.get(t.from).push(t);
    return map;
  }

  function isAccepting(m, id) { const s = Model.getState(m, id); return !!(s && s.accepting); }

  function noStart(m, word) {
    return { type: m.type, word, steps: [], result: 'reject', reason: { code: 'NO_START', text: 'אין מצב התחלתי' }, acceptedStates: [] };
  }

  /* ---------------- אס"ד ---------------- */

  /**
   * אס"ד. מעבר חסר = דחייה (מצב בור משתמע). אם יש שני מעברים עם אותה אות, נלקח הראשון
   * (validate() מסמן את זה כשגיאה, והממשק לא מריץ במצב כזה).
   */
  function runDFA(m, word) {
    word = Model.symbols(word);
    if (!m.start) return noStart(m, word);
    const out = outgoing(m);
    let cur = m.start;
    const steps = [{ pos: 0, active: [cur], edges: [], read: null, note: 'מתחילים במצב ' + nameOf(m, cur) }];
    for (let i = 0; i < word.length; i++) {
      const a = word[i];
      let hit = null;
      for (const t of out.get(cur) || []) {
        const li = t.labels.findIndex((l) => l.read === a);
        if (li >= 0) { hit = { t, li }; break; }
      }
      if (!hit) {
        return {
          type: 'dfa', word, steps, result: 'reject', acceptedStates: [],
          reason: { code: 'NO_TRANSITION', pos: i, state: cur, symbol: a, text: 'אין מעבר מ-' + nameOf(m, cur) + ' עבור ' + a + ', ולכן המילה נדחית' },
        };
      }
      const from = cur;
      cur = hit.t.to;
      steps.push({ pos: i + 1, active: [cur], edges: [{ t: hit.t.id, l: hit.li }], read: a, note: 'קראנו ' + a + ': ' + nameOf(m, from) + ' → ' + nameOf(m, cur) });
    }
    const ok = isAccepting(m, cur);
    return {
      type: 'dfa', word, steps, result: ok ? 'accept' : 'reject', acceptedStates: ok ? [cur] : [],
      reason: ok
        ? { code: 'ACCEPT_STATE', state: cur, text: 'המילה נגמרה במצב מקבל ' + nameOf(m, cur) }
        : { code: 'NOT_ACCEPTING', state: cur, text: 'המילה נגמרה במצב ' + nameOf(m, cur) + ', שאינו מקבל' },
    };
  }

  /* ---------------- אסל"ד ---------------- */

  /** סגור ε של קבוצת מצבים. מחזיר { states: [ids] (לפי סדר גילוי), edges: [{t,l}] שנלקחו }. */
  function epsilonClosure(m, ids, out) {
    out = out || outgoing(m);
    const seen = new Set(ids);
    const order = Array.from(seen);
    const edges = [];
    for (let i = 0; i < order.length; i++) {
      for (const t of out.get(order[i]) || []) {
        t.labels.forEach((l, li) => {
          if (l.read !== EPS) return;
          edges.push({ t: t.id, l: li });
          if (!seen.has(t.to)) { seen.add(t.to); order.push(t.to); }
        });
      }
    }
    return { states: order, edges };
  }

  function runNFA(m, word) {
    word = Model.symbols(word);
    if (!m.start) return noStart(m, word);
    const out = outgoing(m);
    const c0 = epsilonClosure(m, [m.start], out);
    let active = c0.states;
    const steps = [{ pos: 0, active, edges: c0.edges, read: null, note: c0.states.length > 1 ? 'מתחילים ב-' + nameOf(m, m.start) + ', ועם ε גם ב-' + listNames(m, c0.states.slice(1)) : 'מתחילים במצב ' + nameOf(m, m.start) }];
    for (let i = 0; i < word.length; i++) {
      const a = word[i];
      const next = [];
      const edges = [];
      for (const s of active) for (const t of out.get(s) || []) {
        t.labels.forEach((l, li) => {
          if (l.read !== a) return;
          edges.push({ t: t.id, l: li });
          if (!next.includes(t.to)) next.push(t.to);
        });
      }
      const cl = epsilonClosure(m, next, out);
      active = cl.states;
      steps.push({ pos: i + 1, active, edges: edges.concat(cl.edges), read: a, note: active.length ? 'קראנו ' + a + ': המצבים הפעילים {' + listNames(m, active) + '}' : 'קראנו ' + a + ': אין לאן להמשיך' });
      if (!active.length) {
        return {
          type: 'nfa', word, steps, result: 'reject', acceptedStates: [],
          reason: { code: 'ALL_BRANCHES_DEAD', pos: i, symbol: a, text: 'כל הענפים נתקעו באות ' + a + ' (מקום ' + (i + 1) + '), ולכן המילה נדחית' },
        };
      }
    }
    const acc = active.filter((id) => isAccepting(m, id));
    return {
      type: 'nfa', word, steps, result: acc.length ? 'accept' : 'reject', acceptedStates: acc,
      reason: acc.length
        ? { code: 'ACCEPT_STATE', text: 'לפחות ענף אחד הסתיים במצב מקבל (' + listNames(m, acc) + ')' }
        : { code: 'NOT_ACCEPTING', text: 'המילה נגמרה במצבים {' + listNames(m, active) + '}, ואף אחד מהם אינו מקבל' },
    };
  }

  /* ---------------- אוטומט מחסנית ---------------- */

  /**
   * BFS על קבוצת קונפיגורציות { state, pos, stack } (stack = מחרוזת, התו הראשון הוא הראש).
   * צעד = כל קונפיגורציה מבצעת מעבר אחד (כולל מעברי ε). קונפיגורציה שכבר נראתה לא נכנסת שוב.
   * קבלה: מצב מקבל בסוף המילה (acceptBy 'final') או מחסנית ריקה בסוף המילה ('empty').
   * עצירה: קבלה / אין קונפיגורציות / מגבלת צעדים, קונפיגורציות או גובה מחסנית ('limit').
   */
  function runPDA(m, word, opts) {
    opts = Object.assign({ maxSteps: LIMITS.pdaSteps, maxConfigs: LIMITS.pdaConfigs, maxStack: LIMITS.pdaStack }, opts);
    word = Model.symbols(word);
    if (!m.start) return noStart(m, word);
    const out = outgoing(m);
    const byEmpty = m.acceptBy === 'empty';
    const key = (c) => c.state + '|' + c.pos + '|' + c.stack;
    const accepts = (c) => c.pos === word.length && (byEmpty ? c.stack === '' : isAccepting(m, c.state));
    let frontier = [{ state: m.start, pos: 0, stack: m.stackStart || '', id: 0, parent: -1 }];
    const seen = new Set(frontier.map(key));
    let nextId = 1, truncated = false;
    const mkStep = (configs, edges, note) => ({
      pos: Math.max(...configs.map((c) => c.pos), 0),
      active: Array.from(new Set(configs.map((c) => c.state))),
      edges, read: null, note,
      configs: configs.map((c) => ({ id: c.id, parent: c.parent, state: c.state, pos: c.pos, stack: c.stack })),
    });
    const steps = [mkStep(frontier, [], 'מתחילים במצב ' + nameOf(m, m.start) + ' עם ' + (m.stackStart || 'מחסנית ריקה') + ' במחסנית')];
    const done = (result, reason, acc) => ({ type: 'pda', word, steps, result, reason, acceptedStates: acc || [] });

    for (let step = 0; ; step++) {
      const winners = frontier.filter(accepts);
      if (winners.length) {
        return done('accept', { code: byEmpty ? 'ACCEPT_EMPTY' : 'ACCEPT_STATE', text: byEmpty ? 'המילה נקראה כולה והמחסנית ריקה' : 'המילה נקראה כולה ויש ענף במצב מקבל (' + listNames(m, Array.from(new Set(winners.map((c) => c.state)))) + ')' }, Array.from(new Set(winners.map((c) => c.state))));
      }
      if (step >= opts.maxSteps) return done('limit', { code: 'STEP_LIMIT', text: 'הגענו למגבלה של ' + opts.maxSteps + ' צעדים בלי הכרעה. ייתכן שיש לולאת ε.' });
      const next = [];
      const edges = [];
      for (const c of frontier) {
        const top = c.stack.length ? Array.from(c.stack)[0] : null;
        for (const t of out.get(c.state) || []) {
          t.labels.forEach((l, li) => {
            if (l.read !== EPS && (c.pos >= word.length || word[c.pos] !== l.read)) return;
            if (l.pop !== EPS && l.pop !== top) return;
            const rest = l.pop === EPS ? c.stack : c.stack.slice(top.length);
            const stack = l.push + rest;
            if (Array.from(stack).length > opts.maxStack) { truncated = true; return; }
            const nc = { state: t.to, pos: c.pos + (l.read === EPS ? 0 : 1), stack, id: nextId++, parent: c.id };
            const k = key(nc);
            if (seen.has(k)) return;
            seen.add(k);
            next.push(nc);
            edges.push({ t: t.id, l: li });
          });
        }
      }
      if (!next.length) {
        if (truncated) return done('limit', { code: 'STACK_LIMIT', text: 'המחסנית עברה ' + opts.maxStack + ' סמלים בלי הכרעה' });
        const maxPos = Math.max(...frontier.map((c) => c.pos));
        return done('reject', maxPos < word.length
          ? { code: 'ALL_BRANCHES_DEAD', pos: maxPos, text: 'אין מעבר שמתאים לאות ' + word[maxPos] + ' ולראש המחסנית, ולכן המילה נדחית' }
          : { code: byEmpty ? 'STACK_NOT_EMPTY' : 'NOT_ACCEPTING', text: byEmpty ? 'המילה נקראה כולה, אבל המחסנית לא התרוקנה' : 'המילה נקראה כולה, אבל אף ענף לא הגיע למצב מקבל' });
      }
      if (next.length > opts.maxConfigs) return done('limit', { code: 'CONFIG_LIMIT', text: 'יותר מ-' + opts.maxConfigs + ' ענפים פתוחים בבת אחת. עצרנו.' });
      frontier = next;
      const read = Array.from(new Set(next.filter((c) => c.pos > 0).map((c) => word[c.pos - 1])));
      steps.push(mkStep(next, edges, next.length === 1 ? 'מצב ' + nameOf(m, next[0].state) + ', מחסנית ' + (next[0].stack || 'ריקה') : next.length + ' ענפים פתוחים'));
      steps[steps.length - 1].read = read.length === 1 ? read[0] : null;
    }
  }

  /* ---------------- מכונת טיורינג ---------------- */

  /** סרט: מערך תאים + היסט (offset) כך שתא i נמצא ב-cells[i - offset]. */
  function tapeSnapshot(tape, blank) {
    const keys = Array.from(tape.keys());
    if (!keys.length) return { start: 0, cells: [] };
    const lo = Math.min(...keys), hi = Math.max(...keys);
    const cells = [];
    for (let i = lo; i <= hi; i++) cells.push(tape.has(i) ? tape.get(i) : blank);
    return { start: lo, cells };
  }

  /** מכונת טיורינג דטרמיניסטית עם סרט אחד, אינסופי לשני הצדדים. עוצרת כשאין כלל מתאים, או מיד כשנכנסים למצב מקבל (haltOnAccept). */
  function runTM(m, word, opts) {
    opts = Object.assign({ maxSteps: LIMITS.tmSteps, haltOnAccept: true, record: true }, opts);
    word = Model.symbols(word);
    if (!m.start) return noStart(m, word);
    const blank = m.blank || '_';
    const out = outgoing(m);
    const tape = new Map();
    word.forEach((c, i) => tape.set(i, c));
    let head = 0, state = m.start;
    const snap = (edges, note, read) => ({ pos: head, active: [state], edges, read, note, tm: { state, head, tape: opts.record ? tapeSnapshot(tape, blank) : null } });
    const steps = [snap([], 'מתחילים במצב ' + nameOf(m, state) + ', הראש על התא הראשון', null)];
    const done = (result, reason) => {
      const t = tapeSnapshot(tape, blank);
      const output = t.cells.join('').replace(new RegExp('^' + escapeRe(blank) + '+|' + escapeRe(blank) + '+$', 'g'), '');
      return { type: 'tm', word, steps, result, reason, acceptedStates: result === 'accept' ? [state] : [], output, finalTape: t, finalHead: head };
    };
    for (let n = 0; ; n++) {
      if (opts.haltOnAccept && isAccepting(m, state)) return done('accept', { code: 'ACCEPT_STATE', text: 'המכונה הגיעה למצב מקבל ' + nameOf(m, state) });
      if (n >= opts.maxSteps) return done('limit', { code: 'STEP_LIMIT', text: 'המכונה רצה ' + opts.maxSteps + ' צעדים ולא עצרה. ייתכן שהיא לא עוצרת על המילה הזו.' });
      const sym = tape.has(head) ? tape.get(head) : blank;
      let hit = null;
      for (const t of out.get(state) || []) {
        const li = t.labels.findIndex((l) => l.read === sym);
        if (li >= 0) { hit = { t, li, l: t.labels[li] }; break; }
      }
      if (!hit) {
        if (isAccepting(m, state)) return done('accept', { code: 'ACCEPT_STATE', text: 'המכונה עצרה במצב מקבל ' + nameOf(m, state) });
        return done('reject', { code: 'HALT_NOT_ACCEPTING', state, symbol: sym, text: 'אין כלל מ-' + nameOf(m, state) + ' עבור ' + sym + ': המכונה עצרה במצב שאינו מקבל' });
      }
      const from = state;
      if (hit.l.write === blank) tape.delete(head); else tape.set(head, hit.l.write);
      head += hit.l.move === 'R' ? 1 : hit.l.move === 'L' ? -1 : 0;
      state = hit.t.to;
      steps.push(snap([{ t: hit.t.id, l: hit.li }], nameOf(m, from) + ': קראנו ' + sym + ', כתבנו ' + hit.l.write + ', זזנו ' + ({ R: 'ימינה', L: 'שמאלה', S: 'לא זזנו' })[hit.l.move] + ' → ' + nameOf(m, state), sym));
      if (!opts.record && steps.length > 2) steps.splice(1, 1);
    }
  }

  function escapeRe(s) { return s.replace(/[.*+?^${}()|[\]\\]/g, '\\$&'); }

  /* ---------------- כללי ---------------- */

  function run(m, word, opts) {
    switch (m.type) {
      case 'dfa': return runDFA(m, word);
      case 'nfa': return runNFA(m, word);
      case 'pda': return runPDA(m, word, opts);
      case 'tm': return runTM(m, word, opts);
      default: throw new Error('סוג לא מוכר ' + m.type);
    }
  }

  /** מריץ את כל הבדיקות. מחזיר [{ word, expect, got: 'accept'|'reject'|'limit', pass, reason }]. */
  function runTests(m, tests) {
    return tests.map((tc) => {
      const tr = run(m, tc.word, { record: false });
      const got = tr.result;
      return { word: tc.word, expect: tc.expect, got, pass: got !== 'limit' && (got === 'accept') === !!tc.expect, reason: tr.reason, output: tr.output };
    });
  }

  return { LIMITS, epsilonClosure, runDFA, runNFA, runPDA, runTM, run, runTests, tapeSnapshot };
});
