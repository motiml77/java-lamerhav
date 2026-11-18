// /account — החשבון שלי בדפדפן: מורה — עמדת הניהול (console.js: מחשבי המעבדה, תלמידים בכיתות, הגשות), חבילות, בקשות מההנהלה;
// תלמיד — עצמאי / משויך למורה (קוד המורה), מצב המאגר; והמחשבים המחוברים
import { boot, currentUser, googleSignIn, api, h, icon, ICONS, googleLogo, topBar, fmtDate, ago, daysSince, busy, toast, ask, logoutWeb } from '/static/common.js';
import { mountConsole } from '/static/console.js';
import { registerForm, missing } from '/static/register.js';

const app = document.getElementById('app');
let user = null;
let me = null;

const STATUS = { open: ['ממתינה', 'orange'], approved: ['אושרה', 'green'], declined: ['נדחתה', 'gray'] };

function requestText(r) {
  if (r.kind === 'seats') return `${r.amount} עמדות נוספות`;   // בקשות ישנות
  if (r.kind === 'release') return `שחרור ״${r.stationName}״`;
  return 'הרשאת מורה';
}

function requestDialog(kind, station) {   // רק "לשחרר מחשב" — עמדות נוספות קונים בחבילה
  const d = h('dialog', { class: 'modal', 'aria-labelledby': 'rq-t' });
  const amount = h('input', { class: 'field num', type: 'number', min: '1', max: '500', value: '5', id: 'rq-n', style: { width: '96px' } });
  const note = h('textarea', { class: 'field', rows: '2', id: 'rq-note', placeholder: kind === 'seats' ? 'למשל: נפתחה מעבדה חדשה עם 10 מחשבים' : 'למשל: המחשב הוחלף בחדש' });
  const send = h('button', { type: 'button', class: 'btn primary' }, 'שליחת הבקשה');
  const close = () => { d.close(); d.remove(); };
  send.onclick = () => busy(send, async () => {
    const body = kind === 'seats' ? { kind, amount: +amount.value, note: note.value } : { kind, stationId: station.id, note: note.value };
    const r = await api('POST', '/v1/me/request', body);
    close();
    toast(r.already ? 'כבר יש בקשה כזו שמחכה להנהלה.' : 'הבקשה נשלחה להנהלה.');
    load();
  });
  d.append(h('div', { class: 'in' },
    h('h2', { id: 'rq-t' }, kind === 'seats' ? 'בקשת עמדות נוספות' : `בקשה לשחרר את ״${station.name}״`),
    h('p', { class: 'muted' }, kind === 'seats' ? 'ההנהלה רואה את הבקשה ומחליטה. אחרי האישור המספר החדש מופיע כאן מיד.' : 'אחרי שההנהלה משחררת, ההיתר חוזר אליך ואפשר לחבר מחשב אחר.'),
    kind === 'seats' ? h('label', { class: 'lbl', for: 'rq-n', style: { flexDirection: 'row', alignItems: 'center', gap: '12px' } }, 'כמה עמדות להוסיף', amount) : null,
    h('label', { class: 'lbl', for: 'rq-note' }, 'הסבר קצר (לא חובה)', note),
    h('div', { class: 'row' }, send, h('button', { type: 'button', class: 'btn', onclick: close }, 'ביטול'))));
  d.addEventListener('cancel', e => { e.preventDefault(); close(); });
  document.body.append(d);
  d.showModal();
}

function packagesCard() {
  const list = me.packages || [];
  return h('section', { class: 'card', style: { gridColumn: '1 / -1' } },
    h('div', { class: 'row wrap', style: { justifyContent: 'space-between', alignItems: 'baseline' } }, h('h2', null, 'עוד עמדות מחשב'),
      h('span', { class: 'small muted' }, 'העמדות מתווספות מיד אחרי התשלום.')),
    list.length ? h('div', { class: 'packs' }, list.map(p => {
      const b = h('button', { type: 'button', class: 'btn primary wide' }, `קנייה · ${p.price} ₪`);
      b.onclick = () => busy(b, async () => { const r = await api('POST', '/v1/pay/start', { kind: 'seats', package: p.index }); location.href = r.url; });
      return h('div', { class: 'pack' }, h('div', { class: 'display', style: { fontSize: '30px', fontWeight: 700 } }, String(p.seats)), h('div', { class: 'muted' }, 'עמדות נוספות'), b);
    })) : h('p', { class: 'muted' }, 'החבילות עוד לא פתוחות לקנייה. בינתיים — פונים להנהלה.'));
}

