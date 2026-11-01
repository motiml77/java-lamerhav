// בדיקות לשרת "שליחת תרגיל" — בלי רשת: fetch ל-Gemini מוחלף בתשובות מדומות.
//   node test/worker-test.mjs
import assert from 'node:assert/strict';
import worker from '../src/index.js';
import { systemPrompt } from '../src/prompt.js';

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
  assert.equal(sent.generationConfig.maxOutputTokens, 65536);   // המחשבה והתשובה חולקות את המכסה — שלא תיחתך באמצע
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

await test('סדר הקבצים: לפני הקבצים — אזהרה שהם לא בהכרח לפי הסדר, כל חלק ממוספר ("[קובץ 2 מתוך 3: ...]"), ותזכורת אחרי החלקים', async () => {
  const r = await call(exerciseRequest({
    files: [{ bytes: PNG, name: 'b.png', type: 'image/png' }, { bytes: PDF, name: 'a.pdf', type: 'application/pdf' }],
    meta: { note: 'סעיף ב בצילום הראשון', names: ['צילום מסך 2', 'דף 1.pdf'], texts: [{ name: 'targil.docx', text: 'שאלה 3: כתבו פעולה...' }] },
  }));
  assert.equal(r.status, 200, JSON.stringify(r.body));
  const parts = JSON.parse(calls[0].init.body).contents[0].parts;
  // הסדר: הודעה, [קובץ 1] + התמונה, [קובץ 2] + ה-PDF, [קובץ 3] הטקסט מ-Word, התזכורת, ההערה של התלמיד
  assert.equal(parts.length, 8);
  const notice = parts[0].text;
  assert.match(notice, /3 חלקים/);
  assert.match(notice, /הקבצים \/ התמונות לא בהכרח צורפו לפי הסדר/);
  assert.doesNotMatch(notice, /העמודים לא בהכרח/);   // בתוך PDF אחד העמודים בדרך כלל לפי הסדר
  assert.match(notice, /בתוך PDF אחד העמודים בדרך כלל לפי הסדר/);
  assert.match(notice, /מספרי החלקים ושמות הקבצים הם רק הסדר שבו התלמיד צירף אותם/);
  assert.match(notice, /קודם כל קבע את סדר הקריאה הנכון לפי מה שכתוב בתוך החלקים/);
  assert.match(notice, /מספרי העמודים/);
  assert.match(notice, /כתוב ב-pageNotes במה מתחיל ובמה נגמר כל חלק, ואת הסדר ב-readingOrder/);
  assert.match(notice, /בלי לדלג על אף חלק — כל חלק נכנס ל-readingOrder/);
  assert.match(notice, /סעיף, מחלקה או דוגמה שמופיעים בשני צילומים חופפים נלקחים פעם אחת/);
  assert.doesNotMatch(notice, /חלק שמופיע פעמיים/);   // "חלק" = קובץ מצורף — אסור לרמוז שמדלגים על קובץ
  assert.match(notice, /כל מחלקה שהתרגיל מתאר או מבקש לכתוב, עם התכונות שלה/);
  assert.equal(parts[1].text, '[קובץ 1 מתוך 3: צילום מסך 2]');
  assert.equal(parts[2].inlineData.mimeType, 'image/png');
  assert.equal(parts[3].text, '[קובץ 2 מתוך 3: דף 1.pdf]');
  assert.equal(parts[4].inlineData.mimeType, 'application/pdf');
  assert.equal(parts[5].text, '[קובץ 3 מתוך 3: טקסט מתוך targil.docx]\nשאלה 3: כתבו פעולה...');
  // אחרי כל החלקים (לפני ההערה) — תזכורת קצרה: קודם הסדר, אחר כך השדות
  assert.match(parts[6].text, /^סוף החלקים \(3\)\. זכור: קודם קבע מהתוכן את סדר הקריאה \(pageNotes ואז readingOrder\)/);
  assert.match(parts[6].text, /ורק אז מלא את שאר השדות/);
  assert.match(parts[7].text, /^הערה מהתלמיד .*סעיף ב בצילום הראשון$/);
  // קובץ אחד — ממוספר, בלי אזהרת סדר ובלי תזכורת (אין מה לסדר)
  calls = [];
  await call(exerciseRequest({ files: [{ bytes: PNG, name: 'a.png', type: 'image/png' }] }));
  const one = JSON.parse(calls[0].init.body).contents[0].parts;
  assert.equal(one.length, 3);
  assert.match(one[0].text, /חלק אחד, מסומן "\[קובץ 1 מתוך 1: שם\]" \(readingOrder = \[1\], pageNotes = ""\)/);
  assert.doesNotMatch(one[0].text, /לא בהכרח צורפו לפי הסדר/);
  assert.match(one[0].text, /כל מחלקה שהתרגיל מתאר או מבקש לכתוב, עם התכונות שלה/);
  assert.equal(one[1].text, '[קובץ 1 מתוך 1: a.png]');
  assert.ok(one[2].inlineData);
});

