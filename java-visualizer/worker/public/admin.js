// /admin — מסוף ההנהלה: סקירה, בקשות, מורים ועמדות, תלמידים ותשלומים, יומן פעולות, הגדרות.
// רק כתובות שב-ADMIN_EMAILS (בהגדרות השרת). כל פעולה נבדקת בשרת ונרשמת ביומן.
import { boot, currentUser, googleSignIn, api, h, icon, ICONS, googleLogo, avatar, fmtDate, ago, daysSince, busy, toast, ask, logoutWeb } from '/static/common.js';

const app = document.getElementById('app');
let user = null;
let settingsCache = null;
let openCount = 0;
let alertsUnseen = 0;
let alertTimer = null;
const cache = { teachers: null, students: null };

const NAV = [
  ['overview', 'סקירה', ICONS.grid],
  ['requests', 'בקשות', ICONS.msg],
  ['teachers', 'מורים ועמדות', ICONS.user],
  ['rooms', 'עמדות ותלמידי מורים', ICONS.monitor],
  ['students', 'תלמידים ותשלומים', ICONS.cap],
  ['reports', 'דיווחים על בעיות', ICONS.bell],
  ['log', 'יומן פעולות', ICONS.list],
  ['settings', 'הגדרות', ICONS.gear],
  ['check', 'בדיקת מערכת', ICONS.shield],
];

const ACTIONS = {
  'signin.teacher': 'כניסת מורה', 'signin.student': 'כניסת תלמיד', 'station.claim': 'עמדה חדשה חוברה', 'station.again': 'עמדה התחברה מחדש',
  'station.bad-code': 'ניסיון חיבור עם קוד מורה שגוי', 'station.release': 'עמדה שוחררה', 'station.rename': 'שם של מחשב שונה', 'teacher.add': 'מורה נוסף', 'teacher.update': 'שינוי אצל מורה',
  'teacher.new-code': 'קוד מורה חדש', 'request.teacher': 'בקשת הרשאת מורה', 'request.seats': 'בקשת עמדות נוספות', 'request.release': 'בקשה לשחרר מחשב',
  'request.decline': 'בקשה נדחתה', 'student.grant': 'הענקת גישה לתלמיד', 'student.assign': 'תלמיד שויך למורה', 'student.unassign': 'בוטל שיוך של תלמיד למורה', 'student.revoke': 'ביטול גישה לתלמיד', 'session.revoke': 'מחשב נותק',
  'homework.create': 'מורה נתן שיעורי בית', 'homework.delete': 'מורה מחק שיעורי בית',
  'payment.paid': 'תשלום התקבל', 'payment.seats': 'חבילת עמדות נקנתה', 'payment.refunded': 'החזר כספי', 'payment.failed': 'תשלום נכשל', settings: 'שינוי בהגדרות',
};
const dotFor = a => (a.startsWith('payment.') ? 'green' : a === 'station.bad-code' ? 'warn' : /^(teacher|student|settings|session|request\.decline)/.test(a) ? 'dark' : '');
const REQ_KIND = { teacher: ['הרשאת מורה', 'blue'], seats: ['תוספת עמדות', 'orange'], release: ['שחרור מחשב', 'purple'] };

// ------------------------------------------------------------------ סדר לפי זמן (נזכר בדפדפן הזה)

const ORDER_KEY = 'dsviz.admin.order';
const ORDER = (() => { try { return JSON.parse(localStorage.getItem(ORDER_KEY) || '{}') || {}; } catch (e) { return {}; } })();
const orderOf = (key, def = 'desc') => (['asc', 'desc', 'none'].includes(ORDER[key]) ? ORDER[key] : def);
function setOrder(key, v) {
  ORDER[key] = v;
  try { localStorage.setItem(ORDER_KEY, JSON.stringify(ORDER)); } catch (e) { /* בלי זיכרון — רק לביקור הזה */ }
}
/** מיון לפי שדה זמן (ISO). בלי ערך — תמיד בסוף */
function byTime(list, field, dir) {
  if (dir !== 'asc' && dir !== 'desc') return list.slice();
  return list.slice().sort((a, b) => {
    const x = a[field] || '', y = b[field] || '';
    if (!x || !y) return x ? -1 : y ? 1 : 0;
    return dir === 'asc' ? String(x).localeCompare(String(y)) : String(y).localeCompare(String(x));
  });
}
/** כפתורי "החדש קודם / הישן קודם". onChange(dir) מצייר מחדש את הרשימה */
function orderToggle(key, onChange, def = 'desc') {
  const box = h('div', { class: 'order', role: 'group', 'aria-label': 'סדר לפי זמן' });
  const btns = [['desc', 'החדש קודם'], ['asc', 'הישן קודם']].map(([v, label]) => {
    const b = h('button', { type: 'button', 'aria-pressed': String(orderOf(key, def) === v) }, label);
    b.onclick = () => {
      if (orderOf(key, def) === v) return;
      setOrder(key, v);
      btns.forEach(([bv, bb]) => bb.setAttribute('aria-pressed', String(bv === v)));
      onChange(v);
    };
    return [v, b];
  });
  box.append(icon(ICONS.clock, 15, '#5A6478'), ...btns.map(x => x[1]));
  return box;
}
/** כותרת עמודה שממיינת לפי זמן: לחיצה — החדש קודם, שוב — הישן קודם, שוב — בלי מיון */
function timeHeader(label, key, onChange) {
  const cur = orderOf(key, 'none');
  const arrow = cur === 'desc' ? ' ↓' : cur === 'asc' ? ' ↑' : '';
  const b = h('button', { type: 'button', class: 'thsort' + (cur !== 'none' ? ' on' : ''), title: 'מיון לפי זמן', 'aria-label': `${label} — ${cur === 'desc' ? 'החדש קודם' : cur === 'asc' ? 'הישן קודם' : 'בלי מיון'}` }, label, arrow);
  b.onclick = () => { setOrder(key, cur === 'none' ? 'desc' : cur === 'desc' ? 'asc' : 'none'); onChange(); };
  return h('th', { 'aria-sort': cur === 'desc' ? 'descending' : cur === 'asc' ? 'ascending' : 'none' }, b);
}
/** תאריך ושעה מלאים: 10.11.2026 14:05 */
function fmtWhen(iso) {
  const d = new Date(iso);
  if (!iso || isNaN(d)) return '';
  return `${fmtDate(iso)} ${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`;
}

// ------------------------------------------------------------------ מסגרת

function route() {
  const m = /^#([a-z]+)(?:=(.*))?$/.exec(location.hash || '');
  return m ? { page: m[1], arg: decodeURIComponent(m[2] || '') } : { page: 'overview', arg: '' };
}

function shell(content) {
  const r = route();
  const nav = h('nav', { class: 'side', 'aria-label': 'תפריט' },
    h('div', { class: 'brand' }, h('div', { class: 'logo dark' }, icon(ICONS.shield, 19, '#fff')), h('div', null, 'מסוף ההנהלה', h('small', null, 'DSViz · מאגר תרגילים'))),
    h('div', { class: 'nav', style: { display: 'flex', flexDirection: 'column', gap: '4px' } }, NAV.map(([id, label, ic]) => h('a', { href: '#' + id, 'aria-current': r.page === id || (id === 'teachers' && r.page === 'teacher') || (id === 'reports' && r.page === 'report') ? 'page' : null },
      icon(ic, 18), h('span', null, label), id === 'requests' && openCount ? h('span', { class: 'count', 'aria-label': openCount + ' פתוחות' }, String(openCount)) : null))));
  const header = h('header', null, searchBox(), h('div', { class: 'grow' }), bell(),
    h('div', { class: 'who' }, h('div', { class: 'meta' }, h('span', { class: 'ltr' }, user.email),
      h('a', { href: '#', onclick: async e => { e.preventDefault(); await logoutWeb(); location.reload(); } }, 'התנתקות')), avatar(user, true)));
  app.replaceChildren(h('div', { class: 'shell' }, nav, h('div', { class: 'main' }, header, h('main', { class: 'content', id: 'content' }, content))));
  // בטלפון התפריט הוא שורה שנגללת לצד — העמוד הנוכחי תמיד נראה
  const cur = nav.querySelector('a[aria-current="page"]');
  if (cur && nav.scrollWidth > nav.clientWidth + 4 && cur.scrollIntoView) cur.scrollIntoView({ inline: 'center', block: 'nearest' });
}

