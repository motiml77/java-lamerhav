/*
 * lang/vec-build.js - בנייה לשפות שסווגו ב-vec.js:
 *   R1 / R3 -> אס"ד מינימלי: ביטוי רגולרי שהוא איחוד, על צירופי המחלקות שמקיימים את התנאי, של u_i^(קבוע) (u_i^(c·P))*.
 *   C1      -> אוטומט מחסנית. כל בלוק מתפרק לתתי-בלוקים (uⁿ⁺ᵐ = uᵐuⁿ, כי זו אותה מילה), בסדר שמתאים למחסנית:
 *              קודם המשתנים שנסגרים כאן (האחרון שנדחף נשלף ראשון), אחר כך הקבוע והמשתנים החופשיים, ובסוף המשתנים שנפתחים כאן
 *              (זה שנסגר הכי מאוחר נדחף ראשון). משתנה שנפתח דוחף סמל משלו על כל יחידה ועוקב אחרי המחלקה שלו (אוטומט אונרי),
 *              ומשתנה שנסגר שולף את הסמל שלו על כל יחידה. בסוף המחסנית חייבת להיות ריקה (Z בראש).
 * מחזיר אס"ד / מכונה, או null אם הבנייה לא נתמכת. המכונה עוד נבדקת מול השפה ב-build.js.
 */
