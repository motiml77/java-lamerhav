// בדיקות לשרת "שליחת תרגיל" — בלי רשת: fetch ל-Gemini מוחלף בתשובות מדומות.
//   node test/worker-test.mjs
import assert from 'node:assert/strict';
import worker from '../src/index.js';

const SAMPLE = {
  isExercise: true, problem: '', title: 'ספירת זוגיים במחסנית', summary: 'כתיבת פעולה על מחסנית.',
  topic: 'stacks', mainClass: 'StackTargil', imports: [], classes: [],
  tasks: [{ label: 'א', kind: 'method', className: 'StackTargil', isStatic: true, returnType: 'int', methodName: 'countEven',
            parameters: [{ type: 'Stack<Integer>', name: 's' }], instructions: 'כתבו פעולה שמחזירה את מספר הזוגיים.' }],
  givenCode: [], otherQuestions: [],
  mainCode: 'Stack<Integer> s1 = new Stack<Integer>();\ns1.push(4);\nSystem.out.println("סעיף א: " + countEven(s1));',
};

let calls = [];
let script = [];   // תשובות לפי הסדר: {status, json} או {status, text}
globalThis.fetch = async (url, init) => {
  calls.push({ url, init });
  const r = script.shift() || { status: 200, json: geminiOk(SAMPLE) };
  return new Response(r.text !== undefined ? r.text : JSON.stringify(r.json), { status: r.status });
};
function geminiOk(obj) {
  return { candidates: [{ finishReason: 'STOP', content: { parts: [{ text: 'thinking...', thought: true }, { text: JSON.stringify(obj) }] } }] };
}

const ENV = { GEMINI_API_KEY: 'test-key', MODELS: 'model-a,model-b' };
const PDF = new Uint8Array([0x25, 0x50, 0x44, 0x46, 0x2d, 0x31, 0x2e, 0x34, 0x0a, 1, 2, 3, 4, 5, 6, 7]);
const PNG = new Uint8Array([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a, 0, 0, 0, 13, 9, 9, 9]);

function exerciseRequest({ files = [], meta = {}, headers = {} } = {}) {
  const fd = new FormData();
  fd.append('meta', JSON.stringify(meta));
  for (const f of files) fd.append('file', new File([f.bytes], f.name, { type: f.type }));
  return new Request('https://w.example/v1/exercise', { method: 'POST', body: fd, headers });
}
async function call(req, env = ENV) {
  const res = await worker.fetch(req, env, {});
  return { status: res.status, body: await res.json() };
}
let n = 0;
async function test(name, fn) {
  calls = []; script = [];
  await fn();
  n++;
  console.log('✓', name);
}

await test('health', async () => {
  const r = await call(new Request('https://w.example/v1/health'));
  assert.equal(r.status, 200);
  assert.deepEqual(r.body.models, ['model-a', 'model-b']);
});

await test('תרגיל: PDF + תמונה + טקסט מ-Word + הערה → בקשה תקינה ל-Gemini ותשובה מסודרת', async () => {
  const r = await call(exerciseRequest({
    files: [{ bytes: PDF, name: 'targil.pdf', type: 'application/pdf' }, { bytes: PNG, name: 'צילום 1.png', type: 'image/png' }],
    meta: { client: 'test', note: 'רק סעיף א', texts: [{ name: 'targil.docx', text: 'כתבו פעולה...' }] },
  }));
  assert.equal(r.status, 200, JSON.stringify(r.body));
  assert.equal(r.body.ok, true);
  assert.equal(r.body.model, 'model-a');
  assert.equal(r.body.exercise.tasks[0].methodName, 'countEven');
  assert.equal(calls.length, 1);
  assert.match(calls[0].url, /\/v1beta\/models\/model-a:generateContent$/);
  assert.equal(calls[0].init.headers['x-goog-api-key'], 'test-key');
  const sent = JSON.parse(calls[0].init.body);   // הגוף שנבנה בשרשור — חייב להיות JSON תקין
  assert.match(sent.systemInstruction.parts[0].text, /The skeleton itself never contains a solution/);
  assert.match(sent.systemInstruction.parts[0].text, /The student works in Java\./);   // ברירת מחדל: Java
  assert.match(sent.systemInstruction.parts[0].text, /void push\(T x\)/);
  assert.equal(r.body.exercise.language, 'java');
  assert.equal(r.body.exercise.topic, 'stacks');
  assert.equal(sent.generationConfig.responseMimeType, 'application/json');
  assert.ok(sent.generationConfig.responseSchema.properties.tasks);
  const parts = sent.contents[0].parts;
  const inline = parts.filter(p => p.inlineData);
  assert.equal(inline.length, 2);
  assert.equal(inline[0].inlineData.mimeType, 'application/pdf');
  assert.deepEqual(new Uint8Array(Buffer.from(inline[0].inlineData.data, 'base64')), PDF);
  assert.deepEqual(new Uint8Array(Buffer.from(inline[1].inlineData.data, 'base64')), PNG);
  assert.ok(parts.some(p => p.text && p.text.includes('כתבו פעולה...')));
  assert.ok(parts.some(p => p.text && p.text.includes('רק סעיף א')));
  assert.ok(parts.some(p => p.text && p.text.includes('צילום 1.png')));
});

