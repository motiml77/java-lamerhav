package il.lamerhav.dsviz.it;

import java.io.File;
import java.io.PrintStream;
import java.nio.file.Files;
import java.util.concurrent.Callable;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IncrementalProjectBuilder;
import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.eclipse.core.runtime.preferences.InstanceScope;
import org.eclipse.equinox.app.IApplication;
import org.eclipse.equinox.app.IApplicationContext;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.application.WorkbenchAdvisor;
import org.eclipse.ui.ide.IDE;

/**
 * הרצה ויזואלית של הורשה ופולימורפיזם בתוך Eclipse אמיתי, כמו תלמיד: פרויקט Java חדש, כל מחלקה בקובץ משלה
 * (Animal / Dog / Cat / Person / Collar ו-Pets עם main), הקובץ של main פתוח בעורך, ולחיצה על "הרצה ויזואלית"
 * (הפקודה il.lamerhav.dsviz.run — אותו handler כמו הכפתור בסרגל). חלון ההרצה הוא תהליך Java נפרד: את הצעדים והצילומים
 * שלו עושה poly.sh (XTest + import). הבדיקה מחכה ל-done.flag בתיקיית הצילומים, ואז סוגרת את Eclipse.
 *   -application il.lamerhav.dsviz.it.poly <out.txt> <תיקיית קבצי המקור> <תיקיית הצילומים>
 */
public class PolyIt implements IApplication {
    private PrintStream out;
    private Display display;

    @Override
    public Object start(IApplicationContext context) throws Exception {
        final String[] a = (String[]) context.getArguments().get(IApplicationContext.APPLICATION_ARGS);
        out = new PrintStream(new File(a[0]), "UTF-8");
        final File srcDir = new File(a[1]), shots = new File(a[2]);
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
                        Thread.sleep(2500);
                        run(srcDir, shots);
                    } catch (Throwable e) {
                        e.printStackTrace(out);
                        out.println("ERROR " + e);
                    }
                    out.println("DONE");
                    out.close();
                    display.asyncExec(() -> PlatformUI.getWorkbench().close());
                }, "poly-it");
                t.setDaemon(true);
                t.start();
            }
        });
        return IApplication.EXIT_OK;
    }

    private void run(File srcDir, File shots) throws Exception {
        ui(() -> {
            PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell().setBounds(0, 0, 1600, 1000);
            org.eclipse.ui.intro.IIntroPart intro = PlatformUI.getWorkbench().getIntroManager().getIntro();
            if (intro != null) PlatformUI.getWorkbench().getIntroManager().closeIntro(intro);
            return null;
        });
        IJavaProject jp = il.lamerhav.dsviz.ProjectSetup.createProject("Pets");
        IFolder src = jp.getProject().getFolder("src");
        if (!src.exists()) src.create(true, true, null);
        IFile main = null;
        File[] files = srcDir.listFiles((d, n) -> n.endsWith(".java"));
        java.util.Arrays.sort(files);
        for (File f : files) {
            IFile dst = src.getFile(f.getName());
            if (dst.exists()) dst.delete(true, null);
            dst.create(new java.io.ByteArrayInputStream(Files.readAllBytes(f.toPath())), true, null);
            dst.setCharset("UTF-8", null);
            if (f.getName().equals("Pets.java")) main = dst;
        }
        out.println("FILES " + files.length);
        jp.getProject().build(IncrementalProjectBuilder.FULL_BUILD, null);
        int errors = 0;
        for (IMarker m : jp.getProject().findMarkers(IMarker.PROBLEM, true, IResource.DEPTH_INFINITE))
            if (m.getAttribute(IMarker.SEVERITY, 0) == IMarker.SEVERITY_ERROR) {
                errors++;
                out.println("PROBLEM " + m.getResource().getName() + ":" + m.getAttribute(IMarker.LINE_NUMBER, 0) + " " + m.getAttribute(IMarker.MESSAGE, ""));
            }
        out.println("BUILD errors=" + errors);
        final IFile mf = main;
        ui(() -> {
            PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage().activate(
                    IDE.openEditor(PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage(), mf, true));
            return null;
        });
        Thread.sleep(1500);
        out.println("RUN-HANDLED " + ui(() -> PlatformUI.getWorkbench().getService(org.eclipse.ui.commands.ICommandService.class)
                .getCommand("il.lamerhav.dsviz.run").isHandled()));
        out.flush();
        new File(shots, "editor.flag").createNewFile();   // poly.sh: צילום של Eclipse עם הקובץ הפתוח, לפני ההרצה
        Thread.sleep(2500);
        display.asyncExec(() -> {
            try {
                PlatformUI.getWorkbench().getService(org.eclipse.ui.handlers.IHandlerService.class).executeCommand("il.lamerhav.dsviz.run", null);
                out.println("RUN-SENT");
            } catch (Exception e) {
                out.println("ERROR command " + e);
            }
            out.flush();
        });
        File done = new File(shots, "done.flag");
        for (int i = 0; i < 600 && !done.exists(); i++) Thread.sleep(500);
        out.println("FLAG " + done.exists());
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

    @Override
    public void stop() {
    }
}
