/*
 * lang/build.js - "בנה אוטומט": מהנוסחה למכונה.
 *   רגולרית -> אס"ד מינימלי (ביטוי רגולרי / בלוקים עם תנאים על משתנה אחד / תכונות של מילה).
 *   חסרת הקשר -> אוטומט מחסנית (pda.js). מעבר לזה -> מכונת טיורינג למשפחה הקלאסית (tm.js).
 * כל מכונה נבדקת מול בדיקת השייכות (oracle) על כל המילים עד אורך מסוים לפני שהיא מוצגת. מכונה שנכשלת לא מוצגת.
 */
(function (root, f) {
  const node = typeof module === 'object' && module.exports;
  const L = node ? require('./ns.js') : root.AutomataLang;
  f(L, root.AutomataModel || (node ? require('../model.js') : null), root.AutomataSim || (node ? require('../sim.js') : null));
  if (node) module.exports = L;
})(typeof globalThis !== 'undefined' ? globalThis : this, function (L, M, S) {
  'use strict';
  const UNSUPPORTED = 'בנייה אוטומטית לשפה כזו עוד לא נתמכת. אפשר לבנות לבד, ולבדוק מול השפה.';
  const sym = (c) => ({ t: 'sym', c });
  const word = (w, times) => { const a = []; for (let i = 0; i < times; i++) for (const c of w) a.push(sym(c)); return a.length ? { t: 'cat', a } : { t: 'eps' }; };

  /** בלוקים רגולריים -> ביטוי רגולרי: איחוד על כל צירופי ה"מחלקות" של המשתנים שמקיימים את התנאי. */
  function blocksToRegex(ast, an) {
    const alts = [];
    let budget = 20000;
    for (const item of an.regular) {
      const blocks = ast.blocks.map((b) => (b.e.v && item.env[b.e.v] != null ? { word: b.word, e: { c: 0, v: null, d: b.e.c * item.env[b.e.v] + b.e.d } } : b));
      if (blocks.some((b) => !b.e.v && b.e.d < 0)) continue;
      const vars = Array.from(new Set(blocks.filter((b) => b.e.v).map((b) => b.e.v)));
      const { T, P } = L.classParams(item.cond, vars);
      for (const b of blocks) if (b.e.v && b.e.d < 0) T[b.e.v] = Math.max(T[b.e.v], Math.ceil(-b.e.d / b.e.c));
      const env = {};
      const rec = (i) => {
        if (i < vars.length) { for (let j = 0; j <= T[vars[i]] + P[vars[i]]; j++) { env[vars[i]] = j; rec(i + 1); } return; }
        if (--budget < 0) throw new Error('הנוסחה מורכבת מדי לבנייה אוטומטית');
        if (!L.evalC(item.cond, env, '')) return;
        const parts = [];
        for (const b of blocks) {
          const v = b.e.v, count = v ? b.e.c * env[v] + b.e.d : b.e.d;
          if (count < 0) return;
          parts.push(word(b.word, count));
          if (v && env[v] > T[v]) parts.push({ t: 'star', a: word(b.word, b.e.c * P[v]) });
        }
        alts.push({ t: 'cat', a: parts });
      };
      rec(0);
    }
    return alts.length ? { t: 'alt', a: alts } : { t: 'empty' };
  }

  /** תכונות רגולריות של מילה -> אס"ד: מכפלה של מוני מחלקות (לכל אות) ושל אס"דים קטנים ל"מתחילה / מסתיימת / מכילה". */
  function propToDFA(ast, an) {
    const A = ast.alphabet, cond = an.cond, vars = A.map((s) => '#' + s);
    const { T, P } = L.classParams(cond, vars);
    const any = { t: 'star', a: { t: 'any' } };
    const strs = [];
    for (const leaf of L.leaves(cond)) {
      const a = leaf.op === 'not' ? leaf.arg : leaf;
      if (a.op !== 'starts' && a.op !== 'ends' && a.op !== 'has') continue;
      const w = word(a.word, 1);
      const re = { t: 'cat', a: a.op === 'starts' ? [w, any] : a.op === 'ends' ? [any, w] : [any, w, any] };
      strs.push({ atom: a, dfa: L.minimize(L.determinize(L.nfaFromRegex(re), A)) });
    }
    const nC = A.length;
    const truth = (c, st) => {
      switch (c.op) {
        case 'true': return true;
        case 'false': return false;
        case 'and': return c.args.every((x) => truth(x, st));
        case 'or': return c.args.some((x) => truth(x, st));
        case 'not': return !truth(c.arg, st);
        case 'cmp': { const env = {}; vars.forEach((v, i) => { env[v] = st[i]; }); return L.evalC(c, env, ''); }
        default: { const k = strs.findIndex((s) => s.atom === c); return strs[k].dfa.acc[st[nC + k]]; }
      }
    };
    const bump = (j, v) => (j < T[v] ? j + 1 : T[v] + 1 + ((j - T[v]) % P[v]));
    const start = vars.map(() => 0).concat(strs.map((s) => s.dfa.start));
    const ids = new Map([[start.join(','), 0]]), states = [start], tr = [], acc = [];
    for (let i = 0; i < states.length; i++) {
      if (states.length > 20000) throw new Error('הנוסחה מורכבת מדי לבנייה אוטומטית');
      const st = states[i];
      acc[i] = truth(cond, st);
      tr[i] = A.map((c, ci) => {
        const nx = st.slice();
        nx[ci] = bump(st[ci], vars[ci]);
        strs.forEach((s, k) => { nx[nC + k] = s.dfa.tr[st[nC + k]][ci]; });
        const key = nx.join(',');
        if (!ids.has(key)) { ids.set(key, states.length); states.push(nx); }
        return ids.get(key);
      });
    }
    return { alphabet: A.slice(), n: states.length, start: 0, acc, tr };
  }

  /** אס"ד מינימלי לשפה רגולרית (לפי הסיווג). זורק שגיאה אם השפה לא סווגה כרגולרית. */
  function toDFA(ast) {
    const an = L.analyze(ast);
    if (an.cls !== 'regular') throw new Error('השפה לא סווגה כרגולרית');
    if (!ast._dfa) {
      const d = an.engine === 'vec' ? L.vecDFA(ast, an) : L.isCombined(ast) ? L.combinedDFA(ast, an) : ast.kind === 'prop' ? propToDFA(ast, an) : L.determinize(L.nfaFromRegex(ast.kind === 'regex' ? ast.re : blocksToRegex(ast, an)), ast.alphabet);
      Object.defineProperty(ast, '_dfa', { value: L.minimize(d), enumerable: false });
    }
    return ast._dfa;
  }

  /** לבלוקים: כל המילים מהצורה u1^e1 u2^e2 ... (כל צירופי החזקות) עד אורך maxLen. אלה המועמדות היחידות להיות בשפה. */
  function shapeWords(ast, maxLen, cap) {
    const out = [];
    const rec = (i, w) => {
      if (out.length >= cap) return;
      if (i === ast.blocks.length) { out.push(w); return; }
      const b = ast.blocks[i];
      if (!b.e.v && !b.e.gen) { rec(i + 1, w + b.word.repeat(b.e.d)); return; }
      for (let n = 0; w.length + n * b.word.length <= maxLen; n++) rec(i + 1, w + b.word.repeat(n));
    };
    rec(0, '');
    return out;
  }

  /**
   * משווה מכונה לנוסחה: על כל המילים עד אורך שנקבע לפי budget, ובבלוקים גם על כל המילים "בצורה הנכונה" עד אורך 12.
   * מחזיר { ok, maxLen, count, word? }.
   */
  function verify(machine, ast, budget) {
    const A = Array.from(new Set([...ast.alphabet, ...M.inputAlphabet(machine).filter((c) => c !== machine.blank)])).sort();
    const maxLen = L.lengthFor(A.length, budget || 2000, 12);
    const same = (w) => (S.run(machine, w, { record: false }).result === 'accept') === L.accepts(ast, w);
    let count = 0;
    for (const w of L.words(A, maxLen)) { count++; if (!same(w)) return { ok: false, word: w, maxLen, count }; }
    if (ast.kind === 'blocks') for (const w of shapeWords(ast, 12, 1500)) { if (w.length <= maxLen) continue; count++; if (!same(w)) return { ok: false, word: w, maxLen, count }; }
    return { ok: true, maxLen, count };
  }

  /**
   * בונה מכונה לנוסחה. מחזיר { ok: true, machine, kind, checked: {maxLen, count} } או { ok: false, reason }.
   * למכונה מצורפות בדיקות (המילים הקצרות בשפה ומחוצה לה), והשפה עצמה (machine.language).
   */
  /** המכונה לשפה, לפי הסיווג (בלי בדיקה עצמית). null אם הבנייה לא נתמכת. */
  function machineFor(ast) {
    const an = L.analyze(ast);
    if (an.cls === 'regular') return L.dfaToMachine(toDFA(ast));
    if (an.cls === 'cf' || an.cls === 'cfany') {
      if (an.engine === 'vec') return L.vecPDA(ast, an);
      if (L.isCombined(ast)) return L.combinedPDA(ast, an);
      let m = ast.kind === 'blocks' && an.links ? L.buildPDA(ast, an) : null;
      if (!m) m = L.buildExtraPDA(ast);
      if (!m && ast.kind === 'blocks') { const v = L.analyzeVec(ast); if (v && v.mode === 'C1') m = L.vecPDA(ast, v); }
      return m;
    }
    if (an.cls === 'noncf') return L.buildTM(ast, an);
    return null;
  }

  function build(ast) {
    const an = L.analyze(ast);
    let m = null;
    try { m = machineFor(ast); } catch (e) { return { ok: false, reason: e.message || UNSUPPORTED }; }
    if (!m) return { ok: false, reason: UNSUPPORTED };
    const v = verify(m, ast, m.type === 'dfa' ? 3000 : 700);
    if (!v.ok) return { ok: false, reason: UNSUPPORTED, selfCheckFailed: v.word };
    const smp = L.sample(ast, { nIn: 4, nOut: 3 });
    m.tests = smp.ins.map((w) => ({ word: w, expect: true })).concat(smp.outs.map((w) => ({ word: w, expect: false })));
    m.name = L.pretty(ast).text;
    m.language = { text: ast.text };
    return { ok: true, machine: m, kind: m.type, checked: { maxLen: v.maxLen, count: v.count } };
  }

  Object.assign(L, { toDFA, build, machineFor, verify, UNSUPPORTED });
});
