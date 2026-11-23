package unit4.collectionsLib;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.imageio.ImageIO;
import javax.swing.JScrollPane;
import javax.swing.JTextPane;

/**
 * בדיקה בלי חלון לציור של "הלולאה לא רצה אף לא פעם אחת" (LoopSkip.paint): התגית מימין לשורת הלולאה ולא על הקוד,
 * הנורה הצהובה דולקת בעוצמה בשיא ההבהוב וכמעט כבויה בשפל שלו, התגית באה אחרי תגיות שכבר יש בשורה, ובשורה ארוכה — לא על הקוד.
 * עם תיקייה כארגומנט — שומר תמונות של חלון הקוד האמיתי (CodePane) בשיא ההבהוב ובשפל שלו.
 */
public class LoopSkipPaint {
    static int fails = 0;

    static void check(boolean ok, String what) {
        System.out.println((ok ? "  ok  " : "  FAIL ") + what);
        if (!ok) fails++;
    }

    static final List<String> CODE = Arrays.asList(
            "public class A {",
            "    static int run(int k) {",
            "        int sum = 0;",
            "        while (k > 5) {",
            "            sum += k;",
            "            k--;",
            "        }",
            "        return sum;",
            "    }",
            "}");

    public static void main(String[] args) throws Exception {
        Theme.install();
        check(LoopSkip.TEXT.equals("הלולאה לא רצה אף לא פעם אחת"), "הנוסח של ההודעה");
        check(LoopSkip.blink(0) >= 0f && LoopSkip.blink(0) <= 1f && Math.abs(LoopSkip.blink(250) - LoopSkip.blink(750)) > 0.9f,
                "ההבהוב: בין 0 ל-1, ובמחזור של שנייה (רבע מחזור — שיא, שלושה רבעים — שפל)");

        JTextPane c = pane(CODE);
        int line = 4;
        Rectangle row = CondPaint.lineRect(c, line);
        int endX = CondPaint.lineEndX(c, line);

        // שיא ההבהוב: התגית מימין לקוד, והנורה צהובה
        Map<Integer, Integer> used = new HashMap<Integer, Integer>();
        BufferedImage on = canvas(c);
        LoopSkip.paint(on.createGraphics(), c, line, used, 1f);
        check(inkIn(on, 0, row.y, endX + 10, row.height) == 0, "בשיא ההבהוב: שום דבר לא מצויר על הקוד (עד x=" + (endX + 10) + ")");
        check(inkIn(on, endX + 10, row.y, c.getWidth() - endX - 10, row.height) > 200, "התגית מצוירת מימין לשורת הלולאה");
        check(inkOutside(on, row.y - 1, row.height + 2) == 0, "התגית לא גולשת מעל / מתחת לשורה");
        check(used.containsKey(line) && used.get(line) > endX + 22, "התגית נרשמה בשורה (used) — תגיות נוספות יבואו אחריה");
        int yellowOn = yellow(on, row);
        check(yellowOn >= 40, "בשיא ההבהוב הנורה צהובה (" + yellowOn + " פיקסלים)");

        // שפל ההבהוב: הנורה כמעט כבויה
        BufferedImage off = canvas(c);
        LoopSkip.paint(off.createGraphics(), c, line, new HashMap<Integer, Integer>(), 0f);
        int yellowOff = yellow(off, row);
        check(yellowOff * 4 < yellowOn, "בשפל ההבהוב הנורה עמומה (" + yellowOff + " פיקסלים צהובים מול " + yellowOn + ")");
        check(inkIn(off, endX + 10, row.y, c.getWidth() - endX - 10, row.height) > 200, "גם בשפל התגית עצמה נשארת (רק הנורה מהבהבת)");

        // יש כבר תגית בשורה: אחריה
        Map<Integer, Integer> busy = new HashMap<Integer, Integer>();
        busy.put(line, endX + 150);
        BufferedImage after = canvas(c);
        LoopSkip.paint(after.createGraphics(), c, line, busy, 1f);
        check(inkIn(after, 0, row.y, endX + 150, row.height) == 0 && busy.get(line) > endX + 150 + 100, "אחרי תגית שכבר בשורה — לא עליה");

        // השורה מלאה בתגיות (התנאי "לא מתקיים") ואין מקום בה: התגית בשורה שמתחת, בתוך מה שרואים, עם קו קטן אל שורת הלולאה
        Map<Integer, Integer> full = new HashMap<Integer, Integer>();
        full.put(line, c.getVisibleRect().width - 4);
        Rectangle below = CondPaint.lineRect(c, line + 1);
        BufferedImage alt = canvas(c);
        LoopSkip.paint(alt.createGraphics(), c, line, full, 1f);
        int inkBelow = inkIn(alt, 0, below.y, c.getVisibleRect().width, below.height), inkHead = inkIn(alt, 0, row.y, c.getVisibleRect().width, row.height);
        check(inkBelow > 200 && inkHead < 80,
                "אין מקום בשורת הלולאה: התגית בשורה שמתחת (ובשורת הלולאה רק קו החיבור והנקודה) — מתחת " + inkBelow + ", בשורת הלולאה " + inkHead);
        check(maxInk(alt) < c.getVisibleRect().width - 5 && full.containsKey(line + 1) && full.get(line + 1) <= c.getVisibleRect().width, "התגית בתוך מה שרואים (לא נחתכת בצד)");

        // שורה ארוכה בחלון צר: התגית לא על הקוד (או שוברת שורה — לא נבדק כאן, כמו בתגית של לולאה אינסופית)
        JTextPane wide = pane(Arrays.asList("public class A {", "    static int run(int k) {",
                "        while (k > 5 && k % 2 == 0 || k > 100 && k < 1000 && k != 777 && k != 888 && k != 999 && k != 123456) {", "        }", "    }", "}"));
        wide.setSize(520, 200);
        JScrollPane sp = new JScrollPane(wide);
        sp.setSize(520, 200);
        sp.doLayout();
        Rectangle wr = CondPaint.lineRect(wide, 3);
        int codeEnd = CondPaint.codeEndX(wide, 3);
        BufferedImage w = canvas(wide);
        LoopSkip.paint(w.createGraphics(), wide, 3, new HashMap<Integer, Integer>(), 1f);
        check(wr != null && inkIn(w, 0, wr.y, Math.min(codeEnd, wide.getVisibleRect().width - 60), wr.height) == 0 || codeEnd > wide.getVisibleRect().width - 60,
                "שורה ארוכה בחלון צר: התגית לא נכתבת על הקוד");

        // שורת הלולאה לא קיימת (קובץ אחר / מחוץ לטווח): לא מצייר ולא נופל
        BufferedImage none = canvas(c);
        LoopSkip.paint(none.createGraphics(), c, 99, new HashMap<Integer, Integer>(), 1f);
        check(inkIn(none, 0, 0, none.getWidth(), none.getHeight()) == 0, "שורה שלא קיימת — לא מצויר כלום");

        if (args.length > 0) pictures(new File(args[0]));
        System.out.println(fails == 0 ? "MARK-END" : "נכשלו " + fails);
        System.exit(fails == 0 ? 0 : 1);
    }

