package unit4.collectionsLib;

import java.awt.Color;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import unit4.collectionsLib.Model.ArrayView;
import unit4.collectionsLib.Model.ClassInfo;
import unit4.collectionsLib.Model.Frame;
import unit4.collectionsLib.Model.Kind;
import unit4.collectionsLib.Model.Meth;
import unit4.collectionsLib.Model.Obj;
import unit4.collectionsLib.Model.ObjView;
import unit4.collectionsLib.Model.Step;
import unit4.collectionsLib.Model.View;

/**
 * הורשה ופולימורפיזם בהרצה הוויזואלית (Java וגם C# — הכול מחושב מטבלת המחלקות, מהצעדים ומהקוד, כמו SelfViz).
 *
 * <p>לכל מחלקה של התלמיד שבתוך היררכיה — צבע משלה, לפי המקום שלה בעץ (קודם השורשים, אחר כך הילדים, לפי סדר ההגדרה בקוד):
 * אדום, סגול, טורקיז, כחול, ירוק, כתום, ורוד — ואז שוב, עם מסגרת מקווקוות. אותו צבע בכל מקום שהמחלקה מופיעה בו (הקופסה שלה בעצם,
 * התגית בתא של מבנה, הטיפוס בתגית של הפניה, עץ המחלקות, מעקב המשתנים, הפס בקוד) — ושם המחלקה תמיד כתוב ליד הצבע.
 *
 * <p>מתי משתמשים בכל דבר — הכללים:
 * <ol>
 * <li>מחלקה בלי מחלקת-על של התלמיד ובלי מחלקה יורשת בתוכנית ← הכרטיס הרגיל של היום: בלי צבעים ובלי קופסאות ({@link #inHierarchy}).
 * <li>מחלקה בתוך היררכיה ← העצם שלה בכרטיס שעומד לבד (StructPanel.drawObjView, וגם ObjDock): קופסאות זו בתוך זו — מחלקת העל העליונה
 *     בחוץ, וכל מחלקה יורשת בתוך מחלקת העל שלה; הקופסה הפנימית ביותר היא המחלקה של העצם. בכל קופסה: הכותרת בצבע, המאפיינים שהמחלקה
 *     עצמה הגדירה, קו מקווקו, והפעולות שלה (הבנאי ואז הפעולות). פעולה שמחלקה פנימית דורסת — בקו חוצה. Object לא מצויר ({@link #card}).
 * <li>בתוך תא של מבנה (מערך, מטריצה, מחסנית / תור, שרשרת, עץ) ← תגית צבעונית קטנה עם שם המחלקה של העצם, והכותרת הקצרה — אף פעם לא
 *     קופסאות בתוך תא (StructPanel.objBox). בכותרת של מערך — טיפוס האיבר המוצהר, בצבע ({@code zoo : Animal[]}).
 * <li>אובייקט בתוך אובייקט (מאפיין, הכרטיס הפנימי של ObjCard) ממחלקה שבהיררכיה ← גם הוא בקופסאות, אבל קומפקטי: רק מאפיינים, בלי פעולות.
 * <li>פעולות בקופסאות — בכרטיס שעומד לבד. בקופסה עם יותר מ-6 פעולות: רק הבנאי, הפעולות שנדרסות / דורסות והפעולה שרצה עכשיו, ו-"…".
 * <li>מאפיין מאותו סוג (MyNode next ברשימה מקושרת של התלמיד) — נשאר הפניה, כמו היום (Recorder.selfTyped); בתרשים המחלקות — לא מצויר קו.
 * <li>הפניה היא חלון: כל משתנה שמצביע על העצם — תגית "שם : טיפוס" על הקופסה של הטיפוס המוצהר שלו. ההפניה שהשורה משתמשת בה (או
 *     היחידה) — הקופסאות שלא נראות דרכה מכוסות בפסים "לא נראה דרך pet", ורק כשהטיפוס המוצהר שונה מהטיפוס של העצם ({@link #card}).
 * <li>"מותר? / מה רץ?" ({@link #call}) — רק כשהעצם בהיררכיה וגם הפעולה מוגדרת ביותר ממחלקה אחת בשרשרת שלו (או, ב-C#, פעולה בלי
 *     virtual שיש לה גרסה אחרת). אחרת — קריאה רגילה, כמו היום.
 * <li>תרשים המחלקות ({@link #diagram}) — רק כשבתוכנית יש הורשה או הכלה בין מחלקות של התלמיד. מחלקה שלא בעץ הורשה — באפור.
 *     ירושה: קו רציף ומשולש ריק אצל מחלקת העל. הכלה (מאפיין שהטיפוס שלו מחלקה אחרת של התלמיד): קו רציף ויהלום אצל המחלקה שמכילה,
 *     עם שם המאפיין. יהלום מלא (composition) כשהמחלקה שמכילה יוצרת את העצם בעצמה ({@code this.collar = new Collar(...)}, או
 *     {@code = new X(...)} בהגדרת המאפיין) — בתוך המחלקה שלה; יהלום ריק (aggregation) כשהיא מקבלת עצם שנוצר בחוץ (פרמטר / setter /
 *     השמה מבחוץ), וגם כשאי אפשר לדעת. מערך / אוסף של מחלקה של התלמיד — אותו קו, עם * אצל החלק.
 * <li>מחלקה מופשטת: abstract בכותרת. ממשק (interface) לא מצויר כקופסה — "implements X" כתגית קטנה מתחת לכותרת.
 * </ol>
 */
final class Poly {
    private Poly() {}

    // ------------------------------------------------------------ צבעים

    private static final int[] INK = { 0xC0392B, 0x7B3FB0, 0x0C8583, 0x1F6FB5, 0x1F8A4C, 0xC56A12, 0xB02E78 };
    private static final int[] BG = { 0xFDEEED, 0xF4ECFB, 0xE4F6F5, 0xEAF2FB, 0xE8F6EE, 0xFDF1E4, 0xFBEBF4 };
    private static final int[] HEAD = { 0xF9D9D6, 0xE6D6F5, 0xC9ECEA, 0xD3E4F5, 0xCDEEDB, 0xF8DCBF, 0xF3D1E4 };
    /** מחלקה שלא בעץ הורשה (בתרשים המחלקות): אפור-כחול */
    static final Color GRAY = new Color(0x55607A), GRAY_HEAD = new Color(0xEEF1F6), GRAY_BG = new Color(0xFAFBFD);

    static Color ink(String cls) {
        int c = colorOf(cls);
        return c < 0 ? GRAY : new Color(INK[c % INK.length]);
    }

    static Color bg(String cls) {
        int c = colorOf(cls);
        return c < 0 ? GRAY_BG : new Color(BG[c % BG.length]);
    }

    static Color head(String cls) {
        int c = colorOf(cls);
        return c < 0 ? GRAY_HEAD : new Color(HEAD[c % HEAD.length]);
    }

    /** הצבעים חוזרים (יותר מ-7 מחלקות בעצים): מסגרת מקווקוות, כדי ששתי מחלקות לא ייראו אותו דבר */
    static boolean dashed(String cls) {
        return colorOf(cls) >= INK.length;
    }

    static String hex(String cls) {
        int c = colorOf(cls);
        return c < 0 ? "-" : String.format("#%06x", INK[c % INK.length]);
    }

    // ------------------------------------------------------------ טבלת המחלקות

    private static final Map<String, ClassInfo> classes = new LinkedHashMap<String, ClassInfo>();
    private static int nextColor;
    private static boolean scanned;
    /** קוד המקור של התלמיד (Java: הקבצים שנסרקו; C#: מקובץ ההרצה) — בשביל סדר הפעולות והכלה שנוצרת בפנים */
    private static final List<String> sources = new ArrayList<String>();

    static synchronized void define(ClassInfo c) {
        if (c == null || c.name == null || classes.containsKey(c.name)) return;
        c.order = classes.size();
        classes.put(c.name, c);
        recolor();
        cache.clear();
    }

