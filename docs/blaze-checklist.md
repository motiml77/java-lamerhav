# רשימת מעבר ל-Blaze + Firebase Storage — פרויקט `exams-a93fb`

> נגזר מביקורת קוד מאומתת. מספרי שורות מתייחסים ל-`app.html` (12,306 שורות) אלא אם צוין אחרת.
> כלל-על: **דלי → חוקים → קוד → קבצים אמיתיים.** אין להעלות קובץ ראשון לפני שהחוקים פורסמו ושכל אתרי הקריאה תוקנו.

---

## שלב 0 — החלטות שחייבות להיסגר לפני הכל

**1. [חובה] החלטה: מודל הגישה לקבצים.**
`getDownloadURL()` מחזיר כתובת עם `?alt=media&token=...` שהיא **ציבורית ואנונימית** — מי שמחזיק בקישור מוריד גם בלי התחברות וגם אם בית הספר מושהה. שלוש אפשרויות: (א) לשמור `downloadUrl` במסמך ולקבל את זה (המודל הרווח); (ב) לשמור `storagePath` בלבד ולקרוא `getDownloadURL()` ברינדור; (ג) `getBlob()` + Object URL (אכיפה מלאה, בלי CDN). **ההמלצה מהביקורת: (ב).** ההחלטה חייבת להתקבל לפני שנכתב שדה `downloadUrl` ראשון — שינוי בדיעבד = מיגרציה של כל מסמכי `media`/`platform_media` + ביטול טוקנים ישנים.

**2. [חובה] החלטה: שמות שדות במסמך.**
`docs/storage-plan.md:78` מתכנן `uploadedBy`/`uploadedAt`; הקוד בפועל כותב `createdBy` (3944, 11901) ו-`createdAt` (1122). לבחור אחד — אחרת ייווצרו שני זוגות שדות מקבילים וסקריפט הקבצים היתומים יצטרך להכיר את שניהם.

**3. [חובה] החלטה: גרסת חוקי Storage.**
הקביעה ב-`docs/storage-plan.md:29` ("חוקי Firebase Storage לא יכולים לקרוא מ-Firestore") **שגויה** — Cross-service Rules מאפשרים `firestore.get()`/`firestore.exists()` מתוך `storage.rules`. לכן "גרסה ב' הפרגמטית" (§1 שורות 31–43, המלצה בשורה 43) מיותרת, ואופציה א' (Custom Claims) מיותרת לגמרי. לאמץ שיקוף מלא של `isSchoolTeacher`/`isActive`/`canManage`, ולהשאיר את גרסה ב' רק כ-fallback מתועד. לוודא בפועל שהקריאה עובדת מיד אחרי שהדלי נוצר.

**4. [רשות] החלטה: תמיכה בווידאו.**
היום אין העלאת וידאו כלל (`buildVideoEmbed`, 3509 — רק הטמעת קישור). וידאו הוא הסעיף היחיד שיכול להוציא את הפרויקט מהמכסה החינמית. אם מפעילים — התראת התקציב היא חובה מוחלטת.

**אימות:** שלוש ההחלטות כתובות ב-`docs/storage-plan.md` לפני שנוגעים בקוד.

---

## שלב 1 — קונסולה (ידני, לא ניתן ל-CLI)

**5. [חובה] שדרוג ל-Blaze.**
Firebase Console → `exams-a93fb` → ⚙️ Usage and billing → Details & settings → Modify plan → Blaze + חיבור אמצעי תשלום. זהו החסם היחיד שאינו קוד: יצירת דלי נכשלה ב-`The billing account for the owning project is disabled in state absent` (`docs/storage-plan.md:14-15`). Storage דורש Blaze מאז אוקטובר 2024.

**6. [חובה] התראת תקציב — לפני יצירת הדלי.**
Google Cloud → Billing → Budgets & alerts → CREATE BUDGET, ~₪20, ספי 50%/90%/100%/150%, לסמן **Forecasted spend**, ולוודא שערוץ המייל הוא כתובת שנקראת בפועל.
**להבין: Blaze לא כולל תקרת הוצאה.** התקציב שולח מייל ותו לא — החיוב ממשיך. ב-Spark מכסה שנגמרה עצרה פעולות; ב-Blaze היא הופכת לשורה בחשבונית. זהו השינוי המהותי היחיד במודל הסיכון.

**7. [חובה] יצירת הדלי — בחירת המיקום בלתי הפיכה.**
Console → Build → Storage → Get started. שם הדלי חייב להיות בדיוק `exams-a93fb.firebasestorage.app` — הוא כבר מקובע ב-`app.html:509` (`storageBucket`). את המיקום לבחור זהה ל-Firestore (`gcloud firestore databases describe --database='(default)' --project=exams-a93fb --format="value(locationId)"`); בפועל הקונסולה בדרך כלל תכפה את ה-default GCP resource location של הפרויקט.
**ליצור אך ורק את דלי ברירת המחדל** — המכסה החינמית (≈5GB אחסון, ≈1GB הורדות/יום) חלה עליו בלבד; כל דלי נוסף מחויב מהבייט הראשון. הפרדה בין בתי ספר היא בתיקיות, לפי `docs/storage-plan.md` §2.

**8. [חובה] לפרסם את `storage.rules` באותה ישיבה שבה נוצר הדלי.**
בין רגע היצירה לרגע הפרסום, הדלי פתוח בברירת מחדל ל-`allow read, write: if request.auth != null` — כלומר כל מחובר יכול למחוק כל קובץ של כל בית ספר. (הקובץ עצמו נכתב בשלב 2, לפני ההגעה לקונסולה.)

**9. [רשות] App Check.**
Console → App Check → רישום עם reCAPTCHA v3 (חינם) → Enforce על Firestore ועל Storage. בקוד: `firebase-app-check-compat.js` + `firebase.appCheck().activate(...)` מיד אחרי `initializeApp` (676). היום אין App Check כלל. חובה שבוע במצב Monitoring לפני Enforce, אחרת נחסמות תלמידות אמיתיות. הערה: App Check אינו מגן מפני באג-לולאה בקוד שלך עצמו.

**10. [רשות] בקרת שירותים נלווים.**
Google Cloud → APIs & Services → Enabled APIs. **לא לפרוס אף Cloud Function** "רק כדי לבדוק" — פריסה מפעילה Cloud Build + Artifact Registry, והתמונות צוברות אחסון בתשלום גם אחרי מחיקת הפונקציה. היום אין תיקיית `functions` ואין בלוק `functions` ב-`firebase.json` — נקודת פתיחה נקייה. להפעיל Billing → Reports עם פילוח SKU ולסרוק שבועיים.

**אימות שלב 1:**
```
gcloud beta billing projects describe exams-a93fb            # billingEnabled: true
gcloud storage buckets describe gs://exams-a93fb.firebasestorage.app --project=exams-a93fb
```
ובקונסולה: התקציב מופיע ב-Budgets & alerts עם ערוץ מייל פעיל.

---

## שלב 2 — תצורה וחוקים (חלק גדול מזה אפשר לעשות עוד לפני Blaze)

**11. [חובה] `.firebaserc` — לא קיים בריפו.**
ליצור בשורש:
```json
{ "projects": { "default": "exams-a93fb" } }
```
בלעדיו `firebase deploy --only storage` ייפול על "No project active", או גרוע — יפרוס לפי ה-`activeProject` הגלובלי שנשמר ב-`~/.config/configstore/firebase-tools.json`. הקובץ אינו סודי ונכנס ל-git; הוא כבר מכוסה ע"י `**/.*` ב-`hosting.ignore`.

