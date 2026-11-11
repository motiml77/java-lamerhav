/*
 * task.js - "המשימה" של לשונית (סבב 14): נוסחה של שפה, ומי בונה את המכונה. טהור (בלי DOM), נבדק ב-test/task.test.js.
 *
 * נשמרת במכונה, ב-machine.language (השם הישן נשאר, כדי שקבצים, דוגמאות ותרגילי המאגר ימשיכו לעבוד):
 *   { text,                       הנוסחה
 *     mode: 'auto' | 'practice' | 'solution',   התוכנה בונה / אני בונה והתוכנה בודקת / פתרון לדוגמה בחלון נפרד
 *     locked,                     הנוסחה נעולה (מאגר, קובץ משימה)
 *     desc,                       תיאור במילים (לא חובה)
 *     check: { status: 'none'|'ok'|'fail', kind: 'proof'|'bounded', words: { shouldAccept, shouldReject }, total, machineHash, checkedUpTo, count, hint } }
 * בלי language: ציור חופשי (אין פס משימה).
 * "השתנה מאז הבדיקה" לא נשמר: מחושב, כשה-hash של המכונה והנוסחה עכשיו שונה מזה שנבדק (status() מחזיר 'stale').
 */
(function (root, factory) {
  const api = factory();
  if (typeof module === 'object' && module.exports) module.exports = api;
  else root.AutomataTask = api;
})(typeof globalThis !== 'undefined' ? globalThis : this, function () {
  'use strict';
  const FILE_FORMAT = 'dsviz-automata-task';
  const MODES = ['auto', 'practice', 'solution'];
  const NONE = Object.freeze({ status: 'none' });

  /** הצורה של המכונה שהבדיקה תלויה בה (כמו App.machineSig), ועוד הנוסחה */
  const sig = (m) => JSON.stringify([m.type, m.states.map((s) => [s.id, s.accepting]), m.start, m.transitions, m.alphabet, m.acceptBy, m.stackStart, m.blank]);
  const hash = (m) => sig(m) + '\n' + ((m.language && m.language.text) || '');

  /** המשימה של המכונה, אחרי השלמת ברירות מחדל. null: אין משימה */
  function of(m) {
    const l = m && m.language;
    if (!l || typeof l.text !== 'string' || !l.text.trim()) return null;
    const mode = MODES.includes(l.mode) ? l.mode : l.locked ? 'practice' : 'auto';
    return { formula: l.text, mode, locked: !!l.locked, desc: l.desc || '', check: l.check || NONE };
  }
  /** 'none' | 'ok' | 'fail' | 'stale'. stale: המכונה או הנוסחה השתנו מאז הבדיקה */
  function status(m) {
    const t = of(m);
    if (!t || t.check.status === 'none' || !t.check.status) return 'none';
    return t.check.machineHash === hash(m) ? t.check.status : 'stale';
  }

  /** תוצאה של בדיקה (L.check / L.checkExact / L.checker) -> check לשמירה */
  function record(m, res) {
    const words = { shouldAccept: (res.missing || []).slice(0, 5), shouldReject: (res.extra || []).slice(0, 5) };
    return { status: res.ok ? 'ok' : 'fail', kind: res.exact ? 'proof' : 'bounded', words, total: (res.missing || []).length + (res.extra || []).length,
      machineHash: hash(m), checkedUpTo: res.exact ? null : res.maxLen, count: res.count == null ? null : res.count, undecided: res.undecided || 0 };
  }
  /** שומר תוצאה במכונה (בלי היסטוריה: זו לא עריכה) */
  function setCheck(m, res) { if (m.language) m.language.check = record(m, res); return m.language && m.language.check; }

  /** משימה חדשה על המכונה. mode: 'auto' | 'practice' */
  function attach(m, formula, mode, extra) {
    m.language = Object.assign({ text: formula, allowBuild: true, locked: false }, extra || {}, { text: formula, mode });
    delete m.language.check;
    return m.language;
  }
  /** החלפת מצב (התוכנה בונה / אני בונה). הבדיקה האחרונה נשארת (היא עדיין נכונה לאותה מכונה) */
  function setMode(m, mode) { if (m.language && MODES.includes(mode)) m.language.mode = mode; }
  /** "סיום המשימה": המשימה יורדת, המכונה נשארת */
  function finish(m) { delete m.language; }

  /** הכיתוב של התג והסטטוס בפס (typeName: "אס"ד" וכו') */
  function pill(m) {
    const t = of(m), st = status(m);
    if (!t) return null;
    const c = t.check;
    if (st === 'stale') return { cls: 'stale', text: t.mode === 'auto' ? 'השתנה מאז הבנייה' : 'השתנה מאז הבדיקה' };
    if (st === 'none') return { cls: 'none', text: 'עוד לא נבדק' };
    if (st === 'fail') return { cls: 'fail', text: 'לא נכון: ' + words(c.total) };
    if (t.mode === 'auto') return { cls: 'ok', text: 'נבדק מול הנוסחה ✓' };
    return { cls: 'ok', text: c.kind === 'proof' ? 'נכון ✓ הוכחה' : 'נכון ✓ נבדק עד אורך ' + c.checkedUpTo };
  }
  /** "מילה אחת" / "3 מילים" */
  const words = (k) => (k === 1 ? 'מילה אחת' : k + ' מילים');
  const CHIP = { auto: 'נבנה מהנוסחה', practice: 'משימה', solution: 'פתרון לדוגמה' };

  /** בחירת סוג המכונה לפי הסיווג (L.analyze(ast).cls) */
  const typeFor = (cls) => (cls === 'regular' ? 'dfa' : cls === 'cf' || cls === 'cfany' ? 'pda' : cls === 'noncf' ? 'tm' : 'dfa');

  /* ---------- קובץ משימה לתלמידים: הנוסחה, הסוג והא"ב, בלי המכונה ---------- */
  function toFile(m) {
    const t = of(m);
    if (!t) return null;
    const o = { format: FILE_FORMAT, version: 1, type: m.type, alphabet: (m.alphabet || []).slice(), formula: t.formula, mode: 'practice', locked: true };
    if (t.desc) o.desc = t.desc;
    return JSON.stringify(o, null, 2);
  }
  const isFile = (o) => !!o && o.format === FILE_FORMAT;
  /** קובץ משימה -> { type, alphabet, language } (המכונה עצמה נוצרת ריקה ב-App) */
  function fromFile(o) {
    if (!isFile(o)) return null;
    if (typeof o.formula !== 'string' || !o.formula.trim()) throw new Error('בקובץ המשימה אין נוסחה');
    const type = ['dfa', 'nfa', 'pda', 'tm'].includes(o.type) ? o.type : 'dfa';
    const language = { text: o.formula, allowBuild: false, locked: o.locked !== false, mode: 'practice' };
    if (o.desc) language.desc = String(o.desc);
    return { type, alphabet: Array.isArray(o.alphabet) ? o.alphabet.map(String) : [], language };
  }

  return { FILE_FORMAT, MODES, CHIP, words, sig, hash, of, status, record, setCheck, attach, setMode, finish, pill, typeFor, toFile, isFile, fromFile };
});
