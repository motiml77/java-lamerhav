package unit4.collectionsLib;

import java.awt.Component;
import java.awt.Container;
import java.awt.Frame;
import java.awt.Point;
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
 * סידור החלקים בחלון ההרצה הוויזואלית (Dock / DockSplit): הלוגיקה של העץ (העברה לצד, החלפה, שמירה כטקסט, טקסט פגום, חישוב
 * הגדלים), ובחלון האמיתי (TraceViewer על קובץ צעדים, ב-xvfb) — גרירות אמיתיות של העכבר (Robot):
 * - חלק מהכותרת שלו אל צד של חלק אחר ואל האמצע שלו, הסימון הכחול בזמן הגרירה, שמירה לפעם הבאה, הגדלה וחזרה, "פריסה רגילה";
 * - כל קו גרירה (הראשי והפנימיים, לרוחב ולגובה) לשני הכיוונים — בסידור הרגיל, בסידור שמשתמש דיווח עליו (התצוגה משמאל לכל
 *   הגובה, מימינה הפלט ומעקב המשתנים ומתחתיהם הקוד), אחרי הגדלה וחזרה, אחרי "פריסה רגילה", בחלון שנפתח מסידור שמור, בחלון
 *   קטן ובתצוגה מוגדלת (Ctrl +): הקו זז בדיוק כמו העכבר, צד אחד גדל והשני קטן באותה מידה, והחלקים שבתוך כל צד שומרים על
 *   היחס ביניהם; גרירה עד הסוף לא מעלימה אף חלק (גודל קטן ביותר), ואחריה כל הקווים עדיין נגררים; לחיצה כפולה מחזירה לגודל הרגיל.
 *   java -Duser.home=<תיקייה זמנית> unit4.collectionsLib.DockCheck <קובץ צעדים> [תיקייה לצילומים] [recursion]
 * recursion — קובץ צעדים של רקורסיה (recursion.trace: Fact(5) ב-C#, נכתב ביד בפורמט של קובץ הצעדים): אותן גרירות כשבחלק
 * "התצוגה הוויזואלית" מוצג מעקב הרקורסיה.
 *
 * חשוב להריץ גם ב-Java חדש (run-tests.sh מריץ שוב ב-Java של Eclipse אם הוא קיים): ב-Java 25 החלון נפתח הפוך (התצוגה משמאל)
 * ואף קו לא זז — JSplitPane של Java 24 ומעלה מחליף צדדים בחלון מימין לשמאל ומאבד את הצד השמאלי — אף שב-Java 21 הכול עבד.
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
    /** הסידור שמשתמש דיווח עליו: התצוגה משמאל לכל הגובה, מימינה למעלה הפלט ומעקב המשתנים, ומתחתיהם הקוד */
    static final String USER = "H0.300(view,V0.400(H0.500(out,vars),code))";
    static final List<String> ALL = Arrays.asList("code", "vars", "out", "view");
    static final int W = 1400, H = 860;

    public static void main(String[] a) throws Exception {
        logic();
        if (a.length == 0) { end(); return; }
        shots = a.length > 1 ? new File(a[1]) : null;
        if (shots != null) shots.mkdirs();
        TraceViewer.load(a[0]);
        robot = new Robot();
        robot.setAutoDelay(25);
        System.out.println("  (Java " + System.getProperty("java.version") + ")");
        if (a.length > 2 && a[2].equals("recursion")) recursion();
        else window();
        end();
    }

    static void window() throws Exception {
        Viewer v = open(W, H);
        check(DEF.equals(v.dockText()), "חלון חדש: הסידור הרגיל (" + v.dockText() + ")");
        Rectangle code = v.cardOnScreen("code"), view = v.cardOnScreen("view"), out = v.cardOnScreen("out"), vars = v.cardOnScreen("vars");
        check(view.x > code.x && out.y > code.y && out.x > vars.x, "הסידור הרגיל על המסך: התצוגה מימין, הקוד משמאל למעלה, מתחתיו משתנים ופלט");
        check(v.titleOnScreen("code").x > code.x + code.width / 2, "הכותרת של כל חלק מימין (עברית, מימין לשמאל) — גם אחרי שהחלקים נבנים מהסידור");
        shot("1-default");

        // --- קווי הגרירה בסידור הרגיל: כל קו, לשני הכיוונים
        structure(v, "הסידור הרגיל");
        dragEvery(v, "הסידור הרגיל", 90);
        // גרירה איטית (פיקסל-פיקסל) של הקו הראשי: החלקים שבצד שמתכווץ שומרים בדיוק על היחס ביניהם (לא "זוחלים" לצד אחד)
        DockSplit root = v.dockSplits().get(0);
        String inner = v.dockText().substring(v.dockText().indexOf('('));
        dragSash(root, 70, 70);
        Rectangle vr0 = v.cardOnScreen("vars"), or0 = v.cardOnScreen("out");
        check(Math.abs(vr0.width - or0.width) <= 1 && v.dockText().endsWith(inner) && modelOnScreen(v) == null,
                "גרירה איטית של הקו הראשי (70 צעדים של פיקסל): מעקב המשתנים והפלט נשארו חצי-חצי (" + vr0.width + " / " + or0.width + ")" + note(modelOnScreen(v)));
        // גרירה וחזרה לאותו מקום בלי לשחרר: שום דבר לא השתנה, גם לא בטקסט השמור
        String t0 = v.dockText();
        Rectangle[] b0 = rects(v);
        Point sp = center(root.sash);
        robot.mouseMove(sp.x, sp.y);
        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
        for (int i = 1; i <= 6; i++) robot.mouseMove(sp.x - i * 15, sp.y);
        settle();
        int mid = root.firstSize();
        for (int i = 5; i >= 0; i--) robot.mouseMove(sp.x - i * 15, sp.y);
        robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
        settle();
        check(mid < b0[0].width - 60 && t0.equals(v.dockText()) && Arrays.equals(b0, rects(v)), "גרירה וחזרה לאותו מקום (בלי לשחרר באמצע): אותו סידור ואותם גדלים בדיוק");
        // לחיצה כפולה על קו: חזרה לגודל הרגיל שלו
        doubleClick(root.sash);
        check(v.dockText().startsWith("H0.420(") && Math.abs(root.firstSize() - Math.round((root.getWidth() - DockSplit.SASH) * 0.42)) <= 1,
                "לחיצה כפולה על הקו הראשי: חזר לגודל הרגיל (" + v.dockText() + ")");
        // גרירה עד הסוף: הקו נעצר כשהחלקים בצד שמתכווץ בגודל הקטן ביותר — אף חלק לא נעלם; ואחרי שמחזירים קצת, כל הקווים נגררים
        dragSash(root, 3000, 12);
        check(minOk(v) && v.cardOnScreen("view").width == minOf(v, "view").width && titlesWhole(v),
                "גרירת הקו הראשי ימינה עד הסוף: התצוגה נעצרת ברוחב הקטן ביותר שלה (" + minOf(v, "view").width + "), והכותרת שלה שלמה (" + sizes(v) + ")");
        shot("1b-root-sash-far-right");
        dragSash(root, -3000, 12);
        Rectangle vr1 = v.cardOnScreen("vars"), or1 = v.cardOnScreen("out");
        check(minOk(v) && vr1.width == minOf(v, "vars").width && or1.width == minOf(v, "out").width && ALL.size() == showing(v) && titlesWhole(v),
                "גרירת הקו הראשי שמאלה עד הסוף: מעקב המשתנים והפלט נעצרים ברוחב הקטן ביותר שלהם, כל החלקים על המסך והכותרות שלמות (" + sizes(v) + ")");
        shot("1c-root-sash-far-left");
        dragSash(root, 200, 10);
        dragEvery(v, "אחרי גרירה עד הסוף וחזרה", 40);
        SwingUtilities.invokeAndWait(new Runnable() { public void run() { v.resetLayout(); } });
        settle();
        check(DEF.equals(v.dockText()), "״פריסה רגילה״ אחרי כל הגרירות: " + v.dockText());

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
        check(v.cardOnScreen("out").width == o.width - 120, "על המסך: הפלט נהיה צר יותר בדיוק ב-120 (" + o.width + " ← " + v.cardOnScreen("out").width + ")");

        // הגדלה לכל החלון וחזרה: אותו סידור
        SwingUtilities.invokeAndWait(new Runnable() { public void run() { clickMax(v, "code"); } });
        settle();
        check(v.cardOnScreen("out") == null && v.cardOnScreen("code").width > fr.width * 8 / 10, "הגדלה: הקוד לכל החלון");
        SwingUtilities.invokeAndWait(new Runnable() { public void run() { v.restoreLayout(); } });
        settle();
        check(resized.equals(v.dockText()) && v.cardOnScreen("out") != null && v.cardOnScreen("view").y < v.cardOnScreen("code").y, "חזרה מהגדלה: אותו סידור ואותם מקומות");
        structure(v, "אחרי הגדלה וחזרה");
        dragEvery(v, "אחרי הגדלה וחזרה", 60);
        String kept = v.dockText();

        // נשמר לפעם הבאה: חלון חדש נפתח באותו סידור
        Properties p = new Properties();
        try (java.io.InputStream in = new java.io.FileInputStream(new File(new File(System.getProperty("user.home"), ".dsviz"), "zoom.properties"))) { p.load(in); }
        check(kept.equals(p.getProperty("viewer.dock")), "הסידור נשמר בקובץ ההגדרות (viewer.dock)");
        close(v);
        final Viewer v2 = open(W, H);
        check(kept.equals(v2.dockText()), "חלון חדש: נפתח בסידור שהמשתמש בחר");
        check(v2.cardOnScreen("view").y < v2.cardOnScreen("code").y && v2.cardOnScreen("out").x > v2.cardOnScreen("code").x, "וגם על המסך: התצוגה מעל הקוד, הפלט מימין");
        int w1 = v2.cardOnScreen("out").width;
        check(Math.abs(w1 - widthBeforeClose) <= 2, "וגם הגודל שנבחר (רוחב הפלט " + w1 + " מול " + widthBeforeClose + ")");
        structure(v2, "חלון שנפתח מסידור שמור");
        dragEvery(v2, "חלון שנפתח מסידור שמור", 60);

        // "פריסה רגילה"
        SwingUtilities.invokeAndWait(new Runnable() { public void run() { v2.resetLayout(); } });
        settle();
        check(DEF.equals(v2.dockText()), "״פריסה רגילה״ מחזירה את הסידור הרגיל");
        Rectangle c2 = v2.cardOnScreen("code"), w2 = v2.cardOnScreen("view");
        check(w2.x > c2.x && v2.cardOnScreen("out").y > c2.y && Math.abs(c2.width - (c2.width + w2.width) * 0.42) < 3, "וגם על המסך, בגדלים הרגילים");
        shot("6-reset");
        structure(v2, "אחרי ״פריסה רגילה״");
        dragEvery(v2, "אחרי ״פריסה רגילה״", 60);
        close(v2);

        // --- הסידור שמשתמש דיווח עליו (שמור מהפעם הקודמת): התצוגה משמאל לכל הגובה, מימינה למעלה הפלט ומעקב המשתנים, מתחתיהם הקוד
        Theme.saveText("viewer.dock", USER);
        final Viewer v3 = open(W, H);
        check(USER.equals(v3.dockText()), "חלון חדש מהסידור של המשתמש: " + v3.dockText());
        userOnScreen(v3);
        shot("7-user-layout");
        structure(v3, "הסידור של המשתמש");
        dragEvery(v3, "הסידור של המשתמש", 90);
        // להרחיב את הצד השמאלי (התצוגה): כל מה שמימין מתכווץ לבד, ביחס
        DockSplit r3 = v3.dockSplits().get(0);
        Rectangle ob = v3.cardOnScreen("out"), vb = v3.cardOnScreen("vars"), cb = v3.cardOnScreen("code"), wb = v3.cardOnScreen("view");
        dragSash(r3, 300, 15, "8-user-layout-widening-the-view");
        Rectangle oa = v3.cardOnScreen("out"), va = v3.cardOnScreen("vars"), ca = v3.cardOnScreen("code"), wa = v3.cardOnScreen("view");
        check(wa.width == wb.width + 300 && ca.width == cb.width - 300 && oa.width + va.width == ob.width + vb.width - 300 && Math.abs(oa.width - va.width) <= 1,
                "מרחיבים את התצוגה (משמאל) ב-300: הקוד צר ב-300, והפלט ומעקב המשתנים צרים יחד ב-300 ונשארים חצי-חצי (" + sizes(v3) + ")");
        shot("9-user-layout-view-wider");
        // תצוגה מוגדלת (Ctrl +): הקווים עדיין נגררים
        final double zoomed = Theme.stepZoom(Theme.stepZoom(1, 1), 1);
        SwingUtilities.invokeAndWait(new Runnable() { public void run() { v3.setZoom(zoomed); } });
        settle();
        dragEvery(v3, "תצוגה מוגדלת (" + Math.round(zoomed * 100) + "%)", 60);
        SwingUtilities.invokeAndWait(new Runnable() { public void run() { v3.setZoom(1); } });
        settle();
        // חלון קטן (הקטן ביותר שהחלון מרשה): אף חלק לא קטן מהגודל הקטן ביותר, והקווים נגררים
        SwingUtilities.invokeAndWait(new Runnable() { public void run() { v3.setSize(v3.getMinimumSize()); v3.validate(); } });
        settle();
        check(minOk(v3) && showing(v3) == ALL.size(), "חלון קטן (" + v3.getWidth() + "×" + v3.getHeight() + "): כל החלקים על המסך, אף אחד לא קטן מהגודל הקטן ביותר (" + sizes(v3) + ")");
        structure(v3, "חלון קטן");
        dragEvery(v3, "חלון קטן", 25);
        shot("10-user-layout-small-window");
        // שינוי גודל החלון שומר על היחסים
        final String ratios = v3.dockText();
        SwingUtilities.invokeAndWait(new Runnable() { public void run() { v3.setSize(W, H); v3.validate(); } });
        settle();
        check(ratios.equals(v3.dockText()) && modelOnScreen(v3) == null, "החלון חזר לגודל הגדול: אותם יחסים בין החלקים (" + v3.dockText() + ")" + note(modelOnScreen(v3)));
        close(v3);
        Theme.saveText("viewer.dock", null);
    }

    /** קובץ צעדים של רקורסיה, בסידור של המשתמש: בחלק "התצוגה הוויזואלית" מוצג מעקב הרקורסיה — וכל הקווים נגררים */
    static void recursion() throws Exception {
        check(RecursionKind.of().type != RecursionKind.Type.NONE, "קובץ הצעדים הוא של רקורסיה (" + RecursionKind.of().type + ")");
        Theme.saveText("viewer.dock", USER);
        final Viewer v = open(W, H);
        SwingUtilities.invokeAndWait(new Runnable() { public void run() { v.showStep(8); } });
        settle();
        check(recursionShowing(v), "בצעד 9 מוצג מעקב הרקורסיה (כרטיס לכל קריאה)");
        check(USER.equals(v.dockText()), "הסידור של המשתמש: " + v.dockText());
        userOnScreen(v);
        shot("r1-recursion-user-layout");
        structure(v, "רקורסיה, הסידור של המשתמש");
        dragEvery(v, "רקורסיה, הסידור של המשתמש", 90);
        DockSplit root = v.dockSplits().get(0);
        int w0 = v.cardOnScreen("view").width;
        dragSash(root, 350, 14, "r2-recursion-widening-the-view");
        check(v.cardOnScreen("view").width == w0 + 350 && recursionShowing(v), "מרחיבים את הצד השמאלי (מעקב הרקורסיה) ב-350: " + sizes(v));
        shot("r3-recursion-view-wider");
        SwingUtilities.invokeAndWait(new Runnable() { public void run() { v.resetLayout(); } });
        settle();
        check(DEF.equals(v.dockText()) && recursionShowing(v), "״פריסה רגילה״ גם כשמוצגת הרקורסיה");
        dragEvery(v, "רקורסיה, הסידור הרגיל", 90);
        shot("r4-recursion-default");
        close(v);
        Theme.saveText("viewer.dock", null);
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
        check(Dock.parse(USER, ALL) != null, "הסידור של המשתמש — תקין");
        // הגדלים: לפי היחס, בתוך הגודל הקטן ביותר של כל צד
        check(Dock.firstSize(1000, 0.42, 130, 130) == 420 && Dock.firstSize(1001, 0.5, 130, 130) == 501, "גודל הצד הראשון לפי היחס (1000 × 0.42 = 420)");
        check(Dock.firstSize(1000, 0.05, 130, 130) == 130 && Dock.firstSize(1000, 0.95, 130, 269) == 731, "אבל לא פחות מהגודל הקטן ביותר של כל צד (130 משמאל / 269 מימין)");
        check(Dock.firstSize(300, 0.5, 130, 269) == 98 && Dock.firstSize(300, 0.9, 130, 269) == 98 && Dock.firstSize(0, 0.5, 130, 130) == 0 && Dock.firstSize(-5, 0.5, 130, 130) == 0,
                "אין מקום לשני הצדדים: מתחלקים לפי הצורך (130 : 269), בלי גדלים שליליים");
        check(Dock.dragRatio(500, 1000, 0.3, 130, 130) == 0.5 && Dock.dragRatio(10, 1000, 0.3, 130, 130) == 0.13 && Dock.dragRatio(5000, 1000, 0.3, 130, 269) == 0.731,
                "גרירה: היחס לפי המקום החדש, ונעצרים בגודל הקטן ביותר של כל צד");
        check(Dock.dragRatio(150, 300, 0.4, 130, 269) == 0.4 && Dock.dragRatio(10, 10000, 0.3, 130, 130) == Dock.MIN_RATIO && Dock.dragRatio(9990, 10000, 0.3, 130, 130) == Dock.MAX_RATIO,
                "גרירה כשאין לאן לזוז — בלי שינוי; ובמסך רחב מאוד אף צד לא פחות מ-5%");
        boolean exact = true, kept = true;
        for (int total = 269; total <= 3000; total += 37)
            for (int pos = 130; pos <= total - 130; pos += 13) {
                double r = Dock.dragRatio(pos, total, 0.5, 130, 130);
                if (r > Dock.MIN_RATIO && r < Dock.MAX_RATIO && Dock.firstSize(total, r, 130, 130) != pos) exact = false;
                // הטקסט השמור (שלוש ספרות): אחרי פתיחה מחדש — אותו מקום, עד פיקסל-שניים
                Dock saved = Dock.parse(Dock.split(true, r, Dock.leaf("code"), Dock.leaf("view")).format(), Arrays.asList("code", "view"));
                if (saved == null || Math.abs(Dock.firstSize(total, saved.ratio, 130, 130) - Dock.firstSize(total, r, 130, 130)) > 2) kept = false;
            }
        check(exact, "הקו נשאר בדיוק מתחת לעכבר: היחס שנשמר בגרירה מחזיר בדיוק את אותו פיקסל (בכל גודל)");
        check(kept, "וגם אחרי שמירה כטקסט ופתיחה מחדש — אותו מקום (עד שני פיקסלים)");
    }

    // ------------------------------------------------------------ החלון

    static Viewer open(final int w, final int h) throws Exception {
        SwingUtilities.invokeAndWait(new Runnable() { public void run() { Viewer.open(); } });
        Viewer v = null;
        for (Frame f : Frame.getFrames()) if (f instanceof Viewer && f.isShowing()) v = (Viewer) f;
        if (v == null) throw new IllegalStateException("חלון ההרצה לא נפתח");
        final Viewer vv = v;
        // קודם נותנים לחלון להיפתח במקום שלו, ורק אז מזיזים; ובודקים שהוא באמת שם: מול xvfb בלי מנהל חלונות, הזזה מיד אחרי
        // הפתיחה לפעמים "נדרסת" (Java חושב שהחלון במקום הישן) — ואז העכבר של Robot לא פוגע בקווים
        settle();
        final Rectangle want = new Rectangle(40, 20, w, h);
        for (int i = 0; i < 6 && (i == 0 || !want.equals(vv.getBounds()) || !want.getLocation().equals(vv.getLocationOnScreen())); i++) {
            SwingUtilities.invokeAndWait(new Runnable() { public void run() { vv.setBounds(want); vv.validate(); } });
            settle();
        }
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

    // ------------------------------------------------------------ קווי הגרירה

    static Point center(Component c) {
        Point p = c.getLocationOnScreen();
        return new Point(p.x + c.getWidth() / 2, p.y + c.getHeight() / 2);
    }

    static void dragSash(DockSplit s, int dist, int moves) throws Exception {
        dragSash(s, dist, moves, null);
    }

    /** גרירה אמיתית של קו גרירה: dist פיקסלים (ימינה / למטה; שלילי — שמאלה / למעלה) ב-moves צעדים; shotName — צילום לפני השחרור */
    static void dragSash(DockSplit s, int dist, int moves, String shotName) throws Exception {
        Point p = center(s.sash);
        robot.mouseMove(p.x, p.y);
        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
        for (int i = 1; i <= moves; i++) robot.mouseMove(s.node.across ? p.x + dist * i / moves : p.x, s.node.across ? p.y : p.y + dist * i / moves);
        if (shotName != null) {
            settle();
            shot(shotName);
        }
        robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
        settle();
    }

    static void doubleClick(Component c) throws Exception {
        Point p = center(c);
        robot.mouseMove(p.x, p.y);
        for (int i = 0; i < 2; i++) {
            robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
            robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
        }
        settle();
    }

    /** המקום על המסך של כל החלקים של צד אחד של קו גרירה, יחד */
    static Rectangle area(Viewer v, Dock side) {
        Rectangle r = null;
        for (String id : side.leaves()) {
            Rectangle c = v.cardOnScreen(id);
            if (c != null) r = r == null ? c : r.union(c);
        }
        return r;
    }

    static int len(Rectangle r, boolean across) {
        return across ? r.width : r.height;
    }

    /** הגודל הקטן ביותר של צד של קו גרירה, בכיוון של הקו */
    static int minLen(Component c, boolean across) {
        return across ? c.getMinimumSize().width : c.getMinimumSize().height;
    }

    static Rectangle[] rects(Viewer v) {
        Rectangle[] r = new Rectangle[ALL.size()];
        for (int i = 0; i < r.length; i++) r[i] = v.cardOnScreen(ALL.get(i));
        return r;
    }

    static int showing(Viewer v) {
        int n = 0;
        for (Rectangle r : rects(v)) if (r != null && r.width > 0 && r.height > 0) n++;
        return n;
    }

    static String sizes(Viewer v) {
        StringBuilder sb = new StringBuilder();
        for (String id : ALL) {
            Rectangle r = v.cardOnScreen(id);
            sb.append(sb.length() > 0 ? ", " : "").append(id).append(' ').append(r == null ? "—" : r.width + "×" + r.height);
        }
        return sb.toString();
    }

    /** הגודל הקטן ביותר של חלק (לפי הכותרת שלו; לפחות MIN_W × MIN_H) */
    static java.awt.Dimension minOf(Viewer v, String id) {
        for (DockSplit s : v.dockSplits()) {
            if (s.node.a.isLeaf() && s.node.a.card.equals(id)) return s.first.getMinimumSize();
            if (s.node.b.isLeaf() && s.node.b.card.equals(id)) return s.second.getMinimumSize();
        }
        throw new IllegalStateException("אין חלק " + id);
    }

    /** אף חלק לא קטן מהגודל הקטן ביותר שלו */
    static boolean minOk(Viewer v) {
        for (String id : ALL) {
            Rectangle r = v.cardOnScreen(id);
            java.awt.Dimension m = minOf(v, id);
            if (r == null || r.width < m.width || r.height < m.height || m.width < DockSplit.MIN_W || m.height < DockSplit.MIN_H) return false;
        }
        return true;
    }

    /** הכותרת של כל חלק כולה בתוך החלק שלה (לא נחתכה ולא ירדה לשורה שנייה) — גם כשהחלק בגודל הקטן ביותר */
    static boolean titlesWhole(Viewer v) {
        for (String id : ALL) {
            Rectangle c = v.cardOnScreen(id), t = v.titleOnScreen(id);
            if (c == null || t == null || t.width <= 0 || !new Rectangle(c.x, c.y, c.width, 34).contains(t)) return false;
        }
        return true;
    }

    static String name(Dock n) {
        return n.isLeaf() ? n.card : n.leaves().toString().replace("[", "").replace("]", "").replace(", ", "+");
    }

    static String note(String problem) {
        return problem == null ? "" : " — " + problem;
    }

    /**
     * מה שעל המסך תואם לעץ: בכל קו גרירה הצד הראשון משמאל / מעל השני, הקו בדיוק ביניהם, שניהם באותו גובה / רוחב, וכל צד
     * בגודל שהיחס שבעץ קובע. (ב-Java 25, עם JSplitPane, הצדדים היו הפוכים — התצוגה משמאל — והקווים לא זזו)
     */
    static void structure(Viewer v, String where) {
        List<DockSplit> all = v.dockSplits();
        String bad = all.isEmpty() ? "אין קווי גרירה" : null;
        for (DockSplit s : all) {
            Rectangle a = area(v, s.node.a), b = area(v, s.node.b);
            Rectangle sash = s.sash.isShowing() ? new Rectangle(s.sash.getLocationOnScreen(), s.sash.getSize()) : null;
            boolean ok = a != null && b != null && sash != null && s.first.getParent() == s && s.second.getParent() == s;
            if (ok && s.node.across) ok = a.x + a.width == sash.x && sash.x + sash.width == b.x && sash.width == DockSplit.SASH && a.y == b.y && a.height == b.height && sash.height == a.height;
            else if (ok) ok = a.y + a.height == sash.y && sash.y + sash.height == b.y && sash.height == DockSplit.SASH && a.x == b.x && a.width == b.width && sash.width == a.width;
            if (!ok && bad == null) bad = "הקו בין " + name(s.node.a) + " לבין " + name(s.node.b) + ": " + a + " | " + sash + " | " + b;
        }
        if (bad == null) bad = modelOnScreen(v);
        if (bad == null && javaSplitPane(v)) bad = "יש JSplitPane בחלון (מ-Java 24 הוא מחליף צדדים בחלון מימין לשמאל)";
        check(bad == null, where + ": " + all.size() + " קווי גרירה, וכל צד במקום ובגודל שבעץ — הראשון משמאל / למעלה" + note(bad));
    }

    /** null — כל קו גרירה נמצא בדיוק במקום שהיחס שלו בעץ קובע (בתוך הגודל הקטן ביותר של כל צד); אחרת — מה לא תואם */
    static String modelOnScreen(Viewer v) {
        for (DockSplit s : v.dockSplits()) {
            Rectangle a = area(v, s.node.a), all = area(v, s.node);
            if (a == null || all == null) return "חלק לא על המסך";
            boolean across = s.node.across;
            int total = len(all, across) - DockSplit.SASH;
            int want = Dock.firstSize(total, s.node.ratio, minLen(s.first, across), minLen(s.second, across));
            if (len(a, across) != want) return name(s.node.a) + " " + len(a, across) + " במקום " + want + " (יחס " + s.node.ratio + " מתוך " + total + ")";
        }
        return null;
    }

    static boolean javaSplitPane(Container c) {
        for (Component k : c.getComponents())
            if (k instanceof javax.swing.JSplitPane || (k instanceof Container && javaSplitPane((Container) k))) return true;
        return false;
    }

    /**
     * כל קו גרירה (הראשי והפנימיים), לשני הכיוונים — גרירה אמיתית של dist פיקסלים לצד שיש בו יותר מקום (פחות, אם אין dist
     * פיקסלים עד הגודל הקטן ביותר של הצד שמתכווץ) ואחריה גרירה חזרה: בכל גרירה הקו זז בדיוק כמו העכבר, הצד שגדל גדל באותה מידה
     * שהצד השני קטן, שאר החלקים לא זזים, היחסים של שאר הקווים בעץ לא משתנים, והחלקים שבתוך כל צד נשארים ביחס שלהם
     */
    static void dragEvery(Viewer v, String where, int dist) throws Exception {
        int n = v.dockSplits().size();
        for (int i = 0; i < n; i++) {
            DockSplit s0 = v.dockSplits().get(i);
            boolean across = s0.node.across;
            int cur = len(area(v, s0.node.a), across), total = len(area(v, s0.node), across) - DockSplit.SASH;
            int roomUp = total - minLen(s0.second, across) - cur, roomDown = cur - minLen(s0.first, across);
            int first = roomUp >= roomDown ? 1 : -1, d = Math.min(dist, Math.max(roomUp, roomDown));
            for (int dir = first, pass = 0; pass < 2; pass++, dir = -dir) {
                DockSplit s = v.dockSplits().get(i);
                List<String> mine = s.node.leaves();
                Rectangle a0 = area(v, s.node.a), b0 = area(v, s.node.b);
                Rectangle[] r0 = rects(v);
                double[] ratios0 = ratios(v);
                dragSash(s, dir * d, 8);
                Rectangle a1 = area(v, s.node.a), b1 = area(v, s.node.b);
                Rectangle[] r1 = rects(v);
                double[] ratios1 = ratios(v);
                String bad = d < 15 ? "אין לאן לגרור (רק " + d + " פיקסלים עד הגודל הקטן ביותר)" : null;
                int da = len(a1, across) - len(a0, across), db = len(b1, across) - len(b0, across);
                if (bad == null && (da != dir * d || db != -dir * d)) bad = "הצד הראשון השתנה ב-" + da + " והשני ב-" + db;
                for (int k = 0; k < r0.length && bad == null; k++)
                    if (!mine.contains(ALL.get(k)) && !java.util.Objects.equals(r0[k], r1[k])) bad = "חלק שלא ליד הקו זז: " + ALL.get(k);
                for (int k = 0; k < ratios0.length && bad == null; k++)
                    if (k != i && ratios0[k] != ratios1[k]) bad = "היחס של קו אחר השתנה (" + ratios0[k] + " ← " + ratios1[k] + ")";
                if (bad == null && ratios0[i] == ratios1[i]) bad = "היחס בעץ לא השתנה";
                if (bad == null) bad = modelOnScreen(v);
                String dirName = across ? (dir > 0 ? "ימינה" : "שמאלה") : (dir > 0 ? "למטה" : "למעלה");
                check(bad == null, where + ": הקו " + (i == 0 ? "הראשי" : "הפנימי") + " בין ״" + name(s.node.a) + "״ לבין ״" + name(s.node.b) + "״ — " + d + " " + dirName + ": "
                        + len(a0, across) + "|" + len(b0, across) + " ← " + len(a1, across) + "|" + len(b1, across) + note(bad));
            }
        }
    }

    static double[] ratios(Viewer v) {
        List<DockSplit> all = v.dockSplits();
        double[] r = new double[all.size()];
        for (int i = 0; i < r.length; i++) r[i] = all.get(i).node.ratio;
        return r;
    }

    /** הסידור של המשתמש על המסך: התצוגה משמאל לכל הגובה; מימינה למעלה הפלט ומעקב המשתנים זה ליד זה, ומתחתיהם הקוד */
    static void userOnScreen(Viewer v) {
        Rectangle code = v.cardOnScreen("code"), view = v.cardOnScreen("view"), out = v.cardOnScreen("out"), vars = v.cardOnScreen("vars");
        check(view.x < out.x && out.x < vars.x && out.y == vars.y && out.y == view.y && code.y > out.y && code.x == out.x && view.height == code.y + code.height - out.y
                && code.width == out.width + DockSplit.SASH + vars.width, "על המסך: התצוגה משמאל לכל הגובה, מימינה למעלה הפלט ומעקב המשתנים, ומתחתיהם הקוד");
    }

    static boolean recursionShowing(Container c) {
        for (Component k : c.getComponents())
            if ((k instanceof RecursionPanel && k.isShowing()) || (k instanceof Container && recursionShowing((Container) k))) return true;
        return false;
    }
}
