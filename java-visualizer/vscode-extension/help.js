'use strict';
/**
 * "הדרכה לשימוש" (הכפתור ?): דף שבו בוחרים הדרכה — לכל אחת סרטון קצר. אותו דף משמש:
 *   VS Code — חלון (Webview), Eclipse ו-Visual Studio — help/index.html שנפתח בדפדפן (tools/build-help.js יוצר אותו).
 * הסרטונים: help/<id>.mp4 ופוסטר help/<id>.jpg (נוצרים ב-tutorials/make.js).
 */
const TUTORIALS = [
    { id: 'a', num: '01', title: 'יצירת מבנה נתונים חדש', desc: 'שמים את הסמן במקום הנכון בקוד, לוחצים "הוסף מבנה נתונים", בוחרים מבנה, ערכים ושם — ורואים את הקוד שנוצר ואת ההרצה.' },
    { id: 'b', num: '02', title: 'דוגמאות פתורות', desc: 'פותחים תרגיל מוכן מהרשימה, קוראים את הפתרון, ומריצים אותו בחלון ההדמיה — צעד אחרי צעד.' },
    { id: 'c', num: '03', title: 'מבנה בתוך מבנה', desc: 'שלוש דוגמאות: מחסנית של תורים, רשימה של מחסניות ועץ של תורים — יצירת התרגיל וההרצה הוויזואלית.' },
    { id: 'd', num: '04', title: 'מוסיפים התאמות לתרגיל', desc: 'אחרי שהתרגיל נוצר: מוסיפים ידנית מבנה נוסף, משתנה x ומערך — ורואים שגם הם מופיעים בהדמיה.' },
    { id: 'e', num: '05', title: 'שליחת תרגיל והצעה לפתרון', desc: 'מעלים את דף התרגיל — צילום מסך (Ctrl+V), PDF או Word — ומקבלים קוד מוכן עם פעולה לכל סעיף; ולכל סעיף "💡 הצעה לפתרון" שנבדקה.' },
];
// לכל סביבה סרטונים משלה (אותן הדרכות, במסך של הסביבה): Eclipse — help/, VS Code — help/vscode/, Visual Studio — help/vs/

/** "תרום לנו" — כפתור זהב קטן בתחתית הדף. אותו קישור כמו בחלון ההרצה (Theme.DONATE_URL) */
const DONATE_URL = 'https://www.paypal.com/donate/?business=motiml77%40gmail.com&currency_code=ILS&item_name=DSViz';
const DONATE_CSS = `.donate { display: inline-flex; align-items: center; gap: 6px; font: 700 12.5px/1 "Segoe UI", Heebo, Arial, sans-serif; color: #4a3405; text-decoration: none;
  padding: 6px 14px; border-radius: 999px; background: linear-gradient(#f8de8a, #ddb042); border: 1px solid #b68a1e; box-shadow: inset 0 1px 0 rgba(255,255,255,.55), 0 1px 3px rgba(0,0,0,.45); white-space: nowrap; }
.donate:hover, .donate:focus-visible { background: linear-gradient(#fbe7a4, #e6bc4e); outline: none; }
.donate:active { transform: translateY(1px); }
.donate .heart { color: #b4372c; font-size: 13px; }`;
const DONATE_HTML = `<a class="donate" id="donate" href="${DONATE_URL}" target="_blank" rel="noopener" title="התוכנה חינמית — אם היא עוזרת לך, אפשר לתמוך בה ב-PayPal"><span class="heart">&#9829;</span>תרום לנו</a>`;
// בדפדפן — הקישור נפתח בלשונית חדשה. בתוך Eclipse (dsvizOpen) ובתוך VS Code (postMessage) — פותחים בדפדפן של המערכת
const DONATE_JS = `(function () {
  var d = document.getElementById('donate'); if (!d) return;
  d.onclick = function () {
    if (window.dsvizOpen) { try { window.dsvizOpen(d.href); return false; } catch (e) { } }
    if (window.__dsvizVs) { window.__dsvizVs.postMessage({ cmd: 'open', url: d.href }); return false; }
    return true;
  };
})();`;

