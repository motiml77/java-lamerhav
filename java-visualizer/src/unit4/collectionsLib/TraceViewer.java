package unit4.collectionsLib;

import java.io.BufferedReader;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import javax.swing.SwingUtilities;

import unit4.collectionsLib.Model.Access;
import unit4.collectionsLib.Model.ArrayView;
import unit4.collectionsLib.Model.Cell;
import unit4.collectionsLib.Model.ChainView;
import unit4.collectionsLib.Model.Frame;
import unit4.collectionsLib.Model.Kind;
import unit4.collectionsLib.Model.LinearView;
import unit4.collectionsLib.Model.Mark;
import unit4.collectionsLib.Model.MatrixView;
import unit4.collectionsLib.Model.Obj;
import unit4.collectionsLib.Model.ObjView;
import unit4.collectionsLib.Model.Step;
import unit4.collectionsLib.Model.TreeCell;
import unit4.collectionsLib.Model.TreeView;
import unit4.collectionsLib.Model.View;

/**
 * מציג הרצה שנרשמה בתהליך אחר — למשל הרצה ויזואלית של תוכנית C# (Visual Studio).
 * קובץ הצעדים הוא טקסט (UTF-8), שורה לרשומה, שדות מופרדים ב-TAB (בתוך שדה: \\ \t \n מוסתרים).
 * ראו csharp/README.md לתיאור הפורמט.
 *
 * <pre>  java -cp dsviz.jar unit4.collectionsLib.TraceViewer trace.txt</pre>
 *
 * <p>{@code --live}: התוכנית עוד רצה (C# שמחכה לקלט מהמקלדת — LiveViewer ב-dsviz-cs). אותן שורות מגיעות ב-stdin,
 * במנות שמסתיימות ב-{@code SYNC}, ובין המנות {@code ASK} (מחכה לקלט) / {@code RESUME} / {@code DONE}. מה שמוקלד בחלון
 * חוזר ב-stdout: {@code DSVIZ-IN} טקסט, {@code DSVIZ-EOF} ("סיום הקלט"), {@code DSVIZ-NOVIEW} (החלון לא נפתח).
 */
public final class TraceViewer {
    private TraceViewer() {}

    public static void main(String[] args) throws IOException {
        if (args.length == 0) {
            System.err.println("שימוש: TraceViewer <קובץ צעדים>");
            System.exit(2);
        }
        if (args[0].equals("--live")) {
            live();
            return;
        }
        if (args[0].equals("--check") && args.length > 1) {   // בדיקה בלי חלון: קורא את הקובץ ומדפיס כמה צעדים יש
            load(args[1]);
            System.out.println("ok " + Recorder.stepCount() + " steps");
            printInput();
            for (int i = 0; i < Recorder.stepCount(); i++)
                for (Model.View v : Recorder.step(i).views)
                    if (v instanceof Model.ProblemView) {
                        Model.ProblemView p = (Model.ProblemView) v;
                        System.out.println("[כרטיס " + p.kind + (p.name != null ? " " + p.name : "") + (p.op != null ? " " + p.op : "") + "]");
                    }
            if (Boolean.getBoolean("dsviz.conditions")) {   // התנאים והקריאות לפעולות עזר: מה מצויר בכל צעד
                System.out.print(CondViz.dump());
                System.out.print(CallViz.dump());
                System.out.print(SelfViz.dump());
                System.out.print(LoopArrow.dump());
                System.out.print(RecursionKind.dump());
                System.out.print(Recorder.chainDump());
            }
            return;
        }
        load(args[0]);
        SwingUtilities.invokeLater(new Runnable() {
            public void run() { Viewer.open(); }
        });
    }

    // ------------------------------------------------------------ קלט מהמקלדת

    /** שורות WAIT: התוכנית התחילה לחכות לקלט אחרי הצעד f[1] — מחלקה, פעולה, קובץ, שורה, קריאה, מה */
    private static final List<String[]> waits = new ArrayList<String[]>();

    /** לבדיקות (--check): איפה התוכנית חיכתה לקלט ומה הוקלד — כמו בהרצה הטקסטואלית של Java */
    private static void printInput() {
        for (String[] w : waits) if (Integer.parseInt(w[1]) < 0) printWait(w);
        for (int i = 0; i < Recorder.stepCount(); i++) {
            Step s = Recorder.step(i);
            if (s.kind == Kind.INPUT)
                System.out.println("  (" + (s.where == null ? "" : s.where.file + ":" + s.where.line) + ")  " + s.text.replace("\u200E", ""));
            for (String[] w : waits) if (Integer.parseInt(w[1]) == i) printWait(w);
        }
    }

