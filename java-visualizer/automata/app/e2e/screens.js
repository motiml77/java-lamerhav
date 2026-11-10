/*
 * e2e/screens.js - מצלם את המסכים העיקריים ל-docs/shots/ours-*.png, ונכשל אם יש שגיאה בקונסול.
 * הרצה: node e2e/screens.js [תיקיית פלט]. דורש Playwright. (בדיקת האינטראקציה עם הנחות: e2e/flow.js)
 */
'use strict';
const path = require('path');
const { chromium } = require('playwright');

const OUT = path.resolve(process.argv[2] || path.join(__dirname, '..', '..', 'docs', 'shots'));
const URL = 'file://' + path.resolve(__dirname, '..', 'index.html');

(async () => {
  const browser = await chromium.launch();
  const errors = [];

  async function page(theme, keepGuide) {
    const ctx = await browser.newContext({ viewport: { width: 1440, height: 900 }, deviceScaleFactor: 1, colorScheme: theme });
    const p = await ctx.newPage();
    p.on('console', (m) => { if (m.type() === 'error') errors.push(m.text()); });
    p.on('pageerror', (e) => errors.push(e.message));
    await p.goto(URL);
    await p.waitForSelector('.st');
    await p.evaluate(() => document.fonts.ready);
    if (!keepGuide) await p.click('#guide [data-g=close]');
    return p;
  }
  const shot = async (p, name) => { await p.waitForTimeout(350); await p.evaluate(() => document.querySelectorAll('.toast').forEach((t) => t.remove())); await p.screenshot({ path: path.join(OUT, name + '.png') }); console.log('✓', name); };
  const at = (p, name) => p.evaluate((nm) => { const s = App.machine.states.find((q) => q.name === nm); const r = document.querySelector('.stage').getBoundingClientRect(); const q = App.canvas.toScreen(s), z = App.canvas.uiScale(); return { x: r.x + q.x * z, y: r.y + q.y * z }; }, name);
  const click = async (p, pt) => { await p.mouse.move(pt.x, pt.y, { steps: 3 }); await p.mouse.down(); await p.mouse.up(); await p.waitForTimeout(150); };
  const steps = async (p, n) => { for (let i = 0; i < n; i++) { await p.click('[data-run=next]'); await p.waitForTimeout(120); } await p.waitForTimeout(500); };
  const example = async (p, id) => { await p.evaluate((x) => App.loadExample(x), id); await p.waitForTimeout(300); };

  // כניסה ראשונה: ההדרכה פתוחה
  let p = await page('light', true);
  await shot(p, 'ours-guide');
  await p.click('#guide [data-g=close]');

  // אס"ד, מצב נבחר: הסרגל השחור והחץ המקווקו
  const q0 = await at(p, 'q0'), q1 = await at(p, 'q1');
  await click(p, q1);
  await p.mouse.move(q1.x - 120, q1.y + 120, { steps: 4 });
  await shot(p, 'ours-light');

  // עריכת מעבר באס"ד עם אזהרה (b כבר יוצא מ-q0)
  await p.keyboard.press('Escape');
  await click(p, q0);
  await click(p, q1);
  await p.keyboard.press('b');
  await shot(p, 'ours-popover-warn');
  // מאשרים בכל זאת: שגיאת אי-דטרמיניזם, וניסיון הרצה
  await p.keyboard.press('Enter');
  await p.press('#word-input', 'Enter');
  await shot(p, 'ours-error');
  await p.keyboard.press('Escape');
  await p.keyboard.press('Control+z');
  await click(p, { x: q1.x + 160, y: q1.y - 180 });
  await p.click('[data-tab=props]');

  // ריחוף: פח על מצב, ואז חלון האישור
  await p.mouse.move(q1.x + 40, q1.y + 60, { steps: 3 });
  await p.mouse.move(q1.x, q1.y, { steps: 3 });
  await shot(p, 'ours-hover-trash');
  const tb = await p.evaluate(() => document.querySelector('.st.hover .trash').getBoundingClientRect().toJSON());
  await click(p, { x: tb.x + tb.width / 2, y: tb.y + tb.height / 2 });
  await p.waitForSelector('.dialog');
  await shot(p, 'ours-confirm');
  await p.keyboard.press('Escape');

  // סוף הרצה: באנר "מתקבלת"
  await p.press('#word-input', 'Enter');
  await p.click('[data-run=end]');
  await shot(p, 'ours-result');

  // בדיקת מילים עם כישלון אחד
  await p.fill('#test-word', 'ab');
  await p.click('#tests-add [type=submit]');
  await p.click('[data-cmd=run-tests]');
  await shot(p, 'ours-tests');

  // אסל"ד: מעבר חדש q3 → q0 (קשת מקווקוות עד האישור), ואמצע הרצה
  await example(p, 'third-last-a');
  await click(p, await at(p, 'q3'));
  await click(p, await at(p, 'q0'));
  await p.keyboard.press('a');
  await shot(p, 'ours-popover');
  await p.keyboard.press('Escape');
  await steps(p, 3);
  await shot(p, 'ours-run-mid');

  // ניתוב: הדוגמה הצפופה
  await example(p, 'jumps');
  await shot(p, 'ours-crowded');

  // אוטומט מחסנית: חלון הכללים, ואמצע הרצה
  await example(p, 'anbn');
  await click(p, await at(p, 'q0'));
  await click(p, await at(p, 'q0'));
  await shot(p, 'ours-popover-pda');
  await p.keyboard.press('Escape');
  await steps(p, 3);
  await shot(p, 'ours-pda');

  // מכונת טיורינג באמצע הרצה
  await example(p, 'binary-inc');
  await steps(p, 7);
  await shot(p, 'ours-tm');

  // אס"ד חלקי: לשונית הבעיות עם "השלמה עם מצב מלכודת"
  await p.click('#type-tabs [data-type=dfa]');
  await p.click('[data-cmd=new]');
  await shot(p, 'ours-empty');
  const c = await p.evaluate(() => { const r = document.querySelector('.stage').getBoundingClientRect(); return { x: r.x + r.width / 2, y: r.y + r.height / 2 }; });
  await p.mouse.dblclick(c.x - 150, c.y); await p.waitForTimeout(150);
  await p.mouse.dblclick(c.x + 150, c.y); await p.waitForTimeout(150);
  await click(p, await at(p, 'q0')); await click(p, await at(p, 'q1'));
  await p.click('#tpop [data-add=a]'); await p.click('#tpop [data-add=b]'); await p.click('#tpop .sym-chip[data-sym=b]'); await p.keyboard.press('Enter');
  await p.click('[data-tab=issues]');
  await shot(p, 'ours-trap');
  await p.context().close();

  // כהה: חלון צף באסל"ד
  p = await page('dark');
  await p.click('#type-tabs [data-type=nfa]');
  await click(p, await at(p, 'q2'));
  await click(p, await at(p, 'q1'));
  await shot(p, 'ours-dark');
  await p.context().close();

  await browser.close();
  if (errors.length) { console.error(errors.length + ' שגיאות:', errors); process.exit(1); }
  console.log('הכול תקין');
})();
