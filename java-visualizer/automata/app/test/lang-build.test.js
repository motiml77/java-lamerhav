'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const L = require('../js/lang/index.js');
const S = require('../js/sim.js');
const M = require('../js/model.js');

const P = (s) => L.parse(s);
const cls = (s) => L.analyze(P(s)).cls;

test('בדיקת שייכות (oracle): בלוקים, תכונות, ביטויים', () => {
  const yes = (f, ws) => ws.forEach((w) => assert.equal(L.accepts(P(f), w), true, f + ' צריך לקבל "' + w + '"'));
  const no = (f, ws) => ws.forEach((w) => assert.equal(L.accepts(P(f), w), false, f + ' צריך לדחות "' + w + '"'));
  yes('a^n b^n', ['', 'ab', 'aaabbb']); no('a^n b^n', ['a', 'ba', 'aab', 'abab']);
  yes('a^n b^m | n >= 2, m = 2n', ['aabbbb', 'aaabbbbbb']); no('a^n b^m | n >= 2, m = 2n', ['', 'abb', 'aabbb']);
  yes('a^n b^m | n < m', ['b', 'abb']); no('a^n b^m | n < m', ['', 'ab', 'aab']);
  yes('(ab)^n c^2', ['cc', 'ababcc']); no('(ab)^n c^2', ['abc', 'abacc']);
  yes('a^(2n+1)', ['a', 'aaa']); no('a^(2n+1)', ['', 'aa']);
  yes('a* b+', ['b', 'aab']); no('a* b+', ['', 'a', 'ba']);
  yes('a^n b^n c^n', ['', 'abc', 'aabbcc']); no('a^n b^n c^n', ['aabbc', 'abcabc']);
  yes('w ∈ {a,b}* | #a(w) = #b(w)', ['', 'abba']); no('w ∈ {a,b}* | #a(w) = #b(w)', ['a', 'aab']);
  yes('מתחילה ב-a וגם מסתיימת ב-b', ['ab', 'aab']); no('מתחילה ב-a וגם מסתיימת ב-b', ['', 'a', 'ba']);
  yes('w ∈ {a,b}* | פלינדרום', ['', 'aba', 'abba']); no('w ∈ {a,b}* | פלינדרום', ['ab']);
  yes('(a|b)*abb', ['abb', 'babb']); no('(a|b)*abb', ['', 'ab', 'abba']);
  yes('(ab)+ ∪ ε', ['', 'ab', 'abab']); no('(ab)+ ∪ ε', ['a', 'aba']);
  no('a^n b^n', ['abc'], 'אות מחוץ לאלפבית');
});

test('סיווג: טבלה של מקרים ידועים', () => {
  const table = {
    regular: ['a^n', 'a^n b^m', 'a^n b^m | n >= 2', 'a^n b^m | n mod 2 = 0, m mod 3 = 1', 'a^n b^m | n + m >= 5', 'a^n b^m | (n + m) mod 2 = 0',
      'a^n b^m | n = m, n <= 3', 'a^n b^n | n < 4', 'a^n b^m | n > 2 או m > 2', '(ab)^n c*', 'a^(2n+1)', '(a|b)*abb',
      'w ∈ {a,b}* | #a(w) זוגי', 'w ∈ {a,b}* | |w| mod 3 = 0 וגם מסתיימת ב-ab', 'w ∈ {a,b}* | #a(w) + #b(w) > 3', 'w ∈ {a,b}* | #a(w) = |w|', 'לא מכילה aa או מתחילה ב-b'],
    cf: ['a^n b^n', 'a^n b^2n', 'a^n b^m | n = m', 'a^n b^m | n < m', 'a^n b^m | n != m', 'a^n b^m | m = n + 2', 'a^n b^m | 2n = 3m', 'a^n b^m | n >= m, n mod 2 = 0',
      'a^n b^m c^m d^n', 'a^n b^n c^m d^m', 'a^n b^m c^k | n = k', '(ab)^n c^n', 'w ∈ {a,b}* | #a(w) = #b(w)', 'w ∈ {a,b}* | #a(w) > #b(w)', 'w ∈ {a,b,c}* | #a(w) + #c(w) = #b(w)', 'w ∈ {a,b}* | פלינדרום'],
    noncf: ['a^n b^n c^n', 'a^n b^n c^n d^n', 'a^n b^m c^k | n = m, m = k', 'a^n b^m c^k | n < m, m < k', 'a^n b^m c^n d^m', 'a^n b^m c^k d^l | n = k, m = l',
      'w ∈ {a,b,c}* | #a(w) = #b(w) וגם #b(w) = #c(w)'],
    unknown: ['a^n b^m | n = m או n != m', 'a^n a^n', 'a^n b^m | 2n = 2m + 1', 'a^n b^m c^k | n + m = k', 'a^n b^m c^k | n != m, m != k', 'a^n b^m | n = m, 2n = 2m',
      'w ∈ {a,b}* | #a(w) = #b(w) וגם |w| < 5', 'w ∈ {a,b}* | 2#a(w) = 2#b(w) + 1', 'a^n b^m | n != m, n = m'],
  };
  for (const [want, list] of Object.entries(table)) for (const f of list) assert.equal(cls(f), want, f);
});

