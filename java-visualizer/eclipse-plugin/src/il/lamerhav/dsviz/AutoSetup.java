package il.lamerhav.dsviz;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectDescription;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.resources.WorkspaceJob;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.eclipse.core.runtime.preferences.InstanceScope;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.IStartup;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;

/**
 * הכנה אוטומטית בהפעלה הראשונה של Eclipse אחרי ההתקנה — בלי שהתלמיד יצטרך לעשות כלום:
 * קידוד UTF-8 לעברית, פרויקט DataStructures מוכן (זה שתוכנית ההתקנה יצרה במסמכים, או חדש),
 * פתיחת התרגיל הראשון והודעת "ברוכים הבאים". בכל הפעלה: עדכון הספרייה בפרויקטים אחרי שדרוג.
 */
public class AutoSetup implements IStartup {
    private static final String NODE = "il.lamerhav.dsviz";
    private static final String DONE = "setupDone";

    @Override
    public void earlyStartup() {
        WorkspaceJob job = new WorkspaceJob("מבני נתונים — הכנת סביבת העבודה") {
            @Override
            public IStatus runInWorkspace(IProgressMonitor monitor) {
                try {
                    updateLibraries();
                    IEclipsePreferences prefs = InstanceScope.INSTANCE.getNode(NODE);
                    if (!prefs.getBoolean(DONE, false)) {
                        IFile main = firstRun();
                        prefs.putBoolean(DONE, true);
                        prefs.flush();
                        welcome(main);
                    }
                    return Status.OK_STATUS;
                } catch (Exception e) {
                    return new Status(IStatus.WARNING, NODE, "הכנה אוטומטית נכשלה: " + e, e);
                }
            }
        };
        job.setSystem(false);
        job.schedule(1500);
    }

    /** הפעלה ראשונה: קידוד, פרויקט, קובץ פתיחה. מחזיר את Main.java לפתיחה */
    static IFile firstRun() throws Exception {
        IEclipsePreferences res = InstanceScope.INSTANCE.getNode(ResourcesPlugin.PI_RESOURCES);
        res.put(ResourcesPlugin.PREF_ENCODING, "UTF-8");
        res.flush();

        IWorkspace ws = ResourcesPlugin.getWorkspace();
        IProject project = ws.getRoot().getProject(ProjectSetup.DEFAULT_PROJECT);
        if (!project.exists()) {
            File folder = installedProjectFolder();
            if (folder != null) {
                // התיקייה שתוכנית ההתקנה הכינה (משותפת גם ל-VS Code)
                IProjectDescription d = ws.loadProjectDescription(new Path(new File(folder, ".project").getAbsolutePath()));
                project = ws.getRoot().getProject(d.getName());
                if (!project.exists()) project.create(d, null);
            } else {
                project = ProjectSetup.createProject(ProjectSetup.DEFAULT_PROJECT).getProject();
            }
        }
        if (!project.isOpen()) project.open(null);
        IJavaProject jp = JavaCore.create(project);
        IContainer src = ProjectSetup.prepare(jp, null);
        ProjectSetup.copyExamples(src);
        IFile main = src.getFile(new Path("Main.java"));
        if (!main.exists()) ProjectSetup.createFile(src, "Main.java",
                new String(ProjectSetup.resource("/resources/Main.java"), StandardCharsets.UTF_8));
        return main;
    }

    /** הנתיב שתוכנית ההתקנה רשמה ב-~/.dsviz/project-path.txt (אם יש שם פרויקט) */
    static File installedProjectFolder() {
        String path = dsvizFile("project-path.txt");
        if (path == null) return null;
        File dir = new File(path);
        return new File(dir, ".project").isFile() ? dir : null;
    }

    /** קובץ שתוכנית ההתקנה כתבה ב-~/.dsviz (Windows: UTF-16 עם BOM; Mac/Linux: UTF-8), או null */
    static String dsvizFile(String name) {
        try {
            File f = new File(System.getProperty("user.home"), ".dsviz/" + name);
            if (!f.isFile()) return null;
            byte[] b = Files.readAllBytes(f.toPath());
            boolean utf16 = b.length >= 2 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xFE;
            String text = new String(b, utf16 ? StandardCharsets.UTF_16LE : StandardCharsets.UTF_8).trim();
            if (!text.isEmpty() && text.charAt(0) == '\uFEFF') text = text.substring(1);
            return text.isEmpty() ? null : text;
        } catch (Exception e) {
            return null;
        }
    }

    /** אחרי שדרוג התוסף: lib/dsviz.jar בכל פרויקט שמשתמש בו מתעדכן לגרסה החדשה */
    static void updateLibraries() throws CoreException {
        byte[] fresh = ProjectSetup.resource("/resources/" + ProjectSetup.JAR_NAME);
        for (IJavaProject jp : ProjectSetup.javaProjects()) {
            IFile jar = jp.getProject().getFile("lib/" + ProjectSetup.JAR_NAME);
            if (!jar.exists()) continue;
            try (java.io.InputStream in = jar.getContents(true)) {
                byte[] cur = in.readAllBytes();
                if (!Arrays.equals(cur, fresh)) jar.setContents(new java.io.ByteArrayInputStream(fresh), true, false, null);
            } catch (java.io.IOException ignored) {
            }
        }
    }

    private static void welcome(final IFile main) {
        Display.getDefault().asyncExec(() -> {
            IWorkbenchWindow w = PlatformUI.getWorkbench().getActiveWorkbenchWindow();
            if (w == null) return;
            try {
                PlatformUI.getWorkbench().showPerspective("org.eclipse.jdt.ui.JavaPerspective", w);
            } catch (Exception ignored) {}
            try {
                if (main != null && main.exists()) Ui.openAt(w.getActivePage(), main, 0);
            } catch (Exception ignored) {}
            MessageDialog.openInformation(w.getShell(), "מבני נתונים — הכול מוכן",
                    "ברוכים הבאים! סביבת העבודה הוכנה אוטומטית:\n"
                    + "• פרויקט " + main.getProject().getName() + " עם תרגיל לדוגמה (Main.java) ודוגמאות פתורות.\n\n"
                    + "בתפריט העליון \"מבני נתונים\" ובסרגל הכלים:\n"
                    + "• תרגיל חדש — בוחרים מבנה (מחסנית, תור, רשימה, עץ, מערך...) ומקבלים תרגיל מוכן.\n"
                    + "• הוספה לקוד — \"צור תור\", \"צור מערך\", \"צור תלמיד\"... עם נתוני דמה, במקום הסמן.\n"
                    + "• הרצה ויזואלית / שורה אחר שורה — רואים כל צעד בחלון.");
        });
    }
}