    static synchronized ClassInfo get(String name) {
        return name == null ? null : classes.get(name);
    }

    static synchronized List<ClassInfo> all() {
        return new ArrayList<ClassInfo>(classes.values());
    }

    static synchronized void addSource(String text) {
        if (text != null) sources.add(LoopViz.blank(text));
    }

    /** בשביל בדיקות: טבלה ריקה */
    static synchronized void reset() {
        classes.clear();
        sources.clear();
        nextColor = 0;
        scanned = false;
        relKey = null;
        cache.clear();
    }

    /**
     * הצבעים: קודם השורשים (מחלקות בלי מחלקת-על של התלמיד שיש להן יורשת), אחר כך הילדים שלהם, שכבה אחר שכבה, לפי סדר ההגדרה.
     * צבע שכבר ניתן לא משתנה (מחלקה שמתגלה מאוחר יותר מקבלת את הצבע הבא)
     */
    private static void recolor() {
        List<ClassInfo> queue = new ArrayList<ClassInfo>();
        for (ClassInfo c : classes.values()) if (hier(c) && (c.sup == null || !classes.containsKey(c.sup))) queue.add(c);
        for (int i = 0; i < queue.size(); i++)
            for (ClassInfo k : classes.values()) if (queue.get(i).name.equals(k.sup) && !queue.contains(k) && !k.isInterface) queue.add(k);
        for (ClassInfo c : queue) if (c.color < 0) c.color = nextColor++;
    }

    private static boolean hier(ClassInfo c) {
        if (c.isInterface) return false;
        if (c.sup != null && classes.containsKey(c.sup)) return true;
        for (ClassInfo k : classes.values()) if (c.name.equals(k.sup) && !k.isInterface) return true;
        return false;
    }

    static synchronized int colorOf(String type) {
        ClassInfo c = type == null ? null : classes.get(type);
        return c == null ? -1 : c.color;
    }

    /** כלל 1: מחלקה שבהיררכיה של מחלקות התלמיד (יש לה מחלקת-על או יורשת שהתלמיד כתב) */
    static boolean inHierarchy(String type) {
        return colorOf(type) >= 0;
    }

    /** יש בתוכנית הורשה בין מחלקות של התלמיד */
    static synchronized boolean any() {
        for (ClassInfo c : classes.values()) if (c.color >= 0) return true;
        return false;
    }

    /** שרשרת ההורשה של המחלקה — מבחוץ (מחלקת העל העליונה של התלמיד) פנימה (המחלקה עצמה). ריקה אם המחלקה לא בהיררכיה */
    static synchronized List<ClassInfo> chain(String type) {
        List<ClassInfo> res = new ArrayList<ClassInfo>();
        if (!inHierarchy(type)) return res;
        for (ClassInfo c = classes.get(type); c != null && res.size() < 30; c = c.sup == null ? null : classes.get(c.sup)) res.add(0, c);
        return res;
    }

    static synchronized List<ClassInfo> children(String name) {
        List<ClassInfo> res = new ArrayList<ClassInfo>();
        for (ClassInfo k : classes.values()) if (name.equals(k.sup) && !k.isInterface) res.add(k);
        return res;
    }

    /** sub היא sup או יורשת ממנה (לפי הטבלה) */
    static synchronized boolean isA(String sub, String sup) {
        for (ClassInfo c = classes.get(sub); c != null; c = c.sup == null ? null : classes.get(c.sup)) if (c.name.equals(sup)) return true;
        return sub != null && sub.equals(sup);
    }

    /** "Animal[]" / "List<Animal>" → "Animal" */
    static String elem(String declared) {
        if (declared == null) return null;
        String t = declared.trim();
        Matcher g = Pattern.compile("<\\s*([\\w.]+)\\s*>").matcher(t);
        if (g.find()) t = g.group(1);
        while (t.endsWith("[]")) t = t.substring(0, t.length() - 2).trim();
        int k = t.indexOf('[');
        if (k > 0) t = t.substring(0, k).trim();   // C#: int[,]
        return simple(t);
    }

    /** "pkg.Outer$Inner" → "Inner" */
    static String simple(String cls) {
        if (cls == null) return null;
        int i = Math.max(cls.lastIndexOf('.'), cls.lastIndexOf('$'));
        return i < 0 ? cls : cls.substring(i + 1);
    }

    /** הפעולה name שהמחלקה עצמה מגדירה (לא static, לא בנאי) — או null */
    static Meth own(ClassInfo c, String name) {
        if (c == null) return null;
        for (Meth m : c.methods) if (!m.ctor && !m.isStatic && m.name.equals(name)) return m;
        return null;
    }

    private static boolean isCtor(String method) {
        return "<init>".equals(method) || ".ctor".equals(method);
    }

    // ------------------------------------------------------------ Java: המחלקות מהקוד (שמות וסדר) ומ-reflection (מבנה)

    static synchronized boolean scanned() {
        return scanned;
    }

    private static final Pattern DECL = Pattern.compile("\\b(class|interface)\\s+([A-Za-z_$][\\w$]*)");

    /** מ-LineByLine (בתהליך של התוכנית, לפני main): הקבצים בתיקייה של המחלקה הראשית */
    static void scanJava(Class<?> main) {
        if (main == null) return;
        Class<?> top = main;
        while (top.getEnclosingClass() != null) top = top.getEnclosingClass();
        scanJava(new Frame(main.getName(), "main", top.getSimpleName() + ".java", 1), main.getClassLoader());
    }

    /** מ-Recorder, אם LineByLine לא סרק (מסגרת של main) */
    static void scanJava(Frame main) {
        scanJava(main, Thread.currentThread().getContextClassLoader());
    }

    private static void scanJava(Frame main, ClassLoader cl) {
        synchronized (Poly.class) {
            if (scanned) return;
            scanned = true;
        }
        try {
            File file = SourceFiles.fileOf(main);
            if (file == null) return;
            List<File> files = new ArrayList<File>();
            files.add(file);
            File[] sib = file.getParentFile() == null ? null : file.getParentFile().listFiles();
            if (sib != null) {
                Arrays.sort(sib);
                for (File f : sib) if (f.isFile() && f.getName().endsWith(".java") && !f.equals(file) && files.size() < 60) files.add(f);
            }
            String pkg = main.packagePath().replace('/', '.');
            for (File f : files) {
                String text = SourceFiles.readText(f);
                if (text == null) continue;
                addSource(text);
                String blank = LoopViz.blank(text);
                List<String> tops = new ArrayList<String>();
                Matcher m = DECL.matcher(blank);
                while (m.find()) {
                    Class<?> c = load(cl, pkg, m.group(2), tops);
                    if (c == null) continue;
                    if (c.getEnclosingClass() == null) tops.add(m.group(2));
                    ensure(c);
                }
            }
        } catch (Throwable ignored) {
            // בלי טבלה מהקוד — המחלקות נוספות כשהעצמים שלהן מופיעים (ensure)
        }
    }

    private static Class<?> load(ClassLoader cl, String pkg, String name, List<String> tops) {
        List<String> tries = new ArrayList<String>();
        tries.add(pkg + name);
        for (String t : tops) tries.add(pkg + t + "$" + name);
        for (String n : tries) {
            try {
                return Class.forName(n, false, cl);   // בלי לאתחל — לא מריצים קוד של התלמיד
            } catch (Throwable ignored) {
                // לא זה
            }
        }
        return null;
    }

    private static boolean userClass(Class<?> c) {
        if (c == null || c.isArray() || c.isPrimitive() || c.isEnum() || c == Object.class) return false;
        String n = c.getName();
        return !n.startsWith("java.") && !n.startsWith("javax.") && !n.startsWith("jdk.") && !n.startsWith("sun.")
                && !n.startsWith("com.sun.") && !n.startsWith("unit4.collectionsLib.") && !n.contains("$$Lambda");
    }

