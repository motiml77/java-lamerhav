using System;
using System.IO;
using System.Linq;
using System.Text.RegularExpressions;
using EnvDTE;
using Microsoft.VisualStudio.Shell;

namespace DsViz.VsExtension
{
    /// <summary>
    /// הכפתור הירוק של Visual Studio (F5 / Ctrl+F5) נשאר של Visual Studio: ההרחבה אף פעם לא מבטלת ולא מחליפה את ההרצה הרגילה.
    /// היא רק עוזרת שלא ירוץ בשקט קוד אחר ממה שהתלמיד התכוון אליו:
    ///   1. בפתרון התרגילים של DSViz (DataStructures-CS — התיקייה שיש בה Directory.Build.props של DSViz) בלבד: כמו ב-Eclipse, מריצים את
    ///      מה שפתוח. כשעוברים לקובץ C# של תרגיל אחר (פרויקט הרצה של DSViz) — הוא נהיה ה-Startup Project, וגם ברגע שלוחצים "הרצה".
    ///      בלי זה, אחרי "מאגר שאלות" / "תרגיל חדש" היה רץ התרגיל של התבנית (סכום המחסנית). בכל פתרון אחר — לא נוגעים ב-Startup Project.
    ///   2. בכל פתרון: כשלוחצים "הרצה" ו-Visual Studio עומד להריץ פרויקט אחר מזה של הקובץ הפתוח (קובץ שלא שייך לאף פרויקט —
    ///      Miscellaneous Files, כמו Loops4.cs שנפתח מ-File ← Open; או קובץ של פרויקט הרצה אחר) — ההרצה הרגילה ממשיכה כרגיל, ובראש
    ///      החלון מופיעה שורת הודעה (InfoBar, לא חוסמת) שמסבירה מה רץ ולמה, עם כפתור "הרצה ויזואלית" של הקובץ הפתוח.
    /// את הפרופיל "הרצה ויזואלית" מוסיפים DSViz.targets ו-dsviz-cs (LaunchProfile.Ensure) — לא כאן. כל כשל שקט.
    /// כש-DSViz כבוי (Power — הלחצן האדום בסרגל) לא עושים כלום מזה: לא מחליפים Startup Project ולא מסבירים — Visual Studio רגיל.
    /// </summary>
    internal sealed class StartupFollower
    {
        const string Profile = "הרצה ויזואלית";
        static readonly string ProfileEscaped = string.Concat(Profile.Select(c => c < 128 ? c.ToString() : "\\u" + ((int)c).ToString("x4")));

        /// <summary>פקודות ההרצה של Visual Studio (VSStd97CmdID): F5 (Start) ו-Ctrl+F5 (StartNoDebug) — גם הכפתור הירוק</summary>
        const string StdCommandSet = "{5EFC7975-14BC-11CF-9B2B-00AA00573819}";
        const int CmdStart = 295, CmdStartNoDebug = 368;

        readonly DTE _dte;
        readonly WindowEvents _windows;   // שומרים הפניה — אחרת האירועים נאספים (GC) ומפסיקים להגיע
        CommandEvents _start, _startNoDebug;   // שומרים הפניה (ref) — אחרת האירועים נאספים
        readonly SolutionEvents _solution;
        readonly RunInfoBar _bar;               // שורת ההודעה בראש החלון
        readonly Action<string> _visualRun;     // הרצה ויזואלית של קובץ (DsVizPackage — שמירת הכול ו-dsviz-cs על הקובץ)

        /// <summary>פרויקט "Miscellaneous Files" (vsProjectKindMisc) ותיקיית פתרון (Solution Items) — קבצים שאף פרויקט לא בונה</summary>
        const string MiscProjectKind = "{66A2671D-8FB5-11D2-AA7E-00C04F688DDE}", SolutionItemsKind = "{66A26722-8FB5-11D2-AA7E-00C04F688DDE}";

        public StartupFollower(DTE dte, RunInfoBar bar, Action<string> visualRun)
        {
            ThreadHelper.ThrowIfNotOnUIThread();
            _dte = dte;
            _bar = bar;
            _visualRun = visualRun;
            _windows = dte.Events.WindowEvents;
            _windows.WindowActivated += OnActivated;
            // ברגע שלוחצים "הרצה" (F5 / Ctrl+F5 / הכפתור הירוק): רק מסתכלים (cancelDefault לא משתנה אף פעם) — בפתרון התרגילים בודקים שוב
            // מה הקובץ הפעיל, ובכל פתרון מסבירים אם Visual Studio עומד להריץ משהו אחר מהקובץ הפתוח
            Hook(ref _start, CmdStart);
            Hook(ref _startNoDebug, CmdStartNoDebug);
            try
            {
                _solution = dte.Events.SolutionEvents;
                _solution.Opened += OnSolutionOpened;
            }
            catch (Exception ex) { SolutionHints.Log("startup follower: solution events failed: " + ex.Message, true); }
        }

