using System;
using System.Collections;
using System.Collections.Generic;
using System.Diagnostics;
using System.IO;
using System.Text;
using System.Text.RegularExpressions;
using System.Threading;
using System.Web.Script.Serialization;
using Microsoft.VisualStudio.Shell;

namespace DsViz.VsExtension
{
    /// <summary>הצעה לפתרון של סעיף אחד: באיזה קובץ, באיזו מחלקה ופעולה (ומספר הפרמטרים). הקוד עצמו נשאר בחנות — החלון של DSViz מציג אותו.</summary>
    internal sealed class Hint
    {
        public string Path = "", Class = "", Method = "", Label = "";
        public int ParamCount;
        public long Time;

        public string Key { get { return Class + "." + Method + "/" + ParamCount; } }
    }

    /// <summary>פעולה בקובץ הפתוח שיש לה הצעה: המיקום של השם שלה בטקסט</summary>
    internal sealed class LocatedHint
    {
        public Hint Hint;
        public int Start, Length;
    }

    /// <summary>
    /// ההצעות לפתרון שנוצרו ב"שליחת תרגיל ב-C#": אותה חנות כמו של VsSubmit (‎~/.dsviz/solutions.json‎ — מפתח לכל הצעה: נתיב הקובץ, מחלקה,
    /// פעולה ומספר הפרמטרים). רק פעולות שיש להן הצעה מקבלות מנורה; קובץ C# רגיל — לא מציג כלום. כל כשל שקט (לא מפיל את Visual Studio).
    /// לבדיקות: DSVIZ_SOLUTIONS מחליף את מיקום החנות; DSVIZ_VS_TRACE=1 כותב עקבות גם ל-%LOCALAPPDATA%\DSViz\vs-trace.log.
    /// </summary>
    internal static class SolutionHints
    {
        static readonly object Gate = new object();
        static DateTime _stamp = DateTime.MinValue;
        static long _length = -1;
        static List<Hint> _all = new List<Hint>();
        static Timer _timer;

        /// <summary>החנות השתנתה (נשלח מתהליכון רקע)</summary>
        public static event EventHandler StoreChanged;

