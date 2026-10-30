package il.lamerhav.dsviz;

import java.util.List;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.resources.IFile;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.text.ITextSelection;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.handlers.HandlerUtil;
import org.eclipse.ui.texteditor.ITextEditor;

/**
 * "הצעה לפתרון" מהתפריט (מבני נתונים / לחיצה ימנית בעורך): ההצעה לסעיף שבו נמצא הסמן.
 * אותו חלון כמו הכפתור שמעל כל פעולה (SolutionMinings) — למקרה ש-code minings כבויים ב-Eclipse.
 */
public class SolutionHandler extends AbstractHandler {
    @Override
    public Object execute(ExecutionEvent event) {
        Shell shell = HandlerUtil.getActiveShell(event);
        IEditorPart part = HandlerUtil.getActiveEditor(event);
        ITextEditor editor = part instanceof ITextEditor ? (ITextEditor) part : part == null ? null : part.getAdapter(ITextEditor.class);
        IFile file = editor == null ? null : editor.getEditorInput().getAdapter(IFile.class);
        if (file == null || !"java".equals(file.getFileExtension())) {
            none(shell);
            return null;
        }
        String path = file.getFullPath().toString();
        SolutionStore store = SolutionMinings.store();
        List<SolutionStore.Entry> all = store.forFile(path);
        if (all.isEmpty()) {
            none(shell);
            return null;
        }
        SolutionStore.Entry found = null;
        try {
            ITextSelection sel = (ITextSelection) editor.getSelectionProvider().getSelection();
            ICompilationUnit cu = JavaCore.createCompilationUnitFrom(file);
            IJavaElement el = cu.getElementAt(sel.getOffset());
            while (el != null && !(el instanceof IMethod)) el = el.getParent();
            if (el != null) {
                IMethod m = (IMethod) el;
                found = store.find(path, m.getDeclaringType().getElementName(), m.getElementName(), m.getParameterTypes().length);
            }
        } catch (Exception ignored) {
            // לא נמצאה הפעולה שבמקום הסמן
        }
        if (found == null && all.size() == 1) found = all.get(0);
        if (found == null) {
            MessageDialog.openInformation(shell, "הצעה לפתרון",
                    "שימו את הסמן בתוך הפעולה של הסעיף, ובחרו שוב \"הצעה לפתרון\".\n\n(אפשר גם ללחוץ על \"הצעה לפתרון\" שמופיע מעל כל פעולה של סעיף.)");
            return null;
        }
        SolutionDialog.show(shell, found);
        return null;
    }

    private static void none(Shell shell) {
        MessageDialog.openInformation(shell, "הצעה לפתרון",
                "יש הצעה לפתרון לתרגילים שנוצרו ב\"שליחת תרגיל\": מעל הפעולה של כל סעיף מופיע \"הצעה לפתרון\" עם מנורה.\n\n"
                + "כדאי לנסות קודם לבד — ההצעה נפתחת רק כשלוחצים עליה.");
    }
}