    /** המחלקה (ומחלקות העל שלה שהתלמיד כתב) בטבלה — מ-reflection. נקרא גם מ-Recorder לכל עצם של מחלקה של התלמיד */
    static void ensure(Class<?> c) {
        if (!userClass(c) || c.isAnonymousClass() || c.isSynthetic()) return;
        if (get(c.getSimpleName()) != null) return;
        try {
            if (userClass(c.getSuperclass())) ensure(c.getSuperclass());
            define(fromClass(c));
        } catch (Throwable ignored) {
            // מחלקה שאי אפשר לקרוא — בלי קופסאות
        }
    }

    private static ClassInfo fromClass(Class<?> c) {
        ClassInfo ci = new ClassInfo();
        ci.name = c.getSimpleName();
        if (userClass(c.getSuperclass())) ci.sup = c.getSuperclass().getSimpleName();
        ci.isInterface = c.isInterface();
        ci.isAbstract = java.lang.reflect.Modifier.isAbstract(c.getModifiers()) && !c.isInterface();
        for (Class<?> i : c.getInterfaces()) ci.ifaces.add(i.getSimpleName());
        for (java.lang.reflect.Field f : c.getDeclaredFields()) {
            if (java.lang.reflect.Modifier.isStatic(f.getModifiers()) || f.isSynthetic()) continue;
            ci.fields.add(new String[] { f.getName(), typeName(f.getGenericType()), vis(f.getModifiers()) });
        }
        List<Meth> ctors = new ArrayList<Meth>(), ms = new ArrayList<Meth>();
        for (java.lang.reflect.Constructor<?> k : c.getDeclaredConstructors()) {
            if (k.isSynthetic()) continue;
            Meth m = new Meth();
            m.name = ci.name;
            m.ctor = true;
            m.count = k.getParameterTypes().length;
            m.params = params(k.getParameterTypes());
            m.isPrivate = java.lang.reflect.Modifier.isPrivate(k.getModifiers());
            m.vis = vis(k.getModifiers());
            ctors.add(m);
        }
        for (java.lang.reflect.Method x : c.getDeclaredMethods()) {
            if (x.isSynthetic() || x.isBridge() || x.getName().contains("$")) continue;
            int mod = x.getModifiers();
            Meth m = new Meth();
            m.name = x.getName();
            m.count = x.getParameterTypes().length;
            m.params = params(x.getParameterTypes());
            m.isStatic = java.lang.reflect.Modifier.isStatic(mod);
            m.isAbstract = java.lang.reflect.Modifier.isAbstract(mod);
            m.isPrivate = java.lang.reflect.Modifier.isPrivate(mod);
            m.virt = !m.isStatic && !m.isPrivate;
            m.override = m.virt && overridesSuper(c, x);
            m.ret = x.getReturnType() == void.class ? null : typeName(x.getGenericReturnType());
            m.vis = vis(mod);
            ms.add(m);
        }
        final Map<String, Integer> at = positions(ci.name);
        Collections.sort(ms, new java.util.Comparator<Meth>() {
            public int compare(Meth a, Meth b) {
                Integer pa = at.get(a.name), pb = at.get(b.name);
                if (pa != null && pb != null && !pa.equals(pb)) return pa - pb;
                if (pa != null && pb == null) return -1;
                if (pa == null && pb != null) return 1;
                return a.name.compareTo(b.name);
            }
        });
        ci.methods.addAll(ctors);
        ci.methods.addAll(ms);
        return ci;
    }

    private static String vis(int mod) {
        return java.lang.reflect.Modifier.isPrivate(mod) ? "-" : java.lang.reflect.Modifier.isPublic(mod) ? "+"
                : java.lang.reflect.Modifier.isProtected(mod) ? "#" : "~";
    }

    /** java.util.List<pkg.Animal> → List<Animal>; Animal[] */
    private static String typeName(java.lang.reflect.Type t) {
        if (t instanceof Class) return ((Class<?>) t).getSimpleName();
        return t.toString().replaceAll("[\\w$]+\\.", "").replace('$', '.');
    }

    private static String params(Class<?>[] ps) {
        StringBuilder b = new StringBuilder();
        for (Class<?> p : ps) b.append(b.length() == 0 ? "" : ",").append(p.getSimpleName());
        return b.toString();
    }

    private static boolean overridesSuper(Class<?> c, java.lang.reflect.Method x) {
        for (Class<?> s = c.getSuperclass(); userClass(s); s = s.getSuperclass()) {
            try {
                java.lang.reflect.Method m = s.getDeclaredMethod(x.getName(), x.getParameterTypes());
                int mod = m.getModifiers();
                if (!java.lang.reflect.Modifier.isPrivate(mod) && !java.lang.reflect.Modifier.isStatic(mod)) return true;
            } catch (NoSuchMethodException ignored) {
                // לא כאן — ממשיכים למעלה
            }
        }
        return false;
    }

    /** הגוף של המחלקה בקוד (אחרי LoopViz.blank): {המקום של '{', של '}', הטקסט} — או null */
    private static synchronized Object[] body(String cls) {
        Pattern p = Pattern.compile("\\b(?:class|interface|struct|record)\\s+" + Pattern.quote(cls) + "\\b");
        for (String t : sources) {
            Matcher m = p.matcher(t);
            if (!m.find()) continue;
            int open = t.indexOf('{', m.end());
            if (open < 0) continue;
            int close = match(t, open);
            return new Object[] { open, close, t };
        }
        return null;
    }

    private static int match(String t, int open) {
        int d = 0;
        for (int i = open; i < t.length(); i++) {
            char c = t.charAt(i);
            if (c == '{') d++;
            else if (c == '}' && --d == 0) return i;
        }
        return t.length() - 1;
    }

    /** איפה כל פעולה מוגדרת בגוף המחלקה: שם ואחריו '(' בעומק 1 (ישירות בתוך המחלקה — לא קריאה בתוך פעולה) */
    private static Map<String, Integer> positions(String cls) {
        Map<String, Integer> res = new HashMap<String, Integer>();
        Object[] b = body(cls);
        if (b == null) return res;
        int open = (Integer) b[0], close = (Integer) b[1];
        String t = (String) b[2];
        int d = 0;
        for (int i = open; i <= close && i < t.length(); i++) {
            char c = t.charAt(i);
            if (c == '{') { d++; continue; }
            if (c == '}') { d--; continue; }
            if (d != 1 || !Character.isJavaIdentifierStart(c) || (i > 0 && Character.isJavaIdentifierPart(t.charAt(i - 1)))) continue;
            int j = i;
            while (j < t.length() && Character.isJavaIdentifierPart(t.charAt(j))) j++;
            String w = t.substring(i, j);
            int k = j;
            while (k < t.length() && Character.isWhitespace(t.charAt(k))) k++;
            if (k < t.length() && t.charAt(k) == '(' && !res.containsKey(w)) res.put(w, i);
            i = j - 1;
        }
        return res;
    }

    // ------------------------------------------------------------ הקוד: איזו מחלקה בכל שורה (הפס הצבעוני בחלון הקוד)

    /**
     * לכל שורה בקובץ (מ-1): המחלקה שהשורה בתוכה (מחלקה פנימית — היא), רק למחלקות שבטבלה; null — מחוץ למחלקות.
     * הפס נצבע בצבע המחלקה (מחלקה שלא בעץ הורשה — באפור, ורק כשיש תרשים מחלקות)
     */
    static String[] classLines(String text) {
        if (text == null) return new String[0];
        String t = LoopViz.blank(text);
        String[] lines = new String[t.split("\n", -1).length + 1];
        Matcher m = DECL.matcher(t);
        while (m.find()) {
            String n = m.group(2);
            if (get(n) == null) continue;
            int open = t.indexOf('{', m.end());
            if (open < 0) continue;
            int close = match(t, open), from = lineOf(t, m.start()), to = lineOf(t, close);
            for (int l = from; l <= to && l < lines.length; l++) lines[l] = n;   // פנימית אחרי החיצונית — דורסת אותה
        }
        return lines;
    }

