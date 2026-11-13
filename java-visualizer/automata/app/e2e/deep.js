/*
 * e2e/deep.js - הבדיקה היסודית מול הנוסחה ב-Chromium (Playwright), במקרים שבדיקה שטחית טועה בהם:
 * אס"ד מול שפה לא רגולרית (אף פעם לא "נכון"), אוטומט מחסנית עם לולאת ε שדוחפת (מוכרע, בלי "לא הוכרע"),
 * אוטומט מחסנית נכון (מה נבדק ומה הוכח), ומסלול נסתר שמקבל מילה ארוכה אחת. צילומים: docs/shots/deep-*.png. הרצה: node e2e/deep.js
 */
'use strict';
const path = require('path');
const assert = require('assert/strict');
const { chromium } = require('playwright');
const H = require('./helpers.js');

const URL = 'file://' + path.resolve(__dirname, '..', 'index.html');
const OUT = path.resolve(__dirname, '..', '..', 'docs', 'shots');

(async () => {
  const browser = await chromium.launch();
  const errors = [];
  let n = 0;
  const step = async (name, fn) => { await fn(); console.log('✓ ' + (++n) + '. ' + name); };
  const ctx = await browser.newContext({ viewport: { width: 1440, height: 900 } });
  const p = await ctx.newPage();
  p.on('console', (m) => { if (m.type() === 'error') errors.push(m.text()); });
  p.on('pageerror', (e) => errors.push(e.message));
  await p.goto(URL);
  await p.waitForSelector('#guide:not([hidden])');
  await p.evaluate(() => document.fonts.ready);
  await p.click('#guide [data-g=close]');
  const shot = async (name) => { await p.waitForTimeout(350); await p.evaluate(() => document.querySelectorAll('.toast').forEach((t) => t.remove())); await p.screenshot({ path: path.join(OUT, name + '.png') }); };
  const text = (sel) => p.textContent(sel);
  const chips = (sel) => p.$$eval(sel + ' .w-chip', (els) => els.map((e) => e.textContent));
  /** מחליף את המכונה שעל הקנבס: states [[שם, מקבל, x, y]], edges [[מ, אל, תוויות]] */
  const machine = (spec) => p.evaluate((sp) => {
    App.change('בנייה', () => {
      const m = App.machine;
      m.states = []; m.transitions = [];
      if (m.type === 'pda') m.stackAlphabet = ['Z', 'A'];
      sp.states.forEach(([id, acc, x, y]) => AutomataModel.addState(m, { id, name: id, x, y, accepting: !!acc }));
      m.start = sp.states[0][0];
      for (const [f, t, l] of sp.edges) AutomataModel.addTransition(m, f, t, AutomataModel.parseLabels(m.type, l).labels);
    });
    App.canvas.fit();
  }, spec);
  const task = async (formula, type) => { await H.write(p, formula, 'practice'); await H.start(p, false, type); };

  await step('אס"ד מול aⁿbⁿ: "לא נכון", עם מילים נגדיות ועם ההסבר שהשפה לא רגולרית', async () => {
    await task('a^n b^n', 'dfa');
    await machine({ states: [['q0', 1, 300, 300], ['q1', 1, 560, 300]], edges: [['q0', 'q0', 'a'], ['q0', 'q1', 'b'], ['q1', 'q1', 'b']] });
    assert.equal(await H.check(p), 'fail');
    assert.match(await text('#task-card'), /השפה הזו לא רגולרית, ולכן שום אס"ד או אסל"ד לא מקבל בדיוק אותה/);
    assert.deepEqual((await chips('#task-card')).slice(0, 2), ['a', 'b']);
    await shot('deep-dfa-nonregular');
  });

  await step('אס"ד שסופר עד 45 (הטעות הראשונה באורך 92): עדיין "לא נכון: השפה לא רגולרית", בלי מילה', async () => {
    const K = 45, states = [], edges = [];
    for (let i = 0; i <= K; i++) states.push(['a' + i, i === 0, 100 + i * 60, 200]);
    for (let i = 0; i < K; i++) states.push(['b' + i, i === 0, 100 + i * 60, 400]);
    for (let i = 0; i < K; i++) edges.push(['a' + i, 'a' + (i + 1), 'a']);
    for (let i = 1; i <= K; i++) edges.push(['a' + i, 'b' + (i - 1), 'b']);
    for (let i = 1; i < K; i++) edges.push(['b' + i, 'b' + (i - 1), 'b']);
    await machine({ states, edges });
    assert.equal(await H.check(p, 40000), 'fail');
    assert.equal(await text('#ts-pill'), 'לא נכון: השפה לא רגולרית');
    assert.match(await text('#task-card'), /אוטומט סופי לא יכול לזכור מספר שאין לו גבול/);
    assert.deepEqual(await chips('#task-card'), []);
  });

  await step('אוטומט מחסנית נכון ל-aⁿbⁿ: כל המילים הקצרות, מילים ארוכות, ומה שהוכח', async () => {
    await task('a^n b^n', 'pda');
    await machine({ states: [['q0', 0, 300, 300], ['q1', 0, 560, 300], ['q2', 1, 430, 480]], edges: [['q0', 'q0', 'a,Z/AZ | a,A/AA'], ['q0', 'q1', 'b,A/ε'], ['q1', 'q1', 'b,A/ε'], ['q1', 'q2', 'ε,Z/Z'], ['q0', 'q2', 'ε,Z/Z']] });
    assert.equal(await H.check(p, 40000), 'ok');
    const t = await text('#task-card');
    assert.match(t, /נבדקו כל [\d,]+ המילים עד אורך 1[4-9] ועוד [\d,]+ מילים ארוכות \(עד אורך \d+\)\. לא נמצאה טעות/);
    assert.match(t, /הוכח שכל מילה שהמכונה מקבלת היא מהצורה שבנוסחה/);
    assert.match(t, /אין אלגוריתם שמוכיח שאוטומט מחסנית מקבל בדיוק שפה נתונה/);
    assert.doesNotMatch(t, /לא הוכרעה/);
    await shot('deep-pda-ok');
  });

  await step('אותו אוטומט, ועוד מסלול נסתר שמקבל את (ab)³⁰: המילה נמצאת, אף שהיא באורך 60', async () => {
    const w = 'ab'.repeat(30), states = [['q0', 0, 300, 300], ['q1', 0, 560, 300], ['q2', 1, 430, 480]];
    const edges = [['q0', 'q0', 'a,Z/AZ | a,A/AA'], ['q0', 'q1', 'b,A/ε'], ['q1', 'q1', 'b,A/ε'], ['q1', 'q2', 'ε,Z/Z'], ['q0', 'q2', 'ε,Z/Z']];
    for (let i = 1; i <= w.length; i++) states.push(['x' + i, i === w.length, 100 + (i % 20) * 70, 620 + Math.floor(i / 20) * 110]);
    Array.from(w).forEach((c, i) => edges.push([i ? 'x' + i : 'q0', 'x' + (i + 1), c + ',Z/Z']));
    await machine({ states, edges });
    assert.equal(await H.check(p, 40000), 'fail');
    assert.deepEqual(await chips('#task-card'), [w]);
    assert.match(await text('#task-card'), /האוטומט שלך מקבל את המילה (ab){30}, אבל היא לא בשפה/);
  });

  await step('אוטומט מחסנית שמנחש כמה לדחוף (לולאת ε): מוכרע, ונכון ל-aⁿbᵐ עם n ≤ m', async () => {
    await task('a^n b^m | n <= m', 'pda');
    await machine({ states: [['q0', 0, 300, 300], ['q1', 0, 560, 300], ['q2', 1, 820, 300]], edges: [['q0', 'q0', 'a,ε/A | ε,ε/A'], ['q0', 'q1', 'ε,ε/ε'], ['q1', 'q1', 'b,A/ε'], ['q1', 'q2', 'ε,Z/Z']] });
    assert.equal(await H.check(p, 40000), 'ok');
    const t = await text('#task-card');
    assert.match(t, /לא נמצאה טעות/);
    assert.doesNotMatch(t, /לא הוכרעה/);
    // ואותו אוטומט מול aⁿbⁿ: הטעות נמצאת (b מתקבלת), ולא מוסתרת
    await task('a^n b^n', 'pda');
    await machine({ states: [['q0', 0, 300, 300], ['q1', 0, 560, 300], ['q2', 1, 820, 300]], edges: [['q0', 'q0', 'a,ε/A | ε,ε/A'], ['q0', 'q1', 'ε,ε/ε'], ['q1', 'q1', 'b,A/ε'], ['q1', 'q2', 'ε,Z/Z']] });
    assert.equal(await H.check(p, 40000), 'fail');
    assert.equal((await chips('#task-card'))[0], 'b');
    await shot('deep-pda-guess');
  });

  assert.deepEqual(errors, [], 'שגיאות בקונסול: ' + errors.join(' | '));
  await browser.close();
  console.log('\nכל ' + n + ' הבדיקות עברו');
})().catch((e) => { console.error(e); process.exit(1); });
