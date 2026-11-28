package il.lamerhav.dsviz;

import java.io.File;
import java.nio.file.Files;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.graphics.ImageLoader;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.MenuItem;
import org.eclipse.swt.widgets.Shell;

/**
 * שיעורי בית בחלון המאגר של Eclipse (BankDialog — SWT + JFace, כאן לבד בלי Eclipse, ב-xvfb): בלי שיעורי בית — אין כפתור; יש —
 * כפתור זוהר (ההילה פועמת, נעצרת כשהחלון מוסתר) עם הטקסט של BankHomework, תגית ש״ב בעץ; לחיצה — תפריט עם השאלות, בחירה בוחרת
 * את השאלה; הגשה מהמחשב נחשבת מיד — שאלה אחת שנשארה נבחרת בלחיצה; הכול הוגש — רגוע; לא תלמיד משויך — אין כפתור.
 * המורה של התלמיד — BankHomework.teacherForTest (בלי כניסה אמיתית; הכניסה והשרת — HomeworkIt, בחלון של Swing).
 *   java -cp <swt, jface…>:<המחלקות> il.lamerhav.dsviz.BankDialogHw <תיקיית bank> <תיקיית עבודה> <צילומים>
 */
public class BankDialogHw {
    static int pass, fail;
    static Display d;
    static File shots;

    static void check(String name, boolean ok, Object got) {
        if (ok) pass++; else fail++;
        System.out.println((ok ? "  ok   " : "  FAIL ") + name + (ok ? "" : "  ← " + got));
    }

    /** מריץ את לולאת האירועים ms אלפיות שנייה */
    static void pump(long ms) {
        long end = System.currentTimeMillis() + ms;
        while (System.currentTimeMillis() < end) {
            if (!d.readAndDispatch()) {
                try { Thread.sleep(10); } catch (InterruptedException e) { return; }
            }
        }
    }

    static boolean until(Callable<Boolean> c, long ms) throws Exception {
        long end = System.currentTimeMillis() + ms;
        while (System.currentTimeMillis() < end) {
            if (Boolean.TRUE.equals(c.call())) return true;
            pump(50);
        }
        return false;
    }

    /** צילום של החלון עצמו */
    static void shot(Shell s, String name) {
        pump(300);
        Rectangle b = s.getBounds();
        Image img = new Image(d, b.width, b.height);
        GC gc = new GC(d);
        gc.copyArea(img, b.x, b.y);
        gc.dispose();
        save(img.getImageData(), name);
        img.dispose();
    }

    /** צילום של כל המסך (גם תפריט שנפתח — חלון נפרד) */
    static void screen(String name) throws Exception {
        final File out = new File(shots, name + ".png");
        final Process p = new ProcessBuilder("import", "-window", "root", out.getPath()).redirectErrorStream(true).start();
        while (p.isAlive()) pump(50);
    }

    static void save(ImageData data, String name) {
        ImageLoader l = new ImageLoader();
        l.data = new ImageData[] { data };
        l.save(new File(shots, name + ".png").getPath(), SWT.IMAGE_PNG);
    }

    static String sets(String due, boolean allPending) {
        return "[{\"id\":\"h2\",\"title\":\"מחסנית ותור\",\"due\":\"" + due + "\",\"items\":["
                + "{\"bankId\":\"stack-sum-size\",\"title\":\"סכום וגודל של מחסנית\",\"topic\":\"מחסנית\",\"level\":\"קל\",\"done\":false,\"at\":\"\",\"grade\":null},"
                + "{\"bankId\":\"queue-sum-even\",\"title\":\"סכום תור וזוגיים\",\"topic\":\"תור\",\"level\":\"קל\",\"done\":" + (allPending ? "false" : "true") + ",\"at\":\"\",\"grade\":" + (allPending ? "null" : "88") + "},"
                + "{\"bankId\":\"tree-count-sum\",\"title\":\"מספר הצמתים וסכום העץ\",\"topic\":\"עץ בינארי\",\"level\":\"קל\",\"done\":false,\"at\":\"\",\"grade\":null}]},"
                + "{\"id\":\"h1\",\"title\":\"שיעורי בית 28.11\",\"due\":\"\",\"items\":["
                + "{\"bankId\":\"list-evens-remove\",\"title\":\"שרשרת חדשה והוצאת חוליות\",\"topic\":\"שרשרת חוליות\",\"level\":\"בינוני\",\"done\":false,\"at\":\"\",\"grade\":null}]}]";
    }

