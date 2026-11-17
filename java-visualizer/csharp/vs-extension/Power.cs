using System;
using System.IO;

namespace DsViz.VsExtension
{
    /// <summary>
    /// DSViz פועל או כבוי — הלחצן האדום "כיבוי DSViz" / הירוק "הפעלת DSViz" בסוף הסרגל (כמו Power ב-Eclipse ו-Dsviz.on() ב-IntelliJ).
    /// כשכבוי: כל שאר הכפתורים בסרגל מוסתרים ולא מפעילים כלום, אין שורות הודעה (RunInfoBar), אין החלפה של ה-Startup Project לפי הקובץ
    /// הפתוח ואין "הסבר" בזמן הרצה (StartupFollower), אין מנורות "הצעה לפתרון" (SolutionGlyph), ושום כלי של DSViz (dsviz-cs, המאגר, חלון
    /// המורה) לא מופעל מההרחבה — נשאר Visual Studio רגיל בלבד. הסרגל עצמו נשאר גלוי, כדי שהכפתור הירוק יהיה במקום.
    ///
    /// מה לא נכבה, בכוונה ("בלי לשבור את הקוד"): DSViz.targets של ההתקנה (installer/DSViz.targets — נטען על ידי MSBuild, לא על ידי
    /// ההרחבה). הוא מוסיף את Unit4.CollectionsLib לפרויקט רק כשהקוד משתמש בספרייה ואין לו כבר הפניה משלו ל-Unit4 — בלי זה פרויקטים של
    /// תלמידים שנכתבו עם DSViz (בלי הפניה בקובץ הפרויקט) היו מפסיקים להיבנות ברגע שמכבים. לכן הכיבוי לא נוגע בו (וגם לא בקבצי הפרויקט):
    /// הקוד נבנה ורץ ב-F5 / Ctrl+F5 בדיוק כמו קודם. (מי שרוצה לנתק גם אותו: ‎-p:DSVizDisable=true‎ — החלטה של הפרויקט, לא של הכפתור.)
    ///
    /// המצב נשמר בקובץ ‎%LOCALAPPDATA%\DSViz\vs-power-off.flag‎ (קיים = כבוי): נשאר אחרי סגירת Visual Studio ואחרי עדכון של DSViz
    /// (ההתקנה לא מוחקת קבצים בשורש התיקייה — כמו vs-loaded / vs-toolbar-shown), ומשותף לכל מופעי Visual Studio במחשב.
    /// </summary>
    internal static class Power
    {
        static readonly object Gate = new object();
        static bool? _on;

        /// <summary>DSViz כובה / הופעל (נשלח בתהליכון של הלחיצה — ה-UI)</summary>
        public static event EventHandler Changed;

        public static string FlagPath
        {
            get { return Path.Combine(DsVizTool.InstallDir, "vs-power-off.flag"); }
        }

        /// <summary>DSViz פועל? (ברירת המחדל: כן. נקרא מהדיסק פעם אחת — בדיקות הפקודות והמנורות שואלות הרבה)</summary>
        public static bool On
        {
            get
            {
                lock (Gate)
                {
                    if (_on == null)
                    {
                        try { _on = !File.Exists(FlagPath); }
                        catch (Exception) { _on = true; }
                    }
                    return _on.Value;
                }
            }
        }

        /// <summary>מכבה / מפעיל: שומר בקובץ, רושם ביומן ("power: off" / "power: on"), ומודיע לסרגל, ל-StartupFollower ולמנורות</summary>
        public static void Set(bool on)
        {
            lock (Gate)
            {
                try
                {
                    if (on)
                    {
                        if (File.Exists(FlagPath)) File.Delete(FlagPath);
                    }
                    else
                    {
                        Directory.CreateDirectory(DsVizTool.InstallDir);
                        File.WriteAllText(FlagPath, "DSViz is off in Visual Studio (" + DateTime.Now.ToString("s") + "). Delete this file, or click \"הפעלת DSViz\", to turn it back on.",
                            new System.Text.UTF8Encoding(false));
                    }
                }
                catch (Exception ex)
                {
                    // לא נשמר (תיקייה חסומה) — המצב נשאר עד סגירת Visual Studio
                    SolutionHints.Log("power: could not save the state: " + ex.Message, true);
                }
                _on = on;
            }
            SolutionHints.Log(on ? "power: on" : "power: off");
            var h = Changed;
            if (h == null) return;
            foreach (EventHandler one in h.GetInvocationList())
            {
                try { one(null, EventArgs.Empty); }
                catch (Exception ex) { SolutionHints.Log("power: listener failed: " + ex.Message, true); }
            }
        }
    }
}