    private static void printWait(String[] w) {
        System.out.println("  \u2328 מחכה לקלט מהמקלדת — שורה " + w[5] + ":  " + w[6] + "  (" + w[7] + ")");
    }

    /**
     * התוכנית עוד רצה: המנה הראשונה (עד SYNC) נטענת והחלון נפתח; המנות הבאות נוספות לצעדים (Recorder.adoptMore),
     * ו-ASK / RESUME פותחים וסוגרים את פס ההקלדה (Keyboard.remoteWait / remoteResume).
     */
    private static void live() throws IOException {
        final BufferedReader in = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        final PrintStream answers = new PrintStream(new FileOutputStream(FileDescriptor.out), true, "UTF-8");
        Keyboard.remote(new Keyboard.Remote() {
            public void line(String text) { answer(answers, "DSVIZ-IN\t" + escape(text)); }
            public void end() { answer(answers, "DSVIZ-EOF"); }
            public void noViewer() { answer(answers, "DSVIZ-NOVIEW"); }
        });
        if (!readChunk(in, true)) return;   // התוכנית נעלמה לפני שהגיע משהו
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                Viewer.open();
                for (java.awt.Frame f : java.awt.Frame.getFrames())   // החלון גלוי: מכאן סגירה שלו עוצרת את ההרצה
                    if (f.isVisible()) { answer(answers, "DSVIZ-READY"); break; }
            }
        });
        Thread t = new Thread(new Runnable() {
            public void run() {
                try {
                    while (readChunk(in, false)) {
                        // ממשיכים עד DONE / סוף הזרם
                    }
                } catch (IOException | RuntimeException ignored) {
                    // התוכנית נעלמה
                }
                Keyboard.remoteResume();
            }
        }, "dsviz-live");
        t.setDaemon(true);   // החלון נסגר — מסיימים
        t.start();
    }

    private static synchronized void answer(PrintStream out, String line) {
        out.println(line);
        out.flush();
    }

    /** קורא עד SYNC (מנת צעדים) ומטפל בפקודות שבדרך. false = DONE או סוף הזרם */
    private static boolean readChunk(BufferedReader in, boolean first) throws IOException {
        List<String[]> chunk = new ArrayList<String[]>();
        for (String line; (line = in.readLine()) != null; ) {
            if (line.isEmpty()) continue;
            String[] f = split(line);
            switch (f[0]) {
                case "SYNC":
                    if (chunk.isEmpty() && !first) continue;
                    StringBuilder out = new StringBuilder();
                    List<Step> steps = parse(chunk, first ? 0 : Recorder.stepCount(), out);
                    if (first) Recorder.adopt(steps, out.toString());
                    else Recorder.adoptMore(steps, out.toString());
                    return true;
                case "ASK":   // ASK צעד מחלקה פעולה קובץ שורה קריאה מה
                    Keyboard.remoteWait(new Frame(f[2], f[3], f[4].isEmpty() ? null : f[4], Integer.parseInt(f[5])), f[6], f[7], Integer.parseInt(f[1]));
                    break;
                case "RESUME":
                    Keyboard.remoteResume();
                    break;
                case "DONE":
                    return false;
                default:
                    chunk.add(f);
                    break;
            }
        }
        return false;
    }

    // ------------------------------------------------------------ קריאה

    private static List<String[]> rows;
    private static int pos;
    private static String lastLine;

    static void load(String file) throws IOException {
        List<String[]> all = new ArrayList<String[]>();
        try (BufferedReader r = Files.newBufferedReader(Paths.get(file), StandardCharsets.UTF_8)) {
            for (String line; (line = r.readLine()) != null; ) {
                if (line.isEmpty()) continue;
                all.add(split(line));
            }
        }
        StringBuilder out = new StringBuilder();
        List<Step> steps = parse(all, 0, out);
        Recorder.adopt(steps, out.toString());
    }

    private static String[] split(String line) {
        String[] f = line.split("\t", -1);
        for (int i = 0; i < f.length; i++) f[i] = unescape(f[i]);
        return f;
    }

    /** שורות של קובץ הצעדים (או של מנה אחת) → צעדים; base = האינדקס של הצעד הראשון. הפלט נוסף ל-out */
    private static List<Step> parse(List<String[]> chunk, int base, StringBuilder out) {
        rows = chunk;
        List<Step> steps = new ArrayList<Step>();
        Step cur = null;
        pos = 0;
        while (pos < rows.size()) {
            String[] f = rows.get(pos++);
            switch (f[0]) {
                case "DSVIZ":
                    Syntax.csharp = f.length > 2 && f[2].equals("cs");
                    break;
                case "SRC":
                    SourceFiles.preload(f[1], f[2]);
                    break;
                case "STEP":
                    cur = new Step();
                    cur.index = base + steps.size();
                    cur.kind = Kind.valueOf(f[1]);
                    cur.outLen = Integer.parseInt(f[2]);
                    cur.returned = f[3].equals("\u0000") ? null : f[3];
                    cur.text = f[4];
                    steps.add(cur);
                    break;
                case "FR":
                    cur.calls.add(new Frame(f[1], f[2], f[3].isEmpty() ? null : f[3], Integer.parseInt(f[4])));
                    if (cur.where == null) cur.where = cur.calls.get(0);
                    break;
                case "ACC":
                    cur.access = new Access();
                    cur.access.field = f[1];
                    cur.access.text = f[2];
                    cur.access.mark = Mark.valueOf(f[3]);
                    cur.access.obj = readObj();
                    break;
                case "VARS":
                    cur.vars = new ArrayList<String[]>();
                    break;
                case "VAR":
                    cur.vars.add(new String[] { f[1], f[2] });
                    break;
                case "V":
                    cur.views.add(readView(f));
                    break;
                case "OUT":
                    out.append(f.length > 1 ? f[1] : "");
                    break;
                case "WAIT":
                    waits.add(f);
                    break;
                default:
                    break;
            }
        }
        for (Step st : steps) {   // שגיאה (Pop על מחסנית ריקה, null...) — כרטיס שמסביר מה קרה, כמו ב-Java
            if (st.kind == Kind.LINE && st.text != null) lastLine = st.text;
            if (st.kind == Kind.ERROR && st.text != null && st.text.startsWith("שגיאה")) {
                try {
                    Problems.attach(st, lastLine);
                } catch (RuntimeException ignored) {
                    // בלי כרטיס
                }
            }
        }
        return steps;
    }

    private static View readView(String[] f) {
        String type = f[1];
        View v;
        if (type.equals("LIN")) {                      // V LIN stack focus title markIndex mark ghost
            LinearView l = new LinearView();
            l.stack = f[2].equals("1");
            l.markIndex = Integer.parseInt(f[5]);
            l.mark = Mark.valueOf(f[6]);
            l.ghost = f[7].equals("\u0000") ? null : f[7];
            while (next("I")) {
                l.items.add(rows.get(pos++)[1]);
                l.objs.add(readObj());
                l.subs.add(readSub());
            }
            v = l;
            v.focus = f[3].equals("1");
            v.title = f[4];
        } else if (type.equals("CHAIN")) {             // V CHAIN focus title cycleTo joinText truncated
            ChainView c = new ChainView();
            c.cycleTo = Integer.parseInt(f[4]);
            c.joinText = f[5].equals("\u0000") ? null : f[5];
            c.truncated = f[6].equals("1");
            readCells(c);
            while (next("BR")) {                       // BR joinTo — ענף: שרשרת שהאחרונה בה מצביעה לחוליה joinTo, ואחריה שורות C שלה
                ChainView br = new ChainView();
                br.joinTo = Integer.parseInt(rows.get(pos++)[1]);
                readCells(br);
                c.branches.add(br);
            }
            v = c;
            v.focus = f[2].equals("1");
            v.title = f[3];
        } else if (type.equals("TREE")) {              // V TREE focus title  + N... (תחילי)
            TreeView t = new TreeView();
            t.root = readTree();
            v = t;
            v.focus = f[2].equals("1");
            v.title = f[3];
        } else if (type.equals("ARR")) {               // V ARR focus title truncated
            ArrayView a = new ArrayView();
            a.truncated = f[4].equals("1");
            while (true) {
                if (next("I")) { a.items.add(rows.get(pos++)[1]); a.objs.add(readObj()); a.subs.add(readSub()); }
                else if (next("CH")) a.changed.add(Integer.valueOf(rows.get(pos++)[1]));
                else if (next("IL")) { String[] r = rows.get(pos++); label(a.indexLabels, Integer.parseInt(r[1]), r[2]); }
                else break;
            }
            v = a;
            v.focus = f[2].equals("1");
            v.title = f[3];
        } else if (type.equals("OBJ")) {               // V OBJ focus title  + O ...
            ObjView ov = new ObjView();
            ov.obj = readObj();
            v = ov;
            v.focus = f[2].equals("1");
            v.title = f[3];
        } else {                                       // V MAT focus title
            MatrixView m = new MatrixView();
            while (true) {
                if (next("ROW")) {
                    String[] r = rows.get(pos++);
                    List<String> cells = new ArrayList<String>();
                    for (int i = 1; i < r.length; i++) cells.add(r[i]);
                    if (cells.size() == 1 && cells.get(0).equals("\u0000")) cells.clear();
                    m.rows.add(cells);
                    List<Obj> objRow = new ArrayList<Obj>();
                    for (int i = 0; i < cells.size(); i++) objRow.add(null);
                    while (next("OBJAT")) {
                        int col = Integer.parseInt(rows.get(pos++)[1]);
                        Obj ob = readObj();
                        if (col < objRow.size()) objRow.set(col, ob);
                    }
                    m.objs.add(objRow);
                } else if (next("CH")) m.changed.add(rows.get(pos++)[1]);
                else if (next("RL")) { String[] r = rows.get(pos++); label(m.rowLabels, Integer.parseInt(r[1]), r[2]); }
                else if (next("CL")) { String[] r = rows.get(pos++); label(m.colLabels, Integer.parseInt(r[1]), r[2]); }
                else break;
            }
            v = m;
            v.focus = f[2].equals("1");
            v.title = f[3];
        }
        return v;
    }

    private static void readCells(ChainView c) {
        while (next("C")) {
            Cell cell = readCell(new Cell(), rows.get(pos++));
            cell.obj = readObj();
            cell.sub = readSub();
            c.cells.add(cell);
        }
    }

    private static Cell readCell(Cell c, String[] f) {   // C|N value mark current [shared] labels
        c.value = f[1];
        c.mark = Mark.valueOf(f[2]);
        c.current = f[3].equals("1");
        String labels = f[f.length - 1];
        if (!labels.isEmpty()) for (String l : labels.split("\u0001")) c.labels.add(l);
        return c;
    }

    private static TreeCell readTree() {
        if (pos >= rows.size()) return null;
        String[] f = rows.get(pos);
        if (f[0].equals("NULL")) { pos++; return null; }
        if (!f[0].equals("N")) return null;
        pos++;
        TreeCell c = new TreeCell();
        readCell(c, f);
        c.shared = f[4].equals("1");
        c.obj = readObj();
        c.sub = readSub();
        c.left = readTree();
        c.right = readTree();
        return c;
    }

    /** מבנה בתוך תא (אם השורה הבאה היא SUB): SUB, אחריו שורות V של המבנה הפנימי, ו-ENDSUB */
    private static View readSub() {
        if (!next("SUB")) return null;
        pos++;
        View v = readView(rows.get(pos++));
        if (next("ENDSUB")) pos++;
        return v;
    }

    /** אובייקט של מחלקה (אם השורה הבאה היא O): O זהות סוג כותרת פרטים תוויות, ואחריה F לכל מאפיין */
    private static Obj readObj() {
        if (!next("O")) return null;
        String[] f = rows.get(pos++);
        Obj o = new Obj();
        o.id = Integer.parseInt(f[1]);
        o.type = f[2];
        o.title = f[3];
        o.sub = f[4];
        if (!f[5].isEmpty()) for (String l : f[5].split("\u0001")) o.labels.add(l);
        while (next("F")) {
            String[] r = rows.get(pos++);
            o.fields.add(new String[] { r[1], r[2] });
        }
        return o;
    }

    private static void label(java.util.Map<Integer, List<String>> m, int idx, String name) {
        List<String> l = m.get(idx);
        if (l == null) m.put(idx, l = new ArrayList<String>());
        l.add(name);
    }

    private static boolean next(String tag) {
        return pos < rows.size() && rows.get(pos)[0].equals(tag);
    }

    private static String escape(String s) {
        StringBuilder b = new StringBuilder(s.length() + 8);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '\\': b.append("\\\\"); break;
                case '\t': b.append("\\t"); break;
                case '\n': b.append("\\n"); break;
                case '\r': b.append("\\r"); break;
                default: b.append(c); break;
            }
        }
        return b.toString();
    }

    private static String unescape(String s) {
        if (s.indexOf('\\') < 0) return s;
        StringBuilder b = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\' && i + 1 < s.length()) {
                char n = s.charAt(++i);
                b.append(n == 'n' ? '\n' : n == 't' ? '\t' : n == 'r' ? '\r' : n == '0' ? '\u0000' : n);
            } else {
                b.append(c);
            }
        }
        return b.toString();
    }
}
