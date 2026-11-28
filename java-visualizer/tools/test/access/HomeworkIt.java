package il.lamerhav.dsviz;

import java.awt.Component;
import java.awt.Container;
import java.awt.Window;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

import javax.imageio.ImageIO;
import javax.swing.JFrame;
import javax.swing.JPopupMenu;
import javax.swing.SwingUtilities;

/**
 * שיעורי בית מהמורה בחלון המאגר של Visual Studio / IntelliJ / VS Code (BankVsWindow, Swing), מול השרת המקומי והאמולטור:
 * בלי שיעורי בית — אין כפתור (החלון כמו תמיד); המורה נותנת — הכפתור זוהר ("שיעורי בית מהמורה … · 4 שאלות להגשה · להגשה עד …"),
 * ההילה פועמת ונעצרת כשהחלון מוסתר; לחיצה — רשימה (שם · נושא · להגשה / ✓ הוגש / ציון), בחירה בוחרת את השאלה; תגית ש״ב בעץ;
 * התלמיד מגיש — נחשב מיד, ונשאר שאלה אחת: לחיצה בוחרת אותה ישר; השרת מאשר; ציון מהמורה ברשימה; הכול הוגש — רגוע, בלי זוהר;
 * אותו דבר בחלון של C# (אותם מזהים); שרת ישן בלי homework — אין כפתור; תלמיד שעזב / עמדת מחשב — אין כפתור.
 *   java ... HomeworkIt <שרת> <אמולטור auth> <תיקיית עבודה> <צילומים> <dsviz.jar / תיקיית המחלקות> <תיקיית bank>
 */
public class HomeworkIt {
    static int pass, fail;
    static File shots;

    static void check(String name, boolean ok, Object got) {
        if (ok) pass++; else fail++;
        System.out.println((ok ? "  ok   " : "  FAIL ") + name + (ok ? "" : "  ← " + got));
    }

    static <T> T ui(Callable<T> c) throws Exception {
        return SubmitIt.ui(c);
    }

    static BankVsWindow win() throws Exception {
        return ui(() -> BankVsWindow.current);
    }

    /** מחכה (עד ms) שהתנאי יתקיים — בודק ב-EDT */
    static boolean waitUi(Callable<Boolean> c, long ms) throws Exception {
        long end = System.currentTimeMillis() + ms;
        while (System.currentTimeMillis() < end) {
            if (Boolean.TRUE.equals(ui(c))) return true;
            Thread.sleep(120);
        }
        return false;
    }

    static boolean buttonShown() throws Exception {
        return ui(() -> {
            BankVsWindow w = BankVsWindow.current;
            return w != null && w.hwHolder.isVisible() && w.hwButton.isShowing();
        });
    }

    static String buttonText() throws Exception {
        return ui(() -> BankVsWindow.current == null || BankVsWindow.current.homework == null ? "" : BankVsWindow.current.homework.label());
    }

    static String selected() throws Exception {
        return ui(() -> BankVsWindow.current == null || BankVsWindow.current.selected == null ? "" : BankVsWindow.current.selected.id);
    }

    static List<String> tree() throws Exception {
        return ui(() -> new ArrayList<String>(BankVsWindow.current.treeShown));
    }

    static boolean treeHas(String s) throws Exception {
        for (String t : tree()) if (t.equals(s)) return true;
        return false;
    }

    static void clickButton() throws Exception {
        SwingUtilities.invokeAndWait(() -> BankVsWindow.current.hwButton.doClick());
        Thread.sleep(400);
    }

    static boolean popupShown() throws Exception {
        return ui(() -> BankVsHomework.list != null && BankVsHomework.list.isVisible());
    }

    static void pick(String id) throws Exception {
        ui(() -> {
            for (Component c : BankVsHomework.list.getComponents())
                if (c instanceof BankVsHomework.Row && ((BankVsHomework.Row) c).item.bankId.equals(id)) ((BankVsHomework.Row) c).pick();
            return null;
        });
        Thread.sleep(400);
    }

