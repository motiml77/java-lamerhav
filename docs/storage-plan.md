# אחסון קבצים — תכנון (Firebase Storage לכל בית ספר + תיקייה גלובלית)

מסמך תכנון. נכתב לפני המימוש, כדי שלא נגלה בעיות אחרי שיהיו קבצים אמיתיים במערכת.

---

## 0. הממצא שחוסם — ותשובה שצריכה להגיע ממך

בדקתי בפועל מול הפרויקט `exams-a93fb`:

| בדיקה | תוצאה |
|---|---|
| Firebase Storage API | הפעלתי — עכשיו פעיל |
| דלי (bucket) קיים | **אין** |
| יצירת דלי | **נכשלה**: `The billing account for the owning project is disabled in state absent` |

**המשמעות**: הפרויקט על תוכנית Spark (חינם, בלי כרטיס אשראי). מאז אוקטובר 2024 Firebase Storage דורש תוכנית **Blaze** — תשלום-לפי-שימוש עם מכסה חינמית.

**מה זה אומר בכסף בפועל**: המכסה החינמית ב-Blaze היא 5GB אחסון ו-1GB הורדות ביום. לפי סדרי הגודל שלך — כמה מאות קבצים לבית ספר — זה **₪0 בחודש**. אבל צריך לחבר כרטיס אשראי לפרויקט, וזו החלטה שלך ולא שלי. אפשר גם להגדיר תקציב-התראה של ₪20 כדי שלא תהיה הפתעה.

**עד שתחליט** — המערכת ממשיכה לעבוד בדיוק כמו היום (קבצים בתוך Firestore, עד ~700KB). כל התכנון למטה מוכן ליישום ברגע שיהיה דלי.

---

## 1. האילוץ שקובע את כל התכנון

זו הנקודה שגורמת לרוב הפרויקטים להיתקע מאוחר:

> **חוקי Firebase Storage לא יכולים לקרוא מ-Firestore.**

בחוקי Firestore אפשר `get()` על מסמך אחר — ככה בניתי את הבידוד בין בתי הספר (החוק קורא את `schools/{slug}` ובודק מי המורה). **בחוקי Storage אין את היכולת הזו בכלל.** מה שזמין שם: `request.auth` (uid, אימייל), נתוני הקובץ (גודל, סוג), ומטא-דאטה על האובייקט.

כלומר: אי אפשר לשאול בתוך חוקי Storage "האם המשתמש הזה הוא המורה של בית ספר X?" — כי התשובה יושבת ב-Firestore.

### שלוש דרכים לפתור

| | איך | יתרון | חיסרון |
|---|---|---|---|
| **א. Custom Claims** | טוקן ההתחברות נושא `school: 'tzvia'`. החוק בודק `request.auth.token.school == slug` | בידוד מלא ואמיתי | דורש צד-שרת שמזריק את ה-claim (Admin SDK) בעת פתיחת בית ספר / החלפת מורה |
| **ב. חוקים פרגמטיים** | קריאה למחוברים; כתיבה תחת `schools/{slug}/` לכל מחובר, עם תקרת גודל וסוג; **מחיקה** רק למעלה המקורי או למנהל | עובד מיד, בלי צד-שרת | תלמידה יכולה תיאורטית להעלות קובץ לתיקייה של בית ספר אחר (זבל בלבד — לא תוכל להציג אותו, כי Firestore חוסם, ולא תוכל למחוק קבצים של אחרים) |
| **ג. העלאה דרך ה-Worker** | הקובץ עובר ל-Cloudflare Worker, שמאמת מול Firestore וכותב עם הרשאות אדמין | מאובטח לגמרי, בלי claims | ה-Worker נושא את הקבצים — עלות, השהיה, ומגבלת גודל בקשה |

**המלצה**: להתחיל ב-**ב'** (עובד ביום הראשון, הסיכון תחום ולא הרסני), ולעבור ל-**א'** כשיהיה יותר מבית ספר אחד-שניים אמיתיים. הקוד שאכתוב יהיה זהה בשני המקרים — משתנה רק קובץ החוקים.

---

## 2. מבנה התיקיות

```
gs://exams-a93fb.firebasestorage.app/
├── platform/                              ← גלובלי, לכל בתי הספר
│   └── topics/{topicKey}/{fileId}.{ext}      כותב: המנהל הראשי בלבד
└── schools/{slug}/                        ← פרטי לבית ספר
    ├── topics/{classId}/{fileId}.{ext}       כותב: המורה של אותו בית ספר
    └── questions/{examId}/{qNum}/{fileId}    (בהמשך — צרופות לשאלות)
```

**למה בדיוק ככה:**

- המבנה **מראה כמו ב-Firestore**. אותו `slug`, אותה היררכיה — כך החוקים סימטריים, וקל לאתר/למחוק/לתמחר את כל מה ששייך לבית ספר אחד.
- `{topicKey}` הוא **מזהה לטיני יציב** של הנושא (`loops`, `arrays`), ולא הכותרת העברית. אם מורה תשנה את שם הנושא — הנתיב לא נשבר, ואין בעיות קידוד בשמות תיקיות.
- `{fileId}` נוצר על ידינו: `{חותמת-זמן}_{אקראי}.{סיומת}`. **לעולם לא שם הקובץ של המשתמש** — שמות עבריים, רווחים ותווים כמו `../` הם מקור קלאסי לתקלות.

