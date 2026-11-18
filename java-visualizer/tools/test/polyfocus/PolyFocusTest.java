package il.lamerhav.dsviz;

import java.awt.Component;
import java.awt.KeyboardFocusManager;
import java.awt.Robot;
import java.awt.event.KeyEvent;
import java.io.File;
import java.util.List;
import java.util.concurrent.Callable;

import javax.imageio.ImageIO;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

/**
 * "הוסף עצם" (PolyPanels.ObjectPanel — Visual Studio, IntelliJ, Eclipse): הקלדה אמיתית (Robot, מקלדת) בשדה של תא במערך —
 * הפוקוס נשאר בשדה בכל הקשה, והטקסט מגיע שלם; גם במצב "עצם אחד" בשדות הבנאי, ואחרי שינוי גודל המערך (השורות נבנות מחדש —
 * השדה שבו מקלידים מקבל את הפוקוס בחזרה). התקלה שתוקנה: השורות נבנו מחדש בכל הקשה, והשדה איבד את הפוקוס.
 *   java ... PolyFocusTest <תיקיית צילומים>   (ב-Xvfb)
 */
public class PolyFocusTest {
    static int pass, fail;

    static void check(String name, boolean ok, Object got) {
        if (ok) pass++; else fail++;
        System.out.println((ok ? "  ok   " : "  FAIL ") + name + (ok ? "" : "  ← " + got));
    }

    static <T> T ui(Callable<T> c) throws Exception {
        final Object[] r = new Object[2];
        SwingUtilities.invokeAndWait(() -> { try { r[0] = c.call(); } catch (Exception e) { r[1] = e; } });
        if (r[1] != null) throw (Exception) r[1];
        @SuppressWarnings("unchecked") T t = (T) r[0];
        return t;
    }

    static Component owner() throws Exception {
        return ui(() -> KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner());
    }

    static Robot robot;

    /**
     * מחכה שהאירוע יגיע לחלון. בלי Robot.waitForIdle: ב-Xvfb בלי מנהל חלונות הוא לוקח שניות לכל הקשה (הבדיקה כולה ~100 שניות)
     */
    static void settle() {
        java.awt.Toolkit.getDefaultToolkit().sync();
        try {
            SwingUtilities.invokeAndWait(() -> { });
            Thread.sleep(30);
            SwingUtilities.invokeAndWait(() -> { });
        } catch (Exception ignored) {
            // ממשיכים
        }
    }

    static void key(int code, boolean shift) {
        if (shift) robot.keyPress(KeyEvent.VK_SHIFT);
        robot.keyPress(code);
        robot.keyRelease(code);
        if (shift) robot.keyRelease(KeyEvent.VK_SHIFT);
        settle();
        robot.delay(40);
    }

    /** מקליד (אותיות קטנות, ספרות, פסיק, רווח) ובודק אחרי כל הקשה שהפוקוס עדיין בשדה. מחזיר כמה הקשות הפוקוס לא היה בו */
    static int type(JTextField f, String s) throws Exception {
        int lost = 0;
        for (char ch : s.toCharArray()) {
            int code = ch == ',' ? KeyEvent.VK_COMMA : ch == ' ' ? KeyEvent.VK_SPACE : KeyEvent.getExtendedKeyCodeForChar(ch);
            key(code, false);
            Thread.sleep(60);
            if (owner() != f) lost++;
        }
        return lost;
    }

