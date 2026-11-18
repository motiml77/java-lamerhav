// "מאגר תרגילים" ב-VS Code האמיתי ב-Windows, אחרי DSViz-Setup.exe רגיל (vscode-win.ps1 מתקין ומפעיל):
//   node vscode-win.js <phase> <out> <playwright-core>      phase = before (רק לשחזר את התקלה: מה קורה בלחיצה) / after (הבדיקה)
// לחיצת עכבר אמיתית על הכפתור בסרגל של העורך, ואז ב-Windows עצמו: האם חלון הכלי באמת גלוי (IsWindowVisible) ומקדימה
// (WindowFromPoint באמצע שלו = הכלי, לא VS Code). בתוך הכלי פועל ToolbarAgent (JDK_JAVA_OPTIONS) — לחיצה על "כניסה עם Google",
// ואז בודקים שהדפדפן נפתח בדף הכניסה. ובסוף: אחרי כניסה של מורה (מפתח בדיקה + שרת מדומה) נפתח חלון המאגר עצמו.
const [phase, OUT, PW] = process.argv.slice(2);
const { _electron } = require(PW);
const { execFileSync, spawn } = require('child_process');
const fs = require('fs'), path = require('path'), os = require('os');
const HERE = __dirname, R = path.resolve(HERE, '../../..');
const CODE = path.join(process.env.LOCALAPPDATA, 'Programs', 'Microsoft VS Code', 'Code.exe');
const DSVIZ = path.join(process.env.LOCALAPPDATA, 'DSViz');
const CTL = 'C:\\dsagent', WS = path.join(os.homedir(), 'Documents', 'BankVsCode');
const SEEDCP = [path.join(CTL, 'seed'), path.join(DSVIZ, 'bank', 'dsviz-bank-vs.jar'), path.join(DSVIZ, 'dsviz.jar')].join(';');
let ok = 0, fail = 0, n = 0;
const lines = [];
const say = s => { console.log(s); lines.push(s); };
const check = (c, id, t, extra) => { c ? ok++ : fail++; say((c ? 'PASS ' : 'FAIL ') + id + ' — ' + t + (extra ? ' — ' + extra : '')); };
const info = s => say('  ..   ' + s);
const sleep = ms => new Promise(r => setTimeout(r, ms));
const wt = (...a) => { try { return execFileSync('powershell.exe', ['-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', path.join(HERE, 'wintool.ps1'), ...a.map(String)], { encoding: 'utf8' }).trim(); } catch (e) { return 'ERR ' + e.message; } };
const shot = name => { n++; const f = path.join(OUT, String(n).padStart(2, '0') + '-' + phase + '-' + name + '.png'); wt('shot', f); info('צילום: ' + path.basename(f)); };
const windows = () => wt('windows').split(/\r?\n/).filter(Boolean).map(l => { const [pid, proc, vis, rect, ...t] = l.split('\t'); return { pid: +pid, proc, visible: vis === 'True', rect, title: t.join('\t') }; });
const javaWins = () => windows().filter(w => /^javaw?$/i.test(w.proc));
const killJava = () => { try { execFileSync('taskkill', ['/F', '/IM', 'javaw.exe'], { stdio: 'ignore' }); } catch (e) { /* אין */ } };
const seed = (role, email) => execFileSync(path.join(DSVIZ, 'jdk', 'bin', 'java.exe'), ['-Dfile.encoding=UTF-8', '-cp', SEEDCP, 'il.lamerhav.dsviz.SeedSession', role, email, path.join(CTL, 'seed-key.txt')], { encoding: 'utf8', env: Object.assign({}, process.env, { JDK_JAVA_OPTIONS: '' }) }).trim();
function script(lines) {
    for (const f of fs.readdirSync(CTL)) if (/^(mark|ack)-|\.done\.txt$|\.error\.txt$|claimed$/.test(f)) fs.rmSync(path.join(CTL, f), { force: true });
    fs.writeFileSync(path.join(CTL, 'bank.script.txt'), lines.join('\r\n'), 'utf8');
}
/** מחכה ל-marks של ה-agent: צילום + ack לכל אחד; מחזיר את הסטטוס (done / error / timeout) ואת השמות שנראו */
async function marks(sec, onMark) {
    const seen = new Set(), t0 = Date.now();
    while (Date.now() - t0 < sec * 1000) {
        for (const f of fs.readdirSync(CTL)) {
            const m = /^mark-(.+)\.txt$/.exec(f);
            if (!m || seen.has(m[1])) continue;
            seen.add(m[1]);
            await sleep(700);
            if (onMark) await onMark(m[1]);
            shot(m[1]);
            fs.writeFileSync(path.join(CTL, 'ack-' + m[1] + '.txt'), 'ok');
        }
        if (fs.existsSync(path.join(CTL, 'bank.done.txt'))) return { st: 'done', seen };
        if (fs.existsSync(path.join(CTL, 'bank.error.txt'))) return { st: 'error: ' + fs.readFileSync(path.join(CTL, 'bank.error.txt'), 'utf8').split('\n')[0], seen };
        await sleep(500);
    }
    return { st: 'timeout', seen };
}
async function launch(extraEnv) {
    const env = Object.assign({}, process.env, { JDK_JAVA_OPTIONS: '-javaagent:' + CTL + '\\agent.jar=' + CTL }, extraEnv || {});
    const app = await _electron.launch({ executablePath: CODE, env, timeout: 120000, args: [WS, path.join(WS, 'src', 'Main.java'), '--disable-workspace-trust', '--skip-release-notes'] });
    const win = await app.firstWindow();
    await win.waitForSelector('.monaco-workbench', { timeout: 120000 });
    await win.waitForSelector('.tab:has-text("Main.java")', { timeout: 90000 });
    await sleep(10000);   // ההרחבה נטענת (onLanguage:java)
    info(wt('activate', 'Visual Studio Code'));
    await sleep(1500);
    return { app, win };
}
/** לחיצה אמיתית (Windows) על "מאגר תרגילים" בסרגל של העורך: מיקום הכפתור בחלון + מיקום החלון על המסך */
async function clickBank(win) {
    const el = await win.waitForSelector('.editor-actions a.action-label[aria-label*="מאגר תרגילים"]', { timeout: 30000 });
    const b = await el.boundingBox();
    const o = await win.evaluate(() => ({ x: window.screenX, y: window.screenY, dpr: window.devicePixelRatio }));
    const x = Math.round((o.x + b.x + b.width / 2) * o.dpr), y = Math.round((o.y + b.y + b.height / 2) * o.dpr);
    info('כפתור "מאגר תרגילים" על המסך: ' + x + ',' + y + ' (dpr ' + o.dpr + ') — ' + wt('click', x, y));
}
async function waitJavaWindow(re, sec) {
    const t0 = Date.now();
    while (Date.now() - t0 < sec * 1000) {
        const w = javaWins().find(x => re.test(x.title));
        if (w) return w;
        await sleep(700);
    }
    return null;
}

