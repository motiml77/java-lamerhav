// בדיקת חוקי v2 עם Firebase Rules Test API — סימולציה מלאה, בלי לגעת בנתונים אמיתיים
const https = require('https'), fs = require('fs'), os = require('os'), path = require('path');

const RULES = fs.readFileSync('C:/Users/Moti Levi/Desktop/AI/java Bagrut/firestore.rules', 'utf8');
const P = 'projects/exams-a93fb';
const DB = '/databases/(default)';

function token(cb) {
  const cfg = JSON.parse(fs.readFileSync(path.join(os.homedir(), '.config', 'configstore', 'firebase-tools.json'), 'utf8'));
  const body = 'grant_type=refresh_token&refresh_token=' + encodeURIComponent(cfg.tokens.refresh_token) +
    '&client_id=563584335869-fgrhgmd47bqnekij5i8b5pr03ho849e6.apps.googleusercontent.com&client_secret=j9iVZfS8kkCEFUPaAeJV0sAi';
  const req = https.request({ hostname: 'oauth2.googleapis.com', path: '/token', method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' } }, res => {
    let d = ''; res.on('data', c => d += c); res.on('end', () => cb(JSON.parse(d).access_token));
  });
  req.write(body); req.end();
}

const auth = (email) => ({ uid: 'u_' + email.split('@')[0], token: { email, email_verified: true, firebase: { sign_in_provider: 'google.com' } } });
const schoolMock = (status, teacher) => [{
  function: 'get', args: [{ anyValue: {} }],
  result: { value: { data: { slug: 'tzvia', name: 'צביה', teacherEmail: teacher, status } } },
}];

