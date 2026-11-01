package il.lamerhav.dsviz;

import java.io.File;

import org.eclipse.swt.SWT;
import org.eclipse.swt.browser.Browser;
import org.eclipse.swt.browser.BrowserFunction;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.program.Program;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;

/**
 * חלון נגן בתוך Eclipse להדרכה אחת (help/player.html#id): רק הסרטון שנבחר, עם בחירת מהירות (1.25×, 1.5×, 2×).
 * משתמש בדפדפן המשובץ של SWT (Edge ב-Windows). אם אי אפשר ליצור אותו, או שהוא לא מצליח לנגן — נפתח אותו דף בדפדפן הרגיל.
 */
final class HelpPlayer {
    private HelpPlayer() { }

    private static Shell open;   // חלון הנגן הפתוח (אחד בכל פעם)

    static void show(Shell parent, File helpDir, String id) {
        File page = new File(helpDir, "player.html");
        String url = page.toURI().toString() + "#" + id;
        if (open != null && !open.isDisposed()) open.dispose();
        Shell sh = new Shell(parent, SWT.SHELL_TRIM | SWT.MODELESS);
        sh.setText("הדרכה לשימוש");
        sh.setLayout(new FillLayout());
        Browser browser = createBrowser(sh);
        if (browser == null) {
            sh.dispose();
            external(url);
            return;
        }
        final boolean[] failed = { false };
        try {
            new BrowserFunction(browser, "dsvizFail") {   // הדף קורא לזה כשהסרטון לא מתנגן (קודק שחסר בדפדפן המשובץ)
                @Override
                public Object function(Object[] args) {
                    if (failed[0]) return null;
                    failed[0] = true;
                    Display d = sh.getDisplay();
                    d.asyncExec(() -> {
                        if (!sh.isDisposed()) sh.dispose();
                        external(url);
                    });
                    return null;
                }
            };
            new BrowserFunction(browser, "dsvizOpen") {   // "תמכו בפרויקט" — נפתח בדפדפן של המערכת, לא בתוך הנגן
                @Override
                public Object function(Object[] args) {
                    if (args.length > 0 && String.valueOf(args[0]).startsWith("https://www.paypal.com/")) Program.launch(String.valueOf(args[0]));
                    return null;
                }
            };
        } catch (Throwable ignored) {
            // בלי JavaScript מול Java — הדף יציג הודעה בעצמו
        }
        Rectangle area = parent != null && !parent.isDisposed() ? parent.getMonitor().getClientArea() : sh.getDisplay().getPrimaryMonitor().getClientArea();
        int w = Math.min(1180, area.width - 60), h = Math.min(760, area.height - 60);
        sh.setBounds(area.x + (area.width - w) / 2, area.y + (area.height - h) / 2, w, h);
        browser.setUrl(url);
        open = sh;
        sh.addListener(SWT.Dispose, e -> { if (open == sh) open = null; });
        sh.open();
    }

    /** הדפדפן המשובץ: Edge (Windows), ואם אין — ברירת המחדל של המערכת. null אם אי אפשר */
    private static Browser createBrowser(Shell sh) {
        int[] styles = org.eclipse.swt.SWT.getPlatform().equals("win32") ? new int[] { SWT.EDGE, SWT.NONE } : new int[] { SWT.NONE };
        for (int style : styles) {
            try {
                return new Browser(sh, style);
            } catch (Throwable e) {
                // לא זמין (חסר WebView2 / WebKit) — מנסים את הבא
            }
        }
        return null;
    }

    private static void external(String url) {
        if (!Program.launch(url)) Say.error(null, "הדרכה לשימוש", "אין דפדפן ברירת מחדל. אפשר לפתוח ידנית: " + url);
    }
}
