/**
 * בידוד בין בתי ספר — גם החסימות וגם הגישה הלגיטימית.
 *
 * הרקע: isActiveUser היה "מחוברת + בית הספר פעיל", בלי לבדוק שייכות. לכן כל
 * מחוברת יכלה לקרוא נושאים, שאלות והגדרות מבחן של *כל* בית ספר פעיל.
 * התיקון הוסיף isMember() שבודק קיום מסמך תלמידה. הבדיקה כאן מוודאת את שני
 * הכיוונים — שהזרים חסומים, ושהתלמידות והמורה של בית הספר עצמו לא נחסמו.
 *
 * הרצה: node scripts/rules-isolation-test.js
 */
const https = require('https'), fs = require('fs'), os = require('os'), path = require('path');
const RULES = fs.readFileSync(path.join(__dirname, '..', 'firestore.rules'), 'utf8');
const DB = '/databases/(default)';

function token(cb) {
  const cfg = JSON.parse(fs.readFileSync(path.join(os.homedir(), '.config', 'configstore', 'firebase-tools.json'), 'utf8'));
  const b = 'grant_type=refresh_token&refresh_token=' + encodeURIComponent(cfg.tokens.refresh_token) +
    '&client_id=563584335869-fgrhgmd47bqnekij5i8b5pr03ho849e6.apps.googleusercontent.com&client_secret=j9iVZfS8kkCEFUPaAeJV0sAi';
  const r = https.request({ hostname: 'oauth2.googleapis.com', path: '/token', method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' } }, s => {
    let d = ''; s.on('data', c => d += c); s.on('end', () => cb(JSON.parse(d).access_token));
  });
  r.write(b); r.end();
}

const auth = e => ({ uid: 'u_' + e.split('@')[0], token: { email: e, email_verified: true, firebase: { sign_in_provider: 'google.com' } } });
// get מחזיר את מסמך בית הספר; exists עונה על isMember.
// מגבלת ה-Rules Test API: functionMocks מותאם לפי שם הפונקציה בלבד, לא לפי
// הנתיב — ולכן שני get() שונים (מסמך בית הספר, מסמך החברות לבדיקת approved)
// מקבלים את אותה תשובה. approved:true תמיד באובייקט הממוזג; זה לא פותח דלת
// לתלמידה זרה כי exists() המדומה (member) הוא מה שקובע — כשהוא false, ה-&&
// הרשמי ב-isMember עוצר לפני שהוא בכלל מגיע ל-get() השני.
// הסמכות לגבי get() מקונן היא scripts/e2e-isolation-check.js מול השרת האמיתי.
const mocks = (member, teacher, status) => ([
  { function: 'get', args: [{ anyValue: {} }], result: { value: { data: { slug: 'schoolB', teacherEmail: teacher, status: status || 'active', approved: true } } } },
  { function: 'exists', args: [{ anyValue: {} }], result: { value: !!member } },
]);

const T = 't@b.com', INSIDE = 'inside@b.com', OUTSIDER = 'student@school-a.com', BOSS = 'motiml77@gmail.com';
const doc = (c) => DB + '/documents/schools/schoolB/' + c;

