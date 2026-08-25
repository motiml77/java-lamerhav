# מסמך תכנון — הפיכת האתר לפלטפורמה רב־בית־ספרית (Multi-School)

**גרסה:** 1.0 · **תאריך:** 2026-08-25 · **ענף:** `manager` · **סטטוס:** טיוטת תכנון לאישור

---

## 1. המטרה בשורה אחת

להפוך את האתר ממערכת של מורה אחת (motiml77@gmail.com) למערכת שבה **מנהל ראשי** פותח "בתי ספר" לפי דרישה, כל בית ספר מקבל **כתובת משלו** (`הדומיין/school1`), **מורה אחראי משלו**, ו**אזור נתונים מבודד לחלוטין** — עם יכולת השהיה/הפעלה של כל בית ספר מלוח ניהול נוח.

---

## 2. מיפוי המצב הקיים (מה יש היום בקוד)

נקודות העיגון בקוד — אלה המקומות שהשינוי נוגע בהם:

| רכיב | היום | מיקום |
|---|---|---|
| זיהוי המורה | קבוע בקוד: `EDITOR_EMAIL = "motiml77@gmail.com"` | `app.html:492` + עוד ~6 בדיקות ישירות של האימייל |
| אבטחה בשרת | `isTeacher()` משווה לאימייל הקבוע | `firestore.rules:26` |
| אוספי Firestore | כולם ברמת השורש, גלובליים לכל האתר | `classes`, `questions`, `exam_settings`, `grading_rubrics`, `users`, `exam_responses/{examId}/students/{sid}`, `homework_responses/{examId}/students/{sid}`, `notifications` |
| אחסון | אין Firebase Storage — תמונות/PDF נשמרים כ־base64 בתוך מסמכי Firestore | `app.html` (dataUri) |
| בדיקת AI | Cloudflare Worker משותף | `app.html:1550` (`gemini-proxy.motiml77.workers.dev`) |
| אירוח | אתר סטטי (Vercel / Firebase Hosting), SPA אחד בקובץ `app.html` | `firebase.json`, `.gitignore` (`.vercel`) |
| ניתוב | אין ניתוב — `index.html` (נחיתה) → `app.html` (אפליקציה) | — |
| שם המוסד | קבוע בקוד: "אולפנה 'למרחב' · שכבות יא–יב" | `app.html:3935` |

**מסקנה:** אין שום מודל "בעלות" על נתונים. כל השינוי מתרכז בשלושה צירים: (א) ניתוב לפי כתובת, (ב) היררכיית נתונים, (ג) הרשאות ותפעול.

---

## 3. תפקידים (Roles)

| תפקיד | מי | יכולות |
|---|---|---|
| **מנהל ראשי (Super-Admin)** | `motiml77@gmail.com` בלבד (קבוע בקוד ובחוקים, כמו היום) | פתיחת בית ספר, השהיה/הפעלה, החלפת מורה אחראי, צפייה בכל הנתונים, גישה ללוח `/admin` |
| **מורה אחראי (School Teacher)** | אימייל אחד לכל בית ספר, נשמר במסמך בית הספר | כל מה שיש למורה היום — אך ורק בתוך בית הספר שלו: נושאים, מבחנים, שיעורי בית, אישור תלמידות, ציונים, ארכיון |
| **תלמידה** | נרשמת עם Google בתוך כתובת בית הספר שלה | כמו היום — רואה רק את התוכן והתשובות שלה, בתוך בית הספר שלה בלבד |

הערות:
- המנהל הראשי הוא **גם** המורה האחראי של בית הספר הראשון (`lamerhav`) — אין צורך בטיפול מיוחד, זה פשוט אותו אימייל במסמך של אותו בית ספר.
- בגרסה 1 יש **מורה אחראי אחד** לבית ספר. הרחבה לכמה מורים באותו בית ספר — סעיף 12 (עתידי, המבנה כבר יתמוך בזה).

---

## 4. כתובות וניתוב (Routing)

### 4.1 מבנה הכתובות

```
https://<domain>/                → דף נחיתה כללי של הפלטפורמה (או הפניה, ר' 4.4)
https://<domain>/admin           → לוח הניהול של המנהל הראשי
https://<domain>/{school-slug}   → הכניסה של בית הספר (נחיתה + אפליקציה)
```

דוגמה: בית ספר בשם "אולפנת צביה" עם slug `tzvia` → `https://<domain>/tzvia`.

### 4.2 חוקי ה־slug

