package unit4.collectionsLib;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import javax.imageio.ImageIO;

import unit4.collectionsLib.Model.ArrayView;
import unit4.collectionsLib.Model.ClassInfo;
import unit4.collectionsLib.Model.Frame;
import unit4.collectionsLib.Model.Meth;
import unit4.collectionsLib.Model.Obj;
import unit4.collectionsLib.Model.ObjView;
import unit4.collectionsLib.Model.Step;

/**
 * בדיקה בלי חלון לציור של הורשה ופולימורפיזם (Poly + PolyPaint + StructPanel): העצם בקופסאות — הקופסה הפנימית (Dog) בתוך
 * החיצונית (Animal); אותו צבע למחלקה בקופסה, בתגית של ההפניה ובתגית בתא של מערך; פסים רק כשטיפוס ההפניה שהשורה משתמשת בה שונה
 * מהטיפוס של העצם; בתא של מערך — תגית קטנה ולא קופסאות; ותרשים המחלקות — הסידור והיהלומים (מלא / ריק).
 * <pre>  java -Djava.awt.headless=true -cp .:dsviz.jar unit4.collectionsLib.PolyPaintCheck [תיקייה לתמונות]</pre>
 */
public class PolyPaintCheck {
    static int fails = 0;
    static File out;
    static final int W = 900, H = 640;
    static final Color RED = new Color(0xC0392B), PURPLE = new Color(0x7B3FB0), TEAL = new Color(0x0C8583), HATCH = new Color(214, 219, 232);

    static void check(boolean ok, String what) {
        System.out.println((ok ? "  ok  " : "  FAIL ") + what);
        if (!ok) fails++;
    }

    static ClassInfo cls(String name, String sup, String[][] fields, String... methods) {
        ClassInfo c = new ClassInfo();
        c.name = name;
        c.sup = sup;
        for (String[] f : fields) c.fields.add(f);
        Meth k = new Meth();
        k.name = name;
        k.ctor = true;
        k.count = 2;
        c.methods.add(k);
        for (String m : methods) {
            Meth x = new Meth();
            x.name = m;
            x.virt = true;
            x.override = sup != null && m.equals("say");
            x.ret = "String";
            c.methods.add(x);
        }
        return c;
    }

    static Obj obj(int id, String type, String title, String... fields) {
        Obj o = new Obj();
        o.id = id;
        o.type = type;
        o.title = title;
        o.sub = "";
        for (int i = 0; i + 1 < fields.length; i += 2) o.fields.add(new String[] { fields[i], fields[i + 1] });
        return o;
    }

    /** צעד ב-main בשורה line של Main.java, עם הטיפוסים המוצהרים */
    static Step step(int line, String... types) {
        Step s = new Step();
        s.calls.add(new Frame("Main", "main", "Main.java", line));
        s.where = s.calls.get(0);
        s.kind = Model.Kind.LINE;
        s.vars = new ArrayList<String[]>();
        s.types = new HashMap<String, String>();
        for (int i = 0; i + 1 < types.length; i += 2) {
            s.types.put(types[i], types[i + 1]);
            s.vars.add(new String[] { types[i], types[i + 1] + " x" });
        }
        return s;
    }

    static ObjView view(Obj o, String... labels) {
        ObjView v = new ObjView();
        v.obj = o;
        for (String l : labels) o.labels.add(l);
        v.title = String.join(", ", labels);
        return v;
    }

    static BufferedImage render(Step s, String name) throws Exception {
        StructPanel p = new StructPanel();
        p.offscreen = true;
        p.setSize(W, H);
        p.setStep(s);
        BufferedImage img = null;
        for (int k = 0; k < 2; k++) {
            img = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = img.createGraphics();
            p.paint(g);
            g.dispose();
        }
        if (out != null) ImageIO.write(img, "png", new File(out, name + ".png"));
        return img;
    }

