/*
 * lang/vec.js - סיווג של שפות בלוקים עם חזקות בכמה משתנים: aⁿ⁺ᵐbⁿ, aⁿbᵐcⁿ⁺ᵐ, aⁿ⁺ᵐbⁿ⁻ᵐ | n ≥ m, aⁿbᵐcᵏ | k = n+m.
 * וגם של חזקות לא ליניאריות (n², n·m, 2ⁿ), שם רק טענה אחת: "לא חסרת הקשר" כשמספר החזרות גדל בלי סוף.
 *
 * צורה נורמלית: בלוקים u_i עם חזקה E_i = k_i + Σ c_iv·v, כל c_iv ≥ 0, ותנאי שכל ההשוואות בו "בטוחות" (classify.js).
 * מגיעים אליה בהצבות, שלא משנות את השפה:
 *   שוויון      y = ביטוי (ביטוי עם מקדמים אי-שליליים)            k = n + m   ->  k מוחלף ב-n + m
 *   אי-שוויון   y ≥ ביטוי  ->  y = ביטוי + s, עם משתנה חדש s ≥ 0   n ≤ m       ->  m מוחלף ב-n + s
 *   מינוס בחזקה y − m + d = p  ->  y = p + m − d                    aⁿ⁺ᵐbⁿ⁻ᵐ  ->  a²ᵐ⁺ᵖbᵖ
 * העמודה של משתנה v היא (c_1v, …, c_kv), והתמיכה שלו היא הבלוקים שבהם c_iv > 0. U: המשתנים שיכולים לגדול בלי גבול.
 * הכללים (ההוכחות ב-SPEC 13.3):
 *   R1  כל משתנה ב-U בבלוק אחד לכל היותר                               -> רגולרית
 *   R3  משתנה ב-U בכמה בלוקים, ובכל אחד מהם עוד משתנה חופשי ("ממלא")     -> רגולרית
 *   C1  כל משתנה ב-U בשני בלוקים לכל היותר, ואין שני זוגות מצטלבים       -> חסרת הקשר (ונבנה אוטומט מחסנית)
 *   NR  יש בלוק j שכל משתנה שמגדיל אותו מגדיל גם בלוק i                 -> לא רגולרית
 *   NC1 עמודה בשלושה בלוקים ומעלה שאינה צירוף אי-שלילי של עמודות בשני בלוקים -> לא חסרת הקשר
 *   NC2 שני משתנים ב-U עם זוגות מצטלבים, ואף משתנה אחר לא נוגע בבלוקים    -> לא חסרת הקשר
 * טענות שליליות (NR, NC) רק כשאפשר לפרק כל מילה לבלוקים בדרך אחת (האותיות של הבלוקים זרות זו לזו).
 */
