using System;
using System.ComponentModel.Design;
using System.Diagnostics;
using System.IO;
using System.Runtime.InteropServices;
using System.Threading;
using Microsoft.VisualStudio;
using Microsoft.VisualStudio.Shell;
using Microsoft.VisualStudio.Shell.Interop;
using Task = System.Threading.Tasks.Task;

namespace DsViz.VsExtension
{
    /// <summary>
    /// DSViz ב-Visual Studio: סרגל כלים בפני עצמו בראש החלון (DsVizPackage.vsct): תרגיל חדש · הוספת מבנה נתונים · מאגר שאלות ·
    /// שליחת תרגיל · הרצה ויזואלית · הדרכה. כל כפתור מפעיל את הכלי של ההתקנה (%LOCALAPPDATA%\DSViz\cs\dsviz-cs.exe --tool ...)
    /// — אותם חלונות כמו בקיצורים של ההתקנה; "הרצה ויזואלית" מריצה את קובץ ה-C# הפתוח.
    /// </summary>
    [PackageRegistration(UseManagedResourcesOnly = true, AllowsBackgroundLoading = true)]
    [InstalledProductRegistration("DSViz", "Data structures visualizer toolbar", "1.0.1")]
    [ProvideMenuResource("Menus.ctmenu", 1)]
    [ProvideAutoLoad(VSConstants.UICONTEXT.NoSolution_string, PackageAutoLoadFlags.BackgroundLoad)]
    [ProvideAutoLoad(VSConstants.UICONTEXT.SolutionExistsAndNotBuildingAndNotDebugging_string, PackageAutoLoadFlags.BackgroundLoad)]
    [Guid(PackageGuid)]
    public sealed class DsVizPackage : AsyncPackage
    {
        public const string PackageGuid = "7b2f4d1e-9c3a-4e68-b0d5-1a8c6f3e2d97";
        static readonly Guid CmdSet = new Guid("a1f6a3c2-5d9e-4b7a-8c11-3e7f2d9b6a40");

        static string InstallDir { get { return DsVizTool.InstallDir; } }

        static string Runner { get { return DsVizTool.Runner; } }

        /// <summary>מאגר השאלות הוא של המעבדה בלבד — קיים רק כשהקובץ הזה מותקן</summary>
        static string BankJar
        {
            get { return Path.Combine(InstallDir, "lab", "dsviz-bank-vs.jar"); }
        }

        protected override async Task InitializeAsync(CancellationToken cancellationToken, IProgress<ServiceProgressData> progress)
        {
            await JoinableTaskFactory.SwitchToMainThreadAsync(cancellationToken);
            var mcs = await GetServiceAsync(typeof(IMenuCommandService)) as OleMenuCommandService;
            if (mcs == null) return;
            Add(mcs, 0x0101, () => Tool("new"));
            Add(mcs, 0x0102, () => Tool("insert"));
            AddBank(mcs);
            Add(mcs, 0x0104, () => Tool("submit"));
            Add(mcs, 0x0106, VisualRun);
            Add(mcs, 0x0107, () => Tool("help"));
            ShowToolbarOnce();
            FollowActiveDocument();
        }

        StartupFollower _follower;

        /// <summary>הכפתור הירוק ("הרצה ויזואלית") מריץ את התרגיל שפתוח: הפרויקט של הקובץ הפעיל נהיה ה-Startup Project (StartupFollower)</summary>
        void FollowActiveDocument()
        {
            ThreadHelper.ThrowIfNotOnUIThread();
            try
            {
                var dte = GetService(typeof(EnvDTE.DTE)) as EnvDTE.DTE;
                if (dte == null) return;
                _follower = new StartupFollower(dte);
                EnvDTE.Document active = null;
                try { active = dte.ActiveDocument; } catch (Exception) { }
                if (active != null) _follower.Follow(active);
            }
            catch (Exception ex)
            {
                SolutionHints.Log("startup follower failed: " + ex.Message, true);
            }
        }

