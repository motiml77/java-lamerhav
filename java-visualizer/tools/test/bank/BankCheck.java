package il.lamerhav.dsviz;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * בדיקה של כל שאלה במאגר התרגילים (bank/) — שאלה שלא עוברת לא נכנסת לתוסף:
 *   1. הקובץ תקין: id, נושא, רמת קושי, כותרת, נוסח; id לא חוזר; הקובץ בתיקייה של הנושא שלו
 *   2. לכל סעיף של קוד (exercise.tasks) — נוסח בשאלה (question.sections) עם אותה אות, והצעה לפתרון
 *   3. המעטפת שהתלמיד מקבל מתקמפלת (אחרי שהוא כותב return בכל פעולה)
 *   4. עם ההצעות לפתרון: מתקמפל, רץ, ומדפיס בדיוק את הפלט הרצוי (expectedOutput — מה שהתלמיד רואה מעל main)
 *   5. "הרצה ויזואלית" עם ההצעות: רצה עד הסוף, בלי חריגה
 *   java -cp <classes> il.lamerhav.dsviz.BankCheck <תיקיית המאגר> <dsviz.jar>
 */
public final class BankCheck {
    private static int fails;
    /** כמה צעדים לכל היותר בהרצה הוויזואלית של שאלה (עם ההצעות לפתרון) */
    static final int MAX_STEPS = 2000;