const CASES = [
  // 1) מורה של בית ספר פעיל כותבת נושא — מותר
  { name: 'מורה בי"ס פעיל כותבת נושא → ALLOW', expectation: 'ALLOW',
    request: { auth: auth('teacher@x.com'), path: DB + '/documents/schools/tzvia/classes/c1', method: 'create',
      resource: { data: { title: 'לולאות' } } },
    functionMocks: schoolMock('active', 'teacher@x.com') },
  // 2) אותו מורה בבי"ס מושהה — נחסם (השהיה נאכפת בשרת)
  { name: 'מורה בבי"ס מושהה כותבת נושא → DENY', expectation: 'DENY',
    request: { auth: auth('teacher@x.com'), path: DB + '/documents/schools/tzvia/classes/c1', method: 'create',
      resource: { data: { title: 'לולאות' } } },
    functionMocks: schoolMock('suspended', 'teacher@x.com') },
  // 3) תלמידה מבי"ס מושהה קוראת נושא — נחסמת
  { name: 'תלמידה בבי"ס מושהה קוראת → DENY', expectation: 'DENY',
    request: { auth: auth('student@x.com'), path: DB + '/documents/schools/tzvia/classes/c1', method: 'get' },
    functionMocks: schoolMock('suspended', 'teacher@x.com') },
  // 4) מורה של בי"ס אחר מנסה לקרוא מחוון — נחסם (בידוד בין בתי ספר)
  { name: 'מורה זר קורא מחוון של בי"ס אחר → DENY', expectation: 'DENY',
    request: { auth: auth('other-teacher@y.com'), path: DB + '/documents/schools/tzvia/grading_rubrics/e1', method: 'get' },
    functionMocks: schoolMock('active', 'teacher@x.com') },
  // 5) המנהל הראשי קורא מחוון בכל בי"ס — מותר
  { name: 'מנהל ראשי קורא מחוון בכל בי"ס → ALLOW', expectation: 'ALLOW',
    request: { auth: auth('motiml77@gmail.com'), path: DB + '/documents/schools/tzvia/grading_rubrics/e1', method: 'get' },
    functionMocks: schoolMock('active', 'teacher@x.com') },
  // 6) תלמידה מנסה להירשם כמאושרת — נחסמת
  { name: 'הרשמה עם approved:true → DENY', expectation: 'DENY',
    request: { auth: auth('student@x.com'), path: DB + '/documents/schools/tzvia/users/student_x_com', method: 'create',
      resource: { data: { email: 'student@x.com', name: 'א', approved: true } } },
    functionMocks: schoolMock('active', 'teacher@x.com') },
  // 7) הרשמה תקינה (לא מאושרת) — מותרת
  { name: 'הרשמה תקינה approved:false → ALLOW', expectation: 'ALLOW',
    request: { auth: auth('student@x.com'), path: DB + '/documents/schools/tzvia/users/student_x_com', method: 'create',
      resource: { data: { email: 'student@x.com', name: 'א', grade: 'י', classNum: '3', approved: false } } },
    functionMocks: schoolMock('active', 'teacher@x.com') },
  // 8) מורה מנסה לשנות את teacherEmail של בית הספר שלו — נחסם
  { name: 'מורה משנה teacherEmail → DENY', expectation: 'DENY',
    request: { auth: auth('teacher@x.com'), path: DB + '/documents/schools/tzvia', method: 'update',
      resource: { data: { slug: 'tzvia', name: 'צביה', teacherEmail: 'hacker@x.com', status: 'active' } } },
    functionMocks: schoolMock('active', 'teacher@x.com') },
  // 9) platform_media: משתמש רגיל מנסה לכתוב — נחסם
  { name: 'תלמידה כותבת platform_media → DENY', expectation: 'DENY',
    request: { auth: auth('student@x.com'), path: DB + '/documents/platform_media/m1', method: 'create',
      resource: { data: { type: 'youtube', url: 'x' } } } },
  // 10) קריאת מטא-דאטה של בי"ס בלי התחברות — מותרת (מסך הכניסה)
  { name: 'קריאת schools/tzvia בלי auth → ALLOW', expectation: 'ALLOW',
    request: { path: DB + '/documents/schools/tzvia', method: 'get' } },
  // 11) list בתי ספר ע"י תלמידה — נחסם
  { name: 'list schools ע"י תלמידה → DENY', expectation: 'DENY',
    request: { auth: auth('student@x.com'), path: DB + '/documents/schools', method: 'list' } },
  // 12) legacy: תלמידה כותבת finalGrade לעצמה בשורש — עדיין נחסם
  { name: 'legacy: תלמידה מזייפת finalGrade → DENY', expectation: 'DENY',
    request: { auth: auth('student@x.com'), path: DB + '/documents/exam_responses/e1/students/student_x_com', method: 'create',
      resource: { data: { email: 'student@x.com', finalGrade: 100 } } } },
];

token(tok => {
  const body = JSON.stringify({ source: { files: [{ name: 'firestore.rules', content: RULES }] },
    testSuite: { testCases: CASES.map(c => ({ expectation: c.expectation, request: c.request, functionMocks: c.functionMocks || [] })) } });
  const req = https.request({ hostname: 'firebaserules.googleapis.com', path: '/v1/' + P + ':test', method: 'POST',
    headers: { Authorization: 'Bearer ' + tok, 'Content-Type': 'application/json' } }, res => {
    let d = ''; res.on('data', c => d += c); res.on('end', () => {
      const j = JSON.parse(d); if(!j.testResults) console.log('RAW:', d.slice(0,900));
      if (j.issues && j.issues.length) { console.log('COMPILE ISSUES:', JSON.stringify(j.issues, null, 1).slice(0, 1200)); }
      const results = (j.testResults || []);
      let pass = 0;
      results.forEach((r, i) => {
        const ok = r.state === 'SUCCESS';
        if (ok) pass++;
        console.log((ok ? 'PASS ' : 'FAIL ') + CASES[i].name + (ok ? '' : ' — ' + JSON.stringify(r).slice(0, 260)));
      });
      console.log('===== ' + pass + '/' + CASES.length + ' rules tests passed =====');
    });
  });
  req.write(body); req.end();
});
