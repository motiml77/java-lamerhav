package il.lamerhav.dsviz;

import java.awt.Component;
import java.awt.Container;
import java.awt.Window;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

import javax.imageio.ImageIO;
import javax.swing.AbstractButton;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

/**
 * "הגשה למורה" מקצה לקצה, מול השרת המקומי והאמולטור — החלון "השאלה" של Visual Studio / VS Code / IntelliJ (BankVsFloat, כאן במצב
 * Java כמו ב-IntelliJ): תלמיד עצמאי — בלי כפתור; תלמיד שמשויך למורה — "הגשה למורה <שם>": בדיקת ההרצה (פלט שונה / תואם / שגיאת
 * קומפילציה), השליחה, ומה שהמורה רואה בעמדת הניהול (הקוד, הפלט מול הפלט הרצוי); הגשה חוזרת מחליפה; "סיימתי את התרגיל" מגיש לפני
 * המחיקה; עמדת מחשב — "שם המגיש" חובה ולא נזכר; בלי אינטרנט — ההגשה נשמרת ונשלחת כשיש חיבור; מורה — בלי כפתור.
 *   java ... SubmitIt <שרת> <אמולטור auth> <תיקיית עבודה> <צילומים> <dsviz.jar / תיקיית המחלקות> <תיקיית bank>
 */
public class SubmitIt {
    static int pass, fail;
    static File shots;

    static void check(String name, boolean ok, Object got) {
        if (ok) pass++; else fail++;
        System.out.println((ok ? "  ok   " : "  FAIL ") + name + (ok ? "" : "  ← " + got));
    }

    static <T> T ui(Callable<T> c) throws Exception {
        final Object[] out = new Object[2];
        SwingUtilities.invokeAndWait(() -> {
            try { out[0] = c.call(); } catch (Exception e) { out[1] = e; }
        });
        if (out[1] != null) throw (Exception) out[1];
        @SuppressWarnings("unchecked") T t = (T) out[0];
        return t;
    }

    static Component byId(Component c, String id) {
        if (c instanceof JComponent && id.equals(((JComponent) c).getClientProperty("dsviz.id")) && c.isShowing()) return c;
        if (c instanceof Container) for (Component k : ((Container) c).getComponents()) {
            Component r = byId(k, id);
            if (r != null) return r;
        }
        return null;
    }

    static Component waitId(String id, long ms) throws Exception {
        long end = System.currentTimeMillis() + ms;
        while (System.currentTimeMillis() < end) {
            Component c = ui(() -> BankVsFloat.current == null ? null : byId(BankVsFloat.current, id));
            if (c != null) return c;
            Thread.sleep(150);
        }
        return null;
    }

    static boolean click(String id) throws Exception {
        final Component c = waitId(id, 15000);
        if (!(c instanceof AbstractButton)) { System.out.println("  ..   אין כפתור " + id + " · " + shown()); return false; }
        if (!ui(() -> c.isEnabled())) { System.out.println("  ..   הכפתור " + id + " לא פעיל"); return false; }
        SwingUtilities.invokeAndWait(((AbstractButton) c)::doClick);
        Thread.sleep(300);
        return true;
    }

    static boolean clickText(String text) throws Exception {
        long end = System.currentTimeMillis() + 15000;
        while (System.currentTimeMillis() < end) {
            final AbstractButton b = ui(() -> BankVsFloat.current == null ? null : GateUi.find(BankVsFloat.current, text));
            if (b != null) { SwingUtilities.invokeAndWait(b::doClick); Thread.sleep(300); return true; }
            Thread.sleep(150);
        }
        System.out.println("  ..   אין כפתור '" + text + "' · " + shown());
        return false;
    }

    static List<String> shown() throws Exception {
        return ui(() -> new ArrayList<String>(BankVsFloat.shown));
    }

    static boolean has(String prefix) throws Exception {
        for (String s : shown()) if (s.startsWith(prefix)) return true;
        return false;
    }

    static String line(String prefix) throws Exception {
        for (String s : shown()) if (s.startsWith(prefix)) return s;
        return "";
    }

    static boolean waitShown(String prefix, long ms) throws Exception {
        long end = System.currentTimeMillis() + ms;
        while (System.currentTimeMillis() < end) {
            if (has(prefix)) return true;
            Thread.sleep(150);
        }
        return false;
    }

