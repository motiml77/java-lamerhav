package il.lamerhav.dsviz.intellij;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.KillableColoredProcessHandler;
import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.openapi.util.Key;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

/**
 * מקצה לקצה על תוכניות של תלמידים (intellij-plugin/src/test/programs): הפקודה שהתוסף בונה (VisualRunner.commandLine)
 * מריצה את LineByLine מ-release/dsviz.jar בלי מסך, וכל תוכנית מסתיימת עם הסבר בעברית — חריגות בזמן ריצה, System.exit,
 * חוטים, קלט חסר / לא נכון, קובץ חסר, עברית ואמוג'י, CRLF וטאבים, חבילה, תיקייה עם רווחים (ועם עברית — כשהמחשב יכול),
 * ותכונות חדשות של Java. ולולאה אינסופית / רקורסיה אינסופית / מבנה של 10,000 איברים: ה-Stop של IntelliJ עוצר את
 * ההרצה כולה (גם את התהליך של התוכנית עצמה), בלי תהליך יתום שממשיך לרוץ.
 */
public class EdgeProgramsTest extends BasePlatformTestCase {
    private static File bin, work;

    private static File programs() {
        return new File(EdgeSupport.root(), "intellij-plugin/src/test/programs");
    }

    private static String java() {
        JdkFinder.Found j = JdkFinder.find(System.getProperty("java.home"), 0);
        assertNotNull(j);
        return j.java();
    }

    private static File lib() {
        File l = Dsviz.libraryJar();
        assertNotNull("dsviz.jar", l);
        return l;
    }

    /** מקמפל את כל התוכניות פעם אחת (javac של ה-JDK, כמו ה-build של IntelliJ) */
    private static synchronized void compileAll() throws Exception {
        if (bin != null) return;
        work = Files.createTempDirectory("dsviz-edge").toFile();
        File w = work;
        Runtime.getRuntime().addShutdownHook(new Thread(() -> com.intellij.openapi.util.io.FileUtil.delete(w)));
        File src = new File(work, "src");
        copy(programs(), src);
        // CRLF + טאבים — נוצר כאן (שלא יתוקן ע"י git)
        Files.write(new File(src, "Crlf.java").toPath(), ("public class Crlf {\r\n\tpublic static void main(String[] args) {\r\n\t\tint x = 1;\r\n"
                + "\t\tx++;\r\n\t\tSystem.out.println(\"x=\" + x);\r\n\t}\r\n}\r\n").getBytes(StandardCharsets.UTF_8));
        bin = new File(work, "bin");
        bin.mkdirs();
        List<String> files = new ArrayList<String>();
        try (java.util.stream.Stream<java.nio.file.Path> s = Files.walk(src.toPath())) {
            s.filter(p -> p.toString().endsWith(".java") && !p.toString().contains("clash")).forEach(p -> files.add(p.toString()));
        }
        javac(bin, files);
    }

    private static void javac(File out, List<String> files) throws Exception {
        List<String> cmd = new ArrayList<String>(Arrays.asList(new File(System.getProperty("java.home"), "bin/javac").getPath(), "-g", "-nowarn",
                "-encoding", "UTF-8", "-cp", lib().getPath(), "-d", out.getPath()));
        cmd.addAll(files);
        Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
        String o = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertEquals(o, 0, p.waitFor());
    }

    private static void copy(File from, File to) throws Exception {
        to.mkdirs();
        for (File f : from.listFiles()) {
            if (f.isDirectory()) copy(f, new File(to, f.getName()));
            else Files.copy(f.toPath(), new File(to, f.getName()).toPath());
        }
    }

    /** הרצה בלי מסך דרך הפקודה של התוסף; stdin — input (או סגור). עד 3 ניסיונות (השורה האחרונה נכתבת מחוט רקע) */
    private static String run(File cp, File dir, String cls, String input, int timeoutSec, Predicate<String> ok) throws Exception {
        String out = "";
        for (int attempt = 0; attempt < 3; attempt++) {
            GeneralCommandLine cmd = VisualRunner.commandLine(java(), Arrays.asList(lib().getPath(), cp.getPath()), cls, dir, "-Djava.awt.headless=true");
            Process p = cmd.withRedirectErrorStream(true).createProcess();
            if (input != null) p.getOutputStream().write(input.getBytes(StandardCharsets.UTF_8));
            p.getOutputStream().close();
            Future<byte[]> read = Executors.newSingleThreadExecutor().submit(() -> p.getInputStream().readAllBytes());
            boolean done = p.waitFor(timeoutSec, TimeUnit.SECONDS);
            if (!done) {
                p.descendants().forEach(ProcessHandle::destroyForcibly);
                p.destroyForcibly();
                fail(cls + ": לא הסתיימה תוך " + timeoutSec + " שניות");
            }
            out = new String(read.get(30, TimeUnit.SECONDS), StandardCharsets.UTF_8).replaceAll("[‎‏‪-‮]", "");
            if (ok.test(out)) return out;
        }
        fail(cls + ":\n" + tail(out));
        return out;
    }

