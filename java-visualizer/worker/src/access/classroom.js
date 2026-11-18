// עמדת הניהול של המורה: כיתות, תלמידים שהצטרפו בקוד המורה, והגשות (מתלמיד מחובר ומעמדת מחשב).
//
// מי מחליט: רק השרת (Firestore סגור לכל מחשב). המורה נקבע תמיד בשרת — לפי הכרטיס של המגיש, לא לפי מה שהמחשב שולח.
//   • תלמיד — אחרי הכניסה עם Google בוחר: "תלמיד עצמאי" (mode=independent), או קוד מורה (mode=teacher, ‏members/{מורה~תלמיד}).
//             בינתיים בחינם (settings.studentsFree); המעבר לתשלום — מתג אחד בהנהלה, בלי שינוי בתוספים.
//   • הגשה   — "הגשה למורה" (וגם "סיימתי") מתלמיד משויך או מעמדת מחשב. בעמדה — "שם המגיש" חובה.
//             הגשה של תלמיד נשמרת עד שהמורה מוחק אותו / את הכיתה. הגשה מעמדה — עד שהמורה מוחק, ולכל המאוחר אחרי
//             settings.stationSubmitDays ימים (expiresAt: מדיניות TTL ב-Firestore, ובנוסף נמחקת כשקוראים את הרשימה).
//   • כיתות  — של המורה בלבד; שינוי שם בכל זמן; מחיקה מוחקת את התלמידים שבה ואת כל ההגשות שלהם (בשלבים, עד 400 מסמכים בכל קריאה).
//   • שחרור עמדה — המורה לבד, עד settings.teacherReleases בחודש; מעבר לזה — בקשה להנהלה (כמו קודם).
//
// המסמכים:
//   classes/{id}                 teacher, name, createdAt, updatedAt
//   members/{מורה~תלמיד}         teacher, student, name, picture, classId ('' = בלי כיתה), joinedAt, submits, lastSubmitAt, lastTitle
//   submissions/{id}             teacher, from (student / station), student | station, stationName, who, exercise{…}, lang, app, check{…},
//                                n, firstAt, createdAt, seenAt, size, fileCount, expiresAt (רק מעמדה — Timestamp, בשביל TTL)
//   submissionFiles/{id}         teacher, files[{path, text}], main, expiresAt — הקוד עצמו, נקרא רק כשהמורה פותח הגשה
//   students/{email}             + mode, teacher, joinedAt; ההרשמה: fullName, phone, registeredAt (שם מלא וטלפון — פעם אחת, בדפדפן)

import { Fail } from '../http.js';
import { db, timeId } from './firestore.js';
import { sha256Hex, normalizeCode } from './crypto.js';
import { webUser, readJson, log, settings, resolveToken, badCodeLimit, phoneNorm } from './index.js';

const nowIso = () => new Date().toISOString();
const month = () => nowIso().slice(0, 7);
const clip = (s, n) => String(s == null ? '' : s).replace(/[\u0000-\u001f\u007f]/g, ' ').trim().slice(0, n);
/** טקסט של קוד: שומרים ירידות שורה וטאבים, מורידים רק תווי בקרה אחרים */
const codeText = (s, n) => String(s == null ? '' : s).replace(/[\u0000-\u0008\u000b\u000c\u000e-\u001f\u007f]/g, '').slice(0, n);
const memberId = (teacher, student) => teacher + '~' + student;
const CHUNK = 400;                 // כמה מסמכים מוחקים בקריאה אחת (מחיקת כיתה / תלמיד) — הדף ממשיך לקרוא עד done
const MAX_FILES = 12, MAX_BYTES = 200 * 1024, MAX_OUTPUT = 2000;
const SUBMITS_PER_HOUR = 30;

// ------------------------------------------------------------------ מי פונה

/** מורה פעיל (מהדף בדפדפן — Google) */
async function teacherUser(request, env) {
  const u = await webUser(request, env);
  const t = await db(env).get('teachers/' + u.email);
  if (!t) throw new Fail(403, 'teacher', 'החשבון הזה לא מוגדר כמורה.');
  if (!t.active) throw new Fail(403, 'blocked', 'החשבון שלך כמורה הושבת. פונים להנהלה.');
  return Object.assign(u, { teacher: t });
}

/**
 * תלמיד: מהדף (Google — Authorization: Bearer) או מהתוסף (כרטיס הכניסה — x-dsviz-access / x-dsviz-machine).
 * מחזיר {email, name, picture}
 */