    /** כל הטקסט שמוצג בחלון השאלה (תוויות, כפתורים, שדות) — בלי תגי HTML */
    static String windowText() throws Exception {
        return ui(() -> {
            StringBuilder b = new StringBuilder();
            collect(BankVsFloat.current, b);
            return b.toString().replaceAll("(?s)<style.*?</style>", " ").replaceAll("<[^>]+>", " ").replace("&nbsp;", " ");
        });
    }

    static void collect(java.awt.Component c, StringBuilder b) {
        if (c == null) return;
        if (c instanceof javax.swing.JLabel) b.append(((javax.swing.JLabel) c).getText()).append('\n');
        else if (c instanceof javax.swing.text.JTextComponent) b.append(((javax.swing.text.JTextComponent) c).getText()).append('\n');
        else if (c instanceof javax.swing.AbstractButton) b.append(((javax.swing.AbstractButton) c).getText()).append('\n');
        if (c instanceof java.awt.Container) for (java.awt.Component k : ((java.awt.Container) c).getComponents()) collect(k, b);
    }

    // ---- החלון "המורה שלי" (AccessGate.MyTeacher)

    static boolean waitTeacherWindow() throws Exception {
        long end = System.currentTimeMillis() + 10000;
        while (System.currentTimeMillis() < end) {
            if (ui(() -> AccessGate.myTeacher != null && AccessGate.myTeacher.isShowing())) return true;
            Thread.sleep(150);
        }
        return false;
    }

    static void typeCode(final String text) throws Exception {
        ui(() -> {
            Component c = byId(AccessGate.myTeacher, "teacher-code");
            if (c instanceof javax.swing.JTextField) ((javax.swing.JTextField) c).setText(text);
            AccessGate.myTeacher.lastJoin = "";
            return null;
        });
    }

    static boolean clickIn(String id) throws Exception {
        final Component c = ui(() -> AccessGate.myTeacher == null ? null : byId(AccessGate.myTeacher, id));
        if (!(c instanceof AbstractButton)) { System.out.println("  ..   אין כפתור " + id + " בחלון 'המורה שלי' · " + teacherText()); return false; }
        SwingUtilities.invokeAndWait(((AbstractButton) c)::doClick);
        Thread.sleep(300);
        return true;
    }

    /** התשובה של ההצטרפות האחרונה הגיעה */
    static boolean waitJoin(String status) throws Exception {
        long end = System.currentTimeMillis() + 20000;
        while (System.currentTimeMillis() < end) {
            if (status.equals(ui(() -> AccessGate.myTeacher == null ? "" : AccessGate.myTeacher.lastJoin))) { Thread.sleep(300); return true; }
            Thread.sleep(150);
        }
        return false;
    }

    /** המורה של התלמיד (כמו שנשמר במחשב) הוא name ("" — אין מורה) */
    static boolean waitTeacher(String name) throws Exception {
        long end = System.currentTimeMillis() + 20000;
        while (System.currentTimeMillis() < end) {
            if (name.equals(AccessGate.studentTeacher())) { Thread.sleep(400); return true; }
            Thread.sleep(150);
        }
        return false;
    }

    static String teacherText() throws Exception {
        return ui(() -> {
            StringBuilder b = new StringBuilder();
            collect(AccessGate.myTeacher, b);
            return b.toString().replaceAll("<[^>]+>", " ").replace('\n', ' ');
        });
    }

    static void shotTeacher(String name) throws Exception {
        Thread.sleep(400);
        final java.awt.Window w = ui(() -> AccessGate.myTeacher);
        if (w == null) return;
        final BufferedImage[] img = new BufferedImage[1];
        SwingUtilities.invokeAndWait(() -> {
            img[0] = new BufferedImage(w.getWidth(), w.getHeight(), BufferedImage.TYPE_INT_RGB);
            w.paint(img[0].getGraphics());
        });
        ImageIO.write(img[0], "png", new File(shots, name + ".png"));
    }

    static void shot(String name) throws Exception {
        Thread.sleep(400);
        final JFrame f = ui(() -> BankVsFloat.current);
        if (f == null) return;
        final BufferedImage[] img = new BufferedImage[1];
        SwingUtilities.invokeAndWait(() -> {
            img[0] = new BufferedImage(f.getWidth(), f.getHeight(), BufferedImage.TYPE_INT_RGB);
            f.paint(img[0].getGraphics());
        });
        ImageIO.write(img[0], "png", new File(shots, name + ".png"));
    }

    static Map<String, Object> json(String s) {
        return Json.obj(Json.parse(s));
    }

