/**
 * שלב 5 — מיגרציית lamerhav לעץ הרב-בית-ספרי (docs/multi-school-plan.md §7)
 *
 * מעתיק את כל אוספי השורש אל schools/lamerhav/... — העתקה בלבד, שום מחיקה.
 * ההעתקה נעשית ברמת ה-fields הגולמיים של Firestore (ללא המרות) — עותק זהה ביט-ביט.
 * אידמפוטנטי: הרצה חוזרת דורסת את אותם מזהים ביעד, לא מכפילה.
 * מסמך schools/lamerhav (מתג המעבר) נכתב אחרון — רק אחרי שכל הנתונים בעץ.
 *
 * הרצה:  node migrate-lamerhav.js            ← dry-run (ספירה בלבד)
 *        node migrate-lamerhav.js --write    ← העתקה בפועל + אימות
 */
const https = require('https'), fs = require('fs'), os = require('os'), path = require('path');

const PROJECT = 'exams-a93fb';
const BASE = `/v1/projects/${PROJECT}/databases/(default)/documents`;
const SLUG = 'lamerhav';
const WRITE = process.argv.includes('--write');
// כל אוסף שורש שנקרא דרך col() חייב להופיע כאן — אחרת שלב 6 ימחק אותו בלי עותק.
const PLAIN = ['classes', 'questions', 'exam_settings', 'grading_rubrics', 'notifications',
               'users', 'media', 'practice', 'activity'];
const RESP = ['exam_responses', 'homework_responses'];

function getToken() {
  return new Promise((res, rej) => {
    const cfg = JSON.parse(fs.readFileSync(path.join(os.homedir(), '.config', 'configstore', 'firebase-tools.json'), 'utf8'));
    const body = 'grant_type=refresh_token&refresh_token=' + encodeURIComponent(cfg.tokens.refresh_token) +
      '&client_id=563584335869-fgrhgmd47bqnekij5i8b5pr03ho849e6.apps.googleusercontent.com&client_secret=j9iVZfS8kkCEFUPaAeJV0sAi';
    const q = https.request({ hostname: 'oauth2.googleapis.com', path: '/token', method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' } }, r => {
      let d = ''; r.on('data', c => d += c); r.on('end', () => { const t = JSON.parse(d).access_token; t ? res(t) : rej(new Error(d)); });
    });
    q.write(body); q.end();
  });
}

let TOKEN = null;
function api(method, p, body) {
  return new Promise((res, rej) => {
    const q = https.request({ hostname: 'firestore.googleapis.com', path: p, method,
      headers: { Authorization: 'Bearer ' + TOKEN, 'Content-Type': 'application/json' } }, r => {
      let d = ''; r.on('data', c => d += c);
      r.on('end', () => { try { res({ status: r.statusCode, data: JSON.parse(d || '{}') }); } catch (e) { res({ status: r.statusCode, data: d }); } });
    });
    q.on('error', rej);
    if (body) q.write(JSON.stringify(body));
    q.end();
  });
}

// כל מסמכי אוסף (עם עימוד)
async function listAll(collPath) {
  const docs = [];
  let pageToken = '';
  do {
    const r = await api('GET', `${BASE}/${collPath}?pageSize=300${pageToken ? '&pageToken=' + pageToken : ''}`);
    if (r.status !== 200) throw new Error(collPath + ': ' + JSON.stringify(r.data).slice(0, 200));
    (r.data.documents || []).forEach(d => docs.push(d));
    pageToken = r.data.nextPageToken || '';
  } while (pageToken);
  return docs;
}

// כל מסמכי students בשתי היררכיות התשובות — collection group query
async function listStudents() {
  const out = [];
  const body = { structuredQuery: { from: [{ collectionId: 'students', allDescendants: true }], limit: 100000 } };
  const r = await api('POST', `${BASE.replace(/\/documents$/, '')}/documents:runQuery`, body);
  const rows = Array.isArray(r.data) ? r.data : [];
  rows.forEach(row => {
    if (!row.document) return;
    const name = row.document.name; // .../documents/exam_responses/{e}/students/{s}
    const rel = name.split('/documents/')[1];
    if (rel.startsWith('exam_responses/') || rel.startsWith('homework_responses/')) out.push(row.document);
  });
  return out;
}

async function batchWrite(writes) {
  for (let i = 0; i < writes.length; i += 400) {
    const chunk = writes.slice(i, i + 400);
    const r = await api('POST', `${BASE.replace(/\/documents$/, '')}/documents:batchWrite`, { writes: chunk });
    if (r.status !== 200) throw new Error('batchWrite: ' + JSON.stringify(r.data).slice(0, 300));
    const bad = (r.data.status || []).filter(s => s.code && s.code !== 0);
    if (bad.length) throw new Error('batchWrite partial failure: ' + JSON.stringify(bad[0]));
    process.stdout.write(`  ...${Math.min(i + 400, writes.length)}/${writes.length}\r`);
  }
  if (writes.length) process.stdout.write('\n');
}

