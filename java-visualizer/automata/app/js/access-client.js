// הכניסה המשותפת של DSViz בתוך "מודלים חישוביים" — מדבר עם התוכנה המקומית (AutomataLauncher) שמגישה את הדף.
// מורה / תלמיד / עמדת מחשב שנכנסו ב-Eclipse / IntelliJ / Visual Studio מחוברים גם כאן, וההפך; התנתקות בכל מקום — מגיעה לכאן מיד.
// החוזה המלא: java-visualizer/access/README.md ("הממשק לדף").
//
//   import { DSVizAccess } from './access-client.js';
//   if (DSVizAccess.available()) {
//     DSVizAccess.onChange(a => render(a));          // { state, name, email, role, apps, offline, ... } — מיד, ובכל שינוי
//     await DSVizAccess.signIn('teacher');           // נפתח הדפדפן עם Google; מחזיר את התוצאה בסוף הכניסה
//   }
//
// בלי התוכנה (קובץ שנפתח ישירות, אתר) — available() מחזיר false, ו-get() מחזיר { state: 'unavailable' }.
// המפתח של ההפעלה מגיע בכתובת (#dsviz-token=...) ונשמר ב-sessionStorage; הוא נמחק מהכתובת מיד. השרת לא מחזיר אף פעם כרטיס או סוד.

const KEY = 'dsviz.token';
const HEADER = 'X-DSViz-Token';
let token = '';
let memory = '';   // בלי sessionStorage (מצב פרטי / חסום)