**12. [חובה] `firebase.json` — הוספת בלוק `storage` אחרי שורה 4.**
```json
  "firestore": { "rules": "firestore.rules" },
  "storage":   { "rules": "storage.rules" },
```
בלעדיו `firebase deploy --only storage` נכשל ב-"no storage config" — והפריסה תעבור בשקט בלי להחיל שום חוק. **לערוך ידנית בלבד**: `firebase init storage` דורש Blaze ודלי קיים (ייכשל היום), ובכל מקרה עלול לדרוס את `hosting.rewrites` ולשבור את הניתוב הרב-בית-ספרי.

**13. [חובה] יצירת `storage.rules` בשורש — לא קיים היום, יש רק טיוטה במסמך.**
בסיס: `rules_version = '2'; service firebase.storage { match /b/{bucket}/o { ... } }`.
**המלכודת התחבירית:** ב-Storage אין wildcard `{database}`, ולכן נתיב ליטרלי:
```
function schoolDoc(slug) {
  return firestore.get(/databases/(default)/documents/schools/$(slug)).data;
}
```
ולא `/databases/$(database)/documents/...` כמו ב-`firestore.rules:185`. `firestore.get()` מחזיר Resource — ניגשים דרך `.data`, ועל מסמך שאינו קיים הוא מחזיר null והגישה זורקת → להקדים `firestore.exists()` לקבלת deny נקי במקום שגיאה.

**14. [חובה] פונקציות עזר ב-`storage.rules` — שיקוף `firestore.rules`.**
```
isSuperAdmin()      // שיקוף firestore.rules:177-181 — הכול מהטוקן, בלי firestore.get.
                    // לשים ראשונה בכל || כדי לקצר מעגל.
isSchoolTeacher(slug)  // שיקוף 189-193, חובה .lower() בשני הצדדים:
                       // createSchool כותב toLowerCase (app.html:11819) וטוקן Google עשוי להחזיר אותיות גדולות
isActive(slug)      // שיקוף 196-198 — status == 'active'
canManage(slug)     // שיקוף 201-203 — isSuperAdmin() || (isSchoolTeacher(slug) && isActive(slug))
okSize()            // request.resource.size < 20 * 1024 * 1024
okType()            // request.resource.contentType != null && (...)  ← ראה פריט 16
```
לתקצב עד 2 קריאות Firestore לכל פעולת Storage (הדה-דופליקציה של `get()` מתועדת ל-Firestore פנימי, לא ל-cross-service). בלי `isActive` — מורה של בית ספר מושהה תמשיך להעלות ולצבור עלות.

**15. [חובה] ענפי ה-match — לא `{allPaths=**}` גורף על `schools/{slug}`.**
```
match /platform/{allPaths=**} {
  allow read:  if request.auth != null;
  allow write: if isSuperAdmin() && okSize() && okType();
}
match /schools/{slug}/private/{allPaths=**} {          // חומרי מורה / צרופות מחוון
  allow read, write: if canManage(slug);
}
match /schools/{slug}/topics/{allPaths=**} { ... }      // מפורש, לא **
match /schools/{slug}/questions/{allPaths=**} {
  allow read:  if request.auth != null;
  allow write: if canManage(slug) && okSize() && okType();
}
match /{allPaths=**} { allow read, write: if false; }
```
שני דברים קריטיים: (א) כללי `allow` הם **חיבוריים (OR)** — ענף מגביל אינו מבטל ענף מתירני, ולכן הענף הכללי חייב להיות מצומצם לנתיבים ספציפיים; (ב) `write` ב-Storage מתרחב ל-create + update + **delete**, ולכן `allow write: if canManage(slug) && okSize() && okType();` מספיק — אין צורך ב-`allow write, delete`.
זה מחליף את `allow create: if signedIn() && okSize() && okType();` שבטיוטה (`docs/storage-plan.md:133`), שמאפשר לכל תלמידה מחוברת למלא את תיקיית כל בית ספר אחר בזבל.

**16. [חובה] שני באגים בטיוטת החוקים שחייבים תיקון.**
- `okType()` (`storage-plan.md:119`): `request.resource.contentType` יכול להיות null, ו-`.matches()` על null זורק → deny שנראה כמו כשל הרשאות סתמי. להוסיף `!= null &&`. כדאי גם להדק את `application/vnd.*` לרשימה מפורשת של סוגי Office.
- `mine()` (`storage-plan.md:122`): ב-create אין `resource` בכלל, ובאובייקט בלי `customMetadata` — `resource.metadata` הוא null והגישה זורקת → גם המעלה הלגיטימי לא יוכל למחוק, וזה בדיוק מייצר קבצים יתומים. **עדיף לוותר על `mine()` לגמרי ולעבור ל-`canManage(slug)`** — `uploaderUid` הוא הצהרה עצמית של הלקוח ולא ראיה, ובכל מקרה חייבת להיות סימטריה עם Firestore (שם מחיקה = `canManage`, `firestore.rules:246`). אחרת מורה חדשה שהוחלפה תמחק את המסמך ולא את האובייקט → יתום ודאי.

**17. [חובה] `firestore.rules` — נעילת `storagePath` לתחילית של אותו בית ספר.**
ב-`schools/{school}/media` (244-246) — וגם ב-`practice` (249-252) וגם באוסף ה-legacy `media` בשורש (135-138), שאליו `app.html:1125` כותב בפועל דרך `col('media')`:
```
allow write: if canManage(school)
  && (!('storagePath' in request.resource.data)
      || request.resource.data.storagePath.matches('^schools/' + school + '/.*'));
```
התנאי חייב להיות סלחני למסמכי legacy בלי `storagePath`. בלי זה, מורה של בית ספר א' יכולה לכתוב מסמך שמצביע ל-`schools/b/...` או ל-`platform/...`.

**18. [חובה] `firestore.rules` — `platform_*` ל-`isSuperAdmin()` + נעילת `^platform/`.**
שורות 132, 144, 157 כותבות `allow write: if isTeacher();`. `isTeacher()` (24-28) ו-`isSuperAdmin()` (177-181) שקולות **היום** כי שתיהן מקודדות `motiml77@gmail.com` — אבל ברגע ש-`isTeacher()` ירוכך או יימחק בשלב 6 של המיגרציה, התיקייה הגלובלית ב-Firestore תיפתח בעוד זו של Storage תישאר נעולה. זו הצמדה מכוונת, לא no-op. אפשר לפרוס היום, בלי Blaze.

**19. [חובה] `firestore.rules:156` — סגירת הקריאה הציבורית ל-`platform_settings`.**
`allow read: if true` על המסמך שמחזיק את תמונות הפרסום ב-base64 (עד ~980KB לכל אחת). ב-Spark סריקה בלולאה נעצרת במכסה; ב-Blaze היא מחייבת — וזה וקטור ההוצאה היחיד שלא דורש חשבון Google בכלל.
**חובה לבצע יחד עם פריט 40** (`loadAds` deps): `AdPopup` מרונדר ב-8253, אחרי `if (!user) return <LoginScreen/>` ב-8134, ולכן הפרסומת ממילא לא מוצגת לפני התחברות — אבל `loadAds` רץ ב-`useEffect` עם deps ריקות (7162) לפני שה-auth נפתר, וקריאה חסומה תחזיר null שיישאר null לכל הסשן.
אלטרנטיבה עדינה: לפצל ל-`platform_settings/ads_public` שמחזיק רק `imageUrl` ודגלי הפעלה.

**20. [רשות] `firestore.rules:217` — הוספת `storageUsedBytes` לרשימת השדות החסומים.**
`.hasAny(['teacherEmail', 'status', 'slug'])` — כל שדה אחר פתוח למורה. מונה שימוש שיתווסף בלי זה יהיה ניתן לזיוף בקליק אחד מהדפדפן וכל אכיפת מכסה שתישען עליו חסרת ערך. עדיף: מונה במסמך נפרד שנכתב רק ע"י המנהל הראשי / סקריפט ניהולי.

