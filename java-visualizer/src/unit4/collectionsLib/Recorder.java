package unit4.collectionsLib;

import java.awt.GraphicsEnvironment;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.lang.ref.WeakReference;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import javax.swing.SwingUtilities;

import unit4.collectionsLib.Model.Access;
import unit4.collectionsLib.Model.Cell;
import unit4.collectionsLib.Model.ChainView;
import unit4.collectionsLib.Model.Frame;
import unit4.collectionsLib.Model.Kind;
import unit4.collectionsLib.Model.LinearView;
import unit4.collectionsLib.Model.ArrayView;
import unit4.collectionsLib.Model.MatrixView;
import unit4.collectionsLib.Model.Obj;
import unit4.collectionsLib.Model.ObjView;
import unit4.collectionsLib.Model.Mark;
import unit4.collectionsLib.Model.Step;
import unit4.collectionsLib.Model.TreeCell;
import unit4.collectionsLib.Model.TreeView;
import unit4.collectionsLib.Model.View;

/**
 * הלב של המעטפת: כל פעולה על Stack / Queue / Node / BinNode מדווחת לכאן,
 * ונשמר "צילום" של כל המבנים. החלון (Viewer) מציג את הצילומים צעד אחר צעד.
 */
final class Recorder {
    private Recorder() {}

    private static final Object LOCK = new Object();
    private static final int MAX_CELLS = 300;      // מקסימום תאים לציור ברשימה אחת
    private static final int STALE_STEPS = 15;     // מבנה ריק שלא נגעו בו זמן רב — מוסתר
    private static final int MAX_CALL_FRAMES = 60;

    static int maxSteps = 5000;
    /**
     * האם לתעד בכלל. בהרצה רגילה (Run של Eclipse / VS Code) — לא: התוכנית רצה כמו עם Unit4 הרגיל
     * והפלט רק בקונסול. מופעל ב"הרצה ויזואלית" (-Ddsviz.visual=true, או התהליך שרץ שורה-שורה),
     * או כשהתוכנית קוראת ל-Viz.show() / Viz.textMode().
     */
    static boolean active = Boolean.getBoolean("dsviz.visual") || Boolean.getBoolean(LineByLine.CHILD_PROPERTY);
    /** Viz.off() / Viz.on() */
    static boolean enabled = true;

    /** בלי חלון (למשל נעילה לתשלום): מפסיקים לתעד, וה-Tracer מפסיק לעצור בכל שורה — התוכנית רצה במהירות רגילה */
    static void stopVisual() {   // בלי LOCK: נקרא מחלון ה-Swing, ובזמן הזה התוכנית יכולה להחזיק אותו
        enabled = false;
        linesFull = true;
    }
    static boolean textMode = false;
    /** מצב "שורה אחר שורה": מופעל ע"י LineByLine בתהליך שרץ תחת JdiTracer */
    static boolean lineMode = false;
    // המשתנים של המסגרת הנוכחית (מהשורה האחרונה) — שמות המשתנים מוצגים מעל החוליות/המבנים
    private static Map<Object, List<String>> frameVars = new IdentityHashMap<Object, List<String>>();
    private static List<String[]> currentVars;

    private static int suspended = 0;
    private static boolean started, limitReached;

    private static final List<Object> linears = new ArrayList<Object>();
    // חוליות וצמתים נשמרים בהפניה "חלשה": חוליה שאף משתנה כבר לא מגיע אליה נאספת ע"י ה-GC
    // ונעלמת מהתצוגה — בדיוק כמו בזיכרון האמיתי. (Node/BinNode/Stack/Queue לא דורסים את equals,
    // ולכן WeakHashMap עובד כאן לפי זהות האובייקט)
    private static final List<WeakReference<Node<?>>> nodes = new ArrayList<WeakReference<Node<?>>>();
    private static final List<WeakReference<BinNode<?>>> bins = new ArrayList<WeakReference<BinNode<?>>>();
    private static final Map<Object, Integer> order = new WeakHashMap<Object, Integer>();
    private static final Map<Object, String> names = new WeakHashMap<Object, String>();
    private static final Map<Object, Integer> nameRank = new WeakHashMap<Object, Integer>();
    private static final Map<Object, Integer> lastTouch = new WeakHashMap<Object, Integer>();
    private static int orderCounter, gcCount;
    private static final int MAX_GC = 400;          // אחרי כך מפסיקים לאסוף (כדי לא להאט ריצות ארוכות)
    private static final Map<String, Object> pointers = new LinkedHashMap<String, Object>();
    private static final Map<String, Integer> nameCounts = new HashMap<String, Integer>();
    private static int stackCount, queueCount;
    private static WeakReference<Object> lastNode = new WeakReference<Object>(null);
    private static WeakReference<Object> lastBin = new WeakReference<Object>(null);

    private static final List<Step> steps = new ArrayList<Step>();
    private static final StringBuilder output = new StringBuilder();
    private static PrintStream out = System.out;
    private static PrintStream err = System.err;

    // ------------------------------------------------------------ גישה מהחלון

    static int stepCount() {
        sync();   // החלון: שורות שממתינות (למשל בלולאה בלי פעולות על מבנים)
        synchronized (LOCK) { return steps.size(); }
    }

    static Step step(int i) {
        synchronized (LOCK) { return steps.get(i); }
    }

    /** מקור חיצוני (למשל הרצה של C# — TraceViewer): טוענים צעדים מוכנים ופלט, והחלון מציג אותם כמו הרצה שורה-שורה */
    static void adopt(List<Step> loaded, String out) {
        synchronized (LOCK) {
            steps.clear();
            steps.addAll(loaded);
            output.setLength(0);
            output.append(out);
            active = true;
            started = true;
            lineMode = true;
            lineTexts = new String[LINE_CAPACITY];
        }
    }

    static String outputUpTo(int len) {
        synchronized (LOCK) { return output.substring(0, Math.min(len, output.length())); }
    }

    // ------------------------------------------------------------ אתחול

    /** מתעורר כשחלון ההרצה גלוי */
    static final java.util.concurrent.CountDownLatch viewerReady = new java.util.concurrent.CountDownLatch(1);

    /**
     * הרצה שורה-שורה: פותחים את החלון ומחכים שיהיה מוכן לפני שהתוכנית של התלמיד מתחילה.
     * (בזמן שה-debugger צועד על השורות, חוט החלון כמעט לא מקבל זמן ריצה — והחלון היה עולה אחרי כמה שניות)
     */
    static void preload() {
        if (!lineMode || !active) return;
        start();
        if (textMode) return;
        try {
            viewerReady.await(20, java.util.concurrent.TimeUnit.SECONDS);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

    private static void start() {
        if (started) return;
        started = true;
        Timing.mark("Recorder.start (צעד ראשון)");
        out = System.out;
        err = System.err;
        if (GraphicsEnvironment.isHeadless()) textMode = true;
        installOutputTee();
        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            public void uncaughtException(Thread t, Throwable e) {
                failed(e);
                e.printStackTrace(err);
            }
        });
        final Thread main = Thread.currentThread();
        if ("main".equals(main.getName())) {
            Thread watcher = new Thread(new Runnable() {
                public void run() {
                    try { main.join(); } catch (InterruptedException ignored) {}
                    if (failure == null && !loopStopped)   // אחרי שגיאה — היא הצעד האחרון (עם ההסבר), בלי "התוכנית הסתיימה" אחריה
                        recordAt(Kind.END, null, Mark.NONE, null, "התוכנית הסתיימה", new StackTraceElement[0]);
                }
            }, "viz-watcher");
            watcher.setDaemon(true);
            watcher.start();
        }
        if (!textMode) {
            SwingUtilities.invokeLater(new Runnable() {
                public void run() {
                    try { Viewer.open(); } finally { viewerReady.countDown(); }   // גם אם נכשל — לא נתקעים
                }
            });
        }
    }

    private static Throwable failure;

    /**
     * התוכנית נעצרה בשגיאה (גם כשהשגיאה נתפסת ב-LineByLine ולא מגיעה ל-uncaught): צעד אחרון עם השגיאה,
     * ועליו כרטיס שמסביר מה קרה (Problems) — ואם p הוא null אחרי סוף רשימה, p מסומן על ה-null שבסוף השרשרת.
     */
    static void failed(Throwable e) {
        synchronized (LOCK) {
            if (e == null || failure == e) return;
            failure = e;
            int before = steps.size();
            recordAt(Kind.ERROR, null, Mark.NONE, null, "שגיאה: " + describe(e), e.getStackTrace());
            if (steps.size() == before) return;
            Step st = steps.get(steps.size() - 1);
            try {
                Model.ProblemView p = Problems.attach(st);
                if (textMode && p != null) out.println("      [כרטיס " + p.kind + (p.name != null ? " " + p.name : "") + (p.op != null ? " " + p.op : "") + "]");
                Object ln = lastNode.get();
                if (p != null && "null".equals(p.kind) && p.name != null && !p.name.endsWith(")") && ln instanceof Node && ((Node<?>) ln).next == null)
                    for (View v : st.views)
                        if (v instanceof ChainView && chainHas((ChainView) v, ln)) ((ChainView) v).nullLabels.add(p.name);
            } catch (RuntimeException ignored) {
                // בלי כרטיס — השגיאה עצמה כתובה בצעד
            }
        }
    }

    private static boolean chainHas(ChainView c, Object node) {
        if (!(c.src instanceof Node)) return false;
        int k = 0;
        for (Node<?> n = (Node<?>) c.src; n != null && k < MAX_CELLS; n = n.next, k++) if (n == node) return true;
        return false;
    }

    private static String describe(Throwable e) {
        String s = e.getClass().getSimpleName() + (e.getMessage() == null ? "" : ": " + e.getMessage());
        if (e instanceof NullPointerException) s += "   (ניסיון להשתמש באובייקט שהוא null)";
        if (e instanceof StackOverflowError) s += "   (רקורסיה בלי תנאי עצירה?)";
        return s;
    }

    /** כל מה שמודפס ל-System.out ממשיך לקונסול, וגם נשמר כצעד בהרצה */
    private static void installOutputTee() {
        final PrintStream original = out;
        String enc = consoleEncoding(original);
        final Charset cs = Charset.forName(enc);
        OutputStream tee = new OutputStream() {
            public void write(int b) {
                original.write(b);
                onOutput(new String(new byte[] { (byte) b }, cs));
            }
            public void write(byte[] b, int off, int len) {
                original.write(b, off, len);
                onOutput(new String(b, off, len, cs));
            }
            public void flush() { original.flush(); }
        };
        try {
            System.setOut(new PrintStream(tee, true, enc));
        } catch (UnsupportedEncodingException e) {
            System.setOut(new PrintStream(tee, true));
        }
    }

    private static String consoleEncoding(PrintStream ps) {
        try { // Java 18+
            Object c = PrintStream.class.getMethod("charset").invoke(ps);
            return ((Charset) c).name();
        } catch (Exception ignored) {}
        String p = System.getProperty("sun.stdout.encoding");
        if (p != null && Charset.isSupported(p)) return p;
        return Charset.defaultCharset().name();
    }

    private static void onOutput(String text) {
        synchronized (LOCK) {
            if (lineMode) drainLines();   // השורות שלפני ההדפסה — בלי הטקסט החדש
            output.append(text);
            String t = text.trim();
            if (t.length() > 0) {
                record(Kind.OUTPUT, null, Mark.NONE, null, "הדפסה: " + shorten(t, 60));
            }
        }
    }

    // ------------------------------------------------------------ קלט מהמקלדת (Keyboard)

