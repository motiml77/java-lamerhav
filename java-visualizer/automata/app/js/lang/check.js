/*
 * lang/check.js - "בדיקה מול השפה": האם המכונה של התלמיד מקבלת בדיוק את השפה שבנוסחה?
 *
 * שלוש רמות, מהחזקה לחלשה. בכל רמה אומרים רק מה שבאמת נבדק:
 *   1. הוכחה (exact): אס"ד / אסל"ד מול שפה רגולרית. משווים את המכונה לאס"ד המינימלי של השפה (מכפלה). "שווה" הוא הוכחה.
 *      האס"ד של השפה עצמו נבדק קודם מול בדיקת השייכות (trustedDFA), כדי שההוכחה לא תישען על בנייה שלא נבדקה.
 *   2. "לא ייתכן" (impossible): אס"ד / אסל"ד מול שפה שהוכח שאינה רגולרית. שום אוטומט סופי לא מקבל אותה בדיוק, ולכן התשובה
 *      אף פעם לא "נכון", גם אם לא נמצאה מילה נגדית.
 *   3. בדיקה יסודית (לא הוכחה): כל השאר, ובעיקר אוטומט מחסנית (לשוויון בין אוטומט מחסנית לשפה אין אלגוריתם). שלושה שלבים:
 *      א. הוכחה חלקית כשאפשר: המכפלה של המכונה עם המשלים של ה"מעטפת" הרגולרית של השפה (השפה עצמה אם היא רגולרית, אחרת צורת
 *         הבלוקים a*b*...). ריקה = הוכח שהמכונה לא מקבלת אף מילה מחוץ למעטפת. לא ריקה = מילה נגדית אמיתית, באורך כלשהו.
 *      ב. כל המילים עד אורך N (לפי תקציב), עם הכרעה מדויקת לכל מילה (pdax.js: בלי "לא הוכרע" באוטומט מחסנית).
 *      ג. מילים ארוכות שנבנות מהנוסחה: כל צירופי החזקות של הבלוקים, ניפוח של מילים מהשפה, שינוי של אות אחת, ומילים אקראיות.
 *
 * התוצאה: { exact, ok, missing: [בשפה, והמכונה דוחה], extra: [לא בשפה, והמכונה מקבלת], count, maxLen, undecided,
 *           long: { count, maxLen } המילים הארוכות שנבדקו, proven: 'noExtra' | 'shape' | null, impossible }
 */