        public static string StorePath
        {
            get
            {
                string env = Environment.GetEnvironmentVariable("DSVIZ_SOLUTIONS");
                if (!string.IsNullOrWhiteSpace(env)) return env;
                return System.IO.Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.UserProfile), ".dsviz", "solutions.json");
            }
        }

        public static bool Trace
        {
            get { return !string.IsNullOrEmpty(Environment.GetEnvironmentVariable("DSVIZ_VS_TRACE")); }
        }

        /// <summary>שורה ליומן הפעילות של Visual Studio (ActivityLog.xml), ובמצב בדיקה גם לקובץ. לא זורק.</summary>
        public static void Log(string text, bool warning = false)
        {
            try
            {
                if (warning) ActivityLog.LogWarning("DsViz", text);
                else ActivityLog.LogInformation("DsViz", text);
            }
            catch (Exception) { }
            if (!Trace) return;
            try
            {
                string dir = System.IO.Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "DSViz");
                Directory.CreateDirectory(dir);
                File.AppendAllText(System.IO.Path.Combine(dir, "vs-trace.log"), DateTime.Now.ToString("HH:mm:ss.fff") + " " + text + Environment.NewLine, Encoding.UTF8);
            }
            catch (Exception) { }
        }

        /// <summary>בודק מדי כמה שניות אם החנות השתנתה (תרגיל חדש נוצר בזמן ש-Visual Studio פתוח) — ואז המנורות מתעדכנות</summary>
        public static void StartWatching()
        {
            lock (Gate)
            {
                if (_timer != null) return;
                _timer = new Timer(_ =>
                {
                    try
                    {
                        if (Reload())
                        {
                            var h = StoreChanged;
                            if (h != null) h(null, EventArgs.Empty);
                        }
                    }
                    catch (Exception) { }
                }, null, 3000, 3000);
            }
        }

        /// <summary>טוען מחדש אם הקובץ השתנה; true — התוכן השתנה</summary>
        static bool Reload()
        {
            string path = StorePath;
            DateTime stamp = DateTime.MinValue;
            long len = -1;
            try
            {
                var fi = new FileInfo(path);
                if (fi.Exists) { stamp = fi.LastWriteTimeUtc; len = fi.Length; }
            }
            catch (Exception) { }
            lock (Gate)
            {
                if (stamp == _stamp && len == _length) return false;
                _stamp = stamp;
                _length = len;
                _all = len < 0 ? new List<Hint>() : Parse(path);
                return true;
            }
        }

        static List<Hint> Parse(string path)
        {
            var list = new List<Hint>();
            try
            {
                var js = new JavaScriptSerializer { MaxJsonLength = int.MaxValue, RecursionLimit = 64 };
                var root = js.DeserializeObject(File.ReadAllText(path, Encoding.UTF8)) as IDictionary<string, object>;
                object sols;
                if (root == null || !root.TryGetValue("solutions", out sols)) return list;
                var arr = sols as IEnumerable;
                if (arr == null) return list;
                foreach (object o in arr)
                {
                    var j = o as IDictionary<string, object>;
                    if (j == null) continue;
                    var h = new Hint { Path = Str(j, "path"), Class = Str(j, "class"), Method = Str(j, "method"), Label = Str(j, "label") };
                    object ps;
                    if (j.TryGetValue("params", out ps) && ps is ICollection) h.ParamCount = ((ICollection)ps).Count;
                    long.TryParse(Str(j, "time"), out h.Time);
                    // הצעה בלי קוד לא נטענת בחלון — אז גם אין מנורה (כמו SolutionStore)
                    if (Str(j, "code").Length == 0 || h.Method.Length == 0 || !h.Path.EndsWith(".cs", StringComparison.OrdinalIgnoreCase)) continue;
                    list.Add(h);
                }
            }
            catch (Exception ex)
            {
                Log("solutions store unreadable: " + ex.Message, true);
            }
            return list;
        }

        static string Str(IDictionary<string, object> j, string key)
        {
            object v;
            return j.TryGetValue(key, out v) && v != null ? Convert.ToString(v) : "";
        }

        static string Norm(string p)
        {
            return (p ?? "").Replace('\\', '/').TrimEnd('/');
        }

        /// <summary>התיקייה והקובץ: ex01_stacks/StackTargil.cs — כשהתרגיל הועתק / שונה שם התיקייה שמעליו</summary>
        static string Tail(string p)
        {
            string[] parts = Norm(p).Split('/');
            return parts.Length < 2 ? "" : (parts[parts.Length - 2] + "/" + parts[parts.Length - 1]).ToLowerInvariant();
        }

        /// <summary>ההצעות שנוצרו לקובץ הזה (הצעה אחרונה לכל פעולה). קובץ ש-DSViz לא יצר — רשימה ריקה.</summary>
        public static List<Hint> ForFile(string file)
        {
            var result = new List<Hint>();
            if (string.IsNullOrEmpty(file) || !file.EndsWith(".cs", StringComparison.OrdinalIgnoreCase)) return result;
            Reload();
            List<Hint> all;
            lock (Gate) all = _all;
            if (all.Count == 0) return result;
            string norm = Norm(file);
            var pick = new List<Hint>();
            foreach (Hint h in all) if (string.Equals(Norm(h.Path), norm, StringComparison.OrdinalIgnoreCase)) pick.Add(h);
            if (pick.Count == 0)
            {
                string tail = Tail(file);
                if (tail.Length > 0) foreach (Hint h in all) if (Tail(h.Path) == tail) pick.Add(h);
            }
            var byKey = new Dictionary<string, Hint>();
            foreach (Hint h in pick)
            {
                Hint o;
                if (!byKey.TryGetValue(h.Key, out o) || h.Time >= o.Time) byKey[h.Key] = h;
            }
            result.AddRange(byKey.Values);
            return result;
        }

        /// <summary>
        /// מוצא בטקסט של הקובץ את הפעולות שיש להן הצעה: כותרת של פעולה (public/static ...) בשם הנכון, עם אותו מספר פרמטרים,
        /// אחרי ההצהרה על המחלקה. (אותה התאמה כמו ב-Eclipse: שם ומספר פרמטרים — גם אם התלמיד שינה את הטיפוסים.)
        /// </summary>
        public static List<LocatedHint> Locate(string text, List<Hint> hints)
        {
            var found = new List<LocatedHint>();
            foreach (Hint h in hints)
            {
                try
                {
                    Match cls = Regex.Match(text, @"\b(?:class|struct|record|interface)\s+" + Regex.Escape(h.Class) + @"\b");
                    if (!cls.Success) continue;
                    var rx = new Regex(@"^[ \t]*(?:\[[^\]\r\n]*\][ \t]*)*(?:(?:public|private|protected|internal|static|async|override|virtual|sealed|unsafe|partial|new|extern)[ \t]+)+"
                        + @"[^\r\n;={}]*?(?<![\w.])(" + Regex.Escape(h.Method) + @")[ \t]*(?:<[^>\r\n(]*>)?[ \t]*\(", RegexOptions.Multiline);
                    for (Match m = rx.Match(text, cls.Index); m.Success; m = m.NextMatch())
                    {
                        int open = m.Index + m.Length - 1;
                        if (CountParams(text, open) != h.ParamCount) continue;
                        found.Add(new LocatedHint { Hint = h, Start = m.Groups[1].Index, Length = m.Groups[1].Length });
                        break;
                    }
                }
                catch (Exception ex)
                {
                    Log("locate failed for " + h.Method + ": " + ex.Message, true);
                }
            }
            found.Sort((a, b) => a.Start.CompareTo(b.Start));
            return found;
        }

        /// <summary>מספר הפרמטרים בסוגריים שנפתחים ב-open (פסיקים ברמה הראשונה, בלי <> ו-() ו-[] ופנים של מחרוזות)</summary>
        internal static int CountParams(string s, int open)
        {
            int depth = 0, commas = 0;
            bool any = false;
            for (int i = open; i < s.Length; i++)
            {
                char c = s[i];
                if (c == '"' || c == '\'')
                {
                    char q = c;
                    for (i++; i < s.Length && s[i] != q; i++) if (s[i] == '\\') i++;
                    any = true;
                    continue;
                }
                if (c == '(' || c == '[' || c == '{' || c == '<') { if (depth > 0 || c == '(') depth++; if (depth > 1) any = true; continue; }
                if (c == '>' && i > 0 && s[i - 1] == '=') continue;
                if (c == ')' || c == ']' || c == '}' || c == '>')
                {
                    depth--;
                    if (depth == 0) return any ? commas + 1 : 0;
                    continue;
                }
                if (c == ',' && depth == 1) { commas++; continue; }
                if (!char.IsWhiteSpace(c) && depth >= 1) any = true;
            }
            return -1;
        }
    }
}
