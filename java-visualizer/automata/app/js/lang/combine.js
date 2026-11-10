/*
 * lang/combine.js - איחוד ושרשור של שפות:  { aⁿbⁿ } ∪ { bⁿaⁿ }   ·   { aⁿbⁿ } · { cᵏ }  (או { aⁿbⁿ }{ cᵏ }).
 * כל חלק הוא נוסחה רגילה, בכל אחת מהצורות, עם משתנים משלו. ∪ חלש משרשור. ∪ בין ביטויים רגולריים נשאר ביטוי רגולרי.
 *   AST: { kind: 'union' | 'concat', parts: [ast], alphabet, flat? }   flat: שרשור של בלוקים הוא שפת בלוקים אחת (המשתנים מופרדים).
 * סיווג: רגולרית ∪ / · רגולרית = רגולרית. חסרת הקשר ∪ / · חסרת הקשר = חסרת הקשר. "לא רגולרית" / "לא חסרת הקשר" באיחוד
 * רק עם הוכחה: חותכים עם המעטפת u₁*u₂*…u_k* של חלק שהוכח עליו, ובודקים שמשאר החלקים נשאר שם רק מספר סופי של מילים.
 */
(function (root, f) {
  const node = typeof module === 'object' && module.exports;
  const L = node ? require('./ns.js') : root.AutomataLang;
  f(L, root.AutomataModel || (node ? require('../model.js') : null));
  if (node) module.exports = L;
})(typeof globalThis !== 'undefined' ? globalThis : this, function (L, M) {
  'use strict';
  const isComb = (a) => a.kind === 'union' || a.kind === 'concat';
  const sym = (c) => ({ t: 'sym', c });
  const word = (w, n) => { const a = []; for (let i = 0; i < n; i++) for (const c of w) a.push(sym(c)); return a.length ? { t: 'cat', a } : { t: 'eps' }; };
  const R = (cls, why, extra) => Object.assign({ cls, why }, extra);
  const WHY = {
    reg: 'איחוד ושרשור של שפות רגולריות הם רגולריים',
    cf: 'איחוד ושרשור של שפות חסרות הקשר הם חסרי הקשר. חלק אחד לא רגולרי, ושאר החלקים לא מוסיפים לו אלא מספר סופי של מילים מאותה צורה',
    cfany: 'איחוד ושרשור של שפות חסרות הקשר הם חסרי הקשר. לא הוכחתי שהשפה לא רגולרית',
    noncf: 'חלק אחד לא חסר הקשר, ושאר החלקים לא מוסיפים לו אלא מספר סופי של מילים מאותה צורה',
    unk: 'הנוסחה חוקית ואפשר לבדוק מולה, אבל אין לי הוכחה לאיזו משפחה היא שייכת',
  };

  /* ---------- פענוח ---------- */
  function parseCombined(text, toks, opts) {
    let depth = 0;
    const cuts = { union: [], concat: [] };
    toks.forEach((t, i) => {
      if (t.t === '(' || t.t === '{') depth++;
      else if (t.t === ')' || t.t === '}') depth--;
      if (depth) return;
      const prev = toks[i - 1], next = toks[i + 1];
      if (t.t === 'union') cuts.union.push([t.pos, t.end]);
      else if (t.t === 'cdot' && prev && prev.t === '}' && next && next.t === '{') cuts.concat.push([t.pos, t.end]);
      else if (t.t === '}' && next && next.t === '{') cuts.concat.push([t.end, t.end]);
    });
    const op = cuts.union.length ? 'union' : cuts.concat.length ? 'concat' : null;
    if (!op) return null;
    const parts = [];
    let from = 0;
    for (const [a, b] of cuts[op].concat([[text.length, text.length]])) { parts.push({ text: text.slice(from, a), offset: from, at: a }); from = b; }
    for (const p of parts) if (!p.text.trim()) throw new L.LangError('חסרה שפה ' + (p.offset === 0 ? 'לפני' : 'אחרי') + ' ה' + (op === 'union' ? '∪' : 'שרשור'), p.at, p.at + 1, 'למשל { aⁿbⁿ } ∪ { bⁿaⁿ }');
    if (op === 'union' && parts.every((p) => { const u = L.unwrap(p.text); try { return L.styleOf(L.lex(u.text)) === 'regex'; } catch (e) { return false; } })) return null;
    const asts = parts.map((p) => {
      try { return L.parse(p.text, opts); } catch (e) { if (e instanceof L.LangError) { e.pos += p.offset; e.end += p.offset; } throw e; }
    });
    const alphabet = Array.from(new Set(asts.flatMap((a) => a.alphabet)));
    const ast = { kind: op, parts: asts, alphabet };
    if (op === 'concat' && asts.every((a) => a.kind === 'blocks')) ast.flat = flatten(asts);
    return ast;
  }

  /* שרשור של שפות בלוקים: אותם בלוקים ברצף, המשתנים של כל חלק מקבלים שם משלו, והתנאים מתחברים ב"וגם". */
  const renA = (a, m) => (a.t === 'var' ? { t: 'var', n: m[a.n] || a.n } : a.t === 'num' ? a : Object.assign({}, a, a.b ? { a: renA(a.a, m), b: renA(a.b, m) } : { a: renA(a.a, m) }));
  function renC(c, m) {
    if (!c) return c;
    if (c.op === 'and' || c.op === 'or') return { op: c.op, args: c.args.map((x) => renC(x, m)) };
    if (c.op === 'not') return { op: 'not', arg: renC(c.arg, m) };
    if (c.op === 'cmp') return { op: 'cmp', rel: c.rel, l: renA(c.l, m), r: renA(c.r, m) };
    return c;
  }
  function renE(e, m) {
    if (e.gen) {
      const lin = e.lin && { k: e.lin.k, co: {} };
      if (lin) for (const v in e.lin.co) lin.co[m[v] || v] = e.lin.co[v];
      return { gen: true, t: renA(e.t, m), lin, vars: e.vars.map((v) => m[v] || v) };
    }
    return e.v ? Object.assign({}, e, { v: m[e.v] || e.v }) : e;
  }
  function flatten(asts) {
    const blocks = [], vars = [], conds = [];
    asts.forEach((a, i) => {
      const m = {};
      for (const b of a.blocks) for (const v of b.e.gen ? b.e.vars : b.e.v ? [b.e.v] : []) if (i) m[v] = v + '·' + i;
      for (const b of a.blocks) blocks.push({ word: b.word, e: renE(b.e, m) });
      for (const v of a.vars) vars.push(m[v] || v);
      if (a.cond) conds.push(renC(a.cond, m));
    });
    return { kind: 'blocks', blocks, cond: conds.length ? (conds.length === 1 ? conds[0] : { op: 'and', args: conds }) : null, vars, alphabet: Array.from(new Set(blocks.map((b) => b.word).join(''))), gen: blocks.some((b) => b.e.gen) };
  }

  /* ---------- שייכות וכתיבה יפה ---------- */
  function combinedAccepts(ast, w) {
    if (ast.flat) return L.accepts(ast.flat, w);
    if (ast.kind === 'union') return ast.parts.some((p) => L.accepts(p, w));
    const go = (i, pos) => (i === ast.parts.length ? pos === w.length : Array.from({ length: w.length - pos + 1 }, (_, k) => pos + k).some((e) => L.accepts(ast.parts[i], w.slice(pos, e)) && go(i + 1, e)));
    return go(0, 0);
  }
  function combinedPretty(ast, html) {
    const one = (p) => { const t = L.pretty(p)[html ? 'html' : 'text'].replace(/^L = /, ''); return t[0] === '{' ? t : '{ ' + t + ' }'; };
    return 'L = ' + ast.parts.map(one).join(ast.kind === 'union' ? ' ∪ ' : ' · ');
  }

  /* ---------- סיווג ---------- */
  function dfaToNfa(d) {
    const step = d.tr.map((row) => { const m = new Map(); row.forEach((t, i) => m.set(d.alphabet[i], [t])); return m; });
    return { n: d.n, start: d.start, acc: new Set(d.acc.map((x, i) => (x ? i : -1)).filter((i) => i >= 0)), eps: d.tr.map(() => []), step };
  }
  function nfaJoin(list, op) {
    const eps = [[]], step = [new Map()], acc = new Set();
    let prevAcc = [0];
    list.forEach((n, idx) => {
      const off = eps.length;
      for (let s = 0; s < n.n; s++) { eps.push(n.eps[s].map((x) => x + off)); const m = new Map(); n.step[s].forEach((ts, c) => m.set(c, ts.map((x) => x + off))); step.push(m); }
      if (op === 'union') eps[0].push(n.start + off); else prevAcc.forEach((a) => eps[a].push(n.start + off));
      const myAcc = Array.from(n.acc).map((a) => a + off);
      if (op === 'union') myAcc.forEach((a) => acc.add(a)); else if (idx === list.length - 1) myAcc.forEach((a) => acc.add(a));
      prevAcc = myAcc;
    });
    return { n: eps.length, start: 0, acc, eps, step };
  }
  function combinedDFA(ast) {
    if (ast.flat) return L.toDFA(ast.flat);
    const nfa = nfaJoin(ast.parts.map((p) => dfaToNfa(L.toDFA(p))), ast.kind);
    return L.minimize(L.determinize(nfa, ast.alphabet));
  }

  /** מעטפת רגולרית של שפת בלוקים: u₁*u₂*…u_k*. */
  const envelope = (b) => ({ t: 'cat', a: b.blocks.map((x) => ({ t: 'star', a: word(x.word, 1) })) });
  /** קבוצה רגולרית שמכילה את השפה (או null): הדפוס של הבלוקים הריקים / המלאים. */
  function approx(p) {
    const an = L.analyze(p);
    if (an.cls === 'regular') return dfaToNfa(L.toDFA(p));
    if (p.kind !== 'blocks') return null;
    const vars = Array.from(new Set(p.blocks.flatMap((b) => (b.e.gen ? b.e.vars : b.e.v ? [b.e.v] : []))));
    if (vars.length > 8) return null;
    const alts = [];
    for (let mask = 0; mask < (1 << vars.length); mask++) {
      const on = new Set(vars.filter((_, i) => mask & (1 << i)));
      alts.push({ t: 'cat', a: p.blocks.map((b) => {
        const e = b.e;
        if (e.pow || (e.gen && (!e.lin || Object.values(e.lin.co).some((c) => c < 0))) || (!e.gen && e.d < 0)) return { t: 'star', a: word(b.word, 1) };
        const k = e.gen ? e.lin.k : e.d, co = e.gen ? e.lin.co : e.v ? { [e.v]: e.c } : {};
        const grow = Object.keys(co).filter((v) => on.has(v));
        return { t: 'cat', a: [word(b.word, k + grow.reduce((s, v) => s + co[v], 0))].concat(grow.length ? [{ t: 'star', a: word(b.word, 1) }] : []) };
      }) });
    }
    return L.nfaFromRegex({ t: 'alt', a: alts });
  }
  /** האם החיתוך של שני אסל"דים סופי. */
  function finiteMeet(n1, n2, A) {
    const a = L.determinize(n1, A), b = L.determinize(n2, A);
    const key = (x, y) => x * b.n + y, adj = new Map(), acc = [], seen = new Set([key(a.start, b.start)]), st = [[a.start, b.start]];
    while (st.length) {
      const [x, y] = st.pop(), k = key(x, y), out = [];
      if (a.acc[x] && b.acc[y]) acc.push(k);
      A.forEach((_, i) => { const nx = a.tr[x][i], ny = b.tr[y][i], nk = key(nx, ny); out.push(nk); if (!seen.has(nk)) { seen.add(nk); st.push([nx, ny]); } });
      adj.set(k, out);
    }
    const live = new Set(acc);
    for (let changed = true; changed;) { changed = false; adj.forEach((out, k) => { if (!live.has(k) && out.some((o) => live.has(o))) { live.add(k); changed = true; } }); }
    const color = new Map();
    const cyc = (k) => { color.set(k, 1); for (const o of adj.get(k)) { if (!live.has(o)) continue; if (color.get(o) === 1 || (!color.has(o) && cyc(o))) return true; } color.set(k, 2); return false; };
    for (const k of live) if (!color.has(k) && cyc(k)) return false;
    return true;
  }
  /** הוכחה דרך מעטפת: חלק i (בלוקים) שהוכח עליו, וכל שאר החלקים נותנים בתוך המעטפת שלו רק מספר סופי של מילים. */
  function envelopeProof(ast, i) {
    const p = ast.parts[i];
    if (p.kind !== 'blocks') return false;
    const env = L.nfaFromRegex(envelope(p));
    return ast.parts.every((q, j) => { if (j === i) return true; const n = approx(q); return !!n && finiteMeet(env, n, ast.alphabet); });
  }

  function combinedAnalyze(ast) {
    if (ast.flat) return Object.assign({}, L.analyze(ast.flat));
    const an = ast.parts.map((p) => L.analyze(p));
    const cls = an.map((a) => a.cls);
    if (cls.every((c) => c === 'regular')) return R('regular', WHY.reg);
    if (ast.kind === 'union') {
      for (let i = 0; i < cls.length; i++) if (cls[i] === 'noncf' && envelopeProof(ast, i)) return R('noncf', WHY.noncf);
      if (cls.every((c) => c === 'regular' || c === 'cf' || c === 'cfany')) {
        for (let i = 0; i < cls.length; i++) if (cls[i] === 'cf' && envelopeProof(ast, i)) return R('cf', WHY.cf);
        return R('cfany', WHY.cfany);
      }
      return R('unknown', WHY.unk);
    }
    return cls.every((c) => c === 'regular' || c === 'cf' || c === 'cfany') ? R('cfany', WHY.cfany) : R('unknown', WHY.unk);
  }

  /* ---------- אוטומט מחסנית לאיחוד / שרשור ---------- */
  function rulesOf(m) {
    const rules = [];
    for (const t of m.transitions) for (const l of t.labels) rules.push({ from: t.from, to: t.to, read: l.read, pop: m.type === 'pda' ? l.pop : '', push: m.type === 'pda' ? l.push : '' });
    return { rules, start: m.start, acc: m.states.filter((s) => s.accepting).map((s) => s.id), stack: m.type === 'pda' ? m.stackAlphabet : ['Z'] };
  }
  function combinedPDA(ast) {
    if (ast.flat) return L.machineFor(ast.flat);
    const ms = ast.parts.map((p) => L.machineFor(p));
    if (ms.some((m) => !m)) return null;
    const parts = ms.map(rulesOf);
    const B = { n: 0, rules: [], acc: new Set() };
    const used = new Set(parts.flatMap((p) => p.stack).concat(ast.alphabet));
    const ids = parts.map(() => new Map());
    const id = (i, s) => { if (!ids[i].has(s)) ids[i].set(s, B.n++); return ids[i].get(s); };
    const start = B.n++;
    let prev = [start];
    const extra = [];
    parts.forEach((p, i) => {
      // בשרשור: כל חלק אחרי הראשון רץ מעל "תחתית" משלו, כדי שלא יראה את מה שהחלק הקודם השאיר במחסנית
      let bottom = 'Z';
      if (ast.kind === 'concat' && i > 0) { bottom = Array.from('YXWVUTSRQPONMLKJIHGFEDCBA').find((x) => !used.has(x)); used.add(bottom); extra.push(bottom); }
      const rz = (s) => (bottom === 'Z' ? s : s.replace(/Z/g, bottom));
      for (const r of p.rules) B.rules.push({ from: id(i, r.from), to: id(i, r.to), read: r.read, pop: rz(r.pop), push: rz(r.push) });
      for (const s of ast.kind === 'union' ? [start] : prev) B.rules.push({ from: s, to: id(i, p.start), read: '', pop: '', push: bottom === 'Z' ? '' : bottom });
      prev = p.acc.map((s) => id(i, s));
      if (ast.kind === 'union' || i === parts.length - 1) prev.forEach((s) => B.acc.add(s));
    });
    return L.assemblePDA(B, start, ast.alphabet, Array.from(new Set(parts.flatMap((p) => p.stack).concat(extra))).filter((x) => x !== 'Z'));
  }

  Object.assign(L, { parseCombined, combinedAccepts, combinedPretty, combinedAnalyze, combinedDFA, combinedPDA, isCombined: isComb });
});
