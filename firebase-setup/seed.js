/**
 * ============================================================================
 *  Firebase Firestore — סקריפט הקמה (Seed) לאתר "Java לבגרות"
 *  פרויקט: exams-a93fb
 *
 *  מה הסקריפט עושה:
 *   1. יוצר את כל 10 נושאי הלימוד באוסף `classes` (עם שיוך שכבה נכון יא/יב).
 *   2. יוצר מסמכי דוגמה לכל שאר האוספים כדי שהמבנה יהיה ברור ומוכן:
 *      questions · exam_settings · grading_rubrics · users ·
 *      exam_responses · homework_responses
 *   3. הסקריפט "בטוח להרצה חוזרת" (idempotent) — לא ידרוס נושא שכבר קיים.
 *
 *  הרצה: ראי את README.md שבתיקייה זו (התקנת firebase-admin + מפתח שירות).
 * ============================================================================
 */

const admin = require('firebase-admin');
const serviceAccount = require('./serviceAccountKey.json'); // מפתח השירות שתורידי מ-Firebase

admin.initializeApp({ credential: admin.credential.cert(serviceAccount) });
const db = admin.firestore();

// ---- מיפוי שכבה לנושא (זהה ללוגיקה באתר) ----
// יא = משתנים, לולאות, מחרוזות, מחלקות/אובייקטים, מערכים · השאר = יב
const GRADE_YA_KEYWORDS = ['משתנ', 'לולא', 'מחרוז', 'פונקצ', 'מתוד', 'מחלק', 'אובייקט', 'עצמים', 'מערכ'];
const gradeFor = (title) => GRADE_YA_KEYWORDS.some(k => title.includes(k)) ? 'יא' : 'יב';

// ---- 10 נושאי הלימוד המובנים. הכותרת המדויקת מפעילה באתר את ההסבר וההדגמה האינטראקטיבית. ----
const TOPIC_TITLES = [
  'משתנים, קלט ופלט',
  'תנאים',
  'לולאות',
  'מערכים',
  'מחרוזות',
  'מחלקות ואובייקטים',
  'רשימות מקושרות',
  'תור ומחסנית',
  'רקורסיה',
  'עצים בינאריים',
];

async function seedClasses() {
  console.log('▶ יוצר נושאים באוסף classes...');
  const snap = await db.collection('classes').get();
  const existingTitles = new Set(snap.docs.map(d => (d.data().title || '').trim()));
  let created = 0;

  for (let i = 0; i < TOPIC_TITLES.length; i++) {
    const title = TOPIC_TITLES[i];
    if (existingTitles.has(title)) { console.log(`  • "${title}" כבר קיים — מדלג`); continue; }
    const id = 'class_' + Date.now() + '_' + i;
    await db.collection('classes').doc(id).set({
      id,
      title,
      icon: '📚',
      grade: gradeFor(title),   // 'יא' או 'יב'
      order: i,
      archived: false,
      exams: [],                // מבחנים/עבודות נוספים דרך ממשק המורה
    });
    created++;
    console.log(`  ✓ נוצר "${title}" (כיתה ${gradeFor(title)})`);
  }
  console.log(`✔ classes: נוצרו ${created} נושאים חדשים.\n`);
}

