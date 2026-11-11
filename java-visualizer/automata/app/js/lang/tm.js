/*
 * lang/tm.js - מכונת טיורינג למשפחה הקלאסית שאינה חסרת הקשר: aⁿbⁿcⁿ (וגם עם יותר אותיות: aⁿbⁿcⁿdⁿ...).
 * השיטה המוכרת: בכל סיבוב מסמנים a אחד, b אחד ו-c אחד, וחוזרים להתחלה. בסוף בודקים שנשארו רק סימונים.
 * מחזיר מכונה, או null אם הנוסחה אינה מהמשפחה הזו.
 */
(function (root, f) {
  const node = typeof module === 'object' && module.exports;
  const L = node ? require('./ns.js') : root.AutomataLang;
  f(L, root.AutomataModel || (node ? require('../model.js') : null));
  if (node) module.exports = L;
})(typeof globalThis !== 'undefined' ? globalThis : this, function (L, M) {
  'use strict';

  function buildTM(ast) {
    if (ast.kind !== 'blocks') return null;
    const B = ast.blocks, k = B.length, v = B[0].e.v;
    if (k < 3 || !v || !B.every((b) => b.word.length === 1 && b.e.v === v && b.e.c === 1 && b.e.d === 0)) return null;
    const letters = B.map((b) => b.word);
    if (new Set(letters).size !== k) return null;
    // התנאי על n: מותר רק "n ≥ 0" (אין תנאי) או "n ≥ 1"
    const cond = L.nnf(ast.cond || { op: 'true' });
    const ok = (n) => L.evalC(cond, { [v]: n }, '');
    for (let n = 1; n <= 40; n++) if (!ok(n)) return null;
    const pool = Array.from('XYZWVUTSRQ').filter((c) => !letters.includes(c));
    const marks = letters.map((c, i) => (c >= 'a' && c <= 'z' && !letters.includes(c.toUpperCase()) ? c.toUpperCase() : pool[i]));
    const m = M.createMachine('tm', { alphabet: letters, tapeAlphabet: letters.concat(marks, ['_']), blank: '_' });
    const st = (name, acc) => M.addState(m, { id: name, name, accepting: !!acc }).id;
    const q0 = st('q0'), s = letters.slice(0, -1).map((_, i) => st('q' + (i + 1))), back = st('q' + k), chk = st('q' + (k + 1)), acc = st('qf', true);
    const rule = (from, to, read, write, move) => M.addTransition(m, from, to, [{ read, write, move }]);
    rule(q0, s[0], letters[0], marks[0], 'R');
    rule(q0, chk, marks[1], marks[1], 'R');
    if (ok(0)) rule(q0, acc, '_', '_', 'S');
    s.forEach((si, i) => {
      rule(si, si, letters[i], letters[i], 'R');
      rule(si, si, marks[i + 1], marks[i + 1], 'R');
      if (i + 1 < k - 1) rule(si, s[i + 1], letters[i + 1], marks[i + 1], 'R');
      else rule(si, back, letters[i + 1], marks[i + 1], 'L');
    });
    for (let i = 0; i < k; i++) { if (i < k - 1) rule(back, back, letters[i], letters[i], 'L'); if (i > 0) rule(back, back, marks[i], marks[i], 'L'); }
    rule(back, q0, marks[0], marks[0], 'R');
    for (let i = 1; i < k; i++) rule(chk, chk, marks[i], marks[i], 'R');
    rule(chk, acc, '_', '_', 'S');
    m.start = q0;
    L.layoutMachine(m);
    return m;
  }

  L.buildTM = buildTM;
});
