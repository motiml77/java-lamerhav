# האתר של DSViz

עמוד ראשי (`index.html`), מדריך התקנה לכל סביבה (`install.html`), `bagrut.html` (שאלת בגרות לקוד), `privacy.html` ו-`404.html`.
בלי שלב בנייה: HTML, CSS ו-JS רגילים. העיצוב והכללים שלו, ולמה הוא לא נראה "עשוי ב-AI": [DESIGN.md](DESIGN.md).

- **ההדגמה בראש העמוד** (`assets/demo.js`): ההרצה של `countEven` צעד אחרי צעד. הצעדים מחושבים מהקוד עצמו בדפדפן,
  והציור (מחסנית, מעקב משתנים, השורה בצהוב, push שנכנס ו-pop שעף) בצבעים של חלון ההרצה האמיתי.
- **קיצורי מקלדת בהדגמה**: חצים (שמאלה = הבא), רווח מפעיל ועוצר, Home / End; גם כפתור "הפעלה". הרמז מוצג רק במכשיר עם עכבר.
- **שליחת תרגיל, שלב אחרי שלב** (`#exercise`, `assets/flow.js`): שישה שלבים — הדף, מה ש-Gemini החזיר, החבילה, הקובץ, ההרצה
  וההצעה לפתרון. שלושת צילומי הדף (`submit-page-a|b|c.webp`) הם הקבצים ששלחנו, והתשובה של Gemini עליהם מוטמעת ב-flow.js בלי שינוי
  (אותו JSON ב-`tools/test/ai/parity/5-gemini-balanced-java.json`, כך ש-scaffold-parity בודק גם אותו). החבילה והקוד נבנים בדפדפן
  ב-`assets/scaffold.js`, שנטען רק כשמתקרבים לאזור. הצילומים מ-IntelliJ IDEA על אותו תרגיל (`submit-dialog|created|run|solution-ij.webp`,
  חתוכים בלי הודעות הסביבה ובלי נתיבי ‎/tmp), מ-Eclipse (`submit-dialog|bulbs-eclipse.webp`, מ-`docs/`), ו-`submit-web.webp` —
  bagrut.html עם אותה תשובה. הנגן רץ לבד פעם אחת כשהוא על המסך (לא כשמבקשים פחות תנועה), ונעצר בכל לחיצה. ההצעה לפתרון סגורה עד לחיצה על המנורה.
- **נסו בעצמכם** (`#playground`, `assets/playground.js`): מחסנית, תור, רשימה מקושרת ועץ חיפוש, עם כפתורים לפעולות. אותו ציור וצבעים
  כמו ההדגמה וחלון ההרצה (נוסף בירוק, נקרא בצהוב, יצא באדום מקווקו, חוליה שאבדה), ולכל פעולה שורת Java מסומנת. הכול בדפדפן.
- **מה חדש** (`#news`): מהיסטוריית ה-git של המאגר (כותרות ה-PR), מהחדש לישן. מעדכנים ביד כשנכנס משהו חדש.
- **הצילומים** (`assets/img/stack|list|tree|loops-nested.webp`) והסרטון (`assets/video/queue.mp4`) — מחלון ההרצה האמיתי,
  בלי עריכה: תוכניות קצרות בסגנון בגרות (מחסנית, תור, רשימה מקושרת, עץ, לולאה בתוך לולאה) רצות ב-Xvfb, ו-xdotool לוחץ "הבא";
  הסרטון מוקלט ב-ffmpeg (x11grab, בלי סמן העכבר) ומראה את ההנפשה של insert / remove. `intellij-*.webp` — מהתוסף ל-IntelliJ,
  `mentor.webp` / `solution.webp` / `add-structure.webp` — מ-Eclipse. `og.png` — ראש העמוד עצמו ב-1200×630.
- **ההורדה** — הקישור הקבוע של `release/DSViz-Setup.exe` ב-master (גם `is.gd/dsjava`), וב-Mac שורת הטרמינל הקבועה
  (`installer/mac/install.sh` ב-master). הכפתור הראשי לפי מערכת ההפעלה (`<html data-os>`, שורה קצרה בראש כל עמוד; לבדיקה `?os=mac`),
  והשני תמיד גלוי.
- **המונה** (`assets/downloads.js`, בכל עמוד): לחיצה על הורדה ל-Windows שולחת ברקע `POST /v1/count/windows`, והעתקה של שורת ה-Mac
  (כפתור או Ctrl+C) — `POST /v1/count/mac`, פעם אחת לכל טעינה (sendBeacon / keepalive — ההורדה לא מחכה לשרת). המספר מ-`/v1/stats`,
  רק כשה-KV בשם STATS מוגדר (`worker/README.md`); אחרת לא מוצג כלום. כשהמונה נכנס למסך הוא עולה מ-0 למספר האמיתי (פחות משנייה,
  ברוחב שנשמר מראש). תאריך הגרסה מ-`release/version.json` (`assets/site.js`).
- **שאלת בגרות לקוד** (`bagrut.html`, `assets/bagrut.js`): הקבצים מוכנים בדפדפן (Word נפתח עם JSZip מ-cdnjs, תמונות מוקטנות),
  נשלחים ל-`/v1/site/exercise` בשרת, והקוד נבנה בדפדפן ב-`assets/scaffold.js` — העתק של `AiScaffold.java` מהתוסף
  (`tools/test/scaffold-parity.js` בודק שהם מפיקים בדיוק אותו קוד). השרת מקבל בקשות כאלה רק מהכתובת של האתר (`SITE_ORIGINS`).
- **תמכו בפרויקט** — התג בסרגל העליון ובתחתית כל עמוד, לדף התרומה של PayPal (`paypal.com/ncp/payment/DJYTDJ6F57KA2`, אותו קישור
  כמו בחלון ההרצה). בעמוד הראשי גם כפתור PayPal (Hosted Button): ה-SDK נטען רק כשגוללים לאזור, ואם הוא חסום נשאר הקישור.
- **IntelliJ** — הקטע `data-block="intellij"` ב-`install.html`, בין הערות שמסמנות אותו, כדי שיהיה קל לעדכן.

## בדיקות
`tools/test/run-tests.sh` — 9א (scaffold.js מול Eclipse), 9ב (`site-bagrut-test.js`: שאלת בגרות ומונה), 9ג (`site-check.js`: כל העמודים
ב-1366 וב-390, קישורים, meta, הכפתור לפי מערכת ההפעלה, ספירה, תרומה, הנגן של שליחת תרגיל — כולל שהקוד בו זהה ל-scaffold.js —
נסו בעצמכם, קיצורי המקלדת, "מה חדש", וצילומים של כל אזור ושל כמה רגעים בהנפשות). מקומית: `node tools/test/site-check.js /tmp/shots`.

## פרסום
GitHub Pages דרך `.github/workflows/dsviz-site.yml` (בשורש המאגר): כל שינוי בתיקייה הזו ב-master מתפרסם ל-
https://motiml77.github.io/java-lamerhav/ . פעם אחת צריך להפעיל ב-GitHub: **Settings → Pages → Source: GitHub Actions**.
תצוגה מקדימה בלי הגדרות: https://raw.githack.com/motiml77/java-lamerhav/master/java-visualizer/site/index.html
