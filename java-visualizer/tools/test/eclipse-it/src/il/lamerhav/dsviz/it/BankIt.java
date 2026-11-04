package il.lamerhav.dsviz.it;

import java.io.File;
import java.io.PrintStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;


import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.eclipse.core.runtime.preferences.InstanceScope;
import org.eclipse.equinox.app.IApplication;
import org.eclipse.equinox.app.IApplicationContext;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.ImageLoader;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.ToolBar;
import org.eclipse.swt.widgets.ToolItem;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.application.WorkbenchAdvisor;
import org.eclipse.ui.commands.ICommandService;

/**
 * "מאגר תרגילים" (ההרחבה il.lamerhav.dsviz.bank) בתוך Eclipse אמיתי: ההרחבה נטענת ליד התוסף, הכפתור בסרגל של DSViz, החלון
 * (נושאים, שאלות עם נקודת קושי, V), יצירת תרגיל משאלה (קבצים, "כאן עונים", הפלט הרצוי מעל main, הצעות לפתרון נשמרו),
 * "פתח את התרגיל" על אותה עבודה, וחלון הצד "השאלה" (סעיפים, כפתורים, הפלט הרצוי, "סיימתי"). + צילומי מסך.
 * המחלקות של ההרחבה — דרך reflection (כדי ש-run.sh יתקמפל גם בלי ההרחבה).
 *   java -Ddsviz.bankProgress=<קובץ> -jar launcher.jar -application il.lamerhav.dsviz.it.bank <out.txt> <shots-dir>
 */
@SuppressWarnings({ "unchecked", "rawtypes" })
public class BankIt implements IApplication {
    private static final String STACK = "sample-stack-even", LOOPS = "sample-loops-digits";
    private PrintStream out;
    private Display display;
    private File shots;
    private Class<?> handler;

