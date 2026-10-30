// node tools/build-help.js — יוצר את דפי "הדרכה לשימוש" לכל סביבה, מהדף של vscode-extension/help.js:
//   help/ (Eclipse), help/vscode/ (VS Code), help/vs/ (Visual Studio — C#): index.html, player.html ו-tutorials.json (משכי הסרטונים).
// הסרטונים עצמם נוצרים ב-tutorials/make.js (‎<תיקייה>/<id>.mp4 + ‎<id>.jpg; ‎--ide vscode / vs).
const fs = require('fs'), path = require('path'), cp = require('child_process');
const root = path.join(__dirname, '..');
const help = require(path.join(root, 'vscode-extension', 'help.js'));
for (const sub of ['', 'vscode', 'vs']) {
    const dir = path.join(root, 'help', sub);
    if (!fs.existsSync(dir)) continue;
    const durations = {};
    for (const t of help.TUTORIALS) {
        const f = path.join(dir, t.id + '.mp4');
        if (!fs.existsSync(f)) continue;
        const s = parseFloat(cp.execFileSync('ffprobe', ['-v', 'error', '-show_entries', 'format=duration', '-of', 'csv=p=0', f], { encoding: 'utf8' }));
        durations[t.id] = Math.floor(s / 60) + ':' + String(Math.round(s % 60)).padStart(2, '0');
    }
    fs.writeFileSync(path.join(dir, 'tutorials.json'), JSON.stringify(durations, null, 2) + '\n');
    fs.writeFileSync(path.join(dir, 'index.html'), help.page({ urlOf: f => f, durations }));
    fs.writeFileSync(path.join(dir, 'player.html'), help.playerPage({ urlOf: f => f, remote: help.REMOTE_HELP + (sub ? sub + '/' : '') }));
    console.log(path.join('help', sub, 'index.html'), JSON.stringify(durations));
}
