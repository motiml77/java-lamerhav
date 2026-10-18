package il.lamerhav.dsviz;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.resources.IFile;
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
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.handlers.HandlerUtil;
import org.eclipse.ui.ide.IDE;

/** "הרצה ויזואלית": שומר ומריץ את המחלקה של הקובץ הפתוח (עם main) */
public class RunVisualHandler extends AbstractHandler {
    private static final String VM_ARGS = "-Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8";
    private static final String LINE_RUNNER = "unit4.collectionsLib.LineByLine";

    /** true = הרצה שורה אחר שורה (RunLinesHandler) */
    protected boolean lineByLine() {
        return false;
    }

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
            if (lineByLine()) {
                // מוודאים שהספרייה בפרויקט עדכנית (LineByLine נמצא בה)
                ProjectSetup.prepare(JavaCore.create(file.getProject()), Ui.resolver(shell));
            }
            DebugUITools.launch(config(file.getProject().getName(), main, lineByLine()), ILaunchManager.RUN_MODE);
        } catch (CoreException e) {
            Ui.error(shell, "לא הצלחתי להריץ.", e);
        }
        return null;
    }

    private static String mainType(IFile file) throws CoreException {
        ICompilationUnit cu = JavaCore.createCompilationUnitFrom(file);
        for (IType t : cu.getTypes()) {
            for (IMethod m : t.getMethods()) if (m.isMainMethod()) return t.getFullyQualifiedName();
        }
        return null;
    }

    /**
     * תצורת הרצה לכל מחלקה — נוצרת פעם אחת ונשמרת (מופיעה גם ב-Run History).
     * שורה-שורה: המחלקה הראשית היא LineByLine, ושם המחלקה של התלמיד עובר כארגומנט.
     */
    private static ILaunchConfiguration config(String project, String main, boolean lines) throws CoreException {
        ILaunchManager mgr = DebugPlugin.getDefault().getLaunchManager();
        ILaunchConfigurationType type = mgr.getLaunchConfigurationType(IJavaLaunchConfigurationConstants.ID_JAVA_APPLICATION);
        String mainType = lines ? LINE_RUNNER : main;
        String programArgs = lines ? main : "";
        for (ILaunchConfiguration c : mgr.getLaunchConfigurations(type)) {
            if (mainType.equals(c.getAttribute(IJavaLaunchConfigurationConstants.ATTR_MAIN_TYPE_NAME, ""))
                    && programArgs.equals(c.getAttribute(IJavaLaunchConfigurationConstants.ATTR_PROGRAM_ARGUMENTS, ""))
                    && project.equals(c.getAttribute(IJavaLaunchConfigurationConstants.ATTR_PROJECT_NAME, ""))) {
                if (c.getAttribute(IJavaLaunchConfigurationConstants.ATTR_VM_ARGUMENTS, "").contains("stdout.encoding")) return c;
                ILaunchConfigurationWorkingCopy wc = c.getWorkingCopy();
                setup(wc);
                return wc.doSave();
            }
        }
        String simple = main.substring(main.lastIndexOf('.') + 1) + (lines ? " (שורה אחר שורה)" : "");
        ILaunchConfigurationWorkingCopy wc = type.newInstance(null, mgr.generateLaunchConfigurationName(simple));
        wc.setAttribute(IJavaLaunchConfigurationConstants.ATTR_PROJECT_NAME, project);
        wc.setAttribute(IJavaLaunchConfigurationConstants.ATTR_MAIN_TYPE_NAME, mainType);
        if (lines) wc.setAttribute(IJavaLaunchConfigurationConstants.ATTR_PROGRAM_ARGUMENTS, programArgs);
        setup(wc);
        return wc.doSave();
    }

    private static void setup(ILaunchConfigurationWorkingCopy wc) {
        wc.setAttribute(IJavaLaunchConfigurationConstants.ATTR_VM_ARGUMENTS, VM_ARGS);
        wc.setAttribute(IDebugUIConstants.ATTR_CONSOLE_ENCODING, "UTF-8");
    }
}
