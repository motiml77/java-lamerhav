/*
 * lang/parse.js - מפענח תיאור של שפה לעץ (AST). שלוש צורות, מזוהות לבד:
 *   1. בלוקים:   a^n b^m | n >= 2, m = 2n        (גם (ab)^n, a^3, a*, a+, a^(2n+1))
 *   2. תכונה:    w ∈ {a,b}* | #a(w) mod 2 = 0 וגם מסתיימת ב-ab
 *   3. ביטוי רגולרי: (a|b)*abb                   (ב-regex.js)
 *   4. תבנית של מילה: w w^R, w#w                 (ב-extra.js)
 * AST:
 *   { kind:'blocks', blocks:[{ word, e }], cond, vars, alphabet, gen }
 *     e = { c, v, d, pow? }: מספר החזרות c·v + d (או pow בחזקת v).  e = { gen, t, lin, vars }: חזקה כללית (n+m, n−m, n·m, n²)
 *   { kind:'prop', alphabet, cond }      { kind:'regex', re, alphabet }
 *   תנאי: {op:'and'|'or',args} {op:'not',arg} {op:'cmp',rel,l,r} {op:'starts'|'ends'|'has',word} {op:'pal'} {op:'bal',open,close} {op:'true'}
 *   חשבון: {t:'num',v} {t:'var',n} {t:'add'|'sub',a,b} {t:'mul',k,a} {t:'mod',a,m} {t:'prod',a,b} {t:'pw',a,k}   (ב-prop המשתנים '|w|', '#a')
 */
