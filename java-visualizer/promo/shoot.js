// node shoot.js <out-dir> <fps> [times...]   — בלי זמנים: כל הסרטון
const { chromium } = require('playwright');
(async () => {
  const [out, fps, ...times] = process.argv.slice(2);
  const b = await chromium.launch();
  const pg = await b.newPage({ viewport: { width: 1920, height: 1080 } });
  await pg.goto('file://' + __dirname + '/promo.html');
  await pg.evaluate(() => document.fonts.ready);
  await pg.evaluate(() => Promise.all([...document.images].map(i => i.decode().catch(() => 0))));
  const stage = await pg.$('#stage');
  const list = times.length ? times.map(Number) : [...Array(Math.round(await pg.evaluate(() => DURATION) * fps)).keys()].map(i => i / fps);
  let n = 0;
  for (const t of list) {
    await pg.evaluate(t => render(t), t);
    await stage.screenshot({ path: `${out}/${times.length ? 't' + t : String(n).padStart(5, '0')}.jpg`, type: 'jpeg', quality: 92 });
    n++;
  }
  await b.close();
})();
