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

        // 2. אותו מחשב, תלמיד אחר: עם אינטרנט — בדיקה קצרה מול השרת ונפתח; בלי אינטרנט — נפתח (בלי לחכות לשרת)
        AccountIt.use(work, "pc1", "student2");
        opened.set(0);
        long t0 = System.currentTimeMillis();
        SwingUtilities.invokeLater(() -> AccessGate.require(null, opened::incrementAndGet));
        waitUntil(() -> opened.get() > 0, 8000);
        check("עמדה תקפה, עם אינטרנט — המאגר נפתח אחרי בדיקה קצרה (" + (System.currentTimeMillis() - t0) + "ms), בלי מסך הכניסה",
                opened.get() == 1 && openDialogs() == 0);
        System.setProperty("dsviz.worker", "http://127.0.0.1:9");
        opened.set(0);
        t0 = System.currentTimeMillis();
        SwingUtilities.invokeLater(() -> AccessGate.require(null, opened::incrementAndGet));
        waitUntil(() -> opened.get() > 0, 8000);
        long offMs = System.currentTimeMillis() - t0;
        check("משתמש אחר באותו מחשב, בלי אינטרנט — נפתח (" + offMs + "ms), בלי מסך הכניסה", opened.get() == 1 && openDialogs() == 0 && offMs < 5000);
        check("בלי אינטרנט — הכניסה של העמדה נשארת", Account.current() != null && Account.current().isStation());
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
        for (Window x : Window.getWindows()) x.dispose();

        // 6. ההנהלה משחררת את העמדה (pc1) במסוף הניהול: כשיש אינטרנט — המאגר לא נפתח, אלא מסך הכניסה עם הסבר,
        //    והכניסה של העמדה נמחקת מהמחשב
        AccountIt.use(work, "pc1", "student1");
        check("לפני השחרור: המחשב מחובר כעמדה", Account.current() != null && Account.current().isStation());
        java.util.Map<String, Object> tj = Json.obj(Json.parse(AccountIt.api("GET", "/v1/admin/teacher?email=" + teacher, admin, null)));
        java.util.List<?> sts = (java.util.List<?>) tj.get("stations");
        check("במסוף הניהול: העמדה ברשימה של המורה", sts != null && sts.size() == 1);
        String sid = Json.str(Json.obj(sts.get(0)).get("id"));
        AccountIt.api("POST", "/v1/admin/station/release", admin, "{\"id\":\"" + sid + "\",\"reason\":\"בדיקה\"}");
        opened.set(0);
        SwingUtilities.invokeLater(() -> AccessGate.require(null, opened::incrementAndGet));
        w = waitWindow();
        check("אחרי 'שחרר' — נפתח מסך הכניסה, לא המאגר", w != null && opened.get() == 0);
        check("במסך: הסבר שהמורה או ההנהלה שחררו את העמדה", w != null && waitText(w, "המורה או ההנהלה שחררו"));
        check("הכניסה של העמדה נמחקה מהמחשב", Account.current() == null);
        shot(w, "7-released");
        Thread.sleep(1500);
        check("והמאגר לא נפתח גם אחר כך", opened.get() == 0);
        if (w != null) w.dispose();
        // ושוב — בלי אינטרנט: אין כניסה שמורה, אז מסך הכניסה (לא המאגר)
        System.setProperty("dsviz.worker", "http://127.0.0.1:9");
        SwingUtilities.invokeLater(() -> AccessGate.require(null, opened::incrementAndGet));
        w = waitWindow();
        check("אחרי השחרור, גם בלי אינטרנט — מסך הכניסה (לא המאגר)", w != null && opened.get() == 0);
        if (w != null) w.dispose();
        System.setProperty("dsviz.worker", a[0]);
        for (Window x : Window.getWindows()) x.dispose();

        // 7. תלמיד חדש: כניסה עם Google; ההרשמה (שם מלא וטלפון) נעשית בדפדפן לפני החזרה לתוכנה → המאגר נפתח
        AccessGate.Dialog.POLL_MS = 600;
        AccountIt.use(work, "pc5", "pupil");
        String pupil = "gate.pupil" + System.nanoTime() + "@test.dev";
        AccountIt.browserEmail = pupil;
        opened.set(0);
        SwingUtilities.invokeLater(() -> AccessGate.require(null, opened::incrementAndGet));
        w = waitWindow();
        check("הכרטיס של תלמיד מסביר: בפעם הראשונה שם מלא וטלפון", waitText(w, "שם מלא וטלפון"));
        shot(w, "8-choose-student-text");
        clickLast(w, "כניסה עם Google");
        waitUntil(() -> opened.get() > 0, 20000);
        check("תלמיד: אחרי Google וההרשמה בדפדפן — המאגר נפתח", opened.get() == 1 && Account.current() != null && Account.current().isStudent());
        check("רשום כתלמיד עצמאי", Account.studentInfo() != null && Boolean.TRUE.equals(Account.studentInfo().get("registered")) && "independent".equals(Account.studentInfo().get("mode")));
        for (Window x : Window.getWindows()) x.dispose();

        // 8. תלמיד שמחובר בלי הרשמה (התחבר לפני שהייתה הרשמה): במקום המאגר — "נשאר להשלים הרשמה". הכפתור פותח את דף ההרשמה,
        //    ואחרי ההרשמה בדפדפן החלון מתעדכן לבד והמאגר נפתח (בלי כניסה מחדש)
        AccountIt.use(work, "pc6", "late");
        String late = "gate.late" + System.nanoTime() + "@test.dev";
        AccountIt.browserEmail = late;
        AccountIt.registerInBrowser = false;
        Account.Result lr = Account.signIn("student", null, null, 30000);
        AccountIt.registerInBrowser = true;
        check("הכנה: מחובר בלי הרשמה (register), עם כרטיס ובלי גישה", "register".equals(lr.status) && Account.current() == null && Account.userState() != null);
        AccountIt.opened.clear();
        opened.set(0);
        SwingUtilities.invokeLater(() -> AccessGate.require(null, opened::incrementAndGet));
        w = waitWindow();
        check("נפתח 'נשאר להשלים הרשמה' (לא המאגר)", w != null && waitText(w, "נשאר להשלים הרשמה") && opened.get() == 0);
        check("במסך: החשבון המחובר, ובלי שלוש האפשרויות", visibleText(w).contains(late) && find(w, "הקלדת קוד המורה") == null);
        shot(w, "9-register-step");
        Thread.sleep(2500);
        check("בלי ללחוץ — הדפדפן לא נפתח לבד, והמאגר סגור", AccountIt.opened.isEmpty() && opened.get() == 0);
        click(w, "להרשמה בדפדפן");
        waitUntil(() -> opened.get() > 0, 20000);
        check("הכפתור פתח את דף ההרשמה, עם הסימן של החשבון (" + AccountIt.opened + ")", AccountIt.opened.size() == 1
                && AccountIt.opened.get(0).endsWith("/welcome?r=register&role=student&u=" + Account.emailMark(late)));
        check("אחרי ההרשמה בדפדפן — החלון התעדכן לבד והמאגר נפתח", opened.get() == 1 && Account.current() != null && Account.current().isStudent());
        for (Window x : Window.getWindows()) x.dispose();

        // 9. ההנהלה מעבירה את המאגר לתשלום: תלמיד רשום שכבר מחובר — במקום המאגר, "המאגר פתוח עכשיו בתשלום" עם המחיר
        //    (לא "הכניסה לא בתוקף", ובלי כניסה מחדש). הכפתור פותח את דף התשלום, ואחרי התשלום המאגר נפתח לבד
        AccountIt.api("POST", "/v1/admin/settings", admin, "{\"studentsFree\":false,\"price\":60}");
        AccountIt.payInBrowser = false;
        AccountIt.opened.clear();
        opened.set(0);
        SwingUtilities.invokeLater(() -> AccessGate.require(null, opened::incrementAndGet));
        w = waitWindow();
        check("נפתח מסך התשלום (לא המאגר)", w != null && waitText(w, "המאגר פתוח עכשיו בתשלום") && opened.get() == 0);
        check("במסך: המחיר והחשבון; בלי 'הכניסה לא בתוקף'", visibleText(w).contains("60") && visibleText(w).contains(late) && !visibleText(w).contains("לא בתוקף"));
        check("הכרטיס נשאר במחשב — לא צריך להיכנס שוב", Account.userState() != null && Account.current() == null);
        shot(w, "10-pay-step");
        click(w, "לתשלום בדפדפן");
        Thread.sleep(2500);
        check("דף התשלום נפתח; עוד לא שולם — המאגר סגור והחלון מחכה", AccountIt.opened.size() == 1 && AccountIt.opened.get(0).contains("/welcome?r=pay&role=student&u=")
                && opened.get() == 0 && w.isShowing());
        w.dispose();
        // פתיחה נוספת של המאגר — שוב מסך התשלום (ולא שלוש האפשרויות); עכשיו משלמים
        AccountIt.payInBrowser = true;
        SwingUtilities.invokeLater(() -> AccessGate.require(null, opened::incrementAndGet));
        w = waitWindow();
        check("פתיחה נוספת — שוב מסך התשלום", w != null && waitText(w, "המאגר פתוח עכשיו בתשלום") && opened.get() == 0);
        click(w, "לתשלום בדפדפן");
        waitUntil(() -> opened.get() > 0, 20000);
        check("אחרי התשלום — המאגר נפתח לבד", opened.get() == 1 && Account.current() != null && Account.current().isStudent());
        for (Window x : Window.getWindows()) x.dispose();
        // תלמיד חדש כשהמאגר בתשלום: נרשם בדפדפן, ואז "מחכים לתשלום" (הדפדפן כבר בדף התשלום)
        AccountIt.use(work, "pc7", "payer");
        AccountIt.browserEmail = "gate.payer" + System.nanoTime() + "@test.dev";
        AccountIt.payInBrowser = false;
        opened.set(0);
        SwingUtilities.invokeLater(() -> AccessGate.require(null, opened::incrementAndGet));
        w = waitWindow();
        clickLast(w, "כניסה עם Google");
        check("תלמיד חדש, מאגר בתשלום — 'מחכים לתשלום' (לא נפתח)", waitText(w, "מחכים לתשלום") && opened.get() == 0);
        check("הדפדפן הופנה לדף התשלום", AccountIt.lastRedirect.contains("/welcome?r=pay&role=student&u="));
        shot(w, "11-wait-payment");
        AccountIt.api("POST", "/v1/admin/student", admin, "{\"email\":\"" + AccountIt.browserEmail + "\",\"until\":\"2027-08-31\"}");
        waitUntil(() -> opened.get() > 0, 20000);
        check("אחרי התשלום — נפתח לבד", opened.get() == 1 && Account.current() != null);
        for (Window x : Window.getWindows()) x.dispose();
        AccountIt.payInBrowser = true;
        AccountIt.api("POST", "/v1/admin/settings", admin, "{\"studentsFree\":true}");

        System.out.println("\n" + pass + " עברו, " + fail + " נכשלו");
        System.exit(fail == 0 ? 0 : 1);
    }

    /** הכפתור האחרון עם הכיתוב הזה (למשל "כניסה עם Google" של הכרטיס "תלמיד" — אחרי זה של "מורה") */
    static void clickLast(Window w, String label) throws Exception {
        java.util.List<AbstractButton> all = new java.util.ArrayList<>();
        long end = System.currentTimeMillis() + 8000;
        while (System.currentTimeMillis() < end) {
            all.clear();
            buttons(w, label, all);
            if (!all.isEmpty()) break;
            Thread.sleep(100);
        }
        if (all.isEmpty()) throw new IllegalStateException("אין כפתור: " + label + "\n" + visibleText(w));
        final AbstractButton b = all.get(all.size() - 1);
        SwingUtilities.invokeAndWait(b::doClick);
        Thread.sleep(300);
    }

    static void buttons(Component c, String label, java.util.List<AbstractButton> out) {
        if (c instanceof AbstractButton && c.isShowing() && ((AbstractButton) c).getText() != null && ((AbstractButton) c).getText().contains(label)) out.add((AbstractButton) c);
        if (c instanceof Container) for (Component k : ((Container) c).getComponents()) buttons(k, label, out);
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
