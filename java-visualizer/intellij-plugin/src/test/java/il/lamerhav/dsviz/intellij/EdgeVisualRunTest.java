package il.lamerhav.dsviz.intellij;

import java.io.File;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.compiler.CompileContext;
import com.intellij.openapi.compiler.CompileStatusNotification;
import com.intellij.openapi.compiler.CompilerMessage;
import com.intellij.openapi.compiler.CompilerMessageCategory;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.projectRoots.JavaSdk;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.util.ClassUtil;
import com.intellij.testFramework.DumbModeTestUtils;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.DefaultLightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;

/**
 * הרצה ויזואלית — מה תלמידים עושים: קובץ בלי main / שני main / main בקובץ אחר, קובץ ריק, שגיאות קומפילציה בקובץ הזה או רק
 * בקובץ אחר, קוד שלא נשמר, לחיצה כפולה, מודול בלי JDK, IntelliJ שעוד סורק את הפרויקט, שמות בעברית במחשב שלא מוגדר לעברית.
 * הקומפילציה עצמה מוחלפת (VisualRunner.compiler) — בודקים מה התוסף עושה לפני ואחרי. בכל מקרה: בלי חריגה ביומן, ותשובה
 * קצרה בעברית מימין לשמאל.
 */
public class EdgeVisualRunTest extends LightJavaCodeInsightFixtureTestCase {
    private static final LightProjectDescriptor JDK = new DefaultLightProjectDescriptor(
            () -> JavaSdk.getInstance().createJdk("jdk-tests", System.getProperty("java.home"), false));