**21. [רשות] `cors.json` — הקשחה, לא חוסם.**
העלאות והורדות דרך ה-SDK עוברות ל-`firebasestorage.googleapis.com`, שמחזיר כותרות CORS משלו ואינו נשלט ע"י תצורת ה-CORS של הדלי. `cors.json` נדרש **רק** לגישה ישירה ל-`storage.googleapis.com/<bucket>/<object>` — ולכן הפתרון לתקלה 5 במסמך (`storage-plan.md:102`) הוא בדיוק "תמיד `getDownloadURL()`". רשימת ה-`x-goog-upload-*` מיותרת בנתיב ה-SDK. אם בכל זאת מחילים:
```
gcloud storage buckets update gs://exams-a93fb.firebasestorage.app --cors-file=cors.json
gcloud storage buckets describe gs://exams-a93fb.firebasestorage.app --format="default(cors_config)"
```
`cors set` **דורס** ולא ממזג — כל הדומיינים באותו קובץ; `[]` ריק משבית הכול. הקובץ חייב להיות UTF-8 **בלי BOM** — ב-Windows PowerShell 5.1 `Out-File -Encoding utf8` כותב **עם** BOM; להשתמש ב-`[System.IO.File]::WriteAllText($p,$t,(New-Object System.Text.UTF8Encoding($false)))`. `gcloud services enable firebasestorage.googleapis.com` מיותר — ה-API כבר הופעל (`storage-plan.md:13`).

**22. [רשות] `firebase.json:7-12` + `.vercelignore` — הקבצים מוגשים פומבית.**
`"public": "."` עם ignore שמכסה רק `firebase.json`, `**/.*`, `node_modules`, `screenshots` — כלומר `firestore.rules`, `firebase-setup/firestore.rules`, `docs/**`, `scripts/**`, `uploads/**` כולם מוגשים כבר היום, וגם ב-Vercel (אין `.vercelignore`). להרחיב ל-`"*.rules", "cors.json", "docs/**", "scripts/**", "firebase-setup/**"` וליצור `.vercelignore` מקביל. לוודא שלא מחריגים בטעות `codemirror-java.min.js` או `assets/`. הערה: `SUPER_ADMIN_EMAIL` ממילא גלוי ב-`app.html:514` — החשיפה המשמעותית היא `docs/` (תכנית המוצר המלאה) ומפת התיקיות.

**23. [רשות] `firebase-setup/firestore.rules` — עותק ישן ומסוכן.**
148 שורות, אפס אזכורים של `schools`, `isTeacher` עם `motiml77` קשיח (24-28). `firebase.json` מצביע נכון על השורש, אבל שני קבצים באותו שם הם מלכודת — במיוחד כשמוסיפים קובץ חוקים שני. למחוק או לשנות ל-`firestore.rules.legacy`. פרסום בטעות שלו מבטל את כל הבידוד הרב-בית-ספרי.

**אימות שלב 2:**
```
firebase deploy --only firestore:rules --project exams-a93fb
firebase deploy --only storage        --project exams-a93fb
```
לא להריץ `firebase deploy` עירום — הוא יפרוס גם hosting ויעלה את כל שורש הריפו ל-`exams-a93fb.web.app`. (`--dry-run` קיים רק ב-firebase-tools v13.16+ — לאמת גרסה לפני שמסתמכים.)
בקונסולת Storage → Rules לוודא שהקובץ שפורסם הוא זה שבריפו, ולא ברירת המחדל.

---

## שלב 3 — שינויי קוד

### 3א. תשתית

**24. [חובה] `app.html:28` — טעינת ה-SDK.**
להוסיף שורה רביעית מיד אחרי `firebase-firestore-compat.js`, **באותה גרסה 10.7.1** (ערבוב גרסאות ב-compat גורם לשגיאות אתחול סתומות):
```html
<script src="https://www.gstatic.com/firebasejs/10.7.1/firebase-storage-compat.js"></script>
```
ובאתחול (676-680, ליד `auth` ב-677 ו-`db` ב-680): `const storage = firebase.storage();`. **חובה `if (!storage) return` בכל שירות**, במקביל ל-`if (!db)` הקיים — אחרת מצב הדמו בלי מפתח API קורס. `storageBucket` כבר מוגדר נכון ב-509 — אין מה לגעת בקונפיג.

**25. [חובה] `app.html:3481` — פיצול `compressImage`.**
`compressImage(file, maxDim = 1400, quality = 0.8)` היא הצוואר שכל 7 מסלולי ההעלאה עוברים דרכו. לפצל:
- `compressImage` נשארת לדחיסה בלבד אבל מחזירה **Blob** (`canvas.toBlob` במקום `toDataURL` ב-3496).
- לידה `uploadFile(blob, path)` שמריצה `storage.ref(path).put(blob, { contentType, cacheControl, customMetadata: { uploaderUid } })` ואז `getDownloadURL()`.
- **למחוק** את 3497 (`if (out.length > 720000) → quality 0.6`) — 720KB הוא קובץ קטן לחלוטין בדלי.
- **3498** (`if (out.length > 980000) reject('too-large')`) → לבדוק `file.size` המקורי מול 20MB; הודעה: "הקובץ חורג מ-20MB".
- `maxDim` מ-1400 ל-2000/2400 ו-`quality` ל-0.9 — הפרמטרים הופכים מ"אילוץ Firestore" ל"העדפת איכות". להוסיף פרמטר `raw=true` לעקיפת דחיסה.
- להוציא את הסף לקבוע יחיד `MAX_UPLOAD_BYTES` שכל אתרי ההעלאה קוראים ממנו.
**אזהרה:** שינוי חתימה כאן שובר את כל 7 המסלולים בבת אחת. ואסור להעלות תקרות בלקוח לפני שחוקי ה-Storage פורסמו — נוצר חלון שבו אין שום הגבלה (מגבלת המסמך של 1MB הייתה עד היום הבלם האמיתי היחיד).

**26. [חובה] `cacheControl` בכל העלאה.**
`{ cacheControl: 'public, max-age=31536000, immutable' }`. אין שום אזכור של `cacheControl` בריפו. זה ההבדל בין "כל תלמידה מורידה את החוברת פעם בשנה" לבין "כל שעה מחדש" — בחוברת 3MB ו-300 תלמידות, עשרות GB בחודש מול ~0.9GB חד-פעמי. בטוח כאן כי `fileId` הוא `{חותמת-זמן}_{אקראי}.{סיומת}` (`storage-plan.md:62`) ולעולם לא נדרס. **הכלל הנגזר: לעולם לא לעשות overwrite לנתיב קיים — רק fileId חדש ועדכון Firestore.** (לאמת את כותרת ברירת המחדל בפועל, לא להניח 3600.)

**27. [חובה] `getDownloadURL()` פעם אחת בלבד.**
לקרוא מיד אחרי ההעלאה ולשמור בשדה; ברינדור להשתמש רק בערך השמור — לעולם לא בתוך קומפוננטה או `useEffect`. כל קריאה היא בקשת מטא-דאטה מחויבת + round-trip; בגלריה עם 15 קבצים × 300 תלמידות זה אלפי פעולות ביום. ולעולם לא לבנות URL של הדלי ידנית (`storage-plan.md:102`) — זה ייחסם ב-CORS וייראה כתקלת הרשאות מטעה.
**אזהרה:** לחיצה על "Revoke token" בקונסולה הורגת את כל ה-URLs השמורים בבת אחת. לא לגעת בכפתור.