function searchBox() {
  const input = h('input', { type: 'search', placeholder: 'חיפוש מורה, כיתה, תלמיד, קוד מורה או מחשב', 'aria-label': 'חיפוש', autocomplete: 'off' });
  const results = h('div', { class: 'results', hidden: true, role: 'listbox' });
  const box = h('label', { class: 'search' }, icon(ICONS.search, 18, '#5A6478'), input, results);
  let loading = null;
  const ensure = () => loading || (loading = Promise.all([
    cache.teachers ? cache.teachers : api('GET', '/v1/admin/teachers').then(r => (cache.teachers = r.teachers)),
    cache.students ? cache.students : api('GET', '/v1/admin/students').then(r => (cache.students = r.students)),
    api('GET', '/v1/admin/rooms'),
  ]));
  input.addEventListener('focus', () => ensure().catch(() => {}));
  input.addEventListener('input', async () => {
    const q = input.value.trim().toLowerCase().replace(/-/g, '');
    if (q.length < 2) { results.hidden = true; return; }
    const [teachers, students, rooms] = await ensure();
    const has = (...parts) => parts.join(' ').toLowerCase().includes(q);
    const tname = new Map(teachers.map(t => [t.email, t.name || t.email]));
    // [סוג, כותרת, תיאור בעברית, קישור, מייל (בכיוון שמאל-לימין)]
    const hits = [];
    // מורים — לפי שם, מייל, בית ספר, טלפון וקוד
    for (const t of teachers) if (has(t.name, t.email, t.school, t.phone || '', t.code.replace('-', ''))) hits.push(['מורה', t.name || t.email, t.school || '', '#teacher=' + encodeURIComponent(t.email), t.email]);
    // כיתות — של כל המורים; פותחות את תלמידי הכיתה
    for (const c of rooms.classes) if (has(c.name)) hits.push(['כיתה', c.name, `${tname.get(c.teacher) || c.teacher} · ${c.students} תלמידים`, roomsHash('students', c.teacher, c.id), '']);
    // עמדות מעבדה
    for (const x of rooms.stations) if (has(x.name)) hits.push(['מחשב', x.name, x.teacherName || tname.get(x.teacher) || x.teacher, roomsHash('stations', x.teacher), '']);
    // תלמידים: מי שמשויך למורה — בעמוד של המורה (עם הכיתה); עצמאי — ברשימת התלמידים
    const attached = new Map(rooms.students.map(x => [x.email, x]));
    for (const s of students) if (has(s.name, s.email, s.phone || '')) {
      const a = attached.get(s.email);
      const cn = a && a.classId ? (rooms.classes.find(c => c.id === a.classId) || {}).name : '';
      hits.push(a ? ['תלמיד', s.name || s.email, ['המורה: ' + (a.teacherName || a.teacher), cn].filter(Boolean).join(' · '), roomsHash('students', a.teacher, a.classId, a.email), s.email]
        : ['תלמיד', s.name || s.email, 'עצמאי', '#students=' + encodeURIComponent(s.email), s.email]);
    }
    results.replaceChildren(...(hits.length ? hits.slice(0, 20).map(([kind, title, sub, href, mail]) => h('button', { type: 'button', role: 'option', onclick: () => { results.hidden = true; input.value = ''; location.hash = href; } },
      h('span', { class: 'pill gray' }, kind), h('span', { class: 'grow' }, h('b', null, title), sub ? [' ', h('span', { class: 'small muted' }, '· ' + sub)] : null, mail ? [' ', h('span', { class: 'small muted ltr' }, mail)] : null)))
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
    const views = { overview, requests, teachers, teacher: teachers, rooms: roomsView, students, reports: reportsView, report: reportView, log: logView, settings: settingsView, check: checkView };
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

// ------------------------------------------------------------------ התראות: מורה / תלמיד שהצטרפו, בקשת הרשאה, תשלום (עיגול אדום עד שפותחים)

const ALERT_DOT = { 'student.new': 'blue', 'teacher.first': 'blue', 'teacher.request': 'warn', 'payment.paid': 'green', 'payment.seats': 'green' };

function bell() {
  const dot = h('span', { class: 'bell-dot', hidden: !alertsUnseen }, alertsUnseen > 99 ? '99+' : String(alertsUnseen || ''));
  const btn = h('button', { type: 'button', class: 'bell', id: 'bell', 'aria-haspopup': 'dialog', 'aria-expanded': 'false', 'aria-label': alertsUnseen ? `התראות — ${alertsUnseen} חדשות` : 'התראות', title: 'התראות' },
    icon(ICONS.bell, 21, '#17213A'), dot);
  const panel = h('div', { class: 'alerts', hidden: true, role: 'dialog', 'aria-label': 'התראות' });
  const wrap = h('div', { class: 'bell-wrap' }, btn, panel);
  const close = () => { panel.hidden = true; btn.setAttribute('aria-expanded', 'false'); };
  btn.onclick = async () => {
    if (!panel.hidden) return close();
    panel.hidden = false;
    btn.setAttribute('aria-expanded', 'true');
    panel.replaceChildren(h('div', { class: 'empty' }, 'טוען…'));
    try {
      const r = await api('GET', '/v1/admin/alerts?limit=30');
      const item = a => h('button', { type: 'button', class: 'alert-item' + (a.seen ? '' : ' new'), onclick: () => { close(); location.hash = a.go; } },
        h('span', { class: 'dot ' + (ALERT_DOT[a.kind] || '') }),
        h('span', { class: 'grow stack', style: { gap: '2px', textAlign: 'right' } },
          h('b', null, a.title),
          h('span', null, a.name || '', a.name ? ' · ' : '', h('span', { class: 'ltr muted' }, a.email)),
          a.detail ? h('span', { class: 'small muted' }, a.detail) : null,
          h('span', { class: 'small muted' }, h('time', { datetime: a.at, title: fmtWhen(a.at) }, ago(a.at)))));
      panel.replaceChildren(
        h('div', { class: 'alerts-head' }, h('b', null, 'התראות'), h('span', { class: 'small muted' }, r.unseen ? `${r.unseen} חדשות` : 'אין חדשות')),
        r.alerts.length ? h('div', { class: 'alerts-list' }, r.alerts.map(item)) : h('div', { class: 'empty' }, 'עוד אין התראות. כשמורה או תלמיד מצטרפים — זה יופיע כאן.'));
      // פתיחת הרשימה = נקראו (מה שהיה חדש נשאר מסומן ברשימה הפתוחה)
      if (r.unseen) { await api('POST', '/v1/admin/alerts/seen', { all: true }); setAlerts(0); }
    } catch (e) {
      panel.replaceChildren(h('div', { class: 'warn-box' }, e.message || String(e)));
    }
  };
  document.addEventListener('click', e => { if (!wrap.contains(e.target)) close(); });
  wrap.addEventListener('keydown', e => { if (e.key === 'Escape') { close(); btn.focus(); } });
  return wrap;
}

/** מעדכן את העיגול האדום ואת כותרת הלשונית */
function setAlerts(n) {
  alertsUnseen = n;
  const btn = document.getElementById('bell');
  if (btn) {
    const dot = btn.querySelector('.bell-dot');
    dot.hidden = !n;
    dot.textContent = n > 99 ? '99+' : String(n || '');
    btn.setAttribute('aria-label', n ? `התראות — ${n} חדשות` : 'התראות');
  }
  document.title = (n ? `(${n}) ` : '') + 'מסוף ההנהלה';
}

/** בודק התראות חדשות כל דקה, כל עוד המסוף פתוח */
function watchAlerts() {
  const tick = () => api('GET', '/v1/admin/alerts?limit=1').then(r => setAlerts(r.unseen)).catch(() => {});
  tick();
  clearInterval(alertTimer);
  alertTimer = setInterval(() => { if (!document.hidden) tick(); }, 60000);
  document.addEventListener('visibilitychange', () => { if (!document.hidden) tick(); });
}

// ------------------------------------------------------------------ סקירה

async function overview() {
  const o = await api('GET', '/v1/admin/overview');
  refreshCount(o.counts.openRequests);
  setAlerts(o.counts.alertsUnseen || 0);
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
      c.studentsFree
        ? kpi('#students', 'תלמידים (בחינם)', h('b', { class: 'v' }, String(c.studentsAll)), `${c.studentsRegistered} נרשמו · ${c.studentsWithTeacher} משויכים למורה · ${c.studentsSolo} עצמאיים`)
        : kpi('#students', 'תלמידים עם גישה', h('b', { class: 'v' }, String(c.students)), `${c.studentsPaid} בתשלום · ${c.studentsManual} הענקה ידנית`),
      kpi('#students=payments', 'תשלומים החודש', h('b', { class: 'v' }, String(c.paymentsMonth)), c.refundsMonth ? `${c.refundsMonth} הוחזרו` : '')),
    h('div', { class: 'two' },
      h('section', { class: 'card' }, h('div', { class: 'row', style: { justifyContent: 'space-between' } }, h('h2', null, 'דורש טיפול'), h('a', { href: '#requests', class: 'small', style: { fontWeight: 600 } }, 'כל הבקשות')),
        attention.length ? attention : h('div', { class: 'empty' }, 'אין כרגע שום דבר שמחכה לך.')),
      recentCard(o.recent)),
  ];
}

function recentCard(recent) {
  const body = h('div', null, feed(byTime(recent, 'at', orderOf('overview.recent'))));
  const toggle = orderToggle('overview.recent', dir => body.replaceChildren(feed(byTime(recent, 'at', dir))));
  return h('section', { class: 'card' },
    h('div', { class: 'row wrap', style: { justifyContent: 'space-between' } }, h('h2', null, 'פעילות אחרונה'),
      h('div', { class: 'row wrap', style: { gap: '10px' } }, toggle, h('a', { href: '#log', class: 'small', style: { fontWeight: 600 } }, 'יומן מלא'))),
    body);
}

function feed(list) {
  if (!list.length) return h('div', { class: 'empty' }, 'עוד אין פעילות.');
  return h('ol', { class: 'feed' }, list.map(l => h('li', null, h('span', { class: 'dot ' + dotFor(l.action) }),
    h('div', { class: 'stack', style: { gap: '2px' } },
      h('span', null, ACTIONS[l.action] || l.action, l.target ? ' — ' : '', l.target ? h('span', { class: l.target.includes('@') ? 'ltr' : '' }, l.target) : null, l.detail ? ` (${l.detail})` : ''),
      h('span', { class: 'small muted' }, h('time', { datetime: l.at, title: fmtWhen(l.at) }, ago(l.at)), ` · ${fmtWhen(l.at)} · ${l.actor === user.email ? 'את/ה' : l.actor}`)))));
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
    what = h('div', null, 'נכנס/ה כמורה, והחשבון עוד לא מוגדר.', r.computers ? ` כ-${r.computers} מחשבים בכיתה.` : '', r.phone ? [' טלפון: ', h('span', { class: 'ltr' }, r.phone)] : '');
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
  const shown = byTime(all.filter(groups[teacherFilter][1]), 'lastSignIn', orderOf('teachers.lastSignIn', 'none'));
  const filters = h('div', { class: 'filters' }, Object.entries(groups).map(([id, [label, fn]]) => h('button', { type: 'button', 'aria-pressed': String(teacherFilter === id), onclick: () => { teacherFilter = id; render(); } }, `${label} · ${all.filter(fn).length}`)));
  const table = h('div', { class: 'tablewrap' }, h('table', { class: 't cards' },
    h('thead', null, h('tr', null, ['מורה', 'בית ספר', 'עמדות', 'שליחות החודש'].map(x => h('th', null, x)), timeHeader('כניסה אחרונה', 'teachers.lastSignIn', render), h('th', null, 'מצב'))),
    h('tbody', null, shown.length ? shown.map(t => {
      const pct = t.seats ? Math.min(100, Math.round(100 * t.used / t.seats)) : (t.used ? 100 : 0);
      const tr = h('tr', { class: 'click' + (t.email === selected ? ' sel' : ''), tabindex: '0', onclick: () => { location.hash = '#teacher=' + encodeURIComponent(t.email); }, onkeydown: e => { if (e.key === 'Enter') location.hash = '#teacher=' + encodeURIComponent(t.email); } },
        td('', { class: 'main' }, h('div', { class: 'cellname' }, h('b', null, t.name || '—'), h('span', { class: 'ltr' }, t.email))),
        td('בית ספר', { class: t.school ? '' : 'empty' }, t.school || '—'),
        td('עמדות', null, h('div', { class: 'seats' }, h('span', null, h('b', null, String(t.used)), ` / ${t.seats}`), h('span', { class: 'bar' + (full(t) ? ' full' : '') }, h('span', { style: { width: pct + '%' } })))),
        td('שליחות החודש', null, t.sends ? h('span', { class: t.sends.n >= t.sends.limit ? 'pill orange' : '' }, h('b', null, String(t.sends.n)), ` / ${t.sends.limit}`) : '—'),
        td('כניסה אחרונה', { class: 'muted', title: fmtWhen(t.lastSignIn) }, t.lastSignIn ? ago(t.lastSignIn) : 'עוד לא נכנס/ה'),
        td('מצב', null, t.active ? h('span', { class: 'pill green' }, 'פעיל') : h('span', { class: 'pill gray' }, 'מושבת')));
      return tr;
    }) : h('tr', null, h('td', { colspan: '6' }, h('div', { class: 'empty' }, all.length ? 'אין מורים בקבוצה הזו.' : 'עוד אין מורים — מוסיפים בכפתור "הוספת מורה".'))))));
  const main = h('div', { class: 'grow stack', style: { gap: '16px' } },
    h('div', { class: 'titlebar' }, h('h1', null, 'מורים ועמדות'), h('button', { type: 'button', class: 'btn primary', onclick: addTeacher }, icon(ICONS.plus, 18, '#fff', 2.4), 'הוספת מורה')),
    filters, table, h('p', { class: 'small muted' }, 'לוחצים על מורה כדי לשנות את מספר העמדות, לראות את המחשבים שלו ולשחרר עמדה.'));
  if (!selected) return main;
  return h('div', { class: 'split' }, main, await teacherDrawer(selected));
}

/** מנת שליחות חודשית ידנית למורה: קובעים מספר, או חוזרים לברירת המחדל (עמדות × מנה לעמדה) */
function quotaSection(t) {
  const per = (settingsCache && settingsCache.monthlyPerSeat) || 0;
  const auto = per * Math.max(1, t.seats);
  const manual = t.monthlyLimit !== null && t.monthlyLimit !== undefined;
  const cur = t.sends ? t.sends.limit : (manual ? t.monthlyLimit : auto);
  const val = h('input', { class: 'field num', type: 'number', min: '0', max: '100000', value: String(cur), 'aria-label': 'מנת שליחות חודשית' });
  const save = h('button', { type: 'button', class: 'btn primary grow' }, 'שמירה');
  const clear = h('button', { type: 'button', class: 'btn' }, 'ברירת מחדל');
  clear.disabled = !manual;
  const note = h('div', { class: 'note', style: { background: '#fff', color: 'var(--ink)' } },
    manual ? `נקבע ידנית: ${t.monthlyLimit} בחודש (ברירת המחדל הייתה ${auto}).` : `ברירת מחדל: ${per} לכל עמדה × ${Math.max(1, t.seats)} = ${auto} בחודש.`,
    t.sends ? ` נשלחו החודש: ${t.sends.n}.` : '');
  save.onclick = () => busy(save, async () => {
    const n = Math.max(0, Math.round(+val.value || 0));
    await api('POST', '/v1/admin/teacher', { email: t.email, monthlyLimit: n });
    cache.teachers = null; toast(`מנת השליחות החודשית: ${n}`); render();
  });
  clear.onclick = () => busy(clear, async () => {
    await api('POST', '/v1/admin/teacher', { email: t.email, monthlyLimit: null });
    cache.teachers = null; toast('חזרה לברירת המחדל'); render();
  });
  return h('section', { class: 'stack', style: { background: 'var(--bg)', borderRadius: '14px', padding: '16px', gap: '12px' } },
    h('div', { class: 'row', style: { justifyContent: 'space-between', alignItems: 'baseline' } }, h('h3', null, 'שליחות תרגיל בחודש'), h('span', { class: 'small muted' }, 'המורה וכל העמדות שלו יחד')),
    val, note, h('div', { class: 'row' }, save, clear));
}

async function teacherDrawer(email) {
  const [d, rm] = await Promise.all([api('GET', '/v1/admin/teacher?email=' + encodeURIComponent(email)), api('GET', '/v1/admin/rooms?teacher=' + encodeURIComponent(email))]);
  const t = d.teacher;
  const val = h('input', { class: 'field num', type: 'number', min: '0', max: '10000', value: String(t.seats), 'aria-label': 'מספר העמדות' });
  const preview = h('div', { class: 'note', style: { background: '#fff', color: 'var(--ink)' } });
  const save = h('button', { type: 'button', class: 'btn primary grow' }, 'שמירה');
  const reset = h('button', { type: 'button', class: 'btn' }, 'ביטול');
  const update = () => {
    const n = Math.max(0, Math.round(+val.value || 0));
    const changed = n !== t.seats;
    save.disabled = !changed; reset.disabled = !changed;
    const sends = t.sends ? ` · שליחות החודש: ${t.sends.n} מתוך ${t.sends.limit}` : '';
    if (!changed) preview.textContent = `${t.used} בשימוש · ${Math.max(0, t.seats - t.used)} פנויות${sends}.`;
    else if (n >= t.used) preview.replaceChildren(`${t.seats} ← `, h('b', null, String(n)), `: אחרי השמירה יהיו ${n - t.used} עמדות פנויות, ומנת השליחות החודשית תהיה ${n * (settingsCache.monthlyPerSeat || 0)}. העמדות שכבר מחוברות לא משתנות.`);
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
  const stationItem = s => {
    const stale = daysSince(s.lastSeen) > 30;
    const rel = h('button', { type: 'button', class: 'btn sm' }, 'שחרור');
    rel.onclick = async () => {
      if (!await ask({ title: `לשחרר את ״${s.name}״?`, text: `ההיתר חוזר ל${t.name || 'מורה'}. המחשב הזה ננעל בפעם הבאה שהוא מחובר לאינטרנט, ויצטרך את קוד המורה כדי להתחבר שוב.`, ok: 'שחרור' })) return;
      busy(rel, async () => { await api('POST', '/v1/admin/station/release', { id: s.id }); cache.teachers = null; toast('העמדה שוחררה'); render(); });
    };
    return h('div', { class: 'item' + (stale ? ' stale' : ''), 'data-seen': s.lastSeen || '', 'data-claimed': s.claimedAt || '' }, h('div', { class: 'grow' }, h('div', { style: { fontWeight: 600 } }, s.name),
      h('div', { class: 'small', style: { color: stale ? 'var(--orange)' : 'var(--muted)' } }, stale ? `לא נראתה ${daysSince(s.lastSeen)} יום — אולי הוחלפה` : `חוברה ${fmtWhen(s.claimedAt)} · פעילה ${ago(s.lastSeen)}`, stale ? ` · חוברה ${fmtWhen(s.claimedAt)}` : '')), rel);
  };
  // המחשבים של המורה (המעבדה): לפי פעילות אחרונה או לפי מועד החיבור, החדש או הישן קודם
  const STFIELD = { lastSeen: 'פעילות אחרונה', claimedAt: 'מועד החיבור' };
  let stField = STFIELD[ORDER['stations.field']] ? ORDER['stations.field'] : 'lastSeen';
  const stList = h('div', { class: 'list' });
  const drawStations = () => stList.replaceChildren(...(d.stations.length ? byTime(d.stations, stField, orderOf('stations')).map(stationItem) : [h('div', { class: 'empty' }, 'עוד אין מחשבים מחוברים.')]));
  drawStations();
  const stFieldSel = h('select', { class: 'field sm', 'aria-label': 'מיון המחשבים לפי', onchange: e => { stField = e.target.value; setOrder('stations.field', stField); drawStations(); } },
    Object.entries(STFIELD).map(([v, label]) => h('option', { value: v, selected: v === stField }, label)));
  const stOrder = d.stations.length > 1 ? h('div', { class: 'row wrap', style: { gap: '8px' } }, stFieldSel, orderToggle('stations', drawStations)) : null;
  // שני טאבים נפרדים: עמדות המעבדה, והתלמידים שמשויכים למורה (עם הכיתה)
  const cmap = new Map(rm.classes.map(c => [c.id, c]));
  let cls = '', tab = 'stations';
  const studentList = h('div', { class: 'list' });
  const drawStudents = () => {
    const shown = rm.students.filter(x => !cls || (cls === '-' ? !x.classId : x.classId === cls));
    studentList.replaceChildren(...(shown.length ? shown.map(x => h('div', { class: 'item', 'data-student': x.email },
      h('div', { class: 'grow' }, h('div', { style: { fontWeight: 600 } }, x.name || x.email), h('div', { class: 'small muted ltr', style: { textAlign: 'right' } }, x.email),
        h('div', { class: 'small muted' }, (cmap.get(x.classId) || {}).name || 'בלי כיתה', ` · ${x.submits || 0} הגשות`, x.lastSubmitAt ? ' · הגשה אחרונה ' + ago(x.lastSubmitAt) : '')),
      h('button', { type: 'button', class: 'btn sm', onclick: () => studentDialog(x.email) }, 'פרטים')))
      : [h('div', { class: 'empty' }, rm.students.length ? 'אין תלמידים בכיתה הזו.' : 'עוד אין תלמידים שמשויכים למורה.')]));
  };
  drawStudents();
  const clsSel = rm.classes.length ? h('select', { class: 'field sm', 'aria-label': 'כיתה', onchange: e => { cls = e.target.value; drawStudents(); } },
    h('option', { value: '' }, 'כל הכיתות'), rm.classes.map(c => h('option', { value: c.id }, `${c.name} · ${c.students}`)), h('option', { value: '-' }, 'בלי כיתה')) : null;
  const studentsPane = h('div', { class: 'stack' },
    h('div', { class: 'row wrap', style: { justifyContent: 'space-between' } }, clsSel || h('span'), rm.students.length ? h('a', { class: 'small', href: roomsHash('students', t.email) }, 'בעמוד המלא') : null), studentList);
  const stationsPane = h('div', { class: 'stack' }, stOrder, stList);
  const tabsBox = h('div'), paneBox = h('div', { class: 'stack' });
  const drawTab = () => {
    tabsBox.replaceChildren(roomTabs(tab, { stations: d.stations.length, students: rm.students.length }, id => { tab = id; drawTab(); }));
    paneBox.replaceChildren(tab === 'stations' ? stationsPane : studentsPane);
  };
  drawTab();
  return h('aside', { class: 'drawer card', 'aria-label': 'פרטי המורה' },
    h('div', { class: 'row', style: { alignItems: 'flex-start', justifyContent: 'space-between' } },
      h('div', { class: 'stack', style: { gap: '2px' } }, h('h2', { style: { fontSize: '21px' } }, t.name || t.email), h('span', { class: 'small muted' }, h('span', { class: 'ltr' }, t.email), t.school ? ' · ' + t.school : '', t.phone ? [' · ', h('span', { class: 'ltr' }, t.phone)] : ''),
        t.active ? null : h('span', { class: 'pill gray', style: { alignSelf: 'flex-start', marginTop: '6px' } }, 'מושבת')),
      h('a', { class: 'btn sm', href: '#teachers', 'aria-label': 'סגירה' }, icon(ICONS.x, 16))),
    h('section', { class: 'stack', style: { background: 'var(--bg)', borderRadius: '14px', padding: '16px', gap: '12px' } },
      h('div', { class: 'row', style: { justifyContent: 'space-between', alignItems: 'baseline' } }, h('h3', null, 'מכסת עמדות'), h('span', { class: 'small muted' }, `בשימוש ${t.used}`)),
      h('div', { class: 'stepper' }, h('button', { type: 'button', class: 'btn', 'aria-label': 'פחות עמדה', onclick: () => step(-1) }, icon(ICONS.minus, 18, '#17213A', 2.4)), val,
        h('button', { type: 'button', class: 'btn', 'aria-label': 'עוד עמדה', onclick: () => step(1) }, icon(ICONS.plus, 18, '#17213A', 2.4))),
      h('div', { class: 'row' }, [5, 10, 20].map(k => h('button', { type: 'button', class: 'btn sm grow', onclick: () => step(k) }, '‎+' + k))),
      preview, h('div', { class: 'row' }, save, reset)),
    quotaSection(t),
    h('section', { class: 'row wrap' }, h('div', { class: 'grow stack', style: { gap: '2px' } }, h('span', { class: 'small muted', style: { fontWeight: 600 } }, 'קוד המורה'), h('span', { class: 'mono', style: { fontSize: '20px', textAlign: 'right' } }, t.code)), copy),
    h('section', { class: 'stack' }, h('h3', null, `עמדות ותלמידים של ${t.name || 'המורה'}`), tabsBox, paneBox),
    t.note ? h('div', { class: 'note' }, t.note) : null,
    h('section', { class: 'row wrap', style: { borderTop: '1px solid var(--line)', paddingTop: '14px' } }, edit, h('div', { class: 'grow' }), toggle));
}

function teacherForm(title, t, onSave) {
  const d = h('dialog', { class: 'modal', 'aria-labelledby': 'tf-t' });
  const email = h('input', { class: 'field ltr', type: 'email', id: 'tf-e', placeholder: 'name@gmail.com', value: t ? t.email : '', disabled: !!t });
  const name = h('input', { class: 'field', type: 'text', id: 'tf-n', value: t ? t.name : '' });
  const school = h('input', { class: 'field', type: 'text', id: 'tf-s', value: t ? t.school : '' });
  const seats = h('input', { class: 'field', type: 'number', min: '0', max: '10000', id: 'tf-c', value: String(t ? t.seats : settingsCache.defaultSeats) });
  const phone = h('input', { class: 'field ltr', type: 'tel', id: 'tf-p', value: t ? t.phone || '' : '', placeholder: '050-1234567', style: { textAlign: 'left' } });
  const note = h('input', { class: 'field', type: 'text', id: 'tf-o', value: t ? t.note : '', placeholder: 'למשל: הסכם עד סוף השנה' });
  const ok = h('button', { type: 'button', class: 'btn primary' }, t ? 'שמירה' : 'הוספה');
  const close = () => { d.close(); d.remove(); };
  ok.onclick = () => busy(ok, async () => { await onSave({ email: email.value.trim(), name: name.value, school: school.value, phone: phone.value, seats: +seats.value, note: note.value }); close(); });
  d.append(h('div', { class: 'in' }, h('h2', { id: 'tf-t' }, title),
    t ? null : h('p', { class: 'small muted' }, 'חשבון Google של המורה. אחרי ההוספה המורה נכנס/ת מתוך DSViz ("כניסת מורה") ורואה את הקוד שלו.'),
    h('label', { class: 'lbl', for: 'tf-e' }, 'חשבון Google', email), h('label', { class: 'lbl', for: 'tf-n' }, 'שם', name),
    h('label', { class: 'lbl', for: 'tf-s' }, 'בית ספר', school), t ? null : h('label', { class: 'lbl', for: 'tf-c' }, 'מספר עמדות', seats),
    h('label', { class: 'lbl', for: 'tf-p' }, 'טלפון נייד', phone),
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
    await api('POST', '/v1/admin/teacher', { email: t.email, name: v.name, school: v.school, phone: v.phone, note: v.note });
    cache.teachers = null;
    toast('נשמר');
    render();
  });
}

// ------------------------------------------------------------------ עמדות ותלמידים של מורים (שני טאבים נפרדים)

/** הקישור לעמוד: טאב, מורה, כיתה ('-' = בלי כיתה) ותלמיד מסומן */
function roomsHash(tab, teacher, cls, student) {
  return '#rooms=' + encodeURIComponent([tab, teacher || '', cls || '', student || ''].join('|'));
}
function roomsArg(arg) {
  const [tab, teacher, cls, student] = String(arg || '').split('|');
  return { tab: tab === 'students' ? 'students' : 'stations', teacher: teacher || '', cls: cls || '', student: student || '' };
}
const ROOM_TABS = [['stations', 'עמדות מעבדה'], ['students', 'תלמידים']];

/** שורת הטאבים — מחזירה את הכפתורים; onPick(id) */
function roomTabs(current, counts, onPick) {
  return h('div', { class: 'tabs', role: 'tablist' }, ROOM_TABS.map(([id, label]) =>
    h('button', { type: 'button', role: 'tab', id: 'rt-' + id, 'aria-selected': String(current === id), onclick: () => onPick(id) }, `${label} · ${counts[id]}`)));
}

/** עמדה: האם נראית נטושה (לא פעילה 30 יום) והטקסט שמתחת לשם */
function stationInfo(s) {
  const stale = daysSince(s.lastSeen) > 30;
  return { stale, text: stale ? `לא נראתה ${daysSince(s.lastSeen)} יום — אולי הוחלפה · חוברה ${fmtWhen(s.claimedAt)}` : `חוברה ${fmtWhen(s.claimedAt)} · פעילה ${ago(s.lastSeen)}` };
}

/** "הגשה אחרונה": מתי ומה */
function lastSubmit(x) {
  return x.lastSubmitAt ? [ago(x.lastSubmitAt), x.lastTitle ? ' · ' + x.lastTitle : ''] : '—';
}

async function roomsView(arg) {
  const f = roomsArg(arg);
  const r = await api('GET', '/v1/admin/rooms');
  const tmap = new Map(r.teachers.map(t => [t.email, t]));
  const cmap = new Map(r.classes.map(c => [c.id, c]));
  const tn = e => (tmap.get(e) || {}).name || e;
  const go = over => { const n = Object.assign({}, f, over); location.hash = roomsHash(n.tab, n.teacher, n.cls, n.student); };
  const inTeacher = x => !f.teacher || x.teacher === f.teacher;
  const stations = r.stations.filter(inTeacher);
  const studentsInTeacher = r.students.filter(inTeacher);
  const students = studentsInTeacher.filter(x => !f.cls || (f.cls === '-' ? !x.classId : x.classId === f.cls));
  const counts = { stations: stations.length, students: studentsInTeacher.length };

  // סינון: מורה (ואז כיתה — בטאב התלמידים)
  const count = e => f.tab === 'stations' ? r.stations.filter(x => x.teacher === e).length : r.students.filter(x => x.teacher === e).length;
  const teacherSel = h('select', { class: 'field sm', 'aria-label': 'מורה', onchange: e => go({ teacher: e.target.value, cls: '', student: '' }) },
    h('option', { value: '' }, `כל המורים · ${f.tab === 'stations' ? r.stations.length : r.students.length}`),
    r.teachers.filter(t => count(t.email) || t.email === f.teacher).map(t => h('option', { value: t.email, selected: t.email === f.teacher }, `${t.name || t.email} · ${count(t.email)}`)));
  const classList = r.classes.filter(c => !f.teacher || c.teacher === f.teacher);
  const classSel = f.tab === 'students' ? h('select', { class: 'field sm', 'aria-label': 'כיתה', onchange: e => go({ cls: e.target.value, student: '' }) },
    h('option', { value: '' }, 'כל הכיתות'),
    classList.map(c => h('option', { value: c.id, selected: c.id === f.cls }, f.teacher ? `${c.name} · ${c.students}` : `${c.name} — ${tn(c.teacher)} · ${c.students}`)),
    h('option', { value: '-', selected: f.cls === '-' }, 'בלי כיתה')) : null;

  // קבוצות לפי מורה (בסדר השמות)
  const group = list => r.teachers.map(t => [t, list.filter(x => x.teacher === t.email)]).filter(([, l]) => l.length);
  const head = (t, n, what) => h('div', { class: 'row wrap', style: { justifyContent: 'space-between' } },
    h('div', { class: 'stack', style: { gap: '2px' } }, h('h2', { style: { fontSize: '18px' } }, t.name || t.email),
      h('span', { class: 'small muted' }, h('span', { class: 'ltr' }, t.email), t.school ? ' · ' + t.school : '', t.active ? '' : ' · מושבת')),
    h('div', { class: 'row' }, h('span', { class: 'pill blue' }, `${n} ${what}`), h('a', { class: 'btn sm', href: '#teacher=' + encodeURIComponent(t.email) }, 'פתיחת המורה')));

  let body, pb = null;
  if (f.tab === 'stations') {
    body = group(stations).map(([t, list]) => h('section', { class: 'card', 'data-teacher': t.email }, head(t, list.length, 'עמדות'),
      h('div', { class: 'list' }, list.map(x => { const i = stationInfo(x); return h('div', { class: 'item' + (i.stale ? ' stale' : ''), 'data-station': x.id },
        h('div', { class: 'grow' }, h('div', { style: { fontWeight: 600 } }, x.name), h('div', { class: 'small', style: { color: i.stale ? 'var(--orange)' : 'var(--muted)' } }, i.text))); }))));
    if (!body.length) body = [h('div', { class: 'card' }, h('div', { class: 'empty' }, 'עוד אין עמדות מעבדה מחוברות.'))];
  } else {
    const CAP = 500;
    let left = CAP;
    for (const e of [...picked]) if (!students.some(x => x.email === e)) picked.delete(e);
    pb = pickBar(students.slice(0, CAP), { canApprove: false });
    body = group(students).map(([t, list]) => {
      const shown = list.slice(0, Math.max(0, left));
      left -= shown.length;
      if (!shown.length) return null;
      return h('section', { class: 'card', 'data-teacher': t.email }, head(t, list.length, 'תלמידים'),
        h('div', { class: 'tablewrap' }, h('table', { class: 't cards' },
          h('thead', null, h('tr', null, ['', 'תלמיד', 'כיתה', 'הצטרף', 'הגשות', 'הגשה אחרונה', ''].map(x => h('th', null, x)))),
          h('tbody', null, shown.map(x => {
            const box = pickBox(x.email, x.name, pb.draw);
            box.dataset.email = x.email;
            return h('tr', { class: x.email === f.student ? 'sel' : '', 'data-student': x.email },
              td('', { class: 'pickcell' }, box),
              td('', { class: 'main' }, h('div', { class: 'cellname' }, h('b', null, x.name || '—'), h('span', { class: 'ltr' }, x.email),
                x.registered ? null : h('span', { class: 'pill orange', style: { alignSelf: 'flex-start' } }, 'עוד לא נרשם/ה'))),
              td('כיתה', null, x.classId && cmap.has(x.classId) ? cmap.get(x.classId).name : h('span', { class: 'muted' }, 'בלי כיתה')),
              td('הצטרף', { class: 'muted', title: fmtWhen(x.joinedAt) }, x.joinedAt ? fmtDate(x.joinedAt) : '—'),
              td('הגשות', null, String(x.submits || 0)),
              td('הגשה אחרונה', { class: 'muted' + (x.lastSubmitAt ? '' : ' empty'), title: fmtWhen(x.lastSubmitAt) }, lastSubmit(x)),
              td('', { class: 'actions' }, h('div', { class: 'row', style: { gap: '6px' } },
                h('button', { type: 'button', class: 'btn sm', 'data-act': 'assign', onclick: () => assignDialog([x]) }, 'שיוך'),
                h('button', { type: 'button', class: 'btn sm', 'data-act': 'details', onclick: () => studentDialog(x.email) }, 'פרטים'))));
          })))));
    }).filter(Boolean);
    if (!body.length) body = [h('div', { class: 'card' }, h('div', { class: 'empty' }, studentsInTeacher.length ? 'אין תלמידים בכיתה הזו.' : 'עוד אין תלמידים שמשויכים למורים.'))];
    if (students.length > CAP) body.push(h('p', { class: 'small muted' }, `מוצגים ${CAP} מתוך ${students.length} — מצמצמים לפי מורה או כיתה, או משתמשים בחיפוש למעלה.`));
  }
  if (f.student) setTimeout(() => { const el = document.querySelector('tr.sel'); if (el && el.scrollIntoView) el.scrollIntoView({ block: 'center' }); }, 0);
  return [
    h('div', { class: 'stack', style: { gap: '6px' } }, h('h1', null, 'עמדות ותלמידי מורים'),
      h('p', { class: 'muted' }, 'כל מה שמשויך למורים, מופרד לשני טאבים: עמדות המעבדה (מחשבים שחוברו בקוד המורה) והתלמידים שנכנסו עם חשבון Google ושייכים למורה. תלמידים עצמאיים נמצאים ב"תלמידים ותשלומים".')),
    roomTabs(f.tab, counts, id => go({ tab: id, student: '' })),
    h('div', { class: 'row wrap', style: { gap: '8px' } }, teacherSel, classSel),
    pb ? pb.bar : null,
    ...body];
}

// ------------------------------------------------------------------ תלמידים ותשלומים

let studentFilter = 'open';

/** תא בטבלה עם תווית — במסך צר (טלפון) השורה נהיית כרטיס, והתווית מוצגת לפני הערך (app.css: table.t.cards) */
function td(label, attrs, ...kids) {
  return h('td', Object.assign(label ? { 'data-label': label } : {}, attrs || {}), ...kids);
}

/** התלמידים שסומנו (בעמוד "תלמידים" ובטאב התלמידים של המורים) — לאישור / לשיוך של כמה יחד */
const picked = new Set();

/** תיבת סימון של תלמיד; onChange מצייר מחדש את פס הפעולות */
function pickBox(email, name, onChange) {
  const c = h('input', { type: 'checkbox', class: 'pick', 'aria-label': 'סימון ' + (name || email), checked: picked.has(email) });
  c.addEventListener('change', () => { if (c.checked) picked.add(email); else picked.delete(email); onChange(); });
  c.addEventListener('click', e => e.stopPropagation());
  return c;
}

/**
 * פס הפעולות לתלמידים שסומנו: אישור (הענקת גישה עד התאריך שבהגדרות) ושיוך למורה. shown — התלמידים שבתצוגה עכשיו
 * (לכפתור "סימון הכול"); canApprove — בעמוד התלמידים העצמאיים
 */
function pickBar(shown, { canApprove }) {
  const bar = h('div', { class: 'pickbar', id: 'pickbar' });
  const draw = () => {
    const mine = shown.filter(x => picked.has(x.email));
    const all = h('button', { type: 'button', class: 'btn sm', id: 'pick-all' }, mine.length === shown.length && shown.length ? 'ניקוי הסימון' : `סימון הכול (${shown.length})`);
    all.onclick = () => {
      if (mine.length === shown.length) shown.forEach(x => picked.delete(x.email)); else shown.forEach(x => picked.add(x.email));
      document.querySelectorAll('input.pick').forEach(c => { c.checked = picked.has(c.dataset.email); });
      draw();
    };
    const approve = canApprove ? h('button', { type: 'button', class: 'btn sm primary', id: 'pick-approve' }, icon(ICONS.check, 16, '#fff', 2.4), `אישור גישה (${mine.length})`) : null;
    if (approve) approve.onclick = () => busy(approve, () => approveStudents(mine.map(x => x.email)));
    const assign = h('button', { type: 'button', class: 'btn sm', id: 'pick-assign' }, icon(ICONS.user, 16), `שיוך למורה (${mine.length})`);
    assign.onclick = () => assignDialog(mine);
    bar.classList.toggle('on', mine.length > 0);
    bar.replaceChildren(...[all, mine.length ? h('span', { class: 'small muted' }, `${mine.length} מסומנים`) : h('span', { class: 'small muted hint' }, 'מסמנים תלמידים כדי לאשר או לשייך כמה יחד'),
      h('span', { class: 'grow' }), mine.length ? approve : null, mine.length ? assign : null].filter(Boolean));
  };
  draw();
  return { bar, draw };
}

/** "אישור": הענקת גישה עד התאריך שבהגדרות — לתלמיד אחד או לכמה */
async function approveStudents(emails) {
  if (!emails.length) return;
  if (!settingsCache) settingsCache = (await api('GET', '/v1/admin/settings')).settings;
  const r = await api('POST', '/v1/admin/student', { emails, until: settingsCache.accessUntil, note: 'אישור מהמסוף' });
  emails.forEach(e => picked.delete(e));
  toast(r.count === 1 ? `הגישה אושרה עד ${fmtDate(settingsCache.accessUntil)}` : `הגישה אושרה ל-${r.count} תלמידים, עד ${fmtDate(settingsCache.accessUntil)}`);
  render();
}

/**
 * שיוך תלמידים למורה (גם מי שנרשם עצמאי, או שמשויך למורה אחר — עובר), עם כיתה אם רוצים; או "בלי מורה" — חזרה לעצמאי.
 * list: [{ email, name, teacher? }]. המורה רואה את התלמיד מיד בעמדת הניהול שלו, והתלמיד רואה את המורה בתוכנה.
 */
async function assignDialog(list) {
  if (!list.length) return;
  if (!cache.teachers) cache.teachers = (await api('GET', '/v1/admin/teachers')).teachers;
  const teachers = cache.teachers.filter(t => t.active);
  const anyAttached = list.some(x => x.teacher);
  const d = h('dialog', { class: 'modal', 'aria-labelledby': 'as-t' });
  const close = () => { d.close(); d.remove(); };
  const same = list.every(x => x.teacher && x.teacher === list[0].teacher) ? list[0].teacher : '';
  const tSel = h('select', { class: 'field', id: 'as-teacher' },
    h('option', { value: '', disabled: true, selected: !same }, teachers.length ? 'בחירת מורה…' : 'אין מורים פעילים'),
    teachers.map(t => h('option', { value: t.email, selected: t.email === same }, `${t.name || t.email}${t.school ? ' · ' + t.school : ''}`)),
    anyAttached ? h('option', { value: '-' }, '— בלי מורה (תלמיד עצמאי) —') : null);
  const cSel = h('select', { class: 'field', id: 'as-class' }, h('option', { value: '' }, 'בלי כיתה (המורה יסדר)'));
  const cRow = h('label', { class: 'lbl', for: 'as-class' }, 'כיתה (לא חובה)', cSel);
  const loadClasses = async () => {
    cSel.replaceChildren(h('option', { value: '' }, 'בלי כיתה (המורה יסדר)'));
    cRow.hidden = !tSel.value || tSel.value === '-';
    if (cRow.hidden) return;
    try {
      const r = await api('GET', '/v1/admin/rooms?teacher=' + encodeURIComponent(tSel.value));
      r.classes.forEach(c => cSel.append(h('option', { value: c.id }, `${c.name} · ${c.students}`)));
      cRow.hidden = !r.classes.length;
    } catch (e) { cRow.hidden = true; }
  };
  tSel.addEventListener('change', () => { ok.disabled = !tSel.value; loadClasses(); });
  const ok = h('button', { type: 'button', class: 'btn primary', id: 'as-ok', disabled: !same }, 'שיוך');
  ok.onclick = () => busy(ok, async () => {
    const un = tSel.value === '-';
    const r = await api('POST', '/v1/admin/student/assign', { emails: list.map(x => x.email), teacher: un ? '' : tSel.value, classId: un ? '' : cSel.value });
    list.forEach(x => picked.delete(x.email));
    cache.students = null;
    cache.teachers = null;
    close();
    toast(un ? (r.changed === 1 ? 'השיוך בוטל — התלמיד עצמאי' : `השיוך בוטל ל-${r.changed} תלמידים`)
      : r.changed ? (list.length === 1 ? `שויך ל${r.teacher.name || r.teacher.email}` : `${r.changed} תלמידים שויכו ל${r.teacher.name || r.teacher.email}`) : 'כבר משויכים למורה הזה');
    render();
  });
  const names = list.slice(0, 6).map(x => x.name || x.email).join(', ') + (list.length > 6 ? ` ועוד ${list.length - 6}` : '');
  d.append(h('div', { class: 'in' }, h('h2', { id: 'as-t' }, list.length === 1 ? 'שיוך תלמיד למורה' : `שיוך ${list.length} תלמידים למורה`),
    h('p', { class: 'small muted' }, names),
    h('label', { class: 'lbl', for: 'as-teacher' }, 'המורה', tSel), cRow,
    h('p', { class: 'small muted', style: { lineHeight: '1.5' } }, 'המורה יראה את התלמידים מיד בעמדת הניהול שלו, והתלמיד יראה את המורה בתוכנה. תלמיד שמשויך למורה אחר — עובר (ההגשות הקודמות נשארות אצל המורה הקודם).'),
    h('div', { class: 'row' }, ok, h('button', { type: 'button', class: 'btn', onclick: close }, 'ביטול'))));
  d.addEventListener('cancel', e => { e.preventDefault(); close(); });
  document.body.append(d);
  d.showModal();
  loadClasses();
}

async function students(arg) {
  if (arg === 'payments') return payments();
  const r = await api('GET', '/v1/admin/students');
  cache.students = r.students;
  // כל התלמידים במקום אחד — עצמאיים ומשויכים למורה (למשויך: מסגרת קטנה עם שם המורה שלו). כך מאשרים ומשייכים את כולם מכאן;
  // העמוד "עמדות ותלמידי מורים" מציג את המשויכים לפי מורה וכיתה
  const all = r.students;
  // בינתיים בחינם (r.free): כל מי שנכנס פתוח, והקבוצות לפי ההרשמה (שם מלא וטלפון); בתשלום — כמו קודם
  const who = { solo: ['עצמאיים', s => !s.teacher], attached: ['משויכים למורה', s => !!s.teacher] };
  const groups = r.free
    ? { open: ['כולם', () => true], waiting: ['מחכים לאישור', s => !s.paid], ...who, registered: ['נרשמו', s => s.registered], unregistered: ['עוד לא נרשמו', s => !s.registered], paid: ['שילמו / הענקה', s => s.paid] }
    : { open: ['עם גישה', s => s.open], unpaid: ['מחכים לאישור', s => !s.open], ...who, manual: ['הענקה ידנית', s => s.open && s.source === 'manual'], all: ['הכול', () => true] };
  if (!groups[studentFilter]) studentFilter = 'open';
  const shown = byTime(all.filter(groups[studentFilter][1]), 'lastSignIn', orderOf('students.lastSignIn', 'none')).slice(0, 400);
  const total = all.filter(groups[studentFilter][1]).length;
  for (const e of [...picked]) if (!shown.some(x => x.email === e)) picked.delete(e);
  const pb = pickBar(shown, { canApprove: true });
  const sourcePill = s => s.paid ? (s.source === 'manual' ? h('span', { class: 'pill purple' }, 'הענקה ידנית') : h('span', { class: 'pill green' }, 'תשלום' + (s.paidAt ? ' · ' + fmtDate(s.paidAt) : '')))
    : r.free ? h('span', { class: 'pill blue' }, 'בחינם')
    : s.source === 'refunded' ? h('span', { class: 'pill gray' }, 'הוחזר') : s.source === 'revoked' ? h('span', { class: 'pill gray' }, 'בוטל') : h('span', { class: 'pill gray' }, 'לא שילם/ה');
  const rowEl = s => {
    const box = pickBox(s.email, s.name, pb.draw);
    box.dataset.email = s.email;
    const approve = s.paid ? null : h('button', { type: 'button', class: 'btn sm primary', 'data-act': 'approve' }, 'אישור');
    if (approve) approve.onclick = () => busy(approve, () => approveStudents([s.email]));
    return h('tr', { 'data-student': s.email },
      td('', { class: 'pickcell' }, box),
      td('', { class: 'main' }, h('div', { class: 'cellname' }, h('b', null, s.name || '—'), h('span', { class: 'ltr' }, s.email),
        s.registered ? null : h('span', { class: 'pill orange', style: { alignSelf: 'flex-start' } }, 'עוד לא נרשם/ה'))),
      td('טלפון', { class: 'ltr muted' + (s.phone ? '' : ' empty') }, s.phone || '—'),
      td('מורה', { class: 'teachercell' }, s.teacher
        ? h('a', { class: 'tchip', href: roomsHash('students', s.teacher, '', s.email), title: 'משויך/ת למורה — פתיחת התלמידים של המורה' }, icon(ICONS.user, 14), h('span', null, s.teacherName || s.teacher))
        : s.mode === 'independent' ? 'עצמאי' : h('span', { class: 'muted' }, 'עוד לא בחר/ה')),
      td('גישה עד', null, s.paid ? fmtDate(s.until) : r.free ? 'בחינם' : '—'), td('איך', null, sourcePill(s)),
      td('שליחות החודש', null, s.sends ? h('span', { class: s.sends.n >= s.sends.limit ? 'pill orange' : '' }, h('b', null, String(s.sends.n)), ` / ${s.sends.limit}`) : '—'),
      td('כניסה אחרונה', { class: 'muted' + (s.lastSignIn ? '' : ' empty'), title: fmtWhen(s.lastSignIn) }, s.lastSignIn ? ago(s.lastSignIn) : '—'),
      td('', { class: 'actions' }, h('div', { class: 'row', style: { gap: '6px' } }, approve,
        h('button', { type: 'button', class: 'btn sm', 'data-act': 'assign', onclick: () => assignDialog([s]) }, s.teacher ? 'שינוי מורה' : 'שיוך למורה'),
        h('button', { type: 'button', class: 'btn sm', 'data-act': 'details', onclick: () => studentDialog(s.email) }, 'פרטים'))));
  };
  const table = h('div', { class: 'tablewrap' }, h('table', { class: 't cards' },
    h('thead', null, h('tr', null, h('th', null, ''), ['תלמיד', 'טלפון', 'מורה', 'גישה עד', 'איך', 'שליחות החודש'].map(x => h('th', null, x)), timeHeader('כניסה אחרונה', 'students.lastSignIn', render), h('th', null, ''))),
    h('tbody', null, shown.length ? shown.map(rowEl)
      : h('tr', null, h('td', { colspan: '9' }, h('div', { class: 'empty' }, 'אין תלמידים בקבוצה הזו.'))))));
  if (arg && arg.includes('@')) setTimeout(() => studentDialog(arg), 0);
  return h('div', { class: 'split' },
    h('div', { class: 'grow stack', style: { gap: '16px' } },
      h('h1', null, 'תלמידים ותשלומים'),
      h('p', { class: 'small muted' }, 'כל התלמידים במקום אחד: עצמאיים ומשויכים למורה (במסגרת — שם המורה). מכאן מאשרים גישה ומשייכים למורה; לפי מורה וכיתה — ב', h('a', { href: roomsHash('students') }, 'עמדות ותלמידי מורים'), '.'),
      r.free ? h('p', { class: 'small muted' }, 'התלמידים בחינם בינתיים (הגדרות ← "תלמידים בחינם"). כשמכבים — מי שלא שילם / לא קיבל הענקה נדרש לשלם, גם מי שכבר מחובר.') : null,
      h('div', { class: 'tabs', role: 'tablist' }, h('button', { type: 'button', role: 'tab', 'aria-selected': 'true' }, `תלמידים · ${all.filter(s => s.open).length}`),
        h('button', { type: 'button', role: 'tab', 'aria-selected': 'false', onclick: () => { location.hash = '#students=payments'; } }, 'תשלומים')),
      h('div', { class: 'filters' }, Object.entries(groups).map(([id, [label, fn]]) => h('button', { type: 'button', 'aria-pressed': String(studentFilter === id), onclick: () => { studentFilter = id; render(); } }, `${label} · ${all.filter(fn).length}`))),
      pb.bar, table, total > 400 ? h('p', { class: 'small muted' }, `מוצגים 400 מתוך ${total} — משתמשים בחיפוש למעלה.`) : null),
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
  const devList = h('div', { class: 'list' });
  function drawDevices() {
    devList.replaceChildren(...(r.devices.length ? byTime(r.devices, 'lastSeen', orderOf('devices')).map(x => {
      const b = h('button', { type: 'button', class: 'btn sm' }, 'ניתוק');
      b.onclick = () => busy(b, async () => { await api('POST', '/v1/admin/session/revoke', { id: x.id }); r.devices = r.devices.filter(y => y.id !== x.id); drawDevices(); toast('המחשב נותק'); });
      return h('div', { class: 'item' }, h('div', { class: 'grow' }, h('div', { style: { fontWeight: 600 } }, x.name || 'מחשב'),
        h('div', { class: 'small muted' }, 'פעיל ' + ago(x.lastSeen), x.lastSeen ? ` · ${fmtWhen(x.lastSeen)}` : '', x.createdAt ? ` · חובר ${fmtWhen(x.createdAt)}` : '')), b);
    }) : [h('div', { class: 'empty' }, 'אין מחשבים מחוברים.')]));
  }
  drawDevices();
  d.append(h('div', { class: 'in' },
    h('div', { class: 'row', style: { justifyContent: 'space-between' } }, h('h2', { id: 'sd-t' }, s.name || email), h('button', { type: 'button', class: 'btn sm', 'aria-label': 'סגירה', onclick: close }, icon(ICONS.x, 16))),
    h('div', { class: 'small muted ltr', style: { textAlign: 'right' } }, email),
    h('div', { class: 'row' }, open ? h('span', { class: 'pill green' }, `פתוח עד ${fmtDate(s.until)}`) : h('span', { class: 'pill gray' }, 'סגור'), s.source === 'manual' ? h('span', { class: 'pill purple' }, 'הענקה ידנית') : null),
    s.phone ? h('div', { class: 'small muted' }, 'טלפון: ', h('span', { class: 'ltr' }, s.phone)) : null,
    r.sends ? h('div', null, 'שליחות תרגיל החודש: ', h('b', null, String(r.sends.n)), ` מתוך ${r.sends.limit}`) : null,
    s.note ? h('div', { class: 'note' }, s.note) : null,
    h('h3', null, 'תשלומים'),
    h('div', { class: 'list' }, r.payments.length ? r.payments.map(p => h('div', { class: 'item' }, h('div', { class: 'grow' }, `${p.amount} ₪ · ${fmtDate(p.paidAt || p.createdAt)}`, p.payerName ? ` · ${p.payerName}` : '', p.payerPhone ? h('span', { class: 'ltr' }, ' · ' + p.payerPhone) : null, p.ref ? h('span', { class: 'small muted ltr' }, ' ' + p.ref) : null),
      h('span', { class: 'pill ' + (p.status === 'paid' ? 'green' : 'gray') }, ({ paid: 'שולם', refunded: 'הוחזר', pending: 'לא הושלם', failed: 'נכשל', mismatch: 'סכום לא תואם' })[p.status] || p.status)))
      : h('div', { class: 'empty' }, 'אין תשלומים.')),
    h('div', { class: 'row wrap', style: { justifyContent: 'space-between' } }, h('h3', null, 'מחשבים מחוברים'), r.devices.length > 1 ? orderToggle('devices', drawDevices) : null),
    devList,
    h('div', { class: 'row wrap' }, extend, open ? revoke : null,
      h('button', { type: 'button', class: 'btn', id: 'sd-assign', onclick: () => { close(); assignDialog([{ email, name: s.name, teacher: s.teacher }]); } }, s.teacher ? 'העברה למורה אחר / ביטול שיוך' : 'שיוך למורה'))));
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
      h('thead', null, h('tr', null, timeHeader('תאריך', 'payments.date', render), ['מה נקנה', 'חשבון', 'המשלם', 'טלפון', 'סכום', 'מצב', 'אסמכתה'].map(x => h('th', null, x)))),
      h('tbody', null, r.payments.length ? byTime(r.payments.map(p => ({ ...p, when: p.paidAt || p.createdAt })), 'when', orderOf('payments.date', 'none')).map(p => h('tr', null,
        h('td', null, fmtDate(p.paidAt || p.createdAt)),
        h('td', null, p.kind === 'seats' ? h('span', { class: 'pill blue' }, `חבילת ${p.seats} עמדות`) : h('span', { class: 'pill gray' }, 'מאגר לתלמיד')),
        h('td', null, h('div', { class: 'cellname' }, h('b', null, p.name || '—'), h('span', { class: 'ltr' }, p.email))),
        h('td', null, h('div', { class: 'cellname' }, h('b', null, p.payerName || '—'), p.payerEmail && p.payerEmail !== p.email ? h('span', { class: 'ltr' }, p.payerEmail) : null)),
        h('td', { class: 'ltr' }, p.payerPhone ? h('a', { href: 'tel:' + p.payerPhone.replace(/[^0-9+]/g, '') }, p.payerPhone) : '—'),
        h('td', null, `${p.amount} ₪`), h('td', null, h('span', { class: 'pill ' + (label[p.status] || ['', 'gray'])[1] }, (label[p.status] || [p.status])[0])),
        h('td', { class: 'small muted ltr' }, p.ref || '')))
        : h('tr', null, h('td', { colspan: '8' }, h('div', { class: 'empty' }, 'עוד אין תשלומים.')))))));
}

// ------------------------------------------------------------------ דיווחים על בעיות (מהתוספים: "דיווח על הבעיה" ודיווח אוטומטי)

const REPORT_KIND = { 'visual-run': 'ההרצה הוויזואלית לא התחילה', crash: 'קריסה / שגיאה פנימית', diagnose: 'בדיקה מעמיקה', manual: 'דיווח של משתמש' };
const WHO_KIND = { teacher: ['מורה', 'blue'], station: ['עמדת מחשב', 'purple'], student: ['תלמיד', 'green'], none: ['לא מחובר', 'gray'] };

function reportWho(x, full) {
  const w = x.who || {}, t = x.teacher;
  const k = WHO_KIND[w.kind] || WHO_KIND.none;
  const main = w.kind === 'station' ? `עמדה "${w.stationName}"` : w.kind === 'none' ? 'מחשב לא מחובר' : (w.name || w.email);
  const parts = [h('span', { class: 'pill ' + k[1] }, k[0]), ' ', h('b', null, main)];
  if (w.kind === 'student' && w.email && full) parts.push(' ', h('span', { class: 'ltr small muted' }, w.email));
  if (t && w.kind !== 'teacher') parts.push(h('div', { class: 'small muted' }, 'של המורה ', t.name || t.email, t.school ? ' · ' + t.school : ''));
  if (t && w.kind === 'teacher' && t.school) parts.push(h('div', { class: 'small muted' }, t.school));
  return parts;
}

async function reportsView(arg) {
  const done = arg === 'done';
  const r = await api('GET', '/v1/admin/reports' + (done ? '?status=done' : ''));
  const tabs = h('div', { class: 'tabs', role: 'tablist' },
    h('button', { type: 'button', role: 'tab', 'aria-selected': String(!done), onclick: () => { location.hash = '#reports'; } }, `חדשים · ${r.open}`),
    h('button', { type: 'button', role: 'tab', 'aria-selected': String(done), onclick: () => { location.hash = '#reports=done'; } }, 'טופלו'));
  const rowEl = x => {
    const go = () => { location.hash = '#report=' + encodeURIComponent(x.id); };
    return h('tr', { class: 'click', tabindex: '0', onclick: go, onkeydown: e => { if (e.key === 'Enter') go(); } },
      h('td', { class: 'muted', style: { whiteSpace: 'nowrap' } }, ago(x.occurredAt)),
      h('td', null, reportWho(x)),
      h('td', null, h('div', null, h('b', null, x.title)), h('div', { class: 'small muted' }, REPORT_KIND[x.kind] || x.kind, x.similar ? ` · עוד ${x.similar} דומים` : '')),
      h('td', null, x.app || '—', x.dsvizVersion ? h('div', { class: 'small muted' }, 'DSViz ' + x.dsvizVersion) : null),
      h('td', null, h('span', { class: 'pill ' + (x.auto ? 'orange' : 'blue') }, x.auto ? 'אוטומטי' : 'משתמש לחץ'), x.hasDiag ? h('div', { class: 'small muted' }, 'עם בדיקה מעמיקה') : null));
  };
  const table = r.reports.length
    ? h('div', { class: 'tablewrap' }, h('table', { class: 't' }, h('thead', null, h('tr', null, ['מתי', 'מי', 'הבעיה', 'תוכנה', 'איך הגיע'].map(x => h('th', null, x)))), h('tbody', null, r.reports.map(rowEl))))
    : h('div', { class: 'card' }, h('div', { class: 'empty' }, done ? 'עוד אין דיווחים שטופלו.' : 'אין דיווחים חדשים.'));
  return [h('div', { class: 'stack', style: { gap: '6px' } }, h('h1', null, 'דיווחים על בעיות'),
    h('p', { class: 'muted' }, 'מהתוכנה: "דיווח על הבעיה" שתלמיד / מורה לחצו, ודיווחים אוטומטיים — כשההרצה הוויזואלית לא מתחילה בזמן שההרצה הרגילה עובדת, או בשגיאה פנימית. מחשב בלי אינטרנט שולח כשהחיבור חוזר.')),
    tabs, table];
}

async function reportView(id) {
  const r = await api('GET', '/v1/admin/report?id=' + encodeURIComponent(id || ''));
  const x = r.report, t = x.teacher;
  const field = (label, ...v) => h('div', { class: 'kv' }, h('span', { class: 'small muted' }, label), h('div', null, ...v));
  const pre = s => h('pre', { class: 'diag', dir: 'ltr' }, s);
  const status = h('button', { type: 'button', class: 'btn ' + (x.status === 'done' ? '' : 'primary') }, x.status === 'done' ? 'החזרה ל"חדשים"' : 'סימון: טופל');
  const note = h('textarea', { class: 'field', id: 'r-note', rows: '2', placeholder: 'הערה לעצמך (מה מצאת, מה תוקן, באיזו גרסה)' }, x.adminNote || '');
  status.onclick = () => busy(status, async () => {
    await api('POST', '/v1/admin/report', { id: x.id, status: x.status === 'done' ? 'new' : 'done', adminNote: note.value });
    toast(x.status === 'done' ? 'הוחזר לחדשים' : 'סומן כטופל');
    location.hash = '#reports' + (x.status === 'done' ? '=done' : '');
  });
  const saveNote = h('button', { type: 'button', class: 'btn' }, 'שמירת ההערה');
  saveNote.onclick = () => busy(saveNote, async () => { await api('POST', '/v1/admin/report', { id: x.id, adminNote: note.value }); toast('נשמר'); });
  const copy = h('button', { type: 'button', class: 'btn sm' }, 'העתקת הכול');
  copy.onclick = async () => {
    const all = [x.title, x.message, 'תוכנה: ' + x.app + ' ' + x.appVersion + ' · DSViz ' + x.dsvizVersion + ' · ' + x.os, Object.entries(x.context).map(([k, v]) => k + ': ' + v).join('\n'), r.diag].filter(Boolean).join('\n\n');
    try { await navigator.clipboard.writeText(all); toast('הועתק'); } catch (e) { toast('ההעתקה לא הצליחה', true); }
  };
  const ctx = Object.entries(x.context || {});
  return [
    h('div', { class: 'titlebar' }, h('div', { class: 'stack', style: { gap: '4px' } }, h('a', { href: '#reports' + (x.status === 'done' ? '=done' : ''), class: 'small' }, '→ כל הדיווחים'), h('h1', null, x.title)),
      h('span', { class: 'pill ' + (x.status === 'done' ? 'green' : 'orange') }, x.status === 'done' ? 'טופל' : 'חדש')),
    h('div', { class: 'grid2' },
      h('section', { class: 'card' }, h('h2', null, 'מי שלח'),
        field('שולח', ...reportWho(x, true)),
        t ? [field('המורה', h('b', null, t.name || '—'), t.school ? ' · ' + t.school : ''),
          field('טלפון', t.phone ? h('span', { class: 'ltr' }, t.phone) : '—'),
          field('מייל', h('a', { class: 'ltr', href: '#teacher=' + encodeURIComponent(t.email) }, t.email))] : field('המורה', x.who && x.who.kind === 'none' ? 'המחשב לא היה מחובר — אין פרטים' : 'לא משויך למורה'),
        field('מתי', fmtDate(x.occurredAt), ' · ', ago(x.occurredAt), x.at !== x.occurredAt && Math.abs(Date.parse(x.at) - Date.parse(x.occurredAt)) > 3600000 ? h('div', { class: 'small muted' }, 'הגיע לשרת ' + ago(x.at) + ' (המחשב היה בלי אינטרנט)') : null)),
      h('section', { class: 'card' }, h('h2', null, 'התוכנה'),
        field('סוג', REPORT_KIND[x.kind] || x.kind, ' · ', x.auto ? 'אוטומטי' : 'המשתמש לחץ "דיווח על הבעיה"'),
        field('תוכנה', x.app || '—', x.appVersion ? ' ' + x.appVersion : ''),
        field('DSViz', x.dsvizVersion || '—'),
        field('מערכת', x.os || '—'),
        ctx.length ? field('פרטים', h('div', { class: 'stack', style: { gap: '2px' } }, ctx.map(([k, v]) => h('div', { class: 'small ltr', style: { textAlign: 'right' } }, k + ': ' + v)))) : null)),
    x.note ? h('section', { class: 'card' }, h('h2', null, 'מה המשתמש כתב'), h('p', null, x.note)) : null,
    h('section', { class: 'card' }, h('div', { class: 'row', style: { justifyContent: 'space-between' } }, h('h2', null, 'ההודעה'), copy), pre(x.message || '—')),
    r.diag ? h('section', { class: 'card' }, h('h2', null, 'הבדיקה המעמיקה'), h('p', { class: 'small muted' }, 'נאספה אוטומטית במחשב (גרסאות, הגדרות, יומנים). שמות משתמשים בנתיבים הוחלפו.'), pre(r.diag)) : null,
    r.similar.length ? h('section', { class: 'card' }, h('h2', null, `דיווחים דומים (${r.similar.length})`),
      h('div', { class: 'list' }, r.similar.map(o => h('a', { class: 'item', href: '#report=' + encodeURIComponent(o.id) },
        h('div', { class: 'grow' }, reportWho(o)), h('span', { class: 'small muted' }, ago(o.occurredAt)), h('span', { class: 'pill ' + (o.status === 'done' ? 'green' : 'orange') }, o.status === 'done' ? 'טופל' : 'חדש'))))) : null,
    h('section', { class: 'card' }, h('h2', null, 'טיפול'), note, h('div', { class: 'row' }, status, saveNote)),
  ].filter(Boolean);
}

// ------------------------------------------------------------------ יומן

async function logView() {
  const PAGE = 150;
  const box = h('section', { class: 'card' });
  const more = h('button', { type: 'button', class: 'btn', hidden: true }, 'עוד');
  let last = '';
  let dir = orderOf('log');
  const url = () => `/v1/admin/log?limit=${PAGE}&order=${dir}` + (last ? `&${dir === 'asc' ? 'after' : 'before'}=${encodeURIComponent(last)}` : '');
  const load = async reset => {
    if (reset) last = '';
    const n = await api('GET', url());
    if (reset) box.replaceChildren(feed(n.log));
    else if (n.log.length) box.append(feed(n.log));
    if (n.log.length) last = n.log[n.log.length - 1].at;
    more.hidden = n.log.length < PAGE;
  };
  await load(true);
  const toggle = orderToggle('log', async d => { dir = d; box.replaceChildren(loadingView()); try { await load(true); } catch (e) { box.replaceChildren(h('div', { class: 'warn-box' }, e.message || String(e))); } });
  more.onclick = () => busy(more, () => load(false));
  return [h('div', { class: 'titlebar' }, h('h1', null, 'יומן פעולות'), toggle),
    h('p', { class: 'muted' }, 'כל מה שקרה במערכת: כניסות, עמדות, בקשות, תשלומים, ומה שעשו המנהלים.'), box, more];
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
  const mailOn = h('input', { type: 'checkbox', role: 'switch', id: 's-mail', checked: s.alertEmail !== false });
  const mailLabel = h('span', null, s.alertEmail !== false ? 'דולק' : 'כבוי');
  mailOn.addEventListener('change', () => { mailLabel.textContent = mailOn.checked ? 'דולק' : 'כבוי'; });
  const freeOn = h('input', { type: 'checkbox', role: 'switch', id: 's-free', checked: s.studentsFree !== false });
  const freeLabel = h('span', null, s.studentsFree !== false ? 'בחינם' : 'בתשלום');
  freeOn.addEventListener('change', () => { freeLabel.textContent = freeOn.checked ? 'בחינם' : 'בתשלום'; });
  const am = r.alertMail || {};
  // מרכז הבקרה: פתוח לכולם, או רק לגרסה עם מרכז הבקרה + חשבונות הבדיקה
  const ccOn = h('input', { type: 'checkbox', role: 'switch', id: 's-classroom', checked: !!s.classroomOpen });
  const ccLabel = h('span', null, s.classroomOpen ? 'פתוח לכולם' : 'כבוי');
  ccOn.addEventListener('change', () => { ccLabel.textContent = ccOn.checked ? 'פתוח לכולם' : 'כבוי'; });
  const testers = h('textarea', { class: 'field ltr', id: 's-testers', rows: '3', placeholder: 'teacher@school.org.il', style: { textAlign: 'left', height: 'auto', paddingTop: '8px' } });
  testers.value = (s.classroomTesters || []).join('\n');
  const form = h('div', { class: 'stack', style: { gap: '18px', maxWidth: '900px' } },
    h('section', { class: 'card' },
      h('div', { class: 'row', style: { alignItems: 'flex-start', gap: '16px' } },
        h('div', { class: 'grow stack', style: { gap: '6px' } }, h('h2', null, 'שליחת תרגיל ל-Gemini — רק למחוברים'),
          h('p', { class: 'muted', style: { lineHeight: '1.55' } }, 'כשהמתג דולק, רק מורה ותלמיד ששילם (מחוברים עם Google) יכולים לשלוח תרגיל ולקבל הצעה לפתרון. כשהוא כבוי — כל מי שמותקן אצלו DSViz. עמדת מחשב בכיתה לא שולחת לעולם — בעמדה עובדים רק על תרגילים ממאגר השאלות.')),
        h('label', { class: 'switch', for: 's-gem' }, h('span', { class: 'sr' }, 'רק למחוברים'), gem, h('span', { class: 'track', 'aria-hidden': 'true' }, h('span', { class: 'knob' })), gemLabel)),
      h('div', { class: 'warn-box' }, 'מדליקים רק אחרי שכל המחשבים עודכנו לגרסה עם ההתחברות — מחשב עם גרסה ישנה יקבל ״צריך להתחבר / לעדכן את DSViz״.'),
      h('div', { class: 'form3' }, num('monthlyPerSeat', 'שליחות בחודש למורה — לכל עמדה שלו (העמדות עצמן לא שולחות)', s.monthlyPerSeat, 0, 1000), num('monthlyStudent', 'שליחות בחודש — תלמיד רשום', s.monthlyStudent, 0, 5000))),
    h('section', { class: 'card' },
      h('div', { class: 'row', style: { alignItems: 'flex-start', gap: '16px' } },
        h('div', { class: 'grow stack', style: { gap: '6px' } }, h('h2', null, 'התראות כשמצטרפים'),
          h('p', { class: 'muted', style: { lineHeight: '1.55' } }, 'תלמיד חדש, מורה שנכנס בפעם הראשונה, בקשה להרשאת מורה ותשלום — מופיעים בפעמון למעלה עם עיגול אדום, ',
            'ובמייל', am.to ? [' ל-', h('span', { class: 'ltr', style: { fontWeight: 600 } }, am.to)] : '', '.')),
        h('label', { class: 'switch', for: 's-mail' }, h('span', { class: 'sr' }, 'התראות במייל'), mailOn, h('span', { class: 'track', 'aria-hidden': 'true' }, h('span', { class: 'knob' })), mailLabel)),
      am.ready ? null : h('div', { class: 'warn-box' }, 'שליחת המייל עוד לא מוגדרת בשרת (RESEND_API_KEY). עד אז ההתראות מופיעות רק בפעמון.')),
    h('section', { class: 'card' },
      h('div', { class: 'row', style: { alignItems: 'flex-start', gap: '16px' } },
        h('div', { class: 'grow stack', style: { gap: '6px' } }, h('h2', null, 'תלמידים — בחינם'),
          h('p', { class: 'muted', style: { lineHeight: '1.55' } }, 'דולק: תלמיד שנכנס עם Google מקבל את המאגר בלי תשלום (תלמיד עצמאי או עם קוד מורה). כבוי: צריך לשלם (או הענקה ידנית) — המחיר והתאריך למטה.')),
        h('label', { class: 'switch', for: 's-free' }, h('span', { class: 'sr' }, 'תלמידים בחינם'), freeOn, h('span', { class: 'track', 'aria-hidden': 'true' }, h('span', { class: 'knob' })), freeLabel))),
    h('section', { class: 'card', id: 'classroom-card' },
      h('div', { class: 'row', style: { alignItems: 'flex-start', gap: '16px' } },
        h('div', { class: 'grow stack', style: { gap: '6px' } }, h('h2', null, 'מרכז הבקרה — עמדת הניהול, תלמידים, הגשות וציונים'),
          h('p', { class: 'muted', style: { lineHeight: '1.55' } }, 'כבוי: מרכז הבקרה עובד רק בגרסה עם מרכז הבקרה (התקנת הבדיקה), ולחשבונות הבדיקה שלמטה. כל השאר — בגרסה הרגילה — רואים הכול כמו קודם: בלי הרשמת תלמידים, בלי "המורה שלי" ובלי עמדת הניהול החדשה. ',
            'פתוח לכולם: מדליקים כשהגרסה עם מרכז הבקרה יוצאת לכולם.')),
        h('label', { class: 'switch', for: 's-classroom' }, h('span', { class: 'sr' }, 'מרכז הבקרה פתוח לכולם'), ccOn, h('span', { class: 'track', 'aria-hidden': 'true' }, h('span', { class: 'knob' })), ccLabel)),
      h('label', { class: 'lbl', for: 's-testers' }, 'חשבונות בדיקה (מייל בכל שורה) — רואים את מרכז הבקרה בדפדפן גם כשהוא כבוי', testers),
      h('p', { class: 'small muted' }, 'מורה או תלמיד שהתוסף שלהם הוא הגרסה עם מרכז הבקרה נכנסים לבד — לא צריך להוסיף אותם כאן.')),
    h('section', { class: 'card' }, h('h2', null, 'עמדת הניהול של המורה'),
      h('div', { class: 'form2' }, num('teacherReleases', 'כמה עמדות המורה משחרר לבד בחודש (מעבר לזה — בקשה להנהלה)', s.teacherReleases, 0, 100),
        h('p', { class: 'small muted' }, 'הגשות מעמדת מחשב נשמרות רק לאותו יום ונמחקות לבד בחצות.'))),
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
    const body = { requireLoginForGemini: gem.checked, accessUntil: until.value, alertEmail: mailOn.checked, studentsFree: freeOn.checked,
      classroomOpen: ccOn.checked, classroomTesters: testers.value };
    if (ccOn.checked && !s.classroomOpen && !await ask({ title: 'לפתוח את מרכז הבקרה לכולם?', text: 'מעכשיו כל המורים רואים את עמדת הניהול החדשה, ותלמיד שנכנס עם Google — גם מגרסה רגילה — ממלא שם מלא וטלפון ונכנס בחינם.', ok: 'פתיחה לכולם' })) return;
    if (!freeOn.checked && s.studentsFree !== false && !await ask({ title: 'להעביר את התלמידים לתשלום?', text: 'מעכשיו תלמיד בלי תשלום או הענקה יקבל "צריך לשלם" בבדיקה הבאה של המחשב שלו.', ok: 'מעבר לתשלום', danger: true })) return;
    for (const f of form.querySelectorAll('input[data-key]')) body[f.dataset.key] = +f.value;
    body.packages = (s.packages || []).map((p, i) => ({ seats: +form.querySelector(`[data-pk="${i}"][data-f="seats"]`).value, price: +form.querySelector(`[data-pk="${i}"][data-f="price"]`).value }));
    if (gem.checked && !s.requireLoginForGemini && !await ask({ title: 'להדליק "רק למחוברים"?', text: 'מעכשיו מי שלא מחובר (או שהגרסה אצלו ישנה) לא יוכל לשלוח תרגיל ל-Gemini.', ok: 'הדלקה' })) return;
    await api('POST', '/v1/admin/settings', body);
    toast('נשמר — נכנס לתוקף תוך דקה');
    render();
  });
  return [h('h1', null, 'הגדרות'), form, h('div', { class: 'row' }, save, h('span', { class: 'small muted' }, 'שינוי נכנס לתוקף תוך דקה.'))];
}

// ------------------------------------------------------------------ בדיקת מערכת

/** טקסט עם קישור (למשל הקישור ליצירת אינדקס ב-Firebase): רק כתובות https נהפכות לקישור */
function linkify(text) {
  const out = [];
  let last = 0;
  for (const m of String(text || '').matchAll(/https:\/\/[^\s)]+/g)) {
    out.push(text.slice(last, m.index), h('a', { href: m[0], target: '_blank', rel: 'noopener noreferrer' }, 'פתיחה'));
    last = m.index + m[0].length;
  }
  out.push(String(text || '').slice(last));
  return out;
}