    private EdgeSupport.Seen seen;
    private final List<CompileStatusNotification> compiles = new ArrayList<CompileStatusNotification>();
    private final List<Boolean> savedAtCompile = new ArrayList<Boolean>();
    private VisualRunner.Compiler realCompiler;

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
        seen = EdgeSupport.install(getProject(), getTestRootDisposable());
        realCompiler = VisualRunner.compiler;
        VisualRunner.compiler = (project, module, done) -> {
            savedAtCompile.add(FileDocumentManager.getInstance().getUnsavedDocuments().length == 0);
            compiles.add(done);
        };
        VisualRunner.end(getProject());
    }

    @Override
    protected void tearDown() throws Exception {
        try {
            VisualRunner.compiler = realCompiler;
            VisualRunner.end(getProject());
            removeLibrary(getModule());
        } finally {
            super.tearDown();
        }
    }

    private static void removeLibrary(Module m) {
        if (!LibrarySetup.hasLibrary(m)) return;
        ModuleRootModificationUtil.updateModel(m, model -> {
            for (com.intellij.openapi.roots.OrderEntry e : model.getOrderEntries()) {
                if (e instanceof com.intellij.openapi.roots.LibraryOrderEntry && LibrarySetup.LIB_NAME.equals(((com.intellij.openapi.roots.LibraryOrderEntry) e).getLibraryName()))
                    model.getModuleLibraryTable().removeLibrary(((com.intellij.openapi.roots.LibraryOrderEntry) e).getLibrary());
            }
        });
    }

    private PsiJavaFile java(String name, String text) {
        return (PsiJavaFile) myFixture.addFileToProject(name, text);
    }

    // ------------------------------------------------------------------ איזו מחלקה רצה

    public void testEmptyFileAndFileWithoutClass() {
        PsiJavaFile empty = java("Empty.java", "");
        VisualRunner.run(getProject(), empty);
        assertEquals("קובץ ריק — לא מקמפלים", 0, compiles.size());
        assertTrue(seen.last().heading, seen.last().heading.contains("אין מחלקה"));
        EdgeSupport.assertStudentNotice(seen.last());
        PsiJavaFile comments = java("OnlyComment.java", "// כאן יהיה התרגיל\n/* עוד לא כתבתי */\n");
        VisualRunner.run(getProject(), comments);
        assertEquals(0, compiles.size());
        EdgeSupport.assertStudentNotice(seen.last());
    }

    public void testNoMainOffersToAddOneAndOnlyIfAgreed() {
        PsiJavaFile f = (PsiJavaFile) myFixture.configureByText("Lonely.java", "public class Lonely {\n    int x;\n\n    int twice() {\n        return x * 2;\n    }\n}\n");
        String before = myFixture.getEditor().getDocument().getText();
        seen.answer = s -> false;
        VisualRunner.run(getProject(), f);
        assertEquals(0, compiles.size());
        Notice.Spec q = seen.last();
        assertEquals(Modern.Kind.QUESTION, q.kind);
        EdgeSupport.assertStudentNotice(q);
        assertEquals("\"לא\" — הקוד לא משתנה", before, myFixture.getEditor().getDocument().getText());
        seen.answer = s -> true;
        VisualRunner.run(getProject(), f);
        String after = myFixture.getEditor().getDocument().getText();
        assertTrue(after, after.contains("public static void main(String[] args)"));
        assertTrue("מה שהתלמיד כתב נשאר", after.contains("return x * 2;"));
        myFixture.checkHighlighting(false, false, false);
        assertEquals("אחרי שנוספה main — עוד לא מריצים (קודם כותבים בה)", 0, compiles.size());
    }

    public void testTwoMainsRunsTheExerciseOrTheOneAtTheCaret() {
        PsiJavaFile f = (PsiJavaFile) myFixture.configureByText("Two.java",
                "class Helper {\n    public static void main(String[] a) { }\n}\n\npublic class Two {\n    static class Inner {\n        public static void main(String[] a) { }\n    }\n\n"
                + "    public static void main(String[] a) {\n        <caret>int x = 1;\n    }\n}\n");
        assertEquals("המחלקה ששמה כשם הקובץ — לא מחלקת העזר שלפניה", "Two", VisualRunner.mainClassIn(f).getName());
        assertEquals("Two", VisualRunner.mainClassIn(f, f.findElementAt(myFixture.getCaretOffset())).getName());
        int inHelper = f.getText().indexOf("class Helper") + 8;
        assertEquals("הסמן במחלקת העזר — היא רצה", "Helper", VisualRunner.mainClassIn(f, f.findElementAt(inHelper)).getName());
        int inInner = f.getText().indexOf("static class Inner") + 15;
        PsiClass inner = VisualRunner.mainClassIn(f, f.findElementAt(inInner));
        assertEquals("Inner", inner.getName());
        assertEquals("Two$Inner", ClassUtil.getJVMClassName(inner));
        PsiJavaFile other = java("Other.java", "public class Other { public static void main(String[] a) { } }");
        assertEquals("מקום בקובץ אחר — לא משנה", "Two", VisualRunner.mainClassIn(f, other.getClasses()[0]).getName());
    }

    public void testMainInAnotherFileAsksWhichOne() {
        PsiJavaFile tasks = java("ex05/Tasks.java", "package ex05;\npublic class Tasks {\n    static int sum(int n) { return n; }\n}\n");
        java("ex05/RunA.java", "package ex05;\npublic class RunA {\n    public static void main(String[] a) { System.out.println(Tasks.sum(1)); }\n}\n");
        java("ex05/RunB.java", "package ex05;\npublic class RunB {\n    public static void main(String[] a) { System.out.println(Tasks.sum(2)); }\n}\n");
        seen.answer = s -> false;   // ביטול בחירה
        VisualRunner.run(getProject(), tasks);
        assertEquals(0, compiles.size());
        Notice.Spec choose = seen.last();
        assertNotNull("רשימה לבחירה", choose.extra);
        EdgeSupport.assertStudentNotice(choose);
        seen.answer = s -> true;    // הראשונה ברשימה
        VisualRunner.run(getProject(), tasks);
        assertEquals("בחרו — מקמפלים פעם אחת", 1, compiles.size());
    }

    // ------------------------------------------------------------------ שמירה, לחיצה כפולה, שגיאות קומפילציה

    public void testUnsavedEditsAreSavedBeforeCompiling() {
        PsiJavaFile f = (PsiJavaFile) myFixture.configureByText("Saved.java", "public class Saved {\n    public static void main(String[] a) {\n        <caret>\n    }\n}\n");
        myFixture.type("int x = 5;");
        Document doc = myFixture.getEditor().getDocument();
        assertTrue("הקלדה — עוד לא נשמר", FileDocumentManager.getInstance().isDocumentUnsaved(doc));
        VisualRunner.run(getProject(), f);
        assertEquals(1, compiles.size());
        assertEquals("כל הקבצים נשמרו לפני הקומפילציה", Boolean.TRUE, savedAtCompile.get(0));
        assertTrue(seen.notices.isEmpty());
    }

    public void testDoubleClickCompilesOnce() {
        PsiJavaFile f = java("Twice.java", "public class Twice { public static void main(String[] a) { } }");
        VisualRunner.run(getProject(), f);
        VisualRunner.run(getProject(), f);
        VisualRunner.run(getProject(), f);
        assertEquals("שלוש לחיצות מהירות — קומפילציה והרצה אחת", 1, compiles.size());
        assertEquals("בלי חלון קופץ — רק בלון קטן", 0, seen.notices.size());
        assertFalse(seen.balloons.isEmpty());
        EdgeSupport.assertStudentBalloon(seen.balloons.get(0));
        // הקומפילציה בוטלה (למשל IntelliJ נסגר / Stop) — אפשר להריץ שוב
        compiles.get(0).finished(true, 0, 0, context());
        VisualRunner.run(getProject(), f);
        assertEquals(2, compiles.size());
        // שגיאות בקוד — ההודעה, ואפשר להריץ שוב אחרי התיקון
        compiles.get(1).finished(false, 1, 0, context(error(f.getVirtualFile(), 0, "';' expected")));
        Notice.Spec err = seen.last();
        assertEquals(Modern.Kind.ERROR, err.kind);
        assertEquals("יש שגיאה בקוד", err.heading);
        EdgeSupport.assertStudentNotice(err);
        VisualRunner.run(getProject(), f);
        assertEquals(3, compiles.size());
    }

    public void testCompileErrorsHereBlockButErrorsInUnrelatedFilesDoNot() {
        PsiJavaFile main = java("Mine.java", "public class Mine {\n    public static void main(String[] a) {\n        Helper.go();\n    }\n}\n");
        PsiJavaFile helper = java("Helper.java", "public class Helper { static void go() { } }");
        PsiJavaFile other = java("OtherTargil.java", "public class OtherTargil { void f() { int x = } }");
        String src = main.getText();
        VirtualFile mv = main.getVirtualFile();
        // רק בתרגיל אחר — לא חוסם
        assertTrue(VisualRunner.relevantErrors(context(error(other.getVirtualFile(), 0, "illegal start of expression")), mv, src).isEmpty());
        // בקובץ שהתרגיל משתמש בו — חוסם; ושל הקובץ הזה קודם
        List<CompilerMessage> r = VisualRunner.relevantErrors(context(error(helper.getVirtualFile(), 0, "';' expected"), error(other.getVirtualFile(), 0, "x"),
                error(mv, 2, "cannot find symbol")), mv, src);
        assertEquals(2, r.size());
        assertEquals(mv, r.get(0).getVirtualFile());
        // שגיאה בלי קובץ (למשל "release version 99 not supported") — חוסמת רק כשאין אחרות
        assertEquals(1, VisualRunner.relevantErrors(context(error(null, 0, "invalid target release: 99")), mv, src).size());
        // הזרימה כולה: שגיאה בקובץ הזה — הודעה אחת קצרה, "מעבר לשגיאה הראשונה" מגיע לשורה
        VisualRunner.run(getProject(), main);
        compiles.get(0).finished(false, 2, 0, context(error(mv, 2, "cannot find symbol"), error(other.getVirtualFile(), 0, "x")));
        Notice.Spec s = seen.last();
        assertEquals("יש שגיאה בקוד", s.heading);
        assertNotNull(s.onPrimary);
        EdgeSupport.assertStudentNotice(s);
    }

    /** תלמיד עם מחלקה משלו בשם Stack, ו-java.util.Stack, ו-import חסר: אלה שגיאות קומפילציה רגילות (בחלון Build) — לא חריגה */
    public void testStudentStackClassAndMissingImport() {
        LibrarySetup.add(getModule());
        com.intellij.testFramework.IndexingTestUtil.waitUntilIndexesAreReady(getProject());
        myFixture.configureByText("StackUser.java", "import unit4.collectionsLib.*;\n\npublic class StackUser {\n    public static void main(String[] a) {\n"
                + "        Stack s = new Stack();\n        java.util.Stack<Integer> j = new java.util.Stack<>();\n        Queue<Integer> q = new Queue<Integer>();\n    }\n}\n\n"
                + "class Stack {\n    void push(int x) { }\n}\n");
        myFixture.checkHighlighting(false, false, false);   // המחלקה של התלמיד מנצחת (כמו ב-javac) — בלי שגיאה
        PsiFile missing = myFixture.configureByText("NoImport.java", "public class NoImport {\n    public static void main(String[] a) {\n        Queue<Integer> q = new Queue<Integer>();\n    }\n}\n");
        assertFalse("בלי import — שגיאה אדומה רגילה", myFixture.doHighlighting(com.intellij.lang.annotation.HighlightSeverity.ERROR).isEmpty());
        VisualRunner.run(getProject(), (PsiJavaFile) missing);
        assertEquals("מקמפלים, וההודעה תגיע מהקומפיילר", 1, compiles.size());
    }

    // ------------------------------------------------------------------ סביבה: JDK, סריקה, עברית בשורת הפקודה

    public void testModuleWithoutJdkSaysWhatToDo() {
        Module m = getModule();
        com.intellij.openapi.projectRoots.Sdk original = com.intellij.openapi.roots.ModuleRootManager.getInstance(m).getSdk();
        boolean inherited = com.intellij.openapi.roots.ModuleRootManager.getInstance(m).isSdkInherited();
        File dir;
        try {
            dir = com.intellij.openapi.util.io.FileUtil.createTempDirectory("dsviz-edge-deleted-jdk", null, true);
        } catch (java.io.IOException e) {
            throw new RuntimeException(e);
        }
        com.intellij.openapi.projectRoots.Sdk gone = JavaSdk.getInstance().createJdk("deleted-jdk", dir.getPath(), false);
        dir.delete();   // ה-JDK נמחק מהמחשב
        com.intellij.openapi.application.WriteAction.run(() -> com.intellij.openapi.projectRoots.ProjectJdkTable.getInstance().addJdk(gone));
        ModuleRootModificationUtil.setModuleSdk(m, gone);
        try {
            Notice.Spec s = VisualRunner.checkSdk(getProject(), m);
            assertNotNull("JDK שלא קיים במחשב", s);
            EdgeSupport.assertStudentNotice(s);
            assertTrue(s.heading, s.heading.contains("JDK"));
            PsiJavaFile f = java("NoJdk.java", "public class NoJdk { public static void main(String[] a) { } }");
            VisualRunner.run(getProject(), f);
            VisualRunner.run(getProject(), f);
            assertEquals("לא מקמפלים בלי JDK", 0, compiles.size());
            assertEquals("כל לחיצה — אותה הודעה (לא \"כבר מתחילה\")", 2, seen.notices.size());
            assertTrue(seen.balloons.isEmpty());
        } finally {
            if (inherited) ModuleRootModificationUtil.setSdkInherited(m);
            else ModuleRootModificationUtil.setModuleSdk(m, original);
            com.intellij.openapi.application.WriteAction.run(() -> com.intellij.openapi.projectRoots.ProjectJdkTable.getInstance().removeJdk(gone));
        }
        assertNull("עם JDK — בלי הודעה", VisualRunner.checkSdk(getProject(), m));
    }

    public void testWhileIndexingRunsOrSaysToWait() throws Throwable {
        PsiJavaFile withMain = java("Early.java", "public class Early { public static void main(String[] a) { } }");
        PsiJavaFile noMain = java("EarlyTasks.java", "public class EarlyTasks { int f() { return 1; } }");
        AnAction run = ActionManager.getInstance().getAction("DSViz.Run");
        assertTrue("עובד גם בזמן סריקה (לא \"not available while indexing\" באנגלית)", DumbService.isDumbAware(run));
        for (String id : new String[] { "DSViz.NewExercise", "DSViz.Insert", "DSViz.Submit", "DSViz.Examples", "DSViz.Help", "DSViz.Power" })
            assertTrue(id, DumbService.isDumbAware(ActionManager.getInstance().getAction(id)));
        DumbModeTestUtils.runInDumbModeSynchronously(getProject(), () -> {
            assertTrue(DumbService.isDumb(getProject()));
            VisualRunner.run(getProject(), withMain);
            VisualRunner.run(getProject(), noMain);
        });
        assertEquals("יש main — מקמפלים גם בזמן סריקה", 1, compiles.size());
        Notice.Spec wait = seen.last();
        assertTrue(wait.heading + wait.text, (wait.heading + wait.text).contains("סורק"));
        EdgeSupport.assertStudentNotice(wait);
    }

    public void testHebrewNamesWhenTheComputerIsNotSetToHebrew() {
        java.nio.charset.Charset ascii = java.nio.charset.StandardCharsets.US_ASCII, cp1255 = java.nio.charset.Charset.forName("windows-1255"),
                english = java.nio.charset.Charset.forName("windows-1252");
        List<String> cp = Arrays.asList("/home/dani/lib/dsviz.jar", "/home/dani/My Projects/out");
        File dir = new File("/home/dani/My Projects");
        assertNull("רווחים — בסדר", VisualRunner.checkEncoding("Main", cp, dir, ascii));
        assertNull("Windows בעברית — שם מחלקה בעברית עובר", VisualRunner.checkEncoding("שלום", cp, dir, cp1255));
        Notice.Spec cls = VisualRunner.checkEncoding("ex01.שלום", cp, dir, english);
        assertNotNull("Windows באנגלית — שם מחלקה בעברית", cls);
        assertTrue(cls.text, cls.text.contains("שלום"));
        EdgeSupport.assertStudentNotice(cls);
        Notice.Spec path = VisualRunner.checkEncoding("Main", Arrays.asList("/lib/dsviz.jar", "/home/דני/פרויקט/out"), dir, english);
        assertNotNull("תיקייה בעברית", path);
        assertTrue("רק שם התיקייה הבעייתית", path.text.contains("\"דני\"") && !path.text.contains("/home"));
        EdgeSupport.assertStudentNotice(path);
        assertNotNull("אמוג'י בשם תיקייה — גם ב-Windows בעברית", VisualRunner.checkEncoding("Main", Arrays.asList("/x/😀/out"), null, cp1255));
        assertNotNull(VisualRunner.commandCharset());
    }

    /** חבילה, מחלקה פנימית, ושם בעברית — השם שעובר ל-LineByLine */
    public void testJvmNamesForPackagesAndNestedClasses() {
        PsiJavaFile p = java("pkg/sub/InPkg.java", "package pkg.sub;\npublic class InPkg { public static void main(String[] a) { } }");
        assertEquals("pkg.sub.InPkg", ClassUtil.getJVMClassName(VisualRunner.mainClassIn(p)));
        PsiJavaFile he = java("Heb.java", "class שלום { public static void main(String[] a) { int מספר = 1; } }");
        assertEquals("שלום", ClassUtil.getJVMClassName(VisualRunner.mainClassIn(he)));
        PsiJavaFile rec = java("Rec.java", "public class Rec {\n    record P(int x) {}\n    enum E { A }\n    public static void main(String[] a) {\n"
                + "        var p = new P(1);\n        Runnable r = () -> System.out.println(p);\n        String s = switch (E.A) { case A -> \"א\"; };\n    }\n}\n");
        assertEquals("Rec", VisualRunner.mainClassIn(rec).getName());
    }

    /** הספרייה לפני ה-classpath של המודול (LineByLine מהתוסף), ושורת הפקודה לא תלויה ב-IntelliJ */
    public void testClasspathPutsOurLibraryFirst() {
        List<String> cp = VisualRunner.classpath(getModule());
        assertEquals(Dsviz.libraryJar().getAbsolutePath(), cp.get(0));
        com.intellij.execution.configurations.GeneralCommandLine cmd = VisualRunner.commandLine("java", cp, "pkg.Main", new File("/tmp/with space"));
        assertTrue(cmd.getCommandLineString().contains(Dsviz.LINE_RUNNER + " pkg.Main"));
        assertEquals(StandardCharsets.UTF_8, cmd.getCharset());
    }

    // ------------------------------------------------------------------ עזר: הודעות של הקומפיילר מדומות

    private CompilerMessage error(VirtualFile f, int line, String text) {
        com.intellij.openapi.project.Project project = getProject();
        return (CompilerMessage) Proxy.newProxyInstance(CompilerMessage.class.getClassLoader(), new Class<?>[] { CompilerMessage.class }, (p, m, a) -> {
            switch (m.getName()) {
                case "getVirtualFile": return f;
                case "getMessage": return text;
                case "getCategory": return CompilerMessageCategory.ERROR;
                case "getNavigatable": return f == null ? null : new OpenFileDescriptor(project, f, line, 0);
                case "getExportTextPrefix": case "getRenderTextPrefix": return "";
                case "hashCode": return System.identityHashCode(p);
                case "equals": return p == a[0];
                case "toString": return text;
                default: return null;
            }
        });
    }

    private static CompileContext context(CompilerMessage... errors) {
        return (CompileContext) Proxy.newProxyInstance(CompileContext.class.getClassLoader(), new Class<?>[] { CompileContext.class }, (p, m, a) -> {
            switch (m.getName()) {
                case "getMessages": return a[0] == CompilerMessageCategory.ERROR ? errors : new CompilerMessage[0];
                case "getMessageCount": return a[0] == CompilerMessageCategory.ERROR || a[0] == null ? errors.length : 0;
                case "hashCode": return System.identityHashCode(p);
                case "equals": return p == a[0];
                default: return null;
            }
        });
    }
}
