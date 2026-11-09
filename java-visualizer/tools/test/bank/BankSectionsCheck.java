package il.lamerhav.dsviz;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * "הכן סעיף ב" (BankSections) על כל שאלה במאגר, ב-Java וב-C#:
 *   1. התרגיל נוצר עם סעיף א בלבד: המעטפת מתקמפלת, ועם ההצעה לפתרון main מדפיס בדיוק את הפלט הרצוי של סעיף א;
 *   2. מוסיפים את סעיף ב לקוד (עם ההצעה לפתרון): main מדפיס בדיוק את כל הפלט הרצוי של השאלה;
 *   3. מוסיפים את סעיף ב למעטפת (פעולה ריקה): מתקמפל — רק "חסר return" בפעולה הריקה; הסמן בשורה שמתחת ל"כאן עונים סעיף ב'";
 *   4. אותו דבר כשהתלמיד כבר כתב את סעיף א, כשהקובץ עם CRLF ועם טאבים (כמו אחרי Ctrl+Shift+F ב-Eclipse), וכשיש שורות ריקות נוספות;
 *   5. לחיצה שנייה לא מוסיפה שוב; prepared סופר נכון (1 → 2).
 *   java -cp ... il.lamerhav.dsviz.BankSectionsCheck <bank> <dsviz.jar> [dotnet dsviz-cs.dll]
 */
public final class BankSectionsCheck {
    static int pass, fail;
    static File lib;
    static List<String> cs;

    public static void main(String[] a) throws Exception {
        System.setProperty("dsviz.bank", a[0]);
        lib = new File(a[1]).getAbsoluteFile();
        cs = a.length > 3 ? Arrays.asList(a[2], a[3]) : null;
        for (boolean csharp : cs == null ? new boolean[] { false } : new boolean[] { false, true }) {
            BankStore store = BankStore.load(csharp);
            for (BankStore.Question q : store.questions) check(q, csharp);
        }
        System.out.println("BankSections: " + pass + " עברו, " + fail + " נכשלו");
        System.exit(fail == 0 ? 0 : 1);
    }

