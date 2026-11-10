# הכניסה המשותפת (dsviz-access.jar)

קוד אחד לכניסה (מורה / תלמיד / עמדת מחשב) לכל התוכנות במחשב: Eclipse, IntelliJ, Visual Studio (המאגר וסרגל הכלים),
VS Code ו"מודלים חישוביים". מי שנכנס באחת — מחובר בכולן, והתנתקות באחת מנתקת בכולן, מיד.

```
sh tools/build-access.sh        → build/access/dsviz-access.jar   (Java 8, רק מודולים שב-Java של ההתקנה)
```

| קובץ | מה |
|---|---|
| `Account.java` | כניסה עם Google בדפדפן (loopback + PKCE דרך `/signin` של השרת), חיבור עמדה, בדיקה מול השרת, התנתקות, השמירה המוצפנת |
| `AccessGate.java` | מסך הכניסה (Swing) ו-`onChange` (בחוט של Swing) |
| `AccessWatch.java` | שינויים מתוכנה אחרת: `AccessWatch.listen(r)` — WatchService על התיקיות + בדיקה קלה כל ‎1.5‎ שניות |
| `OsTrust.java` | תעודות ממאגר מערכת ההפעלה (סינון אינטרנט שבודק תעבורה מוצפנת) |
| `Json.java` | JSON קטן (משמש גם קוד אחר) |

החבילה נשארה `il.lamerhav.dsviz` — מי שמשתמש בה לא השתנה. ה-jar נכנס כמו שהוא (המחלקות שלו מחולצות) ל:
`dsviz.jar` (tools/build-lib.sh), התוסף של Eclipse (tools/build-all.sh), `dsviz-bank-vs.jar` (tools/build-bank-vs.sh),
התוסף של IntelliJ (`implementation(files(...))` ב-intellij-plugin/build.gradle.kts → `lib/dsviz-access.jar`).

## איפה נשמרת הכניסה (מקום אחד לכל התוכנות, נסתר)

| | Windows | Mac | Linux |
|---|---|---|---|
| המשתמש (`a.dat` מורה / תלמיד, `o.dat` התנתקויות בלי אינטרנט, `i.dat`, `teacher.flag`, `.lock`) | `%LOCALAPPDATA%\Lamerhav\.cache` | `~/.local/share/.lamerhav` | `~/.local/share/.lamerhav` |
| גיבוי | `%APPDATA%\Lamerhav\.cache` | `~/.config/.lamerhav` | `~/.config/.lamerhav` |
| כל המחשב (`s.dat` עמדה, `i.dat`) | `%ProgramData%\Lamerhav\.cache` | `/Users/Shared/.lamerhav` | (אצל המשתמש) |

- הקבצים מוצפנים (AES-GCM, מפתח לפי המחשב) ומסומנים כנסתרים ב-Windows. האישור בתוכם חתום בשרת (RSA) ונבדק בכל קריאה.
- `teacher.flag` — לא סוד (מייל ושם): סרגל הכלים של Visual Studio (C#) מציג לפיו את "עמדת ניהול". עד גרסה 1.0.1 הוא היה
  ב-`%LOCALAPPDATA%\DSViz\teacher.flag`; Java מוחקת את הישן, והסרגל קורא גם ממנו (לגרסה אחת) ועוקב אחרי שתי התיקיות.

## כמה תוכנות בבת אחת

- **נעילה**: כל כתיבה / מחיקה עוברת ב-`Account.locked` — `synchronized` בתוך התהליך ו-`FileLock` על `.lock` בתיקיית
  המשתמש בין התהליכים (עד 5 שניות; בלי הרשאה / תוכנה תקועה — כותבים בלי נעילה, כמו קודם). קובץ זמני ייחודי לכל כתיבה,
  והחלפה אטומית (move) — קורא אף פעם לא רואה חצי קובץ. המזהה `i.dat` נוצר בתוך הנעילה (שתי תוכנות שעולות יחד בפעם הראשונה
  לא יוצרות שני מזהים).
- **בדיקה מול השרת** (`refresh`) שומרת רק אם בקובץ עדיין אותו כרטיס (`storeIfSame` / `forgetIfSame`) — בדיקה ארוכה בתוכנה
  אחת לא מחזירה כניסה שתוכנה אחרת ניתקה בינתיים.
- **שינויים**: `AccessWatch` מודיע רק כשמי שמחובר באמת השתנה (`Account.fingerprint` — סוג, מייל, שם, תוקף, מצב; בלי הכרטיס),
  לא על כל שמירה מחדש. `AccessGate.onChange` מפעיל אותו לבד (Eclipse: הכפתור "עמדת ניהול" מתעדכן מיד גם כשהכניסה /
  ההתנתקות נעשו ב-Visual Studio).

## בדיקות

```
cd worker && npm run test:access-java    # SignInEdge, OsTrustTest, AccountIt, GateUi, TeacherPanelIt, ShareIt (שני תהליכים)
```
