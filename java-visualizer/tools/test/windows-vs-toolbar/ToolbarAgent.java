import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

import javax.swing.SwingUtilities;

/**
 * Java agent לבדיקת הסרגל של Visual Studio (toolbar-e2e.ps1): נטען לתוך תהליך ה-javaw שהסרגל מפעיל (דרך JAVA_TOOL_OPTIONS שיורש
 * Visual Studio), ומריץ בחלון האמיתי את תסריט SwingScript (אותן פקודות: click / pasteimg / waittext / shot ...).
 *   -javaagent:agent.jar=<תיקיית בקרה>
 * בתיקיית הבקרה: <מצב>.script.txt (מצב = submit / bank). השורה הראשונה אפשרית "#access=off" (כמו -Ddsviz.access=off — בלי מסך הכניסה).
 * פקודות נוספות: mark <שם> (כותב mark-<שם>.txt וממתין ל-ack-<שם>.txt — הסקריפט מצלם את המסך האמיתי) · bankselect <מזהה שאלה>
 * · has <טקסט> / hasnot <טקסט> (בודקים שיש / אין רכיב עם הטקסט בחלון; התוצאה ב-results.txt)
 * · target <טקסט> (המיקום על המסך של הכפתור / הרכיב עם הטקסט — target.txt: x y w h; לסרטון ההדרכה: לאן העכבר הולך).
 * · ptarget <טקסט> / targetrow topic|question <מזהה> (אותו דבר בפיקסלים של המסך, גם לשורות המצוירות של המאגר — הסקריפט לוחץ שם בעכבר אמיתי).
 * · pfield <placeholder> (המיקום בפיקסלים של שדה טקסט ריק עם ה-placeholder הזה — "Student" שם המחלקה, "name" שם השדה; הסקריפט לוחץ ומקליד).
 * מצב viewer: חלון ההרצה הוויזואלית (TraceViewer שמפעיל dsviz-cs). מצב insert: "הוספת מבנה נתונים" (CsTool insert) — וממנו "צור מחלקה".
 * סיום: <מצב>.done.txt, או <מצב>.error.txt עם החריגה.
 */
public class ToolbarAgent {
    static File ctl;

