# C# — הרצה ויזואלית (dsviz-cs)

```
csharp/
├── Unit4.CollectionsLib/   הספרייה (netstandard2.0): Stack<T>, Queue<T>, Node<T>, BinNode<T>, StackInt, Student, Car,
│                           ו-Unit4.BinTreeUtilsLib.BinTreeUtils (כמו ב-Unit4.dll של בתי הספר)
│                           + Recorder (התיעוד) + VizHooks (מה שנכנס לקוד של התלמיד)
├── DsViz.Runner/           dsviz-cs: מקמפל (Roslyn), מכניס דיווח לפני כל שורה (Instrumenter), מריץ וכותב קובץ הרצה
└── project/                DataStructures-CS: פתרון Visual Studio — Exercise + 8 דוגמאות (נבנה לתיקיית מסמכים בהתקנה)
```

## איך זה עובד

1. `dsviz-cs <תיקייה | .csproj | קובץ .cs>` קורא את כל קובצי ה-.cs של הפרויקט, ומקמפל אותם **כמו שהם** (שגיאות: `Program.cs(12,5): error CS1002: ...`).
2. `Instrumenter` (Roslyn) מכניס:
   - `VizHooks.Enter/Exit/Fault` סביב כל פעולה (מחסנית הקריאות, ערך החזרה: `return Ret<T>(x)`),
   - `VizHooks.Ln(שורה, "שם", ערך, ...)` לפני כל משפט — ובתנאי של `while` / `for` / `do` בכל סיבוב — עם כל המשתנים שבתחום ומוגדרים (לפי `SemanticModel`).
3. הקוד המשונה מקומפל בזיכרון ורץ **באותו תהליך**. `Recorder` מצלם את כל המבנים בכל צעד (מחסניות, תורים, רשימות, עצים, מערכים — לפי המשתנים שבמסגרות).
4. בסוף נכתב קובץ צעדים, ו-`java ... unit4.collectionsLib.TraceViewer <קובץ>` פותח את אותו חלון כמו ב-Eclipse / VS Code
   (`TraceViewer` טוען את הצעדים ל-`Recorder.adopt`, ומשם החלון עובד כמו בהרצה שורה-שורה של Java).

בהרצה רגילה (בלי dsviz-cs) ההוקים לא קיימים והספרייה היא סתם מבני נתונים — אין תיעוד ואין חלון.

## כל פרויקט ב-Visual Studio, בלי שום פעולה (installer/DSViz.targets)