    /**
     * התוכנית מנסה לקרוא קלט ואין: השורות שממתינות הופכות לצעדים — כך השורה שקוראת היא הצעד האחרון בחלון.
     * תוכנית שעוד לא עשתה כלום שמתועד — החלון נפתח עכשיו, כדי שיהיה איפה להקליד. בלי חלון (בדיקות) — מודפס איפה מחכים.
     * מחזיר את האינדקס של הצעד האחרון (או -1).
     */
    static int inputWait(Frame f, String call) {
        synchronized (LOCK) {
            if (lineMode) drainLines();
            if (active && enabled) start();
            if (textMode) out.println("  \u2328 מחכה לקלט מהמקלדת" + (f == null ? "" : " — שורה " + f.line) + (call == null ? "" : ":  " + call));
            return steps.size() - 1;
        }
    }

    /**
     * התוכנית קיבלה שורת קלט: מופיעה בפלט (בחלון — בצבע של קלט) ונרשמת כצעד INPUT בשורה שקראה אותה.
     * echo = הוקלדה בחלון (או בבדיקה) — מודפסת גם לקונסול, כדי שיופיע שם "הכנס מספר: 5" כמו בהרצה רגילה
     * (מה שהוקלד בקונסול עצמו כבר מופיע שם).
     */
    static void input(String typed, boolean echo) {
        synchronized (LOCK) {
            if (lineMode) drainLines();   // השורות שלפני הקריאה — בלי הקלט החדש
            if (echo) {
                out.println(typed);
                out.flush();
            }
            output.append(typed).append('\n');   // הצעד (outLen) כבר כולל את מה שהוקלד
            int before = steps.size();
            recordAt(Kind.INPUT, null, Mark.NONE, null, "קלט מהמקלדת: \u200E" + shorten(typed, 60) + "\u200E", new Throwable().getStackTrace());
            if (steps.size() > before) steps.get(steps.size() - 1).input = typed;
        }
    }

    /** התוכנית ניסתה לקרוא ואין עוד קלט ("סיום הקלט", או בלי חלון — הקלט נגמר): צעד INPUT בלי input (null) */
    static void inputEnd() {
        synchronized (LOCK) {
            if (lineMode) drainLines();
            recordAt(Kind.INPUT, null, Mark.NONE, null, "סוף הקלט — אין עוד מה לקרוא", new Throwable().getStackTrace());
        }
    }

    // ------------------------------------------------------------ רישום מבנים

    static void register(Object o) {
        synchronized (LOCK) {
            if (!enabled || !active) return;
            if (o instanceof Student || o instanceof Car) {   // אובייקטים של מחלקות: רק זהות (בשביל הכרטיסים והקישור ל-Access)
                int id = ++idCounter;
                byId.put(id, new WeakReference<Object>(o));
                if (o instanceof Student) ((Student) o).vizId = id;
                else ((Car) o).vizId = id;
                return;
            }
            start();
            order.put(o, orderCounter++);
            if (lineMode) {
                int id = ++idCounter;
                byId.put(id, new WeakReference<Object>(o));
                if (o instanceof Stack) ((Stack<?>) o).vizId = id;
                else if (o instanceof Queue) ((Queue<?>) o).vizId = id;
                else if (o instanceof Node) ((Node<?>) o).vizId = id;
                else if (o instanceof BinNode) ((BinNode<?>) o).vizId = id;
            }
            lastTouch.put(o, steps.size());
            if (o instanceof Stack || o instanceof Queue) linears.add(o);
            else if (o instanceof Node) nodes.add(new WeakReference<Node<?>>((Node<?>) o));
            else if (o instanceof BinNode) bins.add(new WeakReference<BinNode<?>>((BinNode<?>) o));

            String guess = null;
            if (suspended == 0) {
                String type = o.getClass().getSimpleName();
                guess = SourceFiles.guessNewName(firstStudentFrame(new Throwable().getStackTrace()), type,
                        !(o instanceof Stack || o instanceof Queue));
            }
            if (guess != null) setName(o, guess, 1);
            else if (o instanceof Stack) setName(o, "stack" + (++stackCount), 0);
            else if (o instanceof Queue) setName(o, "queue" + (++queueCount), 0);
        }
    }

    // ------------------------------------------------------------ מערכים

    private static final List<WeakReference<Object>> arrays = new ArrayList<WeakReference<Object>>();
    private static final Map<Object, List<String>> lastArray = new WeakHashMap<Object, List<String>>();
    private static int arrayCount;
    static final int MAX_ARRAY = 60, MAX_MATRIX = 20;

    /** מערך (חד או דו ממדי) שמוצג בכל צעד: נוצר ב-Build.array / Build.matrix, או נרשם ב-Viz.watch */
    static void watchArray(Object arr, String name) {
        synchronized (LOCK) {
            if (!enabled || !active || arr == null || !arr.getClass().isArray()) return;
            start();
            boolean known = false;
            for (Object a : alive(arrays)) if (a == arr) known = true;
            if (!known) arrays.add(new WeakReference<Object>(arr));
            names.put(arr, name != null ? name : "arr" + (++arrayCount));
            nameRank.put(arr, name != null ? 2 : 0);
        }
    }

    private static boolean isMatrix(Object arr) {
        return arr.getClass().getComponentType().isArray();
    }

    private static View arrayView(Object arr) {
        List<String> flat = new ArrayList<String>();
        View view;
        if (isMatrix(arr)) {
            MatrixView m = new MatrixView();
            boolean anyObj = false;
            int rows = Math.min(java.lang.reflect.Array.getLength(arr), MAX_MATRIX);
            for (int r = 0; r < rows; r++) {
                Object row = java.lang.reflect.Array.get(arr, r);
                List<String> cells = new ArrayList<String>();
                List<Obj> objRow = new ArrayList<Obj>();
                int cols = row == null ? 0 : Math.min(java.lang.reflect.Array.getLength(row), MAX_MATRIX);
                for (int c = 0; c < cols; c++) {
                    Object item = java.lang.reflect.Array.get(row, c);
                    cells.add(str(item));
                    Obj ob = objOf(item);
                    objRow.add(ob);
                    anyObj |= ob != null;
                }
                m.rows.add(cells);
                m.objs.add(objRow);
                for (int c = 0; c < cells.size(); c++) flat.add(r + "," + c + "=" + cells.get(c));
            }
            if (!anyObj) m.objs.clear();
            List<String> old = lastArray.get(arr);
            if (old != null) for (String f : flat) if (!old.contains(f)) m.changed.add(f.substring(0, f.indexOf('=')));
            view = m;
        } else {
            ArrayView a = new ArrayView();
            int n = java.lang.reflect.Array.getLength(arr);
            boolean anyObj = false;
            for (int i = 0; i < Math.min(n, MAX_ARRAY); i++) {
                Object item = java.lang.reflect.Array.get(arr, i);
                a.items.add(str(item));
                Obj ob = objOf(item);
                a.objs.add(ob);
                anyObj |= ob != null;
                a.subs.add(subOf(item, null, Mark.NONE, null));
                a.subSrc.add(item);
            }
            if (!anyObj) a.objs.clear();
            dropEmptySubs(a);
            a.truncated = n > MAX_ARRAY;
            List<String> old = lastArray.get(arr);
            if (old != null) for (int i = 0; i < a.items.size(); i++) if (i >= old.size() || !old.get(i).equals(a.items.get(i))) a.changed.add(i);
            flat.addAll(a.items);
            view = a;
        }
        lastArray.put(arr, flat);
        view.title = names.get(arr);
        return view;
    }

    // מצב שורה-שורה: המערכים של המסגרות הנוכחיות כפי שה-Tracer קרא אותם בשורה האחרונה
    private static List<View> lineArrays = new ArrayList<View>();
    // מצב שורה-שורה: משתני המחרוזת של השורה האחרונה, עם מה שהשורה עושה בהם (StringViz)
    private static List<View> lineStrings = new ArrayList<View>();
    // מצב שורה-שורה: הלולאות שהשורה האחרונה בתוכן (LoopViz)
    private static List<View> lineLoops = new ArrayList<View>();
    private static Map<String, List<String>> lastLineArrays = new HashMap<String, List<String>>();
    /**
     * section = מערכים מופרדים ב-\u0001: שמות \u0002 ממדים \u0002 נתונים (שורות ב-\u0007, איברים ב-\u0006).
     * תווית אינדקס (i, j...) מוצגת מעל תא רק אם בקוד באמת כתוב arr[i... (או mat[r][c...) — כדי שלא יופיע i על כל מערך.
     */
    private static List<View> parseLineArrays(String section, List<String[]> vars, Frame top) {
        Map<String, Integer> indexVars = new LinkedHashMap<String, Integer>();
        for (String[] v : vars) {
            try { indexVars.put(v[0], Integer.parseInt(v[1])); } catch (NumberFormatException ignored) {}
        }
        String code = top == null ? null : SourceFiles.text(top);
        List<View> res = new ArrayList<View>();
        Map<String, List<String>> now = new HashMap<String, List<String>>();
        for (String a : split(section)) {
            String[] p = a.split("\u0002", -1);
            if (p.length < 3) continue;
            List<String> flat = new ArrayList<String>();
            List<String> old = lastLineArrays.get(p[0]);
            if (p[1].equals("2")) {
                MatrixView m = new MatrixView();
                String[] rows = p[2].isEmpty() ? new String[0] : p[2].split("\u0007", -1);
                boolean anyObj = false;
                for (int r = 0; r < rows.length; r++) {
                    List<String> cells = new ArrayList<String>();
                    List<Obj> objRow = new ArrayList<Obj>();
                    if (!rows[r].isEmpty()) {
                        for (String cell : rows[r].split("\u0006", -1)) {
                            Obj ob = objFromCell(cell);
                            objRow.add(ob);
                            cells.add(cellText(cell, ob, cell.startsWith("\u0003") ? byId(cell.substring(1)) : null));
                            anyObj |= ob != null;
                        }
                    }
                    m.rows.add(cells);
                    m.objs.add(objRow);
                    for (int c = 0; c < cells.size(); c++) {
                        String f = r + "," + c + "=" + cells.get(c);
                        flat.add(f);
                        if (old != null && !old.contains(f)) m.changed.add(r + "," + c);
                    }
                }
                if (!anyObj) m.objs.clear();
                for (Map.Entry<String, Integer> e : indexVars.entrySet()) {
                    int idx = e.getValue();
                    for (String arr : localNames(p[0])) {
                        if (usedAs(code, arr, "\\[\\s*", e.getKey()) && idx >= 0 && idx < m.rows.size())
                            addLabel(m.rowLabels, idx, e.getKey());
                        if (usedAs(code, arr, "\\[[^\\]]*\\]\\s*\\[\\s*", e.getKey()) && idx >= 0 && !m.rows.isEmpty()
                                && idx < m.rows.get(0).size())
                            addLabel(m.colLabels, idx, e.getKey());
                    }
                }
                m.title = p[0];
                res.add(m);
            } else {
                ArrayView v = new ArrayView();
                if (!p[2].isEmpty()) {
                    boolean anyObj = false;
                    for (String cell : p[2].split("\u0006", -1)) {
                        Obj ob = objFromCell(cell);
                        Object src = cell.startsWith("\u0003") ? byId(cell.substring(1)) : null;
                        v.objs.add(ob);
                        v.items.add(cellText(cell, ob, src));
                        v.subs.add(ob == null ? subOf(src, null, Mark.NONE, null) : null);
                        v.subSrc.add(src);
                        anyObj |= ob != null;
                    }
                    if (!anyObj) v.objs.clear();
                    dropEmptySubs(v);
                }
                v.truncated = p.length > 3 && p[3].equals("+");
                for (int i = 0; i < v.items.size(); i++) if (old != null && (i >= old.size() || !old.get(i).equals(v.items.get(i)))) v.changed.add(i);
                for (Map.Entry<String, Integer> e : indexVars.entrySet())
                    for (String arr : localNames(p[0]))
                        if (e.getValue() >= 0 && e.getValue() < v.items.size() && usedAs(code, arr, "\\[\\s*", e.getKey()))
                            addLabel(v.indexLabels, e.getValue(), e.getKey());
                flat.addAll(v.items);
                v.title = p[0];
                res.add(v);
            }
            now.put(p[0], flat);
        }
        lastLineArrays = now;
        return res;
    }

