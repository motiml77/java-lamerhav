/**
 * "פתיחת שנה חדשה" מקצה לקצה מול Firestore החי — התרחיש האדוורסרי המדויק
 * מ-docs/plan-fresh-year-exam-reset.md §6 סעיף 1: תלמידה שקודמה מ-יא ליב
 * חייבת להמשיך לראות את המבחן הישן שלה — שאלה, תמונה ותשובה — במלואם,
 * אחרי שכיתה י"א חדשה כבר אופסה.
 *
 * שולח בדיוק את ה-Commit RPC שה-batch.update() של DataService.openNewYear
 * שולח (dot-path על שדה שלא קיים עדיין), כדי לוודא ש-Firestore באמת יוצר
 * את המפה המקוננת נכון ולא דורס שדות אחרים של הנושא.
 *
 * הרצה: node scripts/e2e-open-new-year.js
 */
const https = require('https'), fs = require('fs'), os = require('os'), path = require('path');
const PROJECT = 'exams-a93fb';
const SLUG = 'zz-newyear-probe';
const FS = 'firestore.googleapis.com';
const DOCS = `/v1/projects/${PROJECT}/databases/(default)/documents`;
const YEAR = '2099/00'; // שנה בדיונית מובחנת, לא מתנגשת עם נתונים אמיתיים

function oauth() {
  return new Promise(res => {
    const cfg = JSON.parse(fs.readFileSync(path.join(os.homedir(), '.config', 'configstore', 'firebase-tools.json'), 'utf8'));
    const b = 'grant_type=refresh_token&refresh_token=' + encodeURIComponent(cfg.tokens.refresh_token) +
      '&client_id=563584335869-fgrhgmd47bqnekij5i8b5pr03ho849e6.apps.googleusercontent.com&client_secret=j9iVZfS8kkCEFUPaAeJV0sAi';
    const r = https.request({ hostname: 'oauth2.googleapis.com', path: '/token', method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' } }, s => {
      let d = ''; s.on('data', c => d += c); s.on('end', () => res(JSON.parse(d).access_token));
    });
    r.write(b); r.end();
  });
}
function call(host, p, method, headers, payload) {
  return new Promise(res => {
    const data = payload == null ? null : JSON.stringify(payload);
    const h = Object.assign({}, headers);
    if (data) h['Content-Length'] = Buffer.byteLength(data);
    const r = https.request({ hostname: host, path: p, method, headers: h }, s => {
      let d = ''; s.on('data', c => d += c); s.on('end', () => res({ code: s.statusCode, body: d }));
    });
    r.on('error', e => res({ code: 0, body: String(e) }));
    if (data) r.write(data); r.end();
  });
}
const adm = t => ({ Authorization: 'Bearer ' + t, 'Content-Type': 'application/json' });
const S = v => ({ stringValue: v });

let pass = 0, fail = 0;
function check(label, cond, detail) {
  cond ? pass++ : fail++;
  console.log((cond ? 'PASS ' : 'FAIL ') + label + (cond ? '' : ' — ' + detail));
}

