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

import unit4.collectionsLib.Model.Cell;
import unit4.collectionsLib.Model.ChainView;
import unit4.collectionsLib.Model.Frame;
import unit4.collectionsLib.Model.Kind;
import unit4.collectionsLib.Model.LinearView;
import unit4.collectionsLib.Model.ArrayView;
import unit4.collectionsLib.Model.MatrixView;
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
                recordAt(Kind.ERROR, null, Mark.NONE, null, "שגיאה: " + describe(e), e.getStackTrace());
                e.printStackTrace(err);
            }
        });
        final Thread main = Thread.currentThread();
        if ("main".equals(main.getName())) {
            Thread watcher = new Thread(new Runnable() {
                public void run() {
                    try { main.join(); } catch (InterruptedException ignored) {}
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

    // ------------------------------------------------------------ רישום מבנים

    static void register(Object o) {
        synchronized (LOCK) {
            if (!enabled || !active) return;
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
            int rows = Math.min(java.lang.reflect.Array.getLength(arr), MAX_MATRIX);
            for (int r = 0; r < rows; r++) {
                Object row = java.lang.reflect.Array.get(arr, r);
                List<String> cells = new ArrayList<String>();
                int cols = row == null ? 0 : Math.min(java.lang.reflect.Array.getLength(row), MAX_MATRIX);
                for (int c = 0; c < cols; c++) cells.add(str(java.lang.reflect.Array.get(row, c)));
                m.rows.add(cells);
                for (int c = 0; c < cells.size(); c++) flat.add(r + "," + c + "=" + cells.get(c));
            }
            List<String> old = lastArray.get(arr);
            if (old != null) for (String f : flat) if (!old.contains(f)) m.changed.add(f.substring(0, f.indexOf('=')));
            view = m;
        } else {
            ArrayView a = new ArrayView();
            int n = java.lang.reflect.Array.getLength(arr);
            for (int i = 0; i < Math.min(n, MAX_ARRAY); i++) a.items.add(str(java.lang.reflect.Array.get(arr, i)));
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
                for (int r = 0; r < rows.length; r++) {
                    List<String> cells = rows[r].isEmpty() ? new ArrayList<String>() : new ArrayList<String>(java.util.Arrays.asList(rows[r].split("\u0006", -1)));
                    m.rows.add(cells);
                    for (int c = 0; c < cells.size(); c++) {
                        String f = r + "," + c + "=" + cells.get(c);
                        flat.add(f);
                        if (old != null && !old.contains(f)) m.changed.add(r + "," + c);
                    }
                }
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
                if (!p[2].isEmpty()) v.items.addAll(java.util.Arrays.asList(p[2].split("\u0006", -1)));
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

    // ------------------------------------------------------------ תיעוד צעדים

    static void create(Object target, String text)  { record(Kind.CREATE, target, Mark.NEW, null, text); }
    static void change(Object target, Mark mark, String ghost, String text) { record(Kind.CHANGE, target, mark, ghost, text); }
    static void read(Object target, String text)    { record(Kind.READ, target, Mark.READ, null, text); }
    static void check(Object target, String text)   { record(Kind.CHECK, target, Mark.READ, null, text); }

    static void record(Kind kind, Object target, Mark mark, String ghost, String text) {
        recordAt(kind, target, mark, ghost, text, new Throwable().getStackTrace());
    }

    static void recordAt(Kind kind, Object target, Mark mark, String ghost, String text, StackTraceElement[] trace) {
        synchronized (LOCK) {
            if (lineMode) drainLines();   // קודם השורות שהגיעו לפני הצעד הזה
            if (!enabled || !active || suspended > 0) return;
            start();
            boolean always = kind == Kind.END || kind == Kind.ERROR; // שגיאה וסיום מוצגים תמיד
            if (steps.size() >= maxSteps) {
                if (!limitReached && !always) {
                    limitReached = true;
                    addStep(Kind.ERROR, null, Mark.NONE, null,
                            "התיעוד נעצר אחרי " + maxSteps + " צעדים — אולי יש לולאה אינסופית? (אפשר להגדיל: Viz.maxSteps)",
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
        suspended++;
        try {
            s.views = snapshot(target, mark, ghost);
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

    private static boolean isLibrary(String cls) {
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

        // --- מערכים (במצב שורה-שורה: כפי שנקראו בשורה האחרונה, כי מערך משתנה גם בלי הספרייה)
        if (lineMode) views.addAll(lineArrays);
        else for (Object a : alive(arrays)) views.add(arrayView(a));

        // --- מחסניות ותורים
        for (Object o : linears) {
            boolean focus = o == target;
            List<?> items = o instanceof Stack ? ((Stack<?>) o).items : ((Queue<?>) o).items;
            List<String> aliases = frameVars.get(o);
            if (!focus && aliases == null && items.isEmpty() && stale(o)) continue;
            LinearView v = new LinearView();
            v.stack = o instanceof Stack;
            v.title = aliases != null ? String.join(", ", aliases) : names.get(o);
            v.focus = focus;
            int n = items.size();
            for (int i = 0; i < n; i++) v.items.add(str(v.stack ? items.get(n - 1 - i) : items.get(i)));
            if (focus) {
                v.mark = mark;
                v.ghost = ghost;
                v.markIndex = (!v.stack && mark == Mark.NEW) ? n - 1 : 0;
            }
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
        Set<Object> seen = identitySet();
        for (Node<?> h : heads) addChain(views, h, seen, target, mark, labels, lastNode);
        for (Node<?> n : nodes) if (!seen.contains(n)) addChain(views, n, seen, target, mark, labels, lastNode); // מעגלים

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
            tv.title = names.get(r) != null ? names.get(r) : "עץ";
            Object[] focus = { Boolean.FALSE };
            tv.root = treeCell(r, drawn, target, mark, labels, focus, 0, lastBin);
            tv.focus = focus[0] == Boolean.TRUE;
            views.add(tv);
        }
        return views;
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

    private static void addChain(List<View> views, Node<?> head, Set<Object> seen, Object target, Mark mark,
                                 Map<Object, List<String>> labels, Object lastNode) {
        ChainView c = new ChainView();
        c.title = names.get(head) != null ? names.get(head) : "רשימה";
        Map<Object, Integer> pos = new IdentityHashMap<Object, Integer>();
        Node<?> cur = head;
        while (cur != null) {
            if (pos.containsKey(cur)) { c.cycleTo = pos.get(cur); break; }
            if (seen.contains(cur)) { c.joinText = "[" + str(cur.value) + "]"; break; }
            if (c.cells.size() >= MAX_CELLS) { c.truncated = true; break; }
            pos.put(cur, c.cells.size());
            seen.add(cur);
            Cell cell = new Cell();
            fillCell(cell, cur, cur.value, target, mark, labels, lastNode);
            if (cur == target) c.focus = true;
            c.cells.add(cell);
            cur = cur.next;
        }
        views.add(c);
    }

    private static TreeCell treeCell(BinNode<?> b, Set<Object> drawn, Object target, Mark mark,
                                     Map<Object, List<String>> labels, Object[] focus, int depth, Object lastBin) {
        if (b == null) return null;
        TreeCell c = new TreeCell();
        fillCell(c, b, b.value, target, mark, labels, lastBin);
        if (b == target) focus[0] = Boolean.TRUE;
        if (!drawn.add(b) || depth > 40) { c.shared = true; return c; }
        c.left = treeCell(b.left, drawn, target, mark, labels, focus, depth + 1, lastBin);
        c.right = treeCell(b.right, drawn, target, mark, labels, focus, depth + 1, lastBin);
        return c;
    }

    private static void fillCell(Cell cell, Object node, Object value, Object target, Mark mark,
                                 Map<Object, List<String>> labels, Object last) {
        cell.value = str(value);
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
            s = v instanceof Student ? ((Student) v).label() : v instanceof Car ? ((Car) v).label() : String.valueOf(v);
        } catch (RuntimeException e) {
            s = "?";
        }
        return shorten(s, 14);
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
        if (s.vars != null && s.kind == Kind.LINE) {
            b.append("      {");
            for (int i = 0; i < s.vars.size(); i++) b.append(i == 0 ? "" : ", ").append(s.vars.get(i)[0]).append('=').append(s.vars.get(i)[1]);
            b.append('}');
        }
        if (s.kind != Kind.OUTPUT) out.println(b);
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

    static void initLineMode() {
        lineMode = true;
        lineTexts = new String[LINE_CAPACITY];
    }

    /** נקרא בתחילת כל פעולה ציבורית של הספרייה */
    static void sync() {
        if (lineMode && lineRead < LINE_CAPACITY && lineTexts[lineRead] != null) drainLines();
    }

    static void drainLines() {
        synchronized (LOCK) {
            if (!enabled || !active || !lineMode) return;
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
        if (!enabled) return;
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
        List<String> vs = split(halves.length > 1 ? halves[1] : "");
        suspended++;
        try {
            for (int i = 0; i < vs.size(); i++) {
                String[] p = vs.get(i).split("\u0002", -1);
                String value = p.length > 1 ? p[1] : "";
                Object v = value.startsWith("\u0003") ? byId(value.substring(1)) : null;
                if (v != null) {
                    List<String> l = fv.get(v);
                    if (l == null) fv.put(v, l = new ArrayList<String>());
                    l.add(p[0]);
                    value = formatVar(v);
                }
                vars.add(new String[] { p[0], value });
            }
        } finally {
            suspended--;
        }
        frameVars = fv;
        currentVars = vars;
        Frame f = frames.isEmpty() ? null : frames.get(0);
        lineArrays = parseLineArrays(halves.length > 2 ? halves[2] : "", vars, f);
        String code = f == null ? null : SourceFiles.line(f);
        // הקוד עצמו בתוך "הטבעה" משמאל לימין (\u202A...\u202C), כדי שלא יתהפך בתוך הטקסט העברי
        String text = f == null ? "שורה" : "שורה " + f.line + ":   \u202A" + (code == null ? f.method + "()" : code.trim()) + "\u202C";
        if (steps.size() >= maxSteps) {
            linesFull = true;
            return;
        }
        addStep(Kind.LINE, null, Mark.NONE, null, text, frames);
        if (steps.size() >= maxSteps) linesFull = true;
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
        }
        String m = frames.get(0).method;   // בלי סוגריים — בתוך משפט בעברית הם מתהפכים
        // RLM בהתחלה: המשפט בעברית (מימין לשמאל); LRM מסביב לקוד — כדי שהסוגריים והערך לא יקפצו
        String text = isVoid ? "\u200Fהפעולה " + m + " הסתיימה"
                : "\u200Fהפעולה " + m + " מחזירה  \u200E" + value + "\u200E";
        addStep(Kind.RETURN, null, Mark.NONE, null, text, frames);
        steps.get(steps.size() - 1).returned = isVoid ? null : value;
        if (steps.size() >= maxSteps) linesFull = true;
    }

    private static Object byId(String id) {
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

    private static String formatVar(Object v) {
        if (v == null) return "null";
        if (v instanceof Stack) return "Stack " + shorten(v.toString(), 40);
        if (v instanceof Queue) return "Queue " + shorten(v.toString(), 40);
        if (v instanceof Node || v instanceof BinNode) return (v instanceof Node ? "Node " : "BinNode ") + nodeRef(v);
        if (v instanceof Student) return "Student " + shorten(v.toString(), 40);
        if (v instanceof Car) return "Car " + shorten(v.toString(), 40);
        return shorten(String.valueOf(v), 40);
    }


    private static String describe(View v) {
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
