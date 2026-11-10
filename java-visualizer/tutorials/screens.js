// סרטון הדרכה מצילומי מסך אמיתיים (לא מסגרת מצוירת): node screens.js <תיקיית הצילומים> <תסריט, למשל vs/f> [--fps 24] [--times 3,10]
//   תיקיית הצילומים — tutorial/ מהבדיקה על Windows (toolbar-e2e.ps1, סעיף 9): ‎tut-*.png ו-steps.json (לאן לוחצים בכל צילום).
//   התסריט — scripts/<שם>.screens.js: כיתוב לכל צילום. הפלט: ../help/<תיקייה>/<id>.mp4 + פוסטר ‎<id>.jpg.
// 1. screens_prep.py — איפה השתנה המסך בין צילום לצילום (לאן המצלמה מתקרבת כשמקלידים)
// 2. screens.html — ציר זמן אחד, render(t): העכבר נע למלבן האמיתי של הכפתור, לחיצה, הדגשה, מצלמה וכיתוב גדול
// 3. audio.py (מוזיקה + קליקים) ו-ffmpeg
const fs = require('fs'), path = require('path'), cp = require('child_process');
const T = __dirname, ROOT = path.join(T, '..');
const args = process.argv.slice(2);
const shots = path.resolve(args[0]), sid = args[1];
if (!shots || !sid) { console.error('node screens.js <shots dir> <script, e.g. vs/f>'); process.exit(2); }
const opt = k => (args.includes(k) ? args[args.indexOf(k) + 1] : null);
const fps = +(opt('--fps') || 24);
const [sub, id] = sid.includes('/') ? sid.split('/') : ['', sid];
const W = path.join(T, 'work', 'screens-' + sid.replace('/', '-'));
fs.mkdirSync(W, { recursive: true });
const sh = (c, a, o) => { const r = cp.spawnSync(c, a, Object.assign({ stdio: 'inherit' }, o)); if (r.status !== 0) throw new Error(c + ' failed'); };