**28. [חובה] `app.html:2172` — `topicKeyOf` מחזיר `''` ושובר את הנתיב הגלובלי.**
`TOPIC_KEYS` (2168) מכיל 11 שברי-כותרת; לכל נושא אחר מוחזר `''`. המפתח נכתב למסמכים גלובליים ב-3843 וב-11901, ולכן יגיע לנתיב Storage מכל העלאה גלובלית של נושא שאינו ברשימה → `platform/topics//1712345678_a3f9.pdf` עם מקטע ריק, קובץ שאי אפשר לאתר או למחוק בלי סקריפט. לתקן **לפני** הקובץ הראשון: fallback (תעתיק/hash של הכותרת) או חסימת העלאה גלובלית לנושא בלי `topicKey`.

### 3ב. שכבת הנתונים

**29. [חובה] `app.html:1118-1127` — `saveTopicMedia`.**
`const clean = { ...item, id, createdAt: ... }` (1122) מעתיק כל שדה מהטופס (שהטופס ב-3909 מחזיק `dataUri` ו-`fileName`). לשנות: להעלות קודם, לקבל `storagePath`/`downloadUrl`/`size`/`contentType`, ולעשות `delete clean.dataUri` **רק בנתיב שבו ההעלאה הצליחה** — אחרת המסמך יחזיק גם base64 מלא וגם קישור וכל היתרון נאבד; ואם ימחק מוקדם מדי, פריטי legacy יאבדו תוכן.
**`storagePath` הוא חובה** — הוא הקישור היחיד בין המסמך לאובייקט, ובלעדיו כל הקבצים יתומים בפועל.
שני נתיבי כתיבה (1124-1125: `platform_media` / `col('media')`) × שני מצבים (legacy/schools) = 4 צירופים. נתיב ה-Storage חייב להיגזר מאותו slug ש-`col()` משתמש בו, אחרת קובץ של בית ספר אחד ייכתב לתיקייה של אחר.

**30. [חובה] `app.html:1129-1136` — `deleteTopicMedia`: להפוך את סדר המחיקה.**
היום מוחק מסמך בלבד. אחרי המעבר: **קודם** `storage.ref(item.storagePath).delete()`, ורק אם הצליח (או החזיר `storage/object-not-found`) — `doc.delete()`. הסדר ההפוך מאבד את `storagePath` ומשאיר יתום לנצח.
**אותו טיפול בכל אתרי המחיקה:** כפתור המחיקה 4015, מחיקה מקונסולת האדמין 12216, הסרת צרופה `onChange(null)` ב-4435, כפתור הסרת פרסומת 12124 (שכבר מנקה `image` ו-`imageUrl` — להוסיף מחיקה מ-Storage).
**זה נכתב יחד עם ההעלאה, לא אחריה.**

**31. [חובה] `app.html:1366-1382` — `loadQuestionContent`: נרמול מרכזי.**
הפונקציה מחזירה `imageBase64: d.imageBase64 ?? d.image ?? ''` (1372) ושולטת בכל ארבעת מסלולי תמונת השאלה. להוסיף `imageUrl: d.imageUrl ?? ''` ולהחזיר אותו גם בברירות המחדל ב-1367, 1370, 1381.
**עדיף:** להחזיר שדה מאוחד אחד (`imageSrc`) שכל הרינדור משתמש בו — הנרמול כבר תומך בשני שמות היסטוריים, ושם שלישי מגדיל את שטח הבלבול.

### 3ג. שבעת מסלולי ההעלאה

**32. [חובה] `app.html:3916-3930` — `TopicMediaSection.onFile` (מדיה לנושא, בית-ספרי).**
תמונה נתפסת ב-3918 ועוברת ל-`compressImage` (3919) — היא **לא** נבדקת מול 950,000; קובץ אחר עובר `FileReader.readAsDataURL` עם תקרת 950,000 ב-3926 והודעת "המגבלה כ-700KB". **שני המסלולים עוברים ל-Storage.**
נתיב: `schools/{slug}/topics/{classId}/{ts}_{rand}.{ext}`, או `platform/topics/{topicKey}/...` כשתיבת "גלובלי" מסומנת. לכתוב `storagePath`/`downloadUrl`/`size`/`contentType` ב-item (3942) במקום `dataUri`; `fileName` נשאר לתצוגה. למחוק את בדיקת 950,000 ואת ההודעה.
**סדר קריטי:** אין לכתוב את מסמך Firestore לפני שההעלאה הסתיימה — אחרת נוצר פריט עם `downloadUrl` שבור.
לוודא ש-`accept="image/*,.pdf"` (3987) מכוסה בדיוק ע"י `okType()` בחוקים, אחרת ההעלאה תיכשל רק בצד השרת.

**33. [חובה] `app.html:11879-11889` — `AdminConsole.onMFile` (מדיה גלובלית).**
אותה תבנית: תמונה דרך `compressImage` (11882, עוקפת את הבדיקה), קובץ אחר עם תקרת 950,000 ב-11887. שמירה ישירה ל-`platform_media/{id}` ב-11901 עם `dataUri` + `fileName`.
נתיב: `platform/topics/{topicKey}/{fileId}.{ext}`. לכתוב `downloadUrl`/`storagePath`/`size`/`contentType`. ה-input ב-12201.
**11902** — ההתראה `'השמירה נכשלה: ' + e.message + '\n(קובץ גדול מדי או בעיית הרשאות)'` מנחשת בין שתי סיבות; אחרי המעבר ענף "קובץ גדול מדי" נעלם מהכתיבה ל-Firestore ושגיאות גודל עוברות לשלב ההעלאה עם הודעה מדויקת — לצמצם ל"בעיית הרשאות".
תוכן גלובלי נקרא בכל בתי הספר, ולכן כאן הרווח (CDN-cache במקום קריאת מסמך) הגדול ביותר.

**34. [חובה] `app.html:12098-12103` — תמונות פרסום (פופאפ + באנר).**
`pickImage` מריץ `compressImage(file, 1200, 0.82)` (12100) וכותב `setKind(kind, { image: uri })` (12101). ה-input ב-12122; השמירה ב-`saveAds` (1188-1191, ה-`set` ב-1190) למסמך יחיד `platform_settings/ads`.
**השינוי הזול ביותר ברשימה — שדה אחד:** להעלות ל-`platform/ads/{kind}/{fileId}.jpg` ולכתוב `imageUrl` במקום `image`. **אין שינוי בתצוגה** — `adSrc` (3692) כבר מעדיף `imageUrl`, והוא משמש ב-11 אתרי רינדור (3699, 3706, 3724, 3725, 12121, 12124, 12128, 12148, 12154, 12164, 12169).
**אל תמחק את השדה `image` הישן** — מודעות קיימות ייעלמו.
מבנה התיקיות ב-`storage-plan.md` §2 אינו מגדיר תיקייה לפרסום — `platform/ads/` היא ההחלטה החסרה.
זה גם פותר כשל חי: שתי תמונות באותו מסמך, כל אחת עד ~980KB, ובצירוף חורג מ-1MB → `saveAds` מחזירה false וההודעה ב-12108 מדברת על "תמונה" יחידה במקום על הצירוף.