await test('קובץ שמתחזה ל-PDF נדחה', async () => {
  const r = await call(exerciseRequest({ files: [{ bytes: new Uint8Array([0x4d, 0x5a, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11]), name: 'x.pdf', type: 'application/pdf' }] }));
  assert.equal(r.status, 415);
  assert.equal(calls.length, 0);
});

await test('סוג קובץ לא מותר נדחה', async () => {
  const r = await call(exerciseRequest({ files: [{ bytes: PDF, name: 'x.exe', type: 'application/octet-stream' }] }));
  assert.equal(r.status, 415);
});

await test('יותר מדי קבצים', async () => {
  const files = Array.from({ length: 11 }, (_, i) => ({ bytes: PNG, name: i + '.png', type: 'image/png' }));
  const r = await call(exerciseRequest({ files }));
  assert.equal(r.status, 413);
});

await test('בלי קבצים', async () => {
  const r = await call(exerciseRequest({}));
  assert.equal(r.status, 400);
});

await test('מפתח אפליקציה: בלי המפתח — נחסם, עם המפתח — עובר', async () => {
  const env = Object.assign({}, ENV, { APP_KEY: 'k1' });
  let r = await call(exerciseRequest({ files: [{ bytes: PNG, name: 'a.png', type: 'image/png' }] }), env);
  assert.equal(r.status, 401);
  r = await call(exerciseRequest({ files: [{ bytes: PNG, name: 'a.png', type: 'image/png' }], headers: { 'x-dsviz-key': 'k1' } }), env);
  assert.equal(r.status, 200);
});

await test('השרת בלי מפתח Gemini', async () => {
  const r = await call(exerciseRequest({ files: [{ bytes: PNG, name: 'a.png', type: 'image/png' }] }), { MODELS: 'x' });
  assert.equal(r.status, 503);
});

await test('מכסה של המודל הראשון נגמרה → עוברים למודל השני', async () => {
  script = [{ status: 429, json: { error: { code: 429 } } }, { status: 200, json: geminiOk(SAMPLE) }];
  const r = await call(exerciseRequest({ files: [{ bytes: PNG, name: 'a.png', type: 'image/png' }] }));
  assert.equal(r.status, 200);
  assert.equal(r.body.model, 'model-b');
  assert.equal(calls.length, 2);
});

await test('כל המודלים במכסה → הודעה ברורה', async () => {
  script = [{ status: 429, text: '{}' }, { status: 503, text: '{}' }];
  const r = await call(exerciseRequest({ files: [{ bytes: PNG, name: 'a.png', type: 'image/png' }] }));
  assert.equal(r.status, 429);
  assert.match(r.body.error, /מכסה/);
});

await test('JSON שבור מהמודל הראשון → המודל השני', async () => {
  script = [{ status: 200, json: { candidates: [{ content: { parts: [{ text: '{not json' }] } }] } }, { status: 200, json: geminiOk(SAMPLE) }];
  const r = await call(exerciseRequest({ files: [{ bytes: PNG, name: 'a.png', type: 'image/png' }] }));
  assert.equal(r.status, 200);
  assert.equal(r.body.model, 'model-b');
});

