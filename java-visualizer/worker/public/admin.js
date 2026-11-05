// /admin — מסוף ההנהלה: סקירה, בקשות, מורים ועמדות, תלמידים ותשלומים, יומן פעולות, הגדרות.
// רק כתובות שב-ADMIN_EMAILS (בהגדרות השרת). כל פעולה נבדקת בשרת ונרשמת ביומן.
import { boot, currentUser, googleSignIn, api, h, icon, ICONS, googleLogo, avatar, fmtDate, ago, daysSince, busy, toast, ask, logoutWeb } from '/static/common.js';

const app = document.getElementById('app');
let user = null;
let settingsCache = null;
let openCount = 0;
const cache = { teachers: null, students: null };

const NAV = [
  ['overview', 'סקירה', ICONS.grid],
  ['requests', 'בקשות', ICONS.msg],
  ['teachers', 'מורים ועמדות', ICONS.user],
  ['students', 'תלמידים ותשלומים', ICONS.cap],
  ['log', 'יומן פעולות', ICONS.list],
  ['settings', 'הגדרות', ICONS.gear],
];

const ACTIONS = {
  'signin.teacher': 'כניסת מורה', 'signin.student': 'כניסת תלמיד', 'station.claim': 'עמדה חדשה חוברה', 'station.again': 'עמדה התחברה מחדש',
  'station.bad-code': 'ניסיון חיבור עם קוד מורה שגוי', 'station.release': 'עמדה שוחררה', 'teacher.add': 'מורה נוסף', 'teacher.update': 'שינוי אצל מורה',
  'teacher.new-code': 'קוד מורה חדש', 'request.teacher': 'בקשת הרשאת מורה', 'request.seats': 'בקשת עמדות נוספות', 'request.release': 'בקשה לשחרר מחשב',
  'request.decline': 'בקשה נדחתה', 'student.grant': 'הענקת גישה לתלמיד', 'student.revoke': 'ביטול גישה לתלמיד', 'session.revoke': 'מחשב נותק',
  'payment.paid': 'תשלום התקבל', 'payment.seats': 'חבילת עמדות נקנתה', 'payment.refunded': 'החזר כספי', 'payment.failed': 'תשלום נכשל', settings: 'שינוי בהגדרות',
};
const dotFor = a => (a.startsWith('payment.') ? 'green' : a === 'station.bad-code' ? 'warn' : /^(teacher|student|settings|session|request\.decline)/.test(a) ? 'dark' : '');
const REQ_KIND = { teacher: ['הרשאת מורה', 'blue'], seats: ['תוספת עמדות', 'orange'], release: ['שחרור מחשב', 'purple'] };

// ------------------------------------------------------------------ מסגרת

function route() {
  const m = /^#([a-z]+)(?:=(.*))?$/.exec(location.hash || '');
  return m ? { page: m[1], arg: decodeURIComponent(m[2] || '') } : { page: 'overview', arg: '' };
}

function shell(content) {
  const r = route();
  const nav = h('nav', { class: 'side', 'aria-label': 'תפריט' },
    h('div', { class: 'brand' }, h('div', { class: 'logo dark' }, icon(ICONS.shield, 19, '#fff')), h('div', null, 'מסוף ההנהלה', h('small', null, 'DSViz · מאגר תרגילים'))),
    h('div', { class: 'nav', style: { display: 'flex', flexDirection: 'column', gap: '4px' } }, NAV.map(([id, label, ic]) => h('a', { href: '#' + id, 'aria-current': r.page === id || (id === 'teachers' && r.page === 'teacher') ? 'page' : null },
      icon(ic, 18), h('span', null, label), id === 'requests' && openCount ? h('span', { class: 'count', 'aria-label': openCount + ' פתוחות' }, String(openCount)) : null))));
  const header = h('header', null, searchBox(), h('div', { class: 'grow' }),
    h('div', { class: 'who' }, h('div', { class: 'meta' }, h('span', { class: 'ltr' }, user.email),
      h('a', { href: '#', onclick: async e => { e.preventDefault(); await logoutWeb(); location.reload(); } }, 'התנתקות')), avatar(user, true)));
  app.replaceChildren(h('div', { class: 'shell' }, nav, h('div', { class: 'main' }, header, h('main', { class: 'content', id: 'content' }, content))));
}

function searchBox() {
  const input = h('input', { type: 'search', placeholder: 'חיפוש מורה, תלמיד, קוד מורה או מחשב', 'aria-label': 'חיפוש', autocomplete: 'off' });
  const results = h('div', { class: 'results', hidden: true, role: 'listbox' });
  const box = h('label', { class: 'search' }, icon(ICONS.search, 18, '#5A6478'), input, results);
  let loading = null;
  const ensure = () => loading || (loading = Promise.all([
    cache.teachers ? cache.teachers : api('GET', '/v1/admin/teachers').then(r => (cache.teachers = r.teachers)),
    cache.students ? cache.students : api('GET', '/v1/admin/students').then(r => (cache.students = r.students)),
    api('GET', '/v1/admin/stations').then(r => r.stations),
  ]));
  input.addEventListener('focus', () => ensure().catch(() => {}));
  input.addEventListener('input', async () => {
    const q = input.value.trim().toLowerCase().replace(/-/g, '');
    if (q.length < 2) { results.hidden = true; return; }
    const [teachers, students, stations] = await ensure();
    const hits = [];
    for (const t of teachers) if ((t.name + ' ' + t.email + ' ' + t.school + ' ' + t.code.replace('-', '')).toLowerCase().includes(q)) hits.push(['מורה', t.name || t.email, t.email, '#teacher=' + encodeURIComponent(t.email)]);
    for (const s of stations) if (String(s.name).toLowerCase().includes(q)) hits.push(['מחשב', s.name, s.teacherName || s.teacher, '#teacher=' + encodeURIComponent(s.teacher)]);
    for (const s of students) if ((s.name + ' ' + s.email).toLowerCase().includes(q)) hits.push(['תלמיד', s.name || s.email, s.email, '#students=' + encodeURIComponent(s.email)]);
    results.replaceChildren(...(hits.length ? hits.slice(0, 12).map(([kind, title, sub, href]) => h('button', { type: 'button', role: 'option', onclick: () => { results.hidden = true; input.value = ''; location.hash = href; } },
      h('span', { class: 'pill gray' }, kind), h('span', { class: 'grow' }, h('b', null, title), ' ', h('span', { class: 'small muted ltr' }, sub))))
      : [h('div', { class: 'empty' }, 'לא נמצא')]));
    results.hidden = false;
  });
  input.addEventListener('keydown', e => { if (e.key === 'Escape') results.hidden = true; });
  document.addEventListener('click', e => { if (!box.contains(e.target)) results.hidden = true; });
  return box;
}

