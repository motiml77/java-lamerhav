/*
 * e2e/screens.js - עובר על המסכים העיקריים ב-Chromium (Playwright), מצלם, ונכשל אם יש שגיאה בקונסול.
 * הרצה: node e2e/screens.js [תיקיית פלט]   (ברירת מחדל: ../docs/shots)
 * דורש Playwright מותקן (require('playwright')). לא רץ ב-node --test.
 */
'use strict';
const path = require('path');
const { chromium } = require('playwright');

const OUT = path.resolve(process.argv[2] || path.join(__dirname, '..', '..', 'docs', 'shots'));
const URL = 'file://' + path.resolve(__dirname, '..', 'index.html');

(async () => {
  const browser = await chromium.launch();
  const errors = [];
  const fail = (msg) => { errors.push(msg); console.error('✗', msg); };

  async function page(theme) {
    const ctx = await browser.newContext({ viewport: { width: 1440, height: 900 }, deviceScaleFactor: 1, colorScheme: theme });
    const p = await ctx.newPage();
    p.on('console', (m) => { if (m.type() === 'error') fail('console: ' + m.text()); });
    p.on('pageerror', (e) => fail('pageerror: ' + e.message));
    await p.goto(URL);
    await p.waitForSelector('.st');
    await p.evaluate(() => document.fonts.ready);
    return p;
  }
  const shot = async (p, name) => { await p.waitForTimeout(250); await p.screenshot({ path: path.join(OUT, name + '.png') }); console.log('✓', name); };
  const steps = async (p, n) => { for (let i = 0; i < n; i++) { await p.click('[data-run=next]'); await p.waitForTimeout(120); } await p.waitForTimeout(500); };
  const stage = async (p) => p.evaluate(() => { const r = document.querySelector('.stage').getBoundingClientRect(); return { x: r.x + r.width / 2, y: r.y + r.height / 2 }; });
  const statePos = async (p, name) => p.evaluate((n) => { const m = App.machine; const s = m.states.find((q) => q.name === n); const r = document.querySelector('.stage').getBoundingClientRect(); const q = App.canvas.toScreen(s); return { x: r.x + q.x, y: r.y + q.y }; }, name);

  // בהיר: אס"ד עם מצב נבחר (סרגל ליד המצב, מאפייני מצב)
  let p = await page('light');
  const q1 = await statePos(p, 'q1');
  await p.mouse.click(q1.x, q1.y);
  await shot(p, 'ours-light');

  // באמצע הרצה: אסל"ד עם שני מצבים פעילים
  await p.click('#type-tabs [data-type=nfa]');
  await p.waitForTimeout(300);
  await steps(p, 3);
  await shot(p, 'ours-run-mid');

  // אוטומט מחסנית באמצע הרצה, מכונת טיורינג באמצע הרצה
  await p.click('#type-tabs [data-type=pda]');
  await steps(p, 3);
  await shot(p, 'ours-pda');
  await p.click('#type-tabs [data-type=tm]');
  await steps(p, 7);
  await shot(p, 'ours-tm');

  // בדיקת מילים עם כישלון אחד
  await p.click('#type-tabs [data-type=dfa]');
  await p.fill('#test-word', 'ab');
  await p.click('#tests-add [type=submit]');
  await p.click('[data-cmd=run-tests]');
  await shot(p, 'ours-tests');

  // שגיאה: אי-דטרמיניזם באס"ד
  const q0 = await statePos(p, 'q0');
  const rim = await p.evaluate(() => AutomataGeometry.R * App.machine.view.zoom);
  await p.mouse.move(q0.x + rim, q0.y); await p.mouse.down();
  await p.mouse.move((q0.x + q1.x) / 2, q0.y - 40, { steps: 6 }); await p.mouse.move(q1.x, q1.y, { steps: 6 }); await p.mouse.up();
  await p.waitForSelector('.inline-editor:not([hidden])');
  await p.keyboard.type('a, b');
  await shot(p, 'ours-edit');
  await p.keyboard.press('Enter');
  await p.press('#word-input', 'Enter');
  await shot(p, 'ours-error');
  await p.keyboard.press('Control+z');

  // מצב ריק
  await p.click('[data-cmd=new]');
  await shot(p, 'ours-empty');
  await p.context().close();

  // כהה: אסל"ד באמצע הרצה
  p = await page('dark');
  await p.click('#type-tabs [data-type=nfa]');
  await steps(p, 3);
  await shot(p, 'ours-dark');
  await p.context().close();

  await browser.close();
  if (errors.length) { console.error(errors.length + ' שגיאות'); process.exit(1); }
  console.log('הכול תקין');
})();
