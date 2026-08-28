# תוכנית יישום: "פתיחת שנה חדשה" — איפוס מבחנים לנושא בלי לפגוע בתצוגת "שנה אחורה"

## 1. מה נכון היום

**המנגנון הקיים היחיד ל"שנה חדשה" לא נוגע ב-exams בכלל.** מודל "ניקוי שנה" הקיים (`yearClearModal` → `DataService.clearAllYearResponses`) מוחק רק תשובות תלמידות מ-`exam_responses`/`homework_responses`, ומצהיר בממשק במפורש: "מה יישאר: כל המבחנים ושיעורי הבית" (app.html:11144). הוא לא קורא ולא כותב למסמכי `classes` כלל.

**קיים בדיוק מערך `cls.exams` אחד לכל נושא, ללא הפרדה שנתית.**
- `exams` הוא שדה מוטבע (embedded array) בתוך מסמך `classes/{id}` בלבד — לא תת-אוסף (app.html:920, `saveClass`/`col()`).
- `isPastTopic` (app.html:8590) הוא בוליאני מחושב-מחדש בכל רינדור מהפרש בין `studentGrade` הנוכחי ל-`cls.grade`, **לא נשמר בשום מקום**. אותו document, אותו `cls.exams`, עובר מ"נוכחי" ל"עבר" רק כי `studentGrade` השתנה.
- `selectedClassObj` (app.html:8603) הוא `find()` פשוט מתוך `filteredClasses` — אין ענף/אובייקט חלופי לנושא past. `selectedClassObj.exams` זהה לחלוטין בין past להווה.
- כל אתרי הרינדור נבדקו ואינם מבחינים בין past להווה מעבר ל-CSS: סרגל מובייל (app.html:9070-9134), סרגל דסקטופ (app.html:9215-9285), עמוד הנושא לתלמידה (app.html:10358), כרטיס הנושא בעמוד הבית (`topicStat`/`examState`, app.html:8612, 10441). בכולם `visExams`/`visibleExams` נגזרים ישירות מ-`cls.exams` ללא כל תלות ב-`isPastTopic`.
- **מסקנה: איפוס נאיבי של `cls.exams` (מחיקה/דריסה) יפגע גם בתצוגת "עבר" של מי שכבר קודמה, כי מדובר באותו מערך בדיוק שממנו נגזר גם ה-lookback.** לכן הדרישה של הבעלים לארכוב+snapshot נתמכת ישירות בממצאים ואינה שערורייה מיותרת.

**תשובות התלמידה ותוכן השאלה (הוראות+תמונה) עצמאיים לגמרי ואינם בסיכון:**
- תשובות: `exam_responses`/`homework_responses` תחת `doc(examId)/students/doc(safeEmail)` — מפתח יחיד: `examId`+`email` (app.html:939, 1602-1738). לא תלוי ב-`cls.exams`.
- תוכן שאלה+תמונה: אוסף `questions`, מסמך `${examId}_q_${number}` (app.html:1528, 8029-8065). ה-`imageBase64` מוסר במפורש מהאובייקט לפני שהוא נכתב ל-`cls.exams` (`delete data._imageBase64`, app.html:8029) ונשמר רק שם.
- **אבל הגדרת המבחן עצמה (כותרת/משך/רשימת שאלות עם number/points) קיימת אך ורק ב-`cls.exams`.** `findClassForExam` (app.html:8333) מחפש רק שם; אם ערך המבחן נעלם מ-`cls.exams`, `openFavorite`/`goToNotif` (app.html:7433-7469) מציגים "הפריט אינו זמין יותר" — למרות שהתשובה והתמונה עדיין קיימות. זה בדיוק מה שחייב להימנע.

**מנוע ההעלאה (`applyPromotion`) מבודד לחלוטין מ-`classes`/`exams`** — נוגע רק ב-`col('users')` (app.html:940, 1070-1111). זו נקודת התפר הבטוחה: הפעלת "פתיחת שנה חדשה" לא צריכה לתאם עם שום נעילה/מצב שהעלאת השכבה כבר משנה בנושאי הלימוד, כי אין כזה.

