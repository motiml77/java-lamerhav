# C# — הרצה ויזואלית (dsviz-cs)

```
csharp/
├── Unit4.CollectionsLib/   הספרייה (netstandard2.0): Stack<T>, Queue<T>, Node<T>, BinNode<T>, Student, Car
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
ההסרה מוחקת את הקובץ. בכל פרויקט **קונסול** (`OutputType` = `Exe`) ב-C#:
- **הפניה ל-`Unit4.CollectionsLib.dll`** מתיקיית ההתקנה, לפני `ResolveAssemblyReferences` — רק אם הפרויקט עוד לא מפנה לספרייה
  (`Reference` בשם קצר/מלא או לפי `HintPath`, או `ProjectReference`), ורק ל-‎.NET Core 2.0+ / ‎.NET 5+ / ‎.NET Framework 4.7.2+.
- **פרופיל "הרצה ויזואלית"** ב-`Properties\launchSettings.json` (פרויקט SDK, ‎.NET 6+): אם הפרופיל לא בקובץ, רץ
  `dsviz-cs.exe --ensure-profile <תיקייה>` — ממזג את הפרופיל (ו"הרצה רגילה" אם אין פרופיל `Project`), משאיר את הפרופילים הקיימים,
  ולא נוגע בקובץ פגום. כשהפרופיל כבר שם — לא רץ כלום (בנייה רגילה לא מתעכבת).

לא נוגע בספריות / פרויקטי בדיקות / פרויקטים שאינם C#, ולא אם יש `<DSVizDisable>true</DSVizDisable>` בפרויקט. אף שלב לא מכשיל את הבנייה
(ההתקנה חסרה, DLL חסר, Exec נכשל). פרויקט שהיה פתוח ב-Visual Studio בזמן ההתקנה — סוגרים ופותחים פעם אחת.
נבדק ב-`tools/test/cs-tests.sh`, סעיף 10 (מדמה את `%LOCALAPPDATA%` ב-Linux עם `XDG_DATA_HOME`).
ב-VS Code אין צורך בזה: התוסף מוצא את `dsviz-cs` לבד (`~/.dsviz/cs-path.txt` שההתקנה כותבת, או `%LOCALAPPDATA%\DSViz\cs`) ומריץ אותו על כל פרויקט.

## סדר החיפוש של Java והספרייה (ViewerLauncher)
`DSVIZ_JAVA` / `DSVIZ_JAR` (משתני סביבה) → `..\jdk` ו-`..\dsviz.jar` ליד `dsviz-cs.exe` (כך מותקן) → `~/.dsviz/jdk-path.txt` → `JAVA_HOME` → `PATH`.

## פורמט קובץ הצעדים
טקסט UTF-8, שורה לרשומה, שדות מופרדים ב-TAB. בתוך שדה: `\\` `\t` `\n` `\r` מוסתרים, ו-`\0` = null.

| רשומה | שדות |
|---|---|
| `DSVIZ` | `1`, `cs` |
| `SRC` | שם קובץ, הטקסט כולו (קוד המקור — החלון מציג אותו) |
| `STEP` | סוג (`LINE` `CREATE` `CHANGE` `READ` `CHECK` `OUTPUT` `NOTE` `ERROR` `END` `RETURN`), אורך הפלט עד כאן, ערך החזרה (`\0` = אין), טקסט |
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
| `OUT` | כל הפלט |

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