    static void log(String s) {
        try {
            Files.write(new File(ctl, "agent.log").toPath(), (s + "\n").getBytes(StandardCharsets.UTF_8),
                    java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
        } catch (Exception e) { /* ignore */ }
    }

    public static void premain(String args) {
        try {
            ctl = new File(args);
            String cmd = System.getProperty("sun.java.command", "");
            // מ-VS Code: הארגומנטים במשתנה הסביבה DSVIZ_ARGS (BankVs --args-env) — המצב הוא השורה הראשונה
            String envArgs = System.getenv("DSVIZ_ARGS");
            if (cmd.contains("--args-env") && envArgs != null) cmd = cmd.replace("--args-env", envArgs.replace('\n', ' '));
            String mode = cmd.contains("CsTool submit") ? "submit" : cmd.contains("CsTool insert") ? "insert" : cmd.contains("BankVs bank") ? "bank" : cmd.contains("BankVs teacher") ? "teacher" : cmd.contains("TraceViewer") ? "viewer" : null;
            log("jvm: " + cmd + " -> " + mode);
            if (mode == null) return;
            final File script = new File(ctl, mode + ".script.txt");
            if (!script.isFile()) return;
            final List<String> lines = Files.readAllLines(script.toPath(), StandardCharsets.UTF_8);
            Files.move(script.toPath(), new File(ctl, mode + ".script.txt.claimed").toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            if (!lines.isEmpty() && lines.get(0).trim().equals("#access=off")) System.setProperty("dsviz.access", "off");
            final String m = mode;
            Thread t = new Thread(() -> runScript(m, lines), "toolbar-agent");
            t.setDaemon(true);
            t.start();
        } catch (Throwable e) {
            log("premain failed: " + e);
        }
    }

    static void runScript(String mode, List<String> lines) {
        File shots = new File(ctl, "shots-" + mode);
        shots.mkdirs();
        try {
            for (String line : lines) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                int sp = line.indexOf(' ');
                String cmd = sp < 0 ? line : line.substring(0, sp), arg = sp < 0 ? "" : line.substring(sp + 1);
                log(mode + "> " + line);
                long delay;
                switch (cmd) {
                    case "mark":
                        delay = mark(arg);
                        break;
                    case "bankselect":
                        delay = bankSelect(arg);
                        break;
                    case "has":
                    case "hasnot":
                        delay = has(cmd, arg);
                        break;
                    case "target":
                        delay = target(arg);
                        break;
                    case "clicktarget":
                        delay = clickTarget(arg);
                        break;
                    case "ptarget":
                        delay = physTarget(arg);
                        break;
                    case "targetrow":
                        delay = targetRow(arg);
                        break;
                    case "pfield":
                        delay = physField(arg);
                        break;
                    default:
                        delay = SwingScript.run(cmd, arg, shots);
                }
                Thread.sleep(delay);
            }
            Files.write(new File(ctl, mode + ".done.txt").toPath(), "ok".getBytes(StandardCharsets.UTF_8));
        } catch (Throwable e) {
            StringWriter sw = new StringWriter();
            e.printStackTrace(new PrintWriter(sw));
            log(mode + " error: " + sw);
            try { Files.write(new File(ctl, mode + ".error.txt").toPath(), sw.toString().getBytes(StandardCharsets.UTF_8)); } catch (Exception x) { /* ignore */ }
        }
    }

    static long mark(String name) throws Exception {
        Files.write(new File(ctl, "mark-" + name + ".txt").toPath(), "x".getBytes(StandardCharsets.UTF_8));
        File ack = new File(ctl, "ack-" + name + ".txt");
        for (int i = 0; i < 100 && !ack.exists(); i++) Thread.sleep(200);
        return 200;
    }

    static long bankSelect(String id) throws Exception {
        final Exception[] err = { null };
        SwingUtilities.invokeAndWait(() -> {
            try {
                Class<?> c = Class.forName("il.lamerhav.dsviz.BankVsWindow");
                java.lang.reflect.Field f = c.getDeclaredField("current");
                f.setAccessible(true);
                Object w = f.get(null);
                Method s = c.getDeclaredMethod("select", String.class);
                s.setAccessible(true);
                s.invoke(w, id);
            } catch (Exception e) {
                err[0] = e;
            }
        });
        if (err[0] != null) throw err[0];
        return 900;
    }

    /** target <טקסט>: המיקום על המסך של הרכיב — קודם כפתור שהטקסט שלו בדיוק זה (אותו כפתור ש-click לוחץ עליו), אחרת רכיב שמכיל אותו */
    static long target(String text) throws Exception {
        final java.awt.Rectangle[] r = { null };
        SwingUtilities.invokeAndWait(() -> {
            java.awt.Container root = ((javax.swing.RootPaneContainer) SwingScript.win).getRootPane();
            java.util.List<java.awt.Component> all = SwingScript.all(root, new java.util.ArrayList<java.awt.Component>());
            for (java.awt.Component k : all)
                if (r[0] == null && k.isShowing() && k instanceof javax.swing.AbstractButton && !(k instanceof javax.swing.JCheckBox)
                        && text.equals(((javax.swing.AbstractButton) k).getText()))
                    r[0] = new java.awt.Rectangle(k.getLocationOnScreen(), k.getSize());
            for (java.awt.Component k : all) {
                if (r[0] != null) break;
                String t = k.isShowing() ? SwingScript.text(k) : null;
                if (t != null && t.contains(text)) r[0] = new java.awt.Rectangle(k.getLocationOnScreen(), k.getSize());
            }
        });
        String line = r[0] == null ? "" : r[0].x + " " + r[0].y + " " + r[0].width + " " + r[0].height;
        Files.write(new File(ctl, "target.txt").toPath(), line.getBytes(StandardCharsets.UTF_8));
        log("target " + text + " -> " + line);
        return 50;
    }

    /** המלבן בפיקסלים של המסך (כמו ש-SendInput ו-UI Automation רואים): Java מחזירה קואורדינטות "לוגיות" — כשהתצוגה מוגדלת (125% / 150%) מכפילים */
    static java.awt.Rectangle phys(java.awt.Component k, java.awt.Rectangle r) {
        try {
            java.awt.geom.AffineTransform t = k.getGraphicsConfiguration().getDefaultTransform();
            double sx = t.getScaleX(), sy = t.getScaleY();
            return new java.awt.Rectangle((int) Math.round(r.x * sx), (int) Math.round(r.y * sy), (int) Math.round(r.width * sx), (int) Math.round(r.height * sy));
        } catch (Exception e) {
            return r;
        }
    }

    static void writeTarget(String what, java.awt.Rectangle r) throws Exception {
        String line = r == null ? "" : r.x + " " + r.y + " " + r.width + " " + r.height;
        Files.write(new File(ctl, "target.txt").toPath(), line.getBytes(StandardCharsets.UTF_8));
        log(what + " -> " + line);
    }

    /**
     * ptarget <טקסט>: כמו target, אבל בפיקסלים של המסך — בשביל לחיצת עכבר אמיתית מבחוץ (dsviz-vs-matrix: הסקריפט לוחץ ב-SendInput
     * בנקודה הזו; ה-agent רק אומר איפה הכפתור). target.txt: x y w h, או ריק אם לא נמצא
     */
    static long physTarget(String text) throws Exception {
        final java.awt.Rectangle[] r = { null };
        SwingUtilities.invokeAndWait(() -> {
            java.awt.Container root = ((javax.swing.RootPaneContainer) SwingScript.win).getRootPane();
            java.util.List<java.awt.Component> all = SwingScript.all(root, new java.util.ArrayList<java.awt.Component>());
            java.awt.Component hit = null;
            for (java.awt.Component k : all)
                if (hit == null && k.isShowing() && k instanceof javax.swing.AbstractButton && !(k instanceof javax.swing.JCheckBox)
                        && text.equals(((javax.swing.AbstractButton) k).getText())) hit = k;
            for (java.awt.Component k : all) {
                if (hit != null) break;
                String t = k.isShowing() ? SwingScript.text(k) : null;
                if (t != null && t.contains(text)) hit = k;
            }
            if (hit != null) r[0] = phys(hit, new java.awt.Rectangle(hit.getLocationOnScreen(), hit.getSize()));
        });
        writeTarget("ptarget " + text, r[0]);
        return 50;
    }

    /** pfield <placeholder>: המיקום בפיקסלים של השדה הראשון (שנראה, ריק) עם ה-placeholder הזה — שם בחלון "צור מחלקה" אין טקסט לחפש */
    static long physField(String placeholder) throws Exception {
        final java.awt.Rectangle[] r = { null };
        SwingUtilities.invokeAndWait(() -> {
            java.awt.Container root = ((javax.swing.RootPaneContainer) SwingScript.win).getRootPane();
            for (java.awt.Component k : SwingScript.all(root, new java.util.ArrayList<java.awt.Component>())) {
                if (r[0] != null || !k.isShowing() || !(k instanceof javax.swing.JTextField)) continue;
                javax.swing.JTextField f = (javax.swing.JTextField) k;
                if (placeholder.equals(f.getClientProperty("JTextField.placeholderText")) && f.getText().isEmpty())
                    r[0] = phys(k, new java.awt.Rectangle(k.getLocationOnScreen(), k.getSize()));
            }
        });
        writeTarget("pfield " + placeholder, r[0]);
        return 50;
    }

    /**
     * targetrow topic|question <מזהה>: המיקום על המסך (בפיקסלים) של שורת הנושא / השאלה בעץ של חלון המאגר. השורות מצוירות (TopicRow /
     * QuestionRow ב-BankVsWindow) ואין להן טקסט שאפשר לחפש — מזהים לפי המזהה של הנושא / השאלה. גוללים אליה אם היא מחוץ לחלק הנראה.
     * target.txt: x y w h של החלק הנראה, או ריק אם אין שורה כזו (למשל הנושא עוד סגור). הלחיצה עצמה — עכבר אמיתי מהסקריפט
     */
    static long targetRow(String arg) throws Exception {
        String[] p = arg.trim().split("\\s+", 2);
        final boolean topic = p[0].equals("topic");
        final String id = p.length > 1 ? p[1].trim() : "";
        final java.awt.Rectangle[] r = { null };
        final String[] seen = { "" };
        SwingUtilities.invokeAndWait(() -> {
            java.awt.Container root = ((javax.swing.RootPaneContainer) SwingScript.win).getRootPane();
            for (java.awt.Component k : SwingScript.all(root, new java.util.ArrayList<java.awt.Component>())) {
                if (r[0] != null || !k.isShowing() || !(k instanceof javax.swing.JComponent)) continue;
                if (!k.getClass().getSimpleName().equals(topic ? "TopicRow" : "QuestionRow")) continue;
                try {
                    java.lang.reflect.Field f = k.getClass().getDeclaredField(topic ? "t" : "q");
                    f.setAccessible(true);
                    Object o = f.get(k);
                    java.lang.reflect.Field idf = o.getClass().getDeclaredField("id");
                    idf.setAccessible(true);
                    String rowId = String.valueOf(idf.get(o));
                    seen[0] += rowId + " ";
                    if (!id.equals(rowId)) continue;
                    javax.swing.JComponent jc = (javax.swing.JComponent) k;
                    jc.scrollRectToVisible(new java.awt.Rectangle(0, 0, jc.getWidth(), jc.getHeight()));
                    java.awt.Rectangle vis = jc.getVisibleRect();
                    java.awt.Point at = jc.getLocationOnScreen();
                    if (vis.width > 0 && vis.height > 0) r[0] = phys(jc, new java.awt.Rectangle(at.x + vis.x, at.y + vis.y, vis.width, vis.height));
                } catch (Exception e) {
                    seen[0] += "(" + e + ") ";
                }
            }
        });
        writeTarget("targetrow " + arg + " [rows: " + seen[0].trim() + "]", r[0]);
        return 150;
    }

    /** clicktarget <טקסט>: לוחצים על אותו רכיב ש-target מוצא (גם כפתור מצויר שאין לו getText שווה בדיוק): כפתור — doClick;
     *  אחרת לחיצת עכבר אמיתית (Robot) באמצע הרכיב — כמו שתלמיד לוחץ */
    static long clickTarget(String text) throws Exception {
        final java.awt.Component[] hit = { null };
        final java.awt.Rectangle[] r = { null };
        SwingUtilities.invokeAndWait(() -> {
            java.awt.Container root = ((javax.swing.RootPaneContainer) SwingScript.win).getRootPane();
            java.util.List<java.awt.Component> all = SwingScript.all(root, new java.util.ArrayList<java.awt.Component>());
            for (java.awt.Component k : all)
                if (hit[0] == null && k.isShowing() && k instanceof javax.swing.AbstractButton && !(k instanceof javax.swing.JCheckBox)
                        && text.equals(((javax.swing.AbstractButton) k).getText())) hit[0] = k;
            for (java.awt.Component k : all) {
                if (hit[0] != null) break;
                String t = k.isShowing() ? SwingScript.text(k) : null;
                if (t != null && t.contains(text)) hit[0] = k;
            }
            if (hit[0] != null) r[0] = new java.awt.Rectangle(hit[0].getLocationOnScreen(), hit[0].getSize());
        });
        if (hit[0] == null) throw new IllegalStateException("clicktarget: not found: " + text);
        java.awt.Component b = hit[0];
        while (b != null && !(b instanceof javax.swing.AbstractButton)) b = b.getParent();
        if (b != null) {
            final javax.swing.AbstractButton btn = (javax.swing.AbstractButton) b;
            SwingUtilities.invokeLater(btn::doClick);
            log("clicktarget " + text + " -> doClick " + btn.getClass().getSimpleName());
        } else {
            java.awt.Robot robot = new java.awt.Robot();
            robot.mouseMove(r[0].x + r[0].width / 2, r[0].y + r[0].height / 2);
            robot.delay(150);
            robot.mousePress(java.awt.event.InputEvent.BUTTON1_DOWN_MASK);
            robot.delay(60);
            robot.mouseRelease(java.awt.event.InputEvent.BUTTON1_DOWN_MASK);
            log("clicktarget " + text + " -> mouse at " + r[0]);
        }
        return 300;
    }

    /** has <טקסט>: האם בחלון הנוכחי יש רכיב (כפתור / תווית) עם הטקסט — נרשם ב-results.txt */
    static long has(String cmd, String text) throws Exception {
        final boolean[] found = { false };
        SwingUtilities.invokeAndWait(() -> {
            java.awt.Container root = ((javax.swing.RootPaneContainer) SwingScript.win).getRootPane();
            for (java.awt.Component k : SwingScript.all(root, new java.util.ArrayList<java.awt.Component>())) {
                String t = k.isShowing() ? SwingScript.text(k) : null;
                if (t != null && t.contains(text)) found[0] = true;
            }
        });
        Files.write(new File(ctl, "results.txt").toPath(), (cmd + " " + text + " = " + found[0] + "\n").getBytes(StandardCharsets.UTF_8),
                java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
        return 100;
    }
}
