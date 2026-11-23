package unit4.collectionsLib;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import javax.imageio.ImageIO;

import unit4.collectionsLib.RecursionPanel.Call;

/**
 * בדיקה בלי חלון לאזהרה "הרקורסיה לא נעצרת" בפס של הרקורסיה (RecursionPanel.Banner + RecursionKind.stuckDepth):
 * רקורסיה שירדה 25 רמות ואף קריאה לא חזרה — תגית צהובה עם נורה (בשיא ההבהוב דולקת, בשפל עמומה) והסבר מתחתיה, והפס גבוה יותר;
 * אותו עומק כשקריאה חזרה (תנאי העצירה הושג) — בלי אזהרה; ועומק קטן מהסף — בלי אזהרה. עם תיקייה כארגומנט — שומר תמונות.
 */
public class RecursionWarnPaint {
    static int fails = 0;

    static void check(boolean ok, String what) {
        System.out.println((ok ? "  ok  " : "  FAIL ") + what);
        if (!ok) fails++;
    }

    static int ids = 0;

    /** שרשרת של depth קריאות sum(n), כל אחת בתוך הקודמת; returned — כמה מהפנימיות חזרו (RETURN) */
    static List<Call> chain(int depth, int returned) {
        List<Call> all = new ArrayList<Call>();
        Call parent = null;
        for (int i = 0; i < depth; i++) {
            Call c = new Call();
            c.id = ++ids;
            c.method = "sum";
            c.cls = "Main";
            c.depth = i;
            c.parent = parent;
            if (parent != null) parent.children.add(c);
            List<String[]> v = new ArrayList<String[]>();
            v.add(new String[] { "n", String.valueOf(depth - i) });
            c.params = v;
            c.vars = v;
            c.shown = true;
            all.add(c);
            parent = c;
        }
        for (int i = 0; i < returned; i++) {
            Call c = all.get(depth - 1 - i);
            c.done = true;
            c.viaReturn = true;
            c.returned = String.valueOf(i);
        }
        return all;
    }

    /** פס עם הקריאות האלה: (הפס, גובהו, תמונה) */
    static BufferedImage banner(List<Call> all, RecursionKind[] out) throws Exception {
        RecursionPanel p = new RecursionPanel();
        RecursionKind k = RecursionKind.classify(all);
        out[0] = k;
        set(p, "kind", k);
        set(p, "all", all);
        set(p, "active", all.get(all.size() - 1));
        Dimension d = new Dimension(720, 0);
        p.banner.setSize(d.width, 400);
        d.height = p.banner.getPreferredSize().height;
        p.banner.setSize(d);
        BufferedImage img = new BufferedImage(d.width, d.height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, d.width, d.height);
        p.banner.paint(g);
        g.dispose();
        return img;
    }

    static void set(Object o, String name, Object v) throws Exception {
        Field f = RecursionPanel.class.getDeclaredField(name);
        f.setAccessible(true);
        f.set(o, v);
    }

    /** פיקסלים צהובים בוהקים (אדום וירוק גבוהים, כחול נמוך) */
    static int yellow(BufferedImage img) {
        int n = 0;
        for (int y = 0; y < img.getHeight(); y++)
            for (int x = 0; x < img.getWidth(); x++) {
                int p = img.getRGB(x, y);
                int r = (p >> 16) & 255, g = (p >> 8) & 255, b = p & 255;
                if (r > 235 && g > 180 && b < 150) n++;
            }
        return n;
    }

    public static void main(String[] args) throws Exception {
        Theme.install();
        RecursionKind[] k = new RecursionKind[1];

        // 25 רמות, אף קריאה לא חזרה: חשד
        List<Call> stuck = chain(25, 0);
        System.setProperty("dsviz.blinkFreeze", "1");
        BufferedImage on = banner(stuck, k);
        check(k[0].stuckDepth() == 25, "25 קריאות מקוננות בלי חזרה: חשד בעומק " + k[0].stuckDepth());
        int yOn = yellow(on);
        check(yOn >= 150, "בשיא ההבהוב: תגית צהובה עם נורה (" + yOn + " פיקסלים צהובים)");
        System.setProperty("dsviz.blinkFreeze", "0");
        BufferedImage off = banner(stuck, k);
        int yOff = yellow(off);
        check(yOff * 4 < yOn, "בשפל ההבהוב הנורה והרקע עמומים (" + yOff + " מול " + yOn + ")");
        check(on.getHeight() == off.getHeight(), "הגובה לא משתנה עם ההבהוב");

        // עומק 100 ומעלה: "כנראה"
        List<Call> likely = chain(RecursionKind.LIKELY_DEPTH + 5, 0);
        RecursionKind[] k2 = new RecursionKind[1];
        banner(likely, k2);
        check(k2[0].stuckDepth() == RecursionKind.LIKELY_DEPTH + 5, "עומק " + (RecursionKind.LIKELY_DEPTH + 5) + ": חשד חזק יותר");

        // אותה רקורסיה, אבל אחת הקריאות חזרה: תנאי העצירה הושג — בלי אזהרה
        System.setProperty("dsviz.blinkFreeze", "1");
        List<Call> ok = chain(25, 1);
        RecursionKind[] k3 = new RecursionKind[1];
        BufferedImage fine = banner(ok, k3);
        check(k3[0].stuckDepth() == 0 && yellow(fine) == 0, "קריאה אחת חזרה: אין אזהרה (גם לא צהוב בפס)");
        check(fine.getHeight() < on.getHeight(), "בלי אזהרה הפס נמוך יותר (" + fine.getHeight() + " מול " + on.getHeight() + ")");

        // עומק קטן מהסף (5): בלי אזהרה
        List<Call> small = chain(5, 0);
        RecursionKind[] k4 = new RecursionKind[1];
        banner(small, k4);
        check(k4[0].stuckDepth() == 0, "5 קריאות בלי חזרה (עדיין בירידה, קטן מהסף): אין אזהרה");

        // תנאי עצירה: קריאה שחזרה בלי להמשיך — כן; קריאה שהתוכנית התרסקה בה — לא
        check(k3[0].base(ok.get(24)) && !k[0].base(stuck.get(24)), "תג \"תנאי עצירה\" רק על קריאה שחזרה — לא על קריאה שהתוכנית התרסקה בה");

        if (args.length > 0) {
            File dir = new File(args[0]);
            dir.mkdirs();
            ImageIO.write(on, "png", new File(dir, "banner-warn-bright.png"));
            ImageIO.write(off, "png", new File(dir, "banner-warn-dim.png"));
            ImageIO.write(fine, "png", new File(dir, "banner-ok.png"));
        }
        System.out.println(fails == 0 ? "MARK-END" : "נכשלו " + fails);
        System.exit(fails == 0 ? 0 : 1);
    }
}
