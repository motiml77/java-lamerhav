'use strict';
/*
 * הבדיקה מול הנוסחה, לעומק (lang/check.js + lang/pdax.js): מה מוכח, מה נבדק, ומה אסור שיקרה.
 * כל בדיקה כאן היא מקרה שבו בדיקה שטחית (רק המילים הקצרות, או הרצה עם מגבלת צעדים) נותנת תשובה שגויה.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const L = require('../js/lang/index.js');
const M = require('../js/model.js');
const S = require('../js/sim.js');

const P = (s) => L.parse(s);
function lcg(seed) { return () => { seed = (seed * 1103515245 + 12345) & 0x7fffffff; return seed / 0x7fffffff; }; }
/** מכונה מתיאור קצר: states [[שם, מקבל?]], edges [[מ, אל, תוויות]] */
function machine(type, spec, opts) {
  const m = M.createMachine(type, Object.assign({ alphabet: ['a', 'b'] }, type === 'pda' ? { stackAlphabet: ['Z', 'A', 'B'] } : {}, opts));
  const ids = {};
  for (const [name, acc] of spec.states) ids[name] = M.addState(m, { name, accepting: !!acc }).id;
  for (const [f, t, lab] of spec.edges) { const r = M.parseLabels(type, lab); assert.deepEqual(r.errors, [], lab); M.addTransition(m, ids[f], ids[t], r.labels); }
  return m;
}
const ANBN = { states: [['q0'], ['q1'], ['q2', 1]], edges: [['q0', 'q0', 'a,Z/AZ | a,A/AA'], ['q0', 'q1', 'b,A/ε'], ['q1', 'q1', 'b,A/ε'], ['q1', 'q2', 'ε,Z/Z'], ['q0', 'q2', 'ε,Z/Z']] };
/** אותו אוטומט, ועוד מסלול נסתר של מצבים שמקבל מילה אחת נוספת */
function withHiddenPath(base, word) {
  const states = base.states.slice(), edges = base.edges.slice();
  for (let i = 1; i <= word.length; i++) states.push(['x' + i, i === word.length]);
  Array.from(word).forEach((c, i) => edges.push([i ? 'x' + i : 'q0', 'x' + (i + 1), c + ',Z/Z']));
  return { states, edges };
}
/** אס"ד שסופר עד K (במצבים) במקום במחסנית: מקבל aⁿbⁿ רק עד n = K */
function dfaCount(K) {
  const st = [], ed = [];
  for (let i = 0; i <= K; i++) st.push(['a' + i, i === 0]);
  for (let i = 0; i < K; i++) st.push(['b' + i, i === 0]);
  for (let i = 0; i < K; i++) ed.push(['a' + i, 'a' + (i + 1), 'a']);
  for (let i = 1; i <= K; i++) ed.push(['a' + i, 'b' + (i - 1), 'b']);
  for (let i = 1; i < K; i++) ed.push(['b' + i, 'b' + (i - 1), 'b']);
  return machine('dfa', { states: st, edges: ed });
}

/* ---------- הכרעה מדויקת לאוטומט מחסנית ---------- */
test('pdaAccepts: מסכים עם ההרצה הרגילה בכל מקום שהיא מכריעה, ומכריע גם כשהיא נעצרת במגבלה', () => {
  const rnd = lcg(11), pick = (a) => a[Math.floor(rnd() * a.length)];
  const words = Array.from(L.words(['a', 'b'], 5));
  let limits = 0, decided = 0;
  for (let t = 0; t < 120; t++) {
    const m = M.createMachine('pda', { alphabet: ['a', 'b'], stackAlphabet: ['Z', 'A', 'B'], stackStart: pick(['Z', 'Z', '', 'AZ']), acceptBy: pick(['final', 'final', 'empty']) });
    const ids = [];
    for (let i = 0, n = 1 + Math.floor(rnd() * 4); i < n; i++) ids.push(M.addState(m, { accepting: rnd() < 0.4 }).id);
    for (let i = 0, k = 2 + Math.floor(rnd() * 8); i < k; i++) M.addTransition(m, pick(ids), pick(ids), [{ read: pick(['a', 'b', 'a', 'b', '']), pop: pick(['', 'Z', 'A', 'B', 'A']), push: pick(['', '', 'A', 'B', 'Z', 'AA', 'AZ', 'AB', 'AAZ']) }]);
    const C = L.pdaCompile(m), st = L.pdaStepper(C), after = new Map([['', st.start()]]);
    for (const w of words) {
      const exact = L.pdaAccepts(C, w), sim = S.run(m, w, { record: false }).result;
      if (sim === 'limit') limits++; else { decided++; assert.equal(exact, sim === 'accept', 'מילה ' + JSON.stringify(w) + ' במכונה ' + t); }
      const c = w === '' ? after.get('') : st.step(after.get(w.slice(0, -1)), w[w.length - 1]);
      after.set(w, c);
      if (c !== L.PDA_INEXACT) assert.equal(st.accepts(c), exact, 'ההרצה המהירה, מילה ' + JSON.stringify(w) + ' במכונה ' + t);
    }
  }
  assert.ok(decided > 5000 && limits > 100, 'הבדיקה כוללת גם מכונות שההרצה הרגילה לא מכריעה בהן (' + limits + ')');
});

