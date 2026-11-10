// /signin — נפתח מתוך DSViz (מורה / תלמיד). כניסה עם Google → קוד חד-פעמי מהשרת → חזרה לתוכנה (http://127.0.0.1:port)
import { boot, googleSignIn, api, h, icon, ICONS, googleLogo, topBar } from '/static/common.js';

const app = document.getElementById('app');
const q = new URLSearchParams(location.search);
const role = q.get('role') === 'teacher' ? 'teacher' : 'student';
const port = +q.get('port');
const state = q.get('state') || '';
const challenge = q.get('challenge') || '';
const fromApp = Number.isInteger(port) && port >= 1024 && port <= 65535 && /^[A-Za-z0-9_-]{16,64}$/.test(state) && /^[A-Za-z0-9_-]{43}$/.test(challenge);

function show(...kids) {
  app.replaceChildren(topBar(null), h('main', { class: 'center' }, h('div', { class: 'panel' }, ...kids)));
}

function backToApp(code) {
  show(h('div', { class: 'row', style: { gap: '14px' } }, h('div', { class: 'spinner', style: { width: '32px', height: '32px', borderWidth: '4px' } }), h('h1', { style: { fontSize: '22px' } }, 'מחזירים אותך לתוכנה…')),
    h('p', { class: 'muted' }, 'אם הדף לא משתנה תוך כמה שניות — חוזרים ל-DSViz, הכניסה כבר שם.'));
  location.replace(`http://127.0.0.1:${port}/done?code=${encodeURIComponent(code)}&state=${encodeURIComponent(state)}`);
}

async function main() {
  try { await boot(); } catch (e) {
    show(h('h1', null, 'הכניסה עוד לא פתוחה'), h('p', { class: 'muted' }, 'נסו שוב מאוחר יותר.'));
    return;
  }
  if (!fromApp) {
    show(h('h1', null, 'פותחים את הכניסה מתוך DSViz'),
      h('p', { class: 'muted', style: { lineHeight: '1.6' } }, 'בתוכנה פותחים את מאגר התרגילים, בוחרים "מורה" או "תלמיד" ולוחצים "כניסה עם Google" — והדף הזה נפתח עם הכול מוכן. כאן בדפדפן אפשר לראות את החשבון שלך:'),
      h('a', { class: 'btn primary lg', href: '/account' }, 'החשבון שלי'));
    return;
  }
  const err = h('p', { role: 'alert', class: 'warn-box', hidden: true });
  const btn = h('button', { type: 'button', class: 'gbtn' }, googleLogo(), 'התחברות עם Google');
  btn.addEventListener('click', async () => {
    err.hidden = true;
    btn.disabled = true;
    try {
      const user = await googleSignIn();
      const r = await api('POST', '/v1/auth/exchange', { idToken: await user.getIdToken(), role, challenge });
      backToApp(r.code);
    } catch (e) {
      btn.disabled = false;
      const closed = /popup-closed|cancelled-popup|user-cancelled/.test(String(e && e.code));
      const blocked = /popup-blocked/.test(String(e && e.code));
      err.textContent = closed ? 'החלון של Google נסגר לפני שהכניסה הסתיימה. לוחצים שוב.'
        : blocked ? 'הדפדפן חסם את החלון של Google. מאשרים חלונות קופצים לאתר הזה ולוחצים שוב.'
        : (e && e.message) || 'הכניסה לא הצליחה. נסו שוב.';
      err.hidden = false;
    }
  });
  const teacher = role === 'teacher';
  show(
    h('div', { class: 'badge-icon blue', style: { width: '56px', height: '56px' } }, icon(teacher ? ICONS.user : ICONS.cap, 28, '#1E4FB8')),
    h('h1', null, teacher ? 'כניסת מורה' : 'כניסת תלמיד'),
    h('p', { class: 'muted', style: { lineHeight: '1.6', fontSize: '16px' } }, teacher
      ? 'נכנסים עם חשבון Google שההנהלה הגדירה כמורה. אחרי הכניסה הדפדפן מחזיר אותך לתוכנה, והמחשב זוכר.'
      : 'נכנסים עם חשבון Google. אם כבר שילמת — המאגר נפתח מיד, גם במחשב אחר. אחרי הכניסה המחשב זוכר.'),
    btn, err,
    h('p', { class: 'small muted' }, 'לא מקלידים סיסמה בתוך התוכנה — הכניסה עם Google נעשית אצל Google.'));
}
main();
