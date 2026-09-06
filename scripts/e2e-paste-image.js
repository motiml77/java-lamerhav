/**
 * בדיקת הדבקת תמונה (Ctrl+V) בעורך השאלות.
 *
 * מריץ את האתר האמיתי, נכנס כמורה, פותח שאלה, ומדביק תמונה אמיתית דרך
 * אירוע paste עם DataTransfer מלא — בדיוק כמו צילום מסך אמיתי. אחר כך
 * מוודא שעורך התמונה נפתח עם החיתוך והשחור-לבן.
 */
const { chromium } = require('playwright');
const http = require('http'), fs = require('fs'), path = require('path');

const ROOT = 'C:/Users/MOTILE~1/AppData/Local/Temp/claude/C--Users-Moti-Levi-Desktop-AI-shiurei-kodesh/3acfcd05-a44d-4b41-92f5-94febe6f29b6/scratchpad/builddist';
const CT = { '.html': 'text/html; charset=utf-8', '.js': 'text/javascript; charset=utf-8', '.png': 'image/png' };

let pass = 0, fail = 0;
const t = (n, c, d) => { c ? pass++ : fail++; console.log((c ? 'PASS ' : 'FAIL ') + n + (c ? '' : ' — ' + d)); };

function serve(port) {
  return new Promise(r => {
    http.createServer((req, res) => {
      let u = req.url.split('?')[0]; let f = path.join(ROOT, u);
      if (!fs.existsSync(f) || fs.statSync(f).isDirectory())
        f = /^\/(manage|signup|[a-z0-9][a-z0-9-]{1,28}[a-z0-9])$/.test(u) ? path.join(ROOT, 'app.html') : path.join(ROOT, 'index.html');
      if (!fs.existsSync(f)) { res.writeHead(404); res.end(); return; }
      res.writeHead(200, { 'Content-Type': CT[path.extname(f).toLowerCase()] || 'application/octet-stream' });
      fs.createReadStream(f).pipe(res);
    }).listen(port, () => r());
  });
}

// לוחץ על אלמנט לפי טקסט מדויק, מטפס להורה לחיץ
async function clickByText(pg, text) {
  return await pg.evaluate((txt) => {
    const els = [...document.querySelectorAll('span,div,button,a,h3,h4')]
      .filter(e => e.textContent.trim() === txt && e.children.length === 0);
    const sp = els[0];
    if (!sp) return false;
    let el = sp;
    for (let i = 0; i < 6 && el; i++) {
      if (el.tagName === 'BUTTON' || el.tagName === 'A' || el.getAttribute('role') === 'button') { el.click(); return true; }
      el = el.parentElement;
    }
    sp.parentElement.click();
    return true;
  }, text);
}