test('לולאת ε שדוחפת: ההרצה הרגילה לא מכריעה, והבדיקה כן (ונכונה)', () => {
  // מנחש כמה לדחוף: מקבל aⁿbᵐ עם n ≤ m. נכון, אבל כל הרצה רגילה שלו נתקעת במגבלה
  const guess = machine('pda', { states: [['q0'], ['q1'], ['q2', 1]], edges: [['q0', 'q0', 'a,ε/A | ε,ε/A'], ['q0', 'q1', 'ε,ε/ε'], ['q1', 'q1', 'b,A/ε'], ['q1', 'q2', 'ε,Z/Z']] });
  assert.equal(S.run(guess, 'ab', { record: false }).result === 'limit' || S.run(guess, 'ba', { record: false }).result === 'limit', true, 'ההרצה הרגילה נעצרת במגבלה');
  let r = L.check(guess, P('a^n b^m | n <= m'));
  assert.deepEqual([r.ok, r.undecided], [true, 0]);
  // ואותו אוטומט מול שפה אחרת: הטעות נמצאת, ולא מוסתרת מאחורי "לא הוכרע"
  r = L.check(guess, P('a^n b^n'));
  assert.equal(r.ok, false);
  assert.deepEqual([r.extra[0], r.undecided], ['b', 0]);
});

/* ---------- אס"ד / אסל"ד מול שפה לא רגולרית ---------- */
test('אס"ד מול שפה לא רגולרית: אף פעם לא "נכון", ומוצאים מילה נגדית גם כשהיא ארוכה', () => {
  for (const K of [3, 7, 9, 14, 20]) {
    const m = dfaCount(K), ast = P('a^n b^n'), r = L.check(m, ast);
    assert.deepEqual([r.ok, r.exact, r.impossible], [false, false, true], 'סופר עד ' + K);
    const w = r.missing[0];
    assert.ok(w != null && L.accepts(ast, w) && S.run(m, w).result !== 'accept', 'סופר עד ' + K + ': מילה נגדית אמיתית (' + w + ')');
    assert.equal(w, 'a'.repeat(K + 1) + 'b'.repeat(K + 1), 'הקצרה ביותר');
    assert.match(L.hint(r, m), /השפה הזו לא רגולרית/);
  }
});

test('אס"ד מול שפה לא רגולרית, כשלא נמצאה מילה נגדית: עדיין "לא נכון", עם ההסבר', () => {
  const m = dfaCount(45), r = L.check(m, P('a^n b^n'));          // הטעות הראשונה באורך 92, מעבר למה שנבדק
  assert.deepEqual([r.ok, r.impossible, r.missing, r.extra], [false, true, [], []]);
  assert.equal(L.hint(r, m), L.IMPOSSIBLE_HINT);
  const T = require('../js/task.js');
  m.language = { text: 'a^n b^n', mode: 'practice' };
  T.setCheck(m, r);
  assert.equal(T.pill(m).text, 'לא נכון: השפה לא רגולרית');
});

/* ---------- אוטומט מחסנית: טעות שמתגלה רק במילה ארוכה ---------- */
test('אוטומט מחסנית נכון עובר, עם פירוט של מה שנבדק ומה שהוכח', () => {
  const r = L.check(machine('pda', ANBN), P('a^n b^n'));
  assert.deepEqual([r.ok, r.exact, r.undecided, r.proven], [true, false, 0, 'shape']);
  assert.ok(r.maxLen >= 15 && r.count >= 65535, 'כל המילים עד אורך ' + r.maxLen);
  assert.ok(r.long.count > 1000 && r.long.maxLen >= 40, r.long.count + ' מילים ארוכות, עד אורך ' + r.long.maxLen);
});