**`gradeHistory` הוא הארכיון הבודד-האמין** (append-only, `arrayUnion`, app.html:1082): כל רשומה `{from, to, at, year, classNum}`. `promotionYear`/`promotedFrom`/`promotedAt` נדרסים בכל העלאה (app.html:1084) ולכן לא שמישים כארכיון. הרשומה הרלוונטית ל"מתי עזבה שכבה X" היא זו שבה `entry.from === X` — וה-`year` שלה מחושב פעם אחת לכל ריצת ההעלאה (`schoolYearKey()`, app.html:926, 1072) ומשותף לכל הסטודנטיות שקודמו יחד.

**נקודת תפר טבעית ב-UI קיימת:** `runPromotion` (app.html:7912-7926) מסתיים ב-`setPromoteStep(3)`, ובאותו רגע זמינים יחדיו: `promotePlan.fromGrade/toGrade/mode`, `promoteResult.year` (=`schoolYearKey()` של ריצה זו), ו-`promoteTargets` (רשימת התלמידות שקודמו בפועל). כפתורי ה-footer של שלב 3 קיימים כבר בטווח app.html:8992-9000.

**כתיבה למסמך `classes` לא דורשת שינוי בחוקי Firestore** — `allow write: if canManage(school)` הוא ברמת מסמך שלם, ללא `affectedKeys()` (firestore.rules:267-270), בשונה מ-`users` שיש בו `touchesPromotionFields()` (firestore.rules:89, 322).

**שם השדה חייב להימנע מהתנגשות עם תוכנית קיימת:** `docs/plan-topics-and-archive.md` (שורות 86-91) כבר "תפס" את השמות `archiveYear`/`archiveGrade`/`archiveName`/`archivedAt` לצורך ארכוב **נושא שלם** (topic-level, `cls.archived=true`) — טרם ממומש (`archiveGrade`/`archivedAt` לא קיימים כלל ב-app.html כיום). אסור להשתמש בשמות האלה עבור שדה הארכיון-פר-מבחן החדש.

---

## 2. מודל הנתונים

שדה חדש יחיד על מסמך `classes/{id}` הקיים (topic doc), בשם שאינו מתנגש עם התוכנית הקיימת:

```
classes/{id}
  ...
  exams: []                        // מתאפס — התצוגה החיה, לשכבה החדשה שנרשמת
  examSnapshots: {                 // מפה חדשה: מפתח = schoolYearKey, ערך = עותק מלא של exams כפי שהיה
      "2025/26": [ {id:'exam_123', title:'...', duration:..., questions:[{number,title,points,questionType,...}, ...]}, ... ],
      "2024/25": [ ... ]            // תומך בסבבי איפוס חוזרים באותו נושא
  }
```

נקודות מפתח:
- כל אובייקט מבחן מועתק **כמות שהוא**, כולל `id` — בלי לגעת בו. זה קריטי כי `id` הוא המפתח היחיד שדרכו נשלפים `exam_responses`/`questions` (app.html:939, 1528) — שינוי ה-id ינתק אותם.
- `examSnapshots` הוא מפה (לא מערך) לפי `year` כדי לתמוך בכמה סבבי איפוס עוקבים לאותו נושא בלי לדרוס snapshot קודם.
- ה-`year` שמתויג על ה-snapshot בזמן ריצת "פתיחת שנה חדשה" הוא בדיוק אותו `schoolYearKey()`/`promoteResult.year` שנוצר **באותה ריצת ההעלאה** שהעבירה את הקבוצה מ-`fromGrade` (הנושא המאופס). זה אותו ערך `year` שנכתב ל-`gradeHistory` של כל תלמידה שקודמה, ברשומה `{from: fromGrade, to: toGrade, year, ...}` (app.html:1082).

