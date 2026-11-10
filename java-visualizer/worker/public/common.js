// משותף לכל הדפים: Firebase (כניסה עם Google), קריאות לשרת, ובניית תצוגה בלי innerHTML (אין הזרקת קוד מנתונים)
import { initializeApp } from 'https://www.gstatic.com/firebasejs/10.14.1/firebase-app.js';
import {
  getAuth, GoogleAuthProvider, signInWithPopup, onAuthStateChanged, signOut, connectAuthEmulator,
} from 'https://www.gstatic.com/firebasejs/10.14.1/firebase-auth.js';

export let auth = null;
export let config = null;

export async function boot() {
  const res = await fetch('/v1/access/config', { cache: 'no-store' });
  config = await res.json();
  if (!config.enabled) throw new Error('הכניסה עוד לא פתוחה.');
  const app = initializeApp(config.firebase);
  auth = getAuth(app);
  auth.languageCode = 'he';
  if (config.emulator && config.emulator.auth) connectAuthEmulator(auth, 'http://' + config.emulator.auth, { disableWarnings: true });
  return config;
}

/** המשתמש המחובר (או null) — אחרי ש-Firebase סיים לבדוק את מה שנשמר בדפדפן */
export function currentUser() {
  return new Promise(resolve => {
    const stop = onAuthStateChanged(auth, u => { stop(); resolve(u); });
  });
}

export async function googleSignIn() {
  const p = new GoogleAuthProvider();
  p.setCustomParameters({ prompt: 'select_account' });
  const r = await signInWithPopup(auth, p);
  return r.user;
}

export async function logoutWeb() {
  await signOut(auth);
}

export class ApiError extends Error {
  constructor(status, code, message) { super(message); this.status = status; this.code = code; }
}

export async function api(method, path, body) {
  const headers = {};
  const u = auth && auth.currentUser;
  if (u) headers.authorization = 'Bearer ' + await u.getIdToken();
  if (body !== undefined) headers['content-type'] = 'application/json';
  let res;
  try {
    res = await fetch(path, { method, headers, body: body === undefined ? undefined : JSON.stringify(body), cache: 'no-store' });
  } catch (e) {
    throw new ApiError(0, 'network', 'אין חיבור לשרת. בודקים את האינטרנט ומנסים שוב.');
  }
  const j = await res.json().catch(() => ({}));
  if (!res.ok || j.ok === false) throw new ApiError(res.status, j.code || 'error', j.error || 'משהו השתבש. נסו שוב.');
  return j;
}

/** h('div', {class: 'x', onclick: fn}, 'טקסט', child...) — טקסט תמיד כטקסט, אף פעם לא כ-HTML */
export function h(tag, attrs, ...kids) {
  const el = document.createElement(tag);
  for (const [k, v] of Object.entries(attrs || {})) {
    if (v === undefined || v === null || v === false) continue;
    if (k.startsWith('on') && typeof v === 'function') el.addEventListener(k.slice(2), v);
    else if (k === 'class') el.className = v;
    else if (k === 'style' && typeof v === 'object') Object.assign(el.style, v);
    else if (k === 'html') throw new Error('no html');
    else el.setAttribute(k, v === true ? '' : String(v));
  }
  for (const kid of kids.flat(Infinity)) {
    if (kid === undefined || kid === null || kid === false) continue;
    el.append(kid instanceof Node ? kid : document.createTextNode(String(kid)));
  }
  return el;
}

