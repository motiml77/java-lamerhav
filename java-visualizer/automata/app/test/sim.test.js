'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const M = require('../js/model.js');
const S = require('../js/sim.js');
const { EXAMPLES, get } = require('../js/examples.js');

const load = (id) => M.deserialize(get(id)).machine;

test('אס"ד: מספר זוגי של a', () => {
  const m = load('even-a');
  assert.equal(S.run(m, '').result, 'accept');
  assert.equal(S.run(m, 'abba').result, 'accept');
  assert.equal(S.run(m, 'ab').result, 'reject');
  const tr = S.run(m, 'aba');
  assert.equal(tr.steps.length, 4, 'צעד התחלה + צעד לכל אות');
  assert.deepEqual(tr.steps.map((s) => s.pos), [0, 1, 2, 3]);
  assert.deepEqual(tr.steps.map((s) => M.stateName(m, s.active[0])), ['q0', 'q1', 'q1', 'q0']);
  assert.equal(tr.steps[1].edges.length, 1);
  assert.deepEqual(tr.acceptedStates, [m.start]);
});

test('אס"ד: מעבר חסר = דחייה עם סיבה', () => {
  const m = M.createMachine('dfa');
  const q0 = M.addState(m, { accepting: true });
  M.addTransition(m, q0.id, q0.id, [{ read: 'a' }]);
  const tr = S.run(m, 'aab');
  assert.equal(tr.result, 'reject');
  assert.equal(tr.reason.code, 'NO_TRANSITION');
  assert.equal(tr.reason.pos, 2);
  assert.match(tr.reason.text, /אין מעבר מ-q0 עבור b/);
});

test('בלי מצב התחלתי', () => {
  const m = M.createMachine('nfa');
  assert.equal(S.run(m, 'a').reason.code, 'NO_START');
});

test('אסל"ד: השלישית מהסוף היא a, כל הענפים הפעילים', () => {
  const m = load('third-last-a');
  for (const w of ['aaa', 'abb', 'babb']) assert.equal(S.run(m, w).result, 'accept', w);
  for (const w of ['', 'ab', 'bbb', 'abbb']) assert.equal(S.run(m, w).result, 'reject', w);
  const tr = S.run(m, 'aab');
  const names = (st) => st.active.map((id) => M.stateName(m, id)).sort();
  assert.deepEqual(names(tr.steps[1]), ['q0', 'q1']);
  assert.deepEqual(names(tr.steps[3]), ['q0', 'q2', 'q3']);
  assert.deepEqual(tr.acceptedStates.map((id) => M.stateName(m, id)), ['q3']);
});

test('אסל"ד: סגור ε', () => {
  const m = load('only-a-or-b');
  const cl = S.epsilonClosure(m, [m.start]);
  assert.deepEqual(cl.states.map((id) => M.stateName(m, id)).sort(), ['q0', 'q1', 'q2']);
  assert.equal(cl.edges.length, 2);
  assert.equal(S.run(m, '').result, 'accept');
  assert.equal(S.run(m, 'bbb').result, 'accept');
  const tr = S.run(m, 'ab');
  assert.equal(tr.result, 'reject');
  assert.equal(tr.reason.code, 'ALL_BRANCHES_DEAD');
});

test('אסל"ד: לולאת ε לא נתקעת', () => {
  const m = M.createMachine('nfa');
  const a = M.addState(m), b = M.addState(m, { accepting: true });
  M.addTransition(m, a.id, b.id, [{ read: '' }]);
  M.addTransition(m, b.id, a.id, [{ read: '' }]);
  assert.equal(S.run(m, '').result, 'accept');
});

