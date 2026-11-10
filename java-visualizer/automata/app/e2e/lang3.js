/*
 * e2e/lang3.js - סבב 7 (Playwright): מקלדת הסימנים קטנה ב-20% (ועדיין נוחה ללחיצה), ותנאים על המשתנים בכתיב של ספרי הלימוד.
 * צילומים: docs/shots/r7-*.png. הרצה: node e2e/lang3.js
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
  await p.click('[data-tab=lang]');
  const text = async (sel) => (await p.textContent(sel)).replace(/\s+/g, ' ').trim();
  const write = async (t) => { await p.fill('#lang-input', t); await p.waitForTimeout(300); };
  const words = (sel) => p.$$eval(sel + ' .w-chip', (els) => els.map((e) => e.textContent));
  const shot = async (name) => { await p.waitForTimeout(300); await p.evaluate(() => document.querySelectorAll('.toast').forEach((t) => t.remove())); await p.screenshot({ path: path.join(OUT, name + '.png') }); };
  const build = async () => { await p.click('[data-l=build]'); const d = await p.waitForSelector('.dialog', { timeout: 1500 }).catch(() => null); if (d) await p.click('.dialog [data-r="1"]'); await p.waitForTimeout(400); };
  const machine = () => p.evaluate(() => ({ type: App.machine.type, states: App.machine.states.length }));

  await step('מקלדת הסימנים: קטנה ב-20%, אזור לחיצה של 24px לפחות, ו-, ו-∈ ℕ', async () => {
    const box = await p.$eval('.lang-kb .lk[data-ins=" ≥ "]', (e) => { const r = e.getBoundingClientRect(), a = getComputedStyle(e, '::after'); return { h: r.height, w: r.width, inset: a.inset }; });
    assert.ok(Math.abs(box.h - 23 * 0.9) < 1, 'גובה על המסך: ' + box.h);
    assert.equal(box.inset, '-2px');
    assert.ok((box.h + 4 * 0.9) >= 24, 'אזור לחיצה');
    // לחיצה בשוליים של הכפתור (מחוץ למסגרת, בתוך אזור הלחיצה) עדיין מכניסה את הסימן
    await write('');
    await p.click('#lang-input');
    await p.keyboard.type('a^n b^m | n');
    const r = await p.$eval('.lang-kb .lk[data-ins=" ∈ ℕ"]', (e) => { const b = e.getBoundingClientRect(); return { x: b.x + b.width / 2, y: b.y - 1 }; });
    await p.mouse.click(r.x, r.y);
    await p.click('.lang-kb .lk[data-ins=", "]');
    await p.keyboard.type('m');
    await p.click('.lang-kb .lk[data-ins=" ≥ "]');
    await p.keyboard.type('1');
    await p.waitForTimeout(300);
    assert.equal(await p.inputValue('#lang-input'), 'a^n b^m | n ∈ ℕ, m ≥ 1');
    assert.equal(await text('#lang-pretty .lp-math'), 'L = { anbm | n ∈ ℕ, m ≥ 1 }');
    await shot('r7-chips');
  });

  await step('aⁿbⁿ | n<1: רק המילה הריקה, וההסבר אומר שהשפה סופית', async () => {
    await write('a^n b^n | n<1');
    assert.equal(await text('#lang-pretty .lp-math'), 'L = { anbn | n < 1 }');
    assert.deepEqual(await words('#lang-live .ll-row:nth-child(1)'), ['ε']);
    assert.match(await text('#lang-live .lang-why'), /השפה סופית: מילה אחת/);
    await write('a^n | n<0');
    assert.match(await text('#lang-live .lang-why'), /השפה ריקה/);
    await write('a^n b^n | n<1');
    await shot('r7-n-lt-1');
  });

  await step('aⁿbᵐ | n<m: אוטומט מחסנית, ו-abb מתקבלת', async () => {
    await p.click('#type-tabs [data-type=pda]');
    await p.click('[data-tab=lang]');
    await write('a^n b^m | n<m');
    assert.equal(await text('#lang-class'), 'חסרת הקשר, לא רגולרית');
    await build();
    assert.equal((await machine()).type, 'pda');
    await p.fill('#word-input', 'abb');
    await p.press('#word-input', 'Enter');
    await p.click('[data-run=end]');
    await p.waitForSelector('#result-banner.ok:not([hidden])');
    await p.keyboard.press('Escape');
    await p.click('[data-tab=lang]');
    await shot('r7-n-lt-m');
  });

  await step('aⁿbⁿ | 1≤n≤3: שפה סופית, נבנה אס"ד, והבדיקה מולה היא הוכחה', async () => {
    await p.click('#type-tabs [data-type=dfa]');
    await p.click('[data-tab=lang]');
    await write('a^n b^n | 1≤n≤3');
    assert.equal(await text('#lang-pretty .lp-math'), 'L = { anbn | 1 ≤ n ≤ 3 }');
    assert.match(await text('#lang-live .lang-why'), /השפה סופית: 3 מילים/);
    await build();
    assert.equal((await machine()).type, 'dfa');
    await p.click('[data-l=check]');
    await p.waitForSelector('#lang-result .lr-box.ok');
    assert.match(await text('#lang-result'), /זו הוכחה/);
    await shot('r7-finite');
  });

  await step('הפרדה ב-/ ובהצהרות: aⁿbᵐ / n,m ≥ 1, ותנאי לא ליניארי מקבל הסבר', async () => {
    await write('a^n b^m / n,m ≥ 1');
    assert.equal(await text('#lang-pretty .lp-math'), 'L = { anbm | n, m ≥ 1 }');
    await write('a^n b^m | n·m < 5');
    assert.equal(await text('#lang-class'), 'לא הצלחתי לסווג');
    assert.match(await p.getAttribute('#lang-class', 'title'), /תנאי לא ליניארי/);
    await write('a^n b^n | k ≥ 1');
    assert.match(await text('#lang-pretty'), /המשתנה k לא מופיע באף חזקה/);
  });

  await browser.close();
  assert.deepEqual(errors, [], 'שגיאות בקונסול');
  console.log('הכול עבר (' + n + ' שלבים)');
})().catch((e) => { console.error('✗', e.message); process.exit(1); });