**נוסחת פענוח בזמן lookback render** (סטודנטית שכעת רואה נושא past, כלומר `isPastTopic(cls, studentGrade)===true`, app.html:8590):
1. קחי את `cls.grade` (השכבה שהנושא שייך אליה, למשל `'יא'`).
2. חפשי ב-`user.gradeHistory` את הרשומה **העדכנית ביותר** שבה `entry.from === cls.grade` (הרשומה שתיעדה את היציאה משכבה זו).
3. אם נמצאה — `year = entry.year`; קראי את `cls.examSnapshots?.[year]`. זה מערך המבחנים המקורי, עם אותם `id`-ים.
4. אם `cls.examSnapshots` לא קיים כלל (הנושא מעולם לא עבר איפוס) — נופלים חזרה ל-`cls.exams` הרגיל (תאימות אחורה מלאה לנושאים שלא אופסו).
5. אם לא נמצאה רשומת `gradeHistory` מתאימה (תלמידה שנכנסה ל-grade ידנית בלי אשף העלאה, app.html:977-998) — ראו סיכון פתוח בסעיף 7.

---

## 3. זרימת ה-UI

**מיקום ההפעלה:** שלב המשך טבעי בתוך אותו `<Modal isOpen={promoteModal.open}>` הקיים (app.html:8837), כ-`promoteStep===4` חדש, נפתח מכפתור נוסף בבלוק ה-footer של `promoteStep===3` (app.html:8992-9000) — לצד "ביטול הפעולה"/"שכבה נוספת"/"סיום". הכפתור "פתיחת שנה חדשה לנושאי {promotePlan.fromGrade}" מקבל כפרמטרים את `promotePlan.fromGrade` ואת `promoteResult.year` שכבר זמינים באותו רגע (app.html:7912-7926) — אין צורך לשחזר אותם.

**תצוגה מקדימה (preview) לפני ביצוע:**
- סינון `classes` לפי `cls.grade === promotePlan.fromGrade && !cls.archived` (אותו תבנית סינון כמו `classMatchesGrade`, app.html:8582).
- לכל נושא כזה: שם הנושא, מספר המבחנים/שיעורי הבית הקיימים כרגע ב-`cls.exams` שיאורכבו ויתאפסו.
- הודעת הבהרה מפורשת: "תשובות התלמידות, ציונים, הוראות שאלה ותמונות **לא נמחקים** — הם שמורים בנפרד ("
  ("`exam_responses`/`homework_responses`/`questions`) ויישארו נגישים לתלמידות שקודמו, דרך הצפייה 'שנה אחורה'".
- אזהרה אם נושא מסוים כבר עבר איפוס לאותו `year` בעבר (שכפול הפעלה בטעות) — `cls.examSnapshots?.[year]` כבר קיים.

**כפתור אישור** מבצע את הכתיבה בפועל, ואז `promoteStep` עובר ל"סיום" עם סיכום done/failed (תואם ל-`promoteResult` הקיים).

**דפוס הכתיבה — עוקב אחרי מוסכמת `PROMOTE_CHUNK`/`batch.update()` (לא `saveClassesBatch`):**
- `saveClassesBatch` (app.html:1478) עושה `batch.set()` מלא ללא `merge`, כלומר דורס את כל מסמך הנושא — מסוכן כאן כי מורה אחרת עלולה לערוך שדות אחרים של אותו נושא (title, description, visible) במקביל, ואלה יימחקו.
- לכן יש להשתמש בדפוס patch חלקי כמו `applyPromotion`/`undoPromotionRun` (app.html:935, 1077-1109): `db.batch()`, בצ'אנקים (`PROMOTE_CHUNK`=400 קיים, או קבוע דומה ייעודי — מספר הנושאים לבית ספר קטן בהרבה, אז הצ'אנק הוא הגנה בלבד), ולכל נושא:
  ```
  batch.update(col('classes').doc(cls.id), {
      [`examSnapshots.${year}`]: cls.exams || [],
      exams: []
  });
  ```
  שימוש בנתיב-נקודה (`examSnapshots.${year}`) נוגע רק בשני שדות ספציפיים — לא דורס שום שדה אחר במסמך, כולל לא `examSnapshots` של שנים קודמות.
- כישלון בצ'אנק עוצר מיידית ומחזיר `{done, failed, stopped:true}` — בדיוק כמו `applyPromotion` (app.html:1070-1109) — כדי לא "לנחש" הצלחה חלקית.

---

## 4. שינויי רינדור נדרשים, פונקציה־פונקציה

