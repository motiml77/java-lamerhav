using System;
using System.Collections.Generic;
using System.Globalization;
using System.Runtime.CompilerServices;
using System.Text;

namespace Unit4.CollectionsLib
{
    /// <summary>
    /// לולאה שלא נגמרת בהרצה הוויזואלית של C# (כמו LoopGuard בגרסת ה-Java — אותם כללים ואותם ספים).
    /// ההוק שבתנאי של כל לולאה (VizHooks.Lp) מגיע לכאן בכל סיבוב, עם מה שה-Instrumenter ידע מהקוד (Info)
    /// ועם הערכים של עכשיו. ההחלטה:
    /// <list type="bullet">
    /// <item>א. בטוח: אותו מצב בדיוק (כל המשתנים, והמבנים לפי מונה השינויים) חוזר בשורת התנאי — בתוכנית בלי אקראיות / קלט / שעון;</item>
    /// <item>ב. כנראה: אחרי הרבה סיבובים (1000 — או 30 סיבובים ושתי שניות) המשתנים שבתנאי לא השתנו (והגוף לא משנה אותם),
    ///     או שהם רק מתרחקים מהסוף (i-- כשהתנאי i &lt; 10);</item>
    /// <item>ג. אחרי שהתיעוד מלא: לולאה שרצה יותר מ-10 שניות בלי לצאת (30 — אם היא מתקדמת לסוף).</item>
    /// </list>
    /// לולאה רגילה וארוכה (סכום 1..2000) לא נעצרת: המשתנה שבתנאי מתקדם לסוף.
    /// ההחלטה נכתבת לקובץ הצעדים (LOOP) באותו פורמט כמו ב-Java, והחלון (LoopCard) בונה ממנה את הכרטיס.
    /// </summary>
    internal static class LoopGuard
    {
        const int Rounds = 1000, QuickRounds = 30, SameRounds = 3;
        const long QuickMs = 2000, LongMs = 10000, LongTowardMs = 30000;

        /// <summary>מה ה-Instrumenter ידע על הלולאה מהקוד: "kind|header|end|op|cmpVar|flags|vars"</summary>
        sealed class Info
        {
            public string Kind = "while", Op = "", CmpVar = "";
            public int Header, End;
            public bool Exits, Pure, Written, Outside, Calls, Nondet, Fields, Constant;
            public string[] Vars = new string[0];

            public static Info Parse(string s)
            {
                var info = new Info();
                string[] f = s.Split('|');
                try
                {
                    info.Kind = f[0];
                    info.Header = int.Parse(f[1], CultureInfo.InvariantCulture);
                    info.End = int.Parse(f[2], CultureInfo.InvariantCulture);
                    info.Op = f[3];
                    info.CmpVar = f[4];
                    string fl = f[5];
                    info.Exits = fl.IndexOf('E') >= 0;      // break / return / throw בתוך הלולאה
                    info.Pure = fl.IndexOf('P') >= 0;       // התנאי לא משנה כלום
                    info.Written = fl.IndexOf('W') >= 0;    // משתנה מהתנאי משתנה בלולאה
                    info.Outside = fl.IndexOf('O') >= 0;    // הלולאה כותבת למאפיין / לתא במערך
                    info.Calls = fl.IndexOf('C') >= 0;      // הלולאה קוראת לפעולה של התלמיד
                    info.Nondet = fl.IndexOf('N') >= 0;     // אקראיות / קלט / שעון בקובץ
                    info.Fields = fl.IndexOf('F') >= 0;     // בתנאי יש מאפיין (לא משתנה מקומי)
                    info.Constant = fl.IndexOf('T') >= 0;   // while (true) / for (;;)
                    info.Vars = f.Length > 6 && f[6].Length > 0 ? f[6].Split(',') : new string[0];
                }
                catch (Exception) { info.Exits = true; }   // לא הבנו — לא מחליטים לפי הכללים (רק "אותו מצב")
                return info;
            }
        }

        /// <summary>לולאה אחת שרצה עכשיו (בקריאה מסוימת לפעולה)</summary>
        sealed class Act
        {
            public int Depth, Header, End, Visits, FirstMut, Away, Toward, Flat;
            public string Method, First;
            public long Start;
            public bool Same = true;
            public double? LastP;
            public readonly List<double> Values = new List<double>();
            public readonly HashSet<string> States = new HashSet<string>();
        }

