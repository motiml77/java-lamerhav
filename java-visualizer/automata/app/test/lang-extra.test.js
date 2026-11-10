'use strict';
/* התוספות לכתיב (lang/extra.js): תבנית של מילה (wwᴿ, ww, w#w), "מאוזנת", וחזקה של 2 בתוך חזקה. */
const test = require('node:test');
const assert = require('node:assert/strict');
const L = require('../js/lang/index.js');
const S = require('../js/sim.js');

const P = (s, o) => L.parse(s, o);
const yes = (f, ws) => ws.forEach((w) => assert.equal(L.accepts(P(f), w), true, f + ' צריך לקבל "' + w + '"'));
const no = (f, ws) => ws.forEach((w) => assert.equal(L.accepts(P(f), w), false, f + ' צריך לדחות "' + w + '"'));
const err = (s) => { try { P(s); } catch (e) { assert.ok(e instanceof L.LangError, s + ': ' + e.message); return e; } assert.fail('ציפיתי לשגיאה: ' + s); };

test('תבנית של מילה: פענוח, כתיבה יפה ושייכות', () => {
  for (const f of ['w w^R', 'wwᴿ', 'w wᴿ | w ∈ {a,b}*', 'L = { w w^R | w ∈ {a,b}* }']) {
    const a = P(f);
    assert.equal(a.kind, 'pattern', f);
    assert.deepEqual(a.items, [{ w: true }, { w: true, rev: true }], f);
    assert.equal(L.pretty(a).text, 'L = { wwᴿ | w ∈ {a,b}* }');
  }
  assert.equal(L.pretty(P('w w^R')).html, 'L = { ww<sup>R</sup> | w ∈ {a,b}* }');
  yes('w w^R', ['', 'aa', 'abba', 'baab', 'abaaba']); no('w w^R', ['a', 'ab', 'aba', 'abab', 'abc']);
  yes('ww', ['', 'aa', 'abab', 'abaaba']); no('ww', ['a', 'ab', 'abba', 'aaa']);
  // # בין שני w היא אות, לא "מספר ה-w במילה"
  for (const f of ['w#w', 'w # w', 'w#w | w ∈ {a,b}*']) {
    assert.deepEqual(P(f).items, [{ w: true }, { lit: '#' }, { w: true }], f);
    assert.deepEqual(P(f).alphabet, ['a', 'b', '#'], f);
    yes(f, ['#', 'a#a', 'ab#ab']); no(f, ['', 'a', 'a#b', 'ab#ba', 'a#a#', '##', 'abab']);
  }
  yes('w c w^R', ['c', 'aca', 'abcba']); no('w c w^R', ['', 'acb', 'abcab', 'cc']);
  yes('w#w | w ∈ {0,1}*', ['#', '01#01']); no('w#w | w ∈ {0,1}*', ['a#a', '01#10']);
  // האלפבית של המכונה הוא ברירת המחדל, בלי האותיות שמופיעות בתבנית עצמה
  assert.deepEqual(P('w#w', { alphabet: ['a', 'b', '#'] }).base, ['a', 'b']);
  assert.deepEqual(P('ww', { alphabet: ['0', '1'] }).base, ['0', '1']);
  // w ∈ ... בהתחלה נשאר "תכונה של מילה"
  assert.equal(P('w ∈ {a,b}* | |w| > 2').kind, 'prop');
  assert.match(err('w w^R | n > 2').message, /אחרי התבנית אפשר לכתוב רק מאיזה אלפבית w/);
});

