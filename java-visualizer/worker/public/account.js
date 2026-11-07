// /account — החשבון שלי בדפדפן: מורה (הקוד, העמדות, בקשות מההנהלה) / תלמיד (מצב הגישה, תשלומים) / המחשבים המחוברים
import { boot, currentUser, googleSignIn, api, h, icon, ICONS, googleLogo, topBar, fmtDate, ago, daysSince, busy, toast, ask, logoutWeb } from '/static/common.js';

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

function teacherCards(t) {
  const copy = h('button', { type: 'button', class: 'btn primary grow' }, 'העתקת הקוד');
  copy.onclick = async () => { try { await navigator.clipboard.writeText(t.code); toast('הקוד הועתק'); } catch (e) { toast(t.code); } };
  const free = Math.max(0, t.seats - t.used);
  const pct = t.seats ? Math.min(100, Math.round(100 * t.used / t.seats)) : 100;
  const stations = t.stations || [];
  const pendingRelease = new Set(me.requests.filter(r => r.kind === 'release' && r.status === 'open').map(r => r.stationName));
  return [
    h('section', { class: 'card' },
      h('h2', null, 'קוד המורה'),
      h('p', { class: 'muted' }, 'מקלידים את הקוד הזה פעם אחת בכל מחשב של הכיתה ("עמדת מחשב"). כל מחשב שמתחבר משתמש בהיתר אחד.'),
      h('div', { class: 'codebox' }, h('span', { class: 'mono' }, t.code)),
      h('div', { class: 'row' }, copy),
      h('p', { class: 'small muted' }, 'הקוד קבוע — אותו קוד לכל השנים.')),
    h('section', { class: 'card' },
      h('div', { class: 'row', style: { justifyContent: 'space-between', alignItems: 'baseline' } }, h('h2', null, 'עמדות מחשב'),
        h('span', { class: 'muted' }, h('b', { style: { color: 'var(--ink)', fontSize: '20px' } }, String(t.used)), ` מתוך ${t.seats} בשימוש`)),
      h('span', { class: 'bar big ' + (free ? 'ok' : 'full') }, h('span', { style: { width: pct + '%' } })),
      t.sends ? h('div', { class: 'small', style: { marginTop: '10px' } }, 'שליחות תרגיל החודש (המורה וכל העמדות יחד): ', h('b', null, String(t.sends.n)), ` מתוך ${t.sends.limit}`, t.sends.n >= t.sends.limit ? ' — המנה נגמרה, תתחדש בתחילת החודש הבא' : '') : null,
      h('div', { class: 'small muted' }, free ? `נשארו ${free} היתרים פנויים` : 'כל ההיתרים בשימוש — מחשב חדש יקבל "אין עוד היתרים"'),
      h('div', { class: 'list' }, stations.length ? stations.map(s => h('div', { class: 'item' + (daysSince(s.lastSeen) > 30 ? ' stale' : '') },
        h('div', { class: 'grow' }, h('div', { style: { fontWeight: 600 } }, s.name), h('div', { class: 'small muted' }, (daysSince(s.lastSeen) > 30 ? 'לא נראתה ' + daysSince(s.lastSeen) + ' יום' : 'פעילה ' + ago(s.lastSeen)))),
        pendingRelease.has(s.name) ? h('span', { class: 'pill orange' }, 'בקשה נשלחה')
          : h('a', { href: '#', class: 'small', style: { fontWeight: 600 }, onclick: e => { e.preventDefault(); requestDialog('release', s); } }, 'בקשה לשחרר')))
        : h('div', { class: 'empty' }, 'עוד אין מחשבים מחוברים.')),
      h('span', { class: 'small muted' }, 'מחשב שהוחלף? "בקשה לשחרר" — וההנהלה מחזירה את ההיתר.')),
    packagesCard(),
  ];
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

function studentCard() {
  const s = me.student;
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
  const head = h('div', { class: 'titlebar' }, h('h1', null, me.teacher ? 'הלוח של המורה' : 'החשבון שלי'),
    me.admin ? h('a', { class: 'btn dark', href: '/admin' }, icon(ICONS.shield, 18, '#fff'), 'מסוף ההנהלה') : null);
  const body = me.teacher
    ? [h('div', { class: 'grid2' }, ...teacherCards(me.teacher)), h('div', { class: 'grid2' }, requestsCard() || h('span'), devicesCard())]
    : [h('div', { class: 'grid2' }, studentCard(), devicesCard())];
  if (me.teacher && !me.teacher.active) body.unshift(h('div', { class: 'warn-box' }, 'החשבון שלך כמורה הושבת — העמדות שלך ננעלות בבדיקה הבאה. פונים להנהלה.'));
  app.replaceChildren(topBar(user, { badge, onLogout: async () => { await logoutWeb(); location.reload(); } }), h('main', { class: 'page' }, head, ...body));
}

async function main() {
  try { await boot(); } catch (e) { app.replaceChildren(topBar(null), h('div', { class: 'loading' }, 'הכניסה עוד לא פתוחה.')); return; }
  user = await currentUser();
  if (!user) {
    const b = h('button', { type: 'button', class: 'gbtn' }, googleLogo(), 'התחברות עם Google');
    b.onclick = () => busy(b, async () => { user = await googleSignIn(); await load(); });
    app.replaceChildren(topBar(null), h('main', { class: 'center' }, h('div', { class: 'panel' },
      h('h1', null, 'החשבון שלי'), h('p', { class: 'muted', style: { lineHeight: '1.6' } }, 'מורים רואים כאן את קוד המורה ואת העמדות; תלמידים — את מצב המאגר והתשלומים.'), b)));
    return;
  }
  try {
    if (new URLSearchParams(location.search).get('r') === 'paid') { await load(); await waitSeats(); }
    await load();
  } catch (e) { app.replaceChildren(topBar(user), h('div', { class: 'loading' }, e.message)); }
}
main();
