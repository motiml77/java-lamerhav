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

        public static void Start(params string[] args)
        {
            var parts = new List<string>();
            foreach (string a in args) parts.Add("\"" + a.Replace("\"", "\\\"") + "\"");
            Process.Start(new ProcessStartInfo(Runner, string.Join(" ", parts)) { UseShellExecute = false, CreateNoWindow = true });
        }
    }
}