- נוצר מהשם שהמנהל קובע בעת פתיחת בית הספר, וניתן לעריכה ידנית לפני האישור.
- תבנית: `^[a-z0-9][a-z0-9-]{1,28}[a-z0-9]$` (אותיות לטיניות קטנות, ספרות, מקפים; 3–30 תווים).
- **קבוע לתמיד** אחרי היצירה (שינוי slug = שבירת קישורים ששלחו לתלמידות; לא מאפשרים ב־v1).
- **מילים שמורות** שאסור לשמש כ־slug: `admin`, `app`, `api`, `assets`, `uploads`, `docs`, `login`, `static`, `index`, `firebase-setup`.

### 4.3 מימוש טכני — אתר סטטי, בלי שרת

האתר נשאר SPA סטטי אחד. הניתוב נעשה בשתי שכבות:

**שכבה 1 — rewrites באירוח.** ב־Vercel מוסיפים `vercel.json`:

```json
{
  "rewrites": [
    { "source": "/admin", "destination": "/app.html" },
    { "source": "/:school([a-z0-9-]{3,30})", "destination": "/app.html" },
    { "source": "/:school([a-z0-9-]{3,30})/app", "destination": "/app.html" }
  ]
}
```

(ל־Firebase Hosting יש מקבילה ב־`firebase.json`; נעדכן את שניהם כדי שהריפו יעבוד בכל אירוח.)

**שכבה 2 — פענוח בצד הלקוח.** בתחילת `app.html`:

```js
// "/tzvia" → school="tzvia" ; "/admin" → מצב ניהול ; "/" → נחיתה כללית
const path = location.pathname.split('/').filter(Boolean);
const RESERVED = new Set(['admin','app','api','assets','uploads','docs','login','static','index']);
const IS_ADMIN_ROUTE = path[0] === 'admin';
const SCHOOL_SLUG = (!IS_ADMIN_ROUTE && path[0] && !RESERVED.has(path[0])) ? path[0] : null;
```

