package il.lamerhav.dsviz.it;

import java.io.File;
import java.io.PrintStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.eclipse.core.runtime.preferences.InstanceScope;
import org.eclipse.equinox.app.IApplication;
import org.eclipse.equinox.app.IApplicationContext;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jface.text.IDocument;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.ImageLoader;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.application.WorkbenchAdvisor;
import org.eclipse.ui.handlers.IHandlerService;
import org.eclipse.ui.ide.IDE;
import org.eclipse.ui.texteditor.ITextEditor;

/**
 * "הצעה לפתרון" בתוך Eclipse אמיתי: בונים תרגיל מתשובה לדוגמה (כמו "שליחת תרגיל" — SubmitHandler.Build), ובודקים:
 * ההצעות נבדקו (הרצה של main עם ההצעות) ונשמרו בצד, הקוד של התלמיד בלי פתרון, מעל כל פעולה של סעיף מופיע "הצעה לפתרון"
 * (ומעל main — לא), לחיצה עליו פותחת את החלון עם ההצעה, וגם מהתפריט (הסמן בתוך פעולה). + צילומי מסך.
 *   eclipse -application il.lamerhav.dsviz.it.solution <exercise.json> <out.txt> <shots-dir>
 */
public class Sol implements IApplication {
    private PrintStream out;

    @Override
    public Object start(IApplicationContext context) throws Exception {
        final String[] a = (String[]) context.getArguments().get(IApplicationContext.APPLICATION_ARGS);
        out = new PrintStream(new File(a[1]), "UTF-8");
        IEclipsePreferences p = InstanceScope.INSTANCE.getNode("il.lamerhav.dsviz");
        p.putBoolean("setupDone", true);
        p.flush();
        final Display display = PlatformUI.createDisplay();
        PlatformUI.createAndRunWorkbench(display, new WorkbenchAdvisor() {
            @Override
            public String getInitialWindowPerspectiveId() {
                return "org.eclipse.jdt.ui.JavaPerspective";
            }

            @Override
            public void postStartup() {
                display.timerExec(2000, () -> {
                    try {
                        run(display, a);
                    } catch (Throwable t) {
                        t.printStackTrace(out);
                    }
                    out.close();
                    PlatformUI.getWorkbench().close();
                });
            }
        });
        return IApplication.EXIT_OK;
    }

    @SuppressWarnings("unchecked")
    private void run(Display display, String[] a) throws Exception {
        IWorkbenchPage page = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage();
        page.getWorkbenchWindow().getShell().setBounds(0, 0, 1400, 900);
        // בגרסאות הישנות מסך "Welcome" מכסה את העורך
        org.eclipse.ui.intro.IIntroPart intro = PlatformUI.getWorkbench().getIntroManager().getIntro();
        if (intro != null) PlatformUI.getWorkbench().getIntroManager().closeIntro(intro);
        Class<?> ps = Class.forName("il.lamerhav.dsviz.ProjectSetup");
        IJavaProject jp = (IJavaProject) ps.getMethod("createProject", String.class).invoke(null, "SolutionTest");
        Class<?> resolver = Class.forName("il.lamerhav.dsviz.ProjectSetup$ConflictResolver");
        IContainer src = (IContainer) ps.getMethod("prepare", IJavaProject.class, resolver).invoke(null, jp, null);

        // "שליחת תרגיל" — בלי השרת: התשובה מהקובץ (כמו שהשרת מחזיר)
        Class<?> json = Class.forName("il.lamerhav.dsviz.Json");
        Method parse = json.getDeclaredMethod("parse", String.class);
        parse.setAccessible(true);
        Map<String, Object> raw = (Map<String, Object>) parse.invoke(null, new String(Files.readAllBytes(new File(a[0]).toPath()), StandardCharsets.UTF_8));
        Class<?> build = Class.forName("il.lamerhav.dsviz.SubmitHandler$Build");
        Constructor<?> c = build.getDeclaredConstructors()[0];
        c.setAccessible(true);
        final Object b = c.newInstance(null, raw, Arrays.asList("targil.pdf"), jp, src);
        Method runM = build.getDeclaredMethod("run", org.eclipse.core.runtime.IProgressMonitor.class);
        runM.setAccessible(true);
        final Throwable[] err = new Throwable[1];
        final boolean[] done = { false };
        long t0 = System.currentTimeMillis();
        Thread th = new Thread(() -> {
            try {
                runM.invoke(b, new NullProgressMonitor());
            } catch (Throwable t) {
                err[0] = t;
            }
            done[0] = true;
        });
        th.start();
        while (!done[0]) if (!display.readAndDispatch()) display.sleep();
        if (err[0] != null) throw new RuntimeException(err[0]);
        out.println("BUILD-TIME " + (System.currentTimeMillis() - t0) + "ms");
        out.println("STATUS " + field(b, "solutionStatus"));
        out.println("ERRORS " + field(b, "errors"));
        List<IFile> files = (List<IFile>) field(b, "files");
        IFile main = files.get(0);
        String text = new String(Files.readAllBytes(main.getLocation().toFile().toPath()), StandardCharsets.UTF_8);
        out.println("STUDENT-CODE-HAS-SOLUTION " + (text.contains("temp.push") || text.contains("count++")));
        File store = new File(org.eclipse.core.runtime.Platform.getStateLocation(org.eclipse.core.runtime.Platform.getBundle("il.lamerhav.dsviz")).toFile(), "solutions.json");
        String stored = new String(Files.readAllBytes(store.toPath()), StandardCharsets.UTF_8);
        out.println("STORE " + store.getName() + " entries=" + (stored.split("\"method\"").length - 1) + " path=" + stored.contains(main.getFullPath().toString()));

        // הקובץ בעורך: "הצעה לפתרון" מעל כל פעולה של סעיף
        final ITextEditor ed = (ITextEditor) IDE.openEditor(page, main);
        il.lamerhav.dsviz.Folding.collapseGiven(ed, main);
        wait(display, 5000);
        StyledText st = (StyledText) ed.getAdapter(Control.class);
        IDocument doc = ed.getDocumentProvider().getDocument(ed.getEditorInput());
        int[] headers = new int[3];
        String[] names = { "countEven(", "removeAll(", "void main(" };
        for (int i = 0; i < names.length; i++) {
            int off = doc.get().indexOf(names[i]);
            headers[i] = doc.getLineOfOffset(off);
            int widgetLine = st.getLineAtOffset(modelToWidget(ed, off));
            out.println("MINING " + names[i] + " line=" + (headers[i] + 1) + " indent=" + st.getLineVerticalIndent(widgetLine));
        }
        shot(display, page.getWorkbenchWindow().getShell(), new File(a[2], "editor.png"));

        // לחיצה על "הצעה לפתרון" שמעל countEven
        int off = doc.get().indexOf("public static int countEven(");
        int wl = st.getLineAtOffset(modelToWidget(ed, off));
        Rectangle r = st.getTextBounds(modelToWidget(ed, off), modelToWidget(ed, off));
        int y = st.getLinePixel(wl) + st.getLineVerticalIndent(wl) / 2;   // השורה מעל הכותרת — בתוך הרווח שבראש השורה
        click(st, r.x + 30, y);
        wait(display, 1500);
        Shell dlg = find(display, "הצעה לפתרון — סעיף א'");
        out.println("CLICK-DIALOG " + (dlg != null));
        if (dlg != null) {
            StyledText code = findText(dlg);
            out.println("DIALOG-CODE " + (code == null ? "-" : code.getText().split("\n")[0]) + " lines=" + (code == null ? 0 : code.getLineCount()));
            out.println("DIALOG-RTL " + ((dlg.getStyle() & SWT.RIGHT_TO_LEFT) != 0) + " code-ltr=" + (code != null && (code.getStyle() & SWT.LEFT_TO_RIGHT) != 0));
            dlg.setLocation(120, 80);
            wait(display, 800);
            shot(display, dlg, new File(a[2], "dialog.png"));
            dlg.close();
        }

        // מהתפריט: הסמן בתוך removeAll
        int off2 = doc.get().indexOf("public static void removeAll(");
        ed.selectAndReveal(doc.get().indexOf('{', off2) + 3, 0);
        page.activate(ed);
        IHandlerService hs = PlatformUI.getWorkbench().getService(IHandlerService.class);
        hs.executeCommand("il.lamerhav.dsviz.solution", null);
        wait(display, 1000);
        Shell dlg2 = find(display, "הצעה לפתרון — סעיף ב'");
        out.println("MENU-DIALOG " + (dlg2 != null) + (dlg2 == null ? "" : " " + findText(dlg2).getText().split("\n")[0]));
        if (dlg2 != null) dlg2.close();
        out.println("DONE");
    }

