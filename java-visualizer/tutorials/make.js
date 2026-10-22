// node make.js <id> [--prep-only|--render-only] [--fps 30]
// בונה סרטון הדרכה: 1. צילומים אמיתיים (scripts/<id>.prep.js) → work/<id>/assets.js
//                   2. רינדור כל פריים (engine.html?t=<id>) → work/<id>/frames
//                   3. פסקול (audio.py) וקידוד ל-webm (VP9 + Opus) → ../help/<id>.webm + פוסטר
const fs = require('fs'), path = require('path'), cp = require('child_process');
const T = __dirname, ROOT = path.join(T, '..');
const id = process.argv[2];
const args = process.argv.slice(3);
const fps = +(args[args.indexOf('--fps') + 1] || 30) || 30;
const W = path.join(T, 'work', id);
fs.mkdirSync(W, { recursive: true });
const sh = (cmd, a, opt = {}) => cp.execFileSync(cmd, a, Object.assign({ stdio: ['ignore', 'pipe', 'inherit'], encoding: 'utf8' }, opt));
const templates = require(path.join(ROOT, 'vscode-extension', 'templates'));

const ctx = {
  id, W, templates,
  assets: {},
  /** חלון אמיתי של התוסף לפי תסריט; מחזיר את ה-JSON של כל צילום */
  dlg(name, kind, preset, lines, doc, marker) {
    const dir = path.join(W, 'dlg', name);
    fs.rmSync(dir, { recursive: true, force: true });
    fs.mkdirSync(dir, { recursive: true });
    const sf = path.join(dir, 'script.txt');
    fs.writeFileSync(sf, lines.join('\n') + '\n');
    const a = [kind, preset, sf, dir];
    if (doc !== undefined) { const df = path.join(dir, 'doc.java'); fs.writeFileSync(df, doc); a.push(df); if (marker) a.push(marker); }
    sh('sh', [path.join(T, 'dlg.sh'), ...a], { stdio: 'inherit' });
    // בלי מנהל חלונות, חלק מהחלון שלא צויר יוצא שחור (בימין ולמטה) — חותכים אותו
    sh('python3', ['-c', `
import sys, glob, json
from PIL import Image
import numpy as np
for f in glob.glob(sys.argv[1] + '/*.png'):
    a = np.asarray(Image.open(f).convert('RGB')).astype(int)
    dark = (a.sum(axis=2) < 30)
    h, w = dark.shape
    while h > 10 and dark[h - 1, :].mean() > .9: h -= 1
    while w > 10 and dark[:h, w - 1].mean() > .9: w -= 1
    Image.open(f).crop((0, 0, w, h)).save(f)
    j = f[:-4] + '.json'
    d = json.load(open(j)); d['w'] = w; d['h'] = h; json.dump(d, open(j, 'w'), ensure_ascii=False)
`, dir]);
    const out = {};
    for (const l of lines) {
      const m = /^shot (\S+)/.exec(l.trim());
      if (!m) continue;
      const js = JSON.parse(fs.readFileSync(path.join(dir, m[1] + '.json'), 'utf8'));
      js.src = `work/${id}/dlg/${name}/${m[1]}.png`;
      this.assets[`${name}/${m[1]}`] = js;
      out[m[1]] = js;
    }
    return out;
  },
  /** מריץ תוכנית בחלון ההרצה ומצלם את הצעדים (חתוך לחלון בלבד, 1440x828) */
  viewer(name, files, main, steps, withMain) {
    const dir = path.join(W, 'run', name);
    fs.rmSync(dir, { recursive: true, force: true });
    fs.mkdirSync(dir, { recursive: true });
    for (const [f, text] of Object.entries(files)) { fs.mkdirSync(path.dirname(path.join(dir, f)), { recursive: true }); fs.writeFileSync(path.join(dir, f), text); }
    const cap = path.join(dir, 'cap');
    fs.mkdirSync(cap, { recursive: true });
    for (let attempt = 0; attempt < 3; attempt++) {
      try { sh('sh', [path.join(T, 'viewer.sh'), dir, main, path.join(cap, 's'), ...steps.map(String)], { stdio: 'inherit', env: Object.assign({}, process.env, withMain ? { MAINBTN: '1' } : {}) }); } catch (e) { /* בודקים בהמשך */ }
      if (steps.every(s => fs.existsSync(path.join(cap, `s-${s}.png`)))) break;
    }
    sh('python3', ['-c', `
import sys, glob, os
from PIL import Image
for f in glob.glob(os.path.join(sys.argv[1], 's-*.png')):
    Image.open(f).convert('RGB').crop((80, 36, 1520, 864)).save(f[:-4] + '.jpg', quality=92)
`, cap]);
    return steps.map(s => `work/${id}/run/${name}/cap/s-${s}.jpg`);
  },
  write(file, text) { fs.writeFileSync(path.join(W, file), text); },
};