    static List<Object> submissions(String teacherTok, String query) throws Exception {
        return Json.arr(json(AccountIt.api("GET", "/v1/me/submissions?" + query, teacherTok, null)).get("submissions"));
    }

    static void openFloat(final BankStore.Question q) throws Exception {
        ui(() -> { BankVsFloat.show(q); return null; });
        Thread.sleep(700);
    }

    /** דף ההגשה נפתח, ובדיקת ההרצה הסתיימה */
    static boolean openSubmitPage() throws Exception {
        ui(() -> { BankVsFloat.shown.clear(); return null; });
        if (!click("submit-open")) return false;
        long end = System.currentTimeMillis() + 60000;
        while (System.currentTimeMillis() < end) {
            String s = line("SUBMIT-PAGE");
            if (!s.isEmpty() && !s.contains("checking")) return true;
            Thread.sleep(200);
        }
        return false;
    }

    /** לוחץ "הגשה" ומחכה שההגשה תסתיים. מחזיר את התוצאה (ok / queued / קוד שגיאה), או "" */
    static String sendAndWait() throws Exception {
        if (!click("submit-yes")) return "";
        long end = System.currentTimeMillis() + 30000;
        while (System.currentTimeMillis() < end) {
            String r = ui(() -> BankVsFloat.submitResult);
            if (r != null) return r;
            Thread.sleep(150);
        }
        return "";
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
        String teacher = "sub.teacher" + System.nanoTime() + "@test.dev";
        AccountIt.api("POST", "/v1/admin/teacher", admin, "{\"create\":true,\"email\":\"" + teacher + "\",\"name\":\"שרה כהן\",\"school\":\"תיכון לדוגמה\",\"seats\":2}");
        String code = Json.str(Json.obj(json(AccountIt.api("GET", "/v1/admin/teacher?email=" + teacher, admin, null)).get("teacher")).get("code"));
        String teacherTok = AccountIt.idToken(teacher);

        // ---------------------------------------------------------------- תלמיד
        AccountIt.use(work, "pcS", "pupil");
        String student = "sub.pupil" + System.nanoTime() + "@test.dev";
        AccountIt.browserEmail = student;
        check("תלמיד נכנס (נרשם בדפדפן)", Account.signIn("student", null, null, 30000).ok(), "");
        check("תלמיד עצמאי — אין למי להגיש", Submit.mode().isEmpty() && !Submit.available(), Submit.mode());

        File src = new File(work, "project/src");
        src.mkdirs();
        String javaExe = SolutionCheck.javaOf(System.getProperty("java.home"));
        BankVs.configure(new String[] { "bank", src.getPath(), "--ij", "--lib", lib.getPath(), "--java", javaExe, "--project", "Demo", "--store", new File(work, "solutions.json").getPath() });
        BankStore.Question found = null;
        for (BankStore.Question x : BankVs.store.questions) if (x.id.equals("stack-sum-size")) found = x;
        final BankStore.Question q = found;
        check("השאלה stack-sum-size במאגר", q != null && !q.expectedOutput().isEmpty(), "");
        BankVs.create(q, new ExercisePipeline.Progress() {
            @Override public void say(String text) { }
            @Override public boolean cancelled() { return false; }
        });
        File dir = BankVs.projects(q).get(0);
        check("נוצר תרגיל מהשאלה (" + dir.getName() + ")", new File(dir, "StackTargil1.java").isFile(), dir.list() == null ? "" : String.join(",", dir.list()));

        openFloat(q);
        check("תלמיד עצמאי: בחלון השאלה אין 'הגשה למורה' — במקומו קישור להצטרפות עם קוד המורה", !has("SUBMIT-BUTTON") && has("SUBMIT-JOIN")
                && ui(() -> byId(BankVsFloat.current, "submit-open")) == null, shown());
        check("תלמיד בלי מורה: הקישור במאגר הוא 'הצטרפות למורה'", "הצטרפות למורה".equals(AccessGate.teacherLink()) && AccessGate.isStudent() && AccessGate.studentTeacher().isEmpty(), AccessGate.teacherLink());
        shot("s0-question-join-hint");

        // "המורה שלי" — ההצטרפות עם קוד המורה מתוך התוכנה (בלי הדפדפן): קוד שגוי → הסבר; קוד נכון → הכפתור "הגשה למורה" מופיע לבד
        check("הקישור פותח את החלון 'הצטרפות למורה'", click("submit-join") && waitTeacherWindow(), "");
        typeCode("ZZZZ");
        clickIn("teacher-join");
        check("קוד שגוי: 'הקוד לא נמצא', ועדיין אין מורה", waitJoin("bad-code") && teacherText().contains("הקוד לא נמצא") && AccessGate.studentTeacher().isEmpty(), teacherText());
        shotTeacher("t1-my-teacher-bad-code");
        typeCode("12");
        clickIn("teacher-join");
        check("קוד קצר מדי: הסבר מיד, בלי פנייה לשרת", teacherText().contains("4 תווים"), teacherText());
        typeCode(code.toLowerCase());
        clickIn("teacher-join");
        check("קוד נכון (גם באותיות קטנות): הצטרף — 'מעכשיו מגישים למורה שרה כהן'", waitJoin("ok") && waitTeacher("שרה כהן") && teacherText().contains("מגישים למורה שרה כהן")
                && ui(() -> byId(AccessGate.myTeacher, "teacher-name")) != null, teacherText());
        shotTeacher("t2-my-teacher-joined");
        check("מגישים כתלמיד, למורה שרה כהן; הקישור במאגר הפך ל'המורה שלי'", "student".equals(Submit.mode()) && "שרה כהן".equals(Submit.teacher())
                && "הגשה למורה שרה כהן".equals(Submit.label()) && "המורה שלי: שרה כהן".equals(AccessGate.teacherLink()), Submit.mode() + " / " + Submit.teacher());
        check("אצל המורה: התלמיד מופיע ברשימה (בלי כיתה)", AccountIt.api("GET", "/v1/me/console", teacherTok, null).contains(student), "");
        check("חלון השאלה התעדכן לבד: הכפתור 'הגשה למורה שרה כהן' (בלי לפתוח מחדש)", waitShown("SUBMIT-BUTTON הגשה למורה שרה כהן", 8000) && !has("SUBMIT-JOIN"), shown());
        // יציאה מהמורה וחזרה (עם אישור): הכפתור נעלם ומופיע
        AccessGate.confirmForTest = Boolean.TRUE;
        clickIn("teacher-leave");
        check("יציאה מהמורה (עם אישור): אין למי להגיש, והכפתור בחלון השאלה נעלם", waitTeacher("") && Submit.mode().isEmpty() && waitShown("SUBMIT-JOIN", 8000) && !has("SUBMIT-BUTTON"), shown() + " " + teacherText());
        typeCode(code);
        clickIn("teacher-join");
        check("הצטרפות חוזרת — אותו מורה, והכפתור חזר", waitJoin("ok") && waitTeacher("שרה כהן") && waitShown("SUBMIT-BUTTON הגשה למורה שרה כהן", 8000), shown());
        AccessGate.confirmForTest = null;
        clickIn("teacher-close");
        check("בחלון השאלה: הכפתור 'הגשה למורה שרה כהן'", has("SUBMIT-BUTTON הגשה למורה שרה כהן"), shown());
        shot("s1-question-with-submit");

        // 1. התרגיל כמו שנוצר (הפעולה עוד ריקה — בלי return): לא מתקמפל; הדף אומר את זה, ואפשר להגיש בכל זאת
        check("דף ההגשה נפתח, ובדיקת ההרצה הסתיימה", openSubmitPage(), shown());
        String page = line("SUBMIT-PAGE");
        check("לפני שכותבים את הפעולה: הקוד לא מתקמפל (" + line("SUBMIT-CHECK") + ")", page.contains("bad") && line("SUBMIT-CHECK").contains("לא מתקמפל"), page);
        // לתלמיד מוצג רק כמה שגיאות יש — בלי הרשימה (בלי ההודעות של הקומפיילר ובלי מספרי שורות)
        String seen = windowText();
        check("דף ההגשה: רק מספר השגיאות ('יש בו … שגיא'), בלי פירוט השגיאות", line("SUBMIT-CHECK").matches(".*יש בו (שגיאה אחת|\\d+ שגיאות)\\.")
                && !seen.contains("missing return") && !seen.matches("(?s).*שורה \\d+.*") && seen.contains("יש בו"), line("SUBMIT-CHECK") + " | " + seen.replace('\n', ' '));
        check("בעמדת תלמיד אין שדה 'שם המגיש'", !has("SUBMIT-WHO"), shown());
        shot("s2-submit-page-not-compiling");
        check("'הגשה' — נשלח", "ok".equals(sendAndWait()), shown());
        Thread.sleep(500);
        check("חוזרים לשאלה, עם 'הוגש למורה ב-…'", line("SUBMIT-NOTE").matches("SUBMIT-NOTE הוגש למורה ב-\\d{1,2}:\\d{2}\\."), line("SUBMIT-NOTE"));
        shot("s3-submitted-note");
        // גם אחרי שסוגרים ופותחים מחדש (ההערה של הרגע נשכחה): ההגשה רשומה במחשב — "הוגש למורה שרה כהן ב-…"
        ui(() -> { BankVsFloat.submitNote = null; BankVsFloat.shown.clear(); return null; });
        openFloat(q);
        check("פתיחה מחדש של השאלה: עדיין כתוב שהיא הוגשה, למי ומתי (" + line("SUBMIT-NOTE") + ")", line("SUBMIT-NOTE").matches("SUBMIT-NOTE הוגש למורה שרה כהן ב-\\d{1,2}\\.\\d{1,2} בשעה \\d{1,2}:\\d{2}\\."), shown());
        List<Object> subs = submissions(teacherTok, "student=" + Account.enc(student));
        Map<String, Object> s0 = subs.isEmpty() ? Collections.<String, Object>emptyMap() : Json.obj(subs.get(0));
        Map<String, Object> ex0 = Json.obj(s0.get("exercise")), ck0 = Json.obj(s0.get("check"));
        check("אצל המורה: הגשה אחת של התלמיד, עם השאלה, הנושא, הרמה והתוכנה", subs.size() == 1 && "stack-sum-size".equals(ex0.get("bankId")) && q.title.equals(ex0.get("title"))
                && "bank".equals(ex0.get("source")) && "מחסנית".equals(ex0.get("topic")) && q.level.title.equals(ex0.get("level")) && "IntelliJ".equals(s0.get("app")), s0);
        check("אצל המורה: תוצאת בדיקת ההרצה — לא מתקמפל, עם השגיאה ומספר השורה; והפלט הרצוי של הסעיפים שהוכנו", Boolean.TRUE.equals(ck0.get("ran")) && Boolean.FALSE.equals(ck0.get("compiles"))
                && Json.str(ck0.get("error")).contains("StackTargil1.java, שורה")
                // הפלט הרצוי — רק של הסעיפים שהתלמיד הכין (BankSubmit.expectedFor), כלומר חלק מהפלט הרצוי המלא של השאלה
                && !Json.str(ck0.get("expected")).isEmpty() && SolutionCheck.clean(q.expectedOutput()).contains(Json.str(ck0.get("expected"))), ck0);
        Map<String, Object> full = json(AccountIt.api("GET", "/v1/me/submission?id=" + Json.str(s0.get("id")), teacherTok, null));
        List<Object> files = Json.arr(full.get("files"));
        String sent = files.isEmpty() ? "" : Json.str(Json.obj(files.get(0)).get("text"));
        // הציון: המורה נותן 85 → כשהתלמיד פותח את השאלה (ברקע, מול השרת) — "הציון מהמורה: 85"; נשמר במחשב (גם בלי אינטרנט)
        check("ההגשה עוד בלי ציון — אין שורת ציון", !has("SUBMIT-GRADE"), shown());
        AccountIt.api("POST", "/v1/me/submission/grade", teacherTok, "{\"id\":\"" + Json.str(s0.get("id")) + "\",\"grade\":85}");
        BankSubmit.resetGradesClock();
        ui(() -> { BankVsFloat.shown.clear(); return null; });
        openFloat(q);
        check("המורה נתן ציון → בחלון השאלה של התלמיד: 'הציון מהמורה: 85' (" + line("SUBMIT-GRADE") + ")", waitShown("SUBMIT-GRADE הציון מהמורה: 85", 15000), shown());
        shot("s3b-grade-from-teacher");
        check("הציון נשמר במחשב (קובץ ההתקדמות)", new String(Files.readAllBytes(new File(work, "progress.json").toPath()), StandardCharsets.UTF_8).contains("\"grade\":85"), "");
        check("אצל המורה: הקוד עצמו — הקובץ של main ראשון, כמו שהוא במחשב של התלמיד", !files.isEmpty() && "StackTargil1.java".equals(Json.obj(files.get(0)).get("path"))
                && sent.equals(new String(Files.readAllBytes(new File(dir, "StackTargil1.java").toPath()), StandardCharsets.UTF_8)) && "StackTargil1.java".equals(full.get("main")), full.get("main"));

        // 2. התלמיד פותר (כאן: הפתרון של המאגר) → הפלט תואם; הגשה חוזרת מחליפה את הקודמת
        AiScaffold.Exercise solved = AiScaffold.read(Json.obj(Json.parse(Json.write(q.exercise))));
        for (AiScaffold.GeneratedFile g : AiScaffold.build(solved, dir.getName(), Collections.<String>emptyList(), true))
            Files.write(new File(dir, g.fileName()).toPath(), g.code.getBytes(StandardCharsets.UTF_8));
        Thread.sleep(500);
        boolean opened2 = openSubmitPage();
        check("אחרי שפותרים: דף ההגשה — הפלט תואם לפלט הרצוי (" + line("SUBMIT-CHECK") + ")", opened2 && line("SUBMIT-PAGE").contains("good") && line("SUBMIT-CHECK").contains("תואם"), shown());
        shot("s4-submit-page-output-matches");
        check("הגשה חוזרת — נשלחה", "ok".equals(sendAndWait()), shown());
        Thread.sleep(500);
        check("ההודעה: במקום ההגשה הקודמת", line("SUBMIT-NOTE").contains("במקום ההגשה הקודמת"), line("SUBMIT-NOTE"));
        subs = submissions(teacherTok, "student=" + Account.enc(student));
        s0 = subs.isEmpty() ? Collections.<String, Object>emptyMap() : Json.obj(subs.get(0));
        // הגשה חוזרת אחרי הציון: הציון נשאר, עם "על ההגשה הקודמת"; המורה מעדכן ל-100 → מתעדכן גם אצל התלמיד
        BankSubmit.resetGradesClock();
        BankSubmit.syncGrades();
        check("אחרי הגשה חוזרת: 'הציון מהמורה: 85 (על ההגשה הקודמת)' (" + line("SUBMIT-GRADE") + ")", waitShown("SUBMIT-GRADE הציון מהמורה: 85 (על ההגשה הקודמת)", 8000), shown());
        AccountIt.api("POST", "/v1/me/submission/grade", teacherTok, "{\"id\":\"" + Json.str(s0.get("id")) + "\",\"grade\":100}");
        BankSubmit.syncGrades();
        check("המורה עדכן ל-100 — מתעדכן אצל התלמיד", waitShown("SUBMIT-GRADE הציון מהמורה: 100", 8000) && !line("SUBMIT-GRADE").contains("הקודמת"), shown());
        check("אצל המורה: עדיין הגשה אחת (הוחלפה, n=2), והפלט תואם", subs.size() == 1 && ((Number) s0.get("n")).intValue() == 2 && Boolean.TRUE.equals(Json.obj(s0.get("check")).get("outputMatch")), s0);

        // 3. תשובה שגויה (מתקמפלת ורצה, אבל מדפיסה משהו אחר): הדף אומר שהפלט שונה; המורה רואה את הפלט מול הפלט הרצוי
        File mainFile = new File(dir, "StackTargil1.java");
        String good = new String(Files.readAllBytes(mainFile.toPath()), StandardCharsets.UTF_8);
        Files.write(mainFile.toPath(), good.replaceFirst("return ([^;\\n]+);", "return 0;").getBytes(StandardCharsets.UTF_8));
        Thread.sleep(500);
        boolean opened3 = openSubmitPage();
        check("תשובה שגויה: דף ההגשה אומר שהפלט שונה מהרצוי (" + line("SUBMIT-CHECK") + ")", opened3 && line("SUBMIT-PAGE").contains("warn") && line("SUBMIT-CHECK").contains("שונה"), shown());
        shot("s5-submit-page-output-differs");
        check("הגשה עם התשובה השגויה — נשלחה", "ok".equals(sendAndWait()), shown());
        subs = submissions(teacherTok, "student=" + Account.enc(student));
        Map<String, Object> ck = Json.obj(Json.obj(subs.get(0)).get("check"));
        check("אצל המורה: מתקמפל, הפלט שונה — הפלט של התלמיד והפלט הרצוי", Boolean.TRUE.equals(ck.get("compiles")) && Boolean.FALSE.equals(ck.get("outputMatch"))
                && !Json.str(ck.get("output")).isEmpty() && !Json.str(ck.get("output")).equals(Json.str(ck.get("expected"))), ck);
        Files.write(mainFile.toPath(), good.getBytes(StandardCharsets.UTF_8));

        // 4. בלי אינטרנט: ההגשה נשמרת במחשב, ונשלחת כשיש חיבור
        Thread.sleep(500);
        check("דף ההגשה (לפני הניתוק)", openSubmitPage(), shown());
        System.setProperty("dsviz.worker", "http://127.0.0.1:9");
        check("בלי אינטרנט: 'הגשה' — נשמרה במחשב (queued)", "queued".equals(sendAndWait()) && Submit.waiting() == 1, shown() + " waiting=" + Submit.waiting());
        Thread.sleep(500);
        check("ההודעה מסבירה שההגשה תישלח כשיש חיבור", line("SUBMIT-NOTE").contains("תישלח"), line("SUBMIT-NOTE"));
        shot("s6-offline-queued");
        check("בלי אינטרנט — ההגשה ממשיכה לחכות", Submit.flush() == 0 && Submit.waiting() == 1, Submit.waiting());
        System.setProperty("dsviz.worker", a[0]);
        check("חזר האינטרנט — ההגשה שחיכתה נשלחה", Submit.flush() == 1 && Submit.waiting() == 0, Submit.waiting());
        subs = submissions(teacherTok, "student=" + Account.enc(student));
        check("אצל המורה: ההגשה שחיכתה הגיעה (n=4), והפלט שוב תואם", ((Number) Json.obj(subs.get(0)).get("n")).intValue() == 4 && Boolean.TRUE.equals(Json.obj(Json.obj(subs.get(0)).get("check")).get("outputMatch")), subs.get(0));

        // 5. "סיימתי את התרגיל": מגיש לפני המחיקה
        ui(() -> { BankVsFloat.shown.clear(); return null; });
        ui(() -> { doneButton(BankVsFloat.current).click(); return null; });
        check("'סיימתי את התרגיל' — דף האישור אומר שהתרגיל יוגש למורה", waitShown("DONE-SUBMITS", 8000), shown());
        shot("s7-done-confirm-submits");
        ui(() -> { BankVsFloat.shown.clear(); return null; });
        check("'סיום ומחיקה' → דף ההגשה ('הגשה וסיום')", click("done-yes") && waitShown("SUBMIT-PAGE", 8000) && line("SUBMIT-PAGE").contains("then-complete"), shown());
        long end = System.currentTimeMillis() + 60000;
        while (System.currentTimeMillis() < end && line("SUBMIT-CHECK").isEmpty()) Thread.sleep(200);
        check("קבצי התרגיל עוד במקום עד שההגשה עוברת", mainFile.isFile(), "");
        check("'הגשה וסיום' — נשלח", "ok".equals(sendAndWait()), shown());
        Thread.sleep(1500);
        check("אחרי ההגשה: השאלה מסומנת, קבצי התרגיל נמחקו, והחלון נסגר", BankProgress.get().done(q.id) && !mainFile.exists() && ui(() -> BankVsFloat.current == null || !BankVsFloat.current.isShowing()), "");
        subs = submissions(teacherTok, "student=" + Account.enc(student));
        check("אצל המורה: ההגשה של 'סיימתי' (n=5)", ((Number) Json.obj(subs.get(0)).get("n")).intValue() == 5, subs.get(0));
        Account.signOut();

        // ---------------------------------------------------------------- עמדת מחשב של הכיתה
        AccountIt.use(work, "pcT", "kid");
        BankProgress.get().setDone(q.id, false);
        check("המחשב מתחבר כעמדה בקוד המורה → מגישים כעמדה, למורה שרה כהן", Account.claimStation(code, "מעבדה · עמדה 3").ok() && "station".equals(Submit.mode()) && "שרה כהן".equals(Submit.teacher()), Submit.mode());
        BankVs.create(q, new ExercisePipeline.Progress() {
            @Override public void say(String text) { }
            @Override public boolean cancelled() { return false; }
        });
        dir = BankVs.projects(q).get(BankVs.projects(q).size() - 1);
        openFloat(q);
        check("בעמדה: הכפתור 'הגשה למורה שרה כהן'", has("SUBMIT-BUTTON הגשה למורה שרה כהן"), shown());
        check("דף ההגשה בעמדה: יש 'שם המגיש'", openSubmitPage() && has("SUBMIT-WHO"), shown());
        final Component yes = waitId("submit-yes", 5000), who = waitId("submit-who", 5000);
        check("בלי שם — 'הגשה' לא פעיל", yes != null && who instanceof JTextField && !ui(() -> yes.isEnabled()) && ui(() -> ((JTextField) who).getText()).isEmpty(), "");
        shot("s8-station-who-empty");
        SwingUtilities.invokeAndWait(() -> ((JTextField) who).setText("נ"));
        check("אות אחת — עדיין לא פעיל", !ui(() -> waitIdNow("submit-yes").isEnabled()), "");
        SwingUtilities.invokeAndWait(() -> ((JTextField) who).setText("נועה לוי"));
        check("שם מלא — 'הגשה' פעיל", ui(() -> waitIdNow("submit-yes").isEnabled()), "");
        shot("s9-station-who-filled");
        check("הגשה מהעמדה — נשלחה", "ok".equals(sendAndWait()), shown());
        Map<String, Object> con = json(AccountIt.api("GET", "/v1/me/console", teacherTok, null));
        String stationId = "";
        for (Object o : Json.arr(con.get("stations"))) if ("מעבדה · עמדה 3".equals(Json.obj(o).get("name"))) stationId = Json.str(Json.obj(o).get("id"));
        subs = submissions(teacherTok, "station=" + stationId);
        s0 = subs.isEmpty() ? Collections.<String, Object>emptyMap() : Json.obj(subs.get(0));
        check("אצל המורה: הגשה מהעמדה עם שם המגיש, ועם תאריך מחיקה אוטומטית", subs.size() == 1 && "נועה לוי".equals(s0.get("who")) && "station".equals(s0.get("from")) && !Json.str(s0.get("expiresAt")).isEmpty(), s0);
        Thread.sleep(500);
        boolean opened4 = openSubmitPage();
        final Component who2 = waitId("submit-who", 5000);
        check("הגשה נוספת מאותה עמדה: השדה 'שם המגיש' ריק שוב (לא נזכר)", opened4 && who2 instanceof JTextField && ui(() -> ((JTextField) who2).getText()).isEmpty(), "");
        SwingUtilities.invokeAndWait(() -> ((JTextField) who2).setText("יואב כהן"));
        check("תלמיד אחר באותה עמדה, אותו תרגיל — הגשה נפרדת", "ok".equals(sendAndWait()) && submissions(teacherTok, "station=" + stationId).size() == 2, shown());
        // שרת ישן (בלי features: submit — למשל לפני שהשרת עודכן): אין כפתור שלא עובד; בדיקה מול שרת חדש מחזירה אותו
        File sf = new File(Account.stationFileForTest().split(" \\| ")[0]);
        Map<String, Object> saved = Account.load(sf);
        check("קובץ העמדה נשמר עם features: submit", saved != null && Json.arr(saved.get("features")).contains("submit"), saved == null ? "" : saved.get("features"));
        if (saved != null) {
            saved.remove("features");
            Account.save(sf, saved);
        }
        check("כניסה שנשמרה מול שרת ישן (בלי 'submit') — אין 'הגשה למורה'", Submit.mode().isEmpty() && !Submit.available(), Submit.mode());
        openFloat(q);
        check("ובחלון השאלה אין את הכפתור", !has("SUBMIT-BUTTON"), shown());
        Account.refresh();
        check("אחרי בדיקה מול השרת החדש — מגישים שוב כעמדה", "station".equals(Submit.mode()), Submit.mode());
        ui(() -> { if (BankVsFloat.current != null) BankVsFloat.current.dispose(); return null; });

        // ---------------------------------------------------------------- מורה: לא מגיש לעצמו
        AccountIt.use(work, "pcM", "teacher");
        AccountIt.browserEmail = teacher;
        check("מורה מחובר — אין 'הגשה למורה'", Account.signIn("teacher", null, null, 30000).ok() && Submit.mode().isEmpty(), Submit.mode());

        for (Window w : Window.getWindows()) w.dispose();
        System.out.println("\n" + pass + " עברו, " + fail + " נכשלו");
        System.exit(fail == 0 ? 0 : 1);
    }

    static BankVsFloat.DoneButton doneButton(Component c) {
        if (c instanceof BankVsFloat.DoneButton && c.isShowing()) return (BankVsFloat.DoneButton) c;
        if (c instanceof Container) for (Component k : ((Container) c).getComponents()) {
            BankVsFloat.DoneButton r = doneButton(k);
            if (r != null) return r;
        }
        return null;
    }

    static Component waitIdNow(String id) {
        return BankVsFloat.current == null ? null : byId(BankVsFloat.current, id);
    }
}