/** durations: { a: "1:05", ... } (אופציונלי). urlOf(file) → כתובת הקובץ בתוך התיקייה help (שונה ב-Webview ובדפדפן) */
function page({ urlOf, durations, csp }) {
    const list = TUTORIALS.map(t => Object.assign({}, t, { dur: (durations || {})[t.id] || '', video: urlOf(t.id + '.mp4'), poster: urlOf(t.id + '.jpg') }));
    const data = JSON.stringify(list).replace(/</g, '\\u003c');
    return `<!doctype html>
<html lang="he" dir="rtl"><head><meta charset="utf-8">
<title>הדרכה לשימוש — DSViz</title>
${csp ? `<meta http-equiv="Content-Security-Policy" content="${csp}">` : ''}
<meta name="viewport" content="width=device-width, initial-scale=1">
<style>
:root { color-scheme: dark; --bg: #070913; --card: #10152b; --line: rgba(255,255,255,.14); --accent: #5b8cff; --accent2: #22d3ee; --text: #f2f4fb; --muted: #aab2c8; }
* { box-sizing: border-box; }
body { margin: 0; background: radial-gradient(1200px 700px at 85% -10%, #1d2a6b55, transparent 60%), radial-gradient(900px 600px at 0% 110%, #6d28d955, transparent 60%), var(--bg);
       color: var(--text); font: 16px/1.5 "Segoe UI", Heebo, Arial, sans-serif; min-height: 100vh; }
.wrap { max-width: 1080px; margin: 0 auto; padding: 28px 24px 40px; }
h1 { font-size: 34px; margin: 0; letter-spacing: .3px; }
h1 b { background: linear-gradient(100deg, var(--accent), var(--accent2)); -webkit-background-clip: text; background-clip: text; color: transparent; }
.sub { color: var(--muted); margin: 6px 0 24px; font-size: 18px; }
.grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(440px, 1fr)); gap: 18px; }
.card { background: var(--card); border: 1px solid var(--line); border-radius: 18px; overflow: hidden; cursor: pointer; text-align: right; color: inherit; font: inherit; padding: 0;
        transition: transform .15s, box-shadow .15s, border-color .15s; }
.card:hover, .card:focus-visible { transform: translateY(-3px); border-color: var(--accent); box-shadow: 0 14px 40px #0008, 0 0 0 1px var(--accent); outline: none; }
.thumb { position: relative; aspect-ratio: 16 / 9; background: #000 center / cover no-repeat; }
.thumb::after { content: "▶"; position: absolute; inset: 0; margin: auto; width: 64px; height: 64px; border-radius: 50%; background: #000a; border: 2px solid #fffc; color: #fff;
                font-size: 26px; display: flex; align-items: center; justify-content: center; padding-left: 4px; }
.badge { position: absolute; top: 10px; right: 10px; background: #000b; border: 1px solid var(--line); padding: 2px 12px; border-radius: 999px; font-weight: 700; z-index: 1; direction: ltr; }
.dur { position: absolute; bottom: 10px; left: 10px; background: #000b; padding: 2px 10px; border-radius: 8px; font-size: 14px; z-index: 1; direction: ltr; }
.body { padding: 14px 18px 18px; }
.body h2 { margin: 0 0 4px; font-size: 22px; }
.body p { margin: 0; color: var(--muted); font-size: 15px; }
#player { position: fixed; inset: 0; background: #05060fee; display: none; align-items: center; justify-content: center; flex-direction: column; padding: 18px; z-index: 10; }
#player.on { display: flex; }
#player .bar { width: min(1180px, 100%); display: flex; align-items: center; gap: 12px; margin-bottom: 10px; }
#player .bar h3 { margin: 0; font-size: 22px; flex: 1; }
#player video { width: min(1180px, 100%); max-height: calc(100vh - 150px); background: #000; border-radius: 14px; box-shadow: 0 20px 80px #000a; }
.btn { font: inherit; color: var(--text); background: #ffffff14; border: 1px solid var(--line); border-radius: 12px; padding: 8px 18px; cursor: pointer; }
.btn:hover { background: #ffffff26; }
.speed { display: flex; align-items: center; gap: 6px; direction: ltr; }
.speed span { color: var(--muted); font-size: 14px; direction: rtl; margin-left: 4px; }
.speed .btn { padding: 6px 12px; min-width: 56px; }
.speed .btn.on { background: var(--accent); border-color: var(--accent); color: #fff; font-weight: 700; }
.hint { color: var(--muted); font-size: 14px; margin-top: 22px; }
.copy { font-size: 12px; opacity: .7; direction: ltr; text-align: right; }
.foot { display: flex; align-items: center; justify-content: space-between; gap: 12px; margin-top: 14px; }
${DONATE_CSS}
</style></head><body>
<div class="wrap">
  <h1>הדרכה <b>לשימוש</b></h1>
  <div class="sub">בוחרים הדרכה — לכל אחת סרטון קצר שמראה בדיוק איפה לוחצים ומה נפתח.</div>
  <div class="grid" id="grid"></div>
  <div class="hint">אפשר לחזור לכאן בכל רגע בכפתור <b>?</b> בסרגל הכלים.</div>
  <div class="foot">${DONATE_HTML}<div class="hint copy" style="margin:0">© 2026 Moti Levi · motiml77@gmail.com</div></div>
</div>
<div id="player"><div class="bar"><h3 id="ptitle"></h3><div class="speed" id="speed"><span>מהירות</span></div><button class="btn" id="next">ההדרכה הבאה ◂</button><button class="btn" id="close">✕ סגירה</button></div><video id="vid" controls></video></div>
<script>
const LIST = ${data};
const $ = id => document.getElementById(id);
$('grid').innerHTML = LIST.map((t, i) => '<button class="card" data-i="' + i + '"><div class="thumb" style="background-image:url(' + t.poster + ')"><span class="badge">' + t.num + '</span>'
  + (t.dur ? '<span class="dur">' + t.dur + '</span>' : '') + '</div><div class="body"><h2>' + t.title + '</h2><p>' + t.desc + '</p></div></button>').join('');
let cur = -1, rate = 1;
try { rate = +(localStorage.getItem('dsviz.rate') || 1) || 1; } catch (e) { }
const SPEEDS = [1, 1.25, 1.5, 2];
function setRate(r) {
  rate = r; $('vid').defaultPlaybackRate = r; $('vid').playbackRate = r;
  try { localStorage.setItem('dsviz.rate', String(r)); } catch (e) { }
  [...$('speed').querySelectorAll('button')].forEach(b => b.classList.toggle('on', +b.dataset.r === r));
}
SPEEDS.forEach(r => { const b = document.createElement('button'); b.className = 'btn'; b.dataset.r = r; b.textContent = r === 1 ? 'רגיל' : r + '×'; b.onclick = () => setRate(r); $('speed').appendChild(b); });
setRate(SPEEDS.includes(rate) ? rate : 1);
function play(i) {
  cur = i; const t = LIST[i];
  $('ptitle').textContent = t.num + ' · ' + t.title;
  $('vid').src = t.video; $('vid').poster = t.poster;
  $('player').classList.add('on');
  $('vid').playbackRate = rate;
  $('vid').play().catch(() => {});
  try { history.replaceState(null, '', '#' + t.id); } catch (e) {}
}
function stop() { $('vid').pause(); $('vid').removeAttribute('src'); $('vid').load(); $('player').classList.remove('on'); cur = -1; try { history.replaceState(null, '', location.pathname); } catch (e) {} }
$('grid').onclick = e => { const c = e.target.closest('.card'); if (c) play(+c.dataset.i); };
$('close').onclick = stop;
$('next').onclick = () => play((cur + 1) % LIST.length);
document.addEventListener('keydown', e => { if (e.key === 'Escape') stop(); });
const h = (location.hash || '').slice(1), k = LIST.findIndex(t => t.id === h);
if (k >= 0) play(k);
${DONATE_JS}
</script></body></html>`;
}

