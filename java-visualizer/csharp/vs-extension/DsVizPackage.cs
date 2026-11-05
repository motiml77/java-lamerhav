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
    /// שליחת תרגיל · הצעה לפתרון · הרצה ויזואלית · הדרכה. כל כפתור מפעיל את הכלי של ההתקנה (%LOCALAPPDATA%\DSViz\cs\dsviz-cs.exe --tool ...)
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

        static string InstallDir
        {
            get { return Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "DSViz"); }
        }

        static string Runner
        {
            get { return Path.Combine(InstallDir, "cs", "dsviz-cs.exe"); }
        }

        protected override async Task InitializeAsync(CancellationToken cancellationToken, IProgress<ServiceProgressData> progress)
        {
            await JoinableTaskFactory.SwitchToMainThreadAsync(cancellationToken);
            var mcs = await GetServiceAsync(typeof(IMenuCommandService)) as OleMenuCommandService;
            if (mcs == null) return;
            Add(mcs, 0x0101, () => Tool("new"));
            Add(mcs, 0x0102, () => Tool("insert"));
            Add(mcs, 0x0103, () => Tool("bank"));
            Add(mcs, 0x0104, () => Tool("submit"));
            Add(mcs, 0x0105, () => Tool("solution"));
            Add(mcs, 0x0106, VisualRun);
            Add(mcs, 0x0107, () => Tool("help"));
            ShowToolbarOnce();
        }

        /// <summary>
        /// סרגל שנוסף אחרי שמשתמש כבר פתח את Visual Studio לא תמיד מוצג לבד (DefaultDocked חל על פרופיל חדש) —
        /// בפעם הראשונה מדליקים אותו דרך DTE. אחר כך המשתמש שולט (View ← Toolbars ← DSViz) והבחירה נשמרת.
        /// </summary>
        void ShowToolbarOnce()
        {
            try
            {
                string flag = Path.Combine(InstallDir, "vs-toolbar-shown");
                if (File.Exists(flag)) return;
                var dte = GetService(typeof(EnvDTE.DTE)) as EnvDTE.DTE;
                if (dte == null) return;
                dynamic bars = dte.CommandBars;
                dynamic bar = bars["DSViz"];
                bar.Visible = true;
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
                ActivityLog.LogInformation("DsViz", "toolbar shown");
            }
            catch (Exception ex)
            {
                ActivityLog.LogWarning("DsViz", "toolbar show failed: " + ex.Message);
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
            var parts = new System.Collections.Generic.List<string>();
            foreach (string a in args) parts.Add("\"" + a.Replace("\"", "\\\"") + "\"");
            Process.Start(new ProcessStartInfo(Runner, string.Join(" ", parts)) { UseShellExecute = false, CreateNoWindow = true });
        }

        void Tool(string mode)
        {
            if (Installed()) Start("--tool", mode, "");
        }

        /// <summary>הרצה ויזואלית של קובץ ה-C# הפתוח (ושאר קבצי הפרויקט שלו)</summary>
        void VisualRun()
        {
            ThreadHelper.ThrowIfNotOnUIThread();
            if (!Installed()) return;
            var dte = GetService(typeof(EnvDTE.DTE)) as EnvDTE.DTE;
            string file = null;
            try { file = dte != null && dte.ActiveDocument != null ? dte.ActiveDocument.FullName : null; } catch (Exception) { }
            if (file == null || !file.EndsWith(".cs", StringComparison.OrdinalIgnoreCase))
            {
                Say("פותחים קובץ C# של התרגיל (עם Main) ולוחצים שוב.");
                return;
            }
            Start(file);
        }
    }
}