test('אוטומט מחסנית: aⁿbⁿ', () => {
  const m = load('anbn');
  for (const w of ['', 'ab', 'aabb', 'aaabbb']) assert.equal(S.run(m, w).result, 'accept', w);
  for (const w of ['a', 'b', 'aab', 'abb', 'ba', 'abab']) assert.equal(S.run(m, w).result, 'reject', w);
  const tr = S.run(m, 'aabb');
  const stacks = tr.steps.map((s) => s.configs.map((c) => c.stack).join('|'));
  assert.equal(stacks[0], 'Z');
  assert.equal(stacks[2], 'AAZ', 'אחרי aa יש שני A מעל Z');
  assert.ok(tr.steps.every((s) => s.configs.length >= 1));
});

test('אוטומט מחסנית: קבלה במחסנית ריקה, ולא דטרמיניזם (פלינדרום זוגי)', () => {
  const m = M.createMachine('pda', { acceptBy: 'empty', stackStart: 'Z' });
  const p = M.addState(m), q = M.addState(m);
  M.addTransition(m, p.id, p.id, [
    { read: 'a', pop: '', push: 'a' }, { read: 'b', pop: '', push: 'b' },
  ]);
  M.addTransition(m, p.id, q.id, [{ read: '', pop: '', push: '' }]);
  M.addTransition(m, q.id, q.id, [
    { read: 'a', pop: 'a', push: '' }, { read: 'b', pop: 'b', push: '' }, { read: '', pop: 'Z', push: '' },
  ]);
  for (const w of ['', 'aa', 'abba', 'baab']) assert.equal(S.run(m, w).result, 'accept', w);
  for (const w of ['a', 'ab', 'aba']) assert.equal(S.run(m, w).result, 'reject', w);
  const tr = S.run(m, 'abba');
  assert.ok(tr.steps.some((s) => s.configs.length > 1), 'יש צעדים עם כמה ענפים');
});

test('אוטומט מחסנית: לולאת דחיפה ב-ε נעצרת במגבלה', () => {
  const m = M.createMachine('pda');
  const a = M.addState(m);
  M.addTransition(m, a.id, a.id, [{ read: '', pop: '', push: 'A' }]);
  const tr = S.run(m, 'a', { maxStack: 50 });
  assert.equal(tr.result, 'limit');
});

test('מכונת טיורינג: הוספת 1 בבינארי', () => {
  const m = load('binary-inc');
  const cases = { '1011': '1100', '111': '1000', '0': '1', '': '1' };
  for (const [w, out] of Object.entries(cases)) {
    const tr = S.run(m, w);
    assert.equal(tr.result, 'accept', w);
    assert.equal(tr.output, out, w);
  }
  const tr = S.run(m, '1011');
  assert.equal(tr.steps[0].tm.head, 0);
  assert.deepEqual(tr.steps[0].tm.tape.cells, ['1', '0', '1', '1']);
  assert.equal(tr.finalHead, 0, 'הראש חוזר לתחילת המספר');
});

test('מכונת טיורינג: עצירה במצב לא מקבל, ומגבלת צעדים', () => {
  const m = M.createMachine('tm');
  const a = M.addState(m), b = M.addState(m, { accepting: true });
  M.addTransition(m, a.id, a.id, [{ read: '1', write: '1', move: 'R' }]);
  const tr = S.run(m, '10');
  assert.equal(tr.result, 'reject');
  assert.equal(tr.reason.code, 'HALT_NOT_ACCEPTING');
  M.addTransition(m, a.id, a.id, [{ read: '_', write: '_', move: 'R' }]);
  assert.equal(S.run(m, '1', { maxSteps: 100 }).result, 'limit');
  assert.equal(b.accepting, true);
});

test('runTests על כל הדוגמאות: כל הבדיקות עוברות', () => {
  for (const ex of EXAMPLES) {
    const m = M.deserialize(ex).machine;
    const res = S.runTests(m, m.tests);
    for (const r of res) assert.ok(r.pass, ex.id + ' "' + r.word + '"');
    const issues = M.validate(m).filter((i) => i.level === 'error');
    assert.deepEqual(issues, [], ex.id + ' בלי שגיאות');
  }
});
