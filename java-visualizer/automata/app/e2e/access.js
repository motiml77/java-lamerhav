/*
 * e2e/access.js - הכניסה המשותפת (Playwright) מול שרת מדומה של התוכנה המקומית (e2e/mock-launcher.js, לפי החוזה של access/README.md):
 * הכפתור של החשבון, מסך הכניסה של המאגר (מורה / עמדת מחשב / תלמיד), "ממשיכים בדפדפן" וביטול, הודעות שגיאה, עמדת מחשב,
 * שינויים חיים (כניסה / התנתקות ב-Eclipse), "עמדת ניהול", התנתקות עם אישור, ובלי התוכנה (file://). צילומים: docs/shots/r10-*.png
 * הרצה: node e2e/access.js
 */
'use strict';
const path = require('path');
const assert = require('assert/strict');
const { chromium } = require('playwright');
const mock = require('./mock-launcher.js');

const OUT = path.resolve(__dirname, '..', '..', 'docs', 'shots');
const FILE = 'file://' + path.resolve(__dirname, '..', 'index.html');
const TEACHER = { state: 'teacher', name: 'רונית כהן', email: 'ronit@example.org', role: 'teacher', apps: ['dsviz', 'automata'], canSignOut: true };
const TLS = 'החיבור המאובטח לשרת של DSViz נחסם במחשב הזה. כנראה תוכנת סינון אינטרנט (כמו נטפרי או רימון) או אנטי-וירוס שבודקים תעבורה מוצפנת. מבקשים מספק הסינון / מרכז המחשבים להתקין את תעודת האבטחה שלהם ב-Windows, או לאשר את השרת של DSViz — ומנסים שוב.';

