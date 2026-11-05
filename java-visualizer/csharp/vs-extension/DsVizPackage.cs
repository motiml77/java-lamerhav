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