    public static void main(String[] a) throws Exception {
        File shots = new File(a.length > 0 ? a[0] : "polyfocus-shots");
        shots.mkdirs();
        robot = new Robot();
        robot.setAutoDelay(15);
        String src = "public class Person1 {\n    private String name;\n    private String id;\n    public Person1(String name, String id) {\n        this.name = name;\n        this.id = id;\n    }\n}\n"
                + "public class Student : Person1 {\n    private int grade;\n    public Student(string name, string id, int grade) : base(name, id) {\n        this.grade = grade;\n    }\n}\n";
        final List<ClassGen.Known> known = ClassGen.models(src, "Program.cs");
        final PolyPanels.ObjectPanel[] panel = new PolyPanels.ObjectPanel[1];
        final JFrame[] frame = new JFrame[1];
        SwingUtilities.invokeAndWait(() -> {
            panel[0] = new PolyPanels.ObjectPanel(ClassGen.CS, known, null, null);
            frame[0] = new JFrame("הוסף עצם — בדיקה");
            frame[0].setContentPane(new JScrollPane(panel[0]));
            frame[0].setSize(760, 700);
            frame[0].setLocation(40, 40);
            frame[0].setVisible(true);
            frame[0].toFront();
        });
        Thread.sleep(800);
        final PolyPanels.ObjectPanel p = panel[0];

        // 1. מערך: Person1[] arr בגודל 4; בתאים 0 ו-1 — Person1
        SwingUtilities.invokeAndWait(() -> {
            p.name.setText("arr");
            p.many.doClick();
        });
        Thread.sleep(300);
        for (int i = 0; i < 2; i++) {
            final int k = i;
            SwingUtilities.invokeAndWait(() -> {
                @SuppressWarnings("unchecked") JComboBox<Object> b = (JComboBox<Object>) p.cellRows.get(k)[1];
                for (int j = 0; j < b.getItemCount(); j++) if (PolyPanels.nameOf(b.getItemAt(j)).equals("Person1")) b.setSelectedIndex(j);
            });
        }
        Thread.sleep(300);
        final JTextField c0 = ui(() -> (JTextField) p.cellRows.get(0)[2]), c1 = ui(() -> (JTextField) p.cellRows.get(1)[2]);
        check("תאים 0 ו-1: Person1 — השדות פעילים", ui(() -> c0.isEnabled() && c1.isEnabled()), "");
        // לחיצת עכבר אמיתית על השדה של תא 0 (כמו תלמיד), ואז הקלדה
        java.awt.Point at = ui(() -> { java.awt.Point q = c0.getLocationOnScreen(); return new java.awt.Point(q.x + 20, q.y + c0.getHeight() / 2); });
        robot.mouseMove(at.x, at.y);
        robot.mousePress(java.awt.event.InputEvent.BUTTON1_DOWN_MASK);
        robot.mouseRelease(java.awt.event.InputEvent.BUTTON1_DOWN_MASK);
        settle();
        Thread.sleep(400);
        if (owner() != c0) { SwingUtilities.invokeAndWait(c0::requestFocusInWindow); Thread.sleep(300); }   // בלי מנהל חלונות ב-Xvfb
        check("הלחיצה על התא — הפוקוס בשדה של תא 0", owner() == c0, owner());
        int lost = type(c0, "naor,123");
        String t0 = ui(c0::getText);
        check("מקלידים 'naor,123' בתא 0 — הפוקוס לא עזב את השדה באף הקשה (" + lost + " הקשות בלי פוקוס)", lost == 0, lost);
        check("הטקסט הגיע שלם: '" + t0 + "'", "naor,123".equals(t0), t0);
        String code = ui(() -> p.preview.getText());
        check("הקוד מתעדכן תוך כדי: arr[0] = new Person1(\"naor\", \"123\");", code.contains("arr[0] = new Person1(\"naor\", \"123\");"), code);
        // Tab לתא הבא? לא — עוברים בלחיצה, כמו בצילום של המשתמש: תא 1, "aa,12"
        at = ui(() -> { java.awt.Point q = c1.getLocationOnScreen(); return new java.awt.Point(q.x + 20, q.y + c1.getHeight() / 2); });
        robot.mouseMove(at.x, at.y);
        robot.mousePress(java.awt.event.InputEvent.BUTTON1_DOWN_MASK);
        robot.mouseRelease(java.awt.event.InputEvent.BUTTON1_DOWN_MASK);
        settle();
        Thread.sleep(400);
        if (owner() != c1) { SwingUtilities.invokeAndWait(c1::requestFocusInWindow); Thread.sleep(300); }
        lost = type(c1, "aa,12");
        check("מקלידים 'aa,12' בתא 1 — הפוקוס נשאר (" + lost + ")", lost == 0 && "aa,12".equals(ui(c1::getText)), ui(c1::getText));
        ImageIO.write(shot(frame[0]), "png", new File(shots, "polyfocus-array.png"));
        // מחיקה (Backspace) — גם היא לא מוציאה מהשדה
        key(KeyEvent.VK_BACK_SPACE, false);
        Thread.sleep(80);
        check("Backspace בתא 1 — הפוקוס נשאר, 'aa,1'", owner() == c1 && "aa,1".equals(ui(c1::getText)), ui(c1::getText));

        // 2. שינוי גודל המערך כשהפוקוס בתא: השורות נבנות מחדש — והשדה מקבל את הפוקוס בחזרה, עם הסמן במקום
        SwingUtilities.invokeAndWait(() -> p.size.setValue(6));
        Thread.sleep(500);
        check("אחרי שינוי הגודל ל-6: שש שורות, והפוקוס חזר לשדה של תא 1", ui(() -> p.cells.getComponentCount()) == 6 && owner() == c1, ui(() -> p.cells.getComponentCount()) + " / " + owner());
        lost = type(c1, "9");
        check("ממשיכים להקליד באותו מקום — 'aa,19'", lost == 0 && "aa,19".equals(ui(c1::getText)), ui(c1::getText));

        // 3. עצם אחד: שדות הבנאי — הקלדה בלי לאבד פוקוס
        SwingUtilities.invokeAndWait(() -> p.one.doClick());
        Thread.sleep(400);
        final JTextField arg0 = ui(() -> p.args.isEmpty() ? null : p.args.get(0));
        check("עצם אחד: שדות לפרמטרים של הבנאי", arg0 != null, "");
        if (arg0 != null) {
            SwingUtilities.invokeAndWait(arg0::requestFocusInWindow);
            Thread.sleep(300);
            lost = type(arg0, "dana");
            check("מקלידים בפרמטר הראשון — הפוקוס נשאר, 'dana'", lost == 0 && ui(arg0::getText).endsWith("dana"), ui(arg0::getText));
        }
        SwingUtilities.invokeAndWait(() -> frame[0].dispose());
        System.out.println("הוסף עצם — פוקוס בהקלדה: " + pass + " עברו, " + fail + " נכשלו");
        System.exit(fail == 0 ? 0 : 1);
    }

    static java.awt.image.BufferedImage shot(final JFrame f) throws Exception {
        return ui(() -> {
            java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(f.getWidth(), f.getHeight(), java.awt.image.BufferedImage.TYPE_INT_RGB);
            f.paint(img.getGraphics());
            return img;
        });
    }
}