    private static void addLabel(Map<Integer, List<String>> m, int idx, String name) {
        List<String> l = m.get(idx);
        if (l == null) m.put(idx, l = new ArrayList<String>());
        if (!l.contains(name)) l.add(name);
    }

    /** "a, arr (main)" → [a] — רק השמות מהפעולה הנוכחית (בהן משתני האינדקס מוגדרים) */
    private static List<String> localNames(String title) {
        List<String> res = new ArrayList<String>();
        for (String n : title.split(",\\s*")) if (!n.contains("(")) res.add(n.trim());
        return res;
    }

    /** האם בקוד מופיע arr[ ... index (לפי התבנית שבאמצע) */
    private static boolean usedAs(String code, String arr, String middle, String index) {
        if (code == null) return false;
        return java.util.regex.Pattern.compile("\\b" + java.util.regex.Pattern.quote(arr) + middle
                + java.util.regex.Pattern.quote(index) + "\\b").matcher(code).find();
    }

    /** rank: 0 = שם אוטומטי, 1 = ניחוש מהקוד, 2 = שם מפורש */
    /** עותק מ-Helpers.duplicate: השם של המשתנה שמקבל אותו ("copy = Helpers.duplicate(s)"), אם יש */
    static void nameCopy(Object o) {
        synchronized (LOCK) {
            if (!enabled || !active || o == null) return;
            String guess = SourceFiles.guessCopyName(firstStudentFrame(new Throwable().getStackTrace()));
            if (guess != null) setName(o, guess, 1);
        }
    }

    static void setName(Object o, String name, int rank) {
        synchronized (LOCK) {
            if (name == null || o == null) return;
            boolean linear = o instanceof Stack || o instanceof Queue;
            if (linear) {
                String old = names.get(o);
                if (name.equals(old)) return;
                Integer n = nameCounts.get(name);
                nameCounts.put(name, n == null ? 1 : n + 1);
                if (n != null) name = name + " (" + (n + 1) + ")";
            }
            names.put(o, name);
            nameRank.put(o, rank);
        }
    }

    static String nameOf(Object o) {
        synchronized (LOCK) {
            String n = names.get(o);
            return n == null ? "?" : n;
        }
    }

    static void nameFromCaller(Object o) {
        synchronized (LOCK) {
            String g = SourceFiles.guessAssignName(firstStudentFrame(new Throwable().getStackTrace()));
            if (g != null) setName(o, g, 2);
        }
    }

    static void point(String label, Object node) {
        synchronized (LOCK) {
            if (!active) return;
            if (node == null) pointers.remove(label);
            else pointers.put(label, node);
        }
    }

    static void suspend() { synchronized (LOCK) { suspended++; } }
    static void resume()  { synchronized (LOCK) { suspended--; } }

    // ------------------------------------------------------------ גישה למאפיינים של אובייקט (Student, Car)

    private static Object accOwner;
    private static String accField, accHow;
    private static Mark accMark;

    /** מחרוזת עם מרכאות, לתצוגה בקוד: "נועה" */
    static String lit(String s) {
        return s == null ? "null" : "\"" + shorten(s, 24) + "\"";
    }

    /** getter: קריאה של המאפיין field של owner — מתועדת, והכרטיס של owner מוצג עם המאפיין מודגש. מחזיר את הערך */
    static <T> T got(Object owner, String field, String how, T value) {
        access(owner, field, how, Mark.READ, "  →  " + (value instanceof String ? lit((String) value) : value instanceof int[] ? java.util.Arrays.toString((int[]) value) : String.valueOf(value)));
        return value;
    }

    /** setter / פעולה ששינתה את המאפיין field של owner (אחרי השינוי) */
    static void changed(Object owner, String field, String how) {
        access(owner, field, how, Mark.CHANGED, "");
    }

    private static void access(Object owner, String field, String how, Mark mark, String result) {
        if (!active || !enabled) return;
        sync();
        synchronized (LOCK) {
            if (suspended > 0) return;
            accOwner = owner;
            accField = field;
            accHow = how;
            accMark = mark;
            try {
                record(mark == Mark.CHANGED ? Kind.CHANGE : Kind.READ, owner, mark, null, str(owner) + "." + how + result);
            } finally {
                accOwner = null;
            }
        }
    }

    // ------------------------------------------------------------ אובייקטים של מחלקות: כרטיס + טבלת מאפיינים

    /** האובייקטים שנוצרו בצילום הנוכחי — כדי לחבר אליהם את שמות המשתנים */
    private static final List<Obj> snapObjs = new ArrayList<Obj>();

    private static boolean isRecordLike(Class<?> c) {
        String n = c.getName();
        return !c.isArray() && !c.isEnum() && !c.isPrimitive() && !n.startsWith("java.") && !n.startsWith("javax.") && !n.startsWith("jdk.")
                && !n.startsWith("sun.") && !n.startsWith("com.sun.") && !n.startsWith("unit4.collectionsLib.");
    }

    /** כרטיס לאובייקט: Student / Car (מוכנים), או מחלקה של התלמידים (לפי המאפיינים שלה). null — ערך רגיל */
    static Obj objOf(Object v) {
        if (v == null) return null;
        Obj o = null;
        if (v instanceof Student) o = ((Student) v).vizObj();
        else if (v instanceof Car) o = ((Car) v).vizObj();
        else if (isRecordLike(v.getClass())) o = reflectObj(v);
        if (o != null) snapObjs.add(o);
        return o;
    }

    private static Obj reflectObj(Object v) {
        Obj o = new Obj();
        o.id = System.identityHashCode(v);
        o.type = v.getClass().getSimpleName();
        List<Class<?>> chain = new ArrayList<Class<?>>();
        for (Class<?> c = v.getClass(); c != null && c != Object.class; c = c.getSuperclass()) chain.add(0, c);
        for (Class<?> c : chain) {
            for (java.lang.reflect.Field f : c.getDeclaredFields()) {
                if (java.lang.reflect.Modifier.isStatic(f.getModifiers()) || f.isSynthetic() || o.fields.size() >= 12) continue;
                String val;
                Map<String, String> snap = lineSnap.get(v);
                if (snap != null && snap.containsKey(f.getName())) {
                    val = snap.get(f.getName());   // הערך ברגע של השורה (ראו lineSnap)
                } else {
                    try {
                        f.setAccessible(true);
                        val = fieldText(f.get(v));
                    } catch (Exception e) {
                        val = "?";
                    }
                }
                o.fields.add(new String[] { f.getName(), val });
            }
        }
        describe(o);
        return o;
    }

    /** ערך של מאפיין בטבלה: קצר, בסגנון Java */
    private static String fieldText(Object x) {
        if (x == null) return "null";
        if (x instanceof String) return lit((String) x);
        if (x instanceof Character) return "'" + x + "'";
        if (x instanceof Number || x instanceof Boolean) return String.valueOf(x);
        if (x.getClass().isArray()) {
            StringBuilder b = new StringBuilder("[");
            int n = java.lang.reflect.Array.getLength(x);
            for (int i = 0; i < Math.min(n, 6); i++) b.append(i == 0 ? "" : ", ").append(fieldText(java.lang.reflect.Array.get(x, i)));
            return b.append(n > 6 ? ", …]" : "]").toString();
        }
        if (x instanceof Student || x instanceof Car) return str(x);
        if (x instanceof Stack || x instanceof Queue || x instanceof Node || x instanceof BinNode) return formatVar(x);
        if (x.getClass().isEnum()) return String.valueOf(x);
        if (isRecordLike(x.getClass()) && !overridesToString(x.getClass())) {   // הפניה לאובייקט אחר של התלמיד: → Bob
            if (titleDepth > 0) return "→ " + x.getClass().getSimpleName();   // בלי להיכנס לעומק (אובייקטים שמצביעים זה על זה)
            titleDepth++;
            try {
                return "→ " + shorten(userTitle(x), 14);
            } finally {
                titleDepth--;
            }
        }
        try {
            return shorten(String.valueOf(x), 24);
        } catch (RuntimeException e) {
            return x.getClass().getSimpleName();
        }
    }

    /** שורות הכרטיס של מחלקה כללית: כותרת = שדה טקסט בשם name/title (או הראשון), ושורה שנייה = עוד שני מאפיינים */
    static void describe(Obj o) {
        String[] main = null;
        for (String[] f : o.fields) {
            String n = f[0].toLowerCase();
            if (f[1].startsWith("\"") && (n.equals("name") || n.equals("title") || n.endsWith("name"))) { main = f; break; }
        }
        if (main == null) for (String[] f : o.fields) if (f[1].startsWith("\"")) { main = f; break; }
        StringBuilder sub = new StringBuilder();
        int k = 0;
        for (String[] f : o.fields) {
            if (f == main || k >= 2) continue;
            sub.append(k++ == 0 ? "" : " · ").append(f[0]).append("=").append(unquote(f[1]));
        }
        o.title = main != null ? shorten(unquote(main[1]), 16) : o.type;
        o.sub = shorten(sub.toString(), 22);
    }

    private static String unquote(String s) {
        return s.length() >= 2 && s.startsWith("\"") && s.endsWith("\"") ? s.substring(1, s.length() - 1) : s;
    }

    /** מערך שה-Tracer קרא: תא-אובייקט שמגיע כ-\u0003id (Student / Car) או כתיאור (\u000E סוג \u000F מאפיין \u0010 ערך ...) */
    private static Obj objFromCell(String cell) {
        if (cell.startsWith("\u0003")) {
            Object o = byId(cell.substring(1));
            return o == null ? null : objOf(o);
        }
        if (cell.startsWith("\u000E")) {
            String[] p = cell.substring(1).split("\u000F", -1);
            Obj o = new Obj();
            o.id = -1;
            o.type = p[0];
            for (int i = 1; i < p.length; i++) {
                int k = p[i].indexOf('\u0010');
                if (k > 0) o.fields.add(new String[] { p[i].substring(0, k), p[i].substring(k + 1) });
            }
            describe(o);
            snapObjs.add(o);
            return o;
        }
        return null;
    }

    // ------------------------------------------------------------ תיעוד צעדים

    static void create(Object target, String text)  { record(Kind.CREATE, target, Mark.NEW, null, text); }
    static void change(Object target, Mark mark, String ghost, String text) { record(Kind.CHANGE, target, mark, ghost, text); }
    static void read(Object target, String text)    { record(Kind.READ, target, Mark.READ, null, text); }
    static void check(Object target, String text)   { record(Kind.CHECK, target, Mark.READ, null, text); }

    static void record(Kind kind, Object target, Mark mark, String ghost, String text) {
        recordAt(kind, target, mark, ghost, text, new Throwable().getStackTrace());
    }