    @Override
    public Object start(IApplicationContext context) throws Exception {
        final String[] a = (String[]) context.getArguments().get(IApplicationContext.APPLICATION_ARGS);
        out = new PrintStream(new File(a[0]), "UTF-8");
        shots = new File(a[1]);
        shots.mkdirs();
        IEclipsePreferences p = InstanceScope.INSTANCE.getNode("il.lamerhav.dsviz");
        p.putBoolean("setupDone", true);
        p.flush();
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
                    }
                    out.println("DONE");
                    out.close();
                    display.asyncExec(() -> PlatformUI.getWorkbench().close());
                }, "bank-it");
                t.setDaemon(true);
                t.start();
            }
        });
        return IApplication.EXIT_OK;
    }

    private void run() throws Exception {
        ui(() -> {
            IWorkbenchPage page = page();
            page.getWorkbenchWindow().getShell().setBounds(0, 0, 1500, 950);
            org.eclipse.ui.intro.IIntroPart intro = PlatformUI.getWorkbench().getIntroManager().getIntro();
            if (intro != null) PlatformUI.getWorkbench().getIntroManager().closeIntro(intro);
            return null;
        });
        sleep(1000);
        // 1. ההרחבה נטענה, הפקודה והכפתור
        org.osgi.framework.Bundle frag = Platform.getBundle("il.lamerhav.dsviz.bank");
        out.println("FRAGMENT found=" + (frag != null) + " resolved=" + (frag != null && (frag.getState() & (org.osgi.framework.Bundle.RESOLVED | org.osgi.framework.Bundle.ACTIVE)) != 0));
        handler = Class.forName("il.lamerhav.dsviz.BankHandler");
        out.println("COMMAND " + ui(() -> {
            ICommandService cs = PlatformUI.getWorkbench().getService(ICommandService.class);
            org.eclipse.core.commands.Command c = cs.getCommand("il.lamerhav.dsviz.bank");
            return "defined=" + c.isDefined() + " handled=" + c.isHandled();
        }));
        out.println("TOOLBAR " + ui(() -> {
            StringBuilder b = new StringBuilder();
            for (Control c : all(page().getWorkbenchWindow().getShell())) {
                if (!(c instanceof ToolBar)) continue;
                for (ToolItem it : ((ToolBar) c).getItems()) if (!it.getText().isEmpty()) b.append(it.getText()).append(" | ");
            }
            return b.toString();
        }));
        shotWindow("bank-workbench.png");

        // 2. השאלות במאגר — לפי הנושא ומהקלה לקשה
        for (Object q : (List<?>) handler.getMethod("questionsForTest").invoke(null)) out.println("QUESTION " + q);

        // 3. החלון: נושא מחסנית, השאלה הבינונית
        Shell dlg = ui(() -> (Shell) handler.getMethod("openDialogForTest", Shell.class, String.class, String.class)
                .invoke(null, page().getWorkbenchWindow().getShell(), "stack", STACK));
        sleep(1200);
        out.println("DIALOG title=" + ui(() -> dlg.getText()) + " rtl=" + ui(() -> dlg.getOrientation() == SWT.RIGHT_TO_LEFT));
        out.println("DIALOG-LABELS " + ui(() -> labels(dlg)));
        out.println("DIALOG-BUTTONS " + ui(() -> buttons(dlg)));
        out.println("TOPICS " + ui(() -> {
            StringBuilder b = new StringBuilder();
            for (Object r : (List<?>) field(dlg.getData(), "topicRows")) b.append(field(((Control) r).getData(), "title")).append(" | ");
            return b.toString();
        }));
        out.println("DIALOG-PREVIEW " + ui(() -> preview(dlg)));
        out.println("TREE " + ui(() -> tree(dlg)));
        // חיפוש וסינון לפי קושי
        out.println("SEARCH-QUEUE " + ui(() -> { set(dlg, "search", "תור"); call(dlg, "rebuildTree"); return tree(dlg); }));
        ui(() -> { set(dlg, "search", ""); return null; });
        out.println("FILTER-HARD " + ui(() -> {
            Object hard = Enum.valueOf((Class) Class.forName("il.lamerhav.dsviz.BankStore$Level"), "HARD");
            set(dlg, "levelFilter", hard);
            call(dlg, "rebuildTree");
            return tree(dlg);
        }));
        shot(dlg, "bank-dialog-hard.png");
        ui(() -> { set(dlg, "levelFilter", null); call(dlg, "rebuildTree"); return null; });
        shot(dlg, "bank-dialog.png");
        // V על שאלת הלולאות — ואז הנושא לולאות
        handler.getMethod("setDoneForTest", String.class, boolean.class).invoke(null, LOOPS, true);
        ui(() -> { select(dlg, "loops", LOOPS); return null; });
        sleep(800);
        out.println("DONE-LOOPS " + handler.getMethod("doneForTest", String.class).invoke(null, LOOPS));
        shot(dlg, "bank-dialog-done.png");
        ui(() -> { select(dlg, "tree", "sample-tree-leaves"); return null; });
        sleep(800);
        out.println("PREVIEW-TREE " + ui(() -> preview(dlg)));
        shot(dlg, "bank-dialog-tree.png");
        ui(() -> { select(dlg, "queue", "sample-queue-sum"); return null; });
        sleep(800);
        out.println("PREVIEW-QUEUE " + ui(() -> preview(dlg)));
        shot(dlg, "bank-dialog-queue.png");
        ui(() -> { select(dlg, "list", "sample-list-greater"); return null; });
        sleep(800);
        out.println("PREVIEW-LIST " + ui(() -> preview(dlg)));
        shot(dlg, "bank-dialog-list.png");
        ui(() -> { select(dlg, "strings", ""); return null; });
        sleep(500);
        out.println("EMPTY-TOPIC " + ui(() -> labels(dlg)).contains("עוד אין שאלות בנושא הזה"));
        shot(dlg, "bank-dialog-empty.png");
        ui(() -> { dlg.close(); return null; });

        // 4. יצירת התרגיל מהשאלה
        @SuppressWarnings("unchecked")
        List<String> files = (List<String>) handler.getMethod("createForTest", String.class, String.class).invoke(null, STACK, "Students");
        out.println("CREATED " + files);
        String main = null;
        for (String f : files) {
            if (f.startsWith("ERROR")) continue;
            String text = read(f);
            out.println("FILE " + f + " answer-marks=" + count(text, "כאן עונים סעיף") + " expected-comment=" + text.contains("אם פתרת נכון")
                    + " todo-free=" + !text.contains("return count;"));
            if (text.contains("static void main")) main = f;
            out.println("SOLUTIONS " + f + " " + handler.getMethod("solutionsForTest", String.class).invoke(null, f));
        }
        out.println("MAIN " + main);

        // 5. "פתח את התרגיל" + החלון הצף "השאלה"
        ui(() -> { handler.getMethod("openExistingForTest", IWorkbenchPage.class, String.class).invoke(null, page(), STACK); return null; });
        sleep(1500);
        out.println("ACTIVE-EDITOR " + ui(() -> page().getActiveEditor() == null ? "none" : page().getActiveEditor().getEditorInput().getName()));
        Shell fl = ui(() -> (Shell) handler.getMethod("floatShellForTest").invoke(null));
        out.println("FLOAT open=" + (fl != null) + " question=" + handler.getMethod("floatQuestionForTest").invoke(null)
                + (fl == null ? "" : " rtl=" + ui(() -> fl.getOrientation() == SWT.RIGHT_TO_LEFT) + " title=" + ui(() -> fl.getText())));
        for (Object s : (List<?>) handler.getMethod("floatShownForTest").invoke(null)) out.println("FLOAT-SHOWN " + s);
        shot(fl, "bank-float.png");
        shotWindow("bank-float-workbench.png");
        // סוגרים ופותחים שוב בכפתור "השאלה" (הפקודה) — עם השאלה של הקובץ שבעורך
        ui(() -> { fl.close(); return null; });
        sleep(400);
        ui(() -> {
            PlatformUI.getWorkbench().getService(org.eclipse.ui.handlers.IHandlerService.class).executeCommand("il.lamerhav.dsviz.bankQuestion", null);
            return null;
        });
        sleep(800);
        out.println("FLOAT-REOPEN question=" + handler.getMethod("floatQuestionForTest").invoke(null));

        // 6. "סיימתי": V, וקבצי התרגיל נמחקים
        out.println("COMPLETE " + handler.getMethod("completeForTest", String.class).invoke(null, STACK));
        sleep(800);
        out.println("AFTER-COMPLETE done=" + handler.getMethod("doneForTest", String.class).invoke(null, STACK) + " files-left="
                + ui(() -> {
                    int n = 0;
                    for (String f : files) if (!f.startsWith("ERROR") && ResourcesPlugin.getWorkspace().getRoot().getFile(new Path(f)).exists()) n++;
                    return n;
                }) + " folder-left=" + ui(() -> ResourcesPlugin.getWorkspace().getRoot().getFolder(new Path("/Students/src/ex01_stacks")).exists()));
        out.println("FLOAT-AFTER-DONE " + handler.getMethod("floatShownForTest").invoke(null).toString().contains("DONE true"));
        Shell fl2 = ui(() -> (Shell) handler.getMethod("floatShellForTest").invoke(null));
        shot(fl2, "bank-float-done.png");
        if (fl2 != null) ui(() -> { fl2.close(); return null; });

        // 7. החלון שוב: התרגיל נמחק — "צור תרגיל" מחדש, ו-V בעץ
        Shell dlg2 = ui(() -> (Shell) handler.getMethod("openDialogForTest", Shell.class, String.class, String.class)
                .invoke(null, page().getWorkbenchWindow().getShell(), "stack", STACK));
        sleep(1000);
        out.println("DIALOG2-BUTTONS " + ui(() -> buttons(dlg2)));
        out.println("DIALOG2-TREE " + ui(() -> tree(dlg2)));
        shot(dlg2, "bank-dialog-existing.png");
        ui(() -> { select(dlg2, "list", "sample-list-greater"); return null; });
        sleep(800);
        out.println("PREVIEW-LONG " + ui(() -> preview(dlg2)));
        shot(dlg2, "bank-dialog-long.png");
        ui(() -> { dlg2.close(); return null; });
        out.println("PROGRESS-FILE " + new String(java.nio.file.Files.readAllBytes(new File(System.getProperty("dsviz.bankProgress")).toPath()), StandardCharsets.UTF_8).length());
    }

    // ------------------------------------------------------------------ עזרים

    private static void select(Shell dlg, String topic, String q) throws Exception {
        Object d = dlg.getData();
        Method m = d.getClass().getDeclaredMethod("select", String.class, String.class);
        m.setAccessible(true);
        m.invoke(d, topic, q);
    }

    private static String tree(Shell dlg) throws Exception {
        return String.join(" | ", (List<String>) field(dlg.getData(), "treeShown"));
    }

    private static void set(Shell dlg, String name, Object v) throws Exception {
        Object o = dlg.getData();
        Field f = o.getClass().getDeclaredField(name);
        f.setAccessible(true);
        f.set(o, v);
    }

    private static void call(Shell dlg, String name) throws Exception {
        Object o = dlg.getData();
        Method m = o.getClass().getDeclaredMethod(name);
        m.setAccessible(true);
        m.invoke(o);
    }

    /** מה מוצג בנוסח השאלה בחלון (שורה לכל רכיב: TEXT / FIGURE / EXPECTED) */
    private static String preview(Shell dlg) throws Exception {
        return String.join(" ⏎ ", (List<String>) field(dlg.getData(), "previewShown"));
    }

    /** הטקסט של הכפתורים המצוירים (Look.Round) */
    private static String buttons(Shell dlg) throws Exception {
        StringBuilder b = new StringBuilder();
        for (Control c : all(dlg)) {
            if (!c.getClass().getName().endsWith("Look$Round") || !c.isVisible()) continue;
            b.append(field(c, "text")).append(" | ");
        }
        return b.toString();
    }

    private static String labels(Composite c) {
        StringBuilder b = new StringBuilder();
        for (Control k : all(c)) {
            if (!k.isVisible() || !(k instanceof Label)) continue;
            String t = ((Label) k).getText();
            if (t == null || t.trim().isEmpty()) continue;
            b.append(b.length() > 0 ? " / " : "").append(t.replaceAll("[‎‏‪-‮⁦-⁩]", "").replace("\n", " | "));
        }
        return b.toString();
    }

    private static List<Control> all(Composite c) {
        List<Control> l = new ArrayList<Control>();
        for (Control k : c.getChildren()) {
            l.add(k);
            if (k instanceof Composite) l.addAll(all((Composite) k));
        }
        return l;
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

    private static String read(String path) throws Exception {
        IFile f = ResourcesPlugin.getWorkspace().getRoot().getFile(new Path(path));
        try (java.io.InputStream in = f.getContents()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static int count(String s, String what) {
        int n = 0;
        for (int i = s.indexOf(what); i >= 0; i = s.indexOf(what, i + 1)) n++;
        return n;
    }

    private IWorkbenchPage page() {
        return PlatformUI.getWorkbench().getWorkbenchWindows()[0].getActivePage();
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

    private void shot(Shell s, String name) throws Exception {
        if (s == null) return;
        ui(() -> {
            if (s.isDisposed()) return null;
            Rectangle ca = s.getClientArea();
            org.eclipse.swt.graphics.Point o = s.toDisplay(0, 0), e = s.toDisplay(ca.width, 0);
            capture(new Rectangle(Math.min(o.x, e.x), o.y, ca.width, ca.height), new File(shots, name));
            return null;
        });
    }

    private void shotWindow(String name) throws Exception {
        ui(() -> {
            capture(page().getWorkbenchWindow().getShell().getBounds(), new File(shots, name));
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
