# בידוד בין בתי ספר — דוח ממצאים

## מה בטוח

רשימה קצרה של מה שאכן מבודד, לפי הממצאים בלבד:

- **העץ החדש `schools/{slug}`** — `isMember` ו-`isActiveUser` נאכפים על `classes`, `questions`, `exam_settings`, `media`, `practice`, `notifications` תחת בית ספר (firestore.rules:230-233, 243-245, 261-290). אין קריאה חוצה-בתי-ספר בענף הזה — למעט מה שמתואר בסעיף "הענקת חברות עצמית" למטה.
- **פרופילי תלמידות בעץ החדש** — `allow list` דורש `canManage` (firestore.rules:307), ולכן מספר התלמידות ורשימת השמות/האימיילים אינם ניתנים למיפוי, וגם `activity` לא דולף.
- **`users` באוספי השורש** — `get` מוגבל למסמך של המשתמשת עצמה, `list` למורה בלבד (firestore.rules:73-77). רשימת התלמידות של lamerhav אינה נחשפת.
- **`grading_rubrics` בשורש** (firestore.rules:68-70) ו-**`activity` בשורש** (180-185) — מוגבלים ל-`isTeacher` ואינם חלק מהדליפה.
- **תשובות תלמידות** — מוגנות ב-`ownsExisting` (firestore.rules:98-100). תוכן התשובות האישיות אינו נקרא בין בתי ספר.
- **`allow list` על `schools`** — חסום ל-`isSuperAdmin` בלבד (firestore.rules:251). אי אפשר למפות את כל בתי הספר במכה אחת (אבל ראי הדליפה מס' 3 — ההגנה הזו נשענת על סודיות ה-slug, שאינו סודי).
- **כתיבה לאוספי השורש** — `isTeacher` בלבד (firestore.rules:146, 166). החשיפה בשורש היא קריאה בלבד.
- **מסלולים שאינם מגיעים לקוד כלל** — `/Lamerhav` (אות גדולה) ו-`/x` (תו אחד) נופלים ב-404 ב-CDN לפני הרצת הקוד, כי ה-rewrite הוא `^/[a-z0-9][a-z0-9-]{1,28}[a-z0-9](/app)?$`. `/` מקבל redirect 307 ל-`/lamerhav` (vercel.json:5).

---

## מה דולף

### 1. חברות בבית ספר ניתנת להענקה עצמית — `isMember` בודק קיום מסמך, לא אישור
**`firestore.rules:230`** — החמור מכולם.

**מה חוצה:** כל התוכן של כל בית ספר פעיל בעץ החדש — `classes` (כולל מערך `exams` המלא: מספר המבחנים ושיעורי הבית, אותו מונה שמוצג ב-/manage), `questions` (שאלות המבחנים עצמן, לפני המבחן), `exam_settings`, `notifications`, `media`, `practice`. בנוסף — יכולת כתיבה של `exam_responses`/`homework_responses` בשם התוקף, כלומר זיהום דוח המורה.

**שחזור:**
1. להתחבר עם חשבון Google כלשהו — לא נדרש שיוך לשום בית ספר.
2. להשיג slug של בית ספר פעיל (הדליפה מס' 3 מאשרת שה-slug קיים ופעיל, ללא התחברות בכלל).
3. POST ל-`schools/<slug>/users/<email-של-התוקף-עם-נקודות-מוחלפות>` עם `{email: <שלו>, approved: false}` — נכתב בהצלחה, כי `canRegister(school)` הוא `isSignedIn() && isActive(school)` בלבד (firestore.rules:237-239, 309-310).
4. מהרגע הזה `isMember` מחזיר true → `isActiveUser` מחזיר true → שורות 261, 265, 269, 277, 282, 287 פותחות קריאה מלאה. אין צורך באישור מורה.

`memberId()` (firestore.rules:224) משתמש ב-replace עם regex גלובלי, ולכן תואם בדיוק ל-`safeEmail` שב-app.html:969 — ה-`exists()` אכן נדלק.

הרצף הזה **מאומת אמפירית**: `scripts/e2e-isolation-check.js:110-112` מריץ את ההרשמה העצמית ומצפה ל-200, ושורות 114-116 מצפות ל-200 על קריאת נושא מיד אחריה. הבדיקה הקיימת מקבעת את הבאג כהתנהגות רצויה.

**תיקון:**
```
function isMember(school) {
  return isSignedIn()
    && exists(/databases/$(database)/documents/schools/$(school)/users/$(memberId()))
    && get(/databases/$(database)/documents/schools/$(school)/users/$(memberId()))
         .data.get('approved', false) == true;
}
```
`Map.get(key, default)` קיים ב-Firestore rules, ולכן העטיפה תקינה גם כשהשדה חסר. בנוסף — לצמצם את `canRegister` כך שיאפשר יצירה רק כשאין עדיין מסמך (`resource == null`), ולעדכן את `scripts/e2e-isolation-check.js:115-116` שיצפה ל-403 במקום 200. שימי לב שהתיקון ישלול גישה גם מתלמידה אמיתית שטרם אושרה — וזו בדיוק הכוונה.

---

### 2. אוספי השורש (legacy של lamerhav) פתוחים לכל משתמש מחובר בעולם
**`firestore.rules:49`** (וכן 54, 60, 141, 146, 153, 160, 165-167)

**מה חוצה:** כל התוכן הלימודי של lamerhav — `classes` (כולל מערך `exams` המלא: כותרות כל המבחנים והעבודות, כל השאלות, הניסוחים, הניקוד, `starterCode`, השכבה ומצב ההצגה), `questions`, `exam_settings`, `media`, `practice`, `platform_questions`, `platform_media`, `platform_lessons`. הכל ב-`allow read: if isSignedIn()` — כלומר `get` וגם `list` לכל בעל חשבון Google, בלי שום בדיקת שיוך.

**שחזור — שני מסלולים:**

*א. דרך הדפדפן (מסלול הלגסי בקוד):*
1. תלמידה של בית ספר B מחוברת ב-Google. `auth.setPersistence(LOCAL)` (app.html:680) שומר את הסשן ברמת ה-origin — משותף לכל הכתובות באותו אתר.
2. באותו דפדפן היא פותחת `https://<site>/app.html`. `__seg[0]` הוא `'app.html'`, שמכיל נקודה ולכן נכשל ב-`SLUG_PATTERN` (app.html:523-527) → `SCHOOL_SLUG=null`.
3. `Root` מחזיר `<App />` ישירות (app.html:**12942**) בלי שום שער בית ספר.
4. האפקט ב-app.html:7509-7523 רץ אוטומטית ברגע ש-`user` קיים, ללא כל תלות ב-`approved` → `DataService.loadClasses()` → `col('classes')` נופל לענף ה-else (app.html:912) → `db.collection('classes')` = אוסף השורש.
5. ה-hooks מוצהרים לפני ה-return המוקדם, ולכן `StudentNameScreen`/`PendingApprovalScreen` (app.html:8467-8479) אינם מונעים את ההורדה — הנתונים כבר בזיכרון ובלשונית ה-Network. הם אף נרשמים ל-console (app.html:948-952).

אותה נפילה קורית בכל slug שמור שתואם את הרג'קס: `/docs`, `/admin`, `/login`, `/assets`, `/uploads`, `/promo`, `/static`, `/index`, `/api` — כולם עוברים rewrite ל-app.html (vercel.json:9) ואז נפסלים ב-`RESERVED_SLUGS` (app.html:523), בלי מסך "בית ספר לא נמצא".

*ב. ישירות מול ה-SDK/REST (חמור יותר, לא תלוי ב-UI):* כל חשבון Google מחובר יכול לקרוא GET/LIST ישירות מול `documents/classes`, `documents/questions`, `documents/exam_settings`, `documents/media`, `documents/practice`. מסך ה-UI אינו גבול אבטחה כלל.

**דיוק לגבי מצב הנתונים:** מאז ששלב 5 רץ, מסמך `schools/lamerhav` קיים, ולכן lamerhav כותב לעץ החדש והשורש הוא **תצלום מלא וקפוא** מרגע המיגרציה — לא עותק חי מתעדכן. עדיין דליפה מלאה של תוכן בית ספר A. הנתונים לא נמחקו: `scripts/migrate-lamerhav.js:4` ("העתקה בלבד, שום מחיקה"), `docs/blaze-checklist.md:418` ("שלבים 1-5 בוצעו... שלב 6 לא"), וההערה ב-firestore.rules:172-173, 189-190.

**תיקון:**
1. **להשלים את שלב 6** — לאמת שהעותק ב-`schools/lamerhav` מלא (ראי `docs/blaze-checklist.md:422` לגבי `practice`/`activity`), למחוק את הנתונים באוספי השורש, ולמחוק את בלוקי ה-match שדולפים (49, 54, 60, 141, 146, 153, 160, 165) כך שייפלו לכלל ברירת המחדל `allow read, write: if false` (firestore.rules:355). `grading_rubrics` (68-70) ו-`activity` (180-185) כבר מוגבלים ל-`isTeacher` — מחיקתם אינה דחופה.
2. **עד שזה קורה** — להחליף את `isSignedIn()` בשורות 49, 54, 60, 146, 166 בבדיקת חברות אמיתית ב-lamerhav.
3. **app.html:12942** — כתובת בלי slug תקין חייבת להחזיר `GateScreen` "בית ספר לא נמצא" במקום `<App />`, בדיוק כמו slug לא קיים.
4. **app.html:910-912** — להפוך את `col()` ל-fail-closed: אם אין `SCHOOL_SLUG` — לזרוק שגיאה, לא להחזיר `db.collection(name)`.
5. **vercel.json / firebase.json** — redirect קבוע מ-`/app.html` ומכל `RESERVED_SLUG` אל `/`.

---

### 3. מסמך בית הספר נקרא ללא התחברות — אימייל המורה וסיבת ההשהיה דולפים
**`firestore.rules:250`** — `allow get: if true;`

**מה חוצה:** לכל אדם באינטרנט, ללא טוקן כלל: `name` (שם בית הספר), `teacherName`, `teacherEmail`, `createdBy` (אימייל המנהל הראשי), `createdAt`, וכן `suspendedAt` ו-`suspendReason` — הערת ההשהיה הפנימית שהמנהל הקליד ב-prompt חופשי (app.html:12193-12195), למשל "לא שילמו".

**שחזור:**
1. GET ל-`https://firestore.googleapis.com/v1/projects/exams-a93fb/databases/(default)/documents/schools/<slug>` — בלי טוקן, בלי התחברות.
2. ה-slug אינו סוד: הוא הכתובת הפומבית שהמורה מחלקת לתלמידות (`location.origin + '/' + slug`, app.html:12183), והוא נגזר משם בית הספר בתעתיק (`suggestSlug`, app.html:12078). סורק יכול להריץ מילון תעתיקים על מרחב `SLUG_PATTERN` = `^[a-z0-9][a-z0-9-]{1,28}[a-z0-9]$` (3-30 תווים, app.html:527).
3. גם ללא ניחוש — האפליקציה עצמה מושכת את המסמך המלא לדפדפן של כל מבקר ב-app.html:12823 ושומרת אותו ב-state (12825). כל תלמידה רואה את השדות בכרטיסיית Network או ב-React state.

`allow list` חסום נכון (firestore.rules:251), אבל ההגנה הזו נשענת כולה על סודיות ה-slug — והוא בדיוק ההפך מסודי.

**תיקון:** לפצל את מסמך בית הספר לשניים.
- ציבורי דק `schools/{slug}` — רק `slug`, `name`, `status`, `settings.subtitle`, `settings.grades`.
- פרטי `schools/{slug}/private/meta` — `teacherEmail`, `teacherName`, `createdBy`, `suspendReason`, `suspendedAt`, עם `allow read: if canManage(school)`.

`resource.data.keys().hasOnly([...])` אינו זמין לאכיפה על קריאה, ולכן הפיצול למסמכים הוא הפתרון היחיד האמיתי. יש לעדכן בהתאם את **app.html:12823** (SchoolGate — יקרא רק את הציבורי) ואת **app.html:12111-12116** (AdminConsole `loadSchools` — יקרא בנוסף את מסמכי ה-private).

---

### 4. `lsKey` מוותר על המרחב כשאין slug — `/app.html` הוא מרחב localStorage גלובלי
**`app.html:546`** — `const lsKey = (k) => SCHOOL_SLUG ? (SCHOOL_SLUG + ':' + k) : k;`

**מה חוצה:** כל המצב המקומי — טיימר מבחן, קוד ג'אווה שמור של תרגול, רשימת מועדפים (שכוללת `classTitle`/`examTitle`/`qTitle`, כלומר שמות נושאים ומטלות), דגלי סגירת פרסומת ודגלי התראות שנקראו. שש משפחות המפתחות, 8 קריאות ל-`lsKey` בסך הכל: `visit_` (1340), `ad_popup_` (3841), `practice_` (3883), `java_favorites_v2_` (7375), `java_notif_seen_` (7409), `exam_start_` (8081, 8095, 9391).

**שחזור:**
1. תלמידה של בית ספר A על מחשב משותף (מעבדת מחשבים / מחשב משפחתי) פותחת `/app.html` — או כל slug שמור שתואם את הרג'קס (`/docs`, `/admin`, `/login`, `/assets`, `/uploads`, `/promo`, `/static`, `/index`, `/api`).
2. `SCHOOL_SLUG=null` → `lsKey` מחזיר את המפתח חשוף, ללא קידומת.
3. תלמידה של בית ספר B על אותו דפדפן פותחת גם היא `/app.html` — וקוראת/דורסת את אותם מפתחות בדיוק.

**סייג להיקף:** הזליגה דורשת ש**שתי** התלמידות יהיו על אותו דפדפן פיזי **ושתיהן** ייכנסו ל-`/app.html`. תלמידה שנשארת בכתובת ה-slug שלה לעולם לא כותבת למרחב החשוף. זו זליגה בין משתמשות על מכונה משותפת — לא שאיבה של מסד בית ספר. החשיפה החוצה-בית-ספרית האמיתית ב-`/app.html` מגיעה מחוקי אוספי השורש (דליפה מס' 2), לא מ-`lsKey`.

**תיקון:** אל תיתני ל-`lsKey` ליפול חזרה למפתח חשוף:
```js
const lsKey = (k) => (SCHOOL_SLUG || 'legacy') + ':' + k;
```
כדי לא לאבד מצב קיים של תלמידות למרחב, להריץ מיגרציה חד-פעמית שמעתיקה כל מפתח ישן ללא קידומת אל `'legacy:'+key`. במקביל — לסגור את הדלת עצמה (אותו תיקון של app.html:12942 מדליפה מס' 2).

---

### הערה על `setPersistence`
`auth.setPersistence(LOCAL)` (app.html:680) מסומן כסיכון ולא כדליפה עצמאית, ובצדק: הוא אינו המאפשר. גם בלעדיו התלמידה פשוט תתחבר שוב עם אותו חשבון Google באותו עמוד — הוא רק חוסך לה קליק. הפגם האמיתי הוא בחוקי אוספי השורש (דליפה מס' 2). התיקון שייך שם, לא כאן.

---

## מה לבדוק ידנית

בדיקות שניתוח סטטי אינו יכול להכריע:

1. **האם הנתונים באוספי השורש עדיין קיימים בפועל.** הניתוח מסתמך על `scripts/migrate-lamerhav.js:4`, `docs/blaze-checklist.md:418` וההערה ב-firestore.rules:172-173. יש לפתוח את קונסולת Firestore ולאשר בעיניים ש-`classes`, `questions`, `exam_settings`, `media`, `practice` בשורש מכילים מסמכים — ולספור אותם.

2. **האם העותק ב-`schools/lamerhav` מלא.** לפני מחיקת השורש, להשוות מסמך-מסמך בין `classes`/`questions`/`exam_settings`/`media`/`practice` בשורש ובין `schools/lamerhav/...`. `docs/blaze-checklist.md:422` מסמן ספק ספציפי לגבי `practice` ו-`activity` — יש לאמת אותם ידנית.

3. **הרצת הדליפה של השורש מדפדפן חי.** להתחבר ב-`/<schoolB>` עם חשבון Google, לנווט ל-`/app.html`, ולבדוק בלשונית Network האם ירדו מסמכי `classes` של lamerhav, ומה בדיוק הופיע ב-console (app.html:948-952). זה מאמת שהחוקים בפרויקט החי תואמים לקובץ `firestore.rules` שבמאגר.

4. **האם החוקים שפרוסים בפרויקט החי הם החוקים שבקובץ.** כל הממצאים בקטגוריית firestore.rules מניחים שהקובץ במאגר הוא מה שנפרס בפועל. יש להשוות מול הגרסה הפרוסה בקונסולה.

5. **הענקת חברות עצמית מול הפרויקט החי.** `scripts/e2e-isolation-check.js:110-116` כבר מריץ את הרצף ומצפה ל-200 — יש להריץ אותו ולראות שהוא אכן עובר כיום, ולוודא שהתוקף באמת קורא `questions` (שאלות מבחן) ולא רק `classes`.

6. **התנהגות ה-CDN על המסלולים השמורים.** לוודא בדפדפן איזה slug שמור מחזיר 404 ואיזה מגיש את app.html: `/docs`, `/admin`, `/login`, `/assets`, `/uploads`, `/promo`, `/static`, `/index`, `/api`. הניתוח מבוסס על הרג'קס ב-vercel.json ועל היעדר `docs/index.html`, אבל התנהגות ה-CDN בפועל (וקבצים סטטיים שקיימים בבילד) יכולה להשתנות.

7. **גישה ישירה ל-`/app.html` כקובץ סטטי.** `cleanUrls:false` אמור להגיש אותו ישירות — לוודא בדפדפן שהוא אכן נגיש ולא נחסם.

8. **זליגת ה-localStorage על מכונה משותפת.** לפתוח `/app.html` בשני חשבונות שונים באותו פרופיל דפדפן ולבדוק ב-DevTools → Application → Local Storage שהמפתחות (`exam_start_*`, `practice_*`, `java_favorites_v2_*`) אכן משותפים וללא קידומת.