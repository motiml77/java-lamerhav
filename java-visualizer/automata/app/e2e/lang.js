/*
 * e2e/lang.js - עורך הנוסחה ובדיקה מול השפה ב-Chromium (Playwright), עם הנחות (assert) וצילומים ל-docs/shots/lang-*.png:
 * הקלדה ותצוגה יפה, מקלדת הסימנים, שגיאה בעברית, בנייה והרצה, בדיקה מדויקת (הצלחה וכישלון) בפס המשימה, לחיצה על דוגמה נגדית,
 * אוטומט מחסנית ובדיקה חסומה, שפה בלי בנייה ("לבנות לבד ולבדוק"), וקובץ משימה לתלמידים. הרצה: node e2e/lang.js
 */
'use strict';
const path = require('path');
const fs = require('fs');
const os = require('os');
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
  async function open(theme) {
    const ctx = await browser.newContext({ viewport: { width: 1440, height: 900 }, colorScheme: theme, acceptDownloads: true });
    const p = await ctx.newPage();
    p.on('console', (m) => { if (m.type() === 'error') errors.push(m.text()); });
    p.on('pageerror', (e) => errors.push(e.message));
    await p.goto(URL);
    await p.waitForSelector('#guide:not([hidden])');
    await p.evaluate(() => document.fonts.ready);
    await p.click('#guide [data-g=close]');
    await p.evaluate(() => { App.loadFirstExample(); App.history[App.active] = { past: [], future: [] }; document.querySelectorAll('.toast').forEach((t) => t.remove()); });   // פתיחה ראשונה ריקה (סבב 11): הדוגמה הראשונה כמו פעם
    await H.openEditor(p);
    return p;
  }
  const shot = async (p, name) => { await p.waitForTimeout(350); await p.evaluate(() => document.querySelectorAll('.toast').forEach((t) => t.remove())); await p.screenshot({ path: path.join(OUT, name + '.png') }); };
  const write = (p, text, mode) => H.write(p, text, mode);
  const toast = (p) => p.evaluate(() => Array.from(document.querySelectorAll('.toast')).map((t) => t.textContent).join(' | '));
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
    assert.match(await text(p, '#ld-foot'), /אס"ד מינימלי/, 'שפה רגולרית: בוחרים אס"ד מינימלי או אסל"ד');
    await shot(p, 'lang-panel');
  });

  await step('מקלדת הסימנים מכניסה למקום הסמן, בלי לעבור לאנגלית', async () => {
    await write(p, '');
    for (const ins of ['a', '^(⟦n⟧)', 'b', '^(⟦n⟧)']) await p.click('.lang-kb .lk[data-ins="' + ins + '"]');
    await p.waitForTimeout(250);
    assert.equal(await p.inputValue('#lang-input'), 'a^(n)b^(n)', 'מקש של אות אחרי חזקה שנבחרה: ממשיך אחריה (סבב 13)');
    assert.equal(await text(p, '#lang-class'), 'חסרת הקשר, לא רגולרית');
    assert.ok(await p.evaluate(() => document.activeElement.id === 'lang-input'), 'הפוקוס נשאר בשדה');
    await p.click('.lang-kb .lk[data-ins=" | "]');
    await p.keyboard.type('n');
    await p.click('.lang-kb .lk[data-ins=" ≥ "]');
    await p.keyboard.type('1');
    await p.waitForTimeout(250);
    assert.equal(await p.inputValue('#lang-input'), 'a^(n)b^(n) | n ≥ 1');
    assert.deepEqual((await words(p, '#lang-live .ll-row:nth-child(1)')).slice(0, 2), ['ab', 'aabb']);
  });

  await step('שגיאה: הודעה בעברית, המקום מסומן, ויש הצעה', async () => {
    await write(p, 'a^n b^m | n >');
    assert.match(await text(p, '#lang-pretty b'), /התנאי נקטע באמצע/);
    assert.match(await text(p, '#lang-pretty span'), /למשל/);
    assert.ok(await p.$('#lang-pretty .le-src mark'));
    assert.ok(await p.$eval('#lang-input', (e) => e.classList.contains('bad')));
    await shot(p, 'lang-error');
    assert.match(await text(p, '#ld-foot'), /קודם מתקנים את הנוסחה/);
    assert.ok(await p.$eval('#ld-foot .btn.primary', (e) => e.disabled));
  });

  await step('בנה אוטומט: שואל לפני שמחליף, בונה אס"ד מינימלי, ואפשר להריץ מילה', async () => {
    await write(p, 'a^n b^m | n >= 2');
    await p.click('[data-l=build]');
    await p.waitForSelector('.dialog');
    assert.match(await text(p, '.dialog h2'), /בלשונית "אס"ד" כבר יש מכונה/);
    assert.equal(await text(p, '.dialog [data-r=alt]'), 'לשמור לקובץ ואז להחליף');
    await p.click('.dialog [data-r="1"]');
    await p.waitForTimeout(400);
    const m = await machine(p);
    assert.deepEqual([m.type, m.states], ['dfa', 5]);
    assert.ok(m.names.includes('מלכודת'));
    assert.equal(m.lang.text, 'a^n b^m | n >= 2');
    assert.equal(m.lang.mode, 'auto');
    assert.match(await toast(p), /נבנה אס"ד עם 5 מצבים \(המינימלי\)/);
    assert.equal(await text(p, '#lang-strip .ts-chip'), 'נבנה מהנוסחה');
    assert.equal(await text(p, '#ts-pill'), 'נבדק מול הנוסחה ✓');
    await p.fill('#word-input', 'aaab');
    await p.press('#word-input', 'Enter');
    await p.click('[data-run=end]');
    await p.waitForSelector('#result-banner.ok:not([hidden])');
    await shot(p, 'lang-built');
    await p.keyboard.press('Escape');
  });

  await step('בדיקה מול השפה: אוטומט נכון מקבל הוכחה (כרטיס מתחת לפס)', async () => {
    await p.click('#ts-pill');
    await p.waitForSelector('#task-card.ok');
    assert.match(await text(p, '#task-card'), /המכונה מקבלת בדיוק את השפה/);
    assert.match(await text(p, '#task-card'), /זו הוכחה/);
    await shot(p, 'lang-check-ok');
  });

  await step('אוטומט שגוי (משימה על המכונה שכבר שם): שתי קבוצות של מילים, רמז, ולחיצה על מילה מריצה אותה', async () => {
    await write(p, 'a^n b^m | n >= 1', 'practice');
    await H.start(p, true);
    assert.equal(await text(p, '#lang-strip .ts-chip'), 'משימה');
    assert.equal(await text(p, '#ts-pill'), 'עוד לא נבדק');
    assert.equal(await H.check(p), 'fail');
    assert.match(await text(p, '#ts-pill'), /^לא נכון: (\d+ מילים|מילה אחת)$/);
    assert.match(await text(p, '#task-card .tc-sub'), /המילה a בשפה, אבל האוטומט שלך דוחה אותה/);
    assert.deepEqual(await words(p, '#task-card .tc-group'), ['a', 'ab', 'abb', 'abbb', 'abbbb']);
    assert.equal(await p.$$eval('#task-card .tc-group', (e) => e.length), 1, 'אין מילים עודפות, אז יש רק קבוצה אחת');
    await shot(p, 'lang-check-fail');
    await p.click('#task-card .w-chip[data-word="ab"]');
    await p.waitForSelector('#result-banner.err:not([hidden])', { timeout: 15000 });
    assert.equal(await p.inputValue('#word-input'), 'ab');
    assert.match(await text(p, '#result-banner'), /המילה ab נדחית/);
    await p.keyboard.press('Escape');
    await p.click('#task-card [data-t=add-tests]');
    assert.ok(await p.evaluate(() => App.machine.tests.some((t) => t.word === 'abbb' && t.expect === true)));
  });

  await step('שפה חסרת הקשר: נבנה אוטומט מחסנית, והבדיקה חסומה (ומנוסחת כך)', async () => {
    await write(p, 'a^n b^n', 'auto');
    await H.build(p);
    const m = await machine(p);
    assert.deepEqual([m.type, m.states], ['pda', 3]);
    await p.click('#ts-pill');
    await p.waitForSelector('#task-card.ok', { timeout: 20000 });
    const t = await text(p, '#task-card');
    assert.match(t, /נבדקו כל [\d,]+ המילים עד אורך \d+ ועוד [\d,]+ מילים ארוכות \(עד אורך \d+\)\. לא נמצאה טעות/);
    assert.match(t, /הוכח שכל מילה שהמכונה מקבלת היא מהצורה שבנוסחה/);
    assert.match(t, /זו בדיקה, לא הוכחה/);
    await shot(p, 'lang-pda');
  });

  await step('שפה שאין לה בנייה אוטומטית: נאמר ביושר, ו"לבנות לבד ולבדוק" עובר ל"אני בונה"', async () => {
    await write(p, 'w ∈ {a,b}* | #a(w) = #b(w)', 'auto');
    assert.match(await text(p, '#ld-foot .ld-why'), /עוד לא נתמכת/);
    await p.click('[data-l=to-practice]');
    assert.equal(await p.getAttribute('.ld-seg [data-mode=practice]', 'aria-checked'), 'true');
    await H.start(p, true, 'pda');
    assert.equal(await H.check(p), 'fail');
    assert.equal((await words(p, '#task-card .tc-group'))[0], 'ba');
  });

  await step('למורים: קובץ משימה (הנוסחה בלבד), והתלמיד שפותח אותו מתחיל לבנות לבד', async () => {
    await p.click('#type-tabs [data-type=dfa]');
    await write(p, 'w ∈ {a,b}* | מסתיימת ב-ab', 'auto');
    await H.build(p);
    await p.click('[data-cmd=save-menu]');
    const [dl] = await Promise.all([p.waitForEvent('download'), p.click('[data-cmd=save-task]')]);
    const file = path.join(os.tmpdir(), 'dsviz-task-' + Date.now() + '.json');
    await dl.saveAs(file);
    const data = JSON.parse(fs.readFileSync(file, 'utf8'));
    assert.deepEqual([data.format, data.type, data.formula, data.mode, data.locked, 'states' in data], ['dsviz-automata-task', 'dfa', 'w ∈ {a,b}* | מסתיימת ב-ab', 'practice', true, false]);
    await p.setInputFiles('#file-input', file);
    await p.waitForTimeout(500);
    fs.unlinkSync(file);
    const m = await machine(p);
    assert.deepEqual([m.states, m.lang.mode, m.lang.locked], [0, 'practice', true]);
    assert.equal(await text(p, '#lang-strip .ts-chip'), 'משימה');
    await p.click('#lang-strip [data-t=more]');
    assert.equal(await p.$('#ts-menu [data-t=edit]'), null, 'נעולה: אין עריכת נוסחה');
    assert.ok(await p.$('#ts-menu [data-t=solution]'), 'הצגת פתרון תמיד אפשרית');
    await p.keyboard.press('Escape');
    assert.ok(await p.evaluate(() => App.machine.tests.length >= 4), 'מילים לדוגמה בטבלת הבדיקה');
    await shot(p, 'lang-exercise');
  });
  await p.context().close();

  await step('מצב כהה: אותו פס וכרטיס', async () => {
    p = await open('dark');
    await write(p, 'w ∈ {a,b}* | #a(w) אי-זוגי וגם מסתיימת ב-b', 'practice');
    await H.start(p, true);
    assert.equal(await H.check(p), 'fail');
    await shot(p, 'lang-dark');
    await p.context().close();
  });

  await browser.close();
  assert.deepEqual(errors, [], 'שגיאות בקונסול');
  console.log('הכול עבר (' + n + ' שלבים)');
})().catch((e) => { console.error('✗', e.message); process.exit(1); });
