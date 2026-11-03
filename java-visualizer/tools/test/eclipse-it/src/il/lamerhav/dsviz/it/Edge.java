package il.lamerhav.dsviz.it;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.PrintStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectDescription;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.ILogListener;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.eclipse.core.runtime.preferences.InstanceScope;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.ILaunch;
import org.eclipse.debug.core.model.IProcess;
import org.eclipse.equinox.app.IApplication;
import org.eclipse.equinox.app.IApplicationContext;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jface.text.IDocument;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.ImageLoader;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;
import org.eclipse.swt.widgets.ToolBar;
import org.eclipse.swt.widgets.ToolItem;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.application.WorkbenchAdvisor;
import org.eclipse.ui.handlers.IHandlerService;
import org.eclipse.ui.ide.IDE;
import org.eclipse.ui.texteditor.ITextEditor;

/**
 * מקרי קצה של תלמידים בתוך Eclipse אמיתי: כל מה שתלמיד בתיכון יכול לעשות (קוד בלי main, שגיאות, חריגות בזמן ריצה, לולאה
 * אינסופית, רקורסיה בלי סוף, קלט, עברית, שמות מתנגשים, פרויקט בלי JDK, לחיצה כפולה, סגירת החלון באמצע...) — ובכל מקרה:
 * בלי חלון שגיאה של Eclipse, בלי שגיאה ב-Error Log, בלי תקיעה (מגבלת זמן), הודעה קצרה בעברית מימין לשמאל, ותוצאה נכונה.
 *   java -Ddsviz.worker=http://127.0.0.1:PORT -jar launcher.jar -application il.lamerhav.dsviz.it.edge <out.txt> <shots-dir> <attach-dir>
 * כל שורה בפלט: "ok  <מקרה> <תיאור>" או "FAIL <מקרה> <תיאור> (מה קרה)". ההרצות הוויזואליות — בלי חלון (headless, דרך
 * ‎-Ddsviz.it.vmargs‎) כדי שאפשר לקרוא את הצעדים מהקונסול; ההרצות עם חלון (לחיצה כפולה, סגירה באמצע, צילומים) — על ה-Xvfb.
 */
public class Edge implements IApplication {
    private static final String HEADLESS = "-Djava.awt.headless=true";
    private PrintStream out;
    private Display display;
    private File shots, attach;
    private final List<String> logged = Collections.synchronizedList(new ArrayList<String>());
    private final java.util.Map<IProcess, StringBuffer> outputs = new java.util.concurrent.ConcurrentHashMap<IProcess, StringBuffer>();
    private int oks, fails;
    private IJavaProject edge;
    private IContainer src;

    @Override
    public Object start(IApplicationContext context) throws Exception {
        final String[] a = (String[]) context.getArguments().get(IApplicationContext.APPLICATION_ARGS);
        out = new PrintStream(new File(a[0]), "UTF-8");
        shots = new File(a[1]);
        shots.mkdirs();
        attach = new File(a[2]);
        IEclipsePreferences p = InstanceScope.INSTANCE.getNode("il.lamerhav.dsviz");
        p.putBoolean("setupDone", true);
        p.flush();
        // כל שגיאה שנרשמת ב-Error Log (גם חריגה בפקודה שלנו שהגיעה ל-Eclipse) — נספרת במקרה שבו קרתה
        Platform.addLogListener(new ILogListener() {
            @Override
            public void logging(IStatus s, String plugin) {
                if (s.getSeverity() < IStatus.ERROR) return;
                if (String.valueOf(s.getMessage()).contains("no underlying browser")) return;   // אין WebKit ב-Xvfb (חלון של Eclipse, לא שלנו)
                String t = s.getMessage() + (s.getException() == null ? "" : " " + s.getException());
                StringBuilder trace = new StringBuilder();
                if (s.getException() != null) for (StackTraceElement e : s.getException().getStackTrace()) trace.append(e).append(' ');
                // תקלה של Eclipse עצמו בכל עריכה של מסמך ב-Xvfb (Undo של חלון בלי הקשר) — לא שלנו
                if (trace.indexOf("OperationHistoryActionHandler$HistoryListener") >= 0) return;
                // רק מה שקשור לתוסף (לא לבדיקה עצמה — il.lamerhav.dsviz.it, למשל doc.set כשמחזירים קובץ למקור)
                if (t.contains("il.lamerhav") || java.util.regex.Pattern.compile("il\\.lamerhav\\.dsviz\\.(?!it\\.)").matcher(trace).find()
                        || "il.lamerhav.dsviz".equals(plugin))
                    logged.add(plugin + ": " + t + " @ " + trace.substring(0, Math.min(400, trace.length())));
            }
        });
        // הפלט של כל תהליך — מהרגע שנוצר (הקונסול של Eclipse לא שומר את כל הפלט ב-getContents)
        DebugPlugin.getDefault().addDebugEventListener(events -> {
            for (org.eclipse.debug.core.DebugEvent ev : events) {
                if (ev.getKind() != org.eclipse.debug.core.DebugEvent.CREATE || !(ev.getSource() instanceof IProcess)) continue;
                IProcess pr = (IProcess) ev.getSource();
                StringBuffer b = new StringBuffer();
                outputs.put(pr, b);
                if (pr.getStreamsProxy() == null) continue;
                org.eclipse.debug.core.IStreamListener l = (text, mon) -> b.append(text);
                b.append(pr.getStreamsProxy().getOutputStreamMonitor().getContents()).append(pr.getStreamsProxy().getErrorStreamMonitor().getContents());
                pr.getStreamsProxy().getOutputStreamMonitor().addListener(l);
                pr.getStreamsProxy().getErrorStreamMonitor().addListener(l);
            }
        });
        display = PlatformUI.createDisplay();
        PlatformUI.createAndRunWorkbench(display, new WorkbenchAdvisor() {
            @Override
            public String getInitialWindowPerspectiveId() {
                return "org.eclipse.jdt.ui.JavaPerspective";
            }

            @Override
            public void postStartup() {
                Thread t = new Thread(() -> {
                    try {
                        sleep(2500);
                        run();
                    } catch (Throwable e) {
                        e.printStackTrace(out);
                        fail("CRASH", "הבדיקה עצמה נפלה", String.valueOf(e));
                    }
                    out.println("SUMMARY " + oks + " ok / " + fails + " FAIL");
                    out.println("DONE");
                    out.close();
                    display.asyncExec(() -> PlatformUI.getWorkbench().close());
                }, "edge-it");
                t.setDaemon(true);
                t.start();
            }
        });
        return IApplication.EXIT_OK;
    }

    private void run() throws Exception {
        String only = System.getProperty("edge.only", "");
        ui(() -> {
            IWorkbenchPage page = page();
            page.getWorkbenchWindow().getShell().setBounds(0, 0, 1400, 900);
            org.eclipse.ui.intro.IIntroPart intro = PlatformUI.getWorkbench().getIntroManager().getIntro();
            if (intro != null) PlatformUI.getWorkbench().getIntroManager().closeIntro(intro);
            ResourcesPlugin.getWorkspace().getDescription();
            edge = il.lamerhav.dsviz.ProjectSetup.createProject("Edge");
            Class<?> resolver = Class.forName("il.lamerhav.dsviz.ProjectSetup$ConflictResolver");
            src = (IContainer) il.lamerhav.dsviz.ProjectSetup.class.getMethod("prepare", IJavaProject.class, resolver).invoke(null, edge, null);
            edge.setOption(JavaCore.COMPILER_COMPLIANCE, "17");
            edge.setOption(JavaCore.COMPILER_SOURCE, "17");
            edge.setOption(JavaCore.COMPILER_CODEGEN_TARGET_PLATFORM, "17");
            page.getWorkbenchWindow().getShell().forceActive();
            return null;
        });
        sleep(1000);
        if (only.isEmpty() || only.contains("A")) visualRuns();
        if (only.isEmpty() || only.contains("C")) insertCases();
        if (only.isEmpty() || only.contains("D")) dialogsAndPower();
        if (only.isEmpty() || only.contains("F")) mentorCases();
        if (only.isEmpty() || only.contains("B")) submitCases();
        if (only.isEmpty() || only.contains("E")) solutionCases();
        if (only.isEmpty() || only.contains("G")) guiRuns();
        if (only.isEmpty() || only.contains("M")) wizardModuleRuns();
    }

    // ================================================================== A. הרצה ויזואלית

