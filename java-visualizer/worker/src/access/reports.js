// דיווחים על בעיות מהתוספים: "דיווח על הבעיה" (התלמיד / המורה לחץ) ודיווח אוטומטי (התוסף זיהה בעיה והריץ בדיקה מעמיקה ברקע).
// התוסף שומר כל דיווח במחשב (תיבת יוצא) ושולח כשיש אינטרנט — לכן מגיעים גם דיווחים מלפני כמה ימים, ו-id (של התוסף) מונע כפילות.
// במסוף ההנהלה ("דיווחים"): מי שלח — מורה / עמדת מחשב (ושל איזה מורה) / תלמיד (ולאיזה מורה הוא משויך) — עם הפרטים של המורה
// (שם, בית ספר, טלפון, מייל), התוכנה והגרסה, הבעיה, והבדיקה המעמיקה (בנפרד — reportDiag/{id}, נקראת רק כשפותחים).
//
//   reports/{id}      at, occurredAt, kind, auto, title, message, note, app, appVersion, dsvizVersion, os, context{…},
//                     who{kind, email, name, station, stationName}, teacher{email, name, school, phone} | null, status (new / done),
//                     adminNote, doneAt, doneBy, diagSize, sig (טביעה של הבעיה — כדי לראות כמה דיווחים דומים)
//   reportDiag/{id}   diag (טקסט, עד 300KB)

import { Fail } from '../http.js';
import { db } from './firestore.js';
import { sha256Hex } from './crypto.js';
import { readJson, log, resolveToken, settings } from './index.js';
import { alert } from './alerts.js';

const nowIso = () => new Date().toISOString();
const clip = (s, n) => String(s == null ? '' : s).replace(/[\u0000-\u001f\u007f]/g, ' ').trim().slice(0, n);
/** טקסט רב-שורות (הודעת שגיאה, בדיקה מעמיקה): שומרים ירידות שורה וטאבים */
const text = (s, n) => String(s == null ? '' : s).replace(/[\u0000-\u0008\u000b\u000c\u000e-\u001f\u007f]/g, '').slice(0, n);
const KINDS = ['visual-run', 'crash', 'diagnose', 'manual'];
const MAX_DIAG = 300 * 1024;
const REPORTS_PER_HOUR = 20, REPORTS_PER_IP_HOUR = 400;

/** דיווחים: עד 20 בשעה לכל מחשב (המזהה של המחשב, X-DSViz-Machine), ותקרה גבוהה לכתובת IP — מעבדה שלמה יוצאת מאותה כתובת */
export async function reportLimit(env, request) {
  const kv = env.USAGE && typeof env.USAGE.get === 'function' ? env.USAGE : env.STATS && typeof env.STATS.get === 'function' ? env.STATS : null;
  if (!kv) return;
  const ip = request.headers.get('cf-connecting-ip') || 'local';
  const machine = String(request.headers.get('x-dsviz-machine') || '');
  const hour = nowIso().slice(0, 13);
  const keys = [['report:' + hour + ':' + (await sha256Hex('report|' + ip)).slice(0, 20), machine ? REPORTS_PER_IP_HOUR : REPORTS_PER_HOUR]];
  if (/^[A-Za-z0-9_-]{8,128}$/.test(machine)) keys.push(['report:' + hour + ':m:' + (await sha256Hex('report-m|' + machine)).slice(0, 20), REPORTS_PER_HOUR]);
  const counts = await Promise.all(keys.map(([k]) => kv.get(k).then(v => +v || 0)));
  if (keys.some(([, lim], i) => counts[i] >= lim)) throw new Fail(429, 'rate', 'יותר מדי דיווחים מהמחשב הזה בשעה האחרונה. הדיווח יישלח שוב מאוחר יותר.');
  await Promise.all(keys.map(([k], i) => kv.put(k, String(counts[i] + 1), { expirationTtl: 7200 })));
}

/** מי שלח: לפי כרטיס הכניסה של המחשב (אם יש ובתוקף). בלי כרטיס — "לא מחובר" (גם זה מתקבל) */
async function sender(request, env) {
  const token = request.headers.get('x-dsviz-access') || '';
  const out = { who: { kind: 'none', email: '', name: '', station: '', stationName: '' }, teacher: null };
  if (!token) return out;
  let r;
  try { r = await resolveToken(env, token, request.headers.get('x-dsviz-machine') || ''); } catch (e) { return out; }
  if (!r || !r.kind) return out;
  const store = db(env);
  let teacherEmail = '';
  if (r.kind === 'station') {
    out.who = { kind: 'station', email: '', name: '', station: r.station.id, stationName: r.station.name || '' };
    teacherEmail = r.station.teacher;
  } else if (r.kind === 'teacher') {
    out.who = { kind: 'teacher', email: r.profile.email, name: r.profile.name || '', station: '', stationName: '' };
    teacherEmail = r.profile.email;
  } else {
    out.who = { kind: 'student', email: r.profile.email, name: r.profile.name || '', station: '', stationName: '' };
    teacherEmail = r.student && r.student.teacher ? r.student.teacher.email : '';
  }
  if (r.status !== 'ok') out.who.status = r.status;
  if (teacherEmail) {
    const t = await store.get('teachers/' + teacherEmail);
    if (t) out.teacher = { email: teacherEmail, name: t.name || '', school: t.school || '', phone: t.phone || '' };
  }
  return out;
}

function cleanContext(c) {
  const out = {};
  if (!c || typeof c !== 'object') return out;
  for (const [k, v] of Object.entries(c).slice(0, 20)) {
    const key = clip(k, 40).replace(/[^A-Za-z0-9_.-]/g, '');
    if (key) out[key] = clip(v, 300);
  }
  return out;
}

