'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const L = require('../js/lang/index.js');
const M = require('../js/model.js');
const S = require('../js/sim.js');
const { get } = require('../js/examples.js');

const P = (s) => L.parse(s);
const ex = (id) => M.deserialize(get(id)).machine;
const accepts = (m, w) => S.run(m, w).result === 'accept';

test('בדיקה מדויקת: אוטומט נכון מקבל "שווה" (הוכחה), גם אסל"ד מול נוסחה רגולרית', () => {
  let r = L.check(ex('even-a'), P('w ∈ {a,b}* | #a(w) זוגי'));
  assert.deepEqual([r.exact, r.ok, r.missing, r.extra], [true, true, [], []]);
  r = L.check(ex('ends-ab'), P('(a|b)*ab'));
  assert.deepEqual([r.exact, r.ok], [true, true]);
  r = L.check(ex('ends-ab'), P('w ∈ {a,b}* | מסתיימת ב-ab'));
  assert.ok(r.exact && r.ok);
  r = L.check(ex('only-a-or-b'), P('a* ∪ b*'));
  assert.ok(r.exact && r.ok, 'אסל"ד עם מעברי ε');
});

test('בדיקה מדויקת: דוגמאות נגדיות, הקצרות ביותר קודם, וכל אחת באמת מבדילה', () => {
  const ast = P('w ∈ {a,b}* | #a(w) אי-זוגי');
  const m = ex('even-a');
  const r = L.check(m, ast);
  assert.equal(r.ok, false);
  assert.equal(r.missing[0], 'a');
  assert.equal(r.extra[0], '');
  assert.equal(r.missing.length, 5); assert.equal(r.extra.length, 5);
  for (const w of r.missing) assert.ok(L.accepts(ast, w) && !accepts(m, w), w);
  for (const w of r.extra) assert.ok(!L.accepts(ast, w) && accepts(m, w), w);
  const sorted = (a) => a.every((w, i) => i === 0 || a[i - 1].length < w.length || (a[i - 1].length === w.length && a[i - 1] < w));
  assert.ok(sorted(r.missing) && sorted(r.extra), 'מהקצרה לארוכה, ובסדר האלפבית');
  // הקצרה ביותר באמת: אין מילה קצרה יותר שמבדילה
  for (const w of L.words(['a', 'b'], r.missing[0].length - 1)) assert.equal(L.accepts(ast, w) && !accepts(m, w), false);
});

test('בדיקה מדויקת מוצאת הבדל שמתגלה רק במילה ארוכה', () => {
  // "מכילה aaaaaaa" מול אוטומט שמקבל הכול חוץ מהמילים הקצרות מ-7 שאין בהן... : אוטומט שמקבל כל מילה באורך 7 ומעלה
  const m = M.createMachine('dfa', { alphabet: ['a', 'b'] });
  const q = [];
  for (let i = 0; i <= 7; i++) q.push(M.addState(m, { accepting: i === 7 }));
  for (let i = 0; i < 7; i++) M.addTransition(m, q[i].id, q[i + 1].id, [{ read: 'a' }, { read: 'b' }]);
  M.addTransition(m, q[7].id, q[7].id, [{ read: 'a' }, { read: 'b' }]);
  const r = L.check(m, P('w ∈ {a,b}* | מכילה aaaaaaa'));
  assert.equal(r.exact, true);
  assert.equal(r.ok, false);
  assert.deepEqual(r.missing, []);
  assert.equal(r.extra[0], 'aaaaaab');
});

test('אות שהאוטומט מכיר והשפה לא: מילה איתה נחשבת טעות אם היא מתקבלת', () => {
  const m = ex('even-a');
  M.addTransition(m, m.states[0].id, m.states[0].id, [{ read: 'c' }]);
  m.alphabet.push('c');
  const r = L.check(m, P('w ∈ {a,b}* | #a(w) זוגי'));
  assert.equal(r.ok, false);
  assert.equal(r.extra[0], 'c');
});

test('בדיקה חסומה: אוטומט מחסנית ומכונת טיורינג מול הנוסחה שלהם', () => {
  let r = L.check(ex('anbn'), P('a^n b^n'));
  assert.deepEqual([r.exact, r.ok], [false, true]);
  assert.ok(r.count > 1000 && r.maxLen >= 10, 'נבדקו ' + r.count + ' מילים עד אורך ' + r.maxLen);
  r = L.check(ex('anbn'), P('a^n b^n | n >= 1'));
  assert.equal(r.ok, false);
  assert.deepEqual(r.extra, ['']);
  assert.deepEqual(r.missing, []);
  r = L.check(ex('anbn'), P('a^n b^m | n <= m'));
  assert.equal(r.missing[0], 'b');
  assert.ok(r.maxLen <= 2, 'עוצרים אורך אחד אחרי הטעות הראשונה');
  const tm = L.build(P('a^n b^n c^n')).machine;
  r = L.check(tm, P('a^n b^n c^n'));
  assert.ok(!r.exact && r.ok);
  r = L.check(tm, P('a^n b^n c^n | n >= 1'));
  assert.deepEqual(r.extra, ['']);
});

test('שפה לא רגולרית מול אס"ד: בדיקה חסומה, לא הוכחה', () => {
  const r = L.check(ex('even-a'), P('a^n b^n'));
  assert.equal(r.exact, false);
  assert.equal(r.ok, false);
});

test('בדיקה בחלקים (checker.step) נותנת אותה תוצאה', () => {
  const c = L.checker(ex('anbn'), P('a^n b^n | n >= 1'));
  let rounds = 0;
  while (!c.step(7)) rounds++;
  assert.ok(rounds >= 0);
  assert.deepEqual(c.result.extra, ['']);
});

test('רמז של שורה אחת, בלי לגלות את הפתרון', () => {
  const m = ex('even-a');
  assert.match(L.hint(L.check(m, P('w ∈ {a,b}* | #a(w) זוגי וגם |w| >= 1')), m), /האוטומט שלך מקבל את המילה הריקה, אבל היא לא בשפה/);
  assert.match(L.hint(L.check(ex('ends-ab'), P('w ∈ {a,b}* | מסתיימת ב-b')), ex('ends-ab')), /המילה b בשפה, אבל האוטומט שלך דוחה אותה/);
  assert.equal(L.hint(L.check(m, P('w ∈ {a,b}* | #a(w) זוגי')), m), '');
});

test('המכונה שנבנית מהשפה עוברת את הבדיקה מול אותה שפה', () => {
  for (const t of L.TEMPLATES) {
    if (!t.builds) continue;
    const ast = P(t.text);
    const r = L.check(L.build(ast).machine, ast);
    assert.ok(r.ok, t.name);
    assert.equal(r.exact, t.builds === 'dfa', t.name);
  }
});
