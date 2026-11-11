'use strict';
/* המשימה (סבב 14): המודל, "השתנה מאז הבדיקה", מעבר בין מצבים, משימה נעולה, קובץ משימה, והקבוצות של רשימת התבניות. */
const test = require('node:test');
const assert = require('node:assert/strict');
const M = require('../js/model.js');
require('../js/validate.js');
const L = require('../js/lang/index.js');
const T = require('../js/task.js');

/** אס"ד למספר זוגי של a */
function evenA() {
  const m = M.createMachine('dfa', { alphabet: ['a', 'b'] });
  M.addState(m, { id: 'q0', name: 'q0', x: 0, y: 0, accepting: true });
  M.addState(m, { id: 'q1', name: 'q1', x: 100, y: 0 });
  m.start = 'q0';
  for (const [f, t, l] of [['q0', 'q1', 'a'], ['q1', 'q0', 'a'], ['q0', 'q0', 'b'], ['q1', 'q1', 'b']]) M.addTransition(m, f, t, M.parseLabels('dfa', l).labels);
  return m;
}
const F = 'w ∈ {a,b}* | #a(w) זוגי';

test('בלי נוסחה אין משימה (ציור חופשי); ברירות המחדל של המצב', () => {
  const m = evenA();
  assert.equal(T.of(m), null);
  assert.equal(T.status(m), 'none');
  assert.equal(T.pill(m), null);
  m.language = { text: '  ' };
  assert.equal(T.of(m), null);
  m.language = { text: F };
  assert.equal(T.of(m).mode, 'auto', 'נוסחה ישנה בלי mode: התוכנה בונה');
  m.language = { text: F, locked: true };
  assert.equal(T.of(m).mode, 'practice', 'קובץ תרגיל ישן (נעול): אני בונה');
});

test('בדיקה נשמרת, ו"השתנה מאז הבדיקה" בכל עריכה של המכונה או של הנוסחה; ביטול מחזיר', () => {
  const m = evenA();
  T.attach(m, F, 'practice');
  assert.deepEqual(T.pill(m), { cls: 'none', text: 'עוד לא נבדק' });
  T.setCheck(m, L.checkExact(m, L.parse(F)));
  assert.equal(T.status(m), 'ok');
  assert.deepEqual(T.pill(m), { cls: 'ok', text: 'נכון ✓ הוכחה' });
  const snap = JSON.stringify(m.states);
  m.states[0].accepting = false;
  assert.equal(T.status(m), 'stale');
  assert.deepEqual(T.pill(m), { cls: 'stale', text: 'השתנה מאז הבדיקה' });
  m.states = JSON.parse(snap);
  assert.equal(T.status(m), 'ok', 'אותה מכונה שוב: אותה בדיקה');
  m.view = { x: 5, y: 9, zoom: 2 };
  m.states[0].x = 400;
  assert.equal(T.status(m), 'ok', 'הזזה ותצוגה לא משנות את התוצאה');
  m.language.text = 'w ∈ {a,b}* | #a(w) אי-זוגי';
  assert.equal(T.status(m), 'stale', 'נוסחה אחרת');
});

test('תוצאה שגויה: עד 5 מילים בכל קבוצה, והמספר בסטטוס', () => {
  const m = evenA();
  T.attach(m, 'w ∈ {a,b}* | #b(w) זוגי', 'practice');
  const c = T.setCheck(m, L.checkExact(m, L.parse(m.language.text)));
  assert.equal(c.status, 'fail');
  assert.equal(c.kind, 'proof');
  assert.ok(c.words.shouldAccept.length <= 5 && c.words.shouldReject.length <= 5);
  assert.equal(c.words.shouldAccept[0], 'a', 'a בשפה (#b זוגי), והמכונה דוחה');
  assert.equal(c.words.shouldReject[0], 'b');
  assert.equal(T.pill(m).text, 'לא נכון: ' + c.total + ' מילים');
  const b = T.record(m, { ok: true, exact: false, missing: [], extra: [], maxLen: 7, count: 255 });
  assert.deepEqual([b.kind, b.checkedUpTo, b.count], ['bounded', 7, 255]);
  m.language.check = b;
  assert.equal(T.pill(m).text, 'נכון ✓ נבדק עד אורך 7');
});