    static void sync() throws Exception {
        BankSubmit.resetGradesClock();
        BankSubmit.syncGrades();
        Thread.sleep(500);
    }

    static void shot(String name) throws Exception {
        Thread.sleep(500);
        final JFrame f = ui(() -> BankVsWindow.current == null ? null : BankVsWindow.current.frame);
        if (f == null) return;
        final BufferedImage[] img = new BufferedImage[1];
        SwingUtilities.invokeAndWait(() -> {
            img[0] = new BufferedImage(f.getWidth(), f.getHeight(), BufferedImage.TYPE_INT_RGB);
            f.paint(img[0].getGraphics());
            // רשימה שנפתחה כחלון נפרד (לא בתוך החלון) — מציירים אותה במקום שלה
            JPopupMenu p = BankVsHomework.list;
            if (p != null && p.isVisible()) {
                Window pw = SwingUtilities.getWindowAncestor(p);
                if (pw != null && pw != f) {
                    java.awt.Point at = SwingUtilities.convertPoint(p, 0, 0, f.getRootPane());
                    java.awt.Graphics g = img[0].getGraphics().create(at.x + f.getRootPane().getX() + f.getInsets().left, at.y + f.getRootPane().getY() + f.getInsets().top, p.getWidth(), p.getHeight());
                    p.paint(g);
                    g.dispose();
                }
            }
        });
        ImageIO.write(img[0], "png", new File(shots, name + ".png"));
    }

    /** "הגשה למורה" כמו מחלון השאלה: השליחה, ורישום ההגשה במחשב (BankProgress.submitted) */
    static String submit(String id, String topic, String title) throws Exception {
        Submit.Work w = new Submit.Work();
        w.source = "bank";
        w.bankId = id;
        w.topic = topic;
        w.title = title;
        w.level = "קל";
        w.app = "IntelliJ";
        w.files.put("Targil.java", "public class Targil {\n    public static void main(String[] args) { }\n}\n");
        w.main = "Targil.java";
        Account.Result r = Submit.send(w);
        if ("student".equals(Submit.mode())) BankProgress.get().submitted(id, Submit.teacher(), "queued".equals(r.status));
        Thread.sleep(400);
        return r.status;
    }

    static Map<String, Object> json(String s) {
        return Json.obj(Json.parse(s));
    }

