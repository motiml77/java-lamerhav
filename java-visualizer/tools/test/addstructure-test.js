// בודק את החלון "הוסף מבנה נתונים" של VS Code בדפדפן אמיתי (Chromium): הדף נטען, לוחצים על הכפתורים, מקלידים,
// והתשובות מגיעות מ-Session (אותו קוד שרץ בתוסף). שומר צילומי מסך ל-build/test/addstructure-*.png
const fs = require('fs'), path = require('path'), assert = require('assert');
const ROOT = path.join(__dirname, '..', '..');
const addstructure = require(path.join(ROOT, 'vscode-extension', 'addstructure'));
const java = require(path.join(ROOT, 'vscode-extension', 'templates'));
const csharp = require(path.join(ROOT, 'vscode-extension', 'templates-cs'));
let playwright;
for (const p of ['playwright', '/opt/node-tools/node_modules/playwright']) { try { playwright = require(p); break; } catch (e) { /* הבא */ } }
if (!playwright) { console.log('playwright לא מותקן — מדלגים על בדיקת החלון של VS Code'); process.exit(0); }
const out = process.argv[2] || path.join(ROOT, 'build', 'test');
fs.mkdirSync(out, { recursive: true });

const JAVA_DOC = 'public class A {\n    public static void main(String[] args) {\n        Stack<Integer> stk = new Stack<Integer>();\n        \n    }\n}\n';
const CS_DOC = 'using System;\nclass P {\n    static void Main(string[] args) {\n        var lst = new List<int>();\n        \n    }\n}\n';

async function scenario(browser, name, tpl, doc, lang) {
    const offset = doc.indexOf('        \n') + 8;
    const session = new addstructure.Session({ tpl, text: doc, offset, indent: '        ', preset: 'stack' });
    const page = await browser.newPage({ viewport: { width: 1000, height: 760 } });
    const log = [];
    await page.exposeFunction('hostMsg', async msg => {
        log.push(msg.type);
        if (msg.type === 'ok') { const v = session.view(msg.state); if (v.ok) { log.code = v.code; } return v; }
        if (msg.type === 'state') return session.view(msg.state, msg.reason);
        return null;
    });
    await page.addInitScript(() => {
        window.acquireVsCodeApi = () => ({
            postMessage: async msg => { const v = await window.hostMsg(msg); if (v && msg.type !== 'cancel') window.postMessage({ type: 'view', view: v }, '*'); }
        });
    });
    const body = addstructure.html(addstructure.options(tpl, session.initial()));
    await page.route('http://dsviz.test/dlg', r => r.fulfill({ contentType: 'text/html; charset=utf-8', body }));
    await page.goto('http://dsviz.test/dlg');
    await page.waitForSelector('#ptype:not(:empty)');
    const click = async (sel, text) => { await page.locator(sel, { hasText: text }).first().click(); await page.waitForTimeout(150); };

    // מחסנית + "מחסניות/תורים" בכל תא → קומבינציה, עם 5 מבנים אקראיים
    await click('#inside .chip', 'תורים');
    assert.strictEqual(await page.textContent('#ptype'), tpl === csharp ? 'Stack<Queue<int>>' : 'Stack<Queue<Integer>>');
    await page.check('#random');
    await page.fill('#count', '5');
    await page.waitForTimeout(250);
    const values = await page.inputValue('#values');
    assert.strictEqual(values.split(';').length, 5, values);
    assert((await page.innerHTML('#pv')).includes('<svg'));
    await page.screenshot({ path: path.join(out, `addstructure-${name}-combo.png`) });

    // שם תפוס → שגיאה והכפתור מושבת; שם חוקי → תקין
    const taken = tpl === csharp ? 'lst' : 'stk';
    await page.fill('#name', taken);
    await page.waitForTimeout(300);
    assert((await page.textContent('#err')).includes('כבר יש בקוד'), await page.textContent('#err'));
    assert(await page.isDisabled('#ok'));
    await page.fill('#name', 'class');
    await page.waitForTimeout(300);
    assert((await page.textContent('#err')).includes('שמורה'), await page.textContent('#err'));
    await page.fill('#name', 'myStack');
    await page.waitForTimeout(300);
    assert.strictEqual(await page.textContent('#err'), '');
    assert(!(await page.isDisabled('#ok')));
    // ההצעה (placeholder) מתחשבת במה שכבר קיים
    await page.fill('#name', '');
    await page.waitForTimeout(300);
    const hint = await page.getAttribute('#name', 'placeholder');
    assert(hint && hint !== taken, hint);

    // עץ חיפוש: ערכים ידניים, וציור עץ
    await click('#structs .chip', 'עץ חיפוש');
    assert(await page.isDisabled('#inside .chip:not([data-id=""])') === true);
    await page.fill('#values', '5, 3, 8, 1, 4, 9');
    await page.waitForTimeout(300);
    const svg = await page.innerHTML('#pv');
    assert((svg.match(/<line/g) || []).length >= 5, 'חסרים קווים בעץ');
    await page.screenshot({ path: path.join(out, `addstructure-${name}-bst.png`) });

    // תלמיד בודד: כרטיס
    await click('#structs .chip', 'תלמיד');
    assert((await page.innerHTML('#pv')).includes('Student'));
    await page.screenshot({ path: path.join(out, `addstructure-${name}-student.png`) });

    // אישור: הקוד מגיע לתוסף
    await click('#structs .chip', 'מערך דו-ממדי');
    await page.fill('#values', '1, 2; 3, 4');
    await page.fill('#name', 'grid');
    await page.waitForTimeout(300);
    await page.click('#ok');
    await page.waitForTimeout(200);
    assert(log.includes('ok') && /grid/.test(log.code || ''), log.code);
    assert(/int\[(,)?\]\[?(,)?\]? ?grid|grid = \{/.test(log.code), log.code);
    await page.close();
}

(async () => {
    const browser = await playwright.chromium.launch({ args: ['--no-sandbox'] });
    try {
        await scenario(browser, 'java', java, JAVA_DOC, 'java');
        await scenario(browser, 'cs', csharp, CS_DOC, 'cs');
    } finally { await browser.close(); }
    console.log('חלון "הוסף מבנה נתונים" (VS Code): תקין, צילומים ב-' + out);
})().catch(e => { console.error(e); process.exit(1); });