test('סיווג: המשתנים החסומים והמקרים המנוונים לא מטעים', () => {
  assert.equal(cls('a^n b^n | n = 3'), 'regular');
  assert.equal(cls('a^n b^m | n = m, m < 2'), 'regular');
  assert.notEqual(cls('a^n b^m | n = m, n mod 2 = 0, m mod 2 = 1'), 'cf', 'שפה ריקה: אסור לטעון שהיא לא רגולרית');
  assert.notEqual(cls('a^n b^m | 2n != 2m + 1'), 'cf', 'תנאי שתמיד נכון');
});

function sameAsOracle(m, ast, budget, tag) {
  const v = L.verify(m, ast, budget);
  assert.ok(v.ok, tag + ': המכונה והנוסחה לא מסכימות על "' + v.word + '"');
  return v;
}

test('כל התבניות: הסיווג נכון, והמכונה שנבנית שקולה לנוסחה על כל המילים הקצרות', () => {
  for (const t of L.TEMPLATES) {
    const ast = P(t.text);
    assert.equal(L.analyze(ast).cls, t.cls, t.name);
    const r = L.build(ast);
    if (!t.builds) { assert.equal(r.ok, false, t.name); assert.match(r.reason, /עוד לא נתמכת/); continue; }
    assert.ok(r.ok, t.name + ': ' + r.reason);
    assert.equal(r.machine.type, t.builds, t.name);
    assert.deepEqual(M.validate(r.machine).filter((i) => i.level === 'error'), [], t.name + ' בלי שגיאות תקינות');
    const v = sameAsOracle(r.machine, ast, t.builds === 'dfa' ? 2500 : 1200, t.name);
    assert.ok(v.maxLen >= (ast.alphabet.length >= 4 ? 4 : ast.alphabet.length === 3 ? 6 : 9) && v.count > 300, t.name + ': נבדק עד אורך ' + v.maxLen);
    assert.ok(r.machine.tests.length >= 3 && S.runTests(r.machine, r.machine.tests).every((x) => x.pass), t.name + ': הבדיקות שצורפו עוברות');
    assert.equal(r.machine.language.text, t.text);
  }
});

test('אס"ד מינימלי: מספר המצבים הידוע', () => {
  const n = (f) => L.toDFA(P(f)).n;
  assert.equal(n('(a|b)*abb'), 4);
  assert.equal(n('w ∈ {a,b}* | #a(w) זוגי'), 2);
  assert.equal(n('w ∈ {a,b}* | מסתיימת ב-ab'), 3);
  assert.equal(n('w ∈ {a,b}* | מכילה aba'), 4);
  assert.equal(n('w ∈ {a,b}* | |w| mod 3 = 0'), 3);
  assert.equal(n('a^n b^m | n >= 2'), 5, 'כולל מצב מלכודת');
  assert.equal(n('w ∈ {a,b}* | #a(w) זוגי וגם #b(w) זוגי'), 4);
});

test('אוטומטי מחסנית: עוד משפחות (קבועים, יחסים, אי-שוויונות, רצף וקינון)', () => {
  const fs = ['a^n b^m | m = n + 2', 'a^n b^m | n = m + 1', 'a^n b^m | 2n = 3m', 'a^n b^m | n >= m', 'a^n b^m | n > m + 1', 'a^n b^m | n <= m', 'a^n b^m | n < 2m',
    'a^n b^m | n <= m + 2', 'a^n b^m | n >= m, n mod 2 = 0', 'a^n b^n | n >= 2', 'a^(n+1) b^n', 'a^n b^n c^m d^m', 'a^n b^m c^k | n = k, m >= 1', 'a^n c b^n', '(ab)^n c^n', 'a^n b^3n', 'a^2n b^n',
    'a^n b^m c^m d^n | n != 0', 'a^n b^m c^k d^l | n < l, m = k', 'a* b^n c^n'];
  for (const f of fs) {
    const ast = P(f);
    assert.equal(L.analyze(ast).cls, 'cf', f);
    const r = L.build(ast);
    assert.ok(r.ok, f + ': ' + r.reason);
    sameAsOracle(r.machine, ast, 1200, f);
  }
  const m = L.build(P('a^n b^n')).machine;
  assert.equal(m.states.length, 3, 'aⁿbⁿ הקלאסי: שלושה מצבים');
});

