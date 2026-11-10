/*
 * lang/lex.js - מפרק את הטקסט של השפה לאסימונים. מקבל גם ASCII (^, >=, !=, mod, #a) וגם סימנים (ⁿ, ≥, ≠, ∈, ∪).
 * כל אסימון: { t, v, pos, end, sp } (sp = היה רווח לפניו). שגיאה: LangError עם מיקום מדויק והצעה.
 */
(function (root, f) {
  const node = typeof module === 'object' && module.exports;
  const L = node ? require('./ns.js') : root.AutomataLang;
  f(L);
  if (node) module.exports = L;
})(typeof globalThis !== 'undefined' ? globalThis : this, function (L) {
  'use strict';
  const SUP = { '⁰': '0', '¹': '1', '²': '2', '³': '3', '⁴': '4', '⁵': '5', '⁶': '6', '⁷': '7', '⁸': '8', '⁹': '9', 'ⁿ': 'n', 'ᵐ': 'm', 'ᵏ': 'k', 'ⁱ': 'i', 'ʲ': 'j', 'ˡ': 'l', 'ᵖ': 'p', 'ᴿ': 'R', '⁺': '+', '⁻': '-', '⁽': '(', '⁾': ')' };
  const KEYWORDS = new Set(['mod', 'and', 'or', 'not', 'even', 'odd']);
  const SINGLE = { '≥': '>=', '≤': '<=', '≠': '!=', '=': '=', '<': '<', '>': '>', '+': '+', '-': '-', '−': '-', '–': '-', '*': '*', '%': 'mod', ',': ',', '(': '(', ')': ')', '{': '{', '}': '}', '∈': 'in', '∪': 'union', '∧': 'and', '&': 'and', '∨': 'or', '¬': 'not', '!': 'not', ':': ':', 'ε': 'eps', 'λ': 'eps', '∅': 'empty', 'Σ': 'sigma', '^': '^', '|': '|', '∣': '|', '.': 'dot', '·': 'cdot', '×': 'cdot', '∙': 'cdot', '≡': 'equiv' };
  const isHe = (c) => c >= 'א' && c <= 'ת';
  const isLat = (c) => (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z');
  const isDig = (c) => c >= '0' && c <= '9';

  function lex(text) {
    const out = [];
    let i = 0, sp = false;
    const push = (t, v, pos, end) => { out.push({ t, v, pos, end, sp }); sp = false; };
    while (i < text.length) {
      const c = text[i];
      if (/\s/.test(c) || c === '‎' || c === '‏') { sp = sp || /\s/.test(c); i++; continue; }
      const start = i;
      if (SUP[c] != null) { let v = ''; while (i < text.length && SUP[text[i]] != null) v += SUP[text[i++]]; push('sup', v, start, i); continue; }
      if (isDig(c)) { while (i < text.length && isDig(text[i])) i++; push('num', text.slice(start, i), start, i); continue; }
      if (isHe(c)) {
        while (i < text.length && (isHe(text[i]) || ((text[i] === '-' || text[i] === '־') && isHe(text[i + 1] || '') && i > start))) i++;
        push('he', text.slice(start, i).replace('־', '-'), start, i);
        continue;
      }
      if (isLat(c)) {
        while (i < text.length && isLat(text[i])) i++;
        const run = text.slice(start, i);
        if (KEYWORDS.has(run.toLowerCase())) push('kw', run.toLowerCase(), start, i);
        else for (let k = 0; k < run.length; k++) push('ch', run[k], start + k, start + k + 1);
        continue;
      }
      // |w|  ו-  |w|_a
      const len = /^[|∣]\s*w\s*[|∣]/.exec(text.slice(i));
      if (len) {
        i += len[0].length;
        const sub = /^_?([A-Za-z0-9])(?![A-Za-z0-9])/.exec(text[i] === '_' ? text.slice(i) : '');
        if (sub) { i += sub[0].length; push('cnt', sub[1], start, i); } else push('len', '|w|', start, i);
        continue;
      }
      // #a(w)  /  #a  /  #_a(w)
      if (c === '#') {
        const m = /^#_?([A-Za-z0-9])(\s*\(\s*w\s*\))?/.exec(text.slice(i));
        if (!m && /^#\s*w(?![A-Za-z(])/.test(text.slice(i))) { i++; push('ch', '#', start, i); continue; }   // w # w: כאן # היא אות
        if (!m) throw new L.LangError('אחרי # צריכה לבוא אות: כמה פעמים היא מופיעה במילה', i, i + 1, 'למשל #a(w)');
        i += m[0].length;
        push('cnt', m[1], start, i);
        continue;
      }
      if (c === '>' && text[i + 1] === '=') { i += 2; push('>=', '>=', start, i); continue; }
      if (c === '<' && text[i + 1] === '=') { i += 2; push('<=', '<=', start, i); continue; }
      if ((c === '!' && text[i + 1] === '=') || (c === '<' && text[i + 1] === '>')) { i += 2; push('!=', '!=', start, i); continue; }
      if (c === '=' && text[i + 1] === '=') { i += 2; push('=', '=', start, i); continue; }
      if (SINGLE[c]) { i++; const t = SINGLE[c]; push(t === 'mod' ? 'kw' : t, t, start, i); continue; }
      throw new L.LangError('התו ' + c + ' לא מוכר כאן', i, i + 1, 'אפשר להשתמש באותיות, מספרים, ^ לחזקה, ובסימנים שבמקלדת שמתחת לשדה');
    }
    return out;
  }

  /** מסיר עטיפה: "L = { ... }" או "{ ... }" שעוטף את הכול. מחזיר { text, offset }. */
  function unwrap(text) {
    let off = 0, s = text;
    const lead = /^\s*(L\s*=)?\s*/.exec(s)[0];
    s = s.slice(lead.length); off += lead.length;
    s = s.replace(/\s+$/, '');
    if (s[0] === '{' && s[s.length - 1] === '}') {
      let depth = 0, wraps = true;
      for (let i = 0; i < s.length; i++) {
        if (s[i] === '{') depth++;
        else if (s[i] === '}') { depth--; if (depth === 0 && i < s.length - 1) { wraps = false; break; } }
      }
      if (wraps) { s = s.slice(1, -1); off += 1; }
    }
    return { text: s, offset: off };
  }

  L.lex = lex;
  L.unwrap = unwrap;
  L.SUP = SUP;
});