        void Hook(ref CommandEvents slot, int id)
        {
            try
            {
                ThreadHelper.ThrowIfNotOnUIThread();
                slot = _dte.Events.get_CommandEvents(StdCommandSet, id);
                slot.BeforeExecute += OnBeforeStart;
            }
            catch (Exception ex) { SolutionHints.Log("startup follower: command hook " + id + " failed: " + ex.Message, true); }
        }

        // חריגה שיוצאת מאירוע של DTE יכולה לעצור את פקודת ההרצה עצמה — לכן הכול בתוך try, והכשל רק נרשם ביומן.
        // cancelDefault נשאר כמו שהוא: ההרצה של Visual Studio תמיד ממשיכה
        void OnBeforeStart(string guid, int id, object customIn, object customOut, ref bool cancelDefault)
        {
            try
            {
                ThreadHelper.ThrowIfNotOnUIThread();
                if (!Power.On) return;   // DSViz כבוי: ההרצה של Visual Studio בלבד, בלי הודעה
                if (_dte.Debugger != null && _dte.Debugger.CurrentMode != dbgDebugMode.dbgDesignMode) return;   // F5 בזמן דיבאג = Continue
                FollowActive();
                // אין מה להסביר (רץ הפרויקט של הקובץ הפתוח) — הודעה מהרצה קודמת כבר לא נכונה
                if (!ExplainOtherRun()) _bar.Close();
            }
            catch (Exception ex) { SolutionHints.Log("startup follower (run): " + ex.Message, true); }
        }

        void OnSolutionOpened()
        {
            try
            {
                ThreadHelper.ThrowIfNotOnUIThread();
                _exercisesFor = null;
                if (!Power.On) return;   // DSViz כבוי: שום פעולה אוטומטית בפתיחת פתרון
                FollowActive();
            }
            catch (Exception ex) { SolutionHints.Log("startup follower (solution opened): " + ex.Message, true); }
        }

        // ------------------------------------------------------------------ 2. "הרצה" על קובץ ש-Visual Studio לא מריץ