**35. [חובה] `app.html:4449-4458` — `AttachmentEditor` (צרופת PDF לשאלה).**
`input accept="application/pdf"` (4449) — **Word חסום כאן לגמרי**, לא רק בגלל גודל. בדיקת `f.size > 700 * 1024` ב-4452 עם הודעה שאומרת **"מקסימום 1MB"** — אי-התאמה קיימת; והתווית ב-4447/4448 אומרת "עד 700KB". `FileReader.readAsDataURL` ב-4454 כותב `attachment.dataUri`; נשמר דרך `updateQuestionContent('attachment', att)` ב-9172.
לשנות: העלאה ל-`schools/{slug}/questions/{examId}/{qNum}/{fileId}.pdf`, כתיבת `attachment.downloadUrl` + `storagePath` + `size`. סף ל-20MB דרך `MAX_UPLOAD_BYTES`. לאחד את שלוש המחרוזות (4447/4448, 4452) לאותו מספר. הטיפ ב-4458 ("קבצי Word/PDF גדולים — העלי ל-Drive") הופך למיותר; שדה הקישור נשאר כאופציה ולא כמוצא יחיד.
זו החסימה המורגשת ביותר למורה — דף הנחיות סרוק הוא כמעט תמיד מעל 700KB.

**36. [חובה] `app.html:9146-9150` — תמונת שאלה בעורך המבחן.**
`input accept="image/*"` (9146) → `compressImage(file)` (9149) → `updateQuestionContent('imageBase64', dataUrl)` (9150). `updateQuestionContent` (7806-7812) בונה `qId = ${selectedExam.id}_q_${selectedQuestion}` וקורא ל-`saveQuestionContent` → `col('questions')/{qId}`.
לשנות: העלאה ל-`schools/{slug}/questions/{examId}/{qNum}/{fileId}.jpg`, כתיבת `imageUrl`, ורינדור ב-9141 ל-`src={currentContent.imageUrl || currentContent.imageBase64}`.
**`col('questions')` דו-מצבי** (`schools/{slug}/questions` או `questions` בשורש) — נתיב ה-Storage חייב לעקוב אחרי אותו slug. **ההעלאה חייבת לקרות פעם אחת בבחירת הקובץ ולא בכל שמירה** — `updateQuestionContent` שומר בכל הקלדה.

**37. [חובה] `app.html:11425-11431` — `QuestionForm.handleFileSelect` (5 טריגרים).**
נקודת כניסה אחת, חמישה מקורות: inputs 11588 (החלפת תמונה), 11607 (מצלמה, `capture="environment"`), 11609 (בחירה מאזור ההדבקה), `onDrop` 11601, ו-`paste` גלובלי 11434-11447. קל לפספס אחד בבדיקה.
ה-state נפלט כשדה זמני `_imageBase64` (11623), נקלט ב-`saveQuestion` (7666-7667, כולל `delete data._imageBase64` ב-7667) ונשמר ב-7691. לשנות: `handleFileSelect` מעלה ל-Storage ומחזיק `imageUrl` ב-state, השדה הזמני נקרא `_imageUrl` (עדכון בשני מקומות), `saveQuestion` כותב `imageUrl`, רינדור 11584 → `imageUrl || imageBase64`. `existingImage` (11420, מוזן מ-10251) חייב לדעת לקבל גם `imageUrl`.
זה מסלול ההדבקה שהמורה משתמשת בו בפועל לצילומי מסך — בדיוק מה שההערה ב-3480 מציינת כחורג מ-1MB.

**38. [חובה] `app.html:11241-11247` — אשף ההוספה המהירה.**
`pickImage` → `compressImage` → `setQ(i, { imageBase64: d })`. מקורות: inputs 11383 (גלריה), 11387 (מצלמה), ו-`paste` שמופנה ל-`focusedQ` (11250-11260). רינדור 11376 → `imageUrl || imageBase64`.
השמירה ב-`saveQuickAdd` (7730-7734) רצה בלולאה על כל השאלות. **ההעלאות חייבות לרוץ ב-`Promise.all` לפני יצירת המבחן**, ו-**התנאי ב-7730 (`if (q.instructions || q.imageBase64)`) חייב לכלול גם `q.imageUrl`** — אחרת שאלה עם תמונה בלבד ובלי הוראות פשוט לא תישמר.
היום השמירה מיידית (המרה בדפדפן); אחרי Storage יש רשת → נדרש מצב "מעלה..." ומחוון התקדמות שאינם קיימים בקוד, וטיפול בשגיאת העלאה.

### 3ד. אתרי קריאה שיישברו בשקט — חובה באותה מסירה

**39. [חובה] `app.html:4011` + `4012` — סיווג תמונה מול קובץ.**
```js
const imgs = items.filter(x => x.type === 'file' && (x.dataUri || x.downloadUrl || '').indexOf('data:image') === 0);
```
**זה הפריט המסוכן ביותר ברשימה:** מי שכתב אותו כבר הוסיף `x.downloadUrl` לביטוי ובכך יצר רושם שהקוד מוכן ל-Storage — בעוד שהוא דווקא זה שיישבר. URL של `firebasestorage` לא מתחיל ב-`data:image`, ולכן **כל התמונות ייפלו לקבוצת "חומרים להורדה"** במקום לגלריית התמונות. אותו דבר במסנן `files` ב-4012 (השלילה של אותו תנאי).
תיקון:
```js
const isImg = it => (it.contentType||'').startsWith('image/')
  || /\.(png|jpe?g|gif|webp)$/i.test(it.fileName||'')
  || (it.dataUri||'').startsWith('data:image');
```
הרינדור עצמו (4055, 4056, 4084) כבר נכון ומעדיף `downloadUrl` — רק הסיווג שגוי.

**40. [חובה] `app.html:12220-12221` — תצוגה מקדימה בקונסולת האדמין.**
מסווגת `(it.dataUri || '').startsWith('data:image')` ואחרת מרנדרת `iframe src={it.dataUri}` — **שני הענפים יקבלו undefined** ויציגו iframe ריק. זה הפער החמור והמוחלט: קבצים שיועלו מלוח הניהול לא יוצגו כלל בלוח הניהול עצמו. תיקון: `const src = it.downloadUrl || it.dataUri` + אותו סיווג לפי `contentType` כמו ב-4011.

**41. [חובה] `app.html:4367` — `buildEmbed` לא מכיר `downloadUrl`.**
`if (att.dataUri) return { mode: 'iframe', src: att.dataUri };` ואז `att.url`. צרופה שתישמר עם `downloadUrl` בלבד תחזיר null ו-`AttachmentViewer` לא ירונדר — **הקובץ ייעלם מהעמוד בשקט, בלי קריסה ובלי alert.**
להוסיף כשורה ראשונה: `if (att.downloadUrl) return { mode: 'iframe', src: att.downloadUrl };`
אותה תוספת ב-`attachmentLabel` (4385, קובע תווית PDF/Word), ב-`openUrl` של `AttachmentViewer` (4398) וב-`AttachmentEditor` (4429, 4433) שבודקים `attachment.url || attachment.dataUri`.
(עקיפה אפשרית: לשמור את ה-`downloadUrl` בשדה `url` הקיים — אבל `storage-plan` קובע `downloadUrl`, ועדיף לתקן את הקוד.)
**בונוס:** "פתיחה בכרטיסייה" (4407) חסומה כבר היום בכרום ובפיירפוקס על `data:` URL — עם `downloadUrl` היא פשוט תתחיל לעבוד. אותו שיקול ל-iframe ב-4410.

