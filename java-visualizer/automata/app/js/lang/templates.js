/*
 * lang/templates.js - התבניות של עורך הנוסחה. כל תבנית: שם, הנוסחה כמו שמקלידים אותה, ושם קצר בעברית (he) לרשימה.
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
    { name: 'aⁿbⁿ', text: 'a^n b^n', he: 'מספר שווה, a לפני b', cls: 'cf', builds: 'pda' },
    { name: 'aⁿbⁿ עם n ≥ 1', text: 'a^n b^n | n ≥ 1', he: 'מספר שווה, לא ריקה', cls: 'cf', builds: 'pda' },
    { name: 'aⁿbⁿ עם n ≤ 3', text: 'a^n b^n | n ≤ 3', he: 'מספר שווה, עד 3', cls: 'regular', builds: 'dfa' },
    { name: 'aⁿbᵐ עם n ≥ 2', text: 'a^n b^m | n >= 2', he: 'לפחות שני a', cls: 'regular', builds: 'dfa' },
    { name: 'מספר זוגי של a', text: 'w ∈ {a,b}* | #a(w) זוגי', he: 'מספר זוגי של a', cls: 'regular', builds: 'dfa' },
    { name: 'aⁿ עם n%3=0', text: 'a^n | n%3=0', he: 'מספר a שמתחלק ב-3', cls: 'regular', builds: 'dfa' },
    { name: 'aⁿbᵐ עם n+m זוגי', text: 'a^n b^m | (n+m)%2=0', he: 'אורך זוגי', cls: 'regular', builds: 'dfa' },
    { name: 'מסתיימת ב-ab', text: 'w ∈ {a,b}* | מסתיימת ב-ab', he: 'מסתיימת ב-ab', cls: 'regular', builds: 'dfa' },
    { name: 'מכילה aba', text: 'w ∈ {a,b}* | מכילה aba', he: 'מכילה aba', cls: 'regular', builds: 'dfa' },
    { name: 'אורך שמתחלק ב-3', text: 'w ∈ {a,b}* | |w| mod 3 = 0', he: 'אורך שמתחלק ב-3', cls: 'regular', builds: 'dfa' },
    { name: 'לא מכילה aa', text: 'w ∈ {a,b}* | לא מכילה aa', he: 'לא מכילה aa', cls: 'regular', builds: 'dfa' },
    { name: 'ביטוי רגולרי', text: '(a|b)*abb', he: 'ביטוי רגולרי: מסתיימת ב-abb', cls: 'regular', builds: 'dfa' },
    { name: 'aⁿb²ⁿ', text: 'a^n b^2n', he: 'פי שניים b', cls: 'cf', builds: 'pda' },
    { name: 'aⁿbᵐ עם n < m', text: 'a^n b^m | n < m', he: 'יותר b מ-a', cls: 'cf', builds: 'pda' },
    { name: 'aⁿbᵐ עם n ≠ m', text: 'a^n b^m | n != m', he: 'מספר שונה', cls: 'cf', builds: 'pda' },
    { name: 'aⁿbᵐcᵐdⁿ (מקונן)', text: 'a^n b^m c^m d^n', he: 'מקונן', cls: 'cf', builds: 'pda' },
    { name: 'aⁿbᵐcⁿ⁺ᵐ', text: 'a^n b^m c^(n+m)', he: 'c כמו a ו-b יחד', cls: 'cf', builds: 'pda' },
    { name: 'aⁿ⁺ᵐbⁿ', text: 'a^(n+m) b^n', he: 'לפחות כמו b', cls: 'cf', builds: 'pda' },
    { name: 'aⁿbⁿ ∪ bⁿaⁿ', text: 'a^n b^n ∪ b^n a^n', he: 'שני הסדרים', cls: 'cf', builds: 'pda' },
    { name: 'פלינדרום זוגי wwᴿ', text: 'w w^R', he: 'פלינדרום זוגי', cls: 'cf', builds: 'pda' },
    { name: 'סוגריים מאוזנים', text: 'w ∈ {(,)}* | מאוזנת', he: 'סוגריים מאוזנים', cls: 'cf', builds: 'pda' },
    { name: 'אותו מספר a ו-b', text: 'w ∈ {a,b}* | #a(w) = #b(w)', he: 'אותו מספר a ו-b', cls: 'cf', builds: null },
    { name: 'aⁿbⁿcⁿ', text: 'a^n b^n c^n', he: 'שלושה שווים', cls: 'noncf', builds: 'tm' },
    { name: 'אותה מילה פעמיים: w#w', text: 'w#w', he: 'אותה מילה פעמיים', cls: 'noncf', builds: null },
    { name: 'aⁿ כש-n חזקה של 2', text: 'a^(2^n)', he: 'אורך שהוא חזקה של 2', cls: 'noncf', builds: null },
  ];

  /** הקבוצות של רשימת התבניות בעורך (סבב 14), לפי הסדר. כל תבנית בקבוצה אחת */
  const GROUPS = ['רגולריות', 'חסרות הקשר', 'עם תנאים (≥, <, ≠, %)', 'איחוד ושרשור', 'לא חסרות הקשר / מכונת טיורינג'];
  function groupOf(t) {
    if (/∪|\s·\s/.test(t.text)) return GROUPS[3];
    if (t.cls === 'noncf') return GROUPS[4];
    if (/\^/.test(t.text) && /[|:]/.test(t.text) && /≥|≤|>|<|≠|!=|%|mod/.test(t.text)) return GROUPS[2];
    return t.cls === 'regular' ? GROUPS[0] : GROUPS[1];
  }
  L.TEMPLATE_GROUPS = GROUPS;
  L.templateGroups = () => GROUPS.map((title) => ({ title, items: L.TEMPLATES.map((t, i) => Object.assign({ i }, t)).filter((t) => groupOf(t) === title) })).filter((g) => g.items.length);
});
