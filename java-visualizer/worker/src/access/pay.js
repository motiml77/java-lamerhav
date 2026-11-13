// שירות התשלום — שכבה אחת שכל ספק מתחבר אליה. הספק נבחר ב-PAY_PROVIDER: "paypal" (למטה), או "demo" לבדיקות במחשב.
//
// ספק = { id, create(env, payment, origin) → {url, ref, order?}, verify(env, request, store) → {id, status, amount, ref, payer: {name, email, phone}} | null,
//         routes?(request, env, url, helpers) → Response | null, ack?() → גוף התשובה לספק }
// verify חייב לאמת מול הספק (שאילתה חוזרת מהשרת / חתימה) — לא סומכים על ההודעה לבדה.
//
// demo — רק לבדיקות במחשב (עובד רק מול אמולטור של Firestore): דף "תשלום" מדומה עם כפתור.

import { firestoreEmulator } from './google.js';

export const PAY_PROVIDERS = [];   // הספקים האמיתיים (מוצגים בהגדרות של מסוף ההנהלה)

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
    return b && b.id ? { id: String(b.id), status: b.status || 'paid', amount: b.amount, ref: 'demo-' + b.id, payer: b.payer || null } : null;
  },
  async routes(request, env, url, h) {
    if (url.pathname !== '/pay/demo' || request.method !== 'GET') return null;
    const id = String(url.searchParams.get('id') || '').replace(/[^A-Za-z0-9_-]/g, '');
    const p = id ? await h.db().get('payments/' + id) : null;
    if (!p) return page('תשלום', '<h1>התשלום לא נמצא</h1>');
    const back = p.kind === 'seats' ? '/account?r=paid' : '/welcome?r=paid';
    return page('תשלום לבדיקה', `<h1 style="margin:0">תשלום לבדיקה</h1><p>${p.title || ''} · ${p.amount} ₪</p>
<input id="nm" placeholder="שם המשלם" value="${p.name || ''}" style="height:44px;border:1.5px solid #C9D2E3;border-radius:10px;padding:0 12px;font:15px system-ui">
<input id="ph" placeholder="טלפון" value="050-1234567" dir="ltr" style="height:44px;border:1.5px solid #C9D2E3;border-radius:10px;padding:0 12px;font:15px system-ui">
<button id="pay" type="button">תשלום (בדיקה)</button>
<script>document.getElementById('pay').onclick=async()=>{await fetch('/v1/pay/notify/demo',{method:'POST',headers:{'content-type':'application/json'},body:JSON.stringify({id:${JSON.stringify(id)},status:'paid',amount:${+p.amount},payer:{name:document.getElementById('nm').value,phone:document.getElementById('ph').value,email:${JSON.stringify(p.email)}}})});location.href=${JSON.stringify(back)};};</script>`);
  },
};

// ------------------------------------------------------------------ PayPal (Orders v2)
//
// הגדרות ב-Worker: PAY_PROVIDER = "paypal", PAYPAL_CLIENT_ID (לא סוד), PAYPAL_SECRET (סוד), PAYPAL_ENV = "live" (ברירת מחדל: sandbox),
//                   PAYPAL_WEBHOOK_ID (מזהה ה-webhook בחשבון PayPal — בלעדיו הודעות מ-PayPal לא מתקבלות).
//
// הזרימה, והעיקרון: הדפדפן לא מחליט כלום. הסכום נקבע בשרת, ההזמנה נוצרת בשרת, והכסף נגבה (capture) בשרת.
//   1. /v1/pay/start (משתמש מחובר עם Google) → השרת יוצר הזמנה ב-PayPal עם הסכום מההגדרות ו-custom_id = מזהה התשלום שלנו,
//      ושומר אצלנו את מזהה ההזמנה. הדפדפן מקבל רק קישור לדף האישור של PayPal.
//   2. המשלם מאשר ב-PayPal וחוזר ל-/pay/paypal/return?p=<התשלום>&token=<ההזמנה>. השרת גובה את ההזמנה — רק אם זו ההזמנה
//      שהוא עצמו יצר לתשלום הזה — ובודק בתשובה של PayPal: נגבה (COMPLETED), אותו סכום, אותו מטבע, אותו custom_id. רק אז נפתח.
//   3. גיבוי: webhook מ-PayPal (המשלם סגר את הדפדפן לפני החזרה, תשלום שאושר באיחור, החזר כספי). ההודעה מאומתת בחתימה מול
//      PayPal, וגם אז לא סומכים על התוכן שלה: שואלים את PayPal על ההזמנה / הגבייה, ופועלים רק לפי התשובה.
// הכול אידמפוטנטי: חזרה כפולה, הודעה כפולה או שניהם יחד — תשלום אחד (settlePayment + PayPal-Request-Id).

