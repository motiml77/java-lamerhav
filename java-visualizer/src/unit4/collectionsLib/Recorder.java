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
    static boolean enabled = true;
    static boolean textMode = false;

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
        synchronized (LOCK) { return steps.size(); }
    }

    static Step step(int i) {
        synchronized (LOCK) { return steps.get(i); }
    }

    static String outputUpTo(int len) {
        synchronized (LOCK) { return output.substring(0, Math.min(len, output.length())); }
    }

    // ------------------------------------------------------------ אתחול

    private static void start() {
        if (started) return;
        started = true;
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
                public void run() { Viewer.open(); }
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
            if (!enabled) return;
            start();
            order.put(o, orderCounter++);
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
            if (!enabled || suspended > 0) return;
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
        Step s = new Step();
        s.index = steps.size();
        s.kind = kind;
        s.text = text;
        s.calls = studentFrames(trace);
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

        // --- מחסניות ותורים
        for (Object o : linears) {
            boolean focus = o == target;
            List<?> items = o instanceof Stack ? ((Stack<?>) o).items : ((Queue<?>) o).items;
            if (!focus && items.isEmpty() && stale(o)) continue;
            LinearView v = new LinearView();
            v.stack = o instanceof Stack;
            v.title = names.get(o);
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
        cell.current = node == last;
        List<String> l = labels.get(node);
        if (l != null) cell.labels.addAll(l);
    }

    // ------------------------------------------------------------ עזרים

    static String str(Object v) {
        String s;
        try {
            s = String.valueOf(v);
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
        if (s.kind != Kind.OUTPUT) out.println(b);
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
