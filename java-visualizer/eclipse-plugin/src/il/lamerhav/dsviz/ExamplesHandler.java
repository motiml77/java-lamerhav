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
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.jface.window.Window;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.dialogs.ElementListSelectionDialog;
import org.eclipse.ui.handlers.HandlerUtil;

/** "דוגמאות פתורות": מעתיק את הדוגמאות לפרויקט ופותח את הדוגמה שבוחרים */
public class ExamplesHandler extends AbstractHandler {
    private static final String[] LABELS = { "מחסנית — היפוך, הכנסה לתחתית (רקורסיה)", "תור — ספירה, מחיקת מופעים",
            "רשימה מקושרת — סכום, מחיקה, היפוך", "עצים — ספירה, גובה, הכנסה לעץ חיפוש",
            "מערכים — מקסימום, היפוך, סכומי שורות, מיון בועות", "תלמידים — מצטיינים בתור, הטוב ביותר במערך" };

    @Override
    public Object execute(ExecutionEvent event) {
        Shell shell = HandlerUtil.getActiveShell(event);
        try {
            IJavaProject jp = chooseProject(shell, Ui.currentProject(event));
            if (jp == null) return null;
            IContainer src = ProjectSetup.prepare(jp, Ui.resolver(shell));
            List<IFile> files = ProjectSetup.copyExamples(src);

            ElementListSelectionDialog d = new ElementListSelectionDialog(shell, new LabelProvider());
            d.setTitle("דוגמאות פתורות");
            d.setMessage("איזו דוגמה לפתוח? (כל הדוגמאות הועתקו ל-src/examples)");
            d.setElements(LABELS);
            d.setMultipleSelection(false);
            if (d.open() != Window.OK || d.getFirstResult() == null) return null;
            int i = java.util.Arrays.asList(LABELS).indexOf(d.getFirstResult());
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
        ElementListSelectionDialog d = new ElementListSelectionDialog(shell, new LabelProvider());
        d.setTitle("דוגמאות פתורות");
        d.setMessage("לאיזה פרויקט להעתיק את הדוגמאות?");
        d.setElements(names.toArray());
        d.setMultipleSelection(false);
        if (d.open() != Window.OK || d.getFirstResult() == null) return null;
        if (create.equals(d.getFirstResult())) return ProjectSetup.createProject(ProjectSetup.freeProjectName());
        return projects.get(names.indexOf(d.getFirstResult()));
    }
}
