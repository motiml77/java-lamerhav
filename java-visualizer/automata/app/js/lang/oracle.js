/*
 * lang/oracle.js - "האם המילה בשפה?" לכל נוסחה שהמפענח מקבל, ישירות מההגדרה (בלי לבנות אוטומט).
 * זה מקור האמת: כל אוטומט שנבנה אוטומטית נבדק מולו, וגם האוטומט של התלמיד.
 * בנוסף: דגימה של המילים הקצרות (בשפה / לא בשפה), וכתיבה יפה של הנוסחה (aⁿbᵐ | n ≥ 2).
 */
(function (root, f) {
  const node = typeof module === 'object' && module.exports;
  const L = node ? require('./ns.js') : root.AutomataLang;
  f(L);
  if (node) module.exports = L;
})(typeof globalThis !== 'undefined' ? globalThis : this, function (L) {
  'use strict';
  const mod = (x, m) => ((x % m) + m) % m;

  function evalA(a, env) {
    switch (a.t) {
      case 'num': return a.v;
      case 'var': return env[a.n] || 0;
      case 'add': return evalA(a.a, env) + evalA(a.b, env);
      case 'sub': return evalA(a.a, env) - evalA(a.b, env);
      case 'mul': return a.k * evalA(a.a, env);
      case 'mod': return mod(evalA(a.a, env), a.m);
    }
    throw new Error('arith ' + a.t);
  }
  const REL = { '=': (x, y) => x === y, '!=': (x, y) => x !== y, '<': (x, y) => x < y, '>': (x, y) => x > y, '<=': (x, y) => x <= y, '>=': (x, y) => x >= y };

  /** env: ערכי המשתנים. word: המילה (לתנאים של צורה 2). */
  function evalC(c, env, word) {
    if (!c) return true;
    switch (c.op) {
      case 'true': return true;
      case 'false': return false;
      case 'and': return c.args.every((x) => evalC(x, env, word));
      case 'or': return c.args.some((x) => evalC(x, env, word));
      case 'not': return !evalC(c.arg, env, word);
      case 'cmp': return REL[c.rel](evalA(c.l, env), evalA(c.r, env));
      case 'starts': return word.startsWith(c.word);
      case 'ends': return word.endsWith(c.word);
      case 'has': return word.includes(c.word);
      case 'pal': return word === Array.from(word).reverse().join('');
    }
    throw new Error('cond ' + c.op);
  }

  /** בלוקים: מנסים את כל הדרכים לחלק את המילה לבלוקים (ערכי המשתנים חסומים באורך המילה). */
  function blocksAccept(ast, w) {
    const B = ast.blocks;
    function go(i, pos, env) {
      if (i === B.length) return pos === w.length && evalC(ast.cond, env, w);
      const b = B[i], e = b.e, len = b.word.length;
      const fits = (count) => { for (let k = 0; k < count; k++) if (!w.startsWith(b.word, pos + k * len)) return false; return true; };
      if (!e.v || env[e.v] != null) {
        const count = e.v ? e.c * env[e.v] + e.d : e.d;
        return count >= 0 && pos + count * len <= w.length && fits(count) && go(i + 1, pos + count * len, env);
      }
      for (let v = 0; ; v++) {
        const count = e.c * v + e.d;
        if (count < 0) continue;
        if (pos + count * len > w.length || !fits(count)) return false;
        env[e.v] = v;
        if (go(i + 1, pos + count * len, env)) { delete env[e.v]; return true; }
        delete env[e.v];
      }
    }
    return go(0, 0, {});
  }

  function accepts(ast, word) {
    word = String(word);
    for (const c of word) if (!ast.alphabet.includes(c)) return false;
    if (ast.kind === 'blocks') return blocksAccept(ast, word);
    if (ast.kind === 'regex') return L.reMatch(ast.re, word, ast.alphabet);
    const env = { '|w|': Array.from(word).length };
    for (const s of ast.alphabet) env['#' + s] = 0;
    for (const c of word) env['#' + c]++;
    return evalC(ast.cond, env, word);
  }

  /** כל המילים מעל האלפבית, מהקצרה לארוכה (ובסדר האלפבית). */
  function* words(alphabet, maxLen) {
    let layer = [''];
    for (let n = 0; n <= maxLen; n++) {
      yield* layer;
      if (n === maxLen) return;
      const next = [];
      for (const w of layer) for (const a of alphabet) next.push(w + a);
      layer = next;
    }
  }
  /** האורך הגדול ביותר כך שמספר המילים עד אליו לא עובר את budget. */
  function lengthFor(k, budget, cap) {
    let n = 0, total = 1, layer = 1;
    while (n < (cap || 30)) { layer *= Math.max(k, 1); if (total + layer > budget) break; total += layer; n++; if (k <= 1 && n >= (cap || 30)) break; }
    return n;
  }

  /** המילים הקצרות ביותר בשפה, והקצרות ביותר שאינן בה. */
  function sample(ast, opts) {
    opts = Object.assign({ nIn: 6, nOut: 5, budget: 3000 }, opts);
    const ins = [], outs = [];
    for (const w of words(ast.alphabet, lengthFor(ast.alphabet.length, opts.budget, 14))) {
      if (accepts(ast, w)) { if (ins.length < opts.nIn) ins.push(w); } else if (outs.length < opts.nOut) outs.push(w);
      if (ins.length >= opts.nIn && outs.length >= opts.nOut) break;
    }
    return { ins, outs };
  }

  /* ---------- כתיבה יפה ---------- */
  const SUPS = { 0: '⁰', 1: '¹', 2: '²', 3: '³', 4: '⁴', 5: '⁵', 6: '⁶', 7: '⁷', 8: '⁸', 9: '⁹', n: 'ⁿ', m: 'ᵐ', k: 'ᵏ', i: 'ⁱ', j: 'ʲ', l: 'ˡ', p: 'ᵖ', '+': '⁺', '-': '⁻', '(': '⁽', ')': '⁾' };
  const sup = (s) => Array.from(String(s)).map((c) => SUPS[c] || null);
  const esc = (s) => String(s).replace(/[&<>]/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;' })[c]);
  function expText(e) {
    if (!e.v) return String(e.d);
    return (e.c === 1 ? '' : e.c) + e.v + (e.d > 0 ? '+' + e.d : e.d < 0 ? String(e.d) : '');
  }
  function showA(a, top) {
    switch (a.t) {
      case 'num': return String(a.v);
      case 'var': return a.n[0] === '#' ? '#' + a.n.slice(1) + '(w)' : a.n;
      case 'add': case 'sub': { const s = showA(a.a) + (a.t === 'add' ? ' + ' : ' − ') + showA(a.b); return top ? s : '(' + s + ')'; }
      case 'mul': return a.k + (a.a.t === 'var' ? '' : '·') + showA(a.a);
      case 'mod': return showA(a.a, false) + ' mod ' + a.m;
    }
    return '?';
  }
  const RELS = { '=': '=', '!=': '≠', '<': '<', '>': '>', '<=': '≤', '>=': '≥' };
  const he = (s, html) => (html ? '<bdi class="he">' + s + '</bdi>' : s);
  function showC(c, html, top) {
    switch (c.op) {
      case 'true': return '';
      case 'false': return '∅';
      case 'and': return top ? c.args.map((x) => showC(x, html)).join(', ') : '(' + c.args.map((x) => showC(x, html)).join(' ' + he('וגם', html) + ' ') + ')';
      case 'or': { const s = c.args.map((x) => showC(x, html)).join(' ' + he('או', html) + ' '); return top ? s : '(' + s + ')'; }
      case 'not': return he('לא', html) + ' ' + showC(c.arg, html);
      case 'cmp': return showA(c.l, true) + ' ' + RELS[c.rel] + ' ' + showA(c.r, true);
      case 'starts': return he('מתחילה ב-', html) + (c.word || 'ε');
      case 'ends': return he('מסתיימת ב-', html) + (c.word || 'ε');
      case 'has': return he('מכילה', html) + ' ' + (c.word || 'ε');
      case 'pal': return he('פלינדרום', html);
    }
    return '?';
  }
  function showRe(n, prec) {
    switch (n.t) {
      case 'sym': return n.c;
      case 'eps': return 'ε';
      case 'empty': return '∅';
      case 'any': return 'Σ';
      case 'cat': { const s = n.a.map((x) => showRe(x, 1)).join(''); return prec > 1 ? '(' + s + ')' : s; }
      case 'alt': { const s = n.a.map((x) => showRe(x, 0)).join(' ∪ '); return prec > 0 ? '(' + s + ')' : s; }
      case 'star': return showRe(n.a, 2) + '*';
      case 'plus': return showRe(n.a, 2) + '⁺';
    }
    return '?';
  }
  /** מחזיר { text, html }: L = { aⁿbᵐ | n ≥ 2 }. ב-html החזקות ב-<sup>. */
  function pretty(ast) {
    const make = (html) => {
      if (ast.kind === 'regex') return 'L = ' + showRe(ast.re, 0);
      if (ast.kind === 'prop') { const c = showC(ast.cond, html, true); return 'L = { w ∈ {' + ast.alphabet.join(',') + '}*' + (c ? ' | ' + c : '') + ' }'; }
      const body = ast.blocks.map((b) => {
        const base = b.word.length > 1 ? '(' + b.word + ')' : b.word, e = b.e;
        if (!e.v && e.d === 1) return base;
        if (e.v && e.v[0] === '_') return base + (e.d ? (html ? '<sup>+</sup>' : '⁺') : '*');
        const t = expText(e);
        if (html) return esc(base) + '<sup>' + esc(t) + '</sup>';
        const u = sup(t);
        return base + (u.every(Boolean) ? u.join('') : '^(' + t + ')');
      }).join('');
      const c = ast.cond ? showC(ast.cond, html, true) : '';
      return 'L = { ' + body + (c ? ' | ' + c : '') + ' }';
    };
    return { text: make(false), html: make(true) };
  }

  Object.assign(L, { evalA, evalC, accepts, words, lengthFor, sample, pretty, mod });
});
