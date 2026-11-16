# DSViz ב-Visual Studio: מחקר עומק (התקנה, הרצה, ספריית Unit4)

> נכתב ב-2026-11-16. ההמלצות כאן לא יושמו: בקוד לא שונה דבר.
> **רמת הוודאות** של כל טענה מסומנת:
> **[אומת]** בדקתי בעצמי: בקוד של ה-repo, בתוכן חבילות NuGet של VS SDK או בניסוי בקונטיינר (Linux עם .NET SDK 8).
> **[זיכרון]** מהידע שלי, בלי אימות כאן (learn.microsoft.com חסום).
> **[CI]** לאמת בהרצה ב-GitHub Actions. סעיף 4 מפרט איך.
> הניסויים נמצאים בתיקיית ה-scratchpad (`vs-research/`): `asmver` (קורא metadata של DLL), `compile16` (קומפילציה של ההרחבה מול SDK 16), `unit4` (התנגשויות הספרייה).

---

## 0. תקציר: הממצאים העיקריים

1. **התמיכה ב-VS 2019 חסרה גם בבינארי, לא רק במניפסט.** ה-DLL הנוכחי מפנה ל-`Microsoft.VisualStudio.Interop 17.0.0.0`, ל-`Shell.15.0 17.0.0.0` ול-`Shell.Framework 17.0.0.0` **[אומת]**. האסמבלים האלה לא קיימים ב-VS 2019 **[זיכרון]**. כלומר גם אם נרחיב את טווח הגרסאות במניפסט, החבילה לא תיטען ב-2019. מצד שני, **כל קובצי ה-C# של ההרחבה מתקמפלים בלי שום שינוי מול `Microsoft.VisualStudio.SDK 16.0.206`** **[אומת]**: קומפילציה ב-Linux, בלי אריזה ל-VSIX.
2. **VS 2019 לא יבנה את הפרויקטים של DSViz.** התבנית, התרגילים מהמאגר (`CsSolution.java:25`) ו-`Exercise.csproj` כולם `net8.0`. ‏VS 2019 (MSBuild 16.11) לא יכול להשתמש ב-.NET 8 SDK **[זיכרון]**. ההרצה הוויזואלית עדיין תעבוד, כי `dsviz-cs` מקמפל בעצמו עם Roslyn 4.11.
3. **שורש הבעיה אצל המורה עם 17.9.2 נמצא בהיסטוריית git** **[אומת]**:
   - הגרסאות לפני 2026-11-13 לא כתבו `vsix-install.log` בכלל. זה מסביר למה לא היה יומן.
   - הן גם קראו את פלט vswhere עם הבאג `@(... | ConvertFrom-Json)` של PowerShell 5.1. כשבמחשב יש יותר ממופע אחד (גם SSMS או Build Tools נחשבים), ההרחבה לא הותקנה באף מופע (תוקן ב-`361e563`).
   - לפני `c189800` ההרחבה נבנתה מול SDK 17.12.
4. **DSViz.targets שובר פרויקטים שאינם של DSViz** **[אומת בניסוי]**. פרויקט קונסול רגיל עם `ImplicitUsings`, שמשתמש ב-`Stack<int>` של .NET ובפעולה `Count`, נכשל בבנייה (`CS1503`): הקובץ מוסיף לו `global using Unit4.CollectionsLib` ומוריד את `System.Collections.Generic`.
5. **CS0433 עדיין אפשרי.** ספריית בית ספר שמחוברת ב-`ProjectReference` לפרויקט ששם הקובץ שלו אינו `Unit4` (למשל `CollectionsLib.csproj` עם `AssemblyName=Unit4`) גורמת ל-CS0433 **[אומת בניסוי]**. אב-טיפוס שמחליט **אחרי** ResolveAssemblyReferences פותר את שלושת המקרים **[אומת בניסוי]** (סעיף 3.5).
6. **"הרצה ויזואלית" היא ברירת המחדל של הכפתור הירוק.** בתבנית ובפרויקטים מהמאגר הפרופיל הזה ראשון ב-launchSettings (`project/Exercise/Properties/launchSettings.json:3`, `CsSolution.java:39-51`). זה סותר את הדרישה ש-F5 ירוץ כרגיל. בנוסף, ההרחבה **חוטפת** את F5 ו-Ctrl+F5 כשהקובץ הפעיל לא שייך לאף פרויקט (`StartupFollower.cs:76`), ומחליפה את ה-Startup Project בכל מעבר בין חלונות (`StartupFollower.cs:45,176`).
7. **הבדיקות ב-CI לא מתנהגות כמו תלמיד:**
   - `ClickEl` מנסה קודם UIA Invoke (`toolbar-e2e.ps1:87`).
   - ההרצה מופעלת דרך `DTE.ExecuteCommand('Debug.StartWithoutDebugging')` (`toolbar-e2e.ps1:792,839`).
   - `vsix-test.ps1:71` מריץ `devenv /updateconfiguration`, צעד שאצל משתמש אמיתי לא קורה.
   סעיף 4 מציע מסגרת של לחיצות עכבר אמיתיות.

**ההמלצה בקצרה:**
- שני VSIX מאותו קוד ועם אותו Id: אחד ל-16.x (x86) ואחד ל-17 ומעלה (amd64 ו-arm64). מספר גרסה עולה בכל בנייה.
- לא לחטוף את הכפתור הירוק בכלל. במקומו: InfoBar, ו"הרצה רגילה" ראשונה בפרופילים.
- DSViz.targets מחליט אחרי שה-references נפתרו, ומוסיף `global using` רק בפרויקטים מסומנים של DSViz.
- ההרצה הוויזואלית ממשיכה לקמפל רק מול הספרייה של DSViz.
- מטריצת CI אחת: ‏2019 (מותקן בזמן הריצה, לצד 2022), ‏2022, ‏2026, עם לחיצות עכבר אמיתיות וצילום מסך אחרי כל צעד.

---

## 1. איך זה עובד

### 1.1 גרסאות ומופעים

| מוצר | גרסה פנימית | תהליך devenv | ARM64 | הערה |
|---|---|---|---|---|
| VS 2019 | 16.0 עד 16.11 (רק 16.11 נתמכת, עד 2029) | **x86, 32 ביט** | אין (רץ באמולציה) | MSBuild 16.11; ‏C# עד 9; ‏.NET SDK עד 5 בפועל |
| VS 2022 | 17.0 עד 17.14 | amd64 | native מ-17.4 | ‏.NET 8 SDK דורש 17.8 ומעלה (‏8.0.2xx דורש 17.9) **[זיכרון]** |
| VS 2026 | 18.x | amd64 / arm64 | כן | מוכרז כתואם להרחבות של 2022 **[זיכרון]**; ב-repo זה עובד בפועל (`dsviz-vs2026-real.yml`) |

- **מופעים:** כל התקנה היא "instance" עם `instanceId` משלה. במחשב אחד יכולים להיות 2019, ‏2022 ו-2026 זה לצד זה, וגם שני מופעים של 2022 (למשל Community ו-Preview).
- **vswhere** (`%ProgramFiles(x86)%\Microsoft Visual Studio\Installer\vswhere.exe`) מחזיר את כל המופעים. עם `-products *` הוא מחזיר גם Build Tools, ‏SSMS 21 (שבנוי על המעטפת של 2022), TestAgent ועוד. לכן צריך לסנן לפי `productId`, כמו `Fits` ב-`install-vsix.ps1:33-37` **[אומת]**.
- **SKU:** ‏`InstallationTarget Id="Microsoft.VisualStudio.Community"` מכסה גם את Professional ואת Enterprise **[זיכרון]**.

### 1.2 VSIX: מניפסט, רפרנסים, התקנה ומטמונים

- **מניפסט (v3):**
  - `InstallationTarget Version="[a,b)"` ו-`ProductArchitecture`. הערכים שה-schema מאפשר הם `x86|amd64|arm64` **[אומת: ב-schema בחבילה Microsoft.VSSDK.BuildTools 17.14]**.
  - ב-2019 לא מציינים ארכיטקטורה (או מציינים x86). ב-2022 ומעלה חובה לציין amd64 / arm64 **[זיכרון]**.
  - `Prerequisite CoreEditor` חייב לכלול את הגרסה התחתונה. היום הוא `[17.0,)` (`source.extension.vsixmanifest:21`).
- **מה קובע אם ההרחבה תיטען בפועל: גרסאות האסמבלים שה-DLL מפנה אליהם** **[אומת]**:

| חבילה | אסמבלי | גרסה |
|---|---|---|
| SDK 16.x | Shell.15.0 | 16.0.0.0 |
| SDK 17.x | Shell.15.0 | 17.0.0.0, ומפנה ל-`Microsoft.VisualStudio.Interop 17.0.0.0` (ה-interop המאוחד שנוסף ב-2022) |
| SDK 17.x | `Microsoft.VisualStudio.Shell.Interop.dll` | facade (17.0.0.0) עם type-forward ל-`Microsoft.VisualStudio.Interop` |
| SDK 17.x | EnvDTE | 17.0.0.0 |

  - ה-DLL שלנו כיום (`prebuilt/DsViz.VsExtension.vsix`) מפנה ל-17.0.0.0 בכל אלה **[אומת]**. ב-2019 אין אסמבלים בגרסאות האלה, ו-CLR לא מבצע redirect כלפי מטה **[זיכרון]**.
  - הקומפילציה מול 16.0.206 מפנה ל-`Shell.15.0 16.0`, `Shell.Interop 7.1.40304.0` ו-`EnvDTE 8.0` **[אומת]**. ‏EnvDTE אפשר להטמיע (`EmbedInteropTypes`). את `Shell.Interop 7.1` אי אפשר (CS1759) **[אומת]**.
  - ב-2022 מותקנים facades ו-binding redirects מ-16.x אל 17.0 **[זיכרון]**. ב-2026 זה לא ודאי. לכן VSIX אחד שנבנה מול 16 ואמור לעבוד גם ב-17 וב-18 הוא הימור, ושני VSIX הם הדרך הבטוחה (סעיף 3.1).
