package il.lamerhav.dsviz;

import java.util.Collections;
import java.util.Map;

import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.AbstractSourceProvider;
import org.eclipse.ui.IPartListener2;
import org.eclipse.ui.IWindowListener;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchPartReference;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.ISources;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.services.IServiceLocator;

/**
 * המשתנה il.lamerhav.dsviz.bankQuestion: הכפתור "השאלה" פעיל רק כשיש שאלה להציג —
 * הקובץ שפתוח בעורך הוא תרגיל שנוצר ממאגר התרגילים, או שחלון השאלה כבר פתוח. אחרת הכפתור אפור.
 * מתעדכן כשעוברים בין עורכים / סוגרים עורך, וכשחלון השאלה נפתח או נסגר, וכשמסמנים "סיימתי".
 */
public final class BankQuestionSource extends AbstractSourceProvider {
    public static final String VAR = "il.lamerhav.dsviz.bankQuestion";
    private static BankQuestionSource instance;
    private boolean last;

    private final IPartListener2 parts = new IPartListener2() {
        @Override
        public void partActivated(IWorkbenchPartReference ref) {
            refresh();
        }

        @Override
        public void partClosed(IWorkbenchPartReference ref) {
            refresh();
        }

        @Override
        public void partBroughtToTop(IWorkbenchPartReference ref) {
            refresh();
        }
    };

    public BankQuestionSource() {
        instance = this;
    }

    @Override
    public void initialize(IServiceLocator locator) {
        IWorkbench wb = locator.getService(IWorkbench.class);
        if (wb == null) return;
        wb.addWindowListener(new IWindowListener() {
            @Override
            public void windowOpened(IWorkbenchWindow w) {
                w.getPartService().addPartListener(parts);
                refresh();
            }

            @Override
            public void windowClosed(IWorkbenchWindow w) {
                refresh();
            }

            @Override
            public void windowActivated(IWorkbenchWindow w) {
                refresh();
            }

            @Override
            public void windowDeactivated(IWorkbenchWindow w) {
            }
        });
        for (IWorkbenchWindow w : wb.getWorkbenchWindows()) w.getPartService().addPartListener(parts);
        BankProgress.get().listen(BankQuestionSource::refresh);   // נוצר תרגיל / סומן "סיימתי"
        refresh();
    }

    /** האם יש שאלה להציג עכשיו */
    static boolean available() {
        try {
            if (BankFloat.current() != null) return true;
            IWorkbench wb = PlatformUI.getWorkbench();
            IWorkbenchWindow w = wb.getActiveWorkbenchWindow();
            IWorkbenchPage page = w == null ? null : w.getActivePage();
            return BankFloat.questionOfEditor(page) != null;
        } catch (Throwable e) {
            return true;   // לא חוסמים את הכפתור אם משהו נכשל בבדיקה
        }
    }

    /** מחשב מחדש ומודיע ל-Eclipse (בחוט המסך) */
    static void refresh() {
        BankQuestionSource s = instance;
        if (s == null || !PlatformUI.isWorkbenchRunning()) return;
        Display d = PlatformUI.getWorkbench().getDisplay();
        if (d == null || d.isDisposed()) return;
        d.asyncExec(() -> {
            boolean now = available();
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