/**
 * דף נגן להדרכה אחת (help/player.html#a): רק הסרטון שנבחר, ובחירת מהירות. נכתב ב-ES5 כדי שיעבוד גם בדפדפן הישן שבתוך Eclipse ישן (IE).
 * אם הסרטון לא מתנגן — קוראים ל-window.dsvizFail (ש-Eclipse מגדיר) או מציגים הודעה.
 */
/** הסרטונים באתר (הענף הראשי ב-GitHub) — ההתקנה לא כוללת אותם; הנגן מנגן משם כשאין עותק במחשב, והעדכון ברקע מוריד אותם */
const REMOTE_HELP = 'https://raw.githubusercontent.com/motiml77/java-lamerhav/master/java-visualizer/help/';

function playerPage({ urlOf, csp, id: fixedId, remote }) {
    const list = TUTORIALS.map(t => ({ id: t.id, num: t.num, title: t.title, video: urlOf(t.id + '.mp4'), poster: urlOf(t.id + '.jpg') }));
    const data = JSON.stringify(list).replace(/</g, '\\u003c');
    return `<!doctype html>
<html lang="he" dir="rtl"><head><meta charset="utf-8">
<meta http-equiv="X-UA-Compatible" content="IE=edge">
<title>הדרכה לשימוש — DSViz</title>
${csp ? `<meta http-equiv="Content-Security-Policy" content="${csp}">` : ''}
<meta name="viewport" content="width=device-width, initial-scale=1">
<style>
html, body { height: 100%; margin: 0; }
body { background: #070913; color: #f2f4fb; font: 16px/1.4 "Segoe UI", Heebo, Arial, sans-serif; display: flex; flex-direction: column; }
.bar { display: flex; align-items: center; padding: 10px 16px; flex: 0 0 auto; }
.bar h1 { margin: 0; font-size: 20px; flex: 1; }
.speed { direction: ltr; display: flex; align-items: center; }
.speed span { color: #aab2c8; font-size: 14px; direction: rtl; margin-right: 8px; }
.speed button { font: inherit; font-size: 14px; color: #f2f4fb; background: #1b2140; border: 1px solid #3a4468; border-radius: 10px; padding: 5px 12px; margin-left: 6px; min-width: 52px; cursor: pointer; }
.speed button:hover { background: #2a3260; }
.other { font: inherit; font-size: 14px; color: #f2f4fb; background: #1b2140; border: 1px solid #3a4468; border-radius: 10px; padding: 5px 12px; margin-right: 12px; cursor: pointer; display: none; }
.speed button.on { background: #5b8cff; border-color: #5b8cff; color: #fff; font-weight: 700; }
.stage { flex: 1 1 auto; min-height: 0; display: flex; align-items: center; justify-content: center; padding: 0 12px 12px; }
video { max-width: 100%; max-height: 100%; background: #000; border-radius: 10px; outline: none; }
#err { display: none; color: #ffd27a; text-align: center; padding: 20px; }
.foot { flex: 0 0 auto; display: flex; align-items: center; justify-content: space-between; padding: 0 16px 8px; }
.copy { color: #aab2c8; font-size: 11px; opacity: .7; direction: ltr; }
${DONATE_CSS}
</style></head><body>
<div class="bar"><h1 id="ttl"></h1><button class="other" id="other">הדרכה אחרת</button><div class="speed" id="speed"><span>מהירות</span></div></div>
<div class="stage"><video id="vid" controls autoplay></video><div id="err">לא הצלחתי לנגן את הסרטון בחלון הזה.</div></div>
<div class="foot">${DONATE_HTML}<div class="copy">© 2026 Moti Levi · motiml77@gmail.com</div></div>
<script>
var LIST = ${data};
var SPEEDS = [1, 1.25, 1.5, 2];
function $(id) { return document.getElementById(id); }
var id = ${JSON.stringify(fixedId || null)} || (location.hash || '#a').slice(1), t = LIST[0], i, vs = null;
try { vs = acquireVsCodeApi(); window.__dsvizVs = vs; } catch (e) { }
for (i = 0; i < LIST.length; i++) if (LIST[i].id === id) t = LIST[i];
$('ttl').textContent = t.num + ' · ' + t.title;
document.title = t.title;
var v = $('vid');
v.poster = t.poster;
var rate = 1;
try { rate = parseFloat(localStorage.getItem('dsviz.rate')) || 1; } catch (e) { }
if (SPEEDS.indexOf(rate) < 0) rate = 1;
function setRate(r) {
  rate = r; v.defaultPlaybackRate = r; v.playbackRate = r;
  try { localStorage.setItem('dsviz.rate', String(r)); } catch (e) { }
  var bs = $('speed').getElementsByTagName('button');
  for (var k = 0; k < bs.length; k++) bs[k].className = parseFloat(bs[k].getAttribute('data-r')) === r ? 'on' : '';
}
SPEEDS.forEach(function (r) {
  var b = document.createElement('button');
  b.setAttribute('data-r', r); b.textContent = r === 1 ? 'רגיל' : r + '×';
  b.onclick = function () { setRate(r); };
  $('speed').appendChild(b);
});
if (vs) { $('other').style.display = 'block'; $('other').onclick = function () { vs.postMessage({ cmd: 'list' }); }; }
function fail() {
  if (window.dsvizFail) { try { window.dsvizFail(); return; } catch (e) { } }
  if (vs) { vs.postMessage({ cmd: 'fail' }); }
  v.style.display = 'none'; $('err').style.display = 'block';
}
// הסרטון לא במחשב (ההתקנה בלי סרטונים, והעדכון ברקע עוד לא הוריד אותו) — מנגנים אותו מהאתר
var REMOTE = ${JSON.stringify(remote || '')}, triedRemote = false;
v.onerror = function () {
  if (REMOTE && !triedRemote) { triedRemote = true; v.src = REMOTE + t.id + '.mp4'; return; }
  fail();
};
v.addEventListener('loadedmetadata', function () { v.playbackRate = rate; });
v.src = t.video;
setRate(rate);
${DONATE_JS}
</script></body></html>`;
}

module.exports = { TUTORIALS, page, playerPage, REMOTE_HELP };
