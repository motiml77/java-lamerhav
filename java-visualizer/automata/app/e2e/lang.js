/*
 * e2e/lang.js - לשונית "שפה" ב-Chromium (Playwright), עם הנחות (assert) וצילומים ל-docs/shots/lang-*.png:
 * הקלדה ותצוגה יפה, מקלדת הסימנים, שגיאה בעברית, בנייה והרצה, בדיקה מדויקת (הצלחה וכישלון), לחיצה על דוגמה נגדית,
 * אוטומט מחסנית ובדיקה חסומה, וקובץ תרגיל למורים. הרצה: node e2e/lang.js
 */
'use strict';
const path = require('path');
const fs = require('fs');
const os = require('os');
const assert = require('assert/strict');
const { chromium } = require('playwright');

const URL = 'file://' + path.resolve(__dirname, '..', 'index.html');
const OUT = path.resolve(__dirname, '..', '..', 'docs', 'shots');

(async () => {
  const browser = await chromium.launch();
  const errors = [];
  let n = 0;
  const step = async (name, fn) => { await fn(); console.log('✓ ' + (++n) + '. ' + name); };
  async function open(theme) {
    const ctx = await browser.newContext({ viewport: { width: 1440, height: 900 }, colorScheme: theme, acceptDownloads: true });
    const p = await ctx.newPage();
    p.on('console', (m) => { if (m.type() === 'error') errors.push(m.text()); });
    p.on('pageerror', (e) => errors.push(e.message));
    await p.goto(URL);
    await p.waitForSelector('.st');
    await p.evaluate(() => document.fonts.ready);
    await p.click('#guide [data-g=close]');
    await p.click('[data-tab=lang]');
    return p;
  }
  const shot = async (p, name) => { await p.waitForTimeout(350); await p.evaluate(() => document.querySelectorAll('.toast').forEach((t) => t.remove())); await p.screenshot({ path: path.join(OUT, name + '.png') }); };
  const write = async (p, text) => { await p.fill('#lang-input', text); await p.waitForTimeout(250); };
  const text = (p, sel) => p.textContent(sel);
  const words = (p, sel) => p.$$eval(sel + ' .w-chip', (els) => els.map((e) => e.textContent));
  const machine = (p) => p.evaluate(() => ({ type: App.machine.type, states: App.machine.states.length, names: App.machine.states.map((s) => s.name), lang: App.machine.language }));

  let p = await open('light');

  await step('מקלידים ב-ASCII ורואים מיד כתיבה יפה, סיווג ומילים לדוגמה', async () => {
    await write(p, '');           // הדוגמה שנטענת בהתחלה כבר באה עם שפה משלה
    await p.click('#lang-input');
    await p.keyboard.type('a^n b^m | n >= 2', { delay: 5 });
    await p.waitForTimeout(300);
    assert.equal((await text(p, '#lang-pretty')).replace(/\s+/g, ' ').trim(), 'L = { anbm | n ≥ 2 }');
    assert.equal(await p.$eval('#lang-pretty sup', (e) => e.textContent), 'n');
    assert.equal(await text(p, '#lang-class'), 'רגולרית');
    assert.deepEqual((await words(p, '#lang-live .ll-row:nth-child(1)')).slice(0, 3), ['aa', 'aaa', 'aab']);
    assert.equal((await words(p, '#lang-live .ll-row:nth-child(2)'))[0], 'ε');
    assert.match(await text(p, '#lang-strip'), /n ≥ 2/);
    await shot(p, 'lang-panel');
  });

  await step('מקלדת הסימנים מכניסה למקום הסמן, בלי לעבור לאנגלית', async () => {
    await write(p, '');
    for (const ins of ['a', 'ⁿ', 'b', 'ⁿ']) await p.click('.lang-kb .lk[data-ins="' + ins + '"]');
    await p.waitForTimeout(250);
    assert.equal(await p.inputValue('#lang-input'), 'aⁿbⁿ');
    assert.equal(await text(p, '#lang-class'), 'חסרת הקשר, לא רגולרית');
    assert.ok(await p.evaluate(() => document.activeElement.id === 'lang-input'), 'הפוקוס נשאר בשדה');
    await p.click('.lang-kb .lk[data-ins=" | "]');
    await p.keyboard.type('n');
    await p.click('.lang-kb .lk[data-ins=" ≥ "]');
    await p.keyboard.type('1');
    await p.waitForTimeout(250);
    assert.equal(await p.inputValue('#lang-input'), 'aⁿbⁿ | n ≥ 1');
    assert.deepEqual((await words(p, '#lang-live .ll-row:nth-child(1)')).slice(0, 2), ['ab', 'aabb']);
  });

  await step('שגיאה: הודעה בעברית, המקום מסומן, ויש הצעה', async () => {
    await write(p, 'a^n b^m | n >');
    assert.match(await text(p, '#lang-pretty b'), /התנאי נקטע באמצע/);
    assert.match(await text(p, '#lang-pretty span'), /למשל/);
    assert.ok(await p.$('#lang-pretty .le-src mark'));
    assert.ok(await p.$eval('#lang-input', (e) => e.classList.contains('bad')));
    await shot(p, 'lang-error');
    await p.click('[data-l=check]');
    assert.match(await text(p, '#lang-result'), /קודם צריך לתקן את הנוסחה/);
  });

  await step('בנה אוטומט: שואל לפני שמחליף, בונה אס"ד מינימלי, ואפשר להריץ מילה', async () => {
    await write(p, 'a^n b^m | n >= 2');
    await p.click('[data-l=build]');
    await p.waitForSelector('.dialog');
    assert.match(await text(p, '.dialog h2'), /לבנות אס"ד מהשפה/);
    await p.click('.dialog [data-r="1"]');
    await p.waitForTimeout(400);
    const m = await machine(p);
    assert.deepEqual([m.type, m.states], ['dfa', 5]);
    assert.ok(m.names.includes('מלכודת'));
    assert.equal(m.lang.text, 'a^n b^m | n >= 2');
    assert.match(await text(p, '#lang-result'), /נבנה אס"ד עם 5 מצבים \(המינימלי\)/);
    await p.fill('#word-input', 'aaab');
    await p.press('#word-input', 'Enter');
    await p.click('[data-run=end]');
    await p.waitForSelector('#result-banner.ok:not([hidden])');
    await shot(p, 'lang-built');
    await p.keyboard.press('Escape');
  });

  await step('בדיקה מול השפה: אוטומט נכון מקבל הוכחה', async () => {
    await p.click('[data-tab=lang]');
    await p.click('[data-l=check]');
    await p.waitForSelector('#lang-result .lr-box.ok');
    assert.match(await text(p, '#lang-result'), /האוטומט מקבל בדיוק את השפה/);
    assert.match(await text(p, '#lang-result'), /זו הוכחה/);
    await shot(p, 'lang-check-ok');
  });

  await step('אוטומט שגוי: שתי רשימות של מילים, רמז, ולחיצה על מילה מריצה אותה', async () => {
    await write(p, 'a^n b^m | n >= 1');
    await p.click('[data-l=check]');
    await p.waitForSelector('#lang-result .lr-box.err');
    assert.match(await text(p, '#lang-result .lr-box'), /האוטומט עוד לא מקבל את השפה/);
    assert.match(await text(p, '#lang-result .lr-box small'), /המילה a בשפה, אבל האוטומט שלך דוחה אותה/);
    assert.deepEqual(await words(p, '#lang-result .lr-list'), ['a', 'ab', 'abb', 'abbb', 'abbbb']);
    assert.equal(await p.$$eval('#lang-result .lr-list', (e) => e.length), 1, 'אין מילים עודפות, אז יש רק רשימה אחת');
    await shot(p, 'lang-check-fail');
    await p.click('#lang-result .w-chip[data-word="ab"]');
    await p.waitForSelector('#result-banner.err:not([hidden])', { timeout: 15000 });
    assert.equal(await p.inputValue('#word-input'), 'ab');
    assert.match(await text(p, '#result-banner'), /המילה ab נדחית/);
    await p.keyboard.press('Escape');
    await p.click('[data-l=add-tests]');
    assert.ok(await p.evaluate(() => App.machine.tests.some((t) => t.word === 'abbb' && t.expect === true)));
  });

  await step('שפה חסרת הקשר: נבנה אוטומט מחסנית, והבדיקה חסומה (ומנוסחת כך)', async () => {
    await write(p, 'a^n b^n');
    await p.click('[data-l=build]');
    await p.waitForSelector('.dialog');
    await p.click('.dialog [data-r="1"]');
    await p.waitForTimeout(400);
    const m = await machine(p);
    assert.deepEqual([m.type, m.states], ['pda', 3]);
    await p.click('[data-l=check]');
    await p.waitForSelector('#lang-result .lr-box.ok', { timeout: 20000 });
    const t = await text(p, '#lang-result');
    assert.match(t, /נבדקו כל [\d,]+ המילים עד אורך \d+\. לא נמצאה טעות/);
    assert.match(t, /זו בדיקה, לא הוכחה/);
    await shot(p, 'lang-pda');
  });

  await step('שפה שאין לה בנייה אוטומטית: נאמר ביושר, ואפשר עדיין לבדוק מולה', async () => {
    await write(p, 'w ∈ {a,b}* | #a(w) = #b(w)');
    await p.click('[data-l=build]');
    assert.match(await text(p, '#lang-result'), /עוד לא נתמכת/);
    await p.click('[data-l=check]');
    await p.waitForSelector('#lang-result .lr-box.err', { timeout: 20000 });
    assert.equal((await words(p, '#lang-result .lr-list'))[0], 'ba');
  });

  await step('למורים: קובץ תרגיל עם השפה בלבד, והתלמיד לא יכול ללחוץ "בנה אוטומט"', async () => {
    await p.click('#type-tabs [data-type=dfa]');
    await p.click('[data-tab=lang]');
    await write(p, 'w ∈ {a,b}* | מסתיימת ב-ab');
    await p.uncheck('[data-l=allow]');
    const [dl] = await Promise.all([p.waitForEvent('download'), p.click('[data-l=exercise]')]);
    const file = path.join(os.tmpdir(), 'dsviz-exercise-' + Date.now() + '.json');
    await dl.saveAs(file);
    const data = JSON.parse(fs.readFileSync(file, 'utf8'));
    assert.deepEqual([data.states.length, data.transitions.length, data.language.locked, data.language.allowBuild], [0, 0, true, false]);
    await p.setInputFiles('#file-input', file);
    await p.waitForTimeout(500);
    fs.unlinkSync(file);
    assert.equal(await p.getAttribute('[data-tab=lang]', 'aria-selected'), 'true');
    assert.ok(await p.$eval('#lang-input', (e) => e.readOnly));
    assert.equal(await p.$('[data-l=build]'), null);
    assert.equal((await machine(p)).states, 0);
    assert.match(await text(p, '#lang-strip'), /תרגיל/);
    await shot(p, 'lang-exercise');
  });
  await p.context().close();

  await step('מצב כהה: אותה לשונית', async () => {
    p = await open('dark');
    await write(p, 'w ∈ {a,b}* | #a(w) אי-זוגי וגם מסתיימת ב-b');
    await p.click('[data-l=check]');
    await p.waitForSelector('#lang-result .lr-box.err');
    await shot(p, 'lang-dark');
    await p.context().close();
  });

  await browser.close();
  assert.deepEqual(errors, [], 'שגיאות בקונסול');
  console.log('הכול עבר (' + n + ' שלבים)');
})().catch((e) => { console.error('✗', e.message); process.exit(1); });
