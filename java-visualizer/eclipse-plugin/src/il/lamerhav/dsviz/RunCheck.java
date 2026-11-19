package il.lamerhav.dsviz;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * בדיקת ההרצה של "הגשה למורה": מקמפלים את קובצי התרגיל של התלמיד כמו שהם (בתיקייה זמנית — לא נוגעים בפרויקט שלו), מריצים את
 * main עד TIMEOUT_SEC שניות ושומרים את הפלט (עד MAX_OUTPUT תווים); בתרגיל ממאגר השאלות משווים אותו לפלט הרצוי של השאלה
 * (רווחים בסוף שורה ושורות ריקות בסוף לא נחשבים הבדל). הבדיקה רצה במחשב של התלמיד — השרת לא מריץ קוד של אף אחד.
 * Java: ecj של Eclipse, ואם אין — javac (של ה-Java שמריץ את הכלי, או ליד ה-java שנמסר). C#: dsviz-cs, כמו בבדיקת "הצעה לפתרון".
 * תוכנית שקוראת קלט מהמקלדת לא מורצת (אין מי שיקליד) — ההגשה מסומנת כך.
 */
final class RunCheck {
    private RunCheck() {}

    static final int TIMEOUT_SEC = 10, MAX_OUTPUT = 2000, MAX_ERROR = 900;
    /** dsviz-cs מקמפל בעצמו לפני ההרצה — הזמן הזה לא נספר בתוך 10 השניות של התוכנית */
    static final int CS_COMPILE_SEC = 20;

    /**
     * ההסבר בעברית להודעה של הקומפיילר: (הודעה, קוד השגיאה ב-C# או "") → הסבר, או "" כשאין. הכלי של Visual Studio / VS Code /
     * IntelliJ מחבר לכאן את RtlDialog.explain; בלי חיבור — ההודעה של הקומפיילר כמו שהיא.
     */
    static java.util.function.BiFunction<String, String, String> explainer = (message, code) -> "";

    /** "Targil.java:12: missing return statement" → "Targil.java, שורה 12: חסרה הוראת return... (missing return statement)" */
    static String hebrewLine(String e) {
        Matcher m = Pattern.compile("^([^:\\n]+\\.(?:java|cs)):(\\d+):\\s*(?:error:\\s*)?(.*?)(?:\\s+\\[(CS\\d+)\\])?\\s*$", Pattern.DOTALL).matcher(e);
        if (!m.find()) return e;
        String msg = m.group(3).trim(), he = "";
        try {
            he = explainer.apply(msg, m.group(4) == null ? "" : m.group(4));
        } catch (RuntimeException | LinkageError x) {
            // בלי הסבר
        }
        return m.group(1) + ", שורה " + m.group(2) + ": " + (he == null || he.isEmpty() ? msg : he + " (" + msg + ")");
    }

    static final class Result {
        /** הבדיקה נעשתה (גם אם הקוד לא התקמפל). false — לא נבדק: אין קומפיילר, או שהתוכנית מחכה לקלט */
        boolean ran;
        boolean timedOut, needsInput, crashed;
        /** null — לא ידוע */
        Boolean compiles, outputMatch;
        String error = "", output = "", expected = "";
        /** שגיאות הקומפילציה (עד 6 הראשונות), כל אחת "Targil.java:12: הודעה" (ב-C# גם "   [CS1002]" בסוף) — למורה, בתוך error */
        final List<String> errors = new ArrayList<String>();
        /** כמה שגיאות קומפילציה יש בסך הכול (0 — לא ידוע). לתלמיד מוצג רק המספר, לא הרשימה */
        int errorCount;

        /** השדה check של ההגשה (worker/src/access/classroom.js — cleanCheck) */
        Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<String, Object>();
            m.put("ran", ran);
            if (compiles != null) m.put("compiles", compiles);
            m.put("error", error);
            if (errorCount > 0) m.put("errors", errorCount);
            m.put("output", output);
            m.put("expected", expected);
            if (outputMatch != null) m.put("outputMatch", outputMatch);
            m.put("timedOut", timedOut);
            m.put("needsInput", needsInput);
            return m;
        }