    static void recordAt(Kind kind, Object target, Mark mark, String ghost, String text, StackTraceElement[] trace) {
        if (kind == Kind.CHANGE || kind == Kind.CREATE) mutations++;   // לולאה שלא נגמרת: ה-Tracer בודק אם משהו במבנים השתנה (LoopGuard)
        synchronized (LOCK) {
            if (loopStopped) return;   // הלולאה שלא נגמרת נעצרה — הצעד שלה (עם הכרטיס) הוא האחרון
            if (lineMode) drainLines();   // קודם השורות שהגיעו לפני הצעד הזה
            if (!enabled || !active || suspended > 0) return;
            start();
            boolean always = kind == Kind.END || kind == Kind.ERROR; // שגיאה וסיום מוצגים תמיד
            if (steps.size() >= maxSteps) {
                if (!limitReached && !always) {
                    limitReached = true;
                    // שורה-שורה: התוכנית ממשיכה, ולולאה שלא נגמרת נעצרת עם כרטיס משלה (LoopGuard) — כאן רק הערה
                    addStep(lineMode ? Kind.NOTE : Kind.ERROR, null, Mark.NONE, null, lineMode
                            ? "התיעוד נעצר אחרי " + maxSteps + " צעדים — מכאן התוכנית ממשיכה לרוץ בלי לצייר (אפשר להגדיל: Viz.maxSteps)"
                            : "התיעוד נעצר אחרי " + maxSteps + " צעדים — אולי יש לולאה אינסופית? (אפשר להגדיל: Viz.maxSteps)",
                            trace);
                }
                if (!always) return;
            }
            if (target != null) {
                lastTouch.put(target, steps.size());
                if (target instanceof Node) lastNode = new WeakReference<Object>(target);
                if (target instanceof BinNode) lastBin = new WeakReference<Object>(target);
                // שינוי מצביע — אולי חוליה התנתקה. נותנים ל-GC לאסוף אותה לפני הצילום
                if (kind == Kind.CHANGE && (target instanceof Node || target instanceof BinNode) && gcCount < MAX_GC) {
                    holdUnreachable();   // חוליה שנותקה עכשיו — נשמרת, כדי להראות שהיא אבדה
                    gcCount++;
                    System.gc();
                }
            }
            addStep(kind, target, mark, ghost, text, trace);
        }
    }

    private static void addStep(Kind kind, Object target, Mark mark, String ghost, String text, StackTraceElement[] trace) {
        addStep(kind, target, mark, ghost, text, studentFrames(trace));
    }

    private static void addStep(Kind kind, Object target, Mark mark, String ghost, String text, List<Frame> frames) {
        Step s = new Step();
        s.index = steps.size();
        s.kind = kind;
        s.text = text;
        s.calls = frames;
        s.where = s.calls.isEmpty() ? null : s.calls.get(0);
        kindNow = kind;
        suspended++;
        try {
            s.views = snapshot(target, mark, ghost);
            if (accOwner != null) {
                Access a = new Access();
                a.obj = objOf(accOwner);
                a.field = accField;
                a.text = accHow;
                a.mark = accMark;
                s.access = a.obj == null ? null : a;
            }
        } catch (RuntimeException e) {
            s.views = new ArrayList<View>();
        } finally {
            suspended--;
        }
        s.outLen = output.length();
        s.vars = currentVars;
        steps.add(s);
        if (textMode) printStep(s);
    }

    static boolean isLibrary(String cls) {
        return cls.startsWith("unit4.collectionsLib.") || cls.startsWith("java.") || cls.startsWith("javax.")
                || cls.startsWith("sun.") || cls.startsWith("jdk.") || cls.startsWith("com.sun.");
    }

    private static List<Frame> studentFrames(StackTraceElement[] trace) {
        List<Frame> res = new ArrayList<Frame>();
        for (StackTraceElement e : trace) {
            if (isLibrary(e.getClassName())) continue;
            res.add(new Frame(e));
            if (res.size() >= MAX_CALL_FRAMES) break;
        }
        return res;
    }

    static Frame callerFrame() {
        return firstStudentFrame(new Throwable().getStackTrace());
    }

    private static Frame firstStudentFrame(StackTraceElement[] trace) {
        for (StackTraceElement e : trace) if (!isLibrary(e.getClassName())) return new Frame(e);
        return null;
    }

    // ------------------------------------------------------------ חוליות שאבדו (רשימה מקושרת)

    /*
     * חוליה "אבדה" כשאף משתנה (בפעולה הנוכחית, בפעולות שקראו לה, במשתנים סטטיים, או דרך מבנה / אובייקט שהם מצביעים עליו)
     * כבר לא מגיע אליה — למשל lst = lst.getNext() שהזיז את ראש הרשימה. רק חוליה שהיה אליה מצביע קודם (חוליה חדשה
     * שעוד לא נשמרה במשתנה — לא), ורק אם זה נשאר כך בשני גבולות שורה רצופים (באמצע שורה ערכים מחכים בצד). חוליות שאבדו
     * נשמרות (כדי שה-GC לא ימחק אותן לפני שרואים) ומוצגות באדום LOST_STEPS צעדים — ואז נעלמות, כמו בזיכרון.
     */
    private static Kind kindNow;
    private static List<Object> callerRoots = new ArrayList<Object>();
    private static List<Frame> lineFrames = new ArrayList<Frame>();
    private static final Set<Object> everHeld = java.util.Collections.newSetFromMap(new WeakHashMap<Object, Boolean>());
    private static final Map<Object, Integer> lostAt = new IdentityHashMap<Object, Integer>();
    private static final Map<Object, List<String>> lastNames = new WeakHashMap<Object, List<String>>();
    private static final Set<Object> pendingLost = identitySet();
    private static final Map<String, List<java.lang.reflect.Field>> staticFields = new HashMap<String, List<java.lang.reflect.Field>>();
    private static final Map<Class<?>, List<java.lang.reflect.Field>> objFields = new HashMap<Class<?>, List<java.lang.reflect.Field>>();
    static final int LOST_STEPS = 12;

    /** לפני GC (חוליה נותקה באמצע שורה): מה שכבר אין אליו מצביע — נשמר עד סוף השורה */
    private static void holdUnreachable() {
        if (!lineMode) return;
        try {
            Set<Object> reach = reachable();
            for (Node<?> n : alive(nodes)) if (everHeld.contains(n) && !reach.contains(n)) pendingLost.add(n);
        } catch (RuntimeException ignored) {
            // בלי סימון — רק ההצגה הרגילה
        }
    }

    private static void updateLost(boolean atLine) {
        Set<Object> reach;
        try {
            reach = reachable();
        } catch (RuntimeException e) {
            return;
        }
        for (Node<?> n : alive(nodes)) {
            if (reach.contains(n)) {
                everHeld.add(n);
                lostAt.remove(n);   // הגיעו אליה שוב (למשל ערך שחיכה בצד) — לא אבדה
                pendingLost.remove(n);
            } else if (atLine && everHeld.contains(n) && !lostAt.containsKey(n)) {
                // רק אם עדיין אין אליה מצביע גם בגבול השורה הבא: אחרי שפעולה מחזירה ערך, ה-JVM חוזר לאותה שורה
                // לפני שהערך נשמר במשתנה — ובאותו רגע הערך "ביד" (בלי משתנה) אבל לא אבד
                if (pendingLost.remove(n)) lostAt.put(n, steps.size());
                else pendingLost.add(n);
            }
        }
        for (java.util.Iterator<Map.Entry<Object, Integer>> it = lostAt.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<Object, Integer> e = it.next();
            if (steps.size() - e.getValue() <= LOST_STEPS) continue;
            Object gone = e.getKey();
            it.remove();
            everHeld.remove(gone);
            for (java.util.Iterator<WeakReference<Node<?>>> ni = nodes.iterator(); ni.hasNext(); ) if (ni.next().get() == gone) ni.remove();
        }
    }

    /** כל מה שמשתנה כלשהו מגיע אליו: המשתנים של כל הפעולות, הסטטיים, Viz.point — ודרך חוליות, מבנים, מערכים ואובייקטים */
    private static Set<Object> reachable() {
        Set<Object> seen = identitySet();
        java.util.ArrayDeque<Object> todo = new java.util.ArrayDeque<Object>();
        for (Object o : frameVars.keySet()) push(todo, o);
        for (Object o : callerRoots) push(todo, o);
        for (Object o : pointers.values()) push(todo, o);
        staticRoots(todo);
        int budget = 50000;
        while (!todo.isEmpty() && budget-- > 0) {
            Object o = todo.poll();
            if (!seen.add(o)) continue;
            if (o instanceof Node) { push(todo, ((Node<?>) o).next); push(todo, ((Node<?>) o).value); }
            else if (o instanceof BinNode) { push(todo, ((BinNode<?>) o).left); push(todo, ((BinNode<?>) o).right); push(todo, ((BinNode<?>) o).value); }
            else if (o instanceof Stack) { for (Object x : ((Stack<?>) o).items) push(todo, x); }
            else if (o instanceof Queue) { for (Object x : ((Queue<?>) o).items) push(todo, x); }
            else if (o instanceof Object[]) { for (Object x : (Object[]) o) push(todo, x); }
            else if (o instanceof java.util.Collection) { for (Object x : ((java.util.Collection<?>) o).toArray()) push(todo, x); }
            else if (o instanceof Map) { for (Object x : ((Map<?, ?>) o).values().toArray()) push(todo, x); }
            else if (userClass(o.getClass())) {
                for (java.lang.reflect.Field f : fieldsOf(o.getClass())) {
                    try { push(todo, f.get(o)); } catch (Exception ignored) { /* לא נגיש */ }
                }
            }
        }
        return seen;
    }

    private static void push(java.util.ArrayDeque<Object> todo, Object o) {
        if (o != null && !(o instanceof String) && !(o instanceof Number) && !(o instanceof Character) && !(o instanceof Boolean)) todo.add(o);
    }

    private static boolean userClass(Class<?> c) {
        String n = c.getName();
        return !c.isArray() && !c.isPrimitive() && !n.startsWith("java.") && !n.startsWith("javax.") && !n.startsWith("jdk.")
                && !n.startsWith("sun.") && !n.startsWith("com.sun.") && !n.startsWith("unit4.collectionsLib.");
    }

    private static List<java.lang.reflect.Field> fieldsOf(Class<?> c) {
        List<java.lang.reflect.Field> l = objFields.get(c);
        if (l != null) return l;
        l = new ArrayList<java.lang.reflect.Field>();
        for (Class<?> k = c; k != null && userClass(k); k = k.getSuperclass())
            for (java.lang.reflect.Field f : k.getDeclaredFields()) {
                if (java.lang.reflect.Modifier.isStatic(f.getModifiers()) || f.getType().isPrimitive()) continue;
                try { f.setAccessible(true); l.add(f); } catch (RuntimeException ignored) { /* מודול סגור */ }
            }
        objFields.put(c, l);
        return l;
    }

    /** משתנים סטטיים של המחלקות שבמחסנית הקריאות (static Node<Integer> head...) */
    private static void staticRoots(java.util.ArrayDeque<Object> todo) {
        for (Frame f : lineFrames) {
            List<java.lang.reflect.Field> l = staticFields.get(f.className);
            if (l == null) {
                l = new ArrayList<java.lang.reflect.Field>();
                try {
                    Class<?> c = Class.forName(f.className, false, Thread.currentThread().getContextClassLoader());
                    for (java.lang.reflect.Field x : c.getDeclaredFields()) {
                        if (!java.lang.reflect.Modifier.isStatic(x.getModifiers()) || x.getType().isPrimitive()) continue;
                        try { x.setAccessible(true); l.add(x); } catch (RuntimeException ignored) { /* לא נגיש */ }
                    }
                } catch (Throwable ignored) {
                    // מחלקה שלא נמצאה — בלי משתנים סטטיים
                }
                staticFields.put(f.className, l);
            }
            for (java.lang.reflect.Field x : l) {
                try { push(todo, x.get(null)); } catch (Exception ignored) { /* לא נגיש */ }
            }
        }
    }

    // ------------------------------------------------------------ צילום מצב

    private static boolean stale(Object o) {
        Integer t = lastTouch.get(o);
        return t == null || steps.size() - t > STALE_STEPS;
    }

    private static int rank(Object o) {
        Integer r = nameRank.get(o);
        return r == null ? -1 : r;
    }

    private static int ord(Object o) {
        Integer r = order.get(o);
        return r == null ? -1 : r;
    }

