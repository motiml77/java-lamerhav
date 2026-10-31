using System;
using System.Collections.Generic;
using System.IO;
using System.Text;
using System.Threading;
using Unit4.CollectionsLib;

namespace DsViz
{
    /// <summary>
    /// קלט מהמקלדת בהרצה ויזואלית (Console.ReadLine, Console.Read, Console.In) — כמו Keyboard בגרסת ה-Java:
    /// Console.In של התוכנית מוחלף בקורא שמאחד שלושה מקורות — שורות שמקלידים בחלון ההרצה, הקלט האמיתי
    /// (חלון הקונסול של Visual Studio / הטרמינל של VS Code / קלט מצינור), וקלט מוכן לבדיקה — מה שמגיע קודם.
    /// <para>
    /// כשהתוכנית מנסה לקרוא ואין קלט: נרשם איפה היא מחכה (השורה, "Console.ReadLine()"), והחלון הוויזואלי נפתח
    /// כבר עכשיו (ולא רק בסוף ההרצה — LiveViewer) עם כל הצעדים עד כאן: השורה שקוראת מסומנת "⌨ מחכה לקלט מהמקלדת"
    /// ונפתח פס הקלדה. כל שורה שהתוכנית מקבלת מופיעה בפלט ונרשמת כצעד INPUT ("קלט מהמקלדת: 5").
    /// סוף הקלט (null) — כשלוחצים בחלון "סיום הקלט"; בלי חלון — כשהקלט האמיתי והקלט של הבדיקה נגמרו.
    /// </para>
    /// <para>
    /// בלי חלון (--trace, בדיקות) הקלט מגיע מהקלט האמיתי (צינור / קובץ), או מקלט מוכן לבדיקה:
    /// <c>--input קובץ</c>, או משתנה הסביבה <c>DSVIZ_INPUT</c> ("5\n7", או "@קובץ" — UTF-8) — שורות שמוקלדות לבד
    /// כשהתוכנית מחכה; <c>DSVIZ_INPUT_DELAY</c> = כמה מילישניות לחכות לפני כל שורה. הקלט נמסר שורה-שורה,
    /// כדי שכל שורה תירשם בשורת הקוד שקראה אותה.
    /// </para>
    /// </summary>
    internal sealed class Keyboard : TextReader
    {
        /// <summary>שורת קלט, והאם הוקלדה בחלון / בבדיקה (ואז מודפסת גם לקונסול)</summary>
        sealed class Line
        {
            public string Text;
            public bool Typed;
        }

        /// <summary>קלט שכבר בדרך (צינור / שורה שהוקלדה בקונסול) — מחכים לו רגע לפני "מחכה לקלט", כדי שהחלון לא ייפתח סתם</summary>
        const int GraceMs = 80;

        readonly TextReader real;
        readonly TextWriter console;          // הקונסול האמיתי (לא ההקלטה) — הד למה שהוקלד בחלון / בבדיקה
        readonly Func<Keyboard, LiveViewer> openViewer;   // null = אין חלון בזמן ההרצה (--trace / בדיקות)
        readonly object gate = new object();
        readonly System.Collections.Generic.Queue<Line> pending = new System.Collections.Generic.Queue<Line>();
        readonly System.Collections.Generic.Queue<string> auto = new System.Collections.Generic.Queue<string>();
        readonly int autoDelay;
        LiveViewer viewer;
        string cur;
        int curPos;
        bool ended, realEof, eofShown, readerStarted, noViewer;

        Keyboard(TextReader real, TextWriter console, Func<Keyboard, LiveViewer> openViewer, string autoText, int autoDelay)
        {
            this.real = real;
            this.console = console;
            this.openViewer = openViewer;
            this.autoDelay = autoDelay;
            if (autoText != null)
            {
                string[] lines = autoText.Replace("\\n", "\n").Replace("\r", "").Split('\n');
                int n = lines.Length;
                if (n > 0 && lines[n - 1].Length == 0) n--;   // ירידת שורה בסוף — לא שורה ריקה נוספת
                for (int i = 0; i < n; i++) auto.Enqueue(lines[i]);
            }
        }

        /// <summary>החלון שנפתח בזמן ההרצה (או null) — בסוף ההרצה הוא מקבל את שאר הצעדים</summary>
        public LiveViewer Viewer { get { return viewer; } }

