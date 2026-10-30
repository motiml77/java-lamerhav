// צילומים של חלונות ה-Webview של VS Code (הוסף מבנה נתונים, שליחת תרגיל, הצעה לפתרון) — הדף האמיתי של התוסף, בדפדפן,
// עם הצבעים של VS Code (Light Modern). כמו DlgScript / SwingScript: לכל "shot" צילום + מיקומי הרכיבים (JSON באותו פורמט).
//   capture({ html, host, size, actions, dir, src }) → { name: json }
//   actions: [{ shot: 'a1' }, { click: '#ok' }, { clickText: ['.chip', 'מחסנית'] }, { fill: ['#name', 'stk'] }, { check: '#random' },
//             { wait: 300 }, { eval: 'js' }]
const fs = require('fs'), path = require('path');
let pw;
for (const p of ['playwright', '/opt/node-tools/node_modules/playwright']) { try { pw = require(p); break; } catch (e) { /* הבא */ } }

const FONTS = path.join(__dirname, '..', 'fonts', 'node_modules', '@expo-google-fonts');
function fontFace(family, pkg, file) {
  const f = path.join(FONTS, pkg, file);
  return fs.existsSync(f) ? `@font-face { font-family: '${family}'; src: url(data:font/ttf;base64,${fs.readFileSync(f).toString('base64')}); }` : '';
}
function findTtf(pkg, re) {
  const dir = path.join(FONTS, pkg);
  if (!fs.existsSync(dir)) return null;
  const walk = d => { for (const e of fs.readdirSync(d, { withFileTypes: true })) { const p = path.join(d, e.name); if (e.isDirectory()) { const r = walk(p); if (r) return r; } else if (re.test(e.name)) return p; } return null; };
  return walk(dir);
}

// הצבעים של VS Code (Light Modern) — מה שהתוסף מקבל כמשתני CSS בתוך ה-Webview
const THEME = `
:root {
  --vscode-font-family: 'Heebo', 'Segoe UI', sans-serif; --vscode-editor-font-family: 'JBMono', monospace;
  --vscode-foreground: #3b3b3b; --vscode-editor-background: #ffffff; --vscode-descriptionForeground: #717171;
  --vscode-widget-border: #e5e5e5; --vscode-sideBar-background: #f8f8f8; --vscode-button-background: #005fb8; --vscode-button-foreground: #ffffff;
  --vscode-button-secondaryBackground: #e5e5e5; --vscode-button-secondaryForeground: #3b3b3b; --vscode-button-border: #0000001a;
  --vscode-input-background: #ffffff; --vscode-input-foreground: #3b3b3b; --vscode-input-border: #cecece; --vscode-dropdown-background: #ffffff;
  --vscode-dropdown-foreground: #3b3b3b; --vscode-dropdown-border: #cecece; --vscode-textCodeBlock-background: #f8f8f8; --vscode-focusBorder: #005fb8;
  --vscode-checkbox-background: #f8f8f8; --vscode-checkbox-border: #cecece; --vscode-textLink-foreground: #005fb8;
}`;

async function capture({ html, host, size, actions, dir, src }) {
  if (!pw) throw new Error('playwright לא מותקן');
  fs.mkdirSync(dir, { recursive: true });
  const heebo = findTtf('heebo', /Heebo_400Regular\.ttf$/), mono = findTtf('jetbrains-mono', /JetBrainsMono_400Regular\.ttf$/);
  const css = THEME + (heebo ? `@font-face { font-family: 'Heebo'; src: url(data:font/ttf;base64,${fs.readFileSync(heebo).toString('base64')}); }` : '')
    + (mono ? `@font-face { font-family: 'JBMono'; src: url(data:font/ttf;base64,${fs.readFileSync(mono).toString('base64')}); }` : '');
  const browser = await pw.chromium.launch();
  const page = await browser.newPage({ viewport: { width: size[0], height: size[1] } });
  await page.exposeFunction('hostMsg', async msg => (host ? host(msg) : null));
  await page.addInitScript(() => {
    window.acquireVsCodeApi = () => ({
      postMessage: async msg => { const r = await window.hostMsg(msg); if (r) for (const m of [].concat(r)) window.postMessage(m, '*'); },
      setState() {}, getState() { return null; }
    });
  });
  const body = html.replace('</head>', `<style>${css}</style></head>`).replace('<body>', '<body class="vscode-light">');
  await page.route('http://dsviz.test/page', r => r.fulfill({ contentType: 'text/html; charset=utf-8', body }));
  await page.goto('http://dsviz.test/page');
  await page.evaluate(() => document.fonts.ready);
  await page.waitForTimeout(400);
  const out = {};
  for (const a of actions) {
    if (a.wait) await page.waitForTimeout(a.wait);
    else if (a.click) { await page.click(a.click); await page.waitForTimeout(250); }
    else if (a.clickText) { await page.locator(a.clickText[0], { hasText: a.clickText[1] }).first().click(); await page.waitForTimeout(250); }
    else if (a.fill) { await page.fill(a.fill[0], a.fill[1]); await page.waitForTimeout(300); }
    else if (a.check) { await page.check(a.check); await page.waitForTimeout(300); }
    else if (a.eval) { await page.evaluate(a.eval); await page.waitForTimeout(300); }
    else if (a.shot) {
      await page.screenshot({ path: path.join(dir, a.shot + '.png') });
      const js = await page.evaluate(controls);
      js.w = size[0]; js.h = size[1];
      js.src = `${src}/${a.shot}.png`;
      fs.writeFileSync(path.join(dir, a.shot + '.json'), JSON.stringify(js));
      out[a.shot] = js;
    }
  }
  await browser.close();
  return out;
}

/** רכיבים בדף — באותם סוגים כמו בצילומים של Eclipse (Chip / Round / Button / Text / Spinner / Combo / Label) */
function controls() {
  const res = [];
  const vis = el => { const r = el.getBoundingClientRect(); const s = getComputedStyle(el); return r.width > 0 && r.height > 0 && s.visibility !== 'hidden' && s.display !== 'none'; };
  const add = (type, el, text, extra) => { const r = el.getBoundingClientRect(); res.push(Object.assign({ type, text: text == null ? '' : String(text).trim(), x: Math.round(r.left), y: Math.round(r.top), w: Math.round(r.width), h: Math.round(r.height), enabled: !el.disabled }, extra || {})); };
  let ti = 0;
  for (const el of document.querySelectorAll('button, input, textarea, select, label, h1, h2, h3, p, span, div, pre')) {
    if (!vis(el)) continue;
    const tag = el.tagName;
    if (tag === 'BUTTON') add(/\bchip\b/.test(el.className) ? 'Chip' : 'Round', el, el.textContent);
    else if (tag === 'INPUT' && (el.type === 'checkbox' || el.type === 'radio')) { const l = el.closest('label') || el; add('Button', l, l.textContent); }
    else if (tag === 'INPUT' && el.type === 'number') add('Spinner', el, el.value);
    else if (tag === 'INPUT' || tag === 'TEXTAREA') add('Text', el, el.value, { index: ti++, hint: el.placeholder || '' });
    else if (tag === 'SELECT') add('Combo', el, el.options[el.selectedIndex] ? el.options[el.selectedIndex].text : '');
    else if (tag === 'PRE') add('Label', el, el.textContent.slice(0, 200));
    else if ([...el.childNodes].some(n => n.nodeType === 3 && n.textContent.trim()) && !el.closest('button') && !el.closest('label')) add('Label', el, el.textContent);
  }
  return { controls: res };
}

module.exports = { capture };
