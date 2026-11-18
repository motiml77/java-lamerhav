package il.lamerhav.dsviz;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jface.window.Window;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.handlers.HandlerUtil;
import org.eclipse.ui.texteditor.ITextEditor;

/**
 * "צור מחלקה": מהתפריט / לחיצה ימנית, מהכפתור בחלון "הוסף מבנה נתונים", או כשהסמן מחוץ לפעולה. המחלקה נוצרת בקובץ חדש
 * באותה תיקייה (ובאותה חבילה) של הקובץ הנוכחי — או של החבילה שנבחרה בעץ הקבצים — ונפתחת בעורך.
 */
public class CreateClassHandler extends AbstractHandler {
    static final String TITLE = ClassGen.TITLE;

    @Override
    public Object execute(ExecutionEvent event) {
        if (!Power.on()) return null;
        Shell shell = Ui.shell(event);
        IWorkbenchPage page = HandlerUtil.getActiveWorkbenchWindow(event).getActivePage();
        Object[] t = target(event);
        if (t == null) {
            Say.info(shell, TITLE, "פותחים קובץ Java (או בוחרים חבילה בעץ הקבצים) — המחלקה החדשה נוצרת שם, ואז לוחצים שוב \"צור מחלקה\".");
            return null;
        }
        run(shell, page, (IContainer) t[0], (String) t[1]);
        return null;
    }

    /** התיקייה והחבילה: של הקובץ הנוכחי, או של החבילה / תיקיית הקוד שנבחרה בעץ הקבצים. null — אין (גם "היררכיה מוכנה") */
    static Object[] target(ExecutionEvent event) {
        IEditorPart part = HandlerUtil.getActiveEditor(event);
        IFile file = Ui.currentFile(event);
        if (file != null && file.getName().endsWith(".java")) {
            ITextEditor ed = part == null ? null : part.getAdapter(ITextEditor.class);
            String text = ed != null && file.equals(ed.getEditorInput().getAdapter(IFile.class))
                    ? ed.getDocumentProvider().getDocument(ed.getEditorInput()).get() : read(file);
            return new Object[] { file.getParent(), ClassGen.packageOf(text) };
        }
        org.eclipse.jface.viewers.ISelection sel = HandlerUtil.getCurrentSelection(event);
        if (sel instanceof org.eclipse.jface.viewers.IStructuredSelection && !sel.isEmpty()) {
            Object first = ((org.eclipse.jface.viewers.IStructuredSelection) sel).getFirstElement();
            IJavaElement je = org.eclipse.core.runtime.Adapters.adapt(first, IJavaElement.class);
            if (je instanceof IPackageFragment && je.getResource() instanceof IContainer) return new Object[] { je.getResource(), je.getElementName() };
            if (je instanceof IPackageFragmentRoot && je.getResource() instanceof IContainer) return new Object[] { je.getResource(), "" };
        }
        return null;
    }

    /** פותח את החלון ויוצר את הקובץ בתיקייה folder (החבילה pkg) */
    static void run(Shell shell, IWorkbenchPage page, IContainer folder, String pkg) {
        ClassDialog d = new ClassDialog(shell, ClassGen.JAVA, pkg, taken(folder), known(folder.getProject()));
        if (d.open() != Window.OK || d.code() == null) return;
        try {
            IFile f = folder.getFile(new org.eclipse.core.runtime.Path(d.fileName()));
            if (f.exists()) {
                Say.warn(shell, TITLE, "כבר יש קובץ " + d.fileName() + " בתיקייה — בוחרים שם אחר.");
                return;
            }
            IFile created = ProjectSetup.createFile(folder, d.fileName(), d.code());
            Ui.openAt(page, created, ClassGen.classLine(d.code()));
        } catch (CoreException e) {
            Ui.error(shell, "לא הצלחתי ליצור את המחלקה.", e);
        }
    }

    /** מהעורך של "הוסף מבנה נתונים": אותה תיקייה ואותה חבילה של הקובץ */
    static void run(Shell shell, IWorkbenchPage page, IFile file, String text) {
        if (file == null) {
            Say.info(shell, TITLE, "שומרים את הקובץ בפרויקט (קובץ Java בתוך תיקיית קוד), ואז לוחצים שוב \"צור מחלקה\".");
            return;
        }
        run(shell, page, file.getParent(), ClassGen.packageOf(text));
    }

    /** הקבצים שכבר יש בתיקייה (Student.java ← Student) */
    static Map<String, String> taken(IContainer folder) {
        Map<String, String> t = new LinkedHashMap<String, String>();
        try {
            for (IResource r : folder.members())
                if (r instanceof IFile && r.getName().endsWith(".java")) t.put(r.getName().substring(0, r.getName().length() - 5), r.getName());
        } catch (CoreException ignored) {
            // בלי רשימה — הבדיקה לפני היצירה עדיין מונעת דריסה
        }
        return t;
    }

    /** כל המחלקות בתיקיות הקוד של הפרויקט (לרשימת הטיפוסים, ל-import ולבדיקת שם כפול באותה חבילה) */
    static List<ClassGen.Known> known(org.eclipse.core.resources.IProject project) {
        List<ClassGen.Known> out = new ArrayList<ClassGen.Known>();
        Set<String> seen = new HashSet<String>();
        try {
            IJavaProject jp = JavaCore.create(project);
            if (jp != null && jp.exists()) for (IPackageFragmentRoot r : jp.getPackageFragmentRoots()) {
                if (r.getKind() != IPackageFragmentRoot.K_SOURCE || r.getResource() == null || r.getResource().getLocation() == null) continue;
                for (ClassGen.Known k : ClassGen.scanProject(r.getResource().getLocation().toFile(), ClassGen.JAVA))
                    if (seen.add(k.name + "|" + k.pkg)) out.add(k);
            }
            if (out.isEmpty() && project.getLocation() != null)   // לא פרויקט Java — כל התיקייה
                out.addAll(ClassGen.scanProject(project.getLocation().toFile(), ClassGen.JAVA));
        } catch (CoreException ignored) {
            // בלי מחלקות מהפרויקט — הרשימה מראה רק את הטיפוסים הבסיסיים
        }
        return out;
    }

    private static String read(IFile f) {
        try (java.io.InputStream in = f.getContents()) {
            java.io.ByteArrayOutputStream b = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            for (int n; (n = in.read(buf)) > 0; ) b.write(buf, 0, n);
            return new String(b.toByteArray(), java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "";
        }
    }
}
