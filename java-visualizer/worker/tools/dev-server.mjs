// הרצה מקומית של השרת (בלי Cloudflare) מול אמולטור Firebase — לבדיקות הדפים בדפדפן ושל התוספים.
//   cd test && npx firebase-tools@13.29.1 emulators:exec --config firebase.json --only firestore,auth --project demo-dsviz "node ../tools/dev-server.mjs"
// משתני סביבה: PORT (8787), ADMIN_EMAILS, PASS_KEY_FILE (בלי — נוצר מפתח זמני), DEV_SEED=1 (נתוני דוגמה)
import http from 'node:http';
import { generateKeyPairSync } from 'node:crypto';
import { readFileSync, writeFileSync } from 'node:fs';
import worker from '../src/index.js';

const port = +(process.env.PORT || 8787);
let pem;
if (process.env.PASS_KEY_FILE) {
  try { pem = readFileSync(process.env.PASS_KEY_FILE, 'utf8'); } catch (e) { pem = ''; }
}
if (!pem) {
  const { privateKey, publicKey } = generateKeyPairSync('rsa', { modulusLength: 2048 });
  pem = privateKey.export({ type: 'pkcs8', format: 'pem' });
  if (process.env.PASS_KEY_FILE) writeFileSync(process.env.PASS_KEY_FILE, pem);
  if (process.env.PUBLIC_KEY_FILE) writeFileSync(process.env.PUBLIC_KEY_FILE, publicKey.export({ type: 'spki', format: 'der' }).toString('base64'));
}
const env = {
  ACCESS: 'on', FIREBASE_PROJECT_ID: process.env.FIREBASE_PROJECT_ID || 'demo-dsviz', FIREBASE_API_KEY: 'fake-api-key',
  FIRESTORE_EMULATOR_HOST: process.env.FIRESTORE_EMULATOR_HOST || '127.0.0.1:8181',
  FIREBASE_AUTH_EMULATOR_HOST: process.env.FIREBASE_AUTH_EMULATOR_HOST || '127.0.0.1:9099',
  ADMIN_EMAILS: process.env.ADMIN_EMAILS || 'admin@test.dev', PASS_KEY: pem, PAY_PROVIDER: 'demo',
  GEMINI_API_KEY: process.env.GEMINI_API_KEY || 'dev', MODELS: process.env.MODELS || 'gemini-flash-latest',
};

const server = http.createServer(async (req, res) => {
  const chunks = [];
  for await (const c of req) chunks.push(c);
  const body = chunks.length ? Buffer.concat(chunks) : undefined;
  const request = new Request(`http://${req.headers.host}${req.url}`, { method: req.method, headers: req.headers, body: ['GET', 'HEAD'].includes(req.method) ? undefined : body, duplex: 'half' });
  try {
    const r = await worker.fetch(request, env, { waitUntil() {} });
    const headers = {};
    r.headers.forEach((v, k) => { headers[k] = v; });
    res.writeHead(r.status, headers);
    res.end(Buffer.from(await r.arrayBuffer()));
  } catch (e) {
    res.writeHead(500); res.end(String(e && e.stack || e));
  }
});
server.listen(port, '127.0.0.1', () => console.log(`dev server http://127.0.0.1:${port}`));
if (process.env.DEV_EXIT_AFTER) setTimeout(() => process.exit(0), +process.env.DEV_EXIT_AFTER * 1000);
