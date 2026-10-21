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
            string code = SourceLine(top.File, line);
            string text = "שורה " + line + ":   ‪" + (code == null ? top.Method + "()" : code.Trim()) + "‬";
            AddStep(Kind.LINE, null, Mark.NONE, null, text, false);
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

        public static void Create(object target, string text) { Record(Kind.CREATE, target, Mark.NEW, null, text); }
        public static void Change(object target, Mark mark, string ghost, string text) { Record(Kind.CHANGE, target, mark, ghost, text); }
        public static void Read(object target, string text) { Record(Kind.READ, target, Mark.READ, null, text); }
        public static void Check(object target, string text) { Record(Kind.CHECK, target, Mark.READ, null, text); }
        public static void Note(string text) { Record(Kind.NOTE, null, Mark.NONE, null, text); }

        static void Record(Kind kind, object target, Mark mark, string ghost, string text)
        {
            if (!Active) return;
            AddStep(kind, target, mark, ghost, text, false);
        }

        static void AddStep(Kind kind, object target, Mark mark, string ghost, string text, bool always)
        {
            if (Steps.Count >= MaxSteps)
            {
                if (!limitReached && !always)
                {
                    limitReached = true;
                    AddStep(Kind.ERROR, null, Mark.NONE, null,
                        "התיעוד נעצר אחרי " + MaxSteps + " צעדים — אולי יש לולאה אינסופית?", true);
                }
                if (!always) return;
            }
            var s = new Step { Kind = kind, Text = text };
            for (int i = Frames.Count - 1; i >= 0 && s.Calls.Count < MaxCallFrames; i--) s.Calls.Add(Frames[i].Copy());
            try { s.Views = Snapshot(target, mark, ghost); }
            catch (Exception) { s.Views = new List<View>(); }
            s.OutLen = Output.Length;
            s.Vars = currentVars;
            Steps.Add(s);
        }

        static string Describe(Exception e)
        {
            string s = e.GetType().Name + (string.IsNullOrEmpty(e.Message) ? "" : ": " + e.Message);
            if (e is NullReferenceException) s += "   (ניסיון להשתמש באובייקט שהוא null)";
            if (e is IndexOutOfRangeException) s += "   (אינדקס מחוץ לגבולות המערך)";
            if (e is InvalidCastException) s += "   (המרה לא חוקית)";
            return s;
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
                var v = new LinearView { Stack = l.IsStack, Focus = ReferenceEquals(l, target) };
                v.Title = names.Count > 0 ? string.Join(", ", names) : NameOfStored(l);
                IList<object> items = l.VizItems;                    // מחסנית: הראש ראשון; תור: ראש התור ראשון
                foreach (object it in items) v.Items.Add(Str(it));
                if (v.Focus)
                {
                    v.Mark = mark;
                    v.Ghost = ghost;
                    v.MarkIndex = (!v.Stack && mark == Mark.NEW) ? items.Count - 1 : 0;
                }
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
            heads = heads.OrderByDescending(h => lens[h]).ToList();
            foreach (IVizNode h in heads) views.Add(ChainOf(h, drawnNodes, target, mark, labels));
            foreach (IVizNode n in nodes) if (!drawnNodes.Contains(n)) views.Add(ChainOf(n, drawnNodes, target, mark, labels));   // מעגלים

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
                var tv = new TreeView();
                var al = Aliases(r, false);
                tv.Title = al.Count > 0 ? string.Join(", ", al) : "עץ";
                var focus = new bool[1];
                tv.Root = TreeCellOf(r, drawn, target, mark, labels, focus, 0);
                tv.Focus = focus[0];
                views.Add(tv);
            }
            return views;
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

        static View ChainOf(IVizNode head, HashSet<object> drawn, object target, Mark mark, Dictionary<object, List<string>> labels)
        {
            var c = new ChainView();
            var al = Aliases(head, false);
            c.Title = al.Count > 0 ? string.Join(", ", al) : "רשימה";
            var pos = new Dictionary<object, int>(new RefComparer());
            IVizNode cur = head;
            while (cur != null)
            {
                if (pos.ContainsKey(cur)) { c.CycleTo = pos[cur]; break; }
                if (drawn.Contains(cur)) { c.JoinText = "[" + Str(cur.VizValue) + "]"; break; }
                if (c.Cells.Count >= MaxCells) { c.Truncated = true; break; }
                pos[cur] = c.Cells.Count;
                drawn.Add(cur);
                var cell = new Cell();
                FillCell(cell, cur, cur.VizValue, target, mark, labels);
                if (ReferenceEquals(cur, target)) c.Focus = true;
                c.Cells.Add(cell);
                cur = cur.VizNext;
            }
            return c;
        }

        static TreeCell TreeCellOf(IVizBin b, HashSet<object> drawn, object target, Mark mark,
                                   Dictionary<object, List<string>> labels, bool[] focus, int depth)
        {
            if (b == null) return null;
            var c = new TreeCell();
            FillCell(c, b, b.VizValue, target, mark, labels);
            if (ReferenceEquals(b, target)) focus[0] = true;
            if (!drawn.Add(b) || depth > 40) { c.Shared = true; return c; }
            c.Left = TreeCellOf(b.VizLeft, drawn, target, mark, labels, focus, depth + 1);
            c.Right = TreeCellOf(b.VizRight, drawn, target, mark, labels, focus, depth + 1);
            return c;
        }

        static void FillCell(Cell cell, object node, object value, object target, Mark mark, Dictionary<object, List<string>> labels)
        {
            cell.Value = Str(value);
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
                bool rect = arr.Rank == 2;
                int rows = Math.Min(rect ? arr.GetLength(0) : arr.Length, MaxMatrix);
                for (int r = 0; r < rows; r++)
                {
                    var cells = new List<string>();
                    if (rect)
                    {
                        int cols = Math.Min(arr.GetLength(1), MaxMatrix);
                        for (int c = 0; c < cols; c++) cells.Add(Str(arr.GetValue(r, c)));
                    }
                    else
                    {
                        var row = arr.GetValue(r) as Array;
                        int cols = row == null ? 0 : Math.Min(row.Length, MaxMatrix);
                        for (int c = 0; c < cols; c++) cells.Add(Str(row.GetValue(c)));
                    }
                    m.Rows.Add(cells);
                    for (int c = 0; c < cells.Count; c++)
                    {
                        string f = r + "," + c + "=" + cells[c];
                        flat.Add(f);
                        if (old != null && !old.Contains(f)) m.Changed.Add(r + "," + c);
                    }
                }
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
                for (int i = 0; i < Math.Min(n, MaxArray); i++) a.Items.Add(Str(arr.GetValue(i)));
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
