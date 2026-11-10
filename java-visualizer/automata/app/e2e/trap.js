/*
 * e2e/trap.js - "הצג מצב מלכודת" (Playwright): תיבת הסימון בפינת הקנבס, הסתרה של המלכודת ושל הקשתות אליה,
 * ההרצה והבדיקה מול השפה לא משתנות, וההעדפה נשמרת. צילומים: docs/shots/r8-*.png. הרצה: node e2e/trap.js
 */
'use strict';
const path = require('path');
const assert = require('assert/strict');
const { chromium } = require('playwright');

const URL = 'file://' + path.resolve(__dirname, '..', 'index.html');
const OUT = path.resolve(__dirname, '..', '..', 'docs', 'shots');

(async () => {
  const browser = await chromium.launch();
  const errors = [];
  let n = 0;
  const step = async (name, fn) => { await fn(); console.log('✓ ' + (++n) + '. ' + name); };
  const p = await (await browser.newContext({ viewport: { width: 1440, height: 900 } })).newPage();
  p.on('console', (m) => { if (m.type() === 'error') errors.push(m.text()); });
  p.on('pageerror', (e) => errors.push(e.message));
  await p.goto(URL);
  await p.waitForSelector('.st');
  await p.evaluate(() => document.fonts.ready);
  await p.click('#guide [data-g=close]');
  const shot = async (name) => { await p.waitForTimeout(300); await p.evaluate(() => document.querySelectorAll('.toast').forEach((t) => t.remove())); await p.screenshot({ path: path.join(OUT, name + '.png') }); };
  const canvas = () => p.evaluate(() => ({ states: Array.from(document.querySelectorAll('.st .st-name')).map((e) => e.textContent), edges: document.querySelectorAll('.ed[data-edge]').length, labels: document.querySelectorAll('.lb[data-edge]').length, model: App.machine.states.length, trans: App.machine.transitions.length }));
  const run = async (w, ok, then) => {
    await p.fill('#word-input', w);
    await p.press('#word-input', 'Enter');
    await p.click('[data-run=end]');
    await p.waitForSelector('#result-banner.' + (ok ? 'ok' : 'err') + ':not([hidden])');
    if (then) await then();
    await p.keyboard.press('Escape');
  };

  await step('מכונה בלי מלכודת: אין תיבת סימון', async () => {
    assert.equal(await p.isVisible('#trap-toggle'), false);
  });

  await step('aⁿbⁿ | 1≤n≤3: התיבה מסומנת כברירת מחדל, ולא נוגעת בתג התקינות או ב-?', async () => {
    await p.click('[data-tab=lang]');
    await p.fill('#lang-input', 'a^n b^n | 1≤n≤3');
    await p.waitForTimeout(300);
    await p.click('[data-l=build]');
    await p.waitForSelector('.dialog');
    await p.click('.dialog [data-r="1"]');
    await p.waitForTimeout(400);
    assert.ok(await p.isVisible('#trap-toggle'));
    assert.equal(await p.isChecked('#trap-toggle input'), true);
    const box = (s) => p.$eval(s, (e) => { const r = e.getBoundingClientRect(); return [r.left, r.top, r.right, r.bottom]; });
    const t = await box('#trap-toggle'), c = await box('#canvas-status'), g = await box('#guide-btn');
    const hit = (a, b) => a[0] < b[2] && b[0] < a[2] && a[1] < b[3] && b[1] < a[3];
    assert.ok(!hit(t, c) && !hit(t, g), 'בלי חפיפה');
    assert.ok(t[2] > c[2] - 30, 'בפינה הימנית, מתחת לתג');
    assert.equal(await p.$eval('#trap-toggle', (e) => Math.round(parseFloat(getComputedStyle(e).fontSize))), 11);
    assert.ok((await canvas()).states.includes('מלכודת'));
    await shot('r8-checked');
  });

  await step('כיבוי: המלכודת והקשתות אליה נעלמות מהקנבס בלבד; המכונה, ההרצה והבדיקה לא משתנות', async () => {
    const before = await canvas();
    const pos = await p.evaluate(() => App.machine.states.map((s) => [s.x, s.y]));
    await p.click('#trap-toggle');
    await p.waitForTimeout(200);
    const after = await canvas();
    assert.equal(after.states.includes('מלכודת'), false);
    assert.equal(after.states.length, before.states.length - 1);
    const intoTrap = await p.evaluate(() => { const t = AutomataModel.trapStates(App.machine); return App.machine.transitions.filter((x) => t.has(x.to)).length; });
    assert.equal(after.edges, before.edges - intoTrap);
    assert.equal(after.labels, before.labels - intoTrap);
    assert.deepEqual([after.model, after.trans], [before.model, before.trans], 'המודל לא השתנה');
    assert.deepEqual(await p.evaluate(() => App.machine.states.map((s) => [s.x, s.y])), pos, 'המצבים לא זזו');
    await run('ab', true);
    await run('ba', false, async () => assert.match(await p.textContent('#machine-view'), /מלכודת \(מוסתר\)/));
    await p.click('[data-l=check]');
    await p.waitForSelector('#lang-result .lr-box.ok');
    assert.match(await p.textContent('#lang-result'), /זו הוכחה/);
    const saved = await p.evaluate(() => JSON.parse(AutomataModel.serialize(App.machine)).states.length);
    assert.equal(saved, before.model, 'השמירה כוללת את המלכודת');
    await shot('r8-unchecked');
  });

  await step('ההעדפה נשמרת אחרי טעינה מחדש, וסימון מחזיר את המלכודת', async () => {
    await p.reload();
    await p.waitForSelector('.st');
    assert.equal(await p.isChecked('#trap-toggle input'), false);
    assert.equal((await canvas()).states.includes('מלכודת'), false);
    await p.click('#trap-toggle');
    await p.waitForTimeout(200);
    assert.ok((await canvas()).states.includes('מלכודת'));
  });

  await browser.close();
  assert.deepEqual(errors, [], 'שגיאות בקונסול');
  console.log('הכול עבר (' + n + ' שלבים)');
})().catch((e) => { console.error('✗', e.message); process.exit(1); });
