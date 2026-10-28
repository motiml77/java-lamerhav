package il.lamerhav.dsviz;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.resources.IFile;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jface.text.ITextSelection;
import org.eclipse.jface.text.Position;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.text.source.projection.ProjectionAnnotation;
import org.eclipse.jface.text.source.projection.ProjectionAnnotationModel;
import org.eclipse.jface.text.source.projection.ProjectionViewer;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.texteditor.ITextEditor;

/**
 * בתרגיל שנוצר: main ופעולות העזר שנתונים בתרגיל מכווצים (אפשר ללחוץ על הפלוס ולפתוח),
 * ורק הפעולות שהתלמיד צריך לכתוב — פתוחות. מכווצים לפי המבנה של Java ב-Eclipse (folding),
 * אחרי שה-Eclipse סיים לחשב אותו (לכן כמה ניסיונות).
 */
public final class Folding {
    private static final int TRIES = 40;
    private static final int EVERY_MS = 250;

    private Folding() {
    }

    /** מכווץ כל פעולה בקובץ שאין בה את שורת הסימון של התשובה; אחרי הכיווץ חוזרים למקום הסמן */
    public static void collapseGiven(final IEditorPart editor, final IFile file) {
        if (!(editor instanceof ITextEditor)) return;
        final int revealOffset = cursor((ITextEditor) editor);
        final Display d = editor.getSite().getShell().getDisplay();
        d.timerExec(EVERY_MS, new Runnable() {
            int left = TRIES;

            @Override
            public void run() {
                try {
                    if (editor.getSite().getShell().isDisposed()) return;
                    if (tryCollapse((ITextEditor) editor, file, revealOffset)) return;
                } catch (Throwable e) {
                    return;   // לא קריטי — הקוד פשוט נשאר פתוח
                }
                if (--left > 0) d.timerExec(EVERY_MS, this);
            }
        });
    }

    private static int cursor(ITextEditor te) {
        ISelection sel = te.getSelectionProvider().getSelection();
        return sel instanceof ITextSelection ? ((ITextSelection) sel).getOffset() : -1;
    }

    /** true כשהסתיים (כוונצו, או שאין מה לכווץ) */
    private static boolean tryCollapse(ITextEditor te, IFile file, int revealOffset) throws Exception {
        ProjectionAnnotationModel model = model(te);
        if (model == null) return false;
        ICompilationUnit cu = JavaCore.createCompilationUnitFrom(file);
        if (cu == null) return true;
        List<int[]> toFold = new ArrayList<int[]>();   // {שם-offset, אורך}
        for (IType t : cu.getAllTypes()) {
            for (IMethod m : t.getMethods()) {
                String src = m.getSource();
                if (src == null || src.contains("====")) continue;   // פעולה של התלמיד — נשארת פתוחה
                toFold.add(new int[] { m.getNameRange().getOffset(), m.getSourceRange().getLength() });
            }
        }
        if (toFold.isEmpty()) return true;
        List<ProjectionAnnotation> found = new ArrayList<ProjectionAnnotation>();
        for (int[] f : toFold) {
            ProjectionAnnotation best = null;
            int bestLen = Integer.MAX_VALUE;
            java.util.Iterator<?> it = model.getAnnotationIterator();
            while (it.hasNext()) {
                Object o = it.next();
                if (!(o instanceof ProjectionAnnotation)) continue;
                Position p = model.getPosition((ProjectionAnnotation) o);
                if (p == null || f[0] < p.offset || f[0] >= p.offset + p.length) continue;
                if (p.length < bestLen && p.length <= f[1] + 200) {
                    best = (ProjectionAnnotation) o;
                    bestLen = p.length;
                }
            }
            if (best == null) return false;   // ה-folding עוד לא מוכן
            found.add(best);
        }
        for (ProjectionAnnotation a : found) if (!a.isCollapsed()) model.collapse(a);
        if (revealOffset >= 0) te.selectAndReveal(revealOffset, 0);
        return true;
    }

    private static ProjectionAnnotationModel model(ITextEditor te) {
        ProjectionAnnotationModel m = te.getAdapter(ProjectionAnnotationModel.class);
        if (m != null) return m;
        Object v = te.getAdapter(org.eclipse.jface.text.ITextOperationTarget.class);
        return v instanceof ProjectionViewer ? ((ProjectionViewer) v).getProjectionAnnotationModel() : null;
    }
}
