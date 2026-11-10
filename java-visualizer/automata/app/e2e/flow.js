/*
 * e2e/flow.js - בדיקת אינטראקציה אמיתית ב-Chromium (Playwright), עם הנחות (assert):
 * לחיצה כפולה מוסיפה מצבים, לחיצה-לחיצה יוצרת מעבר, בחירת אותיות בחלון הצף, פח בריחוף + אישור + ביטול,
 * Delete עם אישור, Esc, והרצת מילה עם באנר התוצאה. הרצה: node e2e/flow.js
 */
'use strict';
const path = require('path');
const assert = require('assert/strict');
const { chromium } = require('playwright');

const URL = 'file://' + path.resolve(__dirname, '..', 'index.html');

(async () => {
  const browser = await chromium.launch();
  const ctx = await browser.newContext({ viewport: { width: 1440, height: 900 } });
  const p = await ctx.newPage();
  const errors = [];
  p.on('console', (m) => { if (m.type() === 'error') errors.push(m.text()); });
  p.on('pageerror', (e) => errors.push(e.message));
  let n = 0;
  const step = async (name, fn) => { await fn(); console.log('✓ ' + (++n) + '. ' + name); };
  const machine = () => p.evaluate(() => { const m = App.machine; return { states: m.states.map((s) => s.name), trans: m.transitions.map((t) => AutomataModel.stateName(m, t.from) + '>' + AutomataModel.stateName(m, t.to) + ':' + AutomataModel.formatLabels(m.type, t.labels)) }; });
  const at = (name) => p.evaluate((nm) => { const s = App.machine.states.find((q) => q.name === nm); const r = document.querySelector('.stage').getBoundingClientRect(); const q = App.canvas.toScreen(s); return { x: r.x + q.x, y: r.y + q.y }; }, name);
  const labelAt = (from, to) => p.evaluate(([f, t]) => { const m = App.machine; const tr = m.transitions.find((x) => AutomataModel.stateName(m, x.from) === f && AutomataModel.stateName(m, x.to) === t); const b = document.querySelector('.lb[data-edge="' + tr.id + '"] .lb-bg').getBoundingClientRect(); return { x: b.x + b.width / 2, y: b.y + b.height / 2, id: tr.id }; }, [from, to]);
  const click = async (pt) => { await p.mouse.move(pt.x, pt.y, { steps: 3 }); await p.mouse.down(); await p.mouse.up(); await p.waitForTimeout(120); };
  const visible = (sel) => p.evaluate((s) => { const e = document.querySelector(s); return !!e && !e.hidden && getComputedStyle(e).display !== 'none'; }, sel);

  await p.goto(URL);
  await p.waitForSelector('.st');

  await step('בכניסה הראשונה מופיעה ההדרכה, ו"הבנתי" סוגר אותה', async () => {
    assert.ok(await visible('#guide'));
    await p.click('#guide [data-g=close]');
    assert.equal(await visible('#guide'), false);
  });

  await step('מכונה חדשה: הקנבס ריק וכרטיס שלושת הצעדים במרכז', async () => {
    await p.click('[data-cmd=new]');
    assert.equal(await p.getAttribute('#guide', 'data-mode'), 'empty');
    assert.ok(await visible('#guide'));
  });

  const st = await p.evaluate(() => { const r = document.querySelector('.stage').getBoundingClientRect(); return { x: r.x + r.width / 2, y: r.y + r.height / 2 - 60 }; });
  await step('לחיצה כפולה מוסיפה 3 מצבים, וההדרכה נעלמת', async () => {
    for (const dx of [-240, 0, 240]) { await p.mouse.dblclick(st.x + dx, st.y + (dx ? 0 : 110)); await p.waitForTimeout(150); }
    assert.deepEqual((await machine()).states, ['q0', 'q1', 'q2']);
    assert.equal(await visible('#guide'), false);
    assert.match(await p.textContent('#hint'), /עכשיו מעבר/, 'רמז: מה עושים עכשיו');
  });

  await step('לחיצה על מצב: הוא המקור, וחץ מקווקו הולך אחרי הסמן', async () => {
    await click(await at('q0'));
    const q1 = await at('q1');
    await p.mouse.move(q1.x - 80, q1.y + 40, { steps: 4 });
    assert.ok(await p.evaluate(() => document.querySelector('.stage').classList.contains('arming')));
    assert.ok(await p.$('.overlay .rubber'));
    await p.mouse.move(q1.x, q1.y, { steps: 4 });
    assert.ok(await p.evaluate(() => document.querySelector('.st.hover').dataset.state === App.machine.states[1].id), 'המצב שמתחת לסמן מודגש');
  });

  await step('לחיצה על מצב אחר פותחת חלון צף; בוחרים אותיות ומאשרים', async () => {
    await click(await at('q1'));
    assert.ok(await visible('#tpop'));
    assert.equal((await machine()).trans.length, 0, 'המעבר עוד לא נוצר');
    await p.click('#tpop [data-add=a]');
    await p.click('#tpop [data-add=b]');
    assert.deepEqual(await p.$$eval('#tpop .sym-chip[aria-pressed=true]', (els) => els.map((e) => e.textContent)), ['a', 'b']);
    await p.click('#tpop .sym-chip[data-sym=b]');
    await p.click('#tpop [data-act=ok]');
    assert.equal(await visible('#tpop'), false);
    assert.deepEqual((await machine()).trans, ['q0>q1:a']);
    assert.equal(await p.textContent('.lb .lb-text'), 'a');
  });

  await step('לחיצה על אותו מצב פעמיים: לולאה (בחירה במקלדת, Enter)', async () => {
    await click(await at('q1'));
    await click(await at('q1'));
    assert.ok(await visible('#tpop'));
    await p.keyboard.press('b');
    await p.keyboard.press('Enter');
    assert.ok((await machine()).trans.includes('q1>q1:b'));
  });

  await step('מעבר q1 → q2 עם a, ו-q2 מקבל', async () => {
    await click(await at('q1'));
    await click(await at('q2'));
    await p.keyboard.press('a');
    await p.keyboard.press('Enter');
    await click(await at('q2'));
    await p.keyboard.press('Escape');
    await p.keyboard.press('KeyA');
    assert.ok((await machine()).trans.includes('q1>q2:a'));
    assert.ok(await p.evaluate(() => App.machine.states[2].accepting));
  });

  await step('לחיצה על תווית קיימת פותחת את אותו חלון, בלי קשת כפולה', async () => {
    await click(await labelAt('q0', 'q1'));
    assert.ok(await visible('#tpop'));
    assert.deepEqual(await p.$$eval('#tpop .sym-chip[aria-pressed=true]', (els) => els.map((e) => e.textContent)), ['a']);
    await p.click('#tpop .sym-chip[data-sym=b]');
    await p.keyboard.press('Enter');
    const t = (await machine()).trans;
    assert.ok(t.includes('q0>q1:a, b'));
    assert.equal(t.filter((x) => x.startsWith('q0>q1')).length, 1);
  });

  await step('Esc מבטל מעבר שהתחיל, ולחיצה על רקע גם', async () => {
    await click(await at('q0'));
    assert.ok(await p.evaluate(() => !!App.canvas.source));
    await p.keyboard.press('Escape');
    assert.equal(await p.evaluate(() => App.canvas.source), null);
    await click(await at('q0'));
    await click({ x: st.x, y: st.y - 170 });
    assert.equal(await p.evaluate(() => App.canvas.source), null);
  });

  await step('ריחוף על מצב מראה פח; מחיקה עם אישור, ו-Ctrl+Z מחזיר', async () => {
    const q2 = await at('q2');
    await p.mouse.move(q2.x - 30, q2.y + 30, { steps: 3 });
    await p.mouse.move(q2.x, q2.y, { steps: 3 });
    await p.waitForTimeout(300);
    const tr = await p.evaluate(() => { const el = document.querySelector('.st.hover .trash'); return el && { op: getComputedStyle(el).opacity, box: el.getBoundingClientRect().toJSON() }; });
    assert.ok(tr && tr.op === '1', 'הפח גלוי');
    await p.mouse.move(tr.box.x + tr.box.width / 2, tr.box.y + tr.box.height / 2, { steps: 3 });
    await p.mouse.down(); await p.mouse.up();
    await p.waitForSelector('.dialog');
    const txt = await p.textContent('.dialog h2');
    assert.match(txt, /למחוק את המצב q2 ואת המעבר שלו\?/);
    await p.click('.dialog [data-r="1"]');
    await p.waitForTimeout(200);
    assert.deepEqual((await machine()).states, ['q0', 'q1']);
    await p.keyboard.press('Control+z');
    assert.deepEqual((await machine()).states, ['q0', 'q1', 'q2']);
    assert.ok((await machine()).trans.includes('q1>q2:a'));
  });

  await step('פח על תווית: Esc מבטל, Enter מוחק', async () => {
    const lb = await labelAt('q1', 'q1');
    await p.mouse.move(lb.x, lb.y, { steps: 3 });
    const box = await p.evaluate((id) => document.querySelector('.lb[data-edge="' + id + '"] .trash').getBoundingClientRect().toJSON(), lb.id);
    await p.mouse.move(box.x + box.width / 2, box.y + box.height / 2, { steps: 2 });
    await p.mouse.down(); await p.mouse.up();
    await p.waitForSelector('.dialog');
    assert.match(await p.textContent('.dialog h2'), /q1 → q1/);
    await p.keyboard.press('Escape');
    await p.waitForTimeout(200);
    assert.ok((await machine()).trans.includes('q1>q1:b'));
    await p.mouse.move(lb.x, lb.y, { steps: 2 });
    await p.mouse.move(box.x + box.width / 2, box.y + box.height / 2, { steps: 2 });
    await p.mouse.down(); await p.mouse.up();
    await p.waitForSelector('.dialog');
    await p.keyboard.press('Enter');
    await p.waitForTimeout(200);
    assert.ok(!(await machine()).trans.includes('q1>q1:b'));
    await p.keyboard.press('Control+z');
    assert.ok((await machine()).trans.includes('q1>q1:b'));
  });

  await step('Delete על הבחירה שואל קודם', async () => {
    await click(await at('q0'));
    await p.keyboard.press('Escape');
    await p.keyboard.press('Delete');
    await p.waitForSelector('.dialog');
    await p.keyboard.press('Escape');
    await p.waitForTimeout(200);
    assert.equal((await machine()).states.length, 3);
  });

  await step('הרצת מילה: aba מתקבלת, ab נדחית, עם באנר', async () => {
    await p.fill('#word-input', 'aba');
    await p.press('#word-input', 'Enter');
    await p.click('[data-run=end]');
    await p.waitForSelector('#result-banner.ok:not([hidden])');
    assert.match(await p.textContent('#result-banner'), /המילה aba מתקבלת/);
    await p.fill('#word-input', 'ab');
    await p.press('#word-input', 'Enter');
    await p.click('[data-run=end]');
    await p.waitForSelector('#result-banner.err:not([hidden])');
    assert.match(await p.textContent('#run-status'), /נדחית/);
  });

  await browser.close();
  assert.deepEqual(errors, [], 'שגיאות בקונסול');
  console.log('הכול עבר (' + n + ' שלבים)');
})().catch((e) => { console.error('✗', e.message); process.exit(1); });
