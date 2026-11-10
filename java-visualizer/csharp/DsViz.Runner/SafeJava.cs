using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.IO;
using System.Linq;

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

        /// <summary>ממלא את psi: אפשרויות ה-JVM, ‎-cp‎, המחלקה והארגומנטים</summary>
        public static void Fill(ProcessStartInfo psi, IEnumerable<string> jvm, IList<string> classpath, string main, IList<string> args)
        {
            bool win = OperatingSystem.IsWindows();
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
