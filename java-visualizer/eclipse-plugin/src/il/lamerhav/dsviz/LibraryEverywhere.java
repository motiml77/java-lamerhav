package il.lamerhav.dsviz;

import org.eclipse.core.resources.WorkspaceJob;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.jdt.core.ElementChangedEvent;
import org.eclipse.jdt.core.IElementChangedListener;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaElementDelta;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;

/**
 * ספריית הקורס (unit4.collectionsLib — Stack, Queue, Node, BinNode...) בכל פרויקט Java: בהפעלה, ובכל פעם שנוצר או נפתח פרויקט —
 * אם הספרייה עוד לא ב-Build Path שלו (ולא קיימת אצלו ספרייה אחרת כזו), מעתיקים אליו את lib/dsviz.jar ומוסיפים אותו.
 * כך `Queue` מזוהה בכל פרויקט, גם אחד שהתלמיד יצר בעצמו (והספרייה נשארת בפרויקט — אפשר להעביר אותו למחשב אחר).
 */
public final class LibraryEverywhere {
    private static final String PROBE = "unit4.collectionsLib.Queue";
    private static boolean started;

    private LibraryEverywhere() {
    }

    static synchronized void start() {
        if (started) return;
        started = true;
        JavaCore.addElementChangedListener(new IElementChangedListener() {
            @Override
            public void elementChanged(ElementChangedEvent event) {
                if (relevant(event.getDelta())) schedule(1500);
            }
        }, ElementChangedEvent.POST_CHANGE);
        schedule(0);
    }

    /** אחרי הפעלה מחדש של DSViz (הלחצן הירוק): בודקים שוב את כל הפרויקטים */
    static synchronized void refresh() {
        if (!started) start();
        else schedule(0);
    }

    /** נוסף / נפתח פרויקט Java, או שה-Build Path שלו השתנה */
    private static boolean relevant(IJavaElementDelta d) {
        IJavaElement e = d.getElement();
        if (e.getElementType() == IJavaElement.JAVA_PROJECT) {
            return d.getKind() == IJavaElementDelta.ADDED
                    || (d.getFlags() & (IJavaElementDelta.F_OPENED | IJavaElementDelta.F_CLASSPATH_CHANGED)) != 0;
        }
        if (e.getElementType() == IJavaElement.JAVA_MODEL) {
            for (IJavaElementDelta c : d.getAffectedChildren()) if (relevant(c)) return true;
        }
        return false;
    }

    private static final WorkspaceJob JOB = new WorkspaceJob("מבני נתונים — ספריית הקורס בפרויקטים") {
        @Override
        public IStatus runInWorkspace(IProgressMonitor monitor) {
            if (!Power.on()) return Status.OK_STATUS;   // DSViz כבוי — לא נוגעים בפרויקטים
            for (IJavaProject jp : ProjectSetup.javaProjects()) ensure(jp);
            return Status.OK_STATUS;
        }
    };

    static {
        JOB.setSystem(true);
    }

    private static void schedule(long delay) {
        JOB.schedule(delay);
    }

    public static void ensure(IJavaProject jp) {
        try {
            if (jp.findType(PROBE) != null) return;   // כבר יש (הספרייה שלנו, ה-jar המקורי של הקורס, או קוד מקור)
            if (hasModuleInfo(jp)) return;
            ProjectSetup.prepare(jp, null, false);
        } catch (Throwable e) {
            // פרויקט שלא מצליחים לעדכן (קריאה בלבד וכו') — נשאר כמו שהוא
        }
    }

    private static boolean hasModuleInfo(IJavaProject jp) {
        try {
            return jp.getModuleDescription() != null;
        } catch (Throwable e) {
            return false;   // Eclipse ישן בלי מודולים
        }
    }
}
