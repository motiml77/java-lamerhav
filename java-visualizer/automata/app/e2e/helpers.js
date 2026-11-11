/*
 * e2e/helpers.js - עזרים משותפים לבדיקות הדפדפן אחרי סבב 14: עורך הנוסחה הוא חלון ("שפה פורמלית" בסרגל העליון),
 * והבדיקה מול הנוסחה נמצאת בפס המשימה שמעל הקנבס (התוצאה ב-#task-card).
 */
'use strict';

/** פותח את עורך הנוסחה במצב mode ('auto' | 'practice'). אם הוא כבר פתוח: רק מחליף מצב */
async function openEditor(p, mode) {
  mode = mode || 'auto';
  if (!(await p.isVisible('.lang-dialog'))) {
    await p.click('[data-cmd=lang-menu]');
    await p.click('[data-cmd=lang-' + mode + ']');
    await p.waitForSelector('.lang-dialog');
  } else await p.click('.ld-seg [data-mode=' + mode + ']');
}
const closeEditor = async (p) => { if (await p.isVisible('.lang-dialog')) await p.click('.lang-dialog [data-l=close]'); };

/** כותב נוסחה בעורך (פותח אותו אם צריך) */
async function write(p, text, mode) {
  await openEditor(p, mode);
  await p.fill('#lang-input', text);
  await p.waitForTimeout(250);
}

/** "בנה אוטומט": אם הלשונית תפוסה, "להחליף את המכונה הנוכחית" */
async function build(p) {
  await p.click('[data-l=build]');
  const d = await p.waitForSelector('.dialog', { timeout: 1500 }).catch(() => null);
  if (d) await p.click('.dialog [data-r="1"]');
  await p.waitForTimeout(400);
}

/** "התחלה" (אני בונה). keep: להמשיך עם המכונה שכבר בלשונית (אחרת מכונה ריקה) */
async function start(p, keep, type) {
  if (type) await p.selectOption('#ld-type', type);
  await p.click('[data-l=start]');
  const d = await p.waitForSelector('.dialog', { timeout: 1500 }).catch(() => null);
  if (d) await p.click('.dialog [data-r="' + (keep ? 'alt' : '1') + '"]');
  await p.waitForTimeout(300);
}

/** "בדיקה" בפס המשימה, ומחכה לתוצאה בכרטיס. מחזיר את המחלקה של הכרטיס (ok / fail / ...) */
async function check(p, timeout) {
  await closeEditor(p);
  await p.click('#lang-strip [data-t=check]');
  await p.waitForSelector('#task-card.ok, #task-card.fail, #task-card.err, #task-card.info:not(.busy)', { timeout: timeout || 20000 });
  return p.$eval('#task-card', (e) => e.className.replace('task-card', '').trim());
}

module.exports = { openEditor, closeEditor, write, build, start, check };
