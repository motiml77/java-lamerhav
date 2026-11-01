package il.lamerhav.dsviz.intellij;

import java.io.File;
import java.nio.file.Files;
import java.util.Arrays;

import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.impl.SimpleDataContext;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.projectRoots.JavaSdk;
import com.intellij.openapi.roots.LibraryOrderEntry;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiIdentifier;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.DefaultLightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;

import il.lamerhav.dsviz.IjShared;

/**
 * בדיקות של התוסף בתוך IntelliJ "חי" (בלי מסך): הוספת הספרייה למודול, "הוסף מבנה נתונים", הכפתור ליד main,
 * הפעולות בתפריטים, המנורה של "הצעה לפתרון" (רק ליד פעולה שיש לה הצעה), והחלונות (בלי להציג אותם).
 */
public class DsvizPluginTest extends LightJavaCodeInsightFixtureTestCase {
    private static final LightProjectDescriptor JDK = new DefaultLightProjectDescriptor(
            () -> JavaSdk.getInstance().createJdk("jdk-tests", System.getProperty("java.home"), false));

    @Override
    protected LightProjectDescriptor getProjectDescriptor() {
        return JDK;
    }

    @Override
    protected void setUp() throws Exception {
        VfsRootAccess.allowRootAccess(getTestRootDisposable(), System.getProperty("java.home"),
                new File(System.getProperty("dsviz.lib")).getParent(), System.getProperty("java.io.tmpdir"));
        super.setUp();
        Dsviz.setOn(true);
    }

    @Override
    protected void tearDown() throws Exception {
        try {
            removeLibrary(getModule());
            System.clearProperty("dsviz.solutions");
            Dsviz.setOn(true);
        } finally {
            super.tearDown();
        }
    }

    private static void removeLibrary(Module m) {
        if (!LibrarySetup.hasLibrary(m)) return;
        ModuleRootModificationUtil.updateModel(m, model -> {
            for (OrderEntry e : model.getOrderEntries()) {
                if (e instanceof LibraryOrderEntry && LibrarySetup.LIB_NAME.equals(((LibraryOrderEntry) e).getLibraryName()))
                    model.getModuleLibraryTable().removeLibrary(((LibraryOrderEntry) e).getLibrary());
            }
        });
    }

    private boolean queueKnown() {
        return JavaPsiFacade.getInstance(getProject()).findClass(LibrarySetup.PROBE, GlobalSearchScope.allScope(getProject())) != null;
    }

    // ------------------------------------------------------------------ ספריית הקורס

    public void testLibraryAutoSetup() {
        assertNotNull("dsviz.jar (‎-Ddsviz.lib)", Dsviz.libraryJar());
        assertFalse(queueKnown());
        assertTrue("מודול Java בלי הספרייה צריך אותה", LibrarySetup.needs(getModule()));
        LibrarySetup.add(getModule());
        com.intellij.testFramework.IndexingTestUtil.waitUntilIndexesAreReady(getProject());
        assertTrue(LibrarySetup.hasLibrary(getModule()));
        assertTrue("Queue מוכר אחרי ההוספה", queueKnown());
        assertFalse("פעם שנייה — לא מוסיפים שוב", LibrarySetup.needs(getModule()));
        // עם קוד המקור (src/ שבתוך ה-jar)
        LibraryOrderEntry lib = null;
        for (OrderEntry e : ModuleRootManager.getInstance(getModule()).getOrderEntries())
            if (e instanceof LibraryOrderEntry && LibrarySetup.LIB_NAME.equals(((LibraryOrderEntry) e).getLibraryName())) lib = (LibraryOrderEntry) e;
        assertNotNull(lib);
        String[] sources = lib.getRootUrls(com.intellij.openapi.roots.OrderRootType.SOURCES);
        assertEquals(1, sources.length);
        assertTrue(sources[0], sources[0].endsWith("!/src"));
        // ensureAll לא מכפיל
        LibrarySetup.ensureAll(getProject());
        int count = 0;
        for (OrderEntry e : ModuleRootManager.getInstance(getModule()).getOrderEntries())
            if (e instanceof LibraryOrderEntry && LibrarySetup.LIB_NAME.equals(((LibraryOrderEntry) e).getLibraryName())) count++;
        assertEquals(1, count);
    }

    public void testNoLibraryWhenPoweredOff() {
        Dsviz.setOn(false);
        LibrarySetup.ensureAll(getProject());
        assertFalse(LibrarySetup.hasLibrary(getModule()));
    }

