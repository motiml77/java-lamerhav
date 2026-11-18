// בדיקה ב-VS Code האמיתי (1.146, Linux, Xvfb): ההרחבה מותקנת מה-vsix של ההתקנה, לחיצות עכבר אמיתיות על כפתורי הסרגל:
//  1. הכפתורים של DSViz בסרגל של העורך (כולל "מאגר תרגילים")
//  2. בלי כניסה: הלחיצה על "מאגר תרגילים" פותחת את מסך "כניסה למאגר התרגילים"
//  3. אחרי כניסה של מורה (מפתח בדיקה, כמו בבדיקה של Visual Studio): הלחיצה פותחת את חלון המאגר "מאגר תרגילים — VS Code"
const { _electron } = require('playwright');
const { execSync, execFileSync } = require('child_process');
const fs = require('fs'), path = require('path');
// הכנה (tools/test/vscode-real/run.sh): VS Code ב-$VSC_DIR/dl, ההרחבה מותקנת ב-$VSC_DIR/ext, תיקיית עבודה $VSC_DIR/ws, SeedSession מקומפל ב-$VSC_DIR/seed
const D = process.env.VSC_DIR, R = require('path').resolve(__dirname, '../../..'), DISP = process.env.DISPLAY;
const CODE = D + '/dl/VSCode-linux-x64/code', E = D + '/ext', U = process.env.VSC_UD || '/tmp/vcud', WS = D + '/ws', SHOTS = D + '/shots';
const BANK = R + '/build/bank-parts/dsviz-bank-vs.jar', SEEDCP = D + '/seed:' + BANK + ':' + R + '/release/dsviz.jar';
let ok = 0, fail = 0;
const check = (c, t, extra) => { c ? ok++ : fail++; console.log((c ? 'PASS ' : 'FAIL ') + t + (extra ? ' — ' + extra : '')); };
const wins = () => execFileSync('python3', [__dirname + '/xwins.py', DISP]).toString().split('\n').filter(Boolean);
const shotRoot = n => { try { execSync(`import -display ${DISP} -window root ${SHOTS}/${n}.png`); } catch (e) {} };
const seed = (role, email) => execSync(`java -Dfile.encoding=UTF-8 -cp "${SEEDCP}" il.lamerhav.dsviz.SeedSession ${role} ${email} ${D}/seed-key.txt 2>&1`).toString().split('\n').filter(l => !/JAVA_TOOL/.test(l)).join(' ');
const sleep = ms => new Promise(r => setTimeout(r, ms));
async function waitWin(re, ms) { const t = Date.now(); while (Date.now() - t < ms) { const w = wins().find(x => re.test(x)); if (w) return w; await sleep(500); } return null; }
const killTool = () => { try { execSync("pkill -f il.lamerhav.dsviz.BankVs"); } catch (e) {} };

