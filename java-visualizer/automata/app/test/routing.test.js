'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const G = require('../js/geometry.js');
const RT = require('../js/routing.js');
const { EXAMPLES } = require('../js/examples.js');

const R = G.R, MIN = R + RT.MARGIN;

/** בודק שכל נקודה (80 דגימות לקשת) רחוקה לפחות R + MARGIN מכל מצב שאינו קצה. */
function assertClear(states, edges, routes, tag) {
  const byId = new Map(states.map((s) => [s.id, s]));
  for (const e of edges) {
    const r = routes.get(e.id);
    assert.ok(r, tag + ': אין ניתוב ל-' + e.id);
    const pts = RT.sample(r.pts, 80);
    for (const s of states) {
      if (s.id === e.from || s.id === e.to) continue;
      for (const p of pts) {
        const d = Math.hypot(p.x - s.x, p.y - s.y);
        assert.ok(d >= MIN - 1e-6, tag + ': הקשת ' + e.from + '→' + e.to + ' עוברת ' + d.toFixed(1) + 'px ממצב ' + s.id + ' (מינימום ' + MIN + ')');
      }
    }
    void byId;
  }
}

function assertLabelsApart(states, routes, tag) {
  const labels = Array.from(routes.values()).map((r) => r.label);
  for (let i = 0; i < labels.length; i++) {
    for (const s of states) assert.ok(!RT.rectHitsCircle(labels[i], s, R), tag + ': תווית על מצב ' + s.id);
    for (let j = i + 1; j < labels.length; j++) assert.ok(!RT.rectsOverlap(labels[i], labels[j], 0), tag + ': תוויות חופפות ' + i + ',' + j);
  }
}

const E = (from, to, lines) => ({ id: from + '>' + to, from, to, lines: lines || ['a'] });

test('קשת בלי מכשולים נשארת ישרה; קשתות הפוכות מתעקלות לצדדים מנוגדים', () => {
  const states = [{ id: 'a', x: 0, y: 0 }, { id: 'b', x: 200, y: 0 }];
  const one = RT.route(states, [E('a', 'b')]);
  assert.equal(one.get('a>b').pts.c1.y, 0);
  const two = RT.route(states, [E('a', 'b'), E('b', 'a')]);
  assert.ok(two.get('a>b').label.cy < -10, 'a→b מעל');
  assert.ok(two.get('b>a').label.cy > 10, 'b→a מתחת');
});

test('שורה של 5 מצבים, קשתות שמדלגות מעל האמצעיים', () => {
  const states = [0, 1, 2, 3, 4].map((i) => ({ id: 'q' + i, x: i * 160, y: 0 }));
  const edges = [E('q0', 'q1'), E('q1', 'q2'), E('q2', 'q3'), E('q3', 'q4'),
    E('q0', 'q2', ['b']), E('q0', 'q3', ['a']), E('q1', 'q4', ['a', 'b']), E('q0', 'q4', ['ε']), E('q4', 'q0', ['b']), E('q3', 'q1', ['b']), E('q2', 'q0', ['a'])];
  const routes = RT.route(states, edges, { startId: 'q0' });
  assert.notEqual(routes.get('q0>q2').pts.c1.y, 0, 'הקשת q0→q2 חייבת להתעקל מעל q1');
  assertClear(states, edges, routes, 'שורה');
  assertLabelsApart(states, routes, 'שורה');
});

test('אסל"ד צפוף עם 6 מצבים (כמעט כל הזוגות מחוברים)', () => {
  const states = [[-170, -80], [0, -80], [170, -80], [-170, 80], [0, 80], [170, 80]].map(([x, y], i) => ({ id: 's' + i, x, y }));
  const edges = [];
  for (let i = 0; i < 6; i++) for (let j = 0; j < 6; j++) if (i !== j && (i + 2 * j) % 3 !== 0) edges.push(E('s' + i, 's' + j, ['a']));
  edges.push(E('s0', 's0', ['b']), E('s4', 's4', ['a', 'b']));
  assert.ok(edges.length >= 20);
  const routes = RT.route(states, edges, { startId: 's0' });
  assertClear(states, edges, routes, 'צפוף');
  assertLabelsApart(states, routes, 'צפוף');
});

function rng(seed) { return () => { seed |= 0; seed = (seed + 0x6D2B79F5) | 0; let t = Math.imul(seed ^ (seed >>> 15), 1 | seed); t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t; return ((t ^ (t >>> 14)) >>> 0) / 4294967296; }; }

test('30 פריסות אקראיות (עם זרע קבוע): אף קשת לא עוברת ליד מצב', () => {
  for (let seed = 1; seed <= 30; seed++) {
    const rand = rng(seed);
    const n = 5 + Math.floor(rand() * 4);
    const states = [];
    while (states.length < n) {
      const p = { id: 'q' + states.length, x: Math.round(rand() * 800), y: Math.round(rand() * 560) };
      if (states.every((s) => Math.hypot(s.x - p.x, s.y - p.y) >= 120)) states.push(p);
    }
    const edges = [], seen = new Set();
    const m = 8 + Math.floor(rand() * 7);
    while (edges.length < m) {
      const a = Math.floor(rand() * n), b = rand() < 0.12 ? a : Math.floor(rand() * n);
      const k = a + '>' + b;
      if (seen.has(k)) continue;
      seen.add(k);
      edges.push(E('q' + a, 'q' + b, ['a']));
    }
    const routes = RT.route(states, edges, { startId: 'q0' });
    assertClear(states, edges, routes, 'זרע ' + seed);
  }
});

test('כל הדוגמאות: קשתות לא עוברות ליד מצבים, ותוויות לא חופפות', () => {
  for (const ex of EXAMPLES) {
    const states = ex.states.map((s) => ({ id: s.id, x: s.x, y: s.y, loopAngle: s.loopAngle }));
    const edges = ex.transitions.map((t) => ({ id: t.id, from: t.from, to: t.to, lines: t.labels.map((l) => JSON.stringify(l).slice(0, 8)) }));
    const routes = RT.route(states, edges, { startId: ex.start });
    assertClear(states, edges, routes, ex.id);
    assertLabelsApart(states, routes, ex.id);
  }
});