test('"מאוזנת": סוגריים כאותיות של האלפבית', () => {
  const a = P('w ∈ {(,)}* | מאוזנת');
  assert.equal(a.kind, 'prop');
  assert.deepEqual(a.alphabet, ['(', ')']);
  assert.deepEqual([a.cond.open, a.cond.close], ['(', ')']);
  assert.equal(L.pretty(a).text, 'L = { w ∈ {(,)}* | מאוזנת }');
  yes('w ∈ {(,)}* | מאוזנת', ['', '()', '(())', '()()', '(()())']); no('w ∈ {(,)}* | מאוזנת', ['(', ')', ')(', '(()', '())(']);
  assert.deepEqual(P('סוגריים מאוזנים').alphabet, ['(', ')']);
  // בלי סוגריים באלפבית: האות הראשונה פותחת והשנייה סוגרת
  yes('w ∈ {a,b}* | מאוזנת', ['', 'ab', 'aabb', 'abab']); no('w ∈ {a,b}* | מאוזנת', ['ba', 'aab']);
  yes('w ∈ {(,)}* | מאוזנת וגם |w| > 2', ['(())']); no('w ∈ {(,)}* | מאוזנת וגם |w| > 2', ['()']);
});

test('חזקה של 2 בתוך חזקה: a^(2^n)', () => {
  for (const f of ['a^(2^n)', 'a^2^n', 'a^(2ⁿ)']) {
    const a = P(f);
    assert.deepEqual(a.blocks[0].e, { c: 1, v: 'n', d: 0, pow: 2 }, f);
    assert.equal(L.pretty(a).text, 'L = { a^(2ⁿ) }');
    assert.equal(L.pretty(a).html, 'L = { a<sup>2<sup>n</sup></sup> }');
    yes(f, ['a', 'aa', 'aaaa', 'a'.repeat(16)]); no(f, ['', 'aaa', 'aaaaa', 'a'.repeat(12)]);
  }
  yes('a^(3^n) b', ['ab', 'aaab', 'a'.repeat(9) + 'b']); no('a^(3^n) b', ['b', 'aab']);
  assert.deepEqual(P('a^(2n+1)').blocks[0].e, { c: 2, v: 'n', d: 1 }, 'החזקה הרגילה לא השתנתה');
  assert.match(err('a^(1^n)').message, /הבסיס של החזקה הפנימית/);
});

test('סיווג: רק המקרים הקלאסיים, וכל השאר "לא הצלחתי לסווג"', () => {
  const table = {
    'w w^R': 'cf', 'w c w^R': 'cf', 'w#w^R': 'cf', 'w ∈ {(,)}* | מאוזנת': 'cf', 'w ∈ {a,b}* | מאוזנת': 'cf',
    'ww': 'noncf', 'w#w': 'noncf', 'a^(2^n)': 'noncf', '(ab)^(2^n)': 'noncf',
    'w w^R | w ∈ {a}*': 'unknown', 'ww | w ∈ {a}*': 'unknown', 'www': 'unknown', 'w a w': 'unknown',
    'a^(2^n) b^n': 'unknown', 'a^(2^n) | n >= 1': 'unknown', 'w ∈ {(,)}* | מאוזנת וגם |w| > 2': 'unknown', 'w ∈ {(,)}* | לא מאוזנת': 'unknown',
  };
  for (const f in table) assert.equal(L.analyze(P(f)).cls, table[f], f);
});

test('בנייה: אוטומט מחסנית ל-wwᴿ, ל-w c wᴿ, לפלינדרום ולסוגריים מאוזנים, שקול לנוסחה', () => {
  for (const f of ['w w^R', 'w c w^R', 'w#w^R | w ∈ {0,1}*', 'w ∈ {a,b}* | פלינדרום', 'w ∈ {(,)}* | מאוזנת', 'w ∈ {a,b,c}* | מאוזנת']) {
    const ast = P(f), r = L.build(ast);
    assert.ok(r.ok && r.kind === 'pda', f + ': ' + r.reason);
    assert.equal(r.machine.language.text, f);
    const maxLen = ast.alphabet.length === 2 ? 10 : 7;
    for (const w of L.words(ast.alphabet, maxLen)) assert.equal(S.run(r.machine, w, { record: false }).result === 'accept', L.accepts(ast, w), f + ' "' + w + '"');
  }
  for (const f of ['ww', 'w#w', 'a^(2^n)']) assert.match(L.build(P(f)).reason, /עוד לא נתמכת/, f);
});