    public void testNoLibraryWhenCourseJarAlreadyThere() {
        myFixture.addClass("package unit4.collectionsLib; public class Queue<T> {}");
        assertFalse("כבר יש unit4.collectionsLib.Queue (ספרייה אחרת או קוד מקור) — לא נוגעים", LibrarySetup.needs(getModule()));
    }

    // ------------------------------------------------------------------ הוסף מבנה נתונים

    public void testInsertStructureAtCaret() {
        LibrarySetup.add(getModule());
        PsiFile f = myFixture.configureByText("Targil.java", "public class Targil {\n    public static void main(String[] args) {\n        <caret>\n    }\n}\n");
        String text = myFixture.getEditor().getDocument().getText();
        StructureInserter.Spot spot = new StructureInserter.Spot(myFixture.getEditor().getDocument(), myFixture.getCaretOffset());
        assertTrue(il.lamerhav.dsviz.NameCheck.insideFunction(text, spot.insertAt));
        InsertDialog d = new InsertDialog(getProject(), null, text, spot.insertAt, spot.indent);
        try {
            d.choose("stack", "queue");
            String code = d.code();
            assertNotNull(code);
            assertTrue(code, code.contains("Stack<Queue<Integer>>"));
            StructureInserter.insert(getProject(), myFixture.getEditor().getDocument(), f, spot, code);
        } finally {
            d.close(0);
        }
        String after = myFixture.getEditor().getDocument().getText();
        assertTrue(after, after.startsWith("import unit4.collectionsLib.*;"));
        assertTrue(after.contains("Stack<Queue<Integer>>"));
        myFixture.checkHighlighting(false, false, false);   // הקוד שנכנס מתקמפל (בלי שגיאות אדומות)
    }

    public void testInsertOutsideMethodIsRejected() {
        myFixture.configureByText("T.java", "public class T {\n    <caret>\n    public static void main(String[] a) { }\n}\n");
        StructureInserter.Spot spot = new StructureInserter.Spot(myFixture.getEditor().getDocument(), myFixture.getCaretOffset());
        assertFalse(il.lamerhav.dsviz.NameCheck.insideFunction(myFixture.getEditor().getDocument().getText(), spot.insertAt));
    }

    public void testExerciseDialogGeneratesCode() throws Exception {
        File dir = Files.createTempDirectory("dsviz-ex").toFile();
        ExerciseDialog d = new ExerciseDialog(getProject(), Arrays.asList(new ExerciseDialog.Target(dir, "src")), "list_stack");
        try {
            assertNotNull(d.options());
            assertEquals("Targil1", d.options().className);
            String code = il.lamerhav.dsviz.Scaffold.generate(d.options()).code;
            assertTrue(code, code.contains("Node<Stack<Integer>>"));
            assertTrue(code.contains("public static void main"));
        } finally {
            d.close(0);
        }
    }

    // ------------------------------------------------------------------ הכפתור ליד main והפעולות

    public void testGutterMarkerOnMainOnly() {
        myFixture.configureByText("Main.java", "public class Main {\n    static int f() { return 1; }\n    public static void main(String[] args) { }\n}\n");
        RunLineMarker c = new RunLineMarker();
        PsiMethod main = null, f = null;
        for (PsiMethod m : PsiTreeUtil.findChildrenOfType(myFixture.getFile(), PsiMethod.class)) if (m.getName().equals("main")) main = m; else f = m;
        assertNotNull(c.getInfo(main.getNameIdentifier()));
        assertNull(c.getInfo(f.getNameIdentifier()));
        assertEquals("DSViz.Run", ActionManager.getInstance().getId(c.getInfo(main.getNameIdentifier()).actions[0]));
        Dsviz.setOn(false);
        assertNull("DSViz כבוי — בלי כפתור", c.getInfo(main.getNameIdentifier()));
    }