לאחר הפענוח, האפליקציה טוענת את `schools/{SCHOOL_SLUG}` מ־Firestore:
- לא קיים → מסך "בית ספר לא נמצא".
- `status: "suspended"` → מסך "בית הספר מושהה, פני למנהל" (וגם החוקים בשרת חוסמים — ר' סעיף 7).
- קיים ופעיל → האפליקציה רצה כרגיל, כשכל הנתונים תחומים לבית הספר.

**חשוב — נכסים סטטיים:** ברגע שהדף מוגש מ־`/tzvia`, כל הפניה יחסית (`codemirror-java.min.js`, `background.png`) נשברת. חלק מהשלב הראשון: לעבור על `index.html` + `app.html` ולהפוך את כל הנתיבים לאבסולוטיים (`/codemirror-java.min.js`).

### 4.4 תאימות לאחור

התלמידות הקיימות מכירות את הכתובת הנוכחית (שורש הדומיין). בזמן המעבר: השורש `/` יפנה אוטומטית ל־`/lamerhav` (בית הספר הקיים). בהמשך, כשיהיו כמה בתי ספר, אפשר להחליף את השורש בדף נחיתה כללי. **החלטה פתוחה** — ר' סעיף 12.

---

## 5. היררכיית הנתונים החדשה ב־Firestore

העיקרון: כל מה שהיום ברמת השורש עובר להיות **תת־אוסף של מסמך בית ספר**. שמות האוספים הפנימיים לא משתנים — כך השינוי בקוד הוא בעיקר "הוספת קידומת לנתיב".

```
schools/{slug}                        ← מסמך בית הספר (המטא־דאטה)
│   name:          "אולפנת צביה"          ← שם תצוגה
│   slug:          "tzvia"                ← זהה ל־ID, לנוחות שאילתות
│   teacherEmail:  "teacher@gmail.com"    ← המורה האחראי (המפתח להרשאות!)
│   teacherName:   "..."
│   status:        "active" | "suspended"
│   suspendedAt / suspendReason           ← לתיעוד
│   createdAt, createdBy
│   settings: { grades: ["יא","יב"], subtitle: "..." }  ← מה שהיום קבוע בקוד
│
├── classes/{classId}                 ← נושאי הלימוד + המבחנים (כמו היום)
├── questions/{questionId}
├── exam_settings/{examId}
├── grading_rubrics/{examId}          ← נשאר חסוי למורה בלבד
├── users/{uid}                       ← התלמידות של בית הספר הזה
├── notifications/{id}
├── exam_responses/{examId}/students/{sid}
└── homework_responses/{examId}/students/{sid}
```

נקודות תכנון:

1. **ה־slug הוא ה־Document ID** של `schools` — חיפוש בית ספר לפי כתובת = קריאת מסמך אחת, בלי שאילתה.
2. **תלמידה שייכת לבית ספר אחד.** הפרופיל שלה חי תחת `schools/{slug}/users`. אם אותו חשבון Google ייכנס לבית ספר אחר — הוא פשוט יירשם שם כתלמידה חדשה (מבודד לגמרי). זה המודל הפשוט והנכון ל־v1.
3. **אין אוסף גלובלי של מורים.** ההרשאה נגזרת ישירות מ־`teacherEmail` שבמסמך בית הספר — מקור אמת אחד, בלי סנכרון כפול.
4. **הנתונים הקיימים** של האולפנה עוברים כמו־שהם ל־`schools/lamerhav/...` (מיגרציה — סעיף 10).

---

## 6. חוקי אבטחה (firestore.rules v2) — טיוטה

העיקרון: כל הפונקציות של היום נשארות, אבל `isTeacher()` הופך מ"אימייל קבוע" ל"האימייל שרשום על מסמך בית הספר", וכל ה־match עטוף ב־`schools/{school}`.

```javascript
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {

    function isSignedIn() { return request.auth != null; }

    // המנהל הראשי — קבוע, כמו היום
    function isSuperAdmin() {
      return isSignedIn()
        && request.auth.token.email == 'motiml77@gmail.com'
        && request.auth.token.email_verified == true;
    }

    // מסמך בית הספר של הבקשה הנוכחית
    function schoolDoc(school) {
      return get(/databases/$(database)/documents/schools/$(school)).data;
    }

    // המורה האחראי של בית הספר הזה
    function isSchoolTeacher(school) {
      return isSignedIn()
        && request.auth.token.email_verified == true
        && schoolDoc(school).teacherEmail == request.auth.token.email;
    }

    // בית ספר פעיל (השהיה נאכפת כאן — בשרת, לא רק בתצוגה)
    function isActive(school) {
      return schoolDoc(school).status == 'active';
    }

    // "מורה" בהקשר של בית ספר: המורה האחראי (רק כשהבי"ס פעיל) או המנהל הראשי
    function canManage(school) {
      return isSuperAdmin() || (isSchoolTeacher(school) && isActive(school));
    }

    // משתמש מחובר בבי"ס פעיל (תלמידות)
    function isActiveUser(school) {
      return isSignedIn() && isActive(school);
    }

    function ownsIncoming() {
      return isSignedIn() && request.resource.data.email == request.auth.token.email;
    }
    function ownsExisting() {
      return isSignedIn() && resource.data.email == request.auth.token.email;
    }

    // ---------- מסמכי בתי הספר עצמם ----------
    match /schools/{school} {
      // כל גולש מחובר יכול לקרוא את המטא־דאטה (שם, סטטוס) — נדרש למסך הכניסה.
      // המסמך לא מכיל דבר רגיש מלבד אימייל המורה.
      allow get: if isSignedIn();
      allow list: if isSuperAdmin();          // רק לוח הניהול מציג את כל הרשימה
      allow create, delete: if isSuperAdmin();
      // עדכון: המנהל הכול; המורה — רק שדות תצוגה, לא teacherEmail/status/slug
      allow update: if isSuperAdmin()
        || (isSchoolTeacher(school) && isActive(school)
            && !request.resource.data.diff(resource.data)
                 .affectedKeys().hasAny(['teacherEmail','status','slug']));

      // ---------- תוכן לימודי ----------
      match /classes/{classId}        { allow read: if isActiveUser(school) || isSuperAdmin();
                                        allow write: if canManage(school); }
      match /questions/{questionId}   { allow read: if isActiveUser(school) || isSuperAdmin();
                                        allow write: if canManage(school); }
      match /exam_settings/{examId}   { allow read: if isActiveUser(school) || isSuperAdmin();
                                        allow write: if canManage(school); }
      match /grading_rubrics/{examId} { allow read, write: if canManage(school); }
      match /notifications/{id}       { allow read: if isActiveUser(school) || isSuperAdmin();
                                        allow write: if canManage(school); }

      // ---------- תלמידות ----------
      match /users/{uid} {
        allow get: if canManage(school)
                   || (isActiveUser(school)
                       && (resource == null || resource.data.email == request.auth.token.email));
        allow list: if canManage(school);
        allow create: if isActiveUser(school) && ownsIncoming()
                      && request.resource.data.approved == false;
        allow update: if canManage(school)
                      || (isActiveUser(school) && ownsExisting() && ownsIncoming()
                          && request.resource.data.approved == resource.data.approved);
        allow delete: if canManage(school);
      }

      // ---------- תשובות (מבחנים ושיעורי בית — אותו מבנה בדיוק כמו היום) ----------
      match /exam_responses/{examId} {
        allow read, write: if canManage(school);
        match /students/{sid} {
          allow read: if canManage(school) || resource == null
                      || (isActiveUser(school) && ownsExisting());
          allow create: if canManage(school)
                        || (isActiveUser(school) && ownsIncoming()
                            && !request.resource.data.keys().hasAny(['sentToStudent','finalGrade']));
          allow update: if canManage(school)
                        || (isActiveUser(school) && ownsExisting() && ownsIncoming()
                            && !request.resource.data.diff(resource.data)
                                  .affectedKeys().hasAny(['sentToStudent','finalGrade']));
          allow delete: if canManage(school);
        }
      }
      match /homework_responses/{examId} {
        // זהה ל-exam_responses (מועתק אחד־לאחד)
        allow read, write: if canManage(school);
        match /students/{sid} {
          allow read: if canManage(school) || resource == null
                      || (isActiveUser(school) && ownsExisting());
          allow create: if canManage(school)
                        || (isActiveUser(school) && ownsIncoming()
                            && !request.resource.data.keys().hasAny(['sentToStudent','finalGrade']));
          allow update: if canManage(school)
                        || (isActiveUser(school) && ownsExisting() && ownsIncoming()
                            && !request.resource.data.diff(resource.data)
                                  .affectedKeys().hasAny(['sentToStudent','finalGrade']));
          allow delete: if canManage(school);
        }
      }
    }

    match /{document=**} { allow read, write: if false; }
  }
}
```

הערות טכניות חשובות:

- **עלות `get()` בחוקים:** כל בדיקת הרשאה קוראת את מסמך בית הספר (קריאה מחויבת). Firestore עושה לזה cache בתוך אותה בקשה, והעומס הצפוי (עשרות תלמידות לבית ספר) זניח. זו הדרך הנכונה בלי Cloud Functions / Custom Claims — שידרשו backend שאין היום. אם אי־פעם זה יכאב, משדרגים ל־Custom Claims (סעיף 12).
- **השהיה נאכפת בשרת:** `isActive()` בכל כלל. בית ספר מושהה = גם המורה וגם התלמידות חסומים בפועל, לא רק במסך. המנהל הראשי תמיד עובר.
- החוקים החדשים **אינם** כוללים את האוספים הישנים ברמת השורש — אחרי המיגרציה הם מתים אוטומטית (ברירת המחדל חוסמת הכול).

---

## 7. סמנטיקת "השהיה" (Suspend)

- פעולה של המנהל הראשי בלבד, מלוח `/admin`, עם אישור ("האם להשהות את X?") ושדה סיבה אופציונלי.
- אפקט: `status: "suspended"` במסמך בית הספר. **שום נתון לא נמחק.**
- מה רואים: תלמידה/מורה שנכנסים לכתובת בית הספר מקבלים מסך סטטי מעוצב: "בית הספר מושהה. לפרטים פנו למנהל המערכת." בלי אפשרות התחברות לנתונים (החוקים חוסמים בכל מקרה).
- הפעלה מחדש: כפתור אחד, `status: "active"`, הכול חוזר מיד.
- מחיקה אמיתית של בית ספר — **לא ב־v1** מהלוח (מסוכן מדי; base64 בתוך המסמכים = הרבה דאטה). אם צריך, מוחקים ידנית מהקונסול. הלוח יציג רק "השהיה".

---

## 8. לוח הניהול של המנהל הראשי (`/admin`)

מסך חדש בתוך `app.html` (אותו SPA, מצב תצוגה נוסף), נגיש רק כשמחובר `motiml77@gmail.com` (וגם החוקים אוכפים — `list` על `schools` פתוח רק לו).

**מבנה המסך (RTL, באותה שפה עיצובית של האתר):**

1. **כותרת + סיכום:** "ניהול בתי ספר" · סה"כ פעילים / מושהים.
2. **טבלת בתי ספר** — שורה לכל בית ספר:
   - שם · slug (עם כפתור העתקת קישור מלא) · אימייל המורה · סטטוס (תג ירוק/אפור) · תאריך יצירה
   - פעולות: **כניסה כמנהל** (פותח `/{slug}` — המנהל רואה הכול ממילא) · **השהיה/הפעלה** · **עריכה** (שם תצוגה, מורה אחראי, כתוביות)
3. **כפתור "פתיחת בית ספר חדש"** — טופס:
   - שם בית הספר (עברית חופשית)
   - slug (מוצע אוטומטית מתעתיק השם, ניתן לעריכה, ולידציה חיה מול חוקי סעיף 4.2 + בדיקת "כבר תפוס" + מילים שמורות)
   - אימייל המורה האחראי (Gmail — כי ההתחברות היא Google)
   - שם המורה · שכבות (ברירת מחדל יא–יב)
   - אישור → נכתב מסמק `schools/{slug}` → מוצג הקישור המוכן להעתקה ולשליחה למורה.
4. **החלפת מורה אחראי:** עריכת `teacherEmail` במסמך. המורה הקודם מאבד גישה מיידית (החוקים נגזרים מהמסמך), החדש מקבל מיידית. שום דבר אחר לא זז.

זרימת מורה חדש: המנהל שולח לו את הקישור → המורה נכנס עם חשבון Google שתואם ל־`teacherEmail` → האפליקציה מזהה אותו כמורה של בית הספר הזה (אותו קוד שהיום בודק `EDITOR_EMAIL`, רק מול מסמך בית הספר) → רואה בית ספר ריק ומתחיל להקים נושאים (כולל כפתור "יצירת נושאי לימוד מובנים" הקיים).

---

## 9. שינויים באפליקציה (`app.html`) — מה בפועל משתנה

זה השינוי הגדול, אבל הוא **מכני ברובו**:

1. **שכבת נתיב אחת:** להוסיף פונקציה `col(name)` שמחזירה `db.collection('schools').doc(SCHOOL_SLUG).collection(name)`, ולהחליף בה את כל ~45 הקריאות `db.collection('...')`. שינוי חד־פעמי, קל לוודא ב־grep שלא נשאר `db.collection(` ישיר.
2. **זיהוי תפקיד:** במקום `user.email === EDITOR_EMAIL` —
   ```js
   const isSuperAdmin = user.email === SUPER_ADMIN_EMAIL;            // motiml77
   const isTeacher    = isSuperAdmin || user.email === school.teacherEmail;
   ```
   (7 מקומות בקוד לעדכון; `EDITOR_EMAIL` נשאר רק בתור `SUPER_ADMIN_EMAIL`.)
3. **אתחול:** לפני רינדור — טעינת `schools/{SCHOOL_SLUG}`; מסכי "לא נמצא" / "מושהה"; הזרקת שם בית הספר והכתוביות מ־`settings` במקום הטקסט הקבוע (`app.html:3935`).
4. **מסך `/admin`:** קומפוננטה חדשה (סעיף 8), מנותבת לפי `IS_ADMIN_ROUTE`.
5. **נתיבים אבסולוטיים** לנכסים סטטיים (סעיף 4.3).
6. **דף הנחיתה `index.html`:** נשאר בשורש; ה־CTA שלו יפנה ל־`/{slug}` לפי ההחלטה בסעיף 12.
7. **ה־Worker של ה־AI:** ממשיך משותף לכל בתי הספר (אין בו נתונים, רק proxy). נוסיף לבקשות שדה `school` ללוגים/מכסות עתידיות. ללא שינוי צד־שרת ב־v1.

---

## 10. מיגרציית הנתונים הקיימים

מטרה: כל הנתונים של האולפנה עוברים ל־`schools/lamerhav/...` **בלי אובדן ובלי השבתה ארוכה**.

- **כלי:** סקריפט Node עם Admin SDK, באותה תבנית של `firebase-setup/seed.js` הקיים (יש כבר תשתית ו־README לזה). שם: `firebase-setup/migrate-to-schools.js`.
- **מה הוא עושה:**
  1. יוצר את `schools/lamerhav` (name: "אולפנה 'למרחב'", teacherEmail: motiml77@gmail.com, status: active).
  2. מעתיק כל אוסף שורש → תת־האוסף המקביל, **באותם Document IDs**, כולל תת־האוספים `students` של `exam_responses`/`homework_responses`.
  3. מדפיס סיכום ספירות (מקור מול יעד) לכל אוסף — אימות שלם לפני מעבר.
  4. **לא מוחק** את נתוני המקור. המחיקה ידנית, שבועיים אחרי שהכול יציב.
- **סדר המעבר (חלון של שעה, מחוץ לשעות פעילות):**
  1. הרצת המיגרציה (העתקה — האתר הישן ממשיך לעבוד על הנתונים הישנים).
  2. פריסת הקוד החדש + פרסום `firestore.rules` v2 (בפעולה אחת — הקוד החדש קורא מהמבנה החדש).
  3. בדיקת עשן כמורה וכתלמידה (חשבון בדיקה) על `/lamerhav`.
  4. תקלה? → מחזירים את הפריסה והחוקים הקודמים; נתוני המקור לא נגעו. סיכון נמוך.
- **חלון הקפאה:** בין שלב 1 ל־3 אין לכתוב נתונים (תשובת תלמידה שתיכתב לישן לא תועתק). לכן מחוץ לשעות פעילות + הודעה מראש.

---

## 11. שלבי ביצוע (סדר עבודה מוצע, כל שלב = קומיט/ים בענף `manager`)

| שלב | תוכן | תלות | היקף |
|---|---|---|---|
| **0** | מסמך זה + אישור שלך על ההחלטות הפתוחות (סעיף 12) | — | ✔ הענף הזה |
| **1** | תשתית ניתוב: `vercel.json`, עדכון `firebase.json`, נתיבים אבסולוטיים, פענוח slug, מסכי "לא נמצא"/"מושהה" | 0 | קטן |
| **2** | שכבת הנתונים: `col()` + החלפת כל הקריאות + זיהוי תפקיד מול מסמך בית הספר | 1 | בינוני |
| **3** | `firestore.rules` v2 (טיוטת סעיף 6, סופית) | 2 | קטן |
| **4** | לוח `/admin`: רשימה, פתיחה, השהיה/הפעלה, החלפת מורה | 2 | בינוני |
| **5** | סקריפט מיגרציה + הרצה על `lamerhav` + בדיקות עשן | 2,3 | קטן |
| **6** | מעבר חי (סעיף 10) ואז ניקוי: מחיקת אוספי השורש הישנים | הכול | קטן |

בדיקות בכל שלב: כניסה כמנהל / כמורה של בי"ס אחר / כתלמידה; ניסיון גישה צולבת בין בתי ספר (חייב להיחסם ע"י החוקים); בית ספר מושהה.

