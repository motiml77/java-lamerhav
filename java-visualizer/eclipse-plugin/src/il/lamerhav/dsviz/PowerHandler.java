package il.lamerhav.dsviz;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.resources.WorkspaceJob;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.IViewReference;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.handlers.HandlerUtil;

/**
 * לחצן הכיבוי האדום / ההפעלה הירוק בסוף סרגל הכלים: מסביר מה יקרה, ואחרי אישור מכבה (או מפעיל) את כל DSViz.
 */
public class PowerHandler extends AbstractHandler {
    static final String OFF_TEXT = "כל מה ש-DSViz מוסיף ל-Eclipse ייכבה:\n"
            + "•  הכפתורים בסרגל הכלים והפקודות בתפריט \"מבני נתונים\" (תרגיל חדש, שליחת תרגיל, הוסף מבנה נתונים, הרצה ויזואלית, הדרכה)\n"
            + "•  ההסברים בעברית לשגיאות, וחלון ההסברים\n"
            + "•  ההצעות לפתרון מעל הסעיפים\n"
            + "•  ההוספה האוטומטית של ספריית הקורס לפרויקטים ושל פעולות העזר לקוד\n\n"
            + "יישאר Eclipse רגיל בלבד. הקוד והפרויקטים שלכם לא משתנים ולא נמחקים, והם ממשיכים לרוץ כרגיל (Run).\n\n"
            + "כדי להחזיר הכול: לחצן ההפעלה הירוק בסוף סרגל הכלים, או בתפריט \"מבני נתונים\" ← הפעלת DSViz.";
    static final String ON_TEXT = "הכפתורים והתפריט \"מבני נתונים\" יחזרו, וגם ההסברים בעברית, ההצעות לפתרון וההכנה האוטומטית של הפרויקטים "
            + "לתצוגה הוויזואלית.\n\n"
            + "אפשר לכבות שוב בכל רגע בלחצן האדום בסוף סרגל הכלים, או בתפריט \"מבני נתונים\" ← כיבוי DSViz.";

    @Override
    public Object execute(ExecutionEvent event) {
        Shell shell = Ui.shell(event);
        boolean turnOff = Power.on();
        if (!ask(shell, turnOff)) return null;
        apply(!turnOff);
        return null;
    }

    /** חלון ההסבר (מימין לשמאל): כיבוי / הפעלה, או ביטול */
    static boolean ask(Shell shell, boolean turnOff) {
        return new Say(shell, turnOff ? Say.Kind.WARN : Say.Kind.QUESTION, turnOff ? "כיבוי DSViz" : "הפעלת DSViz")
                .text(turnOff ? OFF_TEXT : ON_TEXT).buttons(turnOff ? "כיבוי" : "הפעלה", "ביטול").width(500).open() == 0;
    }

    /** מכבה / מפעיל: המצב נשמר, הכפתורים מתעדכנים, וההסברים בעברית מוסרים (או חוזרים) בבנייה מחדש */
    public static void apply(boolean on) {
        Power.set(on);
        try {
            for (IWorkbenchWindow w : PlatformUI.getWorkbench().getWorkbenchWindows()) {
                IWorkbenchPage page = w.getActivePage();
                if (page == null) continue;
                if (on) {
                    if (MentorParticipant.enabled()) page.showView(MentorView.ID, null, IWorkbenchPage.VIEW_VISIBLE);
                } else {
                    IViewReference v = page.findViewReference(MentorView.ID);
                    if (v != null) page.hideView(v);
                }
            }
        } catch (Exception e) {
            // החלון לא נמצא / לא נפתח — לא חשוב
        }
        SolutionMinings.refreshAll();
        if (on) {
            LibraryEverywhere.refresh();
            AutoSetup.favoriteHelpers();
        }
        WorkspaceJob job = new WorkspaceJob(on ? "DSViz — הפעלה" : "DSViz — כיבוי") {
            @Override
            public IStatus runInWorkspace(IProgressMonitor monitor) {
                try {
                    if (!on) ResourcesPlugin.getWorkspace().getRoot().deleteMarkers(MentorProblem.MARKER, true, IResource.DEPTH_INFINITE);
                } catch (Exception e) {
                    // הבנייה למטה מנקה אותם בכל מקרה
                }
                return Status.OK_STATUS;
            }
        };
        job.setRule(ResourcesPlugin.getWorkspace().getRoot());
        job.schedule();
        MentorToggleHandler.rebuild(on && MentorParticipant.enabled());
    }

}
