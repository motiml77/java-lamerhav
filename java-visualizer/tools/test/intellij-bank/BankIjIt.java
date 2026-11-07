package il.lamerhav.dsviz;

import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.Toolkit;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import javax.imageio.ImageIO;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;

/**
 * "מאגר תרגילים" ב-IntelliJ אמיתי: התוסף dsviz-bank (בבדיקה — ‎-Ddsviz.bankAutotest) מפעיל את המחלקה הזאת במקום BankVs, עם אותם
 * ארגומנטים ועם הגשר האמיתי. עוברים כמו תלמיד, ומצלמים את כל המסך (IntelliJ + החלון הצף):
 *   חלון המאגר מעל IntelliJ → "צור תרגיל" → הקובץ נפתח ב-IntelliJ בסעיף א → "לפעולה של סעיף ב" → הקלדה בעורך כשהחלון הצף מקדימה →
 *   "הצעה לפתרון" → התלמיד פותר → "הרצה ויזואלית" בסרגל של IntelliJ → שגיאה בקוד: חלון השגיאות לא מוסתר → "סיימתי" (האישור בתוך
 *   החלון הצף) → הקבצים נמחקים והלשונית נסגרת
 */
public final class BankIjIt {
    static File out;
    static int fails, oks;
    static final List<String> lines = new ArrayList<String>();

    public static void main(String[] a) throws Exception {
        for (int i = 0; i < a.length - 1; i++) if (a[i].equals("--it-out")) out = new File(a[i + 1]);
        if (out == null) out = new File("bank-ij-it");
        out.mkdirs();
        try {
            BankVs.configure(a);
            run();
        } catch (Throwable t) {
            fail("crash", "חריגה: " + t);
            t.printStackTrace();
        }
        log("SUMMARY " + oks + " ok / " + fails + " FAIL");
        Files.write(new File(out, "bank-ij-it.txt").toPath(), String.join("\n", lines).getBytes(StandardCharsets.UTF_8));
        System.exit(fails == 0 ? 0 : 1);
    }

    static String state() {
        String s = BankVs.bridge("STATE");
        log("  ..   IntelliJ: " + s);
        return s == null ? "" : s;
    }

