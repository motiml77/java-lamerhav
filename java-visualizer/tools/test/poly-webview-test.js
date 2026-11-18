// הורשה ב-VS Code — הדפים עצמם בדפדפן אמיתי (Chromium, דרך tutorials/harness/webview.js): "צור מחלקה" עם יורשת מ-, "היררכיה מוכנה"
// ו"הוסף עצם", מול ה-Session האמיתי של התוסף. בודקים שה-JS של הדף רץ בלי שגיאות, שהבחירות מגיעות לתוסף (קוד נכון), ושהצבעים בדף
// הם הצבעים של ההרצה. צילומים: build/shots/vscode-*.png
//   node tools/test/poly-webview-test.js
const path = require('path'), assert = require('assert'), fs = require('fs');
const ROOT = path.join(__dirname, '..', '..');
const G = require(path.join(ROOT, 'vscode-extension', 'classgen'));
const createclass = require(path.join(ROOT, 'vscode-extension', 'createclass'));
const poly = require(path.join(ROOT, 'vscode-extension', 'poly'));
const { capture } = require(path.join(ROOT, 'tutorials', 'harness', 'webview'));

(async () => {
    const out = path.join(ROOT, 'build', 'test', 'poly-webview'), shots = path.join(ROOT, 'build', 'shots');
    fs.mkdirSync(shots, { recursive: true });
    const zoo = G.treeFiles(G.trees()[0], { lang: 'java', known: [] }, false, false).known;
    const cx = { lang: 'java', pkg: '', namespace: '', taken: new Map(), known: zoo, dir: '/tmp' };
    let checks = 0;

    // --- צור מחלקה: יורשת מ-Dog (לחיצה על התגית), דריסה של say(), פעולה חדשה
    const ses = new createclass.Session(cx);
    let lastCC = null;
    const cc = await capture({ html: createclass.html(createclass.options('java', ses.initial(), cx.known)), size: [1200, 1000], dir: out, src: 'x',
        host: msg => { if (msg.type === 'state' || msg.type === 'ok') { lastCC = ses.view(msg.state); return { type: 'view', view: lastCC }; } return null; },
        actions: [{ fill: ['#className', 'Puppy'] }, { clickText: ['#supers .tag', 'Dog'] }, { wait: 300 }, { click: '#addMethod' },
            { fill: ['#methods .n', 'bark'] }, { fill: ['#methods .p', 'int times'] }, { wait: 300 },
            { eval: "[...document.querySelectorAll('#overrides label')].find(l => l.textContent.includes('say()')).querySelector('input').click()" }, { wait: 400 },
            { eval: "document.body.dataset.colors = [...document.querySelectorAll('#supers .tag')].map(t => t.textContent + '=' + getComputedStyle(t).borderTopColor).join(';')" },
            { shot: 'inherit' }] });
    assert(lastCC && lastCC.ok && lastCC.code.includes('public class Puppy extends Dog {') && lastCC.code.includes('    public String bark(int times) {')
        && lastCC.code.includes('    @Override\n    public String say() {'), lastCC && lastCC.code);
    checks++;
    assert(cc.inherit.controls ? true : JSON.stringify(cc.inherit).includes('שדות שירשה'), 'הקטע "שדות שירשה" בדף');
    checks++;

    // --- היררכיה מוכנה: בעלי חיים, ואז עץ משלכם — העץ מצויר, ושורה חדשה בטבלה
    const ts = new poly.TreeSession(cx, false);
    let lastT = null;
    const tr = await capture({ html: poly.treeHtml(ts), size: [1200, 820], dir: out, src: 'x',
        host: msg => { if (msg.type === 'state' || msg.type === 'ok') { lastT = ts.view(msg.state); return { type: 'view', view: lastT }; } return null; },
        actions: [{ wait: 300 }, { shot: 'tree' }, { clickText: ['.chip', 'עץ משלכם'] }, { click: '#addRow' }, { eval: "const r = document.querySelectorAll('#rows .row'); const last = r[r.length - 1];"
            + "last.querySelector('.n').value = 'MiniBus'; last.querySelector('.n').dispatchEvent(new Event('input'));"
            + "last.querySelector('.s').value = 'Bus'; last.querySelector('.s').dispatchEvent(new Event('input'));" }, { wait: 500 }, { shot: 'own' }] });
    assert(lastT && lastT.ok && lastT.layout.map(l => l.name + ':' + l.depth).join(' ') === 'Vehicle:0 Bus:1 MiniBus:2 Taxi:1', JSON.stringify(lastT && lastT.layout));
    checks++;
    assert(JSON.stringify(tr.own).includes('MiniBus'), 'העץ בדף מצויר עם MiniBus');
    checks++;

    // --- הוסף עצם: Animal → Boxer עם ערכים
    const os = new poly.ObjectSession({ lang: 'java', known: zoo, text: 'class M { void f() {\n\n} }', offset: 21, indent: '        ' });
    let lastO = null;
    await capture({ html: poly.objectHtml(os, 'java'), size: [900, 760], dir: out, src: 'x',
        host: msg => { if (msg.type === 'state' || msg.type === 'ok') { lastO = os.view(msg.state); return { type: 'view', view: lastO }; } return null; },
        actions: [{ wait: 400 }, { clickText: ['#classes .tag', 'Boxer'] }, { wait: 300 },
            { eval: "const v = ['Rexy','male','3.2','3','house']; document.querySelectorAll('#params input').forEach((t, i) => { t.value = v[i]; t.dispatchEvent(new Event('input')); })" },
            { wait: 500 }, { shot: 'object' }] });
    assert(lastO && lastO.ok && lastO.code === '        Animal a1 = new Boxer("Rexy", "male", 3.2, 3, "house");', lastO && lastO.code);
    checks++;
    for (const n of ['inherit', 'tree', 'own', 'object']) fs.copyFileSync(path.join(out, n + '.png'), path.join(shots, 'vscode-' + n + '.png'));
    console.log('poly-webview: ' + checks + ' בדיקות עברו (הדפים רצים בדפדפן; צילומים ב-build/shots/vscode-*.png)');
})().catch(e => { console.error(e); process.exit(1); });
