package il.lamerhav.dsviz.intellij;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;

import javax.swing.JButton;
import javax.swing.JComponent;

import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.impl.SimpleDataContext;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.projectRoots.JavaSdk;
import com.intellij.openapi.roots.LibraryOrderEntry;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiIdentifier;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.fixtures.DefaultLightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;

import il.lamerhav.dsviz.IjShared;
import il.lamerhav.dsviz.NameCheck;

/**
 * "הוסף מבנה נתונים", תרגיל חדש, 💡 הצעה לפתרון, המקום הצהוב של התשובה, כיבוי/הפעלה וספריית הקורס — מה תלמידים עושים:
 * סמן בהערה / במחרוזת / באמצע פקודה / בלמבדה / מחוץ לפעולה, שמות לא חוקיים או תפוסים, קובץ עם שגיאות, פעולה ששונה שמה,
 * solutions.json פגום, עריכה כבדה ו-undo, lib לקריאה בלבד ו-jar של unit4 משלהם.
 */
public class EdgeEditorTest extends LightJavaCodeInsightFixtureTestCase {
    private static final LightProjectDescriptor JDK = new DefaultLightProjectDescriptor(
            () -> JavaSdk.getInstance().createJdk("jdk-tests", System.getProperty("java.home"), false));

    private EdgeSupport.Seen seen;

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
    }

    @Override
    protected void tearDown() throws Exception {
        try {
            Dsviz.setOn(true);
            System.clearProperty("dsviz.solutions");
            removeLibraries(getModule());
        } finally {
            super.tearDown();
        }
    }

    private static void removeLibraries(Module m) {
        ModuleRootModificationUtil.updateModel(m, model -> {
            for (OrderEntry e : model.getOrderEntries()) {
                if (e instanceof LibraryOrderEntry && ((LibraryOrderEntry) e).getLibrary() != null
                        && (LibrarySetup.LIB_NAME.equals(((LibraryOrderEntry) e).getLibraryName()) || "unit4".equals(((LibraryOrderEntry) e).getLibraryName())))
                    model.getModuleLibraryTable().removeLibrary(((LibraryOrderEntry) e).getLibrary());
            }
        });
    }

    // ------------------------------------------------------------------ הוסף מבנה נתונים: איפה הסמן

    /** מכניס מחסנית במקום הסמן (כמו "הוסף מבנה נתונים") ובודק שהקובץ עדיין מתקמפל */
    private String insertAtCaret() {
        Document doc = myFixture.getEditor().getDocument();
        StructureInserter.Spot spot = StructureInserter.spot(myFixture.getFile(), doc, myFixture.getCaretOffset());
        assertTrue("בתוך פעולה", NameCheck.insideFunction(doc.getText(), spot.insertAt));
        InsertDialog d = new InsertDialog(getProject(), null, doc.getText(), spot.insertAt, spot.indent);
        try {
            d.choose("stack", "");
            assertNotNull(d.code());
            StructureInserter.insert(getProject(), doc, myFixture.getFile(), spot, d.code());
        } finally {
            d.close(0);
        }
        myFixture.checkHighlighting(false, false, false);
        return doc.getText();
    }

    private void libOn() {
        LibrarySetup.add(getModule());
        com.intellij.testFramework.IndexingTestUtil.waitUntilIndexesAreReady(getProject());
    }

    public void testCaretInsideBlockCommentInsertsAfterIt() {
        libOn();
        myFixture.configureByText("C1.java", "public class C1 {\n    public static void main(String[] args) {\n        int a = 1;\n"
                + "        /* הערה ארוכה\n           של <caret>תלמיד\n        */\n        a++;\n    }\n}\n");
        String after = insertAtCaret();
        assertTrue(after, after.indexOf("Stack<") > after.indexOf("*/"));
        assertTrue("ההערה נשארה שלמה", after.contains("/* הערה ארוכה\n           של תלמיד\n        */"));
    }

    public void testCaretInsideStringsAndTextBlocks() {
        libOn();
        myFixture.configureByText("C2.java", "public class C2 {\n    public static void main(String[] args) {\n        String s = \"שלום <caret>עולם\";\n        System.out.println(s);\n    }\n}\n");
        String after = insertAtCaret();
        assertTrue("המחרוזת לא נחתכה", after.contains("\"שלום עולם\";"));
        myFixture.configureByText("C3.java", "public class C3 {\n    public static void main(String[] args) {\n        String t = \"\"\"\n            שורה <caret>ראשונה\n            שנייה\n            \"\"\";\n        System.out.println(t);\n    }\n}\n");
        after = insertAtCaret();
        assertTrue(after, after.indexOf("Stack<") > after.indexOf("\"\"\";"));
    }

    public void testCaretInTheMiddleOfAMultiLineStatement() {
        libOn();
        myFixture.configureByText("C4.java", "public class C4 {\n    static int sum(int a, int b) { return a + b; }\n\n    public static void main(String[] args) {\n"
                + "        int s = sum(1,<caret>\n                2);\n        System.out.println(s);\n    }\n}\n");
        String after = insertAtCaret();
        assertTrue(after, after.contains("int s = sum(1,\n                2);\n"));
        assertTrue(after.indexOf("Stack<") > after.indexOf("2);"));
        // שורה ריקה בתוך הפקודה
        myFixture.configureByText("C5.java", "public class C5 {\n    static int sum(int a, int b) { return a + b; }\n\n    public static void main(String[] args) {\n"
                + "        int s = sum(1,\n<caret>\n                2);\n    }\n}\n");
        after = insertAtCaret();
        assertTrue(after.indexOf("Stack<") > after.indexOf("2);"));
    }

    public void testInstanceMethodLambdaAndStaticMethod() {
        libOn();
        myFixture.configureByText("C6.java", "public class C6 {\n    int x;\n\n    void inst() {\n        <caret>\n    }\n}\n");
        insertAtCaret();
        myFixture.configureByText("C7.java", "public class C7 {\n    public static void main(String[] args) {\n        Runnable r = () -> {\n            <caret>\n        };\n        r.run();\n    }\n}\n");
        String after = insertAtCaret();
        int lambda = after.indexOf("() -> {"), close = after.indexOf("};");
        assertTrue("בתוך הלמבדה", after.indexOf("Stack<") > lambda && after.indexOf("Stack<") < close);
        // למבדה בשורה אחת — אחרי השורה
        myFixture.configureByText("C8.java", "import java.util.*;\npublic class C8 {\n    public static void main(String[] args) {\n"
                + "        List<Integer> l = new ArrayList<>();\n        l.forEach(v -> System.out.<caret>println(v));\n    }\n}\n");
        after = insertAtCaret();
        // יש גם import java.util.* — בלי import מפורש Stack היה דו-משמעי (java.util.Stack) והקוד לא היה מתקמפל
        assertTrue(after, after.contains("import unit4.collectionsLib.Stack;"));
        myFixture.configureByText("C11.java", "import java.util.*;\nimport unit4.collectionsLib.*;\n\npublic class C11 {\n    public static void main(String[] args) {\n"
                + "        Scanner in = new Scanner(System.in);\n        <caret>\n    }\n}\n");
        Document doc = myFixture.getEditor().getDocument();
        StructureInserter.Spot spot = StructureInserter.spot(myFixture.getFile(), doc, myFixture.getCaretOffset());
        InsertDialog d = new InsertDialog(getProject(), null, doc.getText(), spot.insertAt, spot.indent);
        try {
            d.choose("queue", "stack");
            int caret = StructureInserter.insert(getProject(), doc, myFixture.getFile(), spot, d.code());
            assertTrue("הסמן אחרי הקוד שנכנס", doc.getText().substring(0, caret).endsWith(d.code()));
        } finally {
            d.close(0);
        }
        assertTrue(doc.getText(), doc.getText().contains("import unit4.collectionsLib.*;\nimport unit4.collectionsLib.Stack;\nimport unit4.collectionsLib.Queue;"));
        myFixture.checkHighlighting(false, false, false);
    }

    public void testCaretOutsideMethodOrInFieldInitializer() {
        PsiFile f = myFixture.configureByText("C9.java", "public class C9 {\n    int x = 1;<caret>\n\n    public static void main(String[] a) { }\n}\n");
        StructureInserter.run(getProject(), myFixture.getEditor(), f);
        Notice.Spec s = seen.last();
        assertEquals(Modern.Kind.WARN, s.kind);
        EdgeSupport.assertStudentNotice(s);
        assertEquals("הקוד לא השתנה", "public class C9 {\n    int x = 1;\n\n    public static void main(String[] a) { }\n}\n", myFixture.getEditor().getDocument().getText());
        // לפני המחלקה / אחריה / בתוך הערה שמחוץ לפעולה
        for (String text : new String[] { "<caret>\npublic class C10 { }\n", "public class C10 { }\n<caret>\n", "public class C10 {\n    /* <caret> */\n    void f() { }\n}\n" }) {
            seen.clear();
            PsiFile g = myFixture.configureByText("C10.java", text);
            StructureInserter.run(getProject(), myFixture.getEditor(), g);
            EdgeSupport.assertStudentNotice(seen.last());
        }
        // לא קובץ Java / אין עורך
        seen.clear();
        StructureInserter.run(getProject(), null, null);
        EdgeSupport.assertStudentNotice(seen.last());
    }

    public void testFileWithSyntaxErrors() {
        libOn();
        myFixture.configureByText("Broken.java", "public class Broken {\n    public static void main(String[] args) {\n        int x = ;\n        <caret>\n        if (x > {\n    }\n");
        Document doc = myFixture.getEditor().getDocument();
        StructureInserter.Spot spot = StructureInserter.spot(myFixture.getFile(), doc, myFixture.getCaretOffset());
        InsertDialog d = new InsertDialog(getProject(), null, doc.getText(), spot.insertAt, spot.indent);
        try {
            d.choose("queue", "");
            assertNotNull("גם בקובץ עם שגיאות — יש קוד להכנסה", d.code());
            StructureInserter.insert(getProject(), doc, myFixture.getFile(), spot, d.code());
        } finally {
            d.close(0);
        }
        assertTrue(doc.getText().contains("Queue<Integer>"));
        assertTrue(doc.getText().startsWith("import unit4.collectionsLib.*;"));
    }

    public void testNamesInInsertDialog() {
        myFixture.configureByText("N.java", "public class N {\n    public static void main(String[] args) {\n        int stk = 1;\n        <caret>\n    }\n}\n");
        Document doc = myFixture.getEditor().getDocument();
        StructureInserter.Spot spot = StructureInserter.spot(myFixture.getFile(), doc, myFixture.getCaretOffset());
        InsertDialog d = new InsertDialog(getProject(), null, doc.getText(), spot.insertAt, spot.indent);
        try {
            d.choose("stack", "list");
            assertEquals("ריק — נכנסת ההצעה", "", d.typeName(""));
            assertTrue(d.isOKActionEnabled());
            assertTrue("מבנה בתוך מבנה", d.code().contains("Stack<Node<Integer>>") || d.code().contains("Stack<"));
            for (String bad : new String[] { "stk", "מחסנית", "class", "int", "1abc", "my stack", "Stack", "a-b" }) {
                String err = d.typeName(bad);
                assertFalse(bad + " — צריך שגיאה", err.isEmpty());
                EdgeSupport.assertStudentText(bad, err);
                assertFalse(bad, d.isOKActionEnabled());
            }
            assertEquals("", d.typeName("myStack"));
            assertTrue(d.code().contains("myStack"));
        } finally {
            d.close(0);
        }
    }

    // ------------------------------------------------------------------ תרגיל חדש

    public void testNewExerciseNames() throws Exception {
        File dir = com.intellij.openapi.util.io.FileUtil.createTempDirectory("dsviz-edge-dsviz-new", null, true);
        Files.write(new File(dir, "Targil1.java").toPath(), "public class Targil1 { }".getBytes(StandardCharsets.UTF_8));
        ExerciseDialog d = new ExerciseDialog(getProject(), Arrays.asList(new ExerciseDialog.Target(dir, "src")), "stack");
        try {
            assertEquals("Targil1 כבר קיים — ההצעה היא הבא", "Targil2", d.options().className);
            for (String bad : new String[] { "Targil1", "", "1abc", "class", "My Class", "שלום", "Targil1.java" }) {
                String err = d.typeClassName(bad);
                assertFalse("\"" + bad + "\" — צריך שגיאה", err.isEmpty());
                EdgeSupport.assertStudentText(bad, err);
                assertNull(bad, d.options());
            }
            assertEquals("", d.typeClassName("MyStackTargil"));
            assertEquals("MyStackTargil", d.options().className);
        } finally {
            d.close(0);
        }
    }

    public void testNewExerciseIntoFolderThatCannotBeCreated() throws Exception {
        File blocker = com.intellij.openapi.util.io.FileUtil.createTempFile("dsviz-edge-not-a-dir", ".txt", true);   // "תיקייה" שהיא בעצם קובץ — אי אפשר ליצור בה כלום (כמו תיקייה לקריאה בלבד)
        ExerciseDialog.Target t = new ExerciseDialog.Target(new File(blocker, "src"), "src");
        ExerciseDialog d = new ExerciseDialog(getProject(), Arrays.asList(t), "queue");
        il.lamerhav.dsviz.Scaffold.Options o;
        try {
            o = d.options();
        } finally {
            d.close(0);
        }
        assertNotNull(o);
        assertNull(Actions.createExercise(getProject(), t, o));
        Notice.Spec s = seen.last();
        assertEquals(Modern.Kind.ERROR, s.kind);
        EdgeSupport.assertStudentNotice(s);
    }

    public void testExamplesTwiceAndHelpPage() throws Exception {
        File src = com.intellij.openapi.util.io.FileUtil.createTempDirectory("dsviz-edge-dsviz-ex", null, true);
        List<com.intellij.openapi.vfs.VirtualFile> a = ProjectFiles.copyExamples(getProject(), src);
        File stack = new File(src, "examples/StackExamples.java");
        Files.write(stack.toPath(), "// שיניתי\n".getBytes(StandardCharsets.UTF_8), java.nio.file.StandardOpenOption.APPEND);
        List<com.intellij.openapi.vfs.VirtualFile> b = ProjectFiles.copyExamples(getProject(), src);
        assertEquals(a.size(), b.size());
        assertTrue("פעם שנייה — לא דורסים מה שהתלמיד שינה", new String(Files.readAllBytes(stack.toPath()), StandardCharsets.UTF_8).endsWith("// שיניתי\n"));
        File page = Help.playerPage("c");
        assertTrue(page.isFile());
        assertTrue(new String(Files.readAllBytes(page.toPath()), StandardCharsets.UTF_8).contains("var id = \"c\" ||"));
    }

    // ------------------------------------------------------------------ כיבוי: הכול מוסתר

    public void testEverythingHiddenWhenOff() throws Exception {
        PsiFile f = myFixture.configureByText("Off.java", "public class Off {\n    public static void main(String[] a) {\n        // ====כאן כותבים את התשובה====\n    }\n}\n");
        ActionManager am = ActionManager.getInstance();
        String[] ids = { "DSViz.Run", "DSViz.NewExercise", "DSViz.Insert", "DSViz.Submit", "DSViz.Examples", "DSViz.Help" };
        String[] places = { ActionPlaces.EDITOR_POPUP, ActionPlaces.MAIN_MENU, ActionPlaces.PROJECT_VIEW_POPUP, ActionPlaces.TOOLWINDOW_CONTENT, ActionPlaces.MAIN_TOOLBAR };
        Dsviz.setOn(false);
        for (String id : ids) {
            for (String place : places) {
                AnActionEvent e = event(am.getAction(id), place, f);
                assertFalse(id + " ב-" + place + " — מוסתר כש-DSViz כבוי", e.getPresentation().isEnabledAndVisible());
            }
        }
        AnActionEvent p = event(am.getAction("DSViz.Power"), ActionPlaces.MAIN_MENU, f);
        assertTrue("הכפתור של ההפעלה נשאר", p.getPresentation().isEnabledAndVisible());
        assertEquals("הפעלת DSViz", p.getPresentation().getText());
        // הכפתור ליד main, המנורה, חלון DSViz, הצהוב, וספריית הקורס
        PsiMethod main = PsiTreeUtil.findChildOfType(f, PsiMethod.class);
        assertNull(new RunLineMarker().getInfo(main.getNameIdentifier()));
        assertNull(new Solutions().getLineMarkerInfo(main.getNameIdentifier()));
        JComponent tw = DsvizToolWindow.build(getProject());
        List<JButton> buttons = UIUtilButtons.all(tw);
        assertEquals("בחלון DSViz — רק \"הפעלת DSViz\"", 1, buttons.size());
        assertEquals("הפעלת DSViz", buttons.get(0).getText());
        LibrarySetup.ensureAll(getProject());
        assertFalse(LibrarySetup.hasLibrary(getModule()));
        AnswerMarks marks = AnswerMarks.get(getProject());
        marks.track(new File(f.getVirtualFile().getParent().getPath()));
        marks.attach(myFixture.getEditor().getDocument());
        assertTrue("כבוי — בלי צהוב", marks.highlighters(myFixture.getEditor().getDocument()).isEmpty());
        // הפעלה מחזירה הכול
        Dsviz.setOn(true);
        marks.refreshAll();
        assertEquals(1, marks.highlighters(myFixture.getEditor().getDocument()).size());
        for (String id : ids) {
            if (id.equals("DSViz.Submit") && !IjShared.workerConfigured()) continue;
            assertTrue(id, event(am.getAction(id), ActionPlaces.MAIN_MENU, f).getPresentation().isEnabledAndVisible());
        }
        Dsviz.setOn(false);
        marks.refreshAll();
        assertTrue("כיבוי אחרי שכבר היה צהוב — נעלם", marks.highlighters(myFixture.getEditor().getDocument()).isEmpty());
    }

    private AnActionEvent event(AnAction a, String place, PsiFile f) {
        AnActionEvent e = AnActionEvent.createFromDataContext(place, null, SimpleDataContext.builder().add(CommonDataKeys.PROJECT, getProject())
                .add(CommonDataKeys.PSI_FILE, f).add(CommonDataKeys.EDITOR, myFixture.getEditor()).add(CommonDataKeys.VIRTUAL_FILE, f.getVirtualFile()).build());
        a.update(e);
        return e;
    }

    static final class UIUtilButtons {
        static List<JButton> all(java.awt.Component c) {
            List<JButton> out = new java.util.ArrayList<JButton>();
            collect(c, out);
            return out;
        }

        private static void collect(java.awt.Component c, List<JButton> out) {
            if (c instanceof JButton) out.add((JButton) c);
            if (c instanceof java.awt.Container) for (java.awt.Component k : ((java.awt.Container) c).getComponents()) collect(k, out);
        }
    }

    // ------------------------------------------------------------------ 💡 הצעה לפתרון

    private PsiElement methodId(PsiFile f, String name) {
        for (PsiIdentifier id : PsiTreeUtil.findChildrenOfType(f, PsiIdentifier.class))
            if (id.getParent() instanceof PsiMethod && id.getText().equals(name)) return id;
        return null;
    }

    public void testSolutionAfterRenameAndWithBrokenStore() throws Exception {
        File store = com.intellij.openapi.util.io.FileUtil.createTempFile("dsviz-edge-dsviz-solutions", ".json", true);
        store.delete();
        System.setProperty("dsviz.solutions", store.getAbsolutePath());
        Solutions s = new Solutions();
        PsiFile f = myFixture.configureByText("Sol.java", "public class Sol {\n    public static int sumEven(int x) { return 0; }\n}\n");
        assertNull("אין solutions.json — בלי מנורה ובלי שגיאה", s.getLineMarkerInfo(methodId(f, "sumEven")));
        IjShared.addSolution(store, f.getVirtualFile().getPath(), "Sol", "sumEven", Arrays.asList("x"), "א", "return x;", "ok");
        assertNotNull(s.getLineMarkerInfo(methodId(f, "sumEven")));
        // התלמיד שינה את שם הפעולה / הוסיף פרמטר — בלי מנורה
        myFixture.renameElement(methodId(f, "sumEven").getParent(), "sumOfEven");
        assertNull(s.getLineMarkerInfo(methodId(f, "sumOfEven")));
        // ...והחזיר — חוזרת
        myFixture.renameElement(methodId(f, "sumOfEven").getParent(), "sumEven");
        assertNotNull(s.getLineMarkerInfo(methodId(f, "sumEven")));
        // התיקייה של התרגיל נמחקה (ההצעות נשארו בחנות) — קובץ אחר לא מקבל אותן, ואין חריגה
        PsiFile other = myFixture.configureByText("Other.java", "public class Other {\n    public static int sumEven(int x) { return 0; }\n}\n");
        assertNull(s.getLineMarkerInfo(methodId(other, "sumEven")));
        // solutions.json פגום — כל מיני צורות
        String deep = "[".repeat(200_000);
        for (String bad : new String[] { "", "{", "not json at all", "{\"solutions\": 5}", "{\"solutions\": [{\"path\": 1, \"code\": {}}]}",
                "{\"solutions\": [null, 3, \"x\"]}", "\u0000\u0001ÿ", deep }) {
            Files.write(store.toPath(), bad.getBytes(StandardCharsets.UTF_8));
            store.setLastModified(System.currentTimeMillis() + bad.length() + 1000);   // שייקרא מחדש
            assertNull("solutions.json פגום (" + bad.length() + " תווים) — בלי מנורה", s.getLineMarkerInfo(methodId(f, "sumEven")));
        }
        // אחרי קובץ פגום — אפשר שוב לשמור הצעות (שליחת תרגיל חדשה)
        IjShared.addSolution(store, f.getVirtualFile().getPath(), "Sol", "sumEven", Arrays.asList("x"), "א", "return x;", "ok");
        assertNotNull(s.getLineMarkerInfo(methodId(f, "sumEven")));
    }

    public void testSolutionDialogWithHebrewAndLongCode() {
        IjShared.Solution sol = solution("א", "// ‫סכום הזוגיים‬\n" + "int sum = 0;\n".repeat(200) + "return sum;", "failed");
        Solutions.SolutionDialog d = new Solutions.SolutionDialog(getProject(), sol);
        try {
            assertTrue(d.getTitle().contains("הצעה לפתרון"));
            assertTrue(Solutions.statusText("failed").startsWith("⚠"));
            EdgeSupport.assertStudentText("סטטוס", Solutions.statusText("weird-status"));
        } finally {
            d.close(0);
        }
    }

    private IjShared.Solution solution(String label, String code, String status) {
        try {
            File store = com.intellij.openapi.util.io.FileUtil.createTempFile("dsviz-edge-dsviz-sol", ".json", true);
            store.delete();
            IjShared.addSolution(store, "/p/src/ex01/T.java", "T", "f", Arrays.asList("x"), label, code, status);
            return IjShared.findSolution(store, "/p/src/ex01/T.java", "T", "f", 1);
        } catch (java.io.IOException e) {
            throw new RuntimeException(e);
        }
    }

    // ------------------------------------------------------------------ המקום הצהוב: עריכה כבדה, מחיקת פעולה, undo / redo

    public void testAnswerMarksSurviveHeavyEditingAndUndo() {
        PsiFile f = myFixture.addFileToProject("ex09_q/Tasks.java", DsvizPluginTest.STUBS.replace("ex01_q", "ex09_q"));
        AnswerMarks marks = AnswerMarks.get(getProject());
        marks.track(new File(f.getVirtualFile().getParent().getPath()));
        myFixture.configureFromExistingVirtualFile(f.getVirtualFile());
        Document doc = myFixture.getEditor().getDocument();
        marks.attach(doc);
        assertEquals(2, marks.highlighters(doc).size());
        // מחיקת הפעולה של סעיף ב
        int b = doc.getText().indexOf("    public static boolean has"), end = doc.getText().lastIndexOf("    }\n}") + 6;
        WriteCommandAction.runWriteCommandAction(getProject(), "מחיקה", null, () -> doc.deleteString(b, end));
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();
        assertEquals("נשאר רק סעיף א", 1, marks.highlighters(doc).size());
        // undo — סעיף ב חוזר וגם הצהוב שלו
        com.intellij.openapi.fileEditor.FileEditor fe = com.intellij.openapi.fileEditor.impl.text.TextEditorProvider.getInstance().getTextEditor(myFixture.getEditor());
        com.intellij.openapi.command.undo.UndoManager um = com.intellij.openapi.command.undo.UndoManager.getInstance(getProject());
        assertTrue(um.isUndoAvailable(fe));
        um.undo(fe);
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();
        assertTrue(doc.getText().contains("כאן עונים סעיף ב"));
        assertEquals(2, marks.highlighters(doc).size());
        um.redo(fe);
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();
        assertEquals(1, marks.highlighters(doc).size());
        // עריכה כבדה: כל הקובץ מוחלף ב-3000 שורות (הדבקה), ואז מוחזר — הסימון תמיד בתוך המסמך ונכון
        String big = DsvizPluginTest.STUBS.replace("ex01_q", "ex09_q") + "// x\n".repeat(3000);
        long t = System.currentTimeMillis();
        for (int i = 0; i < 30; i++) {
            String text = i % 2 == 0 ? big : "class Empty {}\n";
            WriteCommandAction.runWriteCommandAction(getProject(), () -> doc.setText(text));
        }
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();
        assertTrue("30 החלפות של כל הקובץ — מהר (" + (System.currentTimeMillis() - t) + "ms)", System.currentTimeMillis() - t < 10_000);
        assertTrue(marks.highlighters(doc).isEmpty());
        WriteCommandAction.runWriteCommandAction(getProject(), () -> doc.setText(big));
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();
        List<RangeHighlighter> hs = marks.highlighters(doc);
        assertEquals(2, hs.size());
        for (RangeHighlighter h : hs) assertTrue(h.isValid() && h.getEndOffset() <= doc.getTextLength());
        // הקלדה תו-תו בתוך הסימון — הצהוב נעלם כשיש קוד
        int at = doc.getText().indexOf("        \n    }") + 8;
        for (char c : "return 1;".toCharArray()) {
            int pos = at++;
            WriteCommandAction.runWriteCommandAction(getProject(), () -> doc.insertString(pos, String.valueOf(c)));
        }
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();
        assertEquals(1, marks.highlighters(doc).size());
    }

    // ------------------------------------------------------------------ ספריית הקורס: lib לקריאה בלבד, גרסה אחרת, jar של unit4 משלהם

    public void testLibraryCopyInOddProjectFolders() throws Exception {
        File src = Dsviz.libraryJar();
        // רגיל
        File p1 = com.intellij.openapi.util.io.FileUtil.createTempDirectory("dsviz-edge-dsviz-p1", null, true);
        File c1 = LibrarySetup.copyJar(p1, src);
        assertEquals(new File(p1, "lib/dsviz.jar"), c1);
        assertTrue(Arrays.equals(Files.readAllBytes(src.toPath()), Files.readAllBytes(c1.toPath())));
        // dsviz.jar ישן (גרסה אחרת) — מתעדכן
        Files.write(c1.toPath(), "old version".getBytes(StandardCharsets.UTF_8));
        assertEquals(c1, LibrarySetup.copyJar(p1, src));
        assertEquals(src.length(), c1.length());
        // אותה גרסה — לא נכתב שוב
        c1.setLastModified(1_000_000_000_000L);
        LibrarySetup.copyJar(p1, src);
        assertEquals(1_000_000_000_000L, c1.lastModified());
        // lib הוא קובץ (אי אפשר ליצור בו כלום — כמו תיקייה לקריאה בלבד) — ה-jar של התוסף, בלי חריגה
        File p2 = com.intellij.openapi.util.io.FileUtil.createTempDirectory("dsviz-edge-dsviz-p2", null, true);
        Files.write(new File(p2, "lib").toPath(), new byte[] { 1 });
        assertEquals(src, LibrarySetup.copyJar(p2, src));
        // lib/dsviz.jar הוא תיקייה עם קבצים
        File p3 = com.intellij.openapi.util.io.FileUtil.createTempDirectory("dsviz-edge-dsviz-p3", null, true);
        new File(p3, "lib/dsviz.jar/x").mkdirs();
        assertEquals(src, LibrarySetup.copyJar(p3, src));
    }

    public void testStudentsOwnUnit4Jar() throws Exception {
        File own = com.intellij.openapi.util.io.FileUtil.createTempFile("dsviz-edge-unit4-", ".jar", true);
        Files.copy(Dsviz.libraryJar().toPath(), own.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        com.intellij.openapi.vfs.VirtualFile jar = com.intellij.openapi.vfs.LocalFileSystem.getInstance().refreshAndFindFileByIoFile(own);
        ModuleRootModificationUtil.addModuleLibrary(getModule(), "unit4", Arrays.asList("jar://" + jar.getPath() + "!/"), java.util.Collections.<String>emptyList());
        com.intellij.testFramework.IndexingTestUtil.waitUntilIndexesAreReady(getProject());
        assertFalse("יש להם unit4 משלהם — לא מוסיפים עוד ספרייה", LibrarySetup.needs(getModule()));
        List<String> cp = VisualRunner.classpath(getModule());
        assertEquals("בהרצה הוויזואלית — הספרייה שלנו קודם (LineByLine)", Dsviz.libraryJar().getAbsolutePath(), cp.get(0));
        assertTrue("ושלהם אחריה", cp.contains(own.getAbsolutePath()));
    }

    public void testModuleInfoProjectIsLeftAlone() {
        myFixture.addFileToProject("module-info.java", "module student.app { }");
        com.intellij.testFramework.IndexingTestUtil.waitUntilIndexesAreReady(getProject());
        assertFalse("module-info.java — לא מוסיפים ספרייה (היא תשבור את המודול)", LibrarySetup.needs(getModule()));
    }
}