function readToken() {
  try {
    const m = /(?:^#|&)dsviz-token=([A-Za-z0-9_-]{20,100})/.exec(location.hash || '');
    if (m) {
      memory = m[1];
      try { sessionStorage.setItem(KEY, m[1]); } catch (e) { /* בלי אחסון — רק בזיכרון */ }
      const rest = location.hash.replace(/(^#|&)dsviz-token=[A-Za-z0-9_-]+/, '$1').replace(/^#&/, '#');
      try { history.replaceState(null, '', location.pathname + location.search + (rest === '#' ? '' : rest)); } catch (e) { /* לא חשוב */ }
      return m[1];
    }
  } catch (e) { /* בלי location */ }
  try { const s = sessionStorage.getItem(KEY); if (s) return s; } catch (e) { /* בלי אחסון */ }
  return memory;
}
token = readToken();
const local = (() => { try { return location.protocol === 'http:' && /^(127\.0\.0\.1|localhost)$/.test(location.hostname); } catch (e) { return false; } })();

const listeners = new Set();
const signinWaiters = [];
let last = null;
let streaming = false;
let refused = false;

const UNAVAILABLE = Object.freeze({ state: 'unavailable', name: '', email: '', role: '', apps: [], offline: false });

async function call(method, path, body) {
  if (!DSVizAccess.available()) return { ok: false, status: 'unavailable' };
  const init = { method, headers: { [HEADER]: token }, cache: 'no-store', credentials: 'omit' };
  if (body !== undefined) {
    init.headers['Content-Type'] = 'application/json';
    init.body = JSON.stringify(body);
  }
  let res;
  try {
    res = await fetch(path, init);
  } catch (e) {
    return { ok: false, status: 'offline', message: 'התוכנה "מודלים חישוביים" לא עונה — פותחים אותה שוב מהקיצור.' };
  }
  if (res.status === 401) { refused = true; return { ok: false, status: 'unavailable' }; }
  try { return await res.json(); } catch (e) { return { ok: false, status: 'error' }; }
}

function emit(a) {
  last = a;
  for (const cb of listeners) { try { cb(a); } catch (e) { /* מאזין שנכשל לא עוצר את האחרים */ } }
}

/** זרם האירועים (SSE דרך fetch — כדי לשלוח את המפתח בכותרת ולא בכתובת). גם מסמן לתוכנה שהחלון פתוח */
async function stream() {
  if (streaming || !DSVizAccess.available()) return;
  streaming = true;
  try {
    const res = await fetch('/api/events', { headers: { [HEADER]: token }, cache: 'no-store', credentials: 'omit' });
    if (res.status === 401) { refused = true; return; }
    if (!res.ok || !res.body) throw new Error('events ' + res.status);
    const reader = res.body.getReader();
    const dec = new TextDecoder();
    let buf = '';
    for (;;) {
      const { value, done } = await reader.read();
      if (done) break;
      buf += dec.decode(value, { stream: true });
      let i;
      while ((i = buf.indexOf('\n\n')) >= 0) {
        const frame = buf.slice(0, i);
        buf = buf.slice(i + 2);
        let ev = 'message', data = '';
        for (const line of frame.split('\n')) {
          if (line.startsWith('event:')) ev = line.slice(6).trim();
          else if (line.startsWith('data:')) data += line.slice(5).trim();
        }
        if (!data) continue;
        let obj;
        try { obj = JSON.parse(data); } catch (e) { continue; }
        if (ev === 'access') emit(obj);
        else if (ev === 'signin') signinWaiters.splice(0).forEach(w => w(obj));
      }
    }
  } catch (e) {
    /* נתק — מתחברים שוב */
  } finally {
    streaming = false;
    if (!refused) setTimeout(stream, 2000);
  }
}

export const DSVizAccess = {
  /** הדף נפתח דרך התוכנה המקומית (יש מפתח, והשרת לא דחה אותו) */
  available() {
    return local && !!token && !refused;
  },

  /** מי מחובר עכשיו: { state: 'signed-out'|'teacher'|'student'|'station'|'pending'|'blocked', name, email, role, apps, offline, ... } */
  async get() {
    if (!this.available()) return UNAVAILABLE;
    const a = await call('GET', '/api/access');
    if (!a || !a.state) return UNAVAILABLE;
    last = a;
    return a;
  },

  /** כניסה עם Google (role: 'teacher' | 'student') — אותה כניסה כמו ב-Eclipse. מחזיר את התוצאה כשהכניסה מסתיימת: { ok, status, message } */
  async signIn(role) {
    const done = new Promise(res => signinWaiters.push(res));
    const r = await call('POST', '/api/signin', { role });
    if (!r.ok) { signinWaiters.splice(signinWaiters.length - 1, 1); return r; }
    stream();
    return done;
  },

  /** מבטל כניסה שמחכה לדפדפן */
  async cancelSignIn() {
    return call('POST', '/api/signin-cancel', {});
  },

  /** עמדת מחשב עם קוד המורה: { ok, status: 'ok'|'bad-code'|'full'|'blocked'|'offline'|..., message } */
  async claimStation(code, name) {
    return call('POST', '/api/station', { code: String(code || ''), name: String(name || '') });
  },

  /** התנתקות של מורה / תלמיד — בכל התוכנות במחשב. עמדת מחשב לא מתנתקת מכאן (status: 'station') */
  async signOut() {
    return call('POST', '/api/signout', {});
  },

  /** פותח את עמדת הניהול של המורה (החלון של DSViz) */
  async openTeacherPanel() {
    return call('POST', '/api/teacher-panel', {});
  },

  /** cb(access) — מיד (אם כבר ידוע) ובכל שינוי, גם כשהשינוי נעשה ב-Eclipse / Visual Studio. מחזיר פונקציה להפסקה */
  onChange(cb) {
    listeners.add(cb);
    if (last) { try { cb(last); } catch (e) { /* מאזין שנכשל */ } }
    else if (this.available()) this.get().then(a => { if (listeners.has(cb) && a.state !== 'unavailable') { try { cb(a); } catch (e) { /* מאזין */ } } });
    stream();
    return () => listeners.delete(cb);
  },
};

// נטען אוטומטית מהדף שהתוכנה מגישה: מחזיק את זרם האירועים פתוח — כך התוכנה יודעת שהחלון פתוח (ונסגרת אחריו)
if (DSVizAccess.available()) stream();
