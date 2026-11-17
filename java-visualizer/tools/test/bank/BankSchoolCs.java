package il.lamerhav.dsviz;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * מאגר התרגילים (C#) מול Unit4.dll של בתי הספר — בלי הספרייה של DSViz: תלמיד שמעתיק תרגיל מהמאגר (המעטפת או ההצעה לפתרון)
 * לפרויקט של בית הספר (שמפנה ל-Unit4.dll) — הקוד צריך להיבנות ולהדפיס את הפלט הרצוי גם שם, בהרצה הרגילה. כך נתפס קוד שמשתמש
 * במשהו שיש רק אצלנו (Helpers.Size / Duplicate, Student, Car, GetInfo...), או הדפסה ששונה בספרייה שלהם (Node.ToString).
 *
 * לכל שאלה: הקבצים של התרגיל עם ההצעות לפתרון (AiScaffold — כמו בכלי של Visual Studio), מקומפלים ב-csc מול ה-reference assemblies של
 * ‎.NET 8 + Unit4.dll של בית הספר, ורצים ב-dotnet עם Unit4.dll לידם. וגם המעטפת של התלמיד (עם return זמני) — רק קומפילציה.
 *   java -cp <classes> il.lamerhav.dsviz.BankSchoolCs <תיקיית המאגר> <Unit4.dll> <csc.dll> <תיקיית ref של net8> <תיקיית עבודה>
 */
public final class BankSchoolCs {
    private static int fails;

    public static void main(String[] a) throws Exception {
        File bank = new File(a[0]), school = new File(a[1]), csc = new File(a[2]), refDir = new File(a[3]), work = new File(a[4]);
        List<String> refs = new ArrayList<String>();
        File[] rs = refDir.listFiles((d, n) -> n.endsWith(".dll"));
        if (rs != null) for (File r : rs) refs.add("-r:" + r.getAbsolutePath());
        refs.add("-r:" + school.getAbsolutePath());
        BankStore s = new BankStore();
        s.csharp = true;
        s.loadDir(bank);
        int n = 0;
        for (BankStore.Question q : s.questions) {
            n++;
            check(q, work, csc, refs, school);
        }
        System.out.println(fails == 0 ? "מאגר התרגילים (C#) מול " + school.getParentFile().getName() + "/Unit4.dll: " + n + " שאלות — נבנות ומדפיסות את הפלט הרצוי (בלי הספרייה של DSViz)"
                : "מאגר התרגילים (C#) מול Unit4.dll של בית הספר: " + fails + " בעיות");
        System.exit(fails == 0 ? 0 : 1);
    }

    private static void check(BankStore.Question q, File work, File csc, List<String> refs, File school) throws Exception {
        String where = q.origin + " [C# מול Unit4.dll]";
        AiScaffold.Exercise e = AiScaffold.read(q.exercise);
        File dir = new File(work, q.id);
        // הפתרון: נבנה ורץ
        File sol = new File(dir, "solution");
        write(AiScaffold.build(e, "Ex01_Check", Collections.<String>emptyList(), true), sol);
        String err = compile(csc, refs, sol);
        if (err != null) { fail(where, "ההצעות לפתרון לא נבנות מול Unit4.dll של בית הספר:\n" + err); return; }
        Files.copy(school.toPath(), new File(sol, "Unit4.dll").toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        Files.write(new File(sol, "App.runtimeconfig.json").toPath(),
                "{\"runtimeOptions\":{\"tfm\":\"net8.0\",\"framework\":{\"name\":\"Microsoft.NETCore.App\",\"version\":\"8.0.0\"}}}".getBytes(StandardCharsets.UTF_8));
        List<String> run = new ArrayList<String>();
        run.add("dotnet");
        run.add(new File(sol, "App.dll").getAbsolutePath());
        String[] out = ExercisePipeline.exec(run, sol, 60);
        if (out == null || out[2] == null) { fail(where, "ההרצה לא הסתיימה"); return; }
        if (!"0".equals(out[2])) { fail(where, "ההרצה נכשלה (קוד " + out[2] + "): " + out[1].trim()); return; }
        String got = SolutionCheck.clean(out[0]), want = SolutionCheck.clean(e.expectedOutput);
        if (!got.equals(want)) { fail(where, "הפלט מול Unit4.dll של בית הספר שונה מהפלט הרצוי:\n--- רצוי\n" + want + "\n--- התקבל\n" + got); return; }
        // המעטפת של התלמיד (כמו שהכלי יוצר אותה, עם return זמני): נבנית
        AiScaffold.testReturns = true;
        try {
            File stub = new File(dir, "stub");
            write(AiScaffold.build(e, "Ex01_Check", Collections.<String>emptyList()), stub);
            String se = compile(csc, refs, stub);
            if (se != null) { fail(where, "המעטפת לא נבנית מול Unit4.dll של בית הספר:\n" + se); return; }
        } finally {
            AiScaffold.testReturns = false;
        }
        System.out.println("  ok   " + q.id + "  [C# מול Unit4.dll]  " + q.title);
    }

    /** csc על כל קובצי ה-.cs בתיקייה → App.dll. מחזיר null אם הצליח, אחרת את השגיאות */
    private static String compile(File csc, List<String> refs, File dir) throws Exception {
        List<String> cmd = new ArrayList<String>();
        cmd.add("dotnet");
        cmd.add(csc.getAbsolutePath());
        cmd.add("-nologo");
        cmd.add("-noconfig");
        cmd.add("-nostdlib");
        cmd.add("-target:exe");
        cmd.add("-langversion:latest");
        cmd.add("-nullable:disable");
        cmd.add("-out:" + new File(dir, "App.dll").getAbsolutePath());
        cmd.addAll(refs);
        File[] cs = dir.listFiles((d, n) -> n.endsWith(".cs"));
        if (cs != null) for (File f : cs) cmd.add(f.getAbsolutePath());
        String[] out = ExercisePipeline.exec(cmd, dir, 120);
        if (out == null || out[2] == null) return "csc לא רץ";
        if ("0".equals(out[2])) return null;
        StringBuilder b = new StringBuilder();
        for (String l : (out[0] + "\n" + out[1]).split("\n")) if (l.contains("error")) b.append("  ").append(l.trim().replace(dir.getAbsolutePath() + File.separator, "")).append('\n');
        return b.toString();
    }

    private static void write(List<AiScaffold.GeneratedFile> gen, File dir) throws Exception {
        dir.mkdirs();
        for (AiScaffold.GeneratedFile g : gen) {
            File f = new File(dir, g.fileName());
            f.getParentFile().mkdirs();
            Files.write(f.toPath(), g.code.getBytes(StandardCharsets.UTF_8));
        }
    }

    private static void fail(String where, String why) {
        fails++;
        System.out.println("  FAIL " + where + ": " + why);
    }
}