    private void visualRuns() throws Exception {
        System.setProperty("dsviz.it.vmargs", HEADLESS);
        String imp = "import unit4.collectionsLib.*;\n";

        // --- מבנה הקבצים: בלי main, שתי מחלקות עם main, main בקובץ אחר, מחלקה בלי public
        IFile f = file("NoMain.java", "public class NoMain {\n    int x;\n    public int twice() { return x * 2; }\n}\n");
        R r = visual(f, 20);
        expectSay("A-nomain", "בלי main ובלי מחלקה שמשתמשת בה: מציעים להוסיף main (שאלה בעברית)", r, "QUESTION", "אין main");
        f = file("TwoMains.java", "public class TwoMains {\n    public static void main(String[] args) {\n        System.out.println(\"ראשון\");\n    }\n}\n"
                + "class Other {\n    public static void main(String[] args) {\n        System.out.println(\"שני\");\n    }\n}\n");
        r = visual(f, 60);
        expectRun("A-twomains", "שתי מחלקות עם main בקובץ: רצה המחלקה של הקובץ", r, "ראשון");
        file("Helper.java", "public class Helper {\n    public static int add(int a, int b) { return a + b; }\n}\n");
        file("UsesHelper.java", "public class UsesHelper {\n    public static void main(String[] args) {\n        System.out.println(\"סכום \" + Helper.add(2, 3));\n    }\n}\n");
        r = visual(src.getFile(new Path("Helper.java")), 60);
        expectRun("A-mainelsewhere", "main בקובץ אחר: מריצים את המחלקה שמשתמשת בקובץ", r, "סכום 5");
        f = file("NoPub.java", "class NoPub {\n    public static void main(String[] args) {\n        System.out.println(\"בלי public\");\n    }\n}\n");
        expectRun("A-nopublic", "מחלקה בלי public", visual(f, 60), "בלי public");

        // --- שגיאות קומפילציה; עורך שלא נשמר
        f = file("Broken.java", "public class Broken {\n    public static void main(String[] args) {\n        int x = 5\n        System.out.println(x);\n    }\n}\n");
        expectSay("A-compile-here", "שגיאה בקובץ הזה: לא מריצים, הודעה בעברית", visual(f, 30), "ERROR", "שגיאה");
        file("OtherBroken.java", "public class OtherBroken {\n    void f() { int y = \"x\"; }\n}\n");
        f = file("Fine.java", "public class Fine {\n    public static void main(String[] args) {\n        System.out.println(\"תקין\");\n    }\n}\n");
        expectRun("A-compile-other", "שגיאה רק בקובץ אחר (לא בשימוש): מריצים", visual(f, 60), "תקין");
        f = file("Dirty.java", "public class Dirty {\n    public static void main(String[] args) {\n        System.out.println(\"ישן\");\n    }\n}\n");
        final IFile df = f;
        ui(() -> {
            ITextEditor ed = (ITextEditor) IDE.openEditor(page(), df, true);
            IDocument doc = ed.getDocumentProvider().getDocument(ed.getEditorInput());
            doc.set(doc.get().replace("ישן", "חדש מהעורך"));
            return null;
        });
        r = visual(f, 60);
        expectRun("A-dirty", "עורך שלא נשמר: נשמר לפני ההרצה, ורץ הקוד החדש", r, "חדש מהעורך");
        check("A-dirty-saved", "העורך נשמר (בלי כוכבית)", !ui(() -> page().getActiveEditor().isDirty()), "dirty");

        // --- שגיאות בזמן ריצה: הכרטיס בעברית, והמחסנית בקונסול — רק השורות של התלמיד
        runtime("A-npe", "NullPointerException", imp + "public class Npe {\n    public static void main(String[] args) {\n        Node<Integer> p = null;\n"
                + "        System.out.println(p.getValue());\n    }\n}\n", "null");
        runtime("A-pop", "pop על מחסנית ריקה", imp + "public class PopEmpty {\n    public static void main(String[] args) {\n        Stack<Integer> s = new Stack<Integer>();\n"
                + "        s.pop();\n    }\n}\n", "מחסנית ריקה");
        runtime("A-remove", "remove על תור ריק", imp + "public class QEmpty {\n    public static void main(String[] args) {\n        Queue<Integer> q = new Queue<Integer>();\n"
                + "        q.remove();\n    }\n}\n", "תור ריק");
        runtime("A-aioobe", "ArrayIndexOutOfBounds", "public class Aioobe {\n    public static void main(String[] args) {\n        int[] a = {1, 2, 3};\n"
                + "        for (int i = 0; i <= a.length; i++) System.out.println(a[i]);\n    }\n}\n", "אין תא כזה");
        runtime("A-cce", "ClassCastException", "public class Cce {\n    public static void main(String[] args) {\n        Object o = \"שלום\";\n"
                + "        Integer n = (Integer) o;\n        System.out.println(n);\n    }\n}\n", "אי אפשר להפוך");
        runtime("A-div0", "חלוקה ב-0", "public class Div0 {\n    public static void main(String[] args) {\n        int a = 5, b = 0;\n"
                + "        System.out.println(a / b);\n    }\n}\n", "חלוקה ב-0");
        runtime("A-custom", "חריגה של התלמיד שלא נתפסה", "public class Custom {\n    static class MyErr extends RuntimeException {\n        MyErr(String m) { super(m); }\n    }\n"
                + "    public static void main(String[] args) {\n        throw new MyErr(\"משהו השתבש\");\n    }\n}\n", "משהו השתבש");

        // --- לולאה אינסופית, רקורסיה בלי סוף, לולאה ארוכה, מבנה ענק
        f = file("InfLoop.java", "public class InfLoop {\n    public static void main(String[] args) {\n        int i = 0;\n        while (i < 10) {\n"
                + "            System.out.println(i);\n        }\n    }\n}\n");
        r = visual(f, 90);
        check("A-infloop", "לולאה אינסופית: נעצרת עם כרטיס בעברית", r.ended && r.out.contains("הלולאה לא נגמרת"), r.brief());
        f = file("InfRec.java", "public class InfRec {\n    static int f(int n) {\n        return f(n + 1) + 1;\n    }\n"
                + "    public static void main(String[] args) {\n        System.out.println(f(1));\n    }\n}\n");
        r = visual(f, 120);
        check("A-infrec", "רקורסיה בלי סוף: StackOverflowError עם הסבר בעברית (" + r.secs() + " שניות)",
                r.ended && r.out.contains("StackOverflowError") && r.out.contains("הרקורסיה לא נעצרת"), r.brief());
        f = file("LongLoop.java", "public class LongLoop {\n    public static void main(String[] args) {\n        long sum = 0;\n"
                + "        for (int i = 0; i < 1000000; i++) {\n            sum += i;\n        }\n        System.out.println(\"סכום \" + sum);\n    }\n}\n");
        r = visual(f, 120);
        check("A-longloop", "לולאה של מיליון סיבובים: מגבלת הצעדים, והתוצאה נכונה (" + r.secs() + " שניות)",
                r.ended && r.out.contains("סכום 499999500000") && r.out.contains("התיעוד נעצר"), r.brief());
        f = file("Huge.java", imp + "public class Huge {\n    public static void main(String[] args) {\n        Stack<Integer> s = new Stack<Integer>();\n"
                + "        for (int i = 0; i < 10000; i++) s.push(i);\n        Queue<Integer> q = new Queue<Integer>();\n"
                + "        for (int i = 0; i < 10000; i++) q.insert(i);\n        System.out.println(\"ראש \" + s.top() + \" \" + q.head());\n    }\n}\n");
        r = visual(f, 200);
        check("A-huge", "מחסנית ותור של 10,000 איברים: מסתיים, התוצאה נכונה (" + r.secs() + " שניות)", r.ended && r.out.contains("ראש 9999 0"), r.brief());
        f = file("ListRec.java", imp + "public class ListRec {\n    static int sum(Node<Integer> p) {\n        if (p == null) return 0;\n"
                + "        return p.getValue() + sum(p.getNext());\n    }\n    public static void main(String[] args) {\n        Node<Integer> h = null;\n"
                + "        for (int i = 1; i <= 30; i++) h = new Node<Integer>(i, h);\n        System.out.println(\"סכום \" + sum(h));\n    }\n}\n");
        r = visual(f, 90);
        check("A-listrec", "רקורסיה על רשימה של 30 חוליות (" + r.secs() + " שניות, לפני התיקון: כ-20)", r.ended && r.out.contains("סכום 465") && r.ms < 25000, r.brief());

        // --- System.exit, Thread.sleep, חוטים
        f = file("Exit.java", "public class Exit {\n    public static void main(String[] args) {\n        System.out.println(\"לפני\");\n"
                + "        System.exit(0);\n        System.out.println(\"אחרי\");\n    }\n}\n");
        r = visual(f, 60);
        check("A-exit", "System.exit(0) באמצע: התוכנית נגמרת בשקט", r.ended && r.out.contains("לפני") && !r.out.contains("אחרי") && !r.out.contains("Exception"), r.brief());
        f = file("Sleep.java", "public class Sleep {\n    public static void main(String[] args) throws Exception {\n        System.out.println(\"לפני\");\n"
                + "        Thread.sleep(1500);\n        System.out.println(\"אחרי השינה\");\n    }\n}\n");
        expectRun("A-sleep", "Thread.sleep", visual(f, 60), "אחרי השינה");
        f = file("Threads.java", "public class Threads {\n    public static void main(String[] args) throws Exception {\n"
                + "        Thread t = new Thread(() -> { for (int i = 0; i < 3; i++) System.out.println(\"חוט \" + i); });\n"
                + "        t.start();\n        t.join();\n        System.out.println(\"סוף\");\n    }\n}\n");
        expectRun("A-threads", "חוט נוסף", visual(f, 60), "חוט 2");

        // --- קלט: סוג לא נכון, סוף הקלט; קובץ שלא קיים
        String scan = "import java.util.Scanner;\npublic class Scan {\n    public static void main(String[] args) {\n        Scanner in = new Scanner(System.in);\n"
                + "        System.out.println(\"הכנס מספר\");\n        int n = in.nextInt();\n        System.out.println(\"קיבלתי \" + n);\n    }\n}\n";
        f = file("Scan.java", scan);
        r = visual(f, 60, "abc\n", false);
        check("A-scan-type", "Scanner: הוקלדו אותיות במקום מספר — כרטיס בעברית", r.ended && r.out.contains("הוקלד ערך מסוג אחר"), r.brief());
        r = visual(f, 60, "", true);
        check("A-scan-eof", "Scanner: אין קלט (סוף הקלט) — כרטיס בעברית", r.ended && r.out.contains("אין עוד קלט"), r.brief());
        r = visual(f, 60, "42\n", false);
        expectRun("A-scan-ok", "Scanner: קלט תקין", r, "קיבלתי 42");
        runtime("A-filenf", "קריאה מקובץ שלא קיים", "import java.io.*;\nimport java.util.Scanner;\npublic class FileNF {\n"
                + "    public static void main(String[] args) throws Exception {\n        Scanner s = new Scanner(new File(\"nope.txt\"));\n        System.out.println(s.nextLine());\n    }\n}\n",
                "הקובץ לא נמצא");

        // --- עברית, אמוג'י, שורות ארוכות, טאבים, CRLF, קובץ ריק
        f = file("Heb.java", imp + "public class Heb {\n    public static void main(String[] args) {\n        int מספר = 5;\n        String ש = \"שלום 😀 עולם\";\n"
                + "        Stack<String> מחסנית = new Stack<String>();\n        מחסנית.push(ש);\n        System.out.println(מספר + \" \" + מחסנית.top());\n    }\n}\n");
        expectRun("A-hebrew", "משתנים בעברית ואמוג'י במחרוזת", visual(f, 60), "5 שלום 😀 עולם");
        StringBuilder longLine = new StringBuilder();
        for (int i = 0; i < 400; i++) longLine.append("אבג");
        f = file("LongLine.java", "public class LongLine {\n    public static void main(String[] args) {\n        String s = \"" + longLine + "\"; int x = 1"
                + String.join("", Collections.nCopies(300, " + 1")) + ";\n        System.out.println(\"אורך \" + s.length() + \" \" + x);\n    }\n}\n");
        expectRun("A-longline", "שורה ארוכה מאוד", visual(f, 60), "אורך 1200 301");
        f = fileBytes("Crlf.java", "public class Crlf {\r\n\tpublic static void main(String[] args) {\r\n\t\tint x = 1;\r\n  \t  x++;\r\n\t\tSystem.out.println(\"שורות CRLF \" + x);\r\n\t}\r\n}\r\n");
        expectRun("A-crlf-tabs", "CRLF וטאבים", visual(f, 60), "שורות CRLF 2");
        f = fileBytes("Empty.java", "");
        r = visual(f, 20);
        check("A-empty", "קובץ ריק: הודעה בעברית, בלי חלון שגיאה", r.say != null && r.sayRtl && hebrew(r.sayText) && r.foreign.isEmpty() && r.newLog.isEmpty(), r.brief());

        // --- חבילה, נתיב עם רווחים ועברית, שם קובץ בעברית
        IFolder pkg = (IFolder) src.getFolder(new Path("school"));
        if (!pkg.exists()) pkg.create(true, true, null);
        f = createIn(pkg, "InPkg.java", "package school;\n\npublic class InPkg {\n    public static void main(String[] args) {\n        System.out.println(\"בתוך חבילה\");\n    }\n}\n");
        expectRun("A-package", "מחלקה בתוך package", visual(f, 60), "בתוך חבילה");
        IJavaProject heb = ui(() -> il.lamerhav.dsviz.ProjectSetup.createProject("תרגילים של דני"));
        IContainer hsrc = ui(() -> (IContainer) il.lamerhav.dsviz.ProjectSetup.class.getMethod("prepare", IJavaProject.class,
                Class.forName("il.lamerhav.dsviz.ProjectSetup$ConflictResolver")).invoke(null, heb, null));
        f = createIn(hsrc, "תרגיל.java", "public class תרגיל {\n    public static void main(String[] args) {\n        System.out.println(\"שם בעברית\");\n    }\n}\n");
        expectRun("A-hebrew-names", "פרויקט עם רווחים ועברית בשם, ומחלקה בשם עברי", visual(f, 60), "שם בעברית");
        System.setProperty("dsviz.it.jnu", "windows-1252");   // כמו Windows באנגלית
        r = visual(f, 20);
        System.clearProperty("dsviz.it.jnu");
        check("A-hebrew-names-win", "שם בעברית ב-Windows שלא מוגדר לעברית: הודעה בעברית (במקום Could not find or load main class ???)",
                r.say != null && r.sayRtl && r.sayText.contains("לאנגלית") && r.launches == 0, r.brief());

        // --- מחלקה של התלמיד בשם Stack / Node, java.util.Stack, בלי import
        IFolder own = (IFolder) src.getFolder(new Path("own"));
        if (!own.exists()) own.create(true, true, null);
        createIn(own, "Stack.java", "package own;\n\npublic class Stack<T> {\n    private java.util.ArrayList<T> a = new java.util.ArrayList<T>();\n"
                + "    public void push(T x) { a.add(x); }\n    public T pop() { return a.remove(a.size() - 1); }\n    public boolean isEmpty() { return a.isEmpty(); }\n}\n");
        createIn(own, "Node.java", "package own;\n\npublic class Node {\n    int v;\n    Node next;\n    Node(int v) { this.v = v; }\n}\n");
        f = createIn(own, "UseOwn.java", "package own;\n\nimport unit4.collectionsLib.*;\n\npublic class UseOwn {\n    public static void main(String[] args) {\n"
                + "        Stack<Integer> s = new Stack<Integer>();\n        s.push(7);\n        Node n = new Node(3);\n        System.out.println(\"שלי \" + s.pop() + \" \" + n.v);\n    }\n}\n");
        expectRun("A-own-stack", "מחלקה של התלמיד בשם Stack / Node (מתנגשת בספרייה)", visual(f, 60), "שלי 7 3");
        f = file("JuStack.java", "import java.util.Stack;\n\npublic class JuStack {\n    public static void main(String[] args) {\n"
                + "        Stack<Integer> s = new Stack<Integer>();\n        s.push(1);\n        s.push(2);\n        System.out.println(\"java.util \" + s.pop());\n    }\n}\n");
        expectRun("A-java-util-stack", "java.util.Stack במקום unit4", visual(f, 60), "java.util 2");
        f = file("NoImport.java", "public class NoImport {\n    public static void main(String[] args) {\n        Stack<Integer> s = new Stack<Integer>();\n"
                + "        s.push(1);\n    }\n}\n");
        r = visual(f, 30);
        check("A-no-import", "בלי import unit4: לא רץ, הודעה בעברית" + (r.launches > 0 ? " (או import אוטומטי)" : ""),
                r.say != null ? "ERROR".equals(r.sayKind) && r.sayRtl && hebrew(r.sayText) && !r.sayText.matches(".*[A-Za-z]{4,} [a-z]{3,} [a-z]{3,}.*") : r.ended, r.brief());

        // --- Java מודרנית, מחלקות פנימיות, ממשקים, enum, record, var, switch, static, רקורסיה על עץ
        f = file("Modern.java", "import java.util.*;\n\npublic class Modern {\n    interface Shape { double area(); }\n    record Pt(int x, int y) {}\n"
                + "    enum Color { RED, GREEN }\n    static <T extends Comparable<T>> T max(List<T> l) { T m = l.get(0); for (T t : l) if (t.compareTo(m) > 0) m = t; return m; }\n"
                + "    public static void main(String[] args) {\n        var list = new ArrayList<Integer>(List.of(3, 1, 2));\n"
                + "        int s = list.stream().filter(x -> x > 1).mapToInt(x -> x).sum();\n        Shape sq = new Shape() { public double area() { return 4; } };\n"
                + "        Pt p = new Pt(1, 2);\n        Color c = Color.GREEN;\n        String w = switch (c) { case RED -> \"אדום\"; case GREEN -> \"ירוק\"; };\n"
                + "        Runnable r = () -> System.out.println(\"למבדה\");\n        r.run();\n        System.out.println(\"מודרני \" + s + \" \" + sq.area() + \" \" + p.x() + \" \" + w + \" \" + max(list));\n    }\n}\n");
        r = visual(f, 60);
        expectRun("A-modern", "lambda / stream / record / enum / var / switch / מחלקה אנונימית", r, "מודרני 5 4.0 1 ירוק 3");
        check("A-lambda-frames", "צעדים בתוך lambda: בלי \"שורה -1\" ובלי $$Lambda", r.ended && !r.out.contains("$$Lambda") && !r.out.contains("שורה -1"), r.brief());
        f = file("Inner.java", imp + "public class Inner {\n    class In { int v = 3; }\n    static class Box<T> { T val; Box(T v) { val = v; } }\n"
                + "    static int count;\n    static int size(BinNode<Integer> t) { if (t == null) return 0; count++; return 1 + size(t.getLeft()) + size(t.getRight()); }\n"
                + "    public static void main(String[] args) {\n        In i = new Inner().new In();\n        Box<Box<String>> b = new Box<>(new Box<>(\"x\"));\n"
                + "        Stack<Box<String>> st = new Stack<>();\n        st.push(b.val);\n"
                + "        BinNode<Integer> t = new BinNode<Integer>(new BinNode<Integer>(1), 2, new BinNode<Integer>(3));\n"
                + "        System.out.println(\"פנימי \" + i.v + st.top().val + \" \" + size(t) + \" \" + count);\n    }\n}\n");
        expectRun("A-inner-generics-tree", "מחלקה פנימית, generics של מחלקות התלמיד, static ורקורסיה על עץ", visual(f, 60), "פנימי 3x 3 3");

        // --- פרויקטים מוזרים: בלי JDK, עם module-info.java, מבנה של Maven
        noJdkProject();
        moduleProject();
        mavenProject();
        System.clearProperty("dsviz.it.vmargs");
    }

