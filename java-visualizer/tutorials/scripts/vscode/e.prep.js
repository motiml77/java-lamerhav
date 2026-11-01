// הדרכה ה ב-VS Code: שליחת תרגיל והצעה לפתרון — החלון האמיתי (submit.js), הקבצים שהמנוע האמיתי בונה (SubmitCli מול שרת מדומה
// עם התרגיל של tools/test/ai/1-stack.json — אותו תרגיל כמו בדף), ה-💡 מעל הסעיפים, וחלון ההצעה האמיתי
const fs = require('fs'), path = require('path'), os = require('os'), cp = require('child_process');
const ROOT = path.join(__dirname, '..', '..', '..');
const submit = require(path.join(ROOT, 'vscode-extension', 'submit'));
const PAGE = path.join(__dirname, '..', '..', 'assets', 'exam-page.png');
const toast = (o) => `<!DOCTYPE html><html><head><meta charset="UTF-8"><style>
body { margin: 0; background: #f8f8f8; font: 13px/1.45 var(--vscode-font-family); color: #3b3b3b; overflow: hidden; }
.n { width: 448px; box-sizing: border-box; border: 1px solid #e5e5e5; background: #f8f8f8; padding: 10px 12px; display: flex; gap: 10px; align-items: flex-start; }
.i { width: 16px; height: 16px; border-radius: 50%; background: #005fb8; color: #fff; font-size: 11px; font-weight: 700; display: flex; align-items: center; justify-content: center; flex: none; margin-top: 1px; }
.t { flex: 1; direction: rtl; text-align: right; unicode-bidi: plaintext; }
.src { color: #717171; font-size: 12px; margin-top: 6px; direction: ltr; text-align: left; }
.bar { height: 2px; background: #e5e5e5; margin-top: 8px; position: relative; overflow: hidden; } .bar i { position: absolute; left: 20%; width: 30%; height: 2px; background: #005fb8; }
</style></head><body><div class="n"><span class="i">${o.progress ? '⟳' : 'i'}</span><div class="t">${o.title ? `<b>${o.title}</b>: ` : ''}${o.text}
<div class="src">Source: מבני נתונים (Extension)</div>${o.progress ? '<div class="bar"><i></i></div>' : ''}</div></div></body></html>`;

module.exports = async ctx => {
  // 1. הדף (צילום מסך)
  const b64 = fs.readFileSync(PAGE).toString('base64');
  await ctx.web('page', `<!DOCTYPE html><html><head><meta charset="UTF-8"><style>body{margin:0;background:#fff}img{display:block;width:860px}</style></head><body><img src="data:image/png;base64,${b64}"></body></html>`, null, [860, 340], [{ wait: 200 }, { shot: 'p' }]);

  // 2. החלון "שליחת תרגיל" (הדף האמיתי של התוסף) — ריק, אחרי Ctrl+V, עם הערה, ובזמן השליחה
  const item = { id: 1, name: 'תמונה מודבקת 1.png', kind: 'image', size: fs.statSync(PAGE).size, thumb: 'data:image/png;base64,' + b64 };
  const html = submit.submitHtml({ lang: 'java', where: 'DataStructures', cspSource: '' });
  await ctx.web('sub', html, null, [992, 781], [
    { wait: 300 }, { shot: 's1' },
    { eval: `window.postMessage({ type: 'items', items: ${JSON.stringify([item])} }, '*')` }, { wait: 300 }, { shot: 's2' },
    { fill: ['#note', 'רק שאלה 1'] }, { shot: 's3' },
    { eval: `window.postMessage({ type: 'build' }, '*'); window.postMessage({ type: 'progress', text: 'שולח את התרגיל… (בדרך כלל עד דקה)' }, '*')` }, { wait: 200 }, { shot: 's4' }]);

  // 3. המנוע האמיתי (SubmitCli) מול שרת מדומה — הקבצים, השורה של הסמן וההצעות שנשמרו
  const tmp = fs.mkdtempSync(path.join(os.tmpdir(), 'dsviz-tut-e-'));
  const src = path.join(tmp, 'DataStructures', 'src');
  fs.mkdirSync(src, { recursive: true });
  fs.copyFileSync(PAGE, path.join(tmp, 'page.png'));
  const mock = cp.spawn('node', [path.join(ROOT, 'tools', 'test', 'submit', 'mock-worker.js'), '8801', path.join(ROOT, 'tools', 'test', 'ai', '1-stack.json')]);
  await new Promise(r => setTimeout(r, 700));
  const man = { lang: 'java', out: src, store: path.join(tmp, 'solutions.json'), lib: path.join(ROOT, 'release', 'dsviz.jar'), note: 'רק שאלה 1',
    client: 'dsviz-vscode/1.0.1', items: [{ path: path.join(tmp, 'page.png'), name: 'תמונה מודבקת 1.png' }] };
  fs.writeFileSync(path.join(tmp, 'm.json'), JSON.stringify(man));
  const env = Object.assign({}, process.env, { DSVIZ_WORKER_URL: 'http://127.0.0.1:8801' });
  delete env.JAVA_TOOL_OPTIONS;
  const out = cp.execFileSync('java', ['-Dfile.encoding=UTF-8', '-Dstdout.encoding=UTF-8', '-cp', man.lib, 'il.lamerhav.dsviz.SubmitCli', 'submit', '--manifest', path.join(tmp, 'm.json')], { env, encoding: 'utf8' });
  mock.kill();
  const done = out.trim().split('\n').map(l => { try { return JSON.parse(l); } catch (e) { return {}; } }).find(j => j.done);
  if (!done || done.errors.length) throw new Error('SubmitCli: ' + out);
  const code = fs.readFileSync(done.focus, 'utf8').replace(/\r/g, '').split('\n');
  if (code[code.length - 1] === '') code.pop();
  const entries = submit.readStore(man.store);
  const lenses = submit.lensesFor(entries, done.focus, code.join('\n')).map(l => ({ line: l.line + 1, label: l.entry.label, method: l.entry.method }));
  const mainLine = code.findIndex(l => /static void main/.test(l)) + 1;
  const expectLine = code.findIndex(l => l.includes('צריך להיות מודפס')) + 1;

  // 4. החלון "הצעה לפתרון" (האמיתי) לסעיף א, בחצי הימני של העורך (ViewColumn.Beside)
  const first = entries.find(e => e.method === lenses[0].method);
  await ctx.web('sol', submit.solutionHtml(first), null, [496, 781], [{ wait: 300 }, { shot: 'v1' }]);

  // 5. ההודעות של VS Code (בפינה למטה)
  const T = [['t1', { progress: true, title: 'שליחת תרגיל', text: 'שולח את התרגיל… (בדרך כלל עד דקה)' }],
             ['t2', { progress: true, title: 'שליחת תרגיל', text: 'בודק שהקוד מתקמפל…' }],
             ['t3', { progress: true, title: 'שליחת תרגיל', text: 'בודק שההצעות לפתרון נותנות את התוצאה הנכונה…' }],
             ['t4', { text: submit.doneMessage(done, submit.labelAt(code.join('\n'), done.line)).text }]];
  for (const [n, o] of T) await ctx.web(n, toast(o), null, [448, o.progress ? 86 : 78], [{ wait: 150 }, { shot: n }]);
  fs.rmSync(tmp, { recursive: true, force: true });
  return { code, cursor: done.line + 1, folder: done.folder, file: path.basename(done.focus), lenses, mainLine, expectLine, status: done.solutionStatus };
};
