using System;
using System.Diagnostics;
using System.IO;
using System.Linq;
using System.Text;
using System.Threading;
using Unit4.CollectionsLib;

namespace DsViz
{
    /// <summary>פותח את החלון הוויזואלי (Java) ומחכה שיסגר. ה-Java וה-dsviz.jar מגיעים מההתקנה.</summary>
    internal static class ViewerLauncher
    {
        public static void Show(string trace)
        {
            string java = FindJava(), jar = FindJar();
            if (java == null || jar == null)
            {
                Console.Error.WriteLine("לא נמצאו Java ו/או dsviz.jar להצגת החלון" + (java == null ? " (java)" : "") + (jar == null ? " (dsviz.jar)" : "")
                    + ". מתקינים מחדש את DSViz-Setup.exe. קובץ ההרצה: " + trace);
                return;
            }
            var psi = new ProcessStartInfo(java)
            {
                UseShellExecute = false,
                CreateNoWindow = true
            };
            foreach (string a in JvmArgs(jar)) psi.ArgumentList.Add(a);
            psi.ArgumentList.Add(trace);
            Console.WriteLine("נפתח חלון ההרצה הוויזואלית. סוגרים אותו כדי לסיים.");
            using (Process p = Process.Start(psi)) p.WaitForExit();
        }

        /// <summary>הפקודה עד שם המחלקה (בלי הקובץ / --live)</summary>
        internal static string[] JvmArgs(string jar)
        {
            return new[] { "-Dfile.encoding=UTF-8", "-Xshare:auto", "-XX:TieredStopAtLevel=1", "-XX:+UseSerialGC",
                           "-cp", jar, "unit4.collectionsLib.TraceViewer" };
        }

        static string BaseDir { get { return AppContext.BaseDirectory; } }

        internal static string FindJava()
        {
            string env = Environment.GetEnvironmentVariable("DSVIZ_JAVA");
            if (!string.IsNullOrEmpty(env) && File.Exists(env)) return env;
            bool win = Path.DirectorySeparatorChar == '\\';
            string exe = win ? "javaw.exe" : "java";
            var homes = new[]
            {
                Path.GetFullPath(Path.Combine(BaseDir, "..", "jdk")),                 // ההתקנה: %LOCALAPPDATA%\DSViz\cs\ ← ..\jdk
                ReadDsvizFile("jdk-path.txt"),
                Environment.GetEnvironmentVariable("JAVA_HOME")
            };
            foreach (string h in homes)
            {
                if (string.IsNullOrEmpty(h)) continue;
                string p = Path.Combine(h, "bin", exe);
                if (File.Exists(p)) return p;
            }
            foreach (string d in (Environment.GetEnvironmentVariable("PATH") ?? "").Split(Path.PathSeparator))
            {
                try { string p = Path.Combine(d, exe); if (File.Exists(p)) return p; } catch (Exception) { }
            }
            return null;
        }

        internal static string FindJar()
        {
            string env = Environment.GetEnvironmentVariable("DSVIZ_JAR");
            if (!string.IsNullOrEmpty(env) && File.Exists(env)) return env;
            foreach (string p in new[]
            {
                Path.GetFullPath(Path.Combine(BaseDir, "..", "dsviz.jar")),
                Path.Combine(BaseDir, "dsviz.jar")
            })
                if (File.Exists(p)) return p;
            return null;
        }

