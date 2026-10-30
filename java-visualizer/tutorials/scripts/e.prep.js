// הדרכה ה: שליחת תרגיל והצעה לפתרון — החלון האמיתי "שליחת תרגיל" (Ctrl+V של דף התרגיל), הקובץ שנבנה (אותו מנוע — SubmitCli),
// והחלון האמיתי "הצעה לפתרון". השרת — מדומה (tools/test/submit/mock-worker.js) עם תרגיל המחסניות.
const fs = require('fs'), path = require('path'), cp = require('child_process');
module.exports = async ctx => {
  const ROOT = path.join(__dirname, '..', '..'), test = path.join(ROOT, 'tools', 'test');
  const page = path.join(__dirname, '..', 'assets', 'exam-page.png');
  const mock = cp.spawn('node', [path.join(test, 'submit', 'mock-worker.js'), '8790', path.join(test, 'ai', '1-stack.json')]);
  await new Promise(r => setTimeout(r, 800));
  process.env.DSVIZ_WORKER_URL = 'http://127.0.0.1:8790';
  try {
    ctx.dlg('submit', 'submit', 'x', ['shot e1', 'pasteimg ' + page, 'shot e2', 'text 0 רק שאלה 1', 'shot e3', 'click שליחה ויצירת התרגיל', 'wait 2500']);
    // הקבצים — אותו מנוע כמו בתוך Eclipse (ExercisePipeline)
    const sub = path.join(ctx.W, 'sub');
    fs.rmSync(sub, { recursive: true, force: true });
    fs.mkdirSync(path.join(sub, 'src'), { recursive: true });
    const man = { lang: 'java', out: path.join(sub, 'src'), store: path.join(sub, 'solutions.json'), lib: path.join(ROOT, 'release', 'dsviz.jar'),
      note: 'רק שאלה 1', items: [{ path: page, name: 'תמונה מודבקת 1' }] };
    fs.writeFileSync(path.join(sub, 'm.json'), JSON.stringify(man));
    const env = Object.assign({}, process.env); delete env.JAVA_TOOL_OPTIONS;
    const outp = cp.execFileSync('java', ['-cp', path.join(ROOT, 'release', 'dsviz.jar'), 'il.lamerhav.dsviz.SubmitCli', 'submit', '--manifest', path.join(sub, 'm.json')], { env, encoding: 'utf8' });
    const done = JSON.parse(outp.trim().split('\n').pop());
    if (!done.done) throw new Error(outp);
    const code = fs.readFileSync(done.focus, 'utf8').replace(/\r/g, '').replace(/\n$/, '').split('\n');
    const header = re => code.findIndex(l => re.test(l)) + 1;
    // הדף "המצולם" (תמונה) — כמו חלון שמראים בסרטון
    const im = require('child_process').execFileSync('python3', ['-c', 'from PIL import Image; import sys; print(*Image.open(sys.argv[1]).size)', page], { encoding: 'utf8' }).trim().split(' ').map(Number);
    ctx.assets['page/p'] = { src: 'assets/exam-page.png', w: im[0], h: im[1], controls: [] };
    ctx.dlg('sol', 'solution', path.join(sub, 'solutions.json') + '#0', ['shot s1', 'wait 300']);
    return { code, line: done.line + 1, file: path.basename(done.focus), folder: done.folder, status: done.solutionStatus,
      hA: header(/static int countEven\(/), hB: header(/static void removeAll\(/), main: header(/static void main\(/),
      exp: code.findIndex(l => l.includes('אם פתרת נכון')) + 1 };
  } finally {
    mock.kill();
  }
};
