/*
 * geometry.js - חישובי הציור של הדיאגרמה: קשתות ישרות ומעוקלות, לולאות עצמיות, ראשי חץ ומיקום תוויות.
 * טהור (בלי DOM), כדי שאפשר יהיה לבדוק אותו ב-node --test.
 * מערכת הצירים היא של SVG: x ימינה, y למטה. זוויות ברדיאנים, 0 = ימינה, -π/2 = למעלה.
 */
(function (root, factory) {
  const api = factory();
  if (typeof module === 'object' && module.exports) module.exports = api;
  else root.AutomataGeometry = api;
})(typeof globalThis !== 'undefined' ? globalThis : this, function () {
  'use strict';

  const R = 26;            // רדיוס מצב
  const ARROW_L = 10;      // אורך ראש חץ
  const ARROW_W = 4.6;     // חצי רוחב ראש חץ
  const BEND = 28;         // כמה קשת דו-כיוונית מתעקלת (במרכזה)
  const CHAR_W = 7.5;      // רוחב תו mono ב-12.5px
  const LINE_H = 16;

  const sub = (a, b) => ({ x: a.x - b.x, y: a.y - b.y });
  const add = (a, b) => ({ x: a.x + b.x, y: a.y + b.y });
  const mul = (a, k) => ({ x: a.x * k, y: a.y * k });
  const len = (a) => Math.hypot(a.x, a.y);
  const norm = (a) => { const l = len(a) || 1; return { x: a.x / l, y: a.y / l }; };
  const perp = (a) => ({ x: -a.y, y: a.x });
  const dir = (ang) => ({ x: Math.cos(ang), y: Math.sin(ang) });
  const r1 = (n) => Math.round(n * 10) / 10;
  const pt = (p) => r1(p.x) + ' ' + r1(p.y);

  /** משולש ראש חץ שהקודקוד שלו ב-tip ומצביע בכיוון u. מחזיר מחרוזת points ל-<polygon>. */
  function arrowHead(tip, u) {
    u = norm(u);
    const n = perp(u);
    const base = sub(tip, mul(u, ARROW_L));
    const a = add(base, mul(n, ARROW_W));
    const b = sub(base, mul(n, ARROW_W));
    return [tip, a, b].map((p) => r1(p.x) + ',' + r1(p.y)).join(' ');
  }

  /** נקודה על בזייה ריבועית. */
  function quadAt(p0, c, p1, t) {
    const u = 1 - t;
    return { x: u * u * p0.x + 2 * u * t * c.x + t * t * p1.x, y: u * u * p0.y + 2 * u * t * c.y + t * t * p1.y };
  }

  /**
   * קשת בין שני מצבים שונים.
   * curved: true כשיש גם קשת בכיוון ההפוך; אז כל אחת מתעקלת לצד שלה (לפי כיוון הנסיעה), והן לא מתנגשות.
   * מחזיר { d, arrow, label: {x,y}, normal } - normal הוא הכיוון שאליו התווית "בורחת" מהקו.
   */
  function edgePath(a, b, curved, r) {
    r = r || R;
    const v = sub(b, a);
    const u = norm(v);
    const n = perp(u);                    // שמאל לכיוון הנסיעה במסך (y למטה) = "מעל" לקשת שהולכת ימינה
    if (!curved) {
      const p0 = add(a, mul(u, r));
      const tip = sub(b, mul(u, r));
      const p1 = sub(tip, mul(u, ARROW_L - 1));
      const mid = mul(add(p0, tip), 0.5);
      return { d: 'M' + pt(p0) + 'L' + pt(p1), arrow: arrowHead(tip, u), label: mid, normal: mul(n, -1), mid, tangent: u };
    }
    const m = mul(add(a, b), 0.5);
    const c = add(m, mul(n, -2 * BEND));  // נקודת בקרה: פי 2 מהעיקול הרצוי, כי הבזייה עוברת בחצי הדרך
    const p0 = add(a, mul(norm(sub(c, a)), r));
    const tip = add(b, mul(norm(sub(c, b)), r));
    const uEnd = norm(sub(tip, c));
    const p1 = sub(tip, mul(uEnd, ARROW_L - 1));
    const mid = quadAt(p0, c, tip, 0.5);
    return { d: 'M' + pt(p0) + 'Q' + pt(c) + ' ' + pt(p1), arrow: arrowHead(tip, uEnd), label: mid, normal: mul(n, -1), mid, tangent: u };
  }

  /** לולאה עצמית בזווית ang (ברירת מחדל למעלה). */
  function loopPath(s, ang, r) {
    r = r || R;
    if (ang == null) ang = -Math.PI / 2;
    const spread = 0.48, ctrl = 0.62, reach = 2.9 * r;
    const p0 = add(s, mul(dir(ang - spread), r));
    const tip = add(s, mul(dir(ang + spread), r));
    const c1 = add(s, mul(dir(ang - ctrl), reach));
    const c2 = add(s, mul(dir(ang + ctrl), reach));
    const uEnd = norm(sub(tip, c2));
    const p1 = sub(tip, mul(uEnd, ARROW_L - 1));
    const top = add(s, mul(dir(ang), r * 2.38));
    return { d: 'M' + pt(p0) + 'C' + pt(c1) + ' ' + pt(c2) + ' ' + pt(p1), arrow: arrowHead(tip, uEnd), label: top, normal: dir(ang), mid: top, tangent: perp(dir(ang)) };
  }

  /** חץ הכניסה של המצב ההתחלתי: מגיע משמאל (הדיאגרמה LTR). */
  function startArrow(s, r) {
    r = r || R;
    const tip = { x: s.x - r, y: s.y };
    const from = { x: s.x - r - 34, y: s.y };
    return { d: 'M' + pt(from) + 'L' + pt({ x: tip.x - ARROW_L + 1, y: tip.y }), arrow: arrowHead(tip, { x: 1, y: 0 }) };
  }

  /**
   * בוחר זווית ללולאה עצמית: הכיוון שהכי רחוק משכנים (מתוך 8 כיוונים), עם העדפה ללמעלה.
   * neighbors: נקודות של המצבים שמחוברים למצב (בכל כיוון).
   */
  function bestLoopAngle(s, neighbors) {
    const cands = [-90, -45, -135, 0, 180, 45, 135, 90].map((d) => d * Math.PI / 180);
    if (!neighbors.length) return cands[0];
    const angs = neighbors.map((p) => Math.atan2(p.y - s.y, p.x - s.x));
    let best = cands[0], bestScore = -1;
    for (const c of cands) {
      let minD = Infinity;
      for (const a of angs) { let d = Math.abs(c - a) % (2 * Math.PI); if (d > Math.PI) d = 2 * Math.PI - d; minD = Math.min(minD, d); }
      if (minD > bestScore + 1e-6) { bestScore = minD; best = c; }
    }
    return best;
  }

  /** גודל משוער של תווית (פונט mono, אז ההערכה מדויקת). lines: מערך שורות. */
  function labelBox(lines) {
    const w = Math.max(1, ...lines.map((l) => Array.from(l).length)) * CHAR_W + 12;
    const h = lines.length * LINE_H + 6;
    return { w, h };
  }

  /**
   * ממקם את מלבן התווית: על הקו בקשת ישרה (הרקע של התווית מסתיר את הקו),
   * ובלולאה, מחוץ ללולאה בכיוון שלה.
   */
  function placeLabel(shape, box, isLoop) {
    let c = shape.label;
    if (isLoop) {
      const n = shape.normal;
      c = add(c, { x: n.x * (box.w / 2 + 2), y: n.y * (box.h / 2 + 2) });
    }
    return { x: c.x - box.w / 2, y: c.y - box.h / 2, w: box.w, h: box.h, cx: c.x, cy: c.y };
  }

  /** נקודה על שפת מצב בכיוון נקודה אחרת (לידית החיבור). */
  function rimPoint(s, p, r) {
    r = r || R;
    const u = norm(sub(p, s));
    return add(s, mul(u, r));
  }

  function distance(a, b) { return len(sub(a, b)); }

  /** מרחק של נקודה p מהקטע a-b. */
  function segmentDistance(p, a, b) {
    const ab = sub(b, a), ap = sub(p, a);
    const l2 = ab.x * ab.x + ab.y * ab.y || 1;
    const t = Math.max(0, Math.min(1, (ap.x * ab.x + ap.y * ab.y) / l2));
    return len(sub(p, add(a, mul(ab, t))));
  }

  /** חיבור "גומי" בזמן גרירה ממצב אל נקודה. */
  function rubberBand(s, p, r) {
    r = r || R;
    const u = norm(sub(p, s));
    const p0 = add(s, mul(u, r));
    const tip = p;
    const p1 = sub(tip, mul(u, ARROW_L - 1));
    return { d: 'M' + pt(p0) + 'L' + pt(p1), arrow: arrowHead(tip, u) };
  }

  return { R, ARROW_L, BEND, edgePath, loopPath, startArrow, bestLoopAngle, labelBox, placeLabel, rimPoint, distance, segmentDistance, rubberBand, arrowHead, quadAt };
});
