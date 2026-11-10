using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.IO;
using System.Linq;
using System.Runtime.InteropServices;
using System.Text;

namespace DsViz
{
    /// <summary>
    /// הפעלת java ב-Windows בלי שנתיבים עם עברית (או כל תו מחוץ לקידוד ה-ANSI של המערכת) יישברו.
    /// java מפענחת את שורת הפקודה בקידוד של המערכת: אצל תלמיד ששם המשתמש שלו בעברית (C:\Users\משה\AppData\Local\DSViz) ב-Windows
    /// באנגלית, ה-classpath והנתיבים שמועברים ל-main הופכים ל-"?" — והחלון לא נפתח. לכן:
    ///   • ה-classpath — יחסי, מתוך התיקייה שבה ה-jar (WorkingDirectory), כשהנתיב המלא לא ASCII;
    ///   • הארגומנטים — כשיש בהם תו לא ASCII: "--args-env" ובמשתנה הסביבה DSVIZ_ARGS (שורה לכל ארגומנט). Java קוראת את הסביבה
    ///     ב-Unicode (BankVs / CsTool / TraceViewer — envArgs).
    /// ב-Mac / Linux הקידוד הוא UTF-8 — הכול עובר כמו שהוא.
    /// </summary>
    internal static class SafeJava
    {
        static bool Ascii(string s)
        {
            foreach (char c in s) if (c < 32 || c > 126) return false;
            return true;
        }

        [DllImport("kernel32.dll", CharSet = CharSet.Unicode, SetLastError = true)]
        static extern uint GetShortPathName(string longPath, StringBuilder shortPath, uint size);

        /// <summary>
        /// java.exe עצמו לא עולה מתיקייה עם עברית ב-Windows שהקידוד שלו לא עברי ("could not find java.dll": המשגר מוצא את
        /// התיקייה שלו בקידוד ANSI). לכן — נתיב ASCII לאותו קובץ: צומת (junction) ב-ProgramData שמצביע על תיקיית ה-Java של
        /// ההתקנה (בלי הרשאות מנהל), ואם אי אפשר — השם הקצר (8.3). אחרת — הנתיב כמו שהוא.
        /// </summary>
        public static string Exe(string java)
        {
            if (!OperatingSystem.IsWindows() || Ascii(java) || !File.Exists(java)) return java;
            try
            {
                string home = Path.GetDirectoryName(Path.GetDirectoryName(java));   // ...\jdk
                string hash = Convert.ToHexString(System.Security.Cryptography.SHA256.HashData(Encoding.UTF8.GetBytes(home.ToLowerInvariant()))).Substring(0, 10);
                string link = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.CommonApplicationData), "DSViz-jdk-" + hash);
                string exe = Path.Combine(link, "bin", Path.GetFileName(java));
                if (!Ascii(exe)) return java;
                if (!File.Exists(exe))
                {
                    if (Directory.Exists(link)) Directory.Delete(link);   // צומת ישן ששבור
                    var mk = new ProcessStartInfo("cmd.exe") { UseShellExecute = false, CreateNoWindow = true };
                    mk.ArgumentList.Add("/c");
                    mk.ArgumentList.Add("mklink");
                    mk.ArgumentList.Add("/J");
                    mk.ArgumentList.Add(link);
                    mk.ArgumentList.Add(home);
                    using (Process p = Process.Start(mk)) p.WaitForExit(10000);
                }
                if (File.Exists(exe)) return exe;
            }
            catch (Exception) { }
            try
            {
                var sb = new StringBuilder(1024);
                uint n = GetShortPathName(java, sb, (uint)sb.Capacity);
                if (n > 0 && n < sb.Capacity && Ascii(sb.ToString())) return sb.ToString();
            }
            catch (Exception) { }
            return java;
        }

        /// <summary>ממלא את psi: אפשרויות ה-JVM, ‎-cp‎, המחלקה והארגומנטים (וגם java.exe בנתיב ASCII — Exe)</summary>
        public static void Fill(ProcessStartInfo psi, IEnumerable<string> jvm, IList<string> classpath, string main, IList<string> args)
        {
            bool win = OperatingSystem.IsWindows();
            psi.FileName = Exe(psi.FileName);
            foreach (string o in jvm) psi.ArgumentList.Add(o);
            var cp = classpath.Select(Path.GetFullPath).ToList();
            if (win && !cp.All(Ascii))
            {
                string dir = Path.GetDirectoryName(cp[0]);
                var rel = cp.Select(e => Path.GetRelativePath(dir, e)).ToList();
                if (rel.All(Ascii)) { psi.WorkingDirectory = dir; cp = rel; }
            }
            psi.ArgumentList.Add("-cp");
            psi.ArgumentList.Add(string.Join(Path.PathSeparator.ToString(), cp));
            psi.ArgumentList.Add(main);
            if (!win || args.All(Ascii))
            {
                foreach (string a in args) psi.ArgumentList.Add(a);
            }
            else
            {
                psi.ArgumentList.Add("--args-env");
                psi.Environment["DSVIZ_ARGS"] = string.Join("\n", args);
            }
        }
    }
}