    public static void main(String[] a) throws Exception {
        File work = new File(a[1]);
        shots = new File(a[2]);
        shots.mkdirs();
        System.setProperty("dsviz.bank", a[0]);
        System.setProperty("dsviz.bankProgress", new File(work, "progress.json").getPath());
        System.setProperty("dsviz.home", new File(work, "home").getPath());
        System.setProperty("dsviz.machineDir", new File(work, "machine").getPath());
        Files.deleteIfExists(new File(work, "progress.json").toPath());
        d = new Display();
        Shell parent = new Shell(d);
        BankStore store = BankStore.load();
        check("המאגר נטען (" + store.questions.size() + " שאלות)", store.questions.size() > 100 && store.find("stack-sum-size") != null, store.questions.size());
        BankProgress p = BankProgress.get();
        BankHomework.teacherForTest = "רינה אברהם";   // תלמיד משויך למורה רינה אברהם
        final BankDialog dlg = new BankDialog(parent, store, p, new ArrayList<IJavaProject>(), "DataStructures2", null);
        dlg.setBlockOnOpen(false);
        dlg.open();
        pump(800);
        Shell s = dlg.getShell();
        check("בלי שיעורי בית: אין כפתור (החלון כמו תמיד)", !dlg.hwButton.getVisible() && dlg.hwShown.isEmpty() && dlg.homework == null, dlg.hwShown);
        dlg.select("stack", "");
        check("ובעץ — בלי תגית ש״ב", !String.join("\n", dlg.treeShown).contains(" hw"), dlg.treeShown);

        // ---------------------------------------------------------------- שיעורי בית מהמורה (כמו שהשרת ענה)
        String due = LocalDate.now(ZoneId.of("Asia/Jerusalem")).plusDays(4).toString();
        p.syncHomework("רינה אברהם", Json.arr(Json.parse(sets(due, true))));
        check("שיעורי בית — הכפתור מופיע בלי לפתוח מחדש", until(() -> dlg.hwButton.getVisible() && dlg.hwButton.getSize().y > 30, 3000), dlg.hwShown);
        String text = "HW glow שיעורי בית מהמורה רינה אברהם · 4 שאלות להגשה · להגשה עד " + BankHomework.dueText(due);
        check("זוהר, עם הטקסט: " + text.substring(8), dlg.hwShown.contains(text), dlg.hwShown);
        check("ההילה פועמת", until(() -> dlg.hwAnimating, 2000), "");
        dlg.select("stack", "stack-sum-size");
        check("בעץ: תגית ש״ב", dlg.treeShown.contains("Q stack-sum-size easy hw"), dlg.treeShown);
        shot(s, "e1-swt-glowing");
        // החלון מוסתר — ההילה נעצרת; מוצג שוב — ממשיכה
        s.setVisible(false);
        check("החלון מוסתר — ההילה נעצרת", until(() -> !dlg.hwAnimating, 2000), "");
        s.setVisible(true);
        check("מוצג שוב — ממשיכה", until(() -> dlg.hwAnimating, 2000), "");

        // לחיצה (4 שאלות) — תפריט
        dlg.homeworkClicked();
        pump(600);
        check("לחיצה — תפריט עם שיעורי הבית והשאלות", dlg.hwMenu != null && dlg.hwShown.contains("SHEET מחסנית ותור | עד " + BankHomework.dueText(due))
                && dlg.hwShown.contains("ITEM tree-count-sum מספר הצמתים וסכום העץ · עץ בינארי · להגשה") && dlg.hwMenu.getItemCount() == 7, dlg.hwShown);
        screen("e2-swt-menu");
        MenuItem pick = null;
        for (MenuItem i : dlg.hwMenu.getItems()) if ("hw-item-tree-count-sum".equals(i.getData("dsviz.id"))) pick = i;
        dlg.hwMenu.setVisible(false);
        pump(200);
        if (pick != null) pick.notifyListeners(SWT.Selection, new org.eclipse.swt.widgets.Event());
        pump(300);
        check("בחירה בתפריט — השאלה נבחרת", dlg.question() != null && "tree-count-sum".equals(dlg.question().id), dlg.question() == null ? "" : dlg.question().id);

        // ---------------------------------------------------------------- הגשות מהמחשב: נחשבות מיד
        Thread.sleep(5);
        p.submitted("stack-sum-size", "רינה אברהם", false);
        p.submitted("queue-sum-even", "רינה אברהם", false);
        p.submitted("list-evens-remove", "רינה אברהם", true);   // מחכה לאינטרנט — גם נחשבת
        check("אחרי ההגשות — שאלה אחת להגשה", until(() -> dlg.hwShown.size() == 1 && dlg.hwShown.get(0).contains("· שאלה אחת להגשה"), 3000), dlg.hwShown);
        dlg.select("stack", "stack-brackets");
        dlg.homeworkClicked();
        pump(300);
        check("שאלה אחת שנשארה — לחיצה בוחרת אותה ישר", "tree-count-sum".equals(dlg.question().id), dlg.question().id);
        check("בעץ: ש״ב כתומה לשאלה שנשארה, ואפורה לשאלה שהוגשה", dlg.treeShown.contains("Q tree-count-sum easy hw") && dlg.treeShown.contains("Q stack-sum-size easy hw-done"), dlg.treeShown);
        p.submitted("tree-count-sum", "רינה אברהם", false);
        check("הכול הוגש — רגוע: 'שיעורי הבית הוגשו ✓', בלי פעימה", until(() -> dlg.hwShown.size() == 1 && dlg.hwShown.get(0).startsWith("HW calm שיעורי הבית הוגשו ✓") && !dlg.hwAnimating, 3000), dlg.hwShown);
        dlg.select("stack", "stack-sum-size");
        check("בעץ: hw-done", dlg.treeShown.contains("Q stack-sum-size easy hw-done"), dlg.treeShown);
        shot(s, "e3-swt-calm");
        dlg.homeworkClicked();
        pump(400);
        check("רגוע — הלחיצה פותחת את התפריט (✓ הוגש)", dlg.hwShown.contains("ITEM stack-sum-size סכום וגודל של מחסנית · מחסנית · ✓ הוגש"), dlg.hwShown);
        dlg.hwMenu.setVisible(false);
        pump(200);

        // ---------------------------------------------------------------- לא תלמיד משויך (עמדה / עצמאי / שעזב): אין כפתור
        BankHomework.teacherForTest = null;
        p.clearHomework();
        p.syncHomework("רינה אברהם", Json.arr(Json.parse(sets(due, false))));
        check("לא תלמיד משויך (אף אחד לא מחובר כאן) — אין כפתור, גם כששיעורי הבית בקובץ", until(() -> !dlg.hwButton.getVisible() && dlg.homework == null, 3000), dlg.hwShown);
        BankHomework.teacherForTest = "דוד לוי";
        p.syncHomework("רינה אברהם", Json.arr(Json.parse(sets(due, true))));
        check("המורה של התלמיד השתנה — לא מציגים את של הקודם", until(() -> !dlg.hwButton.getVisible(), 2000), dlg.hwShown);
        dlg.close();
        d.dispose();
        System.out.println("\n" + pass + " עברו, " + fail + " נכשלו");
        System.exit(fail == 0 ? 0 : 1);
    }
}
