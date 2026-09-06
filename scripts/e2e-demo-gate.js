/**
 * כניסת הדמו חייבת להיעלם מבתי ספר אמיתיים — ולהישאר באתר הדוגמה ובבית ספר demo.
 * שני הצדדים נבדקים: גם שהוסר, וגם שלא הרסתי את הדמו במקום שבו הוא נחוץ.
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

const look = async (b, url) => {
  const pg = await b.newPage({ viewport: { width: 1280, height: 900 } });
  await pg.goto(url, { waitUntil: 'domcontentloaded', timeout: 60000 });
  await pg.waitForSelector('text=Java לבגרות', { timeout: 45000 }).catch(() => {});
  await pg.waitForTimeout(2500);
  const body = await pg.textContent('body').catch(() => '');
  const shot = url.replace(/[^a-z0-9]/gi, '_').slice(-30);
  await pg.screenshot({ path: 'shots/demo-gate-' + shot + '.png' });
  await pg.close();
  return {
    demo: /כניסת דמו/.test(body),
    teacherDemo: /דמו למורה/.test(body),
    google: /התחברות עם Google/.test(body),
  };
};

(async () => {
  await serve(4460);
  let b; try { b = await chromium.launch({ channel: 'chrome' }); } catch (e) { b = await chromium.launch({ channel: 'msedge' }); }
  try {
    const real = await look(b, 'http://localhost:4460/lamerhav');
    t('בית ספר אמיתי — אין "כניסת דמו"', !real.demo);
    t('בית ספר אמיתי — אין "דמו למורה"', !real.teacherDemo);
    t('בית ספר אמיתי — כניסת גוגל נשארה', real.google);

    const demoSchool = await look(b, 'http://localhost:4460/demo');
    t('בית הספר demo — הדמו נשאר', demoSchool.demo && demoSchool.teacherDemo);

    console.log('\n===== ' + pass + '/' + (pass + fail) + ' =====');
  } catch (e) {
    fail++; console.log('שגיאה: ' + String(e && e.message).slice(0, 200));
  } finally { await b.close(); process.exit(fail > 0 ? 1 : 0); }
})();
