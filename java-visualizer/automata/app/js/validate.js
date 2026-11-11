/*
 * validate.js - בדיקות תקינות של מכונה (בעברית), בדיקת מילה מול Σ, והשלמה עם מצב מלכודת.
 * מרחיב את AutomataModel (נטען אחרי model.js). טהור, בלי DOM.
 */
(function (root, factory) {
  const M = root.AutomataModel || (typeof require === 'function' ? require('./model.js') : null);
  Object.assign(M, factory(M));
})(typeof globalThis !== 'undefined' ? globalThis : this, function (M) {
  'use strict';
  const { EPS, MOVES, getState, stateName, inputAlphabet, symbols } = M;


  /**
   * מחזיר רשימת בעיות: { level: 'error'|'warning', code, text, states: [ids], transitions: [ids] }.
   * שגיאה חוסמת הרצה; אזהרה לא.
   */
  function validate(m) {
    const issues = [];
    const add = (level, code, text, states, transitions) => issues.push({ level, code, text, states: states || [], transitions: transitions || [] });
    if (!m.states.length) { add('error', 'NO_STATES', 'אין עדיין מצבים. לחיצה כפולה על הקנבס מוסיפה מצב.'); return issues; }
    if (!m.start || !getState(m, m.start)) add('error', 'NO_START', 'אין מצב התחלתי. בחרו מצב וסמנו אותו כהתחלתי.');

    const sigma = new Set(m.alphabet || []);
    const declared = sigma.size > 0;
    const name = (id) => stateName(m, id);

    // סמלים מחוץ לאלפבית
    if (declared) {
      for (const t of m.transitions) for (const l of t.labels) {
        if (l.read !== EPS && !sigma.has(l.read) && !(m.type === 'tm' && ((m.tapeAlphabet || []).includes(l.read) || l.read === m.blank))) {
          add('error', 'SYMBOL_NOT_IN_ALPHABET', 'הסמל ' + l.read + ' במעבר ' + name(t.from) + ' → ' + name(t.to) + ' אינו באלפבית Σ', [], [t.id]);
        }
      }
    }
    if (m.type === 'pda' && m.stackAlphabet && m.stackAlphabet.length) {
      const gamma = new Set(m.stackAlphabet);
      for (const t of m.transitions) for (const l of t.labels) {
        const bad = [l.pop].concat(symbols(l.push)).filter((c) => c !== EPS && !gamma.has(c));
        for (const c of new Set(bad)) add('error', 'STACK_SYMBOL_NOT_IN_GAMMA', 'הסמל ' + c + ' במעבר ' + name(t.from) + ' → ' + name(t.to) + ' אינו באלפבית המחסנית Γ', [], [t.id]);
      }
    }

    if (m.type === 'dfa') {
      const alpha = inputAlphabet(m);
      for (const s of m.states) {
        const out = m.transitions.filter((t) => t.from === s.id);
        const bySym = new Map();
        for (const t of out) for (const l of t.labels) {
          if (l.read === EPS) { add('error', 'EPSILON_IN_DFA', 'באס"ד אין מעברי ε (במצב ' + s.name + ')', [s.id], [t.id]); continue; }
          if (!bySym.has(l.read)) bySym.set(l.read, []);
          bySym.get(l.read).push(t.id);
        }
        for (const [sym, ts] of bySym) if (ts.length > 1) {
          add('error', 'NONDETERMINISTIC', 'מהמצב ' + s.name + ' יש ' + ts.length + ' מעברים עם ' + sym + '. באס"ד מותר מעבר אחד לכל אות.', [s.id], ts);
        }
        const missing = alpha.filter((a) => !bySym.has(a));
        if (missing.length && alpha.length) {
          add('warning', 'MISSING_TRANSITIONS', 'למצב ' + s.name + ' חסר מעבר עבור ' + missing.join(', ') + '. מילה שתגיע לכאן עם האות הזו תידחה. אפשר להשלים עם מצב מלכודת.', [s.id]);
        }
      }
    }

    if (m.type === 'tm') {
      for (const s of m.states) {
        const bySym = new Map();
        for (const t of m.transitions.filter((x) => x.from === s.id)) for (const l of t.labels) {
          if (!MOVES.includes(l.move)) add('error', 'BAD_MOVE', 'כיוון תזוזה לא חוקי "' + l.move + '" (מותר L, R או S)', [], [t.id]);
          if (!bySym.has(l.read)) bySym.set(l.read, []);
          bySym.get(l.read).push(t.id);
        }
        for (const [sym, ts] of bySym) if (ts.length > 1) add('error', 'TM_NONDETERMINISTIC', 'מהמצב ' + s.name + ' יש ' + ts.length + ' כללים שקוראים ' + sym + '. במכונת טיורינג מותר כלל אחד.', [s.id], ts);
      }
    }

    for (const t of m.transitions) if (!t.labels.length) add('error', 'EMPTY_LABEL', 'למעבר ' + name(t.from) + ' → ' + name(t.to) + ' אין סמלים', [], [t.id]);

    if (!m.states.some((s) => s.accepting) && !(m.type === 'pda' && m.acceptBy === 'empty')) {
      add('warning', 'NO_ACCEPTING', 'אין מצב מקבל, ולכן אף מילה לא תתקבל.');
    }

    // מצבים שאי אפשר להגיע אליהם
    if (m.start && getState(m, m.start)) {
      const seen = new Set([m.start]);
      const stack = [m.start];
      while (stack.length) {
        const cur = stack.pop();
        for (const t of m.transitions) if (t.from === cur && !seen.has(t.to)) { seen.add(t.to); stack.push(t.to); }
      }
      const unreachable = m.states.filter((s) => !seen.has(s.id));
      if (unreachable.length) add('warning', 'UNREACHABLE', 'אי אפשר להגיע מהמצב ההתחלתי אל: ' + unreachable.map((s) => s.name).join(', '), unreachable.map((s) => s.id));
    }
    return issues;
  }

  /** בדיקת מילה לפני הרצה. מחזיר null או הודעה. */
  function checkWord(m, word) {
    const sigma = inputAlphabet(m);
    if (!sigma.length) return null;
    const set = new Set(sigma);
    const syms = symbols(word);
    for (let i = 0; i < syms.length; i++) {
      if (!set.has(syms[i])) return { index: i, symbol: syms[i], text: 'האות ' + syms[i] + ' (במקום ' + (i + 1) + ') אינה באלפבית Σ = {' + sigma.join(', ') + '}' };
    }
    return null;
  }

  /**
   * משלים אס"ד חלקי: מוסיף מצב "מלכודת" (לא מקבל) עם לולאה על כל Σ, ומעבר אליו מכל מצב שחסרה לו אות.
   * מחזיר את המצב החדש, או null אם לא חסר כלום.
   */
  function completeWithTrap(m) {
    const sigma = inputAlphabet(m);
    if (!sigma.length || !m.states.length) return null;
    const missing = [];
    for (const s of m.states) {
      const have = new Set();
      for (const t of m.transitions) if (t.from === s.id) for (const l of t.labels) have.add(l.read);
      const miss = sigma.filter((a) => !have.has(a));
      if (miss.length) missing.push({ s, miss });
    }
    if (!missing.length) return null;
    let name = 'מלכודת', k = 2;
    while (m.states.some((s) => s.name === name)) name = 'מלכודת' + k++;
    const xs = m.states.map((s) => s.x), ys = m.states.map((s) => s.y);
    const trap = M.addState(m, { name, x: Math.max(...xs) + 180, y: Math.round((Math.min(...ys) + Math.max(...ys)) / 2 / 20) * 20 + 140 });
    for (const { s, miss } of missing) M.addTransition(m, s.id, trap.id, miss.map((a) => ({ read: a })));
    M.addTransition(m, trap.id, trap.id, sigma.map((a) => ({ read: a })));
    return trap;
  }

  /**
   * מצבי מלכודת (לאפשרות התצוגה "הצג מצב מלכודת"): מצב לא מקבל שנקרא "מלכודת", או מצב מת:
   * לא מקבל, לא התחלתי, כל המעברים שיוצאים ממנו חוזרים אליו, ויש לו לולאה על כל אות של Σ (אז אי אפשר לצאת ממנו למצב מקבל).
   * באס"ד, אסל"ד ואוטומט מחסנית בלבד. מחזיר Set של מזהים.
   */
  function trapStates(m) {
    const out = new Set();
    if (m.type === 'tm' || !m.states.length) return out;
    const sigma = inputAlphabet(m);
    for (const s of m.states) {
      if (s.accepting || s.id === m.start) continue;
      const outs = m.transitions.filter((t) => t.from === s.id);
      if (s.name === 'מלכודת' && outs.every((t) => t.to === s.id)) { out.add(s.id); continue; }
      if (!outs.length || outs.some((t) => t.to !== s.id)) continue;
      const reads = new Set();
      for (const t of outs) for (const l of t.labels) reads.add(l.read);
      if (sigma.length && sigma.every((a) => reads.has(a))) out.add(s.id);
    }
    return out;
  }

  return { validate, checkWord, completeWithTrap, trapStates };
});
