# הקמת Firebase לאתר "Java לבגרות"

ערכת הקמה מלאה שמכינה את כל האוספים והערכים הדרושים ב-Firestore, לפי מבנה האתר.

הפרויקט: **exams-a93fb**

---

## מבנה הנתונים (Firestore)

| אוסף | מה נשמר | מי כותב |
|---|---|---|
| `classes` | נושאים. כל מסמך = נושא, ובתוכו מערך `exams` (מבחנים/עבודות) | מורה |
| `questions` | תוכן שאלה. מזהה: `{examId}_q{number}` — הוראות, תמונה, קוד פתיחה, פתרון, חומר מצורף | מורה |
| `exam_settings` | `homeworkPrompt` לבדיקה עצמית של שיעורי בית (נגיש לתלמידות) | מורה |
| `grading_rubrics` | מחוון בדיקת מבחן (הנחיות כלליות + דגשים לשאלה) — **למורה בלבד** | מורה |
| `users` | פרופיל תלמידה: `email, name, grade, approved` (+`graduated`/`promotedFrom`) | תלמידה (שלה) + מורה |
| `exam_responses/{examId}/students/{email}` | תשובות מבחן: `answers, submitted, pasteAttempts, exitAttempts, finalGrade` | תלמידה (שלה) + מורה |
| `homework_responses/{examId}/students/{email}` | תשובות שיעורי בית — מבנה זהה | תלמידה (שלה) + מורה |

> **הערה:** תוכן הלימוד (הסברים, דוגמאות חיות, הדגמות אינטראקטיביות) **מובנה בקוד האתר** ומופיע אוטומטית לפי כותרת הנושא. לכן `seed.js` יוצר רק את מסמכי ה-`classes` עם הכותרות והשכבות הנכונות — וכל השאר נטען מהקוד.

### שיוך שכבה אוטומטי
- **כיתה יא:** משתנים/קלט-פלט · לולאות · מערכים · מחרוזות · מחלקות ואובייקטים
- **כיתה יב:** תנאים · רשימות מקושרות · תור ומחסנית · רקורסיה · עצים בינאריים

---

## שלב 1 — מפתח שירות (Service Account)

1. Firebase Console → ⚙️ **Project settings** → לשונית **Service accounts**.
2. לחצי **Generate new private key** → יורד קובץ JSON.
3. שמרי אותו בתיקייה הזו בשם **`serviceAccountKey.json`**.
   > ⚠️ קובץ סודי — אל תעלי אותו ל-GitHub (הוסיפי ל-`.gitignore`).

## שלב 2 — הרצת ההקמה

```bash
cd firebase-setup
npm init -y
npm install firebase-admin
node seed.js
```

הסקריפט יצור את 10 הנושאים + מסמכי דוגמה לכל אוסף. הוא בטוח להרצה חוזרת (לא ידרוס נושא קיים).

## שלב 3 — פרסום כללי האבטחה

Firebase Console → **Firestore Database** → לשונית **Rules** → הדביקי את כל תוכן `firestore.rules` → **Publish**.

הכללים אוכפים: רק המורה (motiml77@gmail.com) כותבת תוכן וציונים; כל תלמידה רואה/כותבת רק את התשובות שלה; מחוון המבחן נסתר מתלמידות.

---

## ניקוי מסמכי הדוגמה

לאחר שווידאת שהכל עובד, אפשר למחוק מ-Firestore:
- מסמך `classes/class_example`
- `questions/exam_example_q1`, `exam_settings/hw_example`, `grading_rubrics/exam_example`
- `users/example_student_gmail_com` ותת-המסמך ב-`exam_responses/exam_example`

10 נושאי הלימוד האמיתיים יישארו.

---

## הוספת תוכן אחרי ההקמה

הכל דרך ממשק המורה באתר (התחברות עם motiml77@gmail.com):
- **מבחן/עבודה:** כפתור + בנושא → בחירת סוג, שם, ומשך זמן (למבחן).
- **שאלות:** הוספת שאלה → הוראות, **העלאת/הדבקת תמונה (Ctrl+V)**, קוד פתיחה, או **קישור Google Drive**.
- **הוראות בדיקה:** לכל מבחן — הנחיות כלליות + דגשים לשאלה (נשמר ב-`grading_rubrics`).
