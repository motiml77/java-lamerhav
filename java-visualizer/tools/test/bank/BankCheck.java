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
                for (String c : classNames(j)) {
                    if (UNIT4_NAMES.contains(c)) fail(f.getPath(), "המחלקה " + c + " מתנגשת בסוג של ספריית Unit4 "
                            + "(import unit4.collectionsLib.* / using Unit4.CollectionsLib שבכל תרגיל) — צריך שם אחר למחלקה");
                }
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

    /**
     * שמות של סוגים בספריית Unit4 שהקוד של כל תרגיל רואה: המעטפת תמיד מתחילה ב-{@code import unit4.collectionsLib.*;} / {@code using Unit4.CollectionsLib;}
     * (AiScaffold.build), והתרגיל רץ גם מול Unit4.dll של בתי הספר (BankSchoolCs). מחלקה של התרגיל בשם כזה — AiScaffold משמיט אותה
     * (LIBRARY), וב-C# היא מתנגשת בסוג של הספרייה. המבנים (גם StackInt של גרסת המפקחים ו-BinTreeNode של Java), העזרים שתלמידים מזמנים,
     * ו-List (java.util / System.Collections.Generic). Car ו-Student (מחלקות הדוגמה של DSViz) — לא כאן: במאגר יש להן מחלקה משלה, והיא גוברת
     * (נבדק: classes-car-allcars, classes-student-excellent — ב-dsviz-cs ומול Unit4.dll של בית הספר).
     */
    static final Set<String> UNIT4_NAMES = new HashSet<String>(java.util.Arrays.asList(
            "Node", "BinNode", "BinTreeNode", "Stack", "Queue", "StackInt", "List", "Helpers", "BinTreeUtils", "TreeCanvas", "AnimationSpeed"));

    /** כל המחלקות שהשאלה מגדירה — ב-Java וב-C#: classes, המחלקה של כל סעיף, ו-"class X" בתוך הקוד (גם בקוד הנתון) */
    static Set<String> classNames(Map<String, Object> q) {
        Set<String> out = new java.util.TreeSet<String>();
        java.util.regex.Pattern decl = java.util.regex.Pattern.compile("\\b(?:class|interface|enum)\\s+([A-Za-z_]\\w*)");
        List<Map<String, Object>> exs = new ArrayList<Map<String, Object>>();
        exs.add(Json.obj(q.get("exercise")));
        exs.add(Json.obj(Json.obj(q.get("csharp")).get("exercise")));
        for (Map<String, Object> ex : exs) {
            StringBuilder code = new StringBuilder(Json.str(ex.get("mainCode")));
            for (Object o : Json.arr(ex.get("classes"))) {
                out.add(Json.str(Json.obj(o).get("name")).trim());
                code.append('\n').append(Json.str(Json.obj(o).get("code")));
            }
            for (Object o : Json.arr(ex.get("tasks"))) out.add(Json.str(Json.obj(o).get("className")).trim());
            for (Object o : Json.arr(ex.get("givenCode"))) code.append('\n').append(Json.str(Json.obj(o).get("code")));
            java.util.regex.Matcher m = decl.matcher(code);
            while (m.find()) out.add(m.group(1));
        }
        out.remove("");
        return out;
    }

    private static final java.util.regex.Pattern RTL_LIST = java.util.regex.Pattern.compile("-?\\d+(?:\\.\\d+)?(?:\\s*,\\s*-?\\d+(?:\\.\\d+)?)+");

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
        // הנוסח מוצג מימין לשמאל (bank/README.md — "דוגמאות בנוסח"): רשימת ערכים רק בסוף שורה, ובלי נקודה אחרי מספר בסוף שורה
        StringBuilder all = new StringBuilder(q.intro);
        for (BankStore.Section sec : q.sections) all.append('\n').append(sec.text);
        for (String line : all.toString().split("\n")) {
            java.util.regex.Matcher m = RTL_LIST.matcher(line);
            while (m.find()) {
                boolean tuple = m.start() > 0 && line.charAt(m.start() - 1) == '(' && m.end() < line.length() && line.charAt(m.end()) == ')';
                if (!tuple && line.substring(m.end()).matches(".*[\u05D0-\u05EA].*"))
                    fail(where, "רשימת ערכים באמצע משפט (\"" + m.group() + "\") מתבלגנת מימין לשמאל — שמים אותה בסוף שורה, אחרי נקודתיים, או בציור: " + line);
            }
            if (line.matches(".*\\d\\.\\s*$")) fail(where, "נקודה אחרי מספר בסוף שורה מוצגת כמו \".25\" — בלי נקודה: " + line);
        }
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
