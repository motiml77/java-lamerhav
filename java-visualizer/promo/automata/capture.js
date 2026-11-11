/*
 * promo/automata/capture.js - מצלם את התוכנה "מודלים חישוביים" האמיתית (ערכה כהה, 1440x900, חדות כפולה) לסרטון הפרומו.
 * כל תמונה היא מצב אמיתי של התוכנה; הסרטון (promo.html) רק מניע ביניהן. הדף מוגש מהשרת המדומה של התוכנה (מורה מחובר, בשביל המאגר).
 * הרצה: node capture.js   (פלט: frames/*.jpg + frames/list.json)
 */
'use strict';
const fs = require('fs');
const path = require('path');
const { chromium } = require('playwright');
const APP = path.resolve(__dirname, '..', '..', 'automata', 'app');
const mock = require(path.join(APP, 'e2e', 'mock-launcher.js'));
const H = require(path.join(APP, 'e2e', 'helpers.js'));
const OUT = path.join(__dirname, 'frames');

(async () => {
  fs.mkdirSync(OUT, { recursive: true });
  const launcher = await mock.start({ access: { state: 'teacher', name: 'רונית כהן', email: 'ronit@example.org', role: 'teacher', apps: ['dsviz', 'automata'], canSignOut: true } });
  const browser = await chromium.launch();
  const ctx = await browser.newContext({ viewport: { width: 1440, height: 900 }, deviceScaleFactor: 2, colorScheme: 'dark' });
  const p = await ctx.newPage();
  const errors = [];
  p.on('pageerror', (e) => errors.push(e.message));
  await p.goto(launcher.url);
  await p.waitForSelector('#guide:not([hidden])');
  await p.evaluate(() => document.fonts.ready);
  await p.click('#guide [data-g=close]');

  const list = {};
  const shot = async (scene, wait) => {
    await p.waitForTimeout(wait ?? 380);
    await p.evaluate(() => document.querySelectorAll('.toast').forEach((t) => t.remove()));
    list[scene] = list[scene] || [];
    const name = scene + String(list[scene].length + 1).padStart(2, '0');
    await p.screenshot({ path: path.join(OUT, name + '.jpg'), type: 'jpeg', quality: 90 });
    list[scene].push(name);
    process.stdout.write(name + ' ');
  };
  const machine = (spec) => p.evaluate((sp) => {
    App.change('בנייה', () => {
      const m = App.machine;
      m.states = []; m.transitions = [];
      sp.states.forEach(([id, acc, x, y]) => AutomataModel.addState(m, { id, name: id, x, y, accepting: acc }));
      m.start = sp.states[0][0];
      for (const [f, t, l] of sp.edges) AutomataModel.addTransition(m, f, t, AutomataModel.parseLabels(m.type, l).labels);
    });
    if (sp.fit) App.canvas.fit();
  }, spec);
  const run = async (scene, word, n, every) => {
    await p.fill('#word-input', word);
    await p.evaluate(() => { App.stopRun(true); App.runCmd('next'); });
    await shot(scene, 300);
    const last = await p.evaluate(() => App.run.trace.steps.length - 1);
    n = Math.min(n, last);
    for (let i = 1; i <= n; i++) {
      await p.evaluate(() => App.runCmd('next'));
      if (i % (every || 1) === 0 || i === n) await shot(scene, 330);
    }
  };
  const fresh = async () => {
    await p.keyboard.press('Escape');
    await p.click('[data-cmd=new]').catch(() => {});
    const d = await p.waitForSelector('.dialog', { timeout: 800 }).catch(() => null);
    if (d) await p.click('.dialog [data-r="1"]');
    await p.waitForTimeout(250);
  };

  // ---------------------------------------------------------------- A: "התוכנה בונה"
  await shot('a');                                                         // קנבס ריק
  await p.click('[data-cmd=lang-menu]');
  await p.click('[data-cmd=lang-auto]');
  await p.waitForSelector('.lang-dialog');
  const F = 'w ∈ {a,b}* | מכילה aba';
  for (const k of [3, 7, 11, 15, F.length]) { await p.fill('#lang-input', F.slice(0, k)); await shot('a', 260); }
  await H.build(p);
  await shot('a', 600);
  await run('a', 'babab', 5);
  await p.click('[data-run=end]').catch(() => {});
  await shot('a', 500);

  // ---------------------------------------------------------------- B: "אני בונה, התוכנה בודקת"
  await p.keyboard.press('Escape');
  await p.click('[data-cmd=lang-menu]');
  await p.click('[data-cmd=lang-practice]');
  await p.waitForSelector('.lang-dialog');
  await p.fill('#lang-input', 'w ∈ {a,b}* | #a(w) זוגי');
  await shot('b', 400);
  await H.start(p, false);
  await shot('b');
  const X = 420, Y = 300;
  const wrong = [['q0', 'q1', 'b'], ['q1', 'q0', 'b'], ['q0', 'q0', 'a'], ['q1', 'q1', 'a']];
  await machine({ states: [['q0', true, X, Y], ['q1', false, X + 300, Y]], edges: wrong, fit: true });   // רק בשביל מיקום המבט
  await machine({ states: [['q0', true, X, Y]], edges: [] });
  await shot('b', 250);
  await machine({ states: [['q0', true, X, Y], ['q1', false, X + 300, Y]], edges: [] });
  await shot('b', 250);
  await machine({ states: [['q0', true, X, Y], ['q1', false, X + 300, Y]], edges: wrong.slice(0, 2) });
  await shot('b', 250);
  await machine({ states: [['q0', true, X, Y], ['q1', false, X + 300, Y]], edges: wrong });
  await shot('b', 250);
  await H.check(p);
  await shot('b', 500);
  await machine({ states: [['q0', true, X, Y], ['q1', false, X + 300, Y]], edges: [['q0', 'q1', 'a'], ['q1', 'q0', 'a'], ['q0', 'q0', 'b'], ['q1', 'q1', 'b']] });
  await shot('b', 300);
  await p.click('#lang-strip [data-t=check]');
  await p.waitForSelector('#task-card.ok');
  await shot('b', 500);
  // סיום המשימה
  await p.click('#lang-strip [data-t=more]');
  await p.click('#ts-menu [data-t=end]').catch(() => {});
  await p.waitForTimeout(200);

  // ---------------------------------------------------------------- C: מחסנית וטיורינג
  await p.evaluate(() => App.loadExample('anbn'));
  await p.waitForTimeout(400);
  await run('c', 'aaabbb', 8);
  await p.evaluate(() => App.loadExample('tm-palindrome'));
  await p.waitForTimeout(400);
  await run('d', 'abba', 24, 3);

  // ---------------------------------------------------------------- E: מאגר שאלות
  await p.keyboard.press('Escape');
  await fresh();
  await p.click('[data-cmd=bank]');
  await p.waitForSelector('.bk-window');
  await shot('e', 500);
  await p.click('[data-bk-topic=dfa]');
  await shot('e');
  await p.click('[data-bk-q=dfa-even-a]');
  await shot('e');
  await p.click('[data-bk-topic=pda]');
  await p.click('[data-bk-q=pda-anbn]');
  await shot('e');
  await p.click('[data-bk-topic=tm]');
  await p.click('[data-bk-q=tm-binary-inc]');
  await shot('e');
  await p.click('[data-bk-q=dfa-even-a]');
  await p.click('[data-bk=create]');
  await p.waitForTimeout(500);
  await shot('e', 500);

  const topics = await p.evaluate(() => (window.AutomataBank && AutomataBank.questions ? AutomataBank.questions.length : null)).catch(() => null);
  fs.writeFileSync(path.join(OUT, 'list.json'), JSON.stringify({ frames: list, questions: topics }, null, 1));
  fs.writeFileSync(path.join(OUT, 'list.js'), 'window.FRAMES = ' + JSON.stringify(list) + ';\n');
  console.log('\nerrors:', errors.length ? errors : 'none');
  await browser.close();
  await launcher.close?.();
  process.exit(0);
})().catch((e) => { console.error(e); process.exit(1); });
