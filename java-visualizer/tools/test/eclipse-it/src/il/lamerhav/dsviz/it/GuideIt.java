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
 * תמונות ההדרכה למורים (worker/test/guide-e2e.mjs מפעיל דרך gate.sh עם GATE_APP=guide): Eclipse אמיתי, שלוש כניסות באותו מחשב —
 * מורה (Google) → התנתקות → תלמיד (Google + קוד המורה) → התנתקות → עמדת מחשב (קוד המורה). ואז המורה משנה את שם העמדה (בדפדפן),
 * והמאגר נפתח שוב — והשורה בתחתית מראה את השם החדש. תיאום עם הבדיקה דרך קבצים בתיקיית הצילומים:
 * url-N.txt (מהתוכנה), code.txt / renamed.flag (מהבדיקה), station.done (מהתוכנה).
 */
@SuppressWarnings("unchecked")
public class GuideIt implements IApplication {
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
                }, "guide-it");
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

        // ---------------------------------------------------------------- 1. מורה
        Window w = openGate();
        if (w == null) return;
        window(w, "e01-gate-choose");
        screen("e01-gate-choose-screen");
        clickNth(w, "כניסה עם Google", 0);
        waitFile(new File(shots, "url-1.txt"), 20000);
        sleep(600);
        window(w, "e02-gate-waiting-browser");
        out.println("TEACHER-SUCCESS " + waitText(w, "נכנסת בהצלחה", 180000));
        sleep(200);
        window(w, "e03-teacher-success");
        Shell bank = waitBank(20000);
        out.println("TEACHER-BANK " + (bank != null) + " " + label());
        if (bank != null) {
            sleep(3000);
            shotShell(bank, "e04-teacher-bank.png");
            close(bank);
        }
        waitGateClosed();
        for (int i = 0; i < 20 && !teacherBtn().contains("enabled=true"); i++) sleep(500);
        command("il.lamerhav.dsviz.teacherPanel");
        Window panel = swingDialog("עמדת ניהול", null, 10000);
        out.println("TEACHER-PANEL " + (panel != null));
        if (panel != null) {
            sleep(2500);
            out.println("TEACHER-PANEL-TEXT " + oneLine(visibleText(panel)));
            window(panel, "e05-teacher-panel");
            screen("e05-teacher-panel-screen");
            final Window p = panel;
            SwingUtilities.invokeAndWait(p::dispose);
        }
        out.println("TEACHER-SIGNOUT " + Account.signOut().status + " now=" + (Account.current() == null ? "none" : Account.current().label()));
        Files.write(new File(shots, "teacher.done").toPath(), "1".getBytes());
        sleep(1000);

        // ---------------------------------------------------------------- 2. תלמיד עם Google + קוד המורה
        w = openGate();
        if (w == null) return;
        clickNth(w, "כניסה עם Google", 1);   // הכרטיס השלישי (תלמיד) — הכפתור השני בשם הזה
        String u2 = waitFile(new File(shots, "url-2.txt"), 20000);
        out.println("STUDENT-URL student=" + (u2 != null && u2.contains("role=student")));
        sleep(600);
        window(w, "e06-student-waiting-browser");
        out.println("STUDENT-SUCCESS " + waitText(w, "נכנסת בהצלחה", 240000));
        sleep(200);
        window(w, "e07-student-success");
        bank = waitBank(20000);
        Account.Access sa = Account.current();
        out.println("STUDENT-BANK " + (bank != null) + " " + (sa == null ? "none" : sa.label() + " student=" + sa.isStudent()));
        if (bank != null) {
            sleep(3000);
            shotShell(bank, "e08-student-bank.png");
            close(bank);
        }
        waitGateClosed();
        out.println("STUDENT-SIGNOUT " + Account.signOut().status + " now=" + (Account.current() == null ? "none" : Account.current().label()));
        sleep(1000);

        // ---------------------------------------------------------------- 3. עמדת מחשב
        w = openGate();
        if (w == null) return;
        clickNth(w, "הקלדת קוד המורה", 0);
        sleep(500);
        String code = waitFile(new File(shots, "code.txt"), 5000);
        final java.util.List<javax.swing.JTextField> fields = new ArrayList<>();
        fields(w, fields);
        out.println("STATION-FIELDS " + fields.size());
        final String c = code == null ? "" : code.trim();
        SwingUtilities.invokeAndWait(() -> {
            fields.get(0).setText(c);
            fields.get(1).setText("מחשב 14");
        });
        sleep(400);
        window(w, "e09-station-form");
        clickNth(w, "חיבור המחשב", 0);
        out.println("STATION-SUCCESS " + waitText(w, "המחשב מחובר", 30000));
        sleep(200);
        window(w, "e10-station-success");
        bank = waitBank(20000);
        out.println("STATION-BANK " + (bank != null));
        if (bank != null) {
            sleep(3000);
            out.println("STATION-LINE " + stationLine(bank));
            shotShell(bank, "e11-station-bank.png");
            close(bank);
        }
        waitGateClosed();
        Files.write(new File(shots, "station.done").toPath(), "1".getBytes());

        // ---------------------------------------------------------------- 4. המורה שינה את שם העמדה → המאגר נפתח שוב עם השם החדש
        String renamed = waitFile(new File(shots, "renamed.flag"), 600000);
        out.println("RENAMED-SIGNAL " + (renamed != null ? renamed.trim() : "none"));
        command("il.lamerhav.dsviz.bank");
        bank = waitBank(20000);
        if (bank != null) {
            sleep(3000);
            out.println("STATION-LINE-RENAMED " + stationLine(bank));
            out.println("STATION-ACCESS-RENAMED " + (Account.current() == null ? "none" : Account.current().label()));
            shotShell(bank, "e12-station-bank-renamed.png");
            close(bank);
        }
    }

    private Window openGate() throws Exception {
        command("il.lamerhav.dsviz.bank");
        Window w = waitGate(25000);
        out.println("GATE-OPEN " + (w != null));
        if (w != null) sleep(800);
        return w;
    }

    private void command(String id) {
        display.asyncExec(() -> {
            try {
                PlatformUI.getWorkbench().getService(org.eclipse.ui.handlers.IHandlerService.class).executeCommand(id, null);
            } catch (Exception e) {
                out.println("ERROR command " + id + " " + e);
            }
        });
    }

    private void close(Shell s) throws Exception {
        ui(() -> { if (!s.isDisposed()) s.close(); return null; });
        sleep(800);
    }

    private void waitGateClosed() throws Exception {
        for (int i = 0; i < 30; i++) {
            if (waitGate(100) == null) return;
            sleep(200);
        }
    }

    private static String label() {
        Account.Access a = Account.current();
        return a == null ? "none" : a.label();
    }

    /** השורה בתחתית המאגר (Label עם dsviz.id=station-line) */
    private String stationLine(Shell s) throws Exception {
        return ui(() -> {
            java.util.List<org.eclipse.swt.widgets.Control> all = new ArrayList<>();
            collect(s, all);
            for (org.eclipse.swt.widgets.Control k : all)
                if ("station-line".equals(k.getData("dsviz.id")) && k instanceof org.eclipse.swt.widgets.Label) return ((org.eclipse.swt.widgets.Label) k).getText().trim();
            return "none";
        });
    }

    private static void collect(org.eclipse.swt.widgets.Control c, java.util.List<org.eclipse.swt.widgets.Control> out) {
        out.add(c);
        if (c instanceof org.eclipse.swt.widgets.Composite) for (org.eclipse.swt.widgets.Control k : ((org.eclipse.swt.widgets.Composite) c).getChildren()) collect(k, out);
    }

    private static void fields(Component c, java.util.List<javax.swing.JTextField> out) {
        if (c instanceof javax.swing.JTextField && c.isShowing()) out.add((javax.swing.JTextField) c);
        if (c instanceof Container) for (Component k : ((Container) c).getComponents()) fields(k, out);
    }

    private static void findAll(Component c, String label, java.util.List<AbstractButton> out) {
        if (c instanceof AbstractButton && c.isShowing() && ((AbstractButton) c).getText() != null && ((AbstractButton) c).getText().contains(label)) out.add((AbstractButton) c);
        if (c instanceof Container) for (Component k : ((Container) c).getComponents()) findAll(k, label, out);
    }

    private void clickNth(Window w, String label, int n) throws Exception {
        java.util.List<AbstractButton> l = new ArrayList<>();
        long end = System.currentTimeMillis() + 8000;
        while (System.currentTimeMillis() < end) {
            l.clear();
            findAll(w, label, l);
            if (l.size() > n) break;
            sleep(100);
        }
        if (l.size() <= n) throw new IllegalStateException("אין כפתור: " + label + " #" + n + "\n" + visibleText(w));
        final AbstractButton b = l.get(n);
        SwingUtilities.invokeAndWait(b::doClick);
        out.println("CLICK " + label + " #" + n);
        sleep(300);
    }

    // ------------------------------------------------------------------ Swing

    /** חלון Swing פתוח עם הכותרת (לא except) */
    private Window swingDialog(String title, Window except, long ms) throws Exception {
        long end = System.currentTimeMillis() + ms;
        while (System.currentTimeMillis() < end) {
            for (Window x : Window.getWindows())
                if (x != except && x.isShowing() && x instanceof java.awt.Dialog && title.equals(((java.awt.Dialog) x).getTitle())) { sleep(400); return x; }
            sleep(150);
        }
        return null;
    }

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