(async () => {
    fs.rmSync(SHOTS, { recursive: true, force: true }); fs.mkdirSync(SHOTS, { recursive: true });
    const pk = seed('key', '-').match(/PASSKEY (\S+)/);
    check(!!pk, 'מפתח בדיקה לכניסה (SeedSession)');
    console.log('INFO ' + seed('none', '-'));
    // שרת מדומה במקום השרת האמיתי (כמו בבדיקה של Visual Studio): הכניסה של הבדיקה חתומה במפתח בדיקה, והשרת האמיתי דוחה אותה בצדק
    const mock = require('child_process').spawn('node', [R + '/tools/test/submit/mock-worker.js', '8797', R + '/tools/test/submit/cs-stack.json', D + '/mock.log'], { stdio: 'ignore' });
    await sleep(1500);
    const env = Object.assign({}, process.env, { DSVIZ_PASS_KEY: pk && pk[1], DSVIZ_WORKER_URL: 'http://127.0.0.1:8797' });
    delete env.JAVA_TOOL_OPTIONS;
    const app = await _electron.launch({ executablePath: CODE, env, timeout: 90000,
        args: ['--no-sandbox', '--disable-gpu', '--extensions-dir', E, '--user-data-dir', U, '--skip-release-notes', '--disable-workspace-trust', WS, WS + '/src/Main.java'] });
    const win = await app.firstWindow();
    await win.waitForSelector('.monaco-workbench', { timeout: 90000 });
    await win.waitForSelector('.tab[aria-label*="Main.java"], .tab:has-text("Main.java")', { timeout: 60000 });
    await sleep(8000);   // ההרחבה נטענת (onLanguage:java)
    const labels = await win.$$eval('.editor-actions .action-item a.action-label', as => as.map(a => a.getAttribute('aria-label') || a.title).filter(Boolean));
    console.log('INFO כפתורים בסרגל העורך: ' + labels.join(' | '));
    for (const want of ['שליחת תרגיל', 'מאגר תרגילים', 'הרצה ויזואלית'])
        check(labels.some(l => l.includes(want)), `הכפתור "${want}" בסרגל של VS Code`);
    await win.screenshot({ path: SHOTS + '/1-toolbar.png' });
    const btn = await win.$('.editor-actions a.action-label[aria-label*="מאגר תרגילים"]');
    const clickBank = async () => { const b = await btn.boundingBox(); await win.mouse.click(b.x + b.width / 2, b.y + b.height / 2); };

    // 2. בלי כניסה — מסך הכניסה (קודם לחיצה בתוך העורך: החלון מקבל פוקוס, כמו אצל תלמיד)
    { const v = await (await win.$('.view-lines')).boundingBox(); await win.mouse.click(v.x + 60, v.y + 30); }
    await sleep(1000);
    await clickBank();
    const gate = await waitWin(/כניסה למאגר התרגילים/, 45000);
    await sleep(2500); shotRoot('2-gate');
    try { const pid = execSync("pgrep -f il.lamerhav.dsviz.BankVs | head -1").toString().trim(); const ev = fs.readFileSync('/proc/' + pid + '/environ', 'utf8').split('\0');
          console.log('INFO הכלי (pid ' + pid + '): DSVIZ_PASS_KEY ' + (ev.some(x => x.startsWith('DSVIZ_PASS_KEY=') && x.length > 20) ? 'עבר' : 'לא עבר') + ', DSVIZ_ARGS ' + (ev.some(x => x.startsWith('DSVIZ_ARGS=')) ? 'עבר' : 'לא עבר')); } catch (e) { console.log('INFO אין תהליך של הכלי'); }
    check(!!gate, 'בלי כניסה: הלחיצה על "מאגר תרגילים" פתחה את מסך "כניסה למאגר התרגילים"', gate || ('חלונות: ' + wins().join(' ; ')));
    killTool(); await sleep(1500);

    // 3. אחרי כניסה של מורה — חלון המאגר
    console.log('INFO ' + seed('teacher', 'teacher.test@example.com'));
    await clickBank();
    const bank = await waitWin(/מאגר תרגילים — VS Code/, 60000);
    await sleep(3000); shotRoot('3-bank');
    check(!!bank, 'אחרי כניסה: נפתח חלון המאגר "מאגר תרגילים — VS Code"', bank || ('חלונות: ' + wins().join(' ; ')));
    check(!wins().some(w => /כניסה למאגר/.test(w)), 'אחרי כניסה: בלי מסך כניסה נוסף');
    try { console.log('INFO יומן הגשר: ' + fs.readFileSync(require('os').tmpdir() + '/dsviz-bank-vscode.log', 'utf8').split('\n').slice(-6).join(' / ')); } catch (e) {}

    killTool(); seed('none', '-');
    await app.close();
    mock.kill();
    console.log('INFO בקשות לשרת המדומה: ' + (fs.existsSync(D + '/mock.log') ? fs.readFileSync(D + '/mock.log', 'utf8').trim().split('\n').join(' ; ') : 'אין'));
    console.log(`SUMMARY ${ok} PASS / ${fail} FAIL`);
    process.exit(fail ? 1 : 0);
})().catch(e => { console.log('FAIL שגיאה: ' + e.message); killTool(); try { seed('none', '-'); } catch (x) {} process.exit(1); });
