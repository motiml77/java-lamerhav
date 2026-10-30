# סרטוני ההדרכה (הכפתור ?)

חמש הדרכות קצרות, לכל סביבה סרטונים משלה:
- **Eclipse:** `../help/a.mp4` … `e.mp4`.
- **VS Code:** `../help/vscode/…`.
- **Visual Studio / C#:** `../help/vs/…`.

בכל תיקייה יש גם פוסטרים, דף בחירה (`index.html`) ודף נגן להדרכה אחת (`player.html#a`).

| | הדרכה |
|---|---|
| א | יצירת מבנה נתונים חדש — לשים את הסמן במקום הנכון, "הוסף מבנה נתונים", האפשרויות, הקוד שנוצר וההרצה |
| ב | דוגמאות פתורות — פותחים דוגמה מוכנה ומריצים אותה בחלון ההדמיה |
| ג | מבנה בתוך מבנה — שלוש דוגמאות (מחסנית של תורים, רשימה של מחסניות, עץ של תורים) |
| ד | התאמות ידניות אחרי יצירת התרגיל — מבנה נוסף, משתנה x ומערך — ושגם הם מופיעים בהדמיה |
| ה | שליחת תרגיל — דף התרגיל (Ctrl+V / PDF / Word) → קוד מוכן, ו"💡 הצעה לפתרון" שנבדקה לכל סעיף |

**הסביבות:** `node make.js <id> --ide vscode|vs` — אותו מנוע עם המסגרת של הסביבה (`engine.html?ide=`). החלונות מצולמים מהכלי האמיתי של כל סביבה:
- **VS Code:** דפי ה-Webview של התוסף, בדפדפן — `harness/webview.js`.
- **Visual Studio:** החלונות של C# — `harness/SwingScript.java`.
- **ההרצה של C#:** dsviz-cs ← TraceViewer — `viewer-cs.sh`.

כמה הרצות במקביל: `XD=:95` (מסך מדומה נפרד).

כל מה שרואים בסרטון הוא **אמיתי**: החלונות של התוסף ("הוסף מבנה נתונים", "תרגיל חדש", "דוגמאות פתורות") מצולמים מהתוסף עצמו,
וחלון ההרצה הוויזואלית — מהרצה אמיתית של הקוד שמופיע בעורך. העכבר, הלחיצות וההדגשות מנוהלים לפי מיקומי הרכיבים האמיתיים.
רק מסגרת הסביבה (העורך, התפריט וסרגל הכלים) מצוירת מחדש.

## איך מייצרים מחדש (Linux: Xvfb, Java, Node + Playwright, ffmpeg, Python עם Pillow ו-numpy; אחרי `tools/build-all.sh`)

```sh
cd java-visualizer/tutorials
mkdir -p fonts && (cd fonts && npm init -y && npm i @expo-google-fonts/karantina @expo-google-fonts/secular-one \
  @expo-google-fonts/suez-one @expo-google-fonts/heebo @expo-google-fonts/rubik-mono-one @expo-google-fonts/jetbrains-mono)
node make.js a          # מצלם את החלונות והריצה (scripts/a.prep.js), מרנדר, מוסיף פסקול ומקודד ל-../help/a.mp4
node make.js b && node make.js c && node make.js d
node ../tools/build-help.js     # דף הבחירה help/index.html + המשכים
```

* `scripts/<id>.prep.js` — מה מצלמים: חלונות התוסף לפי תסריט (`harness/DlgScript.java`, שומר גם JSON עם מיקומי הרכיבים) והרצה ויזואלית (`viewer.sh`).
* `scripts/<id>.js` — התסריט של הסרטון: מתי העכבר זז ולוחץ, איזה חלון נפתח, מה נכנס לקוד, איזה צעד בהרצה.
* `engine.html` + `engine.js` — המנוע (ציר זמן אחד, `render(t)`); `audio.py` — פסקול סינתטי + קליקים; `make.js` — הרינדור והקידוד.
* `--prep-only` / `--render-only` — רק חלק מהשלבים; `--times 12,30` — צילומי פריים בודדים לבדיקה.
