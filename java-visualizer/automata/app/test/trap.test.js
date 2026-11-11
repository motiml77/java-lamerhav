'use strict';
/* מצבי מלכודת לאפשרות התצוגה "הצג מצב מלכודת" (validate.js: trapStates). */
const test = require('node:test');
const assert = require('node:assert/strict');
const M = require('../js/model.js');
require('../js/validate.js');
const L = require('../js/lang/index.js');
const { get } = require('../js/examples.js');

const names = (m) => Array.from(M.trapStates(m)).map((id) => M.stateName(m, id)).sort();

test('המלכודת של אס"ד שנבנה מהשפה, ומצב מת בלי השם', () => {
  const m = L.machineFor(L.parse('a^n b^n | 1≤n≤3'));
  assert.deepEqual(names(m), ['מלכודת']);
  // "לא מכילה bb": q2 לא מקבל, וכל המעברים שלו חוזרים אליו על a ועל b
  assert.deepEqual(names(M.deserialize(get('no-bb')).machine), ['q2']);
});

test('מה שלא מלכודת: מצב מקבל, מצב התחלתי, מצב שיוצא ממנו מעבר, לולאה שלא מכסה את כל Σ, מכונת טיורינג', () => {
  assert.deepEqual(names(M.deserialize(get('even-a')).machine), []);
  assert.deepEqual(names(M.deserialize(get('has-aba')).machine), [], 'q3 מקבל');
  const m = M.createMachine('dfa', { alphabet: ['a', 'b'] });
  const s0 = M.addState(m), s1 = M.addState(m), s2 = M.addState(m);
  M.addTransition(m, s0.id, s1.id, [{ read: 'a' }]);
  M.addTransition(m, s1.id, s1.id, [{ read: 'a' }]);                 // רק a: לא מלכודת
  M.addTransition(m, s0.id, s2.id, [{ read: 'b' }]);
  M.addTransition(m, s2.id, s2.id, [{ read: 'a' }, { read: 'b' }]);  // מת
  assert.deepEqual(Array.from(M.trapStates(m)), [s2.id]);
  m.start = s2.id;
  assert.deepEqual(Array.from(M.trapStates(m)), [], 'מצב התחלתי לא מוסתר');
  assert.equal(M.trapStates(M.deserialize(get('binary-inc')).machine).size, 0);
});

test('אוטומט מחסנית: רק מצב מת לפי אותו כלל', () => {
  const m = M.createMachine('pda', { alphabet: ['a', 'b'] });
  const s0 = M.addState(m), d = M.addState(m);
  M.addTransition(m, s0.id, d.id, [{ read: 'b', pop: '', push: '' }]);
  M.addTransition(m, d.id, d.id, [{ read: 'a', pop: '', push: '' }, { read: 'b', pop: '', push: '' }]);
  assert.deepEqual(Array.from(M.trapStates(m)), [d.id]);
  assert.equal(M.trapStates(L.machineFor(L.parse('a^n b^n'))).size, 0);
});
