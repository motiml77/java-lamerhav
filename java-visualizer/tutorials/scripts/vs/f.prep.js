// הדרכה ו (Visual Studio): עבודה ב-Visual Studio מההתחלה — פותחים את תיקיית התרגילים, סרגל הכלים של DSViz, "תרגיל חדש",
// "מאגר שאלות" והרצה ויזואלית. החלונות — מהכלים האמיתיים (CsTool new, BankVs bank), וההרצה — dsviz-cs ← TraceViewer.
const fs = require('fs'), path = require('path');
const { freshRoot, ROOT } = require('./common.prep');
module.exports = async ctx => {
  const root = freshRoot();
  const read = f => fs.readFileSync(f, 'utf8').replace(/^﻿/, '').replace(/\r/g, '');
  // 1. תרגיל חדש (מחסנית)
  ctx.swing('f_new', ['new', root, '--preset', 'stack', '--no-open'],
    ['shot n0', 'chip תור', 'shot n1', 'click יצירת התרגיל', 'waitwin תרגיל חדש — C#', 'shot n2']);
  const newCode = read(path.join(root, 'Exercises', 'Targil1', 'Program.cs')).split('\n');
  // 2. מאגר שאלות
  const bankJar = path.join(ROOT, 'build', 'lab-vs', 'dsviz-bank-vs.jar');
  const before = new Set(fs.readdirSync(path.join(root, 'Exercises')));
  ctx.swing('f_bank', ['bank', root],
    ['shot b0', 'click שאלה אקראית', 'wait 600', 'shot b1', 'click צור תרגיל', 'wait 2500', 'shot b2'],
    {}, ['-Ddsviz.access=off', '-Ddsviz.bankProgress=' + path.join(root, '.bank-progress.json')], { cls: 'il.lamerhav.dsviz.BankVs', cp: bankJar });
  const made = fs.readdirSync(path.join(root, 'Exercises')).filter(n => !before.has(n));
  if (!made.length) throw new Error('המאגר לא יצר פרויקט');
  const bankName = made[0];
  const bankFiles = fs.readdirSync(path.join(root, 'Exercises', bankName)).filter(n => n.endsWith('.cs'));
  const bankCode = read(path.join(root, 'Exercises', bankName, bankFiles[0])).split('\n');
  // 3. הרצה ויזואלית של התרגיל שנוצר ב"תרגיל חדש" (מחסנית)
  const frames = ctx.viewerCs('f_run', { 'Program.cs': newCode.join('\n') }, [1, 3, 6, 9, 12, 15, 18], false);
  const from = Math.max(0, newCode.findIndex(l => l.includes('בונים את המבנה')) + 1);
  return { newCode, newName: 'Targil1', bankName, bankFile: bankFiles[0], bankCode, frames, from };
};