    /** שגיאה בזמן ריצה: רץ, נגמר, ההסבר בעברית בכרטיס, ובקונסול רק השורות של התלמיד */
    private void runtime(String id, String what, String code, String hebrewCard) throws Exception {
        String cls = code.replaceFirst("(?s).*public class (\\w+).*", "$1");
        R r = visual(file(cls + ".java", code), 60);
        check(id, what + ": כרטיס בעברית, בלי מחסנית של הספרייה בקונסול",
                r.ended && r.out.contains("שגיאה:") && r.out.contains(hebrewCard) && !r.out.contains("at unit4.collectionsLib") && !r.out.contains("reflect"), r.brief());
    }

    private void noJdkProject() throws Exception {
        IJavaProject jp = ui(() -> {
            IProject p = ResourcesPlugin.getWorkspace().getRoot().getProject("NoJdk");
            if (!p.exists()) p.create(null);
            p.open(null);
            IProjectDescription d = p.getDescription();
            d.setNatureIds(new String[] { JavaCore.NATURE_ID });
            p.setDescription(d, null);
            IFolder s = p.getFolder("src");
            if (!s.exists()) s.create(true, true, null);
            IJavaProject j = JavaCore.create(p);
            j.setRawClasspath(new IClasspathEntry[] { JavaCore.newSourceEntry(s.getFullPath()) }, p.getFolder("bin").getFullPath(), null);
            return j;
        });
        IFile f = createIn(jp.getProject().getFolder("src"), "Nj.java", "public class Nj {\n    public static void main(String[] args) {\n        System.out.println(\"בלי JDK\");\n    }\n}\n");
        R r = visual(f, 40);
        check("A-nojdk", "פרויקט בלי JDK: הודעה בעברית (או ריצה), בלי חלון שגיאה",
                r.foreign.isEmpty() && r.newLog.isEmpty() && (r.say != null ? r.sayRtl && hebrew(r.sayText) && !englishSentence(r.sayText) : r.ended && r.out.contains("בלי JDK")), r.brief());
    }

    private void moduleProject() throws Exception {
        IJavaProject jp = ui(() -> il.lamerhav.dsviz.ProjectSetup.createProject("WithModule"));
        IContainer s = ui(() -> (IContainer) il.lamerhav.dsviz.ProjectSetup.class.getMethod("prepare", IJavaProject.class,
                Class.forName("il.lamerhav.dsviz.ProjectSetup$ConflictResolver")).invoke(null, jp, null));
        ui(() -> {
            jp.setOption(JavaCore.COMPILER_COMPLIANCE, "17");
            jp.setOption(JavaCore.COMPILER_SOURCE, "17");
            jp.setOption(JavaCore.COMPILER_CODEGEN_TARGET_PLATFORM, "17");
            return null;
        });
        createIn(s, "module-info.java", "module withmodule {\n}\n");
        IFolder app = s instanceof IFolder ? ((IFolder) s).getFolder("app") : ((IProject) s).getFolder("app");
        if (!app.exists()) app.create(true, true, null);
        IFile f = createIn(app, "ModMain.java", "package app;\n\nimport unit4.collectionsLib.*;\n\npublic class ModMain {\n    public static void main(String[] args) {\n"
                + "        Stack<Integer> st = new Stack<Integer>();\n        st.push(4);\n        System.out.println(\"מודול \" + st.top());\n    }\n}\n");
        R r = visual(f, 60);
        check("A-module-info", "פרויקט עם module-info.java: רץ, או הודעה בעברית — בלי חלון שגיאה",
                r.foreign.isEmpty() && r.newLog.isEmpty() && (r.say != null ? r.sayRtl && hebrew(r.sayText) : r.ended && r.out.contains("מודול 4")), r.brief());
    }

    /**
     * פרויקט חדש כמו שהאשף של Eclipse יוצר ("Create module-info.java" מסומן כברירת מחדל): module-info.java בתיקיית src,
     * שם המודול כשם הפרויקט, והמחלקה בחבילה באותו שם — "הרצה ויזואלית" חייבת לרוץ, לא ליפול ב-FindException: Module stam not found
     */
    private void wizardModuleRuns() throws Exception {
        System.setProperty("dsviz.it.vmargs", HEADLESS);
        IJavaProject jp = ui(() -> {
            IProject p = ResourcesPlugin.getWorkspace().getRoot().getProject("stam");
            if (!p.exists()) p.create(null);
            p.open(null);
            IProjectDescription d = p.getDescription();
            d.setNatureIds(new String[] { JavaCore.NATURE_ID });
            p.setDescription(d, null);
            IFolder s = p.getFolder("src");
            if (!s.exists()) s.create(true, true, null);
            IJavaProject jj = JavaCore.create(p);
            jj.setRawClasspath(new IClasspathEntry[] { JavaCore.newSourceEntry(s.getFullPath()),
                    JavaCore.newContainerEntry(new Path("org.eclipse.jdt.launching.JRE_CONTAINER")) }, p.getFolder("bin").getFullPath(), null);
            return jj;
        });
        IFolder src = jp.getProject().getFolder("src");
        createIn(src, "module-info.java", "module stam {\n}\n");
        IFolder pkg = src.getFolder("stam");
        if (!pkg.exists()) pkg.create(true, true, null);
        IFile f = createIn(pkg, "Main.java", "package stam;\n\npublic class Main {\n\n    public static void main(String[] args) {\n        // TODO Auto-generated method stub\n\n"
                + "        String str = \"Moti Levi\";\n        for (int i=0;i<str.length(); i++)\n            System.out.println(str.charAt(i));\n    }\n\n}\n");
        R r = visual(f, 90);
        check("M-wizard-module", "פרויקט חדש עם module-info.java (כמו שהאשף יוצר): ההרצה הוויזואלית רצה עד הסוף, בלי FindException",
                r.say == null && r.ended && r.out.replace("\r", "").replace("\n", "").contains("Moti Levi") && !r.out.contains("FindException"), r.brief());
        check("M-wizard-module-clean", "בלי חלון שגיאה ובלי שגיאה ב-Error Log", r.foreign.isEmpty() && r.newLog.isEmpty() && !r.out.contains("Module stam not found"), r.brief());
        // הצעד הבא של תלמיד: להשתמש בספריית הקורס (Stack / Queue) באותו פרויקט מודולרי
        IFile g = createIn(pkg, "UsesLib.java", "package stam;\n\nimport unit4.collectionsLib.*;\n\npublic class UsesLib {\n    public static void main(String[] args) {\n"
                + "        Stack<Integer> st = new Stack<Integer>();\n        st.push(7);\n        st.push(9);\n        System.out.println(\"מחסנית \" + st.pop() + st.pop());\n    }\n}\n");
        R r2 = visual(g, 90);
        check("M-wizard-module-lib", "פרויקט מודולרי + ספריית הקורס (Stack): רץ ויזואלית, או הודעה קצרה בעברית — לא שגיאה באנגלית",
                r2.foreign.isEmpty() && r2.newLog.isEmpty() && (r2.say != null ? r2.sayRtl && hebrew(r2.sayText) : r2.ended && r2.out.replace("\r", "").replace("\n", "").contains("מחסנית 97")), r2.brief());
        System.clearProperty("dsviz.it.vmargs");
    }