const loadingView = () => h('div', { class: 'loading' }, 'טוען…');
async function render() {
  const r = route();
  shell(loadingView());
  const content = document.getElementById('content');
  try {
    const views = { overview, requests, teachers, teacher: teachers, students, log: logView, settings: settingsView };
    const v = await (views[r.page] || overview)(r.arg);
    content.replaceChildren(...[v].flat(Infinity));
  } catch (e) {
    content.replaceChildren(h('div', { class: 'warn-box' }, e.message || String(e)));
  }
}
function refreshCount(n) {
  openCount = n;
  const a = document.querySelector('.nav a[href="#requests"]');
  if (!a) return;
  const old = a.querySelector('.count');
  if (old) old.remove();
  if (n) a.append(h('span', { class: 'count' }, String(n)));
}

// ------------------------------------------------------------------ סקירה

async function overview() {
  const o = await api('GET', '/v1/admin/overview');
  refreshCount(o.counts.openRequests);
  const c = o.counts;
  const s = settingsCache || (settingsCache = (await api('GET', '/v1/admin/settings')).settings);
  const pct = c.seats ? Math.round(100 * c.used / c.seats) : 0;
  const kpi = (href, k, v, sub, bar) => h('a', { class: 'kpi', href }, h('span', { class: 'k' }, k), v, sub ? h('span', { class: 'small muted' }, sub) : null, bar || null);
  const attention = [
    ...o.requests.map(r => requestCard(r, { compact: true, after: render })),
    ...o.full.map(t => h('div', { class: 'req row wrap' }, h('span', { class: 'dot warn', style: { marginTop: 0 } }),
      h('div', { class: 'grow', style: { minWidth: '220px' } }, h('b', null, t.name || t.email), ` — כל ${t.seats} העמדות בשימוש. עמדה חדשה שתנסה להתחבר תקבל ״אין עוד היתרים״.`),
      h('a', { class: 'btn sm', href: '#teacher=' + encodeURIComponent(t.email) }, 'למורה'))),
  ];
  return [
    h('div', { class: 'titlebar' }, h('h1', null, 'סקירה'), h('span', { class: 'small muted' }, `הגישה בתשלום פתוחה עד ${fmtDate(s.accessUntil)} · שליחה ל-Gemini: ${s.requireLoginForGemini ? 'רק למחוברים' : 'פתוחה לכולם'}`)),
    h('div', { class: 'kpis' },
      kpi('#teachers', 'מורים פעילים', h('b', { class: 'v' }, String(c.teachers)), c.teachersBlocked ? `ועוד ${c.teachersBlocked} מושבתים` : ''),
      kpi('#teachers', 'עמדות מחשב בשימוש', h('span', { class: 'v' }, String(c.used), ' ', h('small', null, `מתוך ${c.seats}`)), '', h('span', { class: 'bar' }, h('span', { style: { width: pct + '%' } }))),
      kpi('#students', 'תלמידים עם גישה', h('b', { class: 'v' }, String(c.students)), `${c.studentsPaid} בתשלום · ${c.studentsManual} הענקה ידנית`),
      kpi('#students=payments', 'תשלומים החודש', h('b', { class: 'v' }, String(c.paymentsMonth)), c.refundsMonth ? `${c.refundsMonth} הוחזרו` : '')),
    h('div', { class: 'two' },
      h('section', { class: 'card' }, h('div', { class: 'row', style: { justifyContent: 'space-between' } }, h('h2', null, 'דורש טיפול'), h('a', { href: '#requests', class: 'small', style: { fontWeight: 600 } }, 'כל הבקשות')),
        attention.length ? attention : h('div', { class: 'empty' }, 'אין כרגע שום דבר שמחכה לך.')),
      h('section', { class: 'card' }, h('div', { class: 'row', style: { justifyContent: 'space-between' } }, h('h2', null, 'פעילות אחרונה'), h('a', { href: '#log', class: 'small', style: { fontWeight: 600 } }, 'יומן מלא')),
        feed(o.recent))),
  ];
}

function feed(list) {
  if (!list.length) return h('div', { class: 'empty' }, 'עוד אין פעילות.');
  return h('ol', { class: 'feed' }, list.map(l => h('li', null, h('span', { class: 'dot ' + dotFor(l.action) }),
    h('div', { class: 'stack', style: { gap: '2px' } },
      h('span', null, ACTIONS[l.action] || l.action, l.target ? ' — ' : '', l.target ? h('span', { class: l.target.includes('@') ? 'ltr' : '' }, l.target) : null, l.detail ? ` (${l.detail})` : ''),
      h('span', { class: 'small muted' }, `${ago(l.at)} · ${l.actor === user.email ? 'את/ה' : l.actor}`)))));
}

// ------------------------------------------------------------------ בקשות

function requestCard(r, { compact, after } = {}) {
  const [kindLabel, kindColor] = REQ_KIND[r.kind] || [r.kind, 'gray'];
  const who = h('div', { style: { fontSize: '16px', lineHeight: '1.5' } }, h('b', null, r.name || r.email), r.school ? ` · ${r.school}` : '', ' · ', h('span', { class: 'ltr muted' }, r.email));
  let what, input = null;
  if (r.kind === 'seats') {
    const b = r.before || {};
    what = h('div', null, 'מבקש/ת ', h('b', null, `${r.amount} עמדות נוספות`), b.seats !== undefined ? `. כשנשלחה: ${b.used} מתוך ${b.seats} בשימוש.` : '.');
    input = h('input', { class: 'field num', type: 'number', min: '1', max: '2000', value: String(r.amount), style: { width: '90px', height: '40px' }, 'aria-label': 'כמה להוסיף' });
  } else if (r.kind === 'release') {
    what = h('div', null, 'מבקש/ת לשחרר את ', h('b', null, `״${r.stationName}״`), '. אחרי השחרור ההיתר חוזר אליו/ה.');
  } else {
    what = h('div', null, 'נכנס/ה כמורה, והחשבון עוד לא מוגדר.', r.computers ? ` כ-${r.computers} מחשבים בכיתה.` : '');
    input = h('input', { class: 'field num', type: 'number', min: '0', max: '2000', value: String(r.computers || (settingsCache && settingsCache.defaultSeats) || 30), style: { width: '90px', height: '40px' }, 'aria-label': 'מספר עמדות' });
  }
  const approveLabel = r.kind === 'seats' ? 'אישור' : r.kind === 'release' ? 'שחרור המחשב' : 'הגדרה כמורה';
  const approve = h('button', { type: 'button', class: 'btn primary grow' }, approveLabel);
  const decline = h('button', { type: 'button', class: 'btn' }, 'דחייה');
  approve.onclick = () => busy(approve, async () => {
    const body = { id: r.id, decision: 'approve' };
    if (r.kind === 'seats') body.amount = +input.value;
    if (r.kind === 'teacher') body.seats = +input.value;
    const res = await api('POST', '/v1/admin/request', body);
    cache.teachers = null;
    toast(res.result || 'אושר');
    if (after) after();
  });
  decline.onclick = async () => {
    const reason = await promptText('לדחות את הבקשה?', 'סיבה (המורה רואה אותה בלוח שלו; לא חובה)');
    if (reason === null) return;
    busy(decline, async () => { await api('POST', '/v1/admin/request', { id: r.id, decision: 'decline', reason }); toast('הבקשה נדחתה'); if (after) after(); });
  };
  const actions = h('div', { class: 'act' },
    input ? h('label', { class: 'lbl', style: { flexDirection: 'row', alignItems: 'center', gap: '10px' } }, r.kind === 'seats' ? 'כמה להוסיף' : 'מספר עמדות', input) : null,
    h('div', { class: 'row' }, approve, decline));
  return h('article', { class: 'req' },
    h('div', { class: 'row wrap', style: { gap: '8px' } }, h('span', { class: 'pill ' + kindColor }, kindLabel), h('span', { class: 'small muted' }, ago(r.createdAt))),
    h('div', { class: 'body' }, h('div', { class: 'grow stack', style: { minWidth: '240px', gap: '6px' } }, who, what, r.note ? h('div', { class: 'note' }, `״${r.note}״`) : null), compact ? null : actions),
    compact ? actions : null);
}