(function (root, f) {
  const node = typeof module === 'object' && module.exports;
  const L = node ? require('./ns.js') : root.AutomataLang;
  f(L, root.AutomataModel || (node ? require('../model.js') : null));
  if (node) module.exports = L;
})(typeof globalThis !== 'undefined' ? globalThis : this, function (L, M) {
  'use strict';
  const sym = (c) => ({ t: 'sym', c });
  const word = (w, times) => { const a = []; for (let i = 0; i < times; i++) for (const c of w) a.push(sym(c)); return a.length ? { t: 'cat', a } : { t: 'eps' }; };
  const MAX_COMBOS = 24;

  /** הביטוי הרגולרי לשפה רגולרית (R1 / R3). */
  function vecRegex(an) {
    const { nf, T, P, combos } = an, exact = an.exact || {};
    const vars = Object.keys(T), alts = [];
    const emit = (env) => {
      const parts = [];
      for (const b of nf.blocks) {
        let count = b.f.k;
        for (const v in b.f.co) count += b.f.co[v] * env[v];
        if (count < 0) return;
        parts.push(word(b.word, count));
        for (const v in b.f.co) if (exact[v] == null && env[v] > T[v]) parts.push({ t: 'star', a: word(b.word, b.f.co[v] * P[v]) });
      }
      alts.push({ t: 'cat', a: parts });
    };
    const multi = Object.keys(exact);
    if (!multi.length) combos.forEach(emit);
    else {                // R3: המשתנים שבכמה בלוקים מקבלים ערכים מדויקים עד החסם, השאר לפי מחלקות
      const env = {};
      let budget = 20000;
      const rec = (i) => {
        if (--budget < 0) throw new Error('הנוסחה מורכבת מדי לבנייה אוטומטית');
        if (i === vars.length) { if (L.evalC(nf.cond, env, '')) emit(env); return; }
        const v = vars[i], top = exact[v] != null ? exact[v] - 1 : T[v] + P[v];
        for (let j = 0; j <= top; j++) { env[v] = j; rec(i + 1); }
      };
      rec(0);
    }
    return alts.length ? { t: 'alt', a: alts } : { t: 'empty' };
  }
  const vecDFA = (ast, an) => L.minimize(L.determinize(L.nfaFromRegex(vecRegex(an)), ast.alphabet));

  /* ---------- אוטומט מחסנית ---------- */
  /** אוטומט אונרי { next, acc } (next = -1: אין המשך) לערכים שמקיימים c, בלי צמתים שלא מגיעים לקבלה. */
  function unary(v, c) {
    const { T, P } = L.classParams(c, [v]);
    const n = T[v] + P[v] + 1, acc = [], tr = [];
    for (let j = 0; j < n; j++) { acc.push(L.evalC(c, { [v]: j }, '')); tr.push([j < T[v] ? j + 1 : T[v] + 1 + ((j - T[v]) % P[v])]); }
    return prune(L.minimize({ alphabet: ['1'], n, start: 0, acc, tr }));
  }
  /** האוטומט של מחלקה אחת: ערך מדויק j, או j, j+P, j+2P, ... */
  function classAuto(j, periodic, P) {
    const next = [], acc = [];
    const n = periodic ? j + P : j + 1;
    for (let u = 0; u < n; u++) { next.push(u + 1 < n ? u + 1 : periodic ? j : -1); acc.push(u === j); }
    return { next, acc };
  }
  function prune(d) {
    const next = d.tr.map((r) => r[0]), good = d.acc.slice();
    for (let changed = true; changed;) { changed = false; next.forEach((x, u) => { if (!good[u] && good[x]) { good[u] = true; changed = true; } }); }
    return { next: next.map((x, u) => (good[u] && good[x] ? x : -1)), acc: d.acc.slice(), good };
  }

  /** אוטומט מחסנית אחד, כשלכל משתנה נתון אוטומט אונרי (auto[v]) וחלק מהמשתנים הם קבועים (fixed[v]). */
  function buildOne(an, auto, fixed, B, syms) {
    const { nf, supp } = an;
    const add = () => B.n++;
    const rule = (from, to, read, pop, push) => B.rules.push({ from, to, read: read || '', pop: pop || '', push: push || '' });
    const path = (from, to, w, op) => {
      const letters = Array.from(w);
      let s = from;
      letters.forEach((ch, i) => {
        const d = i === letters.length - 1 ? to : add();
        rule(s, d, ch, i === 0 && op && op.pop, i === 0 && op && op.push);
        s = d;
      });
    };
    const vars = Object.keys(auto).filter((v) => fixed[v] == null);
    const stackVars = vars.filter((v) => supp[v].length === 2);
    const rank = (v) => vars.indexOf(v);
    for (const v of vars) if (!supp[v].length && !auto[v].acc.some((x, u) => x && (auto[v].good ? auto[v].good[u] : true))) return null;
    const start = add();
    let cur = [start], fresh = true;                // fresh: המצב הנוכחי עוד בלי מעברים יוצאים, אז אפשר להתחיל ממנו לולאה בלי מעבר ε
    const join = () => { if (cur.length === 1) return cur[0]; const s = add(); for (const c of cur) rule(c, s); return s; };
    const entry = () => { if (fresh && cur.length === 1) { fresh = false; return cur[0]; } const s = add(); rule(join(), s); return s; };
    const loopVar = (v, w, op) => {                // משתנה שנפתח או חופשי: הולכים לפי האוטומט האונרי שלו
      const U = auto[v], nodes = U.next.map((_, u) => (u === 0 ? entry() : add()));
      U.next.forEach((x, u) => { if (x >= 0) path(nodes[u], nodes[x], w, op); });
      cur = nodes.filter((_, u) => U.acc[u]);
      if (!cur.length) cur = [add()];               // אין ערך מותר: מצב שלא ממשיכים ממנו
    };
    nf.blocks.forEach((b, i) => {
      const closing = stackVars.filter((v) => supp[v][1] === i).sort((x, y) => supp[y][0] - supp[x][0] || rank(y) - rank(x));
      const opening = stackVars.filter((v) => supp[v][0] === i).sort((x, y) => supp[y][1] - supp[x][1] || rank(x) - rank(y));
      const free = vars.filter((v) => supp[v].length === 1 && supp[v][0] === i);
      for (const v of closing) {
        const s = entry();
        path(s, s, b.word.repeat(b.f.co[v]), { pop: syms[v] });
        cur = [s];
      }
      let k = b.f.k;
      for (const v in fixed) k += (b.f.co[v] || 0) * fixed[v];
      if (k > 0) { const t = add(); path(join(), t, b.word.repeat(k)); cur = [t]; fresh = true; }
      for (const v of free) loopVar(v, b.word.repeat(b.f.co[v]), null);
      for (const v of opening) loopVar(v, b.word.repeat(b.f.co[v]), { push: syms[v] });
    });
    const fin = add();
    rule(join(), fin, '', 'Z', 'Z');
    B.acc.add(fin);
    return start;
  }

  function assemble(B, start, alphabet, stack) {
    const out = new Map();
    for (const r of B.rules) { if (!out.has(r.from)) out.set(r.from, []); out.get(r.from).push(r); }
    const order = [start], seen = new Set(order);
    for (let i = 0; i < order.length; i++) for (const r of out.get(order[i]) || []) if (!seen.has(r.to)) { seen.add(r.to); order.push(r.to); }
    const m = M.createMachine('pda', { alphabet, stackAlphabet: ['Z'].concat(stack), stackStart: 'Z', acceptBy: 'final' });
    const id = new Map(order.map((s, i) => [s, M.addState(m, { id: 's' + i, name: 'q' + i, accepting: B.acc.has(s) }).id]));
    m.start = id.get(start);
    for (const s of order) for (const r of out.get(s) || []) M.addTransition(m, id.get(r.from), id.get(r.to), [{ read: r.read, pop: r.pop, push: r.push }]);
    L.layoutMachine(m);
    return m;
  }

  /** אוטומט מחסנית לשפה שסווגה C1. */
  function vecPDA(ast, an) {
    const { nf, T, P, combos, supp, U } = an;
    if (an.mode !== 'C1' || nf.blocks.some((b) => b.f.k < 0)) return null;
    const vars = Object.keys(T);
    // סמל מחסנית לכל משתנה שקושר שני בלוקים: האות הגדולה של שמו, אם היא פנויה
    const used = new Set(['Z'].concat(ast.alphabet)), syms = {};
    for (const v of vars) {
      if (supp[v].length !== 2) continue;
      const up = v.toUpperCase();
      syms[v] = /^[A-Y]$/.test(up) && !used.has(up) ? up : Array.from('ABCDEFGHIJKLMNOPQRSTUVWXY').find((x) => !used.has(x));
      used.add(syms[v]);
    }
    // משתנה חסום שלא מתאים למחסנית (שלושה בלוקים, או הצטלבות): מקבל ערך מדויק בכל צירוף
    const cross = (A, Bb) => A.length === 2 && Bb.length === 2 && ((A[0] < Bb[0] && Bb[0] < A[1] && A[1] < Bb[1]) || (Bb[0] < A[0] && A[0] < Bb[1] && Bb[1] < A[1]));
    const bad = vars.filter((v) => !U.includes(v) && (supp[v].length > 2 || vars.some((u) => cross(supp[v], supp[u]))));
    const atoms = L.conjuncts(nf.cond).filter((a) => a.op !== 'true');
    const single = !bad.length && atoms.every((a) => a.op === 'cmp' && L.info(a).vAll.size <= 1);
    const B = { n: 0, rules: [], acc: new Set() };
    let start;
    if (single) {
      const auto = {};
      for (const v of vars) auto[v] = unary(v, { op: 'and', args: atoms.filter((a) => L.info(a).vAll.has(v)).concat([{ op: 'true' }]) });
      start = buildOne(an, auto, {}, B, syms);
      if (start == null) return null;
    } else {
      if (combos.length > MAX_COMBOS) return null;
      start = B.n++;
      for (const c of combos) {
        const auto = {}, fixed = {};
        for (const v of vars) { auto[v] = classAuto(c[v], c[v] > T[v], P[v]); if (bad.includes(v)) fixed[v] = c[v]; }
        const s = buildOne(an, auto, fixed, B, syms);
        if (s != null) B.rules.push({ from: start, to: s, read: '', pop: '', push: '' });
      }
    }
    return assemble(B, start, ast.alphabet, Object.values(syms));
  }

  Object.assign(L, { vecRegex, vecDFA, vecPDA, assemblePDA: assemble });
});