    private void mavenProject() throws Exception {
        IJavaProject jp = ui(() -> {
            IProject p = ResourcesPlugin.getWorkspace().getRoot().getProject("MavenStyle");
            if (!p.exists()) p.create(null);
            p.open(null);
            IProjectDescription d = p.getDescription();
            d.setNatureIds(new String[] { JavaCore.NATURE_ID });
            p.setDescription(d, null);
            IFolder s = p.getFolder("src"), m = s.getFolder("main"), j = m.getFolder("java");
            for (IFolder x : new IFolder[] { s, m, j }) if (!x.exists()) x.create(true, true, null);
            IJavaProject jj = JavaCore.create(p);
            jj.setRawClasspath(new IClasspathEntry[] { JavaCore.newSourceEntry(j.getFullPath()),
                    JavaCore.newContainerEntry(new Path("org.eclipse.jdt.launching.JRE_CONTAINER")) }, p.getFolder("target/classes").getFullPath(), null);
            return jj;
        });
        IFile f = createIn(jp.getProject().getFolder("src/main/java"), "MvnMain.java", "import unit4.collectionsLib.*;\n\npublic class MvnMain {\n"
                + "    public static void main(String[] args) {\n        Queue<Integer> q = new Queue<Integer>();\n        q.insert(8);\n        System.out.println(\"מייבן \" + q.head());\n    }\n}\n");
        expectRun("A-maven", "מבנה תיקיות של Maven (src/main/java): הספרייה נוספת והקוד רץ", visual(f, 60), "מייבן 8");
    }

    // ------------------------------------------------------------------ ההרצה עצמה

    /** מה קרה אחרי לחיצה על "הרצה ויזואלית" */
    private static final class R {
        String say, sayKind, sayText = "", sayTitle = "";
        boolean sayRtl;
        int launches;
        boolean ended;
        String out = "";
        long ms;
        List<String> foreign = new ArrayList<String>(), newLog = new ArrayList<String>();

        String secs() {
            return String.valueOf(Math.round(ms / 100.0) / 10.0);
        }

        String brief() {
            String o = out.replaceAll("Picked up JAVA_TOOL_OPTIONS[^\n]*\n", "").replace('\n', '|');
            if (o.length() > 600) o = o.substring(0, 250) + " … " + o.substring(o.length() - 330);
            return "say=" + sayKind + (say == null ? "" : " rtl=" + sayRtl + " [" + sayTitle + "] " + sayText.replace('\n', ' ')) + " launches=" + launches + " ended=" + ended
                    + " " + secs() + "s foreign=" + foreign + " log=" + newLog + " out=" + o;
        }
    }

    private R visual(IFile f, int timeoutSec) throws Exception {
        return visual(f, timeoutSec, null, false);
    }

    /** פותח את הקובץ, לוחץ "הרצה ויזואלית", ומחכה: חלון הודעה שלנו, או הרצה שנגמרת. input — מה להקליד; eof — לסגור את הקלט */
    private R visual(IFile f, int timeoutSec, String input, boolean eof) throws Exception {
        R r = new R();
        int logBefore = logged.size();
        ui(() -> {
            IEditorPart ed = IDE.openEditor(page(), f, true);
            page().activate(ed);
            return null;
        });
        sleep(300);
        Set<ILaunch> before = new HashSet<ILaunch>(Arrays.asList(DebugPlugin.getDefault().getLaunchManager().getLaunches()));
        long start = System.currentTimeMillis();
        command("il.lamerhav.dsviz.run");
        ILaunch launch = null;
        long end = start + timeoutSec * 1000L;
        boolean typed = false;
        while (System.currentTimeMillis() < end) {
            Shell s = ui(() -> findShell(x -> x.getData("dsviz.say") != null));
            if (s != null) {
                sleep(300);
                sayInto(r, s);
                ui(() -> { closeSay(s); return null; });
                break;
            }
            List<String> fs = foreignShells();
            if (!fs.isEmpty()) {
                r.foreign.addAll(fs);
                break;
            }
            for (ILaunch l : DebugPlugin.getDefault().getLaunchManager().getLaunches()) if (!before.contains(l)) launch = l;
            if (launch != null && launch.getProcesses().length > 0) {
                IProcess p = launch.getProcesses()[0];
                if (!typed && (input != null || eof)) {
                    sleep(2500);   // התוכנית הגיעה לקריאה
                    if (input != null && !input.isEmpty()) p.getStreamsProxy().write(input);
                    if (eof && p.getStreamsProxy() instanceof org.eclipse.debug.core.model.IStreamsProxy2) ((org.eclipse.debug.core.model.IStreamsProxy2) p.getStreamsProxy()).closeInputStream();
                    typed = true;
                }
                if (p.isTerminated()) {
                    r.ended = true;
                    break;
                }
            }
            sleep(200);
        }
        r.ms = System.currentTimeMillis() - start;
        for (ILaunch l : DebugPlugin.getDefault().getLaunchManager().getLaunches()) if (!before.contains(l)) r.launches++;
        if (launch != null && launch.getProcesses().length > 0) {
            IProcess p = launch.getProcesses()[0];
            sleep(300);
            StringBuffer b = outputs.get(p);
            r.out = b != null ? b.toString() : p.getStreamsProxy().getOutputStreamMonitor().getContents() + p.getStreamsProxy().getErrorStreamMonitor().getContents();
            if (!p.isTerminated()) launch.terminate();
        }
        r.foreign.addAll(foreignShells());
        closeForeign();
        sleep(200);
        synchronized (logged) {
            r.newLog.addAll(logged.subList(logBefore, logged.size()));
        }
        return r;
    }

    private void expectRun(String id, String desc, R r, String output) {
        check(id, desc, r.ended && r.out.contains(output) && r.foreign.isEmpty() && r.newLog.isEmpty() && !r.out.contains("Exception in thread"), r.brief());
    }

    private void expectSay(String id, String desc, R r, String kind, String text) {
        check(id, desc, r.say != null && kind.equals(r.sayKind) && r.sayRtl && r.sayText.contains(text) && r.launches == 0 && r.foreign.isEmpty() && r.newLog.isEmpty(), r.brief());
    }

    private void sayInto(R r, Shell s) throws Exception {
        r.say = "yes";
        r.sayKind = ui(() -> String.valueOf(s.getData("dsviz.say")));
        r.sayRtl = ui(() -> s.getOrientation() == SWT.RIGHT_TO_LEFT);
        r.sayTitle = ui(() -> s.getText());
        r.sayText = ui(() -> labels(s));
    }

    private void closeSay(Shell s) {
        if (s == null || s.isDisposed()) return;
        Control sec = find(s, c -> "secondary".equals(c.getData("dsviz.say")));
        if (sec != null) click(sec);
        if (!s.isDisposed()) s.close();
    }

    // ================================================================== G. הרצה עם חלון: לחיצה כפולה, עצירה, סגירת החלון, צילומים

    private void guiRuns() throws Exception {
        System.clearProperty("dsviz.it.vmargs");
        String imp = "import unit4.collectionsLib.*;\n";
        IFile slow = file("SlowShow.java", imp + "public class SlowShow {\n    public static void main(String[] args) throws Exception {\n"
                + "        Stack<Integer> s = new Stack<Integer>();\n        for (int i = 0; i < 200; i++) {\n            s.push(i);\n            Thread.sleep(300);\n        }\n    }\n}\n");
        // לחיצה כפולה מהירה
        ui(() -> { page().activate(IDE.openEditor(page(), slow, true)); return null; });
        sleep(300);
        Set<ILaunch> before = new HashSet<ILaunch>(Arrays.asList(DebugPlugin.getDefault().getLaunchManager().getLaunches()));
        command("il.lamerhav.dsviz.run");
        command("il.lamerhav.dsviz.run");
        sleep(6000);
        List<ILaunch> mine = new ArrayList<ILaunch>();
        for (ILaunch l : DebugPlugin.getDefault().getLaunchManager().getLaunches()) if (!before.contains(l)) mine.add(l);
        check("G-doubleclick", "לחיצה כפולה מהירה על \"הרצה ויזואלית\": חלון אחד", mine.size() == 1, "launches=" + mine.size());
        sleep(4000);
        shotScreen("viewer-running.png");
        // עצירה מ-Eclipse (הכפתור האדום בקונסול): גם התהליך של התוכנית (עם החלון) נסגר
        long child = childPid("SlowShow");
        for (ILaunch l : mine) l.terminate();
        boolean gone = waitFor(() -> childPid("SlowShow") < 0, 8000);
        check("G-terminate", "עצירה מהקונסול של Eclipse: גם תהליך התוכנית והחלון שלה נסגרים", child > 0 && gone, "child=" + child + " stillAlive=" + (childPid("SlowShow") > 0));
        killChild("SlowShow");
        // סגירת החלון באמצע ההרצה: התהליכים נגמרים, Eclipse בלי שגיאה
        before = new HashSet<ILaunch>(Arrays.asList(DebugPlugin.getDefault().getLaunchManager().getLaunches()));
        int logBefore = logged.size();
        sleep(2200);
        command("il.lamerhav.dsviz.run");
        waitFor(() -> childPid("SlowShow") > 0, 20000);
        sleep(5000);
        long pid = childPid("SlowShow");
        String win = sh("xdotool", "search", "--pid", String.valueOf(pid)).trim();
        for (String w : win.split("\\s+")) if (!w.isEmpty()) sh("xdotool", "windowkill", w);   // כמו סגירת החלון (בלי מנהל חלונות ב-Xvfb אין כפתור X)
        boolean closed = waitFor(() -> childPid("SlowShow") < 0, 8000);
        List<ILaunch> l2 = new ArrayList<ILaunch>();
        for (ILaunch l : DebugPlugin.getDefault().getLaunchManager().getLaunches()) if (!before.contains(l)) l2.add(l);
        boolean launcherDone = waitFor(() -> { for (ILaunch l : l2) if (!l.isTerminated()) return false; return true; }, 8000);
        check("G-closeviewer", "סגירת חלון ההרצה באמצע: התוכנית והתהליך של Eclipse נגמרים, בלי שגיאה",
                pid > 0 && !win.isEmpty() && closed && launcherDone && logged.size() == logBefore, "pid=" + pid + " win=" + win + " closed=" + closed + " launcher=" + launcherDone);
        for (ILaunch l : l2) if (!l.isTerminated()) l.terminate();
        killChild("SlowShow");
        // צילומים של כרטיסי שגיאה בחלון ההרצה
        guiShot("Npe", "viewer-npe.png");
        guiShot("InfLoop", "viewer-infloop.png");
    }

    private void guiShot(String cls, String name) throws Exception {
        IFile f = src.getFile(new Path(cls + ".java"));
        if (!f.exists()) return;
        sleep(2200);
        ui(() -> { page().activate(IDE.openEditor(page(), f, true)); return null; });
        command("il.lamerhav.dsviz.run");
        waitFor(() -> childPid(cls) > 0, 20000);
        sleep(cls.equals("InfLoop") ? 14000 : 7000);
        shotScreen(name);
        killChild(cls);
        for (ILaunch l : DebugPlugin.getDefault().getLaunchManager().getLaunches()) if (!l.isTerminated()) l.terminate();
    }

    /** התהליך של התוכנית (הילד של LineByLine, עם ‎-Ddsviz.child=true‎) שמריץ את המחלקה, או -1 */
    private static long childPid(String cls) {
        return ProcessHandle.allProcesses().filter(p -> p.info().commandLine().map(c -> c.contains("dsviz.child=true") && c.endsWith(" " + cls)).orElse(false))
                .mapToLong(ProcessHandle::pid).findFirst().orElse(-1);
    }

    private static void killChild(String cls) {
        ProcessHandle.allProcesses().filter(p -> p.info().commandLine().map(c -> c.contains("LineByLine") && c.endsWith(" " + cls)).orElse(false))
                .forEach(ProcessHandle::destroyForcibly);
    }

    // ================================================================== C. הוסף מבנה נתונים

