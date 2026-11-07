# כניסה למאגר — איפה עומדים ומה הלאה (ענף feature/login-access)

## מה מוכן
- שרת (worker/src/access/): מורים, עמדות מחשב (קוד מורה + טרנזקציה), תלמידים (תשלום / הענקה ידנית), בקשות מההנהלה,
  יומן, הגדרות, אישור חתום לעבודה בלי אינטרנט, ונעילת שליחה ל-Gemini (מתג בהנהלה). כבוי כל עוד ACCESS לא "on".
- דפים (worker/public/ → src/access/assets.gen.js): /signin /welcome /account /admin.
- בדיקות: test/worker-test.mjs (39), test/access-test.mjs (21, אמולטור), test/pages-e2e.mjs (11, דפדפן + אמולטור).
- שירות תשלום: עוד לא נבחר — שכבה ב-src/access/pay.js (demo לבדיקות בלבד). המלצה: Grow, חלופה: Cardcom.

## החלטות של המשתמש (5.11.2026)
- קוד המורה קבוע — אין "קוד חדש" (לא למורה ולא בהנהלה).
- רק הכניסה הראשונה צריכה אינטרנט; אחר כך הכול מקומי (offlineDays = 0 = בלי הגבלה). נעילה — בבדיקה הבאה עם אינטרנט.
- אין "בקשת עמדות" — המורה קונה חבילות (10 / 20 / 30, מחיר לכל חבילה בהגדרות ההנהלה), והעמדות מתווספות לבד אחרי התשלום.
- במסוף ההנהלה רואים לכל תשלום: מה נקנה, חשבון, שם המשלם, טלפון, מייל, סכום, מצב, אסמכתה.
- העדפה: שירות תשלום שרק סולק (בלי קבלות אוטומטיות) — השוואה חדשה בדרך.

## Firebase — מה כבר קיים (5.11.2026)
- פרויקט dsviz-access (בחשבון mordechai.l.91@gmail.com). כניסה עם Google — מופעלת (נבדק). Firestore — קיים, סגור (403 מבחוץ).
- הגדרות האתר ב-wrangler.toml (ציבוריות). חסר: מפתח חשבון שירות (GOOGLE_SA_JSON בסביבה) ו-CLOUDFLARE_API_TOKEN.
- לעשות כשיש מפתחות: Authorized domain ל-dsviz-exercise.motiml77.workers.dev, כללי Firestore "הכול חסום", סודות ל-Worker, פריסה עם ACCESS כבוי, בדיקה.

## מה הלאה (לפי הסדר)
1. Firebase — פרויקט חדש ונפרד (לא exams-a93fb). סודות בסביבה: GOOGLE_SA_JSON (חשבון שירות Owner על הפרויקט החדש בלבד).
   לעשות: להפעיל APIs (firestore, identitytoolkit, firebase), ליצור Firestore (me-west1), כללים "הכול חסום",
   Authorized domain ל-dsviz-exercise.motiml77.workers.dev, אפליקציית Web (apiKey/authDomain), וחשבון שירות צר ל-Worker
   (Cloud Datastore User) — את המפתח שלו כסוד FIREBASE_SERVICE_ACCOUNT ב-Worker.
   (את Google כספק כניסה מפעילים בקונסולה — Authentication → Sign-in method → Google.)
2. Cloudflare — סוד בסביבה: CLOUDFLARE_API_TOKEN (תבנית Edit Cloudflare Workers). לעשות: PASS_KEY (tools/pass-key.mjs) כסוד,
   FIREBASE_PROJECT_ID / FIREBASE_API_KEY / FIREBASE_AUTH_DOMAIN / ADMIN_EMAILS=motiml77@gmail.com כ-vars, פריסה עם ACCESS כבוי,
   בדיקה, ורק אז ACCESS=on (ההגדרה "רק למחוברים" ל-Gemini נשארת כבויה עד שהתוספים מעודכנים).
3. תוספים (Java 8): Account — כניסה דרך הדפדפן (127.0.0.1 + PKCE), עמדת מחשב בקוד, שמירה במחשב, בדיקת האישור החתום;
   מסך כניסה במאגר (Eclipse SWT, IntelliJ/Visual Studio Swing) ובשליחת תרגיל; VS Code אחר כך.
4. שירות תשלום אחרי שהמשתמש בוחר.