    private static String tail(String s) {
        return s.length() > 3000 ? s.substring(s.length() - 3000) : s;
    }

    private static Predicate<String> has(String... parts) {
        return o -> {
            for (String p : parts) if (!o.contains(p)) return false;
            return true;
        };
    }

    // ------------------------------------------------------------------ חריגות בזמן ריצה — הסבר בעברית במקום (ו-stack trace רק בקונסול, כמו הרצה רגילה)

    public void testRuntimeErrorsGetHebrewExplanation() throws Exception {
        compileAll();
        Map<String, String[]> want = new LinkedHashMap<String, String[]>();
        want.put("Npe", new String[] { "שגיאה: NullPointerException", "ניסיון להשתמש באובייקט שהוא null", "[כרטיס null s length()]" });
        want.put("EmptyPop", new String[] { "שגיאה: RuntimeException: pop() על מחסנית ריקה", "[כרטיס empty-stack s pop()]" });
        want.put("EmptyRemove", new String[] { "שגיאה: RuntimeException: remove() על תור ריק", "[כרטיס empty-queue q remove()]" });
        want.put("Index", new String[] { "(Index.java:5)  שגיאה: ArrayIndexOutOfBoundsException: Index 3 out of bounds for length 3" });
        want.put("Cast", new String[] { "(Cast.java:4)  שגיאה: ClassCastException" });
        want.put("Div", new String[] { "(Div.java:4)  שגיאה: ArithmeticException: / by zero" });
        want.put("Custom", new String[] { "(Custom.java:10)  שגיאה: MyException: משהו השתבש" });
        want.put("MissingFile", new String[] { "שגיאה: FileNotFoundException: no-such-file.txt" });
        want.put("Scan", new String[] { "שגיאה: NoSuchElementException" });   // בלי קלט (stdin סגור)
        parallel(want);
    }

    // ------------------------------------------------------------------ תוכניות שמסתיימות בצורה מיוחדת / ארוכות

    public void testExitThreadsInputLongLoopAndModernJava() throws Exception {
        compileAll();
        Map<String, String[]> want = new LinkedHashMap<String, String[]>();
        want.put("Exit", new String[] { "before" });
        want.put("Threads", new String[] { "t0", "t2", "main done" });
        want.put("Million", new String[] { "התיעוד נעצר אחרי 5000 צעדים", "התוכנית הסתיימה" });
        want.put("Features", new String[] { "Point[x=1, y=2] ירוק 4 10 3" });
        want.put("HebrewIds", new String[] { "{מספר=7, טקסט=\"שלום עולם 😀\"}", "שלום עולם 😀 7", "תו=א tab=1" });
        want.put("Crlf", new String[] { "(Crlf.java:4)", "x=2" });
        want.put("pkg.sub.InPkg", new String[] { "(InPkg.java:8)", "push(א)", "top=א" });
        parallel(want);
        String exit = run(bin, work, "Exit", null, 60, has("before"));
        assertFalse("System.exit באמצע — מה שאחריו לא רץ", exit.contains("after"));
        // קלט מהמקלדת: מספר — עובד; טקסט במקום מספר — הסבר
        assertTrue(run(bin, work, "Scan", "21\n", 60, has("42")).contains("42"));
        run(bin, work, "Scan", "abc\n", 60, has("שגיאה: InputMismatchException"));
    }