    private void insertCases() throws Exception {
        String code = "import unit4.collectionsLib.*;\n\npublic class Ins {\n    static int counter;\n\n    public static void main(String[] args) {\n"
                + "        Stack<Queue<Integer>> nested = new Stack<Queue<Integer>>();\n        // הערה בתוך main\n        /* הערה\n           של כמה שורות */\n"
                + "        String s = \"טקסט בתוך מחרוזת\";\n        Runnable r = () -> {\n            int inLambda = 1;\n        };\n        int existing = 0;\n    }\n\n"
                + "    void instanceMethod() {\n        int a = 1;\n    }\n}\n";
        IFile f = file("Ins.java", code);
        insert("C-outside", "הסמן מחוץ לפעולה (בגוף המחלקה): אזהרה בעברית", f, "static int counter;", null, "WARN");
        insert("C-comment", "הסמן בשורת הערה בתוך main: הקוד נכנס אחריה ומתקמפל", f, "// הערה בתוך main", "", null);
        insert("C-blockcomment", "הסמן בתוך הערה של כמה שורות: הקוד לא נכנס לתוך ההערה", f, "/* הערה", "", null);
        insert("C-string", "הסמן בתוך מחרוזת: הקוד נכנס אחרי השורה ומתקמפל", f, "טקסט בתוך", "", null);
        insert("C-lambda", "הסמן בתוך lambda", f, "int inLambda = 1;", "", null);
        insert("C-instance", "הסמן בפעולה לא סטטית", f, "int a = 1;", "", null);
        insert("C-conflict", "שם שכבר קיים (existing): הודעה בעברית והכפתור כבוי", f, "int existing = 0;", "existing", null);
        insert("C-hebrew-name", "שם בעברית: הודעה בעברית", f, "int existing = 0;", "מחסנית", null);
        insert("C-reserved", "מילה שמורה (class): הודעה בעברית", f, "int existing = 0;", "class", null);
        insert("C-empty-name", "שם ריק: משתמשים בשם המוצע", f, "int existing = 0;", "", null);
        IFile bad = file("InsBroken.java", "public class InsBroken {\n    public static void main(String[] args) {\n        int x = \n        if (x > 3 {\n    }\n}\n");
        insert("C-syntax", "קובץ עם שגיאות תחביר: החלון נפתח בלי חריגה", bad, "if (x > 3 {", "", "ANY");
    }

    /**
     * מניח את הסמן בשורה שמכילה at, לוחץ "הוסף מבנה נתונים". expectSay — סוג ההודעה שצריכה לקפוץ במקום החלון (או null);
     * name — מה להקליד בשדה השם (null = לא לגעת). אחרי "הוספה לקוד" — בודקים שהקובץ מתקמפל (או שלא נוספו שגיאות)
     */
    private void insert(String id, String desc, IFile f, String at, String name, String expectSay) throws Exception {
        int logBefore = logged.size();
        String original = new String(Files.readAllBytes(f.getLocation().toFile().toPath()), StandardCharsets.UTF_8);
        ui(() -> {
            ITextEditor ed = (ITextEditor) IDE.openEditor(page(), f, true);
            page().activate(ed);
            IDocument doc = ed.getDocumentProvider().getDocument(ed.getEditorInput());
            doc.set(original);
            int off = doc.get().indexOf(at) + Math.min(3, at.length());
            ed.selectAndReveal(off, 0);
            return null;
        });
        sleep(300);
        command("il.lamerhav.dsviz.insert");
        Shell s = waitShell(x -> x.getData("dsviz.say") != null || "InsertDialog".equals(x.getData() == null ? "" : x.getData().getClass().getSimpleName()), 10000);
        if (s == null) {
            fail(id, desc, "לא נפתח חלון. foreign=" + foreignShells());
            closeForeign();
            return;
        }
        boolean isSay = ui(() -> s.getData("dsviz.say") != null);
        if (expectSay != null && !"ANY".equals(expectSay)) {
            String kind = ui(() -> String.valueOf(s.getData("dsviz.say")));
            String text = ui(() -> labels(s));
            boolean rtl = ui(() -> s.getOrientation() == SWT.RIGHT_TO_LEFT);
            ui(() -> { closeSay(s); return null; });
            check(id, desc, isSay && expectSay.equals(kind) && rtl && hebrew(text), "kind=" + kind + " text=" + text);
            return;
        }
        if (isSay) {
            String text = ui(() -> labels(s));
            ui(() -> { closeSay(s); return null; });
            check(id, desc, "ANY".equals(expectSay) && hebrew(text), "say: " + text);
            return;
        }
        Object d = ui(() -> s.getData());
        if (name != null && !name.isEmpty()) {
            ui(() -> {
                Object nf = field(d, "nameField");
                ((Text) field(nf, "text")).setText(name);
                return null;
            });
            sleep(300);
        }
        String err = ui(() -> ((Label) field(d, "errorLabel")).getText());
        boolean okOn = ui(() -> ((Control) field(d, "ok")).getEnabled());
        if (name != null && !name.isEmpty()) {   // שם לא תקין — הודעה בעברית והכפתור כבוי
            ui(() -> { s.close(); return null; });
            check(id, desc, !okOn && hebrew(err) && !englishSentence(err), "ok=" + okOn + " err=" + err);
            return;
        }
        if (!okOn) {
            ui(() -> { s.close(); return null; });
            check(id, desc, "ANY".equals(expectSay), "הכפתור כבוי: " + err);
            return;
        }
        ui(() -> {
            Method m = d.getClass().getSuperclass().getDeclaredMethod("okPressed");
            m.setAccessible(true);
            m.invoke(d);
            return null;
        });
        sleep(800);
        Shell info = ui(() -> findShell(x -> x.isVisible() && x != page().getWorkbenchWindow().getShell()));
        if (info != null) ui(() -> { info.close(); return null; });
        String after = ui(() -> {
            ITextEditor ed = (ITextEditor) page().getActiveEditor();
            return ed.getDocumentProvider().getDocument(ed.getEditorInput()).get();
        });
        boolean changed = !after.equals(original);
        List<String> errs = new ArrayList<String>();
        if (!"ANY".equals(expectSay)) {
            ui(() -> { ((ITextEditor) page().getActiveEditor()).doSave(null); return null; });
            sleep(300);
            ui(() -> { il.lamerhav.dsviz.RunVisualHandler.compileErrors(f, errs); return null; });
        }
        String inserted = after.length() > original.length() ? diff(original, after) : "";
        boolean inComment = false;
        if (id.equals("C-blockcomment")) {
            int c0 = after.indexOf("/* הערה"), c1 = after.indexOf("*/", c0);
            int ins = after.indexOf("new ", c0);
            inComment = ins > c0 && ins < c1;
        }
        // מחזירים את הקובץ למקור
        ui(() -> {
            ITextEditor ed = (ITextEditor) page().getActiveEditor();
            ed.getDocumentProvider().getDocument(ed.getEditorInput()).set(original);
            ed.doSave(null);
            return null;
        });
        List<String> fs = foreignShells();
        check(id, desc, changed && errs.isEmpty() && !inComment && logged.size() == logBefore && fs.isEmpty(),
                "changed=" + changed + " inComment=" + inComment + " errors=" + errs + " log=" + logged.subList(logBefore, logged.size()) + " foreign=" + fs
                + " inserted=" + inserted.replace('\n', '|'));
        closeForeign();
    }

    private static String diff(String a, String b) {
        int i = 0;
        while (i < a.length() && a.charAt(i) == b.charAt(i)) i++;
        int j = 0;
        while (j < a.length() - i && a.charAt(a.length() - 1 - j) == b.charAt(b.length() - 1 - j)) j++;
        return b.substring(i, b.length() - j);
    }

    // ================================================================== D. תרגיל חדש, דוגמאות, הדרכה, כיבוי

    private void dialogsAndPower() throws Exception {
        // תרגיל חדש: שמות לא חוקיים, קובץ שכבר קיים
        file("Fine.java", "public class Fine {\n    public static void main(String[] args) {\n        System.out.println(\"תקין\");\n    }\n}\n");
        ui(() -> { page().activate(IDE.openEditor(page(), src.getFile(new Path("Fine.java")), true)); return null; });
        command("il.lamerhav.dsviz.newExercise");
        Shell d = waitShell(x -> x.getData() != null && x.getData().getClass().getSimpleName().equals("ExerciseDialog"), 10000);
        if (d == null) fail("D-newex", "החלון \"תרגיל חדש\" לא נפתח", String.valueOf(foreignShells()));
        else {
            Object dlg = ui(() -> d.getData());
            ui(() -> {   // הפרויקט Edge
                org.eclipse.swt.widgets.Combo c = (org.eclipse.swt.widgets.Combo) field(dlg, "projectCombo");
                for (int i = 0; i < c.getItems().length; i++) if (c.getItem(i).startsWith("Edge")) { c.select(i); c.notifyListeners(SWT.Selection, new Event()); }
                return null;
            });
            for (String[] t : new String[][] { { "D-newex-digit", "1Targil" }, { "D-newex-reserved", "class" }, { "D-newex-space", "My Targil" },
                    { "D-newex-hebrew", "תרגיל" }, { "D-newex-exists", "Fine" }, { "D-newex-empty", "" } }) {
                ui(() -> {   // כמו הקלדה של התלמיד (classEdited — השם לא מוחלף בשם המוצע)
                    Field ce = dlg.getClass().getDeclaredField("classEdited");
                    ce.setAccessible(true);
                    ce.setBoolean(dlg, true);
                    ((Text) field(dlg, "classText")).setText(t[1]);
                    return null;
                });
                sleep(250);
                String err = ui(() -> ((Label) field(dlg, "errorLabel")).getText());
                boolean ok = ui(() -> ((Control) field(dlg, "ok")).getEnabled());
                boolean hebName = t[1].equals("תרגיל");
                check(t[0], "שם מחלקה \"" + t[1] + "\": " + (hebName ? "מותר, או הודעה בעברית" : "הודעה בעברית והכפתור כבוי"),
                        hebName ? ok || hebrew(err) && !englishSentence(err) : !ok && hebrew(err) && !englishSentence(err), "ok=" + ok + " err=" + err
                        + " class=" + ui(() -> ((Text) field(dlg, "classText")).getText()) + " project=" + ui(() -> {
                            Object jp = invoke(dlg, "selectedProject");
                            return jp == null ? "null" : jp + " src=" + il.lamerhav.dsviz.ProjectSetup.existingSource((IJavaProject) jp);
                        }));
            }
            ui(() -> { d.close(); return null; });
        }
        // דוגמאות פתורות — פעמיים (בפעם השנייה הקבצים כבר קיימים)
        for (int k = 1; k <= 2; k++) {
            int logBefore = logged.size();
            command("il.lamerhav.dsviz.examples");
            Shell ex = waitShell(x -> x.getData() != null && x.getData().getClass().getSimpleName().equals("ExamplesDialog")
                    || x.getData("dsviz.say") != null, 10000);
            boolean ok = ex != null && logged.size() == logBefore && foreignShells().isEmpty();
            if (ex != null) ui(() -> { ex.close(); return null; });
            check("D-examples-" + k, "דוגמאות פתורות (פעם " + k + "): החלון נפתח בלי שגיאה", ok && src.getFolder(new Path("examples")).exists(), "shell=" + ex + " foreign=" + foreignShells());
            closeForeign();
        }
        // הדרכה (בבנייה הזו אולי בלי סרטונים)
        int logBefore = logged.size();
        command("il.lamerhav.dsviz.help");
        Shell help = waitShell(x -> x.isVisible() && x != page().getWorkbenchWindow().getShell(), 10000);
        String helpInfo = help == null ? "none" : ui(() -> help.getText() + " rtl=" + (help.getOrientation() == SWT.RIGHT_TO_LEFT) + " " + labels(help));
        if (help != null) ui(() -> { help.close(); return null; });
        check("D-help", "הדרכה: נפתח חלון בלי שגיאה", help != null && logged.size() == logBefore, helpInfo);
        closeForeign();

        // כיבוי: כל הכפתורים והתפריטים נעלמים; פקודה שמגיעה בכל זאת (קיצור מקשים) — לא עושה כלום; הפעלה — הכול חוזר
        String onBar = ui(this::ourToolItems);
        ui(() -> { il.lamerhav.dsviz.PowerHandler.apply(false); return null; });
        sleep(1500);
        String offBar = ui(this::ourToolItems);
        String offMenu = ui(this::ourMenuItems);
        check("D-power-off-toolbar", "כיבוי: הכפתורים של DSViz נעלמים מסרגל הכלים (חוץ מ\"הפעלה\")", !onBar.isEmpty() && offBar.replace("הפעלה", "").trim().isEmpty(),
                "on=[" + onBar + "] off=[" + offBar + "]");
        check("D-power-off-menu", "כיבוי: בתפריט נשאר רק \"הפעלת DSViz\"", offMenu.replace("הפעלת DSViz...", "").trim().isEmpty(), "menu=[" + offMenu + "]");
        System.setProperty("dsviz.it.vmargs", HEADLESS);
        R r = visual(src.getFile(new Path("Fine.java")), 8);
        check("D-power-off-run", "כיבוי: \"הרצה ויזואלית\" מקיצור מקשים לא מריצה כלום (או הודעה בעברית)",
                r.launches == 0 && r.foreign.isEmpty() && r.newLog.isEmpty() && (r.say == null || hebrew(r.sayText)), r.brief());
        boolean prefOff = !il.lamerhav.dsviz.Power.on();
        check("D-power-persist", "כיבוי נשמר בהעדפות (נשאר כבוי אחרי הפעלה מחדש של Eclipse)", prefOff, "Power.on=" + il.lamerhav.dsviz.Power.on());
        ui(() -> { il.lamerhav.dsviz.PowerHandler.apply(true); return null; });
        sleep(1500);
        String againBar = ui(this::ourToolItems);
        check("D-power-on", "הפעלה: הכפתורים חוזרים", againBar.equals(onBar), "again=[" + againBar + "]");
        System.clearProperty("dsviz.it.vmargs");
    }

