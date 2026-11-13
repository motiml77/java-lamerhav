/*
 * lang/pdax.js - הכרעה מדויקת לאוטומט מחסנית, בלי מגבלת צעדים ובלי "לא הוכרע".
 *
 * ההרצה הרגילה (sim.js) היא BFS על קונפיגורציות, והיא נעצרת במגבלה כשיש לולאת ε שדוחפת. כאן האלגוריתם הקלאסי של
 * "סיכומים" (כמו המרת אוטומט מחסנית לדקדוק, [p X q]): לא עוקבים אחרי המחסנית כולה, אלא אחרי "ראשים" (מצב, מקום בקלט, סמל בראש),
 * ולכל ראש אוספים לאן אפשר להגיע כשהסמל שבראש נשלף. מספר הראשים סופי, ולכן זה תמיד נעצר, והתשובה מדויקת.
 *
 *   pdaReach(P, D)   המכפלה של האוטומט עם אס"ד D: האם יש מילה ש-P מקבל ו-D מקבל, ואיזו (עד: מילה אמיתית).
 *                    D = { n, start, step(d, אות) -> d' או -1, acc(d) }.
 *   pdaAccepts(P, w) שייכות של מילה אחת (D הוא "שרשרת" של המילה).
 *   pdaStepper(P)    הרצה מהירה לבדיקת כל המילים הקצרות: קבוצת הקונפיגורציות אחרי כל רישא, משותפת לכל ההמשכים.
 *                    כשסגור ה-ε לא נגמר (לולאת דחיפה) הוא מחזיר INEXACT, והבדיקה עוברת ל-pdaAccepts.
 *
 * סמנטיקה (כמו sim.js): תווית = קריאה (אות או ε), שליפה (סמל או ε = בלי לשלוף, גם על מחסנית ריקה), דחיפה (מחרוזת, התו הראשון בראש).
 * קבלה: מצב מקבל בסוף המילה, או (acceptBy = 'empty') מחסנית ריקה בסוף המילה.
 */
