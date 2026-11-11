/*
 * e2e/examples.js - הדוגמאות המוכנות ופס השפה ב-Chromium (Playwright), עם הנחות (assert) וצילומים ל-docs/shots/ex-*.png:
 * תפריט בארבע עמודות, טעינה של כל 20 הדוגמאות והרצת כל הבדיקות שלהן, מילים להרצה בלחיצה, קיפול הפס,
 * אותו פס לשפה שהמשתמש כותב, והדגשה של קשת שלמה בריחוף. הרצה: node e2e/examples.js
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
  async function open(theme) {
    const ctx = await browser.newContext({ viewport: { width: 1440, height: 900 }, colorScheme: theme });
    const p = await ctx.newPage();
    p.on('console', (m) => { if (m.type() === 'error') errors.push(m.text()); });
    p.on('pageerror', (e) => errors.push(e.message));
    await p.goto(URL);
    await p.waitForSelector('#guide:not([hidden])');
    await p.evaluate(() => document.fonts.ready);
    await p.click('#guide [data-g=close]');
    await p.evaluate(() => { App.loadFirstExample(); App.history[App.active] = { past: [], future: [] }; document.querySelectorAll('.toast').forEach((t) => t.remove()); });   // פתיחה ראשונה ריקה (סבב 11): הדוגמה הראשונה כמו פעם
    return p;
  }
  const shot = async (p, name) => { await p.waitForTimeout(350); await p.evaluate(() => document.querySelectorAll('.toast').forEach((t) => t.remove())); await p.screenshot({ path: path.join(OUT, name + '.png') }); };
  const text = async (p, sel) => (await p.textContent(sel)).replace(/\s+/g, ' ').trim();
  const pick = async (p, id) => { await p.click('[data-cmd=examples]'); await p.click('#examples-menu [data-example="' + id + '"]'); await p.waitForTimeout(250); };
  const chips = (p) => p.$$eval('#lang-strip .w-chip', (els) => els.map((e) => [e.textContent, e.classList.contains('in')]));

  let p = await open('light');

  await step('תפריט הדוגמאות: עמודה לכל סוג מכונה, חמש דוגמאות בכל אחת, והנוסחה של כל דוגמה', async () => {
    await p.click('[data-cmd=examples]');
    const cols = await p.$$eval('#examples-menu .menu-col', (els) => els.map((c) => [c.dataset.col, c.querySelectorAll('[data-example]').length, c.querySelectorAll('.mi-f').length, c.classList.contains('current')]));
    assert.deepEqual(cols, [['dfa', 5, 5, true], ['nfa', 6, 5, false], ['pda', 5, 5, false], ['tm', 5, 5, false]]);
    assert.equal(await text(p, '#examples-menu [data-example=anbn] .mi-f'), '{ anbn }');
    assert.equal(await p.$eval('#examples-menu [data-example=pow2] .mi-f sup sup', (e) => e.textContent), 'n');
    await shot(p, 'ex-menu');
    await p.keyboard.press('Escape');
  });

  await step('דוגמה נטענת עם פס השפה: נוסחה, תיאור, ומילים שהצבע שלהן בא מהנוסחה', async () => {
    await pick(p, 'anbn');
    assert.equal(await p.evaluate(() => App.active), 'pda');
    assert.equal(await text(p, '#ls-formula'), 'L = { anbn }');
    assert.equal(await p.$$eval('#ls-formula sup', (e) => e.length), 2);
    assert.equal(await text(p, '#lang-strip .ls-desc'), 'מספר שווה של a ו-b, כל ה-a לפני ה-b.');
    assert.deepEqual(await chips(p), [['ε', true], ['ab', true], ['aabb', true], ['aaabbb', true], ['a', false], ['aab', false], ['abb', false], ['ba', false]]);
    assert.equal(await p.inputValue('#word-input'), 'aabb');
    assert.equal(await p.$$eval('.tests-table tbody tr', (e) => e.length), 8);
    const box = await p.$eval('#lang-strip', (e) => { const r = e.getBoundingClientRect(), s = document.getElementById('stage').getBoundingClientRect(); return [Math.round(r.bottom), Math.round(s.top), Math.round(r.height)]; });
    assert.equal(box[0], box[1], 'הפס מעל הקנבס, לא עליו');
    assert.ok(box[2] > 60 && box[2] < 100, 'גובה הפס: ' + box[2]);
    await shot(p, 'ex-pda-anbn');
  });

  await step('לחיצה על מילה בפס מריצה אותה: aab נדחית, aabb מתקבלת', async () => {
    await p.click('#lang-strip .w-chip[data-word="aab"]');
    await p.waitForSelector('#result-banner.err:not([hidden])', { timeout: 15000 });
    assert.equal(await p.inputValue('#word-input'), 'aab');
    await p.click('#lang-strip .w-chip[data-word="aabb"]');
    await p.waitForSelector('#result-banner.ok:not([hidden])', { timeout: 15000 });
    assert.match(await text(p, '#result-banner'), /המילה aabb מתקבלת/);
    await shot(p, 'ex-run-chip');
    await p.keyboard.press('Escape');
  });

  await step('כל 20 הדוגמאות: נטענות מהתפריט, לכל אחת פס עם נוסחה, וכל הבדיקות שלהן עוברות', async () => {
    const ids = await p.evaluate(() => AutomataExamples.EXAMPLES.filter((e) => !e.demo).map((e) => e.id));
    assert.equal(ids.length, 20);
    for (const id of ids) {
      await pick(p, id);
      assert.ok(await p.isVisible('#lang-strip'), id + ': הפס מוצג');
      assert.match(await text(p, '#ls-formula'), /^L = /, id);
      assert.ok((await chips(p)).length >= 6, id + ': מילים להרצה');
      await p.click('[data-cmd=run-tests]');
      const rows = await p.$$eval('.tests-table tbody tr', (els) => els.map((e) => e.className));
      assert.ok(rows.length >= 6 && rows.every((c) => c === 'pass'), id + ': ' + rows.join(','));
      const off = await p.evaluate(() => { const s = document.getElementById('stage').getBoundingClientRect(); return Array.from(document.querySelectorAll('.st, .lb')).filter((e) => { const r = e.getBoundingClientRect(); return r.left < s.left || r.right > s.right || r.top < s.top || r.bottom > s.bottom; }).length; });
      assert.equal(off, 0, id + ': כל השרטוט בתוך הקנבס');
    }
    for (const [id, name] of [['has-aba', 'ex-dfa-has-aba'], ['same-ends', 'ex-nfa-same-ends'], ['balanced', 'ex-pda-balanced'], ['w-hash-w', 'ex-tm-w-hash-w'], ['pow2', 'ex-tm-pow2']]) { await pick(p, id); await shot(p, name); }
  });

  await step('קיפול הפס: שורה דקה עם הנוסחה בלבד, הקנבס גדל, וההעדפה נשמרת', async () => {
    await pick(p, 'anbncn');
    const h0 = await p.$eval('#stage', (e) => e.getBoundingClientRect().height);
    await p.click('#lang-strip .ls-toggle');
    await p.waitForTimeout(150);
    const [h1, strip] = await p.evaluate(() => [document.getElementById('stage').getBoundingClientRect().height, document.getElementById('lang-strip').getBoundingClientRect().height]);
    assert.ok(strip <= 40, 'גובה הפס המקופל: ' + strip);
    assert.ok(h1 > h0 + 30, 'הקנבס גדל');
    assert.equal(await p.isVisible('#lang-strip .ls-sub'), false);
    assert.equal(await text(p, '#ls-formula'), 'L = { anbncn }');
    assert.equal(await p.getAttribute('#lang-strip .ls-toggle', 'aria-expanded'), 'false');
    await shot(p, 'ex-collapsed');
    await p.reload();
    await p.waitForSelector('.st');
    assert.ok(await p.$eval('#lang-strip', (e) => e.classList.contains('collapsed')), 'נשאר מקופל אחרי טעינה מחדש');
    await p.click('#lang-strip .ls-toggle');
    assert.equal(await p.isVisible('#lang-strip .ls-sub'), true);
  });

  await step('אותו פס לשפה שהמשתמש כותב בעצמו (עם תיאור משלו), ונעלם כשאין שפה', async () => {
    await pick(p, 'even-a');
    await p.click('[data-tab=lang]');
    assert.match(await text(p, '#lang-strip .ls-desc'), /זוגי/);
    await p.fill('#lang-input', 'w ∈ {a,b}* | #a(w) אי-זוגי');
    await p.waitForTimeout(300);
    assert.equal(await p.$('#lang-strip .ls-desc'), null, 'הנוסחה השתנתה: התיאור של הדוגמה כבר לא מתאים, ונעלם');
    assert.equal(await p.inputValue('#lang-desc'), '');
    await p.click('[data-cmd=new]');
    await p.waitForTimeout(200);
    assert.equal(await p.isVisible('#lang-strip'), false);
    await p.click('[data-tab=lang]');
    await p.fill('#lang-input', 'a^n b^m | n >= 2');
    await p.waitForTimeout(300);
    assert.equal(await text(p, '#ls-formula'), 'L = { anbm | n ≥ 2 }');
    assert.deepEqual((await chips(p)).slice(0, 2), [['aa', true], ['aaa', true]]);
    assert.equal(await p.$('#lang-strip .ls-desc'), null);
    await p.fill('#lang-desc', 'לפחות שני a, ואחריהם b כמה שרוצים');
    assert.equal(await text(p, '#lang-strip .ls-desc'), 'לפחות שני a, ואחריהם b כמה שרוצים');
    await p.fill('#lang-input', 'a^n b^m | n >= 3');
    await p.waitForTimeout(300);
    assert.equal(await text(p, '#lang-strip .ls-desc'), 'לפחות שני a, ואחריהם b כמה שרוצים', 'תיאור שהמשתמש כתב נשאר גם כשהנוסחה משתנה');
    await p.fill('#lang-input', 'a^n b^m | n >');
    await p.waitForTimeout(300);
    assert.equal(await p.isVisible('#lang-strip'), false, 'נוסחה שבורה: אין פס');
  });

  await step('הדגמת הניתוב: ריחוף על תווית מדגיש את כל הקשת שלה ומעמעם את האחרות', async () => {
    await p.click('[data-tab=props]');
    await pick(p, 'jumps');
    assert.equal(await p.isVisible('#lang-strip'), false, 'להדגמה אין נוסחה');
    await shot(p, 'ex-crowded-after');
    const id = await p.evaluate(() => App.machine.transitions.find((t) => t.from === 'q4' && t.to === 'q2').id);
    const lb = await p.$('.lb[data-edge="' + id + '"]');
    const b = await lb.boundingBox();
    await p.mouse.move(b.x + b.width / 2, b.y + b.height / 2);
    await p.waitForTimeout(300);
    const st = await p.evaluate((id) => {
      const css = (el, prop) => getComputedStyle(el)[prop];
      const mine = document.querySelector('.ed[data-edge="' + id + '"]'), other = document.querySelector('.ed[data-edge]:not([data-edge="' + id + '"])');
      return { vp: document.querySelector('.viewport').classList.contains('hover-edge'), mine: mine.classList.contains('hover'), line: css(mine.querySelector('.ed-line'), 'stroke'), arrow: css(mine.querySelector('.ed-arrow'), 'fill'),
        normal: css(other.querySelector('.ed-line'), 'stroke'), dim: +css(other, 'opacity'), start: +css(document.querySelector('.start-arrow'), 'opacity') };
    }, id);
    assert.ok(st.vp && st.mine);
    assert.equal(st.line, st.arrow, 'הקו וראש החץ באותו צבע');
    assert.notEqual(st.line, st.normal);
    assert.ok(st.dim < 0.5 && st.start === 1, 'האחרות מעומעמות: ' + st.dim);
    await shot(p, 'ex-crowded-hover');
    await p.mouse.move(700, 150);
    await p.waitForTimeout(250);
    assert.equal(await p.evaluate(() => document.querySelector('.viewport').classList.contains('hover-edge')), false);
  });
  await p.context().close();

  await step('מצב כהה: דוגמה עם פס השפה', async () => {
    p = await open('dark');
    await pick(p, 'tm-palindrome');
    await p.click('#lang-strip .w-chip[data-word="abba"]');
    await p.waitForTimeout(1200);
    await p.click('[data-run=pause]').catch(() => {});
    await shot(p, 'ex-dark');
    await p.context().close();
  });

  await browser.close();
  assert.deepEqual(errors, [], 'שגיאות בקונסול');
  console.log('הכול עבר (' + n + ' שלבים)');
})().catch((e) => { console.error('✗', e.message); process.exit(1); });
