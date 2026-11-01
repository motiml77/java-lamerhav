// בדיקת האתר (java-visualizer/site) בדפדפן אמיתי (Chromium), בלי אינטרנט:
//  • כל העמודים, ברוחב 1366 וברוחב 390: בלי גלילה הצידה, בלי שגיאות בקונסול, title / description / og / lang / dir,
//    alt לכל תמונה, h1 אחד, target=_blank עם rel=noopener
//  • כל הקישורים: קובץ מקומי קיים, עוגן (#) קיים בעמוד, וקישור קבוע ל-raw.githubusercontent של המאגר מצביע על קובץ שקיים במאגר
//  • כפתור ההורדה לפי מערכת ההפעלה (Windows / Mac) והאפשרות השנייה תמיד גלויה; ספירה: לחיצה על ההורדה ל-Windows
//    והעתקה של שורת ה-Mac שולחות /v1/count/windows ו-/v1/count/mac; המונה מוצג רק כשהשרת סופר
//  • "תמכו בפרויקט" בראש ובתחתית של כל עמוד, אותו קישור כמו בחלון ההרצה
//  • צילומי מסך של כל עמוד ושל כל אזור — לתיקייה שמקבלים (ברירת מחדל build/test/site-check)
//   node site-check.js [out-dir]
const fs = require('fs'), path = require('path'), http = require('http'), assert = require('assert');
const ROOT = path.join(__dirname, '..', '..');
const SITE = path.join(ROOT, 'site');
let playwright;
for (const p of ['playwright', '/opt/node-tools/node_modules/playwright']) { try { playwright = require(p); break; } catch (e) { /* הבא */ } }
if (!playwright) { console.log('playwright לא מותקן — מדלגים על בדיקת האתר'); process.exit(0); }
const OUT = process.argv[2] || path.join(ROOT, 'build', 'test', 'site-check');
fs.mkdirSync(OUT, { recursive: true });
const SERVER = 'https://dsviz-exercise.motiml77.workers.dev';
const RAW = 'https://raw.githubusercontent.com/motiml77/java-lamerhav/master/';
const EXE = RAW + 'java-visualizer/release/DSViz-Setup.exe';
const MAC = 'curl -fsSL ' + RAW + 'java-visualizer/installer/mac/install.sh | bash';
const DONATE = 'https://www.paypal.com/donate/?business=motiml77%40gmail.com&currency_code=ILS&item_name=DSViz';
const PAGES = ['index.html', 'install.html', 'bagrut.html', 'privacy.html', '404.html'];

let ok = 0;
function pass(msg) { ok++; console.log('  ok  ' + msg); }

function serve() {
  const types = { '.html': 'text/html; charset=utf-8', '.css': 'text/css', '.js': 'text/javascript', '.svg': 'image/svg+xml', '.png': 'image/png', '.webp': 'image/webp', '.mp4': 'video/mp4' };
  return new Promise(res => {
    const s = http.createServer((q, r) => {
      // כמו GitHub Pages: האתר תחת /java-lamerhav/ (בשביל ה-<base> של 404.html)
      let u = decodeURIComponent(q.url.split('?')[0]).replace(/^\/java-lamerhav/, '');
      const f = path.join(SITE, u.replace(/^\/+/, '') || 'index.html');
      if (!f.startsWith(SITE) || !fs.existsSync(f) || fs.statSync(f).isDirectory()) { r.writeHead(404); r.end(); return; }
      r.writeHead(200, { 'content-type': types[path.extname(f)] || 'application/octet-stream' });
      fs.createReadStream(f).pipe(r);
    }).listen(0, '127.0.0.1', () => res(s));
  });
}

