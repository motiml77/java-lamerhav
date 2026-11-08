package il.lamerhav.dsviz;

import java.awt.Component;
import java.awt.Container;
import java.awt.Window;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.concurrent.atomic.AtomicInteger;

import javax.imageio.ImageIO;
import javax.swing.AbstractButton;
import javax.swing.JLabel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

/**
 * מסך הכניסה (AccessGate) כמו שהמשתמש רואה אותו — מול השרת המקומי והאמולטור (ראו AccountIt):
 * לוחצים על הכפתורים האמיתיים, מקלידים קוד, ומצלמים כל שלב.
 *   java -cp <classes>:release/dsviz.jar il.lamerhav.dsviz.GateUi http://127.0.0.1:8787 127.0.0.1:9099 <work> <shots>
 */
public class GateUi {
    static File shots;
    static int pass, fail;

    public static void main(String[] a) throws Exception {
        AccountIt.server = a[0];
        AccountIt.auth = a[1];
        File work = new File(a[2]);
        shots = new File(a[3]);
        shots.mkdirs();
        System.setProperty("dsviz.worker", a[0]);
        System.setProperty("dsviz.passKey", Json.str(Json.obj(Json.parse(AccountIt.get(a[0] + "/v1/access/key"))).get("spki")));
        String admin = AccountIt.idToken("motiml77@gmail.com");
        Account.browser = AccountIt::fakeBrowser;
        String teacher = "gate.teacher" + System.nanoTime() + "@test.dev";
        AccountIt.api("POST", "/v1/admin/teacher", admin, "{\"create\":true,\"email\":\"" + teacher + "\",\"name\":\"מורה בדיקה\",\"seats\":1}");
        String code = Json.str(Json.obj(Json.obj(Json.parse(AccountIt.api("GET", "/v1/admin/teacher?email=" + teacher, admin, null))).get("teacher")).get("code"));
        check("קוד מורה של 4 תווים (" + code + ")", code.matches("[23456789A-HJKMNP-Z]{4}"));

        // 1. עמדת מחשב: בלי כניסה → המסך; קוד שגוי → הודעה; הקוד הנכון → המאגר נפתח
        AccountIt.use(work, "pc1", "student1");
        AtomicInteger opened = new AtomicInteger();
        SwingUtilities.invokeLater(() -> AccessGate.require(null, opened::incrementAndGet));
        Window w = waitWindow();
        check("בלי כניסה — נפתח מסך הכניסה (ולא המאגר)", w != null && opened.get() == 0);
        shot(w, "1-choose");
        click(w, "הקלדת קוד המורה");
        type(w, 0, "AAAA");
        click(w, "חיבור המחשב");
        check("קוד שגוי — הודעה ברורה", waitText(w, "הקוד לא נמצא"));
        shot(w, "2-bad-code");
        clickIf(w, "חזרה");
        click(w, "הקלדת קוד המורה");
        type(w, 0, code.toLowerCase());
        click(w, "חיבור המחשב");
        waitUntil(() -> opened.get() > 0, 15000);
        shot(w, "3-after-claim");
        check("הקוד הנכון (גם באותיות קטנות) — המאגר נפתח", opened.get() == 1);
        check("המחשב זוכר: עמדה", Account.current() != null && Account.current().isStation());
        w.dispose();

        // 2. אותו מחשב, תלמיד אחר, בלי אינטרנט — נפתח מיד
        AccountIt.use(work, "pc1", "student2");
        System.setProperty("dsviz.worker", "http://127.0.0.1:9");
        opened.set(0);
        SwingUtilities.invokeAndWait(() -> AccessGate.require(null, opened::incrementAndGet));
        check("משתמש אחר באותו מחשב, בלי אינטרנט — נפתח מיד", opened.get() == 1 && openDialogs() == 0);
        System.setProperty("dsviz.worker", a[0]);

        // 3. מחשב נוסף — אין עוד היתרים (המורה קנה 1)
        AccountIt.use(work, "pc2", "student3");
        opened.set(0);
        SwingUtilities.invokeLater(() -> AccessGate.require(null, opened::incrementAndGet));
        w = waitWindow();
        click(w, "הקלדת קוד המורה");
        type(w, 0, code);
        click(w, "חיבור המחשב");
        check("כל ההיתרים בשימוש — הסבר (ולא נפתח)", (waitText(w, "תפוס") || waitText(w, "אין עוד") || waitText(w, "כל ה")) && opened.get() == 0);
        shot(w, "4-full");
        w.dispose();

        // 4. מורה — כניסה עם Google (הדפדפן מדומה)
        AccountIt.use(work, "pc3", "teacher");
        AccountIt.browserEmail = teacher;
        opened.set(0);
        SwingUtilities.invokeLater(() -> AccessGate.require(null, opened::incrementAndGet));
        w = waitWindow();
        click(w, "כניסה עם Google");
        waitUntil(() -> opened.get() > 0, 20000);
        shot(w, "5-teacher");
        check("מורה: אחרי Google — המאגר נפתח", opened.get() == 1 && Account.current() != null && Account.current().isTeacher());
        w.dispose();

        // 5. חשבון Google שאינו מורה — הסבר
        AccountIt.use(work, "pc4", "someone");
        AccountIt.browserEmail = "not.a.teacher" + System.nanoTime() + "@test.dev";
        opened.set(0);
        SwingUtilities.invokeLater(() -> AccessGate.require(null, opened::incrementAndGet));
        w = waitWindow();
        click(w, "כניסה עם Google");
        waitUntil(() -> visibleText(waitWindowNow()).length() > 0 && !visibleText(waitWindowNow()).contains("פותח את הדפדפן"), 8000);
        Thread.sleep(2500);
        shot(waitWindowNow(), "6-not-teacher");
        check("לא מורה — לא נפתח", opened.get() == 0);
        System.out.println("\n" + pass + " עברו, " + fail + " נכשלו");
        System.exit(fail == 0 ? 0 : 1);
    }