(async () => {
  const tok = await oauth();
  try {
    // ---------- הקמה: בית ספר, נושא עם מבחן+שאלה+תמונה, תשובת תלמידה ----------
    await call(FS, `${DOCS}/schools?documentId=${SLUG}`, 'POST', adm(tok), { fields: {
      slug: S(SLUG), name: S('בדיקת פתיחת שנה'), teacherEmail: S('teacher@newyear.example'), status: S('active') } });

    const examId = 'exam_probe_1';
    await call(FS, `${DOCS}/schools/${SLUG}/classes?documentId=topic_X`, 'POST', adm(tok), { fields: {
      title: S('לולאות — בדיקה'), grade: S('יא'), topicKey: S('loops'), visible: { booleanValue: true },
      exams: { arrayValue: { values: [{ mapValue: { fields: {
        id: S(examId), title: S('מבחן פרקים א-ג'), type: S('exam'),
        questions: { arrayValue: { values: [{ mapValue: { fields: {
          number: S('1'), title: S('שאלה 1'), points: S('20'), questionType: S('code') } } }] } },
      } } }] } },
    } });

    await call(FS, `${DOCS}/schools/${SLUG}/questions?documentId=${examId}_q_1`, 'POST', adm(tok), { fields: {
      instructions: S('פתרי את התרגיל הבא'), imageBase64: S('data:image/png;base64,AAAA') } });

    const safeEmail = 'studentA_gmail_com';
    await call(FS, `${DOCS}/schools/${SLUG}/exam_responses/${examId}/students?documentId=${safeEmail}`, 'POST', adm(tok), {
      fields: { email: S('studentA@gmail.com'), answers: { mapValue: { fields: { '1': { stringValue: 'תשובתי' } } } } } });

    // תלמידה שכבר קודמה יא→יב, עם gradeHistory תואם
    await call(FS, `${DOCS}/schools/${SLUG}/users?documentId=${safeEmail}`, 'POST', adm(tok), { fields: {
      email: S('studentA@gmail.com'), name: S('תלמידה מקודמת'), grade: S('יב'), approved: { booleanValue: true },
      gradeHistory: { arrayValue: { values: [{ mapValue: { fields: {
        from: S('יא'), to: S('יב'), at: S('2026-06-15T00:00:00Z'), year: S(YEAR), classNum: S('') } } }] } },
    } });

    console.log('הוקם: בית ספר, נושא עם מבחן+שאלה+תמונה, תשובת תלמידה, ותלמידה מקודמת עם gradeHistory\n');

    // ---------- הפעולה: "פתיחת שנה חדשה" — בדיוק ה-batch.update שהקוד שולח ----------
    // הערה: ב-REST חובה לצטט-בגרש-נטוי מקטע שמכיל '/' בנתיב שדה — בקוד עצמו
    // (app.html, DataService.openNewYear) זה נעשה עם firebase.firestore.FieldPath,
    // שאין לו את מגבלת הפענוח הזו כלל (אומת מול ה-SDK האמיתי בדפדפן, לא רק כאן).
    const commitBody = { writes: [{
      updateMask: { fieldPaths: ['examSnapshots.`' + YEAR + '`', 'exams'] },
      update: {
        name: `projects/${PROJECT}/databases/(default)/documents/schools/${SLUG}/classes/topic_X`,
        fields: {
          examSnapshots: { mapValue: { fields: {
            [YEAR]: { arrayValue: { values: [{ mapValue: { fields: {
              id: S(examId), title: S('מבחן פרקים א-ג'), type: S('exam'),
              questions: { arrayValue: { values: [{ mapValue: { fields: {
                number: S('1'), title: S('שאלה 1'), points: S('20'), questionType: S('code') } } }] } },
            } } }] } },
          } } },
          exams: { arrayValue: { values: [] } },
        },
      },
    }] };
    const commitRes = await call(FS, `/v1/projects/${PROJECT}/databases/(default)/documents:commit`, 'POST', adm(tok), commitBody);
    check('כתיבת פתיחת שנה חדשה הצליחה', commitRes.code === 200, commitRes.body.slice(0, 200));

    // ---------- אימות 1: הנושא — exams ריק, examSnapshots מכיל את המבחן, title לא נדרס ----------
    const topicRes = await call(FS, `${DOCS}/schools/${SLUG}/classes/topic_X`, 'GET', adm(tok));
    const topic = JSON.parse(topicRes.body).fields;
    check('exams החי התרוקן', (topic.exams.arrayValue.values || []).length === 0);
    check('examSnapshots[YEAR] מכיל את המבחן', topic.examSnapshots.mapValue.fields[YEAR].arrayValue.values.length === 1);
    const snapExam = topic.examSnapshots.mapValue.fields[YEAR].arrayValue.values[0].mapValue.fields;
    check('אותו exam.id בדיוק ב-snapshot', snapExam.id.stringValue === examId, snapExam.id.stringValue);
    check('כותרת הנושא (title) לא נדרסה ע"י הכתיבה החלקית', topic.title.stringValue === 'לולאות — בדיקה', JSON.stringify(topic.title));
    check('grade של הנושא לא נדרס', topic.grade.stringValue === 'יא');

    // ---------- אימות 2: תוכן השאלה (הוראות+תמונה) לא נגעו בו כלל ----------
    const qRes = await call(FS, `${DOCS}/schools/${SLUG}/questions/${examId}_q_1`, 'GET', adm(tok));
    const q = JSON.parse(qRes.body).fields;
    check('הוראות השאלה שרדו', q.instructions.stringValue === 'פתרי את התרגיל הבא');
    check('תמונת השאלה שרדה', q.imageBase64.stringValue === 'data:image/png;base64,AAAA');

    // ---------- אימות 3: תשובת התלמידה לא נגעו בה כלל ----------
    const rRes = await call(FS, `${DOCS}/schools/${SLUG}/exam_responses/${examId}/students/${safeEmail}`, 'GET', adm(tok));
    const r = JSON.parse(rRes.body).fields;
    check('תשובת התלמידה שרדה', r.answers.mapValue.fields['1'].stringValue === 'תשובתי');

    // ---------- אימות 4: תלמידה חדשה שנרשמת ל-יא (בלי gradeHistory) הייתה רואה נושא ריק ----------
    // (הלוגיקה עצמה — examsForView — נבדקת בנפרד ב-unit test; כאן מוודאים שהנתון
    //  שממנו היא קוראת (cls.exams) באמת ריק, שזה כל מה שקובע את מה שהיא תראה.)
    check('מבחינת תלמידה חדשה: cls.exams ריק = נושא נקי', (topic.exams.arrayValue.values || []).length === 0);

    // ---------- אימות 5: הרצה חוזרת לאותה שנה לא מוחקת שנים קודמות ----------
    const YEAR2 = '2098/99';
    const commit2 = { writes: [{
      updateMask: { fieldPaths: ['examSnapshots.`' + YEAR2 + '`', 'exams'] },
      update: { name: `projects/${PROJECT}/databases/(default)/documents/schools/${SLUG}/classes/topic_X`,
        fields: { examSnapshots: { mapValue: { fields: { [YEAR2]: { arrayValue: { values: [] } } } } }, exams: { arrayValue: { values: [] } } } },
    }] };
    await call(FS, `/v1/projects/${PROJECT}/databases/(default)/documents:commit`, 'POST', adm(tok), commit2);
    const topic2 = JSON.parse((await call(FS, `${DOCS}/schools/${SLUG}/classes/topic_X`, 'GET', adm(tok))).body).fields;
    check('שנה קודמת (YEAR) נשארת אחרי איפוס נוסף (YEAR2)', !!topic2.examSnapshots.mapValue.fields[YEAR], JSON.stringify(Object.keys(topic2.examSnapshots.mapValue.fields || {})));

    console.log('\n===== ' + pass + '/' + (pass + fail) + ' =====');
  } finally {
    for (const p of [`schools/${SLUG}/classes/topic_X`, `schools/${SLUG}/questions/exam_probe_1_q_1`,
                      `schools/${SLUG}/exam_responses/exam_probe_1/students/studentA_gmail_com`,
                      `schools/${SLUG}/exam_responses/exam_probe_1`, `schools/${SLUG}/users/studentA_gmail_com`, `schools/${SLUG}`]) {
      await call(FS, `${DOCS}/${p}`, 'DELETE', adm(tok));
    }
    console.log('ניקוי הושלם.');
  }
})();
