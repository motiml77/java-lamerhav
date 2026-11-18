package il.lamerhav.dsviz;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jface.window.Window;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.handlers.HandlerUtil;

/**
 * "היררכיה מוכנה": כל המחלקות של עץ הורשה (בעלי חיים / צורות / אנשים בבית הספר / עץ משלכם) ו-main לדוגמה עם מערך — בקבצים חדשים
 * באותה תיקייה ובאותה חבילה כמו "צור מחלקה". ה-main נפתח בעורך.
 */
public class CreateTreeHandler extends AbstractHandler {
    static final String TITLE = ClassGen.TREES_TITLE;

    @Override
    public Object execute(ExecutionEvent event) {
        if (!Power.on()) return null;
        Shell shell = Ui.shell(event);
        IWorkbenchPage page = HandlerUtil.getActiveWorkbenchWindow(event).getActivePage();
        Object[] t = CreateClassHandler.target(event);
        if (t == null) {
            Say.info(shell, TITLE, "פותחים קובץ Java (או בוחרים חבילה בעץ הקבצים) — המחלקות נוצרות שם, ואז לוחצים שוב \"" + TITLE + "\".");
            return null;
        }
        IContainer folder = (IContainer) t[0];
        ClassGen.Options base = new ClassGen.Options();
        base.lang = ClassGen.JAVA;
        base.pkg = (String) t[1];
        base.taken = CreateClassHandler.taken(folder);
        base.known = CreateClassHandler.known(folder.getProject());
        SwtPoly.TreeDialog d = new SwtPoly.TreeDialog(shell, base);
        if (d.open() != Window.OK || d.result == null || !d.result.ok()) return null;
        try {
            for (String[] f : d.result.files)
                if (folder.getFile(new org.eclipse.core.runtime.Path(f[0])).exists()) {
                    Say.warn(shell, TITLE, "כבר יש קובץ " + f[0] + " בתיקייה — לא נוצר כלום.");
                    return null;
                }
            List<IFile> created = new ArrayList<IFile>();
            for (String[] f : d.result.files) created.add(ProjectSetup.createFile(folder, f[0], f[1]));
            if (!created.isEmpty()) {
                String[] last = d.result.files.get(d.result.files.size() - 1);
                Ui.openAt(page, created.get(created.size() - 1), ClassGen.classLine(last[1]));
            }
        } catch (CoreException e) {
            Ui.error(shell, "לא הצלחתי ליצור את המחלקות.", e);
        }
        return null;
    }
}
