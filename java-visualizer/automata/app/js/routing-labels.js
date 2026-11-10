/*
 * routing-labels.js - החלק של הניתוב שממקם תוויות, והרשת המרחבית של נקודות הקשתות. טהור (בלי DOM).
 *
 * תווית יושבת על הקשת שלה, בנקודה שבה הקשת רחוקה מכל קשת אחרת. לכל מקום אפשרי לאורך הקשת (t בין 0.25 ל-0.75)
 * נותנים ציון לפי המרחק מקשתות אחרות, מתוויות אחרות וממצבים, ובוחרים את הטוב ביותר. כך ברור לאיזו קשת כל תווית שייכת:
 * הבדיקות דורשות שהקשת הקרובה ביותר לתווית תהיה הקשת שלה, בהפרש של 10px לפחות (OWN).
 * רק אם אין אף מקום פנוי על הקשת, התווית זזה קצת הצידה ממנה או קרוב יותר לקצה.
 */
(function (root, factory) {
  const node = typeof module === 'object' && module.exports;
  const api = factory(root.AutomataGeometry || (node ? require('./geometry.js') : null));
  if (node) module.exports = api;
  else root.AutomataRoutingLabels = api;
})(typeof globalThis !== 'undefined' ? globalThis : this, function (G) {
  'use strict';
  const R = G.R;
  const SEP = 22;        // גודל תא ברשת, וגם המרחק שמתחתיו שתי קשתות "חולקות מסדרון"
  const OWN = 10;        // כמה קרובה יותר הקשת של התווית מכל קשת אחרת (לפחות)
  const LABEL_PAD = 3;

  function cubicAt(p, t) {
    const u = 1 - t, a = u * u * u, b = 3 * u * u * t, c = 3 * u * t * t, d = t * t * t;
    return { x: a * p.p0.x + b * p.c1.x + c * p.c2.x + d * p.p3.x, y: a * p.p0.y + b * p.c1.y + c * p.c2.y + d * p.p3.y };
  }
  function sample(p, n) {
    const out = [];
    for (let i = 0; i <= n; i++) out.push(cubicAt(p, i / n));
    return out;
  }

  /* ---------- רשת מרחבית: "כמה רחוקה הקשת הקרובה ביותר שאינה אני" ---------- */
  class Grid {
    /** הרשת מכסה את המצבים ועוד שוליים רחבים (קשת לא מתעקלת רחוק יותר). */
    constructor(states) {
      const xs = states.map((s) => s.x), ys = states.map((s) => s.y), pad = 440;
      this.x0 = (xs.length ? Math.min(...xs) : 0) - pad;
      this.y0 = (ys.length ? Math.min(...ys) : 0) - pad;
      this.w = Math.ceil(((xs.length ? Math.max(...xs) : 0) + pad - this.x0) / SEP) + 1;
      this.h = Math.ceil(((ys.length ? Math.max(...ys) : 0) + pad - this.y0) / SEP) + 1;
      this.cells = new Array(this.w * this.h);
      this.live = [];
    }
    /** מחליף את הנקודות של קשת id (הישנות נשארות ברשת אבל מסומנות כלא בתוקף). */
    put(id, pts) {
      const ver = (this.live[id] || 0) + 1;
      this.live[id] = ver;
      for (const p of pts) {
        const i = Math.floor((p.x - this.x0) / SEP), j = Math.floor((p.y - this.y0) / SEP);
        if (i < 0 || j < 0 || i >= this.w || j >= this.h) continue;
        const c = this.cells[j * this.w + i] || (this.cells[j * this.w + i] = []);
        c.push(p.x, p.y, id, ver);
      }
    }
    /** המרחק מהמלבן (או מנקודה, כש-w = h = 0) לנקודה הקרובה ביותר של קשת שאינה self. לא יותר מ-reach. */
    rectNear(r, self, reach) {
      const x1 = r.x + (r.w || 0), y1 = r.y + (r.h || 0), live = this.live;
      const i0 = Math.max(0, Math.floor((r.x - reach - this.x0) / SEP)), i1 = Math.min(this.w - 1, Math.floor((x1 + reach - this.x0) / SEP));
      const j0 = Math.max(0, Math.floor((r.y - reach - this.y0) / SEP)), j1 = Math.min(this.h - 1, Math.floor((y1 + reach - this.y0) / SEP));
      let best = reach * reach;
      for (let j = j0; j <= j1; j++) {
        for (let i = i0; i <= i1; i++) {
          const c = this.cells[j * this.w + i];
          if (!c) continue;
          for (let k = 0; k < c.length; k += 4) {
            if (c[k + 2] === self || live[c[k + 2]] !== c[k + 3]) continue;
            const px = c[k], py = c[k + 1];
            const dx = px < r.x ? r.x - px : px > x1 ? px - x1 : 0, dy = py < r.y ? r.y - py : py > y1 ? py - y1 : 0;
            const d = dx * dx + dy * dy;
            if (d < best) best = d;
          }
        }
      }
      return Math.sqrt(best);
    }
  }

  /* ---------- גאומטריה של מלבנים ---------- */
  function rectPointDist(r, p) { return Math.hypot(Math.max(r.x - p.x, 0, p.x - r.x - r.w), Math.max(r.y - p.y, 0, p.y - r.y - r.h)); }
  function rectHitsCircle(r, c, rad) { return rectPointDist(r, c) < rad; }
  function rectsOverlap(a, b, pad) {
    return a.x < b.x + b.w + pad && b.x < a.x + a.w + pad && a.y < b.y + b.h + pad && b.y < a.y + a.h + pad;
  }
  const rectGap = (a, b) => Math.hypot(Math.max(a.x - b.x - b.w, b.x - a.x - a.w, 0), Math.max(a.y - b.y - b.h, b.y - a.y - a.h, 0));
  const boxAt = (c, box) => ({ x: c.x - box.w / 2, y: c.y - box.h / 2, w: box.w, h: box.h, cx: c.x, cy: c.y });

  /**
   * המקומות האפשריים לתווית: { c: המרכז, own: המרחק מהמלבן לקשת שלה, ownC: המרחק מהמרכז לקשת שלה, bias: העדפה מוקדמת }.
   * off = false: על הקשת (ללולאה: מעבר לקצה שלה). off = true: המוצא האחרון, ליד הקשת ולא עליה.
   */
  function candidates(route, box, off) {
    const p = route.pts, out = [];
    if (route.loop) {
      if (off) return out;
      const n = { x: Math.cos(route.angle), y: Math.sin(route.angle) };
      const apex = cubicAt(p, 0.5), ext = Math.abs(n.x) * (box.w / 2) + Math.abs(n.y) * (box.h / 2);
      for (const k of [0, 8, 16, 26, 38]) out.push({ c: { x: apex.x + n.x * (box.w / 2 + 4 + k), y: apex.y + n.y * (box.h / 2 + 4 + k) }, own: 4 + k, ownC: ext + 4 + k, bias: -1.5 * k });
      return out;
    }
    const mid = (t) => -40 * Math.abs(t - 0.5);
    if (!off) {
      for (let i = 0; i <= 28; i++) { const t = 0.15 + i * 0.025; out.push({ c: cubicAt(p, t), own: 0, ownC: 0, bias: mid(t) - (t < 0.249 || t > 0.751 ? 12 : 0) }); }
      return out;
    }
    for (const t of [0.125, 0.875, 0.1, 0.9]) out.push({ c: cubicAt(p, t), own: 0, ownC: 0, bias: mid(t) - 30 });     // קרוב לקצה, אבל עדיין על הקשת
    for (const t of [0.5, 0.4, 0.6, 0.3, 0.7]) {
      const a = cubicAt(p, t - 0.02), b = cubicAt(p, t + 0.02), l = Math.hypot(b.x - a.x, b.y - a.y) || 1;
      const nx = -(b.y - a.y) / l, ny = (b.x - a.x) / l, c = cubicAt(p, t);
      const d = Math.abs(nx) * (box.w / 2) + Math.abs(ny) * (box.h / 2) + 5;
      for (const sg of [1, -1]) out.push({ c: { x: c.x + nx * d * sg, y: c.y + ny * d * sg }, own: 5, ownC: d, bias: mid(t) - 60 });
    }
    return out;
  }

  /**
   * החלק של הציון שלא תלוי בתוויות האחרות. gc: כמה רחוקה מהמלבן הקשת האחרת הקרובה ביותר (מעבר לקשת של התווית עצמה).
   * mid: אותו דבר ממרכז התווית. זה המדד שהבדיקות דורשות (OWN לפחות), ולכן מקום שלא עומד בו נפסל כמעט תמיד.
   */
  function fixedScore(r, cand, self, states, grid) {
    let gs = 40;
    for (const s of states) gs = Math.min(gs, rectPointDist(r, s) - R);
    const gc = grid.rectNear(r, self, 20 + cand.own) - cand.own;
    const mid = grid.rectNear({ x: r.cx, y: r.cy }, self, 26 + cand.ownC) - cand.ownC;
    return cand.bias + 2 * Math.min(gc, 20) + Math.min(mid, 26) + 0.5 * Math.min(gs, 16) - (gs < 2 ? 1000 : 0) - (mid < OWN + 3 ? 300 : 0) - (gc < 6 ? 80 : 0);
  }
  const spots = (item, states, grid, off) => candidates(item.route, item.box, off).map((cand) => { const r = boxAt(cand.c, item.box); return { r, fixed: fixedScore(r, cand, item.self, states, grid) }; });

  function place(item, placed, states, grid) {
    for (;;) {
      let best = null;
      for (const sp of item.spots) {
        if (best && sp.fixed + 8 <= best.sc) continue;
        let gl = 40;
        for (const q of placed) gl = Math.min(gl, rectGap(sp.r, q));
        const sc = sp.fixed + 0.5 * Math.min(gl, 16) - (gl < LABEL_PAD ? 500 : 0);
        if (!best || sc > best.sc) best = { r: sp.r, sc };
      }
      if (best.sc > -150 || item.full) return best.r;
      item.full = true;                 // אין מקום טוב על הקשת: מוסיפים את המקומות שלידה
      item.spots = item.spots.concat(spots(item, states, grid, true));
    }
  }

  /**
   * ממקם את כל התוויות. items: [{ route, self: המספר של הקשת ברשת, box: {w,h}, length }].
   * קודם לולאות (יש להן פחות מקום לזוז), אחר כך מהקשת הקצרה לארוכה. בסבב השני כל תווית ממוקמת שוב מול כל האחרות.
   */
  function placeAll(items, states, grid) {
    const order = items.slice().sort((x, y) => (y.route.loop ? 1 : 0) - (x.route.loop ? 1 : 0) || x.length - y.length);
    for (const it of order) { it.spots = spots(it, states, grid, false); it.full = false; it.route.label = null; }
    for (let pass = 0; pass < 2; pass++) {
      for (const it of order) it.route.label = place(it, order.filter((o) => o !== it && o.route.label).map((o) => o.route.label), states, grid);
    }
  }

  /** כמה רחוקה ממרכז התווית הקשת האחרת הקרובה ביותר (מעבר לקשת של התווית עצמה). זה המדד שהבדיקות דורשות. */
  function margin(item, grid) {
    const r = item.route.label, p = item.route.pts;
    let own = Infinity;
    for (const q of sample(p, 40)) own = Math.min(own, Math.hypot(q.x - r.cx, q.y - r.cy));
    return grid.rectNear({ x: r.cx, y: r.cy }, item.self, 60 + own) - own;
  }
  /** המרווח הכי גדול שאפשר לתת לתווית על הקשת pts: { d: המרחק מקשתות אחרות בנקודה הכי פנויה שלה, c: הנקודה }. */
  function room(pts, self, grid) {
    let best = { d: -1, c: null };
    for (let i = 0; i <= 32; i++) { const c = cubicAt(pts, 0.1 + i * 0.025), d = grid.rectNear(c, self, 30); if (d > best.d) best = { d, c }; }
    return best;
  }

  /** המקום הראשון של התווית של לולאה, והציון שלו (לבחירת הכיוון של הלולאה). מחזיר { r, score }. */
  const loopSpot = (route, box, self, states, grid) => { const sp = spots({ route, box, self }, states, grid, false)[0]; return { r: sp.r, score: sp.fixed }; };

  return { SEP, OWN, Grid, cubicAt, sample, placeAll, loopSpot, margin, room, rectPointDist, rectHitsCircle, rectsOverlap };
});