    private static int modelToWidget(ITextEditor ed, int off) {
        try {
            Method m = org.eclipse.ui.texteditor.AbstractTextEditor.class.getDeclaredMethod("getSourceViewer");
            m.setAccessible(true);
            Object v = m.invoke(ed);
            if (v instanceof org.eclipse.jface.text.ITextViewerExtension5) return ((org.eclipse.jface.text.ITextViewerExtension5) v).modelOffset2WidgetOffset(off);
        } catch (Exception e) {
            // בלי קיפול
        }
        return off;
    }

    private static void click(StyledText st, int x, int y) {
        for (int type : new int[] { SWT.MouseMove, SWT.MouseDown, SWT.MouseUp }) {
            Event e = new Event();
            e.x = x;
            e.y = y;
            e.button = type == SWT.MouseMove ? 0 : 1;
            e.count = 1;
            e.widget = st;
            st.notifyListeners(type, e);
        }
    }

    private static Shell find(Display d, String title) {
        for (Shell s : d.getShells()) if (!s.isDisposed() && s.isVisible() && title.equals(s.getText())) return s;
        return null;
    }

    private static StyledText findText(org.eclipse.swt.widgets.Composite c) {
        for (Control k : c.getChildren()) {
            if (k instanceof StyledText) return (StyledText) k;
            if (k instanceof org.eclipse.swt.widgets.Composite) {
                StyledText t = findText((org.eclipse.swt.widgets.Composite) k);
                if (t != null) return t;
            }
        }
        return null;
    }

    private static Object field(Object o, String name) throws Exception {
        Field f = o.getClass().getDeclaredField(name);
        f.setAccessible(true);
        return f.get(o);
    }

    private static void wait(Display d, long ms) {
        long end = System.currentTimeMillis() + ms;
        while (System.currentTimeMillis() < end) {
            if (!d.readAndDispatch()) {
                try {
                    Thread.sleep(20);
                } catch (InterruptedException ignored) {
                    return;
                }
            }
        }
    }

    private static void shot(Display display, Shell shell, File file) {
        Rectangle b = shell.getBounds();
        Image img = new Image(display, b.width, b.height);
        GC gc = new GC(display);
        gc.copyArea(img, b.x, b.y);
        gc.dispose();
        ImageLoader l = new ImageLoader();
        l.data = new org.eclipse.swt.graphics.ImageData[] { img.getImageData() };
        l.save(file.getAbsolutePath(), SWT.IMAGE_PNG);
        img.dispose();
    }

    @Override
    public void stop() {
    }
}