await test('לא תרגיל → הסבר בעברית', async () => {
  script = [{ status: 200, json: geminiOk({ isExercise: false, problem: 'זו תמונה של חתול, לא תרגיל.' }) }];
  const r = await call(exerciseRequest({ files: [{ bytes: PNG, name: 'a.png', type: 'image/png' }] }));
  assert.equal(r.status, 422);
  assert.equal(r.body.error, 'זו תמונה של חתול, לא תרגיל.');
});

await test('Gemini דוחה את הקובץ (400) → הודעה, בלי לנסות שוב', async () => {
  script = [{ status: 400, text: '{"error":{"message":"bad"}}' }];
  const r = await call(exerciseRequest({ files: [{ bytes: PNG, name: 'a.png', type: 'image/png' }] }));
  assert.equal(r.status, 502);
  assert.equal(calls.length, 1);
});

await test('תיקון: הקוד והשגיאות נשלחים, והתשובה חוזרת', async () => {
  const req = new Request('https://w.example/v1/repair', {
    method: 'POST', headers: { 'content-type': 'application/json' },
    body: JSON.stringify({ exercise: SAMPLE, files: { 'StackTargil.java': 'class X {}' }, errors: ['StackTargil.java:12: cannot find symbol: size()'] }),
  });
  const r = await call(req);
  assert.equal(r.status, 200);
  const sent = JSON.parse(calls[0].init.body);
  const text = sent.contents[0].parts[0].text;
  assert.match(text, /did not compile/);
  assert.match(text, /cannot find symbol: size\(\)/);
  assert.match(text, /class X \{\}/);
});

await test('הצעה לפתרון: ההנחיות מבקשות פתרון פשוט, והפתרון חוזר בתשובה (גם בתוך ```java)', async () => {
  const sol = 'public static int countEven(Stack<Integer> s) {\n    int count = 0;\n    return count;\n}';
  const withSol = JSON.parse(JSON.stringify(SAMPLE));
  withSol.tasks[0].solution = '```java\n' + sol + '\n```';
  script = [{ status: 200, json: geminiOk(withSol) }];
  const r = await call(exerciseRequest({ files: [{ bytes: PNG, name: 'a.png', type: 'image/png' }] }));
  assert.equal(r.status, 200);
  assert.equal(r.body.exercise.tasks[0].solution, sol);
  const sent = JSON.parse(calls[0].init.body);
  const sys = sent.systemInstruction.parts[0].text;
  assert.match(sys, /tasks\[\]\.solution/);
  assert.match(sys, /simple and intuitive/);
  assert.match(sys, /high-school/);
  assert.match(sys, /No streams, lambdas/);
  assert.ok(sent.generationConfig.responseSchema.properties.tasks.items.required.includes('solution'));
});

await test('תיקון ההצעה לפתרון (mode=solution): ההנחיות של בדיקת הפתרון, והפלט בפועל מול הצפוי', async () => {
  const req = new Request('https://w.example/v1/repair', {
    method: 'POST', headers: { 'content-type': 'application/json' },
    body: JSON.stringify({ mode: 'solution', exercise: SAMPLE, files: { 'StackTargil.java': 'class X {}' },
                           errors: ['Expected output:\nסעיף א: 1', 'Actual output:\nסעיף א: 0'] }),
  });
  const r = await call(req);
  assert.equal(r.status, 200);
  const text = JSON.parse(calls[0].init.body).contents[0].parts[0].text;
  assert.match(text, /checked your suggested solutions/);
  assert.match(text, /What happened:/);
  assert.match(text, /Actual output:/);
  assert.doesNotMatch(text, /did not compile\./);
});