    static void check(BankStore.Question q, boolean csharp) throws Exception {
        String where = q.id + (csharp ? " [C#]" : "");
        Map<String, Object> exJson = q.exercise;
        AiScaffold.Exercise full = AiScaffold.read(Json.obj(Json.parse(Json.write(exJson))));
        List<String> labels = BankSections.labels(exJson);
        if (full.tasks.size() < 2) { ok(where + ": סעיף אחד בלבד"); return; }
        // החלוקה של main לא מאבדת ולא משנה סדר
        StringBuilder joined = new StringBuilder();
        for (List<String> part : BankSections.splitMain(full.mainCode, labels).values()) for (String l : part) joined.append(l).append('\n');
        if (!joined.toString().replaceAll("\\s+", "").equals(full.mainCode.replaceAll("\\s+", ""))) { bad(where, "החלוקה של main לפי סעיפים לא שלמה"); return; }
        if (!BankSections.expectedUpTo(full.expectedOutput, labels, labels.size()).equals(full.expectedOutput)) { bad(where, "הפלט הרצוי לפי סעיפים לא שלם"); return; }

        AiScaffold.Exercise first = AiScaffold.read(BankSections.firstSections(exJson, 1));
        String pkg = csharp ? "Ex01_Check" : "ex01_check";
        AiScaffold.Task t2 = full.tasks.get(1);
        File work = Files.createTempDirectory("bank-sections").toFile();
        try {
            // 1. סעיף א בלבד, עם ההצעה לפתרון
            Map<String, String> solved = texts(AiScaffold.build(first, pkg, Collections.<String>emptyList(), true));
            String out1 = run(solved, new File(work, "a"), pkg, first.mainClass, csharp);
            String want1 = BankSections.expectedUpTo(full.expectedOutput, labels, 1);
            if (!same(out1, want1)) { bad(where, "סעיף א בלבד: הפלט\n" + out1 + "\nבמקום\n" + want1); return; }
            // 2. מוסיפים את סעיף ב עם ההצעה לפתרון
            String out2 = run(addAll(solved, full, t2, true, where), new File(work, "b"), pkg, first.mainClass, csharp);
            if (out2 == null) return;
            if (!same(out2, full.expectedOutput)) { bad(where, "אחרי סעיף ב: הפלט\n" + out2 + "\nבמקום\n" + full.expectedOutput); return; }
            // 3. המעטפת (פעולות ריקות): סעיף א, ואז סעיף ב — רק "חסר return"
            Map<String, String> stub = new LinkedHashMap<String, String>();
            for (Map.Entry<String, String> f : texts(AiScaffold.build(first, pkg, Collections.<String>emptyList(), false)).entrySet())
                stub.put(f.getKey(), BankSections.markSections(f.getValue(), first.tasks.get(0), csharp));
            if (BankSections.prepared(full, stub) != 1) { bad(where, "prepared לפני ההוספה: " + BankSections.prepared(full, stub)); return; }
            String mark1 = BankSections.noMarks(AiScaffold.markFor(first.tasks.get(0), true));
            boolean hasMark1 = false;
            for (String s : stub.values()) hasMark1 |= BankSections.noMarks(s).contains(mark1);
            if (!hasMark1) { bad(where, "במעטפת אין \"כאן עונים סעיף א'\""); return; }
            Map<String, String> stub2 = addAll(stub, full, t2, false, where);
            if (stub2 == null) return;
            if (BankSections.prepared(full, stub2) != 2) { bad(where, "prepared אחרי ההוספה: " + BankSections.prepared(full, stub2)); return; }
            String err = compileOnlyMissingReturn(stub2, new File(work, "c"), pkg, csharp);
            if (err != null) { bad(where, "המעטפת אחרי סעיף ב: " + err); return; }
            // הסמן: מתחת לשורת "כאן עונים סעיף ב'"
            BankSections.Edit ed = BankSections.add(stub.get(t2.className), full, t2, t2.className, false);
            String[] ls = ed.text.split("\n", -1);
            String mark2 = BankSections.noMarks(AiScaffold.markFor(t2, true));
            if (ed.cursorLine < 2 || !BankSections.noMarks(ls[ed.cursorLine - 2]).trim().equals("// " + mark2)) { bad(where, "הסמן לא מתחת ל-\"כאן עונים סעיף ב'\" (שורה " + ed.cursorLine + ")"); return; }
            // 5. לחיצה שנייה — בלי שינוי
            for (Map.Entry<String, String> f : stub2.entrySet()) {
                BankSections.Edit again = BankSections.add(f.getValue(), full, t2, f.getKey(), false);
                if (again.changed()) { bad(where, "לחיצה שנייה הוסיפה שוב ל-" + f.getKey()); return; }
            }
            // 4. התלמיד כבר כתב את סעיף א + CRLF + טאבים + שורות ריקות — ואז סעיף ב עם ההצעה: כל הפלט נכון
            Map<String, String> messy = new LinkedHashMap<String, String>();
            for (Map.Entry<String, String> f : solved.entrySet())
                messy.put(f.getKey(), f.getValue().replace("    ", "\t").replace("\n\t}", "\n\n\n\t}").replace("\n", "\r\n"));
            String out4 = run(addAll(messy, full, t2, true, where), new File(work, "d"), pkg, first.mainClass, csharp);
            if (out4 == null) return;
            if (!same(out4, full.expectedOutput)) { bad(where, "CRLF + טאבים: הפלט\n" + out4); return; }
            ok(where);
        } finally {
            delete(work);
        }
    }

    static Map<String, String> addAll(Map<String, String> files, AiScaffold.Exercise full, AiScaffold.Task t, boolean withSolution, String where) {
        Map<String, String> out = new LinkedHashMap<String, String>();
        boolean method = false, main = false;
        for (Map.Entry<String, String> f : files.entrySet()) {
            BankSections.Edit e = BankSections.add(f.getValue(), full, t, f.getKey(), withSolution);
            if (e.problem != null && !e.problem.startsWith("המחלקה")) { bad(where, e.problem); return null; }
            method |= e.method;
            main |= e.main;
            out.put(f.getKey(), e.text);
        }
        if (!method) { bad(where, "הפעולה של סעיף " + t.label + " לא נוספה"); return null; }
        if (!main) { bad(where, "הבדיקות של סעיף " + t.label + " לא נוספו ל-main"); return null; }
        return out;
    }