(async () => {
  const m = await mock.start({
    station: (code) => (code.toUpperCase() === 'K7QM' ? { ok: true, status: 'ok', message: '' } : code === 'TLS1' ? { ok: false, status: 'offline', message: TLS } : { ok: false, status: 'bad-code', message: '' }),
  });
  const browser = await chromium.launch();
  const errors = [];
  let n = 0;
  const step = async (name, fn) => { await fn(); console.log('✓ ' + (++n) + '. ' + name); };
  const p = await (await browser.newContext({ viewport: { width: 1440, height: 900 } })).newPage();
  p.on('console', (x) => { if (x.type() === 'error') errors.push(x.text()); });
  p.on('pageerror', (e) => errors.push(e.message));
  const shot = async (name, clip) => { await p.waitForTimeout(300); await p.evaluate(() => document.querySelectorAll('.toast').forEach((t) => t.remove())); await p.screenshot({ path: path.join(OUT, name + '.png'), clip }); };
  const chip = () => p.textContent('#acct .acct-label');
  const called = (route) => m.calls.some((c) => c.route === route);

  await p.goto(m.url);
  await p.waitForSelector('#guide:not([hidden])');
  await p.click('#guide [data-g=close]');

  await step('לא מחובר: הכפתור בסרגל אומר "לא מחובר", והמפתח נמחק מהכתובת', async () => {
    await p.waitForSelector('#acct:not([hidden])');
    assert.equal(await chip(), 'לא מחובר');
    assert.equal(await p.evaluate(() => location.hash), '');
  });

  await step('"מאגר שאלות" פותח את מסך הכניסה: מורה, עמדת מחשב בכיתה, תלמיד', async () => {
    await p.click('[data-cmd=bank]');
    await p.waitForSelector('.ac-window');
    assert.equal(await p.textContent('.ac-body h2'), 'כניסה למאגר השאלות');
    assert.deepEqual(await p.$$eval('.ac-card h3', (els) => els.map((e) => e.textContent)), ['מורה', 'עמדת מחשב בכיתה', 'תלמיד']);
    assert.deepEqual(await p.$$eval('.ac-card .btn', (els) => els.map((e) => e.textContent)), ['כניסה עם Google', 'הקלדת קוד המורה', 'כניסה עם Google']);
    assert.equal(await p.$('.bk-window'), null, 'המאגר לא נפתח');
    await shot('r10-gate-choose');
  });

  await step('"כניסה עם Google": "ממשיכים בדפדפן", ו"ביטול" מבטל את הכניסה בתוכנה', async () => {
    await p.click('[data-ac-role=teacher]');
    await p.waitForSelector('.ac-body.wait');
    assert.equal(await p.textContent('.ac-body h2'), 'ממשיכים בדפדפן');
    assert.deepEqual(m.calls.find((c) => c.route === 'POST /api/signin').body, { role: 'teacher' });
    await p.waitForFunction(() => document.querySelector('#acct .acct-label').textContent === 'מחכה לכניסה');
    await shot('r10-waiting');
    await p.click('[data-ac-go=cancel]');
    await p.waitForSelector('.ac-body.choose');
    assert.ok(called('POST /api/signin-cancel'));
  });

  await step('חשבון שלא מוגדר כמורה: ההודעה של Java, ו"חזרה"', async () => {
    await p.click('[data-ac-role=teacher]');
    await p.waitForSelector('.ac-body.wait');
    m.finishSignIn({ ok: false, status: 'not-teacher', message: '' });
    await p.waitForSelector('.ac-box.warn');
    assert.equal(await p.textContent('.ac-box h2'), 'החשבון עוד לא מוגדר כמורה');
    await p.click('[data-ac-go=choose]');
    await p.waitForSelector('.ac-body.choose');
  });

  await step('עמדת מחשב: בדיקת השדות, קוד שגוי, והודעת סינון האינטרנט מהתוכנה (בלי קו ארוך)', async () => {
    await p.click('[data-ac-role=station]');
    assert.equal(await p.textContent('.ac-body h2'), 'חיבור עמדת מחשב');
    await p.fill('#ac-code', 'K7');
    await p.click('[data-ac-go=claim]');
    assert.equal(await p.textContent('.ac-error'), 'הקוד הוא 4 תווים, למשל K7QM.');
    await p.fill('#ac-code', 'ABCD');
    await p.fill('#ac-name', 'מעבדה 2 · עמדה 14');
    await p.click('[data-ac-go=claim]');
    await p.waitForFunction(() => document.querySelector('.ac-error').textContent.includes('הקוד לא נמצא'));
    await p.fill('#ac-code', 'TLS1');
    await p.press('#ac-code', 'Enter');
    await p.waitForFunction(() => document.querySelector('.ac-error').textContent.includes('נחסם'));
    const t = await p.textContent('.ac-error');
    assert.ok(!t.includes('—') && t.includes('ומנסים שוב'));
    await shot('r10-error');
  });

  await step('קוד נכון: "המחשב מחובר", והמאגר נפתח; בכפתור "עמדה: ...", ובלי התנתקות', async () => {
    await p.fill('#ac-code', 'k7qm');
    await p.click('[data-ac-go=claim]');
    await p.waitForSelector('.ac-box.ok');
    assert.equal(await p.textContent('.ac-box h2'), 'המחשב מחובר');
    assert.match(await p.textContent('.ac-box p'), /^העמדה של רונית כהן: המאגר פתוח/);
    await p.waitForSelector('.bk-window');
    assert.equal(await p.$('.ac-window'), null);
    assert.equal(await chip(), 'עמדה: מעבדה 2 · עמדה 14');
    await p.click('[data-bk=close]');
    await p.click('#acct .acct-chip');
    assert.equal(await p.$('#acct-menu [data-ac=signout]'), null);
    assert.match(await p.textContent('#acct-menu .acct-note'), /המורה מעמדת הניהול/);
    await p.keyboard.press('Escape');
  });

  await step('כניסה של מורה ב-Eclipse: הכפתור מתעדכן מיד; בתפריט "עמדת ניהול" ו"התנתקות"', async () => {
    m.set(TEACHER);
    await p.waitForFunction(() => document.querySelector('#acct .acct-label').textContent === 'מורה: רונית כהן');
    await p.evaluate(() => document.activeElement && document.activeElement.blur());
    await shot('r10-chip', { x: 0, y: 0, width: 1440, height: 260 });
    await p.click('#acct .acct-chip');
    assert.deepEqual(await p.$$eval('#acct-menu [role=menuitem] b', (els) => els.map((e) => e.textContent)), ['עמדת ניהול', 'התנתקות']);
    assert.match(await p.textContent('#acct-menu .acct-head'), /ronit@example\.org/);
    await shot('r10-teacher-menu', { x: 0, y: 0, width: 1440, height: 360 });
    await p.click('#acct-menu [data-ac=panel]');
    await p.waitForFunction(() => document.querySelector('.toast') && /עמדת הניהול/.test(document.querySelector('.toast').textContent));
    assert.ok(called('POST /api/teacher-panel'));
  });

  await step('התנתקות ב-Eclipse כשהמאגר פתוח: המאגר נסגר והכפתור "לא מחובר"', async () => {
    await p.click('[data-cmd=bank]');
    await p.waitForSelector('.bk-window');
    m.set(mock.SIGNED_OUT);
    await p.waitForFunction(() => !document.querySelector('.bk-window'));
    assert.equal(await chip(), 'לא מחובר');
    await p.waitForFunction(() => /המאגר נסגר/.test((document.querySelector('.toast') || {}).textContent || ''));
  });

  await step('כניסה עם Google שמצליחה: "נכנסת בהצלחה", והמאגר נפתח', async () => {
    await p.click('[data-cmd=bank]');
    await p.click('[data-ac-role=teacher]');
    await p.waitForSelector('.ac-body.wait');
    m.finishSignIn({ ok: true, status: 'ok', message: '' }, TEACHER);
    await p.waitForSelector('.ac-box.ok');
    assert.equal(await p.textContent('.ac-box h2'), 'נכנסת בהצלחה');
    await p.waitForSelector('.bk-window');
    await p.click('[data-bk=close]');
  });

  await step('"התנתקות" מהתפריט: שאלה בעברית, וההתנתקות עוברת לכל התוכנות', async () => {
    await p.click('#acct .acct-chip');
    await p.click('#acct-menu [data-ac=signout]');
    await p.waitForSelector('.dialog');
    assert.match(await p.textContent('.dialog'), /להתנתק מהחשבון ronit@example\.org במחשב הזה\?/);
    assert.match(await p.textContent('.dialog'), /עמדת הניהול והמאגר ייסגרו/);
    await p.click('.dialog [data-r="1"]');
    await p.waitForFunction(() => document.querySelector('#acct .acct-label').textContent === 'לא מחובר');
    assert.ok(called('POST /api/signout'));
  });

  await step('בלי התוכנה (file://): אין כפתור חשבון, והמאגר מסביר שהוא בתוכנה המותקנת; העורך עובד', async () => {
    await p.goto(FILE);
    await p.waitForSelector('#guide:not([hidden])');
    await p.click('#guide [data-g=close]');
    await p.waitForTimeout(300);
    assert.equal(await p.isVisible('#acct'), false);
    await p.click('[data-cmd=bank]');
    await p.waitForSelector('.dialog');
    assert.match(await p.textContent('.dialog h2'), /מאגר השאלות פתוח בתוכנה המותקנת/);
    assert.equal(await p.$$eval('.dialog .btn', (b) => b.length), 1);
    assert.equal(await p.$('.ac-window'), null);
    await p.click('.dialog [data-r="1"]');
    await p.click('[data-cmd=add-state]');
    assert.equal(await p.evaluate(() => App.machine.states.length), 1, 'העורך עובד');
  });

  assert.deepEqual(errors, [], 'אין שגיאות בקונסול');
  console.log('\nכל ' + n + ' הבדיקות עברו');
  await browser.close();
  await m.close();
})().catch((e) => { console.error(e); process.exit(1); });