    /** הכפתורים הגלויים שלנו בסרגל הכלים (לפי הטקסט) */
    private String ourToolItems() {
        Set<String> names = new java.util.TreeSet<String>();
        for (Control c : all(page().getWorkbenchWindow().getShell())) {
            if (!(c instanceof ToolBar) || !c.isVisible()) continue;
            for (ToolItem t : ((ToolBar) c).getItems()) {
                String s = (t.getText() + " " + (t.getToolTipText() == null ? "" : t.getToolTipText())).trim();
                if (s.matches(".*(תרגיל חדש|שליחת תרגיל|הוסף מבנה|הרצה ויזואלית|הדרכה|כיבוי|הפעלה).*")) names.add(t.getText().isEmpty() ? s : t.getText());
            }
        }
        return String.join(",", names);
    }

    private String ourMenuItems() {
        org.eclipse.swt.widgets.Menu bar = page().getWorkbenchWindow().getShell().getMenuBar();
        for (org.eclipse.swt.widgets.MenuItem top : bar.getItems()) {
            if (!top.getText().contains("מבני נתונים") && !top.getText().contains("DSViz")) continue;
            org.eclipse.swt.widgets.Menu m = top.getMenu();
            m.notifyListeners(SWT.Show, new Event());
            List<String> items = new ArrayList<String>();
            for (org.eclipse.swt.widgets.MenuItem it : m.getItems()) if (!it.getText().isEmpty()) items.add(it.getText().replace("&", ""));
            m.notifyListeners(SWT.Hide, new Event());
            return String.join(",", items);
        }
        return "";
    }

    // ================================================================== F. המורה בעברית

    private void mentorCases() throws Exception {
        String[][] cases = {
                { "F-unclosed-comment", "public class Uc {\n    public static void main(String[] args) {\n        int x = 1;\n        /* הערה שלא נסגרת\n    }\n}\n" },
                { "F-unclosed-string", "public class Us {\n    public static void main(String[] args) {\n        String s = \"לא נסגרת;\n        System.out.println(s);\n    }\n}\n" },
                { "F-tabs-spaces", "public class Ts {\n\tpublic static void main(String[] args) {\n  \t  int x = 1;\n\t    if (x > 0)\n  \t\tx++;\n    \tSystem.out.println(x)\n\t}\n}\n" } };
        for (String[] c : cases) {
            String cls = c[1].replaceFirst("(?s)public class (\\w+).*", "$1");
            IFile f = file(cls + ".java", c[1]);
            int logBefore = logged.size();
            List<String> msgs = reconcile(f, null);
            boolean heb = false;
            for (String m : msgs) heb |= hebrew(m);
            check(c[0], "המורה בעברית: " + c[0].substring(2) + " — בלי חריגה, יש הסבר בעברית", heb && logged.size() == logBefore, msgs.toString());
        }
        // קובץ של 5,000 שורות: reconcile בפחות משנייה
        StringBuilder b = new StringBuilder("import unit4.collectionsLib.*;\n\npublic class Big {\n");
        for (int i = 0; i < 1000; i++) b.append("    static int f").append(i).append("(Stack<Integer> s) {\n        int c = 0;\n        while (!s.isEmpty()) { c += s.pop(); }\n        return c;\n    }\n");
        b.append("}\n");
        IFile big = file("Big.java", b.toString());
        reconcile(big, null);   // חימום
        long[] t = new long[1];
        List<String> msgs = reconcile(big, t);
        check("F-5000-lines", "קובץ של 5,000 שורות: reconcile עם המורה בעברית לוקח " + t[0] + "ms (פחות משנייה)", t[0] < 1000, msgs.size() + " messages");
    }

    /** reconcile של עותק עבודה (כמו עורך פתוח) — ההודעות שהתקבלו */
    private List<String> reconcile(IFile f, long[] took) throws Exception {
        final List<String> got = new ArrayList<String>();
        org.eclipse.jdt.core.IProblemRequestor req = new org.eclipse.jdt.core.IProblemRequestor() {
            public void acceptProblem(org.eclipse.jdt.core.compiler.IProblem p) { got.add(il.lamerhav.dsviz.RtlText.strip(p.getMessage())); }
            public void beginReporting() { got.clear(); }
            public void endReporting() {}
            public boolean isActive() { return true; }
        };
        org.eclipse.jdt.core.WorkingCopyOwner owner = new org.eclipse.jdt.core.WorkingCopyOwner() {
            @Override
            public org.eclipse.jdt.core.IProblemRequestor getProblemRequestor(ICompilationUnit workingCopy) { return req; }
        };
        ICompilationUnit wc = JavaCore.createCompilationUnitFrom(f).getWorkingCopy(owner, null);
        try {
            wc.getBuffer().setContents(wc.getSource() + " ");
            long s = System.nanoTime();
            wc.reconcile(il.lamerhav.dsviz.MentorParticipant.latest(), true, owner, null);
            if (took != null) took[0] = (System.nanoTime() - s) / 1000000;
        } finally {
            wc.discardWorkingCopy();
        }
        return got;
    }

    // ================================================================== B. שליחת תרגיל

    private void submitCases() throws Exception {
        File tmp = Files.createTempDirectory("edge-files").toFile();
        File doc = write(tmp, "targil.doc", "word".getBytes()), zip = write(tmp, "targil.zip", new byte[] { 'P', 'K', 3, 4, 1, 2 }),
                heic = write(tmp, "photo.heic", new byte[] { 0, 0, 0, 24, 'f', 't', 'y', 'p' }), zero = write(tmp, "empty.png", new byte[0]),
                badPdf = write(tmp, "broken.pdf", "%PDF-1.4\n garbage garbage".getBytes()), bigPdf = new File(tmp, "big.pdf");
        byte[] big = new byte[11 * 1024 * 1024];
        big[0] = '%'; big[1] = 'P'; big[2] = 'D'; big[3] = 'F';
        Files.write(bigPdf.toPath(), big);
        File photo = new File(attach, "photo.jpg");
        subProject();

        Shell dlg = openSubmit();
        status("B-nofiles", "בלי קבצים: \"שליחה\" — הודעה בעברית", dlg, Collections.<File>emptyList(), true, "עוד לא צירפתם");
        status("B-doc", "קובץ ‎.doc‎: הודעה בעברית", dlg, Arrays.asList(doc), false, "DOCX");
        status("B-zip", "קובץ ‎.zip‎: הודעה בעברית", dlg, Arrays.asList(zip), false, "לא תמונה");
        status("B-heic", "תמונת HEIC: הודעה בעברית", dlg, Arrays.asList(heic), false, "HEIC");
        status("B-zero", "קובץ של 0 בתים: הודעה בעברית", dlg, Arrays.asList(zero), false, "לא תמונה");
        status("B-bigpdf", "PDF גדול מ-10MB: הודעה בעברית", dlg, Arrays.asList(bigPdf), false, "גדול מדי");
        List<File> eleven = new ArrayList<File>();
        for (int i = 0; i < 11; i++) {
            File c = new File(tmp, "p" + i + ".jpg");
            Files.copy(photo.toPath(), c.toPath());
            eleven.add(c);
        }
        status("B-eleven", "11 תמונות: התמונה ה-11 לא מצורפת — הודעה בעברית", dlg, eleven, false, "עד 10");
        clearItems(dlg);
        // PDF פגום — נשלח (השרת יחליט); השרת עונה "לא תרגיל"
        serverError("B-badpdf-notex", "PDF פגום, והשרת עונה \"לא מצאתי תרגיל\": הודעה בעברית ו\"נסו שוב\"", dlg, Arrays.asList(badPdf), "scenario:notex", "לא מצאתי");
        dlg = openSubmit();
        serverError("B-http500", "השרת מחזיר 500 (דף HTML): הודעה בעברית", dlg, Arrays.asList(photo), "scenario:http500", "השרת");
        dlg = openSubmit();
        serverError("B-badjson", "השרת מחזיר JSON שבור: הודעה בעברית", dlg, Arrays.asList(photo), "scenario:badjson", "השרת");
        dlg = openSubmit();
        serverError("B-reset", "החיבור נקטע באמצע: הודעה בעברית בלי אנגלית", dlg, Arrays.asList(photo), "scenario:reset", "החיבור");
        // ביטול באמצע, ולחיצה כפולה על "שליחה"
        dlg = openSubmit();
        final Shell d1 = dlg;
        prepare(dlg, Arrays.asList(photo), "scenario:slow");
        int before = mockRequests();
        ui(() -> { invoke(d1.getData(), "send"); invoke(d1.getData(), "send"); return null; });
        sleep(2500);
        int sent = mockRequests() - before;
        check("B-doublesend", "לחיצה כפולה על \"שליחה\": בקשה אחת לשרת", sent == 1, "requests=" + sent);
        ui(() -> { invoke(d1.getData(), "cancel"); return null; });
        sleep(1500);
        String st = ui(() -> d1.isDisposed() ? "closed" : labels(d1));
        check("B-cancel", "ביטול באמצע השליחה: חוזרים לטופס עם הודעה בעברית", st.contains("השליחה בוטלה"), st);
        // כיבוי DSViz בזמן שחלון הבנייה פתוח
        ui(() -> { if (!d1.isDisposed()) d1.close(); return null; });
        dlg = openSubmit();
        final Shell d2 = dlg;
        prepare(dlg, Arrays.asList(photo), "scenario:slow");
        ui(() -> { invoke(d2.getData(), "send"); return null; });
        sleep(1200);
        int logBefore = logged.size();
        ui(() -> { il.lamerhav.dsviz.PowerHandler.apply(false); return null; });
        sleep(1200);
        ui(() -> { if (!d2.isDisposed()) invoke(d2.getData(), "cancel"); return null; });
        sleep(1200);
        ui(() -> { il.lamerhav.dsviz.PowerHandler.apply(true); return null; });
        sleep(800);
        check("B-power-during", "כיבוי DSViz בזמן שליחה: בלי שגיאה, ואפשר להמשיך", logged.size() == logBefore && foreignShells().isEmpty(), "log=" + logged.subList(logBefore, logged.size()));
        ui(() -> { if (!d2.isDisposed()) d2.close(); return null; });
        closeForeign();
        // אותו תרגיל פעמיים — חבילה חדשה (ex02); הערה ארוכה עם אמוג'י
        StringBuilder note = new StringBuilder("scenario:nowords ");
        for (int i = 0; i < 300; i++) note.append("הערה ארוכה 😀🎉 ");
        String r1 = buildOk(Arrays.asList(photo), note.toString(), "SubEdge");
        String r2 = buildOk(Arrays.asList(photo), "scenario:nowords", "SubEdge");
        check("B-longnote", "הערה ארוכה מאוד עם אמוג'י: התרגיל נוצר", r1.startsWith("TOAST"), r1);
        check("B-twice", "אותו תרגיל פעמיים: חבילה חדשה ולא דורסים (ex01 + ex02)", r2.startsWith("TOAST") && folders("SubEdge").matches(".*ex01_\\w+ ex02_\\w+.*"), r2 + " folders=" + folders("SubEdge"));
        // הפרויקט נסגר / נמחק בזמן השליחה
        ui(() -> { IJavaProject p = il.lamerhav.dsviz.ProjectSetup.createProject("Closing");
            il.lamerhav.dsviz.ProjectSetup.class.getMethod("prepare", IJavaProject.class, Class.forName("il.lamerhav.dsviz.ProjectSetup$ConflictResolver")).invoke(null, p, null);
            return null; });
        String rc = buildDuring(Arrays.asList(photo), "Closing", p -> p.close(null));
        check("B-closed", "הפרויקט נסגר בזמן השליחה: הודעה בעברית (בלי הודעה של Eclipse באנגלית)", rc.startsWith("ERROR") && hebrew(rc) && !englishSentence(rc), rc);
        ui(() -> { IJavaProject p = il.lamerhav.dsviz.ProjectSetup.createProject("Deleting");
            il.lamerhav.dsviz.ProjectSetup.class.getMethod("prepare", IJavaProject.class, Class.forName("il.lamerhav.dsviz.ProjectSetup$ConflictResolver")).invoke(null, p, null);
            return null; });
        String rd = buildDuring(Arrays.asList(photo), "Deleting", p -> p.delete(true, true, null));
        check("B-deleted", "הפרויקט נמחק בזמן השליחה: הודעה בעברית, או שנוצר מחדש — בלי חלון שגיאה",
                (rd.startsWith("ERROR") && hebrew(rd) && !englishSentence(rd) || rd.startsWith("TOAST")), rd);
        ui(() -> { IJavaProject p = il.lamerhav.dsviz.ProjectSetup.createProject("ReadOnly");
            il.lamerhav.dsviz.ProjectSetup.class.getMethod("prepare", IJavaProject.class, Class.forName("il.lamerhav.dsviz.ProjectSetup$ConflictResolver")).invoke(null, p, null);
            return null; });
        File roSrc = ResourcesPlugin.getWorkspace().getRoot().getProject("ReadOnly").getFolder("src").getLocation().toFile();
        String rr = buildDuring(Arrays.asList(photo), "ReadOnly", p -> roSrc.setWritable(false));
        roSrc.setWritable(true);
        check("B-readonly", "תיקיית הפרויקט לקריאה בלבד: הודעה בעברית (לא \"התרגיל נוצר\")", rr.startsWith("ERROR") && hebrew(rr) && !englishSentence(rr) || "root".equals(System.getProperty("user.name")) && rr.startsWith("TOAST"),
                rr + (System.getProperty("user.name").equals("root") ? " (root — הרשאות לא נאכפות)" : ""));
        // אין חיבור לשרת
        String keep = System.getProperty("dsviz.worker");
        System.setProperty("dsviz.worker", "http://127.0.0.1:9");
        int lb = logged.size();
        command("il.lamerhav.dsviz.submit");
        Shell any = waitShell(x -> x.getData("dsviz.say") != null || x.getData() != null && x.getData().getClass().getSimpleName().equals("SubmitDialog"), 20000);
        String offline;
        if (any != null && ui(() -> any.getData("dsviz.say") != null)) {
            offline = "SAY " + ui(() -> labels(any));
            ui(() -> { closeSay(any); return null; });
        } else if (any != null) {
            prepare(any, Arrays.asList(photo), "scenario:nowords");
            ui(() -> { invoke(any.getData(), "send"); return null; });
            waitFor(() -> ui(() -> any.isDisposed() || labels(any).contains("נסו")), 30000);
            offline = "DIALOG " + ui(() -> any.isDisposed() ? "closed" : labels(any));
            ui(() -> { if (!any.isDisposed()) any.close(); return null; });
        } else offline = "none " + foreignShells();
        System.setProperty("dsviz.worker", keep);
        check("B-offline", "אין חיבור לשרת: הודעה בעברית", (offline.contains("אין חיבור") || offline.contains("אינטרנט")) && logged.size() == lb, offline);
        closeForeign();
    }

