/*
 * e2e/bank.js - "מאגר שאלות" (Playwright): פתיחת המאגר, בחירת שאלה, "צור תרגיל", בנייה של אס"ד נכון בעכבר, "בדיקה" בחלון "השאלה",
 * סעיף ב' (שלוש מילים בשפה ושלוש מחוצה לה), "סיימתי את התרגיל" עם כרטיס האישור, וה-V במאגר. ואז מכונת טיורינג שגויה לשאלה מחשבת:
 * הבדיקה מראה את הקלטים שנכשלו. גם: ההתקדמות נשמרת אחרי טעינה מחדש. הדף מוגש משרת מדומה של התוכנה (e2e/mock-launcher.js), עם מורה מחובר. צילומים: docs/shots/r9-*.png. הרצה: node e2e/bank.js
 */
'use strict';
const path = require('path');
const assert = require('assert/strict');
const { chromium } = require('playwright');

const mock = require('./mock-launcher.js');
const OUT = path.resolve(__dirname, '..', '..', 'docs', 'shots');

(async () => {
  // המאגר פתוח רק בתוכנה המקומית ולמי שמחובר: שרת מדומה של התוכנה, עם מורה מחובר
  const launcher = await mock.start({ access: { state: 'teacher', name: 'רונית כהן', email: 'ronit@example.org', role: 'teacher', apps: ['dsviz', 'automata'], canSignOut: true } });
  const browser = await chromium.launch();
  const errors = [];
  let n = 0;
  const step = async (name, fn) => { await fn(); console.log('✓ ' + (++n) + '. ' + name); };
  const p = await (await browser.newContext({ viewport: { width: 1440, height: 900 } })).newPage();
  p.on('console', (m) => { if (m.type() === 'error') errors.push(m.text()); });
  p.on('pageerror', (e) => errors.push(e.message));
  await p.goto(launcher.url);
  await p.waitForSelector('.st');
  await p.evaluate(() => document.fonts.ready);
  await p.click('#guide [data-g=close]');
  const shot = async (name) => { await p.waitForTimeout(350); await p.evaluate(() => document.querySelectorAll('.toast').forEach((t) => t.remove())); await p.screenshot({ path: path.join(OUT, name + '.png') }); };
  const at = (name) => p.evaluate((nm) => { const s = App.machine.states.find((q) => q.name === nm); const r = document.querySelector('.stage').getBoundingClientRect(); const q = App.canvas.toScreen(s), z = App.canvas.uiScale(); return { x: r.x + q.x * z, y: r.y + q.y * z }; }, name);
  const click = async (pt) => { await p.mouse.move(pt.x, pt.y, { steps: 3 }); await p.mouse.down(); await p.mouse.up(); await p.waitForTimeout(120); };
  const edge = async (from, to, letters) => { await click(await at(from)); await click(await at(to)); for (const c of letters) await p.keyboard.press(c); await p.keyboard.press('Enter'); await p.waitForTimeout(100); };
  const accept = async (name) => { await click(await at(name)); await p.keyboard.press('Escape'); await p.keyboard.press('KeyA'); };
  /** נקודה פנויה בקנבס: מימין לחלון "השאלה" (החלון יושב בצד שמאל) */
  const free = () => p.evaluate(() => { const s = document.querySelector('.stage').getBoundingClientRect(), f = document.querySelector('#bank-float').getBoundingClientRect(); return { x: (f.right + s.right) / 2, y: s.y + s.height / 2 }; });
  const sec = (l) => '#bf-sec-' + l;

  await step('כפתור "מאגר שאלות" בסרגל העליון פותח את החלון: ארבעה נושאים, לכל אחד כמה שאלות וכמה סומנו', async () => {
    await p.click('[data-cmd=bank]');
    await p.waitForSelector('.bk-window');
    const topics = await p.$$eval('.bk-topic', (els) => els.map((e) => [e.querySelector('.bk-tname').textContent, e.querySelector('.bk-tcount').textContent]));
    assert.deepEqual(topics.map((t) => t[0]), ['אס"ד', 'אסל"ד', 'אוטומט מחסנית', 'מכונת טיורינג']);
    topics.forEach((t) => assert.match(t[1], /^0\/([89]|\d\d)$/));
    assert.match(await p.textContent('.bk-pr-head'), /0 מתוך 3\d/);
  });

  await step('נושא נפתח לרשימה: נקודה בצבע הקושי מקל לקשה; חיפוש וסינון לפי רמה', async () => {
    await p.click('[data-bk-topic=dfa]');
    const lv = await p.$$eval('.bk-qs .bk-q .bk-dot', (els) => els.map((e) => e.classList[1]));
    assert.ok(lv.length >= 8 && lv[0] === 'easy' && lv[lv.length - 1] === 'hard');
    assert.deepEqual(lv.slice().sort((a, b) => ['easy', 'medium', 'hard'].indexOf(a) - ['easy', 'medium', 'hard'].indexOf(b)), lv);
    await p.fill('#bk-search', 'פלינדרום');
    assert.deepEqual(await p.$$eval('.bk-q', (els) => els.map((e) => e.dataset.bkQ)), ['pda-wwr', 'tm-palindrome']);
    await p.fill('#bk-search', '');
    await p.click('[data-bk-level=hard]');
    assert.ok((await p.$$eval('.bk-q .bk-dot', (els) => els.map((e) => e.classList[1]))).every((c) => c === 'hard'));
    await p.click('[data-bk-level=""]');
  });

  await step('שאלה נבחרת: השם, הרמה, הנוסח, השפה בנוסחה, הא"ב, סוג המכונה הנדרש, דוגמאות וסעיפים', async () => {
    await p.click('[data-bk-q=dfa-even-a]');
    assert.equal((await p.textContent('.bk-qtitle h2')).trim(), 'מספר זוגי של a');
    assert.match(await p.textContent('.bk-qsub'), /אס"ד · רמה: קל/);
    const cards = await p.$$eval('.bk-main .bk-card h3', (els) => els.map((e) => e.textContent));
    assert.deepEqual(cards, ["סעיף א'", "סעיף ב'"]);
    assert.ok(await p.isVisible('.bk-main .bk-formula'));
    assert.match(await p.textContent('.bk-main .bk-need'), /נדרש: אוטומט סופי דטרמיניסטי/);
    assert.match(await p.textContent('.bk-main .bk-meta'), /Σ = \{a, b\}/);
    assert.ok((await p.$$('.bk-main .bk-ex .w-chip.in')).length >= 3);
    assert.equal(await p.textContent('[data-bk=create]'), 'צור תרגיל');
    await shot('r9-window');
  });

  await step('שאלה עם סעיפים של מכונת טיורינג מחשבת: טבלת קלט ופלט', async () => {
    await p.click('[data-bk-topic=tm]');
    await p.click('[data-bk-q=tm-binary-inc]');
    assert.ok((await p.$$('.bk-main .bk-cases tbody tr')).length >= 3);
    await p.click('[data-bk-topic=pda]');
    await p.click('[data-bk-q=pda-anbn]');
    await shot('r9-question');
    await p.click('[data-bk-q=dfa-even-a]');
  });

  await step('"צור תרגיל": אס"ד ריק עם הא"ב של השאלה, והשאלה בחלון צף על הקנבס (בלי חלון שמכסה)', async () => {
    await p.click('[data-bk=create]');
    await p.waitForSelector('#bank-float:not([hidden])');
    assert.equal(await p.$('.bk-window'), null);
    const m = await p.evaluate(() => ({ type: App.active, states: App.machine.states.length, alphabet: App.machine.alphabet, lang: App.machine.language && App.machine.language.text }));
    assert.deepEqual(m, { type: 'dfa', states: 0, alphabet: ['a', 'b'], lang: 'w ∈ {a,b}* | #a(w) זוגי' });
    assert.match(await p.textContent('#bank-float .bf-info'), /על הקנבס: אוטומט סופי דטרמיניסטי של סעיף א'/);
    assert.deepEqual(await p.$$eval('.bf-jump', (els) => els.map((e) => e.textContent)), ["סעיף א'", "סעיף ב'"]);
    assert.ok(await p.isVisible(sec('א') + ' [data-bf-check]'));
  });

  await step('💡 רמז נפתח בתוך הסעיף', async () => {
    await p.click(sec('א') + ' [data-bf-hint]');
    assert.ok((await p.textContent(sec('א') + ' .bf-hint')).length > 20);
    await p.click(sec('א') + ' [data-bf-hint]');
    assert.equal(await p.$(sec('א') + ' .bf-hint'), null);
  });

  await step('בדיקה על קנבס ריק: הסבר במקום תוצאה', async () => {
    await p.click(sec('א') + ' [data-bf-check]');
    assert.match(await p.textContent(sec('א') + ' .bf-result'), /אין עדיין מכונה/);
  });

  await step('בונים בעכבר אס"ד שגוי (אחרי a תמיד דוחה): הבדיקה מראה את המילים הקצרות שנכשלו', async () => {
    const c = await free();
    await p.mouse.dblclick(c.x - 110, c.y); await p.waitForTimeout(150);
    await p.mouse.dblclick(c.x + 110, c.y); await p.waitForTimeout(150);
    await edge('q0', 'q1', 'a');
    await edge('q0', 'q0', 'b');
    await edge('q1', 'q1', 'ab');
    await accept('q0');
    await p.click(sec('א') + ' [data-bf-check]');
    await p.waitForSelector(sec('א') + ' .lr-box.err');
    const missing = await p.$$eval(sec('א') + ' .bf-result .w-chip.in', (els) => els.map((e) => e.textContent));
    assert.ok(missing.includes('aa'), 'aa צריכה להתקבל: ' + missing);
    await p.click(sec('א') + ' .bf-result .w-chip.in');
    await p.waitForSelector('#result-banner.err:not([hidden])');
    await p.keyboard.press('Escape');
  });

  await step('מתקנים (q1 חוזר ל-q0 עם a): "בדיקה" עוברת, עם הוכחה, וסעיף א\' מסומן', async () => {
    await p.evaluate(() => { const m = App.machine; const t = m.transitions.find((x) => x.from === x.to && x.from === m.states[1].id); App.change('תיקון', () => { t.labels = t.labels.filter((l) => l.read !== 'a'); }); });
    await edge('q1', 'q0', 'a');
    await p.click(sec('א') + ' [data-bf-check]');
    await p.waitForSelector(sec('א') + ' .lr-box.ok');
    assert.match(await p.textContent(sec('א') + ' .lr-box.ok'), /בדיוק את השפה/);
    assert.ok(await p.$(sec('א') + ' h3 .bk-v'));
    assert.ok(await p.$('.bf-jump.on .bk-v'));
    await shot('r9-float');
  });

  await step('המכונה של הסעיף נשמרת בהתקדמות בכל שינוי', async () => {
    await p.waitForTimeout(400);
    const w = await p.evaluate(() => JSON.parse(localStorage.getItem(AutomataBank.KEY)).questions['dfa-even-a'].work['א']);
    assert.equal(JSON.parse(w).states.length, 2);
  });

  await step('סעיף ב\': שלוש מילים בשפה ושלוש מחוצה לה; טעות מסומנת, ואחרי תיקון הסעיף נפתר', async () => {
    await p.click('[data-bf-jump=ב]');
    const words = ['aa', 'bb', 'ε', 'a', 'ab', 'aa'];
    const fields = await p.$$(sec('ב') + ' .bf-word');
    for (let i = 0; i < 6; i++) await fields[i].fill(words[i]);
    await p.click(sec('ב') + ' [data-bf-check]');
    const items = await p.$$eval(sec('ב') + ' .bf-items li', (els) => els.map((e) => e.className + ':' + e.querySelector('span').textContent));
    assert.equal(items[5], 'err:המילה כבר הופיעה');
    await (await p.$$(sec('ב') + ' .bf-word'))[5].fill('bab');
    await p.click(sec('ב') + ' [data-bf-check]');
    await p.waitForSelector(sec('ב') + ' .lr-box.ok');
  });

  await step('ההתקדמות נשמרת אחרי טעינה מחדש: החלון חוזר עם אותו תרגיל והמכונה', async () => {
    await p.waitForTimeout(400);
    await p.reload();
    await p.waitForSelector('#bank-float:not([hidden])');
    assert.equal(await p.evaluate(() => App.machine.states.length), 2);
    assert.ok(await p.$(sec('א') + ' h3 .bk-v'));
    assert.equal(await (await p.$$(sec('ב') + ' .bf-word'))[5].inputValue(), 'bab');
  });

  await step('"סיימתי את התרגיל": כרטיס בתוך החלון (לשמור או להתחיל נקי), ו-V לשאלה במאגר', async () => {
    await p.click('[data-bf=finish]');
    assert.ok(await p.isVisible('.bf-confirm'));
    assert.equal(await p.isChecked('.bf-confirm input[value=clean]'), true);
    await p.click('.bf-confirm input[value=keep]');
    assert.match(await p.textContent('[data-bf=finish-yes]'), /שמירת המכונה/);
    await p.click('[data-bf=finish-yes]');
    assert.equal(await p.isVisible('#bank-float'), false);
    assert.equal(await p.evaluate(() => App.machine.states.length), 2, 'המכונה נשארה');
    await p.click('[data-cmd=bank]');
    await p.waitForSelector('.bk-q[data-bk-q=dfa-even-a] .bk-v');
    assert.match(await p.textContent('.bk-topic[data-bk-topic=dfa] .bk-tcount'), /^1\/\d+$/);
    assert.match(await p.textContent('.bk-pr-head'), /1 מתוך/);
    assert.equal(await p.textContent('[data-bk=create]'), 'פתח את התרגיל');
    await shot('r9-done');
  });

  await step('שאלה של מכונת טיורינג מחשבת (הוספת 1 בבינארי): מכונה שגויה, והבדיקה מראה את הקלטים שנכשלו', async () => {
    await p.click('[data-bk-topic=tm]');
    await p.click('[data-bk-q=tm-binary-inc]');
    await p.click('[data-bk=create]');
    await p.waitForSelector('#bank-float:not([hidden])');
    assert.equal(await p.evaluate(() => App.active), 'tm');
    const c = await free();
    await p.mouse.dblclick(c.x, c.y); await p.waitForTimeout(150);
    await accept('q0');
    const label = await p.getAttribute('[data-bf-check]', 'data-bf-check');
    await p.click('[data-bf-check]');
    await p.waitForSelector(sec(label) + ' .bf-fail tbody tr');
    const rows = await p.$$eval(sec(label) + ' .bf-fail tbody tr', (els) => els.map((r) => Array.from(r.cells).map((x) => x.textContent)));
    assert.ok(rows.length >= 3);
    assert.deepEqual(rows[0], ['1011', '1100', '1011'], 'המכונה עוצרת מיד: הסרט נשאר כמו הקלט');
    assert.match(await p.textContent(sec(label) + ' .lr-box.err'), /טועה ב-\d+ מתוך \d+ קלטים/);
    assert.equal(await p.evaluate(() => AutomataBank.Progress({ get: (k) => localStorage.getItem(k), set() {} }).sectionDone('tm-binary-inc', 'א')), false);
    await shot('r9-fail');
  });

  await step('טעינת דוגמה סוגרת את התרגיל (העבודה נשמרת, ו"פתח את התרגיל" מחזיר אותה)', async () => {
    await p.click('[data-cmd=examples]');
    await p.click('#examples-menu [role=menuitem]');
    assert.equal(await p.isVisible('#bank-float'), false);
    await p.click('[data-cmd=bank]');
    assert.equal(await p.getAttribute('.bk-q.sel', 'data-bk-q'), 'tm-binary-inc', 'השאלה האחרונה נבחרת');
    assert.equal(await p.textContent('[data-bk=create]'), 'פתח את התרגיל');
    await p.click('[data-bk=create]');
    await p.waitForSelector('#bank-float:not([hidden])');
    assert.equal(await p.evaluate(() => App.active + ':' + App.machine.states.length), 'tm:1');
  });

  assert.deepEqual(errors, [], 'אין שגיאות בקונסול');
  console.log('\nכל ' + n + ' הבדיקות עברו');
  await browser.close();
  await launcher.close();
})().catch((e) => { console.error(e); process.exit(1); });