/** POST /v1/report — מהתוסף (תיבת היוצא). אותו id פעמיים (שליחה חוזרת אחרי ניתוק) — נשמר פעם אחת */
async function report(request, env) {
  const b = await readJson(request, 2 * MAX_DIAG);
  const id = String(b.id || '');
  if (!/^[A-Za-z0-9_-]{16,64}$/.test(id)) throw new Fail(400, 'format', 'מזהה דיווח לא תקין');
  const store = db(env);
  if (await store.get('reports/' + id)) return { ok: true, id, already: true };
  await reportLimit(env, request);
  const kind = KINDS.includes(b.kind) ? b.kind : 'manual';
  const title = clip(b.title, 200) || clip(b.message, 200) || 'דיווח';
  const app = clip(b.app, 60);
  const diag = text(b.diag, MAX_DIAG);
  const occurred = Date.parse(b.occurredAt);
  const s = await sender(request, env);
  const doc = {
    at: nowIso(), occurredAt: isNaN(occurred) ? nowIso() : new Date(Math.min(occurred, Date.now())).toISOString(),
    kind, auto: b.auto === true, title, message: text(b.message, 8000), note: clip(b.note, 1000),
    app, appVersion: clip(b.appVersion, 60), dsvizVersion: clip(b.dsvizVersion, 40), os: clip(b.os, 120), context: cleanContext(b.context),
    who: s.who, teacher: s.teacher, status: 'new', adminNote: '', diagSize: diag.length,
    sig: (await sha256Hex(kind + '|' + title.replace(/\d+/g, '#') + '|' + app.replace(/[\d.]+/g, ''))).slice(0, 16),
  };
  const created = await store.create('reports/' + id, doc);
  if (!created) return { ok: true, id, already: true };
  if (diag) await store.set('reportDiag/' + id, { diag });
  const from = s.who.kind === 'station' ? `עמדה "${s.who.stationName}"` : s.who.kind === 'none' ? 'מחשב לא מחובר' : (s.who.name || s.who.email);
  await alert(env, store, { kind: 'report.new', email: s.teacher ? s.teacher.email : s.who.email, name: from, detail: `${title}${app ? ' · ' + app : ''}${doc.auto ? ' · אוטומטי' : ''}`, ref: id },
    { email: (await settings(env)).alertEmail !== false });   // פעמון במסוף תמיד; מייל — לפי ההגדרה (ועד 60 ביום)
  return { ok: true, id };
}

// ------------------------------------------------------------------ מסוף ההנהלה

function row(x) {
  return {
    id: x._id, at: x.at, occurredAt: x.occurredAt, kind: x.kind, auto: !!x.auto, title: x.title, app: x.app, appVersion: x.appVersion, dsvizVersion: x.dsvizVersion,
    who: x.who || {}, teacher: x.teacher || null, status: x.status || 'new', sig: x.sig || '', hasDiag: (x.diagSize || 0) > 0, note: x.note || '',
  };
}

async function adminReports(env, url) {
  const store = db(env);
  const status = url.searchParams.get('status') === 'done' ? 'done' : 'new';
  const list = await store.query('reports', { where: [['status', '==', status]] });
  list.sort((a, b) => String(b.at).localeCompare(String(a.at)));
  const rows = list.slice(0, 300).map(row);
  const same = new Map();
  for (const r of rows) same.set(r.sig, (same.get(r.sig) || 0) + 1);
  rows.forEach(r => { r.similar = same.get(r.sig) - 1; });
  const open = status === 'new' ? rows.length : (await store.query('reports', { where: [['status', '==', 'new']] })).length;
  return { ok: true, status, reports: rows, open };
}

async function adminReport(env, url) {
  const store = db(env);
  const id = clip(url.searchParams.get('id'), 64);
  const [x, d] = await store.getMany(['reports/' + id, 'reportDiag/' + id]);
  if (!x) throw new Fail(404, 'not_found', 'הדיווח לא נמצא.');
  let others = [];
  if (x.sig) {
    others = (await store.query('reports', { where: [['sig', '==', x.sig]] })).filter(o => o._id !== id)
      .sort((a, b) => String(b.at).localeCompare(String(a.at))).slice(0, 20).map(row);
  }
  return { ok: true, report: Object.assign(row(x), { message: x.message || '', os: x.os || '', context: x.context || {}, adminNote: x.adminNote || '', doneAt: x.doneAt || '', doneBy: x.doneBy || '' }),
    diag: d ? d.diag || '' : '', similar: others };
}

async function adminSaveReport(env, u, b) {
  const store = db(env);
  const id = clip(b.id, 64);
  const x = await store.get('reports/' + id);
  if (!x) throw new Fail(404, 'not_found', 'הדיווח לא נמצא.');
  const upd = {};
  if (b.status === 'done' || b.status === 'new') Object.assign(upd, { status: b.status, doneAt: b.status === 'done' ? nowIso() : '', doneBy: b.status === 'done' ? u.email : '' });
  if (b.adminNote !== undefined) upd.adminNote = clip(b.adminNote, 1000);
  if (Object.keys(upd).length) await store.update('reports/' + id, upd);
  if (upd.status) await log(store, u.email, upd.status === 'done' ? 'report.done' : 'report.reopen', x.title, x.who && (x.who.email || x.who.stationName) || '');
  return { ok: true };
}

export const REPORT_ROUTES = { 'POST /v1/report': report };
export const REPORT_ADMIN_ROUTES = {
  'GET /v1/admin/reports': (env, u, url) => adminReports(env, url),
  'GET /v1/admin/report': (env, u, url) => adminReport(env, url),
  'POST /v1/admin/report': (env, u, url, b) => adminSaveReport(env, u, b),
};