(async () => {
  if (!args.includes('--render-only')) {
    const prep = require(path.join(T, 'scripts', id + '.prep.js'));
    const extra = await prep(ctx);
    fs.writeFileSync(path.join(W, 'assets.js'), `window.ASSETS = ${JSON.stringify(ctx.assets)};\nwindow.DATA = ${JSON.stringify(extra || {})};\n`);
    console.log('assets ok');
  }
  if (args.includes('--prep-only')) return;

  // רינדור
  let pw;
  for (const p of ['playwright', '/opt/node-tools/node_modules/playwright']) { try { pw = require(p); break; } catch (e) { } }
  const frames = path.join(W, 'frames');
  fs.rmSync(frames, { recursive: true, force: true });
  fs.mkdirSync(frames, { recursive: true });
  const b = await pw.chromium.launch({ args: ['--allow-file-access-from-files'] });
  const pg = await b.newPage({ viewport: { width: 1920, height: 1080 } });
  pg.on('pageerror', e => { console.error('PAGE ERROR', e.message); process.exit(1); });
  await pg.goto('file://' + path.join(T, 'engine.html') + '?t=' + id);
  await pg.waitForFunction(() => window.READY === true, null, { timeout: 60000 });
  const dur = await pg.evaluate(() => DURATION);
  const meta = await pg.evaluate(() => ({ clicks: CLICK_TIMES, cuts: CUT_TIMES, poster: (window.META && window.META.poster) || 0 }));
  const stage = await pg.$('#stage');
  const only = args.includes('--times') ? args[args.indexOf('--times') + 1].split(',').map(Number) : null;
  const list = only || [...Array(Math.ceil(dur * fps)).keys()].map(i => i / fps);
  let n = 0;
  for (const t of list) {
    await pg.evaluate(t => render(t), t);
    await stage.screenshot({ path: path.join(frames, only ? `t${t}.jpg` : String(n).padStart(5, '0') + '.jpg'), type: 'jpeg', quality: 90 });
    n++;
    if (n % 300 === 0) console.log(`${n}/${list.length}`);
  }
  // פוסטר
  await pg.evaluate(t => render(t), meta.poster || Math.min(dur - 1, 12));
  const helpDir = path.join(ROOT, 'help');
  fs.mkdirSync(helpDir, { recursive: true });
  await stage.screenshot({ path: path.join(helpDir, id + '.jpg'), type: 'jpeg', quality: 82 });
  await b.close();
  if (only) return;
  fs.writeFileSync(path.join(W, 'timing.json'), JSON.stringify({ dur, ...meta }));
  sh('python3', [path.join(T, 'audio.py'), path.join(W, 'timing.json'), path.join(W, 'audio.wav')], { stdio: 'inherit' });
  const out = path.join(helpDir, id + '.webm');
  sh('ffmpeg', ['-y', '-v', 'error', '-framerate', String(fps), '-i', path.join(frames, '%05d.jpg'), '-i', path.join(W, 'audio.wav'),
    '-vf', 'scale=1600:900:flags=lanczos', '-c:v', 'libvpx-vp9', '-crf', '36', '-b:v', '0', '-deadline', 'good', '-cpu-used', '4', '-row-mt', '1',
    '-pix_fmt', 'yuv420p', '-c:a', 'libopus', '-b:a', '80k', '-shortest', out], { stdio: 'inherit' });
  console.log('done', out, (fs.statSync(out).size / 1e6).toFixed(1) + 'MB', dur.toFixed(1) + 's');
})().catch(e => { console.error(e); process.exit(1); });