        /// <summary>
        /// מחליף את Console.In — לפני שהתוכנית מתחילה. console = הקונסול האמיתי (לפני ההקלטה);
        /// openViewer = פותח את החלון כשהתוכנית מחכה לקלט (null — בלי חלון); inputFile = --input (או null → DSVIZ_INPUT).
        /// </summary>
        public static Keyboard Install(TextWriter console, Func<Keyboard, LiveViewer> openViewer, string inputFile)
        {
            string auto = inputFile != null ? "@" + inputFile : Environment.GetEnvironmentVariable("DSVIZ_INPUT");
            if (auto != null && auto.StartsWith("@"))   // @קובץ — השורות מקובץ (UTF-8), בלי תלות בקידוד של שורת הפקודה
            {
                try { auto = SourceReader.Read(auto.Substring(1)); }
                catch (Exception e)
                {
                    Console.Error.WriteLine("קלט לבדיקה (" + auto.Substring(1) + "): " + e.Message);
                    auto = null;
                }
            }
            int delay;
            if (!int.TryParse(Environment.GetEnvironmentVariable("DSVIZ_INPUT_DELAY") ?? "", out delay)) delay = 0;
            var k = new Keyboard(Console.In, console, openViewer, auto, delay);
            Console.SetIn(k);
            return k;
        }

        // ------------------------------------------------------------ מהחלון

        /// <summary>שורה שהוקלדה בחלון (בלי ירידת השורה)</summary>
        public void Submit(string text) { Offer(text, true); }

        /// <summary>"סיום הקלט": מכאן והלאה התוכנית מקבלת "אין עוד קלט" (כמו Ctrl+Z / Ctrl+D בקונסול)</summary>
        public void EndInput()
        {
            lock (gate)
            {
                ended = true;
                Monitor.PulseAll(gate);
            }
        }

        /// <summary>החלון לא נפתח (או נסגר): הקלט רק מהקונסול, וכשהוא נגמר — סוף הקלט, כמו בהרצה רגילה</summary>
        public void NoViewer()
        {
            lock (gate)
            {
                noViewer = true;
                Monitor.PulseAll(gate);
            }
        }

        // ------------------------------------------------------------ TextReader

        public override string ReadLine()
        {
            if (!Fill("ReadLine")) return null;
            lock (gate)
            {
                string rest = cur.Substring(curPos);
                curPos = cur.Length;
                return rest.TrimEnd('\n').TrimEnd('\r');
            }
        }

        public override int Read()
        {
            if (!Fill("Read")) return -1;
            lock (gate) return cur[curPos++];
        }

        public override int Peek()
        {
            if (!Fill("Peek")) return -1;
            lock (gate) return cur[curPos];
        }

        public override int Read(char[] buffer, int index, int count)
        {
            if (buffer == null) throw new ArgumentNullException(nameof(buffer));
            if (index < 0 || count < 0 || count > buffer.Length - index) throw new ArgumentOutOfRangeException(nameof(count));
            if (count == 0) return 0;
            if (!Fill("Read")) return 0;
            lock (gate)
            {
                int n = Math.Min(count, cur.Length - curPos);
                cur.CopyTo(curPos, buffer, index, n);
                curPos += n;
                return n;
            }
        }

        public override string ReadToEnd()
        {
            var b = new StringBuilder();
            while (Fill("ReadToEnd"))
            {
                lock (gate)
                {
                    b.Append(cur, curPos, cur.Length - curPos);
                    curPos = cur.Length;
                }
            }
            return b.ToString();
        }

        /// <summary>מוודא שיש תווים בשורה הנוכחית. false = סוף הקלט. method = הפעולה שהתוכנית קראה לה (ReadLine...)</summary>
        bool Fill(string method)
        {
            StartReader();
            bool waited = false;
            try
            {
                while (true)
                {
                    Line next;
                    bool first = false;
                    lock (gate)
                    {
                        if (cur != null && curPos < cur.Length) return true;
                        next = pending.Count > 0 ? pending.Dequeue() : null;
                        if (next == null && AtEnd())
                        {
                            first = !eofShown;
                            eofShown = true;
                        }
                    }
                    if (first) Recorder.InputEnd();   // צעד אחד: "סוף הקלט" (למשל while (... = Console.ReadLine()) != null) שמסתיים כאן)
                    lock (gate)
                    {
                        if (next == null && AtEnd()) return false;
                        if (next == null && !waited && !realEof)
                        {
                            Monitor.Wait(gate, GraceMs);
                            if (pending.Count > 0) next = pending.Dequeue();
                        }
                    }
                    if (next != null)
                    {
                        Take(next);
                        return true;
                    }
                    if (!waited)
                    {
                        waited = true;
                        BeginWait(method);
                        continue;
                    }
                    lock (gate)
                    {
                        if (pending.Count == 0 && !AtEnd()) Monitor.Wait(gate, 1000);
                    }
                }
            }
            finally
            {
                if (waited) viewer?.Resume();   // החלון: הפס נעלם, ומגיעים הצעדים החדשים (גם צעד הקלט)
            }
        }

