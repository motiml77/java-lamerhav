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
      case 'prod': return evalA(a.a, env) * evalA(a.b, env);
      case 'pw': return Math.pow(evalA(a.a, env), a.k);
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
      case 'bal': { let d = 0; for (const ch of word) { if (ch === c.open) d++; else if (ch === c.close && --d < 0) return false; } return d === 0; }
    }
    throw new Error('cond ' + c.op);
  }

  /** מספר החזרות של בלוק כשהמשתנה שלו שווה v. */
  const reps = (e, v) => (!e.v ? e.d : e.pow ? Math.pow(e.pow, v) : e.c * v + e.d);
  /** מספר החזרות של בלוק לפי ערכי המשתנים (גם בחזקה כללית). */
  const countOf = (e, env) => (e.gen ? evalA(e.t, env) : reps(e, env[e.v]));
  /**
   * כמה גדול יכול להיות משתנה שהמילה לא קובעת לבד (למשל m ב-a^(n·m) כש-n = 0): |w| ועוד הקבועים והמודולים שבנוסחה.
   * בכל משתנה אחר, החזקה שבה הוא מופיע עם פלוס כבר חוסמת אותו באורך המילה.
   */
  function slackOf(ast) {
    if (ast._slack == null) {
      let s = 2;
      const walkA = (a) => { if (!a) return; if (a.t === 'num') s += Math.abs(a.v); if (a.t === 'mul') s += Math.abs(a.k); if (a.t === 'mod') s += a.m; walkA(a.a); walkA(a.b); };
      const walkC = (c) => { if (!c) return; if (c.op === 'cmp') { walkA(c.l); walkA(c.r); } (c.args || []).forEach(walkC); walkC(c.arg); };
      for (const b of ast.blocks) if (b.e.gen) walkA(b.e.t); else s += Math.abs(b.e.d || 0);
      walkC(ast.cond);
      Object.defineProperty(ast, '_slack', { value: s, enumerable: false });
    }
    return ast._slack;
  }

  /** בלוקים: מנסים את כל הדרכים לחלק את המילה לבלוקים (ערכי המשתנים חסומים באורך המילה). */
  function blocksAccept(ast, w) {
    const B = ast.blocks;
    function go(i, pos, env) {
      if (i === B.length) return pos === w.length && evalC(ast.cond, env, w);
      const b = B[i], e = b.e, len = b.word.length;
      const fits = (count) => { for (let k = 0; k < count; k++) if (!w.startsWith(b.word, pos + k * len)) return false; return true; };
      const take = (count) => count >= 0 && pos + count * len <= w.length && fits(count) && go(i + 1, pos + count * len, env);
      if (e.gen) {          // חזקה כללית: עוברים על כל הערכים של המשתנים שעוד לא נקבעו (עד |w| + קבועים)
        const need = e.vars.filter((v) => env[v] == null), bound = w.length + slackOf(ast);
        const rec = (j) => {
          if (j === need.length) return take(countOf(e, env));
          for (let x = 0; x <= bound; x++) { env[need[j]] = x; if (rec(j + 1)) return true; }
          delete env[need[j]];
          return false;
        };
        return rec(0);
      }
      if (!e.v || env[e.v] != null) {
        const count = reps(e, env[e.v]);
        return count >= 0 && pos + count * len <= w.length && fits(count) && go(i + 1, pos + count * len, env);
      }
      for (let v = 0; ; v++) {
        const count = reps(e, v);
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
    if (ast.kind === 'pattern') return L.patternAccepts(ast, word);
    if (ast.kind === 'union' || ast.kind === 'concat') return L.combinedAccepts(ast, word);
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
  /** חזקה כללית בכתיב צפוף: n+m, 2n−m+1, n·m, n². ב-html הריבוע ב-<sup>. */
  function showE(a, html, inner) {
    switch (a.t) {
      case 'num': return String(a.v);
      case 'var': return a.n;
      case 'add': case 'sub': { const s = showE(a.a, html, false) + (a.t === 'add' ? '+' : '−') + showE(a.b, html, true); return inner ? '(' + s + ')' : s; }
      case 'mul': return a.k + (a.a.t === 'var' || a.a.t === 'pw' ? '' : '·') + showE(a.a, html, true);
      case 'prod': return showE(a.a, html, true) + '·' + showE(a.b, html, true);
      case 'pw': { const b = showE(a.a, html, true), k = String(a.k); return html ? b + '<sup>' + k + '</sup>' : b + (k === '2' ? '²' : k === '3' ? '³' : '^' + k); }
    }
    return '?';
  }
  function showA(a, top) {
    switch (a.t) {
      case 'prod': return showA(a.a) + '·' + showA(a.b);
      case 'pw': return showA(a.a) + (a.k === 2 ? '²' : a.k === 3 ? '³' : '^' + a.k);
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
  const PRED = { starts: 'מתחילה ב-', ends: 'מסתיימת ב-', has: 'מכילה ', pal: 'פלינדרום', bal: 'מאוזנת' };
  /** תכונת מחרוזת בעברית. ב-html זו יחידה אחת מימין לשמאל, והמילה עצמה בתוכה משמאל לימין: "מסתיימת ב-ab". */
  function pred(c, html, neg) {
    const w = c.op === 'pal' || c.op === 'bal' ? '' : (c.word || 'ε');
    const text = (neg ? 'לא ' : '') + PRED[c.op];
    return html ? '<bdi class="he" dir="rtl">' + text + (w ? '<bdi dir="ltr">' + esc(w) + '</bdi>' : '') + '</bdi>' : text + w;
  }
  function showC(c, html, top) {
    switch (c.op) {
      case 'true': return '';
      case 'false': return '∅';
      case 'and': return top ? c.args.map((x) => showC(x, html)).join(', ') : '(' + c.args.map((x) => showC(x, html)).join(' ' + he('וגם', html) + ' ') + ')';
      case 'or': { const s = c.args.map((x) => showC(x, html)).join(' ' + he('או', html) + ' '); return top ? s : '(' + s + ')'; }
      case 'not': return PRED[c.arg.op] ? pred(c.arg, html, true) : he('לא', html) + ' ' + (c.arg.op === 'cmp' ? '(' + showC(c.arg, html) + ')' : showC(c.arg, html));
      case 'cmp': { const t = showA(c.l, true) + ' ' + RELS[c.rel] + ' ' + showA(c.r, true); return html ? esc(t) : t; }
      default: return pred(c, html, false);
    }
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
      if (ast.kind === 'pattern') return L.patternPretty(ast, html);
      if (ast.kind === 'union' || ast.kind === 'concat') return L.combinedPretty(ast, html);
      if (ast.kind === 'prop') { const c = showC(ast.cond, html, true); return 'L = { w ∈ {' + ast.alphabet.join(',') + '}*' + (c ? ' | ' + c : '') + ' }'; }
      const body = ast.blocks.map((b) => {
        const base = b.word.length > 1 ? '(' + b.word + ')' : b.word, e = b.e;
        if (!e.v && e.d === 1) return base;
        if (e.v && e.v[0] === '_') return base + (e.d ? (html ? '<sup>+</sup>' : '⁺') : '*');
        if (e.gen) { const t = showE(e.t, html, false); if (html) return esc(base) + '<sup>' + t + '</sup>'; const u = sup(t.replace(/−/g, '-')); return base + (u.every(Boolean) ? u.join('') : '^(' + t + ')'); }
        if (e.pow) return html ? esc(base) + '<sup>' + e.pow + '<sup>' + e.v + '</sup></sup>' : base + '^(' + e.pow + (SUPS[e.v] || '^' + e.v) + ')';
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

  Object.assign(L, { evalA, evalC, reps, countOf, slackOf, accepts, words, lengthFor, sample, pretty, mod });
});