    /** הפרויקט שאליו שולחים תרגילים */
    private void subProject() throws Exception {
        if (ResourcesPlugin.getWorkspace().getRoot().getProject("SubEdge").exists()) return;
        ui(() -> {
            IJavaProject jp = il.lamerhav.dsviz.ProjectSetup.createProject("SubEdge");
            Class<?> resolver = Class.forName("il.lamerhav.dsviz.ProjectSetup$ConflictResolver");
            il.lamerhav.dsviz.ProjectSetup.class.getMethod("prepare", IJavaProject.class, resolver).invoke(null, jp, null);
            return null;
        });
    }

    private Shell openSubmit() throws Exception {
        closeForeign();
        command("il.lamerhav.dsviz.submit");
        Shell dlg = waitShell(s -> s.getData() != null && s.getData().getClass().getSimpleName().equals("SubmitDialog"), 20000);
        sleep(400);
        return dlg;
    }

    private void prepare(Shell dlg, List<File> files, String note) throws Exception {
        ui(() -> {
            Object d = dlg.getData();
            if (!files.isEmpty()) {
                Method add = d.getClass().getDeclaredMethod("addFiles", List.class);
                add.setAccessible(true);
                add.invoke(d, files);
            }
            ((Text) field(d, "noteText")).setText(note);
            org.eclipse.swt.widgets.Combo c = (org.eclipse.swt.widgets.Combo) field(d, "projectCombo");
            int i = Arrays.asList(c.getItems()).indexOf("SubEdge");
            if (i >= 0) c.select(i);
            return null;
        });
        sleep(300);
    }

    private void clearItems(Shell dlg) throws Exception {
        ui(() -> { ((List<?>) field(dlg.getData(), "items")).clear(); invoke(dlg.getData(), "refresh"); return null; });
    }

    /** מצרף קבצים (ואם send — לוחץ "שליחה"), ובודק את שורת ההודעה בחלון */
    private void status(String id, String desc, Shell dlg, List<File> files, boolean send, String expect) throws Exception {
        if (dlg == null) {
            fail(id, desc, "החלון לא נפתח");
            return;
        }
        int lb = logged.size();
        clearItems(dlg);
        prepare(dlg, files, "x");
        if (send) ui(() -> { invoke(dlg.getData(), "send"); return null; });
        sleep(500);
        String st = ui(() -> labels(dlg));
        boolean rtl = ui(() -> dlg.getOrientation() == SWT.RIGHT_TO_LEFT);
        check(id, desc, st.contains(expect) && rtl && logged.size() == lb && !st.matches(".*(Exception|java\\.).*"), st.length() > 300 ? st.substring(0, 300) : st);
    }

    private void serverError(String id, String desc, Shell dlg, List<File> files, String note, String expect) throws Exception {
        if (dlg == null) {
            fail(id, desc, "החלון לא נפתח");
            return;
        }
        int lb = logged.size();
        prepare(dlg, files, note);
        ui(() -> { invoke(dlg.getData(), "send"); return null; });
        waitFor(() -> ui(() -> dlg.isDisposed() || ((String) invoke(dlg.getData(), "stageReport")).contains("failed")), 30000);
        sleep(400);
        String st = ui(() -> dlg.isDisposed() ? "closed" : labels(dlg));
        if (id.equals("B-reset")) shot(dlg, "submit-reset.png");
        if (id.equals("B-badpdf-notex")) shot(dlg, "submit-notex.png");
        ui(() -> { if (!dlg.isDisposed()) dlg.close(); return null; });
        check(id, desc, st.contains(expect) && st.contains("התרגיל לא נוצר") && logged.size() == lb && !st.matches(".*[A-Za-z]{3,} [a-z]{3,} [a-z]{3,}.*"), st);
    }

    /** שליחה מלאה עד ההודעה בפינה. מחזיר "TOAST ..." או "ERROR ..." */
    private String buildOk(List<File> files, String note, String project) throws Exception {
        Shell dlg = openSubmit();
        prepare(dlg, files, note);
        ui(() -> { selectProject(dlg, project); invoke(dlg.getData(), "send"); return null; });
        return outcome(dlg);
    }

    private interface ProjectAction {
        void run(IProject p) throws Exception;
    }

    /** שליחה, ובזמן שהשרת "חושב" — עושים משהו לפרויקט (סגירה / מחיקה / קריאה בלבד) */
    private String buildDuring(List<File> files, String project, ProjectAction action) throws Exception {
        Shell dlg = openSubmit();
        prepare(dlg, files, "scenario:nowords");
        ui(() -> { selectProject(dlg, project); invoke(dlg.getData(), "send"); return null; });
        sleep(500);
        action.run(ResourcesPlugin.getWorkspace().getRoot().getProject(project));
        return outcome(dlg);
    }

    private String outcome(Shell dlg) throws Exception {
        int lb = logged.size();
        waitFor(() -> ui(() -> dlg.isDisposed() || ((String) invoke(dlg.getData(), "stageReport")).contains("failed")), 90000);
        sleep(600);
        String res;
        if (!dlg.isDisposed() && ui(() -> !dlg.isDisposed())) {
            res = "ERROR " + ui(() -> labels(dlg));
            shot(dlg, "submit-" + System.currentTimeMillis() % 1000 + ".png");
            ui(() -> { dlg.close(); return null; });
        } else {
            Shell t = waitShell(s -> s.getData("dsviz.toast") != null, 8000);
            res = t == null ? "NONE foreign=" + foreignShells() : "TOAST " + ui(() -> labels(t));
            if (t != null) ui(() -> { t.dispose(); return null; });
        }
        if (logged.size() > lb) res += " LOG=" + logged.subList(lb, logged.size());
        res += foreignShells().isEmpty() ? "" : " FOREIGN=" + foreignShells();
        closeForeign();
        return res;
    }

    private void selectProject(Shell dlg, String project) throws Exception {
        org.eclipse.swt.widgets.Combo c = (org.eclipse.swt.widgets.Combo) field(dlg.getData(), "projectCombo");
        int i = Arrays.asList(c.getItems()).indexOf(project);
        if (i >= 0) c.select(i);
    }

    private String folders(String project) throws Exception {
        org.eclipse.core.resources.IFolder s = ResourcesPlugin.getWorkspace().getRoot().getProject(project).getFolder("src");
        List<String> names = new ArrayList<String>();
        if (s.exists()) for (org.eclipse.core.resources.IResource r : s.members()) if (r.getName().startsWith("ex")) names.add(r.getName());
        Collections.sort(names);
        return String.join(" ", names);
    }

    private int mockRequests() throws Exception {
        File log = new File(System.getProperty("edge.mocklog", "/nonexistent"));
        if (!log.isFile()) return -1;
        int n = 0;
        for (String l : Files.readAllLines(log.toPath())) if (l.startsWith("POST /v1/exercise")) n++;
        return n;
    }

    // ================================================================== E. הצעה לפתרון

