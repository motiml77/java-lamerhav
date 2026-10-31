using System;
using System.Collections.Generic;
using System.Globalization;
using System.Linq;
using System.Runtime.CompilerServices;
using System.Text;
using System.Text.RegularExpressions;

namespace Unit4.CollectionsLib
{
    /// <summary>
    /// הלב של המעטפת (כמו Recorder בגרסת ה-Java): ההרצה הוויזואלית (dsviz-cs) מפעילה אותו,
    /// והספרייה וההוקים שהוכנסו לקוד של התלמיד מדווחים אליו. בהרצה רגילה (Active=false) — לא עושה כלום.
    /// בניגוד ל-Java אין debugger: כל שורה מדווחת מתוך הקוד עצמו (Instrumenter), ולכן הצילום מיידי.
    /// </summary>
    internal static class Recorder
    {
        public static bool Active;
        public static int MaxSteps = 5000;

        const int MaxCells = 300, MaxCallFrames = 60, MaxArray = 60, MaxMatrix = 20;

        public static readonly List<Step> Steps = new List<Step>();
        public static readonly StringBuilder Output = new StringBuilder();
        /// <summary>קבצי הקוד של התלמיד: שם קובץ → טקסט (נרשם ע"י dsviz-cs)</summary>
        public static readonly Dictionary<string, string> Sources = new Dictionary<string, string>();

        static readonly List<Frame> Frames = new List<Frame>();             // העליונה אחרונה
        static readonly Dictionary<object, string> Names = new Dictionary<object, string>(new RefComparer());
        static readonly Dictionary<object, int> Order = new Dictionary<object, int>(new RefComparer());
        static readonly Dictionary<string, List<string>> LastArrays = new Dictionary<string, List<string>>();
        static readonly Dictionary<string, string[]> LineCache = new Dictionary<string, string[]>();
        static List<string[]> currentVars = new List<string[]>();
        static int orderCounter, stackCount, queueCount;
        static bool limitReached, errorRecorded, ended;

        sealed class RefComparer : IEqualityComparer<object>
        {
            public new bool Equals(object a, object b) { return ReferenceEquals(a, b); }
            public int GetHashCode(object o) { return RuntimeHelpers.GetHashCode(o); }
        }

        // ------------------------------------------------------------ מסגרות והוקים

        const int MaxDepth = 4000;

        public static void Enter(string cls, string method, string file, int line)
        {
            if (!Active) return;
            if (Frames.Count >= MaxDepth)
                throw new InvalidOperationException("עומק הקריאות גדול מדי (" + MaxDepth + ") — רקורסיה בלי תנאי עצירה?");
            Frames.Add(new Frame { Class = cls, Method = method, File = file, Line = line });
        }

        public static void Exit()
        {
            if (!Active || Frames.Count == 0) return;
            Frame top = Frames[Frames.Count - 1];
            if (!top.Returned && !top.Faulted) AddReturn(null);
            if (Frames.Count == 1) endVars = new List<KeyValuePair<string, object>>(top.Vars);   // המשתנים של main — לצילום האחרון
            LoopGuard.OnExit(Frames.Count);
            Frames.RemoveAt(Frames.Count - 1);
        }

        public static void Fault(Exception e)
        {
            if (!Active) return;
            if (Frames.Count > 0) Frames[Frames.Count - 1].Faulted = true;
            if (errorRecorded) return;
            errorRecorded = true;
            AddStep(Kind.ERROR, null, Mark.NONE, null, "שגיאה: " + Describe(e), true);
        }

        public static void Line(int line, object[] kv)
        {
            if (!Active || Frames.Count == 0) return;
            Frame top = Frames[Frames.Count - 1];
            top.Line = line;
            LoopGuard.OnLine(Frames.Count, MethodKey(top), line);
            top.Vars.Clear();
            var vars = new List<string[]>();
            for (int i = 0; i + 1 < kv.Length; i += 2)
            {
                string name = (string)kv[i];
                if (name == "args" && kv[i + 1] is string[] && top.Method == "Main") continue;   // כמו ב-Java: args של main לא מוצג
                top.Vars.Add(new KeyValuePair<string, object>(name, kv[i + 1]));
                vars.Add(new[] { name, FormatVar(kv[i + 1]) });
            }
            currentVars = vars;
            currentVarsFrame = top;
            currentCallerVars = CallerVars();
            string code = SourceLine(top.File, line);
            string text = "שורה " + line + ":   ‪" + (code == null ? top.Method + "()" : code.Trim()) + "‬";
            AddStep(Kind.LINE, null, Mark.NONE, null, text, false);
        }

        static string MethodKey(Frame f) { return f.File + "|" + f.Class + "." + f.Method; }

        // ------------------------------------------------------------ לולאה שלא נגמרת (LoopGuard)

        /// <summary>הלולאה נעצרה — ההרצה לא תגיע ל"התוכנית הסתיימה"; Program מחכה ל-LoopStop במקום לסוף התוכנית</summary>
        public static volatile bool LoopStopped;
        public static readonly System.Threading.ManualResetEvent LoopStop = new System.Threading.ManualResetEvent(false);

        /// <summary>
        /// התנאי של לולאה (בכל סיבוב): השורה כמו כל שורה, ואז — האם הלולאה לא נגמרת. אם כן: הצעד האחרון הוא שורת התנאי
        /// עם ההחלטה (החלון מראה כרטיס שמסביר), והתוכנית נעצרת כאן — החוט של התלמיד נשאר תקוע, בלי לשרוף מעבד.
        /// </summary>
        public static void LoopLine(int line, string info, object cmp, object bound, object[] kv)
        {
            Line(line, kv);
            if (!Active || Frames.Count == 0 || LoopStopped) return;
            Frame top = Frames[Frames.Count - 1];
            string verdict;
            try { verdict = LoopGuard.Visit(Frames.Count, MethodKey(top), line, info, cmp, bound, kv, Steps.Count >= MaxSteps); }
            catch (Exception) { verdict = null; }
            if (verdict == null) return;
            LoopStopped = true;
            AddStep(Kind.ERROR, null, Mark.NONE, null, "הלולאה לא נגמרת — התוכנית נעצרה", true);
            Steps[Steps.Count - 1].Loop = verdict;
            try { Console.Error.WriteLine("הלולאה בשורה " + line + " לא נגמרת — התוכנית נעצרה (ההסבר בחלון של ההרצה הוויזואלית)"); }
            catch (Exception) { }
            LoopStop.Set();
            System.Threading.Thread.Sleep(System.Threading.Timeout.Infinite);
        }

