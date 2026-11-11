/*
 * e2e/task.js - סבב 14 (Playwright): "המשימה". כרטיס הפתיחה, עורך הנוסחה בשני המצבים (ו"תבנית…"), פס המשימה (התוכנה בונה / אני בונה),
 * סטטוסים (עוד לא נבדק, לא נכון, השתנה מאז הבדיקה, נכון), כרטיס התוצאה עם שתי קבוצות, רמז, הצגת פתרון בחלון נפרד (המכונה נשארת),
 * תרגיל מהמאגר עם אותו פס, חלון "השאלה" כחלון אמיתי (גרירה, מזעור, סגירה ופתיחה מהפס), וכרטיס "איך בונים" רק בכניסה הראשונה.
 * מוגש משרת מדומה של התוכנה (המאגר צריך כניסה). צילומים: docs/shots/r14-*.png. הרצה: node e2e/task.js
 */
'use strict';
const path = require('path');
const assert = require('assert/strict');
const { chromium } = require('playwright');
const mock = require('./mock-launcher.js');
const H = require('./helpers.js');

const OUT = path.resolve(__dirname, '..', '..', 'docs', 'shots');

(async () => {
  const launcher = await mock.start({ access: { state: 'teacher', name: 'רונית כהן', email: 'ronit@example.org', role: 'teacher', apps: ['dsviz', 'automata'], canSignOut: true } });
  const browser = await chromium.launch();
  const ctx = await browser.newContext({ viewport: { width: 1440, height: 900 } });
  const p = await ctx.newPage();
  const errors = [];
  let n = 0;
  const step = async (name, fn) => { await fn(); console.log('✓ ' + (++n) + '. ' + name); };
  const watch = (pg) => { pg.on('console', (x) => { if (x.type() === 'error') errors.push(x.text()); }); pg.on('pageerror', (e) => errors.push(e.message)); };
  watch(p);
  const shot = async (name, pg) => { pg = pg || p; await pg.waitForTimeout(350); await pg.evaluate(() => document.querySelectorAll('.toast').forEach((t) => t.remove())); await pg.screenshot({ path: path.join(OUT, name + '.png') }); };
  const text = async (sel) => (await p.textContent(sel)).replace(/\s+/g, ' ').trim();
  const pill = () => text('#ts-pill');
  const mode = () => p.getAttribute('#guide', 'data-mode');
  const guideShown = () => p.evaluate(() => !document.getElementById('guide').hidden);
  const box = (sel) => p.$eval(sel, (e) => { const r = e.getBoundingClientRect(); return { x: r.x, y: r.y, w: r.width, h: r.height, r: r.right, b: r.bottom }; });
  /** בונה מכונה במודל (התנהגות הבנייה בעכבר נבדקת ב-flow.js וב-bank.js) */
  const machine = (spec) => p.evaluate((sp) => {
    App.change('בנייה בבדיקה', () => {
      const m = App.machine;
      m.states = []; m.transitions = [];
      sp.states.forEach(([id, acc], i) => AutomataModel.addState(m, { id, name: id, x: 160 + i * 220, y: 200, accepting: acc }));
      m.start = sp.states[0][0];
      for (const [f, t, l] of sp.edges) AutomataModel.addTransition(m, f, t, AutomataModel.parseLabels(m.type, l).labels);
    });
  }, spec);

  await p.goto(launcher.url);
  await p.waitForSelector('#guide:not([hidden])');
  await p.evaluate(() => document.fonts.ready);

  await step('כניסה ראשונה, קנבס ריק: כרטיס הפתיחה עם שלוש דרכים וקישור למאגר', async () => {
    assert.equal(await mode(), 'start');
    assert.deepEqual(await p.$$eval('.gs-card b', (e) => e.map((x) => x.textContent)), ['ציור חופשי', 'התוכנה בונה', 'אני בונה, התוכנה בודקת']);
    assert.match(await text('.gs-card[data-g=auto]'), /כותבים נוסחה של שפה, ומקבלים אוטומט מוכן/);
    assert.ok(await p.$('#guide [data-g=bank]'));
    assert.equal(await p.isVisible('#lang-strip'), false, 'ציור חופשי: אין פס');
    await shot('r14-start');
  });

  await step('"איך מתחילים?" הוא חלון עצמאי מעל התוכנה: במרכז החלון (לא של הקנבס), עם כותרת נגררת', async () => {
    assert.equal(await p.evaluate(() => document.getElementById('guide').parentElement === document.body), true);
    assert.equal(await text('#guide .gw-head b'), 'איך מתחילים?');
    const g = await box('#guide'), stage = await box('.stage');
    assert.ok(Math.abs(g.x + g.w / 2 - 720) <= 2 && Math.abs(g.y + g.h / 2 - 450) <= 2, 'במרכז חלון התוכנה: ' + JSON.stringify(g));
    assert.ok(Math.abs(stage.x + stage.w / 2 - 720) > 40, 'מרכז הקנבס שונה ממרכז החלון, כלומר החלון לא ממורכז על הקנבס');
    const h = await box('#guide .gw-head');
    await p.mouse.move(h.x + 60, h.y + h.h / 2);
    await p.mouse.down();
    await p.mouse.move(h.x - 140, h.y + h.h / 2 + 80, { steps: 5 });
    await p.mouse.up();
    const g2 = await box('#guide');
    assert.ok(Math.abs(g2.x - (g.x - 200)) <= 3 && Math.abs(g2.y - (g.y + 80)) <= 3, 'נגרר: ' + JSON.stringify(g2));
    assert.equal(await mode(), 'start');
    await shot('r15-start-window');
  });

  await step('"איך מתחילים?" רק בפתיחה הראשונה: אחרי פתיחה מחדש של התוכנה הוא לא מופיע', async () => {
    const q = await p.context().newPage();
    await q.goto(launcher.url);
    await q.waitForSelector('.stage');
    await q.waitForTimeout(500);
    assert.equal(await q.evaluate(() => App.machine.states.length), 0, 'קנבס ריק');
    assert.equal(await q.evaluate(() => document.getElementById('guide').hidden), true);
    await q.close();
  });

  await step('"התוכנה בונה": עורך במצב הזה; "תבנית…" היא רשימה בקבוצות עם סינון ומקלדת', async () => {
    await p.click('#guide [data-g=auto]');
    await p.waitForSelector('.lang-dialog');
    assert.equal(await p.getAttribute('.ld-seg [data-mode=auto]', 'aria-checked'), 'true');
    assert.equal(await p.$('.lang-templates, .lt'), null, 'אין ענן תבניות');
    await p.click('.tpl-btn');
    await p.waitForSelector('.tpl-pop:not([hidden])');
    assert.deepEqual(await p.$$eval('.tpl-group', (e) => e.map((x) => x.textContent)), ['רגולריות', 'חסרות הקשר', 'עם תנאים (≥, <, ≠, %)', 'איחוד ושרשור', 'לא חסרות הקשר / מכונת טיורינג']);
    const list = await box('.tpl-list');
    assert.ok(list.h <= 280 * 0.9 + 2, 'גובה הרשימה ' + list.h);
    assert.match(await text('.tpl-opt'), /מספר זוגי של a/);
    await shot('r14-templates');
    await p.keyboard.type('מכילה');
    assert.deepEqual(await p.$$eval('.tpl-opt .tpl-he', (e) => e.map((x) => x.textContent)), ['מכילה aba', 'לא מכילה aa']);
    await p.keyboard.press('ArrowDown');
    await p.keyboard.press('ArrowUp');
    await p.keyboard.press('Enter');
    assert.equal(await p.isVisible('.tpl-pop'), false);
    assert.equal(await p.inputValue('#lang-input'), 'w ∈ {a,b}* | מכילה aba');
    await p.waitForTimeout(300);
    assert.match(await text('#ld-foot'), /אס"ד מינימלי/);
    await shot('r14-editor-auto');
  });

  await step('בנה אוטומט: פס "נבנה מהנוסחה", נבדק מול הנוסחה; עריכה ביד: "השתנה מאז הבנייה" ובדיקה', async () => {
    await H.build(p);
    assert.equal(await text('#lang-strip .ts-chip'), 'נבנה מהנוסחה');
    assert.equal(await pill(), 'נבדק מול הנוסחה ✓');
    assert.equal(await p.$('#lang-strip [data-t=check]'), null, 'נכון: אין צורך בבדיקה');
    assert.ok(await p.$('#lang-strip [data-t=edit]') && await p.$('#lang-strip [data-t=rebuild]'));
    await shot('r14-strip-auto');
    await p.evaluate(() => App.change('מקבל', () => { const s = App.machine.states[0]; s.accepting = !s.accepting; }));
    assert.equal(await pill(), 'השתנה מאז הבנייה');
    assert.equal(await H.check(p), 'fail');
    await p.keyboard.press('Control+z');
    assert.equal(await pill(), 'נבדק מול הנוסחה ✓', 'אחרי ביטול: אותה מכונה, אותה בדיקה');
  });

  await step('"אני בונה": עורך במצב הזה, סוג המכונה לפי הסיווג; "התחלה" שואלת לפני שמנקה, ומוסיפה מילים לטבלה', async () => {
    await p.click('[data-cmd=lang-menu]');
    await p.click('[data-cmd=lang-practice]');
    await p.fill('#lang-input', 'w ∈ {a,b}* | #a(w) זוגי');
    await p.waitForTimeout(300);
    assert.equal(await p.inputValue('#ld-type'), 'dfa');
    assert.match(await text('#ld-lead'), /בונים את המכונה לבד/);
    await shot('r14-editor-practice');
    await p.click('[data-l=start]');
    await p.waitForSelector('.dialog');
    assert.deepEqual(await p.$$eval('.dialog .btn', (e) => e.map((x) => x.textContent)), ['להתחיל ממכונה ריקה', 'להמשיך עם המכונה שכבר שם', 'ביטול']);
    await p.click('.dialog [data-r="1"]');
    await p.waitForTimeout(300);
    assert.equal(await text('#lang-strip .ts-chip'), 'משימה');
    assert.match(await text('#lang-strip .ts-lead'), /בנו אס"ד לשפה/);
    assert.equal(await pill(), 'עוד לא נבדק');
    assert.equal(await p.$('#lang-strip [data-t=rebuild]'), null, 'אין "בנה אוטומט" במשימה');
    assert.ok((await p.$$('#tests-table tbody tr')).length >= 4, 'מילים לדוגמה בטבלת הבדיקה');
    assert.equal(await p.evaluate(() => App.machine.states.length), 0);
    await shot('r14-practice-none');
  });

  await step('מכונה שגויה: "לא נכון", כרטיס עם שתי קבוצות; לחיצה על מילה מריצה אותה', async () => {
    await machine({ states: [['q0', true], ['q1', false]], edges: [['q0', 'q1', 'b'], ['q1', 'q0', 'b'], ['q0', 'q0', 'a'], ['q1', 'q1', 'a']] });   // סופרת b במקום a
    assert.equal(await H.check(p), 'fail');
    assert.match(await pill(), /^לא נכון: (\d+ מילים|מילה אחת)$/);
    assert.deepEqual(await p.$$eval('#task-card .lr-title', (e) => e.map((x) => x.textContent)), ['אמורות להתקבל, ונדחו', 'אמורות להידחות, והתקבלו']);
    const acc = await p.$$eval('#task-card .tc-group:first-child .w-chip', (e) => e.map((x) => x.textContent));
    assert.ok(acc.length <= 5 && acc[0] === 'b', acc.join(','));
    await shot('r14-practice-fail');
    await p.click('#task-card .w-chip[data-word="b"]');
    await p.waitForSelector('#result-banner.err:not([hidden])');
    assert.equal(await p.inputValue('#word-input'), 'b');
    await p.keyboard.press('Escape');
  });

  await step('רמז: מספר המצבים באס"ד המינימלי, ואחר כך מילה שנכשלת', async () => {
    await p.click('#lang-strip [data-t=more]');
    await p.click('#ts-menu [data-t=hint]');
    assert.match(await text('#task-card'), /רמז: באס"ד המינימלי של השפה יש 2 מצבים/);
    await p.click('#lang-strip [data-t=more]');
    await p.click('#ts-menu [data-t=hint]');
    assert.match(await text('#task-card'), /רמז: המילה \S+ אמורה/);
  });

  await step('מתקנים: "השתנה מאז הבדיקה" ואז "נכון ✓ הוכחה" (Ctrl+Enter)', async () => {
    await machine({ states: [['q0', true], ['q1', false]], edges: [['q0', 'q1', 'a'], ['q1', 'q0', 'a'], ['q0', 'q0', 'b'], ['q1', 'q1', 'b']] });
    assert.equal(await pill(), 'השתנה מאז הבדיקה');
    await p.click('#ts-pill');
    assert.match(await text('#task-card'), /המכונה השתנתה מאז הבדיקה/);
    await shot('r14-practice-stale');
    await p.click('.stage .grid-bg >> nth=0', { position: { x: 400, y: 400 } }).catch(() => {});
    await p.keyboard.press('Control+Enter');
    await p.waitForSelector('#task-card.ok');
    assert.equal(await pill(), 'נכון ✓ הוכחה');
    assert.match(await text('#task-card'), /זו הוכחה/);
    await shot('r14-practice-ok');
  });

  await step('הצגת פתרון: אישור, חלון נפרד לצפייה; המכונה של התלמיד והשמירה בדפדפן לא משתנות', async () => {
    const before = await p.evaluate(() => [App.machine.states.length, localStorage.getItem('dsviz.automata.v1').length]);
    await p.click('#lang-strip [data-t=more]');
    await p.click('#ts-menu [data-t=solution]');
    await p.waitForSelector('.dialog');
    assert.match(await text('.dialog'), /נפתח בחלון נפרד/);
    const [sol] = await Promise.all([ctx.waitForEvent('page'), p.click('.dialog [data-r="1"]')]);
    watch(sol);
    await sol.waitForSelector('.view-banner');
    await sol.waitForSelector('.st');
    assert.match(await sol.textContent('.view-banner'), /פתרון לדוגמה, לצפייה/);
    assert.equal(await sol.evaluate(() => App.viewOnly && App.machine.states.length), 2);
    assert.equal(await sol.textContent('#lang-strip .ts-chip'), 'פתרון לדוגמה');
    await sol.evaluate(() => App.change('x', () => App.machine.states.pop()));
    await sol.waitForTimeout(400);
    await sol.close();
    assert.deepEqual(await p.evaluate(() => [App.machine.states.length, localStorage.getItem('dsviz.automata.v1').length]), before);
  });

  await step('"סיום המשימה": המשימה יורדת, המכונה נשארת, ואין פס', async () => {
    await p.click('#lang-strip [data-t=more]');
    await p.click('#ts-menu [data-t=finish]');
    assert.equal(await p.isVisible('#lang-strip'), false);
    assert.equal(await p.evaluate(() => App.machine.states.length), 2);
  });

  await step('תרגיל מהמאגר: אותו פס ("משימה", נעולה), והבדיקה מהחלון "השאלה" מגיעה לפס', async () => {
    await p.click('[data-cmd=bank]');
    await p.click('[data-bk-topic=dfa]');
    await p.click('[data-bk-q=dfa-even-a]');
    await p.click('[data-bk=create]');
    await p.waitForSelector('#bank-float:not([hidden])');
    assert.equal(await guideShown(), false, 'פתיחת שאלה לא פותחת את "איך בונים"');
    assert.equal(await text('#lang-strip .ts-chip'), 'משימה');
    await p.click('#lang-strip [data-t=more]');
    assert.equal(await p.$('#ts-menu [data-t=edit]'), null, 'נעולה');
    assert.equal(await p.$('#ts-menu [data-t=finish]'), null, 'במאגר מסיימים ב"סיימתי"');
    assert.ok(await p.$('#ts-menu [data-t=solution]'));
    await p.keyboard.press('Escape');
    await machine({ states: [['q0', true], ['q1', false]], edges: [['q0', 'q1', 'a'], ['q1', 'q0', 'a'], ['q0', 'q0', 'b'], ['q1', 'q1', 'b']] });
    await p.click('#bf-sec-א [data-bf-check]');
    await p.waitForSelector('#task-card.ok');
    await p.waitForSelector('#bf-sec-א h3 .bk-v');
    assert.equal(await pill(), 'נכון ✓ הוכחה');
    await shot('r14-bank-strip');
  });

  await step('חלון "השאלה": ב-body ומעל הכול, נגרר מעל הלוח, לא יוצא מהחלון, ומקומו נשמר', async () => {
    assert.ok(await p.evaluate(() => document.getElementById('bank-float').parentElement === document.body && getComputedStyle(document.getElementById('bank-float')).position === 'fixed'));
    const h = await box('#bank-float .bf-head');
    await p.mouse.move(h.r - 60, h.y + h.h / 2);     // הכפתורים בצד שמאל של הכותרת
    await p.mouse.down();
    await p.mouse.move(h.r - 60 + 120, h.y + h.h / 2 + 140, { steps: 6 });
    await p.mouse.up();
    const f = await box('#bank-float');
    assert.ok(f.y > h.y + 100, 'זז');
    assert.ok(f.x < 290, 'מעל הלוח (הקצה השמאלי של החלון מעל פאנל הצד)');
    await shot('r14-question-window');
    // לא יוצא מהחלון של הדפדפן
    const h2 = await box('#bank-float .bf-head');
    await p.mouse.move(h2.r - 60, h2.y + h2.h / 2);
    await p.mouse.down();
    await p.mouse.move(-500, 2000, { steps: 4 });
    await p.mouse.up();
    const g = await box('#bank-float');
    assert.ok(g.x >= 0 && g.b <= 900 + 1, JSON.stringify(g));
    // מחזירים, ונשמר אחרי טעינה
    await p.mouse.move((await box('#bank-float .bf-head')).r - 60, (await box('#bank-float .bf-head')).y + 10);
    await p.mouse.down();
    await p.mouse.move(150, 300, { steps: 4 });
    await p.mouse.up();
    const k = await box('#bank-float');
    await p.waitForTimeout(300);
    await p.reload();
    await p.waitForSelector('#bank-float:not([hidden])');
    await p.waitForTimeout(500);                       // אחרי תנועת הפתיחה של החלון
    const k2 = await box('#bank-float');
    assert.ok(Math.abs(k2.x - k.x) < 2 && Math.abs(k2.y - k.y) < 2, JSON.stringify([k, k2]));
  });

  await step('מזעור לגלולה, וסגירה: "השאלה" בפס פותח שוב', async () => {
    await p.click('#bank-float [data-bf=min]');
    const m = await box('#bank-float');
    assert.ok(m.h < 60, 'גלולה ' + m.h);
    await shot('r14-question-min');
    await p.click('#bank-float [data-bf=min]');
    assert.ok((await box('#bank-float')).h > 200);
    await p.click('#bank-float [data-bf=close]');
    assert.equal(await p.isVisible('#bank-float'), false);
    await p.click('#lang-strip [data-t=question]');
    assert.ok(await p.isVisible('#bank-float'));
  });

  await step('שום הדרכה לא נפתחת לבד: לא בלשונית חדשה, לא ב"חדש"; רק "?" פותח', async () => {
    await p.click('#bank-float [data-bf=close]');
    await p.click('#type-tabs [data-type=nfa]');
    assert.equal(await guideShown(), false, 'לשונית ריקה: בלי חלון פתיחה');
    await p.click('[data-cmd=new]');
    assert.equal(await guideShown(), false, '"חדש": בלי חלון פתיחה');
    await p.click('#type-tabs [data-type=dfa]');
    assert.equal(await guideShown(), false);
    await p.click('#guide-btn');
    assert.equal(await guideShown(), true);
    assert.ok(['float', 'empty'].includes(await mode()));
    await p.click('#guide [data-g=close]');
  });

  assert.deepEqual(errors, [], 'אין שגיאות בקונסול');
  console.log('\nכל ' + n + ' הבדיקות עברו');
  await browser.close();
  await launcher.close();
})().catch((e) => { console.error('✗', e.message || e); process.exit(1); });
