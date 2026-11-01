package il.lamerhav.dsviz.intellij;

import java.util.List;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationAction;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.ModuleListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import com.intellij.openapi.wm.ToolWindowManager;

/**
 * בפתיחה של כל פרויקט (כמו AutoSetup ב-Eclipse): JDK לפרויקט בלי JDK (ה-Java 21 של ההתקנה), ספריית הקורס בכל מודול Java,
 * והודעת "ברוכים הבאים" בפעם הראשונה.
 */
public final class DsvizStartup implements StartupActivity.DumbAware {
    private static final String WELCOMED = "il.lamerhav.dsviz.welcomed";

    @Override
    public void runActivity(Project project) {
        if (!Dsviz.on()) return;
        try {
            LibrarySetup.ensureSdk(project);
        } catch (Throwable ignored) {
            // בלי JDK אוטומטי — IntelliJ מציע לבחור
        }
        LibrarySetup.ensureAll(project);
        project.getMessageBus().connect(project).subscribe(ModuleListener.TOPIC, new ModuleListener() {
            @Override
            public void modulesAdded(Project p, List<? extends Module> modules) {
                LibrarySetup.ensureAll(p);
            }
        });
        welcome(project);
        startMacUpdater();
        // ההערות בעברית בתרגילים: בלי הפס "Visual layout of bidirectional text..." מעל כל קובץ (IntelliJ מסדר אותן בעצמו)
        PropertiesComponent.getInstance().setValue("bidi.content.notification.disable", "true");
        String auto = System.getProperty("dsviz.autotest");
        if (auto != null && !auto.isEmpty()) Autotest.start(project, auto);
    }

    /**
     * עדכון אוטומטי ב-Mac (כמו התוספים של VS Code ו-Eclipse): מפעילים ברקע את update.sh של ההתקנה — הוא בודק פעם ביום אם יש בנייה חדשה,
     * ומוסיף את התוסף לכל IntelliJ שעוד אין בו. ב-Windows העדכון רץ בכל כניסה ל-Windows (update.ps1).
     */
    static void startMacUpdater() {
        if (!com.intellij.openapi.util.SystemInfo.isMac) return;
        java.io.File script = new java.io.File(Dsviz.installDir(), "update.sh");
        if (!script.isFile()) return;
        com.intellij.openapi.application.ApplicationManager.getApplication().executeOnPooledThread(() -> {
            try {
                java.io.File none = new java.io.File("/dev/null");
                new ProcessBuilder("/bin/bash", script.getAbsolutePath()).redirectInput(none)
                        .redirectOutput(ProcessBuilder.Redirect.appendTo(none)).redirectErrorStream(true).start();
            } catch (Exception ignored) {
                // ננסה בפעם הבאה
            }
        });
    }

    private static void welcome(Project project) {
        PropertiesComponent props = PropertiesComponent.getInstance();
        if (props.getBoolean(WELCOMED, false)) return;
        props.setValue(WELCOMED, true);
        Notification n = Dsviz.notify(project, "DSViz – מבני נתונים: הכול מוכן",
                "<div dir='rtl'>בתפריט העליון \"מבני נתונים\", בחלון DSViz שבצד ימין ובלחיצה ימנית בקוד:<br>"
                + "• <b>תרגיל חדש</b> — בוחרים מבנה ומקבלים תרגיל מוכן.<br>"
                + "• <b>הוסף מבנה נתונים</b> — המבנה נכנס לקוד במקום הסמן.<br>"
                + "• <b>הרצה ויזואלית</b> (Ctrl+Alt+R) — חלון שמראה כל שורה, את המשתנים ואת מבני הנתונים.<br>"
                + "(הכפתור Run הרגיל של IntelliJ מריץ כרגיל — הפלט רק בקונסול.)</div>", NotificationType.INFORMATION);
        n.addAction(NotificationAction.createSimpleExpiring("פתיחת חלון DSViz", () -> {
            var w = ToolWindowManager.getInstance(project).getToolWindow(DsvizToolWindow.ID);
            if (w != null) w.activate(null);
        }));
    }
}