        public static T Return<T>(T value)
        {
            if (Active && Frames.Count > 0) AddReturn(FormatVar(value));
            return value;
        }

        static void AddReturn(string value)
        {
            if (Steps.Count >= MaxSteps || Frames.Count == 0) return;
            Frame top = Frames[Frames.Count - 1];
            top.Returned = true;
            string m = top.Method == ".ctor" ? "<init>" : top.Method;
            string text = value == null ? "‏הפעולה " + m + " הסתיימה"
                                        : "‏הפעולה " + m + " מחזירה  ‎" + value + "‎";
            AddStep(Kind.RETURN, null, Mark.NONE, null, text, false);
            Steps[Steps.Count - 1].Returned = value;
        }

        public static void OnOutput(string text)
        {
            text = text.Replace("\r\n", "\n");
            Output.Append(text);
            if (!Active) return;
            string t = text.Trim();
            if (t.Length > 0) Record(Kind.OUTPUT, null, Mark.NONE, null, "הדפסה: " + Shorten(t, 60));
        }

        static List<KeyValuePair<string, object>> endVars = new List<KeyValuePair<string, object>>();

        public static void Finish()
        {
            if (!Active || ended) return;
            ended = true;
            Frames.Clear();
            // הצעד האחרון מציג את המבנים כפי שנשארו ב-main (כמו ב-Java), אבל בלי מסגרות קריאה
            Frames.Add(new Frame { Class = "Program", Method = "Main", Vars = endVars });
            AddStep(Kind.END, null, Mark.NONE, null, "התוכנית הסתיימה", true);
            Steps[Steps.Count - 1].Calls.Clear();
            Frames.Clear();
        }

        // ------------------------------------------------------------ דיווח מהספרייה

        public static void Create(object target, string text) { if (Active) LoopGuard.Mutations++; Record(Kind.CREATE, target, Mark.NEW, null, text); }
        public static void Change(object target, Mark mark, string ghost, string text) { if (Active) LoopGuard.Mutations++; Record(Kind.CHANGE, target, mark, ghost, text); }
        public static void Read(object target, string text) { Record(Kind.READ, target, Mark.READ, null, text); }
        public static void Check(object target, string text) { Record(Kind.CHECK, target, Mark.READ, null, text); }
        public static void Note(string text) { Record(Kind.NOTE, null, Mark.NONE, null, text); }

        /// <summary>בתוך פעולת עזר (Helpers.Duplicate): הפעולות שבפנים לא נרשמות — רק התוצאה, בצעד אחד</summary>
        internal static int Quiet;

        static void Record(Kind kind, object target, Mark mark, string ghost, string text)
        {
            if (!Active || Quiet > 0) return;
            AddStep(kind, target, mark, ghost, text, false);
        }

        static void AddStep(Kind kind, object target, Mark mark, string ghost, string text, bool always)
        {
            if (Steps.Count >= MaxSteps)
            {
                if (!limitReached && !always)
                {
                    limitReached = true;
                    // התוכנית ממשיכה לרוץ; לולאה שלא נגמרת תיעצר עם כרטיס משלה (LoopGuard) — כאן רק הערה
                    AddStep(Kind.NOTE, null, Mark.NONE, null,
                        "התיעוד נעצר אחרי " + MaxSteps + " צעדים — מכאן התוכנית ממשיכה לרוץ בלי לצייר", true);
                }
                if (!always) return;
            }
            var s = new Step { Kind = kind, Text = text };
            for (int i = Frames.Count - 1; i >= 0 && s.Calls.Count < MaxCallFrames; i--) s.Calls.Add(Frames[i].Copy());
            try
            {
                s.Views = Snapshot(target, mark, ghost);
                if (accOwner != null)
                {
                    Obj ob = ObjOf(accOwner);
                    if (ob != null) s.Access = new Access { Obj = ob, Field = accField, Text = accHow, Mark = accMark };
                }
            }
            catch (Exception) { s.Views = new List<View>(); }
            s.OutLen = Output.Length;
            s.Vars = currentVars;
            AttachFrameVars(s);
            Steps.Add(s);
        }

        // ------------------------------------------------------------ המשתנים של כל פעולה במחסנית (מעקב משתנים: בחירת פעולה)

        const int FrameVarsDepth = 10, FrameVarsMax = 20;
        /// <summary>המסגרת ש-currentVars שייכים לה (השורה האחרונה), והמשתנים של הפעולות שמחכות לה — כפי שהיו באותה שורה</summary>
        static Frame currentVarsFrame;
        static List<List<string[]>> currentCallerVars = new List<List<string[]>>();

