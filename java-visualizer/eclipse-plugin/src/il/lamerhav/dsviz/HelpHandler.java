package il.lamerhav.dsviz;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.window.Window;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.handlers.HandlerUtil;

/** הכפתור "?": חלון שבו בוחרים הדרכה, והסרטון הנבחר מתנגן בחלון נגן בתוך Eclipse (מתוך help/ שבתוך התוסף או בהתקנה) */
public class HelpHandler extends AbstractHandler {
    private static final String[] FILES = { "index.html", "player.html", "tutorials.json", "a.mp4", "a.jpg", "b.mp4", "b.jpg", "c.mp4", "c.jpg", "d.mp4", "d.jpg", "e.mp4", "e.jpg" };

    @Override
    public Object execute(ExecutionEvent event) {
        Shell shell = HandlerUtil.getActiveShell(event);
        HelpDialog d = new HelpDialog(shell);
        if (d.open() != Window.OK) return null;
        try {
            HelpPlayer.show(shell, helpDir(), d.chosenId());   // נפתח חלון נגן בתוך Eclipse — רק ההדרכה שנבחרה
        } catch (Exception e) {
            MessageDialog.openError(shell, "הדרכה לשימוש", "לא הצלחתי לפתוח את ההדרכה: " + e.getMessage());
        }
        return null;
    }

    /** התיקייה help שההתקנה יצרה (‎%LOCALAPPDATA%\\DSViz\\help; ב-Mac ‎~/Library/Application Support/DSViz/help) — אם התוסף עצמו בלי סרטונים */
    static File installedHelp() {
        try {
            ProjectSetup.resource("/resources/help/index.html");
            return null;   // הסרטונים בתוך התוסף
        } catch (Exception e) {
            String base = System.getenv("LOCALAPPDATA");
            File home = new File(System.getProperty("user.home"));
            File mac = new File(home, "Library/Application Support/DSViz");   // ההתקנה ל-Mac (installer/mac/install.sh)
            File dir = new File(base != null ? new File(base, "DSViz") : mac.isDirectory() ? mac : new File(home, "DSViz"), "help");
            return new File(dir, "index.html").isFile() ? dir : null;
        }
    }

    /** התיקייה שבה נמצאים קובצי ההדרכה: זו של ההתקנה, או (אם הם בתוך התוסף) תיקייה זמנית שאליה הם מחולצים פעם אחת */
    static File helpDir() throws CoreException, IOException {
        File installed = installedHelp();
        if (installed != null) return installed;   // ההתקנה (DSViz-Setup) שומרת את הסרטונים פעם אחת, בתיקייה help שלה
        File dir = new File(System.getProperty("java.io.tmpdir"), "dsviz-help-1.0.1");
        dir.mkdirs();
        for (String f : FILES) {
            File out = new File(dir, f);
            byte[] data;
            try {
                data = ProjectSetup.resource("/resources/help/" + f);
            } catch (CoreException e) {
                if (f.equals("index.html")) throw e;   // שאר הקבצים — אם חסר סרטון, ממשיכים
                continue;
            }
            if (!out.isFile() || out.length() != data.length) Files.write(out.toPath(), data);
        }
        return dir;
    }

}
