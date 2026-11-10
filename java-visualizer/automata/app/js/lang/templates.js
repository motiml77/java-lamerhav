/*
 * lang/templates.js - גלריית התבניות של לשונית "שפה". כל תבנית: שם בעברית והנוסחה כמו שמקלידים אותה.
 * הבדיקה test/lang-build.test.js עוברת על כולן: סיווג, בנייה (אם נתמכת), והשוואה לבדיקת השייכות.
 */
(function (root, f) {
  const node = typeof module === 'object' && module.exports;
  const L = node ? require('./ns.js') : root.AutomataLang;
  f(L);
  if (node) module.exports = L;
})(typeof globalThis !== 'undefined' ? globalThis : this, function (L) {
  'use strict';
  L.TEMPLATES = [
    { name: 'aⁿbⁿ', text: 'a^n b^n', cls: 'cf', builds: 'pda' },
    { name: 'aⁿbᵐ עם n ≥ 2', text: 'a^n b^m | n >= 2', cls: 'regular', builds: 'dfa' },
    { name: 'מספר זוגי של a', text: 'w ∈ {a,b}* | #a(w) זוגי', cls: 'regular', builds: 'dfa' },
    { name: 'מסתיימת ב-ab', text: 'w ∈ {a,b}* | מסתיימת ב-ab', cls: 'regular', builds: 'dfa' },
    { name: 'מכילה aba', text: 'w ∈ {a,b}* | מכילה aba', cls: 'regular', builds: 'dfa' },
    { name: 'אורך שמתחלק ב-3', text: 'w ∈ {a,b}* | |w| mod 3 = 0', cls: 'regular', builds: 'dfa' },
    { name: 'לא מכילה aa', text: 'w ∈ {a,b}* | לא מכילה aa', cls: 'regular', builds: 'dfa' },
    { name: 'ביטוי רגולרי', text: '(a|b)*abb', cls: 'regular', builds: 'dfa' },
    { name: 'aⁿb²ⁿ', text: 'a^n b^2n', cls: 'cf', builds: 'pda' },
    { name: 'aⁿbᵐ עם n < m', text: 'a^n b^m | n < m', cls: 'cf', builds: 'pda' },
    { name: 'aⁿbᵐ עם n ≠ m', text: 'a^n b^m | n != m', cls: 'cf', builds: 'pda' },
    { name: 'aⁿbᵐcᵐdⁿ (מקונן)', text: 'a^n b^m c^m d^n', cls: 'cf', builds: 'pda' },
    { name: 'אותו מספר a ו-b', text: 'w ∈ {a,b}* | #a(w) = #b(w)', cls: 'cf', builds: null },
    { name: 'aⁿbⁿcⁿ', text: 'a^n b^n c^n', cls: 'noncf', builds: 'tm' },
  ];
});