        /// <summary>
        /// Step.Vars של הפעולה שרצה עכשיו, ו-FrameVars — המשתנים של הפעולות שמחכות (עד FrameVarsDepth מתחת לעליונה, ו-Main למטה),
        /// מהשורה האחרונה (כמו Vars וכמו ב-Java). אחרי חזרה מפעולה, בלי שורה חדשה עדיין (s.Push(Helper(x)), צעד החזרה של
        /// הפעולה שקראה, סוף התוכנית) — currentVars הם עדיין של הפעולה שחזרה: אז לוקחים את המשתנים של המסגרות עצמן.
        /// </summary>
        static void AttachFrameVars(Step s)
        {
            int n = Frames.Count;
            if (n == 0) return;
            List<List<string[]>> callers = currentCallerVars;
            if (Frames[n - 1] != currentVarsFrame)
            {
                s.Vars = FormatFrame(Frames[n - 1]);
                callers = CallerVars();
            }
            s.FrameVars = new List<List<string[]>> { s.Vars };
            for (int k = 1; k < s.Calls.Count && k - 1 < callers.Count; k++) s.FrameVars.Add(callers[k - 1]);
        }

        /// <summary>המשתנים של הפעולות שמחכות לפעולה העליונה: [0] = מי שקרא לה ... (null — עמוק מדי)</summary>
        static List<List<string[]>> CallerVars()
        {
            int n = Frames.Count;
            var res = new List<List<string[]>>();
            for (int k = 1; k < n && k < MaxCallFrames; k++)
                res.Add(k <= FrameVarsDepth || k == n - 1 ? FormatFrame(Frames[n - 1 - k]) : null);
            return res;
        }

        static List<string[]> FormatFrame(Frame f)
        {
            var res = new List<string[]>();
            foreach (var kv in f.Vars)
            {
                if (res.Count >= FrameVarsMax) break;
                res.Add(new[] { kv.Key, FormatVar(kv.Value) });
            }
            return res;
        }

        static string Describe(Exception e)
        {
            string s = e.GetType().Name + (string.IsNullOrEmpty(e.Message) ? "" : ": " + e.Message);
            if (e is NullReferenceException) s += "   (ניסיון להשתמש באובייקט שהוא null)";
            if (e is IndexOutOfRangeException) s += "   (אינדקס מחוץ לגבולות המערך)";
            if (e is InvalidCastException) s += "   (המרה לא חוקית)";
            return s;
        }

        // ------------------------------------------------------------ גישה למאפיינים של אובייקט (Student, Car)

        static int objIdCounter;
        static object accOwner;
        static string accField, accHow;
        static Mark accMark;

        /// <summary>מזהה לאובייקט של מחלקה (Student / Car) — 0 בהרצה רגילה</summary>
        public static int NextObjId() { return Active ? ++objIdCounter : 0; }

        /// <summary>מחרוזת עם מרכאות, לתצוגה בקוד: "נועה"</summary>
        public static string Lit(string s) { return s == null ? "null" : "\"" + Shorten(s, 24) + "\""; }

        /// <summary>getter: קריאה של המאפיין field של owner — מתועדת, והכרטיס של owner מוצג עם המאפיין מודגש. מחזיר את הערך</summary>
        public static T Got<T>(object owner, string field, string how, T value)
        {
            if (!Active) return value;
            object v = value;
            string text = v is string ? Lit((string)v) : v is Array ? ArrayText((Array)v) : v == null ? "null" : Plain(v);
            AccessStep(owner, field, how, Mark.READ, "  →  " + text);
            return value;
        }

        /// <summary>setter / פעולה ששינתה את המאפיין field של owner (אחרי השינוי)</summary>
        public static void Changed(object owner, string field, string how)
        {
            if (!Active) return;
            LoopGuard.Mutations++;
            AccessStep(owner, field, how, Mark.CHANGED, "");
        }

        static void AccessStep(object owner, string field, string how, Mark mark, string result)
        {
            accOwner = owner; accField = field; accHow = how; accMark = mark;
            try { AddStep(mark == Mark.CHANGED ? Kind.CHANGE : Kind.READ, owner, mark, null, Str(owner) + "." + how + result, false); }
            finally { accOwner = null; }
        }

        // ------------------------------------------------------------ אובייקטים של מחלקות: כרטיס + טבלת מאפיינים

        /// <summary>האובייקטים שנוצרו בצילום הנוכחי — כדי לחבר אליהם את שמות המשתנים</summary>
        static readonly List<Obj> SnapObjs = new List<Obj>();

        static bool IsRecordLike(Type t)
        {
            if (t == null || t.IsPrimitive || t.IsEnum || t.IsArray || t == typeof(string) || t == typeof(decimal) || t.IsPointer) return false;
            if (typeof(Delegate).IsAssignableFrom(t)) return false;
            string ns = t.Namespace ?? "";
            if (ns == "System" || ns.StartsWith("System.") || ns.StartsWith("Microsoft.")) return false;
            return t.Namespace != "Unit4.CollectionsLib" || t == typeof(Student) || t == typeof(Car);
        }

        /// <summary>כרטיס לאובייקט: Student / Car (מוכנים), או מחלקה של התלמידים (לפי המאפיינים שלה). null — ערך רגיל</summary>
        public static Obj ObjOf(object v)
        {
            if (v == null) return null;
            Obj o = null;
            var st = v as Student;
            var car = v as Car;
            if (st != null) o = st.VizObj();
            else if (car != null) o = car.VizObj();
            else if (IsRecordLike(v.GetType())) o = ReflectObj(v);
            if (o != null) SnapObjs.Add(o);
            return o;
        }

        static Obj ReflectObj(object v)
        {
            var o = new Obj { Id = RuntimeHelpers.GetHashCode(v), Type = CleanTypeName(v.GetType()) };
            var chain = new List<Type>();
            for (Type c = v.GetType(); c != null && c != typeof(object); c = c.BaseType) chain.Insert(0, c);
            foreach (Type c in chain)
            {
                foreach (var f in c.GetFields(System.Reflection.BindingFlags.Instance | System.Reflection.BindingFlags.Public
                                              | System.Reflection.BindingFlags.NonPublic | System.Reflection.BindingFlags.DeclaredOnly))
                {
                    if (o.Fields.Count >= 12) break;
                    string val;
                    try { val = FieldText(f.GetValue(v)); } catch (Exception) { val = "?"; }
                    string name = f.Name;
                    if (name.StartsWith("<") && name.Contains(">k__BackingField")) name = name.Substring(1, name.IndexOf('>') - 1);   // מאפיין אוטומטי
                    o.Fields.Add(new[] { name, val });
                }
            }
            Describe(o);
            return o;
        }