    /** תמונות של חלון הקוד האמיתי (Viewer.CodePane) בשיא ההבהוב ובשפל שלו — לעיון (ולא נכשלות אם אי אפשר) */
    static void pictures(File dir) {
        try {
            dir.mkdirs();
            Class<?> cls = Class.forName("unit4.collectionsLib.Viewer$CodePane");
            java.lang.reflect.Constructor<?> k = cls.getDeclaredConstructor();
            k.setAccessible(true);
            JTextPane p = (JTextPane) k.newInstance();
            java.lang.reflect.Method show = cls.getDeclaredMethod("showCode", String.class);
            show.setAccessible(true);
            java.lang.reflect.Field skip = cls.getDeclaredField("skipLine");
            skip.setAccessible(true);
            p.setFont(Theme.mono != null ? Theme.mono.deriveFont(14f) : new Font(Font.MONOSPACED, Font.PLAIN, 14));
            p.setBackground(Color.WHITE);
            p.setMargin(new Insets(6, 10, 6, 10));
            StringBuilder b = new StringBuilder();
            for (String l : SAMPLE) b.append(b.length() > 0 ? "\n" : "").append(l);
            show.invoke(p, b.toString());
            p.setSize(760, 330);
            p.setEditable(false);
            skip.setInt(p, 5);
            for (float phase : new float[] { 1f, 0f }) {
                // ההבהוב נקבע לפי השעון — לתמונה מקבעים אותו: מציירים את התג ידנית מעל הקוד (אותו ציור כמו בחלון)
                BufferedImage img = new BufferedImage(p.getWidth(), p.getHeight(), BufferedImage.TYPE_INT_RGB);
                Graphics2D g = img.createGraphics();
                g.setColor(Color.WHITE);
                g.fillRect(0, 0, img.getWidth(), img.getHeight());
                p.paint(g);
                skip.setInt(p, -1);   // בלי הציור האוטומטי (עם ההבהוב לפי השעון), כדי לקבוע את השלב
                LoopSkip.paint(g, p, 5, new HashMap<Integer, Integer>(), phase);
                g.dispose();
                skip.setInt(p, 5);
                ImageIO.write(img, "png", new File(dir, phase > 0.5f ? "skip-bright.png" : "skip-dim.png"));
            }
            System.out.println("  ok  תמונות נשמרו ב-" + dir);
        } catch (Throwable t) {
            System.out.println("  --  בלי תמונות (" + t + ")");
        }
    }