const CASES = [
  // ---- חסימות: מי שאינו שייך ----
  { name: 'זרה קוראת נושא', expectation: 'DENY',
    request: { auth: auth(OUTSIDER), path: doc('classes/c1'), method: 'get' }, functionMocks: mocks(false, T) },
  { name: 'זרה קוראת שאלת מבחן', expectation: 'DENY',
    request: { auth: auth(OUTSIDER), path: doc('questions/q1'), method: 'get' }, functionMocks: mocks(false, T) },
  { name: 'זרה קוראת הגדרות מבחן', expectation: 'DENY',
    request: { auth: auth(OUTSIDER), path: doc('exam_settings/e1'), method: 'get' }, functionMocks: mocks(false, T) },
  { name: 'זרה קוראת מדיה', expectation: 'DENY',
    request: { auth: auth(OUTSIDER), path: doc('media/m1'), method: 'get' }, functionMocks: mocks(false, T) },
  { name: 'זרה קוראת שאלת תרגול', expectation: 'DENY',
    request: { auth: auth(OUTSIDER), path: doc('practice/p1'), method: 'get' }, functionMocks: mocks(false, T) },
  { name: 'זרה קוראת הודעות', expectation: 'DENY',
    request: { auth: auth(OUTSIDER), path: doc('notifications/n1'), method: 'get' }, functionMocks: mocks(false, T) },
  { name: 'זרה מציגה רשימת תלמידות', expectation: 'DENY',
    request: { auth: auth(OUTSIDER), path: doc('users/u1'), method: 'list' }, functionMocks: mocks(false, T) },

  // ---- גישה לגיטימית: אסור שנשבור אותה ----
  { name: 'תלמידה של בית הספר קוראת נושא', expectation: 'ALLOW',
    request: { auth: auth(INSIDE), path: doc('classes/c1'), method: 'get' }, functionMocks: mocks(true, T) },
  { name: 'תלמידה של בית הספר קוראת שאלת מבחן', expectation: 'ALLOW',
    request: { auth: auth(INSIDE), path: doc('questions/q1'), method: 'get' }, functionMocks: mocks(true, T) },
  { name: 'תלמידה של בית הספר קוראת הגדרות מבחן', expectation: 'ALLOW',
    request: { auth: auth(INSIDE), path: doc('exam_settings/e1'), method: 'get' }, functionMocks: mocks(true, T) },
  { name: 'תלמידה של בית הספר קוראת מדיה', expectation: 'ALLOW',
    request: { auth: auth(INSIDE), path: doc('media/m1'), method: 'get' }, functionMocks: mocks(true, T) },
  { name: 'תלמידה של בית הספר קוראת תרגול', expectation: 'ALLOW',
    request: { auth: auth(INSIDE), path: doc('practice/p1'), method: 'get' }, functionMocks: mocks(true, T) },
  { name: 'המורה קוראת נושא גם בלי מסמך תלמידה', expectation: 'ALLOW',
    request: { auth: auth(T), path: doc('classes/c1'), method: 'get' }, functionMocks: mocks(false, T) },
  { name: 'המורה עורכת נושא', expectation: 'ALLOW',
    request: { auth: auth(T), path: doc('classes/c1'), method: 'update', resource: { data: { title: 'לולאות' } } }, functionMocks: mocks(false, T) },
  { name: 'המנהל הראשי קורא נושא', expectation: 'ALLOW',
    request: { auth: auth(BOSS), path: doc('classes/c1'), method: 'get' }, functionMocks: mocks(false, T) },

  // ---- הרשמה: משתמשת חדשה בלי מסמך עדיין ----
  { name: 'משתמשת חדשה יוצרת את מסמך ההרשמה שלה', expectation: 'ALLOW',
    request: { auth: auth('new@b.com'), path: doc('users/new_b_com'), method: 'create',
      resource: { data: { email: 'new@b.com', name: 'חדשה', approved: false } } }, functionMocks: mocks(false, T) },
  { name: 'משתמשת חדשה עדיין לא קוראת נושאים', expectation: 'DENY',
    request: { auth: auth('new@b.com'), path: doc('classes/c1'), method: 'get' }, functionMocks: mocks(false, T) },

  // ---- בית ספר מושהה ----
  { name: 'תלמידה של בי"ס מושהה לא קוראת', expectation: 'DENY',
    request: { auth: auth(INSIDE), path: doc('classes/c1'), method: 'get' }, functionMocks: mocks(true, T, 'suspended') },
];

function run(tok, attempt) {
  const body = JSON.stringify({ source: { files: [{ name: 'firestore.rules', content: RULES }] },
    testSuite: { testCases: CASES.map(c => ({ expectation: c.expectation, request: c.request, functionMocks: c.functionMocks || [] })) } });
  const r = https.request({ hostname: 'firebaserules.googleapis.com', path: '/v1/projects/exams-a93fb:test', method: 'POST',
    headers: { Authorization: 'Bearer ' + tok, 'Content-Type': 'application/json' } }, res => {
    let d = ''; res.on('data', c => d += c); res.on('end', () => {
      const j = JSON.parse(d);
      if (j.issues && j.issues.length) console.log('COMPILE:', JSON.stringify(j.issues).slice(0, 400));
      if (!j.testResults) { console.log('RAW:', d.slice(0, 400)); return; }
      let pass = 0;
      j.testResults.forEach((x, i) => {
        const ok = x.state === 'SUCCESS'; if (ok) pass++;
        console.log((ok ? 'PASS ' : 'FAIL ') + CASES[i].expectation.padEnd(5) + ' ' + CASES[i].name +
          (ok ? '' : ' — ' + JSON.stringify(x).slice(0, 160)));
      });
      console.log('===== ' + pass + '/' + CASES.length + ' =====');
    });
  });
  r.on('error', e => { if (attempt < 3) setTimeout(() => run(tok, attempt + 1), 1500); else console.log('NET FAIL'); });
  r.write(body); r.end();
}
token(tok => run(tok, 1));
