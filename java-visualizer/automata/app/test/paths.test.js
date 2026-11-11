'use strict';
/* הדרכים של ריצה לא דטרמיניסטית (paths.js): מספור, מצב של כל דרך, מחסנית, וכפילויות. */
const test = require('node:test');
const assert = require('node:assert/strict');
const L = require('../js/lang/index.js');
const S = require('../js/sim.js');
const M = require('../js/model.js');
const P = require('../js/paths.js');
const { get } = require('../js/examples.js');

const show = (m, ps) => ps.map((p) => p.n + ':' + M.stateName(m, p.state) + '@' + p.pos + (p.stack != null ? '[' + p.stack + ']' : '') + ' ' + p.status + (p.letter ? ' ' + p.letter : ''));

test('אוטומט מחסנית ל-aⁿbⁿ ∪ bⁿaⁿ על bbaa: דרך אחת מקבלת, השאר נתקעות על b', () => {
  const m = L.machineFor(L.parse('a^n b^n ∪ b^n a^n'));
  const tr = S.run(m, 'bbaa');
  assert.equal(tr.result, 'accept');
  assert.deepEqual(show(m, P.paths(tr, 1, m)), ['1:q1@0[Z] active', '2:q2@0[Z] active']);
  const fin = P.paths(tr, tr.steps.length - 1, m);
  assert.deepEqual(show(m, fin), ['2:q6@4[Z] accepted', '1:q5@0[Z] stuck b', '3:q4@1[AZ] stuck b', '4:q6@0[Z] stuck b']);
  // הדרך המקבלת עברה רק במעברים של החלק השני, והראשון שבהם הוא ε מ-q0 ל-q2
  const win = fin[0], tid = (from, to) => m.transitions.find((t) => M.stateName(m, t.from) === from && M.stateName(m, t.to) === to).id;
  assert.equal(win.edges[0].t, tid('q0', 'q2'));
  assert.ok(win.edges.every((e) => e.t !== tid('q0', 'q1')));
  assert.equal(win.edges.length, 7);
});

test('abba נדחית: כל הדרכים נתקעות, ולכל אחת האות שבה נתקעה', () => {
  const m = L.machineFor(L.parse('a^n b^n ∪ b^n a^n'));
  const tr = S.run(m, 'abba');
  assert.equal(tr.result, 'reject');
  const fin = P.paths(tr, tr.steps.length - 1, m);
  assert.ok(fin.length >= 2 && fin.every((p) => p.status === 'stuck'));
  assert.deepEqual(show(m, fin).slice(0, 1), ['1:q5@2[Z] stuck b']);
});

test('אסל"ד: דרך לכל מצב פעיל, ודרך שנתקעה נשארת עם האות', () => {
  const m = M.deserialize(get('third-last-a')).machine;
  const tr = S.run(m, 'abab');
  assert.deepEqual(show(m, P.paths(tr, 3, m)), ['1:q0@3 active', '2:q1@3 active', '3:q3@3 active']);
  assert.deepEqual(show(m, P.paths(tr, 4, m)), ['1:q0@4 end', '2:q2@4 end', '3:q3@3 stuck b']);
  const ok = S.run(m, 'aab');
  assert.ok(P.paths(ok, ok.steps.length - 1, m).some((p) => p.status === 'accepted' && M.stateName(m, p.state) === 'q3'));
});

test('כפילויות: אותה קונפיגורציה מופיעה פעם אחת, והמספור רציף', () => {
  const tr = { type: 'pda', word: ['a'], result: 'reject', steps: [
    { configs: [{ id: 0, parent: -1, state: 's', pos: 0, stack: 'Z' }] },
    { configs: [{ id: 1, parent: 0, state: 't', pos: 1, stack: 'Z', via: { t: 'x', l: 0 } }, { id: 2, parent: 0, state: 't', pos: 1, stack: 'Z', via: { t: 'y', l: 0 } }, { id: 3, parent: 0, state: 'u', pos: 1, stack: 'AZ', via: { t: 'z', l: 0 } }] },
  ] };
  const m = { type: 'pda', acceptBy: 'final', states: [{ id: 's' }, { id: 't' }, { id: 'u', accepting: true }] };
  const ps = P.paths(tr, 1, m);
  assert.deepEqual(ps.map((p) => [p.n, p.state, p.status]), [[2, 'u', 'accepted'], [1, 't', 'end']]);
  assert.deepEqual(ps.map((p) => p.edges.map((e) => e.t)), [['z'], ['x']]);
  assert.equal(P.paths(null, 0, m).length, 0);
});