async function studentUser(request, env) {
  if (/^Bearer\s+\S+$/.test(request.headers.get('authorization') || '')) {
    const u = await webUser(request, env);
    return { email: u.email, name: u.name || '', picture: u.picture || '' };
  }
  // הפרטים של התלמיד (הרשמה, עצמאי / מורה) — גם כשהמאגר שלו עוד סגור (מחכה לתשלום): הכרטיס מזהה אותו בכל מקרה
  const r = await pluginUser(request, env, true);
  if (r.kind !== 'student') throw new Fail(403, 'student', 'הפעולה הזו לתלמידים שמחוברים עם Google.');
  return { email: r.profile.email, name: r.profile.name || '', picture: r.profile.picture || '' };
}

/**
 * מהתוסף: הכרטיס של המחשב (תלמיד / עמדה / מורה), חייב להיות בתוקף.
 * unpaidToo — גם תלמיד שהכרטיס שלו אמיתי אבל המאגר סגור לו כרגע (pay): לפעולות על הפרטים שלו, לא להגשה
 */
async function pluginUser(request, env, unpaidToo) {
  const token = request.headers.get('x-dsviz-access') || '';
  if (!token) throw new Fail(401, 'login', 'צריך להתחבר פעם אחת במחשב (בסרגל של DSViz).');
  const r = await resolveToken(env, token, request.headers.get('x-dsviz-machine') || '');
  if (r.status === 'blocked') throw new Fail(403, 'blocked', 'הגישה של המורה הזה הושבתה. פונים למורה או להנהלה.');
  if (r.status === 'pay' && unpaidToo && r.kind === 'student') return r;
  if (r.status === 'pay') throw new Fail(402, 'pay', 'המאגר סגור כרגע לחשבון הזה — קודם משלימים את התשלום (בחלון הכניסה של DSViz).');
  if (r.status !== 'ok') throw new Fail(401, 'login', 'הכניסה במחשב הזה כבר לא בתוקף — מתחברים שוב.');
  return r;
}

// ------------------------------------------------------------------ תלמיד: עצמאי / הצטרפות למורה

/** הבחירה בחלון הראשון: תלמיד עצמאי (גם יציאה מהמורה הנוכחי) */
async function studentMode(request, env) {
  const u = await studentUser(request, env);
  const b = await readJson(request);
  if (b.mode !== 'independent') throw new Fail(400, 'format', 'בחירה לא מוכרת');
  const store = db(env);
  const st = await store.get('students/' + u.email);
  if (st && st.teacher) await leaveTeacher(store, u.email, st.teacher, 'independent');
  await store.merge('students/' + u.email, { email: u.email, mode: 'independent', teacher: '', modeAt: nowIso() });
  return { ok: true, mode: 'independent' };
}

