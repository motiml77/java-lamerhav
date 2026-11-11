/*
 * examples.js - המכונות לדוגמה: חמש לכל סוג מכונה, ועוד הדגמת ניתוב אחת.
 * המפרט הקצר נמצא ב-examples-fa.js וב-examples-pt.js. כאן הוא הופך לקובץ מכונה רגיל (אותו מבנה כמו שמירה ל-JSON):
 *   - לכל דוגמה יש שפה (machine.language: הנוסחה ותיאור במילים), והיא מוצגת בפס שמעל הקנבס
 *   - התוצאה הצפויה של כל מילה בטבלת הבדיקות מחושבת מהנוסחה (AutomataLang.accepts), לא נכתבת ביד
 * הבדיקה test/examples.test.js מוודאת שכל מכונה מקבלת בדיוק את השפה של הנוסחה שלה, על כל המילים עד אורך 10.
 */
(function (root, factory) {
  const node = typeof module === 'object' && module.exports;
  const specs = node ? require('./examples-fa.js').concat(require('./examples-pt.js')) : root.AutomataExampleSpecs;
  const api = factory(specs, root.AutomataModel || (node ? require('./model.js') : null), root.AutomataLang || (node ? require('./lang/index.js') : null));
  if (node) module.exports = api;
  else root.AutomataExamples = api;
})(typeof globalThis !== 'undefined' ? globalThis : this, function (SPECS, M, L) {
  'use strict';
  const uniq = (list) => Array.from(new Set(list));

  function make(s) {
    const alphabet = Array.from(s.alphabet);
    const m = {
      format: 'dsviz-automaton', version: 1, id: s.id, type: s.type, name: s.name, description: s.desc, alphabet,
      states: s.states.map(([id, x, y, flags, loop]) => Object.assign({ id, name: id, x, y, accepting: /A/.test(flags || '') }, loop != null ? { loopAngle: loop } : {})),
      start: s.states[0][0],
      transitions: s.edges.map(([from, to, text], i) => {
        const r = M.parseLabels(s.type, text);
        if (r.errors.length) throw new Error('דוגמה ' + s.id + ': ' + r.errors[0]);
        return { id: 't' + (i + 1), from, to, labels: r.labels };
      }),
      view: { x: 0, y: 0, zoom: 1 }, sampleWord: s.sample || '', demo: !!s.demo,
    };
    const labels = m.transitions.flatMap((t) => t.labels);
    if (s.type === 'pda') Object.assign(m, { stackAlphabet: uniq(['Z'].concat(labels.flatMap((l) => Array.from(l.pop + l.push)))), stackStart: 'Z', acceptBy: 'final' });
    if (s.type === 'tm') Object.assign(m, { blank: '_', tapeAlphabet: uniq(alphabet.concat(labels.flatMap((l) => [l.read, l.write]), ['_'])) });
    if (s.lang) {
      const ast = L.parse(s.lang, { alphabet });
      m.language = { text: s.lang, desc: s.desc };
      m.tests = s.words.map((w) => ({ word: w, expect: L.accepts(ast, w) }));
    } else m.tests = (s.tests || []).map(([word, expect]) => ({ word, expect }));
    return m;
  }

  const EXAMPLES = SPECS.map(make);
  return { EXAMPLES, get: (id) => EXAMPLES.find((e) => e.id === id) || null };
});
