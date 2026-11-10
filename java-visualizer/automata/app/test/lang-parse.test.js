'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const L = require('../js/lang/index.js');

const P = (s, o) => L.parse(s, o);
const err = (s) => { try { P(s); } catch (e) { assert.ok(e instanceof L.LangError, s + ': ' + e.message); return e; } assert.fail('ציפיתי לשגיאה: ' + s); };

test('שלוש הצורות מזוהות לבד', () => {
  assert.equal(P('a^n b^n').kind, 'blocks');
  assert.equal(P('aⁿbᵐ | n ≥ 2').kind, 'blocks');
  assert.equal(P('w ∈ {a,b}* | #a(w) mod 2 = 0').kind, 'prop');
  assert.equal(P('מסתיימת ב-ab').kind, 'prop');
  assert.equal(P('(a|b)*abb').kind, 'regex');
  assert.equal(P('L = { a^n b^n }').kind, 'blocks');
});

test('בלוקים: חזקות, קבוצות, כוכב, פלוס, ASCII וסימנים', () => {
  const a = P('a^n b^2n (ab)^3 c* d+ e^(2k+1)');
  assert.deepEqual(a.blocks.map((b) => [b.word, b.e.c, b.e.v && b.e.v[0] === '_' ? '_' : b.e.v, b.e.d]), [['a', 1, 'n', 0], ['b', 2, 'n', 0], ['ab', 0, null, 3], ['c', 1, '_', 0], ['d', 1, '_', 1], ['e', 2, 'k', 1]]);
  assert.deepEqual(a.vars, ['n', 'k']);
  assert.deepEqual(P('a^nb^n').blocks.map((b) => b.word), ['a', 'b']);
  assert.deepEqual(P('0^n1^n').alphabet, ['0', '1']);
  assert.equal(L.pretty(P('aⁿb²ⁿ')).text, 'L = { aⁿb²ⁿ }');
  assert.equal(L.pretty(P('a^n b^m | n >= 2, m != n')).text, 'L = { aⁿbᵐ | n ≥ 2, m ≠ n }');
  assert.match(L.pretty(P('a^n b^m, n <= m')).html, /a<sup>n<\/sup>b<sup>m<\/sup>/);
});

test('תנאים: שרשרת, mod, זוגי, וגם / או / לא, סוגריים', () => {
  assert.equal(P('a^n b^m | 0 < n < m').cond.op, 'and');
  assert.equal(P('a^n | n mod 3 = 1').cond.l.t, 'mod');
  assert.equal(P('a^n | n % 3 = 1').cond.l.m, 3);
  assert.deepEqual(P('a^n | n זוגי').cond, P('a^n | n mod 2 = 0').cond);
  assert.deepEqual(P('a^n | n אי-זוגי').cond, P('a^n | n odd').cond);
  assert.deepEqual(P('a^n | n מתחלק ב-3').cond, P('a^n | n mod 3 = 0').cond);
  assert.equal(P('a^n b^m | n > 2 או m > 2').cond.op, 'or');
  assert.equal(P('a^n b^m c^k | (n > 2 או m > 2) וגם k = 1').cond.op, 'and');
  assert.equal(P('a^n b^m | לא (n = m)').cond.op, 'not');
  assert.equal(P('a^n b^m | (n + m) mod 2 = 0').cond.l.t, 'mod');
});

test('תכונות של מילה: אלפבית, ספירות, אורך, תחיליות וסיומות', () => {
  assert.deepEqual(P('w ∈ {a,b,c}* | |w| > 2').alphabet, ['a', 'b', 'c']);
  assert.deepEqual(P('#a(w) זוגי').alphabet, ['a', 'b'], 'ברירת מחדל {a,b}');
  assert.deepEqual(P('#0(w) = #1(w)').alphabet, ['0', '1']);
  assert.deepEqual(P('|w| זוגי', { alphabet: ['x', 'y'] }).alphabet, ['x', 'y'], 'האלפבית של המכונה');
  const c = P('w ∈ {a,b}* | מתחילה ב-a וגם מסתיימת ב-bb וגם לא מכילה aaa').cond;
  assert.deepEqual(c.args.map((x) => x.op), ['starts', 'ends', 'not']);
  assert.equal(c.args[2].arg.word, 'aaa');
  assert.equal(P('|w|_a = 2').cond.l.n, '#a');
});

test('ביטוי רגולרי: + בין ביטויים הוא איחוד, + בסוף הוא "אחד או יותר"', () => {
  assert.equal(L.pretty(P('(a+b)*abb')).text, 'L = (a ∪ b)*abb');
  assert.equal(L.pretty(P('a+')).text, 'L = a⁺');
  assert.equal(L.pretty(P('(ab)+ ∪ ε')).text, 'L = (ab)⁺ ∪ ε');
  assert.equal(L.pretty(P('a⁺b')).text, 'L = a⁺b');
});

test('שגיאות: הודעה בעברית, מיקום מדויק, והצעה', () => {
  let e = err('a^n b^');
  assert.equal(e.pos, 6); assert.match(e.message, /אחרי \^/); assert.match(e.hint, /a\^n/);
  e = err('a^n b^m | n >');
  assert.equal(e.pos, 13); assert.match(e.message, /נקטע/);
  e = err('a^n | x > 2');
  assert.equal(e.pos, 6); assert.match(e.message, /המשתנה x/);
  e = err('a^n b^m | n > 2 m < 3');
  assert.equal(e.pos, 16);
  e = err('(a|b');
  assert.match(e.message, /סוגר/);
  e = err('a^n ? b');
  assert.equal(e.pos, 4); assert.match(e.message, /התו \?/);
  e = err('L = { a^n b^ }');
  assert.equal(e.pos, 13, 'המיקום יחסי לטקסט המקורי, כולל העטיפה');
  assert.match(err('').message, /עוד לא נכתבה/);
  assert.match(err('w ∈ {a,b}* | #c(w) > 1').message, /לא באלפבית/);
  assert.match(err('a^n n^m').message, /גם כאות/);
  assert.match(err('a^(n-m)').message, /המשתנה m מופיע רק עם מינוס/);
  assert.match(err('a^(5-n)').message, /לפחות משתנה אחד עם פלוס/);
  assert.match(err('a^(n mod 2)').message, /בחזקה אי אפשר mod/);
  assert.match(err('a^n | n ≡ 1').message, /אחרי ≡/);
  assert.match(err('a^n | n mod 0 = 1').message, /לחלק ב-0/);
});