/** הצטרפות למורה בקוד הקבוע שלו. כבר משויך למורה אחר — צריך replace:true (התוסף / הדף שואלים קודם) */
async function studentJoin(request, env) {
  const u = await studentUser(request, env);
  const b = await readJson(request);
  const store = db(env);
  const code = normalizeCode(b.code);
  if (!code) {
    const lim = await badCodeLimit(env, request, true);
    await log(store, u.email, 'student.bad-code', '', 'קוד לא בפורמט' + (lim.count ? ` · ניסיון ${lim.count} בשעה הזו` : ''));
    return { ok: true, status: 'bad-code' };
  }
  await badCodeLimit(env, request, false);
  const at = nowIso();
  const res = await store.tx(async t => {
    const c = await t.get('teacherCodes/' + code);
    if (!c) return { status: 'bad-code' };
    const [teacher, st] = await t.getMany(['teachers/' + c.teacher, 'students/' + u.email]);
    if (!teacher || teacher.code !== code) return { status: 'bad-code' };
    if (!teacher.active) return { status: 'blocked', teacher };
    if (teacher.joinOpen === false) return { status: 'closed', teacher };
    const prev = st && st.teacher || '';
    const shown = clip(st && st.fullName || u.name, 80);   // המורה רואה את השם המלא מההרשמה (בחשבון Google לפעמים כינוי / בלי שם)
    const m = await t.get('members/' + memberId(c.teacher, u.email));
    if (prev === c.teacher && m && !m.leftAt) return { status: 'ok', teacher, already: true };
    if (prev && prev !== c.teacher && !b.replace) return { status: 'replace', teacher, prev };
    if (m) {   // חוזר למורה שעזב קודם: אותו מסמך (הכיתה וההגשות נשארו)
      t.update('members/' + memberId(c.teacher, u.email), { leftAt: '', leftWhy: '', name: shown || m.name || '', picture: clip(u.picture, 400) || m.picture || '', rejoinedAt: at });
    } else {
      t.set('members/' + memberId(c.teacher, u.email), {
        teacher: c.teacher, student: u.email, name: shown, picture: clip(u.picture, 400), classId: '', joinedAt: at, leftAt: '', submits: 0, lastSubmitAt: '', lastTitle: '',
      });
    }
    t.merge('students/' + u.email, { email: u.email, mode: 'teacher', teacher: c.teacher, joinedAt: at, modeAt: at });
    t.update('teachers/' + c.teacher, { updatedAt: at }, { members: 1 });
    return { status: 'ok', teacher, prev };
  });
  if (res.status === 'bad-code') {
    const lim = await badCodeLimit(env, request, true);
    await log(store, u.email, 'student.bad-code', '', 'קוד: ' + code.slice(0, 3) + '?' + (lim.count ? ` · ניסיון ${lim.count} בשעה הזו` : ''));
    return { ok: true, status: 'bad-code' };
  }
  const tv = res.teacher ? { name: res.teacher.name || '', school: res.teacher.school || '' } : null;
  if (res.status !== 'ok') return { ok: true, status: res.status, teacher: tv };
  if (res.prev && res.prev !== res.teacher.email) await leaveTeacher(store, u.email, res.prev, 'switched');
  if (!res.already) await log(store, u.email, 'student.join', res.teacher.email, res.prev ? 'עבר ממורה אחר' : '');
  return { ok: true, status: 'ok', teacher: tv, already: !!res.already };
}

/** יציאה מהמורה (מהתוסף / מהדף) — התלמיד לא משויך יותר; ההגשות שלו נשארות אצל המורה, עם "עזב" */
async function studentLeave(request, env) {
  const u = await studentUser(request, env);
  const store = db(env);
  const st = await store.get('students/' + u.email);
  if (st && st.teacher) await leaveTeacher(store, u.email, st.teacher, 'left');
  await store.merge('students/' + u.email, { email: u.email, mode: '', teacher: '', modeAt: nowIso() });
  return { ok: true };
}

/** הקשר מורה-תלמיד מסתיים: המסמך ב-members נשאר (עם leftAt), כדי שהמורה יראה את ההגשות; המונה של המורה יורד */
async function leaveTeacher(store, student, teacher, why) {
  const m = await store.get('members/' + memberId(teacher, student));
  if (m && !m.leftAt) {
    await store.update('members/' + memberId(teacher, student), { leftAt: nowIso(), leftWhy: why });
    await store.merge('teachers/' + teacher, {}, { members: -1 }).catch(() => {});
    await log(store, student, 'student.leave', teacher, why);
  }
}

/** מצב התלמיד (לדף "החשבון שלי" ולתוסף): עצמאי / משויך (לאיזה מורה) / עוד לא בחר */
export async function studentState(env, email) {
  const store = db(env);
  const st = await store.get('students/' + email);
  const mode = st && st.mode || '';
  let teacher = null;
  if (st && st.teacher) {
    const [t, m] = await store.getMany(['teachers/' + st.teacher, 'members/' + memberId(st.teacher, email)]);
    if (t && m && !m.leftAt) teacher = { name: t.name || '', school: t.school || '', email: st.teacher };
  }
  return { mode: teacher ? 'teacher' : (mode === 'teacher' ? '' : mode), teacher, registered: isRegistered(st), fullName: st && st.fullName || '' };
}

// ------------------------------------------------------------------ הרשמת תלמיד: שם מלא וטלפון (פעם אחת)

/** התלמיד השלים הרשמה: שם מלא וטלפון נייד (נשמרים ב-students/{email}) */
export function isRegistered(st) {
  return !!(st && st.fullName && st.phone);
}

/**
 * שם מלא: שם פרטי ושם משפחה — לפחות שתי מילים של שתי אותיות ומעלה; אותיות (בכל שפה), רווח, מקף, גרש וגרשיים, נקודה.
 * מחזיר את השם מסודר (רווח אחד בין מילים), או '' אם הוא לא תקין
 */
