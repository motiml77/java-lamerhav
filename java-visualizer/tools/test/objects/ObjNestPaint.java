package unit4.collectionsLib;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.imageio.ImageIO;

import unit4.collectionsLib.Model.ArrayView;
import unit4.collectionsLib.Model.MatrixView;
import unit4.collectionsLib.Model.Obj;
import unit4.collectionsLib.Model.ObjView;
import unit4.collectionsLib.Model.Step;

/**
 * בדיקה בלי חלון לציור של אובייקט בתוך אובייקט ומערך בתוך אובייקט (ObjCard + StructPanel): כרטיס של Team עם leader (Student),
 * scores (מערך), grid (מטריצה) ומערך ארוך — מצויר לתמונה, ובודקים שהגדלים הגיוניים: הכרטיס הפנימי בתוך הכרטיס החיצוני,
 * התאים הקטנים מצוירים (ותא שהשתנה בכתום), התג של המשתנה והתווית של האינדקס בסגול, מערך ארוך נקטע — והכול נכנס במה שנמדד.
 * וגם: כרטיס בלי אובייקטים / מערכים בתוכו נמדד בדיוק כמו קודם, וכרטיס של אובייקט שמצויר בתוך אחר לא מצויר שוב לבד (keepCards).
 * <pre>  java -Djava.awt.headless=true -cp .:dsviz.jar unit4.collectionsLib.ObjNestPaint [תיקייה לתמונות]</pre>
 */
public class ObjNestPaint {
    static int fails = 0;
    static File out;
    static final int W = 760, H = 460;
    static final Color CARD = new Color(0xF6F8FC), TAG = new Color(0x7C3AED);

    static void check(boolean ok, String what) {
        System.out.println((ok ? "  ok  " : "  FAIL ") + what);
        if (!ok) fails++;
    }

    static Obj obj(int id, String type, String... fields) {
        Obj o = new Obj();
        o.id = id;
        o.type = type;
        for (int i = 0; i + 1 < fields.length; i += 2) o.fields.add(new String[] { fields[i], fields[i + 1] });
        o.title = type;
        o.sub = "";
        return o;
    }

    static ArrayView array(int changed, String... items) {
        ArrayView a = new ArrayView();
        a.items.addAll(Arrays.asList(items));
        if (changed >= 0) a.changed.add(changed);
        return a;
    }

    static ObjView view(String title, Obj o) {
        ObjView v = new ObjView();
        v.title = title;
        v.obj = o;
        o.labels.add(title);
        return v;
    }

    static BufferedImage render(Step s, String name) throws Exception {
        StructPanel p = new StructPanel();
        p.offscreen = true;
        p.setSize(W, H);
        p.setStep(s);
        BufferedImage img = null;
        for (int k = 0; k < 2; k++) {   // פעמיים: בראשונה נקבעת ההגדלה
            img = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = img.createGraphics();
            p.paint(g);
            g.dispose();
        }
        if (out != null) ImageIO.write(img, "png", new File(out, name + ".png"));
        return img;
    }

    /** המלבן של כל הפיקסלים בצבע הזה (בתוך in, או בכל התמונה) — או null */
    static Rectangle bbox(BufferedImage img, Color c, Rectangle in) {
        int want = c.getRGB() & 0xFFFFFF, x0 = W, y0 = H, x1 = -1, y1 = -1;
        Rectangle r = in == null ? new Rectangle(0, 0, W, H) : in;
        for (int y = r.y; y < r.y + r.height; y++)
            for (int x = r.x; x < r.x + r.width; x++)
                if ((img.getRGB(x, y) & 0xFFFFFF) == want) {
                    x0 = Math.min(x0, x); y0 = Math.min(y0, y); x1 = Math.max(x1, x); y1 = Math.max(y1, y);
                }
        return x1 < 0 ? null : new Rectangle(x0, y0, x1 - x0 + 1, y1 - y0 + 1);
    }

    /** כמה פיקסלים בצבע הזה בערך (קו דק / טקסט קטן מוחלקים — לא בדיוק הצבע) */
    static int count(BufferedImage img, Color c, Rectangle in) {
        int n = 0;
        for (int y = in.y; y < in.y + in.height; y++)
            for (int x = in.x; x < in.x + in.width; x++) {
                Color p = new Color(img.getRGB(x, y));
                if (Math.abs(p.getRed() - c.getRed()) < 50 && Math.abs(p.getGreen() - c.getGreen()) < 50 && Math.abs(p.getBlue() - c.getBlue()) < 50) n++;
            }
        return n;
    }

