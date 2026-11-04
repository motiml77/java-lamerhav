package il.lamerhav.dsviz.bank;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.projectRoots.JavaSdk;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase;

/**
 * התוסף "מאגר תרגילים" בתוך IntelliJ "חי" (בלי מסך): הכפתורים בסרגל ובתפריט, הפקודה שמפעילה את הכלי, והגשר — פתיחת קובץ בשורה,
 * בקשה בלי ה-token הנכון נדחית, "הרצה ויזואלית" על הקובץ, ורענון של קבצים שנוצרו מחוץ ל-IntelliJ.
 */
public class BankPluginTest extends JavaCodeInsightFixtureTestCase {
    private File src;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        src = new File(myFixture.getTempDirPath(), "src");
        src.mkdirs();
        VirtualFile vs = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(src);
        ModuleRootModificationUtil.updateModel(getModule(), m -> m.getContentEntries()[0].addSourceFolder(vs, false));
        com.intellij.openapi.projectRoots.Sdk sdk = JavaSdk.getInstance().createJdk("jdk-tests", System.getProperty("java.home"), false);
        com.intellij.openapi.application.WriteAction.runAndWait(() ->
                com.intellij.openapi.projectRoots.ProjectJdkTable.getInstance().addJdk(sdk, getTestRootDisposable()));
        ModuleRootModificationUtil.setModuleSdk(getModule(), sdk);
        // ספריית הקורס (release/dsviz.jar) — מחוץ לתיקיות שבדיקות של IntelliJ מרשות לגשת אליהן
        com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess.allowRootAccess(getTestRootDisposable(), new File(System.getProperty("dsviz.lib")).getParent());
        com.intellij.openapi.application.WriteAction.runAndWait(() -> com.intellij.openapi.roots.ProjectRootManager.getInstance(getProject()).setProjectSdk(sdk));
    }

    @Override
    protected void tearDown() throws Exception {
        try {
            BankLauncher.Session s = BankLauncher.SESSIONS.get(getProject());
            if (s != null) s.dispose();
        } finally {
            super.tearDown();
        }
    }

    public void testActionsInToolbarAndTools() {
        ActionManager am = ActionManager.getInstance();
        AnAction bank = am.getAction("DSViz.Bank"), q = am.getAction("DSViz.BankQuestion");
        assertNotNull("הכפתור 'מאגר תרגילים'", bank);
        assertNotNull("הכפתור 'השאלה'", q);
        assertEquals("מאגר תרגילים", bank.getTemplatePresentation().getText());
        assertEquals("השאלה", q.getTemplatePresentation().getText());
        for (String g : new String[] { "MainToolbarRight", "ToolsMenu" }) {
            List<String> ids = new ArrayList<String>();
            for (AnAction a : ((ActionGroup) am.getAction(g)).getChildren(null)) ids.add(am.getId(a));
            assertTrue(g + ": " + ids, ids.contains("DSViz.Bank") && ids.contains("DSViz.BankQuestion"));
        }
        assertNotNull("התוסף הראשי (DSViz) טעון — התוסף תלוי בו", ActionManager.getInstance().getAction("DSViz.Run"));
    }

    public void testCommand() throws Exception {
        assertNull("אין בעיה לפתוח: " + BankLauncher.problem(getProject()), BankLauncher.problem(getProject()));
        BankLauncher.Session s = BankLauncher.session(getProject());
        List<String> cmd = BankLauncher.command(getProject(), s, "bank", null);
        String c = String.join(" ", cmd);
        assertTrue(c, cmd.contains("il.lamerhav.dsviz.BankVs") && cmd.contains("bank") && cmd.contains("--ij"));
        assertEquals("תיקיית הקוד של הפרויקט", src.getCanonicalPath(), new File(cmd.get(cmd.indexOf("bank") + 1)).getCanonicalPath());
        assertTrue(c, cmd.get(cmd.indexOf("-cp") + 1).contains("dsviz.jar") && cmd.get(cmd.indexOf("-cp") + 1).contains("dsviz-bank-vs.jar"));
        assertEquals(String.valueOf(s.port()), cmd.get(cmd.indexOf("--port") + 1));
        assertEquals(s.token, cmd.get(cmd.indexOf("--token") + 1));
        assertTrue("token אקראי וארוך", s.token.length() >= 32);
        assertTrue(c, cmd.get(cmd.indexOf("--store") + 1).endsWith("solutions.json"));
        assertTrue(c, cmd.get(cmd.indexOf("--out") + 1).contains("production"));
        assertEquals(getProject().getName(), cmd.get(cmd.indexOf("--project") + 1));
    }

    public void testBridgeOpensFileAtLine() throws Exception {
        File f = write("ex01_stacks/StackTargil1.java", "package ex01_stacks;\n\npublic class StackTargil1 {\n\n    public static int sumStack() {\n        return 0;\n    }\n}\n");
        BankLauncher.Session s = BankLauncher.session(getProject());
        assertEquals("OK", send(s, s.token + "\tOPEN\t" + f.getPath() + "\t5"));
        pump();
        Editor ed = FileEditorManager.getInstance(getProject()).getSelectedTextEditor();
        assertNotNull("נפתח עורך", ed);
        assertEquals("StackTargil1.java", FileDocumentManager.getInstance().getFile(ed.getDocument()).getName());
        assertEquals("הסמן בשורה 5 (sumStack)", 4, ed.getCaretModel().getLogicalPosition().line);
    }

    public void testOpenAddsCourseLibraryToModule() throws Exception {
        File f = write("ex05_stacks/StackTargil5.java", "package ex05_stacks;\n\nimport unit4.collectionsLib.*;\n\npublic class StackTargil5 {\n"
                + "    public static void main(String[] a) { Stack<Integer> s = new Stack<Integer>(); }\n}\n");
        // כמו פרויקט שהתוסף הראשי עוד לא הוסיף לו את הספרייה: מוציאים אותה מהמודול (אם נוספה בפתיחה)
        ModuleRootModificationUtil.updateModel(getModule(), m -> {
            for (com.intellij.openapi.roots.OrderEntry e : m.getOrderEntries())
                if (e instanceof com.intellij.openapi.roots.LibraryOrderEntry && "dsviz".equals(((com.intellij.openapi.roots.LibraryOrderEntry) e).getLibraryName())) m.removeOrderEntry(e);
        });
        com.intellij.testFramework.IndexingTestUtil.waitUntilIndexesAreReady(getProject());
        assertTrue("לפני: המודול בלי ספריית הקורס", com.intellij.openapi.application.ReadAction.compute(() -> il.lamerhav.dsviz.intellij.LibrarySetup.needs(getModule())));
        BankLauncher.Session s = BankLauncher.session(getProject());
        assertEquals("OK", send(s, s.token + "\tOPEN\t" + f.getPath() + "\t6"));
        for (int i = 0; i < 40 && com.intellij.openapi.application.ReadAction.compute(() -> il.lamerhav.dsviz.intellij.LibrarySetup.needs(getModule())); i++) {
            pump();
            com.intellij.testFramework.IndexingTestUtil.waitUntilIndexesAreReady(getProject());
        }
        assertFalse("אחרי שהתרגיל נפתח: ספריית הקורס (unit4.collectionsLib) במודול — הבנייה וההרצה הוויזואלית עובדות",
                com.intellij.openapi.application.ReadAction.compute(() -> il.lamerhav.dsviz.intellij.LibrarySetup.needs(getModule())));
    }

    public void testSourceRootPrefersMavenLayout() throws Exception {
        File base = new File(myFixture.getTempDirPath());
        File mainJava = new File(base, "src/main/java"), testJava = new File(base, "src/test/java");
        mainJava.mkdirs();
        testJava.mkdirs();
        VirtualFile vm = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(mainJava), vt = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(testJava);
        ModuleRootModificationUtil.updateModel(getModule(), m -> {
            m.getContentEntries()[0].addSourceFolder(vm, false);
            m.getContentEntries()[0].addSourceFolder(vt, true);
        });
        assertEquals("פרויקט Maven / Gradle: התרגיל נוצר ב-src/main/java (לא בבדיקות)", mainJava.getCanonicalPath(), BankLauncher.sourceRoot(getProject()).getCanonicalPath());
    }

    public void testBridgeRejectsWrongToken() throws Exception {
        File f = write("ex02_queues/Q.java", "package ex02_queues;\npublic class Q {}\n");
        BankLauncher.Session s = BankLauncher.session(getProject());
        assertEquals("DENIED", send(s, "not-the-token\tOPEN\t" + f.getPath() + "\t1"));
        assertEquals("DENIED", send(s, "OPEN\t" + f.getPath()));
        pump();
        assertEquals("שום דבר לא נפתח", 0, FileEditorManager.getInstance(getProject()).getOpenFiles().length);
        assertTrue(s.received.isEmpty());
    }

    public void testBridgeDialogWhenNoDialogOpen() throws Exception {
        BankLauncher.Session s = BankLauncher.session(getProject());
        assertEquals("אין חלון דו-שיח פתוח", "0", send(s, s.token + "\tDIALOG"));
        assertEquals("DENIED", send(s, "x\tDIALOG"));
        assertTrue("הבדיקה החוזרת לא נרשמת", s.received.isEmpty());
    }

    public void testBridgeRefreshSeesNewAndDeletedFiles() throws Exception {
        BankLauncher.Session s = BankLauncher.session(getProject());
        File dir = new File(src, "ex04_lists");
        dir.mkdirs();
        File f = new File(dir, "L.java");
        Files.write(f.toPath(), "package ex04_lists;\npublic class L {}\n".getBytes(StandardCharsets.UTF_8));
        assertEquals("OK", send(s, s.token + "\tREFRESH\t" + src.getPath()));
        pumpUntil(() -> LocalFileSystem.getInstance().findFileByIoFile(f) != null);
        assertNotNull("קובץ שנוצר מחוץ ל-IntelliJ מופיע אחרי REFRESH", LocalFileSystem.getInstance().findFileByIoFile(f));
        f.delete();
        dir.delete();
        assertEquals("OK", send(s, s.token + "\tREFRESH\t" + src.getPath()));
        pumpUntil(() -> { VirtualFile v = LocalFileSystem.getInstance().findFileByIoFile(f); return v == null || !v.isValid(); });
        VirtualFile v = LocalFileSystem.getInstance().findFileByIoFile(f);
        assertTrue("קובץ שנמחק נעלם אחרי REFRESH", v == null || !v.isValid());
    }

    public void testUnknownCommandOutsideAutotest() throws Exception {
        BankLauncher.Session s = BankLauncher.session(getProject());
        assertEquals("פקודות הבדיקה (TYPE) לא פעילות בשימוש רגיל", "UNKNOWN", send(s, s.token + "\tTYPE\thello"));
    }

    // ------------------------------------------------------------------ עזרים

    private File write(String rel, String text) throws Exception {
        File f = new File(src, rel);
        f.getParentFile().mkdirs();
        Files.write(f.toPath(), text.getBytes(StandardCharsets.UTF_8));
        LocalFileSystem.getInstance().refreshAndFindFileByIoFile(f);
        return f;
    }

    private static String send(BankLauncher.Session s, String line) throws Exception {
        try (Socket so = new Socket(InetAddress.getLoopbackAddress(), s.port())) {
            Writer w = new OutputStreamWriter(so.getOutputStream(), StandardCharsets.UTF_8);
            w.write(line + "\n");
            w.flush();
            return new BufferedReader(new InputStreamReader(so.getInputStream(), StandardCharsets.UTF_8)).readLine();
        }
    }

    private static void pump() throws Exception {
        for (int i = 0; i < 10; i++) {
            PlatformTestUtil.dispatchAllEventsInIdeEventQueue();
            Thread.sleep(50);
        }
    }

    private static void pumpUntil(java.util.function.BooleanSupplier c) throws Exception {
        for (int i = 0; i < 100 && !c.getAsBoolean(); i++) {
            PlatformTestUtil.dispatchAllEventsInIdeEventQueue();
            Thread.sleep(50);
        }
    }
}