export function fullNameNorm(v) {
  const s = String(v == null ? '' : v).replace(/[\u0000-\u001f\u007f\u200e\u200f\u202a-\u202e\u2066-\u2069]/g, ' ').replace(/\s+/g, ' ').trim();
  if (s.length < 5 || s.length > 60) return '';
  if (!/^[\p{L}\p{M} .'\u05f3\u05f4\u2019"-]+$/u.test(s)) return '';
  const words = s.split(' ').filter(w => (w.match(/\p{L}/gu) || []).length >= 2);
  return words.length >= 2 ? s : '';
}

/**
 * הרשמה: שם מלא וטלפון. מהדף בדפדפן (אחרי הכניסה עם Google — לפני שחוזרים לתוכנה) או מהתוסף. אפשר לעדכן בכל זמן מ"החשבון שלי".
 * תלמיד שכבר משויך למורה — השם אצל המורה מתעדכן מיד.
 */
async function studentRegister(request, env) {
  const u = await studentUser(request, env);
  const b = await readJson(request);
  const fullName = fullNameNorm(b.fullName);
  if (!fullName) throw new Fail(400, 'name', 'כותבים שם פרטי ושם משפחה (אותיות בלבד, למשל: נועה כהן).');
  const phone = phoneNorm(b.phone);
  if (!phone) throw new Fail(400, 'phone', 'צריך מספר טלפון נייד תקני — 10 ספרות (למשל 050-1234567).');
  const store = db(env);
  const st = await store.get('students/' + u.email);
  const at = nowIso();
  const first = !isRegistered(st);
  await store.merge('students/' + u.email, Object.assign({ email: u.email, fullName, phone, profileAt: at }, first ? { registeredAt: at } : {}));
  if (st && st.teacher) {
    const m = await store.get('members/' + memberId(st.teacher, u.email));
    if (m && m.name !== fullName) await store.update('members/' + memberId(st.teacher, u.email), { name: fullName }).catch(() => {});
  }
  if (first) await log(store, u.email, 'student.register', '', fullName + ' · ' + phone);
  else if (st.fullName !== fullName || st.phone !== phone) await log(store, u.email, 'student.profile', '', fullName + ' · ' + phone);
  return Object.assign({ ok: true, first }, await studentState(env, u.email));
}

// ------------------------------------------------------------------ הגשה (מהתוסף)

async function submitLimit(env, subject) {
  const kv = env.USAGE && typeof env.USAGE.get === 'function' ? env.USAGE : env.STATS && typeof env.STATS.get === 'function' ? env.STATS : null;
  if (!kv) return;
  const key = 'submit:' + nowIso().slice(0, 13) + ':' + (await sha256Hex(subject)).slice(0, 24);
  const n = +(await kv.get(key)) || 0;
  if (n >= SUBMITS_PER_HOUR) throw new Fail(429, 'rate', `עד ${SUBMITS_PER_HOUR} הגשות בשעה מכל מחשב. נסו שוב בעוד שעה.`);
  await kv.put(key, String(n + 1), { expirationTtl: 7200 });
}

function cleanCheck(c) {
  c = c && typeof c === 'object' ? c : {};
  const bool = v => (v === true ? true : v === false ? false : null);
  return {
    ran: c.ran === true, compiles: bool(c.compiles), error: clip(c.error, 1000), output: codeText(c.output, MAX_OUTPUT), expected: codeText(c.expected, MAX_OUTPUT),
    outputMatch: bool(c.outputMatch), timedOut: c.timedOut === true, needsInput: c.needsInput === true,
  };
}

function cleanFiles(list) {
  if (!Array.isArray(list) || !list.length) throw new Fail(400, 'files', 'אין קובצי קוד להגשה.');
  if (list.length > MAX_FILES) throw new Fail(400, 'files', `עד ${MAX_FILES} קבצים בהגשה אחת.`);
  let size = 0;
  const files = list.map(f => {
    const path = clip(f && f.path, 160).replace(/\\/g, '/').replace(/^\/+/, '');
    const raw = String(f && f.text == null ? '' : f.text);
    if (!path || !/\.(java|cs)$/i.test(path)) throw new Fail(400, 'files', 'אפשר להגיש רק קובצי ‎.java / ‎.cs');
    size += new TextEncoder().encode(raw).length;
    if (size > MAX_BYTES) throw new Fail(400, 'files', 'ההגשה גדולה מדי (עד 200KB של קוד).');   // לא חותכים קוד בשקט
    const text = codeText(raw, MAX_BYTES);
    return { path, text };
  });
  if (size > MAX_BYTES) throw new Fail(400, 'files', 'ההגשה גדולה מדי (עד 200KB של קוד).');
  return { files, size };
}

async function submit(request, env) {
  const r = await pluginUser(request, env);
  if (r.kind === 'teacher') throw new Fail(403, 'teacher', 'מורה לא מגיש לעצמו — "הגשה למורה" היא לתלמידים ולעמדות המחשב.');
  const b = await readJson(request, 3 * MAX_BYTES);   // הקוד (עד 200KB) + הפלט + עטיפת JSON
  const store = db(env);
  const s = await settings(env);
  const ex = b.exercise && typeof b.exercise === 'object' ? b.exercise : {};
  const exercise = {
    source: ['bank', 'ai', 'own'].includes(ex.source) ? ex.source : 'own',
    bankId: clip(ex.bankId, 80), topic: clip(ex.topic, 60), title: clip(ex.title, 120), level: clip(ex.level, 20),
  };
  if (!exercise.title) exercise.title = exercise.bankId || 'תרגיל';
  const { files, size } = cleanFiles(b.files);
  const main = files.some(f => f.path === clip(b.main, 160)) ? clip(b.main, 160) : files[0].path;
  const lang = /\.cs$/i.test(main) ? 'csharp' : 'java';
  const at = nowIso();
  const exKey = exercise.bankId || exercise.title;
  let teacher, base, id, member = null, expires = null;
  if (r.kind === 'station') {
    const who = clip(b.who, 40);
    if (Array.from(who).length < 2) throw new Fail(400, 'who', 'כתבו את השם שלכם בשדה "שם המגיש" (לפחות 2 אותיות).');
    teacher = r.station.teacher;
    await submitLimit(env, 'station:' + r.station.id);
    id = 's' + (await sha256Hex([teacher, r.station.id, exKey, who.toLowerCase()].join('|'))).slice(0, 30);
    const days = Math.max(1, Math.min(30, +s.stationSubmitDays || 3));
    expires = new Date(Date.now() + days * 86400000);
    base = { from: 'station', station: r.station.id, stationName: r.station.name || '', who };
  } else {
    const email = r.profile.email;
    const st = await store.get('students/' + email);
    teacher = st && st.teacher || '';
    if (teacher) member = await store.get('members/' + memberId(teacher, email));
    if (!teacher || !member || member.leftAt) throw new Fail(403, 'no-teacher', 'כדי להגיש למורה צריך קודם להצטרף אליו עם קוד המורה (בחלון הכניסה של DSViz או בדף "החשבון שלי").');
    await submitLimit(env, 'student:' + email);
    id = 'u' + (await sha256Hex([teacher, email, exKey].join('|'))).slice(0, 30);
    base = { from: 'student', student: email, who: member.name || r.profile.name || email };
  }
  const prev = await store.get('submissions/' + id);
  const doc = Object.assign(base, {
    teacher, exercise, lang, app: clip(b.app, 40), check: cleanCheck(b.check), size, fileCount: files.length,
    n: (prev && prev.n || 0) + 1, firstAt: prev && prev.firstAt || at, createdAt: at, seenAt: '',
  });
  const filesDoc = { teacher, files, main };
  if (expires) { doc.expiresAt = expires; filesDoc.expiresAt = expires; }
  await store.commit(w => {
    w.set('submissions/' + id, doc);
    w.set('submissionFiles/' + id, filesDoc);
    if (member) w.update('members/' + memberId(teacher, base.student), { lastSubmitAt: at, lastTitle: exercise.title }, prev ? undefined : { submits: 1 });
  });
  return { ok: true, id, at, replaced: !!prev, teacher: r.teacher ? r.teacher.name : undefined };
}

// ------------------------------------------------------------------ המורה: עמדת הניהול (מהדף)

function expired(sub) {
  return !!sub.expiresAt && Date.parse(sub.expiresAt) < Date.now();
}

/** הגשות של עמדות שפג תוקפן — נמחקות כאן (גם בלי מדיניות TTL ב-Firestore) */
async function dropExpired(store, subs) {
  const old = subs.filter(expired);
  if (old.length) await store.commit(w => { for (const x of old.slice(0, CHUNK / 2)) { w.remove('submissions/' + x._id); w.remove('submissionFiles/' + x._id); } }).catch(() => {});
  return subs.filter(x => !expired(x));
}

function subRow(x) {
  return {
    id: x._id, from: x.from, student: x.student || '', station: x.station || '', who: x.who || '', exercise: x.exercise || {}, lang: x.lang || '', app: x.app || '',
    check: x.check || {}, n: x.n || 1, createdAt: x.createdAt, seen: !!x.seenAt, expiresAt: x.expiresAt || '',
  };
}

async function releasesLeft(env, t) {
  const s = await settings(env);
  const used = t.releases && t.releases.month === month() ? t.releases.n || 0 : 0;
  return { limit: s.teacherReleases, used, left: Math.max(0, s.teacherReleases - used) };
}

/** הכול בבת אחת לעמדת הניהול: הכיתות, התלמידים, העמדות (עם ההגשות שממתינות), המתגים */
async function consoleData(request, env) {
  const u = await teacherUser(request, env);
  const store = db(env);
  const t = u.teacher;
  const [classes, members, stations, stationSubs] = await Promise.all([
    store.query('classes', { where: [['teacher', '==', u.email]] }),
    store.query('members', { where: [['teacher', '==', u.email]] }),
    store.query('stations', { where: [['teacher', '==', u.email]] }),
    store.query('submissions', { where: [['teacher', '==', u.email], ['from', '==', 'station']] }),
  ]);
  const live = await dropExpired(store, stationSubs);
  const byStation = new Map();
  for (const x of live) byStation.set(x.station, (byStation.get(x.station) || []).concat([x]));
  const collator = new Intl.Collator('he', { numeric: true });
  const s = await settings(env);
  return {
    ok: true,
    teacher: { email: u.email, name: t.name || '', school: t.school || '', code: t.code || '', seats: t.seats || 0, used: t.used || 0, joinOpen: t.joinOpen !== false },
    releases: await releasesLeft(env, t),
    stationSubmitDays: s.stationSubmitDays,
    classes: classes.map(c => ({ id: c._id, name: c.name, createdAt: c.createdAt })).sort((a, b) => collator.compare(a.name, b.name)),
    students: members.map(m => ({
      email: m.student, name: m.name || '', picture: m.picture || '', classId: m.classId || '', joinedAt: m.joinedAt, left: !!m.leftAt, leftAt: m.leftAt || '',
      submits: m.submits || 0, lastSubmitAt: m.lastSubmitAt || '', lastTitle: m.lastTitle || '',
    })).sort((a, b) => collator.compare(a.name || a.email, b.name || b.email)),
    stations: stations.filter(x => x.active).map(x => {
      const subs = (byStation.get(x._id) || []).sort((a, b) => String(b.createdAt).localeCompare(String(a.createdAt)));
      return { id: x._id, name: x.name, lastSeen: x.lastSeen, client: x.client || '', claimedAt: x.claimedAt, pending: subs.length, unseen: subs.filter(y => !y.seenAt).length };
    }).sort((a, b) => collator.compare(a.name, b.name)),
  };
}

async function listSubmissions(request, env, url) {
  const u = await teacherUser(request, env);
  const store = db(env);
  const student = clip(url.searchParams.get('student'), 200).toLowerCase(), station = clip(url.searchParams.get('station'), 64);
  if (!student && !station) throw new Fail(400, 'format', 'חסר תלמיד או עמדה');
  let list = await store.query('submissions', { where: [['teacher', '==', u.email], student ? ['student', '==', student] : ['station', '==', station]] });
  if (station) list = await dropExpired(store, list);
  return { ok: true, submissions: list.sort((a, b) => String(b.createdAt).localeCompare(String(a.createdAt))).map(subRow) };
}

async function getSubmission(request, env, url) {
  const u = await teacherUser(request, env);
  const store = db(env);
  const id = clip(url.searchParams.get('id'), 64);
  const [x, f] = await store.getMany(['submissions/' + id, 'submissionFiles/' + id]);
  if (!x || x.teacher !== u.email || expired(x)) throw new Fail(404, 'not_found', 'ההגשה לא נמצאה (אולי כבר נמחקה).');
  if (!x.seenAt) await store.update('submissions/' + id, { seenAt: nowIso() }).catch(() => {});
  return { ok: true, submission: Object.assign(subRow(x), { seen: true }), files: f ? f.files || [] : [], main: f ? f.main || '' : '' };
}

/** מחיקת הגשה של עמדה, או כל ההגשות של עמדה ({station, all:true}). הגשות של תלמידים נמחקות עם התלמיד / הכיתה */
async function deleteSubmission(request, env) {
  const u = await teacherUser(request, env);
  const b = await readJson(request);
  const store = db(env);
  let ids;
  if (b.all) {
    const station = clip(b.station, 64);
    ids = (await store.query('submissions', { where: [['teacher', '==', u.email], ['station', '==', station]] })).map(x => x._id);
  } else {
    const x = await store.get('submissions/' + clip(b.id, 64));
    if (!x || x.teacher !== u.email) throw new Fail(404, 'not_found', 'ההגשה לא נמצאה (אולי כבר נמחקה).');
    if (x.from !== 'station') throw new Fail(400, 'format', 'הגשות של תלמיד נמחקות עם התלמיד (מחיקה ברשימת התלמידים).');
    ids = [x._id];
  }
  for (let i = 0; i < ids.length; i += CHUNK / 2) {
    await store.commit(w => { for (const id of ids.slice(i, i + CHUNK / 2)) { w.remove('submissions/' + id); w.remove('submissionFiles/' + id); } });
  }
  return { ok: true, deleted: ids.length };
}

/** שחרור עמדה על ידי המורה — עד settings.teacherReleases בחודש */
async function releaseStation(request, env) {
  const u = await teacherUser(request, env);
  const b = await readJson(request);
  const store = db(env);
  const id = clip(b.id, 64);
  const s = await settings(env);
  const out = await store.tx(async t => {
    const [st, teacher] = await t.getMany(['stations/' + id, 'teachers/' + u.email]);
    if (!st || st.teacher !== u.email) throw new Fail(404, 'not_found', 'העמדה לא נמצאה ברשימה שלך.');
    if (!st.active) return { already: true, st };
    const used = teacher.releases && teacher.releases.month === month() ? teacher.releases.n || 0 : 0;
    if (used >= s.teacherReleases) {
      throw new Fail(429, 'releases', `שחררת כבר ${used} עמדות החודש (עד ${s.teacherReleases}). אפשר לשלוח בקשה להנהלה — ב"בקשה לשחרר" שליד העמדה.`);
    }
    const at = nowIso();
    t.update('stations/' + id, { active: false, releasedAt: at, releasedBy: u.email });
    t.update('teachers/' + u.email, { used: Math.max(0, (teacher.used || 0) - 1), releases: { month: month(), n: used + 1 }, updatedAt: at });
    return { st, n: used + 1 };
  });
  if (!out.already) {
    await log(store, u.email, 'station.release', u.email, `${out.st.name} · המורה (${out.n}/${s.teacherReleases} החודש)`);
    const subs = await store.query('submissions', { where: [['teacher', '==', u.email], ['station', '==', id]] });
    if (subs.length) await store.commit(w => { for (const x of subs.slice(0, CHUNK / 2)) { w.remove('submissions/' + x._id); w.remove('submissionFiles/' + x._id); } }).catch(() => {});
  }
  return { ok: true, already: !!out.already };
}

/** כיתות: יצירה, שינוי שם, מחיקה (עם כל התלמידים והעבודות — בשלבים: {done:false} → הדף קורא שוב) */
async function classOp(request, env) {
  const u = await teacherUser(request, env);
  const b = await readJson(request);
  const store = db(env);
  const name = clip(b.name, 40);
  if (b.op === 'create') {
    if (!name) throw new Fail(400, 'name', 'כתבו שם לכיתה, למשל "יא 3".');
    const mine = await store.query('classes', { where: [['teacher', '==', u.email]] });
    if (mine.length >= 60) throw new Fail(400, 'limit', 'עד 60 כיתות.');
    if (mine.some(c => c.name === name)) throw new Fail(400, 'exists', 'כבר יש כיתה בשם הזה.');
    const id = timeId();
    await store.set('classes/' + id, { teacher: u.email, name, createdAt: nowIso(), updatedAt: nowIso() });
    return { ok: true, id };
  }
  const c = await store.get('classes/' + clip(b.id, 40));
  if (!c || c.teacher !== u.email) throw new Fail(404, 'not_found', 'הכיתה לא נמצאה.');
  if (b.op === 'rename') {
    if (!name) throw new Fail(400, 'name', 'כתבו שם לכיתה.');
    await store.update('classes/' + c._id, { name, updatedAt: nowIso() });
    return { ok: true };
  }
  if (b.op === 'delete') {
    if (clip(b.confirm, 40) !== c.name) throw new Fail(400, 'confirm', 'כדי למחוק כיתה מקלידים את השם שלה בדיוק.');
    const members = await store.query('members', { where: [['teacher', '==', u.email], ['classId', '==', c._id]] });
    let budget = CHUNK, removed = 0;
    for (const m of members) {
      const r = await deleteMemberData(store, u.email, m, budget);
      budget -= r.writes;
      if (!r.done) return { ok: true, done: false, left: members.length - removed };
      removed++;
      if (budget <= 20) return { ok: true, done: false, left: members.length - removed };
    }
    await store.remove('classes/' + c._id);
    await log(store, u.email, 'class.delete', c.name, `${removed} תלמידים`);
    return { ok: true, done: true, removed };
  }
  throw new Fail(400, 'format', 'פעולה לא מוכרת');
}

/** מוחק את ההגשות של תלמיד אצל המורה (עד budget כתיבות), ואז את הקשר עצמו. done=false — יש עוד */
async function deleteMemberData(store, teacher, m, budget) {
  const subs = await store.query('submissions', { where: [['teacher', '==', teacher], ['student', '==', m.student]], limit: Math.max(1, Math.floor((budget - 4) / 2)) });
  if (subs.length) await store.commit(w => { for (const x of subs) { w.remove('submissions/' + x._id); w.remove('submissionFiles/' + x._id); } });
  let writes = subs.length * 2;
  const more = await store.query('submissions', { where: [['teacher', '==', teacher], ['student', '==', m.student]], limit: 1 });
  if (more.length) return { done: false, writes };
  const st = await store.get('students/' + m.student);
  await store.commit(w => {
    w.remove('members/' + memberId(teacher, m.student));
    // התלמיד לא משויך יותר; בפעם הבאה התוסף / הדף מציעים שוב "עצמאי / קוד מורה" (אפשר להצטרף שוב עם הקוד)
    if (st && st.teacher === teacher) w.merge('students/' + m.student, { mode: '', teacher: '', removedBy: teacher, removedAt: nowIso() });
    if (!m.leftAt) w.merge('teachers/' + teacher, {}, { members: -1 });
  });
  return { done: true, writes: writes + 3 };
}

/** תלמיד: העברה לכיתה, או מחיקה (מהרשימה + כל ההגשות שלו) */
async function memberOp(request, env) {
  const u = await teacherUser(request, env);
  const b = await readJson(request);
  const store = db(env);
  const student = clip(b.student, 200).toLowerCase();
  const m = await store.get('members/' + memberId(u.email, student));
  if (!m) throw new Fail(404, 'not_found', 'התלמיד לא נמצא ברשימה שלך.');
  if (b.op === 'move') {
    const classId = clip(b.classId, 40);
    if (classId) {
      const c = await store.get('classes/' + classId);
      if (!c || c.teacher !== u.email) throw new Fail(404, 'not_found', 'הכיתה לא נמצאה.');
    }
    await store.update('members/' + m._id, { classId });
    return { ok: true };
  }
  if (b.op === 'delete') {
    const r = await deleteMemberData(store, u.email, m, CHUNK);
    if (r.done) await log(store, u.email, 'student.remove', student, `${m.submits || 0} הגשות`);
    return { ok: true, done: r.done };
  }
  throw new Fail(400, 'format', 'פעולה לא מוכרת');
}

async function teacherSettings(request, env) {
  const u = await teacherUser(request, env);
  const b = await readJson(request);
  const upd = {};
  if (b.joinOpen !== undefined) upd.joinOpen = !!b.joinOpen;
  if (Object.keys(upd).length) {
    await db(env).update('teachers/' + u.email, Object.assign(upd, { updatedAt: nowIso() }));
    await log(db(env), u.email, 'teacher.settings', '', JSON.stringify(upd));
  }
  return { ok: true };
}

async function myStudent(request, env) {
  const u = await studentUser(request, env);
  return Object.assign({ ok: true }, await studentState(env, u.email));
}

export const CLASSROOM_ROUTES = {
  'GET /v1/student/state': myStudent,
  'POST /v1/student/mode': studentMode,
  'POST /v1/student/join': studentJoin,
  'POST /v1/student/leave': studentLeave,
  'POST /v1/student/register': studentRegister,
  'POST /v1/submit': submit,
  'GET /v1/me/console': consoleData,
  'GET /v1/me/submissions': listSubmissions,
  'GET /v1/me/submission': getSubmission,
  'POST /v1/me/submission/delete': deleteSubmission,
  'POST /v1/me/station/release': releaseStation,
  'POST /v1/me/class': classOp,
  'POST /v1/me/member': memberOp,
  'POST /v1/me/settings': teacherSettings,
};
