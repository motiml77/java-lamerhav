package il.lamerhav.dsviz.intellij;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import com.intellij.openapi.compiler.CompilerMessage;
import com.intellij.openapi.compiler.CompilerMessageCategory;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.impl.JavaAwareProjectJdkTableImpl;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.CompilerTester;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase;

/**
 * הבנייה האמיתית של IntelliJ (JPS) על פרויקט עם module-info.java: אחרי שהתוסף הוסיף את הספרייה וכתב "requires dsviz;" — הקוד
 * שמשתמש ב-Stack מתקמפל; בלי ה-requires — "package unit4.collectionsLib is not visible" (כמו javac).
 */
public class ModuleInfoRealCompileTest extends JavaCodeInsightFixtureTestCase {
    private CompilerTester tester;

    @Override
    protected void setUp() throws Exception {
        VfsRootAccess.allowRootAccess(getTestRootDisposable(), System.getProperty("java.home"),
                new File(System.getProperty("dsviz.lib")).getParent(), System.getProperty("java.io.tmpdir"));
        super.setUp();
        Dsviz.setOn(true);
        Sdk jdk = JavaAwareProjectJdkTableImpl.getInstanceEx().getInternalJdk();
        com.intellij.openapi.application.WriteAction.runAndWait(() -> com.intellij.openapi.projectRoots.ProjectJdkTable.getInstance().addJdk(jdk, getTestRootDisposable()));
        ModuleRootModificationUtil.setModuleSdk(getModule(), jdk);
        IdeaTestUtil.setModuleLanguageLevel(getModule(), LanguageLevel.JDK_21, getTestRootDisposable());
        tester = new CompilerTester(getModule());
    }

    @Override
    protected void tearDown() throws Exception {
        try {
            if (tester != null) tester.tearDown();
        } finally {
            super.tearDown();
        }
    }

    private static List<String> errors(List<CompilerMessage> all) {
        List<String> l = new ArrayList<String>();
        for (CompilerMessage m : all) if (m.getCategory() == CompilerMessageCategory.ERROR) l.add(m.getMessage());
        return l;
    }

    public void testModuleInfoWithTheCourseLibraryCompilesInIntelliJ() throws Exception {
        PsiFile mi = myFixture.addFileToProject("module-info.java", "module student.app {\n}\n");
        myFixture.addFileToProject("app/Main.java", "package app;\nimport unit4.collectionsLib.Stack;\npublic class Main {\n"
                + "    public static void main(String[] a) { Stack<Integer> s = new Stack<Integer>(); s.push(3); System.out.println(s.top()); }\n}\n");
        com.intellij.testFramework.IndexingTestUtil.waitUntilIndexesAreReady(getProject());
        com.intellij.openapi.module.Module mod = getModule();
        String why = "isJava=" + LibrarySetup.isJava(mod) + " external=" + LibrarySetup.externalModule(mod) + " hasLib=" + LibrarySetup.hasLibrary(mod)
                + " probe=" + (com.intellij.psi.JavaPsiFacade.getInstance(getProject()).findClass(LibrarySetup.PROBE, mod.getModuleWithDependenciesAndLibrariesScope(false)) != null)
                + " uses=" + com.intellij.openapi.application.ReadAction.compute(() -> LibrarySetup.usesCourseLibrary(mod))
                + " type=" + com.intellij.openapi.module.ModuleType.get(mod).getId();
        assertTrue(why, LibrarySetup.needs(getModule()));
        LibrarySetup.add(getModule());   // הספרייה + "requires dsviz;"
        com.intellij.testFramework.IndexingTestUtil.waitUntilIndexesAreReady(getProject());
        assertTrue(com.intellij.openapi.fileEditor.FileDocumentManager.getInstance().getDocument(mi.getVirtualFile()).getText().contains("requires dsviz;"));
        List<String> ok = errors(tester.rebuild());
        assertTrue("אחרי התוסף: בלי שגיאות קומפילציה — " + ok, ok.isEmpty());
        assertNotNull("יש class", tester.findClassFile("app.Main", getModule()));
        // בלי requires — IntelliJ נכשל כמו javac (מוכיח שהבדיקה באמת בודקת)
        tester.setFileText(mi, "module student.app {\n}\n");
        List<String> bad = errors(tester.make());
        assertFalse("בלי requires חייבת להיות שגיאה", bad.isEmpty());
        assertTrue(bad.toString(), bad.toString().contains("unit4.collectionsLib"));
    }
}
