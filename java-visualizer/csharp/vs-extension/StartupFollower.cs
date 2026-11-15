using System;
using System.IO;
using System.Linq;
using System.Text.RegularExpressions;
using EnvDTE;
using Microsoft.VisualStudio.Shell;

namespace DsViz.VsExtension
{
    /// <summary>
    /// הכפתור הירוק של Visual Studio ("הרצה ויזואלית") מריץ את ה-Startup Project — לא את הקובץ הפתוח. בפתרון התרגילים (DataStructures-CS)
    /// ה-Startup Project הוא Exercise (תרגיל סכום המחסנית), ולכן כשפתוח תרגיל אחר (למשל מהמאגר) רץ דווקא הקוד של המחסנית.
    /// כאן, כמו ב-Eclipse (מריצים את מה שפתוח): כשעוברים לקובץ C# של פרויקט DSViz אחר — פרויקט הרצה (Exe) שיש לו הפרופיל "הרצה ויזואלית" —
    /// הוא נהיה ה-Startup Project. גם פרויקט הרצה שמפנה לספרייה של DSViz (Unit4.CollectionsLib) ועוד אין לו את הפרופיל (פרויקט שהועתק
    /// בלי Properties, פרויקט חדש לפני ש-DSViz.targets הוסיף את הפרופיל) — כדי שלא ירוץ במקומו פרויקט אחר. את הפרופיל עצמו מוסיפים
    /// DSViz.targets ו-dsviz-cs (LaunchProfile.Ensure) — לא כאן. פרויקט שאינו של DSViz, ופתרון עם כמה Startup Projects — לא נוגעים. כל כשל שקט.
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

        public StartupFollower(DTE dte)
        {
            ThreadHelper.ThrowIfNotOnUIThread();
            _dte = dte;
            _windows = dte.Events.WindowEvents;
            _windows.WindowActivated += OnActivated;
            // ברגע שלוחצים "הרצה" (F5 / Ctrl+F5 / הכפתור הירוק) — לא רק כשעוברים בין חלונות: בודקים שוב מה הקובץ הפעיל ומחליפים את
            // ה-Startup Project לפרויקט שלו, לפני שההרצה מתחילה. כך לעולם לא רץ פרויקט אחר (למשל תרגיל המחסנית של התבנית) רק כי אירוע
            // המעבר בין חלונות לא הגיע, או שהפתרון נפתח אחרי שההרחבה כבר פעלה
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

        // חריגה שיוצאת מאירוע של DTE יכולה לעצור את פקודת ההרצה עצמה — לכן הכול בתוך try, והכשל רק נרשם ביומן
        void OnBeforeStart(string guid, int id, object customIn, object customOut, ref bool cancelDefault)
        {
            try
            {
                ThreadHelper.ThrowIfNotOnUIThread();
                FollowActive();
            }
            catch (Exception ex) { SolutionHints.Log("startup follower (run): " + ex.Message, true); }
        }

        void OnSolutionOpened()
        {
            try
            {
                ThreadHelper.ThrowIfNotOnUIThread();
                FollowActive();
            }
            catch (Exception ex) { SolutionHints.Log("startup follower (solution opened): " + ex.Message, true); }
        }

        /// <summary>הקובץ שפתוח עכשיו (אם יש) קובע את ה-Startup Project</summary>
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

        /// <summary>הקובץ הזה פעיל עכשיו: הפרויקט שלו נהיה ה-Startup Project (אם הוא פרויקט DSViz שאפשר להריץ)</summary>
        public void Follow(Document doc)
        {
            ThreadHelper.ThrowIfNotOnUIThread();
            try
            {
                string file = doc.FullName;
                if (string.IsNullOrEmpty(file) || !file.EndsWith(".cs", StringComparison.OrdinalIgnoreCase)) return;
                if (_dte.Debugger != null && _dte.Debugger.CurrentMode != dbgDebugMode.dbgDesignMode) return;   // בזמן הרצה — לא משנים
                Solution sln = _dte.Solution;
                if (sln == null || !sln.IsOpen) return;
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