test('מסלול נסתר שמקבל מילה אחת נוספת: נתפס בכל אורך', () => {
  const ast = P('a^n b^n');
  const cases = [['aaaaaabbbbbbb', 'מילה באורך 13: בין המילים הקצרות'], ['a'.repeat(12) + 'b'.repeat(13), 'אורך 25, מהצורה a…b…: בין צירופי החזקות'], ['ab'.repeat(30), 'אורך 60, לא מהצורה של הנוסחה: הוכחה (מכפלה עם המעטפת)']];
  for (const [w, why] of cases) {
    const r = L.check(machine('pda', withHiddenPath(ANBN, w)), ast);
    assert.equal(r.ok, false, why);
    assert.deepEqual(r.extra, [w], why);
  }
});

test('אוטומט מחסנית שסופר במצבים (עד עומק K) במקום במחסנית: נתפס במילים שנבנות בניפוח', () => {
  const ast = P('w ∈ {a,b}* | #a(w) = #b(w)');
  for (const K of [3, 10, 20]) {
    const st = [], ed = [];
    for (let d = -K; d <= K; d++) st.push(['d' + d, d === 0]);
    for (let d = -K; d <= K; d++) { if (d < K) ed.push(['d' + d, 'd' + (d + 1), 'a,ε/ε']); if (d > -K) ed.push(['d' + d, 'd' + (d - 1), 'b,ε/ε']); }
    const m = machine('pda', { states: [['d0', 1]].concat(st.filter((s) => s[0] !== 'd0')), edges: ed });
    const r = L.check(m, ast);
    assert.equal(r.ok, false, 'עומק ' + K);
    assert.ok(r.missing.length && r.missing.every((w) => L.accepts(ast, w) && !L.pdaAccepts(L.pdaCompile(m), w)), 'עומק ' + K + ': ' + r.missing[0]);
  }
});

test('אוטומט מחסנית מול שפה רגולרית: מוכח שאין מילים מיותרות, ומילה מיותרת ארוכה נמצאת בוודאות', () => {
  const ast = P('w ∈ {a,b}* | #a(w) זוגי');
  const even = { states: [['e', 1], ['o']], edges: [['e', 'o', 'a,ε/ε'], ['o', 'e', 'a,ε/ε'], ['e', 'e', 'b,ε/ε'], ['o', 'o', 'b,ε/ε']] };
  let r = L.check(machine('pda', even), ast);
  assert.deepEqual([r.ok, r.exact, r.proven], [true, false, 'noExtra']);
  // מקבל גם את a⁴¹ (מספר אי-זוגי), דרך מונה במחסנית: הרבה מעבר לאורך שנבדק מילה אחרי מילה
  const st = even.states.concat([['p'], ['f', 1]]), ed = even.edges.concat([['e', 'p', 'ε,Z/' + 'A'.repeat(41) + 'Z'], ['p', 'p', 'a,A/ε'], ['p', 'f', 'ε,Z/Z']]);
  r = L.check(machine('pda', { states: st, edges: ed }), ast);
  assert.equal(r.ok, false);
  assert.deepEqual(r.extra, ['a'.repeat(41)]);
});

/* ---------- ההוכחה לא נשענת על בנייה שלא נבדקה ---------- */
test('אס"ד שגוי של השפה לא משמש להוכחה: נופלים לבדיקה מול בדיקת השייכות, והתשובה נשארת נכונה', () => {
  const ast = P('w ∈ {a,b}* | #a(w) זוגי');
  // "באג" מדומה בבנייה: אס"ד שמקבל הכול
  Object.defineProperty(ast, '_dfa', { value: { alphabet: ['a', 'b'], n: 1, start: 0, acc: [true], tr: [[0, 0]] }, enumerable: false });
  L.analyze(ast);
  assert.equal(L.trustedDFA(ast), null);
  const all = machine('dfa', { states: [['q', 1]], edges: [['q', 'q', 'a, b']] });
  assert.equal(L.canExact(all, ast), false);
  const r = L.check(all, ast);
  assert.deepEqual([r.exact, r.ok, r.extra[0]], [false, false, 'a']);
});

