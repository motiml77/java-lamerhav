// הורשה ופולימורפיזם בהרצה הוויזואלית — ב-VS Code האמיתי (Linux, Xvfb), כמו תלמיד: תיקייה עם מחלקה בכל קובץ
// (Pets.java של tools/test/objects/poly/pets, מפוצל), Pets.java פתוח, לחיצת עכבר על "הרצה ויזואלית" בסרגל של העורך,
// ובחלון ההרצה — לחיצות על "הבא" עד Animal pet = d; (פסים: "לא נראה דרך pet") ועד pet.say() (החלונית "מותר? / מה רץ?").
//   POLY=1 bash tools/test/vscode-real/run.sh
const { _electron } = require('playwright');
const { execSync, execFileSync } = require('child_process');
const fs = require('fs');
const D = process.env.VSC_DIR, R = require('path').resolve(__dirname, '../../..'), DISP = process.env.DISPLAY;
const CODE = D + '/dl/VSCode-linux-x64/code', E = D + '/ext', U = process.env.VSC_UD || '/tmp/vcud', WS = D + '/wsp', SHOTS = D + '/shots-poly';
const VIEWER = 'הרצה ויזואלית — מבני נתונים';
let ok = 0, fail = 0;
const check = (c, t, extra) => { c ? ok++ : fail++; console.log((c ? 'PASS ' : 'FAIL ') + t + (extra ? ' — ' + extra : '')); };
const keys = (...a) => { try { return execFileSync('python3', [__dirname + '/xkeys.py', DISP, VIEWER, ...a.map(String)]).toString(); } catch (e) { return ''; } };
const shotRoot = n => { try { execSync(`import -display ${DISP} -window root ${SHOTS}/${n}.png`); } catch (e) {} };
const sleep = ms => new Promise(r => setTimeout(r, ms));

(async () => {
    fs.rmSync(SHOTS, { recursive: true, force: true }); fs.mkdirSync(SHOTS, { recursive: true });
    const env = Object.assign({}, process.env); delete env.JAVA_TOOL_OPTIONS;
    const app = await _electron.launch({ executablePath: CODE, env, timeout: 90000,
        args: ['--no-sandbox', '--disable-gpu', '--extensions-dir', E, '--user-data-dir', U, '--skip-release-notes', '--disable-workspace-trust', WS, WS + '/src/Pets.java'] });
    const win = await app.firstWindow();
    await win.waitForSelector('.monaco-workbench', { timeout: 90000 });
    await win.waitForSelector('.tab[aria-label*="Pets.java"], .tab:has-text("Pets.java")', { timeout: 60000 });
    await sleep(8000);   // ההרחבה נטענת (onLanguage:java)
    const btn = await win.$('.editor-actions a.action-label[aria-label*="הרצה ויזואלית"]');
    check(!!btn, 'הכפתור "הרצה ויזואלית" בסרגל של VS Code');
    { const v = await (await win.$('.view-lines')).boundingBox(); await win.mouse.click(v.x + 60, v.y + 30); }
    await sleep(800);
    await win.screenshot({ path: SHOTS + '/1-vscode.png' });
    if (btn) { const b = await btn.boundingBox(); await win.mouse.click(b.x + b.width / 2, b.y + b.height / 2); }
    let opened = false;
    for (let i = 0; i < 120 && !opened; i++) { opened = /^WIN/.test(keys('Shift_L', 0)); if (!opened) await sleep(1000); }
    check(opened, 'הלחיצה פתחה את חלון ההרצה הוויזואלית');
    if (opened) {
        await sleep(6000);
        keys('CLICK:-135,26', 31, 200); await sleep(2000); shotRoot('2-pet-equals-d');
        keys('CLICK:-135,26', 1, 200); await sleep(2000); shotRoot('3-pet-say');
    }
    try { execSync('pkill -f "LineByLine Pets"'); } catch (e) {}
    const bin = fs.existsSync(WS + '/bin') ? fs.readdirSync(WS + '/bin').filter(f => f.endsWith('.class')).sort() : [];
    check(['Animal.class', 'Cat.class', 'Dog.class', 'Pets.class'].every(c => bin.includes(c)), 'VS Code קימפל את כל הקבצים של הפרויקט', bin.join(' '));
    await app.close();
    console.log(`SUMMARY ${ok} PASS / ${fail} FAIL`);
    process.exit(fail ? 1 : 0);
})().catch(e => { console.log('FAIL שגיאה: ' + e.message); try { execSync('pkill -f "LineByLine Pets"'); } catch (x) {} process.exit(1); });