(function (root, f) {
  const node = typeof module === 'object' && module.exports;
  const L = node ? require('./ns.js') : root.AutomataLang;
  f(L);
  if (node) module.exports = L;
})(typeof globalThis !== 'undefined' ? globalThis : this, function (L) {
  'use strict';

  /** המכונה של הכלי -> מבנה מהיר: מצבים וסמלי מחסנית כמספרים. הסמל האחרון (BOT) הוא תחתית המחסנית, ואף כלל לא שולף אותו. */
  function pdaCompile(m) {
    const idx = new Map(m.states.map((s, i) => [s.id, i]));
    const G = [], gi = new Map();
    const sym = (c) => { if (!gi.has(c)) { gi.set(c, G.length); G.push(c); } return gi.get(c); };
    const rules = m.states.map(() => []);
    let nSeq = 0;
    for (const t of m.transitions) for (const l of t.labels) {
      if (!idx.has(t.from) || !idx.has(t.to)) continue;
      const push = Array.from(l.push || '').map(sym);
      push.id = nSeq++;
      rules[idx.get(t.from)].push({ read: l.read || '', pop: l.pop ? sym(l.pop) : -1, push, to: idx.get(t.to), keep: new Map() });
    }
    const init = Array.from(m.stackStart || '').map(sym);
    const P = { nQ: m.states.length, start: idx.has(m.start) ? idx.get(m.start) : -1, acc: m.states.map((s) => !!s.accepting), byEmpty: m.acceptBy === 'empty', rules, init, G, BOT: G.length, nG: G.length + 1, nSeq };
    P.init0 = init.concat([P.BOT]);
    P.init0.id = P.nSeq++;
    return P;
  }
  /** מה נשאר במחסנית במקום הסמל x אחרי הכלל r (התו הראשון בראש). בכלל בלי שליפה, x עצמו נשאר מתחת למה שנדחף. */
  function seqOf(P, r, x) {
    if (r.pop >= 0) return r.push;
    let s = r.keep.get(x);
    if (!s) { s = r.push.concat([x]); s.id = P.nSeq++; r.keep.set(x, s); }
    return s;
  }

  /**
   * המכפלה של P עם האס"ד D. מחזיר { found: מילה ש-P ו-D מקבלים, או null }.
   * כשאין מילה כזו (found === null) זו הוכחה: עברנו על כל הראשים הישיגים.
   */
  function pdaReach(P, D) {
    if (P.start < 0) return { found: null };
    const nD = D.n, nG = P.nG, BOT = P.BOT;
    const heads = new Map(), pops = new Map(), waits = new Map(), waitSeen = new Set(), queue = [], popQ = [];
    let found = null;
    const addHead = (q, d, x, word) => {
      const k = (q * nD + d) * nG + x;
      if (heads.has(k)) return k;
      heads.set(k, word);
      if (found === null && D.acc(d) && (P.byEmpty ? x === BOT : P.acc[q])) found = word;
      queue.push(k);
      return k;
    };
    const addPop = (k, qd, word) => {
      let ps = pops.get(k);
      if (!ps) pops.set(k, (ps = new Map()));
      if (ps.has(qd)) return;
      ps.set(qd, word);
      popQ.push(k, qd, word);
    };
    /** הסמלים seq[off..] עומדים במקום הסמל של הראש target; pre: מה שנקרא מאז target. */
    const chain = (q, d, seq, off, target, pre) => {
      const x = seq[off];
      const k = addHead(q, d, x, (target < 0 ? '' : heads.get(target)) + pre);
      if (x === BOT) return;
      const id = k + '|' + seq.id + '|' + off + '|' + target;
      if (waitSeen.has(id)) return;
      waitSeen.add(id);
      const w = { seq, off, target, pre };
      if (!waits.has(k)) waits.set(k, []);
      waits.get(k).push(w);
      const ps = pops.get(k);
      if (ps) for (const [qd, u] of Array.from(ps)) resume(w, qd, u);
    };
    const resume = (w, qd, u) => {
      if (w.off === w.seq.length - 1) { if (w.target >= 0) addPop(w.target, qd, w.pre + u); } else chain(Math.floor(qd / nD), qd % nD, w.seq, w.off + 1, w.target, w.pre + u);
    };
    chain(P.start, D.start, P.init0, 0, -1, '');
    while (found === null && (queue.length || popQ.length)) {
      if (popQ.length) {
        const word = popQ.pop(), qd = popQ.pop(), k = popQ.pop(), ws = waits.get(k);
        if (ws) for (const w of ws.slice()) resume(w, qd, word);
        continue;
      }
      const k = queue.pop(), x = k % nG, qd = (k - x) / nG, d = qd % nD, q = (qd - d) / nD;
      for (const r of P.rules[q]) {
        if (r.pop >= 0 && r.pop !== x) continue;
        const d2 = r.read === '' ? d : D.step(d, r.read);
        if (d2 < 0) continue;
        const seq = seqOf(P, r, x);
        if (!seq.length) addPop(k, r.to * nD + d2, r.read); else chain(r.to, d2, seq, 0, k, r.read);
      }
    }
    return { found };
  }

  /** האם P מקבל את המילה w? מדויק תמיד. */
  function pdaAccepts(P, w) {
    const a = Array.from(w), n = a.length;
    return pdaReach(P, { n: n + 1, start: 0, step: (d, c) => (d < n && a[d] === c ? d + 1 : -1), acc: (d) => d === n }).found !== null;
  }

  /* ---------- הרצה מהירה על עץ הרישות ---------- */
  const INEXACT = { inexact: true };
  /**
   * קבוצת הקונפיגורציות (מצב + מחסנית) אחרי רישא, סגורה ל-ε. start(), step(C, אות), accepts(C).
   * C יכול להיות null (אין דרך פתוחה: כל המשך יידחה) או INEXACT (הסגור גדול מדי: צריך pdaAccepts).
   */
  function pdaStepper(P, caps) {
    caps = Object.assign({ configs: 3000, stack: 160 }, caps);
    const ch = (i) => String.fromCharCode(48 + i), BOT = P.BOT;
    const close = (seed) => {
      const seen = new Set(seed), list = Array.from(seen);
      for (let i = 0; i < list.length; i++) {
        const c = list[i], q = c.charCodeAt(0) - 48, top = c.length > 1 ? c.charCodeAt(1) - 48 : BOT;
        for (const r of P.rules[q]) {
          if (r.read !== '' || (r.pop >= 0 && r.pop !== top)) continue;
          const nc = ch(r.to) + r.pushStr + c.slice(r.pop >= 0 ? 2 : 1);
          if (seen.has(nc)) continue;
          if (nc.length > caps.stack || list.length >= caps.configs) return INEXACT;
          seen.add(nc); list.push(nc);
        }
      }
      return list;
    };
    for (const rs of P.rules) for (const r of rs) if (r.pushStr == null) r.pushStr = r.push.map(ch).join('');
    return {
      start() { return P.start < 0 ? null : close([ch(P.start) + P.init.map(ch).join('')]); },
      step(C, a) {
        if (C === null || C === INEXACT) return C;
        const seed = [];
        for (const c of C) {
          const q = c.charCodeAt(0) - 48, top = c.length > 1 ? c.charCodeAt(1) - 48 : BOT;
          for (const r of P.rules[q]) if (r.read === a && (r.pop < 0 || r.pop === top)) seed.push(ch(r.to) + r.pushStr + c.slice(r.pop >= 0 ? 2 : 1));
        }
        if (!seed.length) return null;
        return close(seed);
      },
      accepts(C) { return C !== null && C.some((c) => (P.byEmpty ? c.length === 1 : P.acc[c.charCodeAt(0) - 48])); },
    };
  }

  Object.assign(L, { pdaCompile, pdaReach, pdaAccepts, pdaStepper, PDA_INEXACT: INEXACT });
});
