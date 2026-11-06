// /welcome — אחרי הכניסה מתוך התוכנה (התוסף מפנה לכאן עם המצב): הצלחה / תשלום / בקשת הרשאת מורה / מחשבים
import { boot, currentUser, googleSignIn, api, h, icon, ICONS, googleLogo, topBar, fmtDate, ago, busy, toast, logoutWeb } from '/static/common.js';

const app = document.getElementById('app');
const q = new URLSearchParams(location.search);
const r = q.get('r') || 'ok';
const role = q.get('role') === 'teacher' ? 'teacher' : 'student';
let user = null;

function show(...kids) {
  app.replaceChildren(topBar(user, { onLogout: async () => { await logoutWeb(); location.href = '/account'; } }),
    h('main', { class: 'center' }, h('div', { class: 'panel c' }, ...kids)));
}
const done = (title, text, ...more) => show(
  h('div', { class: 'badge-icon green', style: { width: '72px', height: '72px' } }, icon(ICONS.check, 38, '#1F7A44', 2.4)),
  h('h1', null, title), h('p', { class: 'muted', style: { fontSize: '16px', lineHeight: '1.6' } }, text), ...more);

async function needUser() {
  user = await currentUser();
  if (user) return user;
  return new Promise(resolve => {
    const b = h('button', { type: 'button', class: 'gbtn' }, googleLogo(), 'התחברות עם Google');
    b.onclick = () => busy(b, async () => { user = await googleSignIn(); resolve(user); });
    show(h('h1', null, 'עוד צעד אחד'), h('p', { class: 'muted' }, 'נכנסים שוב עם אותו חשבון Google כדי להמשיך.'), b);
  });
}

function openScreen(me) {
  done('המאגר פתוח', `מעכשיו המחשב זוכר אותך: חוזרים ל-DSViz ופותחים את מאגר התרגילים.${me && me.student && me.student.until ? ' פתוח עד ' + fmtDate(me.student.until) + '.' : ''}`);
}

async function pay() {
  await needUser();
  const me = await api('GET', '/v1/me');
  if (me.student.open) return openScreen(me);
  if (!me.canPay) {
    return show(h('div', { class: 'badge-icon orange' }, icon(ICONS.info, 30, '#9A3412')), h('h1', null, 'התשלום עוד לא פתוח'),
      h('p', { class: 'muted' }, 'עוד מעט אפשר יהיה לשלם כאן. בינתיים, אם המורה פתח עמדה בכיתה — המאגר פתוח שם.'));
  }
  const go = h('button', { type: 'button', class: 'btn primary lg wide' }, icon(ICONS.lock, 20, '#fff'), 'מעבר לתשלום מאובטח');
  go.onclick = () => busy(go, async () => {
    const s = await api('POST', '/v1/pay/start', {});
    if (s.already) return openScreen(await api('GET', '/v1/me'));
    location.href = s.url;
  });
  show(
    h('div', { class: 'stack', style: { gap: '6px', textAlign: 'right', width: '100%' } },
      h('h1', null, 'פתיחת המאגר לשנת הלימודים'), h('p', { class: 'muted', style: { fontSize: '16px' } }, 'תשלום אחד — בלי מנוי מתחדש ובלי חיובים נוספים.')),
    h('div', { class: 'price' },
      h('div', { class: 'stack', style: { gap: '4px' } }, h('span', { class: 'display', style: { fontWeight: 600, fontSize: '18px' } }, 'מאגר התרגילים'),
        h('span', { class: 'small muted' }, `פתוח עד ${fmtDate(me.accessUntil)} · בכל המחשבים שלך`)),
      h('div', { class: 'row', style: { gap: '4px', alignItems: 'baseline' } }, h('span', { class: 'display', style: { fontWeight: 700, fontSize: '32px' } }, String(me.price)), h('span', { style: { fontSize: '18px', fontWeight: 600 } }, '₪'))),
    h('div', { class: 'stack', style: { gap: '8px', width: '100%', textAlign: 'right' } }, h('span', { class: 'small', style: { fontWeight: 700 } }, 'אפשר לשלם ב:'),
      h('div', { class: 'chips' }, ['ביט', 'כרטיס אשראי', 'Apple Pay', 'Google Pay'].map(x => h('span', { class: 'chip' }, x)))),
    go,
    h('p', { class: 'small muted', style: { textAlign: 'right' } }, 'התשלום נעשה בדף המאובטח של חברת הסליקה — אנחנו לא רואים את פרטי הכרטיס. קבלה נשלחת אוטומטית למייל.'));
}

async function waitPaid() {
  await needUser();
  show(h('div', { class: 'spinner' }), h('h1', null, 'מחכים לאישור התשלום'),
    h('p', { class: 'muted', style: { lineHeight: '1.6' } }, 'זה לוקח בדרך כלל כמה שניות. גם החלון בתוכנה מתעדכן לבד — לא צריך ללחוץ על כלום.'));
  for (let i = 0; i < 60; i++) {
    const me = await api('GET', '/v1/me').catch(() => null);
    if (me && me.student.open) return openScreen(me);
    await new Promise(res => setTimeout(res, 3000));
  }
  show(h('h1', null, 'התשלום עוד לא אושר'), h('p', { class: 'muted' }, 'אם חויבת — האישור יגיע בדקות הקרובות, והמאגר ייפתח לבד. אפשר לסגור את הדף.'),
    h('a', { class: 'btn', href: '/welcome?r=paid' }, 'לבדוק שוב'));
}