    public static void main(String[] args) throws Exception {
        if (args.length > 0) {
            out = new File(args[0]);
            out.mkdirs();
        }
        Theme.install();   // הגופנים, כמו בחלון
        StructPanel probe = new StructPanel();

        // --- כרטיס רגיל (רק טקסט): נמדד כמו תמיד — שורה של 18 לכל מאפיין
        Obj plain = obj(1, "Dog", "name", "\"רקס\"", "age", "3");
        Dimension dp = ObjCard.table(probe, null, plain, 0, 0, null, 1);
        check(dp.height == 2 * ObjCard.ROW_H, "כרטיס בלי אובייקטים / מערכים: שתי שורות של " + ObjCard.ROW_H + " (" + dp.width + "×" + dp.height + ")");

        // --- Team: leader (Student, והמשתנה s מצביע עליו), scores (תא 1 השתנה), grid, ומערך ארוך שנקטע
        Obj leader = obj(2, "Student", "name", "\"נועה\"", "grade", "90");
        leader.labels.add("s");
        Obj team = obj(3, "Team", "name", "\"אלפא\"", "leader", "→ נועה", "scores", "[0, 7, 0]", "grid", "[[1, 2], [3, 4]]", "many", "[0, 0, …]");
        team.nested.put("leader", leader);
        team.arrays.put("scores", array(1, "0", "7", "0"));
        MatrixView grid = new MatrixView();
        grid.rows.add(new ArrayList<String>(Arrays.asList("1", "2")));
        grid.rows.add(new ArrayList<String>(Arrays.asList("3", "4")));
        team.arrays.put("grid", grid);
        ArrayView many = array(-1, "0", "0", "0", "0", "0", "0", "0", "0", "0", "0", "0", "0");
        many.truncated = true;
        team.arrays.put("many", many);

        Obj asText = obj(4, "Team", "name", "\"אלפא\"", "leader", "→ נועה", "scores", "[0, 7, 0]", "grid", "[[1, 2], [3, 4]]", "many", "[0, 0, …]");
        Dimension dt = ObjCard.table(probe, null, asText, 0, 0, null, 1), dn = ObjCard.table(probe, null, team, 0, 0, null, 1);
        Dimension dl = ObjCard.table(probe, null, leader, 0, 0, null, 1);
        check(dt.height == 5 * ObjCard.ROW_H, "אותו כרטיס כטקסט בלבד: 5 שורות (" + dt.width + "×" + dt.height + ")");
        check(dn.height > dt.height + dl.height && dn.height < 320, "עם הכרטיס הפנימי והמערכים הכרטיס גבוה יותר — ולא ענק (" + dn.width + "×" + dn.height + ")");
        check(dn.width > dl.width + 40 && dn.width < 520, "... ורחב מספיק לכרטיס הפנימי ולמערך של 12 תאים — ולא יותר מדי (" + dn.width + ")");

        // תווית אינדקס (i על התא 1) לא משנה את הגודל: המקום שלה שמור תמיד
        ObjCard.Hot hot = new ObjCard.Hot();
        hot.idx.put("scores", new java.util.TreeMap<Integer, List<String>>());
        hot.idx.get("scores").put(1, new ArrayList<String>(Arrays.asList("i")));
        Dimension dh = ObjCard.table(probe, null, team, 0, 0, null, 1, hot);
        check(dh.equals(dn), "תווית אינדקס על תא לא מזיזה את הכרטיס (אותו גודל: " + dh.width + "×" + dh.height + ")");

        Step s = new Step();
        s.views.add(view("t", team));
        BufferedImage img = render(s, "team");
        Rectangle outer = bbox(img, CARD, null);
        check(outer != null && outer.width >= dn.width && outer.height >= dn.height && outer.x + outer.width < W && outer.y + outer.height < H,
                "הכרטיס של t מצויר כולו בתוך התמונה, ומכיל את הטבלה שנמדדה: " + outer);
        // הכרטיס הפנימי: לבן, בתוך הכרטיס החיצוני (האפור-כחלחל)
        Rectangle white = outer == null ? null : bbox(img, Color.WHITE, new Rectangle(outer.x + 6, outer.y + 6, outer.width - 12, outer.height - 12));
        check(white != null && white.width >= dl.width && white.height >= dl.height + 10 && outer.contains(white)
                && white.x > outer.x + 40 && white.height < outer.height - 60,
                "הכרטיס הפנימי (Student) מצויר בתוך הכרטיס של t, בעמודת הערכים: " + white + " בתוך " + outer);
        check(outer != null && count(img, TAG, outer) > 15, "התג של המשתנה (s) על הכרטיס הפנימי — בסגול");
        Rectangle cells = outer == null ? null : bbox(img, StructPanel.BOX, outer);
        check(cells != null && white != null && cells.width > 12 * 20 && cells.y > white.y + white.height - 4 && outer.contains(cells),
                "התאים הקטנים של המערכים מצוירים מתחת לכרטיס הפנימי, בתוך הכרטיס: " + cells);
        Rectangle orange = outer == null ? null : bbox(img, StructPanel.CHANGED_C, outer);
        check(orange != null && orange.width >= 14 && orange.width <= 60 && orange.height >= 12 && orange.height <= 20,
                "התא שהשתנה (scores[1]) בכתום, בגודל של תא אחד: " + orange);
        check(orange != null && cells != null && orange.x > cells.x + 15, "... והוא התא השני (אחרי התא 0)");

        // --- כרטיס שמצויר בתוך כרטיס אחר לא מצויר שוב לבד; שניים שמכילים זה את זה — נשאר אחד
        Obj sAlone = obj(2, "Student", "name", "\"נועה\"", "grade", "90");
        List<ObjView> cards = new ArrayList<ObjView>(Arrays.asList(view("s", sAlone), view("t", team)));
        List<ObjView> kept = Recorder.keepCards(cards);
        check(kept.size() == 1 && kept.get(0).obj == team, "s מצויר בתוך t.leader — הכרטיס של s לא מצויר שוב לבד (נשאר רק t)");
        Obj a = obj(10, "A", "b", "→ B"), b = obj(11, "B", "a", "→ A");
        a.nested.put("b", obj(11, "B", "a", "→ A"));
        b.nested.put("a", obj(10, "A", "b", "→ B"));
        kept = Recorder.keepCards(new ArrayList<ObjView>(Arrays.asList(view("a", a), view("b", b))));
        check(kept.size() == 1, "שני אובייקטים שמכילים זה את זה: כרטיס אחד (והשני בתוכו)");
        Obj self = obj(2, "Student", "name", "\"נועה\"", "grade", "90");
        kept = Recorder.keepCards(new ArrayList<ObjView>(Arrays.asList(view("this", self), view("t", team))));
        check(kept.size() == 2, "this נשאר כרטיס משלו גם כשהוא מצויר בתוך כרטיס אחר");

        // --- שלוש רמות זו בתוך זו: עדיין נכנס, וכל רמה בתוך הקודמת
        Obj l4 = obj(24, "L4", "x", "4", "in", "→ {y=5}"), l3 = obj(23, "L3", "in", "→ L4"), l2 = obj(22, "L2", "in", "→ L3"), l1 = obj(21, "L1", "in", "→ L2");
        l3.nested.put("in", l4);
        l2.nested.put("in", l3);
        l1.nested.put("in", l2);
        Dimension d1 = ObjCard.table(probe, null, l1, 0, 0, null, 1), d2 = ObjCard.table(probe, null, l2, 0, 0, null, 1),
                d3 = ObjCard.table(probe, null, l3, 0, 0, null, 1), d4 = ObjCard.table(probe, null, l4, 0, 0, null, 1);
        check(d1.width > d2.width && d2.width > d3.width && d3.width > d4.width && d1.height > d2.height && d2.height > d3.height && d3.height > d4.height,
                "שלוש רמות: כל כרטיס גדול מזה שבתוכו (" + d4.width + "×" + d4.height + " → " + d1.width + "×" + d1.height + ")");
        check(d1.width < 420 && d1.height < 200, "... ועדיין קטן מספיק לחלון");
        Step deep = new Step();
        deep.views.add(view("deep", l1));
        BufferedImage deepImg = render(deep, "deep");
        Rectangle o1 = bbox(deepImg, CARD, null);
        check(o1 != null && o1.x + o1.width < W && o1.y + o1.height < H, "הכרטיס של שלוש הרמות מצויר כולו: " + o1);
        check(o1 != null && count(deepImg, TAG, o1) == 0, "... ובלי משתנה שמצביע על הפנימיים — בלי תג סגול");

        // --- שם המאפיין בקטן לפני הכותרת של כרטיס בתא (דיווח של משתמש: "naor" בלי לדעת שזה name): מערך של Person1
        Obj p0 = obj(10, "Person1", "name", "\"naor\"", "id", "\"123\"");
        p0.title = "naor";
        p0.sub = "id=123";
        Obj p1 = obj(11, "Person1", "fullName", "\"Alexandra Ben-David\"", "id", "\"12\"");
        p1.title = "Alexandra Ben-D…";   // כותרת שנחתכה (16 תווים) — לפי ההתחלה
        p1.sub = "id=12";
        Obj p2 = obj(12, "Counter", "count", "3");   // בלי מאפיין טקסט — הכותרת היא שם המחלקה, בלי שם מאפיין
        Obj p3 = obj(13, "Card", "suit", "\"lev\"", "label", "\"lev\"");   // שני מאפיינים עם אותו ערך — הראשון (אין name)
        p3.title = "lev";
        check("name".equals(StructPanel.titleField(p0)), "שם המאפיין של הכותרת: name (\"naor\")");
        check("fullName".equals(StructPanel.titleField(p1)), "כותרת שנחתכה (Alexandra Ben-D…) — fullName");
        check(StructPanel.titleField(p2) == null, "כותרת שהיא שם המחלקה — בלי שם מאפיין");
        check("suit".equals(StructPanel.titleField(p3)), "שני מאפיינים עם אותו ערך — הראשון (suit)");
        Step arr = new Step();
        ArrayView people = new ArrayView();
        people.title = "arr";
        for (Obj o : new Obj[] { p0, p1 }) {
            people.items.add(o.title);
            people.objs.add(o);
        }
        arr.views.add(people);
        BufferedImage pi = render(arr, "obj-title-field");
        Color muted = new Color(0x8A93A3);
        int grey = count(pi, muted, new Rectangle(0, 0, W, H));
        check(grey > 60, "בתאים: שם המאפיין (name / fullName) מצויר באפור קטן לפני הערך (" + grey + " פיקסלים אפורים)");
        System.out.println(fails == 0 ? "MARK-END" : "נכשלו " + fails);
    }
}
