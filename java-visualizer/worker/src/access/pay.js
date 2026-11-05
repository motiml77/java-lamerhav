// שירות התשלום — שכבה אחת שכל ספק מתחבר אליה. הספק נבחר ב-PAY_PROVIDER (עוד לא נבחר — ראו README, "תשלום").
//
// ספק = { id, create(env, payment, origin) → {url, ref}, verify(env, request, store) → {id, status, amount, ref} | null,
//         routes?(request, env, url, helpers) → Response | null, ack?() → גוף התשובה לספק }
// verify חייב לאמת מול הספק (שאילתה חוזרת מהשרת / חתימה) — לא סומכים על ההודעה לבדה.
//
// demo — רק לבדיקות במחשב (עובד רק מול אמולטור של Firestore): דף "תשלום" מדומה עם כפתור.

import { firestoreEmulator } from './google.js';

export const PAY_PROVIDERS = [];   // הספקים האמיתיים יתווספו כאן אחרי הבחירה

const page = (title, body) => new Response(`<!doctype html><html lang="he" dir="rtl"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>${title}</title>
<style>body{margin:0;font-family:system-ui,sans-serif;background:#F4F6FB;color:#17213A;display:flex;min-height:100vh;align-items:center;justify-content:center}
.c{background:#fff;border:1px solid #E1E6F0;border-radius:18px;padding:32px 36px;max-width:420px;text-align:center;display:flex;flex-direction:column;gap:14px}
button{height:48px;border:0;border-radius:12px;background:#2F6FED;color:#fff;font:700 16px system-ui;cursor:pointer}</style></head><body><div class="c">${body}</div></body></html>`,
  { headers: { 'content-type': 'text/html; charset=utf-8', 'cache-control': 'no-store' } });

const DEMO = {
  id: 'demo',
  async create(env, pay, origin) {
    return { url: origin + '/pay/demo?id=' + encodeURIComponent(pay.id), ref: 'demo-' + pay.id };
  },
  async verify(env, request) {
    const b = await request.json().catch(() => ({}));
    return b && b.id ? { id: String(b.id), status: b.status || 'paid', amount: b.amount, ref: 'demo-' + b.id } : null;
  },
  async routes(request, env, url, h) {
    if (url.pathname !== '/pay/demo' || request.method !== 'GET') return null;
    const id = String(url.searchParams.get('id') || '').replace(/[^A-Za-z0-9_-]/g, '');
    const p = id ? await h.db().get('payments/' + id) : null;
    if (!p) return page('תשלום', '<h1>התשלום לא נמצא</h1>');
    return page('תשלום לבדיקה', `<h1 style="margin:0">תשלום לבדיקה</h1><p>${p.amount} ₪ · ${p.email}</p>
<button id="pay" type="button">תשלום (בדיקה)</button>
<script>document.getElementById('pay').onclick=async()=>{await fetch('/v1/pay/notify/demo',{method:'POST',headers:{'content-type':'application/json'},body:JSON.stringify({id:${JSON.stringify(id)},status:'paid',amount:${+p.amount}})});location.href='/welcome?r=paid';};</script>`);
  },
};

export function payProvider(env) {
  const id = String(env.PAY_PROVIDER || '').toLowerCase().trim();
  if (id === 'demo') return firestoreEmulator(env) ? DEMO : null;
  return null;
}
