import java.awt.Component;
import java.awt.Container;
import java.awt.Point;
import java.awt.Window;
import java.awt.image.BufferedImage;
import java.io.File;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

import javax.imageio.ImageIO;
import javax.swing.AbstractButton;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JRootPane;
import javax.swing.JSpinner;
import javax.swing.JToggleButton;
import javax.swing.SwingUtilities;
import javax.swing.text.JTextComponent;

/**
 * כמו DlgScript (Eclipse), לחלונות של Visual Studio / C# (Swing): מריץ כלי (למשל CsTool submit ...), ובחלון שנפתח מבצע תסריט,
 * ושומר בכל "shot" צילום + מיקומי הרכיבים (JSON, באותו פורמט) — לסרטוני ההדרכה ולבדיקות.
 *
 *   SwingScript <script.txt> <out-dir> <main-class> [args...]
 * פקודות: shot <שם> · click <טקסט> · chip <טקסט> · check <חלק> · spin <מספר> · text <אינדקס> <ערך> · combo <אינדקס> <פריט>
 *         addfile <קובץ> · pasteimg <קובץ> · select <אינדקס> (ברשימה) · waitwin <חלק מהכותרת> · wait <ms> · close
 *         progress <טקסט> (שליחת תרגיל: הודעה מהבנייה, כאילו הגיעה מהשרת — לצילום של תצוגת הבנייה)
 */
public class SwingScript {
    static Window win;