        /// <summary>קובץ מהתיקייה ~/.dsviz (ההתקנה כותבת שם נתיבים; UTF-16 עם BOM)</summary>
        static string ReadDsvizFile(string name)
        {
            try
            {
                string f = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.UserProfile), ".dsviz", name);
                return File.Exists(f) ? File.ReadAllText(f).Trim() : null;
            }
            catch (Exception) { return null; }
        }
    }

    /// <summary>
    /// החלון הוויזואלי בזמן ההרצה — נפתח כשהתוכנית מחכה לקלט מהמקלדת (Keyboard), כדי שאפשר יהיה להקליד בו
    /// (בלי קלט החלון נפתח רק בסוף, מקובץ — ViewerLauncher.Show). TraceViewer --live קורא מה-stdin שלו את
    /// אותן שורות של קובץ הצעדים, במנות שמסתיימות ב-SYNC, ובין המנות פקודות:
    /// <c>ASK</c> צעד מחלקה פעולה קובץ שורה קריאה מה — התוכנית מחכה לקלט; <c>RESUME</c> — הפסיקה לחכות; <c>DONE</c> — הסתיימה.
    /// מהחלון חוזרות שורות <c>DSVIZ-IN</c> טקסט (הוקלד), <c>DSVIZ-EOF</c> ("סיום הקלט"), <c>DSVIZ-READY</c> (החלון גלוי),
    /// <c>DSVIZ-NOVIEW</c> (החלון לא נפתח).
    /// </summary>
    internal sealed class LiveViewer
    {
        /// <summary>כל כמה זמן (לכל היותר) החלון מקבל את הצעדים החדשים בזמן שהתוכנית רצה</summary>
        const int TickMs = 250;

        readonly Process proc;
        readonly StreamWriter pipe;
        readonly TextWriter console;          // הקונסול האמיתי (לא ההקלטה)
        readonly Stopwatch since = Stopwatch.StartNew();
        int sent, outSent;
        volatile bool alive = true, done, shown;

        LiveViewer(Process proc, TextWriter console)
        {
            this.proc = proc;
            this.console = console;
            pipe = proc.StandardInput;
            pipe.AutoFlush = false;
        }

        public bool Alive { get { return alive; } }

        /// <summary>פותח את החלון עם כל הצעדים עד עכשיו (או null — אין Java / dsviz.jar: מקלידים בקונסול)</summary>
        public static LiveViewer Open(Keyboard keyboard, TextWriter console)
        {
            string java = ViewerLauncher.FindJava(), jar = ViewerLauncher.FindJar();
            if (java == null || jar == null) return null;
            var psi = new ProcessStartInfo(java)
            {
                UseShellExecute = false,
                CreateNoWindow = true,
                RedirectStandardInput = true,
                RedirectStandardOutput = true,
                StandardInputEncoding = new UTF8Encoding(false),
                StandardOutputEncoding = new UTF8Encoding(false)
            };
            foreach (string a in ViewerLauncher.JvmArgs(jar)) psi.ArgumentList.Add(a);
            psi.ArgumentList.Add("--live");
            Process p = Process.Start(psi);
            if (p == null) return null;
            var v = new LiveViewer(p, console);
            var reader = new Thread(() => v.ReadAnswers(keyboard, console)) { IsBackground = true, Name = "dsviz-viewer" };
            reader.Start();
            try
            {
                TraceWriter.Header(v.pipe);
                v.Flush(true);
            }
            catch (Exception) { v.alive = false; }
            // בקונסול: הסבר בשורה משלו, ואחריו שוב השאלה שהתוכנית הדפיסה בלי ירידת שורה ("כמה מספרים? ")
            string printed = Recorder.Output.ToString();
            string ask = printed.Substring(printed.LastIndexOf('\n') + 1);
            console.Write((ask.Length > 0 ? "\n" : "") + "⌨ התוכנית מחכה לקלט מהמקלדת — נפתח חלון ההרצה הוויזואלית: מקלידים בו (או כאן) ולוחצים Enter.\n" + ask);
            console.Flush();
            Recorder.Live = v.Tick;
            return v;
        }

        /// <summary>מה שחוזר מהחלון: שורות שהוקלדו, "סיום הקלט"; החלון נסגר באמצע ההרצה — ההרצה נעצרת (כמו ב-Java)</summary>
        void ReadAnswers(Keyboard keyboard, TextWriter console)
        {
            try
            {
                for (string l; (l = proc.StandardOutput.ReadLine()) != null;)
                {
                    if (l.StartsWith("DSVIZ-IN\t")) keyboard.Submit(Unescape(l.Substring(9)));
                    else if (l == "DSVIZ-EOF") keyboard.EndInput();
                    else if (l == "DSVIZ-READY") shown = true;
                    else if (l == "DSVIZ-NOVIEW") { alive = false; keyboard.NoViewer(); }
                }
            }
            catch (Exception) { }
            bool wasAlive = alive;
            alive = false;
            Recorder.Live = null;
            keyboard.NoViewer();
            if (wasAlive && !done && !shown)   // החלון לא הצליח להיפתח (למשל אין מסך) — מקלידים בקונסול
            {
                try
                {
                    console.WriteLine();
                    console.WriteLine("חלון ההרצה הוויזואלית לא נפתח — מקלידים כאן ולוחצים Enter.");
                    console.Flush();
                }
                catch (Exception) { }
            }
            else if (wasAlive && !done)
            {
                try
                {
                    console.WriteLine("החלון נסגר — ההרצה הופסקה.");
                    console.Flush();
                }
                catch (Exception) { }
                Environment.Exit(0);
            }
        }

        /// <summary>לפני כל שורה (Recorder.Live): הצעדים החדשים — לכל היותר פעם ב-TickMs</summary>
        void Tick()
        {
            if (alive && since.ElapsedMilliseconds >= TickMs && Recorder.Steps.Count > sent) Flush();
        }

        /// <summary>הצעדים שעוד לא נשלחו, הפלט החדש, ו-SYNC (בחוט של התוכנית — בין צעדים שלמים). first = המנה הראשונה — גם אם ריקה</summary>
        public void Flush(bool first = false)
        {
            if (!alive || !first && Recorder.Steps.Count == sent && Recorder.Output.Length == outSent) return;
            try
            {
                int n = Recorder.Steps.Count;
                TraceWriter.WriteSteps(pipe, sent, n, false);
                sent = n;
                if (Recorder.Output.Length > outSent)
                {
                    TraceWriter.Row(pipe, "OUT", Recorder.Output.ToString(outSent, Recorder.Output.Length - outSent));
                    outSent = Recorder.Output.Length;
                }
                TraceWriter.Row(pipe, "SYNC");
                pipe.Flush();
            }
            catch (Exception) { alive = false; }
            since.Restart();
        }

        /// <summary>התוכנית מחכה לקלט: הצעדים עד כאן, ואיפה היא מחכה — בחלון נפתח פס ההקלדה</summary>
        public void Wait(InputWait w)
        {
            Flush();
            Send("ASK", w.Step.ToString(), w.Class ?? "", w.Method ?? "", w.File ?? "", w.Line.ToString(), w.Call ?? "", w.What ?? "");
        }

        /// <summary>הקלט הגיע (או "סוף הקלט"): צעד הקלט, והפס נעלם</summary>
        public void Resume()
        {
            Flush();
            Send("RESUME");
        }

        /// <summary>התוכנית הסתיימה: שאר הצעדים, ומחכים שסוגרים את החלון</summary>
        public void Finish()
        {
            done = true;
            Recorder.Live = null;
            Flush();
            Send("DONE");
            try { pipe.Close(); } catch (Exception) { }
            if (!alive) return;
            console.WriteLine("ההרצה הסתיימה. סוגרים את חלון ההרצה הוויזואלית כדי לסיים.");
            try { proc.WaitForExit(); } catch (Exception) { }
        }

        void Send(params string[] row)
        {
            if (!alive) return;
            try
            {
                TraceWriter.Row(pipe, row);
                pipe.Flush();
            }
            catch (Exception) { alive = false; }
        }

        static string Unescape(string s)
        {
            if (s.IndexOf('\\') < 0) return s;
            var b = new StringBuilder(s.Length);
            for (int i = 0; i < s.Length; i++)
            {
                char c = s[i];
                if (c == '\\' && i + 1 < s.Length)
                {
                    char n = s[++i];
                    b.Append(n == 'n' ? '\n' : n == 't' ? '\t' : n == 'r' ? '\r' : n);
                }
                else b.Append(c);
            }
            return b.ToString();
        }
    }
}