async function notTeacher() {
  await needUser();
  const school = h('input', { class: 'field', type: 'text', placeholder: 'שם בית הספר והעיר', id: 'school' });
  const computers = h('input', { class: 'field', type: 'number', min: '0', max: '2000', placeholder: '30', id: 'computers' });
  const phone = h('input', { class: 'field ltr', type: 'tel', inputmode: 'numeric', placeholder: '050-1234567', id: 'phone', autocomplete: 'tel', style: { textAlign: 'left' } });
  const send = h('button', { type: 'button', class: 'btn primary lg wide' }, 'שליחת בקשה להנהלה');
  send.onclick = () => busy(send, async () => {
    if (!school.value.trim()) { school.focus(); return toast('כתבו את שם בית הספר.', true); }
    let ph = phone.value.replace(/[\s\-().]/g, '').replace(/^\+?972/, '0');
    if (!/^05\d{8}$/.test(ph)) { phone.focus(); return toast('כתבו נייד תקני של 10 ספרות, למשל 050-1234567.', true); }
    await api('POST', '/v1/me/request', { kind: 'teacher', school: school.value, phone: ph, computers: +computers.value || 0 });
    done('הבקשה נשלחה', 'אחרי שההנהלה מאשרת, נכנסים שוב מתוך DSViz עם אותו חשבון — והלוח של המורה נפתח.');
  });
  show(
    h('div', { class: 'badge-icon blue', style: { width: '56px', height: '56px' } }, icon(ICONS.info, 28, '#1E4FB8')),
    h('h1', null, 'החשבון עוד לא מוגדר כמורה'),
    h('p', { class: 'muted', style: { lineHeight: '1.6' } }, 'את המורים מגדירה ההנהלה. אפשר לשלוח בקשה מכאן — אחרי האישור נכנסים שוב עם אותו חשבון.'),
    h('div', { class: 'stack', style: { width: '100%', textAlign: 'right', gap: '12px' } },
      h('label', { class: 'lbl', for: 'school' }, 'בית הספר', school),
      h('label', { class: 'lbl', for: 'phone' }, 'טלפון נייד (10 ספרות)', phone),
      h('label', { class: 'lbl', for: 'computers' }, 'כמה מחשבים יש בכיתה (בערך)', computers)),
    send,
    h('a', { href: '#', class: 'small', onclick: async e => { e.preventDefault(); await logoutWeb(); user = null; notTeacher(); } }, 'כניסה עם חשבון אחר'));
}

async function devices() {
  await needUser();
  const me = await api('GET', '/v1/me');
  const list = h('div', { class: 'list', style: { width: '100%' } });
  const fill = items => list.replaceChildren(...(items.length ? items.map(d => h('div', { class: 'item' },
    h('div', { class: 'grow', style: { textAlign: 'right' } }, h('div', { style: { fontWeight: 600 } }, d.name || 'מחשב'), h('div', { class: 'small muted' }, 'פעיל ' + ago(d.lastSeen))),
    (() => { const b = h('button', { type: 'button', class: 'btn sm' }, 'ניתוק'); b.onclick = () => busy(b, async () => { await api('POST', '/v1/me/devices/revoke', { id: d.id }); fill(items.filter(x => x !== d)); toast('המחשב נותק. עכשיו חוזרים לתוכנה ולוחצים שוב "התחברות".'); }); return b; })()))
    : [h('div', { class: 'empty' }, 'אין מחשבים מחוברים — חוזרים לתוכנה ולוחצים "התחברות".')]));
  fill(me.devices);
  show(h('h1', null, 'החשבון כבר מחובר בכל המחשבים המותרים'),
    h('p', { class: 'muted', style: { lineHeight: '1.6' } }, 'מנתקים מחשב שכבר לא בשימוש, וחוזרים לתוכנה ללחוץ שוב "התחברות".'), list);
}

async function main() {
  try { await boot(); } catch (e) { show(h('h1', null, 'הכניסה עוד לא פתוחה')); return; }
  try {
    if (r === 'pay') return await pay();
    if (r === 'paid') return await waitPaid();
    if (r === 'not-teacher') return await notTeacher();
    if (r === 'devices') return await devices();
    user = await currentUser();
    if (r === 'blocked') {
      return show(h('div', { class: 'badge-icon orange' }, icon(ICONS.lock, 30, '#9A3412')), h('h1', null, 'הגישה של החשבון הושבתה'),
        h('p', { class: 'muted' }, 'פונים להנהלה כדי לברר.'));
    }
    if (r === 'error') {
      return show(h('h1', null, 'הכניסה לא הצליחה'), h('p', { class: 'muted' }, q.get('m') || 'חוזרים לתוכנה ולוחצים שוב "התחברות".'));
    }
    if (role === 'teacher') {
      return done('נכנסת בהצלחה', 'אפשר לחזור ל-DSViz — מאגר התרגילים פתוח, ושליחת תרגיל זמינה.',
        h('a', { class: 'btn', href: '/account' }, 'ללוח המורה — הקוד והעמדות'));
    }
    const me = user ? await api('GET', '/v1/me').catch(() => null) : null;
    openScreen(me);
  } catch (e) {
    show(h('h1', null, 'משהו השתבש'), h('p', { class: 'muted' }, e.message || String(e)));
  }
}
main();
