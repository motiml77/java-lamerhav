'use strict';
/* סבב 7: תנאים על המשתנים אחרי | (או : או /), בכתיב של ספרי הלימוד. לכל צורה: פענוח, כתיבה יפה, שייכות, סיווג ובנייה. */
const test = require('node:test');
const assert = require('node:assert/strict');
const L = require('../js/lang/index.js');
const S = require('../js/sim.js');

const P = (s) => L.parse(s);
const err = (s) => { try { P(s); } catch (e) { assert.ok(e instanceof L.LangError, s + ': ' + e.message); return e; } assert.fail('ציפיתי לשגיאה: ' + s); };
/** בונה, ובודק שהמכונה מקבלת בדיוק את מה שבדיקת השייכות מקבלת על כל המילים הקצרות. */
function builds(f, type) {
  const ast = P(f), r = L.build(ast);
  assert.ok(r.ok, f + ': ' + r.reason);
  assert.equal(r.machine.type, type, f);
  for (const w of L.words(ast.alphabet, ast.alphabet.length === 1 ? 20 : 9)) assert.equal(S.run(r.machine, w, { record: false }).result === 'accept', L.accepts(ast, w), f + ' "' + w + '"');
}

// [נוסחה, כתיבה יפה, מילים בשפה, מילים לא בשפה, סיווג, מה נבנה]
const TABLE = [
  ['a^n b^n | n<1', 'L = { aⁿbⁿ | n < 1 }', [''], ['ab', 'a'], 'regular', 'dfa'],
  ['a^n | n>2', 'L = { aⁿ | n > 2 }', ['aaa'], ['', 'aa'], 'regular', 'dfa'],
  ['a^n | n≥1', 'L = { aⁿ | n ≥ 1 }', ['a'], [''], 'regular', 'dfa'],
  ['a^n | n>=1', 'L = { aⁿ | n ≥ 1 }', ['a'], [''], 'regular', 'dfa'],
  ['a^n | n≤5', 'L = { aⁿ | n ≤ 5 }', ['', 'aaaaa'], ['aaaaaa'], 'regular', 'dfa'],
  ['a^n | n<=5', 'L = { aⁿ | n ≤ 5 }', ['aaaaa'], ['aaaaaa'], 'regular', 'dfa'],
  ['a^n | n=3', 'L = { aⁿ | n = 3 }', ['aaa'], ['aa', 'aaaa'], 'regular', 'dfa'],
  ['a^n | n≠0', 'L = { aⁿ | n ≠ 0 }', ['a'], [''], 'regular', 'dfa'],
  ['a^n | n!=0', 'L = { aⁿ | n ≠ 0 }', ['a'], [''], 'regular', 'dfa'],
  ['a^n | n<0', 'L = { aⁿ | n < 0 }', [], ['', 'a'], 'regular', 'dfa'],
  ['a^n b^n | n≤3', 'L = { aⁿbⁿ | n ≤ 3 }', ['', 'aaabbb'], ['aaaabbbb', 'aab'], 'regular', 'dfa'],
  ['a^n | 1≤n≤5', 'L = { aⁿ | 1 ≤ n ≤ 5 }', ['a', 'aaaaa'], ['', 'aaaaaa'], 'regular', 'dfa'],
  ['a^n | 1<n<4', 'L = { aⁿ | 1 < n < 4 }', ['aa', 'aaa'], ['a', 'aaaa'], 'regular', 'dfa'],
  ['a^n b^n | 1≤n≤3', 'L = { aⁿbⁿ | 1 ≤ n ≤ 3 }', ['ab', 'aaabbb'], ['', 'aaaabbbb'], 'regular', 'dfa'],
  ['a^n b^m | 2 ≤ n < m', 'L = { aⁿbᵐ | 2 ≤ n < m }', ['aabbb'], ['abb', 'aabb'], 'cf', 'pda'],
  ['a^n b^m | n<m', 'L = { aⁿbᵐ | n < m }', ['b', 'abb'], ['', 'ab'], 'cf', 'pda'],
  ['a^n b^m | n>m', 'L = { aⁿbᵐ | n > m }', ['a', 'aab'], ['', 'ab'], 'cf', 'pda'],
  ['a^n b^m | n≤m', 'L = { aⁿbᵐ | n ≤ m }', ['', 'ab'], ['a'], 'cf', 'pda'],
  ['a^n b^m | n≥m+2', 'L = { aⁿbᵐ | n ≥ m + 2 }', ['aa', 'aaab'], ['a', 'aab'], 'cf', 'pda'],
  ['a^n b^m | m=2n', 'L = { aⁿbᵐ | m = 2n }', ['', 'abb'], ['ab'], 'cf', 'pda'],
  ['a^n b^m | n=m+1', 'L = { aⁿbᵐ | n = m + 1 }', ['a', 'aab'], ['', 'ab'], 'cf', 'pda'],
  ['a^n b^m | n≠m', 'L = { aⁿbᵐ | n ≠ m }', ['a', 'b'], ['', 'ab'], 'cf', 'pda'],
  ['a^n b^m | n+m≥3', 'L = { aⁿbᵐ | n + m ≥ 3 }', ['aab'], ['ab'], 'regular', 'dfa'],
  ['a^n b^m | 2n≤m', 'L = { aⁿbᵐ | 2n ≤ m }', ['abb'], ['ab'], 'cf', 'pda'],
  ['a^n b^m | n≥1, m≥1', 'L = { aⁿbᵐ | n ≥ 1, m ≥ 1 }', ['ab'], ['a', 'b'], 'regular', 'dfa'],
  ['a^n b^m | n>m ∧ m≥2', 'L = { aⁿbᵐ | n > m, m ≥ 2 }', ['aaabb'], ['aab', 'aabb'], 'cf', 'pda'],
  ['a^n b^m | n>m and m≥2', 'L = { aⁿbᵐ | n > m, m ≥ 2 }', ['aaabb'], ['aabb'], 'cf', 'pda'],
  ['a^n b^m | n>m וגם m≥2', 'L = { aⁿbᵐ | n > m, m ≥ 2 }', ['aaabb'], ['aabb'], 'cf', 'pda'],
  ['a^n b^m | n>m או m>3', 'L = { aⁿbᵐ | n > m או m > 3 }', ['a', 'bbbb'], ['ab', 'bbb'], 'cfany', 'pda'],
  ['a^n b^m | n>m ∨ m>3', 'L = { aⁿbᵐ | n > m או m > 3 }', ['a', 'bbbb'], ['bbb'], 'cfany', 'pda'],
  ['a^n b^m | n,m ≥ 0', 'L = { aⁿbᵐ | n, m ≥ 0 }', ['', 'ab'], ['ba'], 'regular', 'dfa'],
  ['a^n b^m | n,m≥1', 'L = { aⁿbᵐ | n, m ≥ 1 }', ['ab'], ['a', 'b'], 'regular', 'dfa'],
  ['a^n | n ∈ ℕ', 'L = { aⁿ | n ∈ ℕ }', ['', 'a'], [], 'regular', 'dfa'],
  ['a^n | n ∈ ℕ⁺', 'L = { aⁿ | n ∈ ℕ⁺ }', ['a'], [''], 'regular', 'dfa'],
  ['a^n b^m | n,m ∈ N', 'L = { aⁿbᵐ | n, m ∈ ℕ }', ['', 'ab'], ['ba'], 'regular', 'dfa'],
  ['a^n b^m | n>0 , m>0', 'L = { aⁿbᵐ | n > 0, m > 0 }', ['ab'], ['a'], 'regular', 'dfa'],
  ['a^n b^k | k ≥ 1', 'L = { aⁿbᵏ | k ≥ 1 }', ['b'], ['a'], 'regular', 'dfa'],
  ['a^n b^m | n,m > 0, n<m', 'L = { aⁿbᵐ | n, m > 0, n < m }', ['abb'], ['b', 'ab'], 'cf', 'pda'],
  ['a^n b^n : n≥1', 'L = { aⁿbⁿ | n ≥ 1 }', ['ab'], [''], 'cf', 'pda'],
  ['a^n b^n / n≥1', 'L = { aⁿbⁿ | n ≥ 1 }', ['ab'], [''], 'cf', 'pda'],
  ['a^n b^n/n≥1', 'L = { aⁿbⁿ | n ≥ 1 }', ['ab'], [''], 'cf', 'pda'],
  ['a^n b^n|n≥1', 'L = { aⁿbⁿ | n ≥ 1 }', ['ab'], [''], 'cf', 'pda'],
  ['a^n b^n כאשר n≥1', 'L = { aⁿbⁿ | n ≥ 1 }', ['ab'], [''], 'cf', 'pda'],
  ['L = { a^n b^n | n ≥ 1 }', 'L = { aⁿbⁿ | n ≥ 1 }', ['ab'], [''], 'cf', 'pda'],
];

