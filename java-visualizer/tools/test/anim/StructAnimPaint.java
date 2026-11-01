package unit4.collectionsLib;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Arrays;
import java.util.List;

import javax.imageio.ImageIO;

import unit4.collectionsLib.Model.Cell;
import unit4.collectionsLib.Model.ChainView;
import unit4.collectionsLib.Model.LinearView;
import unit4.collectionsLib.Model.Mark;
import unit4.collectionsLib.Model.Step;
import unit4.collectionsLib.Model.View;

/**
 * בדיקה בלי חלון להנפשה של הכנסה / הוצאה (StructAnim + StructPanel): מציירים כמה רגעים מכל הנפשה לתמונות,
 * ובודקים שהאיבר שזז באמת זז בכיוון הנכון מרגע לרגע — push נופל למטה, pop עף למעלה, insert נכנס מימין, remove יוצא שמאלה,
 * חוליה נכנסת לשרשרת / יורדת ממנה — ושבסוף ההנפשה הציור זהה בדיוק לציור הרגיל של הצעד (שום "רוח רפאים" לא נשארת).
 * וגם: בלי הנפשה אחורה / כשלא השתנה כלום / כשרק מבנה פנימי השתנה, צעד חדש באמצע מחליף את ההנפשה, וקיצור בהפעלה מהירה.
 * <pre>  java -Djava.awt.headless=true -cp .:dsviz.jar unit4.collectionsLib.StructAnimPaint [תיקייה לתמונות]</pre>
 */
public class StructAnimPaint {
    static int fails = 0;
    static File out;
    static final int W = 760, H = 460;

    static void check(boolean ok, String what) {
        System.out.println((ok ? "  ok  " : "  FAIL ") + what);
        if (!ok) fails++;
    }

    // ------------------------------------------------------------ בניית צעדים

    static Step step(View... views) {
        Step s = new Step();
        s.views.addAll(Arrays.asList(views));
        return s;
    }

    static LinearView linear(boolean stack, String title, int markIndex, Mark mark, String ghost, String... items) {
        LinearView v = new LinearView();
        v.stack = stack;
        v.title = title;
        v.markIndex = markIndex;
        v.mark = mark;
        v.ghost = ghost;
        v.focus = mark != Mark.NONE || ghost != null;
        v.items.addAll(Arrays.asList(items));
        for (int i = 0; i < items.length; i++) v.objs.add(null);
        return v;
    }

    /** חוליה: {id, ערך, סימון} */
    static Cell cell(int id, String value, Mark mark) {
        Cell c = new Cell();
        c.id = id;
        c.value = value;
        c.mark = mark;
        return c;
    }

    static ChainView chain(String title, Cell... cells) {
        ChainView c = new ChainView();
        c.title = title;
        c.cells.addAll(Arrays.asList(cells));
        return c;
    }

    // ------------------------------------------------------------ ציור ומדידה

    static StructPanel panel() {
        StructPanel p = new StructPanel();
        p.offscreen = true;
        p.setSize(W, H);
        return p;
    }

    static BufferedImage render(StructPanel p) {
        BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        p.paint(g);
        g.dispose();
        return img;
    }

    /** הרגע t של ההנפשה (נשמר גם כתמונה) */
    static BufferedImage frame(StructPanel p, float t, String name) {
        p.anim().at(t);
        BufferedImage img = render(p);
        save(img, name + "-" + Math.round(t * 100));
        return img;
    }

    static void save(BufferedImage img, String name) {
        if (out == null) return;
        try {
            ImageIO.write(img, "png", new File(out, name + ".png"));
        } catch (Exception e) {
            System.out.println("  (לא נשמרה התמונה " + name + ": " + e + ")");
        }
    }

