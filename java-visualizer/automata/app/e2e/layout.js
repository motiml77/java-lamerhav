/*
 * e2e/layout.js - סבב 11 (Playwright): פתיחה ראשונה ריקה ושחזור אחרי טעינה; כרטיס ההדרכה מעל הכול ובתוך החלון (1440×900 ו-1024×768),
 * ורק בפעם הראשונה; אזור ההרצה ב-90% ונמוך ב-15%, בלי שורת "מוכן" (הצעד והתוצאה באזור הכהה); חלון המעבר הקומפקטי (אס"ד ואוטומט מחסנית).
 * צילומים: docs/shots/r11-*.png. הרצה: node e2e/layout.js
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
  const open = async (w, h) => {
    const p = await (await browser.newContext({ viewport: { width: w, height: h } })).newPage();
    p.on('console', (x) => { if (x.type() === 'error') errors.push(x.text()); });
    p.on('pageerror', (e) => errors.push(e.message));
    await p.goto(URL);
    await p.waitForSelector('#guide:not([hidden])');
    await p.evaluate(() => document.fonts.ready);
    return p;
  };
  const shot = async (p, name, clip) => { await p.waitForTimeout(350); await p.evaluate(() => document.querySelectorAll('.toast').forEach((t) => t.remove())); await p.screenshot({ path: path.join(OUT, name + '.png'), clip }); };
  const box = (p, sel) => p.$eval(sel, (e) => { const r = e.getBoundingClientRect(); return { x: r.x, y: r.y, w: r.width, h: r.height, b: r.bottom, r: r.right }; });
  // מה שמעל בנקודות של הכרטיס (pointer-events: none של הכרטיס הריק מבוטל לרגע, כדי ש-elementFromPoint יראה אותו)
  const onTop = (p, sel) => p.$eval(sel, (e) => { const pe = e.style.pointerEvents; e.style.pointerEvents = 'auto'; e.querySelectorAll('*').forEach((c) => { c.dataset.pe = c.style.pointerEvents; c.style.pointerEvents = 'auto'; }); const r = e.getBoundingClientRect();
    const ok = [[0.5, 0.15], [0.5, 0.85], [0.15, 0.5], [0.85, 0.5]].every(([fx, fy]) => { const t = document.elementFromPoint(r.x + r.width * fx, r.y + r.height * fy); return t && e.contains(t); });
    e.style.pointerEvents = pe; e.querySelectorAll('*').forEach((c) => { c.style.pointerEvents = c.dataset.pe; delete c.dataset.pe; }); return ok; });
  const at = (p, name) => p.evaluate((nm) => { const s = App.machine.states.find((q) => q.name === nm); const r = document.querySelector('.stage').getBoundingClientRect(); const q = App.canvas.toScreen(s), z = App.canvas.uiScale(); return { x: r.x + q.x * z, y: r.y + q.y * z }; }, name);
  const click = async (p, pt) => { await p.mouse.move(pt.x, pt.y, { steps: 3 }); await p.mouse.down(); await p.mouse.up(); await p.waitForTimeout(150); };

  let p = await open(1440, 900);

  await step('פתיחה ראשונה: קנבס ריק, אס"ד, Σ ריק, בלי שפה ובלי מילים; ההדרכה במרכז ומעל הכול', async () => {
    assert.deepEqual(await p.evaluate(() => [App.active, App.machine.states.length, App.machine.alphabet.length, !!App.machine.language, App.machine.tests.length, App.word]), ['dfa', 0, 0, false, 0, '']);
    assert.equal(await p.isVisible('#lang-strip'), false);
    assert.ok(await onTop(p, '#guide'), 'הכרטיס לא מוסתר');
    const g = await box(p, '#guide'), run = await box(p, '.runpanel');
    assert.ok(g.b <= 900 && g.y >= 0, 'בתוך החלון');
    assert.ok(run.y > 0);
    await shot(p, 'r11-empty-start');
  });

  await step('אזור ההרצה: 15% נמוך יותר וב-90%, בלי שורת "מוכן"; לפני הרצה אין צעד בחלק הכהה', async () => {
    await p.click('#guide [data-g=close]');
    const run = await box(p, '.runpanel');
    assert.ok(Math.abs(run.h - 248 * 0.9) < 2, 'גובה על המסך: ' + run.h);
    assert.equal(await p.$eval('.runpanel', (e) => getComputedStyle(e).zoom), '0.9');
    assert.equal(await p.$('#run-status, #run-note, #step-count, .run-info'), null);
    assert.equal(await p.evaluate(() => document.querySelector('.runpanel').textContent.includes('מוכן')), false);
    assert.equal(await p.$('.mv-step'), null);
    // המחוון של המהירות עדיין עובד ב-zoom כפול
    const sp = await box(p, '#speed');
    await p.mouse.click(sp.x + 3, sp.y + sp.h / 2);               // מימין לשמאל: המהיר משמאל
    assert.equal(await p.evaluate(() => App.speed), 5);
    await p.mouse.click(sp.x + sp.w - 3, sp.y + sp.h / 2);
    assert.equal(await p.evaluate(() => App.speed), 1);
    await p.mouse.click(sp.x + sp.w / 2, sp.y + sp.h / 2);
    assert.equal(await p.evaluate(() => App.speed), 3);
  });

  await step('חלון המעבר קומפקטי: "q0 → q1" ו"מעבר חדש", שורה אחת, צ\'יפים של 28px, בלי שורת Enter / Esc', async () => {
    const c = await p.evaluate(() => { const r = document.querySelector('.stage').getBoundingClientRect(); return { x: r.x + r.width / 2, y: r.y + r.height / 2 }; });
    await p.mouse.dblclick(c.x - 120, c.y); await p.waitForTimeout(150);
    await p.mouse.dblclick(c.x + 120, c.y); await p.waitForTimeout(150);
    await click(p, await at(p, 'q0'));
    await click(p, await at(p, 'q1'));
    await p.waitForSelector('#tpop:not([hidden])');
    assert.equal((await p.textContent('#tpop .pop-title')).replace(/\s+/g, ' ').trim(), 'q0 → q1');
    assert.equal(await p.textContent('#tpop .pop-kicker'), 'מעבר חדש');
    assert.equal(await p.textContent('#tpop .pop-q'), 'אין אותיות ב-Σ: בוחרים הצעה או מקלידים אות');
    assert.equal(await p.$('#tpop .pop-keys, #tpop kbd'), null);
    const pop = await box(p, '#tpop'), chip = await box(p, '#tpop .sym-chip');
    assert.ok(Math.abs(chip.h - 28 * 0.9) < 1.5, 'צ\'יפ: ' + chip.h);
    assert.ok(pop.w * pop.h < 300 * 0.9 * 260 * 0.9 * 0.62, 'שטח: ' + Math.round(pop.w) + '×' + Math.round(pop.h));
    const nr = await box(p, '#tpop .new-row'), inp = await box(p, '#tpop .new-sym'), add = await box(p, '#tpop [data-act=add]');
    assert.ok(Math.abs(inp.y + inp.h / 2 - (add.y + add.h / 2)) < 2 && nr.h < 30, 'אות חדשה והוספה בשורה אחת');
    await p.click('#tpop [data-add=a]');
    await p.click('#tpop [data-add=b]');
    assert.equal(await p.textContent('#tpop .pop-q'), 'בוחרים אותיות למעבר');
    await shot(p, 'r11-popover');
    await p.keyboard.press('Enter');
    assert.equal(await p.isVisible('#tpop'), false, 'Enter עדיין מאשר');
    assert.equal(await p.evaluate(() => App.machine.transitions.length), 1);
  });

  await step('חלון המעבר באוטומט מחסנית: אותו גודל קומפקטי, Esc מבטל', async () => {
    await H.closeEditor(p); await p.click('#type-tabs [data-type=pda]');
    const c = await p.evaluate(() => { const r = document.querySelector('.stage').getBoundingClientRect(); return { x: r.x + r.width / 2, y: r.y + r.height / 2 }; });
    await p.mouse.dblclick(c.x - 120, c.y); await p.waitForTimeout(150);
    await p.mouse.dblclick(c.x + 120, c.y); await p.waitForTimeout(150);
    await click(p, await at(p, 'q0'));
    await click(p, await at(p, 'q1'));
    await p.waitForSelector('#tpop .rules');
    assert.equal(await p.$('#tpop .pop-keys, #tpop kbd, #tpop .pop-q'), null);
    const pop = await box(p, '#tpop'), inp = await box(p, '#tpop .rule input');
    assert.ok(pop.w <= 292 * 0.9 + 2 && pop.h < 190, Math.round(pop.w) + '×' + Math.round(pop.h));
    assert.ok(Math.abs(inp.h - 26 * 0.9) < 1.5);
    const st = await box(p, '.stage');
    assert.ok(pop.x >= st.x && pop.r <= st.r && pop.y >= st.y && pop.b <= st.b, 'בתוך הקנבס');
    await p.fill('#tpop .rule input[data-k=read]', 'a');
    await p.fill('#tpop .rule input[data-k=push]', 'A');
    await shot(p, 'r11-popover-pda');
    await p.keyboard.press('Escape');
    assert.equal(await p.isVisible('#tpop'), false);
    assert.equal(await p.evaluate(() => App.machine.transitions.length), 0);
  });

  await step('הרצה עם דרכים (bbaa): הצעד וההסבר בחלק הכהה, ולפחות 5 שורות של דרכים', async () => {
    await H.write(p, 'a^n b^n ∪ b^n a^n');
    await p.waitForTimeout(300);
    await p.click('[data-l=build]');
    if (await p.waitForSelector('.dialog', { timeout: 1500 }).catch(() => null)) await p.click('.dialog [data-r="1"]');
    await p.waitForTimeout(400);
    await p.fill('#word-input', 'bbaa');
    for (let i = 0; i < 5; i++) await p.click('[data-run=next]');
    await p.waitForTimeout(200);
    assert.equal((await p.textContent('.mv-step .mv-label')).trim(), 'צעד 4 מתוך 7');
    assert.ok((await p.textContent('.mv-step-text')).trim().length > 3, 'הסבר הצעד');
    const fit = await p.$eval('#machine-view .paths', (el) => { const row = el.querySelector('.p-row').getBoundingClientRect().height; return el.getBoundingClientRect().height / (row + 2 * 0.81); });
    assert.ok(fit >= 5, 'שורות: ' + fit.toFixed(2));
    await shot(p, 'r11-mid-run');
    await p.click('[data-run=end]');
    await p.waitForSelector('#result-banner.ok:not([hidden])');
    assert.equal((await p.textContent('.mv-step-text')).trim(), 'המילה מתקבלת: דרך 2 הגיעה ל-q6');
    assert.match(await p.textContent('#result-banner'), /המילה bbaa מתקבלת/);
    await shot(p, 'r11-accepted');
  });

  await step('"בדיקת מילים": כמה שורות נראות, ושורת ההוספה בפנים', async () => {
    await p.keyboard.press('Escape');
    for (const w of ['ab', 'ba', 'aabb', 'bbaa', 'abab']) { await p.fill('#test-word', w); await p.press('#test-word', 'Enter'); }
    const wrap = await box(p, '.tests-table-wrap'), row = await box(p, '#tests-table tbody tr'), add = await box(p, '#tests-add'), run = await box(p, '.runpanel');
    assert.ok((wrap.h - 26) / row.h >= 4, 'שורות: ' + ((wrap.h - 26) / row.h).toFixed(1));
    assert.ok(add.b <= run.b + 1, 'שורת ההוספה נראית');
  });

  await step('אות שלא ב-Σ: השגיאה בחלק הכהה (לא נעלמה עם שורת "מוכן")', async () => {
    await p.fill('#word-input', 'abx');
    await p.press('#word-input', 'Enter');
    await p.waitForSelector('.mv-step.err');
    assert.match(await p.textContent('.mv-step.err'), /x/);
    await p.keyboard.press('Escape');
  });

  await step('טעינה מחדש: המצב האחרון חוזר בדיוק, וההדרכה לא נפתחת שוב', async () => {
    await p.fill('#word-input', 'aabb');
    await p.waitForTimeout(500);
    const before = await p.evaluate(() => JSON.stringify([App.active, App.machine.states.length, App.machine.tests.length, App.machine.language && App.machine.language.text, App.word]));
    await p.reload();
    await p.waitForSelector('.st');
    await p.waitForTimeout(400);
    assert.equal(await p.evaluate(() => JSON.stringify([App.active, App.machine.states.length, App.machine.tests.length, App.machine.language && App.machine.language.text, App.word])), before);
    assert.equal(await p.isVisible('#guide'), false);
    await shot(p, 'r11-restored');
    await p.click('#guide-btn');
    assert.ok(await p.isVisible('#guide'), '? מחזיר');
    assert.ok(await onTop(p, '#guide'));
    await p.click('#guide [data-g=close]');
  });

  await step('1024×768: ההדרכה בתוך החלון ומעל אזור ההרצה ופאנל הצד', async () => {
    const q = await open(1024, 768);
    const g = await box(q, '#guide');
    assert.ok(g.y >= 0 && g.b <= 768 && g.x >= 0 && g.r <= 1024, JSON.stringify(g));
    assert.ok(await onTop(q, '#guide'));
    await shot(q, 'r11-guide-1024');
    // גם הכרטיס הצד (אחרי שיש מצב) לא נחתך בתחתית
    await q.click('#guide [data-g=free]');              // כרטיס הפתיחה (סבב 14): ציור חופשי
    await q.click('[data-cmd=add-state]');
    await q.click('#guide-btn');
    await q.waitForTimeout(300);
    const f = await box(q, '#guide');
    assert.equal(await q.getAttribute('#guide', 'data-mode'), 'float');
    assert.ok(f.b <= 768 && await onTop(q, '#guide'), JSON.stringify(f));
  });

  assert.deepEqual(errors, [], 'אין שגיאות בקונסול');
  console.log('\nכל ' + n + ' הבדיקות עברו');
  await browser.close();
})().catch((e) => { console.error('✗', e.message || e); process.exit(1); });
