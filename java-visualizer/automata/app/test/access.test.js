'use strict';
/* הכניסה המשותפת (access.js) מול DSVizAccess מדומה: שער המאגר, כניסה עם Google, עמדת מחשב, שינויים חיים, התנתקות והודעות. */
const test = require('node:test');
const assert = require('node:assert/strict');
const A = require('../js/access.js');
const B = require('../js/bank/core.js');

const TEACHER = { state: 'teacher', name: 'רונית כהן', email: 'ronit@example.org', role: 'teacher', canSignOut: true };
const OUT = { state: 'signed-out', name: '', email: '' };

/** DSVizAccess מדומה: אותו API כמו js/access-client.js. emit(a) מדמה שינוי מ-Eclipse / Visual Studio */
function fake(start, over) {
  const listeners = new Set(), calls = [];
  const f = {
    access: start, calls, results: {},
    available: () => true,
    get: async () => f.access,
    onChange(cb) { listeners.add(cb); cb(f.access); return () => listeners.delete(cb); },
    emit(a) { f.access = a; listeners.forEach((cb) => cb(a)); },
    async signIn(role) { calls.push(['signIn', role]); const r = f.results.signIn || { ok: true, status: 'ok', message: '' }; if (r.ok) f.emit(Object.assign({}, TEACHER, { state: role })); return r; },
    async cancelSignIn() { calls.push(['cancel']); return { ok: true }; },
    async claimStation(code, name) { calls.push(['station', code, name]); const r = f.results.station || { ok: true, status: 'ok' }; if (r.ok) f.access = { state: 'station', name, teacher: 'רונית כהן' }; return r; },
    async signOut() { calls.push(['signOut']); f.emit(OUT); return { ok: true, status: 'ok', message: '' }; },
    async openTeacherPanel() { calls.push(['panel']); return { ok: true }; },
  };
  return Object.assign(f, over || {});
}
const tick = () => new Promise((r) => setImmediate(r));

test('בלי התוכנה המקומית: unavailable, ואין כפתור חשבון', () => {
  const seen = [];
  const c = A.controller(null, { change: (a) => seen.push(a) }).start();
  assert.equal(c.mode, 'unavailable');
  assert.equal(c.allowed(), false);
  assert.deepEqual(seen, [null]);
  assert.equal(A.controller({ available: () => false }).mode, 'unavailable');
});

test('לא מחובר: השער סוגר, והמאגר מעביר למסך הכניסה; אחרי כניסה של מורה הוא נפתח', async () => {
  const f = fake(OUT);
  const c = A.controller(f).start();
  let denied = null;
  B.setAccess(c.gate, (action, q, retry) => { denied = { action, retry }; });
  try {
    assert.equal(B.access('open'), false);
    assert.equal(B.denied('open', null, () => 'again'), true);
    assert.equal(denied.action, 'open');
    const v = await c.signIn('teacher');
    assert.equal(v.view, 'success');
    assert.equal(v.title, 'נכנסת בהצלחה');
    assert.match(v.text, /"החשבון שלי"/);
    assert.equal(B.access('open'), true);
    assert.equal(A.label(c.access), 'מורה: רונית כהן');
  } finally { B.setAccess(null); }
  assert.equal(B.denied('open', null, () => {}), false, 'בלי שער: אין מי שיטפל');
});

test('שינוי חי: התנתקות ב-Eclipse סוגרת את הגישה ומעדכנת את הכפתור', () => {
  const f = fake(TEACHER), labels = [], lost = [], gained = [];
  const c = A.controller(f, { change: (a) => labels.push(A.label(a)), lost: (b, a) => lost.push([b.state, a.state]), gained: (a) => gained.push(a.state) }).start();
  assert.equal(c.allowed(), true);
  f.emit(OUT);
  assert.equal(c.allowed(), false);
  assert.deepEqual(lost, [['teacher', 'signed-out']]);
  f.emit({ state: 'student', name: 'נועה', email: 'noa@example.org' });
  assert.deepEqual(gained, ['teacher', 'student']);
  assert.deepEqual(labels, ['מורה: רונית כהן', 'לא מחובר', 'תלמיד: נועה']);
  assert.equal(A.label({ state: 'station', name: 'מעבדה 2' }), 'עמדה: מעבדה 2');
  assert.equal(A.label({ state: 'student', name: '', email: 'x@y.org' }), 'תלמיד: x@y.org');
});

