// בדיקות לשרת "שליחת תרגיל" — בלי רשת: fetch ל-Gemini מוחלף בתשובות מדומות.
//   node test/worker-test.mjs
import assert from 'node:assert/strict';
import worker from '../src/index.js';

const SAMPLE = {
  isExercise: true, problem: '', title: 'ספירת זוגיים במחסנית', summary: 'כתיבת פעולה על מחסנית.',
  packageName: 'stack_targil', mainClass: 'StackTargil', imports: [], classes: [],
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
  assert.match(sent.systemInstruction.parts[0].text, /NEVER write the solution/);
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

console.log(`\n${n} בדיקות עברו`);
