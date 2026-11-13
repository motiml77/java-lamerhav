// הדפים בדפדפן: /signin (כניסה מתוך התוכנה), /welcome (אחרי הכניסה / תשלום), /account (החשבון שלי: מורה / תלמיד),
// /admin (מסוף ההנהלה), ו-/static/* (העיצוב והקוד המשותף). התוכן: public/ → assets.gen.js

import { ASSETS } from './assets.gen.js';

const TYPES = { html: 'text/html; charset=utf-8', js: 'text/javascript; charset=utf-8', css: 'text/css; charset=utf-8', svg: 'image/svg+xml' };
const PAGES = { '/signin': '/signin.html', '/welcome': '/welcome.html', '/account': '/account.html', '/admin': '/admin.html' };

// כותרות האבטחה של הדפים. ה-CSP כאן מגביל רק מה שלא נוגע לכניסה עם Google (מסגור, <base>, תוספים): הגבלה של מקורות
// הסקריפטים / החלונות הייתה עלולה לשבור את החלון של Google, שאת כל הכתובות שלו אי אפשר לבדוק מראש.
const SECURITY = {
  'x-content-type-options': 'nosniff',
  'referrer-policy': 'no-referrer',
  'x-frame-options': 'DENY',
  'content-security-policy': "frame-ancestors 'none'; base-uri 'none'; object-src 'none'",
  'strict-transport-security': 'max-age=31536000',
  'permissions-policy': 'camera=(), microphone=(), geolocation=(), payment=()',
};

export async function servePage(path, env, url) {
  const file = PAGES[path] || (path.startsWith('/static/') ? path.slice(7) : '');
  const text = file && ASSETS[file];
  if (text === undefined || !file) return null;
  const ext = file.split('.').pop();
  // הדפים עצמם — בלי מטמון (כדי שעדכון יגיע מיד); הקבצים המשותפים — לשעה
  const cache = ext === 'html' ? 'no-store' : 'public, max-age=3600';
  return new Response(text, { headers: Object.assign({ 'content-type': TYPES[ext] || 'text/plain; charset=utf-8', 'cache-control': cache }, SECURITY) });
}