    private void solutionCases() throws Exception {
        // תרגיל עם הצעות (התרחיש words), ואז: שינוי שם הפעולה, מחיקת התיקייה, solutions.json פגום / חסר
        subProject();
        String res = buildOk(Arrays.asList(new File(attach, "photo.jpg")), "scenario:words", "SubEdge");
        if (!res.startsWith("TOAST")) {
            fail("E-setup", "יצירת תרגיל עם הצעות לפתרון", res);
            return;
        }
        IFile button = null;
        org.eclipse.core.resources.IFolder s = ResourcesPlugin.getWorkspace().getRoot().getProject("SubEdge").getFolder("src");
        for (org.eclipse.core.resources.IResource r : s.members())
            if (r instanceof IFolder && ((IFolder) r).getFile("Button.java").exists()) button = ((IFolder) r).getFile("Button.java");
        if (button == null) {
            fail("E-setup", "Button.java לא נמצא", folders("SubEdge"));
            return;
        }
        final IFile bf = button;
        solution("E-normal", "הסמן בפעולה של סעיף: נפתח חלון ההצעה", bf, "isSameSize", null, true);
        solution("E-renamed", "שם הפעולה שונה אחרי שהתרגיל נוצר: הצעה או הודעה בעברית, בלי חריגה", bf, "isSameSize", t -> t.replace("isSameSize", "sameSizeRenamed"), false);
        File store = new File(ResourcesPlugin.getWorkspace().getRoot().getLocation().toFile(), ".metadata/.plugins/il.lamerhav.dsviz/solutions.json");
        byte[] good = store.isFile() ? Files.readAllBytes(store.toPath()) : new byte[0];
        Files.write(store.toPath(), "{\"solutions\": [ {\"path\": ".getBytes(StandardCharsets.UTF_8));
        store.setLastModified(System.currentTimeMillis() + 5000);
        solution("E-corrupt", "solutions.json פגום: הודעה בעברית, בלי חריגה", bf, "isSameSize", null, false);
        store.delete();
        solution("E-missing", "solutions.json חסר: הודעה בעברית, בלי חריגה", bf, "isSameSize", null, false);
        Files.write(store.toPath(), good);
        store.setLastModified(System.currentTimeMillis() + 10000);
        // מחיקת תיקיית התרגיל כשהעורך פתוח
        int lb = logged.size();
        ui(() -> { bf.getParent().delete(true, null); return null; });
        sleep(800);
        command("il.lamerhav.dsviz.solution");
        Shell sh = waitShell(x -> x.getData("dsviz.say") != null || x.getData() != null && x.getData().getClass().getSimpleName().equals("SolutionDialog"), 6000);
        String info = sh == null ? "none" : ui(() -> labels(sh));
        if (sh != null) ui(() -> { sh.close(); return null; });
        check("E-folder-deleted", "תיקיית התרגיל נמחקה: בלי חריגה (הודעה בעברית או כלום)", logged.size() == lb && foreignShells().isEmpty(), info + " log=" + logged.subList(lb, logged.size()));
        closeForeign();
    }

    private interface Edit {
        String apply(String t);
    }

    private void solution(String id, String desc, IFile f, String method, Edit edit, boolean expectDialog) throws Exception {
        int lb = logged.size();
        ui(() -> {
            ITextEditor ed = (ITextEditor) IDE.openEditor(page(), f, true);
            page().activate(ed);
            IDocument doc = ed.getDocumentProvider().getDocument(ed.getEditorInput());
            if (edit != null) {
                doc.set(edit.apply(doc.get()));
                ed.doSave(null);
            }
            int at = doc.get().indexOf("{", Math.max(0, doc.get().indexOf(edit == null ? method : "sameSizeRenamed"))) + 1;
            ed.selectAndReveal(at, 0);
            return null;
        });
        sleep(400);
        command("il.lamerhav.dsviz.solution");
        Shell sh = waitShell(x -> x.getData("dsviz.say") != null || x.getData() != null && x.getData().getClass().getSimpleName().equals("SolutionDialog"), 8000);
        boolean dialog = sh != null && ui(() -> sh.getData("dsviz.say") == null);
        String text = sh == null ? "none" : ui(() -> labels(sh));
        boolean rtl = sh != null && ui(() -> sh.getOrientation() == SWT.RIGHT_TO_LEFT);
        if (id.equals("E-normal") && sh != null) shot(sh, "solution-dialog.png");
        if (sh != null) ui(() -> { if (sh.getData("dsviz.say") != null) closeSay(sh); else sh.close(); return null; });
        check(id, desc, sh != null && rtl && (expectDialog ? dialog : hebrew(text)) && logged.size() == lb && foreignShells().isEmpty(),
                "dialog=" + dialog + " " + (text.length() > 200 ? text.substring(0, 200) : text) + " log=" + logged.subList(lb, logged.size()));
        closeForeign();
    }

    // ================================================================== עזרים

    private IFile file(String name, String content) throws Exception {
        return createIn(src, name, content);
    }

    private IFile fileBytes(String name, String content) throws Exception {
        return createIn(src, name, content);
    }

    private IFile createIn(IContainer c, String name, String content) throws Exception {
        return ui(() -> {
            IFile f = c.getFile(new Path(name));
            ByteArrayInputStream in = new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
            if (f.exists()) f.setContents(in, true, false, null);
            else f.create(in, true, null);
            f.setCharset("UTF-8", null);
            return f;
        });
    }

    private static File write(File dir, String name, byte[] data) throws Exception {
        File f = new File(dir, name);
        Files.write(f.toPath(), data);
        return f;
    }

    private void check(String id, String desc, boolean ok, String observed) {
        if (ok) {
            oks++;
            out.println("ok   " + id + "  " + desc);
        } else fail(id, desc, observed);
        out.flush();
    }

    private void fail(String id, String desc, String observed) {
        fails++;
        out.println("FAIL " + id + "  " + desc + "  (" + observed + ")");
        out.flush();
    }

    /** יש בטקסט עברית */
    private static boolean hebrew(String s) {
        return s != null && s.matches("(?s).*[א-ת].*");
    }

    /** משפט באנגלית (שלוש מילים ברצף) — הודעה שלא תורגמה */
    private static boolean englishSentence(String s) {
        return s != null && java.util.regex.Pattern.compile("[A-Za-z]{2,} [a-z]{2,} [a-z]{2,} [a-z]{2,}").matcher(s.replaceAll("\\b(import|unit4|java|util|public|static|void|class)\\b", "")).find();
    }

    private IWorkbenchPage page() {
        return PlatformUI.getWorkbench().getWorkbenchWindows()[0].getActivePage();
    }

    private void command(String id) {
        display.asyncExec(() -> {
            try {
                PlatformUI.getWorkbench().getService(IHandlerService.class).executeCommand(id, null);
            } catch (org.eclipse.core.commands.NotHandledException | org.eclipse.core.commands.NotEnabledException e) {
                // הפקודה כבויה (למשל DSViz כבוי) — זו התוצאה הנכונה
            } catch (Exception e) {
                logged.add("command " + id + ": " + e);
            }
        });
    }

    private interface Check {
        boolean test(Shell s);
    }

    private interface Pick {
        boolean test(Control c);
    }

    private interface Cond {
        boolean ok() throws Exception;
    }

    private <T> T ui(Callable<T> c) throws Exception {
        final Object[] r = new Object[1];
        final Exception[] err = new Exception[1];
        display.syncExec(() -> {
            try {
                r[0] = c.call();
            } catch (Exception e) {
                err[0] = e;
            }
        });
        if (err[0] != null) throw err[0];
        @SuppressWarnings("unchecked")
        T t = (T) r[0];
        return t;
    }

    private Shell findShell(Check c) {
        for (Shell x : display.getShells()) if (!x.isDisposed() && x.isVisible() && c.test(x)) return x;
        return null;
    }

    private Shell waitShell(Check c, long ms) throws Exception {
        long end = System.currentTimeMillis() + ms;
        while (System.currentTimeMillis() < end) {
            Shell s = ui(() -> findShell(c));
            if (s != null) return s;
            sleep(150);
        }
        return null;
    }

    private boolean waitFor(Cond c, long ms) throws Exception {
        long end = System.currentTimeMillis() + ms;
        while (System.currentTimeMillis() < end) {
            if (c.ok()) return true;
            sleep(150);
        }
        return c.ok();
    }

    /** חלונות שאינם שלנו ואינם החלון של Eclipse — למשל "Problem Occurred" או חלון שגיאה */
    private List<String> foreignShells() throws Exception {
        return ui(() -> {
            List<String> r = new ArrayList<String>();
            Shell main = page().getWorkbenchWindow().getShell();
            for (Shell x : display.getShells()) {
                if (x.isDisposed() || !x.isVisible() || x == main) continue;
                Object d = x.getData();
                if (x.getData("dsviz.say") != null || x.getData("dsviz.toast") != null || x.getData("dsviz.popover") != null) continue;
                if (d != null && d.getClass().getName().startsWith("il.lamerhav.dsviz")) continue;
                if (x.getText() == null || x.getText().isEmpty()) continue;   // חלוניות קטנות בלי כותרת (רמזים, השלמה)
                r.add(x.getText() + ": " + labels(x));
            }
            return r;
        });
    }

    private void closeForeign() throws Exception {
        ui(() -> {
            Shell main = page().getWorkbenchWindow().getShell();
            for (Shell x : display.getShells()) if (!x.isDisposed() && x.isVisible() && x != main && x.getParent() != null) x.close();
            return null;
        });
    }

    private static List<Control> all(Composite c) {
        List<Control> out = new ArrayList<Control>();
        if (c == null || c.isDisposed()) return out;
        for (Control k : c.getChildren()) {
            out.add(k);
            if (k instanceof Composite) out.addAll(all((Composite) k));
        }
        return out;
    }

    private static Control find(Composite c, Pick p) {
        for (Control k : all(c)) if (p.test(k)) return k;
        return null;
    }

    private static String labels(Composite c) {
        StringBuilder b = new StringBuilder();
        for (Control k : all(c)) {
            if (!k.isVisible()) continue;
            String t = k instanceof Label ? ((Label) k).getText() : k instanceof Text ? null : k instanceof Button ? ((Button) k).getText() : null;
            if (t == null || t.trim().isEmpty()) continue;
            b.append(b.length() > 0 ? " / " : "").append(t.replaceAll("[‎‏‪-‮⁦-⁩]", ""));
        }
        return b.toString();
    }

    private static void click(Control c) {
        if (c == null) return;
        for (int type : new int[] { SWT.MouseDown, SWT.MouseUp }) {
            Event e = new Event();
            e.x = 2;
            e.y = 2;
            e.button = 1;
            e.widget = c;
            c.notifyListeners(type, e);
        }
    }

    private static Object field(Object o, String name) throws Exception {
        for (Class<?> k = o.getClass(); k != null; k = k.getSuperclass()) {
            try {
                Field f = k.getDeclaredField(name);
                f.setAccessible(true);
                return f.get(o);
            } catch (NoSuchFieldException ignored) {
                // במחלקת האב
            }
        }
        throw new NoSuchFieldException(name);
    }

    private static Object invoke(Object o, String name) throws Exception {
        for (Class<?> k = o.getClass(); k != null; k = k.getSuperclass()) {
            try {
                Method m = k.getDeclaredMethod(name);
                m.setAccessible(true);
                return m.invoke(o);
            } catch (NoSuchMethodException ignored) {
                // במחלקת האב
            }
        }
        throw new NoSuchMethodException(name);
    }

    private static String sh(String... cmd) {
        try {
            Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
            String s = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            p.waitFor();
            return s;
        } catch (Exception e) {
            return "";
        }
    }

    private void shot(Shell s, String name) throws Exception {
        ui(() -> {
            if (s == null || s.isDisposed()) return null;
            Rectangle ca = s.getClientArea();
            org.eclipse.swt.graphics.Point o = s.toDisplay(0, 0), e = s.toDisplay(ca.width, 0);
            capture(new Rectangle(Math.min(o.x, e.x), o.y, ca.width, ca.height), new File(shots, name));
            return null;
        });
    }

    private void shotScreen(String name) throws Exception {
        ui(() -> {
            capture(display.getBounds(), new File(shots, name));
            return null;
        });
    }

    private void capture(Rectangle b, File file) {
        Image img = new Image(display, b.width, b.height);
        GC gc = new GC(display);
        gc.copyArea(img, b.x, b.y);
        gc.dispose();
        ImageLoader l = new ImageLoader();
        l.data = new org.eclipse.swt.graphics.ImageData[] { img.getImageData() };
        l.save(file.getAbsolutePath(), SWT.IMAGE_PNG);
        img.dispose();
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public void stop() {
    }
}