(async () => {
    fs.mkdirSync(OUT, { recursive: true });
    fs.rmSync(WS, { recursive: true, force: true });
    fs.mkdirSync(path.join(WS, 'src'), { recursive: true });
    fs.writeFileSync(path.join(WS, 'src', 'Main.java'), 'public class Main {\r\n    public static void main(String[] args) {\r\n        System.out.println("hi");\r\n    }\r\n}\r\n');
    killJava();
    info('כניסה שמורה לפני: ' + seed('none', '-'));

    // ---- 1. בלי כניסה: הלחיצה פותחת את מסך הכניסה — גלוי ומקדימה
    let { app, win } = await launch();
    const labels = await win.$$eval('.editor-actions .action-item a.action-label', as => as.map(a => a.getAttribute('aria-label') || a.title).filter(Boolean));
    info('הכפתורים בסרגל: ' + labels.join(' | '));
    check(labels.some(l => l.includes('מאגר תרגילים')), phase + '-button', 'הכפתור "מאגר תרגילים" בסרגל של VS Code');
    shot('vscode');
    script(phase === 'before' ? ['waitwin כניסה למאגר', 'wait 2500', 'mark gate']
        : ['waitwin כניסה למאגר', 'wait 2500', 'mark gate', 'target כניסה עם Google', 'clicktarget כניסה עם Google', 'wait 5000', 'mark google-1', 'wait 12000', 'mark google-2']);
    await clickBank(win);
    let gateFront = '', browser = '';
    const r1 = await marks(phase === 'before' ? 90 : 150, async m => {
        if (m === 'gate') { gateFront = wt('front', 'כניסה למאגר'); info('מסך הכניסה ב-Windows: ' + gateFront); info('חלונות Java: ' + JSON.stringify(javaWins())); }
        if (m === 'google-2') browser = windows().filter(w => w.visible && /msedge|chrome|firefox/i.test(w.proc)).map(w => w.proc + ': ' + w.title).join(' ; ');
    });
    info('ה-agent בכלי: ' + r1.st + ' (' + [...r1.seen].join(', ') + ')');
    const jw = javaWins();
    const gw = jw.find(w => /כניסה למאגר/.test(w.title));
    const m = /^FOUND (\d+) center-pid=(\d+) foreground-pid=(\d+)/.exec(gateFront);
    if (phase === 'before') {
        say('REPRO מה קרה בלחיצה (ההתקנה הקודמת): ' + (gw ? 'מסך הכניסה קיים, גלוי=' + gw.visible + (m ? ', באמצע שלו=' + (m[2] === m[1] ? 'הכלי' : 'תהליך ' + m[2]) : '') : 'אין חלון של הכלי בכלל') + ' · חלונות Java: ' + JSON.stringify(jw));
    } else {
        check(!!gw && gw.visible, 'after-gate-visible', 'הלחיצה פתחה את מסך "כניסה למאגר התרגילים" וב-Windows הוא באמת גלוי (IsWindowVisible)', JSON.stringify(jw));
        check(!!m && m[2] === m[1], 'after-gate-front', 'מסך הכניסה מקדימה — באמצע שלו רואים אותו ולא את VS Code', gateFront);
        check(/Google|Sign in|accounts|כניסה|signin/i.test(browser), 'after-google', 'לחיצה על "כניסה עם Google" (מורה) פתחה את הדפדפן בדף הכניסה של Google', browser || JSON.stringify(windows().filter(w => w.visible).map(w => w.proc + ': ' + w.title)));
    }
    killJava();
    try { execFileSync('taskkill', ['/F', '/IM', 'msedge.exe'], { stdio: 'ignore' }); } catch (e) { /* אין */ }
    await app.close();

    // ---- 2. אחרי כניסה של מורה (מפתח בדיקה + שרת מדומה, כמו בבדיקה של Visual Studio): חלון המאגר — גלוי ומקדימה
    if (phase === 'after') {
        const pk = (seed('key', '-').match(/PASSKEY (\S+)/) || [])[1];
        const mock = spawn('node', [path.join(R, 'tools', 'test', 'submit', 'mock-worker.js'), '8796', path.join(R, 'tools', 'test', 'submit', 'cs-stack.json'), path.join(OUT, 'mock-worker.log')], { stdio: 'ignore' });
        await sleep(1500);
        info('כניסה של מורה: ' + seed('teacher', 'teacher.test@example.com'));
        ({ app, win } = await launch({ DSVIZ_PASS_KEY: pk, DSVIZ_WORKER_URL: 'http://127.0.0.1:8796' }));
        script(['waitwin מאגר תרגילים', 'wait 3000', 'shot window', 'mark bank', 'bankselect stack-sum-size', 'wait 1500', 'mark selected',
            'click צור תרגיל', 'waitwin השאלה', 'wait 6000', 'shot float', 'mark float', "click הכן סעיף ב'", 'wait 9000', 'mark prepared']);
        await clickBank(win);
        let bankFront = '', bankVisible = false, floatFront = '', tabsAfterCreate = [], editorAfterPrepare = '';
        const tabs = () => win.$$eval('.tab', ts => ts.map(t => t.getAttribute('aria-label') || t.textContent).filter(Boolean)).catch(() => []);
        const r2 = await marks(300, async mk => {
            if (mk === 'bank') { bankFront = wt('front', 'מאגר תרגילים — VS Code'); bankVisible = javaWins().some(w => w.visible && /מאגר תרגילים — VS Code/.test(w.title)); }
            if (mk === 'float') { floatFront = wt('front', 'השאלה'); await sleep(2000); tabsAfterCreate = await tabs(); }
            if (mk === 'prepared') { await sleep(3000); editorAfterPrepare = await win.$eval('.editor-instance .view-lines', e => e.innerText).catch(() => ''); }
        });
        info('ה-agent בכלי: ' + r2.st + ' (' + [...r2.seen].join(', ') + ')');
        const m2 = /^FOUND (\d+) center-pid=(\d+)/.exec(bankFront);
        check(bankVisible, 'after-bank-visible', 'אחרי כניסה: נפתח חלון המאגר "מאגר תרגילים — VS Code" וב-Windows הוא גלוי', JSON.stringify(javaWins()));
        check(!!m2 && m2[2] === m2[1], 'after-bank-front', 'חלון המאגר מקדימה', bankFront);
        check(!javaWins().some(w => w.visible && /כניסה למאגר/.test(w.title)), 'after-no-gate', 'אחרי כניסה — בלי מסך כניסה נוסף');
        // השאלה מהמאגר: חלון "השאלה" גלוי, הקובץ נפתח ב-VS Code, ו"הכן סעיף ב'" מכניס את סעיף ב לקוד (בקובץ ובעורך)
        const winTexts = n => { try { return fs.readFileSync(path.join(CTL, 'shots-bank', n + '.json'), 'utf8'); } catch (e) { return ''; } };
        const bankJson = winTexts('window'), floatJson = winTexts('float');
        check(/התנתקות/.test(bankJson) && /ההתקדמות שלי/.test(bankJson), 'after-bank-teacher', 'חלון המאגר של מורה מחובר: "ההתקדמות שלי" ו"התנתקות" (הנושאים עצמם — בצילום)', bankJson.slice(0, 300));
        const mf = /^FOUND (\d+) center-pid=(\d+)/.exec(floatFront);
        check(!!mf && mf[2] === mf[1], 'after-float-front', '"צור תרגיל" — החלון הצף "השאלה" נפתח, גלוי ומקדימה', floatFront);
        check(/sumStack|סכום|מחסנית/.test(floatJson), 'after-float-text', 'בחלון "השאלה" מוצג הנוסח של השאלה', floatJson.slice(0, 300));
        const javaFiles = [];
        const walk = d => { for (const f of fs.readdirSync(d, { withFileTypes: true })) { const p = path.join(d, f.name); if (f.isDirectory()) walk(p); else if (f.name.endsWith('.java')) javaFiles.push(p); } };
        walk(path.join(WS, 'src'));
        const ex = javaFiles.find(f => /sumStack/.test(fs.readFileSync(f, 'utf8')));
        info('קבצי התרגיל: ' + javaFiles.map(f => path.relative(WS, f)).join(', '));
        check(!!ex, 'after-exercise-file', 'נוצר קובץ התרגיל עם הפעולה של סעיף א (sumStack)', javaFiles.join(', '));
        check(!!ex && tabsAfterCreate.some(t => t.includes(path.basename(ex))), 'after-opened-in-vscode', 'הקובץ של התרגיל נפתח ב-VS Code', JSON.stringify(tabsAfterCreate));
        if (ex) {
            const t = fs.readFileSync(ex, 'utf8'), mi = t.indexOf('static void main'), si = t.indexOf('sizeOf(Stack');
            check(si >= 0 && mi >= 0 && si < mi, 'after-prepare-method', '"הכן סעיף ב\'": הפעולה sizeOf נוספה לפני main', 'sizeOf@' + si + ' main@' + mi);
            check(mi >= 0 && t.indexOf('סעיף ב', mi) > 0, 'after-prepare-main', '"הכן סעיף ב\'": הבדיקות של סעיף ב נוספו ל-main');
            check(!/[\u202A-\u202E]/.test(t), 'after-no-bidi-marks', 'בקוד שנוצר ל-VS Code אין סימוני כיוון נסתרים (VS Code מציג אותם כ-[U+202B] באדום)', (t.match(/.*[\u202A-\u202E].*/g) || []).slice(0, 2).join(' / '));
            check(/sizeOf/.test(editorAfterPrepare), 'after-prepare-editor', 'בעורך של VS Code רואים את סעיף ב (בלי לפתוח מחדש)', editorAfterPrepare.slice(0, 200));
        }
        killJava();
        await app.close();
        mock.kill();
        info('כניסה: ' + seed('none', '-'));
    }
    // ---- 3. הגנה בכלי עצמו: גם כשמפעילים אותו עם "חלון מוסתר" (Start-Process -WindowStyle Hidden = SW_HIDE, כמו ה-spawn הישן) —
    //         מסך הכניסה עדיין גלוי (BankVs.consumeHiddenStart)
    if (phase === 'after') {
        killJava();
        const ps = "Start-Process -FilePath '" + path.join(DSVIZ, 'jdk', 'bin', 'javaw.exe') + "' -WorkingDirectory '" + DSVIZ + "' -WindowStyle Hidden "
            + "-ArgumentList '-cp','\"dsviz.jar;bank\\dsviz-bank-vs.jar\"','il.lamerhav.dsviz.BankVs','bank'";   // כמו הקיצור בתפריט התחל
        try { execFileSync('powershell.exe', ['-NoProfile', '-Command', ps], { env: Object.assign({}, process.env, { JDK_JAVA_OPTIONS: '' }) }); } catch (e) { info('Start-Process: ' + e.message); }
        const hw = await waitJavaWindow(/כניסה למאגר/, 60);
        await sleep(2000);
        shot('hidden-start');
        check(!!hw && javaWins().some(w => w.visible && /כניסה למאגר/.test(w.title)), 'after-hidden-start', 'הכלי שהופעל עם "חלון מוסתר" (SW_HIDE) — מסך הכניסה בכל זאת גלוי', JSON.stringify(javaWins()));
        killJava();
    }
    say('SUMMARY ' + phase + ': ' + ok + ' PASS / ' + fail + ' FAIL');
    fs.writeFileSync(path.join(OUT, 'summary-' + phase + '.txt'), lines.join('\n'), 'utf8');
    process.exit(phase === 'before' ? 0 : (fail ? 1 : 0));
})().catch(e => { say('FAIL ' + phase + '-exception — ' + e.message); killJava(); fs.writeFileSync(path.join(OUT, 'summary-' + phase + '.txt'), lines.join('\n'), 'utf8'); process.exit(phase === 'before' ? 0 : 1); });
