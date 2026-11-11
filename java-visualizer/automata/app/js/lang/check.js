/*
 * lang/check.js - "בדיקה מול השפה": האם המכונה של התלמיד מקבלת בדיוק את השפה שבנוסחה?
 *   exact  - שפה רגולרית + אס"ד/אסל"ד: השוואה מלאה של שני אס"דים (מכפלה). "שווה" כאן הוא הוכחה.
 *   bounded - כל השאר: עוברים על כל המילים עד אורך L ומשווים. זו בדיקה, לא הוכחה.
 * התוצאה בשני המקרים: { exact, ok, missing: [בשפה, והמכונה דוחה], extra: [לא בשפה, והמכונה מקבלת], count, maxLen, undecided }
 */
(function (root, f) {
  const node = typeof module === 'object' && module.exports;
  const L = node ? require('./ns.js') : root.AutomataLang;
  f(L, root.AutomataModel || (node ? require('../model.js') : null), root.AutomataSim || (node ? require('../sim.js') : null));
  if (node) module.exports = L;
})(typeof globalThis !== 'undefined' ? globalThis : this, function (L, M, S) {
  'use strict';
  const LIMIT = 5;
  const BUDGET = { dfa: 20000, nfa: 12000, pda: 2500, tm: 1500 };

  /** האלפבית של הבדיקה: של הנוסחה, ועוד כל אות שהמכונה מכירה (מילה עם אות כזו לא בשפה, ואסור שתתקבל). */
  function alphabetOf(machine, ast) {
    const extra = machine.type === 'tm' ? [] : M.usedInputSymbols(machine);
    return Array.from(new Set([...ast.alphabet, ...(machine.alphabet || []), ...extra])).filter((c) => c !== machine.blank).sort();
  }

  /** האם אפשר בדיקה מדויקת (הוכחה)? */
  function canExact(machine, ast) { return (machine.type === 'dfa' || machine.type === 'nfa') && L.analyze(ast).cls === 'regular'; }

  function checkExact(machine, ast) {
    const A = alphabetOf(machine, ast);
    const lang = L.toDFA(ast);
    const mine = L.minimize(L.determinize(L.nfaFromMachine(machine), A));
    const r = L.compare(lang, mine, LIMIT);
    return { exact: true, ok: r.equal, missing: r.missing, extra: r.extra, count: null, maxLen: null, undecided: 0 };
  }

  /**
   * בדיקה חסומה, בחלקים (כדי שהממשק לא ייתקע): c = checker(machine, ast); while (!c.step(200)) ...; c.result
   * עוצרת מוקדם כשיש LIMIT דוגמאות מכל סוג, או אחרי שנמצאה טעות ועברנו עוד אורך אחד.
   */
  function checker(machine, ast, opts) {
    opts = opts || {};
    const A = alphabetOf(machine, ast);
    const maxLen = L.lengthFor(A.length, opts.budget || BUDGET[machine.type] || 2000, opts.maxLen || 12);
    const it = L.words(A, maxLen);
    const res = { exact: false, ok: true, missing: [], extra: [], count: 0, maxLen, undecided: 0, done: false };
    let stopLen = Infinity;
    return {
      result: res,
      step(n) {
        for (let i = 0; i < n && !res.done; i++) {
          const x = it.next();
          if (x.done || x.value.length > stopLen) { res.done = true; if (!x.done) res.maxLen = stopLen; break; }
          const w = x.value;
          res.count++;
          res.len = w.length;
          const got = S.run(machine, w, { record: false }).result;
          if (got === 'limit') res.undecided++;
          const want = L.accepts(ast, w);
          if (want && got !== 'accept') { if (res.missing.length < LIMIT) res.missing.push(w); } else if (!want && got === 'accept') { if (res.extra.length < LIMIT) res.extra.push(w); } else continue;
          res.ok = false;
          if (stopLen === Infinity) stopLen = w.length + 1;
          if (res.missing.length >= LIMIT && res.extra.length >= LIMIT) res.done = true;
        }
        return res.done;
      },
    };
  }

  /** בדיקה מלאה בבת אחת (לבדיקות יחידה ולמכונות קטנות). */
  function check(machine, ast, opts) {
    if (canExact(machine, ast)) { try { return checkExact(machine, ast); } catch (e) { /* גדול מדי: נופלים לבדיקה חסומה */ } }
    const c = checker(machine, ast, opts);
    while (!c.step(500)) { /* ממשיכים */ }
    return c.result;
  }

  const show = (w) => (w === '' ? 'המילה הריקה' : 'המילה ' + w);
  /** רמז של שורה אחת על הטעות הראשונה, בלי לגלות את הפתרון. */
  function hint(res, machine) {
    if (res.ok) return '';
    const me = { dfa: 'האוטומט שלך', nfa: 'האוטומט שלך', pda: 'האוטומט שלך', tm: 'המכונה שלך' }[machine.type];
    const first = res.missing.length && (!res.extra.length || res.missing[0].length <= res.extra[0].length) ? { w: res.missing[0], miss: true } : { w: res.extra[0], miss: false };
    let s = first.miss ? show(first.w) + ' בשפה, אבל ' + me + ' דוחה אותה.' : me + ' מקבל את ' + show(first.w) + ', אבל היא לא בשפה.';
    if (machine.type === 'tm') s = s.replace('מקבל את', 'מקבלת את');
    if (res.missing.length && !res.extra.length) s += ' כל הטעויות מאותו סוג: יש מילים בשפה שנדחות. אולי חסר מעבר, או שחסר מצב מקבל.';
    else if (res.extra.length && !res.missing.length) s += ' כל הטעויות מאותו סוג: מתקבלות מילים שלא בשפה. אולי יש מצב מקבל מיותר, או מעבר שלא צריך להיות.';
    else s += ' יש טעויות משני הסוגים, אז כדאי להתחיל מהמילה הקצרה ביותר.';
    return s;
  }

  Object.assign(L, { canExact, checkExact, checker, check, hint, checkAlphabet: alphabetOf });
});
