/*
 * routing.js - ניתוב קשתות ומיקום תוויות. טהור (בלי DOM), נבדק ב-test/routing.test.js.
 *
 * כל קשת היא בזייה מעוקבת: נקודות הבקרה על שליש ושני שליש מהקו, מוזזות הצידה ב-h1 / h2 (קשת ישרה: h1 = h2 = 0).
 * לכל קשת בוחרים את העיקול הזול ביותר מתוך רשימת מועמדים. המחיר מורכב מ:
 *   1. גודל העיקול. קשת חייבת לעבור במרחק של לפחות R + MARGIN מכל מצב שאינו קצה שלה.
 *   2. "מסדרון משותף": כל קטע שבו הקשת רצה קרוב (פחות מ-SEP) לקשת אחרת עולה ביוקר. לכן קשתות שמתכנסות לאותו
 *      מצב או רצות יחד מקבלות עיקולים שונים בבירור, והארוכה מתעקלת יותר (הקצרות מנותבות ראשונות).
 * קשתות הפוכות (a→b וגם b→a) מתעקלות כל אחת לשמאל כיוון הנסיעה. אחרי הסבב הראשון יש עוד שני סבבי שיפור,
 * שבהם כל קשת מנותבת מחדש מול כל האחרות. אחר כך לולאות, ובסוף תוויות (ב-routing-labels.js).
 */