        static string CleanTypeName(Type t)
        {
            string n = t.Name;
            int k = n.IndexOf('`');
            return k > 0 ? n.Substring(0, k) : n;
        }

        /// <summary>ערך של מאפיין בטבלה: קצר, בסגנון C#</summary>
        static string FieldText(object x)
        {
            if (x == null) return "null";
            if (x is string) return Lit((string)x);
            if (x is char) return "'" + x + "'";
            if (x is bool || x is IFormattable) return Plain(x);
            if (x is Array)
            {
                var a = (Array)x;
                var b = new StringBuilder("[");
                int i = 0;
                foreach (object e in a)
                {
                    if (i++ >= 6) { b.Append(", …"); break; }
                    b.Append(i == 1 ? "" : ", ").Append(FieldText(e));
                }
                return b.Append(']').ToString();
            }
            if (x is Student || x is Car) return Str(x);
            if (x is IVizLinear || x is IVizNode || x is IVizBin) return FormatVar(x);
            try { return Shorten(x.ToString(), 24); } catch (Exception) { return x.GetType().Name; }
        }

        /// <summary>שורות הכרטיס של מחלקה כללית: כותרת = שדה טקסט בשם name/title (או הראשון), ושורה שנייה = עוד שני מאפיינים</summary>
        static void Describe(Obj o)
        {
            string[] main = null;
            foreach (string[] f in o.Fields)
            {
                string n = f[0].ToLowerInvariant();
                if (f[1].StartsWith("\"") && (n == "name" || n == "title" || n.EndsWith("name"))) { main = f; break; }
            }
            if (main == null) foreach (string[] f in o.Fields) if (f[1].StartsWith("\"")) { main = f; break; }
            var sub = new StringBuilder();
            int k = 0;
            foreach (string[] f in o.Fields)
            {
                if (f == main || k >= 2) continue;
                sub.Append(k++ == 0 ? "" : " · ").Append(f[0]).Append('=').Append(Unquote(f[1]));
            }
            o.Title = main != null ? Shorten(Unquote(main[1]), 16) : o.Type;
            o.Sub = Shorten(sub.ToString(), 22);
        }

        static string Unquote(string s) { return s.Length >= 2 && s[0] == '"' && s[s.Length - 1] == '"' ? s.Substring(1, s.Length - 2) : s; }

        /// <summary>שמות המשתנים מעל הכרטיסים; ואובייקט שמשתנה מצביע אליו ואינו בתוך מבנה — מוצג לבד</summary>
        static void AttachObjects(List<View> views, Frame top)
        {
            if (top == null) return;
            var seen = new HashSet<object>(new RefComparer());
            foreach (var kv in top.Vars)
            {
                object v = kv.Value;
                if (v == null || v.GetType().IsValueType || v is string || !(v is Student || v is Car || IsRecordLike(v.GetType())) || !seen.Add(v)) continue;
                Obj probe = ObjOf(v);                      // מקבל את אותה זהות כמו הכרטיסים בתוך המבנים
                var names = Aliases(v, true);
                bool shown = false;
                foreach (Obj o in SnapObjs.ToArray())
                {
                    if (o == probe || o.Id != probe.Id) continue;
                    shown = true;
                    foreach (string n in names) if (!o.Labels.Contains(n)) o.Labels.Add(n);
                }
                if (!shown)
                {
                    probe.Labels.AddRange(names);
                    views.Add(new ObjView { Obj = probe, Title = string.Join(", ", names) });
                }
            }
        }

        // ------------------------------------------------------------ שמות

        /// <summary>נקרא מהבנאים: מנחשים את שם המשתנה מהשורה בקוד ("Stack&lt;int&gt; stk = new Stack&lt;int&gt;();" → stk)</summary>
        public static void Register(object o, string type)
        {
            if (!Active) return;
            Order[o] = orderCounter++;
            string guess = null;
            if (Frames.Count > 0)
            {
                Frame top = Frames[Frames.Count - 1];
                string line = SourceLine(top.File, top.Line);
                if (line != null)
                {
                    var all = Regex.Matches(line, @"new\s+" + type + @"\b");
                    if (all.Count == 1)
                    {
                        Match m = Regex.Match(line, @"(\w+)\s*=\s*new\s+" + type + @"\b");
                        if (m.Success) guess = m.Groups[1].Value;
                    }
                }
            }
            if (guess == null && type == "Stack") guess = "stack" + (++stackCount);
            if (guess == null && type == "Queue") guess = "queue" + (++queueCount);
            if (guess != null) Names[o] = guess;
        }

        public static string NameOf(object o)
        {
            if (Active)
            {
                var a = Aliases(o, true);
                if (a.Count > 0) return a[0];
            }
            string n;
            return Names.TryGetValue(o, out n) ? n : "?";
        }

        /// <summary>שמות המשתנים (במסגרת העליונה) שמצביעים על האובייקט; ואם topOnly=false גם משאר המסגרות: "שם (פעולה)"</summary>
        static List<string> Aliases(object o, bool topOnly)
        {
            var res = new List<string>();
            for (int i = Frames.Count - 1; i >= 0; i--)
            {
                Frame f = Frames[i];
                bool top = i == Frames.Count - 1;
                if (!top && topOnly) break;
                foreach (var kv in f.Vars)
                {
                    if (!ReferenceEquals(kv.Value, o)) continue;
                    if (!top && res.Contains(kv.Key)) continue;          // אותו שם גם בפעולה הקוראת — לא מכפילים
                    string n = top ? kv.Key : kv.Key + " (" + f.Method + ")";
                    if (!res.Contains(n)) res.Add(n);
                }
            }
            return res;
        }