const SVG_NS = 'http://www.w3.org/2000/svg';
/** אייקון קו (24×24) מתוך רשימת path-ים */
export function icon(paths, size = 18, color = 'currentColor', width = 2) {
  const s = document.createElementNS(SVG_NS, 'svg');
  s.setAttribute('width', size); s.setAttribute('height', size); s.setAttribute('viewBox', '0 0 24 24');
  s.setAttribute('fill', 'none'); s.setAttribute('stroke', color); s.setAttribute('stroke-width', width);
  s.setAttribute('stroke-linecap', 'round'); s.setAttribute('stroke-linejoin', 'round'); s.setAttribute('aria-hidden', 'true');
  for (const d of paths) {
    const tag = d.startsWith('c:') ? 'circle' : d.startsWith('r:') ? 'rect' : 'path';
    const e = document.createElementNS(SVG_NS, tag);
    if (tag === 'circle') { const [cx, cy, r] = d.slice(2).split(','); e.setAttribute('cx', cx); e.setAttribute('cy', cy); e.setAttribute('r', r); }
    else if (tag === 'rect') { const [x, y, w, hh, rx] = d.slice(2).split(','); e.setAttribute('x', x); e.setAttribute('y', y); e.setAttribute('width', w); e.setAttribute('height', hh); e.setAttribute('rx', rx || 0); }
    else e.setAttribute('d', d);
    s.append(e);
  }
  return s;
}
export const ICONS = {
  book: ['M4 19V5a2 2 0 0 1 2-2h12v18H6a2 2 0 0 1-2-2z', 'M8 7h6', 'M8 11h4'],
  shield: ['M12 3l8 4v5c0 4.5-3.4 8.3-8 9-4.6-.7-8-4.5-8-9V7z'],
  grid: ['r:3,3,7,9,1.5', 'r:14,3,7,5,1.5', 'r:14,12,7,9,1.5', 'r:3,16,7,5,1.5'],
  msg: ['M4 4h16v12H8l-4 4z'],
  user: ['c:12,8,4', 'M4 21c1.5-4 4.5-6 8-6s6.5 2 8 6'],
  cap: ['M2 9l10-5 10 5-10 5z', 'M6 11v5c3 2 9 2 12 0v-5'],
  list: ['M8 6h13', 'M8 12h13', 'M8 18h13', 'M3 6h.01', 'M3 12h.01', 'M3 18h.01'],
  gear: ['c:12,12,3', 'M12 2v3', 'M12 19v3', 'M2 12h3', 'M19 12h3', 'M4.9 4.9l2.1 2.1', 'M17 17l2.1 2.1', 'M4.9 19.1L7 17', 'M17 7l2.1-2.1'],
  search: ['c:11,11,7', 'M20 20l-3.5-3.5'],
  plus: ['M12 5v14', 'M5 12h14'],
  minus: ['M5 12h14'],
  x: ['M6 6l12 12', 'M18 6L6 18'],
  check: ['M5 12.5l4.5 4.5L19 7.5'],
  lock: ['r:5,11,14,10,2', 'M8 11V8a4 4 0 0 1 8 0v3'],
  info: ['c:12,12,9', 'M12 8h.01', 'M11 12h1v5h1'],
  monitor: ['r:3,4,18,12,2', 'M8 20h8', 'M12 16v4'],
  clock: ['c:12,12,9', 'M12 7v5l3.5 2'],
};

export function googleLogo() {
  const s = document.createElementNS(SVG_NS, 'svg');
  s.setAttribute('width', 20); s.setAttribute('height', 20); s.setAttribute('viewBox', '0 0 48 48'); s.setAttribute('aria-hidden', 'true');
  for (const [fill, d] of [
    ['#FFC107', 'M43.6 20.5H42V20H24v8h11.3C33.7 32.7 29.2 36 24 36c-6.6 0-12-5.4-12-12s5.4-12 12-12c3.1 0 5.8 1.2 7.9 3.1l5.7-5.7C34 6.1 29.3 4 24 4 12.9 4 4 12.9 4 24s8.9 20 20 20 20-8.9 20-20c0-1.3-.1-2.4-.4-3.5z'],
    ['#FF3D00', 'M6.3 14.7l6.6 4.8C14.7 15.1 19 12 24 12c3.1 0 5.8 1.2 7.9 3.1l5.7-5.7C34 6.1 29.3 4 24 4 16.3 4 9.7 8.3 6.3 14.7z'],
    ['#4CAF50', 'M24 44c5.2 0 9.9-2 13.4-5.2l-6.2-5.2C29.2 35.1 26.7 36 24 36c-5.2 0-9.6-3.3-11.3-8l-6.5 5C9.5 39.6 16.2 44 24 44z'],
    ['#1976D2', 'M43.6 20.5H42V20H24v8h11.3c-.8 2.2-2.2 4.2-4.1 5.6l6.2 5.2C37 39.2 44 34 44 24c0-1.3-.1-2.4-.4-3.5z'],
  ]) { const p = document.createElementNS(SVG_NS, 'path'); p.setAttribute('fill', fill); p.setAttribute('d', d); s.append(p); }
  return s;
}

