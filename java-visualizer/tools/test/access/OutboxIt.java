package il.lamerhav.dsviz;

import java.awt.Window;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.swing.JFrame;

/**
 * הגשה בלי אינטרנט נשלחת לבד, בלי שהתלמיד עושה עוד משהו (Submit — תיבת היוצא), מול השרת המקומי והאמולטור:
 * בלי אינטרנט — נשמרת (ובשיעורי הבית: "נשמר במחשב — יישלח כשיהיה אינטרנט"); כשנפתח חלון המאגר — נשלחת; חוט הרקע (כל
 * dsviz.submitRetryMs) — נשלחת כשהשרת חוזר, הסימון יורד וחלון השאלה מציג "הוגש למורה ב-…"; תהליך חדש (UpdateCheck.start, כמו
 * כשהתוכנה נפתחת) — שולח מה שמחכה; מחובר מישהו אחר — ההגשה מחכה למי שהגיש אותה; ואצל המורה — ההגשות הגיעו.
 *   java ... OutboxIt <שרת> <אמולטור auth> <תיקיית עבודה> <צילומים> <dsviz.jar / תיקיית המחלקות> <תיקיית bank>
 *   (ותהליך הבן: OutboxIt --child <אלפיות שנייה> — מה שהתוכנה עושה כשהיא נפתחת)
 */
public class OutboxIt {
    static int pass, fail;
    static final String OFFLINE = "http://127.0.0.1:9";

    static void check(String name, boolean ok, Object got) {
        if (ok) pass++; else fail++;
        System.out.println((ok ? "  ok   " : "  FAIL ") + name + (ok ? "" : "  ← " + got));
    }

    static boolean waitFor(java.util.concurrent.Callable<Boolean> c, long ms) throws Exception {
        long end = System.currentTimeMillis() + ms;
        while (System.currentTimeMillis() < end) {
            if (Boolean.TRUE.equals(c.call())) return true;
            Thread.sleep(150);
        }
        return false;
    }

    /** כמו "הגשה למורה" בחלון השאלה: השליחה, ורישום ההגשה במחשב */
    static Account.Result submit(String id, String topic, String title) throws Exception {
        Submit.Work w = new Submit.Work();
        w.source = "bank";
        w.bankId = id;
        w.topic = topic;
        w.title = title;
        w.level = "קל";
        w.app = "IntelliJ";
        w.files.put("Targil.java", "public class Targil {\n    // " + id + "\n    public static void main(String[] args) { }\n}\n");
        w.main = "Targil.java";
        Account.Result r = Submit.send(w);
        if ("student".equals(Submit.mode())) BankProgress.get().submitted(id, Submit.teacher(), "queued".equals(r.status));
        return r;
    }

    /** ההגשות של התלמיד אצל המורה (מזהי השאלות במאגר) */
    static List<String> atTeacher(String tt, String student) throws Exception {
        List<String> out = new ArrayList<String>();
        for (Object o : SubmitIt.submissions(tt, "student=" + Account.enc(student))) out.add(Json.str(Json.obj(Json.obj(o).get("exercise")).get("bankId")));
        return out;
    }

    static BankHomework.Item item(String id) {
        BankHomework h = BankHomework.current();
        if (h == null) return null;
        for (BankHomework.Sheet s : h.sheets) for (BankHomework.Item i : s.items) if (i.bankId.equals(id)) return i;
        return null;
    }

    static String progressFile(File work) throws Exception {
        return new String(Files.readAllBytes(new File(work, "progress.json").toPath()), StandardCharsets.UTF_8);
    }

