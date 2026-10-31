package unit4.collectionsLib;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.nio.charset.Charset;
import java.util.ArrayDeque;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.SwingUtilities;

import unit4.collectionsLib.Model.Frame;

/**
 * קלט מהמקלדת בהרצה ויזואלית (Scanner, BufferedReader, System.in.read):
 * System.in של התוכנית מוחלף בזרם שמאחד שני מקורות — שורות שמקלידים בחלון ההרצה, והקלט האמיתי
 * (הקונסול של Eclipse / הטרמינל של VS Code, שהתהליך האב מעביר לכאן) — מה שמגיע קודם.
 * <p>
 * כשהתוכנית מנסה לקרוא ואין קלט: השורות שממתינות הופכות לצעדים (כך השורה שקוראת היא הצעד האחרון בחלון),
 * החלון מסמן אותה "⌨ מחכה לקלט מהמקלדת" ופותח שדה הקלדה, והתוכנית מחכה. כל שורה שהתוכנית מקבלת
 * מופיעה בפלט ונרשמת כצעד INPUT ("קלט מהמקלדת: 5"). סוף הקלט (EOF) — רק כשלוחצים "סיום הקלט":
 * סוף הקלט האמיתי (קונסול סגור / ריק) לא מסיים, כי אפשר עוד להקליד בחלון.
 * <p>
 * בלי חלון (בדיקות, -Djava.awt.headless=true) לא נוגעים ב-System.in — אלא אם יש קלט מוכן לבדיקה:
 * <pre>
 *   -Ddsviz.input="5\n7"        (או משתנה הסביבה DSVIZ_INPUT) — שורות שמוקלדות לבד כשהתוכנית מחכה לקלט
 *   -Ddsviz.input=@input.txt    — אותו דבר מקובץ (UTF-8; כך עברית עוברת גם כששורת הפקודה לא ב-UTF-8)
 *   -Ddsviz.inputDelay=3000     (או DSVIZ_INPUT_DELAY) — כמה מילישניות לחכות לפני כל שורה (כדי לצלם את מצב ההמתנה)
 * </pre>
 * הבתים עוברים לתוכנית בדיוק כמו שהם: מה שמוקלד בחלון מקודד ב-Charset.defaultCharset() — כמו ש-Scanner
 * ו-InputStreamReader מפענחים את System.in (כך עברית עוברת שלמה). הקלט נמסר שורה-שורה (read מחזיר עד סוף השורה),
 * כדי שכל שורה תירשם בשורת הקוד שקראה אותה.
 * <p>
 * תוכנית C# (dsviz-cs) רצה בתהליך אחר ומחליפה שם את Console.In בעצמה: החלון (TraceViewer --live) מקבל ממנה
 * "מחכה לקלט" / "הפסיקה לחכות" (remoteWait / remoteResume), ומה שמוקלד בחלון נשלח אליה (Remote) — אותו פס הקלדה.
 */
final class Keyboard extends InputStream {

    /** שורת קלט: הבתים שהתוכנית מקבלת (עם ירידת השורה), הטקסט לתצוגה, והאם הוקלדה בחלון / בבדיקה (ואז מודפסת גם לקונסול) */
    private static final class Line {
        final byte[] bytes;
        final String text;
        final boolean typed;

        Line(byte[] bytes, String text, boolean typed) {
            this.bytes = bytes;
            this.text = text;
            this.typed = typed;
        }
    }

    /** קלט שכבר בדרך (צינור / שורה שהוקלדה בקונסול) — מחכים לו רגע לפני שמראים "מחכה לקלט", כדי שהפס לא יהבהב */
    private static final long GRACE_MS = 80;

    private static Keyboard installed;
    private static volatile Runnable listener;

    // מצב ההמתנה — נקרא מהחלון
    private static volatile boolean waiting;
    private static volatile int waitId, waitStep = -1;
    private static volatile Frame waitFrame;
    private static volatile String waitCall, waitWhat = "ערך";

    private final InputStream real;
    private final Charset charset = Charset.defaultCharset();
    private final Charset realCharset;
    private final Object lock = new Object();
    private final ArrayDeque<Line> pending = new ArrayDeque<Line>();
    private final ArrayDeque<String> auto = new ArrayDeque<String>();
    private final long autoDelay;
    private volatile boolean viewer;
    private byte[] cur;
    private int curPos;
    private boolean ended, realEof, closed, eofShown;
    private Thread reader;

