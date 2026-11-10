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
 * מצב viewer: חלון ההרצה הוויזואלית (TraceViewer שמפעיל dsviz-cs).
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
            String mode = cmd.contains("CsTool submit") ? "submit" : cmd.contains("BankVs bank") ? "bank" : cmd.contains("TraceViewer") ? "viewer" : null;
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