    private static List<View> snapshot(Object target, Mark mark, String ghost) {
        List<View> views = new ArrayList<View>();
        snapObjs.clear();
        nested.clear();
        nestPath.clear();
        nestDepth = 0;

        // --- מערכים (במצב שורה-שורה: כפי שנקראו בשורה האחרונה, כי מערך משתנה גם בלי הספרייה)
        if (lineMode) {
            views.addAll(lineLoops);
            views.addAll(lineStrings);
            for (View v : lineArrays) views.add(refreshSubs(v, target, mark, ghost));
        }
        else for (Object a : alive(arrays)) views.add(arrayView(a));

        // --- מחסניות ותורים
        for (Object o : linears) {
            boolean focus = o == target;
            List<String> aliases = frameVars.get(o);
            List<?> items = o instanceof Stack ? ((Stack<?>) o).items : ((Queue<?>) o).items;
            if (!focus && aliases == null && items.isEmpty() && stale(o)) continue;
            LinearView v = linearView(o, target, mark, ghost);
            v.title = aliases != null ? String.join(", ", aliases) : names.get(o);
            views.add(v);
        }

        Map<Object, List<String>> labels = new IdentityHashMap<Object, List<String>>();
        for (Map.Entry<String, Object> e : pointers.entrySet()) {
            List<String> l = labels.get(e.getValue());
            if (l == null) labels.put(e.getValue(), l = new ArrayList<String>());
            l.add(e.getKey());
        }
        for (Map.Entry<Object, List<String>> e : frameVars.entrySet()) {
            if (!(e.getKey() instanceof Node || e.getKey() instanceof BinNode)) continue;
            List<String> l = labels.get(e.getKey());
            if (l == null) labels.put(e.getKey(), l = new ArrayList<String>());
            for (String n : e.getValue()) if (!l.contains(n)) l.add(n);
        }

        // --- רשימות מקושרות
        if (lineMode) updateLost(kindNow == Kind.LINE);
        List<Node<?>> nodes = alive(Recorder.nodes);
        List<BinNode<?>> bins = alive(Recorder.bins);
        Object lastNode = Recorder.lastNode.get(), lastBin = Recorder.lastBin.get();
        Set<Object> pointed = identitySet();
        for (Node<?> n : nodes) if (n.next != null) pointed.add(n.next);
        List<Node<?>> heads = new ArrayList<Node<?>>();
        for (Node<?> n : nodes) if (!pointed.contains(n)) heads.add(n);
        final Map<Object, Integer> len = new IdentityHashMap<Object, Integer>();
        for (Node<?> h : heads) len.put(h, chainLength(h));
        Collections.sort(heads, new Comparator<Node<?>>() {
            public int compare(Node<?> a, Node<?> b) {
                if (rank(a) != rank(b)) return rank(b) - rank(a);
                if (!len.get(a).equals(len.get(b))) return len.get(b) - len.get(a);
                return ord(b) - ord(a);
            }
        });
        heads = mainFirst(heads, len);
        for (Node<?> n : nodes)   // השמות שהצביעו על כל חוליה — כדי לדעת אחר כך מי "זז" ממנה
            if (labels.get(n) != null && !lostAt.containsKey(n)) lastNames.put(n, new ArrayList<String>(labels.get(n)));
        Set<Object> seen = identitySet();
        Map<Object, Object[]> inMain = new IdentityHashMap<Object, Object[]>();   // חוליה → {השרשרת שבשורה שלה היא מצוירת, המקום בה}
        for (Node<?> h : heads) addChain(views, h, seen, target, mark, labels, lastNode, inMain);
        for (Node<?> n : nodes) if (!seen.contains(n)) addChain(views, n, seen, target, mark, labels, lastNode, inMain); // מעגלים

        // --- עצים
        Set<Object> children = identitySet();
        for (BinNode<?> b : bins) {
            if (b.left != null) children.add(b.left);
            if (b.right != null) children.add(b.right);
        }
        List<BinNode<?>> roots = new ArrayList<BinNode<?>>();
        for (BinNode<?> b : bins) if (!children.contains(b)) roots.add(b);
        final Map<Object, Integer> size = new IdentityHashMap<Object, Integer>();
        for (BinNode<?> r : roots) size.put(r, treeSize(r, identitySet()));
        Collections.sort(roots, new Comparator<BinNode<?>>() {
            public int compare(BinNode<?> a, BinNode<?> b) {
                if (rank(a) != rank(b)) return rank(b) - rank(a);
                if (!size.get(a).equals(size.get(b))) return size.get(b) - size.get(a);
                return ord(b) - ord(a);
            }
        });
        Set<Object> drawn = identitySet();
        for (BinNode<?> r : roots) {
            TreeView tv = new TreeView();
            tv.src = r;
            tv.title = names.get(r) != null ? names.get(r) : "עץ";
            Object[] focus = { Boolean.FALSE };
            tv.root = treeCell(r, drawn, target, mark, labels, focus, 0, lastBin);
            tv.focus = focus[0] == Boolean.TRUE;
            views.add(tv);
        }
        hideNested(views, labels);
        attachObjects(views);
        return views;
    }

    /** הטקסט של תא שה-Tracer שלח: ערך רגיל, כותרת של כרטיס, או תיאור של מבנה שהתא מצביע עליו */
    private static String cellText(String cell, Obj ob, Object src) {
        if (cell.startsWith("\u0003")) return src == null ? "•" : str(src);
        return ob == null ? cell : ob.title;
    }

    private static void dropEmptySubs(ArrayView a) {
        for (View s : a.subs) if (s != null) return;
        a.subs.clear();
    }

    /** מערך של מבנים (מצב שורה-שורה): התוכן של המבנים שבתאים מתעדכן בכל צעד, גם כשהמערך עצמו לא נקרא מחדש */
    private static View refreshSubs(View v, Object target, Mark mark, String ghost) {
        if (!(v instanceof ArrayView)) return v;
        ArrayView a = (ArrayView) v;
        if (a.subs.isEmpty()) return v;
        ArrayView c = new ArrayView();
        c.title = a.title;
        c.focus = a.focus;
        c.items.addAll(a.items);
        c.objs.addAll(a.objs);
        c.changed.addAll(a.changed);
        c.indexLabels.putAll(a.indexLabels);
        c.truncated = a.truncated;
        c.subSrc.addAll(a.subSrc);
        for (Object src : a.subSrc) c.subs.add(subOf(src, target, mark, ghost));
        for (int i = 0; i < c.items.size() && i < c.subSrc.size(); i++)
            if (c.subSrc.get(i) != null) c.items.set(i, str(c.subSrc.get(i)));
        return c;
    }

    // ------------------------------------------------------------ מבנה בתוך מבנה (מחסנית של מחסניות, רשימה של תורים, עץ של מחסניות...)

    private static final int MAX_NEST = 3;           // כמה רמות מצוירות זו בתוך זו
    private static int nestDepth = 0;
    private static final Set<Object> nested = identitySet();   // מבנים שמוצגים בתוך תא של מבנה אחר (בצעד הנוכחי)
    private static final List<Object> nestPath = new ArrayList<Object>();   // המבנים שבתהליך ציור — נגד מעגלים

    private static LinearView linearView(Object o, Object target, Mark mark, String ghost) {
        boolean added = true;
        for (Object p : nestPath) if (p == o) added = false;
        if (added) nestPath.add(o);   // מחסנית שמכילה את עצמה (גם דרך מבנה אחר) לא נכנסת בתוך עצמה
        try {
            return linearViewIn(o, target, mark, ghost);
        } finally {
            if (added) nestPath.remove(nestPath.size() - 1);
        }
    }

    private static LinearView linearViewIn(Object o, Object target, Mark mark, String ghost) {
        boolean focus = o == target;
        List<?> items = o instanceof Stack ? ((Stack<?>) o).items : ((Queue<?>) o).items;
        LinearView v = new LinearView();
        v.src = o;
        v.stack = o instanceof Stack;
        v.focus = focus;
        int n = items.size();
        boolean anySub = false;
        for (int i = 0; i < n; i++) {
            Object item = v.stack ? items.get(n - 1 - i) : items.get(i);
            v.items.add(str(item));
            v.objs.add(objOf(item));
            View sub = subOf(item, target, mark, ghost);
            v.subs.add(sub);
            anySub |= sub != null;
            if (sub != null && sub.focus) v.focus = true;   // המבנה הפנימי הוא שנגעו בו — גם החיצוני מסומן (וגלילה אליו)
        }
        if (!anySub) v.subs.clear();
        if (focus) {
            v.mark = mark;
            v.ghost = ghost;
            v.markIndex = (!v.stack && mark == Mark.NEW) ? n - 1 : 0;
        }
        return v;
    }

    /**
     * הערך הוא מבנה של הספרייה (Stack / Queue / Node / BinNode) → תצוגה קטנה שלו, לציור בתוך התא.
     * null אם זה ערך רגיל, או שכבר עמוקים מדי / המבנה מכיל את עצמו.
     */
    static View subOf(Object item, Object target, Mark mark, String ghost) {
        if (!(item instanceof Stack || item instanceof Queue || item instanceof Node || item instanceof BinNode)) return null;
        if (nestDepth >= MAX_NEST) return null;
        for (Object p : nestPath) if (p == item) return null;
        nestPath.add(item);
        nestDepth++;
        try {
            nested.add(item);
            List<String> al = frameVars.get(item);
            String name = al == null ? null : String.join(", ", al);   // תג על התא: משתנה שמצביע על המבנה הפנימי
            View res;
            Map<Object, List<String>> none = new IdentityHashMap<Object, List<String>>();
            if (item instanceof Stack || item instanceof Queue) {
                res = linearView(item, target, mark, item == target ? ghost : null);
            } else if (item instanceof Node) {
                List<View> tmp = new ArrayList<View>();
                addChain(tmp, (Node<?>) item, identitySet(), target, mark, none, null, null);
                res = tmp.get(0);
            } else {
                BinNode<?> b = (BinNode<?>) item;
                TreeView tv = new TreeView();
                tv.src = b;
                Object[] focus = { Boolean.FALSE };
                tv.root = treeCell(b, identitySet(), target, mark, none, focus, 0, null);
                tv.focus = focus[0] == Boolean.TRUE;
                res = tv;
            }
            res.title = name;
            return res;
        } finally {
            nestDepth--;
            nestPath.remove(nestPath.size() - 1);
        }
    }

    /** מבנה שמוצג בתוך תא של מבנה אחר — לא מציירים אותו גם לבד */
    private static void hideNested(List<View> views, Map<Object, List<String>> labels) {
        for (Iterator<View> it = views.iterator(); it.hasNext();) {
            View v = it.next();
            if (v.src == null || !nested.contains(v.src)) continue;
            it.remove();   // גם אם יש לו משתנה: שמו מופיע כתג על התא (כך רואים שהמשתנה והתא הם אותו מבנה)
        }
    }

    /** שמות המשתנים מעל הכרטיסים; ואובייקט שמשתנה מצביע אליו ואינו בתוך מבנה — מוצג לבד */
    private static void attachObjects(List<View> views) {
        if (!lineMode) return;
        for (Map.Entry<Object, List<String>> e : frameVars.entrySet()) {
            Object k = e.getKey();
            boolean user = !(k instanceof Student || k instanceof Car);
            if (user && !isRecordLike(k.getClass())) continue;
            int id = k instanceof Student ? ((Student) k).vizId : k instanceof Car ? ((Car) k).vizId : System.identityHashCode(k);
            boolean shown = false;
            for (Obj o : snapObjs) {
                if (o.id != id) continue;
                shown = true;
                for (String n : e.getValue()) if (!o.labels.contains(n)) o.labels.add(n);
            }
            if (!shown) {
                ObjView ov = new ObjView();
                ov.obj = objOf(k);
                ov.obj.labels.addAll(e.getValue());
                ov.title = String.join(", ", e.getValue());
                views.add(ov);
            }
        }
    }

    /** האובייקטים שמוצגים לבד בצעד (כרטיס מלא) — עם שמות המשתנים שמצביעים עליהם */
    static List<Obj> objectsOf(Step s) {
        List<Obj> res = new ArrayList<Obj>();
        if (s != null) for (View v : s.views) if (v instanceof ObjView && ((ObjView) v).obj != null) res.add(((ObjView) v).obj);
        return res;
    }

