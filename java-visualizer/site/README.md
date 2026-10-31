# האתר של DSViz

דף ראשי בעברית (`index.html`), `bagrut.html` (שאלת בגרות לקוד), `privacy.html` ו-`404.html`. בלי שלב בנייה: HTML, CSS ו-JS רגילים.

- **ההדגמה בראש העמוד** (`assets/demo.js`): ההרצה של `countEven` צעד אחרי צעד. הצעדים מחושבים מהקוד עצמו בדפדפן,
  והציור (מחסנית, מעקב משתנים, השורה בצהוב) בצבעים של חלון ההרצה האמיתי.
- **התמונות** (`assets/img`) — צילומי מסך אמיתיים מ-`docs/` ומבדיקות Eclipse; **הסרטון** (`assets/video/loops.mp4`) —
  44 צעדים של מיון בועות בחלון ההרצה (`tutorials/viewer.sh`), בלי עיבוד.
- **הקישור להורדה** — הקישור הקבוע של `release/DSViz-Setup.exe` ב-master, ישירות מ-GitHub (עובד תמיד). `assets/downloads.js`
  שולח ברקע `POST /v1/count/windows` לשרת, והמספר מוצג מ-`/v1/stats` (רק כשה-KV בשם STATS מוגדר — `worker/README.md`).
  תאריך הגרסה נקרא מ-`release/version.json`.
- **שאלת בגרות לקוד** (`bagrut.html`, `assets/bagrut.js`): הקבצים מוכנים בדפדפן (Word נפתח עם JSZip מ-cdnjs, תמונות מוקטנות),
  נשלחים ל-`/v1/site/exercise` בשרת, והקוד נבנה בדפדפן ב-`assets/scaffold.js` — העתק של `AiScaffold.java` מהתוסף
  (`tools/test/scaffold-parity.js` בודק שהם מפיקים בדיוק אותו קוד). השרת מקבל בקשות כאלה רק מהכתובת של האתר (`SITE_ORIGINS`).
- **תמכו בפרויקט** — כפתור שקט (מסגרת דקה, לב קטן) בסרגל העליון ליד "הורדה" ובתחתית כל עמוד, לדף התרומה ב-PayPal (אותו עיצוב ואותו קישור כמו בחלון ההרצה ובנגן הסרטונים).

## פרסום
GitHub Pages דרך `.github/workflows/dsviz-site.yml` (בשורש המאגר): כל שינוי בתיקייה הזו ב-master מתפרסם ל-
https://motiml77.github.io/java-lamerhav/ . פעם אחת צריך להפעיל ב-GitHub: **Settings → Pages → Source: GitHub Actions**.
תצוגה מקדימה בלי הגדרות: https://raw.githack.com/motiml77/java-lamerhav/master/java-visualizer/site/index.html

## מה נמנענו ממנו (כדי שהאתר לא ייראה "עשוי ב-AI")
בלי גרדיאנטים (גם לא בטקסט), זוהר, כרטיסי זכוכית, שלושה כרטיסים זהים עם אייקונים, אימוג'י, תוויות קטנות באותיות גדולות מעל
כותרות, מספור 01/02/03, סרגל ניווט צף, אנימציית "עלייה" על כל אזור, המלצות ומספרים מומצאים, ומילים כמו "מהפכני" או "חוויה".
במקום זה: הצבעים והציורים של התוכנה עצמה, צילומים אמיתיים, גופן אחד (IBM Plex Sans Hebrew, ו-Plex Mono רק לקוד),
חמישה גדלים של טקסט, אזורים שנבדלים ברקע ולא במסגרות, וכותרת של שתי שורות.