    static Map<String, String> texts(List<AiScaffold.GeneratedFile> gen) {
        Map<String, String> m = new LinkedHashMap<String, String>();
        for (AiScaffold.GeneratedFile g : gen) m.put(g.className, g.code);
        return m;
    }

    static List<File> write(Map<String, String> files, File dir, String pkg, boolean csharp) throws Exception {
        File d = csharp ? dir : new File(dir, pkg);
        d.mkdirs();
        List<File> out = new ArrayList<File>();
        for (Map.Entry<String, String> f : files.entrySet()) {
            File file = new File(d, f.getKey() + (csharp ? ".cs" : ".java"));
            Files.write(file.toPath(), f.getValue().getBytes(StandardCharsets.UTF_8));
            out.add(file);
        }
        return out;
    }

    /** מריץ ומחזיר את הפלט (או null אחרי כשל, שכבר נרשם) */
    static String run(Map<String, String> files, File dir, String pkg, String mainClass, boolean csharp) throws Exception {
        if (files == null) return null;
        List<File> src = write(files, dir, pkg, csharp);
        if (csharp) {
            List<String> cmd = new ArrayList<String>(cs);
            cmd.add(dir.getAbsolutePath());
            cmd.add("--trace");
            cmd.add(new File(dir, "run.trace").getAbsolutePath());
            String[] out = ExercisePipeline.exec(cmd, dir, 90);
            if (out == null || !"0".equals(out[2])) return "שגיאה: " + (out == null ? "לא רץ" : out[1]);
            String text = out[0].replace("\r", "");
            int nl = text.indexOf('\n');
            if (text.startsWith("הרצה ויזואלית") && nl >= 0) text = text.substring(nl + 1);
            return SolutionCheck.clean(text);
        }
        File bin = new File(dir, "bin");
        bin.mkdirs();
        List<String> errors = SolutionCheck.javacErrors(src, bin, lib);
        if (!errors.isEmpty()) return "שגיאות קומפילציה: " + errors;
        String java = new File(System.getProperty("java.home"), "bin/java").getPath();
        String[] out = ExercisePipeline.exec(Arrays.asList(java, "-Dstdout.encoding=UTF-8", "-Dfile.encoding=UTF-8", "-cp",
                bin.getPath() + File.pathSeparator + lib.getPath(), pkg + "." + mainClass), dir, 60);
        if (out == null) return "לא רץ";
        if (!"0".equals(out[2])) return "קוד יציאה " + out[2] + ": " + out[1].replaceAll("Picked up[^\n]*\n?", "");
        return SolutionCheck.clean(out[0].replace("\r", ""));
    }

    static String compileOnlyMissingReturn(Map<String, String> files, File dir, String pkg, boolean csharp) throws Exception {
        List<File> src = write(files, dir, pkg, csharp);
        if (csharp) {
            List<String> cmd = new ArrayList<String>(cs);
            cmd.add("--check-project");
            cmd.add(dir.getAbsolutePath());
            String[] out = ExercisePipeline.exec(cmd, dir, 90);
            if (out == null) return "dsviz-cs לא רץ";
            List<String> other = new ArrayList<String>();
            for (String l : (out[0] + "\n" + out[1]).split("\n")) if (l.contains("error CS") && !l.contains("CS0161")) other.add(l.trim());
            return other.isEmpty() ? null : String.join(" | ", other);
        }
        File bin = new File(dir, "bin");
        bin.mkdirs();
        List<String> other = new ArrayList<String>();
        for (String e : SolutionCheck.javacErrors(src, bin, lib)) if (!e.contains("missing return")) other.add(e);
        return other.isEmpty() ? null : String.join(" | ", other);
    }

    static boolean same(String a, String b) {
        return a != null && a.replace("\r", "").trim().equals(b.replace("\r", "").trim());
    }

    static void ok(String what) {
        pass++;
        System.out.println("  ok   " + what);
    }

    static void bad(String where, String why) {
        fail++;
        System.out.println("  FAIL " + where + ": " + why);
    }

    static void delete(File f) {
        File[] k = f.listFiles();
        if (k != null) for (File c : k) delete(c);
        f.delete();
    }
}
