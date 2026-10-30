package il.lamerhav.dsviz;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * "הצעה לפתרון": בודקים שההצעות של Gemini באמת נכונות — בונים את התרגיל עם ההצעות במקום הפעולות הריקות (בתיקייה זמנית, לא בפרויקט
 * של התלמיד), מקמפלים, מריצים את main ומשווים את מה שהודפס לפלט הצפוי (expectedOutput — מה שהתלמיד רואה בהערה מעל main).
 * בלי תלות ב-Eclipse: הקומפיילר — ecj של Eclipse כשהוא זמין (תמיד, בתוך Eclipse), אחרת javac של ה-JDK.
 */
final class SolutionCheck {
    private SolutionCheck() {}

    /** OK — הודפס בדיוק הפלט הצפוי; RAN — רץ בלי שגיאות, אבל לא היה פלט צפוי להשוואה; UNCHECKED — אי אפשר היה לבדוק */
    enum Status { OK, RAN, WRONG, CRASH, TIMEOUT, COMPILE, UNCHECKED }

    static final class Result {
        Status status = Status.UNCHECKED;
        /** מה קרה — לשליחה לתיקון (mode=solution): שגיאות קומפילציה, השגיאה בזמן ריצה, או הפלט מול הפלט הצפוי */
        final List<String> problems = new ArrayList<String>();
        String output = "";

        boolean passed() { return status == Status.OK || status == Status.RAN; }

        /** איזו תוצאה טובה יותר (אחרי סבב תיקון בוחרים את הטובה מהשתיים) */
        int rank() {
            switch (status) {
                case OK: return 6;
                case RAN: return 5;
                case WRONG: return 4;
                case CRASH: return 3;
                case TIMEOUT: return 2;
                case COMPILE: return 1;
                default: return 0;
            }
        }

        /** המצב שנשמר עם ההצעות (SolutionStore) ומוצג בחלון */
        String stored() {
            return status == Status.OK ? "ok" : status == Status.RAN ? "ran" : status == Status.UNCHECKED ? "unchecked" : "failed";
        }
    }

