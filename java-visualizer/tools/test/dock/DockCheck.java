package unit4.collectionsLib;

import java.awt.Frame;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.event.InputEvent;
import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;

/**
 * סידור החלקים בחלון ההרצה הוויזואלית (Dock): הלוגיקה של העץ (העברה לצד, החלפה, שמירה כטקסט, טקסט פגום), ובחלון האמיתי
 * (TraceViewer על קובץ צעדים, ב-xvfb) — גרירה אמיתית של העכבר (Robot) מהכותרת של חלק אל צד של חלק אחר ואל האמצע שלו,
 * הסימון הכחול בזמן הגרירה, גרירת קו לגודל אחר, שמירה לפעם הבאה (חלון חדש נפתח באותו סידור), הגדלה וחזרה, ו"פריסה רגילה".
 *   java -Duser.home=<תיקייה זמנית> unit4.collectionsLib.DockCheck <קובץ צעדים> [תיקייה לצילומים]
 */
public class DockCheck {
    static int fails = 0;
    static Robot robot;
    static File shots;

    static void check(boolean ok, String what) {
        System.out.println((ok ? "  ok  " : "  FAIL ") + what);
        if (!ok) fails++;
    }

    static final String DEF = "H0.420(V0.640(code,H0.500(vars,out)),view)";
    static final List<String> ALL = Arrays.asList("code", "vars", "out", "view");