const script = require(path.join(T, 'scripts', sid + '.screens.js'));
sh('python3', [path.join(T, 'screens_prep.py'), shots, path.join(W, 'prep.json')]);
const prep = JSON.parse(fs.readFileSync(path.join(W, 'prep.json'), 'utf8'));
// בדיקה בעיניים לפני הסרטון: כל מלבן מצויר על הצילום המקורי (work/.../check/_sheet.jpg) — שיושב בדיוק על הכפתור
sh('python3', [path.join(T, 'screens_check.py'), shots, path.join(W, 'check')]);
const info = JSON.parse(fs.readFileSync(path.join(shots, 'steps.json'), 'utf8').replace(/^﻿/, ''));
const raw = Array.isArray(info) ? info : info.steps;
const esc = s => s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
// מילים באנגלית בתוך המשפט בעברית: יחידה אחת משמאל לימין, שלא תישבר בין שורות (Create a new project, Ctrl+A ...)
const ltr = s => s.replace(/[A-Za-z][A-Za-z0-9+#:()\-]*(?:[ .][A-Za-z0-9+#:()\-]+)*/g, m => `<span class="ltr">${m}</span>`);
const html = s => esc(s).split(/\*\*(.+?)\*\*/).map((part, i) => (i % 2 ? `<b>${ltr(part)}</b>` : ltr(part))).join('');
const matches = (m, name) => (m instanceof RegExp ? m.test(name) : name.startsWith(m));
const union = rs => {
  rs = rs.filter(Boolean);
  if (!rs.length) return null;
  const x1 = Math.min(...rs.map(r => r.x)), y1 = Math.min(...rs.map(r => r.y)), x2 = Math.max(...rs.map(r => r.x + r.w)), y2 = Math.max(...rs.map(r => r.y + r.h));
  return { x: x1, y: y1, w: x2 - x1, h: y2 - y1 };
};
const steps = [];
raw.forEach((s, i) => {
  const name = s.shot.replace(/\.png$/, '');
  const item = script.items.find(it => matches(it.match, name));
  if (!item) { console.warn('אין כיתוב לצילום — מדלגים:', name); return; }
  if (item.skip) return;
  const ch = (prep.steps[i] || {}).change;
  steps.push({
    name, src: 'file://' + path.join(shots, s.shot), kind: item.kind || s.kind || 'look', target: s.target || null,
    change: ch && ch.frac < 0.35 ? ch : null,   // כמעט כל המסך השתנה (חלון חדש) — זה לא "מה שהקלידו"
    text: item.text, html: html(item.text), groupDur: item.group ? item.dur : 0, noZoom: !!item.noZoom, maxZoom: item.maxZoom || 0,
    useChange: item.useChange !== undefined ? item.useChange : false, focusFrom: item.focusFrom || null,
  });
});
for (const s of steps) {
  if (s.focusFrom) s.focusRect = union(steps.filter(x => matches(s.focusFrom, x.name)).map(x => x.change));
  delete s.focusFrom;
}
console.log(`${steps.length} צעדים מצילומים אמיתיים (${info.vs || ''})`);
const DATA = { intro: script.intro, outro: script.outro, steps };
fs.writeFileSync(path.join(W, 'data.json'), JSON.stringify(DATA, null, 1));

(async () => {
  let pw;
  for (const p of ['playwright', '/opt/node-tools/node_modules/playwright']) { try { pw = require(p); break; } catch (e) { } }
  const frames = path.join(W, 'frames');
  const only = opt('--times') ? opt('--times').split(',').map(Number) : null;
  if (!only) { fs.rmSync(frames, { recursive: true, force: true }); }
  fs.mkdirSync(frames, { recursive: true });
  const b = await pw.chromium.launch({ args: ['--allow-file-access-from-files'] });
  const pg = await b.newPage({ viewport: { width: 1920, height: 1080 } });
  pg.on('pageerror', e => { console.error('PAGE ERROR', e.message); process.exit(1); });
  await pg.addInitScript(d => { window.DATA = d; }, DATA);
  await pg.goto('file://' + path.join(T, 'screens.html'));
  await pg.waitForFunction(() => window.READY === true, null, { timeout: 120000 });
  const dur = await pg.evaluate(() => DURATION);
  const meta = await pg.evaluate(() => ({ clicks: CLICK_TIMES, cuts: CUT_TIMES, poster: META.poster }));
  const stage = await pg.$('#stage');
  const list = only || [...Array(Math.ceil(dur * fps)).keys()].map(i => i / fps);
  let n = 0;
  for (const t of list) {
    await pg.evaluate(t => render(t), t);
    await stage.screenshot({ path: path.join(frames, only ? `t${t}.jpg` : String(n).padStart(5, '0') + '.jpg'), type: 'jpeg', quality: 90 });
    n++;
    if (n % 300 === 0) console.log(`${n}/${list.length}`);
  }
  const helpDir = path.join(ROOT, 'help', sub);
  fs.mkdirSync(helpDir, { recursive: true });
  if (!only) {
    await pg.evaluate(t => render(t), meta.poster);
    await stage.screenshot({ path: path.join(helpDir, id + '.jpg'), type: 'jpeg', quality: 82 });
  }
  await b.close();
  if (only) { console.log('frames:', frames); return; }
  fs.writeFileSync(path.join(W, 'timing.json'), JSON.stringify({ dur, ...meta }));
  sh('python3', [path.join(T, 'audio.py'), path.join(W, 'timing.json'), path.join(W, 'audio.wav')]);
  const out = path.join(helpDir, id + '.mp4');
  sh('ffmpeg', ['-y', '-v', 'error', '-framerate', String(fps), '-i', path.join(frames, '%05d.jpg'), '-i', path.join(W, 'audio.wav'),
    '-vf', 'scale=1280:720:flags=lanczos', '-r', '20', '-c:v', 'libx264', '-preset', 'slow', '-crf', '29', '-profile:v', 'main',
    '-pix_fmt', 'yuv420p', '-movflags', '+faststart', '-c:a', 'aac', '-b:a', '48k', '-ac', '1', '-shortest', out]);
  console.log('done', out, (fs.statSync(out).size / 1e6).toFixed(1) + 'MB', dur.toFixed(1) + 's');
})().catch(e => { console.error(e); process.exit(1); });
