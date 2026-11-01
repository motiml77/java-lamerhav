package il.lamerhav.dsviz;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * "שליחת תרגיל" בלי Eclipse — ל-VS Code (דרך SubmitCli) ול-Visual Studio (הכלי של C#). אותם שלבים בדיוק כמו SubmitHandler.Build ב-Eclipse:
 * בונים את הקבצים מהתשובה של השרת (AiScaffold) בתיקייה משלהם, בודקים שהם מתקמפלים (ואם לא — תיקון אחד דרך השרת),
 * בודקים שההצעות לפתרון נותנות את הפלט הצפוי (ואם לא — תיקון אחד, mode=solution), ושומרים את ההצעות בצד (SolutionStore).
 * Java: הקבצים בחבילה ex01_stacks בתיקיית src, קומפילציה ב-javac של ה-JDK. C#: פרויקט Ex01_Stacks בתיקיית התרגילים (DataStructures-CS),
 * רשום בפתרון, והקומפילציה וההרצה ב-dsviz-cs.
 */
final class ExercisePipeline {
    private ExercisePipeline() {}

    /** מה קורה עכשיו (לחלון ההתקדמות) */
    interface Progress {
        void say(String text);

        boolean cancelled();
    }

    static final class Result {
        String folderName = "";
        final List<File> files = new ArrayList<File>();
        File focus;
        int line;                 // השורה (מ-0) שבה הסמן מחכה בקובץ focus
        final List<String> errors = new ArrayList<String>();
        int tasks;
        /** הסעיפים שעונים עליהם במילים (בלי קוד): {סעיף, טקסט} — לא בקוד, רק בהודעה לתלמיד (AiScaffold.othersText) */
        final List<String[]> others = new ArrayList<String[]>();
        String solutionStatus = "";   // ok / ran / failed / unchecked ("" — אין הצעות)
        final List<SolutionStore.Entry> entries = new ArrayList<SolutionStore.Entry>();
    }

    // ------------------------------------------------------------------ Java

    /** src — תיקיית המקור של הפרויקט; lib — dsviz.jar; java — ה-java של ה-JDK (להרצת הבדיקה; null = זה שרץ עכשיו) */
    static Result java(WorkerClient client, Map<String, Object> raw, List<String> sources, File src, File lib, String java, Progress p) {
        return java(client, raw, sources, src, lib, java, p, Collections.<String>emptyList());
    }

    /**
     * taken — עוד שמות שתפוסים בפרויקט (מהתוסף שקורא: חבילות בתיקיות מקור אחרות, גם מקוננות — "school.ex03_trees"),
     * כדי שהחבילה החדשה לא תתנגש בהן. מתווספים לקבצים ולתיקיות שכבר בתיקיית המקור.
     */
    static Result java(WorkerClient client, Map<String, Object> raw, List<String> sources, File src, File lib, String java, Progress p, Collection<String> taken) {
        Result r = new Result();
        Set<String> names = new HashSet<String>();
        if (taken != null) names.addAll(taken);
        AiScaffold.Exercise e = AiScaffold.read(raw);
        String pkg = AiScaffold.folderName(e.topic, names, false, src);
        r.folderName = pkg;
        File dir = new File(src, pkg);
        List<AiScaffold.GeneratedFile> generated = write(e, pkg, sources, dir, r);
        p.say("בודק שהקוד מתקמפל…");
        compileJava(r, generated, e, lib);
        if (!r.errors.isEmpty() && client != null && !p.cancelled()) {
            p.say("יש שגיאות קומפילציה — מבקש תיקון…");
            Map<String, String> code = new LinkedHashMap<String, String>();
            for (AiScaffold.GeneratedFile g : generated) code.put(g.fileName(), g.code);
            try {
                raw = client.repair(raw, code, new ArrayList<String>(r.errors));
                e = AiScaffold.read(raw);
                for (File f : r.files) f.delete();
                generated = write(e, pkg, sources, dir, r);
                p.say("בודק שוב…");
                compileJava(r, generated, e, lib);
            } catch (WorkerClient.AiError ignored) {
                // התיקון לא הצליח — נשארים עם הקבצים כמו שהם (והשגיאות מוצגות)
            }
        }
        if (r.errors.isEmpty() && !p.cancelled()) {
            final File libF = lib;
            final String javaExe = java;
            solutions(client, raw, e, pkg, sources, dir, r, p, ex -> {
                try {
                    return SolutionCheck.run(ex, pkg, libF, javaExe, Files.createTempDirectory("dsviz-solution").toFile(), 15);
                } catch (IOException x) {
                    return new SolutionCheck.Result();
                }
            });
        }
        return r;
    }

    /** כמו ב-Eclipse: בלי "missing return statement" — הפעולות של הסעיפים ריקות בכוונה (התלמיד כותב את ה-return) */
    private static void compileJava(Result r, List<AiScaffold.GeneratedFile> generated, AiScaffold.Exercise e, File lib) {
        r.errors.clear();
        try {
            File bin = Files.createTempDirectory("dsviz-build").toFile();
            List<String> all = SolutionCheck.javacErrors(r.files, bin, lib);
            delete(bin);
            if (all == null) return;   // אין קומפיילר — לא בודקים
            for (String err : all) {
                if (err.contains("missing return statement")) continue;
                r.errors.add(err);
                if (r.errors.size() >= 30) break;
            }
        } catch (IOException ignored) {
            // לא בודקים
        }
    }

    // ------------------------------------------------------------------ C#

    /**
     * root — תיקיית התרגילים (DataStructures-CS); dsvizCs — הפקודה שמריצה את dsviz-cs (למשל [".../dsviz-cs.exe"]).
     * הפרויקט נכתב לתיקייה Exercises\Ex01_... ונרשם בפתרון (CsSolution).
     */
    static Result csharp(WorkerClient client, Map<String, Object> raw, List<String> sources, Path root, List<String> dsvizCs, Progress p) throws IOException {
        return csharp(client, raw, sources, root, dsvizCs, p, Collections.<String>emptyList());
    }

    /**
     * taken — עוד שמות שתפוסים (מהכלי שקורא). מתווספים לתיקיות שתחת Exercises, לשמות הפרויקטים שבקובץ ה-.sln
     * ולמה שכבר בתיקייה עצמה — כדי שהפרויקט החדש לא יתנגש באף אחד מהם.
     */
    static Result csharp(WorkerClient client, Map<String, Object> raw, List<String> sources, Path root, List<String> dsvizCs, Progress p, Collection<String> taken) throws IOException {
        Result r = new Result();
        Set<String> names = new HashSet<String>();
        if (taken != null) names.addAll(taken);
        names.addAll(slnProjects(root));
        File ex = CsSolution.exercisesDir(root).toFile();
        AiScaffold.Exercise e = AiScaffold.read(raw);
        String name = AiScaffold.folderName(e.topic, names, true, ex, root.toFile());
        r.folderName = name;
        List<AiScaffold.GeneratedFile> generated = writeCs(e, name, sources, root, r);
        File dir = CsSolution.exercisesDir(root).resolve(name).toFile();
        p.say("בודק שהקוד מתקמפל…");
        compileCs(r, generated, e, dir, dsvizCs);
        if (!r.errors.isEmpty() && client != null && !p.cancelled()) {
            p.say("יש שגיאות קומפילציה — מבקש תיקון…");
            Map<String, String> code = new LinkedHashMap<String, String>();
            for (AiScaffold.GeneratedFile g : generated) code.put(g.fileName(), g.code);
            try {
                raw = client.repair(raw, code, new ArrayList<String>(r.errors));
                e = AiScaffold.read(raw);
                for (File f : r.files) f.delete();
                generated = writeCs(e, name, sources, root, r);
                p.say("בודק שוב…");
                compileCs(r, generated, e, dir, dsvizCs);
            } catch (WorkerClient.AiError ignored) {
                // נשארים עם הקבצים כמו שהם
            }
        }
        if (r.errors.isEmpty() && !p.cancelled()) {
            final File projectDir = dir;
            solutions(client, raw, e, name, sources, dir, r, p, x -> runCs(x, name, dsvizCs, 25));
            if (projectDir.isDirectory()) r.files.removeIf(f -> !f.getName().endsWith(".cs"));
        }
        return r;
    }

    private static final Pattern SLN_PROJECT = Pattern.compile("(?m)^\\s*Project\\(\"[^\"]*\"\\)\\s*=\\s*\"([^\"]+)\"");

    /** שמות הפרויקטים שרשומים בקובץ ה-.sln של התיקייה (בלי .sln — רשימה ריקה) */
    static List<String> slnProjects(Path root) {
        List<String> out = new ArrayList<String>();
        try {
            Path sln = CsSolution.solution(root);
            if (sln == null) return out;
            Matcher m = SLN_PROJECT.matcher(new String(Files.readAllBytes(sln), StandardCharsets.UTF_8));
            while (m.find()) out.add(m.group(1));
        } catch (IOException | RuntimeException ignored) {
            // בלי הפתרון — רק התיקיות
        }
        return out;
    }

    private static List<AiScaffold.GeneratedFile> writeCs(AiScaffold.Exercise e, String name, List<String> sources, Path root, Result r) throws IOException {
        r.tasks = e.tasks.size();
        r.others.clear();
        r.others.addAll(e.others);
        List<AiScaffold.GeneratedFile> gen = AiScaffold.build(e, name, sources);
        Map<String, String> files = new LinkedHashMap<String, String>();
        for (AiScaffold.GeneratedFile g : gen) files.put(g.fileName(), g.code);
        Path dir = CsSolution.exercisesDir(root).resolve(name);
        if (Files.isDirectory(dir)) {   // תיקון: הקבצים נכתבים מחדש (הפרויקט כבר רשום)
            for (Map.Entry<String, String> f : files.entrySet()) Files.write(dir.resolve(f.getKey()), f.getValue().getBytes(StandardCharsets.UTF_8));
        } else {
            CsSolution.write(root, name, files);
        }
        r.files.clear();
        r.focus = null;
        for (AiScaffold.GeneratedFile g : gen) {
            File f = dir.resolve(g.fileName()).toFile();
            r.files.add(f);
            if (g.cursorLine >= 0) {
                r.focus = f;
                r.line = g.cursorLine;
            }
        }
        if (r.focus == null && !r.files.isEmpty()) r.focus = r.files.get(0);
        return gen;
    }

    private static final Pattern CS_ERROR = Pattern.compile("^(.+?)\\((\\d+),(\\d+)\\): error (CS\\d+): (.*)$");

    /** dsviz-cs --check-project: "קובץ(שורה,עמודה): error CSxxxx: הודעה" — בלי CS0161 על הכותרת של פעולה של סעיף (ריקה בכוונה) */
    private static void compileCs(Result r, List<AiScaffold.GeneratedFile> generated, AiScaffold.Exercise e, File dir, List<String> dsvizCs) {
        r.errors.clear();
        List<String> cmd = new ArrayList<String>(dsvizCs);
        cmd.add("--check-project");
        cmd.add(dir.getAbsolutePath());
        String[] out = exec(cmd, dir, 120);
        if (out == null) return;
        for (String l : (out[0] + "\n" + out[1]).split("\r?\n")) {
            Matcher m = CS_ERROR.matcher(l.trim());
            if (!m.matches()) continue;
            String file = new File(m.group(1)).getName();
            int line = Integer.parseInt(m.group(2));
            if (m.group(4).equals("CS0161") && taskHeader(generated, e, file, line)) continue;
            r.errors.add(file + ":" + line + ": " + m.group(5) + "   [" + m.group(4) + "]");
            if (r.errors.size() >= 30) break;
        }
    }

    /** מריצים את התרגיל עם ההצעות (בתיקייה זמנית) ב-dsviz-cs ומשווים את מה שהודפס לפלט הצפוי — כמו SolutionCheck ב-Java */
    static SolutionCheck.Result runCs(AiScaffold.Exercise e, String name, List<String> dsvizCs, int timeoutSec) {
        SolutionCheck.Result r = new SolutionCheck.Result();
        boolean any = false;
        for (AiScaffold.Task t : e.tasks) any |= !t.solution.isEmpty();
        if (!any) return r;
        File work = null;
        try {
            work = Files.createTempDirectory("dsviz-solution").toFile();
            for (AiScaffold.GeneratedFile g : AiScaffold.build(e, name, Collections.<String>emptyList(), true))
                Files.write(new File(work, g.fileName()).toPath(), g.code.getBytes(StandardCharsets.UTF_8));
            List<String> cmd = new ArrayList<String>(dsvizCs);
            cmd.add(work.getAbsolutePath());
            cmd.add("--trace");
            cmd.add(new File(work, "run.trace").getAbsolutePath());
            long start = System.nanoTime();
            String[] out = exec(cmd, work, timeoutSec);
            if (out == null) return r;   // UNCHECKED — לא הצלחנו להריץ
            String text = out[0].replace("\r", "");
            int nl = text.indexOf('\n');
            if (text.startsWith("הרצה ויזואלית") && nl >= 0) text = text.substring(nl + 1);   // "הרצה ויזואלית: מקמפל את..."
            r.output = SolutionCheck.clean(text);
            String err = SolutionCheck.clean(out[1]);
            if (out[2] == null) {
                r.status = SolutionCheck.Status.TIMEOUT;
                r.problems.add("The program did not finish within " + timeoutSec + " seconds (an endless loop?). Output so far:");
                r.problems.add(cut(r.output, 1500));
                return r;
            }
            if (err.contains(": error CS")) {
                r.status = SolutionCheck.Status.COMPILE;
                r.problems.add("Compilation errors with your solutions:");
                r.problems.add(cut(err, 1500));
                return r;
            }
            if (!"0".equals(out[2]) || err.contains("Unhandled exception")) {
                r.status = SolutionCheck.Status.CRASH;
                r.problems.add("The program crashed (exit code " + out[2] + "). Error:");
                r.problems.add(cut(err, 1500));
                r.problems.add("Output before the crash:");
                r.problems.add(cut(r.output, 1500));
                return r;
            }
            String expected = SolutionCheck.clean(e.expectedOutput);
            if (expected.isEmpty()) {
                r.status = SolutionCheck.Status.RAN;
                return r;
            }
            if (SolutionCheck.same(expected, r.output)) {
                r.status = SolutionCheck.Status.OK;
                return r;
            }
            r.status = SolutionCheck.Status.WRONG;
            r.problems.add("The output is different from expectedOutput.");
            r.problems.add("expectedOutput:\n" + cut(expected, 2000));
            r.problems.add("Actual output (with your solutions):\n" + cut(r.output, 2000));
            return r;
        } catch (IOException ex) {
            r.status = SolutionCheck.Status.UNCHECKED;
            return r;
        } finally {
            if (work != null) delete(work);
        }
    }

    // ------------------------------------------------------------------ משותף

    interface Checker {
        SolutionCheck.Result check(AiScaffold.Exercise e);
    }

    /** כמו SubmitHandler.Build.solutions: בדיקה, תיקון אחד אם צריך, ושמירה בצד */
    private static void solutions(WorkerClient client, Map<String, Object> raw, AiScaffold.Exercise e, String pkg, List<String> sources,
            File dir, Result res, Progress p, Checker checker) {
        boolean any = false;
        for (AiScaffold.Task t : e.tasks) any |= !t.solution.isEmpty();
        if (!any) return;   // שרת ישן — בלי הצעות
        p.say("בודק שההצעות לפתרון נותנות את התוצאה הנכונה…");
        SolutionCheck.Result r = checker.check(e);
        if (!r.passed() && r.status != SolutionCheck.Status.UNCHECKED && client != null && !p.cancelled()) {
            p.say("ההצעה לפתרון לא נתנה בדיוק את הפלט הצפוי — מבקש תיקון…");
            Map<String, String> code = new LinkedHashMap<String, String>();
            for (AiScaffold.GeneratedFile g : AiScaffold.build(e, pkg, sources, true)) code.put(g.fileName(), g.code);
            List<String> before = new ArrayList<String>();
            for (AiScaffold.Task t : e.tasks) before.add(t.solution);
            String expectedBefore = e.expectedOutput;
            try {
                boolean changed = AiScaffold.takeSolutions(e, AiScaffold.read(client.repair(raw, code, r.problems, "solution")));
                p.say("בודק שוב…");
                SolutionCheck.Result r2 = checker.check(e);
                if (r2.rank() > r.rank()) {
                    r = r2;
                    if (r2.status != SolutionCheck.Status.OK) e.expectedOutput = expectedBefore;   // פלט צפוי שלא אומת — לא משנים את ההערה
                    else if (changed) {   // הפלט הצפוי תוקן ואומת — ההערה מעל main נכתבת מחדש
                        for (AiScaffold.GeneratedFile g : AiScaffold.build(e, pkg, sources)) {
                            try {
                                Files.write(new File(dir, g.fileName()).toPath(), g.code.getBytes(StandardCharsets.UTF_8));
                            } catch (IOException ignored) {
                                // נשאר הקובץ הקודם
                            }
                        }
                    }
                } else {
                    for (int i = 0; i < e.tasks.size(); i++) e.tasks.get(i).solution = before.get(i);
                    e.expectedOutput = expectedBefore;
                }
            } catch (WorkerClient.AiError ignored) {
                // התיקון לא הצליח — ההצעות נשמרות כ"לא אומתו"
            }
        }
        res.solutionStatus = r.stored();
        for (AiScaffold.Task t : e.tasks) {
            if (t.solution.isEmpty()) continue;
            SolutionStore.Entry s = new SolutionStore.Entry();
            s.path = new File(dir, t.className + (e.cs ? ".cs" : ".java")).getAbsolutePath().replace('\\', '/');
            s.cls = t.className;
            s.method = t.methodName;
            for (String[] prm : t.params) s.params.add(prm[0]);
            s.label = t.label;
            s.code = t.solution;
            s.status = res.solutionStatus;
            res.entries.add(s);
        }
    }

    private static List<AiScaffold.GeneratedFile> write(AiScaffold.Exercise e, String pkg, List<String> sources, File dir, Result r) {
        r.tasks = e.tasks.size();
        r.others.clear();
        r.others.addAll(e.others);
        dir.mkdirs();
        List<AiScaffold.GeneratedFile> gen = AiScaffold.build(e, pkg, sources);
        r.files.clear();
        r.focus = null;
        for (AiScaffold.GeneratedFile g : gen) {
            File f = new File(dir, g.fileName());
            try {
                Files.write(f.toPath(), g.code.getBytes(StandardCharsets.UTF_8));
            } catch (IOException ex) {
                r.errors.add(f.getName() + ": " + ex.getMessage());
            }
            r.files.add(f);
            if (g.cursorLine >= 0) {
                r.focus = f;
                r.line = g.cursorLine;
            }
        }
        if (r.focus == null && !r.files.isEmpty()) r.focus = r.files.get(0);
        return gen;
    }

    /** האם בשורה (מ-1) של הקובץ נמצאת הכותרת של פעולה של סעיף */
    private static boolean taskHeader(List<AiScaffold.GeneratedFile> generated, AiScaffold.Exercise e, String fileName, int line) {
        for (AiScaffold.GeneratedFile g : generated) {
            if (!g.fileName().equals(fileName)) continue;
            String[] lines = g.code.split("\n", -1);
            if (line < 1 || line > lines.length) return false;
            String l = lines[line - 1].trim();
            for (AiScaffold.Task t : e.tasks) if (t.className.equals(g.className) && l.startsWith(AiScaffold.header(t))) return true;
        }
        return false;
    }

    /** מריץ פקודה: {stdout, stderr, קוד יציאה (null = לא הסתיימה בזמן)} — או null אם לא הצליח להריץ בכלל */
    static String[] exec(List<String> cmd, File dir, int timeoutSec) {
        try {
            Process proc = new ProcessBuilder(cmd).directory(dir).start();
            proc.getOutputStream().close();
            Pump out = new Pump(proc.getInputStream()), err = new Pump(proc.getErrorStream());
            out.start();
            err.start();
            boolean done = proc.waitFor(timeoutSec, TimeUnit.SECONDS);
            if (!done) proc.destroyForcibly().waitFor(5, TimeUnit.SECONDS);
            out.join(2000);
            err.join(2000);
            return new String[] { out.text(), err.text(), done ? String.valueOf(proc.exitValue()) : null };
        } catch (IOException ex) {
            return null;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return null;
        }
    }

    private static final class Pump extends Thread {
        private final InputStream in;
        private final ByteArrayOutputStream buf = new ByteArrayOutputStream();

        Pump(InputStream in) {
            this.in = in;
            setDaemon(true);
        }

        @Override
        public void run() {
            byte[] b = new byte[4096];
            try {
                for (int n; (n = in.read(b)) > 0; ) if (buf.size() < 262144) buf.write(b, 0, n);
            } catch (IOException ignored) {
                // התהליך נסגר
            }
        }

        String text() {
            return new String(buf.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    private static String cut(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max) + "\n...";
    }

    static void delete(File f) {
        File[] kids = f.listFiles();
        if (kids != null) for (File k : kids) delete(k);
        f.delete();
    }

    /** הטקסט על בדיקת ההצעה — כמו בחלון של Eclipse (SolutionDialog) */
    static String statusText(String status) {
        switch (status == null ? "" : status) {
            case "ok":
                return "✓  נבדק: הרצנו את main עם ההצעה הזו — והודפס בדיוק מה שצריך להיות מודפס.";
            case "ran":
                return "✓  ההצעה התקמפלה ורצה בלי שגיאות (בתרגיל לא היה פלט צפוי להשוואה).";
            case "failed":
                return "⚠  שימו לב: לא הצלחנו לוודא שההצעה הזו נותנת בדיוק את הפלט הצפוי — בדקו אותה בעצמכם.";
            default:
                return "ההצעה לא נבדקה במחשב — כדאי להריץ ולבדוק.";
        }
    }
}