        /// <summary>
        /// הקובץ הפעיל הוא קובץ C# ש-Visual Studio לא עומד להריץ (לא שייך לאף פרויקט, או שייך לפרויקט הרצה שאינו ה-Startup Project):
        /// שורת הודעה שמסבירה מה רץ, עם "הרצה ויזואלית" של הקובץ הפתוח. ההרצה הרגילה עצמה לא משתנה.
        /// בכל ספק (אין קובץ C#, קובץ זמני / לקריאה בלבד כמו "[from metadata]" אחרי F12, קובץ של ספרייה שה-Startup Project משתמש בה) — כלום.
        /// true — הוצגה (או כבר מוצגת) הודעה על ההרצה הזו
        /// </summary>
        bool ExplainOtherRun()
        {
            ThreadHelper.ThrowIfNotOnUIThread();
            Document doc = null;
            try { doc = _dte.ActiveDocument; } catch (Exception) { }
            if (doc == null) return false;
            string file = doc.FullName;
            if (string.IsNullOrEmpty(file) || !file.EndsWith(".cs", StringComparison.OrdinalIgnoreCase) || !File.Exists(file)) return false;
            bool readOnly = false;
            try { readOnly = doc.ReadOnly; } catch (Exception) { }
            string temp = Path.GetFullPath(Path.GetTempPath());
            if (readOnly || Path.GetFullPath(file).StartsWith(temp, StringComparison.OrdinalIgnoreCase)) return false;

            ProjectItem item = null;
            try { item = doc.ProjectItem; } catch (Exception) { }
            Project project = null;
            try { project = item == null ? null : item.ContainingProject; } catch (Exception) { }
            string kind = null;
            try { kind = project == null ? null : project.Kind; } catch (Exception) { }
            bool loose = project == null || string.Equals(kind, MiscProjectKind, StringComparison.OrdinalIgnoreCase)
                || string.Equals(kind, SolutionItemsKind, StringComparison.OrdinalIgnoreCase);

            // מה Visual Studio מריץ: ה-Startup Project (אחד). כמה Startup Projects — בחירה מכוונת של המשתמש, לא מסבירים
            string startup = null, startupName = null;
            try
            {
                Solution sln = _dte.Solution;
                var sp = sln == null || !sln.IsOpen ? null : sln.SolutionBuild.StartupProjects as Array;
                if (sp != null && sp.Length > 1) return false;
                if (sp != null && sp.Length == 1) startup = sp.GetValue(0) as string;
                if (!string.IsNullOrEmpty(startup)) startupName = Path.GetFileNameWithoutExtension(startup);
            }
            catch (Exception) { }

            string name = Path.GetFileName(file);
            string text;
            if (loose)
            {
                text = startupName != null
                    ? "הקובץ " + name + " לא שייך לאף פרויקט, ולכן Visual Studio מריץ עכשיו את הפרויקט " + startupName + " — לא את הקובץ הזה."
                    : "הקובץ " + name + " לא שייך לאף פרויקט, ולכן Visual Studio לא מריץ אותו.";
            }
            else
            {
                string unique = null, csproj = null;
                try { unique = project.UniqueName; csproj = project.FullName; } catch (Exception) { }
                if (string.IsNullOrEmpty(unique) || string.IsNullOrEmpty(startup)) return false;
                if (string.Equals(unique, startup, StringComparison.OrdinalIgnoreCase)) return false;   // Visual Studio מריץ את הפרויקט של הקובץ הפתוח
                // קובץ של ספרייה (לא פרויקט הרצה): ברור שרץ הפרויקט שמשתמש בה — בלי הודעה
                bool exe = false;
                try { exe = !string.IsNullOrEmpty(csproj) && File.Exists(csproj) && IsExe(File.ReadAllText(csproj)); } catch (Exception) { }
                if (!exe) return false;
                string projectName = Path.GetFileNameWithoutExtension(unique);
                text = "Visual Studio מריץ עכשיו את הפרויקט " + startupName + " (ה-Startup Project) — לא את " + projectName + ", שהקובץ " + name + " שייך אליו.";
            }
            string key = file + "|" + startup;
            SolutionHints.Log("run: normal run" + (startupName != null ? " of " + startupName : "") + " while the open file is " + name
                + (loose ? " (not in a project)" : " (another project)") + " -> info bar, the run itself is not changed");
            if (!File.Exists(DsVizTool.Runner))
            {
                _bar.Show(key, text, new string[0], new Action[0]);
                return true;
            }
            string target = file;
            _bar.Show(key, text, new[] { "הרצה ויזואלית של " + name }, new Action[] { () => _visualRun(target) });
            return true;
        }

        // ------------------------------------------------------------------ 1. פתרון התרגילים: מריצים את מה שפתוח

        string _exercisesFor;   // הפתרון שנבדק
        bool _exercises;

        /// <summary>
        /// הפתרון הפתוח הוא פתרון התרגילים של DSViz: בתיקייה שלו יש Directory.Build.props של DSViz (מגדיר את DsVizLib). רק בו ההרחבה
        /// מחליפה את ה-Startup Project. פתרון אחר (גם אם יש בו פרויקט שמפנה לספרייה) — הבחירה של המשתמש נשארת.
        /// </summary>
        bool InExercisesSolution()
        {
            ThreadHelper.ThrowIfNotOnUIThread();
            try
            {
                Solution sln = _dte.Solution;
                string full = sln == null || !sln.IsOpen ? null : sln.FullName;
                if (string.IsNullOrEmpty(full)) return false;
                if (string.Equals(full, _exercisesFor, StringComparison.OrdinalIgnoreCase)) return _exercises;
                _exercisesFor = full;
                _exercises = false;
                string props = Path.Combine(Path.GetDirectoryName(full), "Directory.Build.props");
                if (File.Exists(props))
                {
                    string text = File.ReadAllText(props);
                    _exercises = text.IndexOf("DsVizLib", StringComparison.OrdinalIgnoreCase) >= 0 || text.IndexOf("DSVizProject", StringComparison.OrdinalIgnoreCase) >= 0;
                }
                SolutionHints.Log("solution " + Path.GetFileName(full) + ": " + (_exercises ? "DSViz exercises solution (the startup project follows the open file)" : "not a DSViz exercises solution (startup project untouched)"));
                return _exercises;
            }
            catch (Exception) { return false; }
        }

        /// <summary>הקובץ שפתוח עכשיו (אם יש) קובע את ה-Startup Project — רק בפתרון התרגילים</summary>
        public void FollowActive()
        {
            ThreadHelper.ThrowIfNotOnUIThread();
            Document doc = null;
            try { doc = _dte.ActiveDocument; } catch (Exception) { }
            if (doc != null) Follow(doc);
        }

