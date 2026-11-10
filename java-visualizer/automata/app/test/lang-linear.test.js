'use strict';
/* סבב 4: חזקות בכמה משתנים (aⁿ⁺ᵐbⁿ), תנאי שארית בכל כתיב, מכפלות וריבועים, איחוד ושרשור של שפות. */
const test = require('node:test');
const assert = require('node:assert/strict');
const L = require('../js/lang/index.js');
const S = require('../js/sim.js');
const M = require('../js/model.js');

const P = (s, o) => L.parse(s, o);
const cls = (s) => L.analyze(P(s)).cls;
const yes = (f, ws) => ws.forEach((w) => assert.equal(L.accepts(P(f), w), true, f + ' צריך לקבל "' + w + '"'));
const no = (f, ws) => ws.forEach((w) => assert.equal(L.accepts(P(f), w), false, f + ' צריך לדחות "' + w + '"'));
const err = (s) => { try { P(s); } catch (e) { assert.ok(e instanceof L.LangError, s + ': ' + e.message); return e; } assert.fail('ציפיתי לשגיאה: ' + s); };
/** המכונה שנבנית מקבלת בדיוק את מה שבדיקת השייכות מקבלת, על כל המילים עד האורך. */
function sameAsOracle(f, type, maxLen) {
  const ast = P(f), r = L.build(ast);
  assert.ok(r.ok, f + ': ' + r.reason);
  if (type) assert.equal(r.machine.type, type, f);
  assert.deepEqual(M.validate(r.machine).filter((i) => i.level === 'error'), [], f + ': בלי שגיאות תקינות');
  const len = maxLen || (ast.alphabet.length === 1 ? 30 : ast.alphabet.length === 2 ? 10 : 7);
  for (const w of L.words(ast.alphabet, len)) assert.equal(S.run(r.machine, w, { record: false }).result === 'accept', L.accepts(ast, w), f + ' "' + w + '"');
  return r.machine;
}

test('חזקות בכמה משתנים: פענוח וכתיבה יפה', () => {
  const a = P('a^(n+m) b^n');
  assert.equal(a.gen, true);
  assert.deepEqual(a.blocks[0].e.lin, { k: 0, co: { n: 1, m: 1 } });
  assert.deepEqual(a.vars, ['n', 'm']);
  const same = { 'a^(n+m) b^n': 'L = { aⁿ⁺ᵐbⁿ }', 'aⁿ⁺ᵐbⁿ': 'L = { aⁿ⁺ᵐbⁿ }', 'a^n+m b^n': 'L = { aⁿ⁺ᵐbⁿ }', 'a^(2n+m+1)': 'L = { a²ⁿ⁺ᵐ⁺¹ }',
    'a^n b^m c^(n+m)': 'L = { aⁿbᵐcⁿ⁺ᵐ }', 'a^(n+m) b^(n-m) | n >= m': 'L = { aⁿ⁺ᵐbⁿ⁻ᵐ | n ≥ m }', 'a^(2·n)': 'L = { a²ⁿ }',
    'a^(n*m)': 'L = { a^(n·m) }', 'a^(n^2)': 'L = { a^(n²) }', 'a^(n²)': 'L = { a^(n²) }', 'a^n b^(n^2)': 'L = { aⁿb^(n²) }' };
  for (const f in same) assert.equal(L.pretty(P(f)).text, same[f], f);
  assert.equal(L.pretty(P('a^(n+m) b^n')).html, 'L = { a<sup>n+m</sup>b<sup>n</sup> }');
  assert.equal(L.pretty(P('a^(n^2)')).html, 'L = { a<sup>n<sup>2</sup></sup> }');
  // חזקה של משתנה אחד נשארת בצורה הרגילה
  assert.deepEqual(P('a^(2n+1)').blocks[0].e, { c: 2, v: 'n', d: 1 });
  assert.equal(P('a^(2n+1)').gen, false);
});

