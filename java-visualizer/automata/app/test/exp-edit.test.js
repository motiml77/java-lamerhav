'use strict';
/* עריכה של חזקה בלחיצה (סבב 13): כל חזקה בתצוגה היפה ממופה למקום שלה בטקסט, וההחלפה משנה רק אותה. */
const test = require('node:test');
const assert = require('node:assert/strict');
const L = require('../js/lang/index.js');

const list = (t) => L.exponents(L.parse(t)).map((s) => [s.form, t.slice(s.at[0], s.at[1]), L.exponentText(t, s)]);
const edit = (t, i, x) => { const ast = L.parse(t); const r = L.editExponent(t, ast, L.exponents(ast)[i].at[0], x); return r.ok ? r.text : 'ERR: ' + r.error; };

test('המיפוי: חזקה עם ^, בסוגריים, בלי סוגריים, בסימנים עיליים, ובתוך L = { }', () => {
  assert.deepEqual(list('a^n b^m c^(n+m)'), [['caret', '^n', 'n'], ['caret', '^m', 'm'], ['paren', '^(n+m)', 'n+m']]);
  assert.deepEqual(list('aⁿ⁺ᵐbᵐ'), [['sup', 'ⁿ⁺ᵐ', 'n+m'], ['sup', 'ᵐ', 'm']]);
  assert.deepEqual(list('L = { a^n+m b^m | n ≥ 1 }'), [['caret', '^n+m', 'n+m'], ['caret', '^m', 'm']]);
  assert.deepEqual(list('a^( 2n + 1 )b^3'), [['paren', '^( 2n + 1 )', '2n + 1'], ['caret', '^3', '3']]);
  assert.deepEqual(list('a^(2^n)'), [['paren', '^(2^n)', '2^n']]);
  assert.deepEqual(list('a* b⁺ c^n').map((x) => x[1]), ['^n'], 'כוכב ופלוס הם לא חזקות לעריכה');
});

test('איחוד ושרשור: לכל חלק המיקומים שלו בטקסט המלא', () => {
  const t = '{ a^n b^n } ∪ { b^(2n) a^n }';
  assert.deepEqual(list(t).map((x) => x[1]), ['^n', '^n', '^(2n)', '^n']);
  assert.equal(edit(t, 2, 'n+1'), '{ a^n b^n } ∪ { b^(n+1) a^n }');
  assert.equal(edit('{ aⁿbⁿ } · { cᵏ }', 2, 'k+1'), '{ aⁿbⁿ } · { cᵏ⁺¹ }');
  const html = L.pretty(L.parse(t), { edit: true }).html;
  assert.equal((html.match(/class="ex"/g) || []).length, 4);
  assert.deepEqual(Array.from(html.matchAll(/data-ex="(\d+)"/g)).map((m) => t.slice(+m[1], +m[1] + 1)), ['^', '^', '^', '^']);
  assert.ok(!L.pretty(L.parse(t)).html.includes('data-ex'), 'בלי edit: אותו html כמו קודם');
});

test('ההחלפה: ביטוי פשוט בלי סוגריים, מורכב בסוגריים, עיליים נשארים עיליים כשאפשר', () => {
  assert.equal(edit('a^n b^m c^(n+m)', 2, 'm+k+2'), 'a^n b^m c^(m+k+2)');
  assert.equal(edit('a^n b^m c^(n+m)', 0, '2n'), 'a^2n b^m c^(n+m)');
  assert.equal(edit('a^n b^n', 1, ' 2n + 1 '), 'a^n b^(2n + 1)');
  assert.equal(edit('a^n+m b^m', 0, 'k'), 'a^k b^m');
  assert.equal(edit('aⁿbⁿ', 1, 'n+1'), 'aⁿbⁿ⁺¹');
  assert.equal(edit('aⁿbⁿ', 1, 'n·m'), 'aⁿb^(n·m)', '· אין בסימנים עיליים');
  assert.equal(edit('aⁿbⁿ', 1, 'n²'), 'aⁿb^(n²)');
  const ast = L.parse(edit('a^n b^m c^(n+m)', 2, 'm+k+2'));
  assert.deepEqual(ast.vars, ['n', 'm', 'k'], 'משתנה חדש פשוט עובד');
  assert.equal(L.analyze(ast).cls, 'cf');
});

test('ביטוי שהמנוע לא מקבל: ההודעה של המפענח, והטקסט לא משתנה', () => {
  assert.match(edit('a^n b^n', 1, 'n-m'), /^ERR: המשתנה m מופיע רק עם מינוס/);
  assert.match(edit('a^n b^n', 1, 'n mod 2'), /^ERR: בחזקה אי אפשר mod/);
  assert.match(edit('a^n b^n', 1, ''), /^ERR: חסרה חזקה/);
  assert.match(edit('a^n b^n', 1, 'n+'), /^ERR: /);
});