export function avatar(user, dark) {
  const a = h('div', { class: 'avatar', style: dark ? { background: '#17213A' } : null });
  const letter = String((user && (user.displayName || user.email)) || '?').trim().charAt(0).toUpperCase();
  if (user && user.photoURL) {
    const img = h('img', { src: user.photoURL, alt: '', referrerpolicy: 'no-referrer' });
    img.onerror = () => { img.remove(); a.textContent = letter; };
    a.append(img);
  } else a.textContent = letter;
  return a;
}

const DAY = 86400000;
export function fmtDate(iso) {
  if (!iso) return '—';
  const d = new Date(/^\d{4}-\d{2}-\d{2}$/.test(iso) ? iso + 'T12:00:00' : iso);
  return isNaN(d) ? '—' : `${d.getDate()}.${d.getMonth() + 1}.${d.getFullYear()}`;
}
export function fmtTime(iso) {
  const d = new Date(iso);
  return isNaN(d) ? '' : d.toLocaleTimeString('he-IL', { hour: '2-digit', minute: '2-digit' });
}
/** "היום" / "אתמול" / "לפני 3 ימים" / תאריך */
export function ago(iso) {
  if (!iso) return '—';
  const d = new Date(iso);
  if (isNaN(d)) return '—';
  const start = new Date(); start.setHours(0, 0, 0, 0);
  const days = Math.floor((start - d) / DAY) + 1;
  if (d >= start) {
    const min = Math.floor((Date.now() - d) / 60000);
    if (min < 1) return 'עכשיו';
    if (min < 60) return `לפני ${min} דקות`;
    return 'היום, ' + fmtTime(iso);
  }
  if (days === 1) return 'אתמול, ' + fmtTime(iso);
  if (days < 30) return `לפני ${days} ימים`;
  return fmtDate(iso);
}
export const daysSince = iso => (iso ? Math.floor((Date.now() - new Date(iso)) / DAY) : 9999);

let toastTimer = null;
export function toast(msg, err) {
  let t = document.querySelector('.toast');
  if (!t) { t = h('div', { class: 'toast', role: 'status', 'aria-live': 'polite' }); document.body.append(t); }
  t.className = 'toast' + (err ? ' err' : '');
  t.textContent = msg;
  t.hidden = false;
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => { t.hidden = true; }, err ? 6000 : 3200);
}

/** חלון אישור — מחזיר true / false */
export function ask({ title, text, ok = 'אישור', danger = false, cancel = 'ביטול' }) {
  return new Promise(resolve => {
    const d = h('dialog', { class: 'modal', 'aria-labelledby': 'ask-t' });
    const done = v => { d.close(); d.remove(); resolve(v); };
    d.append(h('div', { class: 'in' },
      h('h2', { id: 'ask-t' }, title),
      text ? h('p', { class: 'muted', style: { lineHeight: '1.55' } }, text) : null,
      h('div', { class: 'row', style: { justifyContent: 'flex-start', marginTop: '6px' } },
        h('button', { type: 'button', class: 'btn ' + (danger ? 'danger' : 'primary'), onclick: () => done(true) }, ok),
        h('button', { type: 'button', class: 'btn', onclick: () => done(false) }, cancel))));
    d.addEventListener('cancel', e => { e.preventDefault(); done(false); });
    document.body.append(d);
    d.showModal();
  });
}

/** כפתור שמראה "רגע..." בזמן הפעולה, ומציג שגיאה בהודעה */
export async function busy(btn, fn) {
  const label = btn.textContent;
  btn.disabled = true;
  btn.setAttribute('aria-busy', 'true');
  try { return await fn(); } catch (e) { toast(e.message || String(e), true); return undefined; } finally {
    btn.disabled = false; btn.removeAttribute('aria-busy'); if (btn.textContent === 'רגע…') btn.textContent = label;
  }
}

export function topBar(user, { badge, onLogout, dark } = {}) {
  return h('header', { class: 'top' },
    h('div', { class: 'brand' }, h('div', { class: 'logo' + (dark ? ' dark' : '') }, icon(ICONS.book, 20, '#fff')), 'מאגר תרגילים', badge || null),
    user ? h('div', { class: 'who' },
      h('div', { class: 'meta' }, h('span', { class: 'ltr' }, user.email), onLogout ? h('a', { href: '#', onclick: e => { e.preventDefault(); onLogout(); } }, 'התנתקות') : null),
      avatar(user)) : h('span'));
}