test('תנאי שארית בכל כתיב: %, mod, ≡ (mod k), סכומים, |w| ו-#a(w)', () => {
  const forms = ['a^n | n%3=0', 'a^n | n % 3 = 0', 'a^n | n mod 3 = 0', 'a^n | n ≡ 0 (mod 3)', 'a^n | n ≡ 0 mod 3', 'a^n | n ≡ 3 (mod 3)', 'a^n | n מתחלק ב-3'];
  const ref = L.toDFA(P(forms[0]));
  assert.equal(ref.n, 3, 'אס"ד מינימלי של 3 מצבים');
  for (const f of forms) { assert.equal(cls(f), 'regular', f); assert.ok(L.compare(ref, L.toDFA(P(f)), 1).equal, f); }
  assert.equal(L.pretty(P('a^n | n ≡ 4 (mod 3)')).text, 'L = { aⁿ | n mod 3 = 1 }');
  yes('a^n b^m | (n+m)%2=1', ['a', 'b', 'aab', 'abb']); no('a^n b^m | (n+m)%2=1', ['', 'ab', 'ba']);
  yes('a^n b^m | n ≡ m (mod 2)', ['', 'ab', 'aabb', 'aaab']); no('a^n b^m | n ≡ m (mod 2)', ['a', 'aab']);
  yes('w ∈ {a,b}* | #a(w) mod 2 = #b(w) mod 2', ['', 'ab', 'ba', 'aaab']); no('w ∈ {a,b}* | #a(w) mod 2 = #b(w) mod 2', ['a', 'aab']);
  for (const f of ['a^n b^m | (n+m)%2=1', 'a^n b^m | n ≡ m (mod 2)', 'w ∈ {a,b}* | #a(w) mod 2 = #b(w) mod 2', 'w ∈ {a,b}* | |w| % 3 = 0', 'a^n b^m c^k | (n+2m+k)%3=0, k%2=1']) {
    assert.equal(cls(f), 'regular', f);
    sameAsOracle(f, 'dfa');
  }
  // מספרי המצבים הידועים
  const n = (f) => L.toDFA(P(f)).n;
  assert.equal(n('a^n b^m | n%3=0'), 5);
  assert.equal(n('(ab)^n | n%3=0'), 7);
  assert.equal(n('a^n b^m | (n+m)%2=0'), 5);
  assert.equal(n('a^(2n+m+1)'), 2);
  assert.equal(n('a^(n+m)'), 1);
  assert.match(err('a^n | n ≡ 1').message, /אחרי ≡/);
  assert.match(err('a^(n mod 2)').message, /בחזקה אי אפשר mod/);
});

test('שייכות: הצבה של כל הערכים, חסומה באורך המילה', () => {
  yes('a^(n+m) b^n', ['', 'a', 'ab', 'aab', 'aaabb']); no('a^(n+m) b^n', ['b', 'abb', 'ba']);
  yes('a^n b^m c^(n+m)', ['', 'ac', 'bc', 'abcc', 'aabccc']); no('a^n b^m c^(n+m)', ['abc', 'ca', 'aacc' + 'c']);
  yes('a^n b^(n+m) c^m', ['', 'ab', 'bc', 'abbc']); no('a^n b^(n+m) c^m', ['abc', 'abbcc']);
  yes('a^(n+m) b^(n-m) | n >= m', ['', 'aa', 'ab', 'aaab', 'aaaa']); no('a^(n+m) b^(n-m) | n >= m', ['a', 'abb', 'aaa']);
  yes('a^(n+m) b^(n-m)', ['aa', 'ab']); no('a^(n+m) b^(n-m)', ['abb']);
  yes('a^n b^m c^k | k = n+m, m >= 1', ['bc', 'abcc']); no('a^n b^m c^k | k = n+m, m >= 1', ['', 'ac']);
  yes('a^(n*m) | n >= 2, m >= 2', ['aaaa', 'aaaaaa', 'a'.repeat(9)]); no('a^(n*m) | n >= 2, m >= 2', ['', 'aaa', 'aaaaa', 'a'.repeat(7)]);
  yes('a^(n*m) | n >= 5', ['']);
  yes('a^n b^(n^2)', ['', 'ab', 'aabbbb']); no('a^n b^(n^2)', ['abb', 'aabb']);
  assert.match(err('a^(n-m)').message, /המשתנה m מופיע רק עם מינוס/);
});

