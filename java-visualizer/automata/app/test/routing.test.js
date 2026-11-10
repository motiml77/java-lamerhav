'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const G = require('../js/geometry.js');
const RT = require('../js/routing.js');
const M = require('../js/model.js');
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

/** המרחק הקטן ביותר בין קשת (200 דגימות) לנקודה, או למלבן. */
const curveToPoint = (pts, c) => Math.min(...RT.sample(pts, 200).map((p) => Math.hypot(p.x - c.x, p.y - c.y)));
const curveToRect = (pts, r) => Math.min(...RT.sample(pts, 200).map((p) => RT.rectPointDist(r, p)));

/**
 * בעלות על תוויות: הקשת הקרובה ביותר למרכז של כל תווית היא הקשת שלה, בהפרש של margin פיקסלים לפחות.
 * box (אם ניתן): בנוסף, אף קשת אחרת לא מתקרבת למלבן של התווית יותר מ-box פיקסלים מעבר לקשת שלה.
 */
function assertLabelsOwned(edges, routes, tag, margin, box) {
  for (const e of edges) {
    const r = routes.get(e.id), c = { x: r.label.cx, y: r.label.cy };
    const own = curveToPoint(r.pts, c), ownBox = curveToRect(r.pts, r.label);
    assert.ok(ownBox <= 5 || r.loop, tag + ': התווית של ' + e.from + '→' + e.to + ' רחוקה ' + ownBox.toFixed(1) + 'px מהקשת שלה');
    for (const f of edges) {
      if (f === e) continue;
      const other = curveToPoint(routes.get(f.id).pts, c);
      assert.ok(other - own >= margin, tag + ': התווית של ' + e.from + '→' + e.to + ' קרובה מדי לקשת ' + f.from + '→' + f.to + ' (הפרש ' + (other - own).toFixed(1) + 'px, נדרש ' + margin + ')');
      if (box != null) {
        const gap = curveToRect(routes.get(f.id).pts, r.label) - ownBox;
        assert.ok(gap >= box, tag + ': הקשת ' + f.from + '→' + f.to + ' עוברת ' + gap.toFixed(1) + 'px מהמלבן של התווית של ' + e.from + '→' + e.to + ' (נדרש ' + box + ')');
      }
    }
  }
}

/** מסדרון משותף: בחצי האמצעי של קשת, האורך של החלקים שנמצאים פחות מ-8px מקשת אחרת. לא יותר מ-limit לאף זוג. */
function assertSeparated(edges, routes, tag, limit) {
  const straight = edges.filter((e) => e.from !== e.to);
  for (const e of straight) {
    const a = RT.sample(routes.get(e.id).pts, 100).slice(25, 76);
    for (const f of straight) {
      if (f === e) continue;
      const b = RT.sample(routes.get(f.id).pts, 100);
      let run = 0;
      for (let i = 1; i < a.length; i++) if (b.some((q) => Math.hypot(q.x - a[i].x, q.y - a[i].y) < 8)) run += Math.hypot(a[i].x - a[i - 1].x, a[i].y - a[i - 1].y);
      assert.ok(run <= limit, tag + ': הקשתות ' + e.from + '→' + e.to + ' ו-' + f.from + '→' + f.to + ' רצות יחד ' + run.toFixed(0) + 'px (מותר עד ' + limit + ')');
    }
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
  assertLabelsOwned(edges, routes, 'שורה', 10, 10);
  assertSeparated(edges, routes, 'שורה', 45);
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
  // 22 קשתות על 6 מצבים זה מקרה קיצון: כמעט אין מקום פנוי. גם כאן כל תווית שייכת בבירור לקשת שלה, אבל ההפרש המובטח קטן יותר (8 ולא 10).
  assertLabelsOwned(edges, routes, 'צפוף', 8);
  assertSeparated(edges, routes, 'צפוף', 60);
});

