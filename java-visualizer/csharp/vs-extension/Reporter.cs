using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.IO;
using System.Text;
using System.Text.RegularExpressions;
using System.Threading.Tasks;
using System.Web.Script.Serialization;

namespace DsViz.VsExtension
{
    /// <summary>
    /// דיווח על בעיה לצוות DSViz מתוך Visual Studio — "דיווח על הבעיה" (המשתמש לחץ) ודיווח אוטומטי (ההרצה הוויזואלית נכשלה בגלל תקלה
    /// אצלנו: חסר בספרייה של DSViz משהו שיש בספרייה של בית הספר, או שגיאה פנימית של dsviz-cs).
    ///
    /// הדיווח נכתב כקובץ JSON לתיבת היוצא המשותפת (%LOCALAPPDATA%\DSViz\reports\outbox — אותו פורמט של Reports.java), ואז
    /// "javaw -cp dsviz.jar il.lamerhav.dsviz.Reports flush" שולח אותו עם כרטיס הכניסה של המחשב. בלי אינטרנט — נשאר בתיבה, ונשלח
    /// בפתיחה הבאה של Visual Studio (FlushPending) או עם הדיווח הבא. בדיווח אוטומטי מצורפת הבדיקה המעמיקה (DSViz-Diagnose.bat
    /// במצב שקט, ברקע). לא נשלח הקוד של התלמיד; תיקיית הבית ושם המשתמש מוחלפים ב-%USERPROFILE% / %USERNAME%.
    /// </summary>
    internal static class Reporter
    {
        const int MaxDiag = 300 * 1024, MaxFiles = 50;
        static readonly object Gate = new object();

        public static string Outbox { get { return Path.Combine(DsVizTool.InstallDir, "reports", "outbox"); } }

        /// <summary>מוסיף דיווח לתיבת היוצא ושולח ברקע. diagnose — להריץ קודם את הבדיקה המעמיקה (כמה שניות, ברקע)</summary>
        public static void Send(string kind, bool auto, string title, string message, string note, IDictionary<string, string> context, bool diagnose)
        {
            Task.Run(() =>
            {
                try
                {
                    string diag = diagnose ? Diagnose() : "";
                    Write(kind, auto, title, message, note, context, diag);
                    FlushPending();
                }
                catch (Exception ex) { SolutionHints.Log("report failed: " + ex.Message, true); }
            });
        }

        static string Id()
        {
            var b = new byte[18];
            using (var rng = System.Security.Cryptography.RandomNumberGenerator.Create()) rng.GetBytes(b);
            return Convert.ToBase64String(b).TrimEnd('=').Replace('+', '-').Replace('/', '_');
        }

        /// <summary>תיקיית הבית ושם המשתמש → ‎%USERPROFILE%‎ / ‎%USERNAME%‎ (כמו Reports.mask ו-DSViz-Diagnose.bat)</summary>
        public static string Mask(string s)
        {
            if (string.IsNullOrEmpty(s)) return s ?? "";
            string home = Environment.GetFolderPath(Environment.SpecialFolder.UserProfile);
            if (!string.IsNullOrEmpty(home) && home.Length > 3)
            {
                s = Regex.Replace(s, Regex.Escape(home), "%USERPROFILE%", RegexOptions.IgnoreCase);
                s = Regex.Replace(s, Regex.Escape(home.Replace('\\', '/')), "%USERPROFILE%", RegexOptions.IgnoreCase);
            }
            string user = Environment.UserName ?? "";
            if (user.Length >= 3) s = Regex.Replace(s, @"(?<![\p{L}\p{N}])" + Regex.Escape(user) + @"(?![\p{L}\p{N}])", "%USERNAME%", RegexOptions.IgnoreCase);
            return s;
        }

        static string VsName()
        {
            try
            {
                var v = Process.GetCurrentProcess().MainModule.FileVersionInfo;
                int major = v.FileMajorPart;
                return "Visual Studio " + (major >= 18 ? "2026" : major == 17 ? "2022" : major.ToString());
            }
            catch (Exception) { return "Visual Studio"; }
        }

        static string VsVersion()
        {
            try { return Process.GetCurrentProcess().MainModule.FileVersionInfo.ProductVersion ?? ""; } catch (Exception) { return ""; }
        }

        static string DsVizVersion()
        {
            string ext = typeof(Reporter).Assembly.GetName().Version.ToString();
            try
            {
                string b = Path.Combine(DsVizTool.InstallDir, "build.txt");
                if (File.Exists(b)) return File.ReadAllText(b).Trim() + " · הרחבה " + ext;
            }
            catch (Exception) { }
            return "הרחבה " + ext;
        }