    public static void main(String[] a) throws Exception {
        logic();
        if (a.length == 0) { end(); return; }
        shots = a.length > 1 ? new File(a[1]) : null;
        if (shots != null) shots.mkdirs();
        TraceViewer.load(a[0]);
        robot = new Robot();
        robot.setAutoDelay(25);
        Viewer v = open();
        check(DEF.equals(v.dockText()), "חלון חדש: הסידור הרגיל (" + v.dockText() + ")");
        Rectangle code = v.cardOnScreen("code"), view = v.cardOnScreen("view"), out = v.cardOnScreen("out"), vars = v.cardOnScreen("vars");
        check(view.x > code.x && out.y > code.y && out.x > vars.x, "הסידור הרגיל על המסך: התצוגה מימין, הקוד משמאל למעלה, מתחתיו משתנים ופלט");
        check(v.titleOnScreen("code").x > code.x + code.width / 2, "הכותרת של כל חלק מימין (עברית, מימין לשמאל) — גם אחרי שהחלקים נבנים מהסידור");
        shot("1-default");

        // גרירה אמיתית: הפלט → החצי הימני של התצוגה הוויזואלית. באמצע הגרירה — הסימון הכחול
        Rectangle target = v.cardOnScreen("view");
        dragHead(v, "out", target.x + target.width * 9 / 10, target.y + target.height / 2, "2-dragging-out-to-right-of-view");
        check("H0.420(V0.640(code,vars),H0.500(view,out))".equals(v.dockText()), "גרירת ״פלט״ אל ימין התצוגה: " + v.dockText());
        view = v.cardOnScreen("view");
        out = v.cardOnScreen("out");
        code = v.cardOnScreen("code");
        vars = v.cardOnScreen("vars");
        check(out.x >= view.x + view.width - 2 && Math.abs(out.height - view.height) <= 2, "על המסך: הפלט מימין לתצוגה, לכל הגובה");
        check(vars.y >= code.y + code.height - 2 && Math.abs(vars.width - code.width) <= 2, "המקום הישן של הפלט נסגר: מעקב המשתנים קיבל את כל הרוחב שמתחת לקוד");
        shot("3-out-right-of-view");

        // גרירה אל האמצע של חלק אחר: החלפת מקומות (קוד ↔ משתנים)
        Rectangle vr = v.cardOnScreen("vars");
        dragHead(v, "code", vr.x + vr.width / 2, vr.y + vr.height / 2, "4-dragging-code-swap-vars");
        check("H0.420(V0.640(vars,code),H0.500(view,out))".equals(v.dockText()), "גרירת ״הקוד״ אל אמצע ״מעקב משתנים״ — החלפת מקומות: " + v.dockText());
        check(v.cardOnScreen("code").y > v.cardOnScreen("vars").y, "על המסך: הקוד עכשיו מתחת למעקב המשתנים");

        // גרירה אל החלק העליון של חלק: מעליו
        Rectangle cr = v.cardOnScreen("code");
        dragHead(v, "view", cr.x + cr.width / 2, cr.y + cr.height / 12, null);
        check("H0.420(V0.640(vars,V0.500(view,code)),out)".equals(v.dockText()), "גרירת ״התצוגה״ אל ראש ״הקוד״ — מעליו: " + v.dockText());
        check(v.cardOnScreen("view").y < v.cardOnScreen("code").y && v.cardOnScreen("out").x > v.cardOnScreen("code").x, "על המסך: התצוגה מעל הקוד, והפלט מימין לכל הגובה");
        check(v.titleOnScreen("view").x > v.cardOnScreen("view").x + v.cardOnScreen("view").width / 2, "גם אחרי הזזות: הכותרות נשארות מימין");
        shot("5-custom-layout");

        // שחרור על אותו חלק / מחוץ לכל חלק: לא משתנה
        String before = v.dockText();
        Rectangle self = v.cardOnScreen("out");
        dragHead(v, "out", self.x + self.width / 2, self.y + self.height / 2, null);
        check(before.equals(v.dockText()), "גרירה ושחרור על אותו חלק — בלי שינוי");
        Rectangle fr = v.getBounds();
        dragHead(v, "out", fr.x + fr.width / 2, fr.y + 12, null);   // הסרגל העליון
        check(before.equals(v.dockText()), "שחרור מחוץ לחלקים (על הסרגל העליון) — בלי שינוי");

        // קו גרירה: גודל אחר — נשמר
        Rectangle o = v.cardOnScreen("out"), left = v.cardOnScreen("vars");
        int sashX = (left.x + left.width + o.x) / 2, sashY = o.y + o.height / 2;
        robot.mouseMove(sashX, sashY);
        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
        for (int i = 1; i <= 10; i++) robot.mouseMove(sashX + i * 12, sashY);
        robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
        settle();
        String resized = v.dockText();
        check(!resized.equals(before) && resized.startsWith("H0.") && !resized.startsWith("H0.420"), "גרירת הקו הראשי ימינה שינתה את הגודל: " + resized);
        check(v.cardOnScreen("out").width < o.width - 60, "על המסך: הפלט נהיה צר יותר");

        // הגדלה לכל החלון וחזרה: אותו סידור
        SwingUtilities.invokeAndWait(new Runnable() { public void run() { clickMax(v, "code"); } });
        settle();
        check(v.cardOnScreen("out") == null && v.cardOnScreen("code").width > fr.width * 8 / 10, "הגדלה: הקוד לכל החלון");
        SwingUtilities.invokeAndWait(new Runnable() { public void run() { v.restoreLayout(); } });
        settle();
        check(resized.equals(v.dockText()) && v.cardOnScreen("out") != null && v.cardOnScreen("view").y < v.cardOnScreen("code").y, "חזרה מהגדלה: אותו סידור ואותם מקומות");

        // נשמר לפעם הבאה: חלון חדש נפתח באותו סידור
        Properties p = new Properties();
        try (java.io.InputStream in = new java.io.FileInputStream(new File(new File(System.getProperty("user.home"), ".dsviz"), "zoom.properties"))) { p.load(in); }
        check(resized.equals(p.getProperty("viewer.dock")), "הסידור נשמר בקובץ ההגדרות (viewer.dock)");
        close(v);
        final Viewer v2 = open();
        check(resized.equals(v2.dockText()), "חלון חדש: נפתח בסידור שהמשתמש בחר");
        check(v2.cardOnScreen("view").y < v2.cardOnScreen("code").y && v2.cardOnScreen("out").x > v2.cardOnScreen("code").x, "וגם על המסך: התצוגה מעל הקוד, הפלט מימין");
        int w1 = v2.cardOnScreen("out").width;
        check(Math.abs(w1 - widthBeforeClose) <= 6, "וגם הגודל שנבחר (רוחב הפלט " + w1 + " מול " + widthBeforeClose + ")");

        // "פריסה רגילה"
        SwingUtilities.invokeAndWait(new Runnable() { public void run() { v2.resetLayout(); } });
        settle();
        check(DEF.equals(v2.dockText()), "״פריסה רגילה״ מחזירה את הסידור הרגיל");
        Rectangle c2 = v2.cardOnScreen("code"), w2 = v2.cardOnScreen("view");
        check(w2.x > c2.x && v2.cardOnScreen("out").y > c2.y && Math.abs(c2.width - (c2.width + w2.width) * 0.42) < 30, "וגם על המסך, בגדלים הרגילים");
        shot("6-reset");
        close(v2);
        end();
    }

    static int widthBeforeClose;

    static void end() {
        System.out.println(fails == 0 ? "MARK-END" : "FAILED " + fails);
        System.exit(fails == 0 ? 0 : 1);
    }