function promptText(title, label) {
  return new Promise(resolve => {
    const d = h('dialog', { class: 'modal', 'aria-labelledby': 'pt-t' });
    const f = h('textarea', { class: 'field', rows: '2', id: 'pt-f' });
    const end = v => { d.close(); d.remove(); resolve(v); };
    d.append(h('div', { class: 'in' }, h('h2', { id: 'pt-t' }, title), h('label', { class: 'lbl', for: 'pt-f' }, label, f),
      h('div', { class: 'row' }, h('button', { type: 'button', class: 'btn primary', onclick: () => end(f.value) }, 'אישור'), h('button', { type: 'button', class: 'btn', onclick: () => end(null) }, 'ביטול'))));
    d.addEventListener('cancel', e => { e.preventDefault(); end(null); });
    document.body.append(d);
    d.showModal();
  });
}

async function requests(arg) {
  const done = arg === 'done';
  if (!settingsCache) settingsCache = (await api('GET', '/v1/admin/settings')).settings;
  const r = await api('GET', '/v1/admin/requests' + (done ? '?status=done' : ''));
  if (!done) refreshCount(r.requests.length);
  const tabs = h('div', { class: 'tabs', role: 'tablist' },
    h('button', { type: 'button', role: 'tab', 'aria-selected': String(!done), onclick: () => { location.hash = '#requests'; } }, `פתוחות${!done ? ' · ' + r.requests.length : ''}`),
    h('button', { type: 'button', role: 'tab', 'aria-selected': String(done), onclick: () => { location.hash = '#requests=done'; } }, 'טופלו'));
  const doneRow = x => h('tr', null,
    h('td', null, h('span', { class: 'pill ' + (REQ_KIND[x.kind] || ['', 'gray'])[1] }, (REQ_KIND[x.kind] || [x.kind])[0]), ' ', x.kind === 'seats' ? `${x.amount} עמדות` : x.stationName || ''),
    h('td', null, h('div', { class: 'cellname' }, h('b', null, x.name || x.email), h('span', { class: 'ltr' }, x.email))),
    h('td', { class: 'muted' }, fmtDate(x.createdAt)),
    h('td', null, h('span', { class: 'pill ' + (x.status === 'approved' ? 'green' : 'gray') }, x.status === 'approved' ? 'אושרה' : 'נדחתה'), ' ', h('span', { class: 'small muted' }, x.result || x.reason || '')));
  const doneTable = () => h('div', { class: 'tablewrap' }, h('table', { class: 't' },
    h('thead', null, h('tr', null, ['בקשה', 'מי', 'מתי', 'החלטה'].map(x => h('th', null, x)))),
    h('tbody', null, r.requests.map(doneRow))));
  const list = done
    ? (r.requests.length ? doneTable() : h('div', { class: 'empty' }, 'עוד אין בקשות שטופלו.'))
    : (r.requests.length ? r.requests.map(x => requestCard(x, { after: render })) : h('div', { class: 'card' }, h('div', { class: 'empty' }, 'אין בקשות פתוחות.')));
  return [h('div', { class: 'stack', style: { gap: '6px' } }, h('h1', null, 'בקשות'),
    h('p', { class: 'muted' }, 'מורים מבקשים כאן לשחרר מחשב שהוחלף, וחשבונות שנכנסו כמורה מבקשים הרשאה. עמדות נוספות המורים קונים בחבילות — ומתווספות לבד אחרי התשלום.')), tabs, list,
  h('p', { class: 'small muted' }, 'כל אישור ודחייה נרשמים ביומן הפעולות, והמורה רואה את התשובה בלוח שלו.')];
}

// ------------------------------------------------------------------ מורים ועמדות

let teacherFilter = 'all';