    private static int lineOf(String t, int pos) {
        int l = 1;
        for (int i = 0; i < pos && i < t.length(); i++) if (t.charAt(i) == '\n') l++;
        return l;
    }

    // ------------------------------------------------------------ טיפוסים בצעד

    /** הטיפוס המוצהר של משתנה בצעד (this — המחלקה של הפעולה שרצה); null — לא ידוע */
    static String declared(Step s, String name) {
        if (s == null || name == null) return null;
        if (name.equals("this")) return s.calls.isEmpty() ? null : simple(s.calls.get(0).className);
        return s.types == null ? null : s.types.get(name);
    }

    /** הטיפוס של העצם שהמשתנה מצביע עליו — לפי הערך שבמעקב המשתנים ("Dog Rexy" → Dog); null — לא עצם של מחלקה בטבלה */
    static String actual(Step s, String name) {
        if (s == null || s.vars == null) return null;
        for (String[] v : s.vars) {
            if (!v[0].equals(name)) continue;
            String w = v[1].trim().split("\\s+")[0];
            if (get(w) == null) w = simple(w);   // C# בתוך namespace (ConsoleApp1.Dog), Java בתוך package
            return get(w) != null ? w : null;
        }
        return null;
    }

    private static String code(Step s) {
        if (s == null || s.where == null) return null;
        String l = SourceFiles.line(s.where);
        return l == null ? null : LoopViz.blank(l);
    }

    // ------------------------------------------------------------ הכרטיס: קופסאות, תגיות, חלון

    /** תגית של הפניה: "pet : Animal", על הקופסה box (-1 = מעל כל הקופסאות: Object / לא ידוע) */
    static final class Tag {
        String name, type, hint;
        int box = -1;
    }

    /** קופסה אחת בעצם: מחלקה, המאפיינים שלה, הפעולות, ומה מסומן */
    static final class Box {
        ClassInfo cls;
        Obj part;                                   // המאפיינים של המחלקה הזו (חלק מ-Obj.fields, עם אותה זהות)
        final List<Meth> methods = new ArrayList<Meth>();
        final java.util.Set<Meth> struck = new java.util.HashSet<Meth>();
        Meth running;
        boolean more;                               // הוסתרו פעולות ("…")
        final List<Tag> tags = new ArrayList<Tag>();
        boolean hidden;                             // לא נראית דרך ההפניה של השורה (פסים)
        boolean unbuilt, building;                  // בבנאי: עוד לא נבנתה / נבנית עכשיו
    }

    static final class Card {
        final List<Box> boxes = new ArrayList<Box>();
        final List<Tag> outer = new ArrayList<Tag>();   // תגיות מעל הקופסה החיצונית (Object / טיפוס לא ידוע)
        String ref;                                     // ההפניה שהשורה משתמשת בה (או היחידה)
        String hideNote;                                // "לא נראה דרך pet"
        String caption;                                 // מתחת לעצם: המרה / בנאי
        boolean compact;
    }

    private static final Map<Obj, Object[]> cache = new WeakHashMap<Obj, Object[]>();

    /** הקופסאות של העצם בצעד (null — העצם לא בהיררכיה: כרטיס רגיל). compact — כרטיס בתוך כרטיס: רק מאפיינים */
    static Card card(Step s, Obj o, boolean compact) {
        if (o == null || !inHierarchy(o.type)) return null;
        synchronized (cache) {
            Object[] c = cache.get(o);
            if (c != null && c[0] == s && c[1].equals(compact)) return (Card) c[2];
        }
        Card card;
        try {
            card = build(s, o, compact);
        } catch (RuntimeException e) {
            card = null;
        }
        synchronized (cache) {
            cache.put(o, new Object[] { s, compact, card });
        }
        return card;
    }

    private static Card build(Step s, Obj o, boolean compact) {
        List<ClassInfo> chain = chain(o.type);
        if (chain.isEmpty()) return null;
        Card card = new Card();
        card.compact = compact;
        for (ClassInfo c : chain) {
            Box b = new Box();
            b.cls = c;
            b.part = new Obj();
            b.part.id = o.id;
            b.part.type = c.name;
            b.part.title = o.title;
            b.part.sub = o.sub;
            card.boxes.add(b);
        }
        // המאפיינים לפי הסדר (מבחוץ פנימה): כל מאפיין לקופסה הראשונה מהמקום הנוכחי והלאה שמגדירה מאפיין בשם הזה
        int at = 0;
        for (String[] f : o.fields) {
            int k = -1;
            for (int j = at; j < chain.size() && k < 0; j++) for (String[] cf : chain.get(j).fields) if (cf[0].equals(f[0])) { k = j; break; }
            if (k < 0) k = Math.max(at, 0);
            at = k;
            Obj p = card.boxes.get(k).part;
            p.fields.add(f);
            if (o.nested.containsKey(f[0])) p.nested.put(f[0], o.nested.get(f[0]));
            if (o.arrays.containsKey(f[0])) p.arrays.put(f[0], o.arrays.get(f[0]));
        }
        Frame top = s == null || s.calls.isEmpty() ? null : s.calls.get(0);
        boolean self = o.labels.contains("this") && top != null;
        String ran = self ? simple(top.className) : null;
        if (!compact) {
            for (int k = 0; k < chain.size(); k++) methods(card, k, self && ran.equals(chain.get(k).name) ? top.method : null);
            tags(s, o, card, chain);
        }
        // בנאי: הקופסאות מתמלאות מבחוץ פנימה (super(...) של Java רץ לפני שאר הבנאי; ב-C# base(...) — לפני הגוף)
        if (self && isCtor(top.method)) {
            int b = -1;
            for (int k = 0; k < chain.size(); k++) if (chain.get(k).name.equals(ran)) b = k;
            if (b >= 0) {
                String line = code(s);
                boolean early = line != null && (Pattern.compile("\\b(super|this)\\s*\\(").matcher(line).find()
                        || !Syntax.csharp && Pattern.compile("\\b" + Pattern.quote(ran) + "\\s*\\(").matcher(line).find() && !line.contains("new "));
                List<String> unbuilt = new ArrayList<String>();
                for (int k = 0; k < chain.size(); k++) {
                    Box x = card.boxes.get(k);
                    x.building = k == b;
                    x.unbuilt = k > b || early && k < b;
                    if (x.unbuilt) unbuilt.add(chain.get(k).name);
                }
                if (!compact) card.caption = "‏בבנייה: הבנאי ‎" + ran + "()‎ ממלא את הקופסה של " + ran
                        + (unbuilt.isEmpty() ? "" : " — " + String.join(", ", unbuilt) + (unbuilt.size() > 1 ? " עוד לא נבנו" : " עוד לא נבנתה"));
                return card;   // בבנייה — בלי פסים (הקופסאות שעוד לא נבנו כבר מעומעמות)
            }
        }
        if (compact) return card;
        hatch(s, o, card, chain);
        String cap = castCaption(s, o);
        if (cap != null) card.caption = cap;
        return card;
    }

    private static void methods(Card card, int k, String runningMethod) {
        Box b = card.boxes.get(k);
        List<Meth> own = new ArrayList<Meth>();
        for (Meth m : b.cls.methods) if (!m.isStatic) own.add(m);
        for (Meth m : own) {
            if (m.ctor || m.isPrivate) continue;
            for (int j = k + 1; j < card.boxes.size(); j++)
                for (Meth n : card.boxes.get(j).cls.methods)
                    if (!n.ctor && !n.isStatic && n.override && !n.hides && n.sig().equals(m.sig())) b.struck.add(m);
        }
        if (runningMethod != null)
            for (Meth m : own) if (isCtor(runningMethod) ? m.ctor : !m.ctor && m.name.equals(runningMethod)) { b.running = m; break; }
        if (own.size() <= 6) {
            b.methods.addAll(own);
            return;
        }
        for (Meth m : own) if (m.ctor || b.struck.contains(m) || m.override || m == b.running) b.methods.add(m);
        b.more = true;
    }