    static void run() throws Exception {
        BankStore.Question q = null;
        for (BankStore.Question x : BankVs.store.questions) if (x.id.equals("stack-sum-size")) q = x;
        final BankStore.Question sq = q;
        BankProgress.get().setDone(sq.id, false);   // מתחילים נקי (גם אם הרצה קודמת סימנה)
        // 1. חלון המאגר מעל IntelliJ
        BankVsIt.ui(() -> { BankVsWindow.open(null, "stack-sum-size"); return null; });
        BankVsIt.pause(3000);
        screen("ij-01-bank-over-intellij");
        check("window", BankVsIt.ui(() -> BankVsWindow.current.frame.getTitle()).contains("IntelliJ"), "חלון המאגר נפתח מעל IntelliJ ('מאגר תרגילים — IntelliJ')", "");

        // 2. "צור תרגיל" — הקובץ נפתח ב-IntelliJ בסעיף א
        BankVsIt.click(BankVsIt.ui(() -> BankVsWindow.current.frame), "צור תרגיל");
        long end = System.currentTimeMillis() + 180000;
        while (System.currentTimeMillis() < end && BankVsIt.ui(() -> BankVsFloat.current == null || !BankVsFloat.current.isShowing())) BankVsIt.pause(500);
        BankVsIt.pause(7000);
        List<File> projects = BankVs.projects(sq);
        check("created", projects.size() == 1, "נוצר תרגיל בפרויקט: " + (projects.isEmpty() ? "-" : projects.get(0).getName()), "");
        if (projects.isEmpty()) return;
        File dir = projects.get(0);
        File main = new File(dir, "StackTargil1.java");
        String st = state();
        check("opened", st.contains("file=StackTargil1.java") && st.matches("(?s).*line=[1-9].*"), "IntelliJ פתח את StackTargil1.java, והסמן בסעיף א", st);
        check("float-on-top", BankVsIt.ui(() -> BankVsFloat.current.isAlwaysOnTop() && BankVsFloat.current.isShowing()), "החלון הצף 'השאלה' פתוח ותמיד מעל IntelliJ", "");
        // ספריית הקורס: בבדיקה lib/dsviz.jar של הפרויקט נמחק מראש (הספרייה רשומה אבל הקובץ חסר — אז Stack לא מוכר והבנייה נכשלת);
        // פתיחת התרגיל מחזירה אותו
        File jar = new File(new File(String.valueOf(BankVs.root)).getParentFile(), "lib/dsviz.jar");
        for (int i = 0; i < 60 && !jar.isFile(); i++) BankVsIt.pause(1000);
        check("library", jar.isFile(), "ספריית הקורס (lib/dsviz.jar) במקום — IntelliJ מכיר את Stack", jar.getPath());
        screen("ij-02-created-file-open-float");

        // 3. "לפעולה של סעיף ב"
        BankVsIt.click(BankVsIt.ui(() -> BankVsFloat.current), "לפעולה של סעיף ב");
        BankVsIt.pause(3000);
        int sizeLine = BankVs.methodLine(main, "sizeOf");
        st = state();
        check("jump", st.contains("file=StackTargil1.java") && st.contains("line=" + sizeLine), "'לפעולה של סעיף ב' — IntelliJ קפץ לפעולה sizeOf (שורה " + sizeLine + ")", st);
        screen("ij-03-jump-section-b");

        // 4. כותבים קוד בעורך של IntelliJ — החלון הצף נשאר מקדימה
        String typed = BankVs.bridge("TYPE", "int typed = 42;");
        BankVsIt.pause(1500);
        st = state();
        check("typing", "OK".equals(typed) && st.contains("text=true") && BankVsIt.ui(() -> BankVsFloat.current.isShowing() && BankVsFloat.current.isAlwaysOnTop()),
                "הקלדה בעורך של IntelliJ (השורה נכנסת לקוד) — והחלון הצף נשאר מקדימה", st);
        screen("ij-04-typing-float-in-front");
        BankVs.bridge("UNDO");
        BankVsIt.pause(1000);

        // 5. "הצעה לפתרון"
        SwingUtilities.invokeLater(() -> BankVsIt.clickNow(BankVsFloat.current, "הצעה לפתרון"));
        JDialog sol = BankVsIt.waitDialog("הצעה לפתרון", 10000);
        check("solution", sol != null && BankVsIt.texts(sol).contains("sumStack"), "'הצעה לפתרון' של סעיף א (Java: sumStack)", "");
        if (sol != null) {
            BankVsIt.pause(800);
            screen("ij-05-solution");
            BankVsIt.ui(() -> { sol.dispose(); return null; });
        }

        // 6. התלמיד פותר, ולוחץ "הרצה ויזואלית" בסרגל של IntelliJ (הכפתור של DSViz) — אין כפתור הרצה בחלון הצף
        check("float-no-run", BankVsIt.ui(() -> !BankVsIt.texts(BankVsFloat.current).contains("| הרצה ויזואלית |")),
                "בחלון הצף אין כפתור הרצה — מריצים מהכפתור של IntelliJ, איפה שכותבים את הקוד", "");
        int applied = BankIjAll.solve(dir);
        BankVs.bridge("REFRESH", dir.getPath());
        BankVsIt.pause(3000);
        final java.time.Instant t0 = java.time.Instant.now();
        String pressed = BankVs.bridge("IDERUN", main.getAbsolutePath());
        boolean ran = false;
        for (int i = 0; i < 90 && !ran; i++) {
            BankVsIt.pause(1000);
            ran = ProcessHandle.allProcesses().anyMatch(p -> p.info().startInstant().map(t -> t.isAfter(t0)).orElse(false)
                    && p.info().commandLine().orElse("").matches("(?s).*(LineByLine|TraceViewer).*"));
        }
        BankVsIt.pause(8000);
        boolean errors = BankVs.ideDialog();   // חלון "יש שגיאות" — הבנייה נכשלה
        screen("ij-06-visual-run");
        check("visual", "OK".equals(pressed) && applied == 2 && ran && !errors, "'הרצה ויזואלית' בסרגל של IntelliJ — נבנה בלי שגיאות, וההרצה הוויזואלית של DSViz התחילה (אחרי שהתלמיד פתר את 2 הסעיפים)",
                "pressed=" + pressed + " applied=" + applied + " ran=" + ran + " errors-dialog=" + errors);
        if (errors) BankVs.bridge("CLOSEDIALOG");
        ProcessHandle.allProcesses().filter(p -> p.info().commandLine().orElse("").matches("(?s).*(LineByLine|TraceViewer).*")).forEach(ProcessHandle::destroy);
        BankVsIt.pause(2000);

        // 6ב. שגיאה בקוד → IntelliJ מציג חלון "יש שגיאות" — והחלון הצף יורד אחורה כדי לא להסתיר אותו; אחרי שנסגר — שוב מעל
        java.nio.file.Path mp = main.toPath();
        String good = new String(Files.readAllBytes(mp), StandardCharsets.UTF_8);
        Files.write(mp, good.replaceFirst("return sum;", "return sum").getBytes(StandardCharsets.UTF_8));
        BankVs.bridge("REFRESH", dir.getPath());
        BankVsIt.pause(2500);
        BankVs.bridge("IDERUN", main.getAbsolutePath());
        boolean down = false;
        for (int i = 0; i < 60 && !down; i++) {
            BankVsIt.pause(1000);
            down = BankVsIt.ui(() -> !BankVsFloat.current.isAlwaysOnTop());
        }
        BankVsIt.pause(1500);
        screen("ij-06b-error-dialog-in-front");
        check("float-yield", down, "שגיאה בקוד: חלון השגיאות של IntelliJ מקדימה — החלון הצף ירד אחורה ולא מסתיר אותו", "");
        String closed = BankVs.bridge("CLOSEDIALOG");
        boolean up = false;
        for (int i = 0; i < 15 && !up; i++) {
            BankVsIt.pause(500);
            up = BankVsIt.ui(() -> BankVsFloat.current.isAlwaysOnTop());
        }
        check("float-back", up, "אחרי שחלון השגיאות נסגר — החלון הצף שוב 'תמיד מעל'", String.valueOf(closed));
        Files.write(mp, good.getBytes(StandardCharsets.UTF_8));
        BankVs.bridge("REFRESH", dir.getPath());
        BankVsIt.pause(2000);

        // 7. "סיימתי את התרגיל" — רק קבצי התרגיל נמחקים, והלשונית שלהם נסגרת ב-IntelliJ
        if (BankVs.ideDialog()) BankVs.bridge("CLOSEDIALOG");
        final JFrame fl = BankVsIt.ui(() -> BankVsFloat.current);
        SwingUtilities.invokeLater(() -> {
            BankVsFloat.DoneButton b = BankVsIt.find(fl, BankVsFloat.DoneButton.class);
            if (b != null) b.click();
        });
        BankVsIt.pause(1500);
        String ct = BankVsIt.ui(() -> BankVsIt.texts(fl));
        boolean extra = BankVsIt.ui(() -> { for (java.awt.Window x : java.awt.Window.getWindows()) if (x instanceof JDialog && x.isShowing()) return true; return false; });
        check("done-ask", BankVsIt.ui(() -> BankVsFloat.confirm != null) && !extra && ct.contains("StackTargil1.java") && ct.contains("סיום ומחיקה"),
                "'סיימתי את התרגיל' — האישור בתוך החלון הצף (בלי חלון נוסף שמכסה), עם שם הקובץ שיימחק", ct);
        screen("ij-07-done-ask");
        if (BankVsIt.ui(() -> BankVsFloat.confirm == null)) return;
        SwingUtilities.invokeLater(() -> BankVsIt.clickNow(fl, "סיום ומחיקה"));
        BankVsIt.pause(6000);
        st = state();
        for (int i = 0; i < 10 && st.contains("StackTargil1.java"); i++) { BankVsIt.pause(1500); st = state(); }   // IntelliJ סוגר את הלשונית אחרי הרענון
        check("done", !main.exists() && !dir.exists() && BankProgress.get().done(sq.id) && st.startsWith("OK") && !st.contains("StackTargil1.java"),
                "אחרי 'סיימתי': הקבצים והחבילה נמחקו, השאלה סומנה, והלשונית נסגרה ב-IntelliJ", st);
        screen("ij-08-after-done");
    }

    static void screen(String name) {
        try {
            ImageIO.write(new Robot().createScreenCapture(new Rectangle(Toolkit.getDefaultToolkit().getScreenSize())), "png", new File(out, name + ".png"));
            log("  ..   צילום מסך: " + name + ".png");
        } catch (Exception e) {
            log("  ..   צילום " + name + " נכשל: " + e);
        }
    }

    static void check(String id, boolean ok, String what, String why) {
        if (ok) { oks++; log("  ok   " + id + "  " + what); }
        else fail(id, what + (why == null || why.isEmpty() ? "" : " — " + why));
    }

    static void fail(String id, String what) {
        fails++;
        log("  FAIL " + id + "  " + what);
    }

    static void log(String s) {
        System.out.println(s);
        lines.add(s);
    }
}
