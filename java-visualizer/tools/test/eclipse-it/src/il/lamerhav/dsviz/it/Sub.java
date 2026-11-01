package il.lamerhav.dsviz.it;

import java.io.File;
import java.io.PrintStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.eclipse.core.runtime.preferences.InstanceScope;
import org.eclipse.equinox.app.IApplication;
import org.eclipse.equinox.app.IApplicationContext;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jface.text.IDocument;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CTabFolder;
import org.eclipse.swt.custom.CTabItem;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.ImageLoader;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.application.WorkbenchAdvisor;
import org.eclipse.ui.handlers.IHandlerService;
import org.eclipse.ui.texteditor.ITextEditor;

/**
 * "שליחת תרגיל" מקצה לקצה בתוך Eclipse אמיתי, מול שרת מדומה (tools/test/submit/mock-worker.js — תרחיש לפי "scenario:" בהערה):
 * תצוגת הבנייה באותו חלון (השלבים מההתקדמות האמיתית), ההודעה הקטנה בפינה (Toast) והחלונית "הצגה", הלשונית של main ליד
 * הלשונית של הסעיף, הסימון הצהוב של "כאן עונים" בעורך, החלון החדש של שגיאות קומפילציה בהרצה הוויזואלית, ושכל חלון הודעה
 * מימין לשמאל (getOrientation). + צילומי מסך.
 *   java -Ddsviz.worker=http://127.0.0.1:PORT -jar launcher.jar -application il.lamerhav.dsviz.it.submit <out.txt> <shots-dir> <attach-dir>
 * רץ בחוט רקע: כל פעולה על הממשק ב-syncExec, והחלונות המודאליים נפתחים ב-asyncExec (כדי שהחוט לא ייתקע בהם).
 */