const FULL = (rel) => `projects/${PROJECT}/databases/(default)/documents/${rel}`;

(async () => {
  TOKEN = await getToken();
  console.log(WRITE ? '=== מיגרציה בפועל ===' : '=== DRY RUN — ספירה בלבד ===');

  // 0) בטיחות: לא רצים אם schools/lamerhav כבר קיים (המתג כבר הופעל)
  const existing = await api('GET', `${BASE}/schools/${SLUG}`);
  if (existing.status === 200) {
    console.log('אזהרה: schools/' + SLUG + ' כבר קיים — הרצה חוזרת תעדכן נתונים אך לא תיצור מחדש את המסמך.');
  }

  const writes = [];
  const counts = {};

  for (const coll of PLAIN) {
    const docs = await listAll(coll).catch(e => { if (String(e).includes('NOT_FOUND')) return []; throw e; });
    counts[coll] = docs.length;
    docs.forEach(d => {
      const id = d.name.split('/').pop();
      writes.push({ update: { name: FULL(`schools/${SLUG}/${coll}/${id}`), fields: d.fields || {} } });
    });
  }

  // מסמכי-אב של תשובות (אם קיימים כמסמכים אמיתיים)
  for (const coll of RESP) {
    const docs = await listAll(coll);
    counts[coll + ' (parents)'] = docs.length;
    docs.forEach(d => {
      const id = d.name.split('/').pop();
      writes.push({ update: { name: FULL(`schools/${SLUG}/${coll}/${id}`), fields: d.fields || {} } });
    });
  }

  // כל תשובות התלמידות
  const students = await listStudents();
  counts['students (responses)'] = students.length;
  students.forEach(d => {
    const rel = d.name.split('/documents/')[1]; // exam_responses/{e}/students/{s}
    writes.push({ update: { name: FULL(`schools/${SLUG}/${rel}`), fields: d.fields || {} } });
  });

  console.log('\nמה יועתק אל schools/' + SLUG + ':');
  Object.entries(counts).forEach(([k, v]) => console.log('  ' + String(v).padStart(5) + '  ' + k));
  console.log('  ' + String(writes.length).padStart(5) + '  סה"כ כתיבות');

  if (!WRITE) { console.log('\n[DRY RUN] לא נכתב דבר. להרצה: node migrate-lamerhav.js --write'); return; }

  console.log('\nמעתיק...');
  await batchWrite(writes);

  // אימות: ספירה בעץ החדש מול המקור
  console.log('אימות ספירות ביעד:');
  let ok = true;
  for (const coll of PLAIN) {
    const got = (await listAll(`schools/${SLUG}/${coll}`)).length;
    const exp = counts[coll];
    console.log(`  ${coll}: ${got}/${exp} ${got === exp ? '✓' : '✗'}`);
    if (got !== exp) ok = false;
  }
  const gotStudents = (await (async () => {
    const body = { structuredQuery: { from: [{ collectionId: 'students', allDescendants: true }], limit: 100000 } };
    const r = await api('POST', `${BASE.replace(/\/documents$/, '')}/documents:runQuery`, body);
    return (Array.isArray(r.data) ? r.data : []).filter(x => x.document && x.document.name.includes(`/schools/${SLUG}/`)).length;
  })());
  console.log(`  students: ${gotStudents}/${counts['students (responses)']} ${gotStudents === counts['students (responses)'] ? '✓' : '✗'}`);
  if (gotStudents !== counts['students (responses)']) ok = false;

  if (!ok) { console.error('\n✗ אי-התאמה בספירות — מסמך המתג לא נכתב. אפשר להריץ שוב בבטחה.'); process.exit(1); }

  // המתג: יצירת מסמך בית הספר — אחרון
  const meta = {
    slug: { stringValue: SLUG },
    name: { stringValue: "אולפנה 'למרחב'" },
    teacherEmail: { stringValue: 'motiml77@gmail.com' },
    teacherName: { stringValue: 'מוטי לוי' },
    status: { stringValue: 'active' },
    createdAt: { stringValue: new Date().toISOString() },
    migratedFromRoot: { booleanValue: true },
    settings: { mapValue: { fields: {
      grades: { arrayValue: { values: [{ stringValue: 'י' }, { stringValue: 'יא' }, { stringValue: 'יב' }] } },
      subtitle: { stringValue: "אולפנה 'למרחב' · שכבות יא–יב" },
    } } },
  };
  const w = await api('PATCH', `${BASE}/schools/${SLUG}`, { fields: meta });
  if (w.status !== 200) { console.error('כתיבת מסמך בית הספר נכשלה:', JSON.stringify(w.data).slice(0, 300)); process.exit(1); }
  console.log('\n✓ schools/' + SLUG + ' נוצר — מתג המעבר פעיל. ה-preview יקרא כעת מהעץ החדש.');
  console.log('  (האתר החי על master ממשיך לקרוא מהשורש — ללא שינוי.)');
})().catch(e => { console.error('שגיאה:', e.message); process.exit(1); });