        internal static string Write(string kind, bool auto, string title, string message, string note, IDictionary<string, string> context, string diag)
        {
            var ctx = new Dictionary<string, object>();
            if (context != null) foreach (var kv in context) ctx[kv.Key] = Mask(kv.Value);
            if (diag != null && diag.Length > MaxDiag) diag = diag.Substring(0, MaxDiag);
            string id = Id();
            var r = new Dictionary<string, object>
            {
                ["id"] = id, ["kind"] = kind, ["auto"] = auto, ["title"] = Mask(title), ["message"] = Mask(message), ["note"] = Mask(note ?? ""),
                ["app"] = VsName(), ["appVersion"] = VsVersion(), ["dsvizVersion"] = DsVizVersion(),
                ["os"] = Environment.OSVersion.VersionString + (Environment.Is64BitOperatingSystem ? " 64-bit" : "") + " · " + System.Globalization.CultureInfo.CurrentUICulture.Name,
                ["occurredAt"] = DateTime.UtcNow.ToString("yyyy-MM-ddTHH:mm:ss.fffZ"), ["context"] = ctx, ["diag"] = Mask(diag ?? ""),
            };
            var js = new JavaScriptSerializer { MaxJsonLength = 4 * 1024 * 1024 };
            lock (Gate)
            {
                Directory.CreateDirectory(Outbox);
                string tmp = Path.Combine(Outbox, id + ".tmp");
                File.WriteAllText(tmp, js.Serialize(r), new UTF8Encoding(false));
                File.Move(tmp, Path.Combine(Outbox, DateTime.UtcNow.ToString("yyyyMMddHHmmssfff") + "-" + id + ".json"));
                var all = new List<string>(Directory.GetFiles(Outbox, "*.json"));
                all.Sort(StringComparer.Ordinal);
                for (int i = 0; i < all.Count - MaxFiles; i++) { try { File.Delete(all[i]); } catch (Exception) { } }
            }
            SolutionHints.Log("report queued: " + kind + (auto ? " (auto)" : "") + " · " + title);
            return id;
        }

        /// <summary>שולח את מה שבתיבת היוצא (ברקע, בלי חלון) — בפתיחת Visual Studio ואחרי כל דיווח. אין מה לשלוח — לא מריץ כלום</summary>
        public static void FlushPending()
        {
            try
            {
                if (!Directory.Exists(Outbox) || Directory.GetFiles(Outbox, "*.json").Length == 0) return;
                if (Environment.GetEnvironmentVariable("DSVIZ_REPORTS_OFF") != null) { SolutionHints.Log("reports: not sent (DSVIZ_REPORTS_OFF)"); return; }   // בדיקות אוטומטיות
                string jar = Path.Combine(DsVizTool.InstallDir, "dsviz.jar");
                string java = Path.Combine(DsVizTool.InstallDir, "jdk", "bin", "javaw.exe");
                if (!File.Exists(jar) || !File.Exists(java)) return;
                java = DsVizTool.AsciiJava(java);
                // נתיב יחסי מתוך תיקיית ההתקנה — שם משתמש בעברית לא עובר בשורת הפקודה של java (כמו "עמדת ניהול")
                var psi = new ProcessStartInfo(java, "-cp dsviz.jar il.lamerhav.dsviz.Reports flush") { UseShellExecute = false, CreateNoWindow = true, WorkingDirectory = DsVizTool.InstallDir };
                Process.Start(psi);
                SolutionHints.Log("reports: flush started");
            }
            catch (Exception ex) { SolutionHints.Log("reports flush failed: " + ex.Message, true); }
        }

        /// <summary>הבדיקה המעמיקה (DSViz-Diagnose.bat במצב שקט, לקובץ זמני) — עד 90 שניות. אין את הקובץ / נכשל — ריק</summary>
        static string Diagnose()
        {
            string bat = Path.Combine(DsVizTool.InstallDir, "DSViz-Diagnose.bat");
            if (!File.Exists(bat)) return "";
            string outFile = Path.Combine(Path.GetTempPath(), "dsviz-diag-" + Guid.NewGuid().ToString("N") + ".txt");
            try
            {
                var psi = new ProcessStartInfo("cmd.exe", "/c \"\"" + bat + "\"\"") { UseShellExecute = false, CreateNoWindow = true, WindowStyle = ProcessWindowStyle.Hidden };
                psi.EnvironmentVariables["DSVIZ_DIAG_QUIET"] = "1";
                psi.EnvironmentVariables["DSVIZ_DIAG_OUT"] = outFile;
                using (Process p = Process.Start(psi))
                {
                    if (!p.WaitForExit(90000)) { try { p.Kill(); } catch (Exception) { } }
                }
                return File.Exists(outFile) ? File.ReadAllText(outFile, Encoding.UTF8) : "";
            }
            catch (Exception ex) { return "(הבדיקה המעמיקה נכשלה: " + ex.Message + ")"; }
            finally { try { File.Delete(outFile); } catch (Exception) { } }
        }

        /// <summary>מוריד מההודעה שורות סימון של dsviz-cs (‏##dsviz-report …) ומחזיר אם הייתה כזו</summary>
        public static string StripMarker(string text, out string reason)
        {
            reason = null;
            var sb = new StringBuilder();
            foreach (string line in (text ?? "").Replace("\r\n", "\n").Split('\n'))
            {
                if (line.StartsWith("##dsviz-report", StringComparison.Ordinal)) { reason = line.Substring(14).Trim(); continue; }
                sb.Append(line).Append('\n');
            }
            return sb.ToString().TrimEnd();
        }
    }
}
