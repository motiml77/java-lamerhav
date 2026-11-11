'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const G = require('../js/geometry.js');

const nums = (d) => (d.match(/-?\d+(\.\d+)?/g) || []).map(Number);

test('קשת ישרה מתחילה ונגמרת על שפת המצבים', () => {
  const a = { x: 0, y: 0 }, b = { x: 200, y: 0 };
  const e = G.edgePath(a, b, false);
  const [x0, y0] = nums(e.d);
  assert.equal(x0, G.R);
  assert.equal(y0, 0);
  const tip = e.arrow.split(' ')[0].split(',').map(Number);
  assert.deepEqual(tip, [200 - G.R, 0], 'קודקוד החץ על שפת היעד');
  assert.deepEqual([e.label.x, e.label.y], [100, 0]);
});

test('שתי קשתות הפוכות מתעקלות לצדדים מנוגדים', () => {
  const a = { x: 0, y: 0 }, b = { x: 200, y: 0 };
  const ab = G.edgePath(a, b, true), ba = G.edgePath(b, a, true);
  assert.ok(ab.label.y < -10, 'a→b מעל הקו');
  assert.ok(ba.label.y > 10, 'b→a מתחת לקו');
  assert.match(ab.d, /Q/);
});

test('לולאה עצמית: התווית בכיוון הלולאה, מחוץ למצב', () => {
  const s = { x: 50, y: 50 };
  const up = G.loopPath(s, -Math.PI / 2);
  assert.ok(up.label.y < s.y - G.R * 2);
  const right = G.loopPath(s, 0);
  assert.ok(right.label.x > s.x + G.R * 2);
});

test('זווית לולאה: הכיוון הכי רחוק מהשכנים', () => {
  const s = { x: 0, y: 0 };
  assert.equal(G.bestLoopAngle(s, []), -Math.PI / 2, 'בלי שכנים: למעלה');
  const a = G.bestLoopAngle(s, [{ x: 0, y: -100 }]);
  assert.ok(Math.abs(a - Math.PI / 2) < 1e-9, 'שכן למעלה: הלולאה למטה');
  const b = G.bestLoopAngle(s, [{ x: 100, y: 0 }, { x: -100, y: 0 }]);
  assert.ok(Math.abs(b + Math.PI / 2) < 1e-9, 'שכנים משני הצדדים: למעלה');
});

test('מרחק מנקודה לקטע, וגודל תווית', () => {
  assert.equal(G.segmentDistance({ x: 50, y: 30 }, { x: 0, y: 0 }, { x: 100, y: 0 }), 30);
  assert.equal(G.segmentDistance({ x: -40, y: 30 }, { x: 0, y: 0 }, { x: 100, y: 0 }), 50);
  const one = G.labelBox(['a']), two = G.labelBox(['a,Z/AZ', 'b,A/ε']);
  assert.ok(two.w > one.w && two.h > one.h);
});