    /**
     * work — תיקייה זמנית (נמחקת בסוף); lib — הספרייה (dsviz.jar); java — קובץ ההרצה של Java (או null — ה-Java שמריץ את Eclipse)
     */
    static Result run(AiScaffold.Exercise e, String pkg, File lib, String java, File work, int timeoutSec) {
        Result r = new Result();
        boolean any = false;
        for (AiScaffold.Task t : e.tasks) any |= !t.solution.isEmpty();
        if (!any || e.cs) return r;
        try {
            File src = new File(work, "src"), bin = new File(work, "bin"), dir = new File(src, pkg);
            dir.mkdirs();
            bin.mkdirs();
            List<File> files = new ArrayList<File>();
            for (AiScaffold.GeneratedFile g : AiScaffold.build(e, pkg, Collections.<String>emptyList(), true)) {
                File f = new File(dir, g.fileName());
                Files.write(f.toPath(), g.code.getBytes(StandardCharsets.UTF_8));
                files.add(f);
            }
            // ecj מקמפל ל-Java 8 — רץ בכל Java; javac (מחוץ ל-Eclipse) מקמפל לגרסה של עצמו — אז מריצים באותו Java
            List<String> errors = ecj(files, bin, lib);
            boolean sameJava = errors == null;
            if (errors == null) errors = javac(files, bin, lib);
            if (errors == null) return r;   // אין קומפיילר
            if (!errors.isEmpty()) {
                r.status = Status.COMPILE;
                r.problems.add("The files with your solutions do not compile:");
                r.problems.addAll(errors.subList(0, Math.min(20, errors.size())));
                return r;
            }
            String exe = !sameJava && java != null && new File(java).isFile() ? java : javaOf(System.getProperty("java.home"));
            if (exe == null) return r;
            List<String> cmd = new ArrayList<String>(Arrays.asList(exe, "-Djava.awt.headless=true", "-Dfile.encoding=UTF-8",
                    "-Dstdout.encoding=UTF-8", "-Dstderr.encoding=UTF-8", "-Dsun.stdout.encoding=UTF-8", "-Dsun.stderr.encoding=UTF-8",
                    "-cp", bin.getAbsolutePath() + File.pathSeparator + lib.getAbsolutePath(), pkg + "." + e.mainClass));
            ProcessBuilder pb = new ProcessBuilder(cmd).directory(work);
            Process p = pb.start();
            p.getOutputStream().close();   // בלי קלט מהמקלדת
            Reader out = new Reader(p.getInputStream()), err = new Reader(p.getErrorStream());
            out.start();
            err.start();
            boolean done = p.waitFor(timeoutSec, TimeUnit.SECONDS);
            if (!done) p.destroyForcibly().waitFor(5, TimeUnit.SECONDS);
            out.join(2000);
            err.join(2000);
            r.output = clean(out.text());
            String errText = clean(err.text());
            if (!done) {
                r.status = Status.TIMEOUT;
                r.problems.add("The program did not finish within " + timeoutSec + " seconds (an endless loop?). Output so far:");
                r.problems.add(cut(r.output, 1500));
                return r;
            }
            if (p.exitValue() != 0 || errText.contains("Exception in thread \"main\"")) {
                r.status = Status.CRASH;
                r.problems.add("The program crashed (exit code " + p.exitValue() + "). Error:");
                r.problems.add(cut(errText, 1500));
                r.problems.add("Output before the crash:");
                r.problems.add(cut(r.output, 1500));
                return r;
            }
            String expected = clean(e.expectedOutput);
            if (expected.isEmpty()) {
                r.status = Status.RAN;
                return r;
            }
            if (same(expected, r.output)) {
                r.status = Status.OK;
                return r;
            }
            r.status = Status.WRONG;
            r.problems.add("The output is different from expectedOutput.");
            r.problems.add("expectedOutput:\n" + cut(expected, 2000));
            r.problems.add("Actual output (with your solutions):\n" + cut(r.output, 2000));
            return r;
        } catch (Exception ex) {
            r.status = Status.UNCHECKED;
            return r;
        } finally {
            delete(work);
        }
    }

    /** אותו פלט: בלי הבדלים של רווחים בסוף שורה, שורות ריקות בסוף, \r או סימוני כיוון */
    static boolean same(String a, String b) {
        return clean(a).equals(clean(b));
    }

    static String clean(String s) {
        String t = s == null ? "" : s.replace("\r", "").replaceAll("[‎‏‪-‮⁦-⁩﻿]", "");
        StringBuilder b = new StringBuilder();
        for (String l : t.split("\n", -1)) b.append(l.replaceAll("\\s+$", "")).append('\n');
        return b.toString().replaceAll("\\n+$", "").replaceAll("^\\n+", "");
    }