test('טבלה: כל צורות התנאים, כתיבה יפה, שייכות, סיווג ובנייה שקולה לנוסחה', () => {
  for (const [f, pretty, ins, outs, cls, type] of TABLE) {
    const ast = P(f);
    assert.equal(L.pretty(ast).text, pretty, f);
    for (const w of ins) assert.equal(L.accepts(ast, w), true, f + ' צריך לקבל "' + w + '"');
    for (const w of outs) assert.equal(L.accepts(ast, w), false, f + ' צריך לדחות "' + w + '"');
    assert.equal(L.analyze(ast).cls, cls, f);
    builds(f, type);
  }
});

test('שפה ריקה או סופית: ההסבר אומר את זה', () => {
  assert.match(L.analyze(P('a^n | n<0')).why, /השפה ריקה/);
  assert.equal(L.analyze(P('a^n b^n | n<1')).finite, 1);
  assert.match(L.analyze(P('a^n b^n | n<1')).why, /מילה אחת/);
  assert.match(L.analyze(P('a^n b^n | n≤3')).why, /השפה סופית: 4 מילים/);
  assert.equal(L.analyze(P('a^n b^n | 1≤n≤3')).finite, 3);
  assert.equal(L.analyze(P('a^n | n>2')).finite, undefined);
});

