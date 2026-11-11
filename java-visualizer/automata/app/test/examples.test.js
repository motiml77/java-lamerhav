'use strict';
/* הדוגמאות המוכנות: חמש לכל סוג, לכל אחת נוסחה, והמכונה מקבלת בדיוק את השפה של הנוסחה שלה. */
const test = require('node:test');
const assert = require('node:assert/strict');
const M = require('../js/model.js');
const S = require('../js/sim.js');
const L = require('../js/lang/index.js');
const { EXAMPLES } = require('../js/examples.js');

const withLang = EXAMPLES.filter((e) => e.language);
const accepts = (m, w) => {
  const r = S.run(m, w, { record: false }).result;
  assert.ok(r === 'accept' || r === 'reject', m.id + ': ההרצה של "' + w + '" לא הוכרעה (' + r + ')');
  return r === 'accept';
};

test('יש בדיוק חמש דוגמאות עם נוסחה לכל סוג מכונה, ולכל אחת תיאור ומילים', () => {
  for (const type of M.TYPES) assert.equal(withLang.filter((e) => e.type === type).length, 5, type);
  assert.equal(new Set(EXAMPLES.map((e) => e.id)).size, EXAMPLES.length, 'מזהים ייחודיים');
  for (const e of withLang) {
    assert.ok(e.language.desc && !/[—!]/.test(e.language.desc), e.id + ': תיאור');
    assert.ok(e.tests.length >= 6 && e.tests.length <= 9, e.id + ': 6 עד 9 מילים');
    assert.ok(e.tests.some((t) => t.expect), e.id + ': יש מילה שמתקבלת');
    if (e.id !== 'binary-inc') assert.ok(e.tests.filter((t) => !t.expect).length >= 2, e.id + ': יש מילים שנדחות');
    assert.equal(new Set(e.tests.map((t) => t.word)).size, e.tests.length, e.id + ': אין מילה כפולה');
    assert.deepEqual(M.validate(M.deserialize(e).machine).filter((i) => i.level === 'error'), [], e.id + ': בלי שגיאות');
  }
});

test('התוצאה הצפויה של כל מילה באה מהנוסחה, והמכונה מסכימה איתה', () => {
  for (const e of withLang) {
    const m = M.deserialize(e).machine;
    assert.equal(m.language.text, e.language.text);
    assert.equal(m.language.desc, e.language.desc);
    const ast = L.parse(e.language.text, { alphabet: M.inputAlphabet(m) });
    for (const t of m.tests) {
      assert.equal(t.expect, L.accepts(ast, t.word), e.id + ' "' + t.word + '": הצפוי לפי הנוסחה');
      assert.equal(accepts(m, t.word), t.expect, e.id + ' "' + t.word + '": המכונה');
    }
  }
});

/* אורך הבדיקה המלאה: 10 לאלפבית של שתי אותיות. לשלוש אותיות 9 (כ-30,000 מילים), ולאות אחת 40. */
const MAX_LEN = { 1: 40, 2: 10, 3: 9 };
for (const e of withLang) {
  test('המכונה שקולה לנוסחה על כל המילים הקצרות: ' + e.id + '  (' + e.language.text + ')', () => {
    const m = M.deserialize(e).machine;
    const ast = L.parse(e.language.text, { alphabet: M.inputAlphabet(m) });
    assert.deepEqual(ast.alphabet.slice().sort(), e.alphabet.slice().sort(), 'האלפבית של הנוסחה הוא האלפבית של המכונה');
    const maxLen = MAX_LEN[ast.alphabet.length];
    let n = 0, ins = 0;
    for (const w of L.words(ast.alphabet, maxLen)) {
      const want = L.accepts(ast, w);
      assert.equal(accepts(m, w), want, e.id + ': המילה "' + w + '" ' + (want ? 'בשפה אבל נדחתה' : 'לא בשפה אבל התקבלה'));
      n++; if (want) ins++;
    }
    assert.ok(n >= 41 && ins >= 3, 'נבדקו ' + n + ' מילים, ' + ins + ' מהן בשפה');
  });
}

test('הוספת 1 לבינארי: על הסרט נשאר המספר ועוד 1', () => {
  const m = M.deserialize(EXAMPLES.find((e) => e.id === 'binary-inc')).machine;
  for (const w of L.words(['0', '1'], 9)) {
    if (!w) continue;
    const r = S.run(m, w);
    assert.equal(r.result, 'accept', w);
    assert.equal(parseInt(r.output, 2), parseInt(w, 2) + 1, w + ' → ' + r.output);
  }
});
