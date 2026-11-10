/*
 * examples.js - מכונות לדוגמה. כל דוגמה היא קובץ מכונה רגיל (אותו מבנה כמו שמירה ל-JSON).
 */
(function (root, factory) {
  const api = factory();
  if (typeof module === 'object' && module.exports) module.exports = api;
  else root.AutomataExamples = api;
})(typeof globalThis !== 'undefined' ? globalThis : this, function () {
  'use strict';
  const base = { format: 'dsviz-automaton', version: 1, view: { x: 0, y: 0, zoom: 1 } };
  const st = (id, x, y, accepting, extra) => Object.assign({ id, name: id, x, y, accepting: !!accepting }, extra || {});
  const fa = (read) => read.split(',').map((r) => ({ read: r === 'ε' ? '' : r }));

  const EXAMPLES = [
    Object.assign({}, base, {
      id: 'even-a',
      type: 'dfa',
      name: 'מספר זוגי של a',
      description: 'מקבל מילים מעל {a, b} שיש בהן מספר זוגי של a (גם 0).',
      alphabet: ['a', 'b'],
      states: [st('q0', -110, 0, true), st('q1', 110, 0, false)],
      start: 'q0',
      transitions: [
        { id: 't1', from: 'q0', to: 'q1', labels: fa('a') },
        { id: 't2', from: 'q1', to: 'q0', labels: fa('a') },
        { id: 't3', from: 'q0', to: 'q0', labels: fa('b') },
        { id: 't4', from: 'q1', to: 'q1', labels: fa('b') },
      ],
      tests: [
        { word: '', expect: true }, { word: 'aa', expect: true }, { word: 'abba', expect: true },
        { word: 'bab', expect: false }, { word: 'aaab', expect: false }, { word: 'baaba', expect: false },
      ],
      sampleWord: 'abbab',
    }),
    Object.assign({}, base, {
      id: 'ends-ab',
      type: 'nfa',
      name: 'מסתיים ב-ab',
      description: 'אסל"ד שמנחש מתי מתחיל ה-ab האחרון. מקבל מילים מעל {a, b} שמסתיימות ב-ab.',
      alphabet: ['a', 'b'],
      states: [st('q0', -200, 0, false, { loopAngle: -90 }), st('q1', 0, 0, false), st('q2', 200, 0, true)],
      start: 'q0',
      transitions: [
        { id: 't1', from: 'q0', to: 'q0', labels: fa('a,b') },
        { id: 't2', from: 'q0', to: 'q1', labels: fa('a') },
        { id: 't3', from: 'q1', to: 'q2', labels: fa('b') },
      ],
      tests: [
        { word: 'ab', expect: true }, { word: 'aab', expect: true }, { word: 'babab', expect: true },
        { word: '', expect: false }, { word: 'ba', expect: false }, { word: 'abb', expect: false },
      ],
      sampleWord: 'abaab',
    }),
    Object.assign({}, base, {
      id: 'only-a-or-b',
      type: 'nfa',
      name: 'רק a או רק b (עם ε)',
      description: 'אסל"ד עם מעברי ε: מקבל מילים שכולן a או כולן b. מראה סגור ε.',
      alphabet: ['a', 'b'],
      states: [st('q0', -150, 0, false), st('q1', 60, -90, true, { loopAngle: 0 }), st('q2', 60, 90, true, { loopAngle: 0 })],
      start: 'q0',
      transitions: [
        { id: 't1', from: 'q0', to: 'q1', labels: fa('ε') },
        { id: 't2', from: 'q0', to: 'q2', labels: fa('ε') },
        { id: 't3', from: 'q1', to: 'q1', labels: fa('a') },
        { id: 't4', from: 'q2', to: 'q2', labels: fa('b') },
      ],
      tests: [
        { word: '', expect: true }, { word: 'aaa', expect: true }, { word: 'bb', expect: true },
        { word: 'ab', expect: false }, { word: 'aab', expect: false },
      ],
      sampleWord: 'aaa',
    }),
    Object.assign({}, base, {
      id: 'anbn',
      type: 'pda',
      name: 'אותו מספר a ו-b',
      description: 'אוטומט מחסנית שמקבל aⁿbⁿ (n ≥ 0): דוחף A על כל a, ושולף A על כל b.',
      alphabet: ['a', 'b'],
      stackAlphabet: ['Z', 'A'],
      stackStart: 'Z',
      acceptBy: 'final',
      states: [st('q0', -150, -40, false, { loopAngle: -90 }), st('q1', 150, -40, false, { loopAngle: -90 }), st('q2', 0, 130, true)],
      start: 'q0',
      transitions: [
        { id: 't1', from: 'q0', to: 'q0', labels: [{ read: 'a', pop: 'Z', push: 'AZ' }, { read: 'a', pop: 'A', push: 'AA' }] },
        { id: 't2', from: 'q0', to: 'q1', labels: [{ read: 'b', pop: 'A', push: '' }] },
        { id: 't3', from: 'q1', to: 'q1', labels: [{ read: 'b', pop: 'A', push: '' }] },
        { id: 't4', from: 'q1', to: 'q2', labels: [{ read: '', pop: 'Z', push: 'Z' }] },
        { id: 't5', from: 'q0', to: 'q2', labels: [{ read: '', pop: 'Z', push: 'Z' }] },
      ],
      tests: [
        { word: '', expect: true }, { word: 'ab', expect: true }, { word: 'aaabbb', expect: true },
        { word: 'aab', expect: false }, { word: 'abb', expect: false }, { word: 'ba', expect: false },
      ],
      sampleWord: 'aabb',
    }),
    Object.assign({}, base, {
      id: 'binary-inc',
      type: 'tm',
      name: 'הוספת 1 למספר בינארי',
      description: 'מכונת טיורינג שמוסיפה 1: רצה לסוף המספר, הופכת 1 ל-0 עד שמוצאת 0 (או ריק), כותבת 1 וחוזרת להתחלה.',
      alphabet: ['0', '1'],
      tapeAlphabet: ['0', '1', '_'],
      blank: '_',
      states: [st('q0', -220, 0, false, { loopAngle: -90 }), st('q1', 0, 0, false, { loopAngle: -90 }), st('q2', 220, 0, false, { loopAngle: -90 }), st('qf', 220, 170, true)],
      start: 'q0',
      transitions: [
        { id: 't1', from: 'q0', to: 'q0', labels: [{ read: '0', write: '0', move: 'R' }, { read: '1', write: '1', move: 'R' }] },
        { id: 't2', from: 'q0', to: 'q1', labels: [{ read: '_', write: '_', move: 'L' }] },
        { id: 't3', from: 'q1', to: 'q1', labels: [{ read: '1', write: '0', move: 'L' }] },
        { id: 't4', from: 'q1', to: 'q2', labels: [{ read: '0', write: '1', move: 'L' }, { read: '_', write: '1', move: 'L' }] },
        { id: 't5', from: 'q2', to: 'q2', labels: [{ read: '0', write: '0', move: 'L' }, { read: '1', write: '1', move: 'L' }] },
        { id: 't6', from: 'q2', to: 'qf', labels: [{ read: '_', write: '_', move: 'R' }] },
      ],
      tests: [
        { word: '1011', expect: true }, { word: '111', expect: true }, { word: '0', expect: true },
      ],
      sampleWord: '1011',
    }),
  ];

  return { EXAMPLES, get: (id) => EXAMPLES.find((e) => e.id === id) || null };
});