| # | פונקציה/מיקום | שינוי נדרש |
|---|---|---|
| 1 | `isPastTopic` (app.html:8590) | **ללא שינוי** — נשאר בוליאני נכון, אך מעתה חייב להיקרא בפועל בכל אתרי הרינדור למטה (לא רק ל-CSS/מיון) |
| 2 | `studentSeesClass` (app.html:8596) | **ללא שינוי** — עדיין קובע רק אם הנושא בכלל מסונן פנימה (נוכחי + שנה אחורה); לא נוגע ב-exams |
| 3 | `selectedClassObj` (app.html:8603) | להוסיף חישוב נגזר `effectiveExams`: אם `isPastTopic(selectedClassObj, studentGrade)` — פענוח לפי סעיף 2 (`gradeHistory` → `year` → `examSnapshots[year]`, עם נפילה ל-`cls.exams` אם אין snapshots); אחרת `selectedClassObj.exams` הרגיל |
| 4 | `topicStat`/`examState` (app.html:8612) | `topicStat` צריך לקבל את המערך המפוענח (לא תמיד `cls.exams`) כדי שספירת "X לביצוע/הושלם" בכרטיס הנושא (app.html:10441) תהיה נכונה גם לנושאי past אחרי איפוס |
| 5 | סרגל מובייל `visExams` (app.html:9070-9134) | `visExams` להיגזר מ-`effectiveExams` ולא מ-`cls.exams` ישירות, כשה-cls הוא past |
| 6 | סרגל דסקטופ `visExams` (app.html:9215-9285) | אותו שינוי במקביל (שכפול כמעט מילולי של #5) |
| 7 | עמוד נושא לתלמידה `visibleExams` (app.html:10358) | להיגזר מ-`effectiveExams` באותה שיטה — זה מוקד הרינדור המרכזי של `openExam`/"המשימה הבאה" |
| 8 | `findClassForExam` (app.html:8333) | להרחיב חיפוש: אם לא נמצא ב-`c.exams`, לחפש גם בתוך `Object.values(c.examSnapshots||{})` — אחרת מבחן שאורכב "נעלם" מבחינת חיפוש לפי `examId` |
| 9 | `openFavorite`/`goToNotif` (app.html:7433-7469) | לעדכן את שליפת ה-`exam` כך שתשתמש גם היא בחיפוש המורחב (#8) — אחרת מועדפים/התראות לתלמידה שקודמה יציגו "הפריט אינו זמין יותר" |
| 10 | `myProgress` loader (app.html:7622) | **חובה לתקן**: הלולאה היום אוספת `exam.id` רק מ-`classes` הגולמי דרך `c.exams` — אחרי איפוס, מבחנים שהועברו ל-`examSnapshots` ייעלמו מהלולאה הזו, וה-badge (`examState`) יחזור ל"todo" גם אם התלמידה כבר הגישה. יש להרחיב את האיסוף כך שיכלול גם מבחנים בתוך `c.examSnapshots` (לפחות עבור נושאים שהתלמידה עדיין רואה ב-lookback) |
| 11 | תצוגת המורה — `filteredClasses` עורך (app.html:8603), `visExams` עורך (app.html:9072/9217), `buildDetailedReport` (app.html:8371) | **ללא שינוי נדרש** — המורה תמיד עובדת מול `cls.exams` החי (הרשימה הריקה החדשה לשכבה שאופסה); אין דרישה מהבעלים לחשיפת ארכיון למורה — נשאר `cls.exams` בלבד |

---

## 5. Firestore Rules

**אין צורך בשום שינוי.** `allow write: if canManage(school)` על `match /classes/{classId}` (firestore.rules:267-270) הוא ברמת מסמך שלם ללא הגבלת שדות (`affectedKeys()`), בניגוד ל-`users` שבו יש `touchesPromotionFields()` (firestore.rules:89, 322). המורה שכבר רשאית לכתוב ל-`classes` יכולה לכתוב את `examSnapshots` באותה הרשאה בדיוק — נקודה שגם `docs/plan-topics-and-archive.md:122` אימתה עצמאית לצורך שדותיה שלה. גם קריאה (`allow read: if isActiveUser(school) || isSuperAdmin()`) כבר מכסה קריאת `examSnapshots` על ידי כל תלמידה פעילה — לא נדרש שינוי גם שם.

---

## 6. רשימת בדיקות

### 1. (אדוורסרי, ראשון) — תלמידה מקודמת יא→יב חייבת להמשיך לראות מבחן ישן מלא (שאלה+תמונה+תשובה) אחרי שי"א חדשה כבר אופסה

**מצב לפני:**
```
classes/topic_X = { grade:'יא', exams:[{id:'exam_123', title:'מבחן פרקים א-ג',
                     questions:[{number:1, title:'שאלה 1', points:20, questionType:'open'}]}] }
questions/exam_123_q_1 = { instructions:'פתרי את התרגיל הבא', imageBase64:'<base64>' }
exam_responses/exam_123/students/studentA_gmail_com = { answers:{1:'תשובתי'}, submittedAt:... }
users/studentA = { grade:'יא', gradeHistory:[] }
```

**פעולה 1 — קידום:** `applyPromotion({fromGrade:'יא', toGrade:'יב', students:[studentA], mode:'promote'})`
→ `users/studentA.grade = 'יב'`, `gradeHistory` מקבל `{from:'יא', to:'יב', year:'2026/27', at:..., classNum:...}`.

**פעולה 2 — פתיחת שנה חדשה** (סעיף 3), `fromGrade='יא'`, `year='2026/27'` (מ-`promoteResult.year` של אותה ריצה):
→ `classes/topic_X` מתעדכן ל-`{ ..., exams: [], examSnapshots: { '2026/27': [{id:'exam_123', title:'מבחן פרקים א-ג', questions:[...]}] } }`.

**מצב אחרי — תלמידה חדשה ל-י"א:** נרשמת עם `grade:'יא'`, `gradeHistory:[]`. רואה את `topic_X` (`studentSeesClass` — נוכחית) עם `cls.exams=[]` → **נושא נקי לחלוטין**.

**מצב אחרי — studentA (`grade:'יב'`) פותחת את `topic_X`:**
- `isPastTopic(topic_X, 'יב')` = true (`GRADE_ORDER['יא'] < GRADE_ORDER['יב']`).
- פענוח (#3 בסעיף 2): `gradeHistory` → רשומה עם `from==='יא'` → `year='2026/27'` → `cls.examSnapshots['2026/27']` → מחזיר `[{id:'exam_123', title:'מבחן פרקים א-ג', questions:[{number:1,...,points:20}]}]`.
- כותרת/ניקוד/מבנה השאלה — **זהים במדויק** למה שהיה.
- תמונה+הוראות: `qId='exam_123_q_1'` (אותו `id`, לא השתנה) → `loadQuestionContent` → `questions/exam_123_q_1` **לא נגעו בו כלל** → מחזיר את ההוראות והתמונה המקוריות.
- תשובה: `loadStudentData('exam_123','exam','studentA@...')` → `exam_responses/exam_123/students/studentA_gmail_com` **לא נגעו בו כלל** → מחזיר `{answers:{1:'תשובתי'}}`.
- **תוצאה: שאלה, ניקוד, תמונה ותשובה — כולם שרדו במדויק.**

בדקי גם: badge "הוגש/נבדק" בכרטיס הנושא (#10 בסעיף 4) מציג נכון על סמך `exam_responses` הקיים, לא "לביצוע".

### 2. תלמידה חדשה ל-י"א רואה נושא ריק לגמרי (0 מבחנים) בכל נושאי ה-י"א שאופסו.

### 3. תצוגת מורה (`isEditor`) על אותו נושא אחרי איפוס — `cls.exams=[]` בלבד, ניתן להוסיף מבחנים חדשים כרגיל; ה-snapshot לא מוצג ולא מפריע.

### 4. מועדפים/התראות (`openFavorite`/`goToNotif`) של studentA שמצביעים ל-`exam_123` ממשיכים להיפתח נכון (לא "הפריט אינו זמין יותר") — בודק את השינוי בסעיף 4 #8-#9.

### 5. תלמידה שנשארה באותה שכבה שנתיים ברצף (ללא קידום) — regression: `studentSeesClass` עדיין מגביל לשנה אחת אחורה בדיוק; לוודא שהשינוי לא פתח בטעות גישה לשנים נוספות.

### 6. כתיבה עם `batch.update()` חלקי (`examSnapshots.${year}` + `exams`) לא דורסת שדות אחרים של הנושא (`title`, `description`, `visible`) שנערכו במקביל על ידי מורה אחרת.

### 7. כשל בצ'אנק באמצע ריצה על כמה נושאים — לוודא `{done, failed, stopped:true}` מדווח נכון, ושכל נושא בודד עובר את שני השינויים (`examSnapshots`+`exams`) **באותה קריאת `batch.update()`** — לא במצב ביניים של אחד בלי השני.

### 8. איפוס חוזר לאותו נושא בשנה עוקבת — `examSnapshots` צובר שני מפתחות שנה נפרדים (`'2025/26'`, `'2026/27'`), וכל קוהורט מפוענח לשנה הנכונה שלו, לא רק לאחרונה.

### 9. תלמידה עם `grade='יא'` שנרשמה ישירות (ללא מעבר באשף ההעלאה, לפי app.html:977-998) ולכן `gradeHistory` ריק — לוודא שהמערכת לא קורסת כשמנסים לפענח snapshot בהיעדר רשומה מתאימה (התנהגות נפילה, ראו סיכון בסעיף 7).

---

## 7. סיכונים פתוחים / נדחה במפורש

- **תלמידה שקיבלה שכבה ידנית/רישום ישיר בלי מעבר באשף ההעלאה** (app.html:977-998, `saveUserProfile` לא נוגע ב-`gradeHistory`) — במקרה כזה אין רשומת `gradeHistory` עם `from===cls.grade` לפענוח, והתנהגות ברירת המחדל (fallback ל-`cls.exams` הריק, או הסתרת הנושא) צריכה החלטה מפורשת לפני שילוח; לא הוגדרה כאן.
- **אין נעילת עריכה על נושא past** — הממצאים מראים שגם היום תלמידה ב"עבר" יכולה טכנית לפתוח/לענות מחדש על מבחן ישן (onClick בסרגל, app.html:9070-9134/9215-9285, ללא בדיקת past). התוכנית הזו לא משנה זאת — היא רק דואגת שהתצוגה תישאר תקינה. הבעלים לא ביקש נעילה; מוגדר כנדחה.
- **דוחות מורה (`buildDetailedReport`, app.html:8371) לא מרחיבים ל-`examSnapshots`** — מורה שרוצה דוח היסטורי חוצה-שנים לא מקבל זאת כאן; נדחה כתוספת עתידית נפרדת.
- **אין מנגנון "ביטול איפוס" (undo)** — בשונה מ-`undoPromotionRun` שקיים ומוגבל לשנה הנוכחית (app.html:1121-1126), לא מתוכנן כאן מנגנון סימטרי להחזרת `examSnapshots[year]` בחזרה ל-`exams` אם המורה טעתה בהרצת האיפוס. נדחה.
- **מרוץ תנאים (race condition) בין שתי מורות עורכות אותו נושא בו-זמנית** — הבחירה ב-`batch.update()` חלקי (סעיף 3) ממזערת אך לא מבטלת לחלוטין את הסיכון (Firestore אינו תומך כאן ב-compare-and-swap אמיתי); סיכון שיורי מתועד.
- **`myProgress` הרחבה (#10 בסעיף 4) מגדילה עוד יותר את דפוס הטעינה הגלובלית הקיים** (שכבר היום טוען לכל מבחן גלוי בכל שכבה בבית הספר, app.html:7622) — לא תוקן כאן, רק מורחב באותה שיטה; חוב ביצועים קיים שממשיך להצטבר.
- **מקרי קצה של חזרה על שכבה / חזרה מ"בוגרת"** ואינטראקציה עם כלל "הרשומה העדכנית ביותר עם `from===cls.grade`" — לא נבדקו לעומק, סבירות נמוכה, מוגדר כנדחה לבדיקה נקודתית אם יתעורר בפועל.