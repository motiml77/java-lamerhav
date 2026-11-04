package il.lamerhav.dsviz.intellij;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

import com.intellij.execution.configurations.GeneralCommandLine;

import junit.framework.TestCase;

/**
 * מקצה לקצה, בלי IntelliJ: הפקודה שהתוסף בונה להרצה הוויזואלית (VisualRunner.commandLine) מריצה את LineByLine על Main.java
 * של הפרויקט (בלי מסך — מצב טקסט) ומפיקה צעדים; ו"שליחת תרגיל" (SubmitCli) בלי שרת נותנת הודעה בעברית.
 */
public class VisualRunCommandTest extends TestCase {
    private static File root() {
        return new File(System.getProperty("dsviz.root", ".."));
    }

    private static String java() {
        JdkFinder.Found j = JdkFinder.find(System.getProperty("java.home"), 0);
        assertNotNull("JDK", j);
        return j.java();
    }

    public void testVisualRunProducesSteps() throws Exception {
        File lib = Dsviz.libraryJar();
        assertNotNull("dsviz.jar", lib);
        File work = Files.createTempDirectory("dsviz-run").toFile();
        File main = new File(work, "Main.java");
        Files.copy(new File(root(), "src/Main.java").toPath(), main.toPath());
        String javac = new File(System.getProperty("java.home"), "bin/javac").getPath();
        Process c = new ProcessBuilder(javac, "-g", "-encoding", "UTF-8", "-cp", lib.getPath(), "-d", work.getPath(), main.getPath())
                .redirectErrorStream(true).start();
        String cout = new String(c.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertEquals(cout, 0, c.waitFor());

        String out = "";
        for (int attempt = 0; attempt < 3; attempt++) {   // כמו run-tests.sh: השורה האחרונה נכתבת מחוט רקע — לפעמים נאבדת בלי חלון
            GeneralCommandLine cmd = VisualRunner.commandLine(java(), Arrays.asList(lib.getPath(), work.getPath()), "Main", work, "-Djava.awt.headless=true");
            assertEquals(Dsviz.LINE_RUNNER, cmd.getParametersList().getList().get(cmd.getParametersList().getList().size() - 2));
            assertTrue(cmd.getParametersList().getList().contains("-Ddsviz.visual=true"));
            Process p = cmd.withRedirectErrorStream(true).createProcess();
            p.getOutputStream().close();
            out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(p.waitFor(120, TimeUnit.SECONDS));
            if (out.contains("הסכום: 21") && out.matches("(?s).*שורה [0-9]+:.*total \\+= x;.*")) break;
        }
        assertTrue(out, out.contains("הסכום: 21"));
        assertTrue("צעד לכל שורה (JDI):\n" + out, out.matches("(?s).*שורה [0-9]+:.*total \\+= x;.*"));
    }

    /**
     * כל הדוגמאות הפתורות (מחסנית, תור, רשימה, עץ, רקורסיה, מערכים, תלמידים, מכוניות, מבנה בתוך מבנה) — בפקודה שהתוסף בונה
     * ל"הרצה ויזואלית": כל אחת רצה עד הסוף בתוך 2 דקות, עם צעד לכל שורה, ובלי חריגה.
     */
    public void testAllSolvedExamplesRunToTheEnd() throws Exception {
        File lib = Dsviz.libraryJar();
        assertNotNull("dsviz.jar", lib);
        File work = Files.createTempDirectory("dsviz-examples").toFile();
        File pkg = new File(work, "examples");
        assertTrue(pkg.mkdirs());
        File[] all = new File(root(), "src/examples").listFiles((d, n) -> n.endsWith("Examples.java"));
        assertNotNull(all);
        assertTrue("יש לפחות 9 דוגמאות", all.length >= 9);
        java.util.List<String> cmdC = new java.util.ArrayList<>(Arrays.asList(new File(System.getProperty("java.home"), "bin/javac").getPath(),
                "-g", "-encoding", "UTF-8", "-cp", lib.getPath(), "-d", work.getPath()));
        for (File f : all) {
            File to = new File(pkg, f.getName());
            Files.copy(f.toPath(), to.toPath());
            cmdC.add(to.getPath());
        }
        Process c = new ProcessBuilder(cmdC).redirectErrorStream(true).start();
        String cout = new String(c.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertEquals(cout, 0, c.waitFor());
        StringBuilder bad = new StringBuilder();
        for (File f : all) {
            String cls = "examples." + f.getName().replace(".java", "");
            GeneralCommandLine cmd = VisualRunner.commandLine(java(), Arrays.asList(lib.getPath(), work.getPath()), cls, work, "-Djava.awt.headless=true");
            Process p = cmd.withRedirectErrorStream(true).createProcess();
            p.getOutputStream().close();
            java.io.ByteArrayOutputStream buf = new java.io.ByteArrayOutputStream();
            Thread pump = new Thread(() -> { try { p.getInputStream().transferTo(buf); } catch (java.io.IOException ignored) { } });
            pump.start();
            boolean ended = p.waitFor(120, TimeUnit.SECONDS);
            if (!ended) p.destroyForcibly();
            pump.join(5000);
            String out = buf.toString(StandardCharsets.UTF_8);
            if (!ended || !out.matches("(?s).*שורה [0-9]+:.*") || out.contains("Exception in thread"))
                bad.append(cls).append(ended ? "" : " (נתקע)").append(": ").append(out.length() > 400 ? out.substring(out.length() - 400) : out).append('\n');
        }
        assertEquals("כל הדוגמאות רצות עד הסוף", "", bad.toString());
    }

    public void testJdkFinderReadsReleaseFile() {
        File home = new File(System.getProperty("java.home"));
        JdkFinder.Found f = JdkFinder.find(home.getPath(), 0);
        assertNotNull(f);
        assertTrue(f.version >= 17);
        assertTrue("יש jdk.jdi ב-JDK של הבדיקות", f.jdi);
        assertNotNull("אין Java חדשה מספיק — בכל זאת מחזירים מה שיש (LineByLine יודיע)", JdkFinder.find(home.getPath(), 99));
    }

    public void testSubmitWithoutServerGivesHebrewError() throws Exception {
        System.setProperty("dsviz.worker", "http://127.0.0.1:9");   // אין שרת בכתובת הזאת
        try {
            File img = File.createTempFile("exercise", ".png");
            javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(40, 20, java.awt.image.BufferedImage.TYPE_INT_RGB), "png", img);
            File out = Files.createTempDirectory("dsviz-submit").toFile();
            Submitter.Outcome r = Submitter.runCli(JdkFinder.find(System.getProperty("java.home"), 0), Dsviz.libraryJar(), out,
                    Collections.singletonList(new SubmitDialog.Item(img, "דף.png")), "", null);
            assertNotNull(r.error);
            assertTrue(r.error, r.error.matches("(?s).*[א-ת].*"));
            assertTrue(r.files.isEmpty());
        } finally {
            System.clearProperty("dsviz.worker");
        }
    }
}
