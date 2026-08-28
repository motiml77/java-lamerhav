# מבנה Firebase — התייחסות מלאה ועדכנית

**נכתב:** 2026-08-28. **למה עכשיו:** תוך כדי תכנון "פתיחת שנה חדשה" נערמו שדות
נוספים (`gradeHistory`, `promotionYear`, ומוצע `examSnapshots`) על מסמכים
קיימים, בלי מסמך אחד שמראה את כל המבנה. הקובץ `firebase-structure-java-lamerhav.md`
בזיכרון הקבוע **מיושן לחלוטין** — נכתב לפני המעבר הרב-בית-ספרי, לפני מערכת
העלאת השכבה, לפני הארכיון, לפני Storage. זה הקובץ שמחליף אותו.

**עיקרון מנחה:** בית ספר יחיד טיפוסי מחזיק כמה עשרות תלמידות וכמה עשרות
נושאים. שום קולקציה כאן לא צפויה לגדול לאלפי מסמכים. לכן שדה מצטבר על מסמך
קיים (כמו `gradeHistory` או `examSnapshots`) הוא בחירה סבירה — לא בעיית סקאלה.
**הבעיה האמיתית הייתה תיעוד, לא ארכיטקטורה** — לכן הפתרון כאן הוא מסמך אחד
ברור, לא פירוק לתת-אוספים.

---

## 1. העץ הרב-בית-ספרי — `schools/{slug}/...`

זהו המבנה הפעיל לכל בית ספר שנפתח דרך `/manage` (`docs/multi-school-plan.md`).
`col(name)` (`app.html:920`) מנתב לכאן כשיש `SCHOOL_SLUG` תקין ואין `__legacyFallback`.

```
schools/{slug}                          מסמך בית הספר עצמו
├── slug, name, teacherEmail, teacherName, status ('active'|'suspended')
├── createdAt, createdBy, suspendedAt, suspendReason
└── settings: { grades: ['י','יא','יב'], subtitle }

schools/{slug}/classes/{classId}        נושא לימוד — ראו סעיף 2 (המסמך המרכזי)
schools/{slug}/questions/{examId}_q_{n} תוכן שאלה: instructions, imageBase64/imageUrl
schools/{slug}/exam_settings/{examId}   homeworkPrompt, timeLimit
schools/{slug}/grading_rubrics/{examId} הנחיות בדיקה — מורה בלבד
schools/{slug}/notifications/{id}       התראות לתלמידות
schools/{slug}/media/{mediaId}          סרטון/קובץ/הערה לנושא, ברמת בית הספר
schools/{slug}/practice/{qId}           שאלת תרגול קבועה, ברמת בית הספר
schools/{slug}/activity/{email__date}   רישום כניסה יומי (למד"ד סטטיסטיקה)
schools/{slug}/users/{safeEmail}        תלמידה — ראו סעיף 3 (המסמך המרכזי השני)
schools/{slug}/exam_responses/{examId}/students/{safeEmail}
schools/{slug}/homework_responses/{examId}/students/{safeEmail}
```

`safeEmail` = `email.replace(/[.@]/g, '_')` — עקבי בכל מקום (`app.html:969` ואילך).

## 2. `classes/{id}` — נושא לימוד (המסמך המרכזי הראשון)

```js
{
  id: "class_1767263869264",
  title: "רשימה מקושרת",
  topicKey: "lists",              // מזהה לטיני יציב — לא נשבר בשינוי שם
  grade: "י" | "יא" | "יב" | "הכל",  // תלמידה רואה: שכבתה + שנה אחת אחורה (studentSeesClass)
  icon: "🟩",
  order: 1,
  visible: true,                  // מורה מסתירה נושא מתלמידות
  archived: false,                // ArchiveManager — ארכוב נושא שלם (docs/plan-topics-and-archive.md)
  archiveName: "", archiveYear: "",
  lesson: { intro, videoUrl, videoTitle, points:[], sections:[{h,p,code}],
            examples:[{title,explain,code}], exercises:[{task,starter,hint,solution}] },
  exams: [                        // ← מערך אחד, חי, בלי שום ממד של שנה
    {
      id: "exam_1767...",
      title: "מערך — תרגילים קלים",
      type: "homework" | "exam",
      duration: 45 | null,
      visible: true,
      questions: [{ number, title, points, questionType: "code"|"automata", automataType, alphabet }]
    }
  ],

  // ===== מוצע, טרם ממומש — ראו docs/plan-fresh-year-exam-reset.md =====
  examSnapshots: {                // מפתח = schoolYearKey() בזמן "פתיחת שנה חדשה"
    "2025/26": [ /* עותק מלא של exams כפי שהיה, אותם id-ים בדיוק */ ]
  }
}
```

**הכלל הקובע:** `exams` הוא **תמיד** התצוגה החיה של השכבה הנוכחית. `examSnapshots`
הוא **אך ורק** מה שתלמידה שקודמה רואה כשהיא מסתכלת אחורה — הוא לא מוצג למורה
ולא לשכבה הנוכחית. פירוט מלא בסעיף 5.

## 3. `users/{safeEmail}` — תלמידה (המסמך המרכזי השני)