const PP_ID = /^[A-Z0-9]{8,40}$/;          // מזהה הזמנה / גבייה / החזר של PayPal
const OUR_ID = /^[A-Za-z0-9_-]{8,64}$/;    // מזהה התשלום שלנו
let ppToken = { key: '', token: '', until: 0 };

const ppBase = env => (String(env.PAYPAL_ENV || '').toLowerCase() === 'live' ? 'https://api-m.paypal.com' : 'https://api-m.sandbox.paypal.com');
const money = n => (Math.round(+n * 100) / 100).toFixed(2);

async function ppAccessToken(env) {
  const key = ppBase(env) + '|' + env.PAYPAL_CLIENT_ID;
  const now = Math.floor(Date.now() / 1000);
  if (ppToken.key === key && ppToken.until > now + 60) return ppToken.token;
  const res = await fetch(ppBase(env) + '/v1/oauth2/token', {
    method: 'POST',
    headers: { authorization: 'Basic ' + btoa(env.PAYPAL_CLIENT_ID + ':' + env.PAYPAL_SECRET), 'content-type': 'application/x-www-form-urlencoded', accept: 'application/json' },
    body: 'grant_type=client_credentials',
  });
  const j = await res.json().catch(() => ({}));
  if (!res.ok || !j.access_token) throw new Error('paypal token: ' + res.status);
  ppToken = { key, token: j.access_token, until: now + (+j.expires_in || 300) };
  return j.access_token;
}

/** קריאה ל-PayPal. body: אובייקט, או מחרוזת JSON מוכנה (לאימות חתימה: הגוף המקורי של ההודעה, בלי לפרק ולהרכיב מחדש) */
async function pp(env, method, path, body, requestId) {
  const headers = { authorization: 'Bearer ' + await ppAccessToken(env), accept: 'application/json' };
  if (body !== undefined) headers['content-type'] = 'application/json';
  if (requestId) headers['paypal-request-id'] = requestId;     // אותה בקשה פעמיים = אותה תוצאה (לא שתי הזמנות / שתי גביות)
  const res = await fetch(ppBase(env) + path, { method, headers, body: body === undefined ? undefined : typeof body === 'string' ? body : JSON.stringify(body) });
  return { status: res.status, json: await res.json().catch(() => ({})) };
}

const ppIssue = (r, issue) => Array.isArray(r.json && r.json.details) && r.json.details.some(d => d && d.issue === issue);

/** מה PayPal אומר על גבייה: התוצאה בפורמט של settlePayment, או null (עוד לא הוכרע / לא שלנו) */
function captureResult(pid, cap, order) {
  if (!cap || cap.custom_id !== pid || !cap.amount) return null;
  const payer = order && order.payer ? {
    name: [order.payer.name && order.payer.name.given_name, order.payer.name && order.payer.name.surname].filter(Boolean).join(' '),
    email: order.payer.email_address || '', phone: order.payer.phone && order.payer.phone.phone_number && order.payer.phone.phone_number.national_number || '',
  } : null;
  const base = { id: pid, amount: +cap.amount.value, currency: cap.amount.currency_code, ref: String(cap.id || ''), payer };
  if (cap.status === 'COMPLETED') return Object.assign(base, { status: 'paid' });
  if (cap.status === 'REFUNDED') return { id: pid, status: 'refunded', ref: base.ref };
  if (cap.status === 'DECLINED' || cap.status === 'FAILED') return { id: pid, status: 'failed', ref: base.ref };
  return null;   // PENDING / PARTIALLY_REFUNDED: לא משנים כלום (ההודעה הבאה מ-PayPal תכריע)
}

