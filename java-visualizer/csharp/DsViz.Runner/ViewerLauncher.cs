using System;
using System.Diagnostics;
using System.IO;
using System.Linq;

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
            foreach (string a in new[] { "-Dfile.encoding=UTF-8", "-Xshare:auto", "-XX:TieredStopAtLevel=1", "-XX:+UseSerialGC",
                                         "-cp", jar, "unit4.collectionsLib.TraceViewer", trace })
                psi.ArgumentList.Add(a);
            Console.WriteLine("נפתח חלון ההרצה הוויזואלית. סוגרים אותו כדי לסיים.");
            using (Process p = Process.Start(psi)) p.WaitForExit();
        }

        static string BaseDir { get { return AppContext.BaseDirectory; } }

        static string FindJava()
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

        static string FindJar()
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
}
