// שרת "שליחת תרגיל" מדומה (במקום worker/ + Gemini): מחזיר את התרגיל מקובץ — לבדיקות של VS Code, Visual Studio ו-Eclipse.
//   node mock-worker.js PORT EXERCISE.json [LOG]
//   node mock-worker.js PORT DIR [LOG]  — כמה תרחישים: "scenario:NAME" בהערה (או "_scenario": "NAME" בבקשת תיקון) → DIR/NAME.json;
//                                        NAME=error — השרת מחזיר שגיאה. MOCK_DELAY_MS / MOCK_REPAIR_DELAY_MS — השהיה לפני התשובה
const http = require('http'), fs = require('fs'), path = require('path');
const [port, file, log] = process.argv.slice(2);
const delay = +(process.env.MOCK_DELAY_MS || 0), repairDelay = +(process.env.MOCK_REPAIR_DELAY_MS || 0);
const isDir = fs.statSync(file).isDirectory();
http.createServer((req, res) => {
  const chunks = [];
  req.on('data', d => chunks.push(d));
  req.on('end', () => {
    const body = Buffer.concat(chunks);
    const text = body.toString('utf8');
    if (log) fs.appendFileSync(log, req.method + ' ' + req.url + ' ' + body.length + (/language.{0,4}csharp/.test(body.toString('latin1')) ? ' csharp' : '') + '\n');
    const send = (j, status) => { res.writeHead(status || 200, { 'content-type': 'application/json' }); res.end(JSON.stringify(j)); };
    if (req.url === '/v1/health') return send({ ok: true });
    if (req.url !== '/v1/exercise' && req.url !== '/v1/repair') { res.writeHead(404); return res.end('{}'); }
    let ex;
    if (isDir) {
      const m = /scenario:([A-Za-z0-9_-]+)/.exec(text) || /"_scenario"\s*:\s*"([A-Za-z0-9_-]+)"/.exec(text);
      const name = m ? m[1] : 'default';
      if (name === 'error') return setTimeout(() => send({ ok: false, code: 'busy', error: 'השרת עמוס כרגע. נסו שוב בעוד דקה.' }, 503), delay);
      ex = JSON.parse(fs.readFileSync(path.join(file, name + '.json'), 'utf8'));
    } else ex = JSON.parse(fs.readFileSync(file, 'utf8'));
    setTimeout(() => send({ ok: true, exercise: ex }), req.url === '/v1/repair' ? repairDelay : delay);
  });
}).listen(+port, '127.0.0.1');
