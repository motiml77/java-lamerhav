using System;
using System.ComponentModel;

namespace Unit4.CollectionsLib
{
    /// <summary>
    /// ההוקים שההרצה הוויזואלית (dsviz-cs) מכניסה לקוד של התלמיד לפני כל שורה. לא לקרוא להם ישירות.
    /// בהרצה רגילה (בלי dsviz-cs) הם לא עושים כלום.
    /// </summary>
    [EditorBrowsable(EditorBrowsableState.Never)]
    public static class VizHooks
    {
        public static void Enter(string cls, string method, string file, int line) { Recorder.Enter(cls, method, file, line); }

        public static void Exit() { Recorder.Exit(); }

        public static void Fault(Exception e) { Recorder.Fault(e); }

        /// <summary>לפני שורה: מספר השורה, ואחריו זוגות "שם", ערך של המשתנים. מחזירה true (כדי לשבץ בתנאי של לולאה)</summary>
        public static bool Ln(int line, params object[] kv)
        {
            Recorder.Line(line, kv);
            return true;
        }

        /// <summary>
        /// בתנאי של לולאה: כמו Ln, ועוד מה שידוע על הלולאה מהקוד (info), ושני הצדדים של ההשוואה (i &lt; n → i, n; אחרת null) —
        /// כדי לזהות לולאה שלא נגמרת (LoopGuard). מחזירה true
        /// </summary>
        public static bool Lp(int line, string info, object cmp, object bound, params object[] kv)
        {
            Recorder.LoopLine(line, info, cmp, bound, kv);
            return true;
        }

        public static T Ret<T>(T value) { return Recorder.Return(value); }
    }
}