    /** המלבן של הפיקסלים שקרובים לצבע (קווי מסגרת מוחלקים — לא בדיוק הצבע) */
    static Rectangle bbox(BufferedImage img, Color c, Rectangle in, int tol) {
        int x0 = W, y0 = H, x1 = -1, y1 = -1;
        Rectangle r = in == null ? new Rectangle(0, 0, img.getWidth(), img.getHeight()) : in;
        for (int y = r.y; y < r.y + r.height && y < img.getHeight(); y++)
            for (int x = r.x; x < r.x + r.width && x < img.getWidth(); x++) {
                Color p = new Color(img.getRGB(x, y));
                if (Math.abs(p.getRed() - c.getRed()) <= tol && Math.abs(p.getGreen() - c.getGreen()) <= tol && Math.abs(p.getBlue() - c.getBlue()) <= tol) {
                    x0 = Math.min(x0, x); y0 = Math.min(y0, y); x1 = Math.max(x1, x); y1 = Math.max(y1, y);
                }
            }
        return x1 < 0 ? null : new Rectangle(x0, y0, x1 - x0 + 1, y1 - y0 + 1);
    }

    static int count(BufferedImage img, Color c, Rectangle in, int tol) {
        int n = 0;
        for (int y = in.y; y < in.y + in.height && y < img.getHeight(); y++)
            for (int x = in.x; x < in.x + in.width && x < img.getWidth(); x++) {
                Color p = new Color(img.getRGB(x, y));
                if (Math.abs(p.getRed() - c.getRed()) <= tol && Math.abs(p.getGreen() - c.getGreen()) <= tol && Math.abs(p.getBlue() - c.getBlue()) <= tol) n++;
            }
        return n;
    }