    public static void main(String[] a) throws Exception {
        AccountIt.server = a[0];
        AccountIt.auth = a[1];
        File work = new File(a[2]);
        shots = new File(a[3]);
        shots.mkdirs();
        File lib = new File(a[4]);
        System.setProperty("dsviz.worker", a[0]);
        System.setProperty("dsviz.passKey", Json.str(json(AccountIt.get(a[0] + "/v1/access/key")).get("spki")));
        System.setProperty("dsviz.noOpen", "true");
        System.setProperty("dsviz.bank", a[5]);
        System.setProperty("dsviz.bankProgress", new File(work, "progress.json").getPath());
        System.setProperty("dsviz.submits", new File(work, "outbox").getPath());
        System.setProperty("user.home", new File(work, "home").getPath());
        Account.browser = AccountIt::fakeBrowser;
        String admin = AccountIt.idToken("motiml77@gmail.com");
        String teacher = "hw.teacher" + System.nanoTime() + "@test.dev";
        AccountIt.api("POST", "/v1/admin/teacher", admin, "{\"create\":true,\"email\":\"" + teacher + "\",\"name\":\"רינה אברהם\",\"school\":\"תיכון לדוגמה\",\"seats\":2}");
        String code = Json.str(Json.obj(json(AccountIt.api("GET", "/v1/admin/teacher?email=" + teacher, admin, null)).get("teacher")).get("code"));
        String tt = AccountIt.idToken(teacher);
        String cls = Json.str(json(AccountIt.api("POST", "/v1/me/class", tt, "{\"op\":\"create\",\"name\":\"יב 2\"}")).get("id"));

        // ---------------------------------------------------------------- תלמיד משויך, בלי שיעורי בית
        AccountIt.use(work, "pcH", "pupil");
        String student = "hw.pupil" + System.nanoTime() + "@test.dev";
        AccountIt.browserEmail = student;
        check("תלמיד נכנס", Account.signIn("student", null, null, 30000).ok(), "");
        check("מצטרף למורה רינה אברהם בקוד המורה", Account.studentJoin(code, false).ok() && "student".equals(Submit.mode()), Submit.mode());
        AccountIt.api("POST", "/v1/me/member", tt, "{\"op\":\"move\",\"student\":\"" + student + "\",\"classId\":\"" + cls + "\"}");
        File src = new File(work, "project/src");
        src.mkdirs();
        BankVs.configure(new String[] { "bank", src.getPath(), "--ij", "--lib", lib.getPath(), "--java", SolutionCheck.javaOf(System.getProperty("java.home")), "--project", "Demo",
            "--store", new File(work, "solutions.json").getPath() });
        ui(() -> { BankVsWindow.open(null, null); return null; });
        Thread.sleep(1500);   // הבדיקה מול השרת ברקע (BankSubmit.syncGradesLater) — ואין שיעורי בית
        check("בלי שיעורי בית: אין כפתור, והחלון כמו תמיד", !buttonShown() && ui(() -> BankVsWindow.current.homework == null), buttonText());
        ui(() -> { BankVsWindow.current.toggle("stack"); return null; });
        check("ובעץ — בלי תגית ש״ב", !String.join("\n", tree()).contains(" hw"), tree());
        shot("h0-no-homework");

        // ---------------------------------------------------------------- המורה נותנת שיעורי בית
        String due = LocalDate.now(ZoneId.of("Asia/Jerusalem")).plusDays(4).toString();
        String dueShort = BankHomework.dueText(due);
        AccountIt.api("POST", "/v1/me/homework", tt, "{\"op\":\"save\",\"classId\":\"\",\"items\":[\"list-evens-remove\"],\"title\":\"\",\"due\":\"\"}");
        AccountIt.api("POST", "/v1/me/homework", tt, "{\"op\":\"save\",\"classId\":\"" + cls + "\",\"items\":[\"stack-sum-size\",\"queue-sum-even\",\"tree-count-sum\"],\"title\":\"מחסנית ותור\",\"due\":\"" + due + "\"}");
        sync();
        check("שיעורי בית מהמורה — הכפתור מופיע (בלי לפתוח את החלון מחדש)", waitUi(() -> BankVsWindow.current.hwButton.isShowing(), 8000), buttonText());
        check("הטקסט: 'שיעורי בית מהמורה רינה אברהם · 4 שאלות להגשה · להגשה עד " + dueShort + "'",
                ("שיעורי בית מהמורה רינה אברהם · 4 שאלות להגשה · להגשה עד " + dueShort).equals(buttonText()), buttonText());
        check("זוהר, וההילה פועמת", ui(() -> BankVsWindow.current.hwButton.glowing() && BankVsWindow.current.hwButton.animating()), "");
        check("מעל החיפוש, בצד", ui(() -> {
            BankVsWindow w = BankVsWindow.current;
            Component search = SubmitIt.byId(w.frame, "search");
            return search != null && w.hwButton.getLocationOnScreen().y < search.getLocationOnScreen().y;
        }), "");
        check("בעץ: תגית ש״ב לשאלה של שיעורי הבית", treeHas("Q stack-sum-size easy hw"), tree());
        shot("h1-swing-glowing");
        // החלון מוסתר — ההילה נעצרת; מוצג שוב — ממשיכה
        ui(() -> { BankVsWindow.current.frame.setVisible(false); return null; });
        check("החלון מוסתר — ההילה נעצרת", waitUi(() -> !BankVsWindow.current.hwButton.animating(), 3000), "");
        ui(() -> { BankVsWindow.current.frame.setVisible(true); return null; });
        check("מוצג שוב — ממשיכה", waitUi(() -> BankVsWindow.current.hwButton.animating(), 3000), "");

        // לחיצה — כמה שאלות: הרשימה
        clickButton();
        check("לחיצה (4 שאלות) — הרשימה נפתחת", popupShown(), "");
        List<String> list = new ArrayList<String>(BankVsHomework.listShown);
        check("ברשימה: שני שיעורי הבית (החדשים קודם, עם המועד) וארבע שאלות — שם · נושא · להגשה",
                list.size() == 6 && list.get(0).equals("SHEET מחסנית ותור | עד " + dueShort) && list.get(1).equals("ITEM stack-sum-size סכום וגודל של מחסנית · מחסנית · להגשה")
                        && list.get(4).startsWith("SHEET שיעורי בית ") && list.get(5).equals("ITEM list-evens-remove שרשרת חדשה והוצאת חוליות · שרשרת חוליות · להגשה"), list);
        shot("h2-swing-popup");
        pick("tree-count-sum");
        check("בחירה ברשימה — השאלה נבחרת בחלון (והרשימה נסגרת)", "tree-count-sum".equals(selected()) && !popupShown(), selected());

        // ---------------------------------------------------------------- חלון המאגר של C# (Visual Studio): אותם מזהים
        ui(() -> { BankVsWindow.current.frame.dispose(); return null; });
        ui(() -> { BankVs.ij = false; BankVs.store = BankStore.load(true); BankVsWindow.open(null, null); return null; });
        check("C#: אותו כפתור, אותו טקסט", waitUi(() -> BankVsWindow.current != null && BankVsWindow.current.hwButton.isShowing(), 5000) && buttonText().contains("4 שאלות להגשה"), buttonText());
        clickButton();
        pick("queue-sum-even");
        check("C#: בחירה ברשימה בוחרת את השאלה (גרסת C#)", "queue-sum-even".equals(selected()) && ui(() -> BankVs.store.csharp), selected());
        shot("h2b-cs-selected");
        ui(() -> { BankVsWindow.current.frame.dispose(); return null; });
        ui(() -> { BankVs.ij = true; BankVs.store = BankStore.load(false); BankVsWindow.open(null, null); return null; });
        waitUi(() -> BankVsWindow.current != null && BankVsWindow.current.hwButton.isShowing(), 5000);

        // ---------------------------------------------------------------- התלמיד מגיש
        check("הגשה (stack-sum-size)", "ok".equals(submit("stack-sum-size", "מחסנית", "סכום וגודל של מחסנית")), "");
        check("הגשה (queue-sum-even)", "ok".equals(submit("queue-sum-even", "תור", "סכום תור וזוגיים")), "");
        check("הגשה (list-evens-remove)", "ok".equals(submit("list-evens-remove", "שרשרת חוליות", "שרשרת חדשה והוצאת חוליות")), "");
        check("אחרי ההגשות — מיד, עוד לפני השרת: 'שאלה אחת להגשה'", waitUi(() -> BankVsWindow.current.homework != null && BankVsWindow.current.homework.pending().size() == 1, 5000)
                && buttonText().endsWith("שאלה אחת להגשה · להגשה עד " + dueShort), buttonText());
        ui(() -> { BankVsWindow.current.select("stack-brackets"); return null; });
        clickButton();
        check("שאלה אחת שנשארה: לחיצה בוחרת אותה ישר (בלי רשימה)", "tree-count-sum".equals(selected()) && !popupShown(), selected());
        sync();
        String pf = new String(Files.readAllBytes(new File(work, "progress.json").toPath()), StandardCharsets.UTF_8);
        check("השרת אישר: שלוש הוגשו (בקובץ ההתקדמות, מהשרת)", pf.split("\"done\":true", -1).length - 1 >= 3, pf.length());
        // ציון מהמורה
        String sid = "";
        for (Object o : SubmitIt.submissions(tt, "student=" + Account.enc(student))) {
            Map<String, Object> s = Json.obj(o);
            if ("stack-sum-size".equals(Json.obj(s.get("exercise")).get("bankId"))) sid = Json.str(s.get("id"));
        }
        AccountIt.api("POST", "/v1/me/submission/grade", tt, "{\"id\":\"" + sid + "\",\"grade\":90}");
        sync();
        ui(() -> { BankVsHomework.showList(BankVsWindow.current.hwButton, BankVsWindow.current.homework, BankVsWindow.current::select); return null; });
        Thread.sleep(300);
        check("ברשימה: ציון 90 / ✓ הוגש / להגשה", BankVsHomework.listShown.contains("ITEM stack-sum-size סכום וגודל של מחסנית · מחסנית · ציון 90")
                && BankVsHomework.listShown.contains("ITEM queue-sum-even סכום תור וזוגיים · תור · ✓ הוגש")
                && BankVsHomework.listShown.contains("ITEM tree-count-sum מספר הצמתים וסכום העץ · עץ בינארי · להגשה"), BankVsHomework.listShown);
        shot("h3-swing-popup-graded");
        ui(() -> { BankVsHomework.list.setVisible(false); return null; });

        // ---------------------------------------------------------------- הכול הוגש: רגוע
        check("הגשה אחרונה (tree-count-sum)", "ok".equals(submit("tree-count-sum", "עץ בינארי", "מספר הצמתים וסכום העץ")), "");
        check("הכול הוגש: 'שיעורי הבית הוגשו ✓', בלי זוהר ובלי פעימה", waitUi(() -> BankVsWindow.current.homework != null && BankVsWindow.current.homework.allDone(), 5000)
                && buttonText().startsWith("שיעורי הבית הוגשו ✓") && ui(() -> !BankVsWindow.current.hwButton.glowing() && !BankVsWindow.current.hwButton.animating()), buttonText());
        check("בעץ: התגית נשארת (אפורה — הוגשה)", treeHas("Q stack-sum-size easy hw-done"), tree());
        shot("h4-swing-calm");
        clickButton();
        check("רגוע — הלחיצה עדיין פותחת את הרשימה", popupShown() && BankVsHomework.listShown.size() == 6, BankVsHomework.listShown);
        ui(() -> { BankVsHomework.list.setVisible(false); return null; });

        // ---------------------------------------------------------------- שרת ישן / עזב / עמדה
        Map<String, Object> old = new LinkedHashMap<String, Object>();
        old.put("ok", Boolean.TRUE);
        old.put("teacher", Json.parse("{\"name\":\"רינה אברהם\",\"school\":\"\"}"));
        old.put("work", new ArrayList<Object>());
        BankSubmit.applyWork(new Account.Result("ok", "", old));
        check("שרת ישן (בלי homework בתשובה) — אין כפתור", waitUi(() -> !BankVsWindow.current.hwHolder.isVisible(), 3000), buttonText());
        sync();
        check("ושוב מול השרת החדש — חוזר", waitUi(() -> BankVsWindow.current.hwHolder.isVisible(), 5000), "");
        check("התלמיד יוצא מהמורה", Account.studentLeave().ok() && Submit.mode().isEmpty(), Submit.mode());
        ui(() -> { AccessGate.changed(); return null; });   // כמו החלון "המורה שלי" אחרי היציאה
        check("תלמיד שלא משויך — אין כפתור (גם כששיעורי הבית עוד בקובץ)", waitUi(() -> !BankVsWindow.current.hwHolder.isVisible(), 5000)
                && !BankProgress.get().homeworkData().isEmpty(), buttonText());
        ui(() -> { BankVsWindow.current.frame.dispose(); return null; });
        AccountIt.use(work, "pcS", "kid");
        check("עמדת מחשב (אותו קובץ התקדמות, שיעורי הבית עוד בו)", Account.claimStation(code, "מעבדה · עמדה 4").ok() && "station".equals(Submit.mode()), Submit.mode());
        ui(() -> { BankVsWindow.open(null, null); return null; });
        Thread.sleep(1200);
        check("בעמדת מחשב — אף פעם לא כפתור שיעורי בית", !buttonShown() && ui(() -> BankVsWindow.current.homework == null), buttonText());

        for (Window w : Window.getWindows()) w.dispose();
        System.out.println("\n" + pass + " עברו, " + fail + " נכשלו");
        System.exit(fail == 0 ? 0 : 1);
    }
}