    static final List<String> SAMPLE = Arrays.asList(
            "public class Demo {",
            "    static int run(int k) {",
            "        int sum = 0;",
            "        // k = 3, אז התנאי לא מתקיים",
            "        while (k > 5) {",
            "            sum += k;",
            "            k--;",
            "        }",
            "        for (int i = 0; i < sum; i++) {",
            "            System.out.println(i);",
            "        }",
            "        return sum;",
            "    }",
            "}");

    static JTextPane pane(List<String> lines) {
        JTextPane c = new JTextPane();
        c.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 14));
        c.setMargin(new Insets(6, 10, 6, 10));
        StringBuilder b = new StringBuilder();
        for (String l : lines) b.append(b.length() > 0 ? "\n" : "").append(l);
        c.setText(b.toString());
        c.setSize(760, 320);
        return c;
    }

    static BufferedImage canvas(JTextPane c) {
        BufferedImage img = new BufferedImage(c.getWidth(), c.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, img.getWidth(), img.getHeight());
        g.dispose();
        return img;
    }

    /** כמה פיקסלים לא לבנים יש במלבן */
    static int inkIn(BufferedImage img, int x0, int y0, int w, int h) {
        int n = 0;
        for (int y = Math.max(0, y0); y < Math.min(img.getHeight(), y0 + h); y++)
            for (int x = Math.max(0, x0); x < Math.min(img.getWidth(), x0 + w); x++)
                if ((img.getRGB(x, y) & 0xFFFFFF) != 0xFFFFFF) n++;
        return n;
    }

    /** כמה פיקסלים לא לבנים יש מחוץ לרצועת השורה (למעלה ולמטה) */
    static int inkOutside(BufferedImage img, int y, int h) {
        return inkIn(img, 0, 0, img.getWidth(), y) + inkIn(img, 0, y + h, img.getWidth(), img.getHeight() - y - h);
    }

    /** פיקסלים צהובים בוהקים (אדום וירוק גבוהים, כחול נמוך) בשורה */
    static int yellow(BufferedImage img, Rectangle row) {
        int n = 0;
        for (int y = row.y; y < row.y + row.height; y++)
            for (int x = 0; x < img.getWidth(); x++) {
                int p = img.getRGB(x, y);
                int r = (p >> 16) & 255, g = (p >> 8) & 255, b = p & 255;
                if (r > 235 && g > 180 && b < 150) n++;
            }
        return n;
    }

    /** הפיקסל הימני ביותר שצויר (-1 = כלום) */
    static int maxInk(BufferedImage img) {
        int max = -1;
        for (int y = 0; y < img.getHeight(); y++)
            for (int x = 0; x < img.getWidth(); x++)
                if ((img.getRGB(x, y) & 0xFFFFFF) != 0xFFFFFF) max = Math.max(max, x);
        return max;
    }

    static Point origin() {
        return new Point(0, 0);
    }
}