    /** המלבן של כל הפיקסלים בצבע הזה בדיוק (המילוי של תא) — או null */
    static Rectangle bbox(BufferedImage img, Color c) {
        int want = c.getRGB() & 0xFFFFFF, x0 = W, y0 = H, x1 = -1, y1 = -1;
        for (int y = 0; y < H; y++)
            for (int x = 0; x < W; x++)
                if ((img.getRGB(x, y) & 0xFFFFFF) == want) {
                    x0 = Math.min(x0, x); y0 = Math.min(y0, y); x1 = Math.max(x1, x); y1 = Math.max(y1, y);
                }
        return x1 < 0 ? null : new Rectangle(x0, y0, x1 - x0 + 1, y1 - y0 + 1);
    }

    static int count(BufferedImage img, Color c) {
        int want = c.getRGB() & 0xFFFFFF, n = 0;
        for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) if ((img.getRGB(x, y) & 0xFFFFFF) == want) n++;
        return n;
    }

    /** כמה פיקסלים שונים בין שתי תמונות */
    static int diff(BufferedImage a, BufferedImage b) {
        int n = 0;
        for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) if (a.getRGB(x, y) != b.getRGB(x, y)) n++;
        return n;
    }

    /** יש ציור (לא לבן) בתוך המלבן */
    static boolean ink(BufferedImage img, Rectangle r) {
        for (int y = Math.max(0, r.y); y < Math.min(H, r.y + r.height); y++)
            for (int x = Math.max(0, r.x); x < Math.min(W, r.x + r.width); x++)
                if ((img.getRGB(x, y) & 0xFFFFFF) != 0xFFFFFF) return true;
        return false;
    }

    static Rectangle exitAt(StructPanel p) {
        return p.exitBounds.isEmpty() ? null : new Rectangle(p.exitBounds.get(0));
    }

    /** הצעד החדש בלי הנפשה — הציור "הרגיל" שאליו ההנפשה צריכה להגיע */
    static BufferedImage still(Step s) {
        StructPanel q = panel();
        q.setStep(s);
        render(q);   // פעם אחת כדי שההגדלה תיקבע
        return render(q);
    }

    /** מתחילים הנפשה מ-a אל b; מחזיר את הלוח (או null אם לא התחילה) */
    static StructPanel start(Step a, Step b) {
        StructPanel p = panel();
        p.setStep(a);
        render(p);
        p.setStep(b, true, 0);
        return p.animating() ? p : null;
    }

    static void endsClean(StructPanel p, Step b, String name) {
        BufferedImage end = frame(p, 1f, name);
        int d = diff(end, still(b));
        check(d == 0 && p.exitBounds.isEmpty(), name + ": בסוף ההנפשה — בדיוק הציור הרגיל של הצעד, בלי שאריות (" + d + " פיקסלים שונים)");
    }

    public static void main(String[] args) throws Exception {
        if (args.length > 0) {
            out = new File(args[0]);
            out.mkdirs();
        }
        Color GREEN = StructPanel.NEW_C, YELLOW = StructPanel.READ_C, PINK = StructPanel.GHOST_BG;

        // --- מחסנית: push — האיבר החדש (ירוק) נופל מלמעלה, והאיברים שמתחתיו נדחפים מטה
        Step s0 = step(linear(true, "s", -1, Mark.NONE, null, "7", "3"));
        Step s1 = step(linear(true, "s", 0, Mark.NEW, null, "9", "7", "3"));
        StructPanel p = start(s0, s1);
        check(p != null, "push: יש הנפשה");
        if (p != null) {
            BufferedImage before = render(panelWith(s0));
            Rectangle a = bbox(frame(p, 0.25f, "push"), GREEN), b = bbox(frame(p, 0.6f, "push"), GREEN), c = bbox(frame(p, 1f, "push"), GREEN);
            Rectangle fin = bbox(still(s1), GREEN);
            check(a != null && b != null && c != null && a.y < b.y && b.y < c.y && c.equals(fin),
                    "push: האיבר החדש יורד מרגע לרגע (y=" + y(a) + " → " + y(b) + " → " + y(c) + ") ונוחת בראש המחסנית (" + y(fin) + ")");
            check(a != null && a.y + a.height <= bbox(before, StructPanel.BOX).y + 2, "push: בהתחלה הוא מעל הפתח של המחסנית (עוד לא בפנים)");
            // "7" היה בראש: בהתחלה במקום הישן, ובסוף משבצת אחת מתחת — נדחף
            frame(p, 0.05f, "push");
            int d0 = diff(render(p), before);
            check(d0 < W * H / 40, "push: הרגע הראשון כמעט זהה לצעד הקודם — בלי קפיצה (" + d0 + " פיקסלים שונים)");
            endsClean(p, s1, "push");
        }

        // --- מחסנית: pop — האיבר העליון (ירוק, מהצעד הקודם) מורם החוצה, מאדים ונעלם; נשאר הסימן "הוצא"
        Step s2 = step(linear(true, "s", 0, Mark.NONE, "9", "7", "3"));
        p = start(s1, s2);
        check(p != null, "pop: יש הנפשה");
        if (p != null) {
            frame(p, 0.08f, "pop");
            Rectangle e0 = exitAt(p);
            BufferedImage f1 = frame(p, 0.4f, "pop");
            Rectangle e1 = exitAt(p);
            BufferedImage f2 = frame(p, 0.75f, "pop");
            Rectangle e2 = exitAt(p);
            check(e0 != null && e1 != null && e2 != null && e0.y > e1.y && e1.y > e2.y,
                    "pop: האיבר שהוצא עולה (y=" + y(e0) + " → " + y(e1) + " → " + y(e2) + ")");
            check(e1 != null && ink(f1, e1) && e2 != null && e2.width < e0.width, "pop: הוא מצויר בדרך, ומתכווץ (רוחב " + w(e0) + " → " + w(e2) + ")");
            int pink1 = count(f1, PINK), pinkEnd = count(still(s2), PINK);
            check(pink1 < pinkEnd, "pop: הסימן \"הוצא\" מופיע בהדרגה (" + pink1 + " → " + pinkEnd + " פיקסלים)");
            BufferedImage end = frame(p, 1f, "pop");
            check(count(end, GREEN) == 0 && count(f2, GREEN) + count(f1, GREEN) >= 0, "pop: בסוף — האיבר נמחק (אין ממנו כלום)");
            endsClean(p, s2, "pop");
        }

        // --- תור: insert — האיבר החדש (ירוק) נכנס מימין, מבחוץ, דרך הפתח של הזנב
        Step q0 = step(linear(false, "q", -1, Mark.NONE, null, "4", "5"));
        Step q1 = step(linear(false, "q", 2, Mark.NEW, null, "4", "5", "6"));
        p = start(q0, q1);
        check(p != null, "insert: יש הנפשה");
        if (p != null) {
            Rectangle a = bbox(frame(p, 0.35f, "insert"), GREEN), b = bbox(frame(p, 0.6f, "insert"), GREEN), c = bbox(frame(p, 1f, "insert"), GREEN);
            Rectangle fin = bbox(still(q1), GREEN);
            check(a != null && b != null && c != null && a.x > b.x && b.x >= c.x - 8 && c.equals(fin),
                    "insert: האיבר החדש זז שמאלה אל הזנב (x=" + x(a) + " → " + x(b) + " → " + x(c) + ")");
            check(a != null && fin != null && a.x > fin.x + fin.width / 2, "insert: באמצע הדרך הוא עוד מחוץ למקום שלו (מימין)");
            endsClean(p, q1, "insert");
        }

        // --- תור: remove — הראש (צהוב: נקרא בצעד הקודם) יוצא שמאלה דרך היציאה ונמחק; השאר מתקדמים לראש
        Step q2 = step(linear(false, "q", 0, Mark.READ, null, "4", "5", "6"));
        Step q3 = step(linear(false, "q", 0, Mark.NONE, "4", "5", "6"));
        p = start(q2, q3);
        check(p != null, "remove: יש הנפשה");
        if (p != null) {
            frame(p, 0.1f, "remove");
            Rectangle e0 = exitAt(p);
            frame(p, 0.4f, "remove");
            Rectangle e1 = exitAt(p);
            frame(p, 0.75f, "remove");
            Rectangle e2 = exitAt(p);
            // (כל התור גם זז קצת ימינה כשהוא מתקצר — הוא ממורכז בחלון; לכן בשלב השני בודקים רק שהוא נשאר בחוץ ומתכווץ)
            check(e0 != null && e1 != null && e2 != null && e1.x < e0.x - 40 && e2.x < e0.x - 40 && e2.width < e1.width,
                    "remove: הראש יוצא שמאלה דרך היציאה ומתכווץ (x=" + x(e0) + " → " + x(e1) + " → " + x(e2) + ", רוחב " + w(e1) + " → " + w(e2) + ")");
            endsClean(p, q3, "remove");
            check(count(still(q3), YELLOW) == 0, "remove: אחרי ההנפשה אין זכר לאיבר שהוצא (רק הסימן \"הוצא\")");
        }

        // --- רשימה: חוליה 3 (ירוקה) נכנסת בין 2 ל-4 — עולה מהענף אל השורה, ו-4 (צהובה) זזה ימינה לפנות מקום
        ChainView l0 = chain("list", cell(1, "1", Mark.NONE), cell(2, "2", Mark.NONE), cell(3, "4", Mark.READ));
        ChainView br = chain(null, cell(4, "3", Mark.NEW));
        br.joinTo = 2;
        l0.branches.add(br);
        Step c0 = step(l0);
        Step c1 = step(chain("list", cell(1, "1", Mark.NONE), cell(2, "2", Mark.CHANGED), cell(4, "3", Mark.NEW), cell(3, "4", Mark.READ)));
        listInsert(c0, c1, "list-insert");

        // אותו דבר בלי זהות לחוליות (הרצה של C#) — לפי הערכים
        ChainView l0b = chain("list", cell(0, "1", Mark.NONE), cell(0, "2", Mark.NONE), cell(0, "4", Mark.READ));
        ChainView brb = chain(null, cell(0, "3", Mark.NEW));
        brb.joinTo = 2;
        l0b.branches.add(brb);
        listInsert(step(l0b), step(chain("list", cell(0, "1", Mark.NONE), cell(0, "2", Mark.CHANGED), cell(0, "3", Mark.NEW), cell(0, "4", Mark.READ))),
                "list-insert-cs");

        // --- רשימה: חוליה 3 נעלמת (אין אליה הפניה) — נופלת מהשרשרת, מאדימה ונמחקת; 4 (צהובה) זזה שמאלה וסוגרת את הרווח
        Step d0 = step(chain("list", cell(1, "1", Mark.NONE), cell(2, "2", Mark.NONE), cell(4, "3", Mark.NONE), cell(3, "4", Mark.READ)));
        Step d1 = step(chain("list", cell(1, "1", Mark.NONE), cell(2, "2", Mark.CHANGED), cell(3, "4", Mark.READ)));
        p = start(d0, d1);
        check(p != null, "list-remove: יש הנפשה");
        if (p != null) {
            Rectangle y0 = bbox(frame(p, 0.15f, "list-remove"), YELLOW);
            Rectangle e0 = exitAt(p);
            frame(p, 0.5f, "list-remove");
            Rectangle e1 = exitAt(p);
            Rectangle y2 = bbox(frame(p, 0.8f, "list-remove"), YELLOW);
            check(e0 != null && e1 != null && e1.y > e0.y, "list-remove: החוליה שהוסרה נופלת מטה (y=" + y(e0) + " → " + y(e1) + ")");
            check(y0 != null && y2 != null && y2.x < y0.x, "list-remove: החוליה 4 זזה שמאלה וסוגרת את הרווח (x=" + x(y0) + " → " + x(y2) + ")");
            endsClean(p, d1, "list-remove");
        }

        // --- רשימה: חוליה 3 מנותקת אבל עדיין קיימת ואין אליה מצביע ("אבדה") — יורדת לענף שמתחת, ונשארת אדומה ("אבדה")
        Cell lost = cell(4, "3", Mark.NONE);
        lost.lost = true;
        ChainView l2 = chain("list", cell(1, "1", Mark.NONE), cell(2, "2", Mark.CHANGED), cell(3, "4", Mark.READ));
        ChainView br2 = chain(null, lost);
        br2.joinTo = 2;
        l2.branches.add(br2);
        l2.lostNote = "החוליה 3 אבדה";
        l2.lostHint = "אם רצית למחוק אותה — זה בדיוק מה שקרה";
        Step d2 = step(l2);
        p = start(d0, d2);
        check(p != null, "list-unlink: יש הנפשה");
        if (p != null) {
            Rectangle a = bbox(frame(p, 0.3f, "list-unlink"), PINK), b = bbox(frame(p, 0.7f, "list-unlink"), PINK);
            Rectangle c = bbox(frame(p, 1f, "list-unlink"), PINK);
            check(a != null && b != null && c != null && a.y < b.y && b.y <= c.y, "list-unlink: החוליה שאבדה יורדת מהשרשרת לענף (y=" + y(a) + " → " + y(b) + " → " + y(c) + ")");
            check(p.exitBounds.isEmpty(), "list-unlink: היא לא נמחקת — עדיין בזיכרון, ומסומנת \"אבדה\" (כמו בציור הרגיל)");
            endsClean(p, d2, "list-unlink");
        }

        // --- new Node(3) למשתנה חדש: מבנה חדש מעל הרשימה — הרשימה (4 צהובה) יורדת לפנות מקום, ורק אז החוליה החדשה מופיעה
        Step n0 = step(chain("list", cell(1, "1", Mark.NONE), cell(2, "2", Mark.NONE), cell(3, "4", Mark.READ)));
        Step n1 = step(chain("n", cell(4, "3", Mark.NEW)), chain("list", cell(1, "1", Mark.NONE), cell(2, "2", Mark.NONE), cell(3, "4", Mark.READ)));
        p = start(n0, n1);
        check(p != null, "new-node: יש הנפשה");
        if (p != null) {
            BufferedImage f0 = frame(p, 0.3f, "new-node"), f2 = frame(p, 0.8f, "new-node");
            Rectangle a = bbox(f0, YELLOW), b = bbox(f2, YELLOW);
            check(count(f0, GREEN) == 0 && a != null && b != null && b.y > a.y,
                    "new-node: הרשימה יורדת (y=" + y(a) + " → " + y(b) + ") והחוליה החדשה עוד לא מצוירת מעליה");
            endsClean(p, n1, "new-node");
            check(count(still(n1), GREEN) > 0, "new-node: בסוף — החוליה החדשה במקומה");
        }

        // --- בלי הנפשה: אחורה / קפיצה, צעד בלי שינוי במבנים, ומבנה בתוך תא שהשתנה (התור החיצוני לא השתנה)
        StructPanel back = panel();
        back.setStep(s1);
        render(back);
        back.setStep(s0, false, 0);
        check(!back.animating(), "אחורה / קפיצה — בלי הנפשה");
        back.setStep(step(linear(true, "s", -1, Mark.NONE, null, "7", "3")), true, 0);
        check(!back.animating(), "צעד שבו לא נוסף ולא הוצא כלום — בלי הנפשה");
        LinearView inner0 = linear(true, null, -1, Mark.NONE, null, "1"), inner1 = linear(true, null, 0, Mark.NEW, null, "8", "1");
        LinearView outer0 = linear(false, "qs", -1, Mark.NONE, null, "[1]", "[2]"), outer1 = linear(false, "qs", -1, Mark.NONE, null, "[8,1]", "[2]");
        outer0.subs.add(inner0);
        outer0.subs.add(linear(true, null, -1, Mark.NONE, null, "2"));
        outer1.subs.add(inner1);
        outer1.subs.add(linear(true, null, -1, Mark.NONE, null, "2"));
        check(start(step(outer0), step(outer1)) == null, "push למחסנית שבתוך תור — התור החיצוני לא מונפש (לא נוסף לו כלום)");
        // תור של מחסניות: insert של מחסנית — מונפש, והמחסנית שבתא מצוירת גם בדרך
        LinearView outer2 = linear(false, "qs", 2, Mark.NEW, null, "[8,1]", "[2]", "[5]");
        outer2.subs.add(inner1);
        outer2.subs.add(linear(true, null, -1, Mark.NONE, null, "2"));
        outer2.subs.add(linear(true, null, -1, Mark.NONE, null, "5"));
        p = start(step(outer1), step(outer2));
        check(p != null, "תור של מחסניות: insert של מחסנית — מונפש");
        if (p != null) {
            frame(p, 0.5f, "nested");
            endsClean(p, step(outer2), "nested");
        }

        // --- צעד חדש באמצע הנפשה: ההנפשה הקודמת נגמרת, והחדשה מתחילה מהצעד שהיה מוצג
        StructPanel mid = panel();
        mid.setStep(s0);
        render(mid);
        mid.setStep(s1, true, 0);
        StructAnim first = mid.anim();
        render(mid);
        mid.setStep(s2, true, 0);
        check(first != null && first.finished() && mid.anim() != null && mid.anim().from == s1 && mid.anim().to == s2,
                "צעד חדש באמצע הנפשה: הקודמת נעצרת, והחדשה מתחילה מהצעד שהוצג (push → pop)");
        mid.setStep(s1, false, 0);
        check(!mid.animating(), "ואחורה באמצע — ההנפשה נעצרת מיד");

        // --- הזמן: 0.46 שנ'; בהפעלה אוטומטית — קצר יותר מהקצב; מהירה מאוד — בלי
        int full = StructAnim.duration(0), normal = StructAnim.duration(740), fast = StructAnim.duration(420), veryFast = StructAnim.duration(100);
        check(full >= 350 && full <= 500 && normal <= 740 * 0.65 && fast > 0 && fast < 420 * 0.65 && veryFast == 0,
                "הזמן: " + full + " מ\"ש; בהפעלה כל 740 — " + normal + ", כל 420 — " + fast + ", כל 100 — " + veryFast + " (בלי הנפשה)");

        System.out.println(fails == 0 ? "MARK-END" : "נכשלו " + fails);
        System.exit(fails == 0 ? 0 : 1);
    }

    static void listInsert(Step c0, Step c1, String name) {
        StructPanel p = start(c0, c1);
        check(p != null, name + ": יש הנפשה");
        if (p == null) return;
        Color GREEN = StructPanel.NEW_C, YELLOW = StructPanel.READ_C;
        BufferedImage f0 = frame(p, 0.2f, name), f1 = frame(p, 0.55f, name), f2 = frame(p, 1f, name);
        Rectangle a = bbox(f0, GREEN), b = bbox(f1, GREEN), c = bbox(f2, GREEN);
        check(a != null && b != null && c != null && a.y > b.y && b.y > c.y, name + ": החוליה החדשה עולה אל השורה של הרשימה (y=" + y(a) + " → " + y(b) + " → " + y(c) + ")");
        Rectangle ya = bbox(f0, YELLOW), yc = bbox(f2, YELLOW);
        check(ya != null && yc != null && yc.x > ya.x, name + ": החוליה 4 זזה ימינה כדי לפנות מקום (x=" + x(ya) + " → " + x(yc) + ")");
        endsClean(p, c1, name);
    }

    static StructPanel panelWith(Step s) {
        StructPanel q = panel();
        q.setStep(s);
        render(q);
        return q;
    }

    static String y(Rectangle r) { return r == null ? "-" : String.valueOf(r.y); }
    static String x(Rectangle r) { return r == null ? "-" : String.valueOf(r.x); }
    static String w(Rectangle r) { return r == null ? "-" : String.valueOf(r.width); }
}
