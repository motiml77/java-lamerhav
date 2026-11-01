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
}