async function teachers(selected) {
  if (!cache.teachers) cache.teachers = (await api('GET', '/v1/admin/teachers')).teachers;
  if (!settingsCache) settingsCache = (await api('GET', '/v1/admin/settings')).settings;
  const all = cache.teachers;
  const full = t => t.active && t.seats > 0 && t.used >= t.seats;
  const groups = { all: ['הכול', () => true], active: ['פעילים', t => t.active], full: ['כל העמדות בשימוש', full], blocked: ['מושבתים', t => !t.active] };
  const shown = all.filter(groups[teacherFilter][1]);
  const filters = h('div', { class: 'filters' }, Object.entries(groups).map(([id, [label, fn]]) => h('button', { type: 'button', 'aria-pressed': String(teacherFilter === id), onclick: () => { teacherFilter = id; render(); } }, `${label} · ${all.filter(fn).length}`)));
  const table = h('div', { class: 'tablewrap' }, h('table', { class: 't' },
    h('thead', null, h('tr', null, ['מורה', 'בית ספר', 'עמדות', 'כניסה אחרונה', 'מצב'].map(x => h('th', null, x)))),
    h('tbody', null, shown.length ? shown.map(t => {
      const pct = t.seats ? Math.min(100, Math.round(100 * t.used / t.seats)) : (t.used ? 100 : 0);
      const tr = h('tr', { class: 'click' + (t.email === selected ? ' sel' : ''), tabindex: '0', onclick: () => { location.hash = '#teacher=' + encodeURIComponent(t.email); }, onkeydown: e => { if (e.key === 'Enter') location.hash = '#teacher=' + encodeURIComponent(t.email); } },
        h('td', null, h('div', { class: 'cellname' }, h('b', null, t.name || '—'), h('span', { class: 'ltr' }, t.email))),
        h('td', null, t.school || '—'),
        h('td', null, h('div', { class: 'seats' }, h('span', null, h('b', null, String(t.used)), ` / ${t.seats}`), h('span', { class: 'bar' + (full(t) ? ' full' : '') }, h('span', { style: { width: pct + '%' } })))),
        h('td', { class: 'muted' }, t.lastSignIn ? ago(t.lastSignIn) : 'עוד לא נכנס/ה'),
        h('td', null, t.active ? h('span', { class: 'pill green' }, 'פעיל') : h('span', { class: 'pill gray' }, 'מושבת')));
      return tr;
    }) : h('tr', null, h('td', { colspan: '5' }, h('div', { class: 'empty' }, all.length ? 'אין מורים בקבוצה הזו.' : 'עוד אין מורים — מוסיפים בכפתור "הוספת מורה".'))))));
  const main = h('div', { class: 'grow stack', style: { gap: '16px' } },
    h('div', { class: 'titlebar' }, h('h1', null, 'מורים ועמדות'), h('button', { type: 'button', class: 'btn primary', onclick: addTeacher }, icon(ICONS.plus, 18, '#fff', 2.4), 'הוספת מורה')),
    filters, table, h('p', { class: 'small muted' }, 'לוחצים על מורה כדי לשנות את מספר העמדות, לראות את המחשבים שלו ולשחרר עמדה.'));
  if (!selected) return main;
  return h('div', { class: 'split' }, main, await teacherDrawer(selected));
}

async function teacherDrawer(email) {
  const d = await api('GET', '/v1/admin/teacher?email=' + encodeURIComponent(email));
  const t = d.teacher;
  const val = h('input', { class: 'field num', type: 'number', min: '0', max: '10000', value: String(t.seats), 'aria-label': 'מספר העמדות' });
  const preview = h('div', { class: 'note', style: { background: '#fff', color: 'var(--ink)' } });
  const save = h('button', { type: 'button', class: 'btn primary grow' }, 'שמירה');
  const reset = h('button', { type: 'button', class: 'btn' }, 'ביטול');
  const update = () => {
    const n = Math.max(0, Math.round(+val.value || 0));
    const changed = n !== t.seats;
    save.disabled = !changed; reset.disabled = !changed;
    if (!changed) preview.textContent = `${t.used} בשימוש · ${Math.max(0, t.seats - t.used)} פנויות.`;
    else if (n >= t.used) preview.replaceChildren(`${t.seats} ← `, h('b', null, String(n)), `: אחרי השמירה יהיו ${n - t.used} עמדות פנויות. העמדות שכבר מחוברות לא משתנות.`);
    else preview.replaceChildren(`${t.seats} ← `, h('b', null, String(n)), `: פחות מ-${t.used} העמדות שבשימוש. אף עמדה לא תנותק — רק עמדות חדשות לא יוכלו להתחבר עד שישתחררו ${t.used - n}.`);
  };
  const step = k => { val.value = String(Math.max(0, (+val.value || 0) + k)); update(); };
  val.addEventListener('input', update);
  reset.onclick = () => { val.value = String(t.seats); update(); };
  save.onclick = async () => {
    const n = Math.max(0, Math.round(+val.value || 0));
    if (n < t.used && !await ask({ title: 'מכסה קטנה ממה שבשימוש', text: `ל${t.name || 'מורה'} מחוברות ${t.used} עמדות. אף אחת לא תנותק; כדי לפנות מקום — משחררים עמדות ברשימה למטה.`, ok: 'שמירה בכל זאת' })) return;
    busy(save, async () => { await api('POST', '/v1/admin/teacher', { email: t.email, seats: n }); cache.teachers = null; toast(`מכסת העמדות: ${t.seats} ← ${n}`); render(); });
  };
  update();
  const copy = h('button', { type: 'button', class: 'btn sm' }, 'העתקה');
  copy.onclick = async () => { try { await navigator.clipboard.writeText(t.code); toast('הקוד הועתק'); } catch (e) { toast(t.code); } };
  const toggle = h('button', { type: 'button', class: t.active ? 'btn sm danger' : 'btn sm primary' }, t.active ? 'השבתת המורה' : 'הפעלה מחדש');
  toggle.onclick = async () => {
    if (t.active && !await ask({ title: `להשבית את ${t.name || t.email}?`, text: `הלוח של המורה נסגר, וכל מחשב שלו ננעל בפעם הבאה שהוא מחובר לאינטרנט. אפשר להפעיל מחדש בכל רגע.`, ok: 'השבתה', danger: true })) return;
    busy(toggle, async () => { await api('POST', '/v1/admin/teacher', { email: t.email, active: !t.active }); cache.teachers = null; toast(t.active ? 'המורה הושבת' : 'המורה הופעל'); render(); });
  };
  const edit = h('button', { type: 'button', class: 'btn sm' }, 'עריכת פרטים');
  edit.onclick = () => editTeacher(t);
  const stations = d.stations.map(s => {
    const stale = daysSince(s.lastSeen) > 30;
    const rel = h('button', { type: 'button', class: 'btn sm' }, 'שחרור');
    rel.onclick = async () => {
      if (!await ask({ title: `לשחרר את ״${s.name}״?`, text: `ההיתר חוזר ל${t.name || 'מורה'}. המחשב הזה ננעל בפעם הבאה שהוא מחובר לאינטרנט, ויצטרך את קוד המורה כדי להתחבר שוב.`, ok: 'שחרור' })) return;
      busy(rel, async () => { await api('POST', '/v1/admin/station/release', { id: s.id }); cache.teachers = null; toast('העמדה שוחררה'); render(); });
    };
    return h('div', { class: 'item' + (stale ? ' stale' : '') }, h('div', { class: 'grow' }, h('div', { style: { fontWeight: 600 } }, s.name),
      h('div', { class: 'small', style: { color: stale ? 'var(--orange)' : 'var(--muted)' } }, stale ? `לא נראתה ${daysSince(s.lastSeen)} יום — אולי הוחלפה` : `חוברה ${fmtDate(s.claimedAt)} · פעילה ${ago(s.lastSeen)}`)), rel);
  });
  return h('aside', { class: 'drawer card', 'aria-label': 'פרטי המורה' },
    h('div', { class: 'row', style: { alignItems: 'flex-start', justifyContent: 'space-between' } },
      h('div', { class: 'stack', style: { gap: '2px' } }, h('h2', { style: { fontSize: '21px' } }, t.name || t.email), h('span', { class: 'small muted' }, h('span', { class: 'ltr' }, t.email), t.school ? ' · ' + t.school : ''),
        t.active ? null : h('span', { class: 'pill gray', style: { alignSelf: 'flex-start', marginTop: '6px' } }, 'מושבת')),
      h('a', { class: 'btn sm', href: '#teachers', 'aria-label': 'סגירה' }, icon(ICONS.x, 16))),
    h('section', { class: 'stack', style: { background: 'var(--bg)', borderRadius: '14px', padding: '16px', gap: '12px' } },
      h('div', { class: 'row', style: { justifyContent: 'space-between', alignItems: 'baseline' } }, h('h3', null, 'מכסת עמדות'), h('span', { class: 'small muted' }, `בשימוש ${t.used}`)),
      h('div', { class: 'stepper' }, h('button', { type: 'button', class: 'btn', 'aria-label': 'פחות עמדה', onclick: () => step(-1) }, icon(ICONS.minus, 18, '#17213A', 2.4)), val,
        h('button', { type: 'button', class: 'btn', 'aria-label': 'עוד עמדה', onclick: () => step(1) }, icon(ICONS.plus, 18, '#17213A', 2.4))),
      h('div', { class: 'row' }, [5, 10, 20].map(k => h('button', { type: 'button', class: 'btn sm grow', onclick: () => step(k) }, '‎+' + k))),
      preview, h('div', { class: 'row' }, save, reset)),
    h('section', { class: 'row wrap' }, h('div', { class: 'grow stack', style: { gap: '2px' } }, h('span', { class: 'small muted', style: { fontWeight: 600 } }, 'קוד המורה'), h('span', { class: 'mono', style: { fontSize: '20px', textAlign: 'right' } }, t.code)), copy),
    h('section', { class: 'stack' }, h('h3', null, `המחשבים של ${t.name || 'המורה'} (${d.stations.length})`),
      h('div', { class: 'list' }, stations.length ? stations : h('div', { class: 'empty' }, 'עוד אין מחשבים מחוברים.'))),
    t.note ? h('div', { class: 'note' }, t.note) : null,
    h('section', { class: 'row wrap', style: { borderTop: '1px solid var(--line)', paddingTop: '14px' } }, edit, h('div', { class: 'grow' }), toggle));
}

