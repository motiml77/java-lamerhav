package il.lamerhav.dsviz.it;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectDescription;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IncrementalProjectBuilder;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.eclipse.core.runtime.preferences.InstanceScope;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.ILaunch;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.ILaunchConfigurationType;
import org.eclipse.debug.core.ILaunchConfigurationWorkingCopy;
import org.eclipse.debug.core.ILaunchManager;
import org.eclipse.debug.core.model.IProcess;
import org.eclipse.equinox.app.IApplication;
import org.eclipse.equinox.app.IApplicationContext;
import org.eclipse.jdt.core.IClasspathAttribute;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaModelMarker;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.ImageLoader;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.application.WorkbenchAdvisor;

/**
 * באג של תלמידה (Windows, Eclipse עם Java 21): פרויקט "ayala" מהאשף עם "Create module-info.java" (ברירת המחדל), ומחלקה Targil1 בלי
 * package. ההרצה הוויזואלית עובדת, אבל Run As ← Java Application נופל: "FindException: Error reading module: ...\ayala\bin ...
 * Targil1.class found in top-level directory (unnamed package not allowed in module)". DSViz מוחק module-info "ריק" (ModuleFix).
 * שני שלבים, על אותה סביבת עבודה (module.sh):
 *   before — DSViz כבוי: יוצרים את הפרויקטים ותצורת הרצה עם שם המודול (כמו ש-Eclipse יוצר), ומריצים ב-Run הרגיל — FindException.
 *            בסוף מדליקים את DSViz בהעדפות (בלי להפעיל אותו) — כדי שהתיקון יקרה בהפעלה הבאה של Eclipse.
 *   after  — Eclipse עולה עם DSViz דולק: בהפעלה module-info נמחק, הספרייה ב-classpath, תצורת ההרצה בלי שם המודול, Run מדפיס;
 *            module-info עם exports — לא נמחק (הסבר פעם אחת); פרויקט מודולרי שכל המחלקות שלו בחבילות — לא נוגעים.
 *            ואחר כך: קובץ חדש בלי package בפרויקט מודולרי (עם requires dsviz) — מתוקן מיד; ו-DSViz כבוי — לא נוגע.
 *   java -jar launcher.jar -application il.lamerhav.dsviz.it.module <before|after> <out.txt> <shots-dir>
 */
public class ModuleFix implements IApplication {
    private static final String NODE = "il.lamerhav.dsviz";
    // מאפייני התצורה של JDT (IJavaLaunchConfigurationConstants — כמחרוזות, כדי לא להוסיף את org.eclipse.jdt.launching לתוסף הבדיקה)
    private static final String JAVA_APP = "org.eclipse.jdt.launching.localJavaApplication";
    private static final String PROJECT = "org.eclipse.jdt.launching.PROJECT_ATTR";
    private static final String MAIN = "org.eclipse.jdt.launching.MAIN_TYPE";
    private static final String MODULE = "org.eclipse.jdt.launching.MODULE_NAME";

    private PrintStream out;
    private Display display;
    private File shots;
    private final java.util.Map<IProcess, StringBuffer> outputs = new java.util.concurrent.ConcurrentHashMap<IProcess, StringBuffer>();
    /** כל ההודעות בפינה (Toast) שהופיעו, לפי הסדר — אחת לכל חלון: "kind | rtl | text" */
    private final List<String> toasts = Collections.synchronizedList(new ArrayList<String>());
    private final java.util.Set<Shell> seenToasts = new java.util.HashSet<Shell>();
    /** מכאן והלאה — ההודעות של השלב הנוכחי בבדיקה */
    private int mark;