(function (root, factory) {
  const node = typeof module === 'object' && module.exports;
  const api = factory(root.AutomataGeometry || (node ? require('./geometry.js') : null), root.AutomataRoutingLabels || (node ? require('./routing-labels.js') : null));
  if (node) module.exports = api;
  else root.AutomataRouting = api;
})(typeof globalThis !== 'undefined' ? globalThis : this, function (G, LB) {
  'use strict';

  const R = G.R;
  const MARGIN = 8;          // המרחק המובטח (מעבר לרדיוס) בין קשת למצב שאינו קצה שלה
  const SAFE = MARGIN + 4;   // הנתב שומר על מרווח קצת יותר גדול, כדי שדגימה צפופה יותר עדיין תעבור
  const PAIR_H = 37;         // עיקול בסיסי לזוג קשתות הפוכות (שיא של כ-28px)
  const SEP = LB.SEP;        // קשתות שקרובות מזה זו לזו "חולקות מסדרון" (בערך גובה של תווית)
  const SEP_W = 4;           // המחיר של פיקסל אחד במסדרון משותף (משוקלל לפי הקרבה)

  const sub = (a, b) => ({ x: a.x - b.x, y: a.y - b.y });
  const add = (a, b) => ({ x: a.x + b.x, y: a.y + b.y });
  const mul = (a, k) => ({ x: a.x * k, y: a.y * k });
  const len = (a) => Math.hypot(a.x, a.y);
  const norm = (a) => { const l = len(a) || 1; return { x: a.x / l, y: a.y / l }; };
  const r1 = (n) => Math.round(n * 10) / 10;
  const pt = (p) => r1(p.x) + ' ' + r1(p.y);
  const { cubicAt, sample } = LB;

  /* מועמדים לעיקול: [h1, h2] (חיובי = שמאלה לכיוון הנסיעה), ממוינים לפי "מחיר". שתי נקודות הבקרה ברמות סמוכות, כדי שהקשת תהיה חלקה. */
  const LEVELS = [0, 30, 37, 50, 75, 100, 130, 170, 220, 280, 350];
  const CANDIDATES = (() => {
    const out = [];
    for (const s1 of [1, -1]) for (const s2 of [1, -1]) LEVELS.forEach((a, i) => LEVELS.forEach((b, j) => {
      if ((a === 0 && s1 < 0) || (b === 0 && s2 < 0)) return;
      const sCurve = s1 * a * s2 * b < 0;
      if (Math.abs(i - j) > (sCurve ? 1 : 2)) return;
      out.push({ h1: s1 * a, h2: s2 * b, cost: Math.max(a, b) + 0.4 * Math.abs(a - b) + (sCurve ? 80 : 0) });
    }));
    return out.sort((x, y) => x.cost - y.cost);
  })();
  const LONG = 320;          // מעל האורך הזה מחיר העיקול יורד (לפי שורש היחס)
  const ROUNDS = 3;          // כמה סבבי תיקון לכל היותר, כשיש תווית בלי מקום ברור
  const KEEP_WIDE = 60;       // ובסבבי התיקון, לקשת שאין עליה מקום לתווית
  const KEEP = 22;           // כמה עיקולים אפשריים שוקלים לכל קשת

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

  const approxLen = (p) => len(sub(p.c1, p.p0)) + len(sub(p.c2, p.c1)) + len(sub(p.p3, p.c2));
  const samplesFor = (shape, step) => sample(shape.pts, Math.max(20, Math.ceil(approxLen(shape.pts) / (step || 11))));

  /** האורך (בפיקסלים, משוקלל לפי הקרבה) של החלקים בקשת שקרובים לקשת אחרת: המסדרון המשותף. */
  function shared(smp, grid, self, limit) {
    const pt = { x: 0, y: 0 };
    let s = 0;
    for (let i = 1; i < smp.length; i++) {
      pt.x = smp[i].x; pt.y = smp[i].y;
      const d = grid.rectNear(pt, self, SEP);
      if (d < SEP) { const k = 1 - d / SEP, q = smp[i - 1]; s += k * k * Math.hypot(pt.x - q.x, pt.y - q.y); if (s > limit) return s; }
    }
    return s;
  }

  /** העיקולים האפשריים של קשת a→b (עוברים רחוק מספיק מכל מצב), מהזול ליקר: [{ shape, smp, base }]. */
  function options(a, b, obstacles, paired, keep) {
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
    const scale = Math.sqrt(LONG / Math.max(L, LONG));      // לקשת ארוכה עיקול גדול זול יותר: היא זו שמתרחקת, והקצרות נשארות צמודות
    const out = [];
    let fallback = null;
    for (const c of CANDIDATES) {
      if (paired && (c.h1 < PAIR_H || c.h2 < PAIR_H)) continue;
      const shape = shapeEdge(a, b, c.h1, c.h2);
      if (near.length && clearance([cubicAt(shape.pts, 0.5), cubicAt(shape.pts, 0.25), cubicAt(shape.pts, 0.75)], near) < SAFE && fallback) continue;
      const smp = samplesFor(shape);
      const cl = near.length ? clearance(smp, near) : Infinity;
      if (cl < SAFE) { if (!fallback || cl > fallback.cl) fallback = { shape, smp, base: 0, cl }; continue; }
      out.push({ shape, smp, base: c.cost * scale + ((c.h1 + c.h2) >= 0 ? leftCount : rightCount) * 20 });
      if (out.length >= keep) break;
    }
    // אין עיקול שעובר נקי: לוקחים את זה עם המרווח הכי גדול
    return out.length ? out.sort((x, y) => x.base - y.base) : [fallback];
  }

  /** בוחר מתוך האפשרויות את הזולה ביותר, כולל המחיר של מסדרון משותף עם הקשתות שכבר ברשת. */
  function pick(opts, grid, self) {
    let best = null;
    for (const o of opts) {
      if (best && o.base >= best.total) break;
      const total = o.base + SEP_W * shared(o.smp, grid, self, best ? (best.total - o.base) / SEP_W : Infinity);
      if (!best || total < best.total) best = { shape: o.shape, total };
    }
    return best.shape;
  }

  function angleDist(x, y) { let d = Math.abs(x - y) % (2 * Math.PI); return d > Math.PI ? 2 * Math.PI - d : d; }

  /** לולאה: בכיוון הכי פנוי (רחוק ממצבים, מקשתות אחרות, ועם מקום לתווית). מחזיר { shape, spot: המלבן של התווית }. */
  function routeLoop(s, states, incident, fixed, grid, self, box) {
    const obstacles = states.filter((q) => q !== s);
    const spot = (sh) => LB.loopSpot({ pts: sh.pts, loop: true, angle: sh.angle }, box, self, states, grid);
    if (fixed != null) { const sh = shapeLoop(s, fixed); return { shape: sh, spot: spot(sh).r }; }
    let best = null;
    for (let k = 0; k < 16; k++) {
      const ang = -Math.PI / 2 + (k * Math.PI) / 8;
      const sh = shapeLoop(s, ang);
      const smp = samplesFor(sh);
      const cl = obstacles.length ? clearance(smp, obstacles) : Infinity;
      const spread = incident.length ? Math.min(...incident.map((d) => angleDist(d, ang))) : Math.PI;
      const sp = spot(sh);
      const score = (cl < SAFE ? -1000 + cl : 0) + spread * 40 - (k === 0 ? 0 : 3) - Math.min(k, 16 - k) * 0.5 - shared(smp, grid, self, Infinity) + 0.5 * sp.score;
      if (!best || score > best.score) best = { shape: sh, score, spot: sp.r };
    }
    return best;
  }

  /** נקודות על המסגרת של מלבן (כדי שקשתות אחרות יתרחקו מהמקום של תווית של לולאה). */
  function outline(r) {
    const out = [];
    for (let x = r.x; x <= r.x + r.w; x += 6) out.push({ x, y: r.y }, { x, y: r.y + r.h });
    for (let y = r.y; y <= r.y + r.h; y += 6) out.push({ x: r.x, y }, { x: r.x + r.w, y });
    return out;
  }

  let memo = null;   // התוצאה האחרונה: ציור מחדש בלי שינוי בגאומטריה (ריחוף, הרצה) לא מנתב שוב

  /**
   * מנתב את כל הקשתות.
   * states: [{id, x, y, loopAngle?}] · edges: [{id, from, to, lines: [מחרוזות התווית]}] · opts.startId
   * opts.fast: בלי סבבי התיקון של התוויות (בזמן גרירה של מצב, כדי שהציור יישאר חלק גם במכונה צפופה)
   * מחזיר Map: id -> { d, arrow, loop, pts: {p0,c1,c2,p3}, label: {x,y,w,h,cx,cy}, angle? }
   */
  function route(states, edges, opts) {
    opts = opts || {};
    const key = JSON.stringify([states.map((s) => [s.id, s.x, s.y, s.loopAngle]), edges.map((e) => [e.id, e.from, e.to, e.lines]), opts.startId, !!opts.fast]);
    if (memo && memo.key === key) return memo.out;
    const byId = new Map(states.map((s) => [s.id, s]));
    const pairs = new Set(edges.map((e) => e.from + '>' + e.to));
    const index = new Map(edges.map((e, i) => [e.id, i]));
    const boxOf = (e) => G.labelBox(e.lines && e.lines.length ? e.lines : ['?']);
    const grid = new LB.Grid(states);
    const shapes = new Map();
    const put = (e, shape) => { shapes.set(e.id, shape); grid.put(index.get(e.id), samplesFor(shape, 6)); };
    const start = byId.get(opts.startId);
    if (start) grid.put(edges.length, sample({ p0: { x: start.x - R - 34, y: start.y }, c1: { x: start.x - R - 22, y: start.y }, c2: { x: start.x - R - 11, y: start.y }, p3: { x: start.x - R, y: start.y } }, 8));

    // קשתות בין מצבים שונים: מהקצרה לארוכה, ואז שני סבבי שיפור מול כל האחרות
    const straight = edges.filter((e) => e.from !== e.to && byId.has(e.from) && byId.has(e.to));
    straight.sort((x, y) => len(sub(byId.get(x.from), byId.get(x.to))) - len(sub(byId.get(y.from), byId.get(y.to))));
    const optionsOf = (e, keep) => {
      const a = byId.get(e.from), b = byId.get(e.to);
      return options(a, b, states.filter((s) => s !== a && s !== b), pairs.has(e.to + '>' + e.from), keep);
    };
    const choices = new Map(straight.map((e) => [e.id, optionsOf(e, KEEP)]));
    const wide = new Map();      // לתיקון: רשימה רחבה יותר של עיקולים, רק לקשתות שצריכות אותה
    const one = (e) => pick(choices.get(e.id), grid, index.get(e.id));
    for (const e of straight) put(e, one(e));
    // לולאות: בכיוון הכי פנוי. הן נכנסות לרשת לפני סבבי השיפור, כדי שהקשתות יתרחקו מהן ומהתוויות שלהן
    const loops = edges.filter((e) => e.from === e.to && byId.has(e.from));
    const routeLoops = () => {
      const incident = new Map(states.map((s) => [s.id, []]));
      if (start) incident.get(start.id).push(Math.PI);     // חץ הכניסה בא משמאל
      for (const e of straight) {
        const a = byId.get(e.from), b = byId.get(e.to), p = shapes.get(e.id).pts;
        incident.get(a.id).push(Math.atan2(p.p0.y - a.y, p.p0.x - a.x));
        incident.get(b.id).push(Math.atan2(p.p3.y - b.y, p.p3.x - b.x));
      }
      for (const e of loops) {
        const s = byId.get(e.from);
        const r = routeLoop(s, states, incident.get(s.id), s.loopAngle != null ? (s.loopAngle * Math.PI) / 180 : null, grid, index.get(e.id), boxOf(e));
        shapes.set(e.id, r.shape);
        grid.put(index.get(e.id), samplesFor(r.shape, 6).concat(outline(r.spot)));
      }
    };
    routeLoops();

    let stamp = 0;                                   // עולה בכל שינוי; קשת שלא השתנה דבר מאז שנבחרה לא נבדקת שוב
    const seen = new Map(straight.map((e, i) => [e.id, i === straight.length - 1 && !loops.length ? 0 : -1]));
    for (let sweep = 0; sweep < 2; sweep++) {
      const before = stamp;
      for (const e of straight) {
        if (seen.get(e.id) === stamp) continue;
        const old = shapes.get(e.id), sh = one(e);
        if (sh.h1 !== old.h1 || sh.h2 !== old.h2) { put(e, sh); stamp++; }
        seen.set(e.id, stamp);
      }
      if (stamp === before) break;
    }
    if (stamp && loops.length) routeLoops();           // הקשתות זזו: בוחרים שוב את כיווני הלולאות

    const out = new Map();
    const labels = () => {
      const list = edges.filter((e) => shapes.has(e.id)).map((e) => {
        const sh = shapes.get(e.id), loop = e.from === e.to;
        const r = { d: sh.d, arrow: sh.arrow, pts: sh.pts, loop };
        if (loop) r.angle = sh.angle;
        out.set(e.id, r);
        return { edge: e, route: r, self: index.get(e.id), box: boxOf(e), length: approxLen(sh.pts) };
      });
      LB.placeAll(list, states, grid);
      return list;
    };
    let list = labels();

    // תיקון: קשת שאין עליה אף מקום ברור לתווית (קשת אחרת צמודה אליה לכל האורך) מקבלת את העיקול שנותן לתווית הכי הרבה מרווח.
    // אחרי כל סבב בוחרים שוב את כיווני הלולאות, וממקמים את כל התוויות מחדש. בסוף נשארים עם הסבב הטוב ביותר.
    let kept = null;
    for (let round = 0; !opts.fast; round++) {
      const margins = list.map((it) => LB.margin(it, grid)), worst = Math.min(Infinity, ...margins);
      if (worst >= LB.OWN + 3 && !kept) break;                       // המקרה הרגיל: אין מה לתקן
      if (!kept || worst > kept.worst) kept = { worst, out: new Map(Array.from(out, ([id, r]) => [id, Object.assign({}, r)])) };
      if (worst >= LB.OWN + 3 || round === ROUNDS) break;
      list.forEach((it, i) => {
        if (margins[i] >= LB.OWN + 3) return;
        let spot = it.route.label;
        if (!it.route.loop) {
          if (!wide.has(it.edge.id)) wide.set(it.edge.id, optionsOf(it.edge, KEEP_WIDE));
          let best = null;
          for (const o of wide.get(it.edge.id)) {
            const rm = LB.room(o.shape.pts, it.self, grid);
            const val = Math.min(rm.d, 30) - 0.05 * o.base - 0.15 * shared(o.smp, grid, it.self, Infinity);
            if (!best || val > best.val) best = { o, val, c: rm.c };
          }
          // המקום שנבחר לתווית נכנס לרשת יחד עם הקשת, כדי שהקשתות האחרות יתרחקו ממנו
          spot = { x: best.c.x - it.box.w / 2, y: best.c.y - it.box.h / 2, w: it.box.w, h: it.box.h };
          shapes.set(it.edge.id, best.o.shape);
          grid.put(it.self, samplesFor(best.o.shape, 6).concat(outline(spot)));
        }
        // הקשתות שעוברות דרך המקום של התווית מנותבות מחדש
        for (const e of straight) if (e !== it.edge && sample(shapes.get(e.id).pts, 40).some((q) => LB.rectPointDist(spot, q) < LB.OWN + 8)) put(e, one(e));
      });
      if (loops.length) routeLoops();
      list = labels();
    }
    memo = { key, out: kept ? kept.out : out };
    return memo.out;
  }

  return { MARGIN, SEP, route, cubicAt, sample, shapeEdge, shapeLoop, rectsOverlap: LB.rectsOverlap, rectHitsCircle: LB.rectHitsCircle, rectPointDist: LB.rectPointDist };
});