(function (root, f) {
  const node = typeof module === 'object' && module.exports;
  const L = node ? require('./ns.js') : root.AutomataLang;
  f(L);
  if (node) module.exports = L;
})(typeof globalThis !== 'undefined' ? globalThis : this, function (L) {
  'use strict';
  const T_ = { op: 'true' }, F_ = { op: 'false' };
  const R = (cls, why, extra) => Object.assign({ cls, why, engine: 'vec' }, extra);
  const WHY = {
    empty: 'אף הצבה לא מקיימת את התנאים, אז השפה ריקה',
    r1: 'אחרי פתיחת הקשרים, כל משתנה שגדל בלי גבול שולט בחלק אחד בלבד, והתנאים תלויים רק בערך של כל משתנה (עד סף, או שארית). לכן מספיק זיכרון סופי',
    r3: 'יש משתנה שמופיע בכמה חלקים, אבל בכל אחד מהם יש משתנה חופשי שממלא אותו, ולכן החלקים לא באמת קשורים. מספיק זיכרון סופי',
    nr: 'יש חלק שתלוי בחלק אחר (כל מה שמגדיל אותו מגדיל גם את האחר), ולכן צריך לזכור מספר לא חסום. הקשרים מקוננים או עוקבים, ומחסנית אחת מספיקה',
    cfany: 'הקשרים בין החלקים מקוננים או עוקבים, ולכן מחסנית אחת מספיקה. לא הוכחתי שהשפה לא רגולרית',
    three: 'משתנה אחד קושר שלושה חלקים או יותר. מחסנית אחת יכולה להשוות רק שניים',
    cross: 'הקשרים בין החלקים מצטלבים, ומחסנית עובדת רק על קשרים מקוננים או עוקבים',
    nl: 'בחלק אחד מספר החזרות גדל כמו n² (או 2ⁿ), והמרווחים בין האורכים האפשריים גדלים בלי סוף. שפה כזו לא חסרת הקשר',
  };

  /* ---------- צורות ליניאריות { k, co } ---------- */
  const form = (k, co) => ({ k, co: co || {} });
  function addF(a, b, s) {
    const co = Object.assign({}, a.co);
    for (const v in b.co) { co[v] = (co[v] || 0) + s * b.co[v]; if (!co[v]) delete co[v]; }
    return form(a.k + s * b.k, co);
  }
  function substF(f, y, e) { if (!f.co[y]) return f; const c = f.co[y], g = form(f.k, Object.assign({}, f.co)); delete g.co[y]; return addF(g, e, c); }
  function treeOf(f) {
    let t = null;
    for (const v of Object.keys(f.co)) {
      const c = Math.abs(f.co[v]), x = c === 1 ? { t: 'var', n: v } : { t: 'mul', k: c, a: { t: 'var', n: v } };
      t = !t ? (f.co[v] > 0 ? x : { t: 'sub', a: { t: 'num', v: 0 }, b: x }) : { t: f.co[v] > 0 ? 'add' : 'sub', a: t, b: x };
    }
    if (!t) return { t: 'num', v: f.k };
    return f.k > 0 ? { t: 'add', a: t, b: { t: 'num', v: f.k } } : f.k < 0 ? { t: 'sub', a: t, b: { t: 'num', v: -f.k } } : t;
  }
  const substA = (a, y, t) => (a.t === 'var' ? (a.n === y ? t : a) : a.t === 'num' ? a : Object.assign({}, a, a.b ? { a: substA(a.a, y, t), b: substA(a.b, y, t) } : { a: substA(a.a, y, t) }));
  function substC(c, y, t) {
    if (c.op === 'and' || c.op === 'or') return { op: c.op, args: c.args.map((x) => substC(x, y, t)) };
    if (c.op === 'cmp') return { op: 'cmp', rel: c.rel, l: substA(c.l, y, t), r: substA(c.r, y, t) };
    return c;
  }
  const andC = (c, a) => (c.op === 'true' ? a : c.op === 'and' ? { op: 'and', args: c.args.concat([a]) } : { op: 'and', args: [c, a] });
  function removeAtom(c, a) {
    if (c === a) return T_;
    if (c.op === 'and') return { op: 'and', args: c.args.map((x) => removeAtom(x, a)) };
    return c;
  }

  /** מוריד השוואות שתמיד נכונות (או תמיד לא) כשכל המשתנים ≥ 0. */
  function simplify(c) {
    if (c.op === 'and' || c.op === 'or') {
      const and = c.op === 'and', args = c.args.map(simplify);
      if (args.some((x) => x.op === (and ? 'false' : 'true'))) return and ? F_ : T_;
      const rest = args.filter((x) => x.op !== (and ? 'true' : 'false'));
      return !rest.length ? (and ? T_ : F_) : rest.length === 1 ? rest[0] : { op: c.op, args: rest };
    }
    if (c.op !== 'cmp') return c;
    const inf = L.info(c);
    if (!inf.vAll.size) return L.evalC(c, {}, '') ? T_ : F_;
    const lin = inf.lin;
    if (!lin) return c;
    const vs = Object.keys(lin.co), k = lin.k, rel = c.rel;
    const pos = vs.every((v) => lin.co[v] > 0), neg = vs.every((v) => lin.co[v] < 0);
    // Σ c·v + k REL 0, וכל c > 0: הצד השמאלי ≥ k
    if (pos && ((rel === '>=' && k >= 0) || ((rel === '>' || rel === '!=') && k > 0))) return T_;
    if (pos && ((rel === '<' && k >= 0) || ((rel === '<=' || rel === '=') && k > 0))) return F_;
    if (neg && ((rel === '<=' && k <= 0) || ((rel === '<' || rel === '!=') && k < 0))) return T_;
    if (neg && ((rel === '>' && k <= 0) || ((rel === '>=' || rel === '=') && k < 0))) return F_;
    return c;
  }

  /** אם ההשוואה (Σ co·v + k REL 0) קובעת משתנה y כביטוי עם מקדמים אי-שליליים: { y, e }. */
  function solve(rel, lin, fresh) {
    const vs = Object.keys(lin.co);
    const only = (y, sign) => vs.every((v) => v === y || Math.sign(lin.co[v]) !== sign);
    const abs = (y) => { const co = {}; for (const v of vs) if (v !== y) co[v] = Math.abs(lin.co[v]); return co; };
    if (rel === '=') {
      for (const y of vs) {
        if (lin.co[y] === 1 && only(y, 1)) return { y, e: form(-lin.k, abs(y)) };
        if (lin.co[y] === -1 && only(y, -1)) return { y, e: form(lin.k, abs(y)) };
      }
      return null;
    }
    const sgn = rel === '>=' || rel === '>' ? 1 : rel === '<=' || rel === '<' ? -1 : 0;
    if (!sgn) return null;
    const k = sgn * lin.k - (rel === '>' || rel === '<' ? 1 : 0);     // Σ (sgn·co)·v + k ≥ 0
    for (const y of vs) {
      if (sgn * lin.co[y] !== 1 || !vs.every((v) => v === y || sgn * lin.co[v] < 0)) continue;
      const s = fresh(), co = abs(y);
      co[s] = 1;
      return { y, e: form(-k, co) };
    }
    return null;
  }

  function formOf(e) {
    if (e.gen) return e.lin ? form(e.lin.k, Object.assign({}, e.lin.co)) : null;
    if (e.pow) return null;
    return e.v ? form(e.d, { [e.v]: e.c }) : form(e.d);
  }

  /** הצורה הנורמלית: { blocks: [{ word, f }], cond, vars }, או null אם לא הצלחתי להגיע אליה. */
  function normalize(ast) {
    let blocks = ast.blocks.map((b) => ({ word: b.word, f: formOf(b.e) }));
    if (blocks.some((b) => !b.f)) return null;
    let cond = simplify(L.nnf(ast.cond || T_)), fresh = 0;
    const mk = () => '~' + (++fresh);
    const sub = (y, e) => { blocks = blocks.map((b) => ({ word: b.word, f: substF(b.f, y, e) })); cond = substC(cond, y, treeOf(e)); };
    const nonneg = (e) => { if (e.k < 0) cond = andC(cond, { op: 'cmp', rel: '>=', l: treeOf(e), r: { t: 'num', v: 0 } }); };
    for (let round = 0; round < 24; round++) {
      cond = simplify(cond);
      if (cond.op === 'false') break;
      let step = null;
      for (const a of L.conjuncts(cond)) {
        const lin = a.op === 'cmp' && L.info(a).lin;
        const r = lin && Object.keys(lin.co).length >= 1 && solve(a.rel, lin, mk);
        if (r) { step = { a, r }; break; }
      }
      if (step) { cond = removeAtom(cond, step.a); sub(step.r.y, step.r.e); nonneg(step.r.e); continue; }
      const b = blocks.find((x) => Object.values(x.f.co).some((c) => c < 0));
      if (!b) break;
      const pos = Object.keys(b.f.co).filter((v) => b.f.co[v] > 0);
      if (pos.length !== 1 || b.f.co[pos[0]] !== 1) return null;
      const p = mk(), e = form(-b.f.k, { [p]: 1 });
      for (const v in b.f.co) if (b.f.co[v] < 0) e.co[v] = -b.f.co[v];
      sub(pos[0], e); nonneg(e);
    }
    if (blocks.some((b) => Object.values(b.f.co).some((c) => c < 0))) return null;
    for (const b of blocks) if (b.f.k < 0) cond = andC(cond, { op: 'cmp', rel: '>=', l: treeOf(b.f), r: { t: 'num', v: 0 } });
    cond = simplify(cond);
    // בלוקים סמוכים עם אותה מילה מתאחדים (uⁿuᵐ = uⁿ⁺ᵐ), ובלוקים שתמיד ריקים יורדים
    const merged = [];
    for (const b of blocks) {
      if (!b.f.k && !Object.keys(b.f.co).length) continue;
      const last = merged[merged.length - 1];
      if (last && last.word === b.word) last.f = addF(last.f, b.f, 1); else merged.push({ word: b.word, f: b.f });
    }
    // משתנים עם אותה עמודה בדיוק שלא מופיעים בתנאי: הסכום שלהם מקבל כל ערך, אז משתנה אחד מספיק (aⁿ⁺ᵐbⁿ⁺ᵐ = aᴺbᴺ)
    const inCond = new Set();
    for (const a of L.leaves(cond)) if (a.op === 'cmp') for (const v of L.info(a).vAll) inCond.add(v);
    const col = (v) => merged.map((b) => b.f.co[v] || 0).join(',');
    const keep = new Map();
    for (const b of merged) for (const v of Object.keys(b.f.co)) {
      if (inCond.has(v)) continue;
      const c = col(v);
      if (!keep.has(c)) keep.set(c, v);
      else if (keep.get(c) !== v) for (const x of merged) delete x.f.co[v];
    }
    const vars = new Set(inCond);
    for (const b of merged) for (const v in b.f.co) vars.add(v);
    return { blocks: merged, cond, vars: Array.from(vars) };
  }

  /** האם כל מילה מתפרקת לבלוקים בדרך אחת: לכל שני בלוקים אין אות משותפת. */
  const separable = (blocks) => blocks.every((b, i) => blocks.every((c, j) => i === j || !Array.from(b.word).some((x) => c.word.includes(x))));

  /** הצירופים של "מחלקות" המשתנים (ערך מדויק עד סף, אחריו שארית) שמקיימים את התנאי. */
  function classCombos(cond, vars, T, P) {
    const out = [], env = {};
    let budget = 60000;
    const rec = (i) => {
      if (--budget < 0) throw new Error('too many');
      if (i === vars.length) { if (L.evalC(cond, env, '')) out.push(Object.assign({}, env)); return; }
      const v = vars[i];
      for (let j = 0; j <= T[v] + P[v]; j++) { env[v] = j; rec(i + 1); }
      delete env[v];
    };
    rec(0);
    return out;
  }

  function analyzeNF(nf) {
    const { blocks, cond, vars } = nf;
    if (cond.op === 'false') return R('regular', WHY.empty, { nf, combos: [], T: {}, P: {}, U: [], supp: {}, mode: 'R1' });
    const leaves = L.leaves(cond);
    if (leaves.some((a) => a.op !== 'cmp' && a.op !== 'true') || leaves.some((a) => a.op === 'cmp' && L.info(a).cat !== 'safe')) return null;
    const { T, P } = L.classParams(cond, vars);
    const combos = classCombos(cond, vars, T, P);
    const supp = {};
    for (const v of vars) supp[v] = blocks.map((b, i) => (b.f.co[v] > 0 ? i : -1)).filter((i) => i >= 0);
    if (!combos.length) return R('regular', WHY.empty, { nf, combos, T, P, U: [], supp, mode: 'R1' });
    const U = vars.filter((v) => combos.some((c) => c[v] > T[v]));
    const base = { nf, combos, T, P, U, supp };
    if (U.every((v) => supp[v].length <= 1)) return R('regular', WHY.r1, Object.assign(base, { mode: 'R1' }));

    const inCond = new Set(leaves.filter((a) => a.op === 'cmp').flatMap((a) => Array.from(L.info(a).vAll)));
    const multi = U.filter((v) => supp[v].length >= 2);
    const filler = (i) => U.find((f) => supp[f].length === 1 && supp[f][0] === i && !inCond.has(f));
    if (multi.every((v) => supp[v].every((i) => filler(i)))) {
      const exact = {};
      const lcm = (a, b) => L.lcm(a, b);
      for (const v of multi) exact[v] = T[v] + supp[v].map((i) => blocks[i].f.co[filler(i)]).reduce(lcm, 1) * P[v];
      return R('regular', WHY.r3, Object.assign(base, { mode: 'R3', exact }));
    }

    const sep = separable(blocks);
    const cross = (A, B) => A.length === 2 && B.length === 2 && ((A[0] < B[0] && B[0] < A[1] && A[1] < B[1]) || (B[0] < A[0] && A[0] < B[1] && B[1] < A[1]));
    const anyCross = U.some((v) => U.some((u) => cross(supp[v], supp[u])));
    if (U.every((v) => supp[v].length <= 2) && !anyCross) {
      // NR: בלוק j שכל משתנה ב-U שמגדיל אותו מגדיל גם את i, ו-j לא חסום
      const co = (i, v) => blocks[i].f.co[v] || 0;
      let nr = false;
      for (let i = 0; i < blocks.length && sep && !nr; i++) for (let j = 0; j < blocks.length && !nr; j++) {
        if (i !== j && U.some((v) => co(j, v) > 0) && U.every((v) => !(co(j, v) > 0) || co(i, v) > 0)) nr = true;
      }
      return R(nr ? 'cf' : 'cfany', nr ? WHY.nr : WHY.cfany, Object.assign(base, { mode: 'C1' }));
    }
    if (sep) {
      // NC1: עמודה בשלושה בלוקים ומעלה שאי אפשר לכתוב כצירוף אי-שלילי של עמודות שנוגעות בשני בלוקים לכל היותר
      const col = (v) => blocks.map((b) => b.f.co[v] || 0);
      const small = U.filter((u) => supp[u].length <= 2).map(col);
      for (const v of U) if (supp[v].length >= 3 && inCone(col(v), small) === false) return R('noncf', WHY.three, base);
      for (const v of U) for (const u of U) {
        if (!cross(supp[v], supp[u])) continue;
        const four = supp[v].concat(supp[u]);
        if (U.every((w) => w === v || w === u || !supp[w].some((i) => four.includes(i)))) return R('noncf', WHY.cross, base);
      }
    }
    return null;
  }

  /**
   * האם c הוא צירוף אי-שלילי של הווקטורים ws (כולם אי-שליליים)? אלימינציה של Fourier-Motzkin במספרים שלמים, בלי שגיאות עיגול.
   * מחזיר true / false, או null אם החישוב גדל מדי (ואז לא טוענים כלום).
   */
  function inCone(c, ws) {
    const use = ws.filter((w) => w.every((x, i) => !x || c[i] > 0));      // וקטור עם רכיב מחוץ לתמיכה של c לא יכול לעזור
    const n = use.length, rows = [];
    c.forEach((ci, i) => { if (!ci && use.every((w) => !w[i])) return; const a = use.map((w) => w[i]); rows.push([a, ci], [a.map((x) => -x), -ci]); });
    for (let j = 0; j < n; j++) { const a = use.map((_, t) => (t === j ? -1 : 0)); rows.push([a, 0]); }   // μ ≥ 0
    let R = rows;                                                          // כל שורה: a·μ ≤ b
    const g = (x, y) => { x = Math.abs(x); y = Math.abs(y); while (y) [x, y] = [y, x % y]; return x; };
    for (let j = 0; j < n; j++) {
      const pos = R.filter((r) => r[0][j] > 0), neg = R.filter((r) => r[0][j] < 0), next = R.filter((r) => r[0][j] === 0);
      for (const p of pos) for (const q of neg) {
        const a = p[0].map((x, t) => x * -q[0][j] + q[0][t] * p[0][j]), b = p[1] * -q[0][j] + q[1] * p[0][j];
        const d = a.concat([b]).reduce(g, 0) || 1;
        next.push([a.map((x) => x / d), b / d]);
      }
      if (next.length > 3000) return null;
      R = next;
    }
    return R.every((r) => r[1] >= 0);
  }

  /** ניתוח של שפת בלוקים עם חזקות ליניאריות (כולל כמה משתנים). מחזיר null אם אין טענה מוכחת. */
  function analyzeVec(ast) {
    try {
      const nf = normalize(ast);
      return nf ? analyzeNF(nf) : null;
    } catch (e) { return null; }
  }

  /* ---------- חזקות לא ליניאריות ---------- */
  /** אם מספר החזרות של הבלוק תלוי במשתנה אחד והמרווחים בין הערכים גדלים בלי סוף (n², 2ⁿ): המשתנה. */
  function growth(e) {
    if (e.pow) return e.v;
    if (!e.gen || e.lin || e.vars.length !== 1) return null;
    const v = e.vars[0];
    let vals = [];
    for (let x = 0; x < 14; x++) vals.push(L.evalA(e.t, { [v]: x }));
    let deg = 0;
    while (deg < 12 && !vals.every((x) => x === vals[0])) { vals = vals.slice(1).map((x, i) => x - vals[i]); deg++; }
    return deg >= 2 && deg < 12 && vals[0] > 0 ? v : null;     // פולינום ממעלה 2 ומעלה עם מקדם מוביל חיובי
  }
  const nonneg = (a) => a.t !== 'sub' && (a.t !== 'num' || a.v >= 0) && (!a.a || nonneg(a.a)) && (!a.b || nonneg(a.b));

  function analyzeNonlinear(ast) {
    if (!separable(ast.blocks)) return null;
    // כל הצבה נותנת מילה (אין מינוס בחזקות), והתנאים הם על משתנה אחד בכל פעם
    if (ast.blocks.some((b) => (b.e.gen ? !nonneg(b.e.t) : (b.e.d || 0) < 0))) return null;
    const cond = L.nnf(ast.cond || T_);
    const top = L.conjuncts(cond).filter((a) => a.op !== 'true');
    if (top.some((a) => a.op !== 'cmp' || L.info(a).vAll.size > 1)) return null;
    const vars = new Set(ast.vars);
    for (const b of ast.blocks) for (const v of b.e.gen ? b.e.vars : b.e.v ? [b.e.v] : []) vars.add(v);
    const free = {};
    for (const v of vars) {
      const c = { op: 'and', args: top.filter((a) => L.info(a).vAll.has(v)).concat([T_]) };
      const { T, P } = L.classParams(c, [v]);
      let some = false, big = false;
      for (let j = 0; j <= T[v] + P[v]; j++) if (L.evalC(c, { [v]: j }, '')) { some = true; if (j > T[v]) big = true; }
      if (!some) return null;
      free[v] = big;
    }
    for (const b of ast.blocks) { const v = growth(b.e); if (v && free[v]) return R('noncf', WHY.nl, { mode: 'NL' }); }
    return null;
  }

  Object.assign(L, { inCone, normalize, analyzeVec, analyzeNonlinear, separable, VEC_WHY: WHY, linForm: { form, addF, substF, treeOf } });
});
