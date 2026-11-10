/*
 * examples-fa.js - המפרט של הדוגמאות לאוטומטים סופיים (אס"ד, אסל"ד). את המכונות עצמן בונה examples.js.
 * כל דוגמה:
 *   lang   - השפה כנוסחה, בדיוק כמו שמקלידים בלשונית "שפה"
 *   desc   - תיאור של שורה אחת, במילים
 *   words  - מילים לבדיקה, מהקצרה והמלמדת ביותר. התוצאה הצפויה לא נכתבת כאן: היא מחושבת מהנוסחה
 *   states - [שם, x, y, 'A' אם מקבל, זווית הלולאה במעלות]. המצב הראשון הוא ההתחלתי
 *   edges  - [ממצב, למצב, האותיות]
 */
(function (root, factory) {
  const list = factory();
  if (typeof module === 'object' && module.exports) module.exports = list;
  else (root.AutomataExampleSpecs = root.AutomataExampleSpecs || []).push(...list);
})(typeof globalThis !== 'undefined' ? globalThis : this, function () {
  'use strict';
  return [
    /* ---------- אס"ד ---------- */
    {
      id: 'even-a', type: 'dfa', name: 'מספר זוגי של a', alphabet: 'ab',
      lang: 'w ∈ {a,b}* | #a(w) זוגי',
      desc: 'מספר ה-a במילה זוגי (גם 0). ה-b לא משנה.',
      words: ['', 'a', 'aa', 'ab', 'bab', 'abba', 'baaba'], sample: 'abbab',
      states: [['q0', -110, 0, 'A'], ['q1', 110, 0]],
      edges: [['q0', 'q1', 'a'], ['q1', 'q0', 'a'], ['q0', 'q0', 'b'], ['q1', 'q1', 'b']],
    },
    {
      id: 'ends-ab', type: 'dfa', name: 'מסתיימת ב-ab', alphabet: 'ab',
      lang: 'w ∈ {a,b}* | מסתיימת ב-ab',
      desc: 'שתי האותיות האחרונות במילה הן ab.',
      words: ['ab', 'aab', 'babab', '', 'a', 'ba', 'abb', 'abab'], sample: 'abaab',
      states: [['q0', -220, 0], ['q1', 0, 0], ['q2', 220, 0, 'A']],
      edges: [['q0', 'q0', 'b'], ['q0', 'q1', 'a'], ['q1', 'q1', 'a'], ['q1', 'q2', 'b'], ['q2', 'q1', 'a'], ['q2', 'q0', 'b']],
    },
    {
      id: 'has-aba', type: 'dfa', name: 'מכילה aba', alphabet: 'ab',
      lang: 'w ∈ {a,b}* | מכילה aba',
      desc: 'הרצף aba מופיע במילה, בכל מקום שהוא.',
      words: ['aba', 'baba', 'aabab', 'abba', '', 'ab', 'abb', 'bbabaa'], sample: 'babab',
      states: [['q0', -300, 0], ['q1', -100, 0], ['q2', 100, 0], ['q3', 300, 0, 'A']],
      edges: [['q0', 'q0', 'b'], ['q0', 'q1', 'a'], ['q1', 'q1', 'a'], ['q1', 'q2', 'b'], ['q2', 'q3', 'a'], ['q2', 'q0', 'b'], ['q3', 'q3', 'a,b']],
    },
    {
      id: 'len-mod3', type: 'dfa', name: 'אורך שמתחלק ב-3', alphabet: 'ab',
      lang: 'w ∈ {a,b}* | |w| mod 3 = 0',
      desc: 'אורך המילה מתחלק ב-3. האותיות עצמן לא משנות.',
      words: ['', 'aba', 'bbabab', 'a', 'ab', 'abab', 'aaaaa'], sample: 'abbaba',
      states: [['q0', -170, 0, 'A'], ['q1', 60, -110], ['q2', 60, 110]],
      edges: [['q0', 'q1', 'a,b'], ['q1', 'q2', 'a,b'], ['q2', 'q0', 'a,b']],
    },
    {
      id: 'no-bb', type: 'dfa', name: 'לא מכילה bb', alphabet: 'ab',
      lang: 'w ∈ {a,b}* | לא מכילה bb',
      desc: 'אין במילה שני b רצופים. אחרי bb האוטומט נתקע במלכודת.',
      words: ['', 'b', 'ab', 'bab', 'bb', 'abb', 'abba', 'babab'], sample: 'ababb',
      states: [['q0', -220, 0, 'A'], ['q1', 0, 0, 'A'], ['q2', 220, 0]],
      edges: [['q0', 'q0', 'a'], ['q0', 'q1', 'b'], ['q1', 'q0', 'a'], ['q1', 'q2', 'b'], ['q2', 'q2', 'a,b']],
    },

    /* ---------- אסל"ד ---------- */
    {
      id: 'third-last-a', type: 'nfa', name: 'השלישית מהסוף היא a', alphabet: 'ab',
      lang: '(a+b)*a(a+b)(a+b)',
      desc: 'האות השלישית מהסוף היא a. האסל"ד מנחש מתי היא הגיעה.',
      words: ['aaa', 'abb', 'babb', 'aab', '', 'ab', 'bbb', 'abbb'], sample: 'abaab',
      states: [['q0', -300, 0], ['q1', -100, 0], ['q2', 100, 0], ['q3', 300, 0, 'A']],
      edges: [['q0', 'q0', 'a,b'], ['q0', 'q1', 'a'], ['q1', 'q2', 'a,b'], ['q2', 'q3', 'a,b']],
    },
    {
      id: 'ab-or-ba', type: 'nfa', name: 'מכילה ab או ba', alphabet: 'ab',
      lang: 'w ∈ {a,b}* | מכילה ab או מכילה ba',
      desc: 'מופיע במילה ab או ba. כלומר, יש בה גם a וגם b.',
      words: ['ab', 'ba', 'aab', 'bba', '', 'a', 'aaa', 'bb'], sample: 'aaba',
      states: [['q0', -240, 0], ['q1', 0, -100], ['q2', 0, 100], ['q3', 240, 0, 'A']],
      edges: [['q0', 'q0', 'a,b'], ['q0', 'q1', 'a'], ['q1', 'q3', 'b'], ['q0', 'q2', 'b'], ['q2', 'q3', 'a'], ['q3', 'q3', 'a,b']],
    },
    {
      id: 'only-a-or-b', type: 'nfa', name: 'רק a או רק b (עם ε)', alphabet: 'ab',
      lang: 'a* + b*',
      desc: 'כל המילה a, או כל המילה b. מעברי ε בוחרים באיזה מסלול ללכת.',
      words: ['', 'a', 'bb', 'aaa', 'ab', 'ba', 'aab'], sample: 'aaa',
      states: [['q0', -150, 0], ['q1', 60, -90, 'A', 0], ['q2', 60, 90, 'A', 0]],
      edges: [['q0', 'q1', 'ε'], ['q0', 'q2', 'ε'], ['q1', 'q1', 'a'], ['q2', 'q2', 'b']],
    },
    {
      id: 'same-ends', type: 'nfa', name: 'מתחילה ומסתיימת באותה אות', alphabet: 'ab',
      lang: 'a(a+b)*a + b(a+b)*b + a + b',
      desc: 'האות הראשונה והאות האחרונה שוות. גם מילה של אות אחת.',
      words: ['a', 'b', 'aa', 'aba', 'babb', '', 'ab', 'ba', 'abab'], sample: 'abba',
      states: [['q0', -240, 0], ['q1', 0, -110], ['q2', 0, 110], ['q3', 240, 0, 'A']],
      edges: [['q0', 'q1', 'a'], ['q1', 'q1', 'a,b'], ['q1', 'q3', 'a'], ['q0', 'q2', 'b'], ['q2', 'q2', 'a,b'], ['q2', 'q3', 'b'], ['q0', 'q3', 'a,b']],
    },
    {
      id: 'ab-aba-star', type: 'nfa', name: '(ab ∪ aba)*', alphabet: 'ab',
      lang: '(ab + aba)*',
      desc: 'שרשור של ab ושל aba, כמה פעמים שרוצים (גם אפס).',
      words: ['', 'ab', 'aba', 'abab', 'abaab', 'a', 'ba', 'abb', 'abaa'], sample: 'ababa',
      states: [['q0', -180, 0, 'A'], ['q1', 40, -100], ['q2', 40, 100]],
      edges: [['q0', 'q1', 'a'], ['q1', 'q0', 'b'], ['q1', 'q2', 'b'], ['q2', 'q0', 'a']],
    },

    /* הדגמת ניתוב: בלי נוסחה. לא חלק מחמש הדוגמאות של האסל"ד. */
    {
      id: 'jumps', type: 'nfa', name: 'קפיצות מעל מצבים', alphabet: 'ab', demo: true,
      desc: 'הדגמה של ניתוב קשתות: חמישה מצבים בשורה, ומעברים שמדלגים מעל האמצעיים.',
      tests: [['bb', true], ['aab', true], ['abab', true], ['', false], ['ab', false], ['abba', false]], sample: 'aabab',
      states: [['q0', -320, 0], ['q1', -160, 0], ['q2', 0, 0], ['q3', 160, 0], ['q4', 320, 0, 'A']],
      edges: [['q0', 'q1', 'a'], ['q1', 'q2', 'b'], ['q2', 'q3', 'a'], ['q3', 'q4', 'b'], ['q0', 'q2', 'b'],
        ['q1', 'q3', 'a'], ['q2', 'q4', 'b'], ['q4', 'q0', 'a'], ['q3', 'q1', 'b'], ['q4', 'q2', 'a']],
    },
  ];
});
