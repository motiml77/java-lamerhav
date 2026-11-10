package il.lamerhav.dsviz;

import java.util.Collections;
import java.util.Map;

import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.AbstractSourceProvider;
import org.eclipse.ui.ISources;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.services.IServiceLocator;

/**
 * המשתנה il.lamerhav.dsviz.teacher: הכפתור "עמדת ניהול" בסרגל הכלים פעיל רק כשהמחשב מחובר כמורה באמת
 * (כניסה עם Google של מורה — לא תלמיד ולא עמדת מחשב). אחרת הכפתור אפור.
 * מתעדכן מיד אחרי כניסה / יציאה (refresh), וגם בדיקה שקטה כל כמה שניות (כניסה שנעשתה בחלון אחר).
 */
public final class TeacherSource extends AbstractSourceProvider {
    public static final String VAR = "il.lamerhav.dsviz.teacher";
    private static final int POLL_MS = 4000;
    private static TeacherSource instance;
    private boolean last;

    public TeacherSource() {
        instance = this;
    }

    private static boolean listening;

    @Override
    public void initialize(IServiceLocator locator) {
        if (!listening) {   // כניסה / התנתקות בחלונות של הכניסה — הכפתור מתעדכן מיד (לא מחכים לבדיקה הבאה)
            listening = true;
            AccessGate.onChange(TeacherSource::refresh);
        }
        refresh();
        poll();
        // בדיקה אחת מול השרת ברקע בהפעלת Eclipse: מורה שהושבת / כניסה שבוטלה — הכפתור נכבה (בלי אינטרנט לא משתנה כלום)
        Thread t = new Thread(() -> {
            try { if (!AccessGate.disabled()) Account.refresh(); } catch (Throwable ignored) { /* בפעם הבאה */ }
            refresh();
        }, "dsviz-teacher-check");
        t.setDaemon(true);
        t.start();
    }

    private static void poll() {
        if (!PlatformUI.isWorkbenchRunning()) return;
        Display d = PlatformUI.getWorkbench().getDisplay();
        if (d == null || d.isDisposed()) return;
        d.timerExec(POLL_MS, () -> {
            refresh();
            poll();
        });
    }

    /** מחשב מחדש ומודיע ל-Eclipse (בחוט המסך) */
    static void refresh() {
        final TeacherSource s = instance;
        if (s == null || !PlatformUI.isWorkbenchRunning()) return;
        Display d = PlatformUI.getWorkbench().getDisplay();
        if (d == null || d.isDisposed()) return;
        d.asyncExec(() -> {
            boolean now;
            try {
                now = AccessGate.isTeacher();
            } catch (Throwable e) {
                now = false;   // לא בטוחים שזה מורה — הכפתור אפור
            }
            if (now != s.last) {
                s.last = now;
                s.fireSourceChanged(ISources.WORKBENCH, VAR, Boolean.valueOf(now));
            }
        });
    }

    @Override
    public Map<String, Object> getCurrentState() {
        return Collections.singletonMap(VAR, (Object) Boolean.valueOf(last));
    }

    @Override
    public String[] getProvidedSourceNames() {
        return new String[] { VAR };
    }

    @Override
    public void dispose() {
        if (instance == this) instance = null;
    }
}