    private static void tags(Step s, Obj o, Card card, List<ClassInfo> chain) {
        for (String l : o.labels) {
            Tag t = new Tag();
            t.name = l;
            t.type = declared(s, l);
            for (int k = 0; k < chain.size(); k++) if (chain.get(k).name.equals(t.type)) t.box = k;
            if (t.box >= 0) card.boxes.get(t.box).tags.add(t);
            else card.outer.add(t);
        }
        java.util.Set<Integer> where = new java.util.HashSet<Integer>();
        for (Box b : card.boxes) for (Tag t : b.tags) where.add(t.box);
        for (Tag t : card.outer) if (t.type != null) where.add(-1);
        if (where.size() < 2) return;   // רמז רק כשיש הפניות מכמה טיפוסים
        int last = chain.size() - 1;
        List<Tag> all = new ArrayList<Tag>(card.outer);
        for (Box b : card.boxes) all.addAll(b.tags);
        for (Tag t : all) {
            if (t.type == null) continue;
            if (t.box == last) t.hint = "רואה את " + t.type + " ואת כל מה שמסביב";
            else if (t.box == 0) t.hint = "רואה רק את החלק של " + t.type;
            else if (t.box > 0) t.hint = "רואה את " + t.type + " ואת מה שמסביב, לא את " + chain.get(t.box + 1).name;
            else t.hint = "דרך " + t.type + " לא רואים אף קופסה";
        }
    }

    private static final Pattern ASSIGN = Pattern.compile("(?<![\\w.])([A-Za-z_]\\w*)\\s*=(?!=)");

    /** ההפניה שהשורה משתמשת בה: המשתנה שמקבל ערך, או הראשון שמופיע בשורה — ואם יש רק אחת, היא */
    static String currentRef(Step s, List<String> labels) {
        String line = code(s);
        if (line != null) {
            Matcher a = ASSIGN.matcher(line);
            while (a.find()) if (labels.contains(a.group(1))) return a.group(1);
            for (String l : labels) if (Pattern.compile("(?<![\\w.])" + Pattern.quote(l) + "\\b").matcher(line).find()) return l;
        }
        return labels.size() == 1 ? labels.get(0) : null;
    }

    private static void hatch(Step s, Obj o, Card card, List<ClassInfo> chain) {
        card.ref = currentRef(s, o.labels);
        if (card.ref == null || card.ref.equals("this")) return;   // this בפעולה של מחלקת העל — בלי פסים (העצם כולו שלו)
        String t = declared(s, card.ref);
        if (t == null || t.equals(o.type)) return;   // כלל 7: רק כשהטיפוס המוצהר שונה מהטיפוס של העצם
        int k = -1;
        for (int j = 0; j < chain.size(); j++) if (chain.get(j).name.equals(t)) k = j;
        ClassInfo ci = get(t);
        if (k < 0 && ci != null && ci.isInterface) return;   // ממשק — לא קופסה
        if (k < 0 && !(t.equals("Object") || t.equals("object"))) return;
        if (k == chain.size() - 1) return;
        for (int j = k + 1; j < chain.size(); j++) card.boxes.get(j).hidden = true;
        card.hideNote = "לא נראה דרך " + card.ref;
    }

    // ------------------------------------------------------------ המרות (cast) — הכיתוב מתחת לעצם

    private static final Pattern UP = Pattern.compile("^\\s*(?:final\\s+)?([A-Za-z_]\\w*)\\s+([A-Za-z_]\\w*)\\s*=\\s*([A-Za-z_]\\w*)\\s*;");
    private static final Pattern REASSIGN = Pattern.compile("^\\s*([A-Za-z_]\\w*)\\s*=\\s*([A-Za-z_]\\w*)\\s*;");
    private static final Pattern DOWN = Pattern.compile("([A-Za-z_]\\w*)\\s*=\\s*\\(\\s*([A-Za-z_]\\w*)\\s*\\)\\s*([A-Za-z_]\\w*)\\s*;");

    /** השורה הקודמת באותה הפעלה של הפעולה (צעד LINE), או null */
    static Step prevLine(Step s) {
        if (s == null || s.calls.isEmpty() || s.where == null) return null;
        for (int j = s.index - 1, n = 0; j >= 0 && n < 300; j--, n++) {
            Step p;
            try {
                p = Recorder.step(j);
            } catch (RuntimeException e) {
                return null;
            }
            if (p == null || p.calls.size() > s.calls.size()) continue;
            if (p.calls.size() < s.calls.size()) return null;
            if (p.kind != Kind.LINE || p.where == null) continue;
            if (!p.where.method.equals(s.where.method) || !p.where.className.equals(s.where.className)) return null;
            if (p.where.line != s.where.line) return p;
        }
        return null;
    }

    /** הכיתוב אחרי השורה הקודמת: המרה כלפי מעלה (Animal pet = d;) או כלפי מטה ((Mammal) myAnimal) לאותו עצם */
    private static String castCaption(Step s, Obj o) {
        Step p = prevLine(s);
        String line = code(p);
        if (line == null) return null;
        Matcher m = UP.matcher(line);
        String to = null, from = null, type = null;
        if (m.find()) { type = m.group(1); to = m.group(2); from = m.group(3); }
        else if ((m = REASSIGN.matcher(line)).find()) { to = m.group(1); from = m.group(2); type = declared(s, to); }
        if (to != null && o.labels.contains(to) && o.labels.contains(from) && !to.equals(from)) {
            String ft = declared(s, from);
            if (type != null && ft != null && !type.equals(ft) && isA(ft, type))
                return "‏העצם של " + from + " ושל " + to + " הוא אותו עצם. ‪" + SourceFiles.line(p.where).trim()
                        + "‬ לא יצר עצם חדש ולא שינה אותו.";
        }
        Matcher d = DOWN.matcher(line);
        if (d.find() && o.labels.contains(d.group(1)) && inHierarchy(d.group(2)) && isA(o.type, d.group(2)))
            return "‏בעצם יש חלק של " + d.group(2) + " — ההמרה חוקית. דרך " + d.group(1)
                    + (get(d.group(2)).sup == null ? " רואים רק את החלק של " + d.group(2) + "." : " רואים את " + d.group(2) + " ואת מה שמסביב.");
        return null;
    }

    // ------------------------------------------------------------ "מותר? / מה רץ?"

    /** קריאה לפעולה על עצם בהיררכיה (pet.say()), כפי שהחלונית בראש התצוגה מסבירה אותה */
    static final class Call {
        String text;            // pet.say()
        String recv;            // pet / zoo[i] / super / this / ((Mammal) a2)
        String method;          // say
        String declared;        // טיפוס ההפניה (Animal) — null: לא ידוע (מוצג רק "מה רץ?")
        String foundIn;         // איפה הפעולה נמצאה, מטיפוס ההפניה החוצה ("מותר?")
        String actual;          // המחלקה של העצם (Dog)
        final List<String> searched = new ArrayList<String>();   // הקופסאות שנבדקו, מבפנים החוצה, עד שנמצאה
        String runs;            // הגרסה שרצה לפי החיפוש
        String ran;             // המחלקה שהקוד שלה באמת רץ (המסגרת)
        boolean viaSuper, nonVirtual;
        String superFrom;       // super.say() / base.Say(): המחלקה שהקוד שלה קרא (מחוץ לקופסה שלה ממשיכים לחפש)
    }

    private static final Map<Step, Call[]> calls = new WeakHashMap<Step, Call[]>();

    static Call call(Step s) {
        if (s == null) return null;
        synchronized (calls) {
            Call[] c = calls.get(s);
            if (c != null) return c[0];
        }
        Call res;
        try {
            res = computeCall(s);
        } catch (RuntimeException e) {
            res = null;
        }
        synchronized (calls) {
            calls.put(s, new Call[] { res });
        }
        return res;
    }

