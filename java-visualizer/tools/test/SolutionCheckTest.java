package il.lamerhav.dsviz;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * "הצעה לפתרון": ההצעות שבתשובות לדוגמה (tools/test/ai/*.json) — מותאמות לכותרת של הסעיף, לא נכנסות לקוד של התלמיד,
 * ובאמת נותנות את הפלט הצפוי (קומפילציה + הרצה של main עם ההצעות). וגם: הצעה שגויה / שלא מתקמפלת / שלא נגמרת — מזוהה,
 * ומה שנשלח לתיקון (mode=solution) מתאר מה קרה. ובסוף — שמירה וחיפוש של ההצעות (SolutionStore).
 *   java SolutionCheckTest <json-dir> <dsviz.jar> <work-dir>
 */
public class SolutionCheckTest {
    public static void main(String[] a) throws Exception {
        File dir = new File(a[0]), lib = new File(a[1]);
        Path work = new File(a[2]).toPath();
        File[] cases = dir.listFiles((x, n) -> n.endsWith(".json"));
        Arrays.sort(cases);
        int n = 0;
        AiScaffold.Exercise stack = null;
        for (File f : cases) {
            AiScaffold.Exercise e = read(f);
            boolean any = false;
            for (AiScaffold.Task t : e.tasks) any |= !t.solution.isEmpty();
            if (!any) continue;
            if (f.getName().startsWith("1-")) stack = e;
            String pkg = "sol" + (++n);
            // ההצעה לא נכנסת לקבצים של התלמיד
            for (AiScaffold.GeneratedFile g : AiScaffold.build(e, pkg, Collections.<String>emptyList()))
                for (AiScaffold.Task t : e.tasks)
                    if (!t.solution.isEmpty()) {
                        String body = t.solution.split("\n")[2].trim();
                        check(!g.code.contains(body), f + ": ההצעה לפתרון נכנסה לקוד של התלמיד: " + body);
                    }
            for (AiScaffold.Task t : e.tasks) {
                if (t.solution.isEmpty()) continue;
                // הכותרת של ההצעה — בדיוק הכותרת של הסעיף
                check(t.solution.startsWith(AiScaffold.header(t) + " {"), f + ": הכותרת של ההצעה לא כמו בסעיף: " + t.solution.split("\n")[0]);
                check(!t.solution.contains("```"), f + ": נשארה גדר קוד");
            }
            SolutionCheck.Result r = run(e, pkg, lib, work);
            check(r.status == (e.expectedOutput.isEmpty() ? SolutionCheck.Status.RAN : SolutionCheck.Status.OK),
                    f + ": ההצעות לא נתנו את הפלט הצפוי: " + r.status + " " + r.problems + "\nהודפס:\n" + r.output);
            System.out.println("✓ " + f.getName() + ": ההצעות לפתרון נבדקו — " + r.status + " (" + e.tasks.size() + " סעיפים)");
        }
        check(stack != null && n >= 3, "צריך לפחות 3 תרגילים עם הצעות");

        // removeAll נכתב בגדר קוד, עם { בשורה נפרדת ושמות פרמטרים אחרים (stack, num) — הותאם לכותרת של הסעיף, והשמות הוחלפו בגוף
        AiScaffold.Task ra = stack.tasks.get(1);
        check(ra.solution.contains("while (!s.isEmpty())") && ra.solution.contains("if (y != x)") && !ra.solution.contains("stack"),
                "removeAll: השמות לא הוחלפו:\n" + ra.solution);
        check(ra.solution.contains("// ‫"), "הערה בעברית — עם סימון כיוון (כמו בשאר הקוד)");
        System.out.println("✓ הצעה בגדר קוד ובשמות אחרים — הותאמה לכותרת של הסעיף");

        // הצעה שגויה: הפלט שונה מהצפוי → WRONG, ולתיקון נשלחים הפלט הצפוי והפלט שהתקבל
        AiScaffold.Task ce = stack.tasks.get(0);
        String good = ce.solution;
        ce.solution = good.replace("return count;", "return count + 1;");
        SolutionCheck.Result r = run(stack, "wrong", lib, work);
        String p = String.join("\n", r.problems);
        check(r.status == SolutionCheck.Status.WRONG && p.contains("expectedOutput:\nמחסנית: [4,7,2,9]\nסעיף א: 2") && p.contains("סעיף א: 3"),
                "הצעה שגויה: " + r.status + " " + p);
        System.out.println("✓ הצעה שגויה — מזוהה (הפלט מול הפלט הצפוי)");

        // הצעה שלא מתקמפלת
        ce.solution = good.replace("int count = 0;", "int count = \"0\";");
        r = run(stack, "broken", lib, work);
        check(r.status == SolutionCheck.Status.COMPILE && String.join("\n", r.problems).contains("StackTargil.java:"), "לא מתקמפלת: " + r.status + " " + r.problems);
        System.out.println("✓ הצעה שלא מתקמפלת — מזוהה: " + r.problems.get(1));

        // הצעה שלא נגמרת (לולאה אינסופית) — נעצרת אחרי הזמן שנקבע
        ce.solution = good.replace("int x = s.pop();", "int x = s.top();");
        long t0 = System.currentTimeMillis();
        r = SolutionCheck.run(stack, "loop", lib, null, Files.createTempDirectory(work, "c").toFile(), 3);
        check(r.status == SolutionCheck.Status.TIMEOUT && System.currentTimeMillis() - t0 < 15000, "לולאה אינסופית: " + r.status);
        System.out.println("✓ הצעה עם לולאה אינסופית — נעצרת (" + (System.currentTimeMillis() - t0) / 1000 + " שניות)");

        // קריסה בזמן ריצה
        ce.solution = good.replace("int count = 0;", "int count = 0;\n    Stack<Integer> e = null;\n    e.push(1);");
        r = run(stack, "crash", lib, work);
        check(r.status == SolutionCheck.Status.CRASH && String.join("\n", r.problems).contains("NullPointerException"), "קריסה: " + r.status + " " + r.problems);
        System.out.println("✓ הצעה שקורסת — מזוהה");
        ce.solution = good;

        // אחרי סבב תיקון: לוקחים רק את ההצעות ואת הפלט הצפוי — המעטפת לא משתנה
        AiScaffold.Exercise fixed = read(new File(dir, "1-stack.json"));
        fixed.tasks.get(0).solution = good.replace("מעבירים את האיברים", "(מתוקן) מעבירים את האיברים");
        fixed.expectedOutput = stack.expectedOutput + "\n";
        fixed.mainCode = "System.out.println(\"שונה\");";
        String mainBefore = stack.mainCode;
        check(!AiScaffold.takeSolutions(stack, fixed), "הפלט הצפוי לא השתנה (רק רווח בסוף)");
        check(stack.tasks.get(0).solution.contains("(מתוקן)") && stack.mainCode.equals(mainBefore), "takeSolutions");
        fixed.expectedOutput = "אחר";
        check(AiScaffold.takeSolutions(stack, fixed) && stack.expectedOutput.equals("אחר"), "takeSolutions: פלט צפוי חדש");
        System.out.println("✓ סבב תיקון: רק ההצעות והפלט הצפוי מתעדכנים");

        // התאמה לכותרת: שם בעברית שתוקן, C#, ותשובה בלי קוד
        AiScaffold.Task t = new AiScaffold.Task();
        t.methodName = "task1";
        t.returnType = "int";
        t.params.add(new String[] { "Stack<Integer>", "s" });
        String fit = AiScaffold.fitSolution(t, "static int מחסנית(Stack<Integer> s)\n{\n    if (s.isEmpty()) return 0;\n    int x = s.pop();\n    int r = מחסנית(s) + 1;\n    s.push(x);\n    return r;\n}", "מחסנית");
        check(fit.startsWith("public static int task1(Stack<Integer> s) {") && fit.contains("int r = task1(s) + 1;"), "שם בעברית:\n" + fit);
        check(AiScaffold.fitSolution(t, "אין כאן קוד", "x").isEmpty() && AiScaffold.fitSolution(t, "", "x").isEmpty(), "תשובה בלי קוד");
        t.cs = true;
        t.methodName = "Count";
        fit = AiScaffold.fitSolution(t, "public static int Count(Stack<int> st) {\n    return st.IsEmpty() ? 0 : 1; // \"st\" במחרוזת\n}", "Count");
        check(fit.startsWith("public static int Count(Stack<Integer> s)\n{") && fit.contains("s.IsEmpty()") && fit.contains("\"st\""), "C#:\n" + fit);
        System.out.println("✓ התאמה לכותרת: שם בעברית, סגנון C#, תשובה בלי קוד");

        // שמירה וחיפוש (SolutionStore): לפי הקובץ והפעולה; פרויקט ששונה שם — לפי שאר הנתיב
        File store = new File(work.toFile(), "solutions.json");
        store.delete();
        SolutionStore s1 = new SolutionStore(store);
        List<SolutionStore.Entry> add = new ArrayList<SolutionStore.Entry>();
        for (AiScaffold.Task x : read(new File(dir, "1-stack.json")).tasks) {
            SolutionStore.Entry en = new SolutionStore.Entry();
            en.path = "/Students/src/ex01_stacks/StackTargil.java";
            en.cls = x.className;
            en.method = x.methodName;
            for (String[] pp : x.params) en.params.add(pp[0]);
            en.label = x.label;
            en.code = x.solution;
            en.status = "ok";
            add.add(en);
        }
        s1.add(add);
        s1.add(Collections.singletonList(add.get(0)));   // שוב — מחליף, לא מכפיל
        SolutionStore s2 = new SolutionStore(store);      // קורא מהקובץ
        check(s2.forFile("/Students/src/ex01_stacks/StackTargil.java").size() == 2, "forFile");
        SolutionStore.Entry got = s2.find("/Students/src/ex01_stacks/StackTargil.java", "StackTargil", "removeAll", 2);
        check(got != null && got.code.equals(add.get(1).code) && got.label.equals("ב") && got.status.equals("ok") && got.params.size() == 2, "find");
        check(s2.find("/Students/src/ex01_stacks/StackTargil.java", "StackTargil", "removeAll", 1) == null, "find: מספר פרמטרים אחר");
        check(s2.find("/Other/src/ex01_stacks/StackTargil.java", "StackTargil", "countEven", 1) != null, "find: פרויקט בשם אחר");
        check(s2.find("/Students/src/ex02_queues/StackTargil.java", "StackTargil", "countEven", 1) == null, "find: תרגיל אחר");
        check(s2.forFile("/Students/src/ex01_stacks/Other.java").isEmpty(), "forFile: קובץ אחר");
        System.out.println("✓ שמירה וחיפוש של ההצעות");
    }

    static AiScaffold.Exercise read(File f) throws Exception {
        return AiScaffold.read(Json.obj(Json.parse(new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8))));
    }

    static SolutionCheck.Result run(AiScaffold.Exercise e, String pkg, File lib, Path work) throws Exception {
        return SolutionCheck.run(e, pkg, lib, null, Files.createTempDirectory(work, "c").toFile(), 20);
    }

    static void check(boolean ok, String what) {
        if (!ok) throw new AssertionError(what);
    }
}
