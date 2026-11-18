// /signin — נפתח מתוך DSViz (מורה / תלמיד). כניסה עם Google → קוד חד-פעמי מהשרת → חזרה לתוכנה (http://127.0.0.1:port)
// תלמיד בפעם הראשונה: אחרי Google ולפני החזרה לתוכנה — ההרשמה (שם מלא, טלפון, "עצמאי / יש לי קוד מורה"; register.js)
import { boot, googleSignIn, api, h, icon, ICONS, googleLogo, topBar, config } from '/static/common.js';
import { registerForm, missing } from '/static/register.js';

const app = document.getElementById('app');
const q = new URLSearchParams(location.search);
const role = q.get('role') === 'teacher' ? 'teacher' : 'student';
const port = +q.get('port');
const state = q.get('state') || '';
const challenge = q.get('challenge') || '';
const fromApp = Number.isInteger(port) && port >= 1024 && port <= 65535 && /^[A-Za-z0-9_-]{16,64}$/.test(state) && /^[A-Za-z0-9_-]{43}$/.test(challenge);

let signedIn = null;
function show(...kids) {
  app.replaceChildren(topBar(signedIn), h('main', { class: 'center' }, h('div', { class: 'panel' }, ...kids)));
}

/** תלמיד שעוד לא נרשם (או שעוד לא בחר "עצמאי / מורה"): הטופס, ורק אחריו חוזרים לתוכנה */
function registerStep(me, parts, finish) {
  show(
    h('div', { class: 'badge-icon blue', style: { width: '56px', height: '56px' } }, icon(ICONS.cap, 28, '#1E4FB8')),
    h('h1', null, parts.reg ? 'הרשמה למאגר התרגילים' : 'עוד שאלה אחת'),
    h('p', { class: 'muted', style: { lineHeight: '1.6', fontSize: '16px' } }, parts.reg
      ? 'פעם אחת בלבד' + (config && config.studentsFree === false ? '' : ', בחינם') + '. אחר כך המחשב זוכר אותך.'
      : 'כדי שנדע אם להציג את "הגשה למורה".'),
    registerForm({ me, parts, submit: 'סיום וחזרה לתוכנה', onDone: finish }),
    parts.reg ? h('p', { class: 'small muted' }, 'את הפרטים אפשר לעדכן בכל זמן בדף "החשבון שלי".') : null);
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
      signedIn = user;
      const finish = async () => {
        const r = await api('POST', '/v1/auth/exchange', { idToken: await user.getIdToken(), role, challenge });
        backToApp(r.code);
      };
      if (role === 'student') {
        const me = await api('GET', '/v1/me');
        const parts = missing(me);
        if (parts.reg || parts.mode) return registerStep(me, parts, finish);
      }
      await finish();
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
      : config && config.studentsFree === false
        ? 'נכנסים עם חשבון Google. בפעם הראשונה ממלאים שם מלא וטלפון. אם כבר שילמת — המאגר נפתח מיד, גם במחשב אחר.'
        : 'נכנסים עם חשבון Google. בפעם הראשונה ממלאים שם מלא וטלפון — ההרשמה בחינם. אחרי הכניסה המחשב זוכר.'),
    btn, err,
    h('p', { class: 'small muted' }, 'לא מקלידים סיסמה בתוך התוכנה — הכניסה עם Google נעשית אצל Google.'));
}
main();