        // ------------------------------------------------------------ טקסט

        public static string Str(object v)
        {
            string s;
            try
            {
                if (v == null) s = "null";
                else if (v is Student) s = ((Student)v).Label();
                else if (v is Car) s = ((Car)v).Label();
                else s = Plain(v);
            }
            catch (Exception) { s = "?"; }
            return Shorten(s, 14);
        }

        static string Plain(object v)
        {
            if (v is bool) return (bool)v ? "true" : "false";
            if (v is IFormattable) return ((IFormattable)v).ToString(null, CultureInfo.InvariantCulture);
            return v.ToString();
        }

        public static string NodeRef(object node)
        {
            if (node == null) return "null";
            var n = node as IVizNode;
            return "[" + Str(n != null ? n.VizValue : ((IVizBin)node).VizValue) + "]";
        }

        static string FormatVar(object v)
        {
            try
            {
                if (v == null) return "null";
                if (v is IVizLinear) return ((IVizLinear)v).IsStack ? "Stack " + Shorten(v.ToString(), 40) : "Queue " + Shorten(v.ToString(), 40);
                if (v is IVizNode) return "Node " + NodeRef(v);
                if (v is IVizBin) return "BinNode " + NodeRef(v);
                if (v is Student) return "Student " + Shorten(v.ToString(), 40);
                if (v is Car) return "Car " + Shorten(v.ToString(), 40);
                if (v is Array) return Shorten(ArrayText((Array)v), 40);
                return Shorten(Plain(v), 40);
            }
            catch (Exception) { return "?"; }
        }

        internal static string ArrayText(Array a)
        {
            var b = new StringBuilder("[");
            int i = 0;
            foreach (object x in a)
            {
                if (i++ > 0) b.Append(", ");
                b.Append(x is Array ? ArrayText((Array)x) : (x == null ? "null" : Plain(x)));
                if (i >= 200) { b.Append(", …"); break; }
            }
            return b.Append(']').ToString();
        }

        static string Shorten(string s, int max)
        {
            return s.Length <= max ? s : s.Substring(0, max - 1) + "…";
        }

        // ------------------------------------------------------------ קוד המקור

        static string[] LinesOf(string file)
        {
            if (file == null) return null;
            string[] l;
            if (LineCache.TryGetValue(file, out l)) return l;
            string text;
            l = Sources.TryGetValue(file, out text) ? text.Split('\n').Select(x => x.TrimEnd('\r')).ToArray() : null;
            LineCache[file] = l;
            return l;
        }

        /// <summary>שורה בקוד, בלי הערת // בסוף</summary>
        static string SourceLine(string file, int line)
        {
            string[] l = LinesOf(file);
            if (l == null || line < 1 || line > l.Length) return null;
            string s = l[line - 1];
            int c = s.IndexOf("//", StringComparison.Ordinal);
            return c >= 0 ? s.Substring(0, c) : s;
        }

        // ------------------------------------------------------------ צילום מצב

        static List<View> Snapshot(object target, Mark mark, string ghost)
        {
            var views = new List<View>();
            SnapObjs.Clear();
            Nested.Clear();
            NestPath.Clear();
            NestDepth = 0;
            Frame top = Frames.Count > 0 ? Frames[Frames.Count - 1] : null;

            // כל הערכים של המשתנים, מהמסגרת העליונה כלפי מטה (בלי כפילויות לפי זהות)
            var values = new List<object>();
            var seen = new HashSet<object>(new RefComparer());
            for (int i = Frames.Count - 1; i >= 0; i--)
                foreach (var kv in Frames[i].Vars)
                    if (kv.Value != null && !(kv.Value is string) && !kv.Value.GetType().IsValueType && seen.Add(kv.Value))
                        values.Add(kv.Value);
            if (target != null && seen.Add(target)) values.Add(target);

            // --- מערכים
            var arrays = new List<Array>();
            foreach (object v in values) if (v is Array && ((Array)v).Length > 0) arrays.Add((Array)v);
            var newLast = new Dictionary<string, List<string>>();
            foreach (Array a in arrays)
            {
                var names = Aliases(a, false);
                string title = names.Count > 0 ? string.Join(", ", names) : "מערך";
                views.Add(ArrayViewOf(a, title, top, newLast));
            }
            foreach (var kv in newLast) LastArrays[kv.Key] = kv.Value;

            // --- מחסניות ותורים (לפי סדר היצירה)
            var linears = new List<IVizLinear>();
            foreach (object v in values) if (v is IVizLinear) linears.Add((IVizLinear)v);
            linears.Sort((x, y) => Ord(x).CompareTo(Ord(y)));
            foreach (IVizLinear l in linears)
            {
                var names = Aliases(l, false);
                var v = LinearViewOf(l, target, mark, ghost);
                v.Title = names.Count > 0 ? string.Join(", ", names) : NameOfStored(l);
                views.Add(v);
            }

            // תוויות (שמות משתנים) מעל חוליות וצמתים: מהמסגרת העליונה
            var labels = new Dictionary<object, List<string>>(new RefComparer());
            if (top != null)
                foreach (var kv in top.Vars)
                    if (kv.Value is IVizNode || kv.Value is IVizBin)
                    {
                        List<string> l;
                        if (!labels.TryGetValue(kv.Value, out l)) labels[kv.Value] = l = new List<string>();
                        if (!l.Contains(kv.Key)) l.Add(kv.Key);
                    }

            // --- רשימות מקושרות
            var nodes = new List<IVizNode>();
            var nodeSet = new HashSet<object>(new RefComparer());
            foreach (object v in values)
                if (v is IVizNode)
                    for (IVizNode p = (IVizNode)v; p != null && nodeSet.Add(p) && nodes.Count < 5000; p = p.VizNext) nodes.Add(p);
            var pointed = new HashSet<object>(new RefComparer());
            foreach (IVizNode n in nodes) if (n.VizNext != null) pointed.Add(n.VizNext);
            var heads = nodes.Where(n => !pointed.Contains(n)).ToList();
            var drawnNodes = new HashSet<object>(new RefComparer());
            var lens = new Dictionary<object, int>(new RefComparer());
            foreach (IVizNode h in heads) lens[h] = ChainLength(h);
            heads = MainFirst(heads.OrderByDescending(h => lens[h]).ToList(), lens);
            var inMain = new Dictionary<object, KeyValuePair<ChainView, int>>(new RefComparer());   // חוליה → השרשרת שבשורה שלה היא מצוירת, והמקום
            foreach (IVizNode h in heads)
            {
                View cv = ChainOf(h, drawnNodes, target, mark, labels, inMain);
                if (cv != null) views.Add(cv);   // null: ענף של שרשרת קודמת
            }
            foreach (IVizNode n in nodes)   // מעגלים
            {
                if (drawnNodes.Contains(n)) continue;
                View cv = ChainOf(n, drawnNodes, target, mark, labels, inMain);
                if (cv != null) views.Add(cv);
            }

            // --- עצים
            var bins = new List<IVizBin>();
            var binSet = new HashSet<object>(new RefComparer());
            foreach (object v in values) if (v is IVizBin) CollectBins((IVizBin)v, bins, binSet, 0);
            var children = new HashSet<object>(new RefComparer());
            foreach (IVizBin b in bins) { if (b.VizLeft != null) children.Add(b.VizLeft); if (b.VizRight != null) children.Add(b.VizRight); }
            var roots = bins.Where(b => !children.Contains(b)).ToList();
            var sizes = new Dictionary<object, int>(new RefComparer());
            foreach (IVizBin r in roots) sizes[r] = TreeSize(r, new HashSet<object>(new RefComparer()));
            roots = roots.OrderByDescending(r => sizes[r]).ToList();
            var drawn = new HashSet<object>(new RefComparer());
            foreach (IVizBin r in roots)
            {
                var tv = new TreeView { Src = r };
                var al = Aliases(r, false);
                tv.Title = al.Count > 0 ? string.Join(", ", al) : "עץ";
                var focus = new bool[1];
                tv.Root = TreeCellOf(r, drawn, target, mark, labels, focus, 0);
                tv.Focus = focus[0];
                views.Add(tv);
            }
            // מבנה שמוצג בתוך תא של מבנה אחר, ואין משתנה שמצביע עליו (למשל ה-target של הצעד) — לא מציירים גם לבד
            views.RemoveAll(v => v.Src != null && Nested.Contains(v.Src));   // גם אם יש משתנה: שמו מופיע כתג על התא
            AttachObjects(views, top);
            return views;
        }

