/*
 * e2e/expedit.js - סבב 13 (Playwright): חזקות שאפשר לשנות לכל ביטוי. המקש "חזקה" מכניס ^( ) עם הסמן בפנים; המקשים xⁿ, xᵐ… משאירים את
 * החזקה נבחרת; לחיצה על חזקה בתצוגה היפה פותחת עורך קטן (Enter מחיל, Esc מבטל, שגיאה בעברית), ומשנה רק אותה בטקסט.
 * צילומים: docs/shots/r13-*.png. הרצה: node e2e/expedit.js
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
  const p = await (await browser.newContext({ viewport: { width: 1440, height: 900 } })).newPage();
  p.on('console', (x) => { if (x.type() === 'error') errors.push(x.text()); });
  p.on('pageerror', (e) => errors.push(e.message));
  await p.goto(URL);
  await p.waitForSelector('#guide:not([hidden])');
  await p.evaluate(() => document.fonts.ready);
  await p.click('#guide [data-g=close]');
  const shot = async (name) => { await p.waitForTimeout(350); await p.evaluate(() => document.querySelectorAll('.toast').forEach((t) => t.remove())); await p.screenshot({ path: path.join(OUT, name + '.png') }); };
  const text = async (sel) => (await p.textContent(sel)).replace(/\s+/g, ' ').trim();
  const val = () => p.inputValue('#lang-input');
  const sel = () => p.$eval('#lang-input', (e) => e.value.slice(e.selectionStart, e.selectionEnd) + '|' + e.selectionStart + '-' + e.selectionEnd);
  const sups = () => p.$$eval('#lang-pretty .lp-math > sup.ex', (els) => els.map((e) => e.textContent));
  const editSup = async (i, value, key) => {
    await p.click('#lang-pretty .lp-math > sup.ex >> nth=' + i);
    await p.waitForSelector('.exp-pop');
    if (value != null) { await p.fill('.exp-input', value); await p.keyboard.press(key || 'Enter'); await p.waitForTimeout(250); }
  };

  await step('המקש "חזקה" מכניס ^( ) עם הסמן בפנים; מקלידים m+k+2, והנוסחה נקראת ומסווגת', async () => {
    await H.openEditor(p);
    await p.click('#lang-input');
    await p.keyboard.type('a');
    assert.equal(await p.getAttribute('.lang-kb .lk[data-ins="^(‸)"]', 'aria-label'), 'חזקה: כותבים בתוך הסוגריים כל ביטוי, למשל a^(m+k+2)');
    assert.equal(await p.$('.lang-kb .lk[data-ins="ⁿ⁺ᵐ"]'), null, 'המקש הקבוע n+m הוחלף');
    await p.click('.lang-kb .lk[data-ins="^(‸)"]');
    assert.equal(await val(), 'a^()');
    assert.equal(await sel(), '|3-3');
    await p.keyboard.type('m+k+2');
    await p.waitForTimeout(300);
    assert.equal(await val(), 'a^(m+k+2)');
    assert.equal(await text('#lang-pretty .lp-math'), 'L = { am+k+2 }');
    assert.deepEqual(await sups(), ['m+k+2']);
    assert.equal(await text('#lang-pretty .lp-note'), 'כל משתנה הוא מספר שלם אי-שלילי: m, k ≥ 0');
    assert.equal(await text('#lang-class'), 'רגולרית');
  });

  await step('מקש מוכן (xᵐ): החזקה נבחרת, והקלדה מחליפה אותה מיד', async () => {
    await p.keyboard.press('End');
    await p.keyboard.type(' b');
    await p.click('.lang-kb .lk[data-ins="^(⟦m⟧)"]');
    assert.equal(await val(), 'a^(m+k+2) b^(m)');
    assert.equal(await sel(), 'm|13-14');
    await p.waitForTimeout(300);
    await shot('r13-exp-selected');
    await p.keyboard.type('n');
    assert.equal(await val(), 'a^(m+k+2) b^(n)');
    await p.fill('#lang-input', 'a^(m+k+2) b^m');
    await p.waitForTimeout(300);
    assert.equal(await text('#lang-class'), 'חסרת הקשר, לא רגולרית');
  });

  await step('ריחוף על חזקה בתצוגה: סמן של לחיצה והסבר; לחיצה פותחת עורך קטן עם הביטוי', async () => {
    const s0 = '#lang-pretty .lp-math > sup.ex >> nth=0';
    assert.equal(await p.getAttribute(s0, 'title'), 'לחיצה לעריכת החזקה');
    await p.hover(s0);
    assert.equal(await p.$eval('#lang-pretty .lp-math > sup.ex', (e) => getComputedStyle(e).cursor), 'pointer');
    await editSup(0);
    assert.equal(await p.inputValue('.exp-input'), 'm+k+2');
    assert.equal(await p.evaluate(() => document.activeElement.className), 'exp-input');
    const pop = await p.$eval('.exp-pop', (e) => { const r = e.getBoundingClientRect(); return { w: r.width, h: r.height }; });
    assert.ok(pop.w < 200 && pop.h < 60, JSON.stringify(pop));
    await p.fill('.exp-input', '2n+1');
    await shot('r13-editor');
  });

  await step('Enter מחיל: רק החזקה הזו משתנה בטקסט, והנוסחה נקראת מחדש', async () => {
    await p.keyboard.press('Enter');
    await p.waitForTimeout(300);
    assert.equal(await p.$('.exp-pop'), null);
    assert.equal(await val(), 'a^(2n+1) b^m');
    assert.deepEqual(await sups(), ['2n+1', 'm']);
    assert.equal(await text('#lang-class'), 'רגולרית');
    assert.equal(await sel(), '^(2n+1)|1-8', 'החזקה החדשה מסומנת בשדה');
  });

  await step('בנייה ובדיקה עדיין עובדות אחרי העריכה', async () => {
    await p.click('[data-l=build]');
    if (await p.waitForSelector('.dialog', { timeout: 1200 }).catch(() => null)) await p.click('.dialog [data-r="1"]');
    await p.waitForTimeout(400);
    assert.equal(await p.evaluate(() => App.machine.type), 'dfa');
    await p.click('#ts-pill');
    await p.waitForSelector('#task-card.ok', { timeout: 20000 });
    assert.match(await text('#task-card'), /זו הוכחה/);
    await shot('r13-result');
  });

  await step('ביטוי שהמנוע לא מקבל: הודעה בעברית בעורך, והנוסחה לא משתנה; Esc מבטל', async () => {
    await p.click('#lang-strip [data-t=edit]');          // "עריכת הנוסחה" בפס המשימה פותח את העורך עם הנוסחה
    await p.waitForSelector('.lang-dialog');
    await editSup(1, 'n-m');
    assert.match(await text('.exp-err'), /מופיע רק עם מינוס/);
    assert.ok(await p.$('.exp-pop'));
    await p.keyboard.press('Escape');
    assert.equal(await p.$('.exp-pop'), null);
    assert.equal(await val(), 'a^(2n+1) b^m');
  });

  await step('סימנים עיליים (aⁿbⁿ), ומשתנה חדש בחזקה (k)', async () => {
    await p.fill('#lang-input', 'aⁿbⁿ');
    await p.waitForTimeout(300);
    await editSup(1, 'n+k');
    assert.equal(await val(), 'aⁿbⁿ⁺ᵏ');
    assert.equal(await text('#lang-pretty .lp-note'), 'כל משתנה הוא מספר שלם אי-שלילי: n, k ≥ 0');
  });

  await step('איחוד: לכל חזקה המקום שלה; מהמקלדת (Tab ו-Enter)', async () => {
    await p.fill('#lang-input', '{ a^n b^n } ∪ { b^(2n) a^n }');
    await p.waitForTimeout(300);
    assert.deepEqual(await p.$$eval('#lang-pretty sup.ex', (els) => els.map((e) => e.textContent)), ['n', 'n', '2n', 'n']);
    await p.focus('#lang-pretty sup.ex >> nth=2');
    await p.keyboard.press('Enter');
    await p.waitForSelector('.exp-pop');
    assert.equal(await p.inputValue('.exp-input'), '2n');
    await p.fill('.exp-input', 'n+1');
    await p.keyboard.press('Enter');
    await p.waitForTimeout(300);
    assert.equal(await val(), '{ a^n b^n } ∪ { b^(n+1) a^n }');
  });

  assert.deepEqual(errors, [], 'אין שגיאות בקונסול');
  console.log('\nכל ' + n + ' הבדיקות עברו');
  await browser.close();
})().catch((e) => { console.error('✗', e.message || e); process.exit(1); });