test('שגיאות והודעות: משתנה שלא בחזקה, תנאי לא ליניארי, ∈ בלי ℕ', () => {
  assert.match(err('a^n b^n | k≥1').message, /המשתנה k לא מופיע באף חזקה/);
  assert.match(err('a^n b^n | n,k ≥ 1').message, /המשתנה k לא מופיע באף חזקה/);
  assert.match(err('a^n | n ∈ Z').message, /אחרי ∈ בתנאי צריך לבוא ℕ/);
  for (const f of ['a^n b^m | n·m<5', 'a^n b^m | n*m < 5', 'a^n b^m | n^2 < m']) {
    const an = L.analyze(P(f));
    assert.equal(an.cls, 'unknown', f);
    assert.match(an.why, /תנאי לא ליניארי.*נתמכים/, f);
    assert.match(L.build(P(f)).reason, /עוד לא נתמכת/);
  }
  assert.equal(L.analyze(P('a^n b^m | (n mod 2) < m')).cls, 'regular', 'mod ועוד משתנה: בטוח');
});

function rng(seed) { return () => { seed |= 0; seed = (seed + 0x6D2B79F5) | 0; let t = Math.imul(seed ^ (seed >>> 15), 1 | seed); t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t; return ((t ^ (t >>> 14)) >>> 0) / 4294967296; }; }

test('fuzz עם זרע קבוע: גבולות ויחסים אקראיים; כל מכונה שנבנית שקולה לנוסחה', () => {
  const rand = rng(77), pick = (a) => a[Math.floor(rand() * a.length)], num = () => Math.floor(rand() * 4);
  const one = () => pick([
    () => 'n ' + pick(['<', '>', '≤', '≥', '=', '≠']) + ' ' + num(),
    () => 'm ' + pick(['<', '>', '≤', '≥']) + ' ' + num(),
    () => num() + ' ≤ n ≤ ' + (num() + 2),
    () => 'n ' + pick(['<', '>', '≤', '≥', '=', '≠']) + ' m' + pick(['', ' + 1', ' + 2']),
    () => pick(['2n', 'n']) + ' ' + pick(['≤', '≥', '=']) + ' ' + pick(['m', '2m', 'm + 1']),
    () => 'n + m ' + pick(['≥', '≤', '<']) + ' ' + (num() + 1),
    () => 'n,m ' + pick(['≥ 1', '> 0', '∈ ℕ']),
  ])();
  const seen = {};
  for (let it = 0; it < 60; it++) {
    const blocks = pick(['a^n b^m', 'a^n b^m c^n', 'a^n b^(n+m)', 'a^n b^m a^m']);
    const conds = [one()];
    if (rand() < 0.5) conds.push(one());
    const f = blocks + ' | ' + conds.join(pick([', ', ' וגם ', ' ∧ ']));
    const ast = P(f), an = L.analyze(ast);
    seen[an.cls] = (seen[an.cls] || 0) + 1;
    if (an.cls === 'regular' || an.cls === 'cf' || an.cls === 'cfany') {
      const r = L.build(ast);
      assert.ok(r.ok, f + ': ' + r.reason);
      for (const w of L.words(ast.alphabet, ast.alphabet.length === 2 ? 9 : 6)) assert.equal(S.run(r.machine, w, { record: false }).result === 'accept', L.accepts(ast, w), f + ' "' + w + '"');
    }
  }
  assert.ok(seen.regular >= 5 && seen.cf >= 5, JSON.stringify(seen));
});