- **כל הממשקים שהקוד משתמש בהם קיימים ב-16.0** **[אומת]**: `AsyncPackage`, `PackageAutoLoadFlags`, `OleMenuCommandService`, `ProvideMenuResource`, `ActivityLog`, `VsShellUtilities`, `InfoBarModel`, ועוד ממשקי ה-editor (Tagger / GlyphFactory). גם מוניקרי האייקונים של ה-vsct (`NewDocument, Hierarchy, Library, Document, Send, Run, StatusHelp, User`) קיימים ב-ImageCatalog 16.0.
- **התקנה (VSIXInstaller):**
  - לכל מופע יש עותק משלו ב-`Common7\IDE\VSIXInstaller.exe`. הפרמטר `/instanceIds:` מגביל את ההתקנה למופע מסוים.
  - **למשתמש** (ברירת המחדל, בלי הרשאות מנהל): `%LOCALAPPDATA%\Microsoft\VisualStudio\<major>.0_<instanceId>\Extensions\<תיקייה אקראית>\`.
  - **לכל המשתמשים** (`AllUsers="true"`, דורש מנהל): `Common7\IDE\Extensions\...` **[אומת: schema; הנתיבים מזיכרון]**.
  - הפונקציה `Installed` בסקריפט מחפשת בשני המקומות (`install-vsix.ps1:46-57`) והנתיב מתאים גם ל-16.
- **"Pending" ומטמונים:**
  - VS טוען הרחבות **רק כשהוא עולה**.
  - VSIXInstaller לא מתקין כש-devenv של המופע פתוח. גם "Manage Extensions" בתוך VS רק מתזמן את ההתקנה ל"אחרי שכל החלונות נסגרים" **[זיכרון]**.
  - אחרי התקנה מתעדכן הקובץ `Extensions\extensions.configurationchanged`. בעלייה הבאה VS בונה מחדש את מטמון ה-pkgdef ‏(privateregistry.bin), את מטמון ה-MEF ‏(ComponentModelCache) ואת מטמון התפריטים (מיזוג ctmenu) **[זיכרון]**.
  - `devenv /updateconfiguration` עושה את אותו הדבר מראש. ה-CI מריץ אותו (`vsix-test.ps1:71`), משתמש אמיתי לא.
- **מושבתת (disabled):**
  - המצב נשמר ב-privateregistry.bin (ExtensionManager). הקבצים נשארים במקומם, ולכן `Installed` ו-`Current` בסקריפט יחזירו "מותקן ועדכני" גם על הרחבה מושבתת.
  - מבחוץ אפשר לקרוא את המצב רק עם `vsregedit.exe` של המופע, או בעקיפין בעזרת "דופק" (heartbeat) שההרחבה כותבת (סעיף 3.2) **[זיכרון]**.
- **אותה גרסה לא מתעדכנת:** VSIXInstaller מסרב להתקין מחדש Id עם אותו מספר גרסה ("already installed") **[זיכרון]**. לכן `install-vsix.ps1:127-129` מסיר ואז מתקין. אם ההסרה הצליחה וההתקנה נכשלה, לא נשאר סרגל בכלל. המניפסט תקוע על `1.0.1` (`source.extension.vsixmanifest:4`).
- **`ProvideMenuResource("Menus.ctmenu", 1)`** (`DsVizPackage.cs:21`): הספרה מסמנת את גרסת משאב התפריטים. מקובל להעלות אותה בכל שינוי של ה-vsct, כדי לאלץ מיזוג מחדש **[זיכרון]**. כיום היא קבועה, למרות שכפתורים נוספו.

### 1.3 סרגל הכלים: מיקום ושורה משלו

- ב-vsct יש `type="Toolbar"` עם `DefaultDocked` (`DsVizPackage.vsct:11-12`). ‏DefaultDocked אומר רק "מעוגן למעלה כשהוא מוצג". הוא **לא** מציג את הסרגל, ואין ב-vsct שום דגל ל"שורה חדשה" או "לכל הרוחב" **[זיכרון]**.
- VS שומר את סידור הסרגלים (גלוי, band כלומר שורה, מיקום) בהגדרות המשתמש, ושומר אותם **רק בסגירה רגילה**. בעלייה הוא משחזר את הסידור, ולעיתים **אחרי** שהחבילה כבר נטענה. בגלל זה יש ניסיונות חוזרים אחרי 3, ‏10, ‏30 ו-60 שניות (`DsVizPackage.cs:80-89`) **[אומת בקוד; ההתנהגות מזיכרון ומהערות הקוד]**.
- הדרך היחידה שעובדת היא DTE `CommandBars["DSViz"]`: לקבוע `Visible`, `Position=msoBarTop`, `RowIndex=max(שורה אחרונה+1, 3)`, `Left=0` (`DsVizPackage.cs:141-209`). ‏"לכל הרוחב" לא אפשרי ב-VS. מה שאפשר להשיג הוא שורה משלו, ואז אף כפתור לא נדחס מאחורי החץ "עוד".
- **חלופה מוצהרת** **[זיכרון, לבדוק ב-CI]**: `VisibilityConstraints` ב-vsct עם `VisibilityItem` לסרגל ב-`UICONTEXT_NoSolution` וגם ב-`UICONTEXT_SolutionExists`. VS מציג אוטומטית סרגל שההקשר שלו פעיל, אלא אם המשתמש הסתיר אותו. זה לא מחליף את קביעת השורה.

### 1.4 צינור ההרצה (F5, Ctrl+F5, הכפתור הירוק)

1. **פקודות:** F5 היא `Debug.Start` (`{5EFC7975-14BC-11CF-9B2B-00AA00573819}`:295) ו-Ctrl+F5 היא `Debug.StartWithoutDebugging` (‏368). הכפתור הירוק (וב-2022 גם הכפתור הירוק החלול) מפעיל את אותן פקודות. הפקודות עוברות דרך ה-command dispatcher של VS, ולכן `DTE.Events.CommandEvents.BeforeExecute` מקבל אותן ויכול לבטל אותן עם `CancelDefault` **[אומת בקוד ובבדיקת CI קיימת; המנגנון מזיכרון]**. יש מסלולים אחרים שלא עוברים בפקודות האלה: Debug ← Start New Instance מתפריט הפרויקט, Test Explorer, ‏Hot Reload restart.
2. **מה רץ:** ה-Startup Project או הפרויקטים של הפתרון (אחד או כמה). הבחירה נשמרת ב-`.vs\<sln>\v1x\.suo`, לא ב-.sln. יש גם אפשרות מובנית: Solution Properties ← Startup Project ← "Current selection" (עוקב אחרי הבחירה ב-Solution Explorer) **[זיכרון]**.
3. **Build לפני Run:** VS בונה קודם את הפרויקט ואת התלויות שלו. אם הבנייה נכשלת מופיע הדיאלוג "There were build errors. Continue?". זה נכון **גם לפרופיל "הרצה ויזואלית"**, כי הפרופיל הוא commandName=Executable שרץ אחרי ה-build. לכן הפרופיל הוויזואלי תלוי בכך שהבנייה הרגילה מצליחה. הכפתור בסרגל שלנו לא תלוי בזה **[זיכרון]**.
4. **פרויקט SDK-style (‏CPS, ‏dotnet/project-system):**
   - `Properties\launchSettings.json` מגדיר פרופילים. הרשימה מוצגת בתפריט שליד הכפתור הירוק.
   - **ללא `ActiveDebugProfile` ב-`.csproj.user` נבחר הפרופיל הראשון.**
   - `commandName: Project` מריץ את הפלט של הפרויקט (ב-2019 ו-2022 דרך `VsDebugConsole`). ‏`Executable` מריץ את `executablePath` עם `commandLineArgs`. משתני סביבה כמו `%LOCALAPPDATA%` מורחבים **[זיכרון]**.
   - זה עובד גם ל-SDK-style שמכוון ל-`net48`.
5. **csproj קלאסי (.NET Framework, ‏project system ישן):** אין launchSettings. ההגדרות נמצאות ב-Properties ← Debug: ‏`StartAction=Project|Program|URL`, ‏`StartProgram`, ‏`StartArguments`, ונשמרות ב-csproj או ב-.user לפי configuration. **אין פרופילים בתפריט**, ולכן "הרצה ויזואלית" בפרויקט קלאסי אפשרית **רק** דרך כפתור הסרגל **[זיכרון]**.
6. **קובץ בלי פרויקט (Miscellaneous Files):**
   - VS לא יודע להריץ קובץ בודד.
   - כשפתרון פתוח, F5 מריץ את ה-Startup Project. זה בדיוק מה שקרה לתלמידה: היא קיבלה את תוכנית המחסנית של התבנית.
   - כשאין פתרון, הכפתור הופך ל-"Attach..." **[זיכרון]**.
   - ‏.NET 10 הוסיף `dotnet run file.cs` (file-based apps). לא ידוע לי אם VS 2026 מריץ כך מתוך F5 **[לא ודאי]**.
7. **Top-level statements:** המהדר יוצר `Program.<Main>$`. ב-VS זה עובד מ-C# 9 (VS 16.8 ומעלה, גם ב-net48 עם `LangVersion 9`). `dsviz-cs` עוטף את הקוד ב-Main בעצמו (`Program.cs:685`).
8. **ההרצה הוויזואלית (`dsviz-cs`):**
   - שומרת את כל הקבצים.
   - מקבלת קובץ או פרויקט. `ProjectInfo.Load` מחפש csproj עד 4 תיקיות מעלה (`Program.cs:642-653`).
   - מקמפלת **את כל קובצי ה-cs שבתיקייה, רקורסיבית** (`Program.cs:628`) עם Roslyn, מול ה-TPA של .NET 8 ומול `Unit4.CollectionsLib` של DSViz בלבד (`Program.cs:484-495`). ה-references של הפרויקט מתעלמים לגמרי.
   - בוחרת את ה-Main של הקובץ שביקשו.

### 1.5 איך נפתרת הספרייה

- **DSViz.targets** מותקן ל-`%LOCALAPPDATA%\Microsoft\MSBuild\Current\Microsoft.CSharp.targets\ImportAfter`. ‏Microsoft.CSharp.CurrentVersion.targets מייבא את התיקייה הזו בכל בנייה של C#: ב-VS (כולל design-time build, שמזין את IntelliSense) וב-`dotnet build`. ‏`Current` נכון מ-MSBuild 16 ומעלה, כלומר 2019, ‏2022, ‏2026 ו-SDK **[זיכרון; שעובד ב-2022 אומת ב-CI הקיים]**.
- **ResolveAssemblyReferences (RAR)** הופך את `Reference` ואת הפלט של `ProjectReference` לרשימה `ReferencePath`, וזה מה ש-csc מקבל.
  - **CS0433:** אותו שם מלא של טיפוס בשני אסמבלים, למשל `Unit4` של בית הספר ו-`Unit4.CollectionsLib` שלנו. אין דרך לתקן את זה מהקוד בלי extern alias.
  - **CS0104:** ‏`Stack` דו-משמעי בין namespaces (‏`System.Collections.Generic` מול `Unit4.CollectionsLib`).
- **היום:**
  - DSViz.targets מוסיף את הספרייה **לפני** RAR לכל פרויקט Exe, אלא אם יש reference ששמו או ה-HintPath שלו מתחיל ב-Unit4 (`DSViz.targets:58-81`).
  - בפרויקט .NET 6 ומעלה שלא נראה שהוא משתמש באוספי .NET, הוא מוסיף `global using Unit4.CollectionsLib` ומוריד את SCG (`DSViz.targets:122-141`).
  - הוא כותב את הפרופיל לכל פרויקט SDK מסוג Exe ב-.NET 6 ומעלה (`DSViz.targets:90-108`).
- **ההרצה הוויזואלית** לא נוגעת ב-MSBuild בכלל, ולכן תמיד משתמשת בספרייה של DSViz. ההפרדה כבר קיימת שם. הבעיה היא רק בבנייה הרגילה.

---

## 2. פערים וסיכונים במימוש הנוכחי (עם ראיות)

| # | פער / סיכון | ראיה | חומרה |
|---|---|---|---|
| G1 | אין תמיכה ב-2019: גם המניפסט וגם הבינארי מפנים ל-17.0. ‏`Fits` מסנן major ≥ 17 | `source.extension.vsixmanifest:9-15,21`; `DsViz.VsExtension.csproj:26`; `install-vsix.ps1:36`; רפרנסי ה-DLL **[אומת]** | גבוהה (דרישה) |
| G2 | כל הפרויקטים הם net8.0 ונשענים על `global using` ו-`<Using>`. ב-2019 הבנייה הרגילה נכשלת | `CsSolution.java:25`; `project/Exercise/Exercise.csproj`; `project/Directory.Build.props` (`Using Static`) | גבוהה ל-2019 |
| G3 | הכפתור הירוק מריץ ויזואלית כברירת מחדל, כי "הרצה ויזואלית" ראשונה | `project/*/Properties/launchSettings.json:3`; `CsSolution.java:39-51` (לעומת `Program.cs:817`, שם "הרצה רגילה" נוספת ראשונה) | גבוהה (דרישה) |
| G4 | חטיפת F5 ו-Ctrl+F5 לקובץ בלי פרויקט (`cancelDefault=true`). כך "הרצה רגילה" הופכת לוויזואלית, בניגוד לדרישה. זה גם מסתיר תקלות: אם dsviz-cs לא קיים, VS מריץ את ה-Startup Project | `StartupFollower.cs:49-50,76,97-132` | גבוהה |
| G5 | ה-Startup Project מוחלף בכל מעבר חלון. זה דורס בחירה מכוונת של המשתמש ונשמר ב-.suo. "פרויקט DSViz" מזוהה לפי טקסט ב-csproj, כך שגם פרויקט בית ספר עם `Reference Include="Unit4.CollectionsLib"` נחשב | `StartupFollower.cs:45,156-183,189-198` | בינונית |
| G6 | ‏DSViz.targets משנה **כל** פרויקט קונסול במחשב: מוסיף DLL, ‏`global using`, ומוריד SCG. נמצא בניסוי: ‏`Stack<int>` עם `.Count` נכשל ב-CS1503. בתבנית הזיהוי חסרים `Stack<`, `Queue<`, `.Push(`, `.Pop(` ו-`.Count` | `DSViz.targets:122-145`; ניסוי `unit4/app2` **[אומת]** | גבוהה |
| G7 | CS0433 כש-ProjectReference לספריית בית הספר בשם קובץ אחר. הבדיקה לפי `Filename` בלבד | `DSViz.targets:73`; ניסוי `unit4/app1` **[אומת]** | גבוהה |
| G8 | ‏DSViz.targets כותב `launchSettings.json` לכל פרויקט SDK מסוג Exe של המשתמש (גם עבודה של מורה). זה מייצר רעש ב-git ושינויים לא מבוקשים | `DSViz.targets:90-108` | בינונית |
| G9 | ‏`Private=true` מעתיק את DLL של DSViz ל-bin עם HintPath מחוץ לפרויקט. פרויקט שנשלח למורה בלי DSViz לא נבנה (CS0246) | `DSViz.targets:76-79` | בינונית |
| G10 | `dsviz-cs` מקמפל את כל ה-cs בתיקייה (גם קבצים שלא בפרויקט, ותיקיות-בת עם csproj משלהן) ומתעלם מ-references שאינם Unit4 ומ-ProjectReference. זה סוטה ממה שנבנה בהרצה רגילה. ‏API של .NET Framework בלבד (WinForms ב-net48) לא יתקמפל | `Program.cs:484-495,628,642-653` | בינונית |
| G11 | מספר הגרסה של ה-VSIX קבוע, וההתקנה היא הסרה ואז התקנה. אם ההתקנה נכשלת, לא נשאר סרגל | `source.extension.vsixmanifest:4`; `install-vsix.ps1:127-138` | בינונית |
| G12 | ‏ProvideMenuResource תקוע על גרסה 1 למרות שינויי vsct | `DsVizPackage.cs:21` | נמוכה ועד בינונית |
| G13 | אין זיהוי של הרחבה מושבתת או שנכשלה בטעינה. התקנה נחשבת "תקינה" לפי קבצים בלבד | `install-vsix.ps1:46-87` | בינונית |
| G14 | תלות ב-PowerShell. מדיניות ExecutionPolicy שנקבעה ב-GPO (MachinePolicy) גוברת על `-ExecutionPolicy Bypass`, ואז אין שום יומן, כי היומן נכתב מתוך הסקריפט עצמו. ל-NSIS אין גיבוי | `setup.nsi:586`; `install-vsix.ps1:17-26` **[זיכרון לגבי GPO]** | בינונית (בתי ספר) |
| G15 | התקנה למשתמש בלבד (`RequestExecutionLevel user`). אם איש IT מתקין עם "Run as different user", הכול מותקן בפרופיל שלו | `setup.nsi:51` | בינונית (בתי ספר) |
| G16 | ‏`-WhenClosed` מחכה לכל devenv במחשב, ובודק רק `devenv` ולא תהליכים שעלולים לחסום (ServiceHub, צמתי MSBuild) **[זיכרון]** | `install-vsix.ps1:99-100,126` | נמוכה |
| G17 | ה-CI לא מתנהג כמו תלמיד: UIA Invoke לפני עכבר, ‏`DTE.ExecuteCommand` להרצה, ו-`/updateconfiguration` | `toolbar-e2e.ps1:86-87,792,839`; `vs-helpers.ps1:63-64`; `vsix-test.ps1:71` | גבוהה (אמינות) |
| G18 | ‏`vs-toolbar-shown` הוא דגל גלובלי, לא לכל מופע. ‏`ShowToolbarOnce` מציג את הסרגל מחדש גם אם המשתמש הסתיר אותו בכוונה, ומזיז אותו שוב אם המשתמש סידר אותו באותה שורה עם סרגל אחר | `DsVizPackage.cs:155,186` | נמוכה |
| G19 | ‏Mac / Rider: אותו DSViz.targets (ב-Mac) מושפע מ-G6 ו-G7 | `DSViz.targets:30` (הערה) | נמוכה |

---

## 3. התכנון המומלץ

### 3.1 תמיכה ב-VS 2019 ומעלה

**הרחבה: שני VSIX מאותו קוד (מומלץ). VSIX אחד הוא ניסוי אופציונלי.**

| VSIX | חבילה | מניפסט | ארכיטקטורה |
|---|---|---|---|
| `DsViz.VsExtension.vs16.vsix` | `Microsoft.VisualStudio.SDK 16.0.206` (ExcludeAssets=runtime) | `InstallationTarget [16.0,17.0)` בלי `ProductArchitecture`; ‏`CoreEditor [16.0,)` | x86 (devenv של 2019) |
| `DsViz.VsExtension.vsix` | בדיוק כמו היום, `SDK 17.0.32112.339` | `[17.0,)` amd64 ו-`[17.4,)` arm64 | נבדק כבר על 17.14 ו-18.x |

- שתי החבילות עם **אותו Identity Id**, כך שההסרה וזיהוי "מותקן" לא משתנים.
- ב-csproj: `<VsTarget>` ‏(16|17) קובע את גרסת ה-PackageReference ואת המניפסט. אפשר להשתמש ב-`source.extension.vs16.vsixmanifest` נפרד, או לבנות את המניפסט ב-MSBuild.
- בונים פעמיים באותו job על windows-2022 (MSBuild של 2022 ו-`VSSDK.BuildTools 17.14`): ‏`msbuild -p:VsTarget=16` ו-`-p:VsTarget=17`, עם `BaseIntermediateOutputPath` נפרד לכל בנייה.
- הקוד מתקמפל מול 16.0 בלי שינוי **[אומת]**. מומלץ להטמיע את EnvDTE (`EmbedInteropTypes`) גם ב-16.
- AnyCPU מתאים ל-x86, ל-amd64 ול-arm64. ה-DLL מסומן `ILOnly` ולא 32BITREQ **[אומת]**.
- VSIX אחד (`[16.0,17.0)` x86 ו-`[17.0,)` amd64) תלוי ב-binding redirects של 2022 ו-2026 ל-`Shell.Interop 7.1` ול-`Shell.15.0 16.0`. אם רוצים לנסות: ריצת CI אחת (סעיף 4) תכריע. בכל מקרה לא נוטשים את הבנייה מול 17 שכבר עובדת.
- **ProvideMenuResource:** להעלות את הגרסה בכל שינוי vsct. **מניפסט:** `Version="1.0.<build>"`, שמגיע מ-CI ומשותף עם ה-pkgdef ‏(`InstalledProductRegistration`).

**התבנית והפרויקטים ל-2019 (חובה אם רוצים שגם הכפתור הירוק יעבוד ב-2019):**
- "תרגיל חדש", המאגר ו-`CsSolution.csproj` צריכים לבחור מסגרת לפי ה-VS שפותח אותם. ‏`DSVIZ_VS_PID` מאפשר לזהות את גרסת devenv. ב-major 16, או כשאין .NET 8 SDK, ליצור SDK-style `net48` עם `LangVersion 9` ובלי `ImplicitUsings`. במקום `Using Static` לכתוב `using static Unit4.CollectionsLib.Helpers;` בקובץ התבנית עצמו.
- הספרייה כבר `netstandard2.0` ותואמת ל-net472 ומעלה **[אומת: ‏csproj של הספרייה]**.
- `dsviz-cs` לא תלוי בגרסת VS, והוא תמיד מקמפל עם C# העדכני.

### 3.2 שינויים במתקין

1. `install-vsix.ps1`:
   - `Fits` מקבל major ≥ 16.
   - בוחר VSIX לפי major: ‏16 מקבל `vs16.vsix`, ‏17 ומעלה מקבל את הרגיל.
   - כשהגרסה שונה מתקין **מעל** הקיים (upgrade), ומסיר קודם רק כשמספר הגרסה זהה.
   - מעביר `/logFile:%LOCALAPPDATA%\DSViz\logs\vsix-<instance>.log` **[זיכרון שהמתג קיים; ב-CI מתעדים `VSIXInstaller /?` לכל גרסה]**.
2. **"דופק" מההרחבה:**
   - בכל טעינה החבילה כותבת `%LOCALAPPDATA%\DSViz\vs-loaded\<RegistryRoot-suffix>.json` עם הגרסה, השעה ו-`dte.Version`. ‏`dte.RegistryRoot` מכיל את `17.0_<instanceId>`.
   - `update.ps1` ו-`DSViz-Diagnose` משווים: יש הרחבה מותקנת, VS נפתח מאז ההתקנה, ואין דופק. המשמעות: מושבתת או נכשלה בטעינה. אז מראים הודעה ממוקדת ("Extensions ← Manage ← DSViz ← Enable") ואוספים את ActivityLog.
   - זה פותר את G13 בלי לקרוא את privateregistry.
3. **NSIS בלי תלות ב-PowerShell:**
   - לפני הקריאה ל-PowerShell, NSIS כותב שורה ל-`vsix-install.log`.
   - אם PowerShell נכשל או לא כתב שורת "התקנה:", מפעילים גיבוי ב-NSIS עצמו: ‏`vswhere -all -products * -format value -property instanceId` ו-`installationPath`, ולכל מופע מתאים `VSIXInstaller /quiet /instanceIds:<id> <vsix>` דרך nsExec.
4. **VS פתוח בזמן ההתקנה:**
   - שומרים את `-WhenClosed`, כי זו ההתנהגות של VS עצמו.
   - מוסיפים: ההרחבה (מהגרסה הבאה ואילך) בודקת בעלייה אם ב-`%LOCALAPPDATA%\DSViz\cs` יש VSIX שה-hash של ה-DLL בו שונה משלה. אם כן היא מציגה InfoBar: "יש עדכון לסרגל DSViz: סגרו את Visual Studio והוא יותקן".
   - הניסוח "סרגל הכלים מוכן" כבר קיים.
5. **שקיפות:** בסוף ההתקנה מראים שורה לכל מופע ("VS 2019 Community 16.11: הותקן / דולג: Build Tools"). את אותו המידע כותבים ל-`vsix-install.log` יחד עם `productId`.
6. **מעבר לכל המשתמשים** (`AllUsers`, ‏Common7\IDE\Extensions): לא מומלץ. זה דורש מנהל ושובר את "בלי הרשאות". מספיק לתעד את מקרה ה-"Run as different user" (G15), ושה-Diagnose יזהה אותו (ההתקנה נמצאת בפרופיל אחר).

### 3.3 סרגל שמופיע תמיד, בשורה משלו

- להשאיר את `ShowToolbarOnce` (‏Visible ו-RowIndex דרך DTE), עם שלושה תיקונים:
  - הדגל לכל מופע: `vs-toolbar-shown-<suffix>`.
  - להזיז את הסרגל רק אם הוא לא בשורה משלו **וגם** המשתמש לא הזיז אותו (שומרים את ה-band שקבענו, ואם המשתמש שינה, לא נוגעים).
  - לרשום ל-vs-trace את `RowIndex` ואת ה-rect.
- לנסות `VisibilityConstraints` (‏NoSolution ו-SolutionExists) כגיבוי להצגה הראשונה **[CI]**.
- לא לייבא `.vssettings`, כי זה דורס התאמות של המשתמש.
- בדיקה (סעיף 4): ה-rect של כל הכפתורים גלוי (`IsOffscreen=false`, רוחב גדול מ-0), וה-Y של הסרגל שונה מה-Y של Standard. אחרי סגירה רגילה ופתיחה מחדש זה נשאר כך, וגם אחרי סגירה בכוח.

### 3.4 הפרדה נקייה בין הרצה רגילה להרצה ויזואלית (בלי חטיפת הכפתור הירוק)

1. **לא מבטלים** את `Debug.Start` ו-`StartWithoutDebugging` לעולם. מוחקים את `RunLooseFile` ואת `cancelDefault` (`StartupFollower.cs:76,97-132`). אפשר להשאיר hook **צופה בלבד**: כשהקובץ הפעיל לא שייך לאף פרויקט, מציגים InfoBar לא חוסם (`IVsInfoBarUIFactory`, קיים ב-16 **[אומת: ‏InfoBarModel ב-16.0]**): "הקובץ Loops4.cs לא שייך לאף פרויקט, ולכן Visual Studio מריץ את Exercise. [הרצה ויזואלית של הקובץ] [הוספה לפרויקט]".
2. **סדר הפרופילים:** "הרצה רגילה" (Project) ראשונה ו"הרצה ויזואלית" שנייה. מתקנים את `CsSolution.LAUNCH`, את קובצי launchSettings של התבנית ואת `LaunchProfile.Ensure`, כולל מיגרציה שמעבירה את "רגילה" לראש פרופילים קיימים של DSViz. כך F5 תמיד רגיל, וההרצה הוויזואלית נבחרת בתפריט או בכפתור הסרגל.
3. **מעקב Startup Project:** רק בפתרון התרגילים של DSViz (התנאי של `ExercisesRoot`), ורק כשהבחירה הנוכחית לא נעשתה ידנית מאז הפתיחה. בכל פתרון אחר לא נוגעים (VS כרגיל). אפשר גם להציע ב-InfoBar חד-פעמי את "Current selection".
4. **כפתור "הרצה ויזואלית" בסרגל** (לכל סוג פרויקט, כולל קלאסי וקובץ בודד). היעד נקבע כך:
   1. הקובץ הפעיל: אם יש לו פרויקט, הפרויקט עם `StartFile` (‏Main של הקובץ). אם אין, הקובץ לבד.
   2. אחרת, ה-Startup Project.
   3. אחרת, הודעה.
   
   זה כבר המצב (`DsVizPackage.cs:573-596`). מה שצריך להוסיף הוא ש-`dsviz-cs` יקבל רשימת קבצים מהפרויקט כפי ש-VS רואה אותו (DTE ‏`ProjectItems`, או `--files` / `--csproj`), במקום סריקה של התיקייה (G10).
5. **פרויקט קלאסי:** בלי פרופיל, רק הכפתור בסרגל. לא להוסיף Configuration "Visual", כי זה משנה את ה-sln.

### 3.5 Unit4: שתי הספריות יכולות לחיות יחד בלי לשנות את קוד התלמיד

**הכלל:** ההרצה הרגילה בונה עם מה שהפרויקט מפנה אליו. ההרצה הוויזואלית מקמפלת רק מול הספרייה של DSViz. זה אפשרי, ורוב העבודה כבר קיימת.

| אפשרות | הערכה |
|---|---|
| ‏**dsviz-cs מקמפל עם ה-references שלו בלבד** (המצב היום) | ✔ ההפרדה בהרצה הוויזואלית מושלמת. להרחיב: לצרף references של הפרויקט **שאינם** מספקים את `Unit4.CollectionsLib` (HintPath ל-DLL אחרים, הפלט של ProjectReference אחרים), ולוודא ש-DSViz הוא superset של ה-API של ספריית בית הספר (`Node`: ‏GetValue, SetValue, GetNext, SetNext, HasNext; ‏`BinNode`: ‏GetLeft, GetRight, HasLeft וכו'; ‏`Stack`: ‏Push, Pop, Top, IsEmpty; ‏`Queue`: ‏Insert, Remove, Head, IsEmpty). ה-API של ספריית בית הספר לא אומת כאן |
| ‏**DSViz.targets מחליט אחרי RAR** (מומלץ) | ✔ **אב-טיפוס אומת** (`scratchpad/vs-research/unit4/proto.targets`), תוצאות למטה |
| תנאי MSBuild על מאפיין שרק ההרצה הוויזואלית קובעת (`-p:DSVizVisual=true`) | רלוונטי רק אם ההרצה הוויזואלית תעבור ל-MSBuild (למשל כדי לתמוך ב-NuGet או ב-ProjectReference מלאים). אז ב-DSVizVisual מוציאים מ-`ReferencePath` כל Unit4* ומוסיפים את שלנו. נקי, אבל מוסיף תלות ב-MSBuild ובגרסת ה-SDK. לשמור לעתיד |
| extern alias | ✘ מחייב שינוי בקוד התלמיד |
| shim או type-forwarding (‏DSViz כ-`Unit4.dll`) | ✘ שני אסמבלים באותו שם גורמים ל-RAR לבחור אחד (MSB3277). אי אפשר לנבא איזה, וההרצה הרגילה עלולה לקבל את שלנו |
| תיקיות פלט נפרדות | ✔ קיים כבר (dsviz-cs מקמפל בזיכרון). אסור לכתוב ל-bin/obj של התלמיד |

**תוצאות האב-טיפוס** (‏.NET SDK 8, ‏`CustomAfterMicrosoftCSharpTargets`):

| פרויקט | DSViz.targets הנוכחי | `proto.targets` |
|---|---|---|
| ‏`app1`: ‏ProjectReference ל-`CollectionsLib.csproj` (‏AssemblyName=Unit4) | CS0433 ✘ | נבנה ✔ (זיהה `have=Unit4`) |
| ‏`app2`: קונסול רגיל, `Stack<int>` של .NET ו-`.Count` | CS1503 ✘ | נבנה ✔ (לא נוסף כלום) |
| ‏`app3`: ‏`using Unit4.CollectionsLib` בלי reference | נבנה ✔ | נבנה ✔ והורץ (DLL הועתק) |

**העיקרון של DSViz.targets v2:**
1. Target עם `AfterTargets="ResolveAssemblyReferences" BeforeTargets="FindReferenceAssembliesForReferences"`.
2. מוסיפים ל-`ReferencePath` ול-`ReferenceCopyLocalPaths` את הספרייה שלנו **רק אם** קובץ כלשהו ב-`@(Compile)` מזכיר `Unit4.CollectionsLib` **וגם** אין ב-`@(ReferencePath)` אסמבלי ששמו מתחיל ב-`Unit4`. אפשר להקשיח: inline task שבודק ב-System.Reflection.Metadata אם אסמבלי מגדיר את ה-namespace.
3. ‏`global using Unit4.CollectionsLib` והורדת SCG רק כשמסומן `<DSVizProject>true</DSVizProject>`. מקור הסימון: `Directory.Build.props` של DataStructures-CS, ה-csproj של "תרגיל חדש" והמאגר.
4. הפרופיל: רק בפרויקטים שמשתמשים בספרייה או מסומנים.
5. ‏MSBuild 16: להשתמש ב-`ToLowerInvariant().StartsWith('unit4')` (בלי overload עם StringComparison) **[CI]**.
6. לוודא ב-CI ש-IntelliSense בפרויקט **קלאסי** קורא את התוספת ל-ReferencePath (ה-project system הישן) **[CI]**.

**ניידות:** פרויקטים של DSViz יקבלו `lib\Unit4.CollectionsLib.dll` בתוך תיקיית התרגילים עם HintPath יחסי (כמו היום ב-DataStructures-CS), כדי שפרויקט שנשלח למורה ייבנה גם אצלו.

---

## 4. תוכנית מימוש ותוכנית בדיקות (רק ב-GitHub Actions, מינימום סבבים)

### 4.1 סדר עדיפויות

| שלב | מה | למה |
|---|---|---|
| P0 | ‏DSViz.targets v2 (סעיף 3.5) ובדיקות ב-`cs-tests.sh` (app1, app2, app3) | משנה בנייה רגילה בכל מחשב, ולכן הכי דחוף. אפשר לבדוק כבר ב-Linux |
| P0 | ביטול החטיפה (G4), "רגילה" ראשונה (G3), הגבלת המעקב (G5), InfoBar | דרישת המוצר |
| P1 | שני VSIX (16 ו-17), ‏`Fits≥16`, גרסה עולה, upgrade במקום הסרה, ‏`/logFile`, דופק | תמיכה ב-2019 ואמינות |
| P1 | גיבוי NSIS בלי PowerShell | בתי ספר עם GPO |
| P2 | תבנית net48 ל-2019 (‏CsSolution ו"תרגיל חדש" לפי major) | הכפתור הירוק ב-2019 |
| P2 | ‏dsviz-cs: רשימת קבצים מ-VS ו-references שאינם Unit4 | התאמה להרצה הרגילה |
| P3 | ניסוי VSIX יחיד, VisibilityConstraints, הקשחת ה-metadata-check | אופציונלי |

**כדי לצמצם סבבים:** כל P0 ו-P1 נכנסים לענף אחד, וריצה אחת של `dsviz-vs-matrix.yml` (למטה) בודקת הכול במקביל. כל בדיקה ממשיכה גם אם קודמת נכשלה (fail-soft), והתוצאה היא טבלת סיכום אחת ב-`$GITHUB_STEP_SUMMARY` ו-Artifacts.

### 4.2 מבנה ה-workflow

```yaml
name: DSViz VS matrix (real clicks)
on: { workflow_dispatch: {}, push: { paths: [ 'java-visualizer/csharp/**', 'java-visualizer/installer/**', 'java-visualizer/tools/test/windows-vs-matrix/**' ] } }
jobs:
  build:            # windows-2022: vs16.vsix + vsix (17) + bank jar + DSViz-Setup.exe (build-all) → artifact "kit"
    runs-on: windows-2022
    timeout-minutes: 60
  e2e:
    needs: build
    strategy:
      fail-fast: false
      matrix:
        include:
          - { name: vs2022,      os: windows-2022, installVs: '',   product: Enterprise }   # 17.14 קיים בתמונה
          - { name: vs2019+2022, os: windows-2022, installVs: '16', product: Community }    # גם בדיקת ריבוי מופעים
          - { name: vs2026,      os: windows-2025, installVs: '18', product: Community }    # כמו dsviz-vs2026-real.yml
    runs-on: ${{ matrix.os }}
    timeout-minutes: 300        # מגבלת job ב-GitHub-hosted: 360 דקות
    steps:
      - uses: actions/checkout@v4
      - uses: actions/download-artifact@v4   # kit
      - name: Install VS
        if: matrix.installVs != ''
        timeout-minutes: 120
        shell: powershell
        run: tools/test/windows-vs-matrix/install-vs.ps1 -Major ${{ matrix.installVs }} -Out out
      - name: E2E (real mouse)
        timeout-minutes: 160
        shell: powershell
        run: tools/test/windows-vs-matrix/e2e.ps1 -Kit kit -Product ${{ matrix.product }} -Out out
      - uses: actions/upload-artifact@v4
        if: always()
        with: { name: e2e-${{ matrix.name }}, path: out }
```

**התקנת VS 2019 (16.11) על windows-2022** (‏`install-vs.ps1`, אותו דפוס כמו `dsviz-vs2026-real.yml:74-84` **[אומת שהדפוס עובד ל-18]**):

```powershell
param([string]$Major, [string]$Out)
$ProgressPreference = 'SilentlyContinue'
$url = @{ '16' = 'https://aka.ms/vs/16/release/vs_community.exe'; '18' = 'https://aka.ms/vs/18/stable/vs_community.exe' }[$Major]
$boot = Join-Path $env:RUNNER_TEMP "vs_community_$Major.exe"
Invoke-WebRequest $url -OutFile $boot -UseBasicParsing
$ids = @('Microsoft.VisualStudio.Workload.ManagedDesktop')           # C#, .NET Framework, WPF/WinForms, .NET SDK (recommended)
if ($Major -eq '16') { $ids += 'Microsoft.Net.Component.4.8.TargetingPack', 'Microsoft.Net.Component.4.8.SDK', 'Microsoft.NetCore.Component.SDK' }
$a = @(); foreach ($i in $ids) { $a += '--add', $i }
$a += '--includeRecommended', '--quiet', '--wait', '--norestart', '--nocache'
Get-PSDrive C | Select-Object Used, Free | Format-Table | Out-String | Write-Host       # תיעוד מקום פנוי
$t0 = Get-Date; $p = Start-Process $boot -ArgumentList $a -PassThru -Wait
Write-Host ("vs_community $Major exit {0} after {1} min" -f $p.ExitCode, [int]((Get-Date) - $t0).TotalMinutes)
New-Item -ItemType Directory -Force $Out | Out-Null
Get-ChildItem $env:TEMP -Filter 'dd_*.log' | Copy-Item -Destination $Out -ErrorAction SilentlyContinue
$vw = Join-Path ${env:ProgramFiles(x86)} 'Microsoft Visual Studio\Installer\vswhere.exe'
& $vw -all -prerelease -products * -format json | Set-Content (Join-Path $Out 'vswhere.json') -Encoding UTF8
foreach ($i in (& $vw -all -prerelease -products * -format json | Out-String | ConvertFrom-Json | ForEach-Object { $_ })) {
    $x = Join-Path $i.installationPath 'Common7\IDE\VSIXInstaller.exe'
    if (Test-Path $x) { & $x /? 2>&1 | Out-File (Join-Path $Out "vsixinstaller-help-$($i.instanceId).txt") }   # אילו מתגים קיימים בכל גרסה
}
if ($p.ExitCode -notin 0, 3010) { exit 1 }       # 3010 = הצליח, נדרש reboot (לא נדרש בפועל להרחבות)
```

- **מזהי רכיבים** **[זיכרון, לאמת לפי dd_*.log ו-vswhere.json בריצה הראשונה]**: ‏`Microsoft.VisualStudio.Workload.ManagedDesktop`, ‏`Microsoft.Net.Component.4.8.TargetingPack`, ‏`Microsoft.Net.Component.4.8.SDK`, ‏`Microsoft.NetCore.Component.SDK` (ב-16.11 זה .NET 5 SDK).
- **זמן ומקום** **[הערכה]**: ‏15 עד 30 דקות, ‏6 עד 9 GB.
- **מטמון:** אי אפשר לשמור התקנת VS ב-`actions/cache` (רישום, MSI, מצב ה-installer ב-ProgramData). Layout ‏(`--layout`) של 3 עד 5 GB לא שווה, כי ההורדה מ-CDN מהירה ב-Azure.
- Chocolatey (`visualstudio2019community` ו-`visualstudio2019-workload-manageddesktop`) הוא חלופה, אבל זו עטיפה לאותו bootstrapper. עדיף ישירות.
- **VS 2026:** אם `windows-2025` כבר מכיל 18.x (בודקים ב-vswhere), מדלגים על ההתקנה.
- **17.x ישן** (כמו המורה עם 17.9): ‏Community אין LTSC. אפשרות היא Enterprise LTSC (‏`https://aka.ms/vs/17/release.ltsc.17.8/vs_enterprise.exe`) לצד 17.14 **[זיכרון, לא מאומת; שורה רביעית אופציונלית במטריצה]**.

### 4.3 עזרים: לחיצת עכבר אמיתית, צילום אחרי כל צעד, המתנה לחלונות

**סביבת ה-runner:**
- ה-runner של GitHub (Windows) רץ בסשן אינטראקטיבי, ו-UIA, צילומי מסך ועכבר כבר עובדים בבדיקות הקיימות **[אומת: ‏toolbar-e2e רץ על 2022 ו-2025]**.
- משתמשים מחדש ב-`Screen2::Set` (רזולוציה 1920x1080), ב-`Shot`, ב-`SkipFirstRun` וב-watchdog שהורג devenv תקוע (`toolbar-e2e.ps1:42-84`; `vsix-test.ps1:51`).
- ב-2019 צריך להוסיף ל-`SkipFirstRun` את הכיתובים של מסכי ההפעלה הראשונה שלו ("Not now, maybe later.", בחירת ערכת צבעים).

```powershell
Add-Type -TypeDefinition @'
using System; using System.Runtime.InteropServices; using System.Threading;
public static class Real {
  [StructLayout(LayoutKind.Sequential)] public struct MI { public int dx, dy; public uint data, flags, time; public IntPtr extra; }
  [StructLayout(LayoutKind.Sequential)] public struct KI { public ushort vk, scan; public uint flags, time; public IntPtr extra; uint p1, p2; }
  [StructLayout(LayoutKind.Sequential)] public struct IN { public uint type; public MI mi; }
  [StructLayout(LayoutKind.Sequential)] public struct INK { public uint type; public KI ki; }
  [DllImport("user32.dll")] static extern uint SendInput(uint n, IN[] i, int cb);
  [DllImport("user32.dll", EntryPoint = "SendInput")] static extern uint SendKeys(uint n, INK[] i, int cb);
  [DllImport("user32.dll")] static extern int GetSystemMetrics(int i);
  [DllImport("user32.dll")] public static extern bool SetProcessDPIAware();
  [DllImport("user32.dll")] public static extern bool SetForegroundWindow(IntPtr h);
  [DllImport("user32.dll")] public static extern bool GetCursorPos(out System.Drawing.Point p);
  public static void Click(int x, int y) {               // MOVE מוחלט על כל שולחן העבודה הווירטואלי, ואז LEFTDOWN ו-LEFTUP
    int vx = GetSystemMetrics(76), vy = GetSystemMetrics(77), vw = GetSystemMetrics(78), vh = GetSystemMetrics(79);
    var mv = new IN { type = 0, mi = new MI { dx = (int)((x - vx) * 65535.0 / (vw - 1)), dy = (int)((y - vy) * 65535.0 / (vh - 1)), flags = 0x8001 | 0x4000 } };
    SendInput(1, new[] { mv }, Marshal.SizeOf(typeof(IN))); Thread.Sleep(300);
    SendInput(2, new[] { new IN { mi = new MI { flags = 2 } }, new IN { mi = new MI { flags = 4 } } }, Marshal.SizeOf(typeof(IN)));
  }
  public static void Chord(params ushort[] vks) {        // למשל Chord(0x11, 0x74) = Ctrl+F5 (מקלדת אמיתית)
    var l = new System.Collections.Generic.List<INK>();
    foreach (var k in vks) l.Add(new INK { type = 1, ki = new KI { vk = k } });
    for (int i = vks.Length - 1; i >= 0; i--) l.Add(new INK { type = 1, ki = new KI { vk = vks[i], flags = 2 } });
    SendKeys((uint)l.Count, l.ToArray(), Marshal.SizeOf(typeof(INK)));
  }
}
'@ -ReferencedAssemblies System.Drawing
[Real]::SetProcessDPIAware() | Out-Null      # ‏BoundingRectangle של UIA בפיקסלים פיזיים

$script:step = 0
function Step([string]$name, [int]$x = -1, [int]$y = -1) {      # צילום מסך מלא אחרי כל צעד, עם צלב אדום בנקודת הלחיצה
    $script:step++
    $b = [Windows.Forms.SystemInformation]::VirtualScreen
    $bmp = New-Object Drawing.Bitmap $b.Width, $b.Height; $g = [Drawing.Graphics]::FromImage($bmp)
    $g.CopyFromScreen($b.Left, $b.Top, 0, 0, $bmp.Size)
    if ($x -ge 0) { $pen = New-Object Drawing.Pen ([Drawing.Color]::Red), 3; $g.DrawEllipse($pen, $x - $b.Left - 14, $y - $b.Top - 14, 28, 28); $g.DrawLine($pen, $x - $b.Left - 20, $y - $b.Top, $x - $b.Left + 20, $y - $b.Top); $g.DrawLine($pen, $x - $b.Left, $y - $b.Top - 20, $x - $b.Left, $y - $b.Top + 20) }
    $f = Join-Path $Out ('{0:D3}-{1}.png' -f $script:step, ($name -replace '[^\w\-]', '_'))
    $bmp.Save($f, [Drawing.Imaging.ImageFormat]::Png); $g.Dispose(); $bmp.Dispose()
    Info "step $script:step $name -> $(Split-Path -Leaf $f)"
}
function Front($proc) {   # עוקף את foreground-lock: Alt אמיתי ואז SetForegroundWindow
    [Real]::Chord(0x12); [Real]::SetForegroundWindow($proc.MainWindowHandle) | Out-Null; Start-Sleep -Milliseconds 400
}
function RealClick($el, [string]$name, $proc) {   # לא Invoke ולא DTE: מזיזים את העכבר ולוחצים בנקודה שעל המסך
    $c = $el.Current
    if ($c.IsOffscreen -or $c.BoundingRectangle.IsEmpty) { Fail "click-$name" "הרכיב לא גלוי: $(DescEl $el)"; Step "$name-not-visible"; return $false }
    if ($proc) { Front $proc }
    $r = $el.Current.BoundingRectangle; $x = [int]($r.X + $r.Width / 2); $y = [int]($r.Y + $r.Height / 2)
    $hit = [Windows.Automation.AutomationElement]::FromPoint((New-Object Windows.Point $x, $y))   # מה באמת נמצא שם (חלון קופץ? tooltip?)
    $ok = $false; for ($h = $hit; $h -ne $null; $h = [Windows.Automation.TreeWalker]::ControlViewWalker.GetParent($h)) { if ([Windows.Automation.Automation]::Compare($h, $el)) { $ok = $true; break } }
    if (-not $ok) { Info "אזהרה: בנקודה ($x,$y) נמצא '$(ElName $hit)' ולא '$(ElName $el)'" }
    [Real]::Click($x, $y); Start-Sleep -Milliseconds 700
    Step "$name-clicked" $x $y
    return $true
}
function WaitFor([scriptblock]$cond, [int]$sec, [string]$name) {   # מחכה לתוצאה, מצלם בהצלחה ובכישלון
    $t0 = Get-Date
    while (((Get-Date) - $t0).TotalSeconds -lt $sec) { $v = & $cond; if ($v) { Step "$name-ok"; return $v }; Start-Sleep -Milliseconds 500 }
    Step "$name-timeout"; return $null
}
```

**איך מזהים מה נפתח:**
- **הרצה רגילה (קונסול):** תהליך חדש בשם ה-AssemblyName של התוכנית (או `VsDebugConsole.exe` ב-2019 16.3 ומעלה וב-2022 ומעלה), עם חלון שהכותרת שלו מכילה "Debug Console" או את נתיב ה-exe.
  - **ההוכחה החזקה:** כל תוכנית בדיקה כותבת `%TEMP%\dsviz-ci\run-<id>.json` עם `{"prog":"<id>","lib":"<typeof(Node<int>).Assembly.GetName().Name>"}`.
  - בהרצה רגילה של פרויקט בית ספר מצפים ל-`lib=Unit4`. בהרצה ויזואלית של אותו פרויקט מצפים ל-`lib=Unit4.CollectionsLib`. זו הוכחה ישירה לכך שההפרדה עובדת.
  - באותה דרך מוכיחים *איזו* תוכנית רצה (לא תוכנית המחסנית של התבנית).
- **הרצה ויזואלית:** תהליך `dsviz-cs.exe` (שורת הפקודה דרך `Win32_Process`, כמו `Launched` ב-`toolbar-e2e.ps1`), ואחריו חלון java או javaw של התצוגה (כותרת כמו בבדיקות הקיימות). בנוסף קובץ ה-marker.
- **הודעות:**
  - חלון UIA מסוג Window עם `ProcessId=devenv` ו-`Name='DSViz'` (הכותרת של `Say`). קוראים את ה-Text שבו, מצלמים, ולוחצים OK בלחיצה אמיתית.
  - הדיאלוג "There were build errors" מזוהים לפי הטקסט, ונסגרים בלחיצה על "No".
  - ה-InfoBar מזוהה ב-UIA לפי הטקסט שלו.
- **חלונות Swing (המאגר)** לא חשופים ל-UIA (`toolbar-e2e.ps1:7`). משתמשים ב-ToolbarAgent הקיים (‏`JAVA_TOOL_OPTIONS`) **רק כדי לקבל מיקום**: הפקודה `target <טקסט>` כותבת `target.txt` עם rect (`toolbar-e2e.ps1:277-284`), ו-PowerShell מבצע `[Real]::Click` בנקודה הזו. כך הלחיצה עצמה אמיתית. חלופה: ‏Java Access Bridge (`jabswitch -enable`) חושף Swing דרך MSAA ל-UIA **[זיכרון]**.

### 4.4 תרחישים לכל גרסת VS במטריצה

הבדיקות רצות על 2019 (‏net48, ‏SDK-style וקלאסי), 2022 ו-2026 (‏net8 וגם net48). כל צעד מסתיים ב-`Step`.

| ID | תרחיש | פעולות (עכבר אמיתי) | מצופה |
|---|---|---|---|
| T0 | התקנה | ‏`DSViz-Setup.exe /S` (או רק install-vsix) כש-VS סגור | ב-`vsix-install.log` שורה לכל מופע. ‏SSMS ו-BuildTools: "לא מתאים". ב-2019+2022 שני המופעים מותקנים, כל אחד עם ה-VSIX הנכון (vs16 / 17) |
| T1 | הסרגל בשורה משלו | לפתוח VS בלי פתרון. אחר כך עם פתרון. אחר כך סגירה רגילה ופתיחה. אחר כך הריגה (kill) ופתיחה | כל 7 הכפתורים גלויים (rect בתוך המסך), ה-Y של DSViz שונה מה-Y של Standard ואין chevron. קובץ הדופק נכתב |
| T2 | הרצה רגילה, SDK-style | ללחוץ על הכפתור הירוק (או על הירוק החלול ב-2022 ומעלה; ב-2019: תפריט Debug ← Start Without Debugging, שתי לחיצות) | קונסול, ‏marker `prog=sdk`, **אין** dsviz-cs |
| T2k | הרצה רגילה במקלדת | Ctrl+F5 עם `Real::Chord` | כמו T2 |
| T3 | ‏net48 קלאסי עם `Unit4.dll` דמוי בית ספר (HintPath) | Build מתפריט Build (לחיצות) ואז הכפתור הירוק | חלון Output בלי CS0433. ‏marker `lib=Unit4`. ב-bin אין `Unit4.CollectionsLib.dll` |
| T3p | בית ספר דרך ProjectReference (`CollectionsLib.csproj`, ‏AssemblyName=Unit4) | כמו T3 | בלי CS0433 (‏G7) |
| T3v | הרצה ויזואלית של T3 ו-T3p | לחיצה על "הרצה ויזואלית" בסרגל | חלון התצוגה, ‏marker `lib=Unit4.CollectionsLib` |
| T4 | Top-level statements (ב-2019: ‏net48 עם `LangVersion 9`) | הכפתור הירוק, ואחר כך כפתור הסרגל | קונסול, ואז חלון התצוגה |
| T5 | קובץ בלי פרויקט בתוך פתרון שה-Startup שלו הוא Exercise | File ← Open ← File (לחיצות וחלון הדיאלוג), ואז הכפתור הירוק | רץ **Exercise** (כמו VS בלי DSViz) וה-InfoBar מוצג. לחיצה על "הרצה ויזואלית" ב-InfoBar או בסרגל מריצה את Loops4 (marker) |
| T6 | שגיאות | בכל אחד: קובץ בלי Main, ‏Main ריק, שגיאת קומפילציה, ואז כפתור הסרגל | הודעה "DSViz" עם הטקסט הצפוי (‏"אין פעולת Main", ‏"Main ריקה", קוד השגיאה). צילום, ולחיצה על OK |
| T7 | פתרון עם כמה פרויקטים | Startup=B, הקובץ הפעיל ב-A: הכפתור הירוק, ואחר כך כפתור הסרגל | הירוק מריץ את B (בלי שינוי Startup בפתרון שאינו של DSViz). הסרגל מריץ את A |
| T8 | פרויקט רגיל לא מושפע | קונסול `ImplicitUsings` עם `Stack<int>` של .NET ו-`.Count`, ואז Build | נבנה (‏G6) |
| T9 | VS פתוח בזמן ההתקנה | VS פתוח עם גרסה N, מריצים התקנה של N+1, סוגרים VS (לחיצה על X), מחכים ל-waiter ופותחים VS | בזמן שהוא פתוח: ביומן "ממתין". אחרי הסגירה: הותקן. בפתיחה: הדופק מראה N+1. ‏(ההרחבה N+1 מציגה InfoBar של "עדכון ממתין" רק מהגרסה שאחריה) |
| T10 | הרחבה מושבתת | Extensions ← Manage Extensions ← Installed ← DSViz ← Disable (לחיצות), ואז סגירה ופתיחה | אין דופק, ו-`update.ps1 -Check` מדווח "מושבתת" |
| T11 | GPO ‏ExecutionPolicy | ‏`HKLM\SOFTWARE\Policies\Microsoft\Windows\PowerShell` עם ‏`EnableScripts=1`, ‏`ExecutionPolicy=AllSigned`, ואז התקנה | גיבוי ה-NSIS התקין (שורת יומן "nsis-fallback") |
| T12 | **מאגר שאלות** | ראו למטה | ראו למטה |

**T12: מאגר השאלות (בכל גרסה במטריצה):**

- **כניסה:** כמו `toolbar-e2e.ps1:315-338`. בונים את `dsviz-bank-vs.jar` מהמקור, ומריצים `SeedSession` עם מפתח בדיקה לפני ש-VS עולה. כך `DSVIZ_PASS_KEY` עובר לכלי שהסרגל מפעיל, ומתקבלת כניסה מוכנה של תחנה או מורה. ‏VS יורש את משתני הסביבה. כגיבוי, או כדי לבדוק רק את הזרימה: `DSVIZ_ACCESS=off` (‏`AccessGate.java:54`).
- **שתי ריצות:**
  1. מסך הכניסה, בלי seed (רק מוודאים שהוא נפתח ומצלמים).
  2. עם seed: חלון המאגר עצמו.

**צעדים** (צילום אחרי כל צעד):
1. לחיצה אמיתית על "מאגר שאלות" בסרגל.
2. המתנה לחלון המאגר (javaw וכותרת).
3. בחירת נושא ושאלה, ולחיצה על "צור תרגיל". המיקום מגיע מה-agent (`target`), והלחיצה היא `Real::Click`.
4. הבדיקות:
   - `Exercises\<Name>\<Name>.csproj` וקובץ ה-cs קיימים בתיקיית התרגילים.
   - הפרויקט מופיע ב-Solution Explorer (חיפוש UIA של TreeItem בשם הפרויקט, וצילום).
   - הקובץ פתוח בעורך (כותרת הלשונית ב-UIA).
   - ‏Build דרך תפריט Build ← Build Solution (לחיצות) מסתיים ב-"Build succeeded" בחלון Output. שלד עם CS0161 מתקבל, כמו בבדיקה הקיימת.
   - לחיצה על "הרצה ויזואלית" בסרגל פותחת את חלון התצוגה.

**הצפי ב-2019:** "צור תרגיל" מייצר היום net8.0 (`CsSolution.java:25`), ולכן ה-Build יכשל עד שיתוקן P2 (‏NETSDK1045) **[זיכרון]**. ההרצה הוויזואלית צריכה לעבוד. הבדיקה רושמת את זה כ"צפוי לשנות אחרי P2", לא כהצלחה שקטה.

**T12b: ‏`lib\Unit4.CollectionsLib.dll` חסר:**
- לפני הלחיצה מוחקים את `Documents\DataStructures-CS\lib\Unit4.CollectionsLib.dll`.
- מצופה: `CsSolution.ensure` (repair ואז check, ‏`CsSolution.java:78-108`) מעתיק את הקובץ מ-`%LOCALAPPDATA%\DSViz\cs-project\lib`, השאלה נוספת, והבנייה עוברת.
- מוודאים שהקובץ חזר, ושה-hash שלו זהה לזה של `cs-project`.
- מקרה שלילי: גם `cs-project\lib` חסר. מצופה הודעה ברורה ולא קריסה.

### 4.5 מה אי אפשר לבדוק ב-CI, ואיך מכסים את זה

| לא ניתן | כיסוי |
|---|---|
| ‏ARM64 native | ‏`windows-11-arm` (‏runner ל-repo ציבורי). לא ברור אם יש עליו VS **[זיכרון]**. אחרת מסתמכים על כך שה-DLL מסומן ILOnly **[אומת]** ועל בדיקה ידנית אצל משתמש אחד |
| ‏17.x ישנים (17.9) | ‏Enterprise LTSC 17.8 לצד 17.14 (שורה אופציונלית). ‏SDK 17.0 כבר מבטיח שאין תלות בממשקים חדשים |
| ‏GPO, ‏AppLocker, אנטי-וירוס בבית ספר | ‏T11 מדמה GPO. ‏AppLocker לא. מכסים עם הגיבוי ב-NSIS וההודעות ב-Diagnose |
| התקנה בידי IT כמשתמש אחר | מתעדים. ‏Diagnose מזהה שאין `%LOCALAPPDATA%\DSViz` למשתמש הנוכחי |
| ספריית Unit4 האמיתית של בתי הספר | ב-CI ספרייה מדומה. מומלץ לקבל עותק אמיתי ולהוסיף בדיקת API-superset ל-`cs-tests.sh` |
| ממשק VS בעברית | VS לא מגיע בעברית. אין מה לבדוק |

---

## 5. נספח: מה אומת ואיך

- **ניסויים [אומת]:**
  - רפרנסים של `prebuilt/DsViz.VsExtension.vsix`: ‏17.0.0.0, ‏ILOnly. ה-pkgdef כולל `Menus ..., 1`.
  - ב-NuGet: ‏Shell.15.0 ‏16.x הוא 16.0.0.0. ‏17.x הוא 17.0.0.0 ומפנה ל-`Microsoft.VisualStudio.Interop`. ‏`Shell.Interop` 17.x הוא facade עם forward. ‏EnvDTE 17.x הוא 17.0.0.0.
  - קומפילציה של `vs-extension/*.cs` מול SDK 16.0.206 הצליחה. EnvDTE ניתן להטמעה, `Shell.Interop 7.1` לא.
  - הממשקים ומוניקרי האייקונים קיימים ב-16.0.
  - ה-schema של `ProductArchitecture` ו-`AllUsers`.
  - שלושת ניסויי ה-MSBuild (app1, app2, app3) עם ה-targets הנוכחי ועם האב-טיפוס.
  - היסטוריית git של `install-vsix.ps1` ושל המניפסט.
- **[זיכרון]:**
  - ‏binding redirects ב-2022 וב-2026.
  - התנהגות VSIXInstaller (חסימה כש-VS פתוח, already-installed, ‏`/logFile`).
  - מטמונים ו-configurationchanged.
  - DefaultDocked ו-VisibilityConstraints.
  - ‏CPS (פרופיל ראשון, הרחבת משתנים), "Current selection", ‏Attach... ללא פתרון.
  - דרישות ה-SDK לגרסאות VS.
  - מזהי הרכיבים של התקנת 2019.
  - כתובות ה-bootstrapper של 16 ושל LTSC.
  - ‏GPO מול `-ExecutionPolicy Bypass`.
- **[CI]:** כל מה שבסעיף 4.4. סבב אחד של המטריצה אמור להכריע את כל נקודות ה"זיכרון" שקריטיות לתכנון (2019, ‏redirects, ‏VisibilityConstraints, ‏/logFile).