public class Sub implements IApplication {
    private PrintStream out;
    private Display display;
    private File shots, attach;

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
                }, "submit-it");
                t.setDaemon(true);
                t.start();
            }
        });
        return IApplication.EXIT_OK;
    }

    private void run() throws Exception {
        ui(() -> {
            IWorkbenchPage page = page();
            page.getWorkbenchWindow().getShell().setBounds(0, 0, 1400, 900);
            org.eclipse.ui.intro.IIntroPart intro = PlatformUI.getWorkbench().getIntroManager().getIntro();
            if (intro != null) PlatformUI.getWorkbench().getIntroManager().closeIntro(intro);
            Class<?> ps = Class.forName("il.lamerhav.dsviz.ProjectSetup");
            IJavaProject jp = (IJavaProject) ps.getMethod("createProject", String.class).invoke(null, "Sub");
            Class<?> resolver = Class.forName("il.lamerhav.dsviz.ProjectSetup$ConflictResolver");
            ps.getMethod("prepare", IJavaProject.class, resolver).invoke(null, jp, null);
            page.getWorkbenchWindow().getShell().forceActive();
            return null;
        });
        sleep(1500);
        List<File> files = Arrays.asList(new File(attach, "photo.jpg"), new File(attach, "exam.pdf"));

        // ---------- 1. סעיפים בקוד, סעיף במילים (עם קוד בטקסט), הצעות לפתרון; הסעיפים ב-Button.java ו-main ב-Program.java
        Shell dlg = openSubmit(files, "scenario:words");
        sleep(1200);
        report("BUILD-SENDING", dlg);
        shot(dlg, "build-sending.png");
        waitGone(dlg, 120000);
        Shell toast = waitShell(s -> s.getData("dsviz.toast") != null, 20000);
        sleep(600);
        toastReport("WORDS", toast);
        // ההודעה לא לוקחת את המיקוד: היא לא החלון הפעיל, והמיקוד לא בתוכה (בלי מנהל חלונות ב-Xvfb אין "חלון פעיל" — אז רק זה נבדק)
        out.println("FOCUS-AFTER-TOAST toast-focus=" + ui(() -> {
            Control f = display.getFocusControl();
            return display.getActiveShell() == toast || f != null && f.getShell() == toast;
        }) + " no-focus-style=" + ui(() -> (toast.getStyle() & SWT.NO_FOCUS) != 0) + " focus=" + ui(() -> {
            Control f = display.getFocusControl();
            return f instanceof StyledText ? "editor" : f == null ? "none" : f.getClass().getSimpleName();
        }));
        out.println("ACTIVE-EDITOR " + ui(() -> page().getActiveEditor().getEditorInput().getName()));
        out.println("TABS " + ui(this::tabs));
        shotWindow("toast-words.png");
        // "הצגה" — החלונית עם הטקסט של סעיף ד
        ui(() -> { click(find(toast, c -> "words".equals(c.getData("dsviz.toast")))); return null; });
        Shell pop = waitShell(s -> s.getData("dsviz.popover") != null, 5000);
        sleep(500);
        out.println("POPOVER " + (pop != null) + (pop == null ? "" : " rtl=" + ui(() -> pop.getOrientation() == SWT.RIGHT_TO_LEFT)
                + " code-ltr-mono=" + ui(() -> codeBlocks(pop)) + " height=" + ui(() -> pop.getSize().y)));
        shotWindow("popover.png");
        if (pop != null) shot(pop, "popover-only.png");
        ui(() -> { if (toast != null && !toast.isDisposed()) toast.dispose(); return null; });
        out.println("POPOVER-CLOSED-WITH-TOAST " + ui(() -> pop == null || pop.isDisposed()));

        // הסימון הצהוב של "כאן עונים" — לפני ואחרי שכותבים בסעיף א
        sleep(800);
        out.println("HIGHLIGHT-BEFORE " + ui(() -> highlight()));
        shotWindow("highlight-before.png");
        ui(() -> {
            ITextEditor ed = (ITextEditor) page().getActiveEditor();
            IDocument doc = ed.getDocumentProvider().getDocument(ed.getEditorInput());
            int at = doc.get().indexOf("isSameSize(Button other) {");
            int line = doc.getLineOfOffset(at) + 2;   // השורה הריקה שמתחת לשורת הסימון
            doc.replace(doc.getLineOffset(line), doc.getLineLength(line) - 1, "        return this.getSize() == other.getSize() && this.getWidth() == other.getWidth();");
            return null;
        });
        sleep(900);
        out.println("HIGHLIGHT-AFTER " + ui(() -> highlight()));
        shotWindow("highlight-after.png");
        // הרצה ויזואלית מקובץ הסעיפים (Button.java, בלי main): ב ו-ג עוד ריקים — החלון החדש של שגיאות הקומפילציה
        ui(() -> { ((ITextEditor) page().getActiveEditor()).doSave(null); return null; });
        sleep(800);
        command("il.lamerhav.dsviz.run");
        Shell errs = waitShell(s -> s.getData("dsviz.say") != null, 30000);
        sleep(600);
        sayReport("RUN-ERRORS", errs);
        out.println("RUN-ERRORS-CODE " + ui(() -> {   // שורת הקוד בכל כרטיס — משמאל לימין, בגופן קוד
            StringBuilder b = new StringBuilder();
            for (Control c : all(errs)) if (c instanceof Label && (c.getStyle() & SWT.LEFT_TO_RIGHT) != 0 && ((Label) c).getText().indexOf('©') < 0)
                b.append(b.length() > 0 ? " | " : "").append(((Label) c).getText());
            return b.toString();
        }));
        // הפירוט — רק בחלון הצדדי "הסברים בעברית" (נפתח לבד); בחלון שקופץ אין כרטיסים ואין שורות קוד
        out.println("RUN-ERRORS-SIDE " + ui(() -> {
            for (org.eclipse.ui.IViewReference r : page().getViewReferences())
                if ("il.lamerhav.dsviz.mentorView".equals(r.getId()) && page().isPartVisible(r.getPart(false))) return r.getId();
            return "-";
        }));
        shot(errs, "run-errors.png");
        ui(() -> { click(find(errs, c -> "primary".equals(c.getData("dsviz.say")))); return null; });
        sleep(800);
        out.println("RUN-ERRORS-JUMP closed=" + ui(() -> errs.isDisposed()) + " editor=" + ui(() -> page().getActiveEditor().getEditorInput().getName()));

        // ---------- 2. בלי סעיפים במילים — ואותו נושא כמו התרגיל הקודם: חבילה חדשה (ex02_buttons), לא דורסים
        dlg = openSubmit(files, "scenario:nowords");
        waitGone(dlg, 120000);
        Shell toast2 = waitShell(s -> s.getData("dsviz.toast") != null && !s.isDisposed(), 20000);
        sleep(600);
        toastReport("NOWORDS", toast2);
        shotWindow("toast-plain.png");
        out.println("FOLDERS " + ui(this::folders));
        // ההרצה הוויזואלית מקובץ הסעיפים בוחרת את main של התרגיל הזה (אותה חבילה), בלי לשאול — גם כשבפרויקט יש עוד Program שמשתמש ב-Button
        out.println("RUN-MAIN " + mainFor());
        ui(() -> { if (!toast2.isDisposed()) toast2.dispose(); return null; });

        // ---------- 3. שגיאות קומפילציה שנשארו גם אחרי התיקון: השלב "בודק שהקוד מתקמפל" + "מבקש תיקון", והודעה בצבע אזהרה
        dlg = openSubmit(files, "scenario:broken");
        final Shell d3 = dlg;
        waitFor(() -> ui(() -> d3.isDisposed() || stageReport(d3).contains("now pending") && stageReport(d3).startsWith("done done done now")), 60000);
        sleep(1500);
        report("BUILD-REPAIR", dlg);
        out.println("BUILD-REPAIR-SUB " + ui(() -> d3.isDisposed() ? "-" : labels(d3).contains("מבקש תיקון")));
        shot(dlg, "build-repair.png");
        waitGone(dlg, 120000);
        Shell toast3 = waitShell(s -> s.getData("dsviz.toast") != null && !s.isDisposed(), 20000);
        sleep(600);
        toastReport("BROKEN", toast3);
        shotWindow("toast-errors.png");
        ui(() -> { click(find(toast3, c -> "errors".equals(c.getData("dsviz.toast")))); return null; });
        sleep(800);
        out.println("ERRORS-JUMP " + ui(() -> {
            ITextEditor ed = (ITextEditor) page().getActiveEditor();
            IDocument doc = ed.getDocumentProvider().getDocument(ed.getEditorInput());
            int off = ((org.eclipse.jface.text.ITextSelection) ed.getSelectionProvider().getSelection()).getOffset();
            int line = doc.getLineOfOffset(off);
            return ed.getEditorInput().getName() + ":" + (line + 1) + " " + doc.get(doc.getLineOffset(line), doc.getLineLength(line)).trim();
        }));
        ui(() -> { if (!toast3.isDisposed()) toast3.dispose(); return null; });

        // ---------- 4. בלי סעיפים בקוד בכלל
        dlg = openSubmit(files, "scenario:nocode");
        waitGone(dlg, 120000);
        Shell toast4 = waitShell(s -> s.getData("dsviz.toast") != null && !s.isDisposed(), 20000);
        sleep(600);
        toastReport("NOCODE", toast4);
        shotWindow("toast-nocode.png");
        ui(() -> { click(find(toast4, c -> "words".equals(c.getData("dsviz.toast")))); return null; });
        Shell pop4 = waitShell(s -> s.getData("dsviz.popover") != null && !s.isDisposed(), 5000);
        sleep(500);
        out.println("POPOVER-NOCODE " + (pop4 != null) + (pop4 == null ? "" : " code-ltr-mono=" + ui(() -> codeBlocks(pop4))));
        if (pop4 != null) shot(pop4, "popover-nocode.png");
        ui(() -> { if (!toast4.isDisposed()) toast4.dispose(); return null; });

        // ---------- 5. השרת מחזיר שגיאה: שורה אדומה אחת ו"נסו שוב" — שחוזר לטופס עם אותם קבצים
        dlg = openSubmit(files, "scenario:error");
        final Shell d5 = dlg;
        waitFor(() -> ui(() -> stageReport(d5).contains("failed")), 30000);
        sleep(500);
        report("BUILD-ERROR", dlg);
        out.println("BUILD-ERROR-BUTTONS " + ui(() -> visibleButtons(d5)));
        shot(dlg, "build-error.png");
        ui(() -> {
            for (Control c : all(d5)) if (c.isVisible() && c.getClass().getName().endsWith("Look$Round") && "נסו שוב".equals(text(c))) click(c);
            return null;
        });
        sleep(600);
        out.println("RETRY form=" + ui(() -> labels(d5).contains("גררו לכאן") || labels(d5).contains("קבצים")) + " items=" + ui(() -> ((List<?>) field(d5.getData(), "items")).size())
                + " buttons=" + ui(() -> visibleButtons(d5)));
        ui(() -> { d5.close(); return null; });
        sleep(500);

        // ---------- 6. עוד חלונות הודעה — כולם מימין לשמאל ובמראה החדש
        command("il.lamerhav.dsviz.power");
        Shell power = waitShell(s -> s.getData("dsviz.say") != null, 10000);
        sleep(500);
        sayReport("POWER", power);
        shot(power, "dialog-power.png");
        ui(() -> { click(find(power, c -> "secondary".equals(c.getData("dsviz.say")))); return null; });
        sleep(500);
        out.println("POWER-STILL-ON " + ui(() -> il.lamerhav.dsviz.Power.on()));
        // "הצעה לפתרון" כשהסמן בקובץ בלי הצעות (Theory.java — התרגיל בלי סעיפים בקוד)
        ui(() -> {
            for (org.eclipse.ui.IEditorReference r : page().getEditorReferences())
                if (r.getName().equals("Theory.java")) { page().activate(r.getEditor(true)); break; }
            return null;
        });
        sleep(500);
        command("il.lamerhav.dsviz.solution");
        Shell sol = waitShell(s -> s.getData("dsviz.say") != null, 10000);
        sleep(500);
        sayReport("SOLUTION-NONE", sol);
        shot(sol, "dialog-solution.png");
        ui(() -> { if (sol != null) sol.close(); return null; });
    }

    // ------------------------------------------------------------------ "שליחת תרגיל"

    /** פותח את החלון, מצרף קבצים, כותב את התרחיש בהערה, בוחר את הפרויקט Sub ושולח */
    private Shell openSubmit(List<File> files, String note) throws Exception {
        command("il.lamerhav.dsviz.submit");
        Shell dlg = waitShell(s -> "שליחת תרגיל".equals(s.getText()) && s.getData() != null && s.getData().getClass().getSimpleName().equals("SubmitDialog"), 20000);
        sleep(500);
        ui(() -> {
            Object d = dlg.getData();
            Method add = d.getClass().getDeclaredMethod("addFiles", List.class);
            add.setAccessible(true);
            add.invoke(d, files);
            ((Text) field(d, "noteText")).setText(note);
            Combo c = (Combo) field(d, "projectCombo");
            c.select(Arrays.asList(c.getItems()).indexOf("Sub"));
            return null;
        });
        sleep(400);
        if (note.endsWith("words") && !note.contains("no")) shot(dlg, "submit-form.png");
        ui(() -> {
            Method send = dlg.getData().getClass().getDeclaredMethod("send");
            send.setAccessible(true);
            send.invoke(dlg.getData());
            return null;
        });
        return dlg;
    }

    private String stageReport(Shell dlg) throws Exception {
        if (dlg == null || dlg.isDisposed()) return "-";
        Method m = dlg.getData().getClass().getDeclaredMethod("stageReport");
        m.setAccessible(true);
        return (String) m.invoke(dlg.getData());
    }

    private void report(String tag, Shell dlg) throws Exception {
        out.println(tag + " " + ui(() -> dlg.isDisposed() ? "closed" : "stages=[" + stageReport(dlg) + "] rtl=" + (dlg.getOrientation() == SWT.RIGHT_TO_LEFT)
                + " buttons=" + visibleButtons(dlg) + " text=" + labels(dlg).replace("\n", " | ")));
    }

    private void toastReport(String tag, Shell t) throws Exception {
        if (t == null) {
            out.println("TOAST-" + tag + " none");
            return;
        }
        out.println("TOAST-" + tag + " kind=" + ui(() -> t.getData("dsviz.toast")) + " rtl=" + ui(() -> t.getOrientation() == SWT.RIGHT_TO_LEFT)
                + " modal=" + ui(() -> (t.getStyle() & (SWT.APPLICATION_MODAL | SWT.PRIMARY_MODAL | SWT.SYSTEM_MODAL)) != 0)
                + " corner=" + ui(() -> corner(t)) + " text=" + ui(() -> labels(t).replace("\n", " | ")));
    }

    private void sayReport(String tag, Shell s) throws Exception {
        if (s == null) {
            out.println("SAY-" + tag + " none");
            return;
        }
        out.println("SAY-" + tag + " kind=" + ui(() -> s.getData("dsviz.say")) + " rtl=" + ui(() -> s.getOrientation() == SWT.RIGHT_TO_LEFT)
                + " title=" + ui(() -> s.getText()) + " buttons=" + ui(() -> visibleButtons(s)) + " text=" + ui(() -> labels(s).replace("\n", " | ")));
    }

    /** איפה ההודעה: בפינה התחתונה-שמאלית של חלון Eclipse */
    private String corner(Shell t) {
        Rectangle w = page().getWorkbenchWindow().getShell().getBounds(), b = t.getBounds();
        boolean left = b.x - w.x < 60, bottom = w.y + w.height - (b.y + b.height) < 90;
        return (bottom ? "bottom" : "top?") + "-" + (left ? "left" : "right?");
    }

    /** הלשוניות של העורכים לפי הסדר, והפעילה בסוגריים — לבדיקה שהלשונית של main ממש ליד הלשונית של הסעיף */
    private String tabs() {
        IEditorPart active = page().getActiveEditor();
        Control c = active.getAdapter(Control.class);
        while (c != null && !(c instanceof CTabFolder)) c = c.getParent();
        if (c == null) return "?";
        StringBuilder b = new StringBuilder();
        CTabFolder f = (CTabFolder) c;
        for (CTabItem it : f.getItems()) {
            String n = it.getText().replace("*", "");
            b.append(b.length() > 0 ? " " : "").append(it == f.getSelection() ? "[" + n + "]" : n);
        }
        return b.toString();
    }

    private String folders() throws Exception {
        org.eclipse.core.resources.IFolder src = org.eclipse.core.resources.ResourcesPlugin.getWorkspace().getRoot().getProject("Sub").getFolder("src");
        List<String> names = new ArrayList<String>();
        for (org.eclipse.core.resources.IResource r : src.members()) if (r.getName().startsWith("ex")) names.add(r.getName());
        java.util.Collections.sort(names);
        return String.join(" ", names);
    }

    /** RunVisualHandler.mainUsing על Button.java של התרגיל השני — בלי חלון בחירה */
    private String mainFor() throws Exception {
        final String[] r = { "timeout" };
        display.asyncExec(() -> {
            try {
                IFile f = org.eclipse.core.resources.ResourcesPlugin.getWorkspace().getRoot().getProject("Sub").getFile("src/ex02_buttons/Button.java");
                Class<?> h = Class.forName("il.lamerhav.dsviz.RunVisualHandler");
                Method m = h.getDeclaredMethod("mainUsing", IFile.class, Shell.class);
                m.setAccessible(true);
                Object t = m.invoke(null, f, page().getWorkbenchWindow().getShell());
                r[0] = t == null ? "null" : ((org.eclipse.jdt.core.IType) t).getFullyQualifiedName();
            } catch (Throwable e) {
                r[0] = "error " + e;
            }
        });
        for (int i = 0; i < 50 && r[0].equals("timeout"); i++) {
            sleep(100);
            Shell asked = ui(() -> { for (Shell s : display.getShells()) if (!s.isDisposed() && s.isVisible() && s.getData("dsviz.say") != null) return s; return null; });
            if (asked != null) {
                ui(() -> { asked.close(); return null; });
                return "PROMPTED";
            }
        }
        return r[0];
    }

    private String highlight() throws Exception {
        Class<?> h = Class.forName("il.lamerhav.dsviz.AnswerHighlight");
        Method m = h.getDeclaredMethod("report", IEditorPart.class);
        m.setAccessible(true);
        return page().getActiveEditor().getEditorInput().getName() + " " + m.invoke(null, page().getActiveEditor());
    }

    /** כמה בלוקים של קוד בחלונית: משמאל לימין ובגופן קוד */
    private String codeBlocks(Shell s) {
        int n = 0;
        StringBuilder b = new StringBuilder();
        String mono = org.eclipse.jface.resource.JFaceResources.getTextFont().getFontData()[0].getName();
        for (Control c : all(s)) {
            if (!(c instanceof Label) || (c.getStyle() & SWT.LEFT_TO_RIGHT) == 0) continue;
            if (!c.getFont().getFontData()[0].getName().equals(mono)) continue;
            n++;
            if (b.length() == 0) b.append(((Label) c).getText().split("\n")[0]);
        }
        return n + " first=" + b;
    }

    private String visibleButtons(Shell s) {
        List<String> names = new ArrayList<String>();
        for (Control c : all(s)) if (c.isVisible() && c.getClass().getName().endsWith("Look$Round")) names.add(text(c));
        return String.join("+", names);
    }

    private static String text(Control round) {
        try {
            return (String) field(round, "text");
        } catch (Exception e) {
            return "?";
        }
    }

    // ------------------------------------------------------------------ עזרים

    private interface Check {
        boolean test(Shell s);
    }

    private interface Pick {
        boolean test(Control c);
    }

    private IWorkbenchPage page() {
        return PlatformUI.getWorkbench().getWorkbenchWindows()[0].getActivePage();
    }

    private void command(String id) {
        display.asyncExec(() -> {
            try {
                IHandlerService hs = PlatformUI.getWorkbench().getService(IHandlerService.class);
                hs.executeCommand(id, null);
            } catch (Exception e) {
                e.printStackTrace(out);
            }
        });
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

    private Shell waitShell(Check c, long ms) throws Exception {
        long end = System.currentTimeMillis() + ms;
        while (System.currentTimeMillis() < end) {
            Shell s = ui(() -> {
                for (Shell x : display.getShells()) if (!x.isDisposed() && x.isVisible() && c.test(x)) return x;
                return null;
            });
            if (s != null) return s;
            sleep(150);
        }
        out.println("WAIT-SHELL timeout");
        return null;
    }

    private void waitGone(Shell s, long ms) throws Exception {
        waitFor(() -> ui(() -> s == null || s.isDisposed()), ms);
    }

    private interface Cond {
        boolean ok() throws Exception;
    }

    private void waitFor(Cond c, long ms) throws Exception {
        long end = System.currentTimeMillis() + ms;
        while (System.currentTimeMillis() < end && !c.ok()) sleep(150);
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

    /** כל הטקסט שמוצג (בלי תווי הכיווניות) */
    private static String labels(Composite c) {
        StringBuilder b = new StringBuilder();
        for (Control k : all(c)) {
            if (!k.isVisible()) continue;
            String t = k instanceof Label ? ((Label) k).getText() : null;
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

    private void shot(Shell s, String name) throws Exception {
        if (s == null) return;
        ui(() -> {
            if (s.isDisposed()) return null;
            // רק התוכן (בלי מסגרת החלון — ב-Xvfb אין מנהל חלונות שמצייר אותה)
            Rectangle ca = s.getClientArea();
            org.eclipse.swt.graphics.Point o = s.toDisplay(0, 0), e = s.toDisplay(ca.width, 0);   // מימין לשמאל: 0 הוא הקצה הימני
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
