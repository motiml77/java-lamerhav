// הסמל של "הצעה לפתרון" (מנורה): מ-eclipse-plugin/icons/src/solution.svg ל-PNG בגדלים ש-Eclipse צריך
// (SWT לא מצייר SVG בגרסאות הישנות). מריצים פעם אחת אחרי שינוי ב-SVG:  node tools/make-solution-icons.js
const { chromium } = require('playwright');
const fs = require('fs');
const path = require('path');
const dir = path.join(__dirname, '..', 'eclipse-plugin', 'icons');
const svg = fs.readFileSync(path.join(dir, 'src', 'solution.svg'), 'utf8');
(async () => {
  const browser = await chromium.launch();
  const page = await browser.newPage();
  for (const [name, size] of [['solution.png', 16], ['solution@2x.png', 32], ['solution-big.png', 36], ['solution-big@2x.png', 72]]) {
    await page.setViewportSize({ width: size, height: size });
    await page.setContent(`<html><body style="margin:0;background:transparent">${svg.replace(/width="32" height="32"/, `width="${size}" height="${size}"`)}</body></html>`);
    await page.screenshot({ path: path.join(dir, name), omitBackground: true, clip: { x: 0, y: 0, width: size, height: size } });
    console.log(name, size);
  }
  await browser.close();
})();