test('סיווג: טבלה של מקרים ידועים (חזקות בכמה משתנים, מכפלות, איחוד ושרשור)', () => {
  const table = {
    regular: ['a^(n+m)', 'a^(2n+m+1)', 'a^n a^n', 'a^(n+m) b^(m+k)', 'a^n b^m | (n+m)%2=0', 'a^(n+m) b^k | (n+m+k)%3=0', '{a^n} ∪ {b^n}', '{a^n} · {b^m}', 'a* b ∪ b a*'],
    cf: ['a^(n+m) b^n', 'a^n b^m c^(n+m)', 'a^n b^(n+m) c^m', 'a^(n+m) b^(n-m) | n >= m', 'a^(n+m) b^(n-m)', 'a^n b^m c^k | k = n+m, m >= 1', 'a^n b^m c^k | n + m = k',
      'a^(2n) b^(n+1)', 'a^n b^m c^(n+m) | n%2=0', 'a^n b^m | n <= m, n%2=0', 'a^(n+m) b^m c^(n+k) d^k', 'a^n b^n ∪ b^n a^n', '{a^n b^n} · {c^k}', '{a^n b^n}{c^k}'],
    noncf: ['a^n b^m c^n d^m', 'a^(n+m) b^(n+m) c^(n+m)', 'a^(n+m) b^n c^n', 'a^n b^(n+m) c^(n+k)', 'a^(2n+1) b^(n+m) c^(n+m)', 'a^n b^m c^n d^m | n%2=0', 'a^(n^2)', 'a^n b^(n^2)', 'a^(n^2+1) b^m', 'a^(2^n) b^n', '{a^n b^n c^n} ∪ {b^n a^n}'],
    unknown: ['a^(n*m)', 'a^n b^m | n < 2m, m < 2n', 'a^n b^m c^k | n != m + k', 'a^(n^2+m)', 'a^(n^2) a^n', '{a^n b^n c^n} ∪ {a^n b^m}'],
  };
  for (const [want, list] of Object.entries(table)) for (const f of list) assert.equal(cls(f), want, f);
  // "חסרת הקשר" בלי טענה על רגולריות, כשאין הוכחה
  assert.equal(cls('a^n b^n ∪ a^n b^2n'), 'cfany');
  assert.equal(cls('a^(n+m) b^(n+k) c^(m+k)'), 'cfany');
  assert.equal(cls('a^(n+f) b^(n+g) c^(n+h)'), 'regular', 'כל בלוק עם ממלא משלו');
  assert.equal(L.analyze(P('a^n b^n ∪ a^n b^2n')).label, 'חסרת הקשר');
});

test('בנייה: אס"ד ואוטומט מחסנית לחזקות בכמה משתנים, שקולים לנוסחה על כל המילים הקצרות', () => {
  for (const f of ['a^(n+m)', 'a^(2n+m+1)', 'a^n a^n', 'a^(n+m) b^(m+k)', 'a^n b^m | (n+m)%2=0']) sameAsOracle(f, 'dfa');
  for (const f of ['a^(n+m) b^n', 'a^n b^m c^(n+m)', 'a^n b^(n+m) c^m', 'a^(n+m) b^(n-m) | n >= m', 'a^(n+m) b^(n-m)', 'a^n b^m c^k | k = n+m, m >= 1',
    'a^(2n) b^(n+1)', 'a^n b^m c^(n+m) | n%2=0', 'a^n b^m | n <= m, n%2=0', 'a^n b^m c^(n+m) | (n+m)%2=0', 'a^(n+m) b^m c^(n+k) d^k']) sameAsOracle(f, 'pda', f.includes('d') ? 6 : null);
  // aⁿbᵐcⁿ⁺ᵐ: אוטומט קטן, ומילים מוכרות
  const m = sameAsOracle('a^n b^m c^(n+m)', 'pda');
  assert.ok(m.states.length <= 8, 'מצבים: ' + m.states.length);
  for (const w of ['aabccc', 'bbcc']) assert.equal(S.run(m, w).result, 'accept', w);
  for (const w of ['aabcc', 'abc']) assert.equal(S.run(m, w).result, 'reject', w);
  // מה שלא נבנה נאמר ביושר
  for (const f of ['a^n b^m c^n d^m', 'a^(n^2)', 'a^(n*m)']) assert.match(L.build(P(f)).reason, /עוד לא נתמכת/, f);
});