        void OnActivated(Window gotFocus, Window lostFocus)
        {
            try
            {
                ThreadHelper.ThrowIfNotOnUIThread();
                Document doc = null;
                try { doc = gotFocus == null ? null : gotFocus.Document; } catch (Exception) { }   // חלון כלי (Solution Explorer וכו') — אין מסמך
                if (doc != null) Follow(doc);
            }
            catch (Exception ex) { SolutionHints.Log("startup follower (window): " + ex.Message, true); }
        }

        /// <summary>הקובץ הזה פעיל עכשיו: בפתרון התרגילים הפרויקט שלו נהיה ה-Startup Project (אם הוא פרויקט DSViz שאפשר להריץ)</summary>
        public void Follow(Document doc)
        {
            ThreadHelper.ThrowIfNotOnUIThread();
            if (!Power.On) return;   // DSViz כבוי: ה-Startup Project נשאר כמו שהמשתמש בחר
            try
            {
                string file = doc.FullName;
                if (string.IsNullOrEmpty(file) || !file.EndsWith(".cs", StringComparison.OrdinalIgnoreCase)) return;
                if (_dte.Debugger != null && _dte.Debugger.CurrentMode != dbgDebugMode.dbgDesignMode) return;   // בזמן הרצה — לא משנים
                Solution sln = _dte.Solution;
                if (sln == null || !sln.IsOpen) return;
                if (!InExercisesSolution()) return;   // פתרון של המשתמש: ה-Startup Project שלו
                ProjectItem item = doc.ProjectItem;
                Project project = item == null ? null : item.ContainingProject;
                if (project == null) return;   // קובץ שלא בפתרון (Miscellaneous Files)
                string csproj = project.FullName;
                if (string.IsNullOrEmpty(csproj) || !csproj.EndsWith(".csproj", StringComparison.OrdinalIgnoreCase) || !File.Exists(csproj)) return;
                if (!Runnable(csproj) && !UsesLibrary(csproj)) return;
                string unique = project.UniqueName;
                var current = sln.SolutionBuild.StartupProjects as Array;
                if (current != null && current.Length > 1) return;   // המשתמש בחר כמה Startup Projects
                if (current != null && current.Length == 1 && string.Equals(current.GetValue(0) as string, unique, StringComparison.OrdinalIgnoreCase)) return;
                sln.SolutionBuild.StartupProjects = unique;
                SolutionHints.Log("startup project -> " + unique + " (" + Path.GetFileName(file) + ")");
            }
            catch (Exception ex)
            {
                SolutionHints.Log("startup follow failed: " + ex.Message, true);
            }
        }

        /// <summary>
        /// פרויקט הרצה (Exe) שמפנה בעצמו לספרייה של DSViz (Unit4.CollectionsLib, או $(DsVizLib) של תיקיית התרגילים) — פרויקט של DSViz
        /// גם כשעוד אין לו את הפרופיל "הרצה ויזואלית" (למשל הועתק בלי Properties). לא כותבים כלום לפרויקט: רק קוראים
        /// </summary>
        static bool UsesLibrary(string csproj)
        {
            try
            {
                string text = File.ReadAllText(csproj);
                if (!IsExe(text)) return false;
                return text.IndexOf("Unit4.CollectionsLib", StringComparison.OrdinalIgnoreCase) >= 0 || text.IndexOf("$(DsVizLib)", StringComparison.OrdinalIgnoreCase) >= 0;
            }
            catch (Exception) { return false; }
        }

        static bool IsExe(string csprojText)
        {
            return Regex.IsMatch(csprojText, @"<OutputType>\s*(Exe|WinExe)\s*</OutputType>", RegexOptions.IgnoreCase);
        }

        /// <summary>פרויקט הרצה (Exe) עם הפרופיל "הרצה ויזואלית" ב-Properties\launchSettings.json — פרויקט של DSViz</summary>
        static bool Runnable(string csproj)
        {
            string launch = Path.Combine(Path.GetDirectoryName(csproj), "Properties", "launchSettings.json");
            if (!File.Exists(launch)) return false;
            string json = File.ReadAllText(launch);
            if (json.IndexOf(Profile, StringComparison.Ordinal) < 0 && json.IndexOf(ProfileEscaped, StringComparison.OrdinalIgnoreCase) < 0) return false;
            return IsExe(File.ReadAllText(csproj));
        }
    }
}
