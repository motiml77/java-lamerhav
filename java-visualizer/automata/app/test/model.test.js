'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const M = require('../js/model.js');

test('המצב הראשון הוא ההתחלתי, ושמות q0, q1...', () => {
  const m = M.createMachine('dfa');
  const a = M.addState(m, { x: 0, y: 0 });
  const b = M.addState(m, { x: 100, y: 0 });
  assert.equal(m.start, a.id);
  assert.equal(a.name, 'q0');
  assert.equal(b.name, 'q1');
  M.removeStates(m, [a.id]);
  assert.equal(m.start, b.id, 'מחיקת ההתחלתי מעבירה את ההתחלה למצב הבא');
  assert.equal(M.nextStateName(m), 'q0', 'השם הפנוי הקטן ביותר');
});

test('מחיקת מצב מוחקת את המעברים שלו', () => {
  const m = M.createMachine('nfa');
  const a = M.addState(m), b = M.addState(m);
  M.addTransition(m, a.id, b.id, [{ read: 'a' }]);
  M.addTransition(m, b.id, b.id, [{ read: 'b' }]);
  M.removeStates(m, [b.id]);
  assert.equal(m.transitions.length, 0);
});

test('מעבר שני בין אותו זוג מתמזג לאותה קשת, בלי כפילויות', () => {
  const m = M.createMachine('dfa');
  const a = M.addState(m), b = M.addState(m);
  const t1 = M.addTransition(m, a.id, b.id, [{ read: 'a' }]);
  const t2 = M.addTransition(m, a.id, b.id, [{ read: 'b' }, { read: 'a' }]);
  assert.equal(t1, t2);
  assert.deepEqual(t1.labels.map((l) => l.read), ['a', 'b']);
});

test('שינוי שם: ייחודי ולא ריק', () => {
  const m = M.createMachine('dfa');
  const a = M.addState(m), b = M.addState(m);
  assert.equal(M.renameState(m, a.id, 'התחלה'), null);
  assert.match(M.renameState(m, b.id, 'התחלה'), /כבר יש/);
  assert.match(M.renameState(m, b.id, '  '), /לפחות/);
});

test('פענוח תוויות FA', () => {
  assert.deepEqual(M.parseLabels('dfa', 'a, b c').labels.map((l) => l.read), ['a', 'b', 'c']);
  assert.deepEqual(M.parseLabels('nfa', 'ε, a').labels, [{ read: '' }, { read: 'a' }]);
  assert.deepEqual(M.parseLabels('nfa', 'eps').labels, [{ read: '' }]);
  assert.match(M.parseLabels('dfa', 'ε').errors[0], /ε/);
  assert.match(M.parseLabels('dfa', 'ab').errors[0], /תו אחד/);
  assert.match(M.parseLabels('dfa', '').errors[0], /לפחות/);
});

test('פענוח תוויות PDA ו-TM', () => {
  assert.deepEqual(M.parseLabels('pda', 'a,Z/AZ\nε,Z→ε').labels, [
    { read: 'a', pop: 'Z', push: 'AZ' }, { read: '', pop: 'Z', push: '' },
  ]);
  assert.deepEqual(M.parseLabels('pda', 'b, A / ε | a,ε/A').labels, [
    { read: 'b', pop: 'A', push: '' }, { read: 'a', pop: '', push: 'A' },
  ]);
  assert.ok(M.parseLabels('pda', 'aZAZ').errors.length);
  assert.deepEqual(M.parseLabels('tm', '1/0,L | _→1,R\n0/0S').labels, [
    { read: '1', write: '0', move: 'L' }, { read: '_', write: '1', move: 'R' }, { read: '0', write: '0', move: 'S' },
  ]);
  assert.ok(M.parseLabels('tm', '1/0').errors.length);
});

test('הצגת תוויות', () => {
  assert.equal(M.formatLabels('nfa', [{ read: 'a' }, { read: '' }]), 'a, ε');
  assert.equal(M.formatLabel('pda', { read: '', pop: 'Z', push: '' }), 'ε,Z/ε');
  assert.equal(M.formatLabel('tm', { read: '1', write: '0', move: 'L' }), '1/0,L');
  // סבב: format -> parse מחזיר אותו דבר
  const pda = [{ read: 'a', pop: 'A', push: 'AA' }, { read: '', pop: 'Z', push: 'Z' }];
  assert.deepEqual(M.parseLabels('pda', M.formatLabels('pda', pda)).labels, pda);
});

