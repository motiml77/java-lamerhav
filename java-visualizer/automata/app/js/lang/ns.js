/*
 * lang/ns.js - מרחב השמות של "שפה פורמלית" (AutomataLang). כל קובץ ב-lang/ מוסיף אליו פונקציות.
 * בדפדפן: globalThis.AutomataLang. ב-Node: require('./lang/index.js').
 */
(function (root) {
  'use strict';
  const L = { LangError: class LangError extends Error { constructor(msg, pos, end, hint) { super(msg); this.pos = pos; this.end = end == null ? pos + 1 : end; this.hint = hint || ''; } } };
  if (typeof module === 'object' && module.exports) module.exports = L;
  else root.AutomataLang = L;
})(typeof globalThis !== 'undefined' ? globalThis : this);