(async () => {
  const server = await serve();
  const base = 'http://127.0.0.1:' + server.address().port + '/java-lamerhav/';
  const browser = await playwright.chromium.launch();
  let stats = { ok: true, counting: true, downloads: { windows: 1230, zip: 4, mac: 4, total: 1238 } };
  const counted = [];
  async function context(viewport) {
    const ctx = await browser.newContext({ viewport, deviceScaleFactor: 1, locale: 'he-IL' });
    // בלי אינטרנט — בלי הגופנים (הבדיקות לא תלויות בהם). לצילומים עם הגופנים האמיתיים: SITE_FONTS=<תיקייה עם css.txt
    // וקובצי ה-woff2 של Google Fonts, שהורדו קודם עם curl>
    const FONTS = process.env.SITE_FONTS;
    await ctx.route('https://fonts.googleapis.com/**', r => r.fulfill(FONTS ? { path: path.join(FONTS, 'css.txt'), contentType: 'text/css' } : { body: '', contentType: 'text/css' }));
    await ctx.route('https://fonts.gstatic.com/**', r => {
      const f = FONTS && path.join(FONTS, path.basename(new URL(r.request().url()).pathname));
      return f && fs.existsSync(f) ? r.fulfill({ path: f, contentType: 'font/woff2', headers: { 'access-control-allow-origin': '*' } }) : r.fulfill({ status: 404, body: '' });
    });
    await ctx.route(SERVER + '/**', r => {
      const u = r.request().url();
      if (u.endsWith('/v1/stats')) return r.fulfill({ status: 200, contentType: 'application/json', headers: { 'access-control-allow-origin': '*' }, body: JSON.stringify(stats) });
      const m = /\/v1\/count\/(\w+)$/.exec(u);
      if (m && r.request().method() === 'POST') { counted.push(m[1]); return r.fulfill({ status: 204, headers: { 'access-control-allow-origin': '*' } }); }
      return r.fulfill({ status: 404 });
    });
    await ctx.route('https://raw.githubusercontent.com/**', r => r.request().url().endsWith('version.json')
      ? r.fulfill({ status: 200, contentType: 'text/plain', body: JSON.stringify({ version: '1.0.1', build: '202610301200' }) })
      : r.fulfill({ status: 200, contentType: 'application/octet-stream', body: 'x' }));
    return ctx;
  }

  // ---- כל העמודים, בשני הרחבים
  const links = new Map();   // href → [עמוד]
  const ids = {};
  for (const [label, viewport] of [['desktop', { width: 1366, height: 900 }], ['mobile', { width: 390, height: 844 }]]) {
    const ctx = await context(viewport);
    for (const name of PAGES) {
      const page = await ctx.newPage();
      const errors = [];
      page.on('console', m => { if (m.type() === 'error' && !/fonts\.gstatic|Failed to load resource: the server responded with a status of 404/.test(m.text())) errors.push(m.text()); });
      page.on('pageerror', e => errors.push(e.message));
      await page.goto(base + name, { waitUntil: 'load' });
      await page.evaluate(() => document.fonts && document.fonts.ready);
      await page.waitForTimeout(300);
      const info = await page.evaluate(() => ({
        sw: document.documentElement.scrollWidth, iw: innerWidth,
        title: document.title, lang: document.documentElement.lang, dir: document.documentElement.dir,
        desc: (document.querySelector('meta[name=description]') || {}).content || '',
        ogTitle: (document.querySelector('meta[property="og:title"]') || {}).content || '',
        ogImage: (document.querySelector('meta[property="og:image"]') || {}).content || '',
        viewport: !!document.querySelector('meta[name=viewport]'),
        noAlt: [...document.images].filter(i => !i.hasAttribute('alt')).map(i => i.src),
        noSize: [...document.images].filter(i => !i.getAttribute('width') || !i.getAttribute('height')).map(i => i.src),
        h1: document.querySelectorAll('h1').length,
        blank: [...document.querySelectorAll('a[target=_blank]')].filter(a => !/noopener/.test(a.rel)).map(a => a.href),
        unnamed: [...document.querySelectorAll('button, a[href]')].filter(b => !(b.textContent.trim() || b.getAttribute('aria-label') || (b.querySelector('img') || {}).alt)).map(b => b.outerHTML.slice(0, 80)),
        wide: [...document.querySelectorAll('body *')].filter(e => { const r = e.getBoundingClientRect(); return r.width > 0 && (r.right > innerWidth + 1 || r.left < -1) && getComputedStyle(e).position !== 'fixed' && !e.closest('.cmd, .tabbar, .ide-code, .ed, pre, .tabs, .skip, .demo-code, video'); }).slice(0, 5).map(e => e.tagName + '.' + e.className),
        donateTop: !!document.querySelector('header .donate[href^="https://www.paypal.com/donate/"]'),
        donateFoot: document.querySelectorAll('footer .donate[href^="https://www.paypal.com/donate/"]').length,
        donateHref: (document.querySelector('.donate') || {}).href,
        links: [...document.querySelectorAll('a[href]')].map(a => a.getAttribute('href')),
        ids: [...document.querySelectorAll('[id]')].map(e => e.id),
      }));
      const where = name + ' @' + viewport.width;
      assert.ok(info.sw <= info.iw, 'גלילה הצידה: ' + where + ' (' + info.sw + ' > ' + info.iw + ') ' + info.wide.join(', '));
      assert.deepStrictEqual(info.wide, [], 'אלמנטים שיוצאים מהמסך: ' + where);
      assert.deepStrictEqual(errors, [], 'שגיאות בקונסול: ' + where);
      assert.ok(info.title && info.desc && info.viewport && info.lang === 'he' && info.dir === 'rtl', 'title / description / viewport / lang / dir: ' + where);
      if (name !== '404.html') assert.ok(info.ogTitle && /^https:\/\/motiml77\.github\.io\/java-lamerhav\/assets\/og\.png$/.test(info.ogImage), 'og: ' + where);
      assert.deepStrictEqual(info.noAlt, [], 'תמונה בלי alt: ' + where);
      assert.deepStrictEqual(info.noSize, [], 'תמונה בלי width/height: ' + where);
      assert.strictEqual(info.h1, 1, 'h1 אחד: ' + where);
      assert.deepStrictEqual(info.blank, [], 'target=_blank בלי noopener: ' + where);
      assert.deepStrictEqual(info.unnamed, [], 'כפתור / קישור בלי שם: ' + where);
      assert.ok(info.donateTop && info.donateFoot >= 1 && info.donateHref === DONATE, '"תמכו בפרויקט" בראש ובתחתית: ' + where);
      for (const h of info.links) { if (!links.has(h)) links.set(h, new Set()); links.get(h).add(name); }
      ids[name] = new Set(info.ids);
      // צילום של כל העמוד: גם התמונות שנטענות רק כשמגיעים אליהן (loading=lazy)
      await page.evaluate(async () => {
        for (const i of document.images) i.loading = 'eager';
        await Promise.all([...document.images].map(i => i.complete ? 0 : new Promise(r => { i.onload = i.onerror = r; })));
      });
      await page.screenshot({ path: path.join(OUT, label + '-' + name.replace('.html', '') + '.png'), fullPage: true });
      await page.close();
    }
    pass(label + ' (' + viewport.width + 'px): ' + PAGES.length + ' עמודים — בלי גלילה הצידה ובלי שגיאות; meta / og / alt / h1; "תמכו בפרויקט" בראש ובתחתית');
    await ctx.close();
  }

  // ---- הקישורים
  let local = 0, anchors = 0, repo = 0;
  const external = new Set();
  for (const [href, from] of links) {
    const src = [...from][0];
    if (/^mailto:/.test(href)) continue;
    if (/^https?:/.test(href)) {
      if (href.startsWith(RAW)) {
        const f = path.join(ROOT, '..', href.slice(RAW.length));
        assert.ok(fs.existsSync(f), 'קישור קבוע לקובץ שלא קיים במאגר: ' + href);
        repo++;
      } else external.add(href.replace(/\?.*/, ''));
      continue;
    }
    const [file, hash] = href.split('#');
    const target = file ? path.normalize(file).replace(/^\.\/?$/, 'index.html') || 'index.html' : src;
    const t = target === '.' || target === './' ? 'index.html' : target;
    assert.ok(fs.existsSync(path.join(SITE, t)), 'קישור שבור: ' + href + ' (ב-' + [...from].join(', ') + ')');
    local++;
    if (hash) {
      assert.ok(ids[t] && ids[t].has(hash), 'עוגן שלא קיים: ' + href + ' (ב-' + [...from].join(', ') + ')');
      anchors++;
    }
  }
  for (const e of external) assert.ok(/^https:\/\/(www\.paypal\.com\/donate\/|is\.gd\/dsjava$)/.test(e), 'קישור חיצוני לא צפוי: ' + e);
  assert.ok(fs.existsSync(path.join(ROOT, 'installer', 'mac', 'install.sh')), 'שורת ה-Mac מצביעה על installer/mac/install.sh');
  const repoFiles = [...links.keys()].filter(h => h.startsWith(RAW)).map(h => h.split('/').pop());
  pass('קישורים: ' + local + ' מקומיים (' + anchors + ' עוגנים), ' + repo + ' קבועים לקבצים במאגר (' + repoFiles.join(', ') + ', ושורת ה-Mac ל-install.sh), חיצוניים: ' + [...external].join(' , '));

  // ---- כפתור ההורדה לפי מערכת ההפעלה, וספירה
  for (const [os, vw] of [['win', 1366], ['mac', 1366], ['win', 390], ['mac', 390]]) {
    const ctx = await context({ width: vw, height: 900 });
    await ctx.grantPermissions(['clipboard-read', 'clipboard-write']);
    for (const name of ['index.html', 'install.html']) {
      const page = await ctx.newPage();
      await page.goto(base + name + '?os=' + os);
      await page.waitForTimeout(200);
      const scope = name === 'index.html' ? '.hero' : '#start';
      const st = await page.evaluate(sc => {
        const vis = el => !!el && el.getClientRects().length > 0 && getComputedStyle(el).visibility !== 'hidden';
        const q = s => document.querySelector(sc + ' ' + s);
        return { os: document.documentElement.dataset.os, win: vis(q('.get-win .btn')), mac: vis(q('.get-mac [data-cmd]')),
          altMac: vis(q('.alt-mac a')), altWin: vis(q('.alt-win a')), winHref: (q('.get-win .btn') || {}).href,
          cmd: (q('[data-cmd]') || {}).textContent, barWin: vis(document.querySelector('.bar .dl-win')), barMac: vis(document.querySelector('.bar .dl-mac')) };
      }, scope);
      assert.strictEqual(st.os, os);
      assert.strictEqual(st.winHref, EXE, 'הקישור הקבוע ל-Windows');
      assert.strictEqual(st.cmd.trim(), MAC, 'שורת ההתקנה ל-Mac');
      if (os === 'win') assert.ok(st.win && !st.mac && st.altMac && !st.altWin, 'Windows: ' + JSON.stringify(st));
      else assert.ok(!st.win && st.mac && st.altWin && !st.altMac, 'Mac: ' + JSON.stringify(st));
      if (vw > 900) assert.ok(os === 'win' ? st.barWin && !st.barMac : st.barMac && !st.barWin, 'הכפתור בסרגל העליון: ' + JSON.stringify(st));
      if (vw === 1366 && name === 'index.html') {
        await page.screenshot({ path: path.join(OUT, 'os-' + os + '-hero.png'), clip: { x: 0, y: 0, width: 1366, height: 820 } });
      }
      if (vw === 390 && name === 'index.html') await page.screenshot({ path: path.join(OUT, 'os-' + os + '-hero-mobile.png') });
      // ספירה: Windows — לחיצה על ההורדה; Mac — "העתקה" של השורה
      counted.length = 0;
      if (os === 'win') {
        await Promise.all([page.waitForEvent('download', { timeout: 3000 }).catch(() => null), page.click(scope + ' .get-win .btn')]);
        await page.waitForTimeout(300);
        assert.deepStrictEqual(counted, ['windows'], 'ספירה בלחיצה על ההורדה: ' + counted);
      } else {
        await page.click(scope + ' .get-mac [data-copy-cmd]');
        await page.waitForTimeout(300);
        assert.deepStrictEqual(counted, ['mac'], 'ספירה בהעתקה של שורת ה-Mac: ' + counted);
        const clip = await page.evaluate(() => navigator.clipboard.readText()).catch(() => null);
        if (clip !== null) assert.strictEqual(clip.trim(), MAC, 'מה שהועתק');
        assert.strictEqual(await page.textContent(scope + ' [data-copy-cmd]'), 'הועתק');
        await page.click(scope + ' .get-mac [data-copy-cmd]');   // פעם שנייה באותו עמוד — לא נספר שוב
        await page.waitForTimeout(200);
        assert.deepStrictEqual(counted, ['mac'], 'העתקה שנייה לא נספרת: ' + counted);
        // והקישור ל-Windows שנשאר גלוי — נספר
        counted.length = 0;
        await Promise.all([page.waitForEvent('download', { timeout: 3000 }).catch(() => null), page.click(scope + ' .alt-win a')]);
        await page.waitForTimeout(300);
        assert.deepStrictEqual(counted, ['windows'], 'ספירה בקישור החלופי ל-Windows: ' + counted);
      }
      await page.close();
    }
    await ctx.close();
  }
  pass('כפתור ההורדה לפי מערכת ההפעלה (Windows / Mac, 1366 ו-390) — והשני גלוי; ספירה: windows בלחיצה, mac בהעתקה (פעם אחת)');

  // מונה: מוצג כשהשרת סופר, ונעלם בשקט כשלא (KV לא מוגדר) או כשהשרת לא עונה
  {
    const ctx = await context({ width: 1366, height: 900 });
    const page = await ctx.newPage();
    await page.goto(base + 'index.html');
    await page.waitForFunction(() => !document.querySelector('[data-downloads]').hidden, null, { timeout: 5000 });
    const t = await page.textContent('[data-downloads]');
    assert.ok(/1,238/.test(t), 'המונה: ' + t);
    stats = { ok: true, counting: false, downloads: null };
    await page.goto(base + 'install.html');
    await page.waitForTimeout(500);
    assert.ok(await page.$$eval('[data-downloads]', l => l.every(e => e.hidden)), 'בלי מונה בשרת — לא מוצג');
    await ctx.route(SERVER + '/**', r => r.abort());
    await page.goto(base + 'index.html');
    await page.waitForTimeout(500);
    assert.ok(await page.$$eval('[data-downloads]', l => l.every(e => e.hidden)), 'שרת לא עונה — לא מוצג');
    stats = { ok: true, counting: true, downloads: { windows: 1230, zip: 4, mac: 4, total: 1238 } };
    pass('המונה: "' + t + '"; כשהשרת לא סופר או לא עונה — לא מוצג כלום');
    await ctx.close();
  }

  // ---- ההדגמה, הלשוניות וצילומים של האזורים
  for (const [label, viewport] of [['desktop', { width: 1366, height: 900 }], ['mobile', { width: 390, height: 844 }]]) {
    const ctx = await context(viewport);
    const page = await ctx.newPage();
    await page.emulateMedia({ reducedMotion: 'reduce' });
    await page.goto(base + 'index.html?os=win');
    await page.waitForSelector('[data-demo].ready');
    for (let i = 0; i < 5; i++) await page.click('[data-next]');
    const demo = await page.evaluate(() => ({ count: document.querySelector('[data-count]').textContent, now: document.querySelectorAll('.ln.now').length, boxes: document.querySelectorAll('.box:not(.gone)').length, gone: document.querySelectorAll('.box.gone').length }));
    assert.ok(/צעד 6 מתוך/.test(demo.count) && demo.now === 1 && demo.boxes >= 3, 'ההדגמה: ' + JSON.stringify(demo));
    await page.click('[role=tab]#t-list');
    assert.strictEqual(await page.getAttribute('#p-list', 'aria-hidden'), 'false');
    assert.ok(await page.isVisible('#p-list img') && !(await page.isVisible('#p-stack img')), 'לשונית ListTargil');
    await page.focus('#t-list');
    await page.keyboard.press('ArrowRight');
    assert.strictEqual(await page.getAttribute('#t-tree', 'aria-selected'), 'true', 'חיצים בלשוניות');
    await page.click('[role=tab]#t-stack');
    for (const id of ['run', 'errors', 'exercise', 'teachers', 'install', 'faq']) {
      const el = await page.$('#' + id);
      await el.screenshot({ path: path.join(OUT, label + '-index-' + id + '.png') });
    }
    await page.goto(base + 'install.html?os=mac');
    for (const id of ['windows', 'mac', 'vscode', 'intellij']) {
      const el = await page.$('#' + id);
      await el.screenshot({ path: path.join(OUT, label + '-install-' + id + '.png') });
    }
    await ctx.close();
  }
  pass('ההדגמה (הבא ×5, השורה המסומנת, המחסניות), הלשוניות (לחיצה וחיצים), וצילומי האזורים');

  await browser.close();
  server.close();
  console.log(ok + ' בדיקות של האתר עברו (צילומים: ' + OUT + ')');
})().catch(e => { console.error('  FAIL', e && e.message || e); process.exit(1); });
