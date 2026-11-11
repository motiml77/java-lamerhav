/*
 * lang/classify.js - סיווג השפה: רגולרית / חסרת הקשר ולא רגולרית / לא חסרת הקשר / "לא הצלחתי לסווג".
 * הטענה מוצגת לתלמידים, ולכן טוענים רק מה שאפשר להצדיק (ההצדקות ב-docs/SPEC.md, פרק "שפה"). בכל ספק: unknown.
 * בנוסף: כלי ניתוח שמשמשים גם את הבנייה (nnf, info, classParams).
 */
(function (root, f) {
  const node = typeof module === 'object' && module.exports;
  const L = node ? require('./ns.js') : root.AutomataLang;
  f(L);
  if (node) module.exports = L;
})(typeof globalThis !== 'undefined' ? globalThis : this, function (L) {
  'use strict';
  const NEG = { '=': '!=', '!=': '=', '<': '>=', '>=': '<', '>': '<=', '<=': '>' };
  const FLIP = { '=': '=', '!=': '!=', '<': '>', '>': '<', '<=': '>=', '>=': '<=' };
  const gcd = (a, b) => { a = Math.abs(a); b = Math.abs(b); while (b) [a, b] = [b, a % b]; return a; };
  const lcm = (a, b) => (a && b ? (a / gcd(a, b)) * b : a || b);

  /** דוחף שלילות פנימה (NNF). שלילה של השוואה הופכת את היחס; שלילה של תכונת מחרוזת נשארת כעלה {op:'not'}. */
  function nnf(c, neg) {
    switch (c.op) {
      case 'true': return neg ? { op: 'false' } : c;
      case 'false': return neg ? { op: 'true' } : c;
      case 'and': case 'or': return { op: (c.op === 'and') !== !!neg ? 'and' : 'or', args: c.args.map((x) => nnf(x, neg)) };
      case 'not': return nnf(c.arg, !neg);
      case 'cmp': return neg ? { op: 'cmp', rel: NEG[c.rel], l: c.l, r: c.r } : c;
      default: return neg ? { op: 'not', arg: c } : c;
    }
  }
  const leaves = (c) => (c.op === 'and' || c.op === 'or' ? c.args.flatMap(leaves) : [c]);
  const conjuncts = (c) => (c.op === 'and' ? c.args.flatMap(conjuncts) : [c]);

  function mapA(a, fn) {
    if (a.t === 'var') return fn(a);
    if (a.t === 'num') return a;
    if (a.t === 'mul' || a.t === 'mod') return Object.assign({}, a, { a: mapA(a.a, fn) });
    return { t: a.t, a: mapA(a.a, fn), b: mapA(a.b, fn) };
  }
  function mapC(c, fn) {
    if (c.op === 'and' || c.op === 'or') return { op: c.op, args: c.args.map((x) => mapC(x, fn)) };
    if (c.op === 'not') return { op: 'not', arg: mapC(c.arg, fn) };
    if (c.op === 'cmp') return { op: 'cmp', rel: c.rel, l: mapA(c.l, fn), r: mapA(c.r, fn) };
    return c;
  }
  /** מציב ערכים למשתנים (env), ומשאיר את השאר. */
  const subst = (c, env) => mapC(c, (v) => (env[v.n] != null ? { t: 'num', v: env[v.n] } : v));
  /** |w| -> סכום הספירות של כל אותיות האלפבית. */
  const expandLen = (c, alphabet) => mapC(c, (v) => (v.n === '|w|' ? alphabet.map((s) => ({ t: 'var', n: '#' + s })).reduce((x, y) => ({ t: 'add', a: x, b: y })) : v));

  function walk(a, inMod, acc) {
    if (a.t === 'num') acc.consts += Math.abs(a.v);
    else if (a.t === 'var') { acc.vAll.add(a.n); if (!inMod) acc.vOut.add(a.n); }
    else if (a.t === 'mul') { acc.consts += Math.abs(a.k); walk(a.a, inMod, acc); }
    else if (a.t === 'mod') { acc.consts += a.m; acc.mods.push(a.m); walk(a.a, true, acc); }
    else { walk(a.a, inMod, acc); if (a.b) walk(a.b, inMod, acc); }
  }
  /**
   * ניתוח של השוואה אחת: אילו משתנים יש בה, ואיזה סוג היא:
   *   safe    - האמת שלה נקבעת מ"מחלקה" של כל משתנה (ערך מדויק עד סף, ואחריו שארית): משתנה אחד, mod, או סכום עם מקדמים באותו סימן
   *   link    - קשר ליניארי בין שני משתנים או יותר עם סימנים מנוגדים (n = m, n < 2m ...)
   *   complex - לא ידוע
   */
  function info(atom) {
    const acc = { vOut: new Set(), vAll: new Set(), mods: [], consts: 0 };
    walk(atom.l, false, acc); walk(atom.r, false, acc);
    const l = L.linear(atom.l), r = L.linear(atom.r);
    if (l && r) {
      const co = Object.assign({}, l.co);
      for (const n in r.co) { co[n] = (co[n] || 0) - r.co[n]; if (!co[n]) delete co[n]; }
      acc.lin = { k: l.k - r.k, co };
      acc.vOut = new Set(Object.keys(co));
    }
    if (acc.vOut.size <= 1) acc.cat = 'safe';
    else if (!acc.lin) acc.cat = 'complex';
    else { const s = Object.values(acc.lin.co).map(Math.sign); acc.cat = s.every((x) => x === s[0]) ? 'safe' : 'link'; }
    return acc;
  }

  /** לכל משתנה: סף T (מעליו רק השארית משנה) ומחזור P. מחלקה של ערך x: x אם x ≤ T, אחרת T+1+((x−T−1) mod P). */
  function classParams(cond, vars) {
    const T = {}, P = {};
    for (const v of vars) { T[v] = 0; P[v] = 1; }
    for (const a of leaves(cond)) {
      if (a.op !== 'cmp') continue;
      const inf = info(a);
      for (const v of inf.vOut) if (v in T) T[v] = Math.max(T[v], inf.consts + 1);
      for (const v of inf.vAll) if (v in P) for (const m of inf.mods) P[v] = lcm(P[v], m);
    }
    return { T, P };
  }

  const R = (cls, why, extra) => Object.assign({ cls, why }, extra);
  const WHY = {
    regex: 'כל ביטוי רגולרי מתאר שפה רגולרית',
    reg: 'כל תנאי תלוי בספירה אחת בלבד (עד סף, או שארית), ולכן מספיק זיכרון סופי',
    link: 'יש קשר בין שתי ספירות שאין להן גבול, ואוטומט סופי לא יכול לזכור מספר לא חסום. מחסנית אחת מספיקה',
    three: 'שלוש ספירות (או יותר) תלויות זו בזו. מחסנית אחת יכולה להשוות רק שתיים',
    cross: 'הקשרים בין הספירות מצטלבים, ומחסנית עובדת רק על קשרים מקוננים או עוקבים',
    unk: 'הנוסחה חוקית ואפשר לבדוק מולה, אבל אין לי הוכחה לאיזו משפחה היא שייכת',
    nonlin: 'תנאי לא ליניארי (כמו n·m < 5) אפשר לבדוק מולו, אבל לא לסווג או לבנות. נתמכים: השוואות של צירופים ליניאריים (n < m, 2n ≤ m + 1), שארית (n mod 3 = 0), והצירופים שלהם',
  };

  /* ---------- צורה 1: בלוקים ---------- */
  function upperBounds(cond) {
    const ub = {};
    for (const a of conjuncts(cond)) {
      if (a.op !== 'cmp') continue;
      const lin = info(a).lin;
      if (!lin || Object.keys(lin.co).length !== 1) continue;
      const v = Object.keys(lin.co)[0], c = lin.co[v], x = -lin.k / c;    // c·v + k REL 0  <=>  v REL' x
      const rel = c > 0 ? a.rel : FLIP[a.rel];
      let b = null;
      if (rel === '=') b = Math.floor(x); else if (rel === '<=') b = Math.floor(x); else if (rel === '<') b = Math.ceil(x) - 1;
      if (b != null) ub[v] = Math.max(0, Math.min(ub[v] == null ? Infinity : ub[v], b));
    }
    return ub;
  }
  function distinctBlocks(blocks) {
    const single = blocks.every((b) => b.word.length === 1) && blocks.every((b, i) => i === 0 || b.word !== blocks[i - 1].word);
    const sets = blocks.map((b) => new Set(b.word));
    const disjoint = sets.every((s, i) => sets.every((t, j) => i === j || ![...s].some((c) => t.has(c))));
    return single || disjoint;
  }

  function analyzeBlocks(ast) {
    const cond = nnf(ast.cond || { op: 'true' });
    const occ = {};
    ast.blocks.forEach((b, i) => { if (b.e.v) (occ[b.e.v] = occ[b.e.v] || []).push(i); });
    const vars = Object.keys(occ);
    const cmps = (c) => leaves(c).filter((a) => a.op === 'cmp');
    const ready = (c, env) => vars.every((v) => env[v] != null || occ[v].length < 2) && cmps(c).every((a) => info(a).cat === 'safe');
    if (ready(cond, {})) return R('regular', WHY.reg, { regular: [{ env: {}, cond }], cond });

    // משתנים חסומים שמפריעים (משותפים לכמה בלוקים, או בתוך קשר): מציבים את כל הערכים שלהם, וזה איחוד סופי
    const ub = upperBounds(cond);
    const hard = vars.filter((v) => ub[v] != null && (occ[v].length >= 2 || cmps(cond).some((a) => { const x = info(a); return x.cat !== 'safe' && x.vOut.has(v); })));
    if (hard.length) {
      if (hard.reduce((n, v) => n * (ub[v] + 1), 1) > 400) return R('unknown', WHY.unk);
      const list = [];
      const rec = (i, env) => {
        if (i === hard.length) { const c = subst(cond, env); list.push({ env: Object.assign({}, env), cond: c, ok: ready(c, env) }); return; }
        for (let x = 0; x <= ub[hard[i]]; x++) { env[hard[i]] = x; rec(i + 1, env); }
        delete env[hard[i]];
      };
      rec(0, {});
      return list.every((x) => x.ok) ? R('regular', 'המשתנים שמקשרים בין החלקים חסומים, אז יש מספר סופי של מקרים, וכל אחד מהם רגולרי', { regular: list, cond }) : R('unknown', WHY.unk);
    }

    // מכאן: טענות "לא רגולרית". רק לצירוף פשוט של תנאים (בלי "או"), ולבלוקים שאפשר להבדיל ביניהם
    const top = conjuncts(cond);
    if (top.some((a) => a.op !== 'cmp' && a.op !== 'true') || !distinctBlocks(ast.blocks)) return R('unknown', WHY.unk);
    const links = [];
    let multi = false, triple = null;
    for (const v of vars) { if (occ[v].length >= 3) triple = v; else if (occ[v].length === 2) links.push({ i: occ[v][0], j: occ[v][1], eq: true, shared: v, vars: [v] }); }
    for (const a of top) {
      if (a.op !== 'cmp') continue;
      const inf = info(a);
      if (inf.cat === 'complex') return R('unknown', WHY.unk);
      if (inf.cat !== 'link') continue;
      const names = Object.keys(inf.lin.co);
      if (names.length !== 2 || names.some((v) => occ[v].length !== 1)) { multi = true; continue; }
      const [x, y] = occ[names[0]][0] < occ[names[1]][0] ? names : [names[1], names[0]];
      const pos = inf.lin.co[x] > 0;           // a·x − b·y REL k, כש-x הוא הבלוק המוקדם
      const la = { atom: a, i: occ[x][0], j: occ[y][0], vars: [x, y], x, y, a: Math.abs(inf.lin.co[x]), b: Math.abs(inf.lin.co[y]), rel: pos ? a.rel : FLIP[a.rel], k: pos ? -inf.lin.k : inf.lin.k };
      la.eq = la.rel === '=';
      if (la.rel === '!=' && la.k % gcd(la.a, la.b) !== 0) return R('unknown', WHY.unk);   // התנאי תמיד נכון
      links.push(la);
    }
    // עד: הצבה שבה כל הבלוקים לא ריקים והמשתנים המקושרים מעבר לסף (ואז יש אינסוף פתרונות "באותו כיוון")
    const { T } = classParams(cond, vars);
    const linked = new Set(links.flatMap((l) => l.vars).concat(triple ? [triple] : []));
    const range = Math.max(4, Math.min(Math.max(...vars.map((v) => T[v])) * 3 + 12, Math.floor(Math.pow(300000, 1 / vars.length))));
    const env = {};
    const search = (i) => {
      if (i === vars.length) return L.evalC(cond, env, '') && ast.blocks.every((b) => (b.e.v ? b.e.c * env[b.e.v] + b.e.d : b.e.d) >= 1);
      for (let x = linked.has(vars[i]) ? T[vars[i]] + 1 : 0; x <= range; x++) { env[vars[i]] = x; if (search(i + 1)) return true; }
      return false;
    };
    if (!search(0) || multi) return R('unknown', WHY.unk);
    const count = {};
    for (const l of links) for (const v of l.vars) count[v] = (count[v] || 0) + 1;
    if (triple) return count[triple] ? R('unknown', WHY.unk) : R('noncf', WHY.three);
    const chained = Object.keys(count).filter((v) => count[v] >= 2);
    if (chained.length) {
      for (const v of chained) {
        const ls = links.filter((l) => l.vars.includes(v));
        const dir = (l) => (l.a !== 1 || l.b !== 1 ? null : l.rel === '<' || l.rel === '<=' ? 'lt' : l.rel === '>' || l.rel === '>=' ? 'gt' : null);
        const mono = ls.length === 2 && dir(ls[0]) && dir(ls[0]) === dir(ls[1]) && (ls[0].y === v) !== (ls[1].y === v);
        if (new Set(ls.flatMap((l) => l.vars)).size < 3 || (!ls.every((l) => l.eq) && !mono)) return R('unknown', WHY.unk);
      }
      return R('noncf', WHY.three);
    }
    for (const p of links) for (const q of links) if (p.i < q.i && q.i < p.j && p.j < q.j) return p.eq && q.eq ? R('noncf', WHY.cross) : R('unknown', WHY.unk);
    return R('cf', WHY.link, { links, cond });
  }

  /* ---------- צורה 2: תכונה של מילה ---------- */
  function analyzeProp(ast) {
    const cond = expandLen(nnf(ast.cond), ast.alphabet);
    const hard = (a) => a.op === 'pal' || a.op === 'bal';
    const isPal = (a) => hard(a) || (a.op === 'not' && hard(a.arg));
    const bad = leaves(cond).filter((a) => (a.op === 'cmp' && info(a).cat !== 'safe') || isPal(a));
    if (!bad.length) return R('regular', WHY.reg, { cond });
    if (cond === bad[0]) {
      if (cond.op === 'bal') return R('cf', L.EXTRA_WHY.bal);
      if (cond.op === 'not' && cond.arg.op === 'bal') return R('unknown', WHY.unk);
      if (isPal(cond)) return ast.alphabet.length >= 2 ? R('cf', 'צריך לזכור את כל החצי הראשון של המילה כדי להשוות אותו לחצי השני, וזה בדיוק מה שמחסנית עושה') : R('unknown', WHY.unk);
      const inf = info(cond);
      if (inf.cat !== 'link') return R('unknown', WHY.unk);
      const g = Object.values(inf.lin.co).reduce(gcd, 0);
      if ((cond.rel === '=' || cond.rel === '!=') && inf.lin.k % g !== 0) return R('unknown', WHY.unk);
      return R('cf', WHY.link);
    }
    // צירוף של שוויונות #x = #y: שניים בלתי תלויים ומעלה = לא חסרת הקשר
    const top = conjuncts(cond);
    const eqs = top.map((a) => { const inf = a.op === 'cmp' && a.rel === '=' ? info(a) : null; const n = inf && inf.lin && !inf.lin.k ? Object.keys(inf.lin.co) : []; return n.length === 2 && inf.lin.co[n[0]] + inf.lin.co[n[1]] === 0 && Math.abs(inf.lin.co[n[0]]) === 1 ? n : null; });
    if (eqs.every(Boolean)) {
      const parent = {};
      const find = (x) => (parent[x] === x ? x : (parent[x] = find(parent[x])));
      let rank = 0;
      for (const [x, y] of eqs) { for (const z of [x, y]) if (!(z in parent)) parent[z] = z; if (find(x) !== find(y)) { parent[find(x)] = find(y); rank++; } }
      if (rank >= 2) return R('noncf', WHY.three);
    }
    return R('unknown', WHY.unk);
  }

  /** בלוקים: חזקות כלליות (כמה משתנים, n², 2ⁿ) עוברות ל-vec.js. אחרת הניתוח הרגיל, ואם הוא לא הכריע, ניסיון נוסף ב-vec.js. */
  function analyzeAllBlocks(ast) {
    if (ast.gen || ast.blocks.some((b) => b.e.pow)) {
      if (ast.blocks.length === 1 && !ast.cond && ast.blocks[0].e.pow) return R('noncf', L.EXTRA_WHY.pow);
      if (ast.blocks.some((b) => b.e.pow || (b.e.gen && !b.e.lin))) return L.analyzeNonlinear(ast) || R('unknown', WHY.unk);
      return L.analyzeVec(ast) || R('unknown', WHY.unk);
    }
    let r = analyzeBlocks(ast);
    if (r.cls === 'unknown') r = L.analyzeVec(ast) || viaUnion(ast) || r;
    if (r.cls === 'unknown' && hasNonlinear(ast.cond)) r = R('unknown', WHY.nonlin);
    return r;
  }
  const hasNonlinearA = (a) => !!a && (a.t === 'prod' || a.t === 'pw' || hasNonlinearA(a.a) || hasNonlinearA(a.b));
  const hasNonlinear = (c) => !!c && (c.op === 'cmp' ? hasNonlinearA(c.l) || hasNonlinearA(c.r) : (c.args || []).some(hasNonlinear) || hasNonlinear(c.arg));
  /** "או" ברמה העליונה של התנאי: השפה היא איחוד של שפות עם אותם בלוקים, ואותו מסווגים (combine.js). */
  function viaUnion(ast) {
    const top = conjuncts(nnf(ast.cond || { op: 'true' }));
    const ors = top.filter((a) => a.op === 'or'), rest = top.filter((a) => a.op !== 'or');
    if (ors.length !== 1 || ors[0].args.length > 4) return null;
    const parts = ors[0].args.map((b) => Object.assign({}, ast, { cond: { op: 'and', args: rest.concat([b]) }, text: undefined }));
    const u = { kind: 'union', parts, alphabet: ast.alphabet };
    const r = L.combinedAnalyze(u);
    if (r.cls === 'unknown') return null;
    return Object.assign(r, { viaUnion: u, why: r.why + '. התנאי עם "או" הופך את השפה לאיחוד של שתי שפות' });
  }

  const LABEL = { regular: 'רגולרית', cf: 'חסרת הקשר, לא רגולרית', cfany: 'חסרת הקשר', noncf: 'לא חסרת הקשר', unknown: 'לא הצלחתי לסווג' };
  /** מחזיר { cls, label, why, ... }. נשמר על ה-AST. */
  function analyze(ast) {
    if (!ast._an) {
      const ex = ast.kind === 'pattern' ? L.analyzePattern(ast) || R('unknown', WHY.unk) : null;
      const r = ex ? R(ex.cls, ex.why) : L.isCombined(ast) ? L.combinedAnalyze(ast) : ast.kind === 'regex' ? R('regular', WHY.regex) : ast.kind === 'prop' ? analyzeProp(ast) : analyzeAllBlocks(ast);
      r.label = LABEL[r.cls];
      Object.defineProperty(ast, '_an', { value: r, enumerable: false });
      if (r.cls === 'regular') finiteness(ast, r);
    }
    return ast._an;
  }

  /** לשפה רגולרית: האם היא ריקה או סופית (לפי האס"ד המינימלי). נכתב בהסבר. */
  function finiteness(ast, r) {
    let d;
    try { d = L.toDFA(ast); } catch (e) { return; }
    const live = d.acc.map(Boolean);
    for (let ch = true; ch;) { ch = false; d.tr.forEach((row, s) => { if (!live[s] && row.some((t) => live[t])) { live[s] = true; ch = true; } }); }
    if (!live[d.start]) { r.empty = true; r.why = 'השפה ריקה (∅): אף מילה לא מקיימת את התנאים'; return; }
    const memo = new Map(), color = new Map();
    const count = (s) => {            // מספר המילים מ-s, או Infinity אם יש מעגל
      if (memo.has(s)) return memo.get(s);
      if (color.get(s) === 1) return Infinity;
      color.set(s, 1);
      let n = d.acc[s] ? 1 : 0;
      for (const t of d.tr[s]) if (live[t]) n += count(t);
      color.set(s, 2); memo.set(s, n);
      return n;
    };
    const n = count(d.start);
    if (n !== Infinity) { r.finite = n; r.why = 'השפה סופית: ' + (n === 1 ? 'מילה אחת' : n + ' מילים') + ', ולכן רגולרית'; }
  }

  Object.assign(L, { nnf, leaves, conjuncts, subst, expandLen, info, classParams, analyze, classify: analyze, gcd, lcm });
});
