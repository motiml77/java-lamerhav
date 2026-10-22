// node tools/build-help.js — יוצר help/index.html (ו-help/tutorials.json עם משכי הסרטונים) מהדף של vscode-extension/help.js.
// הסרטונים עצמם נוצרים ב-tutorials/make.js (help/<id>.mp4 + help/<id>.jpg).
const fs = require('fs'), path = require('path'), cp = require('child_process');
const root = path.join(__dirname, '..'), dir = path.join(root, 'help');
const help = require(path.join(root, 'vscode-extension', 'help.js'));
const durations = {};
for (const t of help.TUTORIALS) {
    const f = path.join(dir, t.id + '.mp4');
    if (!fs.existsSync(f)) continue;
    const s = parseFloat(cp.execFileSync('ffprobe', ['-v', 'error', '-show_entries', 'format=duration', '-of', 'csv=p=0', f], { encoding: 'utf8' }));
    durations[t.id] = Math.floor(s / 60) + ':' + String(Math.round(s % 60)).padStart(2, '0');
}
fs.writeFileSync(path.join(dir, 'tutorials.json'), JSON.stringify(durations, null, 2) + '\n');
fs.writeFileSync(path.join(dir, 'index.html'), help.page({ urlOf: f => f, durations }));
fs.writeFileSync(path.join(dir, 'player.html'), help.playerPage({ urlOf: f => f }));
console.log('help/index.html + player.html', JSON.stringify(durations));
