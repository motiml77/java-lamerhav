/*
 * e2e/resize.js - סבב 12 (Playwright): גרירה של קווי ההפרדה (גובה אזור ההרצה, רוחב פאנל הצד), גבולות, לחיצה כפולה לברירת המחדל, מקלדת,
 * קיפול ופתיחה של הלוח (כפתור ו-Ctrl+B), שמירה אחרי טעינה, השרטוט לא זז כשהקנבס משתנה, ולחיצה על הקנבס נוחתת מתחת לסמן.
 * בלי חפיפות ב-1024×768 וב-1440×900. צילומים: docs/shots/r12-*.png. הרצה: node e2e/resize.js
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
  const open = async (w, h) => {
    const p = await (await browser.newContext({ viewport: { width: w, height: h } })).newPage();
    p.on('console', (x) => { if (x.type() === 'error') errors.push(x.text()); });
    p.on('pageerror', (e) => errors.push(e.message));
    await p.goto(URL);
    await p.waitForSelector('#guide:not([hidden])');
    await p.evaluate(() => document.fonts.ready);
    await p.click('#guide [data-g=close]');
    await p.evaluate(() => { App.loadFirstExample(); document.querySelectorAll('.toast').forEach((t) => t.remove()); });
    await p.waitForSelector('.st');
    await p.waitForTimeout(300);
    return p;
  };
  const shot = async (p, name) => { await p.waitForTimeout(350); await p.evaluate(() => document.querySelectorAll('.toast').forEach((t) => t.remove())); await p.screenshot({ path: path.join(OUT, name + '.png') }); };
  const box = (p, sel) => p.$eval(sel, (e) => { const r = e.getBoundingClientRect(); return { x: r.x, y: r.y, w: r.width, h: r.height, b: r.bottom, r: r.right, cx: r.x + r.width / 2, cy: r.y + r.height / 2 }; });
  const state = (p, nm) => p.evaluate((n) => { const s = App.machine.states.find((q) => q.name === n); const b = document.querySelector('.st[data-state="' + s.id + '"] .st-body').getBoundingClientRect(); return { x: b.x + b.width / 2, y: b.y + b.height / 2 }; }, nm);
  const drag = async (p, sel, dx, dy) => {
    const b = await box(p, sel);
    await p.mouse.move(b.cx, b.cy + (sel === '#split-side' ? 120 : 0));
    await p.mouse.down();
    await p.mouse.move(b.cx + dx / 2, b.cy + dy / 2 + (sel === '#split-side' ? 120 : 0), { steps: 4 });
    await p.mouse.move(b.cx + dx, b.cy + dy + (sel === '#split-side' ? 120 : 0), { steps: 4 });
    await p.mouse.up();
    await p.waitForTimeout(150);
  };
  const near = (a, b, tol, msg) => assert.ok(Math.abs(a - b) <= tol, (msg || '') + ' ' + a.toFixed(1) + ' / ' + b.toFixed(1));

  let p = await open(1440, 900);

  await step('ברירת המחדל: קווי ההפרדה נגישים מהמקלדת, ולוח פתוח עם כפתור קיפול', async () => {
    assert.equal(await p.getAttribute('#split-run', 'role'), 'separator');
    assert.equal(await p.getAttribute('#split-run', 'tabindex'), '0');
    assert.equal(await p.getAttribute('#split-side', 'aria-orientation'), 'vertical');
    assert.equal(await p.getAttribute('#side-toggle', 'aria-label'), 'קיפול הלוח');
    near((await box(p, '.runpanel')).h, 248 * 0.9, 1.5, 'גובה');
    near((await box(p, '.side')).w, 328 * 0.9, 1.5, 'רוחב');
    await shot(p, 'r12-default');
  });

  await step('ריחוף על קו ההפרדה: סמן row-resize ונקודות אחיזה', async () => {
    const b = await box(p, '#split-run');
    await p.mouse.move(b.cx, b.cy);
    await p.waitForTimeout(200);
    assert.equal(await p.$eval('#split-run', (e) => getComputedStyle(e).cursor), 'row-resize');
    assert.equal(await p.$eval('#split-run', (e) => getComputedStyle(e, '::after').opacity), '1');
    assert.equal(await p.$eval('#split-side', (e) => getComputedStyle(e).cursor), 'col-resize');
    await shot(p, 'r12-splitter-hover');
  });

  await step('גרירה למעלה מגדילה את אזור ההרצה; השרטוט לא זז; יש גבול עליון', async () => {
    const q0 = await state(p, 'q0');
    await drag(p, '#split-run', 0, -180);
    near((await box(p, '.runpanel')).h, 248 * 0.9 + 180, 3, 'אחרי גרירה');
    const q0b = await state(p, 'q0');
    near(q0b.x, q0.x, 1, 'x'); near(q0b.y, q0.y, 1, 'y');
    await drag(p, '#split-run', 0, -2000);
    near((await box(p, '.runpanel')).h, 900 * 0.6, 3, 'מקסימום 60%');
    await drag(p, '#split-run', 0, 3000);
    near((await box(p, '.runpanel')).h, 140 * 0.9, 2, 'מינימום 140');
    await drag(p, '#split-run', 0, -300);
    await shot(p, 'r12-bottom-enlarged');
  });

  await step('גרירה ימינה מרחיבה את הלוח; השרטוט נשאר באותו מקום על המסך; גבולות', async () => {
    const q1 = await state(p, 'q1');
    await drag(p, '#split-side', 120, 0);
    near((await box(p, '.side')).w, 328 * 0.9 + 120, 3, 'רוחב');
    const q1b = await state(p, 'q1');
    near(q1b.x, q1.x, 1.5, 'x'); near(q1b.y, q1.y, 1, 'y');
    await drag(p, '#split-side', -2000, 0);
    near((await box(p, '.side')).w, 220 * 0.9, 2, 'מינימום 220');
    await drag(p, '#split-side', 3000, 0);
    near((await box(p, '.side')).w, 1440 * 0.45, 3, 'מקסימום 45%');
    await drag(p, '#split-side', -260, 0);
  });

  await step('אחרי שינוי הגודל, לחיצה כפולה על הקנבס מוסיפה מצב בדיוק מתחת לסמן', async () => {
    const st = await box(p, '.stage');
    const x = st.x + st.w * 0.7, y = st.y + st.h * 0.3;
    await p.mouse.dblclick(x, y);
    await p.waitForTimeout(200);
    const last = await p.evaluate(() => App.machine.states[App.machine.states.length - 1].name);
    const c = await state(p, last);
    near(c.x, x, 9, 'x'); near(c.y, y, 9, 'y');
    await p.keyboard.press('Control+z');
  });

  await step('מקלדת: חצים משנים, Enter מחזיר לרגיל; לחיצה כפולה מחזירה לרגיל', async () => {
    await p.focus('#split-side');
    const w0 = (await box(p, '.side')).w;
    await p.keyboard.press('ArrowRight');
    await p.waitForTimeout(260);   // הלוח זז בתנועה של 180ms
    near((await box(p, '.side')).w, w0 + 16 * 0.9, 1.5, 'חץ');
    await p.keyboard.press('Shift+ArrowLeft');
    await p.waitForTimeout(260);   // הלוח זז בתנועה של 180ms
    near((await box(p, '.side')).w, w0 - 48 * 0.9, 1.5, 'Shift');
    await p.keyboard.press('Enter');
    await p.waitForTimeout(260);   // הלוח זז בתנועה של 180ms
    near((await box(p, '.side')).w, 328 * 0.9, 1.5, 'Enter');
    await p.focus('#split-run');
    await p.keyboard.press('ArrowUp');
    const h1 = (await box(p, '.runpanel')).h;
    await p.dblclick('#split-run');
    await p.waitForTimeout(260);   // הלוח זז בתנועה של 180ms
    near((await box(p, '.runpanel')).h, 248 * 0.9, 1.5, 'לחיצה כפולה');
    assert.ok(h1 > 248 * 0.9);
    await drag(p, '#split-run', 0, -120);
    await drag(p, '#split-side', 60, 0);
  });

  await step('קיפול: הלוח נעלם בתנועה, הקנבס ברוחב מלא, השרטוט לא זז; לשונית צרה נשארת; Ctrl+B פותח', async () => {
    const q0 = await state(p, 'q0');
    await p.click('#side-toggle');
    await p.waitForTimeout(400);
    assert.ok((await box(p, '.side')).w < 2, 'הלוח מקופל');
    near((await box(p, '.stage')).x, 0, 1, 'הקנבס מתחיל בקצה');
    const q0b = await state(p, 'q0');
    near(q0b.x, q0.x, 1.5, 'x');
    const t = await box(p, '#side-toggle');
    assert.ok(t.x >= 0 && t.w >= 14, 'לשונית גלויה');
    assert.equal(await p.getAttribute('#side-toggle', 'aria-label'), 'פתיחת הלוח');
    assert.equal(await p.isVisible('#split-side'), false);
    await shot(p, 'r12-side-collapsed');
    await p.keyboard.press('Control+b');
    await p.waitForTimeout(400);
    assert.ok((await box(p, '.side')).w > 200);
    await p.keyboard.press('Control+b');
    await p.waitForTimeout(400);
  });

  await step('טעינה מחדש: הגובה, הרוחב והקיפול נשמרים', async () => {
    const run = (await box(p, '.runpanel')).h;
    await p.reload();
    await p.waitForSelector('.st');
    await p.waitForTimeout(300);
    near((await box(p, '.runpanel')).h, run, 1.5, 'גובה');
    assert.ok((await box(p, '.side')).w < 2, 'עדיין מקופל');
    await p.click('#side-toggle');
    await p.waitForTimeout(400);
    near((await box(p, '.side')).w, 328 * 0.9 + 60, 2, 'הרוחב שנגרר חוזר');
    await p.context().close();
  });

  const overlaps = async (q) => {
    const sel = ['#side-toggle', '#guide-btn', '.zoom', '#canvas-status'];
    const bs = [];
    for (const s of sel) if (await q.isVisible(s)) bs.push([s, await box(q, s)]);
    const hit = (a, b) => a.x < b.r - 1 && b.x < a.r - 1 && a.y < b.b - 1 && b.y < a.b - 1;
    for (let i = 0; i < bs.length; i++) for (let j = i + 1; j < bs.length; j++) assert.ok(!hit(bs[i][1], bs[j][1]), bs[i][0] + ' חופף ל-' + bs[j][0]);
    const st = await box(q, '.stage');
    for (const [s, b] of bs) if (s !== '#side-toggle') assert.ok(b.x >= st.x - 1 && b.r <= st.r + 1 && b.y >= st.y - 1 && b.b <= st.b + 1, s + ' בתוך הקנבס');
  };

  await step('בלי חפיפות ב-1024×768 וב-1440×900: פתוח, מקופל, אזור הרצה קטן מאוד וגדול מאוד, וגם עם ההדרכה והחלון הצף', async () => {
    for (const [w, h] of [[1024, 768], [1440, 900]]) {
      const q = await open(w, h);
      for (const collapsed of [false, true]) {
        if (collapsed) { await q.click('#side-toggle'); await q.waitForTimeout(400); }
        for (const dy of [3000, -3000]) {
          await drag(q, '#split-run', 0, dy);
          await overlaps(q);
          await q.click('#guide-btn');
          await q.waitForTimeout(250);
          const g = await box(q, '#guide'), tg = await box(q, '#side-toggle');
          assert.ok(g.b <= h + 1 && g.y >= 0, 'ההדרכה בתוך החלון');
          assert.ok(g.x >= tg.r - 1 || g.r <= tg.x + 1 || g.y >= tg.b || g.b <= tg.y, 'ההדרכה לא מכסה את כפתור הקיפול');
          await q.click('#guide [data-g=close]');
          // חלון מעבר
          const q0 = await state(q, 'q0'), q1 = await state(q, 'q1');
          await q.mouse.click(q0.x, q0.y); await q.waitForTimeout(120); await q.mouse.click(q1.x, q1.y);
          await q.waitForSelector('#tpop:not([hidden])');
          const pop = await box(q, '#tpop'), st = await box(q, '.stage');
          assert.ok(pop.y >= st.y - 1 && pop.b <= st.b + 1 && pop.x >= st.x - 1 && pop.r <= st.r + 1, 'חלון המעבר בתוך הקנבס ' + JSON.stringify([pop, st]));
          await q.keyboard.press('Escape'); await q.keyboard.press('Escape');
        }
      }
      if (w === 1024) await shot(q, 'r12-1024-collapsed');
      await q.context().close();
    }
  });

  assert.deepEqual(errors, [], 'אין שגיאות בקונסול');
  console.log('\nכל ' + n + ' הבדיקות עברו');
  await browser.close();
})().catch((e) => { console.error('✗', e.message || e); process.exit(1); });
