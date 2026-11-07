package il.lamerhav.dsviz.it;

import java.awt.Component;
import java.awt.Container;
import java.awt.Robot;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;

import javax.imageio.ImageIO;
import javax.swing.AbstractButton;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;

import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.eclipse.core.runtime.preferences.InstanceScope;
import org.eclipse.equinox.app.IApplication;
import org.eclipse.equinox.app.IApplicationContext;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.ImageLoader;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.application.WorkbenchAdvisor;

import il.lamerhav.dsviz.Account;

/**
 * מסך הכניסה האמיתי (AccessGate.Dialog, Swing) בתוך Eclipse אמיתי, בהרשמת מורה מקצה לקצה:
 * לוחצים על הכפתור "מאגר תרגילים" (הפקודה il.lamerhav.dsviz.bank → BankHandler → AccessGate.require),
 * בחלון "כניסה למאגר התרגילים" לוחצים על "כניסה עם Google" של המורה; הכתובת שהתוכנה הייתה פותחת בדפדפן נכתבת לקובץ
 * (Account.browser) — ושם הבדיקה (worker/test/eclipse-gate-e2e.mjs, Playwright) נכנסת כמורה חדש, שולחת בקשה עם טלפון, ומאשרת כהנהלה.
 * ואז: "החשבון עוד לא מוגדר כמורה" → "חזרה" → כניסה שוב → "נכנסת בהצלחה" → המאגר נפתח.
 * הבדיקה והתוכנה מתואמות דרך קבצים בתיקיית הצילומים: url-1.txt / url-2.txt (מהתוכנה), approved.flag (מהבדיקה).
 *   java -Ddsviz.worker=... -Ddsviz.passKey=... -Ddsviz.machineDir=... -Ddsviz.home=... -Ddsviz.machine=... -jar launcher.jar
 *        -application il.lamerhav.dsviz.it.gate <out.txt> <shots-dir>
 */