test('הדוגמה "קפיצות מעל מצבים": לכל קשת עיקול משלה, והתוויות לא מתבלבלות', () => {
  const ex = EXAMPLES.find((e) => e.id === 'jumps');
  const edges = ex.transitions.map((t) => ({ id: t.id, from: t.from, to: t.to, lines: [t.labels[0].read] }));
  const routes = RT.route(ex.states, edges, { startId: ex.start });
  const of = (from, to) => routes.get(ex.transitions.find((t) => t.from === from && t.to === to).id);
  const depth = (r) => RT.cubicAt(r.pts, 0.5).y;      // כמה נמוך (או גבוה) עוברת הקשת באמצע שלה
  assertClear(ex.states, edges, routes, 'קפיצות');
  assertLabelsOwned(edges, routes, 'קפיצות', 18, 14);
  assertSeparated(edges, routes, 'קפיצות', 30);
  // שלוש הקשתות שחוזרות אחורה מתחת לשורה: הארוכה (q4→q0) מתעקלת הכי הרבה, והן רחוקות זו מזו באמצע
  const back = [of('q4', 'q0'), of('q3', 'q1'), of('q4', 'q2')].map(depth);
  assert.ok(back.every((y) => y > 26), 'כולן מתחת לשורה');
  assert.ok(back[0] > back[1] + 25 && back[0] > back[2] + 25, 'q4→q0 היא הקשת הרחבה ביותר: ' + back.map((y) => y.toFixed(0)));
  assert.ok(Math.abs(back[1] - back[2]) >= 12, 'q3→q1 ו-q4→q2 לא באותו עומק: ' + back.map((y) => y.toFixed(0)));
  // ושלוש הקשתות שמדלגות קדימה עוברות מעל השורה
  for (const [a, b] of [['q0', 'q2'], ['q1', 'q3'], ['q2', 'q4']]) assert.ok(depth(of(a, b)) < -26, a + '→' + b + ' מעל השורה');
});

function rng(seed) { return () => { seed |= 0; seed = (seed + 0x6D2B79F5) | 0; let t = Math.imul(seed ^ (seed >>> 15), 1 | seed); t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t; return ((t ^ (t >>> 14)) >>> 0) / 4294967296; }; }

test('30 פריסות אקראיות (עם זרע קבוע): אף קשת לא עוברת ליד מצב, וכל תווית שייכת בבירור לקשת שלה', () => {
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
    assertLabelsOwned(edges, routes, 'זרע ' + seed, 10);
    assertSeparated(edges, routes, 'זרע ' + seed, 45);
  }
});

test('כל הדוגמאות (20 ועוד הדגמת הניתוב): קשתות לא עוברות ליד מצבים, תוויות לא חופפות, וכל תווית שייכת בבירור לקשת שלה', () => {
  assert.ok(EXAMPLES.length >= 21);
  for (const ex of EXAMPLES) {
    const m = M.deserialize(ex).machine;
    const perLine = m.type === 'pda' || m.type === 'tm';
    const lines = (t) => (perLine ? t.labels.map((l) => M.formatLabel(m.type, l)) : [t.labels.map((l) => M.formatLabel(m.type, l)).join(', ')]);
    const edges = m.transitions.map((t) => ({ id: t.id, from: t.from, to: t.to, lines: lines(t) }));
    const routes = RT.route(m.states, edges, { startId: m.start });
    assertClear(m.states, edges, routes, ex.id);
    assertLabelsApart(m.states, routes, ex.id);
    assertLabelsOwned(edges, routes, ex.id, 10, 10);
    assertSeparated(edges, routes, ex.id, 30);
  }
});

test('ניתוב מהיר (בזמן גרירה) נותן אותן קשתות כשאין מה לתקן, והתוצאה נשמרת בין ציורים', () => {
  const ex = EXAMPLES.find((e) => e.id === 'jumps');
  const edges = ex.transitions.map((t) => ({ id: t.id, from: t.from, to: t.to, lines: ['a'] }));
  const full = RT.route(ex.states, edges, { startId: ex.start });
  assert.equal(RT.route(ex.states, edges, { startId: ex.start }), full, 'אותו קלט מחזיר את אותה תוצאה בלי לנתב שוב');
  const fast = RT.route(ex.states, edges, { startId: ex.start, fast: true });
  for (const e of edges) assert.equal(fast.get(e.id).d, full.get(e.id).d);
});