    /** הלוגיקה של העץ, בלי חלון */
    static void logic() {
        Dock d = Dock.parse(DEF, ALL);
        check(d != null && DEF.equals(d.format()), "טקסט ← עץ ← אותו טקסט");
        check(d.move("out", "view", Dock.RIGHT) && "H0.420(V0.640(code,vars),H0.500(view,out))".equals(d.format()), "העברה לצד: המקום הישן נסגר, היעד מתחלק לשניים");
        check(!d.move("out", "view", Dock.RIGHT), "כבר שם — בלי שינוי");
        check(d.move("out", "view", Dock.LEFT) && "H0.420(V0.640(code,vars),H0.500(out,view))".equals(d.format()), "אותם שכנים, הצד השני");
        check(d.move("code", "vars", Dock.CENTER) && "H0.420(V0.640(vars,code),H0.500(out,view))".equals(d.format()), "אמצע — החלפת מקומות");
        check(d.move("view", "vars", Dock.BOTTOM) && "H0.420(V0.640(V0.500(vars,view),code),out)".equals(d.format()), "העברה מתחת לחלק אחר");
        check(!d.move("view", "view", Dock.LEFT) && !d.move("nope", "view", Dock.LEFT), "אותו חלק / חלק שלא קיים — בלי שינוי");
        check(d.leaves().size() == 4 && d.leaves().containsAll(ALL), "אחרי כל ההעברות: כל ארבעת החלקים, כל אחד פעם אחת");
        for (String bad : new String[] { "", "code", "H0.5(code,vars)", "H0.5(code,H0.5(code,vars),view)", "H0.5(code,V0.5(vars,out),view", "H2(code,V0.5(vars,H0.5(out,view)))",
                "H0.5(code,V0.5(vars,H0.5(out,code)))", "X0.5(a,b)", "H0.5(code,V0.5(vars,H0.5(out,view)))zzz" })
            check(Dock.parse(bad, ALL) == null, "טקסט שמור לא תקין נדחה (חוזרים לסידור הרגיל): \"" + bad + "\"");
        check(Dock.parse("H0.300(code,V0.700(out,view))", Arrays.asList("code", "out", "view")) != null, "שלושה חלקים (בלי מעקב משתנים) — תקין");
    }

    static Viewer open() throws Exception {
        SwingUtilities.invokeAndWait(new Runnable() { public void run() { Viewer.open(); } });
        Viewer v = null;
        for (Frame f : Frame.getFrames()) if (f instanceof Viewer && f.isShowing()) v = (Viewer) f;
        if (v == null) throw new IllegalStateException("חלון ההרצה לא נפתח");
        final Viewer vv = v;
        SwingUtilities.invokeAndWait(new Runnable() { public void run() { vv.setBounds(40, 20, 1400, 860); vv.validate(); } });
        settle();
        return v;
    }

    static void close(final Viewer v) throws Exception {
        widthBeforeClose = v.cardOnScreen("out") != null ? v.cardOnScreen("out").width : 0;
        SwingUtilities.invokeAndWait(new Runnable() { public void run() { v.setVisible(false); v.dispose(); } });
        settle();
    }

    /** מחכים שהחלון יסיים לטפל באירועים (בלי Robot.waitForIdle — הוא נתקע לפעמים מול xvfb בלי מנהל חלונות) */
    static void settle() throws Exception {
        for (int i = 0; i < 3; i++) {
            Thread.sleep(120);
            SwingUtilities.invokeAndWait(new Runnable() { public void run() { } });
        }
    }

    /** גרירה אמיתית מהכותרת של חלק (ליד הקצה, לא על הכפתורים) אל נקודה על המסך; shotName — צילום באמצע הגרירה, לפני השחרור */
    static void dragHead(Viewer v, String id, int toX, int toY, String shotName) throws Exception {
        Rectangle r = v.cardOnScreen(id);
        int fx = r.x + r.width / 2, fy = r.y + 14;
        robot.mouseMove(fx, fy);
        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
        for (int i = 1; i <= 12; i++) robot.mouseMove(fx + (toX - fx) * i / 12, fy + (toY - fy) * i / 12);
        robot.mouseMove(toX, toY);
        settle();
        if (shotName != null) shot(shotName);
        robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
        settle();
    }

    static void clickMax(Viewer v, String id) {
        v.toggleMaxOf(id);
    }

    static void shot(String name) throws Exception {
        if (shots == null) return;
        Frame f = null;
        for (Frame x : Frame.getFrames()) if (x instanceof Viewer && x.isShowing()) f = x;
        if (f != null) ImageIO.write(robot.createScreenCapture(f.getBounds()), "png", new File(shots, name + ".png"));
    }
}
