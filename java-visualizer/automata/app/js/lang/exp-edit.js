/*
 * lang/exp-edit.js - עריכה של חזקה אחת בנוסחה (סבב 13): לחיצה על חזקה בתצוגה היפה (L.pretty(ast, { edit: true }) מסמן אותן ב-data-ex)
 * מוצאת את המקום שלה בטקסט (blocks[].src מהמפענח: at = כל החזקה כולל ^ או הסימנים העיליים, inner = הביטוי עצמו),
 * ומחליפה רק אותה. עובד עם a^n, a^(n+m), a^n+m, aⁿ⁺ᵐ, ובאיחוד ובשרשור (לכל חלק המיקומים שלו).
 */
(function (root, f) {
  const node = typeof module === 'object' && module.exports;
  const L = node ? require('./ns.js') : root.AutomataLang;
  f(L);
  if (node) module.exports = L;
})(typeof globalThis !== 'undefined' ? globalThis : this, function (L) {
  'use strict';
  /** כל החזקות שאפשר לערוך, לפי הסדר בטקסט: [{ at, inner, form }] */
  function exponents(ast) {
    const out = [];
    const walk = (a) => { if (!a) return; if (a.parts) a.parts.forEach(walk); if (a.blocks) for (const b of a.blocks) if (b.src) out.push(b.src); };
    walk(ast);
    return out.sort((x, y) => x.at[0] - y.at[0]);
  }
  /** החזקה שמתחילה במקום at (מה שכתוב ב-data-ex) */
  const exponentAt = (ast, at) => exponents(ast).find((s) => s.at[0] === +at) || null;

  /** הביטוי לעריכה, בכתיב רגיל: "n+m" (גם מ-ⁿ⁺ᵐ) */
  function exponentText(text, src) {
    const raw = String(text).slice(src.inner[0], src.inner[1]);
    return src.form === 'sup' ? Array.from(raw).map((c) => (L.SUP[c] != null ? L.SUP[c] : c)).join('') : raw.trim();
  }

  const TO_SUP = {};
  for (const k in L.SUP) if (!/[R()]/.test(L.SUP[k])) TO_SUP[L.SUP[k]] = k;
  /**
   * מחליף את החזקה בביטוי חדש. מספר / משתנה / מקדם ומשתנה (2n): ^x; כל השאר בסוגריים: ^(m+k+2).
   * חזקה שנכתבה בסימנים עיליים נשארת כך אם אפשר (ⁿ⁺ᵐ → ᵐ⁺ᵏ⁺²), אחרת ^( ).
   */
  function replaceExponent(text, src, expr) {
    const x = String(expr || '').trim().replace(/−/g, '-'), tight = x.replace(/\s+/g, '');   // רווחים בפנים נשמרים (n mod 2 צריך אותם)
    let body;
    if (src.form === 'sup' && tight && Array.from(tight).every((c) => TO_SUP[c])) body = Array.from(tight).map((c) => TO_SUP[c]).join('');
    else body = '^' + (/^(\d+|\d*[A-Za-z])$/.test(tight) ? tight : '(' + x + ')');
    return { text: String(text).slice(0, src.at[0]) + body + String(text).slice(src.at[1]), start: src.at[0], end: src.at[0] + body.length };
  }

  /** מחליף ובודק שהנוסחה החדשה נקראת. מחזיר { ok, text } או { ok: false, error } (ההודעה של המפענח) */
  function editExponent(text, ast, at, expr, opts) {
    const src = exponentAt(ast, at);
    if (!src) return { ok: false, error: 'לא מצאתי את החזקה בנוסחה' };
    if (!String(expr || '').trim()) return { ok: false, error: 'חסרה חזקה. למשל n, 2n+1 או m+k+2' };
    const r = replaceExponent(text, src, expr);
    try { L.parse(r.text, opts); } catch (e) { if (e instanceof L.LangError) return { ok: false, error: e.message + (e.hint ? '. ' + e.hint : '') }; throw e; }
    return { ok: true, text: r.text, start: r.start, end: r.end };
  }

  Object.assign(L, { exponents, exponentAt, exponentText, replaceExponent, editExponent });
});
