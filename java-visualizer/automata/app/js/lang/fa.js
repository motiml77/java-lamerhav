/*
 * lang/fa.js - אלגוריתמים על אוטומטים סופיים: ביטוי רגולרי -> אסל"ד (Thompson), אסל"ד -> אס"ד (תת-קבוצות),
 * מזעור (חלוקה למחלקות), השוואה בין שני אס"דים (מכפלה) עם הדוגמאות הנגדיות הקצרות ביותר, והמרה למכונה של הכלי.
 * אס"ד כאן: { alphabet, n, start, acc: [bool], tr: [[יעד לכל אות]] }, תמיד מלא (יש מצב מלכודת אם צריך).
 */
(function (root, f) {
  const node = typeof module === 'object' && module.exports;
  const L = node ? require('./ns.js') : root.AutomataLang;
  f(L, root.AutomataModel || (node ? require('../model.js') : null));
  if (node) module.exports = L;
})(typeof globalThis !== 'undefined' ? globalThis : this, function (L, M) {
  'use strict';
  const MAX_STATES = 3000;

  /** אסל"ד: { n, start, acc:Set, eps:[[...]], step:[Map(אות -> [...])] } */
  function nfaFromRegex(re) {
    const eps = [], step = [];
    const add = () => { eps.push([]); step.push(new Map()); return eps.length - 1; };
    const edge = (a, c, b) => { if (c == null) eps[a].push(b); else { if (!step[a].has(c)) step[a].set(c, []); step[a].get(c).push(b); } };
    function build(n) {          // מחזיר [כניסה, יציאה]
      const s = add(), e = add();
      switch (n.t) {
        case 'eps': edge(s, null, e); break;
        case 'empty': break;
        case 'sym': edge(s, n.c, e); break;
        case 'any': edge(s, '\u0000any', e); break;
        case 'cat': { let cur = s; for (const x of n.a) { const [a, b] = build(x); edge(cur, null, a); cur = b; } edge(cur, null, e); break; }
        case 'alt': for (const x of n.a) { const [a, b] = build(x); edge(s, null, a); edge(b, null, e); } break;
        case 'star': case 'plus': { const [a, b] = build(n.a); edge(s, null, a); edge(b, null, a); edge(b, null, e); if (n.t === 'star') edge(s, null, e); break; }
        default: throw new Error('regex node ' + n.t);
      }
      return [s, e];
    }
    const [s, e] = build(re);
    return { n: eps.length, start: s, acc: new Set([e]), eps, step };
  }

  /** אסל"ד מהמכונה של הכלי (אס"ד או אסל"ד, כולל מעברי ε). */
  function nfaFromMachine(m) {
    const idx = new Map(m.states.map((s, i) => [s.id, i]));
    const eps = m.states.map(() => []), step = m.states.map(() => new Map());
    for (const t of m.transitions) for (const l of t.labels) {
      const a = idx.get(t.from), b = idx.get(t.to);
      if (l.read === '') eps[a].push(b); else { if (!step[a].has(l.read)) step[a].set(l.read, []); step[a].get(l.read).push(b); }
    }
    return { n: m.states.length, start: idx.has(m.start) ? idx.get(m.start) : -1, acc: new Set(m.states.map((s, i) => (s.accepting ? i : -1)).filter((i) => i >= 0)), eps, step };
  }

  function determinize(nfa, alphabet) {
    const closure = (set) => { const st = [...set]; const seen = new Set(set); while (st.length) for (const y of nfa.eps[st.pop()]) if (!seen.has(y)) { seen.add(y); st.push(y); } return [...seen].sort((a, b) => a - b); };
    const key = (a) => a.join(',');
    const start = nfa.start < 0 ? [] : closure([nfa.start]);
    const ids = new Map([[key(start), 0]]), sets = [start], tr = [], acc = [];
    for (let i = 0; i < sets.length; i++) {
      if (sets.length > MAX_STATES) throw new Error('האוטומט גדול מדי');
      acc[i] = sets[i].some((s) => nfa.acc.has(s));
      tr[i] = alphabet.map((c) => {
        const nx = new Set();
        for (const s of sets[i]) { for (const y of nfa.step[s].get(c) || []) nx.add(y); for (const y of nfa.step[s].get('\u0000any') || []) nx.add(y); }
        const cl = closure(nx), k = key(cl);
        if (!ids.has(k)) { ids.set(k, sets.length); sets.push(cl); }
        return ids.get(k);
      });
    }
    return { alphabet: alphabet.slice(), n: sets.length, start: 0, acc, tr };
  }

  /** אס"ד מינימלי (ישיג, ממוספר מחדש לפי BFS מההתחלה). */
  function minimize(d) {
    const k = d.alphabet.length;
    const order = [d.start], seen = new Set(order);
    for (let i = 0; i < order.length; i++) for (const y of d.tr[order[i]]) if (!seen.has(y)) { seen.add(y); order.push(y); }
    let block = new Map(order.map((s) => [s, d.acc[s] ? 1 : 0]));
    for (;;) {
      const sig = new Map(), next = new Map();
      for (const s of order) { const g = block.get(s) + '|' + d.tr[s].map((y) => block.get(y)).join(','); if (!sig.has(g)) sig.set(g, sig.size); next.set(s, sig.get(g)); }
      const same = sig.size === new Set(block.values()).size;
      block = next;
      if (same) break;
    }
    const id = new Map(), reps = [];
    for (const s of order) { const b = block.get(s); if (!id.has(b)) { id.set(b, reps.length); reps.push(s); } }
    return { alphabet: d.alphabet.slice(), n: reps.length, start: 0, acc: reps.map((s) => !!d.acc[s]), tr: reps.map((s) => d.tr[s].map((y) => id.get(block.get(y)))), k };
  }

  function dfaAccepts(d, word) {
    let s = d.start;
    for (const c of word) { const i = d.alphabet.indexOf(c); if (i < 0) return false; s = d.tr[s][i]; }
    return d.acc[s];
  }

  /** מרחיב אס"ד לאלפבית גדול יותר (אותיות חדשות מובילות למלכודת). */
  function widen(d, alphabet) {
    if (alphabet.every((c) => d.alphabet.includes(c)) && alphabet.length === d.alphabet.length) return d;
    const dead = d.n;
    const tr = d.tr.map((row) => alphabet.map((c) => { const i = d.alphabet.indexOf(c); return i < 0 ? dead : row[i]; }));
    tr.push(alphabet.map(() => dead));
    return { alphabet: alphabet.slice(), n: d.n + 1, start: d.start, acc: d.acc.concat([false]), tr };
  }

  /**
   * משווה שני אס"דים. מחזיר { equal, missing: [מילים ב-A ולא ב-B], extra: [ב-B ולא ב-A] }, הקצרות ביותר קודם, עד limit מכל סוג.
   * אם equal = true זו הוכחה: עברנו על כל הזוגות הישיגים במכפלה.
   */
  function compare(A, B, limit) {
    const alphabet = Array.from(new Set([...A.alphabet, ...B.alphabet])).sort();
    A = widen(A, alphabet); B = widen(B, alphabet);
    const k = alphabet.length, id = (a, b) => a * B.n + b;
    const start = id(A.start, B.start), order = [start], seen = new Set(order), back = new Map();
    for (let i = 0; i < order.length; i++) {
      const a = Math.floor(order[i] / B.n), b = order[i] % B.n;
      for (let c = 0; c < k; c++) { const y = id(A.tr[a][c], B.tr[b][c]); if (!back.has(y)) back.set(y, []); back.get(y).push(order[i]); if (!seen.has(y)) { seen.add(y); order.push(y); } }
    }
    const list = (inA) => {
      const isT = (p) => (inA ? A.acc[Math.floor(p / B.n)] && !B.acc[p % B.n] : !A.acc[Math.floor(p / B.n)] && B.acc[p % B.n]);
      const dist = new Map(), q = order.filter(isT);
      q.forEach((p) => dist.set(p, 0));
      for (let i = 0; i < q.length; i++) for (const y of back.get(q[i]) || []) if (!dist.has(y)) { dist.set(y, dist.get(q[i]) + 1); q.push(y); }
      const out = [];
      if (!dist.has(start)) return out;
      let budget = 200000;
      const dfs = (p, w, D) => {
        if (out.length >= limit || budget-- <= 0) return;
        if (w.length === D) { if (isT(p)) out.push(w); return; }
        const a = Math.floor(p / B.n), b = p % B.n;
        for (let c = 0; c < k; c++) { const y = id(A.tr[a][c], B.tr[b][c]); if (dist.has(y) && w.length + 1 + dist.get(y) <= D) dfs(y, w + alphabet[c], D); }
      };
      for (let D = dist.get(start); out.length < limit && D <= dist.get(start) + 8 && budget > 0; D++) dfs(start, '', D);
      return out;
    };
    const missing = list(true), extra = list(false);
    return { equal: !missing.length && !extra.length, missing, extra };
  }

  /** אס"ד -> מכונה של הכלי, עם שמות q0, q1... ומצב "מלכודת", ופריסה בשכבות. */
  function dfaToMachine(d, name) {
    const m = M.createMachine('dfa', { name: name || 'אס"ד מהשפה', alphabet: d.alphabet });
    const canAccept = new Array(d.n).fill(false);
    for (let changed = true; changed;) { changed = false; for (let s = 0; s < d.n; s++) if (!canAccept[s] && (d.acc[s] || d.tr[s].some((y) => canAccept[y]))) { canAccept[s] = true; changed = true; } }
    let q = 0;
    const ids = [];
    for (let s = 0; s < d.n; s++) ids.push(M.addState(m, { id: 's' + s, name: canAccept[s] || s === d.start ? 'q' + q++ : 'מלכודת', accepting: d.acc[s] }).id);
    m.start = ids[d.start];
    for (let s = 0; s < d.n; s++) d.alphabet.forEach((c, i) => M.addTransition(m, ids[s], ids[d.tr[s][i]], [{ read: c }]));
    layout(m);
    return m;
  }

  /** פריסה: שכבות BFS בלי מצב המלכודת, והמלכודת מתחת לאמצע. */
  function layout(m) {
    const trap = m.states.find((s) => s.name === 'מלכודת');
    if (trap) {
      const keep = m.transitions;
      m.transitions = keep.filter((t) => t.from !== trap.id && t.to !== trap.id);
      m.states = m.states.filter((s) => s !== trap);
      M.autoLayout(m, { gapX: 180, gapY: 140 });
      const xs = m.states.map((s) => s.x), ys = m.states.map((s) => s.y);
      trap.x = Math.round((Math.min(...xs) + Math.max(...xs)) / 2 / 20) * 20;
      trap.y = Math.max(...ys) + 180;
      m.states.push(trap);
      m.transitions = keep;
    } else M.autoLayout(m, { gapX: 180, gapY: 140 });
    for (const s of m.states) { s.x = Math.round(s.x / 20) * 20; s.y = Math.round(s.y / 20) * 20; }
  }

  Object.assign(L, { nfaFromRegex, nfaFromMachine, determinize, minimize, dfaAccepts, compare, dfaToMachine, layoutMachine: layout });
});
