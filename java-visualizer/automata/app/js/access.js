/*
 * access.js - הכניסה המשותפת של DSViz (מורה / תלמיד / עמדת מחשב) בתוך "מודלים חישוביים". טהור (בלי DOM), נבדק ב-test/access.test.js.
 * מדבר עם DSVizAccess (js/access-client.js, שהתוכנה המקומית מגישה); הנוסחים כמו במסך הכניסה של Java (AccessGate.java).
 *
 *   const c = AutomataAccess.controller(DSVizAccess, { change(a) {}, gained(a) {}, lost(before, now) {} });
 *   c.start();  c.allowed();  await c.signIn('teacher');  await c.station(code, name);  await c.signOut(ask);
 *
 * בלי התוכנה (קובץ שנפתח ישירות, אתר): mode = 'unavailable', ושום דבר לא נשלח.
 * כל פעולה של כניסה מחזירה "מסך" לחלון הכניסה: { view: 'choose' | 'message' | 'pay' | 'success' | 'inline', title, text, tone }.
 */
(function (root, factory) {
  const api = factory();
  if (typeof module === 'object' && module.exports) module.exports = api;
  else root.AutomataAccess = api;
})(typeof globalThis !== 'undefined' ? globalThis : this, function () {
  'use strict';
  const SIGNED = ['teacher', 'student', 'station'];
  const allowed = (a) => !!a && SIGNED.includes(a.state);
  /** הודעות מהשרת / מהתוכנה: בלי קו מפריד ארוך */
  const clean = (s) => String(s || '').replace(/\s*—\s*/g, ', ').trim();
  const who = (a) => (a && (a.name || a.email)) || '';

  /** מה כתוב על הכפתור בסרגל העליון */
  function label(a) {
    if (!a) return '';
    switch (a.state) {
      case 'teacher': return 'מורה: ' + who(a);
      case 'student': return 'תלמיד: ' + who(a);
      case 'station': return 'עמדה: ' + who(a);
      case 'pending': return 'מחכה לכניסה';
      case 'blocked': return 'הגישה הושבתה';
      case 'unavailable': return '';
      default: return 'לא מחובר';
    }
  }
  /** מורה / תלמיד שנכנסו עם Google. עמדת מחשב לא מתנתקת מכאן: המורה / ההנהלה משחררים אותה (כמו ב-Java) */
  const canSignOut = (a) => !!a && (a.state === 'teacher' || a.state === 'student') && a.canSignOut !== false;

  const T = {
    title: 'כניסה למאגר השאלות',
    sub: 'פעם אחת במחשב הזה, ואחר כך המאגר נפתח לבד, גם בלי אינטרנט.',
    roles: [
      { id: 'teacher', title: 'מורה', text: 'כניסה עם חשבון Google שההנהלה הגדירה. רואים את קוד המורה ואת העמדות.', action: 'כניסה עם Google' },
      { id: 'station', title: 'עמדת מחשב בכיתה', text: 'מקלידים את קוד המורה, והמחשב הזה פתוח לכל התלמידים שעובדים עליו.', action: 'הקלדת קוד המורה' },
      { id: 'student', title: 'תלמיד', text: 'כניסה עם חשבון Google. מי ששילם: המאגר נפתח בכל המחשבים שלו.', action: 'כניסה עם Google' },
    ],
    stationTitle: 'חיבור עמדת מחשב',
    stationText: 'את הקוד מקבלים מהמורה (4 תווים). כל מחשב שמתחבר משתמש בהיתר אחד של המורה; אותו מחשב שמתחבר שוב: בלי היתר נוסף.',
    code: 'קוד המורה', name: 'שם למחשב (כדי שהמורה יזהה אותו ברשימה)', connect: 'חיבור המחשב', connecting: 'מתחבר…', back: 'חזרה', cancel: 'ביטול',
    waitTitle: 'ממשיכים בדפדפן', waitText: 'נפתח דפדפן עם הכניסה של Google. נכנסים שם, והחלון הזה ממשיך לבד.',
    blocked: 'הגישה של החשבון הושבתה. פונים להנהלה.',
    unavailableTitle: 'מאגר השאלות פתוח בתוכנה המותקנת',
    unavailableText: 'מאגר השאלות (עם הבדיקה של כל סעיף וההתקדמות) עובד בתוכנה "מודלים חישוביים" שמותקנת עם DSViz, ונפתחת מהקיצור שלה. כאן העורך, הדוגמאות והשפה עובדים כרגיל.',
  };

  const msg = (title, text, tone) => ({ view: 'message', title, text, tone: tone || 'warn' });
  const success = (title, text) => ({ view: 'success', title, text, tone: 'ok' });
  const payment = () => ({ view: 'pay', title: 'מחכים לתשלום', text: 'דף התשלום פתוח בדפדפן. אחרי שהתשלום עובר, החלון הזה מתעדכן לבד. לא צריך ללחוץ על כלום.', tone: 'info' });
  const paid = () => success('התשלום התקבל', 'המאגר פתוח עד סוף שנת הלימודים, בכל המחשבים שלך.');

  /** התוצאה של כניסה עם Google (afterSignIn ב-Java) */
  function signInView(role, r) {
    const m = clean(r && r.message);
    switch (r && r.status) {
      case 'ok': return success('נכנסת בהצלחה', role === 'teacher' ? 'המאגר פתוח. את קוד המורה ואת העמדות רואים בדף "החשבון שלי" בדפדפן.' : 'המאגר פתוח, גם בפעם הבאה, גם בלי אינטרנט.');
      case 'pay': return payment();
      case 'not-teacher': return msg('החשבון עוד לא מוגדר כמורה', 'בדפדפן אפשר לשלוח בקשה להנהלה. אחרי האישור לוחצים שוב "כניסה עם Google".');
      case 'blocked': return msg('הגישה של החשבון הושבתה', 'פונים להנהלה.');
      case 'devices': return msg('החשבון כבר מחובר בכל המחשבים המותרים', (m ? m + ' ' : '') + 'בדפדפן אפשר לנתק מחשב שכבר לא בשימוש, ואז לנסות שוב.');
      case 'cancelled': return m ? msg('לא התקבלה כניסה', m) : { view: 'choose' };
      default: return msg('הכניסה לא הצליחה', m || 'נסו שוב בעוד רגע.');
    }
  }

  /** בדיקה של השדות לפני החיבור (claim ב-Java). null: תקין */
  function stationInput(code, name) {
    if (String(code || '').replace(/[\s-]/g, '').length !== 4) return 'הקוד הוא 4 תווים, למשל K7QM.';
    if (!String(name || '').trim()) return 'כתבו שם למחשב, למשל "מעבדה 2 · עמדה 14".';
    return null;
  }
  /** התוצאה של חיבור עמדה. teacher: שם המורה (מהכניסה אחרי החיבור) */
  function stationView(r, teacher) {
    const t = teacher || 'המורה';
    switch (r && r.status) {
      case 'ok': return success('המחשב מחובר', 'העמדה של ' + t + ': המאגר פתוח לכל מי שעובד על המחשב הזה, גם בלי אינטרנט.');
      case 'bad-code': return { view: 'inline', text: 'הקוד לא נמצא. בודקים מול המורה ומקלידים שוב.' };
      case 'full': return msg('אין עוד היתרים', 'כל העמדות של ' + t + ' כבר בשימוש. אפשר לקנות עוד עמדות בלוח המורה, או לבקש מההנהלה לשחרר מחשב שהוחלף.');
      case 'blocked': return msg('הגישה של המורה הושבתה', 'פונים למורה או להנהלה.');
      default: return { view: 'inline', text: clean(r && r.message) || 'החיבור לא הצליח. נסו שוב.' };
    }
  }

  /** "להתנתק?" (AccessGate.signOut) */
  function signOutQuestion(a) {
    const teacher = a && a.state === 'teacher', mail = (a && a.email) || '';
    return { title: 'התנתקות', ok: 'התנתקות', cancel: 'ביטול',
      lines: ['להתנתק' + (mail ? ' מהחשבון ' + mail : '') + ' במחשב הזה?', teacher ? 'עמדת הניהול והמאגר ייסגרו. עמדות המחשב שחיברת בכיתה ממשיכות לעבוד כרגיל.' : 'המאגר ייסגר.', 'בפעם הבאה ייפתח מסך הכניסה.'] };
  }

  /** הבקר: מי מחובר, שינויים חיים (גם מ-Eclipse / Visual Studio), והפעולות של חלון הכניסה */
  function controller(client, hooks) {
    hooks = hooks || {};
    const ok = !!client && typeof client.available === 'function' && client.available();
    const c = {
      mode: ok ? 'loading' : 'unavailable',
      access: null,
      cancelled: false,
      allowed: () => c.mode === 'ready' && allowed(c.access),
      /** שער הגישה של המאגר (AutomataBank.setAccess) */
      gate: () => c.allowed(),
      start() {
        if (c.mode === 'unavailable') { if (hooks.change) hooks.change(null); return c; }
        c.stop = client.onChange((a) => c.update(a));
        return c;
      },
      update(a) {
        if (!a || !a.state) return;
        if (a.state === 'unavailable') { c.mode = 'unavailable'; c.access = null; if (hooks.change) hooks.change(null); return; }
        const was = c.allowed(), before = c.access;
        c.access = a; c.mode = 'ready';
        if (hooks.change) hooks.change(a);
        if (was && !allowed(a) && hooks.lost) hooks.lost(before, a);
        if (!was && allowed(a) && hooks.gained) hooks.gained(a);
      },
      async refresh() { const a = await client.get(); c.update(a); return a; },
      async signIn(role) {
        c.cancelled = false;
        let r;
        try { r = await client.signIn(role); } catch (e) { r = { ok: false, status: 'error', message: 'הכניסה לא הצליחה.' }; }
        // ביטול כשהכניסה כבר הסתיימה בדפדפן: נכנסנו, ומראים את התוצאה ולא "בוטל"
        if (c.cancelled && !(r && r.ok) && !(r && r.status === 'pay')) return { view: 'choose' };
        if (r && r.ok) await c.refresh().catch(() => null);
        return signInView(role, r || {});
      },
      cancel() { c.cancelled = true; return client.cancelSignIn(); },
      async station(code, name) {
        const bad = stationInput(code, name);
        if (bad) return { view: 'inline', text: bad };
        let r;
        try { r = await client.claimStation(String(code).trim(), String(name).trim()); } catch (e) { r = { ok: false, status: 'error', message: 'החיבור לא הצליח.' }; }
        let a = null;
        if (r && (r.ok || r.status === 'full')) a = await c.refresh().catch(() => null);
        return stationView(r || {}, a && a.teacher);
      },
      /** ask(question) -> Promise<boolean>. מחזיר { ok, message } או null (בוטל / עמדה) */
      async signOut(ask) {
        if (!canSignOut(c.access)) return null;
        if (!(await ask(signOutQuestion(c.access)))) return null;
        let r;
        try { r = await client.signOut(); } catch (e) { r = { ok: false, status: 'offline', message: 'התנתקת במחשב הזה.' }; }
        await c.refresh().catch(() => null);
        return { ok: !!(r && r.ok), message: r && r.ok ? 'התנתקת. בפעם הבאה שפותחים את המאגר מופיע מסך הכניסה.' : clean(r && r.message) || 'ההתנתקות לא הצליחה.' };
      },
      openTeacherPanel() { return client.openTeacherPanel(); },
    };
    return c;
  }

  return { SIGNED, allowed, label, canSignOut, clean, T, signInView, stationInput, stationView, signOutQuestion, payment, paid, controller };
});