    public static void main(String[] a) throws Exception {
        List<String> script = Files.readAllLines(Paths.get(a[0]), StandardCharsets.UTF_8);
        File out = new File(a[1]);
        out.mkdirs();
        Class<?> main = Class.forName(a[2]);
        String[] args = Arrays.copyOfRange(a, 3, a.length);
        Thread t = new Thread(() -> {
            try {
                Method m = main.getMethod("main", String[].class);
                m.invoke(null, (Object) args);
            } catch (Exception e) {
                e.printStackTrace();
                System.exit(3);
            }
        }, "tool");
        t.setDaemon(true);
        t.start();
        win = waitWindow(null, 20000);
        if (win == null) { System.err.println("no window"); System.exit(2); }
        Thread.sleep(900);
        for (String line : script) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            int sp = line.indexOf(' ');
            String cmd = sp < 0 ? line : line.substring(0, sp), arg = sp < 0 ? "" : line.substring(sp + 1);
            long delay = run(cmd, arg, out);
            Thread.sleep(delay);
        }
        System.exit(0);
    }

    static Window waitWindow(String title, long ms) throws Exception {
        long end = System.currentTimeMillis() + ms;
        while (System.currentTimeMillis() < end) {
            for (Window w : Window.getWindows()) {
                if (!w.isShowing()) continue;
                String t = w instanceof JDialog ? ((JDialog) w).getTitle() : w instanceof JFrame ? ((JFrame) w).getTitle() : "";
                if (title == null ? w != win : t != null && t.contains(title)) return w;
            }
            Thread.sleep(150);
        }
        return null;
    }

    static long run(String cmd, String arg, File out) throws Exception {
        final Object[] res = { 300L };
        if (cmd.equals("wait")) return Long.parseLong(arg);
        if (cmd.equals("waitwin")) {
            Window w = waitWindow(arg, 120000);
            if (w == null) throw new IllegalStateException("window not found: " + arg);
            win = w;
            return 900;
        }
        SwingUtilities.invokeAndWait(() -> {
            try {
                res[0] = exec(cmd, arg, out);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        return (Long) res[0];
    }

    @SuppressWarnings("unchecked")
    static long exec(String cmd, String arg, File out) throws Exception {
        JRootPane rp = win instanceof JDialog ? ((JDialog) win).getRootPane() : ((JFrame) win).getRootPane();
        switch (cmd) {
            case "shot": {
                Container c = rp.getContentPane();
                c.validate();
                BufferedImage img = new BufferedImage(c.getWidth(), c.getHeight(), BufferedImage.TYPE_INT_RGB);
                java.awt.Graphics2D g = img.createGraphics();
                c.paint(g);
                g.dispose();
                ImageIO.write(img, "png", new File(out, arg + ".png"));
                StringBuilder js = new StringBuilder("{\"w\":" + c.getWidth() + ",\"h\":" + c.getHeight() + ",\"controls\":[");
                boolean first = true;
                int ti = 0;
                for (Component k : all(c, new ArrayList<Component>())) {
                    if (!k.isShowing()) continue;
                    String type = type(k), text = text(k);
                    if (type == null) continue;
                    Point o = SwingUtilities.convertPoint(k.getParent(), k.getLocation(), c);
                    js.append(first ? "" : ",").append("{\"type\":\"").append(type).append("\",\"text\":").append(q(text))
                      .append(type.equals("Text") ? ",\"index\":" + (ti++) : "")
                      .append(",\"x\":").append(o.x).append(",\"y\":").append(o.y).append(",\"w\":").append(k.getWidth()).append(",\"h\":").append(k.getHeight())
                      .append(",\"enabled\":").append(k.isEnabled()).append("}");
                    first = false;
                }
                js.append("]}");
                Files.write(new File(out, arg + ".json").toPath(), js.toString().getBytes(StandardCharsets.UTF_8));
                return 300;
            }
            case "click":
            case "chip": {
                for (Component k : all(rp, new ArrayList<Component>()))
                    if (k instanceof AbstractButton && !(k instanceof JCheckBox) && arg.equals(((AbstractButton) k).getText()) && k.isShowing()) {
                        ((AbstractButton) k).doClick();
                        return 500;
                    }
                throw new IllegalStateException("button not found: " + arg);
            }
            case "check": {
                for (Component k : all(rp, new ArrayList<Component>()))
                    if (k instanceof JCheckBox && ((JCheckBox) k).getText().contains(arg)) {
                        ((JCheckBox) k).doClick();
                        return 400;
                    }
                throw new IllegalStateException("checkbox not found: " + arg);
            }
            case "spin": {
                for (Component k : all(rp, new ArrayList<Component>())) if (k instanceof JSpinner) { ((JSpinner) k).setValue(Integer.parseInt(arg)); return 400; }
                throw new IllegalStateException("no spinner");
            }
            case "text": {
                int sp = arg.indexOf(' ');
                int idx = Integer.parseInt(sp < 0 ? arg : arg.substring(0, sp));
                String v = sp < 0 ? "" : arg.substring(sp + 1);
                int i = 0;
                for (Component k : all(rp, new ArrayList<Component>()))
                    if (k instanceof JTextComponent && ((JTextComponent) k).isEditable() && k.isShowing() && i++ == idx) { ((JTextComponent) k).setText(v); return 400; }
                throw new IllegalStateException("text not found: " + idx);
            }
            case "combo": {
                int sp = arg.indexOf(' ');
                int idx = Integer.parseInt(arg.substring(0, sp)), i = 0;
                for (Component k : all(rp, new ArrayList<Component>()))
                    if (k instanceof JComboBox && i++ == idx) { ((JComboBox<Object>) k).setSelectedItem(arg.substring(sp + 1)); return 400; }
                throw new IllegalStateException("combo not found");
            }
            case "addfile":
                ((Consumer<File>) rp.getClientProperty("dsviz.addFile")).accept(new File(arg));
                return 500;
            case "pasteimg":
                ((Consumer<File>) rp.getClientProperty("dsviz.pasteImage")).accept(new File(arg));
                return 500;
            case "progress":
                ((Consumer<String>) rp.getClientProperty("dsviz.progress")).accept(arg);
                return 300;
            case "select":
                ((JList<?>) rp.getClientProperty("dsviz.list")).setSelectedIndex(Integer.parseInt(arg));
                return 500;
            case "close":
                win.dispose();
                return 300;
            default:
                throw new IllegalStateException("unknown command " + cmd);
        }
    }

    /** הסוג באותם שמות כמו ב-DlgScript (המנוע של הסרטונים מחפש לפיהם) */
    static String type(Component k) {
        if (k instanceof JCheckBox) return "Button";
        if (k instanceof JToggleButton) return "Chip";
        if (k instanceof JButton) return "Round";
        if (k instanceof JTextComponent && ((JTextComponent) k).isEditable()) return "Text";
        if (k instanceof JTextComponent && ((JComponent) k).getClientProperty("dsviz.label") != null) return "Label";   // טקסט של RtlDialog
        if (k instanceof JSpinner) return "Spinner";
        if (k instanceof JComboBox) return "Combo";
        if (k instanceof JList) return "List";
        if (k instanceof JLabel && ((JLabel) k).getText() != null && !((JLabel) k).getText().trim().isEmpty()) return "Label";
        return null;
    }

    static String text(Component k) {
        if (k instanceof AbstractButton) return ((AbstractButton) k).getText();
        if (k instanceof JLabel) return ((JLabel) k).getText();
        if (k instanceof JTextComponent) return ((JTextComponent) k).getText();
        return "";
    }

    static List<Component> all(Component c, List<Component> acc) {
        acc.add(c);
        if (c instanceof JSpinner) return acc;   // בלי השדה שבתוכו
        if (c instanceof Container) for (Component k : ((Container) c).getComponents()) all(k, acc);
        return acc;
    }

    static String q(String s) {
        if (s == null) return "null";
        StringBuilder b = new StringBuilder("\"");
        for (char ch : s.toCharArray()) {
            if (ch == '"' || ch == '\\') b.append('\\').append(ch);
            else if (ch == '\n') b.append("\\n");
            else if (ch < 32) b.append(' ');
            else b.append(ch);
        }
        return b.append('"').toString();
    }
}
