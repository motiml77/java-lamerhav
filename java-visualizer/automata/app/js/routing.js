/*
 * routing.js - ניתוב קשתות ומיקום תוויות. טהור (בלי DOM), נבדק ב-test/routing.test.js.
 *
 * כל קשת היא בזייה מעוקבת: נקודות הבקרה על שליש ושני שליש מהקו, מוזזות הצידה ב-h1 / h2.
 * קשת ישרה = h1 = h2 = 0. מחפשים את העיקול הזול ביותר שעובר במרחק של לפחות R + MARGIN מכל מצב
 * שאינו קצה של הקשת. קשתות הפוכות (a→b וגם b→a) מתעקלות כל אחת לשמאל כיוון הנסיעה, אז הן נפרדות.
 * אחר כך לולאות (בכיוון הכי פנוי), ובסוף תוויות: באמצע הקשת, ואם הן נוגעות במצב או בתווית אחרת, זזות.
 */
(function (root, factory) {
  const api = factory(root.AutomataGeometry || (typeof require === 'function' ? require('./geometry.js') : null));
  if (typeof module === 'object' && module.exports) module.exports = api;
  else root.AutomataRouting = api;
})(typeof globalThis !== 'undefined' ? globalThis : this, function (G) {
  'use strict';

  const R = G.R;
  const MARGIN = 8;          // המרחק המובטח (מעבר לרדיוס) בין קשת למצב שאינו קצה שלה
  const SAFE = MARGIN + 4;   // הנתב שומר על מרווח קצת יותר גדול, כדי שדגימה צפופה יותר עדיין תעבור
  const PAIR_H = 37;         // עיקול בסיסי לזוג קשתות הפוכות (שיא של כ-28px)
  const LABEL_PAD = 3;

  const sub = (a, b) => ({ x: a.x - b.x, y: a.y - b.y });
  const add = (a, b) => ({ x: a.x + b.x, y: a.y + b.y });
  const mul = (a, k) => ({ x: a.x * k, y: a.y * k });
  const len = (a) => Math.hypot(a.x, a.y);
  const norm = (a) => { const l = len(a) || 1; return { x: a.x / l, y: a.y / l }; };
  const r1 = (n) => Math.round(n * 10) / 10;
  const pt = (p) => r1(p.x) + ' ' + r1(p.y);

  function cubicAt(p, t) {
    const u = 1 - t, a = u * u * u, b = 3 * u * u * t, c = 3 * u * t * t, d = t * t * t;
    return { x: a * p.p0.x + b * p.c1.x + c * p.c2.x + d * p.p3.x, y: a * p.p0.y + b * p.c1.y + c * p.c2.y + d * p.p3.y };
  }
  function sample(p, n) {
    const out = [];
    for (let i = 0; i <= n; i++) out.push(cubicAt(p, i / n));
    return out;
  }

  /* מועמדים לעיקול: [h1, h2] (חיובי = שמאלה לכיוון הנסיעה), ממוינים לפי "מחיר" */
  const LEVELS = [0, 30, 37, 50, 75, 100, 130, 170, 220, 280, 350];
  const CANDIDATES = (() => {
    const out = [];
    for (const s1 of [1, -1]) for (const s2 of [1, -1]) for (const a of LEVELS) for (const b of LEVELS) {
      if ((a === 0 && s1 < 0) || (b === 0 && s2 < 0)) continue;
      const h1 = s1 * a, h2 = s2 * b;
      const sCurve = h1 * h2 < 0;
      out.push({ h1, h2, cost: Math.max(a, b) + 0.4 * Math.abs(a - b) + (sCurve ? 80 : 0) });
    }
    return out.sort((x, y) => x.cost - y.cost);
  })();

  /** הגאומטריה של קשת a→b עם עיקול [h1, h2]. */
  function shapeEdge(a, b, h1, h2) {
    const v = sub(b, a), L = len(v), u = norm(v);
    const left = { x: u.y, y: -u.x };
    const c1 = add(add(a, mul(u, L / 3)), mul(left, h1));
    const c2 = add(add(a, mul(u, (2 * L) / 3)), mul(left, h2));
    const p0 = add(a, mul(norm(sub(c1, a)), R));
    const tip = add(b, mul(norm(sub(c2, b)), R));
    const uEnd = norm(sub(tip, c2));
    const pEnd = sub(tip, mul(uEnd, G.ARROW_L - 1));
    const pts = { p0, c1, c2, p3: tip };
    return { pts, d: 'M' + pt(p0) + 'C' + pt(c1) + ' ' + pt(c2) + ' ' + pt(pEnd), arrow: G.arrowHead(tip, uEnd), h1, h2 };
  }

  /** לולאה עצמית בזווית ang (ברדיאנים). */
  function shapeLoop(s, ang) {
    const dir = (t) => ({ x: Math.cos(t), y: Math.sin(t) });
    const spread = 0.48, ctrl = 0.62, reach = 2.9 * R;
    const p0 = add(s, mul(dir(ang - spread), R));
    const tip = add(s, mul(dir(ang + spread), R));
    const c1 = add(s, mul(dir(ang - ctrl), reach));
    const c2 = add(s, mul(dir(ang + ctrl), reach));
    const uEnd = norm(sub(tip, c2));
    const pEnd = sub(tip, mul(uEnd, G.ARROW_L - 1));
    return { pts: { p0, c1, c2, p3: tip }, d: 'M' + pt(p0) + 'C' + pt(c1) + ' ' + pt(c2) + ' ' + pt(pEnd), arrow: G.arrowHead(tip, uEnd), angle: ang };
  }

  /** המרחק הקטן ביותר (פחות R) בין הדגימות למכשולים. */
  function clearance(samples, obstacles) {
    let min = Infinity;
    for (const o of obstacles) for (const p of samples) {
      const d = Math.hypot(p.x - o.x, p.y - o.y) - R;
      if (d < min) min = d;
    }
    return min;
  }

  function samplesFor(shape) {
    const p = shape.pts;
    const approx = len(sub(p.c1, p.p0)) + len(sub(p.c2, p.c1)) + len(sub(p.p3, p.c2));
    return sample(p, Math.max(24, Math.ceil(approx / 10)));
  }

  /** כמה דגימות קרובות (פחות מ-10px) לקשתות שכבר נותבו. */
  function crowding(samples, routed) {
    let n = 0;
    for (const p of samples) for (const q of routed) if (Math.abs(p.x - q.x) < 10 && Math.abs(p.y - q.y) < 10) { n++; break; }
    return n;
  }

  function routeEdge(a, b, obstacles, paired, routedSamples) {
    // מכשולים רלוונטיים: רק מה שקרוב מספיק לאזור של הקשת
    const L = len(sub(b, a));
    const mid = mul(add(a, b), 0.5);
    const near = obstacles.filter((o) => len(sub(o, mid)) < L / 2 + 400);
    // צד מועדף: הצד עם פחות מצבים ליד הקו
    const u = norm(sub(b, a)), left = { x: u.y, y: -u.x };
    let leftCount = 0, rightCount = 0;
    for (const o of near) {
      const w = sub(o, a), along = w.x * u.x + w.y * u.y, side = w.x * left.x + w.y * left.y;
      if (along > -R && along < L + R && Math.abs(side) < R + SAFE + 60) { if (side > 0) leftCount++; else rightCount++; }
    }
    let best = null, found = 0;
    for (const c of CANDIDATES) {
      if (paired && (c.h1 < PAIR_H || c.h2 < PAIR_H)) continue;
      if (best && c.cost > best.total) break;
      const side = (c.h1 + c.h2) >= 0 ? leftCount : rightCount;
      const shape = shapeEdge(a, b, c.h1, c.h2);
      const smp = samplesFor(shape);
      if (near.length && clearance(smp, near) < SAFE) continue;
      const total = c.cost + side * 20 + crowding(smp, routedSamples) * 3;
      if (!best || total < best.total) best = { shape, smp, total };
      if (++found >= 6) break;
    }
    if (best) return best;
    // אין עיקול שעובר נקי: לוקחים את זה עם המרווח הכי גדול
    let fallback = null;
    for (const c of CANDIDATES) {
      if (paired && (c.h1 < PAIR_H || c.h2 < PAIR_H)) continue;
      const shape = shapeEdge(a, b, c.h1, c.h2);
      const smp = samplesFor(shape);
      const cl = clearance(smp, near);
      if (!fallback || cl > fallback.cl) fallback = { shape, smp, cl };
    }
    return fallback;
  }

  function angleDist(x, y) { let d = Math.abs(x - y) % (2 * Math.PI); return d > Math.PI ? 2 * Math.PI - d : d; }

  function routeLoop(s, obstacles, incident, fixed) {
    if (fixed != null) { const sh = shapeLoop(s, fixed); return { shape: sh, smp: samplesFor(sh) }; }
    let best = null;
    for (let k = 0; k < 16; k++) {
      const ang = -Math.PI / 2 + (k * Math.PI) / 8;
      const sh = shapeLoop(s, ang);
      const smp = samplesFor(sh);
      const cl = obstacles.length ? clearance(smp, obstacles) : Infinity;
      const spread = incident.length ? Math.min(...incident.map((d) => angleDist(d, ang))) : Math.PI;
      const score = (cl < SAFE ? -1000 + cl : 0) + spread * 40 - (k === 0 ? 0 : 3) - Math.min(k, 16 - k) * 0.5;
      if (!best || score > best.score) best = { shape: sh, smp, score };
    }
    return best;
  }

  /* ---------- תוויות ---------- */
  function rectHitsCircle(r, c, rad) {
    const x = Math.max(r.x, Math.min(c.x, r.x + r.w)), y = Math.max(r.y, Math.min(c.y, r.y + r.h));
    return Math.hypot(c.x - x, c.y - y) < rad;
  }
  function rectsOverlap(a, b, pad) {
    return a.x < b.x + b.w + pad && b.x < a.x + a.w + pad && a.y < b.y + b.h + pad && b.y < a.y + a.h + pad;
  }
  function overlapScore(r, states, placed) {
    let s = 0;
    for (const c of states) if (rectHitsCircle(r, c, R + 2)) s += 2;
    for (const q of placed) if (rectsOverlap(r, q, LABEL_PAD)) s += 1;
    return s;
  }
  const boxAt = (c, box) => ({ x: c.x - box.w / 2, y: c.y - box.h / 2, w: box.w, h: box.h, cx: c.x, cy: c.y });

  function placeLabel(route, box, states, placed) {
    const p = route.shape.pts;
    const cands = [];
    if (route.loop) {
      const n = { x: Math.cos(route.shape.angle), y: Math.sin(route.shape.angle) };
      const apex = cubicAt(p, 0.5);
      const base = add(apex, { x: n.x * (box.w / 2 + 4), y: n.y * (box.h / 2 + 4) });
      for (const k of [0, 8, 16, 26, 38]) cands.push(add(base, mul(n, k)));
    } else {
      const tan = norm(sub(cubicAt(p, 0.52), cubicAt(p, 0.48)));
      const nrm = { x: -tan.y, y: tan.x };
      const off = Math.abs(nrm.x) * (box.w / 2) + Math.abs(nrm.y) * (box.h / 2) + 5;
      for (const t of [0.5, 0.42, 0.58, 0.34, 0.66, 0.27, 0.73, 0.2, 0.8]) {
        const c = cubicAt(p, t);
        cands.push(c, add(c, mul(nrm, off)), sub(c, mul(nrm, off)));
      }
    }
    let best = null;
    for (const c of cands) {
      const r = boxAt(c, box);
      const s = overlapScore(r, states, placed);
      if (s === 0) return r;
      if (!best || s < best.s) best = { r, s };
    }
    return best.r;
  }

  /**
   * מנתב את כל הקשתות.
   * states: [{id, x, y, loopAngle?}] · edges: [{id, from, to, lines: [מחרוזות התווית]}] · opts.startId
   * מחזיר Map: id -> { d, arrow, loop, pts: {p0,c1,c2,p3}, label: {x,y,w,h,cx,cy}, angle? }
   */
  function route(states, edges, opts) {
    opts = opts || {};
    const byId = new Map(states.map((s) => [s.id, s]));
    const pairs = new Set(edges.map((e) => e.from + '>' + e.to));
    const out = new Map();
    const routedSamples = [];
    const incident = new Map(states.map((s) => [s.id, []]));
    const start = byId.get(opts.startId);
    if (start) incident.get(start.id).push(Math.PI);     // חץ הכניסה בא משמאל
    const straight = edges.filter((e) => e.from !== e.to && byId.has(e.from) && byId.has(e.to));
    straight.sort((x, y) => len(sub(byId.get(x.from), byId.get(x.to))) - len(sub(byId.get(y.from), byId.get(y.to))));
    for (const e of straight) {
      const a = byId.get(e.from), b = byId.get(e.to);
      const obstacles = states.filter((s) => s !== a && s !== b);
      const r = routeEdge(a, b, obstacles, pairs.has(e.to + '>' + e.from), routedSamples);
      for (const p of r.smp) routedSamples.push(p);
      incident.get(a.id).push(Math.atan2(r.shape.pts.p0.y - a.y, r.shape.pts.p0.x - a.x));
      incident.get(b.id).push(Math.atan2(r.shape.pts.p3.y - b.y, r.shape.pts.p3.x - b.x));
      out.set(e.id, { d: r.shape.d, arrow: r.shape.arrow, pts: r.shape.pts, loop: false, shape: r.shape });
    }
    for (const e of edges) {
      if (e.from !== e.to || !byId.has(e.from)) continue;
      const s = byId.get(e.from);
      const r = routeLoop(s, states.filter((q) => q !== s), incident.get(s.id), s.loopAngle != null ? (s.loopAngle * Math.PI) / 180 : null);
      out.set(e.id, { d: r.shape.d, arrow: r.shape.arrow, pts: r.shape.pts, loop: true, angle: r.shape.angle, shape: r.shape });
    }
    // תוויות: קודם לולאות (יש להן פחות מקום לזוז), אחר כך לפי הסדר
    const placed = [];
    const order = edges.filter((e) => out.has(e.id)).sort((x, y) => (out.get(y.id).loop ? 1 : 0) - (out.get(x.id).loop ? 1 : 0));
    for (const e of order) {
      const r = out.get(e.id);
      const box = G.labelBox(e.lines && e.lines.length ? e.lines : ['?']);
      const lab = placeLabel(r, box, states, placed);
      placed.push(lab);
      r.label = lab;
      delete r.shape;
    }
    return out;
  }

  return { MARGIN, route, cubicAt, sample, shapeEdge, shapeLoop, rectsOverlap, rectHitsCircle };
});