        /// <summary>(בתוך gate) אין ולא יהיה עוד קלט: לחצו "סיום הקלט", או שאין חלון והקלט האמיתי והקלט של הבדיקה נגמרו</summary>
        bool AtEnd()
        {
            return pending.Count == 0 && (ended || !ViewerPossible() && auto.Count == 0 && realEof);
        }

        bool ViewerPossible()
        {
            if (noViewer) return false;
            return viewer != null ? viewer.Alive : openViewer != null;
        }

        /// <summary>התוכנית מקבלת את השורה: היא נרשמת (פלט + צעד INPUT), והתווים שלה עוברים לתוכנית</summary>
        void Take(Line l)
        {
            try { Recorder.Input(l.Text, l.Typed ? console : null); }
            catch (Exception) { }   // בלי צעד — התוכנית בכל זאת מקבלת את הקלט
            lock (gate)
            {
                cur = l.Text + "\n";
                curPos = 0;
            }
        }

        /// <summary>התוכנית מתחילה לחכות: איפה ומה היא קוראת; קלט לבדיקה "מוקלד", ואם אין — נפתח החלון ומחכים לו</summary>
        void BeginWait(string method)
        {
            InputWait w;
            try { w = Recorder.InputWaiting(method); }
            catch (Exception) { w = new InputWait { Step = -1, Call = method + "()", What = "ערך" }; }
            string line = null;
            lock (gate)
            {
                if (auto.Count > 0) line = auto.Dequeue();
            }
            if (viewer == null && line == null && openViewer != null && !noViewer)
            {
                LiveViewer v = null;
                try { v = openViewer(this); }
                catch (Exception e) { Console.Error.WriteLine("החלון לא נפתח: " + e.Message); }
                lock (gate)
                {
                    viewer = v;
                    if (v == null) noViewer = true;   // בלי Java — מקלידים בקונסול, כמו בהרצה רגילה
                    Monitor.PulseAll(gate);
                }
            }
            viewer?.Wait(w);
            if (line != null) TypeLater(line);
        }

        /// <summary>קלט מוכן לבדיקה: "מוקלד" אחרי DSVIZ_INPUT_DELAY מילישניות (או מיד)</summary>
        void TypeLater(string text)
        {
            if (autoDelay <= 0)
            {
                Offer(text, true);
                return;
            }
            var t = new Thread(() =>
            {
                Thread.Sleep(autoDelay);
                Offer(text, true);
            }) { IsBackground = true, Name = "dsviz-input-auto" };
            t.Start();
        }

        void Offer(string text, bool typed)
        {
            lock (gate)
            {
                pending.Enqueue(new Line { Text = text.Replace("\r", "").Replace("\n", " "), Typed = typed });
                Monitor.PulseAll(gate);
            }
        }

        /// <summary>
        /// הקלט האמיתי (חלון הקונסול / הטרמינל / צינור) — בחוט רקע, שורה-שורה. מתחיל רק בקריאה הראשונה,
        /// כך שתוכנית שלא קוראת קלט לא נוגעת בקונסול בכלל.
        /// </summary>
        void StartReader()
        {
            lock (gate)
            {
                if (readerStarted) return;
                readerStarted = true;
            }
            var t = new Thread(() =>
            {
                try
                {
                    for (string l; (l = real.ReadLine()) != null;)
                    {
                        lock (gate)
                        {
                            pending.Enqueue(new Line { Text = l, Typed = false });
                            Monitor.PulseAll(gate);
                        }
                    }
                }
                catch (Exception) { }   // הקלט האמיתי נסגר
                lock (gate)
                {
                    realEof = true;
                    Monitor.PulseAll(gate);
                }
            }) { IsBackground = true, Name = "dsviz-stdin" };
            t.Start();
        }
    }
}