function teacherForm(title, t, onSave) {
  const d = h('dialog', { class: 'modal', 'aria-labelledby': 'tf-t' });
  const email = h('input', { class: 'field ltr', type: 'email', id: 'tf-e', placeholder: 'name@gmail.com', value: t ? t.email : '', disabled: !!t });
  const name = h('input', { class: 'field', type: 'text', id: 'tf-n', value: t ? t.name : '' });
  const school = h('input', { class: 'field', type: 'text', id: 'tf-s', value: t ? t.school : '' });
  const seats = h('input', { class: 'field', type: 'number', min: '0', max: '10000', id: 'tf-c', value: String(t ? t.seats : settingsCache.defaultSeats) });
  const note = h('input', { class: 'field', type: 'text', id: 'tf-o', value: t ? t.note : '', placeholder: 'למשל: הסכם עד סוף השנה' });
  const ok = h('button', { type: 'button', class: 'btn primary' }, t ? 'שמירה' : 'הוספה');
  const close = () => { d.close(); d.remove(); };
  ok.onclick = () => busy(ok, async () => { await onSave({ email: email.value.trim(), name: name.value, school: school.value, seats: +seats.value, note: note.value }); close(); });
  d.append(h('div', { class: 'in' }, h('h2', { id: 'tf-t' }, title),
    t ? null : h('p', { class: 'small muted' }, 'חשבון Google של המורה. אחרי ההוספה המורה נכנס/ת מתוך DSViz ("כניסת מורה") ורואה את הקוד שלו.'),
    h('label', { class: 'lbl', for: 'tf-e' }, 'חשבון Google', email), h('label', { class: 'lbl', for: 'tf-n' }, 'שם', name),
    h('label', { class: 'lbl', for: 'tf-s' }, 'בית ספר', school), t ? null : h('label', { class: 'lbl', for: 'tf-c' }, 'מספר עמדות', seats),
    h('label', { class: 'lbl', for: 'tf-o' }, 'הערה (רק להנהלה)', note),
    h('div', { class: 'row' }, ok, h('button', { type: 'button', class: 'btn', onclick: close }, 'ביטול'))));
  d.addEventListener('cancel', e => { e.preventDefault(); close(); });
  document.body.append(d);
  d.showModal();
  (t ? name : email).focus();
}

function addTeacher() {
  teacherForm('הוספת מורה', null, async v => {
    await api('POST', '/v1/admin/teacher', Object.assign({ create: true }, v));
    cache.teachers = null;
    toast('המורה נוסף/ה');
    location.hash = '#teacher=' + encodeURIComponent(v.email.toLowerCase());
    render();
  });
}
function editTeacher(t) {
  teacherForm('עריכת פרטים', t, async v => {
    await api('POST', '/v1/admin/teacher', { email: t.email, name: v.name, school: v.school, note: v.note });
    cache.teachers = null;
    toast('נשמר');
    render();
  });
}

// ------------------------------------------------------------------ תלמידים ותשלומים

let studentFilter = 'open';