/**
 * השרת בודק את עצמו מול Firebase / Google / Cloudflare (GET /v1/admin/selftest, שני חלקים): כתיבה וקריאה, טרנזקציות,
 * כל צורות השאילתה (אינדקסים), כללי האבטחה, מפתח החתימה, הגבלות הקצב, המייל והתשלום. לא משנה שום נתון אמיתי.
 */
async function checkView() {
  const list = h('div', { class: 'stack', id: 'check-list', style: { gap: '8px', maxWidth: '860px' } });
  const summary = h('div', { class: 'note', id: 'check-summary', role: 'status' }, 'בודק…');
  const run = h('button', { type: 'button', class: 'btn primary', id: 'check-run' }, 'בדיקה מחדש');
  const row = c => h('div', { class: 'chk ' + c.state, 'data-id': c.id, 'data-state': c.state },
    h('span', { class: 'mark', 'aria-hidden': 'true' }, c.state === 'ok' ? '✓' : c.state === 'warn' ? '!' : '✕'),
    h('div', { class: 'grow stack', style: { gap: '2px' } }, h('b', null, c.title), c.detail ? h('span', { class: 'd' }, linkify(c.detail)) : null));
  const go = async () => {
    const all = [];
    list.replaceChildren();
    summary.className = 'note';
    summary.textContent = 'בודק את השרת מול Firebase, Google ו-Cloudflare…';
    for (const part of [1, 2]) {
      try {
        all.push(...(await api('GET', '/v1/admin/selftest?part=' + part)).checks);
      } catch (e) {
        all.push({ id: 'part' + part, title: part === 1 ? 'החיבור לנתונים' : 'השאילתות של המערכת', state: 'fail', detail: e.message });
      }
      list.replaceChildren(...all.map(row));
    }
    const bad = all.filter(x => x.state === 'fail').length, notes = all.filter(x => x.state === 'warn').length;
    summary.textContent = bad ? (bad === 1 ? 'בדיקה אחת נכשלה' : `${bad} בדיקות נכשלו`) + ' — ההסבר ליד כל שורה אדומה.'
      : notes ? `הכול עובד. ${notes === 1 ? 'הערה אחת' : notes + ' הערות'} (לא חובה לטפל).` : 'הכול תקין.';
    summary.className = bad ? 'warn-box' : 'note ok';
    summary.dataset.done = bad ? 'fail' : 'ok';
  };
  run.onclick = () => busy(run, go);
  go();
  return [h('h1', null, 'בדיקת מערכת'),
    h('p', { class: 'muted', style: { maxWidth: '860px' } }, 'השרת בודק את עצמו מול Firebase, Google ו-Cloudflare: מה עובד, ומה חסר בהגדרות. הבדיקה לא משנה שום נתון של מורים ותלמידים. כדאי להריץ אותה אחרי כל עדכון של השרת.'),
    h('div', { class: 'row', style: { gap: '12px', flexWrap: 'wrap' } }, run, summary), list];
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
    watchAlerts();
  };
  if (!user) return signin();
  start();
}
main();
