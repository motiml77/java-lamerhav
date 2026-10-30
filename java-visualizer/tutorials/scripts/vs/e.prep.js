// הדרכה ה (Visual Studio): שליחת תרגיל והצעה לפתרון — החלונות האמיתיים (CsTool submit / solution) מול שרת מדומה,
// עם צילום דף הבחינה (assets/exam-page.png). הקוד שמוצג בעורך — הקובץ שהכלי באמת יצר (Exercises\Ex01_Stacks)
const fs = require('fs'), path = require('path'), cp = require('child_process');
const { freshRoot, ROOT } = require('./common.prep');
module.exports = async ctx => {
  const root = freshRoot();
  const home = path.join(ctx.W, 'home');
  fs.rmSync(home, { recursive: true, force: true });
  fs.mkdirSync(home, { recursive: true });
  const page = path.join(__dirname, '..', '..', 'assets', 'exam-page.png');
  const mock = cp.spawn('node', [path.join(ROOT, 'tools', 'test', 'submit', 'mock-worker.js'), '8811', path.join(ROOT, 'tools', 'test', 'submit', 'cs-stack.json')], { stdio: 'ignore' });
  await new Promise(r => setTimeout(r, 800));
  try {
    const env = { DSVIZ_WORKER_URL: 'http://127.0.0.1:8811', DSVIZ_CS: path.join(ROOT, 'csharp', 'DsViz.Runner', 'bin', 'Release', 'net8.0', 'dsviz-cs.dll') };
    ctx.swing('sub', ['submit', root], ['shot e1', `pasteimg ${page}`, 'shot e2', 'text 0 רק שאלה 1', 'shot e3',
      'click שליחה ויצירת התרגיל', 'waitwin שליחת תרגיל — C#', 'shot e4'], env, ['-Duser.home=' + home]);
    ctx.swing('sol', ['solution', root], ['shot l0', 'select 1', 'shot l1', 'select 2', 'shot l2'], {}, ['-Duser.home=' + home]);
  } finally {
    mock.kill();
  }
  const file = path.join(root, 'Exercises', 'Ex01_Stacks', 'StackTargil.cs');
  const code = fs.readFileSync(file, 'utf8').replace(/^﻿/, '').replace(/\r/g, '').split('\n');
  while (code.length && code[code.length - 1] === '') code.pop();
  const marks = code.map((l, i) => l.includes('כאן עונים') ? i + 1 : 0).filter(Boolean);
  const mainLine = code.findIndex(l => l.includes('static void Main')) + 1;
  const expFrom = code.findIndex(l => l.includes('צריך להיות מודפס')) + 1;
  // הדף כ"חלון" (תמונה) — צילום המסך שהתלמיד מדביק
  const sz = cp.execFileSync('python3', ['-c', 'import sys; from PIL import Image; print(*Image.open(sys.argv[1]).size)', page], { encoding: 'utf8' }).trim().split(' ').map(Number);
  ctx.assets['page/p'] = { w: sz[0], h: sz[1], controls: [], src: 'assets/exam-page.png' };
  const files = fs.readdirSync(path.join(root, 'Exercises', 'Ex01_Stacks')).filter(f => f.endsWith('.cs'));
  return { code, marks, mainLine, expFrom, files };
};