async function students(arg) {
  if (arg === 'payments') return payments();
  const r = await api('GET', '/v1/admin/students');
  cache.students = r.students;
  const all = r.students;
  const groups = { open: ['עם גישה', s => s.open], manual: ['הענקה ידנית', s => s.open && s.source === 'manual'], unpaid: ['נכנסו ועוד לא שילמו', s => !s.open], all: ['הכול', () => true] };
  const shown = all.filter(groups[studentFilter][1]);
  const sourcePill = s => s.open ? (s.source === 'manual' ? h('span', { class: 'pill purple' }, 'הענקה ידנית') : h('span', { class: 'pill green' }, 'תשלום' + (s.paidAt ? ' · ' + fmtDate(s.paidAt) : '')))
    : s.source === 'refunded' ? h('span', { class: 'pill gray' }, 'הוחזר') : s.source === 'revoked' ? h('span', { class: 'pill gray' }, 'בוטל') : h('span', { class: 'pill gray' }, 'לא שילם/ה');
  const table = h('div', { class: 'tablewrap' }, h('table', { class: 't' },
    h('thead', null, h('tr', null, ['תלמיד', 'גישה עד', 'איך', 'כניסה אחרונה', ''].map(x => h('th', null, x)))),
    h('tbody', null, shown.length ? shown.slice(0, 400).map(s => h('tr', null,
      h('td', null, h('div', { class: 'cellname' }, h('b', null, s.name || '—'), h('span', { class: 'ltr' }, s.email))),
      h('td', null, s.open ? fmtDate(s.until) : '—'), h('td', null, sourcePill(s)), h('td', { class: 'muted' }, s.lastSignIn ? ago(s.lastSignIn) : '—'),
      h('td', null, h('button', { type: 'button', class: 'btn sm', onclick: () => studentDialog(s.email) }, 'פרטים'))))
      : h('tr', null, h('td', { colspan: '5' }, h('div', { class: 'empty' }, 'אין תלמידים בקבוצה הזו.'))))));
  if (arg && arg.includes('@')) setTimeout(() => studentDialog(arg), 0);
  return h('div', { class: 'split' },
    h('div', { class: 'grow stack', style: { gap: '16px' } },
      h('h1', null, 'תלמידים ותשלומים'),
      h('div', { class: 'tabs', role: 'tablist' }, h('button', { type: 'button', role: 'tab', 'aria-selected': 'true' }, `תלמידים · ${all.filter(s => s.open).length}`),
        h('button', { type: 'button', role: 'tab', 'aria-selected': 'false', onclick: () => { location.hash = '#students=payments'; } }, 'תשלומים')),
      h('div', { class: 'filters' }, Object.entries(groups).map(([id, [label, fn]]) => h('button', { type: 'button', 'aria-pressed': String(studentFilter === id), onclick: () => { studentFilter = id; render(); } }, `${label} · ${all.filter(fn).length}`))),
      table, shown.length > 400 ? h('p', { class: 'small muted' }, `מוצגים 400 מתוך ${shown.length} — משתמשים בחיפוש למעלה.`) : null),
    grantPanel());
}

function grantPanel() {
  const emails = h('textarea', { class: 'field ltr', rows: '3', id: 'g-e', placeholder: 'name@gmail.com', style: { textAlign: 'left' } });
  const until = h('input', { class: 'field', type: 'date', id: 'g-u', value: (settingsCache && settingsCache.accessUntil) || '' });
  const note = h('input', { class: 'field', type: 'text', id: 'g-n', placeholder: 'למשל: הסכם עם תיכון הדר' });
  const go = h('button', { type: 'button', class: 'btn primary lg' }, 'הענקת גישה');
  go.onclick = () => busy(go, async () => {
    const list = emails.value.split(/[\s,;]+/).filter(Boolean);
    if (!list.length) { emails.focus(); throw new Error('כתבו לפחות כתובת אחת.'); }
    const r = await api('POST', '/v1/admin/student', { emails: list, until: until.value, note: note.value });
    toast(r.count === 1 ? 'הגישה ניתנה' : `הגישה ניתנה ל-${r.count} תלמידים`);
    render();
  });
  if (!settingsCache) api('GET', '/v1/admin/settings').then(r => { settingsCache = r.settings; until.value = settingsCache.accessUntil; }).catch(() => {});
  return h('aside', { class: 'drawer card', 'aria-label': 'הענקת גישה' }, h('h2', null, 'הענקת גישה בלי תשלום'),
    h('p', { class: 'small muted', style: { lineHeight: '1.55' } }, 'לתלמיד שקיבל הנחה מלאה, להסכם עם בית ספר, או כשהתשלום עבר בדרך אחרת. התלמיד נכנס עם אותו חשבון Google — והמאגר פתוח.'),
    h('label', { class: 'lbl', for: 'g-e' }, 'חשבונות Google (כתובת בכל שורה)', emails), h('label', { class: 'lbl', for: 'g-u' }, 'פתוח עד', until),
    h('label', { class: 'lbl', for: 'g-n' }, 'סיבה (נשמרת ביומן)', note), go);
}

async function studentDialog(email) {
  const r = await api('GET', '/v1/admin/student?email=' + encodeURIComponent(email));
  const d = h('dialog', { class: 'modal', 'aria-labelledby': 'sd-t', style: { width: 'min(560px, calc(100vw - 32px))' } });
  const close = () => { d.close(); d.remove(); };
  const s = r.student || {};
  const open = s.until && s.until >= new Date().toISOString().slice(0, 10);
  const extend = h('button', { type: 'button', class: 'btn primary' }, open ? 'הארכה' : 'הענקת גישה');
  extend.onclick = () => busy(extend, async () => {
    await api('POST', '/v1/admin/student', { email, until: settingsCache ? settingsCache.accessUntil : s.until, note: 'מתוך פרטי התלמיד' });
    toast('הגישה עודכנה'); close(); render();
  });
  const revoke = h('button', { type: 'button', class: 'btn danger' }, 'ביטול הגישה');
  revoke.onclick = async () => {
    if (!await ask({ title: 'לבטל את הגישה?', text: 'המאגר ננעל אצל התלמיד בבדיקה הבאה. תשלומים לא מוחזרים מכאן — החזר עושים בשירות התשלום.', ok: 'ביטול הגישה', danger: true })) return;
    busy(revoke, async () => { await api('POST', '/v1/admin/student', { email, mode: 'revoke' }); toast('הגישה בוטלה'); close(); render(); });
  };
  d.append(h('div', { class: 'in' },
    h('div', { class: 'row', style: { justifyContent: 'space-between' } }, h('h2', { id: 'sd-t' }, s.name || email), h('button', { type: 'button', class: 'btn sm', 'aria-label': 'סגירה', onclick: close }, icon(ICONS.x, 16))),
    h('div', { class: 'small muted ltr', style: { textAlign: 'right' } }, email),
    h('div', { class: 'row' }, open ? h('span', { class: 'pill green' }, `פתוח עד ${fmtDate(s.until)}`) : h('span', { class: 'pill gray' }, 'סגור'), s.source === 'manual' ? h('span', { class: 'pill purple' }, 'הענקה ידנית') : null),
    s.note ? h('div', { class: 'note' }, s.note) : null,
    h('h3', null, 'תשלומים'),
    h('div', { class: 'list' }, r.payments.length ? r.payments.map(p => h('div', { class: 'item' }, h('div', { class: 'grow' }, `${p.amount} ₪ · ${fmtDate(p.paidAt || p.createdAt)}`, p.payerName ? ` · ${p.payerName}` : '', p.payerPhone ? h('span', { class: 'ltr' }, ' · ' + p.payerPhone) : null, p.ref ? h('span', { class: 'small muted ltr' }, ' ' + p.ref) : null),
      h('span', { class: 'pill ' + (p.status === 'paid' ? 'green' : 'gray') }, ({ paid: 'שולם', refunded: 'הוחזר', pending: 'לא הושלם', failed: 'נכשל', mismatch: 'סכום לא תואם' })[p.status] || p.status)))
      : h('div', { class: 'empty' }, 'אין תשלומים.')),
    h('h3', null, 'מחשבים מחוברים'),
    h('div', { class: 'list' }, r.devices.length ? r.devices.map(x => {
      const b = h('button', { type: 'button', class: 'btn sm' }, 'ניתוק');
      b.onclick = () => busy(b, async () => { await api('POST', '/v1/admin/session/revoke', { id: x.id }); b.closest('.item').remove(); toast('המחשב נותק'); });
      return h('div', { class: 'item' }, h('div', { class: 'grow' }, h('div', { style: { fontWeight: 600 } }, x.name || 'מחשב'), h('div', { class: 'small muted' }, 'פעיל ' + ago(x.lastSeen))), b);
    }) : h('div', { class: 'empty' }, 'אין מחשבים מחוברים.')),
    h('div', { class: 'row' }, extend, open ? revoke : null)));
  d.addEventListener('cancel', e => { e.preventDefault(); close(); });
  document.body.append(d);
  d.showModal();
}