test('איחוד ושרשור: פענוח, שייכות, כתיבה יפה ובנייה', () => {
  const u = P('a^n b^n ∪ b^n a^n');
  assert.equal(u.kind, 'union');
  assert.equal(L.pretty(u).text, 'L = { aⁿbⁿ } ∪ { bⁿaⁿ }');
  yes('a^n b^n ∪ b^n a^n', ['', 'ab', 'ba', 'aabb', 'bbaa']); no('a^n b^n ∪ b^n a^n', ['aab', 'abab', 'abba']);
  const c = P('{a^n b^n} · {c^k}');
  assert.equal(c.kind, 'concat');
  assert.ok(c.flat, 'שרשור של בלוקים הוא שפת בלוקים אחת');
  assert.equal(L.pretty(c).text, 'L = { aⁿbⁿ } · { cᵏ }');
  assert.equal(L.pretty(P('{a^n b^n}{c^k}')).text, 'L = { aⁿbⁿ } · { cᵏ }');
  yes('{a^n b^n} · {c^k}', ['', 'c', 'abcc']); no('{a^n b^n} · {c^k}', ['ca', 'aabc']);
  yes('{a^n b^n} · {w ∈ {a,b}* | מסתיימת ב-a}', ['a', 'aba', 'abba']); no('{a^n b^n} · {w ∈ {a,b}* | מסתיימת ב-a}', ['', 'ab']);
  assert.equal(P('(a|b)* ∪ ab').kind, 'regex', '∪ בין ביטויים רגולריים נשאר ביטוי רגולרי');
  assert.equal(L.pretty(P('{a^n} ∪ {b^m | m%2=0}')).text, 'L = { aⁿ } ∪ { bᵐ | m mod 2 = 0 }');
  assert.match(err('a^n b^n ∪').message, /חסרה שפה אחרי/);
  for (const f of ['{a^n} ∪ {b^m | m%2=0}', '{a^n} · {b^m}', 'a* b ∪ b a*']) sameAsOracle(f, 'dfa');
  for (const f of ['a^n b^n ∪ b^n a^n', '{a^n b^n} · {c^k}', 'a^n b^n ∪ a^n b^2n', '{a^n b^n} ∪ {w ∈ {a,b}* | מסתיימת ב-ba}', '{a^n b^n} · {w ∈ {a,b}* | מסתיימת ב-a}', '{w w^R} · {c^n}']) sameAsOracle(f, 'pda', 8);
});

test('בדיקה מול השפה עובדת גם לצורות החדשות (מדויקת לרגולריות, חסומה לשאר)', () => {
  const ok = (f) => { const ast = P(f), m = L.machineFor(ast), r = L.check(m, ast); assert.ok(r.ok, f); return r; };
  assert.equal(ok('a^n b^m | (n+m)%2=0').exact, true);
  assert.equal(ok('{a^n} ∪ {b^m}').exact, true);
  assert.equal(ok('a^n b^m c^(n+m)').exact, false);
  const wrong = L.check(L.machineFor(P('a^n b^m c^(n+m)')), P('a^n b^m c^(n+m+1)'));
  assert.equal(wrong.ok, false);
  assert.equal(wrong.missing[0], 'c');
});

function rng(seed) { return () => { seed |= 0; seed = (seed + 0x6D2B79F5) | 0; let t = Math.imul(seed ^ (seed >>> 15), 1 | seed); t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t; return ((t ^ (t >>> 14)) >>> 0) / 4294967296; }; }

test('fuzz עם זרע קבוע: חזקות ליניאריות אקראיות בכמה משתנים; כל בנייה שקולה לנוסחה', () => {
  const rand = rng(404), pick = (a) => a[Math.floor(rand() * a.length)];
  const CONDS = ['', '', '', ' | n%2=0', ' | m >= 1', ' | (n+m)%3=0', ' | n <= m', ' | n%3=1, m >= 2', ' | n >= 2', ' | n < m', ' | n = 2m'];
  const seen = { regular: 0, cf: 0, cfany: 0, noncf: 0, unknown: 0 };
  for (let it = 0; it < 70; it++) {
    const nb = 2 + Math.floor(rand() * 2), letters = ['a', 'b', 'c'].slice(0, nb), vars = ['n', 'm'].slice(0, 1 + Math.floor(rand() * 2));
    const used = new Set();
    const exps = letters.map(() => {
      const t = [];
      for (const v of vars) { const c = pick([0, 0, 1, 1, 2]); if (c) { t.push((c === 1 ? '' : c) + v); used.add(v); } }
      if (pick([0, 0, 0, 1]) || !t.length) t.push('1');
      return t.join('+');
    });
    for (const v of vars) if (!used.has(v)) exps[0] += '+' + v;
    let cond = pick(CONDS);
    if (/m/.test(cond) && !vars.includes('m')) cond = '';
    const f = letters.map((l, i) => l + '^(' + exps[i] + ')').join(' ') + cond;
    const ast = P(f), an = L.analyze(ast);
    seen[an.cls]++;
    if (an.cls === 'regular' || an.cls === 'cf' || an.cls === 'cfany') sameAsOracle(f, an.cls === 'regular' ? 'dfa' : 'pda', nb === 2 ? 9 : 6);
  }
  assert.ok(seen.regular >= 10 && seen.cf >= 10, JSON.stringify(seen));
});