test('עמדת מחשב: בדיקת השדות, קוד שגוי בשורה, הצלחה עם שם המורה, ואין התנתקות', async () => {
  const f = fake(OUT);
  const c = A.controller(f).start();
  assert.deepEqual(await c.station('K7', 'מעבדה'), { view: 'inline', text: 'הקוד הוא 4 תווים, למשל K7QM.' });
  assert.match((await c.station('K7QM', ' ')).text, /כתבו שם למחשב/);
  assert.equal(f.calls.length, 0, 'לא נשלח כלום');
  f.results.station = { ok: false, status: 'bad-code' };
  assert.deepEqual(await c.station('ABCD', 'מעבדה 2'), { view: 'inline', text: 'הקוד לא נמצא. בודקים מול המורה ומקלידים שוב.' });
  f.results.station = { ok: true, status: 'ok' };
  const v = await c.station(' k7qm ', 'מעבדה 2 · עמדה 14');
  assert.deepEqual(f.calls.at(-1), ['station', 'k7qm', 'מעבדה 2 · עמדה 14']);
  assert.equal(v.title, 'המחשב מחובר');
  assert.equal(v.text, 'העמדה של רונית כהן: המאגר פתוח לכל מי שעובד על המחשב הזה, גם בלי אינטרנט.');
  assert.equal(c.allowed(), true);
  assert.equal(A.canSignOut(c.access), false);
  assert.equal(await c.signOut(async () => true), null, 'עמדה לא מתנתקת מכאן');
  f.results.station = { ok: false, status: 'full' };
  assert.equal((await c.station('ABCD', 'x')).title, 'אין עוד היתרים');
  f.results.station = { ok: false, status: 'offline', message: 'החיבור המאובטח לשרת של DSViz נחסם במחשב הזה. ... את השרת של DSViz — ומנסים שוב.' };
  const off = await c.station('ABCD', 'x');
  assert.equal(off.view, 'inline');
  assert.match(off.text, /נחסם במחשב הזה/);
  assert.ok(!off.text.includes('—'), 'בלי קו ארוך');
});

test('הודעות של כניסה עם Google, כמו במסך של Java', async () => {
  const views = (s, m) => A.signInView('teacher', { ok: false, status: s, message: m || '' });
  assert.equal(views('not-teacher').title, 'החשבון עוד לא מוגדר כמורה');
  assert.equal(views('blocked').title, 'הגישה של החשבון הושבתה');
  assert.match(views('devices', 'מחובר ב: בית, כיתה.').text, /^מחובר ב: בית, כיתה\. בדפדפן אפשר לנתק/);
  assert.deepEqual(views('cancelled'), { view: 'choose' });
  assert.equal(views('cancelled', 'הדפדפן נסגר').title, 'לא התקבלה כניסה');
  assert.equal(views('pay').view, 'pay');
  assert.deepEqual([views('offline', 'התוכנה "מודלים חישוביים" לא עונה — פותחים אותה שוב מהקיצור.').title, views('offline', 'התוכנה "מודלים חישוביים" לא עונה — פותחים אותה שוב מהקיצור.').text],
    ['הכניסה לא הצליחה', 'התוכנה "מודלים חישוביים" לא עונה, פותחים אותה שוב מהקיצור.']);
  assert.equal(views('error').text, 'נסו שוב בעוד רגע.');
  assert.equal(A.signInView('student', { ok: true, status: 'ok' }).text, 'המאגר פתוח, גם בפעם הבאה, גם בלי אינטרנט.');
});

test('ביטול באמצע הכניסה: חוזרים לבחירה; אבל אם הכניסה כבר הצליחה, מראים הצלחה', async () => {
  let finish;
  const f = fake(OUT, { signIn() { return new Promise((r) => { finish = r; }); } });
  const c = A.controller(f).start();
  let p = c.signIn('student');
  await tick();
  await c.cancel();
  finish({ ok: false, status: 'cancelled', message: '' });
  assert.deepEqual(await p, { view: 'choose' });
  p = c.signIn('student');
  await tick();
  await c.cancel();
  f.access = { state: 'student', name: 'נועה' };
  finish({ ok: true, status: 'ok' });
  assert.equal((await p).view, 'success');
  assert.equal(c.allowed(), true);
});

test('התנתקות: השאלה בעברית, ביטול לא שולח, אישור מתנתק ומודיע', async () => {
  const f = fake(TEACHER), lost = [];
  const c = A.controller(f, { lost: () => lost.push(1) }).start();
  let q = null;
  assert.equal(await c.signOut(async (x) => { q = x; return false; }), null);
  assert.deepEqual(q.lines, ['להתנתק מהחשבון ronit@example.org במחשב הזה?', 'עמדת הניהול והמאגר ייסגרו. עמדות המחשב שחיברת בכיתה ממשיכות לעבוד כרגיל.', 'בפעם הבאה ייפתח מסך הכניסה.']);
  assert.equal(q.ok, 'התנתקות');
  assert.ok(!f.calls.some((x) => x[0] === 'signOut'));
  const r = await c.signOut(async () => true);
  assert.deepEqual(r, { ok: true, message: 'התנתקת. בפעם הבאה שפותחים את המאגר מופיע מסך הכניסה.' });
  assert.equal(c.allowed(), false);
  assert.deepEqual(lost, [1]);
  assert.deepEqual(A.signOutQuestion({ state: 'student', email: '' }).lines[0], 'להתנתק במחשב הזה?');
});

test('הנוסחים בלי קו ארוך ובלי סימן קריאה', () => {
  const all = JSON.stringify(A.T) + JSON.stringify(['not-teacher', 'blocked', 'devices', 'pay', 'ok', 'error'].map((s) => A.signInView('teacher', { status: s })))
    + JSON.stringify(['ok', 'bad-code', 'full', 'blocked', 'error'].map((s) => A.stationView({ status: s }, 'x'))) + JSON.stringify(A.signOutQuestion(TEACHER)) + JSON.stringify(A.paid());
  assert.ok(!/[—!]/.test(all));
});
