/*
 * e2e/lang2.js - סבב 4 בלשונית "שפה" (Playwright), עם הנחות וצילומים ל-docs/shots/r4-*.png:
 * חזקות בכמה משתנים (aⁿbᵐcⁿ⁺ᵐ -> אוטומט מחסנית -> הרצה), שארית (aⁿ | n%3=0 -> אס"ד של 3 מצבים, הוכחה),
 * מקלדת הסימנים לחזקות, איחוד של שפות, וריבוע (סיווג בלי בנייה, ובדיקה חסומה). הרצה: node e2e/lang2.js
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
  const ctx = await browser.newContext({ viewport: { width: 1440, height: 900 } });
  const p = await ctx.newPage();
  p.on('console', (m) => { if (m.type() === 'error') errors.push(m.text()); });
  p.on('pageerror', (e) => errors.push(e.message));
  await p.goto(URL);
  await p.waitForSelector('.st');
  await p.evaluate(() => document.fonts.ready);
  await p.click('#guide [data-g=close]');
  await p.click('[data-tab=lang]');
  const shot = async (name) => { await p.waitForTimeout(350); await p.evaluate(() => document.querySelectorAll('.toast').forEach((t) => t.remove())); await p.screenshot({ path: path.join(OUT, name + '.png') }); };
  const text = async (sel) => (await p.textContent(sel)).replace(/\s+/g, ' ').trim();
  const write = async (t) => { await p.fill('#lang-input', t); await p.waitForTimeout(300); };
  const words = (sel) => p.$$eval(sel + ' .w-chip', (els) => els.map((e) => e.textContent));
  const build = async () => { await p.click('[data-l=build]'); const d = await p.waitForSelector('.dialog', { timeout: 1500 }).catch(() => null); if (d) await p.click('.dialog [data-r="1"]'); await p.waitForTimeout(400); };
  const machine = () => p.evaluate(() => ({ type: App.machine.type, states: App.machine.states.length }));

  await step('מקלידים aⁿbᵐcⁿ⁺ᵐ: כתיבה יפה, סיווג ומילים לדוגמה', async () => {
    await write('');
    await p.click('#lang-input');
    await p.keyboard.type('a^n b^m c', { delay: 5 });
    await p.click('.lang-kb .lk[data-ins="^("]');
    await p.keyboard.type('n');
    await p.click('.lang-kb .lk[data-ins="+"]');
    await p.keyboard.type('m)');
    await p.waitForTimeout(300);
    assert.equal(await p.inputValue('#lang-input'), 'a^n b^m c^(n+m)');
    assert.equal(await text('#lang-pretty'), 'L = { anbmcn+m }');
    assert.equal(await p.$$eval('#lang-pretty sup', (e) => e.map((x) => x.textContent).join(',')), 'n,m,n+m');
    assert.equal(await text('#lang-class'), 'חסרת הקשר, לא רגולרית');
    assert.deepEqual((await words('#lang-live .ll-row:nth-child(1)')).slice(0, 3), ['ε', 'ac', 'bc']);
    assert.match(await text('#ls-formula'), /L = \{ anbmcn\+m \}/);
  });

  await step('בנה אוטומט: אוטומט מחסנית, נבדק מול הנוסחה, ו-aabccc מתקבלת', async () => {
    await build();
    const m = await machine();
    assert.equal(m.type, 'pda');
    assert.ok(m.states <= 8, 'מצבים: ' + m.states);
    assert.match(await text('#lang-result'), /נבנה אוטומט מחסנית עם \d+ מצבים\. הוא נבדק מול הנוסחה/);
    await p.fill('#word-input', 'aabccc');
    await p.press('#word-input', 'Enter');
    await p.click('[data-run=end]');
    await p.waitForSelector('#result-banner.ok:not([hidden])');
    await shot('r4-pda');
    await p.keyboard.press('Escape');
    await p.click('[data-tab=lang]');
  });

  await step('aⁿ עם n%3=0: אס"ד מינימלי של 3 מצבים, ובדיקה מול השפה היא הוכחה', async () => {
    await p.click('#type-tabs [data-type=dfa]');
    await p.click('[data-tab=lang]');
    await write('a^n | n%3=0');
    assert.equal(await text('#lang-pretty'), 'L = { an | n mod 3 = 0 }');
    assert.equal(await text('#lang-class'), 'רגולרית');
    await build();
    assert.deepEqual(await machine(), { type: 'dfa', states: 3 });
    assert.match(await text('#lang-result'), /נבנה אס"ד עם 3 מצבים \(המינימלי\)/);
    for (const f of ['a^n | n mod 3 = 0', 'a^n | n ≡ 0 (mod 3)']) {
      await write(f);
      await p.click('[data-l=check]');
      await p.waitForSelector('#lang-result .lr-box.ok');
      assert.match(await text('#lang-result'), /זו הוכחה/, f);
    }
    await shot('r4-mod');
  });

  await step('מקלדת הסימנים: ^( + − · % ≡ נכנסים למקום הסמן', async () => {
    await write('');
    await p.click('#lang-input');
    await p.keyboard.type('a');
    await p.click('.lang-kb .lk[data-ins="^("]');
    await p.keyboard.type('n');
    await p.click('.lang-kb .lk[data-ins="·"]');
    await p.keyboard.type('m) | n');
    await p.click('.lang-kb .lk[data-ins=" % "]');
    await p.keyboard.type('2 = 0');
    await p.waitForTimeout(300);
    assert.equal(await p.inputValue('#lang-input'), 'a^(n·m) | n % 2 = 0');
    assert.equal(await text('#lang-class'), 'לא הצלחתי לסווג');
  });

  await step('איחוד של שפות: { aⁿbⁿ } ∪ { bⁿaⁿ }, חסרת הקשר, ונבנה אוטומט מחסנית', async () => {
    await write('a^n b^n ∪ b^n a^n');
    assert.equal(await text('#lang-pretty'), 'L = { anbn } ∪ { bnan }');
    assert.equal(await text('#lang-class'), 'חסרת הקשר, לא רגולרית');
    assert.deepEqual((await words('#lang-live .ll-row:nth-child(1)')).slice(0, 3), ['ε', 'ab', 'ba']);
    await build();
    assert.equal((await machine()).type, 'pda');
    await p.fill('#word-input', 'bbaa');
    await p.press('#word-input', 'Enter');
    await p.click('[data-run=end]');
    await p.waitForSelector('#result-banner.ok:not([hidden])');
    await p.keyboard.press('Escape');
    await p.click('[data-tab=lang]');
    await shot('r4-union');
  });

  await step('ריבוע: aⁿb^(n²) מסווגת "לא חסרת הקשר" עם הסבר, אין בנייה, והבדיקה מול השפה חסומה', async () => {
    await write('a^n b^(n^2)');
    assert.equal(await text('#lang-pretty'), 'L = { anbn2 }');
    assert.equal(await text('#lang-class'), 'לא חסרת הקשר');
    assert.match(await p.getAttribute('#lang-class', 'title'), /המרווחים בין האורכים האפשריים גדלים/);
    assert.deepEqual((await words('#lang-live .ll-row:nth-child(1)')).slice(0, 3), ['ε', 'ab', 'aabbbb']);
    await p.click('[data-l=build]');
    assert.match(await text('#lang-result'), /עוד לא נתמכת/);
    await p.click('[data-l=check]');
    await p.waitForSelector('#lang-result .lr-box.err', { timeout: 20000 });
    assert.match(await text('#lang-result'), /האוטומט עוד לא מקבל את השפה/);
    await shot('r4-square');
  });

  await step('שגיאה בחזקה: הודעה בעברית שאומרת מה כן אפשר', async () => {
    await write('a^(n-m)');
    assert.match(await text('#lang-pretty'), /המשתנה m מופיע רק עם מינוס/);
    assert.match(await text('#lang-pretty'), /a\^\(n\+m\) b\^\(n−m\)/);
    await write('a^(n mod 2)');
    assert.match(await text('#lang-pretty'), /בחזקה אי אפשר mod/);
  });

  await browser.close();
  assert.deepEqual(errors, [], 'שגיאות בקונסול');
  console.log('הכול עבר (' + n + ' שלבים)');
})().catch((e) => { console.error('✗', e.message); process.exit(1); });