// ---- מסמכי דוגמה שממחישים את המבנה המלא של כל אוסף ----
async function seedExamples() {
  console.log('▶ יוצר מסמכי דוגמה (מבנה מלא)...');

  // 1) נושא לדוגמה עם מבחן ועבודה — מדגים את שדה exams[] כולל duration (משך מבחן בדקות)
  const demoClassId = 'class_example';
  await db.collection('classes').doc(demoClassId).set({
    id: demoClassId,
    title: 'נושא לדוגמה (אפשר למחוק)',
    icon: '📚',
    grade: 'יא',
    order: 99,
    archived: false,
    exams: [
      {
        id: 'exam_example',
        title: 'מבחן לדוגמה',
        type: 'exam',          // 'exam' | 'homework'
        duration: 60,          // משך המבחן בדקות (null = ללא הגבלה). רק למבחן.
        visible: true,
        questions: [
          { number: 1, title: 'שאלה ראשונה', points: 50, questionType: 'code' },
          { number: 2, title: 'שאלה שנייה', points: 50, questionType: 'code' },
        ],
      },
      {
        id: 'hw_example',
        title: 'שיעורי בית לדוגמה',
        type: 'homework',
        visible: true,
        questions: [
          { number: 1, title: 'תרגיל', points: 100, questionType: 'code' },
        ],
      },
    ],
  }, { merge: true });

  // 2) questions — תוכן שאלה. מזהה המסמך: `${examId}_q${number}`
  //    תומך בשני שמות שדה: instructions/body, imageBase64/image, starterCode/starter
  await db.collection('questions').doc('exam_example_q1').set({
    instructions: 'כתבי מתודה שמקבלת מספר שלם ומחזירה את ריבועו.',
    imageBase64: '',                 // תמונת השאלה (base64, דחוסה אוטומטית באתר עד <1MB)
    starterCode: 'public static int square(int n) {\n    // כתבי כאן\n    return 0;\n}',
    solutionCode: '',                // פתרון (נראה רק למורה)
    questionPrompt: '',              // דגשי בדיקה לשאלה זו (אופציונלי)
    attachment: null,               // חומר מצורף: { name, url, dataUri } — Drive/PDF/Word
  }, { merge: true });

  // 3) exam_settings — נקרא ע"י כל מי שמחוברת. מחזיק את ה-prompt לבדיקה עצמית של שיעורי בית.
  await db.collection('exam_settings').doc('hw_example').set({
    homeworkPrompt: 'בדקי את הקוד של התלמידה, ציין שגיאות תחביר והיגיון, והצע שיפורים — בעברית.',
  }, { merge: true });

  // 4) grading_rubrics — מחוון הבדיקה של מבחן. למורה בלבד (ראי firestore.rules).
  await db.collection('grading_rubrics').doc('exam_example').set({
    examInstructions: 'הקפידי על נכונות לוגית, מקרי קצה וחתימת מתודה.',
    questions: { '1': 'ודאי שהמתודה מחזירה int ולא מדפיסה.' },
    // שמות חלופיים נתמכים גם הם: generalInstructions / questionNotes
    generalInstructions: 'הקפידי על נכונות לוגית, מקרי קצה וחתימת מתודה.',
    questionNotes: { '1': 'ודאי שהמתודה מחזירה int ולא מדפיסה.' },
  }, { merge: true });

  // 5) users — פרופיל תלמידה. מזהה המסמך: האימייל עם . ו-@ מוחלפים ב-_
  await db.collection('users').doc('example_student_gmail_com').set({
    email: 'example.student@gmail.com',
    name: 'תלמידה לדוגמה',
    grade: 'יא',                 // 'יא' | 'יב'
    approved: false,            // המורה מאשרת ידנית. תלמידה לא יכולה לאשר את עצמה (רולס).
    // graduated: true,         // מסומן כשמעלים שכבה / מסיימים לימודים (ארכיון)
    // promotedFrom: 'יא',      // נשמר אוטומטית בעליית כיתה
  }, { merge: true });

  // 6) exam_responses / homework_responses — תשובות. תת-אוסף students, מזהה = אימייל בטוח.
  await db.collection('exam_responses').doc('exam_example')
    .collection('students').doc('example_student_gmail_com').set({
      email: 'example.student@gmail.com',
      studentName: 'תלמידה לדוגמה',
      examType: 'exam',
      answers: { '1': { code: 'public static int square(int n){ return n*n; }' } },
      submitted: false,
      pasteAttempts: [],          // ניסיונות הדבקה שנחסמו במבחן
      exitAttempts: [],           // יציאות מהמבחן (מעבר טאב / יציאה ממסך מלא) — חשד להעתקה
      // finalGrade: 90,          // ציון סופי — נכתב ע"י המורה בלבד (רולס)
      // sentToStudent: true,     // נשלח לתלמידה — נכתב ע"י המורה בלבד (רולס)
    }, { merge: true });

  console.log('✔ מסמכי דוגמה נוצרו.\n');
}

(async () => {
  try {
    await seedClasses();
    await seedExamples();
    console.log('🎉 ההקמה הושלמה. אל תשכחי לפרסם את firestore.rules ב-Firebase Console.');
    process.exit(0);
  } catch (e) {
    console.error('❌ שגיאה בהקמה:', e);
    process.exit(1);
  }
})();
