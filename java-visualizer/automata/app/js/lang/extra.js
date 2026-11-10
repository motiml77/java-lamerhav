/*
 * lang/extra.js - תוספות לכתיב שהדוגמאות המוכנות צריכות (מעבר לבלוקים, לתכונות ולביטוי רגולרי):
 *   1. תבנית של מילה:   w w^R ,  ww ,  w#w ,  w c w^R        ברירת מחדל w ∈ {a,b}*; אפשר לכתוב אחרי | אלפבית אחר.
 *      AST: { kind:'pattern', items:[{w:true, rev?} | {lit}], base, alphabet }
 *      השייכות פשוטה: אורך המילה קובע את |w|, ואז רק משווים.
 *   2. התכונה "מאוזנת" (סוגריים מאוזנים): w ∈ {(,)}* | מאוזנת      הצומת {op:'bal', open, close} מפוענח ב-parse.js.
 *   3. חזקה של 2 בתוך חזקה: a^(2^n)                                  ב-parse.js (e.pow) וב-oracle.js (L.reps).
 * כאן: הפענוח של התבנית, השייכות, הכתיבה היפה, הסיווג, ובניית אוטומט מחסנית ל-wwᴿ, ל-w c wᴿ, לפלינדרום ולסוגריים מאוזנים.
 */