@SuppressWarnings("unchecked")
public class GateIt implements IApplication {
    private static final String TITLE = "כניסה למאגר התרגילים";
    private PrintStream out;
    private Display display;
    private File shots;
    private Robot robot;
    private final AtomicInteger urls = new AtomicInteger();

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
                }, "gate-it");
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
        sleep(1000);
        robot = new Robot();
        // במקום לפתוח דפדפן — הכתובת נכתבת לקובץ, והבדיקה (Playwright) פותחת אותה
        Account.browser = u -> {
            try {
                int n = urls.incrementAndGet();
                out.println("BROWSER-URL " + n + " " + u.replaceAll("(state|challenge)=[^&]+", "$1=*"));
                Files.write(new File(shots, "url-" + n + ".txt").toPath(), u.getBytes(StandardCharsets.UTF_8));
            } catch (Exception e) {
                out.println("ERROR url " + e);
            }
        };
        out.println("ACCESS-BEFORE " + (Account.current() == null ? "none" : Account.current().label()));
        screen("0-workbench");

        // 0b. "עמדת ניהול" בסרגל: לפני כניסה כמורה — אפור
        sleep(1500);
        out.println("TEACHER-BTN-BEFORE " + teacherBtn());
        // 1. הכפתור "מאגר תרגילים" בסרגל (הפקודה שלו) → המסך האמיתי של הכניסה
        out.println("TOOLBAR-COMMAND " + ui(() -> PlatformUI.getWorkbench().getService(org.eclipse.ui.commands.ICommandService.class)
                .getCommand("il.lamerhav.dsviz.bank").isHandled()));
        display.asyncExec(() -> {
            try {
                PlatformUI.getWorkbench().getService(org.eclipse.ui.handlers.IHandlerService.class).executeCommand("il.lamerhav.dsviz.bank", null);
            } catch (Exception e) {
                out.println("ERROR command " + e);
            }
        });
        Window w = waitGate(25000);
        out.println("GATE-OPEN " + (w != null));
        if (w == null) return;
        sleep(800);
        // מימין לשמאל: הכותרת והכרטיס של המורה (הראשון) בחצי הימני של החלון, כרטיס התלמיד בשמאלי
        out.println("GATE-RTL title-right=" + rightHalf(w, "כניסה למאגר התרגילים") + " teacher-card-right=" + rightHalf(w, "כניסה עם חשבון Google שההנהלה") + " size=" + w.getWidth() + "x" + w.getHeight());
        out.println("GATE-TEXT " + oneLine(visibleText(w)));
        out.println("GATE-BUTTONS " + buttons(w));
        screen("1-gate-choose");
        window(w, "1-gate-choose-window");

        // 2. מורה: "כניסה עם Google" (הראשון — הכרטיס של המורה)
        click(w, "כניסה עם Google");
        String url1 = waitFile(new File(shots, "url-1.txt"), 20000);
        out.println("URL1 teacher=" + (url1 != null && url1.contains("role=teacher")) + " signin=" + (url1 != null && url1.contains("/signin")));
        out.println("WAITING-TEXT " + waitText(w, "נפתח דפדפן", 5000) + " " + oneLine(visibleText(w)));
        screen("2-gate-waiting-browser");
        window(w, "2-gate-waiting-window");

        // 3. הבדיקה בדפדפן: Google כחשבון חדש, "לא מורה", טלפון לא תקין ואז תקין — והחלון מראה "עוד לא מוגדר כמורה"
        boolean notTeacher = waitText(w, "החשבון עוד לא מוגדר כמורה", 120000);
        out.println("NOT-TEACHER " + notTeacher);
        sleep(500);
        out.println("NOT-TEACHER-TEXT " + oneLine(visibleText(w)));
        out.println("NOT-TEACHER-ACCESS " + (Account.current() == null ? "none" : Account.current().label()));
        screen("3-gate-not-teacher");
        window(w, "3-gate-not-teacher-window");
        Files.write(new File(shots, "not-teacher.flag").toPath(), "1".getBytes());

        // 4. ההנהלה מאשרת (הבדיקה) — המשתמש לוחץ "חזרה" ואז "כניסה עם Google" שוב
        String ok = waitFile(new File(shots, "approved.flag"), 180000);
        out.println("APPROVED-SIGNAL " + (ok != null));
        click(w, "חזרה");
        sleep(500);
        screen("4-gate-back-to-choose");
        click(w, "כניסה עם Google");
        String url2 = waitFile(new File(shots, "url-2.txt"), 20000);
        out.println("URL2 teacher=" + (url2 != null && url2.contains("role=teacher")));

        // 5. הכניסה הצליחה: "נכנסת בהצלחה" ואחריה (1.4 ש') נפתח המאגר
        boolean success = waitText(w, "נכנסת בהצלחה", 120000);
        out.println("SUCCESS " + success);
        if (success) {
            out.println("SUCCESS-TEXT " + oneLine(visibleText(w)));
            sleep(200);
            screen("5-gate-success");
            window(w, "5-gate-success-window");
        }
        Account.Access acc = Account.current();
        out.println("ACCESS-AFTER " + (acc == null ? "none" : acc.label() + " teacher=" + acc.isTeacher()));

        // 5b. אחרי כניסה כמורה: "עמדת ניהול" בסרגל נהיה פעיל; לחיצה עליו פותחת את חלון המורה מקדימה
        String tb = "";
        for (int i = 0; i < 20; i++) {
            tb = teacherBtn();
            if (tb.contains("enabled=true")) break;
            sleep(500);
        }
        out.println("TEACHER-BTN-AFTER " + tb);
        screen("7-toolbar-teacher");
        display.asyncExec(() -> {
            try {
                PlatformUI.getWorkbench().getService(org.eclipse.ui.handlers.IHandlerService.class).executeCommand("il.lamerhav.dsviz.teacherPanel", null);
            } catch (Exception e) {
                out.println("ERROR teacherPanel " + e);
            }
        });
        Window tp = null;
        for (int i = 0; i < 40 && tp == null; i++) {
            sleep(250);
            for (Window x : Window.getWindows()) if (x.isShowing() && x instanceof javax.swing.JDialog && "עמדת ניהול".equals(((javax.swing.JDialog) x).getTitle())) tp = x;
        }
        out.println("TEACHER-PANEL-OPEN " + (tp != null));
        if (tp != null) {
            final Window tpw = tp;
            sleep(900);
            out.println("TEACHER-PANEL-FRONT " + ui(() -> "active=" + tpw.isActive() + " focused=" + tpw.isFocused() + " visible=" + tpw.isShowing()
                    + " onscreen=" + java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds().intersects(tpw.getBounds())));
            screen("8-teacher-panel-front");
            out.println("TEACHER-PANEL-TEXT " + oneLine(visibleText(tp)));
            // לחיצה נוספת על הכפתור כשהחלון פתוח — לא פותחת עוד חלון
            display.asyncExec(() -> {
                try {
                    PlatformUI.getWorkbench().getService(org.eclipse.ui.handlers.IHandlerService.class).executeCommand("il.lamerhav.dsviz.teacherPanel", null);
                } catch (Exception e) {
                    out.println("ERROR teacherPanel2 " + e);
                }
            });
            sleep(1000);
            int n = 0;
            for (Window x : Window.getWindows()) if (x.isShowing() && x instanceof javax.swing.JDialog && "עמדת ניהול".equals(((javax.swing.JDialog) x).getTitle())) n++;
            out.println("TEACHER-PANEL-COUNT " + n);
            final Window tpc = tp;
            javax.swing.SwingUtilities.invokeAndWait(() -> tpc.dispose());
        }

        // 6. המאגר (חלון SWT "מאגר תרגילים") נפתח
        Shell bank = null;
        long end = System.currentTimeMillis() + 25000;
        while (bank == null && System.currentTimeMillis() < end) {
            bank = ui(() -> {
                for (Shell s : display.getShells()) if (!s.isDisposed() && s.isVisible() && s.getText().contains("מאגר תרגילים")) return s;
                return null;
            });
            if (bank == null) sleep(200);
        }
        out.println("BANK-OPEN " + (bank != null));
        if (bank != null) {
            sleep(3000);
            final Shell b = bank;
            out.println("BANK-TREE " + ui(() -> {
                try {
                    java.lang.reflect.Field f = b.getData().getClass().getDeclaredField("treeShown");
                    f.setAccessible(true);
                    return String.join(" | ", (java.util.List<String>) f.get(b.getData()));
                } catch (Exception e) {
                    return "? " + e;
                }
            }));
            out.println("BANK-RTL " + ui(() -> b.getOrientation() == SWT.RIGHT_TO_LEFT) + " title=" + ui(() -> b.getText()));
            out.println("BANK-DIAG " + bankDiag(b));
            shotShell(bank, "6-bank-open.png");
            screen("6-bank-open-screen");
            ui(() -> {   // בדיקה: האם בניית העץ מחדש (אחרי שהחלון כבר בגודל שלו) מציירת אותו
                try {
                    java.lang.reflect.Method m = b.getData().getClass().getDeclaredMethod("rebuildTree");
                    m.setAccessible(true);
                    m.invoke(b.getData());
                } catch (Exception e) {
                    out.println("ERROR rebuild " + e);
                }
                return null;
            });
            sleep(800);
            out.println("BANK-DIAG-REBUILT " + bankDiag(b));
            shotShell(bank, "6c-bank-after-rebuild.png");
            ui(() -> { b.close(); return null; });
            sleep(800);
            // פתיחה שנייה של המאגר — כבר מחוברים, בלי מסך כניסה (אותו מסלול של כפתור "מאגר תרגילים")
            display.asyncExec(() -> {
                try {
                    PlatformUI.getWorkbench().getService(org.eclipse.ui.handlers.IHandlerService.class).executeCommand("il.lamerhav.dsviz.bank", null);
                } catch (Exception e) {
                    out.println("ERROR command2 " + e);
                }
            });
            Shell b2 = waitBank(15000);
            if (b2 != null) {
                sleep(3000);
                out.println("BANK2-DIAG " + bankDiag(b2));
                shotShell(b2, "7-bank-second-open.png");
                ui(() -> { b2.close(); return null; });
            }
        }
        out.println("GATE-CLOSED " + (waitGate(1500) == null));
    }

    // ------------------------------------------------------------------ Swing

    private Window waitGate(long ms) throws Exception {
        long end = System.currentTimeMillis() + ms;
        while (System.currentTimeMillis() < end) {
            for (Window x : Window.getWindows()) if (x.isShowing() && x instanceof java.awt.Dialog && TITLE.equals(((java.awt.Dialog) x).getTitle())) return x;
            sleep(150);
        }
        return null;
    }

    private static String visibleText(Component c) {
        if (c == null || !c.isShowing()) return "";
        StringBuilder b = new StringBuilder();
        if (c instanceof JLabel) b.append(((JLabel) c).getText()).append('\n');
        if (c instanceof AbstractButton) b.append("[").append(((AbstractButton) c).getText()).append("]\n");
        if (c instanceof Container) for (Component k : ((Container) c).getComponents()) b.append(visibleText(k));
        return b.toString();
    }

    private static JLabel findLabel(Component c, String part) {
        if (c instanceof JLabel && c.isShowing() && ((JLabel) c).getText() != null && ((JLabel) c).getText().contains(part)) return (JLabel) c;
        if (c instanceof Container) for (Component k : ((Container) c).getComponents()) {
            JLabel r = findLabel(k, part);
            if (r != null) return r;
        }
        return null;
    }

    /** האם מרכז התווית בחצי הימני של החלון (בקואורדינטות המסך) */
    private static boolean rightHalf(Window w, String part) {
        JLabel l = findLabel(w, part);
        if (l == null) return false;
        int cx = l.getLocationOnScreen().x + l.getWidth() / 2;
        return cx > w.getLocationOnScreen().x + w.getWidth() / 2;
    }

    private static String oneLine(String s) {
        return s.replaceAll("<[^>]*>", "").replaceAll("\\s+", " ").trim();
    }

    private static void buttons(Component c, List<String> out) {
        if (c instanceof AbstractButton && c.isShowing()) out.add(((AbstractButton) c).getText());
        if (c instanceof Container) for (Component k : ((Container) c).getComponents()) buttons(k, out);
    }

    private static String buttons(Component c) {
        List<String> l = new ArrayList<>();
        buttons(c, l);
        return String.join(" | ", l);
    }

    private static AbstractButton find(Component c, String label) {
        if (c instanceof AbstractButton && c.isShowing() && ((AbstractButton) c).getText() != null && ((AbstractButton) c).getText().contains(label)) return (AbstractButton) c;
        if (c instanceof Container) for (Component k : ((Container) c).getComponents()) {
            AbstractButton r = find(k, label);
            if (r != null) return r;
        }
        return null;
    }

    private boolean waitText(Window w, String s, long ms) throws Exception {
        long end = System.currentTimeMillis() + ms;
        while (System.currentTimeMillis() < end) {
            if (visibleText(w).contains(s)) return true;
            sleep(150);
        }
        return false;
    }

    private void click(Window w, String label) throws Exception {
        AbstractButton b = null;
        long end = System.currentTimeMillis() + 8000;
        while ((b = find(w, label)) == null && System.currentTimeMillis() < end) sleep(100);
        if (b == null) throw new IllegalStateException("אין כפתור: " + label + "\n" + visibleText(w));
        final AbstractButton fb = b;
        SwingUtilities.invokeAndWait(fb::doClick);   // הלחיצה האמיתית של הכפתור (אותם מאזינים כמו עכבר)
        out.println("CLICK " + label);
        sleep(300);
    }

    /** כל המסך (Robot) — כמו שהמשתמש רואה: החלון מעל Eclipse */
    /** מצב הפקודה "עמדת ניהול" (הכפתור בסרגל): מוגדרת, מטופלת, פעילה */
    private String teacherBtn() throws Exception {
        return ui(() -> {
            org.eclipse.core.commands.Command c = PlatformUI.getWorkbench().getService(org.eclipse.ui.commands.ICommandService.class)
                    .getCommand("il.lamerhav.dsviz.teacherPanel");
            return "defined=" + c.isDefined() + " handled=" + c.isHandled() + " enabled=" + c.isEnabled();
        });
    }

    private void screen(String name) throws Exception {
        sleep(300);
        java.awt.Dimension d = Toolkit.getDefaultToolkit().getScreenSize();
        BufferedImage img = robot.createScreenCapture(new java.awt.Rectangle(d));
        ImageIO.write(img, "png", new File(shots, name + ".png"));
    }

    /** רק החלון של הכניסה, בציור ישיר */
    private void window(Window w, String name) throws Exception {
        final BufferedImage[] img = new BufferedImage[1];
        SwingUtilities.invokeAndWait(() -> {
            img[0] = new BufferedImage(w.getWidth(), w.getHeight(), BufferedImage.TYPE_INT_RGB);
            w.paint(img[0].getGraphics());
        });
        ImageIO.write(img[0], "png", new File(shots, name + ".png"));
    }

    // ------------------------------------------------------------------ SWT

    private Shell waitBank(long ms) throws Exception {
        long end = System.currentTimeMillis() + ms;
        while (System.currentTimeMillis() < end) {
            Shell s = ui(() -> {
                for (Shell x : display.getShells()) if (!x.isDisposed() && x.isVisible() && x.getText().contains("מאגר תרגילים")) return x;
                return null;
            });
            if (s != null) return s;
            sleep(200);
        }
        return null;
    }

    private String bankDiag(Shell b) throws Exception {
        return ui(() -> {
            try {
                Object d = b.getData();
                java.lang.reflect.Field f = d.getClass().getDeclaredField("topicRows");
                f.setAccessible(true);
                java.util.List<org.eclipse.swt.widgets.Control> rows = (java.util.List<org.eclipse.swt.widgets.Control>) f.get(d);
                f = d.getClass().getDeclaredField("treeScroll");
                f.setAccessible(true);
                org.eclipse.swt.custom.ScrolledComposite sc = (org.eclipse.swt.custom.ScrolledComposite) f.get(d);
                return "rows=" + rows.size() + " first=" + rows.get(0).getBounds() + " scroll=" + sc.getBounds() + " content=" + sc.getContent().getBounds();
            } catch (Exception e) {
                return "? " + e;
            }
        });
    }

    private void shotShell(Shell s, String name) throws Exception {
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

    private static String waitFile(File f, long ms) throws Exception {
        long end = System.currentTimeMillis() + ms;
        while (System.currentTimeMillis() < end) {
            if (f.isFile() && f.length() > 0) return new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
            sleep(200);
        }
        return null;
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