    private void parallel(Map<String, String[]> want) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(3);
        try {
            Map<String, Future<String>> runs = new LinkedHashMap<String, Future<String>>();
            for (Map.Entry<String, String[]> e : want.entrySet())
                runs.put(e.getKey(), pool.submit(() -> run(bin, work, e.getKey(), null, 120, has(e.getValue()))));
            for (Map.Entry<String, Future<String>> r : runs.entrySet()) {
                String out = r.getValue().get(400, TimeUnit.SECONDS);
                for (String w : want.get(r.getKey())) assertTrue(r.getKey() + ": " + w + "\n" + tail(out), out.contains(w));
            }
        } finally {
            pool.shutdownNow();
        }
    }

    // ------------------------------------------------------------------ תיקייה עם רווחים ועברית, ושם מחלקה בעברית

    public void testProjectPathWithSpacesAndHebrew() throws Exception {
        compileAll();
        File spaced = new File(work, "my project dir");
        copy(bin, spaced);
        assertTrue(run(spaced, spaced, "pkg.sub.InPkg", null, 60, has("top=א")).contains("top=א"));
        java.nio.charset.Charset cs = VisualRunner.commandCharset();
        String hebrewDir = "פרויקט של דני";
        if (!cs.newEncoder().canEncode(hebrewDir)) {
            // המחשב לא מוגדר לעברית (כמו Windows באנגלית): Java לא תמצא את התיקייה — במקום "ClassNotFoundException" באנגלית, הודעה בעברית
            Notice.Spec s = VisualRunner.checkEncoding("pkg.sub.InPkg", Arrays.asList(lib().getPath(), work.getPath() + "/" + hebrewDir), work, cs);
            assertNotNull(s);
            EdgeSupport.assertStudentNotice(s);
            Notice.Spec c = VisualRunner.checkEncoding("שלום", Arrays.asList(lib().getPath()), work, cs);
            assertNotNull(c);
            EdgeSupport.assertStudentNotice(c);
            return;
        }
        File he = new File(work, hebrewDir);
        copy(bin, he);
        assertNull(VisualRunner.checkEncoding("pkg.sub.InPkg", Arrays.asList(lib().getPath(), he.getPath()), he, cs));
        run(he, he, "pkg.sub.InPkg", null, 60, has("top=א"));
        // מחלקה ששמה בעברית
        File hs = new File(work, "hesrc");
        hs.mkdirs();
        File f = new File(hs, "שלום.java");
        Files.write(f.toPath(), "public class שלום {\n    public static void main(String[] args) {\n        int מספר = 7;\n        System.out.println(\"מספר=\" + מספר);\n    }\n}\n"
                .getBytes(StandardCharsets.UTF_8));
        File hb = new File(work, "hebin");
        hb.mkdirs();
        javac(hb, Arrays.asList(f.getPath()));
        run(hb, work, "שלום", null, 60, has("מספר=7"));
    }

    /** מחלקה של התלמיד בשם Stack באותה חבילה + import unit4.collectionsLib.* — Stack שלו מנצח (כמו בכל Java), ו-java.util.Stack בשם מלא עובד */
    public void testStudentClassNamedStack() throws Exception {
        compileAll();
        File out = new File(work, "clashbin");
        out.mkdirs();
        javac(out, Arrays.asList(new File(work, "src/clash/StudentStack.java").getPath()));
        String o = run(out, work, "StudentStack", null, 60, has("3", "4", "התוכנית הסתיימה"));
        assertFalse(o, o.contains("שגיאה:"));
    }

    // ------------------------------------------------------------------ Stop: לולאה אינסופית, רקורסיה אינסופית, 10,000 איברים

    public void testStopKillsTheWholeRunWithoutOrphans() throws Exception {
        compileAll();
        for (String cls : new String[] { "Inf", "Rec", "Big" }) {
            File dir = new File(work, "stop-" + cls);   // תיקייה ייחודית — כדי למצוא את התהליכים של ההרצה הזאת
            copy(bin, dir);
            GeneralCommandLine cmd = VisualRunner.commandLine(java(), Arrays.asList(lib().getPath(), dir.getPath()), cls, dir, "-Djava.awt.headless=true");
            KillableColoredProcessHandler h = new KillableColoredProcessHandler(cmd);   // כמו VisualRunner.start
            StringBuffer out = new StringBuffer();
            h.addProcessListener(new ProcessAdapter() {
                @Override
                public void onTextAvailable(ProcessEvent e, Key outputType) {
                    if (out.length() < 200_000) out.append(e.getText());
                }
            });
            h.startNotify();
            long until = System.currentTimeMillis() + 30_000;
            while (System.currentTimeMillis() < until && children(dir).size() < 2) Thread.sleep(100);
            assertEquals(cls + ": LineByLine והתוכנית שהוא מריץ\n" + tail(out.toString()), 2, children(dir).size());
            Thread.sleep(3000);   // רצה כמה שניות (מדפיסה / מעמיקה / ממלאת)
            assertFalse(cls + ": עוד רצה (לא נגמרה מעצמה)", h.isProcessTerminated());
            long t = System.currentTimeMillis();
            h.destroyProcess();   // הכפתור Stop
            assertTrue(cls + ": נעצרה", h.waitFor(20_000));
            for (int i = 0; i < 100 && !children(dir).isEmpty(); i++) Thread.sleep(100);
            List<ProcessHandle> left = children(dir);
            for (ProcessHandle p : left) p.destroyForcibly();
            assertTrue(cls + ": נשאר תהליך יתום שממשיך לרוץ: " + left, left.isEmpty());
            assertTrue(cls + ": Stop לקח " + (System.currentTimeMillis() - t) + "ms", System.currentTimeMillis() - t < 15_000);
        }
    }

    /** התהליכים (java) שבשורת הפקודה שלהם התיקייה — LineByLine והתוכנית שהוא מריץ */
    private static List<ProcessHandle> children(File dir) {
        List<ProcessHandle> l = new ArrayList<ProcessHandle>();
        ProcessHandle.allProcesses().forEach(p -> {
            String c = p.info().commandLine().orElse("");
            if (p.isAlive() && c.contains(dir.getPath()) && c.contains("java")) l.add(p);
        });
        return l;
    }
}
