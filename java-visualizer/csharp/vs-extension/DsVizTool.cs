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
            var parts = new List<string>();
            foreach (string a in args) parts.Add("\"" + a.Replace("\"", "\\\"") + "\"");
            Process.Start(new ProcessStartInfo(Runner, string.Join(" ", parts)) { UseShellExecute = false, CreateNoWindow = true });
        }
    }
}