**42. [חובה] `app.html:1775-1794` + `1756-1763` — תיאום מול ה-Worker.**
`AIService.checkHomework` שולח `questionImage` (1789) בגוף ה-JSON ל-`WORKER_URL`; **וגם `AIService.checkCode`** (חתימה 1756, `gradingData.questionImage` ב-1763, action `gradeExam`). שלושה אתרי קריאה: 3754 (תרגול חופשי), 7917 (בדיקת מבחן ע"י המורה — `checkCode`), 9398/9401 (בדיקה עצמית של התלמידה).
זו התלות היחידה שיוצאת מחוץ ל-Firebase ודורשת שינוי בצד ה-Worker, **שאינו בריפו הזה — חייב deploy מתואם באותו רגע.** מומלץ: ה-Worker מקבל URL ומושך את הבייטים בעצמו (חוסך סיבוב שלם דרך הדפדפן).
**סיכון גבוה ושקט:** אם ה-Worker יקבל URL ולא יטפל בו, Gemini לא יראה את התמונה והמשוב לתלמידה יהיה שגוי בלי שום הודעת שגיאה.
לשים לב: `getDownloadURL` כולל טוקן — שליחתו ל-Worker מעבירה אליו גישה לקובץ.

**43. [חובה] `app.html:7811` — `updateQuestionContent` שומר בלי `await` ובלי `catch`.**
`DataService.saveQuestionContent(qId, updated);` — חריגה נבלעת. היום זה מסתיר את בעיית ה-1MB מהמורה (היא רואה את התמונה במסך, עוזבת, והתוכן לא נשמר); אחרי המעבר שגיאות רשת בהעלאה יחליפו אותה ויידרש בדיוק אותו טיפול. להפוך לאסינכרוני, להוסיף `catch` והתראה, ולעדכן את הקוראים.

**44. [רשות] `app.html:4081` — שדה `size` מוצג ואף פעם לא נכתב.**
`{it.fileName || ''}{it.size ? ' · ' + prettySize(it.size) : ''}` — `prettySize` (4027) מחכה לערך שאף אחד לא כותב. הוא יתמלא אוטומטית ברגע שההעלאות כותבות `size`. הרינדור כבר מוגן.

**45. [רשות] `app.html:4056` — `loading="lazy"` יתחיל לעבוד מעצמו.**
על `data:` URI התכונה חסרת אפקט — הבייטים כבר הגיעו בתוך מסמך Firestore. `src` כבר כתוב `it.downloadUrl || it.dataUri`, ולכן אין מה לשנות. כדאי להוסיף `decoding="async"` ו-`width`/`height` מפורשים למניעת קפיצות פריסה.

### 3ה. אופטימיזציות עלות (לא חוסמות, אבל זולות מאוד)

**46. [חובה] `app.html:7162` — `loadAds` בלי תנאי ובלי מטמון.**
`useEffect(() => { DataService.loadAds().then(setAds)... }, []);` — כל רענון של כל תלמידה וכל כניסת בוט מוריד מחדש את המסמך המלא. לשנות ל-`}, [user])` (**תנאי מוקדם לפריט 19**) ולהוסיף מטמון ב-localStorage עם חותמת זמן (6 שעות) + שדה `version` במסמך נפרד לרענון כפוי.

**47. [רשות] `app.html:1107` — `loadTopicMedia` מוריד את אותם מסמכים פעמיים.**
שאילתת `topicTitle` ושאילתת `topicKey` מחזירות את אותם מסמכים; ה-`seen` מסנן כפילויות **אחרי** שהבייטים כבר ירדו. שני השדות נכתבים יחד בכל שמירה גלובלית (3843, 11901), ולכן הכפילות מובטחת לכל תוכן חדש. בנוסף, כל שאילתה מחויבת בקריאת מסמך אחת לפחות גם כשהיא מחזירה אפס.
תיקון: להריץ את שאילתת `topicKey` רק אם הראשונה החזירה 0, או לעבור ל-`topicKey` בלבד אחרי מילוי חד-פעמי. **אותה תבנית בדיוק ב-`loadTopicPractice` (1147-1149) על `platform_questions`.**

**48. [רשות] `app.html:3912` — מטמון למדיה של נושא.**
`reload` שולף 3 שאילתות בכל מעבר נושא. `MEDIA_CACHE = new Map()` ברמת המודול לפי `classId + '|' + topicTitle`, עם ניקוי המפתח ב-save וב-delete (אחרת מורה לא תראה חומר חדש).

**49. [רשות] `app.html:3496` — תמונה ממוזערת.**
לייצר שני קבצים מאותו canvas: `thumb` ב-480px/0.7 ו-`full` ב-1600px, ולשמור `thumbUrl` + `downloadUrl`. הגלריה (רשת `minmax(min(100%,260px),340px)` ב-4052) מציגה `thumbUrl`, ולחיצה פותחת את המלא. ממוזערת שוקלת כעשירית — מכפילה את מכסת ההורדות פי 10. `compressImage` כבר עושה את העבודה, רק לקרוא לה פעמיים.

**50. [רשות] `app.html:7861` + `7824-7828` — שמירה אוטומטית.**
זו הפעולה בעלת הנפח הגבוה ביותר, ומתרחבת לפי תלמידות × דקות (מכסה חינמית: ~20,000 כתיבות/יום).
**באג אמיתי:** הקולבק של ה-debounce (7824-7828) לא מאפס את `saveTimeoutRef.current`, ולכן ה-ref נשאר truthy "מת" — ה-interval הבא מבצע כתיבה כפולה מיותרת אחרי כל התקף הקלדה.
**חיסכון נקי בלי סיכון:** `lastSavedRef` + דילוג אם הקוד לא השתנה. הארכת המרווחים (5s→15s, 1.5s→3s) היא טרייד-אוף נפרד שמגדיל את חלון האובדן בקריסה.
הערה: זה דווקא טיעון **בעד** Blaze — היום יום מבחנים עמוס עלול לפגוש את התקרה ותלמידות יאבדו עבודה.

**51. [רשות] `app.html:7346` — `loadClasses` בלי מטמון.**
stale-while-revalidate מ-localStorage, או שדה `updatedAt` במסמך בית הספר וקריאה מותנית. אין כאן base64 (התמונות באוסף `questions` הנפרד) — העלות היא בקריאות בלבד.

**52. [רשות] `app.html:7390` — מטמון תוכן השאלה בזיכרון בלבד.**
`if (!questionContent[qId])` נכון וחוסך, אבל נמחק בכל רענון — ובמבחן נעול שבו תלמידות מרעננות מלחץ, אותה תמונה נמשכת שוב ושוב. להעביר ל-sessionStorage (שורד רענון באותה לשונית), תחום ל-50 פריטים, עטוף ב-try/catch (QuotaExceededError). אחרי המעבר ל-Storage הבעיה נפתרת מעצמה דרך מטמון הדפדפן. באותה הזדמנות: `questionContent` חסר מ-deps של ה-useEffect ב-7387-7393.

### 3ו. הרחבות שנפתחות רק עכשיו

**53. [רשות] הרחבת `accept`.** ב-3987 וב-12201 (ואולי 4449) ל-Word/Excel/PowerPoint. **אבל:** תצוגת Word דורשת `view.officeapps.live.com` עם URL ציבורי — שיקול פרטיות שצריך להכריע לפניו. (היום Word חסום דה-פקטו: `buildEmbed` בודק `att.dataUri` ראשון ב-4367 ומחזיר iframe על ה-data URI, כך שהענף ב-4380 לעולם לא נדרס לקבצים שהועלו.)

**54. [רשות] תמונות לשאלות תרגול.** `PracticeQuestionCard` קורא `q.imageBase64` (3754, 3783) אבל `TopicPracticeSection` (3838, 3844) לא כולל שדה תמונה כלל — השדה יכול להתמלא רק בזריעה ידנית. להוסיף העלאה ב-`platform/questions/{topicKey}/{fileId}` או `schools/{slug}/practice/{qId}/{fileId}`, בהפרדה מדויקת בין גלובלי לבית-ספרי.

**55. [רשות] העלאה ע"י תלמידות (הגשת פתרון בכתב יד).**
היום כל 10 שדות ה-`type="file"` (3987, 4449, 9146, 11383, 11387, 11588, 11607, 11609, 12122, 12201) נמצאים בענפי `editMode`/`isTeacher`/AdminConsole. אם מוסיפים — הנתיב חייב להיסגר **לפני** הקובץ הראשון:
```
match /schools/{slug}/submissions/{examId}/{key}/{allPaths=**} {
  allow read:   if canManage(slug) || <בעלות המגיש>;
  allow write:  if <בעלות המגיש> && okSize() && okType();
  allow delete: if canManage(slug);
}
```
**הערה חשובה:** מסמכי ההגשה ב-Firestore ממופתחים ב-`safeEmail` ולא ב-uid (1439, 1514, 1525, 1556, 1577, 1643), והחוק מאמת דרך `resource.data.email`. חלוקת תיקיות לפי `request.auth.uid` תנתק את הקורלציה — עדיף אותו מקטע `safeEmail`, או לשמור uid על מסמך ההגשה קודם.

**אימות שלב 3:** ראה שלב 5.

---

## שלב 4 — נתונים ומיגרציה

**56. [חובה] אין מיגרציה כפויה — ואסור למחוק את `dataUri` מהסכימה.**
`docs/storage-plan.md:83` קובע: הרינדור בודק `downloadUrl` ואם אין — `dataUri`; כל מה שכבר הועלה ממשיך לעבוד. זו הקלה מכוונת שמקטינה את רדיוס הסיכון. **אבל התאימות הזו מתקיימת היום רק במדיה לנושא (4055, 4056, 4084)** — בצרופות ובתמונות השאלה אין נפילה כזו בכלל, ובסיווג (4011) היא קיימת אך הפוכה ולכן שגויה. פריטים 39-41 הם התנאי לכך שההבטחה הזו תהיה נכונה.
מסמכי base64 ישנים ימשיכו לתפוס נפח ב-Firestore. ניקוי שלהם הוא מיגרציה נפרדת שאינה מתוכננת כאן.

**57. [חובה] `scripts/storage-orphans.js` — לא קיים; `scripts/` מכיל היום רק `migrate-lamerhav.js` ו-`rules-test.js`.**
לבנות עם אותו דפוס `getToken()` (`migrate-lamerhav.js:22`) ואותה מוסכמת `--write` (שורה 17, dry-run בשורות 9-10, 134):
1. `gcloud storage ls -r gs://exams-a93fb.firebasestorage.app/**` או REST `storage/v1/b/.../o`;
2. סריקת `schools/{slug}/media`, `schools/{slug}/classes` ו-`platform_media` ואיסוף כל ערכי `storagePath`;
3. הפרש בשני הכיוונים — אובייקט בלי מסמך (יתום) ומסמך שמצביע לאובייקט מחוק (קישור שבור).
**חובה: dry-run כברירת מחדל, `--write` מפורש, `--school <slug>`, סינון גיל (רק אובייקטים מעל 7 ימים), והדפסת רשימה לאישור.** להריץ ידנית פעם בסמסטר, **לא כ-Cloud Function** — סקריפט אוטומטי עלול למחוק קובץ שהמסמך שלו נכתב באותו רגע. אין סל מיחזור ב-GCS.
זו התקלה היחידה שיוצרת חיוב שגדל מונוטונית ולעולם לא יורד מעצמו.

**58. [רשות] מילוי `topicKey` במסמכי `platform_media` ישנים** — תנאי מוקדם להסרת שאילתת `topicTitle` בפריט 47.

**59. [רשות] הידוק חוקי Firestore נגד `dataUri` חדש — רק אחרי שבועיים של ריצה יציבה.**
`allow write: if canManage(...) && !('dataUri' in request.resource.data)` על `platform_media` (130-133), `media` (135-138), `platform_questions` (142-145), `practice` (147-150), `questions` (45-48), `schools/{school}/media` (244-247).
**סדר קריטי:** קודם להעביר את כל הקוד, לוודא שבועיים, ורק אז להדק. הידוק מוקדם = כל העלאה נכשלת. שים לב שהאיסור ישבור גם `merge` על מסמך ישן שכבר מכיל `dataUri`.

**60. [רשות] מחיקת בית ספר — רק בסקריפט ניהולי, עם אישור כפול.**
ל-GCS אין "מחק תיקייה" אטומי מהלקוח. מחיקה רקורסיבית מהלקוח = מחיקה חלקית בלי דרך לחזור. לוח `/manage` מציע היום השהיה/הפעלה בלבד (11842) — **לשמור על כך** (`multi-school-plan.md:283`: מחיקה אמיתית אינה ב-v1).

**61. [רשות] `firebase-setup/seed.js:20` — `storageBucket` באתחול.**
רק אם הסקריפט אמור לגעת בקבצים: `admin.initializeApp({ credential: ..., storageBucket: 'exams-a93fb.firebasestorage.app' })` + `const bucket = admin.storage().bucket();`. אם הוא נשאר Firestore טהור — להוסיף הערה שהוא **לא** מנקה Storage. (הוא ממילא דורש `serviceAccountKey.json` שב-.gitignore. `seed-with-token.js` אינו משתמש ב-firebase-admin כלל.)

**62. [רשות] `firestore.indexes.json` — לא נדרש למעבר.**
שום שלב בהפעלת Storage לא דורש אינדקס חדש. אם בכל זאת: `firebase firestore:indexes --project exams-a93fb > firestore.indexes.json` (**לייצא קודם** — כל אינדקס שקיים בשרת ולא בקובץ מסומן למחיקה), ואז `"indexes": "firestore.indexes.json"` בבלוק firestore.

**אימות שלב 4:** להריץ `storage-orphans.js` ב-dry-run מיד אחרי הקובץ הראשון שנמחק דרך האפליקציה — חייב להחזיר 0 יתומים. פריט ישן עם `dataUri` בלבד עדיין מוצג נכון בכל אתרי הרינדור.

---

## שלב 5 — אימות ובדיקות

**63. [חובה] `scripts/storage-rules-test.js` — אין היום שום בדיקה לחוקי Storage.**
לשכפל את `scripts/rules-test.js` (12 מקרים מול `firebaserules.googleapis.com`) עם שלושה הבדלים: (1) לקרוא `storage.rules` במקום `firestore.rules` (שורה 4); (2) `source.files[0].name = 'storage.rules'`; (3) מבנה request שונה — `path: '/b/exams-a93fb.firebasestorage.app/o/schools%2Ftzvia%2Ftopics%2Fc1%2Ff.pdf'` עם `resource: { size, contentType }` ו-`metadata`.
**מקרי חובה:** קובץ 25MB → DENY · `contentType` null → DENY · `application/x-msdownload` → DENY · מורה של בית ספר א' כותבת ל-`schools/b/` → DENY · כתיבה ל-`platform/` ע"י מורה → DENY · מנהל ראשי מוחק → ALLOW · מורה של בית ספר מושהה מעלה → DENY · נתיב מחוץ ל-`schools/`/`platform/` → DENY · קריאת `schools/{slug}/private/` ע"י תלמידה → DENY.
אם משתמשים ב-cross-service — יידרשו `functionMocks` ל-`firestore.get()`.

**64. [חובה] בדיקה ידנית של כל שבעת מסלולי ההעלאה** (32-38), כולל **חמשת** הטריגרים של `handleFileSelect` (11588, 11607 מצלמה, 11609, drop, paste) ושלושת המקורות של האשף.

**65. [חובה] בדיקת רינדור אחרי כל העלאה:**
- תמונה שהועלתה מופיעה ב**גלריית התמונות** ולא ברשימת "חומרים להורדה" (פריט 39);
- הפריט מופיע בתצוגה המקדימה של לוח הניהול (פריט 40);
- צרופת PDF מרונדרת ב-`AttachmentViewer` ו"פתיחה בכרטיסייה" עובדת (פריט 41);
- תמונת הפרסום מוצגת בפופאפ ובבאנר בלי שינוי (`adSrc`, 3692);
- `prettySize` מציג גודל אמיתי (4081).

**66. [חובה] בדיקת AI עם תמונה — ידנית, בשלושת האתרים** (3754 תרגול, 7917 בדיקת מורה, 9398 בדיקה עצמית). **זו התקלה שלא תצעק** — אם ה-Worker לא עודכן, Gemini פשוט לא רואה את התמונה והמשוב שגוי בלי שגיאה.

**67. [חובה] בדיקת המסמך אחרי שמירה:** לפתוח את מסמך `media`/`platform_media`/`questions` בקונסולת Firestore ולוודא שהוא מכיל `storagePath` + `downloadUrl` + `size` + `contentType` ו**אינו** מכיל `dataUri` חדש.

**68. [חובה] בדיקת מחיקה:** למחוק פריט מהאפליקציה ולוודא בקונסולת Storage שהאובייקט נעלם — ולא רק המסמך.

**69. [רשות] בדיקת CORS אמיתית** (רק אם הוחלט להחיל `cors.json`) — מול **`storage.googleapis.com`**, לא מול `firebasestorage.googleapis.com` (endpoint אחר לגמרי, לא בודק את תצורת הדלי):
```
curl -i -X OPTIONS "https://storage.googleapis.com/exams-a93fb.firebasestorage.app/<object>" \
  -H "Origin: https://java-lamerhav.vercel.app" -H "Access-Control-Request-Method: GET"
```

**70. [רשות] פקודות הפריסה — לתעד ב-`implementation-notes.md`:**
```
firebase deploy --only storage                        --project exams-a93fb
firebase deploy --only firestore:rules,storage        --project exams-a93fb   # שחרור מלא
```
**לעולם לא `firebase deploy` עירום.**

---

## נספח — עדכוני תיעוד (כולם [רשות], אבל שניים מהם מסוכנים)

**71. [רשות] `docs/storage-plan.md`** — טבלת המצב (11-15): "דלי קיים" → כן, עם שם ומיקום; "יצירת דלי" → הצליחה בתאריך X; להסיר את המשפט בשורה 21; לסמן שלבים שבוצעו ב-§6 (147-153). לתקן את §1 (29-43) לפי פריט 3. להוסיף לרשימת המימוש בשלב 4 (152) את **כל** שבעת מסלולי ההעלאה ואת שלושת תיקוני הקריאה ואת תיאום ה-Worker. לתקן את הערכת ה-₪0 בשורה 19: האחסון (5GB) אכן לא יגיע לתקרה, אבל **מכסת ההורדות (1GB/יום) נשרפת לפי צפיות × גודל, לא לפי מספר קבצים** — חוברת 3MB × 300 תלמידות = ~0.9GB ביום אחד. ₪0 מתקיים רק עם `cacheControl` וממוזערות. להוסיף מיפוי מפורש: `topics/` בדלי ↔ אוסף `media` עם שדה `classId` ב-Firestore (912-941, 1112).

**72. [רשות] `docs/implementation-notes.md`** — למחוק את "כי Firebase Storage עדיין חסום על Spark" (שורה 220; 221-222 כבר נכונות). להוסיף את פקודות הפריסה לסעיף 228-232. **לתקן את בדיקת השלמות (שורה 12): `col('` = 37 ו-`db.collection(` = 23 (כולן לגיטימיות) — לא 40 ו-0.** בדיקה שנכשלת תמיד היא בדיקה שמפסיקים להסתכל עליה. לעדכן/להסיר את טבלאות מספרי השורות (§6) — הן נכונות ל-`41a9bbf` (10,996 שורות) והקובץ היום 12,306. לסמן כבוצע: §2 (ארבע בדיקות `motiml77` עברו ל-`isTeacherEmailOfCurrentSchool` — 1010, 1050, 1085, 1240), §6 (`lsKey`, 545), §11 (שדה `school` ל-Worker — 1761, 1785, 1827).

**73. [רשות] `docs/implementation-notes.md:107` — סכנת רגרסיה.**
§5 מורה להוסיף `<base href="/">`. `app.html:32` מכיל הערה מפורשת שהוא **הושמט בכוונה** כי שבר הפניות SVG פנימיות (`url(#grid)`, `url(#arrowhead)`); התיקון שיושם הוא הנתיב האבסולוטי בלבד (33). מי שיישם את ההוראה ישבור את כל ההדגמות באתר — תקלה חזותית שקטה.

**74. [רשות] `docs/multi-school-plan.md`** — §4.1/§4.3/§8: `/admin` → `/manage` (הקוד מנתב לפי `manage` ב-525; `vercel.json` ו-`firebase.json` מפנים `/manage`). §5: להוסיף `media`, `practice`, `activity` תחת `schools/{slug}` ואת `platform_media`/`platform_questions`/`platform_settings` בשורש — **אלה בדיוק האוספים שמצביעים על קבצי Storage**, וסקריפט יתומים שייכתב לפי §5 הנוכחי יפספס אותם. §6: `allow get: if true` (`firestore.rules:213`) ולא `isSignedIn()`, `.lower()` בשני הצדדים (192), ו-match ל-`media`/`practice`/`activity` (244, 249, 256). §11: שלבים 1-5 בוצעו (83ed317, f69488c, 6042f35, 800e42e, 2fe96e5), שלב 6 **לא**, §14 (Vite) לא התחיל.

**75. [חובה] `docs/multi-school-plan.md:273` ו-§10 — שתי טעויות שיכולות לגרום לאובדן נתונים.**
- §6 טוען שהחוקים החדשים אינם כוללים את אוספי השורש. **הם כן** — `firestore.rules` שומר אותם בכוונה (40-168) כמצב legacy של lamerhav עד שלב 6, וההערה בשורות 172-173 אומרת זאת. מי שיסיק שהשורש כבר חסום עלול למחוק אותם בזמן שהאפליקציה עדיין נופלת אליהם דרך `__legacyFallback` (535, נצרך ב-902).
- §10 מכריז על `firebase-setup/migrate-to-schools.js`; בפועל הסקריפט הוא `scripts/migrate-lamerhav.js`, ו-`PLAIN` בשורה 18 מעתיק `classes, questions, exam_settings, grading_rubrics, notifications, users, media` — **בלי `practice` ובלי `activity`**, בעוד שהמסמך (335) מבטיח "כל אוסף שורש". מחיקת אוספי השורש בשלב 6 בהסתמך על "הועתק הכול" תמחק אותם לצמיתות.

**76. [רשות] `vercel.json` — נבדק, אין מה לשנות.**
12 שורות, בלי בלוק `headers` ולכן בלי CSP שתחסום את `firebasestorage.googleapis.com`; אין משתני סביבה כי `firebaseConfig` מוטמע ב-505-512; ה-rewrites (8-10) לא מושפעים. **מתועד כאן רק כדי שלא יחפשו שם.** אם בעתיד מוסיפים CSP — חובה `connect-src` ו-`img-src` עם `https://firebasestorage.googleapis.com`, אחרת כל ההעלאות נשברות בבת אחת ונראות כתקלת CORS למרות ש-CORS תקין.

**אימות הנספח:** `grep -n "Spark" docs/*.md` לא מחזיר טענה שהפרויקט חסום; `grep -rn "/admin" docs/multi-school-plan.md` נקי; בדיקת השלמות ב-implementation-notes עוברת בהרצה.