    public static void main(String[] a) throws Exception {
        File dir = new File(a[0]);
        File lib = new File(a[1]);
        BankStore s = new BankStore();
        s.loadDir(dir);
        for (String[] p : s.problems) fail(p[0], p[1]);
        Set<String> ids = new HashSet<String>();
        for (File sub : sorted(dir.listFiles(File::isDirectory))) {
            for (File f : sorted(sub.listFiles((d, n) -> n.endsWith(".json")))) {
                Map<String, Object> j = Json.obj(Json.parse(new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8)));
                String id = Json.str(j.get("id"));
                if (!ids.add(id)) fail(f.getPath(), "ה-id \"" + id + "\" כבר קיים בשאלה אחרת");
                if (!sub.getName().equals(Json.str(j.get("topic")))) fail(f.getPath(), "הקובץ בתיקייה " + sub.getName() + " אבל הנושא שלו " + Json.str(j.get("topic")));
                if (!f.getName().equals(id + ".json")) fail(f.getPath(), "שם הקובץ צריך להיות " + id + ".json");
            }
        }
        int n = 0;
        for (BankStore.Question q : s.questions) {
            n++;
            check(q, lib);
        }
        System.out.println(fails == 0 ? "מאגר התרגילים: " + n + " שאלות — כולן תקינות" : "מאגר התרגילים: " + fails + " בעיות");
        System.exit(fails == 0 ? 0 : 1);
    }

    private static void check(BankStore.Question q, File lib) throws Exception {
        String where = q.origin;
        AiScaffold.Exercise e = AiScaffold.read(q.exercise);
        if (e.cs) { fail(where, "רק Java בשלב הזה (language: java)"); return; }
        int jsonTasks = Json.arr(q.exercise.get("tasks")).size();
        if (e.tasks.size() != jsonTasks) fail(where, "רק " + e.tasks.size() + " מתוך " + jsonTasks + " סעיפי הקוד נקראו (חתימה כפולה / חסרה?)");
        Set<String> labels = new HashSet<String>();
        for (BankStore.Section sec : q.sections) {
            if (!labels.add(norm(sec.label))) fail(where, "האות של הסעיף \"" + sec.label + "\" חוזרת פעמיים בנוסח");
        }
        for (AiScaffold.Task t : e.tasks) {
            if (!q.sections.isEmpty() && !labels.contains(norm(t.label))) fail(where, "לסעיף " + t.label + " (" + t.methodName + ") אין נוסח ב-question.sections");
            if (t.solution.isEmpty()) fail(where, "לסעיף " + t.label + " (" + t.methodName + ") אין הצעה לפתרון");
        }
        if (q.expectedOutput().isEmpty()) fail(where, "חסר הפלט הרצוי (exercise.expectedOutput) — התלמיד לא יידע אם הגיע לתוצאה");
        String pkg = "ex01_check";
        File work = Files.createTempDirectory("bank-check").toFile();
        try {
            // 3. המעטפת של התלמיד (עם return זמני — כמו אחרי שהוא כותב את התשובה)
            AiScaffold.testReturns = true;
            List<File> files = write(AiScaffold.build(e, pkg, Collections.<String>emptyList()), new File(work, "stub/" + pkg));
            AiScaffold.testReturns = false;
            File bin = new File(work, "stub-bin");
            bin.mkdirs();
            List<String> errors = SolutionCheck.javacErrors(files, bin, lib);
            if (errors != null && !errors.isEmpty()) fail(where, "המעטפת לא מתקמפלת: " + errors.subList(0, Math.min(3, errors.size())));
            // 4. ההצעות נותנות את הפלט הרצוי
            SolutionCheck.Result r = SolutionCheck.run(e, pkg, lib, null, new File(work, "solution"), 20);
            if (r.status != SolutionCheck.Status.OK) fail(where, "ההצעות לפתרון: " + r.status + " " + r.problems);
            // 5. הרצה ויזואלית עם ההצעות
            File vis = new File(work, "visual");
            List<File> sol = write(AiScaffold.build(e, pkg, Collections.<String>emptyList(), true), new File(vis, pkg));
            List<String> vErr = SolutionCheck.javacErrors(sol, vis, lib);
            if (vErr != null && !vErr.isEmpty()) { fail(where, "הרצה ויזואלית: לא מתקמפל " + vErr); return; }
            String out = visual(vis, lib, pkg + "." + e.mainClass);
            if (!out.contains("התוכנית הסתיימה") || out.contains("Exception")) fail(where, "הרצה ויזואלית לא הגיעה לסוף:\n" + tail(out));
            // ההרצה הוויזואלית של התלמיד צריכה להיות קצרה מספיק כדי לעבור עליה (ולא להגיע למגבלת הצעדים): main עם בדיקות קטנות
            int steps = 0;
            java.util.regex.Matcher sm = java.util.regex.Pattern.compile("#(\\d+)").matcher(out);
            while (sm.find()) steps = Math.max(steps, Integer.parseInt(sm.group(1)));
            if (steps > MAX_STEPS) fail(where, "ההרצה הוויזואלית ארוכה מדי: " + steps + " צעדים (עד " + MAX_STEPS + ") — להקטין את הבדיקות ב-main");
            System.out.println("  ok   " + q.id + "  [" + q.level.title + "]  " + q.title + "  (" + e.tasks.size() + " סעיפים)");
        } finally {
            AiScaffold.testReturns = false;
            delete(work);
        }
    }

    /** כמו "הרצה ויזואלית" בלי מסך: LineByLine מקבל את המחלקה, והקבצים שליד המחלקות (כדי להראות את השורות) */
    private static String visual(File dir, File lib, String main) throws Exception {
        String java = new File(System.getProperty("java.home"), "bin/java").getPath();
        ProcessBuilder pb = new ProcessBuilder(java, "-Djava.awt.headless=true", "-Dstdout.encoding=UTF-8", "-Dfile.encoding=UTF-8",
                "-Ddsviz.visual=true", "-cp", "." + File.pathSeparator + lib.getAbsolutePath(), "unit4.collectionsLib.LineByLine", main)
                .directory(dir).redirectErrorStream(true);
        Process p = pb.start();
        p.getOutputStream().close();
        byte[] all = ProjectSetupFree.readAll(p.getInputStream());
        if (!p.waitFor(120, TimeUnit.SECONDS)) p.destroyForcibly();
        return new String(all, StandardCharsets.UTF_8);
    }

    private static List<File> write(List<AiScaffold.GeneratedFile> gen, File dir) throws Exception {
        dir.mkdirs();
        List<File> out = new ArrayList<File>();
        for (AiScaffold.GeneratedFile g : gen) {
            File f = new File(dir, g.fileName());
            Files.write(f.toPath(), g.code.getBytes(StandardCharsets.UTF_8));
            out.add(f);
        }
        return out;
    }

    private static String norm(String label) {
        return label == null ? "" : label.replaceAll("[\\s'׳\"״.)]", "");
    }

    private static void fail(String where, String why) {
        fails++;
        System.out.println("  FAIL " + where + ": " + why);
    }

    private static String tail(String s) {
        String[] l = s.split("\n");
        StringBuilder b = new StringBuilder();
        for (int i = Math.max(0, l.length - 12); i < l.length; i++) b.append("      ").append(l[i]).append('\n');
        return b.toString();
    }

    private static File[] sorted(File[] f) {
        if (f == null) return new File[0];
        java.util.Arrays.sort(f);
        return f;
    }

    private static void delete(File f) {
        File[] k = f.listFiles();
        if (k != null) for (File c : k) delete(c);
        f.delete();
    }

    /** קריאת זרם עד הסוף (בלי ProjectSetup — שתלוי ב-Eclipse) */
    private static final class ProjectSetupFree {
        static byte[] readAll(java.io.InputStream in) throws java.io.IOException {
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            for (int n; (n = in.read(buf)) > 0;) out.write(buf, 0, n);
            return out.toByteArray();
        }
    }
}
