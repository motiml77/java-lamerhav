// שרת "שליחת תרגיל" מדומה (במקום worker/ + Gemini): מחזיר את התרגיל מקובץ — לבדיקות של VS Code ושל Visual Studio.
//   node mock-worker.js PORT EXERCISE.json [LOG]
const http = require('http'), fs = require('fs');
const [port, file, log] = process.argv.slice(2);
http.createServer((req, res) => {
  const chunks = [];
  req.on('data', d => chunks.push(d));
  req.on('end', () => {
    const body = Buffer.concat(chunks);
    if (log) fs.appendFileSync(log, req.method + ' ' + req.url + ' ' + body.length + (/language.{0,4}csharp/.test(body.toString('latin1')) ? ' csharp' : '') + '\n');
    const send = j => { res.writeHead(200, { 'content-type': 'application/json' }); res.end(JSON.stringify(j)); };
    if (req.url === '/v1/health') return send({ ok: true });
    const ex = JSON.parse(fs.readFileSync(file, 'utf8'));
    if (req.url === '/v1/exercise' || req.url === '/v1/repair') return send({ ok: true, exercise: ex });
    res.writeHead(404); res.end('{}');
  });
}).listen(+port, '127.0.0.1');
