// בדיקת האתר (java-visualizer/site) בדפדפן אמיתי (Chromium), בלי אינטרנט:
//  • כל העמודים, ברוחב 1366 וברוחב 390: בלי גלילה הצידה, בלי שגיאות בקונסול, title / description / og / lang / dir,
//    alt לכל תמונה, h1 אחד, target=_blank עם rel=noopener
//  • כל הקישורים: קובץ מקומי קיים, עוגן (#) קיים בעמוד, וקישור קבוע ל-raw.githubusercontent של המאגר מצביע על קובץ שקיים במאגר
//  • כפתור ההורדה לפי מערכת ההפעלה (Windows / Mac) והאפשרות השנייה תמיד גלויה; ספירה: לחיצה על ההורדה ל-Windows
//    והעתקה של שורת ה-Mac שולחות /v1/count/windows ו-/v1/count/mac; המונה מוצג רק כשהשרת סופר
//  • "תמכו בפרויקט" בראש ובתחתית של כל עמוד, אותו קישור כמו בחלון ההרצה
//  • שליחת תרגיל, שלב אחרי שלב: השלבים, הקוד שנבנה בדפדפן זהה ל-scaffold.js, ההצעה סגורה עד לחיצה, ההפעלה לבד ובלי תנועה
//  • נסו בעצמכם: כל כפתור משנה את הציור ומוסיף שורת Java; ההדגמה (רווח / Home / End); המונה עולה בהדרגה; "מה חדש"
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
const DONATE = 'https://www.paypal.com/ncp/payment/DJYTDJ6F57KA2';
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
  let sdkRequests = 0;
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
    // ה-SDK של PayPal (כפתור התרומה בעמוד הראשי): בלי אינטרנט — 404, ואז נשאר הקישור הרגיל
    await ctx.route('https://www.paypal.com/sdk/**', r => { sdkRequests++; return r.fulfill({ status: 404, body: '' }); });
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
        donateTop: !!document.querySelector('header .donate[href="https://www.paypal.com/ncp/payment/DJYTDJ6F57KA2"][target=_blank][rel~=noopener]'),
        donateFoot: document.querySelectorAll('footer .donate[href="https://www.paypal.com/ncp/payment/DJYTDJ6F57KA2"][target=_blank][rel~=noopener]').length,
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
      await page.waitForFunction(() => [...document.querySelectorAll('[data-downloads]')].every(e => e.hidden || !e.dataset.total || e.textContent.startsWith(Number(e.dataset.total).toLocaleString('he-IL'))), null, { timeout: 3000 }).catch(() => {});
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
  for (const e of external) assert.ok(/^https:\/\/(www\.paypal\.com\/ncp\/payment\/DJYTDJ6F57KA2$|is\.gd\/dsjava$)/.test(e), 'קישור חיצוני לא צפוי: ' + e);
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
      // צילום אחרי שהמונה סיים לעלות (שלא יצולם מספר באמצע)
      await page.waitForFunction(() => [...document.querySelectorAll('[data-downloads]')].every(e => e.hidden || !e.dataset.total || e.textContent.startsWith(Number(e.dataset.total).toLocaleString('he-IL'))), null, { timeout: 3000 }).catch(() => {});
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
    // המונה עולה מ-0 למספר האמיתי כשהוא על המסך (בלי להזיז את מה שסביבו), ונעצר בדיוק עליו
    const seen = new Set();
    for (let i = 0; i < 40 && !/1,238/.test(await page.textContent('[data-downloads]')); i++) { seen.add(await page.textContent('[data-downloads]')); await page.waitForTimeout(40); }
    await page.waitForFunction(() => /1,238 /.test(document.querySelector('[data-downloads]').textContent), null, { timeout: 3000 });
    const t = await page.textContent('[data-downloads]');
    assert.ok(/^1,238 הורדות עד היום$/.test(t), 'המונה: ' + t);
    assert.ok([...seen].some(x => /^\d+ הורדות/.test(x) && !/1,238/.test(x)), 'המונה עולה בהדרגה: ' + [...seen].join(' | '));
    assert.ok(parseFloat(await page.$eval('[data-downloads]', e => e.style.minWidth)) > 0, 'רוחב שמור למונה');
    await page.emulateMedia({ reducedMotion: 'reduce' });
    await page.goto(base + 'index.html');
    await page.waitForFunction(() => !document.querySelector('[data-downloads]').hidden, null, { timeout: 5000 });
    assert.ok(/^1,238 /.test(await page.textContent('[data-downloads]')), 'פחות תנועה: המספר מיד');
    await page.emulateMedia({ reducedMotion: 'no-preference' });
    stats = { ok: true, counting: false, downloads: null };
    await page.goto(base + 'install.html');
    await page.waitForTimeout(500);
    assert.ok(await page.$$eval('[data-downloads]', l => l.every(e => e.hidden)), 'בלי מונה בשרת — לא מוצג');
    await ctx.route(SERVER + '/**', r => r.abort());
    await page.goto(base + 'index.html');
    await page.waitForTimeout(500);
    assert.ok(await page.$$eval('[data-downloads]', l => l.every(e => e.hidden)), 'שרת לא עונה — לא מוצג');
    stats = { ok: true, counting: true, downloads: { windows: 1230, zip: 4, mac: 4, total: 1238 } };
    pass('המונה: "' + t + '" (עולה בהדרגה, ובלי תנועה כשמבקשים); כשהשרת לא סופר או לא עונה — לא מוצג כלום');
    await ctx.close();
  }

  // ---- כפתור התרומה של PayPal: נטען רק כשמגיעים לאזור; אם לא נטען — הקישור לדף התרומה
  {
    const ctx = await context({ width: 1366, height: 900 });
    const page = await ctx.newPage();
    sdkRequests = 0;
    await page.goto(base + 'index.html');
    await page.waitForTimeout(500);
    assert.strictEqual(sdkRequests, 0, 'ה-SDK של PayPal נטען לפני שגוללים אליו');
    await page.locator('#support').scrollIntoViewIfNeeded();
    await page.waitForTimeout(800);
    assert.strictEqual(sdkRequests, 1, 'ה-SDK של PayPal לא נטען כשהגענו לאזור');
    const fb = await page.$('#support .pp-fallback');
    assert.ok(fb && await fb.isVisible() && await fb.getAttribute('href') === DONATE && await fb.getAttribute('target') === '_blank', 'הקישור החלופי לתרומה');
    pass('תרומה: ה-SDK של PayPal נטען רק כשגוללים לאזור, וכשהוא חסום נשאר הקישור ' + DONATE);
    await ctx.close();
  }

  // ---- שליחת תרגיל, שלב אחרי שלב (#exercise): השלבים, הקוד שנבנה בדפדפן מהתשובה של Gemini, וההצעה שסגורה עד שלוחצים
  {
    const S = require(path.join(SITE, 'assets', 'scaffold.js'));
    const FIX = JSON.parse(fs.readFileSync(path.join(__dirname, 'ai', 'parity', '5-gemini-balanced-java.json'), 'utf8'));
    const ex = S.read(FIX), pkg = S.folderName(ex.topic, new Set(['examples']), ex.cs), files = S.build(ex, pkg, ['a.png', 'b.png', 'c.png'], false);
    const blank = s => s.replace(/^ $/gm, '');
    for (const [label, viewport] of [['desktop', { width: 1366, height: 900 }], ['mobile', { width: 390, height: 844 }]]) {
      const ctx = await context(viewport);
      const page = await ctx.newPage();
      const errors = [];
      page.on('console', m => { if (m.type() === 'error' && !/fonts\.gstatic|Failed to load resource: the server responded with a status of 404/.test(m.text())) errors.push(m.text()); });
      page.on('pageerror', e => errors.push(e.message));
      await page.emulateMedia({ reducedMotion: 'reduce' });
      await page.goto(base + 'index.html?os=win');
      await page.addStyleTag({ content: '.bar{position:static!important}' });
      await page.waitForSelector('[data-flow].ready');
      const sel = () => page.$$eval('[data-flow] .fp-steps [role=tab]', ts => ts.findIndex(t => t.getAttribute('aria-selected') === 'true'));
      const vis = n => page.isVisible('#fp-' + n + ' .fp-cap');
      assert.strictEqual(await page.$$eval('[data-flow] .fp-steps [role=tab]', l => l.length), 6, '6 שלבים');
      assert.ok(await vis(1) && !(await vis(2)), 'שלב 1 גלוי, והשאר מוסתרים');
      assert.ok(await page.$$eval('#exercise img[src*="img/"]', l => l.length >= 10 && l.every(i => i.loading === 'lazy')), 'הצילומים בנגן נטענים רק כשמגיעים אליהם');
      await page.locator('[data-flow]').scrollIntoViewIfNeeded();
      // הבא / הקודם
      await page.click('[data-fnext]');
      assert.strictEqual(await sel(), 1, 'הבא → שלב 2');
      assert.ok(await vis(2) && !(await vis(1)), 'שלב 2 גלוי');
      const fields = await page.$$eval('[data-ffields] li', l => l.map(x => x.textContent));
      assert.strictEqual(fields.length, 6, 'מה Gemini החזיר: ' + fields.join(' | '));
      assert.ok(fields.some(f => f.includes('boolean isBalanced(Queue<Integer> q)')) && fields.some(f => f.includes('int countBalanced(Stack<Queue<Integer>> st)')) && fields.some(f => f.includes('int sumPositive(Queue<Integer> q)')), 'הסעיפים והפעולה הנתונה מהתשובה: ' + fields.join(' | '));
      assert.ok(await page.isVisible('[data-ffields] li[data-k=main]'), 'בלי תנועה: כל השדות גלויים מיד');
      await page.click('[data-fprev]');
      assert.strictEqual(await sel(), 0, 'הקודם → שלב 1');
      // מקלדת: שמאלה = הבא, End / Home
      await page.focus('#fs-1');
      await page.keyboard.press('ArrowLeft');
      assert.strictEqual(await sel(), 1, 'חץ שמאלה → שלב 2');
      await page.keyboard.press('End');
      assert.strictEqual(await sel(), 5, 'End → שלב 6');
      await page.keyboard.press('Home');
      assert.strictEqual(await sel(), 0, 'Home → שלב 1');
      // שלב 3: החבילה, מהקוד של scaffold.js בדפדפן
      await page.click('#fs-3');
      await page.waitForSelector('[data-flow].fp-built', { timeout: 5000 });
      assert.strictEqual(await page.textContent('[data-fpkg]'), pkg, 'שם החבילה');
      assert.deepStrictEqual(await page.$$eval('[data-ffiles] .ff', l => l.map(x => x.textContent)), files.map(f => f.fileName), 'הקבצים בחבילה');
      assert.ok((await page.textContent('[data-fcount]')).startsWith(ex.tasks.length + ' '), 'מספר הסעיפים');
      // שלב 4: הקוד שמוצג הוא בדיוק מה שהתוסף בונה מאותה תשובה (scaffold-parity בודק שזה זהה ל-AiScaffold.java)
      await page.click('#fs-4');
      const code = await page.$eval('[data-fcode]', pre => [...pre.children].map(l => { const c = l.cloneNode(true); c.querySelectorAll('.n, .fp-bulb, .caret').forEach(x => x.remove()); return c.textContent; }).join('\n'));
      assert.strictEqual(blank(code), blank(files[0].code.replace(/\n$/, '')), 'הקוד בשלב 4 שונה ממה ש-scaffold.js בונה');
      assert.strictEqual(+(await page.getAttribute('[data-fcode] .l.cur', 'data-i')), files[0].cursorLine, 'הסמן בסעיף הראשון');
      assert.ok(await page.$('[data-fcode] .l.cur .caret'), 'סמן מהבהב בשורה');
      assert.strictEqual(await page.$$eval('[data-fcode] .fp-bulb', l => l.length), ex.tasks.length, 'מנורה ליד כל סעיף');
      const legend = await page.$$eval('[data-flegend] button', l => l.map(b => b.getAttribute('data-part')));
      assert.deepStrictEqual(legend, ['task-א', 'task-ב', 'given', 'expected', 'main'], 'מה יש בקובץ: ' + legend);
      await page.click('[data-flegend] [data-part=main]');
      await page.waitForTimeout(100);
      const mainSel = await page.$$eval('[data-fcode] .l.sel', l => l.map(x => x.textContent));
      assert.ok(mainSel.length > 10 && /void main\(/.test(mainSel[0]) && await page.$eval('[data-fcode]', p => p.scrollTop) > 0, 'לחיצה על main מסמנת וגוללת: ' + mainSel[0]);
      await (await page.$('[data-flow]')).screenshot({ path: path.join(OUT, label + '-flow-4-main.png') });
      // שלב 6: ההצעה סגורה עד שלוחצים על המנורה
      await page.click('#fs-6');
      assert.ok(await page.isVisible('[data-sol-closed]') && !(await page.isVisible('[data-sol-open=א]')), 'ההצעה סגורה');
      await page.click('[data-fsol] [data-bulb=א]');
      assert.ok(!(await page.isVisible('[data-sol-closed]')) && await page.isVisible('[data-sol-open=א] img'), 'מנורה של סעיף א פותחת את ההצעה');
      assert.strictEqual(await page.getAttribute('[data-fsol] [data-bulb=א]', 'aria-expanded'), 'true');
      await (await page.$('[data-flow]')).screenshot({ path: path.join(OUT, label + '-flow-6-open.png') });
      await page.click('[data-sol-open=א] [data-sol-close]');
      assert.ok(await page.isVisible('[data-sol-closed]'), 'סגירה');
      await page.click('[data-fsol] [data-bulb=ב]');
      assert.ok(await page.isVisible('[data-sol-open=ב]'), 'סעיף ב: הודעה');
      // מנורה בשלב 4 עוברת לשלב 6 ופותחת
      await page.click('#fs-4');
      await page.click('[data-fcode] [data-bulb=א]');
      assert.ok(await sel() === 5 && await page.isVisible('[data-sol-open=א] img'), 'מנורה בקוד → שלב 6, פתוח');
      for (let n = 1; n <= 6; n++) {
        await page.click('#fs-' + n);
        await page.waitForTimeout(60);
        await (await page.$('[data-flow]')).screenshot({ path: path.join(OUT, label + '-flow-' + n + '.png') });
      }
      // לנסות באתר, והלשוניות עם הצילומים מכל סביבה
      assert.ok(/נסו עכשיו בדפדפן, בלי התקנה/.test(await page.textContent('#exercise .fp-after a.btn[href="bagrut.html"]')), 'הקישור ל-bagrut.html');
      await page.click('#t-sub-ecl');
      assert.ok(await page.isVisible('#p-sub-ecl img') && !(await page.isVisible('#p-sub-ij img')), 'לשונית Eclipse');
      await page.click('#t-sub-web');
      assert.ok(await page.isVisible('#p-sub-web img'), 'לשונית bagrut.html');
      await page.click('#t-sub-ij');
      assert.deepStrictEqual(errors, [], 'שגיאות בקונסול (שליחת תרגיל): ' + label);
      await ctx.close();
    }

    // בלי "פחות תנועה": הנגן מתחיל לבד פעם אחת כשהוא על המסך, עוצר בלחיצה, ו"הפעלה" ממשיכה
    const ctx = await context({ width: 1366, height: 900 });
    const page = await ctx.newPage();
    const errors = [];
    page.on('pageerror', e => errors.push(e.message));
    await page.goto(base + 'index.html?os=win');
    await page.addStyleTag({ content: '.bar{position:static!important}' });
    await page.waitForSelector('[data-flow].ready');
    const sel = () => page.$$eval('[data-flow] .fp-steps [role=tab]', ts => ts.findIndex(t => t.getAttribute('aria-selected') === 'true'));
    assert.strictEqual(await sel(), 0);
    await page.addStyleTag({ content: 'html{scroll-behavior:auto!important}' });
    await page.evaluate(() => document.querySelector('[data-flow]').scrollIntoView({ block: 'center' }));
    await page.waitForTimeout(400);
    assert.ok(await page.$eval('[data-flow]', r => r.classList.contains('playing')), 'הנגן התחיל לבד');
    await (await page.$('[data-flow]')).screenshot({ path: path.join(OUT, 'anim-flow-1.png') });
    await page.waitForTimeout(3700);
    assert.strictEqual(await sel(), 1, 'אחרי 3.6 שניות: שלב 2');
    await page.waitForTimeout(1300);
    await (await page.$('[data-flow]')).screenshot({ path: path.join(OUT, 'anim-flow-2-reading.png') });
    const lit = await page.$$eval('#fp-2 .hl.lit, [data-ffields] li.lit', l => l.length);
    assert.ok(lit >= 2, 'Gemini "קורא": החלק בדף והשדה שיצא ממנו מסומנים יחד (' + lit + ')');
    await page.waitForTimeout(4400);
    assert.strictEqual(await sel(), 2, 'שלב 3');
    await page.waitForTimeout(500);
    await (await page.$('[data-flow]')).screenshot({ path: path.join(OUT, 'anim-flow-3-tree.png') });
    await page.waitForTimeout(3600);
    assert.strictEqual(await sel(), 3, 'שלב 4');
    await page.waitForTimeout(2200);
    await (await page.$('[data-flow]')).screenshot({ path: path.join(OUT, 'anim-flow-4-tour.png') });
    await page.click('#fs-2');
    assert.ok(!(await page.$eval('[data-flow]', r => r.classList.contains('playing'))) && await sel() === 1, 'לחיצה על שלב עוצרת');
    await page.waitForTimeout(4000);
    assert.strictEqual(await sel(), 1, 'אחרי עצירה לא ממשיך לבד');
    await page.click('[data-fplay]');
    assert.strictEqual(await page.getAttribute('[data-fplay]', 'aria-pressed'), 'true', '"הפעלה" ממשיכה');
    await page.click('[data-fplay]');
    assert.strictEqual(await page.getAttribute('[data-fplay]', 'aria-pressed'), 'false', '"עצירה"');
    assert.deepStrictEqual(errors, [], 'שגיאות (הנגן רץ לבד)');
    await ctx.close();
    pass('שליחת תרגיל, שלב אחרי שלב: 6 שלבים (הבא / הקודם / חצים / Home / End), מה Gemini החזיר, החבילה ' + pkg + ' והקוד זהים ל-scaffold.js, סמן בשורה ' + (files[0].cursorLine + 1) + ', ההצעה סגורה עד לחיצה על המנורה, מתחיל לבד פעם אחת ונעצר בלחיצה, בלי תנועה כשמבקשים');
  }

  // ---- נסו בעצמכם (#playground): הכפתורים משנים את הציור, והשורה ב-Java נוספת ומסומנת
  for (const [label, viewport] of [['desktop', { width: 1366, height: 900 }], ['mobile', { width: 390, height: 844 }]]) {
    const ctx = await context(viewport);
    const page = await ctx.newPage();
    const errors = [];
    page.on('pageerror', e => errors.push(e.message));
    page.on('console', m => { if (m.type() === 'error' && !/fonts\.gstatic|status of 404/.test(m.text())) errors.push(m.text()); });
    await page.goto(base + 'index.html?os=win');
    await page.addStyleTag({ content: '.bar{position:static!important}' });
    await page.waitForSelector('[data-playground].ready');
    await page.locator('[data-playground]').scrollIntoViewIfNeeded();
    const P = '[data-playground] ';
    const st = () => page.evaluate(() => {
      const r = document.querySelector('[data-playground]');
      return {
        boxes: r.querySelectorAll('.cup .box:not(.gone), .tube .box:not(.gone-q)').length,
        nodes: r.querySelectorAll('.pg-list .node:not(.lost)').length, lost: r.querySelectorAll('.pg-list .lost').length,
        tree: r.querySelectorAll('.pg-tree .tn').length,
        lines: r.querySelectorAll('[data-pcode] .ln').length, now: (r.querySelector('[data-pcode] .ln.now code') || {}).textContent,
        nowIsLast: !!r.querySelector('[data-pcode] .ln.now:last-child'),
        out: r.querySelector('[data-pout]').textContent, said: r.querySelector('[data-psaid]').textContent,
        vars: [...r.querySelectorAll('[data-pvars] tr')].map(t => t.textContent),
      };
    });
    const op = async (name, value) => {
      if (value !== undefined) await page.fill(P + '[data-pval]', String(value));
      await page.click(P + '[data-op=' + name + ']');
      return st();
    };
    // מחסנית
    let s0 = await st();
    assert.ok(s0.boxes === 2 && s0.lines === 3 && s0.nowIsLast, 'מחסנית בהתחלה: ' + JSON.stringify(s0));
    let s1 = await op('push', 7);
    assert.ok(s1.boxes === 3 && s1.lines === 4 && s1.now === 's.push(7);' && s1.nowIsLast && await page.$(P + '.cup .box.new.in'), 'push: ' + JSON.stringify(s1));
    await page.screenshot({ path: path.join(OUT, label + '-play-stack-push.png'), clip: await page.$eval(P, e => { const r = e.getBoundingClientRect(); return { x: r.x, y: r.y, width: r.width, height: r.height }; }) }).catch(() => {});
    s1 = await op('pop');
    assert.ok(s1.boxes === 2 && s1.now === 'int x = s.pop();' && s1.vars.some(v => /^x7$/.test(v)) && await page.$(P + '.cup .box.gone'), 'pop: ' + JSON.stringify(s1));
    s1 = await op('top');
    assert.ok(s1.out.trim() === '9' && s1.now === 'System.out.println(s.top());' && await page.$(P + '.cup .box.read'), 'top: ' + JSON.stringify(s1));
    s1 = await op('isEmpty');
    assert.ok(s1.out.split('\n').pop() === 'false', 'isEmpty: ' + JSON.stringify(s1));
    for (let i = 0; i < 6; i++) if (!(await page.isDisabled(P + '[data-op=push]'))) await op('push', i + 1);
    assert.ok(await page.isDisabled(P + '[data-op=push]') && (await st()).boxes === 6, 'מחסנית מלאה: push מושבת');
    await page.fill(P + '[data-pval]', '500');
    await page.click(P + '[data-op=pop]');
    while (!(await page.isDisabled(P + '[data-op=pop]'))) await page.click(P + '[data-op=pop]');
    assert.ok((await st()).boxes === 0 && await page.isDisabled(P + '[data-op=top]'), 'מחסנית ריקה: pop ו-top מושבתים');
    await page.fill(P + '[data-pval]', '500');
    await page.click(P + '[data-op=push]');
    assert.ok(/ערך בין/.test((await st()).said), 'ערך לא חוקי');
    await page.click(P + '[data-preset]');
    assert.strictEqual((await st()).boxes, 2, '"מההתחלה"');
    // תור
    await page.click(P + '[data-kind=queue]');
    s0 = await st();
    assert.ok(s0.boxes === 2 && /^Queue<Integer> q/.test((await page.textContent(P + '[data-pcode] .ln code'))), 'תור בהתחלה: ' + JSON.stringify(s0));
    s1 = await op('insert', 4);
    assert.ok(s1.boxes === 3 && s1.now === 'q.insert(4);' && s1.vars[0] === 'q[3,8,4]', 'insert: ' + JSON.stringify(s1));
    s1 = await op('remove');
    assert.ok(s1.boxes === 2 && s1.now === 'int x = q.remove();' && s1.vars.some(v => v === 'x3') && await page.$(P + '.tube .gone-q'), 'remove: ' + JSON.stringify(s1));
    s1 = await op('head');
    assert.ok(s1.out.trim() === '8', 'head: ' + JSON.stringify(s1));
    await page.screenshot({ path: path.join(OUT, label + '-play-queue.png'), clip: await page.$eval(P, e => { const r = e.getBoundingClientRect(); return { x: r.x, y: r.y, width: r.width, height: r.height }; }) }).catch(() => {});
    // רשימה מקושרת
    await page.click(P + '[data-kind=list]');
    s0 = await st();
    assert.strictEqual(s0.nodes, 3, 'רשימה בהתחלה');
    s1 = await op('addFirst', 1);
    assert.ok(s1.nodes === 4 && s1.now === 'lst = new Node<Integer>(1, lst);' && s1.vars[0] === 'lstNode [1]', 'הוספה בהתחלה: ' + JSON.stringify(s1));
    s1 = await op('addSecond', 6);
    assert.ok(s1.nodes === 5 && s1.now === 'lst.setNext(new Node<Integer>(6, lst.getNext()));', 'הוספה אחרי הראשונה: ' + JSON.stringify(s1));
    s1 = await op('removeFirst');
    assert.ok(s1.nodes === 4 && s1.lost === 1 && s1.now === 'lst = lst.getNext();' && s1.vars[0] === 'lstNode [6]', 'מחיקת הראשונה (והחוליה שאבדה): ' + JSON.stringify(s1));
    s1 = await op('removeSecond');
    assert.ok(s1.nodes === 3 && s1.lost === 1 && s1.now === 'lst.setNext(lst.getNext().getNext());', 'מחיקת השנייה: ' + JSON.stringify(s1));
    await page.screenshot({ path: path.join(OUT, label + '-play-list.png'), clip: await page.$eval(P, e => { const r = e.getBoundingClientRect(); return { x: r.x, y: r.y, width: r.width, height: r.height }; }) }).catch(() => {});
    // עץ חיפוש: המסלול נדלק, ואז הצומת החדש; inOrder מדפיס מהקטן לגדול
    await page.click(P + '[data-kind=tree]');
    assert.strictEqual((await st()).tree, 4, 'עץ בהתחלה');
    await op('insert', 7);
    await page.waitForTimeout(200);
    await page.screenshot({ path: path.join(OUT, label + '-play-tree-path.png'), clip: await page.$eval(P, e => { const r = e.getBoundingClientRect(); return { x: r.x, y: r.y, width: r.width, height: r.height }; }) }).catch(() => {});
    await page.waitForFunction(() => document.querySelectorAll('[data-playground] .pg-tree .tn').length === 5, null, { timeout: 3000 });
    s1 = await st();
    assert.ok(s1.now === 'insert(t, 7);' && /7 נכנס כבן הימני של 5/.test(s1.said) && await page.$(P + '.pg-tree .tn.new'), 'insert: ' + JSON.stringify(s1));
    await op('inOrder');
    await page.waitForTimeout(2600);
    s1 = await st();
    assert.ok(s1.out.trim() === '3 5 7 10 15' && (await page.$$(P + '.pg-tree .tn.read')).length === 5, 'inOrder: ' + JSON.stringify(s1));
    await page.screenshot({ path: path.join(OUT, label + '-play-tree.png'), clip: await page.$eval(P, e => { const r = e.getBoundingClientRect(); return { x: r.x, y: r.y, width: r.width, height: r.height }; }) }).catch(() => {});
    for (const v of [12, 13]) await op('insert', v);
    await page.waitForTimeout(1500);
    const deep = await op('insert', 14);
    assert.ok(/4 רמות/.test(deep.said) && deep.tree === 7, 'עץ עמוק מדי לציור: ' + JSON.stringify(deep));
    // לשוניות המבנים: חיצים (מימין לשמאל: שמאלה = הבאה)
    await page.focus(P + '[data-kind=stack]');
    await page.keyboard.press('ArrowLeft');
    assert.strictEqual(await page.getAttribute(P + '[data-kind=queue]', 'aria-selected'), 'true', 'חץ בלשוניות המבנים');
    const sw = await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth);
    assert.ok(sw, 'נסו בעצמכם: בלי גלילה הצידה ' + label);
    assert.deepStrictEqual(errors, [], 'שגיאות בקונסול (נסו בעצמכם): ' + label);
    await ctx.close();
  }
  pass('נסו בעצמכם (1366 ו-390): מחסנית, תור, רשימה ועץ — כל כפתור משנה את הציור ומוסיף שורת Java מסומנת; pop/remove מנפישים יציאה, חוליה שאבדה, מסלול בעץ ו-inOrder; גבולות, ערך לא חוקי ו"מההתחלה"');

  // ---- ההדגמה בראש העמוד: הפעלה ועצירה (כפתור ורווח), Home / End; ו"מה חדש"
  {
    const ctx = await context({ width: 1366, height: 900 });
    const page = await ctx.newPage();
    await page.emulateMedia({ reducedMotion: 'reduce' });
    await page.goto(base + 'index.html?os=win');
    await page.waitForSelector('[data-demo].ready');
    const step = async () => +(/צעד (\d+)/.exec(await page.textContent('[data-demo] [data-count]'))[1]);
    await page.focus('[data-demo]');
    await page.keyboard.press('Space');
    assert.strictEqual(await page.getAttribute('[data-demo] [data-play]', 'aria-pressed'), 'true', 'רווח מפעיל');
    await page.waitForTimeout(1300);
    assert.ok(await step() >= 2, 'ההדגמה רצה');
    await page.keyboard.press('Space');
    assert.strictEqual(await page.getAttribute('[data-demo] [data-play]', 'aria-pressed'), 'false', 'רווח עוצר');
    const at = await step();
    await page.waitForTimeout(1300);
    assert.strictEqual(await step(), at, 'אחרי עצירה לא זז');
    await page.keyboard.press('End');
    const last = await step();
    assert.ok(/מתוך (\d+)/.exec(await page.textContent('[data-demo] [data-count]'))[1] == last, 'End → הצעד האחרון');
    await page.keyboard.press('Home');
    assert.strictEqual(await step(), 1, 'Home → הצעד הראשון');
    await page.keyboard.press('ArrowLeft');
    assert.strictEqual(await step(), 2, 'חץ שמאלה → הבא');
    await page.click('[data-demo] [data-play]');
    assert.strictEqual(await page.textContent('[data-demo] [data-play]'), 'עצירה', 'כפתור הפעלה');
    await page.click('[data-demo] [data-play]');
    assert.ok(await page.isVisible('.keys kbd'), 'רמז קיצורי המקלדת');
    const news = await page.$$eval('#news .log li', l => l.map(li => ({ d: (li.querySelector('time') || {}).getAttribute && li.querySelector('time').getAttribute('datetime'), h: (li.querySelector('h3') || {}).textContent })));
    assert.ok(news.length >= 5 && news.every(n => /^2026-\d\d-\d\d$/.test(n.d) && n.h), 'מה חדש: ' + JSON.stringify(news));
    assert.ok(news.every((n, i) => i === 0 || n.d <= news[i - 1].d), 'מה חדש: מהחדש לישן');
    await ctx.close();
    pass('ההדגמה: רווח / כפתור מפעילים ועוצרים, Home / End / חצים; "מה חדש": ' + news.length + ' פריטים עם תאריך, מהחדש לישן');
  }

  // ---- ההדגמה, הלשוניות וצילומים של האזורים
  for (const [label, viewport] of [['desktop', { width: 1366, height: 900 }], ['mobile', { width: 390, height: 844 }]]) {
    const ctx = await context(viewport);
    const page = await ctx.newPage();
    await page.emulateMedia({ reducedMotion: 'reduce' });
    await page.goto(base + 'index.html?os=win');
    await page.waitForSelector('[data-demo].ready');
    for (let i = 0; i < 5; i++) await page.click('[data-next]');
    const demo = await page.evaluate(() => ({ count: document.querySelector('[data-count]').textContent, now: document.querySelectorAll('[data-demo] .ln.now').length, boxes: document.querySelectorAll('[data-demo] .box:not(.gone)').length, gone: document.querySelectorAll('[data-demo] .box.gone').length }));
    assert.ok(/צעד 6 מתוך/.test(demo.count) && demo.now === 1 && demo.boxes >= 3, 'ההדגמה: ' + JSON.stringify(demo));
    await page.click('[role=tab]#t-list');
    assert.strictEqual(await page.getAttribute('#p-list', 'aria-hidden'), 'false');
    assert.ok(await page.isVisible('#p-list img') && !(await page.isVisible('#p-stack img')), 'לשונית ListTargil');
    await page.focus('#t-list');
    await page.keyboard.press('ArrowRight');
    assert.strictEqual(await page.getAttribute('#t-tree', 'aria-selected'), 'true', 'חיצים בלשוניות');
    await page.click('[role=tab]#t-stack');
    // צילום של אזור: בלי הסרגל העליון הדביק מעליו
    await page.addStyleTag({ content: '.bar{position:static!important}' });
    for (const id of ['exercise', 'run', 'playground', 'errors', 'teachers', 'install', 'news', 'faq', 'support']) {
      const el = await page.$('#' + id);
      await el.screenshot({ path: path.join(OUT, label + '-index-' + id + '.png') });
    }
    await page.goto(base + 'install.html?os=mac');
    await page.addStyleTag({ content: '.bar{position:static!important}' });
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