async function waitSeats() {
  // חזרה מדף התשלום: מחכים שהתשלום יאושר (בדרך כלל כמה שניות) ואז מציגים את המספר החדש
  const since = Date.now() - 15 * 60000;
  for (let i = 0; i < 40; i++) {
    const m = await api('GET', '/v1/me').catch(() => null);
    const paid = m && m.payments.find(p => p.kind === 'seats' && p.status === 'paid' && Date.parse(p.paidAt) > since);
    if (paid) { history.replaceState(null, '', '/account'); toast(`התשלום התקבל — נוספו ${paid.seats} עמדות`); return; }
    await new Promise(r => setTimeout(r, 3000));
  }
  toast('התשלום עוד לא אושר. אם חויבת — העמדות יתווספו לבד בדקות הקרובות.', true);
}

function requestsCard() {
  if (!me.requests.length) return null;
  return h('section', { class: 'card' }, h('h2', null, 'הבקשות שלי'),
    h('div', { class: 'stack' }, me.requests.map(r => h('div', { class: 'list' }, h('div', { class: 'item' },
      h('div', { class: 'grow' }, h('div', { style: { fontWeight: 600 } }, requestText(r)),
        h('div', { class: 'small muted' }, r.status === 'open' ? 'נשלחה ' + ago(r.createdAt) : (r.status === 'approved' ? 'אושרה ' : 'נדחתה ') + fmtDate(r.decidedAt) + (r.reason ? ' · ' + r.reason : ''))),
      h('span', { class: 'pill ' + STATUS[r.status][1] }, STATUS[r.status][0]))))));
}

/** תלמיד: עצמאי, או משויך למורה בקוד הקבוע של המורה (מופיע אצלו ב"תלמידים", ויכול להגיש לו תרגילים) */
function teacherLinkCard() {
  const st = me.student.state || { mode: '', teacher: null };
  const code = h('input', { class: 'field mono', id: 'join-code', maxlength: '6', placeholder: 'W8S2', 'aria-label': 'קוד המורה', style: { width: '120px', textAlign: 'center' } });
  const join = h('button', { type: 'button', class: 'btn primary' }, 'הצטרפות');
  const doJoin = replace => busy(join, async () => {
    const r = await api('POST', '/v1/student/join', { code: code.value, replace: !!replace });
    if (r.status === 'replace') {
      if (await ask({ title: `לעבור למורה ${r.teacher.name}?`, text: 'אפשר להיות משויך למורה אחד. המורה הקודם יראה אותך כ"עזב", והתרגילים שהגשת לו נשארים אצלו.', ok: 'מעבר' })) return doJoin(true);
      return;
    }
    const msg = { 'bad-code': 'הקוד לא נכון. בודקים אותו מול המורה (4 תווים, אותיות באנגלית וספרות).', closed: 'המורה סגר כרגע את ההצטרפות. פונים למורה.', blocked: 'המורה הזה לא פעיל כרגע.' }[r.status];
    if (msg) { toast(msg, true); return; }
    toast(`הצטרפת למורה ${r.teacher.name}`);
    load();
  });
  join.onclick = () => doJoin(false);
  code.onkeydown = e => { if (e.key === 'Enter') doJoin(false); };
  const form = h('div', { class: 'stack' },
    h('label', { class: 'lbl', for: 'join-code' }, 'קוד המורה'),
    h('div', { class: 'row' }, code, join),
    h('p', { class: 'small muted' }, 'המורה יראה את השם שלך, את המייל ואת התרגילים שתגיש לו.'));
  if (st.teacher) {
    const leave = h('button', { type: 'button', class: 'btn sm' }, 'יציאה מהמורה');
    leave.onclick = async () => {
      if (!await ask({ title: `לצאת מהמורה ${st.teacher.name}?`, text: 'לא תוכל להגיש לו תרגילים עד שתצטרף שוב עם הקוד. התרגילים שכבר הגשת נשארים אצלו.', ok: 'יציאה', danger: true })) return;
      busy(leave, async () => { await api('POST', '/v1/student/leave', {}); toast('יצאת מהמורה'); load(); });
    };
    return h('section', { class: 'card' }, h('h2', null, 'המורה שלי'),
      h('div', { class: 'row' }, h('span', { class: 'pill green' }, 'משויך'), h('b', null, st.teacher.name), h('span', { class: 'muted' }, st.teacher.school || '')),
      h('p', { class: 'small muted' }, 'בתוכנה (Eclipse, Visual Studio, IntelliJ): הכפתור "הגשה למורה" שולח לו את התרגיל.'), leave);
  }
  if (st.mode === 'independent') {
    return h('section', { class: 'card' }, h('h2', null, 'המורה שלי'),
      h('div', { class: 'row' }, h('span', { class: 'pill gray' }, 'תלמיד עצמאי')),
      h('p', { class: 'muted' }, 'יש לך קוד מורה? אפשר להצטרף בכל זמן:'), form);
  }
  const indep = h('button', { type: 'button', class: 'btn' }, 'תלמיד עצמאי');
  indep.onclick = () => busy(indep, async () => { await api('POST', '/v1/student/mode', { mode: 'independent' }); load(); });
  return h('section', { class: 'card' }, h('h2', null, 'תלמיד עצמאי, או עם מורה?'),
    h('p', { class: 'muted' }, 'עם קוד מורה — המורה רואה את התרגילים שתגיש לו. בלי — לומדים לבד; אפשר להצטרף למורה גם אחר כך.'),
    h('div', { class: 'row' }, indep), form);
}