    public void testActionsAvailability() {
        myFixture.configureByText("Main.java", "public class Main { public static void main(String[] a) { } }");
        ActionManager am = ActionManager.getInstance();
        for (String id : new String[] { "DSViz.Run", "DSViz.NewExercise", "DSViz.Insert", "DSViz.Submit", "DSViz.Examples", "DSViz.Help", "DSViz.Power", "DSViz.Menu" })
            assertNotNull(id, am.getAction(id));
        assertNull("הצעה לפתרון — רק ליד הפעולות, לא כפעולה בתפריט", am.getAction("DSViz.Solution"));
        AnAction run = am.getAction("DSViz.Run");
        AnActionEvent e = AnActionEvent.createFromDataContext(ActionPlaces.EDITOR_POPUP, null, SimpleDataContext.builder()
                .add(CommonDataKeys.PROJECT, getProject()).add(CommonDataKeys.PSI_FILE, myFixture.getFile()).add(CommonDataKeys.EDITOR, myFixture.getEditor()).build());
        run.update(e);
        assertTrue(e.getPresentation().isEnabledAndVisible());
        Dsviz.setOn(false);
        AnActionEvent e2 = AnActionEvent.createFromDataContext(ActionPlaces.EDITOR_POPUP, null, SimpleDataContext.builder()
                .add(CommonDataKeys.PROJECT, getProject()).add(CommonDataKeys.PSI_FILE, myFixture.getFile()).build());
        run.update(e2);
        assertFalse("DSViz כבוי — הפעולות מוסתרות", e2.getPresentation().isEnabledAndVisible());
        assertTrue(VisualRunner.mainClassIn((com.intellij.psi.PsiJavaFile) myFixture.getFile()) != null);
    }

    // ------------------------------------------------------------------ 💡 הצעה לפתרון

    public void testSolutionMarkerOnlyNextToStoredMethods() throws Exception {
        File store = File.createTempFile("dsviz-solutions", ".json");
        store.delete();
        System.setProperty("dsviz.solutions", store.getAbsolutePath());
        PsiFile f = myFixture.configureByText("StackTargil.java",
                "public class StackTargil {\n    public static int sumEven(int x) { return 0; }\n    public static int other(int x) { return 0; }\n}\n");
        String path = f.getVirtualFile().getPath();
        IjShared.addSolution(store, path, "StackTargil", "sumEven", Arrays.asList("x"), "א", "return x;", "ok");
        Solutions s = new Solutions();
        PsiElement sumEven = null, other = null;
        for (PsiIdentifier id : PsiTreeUtil.findChildrenOfType(f, PsiIdentifier.class)) {
            if (!(id.getParent() instanceof PsiMethod)) continue;
            if (id.getText().equals("sumEven")) sumEven = id;
            if (id.getText().equals("other")) other = id;
        }
        assertNotNull("💡 ליד פעולה של סעיף עם הצעה", s.getLineMarkerInfo(sumEven));
        assertNull("בלי 💡 ליד פעולה בלי הצעה", s.getLineMarkerInfo(other));
        PsiFile plain = myFixture.configureByText("Plain.java", "public class Plain { static int sumEven(int x) { return 0; } }");
        for (PsiIdentifier id : PsiTreeUtil.findChildrenOfType(plain, PsiIdentifier.class))
            if (id.getParent() instanceof PsiMethod) assertNull("בקובץ רגיל — אין 💡", s.getLineMarkerInfo(id));
        assertEquals("ok", Solutions.find((PsiMethod) sumEven.getParent()).status);
    }

    // ------------------------------------------------------------------ עברית

    public void testHebrewCompilerMessages() {
        assertEquals("חסרה הוראת return — הפעולה צריכה להחזיר ערך בכל מסלול (גם אחרי לולאה או if)", JavacHebrew.translate("java: missing return statement"));
        assertEquals("חסר ; בסוף הפקודה", JavacHebrew.translate("';' expected"));
        assertTrue(JavacHebrew.translate("incompatible types: String cannot be converted to int").contains("String"));
        assertEquals("something new", JavacHebrew.translate("something new"));
        assertEquals("// ab", Submitter.clean("// ‫a‬b"));
    }

    // ------------------------------------------------------------------ שליחת תרגיל: שמות תפוסים, main, סדר הלשוניות