```js
{
  email, name, approved: true|false,
  grade: "י" | "יא" | "יב",
  classNum: "3",                  // אופציונלי

  // ===== מערכת העלאת שכבה — app.html:920-1150 =====
  graduated: false,               // true = סיימה יב; השכבה עצמה נשארת 'יב' כדי שציונים ימשיכו להיקרא
  graduatedAt: "",
  promotionYear: "2025/26",       // חוסם הרצה כפולה באותה שנה"ל
  promotedFrom: "יא",
  promotedAt: "2026-06-15T...",
  gradeHistory: [                 // append-only, arrayUnion — אף פעם לא נמחק/נדרס
    { from: "יא", to: "יב", at, year: "2025/26", classNum: "3" }
  ]
}
```

`schoolYearKey()` (`app.html:926`): ספטמבר ואילך → שנה נוכחית היא ההתחלה.
פורמט `"YYYY/YY"`, למשל `"2025/26"`.

## 4. אוספי שורש — `schools/lamerhav` ומצב legacy בלבד

**אלה קיימים אך ורק בגלל lamerhav, שעדיין לא עבר את שלב 6 (המעבר הסופי).**
כתובת בית ספר חדש שנפתח דרך `/manage` **אינה** משתמשת בהם בכלל — `col()`
מנתב ישירות לעץ `schools/{slug}/...`. אין לשנות אותם או להוסיף להם שדות
חדשים; כל תכונה חדשה נכתבת רק תחת העץ הרב-בית-ספרי.

```
classes, questions, exam_settings, grading_rubrics, users,
exam_responses/{id}/students, homework_responses/{id}/students
```

## 5. תוכן גלובלי לכל בתי הספר (שורש, לא legacy — פעיל תמיד)

```
platform_media/{id}         סרטון/קובץ/הערה גלובליים לנושא (לפי topicKey)
platform_questions/{id}     שאלת תרגול קבועה גלובלית
platform_lessons/{topicKey} דריסת תוכן שיעור גלובלית (עדיפות: כיתה > גלובלי > מובנה בקוד)
platform_settings/ads       מסמך יחיד — פרסום (popup/banner), imageUrl מ-Storage
```

## 6. Firebase Storage — `gs://exams-a93fb.firebasestorage.app`

```
platform/ads/{popup|banner}/{fileId}.{ext}      תמונות פרסום — כתיבה: מנהל ראשי בלבד
platform/topics/{topicKey}/{fileId}.{ext}       תוכן גלובלי לנושא — מנהל ראשי בלבד
schools/{slug}/{כל נתיב אחר}/{fileId}.{ext}      כל קובץ ברמת בית ספר — כתיבה: כל מחוברת (עד 20MB,
                                                  תמונה/PDF/Office), מחיקה/דריסה: המעלה או מנהל
```
`worker.storagePath(folder, file)` (`app.html`, אזור ה-ads) יוצר `{timestamp}_{random}.{ext}` —
לעולם לא שם הקובץ המקורי. `storage.rules` אינו בודק שיוך לבית ספר ספציפי
(cross-service rules לא עובדים בפרויקט הזה — ראה `worker/README.md`
וההערה בראש `storage.rules`) — **הבידוד האמיתי הוא ב-firestore.rules**, לא כאן.

## 7. עקרונות שחלים על כל מה שמתווסף כאן

1. **שום מחיקה — רק תיוג/העברה.** תואם את `gradeHistory` (append-only)
   ואת `ArchiveManager` (`archived:true`, לא מחיקה).
2. **מזהים לא משתנים לעולם.** `topicKey`, `examId`, `qId=${examId}_q_${n}`,
   `safeEmail` — כל שינוי בהם מנתק קריאה קיימת בשקט. גם `examSnapshots`
   שומר את אותם `exam.id` בדיוק, כדי ש-`exam_responses`/`questions` ימשיכו
   להיפתר נכון גם אחרי שהמבחן "עבר" ל-snapshot.
3. **כתיבה חלקית (`batch.update()` עם dot-path), לא `.set()` מלא**, כשעורכים
   שדה בודד על מסמך שיכול להיערך במקביל על ידי מישהי אחרת (ראה `saveClassesBatch`
   מול `applyPromotion`/`PROMOTE_CHUNK` ב-`docs/plan-fresh-year-exam-reset.md` §3).
4. **חוקי Firestore ברמת מסמך שלם** (`canManage(school)`) — לא ברמת שדה,
   פרט ל-`users` שיש בו `touchesPromotionFields()`. שדה חדש על `classes`
   או `users` שאינו ברשימת השדות המוגנים **לא** דורש שינוי חוקים; שדה
   חדש שכן צריך להיות מוגן ממורה/תלמידה (כמו שדות ההעלאה) **כן** דורש.
5. **מסמך זה מתעדכן בכל תוסף סכימה.** אם שדה חדש נוסף ל-`classes` או
   ל-`users` בלי עדכון כאן — זו טעות תהליך, לא רק פער תיעוד.

---

## מה עוד פתוח

- `docs/plan-topics-and-archive.md` — גרירת נושאים לשכבה + עץ ארכיון לפי
  שנה ושכבה (סעיף ב' שם). לא סותר את המסמך הזה — `archiveYear`/`archiveGrade`
  שם הם ברמת **נושא שלם**, שונה במפורש מ-`examSnapshots` שכאן שהוא ברמת
  **מבחן בודד בתוך נושא שנשאר פעיל**.
- `docs/plan-fresh-year-exam-reset.md` — התוכנית המלאה, כולל כל שינויי
  הרינדור הנדרשים (10 מוקדים, טבלה מלאה) ורשימת הבדיקות.
