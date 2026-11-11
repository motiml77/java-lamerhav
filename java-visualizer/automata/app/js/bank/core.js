/*
 * bank/core.js - "מאגר שאלות": הנתונים, המכונות, הבדיקות וההתקדמות. טהור (בלי DOM), נבדק ב-test/bank.test.js.
 * הנתונים: js/bank/data.js (נוצר מ-bank/*.json על ידי tools/bank-build.js).
 *
 * סוגי סעיפים:
 *   build      בונים מכונה מסוג machine לשפה language. בודקים עם המנוע של לשונית "שפה" (הוכחה לאס"ד / אסל"ד ושפה רגולרית, אחרת חסומה)
 *   tm-output  מכונת טיורינג שמחשבת: לכל קלט, עוצרת במצב מקבל ועל הסרט הפלט (rule). קלטים: cases ועוד כל המילים הקצרות ב-domain
 *   words      רושמים 3 מילים בשפה ו-3 מחוצה לה
 *   explain    הסבר במילים. אין בדיקה אוטומטית: תשובה לדוגמה ב-💡, והתלמיד מסמן שסיים
 * התקדמות (כמו BankProgress בתוכנות של Java ו-C#): V לשאלה (רק "סיימתי"), V לכל סעיף, והמכונה של כל סעיף. נשמר בדפדפן.
 */