    /** החבילה של התרגיל לא תתנגש: כל התיקיות שבראש כל תיקיות הקוד של הפרויקט נכנסות ל-"taken" במניפסט */
    public void testTakenNamesFromAllSourceRoots() throws Exception {
        com.intellij.openapi.vfs.VirtualFile src2 = myFixture.getTempDirFixture().findOrCreateDir("src2");
        com.intellij.testFramework.PsiTestUtil.addSourceRoot(getModule(), src2);
        try {
            myFixture.getTempDirFixture().findOrCreateDir("src2/ex01_stacks");
            myFixture.addFileToProject("ex02_queues/Q.java", "package ex02_queues; class Q {}");
            File chosen = Files.createTempDirectory("dsviz-src").toFile();
            new File(chosen, "ex03_trees").mkdirs();
            java.util.List<String> taken = com.intellij.openapi.application.ReadAction.compute(() -> Submitter.takenNames(getProject(), chosen));
            assertTrue(taken.toString(), taken.contains("ex01_stacks"));
            assertTrue("גם בתיקיית הקוד הראשונה", taken.contains("ex02_queues"));
            assertTrue("וגם בתיקייה שנבחרה", taken.contains("ex03_trees"));
            java.util.Map<String, Object> man = Submitter.manifest("java", new File(System.getProperty("dsviz.lib")), chosen,
                    java.util.Collections.<SubmitDialog.Item>emptyList(), "", taken);
            assertTrue(((java.util.List<?>) man.get("taken")).contains("ex01_stacks"));
            assertTrue(IjShared.toJson(man), IjShared.toJson(man).contains("\"taken\""));
        } finally {
            com.intellij.testFramework.PsiTestUtil.removeSourceRoot(getModule(), src2);
        }
    }

    /** הסעיפים במחלקה אחת ו-main ב-Program.java באותה חבילה: "הרצה ויזואלית" מהקובץ של הסעיפים מריצה את main של התרגיל */
    public void testMainOfExerciseFoundInSamePackage() {
        myFixture.addFileToProject("ex01_q/Program.java",
                "package ex01_q;\npublic class Program {\n    public static void main(String[] a) { System.out.println(Tasks.sum(3)); }\n}\n");
        myFixture.addFileToProject("ex01_q/Other.java", "package ex01_q;\npublic class Other {\n    public static void main(String[] a) { }\n}\n");
        PsiFile tasks = myFixture.addFileToProject("ex01_q/Tasks.java", "package ex01_q;\npublic class Tasks {\n    public static int sum(int n) {\n        return 0;\n    }\n}\n");
        com.intellij.psi.PsiClass m = VisualRunner.mainInPackage((com.intellij.psi.PsiJavaFile) tasks, ((com.intellij.psi.PsiJavaFile) tasks).getClasses()[0]);
        assertNotNull(m);
        assertEquals("ex01_q.Program", m.getQualifiedName());
        assertEquals(Arrays.asList("Other.java", "Program.java", "Tasks.java"),
                Submitter.openOrder(Arrays.asList("Program.java", "Other.java", "Tasks.java"), "Tasks.java", "Program.java"));
        assertEquals("main בקובץ של הסעיפים — פעם אחת", Arrays.asList("A.java", "Tasks.java"),
                Submitter.openOrder(Arrays.asList("Tasks.java", "A.java"), "Tasks.java", "Tasks.java"));
    }

    // ------------------------------------------------------------------ המקום של התשובה בעורך (צהוב)

    static final String STUBS = "package ex01_q;\n\npublic class Tasks {\n"
            + "    public static int sumEven(Queue<Integer> q) {\n        // ====כאן עונים סעיף א'====\n        \n    }\n\n"
            + "    public static boolean has(int x) {\n        // ====כאן עונים סעיף ב'====\n        \n    }\n}\n";

    public void testAnswerHighlightInSubmittedFilesOnly() {
        PsiFile f = myFixture.addFileToProject("ex01_q/Tasks.java", STUBS);
        PsiFile other = myFixture.addFileToProject("mine/Tasks.java", STUBS.replace("ex01_q", "mine"));
        AnswerMarks marks = AnswerMarks.get(getProject());
        marks.track(new File(f.getVirtualFile().getParent().getPath()));
        myFixture.configureFromExistingVirtualFile(f.getVirtualFile());
        com.intellij.openapi.editor.Document doc = myFixture.getEditor().getDocument();
        marks.attach(doc);
        java.util.List<com.intellij.openapi.editor.markup.RangeHighlighter> hs = marks.highlighters(doc);
        assertEquals("שני סעיפים — שני מקומות צהובים", 2, hs.size());
        assertEquals(Modern.ANSWER, hs.get(0).getTextAttributes(null).getBackgroundColor());
        assertNotNull("פס צהוב בשוליים", hs.get(0).getLineMarkerRenderer());
        assertTrue(doc.getText(hs.get(0).getTextRange()).contains("כאן עונים סעיף א"));
        // התלמיד כותב קוד בסעיף א — הצהוב שלו נעלם, של ב נשאר
        int at = doc.getText().indexOf("        \n    }") + 8;
        com.intellij.openapi.command.WriteCommandAction.runWriteCommandAction(getProject(), () -> doc.insertString(at, "return 1;"));
        com.intellij.testFramework.PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();
        hs = marks.highlighters(doc);
        assertEquals(1, hs.size());
        assertTrue(doc.getText(hs.get(0).getTextRange()).contains("כאן עונים סעיף ב"));
        // קובץ שלא נוצר ב"שליחת תרגיל" — בלי צהוב
        myFixture.configureFromExistingVirtualFile(other.getVirtualFile());
        marks.attach(myFixture.getEditor().getDocument());
        assertTrue(marks.highlighters(myFixture.getEditor().getDocument()).isEmpty());
        assertFalse(marks.tracked(other.getVirtualFile()));
    }