async function payments() {
  const r = await api('GET', '/v1/admin/payments');
  const paid = r.payments.filter(p => p.status === 'paid');
  const total = paid.reduce((n, p) => n + (+p.amount || 0), 0);
  const label = { paid: ['שולם', 'green'], refunded: ['הוחזר', 'gray'], pending: ['ממתין', 'orange'], failed: ['נכשל', 'gray'], mismatch: ['סכום לא תואם', 'orange'] };
  return h('div', { class: 'stack', style: { gap: '16px' } },
    h('div', { class: 'titlebar' }, h('h1', null, 'תלמידים ותשלומים'), h('span', { class: 'small muted' }, `${paid.length} תשלומים · ${total.toLocaleString('he-IL')} ₪ (ברשימה למטה)`)),
    h('div', { class: 'tabs', role: 'tablist' }, h('button', { type: 'button', role: 'tab', 'aria-selected': 'false', onclick: () => { location.hash = '#students'; } }, 'תלמידים'),
      h('button', { type: 'button', role: 'tab', 'aria-selected': 'true' }, `תשלומים · ${r.payments.length}`)),
    h('div', { class: 'tablewrap' }, h('table', { class: 't', style: { minWidth: '900px' } },
      h('thead', null, h('tr', null, ['תאריך', 'מה נקנה', 'חשבון', 'המשלם', 'טלפון', 'סכום', 'מצב', 'אסמכתה'].map(x => h('th', null, x)))),
      h('tbody', null, r.payments.length ? r.payments.map(p => h('tr', null,
        h('td', null, fmtDate(p.paidAt || p.createdAt)),
        h('td', null, p.kind === 'seats' ? h('span', { class: 'pill blue' }, `חבילת ${p.seats} עמדות`) : h('span', { class: 'pill gray' }, 'מאגר לתלמיד')),
        h('td', null, h('div', { class: 'cellname' }, h('b', null, p.name || '—'), h('span', { class: 'ltr' }, p.email))),
        h('td', null, h('div', { class: 'cellname' }, h('b', null, p.payerName || '—'), p.payerEmail && p.payerEmail !== p.email ? h('span', { class: 'ltr' }, p.payerEmail) : null)),
        h('td', { class: 'ltr' }, p.payerPhone ? h('a', { href: 'tel:' + p.payerPhone.replace(/[^0-9+]/g, '') }, p.payerPhone) : '—'),
        h('td', null, `${p.amount} ₪`), h('td', null, h('span', { class: 'pill ' + (label[p.status] || ['', 'gray'])[1] }, (label[p.status] || [p.status])[0])),
        h('td', { class: 'small muted ltr' }, p.ref || '')))
        : h('tr', null, h('td', { colspan: '8' }, h('div', { class: 'empty' }, 'עוד אין תשלומים.')))))));
}

// ------------------------------------------------------------------ יומן

async function logView() {
  const r = await api('GET', '/v1/admin/log?limit=150');
  const box = h('section', { class: 'card' }, feed(r.log));
  let last = r.log.length ? r.log[r.log.length - 1].at : '';
  const more = h('button', { type: 'button', class: 'btn', hidden: r.log.length < 150 }, 'עוד');
  more.onclick = () => busy(more, async () => {
    const n = await api('GET', '/v1/admin/log?limit=150&before=' + encodeURIComponent(last));
    if (n.log.length) { box.append(feed(n.log)); last = n.log[n.log.length - 1].at; }
    more.hidden = n.log.length < 150;
  });
  return [h('h1', null, 'יומן פעולות'), h('p', { class: 'muted' }, 'כל מה שקרה במערכת: כניסות, עמדות, בקשות, תשלומים, ומה שעשו המנהלים.'), box, more];
}

// ------------------------------------------------------------------ הגדרות

