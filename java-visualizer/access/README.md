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

## מול השרת: איזו תוכנה

`/v1/auth/redeem` ו-`/v1/station/claim` מקבלים שדה לא חובה `app` (`dsviz` / `automata`; `Account.app`, ברירת מחדל `dsviz`,
"מודלים חישוביים" — `automata`). הוא נרשם ביומן הפעילות (`app=automata`); ערך לא מוכר / לקוח ישן — בלי שינוי.
באישור החתום יש `apps` (בינתיים תמיד `["dsviz","automata"]`); `Account.Access.apps` / `opens(app)` — אישור ישן בלי השדה פותח בשתיהן.

## "מודלים חישוביים": התוכנה המקומית והממשק לדף

`automata-launcher/` (`il.lamerhav.dsviz.AutomataLauncher`, `tools/build-automata.sh` → `build/automata/dsviz-automata.jar`
עם dsviz-access בפנים, ו-`build/automata/app`). בהתקנה: `<התקנה>\automata\` ; הקיצור "מודלים חישוביים" מריץ
`javaw -cp "automata\dsviz-automata.jar;dsviz.jar" il.lamerhav.dsviz.AutomataLauncher`.

- שרת על `127.0.0.1` בלבד. הפורט נבחר באקראי בפעם הראשונה ונשמר (`%LOCALAPPDATA%\DSViz\automata\port`) — כדי שהשמירה
  של הדף (localStorage: העבודה, ההעדפות, ההתקדמות במאגר) לא תאבד בכל הפעלה; תפוס — פורט אחר.
- מפתח אקראי לכל הפעלה (`X-DSViz-Token`). הוא לא בשורת הפקודה: החלון נפתח עם קוד חד-פעמי (`/launch?c=…`, דקה אחת,
  פעם אחת) שמחזיר 302 ל-`/#dsviz-token=<מפתח>`; `access-client.js` שומר אותו ב-sessionStorage ומוחק אותו מהכתובת.
- Windows: Edge `--app=<כתובת>` עם פרופיל משלו (`%LOCALAPPDATA%\DSViz\automata\edge`); בלי Edge — הדפדפן הרגיל.
  Mac: הדפדפן הרגיל (`open`). הפעלה שנייה (עוד לחיצה על הקיצור) — לא שרת שני: קוד פתיחה מהשרת שרץ ועוד חלון.
- יציאה: כשאין דף פתוח — זרם האירועים נסגר ואין פנייה ‎30‎ שניות (בהתחלה — עד ‎2‎ דקות לחלון לעלות).
- בדיקות אבטחה בכל בקשה: `Host` = ‏`127.0.0.1:<port>` / `localhost:<port>` (נגד DNS rebinding); `Origin` (אם נשלח) — רק
  השרת עצמו; `Sec-Fetch-Site` (אם נשלח) — `same-origin` / `none`; `/api/*` — רק עם המפתח (השוואה בזמן קבוע), POST — רק
  `application/json`. קבצים — רק מתוך תיקיית האפליקציה. אף תשובה לא כוללת כרטיס, אישור או סוד.

| | בקשה | תשובה |
|---|---|---|
| מי מחובר | `GET /api/access` | `{state: "signed-out"/"teacher"/"student"/"station"/"pending"/"blocked", name, email, role, apps, offline, until, teacher, signing, canSignOut}` |
| כניסה עם Google | `POST /api/signin {role: "teacher"/"student"}` | `{ok, status: "waiting"}` מיד; בסוף — אירוע `signin` `{ok, status, message}` |
| ביטול כניסה | `POST /api/signin-cancel` | `{ok}` |
| עמדת מחשב | `POST /api/station {code, name}` | `{ok, status: "ok"/"bad-code"/"full"/"blocked"/"offline"/…, message}` |
| התנתקות | `POST /api/signout` | `{ok, status, message}`; עמדה — `{ok: false, status: "station"}` (רק המורה משחרר) |
| עמדת הניהול | `POST /api/teacher-panel` | `{ok}` (פותח את החלון של DSViz) / `{ok: false, status: "not-teacher"}` |
| אירועים | `GET /api/events` (SSE) | `event: access` (מיד, ובכל שינוי — גם מ-Eclipse / Visual Studio), `event: signin` |

`pending` — כניסה שמחכה לדפדפן, או תלמיד שעוד לא שילם; `blocked` — הגישה הושבתה.

בדף: `automata/app/js/access-client.js` (ES module) — `DSVizAccess.available()`, `get()`, `signIn(role)` (מחזיר את התוצאה
בסוף הכניסה), `cancelSignIn()`, `claimStation(code, name)`, `signOut()`, `openTeacherPanel()`, `onChange(cb)` (מחזיר פונקציה
להפסקה). התוכנה מוסיפה אותו לבד ל-index.html שהיא מגישה (הוא מחזיק את זרם האירועים — כך היא יודעת שהחלון פתוח); בלי
התוכנה (קובץ / אתר) — `available()` הוא false ו-`get()` מחזיר `{state: "unavailable"}`. אם באפליקציה אין את הקובץ — מוגש העותק
שבתוך ה-jar.

בדיקות: `AutomataIt` ב-`npm run test:access-java` (אבטחה, הממשק, כניסה / התנתקות / עמדה משותפות, הפעלה שנייה, יציאה כשאין
דף; `AUTOMATA_APP=<תיקייה>` — עם האפליקציה האמיתית).