/**
 * גובה את ההזמנה של התשלום pid (או קורא אותה אם כבר נגבתה), ומחזיר את התוצאה ל-settlePayment. null: עוד לא אושרה / לא שלנו.
 * ההזמנה חייבת להיות זו שהשרת יצר לתשלום הזה (providerOrder) — כך אי אפשר "לסגור" תשלום עם הזמנה אחרת, זולה יותר.
 */
async function ppSettleOrder(env, store, pid, orderId) {
  if (!OUR_ID.test(pid) || !PP_ID.test(orderId)) return null;
  const p = await store.get('payments/' + pid);
  if (!p || p.provider !== 'paypal' || p.providerOrder !== orderId) return null;
  let r = await pp(env, 'POST', '/v2/checkout/orders/' + orderId + '/capture', {}, 'capture-' + pid);
  if (r.status === 422 && ppIssue(r, 'ORDER_ALREADY_CAPTURED')) r = await pp(env, 'GET', '/v2/checkout/orders/' + orderId);
  if (r.status !== 200 && r.status !== 201) return null;       // ORDER_NOT_APPROVED (עוד לא אישר / ביטל), או תקלה: נשאר "ממתין"
  const order = r.json;
  if (order.id !== orderId) return null;
  const unit = (order.purchase_units || [])[0];
  const cap = unit && unit.payments && (unit.payments.captures || [])[0];
  return captureResult(pid, cap, order);
}

