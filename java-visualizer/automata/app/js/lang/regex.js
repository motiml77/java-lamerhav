/*
 * lang/regex.js - צורה 3: ביטוי רגולרי. איחוד: | או ∪ או + (בין שני ביטויים, כמו בספרי הלימוד). חזרות: * ו-⁺ (או + בסוף ביטוי).
 * עץ: {t:'sym',c} {t:'eps'} {t:'empty'} {t:'any'} {t:'cat',a:[]} {t:'alt',a:[]} {t:'star',a} {t:'plus',a}
 * כולל בדיקת שייכות ישירה על העץ (reMatch), בלי אוטומט, כדי שהיא תהיה בלתי תלויה בבנייה של האס"ד.
 */
(function (root, f) {
  const node = typeof module === 'object' && module.exports;
  const L = node ? require('./ns.js') : root.AutomataLang;
  f(L);
  if (node) module.exports = L;
})(typeof globalThis !== 'undefined' ? globalThis : this, function (L) {
  'use strict';
  const startsAtom = (t) => t.t === 'ch' || t.t === 'num' || t.t === '(' || t.t === 'eps' || t.t === 'empty' || t.t === 'sigma' || t.t === 'dot';

  function parseRegex(p, opts) {
    const symbols = new Set();
    let usesAny = false;
    function atom() {
      const x = p.peek();
      if (x.t === 'ch') { p.next(); symbols.add(x.v); return { t: 'sym', c: x.v }; }
      if (x.t === 'num') { p.next(); for (const c of x.v) symbols.add(c); const a = Array.from(x.v).map((c) => ({ t: 'sym', c })); return a.length === 1 ? a[0] : { t: 'cat', a }; }
      if (x.t === 'eps') { p.next(); return { t: 'eps' }; }
      if (x.t === 'empty') { p.next(); return { t: 'empty' }; }
      if (x.t === 'sigma' || x.t === 'dot') { p.next(); usesAny = true; return { t: 'any' }; }
      if (x.t === '(') {
        p.next();
        if (p.is(')')) p.fail('סוגריים ריקים', 'למילה הריקה כותבים ε');
        const a = alt();
        p.expect(')', 'חסר סוגר סוגר )', 'כל ( צריך ) שסוגר אותו');
        return a;
      }
      if (x.t === '*' || x.t === '+' || x.t === 'sup') p.fail('לפני ' + (x.t === '*' ? '*' : '+') + ' צריך לבוא ביטוי', 'למשל a* או (ab)*');
      if (x.t === 'he') p.fail('לא הצלחתי להבין את המילה "' + x.v + '" כאן', 'בתיאור של מילה w אפשר לכתוב: מתחילה ב-, מסתיימת ב-, מכילה');
      p.fail(x.t === 'eof' ? 'הביטוי נקטע באמצע' : 'כאן צריכה לבוא אות או סוגר', 'למשל (a|b)*abb');
    }
    function rep() {
      let a = atom();
      for (;;) {
        const x = p.peek();
        if (x.t === '*') { p.next(); a = { t: 'star', a }; }
        else if (x.t === 'sup' && (x.v === '+' || x.v === '*')) { p.next(); a = { t: x.v === '+' ? 'plus' : 'star', a }; }
        else if (x.t === '^' && (p.peek(1).t === '+' || p.peek(1).t === '*')) { p.next(); a = { t: p.next().t === '+' ? 'plus' : 'star', a }; }
        else if (x.t === '+' && !startsAtom(p.peek(1))) { p.next(); a = { t: 'plus', a }; }   // + בסוף ביטוי = אחד או יותר
        else return a;
      }
    }
    function cat() {
      const a = [];
      while (startsAtom(p.peek())) a.push(rep());
      if (!a.length) p.fail(p.is('eof') ? 'הביטוי נקטע באמצע' : 'כאן חסר ביטוי', 'למילה הריקה כותבים ε');
      return a.length === 1 ? a[0] : { t: 'cat', a };
    }
    function alt() {
      const a = [cat()];
      while (p.is('|') || p.is('union') || p.is('+')) { p.next(); a.push(cat()); }
      return a.length === 1 ? a[0] : { t: 'alt', a };
    }
    const re = alt();
    const alphabet = Array.from(new Set([...symbols, ...(usesAny && opts && opts.alphabet ? opts.alphabet : [])])).sort();
    if (!alphabet.length) alphabet.push('a', 'b');
    return { kind: 'regex', re, alphabet };
  }

  /** קבוצת מקומות הסיום האפשריים כשמתחילים מ-starts (מערך בוליאני באורך |w|+1). */
  function ends(n, w, starts, alphabet) {
    const N = w.length + 1;
    const out = new Array(N).fill(false);
    switch (n.t) {
      case 'eps': return starts.slice();
      case 'empty': return out;
      case 'sym': for (let i = 0; i < N - 1; i++) if (starts[i] && w[i] === n.c) out[i + 1] = true; return out;
      case 'any': for (let i = 0; i < N - 1; i++) if (starts[i] && alphabet.includes(w[i])) out[i + 1] = true; return out;
      case 'cat': { let cur = starts; for (const x of n.a) cur = ends(x, w, cur, alphabet); return cur; }
      case 'alt': { for (const x of n.a) { const e = ends(x, w, starts, alphabet); for (let i = 0; i < N; i++) out[i] = out[i] || e[i]; } return out; }
      case 'star': case 'plus': {
        let acc = n.t === 'star' ? starts.slice() : ends(n.a, w, starts, alphabet);
        for (;;) {
          const more = ends(n.a, w, acc, alphabet);
          let grew = false;
          for (let i = 0; i < N; i++) if (more[i] && !acc[i]) { acc[i] = true; grew = true; }
          if (!grew) return acc;
        }
      }
    }
    throw new Error('regex node ' + n.t);
  }
  function reMatch(re, word, alphabet) {
    const w = Array.from(word);
    const s = new Array(w.length + 1).fill(false);
    s[0] = true;
    return ends(re, w, s, alphabet)[w.length];
  }

  L.parseRegex = parseRegex;
  L.reMatch = reMatch;
});
