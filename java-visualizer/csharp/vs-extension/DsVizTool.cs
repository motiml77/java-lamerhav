using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.IO;

namespace DsViz.VsExtension
{
    /// <summary>הכלי של ההתקנה (%LOCALAPPDATA%\DSViz\cs\dsviz-cs.exe) — משותף לסרגל הכלים ולמנורות "הצעה לפתרון" שבעורך</summary>
    internal static class DsVizTool
    {
        public static string InstallDir
        {
            get { return Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "DSViz"); }
        }

        public static string Runner
        {
            get { return Path.Combine(InstallDir, "cs", "dsviz-cs.exe"); }
        }

        [System.Runtime.InteropServices.DllImport("kernel32.dll", CharSet = System.Runtime.InteropServices.CharSet.Unicode, SetLastError = true)]
        static extern uint GetShortPathName(string longPath, System.Text.StringBuilder shortPath, uint size);

        static bool Ascii(string s)
        {
            foreach (char c in s) if (c < 32 || c > 126) return false;
            return true;
        }

        /// <summary>
        /// java.exe לא עולה מתיקייה עם עברית ב-Windows שהקידוד שלו לא עברי ("could not find java.dll") — נתיב ASCII לאותו קובץ:
        /// צומת (junction) ב-ProgramData לתיקיית ה-Java, ואם אי אפשר — השם הקצר (8.3) (כמו SafeJava.Exe ב-dsviz-cs.exe)
        /// </summary>
        public static string AsciiJava(string java)
        {
            if (Ascii(java) || !File.Exists(java)) return java;
            try
            {
                string home = Path.GetDirectoryName(Path.GetDirectoryName(java));
                string hash;
                using (var sha = System.Security.Cryptography.SHA256.Create())
                    hash = BitConverter.ToString(sha.ComputeHash(System.Text.Encoding.UTF8.GetBytes(home.ToLowerInvariant()))).Replace("-", "").Substring(0, 10);
                string link = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.CommonApplicationData), "DSViz-jdk-" + hash);
                string exe = Path.Combine(link, "bin", Path.GetFileName(java));
                if (!Ascii(exe)) return java;
                if (!File.Exists(exe))
                {
                    if (Directory.Exists(link)) Directory.Delete(link);
                    var mk = new ProcessStartInfo("cmd.exe", "/c mklink /J \"" + link + "\" \"" + home + "\"") { UseShellExecute = false, CreateNoWindow = true };
                    using (Process p = Process.Start(mk)) p.WaitForExit(10000);
                }
                if (File.Exists(exe)) return exe;
            }
            catch (Exception) { }
            try
            {
                var sb = new System.Text.StringBuilder(1024);
                uint n = GetShortPathName(java, sb, (uint)sb.Capacity);
                if (n > 0 && n < sb.Capacity && Ascii(sb.ToString())) return sb.ToString();
            }
            catch (Exception) { }
            return java;
        }

        public static void Start(params string[] args)
        {
            Start(null, args);
        }

        /// <summary>
        /// כמו Start, ועוד משתני סביבה לכלי (env — שם ← ערך; ריק / null — לא נשלח). למשל DSVIZ_VS_FILE / DSVIZ_VS_SLN: הקובץ שפתוח ב-Visual Studio
        /// והפתרון — "צור מחלקה" יוצר את המחלקה ליד הקובץ ופותח אותה באותו Visual Studio. משתני סביבה (לא שורת הפקודה) — כדי שנתיב עם עברית יעבור כמו שהוא
        /// </summary>
        public static void Start(IDictionary<string, string> env, params string[] args)
        {
            var parts = new List<string>();
            foreach (string a in args) parts.Add("\"" + a.Replace("\"", "\\\"") + "\"");
            var psi = new ProcessStartInfo(Runner, string.Join(" ", parts)) { UseShellExecute = false, CreateNoWindow = true };
            // איזה Visual Studio פתח את הכלי: "הוספת מבנה נתונים" מכניס את הקוד בקובץ הפתוח שלו, בשורת הסמן (בלי הדבקה), ו"תרגיל חדש" מוסיף אליו את הפרויקט
            try { psi.EnvironmentVariables["DSVIZ_VS_PID"] = Process.GetCurrentProcess().Id.ToString(); } catch (Exception) { }
            if (env != null)
                foreach (var kv in env)
                    if (!string.IsNullOrEmpty(kv.Value))
                        try { psi.EnvironmentVariables[kv.Key] = kv.Value; } catch (Exception) { }
            Process.Start(psi);
        }

        /// <summary>
        /// הרצה ויזואלית: כמו Start, אבל אוספים את מה ש-dsviz-cs כותב. בלי חלון הוא כותב את ההסבר ל-stderr — ובלי זה כשההרצה נכשלת
        /// (שגיאת קומפילציה, אין Main) הלחיצה על הכפתור "לא עושה כלום". אם dsviz-cs נגמר בקוד 1/2/3 עם הסבר — onFail מקבל אותו.
        /// כשההרצה מצליחה התהליך ממשיך (החלון של התצוגה), ולא קורה כלום.
        /// </summary>
        /// <summary>onFail(ההודעה לתלמיד, קוד היציאה של dsviz-cs): ההודעה עוד כוללת שורות סימון (##dsviz-report) — Reporter.StripMarker מוריד אותן</summary>
        public static void Run(Action<string, int> onFail, params string[] args)
        {
            var parts = new List<string>();
            foreach (string a in args) parts.Add("\"" + a.Replace("\"", "\\\"") + "\"");
            var psi = new ProcessStartInfo(Runner, string.Join(" ", parts)) { UseShellExecute = false, CreateNoWindow = true };
            psi.RedirectStandardOutput = true;
            psi.RedirectStandardError = true;
            psi.StandardOutputEncoding = System.Text.Encoding.UTF8;
            psi.StandardErrorEncoding = System.Text.Encoding.UTF8;
            try { psi.EnvironmentVariables["DSVIZ_VS_PID"] = Process.GetCurrentProcess().Id.ToString(); } catch (Exception) { }
            var err = new System.Text.StringBuilder();
            var outp = new System.Text.StringBuilder();
            Process p = new Process { StartInfo = psi, EnableRaisingEvents = true };
            // הקריאה האסינכרונית חובה: בלי לרוקן את הצינור התהליך נתקע כשהוא כותב הרבה
            p.OutputDataReceived += (s, e) => { if (e.Data != null) lock (outp) { if (outp.Length < 6000) outp.AppendLine(e.Data); } };
            p.ErrorDataReceived += (s, e) => { if (e.Data != null) lock (err) { if (err.Length < 6000) err.AppendLine(e.Data); } };
            p.Exited += (s, e) =>
            {
                int code;
                try { p.WaitForExit(); code = p.ExitCode; } catch (Exception) { return; }   // WaitForExit() בלי זמן: מחכה שגם הקריאה מהצינורות תיגמר
                // רק כשההרצה לא התחילה ו-dsviz-cs הסביר למה: 1 = שגיאות קומפילציה, 2 = אין מה להריץ (אין Main, Main ריקה, קובץ לא נמצא),
                // 3 = תקלה פנימית. קוד אחר (תהליך שנסגר בכוח, החלון נסגר באמצע ההרצה) — בלי חלון הודעה: זו לא תקלה של התלמיד,
                // וחלון מודאלי היה תוקע את Visual Studio עד שמישהו לוחץ "אישור"
                if (code != 1 && code != 2 && code != 3) return;
                string text;
                lock (err) text = err.ToString().Trim();
                if (text.Length == 0) return;
                if (text.Length > 1500) text = text.Substring(0, 1500) + "…";
                try { onFail("ההרצה הוויזואלית לא התחילה:" + Environment.NewLine + Environment.NewLine + text, code); } catch (Exception) { }
            };
            p.Start();
            p.BeginOutputReadLine();
            p.BeginErrorReadLine();
        }
    }
}
