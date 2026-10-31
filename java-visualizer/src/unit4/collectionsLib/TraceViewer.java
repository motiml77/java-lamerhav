package unit4.collectionsLib;

import java.io.BufferedReader;
import java.io.IOException;
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
 */
public final class TraceViewer {
    private TraceViewer() {}

    public static void main(String[] args) throws IOException {
        if (args.length == 0) {
            System.err.println("שימוש: TraceViewer <קובץ צעדים>");
            System.exit(2);
        }
        if (args[0].equals("--check") && args.length > 1) {   // בדיקה בלי חלון: קורא את הקובץ ומדפיס כמה צעדים יש
            load(args[1]);
            System.out.println("ok " + Recorder.stepCount() + " steps");
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
            }
            return;
        }
        load(args[0]);
        SwingUtilities.invokeLater(new Runnable() {
            public void run() { Viewer.open(); }
        });
    }

    // ------------------------------------------------------------ קריאה

    private static List<String[]> rows;
    private static int pos;

    static void load(String file) throws IOException {
        rows = new ArrayList<String[]>();
        try (BufferedReader r = Files.newBufferedReader(Paths.get(file), StandardCharsets.UTF_8)) {
            for (String line; (line = r.readLine()) != null; ) {
                if (line.isEmpty()) continue;
                String[] f = line.split("\t", -1);
                for (int i = 0; i < f.length; i++) f[i] = unescape(f[i]);
                rows.add(f);
            }
        }
        List<Step> steps = new ArrayList<Step>();
        StringBuilder out = new StringBuilder();
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
                    cur.index = steps.size();
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
                default:
                    break;
            }
        }
        String lastLine = null;
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
        Recorder.adopt(steps, out.toString());
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
            while (next("C")) {
                Cell cell = readCell(new Cell(), rows.get(pos++));
                cell.obj = readObj();
                cell.sub = readSub();
                c.cells.add(cell);
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