ההתקנה (חבילת C#) מעתיקה את `installer/DSViz.targets` ל-
`%LOCALAPPDATA%\Microsoft\MSBuild\Current\Microsoft.CSharp.targets\ImportAfter\` — נקודת הרחבה של MSBuild ברמת המשתמש
(בלי הרשאות מנהל) שכל בנייה של פרויקט C# מייבאת: Visual Studio (גם הבנייה של עיצוב-זמן כשהפרויקט נטען), `dotnet build`, ו-C# Dev Kit.
ההסרה מוחקת את הקובץ. העיקרון: **פרויקט שאין לו קשר ל-DSViz נבנה בדיוק כמו בלי DSViz**. הקובץ פועל רק בפרויקט **קונסול**
(`OutputType` = `Exe`) שהקוד שלו מזכיר את `Unit4.CollectionsLib`, או שמסומן כפרויקט של DSViz (`<DSVizProject>true</DSVizProject>` —
את הסימון שם `Directory.Build.props` של תיקיית התרגילים):
- **`Unit4.CollectionsLib.dll`** מתיקיית ההתקנה מתווסף **אחרי** `ResolveAssemblyReferences` — רק אם אף אחת מההפניות שנפתרו אינה `Unit4*`
  (הספרייה שלנו, או `Unit4.dll` של בית הספר — גם כשהיא מגיעה מ-`ProjectReference` לפרויקט בשם אחר), ורק ל-‎.NET Core 2.0+ / ‎.NET 5+ /
  ‎.NET Framework 4.7.2+. כך אין CS0433: ההרצה הרגילה משתמשת במה שהפרויקט מפנה אליו, וההרצה הוויזואלית (dsviz-cs) — תמיד בספרייה של DSViz.
  ב-‎.NET Framework מתווסף גם `netstandard.dll`.
- **`using` מוסתר**: כשהקוד משתמש בספרייה ולא באוספים של ‎.NET — `System.Collections.Generic` יוצא מהרשימה המוסתרת (אחרת CS0104).
  `global using Unit4.CollectionsLib` האוטומטי — רק בפרויקט מסומן. פרויקט רגיל עם `Stack<int>` של ‎.NET לא משתנה.
- **פרופיל "הרצה ויזואלית"** ב-`Properties\launchSettings.json` (פרויקט SDK של ‎.NET 6+ שמשתמש בספרייה / מסומן): אם הפרופיל לא בקובץ, רץ
  `dsviz-cs.exe --ensure-profile <תיקייה>` — ממזג את הפרופיל (ו"הרצה רגילה" אם אין פרופיל `Project`), משאיר את הפרופילים הקיימים,
  ולא נוגע בקובץ פגום. **"הרצה רגילה" ראשונה** — הפרופיל הראשון הוא ברירת המחדל של הכפתור הירוק / F5 / Ctrl+F5, והם מריצים
  כרגיל, בקונסול; קובץ מגרסה ישנה שבו "הרצה ויזואלית" הייתה ראשונה מתוקן פעם אחת. כשהכול תקין — לא רץ כלום (הבנייה לא מתעכבת).
  פרופיל מגרסה ישנה (`"\"$(ProjectDir)\""` — ProjectDir נגמר ב-`\`, וב-Windows הנתיב מגיע עם מרכאה בסוף) מתוקן ל-`"\"$(ProjectDir).\""`.
  dsviz-cs עצמו מקבל גם ארגומנט כזה (מרכאות בקצוות, `C:\dir" --trace x` שנבלע לארגומנט אחד), ונתיב שלא קיים — הודעה בעברית, קוד 2.

## הספרייה של בתי הספר (Unit4.dll)

בתי הספר מקבלים `Unit4.dll` (‎.NET Framework 4.0, מתיקיית Unit4CSharp של המפקחים): `Unit4.CollectionsLib` — Stack, Queue, Node, BinNode,
StackInt; `Unit4.BinTreeUtilsLib.BinTreeUtils` — פעולות שירות לעץ; `Unit4.BinTreeCanvasLib.TreeCanvas` — ציור עץ בחלון; ועוד ספריות גרפיות
(`Unit4.GridLib`, `TurtleLib`, `FrogLib`, `TowerLib`, `HanoiLib`, `CardLib`, `PolygonLib`, `OrganLib`, `DiceLib`, `DreidelLib`, `BucketLib`) שלא רלוונטיות להרצה הוויזואלית.

**התבנית של בתי הספר** מתחילה לרוב ב-`using Unit4.BinTreeCanvasLib; using Unit4.BinTreeUtilsLib; using Unit4.GridLib;` גם כשלא משתמשים בהן.
`TreeCanvas` קיים גם בספרייה של DSViz (`Unit4.CollectionsLib/TreeCanvas.cs`): בלי חלון — העץ כבר מצויר בתצוגה — אבל עם אותו פלט לקונסול
(`PrintOutAllElements(true)` ← הערכים בסדר המעבר, `"1, 2, 4"`; ב-`StopAfterAllSteps` ירידת שורה בסוף, בלי לחכות למקשים) וצעד בתצוגה לכל מעבר.
`using` לספרייה גרפית שאין אצלנו (`GridLib`...) — ההרצה הוויזואלית מורידה אותו (רק כשהמהדר לא מוצא אותו; מוחלף ברווחים, כך שמספרי השורות
לא זזים — `DsViz.Runner/SchoolUsings.cs`) ומקמפלת שוב. קוד שבאמת משתמש ב-`Grid` / `Turtle`... — הסבר בעברית שזו ספרייה גרפית של בית הספר,
וההרצה הרגילה לא מושפעת. נבדק ב-`cs-tests.sh`, סעיף כח, מול הקובץ האמיתי.
ההרצה הוויזואלית מקמפלת תמיד מול הספרייה של DSViz, ולכן יש בה **כל** ה-API של האוספים ושל BinTreeUtils, באותה התנהגות (גובה עלה 0, IsFull = עץ
מלא, BuildRandomTree עם אותה התפלגות צורות, NullReferenceException על עץ ריק — עם הסבר בעברית). ההבדל היחיד בהדפסה — `Node.ToString`: אצלנו כל
השרשרת (`4 -> 8 -> null`), שם רק הערך; כשהפרויקט מפנה ל-Unit4.dll של בית הספר, dsviz-cs מדליק `Recorder.SchoolText` ומודפס רק הערך — אותו פלט
כמו בהרצה הרגילה. נבדק ב-`tools/test/cs-tests.sh` מול הקובץ האמיתי (מורד ונבדק מול sha256 — `helpers/school-unit4.sh`; לא נשמר במאגר):
השוואת ה-API חבר-חבר (`helpers/ApiCompare.cs`) ואותו פלט בשתי ההרצות (`helpers/SchoolProgram.cs`).

לא נוגע בספריות / פרויקטי בדיקות / פרויקטים שאינם C#, ולא אם יש `<DSVizDisable>true</DSVizDisable>` בפרויקט. אף שלב לא מכשיל את הבנייה
(ההתקנה חסרה, DLL חסר, Exec נכשל). פרויקט שהיה פתוח ב-Visual Studio בזמן ההתקנה — סוגרים ופותחים פעם אחת.
נבדק ב-`tools/test/cs-tests.sh`, סעיף 10 (מדמה את `%LOCALAPPDATA%` ב-Linux עם `XDG_DATA_HOME`).
ב-VS Code אין צורך בזה: התוסף מוצא את `dsviz-cs` לבד (`~/.dsviz/cs-path.txt` שההתקנה כותבת, או `%LOCALAPPDATA%\DSViz\cs`) ומריץ אותו על כל פרויקט.

### קלט מהמקלדת (Keyboard, LiveViewer)
`Console.In` של התוכנית מוחלף בקורא (`Keyboard`) שמאחד את הקלט האמיתי (חלון הקונסול / הטרמינל / צינור), שורות שמוקלדות בחלון,
וקלט מוכן לבדיקה — מה שמגיע קודם. כל שורה שהתוכנית מקבלת נכנסת לפלט ונרשמת כצעד `INPUT` ("קלט מהמקלדת: 5").
כשהתוכנית מחכה ואין קלט, החלון נפתח **כבר עכשיו** (`TraceViewer --live`) עם כל הצעדים עד כאן: השורה שקוראת מסומנת
"⌨ מחכה לקלט מהמקלדת" ונפתח פס ההקלדה — כמו ב-Java. מכאן הצעדים נשלחים לחלון תוך כדי ההרצה (ב-stdin שלו, אותן שורות של
קובץ הצעדים במנות שמסתיימות ב-`SYNC`, ובין המנות `ASK` / `RESUME` / `DONE`), ומה שמוקלד חוזר ב-stdout שלו (`DSVIZ-IN` טקסט,
`DSVIZ-EOF` = "סיום הקלט"). סגירת החלון באמצע עוצרת את ההרצה. תוכנית שלא קוראת קלט — כמו קודם: החלון נפתח בסוף, מקובץ.

בלי חלון (`--trace`, בדיקות): `--input קובץ` או `DSVIZ_INPUT` (`"3\n4"` או `@קובץ`, UTF-8) מקלידים שורה בכל פעם שהתוכנית מחכה
(`DSVIZ_INPUT_DELAY` — מילישניות לפני כל שורה); אחריהן — הקלט האמיתי, ובסופו `ReadLine()` מחזירה `null` (צעד "סוף הקלט").

## סדר החיפוש של Java והספרייה (ViewerLauncher)
`DSVIZ_JAVA` / `DSVIZ_JAR` (משתני סביבה) → `..\jdk` ו-`..\dsviz.jar` ליד `dsviz-cs.exe` (כך מותקן) → `~/.dsviz/jdk-path.txt` → `JAVA_HOME` → `PATH`.

## פורמט קובץ הצעדים
טקסט UTF-8, שורה לרשומה, שדות מופרדים ב-TAB. בתוך שדה: `\\` `\t` `\n` `\r` מוסתרים, ו-`\0` = null.

| רשומה | שדות |
|---|---|
| `DSVIZ` | `1`, `cs` |
| `SRC` | שם קובץ, הטקסט כולו (קוד המקור — החלון מציג אותו) |
| `STEP` | סוג (`LINE` `CREATE` `CHANGE` `READ` `CHECK` `OUTPUT` `NOTE` `ERROR` `END` `RETURN` `INPUT`), אורך הפלט עד כאן, ערך החזרה (`\0` = אין), טקסט |
| `FR` | מחלקה, פעולה, קובץ, שורה — מסגרת קריאה (העליונה ראשונה) |
| `VARS` / `VAR` | המשתנים של הפעולה העליונה: שם, ערך |
| `V LIN` | מחסנית (1) / תור (0), focus, כותרת, אינדקס סימון, סימון, ghost — ואחריו `I` לכל איבר |
| `V CHAIN` | focus, כותרת, מעגל-אל, טקסט-חיבור, חתוך — ואחריו `C` לכל חוליה: ערך, סימון, current, תוויות (מופרדות ב-`\u0001`) |
| `V TREE` | focus, כותרת — ואחריו צמתים בסדר תחילי: `N` ערך, סימון, current, shared, תוויות; `NULL` = אין צומת |
| `V ARR` / `V MAT` | focus, כותרת — ואחריו `I` / `ROW`, `CH` (השתנה), `IL` / `RL` / `CL` (תוויות אינדקס) |
| `O` / `F` | אובייקט של מחלקה (כרטיס): מזהה, טיפוס, כותרת, שורת פרטים, תוויות — ואחריו `F` לכל מאפיין: שם, ערך. מופיע אחרי `I` / `C` / `N` / `ROW` של איבר שהוא אובייקט; ב-`V ARR`/`V MAT` של מטריצה `OBJAT` מציין את העמודה |
| `SUB` / `ENDSUB` | (אחרי `I` / `C` / `N`, ואחרי ה-`O` שלהם אם יש) מבנה בתוך התא: שורות `V ...` של המבנה הפנימי (אפשר שוב `SUB` בתוכו), ו-`ENDSUB` |
| `V OBJ` | אובייקט שמשתנה מצביע אליו ואינו בתוך מבנה: כותרת — ואחריו `O` + `F` |
| `ACC` | (אחרי `STEP`) גישה למאפיין: שם מאפיין, טקסט הפעולה (`GetName()`), סימון (`READ` / `CHANGED`) — ואחריו `O` + `F` של האובייקט |
| `WAIT` | (אחרי הצעד שבו התחילה) התוכנית חיכתה לקלט: מספר הצעד, מחלקה, פעולה, קובץ, שורה, הקריאה (`Console.ReadLine()`), מה מחכים לו (`מספר שלם`) |
| `LOOP` | (אחרי `STEP ERROR` האחרון) התוכנית נעצרה בלולאה שלא נגמרת: ההחלטה של LoopGuard (`loop`, סוג, שורת התנאי, סיבובים, זמן, ...) — החלון מראה ממנה את הכרטיס (LoopCard) |
| `OUT` | הפלט (כמה שורות `OUT` — מצטרפות) |

אפשר לבדוק קובץ בלי חלון: `java -cp dsviz.jar unit4.collectionsLib.TraceViewer --check trace.txt`.

## בנייה ובדיקות
```sh
tools/build-all.sh                 # בונה הכול (כולל dsviz-cs ל-Windows, self-contained, ~97MB לפני דחיסה)
sh tools/test/cs-tests.sh          # בדיקות C#: תבניות (2331 קבצים מתקמפלים), הרצה ויזואלית של כל הדוגמאות, שגיאות
```
דורש ‎.NET SDK 8 (רק למי שבונה). התבניות ל-VS Code (`vscode-extension/templates-cs.js`) מופקות מאותו מחולל של Java בהמרה מכנית — מקור אחד.

## חלונות "תרגיל חדש" ו"הוספת משתנה" (Visual Studio / Rider)

`csharp/new-exercise/src/il/lamerhav/dsviz/cs/` — חלונות Swing שנארזים ב-`dsviz.jar` ומופעלים מקיצורים שההתקנה יוצרת:
`javaw -cp dsviz.jar il.lamerhav.dsviz.cs.CsTool new|insert <תיקיית DataStructures-CS>`.
`ScaffoldCs` ממיר את הקוד של `Scaffold` (Eclipse) ל-C# באותה המרה כמו `vscode-extension/templates-cs.js` (נבדק קובץ-קובץ ב-`tools/test/cs-tests.sh`, סעיף 8).
`CsProject` כותב `Exercises\<שם>\` (`.csproj`, `Program.cs`, `launchSettings.json`) ורושם את הפרויקט בקובץ ה-`.sln`.

## סרגל הכלים של DSViz ב-Visual Studio (הרחבה)

`csharp/vs-extension/` — הרחבה ל-Visual Studio 2022 ומעלה (VSIX רגיל, בתוך התהליך, מול ה-SDK של 17.0) עם סרגל כלים בפני עצמו, בשורה משלו
(View ← Toolbars ← "DSViz"): תרגיל חדש · הוספת מבנה נתונים · מאגר שאלות · השאלה · שליחת תרגיל · הרצה ויזואלית · הדרכה · **כיבוי DSViz** (אדום, בסוף).
כל כפתור מפעיל את הכלי של ההתקנה (`dsviz-cs.exe --tool <מצב>` — אותם חלונות כמו בקיצורים), וההרצה הוויזואלית מריצה את קובץ ה-C# הפתוח.
- **הכפתור הירוק נשאר של Visual Studio**: ההרחבה לא מבטלת ולא מחליפה את ההרצה הרגילה. בפתרון התרגילים של DSViz הפרויקט של הקובץ הפתוח
  נהיה ה-Startup Project; בכל פתרון — כש-Visual Studio מריץ פרויקט אחר מהקובץ הפתוח (למשל קובץ שלא שייך לאף פרויקט), שורת הודעה
  מסבירה מה רץ, עם כפתור "הרצה ויזואלית" של הקובץ.
- **כיבוי DSViz** (`Power.cs`): אחרי אישור, כל שאר הכפתורים נעלמים ובמקום האדום מופיע הירוק **הפעלת DSViz**. כשכבוי אין שורות הודעה, אין החלפה
  של ה-Startup Project, אין מנורות "הצעה לפתרון" ושום כלי של DSViz לא נפתח — Visual Studio רגיל. `DSViz.targets` **לא** נכבה (הוא של MSBuild,
  לא של ההרחבה), כדי שקוד שמשתמש בספריית הקורס ימשיך להיבנות ולרוץ. המצב נשמר ב-`%LOCALAPPDATA%\DSViz\vs-power-off.flag` (קיים = כבוי).
- מספר הגרסה עולה בכל בנייה (1.0.‹שנה ויום›.‹דקה›), כך שעדכון מתקין מעל הקיים. בכל טעינה ההרחבה כותבת
  `%LOCALAPPDATA%\DSViz\vs-loaded-<מופע>.txt` (גרסה ושעה) — `DSViz-Diagnose.bat` מציג אותו (מותקנת ≠ נטענת).
- נבנית ב-CI של Windows (`.github/workflows/dsviz-vsix.yml`, `tools/test/windows-vs/build-vsix.ps1`), שמתקין אותה גם ב-Visual Studio האמיתי
  של המכונה ומצלם; ה-VSIX נשמר ב-`vs-extension/prebuilt/`. `tools/test/vs-extension-compile.sh` מקמפל את הקוד גם ב-Linux.
  הבדיקה המלאה, בלחיצות עכבר אמיתיות ב-2022 וב-2026: `.github/workflows/dsviz-vs-matrix.yml` (`tools/test/windows-vs-matrix/`).
- `tools/build-all.sh` אורז אותו להתקנה, ו-`installer/install-vsix.ps1` מתקין אותו בכל Visual Studio (Community / Professional / Enterprise,
  מ-2022) ומסיר בהסרה. `tools/test/install-vsix-test.sh` בודק את הלוגיקה שלו עם Visual Studio מדומה.
- גיבוי בכל גרסה של Visual Studio: אותם כלים כפרופילי הרצה בתפריט שליד הכפתור הירוק ("תרגיל חדש", "הוספת מבנה נתונים", "שליחת תרגיל", "הצעה לפתרון") — `DSViz.targets` ← `dsviz-cs --ensure-profile`.