        /** good — רץ והפלט תואם (או רץ בלי פלט רצוי להשוואה); warn — רץ אבל משהו לא תואם; bad — לא מתקמפל; none — לא נבדק */
        String tone() {
            if (!ran) return "none";
            if (Boolean.FALSE.equals(compiles)) return "bad";
            if (timedOut || crashed || Boolean.FALSE.equals(outputMatch)) return "warn";
            return "good";
        }

        /** שורה אחת לתלמיד, לפני השליחה */
        String summary() {
            if (needsInput) return "התוכנית מחכה לקלט מהמקלדת, ולכן לא נבדקה בהרצה.";
            if (!ran) return "הקוד לא נבדק בהרצה (לא נמצא קומפיילר במחשב הזה).";
            // רק כמה שגיאות יש — לא הרשימה (כשיש הרבה היא רק מבלבלת); את השגיאות עצמן רואים בעורך
            if (Boolean.FALSE.equals(compiles)) return "הקוד לא מתקמפל — יש בו " + (errorCount > 1 ? errorCount + " שגיאות." : errorCount == 1 ? "שגיאה אחת." : "שגיאה.");
            if (timedOut) return error.isEmpty() ? "ההרצה נעצרה אחרי " + TIMEOUT_SEC + " שניות (אולי לולאה שלא נגמרת)." : "ההרצה נעצרה: " + error + ".";
            if (crashed) return "התוכנית נעצרה באמצע בגלל שגיאה בזמן ריצה.";
            if (outputMatch == null) return "הקוד מתקמפל ורץ.";
            return outputMatch ? "הקוד רץ, והפלט תואם לפלט הרצוי." : "הקוד רץ, אבל הפלט שונה מהפלט הרצוי.";
        }
    }

    private static final Pattern JAVA_INPUT = Pattern.compile("new\\s+Scanner\\s*\\(\\s*System\\s*\\.\\s*in\\b|System\\s*\\.\\s*in\\s*\\.\\s*read\\s*\\(|InputStreamReader\\s*\\(\\s*System\\s*\\.\\s*in\\b|System\\s*\\.\\s*console\\s*\\(");
    private static final Pattern CS_INPUT = Pattern.compile("Console\\s*\\.\\s*(ReadLine|ReadKey|Read)\\s*\\(");
    private static final Pattern JAVA_PACKAGE = Pattern.compile("(?m)^\\s*package\\s+([\\w.]+)\\s*;");
    private static final Pattern JAVA_MAIN = Pattern.compile("static\\s+void\\s+main\\s*\\(");

    /** בלי הערות ומחרוזות — כדי ש-"Scanner(System.in)" בתוך הערה לא ייחשב קריאת קלט */
    static String code(String src) {
        return src.replaceAll("(?s)/\\*.*?\\*/", " ").replaceAll("//[^\\n]*", " ").replaceAll("\"(\\\\.|[^\"\\\\\\n])*\"", "\"\"");
    }

    /** התוכנית קוראת קלט מהמקלדת */
    static boolean readsInput(Collection<String> sources, boolean cs) {
        for (String s : sources) if ((cs ? CS_INPUT : JAVA_INPUT).matcher(code(s)).find()) return true;
        return false;
    }

    private static String cut(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max) + "\n…";
    }

    private static void compare(Result r, String expected) {
        r.expected = cut(SolutionCheck.clean(expected), MAX_OUTPUT);
        if (!r.expected.isEmpty() && !r.timedOut) r.outputMatch = !r.crashed && SolutionCheck.same(expected, r.output);
        else if (!r.expected.isEmpty()) r.outputMatch = false;
        r.output = cut(r.output, MAX_OUTPUT);
    }

    /**
     * Java. files — שם הקובץ → התוכן (חבילה אחת, כמו תרגיל מהמאגר); mainClass — המחלקה עם main (בלי חבילה; ריק — מחפשים);
     * lib — dsviz.jar; javaExe — ה-java של הסביבה (או null — ה-Java שמריץ את הכלי); expected — הפלט הרצוי (ריק — אין השוואה)
     */
    static Result java(Map<String, String> files, String mainClass, File lib, String javaExe, String expected) {
        Result r = new Result();
        File work = null;
        try {
            String main = mainClass == null ? "" : mainClass, pkg = "";
            for (Map.Entry<String, String> f : files.entrySet()) {
                String name = f.getKey().replaceAll(".*[/\\\\]", "").replaceAll("\\.java$", "");
                if ((main.isEmpty() || !files.containsKey(main + ".java")) && JAVA_MAIN.matcher(code(f.getValue())).find()) main = name;
            }
            if (main.isEmpty()) return r;   // אין main — אין מה להריץ
            String mainText = files.get(main + ".java");
            Matcher pm = JAVA_PACKAGE.matcher(code(mainText == null ? "" : mainText));
            if (pm.find()) pkg = pm.group(1);
            work = Files.createTempDirectory("dsviz-submit").toFile();
            File src = new File(work, "src"), bin = new File(work, "bin"), dir = pkg.isEmpty() ? src : new File(src, pkg.replace('.', File.separatorChar));
            dir.mkdirs();
            bin.mkdirs();
            List<File> sources = new ArrayList<File>();
            for (Map.Entry<String, String> f : files.entrySet()) {
                File out = new File(dir, f.getKey().replaceAll(".*[/\\\\]", ""));
                Files.write(out.toPath(), f.getValue().getBytes(StandardCharsets.UTF_8));
                sources.add(out);
            }
            // ecj (בתוך Eclipse) מקמפל ל-Java 8 — רץ בכל Java; javac מקמפל לגרסה של עצמו, ולכן מריצים עם אותו Java
            String exe = null;
            List<String> errors = SolutionCheck.javacErrors(sources, bin, lib);
            if (errors == null) {
                File javac = javacNear(javaExe);
                if (javac == null) return r;   // אין קומפיילר במחשב
                errors = javacProcess(javac, sources, bin, lib, work);
                if (errors == null) return r;
                exe = SolutionCheck.javaOf(javac.getParentFile().getParent());
            }
            r.ran = true;
            r.compiles = errors.isEmpty();
            if (!errors.isEmpty()) {
                StringBuilder b = new StringBuilder();
                r.errorCount = errors.size();
                r.errors.addAll(errors.subList(0, Math.min(6, errors.size())));
                for (String e : r.errors) b.append(b.length() == 0 ? "" : "\n").append(hebrewLine(e.replaceAll("\\s+\\[[^\\]]*\\]\\s*$", "")));
                r.error = cut(b.toString(), MAX_ERROR);
                r.expected = cut(SolutionCheck.clean(expected), MAX_OUTPUT);
                return r;
            }
            if (readsInput(files.values(), false)) {
                r.ran = false;
                r.needsInput = true;
                r.expected = cut(SolutionCheck.clean(expected), MAX_OUTPUT);
                return r;
            }
            if (exe == null) exe = javaExe != null && new File(javaExe).isFile() && !inEclipse() ? javaExe : SolutionCheck.javaOf(System.getProperty("java.home"));
            if (exe == null) { r.ran = false; return r; }
            List<String> cmd = new ArrayList<String>(Arrays.asList(exe, "-Djava.awt.headless=true", "-Dfile.encoding=UTF-8", "-Dstdout.encoding=UTF-8",
                    "-Dstderr.encoding=UTF-8", "-Dsun.stdout.encoding=UTF-8", "-Dsun.stderr.encoding=UTF-8", "-Xmx256m",
                    "-cp", bin.getAbsolutePath() + File.pathSeparator + lib.getAbsolutePath(), (pkg.isEmpty() ? "" : pkg + ".") + main));
            String[] out = exec(cmd, work, TIMEOUT_SEC);
            if (out == null) { r.ran = false; return r; }
            r.output = SolutionCheck.clean(out[0]);
            String err = SolutionCheck.clean(out[1]);
            r.timedOut = out[2] == null;
            if (!r.timedOut && (!"0".equals(out[2]) || err.contains("Exception in thread \"main\""))) {
                r.crashed = true;
                r.error = cut(firstLines(err.replaceAll("(?m)^Picked up .*\\n?", ""), 4), MAX_ERROR);
            }
            compare(r, expected);
            return r;
        } catch (IOException | RuntimeException e) {
            r.ran = false;
            return r;
        } finally {
            if (work != null) delete(work);
        }
    }

    /** C#. files — שם הקובץ → התוכן (פרויקט אחד); dsvizCs — הפקודה של dsviz-cs (VsSubmit.dsvizCs) */
    static Result cs(Map<String, String> files, List<String> dsvizCs, String expected) {
        Result r = new Result();
        File work = null;
        try {
            work = Files.createTempDirectory("dsviz-submit").toFile();
            for (Map.Entry<String, String> f : files.entrySet())
                Files.write(new File(work, f.getKey().replaceAll(".*[/\\\\]", "")).toPath(), f.getValue().getBytes(StandardCharsets.UTF_8));
            boolean input = readsInput(files.values(), true);
            List<String> cmd = new ArrayList<String>(dsvizCs);
            if (input) {   // רק קומפילציה של הפרויקט כולו: אין מי שיקליד
                cmd.add("--check-project");
                cmd.add(work.getAbsolutePath());
            } else {
                cmd.add(work.getAbsolutePath());
                cmd.add("--trace");
                cmd.add(new File(work, "run.trace").getAbsolutePath());
            }
            String[] out = exec(cmd, work, TIMEOUT_SEC + CS_COMPILE_SEC);
            if (out == null) return r;   // אין dsviz-cs במחשב
            String text = out[0].replace("\r", "");
            int nl = text.indexOf('\n');
            // dsviz-cs פותח תמיד ב-"הרצה ויזואלית: מקמפל את...". בלי השורה הזו הוא לא עלה בכלל (אין ‎.NET‎, קובץ חסר) — "לא נבדק",
            // ולא "התוכנית של התלמיד נפלה"
            boolean started = text.startsWith("הרצה ויזואלית");
            if (started) text = nl >= 0 ? text.substring(nl + 1) : "";
            String err = SolutionCheck.clean(out[1]);
            r.expected = cut(SolutionCheck.clean(expected), MAX_OUTPUT);
            String all = input ? SolutionCheck.clean(out[0] + "\n" + out[1]) : err;   // --check-project מדפיס את השגיאות לפלט
            if (all.contains(": error CS")) {
                r.ran = true;
                r.compiles = false;
                StringBuilder b = new StringBuilder();
                Matcher em = Pattern.compile("(?m)^\\s*(.+?\\.cs)\\((\\d+),\\d+\\): error (CS\\d+): (.*)$").matcher(all.replace(work.getAbsolutePath() + File.separator, ""));
                java.util.Set<String> all2 = new java.util.LinkedHashSet<String>();   // כל השגיאות (לספירה); לרשימה — עד 6
                while (em.find()) {
                    String raw = new File(em.group(1)).getName() + ":" + em.group(2) + ": " + em.group(4).trim() + "   [" + em.group(3) + "]";
                    if (!all2.add(raw) || r.errors.size() >= 6) continue;
                    r.errors.add(raw);
                    b.append(b.length() == 0 ? "" : "\n").append(hebrewLine(raw));
                }
                r.errorCount = all2.size();
                if (r.errors.isEmpty()) {   // פורמט אחר — הטקסט כמו שהוא
                    for (String line : all.replace(work.getAbsolutePath() + File.separator, "").split("\n")) {
                        if (line.trim().isEmpty() || line.startsWith("errors:")) continue;
                        if (b.length() > 0) b.append('\n');
                        b.append(line.trim());
                    }
                }
                r.error = cut(firstLines(b.toString(), 8), MAX_ERROR);
                return r;
            }
            if (input) {
                r.compiles = out[2] != null && "0".equals(out[2]) ? Boolean.TRUE : null;
                r.needsInput = true;
                return r;
            }
            if (!started) return r;
            r.ran = true;
            r.compiles = true;
            r.output = SolutionCheck.clean(text);
            r.timedOut = out[2] == null;
            Matcher loop = Pattern.compile("(?m)^.*לא נגמרת").matcher(err);
            if (!r.timedOut && loop.find()) {
                // dsviz-cs מזהה בעצמו לולאה שלא נגמרת ועוצר את התוכנית (לפני 10 השניות): "הלולאה בשורה 9 לא נגמרת — ..."
                r.timedOut = true;
                r.error = cut(loop.group().trim(), MAX_ERROR);
            } else if (!r.timedOut && (!"0".equals(out[2]) || err.contains("Unhandled exception"))) {
                r.crashed = true;
                r.error = cut(firstLines(err, 4), MAX_ERROR);
            }
            compare(r, expected);
            return r;
        } catch (IOException | RuntimeException e) {
            r.ran = false;
            return r;
        } finally {
            if (work != null) delete(work);
        }
    }

    private static String firstLines(String s, int n) {
        String[] lines = s.trim().split("\n");
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < lines.length && i < n; i++) b.append(i == 0 ? "" : "\n").append(lines[i].trim());
        return b.toString();
    }

    /** בתוך Eclipse הקוד התקמפל ב-ecj ל-Java 8, ומריצים אותו ב-Java שמריץ את Eclipse (תמיד קיים) */
    private static boolean inEclipse() {
        try {
            Class.forName("org.eclipse.jdt.core.compiler.batch.BatchCompiler", false, RunCheck.class.getClassLoader());
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    /** javac ליד ה-java שנמסר (JDK של IntelliJ / VS Code), או ליד ה-Java שמריץ את הכלי */
    static File javacNear(String javaExe) {
        List<File> dirs = new ArrayList<File>();
        if (javaExe != null && !javaExe.isEmpty() && new File(javaExe).getParentFile() != null) dirs.add(new File(javaExe).getAbsoluteFile().getParentFile());
        dirs.add(new File(System.getProperty("java.home"), "bin"));
        for (File d : dirs) for (String n : new String[] { "javac.exe", "javac" }) {
            File f = new File(d, n);
            if (f.isFile()) return f;
        }
        return null;
    }

    /** javac כתהליך נפרד. השגיאות ("Targil.java:12: ..."), ריק אם התקמפל, null אם לא הצליח לרוץ */
    private static List<String> javacProcess(File javac, List<File> sources, File bin, File lib, File work) {
        List<String> cmd = new ArrayList<String>(Arrays.asList(javac.getAbsolutePath(), "-J-Duser.language=en", "-encoding", "UTF-8", "-nowarn",
                "-cp", lib.getAbsolutePath(), "-d", bin.getAbsolutePath()));
        for (File f : sources) cmd.add(f.getAbsolutePath());
        String[] out = exec(cmd, work, 60);
        if (out == null || out[2] == null) return null;
        List<String> errors = new ArrayList<String>();
        Matcher m = Pattern.compile("(?m)^(.+?\\.java):(\\d+): error: (.*)$").matcher((out[1] + "\n" + out[0]).replace("\r", ""));
        while (m.find()) errors.add(new File(m.group(1)).getName() + ":" + m.group(2) + ": " + m.group(3).trim());
        if (errors.isEmpty() && !"0".equals(out[2])) errors.add(cut(SolutionCheck.clean(out[1]), 600));
        return errors;
    }

    /** מריץ פקודה בלי קלט: {stdout, stderr, קוד יציאה (null — לא הסתיימה בזמן, ונעצרה)} — או null אם לא הצליח להריץ בכלל */
    static String[] exec(List<String> cmd, File dir, int timeoutSec) {
        try {
            Process proc = new ProcessBuilder(cmd).directory(dir).start();
            proc.getOutputStream().close();
            Pump out = new Pump(proc.getInputStream()), err = new Pump(proc.getErrorStream());
            out.start();
            err.start();
            boolean done = proc.waitFor(timeoutSec, java.util.concurrent.TimeUnit.SECONDS);
            if (!done) proc.destroyForcibly().waitFor(5, java.util.concurrent.TimeUnit.SECONDS);
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

    /** קורא את הפלט של התהליך ברקע (עד 256KB — יותר מזה לא נשמר, אבל ממשיכים לקרוא כדי שהתהליך לא ייתקע) */
    private static final class Pump extends Thread {
        private final java.io.InputStream in;
        private final java.io.ByteArrayOutputStream buf = new java.io.ByteArrayOutputStream();

        Pump(java.io.InputStream in) {
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

        synchronized String text() {
            return new String(buf.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    private static void delete(File f) {
        File[] kids = f.listFiles();
        if (kids != null) for (File k : kids) delete(k);
        f.delete();
    }
}
