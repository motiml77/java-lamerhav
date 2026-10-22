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
                try { run(display, line, out); } catch (Exception e) { e.printStackTrace(); System.exit(2); }
                display.timerExec(350, step[0]);
                return;
            }
            dlgShell.close();
        };
        display.timerExec(1200, step[0]);
        if (kind.equals("ex")) new ExerciseDialog(parent, new ArrayList<org.eclipse.jdt.core.IJavaProject>(), "DataStructures", null, preset).open();
        else if (kind.equals("ins")) new InsertDialog(parent, preset, fDoc, fOffset, "        ").open();
        else if (kind.equals("help")) new HelpDialog(parent).open();
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

    static void run(Display d, String line, File out) throws Exception {
        String[] p = line.split(" ", 2);
        String cmd = p[0], arg = p.length > 1 ? p[1] : "";
        List<Control> cs = all(dlgShell, new ArrayList<Control>());
        switch (cmd) {
            case "chip": {
                for (Control c : cs) if (c.getClass().getSimpleName().equals("Chip") && arg.equals(textOf(c))) { c.notifyListeners(SWT.Selection, new Event()); return; }
                throw new IllegalArgumentException("chip not found: " + arg);
            }
            case "check": {
                for (Control c : cs) if (c instanceof Button && (((Button) c).getStyle() & SWT.CHECK) != 0 && ((Button) c).getText().contains(arg)) {
                    ((Button) c).setSelection(!((Button) c).getSelection());
                    c.notifyListeners(SWT.Selection, new Event());
                    return;
                }
                throw new IllegalArgumentException("check not found: " + arg);
            }
            case "spin": {
                for (Control c : cs) if (c instanceof Spinner) { ((Spinner) c).setSelection(Integer.parseInt(arg)); c.notifyListeners(SWT.Modify, new Event()); return; }
                throw new IllegalArgumentException("no spinner");
            }
            case "text": {
                String[] q = arg.split(" ", 2);
                int idx = Integer.parseInt(q[0]), n = 0;
                for (Control c : cs) if (c instanceof Text) { if (n++ == idx) { ((Text) c).setText(q.length > 1 ? q[1] : ""); return; } }
                throw new IllegalArgumentException("text not found: " + idx);
            }
            case "combo": {
                String[] q = arg.split(" ");
                int idx = Integer.parseInt(q[0]), n = 0;
                for (Control c : cs) if (c instanceof Combo) { if (n++ == idx) { ((Combo) c).select(Integer.parseInt(q[1])); c.notifyListeners(SWT.Selection, new Event()); return; } }
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
                return;
            }
            default: throw new IllegalArgumentException("unknown: " + line);
        }
    }

    static String q(String s) {
        if (s == null) return "null";
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\"";
    }
}