        /// <summary>
        /// הסרגל של DSViz מוצג בכל פתיחה של Visual Studio. Visual Studio שומר את מצב הסרגלים רק כשהוא נסגר כרגיל —
        /// אם הוא נסגר בכוח או קרס בפעם הראשונה, הסרגל שהדלקנו לא נשמר, ובלי הדלקה בכל פתיחה הוא היה נשאר מוסתר לתמיד.
        /// המיקום (שורה משלו מתחת לסרגלים הרגילים) נקבע רק בפעם הראשונה — אחר כך המשתמש יכול להזיז אותו.
        /// </summary>
        void ShowToolbarOnce()
        {
            try
            {
                var dte = GetService(typeof(EnvDTE.DTE)) as EnvDTE.DTE;
                if (dte == null) return;
                dynamic bars = dte.CommandBars;
                dynamic bar = bars["DSViz"];
                if (!(bool)bar.Visible)
                {
                    bar.Visible = true;
                    ActivityLog.LogInformation("DsViz", "toolbar shown");
                }
                string flag = Path.Combine(InstallDir, "vs-toolbar-shown");
                if (File.Exists(flag)) return;
                // שורה משלו מתחת לסרגלים הרגילים (בלי זה הוא נדחס לשורה הראשונה ורוב הכפתורים נעלמים לחץ "עוד")
                try
                {
                    object cur = bar.Position;
                    dynamic top = Enum.Parse(cur.GetType(), "msoBarTop");   // MsoBarPosition.msoBarTop (בלי הפניה לספרייה בזמן קומפילציה)
                    bar.Position = top;
                    bar.RowIndex = 3;
                    bar.Left = 0;
                }
                catch (Exception ex2)
                {
                    ActivityLog.LogWarning("DsViz", "toolbar row failed: " + ex2.Message);
                }
                Directory.CreateDirectory(InstallDir);
                File.WriteAllText(flag, DateTime.Now.ToString("s"));
            }
            catch (Exception ex)
            {
                ActivityLog.LogWarning("DsViz", "toolbar show failed: " + ex.Message);
            }
        }

        /// <summary>"מאגר שאלות": נראה רק כשהמאגר מותקן (בגרסה הציבורית אין אותו)</summary>
        void AddBank(OleMenuCommandService mcs)
        {
            var cmd = new OleMenuCommand((s, e) =>
            {
                try { Tool("bank"); }
                catch (Exception ex) { Say("DSViz: " + ex.Message); }
            }, new CommandID(CmdSet, 0x0103));
            cmd.BeforeQueryStatus += (s, e) =>
            {
                bool on = false;
                try { on = File.Exists(BankJar); } catch (Exception) { }
                var c = (OleMenuCommand)s;
                c.Visible = on;
                c.Enabled = on;
                if (on != _bankOn)
                {
                    _bankOn = on;
                    SolutionHints.Log("bank button: " + (on ? "shown" : "hidden"));
                    SyncBankControl(on);
                }
            };
            mcs.AddCommand(cmd);
            SyncBankControl(File.Exists(BankJar));
        }

        bool? _bankOn;

        /// <summary>
        /// בנוסף ל-DynamicVisibility של הפקודה: מסתיר / מציג את הכפתור גם דרך DTE (CommandBars). בסרגל כלים הסתרה דרך הפקודה לבדה
        /// לא תמיד מעלימה את הכפתור (הוא נשאר אפור) — כאן מוודאים שהוא נעלם כשהמאגר לא מותקן.
        /// </summary>
        void SyncBankControl(bool on)
        {
            try
            {
                ThreadHelper.ThrowIfNotOnUIThread();
                var dte = GetService(typeof(EnvDTE.DTE)) as EnvDTE.DTE;
                if (dte == null) return;
                dynamic bar = ((dynamic)dte.CommandBars)["DSViz"];
                foreach (dynamic ctl in bar.Controls)
                {
                    string cap = ctl.Caption;
                    if (cap != null && cap.Contains("מאגר שאלות"))
                    {
                        if (ctl.Visible != on) ctl.Visible = on;
                        SolutionHints.Log("bank control visible=" + on);
                    }
                }
            }
            catch (Exception ex)
            {
                SolutionHints.Log("bank control sync failed: " + ex.Message, true);
            }
        }

        void Add(OleMenuCommandService mcs, int id, Action action)
        {
            mcs.AddCommand(new MenuCommand((s, e) =>
            {
                try { action(); }
                catch (Exception ex) { Say("DSViz: " + ex.Message); }
            }, new CommandID(CmdSet, id)));
        }

