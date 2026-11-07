// מאגר תרגילים: האייקונים של הנושאים — מה-SVG (eclipse-bank/icons/topics/src/*.svg) ל-PNG של 64 פיקסלים
// (התוסף מצייר אותם בגודל 22–28, גם במסך חד). צריך Playwright + Chromium.  node tools/make-topic-icons.js
const fs = require('fs'), path = require('path');
const { chromium } = require('playwright');
(async () => {
    const dir = path.join(__dirname, '..', 'eclipse-bank', 'icons', 'topics');
    const browser = await chromium.launch();
    const page = await browser.newPage({ viewport: { width: 64, height: 64 }, deviceScaleFactor: 1 });
    const all = [];
    for (const f of fs.readdirSync(path.join(dir, 'src')).filter(n => n.endsWith('.svg')).sort()) {
        const svg = fs.readFileSync(path.join(dir, 'src', f), 'utf8').replace(/width="48" height="48"/, 'width="64" height="64"');
        await page.setContent(`<html><body style="margin:0;background:transparent">${svg}</body></html>`);
        await page.screenshot({ path: path.join(dir, f.replace('.svg', '.png')), omitBackground: true, clip: { x: 0, y: 0, width: 64, height: 64 } });
        all.push(svg.replace(/width="64" height="64"/, 'width="96" height="96"'));
    }
    // דף תצוגה של כולם יחד (לבדיקה בעין)
    await page.setViewportSize({ width: 960, height: 120 });
    await page.setContent(`<html><body style="margin:12px;background:#fff;display:flex;gap:8px">${all.join('')}</body></html>`);
    await page.screenshot({ path: path.join(dir, 'src', 'all.png') });
    await browser.close();
    console.log('ok');
})();
