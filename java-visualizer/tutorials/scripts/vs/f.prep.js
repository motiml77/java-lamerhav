// הדרכה ו (Visual Studio): עבודה ב-Visual Studio מההתחלה — פותחים את תיקיית התרגילים, סרגל הכלים של DSViz, "תרגיל חדש",
// "מאגר שאלות" והרצה ויזואלית. החלונות — מהכלים האמיתיים (CsTool new, BankVs bank), וההרצה — dsviz-cs ← TraceViewer.
const fs = require('fs'), path = require('path');
const { freshRoot, ROOT } = require('./common.prep');
module.exports = async ctx => {
  const root = freshRoot();
  // 0. "פרויקט חדש" ב-Visual Studio: הדף "Create a new project" ו-"Configure your new project" (מצוירים — הם של Visual Studio עצמו)
  const css = `body{margin:0;font-family:"Segoe UI",Arial,sans-serif;background:#fff;color:#1e1e1e;font-size:14px}
    .h{padding:22px 32px 8px;font-size:26px;font-weight:600}.sub{padding:0 32px;color:#555}
    .bar{display:flex;gap:10px;padding:14px 32px}.in{flex:1;border:1px solid #8a8a8a;height:30px;line-height:30px;padding:0 10px;background:#fff}
    .f{border:1px solid #bbb;height:30px;line-height:30px;padding:0 12px;min-width:90px;text-align:center;color:#444}
    .row{display:flex;gap:14px;align-items:center;margin:0 32px;padding:12px 14px;border:1px solid transparent;direction:rtl;text-align:right}
    .row.sel{background:#cfe3ff;border-color:#7fb0f5}.ic{width:44px;height:44px;background:#865fc5;color:#fff;font-weight:700;font-size:18px;display:flex;align-items:center;justify-content:center;border-radius:4px}
    .nm{font-size:16px;font-weight:600}.ds{color:#555;font-size:13px;margin-top:3px}.tags{display:flex;gap:6px;margin-top:6px}.tag{background:#e7e7e7;padding:2px 8px;font-size:12px;border-radius:2px}
    .foot{position:absolute;right:32px;bottom:22px;display:flex;gap:10px}.btn{min-width:96px;height:32px;line-height:32px;text-align:center;background:#007acc;color:#fff;border:1px solid #007acc}
    .btn.g{background:#fff;color:#333;border-color:#999}.lab{padding:0 32px;margin-top:14px;color:#333}.val{margin:4px 32px 0;border:1px solid #8a8a8a;height:30px;line-height:30px;padding:0 10px}`;
  const tmpl = 'תרגיל מבני נתונים (DSViz)';
  await ctx.mock('f_open', {
    p0: { w: 1000, h: 620, html: `<style>${css}</style><div class="h">Create a new project</div><div class="sub">Recent project templates</div>
      <div class="bar"><div class="in" data-c="Text|DSViz">DSViz</div><div class="f">C#</div><div class="f">Windows</div><div class="f">Console</div></div>
      <div class="row sel" data-c="Chip|${tmpl}"><div class="ic">C#</div><div><div class="nm">${tmpl}</div><div class="ds">פרויקט קונסול עם הספרייה Unit4.CollectionsLib ותרגיל מחסנית מוכן</div><div class="tags"><span class="tag">C#</span><span class="tag">Windows</span><span class="tag">Console</span></div></div></div>
      <div class="row"><div class="ic" style="background:#9a9a9a">C#</div><div><div class="nm">Console App</div><div class="ds">A project for creating a command-line application that can run on .NET</div></div></div>
      <div class="foot"><div class="btn" data-c="Round|Next">Next</div></div>` },
    p1: { w: 1000, h: 620, html: `<style>${css}</style><div class="h">Configure your new project</div><div class="sub">${tmpl} &nbsp;C# &nbsp;Windows &nbsp;Console</div>
      <div class="lab">Project name</div><div class="val" data-c="Text|Targil1">Targil1</div>
      <div class="lab">Location</div><div class="val">C:\\Users\\Student\\source\\repos</div>
      <div class="lab">Solution name</div><div class="val">Targil1</div>
      <div class="foot"><div class="btn g">Back</div><div class="btn" data-c="Round|Create">Create</div></div>` },
  });
  const tmplCode = fs.readFileSync(path.join(root, 'Exercise', 'Program.cs'), 'utf8').replace(/^﻿/, '').replace(/\r/g, '').split('\n');
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
  return { tmplCode, tmpl, newCode, newName: 'Targil1', bankName, bankFile: bankFiles[0], bankCode, frames, from };
};
