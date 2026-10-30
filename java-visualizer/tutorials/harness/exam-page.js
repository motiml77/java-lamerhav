// דף תרגיל "מצולם" (כמו צילום מסך של שאלה מבחינה) — לסרטון "שליחת תרגיל". node harness/exam-page.js out.png
const path = require('path'), fs = require('fs');
let pw;
for (const p of ['playwright', '/opt/node-tools/node_modules/playwright']) { try { pw = require(p); break; } catch (e) { /* הבא */ } }
const out = process.argv[2];
const html = `<!DOCTYPE html><html dir="rtl"><head><meta charset="utf-8"><style>
body { margin: 0; background: #fff; font: 19px/1.6 'Times New Roman', 'David', 'Noto Serif Hebrew', serif; color: #111; }
.page { width: 980px; padding: 34px 48px 30px; }
.top { display: flex; justify-content: space-between; font-size: 15px; color: #444; border-bottom: 2px solid #222; padding-bottom: 6px; margin-bottom: 18px; }
h2 { font-size: 23px; margin: 0 0 10px; }
.s { margin: 14px 0 0; } .s b { margin-left: 8px; }
.ex { direction: ltr; text-align: center; font-family: 'DejaVu Sans Mono', monospace; font-size: 17px; margin: 8px 0; }
.foot { margin-top: 26px; font-size: 14px; color: #666; text-align: center; }
</style></head><body><div class="page">
<div class="top"><span>מדעי המחשב — מבני נתונים</span><span>שאלה 1 (15 נקודות)</span></div>
<h2>שאלה 1 — מחסניות</h2>
<div>בשאלה זו המחסנית מכילה מספרים שלמים. אפשר להשתמש בפעולות של המחלקה <span dir="ltr">Stack&lt;T&gt;</span> בלבד.</div>
<div class="s"><b>א.</b>כתבו פעולה המקבלת מחסנית של מספרים שלמים ומחזירה את מספר האיברים הזוגיים בה. בסוף הפעולה המחסנית צריכה להיות כמו בהתחלה.</div>
<div>לדוגמה: עבור המחסנית שלפניכם (4 בראש) יוחזר 2.</div>
<div class="ex">top → 4 , 7 , 2 , 9</div>
<div class="s"><b>ב.</b>כתבו פעולה המקבלת מחסנית ומספר x, ומוציאה מהמחסנית את כל המופעים של x. הסדר של שאר האיברים לא ישתנה.</div>
<div class="foot">— בהצלחה —</div>
</div></body></html>`;
(async () => {
  const b = await pw.chromium.launch();
  const pg = await b.newPage({ viewport: { width: 1076, height: 520 }, deviceScaleFactor: 1 });
  await pg.setContent(html);
  const el = await pg.$('.page');
  await el.screenshot({ path: out });
  await b.close();
})();