        static readonly Dictionary<string, Info> infos = new Dictionary<string, Info>();
        static readonly Dictionary<string, Act> acts = new Dictionary<string, Act>();
        static readonly List<string> drop = new List<string>();

        /// <summary>כל שינוי במבנה של הספרייה (Push / Pop / SetNext / new...) — כדי לדעת אם משהו בלולאה השתנה</summary>
        internal static int Mutations;

        static long Now() { return DateTime.UtcNow.Ticks / TimeSpan.TicksPerMillisecond; }

        /// <summary>כל שורה: לולאות שיצאנו מהן (או שהפעולה שלהן הסתיימה) נשכחות</summary>
        internal static void OnLine(int depth, string method, int line)
        {
            if (acts.Count == 0) return;
            drop.Clear();
            foreach (var kv in acts)
            {
                Act a = kv.Value;
                if (a.Depth > depth || a.Depth == depth && (a.Method != method || line < a.Header || line > a.End)) drop.Add(kv.Key);
            }
            foreach (string k in drop) acts.Remove(k);
        }

        /// <summary>פעולה הסתיימה: הלולאות שלה (ושל מה שהיא קראה) נשכחות</summary>
        internal static void OnExit(int depth)
        {
            if (acts.Count == 0) return;
            drop.Clear();
            foreach (var kv in acts) if (kv.Value.Depth >= depth) drop.Add(kv.Key);
            foreach (string k in drop) acts.Remove(k);
        }

        /// <summary>
        /// ביקור בשורת התנאי. cmp / bound = שני הצדדים של השוואה פשוטה (i &lt; n) או null; kv = זוגות שם, ערך של המשתנים.
        /// מחזיר את ההחלטה (טקסט כמו LoopGuard.Verdict ב-Java) — או null אם הלולאה בסדר.
        /// </summary>
        internal static string Visit(int depth, string method, int line, string infoText, object cmp, object bound, object[] kv, bool recordingFull)
        {
            Info info;
            if (!infos.TryGetValue(infoText, out info)) infos[infoText] = info = Info.Parse(infoText);
            string key = depth + "|" + method + "|" + info.Header;
            Act a;
            if (!acts.TryGetValue(key, out a)) acts[key] = a = new Act { Depth = depth, Method = method, Header = info.Header, End = info.End };
            a.Visits++;
            long now = Now();
            int rounds = info.Kind == "do" ? a.Visits : a.Visits - 1;

            // המשתנים שבתנאי
            var cond = new StringBuilder();
            bool refs = false, foreign = false;
            foreach (string v in info.Vars)
            {
                object val = Find(kv, v);
                cond.Append(v).Append('=').Append(Ident(val)).Append(';');
                if (IsRef(val))
                {
                    refs = true;
                    if (!(val is IVizLinear || val is IVizNode || val is IVizBin || val is Student || val is Car)) foreign = true;   // אובייקט של התלמיד
                }
            }
            string condKey = cond.ToString();
            if (a.Visits == 1)
            {
                a.Start = now;
                a.First = condKey;
                a.FirstMut = Mutations;
            }
            else if (condKey != a.First || refs && Mutations != a.FirstMut)
            {
                a.Same = false;
            }
            double? p = Progress(info, cmp, bound);
            if (p != null && a.LastP != null)
            {
                if (p < a.LastP) { a.Away++; a.Toward = 0; a.Flat = 0; }
                else if (p > a.LastP) { a.Toward++; a.Away = 0; a.Flat = 0; }
                else { a.Flat++; a.Away = 0; a.Toward = 0; }
            }
            else if (p == null) a.Away = a.Toward = a.Flat = 0;
            a.LastP = p;
            double? cv = Num(cmp);
            if (cv != null)
            {
                if (a.Values.Count < 4) a.Values.Add(cv.Value);
                else a.Values[3] = cv.Value;
            }
            long ms = now - a.Start;
            bool enough = rounds >= Rounds || rounds >= QuickRounds && ms >= QuickMs;

            // א. אותו מצב בדיוק חוזר (הלולאה לא קוראת לפעולות של התלמיד ולא כותבת למאפיינים — אז המשתנים + המבנים הם כל המצב)
            if (!info.Nondet && !info.Calls && !info.Outside && !info.Fields && a.States.Count < 2000)
            {
                var st = new StringBuilder();
                for (int i = 0; i + 1 < kv.Length; i += 2) st.Append(kv[i]).Append('=').Append(Ident(kv[i + 1])).Append(';');
                st.Append('#').Append(Mutations);
                if (!a.States.Add(st.ToString()) && rounds >= SameRounds) return Verdict("same", info, line, rounds, ms, a);
            }
            if (!enough || info.Exits) return null;
            // ב. המשתנים שבתנאי לא השתנו בכלל, ושום דבר בלולאה לא משנה אותם
            bool touchable = foreign && (info.Calls || info.Outside) || info.Fields && (info.Calls || info.Outside);
            if (a.Same && info.Pure && !info.Written && !touchable) return Verdict("unchanged", info, line, rounds, ms, a);
            // ... או שהם משתנים יחד (i++ וגם n++) — המרחק לסוף לא קטן
            if (rounds >= Rounds && info.Pure && p != null && a.Flat >= rounds - 1) return Verdict("unchanged", info, line, rounds, ms, a);
            // ... או שהם רק מתרחקים מהסוף
            if (info.Pure && p != null && a.Away >= QuickRounds && a.Away >= Math.Min(rounds - 1, 200)) return Verdict("away", info, line, rounds, ms, a);
            // ג. התיעוד כבר מלא, והלולאה רצה הרבה זמן בלי לצאת
            if (recordingFull && ms >= (a.Toward > 0 ? LongTowardMs : LongMs)) return Verdict("long", info, line, rounds, ms, a);
            return null;
        }