        // ------------------------------------------------------------ מבנה בתוך מבנה (מחסנית של מחסניות, רשימה של תורים, עץ של מחסניות...)

        const int MaxNest = 3;                                // כמה רמות מצוירות זו בתוך זו
        static int NestDepth;
        static readonly HashSet<object> Nested = new HashSet<object>(new RefComparer());   // מבנים שמוצגים בתוך תא של מבנה אחר
        static readonly List<object> NestPath = new List<object>();                       // המבנים שבתהליך ציור — נגד מעגלים

        static LinearView LinearViewOf(IVizLinear l, object target, Mark mark, string ghost)
        {
            bool added = !NestPath.Any(p => ReferenceEquals(p, l));
            if (added) NestPath.Add(l);   // מחסנית שמכילה את עצמה (גם דרך מבנה אחר) לא נכנסת בתוך עצמה
            try { return LinearViewIn(l, target, mark, ghost); }
            finally { if (added) NestPath.RemoveAt(NestPath.Count - 1); }
        }

        static LinearView LinearViewIn(IVizLinear l, object target, Mark mark, string ghost)
        {
            var v = new LinearView { Stack = l.IsStack, Focus = ReferenceEquals(l, target), Src = l };
            IList<object> items = l.VizItems;                    // מחסנית: הראש ראשון; תור: ראש התור ראשון
            bool anySub = false;
            foreach (object it in items)
            {
                v.Items.Add(Str(it));
                v.Objs.Add(ObjOf(it));
                View sub = SubOf(it, target, mark, ghost);
                v.Subs.Add(sub);
                anySub |= sub != null;
                if (sub != null && sub.Focus) v.Focus = true;   // המבנה הפנימי הוא שנגעו בו — גם החיצוני מסומן
            }
            if (!anySub) v.Subs.Clear();
            if (v.Focus)
            {
                v.Mark = mark;
                v.Ghost = ghost;
                v.MarkIndex = (!v.Stack && mark == Mark.NEW) ? items.Count - 1 : 0;
            }
            return v;
        }

        /// <summary>הערך הוא מבנה של הספרייה → תצוגה קטנה שלו, לציור בתוך התא. null: ערך רגיל / עמוק מדי / המבנה מכיל את עצמו</summary>
        static View SubOf(object item, object target, Mark mark, string ghost)
        {
            if (!(item is IVizLinear || item is IVizNode || item is IVizBin)) return null;
            if (NestDepth >= MaxNest) return null;
            foreach (object p in NestPath) if (ReferenceEquals(p, item)) return null;
            NestPath.Add(item);
            NestDepth++;
            try
            {
                Nested.Add(item);
                var none = new Dictionary<object, List<string>>(new RefComparer());
                var al = Aliases(item, false);
                string name = al.Count > 0 ? string.Join(", ", al) : null;   // תג על התא: משתנה שמצביע על המבנה הפנימי
                View res;
                var lin = item as IVizLinear;
                var node = item as IVizNode;
                if (lin != null) res = LinearViewOf(lin, target, mark, ReferenceEquals(item, target) ? ghost : null);
                else if (node != null) res = ChainOf(node, new HashSet<object>(new RefComparer()), target, mark, none);
                else
                {
                    var bin = (IVizBin)item;
                    var tv = new TreeView { Src = bin };
                    var focus = new bool[1];
                    tv.Root = TreeCellOf(bin, new HashSet<object>(new RefComparer()), target, mark, none, focus, 0);
                    tv.Focus = focus[0];
                    res = tv;
                }
                res.Title = name;
                return res;
            }
            finally
            {
                NestDepth--;
                NestPath.RemoveAt(NestPath.Count - 1);
            }
        }