(async () => {
  await serve(4453);
  let b; try { b = await chromium.launch({ channel: 'chrome' }); } catch (e) { b = await chromium.launch({ channel: 'msedge' }); }
  const pg = await b.newPage({ viewport: { width: 1280, height: 950 } });
  const errs = [];
  pg.on('pageerror', e => errs.push(String(e).slice(0, 160)));

  try {
    await pg.goto('http://localhost:4453/demo', { waitUntil: 'domcontentloaded' });
    await pg.waitForSelector('text=התחברות עם Google', { timeout: 60000 });
    const d = await pg.$('button:has-text("דמו למורה")') || await pg.$('button:has-text("כניסת דמו")');
    if (d) await d.click({ force: true });
    await pg.waitForTimeout(5000);

    // מצב עריכה — ההדבקה פעילה רק שם, בכוונה (לא במצב ניהול/תלמידה)
    const editBtn = await pg.$('button:has-text("עריכה")');
    if (editBtn) { await editBtn.click({ force: true }); await pg.waitForTimeout(2500); }

    // נושא -> עבודה
    await clickByText(pg, 'לולאות');
    await pg.waitForTimeout(2500);
    const okWork = await clickByText(pg, 'עבודה 1 — לולאות');
    await pg.waitForTimeout(4000);
    await pg.screenshot({ path: 'shots/paste-1-work.png' });
    const body1 = await pg.textContent('body');
    t('נפתחה עבודה עם שאלות', okWork && /שאלה|Ctrl\+V|העלה תמונה|קוד/.test(body1), body1.replace(/\s+/g, ' ').slice(0, 160));

    // המסך עצמו מנחה: "לעריכת שאלה בחרי אותה בסרגל הצד"
    await clickByText(pg, 'שאלה 1');
    await pg.waitForTimeout(3500);
    await pg.screenshot({ path: 'shots/paste-1b-question.png' });
    t('עורך השאלה נפתח', /העלה תמונה|החלף תמונה|Ctrl\+V/.test(await pg.textContent('body')));

    // רמז ההדבקה — מעיד שאנחנו במצב עריכה של שאלה
    const hasHint = /Ctrl\+V/.test(await pg.textContent('body'));
    t('רמז ההדבקה מוצג למורה', hasHint);

    // ---- ההדבקה ----
    const res = await pg.evaluate(async () => {
      const c = document.createElement('canvas');
      c.width = 320; c.height = 220;
      const x = c.getContext('2d');
      x.fillStyle = '#2b3a8f'; x.fillRect(0, 0, 320, 220);
      x.fillStyle = '#ffd400'; x.fillRect(30, 30, 140, 100);
      x.fillStyle = '#fff'; x.font = '20px sans-serif'; x.fillText('for (i=0..)', 30, 180);
      const blob = await new Promise(r => c.toBlob(r, 'image/png'));
      const file = new File([blob], 'screenshot.png', { type: 'image/png' });
      const dt = new DataTransfer();
      dt.items.add(file);
      const ev = new ClipboardEvent('paste', { clipboardData: dt, bubbles: true, cancelable: true });
      document.dispatchEvent(ev);
      return { size: blob.size, prevented: ev.defaultPrevented };
    });
    console.log('   תמונה שהודבקה:', res.size, 'bytes · preventDefault:', res.prevented);
    t('ההדבקה טופלה על ידי האתר', res.prevented === true);

    await pg.waitForTimeout(3500);
    await pg.screenshot({ path: 'shots/paste-2-editor.png' });
    const body2 = await pg.textContent('body');
    t('עורך התמונה נפתח', /חיתוך|סיבוב|שחור.לבן|עריכת תמונה/.test(body2), body2.replace(/\s+/g, ' ').slice(0, 200));

    // הכפתורים באמת שם
    t('כפתור שחור-לבן קיים', /שחור.לבן/.test(body2));
    t('כפתור סיבוב קיים', /סיבוב/.test(body2));

    // הפעלת שחור-לבן ובדיקה שהתצוגה משתנה
    const bwBtn = await pg.$('button:has-text("שחור-לבן")');
    if (bwBtn) { await bwBtn.click({ force: true }); await pg.waitForTimeout(1200); }
    await pg.screenshot({ path: 'shots/paste-3-bw.png' });
    const bwOn = await pg.evaluate(() => !!document.querySelector('.img-bw'));
    t('שחור-לבן מופעל בתצוגה', bwOn);

    // סגירת העורך, ואז הסיכון האמיתי: הדבקת טקסט רגילה חייבת להמשיך לעבוד
    const cancel = await pg.$('button:has-text("ביטול")');
    if (cancel) { await cancel.click({ force: true }); await pg.waitForTimeout(1500); }
    const textPaste = await pg.evaluate(() => {
      const dt = new DataTransfer();
      dt.setData('text/plain', 'int sum = 0;');
      const ev = new ClipboardEvent('paste', { clipboardData: dt, bubbles: true, cancelable: true });
      document.dispatchEvent(ev);
      return ev.defaultPrevented;
    });
    t('הדבקת טקסט רגילה לא נחטפת', textPaste === false, 'preventDefault=' + textPaste);

    console.log('\n===== ' + pass + '/' + (pass + fail) + ' =====');
    console.log('שגיאות JS:', errs.filter(e => !/BABEL|deoptimised|permissions|Firestore|net::/i.test(e)).slice(0, 3));
  } catch (e) {
    fail++; console.log('שגיאה: ' + String(e && e.message).slice(0, 250));
  } finally {
    await b.close();
    process.exit(fail > 0 ? 1 : 0);
  }
})();
