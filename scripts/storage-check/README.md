# בדיקת חוקי Storage — דרך ה-SDK, לא דרך REST

**אל תבדקי חוקי Storage עם ה-Rules Test API או עם REST גולמי.**
שניהם החזירו כאן תוצאות שגויות:

- `firebaserules.googleapis.com:test` אינו מריץ קריאות cross-service בכלל
  (`firestore.get` → "Function not found"), ולכן כל DENY עובר שם מהסיבה הלא נכונה.
- העלאה ב-REST (`uploadType=media` / `multipart` / `resumable` בבנייה ידנית)
  לא הפעילה את מגבלות הגודל והסוג, והראתה כשלים שאינם קיימים במציאות.

הבדיקה כאן מריצה את **firebase-storage-compat האמיתי בדפדפן** — אותו נתיב
שהאפליקציה משתמשת בו. זו הבדיקה היחידה שאפשר לסמוך עליה.

## הרצה

1. להפעיל זמנית ספק email/password ב-Authentication וליצור שני משתמשי בדיקה
   מאומתים: `storage-probe-a@example.com` / `storage-probe-b@example.com`.
2. להחליף `REPLACE_KEY` ב-`index.html` ב-apiKey מתוך `app.html`.
3. `node scripts/storage-check/run.js`
4. למחוק את המשתמשים ולכבות את הספק בחזרה.

## מה נבדק (8/8 עברו ב-2026-08-27)

תמונה לתיקיית בית ספר ✓ · קובץ הרצה נחסם ✓ · כתיבה לתוכן גלובלי נחסמת ✓ ·
החלפת תמונת פרסום נחסמת ✓ · נתיב מחוץ למבנה נחסם ✓ ·
דריסה ומחיקה של קובץ זר נחסמות ✓ · המעלה המקורית מוחקת את שלה ✓
