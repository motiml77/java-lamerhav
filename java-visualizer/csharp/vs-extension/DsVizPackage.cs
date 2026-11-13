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

        /// <summary>
        /// מאגר השאלות: lab\dsviz-bank-vs.jar, ואם הוא חסר — העותק שההתקנה שמה ב-bank\ (בכל התקנה). כך הכפתור "מאגר שאלות" מופיע
        /// גם כששלב ההעתקה ל-lab לא רץ (PowerShell חסום, תיקיית המסמכים חסומה). null — המאגר לא מותקן
        /// </summary>
        static string BankJar
        {
            get
            {
                foreach (string dir in new[] { "lab", "bank" })
                {
                    string p = Path.Combine(InstallDir, dir, "dsviz-bank-vs.jar");
                    try { if (File.Exists(p)) return p; } catch (Exception) { }
                }
                return Path.Combine(InstallDir, "lab", "dsviz-bank-vs.jar");
            }
        }

        /// <summary>הנתיב של המאגר יחסית לתיקיית ההתקנה (לשורת הפקודה של java — שם משתמש בעברית לא עובר בנתיב מלא)</summary>
        static string BankJarRelative
        {
            get
            {
                string full = BankJar, root = InstallDir.TrimEnd('\\') + "\\";
                return full.StartsWith(root, StringComparison.OrdinalIgnoreCase) ? full.Substring(root.Length) : full;
            }
        }

        protected override async Task InitializeAsync(CancellationToken cancellationToken, IProgress<ServiceProgressData> progress)
        {
            await JoinableTaskFactory.SwitchToMainThreadAsync(cancellationToken);
            var mcs = await GetServiceAsync(typeof(IMenuCommandService)) as OleMenuCommandService;
            if (mcs == null) return;
            // כל שלב בנפרד: חריגה באחד מהם (גרסת Visual Studio ישנה, CommandBars חסר, תוסף אחר...) לא מפילה את כל החבילה —
            // אחרת Visual Studio מראה "The 'DsVizPackage' package did not load correctly" ואף כפתור לא עובד
            Safe("new", () => Add(mcs, 0x0101, () => Tool("new")));
            Safe("insert", () => Add(mcs, 0x0102, () => Tool("insert")));
            Safe("bank", () => AddBank(mcs));
            Safe("submit", () => Add(mcs, 0x0104, () => Tool("submit")));
            Safe("visual run", () => Add(mcs, 0x0106, VisualRun));
            Safe("help", () => Add(mcs, 0x0107, () => Tool("help")));
            Safe("teacher", () => AddTeacher(mcs));
            Safe("toolbar", ShowToolbarOnce);
            Safe("follower", FollowActiveDocument);
            // "מאגר שאלות" / "השאלה" / "עמדת ניהול": בטעינה הסרגל עוד לא תמיד קיים, וכפתור שהוסתר פעם (גרסה בלי מאגר) נשאר מוסתר
            // ב-Visual Studio — ואז Visual Studio גם לא שואל עליו שוב. לכן מסנכרנים שוב אחרי שהסרגל עלה, ועוד כמה פעמים בדקה הראשונה
            Safe("bank sync", ResyncButtons);
            _ = JoinableTaskFactory.RunAsync(async () =>
            {
                foreach (int ms in new[] { 3000, 10000, 30000, 60000 })
                {
                    await Task.Delay(ms);
                    await JoinableTaskFactory.SwitchToMainThreadAsync();
                    Safe("bank sync", ResyncButtons);
                    Safe("toolbar", ShowToolbarOnce);   // Visual Studio משחזר את סידור הסרגלים השמור אחרי שההרחבה נטענה
                }
            });
        }

        void ResyncButtons()
        {
            ThreadHelper.ThrowIfNotOnUIThread();
            bool bank = File.Exists(BankJar);
            if (_bankOn != bank) SolutionHints.Log("bank button (resync): " + (bank ? "shown" : "hidden"));
            _bankOn = bank;   // לפני הגישה ל-CommandBars: היא מפעילה שוב את BeforeQueryStatus
            SyncBankControl(bank);
            bool teacher = TeacherOn();
            _teacherSyncing = true;
            try
            {
                if (SyncControl("עמדת ניהול", "teacher", teacher)) { _teacherOn = teacher; _teacherSynced = true; }
            }
            finally { _teacherSyncing = false; }
            // ש-Visual Studio ישאל שוב על הפקודות (מאפשר את הכפתורים כשהמאגר הגיע אחרי הטעינה, למשל בעדכון ברקע)
            var shell = GetService(typeof(SVsUIShell)) as IVsUIShell;
            if (shell != null) shell.UpdateCommandUI(0);
        }

        static void Safe(string what, Action step)
        {
            try { step(); }
            catch (Exception ex) { SolutionHints.Log("init " + what + " failed: " + ex, true); }
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
        /// המיקום (שורה משלו מתחת לסרגלים הרגילים) נקבע בפעם הראשונה ובכל פעם שהסרגל היה מוסתר — אחרת המשתמש יכול להזיז אותו.
        /// </summary>
        void ShowToolbarOnce()
        {
            try
            {
                var dte = GetService(typeof(EnvDTE.DTE)) as EnvDTE.DTE;
                if (dte == null) return;
                dynamic bars = dte.CommandBars;
                dynamic bar = bars["DSViz"];
                bool wasHidden = !(bool)bar.Visible;
                if (wasHidden)
                {
                    bar.Visible = true;
                    ActivityLog.LogInformation("DsViz", "toolbar shown");
                }
                string flag = Path.Combine(InstallDir, "vs-toolbar-shown");
                // מיקום בשורה משלו: בפעם הראשונה, וגם בכל פעם שהסרגל היה מוסתר — אז Visual Studio החזיר את ברירת המחדל
                // (למשל אחרי סגירה בכוח), והסרגל נדחס לסוף השורה הראשונה ורוב הכפתורים נעלמים מאחורי החץ
                // וגם כשהוא באותה שורה של סרגל אחר (Standard, Build, סרגל של הרחבה אחרת): שם אין לו מקום, והכפתורים נדחסים
                // מאחורי החץ "עוד" (הסרגל "מקופל"). בודקים בכל פתיחה — ומעבירים אותו לשורה משלו, אחרי כל הסרגלים העליונים
                string crowdedBy = null;
                int lastRow = 0;
                try
                {
                    object cur0 = bar.Position;
                    int topPos = Convert.ToInt32(Enum.Parse(cur0.GetType(), "msoBarTop"));
                    int myPos = Convert.ToInt32(cur0), myRow = (int)bar.RowIndex;
                    foreach (dynamic other in bars)
                    {
                        try
                        {
                            if (Convert.ToInt32(other.Type) != 0) continue;   // msoBarTypeNormal: סרגל כלים, לא תפריט
                            if (!(bool)other.Visible || Convert.ToInt32(other.Position) != topPos) continue;
                            string name = other.Name;
                            if (name == "DSViz") continue;
                            int r = (int)other.RowIndex;
                            if (r > lastRow) lastRow = r;
                            if (crowdedBy == null && myPos == topPos && r == myRow) crowdedBy = name;
                        }
                        catch (Exception) { }
                    }
                }
                catch (Exception ex1)
                {
                    SolutionHints.Log("toolbar rows scan failed: " + ex1.Message, true);
                }
                if (File.Exists(flag) && !wasHidden && crowdedBy == null) return;
                SolutionHints.Log("toolbar row: " + (wasHidden ? "was hidden" : crowdedBy != null ? "shared the row of " + crowdedBy : "first time"));
                // שורה משלו מתחת לסרגלים הרגילים (בלי זה הוא נדחס לשורה הראשונה ורוב הכפתורים נעלמים לחץ "עוד")
                try
                {
                    object cur = bar.Position;
                    dynamic top = Enum.Parse(cur.GetType(), "msoBarTop");   // MsoBarPosition.msoBarTop (בלי הפניה לספרייה בזמן קומפילציה)
                    bar.Position = top;
                    bar.RowIndex = Math.Max(lastRow + 1, 3);
                    bar.Left = 0;
                    SolutionHints.Log("toolbar row: moved to row " + (int)bar.RowIndex);
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
            // "השאלה": החלון הצף של השאלה האחרונה מהמאגר (כמו הכפתור "השאלה" ב-Eclipse) — עד עכשיו רק מקיצור בשולחן העבודה
            var q = new OleMenuCommand((s, e) =>
            {
                try { Tool("question"); }
                catch (Exception ex) { Say("DSViz: " + ex.Message); }
            }, new CommandID(CmdSet, 0x0109));
            q.BeforeQueryStatus += (s, e) =>
            {
                bool on = false;
                try { on = File.Exists(BankJar); } catch (Exception) { }
                var c = (OleMenuCommand)s;
                c.Visible = on;
                c.Enabled = on;
            };
            mcs.AddCommand(q);
            SyncBankControl(File.Exists(BankJar));
        }

        bool? _bankOn;

        /// <summary>
        /// בנוסף ל-DynamicVisibility של הפקודה: מסתיר / מציג את הכפתור גם דרך DTE (CommandBars). בסרגל כלים הסתרה דרך הפקודה לבדה
        /// לא תמיד מעלימה את הכפתור (הוא נשאר אפור) — כאן מוודאים שהוא נעלם כשהמאגר לא מותקן.
        /// </summary>
        void SyncBankControl(bool on)
        {
            SyncControl("מאגר שאלות", "bank", on);
            SyncControl("השאלה", "question", on);
        }

        /// <returns>true — הכפתור נמצא בסרגל ועודכן (בטעינה הסרגל עוד לא תמיד קיים)</returns>

        /// <summary>מציג / מסתיר את הכפתור שהכיתוב שלו מכיל caption גם דרך DTE (CommandBars) — ראו SyncBankControl</summary>
        bool SyncControl(string caption, string tag, bool on)
        {
            bool found = false;
            try
            {
                ThreadHelper.ThrowIfNotOnUIThread();
                var dte = GetService(typeof(EnvDTE.DTE)) as EnvDTE.DTE;
                if (dte == null) return false;
                dynamic bar = ((dynamic)dte.CommandBars)["DSViz"];
                foreach (dynamic ctl in bar.Controls)
                {
                    string cap = ctl.Caption;
                    if (cap != null && cap.Contains(caption))
                    {
                        if (ctl.Visible != on) ctl.Visible = on;
                        found = true;
                        SolutionHints.Log(tag + " control visible=" + on);
                    }
                }
            }
            catch (Exception ex)
            {
                SolutionHints.Log(tag + " control sync failed: " + ex.Message, true);
            }
            return found;
        }

        // ------------------------------------------------------------------ "עמדת ניהול" — רק למורה מחובר

        /// <summary>
        /// הסימון שהכלי של Java כותב כשהמשתמש מחובר כמורה (Account.syncTeacherFlag): מייל ושם בלבד, בלי הכרטיס. הכניסה עצמה מוצפנת
        /// ונבדקת רק ב-Java — הסימון הוא רמז לסרגל, וחלון המורה בודק שוב (מורה שהושבת: הסבר, והסימון נמחק).
        /// המקום: התיקייה הנסתרת של כל קובצי הזיהוי, המשותפת לכל התוכנות (‎%LOCALAPPDATA%\Lamerhav\.cache‎). עד גרסה 1.0.1 הסימון
        /// היה בתיקיית ההתקנה — נקרא גם משם, לגרסה אחת (כלי Java ישן שעוד לא עודכן)
        /// </summary>
        static string IdentityDir
        {
            get { return Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "Lamerhav", ".cache"); }
        }

        static string TeacherFlag
        {
            get { return Path.Combine(IdentityDir, "teacher.flag"); }
        }

        static string LegacyTeacherFlag
        {
            get { return Path.Combine(InstallDir, "teacher.flag"); }
        }

        static bool TeacherOn()
        {
            try { return File.Exists(BankJar) && (File.Exists(TeacherFlag) || File.Exists(LegacyTeacherFlag)); }
            catch (Exception) { return false; }
        }

        bool? _teacherOn;
        bool _teacherSynced, _teacherSyncing;
        FileSystemWatcher _teacherWatch, _legacyTeacherWatch;

        /// <summary>"עמדת ניהול": כמו ב-Eclipse — בסרגל, נראה רק כשהמשתמש מחובר כמורה; פותח את חלון המורה (הקוד, העמדות, התנתקות)</summary>
        void AddTeacher(OleMenuCommandService mcs)
        {
            var cmd = new OleMenuCommand((s, e) =>
            {
                try { Teacher(); }
                catch (Exception ex) { Say("DSViz: " + ex.Message); }
            }, new CommandID(CmdSet, 0x0108));
            cmd.BeforeQueryStatus += (s, e) =>
            {
                bool on = TeacherOn();
                var c = (OleMenuCommand)s;
                c.Visible = on;
                c.Enabled = on;
                TeacherChanged(on);
            };
            mcs.AddCommand(cmd);
            // כמו "מאגר שאלות": סנכרון ראשון כאן, בלי לזכור את המצב — הבדיקה הראשונה של הפקודה (BeforeQueryStatus, אחרי שהסרגל
            // נבנה) מסנכרנת שוב. אם המצב נזכר כבר כאן, הכפתור נשאר אפור וגלוי (נמצא ב-vsix-test)
            _teacherSyncing = true;
            try { SyncControl("עמדת ניהול", "teacher", TeacherOn()); }
            finally { _teacherSyncing = false; }
            // כניסה / התנתקות בחלון של Java: הכפתור מתעדכן מיד (לא רק כשחוזרים ל-Visual Studio)
            // התיקייה הנסתרת עוד לא קיימת (אף תוכנה לא נכנסה במחשב) — יוצרים אותה (נסתרת, כמו ב-Java), כדי שיהיה אחרי מה לעקוב
            try
            {
                if (!Directory.Exists(IdentityDir))
                {
                    var d = Directory.CreateDirectory(IdentityDir);
                    d.Attributes |= FileAttributes.Hidden;
                }
                _teacherWatch = WatchFlag(IdentityDir);
            }
            catch (Exception ex)
            {
                SolutionHints.Log("teacher flag watch failed: " + ex.Message, true);
            }
            try
            {
                if (Directory.Exists(InstallDir)) _legacyTeacherWatch = WatchFlag(InstallDir);
            }
            catch (Exception ex)
            {
                SolutionHints.Log("legacy teacher flag watch failed: " + ex.Message, true);
            }
        }

        FileSystemWatcher WatchFlag(string dir)
        {
            var w = new FileSystemWatcher(dir, "teacher.flag") { NotifyFilter = NotifyFilters.FileName | NotifyFilters.LastWrite };
            w.Created += (s, e) => TeacherFlagChanged();
            w.Deleted += (s, e) => TeacherFlagChanged();
            w.Changed += (s, e) => TeacherFlagChanged();
            w.Renamed += (s, e) => TeacherFlagChanged();
            w.EnableRaisingEvents = true;
            return w;
        }

        /// <summary>
        /// מעדכן גם דרך CommandBars. בטעינה הכפתור עוד לא תמיד בסרגל — אז מנסים שוב בכל בדיקת פקודות עד שהוא נמצא (אחרת הוא
        /// נשאר אפור ונראה גם למי שלא מחובר כמורה — כך נמצא בבדיקה ב-Visual Studio האמיתי)
        /// </summary>
        void TeacherChanged(bool on)
        {
            ThreadHelper.ThrowIfNotOnUIThread();
            // הגישה ל-CommandBars (גם קריאה של Visible) מפעילה שוב את BeforeQueryStatus — בלי השמירה הזו: רקורסיה עד
            // stack overflow של Visual Studio (נמצא ב-CI: devenv נסגר בקוד 0xC00000FD)
            if (_teacherSyncing || (on == _teacherOn && _teacherSynced)) return;
            if (on != _teacherOn) SolutionHints.Log("teacher button: " + (on ? "shown" : "hidden"));
            _teacherOn = on;
            _teacherSyncing = true;
            try { _teacherSynced = SyncControl("עמדת ניהול", "teacher", on); }
            finally { _teacherSyncing = false; }
        }

        void TeacherFlagChanged()
        {
            _ = JoinableTaskFactory.RunAsync(async delegate
            {
                await JoinableTaskFactory.SwitchToMainThreadAsync();
                try
                {
                    TeacherChanged(TeacherOn());
                    var shell = await GetServiceAsync(typeof(SVsUIShell)) as IVsUIShell;
                    if (shell != null) shell.UpdateCommandUI(0);
                }
                catch (Exception ex)
                {
                    SolutionHints.Log("teacher refresh failed: " + ex.Message, true);
                }
            });
        }

        /// <summary>
        /// חלון המורה: ה-Java של ההתקנה מריץ את BankVs במצב teacher ישירות (לא דרך dsviz-cs.exe — כך זה עובד גם עם כלי ישן יותר
        /// בהתקנה). הכלי בודק בעצמו שהמשתמש מורה מחובר.
        /// </summary>
        void Teacher()
        {
            ThreadHelper.ThrowIfNotOnUIThread();
            if (!Installed()) return;
            if (!File.Exists(BankJar)) { Say("DSViz: מאגר השאלות לא מותקן."); return; }
            string java = Path.Combine(InstallDir, "jdk", "bin", "javaw.exe");
            if (!File.Exists(java)) java = "javaw";
            else java = DsVizTool.AsciiJava(java);   // שם משתמש בעברית: java.exe לא עולה מנתיב שלא בקידוד של Windows
            // נתיבים יחסיים מתוך תיקיית ההתקנה: שם משתמש בעברית (או בשפה אחרת) ב-%LOCALAPPDATA% לא עובר בשורת הפקודה של java
            // (הארגומנטים מפוענחים בקידוד של המערכת), אבל התיקייה הנוכחית כן
            var psi = new ProcessStartInfo(java) { UseShellExecute = false, CreateNoWindow = true, WorkingDirectory = InstallDir };
            psi.Arguments = "-cp \"" + BankJarRelative + ";dsviz.jar\" il.lamerhav.dsviz.BankVs teacher";
            Process.Start(psi);
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
