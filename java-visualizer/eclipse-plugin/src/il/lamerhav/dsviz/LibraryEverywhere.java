package il.lamerhav.dsviz;

import java.util.ArrayList;
import java.util.List;

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
 * באותו סבב: module-info.java "ריק" בפרויקט שיש בו מחלקה בלי package — נמחק, כדי שגם ההרצה הרגילה תעבוד (ModuleFix).
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
                if (relevant(event.getDelta()) || moduleRelevant(event.getDelta())) schedule(1500);
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

    /** נוסף קובץ Java בלי package (ישר בתיקיית הקוד), או ש-module-info.java נוסף / נמחק — בודקים שוב (ModuleFix) */
    private static boolean moduleRelevant(IJavaElementDelta d) {
        IJavaElement e = d.getElement();
        int type = e.getElementType();
        if (type == IJavaElement.COMPILATION_UNIT) {
            if (d.getKind() == IJavaElementDelta.ADDED) return e.getParent().getElementName().isEmpty();   // גם module-info.java
            return d.getKind() == IJavaElementDelta.REMOVED && ModuleFix.FILE.equals(e.getElementName());
        }
        // יורדים עד הקבצים — רק בחבילת ברירת המחדל
        boolean down = type == IJavaElement.JAVA_MODEL || type == IJavaElement.JAVA_PROJECT || type == IJavaElement.PACKAGE_FRAGMENT_ROOT
                || type == IJavaElement.PACKAGE_FRAGMENT && e.getElementName().isEmpty();
        if (down) for (IJavaElementDelta c : d.getAffectedChildren()) if (moduleRelevant(c)) return true;
        return false;
    }

    private static final WorkspaceJob JOB = new WorkspaceJob("מבני נתונים — ספריית הקורס בפרויקטים") {
        @Override
        public IStatus runInWorkspace(IProgressMonitor monitor) {
            if (!Power.on()) return Status.OK_STATUS;   // DSViz כבוי — לא נוגעים בפרויקטים
            List<String> removed = new ArrayList<String>(), blocked = new ArrayList<String>();
            for (IJavaProject jp : ProjectSetup.javaProjects()) {
                // module-info.java + מחלקה בלי package: ההרצה הרגילה נופלת (FindException) — ModuleFix
                ModuleFix.Result r = ModuleFix.check(jp);
                if (r == ModuleFix.Result.REMOVED) {
                    removed.add(jp.getElementName());
                    continue;   // הספרייה — בסבב הבא (למטה), כש-JDT כבר יודע שהקובץ נמחק (באמצע הפעולה הזו הוא עוד חושב שהפרויקט מודולרי)
                }
                if (r == ModuleFix.Result.BLOCKED) blocked.add(jp.getElementName());
                ensure(jp);
            }
            if (!removed.isEmpty()) schedule(300);   // עבודה שרצה עכשיו — תרוץ שוב מיד כשתסתיים
            ModuleFix.notify(removed, blocked);
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
            if (hasModuleInfo(jp)) {
                // פרויקט מודולרי (Eclipse יוצר module-info.java בכל פרויקט חדש): הספרייה נכנסת כמו בכל פרויקט — כמודול (module path +
                // requires dsviz), ואם היא כבר בפרויקט ב-classpath רגיל מעבירים אותה (אחרת "החבילה לא נגישה"). ספרייה אחרת של הקורס — לא נוגעים
                if (jp.findType(PROBE) != null && !ProjectSetup.hasOurJar(jp)) return;
                ProjectSetup.prepare(jp, null, false);
                return;
            }
            // כבר יש (הספרייה שלנו, ה-jar המקורי של הקורס, או קוד מקור) — אלא אם הספרייה שלנו נשארה ב-module path אחרי ש-module-info נמחק
            if (jp.findType(PROBE) != null && !ProjectSetup.ourJarOnModulePathWithoutModule(jp)) return;
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