    private static <T> List<T> alive(List<WeakReference<T>> refs) {
        List<T> res = new ArrayList<T>();
        for (Iterator<WeakReference<T>> it = refs.iterator(); it.hasNext();) {
            T x = it.next().get();
            if (x == null) it.remove();
            else res.add(x);
        }
        return res;
    }

    private static Set<Object> identitySet() {
        return Collections.newSetFromMap(new IdentityHashMap<Object, Boolean>());
    }

    private static int chainLength(Node<?> h) {
        Set<Object> s = identitySet();
        int c = 0;
        for (Node<?> p = h; p != null && s.add(p) && c < MAX_CELLS; p = p.next) c++;
        return c;
    }

    private static int treeSize(BinNode<?> t, Set<Object> s) {
        if (t == null || !s.add(t) || s.size() > 2000) return 0;
        return 1 + treeSize(t.left, s) + treeSize(t.right, s);
    }

    /**
     * שרשראות שמתחברות לאותו המשך (למשל חוליה חדשה q אחרי q.setNext(p.getNext()) — היא ו-p.getNext() מצביעות לאותה חוליה):
     * הארוכה מביניהן — ואם שוות, הוותיקה (הרשימה הקיימת) — ראשונה, והשאר מיד אחריה: הן יצוירו כענפים שלה ולא כרשימה נפרדת.
     * הסדר בין הקבוצות לא משתנה
     */
    private static List<Node<?>> mainFirst(List<Node<?>> heads, final Map<Object, Integer> len) {
        Map<Object, Integer> group = new IdentityHashMap<Object, Integer>();   // חוליה → מספר הקבוצה שלה
        List<List<Node<?>>> groups = new ArrayList<List<Node<?>>>();
        for (Node<?> h : heads) {
            Integer g = null;
            List<Node<?>> walked = new ArrayList<Node<?>>();
            Set<Object> mine = identitySet();
            for (Node<?> p = h; p != null && mine.add(p) && walked.size() < MAX_CELLS; p = p.next) {
                g = group.get(p);
                if (g != null) break;   // הגענו להמשך של שרשרת קודמת
                walked.add(p);
            }
            if (g == null) {
                g = groups.size();
                groups.add(new ArrayList<Node<?>>());
            }
            for (Node<?> p : walked) group.put(p, g);
            groups.get(g).add(h);
        }
        List<Node<?>> res = new ArrayList<Node<?>>();
        for (List<Node<?>> l : groups) {
            Collections.sort(l, new Comparator<Node<?>>() {
                public int compare(Node<?> a, Node<?> b) {
                    if (!len.get(a).equals(len.get(b))) return len.get(b) - len.get(a);
                    return ord(a) - ord(b);
                }
            });
            res.addAll(l);
        }
        return res;
    }

    /**
     * inMain: החוליות שכבר מצוירות בשורה הראשית של שרשרת (→ {השרשרת, המקום}). שרשרת שמגיעה לאחת מהן היא "ענף" של אותה שרשרת
     * (ומצוירת מתחתיה, עם חץ אל החוליה) — ולא רשימה נפרדת עם "ממשיך ברשימה אחרת". null = בלי ענפים (מבנה בתוך תא)
     */
    private static void addChain(List<View> views, Node<?> head, Set<Object> seen, Object target, Mark mark,
                                 Map<Object, List<String>> labels, Object lastNode, Map<Object, Object[]> inMain) {
        ChainView c = new ChainView();
        c.src = head;
        c.title = names.get(head) != null ? names.get(head) : "רשימה";
        Map<Object, Integer> pos = new IdentityHashMap<Object, Integer>();
        List<Node<?>> mine = new ArrayList<Node<?>>();
        Object[] join = null;
        Node<?> cur = head;
        while (cur != null) {
            if (pos.containsKey(cur)) { c.cycleTo = pos.get(cur); break; }
            if (seen.contains(cur)) {
                join = inMain == null ? null : inMain.get(cur);
                if (join == null) c.joinText = "[" + str(cur.value) + "]";
                break;
            }
            if (c.cells.size() >= MAX_CELLS) { c.truncated = true; break; }
            pos.put(cur, c.cells.size());
            seen.add(cur);
            mine.add(cur);
            Cell cell = new Cell();
            fillCell(cell, cur, cur.value, target, mark, labels, lastNode);
            if (cur == target || (cell.sub != null && cell.sub.focus)) c.focus = true;
            cell.lost = lostAt.containsKey(cur);
            c.cells.add(cell);
            cur = cur.next;
        }
        lostNote(c, head);
        if (join != null) {   // ענף: האחרונה מצביעה לחוליה באמצע שרשרת שכבר מצוירת
            ChainView main = (ChainView) join[0];
            c.joinTo = (Integer) join[1];
            main.branches.add(c);
            if (c.focus) main.focus = true;
            return;
        }
        if (inMain != null) for (int i = 0; i < mine.size(); i++) inMain.put(mine.get(i), new Object[] { c, i });
        views.add(c);
    }

    /** הסבר על החוליות שאבדו בשרשרת: אילו, ומי הצביע עליהן קודם */
    private static void lostNote(ChainView c, Node<?> head) {
        List<String> lost = new ArrayList<String>();
        boolean reachableAfter = false;
        for (Cell x : c.cells) {
            if (x.lost) lost.add(x.value);
            else if (!lost.isEmpty()) reachableAfter = true;
        }
        if (lost.isEmpty()) return;
        boolean all = lost.size() == c.cells.size();
        if (all) c.title = "חוליות שאבדו";
        String which = lost.size() == 1 ? "החוליה " + lost.get(0) : "החוליות " + String.join(", ", lost.subList(0, Math.min(6, lost.size()))) + (lost.size() > 6 ? "..." : "");
        c.lostNote = which + (lost.size() == 1 ? " אבדה" : " אבדו") + ": אף משתנה לא מצביע " + (lost.size() == 1 ? "עליה" : "עליהן") + ", ואי אפשר להגיע " + (lost.size() == 1 ? "אליה" : "אליהן") + " יותר";
        List<String> was = lastNames.get(head);
        if (c.cells.get(0).lost && reachableAfter && was != null && !was.isEmpty()) {
            c.lostHint = was.get(0) + " הצביע על ראש הרשימה וזז הלאה, ולכן ראש הרשימה אבד. כדי לעבור על רשימה בלי לאבד אותה — מתקדמים עם משתנה נוסף: " + Problems.code("Node<Integer> p = " + was.get(0) + ";");
        } else {
            c.lostHint = "אם רצית למחוק " + (lost.size() == 1 ? "אותה" : "אותן") + " — זה בדיוק מה שקרה (Java תמחק " + (lost.size() == 1 ? "אותה" : "אותן") + " מהזיכרון). אם לא — שומרים הפניה במשתנה לפני שמנתקים.";
        }
    }

    private static TreeCell treeCell(BinNode<?> b, Set<Object> drawn, Object target, Mark mark,
                                     Map<Object, List<String>> labels, Object[] focus, int depth, Object lastBin) {
        if (b == null) return null;
        TreeCell c = new TreeCell();
        fillCell(c, b, b.value, target, mark, labels, lastBin);
        if (b == target || (c.sub != null && c.sub.focus)) focus[0] = Boolean.TRUE;
        if (!drawn.add(b) || depth > 40) { c.shared = true; return c; }
        c.left = treeCell(b.left, drawn, target, mark, labels, focus, depth + 1, lastBin);
        c.right = treeCell(b.right, drawn, target, mark, labels, focus, depth + 1, lastBin);
        return c;
    }

    private static void fillCell(Cell cell, Object node, Object value, Object target, Mark mark,
                                 Map<Object, List<String>> labels, Object last) {
        cell.value = str(value);
        cell.obj = objOf(value);
        cell.sub = subOf(value, target, mark, null);
        cell.mark = node == target ? mark : Mark.NONE;
        // בהרצה שורה-שורה שמות המשתנים מסמנים איפה אנחנו — סימן "הצומת האחרון שנגעו בו" רק מבלבל
        cell.current = !lineMode && node == last;
        List<String> l = labels.get(node);
        if (l != null) cell.labels.addAll(l);
    }

    // ------------------------------------------------------------ עזרים

    static String str(Object v) {
        String s;
        try {
            s = v instanceof Student ? ((Student) v).label() : v instanceof Car ? ((Car) v).label()
                    : v != null && isRecordLike(v.getClass()) && !overridesToString(v.getClass()) ? userTitle(v) : String.valueOf(v);
        } catch (RuntimeException e) {
            s = "?";
        }
        return shorten(s, 14);
    }

    /** תוכן מחסנית / תור; אובייקט של התלמיד בלי toString — לפי הכותרת שלו (Rex) ולא zoo.Dog@1b6d3586 */
    private static String itemsText(List<?> items, Object struct) {
        boolean plain = true;
        for (Object x : items) if (x != null && isRecordLike(x.getClass()) && !overridesToString(x.getClass())) plain = false;
        if (plain) return struct.toString();
        StringBuilder b = new StringBuilder("[");
        boolean stack = struct instanceof Stack;   // במחסנית: הראש ראשון (כמו ב-toString)
        for (int i = 0; i < items.size(); i++) b.append(i == 0 ? "" : ",").append(str(items.get(stack ? items.size() - 1 - i : i)));
        return b.append(']').toString();
    }

    private static int titleDepth;

    /** מחלקה של התלמיד בלי toString משלה: במקום Dog@1b6d3586 — הכותרת של הכרטיס (השם, או סוג המחלקה) */
    private static String userTitle(Object v) {
        Obj o = reflectObj(v);
        if (!o.title.equals(o.type)) return o.title;           // שדה שם / טקסט
        return o.sub.isEmpty() ? o.type : "{" + o.sub + "}";   // אחרת — המאפיינים הראשונים: {x=3 · y=4}
    }

    private static boolean overridesToString(Class<?> c) {
        try {
            return c.getMethod("toString").getDeclaringClass() != Object.class;
        } catch (NoSuchMethodException e) {
            return false;
        }
    }

    static String nodeRef(Object node) {
        if (node == null) return "null";
        Object v = node instanceof Node ? ((Node<?>) node).value : ((BinNode<?>) node).value;
        return "[" + str(v) + "]";
    }

    private static String shorten(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }

    private static void printStep(Step s) {
        StringBuilder b = new StringBuilder();
        b.append("  #").append(s.index + 1).append("  ");
        if (s.where != null) b.append('(').append(s.where.place()).append(")  ");
        b.append(s.text);
        for (View v : s.views) {
            if (!v.focus) continue;
            b.append("      ").append(v.title).append(": ").append(describe(v));
        }
        for (View v : s.views) {   // חוליות שאבדו וכרטיס השגיאה — תמיד (גם כשהמבנה לא בפוקוס)
            if (v instanceof ChainView && ((ChainView) v).lostNote != null) b.append("      [").append(((ChainView) v).lostNote).append(']');
            if (v instanceof ChainView)
                for (ChainView br : ((ChainView) v).branches) if (br.lostNote != null) b.append("      [").append(br.lostNote).append(']');
            if (v instanceof ChainView && !((ChainView) v).nullLabels.isEmpty()) b.append("      [null: ").append(((ChainView) v).nullLabels).append(']');
            if (v instanceof LinearView && ((LinearView) v).error != null) b.append("      [").append(v.title).append(" ריק: ").append(((LinearView) v).error).append(']');
            if (v instanceof Model.ProblemView) {
                Model.ProblemView p = (Model.ProblemView) v;
                b.append("      [כרטיס ").append(p.kind).append(p.name != null ? " " + p.name : "").append(p.op != null ? " " + p.op : "").append(']');
            }
        }
        if (s.vars != null && s.kind == Kind.LINE) {
            b.append("      {");
            for (int i = 0; i < s.vars.size(); i++) b.append(i == 0 ? "" : ", ").append(s.vars.get(i)[0]).append('=').append(s.vars.get(i)[1]);
            b.append('}');
        }
        if (s.kind != Kind.OUTPUT) out.println(b);
    }

