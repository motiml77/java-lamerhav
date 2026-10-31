package unit4.collectionsLib;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import javax.imageio.ImageIO;
import javax.swing.JTextPane;

/**
 * בדיקה בלי חלון לציור של תנאי מורכב (CondPaint): קו צבעוני מתחת לכל חלק בקוד — ירוק מתקיים, אדום לא מתקיים,
 * אפור (מקווקו) לא נבדק — גם כשהתנאי ממשיך לשורה הבאה; השבב מימין לקוד (לא על הטקסט); וההסבר בשורה שמתחת.
 * ארגומנט (לא חובה): קובץ PNG לשמירת הציור, כדי להסתכל עליו.
 */
public class CondPartsPaint {
    static int fails = 0;

    static void check(boolean ok, String what) {
        System.out.println((ok ? "  ok  " : "  FAIL ") + what);
        if (!ok) fails++;
    }

    static final List<String> CODE = Arrays.asList(
            "public class A {",
            "    static void f(int x, int y) {",
            "        if (x > 5 || y > 4) {",
            "            x++;",
            "        }",
            "        while (x > 0",
            "                && y != 0) {",
            "            x--;",
            "        }",
            "    }",
            "}");

    public static void main(String[] args) throws Exception {
        Theme.install();   // הגופנים (כמו בחלון)
        JTextPane c = new JTextPane();
        c.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 14));
        c.setMargin(new Insets(6, 10, 6, 10));
        StringBuilder b = new StringBuilder();
        for (String l : CODE) b.append(b.length() > 0 ? "\n" : "").append(l);
        c.setText(b.toString());
        c.setSize(900, 300);
        CondViz.Block ifB = null, whileB = null;
        for (CondViz.Block k : CondViz.parse(b.toString())) {
            if (k.kind.equals("if")) ifB = k;
            if (k.kind.equals("while")) whileB = k;
        }
        check(ifB != null && ifB.condLine == 3 && ifB.condCol == 12, "if: התנאי מתחיל בשורה 3, עמודה 12");
        check(whileB != null && whileB.condLine == 6 && whileB.condLastLine() == 7, "while: התנאי בשורות 6-7");
        if (ifB == null || whileB == null) System.exit(1);

        // if (x > 5 || y > 4): החלק הראשון מתקיים — את השני לא בודקים
        CondViz.Check k = check(ifB, true, "נכנסים ל-if", new Boolean[] { true, null });
        check(k.why.contains("לא בודקים"), "ההסבר: " + k.why.replace("‎", ""));
        BufferedImage img = paint(c, k);
        Rectangle r3 = CondPaint.lineRect(c, 3), r4 = CondPaint.lineRect(c, 4);
        int[] p1 = span(c, 3, 12, 17), p2 = span(c, 3, 21, 26);   // x > 5 / y > 4
        check(near(img, p1[0], r3.y + r3.height / 2, p1[1] - p1[0], r3.height / 2 + 2, CondPaint.TRUE), "קו ירוק מתחת ל-x > 5");
        check(!near(img, p1[0], r3.y + r3.height / 2, p1[1] - p1[0], r3.height / 2 + 2, CondPaint.FALSE), "בלי אדום מתחת ל-x > 5");
        check(near(img, p2[0], r3.y + r3.height / 2, p2[1] - p2[0], r3.height / 2 + 2, CondPaint.SKIP), "קו אפור (לא נבדק) מתחת ל-y > 4");
        check(!near(img, p2[0], r3.y + r3.height / 2, p2[1] - p2[0], r3.height / 2 + 2, CondPaint.TRUE), "בלי ירוק מתחת ל-y > 4");
        int end3 = CondPaint.lineEndX(c, 3), end4 = CondPaint.lineEndX(c, 4);
        check(near(img, end3 + 4, r3.y, img.getWidth() - end3 - 4, r3.height, CondPaint.TRUE), "השבב (✓ מתקיים) מימין לקוד בשורה 3");
        check(near(img, end4 + 4, r4.y, img.getWidth() - end4 - 4, r4.height, CondPaint.TRUE), "ההסבר בשורה שמתחת (4), מימין לקוד");
        check(!ink(img, CallPaint.textStart(c, 4), r4.y + 2, end4 - CallPaint.textStart(c, 4), r4.height - 4, 0), "ההסבר לא מכסה את הקוד של שורה 4");
        save(img, args, "-if");

        // while (x > 0 \n && y != 0): הראשון מתקיים, השני לא — שני הקווים, כל אחד בשורה שלו
        k = check(whileB, false, "יוצאים מהלולאה", new Boolean[] { true, false });
        img = paint(c, k);
        Rectangle r6 = CondPaint.lineRect(c, 6), r7 = CondPaint.lineRect(c, 7);
        int[] q1 = span(c, 6, 15, 20), q2 = span(c, 7, 19, 25);   // x > 0 / y != 0
        check(near(img, q1[0], r6.y + r6.height / 2, q1[1] - q1[0], r6.height / 2 + 2, CondPaint.TRUE), "while: קו ירוק מתחת ל-x > 0 (שורה 6)");
        check(near(img, q2[0], r7.y + r7.height / 2, q2[1] - q2[0], r7.height / 2 + 2, CondPaint.FALSE), "while: קו אדום מתחת ל-y != 0 (שורה 7)");
        int[] after = span(c, 7, 25, 28);   // ") {" — הקוד שאחרי החלק לא מוסתר
        check(!ink(img, after[0], r7.y, after[1] - after[0], r7.height * 2 / 3, 0), "while: שום סימן לא מכסה את ה-) שאחרי y != 0");
        check(k.why.contains("צריך ששניהם יתקיימו"),"while: ההסבר: " + k.why.replace("‎", ""));
        Rectangle r8 = CondPaint.lineRect(c, 8);
        int end8 = CondPaint.lineEndX(c, 8);
        check(near(img, end8 + 4, r8.y, img.getWidth() - end8 - 4, r8.height, CondPaint.FALSE), "while: ההסבר מתחת לשורה האחרונה של התנאי (8)");
        save(img, args, "-while");

        System.out.println(fails == 0 ? "MARK-END" : "נכשלו " + fails);
        System.exit(fails == 0 ? 0 : 1);
    }

    /** בדיקה של תנאי מורכב בלי הרצה: הערך של כל חלק (null = לא נבדק) */
    static CondViz.Check check(CondViz.Block b, boolean result, String outcome, Boolean[] values) {
        CondViz.Check k = new CondViz.Check();
        k.block = b;
        k.result = result;
        k.outcome = outcome;
        k.tree = CondViz.tree(b.cond);
        List<CondViz.Part> parts = new ArrayList<CondViz.Part>();
        CondViz.leaves(k.tree, parts);
        for (int i = 0; i < parts.size(); i++) {
            CondViz.Part p = parts.get(i);
            p.value = values[i];
            p.state = values[i] == null ? CondViz.SKIPPED : CondViz.CHECKED;
        }
        k.parts.addAll(parts);
        k.vars.add(new String[] { "x", "7" });
        k.vars.add(new String[] { "y", "3" });
        k.why = CondViz.why(k);
        return k;
    }

    static BufferedImage paint(JTextPane c, CondViz.Check k) {
        BufferedImage img = new BufferedImage(c.getWidth(), c.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, img.getWidth(), img.getHeight());
        c.paint(g);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        CondViz.Marks m = new CondViz.Marks();
        m.check = k;
        CondPaint.paint(g, c, m, new HashMap<Integer, Integer>());
        g.dispose();
        return img;
    }

    /** ה-x של התווים from..to (עמודות מ-0) בשורה line */
    @SuppressWarnings("deprecation")
    static int[] span(JTextPane c, int line, int from, int to) throws Exception {
        int start = c.getDocument().getDefaultRootElement().getElement(line - 1).getStartOffset();
        return new int[] { c.modelToView(start + from).x, c.modelToView(start + to).x };
    }

    static void save(BufferedImage img, String[] args, String suffix) throws Exception {
        if (args.length > 0) ImageIO.write(img, "png", new File(args[0].replace(".png", suffix + ".png")));
    }

    /** יש במלבן פיקסל בצבע קרוב ל-col */
    static boolean near(BufferedImage img, int x0, int y0, int w, int h, Color col) {
        for (int y = Math.max(0, y0); y < Math.min(img.getHeight(), y0 + h); y++)
            for (int x = Math.max(0, x0); x < Math.min(img.getWidth(), x0 + w); x++) {
                int rgb = img.getRGB(x, y);
                int dr = (rgb >> 16 & 255) - col.getRed(), dg = (rgb >> 8 & 255) - col.getGreen(), db = (rgb & 255) - col.getBlue();
                if (dr * dr + dg * dg + db * db < 30 * 30) return true;
            }
        return false;
    }

    /** יש במלבן פיקסל צבעוני (לא אפור / לבן / שחור) — tol = כמה סטייה מאפור מותרת */
    static boolean ink(BufferedImage img, int x0, int y0, int w, int h, int tol) {
        for (int y = Math.max(0, y0); y < Math.min(img.getHeight(), y0 + h); y++)
            for (int x = Math.max(0, x0); x < Math.min(img.getWidth(), x0 + w); x++) {
                int rgb = img.getRGB(x, y), r = rgb >> 16 & 255, g = rgb >> 8 & 255, bl = rgb & 255;
                if (Math.max(r, Math.max(g, bl)) - Math.min(r, Math.min(g, bl)) > tol + 20) return true;
            }
        return false;
    }
}