    private static Call computeCall(Step s) {
        if (s.calls.size() < 2 || s.vars == null) return null;
        Frame top = s.calls.get(0), caller = s.calls.get(1);
        if (isCtor(top.method)) return null;
        String actual = actual(s, "this");
        if (actual == null || !inHierarchy(actual)) return null;
        List<ClassInfo> chain = chain(actual);
        int defs = 0;
        for (ClassInfo c : chain) if (own(c, top.method) != null) defs++;
        Call c = new Call();
        c.method = top.method;
        c.actual = actual;
        c.ran = simple(top.className);
        String line = caller.file == null ? null : SourceFiles.line(caller);
        line = line == null ? null : LoopViz.blank(line);
        String callerCls = simple(caller.className);
        if (line != null) {
            Matcher m = Pattern.compile("(\\(\\s*\\(\\s*[A-Za-z_]\\w*\\s*\\)\\s*[A-Za-z_]\\w*\\s*\\)|[A-Za-z_]\\w*(?:\\s*\\[[^\\]]*\\])*)\\s*\\.\\s*"
                    + Pattern.quote(top.method) + "\\s*\\(").matcher(line);
            if (m.find()) {
                c.recv = m.group(1).replaceAll("\\s+", m.group(1).startsWith("(") ? " " : "");
            } else if (Pattern.compile("(?<![\\w.])" + Pattern.quote(top.method) + "\\s*\\(").matcher(line).find()) {
                c.recv = "this";   // בלי נקודה — על אותו עצם
            }
        }
        Map<String, String> ct = callerTypes(s);
        if (c.recv != null) {
            c.text = (c.recv.equals("this") && !line.matches("(?s).*\\bthis\\s*\\..*") ? "" : c.recv + ".") + top.method + "()";
            if (c.recv.equals("super") || c.recv.equals("base")) {
                c.viaSuper = true;
                c.superFrom = get(callerCls) != null ? callerCls : null;
                ClassInfo cc = get(callerCls);
                c.declared = cc == null ? null : cc.sup;
            } else if (c.recv.equals("this")) {
                c.declared = callerCls;
            } else if (c.recv.startsWith("(")) {
                Matcher k = Pattern.compile("\\(\\s*\\(\\s*([A-Za-z_]\\w*)").matcher(c.recv);
                if (k.find()) c.declared = k.group(1);
            } else {
                String name = c.recv.replaceAll("\\[.*$", "");
                String t = ct == null ? null : ct.get(name);
                if (t == null) t = fieldType(callerCls, name);
                c.declared = c.recv.contains("[") ? elem(t) : t;
            }
            if (c.declared != null && get(c.declared) == null) c.declared = null;
        } else {
            c.text = top.method + "()";
        }
        if (c.declared != null)
            for (ClassInfo x = get(c.declared); x != null; x = x.sup == null ? null : get(x.sup))
                if (own(x, c.method) != null) { c.foundIn = x.name; break; }
        // מה רץ: מהקופסה הפנימית החוצה (super — מהקופסה שמחוץ לזו שהקוד שלה קרא)
        String start = c.viaSuper && c.declared != null ? c.declared : actual;
        ClassInfo found = null;
        for (ClassInfo x = get(start); x != null; x = x.sup == null ? null : get(x.sup)) {
            c.searched.add(x.name);
            Meth m = own(x, c.method);
            if (m == null) continue;
            if (Syntax.csharp && !c.viaSuper && c.foundIn != null && !x.name.equals(c.foundIn) && !m.override) continue;   // new — מסתירה, לא דורסת
            found = x;
            break;
        }
        c.runs = found == null ? c.ran : found.name;
        if (Syntax.csharp && c.foundIn != null && !c.viaSuper) {
            Meth m = own(get(c.foundIn), c.method);
            boolean hidden = false;   // יש גרסה אחרת (new) בקופסה פנימית יותר — רק אז ההסבר על virtual מעניין
            for (ClassInfo x = get(actual); x != null && !x.name.equals(c.foundIn); x = x.sup == null ? null : get(x.sup))
                if (own(x, c.method) != null) hidden = true;
            if (m != null && !m.virt && hidden) {   // C#: בלי virtual — הגרסה של טיפוס ההפניה
                c.nonVirtual = true;
                c.runs = c.foundIn;
            }
        }
        if (defs < 2 && !c.nonVirtual) return null;   // כלל 8: הפעולה מוגדרת רק במחלקה אחת — קריאה רגילה
        return c;
    }

    /** הטיפוסים של המשתנים בפעולה שקראה: הצעד האחרון שלה לפני שנכנסנו */
    private static Map<String, String> callerTypes(Step s) {
        int d = s.calls.size();
        for (int j = s.index - 1, n = 0; j >= 0 && n < 2000; j--, n++) {
            Step p;
            try {
                p = Recorder.step(j);
            } catch (RuntimeException e) {
                return null;
            }
            if (p == null) continue;
            if (p.calls.size() == d - 1 && !p.calls.isEmpty()) return p.types;
            if (p.calls.size() < d - 1) return null;
        }
        return null;
    }

    private static String fieldType(String cls, String field) {
        for (ClassInfo x = get(cls); x != null; x = x.sup == null ? null : get(x.sup))
            for (String[] f : x.fields) if (f[0].equals(field)) return f[1];
        return null;
    }

    /**
     * השורות של החלונית (לציור ולבדיקות): כל שורה — רשימה של חלקים: טקסט בעברית, \u0001 + מחלקה (תגית צבעונית), \u0002 + קוד,
     * \u0003 + תווית ("מותר?" / "מה רץ?"), \u0004 = ✓
     */
    static List<List<String>> lines(Call c) {
        List<List<String>> res = new ArrayList<List<String>>();
        res.add(new ArrayList<String>(Arrays.asList("\u0002" + c.text)));
        if (c.declared != null && c.foundIn != null && !c.viaSuper) {
            List<String> l = new ArrayList<String>();
            l.add("\u0003מותר?");
            String who = c.recv.equals("this") ? "this" : c.recv;
            l.add("\u0002" + who);
            l.add("היא הפניה מסוג");
            l.add("\u0001" + c.declared);
            l.add(", ול-");
            l.add("\u0001" + c.declared);
            l.add("יש");
            l.add("\u0002" + c.method + "()");
            if (!c.foundIn.equals(c.declared)) {
                l.add("בירושה מ-");
                l.add("\u0001" + c.foundIn);
            }
            l.add("\u0004");
            res.add(l);
        }
        List<String> r = new ArrayList<String>();
        r.add("\u0003מה רץ?");
        String runs = c.ran != null ? c.ran : c.runs;
        if (c.nonVirtual) {
            r.add("ב-");
            r.add("\u0001" + c.foundIn);
            r.add("הפעולה");
            r.add("\u0002" + c.method + "()");
            r.add("לא סומנה virtual, ולכן רצה הגרסה של טיפוס ההפניה:");
            r.add("\u0002" + runs + "." + c.method + "()");
        } else if (c.viaSuper) {
            r.add("\u0002" + c.recv + "." + c.method + "()");
            r.add("ממשיך לחפש מהקופסה שמחוץ ל-");
            r.add("\u0001" + (c.superFrom != null ? c.superFrom : c.searched.isEmpty() ? runs : childOf(c.searched.get(0))));
            r.add(". ב-");
            r.add("\u0001" + runs);
            r.add("יש");
            r.add("\u0002" + c.method + "()");
            r.add(", ולכן רצה");
            r.add("\u0002" + runs + "." + c.method + "()");
        } else {
            r.add("מחפשים מהקופסה הפנימית החוצה.");
            boolean first = true;
            for (String x : c.searched) {
                if (x.equals(runs)) break;
                r.add(first ? "בקופסה הפנימית" : "בקופסה");
                r.add("\u0001" + x);
                r.add("אין");
                r.add("\u0002" + c.method + "()");
                r.add(";");
                first = false;
            }
            r.add(first ? "בקופסה הפנימית" : "בקופסה");
            r.add("\u0001" + runs);
            r.add("יש");
            r.add("\u0002" + c.method + "()");
            r.add(", ולכן רצה");
            r.add("\u0002" + runs + "." + c.method + "()");
        }
        res.add(r);
        return res;
    }