    @Override
    public Object start(IApplicationContext context) throws Exception {
        final String[] a = (String[]) context.getArguments().get(IApplicationContext.APPLICATION_ARGS);
        final boolean before = "before".equals(a[0]);
        out = new PrintStream(new File(a[1]), "UTF-8");
        shots = new File(a[2]);
        shots.mkdirs();
        IEclipsePreferences p = InstanceScope.INSTANCE.getNode(NODE);
        p.putBoolean("setupDone", true);   // בלי "ברוכים הבאים" (חלון מודאלי)
        if (before) p.putBoolean("enabled", false);   // DSViz כבוי — כמו Eclipse בלי התוסף: רואים את הבאג עצמו
        p.flush();
        // "Errors exist in required project(s) ... Proceed with launch?" — כמו תלמידה שלוחצת Proceed (בלי חלון שמחכה ללחיצה).
        // ב-Eclipse החדש, מחלקה בלי package במודול היא גם שגיאת קומפילציה ("Must declare a named package ...")
        IEclipsePreferences dbg = InstanceScope.INSTANCE.getNode("org.eclipse.debug.ui");
        dbg.put("org.eclipse.debug.ui.cancel_launch_with_compile_errors", "always");
        dbg.flush();
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
                watchToasts();
                Thread t = new Thread(() -> {
                    try {
                        ui(() -> {
                            PlatformUI.getWorkbench().getWorkbenchWindows()[0].getShell().setBounds(0, 0, 1400, 900);
                            return null;
                        });
                        if (before) before();
                        else after();
                    } catch (Throwable e) {
                        e.printStackTrace(out);
                    }
                    out.println("DONE");
                    out.close();
                    display.asyncExec(() -> PlatformUI.getWorkbench().close());
                }, "module-it");
                t.setDaemon(true);
                t.start();
            }
        });
        return IApplication.EXIT_OK;
    }

    // ================================================================== שלב 1: הבאג (DSViz כבוי)

    private void before() throws Exception {
        sleep(2500);
        out.println("POWER " + il.lamerhav.dsviz.Power.on());
        // הפרויקט של התלמידה: module-info "ריק" + Targil1 בלי package, ותצורת הרצה Targil1 עם שם המודול
        IJavaProject ayala = project("ayala");
        file(src(ayala), "module-info.java", "module ayala {\n}\n");
        file(src(ayala), "Targil1.java", "public class Targil1 {\n\n    public static void main(String[] args) {\n"
                + "        System.out.println(\"Targil1 runs: \" + (6 * 7));\n    }\n}\n");
        config("Targil1", "ayala", "Targil1", "ayala");
        // module-info עם exports (לא "ריק") + מחלקה בלי package — לא נמחק
        IJavaProject exporter = project("exporter");
        file(src(exporter), "module-info.java", "module exporter {\n    exports something;\n}\n");
        file(folder(src(exporter), "something"), "Lib.java", "package something;\n\npublic class Lib {\n    public static int value() {\n        return 5;\n    }\n}\n");
        file(src(exporter), "Main2.java", "public class Main2 {\n    public static void main(String[] args) {\n        System.out.println(\"Main2 \" + something.Lib.value());\n    }\n}\n");
        // פרויקט מודולרי תקין: כל המחלקות בחבילות — לא נוגעים
        IJavaProject pkgonly = project("pkgonly");
        file(src(pkgonly), "module-info.java", "module pkgonly {\n}\n");
        file(folder(src(pkgonly), "app"), "Hello.java", "package app;\n\npublic class Hello {\n    public static void main(String[] args) {\n"
                + "        System.out.println(\"pkgonly runs\");\n    }\n}\n");
        config("Hello", "pkgonly", "app.Hello", "pkgonly");
        buildAll();
        out.println("BEFORE-ERRORS ayala=" + errors(ayala.getProject()) + " exporter=" + errors(exporter.getProject()) + " pkgonly=" + errors(pkgonly.getProject()));
        out.println("BEFORE-MODULAR ayala=" + modular(ayala) + " exporter=" + modular(exporter) + " pkgonly=" + modular(pkgonly));
        launchReport("BEFORE-LAUNCH ayala", "Targil1");
        launchReport("BEFORE-LAUNCH pkgonly", "Hello");
        sleep(3000);
        out.println("BEFORE-OFF-UNTOUCHED module-info=" + src(ayala).getFile(new Path("module-info.java")).exists() + " toasts=" + toasts.size());
        // DSViz דולק בהפעלה הבאה (רק ההעדפה — בלי להפעיל עכשיו)
        IEclipsePreferences p = InstanceScope.INSTANCE.getNode(NODE);
        p.putBoolean("enabled", true);
        p.flush();
    }

    // ================================================================== שלב 2: Eclipse עולה עם DSViz

    private void after() throws Exception {
        out.println("POWER " + il.lamerhav.dsviz.Power.on());
        IJavaProject ayala = JavaCore.create(root().getProject("ayala"));
        IJavaProject exporter = JavaCore.create(root().getProject("exporter"));
        IJavaProject pkgonly = JavaCore.create(root().getProject("pkgonly"));
        IFile info = src(ayala).getFile(new Path("module-info.java"));
        long t0 = System.currentTimeMillis();
        waitFor(() -> !info.exists(), 60000);
        out.println("STARTUP-REMOVED ayala module-info=" + info.exists() + " after=" + (System.currentTimeMillis() - t0) / 1000 + "s");
        // הסבב הבא של LibraryEverywhere: הספרייה נכנסת לפרויקט — ב-classpath הרגיל
        waitFor(() -> !"missing".equals(lib(ayala)), 30000);
        waitIdle();
        out.println("AYALA module-info=" + info.exists() + " history=" + info.getHistory(null).length + " modular=" + modular(ayala) + " lib=" + lib(ayala));
        out.println("AYALA-HISTORY " + lastState(info));
        out.println("AYALA-CONFIG " + configReport("Targil1"));
        buildAll();
        out.println("AYALA-ERRORS " + errors(ayala.getProject()));
        launchReport("AFTER-LAUNCH ayala", "Targil1");

        // exporter: module-info עם exports — נשאר; pkgonly: מודולרי וכל המחלקות בחבילות — נשאר, ו-Run שלו עדיין עובד
        IFile einfo = src(exporter).getFile(new Path("module-info.java"));
        out.println("EXPORTER module-info=" + einfo.exists() + " exports=" + (einfo.exists() && text(einfo).contains("exports something;")) + " modular=" + modular(exporter));
        IFile pinfo = src(pkgonly).getFile(new Path("module-info.java"));
        out.println("PKGONLY module-info=" + pinfo.exists() + " modular=" + modular(pkgonly) + " lib=" + lib(pkgonly));
        out.println("PKGONLY-CONFIG " + configReport("Hello"));
        launchReport("AFTER-LAUNCH pkgonly", "Hello");
        sleep(1500);
        shotWindow("module-toast-startup.png");
        reportToasts("STARTUP");

        // ההסבר על exporter — פעם אחת בלבד: עוד קובץ בלי package בפרויקט הזה לא מקפיץ אותו שוב
        file(src(exporter), "Main3.java", "public class Main3 {\n    public static void main(String[] args) {\n    }\n}\n");
        sleep(6000);
        int exporterToasts = 0;
        synchronized (toasts) {
            for (String t : toasts) if (t.contains("exporter")) exporterToasts++;
        }
        out.println("EXPORTER-TOASTS " + exporterToasts + " module-info=" + einfo.exists());

        // קובץ חדש בלי package בפרויקט מודולרי שכבר הוכן (lib ב-module path + requires dsviz) — מתוקן מיד, ו-Stack מהספרייה מתקמפל ורץ
        mark = toasts.size();
        IJavaProject beni = project("beni");
        file(src(beni), "module-info.java", "module beni {\n}\n");
        file(folder(src(beni), "beni"), "Start.java", "package beni;\n\npublic class Start {\n}\n");
        IFile binfo = src(beni).getFile(new Path("module-info.java"));
        waitFor(() -> binfo.exists() && text(binfo).contains("requires dsviz") && "module".equals(lib(beni)), 30000);
        out.println("BENI-PREPARED module-info=" + binfo.exists() + " requires-dsviz=" + (binfo.exists() && text(binfo).contains("requires dsviz;")) + " lib=" + lib(beni));
        config("Targil2", "beni", "Targil2", "beni");
        file(src(beni), "Targil2.java", "import unit4.collectionsLib.*;\n\npublic class Targil2 {\n    public static void main(String[] args) {\n"
                + "        Stack<Integer> s = new Stack<Integer>();\n        s.push(7);\n        s.push(9);\n"
                + "        System.out.println(\"Targil2 stack: \" + s.pop() + s.pop());\n    }\n}\n");
        t0 = System.currentTimeMillis();
        waitFor(() -> !binfo.exists(), 30000);
        out.println("NEWFILE-REMOVED beni module-info=" + binfo.exists() + " after=" + (System.currentTimeMillis() - t0) / 1000 + "s");
        waitFor(() -> "plain".equals(lib(beni)), 20000);
        waitIdle();
        out.println("BENI module-info=" + binfo.exists() + " history=" + binfo.getHistory(null).length + " modular=" + modular(beni) + " lib=" + lib(beni));
        out.println("BENI-HISTORY " + lastState(binfo));
        out.println("BENI-CONFIG " + configReport("Targil2"));
        buildAll();
        out.println("BENI-ERRORS " + errors(beni.getProject()));
        launchReport("AFTER-LAUNCH beni", "Targil2");
        sleep(1000);
        shotWindow("module-toast-newfile.png");
        reportToasts("NEWFILE");

        // DSViz כבוי (לחצן הכיבוי): לא נוגעים; מפעילים שוב — מתוקן
        mark = toasts.size();
        ui(() -> {
            il.lamerhav.dsviz.PowerHandler.apply(false);
            return null;
        });
        IJavaProject dana = project("dana");
        file(src(dana), "module-info.java", "module dana {\n}\n");
        file(src(dana), "Targil3.java", "public class Targil3 {\n    public static void main(String[] args) {\n        System.out.println(\"Targil3\");\n    }\n}\n");
        IFile dinfo = src(dana).getFile(new Path("module-info.java"));
        sleep(6000);
        out.println("POWER-OFF dana module-info=" + dinfo.exists() + " toasts=" + (toasts.size() - mark));
        ui(() -> {
            il.lamerhav.dsviz.PowerHandler.apply(true);
            return null;
        });
        waitFor(() -> !dinfo.exists(), 30000);
        sleep(1500);
        out.println("POWER-ON dana module-info=" + dinfo.exists());
        reportToasts("POWERON");
    }

    // ------------------------------------------------------------------ פרויקטים

    /** פרויקט Java כמו שהאשף יוצר: src, bin, JRE System Library ב-module path, Java 21 */
    private IJavaProject project(String name) throws Exception {
        return ui(() -> {
            IProject p = root().getProject(name);
            if (!p.exists()) p.create(null);
            p.open(null);
            IProjectDescription d = p.getDescription();
            d.setNatureIds(new String[] { JavaCore.NATURE_ID });
            p.setDescription(d, null);
            p.setDefaultCharset("UTF-8", null);
            IFolder s = p.getFolder("src");
            if (!s.exists()) s.create(true, true, null);
            IJavaProject jp = JavaCore.create(p);
            jp.setRawClasspath(new IClasspathEntry[] { JavaCore.newSourceEntry(s.getFullPath()),
                    JavaCore.newContainerEntry(new Path("org.eclipse.jdt.launching.JRE_CONTAINER"), new org.eclipse.jdt.core.IAccessRule[0],
                            new IClasspathAttribute[] { JavaCore.newClasspathAttribute("module", "true") }, false) },
                    p.getFolder("bin").getFullPath(), null);
            jp.setOption(JavaCore.COMPILER_COMPLIANCE, "21");
            jp.setOption(JavaCore.COMPILER_SOURCE, "21");
            jp.setOption(JavaCore.COMPILER_CODEGEN_TARGET_PLATFORM, "21");
            return jp;
        });
    }

    private static org.eclipse.core.resources.IWorkspaceRoot root() {
        return ResourcesPlugin.getWorkspace().getRoot();
    }

    private static IFolder src(IJavaProject jp) {
        return jp.getProject().getFolder("src");
    }

    private IFolder folder(IFolder parent, String name) throws Exception {
        return ui(() -> {
            IFolder f = parent.getFolder(name);
            if (!f.exists()) f.create(true, true, null);
            return f;
        });
    }

    private IFile file(IContainer c, String name, String content) throws Exception {
        return ui(() -> {
            IFile f = c.getFile(new Path(name));
            ByteArrayInputStream in = new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
            if (f.exists()) f.setContents(in, true, false, null);
            else f.create(in, true, null);
            return f;
        });
    }

    /** תצורת הרצה רגילה (Java Application) כמו ש-Eclipse יוצר ב-Run As בפרויקט מודולרי: פרויקט, מחלקה ושם המודול */
    private void config(String name, String project, String main, String module) throws Exception {
        ILaunchManager mgr = DebugPlugin.getDefault().getLaunchManager();
        ILaunchConfigurationType type = mgr.getLaunchConfigurationType(JAVA_APP);
        for (ILaunchConfiguration c : mgr.getLaunchConfigurations(type)) if (c.getName().equals(name)) c.delete();
        ILaunchConfigurationWorkingCopy wc = type.newInstance(null, name);
        wc.setAttribute(PROJECT, project);
        wc.setAttribute(MAIN, main);
        wc.setAttribute(MODULE, module);
        wc.doSave();
    }

    private static ILaunchConfiguration findConfig(String name) throws Exception {
        ILaunchManager mgr = DebugPlugin.getDefault().getLaunchManager();
        for (ILaunchConfiguration c : mgr.getLaunchConfigurations(mgr.getLaunchConfigurationType(JAVA_APP))) if (c.getName().equals(name)) return c;
        return null;
    }

    /** "module-name=ayala others=[...]" — שם המודול, וכל מאפיין של מודולים שנשאר בתצורה */
    private static String configReport(String name) throws Exception {
        ILaunchConfiguration c = findConfig(name);
        if (c == null) return "missing";
        List<String> mod = new ArrayList<String>();
        for (Object k : c.getAttributes().keySet()) if (String.valueOf(k).toUpperCase().contains("MODULE")) mod.add(String.valueOf(k));
        Collections.sort(mod);
        return "module-name=" + c.getAttribute(MODULE, "none") + " module-attrs=" + mod;
    }

    /** Run רגיל של התצורה (DebugPlugin, RUN_MODE — כמו Run As ← Java Application), עד שהתוכנית מסתיימת */
    private void launchReport(String tag, String configName) throws Exception {
        ILaunchConfiguration c = findConfig(configName);
        if (c == null) {
            out.println(tag + " no-config");
            return;
        }
        ILaunch launch;
        try {
            launch = c.launch(ILaunchManager.RUN_MODE, new NullProgressMonitor(), true);
        } catch (Exception e) {
            out.println(tag + " launch-failed " + e);
            return;
        }
        long end = System.currentTimeMillis() + 60000;
        while (!launch.isTerminated() && System.currentTimeMillis() < end) sleep(200);
        sleep(500);
        IProcess[] ps = launch.getProcesses();
        if (ps.length == 0) {
            out.println(tag + " no-process");
            return;
        }
        IProcess p = ps[0];
        StringBuffer b = outputs.get(p);
        String text = b != null ? b.toString() : p.getStreamsProxy().getOutputStreamMonitor().getContents() + p.getStreamsProxy().getErrorStreamMonitor().getContents();
        text = text.replaceAll("(?m)^Picked up (JAVA_TOOL_OPTIONS|_JAVA_OPTIONS|JDK_JAVA_OPTIONS):.*(\r?\n)?", "");   // הודעה של ה-JVM על משתנה סביבה במחשב הבדיקה
        String cmd = String.valueOf(p.getAttribute(IProcess.ATTR_CMDLINE));
        out.println(tag + " terminated=" + launch.isTerminated() + " exit=" + (p.isTerminated() ? String.valueOf(p.getExitValue()) : "?")
                + " findException=" + text.contains("FindException") + " module-path=" + (cmd.contains("--module-path") || cmd.contains(" -p "))
                + " out=" + text.trim().replace("\r", "").replace("\n", " | "));
        out.println(tag + "-CMDLINE " + cmd.replaceAll("\\S*/jre/bin/java", "java"));
    }

    private void buildAll() throws Exception {
        ResourcesPlugin.getWorkspace().build(IncrementalProjectBuilder.FULL_BUILD, null);
        waitIdle();
    }

    private static void waitIdle() throws Exception {
        Job.getJobManager().join(ResourcesPlugin.FAMILY_AUTO_BUILD, null);
        sleep(800);
        Job.getJobManager().join(ResourcesPlugin.FAMILY_AUTO_BUILD, null);
    }

    /** שגיאות קומפילציה בפרויקט: "[]" או "[Targil.java:3 ...]" */
    private static String errors(IProject p) throws Exception {
        List<String> errs = new ArrayList<String>();
        for (IMarker m : p.findMarkers(IJavaModelMarker.JAVA_MODEL_PROBLEM_MARKER, true, IResource.DEPTH_INFINITE))
            if (m.getAttribute(IMarker.SEVERITY, 0) == IMarker.SEVERITY_ERROR)
                errs.add(m.getResource().getName() + ":" + m.getAttribute(IMarker.LINE_NUMBER, 0) + " " + m.getAttribute(IMarker.MESSAGE, ""));
        return errs.toString();
    }

    private static boolean modular(IJavaProject jp) {
        try {
            return jp.getModuleDescription() != null;
        } catch (Exception e) {
            return false;
        }
    }

    /** lib/dsviz.jar ב-Build Path: "plain" (classpath), "module" (module path), או "missing" */
    private static String lib(IJavaProject jp) {
        try {
            for (IClasspathEntry e : jp.getRawClasspath()) {
                if (e.getEntryKind() != IClasspathEntry.CPE_LIBRARY || !e.getPath().toString().endsWith("/lib/dsviz.jar")) continue;
                for (IClasspathAttribute a : e.getExtraAttributes()) if ("module".equals(a.getName()) && "true".equals(a.getValue())) return "module";
                return "plain";
            }
        } catch (Exception ignored) {
            // אין classpath
        }
        return "missing";
    }

    private static String text(IFile f) {
        try (InputStream in = f.getContents(true)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "";
        }
    }

    /** התוכן האחרון של הקובץ ב-Local History (מה שהתלמיד מקבל ב-Restore from Local History), בשורה אחת */
    private static String lastState(IFile f) {
        try {
            org.eclipse.core.resources.IFileState[] h = f.getHistory(null);
            if (h.length == 0) return "none";
            try (InputStream in = h[0].getContents()) {
                return new String(in.readAllBytes(), StandardCharsets.UTF_8).trim().replace("\n", " ").replace("\t", " ");
            }
        } catch (Exception e) {
            return "error " + e;
        }
    }

    // ------------------------------------------------------------------ ההודעות בפינה

    /** כל 200ms: ההודעות בפינה (Toast — shell.getData("dsviz.toast")) שמוצגות עכשיו נרשמות (בלי כפילויות) */
    private void watchToasts() {
        display.timerExec(200, new Runnable() {
            @Override
            public void run() {
                if (display.isDisposed()) return;
                for (Shell s : display.getShells()) {
                    Object kind = s.isDisposed() || !s.isVisible() ? null : s.getData("dsviz.toast");
                    if (kind == null || !seenToasts.add(s)) continue;
                    toasts.add(kind + " | rtl=" + ((s.getStyle() & org.eclipse.swt.SWT.RIGHT_TO_LEFT) != 0) + " | " + labels(s));
                }
                display.timerExec(200, this);
            }
        });
    }

    private void reportToasts(String tag) {
        synchronized (toasts) {
            out.println("TOASTS-" + tag + " " + (toasts.size() - mark));
            for (String t : toasts.subList(mark, toasts.size())) out.println("TOAST-" + tag + " " + t.replace("\n", " / "));
        }
    }

    private static String labels(Composite c) {
        StringBuilder b = new StringBuilder();
        for (Control k : c.getChildren()) {
            if (k instanceof Label && k.isVisible()) {
                String t = ((Label) k).getText();
                // \u05D1\u05DC\u05D9 \u05EA\u05D5\u05D5\u05D9 \u05D4\u05DB\u05D9\u05D5\u05D5\u05E0\u05D9\u05D5\u05EA; \u05D4\u05DE\u05E7\u05E3 \u05E9\u05DC\u05D0 \u05E0\u05E9\u05D1\u05E8 \u05E9\u05DC RtlText ("\u05DE\u2011Local") \u2014 \u05DE\u05E7\u05E3 \u05E8\u05D2\u05D9\u05DC
                if (t != null && !t.trim().isEmpty())
                    b.append(b.length() > 0 ? " / " : "").append(t.replaceAll("[\u200E\u200F\u202A-\u202E\u2066-\u2069]", "").replace('\u2011', '-'));
            }
            if (k instanceof Composite) {
                String inner = labels((Composite) k);
                if (!inner.isEmpty()) b.append(b.length() > 0 ? " / " : "").append(inner);
            }
        }
        return b.toString();
    }

    private void shotWindow(String name) throws Exception {
        ui(() -> {
            Rectangle r = PlatformUI.getWorkbench().getWorkbenchWindows()[0].getShell().getBounds();
            Image img = new Image(display, r.width, r.height);
            GC gc = new GC(display);
            gc.copyArea(img, r.x, r.y);
            gc.dispose();
            ImageLoader l = new ImageLoader();
            l.data = new org.eclipse.swt.graphics.ImageData[] { img.getImageData() };
            l.save(new File(shots, name).getAbsolutePath(), org.eclipse.swt.SWT.IMAGE_PNG);
            img.dispose();
            return null;
        });
    }

    // ------------------------------------------------------------------ עזרים

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

    private interface Cond {
        boolean ok() throws Exception;
    }

    private static void waitFor(Cond c, long ms) throws Exception {
        long end = System.currentTimeMillis() + ms;
        while (System.currentTimeMillis() < end && !c.ok()) sleep(200);
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