    public static void main(String[] a) throws Exception {
        if (a.length > 0 && a[0].equals("--child")) { child(a); return; }
        AccountIt.server = a[0];
        AccountIt.auth = a[1];
        File work = new File(a[2]);
        File shots = new File(a[3]);
        shots.mkdirs();
        File lib = new File(a[4]);
        final String real = a[0];
        System.setProperty("dsviz.worker", real);
        System.setProperty("dsviz.passKey", Json.str(Json.obj(Json.parse(AccountIt.get(real + "/v1/access/key"))).get("spki")));
        System.setProperty("dsviz.noOpen", "true");
        System.setProperty("dsviz.bank", a[5]);
        System.setProperty("dsviz.bankProgress", new File(work, "progress.json").getPath());
        System.setProperty("dsviz.submits", new File(work, "outbox").getPath());
        System.setProperty("user.home", new File(work, "home").getPath());
        System.setProperty("dsviz.submitRetryMs", "600000");   // בהתחלה ארוך — כדי לבדוק כל סיבה לשליחה בנפרד
        Account.browser = AccountIt::fakeBrowser;
        String admin = AccountIt.idToken("motiml77@gmail.com");
        String teacher = "outbox.teacher" + System.nanoTime() + "@test.dev";
        AccountIt.api("POST", "/v1/admin/teacher", admin, "{\"create\":true,\"email\":\"" + teacher + "\",\"name\":\"רינה אברהם\",\"school\":\"תיכון לדוגמה\",\"seats\":2}");
        String code = Json.str(Json.obj(Json.obj(Json.parse(AccountIt.api("GET", "/v1/admin/teacher?email=" + teacher, admin, null))).get("teacher")).get("code"));
        final String tt = AccountIt.idToken(teacher);
        AccountIt.api("POST", "/v1/me/homework", tt, "{\"op\":\"save\",\"classId\":\"\",\"items\":[\"stack-sum-size\",\"queue-sum-even\",\"tree-count-sum\"],\"title\":\"חזרה\",\"due\":\"\"}");

        AccountIt.use(work, "pcO", "pupilA");
        final String a1 = "outbox.a" + System.nanoTime() + "@test.dev";
        AccountIt.browserEmail = a1;
        check("תלמיד A נכנס ומצטרף למורה", Account.signIn("student", null, null, 30000).ok() && Account.studentJoin(code, false).ok() && "student".equals(Submit.mode()), Submit.mode());
        File src = new File(work, "project/src");
        src.mkdirs();
        BankVs.configure(new String[] { "bank", src.getPath(), "--ij", "--lib", lib.getPath(), "--java", SolutionCheck.javaOf(System.getProperty("java.home")), "--project", "Demo",
            "--store", new File(work, "solutions.json").getPath() });
        BankSubmit.resetGradesClock();
        BankSubmit.syncGrades();
        check("שיעורי הבית הגיעו (3 שאלות להגשה)", BankHomework.current() != null && BankHomework.current().pending().size() == 3, BankHomework.current() == null ? "" : BankHomework.current().label());

        // ---------------------------------------------------------------- 1. בלי אינטרנט: נשמרת; חלון המאגר שנפתח — שולח
        System.setProperty("dsviz.worker", OFFLINE);
        check("בלי אינטרנט: ההגשה נשמרה במחשב (queued)", "queued".equals(submit("stack-sum-size", "מחסנית", "סכום וגודל של מחסנית").status) && Submit.waiting() == 1, Submit.waiting());
        check("חוט הרקע פועל (יש מה לשלוח)", Submit.retrying(), "");
        BankHomework.Item it = item("stack-sum-size");
        check("שיעורי הבית: 'נשמר במחשב — יישלח כשיהיה אינטרנט' — לא להגשה ולא הוגש", it != null && it.waiting && !it.done
                && BankHomework.WAITING.equals(BankHomework.status(it)) && BankHomework.current().pending().size() == 2, it == null ? "" : BankHomework.status(it));
        check("חלון השאלה: 'נשמרה במחשב ונשלחת כשיש אינטרנט'", BankProgress.get().submittedNote("stack-sum-size", Submit.teacher()).contains("נשמרה במחשב"), BankProgress.get().submittedNote("stack-sum-size", Submit.teacher()));
        System.setProperty("dsviz.worker", real);
        SubmitIt.ui(() -> { BankVsWindow.open(null, null); return null; });
        check("נפתח חלון המאגר — נשלחה לבד", waitFor(() -> Submit.waiting() == 0, 8000), Submit.waiting());
        check("אצל המורה: ההגשה הגיעה", waitFor(() -> atTeacher(tt, a1).contains("stack-sum-size"), 5000), atTeacher(tt, a1));
        check("הסימון ירד: 'הוגש למורה ב-…', ובשיעורי הבית — ✓ הוגש", waitFor(() -> BankProgress.get().submittedNote("stack-sum-size", Submit.teacher()).startsWith("הוגש למורה")
                && item("stack-sum-size").done, 5000) && !progressFile(work).matches("(?s).*\"stack-sum-size\":\\{[^}]*submitQueued.*"), BankProgress.get().submittedNote("stack-sum-size", Submit.teacher()));
        check("והחלון הפתוח התעדכן (2 שאלות להגשה)", waitFor(() -> SubmitIt.ui(() -> BankVsWindow.current.homework != null && BankVsWindow.current.homework.pending().size() == 2), 5000), "");
        check("תיבת היוצא ריקה — חוט הרקע נעצר", waitFor(() -> !Submit.retrying(), 4000), "");
        SubmitIt.ui(() -> { BankVsWindow.current.frame.dispose(); return null; });

        // ---------------------------------------------------------------- 2. הניסיון החוזר ברקע: השרת חוזר — נשלחת בלי לעשות כלום
        System.setProperty("dsviz.submitRetryMs", "1500");
        System.setProperty("dsviz.worker", OFFLINE);
        BankStore.Question q = BankVs.store.find("queue-sum-even");
        BankVs.create(q, new ExercisePipeline.Progress() {   // התרגיל קיים — בחלון השאלה יש "הגשה למורה" וההערה שמתחתיו
            @Override public void say(String text) { }
            @Override public boolean cancelled() { return false; }
        });
        SubmitIt.openFloat(q);
        Account.Result r2 = submit("queue-sum-even", "תור", "סכום תור וזוגיים");
        SubmitIt.ui(() -> { BankVsFloat.submitNote = Submit.doneText(r2); BankVsFloat.submitNoteQueued = true; BankVsFloat.shown.clear(); BankVsFloat.render(); return null; });
        check("בלי אינטרנט: נשמרה; בחלון השאלה — 'ההגשה נשמרה במחשב'", "queued".equals(r2.status) && SubmitIt.line("SUBMIT-NOTE").contains("נשמרה במחשב"), SubmitIt.line("SUBMIT-NOTE"));
        Thread.sleep(2500);
        check("כל עוד אין אינטרנט — ממשיכה לחכות (חוט הרקע מנסה ונכשל)", Submit.waiting() == 1 && Submit.retrying(), Submit.waiting());
        System.setProperty("dsviz.worker", real);
        long t0 = System.currentTimeMillis();
        check("השרת חזר — נשלחה לבד, בתוך פרק הזמן של הניסיון החוזר", waitFor(() -> Submit.waiting() == 0, 8000), Submit.waiting());
        System.out.println("  ..   נשלחה אחרי " + (System.currentTimeMillis() - t0) + " אלפיות שנייה");
        check("אצל המורה: ההגשה הגיעה", waitFor(() -> atTeacher(tt, a1).contains("queue-sum-even"), 5000), atTeacher(tt, a1));
        check("חלון השאלה התעדכן לבד: 'הוגש למורה רינה אברהם ב-…'", waitFor(() -> SubmitIt.line("SUBMIT-NOTE").startsWith("SUBMIT-NOTE הוגש למורה רינה אברהם ב-"), 5000), SubmitIt.line("SUBMIT-NOTE"));
        check("הסימון בקובץ ירד", !progressFile(work).matches("(?s).*\"queue-sum-even\":\\{[^}]*submitQueued.*"), "");
        SubmitIt.ui(() -> { if (BankVsFloat.current != null) BankVsFloat.current.dispose(); return null; });

        // ---------------------------------------------------------------- 3. תהליך חדש (התוכנה נפתחת) שולח את מה שמחכה
        System.setProperty("dsviz.submitRetryMs", "600000");
        System.setProperty("dsviz.worker", OFFLINE);
        check("בלי אינטרנט: עוד הגשה נשמרה (tree-count-sum)", "queued".equals(submit("tree-count-sum", "עץ בינארי", "מספר הצמתים וסכום העץ").status) && Submit.waiting() == 1, Submit.waiting());
        // שיעורי הבית: כל השאר הוגש, וזו מחכה — הכפתור שקט ("נשמרה במחשב")
        SubmitIt.ui(() -> { BankVsWindow.open(null, null); return null; });
        check("הכפתור: בלי זוהר, 'נשמרה במחשב — תישלח לבד'", waitFor(() -> SubmitIt.ui(() -> BankVsWindow.current.homework != null
                && !BankVsWindow.current.hwButton.glowing() && BankVsWindow.current.homework.detail().equals("נשמרה במחשב — תישלח לבד")), 5000),
                SubmitIt.ui(() -> BankVsWindow.current.homework == null ? "" : BankVsWindow.current.homework.label()));
        HomeworkIt.shots = shots;
        HomeworkIt.shot("o1-swing-waiting");
        SubmitIt.ui(() -> { BankVsWindow.current.homeworkClicked(); return null; });
        Thread.sleep(400);
        check("ברשימה: 'נשמר במחשב — יישלח כשיהיה אינטרנט'", BankVsHomework.listShown.contains("ITEM tree-count-sum מספר הצמתים וסכום העץ · עץ בינארי · " + BankHomework.WAITING), BankVsHomework.listShown);
        HomeworkIt.shot("o2-swing-waiting-list");
        SubmitIt.ui(() -> { BankVsHomework.list.setVisible(false); BankVsWindow.current.frame.dispose(); return null; });
        List<String> cmd = new ArrayList<String>();
        cmd.add(new File(System.getProperty("java.home"), "bin/java").getPath());
        for (String k : new String[] { "dsviz.passKey", "dsviz.noOpen", "dsviz.bank", "dsviz.bankProgress", "dsviz.submits", "user.home", "dsviz.machine", "dsviz.machineDir", "dsviz.home" })
            cmd.add("-D" + k + "=" + System.getProperty(k));
        cmd.add("-Ddsviz.worker=" + real);
        cmd.add("-Djava.awt.headless=true");
        cmd.add("-Dstdout.encoding=UTF-8");
        cmd.add("-cp");
        cmd.add(System.getProperty("java.class.path"));
        cmd.add(OutboxIt.class.getName());
        cmd.add("--child");
        cmd.add("15000");
        Process child = new ProcessBuilder(cmd).redirectErrorStream(true).start();
        String childOut = new String(readAll(child.getInputStream()), StandardCharsets.UTF_8).trim();
        int code2 = child.waitFor();
        check("תהליך חדש (UpdateCheck.start, כמו כשהתוכנה נפתחת) שלח את מה שחיכה", code2 == 0 && childOut.contains("CHILD before=1 after=0"), childOut);
        check("אצל המורה: ההגשה הגיעה", waitFor(() -> atTeacher(tt, a1).contains("tree-count-sum"), 5000), atTeacher(tt, a1));
        check("בתהליך הזה: תיבת היוצא ריקה, וחלון השאלה כבר לא אומר 'נשמרה במחשב'", Submit.waiting() == 0
                && BankProgress.get().submittedNote("tree-count-sum", Submit.teacher()).startsWith("הוגש למורה"), BankProgress.get().submittedNote("tree-count-sum", Submit.teacher()));
        BankHomework h = BankHomework.current();
        check("שיעורי הבית: הכול הוגש", h != null && h.allDone(), h == null ? "" : h.label());

        // ---------------------------------------------------------------- 4. מחובר מישהו אחר: ההגשה של A מחכה ל-A
        System.setProperty("dsviz.submitRetryMs", "1000");
        check("בלי אינטרנט: A מגיש עוד תרגיל — נשמר", "queued".equals(submit("stack-brackets", "מחסנית", "סוגריים מאוזנים").status) && Submit.waiting() == 1, Submit.waiting());
        AccountIt.use(work, "pcO", "pupilB");   // אותו מחשב, תלמיד אחר (B) נכנס — ורק אז האינטרנט חוזר
        System.setProperty("dsviz.worker", real);
        String b1 = "outbox.b" + System.nanoTime() + "@test.dev";
        AccountIt.browserEmail = b1;
        check("תלמיד B נכנס באותו מחשב", Account.signIn("student", null, null, 30000).ok() && Account.studentJoin(code, false).ok(), "");
        Submit.flushAsync();
        Thread.sleep(3500);   // כמה סיבובים של חוט הרקע
        check("B מחובר: ההגשה של A לא נשלחת בשמו (ממשיכה לחכות)", Submit.waiting() == 1 && Submit.flush() == 0 && !atTeacher(tt, a1).contains("stack-brackets")
                && !atTeacher(tt, b1).contains("stack-brackets"), Submit.waiting());
        AccountIt.use(work, "pcO", "pupilA");   // A חוזר
        check("A מחובר שוב — נשלחת לבד", waitFor(() -> Submit.waiting() == 0, 8000) && waitFor(() -> atTeacher(tt, a1).contains("stack-brackets"), 5000), Submit.waiting());
        Map<String, Object> con = Json.obj(Json.parse(AccountIt.api("GET", "/v1/me/console", tt, null)));
        int works = 0;
        for (Object o : Json.arr(con.get("students"))) if (a1.equals(Json.obj(o).get("email"))) works = Json.arr(Json.obj(o).get("work")).size();
        check("בעמדת הניהול של המורה: 4 התרגילים של A", works == 4, works);

        for (Window w : Window.getWindows()) w.dispose();
        System.out.println("\n" + pass + " עברו, " + fail + " נכשלו");
        System.exit(fail == 0 ? 0 : 1);
    }

    static byte[] readAll(java.io.InputStream in) throws java.io.IOException {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        byte[] b = new byte[8192];
        int n;
        while ((n = in.read(b)) > 0) out.write(b, 0, n);
        return out.toByteArray();
    }

    /** תהליך הבן: מה שהתוכנה עושה כשהיא נפתחת (UpdateCheck.start) — ומחכה שתיבת היוצא תתרוקן */
    static void child(String[] a) throws Exception {
        long end = System.currentTimeMillis() + Long.parseLong(a[1]);
        int before = Submit.waiting();
        UpdateCheck.start("-FromIde");
        while (System.currentTimeMillis() < end && Submit.waiting() > 0) Thread.sleep(200);
        System.out.println("CHILD before=" + before + " after=" + Submit.waiting());
        System.exit(before > 0 && Submit.waiting() == 0 ? 0 : 3);
    }
}