const PAYPAL = {
  id: 'paypal',
  async create(env, pay, origin) {
    const r = await pp(env, 'POST', '/v2/checkout/orders', {
      intent: 'CAPTURE',
      purchase_units: [{ reference_id: pay.id, custom_id: pay.id, invoice_id: pay.id, description: String(pay.title || 'DSViz').slice(0, 120),
        amount: { currency_code: pay.currency || 'ILS', value: money(pay.amount) } }],
      payment_source: { paypal: { experience_context: {
        brand_name: 'DSViz', locale: 'he-IL', shipping_preference: 'NO_SHIPPING', user_action: 'PAY_NOW',
        return_url: origin + '/pay/paypal/return?p=' + encodeURIComponent(pay.id), cancel_url: origin + '/pay/paypal/cancel?p=' + encodeURIComponent(pay.id),
      } } },
    }, 'create-' + pay.id);
    const link = (r.json.links || []).find(l => l.rel === 'payer-action') || (r.json.links || []).find(l => l.rel === 'approve');
    // הקישור שהדפדפן יקבל חייב להיות של PayPal עצמו
    if ((r.status !== 200 && r.status !== 201) || !PP_ID.test(String(r.json.id || '')) || !link || !/^https:\/\/www\.(sandbox\.)?paypal\.com\//.test(String(link.href))) {
      throw new Error('paypal create: ' + r.status + ' ' + (r.json.name || ''));
    }
    return { url: link.href, ref: r.json.id, order: r.json.id };
  },
  /** webhook: חתימה מול PayPal, ואז שאילתה חוזרת על ההזמנה / הגבייה. מה שכתוב בהודעה עצמה משמש רק כדי לדעת על מה לשאול */
  async verify(env, request, store) {
    const raw = await request.text();
    if (!env.PAYPAL_WEBHOOK_ID || raw.length > 200000) throw new Error('paypal webhook: not configured');
    let ev;
    try { ev = JSON.parse(raw); } catch (e) { return null; }
    const hd = n => String(request.headers.get(n) || '');
    const v = await pp(env, 'POST', '/v1/notifications/verify-webhook-signature',
      '{"auth_algo":' + JSON.stringify(hd('paypal-auth-algo')) + ',"cert_url":' + JSON.stringify(hd('paypal-cert-url')) + ',"transmission_id":' + JSON.stringify(hd('paypal-transmission-id'))
      + ',"transmission_sig":' + JSON.stringify(hd('paypal-transmission-sig')) + ',"transmission_time":' + JSON.stringify(hd('paypal-transmission-time'))
      + ',"webhook_id":' + JSON.stringify(String(env.PAYPAL_WEBHOOK_ID)) + ',"webhook_event":' + raw + '}');
    if (v.status !== 200 || v.json.verification_status !== 'SUCCESS') throw new Error('paypal webhook: bad signature');
    const res = ev && ev.resource || {}, type = String(ev && ev.event_type || '');
    if (type === 'CHECKOUT.ORDER.APPROVED') {      // אישר ולא חזר לאתר: גובים עכשיו
      const pid = String(((res.purchase_units || [])[0] || {}).custom_id || '');
      return ppSettleOrder(env, store, pid, String(res.id || ''));
    }
    if (/^PAYMENT\.CAPTURE\./.test(type)) {
      // בהחזר כספי ה-resource הוא ההחזר, והגבייה בקישור "up"
      const up = (res.links || []).find(l => l && l.rel === 'up' && /\/v2\/payments\/captures\/[A-Z0-9]+$/.test(String(l.href)));
      const capId = up ? String(up.href).split('/').pop() : String(res.id || '');
      if (!PP_ID.test(capId)) return null;
      const c = await pp(env, 'GET', '/v2/payments/captures/' + capId);
      if (c.status !== 200) return null;
      const pid = String(c.json.custom_id || '');
      if (!OUR_ID.test(pid)) return null;
      const p = await store.get('payments/' + pid);
      const orderId = c.json.supplementary_data && c.json.supplementary_data.related_ids && c.json.supplementary_data.related_ids.order_id;
      if (!p || p.provider !== 'paypal' || (orderId && orderId !== p.providerOrder)) return null;
      return captureResult(pid, c.json, null);
    }
    return null;
  },
  async routes(request, env, url, h) {
    if (request.method !== 'GET' || (url.pathname !== '/pay/paypal/return' && url.pathname !== '/pay/paypal/cancel')) return null;
    const pid = String(url.searchParams.get('p') || ''), store = h.db();
    const p = OUR_ID.test(pid) ? await store.get('payments/' + pid) : null;
    const home = p && p.kind === 'seats' ? '/account' : '/welcome';
    const go = to => new Response(null, { status: 302, headers: { location: to, 'cache-control': 'no-store', 'referrer-policy': 'no-referrer' } });
    if (!p || p.provider !== 'paypal') return go('/welcome?r=error');
    if (url.pathname === '/pay/paypal/cancel') return go(home + (home === '/welcome' ? '?r=pay' : ''));
    const n = await ppSettleOrder(env, store, pid, String(url.searchParams.get('token') || '')).catch(() => null);
    if (n) await h.settlePayment(n);
    // גם כשעוד לא הוכרע (PayPal עוד בודק): הדף מחכה לאישור, והודעה מ-PayPal תשלים
    return go(home + '?r=paid');
  },
};

PAY_PROVIDERS.push('paypal');

export function payProvider(env) {
  const id = String(env.PAY_PROVIDER || '').toLowerCase().trim();
  if (id === 'demo') return firestoreEmulator(env) ? DEMO : null;
  if (id === 'paypal') return env.PAYPAL_CLIENT_ID && env.PAYPAL_SECRET ? PAYPAL : null;
  return null;
}

export const _payTest = { reset() { ppToken = { key: '', token: '', until: 0 }; } };