test('מכונת טיורינג ל-aⁿbⁿcⁿ, ומה שלא נתמך נאמר ביושר', () => {
  for (const f of ['a^n b^n c^n', 'a^n b^n c^n | n >= 1', '0^n 1^n 2^n 3^n']) {
    const r = L.build(P(f));
    assert.ok(r.ok && r.machine.type === 'tm', f + ': ' + r.reason);
    sameAsOracle(r.machine, P(f), 1500, f);
  }
  for (const f of ['a^n b^m c^n d^m', 'a^n b^n c^n | n mod 2 = 0', 'w ∈ {a,b}* | פלינדרום', 'a^n b^m | n = m או n != m']) {
    const r = L.build(P(f));
    assert.equal(r.ok, false, f);
    assert.match(r.reason, /עוד לא נתמכת/);
  }
});

function rng(seed) { return () => { seed |= 0; seed = (seed + 0x6D2B79F5) | 0; let t = Math.imul(seed ^ (seed >>> 15), 1 | seed); t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t; return ((t ^ (t >>> 14)) >>> 0) / 4294967296; }; }

test('fuzz עם זרע קבוע: נוסחאות רגולריות אקראיות, האס"ד מול בדיקת השייכות', () => {
  const rand = rng(2026), pick = (a) => a[Math.floor(rand() * a.length)];
  const regex = (d) => {
    const r = rand();
    if (d <= 0 || r < 0.3) return pick(['a', 'b', 'a', 'b', 'ε']);
    if (r < 0.55) return regex(d - 1) + regex(d - 1);
    if (r < 0.8) return '(' + regex(d - 1) + '|' + regex(d - 1) + ')';
    return '(' + regex(d - 1) + ')' + pick(['*', '*', '⁺']);
  };
  const single = (v) => pick([v + ' >= ' + Math.floor(rand() * 4), v + ' <= ' + Math.floor(rand() * 4), v + ' mod ' + (2 + Math.floor(rand() * 2)) + ' = ' + Math.floor(rand() * 2), v + ' != ' + Math.floor(rand() * 3)]);
  const blocks = () => {
    const c = [single('n'), single('m'), pick(['n + m >= ' + Math.floor(rand() * 5), '(n + m) mod 2 = 0', 'n >= 1'])];
    return pick(['a^n b^m', 'a^n b^m a', '(ab)^n b^m', 'a^n b a^m', 'a^2n b^(m+1)']) + ' | ' + c[0] + pick([', ', ' או ']) + c[1] + pick([', ', ' או ', ' וגם לא ']) + c[2];
  };
  const patom = () => pick(['#a(w) mod 2 = ' + Math.floor(rand() * 2), '#b(w) >= ' + Math.floor(rand() * 3), '|w| mod 3 = ' + Math.floor(rand() * 3), 'מתחילה ב-' + pick(['a', 'b', 'ab']), 'מסתיימת ב-' + pick(['a', 'ba', 'bb']), 'מכילה ' + pick(['aa', 'ab', 'bab']), '#a(w) + #b(w) <= ' + Math.floor(rand() * 4), '|w| != ' + Math.floor(rand() * 3)]);
  const prop = () => 'w ∈ {a,b}* | ' + patom() + pick([' וגם ', ' או ']) + pick(['', 'לא ']) + patom() + pick(['', ' או ' + patom()]);
  let built = 0;
  for (let i = 0; i < 150; i++) {
    const f = i % 3 === 0 ? regex(3) : i % 3 === 1 ? blocks() : prop();
    const ast = P(f);
    assert.equal(L.analyze(ast).cls, 'regular', f);
    const d = L.toDFA(ast);
    for (const w of L.words(ast.alphabet, 8)) assert.equal(L.dfaAccepts(d, w), L.accepts(ast, w), f + ' על "' + w + '"');
    built++;
  }
  assert.equal(built, 150);
});