    public static void main(String[] args) throws Exception {
        if (args.length > 0) {
            out = new File(args[0]);
            out.mkdirs();
        }
        Theme.install();
        Poly.reset();
        String src = "public class Main {\n    public static void main(String[] args) {\n        Dog d = new Dog(\"Rexy\", 5);\n        Animal pet = d;\n"
                + "        System.out.println(pet.say());\n        System.out.println(d.say());\n    }\n}\n"
                + "class Animal { private String name; private int age; public String say() { return \"\"; } }\n"
                + "class Dog extends Animal { private String breed; private Person owner; public String say() { return \"Woof\"; } }\n"
                + "class Cat extends Animal { private Collar collar; Cat() { this.collar = new Collar(\"red\"); } public String say() { return \"Meow\"; } }\n"
                + "class Person { private String name; }\nclass Collar { private String color; }\n";
        SourceFiles.preload("Main.java", src);
        Poly.addSource(src);
        Poly.define(cls("Animal", null, new String[][] { { "name", "String", "-" }, { "age", "int", "-" } }, "getName", "say"));
        Poly.define(cls("Dog", "Animal", new String[][] { { "breed", "String", "-" }, { "owner", "Person", "-" } }, "say", "fetch"));
        Poly.define(cls("Cat", "Animal", new String[][] { { "collar", "Collar", "-" } }, "say"));
        Poly.define(cls("Person", null, new String[][] { { "name", "String", "-" } }));
        Poly.define(cls("Collar", null, new String[][] { { "color", "String", "-" } }));

        check(Poly.hex("Animal").equals("#c0392b") && Poly.hex("Dog").equals("#7b3fb0") && Poly.hex("Cat").equals("#0c8583"),
                "הצבעים לפי המקום בעץ: Animal אדום, Dog סגול, Cat טורקיז");
        check(!Poly.inHierarchy("Person") && !Poly.inHierarchy("Collar"), "Person ו-Collar לא בעץ הורשה — בלי צבע (כרטיס רגיל)");

        // --- העצם של d בשורה "System.out.println(pet.say());" (pet : Animal) — פסים על Dog
        Obj rexy = obj(7, "Dog", "Rexy", "name", "\"Rexy\"", "age", "5", "breed", "\"Boxer\"", "owner", "→ Hila");
        Step s = step(5, "d", "Dog", "pet", "Animal");
        s.views.add(view(rexy, "d", "pet"));
        Poly.Card card = Poly.card(s, rexy, false);
        check(card != null && card.boxes.size() == 2 && card.boxes.get(0).cls.name.equals("Animal") && card.boxes.get(1).cls.name.equals("Dog"),
                "שתי קופסאות: Animal בחוץ, Dog בפנים");
        check(card != null && card.boxes.get(0).part.fields.size() == 2 && card.boxes.get(1).part.fields.size() == 2,
                "המאפיינים מחולקים: name, age ב-Animal; breed, owner ב-Dog");
        check(card != null && "pet".equals(card.ref) && card.boxes.get(1).hidden && card.hideNote.equals("לא נראה דרך pet"),
                "השורה משתמשת ב-pet (Animal): הקופסה של Dog מכוסה — \"לא נראה דרך pet\"");
        check(card != null && card.boxes.get(0).struck.size() == 1 && card.boxes.get(0).struck.iterator().next().name.equals("say"),
                "say() של Animal בקו חוצה (Dog דורסת אותה)");
        BufferedImage img = render(s, "boxes-hatched");

        // --- אותו עצם, השורה משתמשת ב-d (Dog = הטיפוס של העצם) — בלי פסים
        Obj rexy2 = obj(7, "Dog", "Rexy", "name", "\"Rexy\"", "age", "5", "breed", "\"Boxer\"", "owner", "→ Hila");
        Step s2 = step(6, "d", "Dog", "pet", "Animal");
        s2.views.add(view(rexy2, "d", "pet"));
        Poly.Card card2 = Poly.card(s2, rexy2, false);
        check(card2 != null && "d".equals(card2.ref) && card2.hideNote == null && !card2.boxes.get(1).hidden,
                "השורה משתמשת ב-d (Dog — כמו העצם): בלי פסים");
        BufferedImage img2 = render(s2, "boxes-plain");
        // הקופסאות — לפי צבע הרקע של כל מחלקה (בציור בלי פסים): Dog בתוך Animal
        Rectangle outer = bbox(img2, Poly.bg("Animal"), null, 1), inner = bbox(img2, Poly.bg("Dog"), null, 1);
        check(outer != null && inner != null && outer.contains(inner) && inner.x > outer.x + 4 && inner.y > outer.y + 60
                && inner.x + inner.width < outer.x + outer.width && inner.y + inner.height < outer.y + outer.height,
                "הקופסה של Dog בתוך הקופסה של Animal: " + inner + " בתוך " + outer);
        int hatched = inner == null ? 0 : count(img, HATCH, inner, 14), plain = inner == null ? -1 : count(img2, HATCH, inner, 14);
        check(hatched > 300 && plain >= 0 && hatched > 5 * Math.max(plain, 1),
                "פסים רק כשהטיפוסים שונים: בקופסה של Dog " + hatched + " פיקסלים של פסים דרך pet, ו-" + plain + " דרך d");
        // התגיות: pet בצבע של Animal מעל הקופסה החיצונית, d בצבע של Dog — בתוך Animal, מעל Dog
        Rectangle tagRow = outer == null ? null : new Rectangle(outer.x, Math.max(0, outer.y - 58), outer.width, 30);
        check(tagRow != null && count(img, RED, tagRow, 30) > 20 && count(img, PURPLE, tagRow, 30) == 0,
                "תגית ההפניה pet : Animal (אדום) מעל הקופסה של Animal");
        Rectangle innerTags = inner == null ? null : new Rectangle(inner.x, Math.max(0, inner.y - 56), inner.width, 28);
        check(innerTags != null && count(img2, PURPLE, innerTags, 30) > 20, "תגית ההפניה d : Dog (סגול) בתוך Animal, מעל הקופסה של Dog");
        check(outer != null && count(img2, RED, new Rectangle(outer.x, outer.y - 26, outer.width, 26), 30) > 20
                && inner != null && count(img2, PURPLE, new Rectangle(inner.x, inner.y - 26, inner.width, 26), 30) > 20,
                "אותו צבע בכותרת של כל קופסה ובתגית שלה (אדום ל-Animal, סגול ל-Dog)");

        // --- מחלקה שלא בעץ (Person) — כרטיס רגיל, בלי קופסאות
        Obj hila = obj(9, "Person", "Hila", "name", "\"Hila\"");
        check(Poly.card(s, hila, false) == null, "Person (לא בעץ): Poly.card == null — הכרטיס הרגיל");

        // --- מערך Animal[] עם Dog ו-Cat: בתא — תגית צבעונית קטנה, לא קופסאות; בכותרת zoo : Animal[]
        Step s3 = step(5, "zoo", "Animal[]");
        ArrayView zoo = new ArrayView();
        zoo.title = "zoo";
        zoo.items.add("Rexy");
        zoo.items.add("Mitzi");
        zoo.objs.add(obj(11, "Dog", "Rexy", "name", "\"Rexy\""));
        zoo.objs.add(obj(12, "Cat", "Mitzi", "name", "\"Mitzi\""));
        s3.views.add(zoo);
        BufferedImage img3 = render(s3, "array-chips");
        Rectangle dogChip = bbox(img3, PURPLE, null, 30), catChip = bbox(img3, TEAL, null, 30), red3 = bbox(img3, RED, null, 30);
        check(dogChip != null && dogChip.height <= 22 && catChip != null && catChip.height <= 22 && catChip.x > dogChip.x,
                "בתאים: תגית Dog (סגול) ותגית Cat (טורקיז), קטנות (" + dogChip + ", " + catChip + ")");
        check(red3 != null && red3.height <= 24 && dogChip != null && red3.y + red3.height < dogChip.y,
                "בכותרת: zoo : Animal[] — Animal באדום, מעל התאים (ובתאים אין קופסה אדומה של Animal)");

        // --- תרשים המחלקות: רק מחלקות שההרצה משתמשת בהן. לפני שנרשם צעד — אין תרשים
        check(Poly.relations().isEmpty() && Poly.usedClasses().isEmpty(), "לפני שההרצה רשמה צעדים: התרשים ריק");
        List<Step> run = new ArrayList<Step>();
        run.add(s);
        run.add(s2);
        run.add(s3);
        Recorder.adopt(run, "");
        Poly.relations();   // סורק את הצעדים החדשים
        check(Poly.usedClasses().containsAll(java.util.Arrays.asList("Animal", "Dog", "Cat", "Person", "Collar")),
                "אחרי ההרצה (Dog, Cat): גם Animal (מחלקת האב), Person ו-Collar (שדות) בתרשים — " + Poly.usedClasses());

        // --- תרשים המחלקות: הסידור והקשרים
        List<Poly.Rel> rels = Poly.relations();
        boolean collar = false, owner = false;
        for (Poly.Rel r : rels) {
            if ("collar".equals(r.field)) collar = r.composition;
            if ("owner".equals(r.field)) owner = !r.composition;
        }
        check(collar && owner, "יהלום מלא ל-Cat.collar (Cat יוצרת אותו: this.collar = new Collar) וריק ל-Dog.owner (מתקבל מבחוץ)");
        StructPanel probe = new StructPanel();
        PolyPaint.Layout L = PolyPaint.layout(probe, false);
        Rectangle a = L.at.get("Animal"), d = L.at.get("Dog"), c = L.at.get("Cat"), p = L.at.get("Person"), k = L.at.get("Collar");
        check(a != null && d != null && c != null && a.y + a.height < d.y && a.y + a.height < c.y && d.x < c.x,
                "בתרשים: Animal למעלה, Dog ו-Cat מתחת (Dog משמאל)");
        check(p != null && k != null && p.x + p.width <= d.x && k.x >= c.x + c.width && Math.abs(p.y - d.y) < 6,
                "Person ליד Dog (משמאל) ו-Collar ליד Cat (מימין), באותה שורה");
        Poly.Live live = new Poly.Live();
        live.ref = "pet";
        live.refType = "Animal";
        live.objType = "Dog";
        live.runClass = "Dog";
        live.runMethod = "say";
        Dimension sd = PolyPaint.smallDiagram(probe, null, live, 0, 0), fd = PolyPaint.fullDiagram(probe, null, live, 0, 0);
        check(sd.height < 140 && sd.width < 640, "התרשים הקטן קטן (" + sd.width + "×" + sd.height + ")");
        check(fd.width > sd.width && fd.height > 2 * sd.height, "התרשים המלא (בבועה) גדול ממנו (" + fd.width + "×" + fd.height + ")");
        BufferedImage di = new BufferedImage(fd.width + 20, fd.height + 20, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = di.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, di.getWidth(), di.getHeight());
        PolyPaint.fullDiagram(probe, g, live, 10, 10);
        g.dispose();
        if (out != null) ImageIO.write(di, "png", new File(out, "diagram-full.png"));
        Rectangle yellow = bbox(di, PolyPaint.RUN_BG, null, 3);
        check(yellow != null && yellow.height < 30, "בתרשים המלא: הפעולה שרצה (Dog.say) בצהוב " + yellow);

        System.out.println(fails == 0 ? "MARK-END" : "נכשלו " + fails);
    }
}
