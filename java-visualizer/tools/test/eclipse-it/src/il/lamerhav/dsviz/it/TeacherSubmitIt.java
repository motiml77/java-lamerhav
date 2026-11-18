package il.lamerhav.dsviz.it;

import java.io.File;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

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
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.application.WorkbenchAdvisor;

import il.lamerhav.dsviz.Account;
import il.lamerhav.dsviz.Submit;

/**
 * "הגשה למורה" בחלון הצף "השאלה" של Eclipse אמיתי, מול השרת המקומי (worker/test/eclipse-submit-e2e.mjs מפעיל דרך gate.sh עם
 * GATE_APP=teachersubmit): המחשב מתחבר כעמדת מחשב בקוד המורה (code.txt), יוצרים תרגיל מהמאגר, "הגשה למורה" → בדיקת ההרצה →
 * "שם המגיש" (בלי שם הכפתור לא פעיל) → "הגשה" → "הוגש למורה ב-…"; ואז "סיימתי את התרגיל" → "סיום ומחיקה" → דף ההגשה ("הגשה וסיום")
 * → שם אחר → ההגשה עוברת, והקבצים נמחקים. מה שהמורה קיבל בודקת הבדיקה ב-Node, דרך השרת.
 *   -application il.lamerhav.dsviz.it.teachersubmit <out.txt> <תיקיית צילומים>
 */
