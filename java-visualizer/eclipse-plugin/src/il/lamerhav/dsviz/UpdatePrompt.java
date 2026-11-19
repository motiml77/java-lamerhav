package il.lamerhav.dsviz;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;

/**
 * עדכון שהורד ומחכה להתקנה (update.ps1 שומר אותו ב-‎<תיקיית ההתקנה>\pending\‎ עד שהוא באמת מותקן): Eclipse מציע
 * "הפעלה מחדש ועדכון" — Eclipse נסגר, update.ps1 ‏-InstallNow מתקין (חצי דקה בערך) ופותח את Eclipse מחדש.
 * כך מי שסגר את Eclipse ופתח אותו מיד (לפני שהעדכון הספיק להיכנס), או שההתקנה שלו לא הושלמה, לא נשאר עם הגרסה הישנה.
 * בודקים 20 שניות אחרי הפתיחה ואחר כך כל דקה (העדכון יכול לרדת בזמן העבודה), שואלים פעם אחת לכל גרסה, ורק כש-Eclipse
 * הוא החלון הפעיל (לא באמצע חלון אחר). "אחר כך" — העדכון יותקן כשסוגרים את Eclipse (כמו קודם).
 * ואם העדכון הותקן בזמן ש-Eclipse פתוח (ההתקנה כותבת אז את הגרסה החדשה ליד הישנה — build.txt חדש מהתוסף שרץ):
 * "הפעלה מחדש עכשיו" — Eclipse עולה עם הגרסה החדשה.
 */
final class UpdatePrompt {
    private static String asked = "";
    private static boolean started;

    private UpdatePrompt() {}

    static void start(final Display d) {
        if (started || !System.getProperty("os.name", "").toLowerCase().contains("win")) return;
        started = true;
        d.timerExec(20000, new Runnable() {
            public void run() {
                if (d.isDisposed()) return;
                try { check(d); } catch (Throwable ignored) { /* ננסה בדקה הבאה */ }
                d.timerExec(60000, this);
            }
        });
    }

    /** {build, version} של העדכון שמחכה, או null */
    static String[] pending() {
        String dir = AutoSetup.dsvizFile("install-dir.txt");
        if (dir == null) return null;
        File f = new File(dir, "pending" + File.separator + "pending.json");
        if (!f.isFile()) return null;
        try {
            String t = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
            Matcher b = Pattern.compile("\"build\"\\s*:\\s*\"?(\\d+)").matcher(t);
            Matcher v = Pattern.compile("\"version\"\\s*:\\s*\"([^\"]*)\"").matcher(t);
            Matcher n = Pattern.compile("\"file\"\\s*:\\s*\"([^\"]+)\"").matcher(t);
            if (!b.find() || !n.find() || !new File(f.getParentFile(), n.group(1)).isFile()) return null;
            return new String[] { b.group(1), v.find() ? v.group(1) : "" };
        } catch (Exception e) {
            return null;
        }
    }

    private static long num(String s) {
        try { return Long.parseLong(s.trim()); } catch (Exception e) { return 0; }
    }

    /** מספר הבנייה שההתקנה רשמה (build.txt) — 0 כשאין */
    static long installedBuild() {
        String dir = AutoSetup.dsvizFile("install-dir.txt");
        if (dir == null) return 0;
        try {
            return num(new String(Files.readAllBytes(new File(dir, "build.txt").toPath()), StandardCharsets.UTF_8));
        } catch (Exception e) {
            return 0;
        }
    }

    static void check(Display d) {
        long own = num(Look.build());
        if (own <= 0) return;   // בנייה מקומית בלי מספר
        String[] p = pending();
        boolean waiting = p != null && num(p[0]) > own;
        // העדכון כבר הותקן בזמן ש-Eclipse פתוח (הגרסה החדשה נכתבה ליד הישנה) — צריך רק הפעלה מחדש של Eclipse
        long inst = waiting ? 0 : installedBuild();
        boolean installed = inst > own;
        String key = waiting ? p[0] : installed ? "i" + inst : null;
        if (key == null || key.equals(asked)) return;
        IWorkbenchWindow w = PlatformUI.getWorkbench().getActiveWorkbenchWindow();
        Shell s = d.getActiveShell();
        if (w == null || s == null || s != w.getShell()) return;   // לא באמצע חלון אחר — ננסה בדקה הבאה
        asked = key;
        if (installed) {
            boolean restart = Say.ask(s, "עדכון ל-DSViz",
                    "DSViz התעדכן לגרסה חדשה.\n\n"
                    + "כדי לעבוד איתה צריך להפעיל את Eclipse מחדש.\n"
                    + "(אם יש קבצים שלא נשמרו, Eclipse ישאל אם לשמור.)",
                    "הפעלה מחדש עכשיו", "אחר כך");
            if (restart) PlatformUI.getWorkbench().restart();
            return;
        }
        String ver = p[1].isEmpty() ? "" : " (" + p[1] + ")";
        boolean now = Say.ask(s, "עדכון ל-DSViz",
                "גרסה חדשה של DSViz" + ver + " הורדה ומוכנה להתקנה.\n\n"
                + "כדי להתקין אותה צריך לסגור את Eclipse לחצי דקה בערך — Eclipse ייפתח מחדש לבד.\n"
                + "(אם יש קבצים שלא נשמרו, Eclipse ישאל אם לשמור.)",
                "הפעלה מחדש ועדכון", "אחר כך — כשאסגור את Eclipse");
        if (!now) return;
        String launcher = System.getProperty("eclipse.launcher", "");
        String ws = "";
        try {
            String u = org.eclipse.core.runtime.Platform.getInstanceLocation().getURL().getFile();
            if (u.matches("^/[A-Za-z]:.*")) u = u.substring(1);
            ws = new File(java.net.URLDecoder.decode(u.replace("+", "%2B"), "UTF-8")).getAbsolutePath();
        } catch (Exception ignored) { /* בלי -data: Eclipse שואל / פותח את האחרון */ }
        AutoSetup.startUpdater("-InstallNow", "-Relaunch", launcher, "-Workspace", ws);
        PlatformUI.getWorkbench().close();
    }
}
