package il.lamerhav.dsviz;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jface.window.Window;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.handlers.HandlerUtil;

/** "דוגמאות פתורות": מעתיק את הדוגמאות לפרויקט ופותח את הדוגמה שבוחרים */
public class ExamplesHandler extends AbstractHandler {
    @Override
    public Object execute(ExecutionEvent event) {
        Shell shell = Ui.shell(event);
        try {
            IJavaProject jp = chooseProject(shell, Ui.currentProject(event));
            if (jp == null) return null;
            IContainer src = ProjectSetup.prepare(jp, Ui.resolver(shell));
            List<IFile> files = ProjectSetup.copyExamples(src);

            ExamplesDialog d = new ExamplesDialog(shell);
            if (d.open() != Window.OK) return null;
            int i = d.chosen();
            Ui.openAt(HandlerUtil.getActiveWorkbenchWindow(event).getActivePage(), files.get(i), 0);
        } catch (CoreException e) {
            Ui.error(shell, "לא הצלחתי להעתיק את הדוגמאות.", e);
        }
        return null;
    }

    private static IJavaProject chooseProject(Shell shell, IProject current) throws CoreException {
        List<IJavaProject> projects = ProjectSetup.javaProjects();
        for (IJavaProject jp : projects) if (jp.getProject().equals(current)) return jp;
        if (projects.isEmpty()) return ProjectSetup.createProject(ProjectSetup.freeProjectName());
        List<String> names = new ArrayList<String>();
        for (IJavaProject jp : projects) names.add(jp.getProject().getName());
        String create = "פרויקט חדש: " + ProjectSetup.freeProjectName();
        names.add(create);
        int i = Say.choose(shell, "דוגמאות פתורות", "לאיזה פרויקט להעתיק את הדוגמאות?", names);
        if (i < 0) return null;
        if (create.equals(names.get(i))) return ProjectSetup.createProject(ProjectSetup.freeProjectName());
        return projects.get(i);
    }
}
