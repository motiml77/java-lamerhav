'use strict';
/* מאגר השאלות: הנתונים, מכונות הפתרון מול השפה של כל שאלה, הבדיקות וההתקדמות. */
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('fs');
const B = require('../js/bank/core.js');
const M = require('../js/model.js');
require('../js/validate.js');
const L = require('../js/lang/index.js');
const S = require('../js/sim.js');
const build = require('../tools/bank-build.js');

test('data.js מעודכן מול bank/*.json', () => {
  assert.equal(fs.readFileSync(build.OUT, 'utf8'), build.render(build.collect()), 'מריצים: node tools/bank-build.js');
});

test('ארבעה נושאים, לפחות 8 שאלות בכל אחד, מהקל לקשה, וכל שדה במקום', () => {
  assert.deepEqual(B.topics.map((t) => t.id), ['dfa', 'nfa', 'pda', 'tm']);
  assert.deepEqual(B.topics.map((t) => t.title), ['אס"ד', 'אסל"ד', 'אוטומט מחסנית', 'מכונת טיורינג']);
  const rank = { easy: 0, medium: 1, hard: 2 };
  for (const t of B.topics) {
    const qs = B.ofTopic(t.id);
    assert.ok(qs.length >= 8, t.id + ': ' + qs.length);
    for (let i = 1; i < qs.length; i++) assert.ok(rank[qs[i - 1].level] <= rank[qs[i].level], t.id + ': מהקל לקשה');
    assert.ok(qs.some((q) => q.level === 'easy') && qs.some((q) => q.level === 'hard'), t.id);
  }
  assert.equal(new Set(B.questions.map((q) => q.id)).size, B.questions.length, 'מזהים ייחודיים');
  for (const q of B.questions) {
    assert.ok(q.title && q.question.intro && q.question.sections.length && q.alphabet.length, q.id);
    assert.ok(B.machineSections(q).length >= 1, q.id + ': יש סעיף שבונים בו מכונה');
    q.question.sections.forEach((s, i) => {
      assert.equal(s.label, 'אבגד'[i], q.id + ': סעיפים א, ב, ג לפי הסדר');
      assert.ok(s.text && s.hint, q.id + ' ' + s.label + ': נוסח ורמז');
      assert.ok(['build', 'tm-output', 'words', 'explain'].includes(s.kind), q.id + ' ' + s.label);
      if (s.kind === 'build') assert.ok(['dfa', 'nfa', 'pda', 'tm'].includes(s.machine), q.id);
      if (s.kind === 'explain') assert.ok(s.answer, q.id + ': תשובה לדוגמה');
      if (s.label === 'א') assert.equal(B.machineType(s), q.topic, q.id + ': סעיף א בונה מכונה מסוג הנושא');
      // כללי הכתיבה מימין לשמאל (כמו ב-bank/README.md): בלי מקף ארוך, בלי נקודה אחרי מספר בסוף שורה
      for (const t of [s.text, s.hint, s.answer || '', q.question.intro]) {
        assert.ok(!/[—!]/.test(t), q.id + ': בלי מקף ארוך וסימן קריאה');
        assert.ok(!/[0-9]\.\s*$/m.test(t), q.id + ': בלי נקודה אחרי מספר בסוף שורה');
      }
    });
  }
});

test('כל שאלה מתפענחת, והדוגמאות נכונות לפי בדיקת השייכות', () => {
  for (const q of B.questions) for (const s of q.question.sections) {
    if (!s.language) continue;
    const ast = L.parse(s.language);
    for (const c of ast.alphabet) assert.ok(q.alphabet.includes(c), q.id + ': האות ' + c + ' של השפה בא"ב של השאלה');
    for (const w of (s.examples || {}).in || []) assert.equal(L.accepts(ast, w), true, q.id + ' "' + w + '" בשפה');
    for (const w of (s.examples || {}).out || []) assert.equal(L.accepts(ast, w), false, q.id + ' "' + w + '" לא בשפה');
  }
});

test('כל מכונת פתרון מקבלת בדיוק את השפה של הסעיף (הוכחה לרגולריות, ואחרת כל המילים הקצרות)', () => {
  for (const q of B.questions) for (const s of q.question.sections) {
    if (s.kind !== 'build') continue;
    const m = B.referenceMachine(q, s);
    assert.equal(m.type, s.machine, q.id);
    assert.deepEqual(M.validate(m).filter((i) => i.level === 'error'), [], q.id + ': בלי שגיאות');
    const r = B.checkBuild(m, s);
    assert.ok(r.ok, q.id + ' ' + s.label + ': ' + JSON.stringify({ missing: r.missing, extra: r.extra }));
    if (s.machine === 'dfa' || s.machine === 'nfa') assert.equal(r.exact, true, q.id + ': הוכחה');
    else {
      const ast = L.parse(s.language), len = ast.alphabet.length <= 2 ? 9 : 6;
      for (const w of L.words(ast.alphabet, len)) assert.equal(S.run(m, w, { record: false }).result === 'accept', L.accepts(ast, w), q.id + ' "' + w + '"');
    }
  }
});