/** תלמיד: ההרשמה (שם מלא וטלפון). עוד לא נרשם — הטופס (עם "עצמאי / קוד מורה" אם עוד לא בחר); נרשם — הפרטים, ו"עדכון" */
function detailsCard() {
  const s = me.student;
  const parts = missing(me);
  if (parts.reg) {
    return h('section', { class: 'card', style: { gridColumn: '1 / -1' } }, h('h2', null, 'השלמת ההרשמה'),
      h('p', { class: 'muted' }, 'כדי להשתמש במאגר התרגילים ממלאים פעם אחת שם מלא וטלפון' + (s.free ? ' — בחינם.' : '.')),
      h('div', { style: { maxWidth: '460px' } }, registerForm({ me, parts, onDone: async () => { toast('ההרשמה הושלמה'); await load(); } })));
  }
  const edit = h('button', { type: 'button', class: 'btn sm' }, 'עדכון');
  const card = h('section', { class: 'card' }, h('h2', null, 'הפרטים שלי'),
    h('div', { class: 'row wrap' }, h('b', null, s.fullName), h('span', { class: 'muted ltr' }, s.phone.replace(/^(\d{3})(\d{7})$/, '$1-$2'))),
    h('div', { class: 'small muted ltr', style: { textAlign: 'right' } }, me.email), edit);
  edit.onclick = () => card.replaceChildren(h('h2', null, 'הפרטים שלי'),
    registerForm({ me, parts: { reg: true, mode: false }, submit: 'שמירה', onDone: async () => { toast('הפרטים נשמרו'); await load(); } }),
    h('button', { type: 'button', class: 'btn sm', onclick: () => load() }, 'ביטול'));
  return card;
}

function studentCard() {
  const s = me.student;
  if (s.free) {
    return h('section', { class: 'card' }, h('h2', null, 'מאגר התרגילים'),
      h('div', { class: 'row' }, h('span', { class: 'pill green' }, 'פתוח'), h('span', null, 'בלי תשלום')),
      s.sends ? h('div', { class: 'small' }, 'שליחות תרגיל החודש: ', h('b', null, String(s.sends.n)), ` מתוך ${s.sends.limit}`) : null);
  }
  return h('section', { class: 'card' }, h('h2', null, 'מאגר התרגילים'),
    s.sends && s.open ? h('div', { class: 'small', style: { marginBottom: '8px' } }, 'שליחות תרגיל החודש: ', h('b', null, String(s.sends.n)), ` מתוך ${s.sends.limit}`) : null,
    s.open ? h('div', { class: 'row' }, h('span', { class: 'pill green' }, 'פתוח'), h('span', null, `עד ${fmtDate(s.until)}${s.source === 'manual' ? ' · הענקה מההנהלה' : ''}`))
      : h('div', { class: 'stack' }, h('div', { class: 'row' }, h('span', { class: 'pill gray' }, 'סגור'), h('span', { class: 'muted' }, me.canPay ? `תשלום אחד של ${me.price} ₪ פותח עד ${fmtDate(me.accessUntil)}` : 'התשלום עוד לא פתוח')),
        me.canPay ? h('a', { class: 'btn primary', href: '/welcome?r=pay', style: { alignSelf: 'flex-start' } }, 'לתשלום') : null),
    me.payments.length ? h('div', { class: 'list' }, me.payments.map(p => h('div', { class: 'item' },
      h('div', { class: 'grow' }, `${p.kind === 'seats' ? `חבילת ${p.seats} עמדות · ` : ''}${p.amount} ₪ · ${fmtDate(p.paidAt || p.createdAt)}`),
      h('span', { class: 'pill ' + (p.status === 'paid' ? 'green' : 'gray') }, p.status === 'paid' ? 'שולם' : p.status === 'refunded' ? 'הוחזר' : 'לא הושלם')))) : null);
}