(function (root, factory) {
  const node = typeof module === 'object' && module.exports;
  const api = factory(node ? require('./data.js') : root.AutomataBankData, root.AutomataModel || (node ? require('../model.js') : null),
    root.AutomataLang || (node ? require('../lang/index.js') : null), root.AutomataSim || (node ? require('../sim.js') : null));
  if (node) module.exports = api;
  else root.AutomataBank = api;
})(typeof globalThis !== 'undefined' ? globalThis : this, function (DATA, M, L, S) {
  'use strict';
  const LEVELS = [{ id: 'easy', title: 'קל' }, { id: 'medium', title: 'בינוני' }, { id: 'hard', title: 'קשה' }];
  const levelTitle = (id) => (LEVELS.find((l) => l.id === id) || LEVELS[0]).title;
  const topics = DATA.topics, questions = DATA.questions;
  const byId = new Map(questions.map((q) => [q.id, q]));
  const uniq = (a) => Array.from(new Set(a));

  /**
   * שער הגישה: המקום היחיד שמחליט אם מותר לפתוח את המאגר / ליצור תרגיל. היום תמיד כן.
   * כשתהיה כניסה (מורה / תלמיד / עמדה, כמו במאגר של Java), מחליפים רק את הפעולה הזו (AutomataBank.setAccess).
   */
  let gate = () => true, onDeny = null;
  const access = (action, q) => gate(action, q);
  /** deny(action, q, retry): מה עושים כשאין גישה (מסך הכניסה); retry נקרא אחרי כניסה מוצלחת */
  const setAccess = (fn, deny) => { gate = fn || (() => true); onDeny = fn ? deny || null : null; };
  /** הגישה נדחתה: מעבירים למי שהגדיר את השער. false: אין מי שיטפל */
  const denied = (action, q, retry) => { if (!onDeny) return false; onDeny(action, q, retry); return true; };

  const question = (id) => byId.get(id) || null;
  const section = (q, label) => q.question.sections.find((s) => s.label === label) || null;
  const ofTopic = (topic) => questions.filter((q) => q.topic === topic);
  /** הסעיפים שבונים בהם מכונה (build, tm-output), לפי הסדר */
  const machineSections = (q) => q.question.sections.filter((s) => s.kind === 'build' || s.kind === 'tm-output');
  const machineType = (s) => (s.kind === 'tm-output' ? 'tm' : s.machine);

  /** חיפוש: בשם, בנוסח ובנוסחאות */
  function matches(q, text, level) {
    if (level && q.level !== level) return false;
    const t = String(text || '').trim().toLowerCase();
    if (!t) return true;
    const hay = [q.title, q.question.intro].concat(q.question.sections.flatMap((s) => [s.text, s.language || ''])).join(' ').toLowerCase();
    return t.split(/\s+/).every((w) => hay.includes(w));
  }

  /* ---------- מכונות ---------- */
  /** הצורה הקצרה (states [שם, x, y, 'A', זווית], edges [מ, אל, תווית]) -> מכונה של הכלי */
  function machineFromSpec(spec, type, alphabet, name) {
    const m = M.createMachine(type, { name: name || 'פתרון', alphabet });
    for (const [id, x, y, flags, loop] of spec.states) M.addState(m, Object.assign({ id, name: id, x, y, accepting: /A/.test(flags || '') }, loop != null ? { loopAngle: loop } : {}));
    m.start = spec.states[0][0];
    for (const [from, to, text] of spec.edges) {
      const r = M.parseLabels(type, text);
      if (r.errors.length) throw new Error(name + ': ' + r.errors[0]);
      M.addTransition(m, from, to, r.labels);
    }
    const labels = m.transitions.flatMap((t) => t.labels);
    if (type === 'pda') m.stackAlphabet = uniq(['Z'].concat(labels.flatMap((l) => Array.from(l.pop + l.push))));
    if (type === 'tm') { m.blank = spec.blank || '_'; m.tapeAlphabet = uniq(alphabet.concat(labels.flatMap((l) => [l.read, l.write]), [m.blank])); }
    return m;
  }
  const referenceMachine = (q, s) => machineFromSpec(s.reference, machineType(s), q.alphabet, q.title + ', סעיף ' + s.label + ' (פתרון)');

  /** מכונה ריקה לסעיף: הסוג והא"ב הנכונים, והשפה של הסעיף נעולה (מוצגת בפס שמעל הקנבס, כמו בקובץ תרגיל) */
  function emptyMachine(q, s) {
    const m = M.createMachine(machineType(s), { name: q.title + (machineSections(q).length > 1 ? ', סעיף ' + s.label : ''), alphabet: q.alphabet.slice() });
    if (s.language) m.language = { text: s.language, desc: q.title, allowBuild: false, locked: true, mode: 'practice' };   // משימה נעולה: אותו פס ובדיקה כמו כל משימה
    return m;
  }

  /* ---------- בדיקות ---------- */
  const parse = (s) => L.parse(s.language);
  /** בדיקה מלאה (סינכרונית) של סעיף build: כמו L.check, ועוד בדיקה של סוג המכונה */
  function checkBuild(machine, s) {
    if (machine.type !== s.machine) return { ok: false, wrongType: true };
    return L.check(machine, parse(s));
  }

  /** סעיף words: 3 מילים בשפה ו-3 מחוצה לה. "ε" היא המילה הריקה. */
  function checkWords(q, s, ins, outs) {
    const ast = parse(s), items = [], seen = new Set();
    const one = (raw, want) => {
      const t = String(raw == null ? '' : raw).trim();
      if (!t) return { word: '', want, ok: false, why: 'חסרה מילה' };
      const w = t === 'ε' ? '' : t;
      const bad = Array.from(w).find((c) => !q.alphabet.includes(c));
      if (bad != null) return { word: w, want, ok: false, why: 'האות ' + bad + ' לא בא"ב' };
      if (seen.has(w)) return { word: w, want, ok: false, why: 'המילה כבר הופיעה' };
      seen.add(w);
      const inLang = L.accepts(ast, w);
      return inLang === want ? { word: w, want, ok: true } : { word: w, want, ok: false, why: want ? 'המילה לא בשפה' : 'המילה דווקא בשפה' };
    };
    for (const w of ins) items.push(one(w, true));
    for (const w of outs) items.push(one(w, false));
    return { ok: items.length === 6 && items.every((x) => x.ok), items };
  }

  /** הפלט הנכון של מכונה מחשבת (tm-output), לפי שם הכלל */
  const RULES = {
    complement: (w) => Array.from(w).map((c) => (c === '0' ? '1' : c === '1' ? '0' : c)).join(''),
    'unary-add': (w) => '1'.repeat(Array.from(w).filter((c) => c === '1').length),
    'binary-inc': (w) => {
      const d = Array.from(w);
      let i = d.length - 1;
      while (i >= 0 && d[i] === '1') d[i--] = '0';
      if (i >= 0) d[i] = '1'; else d.unshift('1');
      return d.join('');
    },
  };
  /** הקלטים של סעיף tm-output: הדוגמאות, ועוד כל המילים ב-domain עד אורך maxLen (בלי כפילויות) */
  function outputCases(q, s, maxLen) {
    const out = s.cases.map(([input, output]) => ({ input, output }));
    const have = new Set(out.map((c) => c.input));
    const dom = L.parse(s.domain);
    for (const w of L.words(dom.alphabet, maxLen == null ? 7 : maxLen)) {
      if (have.has(w) || !L.accepts(dom, w)) continue;
      have.add(w);
      out.push({ input: w, output: RULES[s.rule](w) });
    }
    return out;
  }
  /** מריץ את המכונה על כל הקלטים. נכון: עוצרת במצב מקבל, ועל הסרט (בלי התווים הריקים בקצוות) בדיוק הפלט */
  function checkTmOutput(machine, q, s, maxLen) {
    if (machine.type !== 'tm') return { ok: false, wrongType: true };
    const cases = outputCases(q, s, maxLen), failing = [];
    for (const c of cases) {
      const tr = S.run(machine, c.input, { record: false });
      const got = tr.output == null ? '' : tr.output;
      let why = null;
      if (tr.result === 'limit') why = 'המכונה לא עצרה (עברה ' + S.LIMITS.tmSteps.toLocaleString('he') + ' צעדים)';
      else if (tr.result !== 'accept') why = 'המכונה עצרה במצב שאינו מקבל';
      else if (got !== c.output) why = 'על הסרט נשאר פלט אחר';
      if (why) failing.push({ input: c.input, expected: c.output, got: tr.result === 'accept' ? got : null, why });
    }
    return { ok: !failing.length, failing, count: cases.length };
  }

  /* ---------- התקדמות ---------- */
  const KEY = 'dsviz.automata.bank.v1';
  /** storage: { get(key), set(key, value) } (בדפדפן: localStorage עטוף ב-try/catch; בבדיקות: Map) */
  function Progress(storage) {
    let data = null;
    const load = () => {
      if (data) return data;
      try { data = JSON.parse(storage.get(KEY) || 'null'); } catch (e) { data = null; }
      if (!data || typeof data !== 'object' || !data.questions) data = { questions: {}, active: null };
      return data;
    };
    const save = () => { try { storage.set(KEY, JSON.stringify(data)); } catch (e) { /* אין אחסון: נשאר רק בזיכרון */ } listeners.forEach((f) => f()); };
    const entry = (id) => { const d = load(); return d.questions[id] || (d.questions[id] = {}); };
    const listeners = [];
    const api = {
      listen: (f) => listeners.push(f),
      done: (id) => !!(load().questions[id] || {}).done,
      setDone(id, on) { const e = entry(id); if (on) { e.done = true; e.doneAt = Date.now(); } else { delete e.done; delete e.doneAt; } save(); },
      doneCount: (list) => list.filter((q) => api.done(q.id)).length,
      sectionDone: (id, label) => !!((load().questions[id] || {}).sections || {})[label],
      setSectionDone(id, label, on) { const e = entry(id); e.sections = e.sections || {}; if (on) e.sections[label] = true; else delete e.sections[label]; save(); },
      work: (id, label) => ((load().questions[id] || {}).work || {})[label] || null,
      setWork(id, label, machineJson) { const e = entry(id); e.work = e.work || {}; if (machineJson) e.work[label] = machineJson; else delete e.work[label]; save(); },
      hasWork: (id) => Object.keys((load().questions[id] || {}).work || {}).length > 0,
      /** "סיימתי" עם "להתחיל נקי": שוכחים את המכונות, המילים וההסברים של השאלה (ה-V נשאר) */
      clearWork(id) { const e = entry(id); delete e.work; delete e.words; delete e.text; save(); },
      words: (id, label) => ((load().questions[id] || {}).words || {})[label] || { in: ['', '', ''], out: ['', '', ''] },
      setWords(id, label, w) { const e = entry(id); e.words = e.words || {}; e.words[label] = w; save(); },
      text: (id, label) => ((load().questions[id] || {}).text || {})[label] || '',
      setText(id, label, t) { const e = entry(id); e.text = e.text || {}; e.text[label] = t; save(); },
      /** התרגיל הפתוח עכשיו: { id, label } של הסעיף שהמכונה שלו על הקנבס */
      active: () => load().active || null,
      setActive(a) { load().active = a || null; save(); },
      reset() { data = { questions: {}, active: null }; save(); },
    };
    return api;
  }

  return { LEVELS, levelTitle, topics, questions, question, section, ofTopic, machineSections, machineType, matches,
    machineFromSpec, referenceMachine, emptyMachine, checkBuild, checkWords, RULES, outputCases, checkTmOutput, Progress, KEY, access, setAccess, denied };
});