test('מכונות טיורינג מחשבות: הפתרון נותן את הפלט הנכון לכל קלט קצר, והכללים עצמם', () => {
  assert.equal(B.RULES['binary-inc']('1011'), '1100');
  assert.equal(B.RULES['binary-inc']('111'), '1000');
  assert.equal(B.RULES['binary-inc']('0011'), '0100');
  assert.equal(B.RULES['unary-add']('11011'), '1111');
  assert.equal(B.RULES.complement('1010'), '0101');
  for (const q of B.questions) for (const s of q.question.sections) {
    if (s.kind !== 'tm-output') continue;
    for (const [i, o] of s.cases) assert.equal(B.RULES[s.rule](i), o, q.id + ': הדוגמה ' + i);
    const r = B.checkTmOutput(B.referenceMachine(q, s), q, s, 8);
    assert.ok(r.ok && r.count >= 20, q.id + ': ' + JSON.stringify(r.failing.slice(0, 3)) + ' ' + r.count);
  }
  // מכונה שגויה: הפלט לא נכון
  const q = B.question('tm-binary-inc'), s = q.question.sections[0];
  const wrong = M.createMachine('tm', { alphabet: ['0', '1'] });
  const a = M.addState(wrong, { accepting: true });
  wrong.start = a.id;
  const r = B.checkTmOutput(wrong, q, s);
  assert.equal(r.ok, false);
  assert.deepEqual(r.failing[0], { input: '1011', expected: '1100', got: '1011', why: 'על הסרט נשאר פלט אחר' });
});

test('סעיף מילים: 3 בשפה ו-3 מחוצה לה, עם הסבר לכל טעות', () => {
  const q = B.question('dfa-even-a'), s = B.section(q, 'ב');
  assert.equal(B.checkWords(q, s, ['ε', 'aa', 'bab' + 'a'], ['a', 'ab', 'aaab']).ok, true);
  const r = B.checkWords(q, s, ['a', 'aa', 'aa'], ['c', '', 'bb']);
  assert.equal(r.ok, false);
  assert.deepEqual(r.items.map((x) => x.why || 'ok'), ['המילה לא בשפה', 'ok', 'המילה כבר הופיעה', 'האות c לא בא"ב', 'חסרה מילה', 'המילה דווקא בשפה']);
});

test('בדיקת סעיף build: סוג מכונה שגוי, ומכונה שגויה נותנת מילים', () => {
  const q = B.question('dfa-even-a'), s = B.section(q, 'א');
  assert.equal(B.checkBuild(M.createMachine('nfa'), s).wrongType, true);
  const m = B.emptyMachine(q, s);
  assert.deepEqual([m.type, m.alphabet, m.language.locked, m.language.allowBuild, m.states.length], ['dfa', ['a', 'b'], true, false, 0]);
  const one = M.addState(m, { accepting: true });
  m.start = one.id;
  M.addTransition(m, one.id, one.id, [{ read: 'a' }, { read: 'b' }]);
  const r = B.checkBuild(m, s);
  assert.equal(r.ok, false);
  assert.equal(r.exact, true);
  assert.deepEqual(r.extra.slice(0, 2), ['a', 'ab']);
});

test('התקדמות: V לשאלה ולסעיף, מכונה לכל סעיף, ניקוי, ושמירה בין טעינות', () => {
  const store = new Map(), st = { get: (k) => store.get(k) || null, set: (k, v) => store.set(k, v) };
  let p = B.Progress(st), fired = 0;
  p.listen(() => fired++);
  assert.equal(p.done('dfa-even-a'), false);
  p.setSectionDone('dfa-even-a', 'א', true);
  p.setWork('dfa-even-a', 'א', '{"x":1}');
  p.setWords('dfa-even-a', 'ב', { in: ['a', '', ''], out: ['', '', ''] });
  p.setActive({ id: 'dfa-even-a', label: 'א' });
  p.setDone('dfa-even-a', true);
  assert.ok(fired >= 5);
  p = B.Progress(st);                // טעינה מחדש
  assert.equal(p.done('dfa-even-a'), true);
  assert.equal(p.sectionDone('dfa-even-a', 'א'), true);
  assert.equal(p.work('dfa-even-a', 'א'), '{"x":1}');
  assert.equal(p.hasWork('dfa-even-a'), true);
  assert.deepEqual(p.active(), { id: 'dfa-even-a', label: 'א' });
  assert.equal(p.doneCount(B.ofTopic('dfa')), 1);
  p.clearWork('dfa-even-a');
  assert.equal(p.hasWork('dfa-even-a'), false);
  assert.equal(p.done('dfa-even-a'), true, 'ה-V נשאר');
  p.setDone('dfa-even-a', false);
  assert.equal(p.done('dfa-even-a'), false);
  // אחסון שבור: עובדים בזיכרון בלי לזרוק
  const broken = B.Progress({ get() { throw new Error('x'); }, set() { throw new Error('x'); } });
  broken.setDone('q', true);
  assert.equal(broken.done('q'), true);
});

test('חיפוש וסינון לפי רמה, ושער הגישה', () => {
  assert.ok(B.questions.filter((q) => B.matches(q, 'פלינדרום')).length >= 2);
  assert.ok(B.questions.filter((q) => B.matches(q, '', 'hard')).every((q) => q.level === 'hard'));
  assert.equal(B.matches(B.question('dfa-even-a'), 'aⁿbⁿcⁿ zzz'), false);
  assert.equal(B.access('open'), true);
  B.setAccess(() => false);
  assert.equal(B.access('open'), false);
  B.setAccess(null);
  assert.equal(B.access('create', B.questions[0]), true);
});