test('בדיקות תקינות של אס"ד', () => {
  const m = M.createMachine('dfa', { alphabet: ['a', 'b'] });
  assert.equal(M.validate(m)[0].code, 'NO_STATES');
  const q0 = M.addState(m), q1 = M.addState(m);
  M.addTransition(m, q0.id, q1.id, [{ read: 'a' }]);
  M.addTransition(m, q0.id, q0.id, [{ read: 'a' }]);
  M.addTransition(m, q1.id, q1.id, [{ read: 'c' }]);
  const codes = M.validate(m).map((i) => i.code);
  assert.ok(codes.includes('NONDETERMINISTIC'));
  assert.ok(codes.includes('MISSING_TRANSITIONS'));
  assert.ok(codes.includes('SYMBOL_NOT_IN_ALPHABET'));
  assert.ok(codes.includes('NO_ACCEPTING'));
  const nd = M.validate(m).find((i) => i.code === 'NONDETERMINISTIC');
  assert.equal(nd.level, 'error');
  assert.deepEqual(nd.states, [q0.id]);
  assert.equal(nd.transitions.length, 2);
  assert.equal(M.validate(m).find((i) => i.code === 'MISSING_TRANSITIONS').level, 'warning');
});

test('ε באס"ד, מצב לא ישיג, אוטומט מחסנית עם סמל מחוץ ל-Γ', () => {
  const m = M.createMachine('dfa');
  const q0 = M.addState(m), q1 = M.addState(m, { accepting: true });
  m.transitions.push({ id: 'x', from: q0.id, to: q0.id, labels: [{ read: '' }] });
  const codes = M.validate(m).map((i) => i.code);
  assert.ok(codes.includes('EPSILON_IN_DFA'));
  assert.ok(codes.includes('UNREACHABLE'));
  const p = M.createMachine('pda', { stackAlphabet: ['Z', 'A'] });
  const s = M.addState(p, { accepting: true });
  M.addTransition(p, s.id, s.id, [{ read: 'a', pop: 'Z', push: 'BZ' }]);
  assert.ok(M.validate(p).some((i) => i.code === 'STACK_SYMBOL_NOT_IN_GAMMA'));
});

test('בדיקת מילה מול Σ', () => {
  const m = M.createMachine('dfa', { alphabet: ['a', 'b'] });
  assert.equal(M.checkWord(m, 'abba'), null);
  const r = M.checkWord(m, 'abca');
  assert.equal(r.index, 2);
  assert.match(r.text, /אינה באלפבית/);
});

test('שמירה וטעינה: סבב מלא, וקבצים פגומים', () => {
  const m = M.createMachine('pda', { name: 'בדיקה' });
  const a = M.addState(m, { x: 10, y: 20 });
  M.addTransition(m, a.id, a.id, [{ read: 'a', pop: 'Z', push: 'AZ' }]);
  m.tests.push({ word: 'a', expect: false });
  const { machine, errors } = M.deserialize(M.serialize(m));
  assert.deepEqual(errors, []);
  assert.deepEqual(machine, m);
  assert.match(M.deserialize('{oops').errors[0], /JSON/);
  assert.match(M.deserialize({ format: 'other', type: 'dfa' }).errors[0], /DSViz/);
  assert.match(M.deserialize({ type: 'xyz' }).errors[0], /לא מוכר/);
  const broken = M.deserialize({ type: 'dfa', states: [{ id: 'a' }], transitions: [{ from: 'a', to: 'zz', labels: [] }] });
  assert.equal(broken.machine.transitions.length, 0);
  assert.equal(broken.errors.length, 1);
});

test('סידור אוטומטי: שכבות BFS משמאל לימין', () => {
  const m = M.createMachine('dfa');
  const a = M.addState(m), b = M.addState(m), c = M.addState(m), d = M.addState(m);
  M.addTransition(m, a.id, b.id, [{ read: 'a' }]);
  M.addTransition(m, a.id, c.id, [{ read: 'b' }]);
  M.autoLayout(m, { gapX: 100, gapY: 50 });
  assert.ok(a.x < b.x);
  assert.equal(b.x, c.x);
  assert.notEqual(b.y, c.y);
  assert.ok(d.x > b.x, 'מצב לא ישיג בשכבה האחרונה');
});

test('השלמה עם מצב מלכודת: אס"ד חלקי הופך למלא, והשפה לא משתנה', () => {
  const S = require('../js/sim.js');
  const m = M.createMachine('dfa', { alphabet: ['a', 'b'] });
  const q0 = M.addState(m), q1 = M.addState(m, { accepting: true });
  M.addTransition(m, q0.id, q1.id, [{ read: 'a' }]);
  M.addTransition(m, q1.id, q1.id, [{ read: 'b' }]);
  const before = ['', 'a', 'ab', 'abb', 'b', 'aa', 'aba'].map((w) => S.run(m, w).result);
  const trap = M.completeWithTrap(m);
  assert.ok(trap);
  assert.equal(trap.name, 'מלכודת');
  assert.equal(trap.accepting, false);
  assert.ok(!M.validate(m).some((i) => i.code === 'MISSING_TRANSITIONS'));
  assert.deepEqual(['', 'a', 'ab', 'abb', 'b', 'aa', 'aba'].map((w) => S.run(m, w).result), before);
  assert.equal(M.completeWithTrap(m), null, 'כבר מלא');
});
