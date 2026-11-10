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
    { name: 'aⁿbⁿ עם n ≥ 1', text: 'a^n b^n | n ≥ 1', cls: 'cf', builds: 'pda' },
    { name: 'aⁿbⁿ עם n ≤ 3', text: 'a^n b^n | n ≤ 3', cls: 'regular', builds: 'dfa' },
    { name: 'aⁿbᵐ עם n ≥ 2', text: 'a^n b^m | n >= 2', cls: 'regular', builds: 'dfa' },
    { name: 'מספר זוגי של a', text: 'w ∈ {a,b}* | #a(w) זוגי', cls: 'regular', builds: 'dfa' },
    { name: 'aⁿ עם n%3=0', text: 'a^n | n%3=0', cls: 'regular', builds: 'dfa' },
    { name: 'aⁿbᵐ עם n+m זוגי', text: 'a^n b^m | (n+m)%2=0', cls: 'regular', builds: 'dfa' },
    { name: 'מסתיימת ב-ab', text: 'w ∈ {a,b}* | מסתיימת ב-ab', cls: 'regular', builds: 'dfa' },
    { name: 'מכילה aba', text: 'w ∈ {a,b}* | מכילה aba', cls: 'regular', builds: 'dfa' },
    { name: 'אורך שמתחלק ב-3', text: 'w ∈ {a,b}* | |w| mod 3 = 0', cls: 'regular', builds: 'dfa' },
    { name: 'לא מכילה aa', text: 'w ∈ {a,b}* | לא מכילה aa', cls: 'regular', builds: 'dfa' },
    { name: 'ביטוי רגולרי', text: '(a|b)*abb', cls: 'regular', builds: 'dfa' },
    { name: 'aⁿb²ⁿ', text: 'a^n b^2n', cls: 'cf', builds: 'pda' },
    { name: 'aⁿbᵐ עם n < m', text: 'a^n b^m | n < m', cls: 'cf', builds: 'pda' },
    { name: 'aⁿbᵐ עם n ≠ m', text: 'a^n b^m | n != m', cls: 'cf', builds: 'pda' },
    { name: 'aⁿbᵐcᵐdⁿ (מקונן)', text: 'a^n b^m c^m d^n', cls: 'cf', builds: 'pda' },
    { name: 'aⁿbᵐcⁿ⁺ᵐ', text: 'a^n b^m c^(n+m)', cls: 'cf', builds: 'pda' },
    { name: 'aⁿ⁺ᵐbⁿ', text: 'a^(n+m) b^n', cls: 'cf', builds: 'pda' },
    { name: 'aⁿbⁿ ∪ bⁿaⁿ', text: 'a^n b^n ∪ b^n a^n', cls: 'cf', builds: 'pda' },
    { name: 'פלינדרום זוגי wwᴿ', text: 'w w^R', cls: 'cf', builds: 'pda' },
    { name: 'סוגריים מאוזנים', text: 'w ∈ {(,)}* | מאוזנת', cls: 'cf', builds: 'pda' },
    { name: 'אותו מספר a ו-b', text: 'w ∈ {a,b}* | #a(w) = #b(w)', cls: 'cf', builds: null },
    { name: 'aⁿbⁿcⁿ', text: 'a^n b^n c^n', cls: 'noncf', builds: 'tm' },
    { name: 'אותה מילה פעמיים: w#w', text: 'w#w', cls: 'noncf', builds: null },
    { name: 'aⁿ כש-n חזקה של 2', text: 'a^(2^n)', cls: 'noncf', builds: null },
  ];
});