(function (root, f) {
  const node = typeof module === 'object' && module.exports;
  const L = node ? require('./ns.js') : root.AutomataLang;
  f(L, root.AutomataModel || (node ? require('../model.js') : null));
  if (node) module.exports = L;
})(typeof globalThis !== 'undefined' ? globalThis : this, function (L, M) {
  'use strict';
  const isW = (t) => t.t === 'ch' && t.v === 'w';
  const isHashW = (t) => t.t === 'cnt' && t.v === 'w' && t.end - t.pos === 2;      // "#w" נקרא כ-# ואחריו w
  const isEnd = (t) => !t || t.t === '|' || t.t === ':' || t.t === ',' || t.t === 'eof';

  /** האם האסימונים הם תבנית של מילה: מתחילים ב-w, יש לפחות שני w, ושום דבר מלבד אותיות ו-ᴿ. */
  function isPattern(toks) {
    if (!toks.length || !isW(toks[0]) || (toks[1] || {}).t === 'in') return false;
    let n = 0;
    for (let i = 0; i < toks.length && !isEnd(toks[i]); i++) {
      const t = toks[i];
      if (isW(t) || isHashW(t)) n++;
      else if (t.t === '^' && (toks[i + 1] || {}).v === 'R') i++;
      else if (!(t.t === 'ch' || t.t === 'num' || (t.t === 'sup' && t.v === 'R'))) return false;
    }
    return n >= 2;
  }

  function parsePattern(p, opts) {
    const items = [];
    const lit = (c) => { const last = items[items.length - 1]; if (last && last.lit != null) last.lit += c; else items.push({ lit: c }); };
    while (!isEnd(p.peek())) {
      const x = p.next();
      if (isHashW(x)) { lit('#'); items.push({ w: true }); }
      else if (isW(x)) items.push({ w: true });
      else if (x.t === 'ch' || x.t === 'num') lit(x.v);
      else {
        if (x.t === '^') p.next();
        const last = items[items.length - 1];
        if (!last || !last.w || last.rev) p.fail('ᴿ (היפוך) בא רק מיד אחרי w', 'למשל w w^R', x);
        last.rev = true;
      }
    }
    let base = null;
    if (!p.is('eof')) {
      p.next();
      if (!(isW(p.peek()) && p.peek(1).t === 'in')) p.fail('אחרי התבנית אפשר לכתוב רק מאיזה אלפבית w', 'למשל w w^R | w ∈ {a,b}*');
      p.next(); p.next();
      p.expect('{', 'אחרי ∈ צריך לבוא האלפבית בסוגריים מסולסלים', 'למשל w ∈ {a,b}*');
      base = [];
      while (p.is('ch') || p.is('num')) { for (const c of p.next().v) if (!base.includes(c)) base.push(c); if (p.is(',')) p.next(); }
      p.expect('}', 'חסר } שסוגר את האלפבית', 'למשל {a,b}*');
      if (!base.length) p.fail('האלפבית ריק', 'למשל {a,b}*');
      if (p.is('*')) p.next();
    }
    const lits = Array.from(new Set(items.map((i) => i.lit || '').join('')));
    if (!base) {
      base = ((opts && opts.alphabet) || []).filter((c) => !lits.includes(c));
      for (const d of ['a', 'b']) if (base.length < 2 && !base.includes(d) && !lits.includes(d)) base.push(d);
      base.sort();
    }
    return { kind: 'pattern', items, base, alphabet: Array.from(new Set(base.concat(lits))) };
  }

  const reverse = (s) => Array.from(s).reverse().join('');
  function patternAccepts(ast, word) {
    const k = ast.items.filter((i) => i.w).length;
    const n = (word.length - ast.items.reduce((s, i) => s + (i.lit || '').length, 0)) / k;
    if (n < 0 || !Number.isInteger(n)) return false;
    let pos = 0, w = null;
    for (const it of ast.items) {
      if (it.lit != null) { if (!word.startsWith(it.lit, pos)) return false; pos += it.lit.length; continue; }
      const part = it.rev ? reverse(word.slice(pos, pos + n)) : word.slice(pos, pos + n);
      pos += n;
      if (w === null) { for (const c of part) if (!ast.base.includes(c)) return false; w = part; } else if (part !== w) return false;
    }
    return true;
  }

  function patternPretty(ast, html) {
    const body = ast.items.map((i) => (i.lit != null ? i.lit : 'w' + (i.rev ? (html ? '<sup>R</sup>' : 'ᴿ') : ''))).join('');
    return 'L = { ' + body + ' | w ∈ {' + ast.base.join(',') + '}* }';
  }

  const WHY = {
    mirror: 'צריך לזכור את כל החצי הראשון כדי להשוות אותו, בסדר הפוך, לחצי השני. זה בדיוק מה שמחסנית עושה',
    copy: 'צריך להשוות את החצי השני לראשון באותו סדר, ומחסנית מחזירה את מה שנדחף רק בסדר הפוך',
    bal: 'צריך לספור כמה סוגריים נפתחו ועוד לא נסגרו, ולמספר הזה אין חסם. מחסנית עושה בדיוק את זה',
    pow: 'מעל אות אחת, כל שפה חסרת הקשר היא גם רגולרית. השפה הזו לא רגולרית: המרווחים בין אורכי המילים גדלים בלי סוף',
  };
  /** סיווג של תבנית: רק המקרים הקלאסיים, שיש להם הוכחה מוכרת. כל השאר: לא ידוע. */
  function analyzePattern(ast) {
    const sig = ast.items.map((i) => (i.lit != null ? 'c' : i.rev ? 'r' : 'w')).join('');
    const sep = ast.items.every((i) => i.lit == null || Array.from(i.lit).every((c) => !ast.base.includes(c)));
    const two = ast.base.length >= 2;
    if ((sig === 'wr' && two) || (sig === 'wcr' && sep)) return { cls: 'cf', why: WHY.mirror };
    if ((sig === 'ww' || (sig === 'wcw' && sep)) && two) return { cls: 'noncf', why: WHY.copy };
    return null;
  }

  /* ---------- אוטומט מחסנית ל-wwᴿ, ל-w c wᴿ ולסוגריים מאוזנים ---------- */
  function pda(alphabet, stack, states, rules) {
    const m = M.createMachine('pda', { alphabet, stackAlphabet: stack, stackStart: 'Z', acceptBy: 'final' });
    states.forEach((acc, i) => M.addState(m, { id: 's' + i, name: 'q' + i, accepting: acc }));
    m.start = 's0';
    for (const [from, to, read, pop, push] of rules) M.addTransition(m, 's' + from, 's' + to, [{ read, pop, push }]);
    L.layoutMachine(m);
    return m;
  }
  function buildExtraPDA(ast) {
    const pal = ast.kind === 'prop' && ast.cond.op === 'pal';        // פלינדרום: כמו wwᴿ, ובאמצע מותרת גם אות אחת
    if (ast.kind === 'pattern' || pal) {
      const sig = pal ? 'wr' : ast.items.map((i) => (i.lit != null ? 'c' : i.rev ? 'r' : 'w')).join('');
      if (sig !== 'wr' && sig !== 'wcr') return null;
      if (pal) ast = { alphabet: ast.alphabet, base: ast.alphabet };
      const mid = sig === 'wcr' ? Array.from(ast.items[1].lit) : [];
      const used = new Set(ast.alphabet.concat(['Z']));
      const sym = ast.base.map((c) => { const up = c.toUpperCase(); const s = up !== c && !used.has(up) ? up : Array.from('ABCDEFGHIJKLMNOPQRSTUVWXY').find((x) => !used.has(x)); used.add(s); return s; });
      const rules = [], states = [false];
      ast.base.forEach((c, i) => rules.push([0, 0, c, '', sym[i]]));
      let cur = 0;
      if (!mid.length) { states.push(false); rules.push([0, 1, '', '', '']); cur = 1; if (pal) for (const c of ast.base) rules.push([0, 1, c, '', '']); }
      for (const c of mid) { states.push(false); rules.push([cur, cur + 1, c, '', '']); cur++; }
      ast.base.forEach((c, i) => rules.push([cur, cur, c, sym[i], '']));
      states.push(true);
      rules.push([cur, cur + 1, '', 'Z', 'Z']);
      return pda(ast.alphabet, ['Z'].concat(sym), states, rules);
    }
    if (ast.kind === 'prop' && ast.cond.op === 'bal') {
      const { open, close } = ast.cond;
      const rules = [[0, 0, open, '', 'A'], [0, 0, close, 'A', ''], [0, 1, '', 'Z', 'Z']];
      for (const c of ast.alphabet) if (c !== open && c !== close) rules.push([0, 0, c, '', '']);
      return pda(ast.alphabet, ['Z', 'A'], [false, true], rules);
    }
    return null;
  }

  Object.assign(L, { isPattern, parsePattern, patternAccepts, patternPretty, analyzePattern, buildExtraPDA, EXTRA_WHY: WHY });
});
