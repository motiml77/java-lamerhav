import il.lamerhav.dsviz.*;
import java.io.*;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.*;
import org.eclipse.swt.widgets.*;

/**
 * מפעיל חלון אמיתי של התוסף (תרגיל חדש / הוסף מבנה נתונים / דוגמאות פתורות) לפי תסריט, ושומר צילום + מיקומי הרכיבים (JSON)
 * בכל "shot" — כדי שבסרטון ההדרכה העכבר יגיע בדיוק לכפתור הנכון.
 *
 *   DlgScript <ex|ins|examples> <preset> <script.txt> <out-dir> [doc.java] [offset-marker]
 * פקודות בתסריט (שורה לכל אחת): chip <טקסט> · check <חלק מהטקסט> · spin <מספר> · text <אינדקס> <ערך> · combo <אינדקס> <פריט> · shot <שם>
 */
public class DlgScript {
    static Shell dlgShell;
    static Object dialog;   // החלון עצמו (לפקודות שקוראות לפעולות שלו)

    public static void main(String[] a) throws Exception {
        Display display = new Display();
        Shell parent = new Shell(display);
        String kind = a[0], preset = a[1];
        List<String> script = Files.readAllLines(Paths.get(a[2]), StandardCharsets.UTF_8);
        File out = new File(a[3]);
        out.mkdirs();
        String doc = a.length > 4 ? new String(Files.readAllBytes(Paths.get(a[4])), StandardCharsets.UTF_8) : "";
        int offset = -1;
        if (a.length > 5) { offset = doc.indexOf(a[5]); doc = doc.replace(a[5], ""); }
        final int fOffset = offset;
        final String fDoc = doc;
        final Iterator<String> it = script.iterator();
        Runnable[] step = new Runnable[1];
        step[0] = () -> {
            if (dlgShell == null) for (Shell s : display.getShells()) if (s != parent && s.isVisible()) dlgShell = s;
            if (dlgShell == null) { display.timerExec(200, step[0]); return; }
            while (it.hasNext()) {
                String line = it.next().trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                int delay;
                try { delay = run(display, line, out); } catch (Exception e) { e.printStackTrace(); System.exit(2); return; }
                if (dlgShell.isDisposed()) return;   // החלון נסגר (למשל אחרי שליחה)
                display.timerExec(delay, step[0]);
                return;
            }
            dlgShell.close();
        };
        display.timerExec(1200, step[0]);
        if (kind.equals("ex")) new ExerciseDialog(parent, new ArrayList<org.eclipse.jdt.core.IJavaProject>(), "DataStructures", null, preset).open();
        else if (kind.equals("ins")) new InsertDialog(parent, preset, fDoc, fOffset, "        ").open();
        else if (kind.equals("help")) new HelpDialog(parent).open();
        else if (kind.equals("solution")) {
            // החלון "הצעה לפתרון" עם הצעה מ-solutions.json (preset = הקובץ, הצעה ראשונה; או "קובץ#אינדקס")
            String[] pa = preset.split("#");
            Class<?> sc = Class.forName("il.lamerhav.dsviz.SolutionStore");
            java.lang.reflect.Constructor<?> c = sc.getDeclaredConstructor(File.class);
            c.setAccessible(true);
            Object store = c.newInstance(new File(pa[0]));
            java.lang.reflect.Method all = sc.getDeclaredMethod("all");
            all.setAccessible(true);
            Object entry = ((java.util.List<?>) all.invoke(store)).get(pa.length > 1 ? Integer.parseInt(pa[1]) : 0);
            java.lang.reflect.Constructor<?> dc = Class.forName("il.lamerhav.dsviz.SolutionDialog").getDeclaredConstructor(Shell.class, entry.getClass());
            dc.setAccessible(true);
            Object dlgObj = dc.newInstance(parent, entry);
            dialog = dlgObj;
            java.lang.reflect.Method open = org.eclipse.jface.window.Window.class.getMethod("open");
            open.invoke(dlgObj);
            // החלון לא חוסם (כמו בתוך Eclipse) — ממשיכים לטפל באירועים עד שהתסריט סוגר אותו
            while (dlgShell == null || !dlgShell.isDisposed()) if (!display.readAndDispatch()) display.sleep();
        }
        else if (kind.equals("submit")) {
            // החלון "שליחת תרגיל" מול שרת (DSVIZ_WORKER_URL — למשל wrangler dev מקומי עם Gemini מדומה)
            String url = System.getenv("DSVIZ_WORKER_URL");
            Class<?> wcc = Class.forName("il.lamerhav.dsviz.WorkerClient");
            java.lang.reflect.Constructor<?> wc = wcc.getDeclaredConstructor(String.class, String.class);
            wc.setAccessible(true);
            Object client = url == null || url.isEmpty() ? null : wc.newInstance(url, "");
            SubmitDialog sd = (SubmitDialog) SubmitDialog.class.getConstructor(Shell.class, java.util.List.class, String.class,
                    org.eclipse.core.resources.IProject.class, wcc).newInstance(parent, new ArrayList<Object>(), "DataStructures", null, client);
            dialog = sd;
            int rc = sd.open();
            System.out.println("submit rc=" + rc + " exercise=" + (sd.exercise() != null));
            if (sd.exercise() != null) {
                java.lang.reflect.Method w = Class.forName("il.lamerhav.dsviz.Json").getDeclaredMethod("write", Object.class);
                w.setAccessible(true);
                Files.write(new File(out, "exercise.json").toPath(), ((String) w.invoke(null, sd.exercise())).getBytes(StandardCharsets.UTF_8));
            }
        }
        else new ExamplesDialog(parent).open();
        display.dispose();
    }

