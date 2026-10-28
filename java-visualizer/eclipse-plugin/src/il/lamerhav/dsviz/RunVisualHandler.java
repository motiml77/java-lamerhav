package il.lamerhav.dsviz;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IncrementalProjectBuilder;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.ILaunchConfigurationType;
import org.eclipse.debug.core.ILaunchConfigurationWorkingCopy;
import org.eclipse.debug.core.ILaunchManager;
import org.eclipse.debug.ui.DebugUITools;
import org.eclipse.debug.ui.IDebugUIConstants;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.launching.IJavaLaunchConfigurationConstants;
import org.eclipse.jdt.launching.JavaRuntime;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.handlers.HandlerUtil;
import org.eclipse.ui.ide.IDE;

/**
 * "הרצה ויזואלית": שומר ומריץ את המחלקה של הקובץ הפתוח (עם main) דרך LineByLine שבספרייה —
 * החלון מראה כל שורה בקוד, את המשתנים ואת המבנים. (Run הרגיל של Eclipse מדפיס רק לקונסול.)
 */
public class RunVisualHandler extends AbstractHandler {
    private static final String VM_ARGS = "-Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -Ddsviz.visual=true";
    private static final String LINE_RUNNER = "unit4.collectionsLib.LineByLine";

    @Override
    public Object execute(ExecutionEvent event) {
        Shell shell = HandlerUtil.getActiveShell(event);
        IFile file = Ui.currentFile(event);
        if (file == null || !"java".equals(file.getFileExtension())) {
            MessageDialog.openInformation(shell, "הרצה ויזואלית", "פתחו קובץ Java (תרגיל) ואז לחצו \"הרצה ויזואלית\".");
            return null;
        }
        IDE.saveAllEditors(new IResource[] { file.getProject() }, false);
        try {
            String main = mainType(file);
            if (main == null) {
                MessageDialog.openInformation(shell, "הרצה ויזואלית", "בקובץ " + file.getName() + " אין main — אין מה להריץ.");
                return null;
            }
            // מוודאים שהספרייה בפרויקט עדכנית (LineByLine נמצא בה)
            ProjectSetup.prepare(JavaCore.create(file.getProject()), Ui.resolver(shell));
            // קודם מקמפלים ובודקים שגיאות — עם שגיאות לא מריצים (הרצה ויזואלית של קוד שלא מתקמפל נותנת תוצאות שגויות)
            List<String> errors = new ArrayList<String>();
            IMarker first = compileErrors(file, errors);
            if (!errors.isEmpty()) {
                warn(shell, event, file, first, errors);
                return null;
            }
            DebugUITools.launch(config(file.getProject().getName(), main), ILaunchManager.RUN_MODE);
        } catch (CoreException e) {
            Ui.error(shell, "לא הצלחתי להריץ.", e);
        }
        return null;
    }