(function (root, f) {
  const node = typeof module === 'object' && module.exports;
  const L = node ? require('./ns.js') : root.AutomataLang;
  f(L, root.AutomataModel || (node ? require('../model.js') : null), root.AutomataSim || (node ? require('../sim.js') : null));
  if (node) module.exports = L;
})(typeof globalThis !== 'undefined' ? globalThis : this, function (L, M, S) {
  'use strict';
  const LIMIT = 5;
  /** כמה מילים בשלב "כל המילים הקצרות", כמה מילים ארוכות, ומה האורך הגדול ביותר שלהן */
  const BUDGET = { dfa: 270000, nfa: 270000, pda: 90000, tm: 1500 };
  const LONG = { dfa: 6000, nfa: 6000, pda: 2500, tm: 0 };
  const LONG_LEN = { dfa: 80, nfa: 80, pda: 48, tm: 0 };
  const isFA = (m) => m.type === 'dfa' || m.type === 'nfa';

  /** האלפבית של הבדיקה: של הנוסחה, ועוד כל אות שהמכונה מכירה (מילה עם אות כזו לא בשפה, ואסור שתתקבל). */
  function alphabetOf(machine, ast) {
    const extra = machine.type === 'tm' ? [] : M.usedInputSymbols(machine);
    return Array.from(new Set([...ast.alphabet, ...(machine.alphabet || []), ...extra])).filter((c) => c !== machine.blank).sort();
  }

  /* ---------- צורת הבלוקים: המילים u1^e1 u2^e2 ... וה"מעטפת" הרגולרית ---------- */
  /** רשימת הבלוקים של הנוסחה (או של כל חלק באיחוד): [[{ word, free, d }]]. null אם אין לנוסחה צורת בלוקים. */
  function shapesOf(ast) {
    const one = (a) => (a.kind === 'blocks' ? a.blocks.map((b) => ({ word: b.word, free: !!(b.e.v || b.e.gen), d: b.e.d || 0 })) : null);
    if (ast.kind === 'blocks') return [one(ast)];
    if (ast.kind === 'concat' && ast.flat) return [one(ast.flat)];
    if (ast.kind === 'union') { const all = ast.parts.map((p) => shapesOf(p)); return all.every(Boolean) ? all.flat() : null; }
    return null;
  }
  /** כל צירופי החזקות (לא רק אלה שבשפה), מהקטנים לגדולים, עד אורך maxLen ועד cap מילים. */
  function shapeWords(ast, maxLen, cap) {
    const shapes = shapesOf(ast), out = new Set();
    if (!shapes) return [];
    const per = Math.max(50, Math.floor(cap / shapes.length));
    for (const bs of shapes) {
      const free = bs.filter((b) => b.free).length, mine = new Set();
      const rec = (i, w, K, hit) => {
        if (mine.size >= per || w.length > maxLen) return;
        if (i === bs.length) { if (hit || !free) mine.add(w); return; }
        const b = bs[i];
        if (!b.free) { rec(i + 1, w + b.word.repeat(Math.max(b.d, 0)), K, hit); return; }
        for (let n = 0; n <= K; n++) rec(i + 1, w + b.word.repeat(n), K, hit || n === K);
      };
      const minLen = free ? Math.min(...bs.filter((b) => b.free).map((b) => b.word.length || 1)) : 1;
      for (let K = 0; mine.size < per && (K === 0 || (free && K * minLen <= maxLen)); K++) rec(0, '', K, false);
      mine.forEach((w) => out.add(w));
    }
    return Array.from(out);
  }
  const symRe = (c) => ({ t: 'sym', c });
  const wordRe = (w, n) => { const a = []; for (let i = 0; i < n; i++) for (const c of w) a.push(symRe(c)); return a.length ? { t: 'cat', a } : { t: 'eps' }; };
  /**
   * אס"ד של שפה רגולרית שמכילה את השפה של הנוסחה: השפה עצמה אם היא רגולרית (exact), אחרת צורת הבלוקים בלי התנאים.
   * מחזיר { dfa, exact } או null.
   */
  function envelope(ast) {
    if (ast._env === undefined) {
      let env = null;
      try {
        const d = trustedDFA(ast);
        if (d) env = { dfa: d, exact: true };
        else {
          const shapes = shapesOf(ast);
          if (shapes) {
            const re = { t: 'alt', a: shapes.map((bs) => ({ t: 'cat', a: bs.map((b) => (b.free ? { t: 'star', a: wordRe(b.word, 1) } : wordRe(b.word, Math.max(b.d, 0)))) })) };
            const dfa = L.minimize(L.determinize(L.nfaFromRegex(re), ast.alphabet));
            // המעטפת חייבת להכיל את השפה: בודקים על המילים הקצרות (אחרת לא משתמשים בה)
            let ok = true;
            for (const w of L.words(ast.alphabet, L.lengthFor(ast.alphabet.length, 1500, 10))) if (L.accepts(ast, w) && !L.dfaAccepts(dfa, w)) { ok = false; break; }
            if (ok) env = { dfa, exact: false };
          }
        }
      } catch (e) { env = null; }
      Object.defineProperty(ast, '_env', { value: env, enumerable: false });
    }
    return ast._env;
  }
  /** המשלים של אס"ד, מעל אלפבית גדול יותר (אות שהאס"ד לא מכיר מובילה למצב מקבל: המילה מחוץ לשפה). */
  function complementOver(d, A) {
    const dead = d.n;
    const tr = d.tr.map((row) => A.map((c) => { const i = d.alphabet.indexOf(c); return i < 0 ? dead : row[i]; }));
    tr.push(A.map(() => dead));
    return { alphabet: A.slice(), n: d.n + 1, start: d.start, acc: d.acc.map((x) => !x).concat([true]), tr };
  }

  /* ---------- 1. הוכחה ---------- */
  /** האס"ד המינימלי של שפה רגולרית, אחרי שנבדק מול בדיקת השייכות (כל המילים הקצרות וצירופי החזקות). null אם אין או אם לא תואם. */
  function trustedDFA(ast) {
    if (ast._dfaOk === undefined) {
      let ok = false;
      try {
        if (L.analyze(ast).cls === 'regular') {
          const d = L.toDFA(ast);
          ok = true;
          for (const w of L.words(ast.alphabet, L.lengthFor(ast.alphabet.length, 3000, 12))) if (L.dfaAccepts(d, w) !== L.accepts(ast, w)) { ok = false; break; }
          if (ok) for (const w of shapeWords(ast, 30, 400)) if (L.dfaAccepts(d, w) !== L.accepts(ast, w)) { ok = false; break; }
        }
      } catch (e) { ok = false; }
      Object.defineProperty(ast, '_dfaOk', { value: ok, enumerable: false });
    }
    return ast._dfaOk ? L.toDFA(ast) : null;
  }
  /** האם אפשר בדיקה מדויקת (הוכחה)? */
  function canExact(machine, ast) { return isFA(machine) && !!trustedDFA(ast); }

  function checkExact(machine, ast) {
    const A = alphabetOf(machine, ast);
    const lang = trustedDFA(ast);
    if (!lang) throw new Error('אין אס"ד בדוק לשפה');
    const mine = L.minimize(L.determinize(L.nfaFromMachine(machine), A));
    const r = L.compare(lang, mine, LIMIT);
    return { exact: true, ok: r.equal, missing: r.missing, extra: r.extra, count: null, maxLen: null, undecided: 0, long: null, proven: null, impossible: false };
  }

  /* ---------- ההכרעה של המכונה: מהירה על עץ הרישות, ומדויקת למילה בודדת ---------- */
  /** מחזיר { start(), step(מצב, אות), verdict(מצב, מילה) -> 'accept' | 'reject' | 'limit', run(מילה) } */
  function decider(machine, A) {
    if (isFA(machine)) {
      let d = null;
      try { d = L.determinize(L.nfaFromMachine(machine), A); } catch (e) { d = null; }      // גדול מדי: הרצה רגילה
      if (d) {
        const col = new Map(A.map((c, i) => [c, i]));
        const run = (w) => { let s = d.start; for (const c of w) { const i = col.get(c); if (i == null) return 'reject'; s = d.tr[s][i]; } return d.acc[s] ? 'accept' : 'reject'; };
        return { start: () => d.start, step: (s, a) => d.tr[s][col.get(a)], verdict: (s) => (d.acc[s] ? 'accept' : 'reject'), run };
      }
    }
    if (machine.type === 'pda') {
      const P = L.pdaCompile(machine), st = L.pdaStepper(P), X = L.PDA_INEXACT;
      const exact = (w) => (L.pdaAccepts(P, w) ? 'accept' : 'reject');
      const run = (w) => { let C = st.start(); for (const c of w) { if (C === null) return 'reject'; if (C === X) return exact(w); C = st.step(C, c); } return C === X ? exact(w) : st.accepts(C) ? 'accept' : 'reject'; };
      return { start: () => st.start(), step: (C, a) => st.step(C, a), verdict: (C, w) => (C === X ? exact(w) : st.accepts(C) ? 'accept' : 'reject'), run, P };
    }
    const run = (w) => S.run(machine, w, { record: false }).result;
    return { start: () => null, step: () => null, verdict: (s, w) => run(w), run };
  }

  /* ---------- 3ג. מילים ארוכות שנבנות מהנוסחה ---------- */
  function prng(text) {
    let s = 2166136261;
    for (let i = 0; i < text.length; i++) s = Math.imul(s ^ text.charCodeAt(i), 16777619) >>> 0;
    return () => { s = (Math.imul(s, 1664525) + 1013904223) >>> 0; return s / 4294967296; };
  }
  /** ניפוח: u v^t x y^t z לכל חלוקה של מילה מהשפה עם |v|, |y| ≤ 2. כך מגיעים למילים ארוכות "מאותו סוג" (גם בשפה וגם מחוצה לה). */
  function pumped(seeds, maxLen, cap) {
    const out = new Set(), T = [2, 3, 5, 8, 13, 21, 34];
    for (const s of seeds) {
      const n = s.length;
      for (let i = 0; i <= n; i++) for (let lv = 0; lv <= 2 && i + lv <= n; lv++) for (let k = i + lv; k <= n; k++) for (let ly = 0; ly <= 2 && k + ly <= n; ly++) {
        if (!lv && !ly) continue;
        for (const t of T) {
          if (n + (lv + ly) * (t - 1) > maxLen) break;
          if (out.size >= cap) return Array.from(out);
          out.add(s.slice(0, i) + s.slice(i, i + lv).repeat(t) + s.slice(i + lv, k) + s.slice(k, k + ly).repeat(t) + s.slice(k + ly));
        }
      }
    }
    return Array.from(out);
  }
  /** שינוי של אות אחת (החלפה, מחיקה, הוספה) בהתחלה, באמצע, בסוף ובגבולות בין רצפים: "כמעט" מילים מהשפה. */
  function nearMisses(w, A) {
    const n = w.length, at = new Set([0, n - 1, n >> 1]);
    for (let i = 1; i < n && at.size < 7; i++) if (w[i] !== w[i - 1]) { at.add(i); at.add(i - 1); }
    const out = [];
    for (const i of at) {
      if (i < 0 || i >= n) continue;
      out.push(w.slice(0, i) + w.slice(i + 1));
      for (const c of A) { if (c !== w[i]) out.push(w.slice(0, i) + c + w.slice(i + 1)); out.push(w.slice(0, i) + c + w.slice(i)); }
    }
    for (const c of A) out.push(w + c);
    return out;
  }

  /**
   * בדיקה יסודית, בחלקים (כדי שהממשק לא ייתקע): c = checker(machine, ast); while (!c.step(200)) ...; c.result
   * עוצרת מוקדם כשיש LIMIT דוגמאות מכל סוג, או אחרי שנמצאה טעות ועברנו עוד אורך אחד. c.hurry(): לסיים מהר (נגמר הזמן).
   */
  function checker(machine, ast, opts) {
    opts = opts || {};
    const A = alphabetOf(machine, ast), type = machine.type;
    const maxLen = L.lengthFor(A.length, opts.budget || BUDGET[type] || 2000, opts.maxLen || 18);
    const res = { exact: false, ok: true, missing: [], extra: [], count: 0, maxLen, undecided: 0, done: false, len: 0, long: null, proven: null, impossible: false };
    const dec = decider(machine, A);
    const seen = new Set();
    const test = (w, got) => {
      if (got === 'limit') res.undecided++;
      const want = L.accepts(ast, w);
      if (want && got !== 'accept') { if (res.missing.length < LIMIT && !seen.has(w)) { seen.add(w); res.missing.push(w); } } else if (!want && got === 'accept') { if (res.extra.length < LIMIT && !seen.has(w)) { seen.add(w); res.extra.push(w); } } else return want;
      res.ok = false;
      return want;
    };

    // שלב א: מה שאפשר להוכיח
    let witness = null;
    const cls = L.analyze(ast).cls;
    if (isFA(machine) && (cls === 'cf' || cls === 'noncf')) res.impossible = true;
    if (type !== 'tm' && opts.proofs !== false) {
      try {
        const env = envelope(ast);
        if (env) {
          const comp = complementOver(env.dfa, A), col = new Map(A.map((c, i) => [c, i]));
          let w = null;
          if (type === 'pda') w = L.pdaReach(dec.P, { n: comp.n, start: comp.start, step: (d, c) => (col.has(c) ? comp.tr[d][col.get(c)] : -1), acc: (d) => comp.acc[d] }).found;
          else { const r = L.compare(env.dfa, L.minimize(L.determinize(L.nfaFromMachine(machine), A)), 1); w = r.extra.length ? r.extra[0] : null; }
          if (w == null) res.proven = env.exact ? 'noExtra' : 'shape';
          else if (!L.accepts(ast, w) && dec.run(w) === 'accept') witness = w;       // בדיקה כפולה של העד, ישירות
        }
      } catch (e) { /* בלי הוכחה חלקית */ }
    }

    // שלב ב: כל המילים עד maxLen, שכבה אחרי שכבה (מצב המכונה אחרי כל רישא נשמר, וההמשכים ממשיכים ממנו)
    let cur = [['', dec.start()]], next = [], idx = 0, n = 0, stopLen = Infinity, phase = 'short', countBefore = 0;
    const seeds = [];             // מילים קצרות מהשפה, לניפוח
    // שלב ג
    let longList = null, li = 0, longIn = [], nearDone = false;
    const longCap = opts.long == null ? LONG[type] || 0 : opts.long, longLen = LONG_LEN[type] || 0;
    const startLong = () => {
      phase = 'long';
      if (!longCap || !res.ok || witness != null) { phase = 'end'; return; }
      const rnd = prng(ast.text || JSON.stringify(ast.alphabet)), set = new Set();
      const add = (w) => { if (w.length > res.maxLen && w.length <= longLen) set.add(w); };
      shapeWords(ast, longLen, Math.floor(longCap * 0.45)).forEach(add);
      for (let i = 0; i < Math.floor(longCap * 0.1); i++) { let w = ''; const len = res.maxLen + 1 + Math.floor(rnd() * 10); for (let j = 0; j < len; j++) w += A[Math.floor(rnd() * A.length)]; add(w); }
      const room = Math.floor(longCap * 0.75) - set.size;          // מה שצירופי החזקות לא ניצלו עובר לניפוח
      pumped(seeds, longLen, Math.max(room, Math.floor(longCap * 0.2)) * 2).forEach((w) => { if (set.size < Math.floor(longCap * 0.75)) add(w); });
      longList = Array.from(set).sort((a, b) => a.length - b.length || (a < b ? -1 : 1));
      res.long = { count: 0, maxLen: 0 };
    };
    const finish = () => {
      if (res.ok && witness != null) { res.extra.push(witness); res.ok = false; }
      if (res.impossible) res.ok = false;
      res.done = true;
    };

    return {
      result: res,
      /** עצירה מיידית (הזמן נגמר לגמרי): מה שנבדק עד עכשיו הוא התוצאה */
      stop() { if (res.done) return; if (phase === 'short') { res.maxLen = Math.max(0, Math.min(maxLen, stopLen, n - 1)); res.count = countBefore; } phase = 'end'; finish(); },
      /** נגמר הזמן: מסיימים את האורך הנוכחי, ועוברים לכמה מילים ארוכות */
      hurry() { if (phase === 'short' && n > 0 && n - 1 < Math.min(maxLen, stopLen)) { stopLen = n - 1; res.count = countBefore; } else if (phase === 'long' && longList) longList.length = Math.min(longList.length, li + 300); },
      step(k) {
        for (let i = 0; i < k && !res.done; i++) {
          if (phase === 'short') {
            if (idx === cur.length) { cur = next; next = []; idx = 0; n++; countBefore = res.count; }
            if (n > Math.min(maxLen, stopLen) || !cur.length) { res.maxLen = Math.min(maxLen, stopLen); if (res.ok) startLong(); else phase = 'end'; continue; }
            const [w, st] = cur[idx++];
            if (n < Math.min(maxLen, stopLen)) for (const a of A) next.push([w + a, dec.step(st, a)]);
            res.count++;
            res.len = n;
            const wasOk = res.ok, want = test(w, dec.verdict(st, w));
            if (want && seeds.length < 40 && n >= 1) seeds.push(w);
            if (wasOk && !res.ok && stopLen === Infinity) stopLen = n + 1;
            if (res.missing.length >= LIMIT && res.extra.length >= LIMIT) phase = 'end';
          } else if (phase === 'long') {
            if (li < longList.length) {
              const w = longList[li++];
              res.long.count++;
              if (w.length > res.long.maxLen) res.long.maxLen = w.length;
              if (test(w, dec.run(w)) && longIn.length < 120) longIn.push(w);
              if (!res.ok && res.missing.length + res.extra.length >= LIMIT) phase = 'end';
            } else if (!nearDone && res.ok) {
              // מילים "כמעט" בשפה: שינוי של אות אחת במילים ארוכות מהשפה
              nearDone = true;
              const set = new Set(), cap = Math.floor(longCap * 0.3);
              for (const w of longIn) { for (const x of nearMisses(w, A)) if (x.length > res.maxLen && set.size < cap) set.add(x); if (set.size >= cap) break; }
              longList = Array.from(set); li = 0;
            } else phase = 'end';
          } else finish();
        }
        return res.done;
      },
    };
  }

  /** בדיקה מלאה בבת אחת (לבדיקות יחידה, למאגר ולמכונות קטנות). */
  function check(machine, ast, opts) {
    if (canExact(machine, ast)) { try { return checkExact(machine, ast); } catch (e) { /* גדול מדי: נופלים לבדיקה היסודית */ } }
    const c = checker(machine, ast, opts);
    while (!c.step(500)) { /* ממשיכים */ }
    return c.result;
  }

  const show = (w) => (w === '' ? 'המילה הריקה' : 'המילה ' + w);
  const IMPOSSIBLE = 'השפה הזו לא רגולרית, ולכן שום אס"ד או אסל"ד לא מקבל בדיוק אותה: אוטומט סופי לא יכול לזכור מספר שאין לו גבול. צריך אוטומט מחסנית (או מכונת טיורינג).';
  /** רמז של שורה אחת על הטעות הראשונה, בלי לגלות את הפתרון. */
  function hint(res, machine) {
    if (res.ok) return '';
    const me = { dfa: 'האוטומט שלך', nfa: 'האוטומט שלך', pda: 'האוטומט שלך', tm: 'המכונה שלך' }[machine.type];
    if (!res.missing.length && !res.extra.length) return res.impossible ? IMPOSSIBLE : '';
    const first = res.missing.length && (!res.extra.length || res.missing[0].length <= res.extra[0].length) ? { w: res.missing[0], miss: true } : { w: res.extra[0], miss: false };
    let s = first.miss ? show(first.w) + ' בשפה, אבל ' + me + ' דוחה אותה.' : me + ' מקבל את ' + show(first.w) + ', אבל היא לא בשפה.';
    if (machine.type === 'tm') s = s.replace('מקבל את', 'מקבלת את');
    if (res.impossible) return s + ' ' + IMPOSSIBLE;
    if (res.missing.length && !res.extra.length) s += ' כל הטעויות מאותו סוג: יש מילים בשפה שנדחות. אולי חסר מעבר, או שחסר מצב מקבל.';
    else if (res.extra.length && !res.missing.length) s += ' כל הטעויות מאותו סוג: מתקבלות מילים שלא בשפה. אולי יש מצב מקבל מיותר, או מעבר שלא צריך להיות.';
    else s += ' יש טעויות משני הסוגים, אז כדאי להתחיל מהמילה הקצרה ביותר.';
    return s;
  }

  Object.assign(L, { canExact, checkExact, checker, check, hint, checkAlphabet: alphabetOf, trustedDFA, envelope, shapeWords, IMPOSSIBLE_HINT: IMPOSSIBLE });
});