test('מצבים: התוכנה בונה / אני בונה, החלפה, סיום (המכונה נשארת)', () => {
  const m = evenA();
  T.attach(m, F, 'auto', { desc: 'זוגי' });
  T.setCheck(m, L.checkExact(m, L.parse(F)));
  assert.deepEqual(T.pill(m), { cls: 'ok', text: 'נבדק מול הנוסחה ✓' });
  m.states[0].accepting = false;
  assert.equal(T.pill(m).text, 'השתנה מאז הבנייה');
  m.states[0].accepting = true;
  T.setMode(m, 'practice');
  assert.equal(T.of(m).mode, 'practice');
  assert.equal(T.status(m), 'ok', 'הבדיקה נשארת: אותה מכונה');
  T.setMode(m, 'nonsense');
  assert.equal(T.of(m).mode, 'practice');
  T.attach(m, 'a^n', 'auto');
  assert.equal(T.status(m), 'none', 'נוסחה חדשה: בלי בדיקה ישנה');
  T.finish(m);
  assert.equal(T.of(m), null);
  assert.equal(m.states.length, 2);
});

test('משימה נעולה, ושמירה וטעינה של מכונה עם משימה (mode ו-check נשמרים)', () => {
  const m = evenA();
  m.language = { text: F, locked: true, mode: 'practice', allowBuild: false };
  T.setCheck(m, L.checkExact(m, L.parse(F)));
  const back = M.deserialize(M.serialize(m)).machine;
  assert.deepEqual([T.of(back).mode, T.of(back).locked, T.status(back)], ['practice', true, 'ok']);
});

test('קובץ משימה: הנוסחה, הסוג והא"ב, בלי המכונה; נפתח כמשימה נעולה', () => {
  const m = evenA();
  T.attach(m, F, 'auto', { desc: 'מספר זוגי של a' });
  const json = T.toFile(m), o = JSON.parse(json);
  assert.deepEqual(o, { format: 'dsviz-automata-task', version: 1, type: 'dfa', alphabet: ['a', 'b'], formula: F, mode: 'practice', locked: true, desc: 'מספר זוגי של a' });
  assert.ok(!('states' in o) && !('transitions' in o));
  assert.equal(T.isFile(o), true);
  assert.equal(T.isFile(JSON.parse(M.serialize(m))), false, 'קובץ מכונה רגיל');
  const f = T.fromFile(o);
  assert.deepEqual(f, { type: 'dfa', alphabet: ['a', 'b'], language: { text: F, allowBuild: false, locked: true, mode: 'practice', desc: 'מספר זוגי של a' } });
  assert.throws(() => T.fromFile({ format: 'dsviz-automata-task', formula: '' }), /אין נוסחה/);
  assert.equal(T.toFile(evenA()), null, 'אין משימה: אין קובץ');
});

test('סוג המכונה לפי הסיווג, ורשימת התבניות בקבוצות', () => {
  assert.deepEqual(['regular', 'cf', 'cfany', 'noncf', 'unknown'].map(T.typeFor), ['dfa', 'pda', 'pda', 'tm', 'dfa']);
  const g = L.templateGroups();
  assert.deepEqual(g.map((x) => x.title), ['רגולריות', 'חסרות הקשר', 'עם תנאים (≥, <, ≠, %)', 'איחוד ושרשור', 'לא חסרות הקשר / מכונת טיורינג']);
  assert.equal(g.reduce((s, x) => s + x.items.length, 0), L.TEMPLATES.length, 'כל תבנית בדיוק פעם אחת');
  assert.ok(L.TEMPLATES.every((t) => t.he && !/[—!]/.test(t.he)));
  assert.ok(g[2].items.every((t) => /[|:]/.test(t.text)));
});