---

## 12. החלטות פתוחות — דרוש אישור שלך

1. **מה בשורש הדומיין `/`?**
   - א. הפניה אוטומטית ל־`/lamerhav` (הכי שקוף לתלמידות הקיימות) ← **מומלץ ל־v1**
   - ב. דף נחיתה כללי של הפלטפורמה עם "בחרי בית ספר" — נעבור לזה כשיהיו כמה בתי ספר.
2. **דומיין:** נשארים על הדומיין הנוכחי של Vercel, או קונים דומיין (למשל `java-bagrut.app`)? (טכנית זהה; רק DNS.)
3. **מכסות AI:** ה־Worker משותף ועל חשבונך. כשבתי ספר נוספים יצטרפו — להשאיר משותף, או להוסיף מפתח/מכסה לכל בית ספר? (v1: משותף + לוג לפי school.)
4. **כמה מורים לבית ספר:** המבנה תומך בהרחבה עתידית (להפוך את `teacherEmail` ל־`teacherEmails` — שינוי של שורה בחוקים ובקוד). לא נבנה עכשיו אלא אם תרצה.
5. **שדרוג עתידי ל־Custom Claims** (אם מספר בתי הספר יגדל מאוד): מצריך Cloud Functions. לא נדרש עכשיו — מתועד כאן כדי שנדע את נתיב ההמשך.

---

## 13. מה מסמך זה *לא* משנה

- אין שינוי בחוויית התלמידה או במסכי המורה הקיימים (מעבר לזה שהם תחומים לבית הספר).
- אין הכנסת backend/שרת — האתר נשאר סטטי לחלוטין.
- אין שינוי בדגם ה־AI, בעורך הקוד, או בתוכן הלימודי.
- אין מחיקת נתונים בשום שלב אוטומטי.

---

*המסמך נכתב על בסיס קריאת הקוד הקיים בענף `master` (קומיט `41a9bbf`). לאחר אישור ההחלטות הפתוחות — מתחילים בשלב 1.*