public class TeacherSubmitIt implements IApplication {
    private static final String Q = "stack-sum-size";
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
                        out.println("ERROR " + e);
                    }
                    out.println("DONE");
                    out.close();
                    display.asyncExec(() -> PlatformUI.getWorkbench().close());
                }, "teacher-submit-it");
                t.setDaemon(true);
                t.start();
            }
        });
        return IApplication.EXIT_OK;
    }

    private void run() throws Exception {
        ui(() -> {
            PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell().setBounds(0, 0, 1500, 950);
            org.eclipse.ui.intro.IIntroPart intro = PlatformUI.getWorkbench().getIntroManager().getIntro();
            if (intro != null) PlatformUI.getWorkbench().getIntroManager().closeIntro(intro);
            return null;
        });
        handler = Class.forName("il.lamerhav.dsviz.BankHandler", true, Account.class.getClassLoader());
        // 1. עמדת מחשב: הקוד של המורה (הבדיקה כותבת אותו)
        File codeFile = new File(shots, "code.txt");
        for (int i = 0; i < 200 && !codeFile.isFile(); i++) sleep(300);
        String code = new String(Files.readAllBytes(codeFile.toPath()), StandardCharsets.UTF_8).trim();
        Account.Result claim = Account.claimStation(code, "מעבדה · עמדה 7");
        out.println("CLAIM " + claim.status + " mode=" + Submit.mode() + " teacher=" + Submit.teacher());
        // 2. תרגיל מהמאגר, והחלון הצף
        @SuppressWarnings("unchecked")
        List<String> files = (List<String>) handler.getMethod("createForTest", String.class, String.class).invoke(null, Q, "Lab");
        out.println("CREATED " + files.size());
        ui(() -> { handler.getMethod("openExistingForTest", IWorkbenchPage.class, String.class).invoke(null, page(), Q); return null; });
        sleep(1500);
        Shell fl = ui(() -> (Shell) handler.getMethod("floatShellForTest").invoke(null));
        out.println("FLOAT " + (fl != null));
        out.println("SHOWN " + shown());
        shot(fl, "e-1-question.png");
        // 3. "הגשה למורה" → בדיקת ההרצה
        out.println("OPEN " + click(fl, "submit-open"));
        out.println("PAGE " + waitPage());
        shot(fl, "e-2-submit-page.png");
        Control yes = ui(() -> find(fl, "submit-yes"));
        out.println("YES-BEFORE-NAME enabled=" + ui(() -> yes != null && yes.getEnabled()));
        out.println("WHO " + type(fl, "נועה לוי"));
        out.println("YES-AFTER-NAME enabled=" + ui(() -> { Control y = find(fl, "submit-yes"); return y != null && y.getEnabled(); }));
        shot(fl, "e-3-name.png");
        click(fl, "submit-yes");
        out.println("RESULT " + waitResult());
        sleep(800);
        out.println("AFTER " + shown());
        shot(fl, "e-4-submitted.png");
        // 4. "סיימתי את התרגיל" — מגיש לפני המחיקה
        ui(() -> {
            for (Control c : all(fl)) if (c.getClass().getSimpleName().equals("DoneButton") && c.isVisible()) {
                Event e = new Event();
                e.button = 1;
                c.notifyListeners(SWT.MouseUp, e);
                break;
            }
            return null;
        });
        sleep(800);
        out.println("CONFIRM " + shown());
        shot(fl, "e-5-done-confirm.png");
        ui(() -> {   // "סיום ומחיקה" (הכפתור הראשי בכרטיס)
            for (Control c : all(fl)) if ("primary".equals(c.getData("dsviz.say")) && c.isVisible()) { press(c); break; }
            return null;
        });
        out.println("PAGE2 " + waitPage());
        String before = String.join(",", files);
        type(fl, "יואב כהן");
        shot(fl, "e-6-submit-and-finish.png");
        click(fl, "submit-yes");
        String r2 = waitResult();
        sleep(1500);
        boolean gone = true;
        for (String f : files) if (!f.startsWith("ERROR") && new File(f).exists()) gone = false;
        out.println("RESULT2 " + r2 + " files-deleted=" + gone + " done=" + handler.getMethod("doneForTest", String.class).invoke(null, Q)
                + " float-closed=" + ui(() -> fl.isDisposed() || !fl.isVisible()));
        out.println("FILES " + before.length());
    }

    /** מחכה שבדיקת ההרצה תסתיים (SUBMIT-PAGE בלי checking). מחזיר את השורות של הדף */
    private String waitPage() throws Exception {
        long end = System.currentTimeMillis() + 90000;
        while (System.currentTimeMillis() < end) {
            String page = "", check = "";
            for (String s : shown()) {
                if (s.startsWith("SUBMIT-PAGE")) page = s;
                if (s.startsWith("SUBMIT-CHECK")) check = s;
            }
            if (!page.isEmpty() && !page.contains("checking")) return page + " | " + check;
            sleep(300);
        }
        return "TIMEOUT " + shown();
    }

    private String waitResult() throws Exception {
        long end = System.currentTimeMillis() + 40000;
        while (System.currentTimeMillis() < end) {
            Object r = handler.getMethod("floatSubmitResultForTest").invoke(null);
            if (r != null) return String.valueOf(r);
            sleep(300);
        }
        return "TIMEOUT";
    }

    @SuppressWarnings("unchecked")
    private List<String> shown() throws Exception {
        return (List<String>) handler.getMethod("floatShownForTest").invoke(null);
    }

    private String type(Shell fl, String text) throws Exception {
        return ui(() -> {
            Control c = find(fl, "submit-who");
            if (!(c instanceof Text)) return "NO-FIELD";
            ((Text) c).setText(text);   // Modify — כמו הקלדה
            return ((Text) c).getText();
        });
    }

    private boolean click(Shell fl, String id) throws Exception {
        return ui(() -> {
            Control c = find(fl, id);
            if (c == null || !c.getEnabled()) return false;
            press(c);
            return true;
        });
    }

    private static void press(Control c) {
        for (int type : new int[] { SWT.MouseDown, SWT.MouseUp }) {
            Event e = new Event();
            e.x = 2;
            e.y = 2;
            e.button = 1;
            e.widget = c;
            c.notifyListeners(type, e);
        }
    }

    private static Control find(Composite c, String id) {
        for (Control k : all(c)) if (id.equals(k.getData("dsviz.id")) && !k.isDisposed() && k.isVisible()) return k;
        return null;
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

    private IWorkbenchPage page() {
        return PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage();
    }

    private void shot(Shell s, String name) throws Exception {
        if (s == null) return;
        sleep(500);
        ui(() -> {
            if (s.isDisposed()) return null;
            Rectangle b = s.getBounds();
            Image img = new Image(display, b.width, b.height);
            GC gc = new GC(display);
            gc.copyArea(img, b.x, b.y);
            gc.dispose();
            ImageLoader l = new ImageLoader();
            l.data = new org.eclipse.swt.graphics.ImageData[] { img.getImageData() };
            l.save(new File(shots, name).getAbsolutePath(), SWT.IMAGE_PNG);
            img.dispose();
            return null;
        });
    }

    private <T> T ui(Callable<T> c) throws Exception {
        final Object[] r = new Object[2];
        display.syncExec(() -> {
            try {
                r[0] = c.call();
            } catch (Exception e) {
                r[1] = e;
            }
        });
        if (r[1] != null) throw (Exception) r[1];
        @SuppressWarnings("unchecked") T t = (T) r[0];
        return t;
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ignored) {
            // ממשיכים
        }
    }

    @Override
    public void stop() {
    }
}