    private static String cut(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max) + "\n...";
    }

    /** bin/java (ב-Windows: javaw.exe — בלי חלון שחור, ואם אין — java.exe) */
    static String javaOf(String home) {
        if (home == null || home.isEmpty()) return null;
        for (String n : new String[] { "bin/javaw.exe", "bin/java.exe", "bin/java" }) {
            File f = new File(home, n);
            if (f.isFile()) return f.getAbsolutePath();
        }
        return null;
    }

    // ------------------------------------------------------------------ קומפילציה

    private static final Pattern ECJ_ERROR = Pattern.compile("(?m)^\\d+\\. ERROR in (.+?) \\(at line (\\d+)\\)\\n(.*)\\n(.*)\\n(.*)");

    /**
     * השגיאות ("Targil.java:12: ..."), רשימה ריקה אם התקמפל, או null אם אין קומפיילר.
     * ecj — הקומפיילר של Eclipse (org.eclipse.jdt.core.compiler.batch.BatchCompiler), דרך reflection: מחוץ ל-Eclipse הוא לא קיים
     */
    private static List<String> ecj(List<File> files, File bin, File lib) {
        try {
            ClassLoader cl = SolutionCheck.class.getClassLoader();
            Class<?> bc = Class.forName("org.eclipse.jdt.core.compiler.batch.BatchCompiler", true, cl);
            Class<?> progress = Class.forName("org.eclipse.jdt.core.compiler.CompilationProgress", true, cl);
            Method m = bc.getMethod("compile", String[].class, PrintWriter.class, PrintWriter.class, progress);
            for (String level : new String[] { "-1.8", null }) {
                List<String> args = new ArrayList<String>();
                if (level != null) args.add(level);
                args.addAll(Arrays.asList("-nowarn", "-encoding", "UTF-8", "-d", bin.getAbsolutePath(), "-cp", lib.getAbsolutePath()));
                for (File f : files) args.add(f.getAbsolutePath());
                StringWriter out = new StringWriter(), err = new StringWriter();
                boolean ok = (Boolean) m.invoke(null, args.toArray(new String[0]), new PrintWriter(out), new PrintWriter(err), null);
                if (ok) return new ArrayList<String>();
                String text = (out + "\n" + err).replace("\r", "");
                List<String> errors = new ArrayList<String>();
                Matcher em = ECJ_ERROR.matcher(text);
                while (em.find()) errors.add(new File(em.group(1)).getName() + ":" + em.group(2) + ": " + em.group(5).trim() + "   [" + em.group(3).trim() + "]");
                if (!errors.isEmpty()) return errors;
                if (level == null) return Collections.singletonList(cut(text.trim(), 1500));
                // האפשרות -1.8 לא נתמכת בגרסה הזו של ecj — מנסים בלי
            }
            return null;
        } catch (Throwable t) {
            return null;
        }
    }

    /** javac של ה-JDK (בבדיקות מחוץ ל-Eclipse, או אם ecj לא נמצא) */
    private static List<String> javac(List<File> files, File bin, File lib) {
        try {
            javax.tools.JavaCompiler jc = javax.tools.ToolProvider.getSystemJavaCompiler();
            if (jc == null) return null;
            javax.tools.DiagnosticCollector<javax.tools.JavaFileObject> dc = new javax.tools.DiagnosticCollector<javax.tools.JavaFileObject>();
            try (javax.tools.StandardJavaFileManager fm = jc.getStandardFileManager(dc, Locale.ENGLISH, StandardCharsets.UTF_8)) {
                jc.getTask(null, fm, dc, Arrays.asList("-encoding", "UTF-8", "-nowarn", "-cp", lib.getAbsolutePath(), "-d", bin.getAbsolutePath()), null,
                        fm.getJavaFileObjectsFromFiles(files)).call();
            }
            List<String> errors = new ArrayList<String>();
            for (javax.tools.Diagnostic<? extends javax.tools.JavaFileObject> d : dc.getDiagnostics())
                if (d.getKind() == javax.tools.Diagnostic.Kind.ERROR)
                    errors.add((d.getSource() == null ? "" : new File(d.getSource().getName()).getName()) + ":" + d.getLineNumber() + ": "
                            + d.getMessage(Locale.ENGLISH).split("\n")[0]);
            return errors;
        } catch (Throwable t) {
            return null;
        }
    }

    // ------------------------------------------------------------------ עזרים

    /** קורא את הפלט של התוכנית ברקע (עד 64KB — יותר מזה לא נשמר) */
    private static final class Reader extends Thread {
        private final InputStream in;
        private final ByteArrayOutputStream buf = new ByteArrayOutputStream();

        Reader(InputStream in) {
            this.in = in;
            setDaemon(true);
        }

        @Override
        public void run() {
            byte[] b = new byte[4096];
            try {
                for (int n; (n = in.read(b)) > 0; ) if (buf.size() < 65536) buf.write(b, 0, n);
            } catch (Exception ignored) {
                // התהליך נסגר
            }
        }

        String text() {
            return new String(buf.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    private static void delete(File f) {
        File[] kids = f.listFiles();
        if (kids != null) for (File k : kids) delete(k);
        f.delete();
    }
}
