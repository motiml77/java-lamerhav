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
    /// הוא נהיה ה-Startup Project. פרויקט שאינו של DSViz, ופתרון עם כמה Startup Projects — לא נוגעים. כל כשל שקט.
    /// </summary>
    internal sealed class StartupFollower
    {
        const string Profile = "הרצה ויזואלית";
        static readonly string ProfileEscaped = string.Concat(Profile.Select(c => c < 128 ? c.ToString() : "\\u" + ((int)c).ToString("x4")));

        readonly DTE _dte;
        readonly WindowEvents _windows;   // שומרים הפניה — אחרת האירועים נאספים (GC) ומפסיקים להגיע

        public StartupFollower(DTE dte)
        {
            ThreadHelper.ThrowIfNotOnUIThread();
            _dte = dte;
            _windows = dte.Events.WindowEvents;
            _windows.WindowActivated += OnActivated;
        }

        void OnActivated(Window gotFocus, Window lostFocus)
        {
            ThreadHelper.ThrowIfNotOnUIThread();
            Document doc = null;
            try { doc = gotFocus == null ? null : gotFocus.Document; } catch (Exception) { }   // חלון כלי (Solution Explorer וכו') — אין מסמך
            if (doc != null) Follow(doc);
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
                if (!Runnable(csproj)) return;
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

        /// <summary>פרויקט הרצה (Exe) עם הפרופיל "הרצה ויזואלית" ב-Properties\launchSettings.json — פרויקט של DSViz</summary>
        static bool Runnable(string csproj)
        {
            string launch = Path.Combine(Path.GetDirectoryName(csproj), "Properties", "launchSettings.json");
            if (!File.Exists(launch)) return false;
            string json = File.ReadAllText(launch);
            if (json.IndexOf(Profile, StringComparison.Ordinal) < 0 && json.IndexOf(ProfileEscaped, StringComparison.OrdinalIgnoreCase) < 0) return false;
            return Regex.IsMatch(File.ReadAllText(csproj), @"<OutputType>\s*(Exe|WinExe)\s*</OutputType>", RegexOptions.IgnoreCase);
        }
    }
}
