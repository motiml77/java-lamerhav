/*
 * lang/pda.js - בניית אוטומט מחסנית לשפות בלוקים חסרות הקשר: זוגות של בלוקים מקושרים (aⁿ…bᵐ עם a·n − b·m REL k,
 * או אותו משתנה בשני בלוקים), מקוננים או עוקבים, ועוד תנאים על משתנה בודד (n ≥ 2, n זוגי).
 *
 * הרעיון: הבלוק הפותח דוחף a אסימונים (A) לכל יחידה, הבלוק הסוגר שולף b לכל יחידה. מתחת לאסימונים יש סמן (Z או M).
 *   =   בסוף שולפים בדיוק k (או דוחפים |k| מראש אם k שלילי), ואז הסמן חייב להיות בראש.
 *   ≥   כמו =, ואז מותר לשלוף את כל מה שנשאר.
 *   ≤   כמו =, אבל כל שליפה בבלוק הסוגר היא רשות (אי-דטרמיניזם).
 *   ≠   איחוד של שני אוטומטים: > ו-<.
 * מחזיר מכונה, או null אם הנוסחה מחוץ למשפחה הזו.
 */
(function (root, f) {
  const node = typeof module === 'object' && module.exports;
  const L = node ? require('./ns.js') : root.AutomataLang;
  f(L, root.AutomataModel || (node ? require('../model.js') : null));
  if (node) module.exports = L;
})(typeof globalThis !== 'undefined' ? globalThis : this, function (L, M) {
  'use strict';

  /** האוטומט האונרי של משתנה: אילו ערכים מותרים לפי התנאים שעליו בלבד. מחזיר { next: [], acc: [] } מינימלי. */
  function unary(v, atoms) {
    const c = atoms.length ? { op: 'and', args: atoms } : { op: 'true' };
    const { T, P } = L.classParams(c, [v]);
    const n = T[v] + P[v] + 1;
    const acc = [], tr = [];
    for (let j = 0; j < n; j++) { acc.push(L.evalC(c, { [v]: j }, '')); tr.push([j < T[v] ? j + 1 : T[v] + 1 + ((j - T[v]) % P[v])]); }
    const d = L.minimize({ alphabet: ['1'], n, start: 0, acc, tr });
    return { next: d.tr.map((r) => r[0]), acc: d.acc };
  }

  function buildVariant(ast, links, perVar, B) {
    const add = () => B.n++;
    const rule = (from, to, read, pop, push) => B.rules.push({ from, to, read: read || '', pop: pop || '', push: push || '' });
    let cur = null, start = null, pending = [], open = 0;
    const flushTo = (target) => {
      if (cur === null && !pending.length) { start = target; return; }
      if (cur === null) { start = add(); cur = [start]; }
      const ops = pending.length ? pending : [{}];
      ops.forEach((op, i) => {
        if (op.loop) { const s = add(); for (const c of cur) rule(c, s); rule(s, s, '', op.loop, ''); cur = [s]; return; }
        const dest = i === ops.length - 1 ? target : add();
        for (const c of cur) { rule(c, dest, '', op.pop, op.push); if (op.optional) rule(c, dest); }
        cur = [dest];
      });
      pending = [];
    };
    /** קורא את האותיות seq מ-from אל to, עם פעולת המחסנית של היחידה (דחיפה באות הראשונה / שליפות). */
    const path = (from, to, seq, role) => {
      const pops = role && role.pop ? role.pop : 0;
      let s = from;
      seq.forEach((ch, i) => {
        const dest = i === seq.length - 1 && pops <= 1 ? to : add();
        if (i === 0 && role && role.push) rule(s, dest, ch, '', 'A'.repeat(role.push));
        else if (i === 0 && pops) { rule(s, dest, ch, 'A', ''); if (role.optional) rule(s, dest, ch, '', ''); }
        else rule(s, dest, ch, '', '');
        s = dest;
      });
      for (let p = 1; p < pops; p++) { const dest = p === pops - 1 ? to : add(); rule(s, dest, '', 'A', ''); if (role.optional) rule(s, dest, '', '', ''); s = dest; }
    };
    const letters = (w, times) => Array.from(w.repeat(times));

    ast.blocks.forEach((b, i) => {
      const opening = links.find((l) => l.i === i), closing = links.find((l) => l.j === i);
      let role = null;
      if (opening) {
        opening.marker = open === 0 ? 'Z' : 'M';
        if (opening.marker === 'M') pending.push({ pop: '', push: 'M' });
        if (opening.k < 0) pending.push({ pop: '', push: 'A'.repeat(-opening.k) });
        open++;
        role = { push: opening.a };
      } else if (closing) role = { pop: closing.b, optional: closing.rel === '<=' };
      const e = b.e;
      if (!e.v) {
        if (e.d > 0) { const a = add(), z = add(); path(a, z, letters(b.word, e.d), null); flushTo(a); cur = [z]; }
      } else {
        const U = closing && closing.shared ? { next: [0], acc: [true] } : perVar[e.v];
        const nodes = U.next.map(() => add());
        U.next.forEach((nx, u) => path(nodes[u], nodes[nx], letters(b.word, e.c), role));
        let entry = nodes[0];
        if (e.d > 0) { entry = add(); path(entry, nodes[0], letters(b.word, e.d), null); }
        flushTo(entry);
        cur = nodes.filter((_, u) => U.acc[u]);
      }
      if (closing) {
        for (let k = 0; k < closing.k; k++) pending.push({ pop: 'A', push: '', optional: closing.rel === '<=' });   // ב-≤ מותר שיישארו עד k
        if (closing.rel === '>=') pending.push({ loop: 'A' });
        pending.push(closing.marker === 'Z' ? { pop: 'Z', push: 'Z' } : { pop: 'M', push: '' });
        open--;
      }
    });
    if (cur === null) { start = add(); cur = [start]; }
    if (pending.length) { const fin = add(); flushTo(fin); cur = [fin]; }
    for (const s of cur) B.acc.add(s);
    return start;
  }

  function buildPDA(ast, an) {
    if (ast.kind !== 'blocks' || !an.links) return null;
    if (ast.blocks.some((b) => b.e.d < 0)) return null;
    // התנאים: קשרים (מהסיווג), או תנאי על משתנה אחד בלבד
    const linkAtoms = new Set(an.links.map((l) => l.atom).filter(Boolean));
    const byVar = {};
    for (const a of L.conjuncts(an.cond)) {
      if (a.op === 'true' || linkAtoms.has(a)) continue;
      const inf = L.info(a);
      if (inf.vAll.size === 0) { if (!L.evalC(a, {}, '')) return null; continue; }
      if (inf.vAll.size !== 1) return null;
      const v = [...inf.vAll][0];
      (byVar[v] = byVar[v] || []).push(a);
    }
    const perVar = {};
    for (const b of ast.blocks) if (b.e.v && !perVar[b.e.v]) perVar[b.e.v] = unary(b.e.v, byVar[b.e.v] || []);
    // ≠ מתפצל לשתי גרסאות; > ו-< הופכים ל-≥ ול-≤
    let variants = [[]];
    for (const l of an.links) {
      const base = l.shared ? { i: l.i, j: l.j, a: 1, b: 1, k: 0, shared: l.shared } : { i: l.i, j: l.j, a: l.a, b: l.b, k: l.k };
      const opts = l.shared || l.rel === '=' ? [{ rel: '=' }] : l.rel === '>=' ? [{ rel: '>=' }] : l.rel === '>' ? [{ rel: '>=', k: l.k + 1 }] : l.rel === '<=' ? [{ rel: '<=' }] : l.rel === '<' ? [{ rel: '<=', k: l.k - 1 }] : [{ rel: '>=', k: l.k + 1 }, { rel: '<=', k: l.k - 1 }];
      variants = variants.flatMap((vs) => opts.map((o) => vs.concat([Object.assign({}, base, o)])));
    }
    if (variants.length > 4) return null;
    const B = { n: 0, rules: [], acc: new Set() };
    let start;
    if (variants.length === 1) start = buildVariant(ast, variants[0], perVar, B);
    else { start = B.n++; for (const vs of variants) { const s = buildVariant(ast, vs, perVar, B); B.rules.push({ from: start, to: s, read: '', pop: '', push: '' }); } }

    // למכונה של הכלי: רק מצבים ישיגים, ממוספרים לפי BFS
    const out = new Map();
    for (const r of B.rules) { if (!out.has(r.from)) out.set(r.from, []); out.get(r.from).push(r); }
    const order = [start], seen = new Set(order);
    for (let i = 0; i < order.length; i++) for (const r of out.get(order[i]) || []) if (!seen.has(r.to)) { seen.add(r.to); order.push(r.to); }
    const m = M.createMachine('pda', { alphabet: ast.alphabet, stackAlphabet: ['Z', 'A', 'M'], stackStart: 'Z', acceptBy: 'final' });
    const id = new Map(order.map((s, i) => [s, M.addState(m, { id: 's' + i, name: 'q' + i, accepting: B.acc.has(s) }).id]));
    m.start = id.get(start);
    for (const s of order) for (const r of out.get(s) || []) M.addTransition(m, id.get(r.from), id.get(r.to), [{ read: r.read, pop: r.pop, push: r.push }]);
    L.layoutMachine(m);
    return m;
  }

  L.buildPDA = buildPDA;
});