await test('C#: ההנחיות ב-C# (ממשק הספרייה ב-PascalCase), והשפה חוזרת בתשובה', async () => {
  const r = await call(exerciseRequest({ files: [{ bytes: PNG, name: 'a.png', type: 'image/png' }], meta: { language: 'csharp' } }));
  assert.equal(r.status, 200);
  const sys = JSON.parse(calls[0].init.body).systemInstruction.parts[0].text;
  assert.match(sys, /The student works in C#\./);
  assert.match(sys, /void Push\(T x\)/);
  assert.match(sys, /Never write "using System\.Collections\.Generic;"/);
  assert.doesNotMatch(sys, /void push\(T x\)/);
  assert.equal(r.body.exercise.language, 'csharp');
});

await test('תיקון ב-C#: השפה עוברת גם לבקשת התיקון', async () => {
  const req = new Request('https://w.example/v1/repair', {
    method: 'POST', headers: { 'content-type': 'application/json' },
    body: JSON.stringify({ language: 'csharp', exercise: SAMPLE, files: { 'StackTargil.cs': 'class X {}' }, errors: ['StackTargil.cs(3,5): error CS0103'] }),
  });
  const r = await call(req);
  assert.equal(r.status, 200);
  const sent = JSON.parse(calls[0].init.body);
  assert.match(sent.contents[0].parts[0].text, /The C# skeleton/);
  assert.match(sent.systemInstruction.parts[0].text, /The student works in C#/);
});

await test('הגבלת קצב (LIMITER)', async () => {
  const env = Object.assign({}, ENV, { LIMITER: { limit: async () => ({ success: false }) } });
  const r = await call(exerciseRequest({ files: [{ bytes: PNG, name: 'a.png', type: 'image/png' }] }), env);
  assert.equal(r.status, 429);
  assert.equal(calls.length, 0);
});

await test('מכסה יומית (KV)', async () => {
  const store = new Map();
  const env = Object.assign({}, ENV, { DAILY_LIMIT: '2', USAGE: { get: async k => store.get(k) || null, put: async (k, v) => store.set(k, v) } });
  const req = () => exerciseRequest({ files: [{ bytes: PNG, name: 'a.png', type: 'image/png' }] });
  assert.equal((await call(req(), env)).status, 200);
  assert.equal((await call(req(), env)).status, 200);
  assert.equal((await call(req(), env)).status, 429);
});

await test('קובץ גדול (8MB) — הבקשה נבנית בלי JSON.stringify על הקובץ', async () => {
  const big = new Uint8Array(8 * 1024 * 1024);
  big.set(PDF);
  const t0 = performance.now();
  const r = await call(exerciseRequest({ files: [{ bytes: big, name: 'big.pdf', type: 'application/pdf' }] }));
  const ms = performance.now() - t0;
  assert.equal(r.status, 200);
  const sent = JSON.parse(calls[0].init.body);
  assert.equal(Buffer.from(sent.contents[0].parts.find(p => p.inlineData).inlineData.data, 'base64').length, big.length);
  console.log(`   (8MB: ${ms.toFixed(0)}ms בסך הכול, כולל בניית FormData בבדיקה)`);
});

// ------------------------------------------------------------------ נעילה לתשלום
const { licenseSignature } = await import('../src/index.js');
function licenseRequest(body) {
  return new Request('https://w.example/v1/license', { method: 'POST', body: JSON.stringify(body), headers: { 'content-type': 'application/json' } });
}
await test('רישיון: בלי PAYWALL — פתוח לכולם (גם בלי קוד, וגם בלי מפתח-אפליקציה)', async () => {
  const r = await call(licenseRequest({ machine: 'm1' }), { ...ENV, APP_KEY: 'secret-app' });
  assert.equal(r.status, 200);
  assert.equal(r.body.locked, false);
});
await test('רישיון: PAYWALL=on — נעול בלי קוד / עם קוד שגוי / מבוטל, ופתוח עם קוד תקין', async () => {
  const env = { ...ENV, PAYWALL: 'on', LICENSE_SECRET: 's3cret', PAY_URL: 'https://pay.example', REVOKED: 'bad@x.com' };
  let r = await call(licenseRequest({ machine: 'm1' }), env);
  assert.equal(r.body.locked, true);
  assert.equal(r.body.payUrl, 'https://pay.example');
  assert.ok(r.body.message.length > 10);
  r = await call(licenseRequest({ code: 'DSV-dana@x.com-0000000000000000' }), env);
  assert.equal(r.body.locked, true);
  const good = 'DSV-dana@x.com-' + await licenseSignature('s3cret', 'dana@x.com');
  r = await call(licenseRequest({ code: good }), env);
  assert.equal(r.body.locked, false);
  assert.equal(r.body.licensed, true);
  r = await call(licenseRequest({ code: 'DSV-bad@x.com-' + await licenseSignature('s3cret', 'bad@x.com') }), env);
  assert.equal(r.body.locked, true);
});


// ------------------------------------------------------------------ מונה הורדות והאתר
function memKV() {
  const m = new Map();
  return { get: async k => (m.has(k) ? m.get(k) : null), put: async (k, v) => { m.set(k, v); }, m };
}
await test('הורדה דרך /dl/windows: נספרת ומפנה לקובץ; /v1/stats מחזיר את המספר (ופתוח לכל אתר)', async () => {
  const STATS = memKV(), env = { ...ENV, STATS, DOWNLOADS_BEFORE: '5' };
  for (const kind of ['windows', 'windows', 'zip']) {
    const res = await worker.fetch(new Request('https://w.example/dl/' + kind), env, {});
    assert.equal(res.status, 302);
    assert.match(res.headers.get('location'), kind === 'zip' ? /dsviz-install\.zip$/ : /DSViz-Setup\.exe$/);
  }
  const res = await worker.fetch(new Request('https://w.example/v1/stats', { headers: { origin: 'https://example.org' } }), env, {});
  assert.equal(res.headers.get('access-control-allow-origin'), '*');
  const body = await res.json();
  assert.deepEqual(body.downloads, { windows: 2, zip: 1, total: 8 });
  // האתר מוריד ישירות מ-GitHub ושולח "נספר" ברקע
  const beacon = await worker.fetch(new Request('https://w.example/v1/count/zip', { method: 'POST', body: '' }), env, {});
  assert.equal(beacon.status, 204);
  assert.equal(beacon.headers.get('access-control-allow-origin'), '*');
  assert.equal(STATS.m.get('dl:zip'), '2');
  const none = await call(new Request('https://w.example/v1/stats'), ENV);
  assert.equal(none.body.counting, false);
  const other = await worker.fetch(new Request('https://w.example/dl/linux'), ENV, {});
  assert.equal(other.status, 405);
});
await test('האתר (/v1/site/exercise): רק מהכתובת של האתר, בלי מפתח-אפליקציה, עם CORS, ומכסה יומית משלו', async () => {
  const env = { ...ENV, APP_KEY: 'secret-app', USAGE: memKV(), SITE_DAILY_LIMIT: '2' };
  const site = (origin) => {
    const fd = new FormData();
    fd.append('meta', JSON.stringify({ client: 'dsviz-site', language: 'java' }));
    fd.append('file', new File([PDF], 'q.pdf', { type: 'application/pdf' }));
    return new Request('https://w.example/v1/site/exercise', { method: 'POST', body: fd, headers: origin ? { origin } : {} });
  };
  let res = await worker.fetch(site('https://evil.example'), env, {});
  assert.equal(res.status, 403);
  assert.equal(res.headers.get('access-control-allow-origin'), null);
  res = await worker.fetch(site(null), env, {});
  assert.equal(res.status, 403);
  const pre = await worker.fetch(new Request('https://w.example/v1/site/exercise', { method: 'OPTIONS', headers: { origin: 'https://motiml77.github.io' } }), env, {});
  assert.equal(pre.status, 204);
  assert.equal(pre.headers.get('access-control-allow-origin'), 'https://motiml77.github.io');
  for (let i = 0; i < 2; i++) {
    script = [{ status: 200, json: geminiOk(SAMPLE) }];
    res = await worker.fetch(site('https://motiml77.github.io'), env, {});
    assert.equal(res.status, 200);
    assert.equal(res.headers.get('access-control-allow-origin'), 'https://motiml77.github.io');
    assert.equal((await res.json()).exercise.mainClass, 'StackTargil');
  }
  res = await worker.fetch(site('https://motiml77.github.io'), env, {});
  assert.equal(res.status, 429);
  assert.equal(res.headers.get('access-control-allow-origin'), 'https://motiml77.github.io');   // גם השגיאה נקראת מהדפדפן
  // התוסף (/v1/exercise) עדיין דורש את המפתח
  const r = await call(exerciseRequest({ files: [{ bytes: PDF, name: 'a.pdf', type: 'application/pdf' }] }), env);
  assert.equal(r.status, 401);
});

console.log(`\n${n} בדיקות עברו`);