(function (root, f) {
  const node = typeof module === 'object' && module.exports;
  const L = node ? require('./ns.js') : root.AutomataLang;
  f(L);
  if (node) module.exports = L;
})(typeof globalThis !== 'undefined' ? globalThis : this, function (L) {
  'use strict';
  const E = L.LangError;
  const VARS = new Set(['n', 'm', 'k', 'i', 'j', 'l', 'p', 'q', 'r', 't']);
  const RELS = new Set(['=', '!=', '<', '>', '<=', '>=']);
  const HE = {
    and: ['וגם', 'ו'], or: ['או'], not: ['לא'], even: ['זוגי', 'זוגית'], odd: ['אי-זוגי', 'אי-זוגית', 'איזוגי'], div: ['מתחלק', 'מתחלקת'],
    starts: ['מתחילה', 'מתחיל', 'מתחילות'], ends: ['מסתיימת', 'מסתיים', 'נגמרת', 'נגמר'], has: ['מכילה', 'מכיל', 'כוללת'], pal: ['פלינדרום'], bal: ['מאוזנת', 'מאוזן', 'מאוזנים'],
    filler: ['ב', 'את', 'עם', 'היא', 'הוא', 'המילה', 'סוגריים'], sep: ['כאשר', 'כך', 'ש', 'שבהן', 'שבה'],
  };

  class P {
    constructor(toks, len) { this.toks = toks; this.i = 0; this.eof = { t: 'eof', v: '', pos: len, end: len, sp: true }; }
    peek(k) { return this.toks[this.i + (k || 0)] || this.eof; }
    next() { return this.toks[this.i++] || this.eof; }
    is(t, v) { const x = this.peek(); return x.t === t && (v == null || x.v === v); }
    he(group) { const x = this.peek(); return x.t === 'he' && HE[group].includes(x.v); }
    word(group, t, kw) { const x = this.peek(); return (x.t === 'he' && HE[group].includes(x.v)) || x.t === t || (x.t === 'kw' && x.v === kw); }
    fail(msg, hint, tok) { tok = tok || this.peek(); throw new E(msg, tok.pos, Math.max(tok.end, tok.pos + 1), hint); }
    expect(t, msg, hint) { if (!this.is(t)) this.fail(msg, hint); return this.next(); }
  }

  /* ---------- חשבון ותנאים (משותף לבלוקים ולתכונות) ---------- */
  function primary(p, ctx) {
    const x = p.peek();
    if (x.t === 'num') {
      p.next();
      const k = +x.v, y = p.peek();
      if (!y.sp && (y.t === 'len' || y.t === 'cnt' || (y.t === 'ch' && ctx.isVar(y.v)))) return { t: 'mul', k, a: factor(p, ctx) };
      if ((y.t === '*' || y.t === 'cdot') && p.peek(1).t !== 'num') { p.next(); return { t: 'mul', k, a: factor(p, ctx) }; }
      return { t: 'num', v: k };
    }
    if (x.t === '(') { p.next(); const a = arith(p, ctx); p.expect(')', 'חסר סוגר סוגר )', 'כל ( צריך ) שסוגר אותו'); return a; }
    if (x.t === 'len' || x.t === 'cnt') {
      if (ctx.mode !== 'prop') p.fail('|w| ו-#a(w) שייכים לתיאור של מילה w', 'התחילו ב- w ∈ {a,b}* | ואז התנאי');
      p.next();
      if (x.t === 'cnt') ctx.symbols.add(x.v);
      return { t: 'var', n: x.t === 'len' ? '|w|' : '#' + x.v };
    }
    if (x.t === 'ch') {
      if (ctx.mode === 'prop') p.fail('לא ברור מה זה ' + x.v + ' כאן', 'אורך המילה כותבים |w|, ומספר ה-a במילה כותבים #a(w)');
      if (!ctx.isVar(x.v)) p.fail('המשתנה ' + x.v + ' לא מופיע באף חזקה', 'תנאי יכול לדבר רק על משתנים שמופיעים בחזקות, כמו n ב-a^n');
      p.next();
      return { t: 'var', n: x.v };
    }
    p.fail(x.t === 'eof' ? 'התנאי נקטע באמצע' : 'כאן צריך לבוא מספר או משתנה', ctx.mode === 'prop' ? 'למשל #a(w) >= 2' : 'למשל n >= 2');
  }
  const powOf = (a, k) => (k === 0 ? { t: 'num', v: 1 } : k === 1 ? a : a.t === 'num' ? { t: 'num', v: Math.pow(a.v, k) } : { t: 'pw', a, k });
  const mulOf = (a, b) => (a.t === 'num' && b.t === 'num' ? { t: 'num', v: a.v * b.v } : a.t === 'num' ? { t: 'mul', k: a.v, a: b } : b.t === 'num' ? { t: 'mul', k: b.v, a } : { t: 'prod', a, b });
  /** גורם: משתנה או סוגריים, ואולי בריבוע (n^2, n²). בתוך חזקה עילית "n2" הוא n². */
  function factor(p, ctx) {
    let a = primary(p, ctx);
    for (;;) {
      const x = p.peek();
      if (a.t !== 'num' && x.t === '^' && p.peek(1).t === 'num') { p.next(); a = powOf(a, +p.next().v); continue; }
      if (a.t !== 'num' && !x.sp && (x.t === 'sup' && /^[0-9]+$/.test(x.v) || (ctx.sup && x.t === 'num'))) { p.next(); a = powOf(a, +x.v); continue; }
      return a;
    }
  }
  function term(p, ctx) {
    let a = factor(p, ctx);
    for (;;) {
      if (p.is('kw', 'mod')) {
        p.next();
        const m = p.expect('num', 'אחרי mod צריך לבוא מספר', 'למשל n mod 3 = 1');
        if (+m.v < 1) p.fail('אי אפשר לחלק ב-0', '', m);
        a = { t: 'mod', a, m: +m.v };
      } else if (p.is('*') || p.is('cdot')) { p.next(); a = mulOf(a, factor(p, ctx)); } else return a;
    }
  }
  function arith(p, ctx) {
    let a = term(p, ctx);
    while (p.is('+') || p.is('-')) { const op = p.next().t; a = { t: op === '+' ? 'add' : 'sub', a, b: term(p, ctx) }; }
    return a;
  }
  function literal(p) {
    while (p.he('filler')) p.next();
    if (p.is('-')) p.next();
    if (p.is('eps')) { p.next(); return ''; }
    let w = '', first = true;
    while ((p.is('ch') || p.is('num')) && (first || !p.peek().sp)) { w += p.next().v; first = false; }
    if (!w) p.fail('כאן צריכה לבוא מילה (רצף אותיות)', 'למשל: מסתיימת ב-ab');
    return w;
  }
  /** הצהרה על משתנים: n,m ≥ 1 · n ∈ ℕ · n,m ∈ N · n ∈ ℕ⁺. מחזיר את התנאי (עם טקסט להצגה), או null אם זו לא הצהרה. */
  function declaration(p, ctx) {
    if (ctx.mode !== 'blocks') return null;
    const vars = [];
    let k = 0;
    for (;;) {
      const t = p.peek(k);
      if (t.t !== 'ch' || !(VARS.has(t.v) || ctx.isVar(t.v))) return null;
      vars.push(t);
      k++;
      if (p.peek(k).t === ',' && p.peek(k + 1).t === 'ch') { k++; continue; }
      break;
    }
    const nx = p.peek(k);
    if (!(nx.t === 'in' || (vars.length >= 2 && RELS.has(nx.t)))) return null;
    for (const v of vars) if (!ctx.isVar(v.v)) p.fail('המשתנה ' + v.v + ' לא מופיע באף חזקה', 'תנאי יכול לדבר רק על משתנים שמופיעים בחזקות, כמו n ב-a^n', v);
    for (let i = 0; i < k; i++) p.next();
    const names = vars.map((v) => v.v).join(', ');
    if (p.is('in')) {
      p.next();
      const s = p.peek();
      if (!(s.t === 'nat' || (s.t === 'ch' && s.v === 'N'))) p.fail('אחרי ∈ בתנאי צריך לבוא ℕ (או N)', 'למשל n ∈ ℕ, כלומר n ≥ 0. אפשר גם n ≥ 1', s);
      p.next();
      let min = 0;
      if (p.is('sup') && p.peek().v === '+' || (p.is('+') && !p.peek().sp)) { p.next(); min = 1; } else if (p.is('num') && !p.peek().sp && p.peek().v === '0') p.next();
      const args = vars.map((v) => ({ op: 'cmp', rel: '>=', l: { t: 'var', n: v.v }, r: { t: 'num', v: min } }));
      return { op: 'and', args, decl: names + ' ∈ ℕ' + (min ? '⁺' : '') };
    }
    const rel = p.next().t, r = arith(p, ctx);
    const args = vars.map((v) => ({ op: 'cmp', rel, l: { t: 'var', n: v.v }, r }));
    return { op: 'and', args, decl: { names, rel, r } };
  }
  function atom(p, ctx) {
    const d = declaration(p, ctx);
    if (d) return d;
    if (ctx.mode === 'prop') {
      if (p.is('ch', 'w') && p.peek(1).t === 'he') p.next();
      while (p.he('filler') && p.peek(1).t === 'he') p.next();
      for (const op of ['starts', 'ends', 'has']) if (p.he(op)) { p.next(); const word = literal(p); for (const c of word) ctx.symbols.add(c); return { op, word }; }
      if (p.he('pal')) { p.next(); return { op: 'pal' }; }
      if (p.he('bal')) { p.next(); ctx.bal = ctx.bal || []; ctx.bal.push({ op: 'bal' }); return ctx.bal[ctx.bal.length - 1]; }
    }
    const l = arith(p, ctx);
    if (p.is('equiv')) {            // n ≡ 1 (mod 3)
      p.next();
      let r = arith(p, ctx), m = null;
      if (p.is('(') && p.peek(1).t === 'kw' && p.peek(1).v === 'mod') { p.next(); p.next(); m = p.expect('num', 'אחרי mod צריך לבוא מספר', 'למשל n ≡ 1 (mod 3)'); p.expect(')', 'חסר סוגר סוגר )', 'למשל n ≡ 1 (mod 3)'); m = +m.v; }
      else if (p.is('kw', 'mod')) { p.next(); m = +p.expect('num', 'אחרי mod צריך לבוא מספר', 'למשל n ≡ 1 (mod 3)').v; }
      else if (r.t === 'mod') { m = r.m; r = r.a; }
      if (!m) p.fail('אחרי ≡ צריך לכתוב לפי איזה מספר', 'למשל n ≡ 1 (mod 3), כלומר n mod 3 = 1');
      return { op: 'cmp', rel: '=', l: { t: 'mod', a: l, m }, r: r.t === 'num' ? { t: 'num', v: ((r.v % m) + m) % m } : { t: 'mod', a: r, m } };
    }
    if (p.word('even', '', 'even')) { p.next(); return { op: 'cmp', rel: '=', l: { t: 'mod', a: l, m: 2 }, r: { t: 'num', v: 0 } }; }
    if (p.word('odd', '', 'odd')) { p.next(); return { op: 'cmp', rel: '=', l: { t: 'mod', a: l, m: 2 }, r: { t: 'num', v: 1 } }; }
    if (p.he('div')) {
      p.next(); while (p.he('filler')) p.next(); if (p.is('-')) p.next();
      const m = p.expect('num', 'אחרי "מתחלק ב-" צריך לבוא מספר', 'למשל n מתחלק ב-3');
      if (+m.v < 1) p.fail('אי אפשר לחלק ב-0', '', m);
      return { op: 'cmp', rel: '=', l: { t: 'mod', a: l, m: +m.v }, r: { t: 'num', v: 0 } };
    }
    if (!RELS.has(p.peek().t)) p.fail('כאן חסרה השוואה', 'אחרי ביטוי צריך לבוא = ≠ < > ≤ ≥, או "זוגי" / "אי-זוגי"');
    const parts = [];
    let left = l;
    while (RELS.has(p.peek().t)) { const rel = p.next().t; const r = arith(p, ctx); parts.push({ op: 'cmp', rel, l: left, r }); left = r; }
    return parts.length === 1 ? parts[0] : { op: 'and', args: parts, chain: true };
  }
  function notExpr(p, ctx) {
    if (p.word('not', 'not', 'not')) { p.next(); return { op: 'not', arg: notExpr(p, ctx) }; }
    if (p.is('(')) {
      const save = p.i;
      try { return atom(p, ctx); } catch (e) { if (!(e instanceof E)) throw e; p.i = save; }
      p.next();
      const c = cond(p, ctx);
      p.expect(')', 'חסר סוגר סוגר )', 'כל ( צריך ) שסוגר אותו');
      return c;
    }
    return atom(p, ctx);
  }
  const flat = (op, args) => (args.length === 1 ? args[0] : { op, args });
  function andExpr(p, ctx) { const a = [notExpr(p, ctx)]; while (p.word('and', 'and', 'and')) { p.next(); a.push(notExpr(p, ctx)); } return flat('and', a); }
  function orExpr(p, ctx) { const a = [andExpr(p, ctx)]; while (p.word('or', 'or', 'or')) { p.next(); a.push(andExpr(p, ctx)); } return flat('or', a); }
  function cond(p, ctx) { const a = [orExpr(p, ctx)]; while (p.is(',')) { p.next(); a.push(orExpr(p, ctx)); } return flat('and', a); }

  /** עץ חשבון -> צורה ליניארית { k, co: {שם: מקדם} }, או null אם יש בו mod. */
  function linear(a) {
    if (a.t === 'num') return { k: a.v, co: {} };
    if (a.t === 'var') return { k: 0, co: { [a.n]: 1 } };
    if (a.t === 'mod' || a.t === 'prod' || a.t === 'pw') return null;
    if (a.t === 'mul') { const x = linear(a.a); if (!x) return null; const co = {}; for (const n in x.co) co[n] = x.co[n] * a.k; return { k: x.k * a.k, co }; }
    const x = linear(a.a), y = linear(a.b), s = a.t === 'add' ? 1 : -1;
    if (!x || !y) return null;
    const co = Object.assign({}, x.co);
    for (const n in y.co) { co[n] = (co[n] || 0) + s * y.co[n]; if (!co[n]) delete co[n]; }
    return { k: x.k + s * y.k, co };
  }

  /* ---------- צורה 1: בלוקים ---------- */
  const varsOf = (a, out) => { if (a.t === 'var') out.add(a.n); else if (a.a) { varsOf(a.a, out); if (a.b) varsOf(a.b, out); } return out; };
  const hasMod = (a) => a.t === 'mod' || (!!a.a && (hasMod(a.a) || (!!a.b && hasMod(a.b))));
  /** חזקה: מספר, משתנה אחד (c·v + d), או חזקה כללית: צירוף ליניארי של כמה משתנים (n+m, 2n−m+1), מכפלה (n·m) או ריבוע (n²). */
  function exponent(tree, p, tok) {
    if (hasMod(tree)) p.fail('בחזקה אי אפשר mod', 'שארית כותבים כתנאי אחרי |, למשל a^n | n mod 3 = 0', tok);
    const lin = linear(tree);
    const names = lin ? Object.keys(lin.co) : [];
    if (lin && !names.length) { if (lin.k < 0) p.fail('חזקה לא יכולה להיות שלילית', '', tok); return { c: 0, v: null, d: lin.k }; }
    if (lin && names.length === 1 && lin.co[names[0]] >= 1) return { c: lin.co[names[0]], v: names[0], d: lin.k };
    if (lin && names.every((v) => lin.co[v] < 0)) p.fail('בחזקה צריך לפחות משתנה אחד עם פלוס', 'למשל a^(n−m) b^m, או a^(n+m)', tok);
    return { gen: true, t: tree, lin, vars: Array.from(varsOf(tree, new Set())) };
  }
  /** a^n+m (בלי סוגריים, בלי רווח): ממשיכים את החזקה. */
  function moreTerms(e, p) {
    const go = () => (p.is('+') || p.is('-')) && !p.peek().sp && !p.peek(1).sp && (p.peek(1).t === 'num' || (p.peek(1).t === 'ch' && VARS.has(p.peek(1).v)));
    if (!go()) return e;
    const v = e.v && (e.c === 1 ? { t: 'var', n: e.v } : { t: 'mul', k: e.c, a: { t: 'var', n: e.v } });
    let t = !v ? { t: 'num', v: e.d } : e.d ? { t: 'add', a: v, b: { t: 'num', v: e.d } } : v;
    const tok = p.peek();
    while (go()) { const op = p.next().t; const x = p.next(); t = { t: op === '+' ? 'add' : 'sub', a: t, b: x.t === 'num' ? { t: 'num', v: +x.v } : { t: 'var', n: x.v } }; }
    return exponent(t, p, tok);
  }
  function parseBlocks(p) {
    const blocks = [], vars = [], anyVar = { mode: 'blocks', isVar: () => true }, supVar = { mode: 'blocks', isVar: () => true, sup: true };
    let hidden = 0;
    const isSep = () => p.is('|') || p.is(':') || p.is('slash') || p.is(',') || p.he('sep') || p.is('eof');
    while (!isSep()) {
      const x = p.peek();
      let word = '';
      if (x.t === 'cdot') { p.next(); continue; }
      if (x.t === '(') {
        p.next();
        while (p.is('ch') || p.is('num')) word += p.next().v;
        if (!word || !p.is(')')) p.fail('בתוך סוגריים אפשר רק רצף אותיות, כמו (ab)^n', 'חזקה בתוך חזקה עוד לא נתמכת');
        p.next();
      } else if (x.t === 'ch' || x.t === 'num') {
        p.next();
        for (const c of x.v.slice(0, -1)) blocks.push({ word: c, e: { c: 0, v: null, d: 1 } });
        word = x.v.slice(-1);
      } else if (x.t === 'eps') { p.next(); continue; } else p.fail('כאן צריכה לבוא אות של האלפבית', 'למשל a^n b^m');
      let e = { c: 0, v: null, d: 1 }, src = null;
      const y = p.peek();
      const fresh = (d) => ({ c: 1, v: '_' + (++hidden), d });
      if (y.t === '*') { p.next(); e = fresh(0); } else if (y.t === '+') { p.next(); e = fresh(1); } else if (y.t === 'sup') {
        p.next();
        if (y.v === '+') e = fresh(1);
        else { const sub = new P(L.lex(y.v), y.v.length); e = exponent(arith(sub, supVar), p, y); if (!sub.is('eof')) p.fail('לא הצלחתי להבין את החזקה', '', y); src = { at: [y.pos, y.end], inner: [y.pos, y.end], form: 'sup' }; }
      } else if (y.t === '^') {
        p.next();
        const z = p.peek(), par = z.t === '(' ? 1 : 0;
        const two = p.peek(par + 1), three = p.peek(par + 2);
        const pw = p.peek(par).t !== 'num' ? 0 : two.t === 'sup' && VARS.has(two.v) ? 1 : two.t === '^' && three.t === 'ch' && VARS.has(three.v) ? 2 : 0;
        if (pw) {            // a^(2^n): חזקה בתוך חזקה
          if (par) p.next();
          const b = +p.next().v;
          if (pw === 2) p.next();
          e = { c: 1, v: p.next().v, d: 0, pow: b };
          if (b < 2) p.fail('הבסיס של החזקה הפנימית צריך להיות 2 לפחות', 'למשל a^(2^n)', z);
          const close = par ? p.expect(')', 'חסר סוגר סוגר ) בחזקה', '') : null;
          src = close ? { at: [y.pos, close.end], inner: [z.end, close.pos], form: 'paren' } : { at: [y.pos, p.toks[p.i - 1].end], inner: [y.end, p.toks[p.i - 1].end], form: 'caret' };
        } else if (z.t === '(') { p.next(); e = exponent(arith(p, anyVar), p, z); const close = p.expect(')', 'חסר סוגר סוגר ) בחזקה', ''); src = { at: [y.pos, close.end], inner: [z.end, close.pos], form: 'paren' }; }
        else if (z.t === '+') { p.next(); e = fresh(1); } else if (z.t === '*') { p.next(); e = fresh(0); } else {
          let c = null, v = null;
          if (z.t === 'num') c = +p.next().v;
          const q = p.peek();
          if (q.t === 'ch' && !q.sp && (c === null || VARS.has(q.v))) v = p.next().v;
          if (c === null && v === null) p.fail('אחרי ^ צריך לבוא מספר או משתנה', 'למשל a^n או a^3. חזקה מורכבת בסוגריים: a^(2n+1)');
          if (v && c === 0) p.fail('המקדם בחזקה חייב להיות חיובי', '', z);
          e = moreTerms(v ? { c: c === null ? 1 : c, v, d: 0 } : { c: 0, v: null, d: c }, p);
          src = { at: [y.pos, p.toks[p.i - 1].end], inner: [y.end, p.toks[p.i - 1].end], form: 'caret' };
        }
      }
      for (const v of e.gen ? e.vars : e.v ? [e.v] : []) if (v[0] !== '_' && !vars.includes(v)) vars.push(v);
      blocks.push(src ? { word, e, src } : { word, e });   // src: איפה החזקה בטקסט (לעריכה בלחיצה בתצוגה, exp-edit.js)
    }
    if (!blocks.length) p.fail('חסר תיאור של המילים', 'למשל a^n b^m');
    const letters = new Set(blocks.map((b) => b.word).join(''));
    for (const v of vars) if (letters.has(v)) throw new E('האות ' + v + ' משמשת גם כאות באלפבית וגם כמשתנה', 0, 1, 'בחרו למשתנה שם אחר, למשל n, m או k');
    let c = null;
    if (!p.is('eof')) {
      while (p.is('|') || p.is(':') || p.is('slash') || p.is(',') || p.he('sep') || p.is('-')) p.next();
      if (!p.is('eof')) c = cond(p, { mode: 'blocks', isVar: (n) => vars.includes(n) });
    }
    // כל משתנה צריך להופיע לפחות פעם אחת עם פלוס (אחרת המילה לא קובעת אותו)
    const plus = new Set();
    for (const b of blocks) for (const v of b.e.gen ? (b.e.lin ? Object.keys(b.e.lin.co).filter((x) => b.e.lin.co[x] > 0) : b.e.vars) : b.e.v ? [b.e.v] : []) plus.add(v);
    for (const v of vars) if (!plus.has(v)) throw new E('המשתנה ' + v + ' מופיע רק עם מינוס', 0, 1, 'כל משתנה צריך להופיע לפחות בחזקה אחת עם פלוס, למשל a^(n+m) b^(n−m)');
    return { kind: 'blocks', blocks, cond: c, vars, alphabet: Array.from(letters), gen: blocks.some((b) => b.e.gen) };
  }

  /* ---------- צורה 2: תכונה של מילה ---------- */
  function parseProp(p, opts) {
    const ctx = { mode: 'prop', isVar: () => false, symbols: new Set() };
    let alphabet = null;
    if (p.is('ch', 'w') && p.peek(1).t === 'in') {
      p.next(); p.next();
      if (p.is('sigma')) { p.next(); alphabet = null; } else {
        p.expect('{', 'אחרי ∈ צריך לבוא האלפבית בסוגריים מסולסלים', 'למשל w ∈ {a,b}*');
        alphabet = [];
        while (p.is('ch') || p.is('num') || p.is('(') || p.is(')')) { for (const c of p.next().v) if (!alphabet.includes(c)) alphabet.push(c); if (p.is(',')) p.next(); }
        p.expect('}', 'חסר } שסוגר את האלפבית', 'למשל {a,b}*');
        if (!alphabet.length) p.fail('האלפבית ריק', 'למשל {a,b}*');
      }
      if (p.is('*')) p.next();
      while (p.is('|') || p.is(':') || p.is('slash') || p.is(',') || p.he('sep') || p.is('-')) p.next();
    }
    const c = p.is('eof') ? { op: 'true' } : cond(p, ctx);
    if (!alphabet && ctx.bal) alphabet = Array.from(new Set(['(', ')', ...ctx.symbols]));
    if (!alphabet) {
      alphabet = Array.from(new Set([...ctx.symbols, ...((opts && opts.alphabet) || [])]));
      if (alphabet.length < 2) for (const d of ['a', 'b']) if (alphabet.length < 2 && !alphabet.includes(d)) alphabet.push(d);
      alphabet.sort();
    } else for (const s of ctx.symbols) if (!alphabet.includes(s)) throw new E('האות ' + s + ' מופיעה בתנאי אבל לא באלפבית {' + alphabet.join(',') + '}', 0, 1, 'הוסיפו אותה לאלפבית, או תקנו את התנאי');
    if (ctx.bal) {          // "מאוזנת": הסוגריים הם ( ו-), ואם הם לא באלפבית, שתי האותיות הראשונות שלו
      const br = alphabet.includes('(') && alphabet.includes(')');
      if (alphabet.length < 2) throw new E('"מאוזנת" צריכה אלפבית עם שני סימנים: פותח וסוגר', 0, 1, 'למשל w ∈ {(,)}* | מאוזנת');
      for (const b of ctx.bal) { b.open = br ? '(' : alphabet[0]; b.close = br ? ')' : alphabet[1]; }
    }
    return { kind: 'prop', alphabet, cond: c };
  }

  function style(toks) {
    const PRED = [...HE.starts, ...HE.ends, ...HE.has, ...HE.pal, ...HE.bal];
    if (L.isPattern(toks)) return 'pattern';
    if (toks.some((t, i) => t.t === 'len' || t.t === 'cnt' || (t.t === 'he' && PRED.includes(t.v)) || (t.t === 'ch' && t.v === 'w' && (toks[i + 1] || {}).t === 'in'))) return 'prop';
    if (toks.some((t) => t.t === '^' || (t.t === 'sup' && t.v !== '+'))) return 'blocks';
    if (toks.some((t) => RELS.has(t.t))) return 'blocks';
    return 'regex';
  }

  /** מפענח. מחזיר AST, או זורק LangError (pos/end יחסית לטקסט המקורי). opts.alphabet: Σ של המכונה (ברירת מחדל לצורה 2). */
  function parse(text, opts) {
    const u = L.unwrap(String(text || ''));
    try {
      if (!u.text.trim()) throw new E('עוד לא נכתבה שפה', 0, 0, 'למשל a^n b^n, או בחרו תבנית');
      const toks = L.lex(u.text);
      const both = L.parseCombined && L.parseCombined(u.text, toks, opts);
      if (both) { both.text = String(text); shiftSrc(both, u.offset); return both; }
      const p = new P(toks, u.text.length);
      const kind = style(toks);
      const ast = kind === 'pattern' ? L.parsePattern(p, opts) : kind === 'prop' ? parseProp(p, opts) : kind === 'blocks' ? parseBlocks(p) : L.parseRegex(p, opts);
      if (!p.is('eof')) p.fail('לא הצלחתי להבין את ההמשך מכאן', kind === 'blocks' ? 'תנאים מפרידים בפסיק או ב"וגם"' : '');
      ast.text = String(text);
      shiftSrc(ast, u.offset);
      return ast;
    } catch (e) {
      if (e instanceof E) { e.pos += u.offset; e.end += u.offset; }
      throw e;
    }
  }

  /** המיקומים של החזקות (blocks[].src) יחסית לטקסט המקורי: מוסיפים את מה שהוסר בהתחלה ("L = {", או החלק הקודם באיחוד) */
  function shiftSrc(ast, off) {
    if (!off || !ast) return;
    if (ast.parts) for (const x of ast.parts) shiftSrc(x, off);
    if (ast.blocks) for (const b of ast.blocks) if (b.src) for (const k of ['at', 'inner']) b.src[k] = [b.src[k][0] + off, b.src[k][1] + off];
  }

  L.shiftSrc = shiftSrc;
  L.P = P;
  L.styleOf = style;
  L.parse = parse;
  L.linear = linear;
  L.VARS = VARS;
});