function devicesCard() {
  const items = me.devices;
  return h('section', { class: 'card' }, h('h2', null, 'המחשבים שלי'),
    h('p', { class: 'small muted' }, 'המחשבים שבהם נכנסת עם החשבון הזה. מחשב שנותק — יצטרך להתחבר שוב.'),
    h('div', { class: 'list' }, items.length ? items.map(d => {
      const b = h('button', { type: 'button', class: 'btn sm' }, 'ניתוק');
      b.onclick = async () => {
        if (!await ask({ title: `לנתק את ״${d.name || 'המחשב'}״?`, text: 'במחשב הזה יצטרכו להתחבר שוב כדי לפתוח את המאגר.', ok: 'ניתוק', danger: true })) return;
        busy(b, async () => { await api('POST', '/v1/me/devices/revoke', { id: d.id }); toast('המחשב נותק'); load(); });
      };
      return h('div', { class: 'item' }, icon(ICONS.monitor, 20, '#5A6478'),
        h('div', { class: 'grow' }, h('div', { style: { fontWeight: 600 } }, d.name || 'מחשב'), h('div', { class: 'small muted' }, (d.role === 'teacher' ? 'כמורה · ' : '') + 'פעיל ' + ago(d.lastSeen))), b);
    }) : h('div', { class: 'empty' }, 'אין מחשבים מחוברים.')));
}

async function load() {
  me = await api('GET', '/v1/me');
  const badge = me.teacher ? h('span', { class: 'pill blue' }, 'מורה') : null;
  const head = h('div', { class: 'titlebar' }, h('h1', null, me.teacher ? 'עמדת הניהול' : 'החשבון שלי'),
    me.admin ? h('a', { class: 'btn dark', href: '/admin' }, icon(ICONS.shield, 18, '#fff'), 'מסוף ההנהלה') : null);
  const con = h('div', { id: 'console' }, h('div', { class: 'loading' }, 'רגע…'));
  const body = me.teacher
    ? [con, h('div', { class: 'grid2' }, packagesCard(), requestsCard() || devicesCard()), me.requests.length ? h('div', { class: 'grid2' }, devicesCard()) : null]
    : missing(me).reg ? [h('div', { class: 'grid2' }, detailsCard()), h('div', { class: 'grid2' }, studentCard(), devicesCard())]
      : [h('div', { class: 'grid2' }, detailsCard(), teacherLinkCard()), h('div', { class: 'grid2' }, studentCard(), devicesCard())];
  if (me.teacher && !me.teacher.active) body.unshift(h('div', { class: 'warn-box' }, 'החשבון שלך כמורה הושבת — העמדות שלך ננעלות בבדיקה הבאה. פונים להנהלה.'));
  app.replaceChildren(topBar(user, { badge, onLogout: async () => { await logoutWeb(); location.reload(); } }), h('main', { class: 'page' }, head, ...body));
  if (me.teacher && me.teacher.active) {
    try { await mountConsole(con, { requestRelease: s => requestDialog('release', s) }); } catch (e) { con.replaceChildren(h('div', { class: 'warn-box' }, e.message)); }
  } else if (me.teacher) con.remove();
}

async function main() {
  try { await boot(); } catch (e) { app.replaceChildren(topBar(null), h('div', { class: 'loading' }, 'הכניסה עוד לא פתוחה.')); return; }
  user = await currentUser();
  if (!user) {
    const b = h('button', { type: 'button', class: 'gbtn' }, googleLogo(), 'התחברות עם Google');
    b.onclick = () => busy(b, async () => { user = await googleSignIn(); await load(); });
    app.replaceChildren(topBar(null), h('main', { class: 'center' }, h('div', { class: 'panel' },
      h('h1', null, 'החשבון שלי'), h('p', { class: 'muted', style: { lineHeight: '1.6' } }, 'מורים — עמדת הניהול: מחשבי המעבדה, התלמידים והתרגילים שהוגשו. תלמידים — הצטרפות למורה ומצב המאגר.'), b)));
    return;
  }
  try {
    if (new URLSearchParams(location.search).get('r') === 'paid') { await load(); await waitSeats(); }
    await load();
  } catch (e) { app.replaceChildren(topBar(user), h('div', { class: 'loading' }, e.message)); }
}
main();