---

## 3. מה נשמר ב-Firestore (מקור האמת)

Storage מחזיק בייטים. **Firestore נשאר מקור האמת** — הוא מה שהאפליקציה קוראת:

```js
{
  id, type: 'file',
  title: 'חוברת תרגול',
  fileName: 'חוברת תרגול סופית.pdf',   // המקורי — לתצוגה בלבד
  storagePath: 'schools/tzvia/topics/class_x/1712345678_a3f9.pdf',
  downloadUrl: 'https://firebasestorage.googleapis.com/...',
  size: 2481920, contentType: 'application/pdf',
  uploadedBy: 'teacher@x.com', uploadedAt: '2026-...',
  // dataUri — נשאר אופציונלי לפריטים הישנים
}
```

**תאימות לאחור**: הרינדור יבדוק `downloadUrl` ואם אין — `dataUri`. כל מה שכבר הועלה ימשיך לעבוד, בלי מיגרציה כפויה.

---

## 4. חמש התקלות הקלאסיות — ואיך התכנון מונע כל אחת

**1. קבצים יתומים.** מוחקים את המסמך ב-Firestore והקובץ נשאר לנצח ותופס מקום.
→ סדר המחיקה: **קודם Storage, אחר כך Firestore**. אם האובייקט כבר לא קיים — ממשיכים. בנוסף סקריפט השוואה תקופתי שמאתר אובייקטים בלי מסמך מתאים.

**2. שמות קבצים מסוכנים.** `../../etc` או שם עברי עם רווחים.
→ שם הקובץ בנתיב נוצר אצלנו. המקורי נשמר ב-Firestore לתצוגה בלבד.

**3. דליפה בין בתי ספר.** קובץ של בית ספר אחד נגיש לאחר.
→ הנתיב תמיד מתחיל ב-`schools/{slug}/`, והחוק נועל את מקטע ה-slug. מחיקה — רק המעלה או המנהל.

**4. עלות שמתפוצצת.** מישהו מעלה 4GB.
→ תקרת גודל **בתוך החוקים** (לא רק בצד הלקוח — שם קל לעקוף): 20MB לקובץ. רשימת סוגים מותרים (תמונות, PDF, Office). מונה שימוש למסמך בית הספר.

**5. CORS.** קריאה ישירה ל-`gs://` נחסמת בדפדפן.
→ תמיד `getDownloadURL()`. לא נוגעים ב-URL של הדלי ישירות.

**ובונוס — מחיקת בית ספר**: ל-GCS אין "מחק תיקייה" אטומי מהלקוח. מחיקה רקורסיבית תיעשה רק בסקריפט הניהולי, אחרי אישור כפול.

---

## 5. טיוטת חוקי Storage (גרסה ב' — פרגמטית)

```javascript
rules_version = '2';
service firebase.storage {
  match /b/{bucket}/o {
    function signedIn()   { return request.auth != null; }
    function isBoss()     { return signedIn()
                                && request.auth.token.email == 'motiml77@gmail.com'
                                && request.auth.token.email_verified == true; }
    function okSize()     { return request.resource.size < 20 * 1024 * 1024; }
    function okType()     { return request.resource.contentType.matches('image/.*')
                                || request.resource.contentType == 'application/pdf'
                                || request.resource.contentType.matches('application/vnd.*'); }
    function mine()       { return resource.metadata.uploaderUid == request.auth.uid; }

    // גלובלי — המנהל הראשי בלבד
    match /platform/{allPaths=**} {
      allow read:  if signedIn();
      allow write: if isBoss() && okSize() && okType();
    }

    // פרטי לבית ספר
    match /schools/{slug}/{allPaths=**} {
      allow read:   if signedIn();
      allow create: if signedIn() && okSize() && okType();
      allow update: if isBoss() || (signedIn() && mine());
      allow delete: if isBoss() || (signedIn() && mine());
    }

    match /{allPaths=**} { allow read, write: if false; }
  }
}
```

בגרסה א' (custom claims) משתנה רק שורה אחת: `create` יידרוש `request.auth.token.school == slug`.

---

## 6. סדר המימוש כשתאשר

1. שדרוג ל-Blaze + הגדרת התראת תקציב (אתה, בקונסולה — דקה)
2. יצירת הדלי וקישורו ל-Firebase (אני)
3. פרסום חוקי Storage + בדיקות (אני)
4. טעינת `firebase-storage-compat.js` והחלפת נתיב ההעלאה ב-`TopicMediaSection` וב-AdminConsole (אני)
5. תאימות לאחור לפריטים הישנים + סקריפט השוואה לקבצים יתומים (אני)

**מה שלא משתנה**: הקישורים ליוטיוב, הערות הטקסט, וכל התוכן שכבר קיים. Storage נוגע רק בקבצים.