async function settingsView() {
  const r = await api('GET', '/v1/admin/settings');
  settingsCache = r.settings;
  const s = r.settings;
  const num = (id, label, v, min, max) => {
    const f = h('input', { class: 'field', type: 'number', id: 's-' + id, value: String(v), min: String(min), max: String(max) });
    f.dataset.key = id;
    return h('label', { class: 'lbl', for: 's-' + id }, label, f);
  };
  const gem = h('input', { type: 'checkbox', role: 'switch', id: 's-gem', checked: s.requireLoginForGemini });
  const gemLabel = h('span', null, s.requireLoginForGemini ? 'דולק' : 'כבוי');
  gem.addEventListener('change', () => { gemLabel.textContent = gem.checked ? 'דולק' : 'כבוי'; });
  const until = h('input', { class: 'field', type: 'date', id: 's-until', value: s.accessUntil });
  const form = h('div', { class: 'stack', style: { gap: '18px', maxWidth: '900px' } },
    h('section', { class: 'card' },
      h('div', { class: 'row', style: { alignItems: 'flex-start', gap: '16px' } },
        h('div', { class: 'grow stack', style: { gap: '6px' } }, h('h2', null, 'שליחת תרגיל ל-Gemini — רק למחוברים'),
          h('p', { class: 'muted', style: { lineHeight: '1.55' } }, 'כשהמתג דולק, רק מורה, עמדת מחשב מחוברת ותלמיד ששילם יכולים לשלוח תרגיל ולקבל הצעה לפתרון. כשהוא כבוי — כמו היום, כל מי שמותקן אצלו DSViz.')),
        h('label', { class: 'switch', for: 's-gem' }, h('span', { class: 'sr' }, 'רק למחוברים'), gem, h('span', { class: 'track', 'aria-hidden': 'true' }, h('span', { class: 'knob' })), gemLabel)),
      h('div', { class: 'warn-box' }, 'מדליקים רק אחרי שכל המחשבים עודכנו לגרסה עם ההתחברות — מחשב עם גרסה ישנה יקבל ״צריך להתחבר / לעדכן את DSViz״.'),
      h('div', { class: 'form3' }, num('geminiTeacher', 'שליחות ביום — מורה', s.geminiTeacher, 0, 1000), num('geminiStation', 'שליחות ביום — עמדה', s.geminiStation, 0, 1000), num('geminiStudent', 'שליחות ביום — תלמיד', s.geminiStudent, 0, 1000))),
    h('section', { class: 'card' }, h('h2', null, 'תשלום תלמידים'),
      h('div', { class: 'form3' }, num('price', 'מחיר (₪) — 0 = עוד לא פתוח', s.price, 0, 5000),
        h('label', { class: 'lbl', for: 's-until' }, 'תשלום פותח את המאגר עד', until),
        h('div', { class: 'lbl' }, 'שירות התשלום', h('span', { class: 'field', style: { display: 'flex', alignItems: 'center', background: 'var(--bg)', borderColor: 'var(--bg)', fontWeight: 600, color: 'var(--muted)' } }, r.provider || 'עוד לא נבחר')))),
    h('section', { class: 'card' }, h('h2', null, 'חבילות עמדות למורים'),
      h('p', { class: 'small muted' }, 'המורה קונה חבילה בלוח שלו, והעמדות מתווספות לו מיד אחרי שהתשלום מאושר. מחיר 0 — החבילה לא מוצגת.'),
      h('div', { class: 'form3' }, (s.packages || []).map((p, i) => h('div', { class: 'stack', style: { gap: '8px', background: 'var(--bg)', borderRadius: '12px', padding: '12px' } },
        h('label', { class: 'lbl', for: 'pk-s' + i }, 'עמדות', h('input', { class: 'field', type: 'number', min: '1', max: '500', id: 'pk-s' + i, value: String(p.seats), 'data-pk': i, 'data-f': 'seats' })),
        h('label', { class: 'lbl', for: 'pk-p' + i }, 'מחיר (₪)', h('input', { class: 'field', type: 'number', min: '0', max: '20000', id: 'pk-p' + i, value: String(p.price), 'data-pk': i, 'data-f': 'price' })))))),
    h('section', { class: 'card' }, h('h2', null, 'עמדות ומחשבים'),
      h('div', { class: 'form2' }, num('defaultSeats', 'עמדות חינם למורה חדש', s.defaultSeats, 0, 2000), num('offlineDays', 'ימים בלי אינטרנט (0 = בלי הגבלה)', s.offlineDays, 0, 365),
        num('studentDevices', 'מחשבים לכל תלמיד', s.studentDevices, 1, 20), num('teacherDevices', 'מחשבים לכל מורה', s.teacherDevices, 1, 50))),
    h('section', { class: 'card' }, h('h2', null, 'מנהלים'),
      h('div', { class: 'stack' }, r.admins.map((a, i) => h('div', { class: 'row' }, h('span', { class: 'ltr', style: { fontWeight: 600 } }, a), i === 0 ? h('span', { class: 'pill blue' }, 'מנהל ראשי') : null))),
      h('p', { class: 'small muted' }, 'רשימת המנהלים נקבעת בהגדרות השרת (ADMIN_EMAILS), כדי שאף אחד לא יוכל להוסיף את עצמו מכאן.'),
      r.passKey ? null : h('div', { class: 'warn-box' }, 'חסר מפתח החתימה (PASS_KEY) בשרת — בלי זה המאגר לא נפתח בלי אינטרנט.')));
  const save = h('button', { type: 'button', class: 'btn primary lg' }, 'שמירת השינויים');
  save.onclick = () => busy(save, async () => {
    const body = { requireLoginForGemini: gem.checked, accessUntil: until.value };
    for (const f of form.querySelectorAll('input[data-key]')) body[f.dataset.key] = +f.value;
    body.packages = (s.packages || []).map((p, i) => ({ seats: +form.querySelector(`[data-pk="${i}"][data-f="seats"]`).value, price: +form.querySelector(`[data-pk="${i}"][data-f="price"]`).value }));
    if (gem.checked && !s.requireLoginForGemini && !await ask({ title: 'להדליק "רק למחוברים"?', text: 'מעכשיו מי שלא מחובר (או שהגרסה אצלו ישנה) לא יוכל לשלוח תרגיל ל-Gemini.', ok: 'הדלקה' })) return;
    await api('POST', '/v1/admin/settings', body);
    toast('נשמר — נכנס לתוקף תוך דקה');
    render();
  });
  return [h('h1', null, 'הגדרות'), form, h('div', { class: 'row' }, save, h('span', { class: 'small muted' }, 'שינוי נכנס לתוקף תוך דקה.'))];
}

// ------------------------------------------------------------------ התחלה

async function main() {
  try { await boot(); } catch (e) { app.replaceChildren(h('div', { class: 'loading' }, 'הכניסה עוד לא פתוחה בשרת (ACCESS).')); return; }
  user = await currentUser();
  const signin = msg => {
    const b = h('button', { type: 'button', class: 'gbtn' }, googleLogo(), 'התחברות עם Google');
    b.onclick = () => busy(b, async () => { user = await googleSignIn(); start(); });
    app.replaceChildren(h('main', { class: 'center', style: { minHeight: '100vh' } }, h('div', { class: 'panel' },
      h('div', { class: 'logo dark', style: { width: '48px', height: '48px', borderRadius: '12px' } }, icon(ICONS.shield, 26, '#fff')),
      h('h1', null, 'מסוף ההנהלה'), h('p', { class: 'muted' }, msg || 'נכנסים עם חשבון Google של ההנהלה.'), b,
      user ? h('a', { href: '#', class: 'small', onclick: async e => { e.preventDefault(); await logoutWeb(); user = null; signin(); } }, 'כניסה עם חשבון אחר') : null)));
  };
  const start = async () => {
    try {
      await api('GET', '/v1/admin/settings').then(r => { settingsCache = r.settings; });
    } catch (e) {
      return signin(e.code === 'admin' ? `החשבון ${user.email} לא מוגדר כמנהל.` : e.message);
    }
    window.addEventListener('hashchange', render);
    render();
  };
  if (!user) return signin();
  start();
}
main();