    private static String childOf(String sup) {
        for (ClassInfo c : all()) if (sup.equals(c.sup)) return c.name;
        return sup;
    }

    static String flat(List<String> parts) {
        StringBuilder b = new StringBuilder();
        for (String p : parts) {
            String t = p.isEmpty() ? p : p.charAt(0) <= '\u0004' ? (p.charAt(0) == '\u0004' ? "✓" : p.substring(1)) : p;
            if (b.length() > 0 && !t.startsWith(",") && !t.startsWith(".") && !t.startsWith(";")) b.append(' ');
            b.append(t);
        }
        return b.toString().replace("ול- ", "ול-").replace("ב- ", "ב-").replace("מ- ", "מ-").replace("ל- ", "ל-");
    }

    // ------------------------------------------------------------ instanceof / is, והמרה בשורה

    /** בדיקה בשורה: zoo[i] instanceof Mammal (C#: is) — על העצם עצמו */
    static final class Check {
        String expr, type, actual;    // actual == null: הערך הוא null
        boolean result, cast;         // cast: המרה (Mammal) x שתצליח — "בעצם יש חלק של Mammal"
    }

    private static final Map<Step, Check[]> checks = new WeakHashMap<Step, Check[]>();

    static Check check(Step s) {
        if (s == null) return null;
        synchronized (checks) {
            Check[] c = checks.get(s);
            if (c != null) return c[0];
        }
        Check res;
        try {
            res = computeCheck(s);
        } catch (RuntimeException e) {
            res = null;
        }
        synchronized (checks) {
            checks.put(s, new Check[] { res });
        }
        return res;
    }

    private static final String EXPR = "([A-Za-z_]\\w*(?:\\s*\\[[^\\]]*\\])?)";

    private static Check computeCheck(Step s) {
        if (s.kind != Kind.LINE || !any()) return null;
        String line = code(s);
        if (line == null) return null;
        Matcher m = Pattern.compile(EXPR + "\\s+" + (Syntax.csharp ? "is" : "instanceof") + "\\s+([A-Za-z_]\\w*)").matcher(line);
        boolean cast = false;
        if (!m.find()) {
            m = Pattern.compile("\\(\\s*([A-Za-z_]\\w*)\\s*\\)\\s*" + EXPR).matcher(line);
            if (!m.find() || !inHierarchy(m.group(1))) return null;
            cast = true;
        }
        Check c = new Check();
        c.cast = cast;
        c.expr = (cast ? m.group(2) : m.group(1)).replaceAll("\\s+", "");
        c.type = cast ? m.group(1) : m.group(2);
        if (!inHierarchy(c.type) && get(c.type) == null) return null;
        Object[] a = valueOf(s, c.expr);
        if (a == null) return null;
        c.actual = (String) a[0];
        c.result = c.actual != null && isA(c.actual, c.type);
        if (cast && !c.result) return null;   // המרה שתיכשל — כרטיס השגיאה בצעד הבא
        if (cast && c.actual != null && c.actual.equals(declared(s, c.expr))) return null;
        return c;
    }

    /** {הטיפוס של העצם (או null אם הערך null)} של ביטוי פשוט: משתנה, או תא במערך (zoo[i] / zoo[2]); null — לא יודעים */
    private static Object[] valueOf(Step s, String expr) {
        Matcher m = Pattern.compile("^([A-Za-z_]\\w*)(?:\\[([^\\]]*)\\])?$").matcher(expr);
        if (!m.matches()) return null;
        String name = m.group(1);
        if (m.group(2) == null) {
            if (s.vars == null) return null;
            for (String[] v : s.vars) if (v[0].equals(name)) return new Object[] { v[1].equals("null") ? null : actual(s, name) };
            return null;
        }
        int idx = -1;
        String i = m.group(2).trim();
        try {
            idx = Integer.parseInt(i);
        } catch (NumberFormatException e) {
            if (s.vars != null) for (String[] v : s.vars) if (v[0].equals(i)) try { idx = Integer.parseInt(v[1]); } catch (NumberFormatException ignored) { /* לא מספר */ }
        }
        if (idx < 0) return null;
        for (View v : s.views) {
            if (!(v instanceof ArrayView) || v.title == null) continue;
            boolean named = false;
            for (String n : v.title.split(",\\s*")) if (n.trim().equals(name)) named = true;
            ArrayView av = (ArrayView) v;
            if (!named || idx >= av.items.size()) continue;
            Obj o = idx < av.objs.size() ? av.objs.get(idx) : null;
            if (o != null) return new Object[] { inHierarchy(o.type) || get(o.type) != null ? o.type : null };
            return av.items.get(idx).equals("null") ? new Object[] { null } : null;
        }
        return null;
    }

    // ------------------------------------------------------------ תרשים המחלקות

    /** קשר בתרשים: ירושה (part = מחלקת העל) או הכלה (owner מכיל את part במאפיין field) */
    static final class Rel {
        String owner, part, field;
        boolean inherit, many, composition;
    }

    /** הקשרים בין מחלקות התלמיד (בלי מאפיין מאותו סוג — רשימה מקושרת: לא מצויר קו) */
    static synchronized List<Rel> relations() {
        int fields = 0;
        for (ClassInfo c : classes.values()) fields += c.fields.size();
        String key = classes.size() + "|" + fields + "|" + sources.size();
        if (key.equals(relKey)) return relCache;   // נקרא בכל ציור — מחושב רק כשהטבלה השתנתה
        List<Rel> res = new ArrayList<Rel>();
        for (ClassInfo c : classes.values()) {
            if (c.sup != null && classes.containsKey(c.sup) && !c.isInterface) {
                Rel r = new Rel();
                r.owner = c.name;
                r.part = c.sup;
                r.inherit = true;
                res.add(r);
            }
        }
        for (ClassInfo c : classes.values()) {
            for (String[] f : c.fields) {
                String p = elem(f[1]);
                if (p == null || !classes.containsKey(p) || classes.get(p).isInterface) continue;
                if (p.equals(c.name) || isA(c.name, p)) continue;   // מאפיין מאותו סוג (next) — לא קו
                Rel r = new Rel();
                r.owner = c.name;
                r.part = p;
                r.field = f[0];
                r.many = !p.equals(f[1].trim());
                r.composition = createsItself(c.name, f[0], p);
                res.add(r);
            }
        }
        relKey = key;
        relCache = res;
        return res;
    }

    private static String relKey;
    private static List<Rel> relCache;

    /** המחלקה owner יוצרת בעצמה (בתוך הקוד שלה) את העצם שבמאפיין field: field = new Part(...) / this.field = new Part(...) */
    static boolean createsItself(String owner, String field, String part) {
        Object[] b = body(owner);
        if (b == null) return false;
        String t = ((String) b[2]).substring((Integer) b[0], Math.min(((String) b[2]).length(), (Integer) b[1] + 1));
        return Pattern.compile("(?<![\\w.])(?:this\\s*\\.\\s*)?" + Pattern.quote(field) + "(?:\\s*\\[[^\\]]*\\])?\\s*=\\s*new\\s+"
                + Pattern.quote(part) + "\\s*[(<{]").matcher(t).find();
    }

    /** כלל 9: התרשים מוצג רק כשיש הורשה או הכלה בין מחלקות של התלמיד */
    static boolean diagram() {
        return !relations().isEmpty();
    }

