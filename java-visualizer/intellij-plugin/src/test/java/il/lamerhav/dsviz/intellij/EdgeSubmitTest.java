package il.lamerhav.dsviz.intellij;

import java.io.File;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/**
 * "שליחת תרגיל" — מה תלמידים עושים: בלי קבצים, Word ישן / zip / קובץ ריק / PDF פגום / קובץ ענק / 11 תמונות, דף שהוא לא תרגיל,
 * שרת שנופל (500), עמוס (503), עונה ג'יבריש, מנתק באמצע, אין אינטרנט, ביטול באמצע, לחיצה כפולה על "שליחה", אותו תרגיל פעמיים
 * (ex02), תיקייה שאי אפשר לכתוב בה או שנמחקה באמצע, והערה ארוכה עם אמוג'י. כל השליחה עוברת דרך SubmitCli (dsviz.jar) כמו
 * בתוסף, מול שרת מדומה (כמו tools/test/submit/mock-worker.js — התרגיל מ-tools/test/eclipse-it/submit/words.json, התרחיש לפי
 * "scenario:NAME" בהערה). בכל מקרה: הודעה קצרה בעברית, בלי stack trace ובלי אנגלית טכנית.
 */
public class EdgeSubmitTest extends BasePlatformTestCase {
    private HttpServer server;
    private final List<String> received = Collections.synchronizedList(new ArrayList<String>());
    private final AtomicInteger requests = new AtomicInteger();

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", this::handle);
        server.setExecutor(java.util.concurrent.Executors.newCachedThreadPool());
        server.start();
        System.setProperty("dsviz.worker", "http://127.0.0.1:" + server.getAddress().getPort());
    }

    @Override
    protected void tearDown() throws Exception {
        try {
            System.clearProperty("dsviz.worker");
            server.stop(0);
        } finally {
            super.tearDown();
        }
    }

    private void handle(HttpExchange x) throws java.io.IOException {
        byte[] body = x.getRequestBody().readAllBytes();
        String text = new String(body, StandardCharsets.UTF_8);
        String path = x.getRequestURI().getPath();
        if (path.equals("/v1/health")) {
            send(x, 200, "{\"ok\": true}");
            return;
        }
        requests.incrementAndGet();
        received.add(text);
        Matcher m = Pattern.compile("scenario:([A-Za-z0-9_-]+)").matcher(text);
        String sc = m.find() ? m.group(1) : "words";
        try {
            switch (sc) {
                case "busy": send(x, 503, "{\"ok\": false, \"code\": \"busy\", \"error\": \"השרת עמוס כרגע. נסו שוב בעוד דקה.\"}"); return;
                case "e500": send(x, 500, "<html><body>Internal Server Error</body></html>"); return;
                case "garbage": send(x, 200, "{\"ok\": tru"); return;
                case "notexercise": send(x, 422, "{\"ok\": false, \"code\": \"not_exercise\", \"error\": \"לא מצאתי בקבצים תרגיל תכנות. שלחו את דף התרגיל (צילום ברור או PDF).\"}"); return;
                case "empty": send(x, 200, "{\"ok\": true, \"exercise\": {}}"); return;
                case "reset": x.close(); return;   // השרת מנתק באמצע
                case "slow": Thread.sleep(6000); break;
                default: break;
            }
        } catch (InterruptedException e) {
            return;
        }
        String ex = new String(Files.readAllBytes(new File(EdgeSupport.root(), "tools/test/eclipse-it/submit/words.json").toPath()), StandardCharsets.UTF_8);
        send(x, 200, "{\"ok\": true, \"exercise\": " + ex + "}");
    }

    private static void send(HttpExchange x, int status, String json) throws java.io.IOException {
        byte[] b = json.getBytes(StandardCharsets.UTF_8);
        x.getResponseHeaders().add("content-type", "application/json");
        x.sendResponseHeaders(status, b.length);
        try (OutputStream o = x.getResponseBody()) {
            o.write(b);
        }
    }

    // ------------------------------------------------------------------ עזר

    private static File png() throws Exception {
        File img = File.createTempFile("page", ".png");
        javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(120, 80, java.awt.image.BufferedImage.TYPE_INT_RGB), "png", img);
        return img;
    }

    private static File file(String suffix, byte[] data) throws Exception {
        File f = File.createTempFile("edge", suffix);
        Files.write(f.toPath(), data);
        return f;
    }

    private static List<SubmitDialog.Item> items(File... files) {
        List<SubmitDialog.Item> l = new ArrayList<SubmitDialog.Item>();
        for (File f : files) l.add(new SubmitDialog.Item(f, f.getName()));
        return l;
    }

    private static Submitter.Outcome submit(File out, List<SubmitDialog.Item> items, String note, Submitter.Listener l) {
        return Submitter.runCli(JdkFinder.find(System.getProperty("java.home"), 0), Dsviz.libraryJar(), out, items, note, Collections.<String>emptyList(), l);
    }

    private static void assertFailedInHebrew(String what, Submitter.Outcome o) {
        assertNotNull(what + ": צריכה להיות שגיאה; files=" + o.files + " folder=" + o.folder + " errors=" + o.errors, o.error);
        assertTrue(what + ": בלי קבצים", o.files.isEmpty());
        EdgeSupport.assertStudentText(what, o.error);
        EdgeSupport.assertStudentText(what + " (השורה בחלון)", SubmitDialog.shortError(o.error));
    }

    // ------------------------------------------------------------------ הקבצים

    public void testBadFilesAreRejectedInHebrew() throws Exception {
        File out = Files.createTempDirectory("dsviz-sub").toFile();
        assertFailedInHebrew("בלי קבצים", submit(out, items(), "", null));
        assertFailedInHebrew("Word ישן (.doc)", submit(out, items(file(".doc", "word97".getBytes(StandardCharsets.UTF_8))), "", null));
        assertFailedInHebrew("zip", submit(out, items(file(".zip", new byte[] { 'P', 'K', 3, 4, 0, 0, 1, 2, 3 })), "", null));
        assertFailedInHebrew("תמונה ריקה (0 בתים)", submit(out, items(file(".png", new byte[0])), "", null));
        assertFailedInHebrew("PDF פגום (בלי %PDF)", submit(out, items(file(".pdf", "this is not a pdf".getBytes(StandardCharsets.UTF_8))), "", null));
        assertFailedInHebrew("Word פגום", submit(out, items(file(".docx", "PK not really".getBytes(StandardCharsets.UTF_8))), "", null));
        byte[] huge = new byte[11 * 1024 * 1024];
        huge[0] = '%'; huge[1] = 'P'; huge[2] = 'D'; huge[3] = 'F';
        assertFailedInHebrew("PDF של 11MB", submit(out, items(file(".pdf", huge)), "", null));
        File[] many = new File[11];
        for (int i = 0; i < many.length; i++) many[i] = png();
        assertFailedInHebrew("11 תמונות", submit(out, items(many), "", null));
        assertEquals("שום דבר לא נשלח לשרת", 0, requests.get());
        assertEquals("לא נוצר כלום בתיקייה", 0, out.list().length);
    }

    public void testDialogRejectsBadFilesRightAway() throws Exception {
        File dir = Files.createTempDirectory("dsviz-sd").toFile();
        SubmitDialog d = new SubmitDialog(getProject(), Arrays.asList(new ExerciseDialog.Target(dir, "src")));
        try {
            ValidationInfo v = d.doValidate();
            assertNotNull("בלי קבצים — \"שליחה\" אומרת מה חסר", v);
            EdgeSupport.assertStudentText("בלי קבצים", v.message);
            d.start();
            assertNull("בלי קבצים לא מתחילים", d.outcome());
            byte[] huge = new byte[(int) SubmitDialog.MAX_BYTES + 10];
            for (File bad : new File[] { file(".doc", new byte[] { 1 }), file(".zip", new byte[] { 1 }), file(".png", new byte[0]), file(".heic", new byte[] { 1 }),
                    file(".exe", new byte[] { 1 }), file("", new byte[] { 1 }), file(".pdf", huge) }) {
                d.add(bad, bad.getName());
                assertTrue(bad.getName() + " — לא נכנס", d.items().isEmpty());
                EdgeSupport.assertStudentText(bad.getName(), d.rejected());
            }
            for (int i = 0; i < 10; i++) d.add(png(), "צילום " + (i + 1) + ".png");
            assertEquals(10, d.items().size());
            assertNull(d.rejected());
            d.add(png(), "צילום 11.png");
            assertEquals("ה-11 לא נכנס", 10, d.items().size());
            EdgeSupport.assertStudentText("11", d.rejected());
            d.add(new File(dir, "no-such-file.png"), "x.png");   // קובץ שנמחק בין הבחירה להוספה
            assertEquals(10, d.items().size());
        } finally {
            d.close(1);
        }
    }

    // ------------------------------------------------------------------ השרת

    public void testServerProblemsGiveShortHebrewErrors() throws Exception {
        File out = Files.createTempDirectory("dsviz-sub").toFile();
        File page = png();
        for (String sc : new String[] { "busy", "e500", "garbage", "notexercise", "empty", "reset" })
            assertFailedInHebrew("שרת: " + sc, submit(out, items(page), "scenario:" + sc, null));
        System.setProperty("dsviz.worker", "http://127.0.0.1:9");   // אין אינטרנט / השרת לא עונה
        assertFailedInHebrew("אין חיבור", submit(out, items(page), "", null));
        assertEquals(0, out.list().length);
    }

    public void testSameExerciseTwiceAndLongNoteWithEmoji() throws Exception {
        File out = Files.createTempDirectory("dsviz-sub").toFile();
        String note = "רק סעיפים א-ב בבקשה 🙏😀 " + "ארוך מאוד ".repeat(400) + "👍";
        Submitter.Outcome a = submit(out, items(png()), note, null);
        assertNull(a.error, a.error);
        assertFalse(a.files.isEmpty());
        assertTrue("ההערה הגיעה לשרת שלמה, עם האמוג'י", received.get(0).contains("🙏😀") && received.get(0).contains("👍"));
        Submitter.Outcome b = submit(out, items(png()), "", null);
        assertNull(b.error, b.error);
        assertFalse(a.folder.equals(b.folder));
        assertTrue(a.folder, a.folder.startsWith("ex01_"));
        assertTrue("אותו תרגיל פעמיים — תיקייה חדשה: " + b.folder, b.folder.startsWith("ex02_"));
        for (String f : a.files) assertTrue(f, new File(f).isFile());
        // התלמיד כבר התחיל לענות ב-ex01 — הפעם השנייה לא דורסת
        File first = new File(a.files.get(0));
        Files.write(first.toPath(), "// התשובה שלי\n".getBytes(StandardCharsets.UTF_8), java.nio.file.StandardOpenOption.APPEND);
        submit(out, items(png()), "", null);
        assertTrue(new String(Files.readAllBytes(first.toPath()), StandardCharsets.UTF_8).endsWith("// התשובה שלי\n"));
    }

    public void testCancelMidwayStopsQuickly() throws Exception {
        File out = Files.createTempDirectory("dsviz-sub").toFile();
        AtomicBoolean cancel = new AtomicBoolean();
        List<String> progress = Collections.synchronizedList(new ArrayList<String>());
        Submitter.Listener l = new Submitter.Listener() {
            @Override
            public void progress(String text) {
                progress.add(text);
                if (text.startsWith("שולח")) cancel.set(true);   // התלמיד לוחץ "ביטול" כשהשליחה התחילה
            }

            @Override
            public boolean cancelled() {
                return cancel.get();
            }
        };
        long t = System.currentTimeMillis();
        Submitter.Outcome o = submit(out, items(png()), "scenario:slow", l);
        assertTrue("ביטול — מפסיקים תוך שניות, לא מחכים לשרת (" + (System.currentTimeMillis() - t) + "ms)", System.currentTimeMillis() - t < 5000);
        assertEquals("בוטל.", o.error);
        assertTrue(o.files.isEmpty());
        for (String p : progress) EdgeSupport.assertStudentText("התקדמות", p);
        Thread.sleep(7000);   // השרת "עונה" אחרי שבוטל — שום קובץ לא נוצר
        assertEquals(0, out.list().length);
    }

    public void testTargetFolderUnwritableOrDeletedDuringBuild() throws Exception {
        // תיקייה שאי אפשר לכתוב בה (כאן: "תיקייה" שהיא בעצם קובץ — כך זה נכשל גם כשהבדיקות רצות כ-root)
        File blocker = File.createTempFile("not-a-dir", ".txt");
        File bad = new File(blocker, "src");
        String why = Submitter.prepare(getProject(), new ExerciseDialog.Target(bad, "src"));
        assertNotNull(why);
        EdgeSupport.assertStudentText("prepare", why);
        assertFailedInHebrew("SubmitCli לתיקייה שאי אפשר ליצור", submit(bad, items(png()), "", null));
        // התיקייה נמחקה בזמן שהשרת עונה
        File out = Files.createTempDirectory("dsviz-sub").toFile();
        Thread killer = new Thread(() -> {
            try {
                Thread.sleep(2500);
                com.intellij.openapi.util.io.FileUtil.delete(out);
            } catch (InterruptedException ignored) {
                // סוף
            }
        });
        killer.start();
        Submitter.Outcome o = submit(out, items(png()), "scenario:slow", null);
        killer.join();
        if (o.error != null) assertFailedInHebrew("התיקייה נמחקה", o);
        else for (String f : o.files) assertTrue("נוצרה מחדש: " + f, new File(f).isFile());
    }

    // ------------------------------------------------------------------ החלון: לחיצה כפולה, ביטול

    public void testDoubleClickSendRunsOnceAndCancelStops() throws Exception {
        File dir = Files.createTempDirectory("dsviz-sd").toFile();
        SubmitDialog d = new SubmitDialog(getProject(), Arrays.asList(new ExerciseDialog.Target(dir, "src")));
        try {
            d.add(png(), "דף.png");
            AtomicInteger runs = new AtomicInteger();
            AtomicBoolean sawCancel = new AtomicBoolean();
            d.runner((items, note, target, l) -> {
                runs.incrementAndGet();
                for (int i = 0; i < 100 && !l.cancelled(); i++) {
                    try {
                        Thread.sleep(50);
                    } catch (InterruptedException e) {
                        break;
                    }
                }
                sawCancel.set(l.cancelled());
                Submitter.Outcome o = new Submitter.Outcome();
                o.error = "בוטל.";
                return o;
            });
            d.doOKAction();
            d.doOKAction();   // לחיצה כפולה על "שליחה"
            d.doOKAction();
            for (int i = 0; i < 20; i++) {
                PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();
                Thread.sleep(20);
            }
            assertEquals("שליחה אחת", 1, runs.get());
            d.doCancelAction();
            for (int i = 0; i < 100 && !sawCancel.get(); i++) Thread.sleep(50);
            assertTrue("השליחה ברקע ראתה את הביטול", sawCancel.get());
            PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();
            assertNull("אחרי ביטול — בלי תוצאה (לא נפתחים קבצים)", d.outcome());
        } finally {
            if (!d.isDisposed()) d.close(1);
        }
    }

    /** שגיאה שהגיעה עם זנב טכני באנגלית (נתיב, Permission denied, Connection reset) — נחתך; שורות בלי עברית נמחקות */
    public void testCleanRemovesTechnicalTails() {
        assertEquals("לא הצלחתי לקרוא את הקובץ דף.pdf.", Say.clean("לא הצלחתי לקרוא את הקובץ דף.pdf: /tmp/x/דף.pdf (Permission denied)"));
        assertEquals("תקלה בתקשורת עם השרת.", Say.clean("תקלה בתקשורת עם השרת: Connection reset"));
        assertEquals("השרת עמוס כרגע. נסו שוב בעוד דקה.", Say.clean("השרת עמוס כרגע. נסו שוב בעוד דקה."));
        assertEquals("לא הצלחתי ליצור את התרגיל.", Say.clean("לא הצלחתי ליצור את התרגיל.\njava.io.IOException: boom\n\tat x.y(Z.java:1)"));
        assertEquals("", Say.clean("Exception in thread \"main\" java.lang.NoClassDefFoundError"));
        assertEquals("לא הצלחתי ליצור את התרגיל: אין הרשאה לכתוב בתיקייה הזאת",
                Say.failed("לא הצלחתי ליצור את התרגיל", new java.nio.file.AccessDeniedException("/x/y")));
        assertEquals("לא הצלחתי להריץ את התוכנית.", Say.failed("לא הצלחתי להריץ את התוכנית", new RuntimeException("Cannot run program \"java\"")));
        assertEquals("לא הצלחתי להריץ את התוכנית: אין מספיק מקום בדיסק", Say.failed("לא הצלחתי להריץ את התוכנית", new java.io.IOException("No space left on device")));
    }
}