    /**
     * בנייה סינכרונית של הפרויקט (גם אם הבנייה האוטומטית כבויה) ואיסוף כל השגיאות בו.
     * ההסברים בעברית (סימון המורה בעברית) מועדפים; שגיאה של Eclipse בשורה שכבר יש בה הסבר בעברית לא חוזרת.
     * מחזיר את הסימון הראשון (לפתיחה בעורך).
     */
    public static IMarker compileErrors(IFile file, List<String> out) throws CoreException {
        file.getProject().build(IncrementalProjectBuilder.INCREMENTAL_BUILD, null);
        List<IMarker> errs = new ArrayList<IMarker>();
        // בפרויקט יש הרבה תרגילים — שגיאה בתרגיל אחר לא צריכה לחסום. חוסמות: שגיאות בקובץ הזה, ובקבצים שהוא משתמש בהם (לפי שם המחלקה)
        String source = "";
        try {
            source = new String(file.getContents().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            // בלי טקסט — רק השגיאות של הקובץ עצמו
        }
        for (IMarker m : file.getProject().findMarkers(IMarker.PROBLEM, true, IResource.DEPTH_INFINITE)) {
            if (m.getAttribute(IMarker.SEVERITY, 0) != IMarker.SEVERITY_ERROR) continue;
            IResource r = m.getResource();
            boolean used = r.equals(file) || "java".equals(r.getFileExtension())
                    && java.util.regex.Pattern.compile("\\b" + java.util.regex.Pattern.quote(r.getName().replaceFirst("\\.java$", "")) + "\\b").matcher(source).find();
            if (used) errs.add(m);
        }
        // שגיאות בקובץ הפתוח קודם
        java.util.Collections.sort(errs, (a, b) -> {
            boolean fa = a.getResource().equals(file), fb = b.getResource().equals(file);
            if (fa != fb) return fa ? -1 : 1;
            return a.getAttribute(IMarker.LINE_NUMBER, 0) - b.getAttribute(IMarker.LINE_NUMBER, 0);
        });
        java.util.Set<String> hebrew = new java.util.HashSet<String>();
        for (IMarker m : errs) if (MentorProblem.MARKER.equals(m.getType())) hebrew.add(m.getResource().getFullPath() + ":" + m.getAttribute(IMarker.LINE_NUMBER, 0));
        IMarker first = null;
        for (IMarker m : errs) {
            boolean heb = MentorProblem.MARKER.equals(m.getType());
            if (!heb && hebrew.contains(m.getResource().getFullPath() + ":" + m.getAttribute(IMarker.LINE_NUMBER, 0))) continue;
            if (first == null) first = m;
            String msg = m.getAttribute(IMarker.MESSAGE, "").replace("\u202B", "").replace("\u202C", "").replace("\u200E", "");
            out.add(m.getResource().getName() + " · שורה " + m.getAttribute(IMarker.LINE_NUMBER, 0) + "\n" + msg);
        }
        return first;
    }

    /** חלון אזהרה: יש שגיאות קומפילציה, לא מריצים. פותח את השגיאה הראשונה בעורך */
    private static void warn(Shell shell, ExecutionEvent event, IFile file, IMarker first, List<String> errors) {
        StringBuilder b = new StringBuilder("יש " + errors.size() + (errors.size() == 1 ? " שגיאת קומפילציה" : " שגיאות קומפילציה")
                + " — לכן לא מריצים את ההרצה הוויזואלית. מתקנים את השגיאות (מסומנות באדום בקוד) ומריצים שוב.\n");
        for (int i = 0; i < errors.size() && i < 3; i++) b.append("\n").append(errors.get(i)).append('\n');
        if (errors.size() > 3) b.append("\nועוד ").append(errors.size() - 3).append(" — בחלון Problems ובחלון \"הסברים בעברית\".");
        try {
            if (first != null && first.getResource() instanceof IFile) {
                Ui.openAt(HandlerUtil.getActiveWorkbenchWindow(event).getActivePage(), (IFile) first.getResource(),
                        Math.max(0, first.getAttribute(IMarker.LINE_NUMBER, 1) - 1));
            }
        } catch (Exception e) {
            // לא נפתח — החלון בכל זאת מוצג
        }
        MessageDialog.openError(shell, "הרצה ויזואלית — יש שגיאות בקוד", b.toString());
    }

    private static String mainType(IFile file) throws CoreException {
        ICompilationUnit cu = JavaCore.createCompilationUnitFrom(file);
        for (IType t : cu.getTypes()) {
            for (IMethod m : t.getMethods()) if (m.isMainMethod()) return t.getFullyQualifiedName();
        }
        return null;
    }

    /**
     * תצורת הרצה לכל מחלקה — נוצרת פעם אחת ונשמרת. המחלקה הראשית היא LineByLine, ושם המחלקה של התלמיד
     * עובר כארגומנט. התצורה "פרטית" (לא בהיסטוריית Run) — כדי שכפתור ה-Run הרגיל לא יפתח את החלון.
     */
    private static ILaunchConfiguration config(String project, String main) throws CoreException {
        ILaunchManager mgr = DebugPlugin.getDefault().getLaunchManager();
        ILaunchConfigurationType type = mgr.getLaunchConfigurationType(IJavaLaunchConfigurationConstants.ID_JAVA_APPLICATION);
        for (ILaunchConfiguration c : mgr.getLaunchConfigurations(type)) {
            if (LINE_RUNNER.equals(c.getAttribute(IJavaLaunchConfigurationConstants.ATTR_MAIN_TYPE_NAME, ""))
                    && main.equals(c.getAttribute(IJavaLaunchConfigurationConstants.ATTR_PROGRAM_ARGUMENTS, ""))
                    && project.equals(c.getAttribute(IJavaLaunchConfigurationConstants.ATTR_PROJECT_NAME, ""))) {
                if (c.getAttribute(IJavaLaunchConfigurationConstants.ATTR_VM_ARGUMENTS, "").contains("dsviz.visual")) return c;
                ILaunchConfigurationWorkingCopy wc = c.getWorkingCopy();
                setup(wc);
                return wc.doSave();
            }
        }
        String simple = main.substring(main.lastIndexOf('.') + 1) + " (הרצה ויזואלית)";
        ILaunchConfigurationWorkingCopy wc = type.newInstance(null, mgr.generateLaunchConfigurationName(simple));
        wc.setAttribute(IJavaLaunchConfigurationConstants.ATTR_PROJECT_NAME, project);
        wc.setAttribute(IJavaLaunchConfigurationConstants.ATTR_MAIN_TYPE_NAME, LINE_RUNNER);
        wc.setAttribute(IJavaLaunchConfigurationConstants.ATTR_PROGRAM_ARGUMENTS, main);
        String jre = bundledJre();
        if (jre != null) wc.setAttribute(IJavaLaunchConfigurationConstants.ATTR_JRE_CONTAINER_PATH, jre);
        setup(wc);
        return wc.doSave();
    }

    /**
     * הרצה שורה-שורה צריכה Java עם ממשק הדיבאג (jdk.jdi). אם תוכנית ההתקנה התקינה Java 21 —
     * רושמים אותה ב-Eclipse (פעם אחת) ומריצים איתה. מחזיר את נתיב ה-JRE container, או null.
     */
    private static String bundledJre() {
        try {
            String home = AutoSetup.dsvizFile("jdk-path.txt");
            if (home == null || !(new java.io.File(home, "bin/java.exe").isFile() || new java.io.File(home, "bin/java").isFile()))
                return null;
            org.eclipse.jdt.launching.IVMInstallType type =
                    JavaRuntime.getVMInstallType("org.eclipse.jdt.internal.debug.ui.launcher.StandardVMType");
            if (type == null) return null;
            org.eclipse.jdt.launching.IVMInstall vm = type.findVMInstall("il.lamerhav.dsviz.jdk21");
            if (vm == null) {
                org.eclipse.jdt.launching.VMStandin s = new org.eclipse.jdt.launching.VMStandin(type, "il.lamerhav.dsviz.jdk21");
                s.setName("Java 21 (מבני נתונים)");
                s.setInstallLocation(new java.io.File(home));
                vm = s.convertToRealVM();
                JavaRuntime.saveVMConfiguration();
            }
            return JavaRuntime.newJREContainerPath(vm).toPortableString();
        } catch (Exception e) {
            return null;   // נשארים עם ה-Java של הפרויקט
        }
    }

    private static void setup(ILaunchConfigurationWorkingCopy wc) {
        wc.setAttribute(IJavaLaunchConfigurationConstants.ATTR_VM_ARGUMENTS, VM_ARGS);
        wc.setAttribute(IDebugUIConstants.ATTR_CONSOLE_ENCODING, "UTF-8");
        wc.setAttribute(IDebugUIConstants.ATTR_PRIVATE, true);
    }
}