    /** הבנייה בחלון: שגיאה — שורה אדומה ו"נסו שוב", והקבצים נשארים; הצלחה — התוצאה נשמרת. השליחה לא בחוט של המסך */
    public void testSubmitDialogBuildsInPlace() throws Exception {
        File dir = Files.createTempDirectory("dsviz-sd").toFile();
        File img = File.createTempFile("page", ".png");
        javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(40, 20, java.awt.image.BufferedImage.TYPE_INT_RGB), "png", img);
        SubmitDialog d = new SubmitDialog(getProject(), Arrays.asList(new ExerciseDialog.Target(dir, "src")));
        try {
            d.add(img, "דף.png");
            java.util.concurrent.atomic.AtomicBoolean edt = new java.util.concurrent.atomic.AtomicBoolean(true);
            d.runner((items, note, target, l) -> {
                edt.set(javax.swing.SwingUtilities.isEventDispatchThread());
                l.progress("שולח את התרגיל… (בדרך כלל עד דקה)");
                Submitter.Outcome o = new Submitter.Outcome();
                o.error = "השרת לא ענה בזמן.\nפרטים טכניים ארוכים";
                return o;
            });
            d.start();
            for (int i = 0; i < 150 && !d.build().states().contains("✕"); i++) {
                com.intellij.testFramework.PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();
                Thread.sleep(20);
            }
            assertFalse("השליחה לא בחוט של המסך", edt.get());
            assertEquals("✓✕○○○", d.build().states());
            assertEquals("נסו שוב", ((javax.swing.JButton) d.footer().getComponent(0)).getText());
            assertEquals("הקבצים נשארים", 1, d.items().size());
            d.runner((items, note, target, l) -> {
                l.progress("בונה את התרגיל…");
                l.progress("בודק שהקוד מתקמפל…");
                l.progress("יש שגיאות קומפילציה — מבקש תיקון…");
                Submitter.Outcome o = new Submitter.Outcome();
                o.files.add(new File(dir, "T.java").getPath());
                return o;
            });
            d.start();
            for (int i = 0; i < 150 && d.outcome() == null; i++) {
                com.intellij.testFramework.PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();
                Thread.sleep(20);
            }
            assertNotNull(d.outcome());
            assertEquals("✓✓✓✓✓", d.build().states());
        } finally {
            if (!d.isDisposed()) d.close(1);
        }
    }

    /** כל ההודעות — בחלון האחד (Notice), מימין לשמאל: אין Messages.* / JOptionPane בשום מקום אחר בתוסף */
    public void testNoMessagesOrJOptionPaneOutsideNotice() throws Exception {
        File dir = new File(System.getProperty("dsviz.root"), "intellij-plugin/src/main/java");
        assertTrue(dir.getPath(), dir.isDirectory());
        java.util.List<String> bad = new java.util.ArrayList<String>();
        java.util.regex.Pattern p = java.util.regex.Pattern.compile("\\bMessages\\.|JOptionPane|import com\\.intellij\\.openapi\\.ui\\.Messages");
        int seen = 0;
        try (java.util.stream.Stream<java.nio.file.Path> files = Files.walk(dir.toPath())) {
            for (java.nio.file.Path f : (Iterable<java.nio.file.Path>) files.filter(x -> x.toString().endsWith(".java"))::iterator) {
                seen++;
                if (f.getFileName().toString().equals("Notice.java")) continue;
                java.util.List<String> lines = Files.readAllLines(f, java.nio.charset.StandardCharsets.UTF_8);
                for (int i = 0; i < lines.size(); i++) if (p.matcher(lines.get(i)).find()) bad.add(f.getFileName() + ":" + (i + 1) + ": " + lines.get(i).trim());
            }
        }
        assertTrue(seen > 20);
        assertTrue("חלונות הודעה ישנים (לא מימין לשמאל):\n" + String.join("\n", bad), bad.isEmpty());
    }
}