test('האס"ד של השפה (הבסיס של ההוכחה) שווה לבדיקת השייכות: נוסחאות אקראיות, כל המילים הקצרות וצירופי החזקות', () => {
  const rnd = lcg(2024), pick = (a) => a[Math.floor(rnd() * a.length)];
  const blocks = () => {
    const vars = [], parts = [];
    for (let i = 0, n = 1 + Math.floor(rnd() * 3); i < n; i++) {
      const v = ['n', 'm', 'k'][vars.length], k = rnd();
      if (k < 0.55) { parts.push('abc'[i] + '^' + pick(['', '2']) + v); vars.push(v); } else if (k < 0.7) { parts.push('abc'[i] + '^(' + v + '+1)'); vars.push(v); } else parts.push('abc'[i] + pick(['*', '+', '^2']));
    }
    const atoms = [];
    for (let i = 0, n = Math.floor(rnd() * 3); i < n && vars.length; i++) {
      const x = pick(vars), y = pick(vars), r = rnd();
      atoms.push(r < 0.3 ? x + ' ' + pick(['>=', '<=', '=', '!=', '<']) + ' ' + pick([0, 1, 2, 3]) : r < 0.6 ? x + ' mod ' + pick([2, 3]) + ' = ' + pick([0, 1]) : r < 0.8 && x !== y ? '(' + x + '+' + y + ') mod 2 = ' + pick([0, 1]) : x + ' זוגי');
    }
    return parts.join(' ') + (atoms.length ? ' | ' + atoms.join(rnd() < 0.7 ? ', ' : ' או ') : '');
  };
  const prop = () => {
    const at = () => pick(['#a(w) זוגי', '#b(w) mod 3 = 1', '|w| mod 2 = 0', '|w| >= 2', 'מכילה ab', 'לא מכילה aa', 'מתחילה ב-a', 'מסתיימת ב-ba', '#a(w) <= 2', '#a(w) + #b(w) mod 3 = 0', 'לא מסתיימת ב-b']);
    return 'w ∈ {a,b}* | ' + at() + (rnd() < 0.6 ? pick([' וגם ', ' או ']) + at() : '');
  };
  let regular = 0;
  for (let t = 0; t < 160; t++) {
    const text = rnd() < 0.5 ? blocks() : prop();
    const ast = P(text);
    if (L.analyze(ast).cls !== 'regular') continue;
    regular++;
    const d = L.toDFA(ast);
    for (const w of L.words(ast.alphabet, ast.alphabet.length <= 2 ? 9 : 6)) assert.equal(L.dfaAccepts(d, w), L.accepts(ast, w), text + ' / ' + JSON.stringify(w));
    for (const w of L.shapeWords(ast, 24, 500)) assert.equal(L.dfaAccepts(d, w), L.accepts(ast, w), text + ' / ' + w);
    assert.ok(L.trustedDFA(ast), text);
  }
  assert.ok(regular > 80, regular + ' נוסחאות רגולריות');
});

/* ---------- המילים הארוכות ---------- */
test('צירופי החזקות: כל הצירופים הקטנים, גם בשפה וגם מחוצה לה, ובכל חלק של איחוד', () => {
  const ws = L.shapeWords(P('a^n b^n'), 40, 500);
  for (const w of ['', 'a', 'b', 'ab', 'aab', 'abb', 'a'.repeat(15) + 'b'.repeat(15), 'a'.repeat(14) + 'b'.repeat(15)]) assert.ok(ws.includes(w), w);
  assert.ok(ws.every((w) => /^a*b*$/.test(w) && w.length <= 40));
  const u = L.shapeWords(P('a^n b^n ∪ b^n a^n'), 30, 400);
  assert.ok(u.includes('aaabbb') && u.includes('bbbaaa') && u.includes('bba'));
  assert.deepEqual(L.shapeWords(P('w ∈ {a,b}* | פלינדרום'), 30, 100), []);
});

test('הבדיקה בחלקים: hurry ו-stop נותנים תוצאה עקבית (מה שנספר הוא מה שנבדק)', () => {
  const c = L.checker(machine('pda', ANBN), P('a^n b^n'));
  for (let i = 0; i < 40; i++) c.step(100);
  c.hurry();
  while (!c.step(500)) { /* עוד */ }
  const r = c.result;
  assert.equal(r.ok, true);
  assert.equal(r.count, Math.pow(2, r.maxLen + 1) - 1, 'הספירה היא בדיוק כל המילים עד האורך שהושלם');
  assert.ok(r.long.count > 0 && r.long.count <= 2500);
  const c2 = L.checker(machine('pda', ANBN), P('a^n b^n'));
  for (let i = 0; i < 40; i++) c2.step(100);
  c2.stop();
  assert.deepEqual([c2.result.done, c2.result.ok, c2.result.count], [true, true, Math.pow(2, c2.result.maxLen + 1) - 1]);
});
