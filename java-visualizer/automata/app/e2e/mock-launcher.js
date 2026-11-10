/*
 * e2e/mock-launcher.js - תחליף קטן ל-AutomataLauncher (התוכנה המקומית) לבדיקות: מגיש את app/ על 127.0.0.1, וממשק /api/* לפי
 * החוזה ב-java-visualizer/access/README.md: GET /api/access, POST /api/signin, /api/signin-cancel, /api/station, /api/signout,
 * /api/teacher-panel, ו-GET /api/events (SSE: access, signin). מפתח בכותרת X-DSViz-Token. הבדיקה שולטת במצב מבפנים:
 *   const m = await start({ access: { state: 'teacher', ... } });  page.goto(m.url);  m.set({...});  m.finishSignIn(result, access);
 */
'use strict';
const http = require('http');
const fs = require('fs');
const path = require('path');

const ROOT = path.resolve(__dirname, '..');
const TYPES = { '.html': 'text/html; charset=utf-8', '.js': 'text/javascript; charset=utf-8', '.css': 'text/css; charset=utf-8', '.json': 'application/json', '.woff2': 'font/woff2', '.svg': 'image/svg+xml', '.png': 'image/png' };
const SIGNED_OUT = { state: 'signed-out', name: '', email: '', role: '', apps: [], offline: false, until: '', teacher: '', signing: false, canSignOut: false };

function start(opts) {
  opts = opts || {};
  const token = 'mock-token-' + Math.random().toString(36).slice(2) + Math.random().toString(36).slice(2);
  const clients = new Set();
  const m = {
    token, calls: [],
    access: Object.assign({}, SIGNED_OUT, opts.access || {}),
    station: opts.station || ((code) => (code.toUpperCase() === 'K7QM' ? { ok: true, status: 'ok', message: '' } : { ok: false, status: 'bad-code', message: '' })),
    stationAccess: opts.stationAccess || { state: 'station', name: 'מעבדה 2 · עמדה 14', email: '', role: 'station', apps: ['dsviz', 'automata'], teacher: 'רונית כהן', canSignOut: false },
    send(ev, obj) { const f = 'event: ' + ev + '\ndata: ' + JSON.stringify(obj) + '\n\n'; for (const r of clients) r.write(f); },
    /** מצב חדש (כמו כניסה / התנתקות ב-Eclipse): נשלח לכל הדפים */
    set(a) { m.access = Object.assign({}, SIGNED_OUT, a); m.send('access', m.access); },
    /** סוף הכניסה עם Google: אירוע signin, ואחריו המצב החדש (כמו בתוכנה) */
    finishSignIn(result, access) { m.send('signin', result); if (access) m.set(access); else m.set(Object.assign({}, m.access, { state: 'signed-out', signing: false })); },
    close() { for (const r of clients) r.end(); return new Promise((res) => server.close(res)); },
  };
  const json = (res, code, obj) => { res.writeHead(code, { 'Content-Type': 'application/json; charset=utf-8', 'Cache-Control': 'no-store' }); res.end(JSON.stringify(obj)); };
  const server = http.createServer((req, res) => {
    const url = new URL(req.url, 'http://127.0.0.1');
    if (url.pathname.startsWith('/api/')) {
      if (req.headers['x-dsviz-token'] !== token) return json(res, 401, { error: 'token' });
      let body = '';
      req.on('data', (c) => { body += c; });
      req.on('end', () => {
        let b = {};
        try { b = body ? JSON.parse(body) : {}; } catch (e) { b = {}; }
        const route = req.method + ' ' + url.pathname;
        m.calls.push({ route, body: b });
        switch (route) {
          case 'GET /api/access': return json(res, 200, m.access);
          case 'GET /api/events':
            res.writeHead(200, { 'Content-Type': 'text/event-stream', 'Cache-Control': 'no-store' });
            res.write('event: access\ndata: ' + JSON.stringify(m.access) + '\n\n');
            clients.add(res);
            res.on('close', () => clients.delete(res));
            return undefined;
          case 'POST /api/signin':
            if (opts.signinRefuse) return json(res, 200, opts.signinRefuse);
            m.set(Object.assign({}, m.access, { state: 'pending', signing: true }));
            return json(res, 200, { ok: true, status: 'waiting' });
          case 'POST /api/signin-cancel':
            m.finishSignIn({ ok: false, status: 'cancelled', message: '' }, Object.assign({}, SIGNED_OUT));
            return json(res, 200, { ok: true });
          case 'POST /api/station': {
            const r = m.station(String(b.code || ''), String(b.name || ''));
            if (r.ok) m.set(Object.assign({}, m.stationAccess, { name: String(b.name || '') }));
            return json(res, 200, r);
          }
          case 'POST /api/signout':
            if (m.access.state === 'station') return json(res, 200, { ok: false, status: 'station', message: 'עמדת מחשב לא מתנתקת מכאן — המורה משחרר אותה מעמדת הניהול.' });
            m.set(SIGNED_OUT);
            return json(res, 200, { ok: true, status: 'ok', message: '' });
          case 'POST /api/teacher-panel':
            return json(res, 200, m.access.state === 'teacher' ? { ok: true, status: 'ok' } : { ok: false, status: 'not-teacher', message: 'עמדת הניהול פתוחה רק למורה שמחובר במחשב הזה.' });
          default: return json(res, 404, { error: 'route' });
        }
      });
      return undefined;
    }
    const rel = decodeURIComponent(url.pathname === '/' ? '/index.html' : url.pathname);
    const f = path.join(ROOT, rel);
    if (!f.startsWith(ROOT) || !fs.existsSync(f) || !fs.statSync(f).isFile()) { res.writeHead(404); res.end(); return undefined; }
    res.writeHead(200, { 'Content-Type': TYPES[path.extname(f)] || 'application/octet-stream' });
    fs.createReadStream(f).pipe(res);
    return undefined;
  });
  return new Promise((resolve) => server.listen(0, '127.0.0.1', () => {
    m.port = server.address().port;
    m.url = 'http://127.0.0.1:' + m.port + '/#dsviz-token=' + token;
    resolve(m);
  }));
}

module.exports = { start, SIGNED_OUT };