await test('meta שנשלח כ-Blob (כמו מהאתר) נקרא: שפה, הערה, טקסט מה-Word ושמות הקבצים', async () => {
  const fd = new FormData();
  fd.append('meta', new Blob([JSON.stringify({ language: 'csharp', note: 'רק שאלה 2', names: ['צילום.png'], texts: [{ name: 'q.docx', text: 'שאלה 2' }] })], { type: 'application/json' }));
  fd.append('file', new File([PNG], 'file1.png', { type: 'image/png' }));
  const r = await call(new Request('https://w.example/v1/exercise', { method: 'POST', body: fd }));
  assert.equal(r.status, 200, JSON.stringify(r.body));
  assert.equal(r.body.exercise.language, 'csharp');
  const sent = JSON.parse(calls[0].init.body);
  assert.match(sent.systemInstruction.parts[0].text, /The student works in C#\./);
  const texts = sent.contents[0].parts.filter(p => p.text).map(p => p.text);
  assert.equal(texts[1], '[קובץ 1 מתוך 2: צילום.png]');
  assert.equal(texts[2], '[קובץ 2 מתוך 2: טקסט מתוך q.docx]\nשאלה 2');
  assert.match(texts[3], /^סוף החלקים \(2\)/);
  assert.match(texts[4], /רק שאלה 2$/);
});

await test('meta גדול מדי (Blob או מחרוזת) — נדחה עם הסבר, ולא "נעלם" (אחרת הבקשה הייתה ממשיכה בלי השפה, ההערה והטקסט)', async () => {
  const huge = JSON.stringify({ language: 'csharp', texts: [1, 2, 3, 4, 5].map(i => ({ name: 'טקסט מודבק ' + i, text: 'שאלה '.repeat(30000) })) });
  assert.ok(huge.length > 720000);
  for (const meta of [new Blob([huge], { type: 'application/json' }), huge]) {
    calls = [];
    const fd = new FormData();
    fd.append('meta', meta);
    fd.append('file', new File([PNG], 'file1.png', { type: 'image/png' }));
    const r = await call(new Request('https://w.example/v1/exercise', { method: 'POST', body: fd }));
    assert.equal(r.status, 413, typeof meta + ': ' + JSON.stringify(r.body));
    assert.equal(r.body.code, 'too_big');
    assert.match(r.body.error, /הטקסט ארוך מדי/);
    assert.equal(calls.length, 0);
  }
  // מעט מעל מגבלת הטקסט (60000 תווים) אבל meta קטן מהתקרה — אותה הודעה, מבדיקת הטקסט עצמו
  calls = [];
  const fd = new FormData();
  fd.append('meta', new Blob([JSON.stringify({ texts: [{ name: 'a', text: 'x'.repeat(60001) }] })]));
  const r = await call(new Request('https://w.example/v1/exercise', { method: 'POST', body: fd }));
  assert.equal(r.status, 413);
  assert.match(r.body.error, /הטקסט ארוך מדי/);
});

await test('ההנחיות (Java ו-C#): קודם לקבוע את סדר הקריאה, ומחלקות נוספות תמיד עם התוכן שבתרגיל', async () => {
  for (const [language, bookFields] of [['java', 'private String name; private String author; private int pages;'], ['csharp', 'private string name; private string author; private int pages;']]) {
    calls = [];
    const r = await call(exerciseRequest({ files: [{ bytes: PNG, name: 'a.png', type: 'image/png' }], meta: { language } }));
    assert.equal(r.status, 200);
    const sys = JSON.parse(calls[0].init.body).systemInstruction.parts[0].text;
    // סדר הקבצים — לפני "Return ONLY JSON", כלומר לפני כל השדות
    const step1 = sys.indexOf('STEP 1 — PUT THE PAGES IN ORDER, THEN READ.');
    assert.ok(step1 > 0 && step1 < sys.indexOf('Return ONLY JSON'), language + ': שלב סדר הקריאה לפני השדות');
    assert.match(sys, /\[קובץ 2 מתוך 4: name\]/);
    assert.match(sys, /often NOT attached in the order of the exercise/);
    assert.match(sys, /BEFORE extracting anything/);
    assert.match(sys, /page numbers/);
    assert.match(sys, /sub-questions\s+\(1, 2, 3 \/ א, ב, ג/);
    assert.match(sys, /continues at the top of another/);
    assert.match(sys, /code line numbers\s+that continue/);
    assert.match(sys, /"המשך", "\(המשך\)"/);
    assert.match(sys, /the student's note/);
    assert.match(sys, /"- 3 -" at the top and "\/המשך בעמוד 4\/" at the bottom/);
    // מספרי החלקים ושמות הקבצים ("צילום מסך 2") הם רק סדר הצירוף — לא ראיה לסדר התרגיל
    assert.match(sys, /The part numbers and the file names only show the order in which the student attached the parts/);
    assert.match(sys, /"צילום מסך 1",\s+"תמונה מודבקת 2"/);
    assert.match(sys, /they are NOT evidence of the exercise order/);
    assert.match(sys, /from what is written INSIDE the parts/);
    // תמונות מתוך קובץ Word ("X (1)") שייכות לתוך הטקסט שלו
    assert.match(sys, /Parts named "X \(1\)", "X \(2\)"\.\.\. are pictures from inside the same file X/);
    assert.match(sys, /the part "טקסט מתוך X"\s+is its text/);
    assert.match(sys, /in readingOrder put the text\s+first and then its pictures/);
    assert.match(sys, /Never drop a part while ordering and reading/);
    assert.match(sys, /still order and read everything, but build only what the note asks/);
    assert.match(sys, /overlap or show the same text twice are merged/);
    assert.match(sys, /the same label alone is not a duplicate: 2א is not 1א/);
    assert.match(sys, /Every part is still listed in readingOrder/);
    // pageNotes ("חושב בקול": במה מתחיל ובמה נגמר כל חלק) נכתב לפני readingOrder
    assert.match(sys, /Write in pageNotes what every part starts and ends with, decide the order from that, and write it in readingOrder/);
    assert.match(sys, /^pageNotes — written BEFORE readingOrder/m);
    assert.match(sys, /"<n>: מתחיל ב\.\.\. \/ נגמר ב\.\.\."/);
    assert.match(sys, /^readingOrder — the order decided from pageNotes/m);
    assert.ok(sys.indexOf('\npageNotes — ') < sys.indexOf('\nreadingOrder — '), language + ': pageNotes לפני readingOrder');
    // מחלקות: כל מחלקה שהקוד צריך, עם התכונות מהתרגיל — גם מחלקה שהתלמיד מתבקש לכתוב
    assert.match(sys, /classes — EVERY class that the code needs besides the main class and the library classes/);
    assert.match(sys, /\(a\) classes that the exercise gives or describes/);
    assert.match(sys, /\(b\) classes that the student is asked to write, define or complete \("כתבו את המחלקה Team/);
    assert.match(sys, /\(c\) classes implied by the data/);
    assert.match(sys, /"לכל ספר יש שם, מחבר ומספר עמודים"/);
    assert.ok(sys.includes('public class Book { ' + bookFields), language + ': דוגמת Book בשפה הנכונה');
    assert.match(sys, /HARD RULE: every className in tasks and in givenCode is either mainClass or the name of a class in classes/);
    assert.match(sys, /never leave out a field or property that the exercise states/);
    assert.match(sys, /Do not invent fields that the exercise does not mention/);
    assert.doesNotMatch(sys, /never leave it empty|none is empty/);   // לא לדחוף את המודל להמציא תכונות
    assert.match(sys, /A className other than mainClass must also be defined in classes/);
    // מחלקה של הספרייה אף פעם לא className (ולא מגדירים אותה מחדש בשביל הכלל)
    assert.match(sys, /A library class \(Stack, Queue, Node, BinNode\.\.\.\) is never a className/);
    assert.match(sys, /never a library class — see className/);
    // סעיף שמבקש רק את הכותרת והתכונות — התכונות נכתבות בכל זאת, והסעיף עובר ל-otherQuestions
    assert.match(sys, /Even when a sub-question asks only to write the header and the fields of the class, write the fields here/);
    assert.match(sys, /the fields of the classes are not a task: they are always written/);
    assert.match(sys, /SELF-CHECK before you answer/);
    assert.match(sys, /2\. The HARD RULE of classes holds, and no class in classes is missing a field or property that the exercise states/);
    assert.match(sys, /none is taken twice\s+from overlapping screenshots/);
    assert.ok(sys.indexOf('SELF-CHECK') > sys.indexOf('isExercise / problem'), language + ': הבדיקה העצמית בסוף');
    // הכללים הקיימים נשארו
    assert.match(sys, /must be defined here even if its name is Student or Car/);
    assert.match(sys, language === 'java' ? /NEVER define Stack, Queue, Node, BinNode or BinTreeNode/ : /NEVER define Stack, Queue, Node or BinNode/);
    assert.match(sys, /Do NOT include the methods\/constructors that the student must write/);
  }
});

await test('התבנית: pageNotes ואז readingOrder מיד אחרי isExercise / problem (קודם ההסבר, אחר כך ההחלטה), וחובה', async () => {
  const { RESPONSE_SCHEMA } = await import('../src/prompt.js');
  const order = RESPONSE_SCHEMA.propertyOrdering;
  assert.deepEqual(order.slice(0, 5), ['isExercise', 'problem', 'pageNotes', 'readingOrder', 'title']);
  assert.deepEqual(RESPONSE_SCHEMA.required.slice(0, 5), order.slice(0, 5));
  assert.match(RESPONSE_SCHEMA.properties.pageNotes.description, /written before readingOrder/);
  assert.match(RESPONSE_SCHEMA.properties.readingOrder.description, /decided from pageNotes/);
  assert.ok(RESPONSE_SCHEMA.required.includes('readingOrder') && RESPONSE_SCHEMA.required.includes('pageNotes'));
  assert.equal(RESPONSE_SCHEMA.properties.readingOrder.type, 'ARRAY');
  assert.equal(RESPONSE_SCHEMA.properties.readingOrder.items.type, 'INTEGER');
  assert.equal(RESPONSE_SCHEMA.properties.pageNotes.type, 'STRING');
  assert.deepEqual(Object.keys(RESPONSE_SCHEMA.properties).sort(), [...order].sort());   // כל שדה ב-propertyOrdering
  // גם בבקשה עצמה
  await call(exerciseRequest({ files: [{ bytes: PNG, name: 'a.png', type: 'image/png' }] }));
  assert.deepEqual(JSON.parse(calls[0].init.body).generationConfig.responseSchema.propertyOrdering.slice(2, 4), ['pageNotes', 'readingOrder']);
});

await test('התשובה: readingOrder נשאר (רק מספרים שלמים חיוביים, בלי כפילויות) ו-pageNotes נשאר (עד 1500 תווים); בלי השדות — ריקים', async () => {
  const withOrder = Object.assign(JSON.parse(JSON.stringify(SAMPLE)), { readingOrder: [2, '1', 2, 0, -1, 1.5, 'x', null, 3], pageNotes: '1: מתחיל באמצע סעיף ב / נגמר בסוף השאלה\n' + 'א'.repeat(2000) });
  script = [{ status: 200, json: geminiOk(withOrder) }];
  let r = await call(exerciseRequest({ files: [{ bytes: PNG, name: 'a.png', type: 'image/png' }] }));
  assert.equal(r.status, 200);
  assert.deepEqual(r.body.exercise.readingOrder, [2, 1, 3]);
  assert.ok(r.body.exercise.pageNotes.startsWith('1: מתחיל באמצע סעיף ב / נגמר בסוף השאלה\n'));
  assert.equal(r.body.exercise.pageNotes.length, 1500);   // שורה לכל חלק — עד 10 חלקים
  assert.deepEqual(Object.keys(r.body.exercise).slice(0, 2), ['pageNotes', 'readingOrder']);
  assert.equal(r.body.exercise.tasks[0].methodName, 'countEven');   // שאר התשובה כרגיל
  r = await call(exerciseRequest({ files: [{ bytes: PNG, name: 'a.png', type: 'image/png' }] }));   // SAMPLE — בלי השדות
  assert.deepEqual(r.body.exercise.readingOrder, []);
  assert.equal(r.body.exercise.pageNotes, '');
  script = [{ status: 200, json: geminiOk(Object.assign({}, SAMPLE, { readingOrder: 'not an array', pageNotes: 7 })) }];
  r = await call(exerciseRequest({ files: [{ bytes: PNG, name: 'a.png', type: 'image/png' }] }));
  assert.deepEqual(r.body.exercise.readingOrder, []);
  assert.equal(r.body.exercise.pageNotes, '');
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

await test('התשובה נחתכה (MAX_TOKENS) → נרשם בלוג בנפרד, ועוברים למודל השני', async () => {
  script = [{ status: 200, json: { candidates: [{ finishReason: 'MAX_TOKENS', content: { parts: [{ text: '{"isExercise":true,"tasks":[{"label"' }] } }] } },
            { status: 200, json: geminiOk(SAMPLE) }];
  const logged = [], orig = console.error;
  console.error = (...a) => logged.push(a.join(' '));
  let r;
  try { r = await call(exerciseRequest({ files: [{ bytes: PNG, name: 'a.png', type: 'image/png' }] })); } finally { console.error = orig; }
  assert.equal(r.status, 200);
  assert.equal(r.body.model, 'model-b');
  assert.ok(logged.some(l => /^gemini MAX_TOKENS model-a/.test(l)), logged.join(' | '));
});

await test('מודל שלא מקבל maxOutputTokens=65536 (400) → אותו מודל שוב עם 32768, ולא נכשלים', async () => {
  script = [{ status: 400, json: { error: { code: 400, message: 'The specified maxOutputTokens value is out of range', status: 'INVALID_ARGUMENT' } } },
            { status: 200, json: geminiOk(SAMPLE) }];
  const logged = [], orig = console.error;
  console.error = (...a) => logged.push(a.join(' '));
  let r;
  try { r = await call(exerciseRequest({ files: [{ bytes: PNG, name: 'a.png', type: 'image/png' }] })); } finally { console.error = orig; }
  assert.equal(r.status, 200);
  assert.equal(r.body.model, 'model-a');
  assert.equal(calls.length, 2);
  assert.equal(JSON.parse(calls[0].init.body).generationConfig.maxOutputTokens, 65536);
  assert.equal(JSON.parse(calls[1].init.body).generationConfig.maxOutputTokens, 32768);
  assert.ok(logged.some(l => /maxOutputTokens rejected model-a/.test(l)), logged.join(' | '));
});

await test('הצעת toString פשוטה ובלי מתודות מיותרות — בהנחיות לשתי השפות', async () => {
  for (const l of ['java', 'csharp']) {
    const p = systemPrompt(l);
    assert.ok(p.includes('Setters only for fields that the exercise or the code really changes'), l);
    assert.ok(p.includes('no English words such as'), l);
  }
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
  // מחלקה חסרה — מוסיפים אותה עם התכונות, ולא מוחקים סעיף; סדר הקריאה נשאר כמו שהוא
  assert.match(text, /Keep every class in classes with all its fields/);
  assert.match(text, /ADD it to classes with the fields that the exercise describes/);
  assert.match(text, /never delete a task, or a class that the exercise describes or that a task belongs to, to make the code compile/);
  assert.match(text, /a class that duplicates the main class\s+or a library \/ standard class may be removed/);
  assert.ok(text.includes('(e.g. "cannot find symbol: class Book",'), 'דוגמת שגיאה של Java');
  assert.doesNotMatch(text, /CS0246/);
  assert.match(text, /Keep readingOrder and pageNotes as they are/);
});

await test('תיקון: readingOrder מהתשובה הקודמת חוזר למודל ונשמר בתשובה המתוקנת', async () => {
  const previous = Object.assign(JSON.parse(JSON.stringify(SAMPLE)), { readingOrder: [2, 1], pageNotes: 'צילום 2 קודם' });
  script = [{ status: 200, json: geminiOk(previous) }];
  const req = new Request('https://w.example/v1/repair', {
    method: 'POST', headers: { 'content-type': 'application/json' },
    body: JSON.stringify({ exercise: previous, files: { 'StackTargil.java': 'class X {}' }, errors: ['Book.java:1: cannot find symbol: class Book'] }),
  });
  const r = await call(req);
  assert.equal(r.status, 200);
  assert.match(JSON.parse(calls[0].init.body).contents[0].parts[0].text, /"readingOrder":\[2,1\]/);
  assert.deepEqual(r.body.exercise.readingOrder, [2, 1]);
  assert.equal(r.body.exercise.pageNotes, 'צילום 2 קודם');
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
  assert.match(text, /never delete a task, or a class that the exercise describes or that a task belongs to/);
  assert.match(text, /keep readingOrder and pageNotes as they are/);
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
  // דוגמת "מחלקה חסרה" — בשגיאה של המהדר של C#, לא של Java
  assert.ok(sent.contents[0].parts[0].text.includes(`(e.g. "error CS0246: The type or namespace name 'Book' could not be found",`), 'דוגמת שגיאה של C#');
  assert.doesNotMatch(sent.contents[0].parts[0].text, /cannot find symbol: class Book/);
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
  assert.deepEqual(body.downloads, { windows: 2, zip: 1, mac: 0, total: 8 });
  // האתר מוריד ישירות מ-GitHub ושולח "נספר" ברקע
  const beacon = await worker.fetch(new Request('https://w.example/v1/count/zip', { method: 'POST', body: '' }), env, {});
  assert.equal(beacon.status, 204);
  assert.equal(beacon.headers.get('access-control-allow-origin'), '*');
  assert.equal(STATS.m.get('dl:zip'), '2');
  // Mac: אין קובץ — האתר סופר העתקה של פקודת ההתקנה (curl ... | bash)
  for (let i = 0; i < 3; i++) assert.equal((await worker.fetch(new Request('https://w.example/v1/count/mac', { method: 'POST', body: '' }), env, {})).status, 204);
  const after = await (await worker.fetch(new Request('https://w.example/v1/stats'), env, {})).json();
  assert.deepEqual(after.downloads, { windows: 2, zip: 2, mac: 3, total: 12 });
  const linux = await worker.fetch(new Request('https://w.example/v1/count/linux', { method: 'POST', body: '' }), env, {});
  assert.notEqual(linux.status, 204);
  assert.equal(STATS.m.has('dl:linux'), false);
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