        static object Find(object[] kv, string name)
        {
            for (int i = 0; i + 1 < kv.Length; i += 2) if ((string)kv[i] == name) return kv[i + 1];
            return null;
        }

        static bool IsRef(object v)
        {
            return v != null && !(v is string || v is bool || v is char || v is Enum || v is decimal || v.GetType().IsPrimitive);
        }

        /// <summary>ערך להשוואה בין סיבובים: מספר / מחרוזת כמו שהם; אובייקט / מבנה / מערך — הזהות שלו (התוכן — לפי מונה השינויים)</summary>
        static string Ident(object v)
        {
            if (v == null) return "null";
            if (v is string) return "\"" + v + "\"";
            if (!IsRef(v)) return v is IFormattable ? ((IFormattable)v).ToString(null, CultureInfo.InvariantCulture) : v.ToString();
            if (v is Array) return "@" + RuntimeHelpers.GetHashCode(v) + Recorder.ArrayText((Array)v);
            return "@" + v.GetType().Name + RuntimeHelpers.GetHashCode(v);
        }

        static double? Num(object v)
        {
            try
            {
                if (v == null || v is bool || v is string) return null;
                if (v is char) return (char)v;
                return Convert.ToDouble(v, CultureInfo.InvariantCulture);
            }
            catch (Exception) { return null; }
        }

        /// <summary>כמה הלולאה "קרובה לסוף" (גדל = מתקרבים): i &lt; n → i - n; x &gt; 0 → 0 - x; x != 0 → -|x - 0|</summary>
        static double? Progress(Info info, object cmp, object bound)
        {
            if (info.Op.Length == 0) return null;
            double? v = Num(cmp), b = Num(bound);
            if (v == null || b == null || double.IsNaN(v.Value) || double.IsNaN(b.Value)) return null;
            if (info.Op[0] == '<') return v - b;
            if (info.Op[0] == '>') return b - v;
            return -Math.Abs(v.Value - b.Value);
        }

        static string Verdict(string kind, Info info, int line, int rounds, long ms, Act a)
        {
            // הערכים של המשתנה שבהשוואה: כולם — או שלושת הראשונים, … והאחרון
            var t = new StringBuilder();
            var vals = a.Values;
            if (info.CmpVar.Length > 0 && vals.Count > 0)
            {
                bool more = vals.Count == 4 && a.Visits > 4;
                for (int i = 0; i < vals.Count; i++)
                {
                    if (i > 0) t.Append(',');
                    if (more && i == 3) t.Append("…,");
                    t.Append(Fmt(vals[i]));
                }
            }
            const char S = '\u0002';
            return "loop" + S + kind + S + line + S + rounds + S + ms + S + "0" + S + info.CmpVar + S + t;
        }

        static string Fmt(double x)
        {
            return x == Math.Floor(x) && Math.Abs(x) < 1e15 ? ((long)x).ToString(CultureInfo.InvariantCulture) : x.ToString(CultureInfo.InvariantCulture);
        }
    }
}