    private Keyboard(InputStream real, String autoText, long autoDelay, boolean viewer) {
        this.real = real;
        this.autoDelay = autoDelay;
        this.viewer = viewer;
        String enc = System.getProperty("stdin.encoding");   // Java 25+: הקידוד של הקונסול
        Charset rc = charset;
        try {
            if (enc != null && Charset.isSupported(enc)) rc = Charset.forName(enc);
        } catch (RuntimeException ignored) {
            // נשאר הקידוד הרגיל
        }
        realCharset = rc;
        if (autoText != null) {
            String[] lines = autoText.replace("\\n", "\n").replace("\r", "").split("\n", -1);
            int n = lines.length;
            if (n > 0 && lines[n - 1].isEmpty()) n--;   // ירידת שורה בסוף — לא שורה ריקה נוספת
            for (int i = 0; i < n; i++) auto.add(lines[i]);
        }
    }

    // ------------------------------------------------------------ התקנה

    /**
     * מחליף את System.in — לפני ש-main של התלמיד מתחיל (Scanner שומר את הזרם שהיה כשנוצר).
     * withViewer = יש חלון שאפשר להקליד בו. בלי חלון ובלי קלט לבדיקה — לא נוגעים: System.in הרגיל, בדיוק כמו קודם.
     */
    static synchronized void install(boolean withViewer) {
        if (installed != null) return;
        String auto = System.getProperty("dsviz.input");
        if (auto == null) auto = System.getenv("DSVIZ_INPUT");
        if (auto != null && auto.startsWith("@")) {   // @קובץ — השורות מקובץ (UTF-8), בלי תלות בקידוד של שורת הפקודה
            try {
                auto = new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(auto.substring(1))), "UTF-8");
            } catch (IOException | RuntimeException e) {
                System.err.println("dsviz.input: " + e);
                auto = null;
            }
        }
        if (!withViewer && auto == null) return;
        long delay = 0;
        String d = System.getProperty("dsviz.inputDelay");
        if (d == null) d = System.getenv("DSVIZ_INPUT_DELAY");
        try {
            if (d != null) delay = Long.parseLong(d.trim());
        } catch (NumberFormatException ignored) {
            // בלי השהיה
        }
        // Recorder.enabled כבוי לפני main — החלון לא נפתח (נעילה לתשלום): אין חלון לחכות לו
        installed = new Keyboard(System.in, auto, delay, withViewer && Recorder.enabled);
        System.setIn(installed);
    }

    static boolean installed() {
        return installed != null;
    }

    /** הקלט האמיתי שמתחת (בשביל התהליך האב של ההרצה שורה-שורה, שמעביר את הקונסול לתוכנית) */
    static InputStream original(InputStream in) {
        return in instanceof Keyboard ? ((Keyboard) in).real : in;
    }

    /** אין חלון (למשל נעילה לתשלום): כשהקלט האמיתי נגמר — סוף הקלט, כמו בהרצה רגילה */
    static void noViewer() {
        Remote r = remote;
        if (r != null) r.noViewer();
        Keyboard k = installed;
        if (k == null) return;
        k.viewer = false;
        synchronized (k.lock) { k.lock.notifyAll(); }
    }

    // ------------------------------------------------------------ מהחלון

    /** החלון: נקרא (בחוט של Swing) בכל פעם שהתוכנית מתחילה או מפסיקה לחכות לקלט */
    static void listen(Runnable r) {
        listener = r;
    }

    static boolean waiting() { return waiting; }
    /** מספר ההמתנה (גדל בכל פעם שהתוכנית מתחילה לחכות) — כדי שהחלון יבחין בין שתי המתנות רצופות */
    static int waitId() { return waitId; }
    /** האינדקס של הצעד האחרון כשהתוכנית התחילה לחכות (השורה שקוראת), או -1 */
    static int waitStep() { return waitStep; }
    /** השורה בקוד של התלמיד שקוראת עכשיו קלט */
    static Frame waitFrame() { return waitFrame; }
    /** "in.nextInt()" */
    static String waitCall() { return waitCall; }
    /** "מספר שלם" / "שורת טקסט" / "ערך"... */
    static String waitWhat() { return waitWhat; }

    /** שורה שהוקלדה בחלון (בלי ירידת השורה) */
    static void submit(String text) {
        Remote r = remote;
        if (r != null) {
            r.line(text);
            return;
        }
        Keyboard k = installed;
        if (k != null) k.offer(text, true);
    }

    /** "סיום הקלט": מכאן והלאה התוכנית מקבלת "אין עוד קלט" (כמו Ctrl+D / Ctrl+Z בקונסול) */
    static void endInput() {
        Remote r = remote;
        if (r != null) {
            r.end();
            return;
        }
        Keyboard k = installed;
        if (k == null) return;
        synchronized (k.lock) {
            k.ended = true;
            k.lock.notifyAll();
        }
    }

    // ------------------------------------------------------------ תוכנית שרצה בתהליך אחר (C# — TraceViewer --live)

    /** לאן הולך מה שמוקלד בחלון כשהתוכנית רצה בתהליך אחר */
    interface Remote {
        /** שורה שהוקלדה (בלי ירידת השורה) */
        void line(String text);
        /** "סיום הקלט" */
        void end();
        /** החלון לא נפתח — התוכנית מקבלת קלט רק מהקונסול */
        void noViewer();
    }

    private static volatile Remote remote;

    static void remote(Remote r) {
        remote = r;
    }

    /** התוכנית בתהליך האחר מתחילה לחכות לקלט: f = השורה שקוראת, call = "Console.ReadLine()", step = הצעד האחרון */
    static void remoteWait(Frame f, String call, String what, int step) {
        waitFrame = f;
        waitCall = call == null || call.isEmpty() ? null : call;
        waitWhat = what == null || what.isEmpty() ? "ערך" : what;
        waitStep = step;
        waitId++;
        waiting = true;
        notifyViewer();
    }

    /** התוכנית בתהליך האחר קיבלה קלט (או "סוף הקלט") — הצעדים החדשים כבר נטענו */
    static void remoteResume() {
        if (!waiting) return;
        waiting = false;
        notifyViewer();
    }

    // ------------------------------------------------------------ InputStream

    @Override
    public int read() throws IOException {
        byte[] b = new byte[1];
        return read(b, 0, 1) <= 0 ? -1 : b[0] & 0xFF;
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        if (b == null) throw new NullPointerException();
        if (off < 0 || len < 0 || len > b.length - off) throw new IndexOutOfBoundsException();
        if (closed) throw new IOException("Stream closed");   // כמו System.in אחרי in.close()
        if (len == 0) return 0;
        if (!fill()) return -1;
        synchronized (lock) {
            int n = Math.min(len, cur.length - curPos);
            System.arraycopy(cur, curPos, b, off, n);
            curPos += n;
            return n;
        }
    }

    /** רק מה שנשאר מהשורה הנוכחית — שורה חדשה נרשמת (כצעד) רק כשהתוכנית באמת קוראת אותה */
    @Override
    public int available() {
        synchronized (lock) {
            return cur == null ? 0 : cur.length - curPos;
        }
    }

    /** in.close() סוגר גם את System.in — קריאה אחרי זה נכשלת, בדיוק כמו בהרצה רגילה */
    @Override
    public void close() {
        synchronized (lock) {
            closed = true;
            lock.notifyAll();
        }
    }

    /** מוודא שיש בתים בשורה הנוכחית. false = סוף הקלט */
    private boolean fill() throws IOException {
        startReader();
        boolean waited = false;
        try {
            while (true) {
                Line next;
                boolean first = false;
                synchronized (lock) {
                    if (cur != null && curPos < cur.length) return true;
                    next = pending.poll();
                    if (next == null && atEnd()) {
                        first = !eofShown;
                        eofShown = true;
                    }
                }
                if (first) {
                    try {
                        Recorder.inputEnd();   // צעד אחד: "סוף הקלט" (למשל while (in.hasNextInt()) שמסתיים כאן)
                    } catch (RuntimeException e) {
                        // בלי צעד
                    }
                }
                synchronized (lock) {
                    if (next == null && atEnd()) return false;
                    if (next == null && !waited && !realEof) {
                        lock.wait(GRACE_MS);
                        next = pending.poll();
                    }
                }
                if (next != null) {
                    take(next);
                    return true;
                }
                if (!waited) {
                    waited = true;
                    beginWait();
                    continue;
                }
                synchronized (lock) {
                    if (pending.isEmpty() && !atEnd()) lock.wait(1000);
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new InterruptedIOException("הקריאה מהמקלדת הופסקה");
        } finally {
            if (waited) endWait();
        }
    }

    /** (בתוך lock) אין ולא יהיה עוד קלט: לחצו "סיום הקלט", או שאין חלון והקלט האמיתי והקלט של הבדיקה נגמרו */
    private boolean atEnd() {
        return pending.isEmpty() && (ended || !viewer() && auto.isEmpty() && realEof);
    }

    private boolean viewer() {
        return viewer && !Recorder.textMode;   // Viz.textMode() באמצע התוכנית — אין חלון לחכות לו
    }

    /** התוכנית מקבלת את השורה: היא נרשמת (פלט + צעד INPUT), והבתים שלה עוברים לתוכנית */
    private void take(Line l) {
        try {
            Recorder.input(l.text, l.typed);
        } catch (RuntimeException e) {
            // בלי צעד — התוכנית בכל זאת מקבלת את הקלט
        }
        synchronized (lock) {
            cur = l.bytes;
            curPos = 0;
        }
    }

    /** התוכנית מתחילה לחכות: איפה (השורה שקוראת, ומה היא קוראת), צעד אחרון בחלון, והודעה לחלון */
    private void beginWait() {
        StackTraceElement[] trace = new Throwable().getStackTrace();
        int at = -1;
        for (int i = 0; i < trace.length && at < 0; i++) if (!Recorder.isLibrary(trace[i].getClassName())) at = i;
        Frame f = at < 0 ? null : new Frame(trace[at]);
        String method = at > 0 ? trace[at - 1].getMethodName() : "read";
        String call = callText(f, method);
        int step;
        try {
            step = Recorder.inputWait(f, call);
        } catch (RuntimeException e) {
            step = -1;
        }
        waitFrame = f;
        waitCall = call;
        waitWhat = what(method);
        waitStep = step;
        waitId++;
        waiting = true;
        notifyViewer();
        String line;
        synchronized (lock) {
            line = auto.poll();
        }
        if (line != null) typeLater(line);
    }

    private void endWait() {
        waiting = false;
        notifyViewer();
    }

    private static void notifyViewer() {
        Runnable r = listener;
        if (r != null) SwingUtilities.invokeLater(r);
    }

    /** קלט מוכן לבדיקה: "מוקלד" אחרי dsviz.inputDelay מילישניות (או מיד) */
    private void typeLater(final String text) {
        if (autoDelay <= 0) {
            offer(text, true);
            return;
        }
        Thread t = new Thread(new Runnable() {
            public void run() {
                try {
                    Thread.sleep(autoDelay);
                } catch (InterruptedException ignored) {
                    // מקלידים מיד
                }
                offer(text, true);
            }
        }, "dsviz-input-auto");
        t.setDaemon(true);
        t.start();
    }

    private void offer(String text, boolean typed) {
        Line l = new Line((text + System.lineSeparator()).getBytes(charset), text, typed);
        synchronized (lock) {
            pending.add(l);
            lock.notifyAll();
        }
    }

    private void offerBytes(byte[] bytes) {
        String text = new String(bytes, realCharset);
        int e = text.length();
        while (e > 0 && (text.charAt(e - 1) == '\n' || text.charAt(e - 1) == '\r')) e--;
        Line l = new Line(bytes, text.substring(0, e), false);
        synchronized (lock) {
            pending.add(l);
            lock.notifyAll();
        }
    }

    /**
     * הקלט האמיתי (הקונסול / הטרמינל, דרך התהליך האב) — בחוט רקע, שורה-שורה. מתחיל רק בקריאה הראשונה:
     * אם התוכנית קוראת מ-System.in הישן (Scanner שנוצר לפני ההחלפה) — לא "גונבים" ממנה קלט.
     */
    private void startReader() {
        synchronized (lock) {
            if (reader != null) return;
            reader = new Thread(new Runnable() {
                public void run() { pumpReal(); }
            }, "dsviz-stdin");
            reader.setDaemon(true);
            reader.start();
        }
    }

    private void pumpReal() {
        ByteArrayOutputStream line = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        try {
            for (int n; (n = real.read(buf)) >= 0;) {
                for (int i = 0; i < n; i++) {
                    line.write(buf[i]);
                    if (buf[i] == '\n') {
                        offerBytes(line.toByteArray());
                        line.reset();
                    }
                }
            }
        } catch (IOException ignored) {
            // הקלט האמיתי נסגר
        } finally {
            if (line.size() > 0) offerBytes(line.toByteArray());   // שורה אחרונה בלי ירידת שורה
            synchronized (lock) {
                realEof = true;
                lock.notifyAll();
            }
        }
    }

    // ------------------------------------------------------------ מה התוכנית קוראת

    /** "in.nextInt()" — מתוך השורה בקוד (לפי הפעולה שהתלמיד קרא לה), או רק "nextInt()" */
    static String callText(Frame f, String method) {
        String src = f == null ? null : SourceFiles.line(f);
        if (src != null && method.matches("[\\w$]+")) {
            Matcher m = Pattern.compile("([A-Za-z_$][\\w$]*(?:\\s*\\.\\s*[A-Za-z_$][\\w$]*)*)\\s*\\.\\s*" + method + "\\s*\\(\\s*\\)").matcher(src);
            if (m.find()) return m.group(1).replaceAll("\\s+", "") + "." + method + "()";
        }
        return method + "()";
    }

    /** מה התוכנית מחכה שיקלידו — לפי הפעולה (nextInt → מספר שלם) */
    static String what(String method) {
        if (method.matches("nextInt|nextLong|nextShort|nextByte|nextBigInteger")) return "מספר שלם";
        if (method.matches("nextDouble|nextFloat|nextBigDecimal")) return "מספר";
        if (method.matches("nextLine|readLine")) return "שורת טקסט";
        if (method.equals("next")) return "מילה";
        if (method.equals("nextBoolean")) return "true או false";
        if (method.equals("read")) return "תו";
        return "ערך";
    }
}
