package unit4.collectionsLib;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

import javax.swing.JScrollPane;
import javax.swing.JTextPane;

/**
 * בדיקה בלי חלון לציור של חץ "חוזרים לראש הלולאה" (LoopArrow.paint):
 * החץ מצויר משמאל לקוד של הלולאה (לא על הטקסט), יוצא מהסוגר ונכנס לשורת הלולאה; כשהשקיפות 0 — לא מצויר כלום;
 * כשהשורה שחוזרים אליה לא נראית (גלילה) — אין חץ; וההנפשה מתקצרת בהפעלה אוטומטית מהירה.
 */
public class LoopArrowPaint {
    static int fails = 0;

    static void check(boolean ok, String what) {
        System.out.println((ok ? "  ok  " : "  FAIL ") + what);
        if (!ok) fails++;
    }

    static final List<String> CODE = Arrays.asList(
            "public class A {",
            "    static int run(int n) {",
            "        int sum = 0;",
            "        for (int i = 0; i < n; i++) {",
            "            sum += i;",
            "            sum++;",
            "        }",
            "        int d = 2;",
            "        do {",
            "            d--;",
            "        } while (d > 0);",
            "        return sum;",
            "    }",
            "}");

    public static void main(String[] args) {
        JTextPane c = new JTextPane();
        c.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 14));
        c.setMargin(new Insets(6, 10, 6, 10));
        StringBuilder b = new StringBuilder();
        for (String l : CODE) b.append(b.length() > 0 ? "\n" : "").append(l);
        c.setText(b.toString());
        c.setSize(640, 420);
        List<LoopViz.Loop> loops = LoopViz.parse(CODE);
        LoopViz.Loop forLoop = null, doLoop = null;
        for (LoopViz.Loop l : loops) {
            if (l.kind.equals("for")) forLoop = l;
            if (l.kind.equals("do")) doLoop = l;
        }
        check(forLoop != null && forLoop.header == 4 && forLoop.end == 7, "הלולאה for נמצאה (שורות 4-7)");
        check(doLoop != null && doLoop.header == 9 && doLoop.end == 11, "הלולאה do-while נמצאה (שורות 9-11)");
        if (forLoop == null || doLoop == null) System.exit(1);

        // for: מהסוגר (שורה 7) אל שורת ה-for (4)
        LoopArrow.Jump j = jump(forLoop, 6, 4, 7);
        BufferedImage img = canvas(c);
        Rectangle r = LoopArrow.paint(img.createGraphics(), c, j, 1f, -1f, 1f, Collections.<Integer>emptySet());
        int xCode = CallPaint.textStart(c, 4);   // איפה מתחיל הקוד של הלולאה
        Rectangle head = CondPaint.lineRect(c, 4), tail = CondPaint.lineRect(c, 7);
        check(r != null, "for: החץ צויר");
        check(maxInkX(img) < xCode, "for: הכול משמאל לקוד (עד x=" + maxInkX(img) + ", הקוד מתחיל ב-" + xCode + ")");
        check(ink(img, xCode - 10, head.y, 9, head.height), "for: ראש החץ נכנס לשורת ה-for");
        check(ink(img, xCode - 12, tail.y, 10, tail.height), "for: החץ יוצא מהסוגר של הלולאה");
        check(inkRows(img, head.y + head.height, tail.y), "for: הקשת עוברת בשוליים לאורך הגוף");

        // באמצע ההנפשה: רק החלק התחתון — ראש החץ עוד לא הגיע לשורת ה-for
        img = canvas(c);
        LoopArrow.paint(img.createGraphics(), c, j, 0.3f, -1f, 1f, Collections.<Integer>emptySet());
        check(!ink(img, xCode - 8, head.y, 7, head.height / 2) && ink(img, 0, tail.y, xCode, tail.height), "באמצע הציור: החץ עוד לא הגיע לשורת הלולאה");

        // שקיפות 0 / עוד לא התחיל — לא מצויר כלום
        img = canvas(c);
        LoopArrow.paint(img.createGraphics(), c, j, 1f, -1f, 0f, Collections.<Integer>emptySet());
        LoopArrow.paint(img.createGraphics(), c, j, 0f, -1f, 1f, Collections.<Integer>emptySet());
        check(maxInkX(img) < 0, "בסוף הדעיכה (וגם לפני ההתחלה) — לא נשאר כלום");

        // do-while: מה-while שבסוף (11) אל תחילת הגוף (10)
        img = canvas(c);
        r = LoopArrow.paint(img.createGraphics(), c, jump(doLoop, 11, 10, 11), 1f, -1f, 1f, Collections.<Integer>emptySet());
        Rectangle body = CondPaint.lineRect(c, 10);
        int xBody = CallPaint.textStart(c, 10);
        check(r != null && maxInkX(img) < xBody && ink(img, xBody - 10, body.y, 9, body.height), "do-while: החץ נכנס לתחילת הגוף, משמאל לקוד");

        // יש בשורה גם חץ של פעולת עזר — החץ נכנס מתחת לאמצע השורה
        img = canvas(c);
        LoopArrow.paint(img.createGraphics(), c, j, 1f, -1f, 1f, new HashSet<Integer>(Arrays.asList(4)));
        check(ink(img, xCode - 8, head.y + head.height / 2 + 3, 6, head.height / 2) && !ink(img, xCode - 6, head.y, 4, head.height / 2 - 2),
                "עם חץ של פעולת עזר באותה שורה: החץ של הלולאה נכנס מתחת לאמצע");

        // גלילה: שורת הלולאה מעל מה שרואים — אין חץ
        JScrollPane sp = new JScrollPane(c);
        sp.setSize(640, 90);
        sp.doLayout();
        sp.getViewport().setViewPosition(new Point(0, CondPaint.lineRect(c, 9).y));
        img = canvas(c);
        r = LoopArrow.paint(img.createGraphics(), c, j, 1f, -1f, 1f, Collections.<Integer>emptySet());
        check(r == null && maxInkX(img) < 0, "שורת הלולאה לא נראית (גללו למטה) — בלי חץ");

        // ההנפשה: ~1.4 שנ', ובהפעלה אוטומטית מהירה מתקצרת כדי להיגמר לפני הצעד הבא
        LoopArrow.Anim anim = new LoopArrow.Anim(c);
        anim.start(j, 0);
        int full = anim.duration();
        anim.start(j, 420);
        int fast = anim.duration();
        anim.start(null, 0);
        check(full == 1400 && fast <= 420 && fast > 0 && !anim.active(), "ההנפשה: " + full + " מ\"ש, ובהפעלה כל 420 מ\"ש — " + fast + " מ\"ש; צעד בלי חזרה — החץ נעלם");

        System.out.println(fails == 0 ? "MARK-END" : "נכשלו " + fails);
        System.exit(fails == 0 ? 0 : 1);
    }

    static LoopArrow.Jump jump(LoopViz.Loop l, int from, int to, int bottom) {
        LoopArrow.Jump j = new LoopArrow.Jump();
        j.loop = l;
        j.from = from;
        j.to = to;
        j.bottom = bottom;
        j.fileKey = "A.java";
        return j;
    }

    static BufferedImage canvas(JTextPane c) {
        BufferedImage img = new BufferedImage(c.getWidth(), c.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, img.getWidth(), img.getHeight());
        g.dispose();
        return img;
    }

    /** הפיקסל הימני ביותר שצויר (-1 = כלום) */
    static int maxInkX(BufferedImage img) {
        int max = -1;
        for (int y = 0; y < img.getHeight(); y++)
            for (int x = 0; x < img.getWidth(); x++)
                if ((img.getRGB(x, y) & 0xFFFFFF) != 0xFFFFFF) max = Math.max(max, x);
        return max;
    }

    /** יש צבע (לא לבן) במלבן */
    static boolean ink(BufferedImage img, int x0, int y0, int w, int h) {
        for (int y = Math.max(0, y0); y < Math.min(img.getHeight(), y0 + h); y++)
            for (int x = Math.max(0, x0); x < Math.min(img.getWidth(), x0 + w); x++)
                if ((img.getRGB(x, y) & 0xFFFFFF) != 0xFFFFFF) return true;
        return false;
    }

    /** בכל שורת פיקסלים בין y1 ל-y2 יש צבע (הקשת רציפה) */
    static boolean inkRows(BufferedImage img, int y1, int y2) {
        for (int y = y1; y < y2; y++) if (!ink(img, 0, y, img.getWidth(), 1)) return false;
        return true;
    }
}
