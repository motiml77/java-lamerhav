// התראות למנהל: מורה או תלמיד שהצטרפו, בקשת הרשאת מורה, תשלום.
//   • במסוף ההנהלה: alerts/{id} עם seen = false → עיגול אדום על הפעמון, עד שהמנהל פותח אותן.
//   • במייל: דרך Resend (https://resend.com) — RESEND_API_KEY (סוד), ALERT_EMAIL (לאן; ברירת מחדל: המנהל הראשי),
//     ALERT_FROM (ממי; ברירת מחדל: onboarding@resend.dev, שמותר לשלוח ממנו רק לכתובת שבה נפתח החשבון ב-Resend).
//     בלי RESEND_API_KEY — רק ההתראה במסוף. אפשר לכבות את המייל בהגדרות של המסוף (alertEmail).
// המייל נשלח ברקע (ctx.waitUntil), כך שהכניסה / התשלום לא מחכים לו, ותקלה בו לא מפילה אותם. עד 60 מיילים ביום.

import { timeId } from './firestore.js';

/** הסוגים: הכותרת במסוף ובמייל, ולאן לוחצים במסוף */
export const ALERT_KINDS = {
  'student.new': { title: 'תלמיד חדש הצטרף', go: a => '#students=' + encodeURIComponent(a.email) },
  'teacher.first': { title: 'מורה נכנס בפעם הראשונה', go: a => '#teacher=' + encodeURIComponent(a.email) },
  'teacher.request': { title: 'בקשה חדשה להרשאת מורה', go: () => '#requests' },
  'payment.paid': { title: 'תלמיד שילם', go: a => '#students=' + encodeURIComponent(a.email) },
  'payment.seats': { title: 'מורה קנה חבילת עמדות', go: a => '#teacher=' + encodeURIComponent(a.email) },
};

export const WAIT = Symbol('waitUntil');
const MAIL_PER_DAY = 60;
const clip = (s, n) => String(s == null ? '' : s).replace(/[\u0000-\u001f\u007f]/g, ' ').trim().slice(0, n);
const esc = s => String(s).replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[c]);

/** a = { kind, email, name, detail }. לא זורק: התראה שנכשלה לא מפילה את הפעולה שגרמה לה */
export async function alert(env, store, a, opts) {
  try {
    const kind = ALERT_KINDS[a.kind] ? a.kind : '';
    if (!kind) return;
    const doc = { kind, email: clip(a.email, 120).toLowerCase(), name: clip(a.name, 80), detail: clip(a.detail, 200), at: new Date().toISOString(), seen: false };
    await store.set('alerts/' + timeId(), doc);
    if (opts && opts.email === false) return;
    const send = mail(env, doc).catch(e => console.error('alert mail', e && e.message));
    if (typeof env[WAIT] === 'function') env[WAIT](send); else await send;
  } catch (e) {
    console.error('alert', e && e.message);
  }
}

/** לאן המייל הולך ('' — לא מוגדר) */
export function alertTo(env) {
  const to = String(env.ALERT_EMAIL || String(env.ADMIN_EMAILS || '').split(',')[0] || '').trim().toLowerCase();
  return /^[^\s@/]{1,64}@[^\s@/]{1,190}\.[^\s@/.]{2,}$/.test(to) ? to : '';
}
export const mailReady = env => !!(env.RESEND_API_KEY && alertTo(env));

async function mail(env, a) {
  if (!mailReady(env)) return;
  // עד 60 מיילים ביום (כיתה שלמה שנכנסת בבת אחת לא מציפה את התיבה; ההתראות עדיין במסוף)
  const kv = env.USAGE && typeof env.USAGE.get === 'function' ? env.USAGE : env.STATS && typeof env.STATS.get === 'function' ? env.STATS : null;
  if (kv) {
    const key = 'alertmail:' + a.at.slice(0, 10);
    const n = +(await kv.get(key)) || 0;
    if (n >= MAIL_PER_DAY) return;
    await kv.put(key, String(n + 1), { expirationTtl: 3 * 86400 });
  }
  const k = ALERT_KINDS[a.kind];
  const who = a.name ? a.name + ' (' + a.email + ')' : a.email;
  const base = String(env.PUBLIC_URL || 'https://dsviz-exercise.motiml77.workers.dev').replace(/\/+$/, '');
  const link = base + '/admin' + k.go(a);
  const lines = [k.title + ': ' + who, a.detail || '', '', 'במסוף ההנהלה: ' + link].filter((x, i) => x || i === 2);
  const html = '<div dir="rtl" style="font-family:Arial,sans-serif;font-size:15px;line-height:1.6;color:#17213A">'
    + '<p style="margin:0 0 6px"><b>' + esc(k.title) + '</b></p>'
    + '<p style="margin:0 0 6px">' + esc(a.name || '') + (a.name ? ' · ' : '') + '<span dir="ltr">' + esc(a.email) + '</span></p>'
    + (a.detail ? '<p style="margin:0 0 6px;color:#5A6478">' + esc(a.detail) + '</p>' : '')
    + '<p style="margin:14px 0 0"><a href="' + esc(link) + '" style="background:#2F6FED;color:#fff;padding:9px 16px;border-radius:9px;text-decoration:none;font-weight:700">פתיחה במסוף ההנהלה</a></p></div>';
  const res = await fetch('https://api.resend.com/emails', {
    method: 'POST',
    headers: { authorization: 'Bearer ' + env.RESEND_API_KEY, 'content-type': 'application/json' },
    body: JSON.stringify({ from: env.ALERT_FROM || 'DSViz <onboarding@resend.dev>', to: [alertTo(env)], subject: 'DSViz: ' + k.title + ' — ' + (a.name || a.email), text: lines.join('\n'), html }),
  });
  if (!res.ok) throw new Error('resend ' + res.status);
}

/** במסוף: ההתראות האחרונות ומספר אלה שלא נראו */
export async function listAlerts(store, limit) {
  const [recent, unseen] = await Promise.all([
    store.query('alerts', { orderBy: ['at', 'desc'], limit: Math.min(Math.max(+limit || 30, 1), 100) }),
    store.query('alerts', { where: [['seen', '==', false]], limit: 200 }),
  ]);
  return {
    ok: true, unseen: unseen.length,
    alerts: recent.map(a => ({ id: a._id, kind: a.kind, title: (ALERT_KINDS[a.kind] || {}).title || a.kind, email: a.email, name: a.name, detail: a.detail, at: a.at, seen: !!a.seen,
      go: ALERT_KINDS[a.kind] ? ALERT_KINDS[a.kind].go(a) : '#overview' })),
  };
}

/** סימון כנקראו: ids, או all */
export async function markSeen(store, b) {
  const ids = b && b.all ? (await store.query('alerts', { where: [['seen', '==', false]], limit: 400 })).map(a => a._id)
    : (Array.isArray(b && b.ids) ? b.ids : []).map(x => String(x)).filter(x => /^[a-z0-9]{8,40}$/.test(x)).slice(0, 100);
  for (let i = 0; i < ids.length; i += 50) {
    const part = ids.slice(i, i + 50);
    await store.commit(w => { for (const id of part) w.update('alerts/' + id, { seen: true }); });
  }
  return { ok: true, marked: ids.length };
}