        static string NameOfStored(object o)
        {
            string n;
            return Names.TryGetValue(o, out n) ? n : "?";
        }

        static int Ord(object o)
        {
            int r;
            return Order.TryGetValue(o, out r) ? r : -1;
        }

        static int ChainLength(IVizNode h)
        {
            var s = new HashSet<object>(new RefComparer());
            int c = 0;
            for (IVizNode p = h; p != null && s.Add(p) && c < MaxCells; p = p.VizNext) c++;
            return c;
        }

        static void CollectBins(IVizBin b, List<IVizBin> list, HashSet<object> set, int depth)
        {
            if (b == null || depth > 60 || !set.Add(b) || list.Count > 3000) return;
            list.Add(b);
            CollectBins(b.VizLeft, list, set, depth + 1);
            CollectBins(b.VizRight, list, set, depth + 1);
        }

        static int TreeSize(IVizBin t, HashSet<object> s)
        {
            if (t == null || !s.Add(t) || s.Count > 2000) return 0;
            return 1 + TreeSize(t.VizLeft, s) + TreeSize(t.VizRight, s);
        }

        /// <summary>
        /// שרשראות שמתחברות לאותו המשך (חוליה חדשה q אחרי q.SetNext(p.GetNext())): הארוכה — ואם שוות, הוותיקה (הרשימה הקיימת) —
        /// ראשונה, והשאר מיד אחריה: הן יצוירו כענפים שלה ולא כרשימה נפרדת (כמו ב-Java). הסדר בין הקבוצות לא משתנה
        /// </summary>
        static List<IVizNode> MainFirst(List<IVizNode> heads, Dictionary<object, int> lens)
        {
            var group = new Dictionary<object, int>(new RefComparer());   // חוליה → מספר הקבוצה שלה
            var groups = new List<List<IVizNode>>();
            foreach (IVizNode h in heads)
            {
                int g = -1;
                var walked = new List<IVizNode>();
                var mine = new HashSet<object>(new RefComparer());
                for (IVizNode p = h; p != null && mine.Add(p) && walked.Count < MaxCells; p = p.VizNext)
                {
                    if (group.TryGetValue(p, out g)) break;   // הגענו להמשך של שרשרת קודמת
                    g = -1;
                    walked.Add(p);
                }
                if (g < 0) { g = groups.Count; groups.Add(new List<IVizNode>()); }
                foreach (IVizNode p in walked) group[p] = g;
                groups[g].Add(h);
            }
            return groups.SelectMany(l => l.OrderByDescending(h => lens[h]).ThenBy(h => Ord(h))).ToList();
        }

        /// <summary>
        /// inMain: החוליות שכבר מצוירות בשורה הראשית של שרשרת. שרשרת שמגיעה לאחת מהן היא "ענף" של אותה שרשרת (מצוירת מתחתיה,
        /// עם חץ אל החוליה) — ואז מחזירים null. בלי inMain (מבנה בתוך תא) — "ממשיך ברשימה אחרת" כמו קודם
        /// </summary>
        static View ChainOf(IVizNode head, HashSet<object> drawn, object target, Mark mark, Dictionary<object, List<string>> labels,
                            Dictionary<object, KeyValuePair<ChainView, int>> inMain = null)
        {
            var c = new ChainView { Src = head };
            var al = Aliases(head, false);
            c.Title = al.Count > 0 ? string.Join(", ", al) : "רשימה";
            var pos = new Dictionary<object, int>(new RefComparer());
            var mine = new List<IVizNode>();
            bool joined = false;
            KeyValuePair<ChainView, int> join = default(KeyValuePair<ChainView, int>);
            IVizNode cur = head;
            while (cur != null)
            {
                if (pos.ContainsKey(cur)) { c.CycleTo = pos[cur]; break; }
                if (drawn.Contains(cur))
                {
                    if (inMain != null && inMain.TryGetValue(cur, out join)) joined = true;
                    else c.JoinText = "[" + Str(cur.VizValue) + "]";
                    break;
                }
                if (c.Cells.Count >= MaxCells) { c.Truncated = true; break; }
                pos[cur] = c.Cells.Count;
                drawn.Add(cur);
                mine.Add(cur);
                var cell = new Cell();
                FillCell(cell, cur, cur.VizValue, target, mark, labels);
                if (ReferenceEquals(cur, target) || (cell.Sub != null && cell.Sub.Focus)) c.Focus = true;
                c.Cells.Add(cell);
                cur = cur.VizNext;
            }
            if (joined)   // ענף: האחרונה מצביעה לחוליה באמצע שרשרת שכבר מצוירת
            {
                c.JoinTo = join.Value;
                join.Key.Branches.Add(c);
                if (c.Focus) join.Key.Focus = true;
                return null;
            }
            if (inMain != null)
                for (int i = 0; i < mine.Count; i++) inMain[mine[i]] = new KeyValuePair<ChainView, int>(c, i);
            return c;
        }

