package il.lamerhav.dsviz;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jface.window.Window;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.handlers.HandlerUtil;

/** "תרגיל חדש": חלון בחירה → הכנת הפרויקט → יצירת הקובץ ופתיחתו */
public class NewExerciseHandler extends AbstractHandler {
    @Override
    public Object execute(ExecutionEvent event) {
        Shell shell = Ui.shell(event);
        IWorkbenchWindow window = HandlerUtil.getActiveWorkbenchWindow(event);
        String preset = event.getParameter("il.lamerhav.dsviz.structure");
        ExerciseDialog d = new ExerciseDialog(shell, ProjectSetup.javaProjects(), ProjectSetup.freeProjectName(),
                Ui.currentProject(event), preset);
        if (d.open() != Window.OK || d.options() == null) return null;
        Scaffold.Options o = d.options();
        try {
            IJavaProject jp = d.project() != null ? d.project() : ProjectSetup.createProject(d.newProjectName());
            IContainer src = ProjectSetup.prepare(jp, Ui.resolver(shell));
            Scaffold.Result r = Scaffold.generate(o);
            IFile file = ProjectSetup.createFile(src, o.className + ".java", r.code);
            Ui.openAt(window.getActivePage(), file, r.cursorLine);
            ClassInfoDialog.show(shell, o.type);
        } catch (CoreException e) {
            Ui.error(shell, "לא הצלחתי ליצור את התרגיל.", e);
        }
        return null;
    }
}
