/*
 * e2e/paths.js - הדרכים בחלון ההרצה (Playwright): הסבר במילים, שורה לכל דרך עם מצב, קלט, מחסנית ומצב הדרך,
 * לחיצה על דרך מדגישה אותה על הקנבס, וסיכום שנותן שם לדרך המקבלת. צילומים: docs/shots/r5-*.png. הרצה: node e2e/paths.js
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
  const text = async (sel) => (await p.textContent(sel)).replace(/\s+/g, ' ').trim();
  const rows = () => p.$$eval('#machine-view .p-row', (els) => els.map((e) => Array.from(e.children).map((c) => c.textContent).join(' ')));
  const shot = async (name) => { await p.waitForTimeout(300); await p.evaluate(() => document.querySelectorAll('.toast').forEach((t) => t.remove())); await p.screenshot({ path: path.join(OUT, name + '.png') }); };
  const runTo = async (word, steps) => {
    await p.fill('#word-input', word);
    await p.click('[data-run=next]');
    for (let i = 0; i < steps; i++) await p.click('[data-run=next]');
    await p.waitForTimeout(150);
  };

  await step('אוטומט מחסנית: ההסבר מדבר על דרכים, לא על "ענפים"', async () => {
    await p.click('#type-tabs [data-type=pda]');
    await p.click('[data-tab=lang]');
    await p.fill('#lang-input', 'a^n b^n ∪ b^n a^n');
    await p.waitForTimeout(300);
    await p.click('[data-l=build]');
    await p.waitForSelector('.dialog');
    await p.click('.dialog [data-r="1"]');
    await p.waitForTimeout(400);
    await p.fill('#word-input', 'bbaa');
    assert.equal(await text('#run-note'), 'לאוטומט יש כמה דרכים אפשריות. בודקים את כולן במקביל, וכל דרך היא ריצה נפרדת עם המחסנית שלה. המילה מתקבלת אם לפחות דרך אחת מגיעה למצב מקבל.');
    assert.equal(await p.evaluate(() => document.body.textContent.includes('ענף')), false);
  });

  await step('bbaa באמצע הריצה: שורה לכל דרך, עם מצב, קלט, מחסנית ומצב הדרך', async () => {
    await runTo('bbaa', 4);
    const r = await rows();
    assert.deepEqual(r, ['דרך 2 q4 bbaa ראשAAZ פעילה', 'דרך 1 q5 εbbaa ראשZ נתקעה באות b', 'דרך 3 q4 bbaa ראשAZ נתקעה באות b', 'דרך 4 q6 εbbaa ראשZ נתקעה באות b']);
    assert.match(await text('#machine-view .mv-paths .mv-label'), /דרכים 4/);
    await shot('r5-mid');
  });

  await step('לחיצה על דרך מדגישה רק אותה על הקנבס, ולחיצה נוספת מבטלת', async () => {
    const before = await p.$$eval('.st.active', (e) => e.length);
    await p.click('#machine-view .p-row:nth-child(2)');
    await p.waitForTimeout(150);
    assert.ok(await p.$('#machine-view .p-row.focus'));
    assert.deepEqual(await p.$$eval('.st.rejected .st-name', (e) => e.map((x) => x.textContent.trim())), ['q5']);
    assert.deepEqual(await p.$$eval('.st.active .st-name', (e) => e.map((x) => x.textContent.trim())), ['q5']);
    assert.equal(await p.$$eval('.ed.taken', (e) => e.length), 3, 'q0→q1→q3→q5');
    await p.click('#machine-view .p-row:nth-child(2)');
    await p.waitForTimeout(150);
    assert.equal(await p.$$eval('.st.active', (e) => e.length), before);
  });

  await step('בסוף: הסיכום אומר איזו דרך קיבלה, והבאנר גם', async () => {
    await p.click('[data-run=end]');
    await p.waitForSelector('#result-banner.ok:not([hidden])');
    assert.equal(await text('#run-note'), 'המילה מתקבלת: דרך 2 הגיעה ל-q6');
    assert.match(await text('#result-banner'), /דרך 2 הגיעה ל-q6/);
    assert.equal((await rows())[0], 'דרך 2 q6 bbaa ראשZ הגיעה למצב מקבל');
    await p.click('#machine-view .p-row:nth-child(1)');
    await p.waitForTimeout(150);
    assert.equal(await p.$$eval('.ed.taken', (e) => e.length), 5, 'q0→q2, הלולאה ב-q2, q2→q4, הלולאה ב-q4, q4→q6');
    await shot('r5-accepted');
  });

  await step('abba נדחית: כל הדרכים נתקעו', async () => {
    await p.keyboard.press('Escape');
    await p.fill('#word-input', 'abba');
    await p.press('#word-input', 'Enter');
    await p.click('[data-run=end]');
    await p.waitForSelector('#result-banner.err:not([hidden])');
    assert.equal(await text('#run-note'), 'המילה נדחית: כל הדרכים נתקעו או הסתיימו במצב לא מקבל');
    assert.ok((await rows()).every((r) => /נתקעה/.test(r)));
  });

  await step('אסל"ד: אותו הסבר בלי מחסנית, ושורה לכל דרך', async () => {
    await p.keyboard.press('Escape');
    await p.click('[data-cmd=examples]');
    await p.click('#examples-menu [data-example=third-last-a]');
    await p.waitForTimeout(300);
    await p.fill('#word-input', 'abab');
    assert.equal(await text('#run-note'), 'לאוטומט יש כמה דרכים אפשריות. בודקים את כולן במקביל, וכל דרך היא ריצה נפרדת. המילה מתקבלת אם לפחות דרך אחת מגיעה למצב מקבל.');
    await runTo('abab', 3);
    assert.deepEqual(await rows(), ['דרך 1 q0 abab פעילה', 'דרך 2 q1 abab פעילה', 'דרך 3 q3 abab פעילה']);
    await p.click('[data-run=end]');
    assert.equal(await text('#run-note'), 'המילה נדחית: כל הדרכים נתקעו או הסתיימו במצב לא מקבל');
    await shot('r5-nfa');
  });

  await browser.close();
  assert.deepEqual(errors, [], 'שגיאות בקונסול');
  console.log('הכול עבר (' + n + ' שלבים)');
})().catch((e) => { console.error('✗', e.message); process.exit(1); });
