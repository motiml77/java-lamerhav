package il.lamerhav.dsviz;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * גרסת C# של מאגר התרגילים (Visual Studio) — כל שאלה נבדקת ב-dsviz-cs, כמו שהכלי של Visual Studio בונה אותה:
 *   1. יש גרסת C# (השדה "csharp") עם נוסח לכל סעיף והצעה לפתרון; בנוסח אין שמות פעולות של Java (sumStack במקום SumStack)
 *   2. המעטפת של התלמיד מתקמפלת (עם return זמני)
 *   3. עם ההצעות לפתרון — רצה ב-dsviz-cs (ההרצה הוויזואלית) ומדפיסה בדיוק את הפלט הרצוי
 *   java -cp <classes> il.lamerhav.dsviz.BankCheckCs <תיקיית המאגר> <dsviz-cs: "dotnet" "…/dsviz-cs.dll">
 */
public final class BankCheckCs {
    private static int fails;

    public static void main(String[] a) throws Exception {
        File dir = new File(a[0]);
        List<String> cs = new ArrayList<String>(Arrays.asList(a).subList(1, a.length));
        BankStore s = new BankStore();
        s.csharp = true;
        s.loadDir(dir);
        for (String[] p : s.problems) fail(p[0], p[1]);
        int n = 0;
        for (BankStore.Question q : s.questions) {
            n++;
            check(q, cs);
        }
        System.out.println(fails == 0 ? "מאגר התרגילים (C#): " + n + " שאלות — כולן תקינות" : "מאגר התרגילים (C#): " + fails + " בעיות");
        System.exit(fails == 0 ? 0 : 1);
    }

    private static void check(BankStore.Question q, List<String> cs) throws Exception {
        String where = q.origin + " [C#]";
        AiScaffold.Exercise e = AiScaffold.read(q.exercise);
        if (!e.cs) { fail(where, "language צריך להיות csharp"); return; }
        int jsonTasks = Json.arr(q.exercise.get("tasks")).size();
        if (e.tasks.size() != jsonTasks) fail(where, "רק " + e.tasks.size() + " מתוך " + jsonTasks + " סעיפי הקוד נקראו");
        StringBuilder text = new StringBuilder(q.intro);
        for (BankStore.Section sec : q.sections) text.append('\n').append(sec.text);
        for (AiScaffold.Task t : e.tasks) {
            if (t.solution.isEmpty()) fail(where, "לסעיף " + t.label + " אין הצעה לפתרון");
            if (!Character.isUpperCase(t.methodName.charAt(0))) fail(where, "שם פעולה ב-C# מתחיל באות גדולה: " + t.methodName);
            String javaName = Character.toLowerCase(t.methodName.charAt(0)) + t.methodName.substring(1);
            if (text.toString().matches("(?s).*(?<![\\w.])" + javaName + "\\b.*")) fail(where, "בנוסח נשאר השם של Java: " + javaName);
        }
        String name = "Ex01_Check";
        File work = Files.createTempDirectory("bank-check-cs").toFile();
        try {
            AiScaffold.testReturns = true;
            File stub = new File(work, "stub");
            write(AiScaffold.build(e, name, Collections.<String>emptyList()), stub);
            AiScaffold.testReturns = false;
            List<String> cmd = new ArrayList<String>(cs);
            cmd.add("--check-project");
            cmd.add(stub.getAbsolutePath());
            String[] out = ExercisePipeline.exec(cmd, stub, 120);
            if (out == null || !"0".equals(out[2])) fail(where, "המעטפת לא מתקמפלת: " + (out == null ? "dsviz-cs לא רץ" : out[0].trim()));
            SolutionCheck.Result r = ExercisePipeline.runCs(e, name, cs, 60);
            if (r.status != SolutionCheck.Status.OK) fail(where, "ההצעות לפתרון: " + r.status + " " + r.problems);
            else System.out.println("  ok   " + q.id + "  [C#]  " + q.title);
        } finally {
            AiScaffold.testReturns = false;
            delete(work);
        }
    }

    private static void write(List<AiScaffold.GeneratedFile> gen, File dir) throws Exception {
        dir.mkdirs();
        for (AiScaffold.GeneratedFile g : gen) Files.write(new File(dir, g.fileName()).toPath(), g.code.getBytes(StandardCharsets.UTF_8));
    }

    private static void fail(String where, String why) {
        fails++;
        System.out.println("  FAIL " + where + ": " + why);
    }

    private static void delete(File f) {
        File[] k = f.listFiles();
        if (k != null) for (File c : k) delete(c);
        f.delete();
    }
}