        void Say(string text)
        {
            ThreadHelper.ThrowIfNotOnUIThread();
            VsShellUtilities.ShowMessageBox(this, text, "DSViz", OLEMSGICON.OLEMSGICON_INFO, OLEMSGBUTTON.OLEMSGBUTTON_OK, OLEMSGDEFBUTTON.OLEMSGDEFBUTTON_FIRST);
        }

        bool Installed()
        {
            if (File.Exists(Runner)) return true;
            Say("DSViz לא מותקן במחשב הזה — מריצים את DSViz-Setup.exe.");
            return false;
        }

        static void Start(params string[] args)
        {
            DsVizTool.Start(args);
        }

        void Tool(string mode)
        {
            ThreadHelper.ThrowIfNotOnUIThread();
            if (Installed()) Start("--tool", mode, ExercisesRoot());
        }

        /// <summary>
        /// התיקייה של הפתרון הפתוח — אם הוא פתרון התרגילים של DSViz (DataStructures-CS.sln, עם הספרייה ו-Directory.Build.props): התרגיל
        /// (ממאגר השאלות / שליחת תרגיל / תרגיל חדש) נוצר בפתרון שפתוח ב-Visual Studio. אחרת "" — DataStructures-CS שבמסמכים, כמו קודם.
        /// </summary>
        string ExercisesRoot()
        {
            ThreadHelper.ThrowIfNotOnUIThread();
            try
            {
                var dte = GetService(typeof(EnvDTE.DTE)) as EnvDTE.DTE;
                string sln = dte == null || dte.Solution == null ? null : dte.Solution.FullName;
                if (string.IsNullOrEmpty(sln)) return "";
                string dir = Path.GetDirectoryName(sln);
                bool ok = File.Exists(Path.Combine(dir, "DataStructures-CS.sln")) && File.Exists(Path.Combine(dir, "lib", "Unit4.CollectionsLib.dll"))
                    && File.Exists(Path.Combine(dir, "Directory.Build.props"));
                return ok ? dir : "";
            }
            catch (Exception)
            {
                return "";
            }
        }

        /// <summary>תיקיית ה-Startup Project של הפתרון הפתוח (או null)</summary>
        static string StartupProjectDir(EnvDTE.DTE dte)
        {
            try
            {
                if (dte == null || dte.Solution == null || string.IsNullOrEmpty(dte.Solution.FullName)) return null;
                var sp = dte.Solution.SolutionBuild.StartupProjects as Array;
                if (sp == null || sp.Length == 0) return null;
                string rel = sp.GetValue(0) as string;
                if (string.IsNullOrEmpty(rel)) return null;
                string full = Path.GetFullPath(Path.Combine(Path.GetDirectoryName(dte.Solution.FullName), rel));
                string dir = Path.GetDirectoryName(full);
                return dir != null && Directory.Exists(dir) ? dir : null;
            }
            catch (Exception)
            {
                return null;
            }
        }

        /// <summary>הרצה ויזואלית של קובץ ה-C# הפתוח (ושאר קבצי הפרויקט שלו)</summary>
        void VisualRun()
        {
            ThreadHelper.ThrowIfNotOnUIThread();
            if (!Installed()) return;
            var dte = GetService(typeof(EnvDTE.DTE)) as EnvDTE.DTE;
            // כמו הכפתור הירוק של Visual Studio: קודם שומרים את כל הקבצים — dsviz-cs קורא את הקוד מהדיסק, ובלי זה תלמיד שכתב
            // קוד ולא שמר היה רואה את הגרסה הקודמת (למשל "Hello, World!" של התבנית)
            try { if (dte != null) dte.Documents.SaveAll(); } catch (Exception) { }
            string file = null;
            try { file = dte != null && dte.ActiveDocument != null ? dte.ActiveDocument.FullName : null; } catch (Exception) { }
            if (file == null || !file.EndsWith(".cs", StringComparison.OrdinalIgnoreCase))
            {
                // אין קובץ C# פתוח (למשל רק "What's New"): מריצים את הפרויקט שהוא Startup Project — כמו הכפתור הירוק
                string dir = StartupProjectDir(dte);
                if (dir != null)
                {
                    Start(dir);
                    return;
                }
                Say("פותחים קובץ C# של התרגיל (עם Main), או בוחרים פרויקט להרצה (קליק ימני עליו ב-Solution Explorer " + "\u2190" + " Set as Startup Project), ולוחצים שוב.");
                return;
            }
            Start(file);
        }
    }
}
