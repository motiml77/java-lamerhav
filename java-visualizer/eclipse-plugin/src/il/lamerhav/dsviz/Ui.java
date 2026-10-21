package il.lamerhav.dsviz;

import java.util.List;

import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.Adapters;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.handlers.HandlerUtil;
import org.eclipse.ui.ide.IDE;
import org.eclipse.ui.ide.ResourceUtil;
import org.eclipse.ui.texteditor.ITextEditor;

/** עזרי ממשק משותפים לפקודות */
final class Ui {
    private Ui() {}

    /** הקובץ שעובדים עליו עכשיו: העורך הפעיל, או מה שנבחר בעץ הקבצים */
    static IFile currentFile(ExecutionEvent event) {
        ISelection sel = HandlerUtil.getCurrentSelection(event);
        IEditorPart ed = HandlerUtil.getActiveEditor(event);
        if (HandlerUtil.getActivePart(event) == ed && ed != null) {
            IFile f = ResourceUtil.getFile(ed.getEditorInput());
            if (f != null) return f;
        }
        if (sel instanceof IStructuredSelection && !sel.isEmpty()) {
            IResource r = Adapters.adapt(((IStructuredSelection) sel).getFirstElement(), IResource.class);
            if (r instanceof IFile) return (IFile) r;
        }
        return ed == null ? null : ResourceUtil.getFile(ed.getEditorInput());
    }

    /** הפרויקט שעובדים עליו עכשיו (לפי בחירה או עורך), או null */
    static IProject currentProject(ExecutionEvent event) {
        ISelection sel = HandlerUtil.getCurrentSelection(event);
        if (sel instanceof IStructuredSelection && !sel.isEmpty()) {
            IResource r = Adapters.adapt(((IStructuredSelection) sel).getFirstElement(), IResource.class);
            if (r != null) return r.getProject();
        }
        IFile f = currentFile(event);
        return f == null ? null : f.getProject();
    }

    static ProjectSetup.ConflictResolver resolver(final Shell shell) {
        return new ProjectSetup.ConflictResolver() {
            public boolean removeConflicts(List<String> names) {
                return MessageDialog.openQuestion(shell, "ספרייה מתנגשת",
                        "ב-Build Path נמצאה ספריית unit4 נוספת: " + String.join(", ", names) + "\n"
                        + "היא תתנגש עם המעטפת הוויזואלית (שתי מחלקות Stack באותו שם).\n\n"
                        + "להסיר אותה מה-Build Path? (הקובץ עצמו לא יימחק)");
            }
        };
    }

    /** פותח קובץ בעורך ומציב את הסמן בסוף השורה line (מ-0) */
    static void openAt(IWorkbenchPage page, IFile file, int line) throws PartInitException {
        IEditorPart ed = IDE.openEditor(page, file, true);
        if (line > 0 && ed instanceof ITextEditor) {
            ITextEditor te = (ITextEditor) ed;
            IDocument doc = te.getDocumentProvider().getDocument(te.getEditorInput());
            try {
                int offset = doc.getLineOffset(line) + doc.getLineLength(line) - doc.getLineDelimiter(line).length();
                te.selectAndReveal(offset, 0);
            } catch (Exception ignored) {}
        }
    }

    static void error(Shell shell, String what, CoreException e) {
        MessageDialog.openError(shell, "מבני נתונים", what + "\n\n" + e.getStatus().getMessage());
    }
}