    static List<Control> all(Control c, List<Control> out) {
        out.add(c);
        if (c instanceof Composite) for (Control k : ((Composite) c).getChildren()) all(k, out);
        return out;
    }

    static String textOf(Control c) {
        try {
            if (c.getClass().getSimpleName().equals("Chip") || c.getClass().getSimpleName().equals("Round")) {
                Field f = c.getClass().getDeclaredField("text");
                f.setAccessible(true);
                return (String) f.get(c);
            }
        } catch (Exception ignored) {}
        if (c instanceof Button) return ((Button) c).getText();
        if (c instanceof Label) return ((Label) c).getText();
        if (c instanceof Text) return ((Text) c).getText();
        if (c instanceof Combo) return ((Combo) c).getText();
        if (c instanceof Spinner) return String.valueOf(((Spinner) c).getSelection());
        return null;
    }

    static int run(Display d, String line, File out) throws Exception {
        String[] p = line.split(" ", 2);
        String cmd = p[0], arg = p.length > 1 ? p[1] : "";
        List<Control> cs = all(dlgShell, new ArrayList<Control>());
        switch (cmd) {
            case "chip": {
                for (Control c : cs) if (c.getClass().getSimpleName().equals("Chip") && arg.equals(textOf(c))) { c.notifyListeners(SWT.Selection, new Event()); return 350; }
                throw new IllegalArgumentException("chip not found: " + arg);
            }
            case "check": {
                for (Control c : cs) if (c instanceof Button && (((Button) c).getStyle() & SWT.CHECK) != 0 && ((Button) c).getText().contains(arg)) {
                    ((Button) c).setSelection(!((Button) c).getSelection());
                    c.notifyListeners(SWT.Selection, new Event());
                    return 350;
                }
                throw new IllegalArgumentException("check not found: " + arg);
            }
            case "spin": {
                for (Control c : cs) if (c instanceof Spinner) { ((Spinner) c).setSelection(Integer.parseInt(arg)); c.notifyListeners(SWT.Modify, new Event()); return 350; }
                throw new IllegalArgumentException("no spinner");
            }
            case "text": {
                String[] q = arg.split(" ", 2);
                int idx = Integer.parseInt(q[0]), n = 0;
                for (Control c : cs) if (c instanceof Text) { if (n++ == idx) { ((Text) c).setText(q.length > 1 ? q[1] : ""); return 350; } }
                throw new IllegalArgumentException("text not found: " + idx);
            }
            case "combo": {
                String[] q = arg.split(" ");
                int idx = Integer.parseInt(q[0]), n = 0;
                for (Control c : cs) if (c instanceof Combo) { if (n++ == idx) { ((Combo) c).select(Integer.parseInt(q[1])); c.notifyListeners(SWT.Selection, new Event()); return 350; } }
                throw new IllegalArgumentException("combo not found");
            }
            case "shot": {
                while (d.readAndDispatch()) { }
                dlgShell.layout(true, true);
                while (d.readAndDispatch()) { }
                Rectangle b = dlgShell.getBounds();
                Image img = new Image(d, b.width, b.height);
                GC gc = new GC(d);
                gc.copyArea(img, b.x, b.y);
                gc.dispose();
                ImageLoader l = new ImageLoader();
                l.data = new ImageData[] { img.getImageData() };
                l.save(new File(out, arg + ".png").getPath(), SWT.IMAGE_PNG);
                img.dispose();
                StringBuilder js = new StringBuilder("{\"w\":" + b.width + ",\"h\":" + b.height + ",\"controls\":[");
                boolean first = true;
                int ti = 0;
                for (Control c : all(dlgShell, new ArrayList<Control>())) {
                    if (!c.isVisible()) continue;
                    String type = c.getClass().getSimpleName();
                    String t = textOf(c);
                    if (t == null && !(c instanceof Text)) continue;
                    Point o = c.toDisplay(0, 0);
                    Point s = c.getSize();
                    js.append(first ? "" : ",").append("{\"type\":\"").append(type).append("\",\"text\":").append(q(t))
                      .append(c instanceof Text ? ",\"index\":" + (ti++) : "")
                      .append(c instanceof Text ? ",\"hint\":" + q(((Text) c).getMessage()) : "")
                      .append(",\"x\":").append(((c.getStyle() & SWT.RIGHT_TO_LEFT) != 0 ? o.x - s.x : o.x) - b.x).append(",\"y\":").append(o.y - b.y).append(",\"w\":").append(s.x).append(",\"h\":").append(s.y)
                      .append(",\"enabled\":").append(c.getEnabled()).append("}");
                    first = false;
                }
                js.append("]}");
                Files.write(new File(out, arg + ".json").toPath(), js.toString().getBytes(StandardCharsets.UTF_8));
                return 350;
            }
            case "wait": return Integer.parseInt(arg);
            case "click": {   // כפתור מעוגל (Round) לפי הטקסט שלו
                for (Control c : cs) if (c.getClass().getSimpleName().equals("Round") && arg.equals(textOf(c))) {
                    Event e = new Event(); e.button = 1; e.x = 5; e.y = 5;
                    c.notifyListeners(SWT.MouseDown, e);
                    c.notifyListeners(SWT.MouseUp, e);
                    return 350;
                }
                throw new IllegalArgumentException("button not found: " + arg);
            }
            case "addfile": {   // כמו בחירה בחלון "בחירת קבצים"
                java.lang.reflect.Method m = dialog.getClass().getDeclaredMethod("addFiles", java.util.List.class);
                m.setAccessible(true);
                m.invoke(dialog, java.util.Arrays.asList(new File(arg)));
                return 350;
            }
            case "pasteimg":   // תמונה ללוח, ואז Ctrl+V אמיתי מהמקלדת
            case "pastetext": {
                org.eclipse.swt.dnd.Clipboard cb = new org.eclipse.swt.dnd.Clipboard(d);
                if (cmd.equals("pasteimg")) cb.setContents(new Object[] { new ImageData(arg) }, new org.eclipse.swt.dnd.Transfer[] { org.eclipse.swt.dnd.ImageTransfer.getInstance() });
                else cb.setContents(new Object[] { arg }, new org.eclipse.swt.dnd.Transfer[] { org.eclipse.swt.dnd.TextTransfer.getInstance() });
                cb.dispose();
                dlgShell.forceActive();
                dlgShell.forceFocus();
                while (d.readAndDispatch()) { }
                Event k = new Event();
                k.type = SWT.KeyDown; k.keyCode = SWT.CTRL; d.post(k);
                k = new Event(); k.type = SWT.KeyDown; k.character = 'v'; k.keyCode = 'v'; d.post(k);
                k = new Event(); k.type = SWT.KeyUp; k.character = 'v'; k.keyCode = 'v'; d.post(k);
                k = new Event(); k.type = SWT.KeyUp; k.keyCode = SWT.CTRL; d.post(k);
                return 900;
            }
            default: throw new IllegalArgumentException("unknown: " + line);
        }
    }

    static String q(String s) {
        if (s == null) return "null";
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\"";
    }
}