    /**
     * בדיקות (-Ddsviz.conditions=true): מה מצויר ברשימות המקושרות — שורה לכל צעד שבו הציור השתנה. למשל
     * [שרשרת #8: n: 5(n)→3(list)→7→1→null] — ערך(המשתנים שמצביעים עליה); ~ = חוליה שאבדה; ענף 4(q)↗7 = חוליה שמצביעה לאמצע השרשרת
     */
    static String chainDump() {
        StringBuilder b = new StringBuilder();
        String last = null;
        for (int i = 0; i < stepCount(); i++) {
            StringBuilder now = new StringBuilder();
            for (View v : step(i).views)
                if (v instanceof ChainView)
                    now.append("  [שרשרת #").append(i + 1).append(": ").append(v.title).append(": ").append(chainText((ChainView) v)).append("]\n");
            String key = now.toString().replaceAll("#\\d+:", "#:");
            if (!key.equals(last)) b.append(now);
            last = key;
        }
        return b.toString();
    }

    static String chainText(ChainView c) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < c.cells.size(); i++) {
            Cell x = c.cells.get(i);
            b.append(i == 0 ? "" : "→").append(x.lost ? "~" : "").append(x.value);
            if (!x.labels.isEmpty()) b.append('(').append(String.join(",", x.labels)).append(')');
        }
        if (c.joinTo >= 0) return b.toString();
        b.append('→').append(c.cycleTo >= 0 ? "מעגל:" + c.cycleTo : c.joinText != null ? c.joinText + "(ברשימה אחרת)" : c.truncated ? "..." : "null");
        if (!c.nullLabels.isEmpty()) b.append('(').append(String.join(",", c.nullLabels)).append(')');
        for (ChainView br : c.branches)
            b.append(" ; ענף ").append(chainText(br)).append('↗').append(br.joinTo < c.cells.size() ? c.cells.get(br.joinTo).value : "?");
        return b.toString();
    }

    static {
        // בדיקות (-Ddsviz.conditions=true): בסוף ההרצה — מה התצוגה של התנאים מראה בכל צעד.
        // התוצאה נקבעת לפי הצעדים שאחרי, לכן רק בסוף; ביציאה מהתהליך, כדי שלא תיחתך
        if (Boolean.getBoolean("dsviz.conditions") && Boolean.getBoolean(LineByLine.CHILD_PROPERTY)) {
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    out.print(CondViz.dump());
                    out.print(CallViz.dump());
                    out.print(SelfViz.dump());
                    out.print(LoopArrow.dump());
                    out.print(RecursionKind.dump());
                    out.print(chainDump());
                    out.flush();
                } catch (RuntimeException e) {
                    out.println("  [תנאים: " + e + "]");
                }
            }));
        }
    }

    // ------------------------------------------------------------ מצב שורה אחר שורה
    //
    // ה-JdiTracer (בתהליך האב) עוצר לפני כל שורה בקוד של התלמיד וכותב את פרטי השורה כמחרוזת אחת
    // למקום הפנוי הבא ב-lineTexts שכאן (שתי פניות בלבד — כל פנייה לתהליך הזה יקרה). מבנים מזוהים לפי vizId.
    // השורות הופכות לצעדים ב-drainLines: בתחילת כל פעולה של הספרייה (sync), לפני כל צעד אחר,
    // ומהחלון. המבנים משתנים רק בתוך פעולות הספרייה — ולכן הצילום ברגע הזה מדויק לכל השורות שממתינות.

    static final int LINE_CAPACITY = 20000;
    static String[] lineTexts;
    /** ה-Tracer בודק את זה מדי פעם: true = הגענו למגבלה, אפשר להפסיק לעצור בכל שורה */
    static volatile boolean linesFull;
    private static int lineRead;
    private static int idCounter;
    private static final Map<Integer, WeakReference<Object>> byId = new HashMap<Integer, WeakReference<Object>>();

    /**
     * אובייקטים של מחלקות שהתלמיד כתב (Dog, Person...) שה-Tracer ראה במשתנים: הוא שם אותם כאן (בלי להריץ קוד בתהליך),
     * ושולח \u0003u + המקום — כך יודעים בדיוק איזה אובייקט זה (גם כשהוא בתוך מבנה), ובונים לו כרטיס.
     */
    static final int OBJ_CAPACITY = 4096;
    static Object[] lineObjs;

    static void initLineMode() {
        lineMode = true;
        lineTexts = new String[LINE_CAPACITY];
        lineObjs = new Object[OBJ_CAPACITY];
        Thread t = new Thread(new Runnable() {
            public void run() { watchLoopVerdict(); }
        }, "dsviz-loop");
        t.setDaemon(true);
        t.start();
    }

    /** נקרא בתחילת כל פעולה ציבורית של הספרייה */
    static void sync() {
        if (lineMode && lineRead < LINE_CAPACITY && lineTexts[lineRead] != null) drainLines();
    }

    static void drainLines() {
        synchronized (LOCK) {
            if (!enabled || !active || !lineMode || loopStoppedDone) return;
            start();   // גם תוכנית בלי מבנים בכלל מקבלת חלון
            while (lineRead < LINE_CAPACITY && lineTexts[lineRead] != null) {
                String meta = lineTexts[lineRead];
                lineTexts[lineRead++] = "";   // לא null — כדי לא לקרוא שוב
                if (meta.startsWith("\u0005")) addReturn(meta.substring(1));
                else if (!meta.isEmpty()) addLine(meta);
            }
        }
    }

    /**
     * meta = מסגרות \u0004 משתנים. מסגרת: מחלקה\u0002פעולה\u0002קובץ\u0002שורה.
     * משתנה: שם\u0002ערך, וכשהערך הוא \u0003 ואחריו מספר — זה המבנה עם ה-vizId הזה.
     */
    private static void addLine(String meta) {
        addLine(meta, Kind.LINE, null);
    }

    /** forced != null: צעד מסוג kind עם הטקסט הזה — גם אחרי המגבלה (העצירה של לולאה שלא נגמרת). מחזיר את הצעד (או null) */
    private static Step addLine(String meta, Kind kind, String forced) {
        if (!enabled) return null;
        String[] halves = meta.split("\u0004", -1);
        List<Frame> frames = new ArrayList<Frame>();
        for (String f : split(halves[0])) {
            String[] p = f.split("\u0002", -1);
            int line;
            try { line = Integer.parseInt(p[3]); } catch (NumberFormatException e) { line = -1; }
            frames.add(new Frame(p[0], p[1], p[2].isEmpty() ? null : p[2], line));
        }
        Map<Object, List<String>> fv = new IdentityHashMap<Object, List<String>>();
        List<String[]> vars = new ArrayList<String[]>();
        lineSnap.clear();
        List<String> vs = split(halves.length > 1 ? halves[1] : "");
        suspended++;
        try {
            for (int i = 0; i < vs.size(); i++) {
                String[] p = vs.get(i).split("\u0002", -1);
                String value = p.length > 1 ? p[1] : "";
                String snap = null;
                int cut = value.startsWith("\u0003") ? value.indexOf('\u0007') : -1;
                if (cut > 0) {
                    snap = value.substring(cut + 1);
                    value = value.substring(0, cut);
                }
                Object v = value.startsWith("\u0003") ? byId(value.substring(1)) : null;
                if (v != null && snap != null) lineSnap.put(v, parseSnap(snap));
                if (v != null) {
                    List<String> l = fv.get(v);
                    if (l == null) fv.put(v, l = new ArrayList<String>());
                    l.add(p[0]);
                    value = formatVar(v);
                } else {
                    value = resolveRefs(value);
                }
                vars.add(new String[] { p[0], value });
            }
        } finally {
            suspended--;
        }
        frameVars = fv;
        currentVars = vars;
        // המבנים שהמשתנים של הפעולות שקראו לנוכחית מצביעים עליהם — כדי לדעת אם חוליה באמת אבדה
        List<Object> roots = new ArrayList<Object>();
        if (halves.length > 3) for (String id : split(halves[3])) {
            Object o = id.startsWith("\u0003") ? byId(id.substring(1)) : null;
            if (o != null) roots.add(o);
        }
        callerRoots = roots;
        lineFrames = frames;
        Frame f = frames.isEmpty() ? null : frames.get(0);
        lineArrays = parseLineArrays(halves.length > 2 ? halves[2] : "", vars, f);
        try {
            lineStrings = StringViz.views(vars, f == null ? null : SourceFiles.line(f));
        } catch (RuntimeException e) {
            lineStrings = new ArrayList<View>();   // מחרוזות לא מוצגות — שאר ההרצה ממשיכה
        }
        try {
            Map<String, List<String>> arrs = new HashMap<String, List<String>>();   // המערכים של השורה (בשביל arr.length, arr[i], for-each)
            for (View av : lineArrays) {
                if (!(av instanceof ArrayView) || av.title == null) continue;
                for (String n : av.title.split(",\\s*")) arrs.put(n.replaceAll("\\s*\\(.*\\)$", "").trim(), ((ArrayView) av).items);
            }
            lineLoops = LoopViz.views(frames, vars, arrs);
        } catch (RuntimeException e) {
            lineLoops = new ArrayList<View>();
        }
        String code = f == null ? null : SourceFiles.line(f);
        // הקוד עצמו בתוך "הטבעה" משמאל לימין (\u202A...\u202C), כדי שלא יתהפך בתוך הטקסט העברי
        String text = f == null ? "שורה" : "שורה " + f.line + ":   \u202A" + (code == null ? f.method + "()" : code.trim()) + "\u202C";
        if (forced != null) text = forced;
        if (steps.size() >= maxSteps && forced == null) {
            linesFull = true;
            if (!limitReached) {   // פעם אחת: הסבר שהתיעוד נעצר (התוכנית ממשיכה; לולאה שלא נגמרת תיעצר עם כרטיס משלה)
                limitReached = true;
                addStep(Kind.NOTE, null, Mark.NONE, null, "התיעוד נעצר אחרי " + maxSteps + " צעדים — מכאן התוכנית ממשיכה לרוץ בלי לצייר (אפשר להגדיל: Viz.maxSteps)", frames);
            }
            return null;
        }
        try {
            addStep(forced == null ? Kind.LINE : kind, null, Mark.NONE, null, text, frames);
        } finally {
            lineSnap.clear();   // צעדים אחרים (פעולות של הספרייה) מצלמים את האובייקט כפי שהוא עכשיו
        }
        if (steps.size() >= maxSteps) linesFull = true;
        return steps.get(steps.size() - 1);
    }

    // ------------------------------------------------------------ לולאה שלא נגמרת (LoopGuard / LoopCard)
    //
    // ה-JdiTracer (בתהליך האב) מזהה לולאה שלא נגמרת, עוצר את ה-main של התלמיד (משאיר אותו מושהה — בלי לשרוף מעבד),
    // וכותב כאן את ההחלטה. חוט קטן כאן מוסיף את הצעד האחרון — שורת התנאי של הלולאה עם הכרטיס שמסביר — והחלון נשאר פתוח.
    // סגירת החלון מסיימת את התהליך (EXIT_ON_CLOSE); בלי חלון (בדיקות) — מדפיסים ויוצאים.

    /** ההחלטה מה-Tracer: LoopGuard.Verdict \u0011 פרטי השורה (כמו ב-lineTexts) — או null */
    static volatile String loopVerdict;
    /** כל שינוי במבנה של הספרייה (push / pop / setNext / new...) — ה-Tracer משווה בין סיבובים כדי לדעת אם משהו השתנה */
    static volatile int mutations;
    /**
     * קלט מהמקלדת: קוד שממתין לקלט מהתלמיד (למשל שורת הקלט שבחלון) שם כאן true בזמן ההמתנה, ו-false אחריה —
     * כך "אין שורות חדשות כמה שניות" לא נחשב לולאה תקועה. (Scanner רגיל על System.in מזוהה גם בלי זה — לפי מחסנית הקריאות.)
     */
    static volatile boolean waitingForInput;
    /** הלולאה נעצרה — ההרצה לא תגיע ל"התוכנית הסתיימה" */
    static volatile boolean loopStopped;
    /** ... והצעד של העצירה כבר נוסף — אחריו לא נוספים צעדים (גם לא שורות שעוד ממתינות) */
    private static boolean loopStoppedDone;

    private static void watchLoopVerdict() {
        while (true) {
            try {
                Thread.sleep(80);
            } catch (InterruptedException e) {
                return;
            }
            String v = loopVerdict;
            if (v == null) continue;
            loopStopped(v);
            return;
        }
    }

    /** הצעד האחרון: שורת התנאי של הלולאה, עם הכרטיס "הלולאה לא נגמרת" */
    static void loopStopped(String v) {
        int cut = v.indexOf('\u0011');
        String verdict = cut < 0 ? v : v.substring(0, cut), meta = cut < 0 ? null : v.substring(cut + 1);
        synchronized (LOCK) {
            loopStopped = true;
            drainLines();   // קודם כל השורות שעוד ממתינות — כך אפשר לחזור אחורה בסיבובים האחרונים
            Step st = meta != null && enabled && active ? addLine(meta, Kind.ERROR, LoopCard.STOP_TEXT) : null;
            loopStoppedDone = true;
            if (st != null) {
                try {
                    Model.ProblemView p = LoopCard.attach(steps, st, verdict);
                    if (textMode && p != null) {
                        out.println("      [כרטיס " + p.kind + (p.name != null ? " " + p.name : "") + (p.op != null ? " " + p.op : "") + "]");
                        out.println("      " + LoopCard.text(p));
                    }
                } catch (RuntimeException e) {
                    // בלי כרטיס — הצעד עצמו מראה איפה נעצרנו
                }
            }
        }
        if (textMode) {   // בלי חלון: אין מה לראות — יוצאים (ה-main מושהה ע"י ה-Tracer)
            out.flush();
            System.exit(1);
        }
    }

    /**
     * הערכים של אובייקטים של התלמיד ברגע של השורה (מה-Tracer): השורות הופכות לצעדים מאוחר יותר,
     * כשהאובייקט כבר השתנה — בלי זה הבנאי היה מראה כבר את הגיל אחרי birthday.
     */
    private static final Map<Object, Map<String, String>> lineSnap = new IdentityHashMap<Object, Map<String, String>>();

    private static Map<String, String> parseSnap(String snap) {
        Map<String, String> m = new HashMap<String, String>();
        for (String kv : snap.split("\u000F")) {
            int k = kv.indexOf('\u0010');
            if (k <= 0 || k + 1 >= kv.length()) continue;
            String raw = kv.substring(k + 2);
            char tag = kv.charAt(k + 1);
            m.put(kv.substring(0, k), tag == 's' ? lit(raw) : tag == 'c' ? "'" + raw + "'" : tag == '0' ? "null" : raw);
        }
        return m;
    }

    /** פעולה של התלמיד הסתיימה: מסגרות \u0004 ערך ההחזרה (\u0006 = void) */
    private static void addReturn(String meta) {
        if (!enabled || steps.size() >= maxSteps) return;
        String[] halves = meta.split("\u0004", -1);
        List<Frame> frames = new ArrayList<Frame>();
        for (String f : split(halves[0])) {
            String[] p = f.split("\u0002", -1);
            int line;
            try { line = Integer.parseInt(p[3]); } catch (NumberFormatException e) { line = -1; }
            frames.add(new Frame(p[0], p[1], p[2].isEmpty() ? null : p[2], line));
        }
        if (frames.isEmpty()) return;
        String value = halves.length > 1 ? halves[1] : "";
        boolean isVoid = value.equals("\u0006");
        if (value.startsWith("\u0003")) {
            Object v = byId(value.substring(1));
            suspended++;
            try {
                value = v == null ? "?" : formatVar(v);
            } finally {
                suspended--;
            }
        } else {
            value = resolveRefs(value);
        }
        String m = frames.get(0).method;   // בלי סוגריים — בתוך משפט בעברית הם מתהפכים
        // RLM בהתחלה: המשפט בעברית (מימין לשמאל); LRM מסביב לקוד — כדי שהסוגריים והערך לא יקפצו
        String text = isVoid ? "\u200Fהפעולה " + m + " הסתיימה"
                : "\u200Fהפעולה " + m + " מחזירה  \u200E" + value + "\u200E";
        lineSnap.clear();
        String self = halves.length > 2 ? halves[2] : "";   // האובייקט שהפעולה רצה בתוכו, כפי שהוא ביציאה
        int cut = self.indexOf('\u0007');
        if (self.startsWith("\u0003") && cut > 0) {
            Object o = byId(self.substring(1, cut));
            if (o != null) lineSnap.put(o, parseSnap(self.substring(cut + 1)));
        }
        try {
            addStep(Kind.RETURN, null, Mark.NONE, null, text, frames);
        } finally {
            lineSnap.clear();
        }
        steps.get(steps.size() - 1).returned = isVoid ? null : value;
        if (steps.size() >= maxSteps) linesFull = true;
    }

    private static Object byId(String id) {
        if (id.startsWith("u")) {   // אובייקט של מחלקה של התלמיד (ראו lineObjs)
            try {
                int k = Integer.parseInt(id.substring(1));
                return lineObjs != null && k >= 0 && k < lineObjs.length ? lineObjs[k] : null;
            } catch (NumberFormatException e) {
                return null;
            }
        }
        try {
            WeakReference<Object> r = byId.get(Integer.valueOf(id));
            return r == null ? null : r.get();
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static List<String> split(String s) {
        List<String> res = new ArrayList<String>();
        if (s.isEmpty()) return res;
        for (String x : s.split("\u0001", -1)) if (!x.isEmpty()) res.add(x);
        return res;
    }

    /** הפניות למבנים בתוך טקסט (איברי מערך: \u0003 + vizId) → תיאור המבנה, למשל [9,8] */
    private static String resolveRefs(String value) {
        if (value.indexOf('\u0003') < 0) return value;
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\u0003(u?\\d+)").matcher(value);
        StringBuffer b = new StringBuffer();
        suspended++;
        try {
            while (m.find()) {
                Object o = byId(m.group(1));
                m.appendReplacement(b, java.util.regex.Matcher.quoteReplacement(o == null ? "•" : str(o)));
            }
        } finally {
            suspended--;
        }
        m.appendTail(b);
        return b.toString();
    }

    private static String formatVar(Object v) {
        if (v == null) return "null";
        if (v instanceof Stack) return "Stack " + shorten(itemsText(((Stack<?>) v).items, v), 40);
        if (v instanceof Queue) return "Queue " + shorten(itemsText(((Queue<?>) v).items, v), 40);
        if (v instanceof Node || v instanceof BinNode) return (v instanceof Node ? "Node " : "BinNode ") + nodeRef(v);
        if (v instanceof Student) return "Student " + shorten(v.toString(), 40);
        if (v instanceof Car) return "Car " + shorten(v.toString(), 40);
        if (isRecordLike(v.getClass())) return v.getClass().getSimpleName() + " " + str(v);
        return shorten(String.valueOf(v), 40);
    }


    private static String describe(View v) {
        if (v instanceof Model.StringView) {   // "hello" [2..5) ↑4  s.substring(2, 5) → "llo"
            Model.StringView sv = (Model.StringView) v;
            StringBuilder b = new StringBuilder("\"").append(sv.text).append('"');
            if (sv.from >= 0) b.append(" [").append(sv.from).append("..").append(sv.to).append(')');
            for (Map.Entry<Integer, List<String>> e : new java.util.TreeMap<Integer, List<String>>(sv.arrows).entrySet())
                b.append(" ↑").append(e.getKey()).append(e.getValue());
            for (String o : sv.ops) b.append("  ").append(o.replace(StringViz.NOTE, "  — "));
            return b.toString();
        }
        if (v instanceof Model.LoopView) {   // for (int i = 0; i < 5; i++) סיבוב 3, i=2 [0,1,(2),3,4], תנאי: 2 < 5 → true
            Model.LoopView l = (Model.LoopView) v;
            StringBuilder b = new StringBuilder(l.header).append(" סיבוב ").append(l.iteration);
            if (l.var != null && l.varValue != null) b.append(", ").append(l.var).append('=').append(l.varValue);
            if (!l.values.isEmpty()) {
                b.append(" [");
                for (int i = 0; i < l.values.size(); i++) b.append(i == 0 ? "" : ",").append(i == l.currentIndex ? "(" + l.values.get(i) + ")" : l.values.get(i));
                b.append(']');
            }
            if (l.done) b.append(" הסתיימה");
            if (l.subjectKind != null) b.append(", על ").append(l.subjectKind).append(' ').append(l.subjectName).append(l.elements.isEmpty() ? "" : " " + l.elements);
            if (!l.structItems.isEmpty()) b.append(" ").append(l.structItems);
            if (l.structNote != null) b.append(", ").append(l.structNote);
            if (!l.history.isEmpty()) b.append(", ").append(l.historyVar).append(": ").append(String.join(" → ", l.history));
            if (l.note != null) b.append(", ").append(l.note);
            if (!l.cond.isEmpty()) b.append(", תנאי: ").append(l.condValues.isEmpty() ? l.cond : l.condValues).append(l.condResult == null ? "" : " → " + l.condResult);
            return b.toString();
        }
        if (v instanceof Model.LoopCountersView) {
            StringBuilder b = new StringBuilder();
            for (String[] l : ((Model.LoopCountersView) v).levels) b.append(b.length() == 0 ? "" : " ⊃ ").append(l[0]).append('=').append(l[1]);
            return b.toString();
        }
        if (v instanceof Model.LoopGridView) {
            Model.LoopGridView g = (Model.LoopGridView) v;
            StringBuilder b = new StringBuilder();
            for (int r = 0; r < g.rows.size(); r++) {
                b.append(r == 0 ? "" : "/");
                for (int c : g.cells.get(r)) b.append(".-xO".charAt(c));
            }
            return b.toString();
        }
        if (v instanceof ObjView) {
            Obj o = ((ObjView) v).obj;
            StringBuilder b = new StringBuilder(o.type).append(" {");
            for (int i = 0; i < o.fields.size(); i++) b.append(i == 0 ? "" : ", ").append(o.fields.get(i)[0]).append('=').append(o.fields.get(i)[1]);
            return b.append('}').toString();
        }
        if (!(v instanceof LinearView || v instanceof ChainView || v instanceof TreeView)) return v.getClass().getSimpleName();
        if (v instanceof LinearView) return ((LinearView) v).items.toString() + (((LinearView) v).stack ? " (ראש משמאל)" : " (ראש התור משמאל)");
        if (v instanceof ChainView) {
            StringBuilder b = new StringBuilder();
            for (Cell c : ((ChainView) v).cells) b.append(c.value).append(" -> ");
            return b.append(((ChainView) v).cycleTo >= 0 ? "(מעגל)" : "null").toString();
        }
        StringBuilder b = new StringBuilder();
        treeText(((TreeView) v).root, b);
        return b.toString();
    }

    private static void treeText(TreeCell c, StringBuilder b) {
        if (c == null) { b.append('-'); return; }
        b.append(c.value);
        if (c.left == null && c.right == null) return;
        b.append('(');
        treeText(c.left, b);
        b.append(',');
        treeText(c.right, b);
        b.append(')');
    }

}