    interface Cond { boolean ok(); }

    static void waitUntil(Cond c, long ms) throws InterruptedException {
        long end = System.currentTimeMillis() + ms;
        while (!c.ok() && System.currentTimeMillis() < end) Thread.sleep(150);
    }

    static Window waitWindowNow() {
        for (Window x : Window.getWindows()) if (x.isShowing()) return x;
        return null;
    }

    static int openDialogs() {
        int n = 0;
        for (Window x : Window.getWindows()) if (x.isShowing()) n++;
        return n;
    }

    static Window waitWindow() throws InterruptedException {
        long end = System.currentTimeMillis() + 15000;
        while (System.currentTimeMillis() < end) {
            Window x = waitWindowNow();
            if (x != null) { Thread.sleep(400); return x; }
            Thread.sleep(100);
        }
        return null;
    }

    static boolean waitText(Window w, String s) throws InterruptedException {
        long end = System.currentTimeMillis() + 12000;
        while (System.currentTimeMillis() < end) {
            if (visibleText(w).contains(s)) return true;
            Thread.sleep(150);
        }
        return false;
    }

    static String visibleText(Component c) {
        if (c == null || !c.isShowing()) return "";
        StringBuilder b = new StringBuilder();
        if (c instanceof JLabel) b.append(((JLabel) c).getText()).append('\n');
        if (c instanceof Container) for (Component k : ((Container) c).getComponents()) b.append(visibleText(k));
        return b.toString();
    }

    static AbstractButton find(Component c, String label) {
        if (c instanceof AbstractButton && c.isShowing() && ((AbstractButton) c).getText() != null && ((AbstractButton) c).getText().contains(label)) return (AbstractButton) c;
        if (c instanceof Container) for (Component k : ((Container) c).getComponents()) {
            AbstractButton r = find(k, label);
            if (r != null) return r;
        }
        return null;
    }

    static void click(Window w, String label) throws Exception {
        AbstractButton b = null;
        long end = System.currentTimeMillis() + 8000;
        while ((b = find(w, label)) == null && System.currentTimeMillis() < end) Thread.sleep(100);
        if (b == null) throw new IllegalStateException("אין כפתור: " + label + "\n" + visibleText(w));
        final AbstractButton fb = b;
        SwingUtilities.invokeAndWait(fb::doClick);
        Thread.sleep(300);
    }

    static void clickIf(Window w, String label) throws Exception {
        if (find(w, label) != null) click(w, label);
    }

    static void fields(Component c, java.util.List<JTextField> out) {
        if (c instanceof JTextField && c.isShowing()) out.add((JTextField) c);
        if (c instanceof Container) for (Component k : ((Container) c).getComponents()) fields(k, out);
    }

    static void type(Window w, int i, String s) throws Exception {
        java.util.List<JTextField> f = new java.util.ArrayList<>();
        fields(w, f);
        SwingUtilities.invokeAndWait(() -> f.get(i).setText(s));
    }

    static void shot(Window w, String name) throws Exception {
        if (w == null) return;
        Thread.sleep(300);
        final BufferedImage[] img = new BufferedImage[1];
        SwingUtilities.invokeAndWait(() -> {
            img[0] = new BufferedImage(w.getWidth(), w.getHeight(), BufferedImage.TYPE_INT_RGB);
            w.paint(img[0].getGraphics());
        });
        ImageIO.write(img[0], "png", new File(shots, name + ".png"));
    }

    static void check(String name, boolean ok) {
        if (ok) pass++; else fail++;
        System.out.println((ok ? "  ok   " : "  FAIL ") + name);
    }
}