    /** המחלקות שבתרשים: כל מחלקה שיש לה קשר, לפי הסדר */
    static List<ClassInfo> diagramClasses() {
        List<String> names = new ArrayList<String>();
        for (Rel r : relations()) {
            if (!names.contains(r.owner)) names.add(r.owner);
            if (!names.contains(r.part)) names.add(r.part);
        }
        List<ClassInfo> res = new ArrayList<ClassInfo>();
        for (ClassInfo c : all()) if (names.contains(c.name)) res.add(c);
        return res;
    }

    /** הסימון החי בתרשים: ההפניה של השורה (pet : Animal) והמחלקה של העצם שלה, והפעולה שרצה עכשיו (המחלקה, הפעולה) */
    static final class Live {
        String ref, refType, objType, runClass, runMethod;
    }

    static Live live(Step s) {
        Live l = new Live();
        if (s == null) return l;
        try {
            for (View v : s.views) {
                if (!(v instanceof ObjView) || ((ObjView) v).obj == null) continue;
                Card c = card(s, ((ObjView) v).obj, false);
                if (c == null || c.ref == null) continue;
                String line = code(s);
                boolean used = line != null && Pattern.compile("(?<![\\w.])" + Pattern.quote(c.ref) + "\\b").matcher(line).find();
                if (l.ref != null && !used) continue;
                l.ref = c.ref;
                l.refType = declared(s, c.ref);
                l.objType = ((ObjView) v).obj.type;
                if (used) break;
            }
            Call call = call(s);
            if (call != null) {
                l.runClass = call.ran;
                l.runMethod = call.method;
            } else if (!s.calls.isEmpty() && s.vars != null && actual(s, "this") != null && inHierarchy(actual(s, "this"))) {
                l.runClass = simple(s.calls.get(0).className);
                l.runMethod = s.calls.get(0).method;
            }
        } catch (RuntimeException ignored) {
            // בלי סימון
        }
        return l;
    }

    // ------------------------------------------------------------ בדיקות (-Ddsviz.conditions=true / TraceViewer --check)

    /**
     * מה מצויר בכל צעד: [קופסאות #צעד שורה L: d: Animal{name="Rexy"} > Dog{breed="Boxer"} | תגים d:Dog pet:Animal | פסים מ-Dog (pet) | כיתוב...],
     * [דריסה ...], [בדיקה ...], [תא ...], ובהתחלה — טבלת המחלקות והתרשים
     */
    static String dump() {
        StringBuilder b = new StringBuilder();
        if (classes.isEmpty() || !diagram()) return "";   // בלי הורשה / הכלה בין מחלקות של התלמיד — כמו קודם
        b.append("  [מחלקות:");
        for (ClassInfo c : all()) {
            b.append(' ').append(c.name);
            if (c.sup != null) b.append('<').append(c.sup);
            if (c.color >= 0) b.append('=').append(hex(c.name)).append(dashed(c.name) ? "-" : "");
            if (c.isAbstract) b.append("(abstract)");
        }
        b.append("]\n");
        if (diagram()) {
            b.append("  [תרשים:");
            for (Rel r : relations())
                b.append(' ').append(r.owner).append(r.inherit ? " ▷ " : r.composition ? " ◆" + r.field + " " : " ◇" + r.field + " ")
                 .append(r.part).append(r.many ? "*" : "").append(';');
            b.append("]\n");
        }
        String lastCells = null;
        for (int i = 0; i < Recorder.stepCount(); i++) {
            Step s = Recorder.step(i);
            String where = " שורה " + (s.where == null ? "?" : String.valueOf(s.where.line));
            for (View v : s.views) {
                if (!(v instanceof ObjView) || ((ObjView) v).obj == null) continue;
                Card c = card(s, ((ObjView) v).obj, false);
                if (c == null) continue;
                b.append("  [קופסאות #").append(i + 1).append(where).append(": ").append(v.title).append(": ").append(text(c)).append("]\n");
            }
            StringBuilder cells = new StringBuilder();
            for (View v : s.views) {
                if (!(v instanceof ArrayView)) continue;
                ArrayView a = (ArrayView) v;
                String decl = a.title == null ? null : declared(s, a.title.split(",\\s*")[0].trim());
                boolean any = false;
                StringBuilder t = new StringBuilder();
                for (int k = 0; k < a.objs.size(); k++) {
                    Obj o = a.objs.get(k);
                    t.append(k == 0 ? "" : ", ").append(o == null ? a.items.get(k) : (inHierarchy(o.type) ? "{" + o.type + "}" : "") + o.title);
                    any |= o != null && inHierarchy(o.type);
                }
                if (any) cells.append(a.title).append(decl == null ? "" : " : " + decl).append(" [").append(t).append("] ");
            }
            String ck = cells.toString();
            if (!ck.isEmpty() && !ck.equals(lastCells)) b.append("  [תאים #").append(i + 1).append(where).append(": ").append(ck.trim()).append("]\n");
            lastCells = ck;
            Call call = call(s);
            if (call != null) {
                b.append("  [דריסה #").append(i + 1).append(where).append(": ").append(call.text)
                 .append(" מותר=").append(call.declared == null ? "?" : call.declared + (call.foundIn == null ? "" : "(" + call.foundIn + ")"))
                 .append(" רץ=").append(call.runs).append(" בפועל=").append(call.ran)
                 .append(call.viaSuper ? " super" : "").append(call.nonVirtual ? " לא-virtual" : "").append(" |");
                for (List<String> l : lines(call)) b.append(' ').append(flat(l)).append(" |");
                b.append("]\n");
            }
            Check ch = check(s);
            if (ch != null)
                b.append("  [").append(ch.cast ? "המרה" : "בדיקה").append(" #").append(i + 1).append(where).append(": ").append(ch.cast ? "(" + ch.type + ") " + ch.expr : ch.expr + " " + (Syntax.csharp ? "is" : "instanceof") + " " + ch.type)
                 .append(" → ").append(ch.cast ? "חוקית" : String.valueOf(ch.result)).append(" (").append(ch.actual == null ? "null" : ch.actual).append(")]\n");
            Live lv = live(s);
            if (lv.refType != null && diagram())
                b.append("  [תרשים #").append(i + 1).append(where).append(": ").append(lv.ref).append(" : ").append(lv.refType).append(" → ").append(lv.objType)
                 .append(lv.runClass == null ? "" : " רצה " + lv.runClass + "." + lv.runMethod + "()").append("]\n");
        }
        return b.toString();
    }

    static String text(Card c) {
        StringBuilder b = new StringBuilder();
        for (int k = 0; k < c.boxes.size(); k++) {
            Box x = c.boxes.get(k);
            b.append(k == 0 ? "" : " > ").append(x.cls.isAbstract ? "abstract " : "").append(x.cls.name).append('{');
            for (int i = 0; i < x.part.fields.size(); i++) b.append(i == 0 ? "" : ", ").append(x.part.fields.get(i)[0]).append('=').append(x.part.fields.get(i)[1]);
            b.append('}');
            if (!x.methods.isEmpty()) {
                b.append('(');
                for (int i = 0; i < x.methods.size(); i++) {
                    Meth m = x.methods.get(i);
                    b.append(i == 0 ? "" : " ").append(x.struck.contains(m) ? "~" : "").append(m.label()).append(m == x.running ? "◀" : "");
                }
                b.append(x.more ? " …)" : ")");
            }
            if (x.unbuilt) b.append("·לא-נבנתה");
            if (x.building) b.append("·נבנית");
        }
        b.append(" | תגים");
        for (Tag t : c.outer) b.append(' ').append(t.name).append(':').append(t.type == null ? "?" : t.type).append("^");
        for (Box x : c.boxes) for (Tag t : x.tags) b.append(' ').append(t.name).append(':').append(t.type);
        if (c.hideNote != null) {
            for (Box x : c.boxes) if (x.hidden) { b.append(" | פסים מ-").append(x.cls.name).append(" (").append(c.ref).append(')'); break; }
        }
        if (c.caption != null) b.append(" | ").append(c.caption.replaceAll("[‎‏‪‬]", ""));
        return b.toString();
    }
}