        static TreeCell TreeCellOf(IVizBin b, HashSet<object> drawn, object target, Mark mark,
                                   Dictionary<object, List<string>> labels, bool[] focus, int depth)
        {
            if (b == null) return null;
            var c = new TreeCell();
            FillCell(c, b, b.VizValue, target, mark, labels);
            if (ReferenceEquals(b, target) || (c.Sub != null && c.Sub.Focus)) focus[0] = true;
            if (!drawn.Add(b) || depth > 40) { c.Shared = true; return c; }
            c.Left = TreeCellOf(b.VizLeft, drawn, target, mark, labels, focus, depth + 1);
            c.Right = TreeCellOf(b.VizRight, drawn, target, mark, labels, focus, depth + 1);
            return c;
        }

        static void FillCell(Cell cell, object node, object value, object target, Mark mark, Dictionary<object, List<string>> labels)
        {
            cell.Value = Str(value);
            cell.Obj = ObjOf(value);
            cell.Sub = SubOf(value, target, mark, null);
            cell.Mark = ReferenceEquals(node, target) ? mark : Mark.NONE;
            cell.Current = false;
            List<string> l;
            if (labels.TryGetValue(node, out l)) cell.Labels.AddRange(l);
        }

        // ------------------------------------------------------------ מערכים

        static bool IsMatrix(Array a)
        {
            return a.Rank == 2 || (a.Rank == 1 && a.GetType().GetElementType().IsArray);
        }

        static View ArrayViewOf(Array arr, string title, Frame top, Dictionary<string, List<string>> newLast)
        {
            // משתני אינדקס: משתנים שלמים במסגרת העליונה (i, j...)
            var indexVars = new List<KeyValuePair<string, int>>();
            if (top != null)
                foreach (var kv in top.Vars)
                    if (kv.Value is int) indexVars.Add(new KeyValuePair<string, int>(kv.Key, (int)kv.Value));
            string code = null;
            if (top != null && top.File != null) Sources.TryGetValue(top.File, out code);
            var local = title.Split(new[] { ", " }, StringSplitOptions.None).Where(n => !n.Contains("(")).Select(n => n.Trim()).ToList();
            List<string> old;
            LastArrays.TryGetValue(title, out old);
            var flat = new List<string>();
            View view;
            if (IsMatrix(arr))
            {
                var m = new MatrixView();
                bool rect = arr.Rank == 2, anyObj = false;
                int rows = Math.Min(rect ? arr.GetLength(0) : arr.Length, MaxMatrix);
                for (int r = 0; r < rows; r++)
                {
                    var cells = new List<string>();
                    var objRow = new List<Obj>();
                    Action<object> add = item =>
                    {
                        cells.Add(Str(item));
                        Obj ob = ObjOf(item);
                        objRow.Add(ob);
                        anyObj |= ob != null;
                    };
                    if (rect)
                    {
                        int cols = Math.Min(arr.GetLength(1), MaxMatrix);
                        for (int c = 0; c < cols; c++) add(arr.GetValue(r, c));
                    }
                    else
                    {
                        var row = arr.GetValue(r) as Array;
                        int cols = row == null ? 0 : Math.Min(row.Length, MaxMatrix);
                        for (int c = 0; c < cols; c++) add(row.GetValue(c));
                    }
                    m.Rows.Add(cells);
                    m.Objs.Add(objRow);
                    for (int c = 0; c < cells.Count; c++)
                    {
                        string f = r + "," + c + "=" + cells[c];
                        flat.Add(f);
                        if (old != null && !old.Contains(f)) m.Changed.Add(r + "," + c);
                    }
                }
                if (!anyObj) m.Objs.Clear();
                foreach (var iv in indexVars)
                    foreach (string nm in local)
                    {
                        string rowPat = rect ? @"\[\s*" : @"\[\s*";
                        string colPat = rect ? @"\[[^\],]*,\s*" : @"\[[^\]]*\]\s*\[\s*";
                        if (UsedAs(code, nm, rowPat, iv.Key) && iv.Value >= 0 && iv.Value < m.Rows.Count) AddLabel(m.RowLabels, iv.Value, iv.Key);
                        if (UsedAs(code, nm, colPat, iv.Key) && iv.Value >= 0 && m.Rows.Count > 0 && iv.Value < m.Rows[0].Count) AddLabel(m.ColLabels, iv.Value, iv.Key);
                    }
                view = m;
            }
            else
            {
                var a = new ArrayView();
                int n = arr.Length;
                bool anyObj = false;
                for (int i = 0; i < Math.Min(n, MaxArray); i++)
                {
                    object item = arr.GetValue(i);
                    a.Items.Add(Str(item));
                    Obj ob = ObjOf(item);
                    a.Objs.Add(ob);
                    a.Subs.Add(SubOf(item, null, Mark.NONE, null));
                    anyObj |= ob != null;
                }
                if (!anyObj) a.Objs.Clear();
                if (a.Subs.All(x => x == null)) a.Subs.Clear();
                a.Truncated = n > MaxArray;
                for (int i = 0; i < a.Items.Count; i++)
                    if (old != null && (i >= old.Count || old[i] != a.Items[i])) a.Changed.Add(i);
                foreach (var iv in indexVars)
                    foreach (string nm in local)
                        if (iv.Value >= 0 && iv.Value < a.Items.Count && UsedAs(code, nm, @"\[\s*", iv.Key)) AddLabel(a.IndexLabels, iv.Value, iv.Key);
                flat.AddRange(a.Items);
                view = a;
            }
            newLast[title] = flat;
            view.Title = title;
            return view;
        }

        static void AddLabel(Dictionary<int, List<string>> m, int idx, string name)
        {
            List<string> l;
            if (!m.TryGetValue(idx, out l)) m[idx] = l = new List<string>();
            if (!l.Contains(name)) l.Add(name);
        }

        /// <summary>האם בקוד מופיע arr[ ... index (לפי התבנית שבאמצע)</summary>
        static bool UsedAs(string code, string arr, string middle, string index)
        {
            if (code == null) return false;
            return Regex.IsMatch(code, @"\b" + Regex.Escape(arr) + middle + Regex.Escape(index) + @"\b");
        }
    }
}
