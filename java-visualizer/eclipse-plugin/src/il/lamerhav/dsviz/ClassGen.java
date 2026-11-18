package il.lamerhav.dsviz;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * "צור מחלקה" (בתוך "הוסף מבנה נתונים"): מחלקה בקובץ חדש — שדות private, בנאי שמקבל את כל השדות, get ו-set לכל שדה
 * (בסגנון ספריית הקורס: getValue / setValue — פעולות, לא properties), ו-toString אם מבקשים. Java ו-C#.
 *
 * עותק זהה ב-JavaScript בתוסף של VS Code (vscode-extension/classgen.js) — tools/test/classgen-tests.sh משווה את שניהם
 * על אותם מקרים (קוד והודעות), ומקמפל ומריץ את המחלקות שנוצרו מול הספרייה (javac / dotnet).
 * תואם Java 8 (התוסף של Eclipse, IntelliJ והכלי של Visual Studio).
 *
 * הורשה ופולימורפיזם: "יורשת מ-" (מחלקת העל מהפרויקט — models קורא מהקוד את השדות, הבנאי והפעולות; בנאי עם super / base, דריסות,
 * פעולות חדשות, abstract, ותיקון virtual בקובץ של מחלקת העל ב-C#), הצבעים כמו בהרצה הוויזואלית (colors — כמו Poly), "היררכיה מוכנה"
 * (trees / ownTree / treeFiles — כל מחלקה עוברת באותו check, כולל שמות של Unit4) ו"הוסף עצם" (objectCode).
 */
public final class ClassGen {
    private ClassGen() {}

    public static final String JAVA = "java", CS = "cs";

    /** הכותרת של החלון והכפתור בחלון "הוסף מבנה נתונים" */
    public static final String TITLE = "צור מחלקה";

    public static String subtitle(String lang) {
        return CS.equals(lang) ? "כותבים שם ושדות — והמחלקה נוצרת בקובץ חדש, עם בנאי ועם Get ו-Set לכל שדה."
                : "כותבים שם ושדות — והמחלקה נוצרת בקובץ חדש, עם בנאי ועם get ו-set לכל שדה.";
    }

    /** הכיתוב של תיבת הסימון toString (עם LRM אחרי הסוגריים — שלא יתהפכו בשורה מימין לשמאל) */
    public static String toStringLabel(String lang) {
        return "תיאור העצם כמחרוזת — " + (CS.equals(lang) ? "ToString()" : "toString()") + "\u200E";
    }

    /** שורת העזר על המקשים */
    public static final String KEYS = "מקשים: Enter בשדה האחרון — עוד שדה · Ctrl+Enter — יצירה · Esc — סגירה";

    /** שדה: טיפוס ושם (כמו שהמשתמש כתב). refs — מחלקות שנבחרו מהרשימה: שם ← החבילה / ה-namespace שלה */
    public static final class Field {
        public String type, name;
        public Map<String, String> refs = new LinkedHashMap<String, String>();

        public Field(String type, String name) {
            this.type = type == null ? "" : type;
            this.name = name == null ? "" : name;
        }
    }

    /** מחלקה שכבר קיימת בפרויקט: שם, package (Java) / namespace (C#) — "" אם אין, והקובץ (יחסית לשורש הסריקה, עם /) */
    public static final class Known {
        public final String name, pkg, file;
        /** מה שנקרא מהקוד (models): מחלקת העל ("" — אין), abstract, final / sealed, interface */
        public String sup = "";
        public boolean isAbstract, isFinal, isInterface;
        /** שדות של עצם (לא static) — כולל property של C# */
        public List<Field> fields = new ArrayList<Field>();
        /** הפרמטרים של הבנאי הרחב ביותר (לא private); null — אין בנאי בקוד (בנאי ברירת מחדל) */
        public List<Field> ctor;
        /** הפעולות שהמחלקה מגדירה (לא בנאים) */
        public List<Method> methods = new ArrayList<Method>();

        public Known(String name, String pkg, String file) {
            this.name = name;
            this.pkg = pkg == null ? "" : pkg;
            this.file = file == null ? "" : file;
        }
    }

    /**
     * פעולה. במחלקה חדשה: טיפוס מוחזר, שם ופרמטרים כמו שכתבו ("int steps, String s"), מופשטת או לא, וגוף (null — ברירת מחדל).
     * בפעולה שנקראה מהקוד: mods — המילים שלפני הטיפוס (public virtual ...)
     */
    public static final class Method {
        public String ret, name, params, mods = "", body;
        public boolean isAbstract;

        public Method(String ret, String name, String params) {
            this.ret = ret == null ? "" : ret;
            this.name = name == null ? "" : name;
            this.params = params == null ? "" : params;
        }
    }

    /** פריט ברשימת הטיפוסים: הטיפוס, הטקסט האפור לידו (package כששני שמות זהים), החבילה (למחלקות מהפרויקט, אחרת null) */
    public static final class Item {
        public final String type, hint, pkg;

        Item(String type, String hint, String pkg) {
            this.type = type;
            this.hint = hint;
            this.pkg = pkg;
        }
    }

    /** קבוצה ברשימת הטיפוסים: כותרת, הפריטים, ומה כתוב (באפור) כשאין פריטים */
    public static final class Group {
        public final String title, empty;
        public final List<Item> items = new ArrayList<Item>();

        Group(String title, String empty) {
            this.title = title;
            this.empty = empty;
        }
    }

    public static final class Options {
        public String lang = JAVA;
        public String className = "";
        public List<Field> fields = new ArrayList<Field>();
        public boolean constructor = true;
        public boolean toString = false;
        /** Java: החבילה של הקובץ הנוכחי ("" — בלי package) */
        public String pkg = "";
        /** C#: ה-namespace של הקובץ הנוכחי ("" — בלי namespace) */
        public String namespace = "";
        /** C#: namespace X; (בלי סוגריים) — כמו בקובץ הנוכחי */
        public boolean fileScoped;
        /** מחלקות / קבצים שכבר קיימים בתיקייה (שם ← הקובץ) — מחלקה חדשה בשם כזה נחסמת */
        public Map<String, String> taken = new LinkedHashMap<String, String>();
        /** כל המחלקות של הפרויקט (scanProject): לרשימת הטיפוסים, לחסימת שם כפול באותה חבילה, ול-import / using */
        public List<Known> known = new ArrayList<Known>();
        /** "יורשת מ-": שם מחלקת העל מהפרויקט ("" — בלי), והחבילה / ה-namespace שלה (null — לפי השם) */
        public String superName = "", superPkg;
        /** מחלקה מופשטת (abstract) */
        public boolean isAbstract;
        /** "פעולות לדריסה": המפתחות שנבחרו (say() / toString()) — מ-Check.overridable */
        public List<String> overrides = new ArrayList<String>();
        /** "פעולה חדשה" */
        public List<Method> methods = new ArrayList<Method>();
        /** גוף מוכן לדריסה (לפי שם הפעולה, באותיות קטנות) — להיררכיות המוכנות */
        public Map<String, String> overrideBodies = new LinkedHashMap<String, String>();
    }

    /** פעולה שאפשר לדרוס: המפתח (say(int)), הפעולה, המחלקה שהגדירה אותה, מופשטת (חובה לדרוס), וב-C# — צריך להוסיף לה virtual */
    public static final class Overridable {
        public final String key, from, file;
        public final Method method;
        public final boolean isAbstract, needsVirtual;

        Overridable(String key, Method m, String from, String file, boolean isAbstract, boolean needsVirtual) {
            this.key = key;
            this.method = m;
            this.from = from;
            this.file = file;
            this.isAbstract = isAbstract;
            this.needsVirtual = needsVirtual;
        }

        /** הכיתוב ברשימה: say() — מ-Dog */
        public String label() {
            return method.name + "(" + method.params + ")\u200E";
        }
    }

    /** שדות שהמחלקה יורשת — לפי המחלקה שהגדירה אותם (מחלקת העל העליונה קודם) */
    public static final class Inherited {
        public final String from;
        public final List<Field> fields = new ArrayList<Field>();

        Inherited(String from) {
            this.from = from;
        }
    }

    /** שינוי בקובץ של מחלקת העל (C#): להוסיף virtual לפעולה */
    public static final class Edit {
        public final String file, className, method, params;

        Edit(String file, String className, String method, String params) {
            this.file = file;
            this.className = className;
            this.method = method;
            this.params = params;
        }
    }

    /** תוצאת הבדיקה: שגיאות (חוסמות), אזהרות, שם המחלקה המתוקן והשדות התקינים (טיפוסים מתוקנים) */
    public static final class Check {
        public final List<String> errors = new ArrayList<String>(), warnings = new ArrayList<String>();
        public String className;
        public final List<Field> fields = new ArrayList<Field>();
        /** שורות ה-import / using של מחלקות מהפרויקט (ממוינות), והאם צריך את ספריית הקורס */
        public final List<String> imports = new ArrayList<String>();
        public boolean course;
        /** מחלקת העל (null — בלי) והשרשרת שלה מלמטה למעלה */
        public Known sup;
        public final List<Known> chain = new ArrayList<Known>();
        /** השדות שעוברים בירושה, הפרמטרים של הבנאי של מחלקת העל, הפעולות שאפשר לדרוס, ותיקוני virtual (C#) */
        public final List<Inherited> inherited = new ArrayList<Inherited>();
        public final List<Field> superParams = new ArrayList<Field>();
        public final List<Overridable> overridable = new ArrayList<Overridable>();
        public final List<Edit> edits = new ArrayList<Edit>();
        /** הפעולות החדשות התקינות (שם מתוקן) והדריסות שייווצרו (כולל מופשטות שחובה לדרוס) */
        public final List<Method> methods = new ArrayList<Method>();
        public final List<Overridable> overrides = new ArrayList<Overridable>();
        /** דריסה של toString (יש toString במחלקה של התלמיד בשרשרת — super.toString()) */
        public boolean superToString;

        public boolean ok() {
            return errors.isEmpty();
        }
    }

    private static final Set<String> JAVA_KEYWORDS = new HashSet<String>(Arrays.asList((
            "abstract assert boolean break byte case catch char class const continue default do double "
            + "else enum extends final finally float for goto if implements import instanceof int interface long native new "
            + "package private protected public return short static strictfp super switch synchronized this throw throws "
            + "transient try void volatile while true false null var record").split(" ")));

    private static final Set<String> CS_KEYWORDS = new HashSet<String>(Arrays.asList((
            "abstract as base bool break byte case catch char checked class const continue decimal default delegate do "
            + "double else enum event explicit extern false finally fixed float for foreach goto if implicit in int interface internal is lock long "
            + "namespace new null object operator out override params private protected public readonly ref return sbyte sealed short sizeof "
            + "stackalloc static string struct switch this throw true try typeof uint ulong unchecked unsafe ushort using virtual void volatile while").split(" ")));

    /** מילים שמורות שהן גם שמות של טיפוסים */
    private static final Set<String> JAVA_PRIMITIVES = new HashSet<String>(Arrays.asList("int", "double", "boolean", "char", "long", "float", "short", "byte"));
    private static final Set<String> CS_PRIMITIVES = new HashSet<String>(Arrays.asList("int", "double", "bool", "char", "string", "long", "float", "short",
            "byte", "decimal", "object", "uint", "ulong", "ushort", "sbyte"));

    /** המבנים של ספריית הקורס — שדה מהטיפוסים האלה צריך import unit4.collectionsLib.*; / using Unit4.CollectionsLib; */
    public static final List<String> COURSE = Arrays.asList("Queue", "Stack", "Node", "BinNode");
    /** שמות שאסור לתת למחלקה חדשה: המבנים של הקורס ומחלקות בסיסיות של השפה */
    private static final List<String> BUILTIN = Arrays.asList("Stack", "Queue", "Node", "BinNode", "String", "Object", "Math", "System",
            "Integer", "Double", "Character", "Boolean", "Console", "Scanner");
    /** מחלקות מוכנות בספריית הקורס — מותר (תרגיל נפוץ), אבל מזהירים */
    private static final List<String> LIBRARY_CLASSES = Arrays.asList("Student", "Car");

    private static final Pattern IDENT = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

    static boolean isCs(String lang) {
        return CS.equals(lang);
    }

    /** שם חוקי בשפה: אותיות באנגלית, ספרות ו-_, מתחיל באות, ולא מילה שמורה */
    public static boolean isIdentifier(String s, String lang) {
        return s != null && IDENT.matcher(s).matches() && !(isCs(lang) ? CS_KEYWORDS : JAVA_KEYWORDS).contains(s);
    }

    /** הטיפוס של שורה חדשה */
    public static String defaultType(String lang) {
        return "int";
    }

    /** הטיפוסים הבסיסיים (הקבוצה הראשונה ברשימה) */
    public static List<String> basicTypes(String lang) {
        return isCs(lang) ? Arrays.asList("int", "double", "bool", "char", "string") : Arrays.asList("int", "double", "boolean", "char", "String");
    }

    /** מה יכול להיות בתוך מבנה של הקורס (הרשימה הקטנה ליד Queue / Stack / Node / BinNode), לפני המחלקות של הפרויקט */
    public static List<String> elementTypes(String lang) {
        return isCs(lang) ? Arrays.asList("int", "double", "bool", "char", "string") : Arrays.asList("Integer", "Double", "Boolean", "Character", "String");
    }

    public static String defaultElement(String lang) {
        return isCs(lang) ? "int" : "Integer";
    }

    public static final String GROUP_BASIC = "סוגים בסיסיים", GROUP_COURSE = "מבני נתונים של הקורס",
            GROUP_CLASSES = "מחלקות שכבר קיימות בפרויקט", GROUP_ARRAYS = "מערכים", NO_CLASSES = "אין עדיין מחלקות אחרות בפרויקט";

    /** מבנה של הקורס שצריך טיפוס בפנים (Queue / Stack / Node / BinNode, בלי <>) */
    public static boolean isCourse(String base) {
        return base != null && COURSE.contains(base.trim());
    }

    /** הטיפוס המלא של שורה: Queue + Student ← Queue<Student>; כל דבר אחר — כמו שנכתב */
    public static String fullType(String base, String elem, String lang) {
        String b = base == null ? "" : base.trim(), e = elem == null ? "" : elem.trim();
        return isCourse(b) ? b + "<" + (e.isEmpty() ? defaultElement(lang) : e) + ">" : b;
    }

    /** המחלקות של הפרויקט כפריטים ברשימה (עם suffix — למערכים); ה-package באפור רק כשיש שתי מחלקות באותו שם */
    static List<Item> classItems(List<Known> known, String lang, String suffix) {
        List<Item> out = new ArrayList<Item>();
        for (Known k : known) {
            int same = 0;
            for (Known o : known) if (o.name.equals(k.name)) same++;
            String hint = same > 1 ? (k.pkg.isEmpty() ? (isCs(lang) ? "(בלי namespace)" : "(בלי package)") : k.pkg) : "";
            out.add(new Item(k.name + suffix, hint, k.pkg));
        }
        return out;
    }

    /** הרשימה של טיפוס השדה: סוגים בסיסיים, מבני הנתונים של הקורס, המחלקות של הפרויקט, מערכים */
    public static List<Group> typeGroups(String lang, List<Known> known) {
        List<Known> k = sorted(known);
        List<Group> out = new ArrayList<Group>();
        Group g = new Group(GROUP_BASIC, "");
        for (String t : basicTypes(lang)) g.items.add(new Item(t, "", null));
        out.add(g);
        g = new Group(GROUP_COURSE, "");
        for (String t : COURSE) g.items.add(new Item(t, "", null));
        out.add(g);
        g = new Group(GROUP_CLASSES, NO_CLASSES);
        g.items.addAll(classItems(k, lang, ""));
        out.add(g);
        g = new Group(GROUP_ARRAYS, "");
        for (String t : isCs(lang) ? Arrays.asList("int[]", "double[]", "string[]") : Arrays.asList("int[]", "double[]", "String[]")) g.items.add(new Item(t, "", null));
        g.items.addAll(classItems(k, lang, "[]"));
        out.add(g);
        return out;
    }

    /** הרשימה הקטנה (מה יש בתוך Queue / Stack / Node / BinNode): הטיפוסים של הקורס, והמחלקות של הפרויקט */
    public static List<Group> elementGroups(String lang, List<Known> known) {
        List<Group> out = new ArrayList<Group>();
        Group g = new Group(GROUP_BASIC, "");
        for (String t : elementTypes(lang)) g.items.add(new Item(t, "", null));
        out.add(g);
        g = new Group(GROUP_CLASSES, NO_CLASSES);
        g.items.addAll(classItems(sorted(known), lang, ""));
        out.add(g);
        return out;
    }

    public static List<Known> sorted(List<Known> known) {
        List<Known> k = new ArrayList<Known>(known == null ? Collections.<Known>emptyList() : known);
        Collections.sort(k, (a, b) -> a.name.equals(b.name) ? a.pkg.compareTo(b.pkg) : a.name.compareTo(b.name));
        return k;
    }

    static String cap(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    /** שם הקובץ של המחלקה: Student.java / Student.cs */
    public static String fileName(String className, String lang) {
        return className + (isCs(lang) ? ".cs" : ".java");
    }

    public static String getter(String field, String lang) {
        return (isCs(lang) ? "Get" : "get") + cap(field);
    }

    public static String setter(String field, String lang) {
        return (isCs(lang) ? "Set" : "set") + cap(field);
    }

    // ------------------------------------------------------------------ טיפוסים

    /** מנתח טיפוס ומחזיר אותו בכתיב אחיד (Queue<Integer>, int[], Map<String, Integer>) עם התיקונים של השפה; null — לא טיפוס */
    public static String normalizeType(String type, String lang) {
        if (type == null) return null;
        String s = type.trim();
        if (s.isEmpty()) return null;
        int[] pos = { 0 };
        String r = parseType(s, pos, lang, false);
        skip(s, pos);
        return r == null || pos[0] != s.length() ? null : r;
    }

    /** רווחים בתוך טיפוס (בדיוק אותם תווים כמו ב-classgen.js) */
    private static boolean ws(char c) {
        return c == ' ' || c == '\t' || c == '\n' || c == '\r' || c == '\f' || c == '\u000B';
    }

    private static void skip(String s, int[] pos) {
        while (pos[0] < s.length() && ws(s.charAt(pos[0]))) pos[0]++;
    }

    private static String parseType(String s, int[] pos, String lang, boolean arg) {
        boolean cs = isCs(lang);
        skip(s, pos);
        StringBuilder name = new StringBuilder();
        while (true) {
            Matcher m = IDENT.matcher(s);
            m.region(pos[0], s.length());
            if (!m.lookingAt()) return null;
            name.append(m.group());
            pos[0] = m.end();
            skip(s, pos);
            if (pos[0] < s.length() && s.charAt(pos[0]) == '.') {
                name.append('.');
                pos[0]++;
                skip(s, pos);
                continue;
            }
            break;
        }
        String n = name.toString();
        boolean dotted = n.indexOf('.') >= 0;
        if (!dotted) {
            n = mapName(n, lang, arg);
            Set<String> kw = cs ? CS_KEYWORDS : JAVA_KEYWORDS;
            Set<String> prim = cs ? CS_PRIMITIVES : JAVA_PRIMITIVES;
            if (kw.contains(n) && !prim.contains(n)) return null;   // void, class, if...
        }
        StringBuilder out = new StringBuilder(n);
        if (pos[0] < s.length() && s.charAt(pos[0]) == '<') {
            pos[0]++;
            List<String> args = new ArrayList<String>();
            while (true) {
                String a = parseType(s, pos, lang, true);
                if (a == null) return null;
                args.add(a);
                skip(s, pos);
                if (pos[0] < s.length() && s.charAt(pos[0]) == ',') { pos[0]++; continue; }
                if (pos[0] < s.length() && s.charAt(pos[0]) == '>') { pos[0]++; break; }
                return null;
            }
            out.append('<');
            for (int i = 0; i < args.size(); i++) out.append(i > 0 ? ", " : "").append(args.get(i));
            out.append('>');
            skip(s, pos);
        }
        if (cs && pos[0] < s.length() && s.charAt(pos[0]) == '?') {   // int? — ב-C# בלבד
            out.append('?');
            pos[0]++;
            skip(s, pos);
        }
        while (pos[0] < s.length() && s.charAt(pos[0]) == '[') {
            pos[0]++;
            skip(s, pos);
            StringBuilder dims = new StringBuilder("[");
            while (cs && pos[0] < s.length() && s.charAt(pos[0]) == ',') { dims.append(','); pos[0]++; skip(s, pos); }
            if (pos[0] >= s.length() || s.charAt(pos[0]) != ']') return null;
            pos[0]++;
            dims.append(']');
            out.append(dims);
            skip(s, pos);
        }
        return out.toString();
    }

    /** תיקון שמות שמתאימים לשפה האחרת: boolean ↔ bool, String ↔ string, ובתוך <> של Java — Integer במקום int */
    private static String mapName(String n, String lang, boolean arg) {
        if (isCs(lang)) {
            switch (n) {
                case "boolean": case "Boolean": return "bool";
                case "String": return "string";
                case "Integer": return "int";
                case "Double": return "double";
                case "Character": return "char";
                case "Long": return "long";
                case "Float": return "float";
                case "Short": return "short";
                case "Byte": return "byte";
                case "Object": return "object";
                default: return n;
            }
        }
        if (n.equals("string")) return "String";
        if (n.equals("bool")) n = "boolean";
        if (n.equals("object")) return "Object";
        if (!arg) return n;
        switch (n) {   // Queue<int> לא חוקי ב-Java
            case "int": return "Integer";
            case "double": return "Double";
            case "char": return "Character";
            case "boolean": return "Boolean";
            case "long": return "Long";
            case "float": return "Float";
            case "short": return "Short";
            case "byte": return "Byte";
            default: return n;
        }
    }

    /** השמות הפשוטים (בלי שם מלא עם נקודות) בטיפוס: Queue<Student>[] ← Queue, Student */
    static List<String> simpleNames(String type) {
        List<String> out = new ArrayList<String>();
        Matcher m = Pattern.compile("[A-Za-z_][\\w.]*").matcher(type);
        while (m.find()) if (m.group().indexOf('.') < 0 && !out.contains(m.group())) out.add(m.group());
        return out;
    }

    /** הטיפוס משתמש במבנה של ספריית הקורס (Stack / Queue / Node / BinNode — בלי שם מלא) */
    static boolean usesCourse(String type) {
        for (String n : simpleNames(type)) if (COURSE.contains(n)) return true;
        return false;
    }

    // ------------------------------------------------------------------ בדיקה

    public static Check check(Options o) {
        Check c = new Check();
        String lang = isCs(o.lang) ? CS : JAVA;
        String langName = isCs(lang) ? "C#" : "Java";
        checkName(o.className, o, c, langName);
        inheritance(o, c);
        Set<String> inheritedNames = new HashSet<String>();
        for (Inherited h : c.inherited) for (Field f : h.fields) inheritedNames.add(f.name);
        Map<String, String> names = new LinkedHashMap<String, String>();   // שם השדה ← שם השדה (לבדיקת כפילות)
        Map<String, String> accessors = new LinkedHashMap<String, String>();   // getName ← השדה
        int filled = 0;
        for (Field f : o.fields) {
            String name = f.name == null ? "" : f.name.trim(), type = f.type == null ? "" : f.type.trim();
            if (name.isEmpty()) continue;   // שורה בלי שם — לא נכנסת (למשל שורה חדשה שעוד לא מילאו)
            filled++;
            String err = null;
            if (!IDENT.matcher(name).matches()) err = "השדה \"" + name + "\": שם חייב להיות באנגלית, בלי רווחים, ולהתחיל באות";
            else if ((isCs(lang) ? CS_KEYWORDS : JAVA_KEYWORDS).contains(name)) err = "השדה \"" + name + "\": זו מילה שמורה של " + langName + " — בוחרים שם אחר";
            else if (names.containsKey(name)) err = "יש שני שדות בשם " + name;
            else if (c.className != null && name.equals(c.className)) err = "שדה לא יכול להיקרא כמו המחלקה (" + name + ")";
            else if (accessors.containsKey(getter(name, lang)))
                err = "השדות " + accessors.get(getter(name, lang)) + " ו-" + name + " יוצרים את אותה פעולה (" + getter(name, lang) + ") — משנים שם לאחד מהם";
            else if (inheritedNames.contains(name)) err = "השדה " + name + " כבר קיים במחלקה " + ownerOf(c, name) + " — המחלקה יורשת אותו. בוחרים שם אחר";
            else for (Field p : c.superParams) if (p.name.equals(name)) err = "השם " + name + " כבר משמש פרמטר בבנאי של " + c.sup.name + " — בוחרים שם אחר לשדה";
            String norm = null;
            if (err == null) {
                if (type.isEmpty()) err = "השדה " + name + ": חסר טיפוס";
                else if ((norm = normalizeType(type, lang)) == null)
                    err = "השדה " + name + ": הטיפוס \"" + type + "\" לא תקין (למשל " + (isCs(lang) ? "int, string, Queue<int>" : "int, String, Queue<Integer>") + ")";
            }
            if (err != null) {
                c.errors.add(err);
                continue;
            }
            names.put(name, name);
            accessors.put(getter(name, lang), name);
            Field ok = new Field(norm, name);
            if (f.refs != null) ok.refs.putAll(f.refs);
            c.fields.add(ok);
        }
        methods(o, c, accessors);
        boolean plain = (o.superName == null || o.superName.trim().isEmpty()) && c.methods.isEmpty() && !o.isAbstract;
        if (filled == 0 && plain) c.errors.add("מוסיפים לפחות שדה אחד (טיפוס ושם)");
        if (c.sup != null && !c.superParams.isEmpty() && !o.constructor)
            c.warnings.add("לבנאי של " + c.sup.name + " יש פרמטרים — לכן נוצר בנאי שמעביר אותם (" + (isCs(lang) ? "base(...)" : "super(...)") + ")");
        resolve(o, c);
        return c;
    }

    /** שמות שאסור לתת למחלקה חדשה כי הם בספריית הקורס (Unit4) — ב-C# הם מתנגשים ב-using Unit4.CollectionsLib; */
    public static final List<String> UNIT4 = Arrays.asList("StackInt", "BinTreeNode", "BinTreeUtils", "TreeCanvas", "Helpers", "VizHooks", "AnimationSpeed",
            "List", "Viz", "Theme", "Build", "LineByLine", "TraceViewer", "LineAgent");

    /** בדיקת שם למחלקה חדשה (גם לכל מחלקה בהיררכיה מוכנה / בעץ משלכם): c.className — השם המתוקן, או null */
    static void checkName(String rawName, Options o, Check c, String langName) {
        String lang = isCs(o.lang) ? CS : JAVA;
        String raw = rawName == null ? "" : rawName.trim();
        if (raw.isEmpty()) {
            c.errors.add("כותבים שם למחלקה");
        } else if (!IDENT.matcher(raw).matches()) {
            c.errors.add("שם המחלקה חייב להיות באנגלית, בלי רווחים, ולהתחיל באות");
        } else {
            String fixed = cap(raw);
            Set<String> kw = isCs(lang) ? CS_KEYWORDS : JAVA_KEYWORDS;
            if (kw.contains(raw) || kw.contains(fixed)) {
                c.errors.add("\"" + (kw.contains(raw) ? raw : fixed) + "\" היא מילה שמורה של " + langName + " — בוחרים שם אחר למחלקה");
            } else {
                c.className = fixed;
                if (!fixed.equals(raw)) c.warnings.add("שם של מחלקה מתחיל באות גדולה — המחלקה תיקרא " + fixed);
                if (BUILTIN.contains(fixed)) c.errors.add("\"" + fixed + "\" הוא שם של מחלקה מוכנה — בוחרים שם אחר למחלקה");
                else if (UNIT4.contains(fixed))
                    c.errors.add("\"" + fixed + "\" הוא שם של מחלקה בספריית הקורס (Unit4) — מחלקה בשם הזה תתנגש ב-"
                            + (isCs(lang) ? "using Unit4.CollectionsLib;" : "import unit4.collectionsLib.*;") + " בוחרים שם אחר למחלקה");
                else {
                    String where = null, found = null;
                    for (Map.Entry<String, String> t : o.taken.entrySet())
                        if (t.getKey().equalsIgnoreCase(fixed)) { found = t.getKey(); where = t.getValue(); break; }
                    String target = target(o);
                    if (found == null) for (Known k : o.known)
                        if (k.pkg.equals(target) && k.name.equalsIgnoreCase(fixed)) { found = k.name; where = k.file; break; }
                    if (found != null) c.errors.add("כבר יש מחלקה בשם " + found + (where != null && !where.isEmpty() ? " (בקובץ " + where + ")" : "") + " — בוחרים שם אחר");
                    else if (LIBRARY_CLASSES.contains(fixed))
                        c.warnings.add("בספריית הקורס כבר יש מחלקה " + fixed + " — בפרויקט הזה המחלקה החדשה תחליף אותה");
                }
            }
        }
    }

    /** המחלקה מהפרויקט בשם הזה: באותה חבילה של pkg אם יש, אחרת באותה חבילה של המחלקה החדשה, אחרת הראשונה */
    static Known findKnown(List<Known> known, String name, String pkg, String target) {
        Known first = null, here = null;
        for (Known k : known) {
            if (!k.name.equals(name)) continue;
            if (pkg != null && k.pkg.equals(pkg)) return k;
            if (first == null) first = k;
            if (here == null && k.pkg.equals(target)) here = k;
        }
        return here != null ? here : first;
    }

    /** השרשרת של מחלקה: היא, מחלקת העל שלה, וכן הלאה (רק מחלקות מהפרויקט; עוצרים במעגל) */
    public static List<Known> chainOf(List<Known> known, Known start) {
        List<Known> out = new ArrayList<Known>();
        Known k = start;
        while (k != null && !out.contains(k) && out.size() < 30) {
            out.add(k);
            k = k.sup.isEmpty() ? null : findKnown(known, k.sup, null, k.pkg);
        }
        return out;
    }

    /** מאיזו מחלקה בשרשרת בא השדה */
    private static String ownerOf(Check c, String field) {
        for (Inherited h : c.inherited) for (Field f : h.fields) if (f.name.equals(field)) return h.from;
        return "";
    }

    /** מפתח של פעולה: say(int,String) */
    public static String key(String name, List<Field> params) {
        StringBuilder sb = new StringBuilder(name).append('(');
        for (int i = 0; i < params.size(); i++) sb.append(i > 0 ? "," : "").append(params.get(i).type);
        return sb.append(')').toString();
    }

    static boolean hasWord(String mods, String w) {
        return (" " + mods + " ").contains(" " + w + " ");
    }

    /** "יורשת מ-": מחלקת העל, השדות שעוברים, הבנאי שלה, הפעולות שאפשר לדרוס, ומה שנבחר לדריסה */
    static void inheritance(Options o, Check c) {
        String sup = o.superName == null ? "" : o.superName.trim();
        if (sup.isEmpty()) return;
        boolean cs = isCs(o.lang);
        Known k = findKnown(o.known, sup, o.superPkg, target(o));
        if (k == null) {
            c.errors.add("המחלקה " + sup + " לא נמצאה בפרויקט — בוחרים מחלקה מהרשימה של \"יורשת מ-\"");
            return;
        }
        if (c.className != null && k.name.equals(c.className)) {
            c.errors.add("מחלקה לא יכולה לרשת מעצמה");
            return;
        }
        if (k.isInterface) {
            c.errors.add(k.name + " הוא ממשק (interface) — יורשים רק ממחלקה");
            return;
        }
        if (k.isFinal) {
            c.errors.add("המחלקה " + k.name + " סגורה (" + (cs ? "sealed" : "final") + ") — אי אפשר לרשת ממנה");
            return;
        }
        c.sup = k;
        c.chain.addAll(chainOf(o.known, k));
        for (int i = c.chain.size() - 1; i >= 0; i--) {
            Inherited h = new Inherited(c.chain.get(i).name);
            h.fields.addAll(c.chain.get(i).fields);
            c.inherited.add(h);
        }
        if (k.ctor != null) c.superParams.addAll(k.ctor);
        Set<String> seen = new HashSet<String>();
        String ts = cs ? "ToString" : "toString";
        for (Known x : c.chain)
            for (Method m : x.methods) {
                List<Field> ps = paramList(m.params);
                String key = key(m.name, ps);
                if (!seen.add(key) || hasWord(m.mods, "static") || hasWord(m.mods, "private")) continue;
                if (m.name.equals(ts) && ps.isEmpty()) {
                    c.superToString = true;
                    continue;
                }
                if (cs ? hasWord(m.mods, "sealed") : hasWord(m.mods, "final")) continue;
                boolean virt = m.isAbstract || hasWord(m.mods, "virtual") || hasWord(m.mods, "override");
                c.overridable.add(new Overridable(key, m, x.name, x.file, m.isAbstract, cs && !virt));
            }
        for (Overridable ov : c.overridable) {
            boolean picked = o.overrides.contains(ov.key) || o.overrides.contains(ov.method.name) || (ov.isAbstract && !o.isAbstract);
            if (!picked) continue;
            c.overrides.add(ov);
            if (ov.needsVirtual) {
                c.edits.add(new Edit(ov.file, ov.from, ov.method.name, ov.method.params));
                c.warnings.add("בקובץ " + (ov.file.isEmpty() ? ov.from + ".cs" : ov.file) + " תתווסף המילה virtual לפעולה " + ov.method.name
                        + "() של " + ov.from + " — ב-C# אפשר לדרוס רק פעולה virtual");
            }
        }
    }

    /** "פעולה חדשה": שם, טיפוס מוחזר ופרמטרים — בדיקה ותיקון */
    static void methods(Options o, Check c, Map<String, String> accessors) {
        boolean cs = isCs(o.lang);
        String lang = cs ? CS : JAVA, langName = cs ? "C#" : "Java";
        Set<String> keys = new HashSet<String>();
        Set<String> kw = cs ? CS_KEYWORDS : JAVA_KEYWORDS;
        for (Method m : o.methods) {
            String raw = m.name == null ? "" : m.name.trim();
            if (raw.isEmpty()) continue;
            String err = null, name = raw;
            if (!IDENT.matcher(raw).matches()) err = "הפעולה \"" + raw + "\": שם חייב להיות באנגלית, בלי רווחים, ולהתחיל באות";
            else {
                if (cs) name = cap(raw);
                if (kw.contains(name)) err = "הפעולה \"" + name + "\": זו מילה שמורה של " + langName + " — בוחרים שם אחר";
                else if (c.className != null && name.equals(c.className)) err = "פעולה לא יכולה להיקרא כמו המחלקה (" + name + ") — זה השם של הבנאי";
                else if (name.equalsIgnoreCase("toString")) err = "את " + (cs ? "ToString" : "toString") + " מסמנים בתיבת הסימון שלה";
                else if (accessors.containsKey(name)) err = "הפעולה " + name + " כבר נוצרת בשביל השדה " + accessors.get(name);
                else for (String a : accessors.keySet())
                    if (setter(accessors.get(a), lang).equals(name)) err = "הפעולה " + name + " כבר נוצרת בשביל השדה " + accessors.get(a);
            }
            String ret = m.ret == null ? "" : m.ret.trim(), nret = null;
            if (err == null) {
                if (ret.isEmpty()) err = "הפעולה " + name + ": חסר טיפוס מוחזר (void — לא מחזירה כלום)";
                else if (ret.equals("void")) nret = "void";
                else if ((nret = normalizeType(ret, lang)) == null) err = "הפעולה " + name + ": הטיפוס המוחזר \"" + ret + "\" לא תקין";
            }
            List<Field> ps = new ArrayList<Field>();
            if (err == null) {
                Set<String> pn = new HashSet<String>();
                for (String part : splitTop(m.params == null ? "" : m.params)) {
                    String t = part.trim();
                    if (t.isEmpty()) continue;
                    int sp = t.lastIndexOf(' ');
                    String pt = sp < 0 ? "" : t.substring(0, sp).trim(), pname = sp < 0 ? t : t.substring(sp + 1);
                    String npt = pt.isEmpty() ? null : normalizeType(pt, lang);
                    if (!IDENT.matcher(pname).matches() || kw.contains(pname) || npt == null) {
                        err = "הפעולה " + name + ": הפרמטר \"" + t + "\" לא תקין (כותבים טיפוס ושם, למשל " + (cs ? "int steps, string word" : "int steps, String word") + ")";
                        break;
                    }
                    if (!pn.add(pname)) {
                        err = "הפעולה " + name + ": יש שני פרמטרים בשם " + pname;
                        break;
                    }
                    ps.add(new Field(npt, pname));
                }
            }
            if (err == null) {
                String key = key(name, ps);
                for (Overridable ov : c.overridable)
                    if (ov.key.equals(key)) err = "הפעולה " + name + "() כבר קיימת ב-" + ov.from + " — מסמנים אותה ב\"פעולות לדריסה\"";
                if (err == null && !keys.add(key)) err = "יש שתי פעולות " + name + " עם אותם פרמטרים";
                if (err == null && m.isAbstract && !o.isAbstract) err = "הפעולה " + name + ": פעולה מופשטת (abstract) רק במחלקה מופשטת — מסמנים \"מחלקה מופשטת\"";
            }
            if (err != null) {
                c.errors.add(err);
                continue;
            }
            if (!name.equals(raw)) c.warnings.add("ב-C# שם של פעולה מתחיל באות גדולה — הפעולה תיקרא " + name);
            StringBuilder pt = new StringBuilder();
            for (Field f : ps) pt.append(pt.length() > 0 ? ", " : "").append(f.type).append(' ').append(f.name);
            Method ok = new Method(nret, name, pt.toString());
            ok.isAbstract = m.isAbstract;
            ok.body = m.body;
            c.methods.add(ok);
        }
    }

    /** מפצל רשימה בפסיקים שלא בתוך <> / () / [] */
    static List<String> splitTop(String list) {
        List<String> parts = new ArrayList<String>();
        int depth = 0;
        StringBuilder cur = new StringBuilder();
        for (int i = 0; i < list.length(); i++) {
            char ch = list.charAt(i);
            if (ch == '<' || ch == '(' || ch == '[') depth++;
            else if (ch == '>' || ch == ')' || ch == ']') depth--;
            if (ch == ',' && depth == 0) {
                parts.add(cur.toString());
                cur.setLength(0);
            } else cur.append(ch);
        }
        parts.add(cur.toString());
        return parts;
    }

    private static final Set<String> PARAM_MODS = new HashSet<String>(Arrays.asList("final", "ref", "out", "in", "params", "this", "readonly"));

    /** הפרמטרים של פעולה ("final int x, String s = \"a\"") — טיפוס ושם לכל אחד */
    public static List<Field> paramList(String params) {
        List<Field> out = new ArrayList<Field>();
        for (String part : splitTop(params == null ? "" : params)) {
            String t = part.replaceAll("@\\w+", " ");
            int eq = t.indexOf('=');
            if (eq >= 0) t = t.substring(0, eq);
            t = t.trim();
            if (t.isEmpty()) continue;
            Matcher m = Pattern.compile("([A-Za-z_]\\w*)\\s*$").matcher(t);
            if (!m.find()) continue;
            String type = t.substring(0, m.start()).trim();
            while (true) {
                int sp = type.indexOf(' ');
                if (sp > 0 && PARAM_MODS.contains(type.substring(0, sp))) type = type.substring(sp + 1).trim();
                else break;
            }
            if (type.isEmpty()) continue;
            out.add(new Field(tidy(type), m.group(1)));
        }
        return out;
    }

    /** טיפוס כמו שנכתב בקוד, בכתיב אחיד (רווח אחד אחרי פסיק, בלי רווחים סביב <>[]) — בלי להמיר בין השפות */
    static String tidy(String type) {
        return type.trim().replaceAll("\\s+", " ").replaceAll(" ?([<>\\[\\],?]) ?", "$1").replaceAll(",(?![\\],])", ", ");
    }

    /** ה-package (Java) / ה-namespace (C#) של המחלקה החדשה */
    static String target(Options o) {
        String t = isCs(o.lang) ? o.namespace : o.pkg;
        return t == null ? "" : t.trim();
    }

    /**
     * import / using לכל מחלקה מהפרויקט שבשימוש בשדות (וגם מחלקת העל והטיפוסים של פעולות חדשות) ונמצאת בחבילה אחרת (מה שנבחר
     * מהרשימה — refs; אחרת: באותה חבילה ← בלי, אחת בשם הזה ← היא, כמה ← הראשונה ואזהרה). Student / Car שאין בפרויקט — של ספריית הקורס
     */
    static void resolve(Options o, Check c) {
        boolean cs = isCs(o.lang);
        String target = target(o);
        List<String> lines = new ArrayList<String>();
        List<Field> uses = new ArrayList<Field>(c.fields);
        if (c.sup != null) {
            Field sf = new Field(c.sup.name, "");
            sf.refs.put(c.sup.name, c.sup.pkg);
            uses.add(sf);
        }
        for (Method m : c.methods) {
            uses.add(new Field(m.ret, ""));
            uses.addAll(paramList(m.params));
        }
        for (Field f : uses) {
            for (String n : simpleNames(f.type)) {
                if (COURSE.contains(n)) { c.course = true; continue; }
                List<Known> cand = new ArrayList<Known>();
                for (Known k : o.known) if (k.name.equals(n)) cand.add(k);
                if (cand.isEmpty()) {
                    if (LIBRARY_CLASSES.contains(n)) c.course = true;
                    continue;
                }
                String pkg = null;
                String ref = f.refs == null ? null : f.refs.get(n);
                if (ref != null) for (Known k : cand) if (k.pkg.equals(ref)) pkg = ref;
                if (pkg == null) {
                    boolean here = false;
                    for (Known k : cand) here |= k.pkg.equals(target);
                    if (here) continue;
                    pkg = cand.get(0).pkg;
                    if (cand.size() > 1) {
                        StringBuilder all = new StringBuilder();
                        for (Known k : cand) all.append(all.length() > 0 ? ", " : "").append(k.pkg.isEmpty() ? (cs ? "(בלי namespace)" : "(בלי package)") : k.pkg);
                        String w = "יש כמה מחלקות בשם " + n + " (" + all + ") — נבחרה הראשונה. אפשר לבחור אחרת מהרשימה";
                        if (!c.warnings.contains(w)) c.warnings.add(w);
                    }
                }
                if (pkg.equals(target)) continue;
                String line;
                if (cs) {
                    if (pkg.isEmpty() || target.startsWith(pkg + ".")) continue;   // namespace עוטף — רואים אותו בלי using
                    // Student / Car יש גם בספריית הקורס: using עם שם (alias) — כדי שלא יהיה דו-משמעי עם Unit4.CollectionsLib
                    line = LIBRARY_CLASSES.contains(n) ? "using " + n + " = " + pkg + "." + n + ";" : "using " + pkg + ";";
                } else {
                    if (pkg.isEmpty()) {
                        String w = "המחלקה " + n + " בלי package — אי אפשר להשתמש בה מתוך package " + target;
                        if (!c.warnings.contains(w)) c.warnings.add(w);
                        continue;
                    }
                    line = "import " + pkg + "." + n + ";";
                }
                if (!lines.contains(line)) lines.add(line);
            }
        }
        Collections.sort(lines);
        c.imports.addAll(lines);
    }

    // ------------------------------------------------------------------ הקוד

    /** הקוד של הקובץ. שגיאה בבדיקה — IllegalArgumentException עם ההודעה הראשונה */
    public static String generate(Options o) {
        Check c = check(o);
        if (!c.ok()) throw new IllegalArgumentException(c.errors.get(0));
        return code(o, c.className, c);
    }

    /** תצוגה מקדימה תוך כדי הקלדה: רק השדות התקינים, ובלי שם תקין — MyClass */
    public static String preview(Options o) {
        Check c = check(o);
        return code(o, c.className != null ? c.className : "MyClass", c);
    }

    static String code(Options o, String name, Check c) {
        boolean cs = isCs(o.lang);
        List<Field> fields = c.fields;
        StringBuilder sb = new StringBuilder();
        String in;   // ההזחה של גוף המחלקה
        String head = "public " + (o.isAbstract ? "abstract " : "") + "class " + name + (c.sup == null ? "" : (cs ? " : " : " extends ") + c.sup.name);
        if (cs) {
            if (c.course) sb.append("using Unit4.CollectionsLib;\n");
            for (String i : c.imports) sb.append(i).append('\n');
            if (c.course || !c.imports.isEmpty()) sb.append('\n');
            String ns = o.namespace == null ? "" : o.namespace.trim();
            String base = "";
            if (!ns.isEmpty() && o.fileScoped) sb.append("namespace ").append(ns).append(";\n\n");
            else if (!ns.isEmpty()) {
                sb.append("namespace ").append(ns).append("\n{\n");
                base = "    ";
            }
            in = base + "    ";
            sb.append(base).append(head).append('\n').append(base).append("{\n");
            body(sb, name, fields, o, c, in, true);
            sb.append(base).append("}\n");
            if (!base.isEmpty()) sb.append("}\n");
        } else {
            String pkg = o.pkg == null ? "" : o.pkg.trim();
            if (!pkg.isEmpty()) sb.append("package ").append(pkg).append(";\n\n");
            if (c.course) sb.append("import unit4.collectionsLib.*;\n");
            for (String i : c.imports) sb.append(i).append('\n');
            if (c.course || !c.imports.isEmpty()) sb.append('\n');
            in = "    ";
            sb.append(head).append(" {\n");
            body(sb, name, fields, o, c, in, false);
            sb.append("}\n");
        }
        return sb.toString();
    }

    /** פתיחת בלוק: ב-Java בסוף השורה, ב-C# בשורה משלו (כמו ש-Visual Studio מסדר) */
    private static void open(StringBuilder sb, String head, String in, boolean cs) {
        if (cs) sb.append(in).append(head).append('\n').append(in).append("{\n");
        else sb.append(in).append(head).append(" {\n");
    }

    /** ערך ברירת מחדל שפעולה מחזירה (String — שם המחלקה, כדי שבהרצה רואים מי ענה) */
    public static String defaultValue(String type, String cls) {
        String t = type == null ? "" : type.trim();
        switch (t) {
            case "int": case "long": case "short": case "byte": case "float": case "double": case "decimal": return "0";
            case "boolean": case "bool": return "false";
            case "char": return "' '";
            case "String": case "string": return "\"" + cls + "\"";
            default: return "null";
        }
    }

    /** השמות של הפרמטרים, מופרדים בפסיקים (לקריאה ל-super / base) */
    static String args(List<Field> params) {
        StringBuilder sb = new StringBuilder();
        for (Field f : params) sb.append(sb.length() > 0 ? ", " : "").append(f.name);
        return sb.toString();
    }

    /** שורה ריקה בין חברים — אבל לא מיד אחרי ה-{ של המחלקה */
    private static void gap(StringBuilder sb) {
        if (sb.length() < 2 || sb.charAt(sb.length() - 2) != '{') sb.append('\n');
    }

    private static void lines(StringBuilder sb, String body, String in2) {
        for (String l : body.split("\n", -1)) sb.append(l.isEmpty() ? "" : in2 + l).append('\n');
    }

    private static void body(StringBuilder sb, String name, List<Field> fields, Options o, Check c, String in, boolean cs) {
        String lang = cs ? CS : JAVA, in2 = in + "    ";
        for (Field f : fields) sb.append(in).append("private ").append(f.type).append(' ').append(f.name).append(";\n");
        List<Field> sp = c.superParams;
        if ((o.constructor || !sp.isEmpty()) && (!fields.isEmpty() || !sp.isEmpty())) {
            StringBuilder params = new StringBuilder();
            for (Field f : sp) params.append(params.length() > 0 ? ", " : "").append(f.type).append(' ').append(f.name);
            for (Field f : fields) params.append(params.length() > 0 ? ", " : "").append(f.type).append(' ').append(f.name);
            if (!fields.isEmpty()) sb.append('\n');
            open(sb, "public " + name + "(" + params + ")" + (cs && !sp.isEmpty() ? " : base(" + args(sp) + ")" : ""), in, cs);
            if (!cs && !sp.isEmpty()) sb.append(in2).append("super(").append(args(sp)).append(");\n");
            for (Field f : fields) sb.append(in2).append("this.").append(f.name).append(" = ").append(f.name).append(";\n");
            sb.append(in).append("}\n");
        }
        for (Field f : fields) {
            sb.append('\n');
            open(sb, "public " + f.type + " " + getter(f.name, lang) + "()", in, cs);
            sb.append(in2).append("return this.").append(f.name).append(";\n");
            sb.append(in).append("}\n\n");
            open(sb, "public void " + setter(f.name, lang) + "(" + f.type + " " + f.name + ")", in, cs);
            sb.append(in2).append("this.").append(f.name).append(" = ").append(f.name).append(";\n");
            sb.append(in).append("}\n");
        }
        // פעולות חדשות: ב-C# virtual — כדי שמחלקה יורשת תוכל לדרוס אותן
        for (Method m : c.methods) {
            gap(sb);
            if (m.isAbstract) {
                sb.append(in).append("public abstract ").append(m.ret).append(' ').append(m.name).append('(').append(m.params).append(");\n");
                continue;
            }
            open(sb, "public " + (cs ? "virtual " : "") + m.ret + " " + m.name + "(" + m.params + ")", in, cs);
            if (m.body != null) lines(sb, m.body, in2);
            else if (!m.ret.equals("void")) sb.append(in2).append("return ").append(defaultValue(m.ret, name)).append(";\n");
            sb.append(in).append("}\n");
        }
        // דריסות: אותה חתימה כמו במחלקת העל; גוף — מה שכתוב בהיררכיה המוכנה, או קריאה ל-super / base, או ברירת מחדל (מופשטת)
        for (Overridable ov : c.overrides) {
            Method m = ov.method;
            List<Field> ps = paramList(m.params);
            StringBuilder pt = new StringBuilder();
            for (Field f : ps) pt.append(pt.length() > 0 ? ", " : "").append(f.type).append(' ').append(f.name);
            String access = hasWord(m.mods, "protected") ? "protected" : "public";
            String ret = tidy(m.ret);
            gap(sb);
            if (!cs) sb.append(in).append("@Override\n");
            open(sb, access + " " + (cs ? "override " : "") + ret + " " + m.name + "(" + pt + ")", in, cs);
            String given = o.overrideBodies.get(m.name.toLowerCase(java.util.Locale.ROOT));
            if (given != null) lines(sb, given, in2);
            else if (ov.isAbstract) {
                if (!ret.equals("void")) sb.append(in2).append("return ").append(defaultValue(ret, name)).append(";\n");
            } else
                sb.append(in2).append(ret.equals("void") ? "" : "return ").append(cs ? "base." : "super.").append(m.name).append('(').append(args(ps)).append(");\n");
            sb.append(in).append("}\n");
        }
        if (o.toString) {
            gap(sb);
            if (!cs && c.sup != null) sb.append(in).append("@Override\n");
            open(sb, cs ? "public override string ToString()" : "public String toString()", in, cs);
            String start = c.superToString ? (cs ? "base.ToString()" : "super.toString()") : null;
            if (fields.isEmpty()) sb.append(in2).append("return ").append(start != null ? start : "\"" + name + "\"").append(";\n");
            else {
                List<String> parts = new ArrayList<String>();
                if (start != null) parts.add(start);
                for (int i = 0; i < fields.size(); i++) {
                    Field f = fields.get(i);
                    parts.add("\"" + (i == 0 && start == null ? name + ": " : ", ") + f.name + "=\" + " + show(f, cs));
                }
                if (parts.size() <= 3) sb.append(in2).append("return ").append(String.join(" + ", parts)).append(";\n");
                else {
                    sb.append(in2).append("return ").append(parts.get(0)).append('\n');
                    for (int i = 1; i < parts.size(); i++)
                        sb.append(in2).append("        + ").append(parts.get(i)).append(i == parts.size() - 1 ? ";\n" : "\n");
                }
            }
            sb.append(in).append("}\n");
        }
    }

    /** איך שדה נראה בתוך toString: מערך — עם הערכים שבו (Arrays.toString / string.Join), אחר — כמו שהוא */
    private static String show(Field f, boolean cs) {
        String t = f.type, v = "this." + f.name;
        if (!t.endsWith("]")) return v;
        int rank = t.length() - t.replace("[", "").length();
        // string.Join<T> (ולא string.Join): במערך של מחלקות (Car[]) הקריאה בלי <T> דו-משמעית (CS0121)
        if (cs) return rank == 1 && t.endsWith("[]")
                ? "(" + v + " == null ? \"null\" : \"[\" + string.Join<" + t.substring(0, t.length() - 2) + ">(\", \", " + v + ") + \"]\")" : v;
        return (rank == 1 ? "java.util.Arrays.toString(" : "java.util.Arrays.deepToString(") + v + ")";
    }

    // ------------------------------------------------------------------ עזר: מה כבר יש בקובץ / בתיקייה

    /** הקוד בלי הערות ומחרוזות (כדי לא למצוא "class X" בתוך הערה) */
    static String strip(String text) {
        return text.replaceAll("(?s)/\\*.*?\\*/", " ").replaceAll("//[^\\n]*", " ")
                .replaceAll("\"(?:[^\"\\\\\\n]|\\\\.)*\"", "\"\"").replaceAll("'(?:[^'\\\\\\n]|\\\\.)*'", "''");
    }

    /** ה-package של קובץ Java ("" — בלי) */
    public static String packageOf(String javaSource) {
        Matcher m = Pattern.compile("(?m)^\\s*package\\s+([\\w.]+)\\s*;").matcher(strip(javaSource == null ? "" : javaSource));
        return m.find() ? m.group(1) : "";
    }

    /** ה-namespace של קובץ C#: { שם, "file" / "block" }, או null — בלי namespace */
    public static String[] namespaceOf(String csSource) {
        Matcher m = Pattern.compile("(?m)^\\s*namespace\\s+([\\w.]+)\\s*(;)?").matcher(strip(csSource == null ? "" : csSource));
        return m.find() ? new String[] { m.group(1), m.group(2) != null ? "file" : "block" } : null;
    }

    /** שמות המחלקות (וגם interface / enum / record / struct) שמוגדרות בקובץ — רק ברמה העליונה (לא מחלקה בתוך מחלקה) */
    public static List<String> declaredTypes(String source) {
        List<String> out = new ArrayList<String>();
        for (String[] t : topTypes(source)) if (!out.contains(t[0])) out.add(t[0]);
        return out;
    }

    /**
     * המחלקות ברמה העליונה של קובץ, כל אחת { שם, package / namespace }. Java: ה-package של הקובץ; C#: ה-namespace שעוטף
     * (גם namespace X; וגם namespace X { } מקוננים). מחלקה בתוך מחלקה / פעולה — לא נספרת
     */
    public static List<String[]> topTypes(String source) {
        String text = strip(source == null ? "" : source);
        String pkg = packageOf(source);
        List<String[]> out = new ArrayList<String[]>();
        List<String> stack = new ArrayList<String>();   // לכל { פתוח: שם ה-namespace, או null — בלוק אחר
        String fileNs = "";
        Matcher m = Pattern.compile("\\bnamespace\\s+([\\w.]+)\\s*([;{])|\\b(?:class|interface|enum|record|struct)\\s+([A-Za-z_]\\w*)|[{}]").matcher(text);
        while (m.find()) {
            if (m.group(1) != null) {
                if (m.group(2).equals(";")) fileNs = m.group(1);
                else stack.add(m.group(1));
            } else if (m.group(3) != null) {
                boolean top = true;
                StringBuilder ns = new StringBuilder(fileNs);
                for (String e : stack) {
                    if (e == null) { top = false; break; }
                    ns.append(ns.length() > 0 ? "." : "").append(e);
                }
                if (top) out.add(new String[] { m.group(3), pkg.isEmpty() ? ns.toString() : pkg });
            } else if (m.group().equals("{")) stack.add(null);
            else if (!stack.isEmpty()) stack.remove(stack.size() - 1);
        }
        return out;
    }

    /**
     * המחלקות ברמה העליונה של קובץ, עם מה שצריך בשביל הורשה: מחלקת העל, abstract / final, השדות, הבנאי הרחב והפעולות.
     * ניתוח פשוט של הקוד (בלי הערות ומחרוזות) — מספיק לקוד של תלמידים ולמה שהמחולל יוצר. file — השם שנשמר ב-Known
     */
    public static List<Known> models(String source, String file) {
        String text = strip(source == null ? "" : source);
        String pkg = packageOf(source);
        List<Known> out = new ArrayList<Known>();
        List<String> stack = new ArrayList<String>();
        String fileNs = "";
        Matcher m = Pattern.compile("\\bnamespace\\s+([\\w.]+)\\s*([;{])|\\b(class|interface|enum|record|struct)\\s+([A-Za-z_]\\w*)|[{}]").matcher(text);
        while (m.find()) {
            if (m.group(1) != null) {
                if (m.group(2).equals(";")) fileNs = m.group(1);
                else stack.add(m.group(1));
            } else if (m.group(4) != null) {
                boolean top = true;
                StringBuilder ns = new StringBuilder(fileNs);
                for (String e : stack) {
                    if (e == null) { top = false; break; }
                    ns.append(ns.length() > 0 ? "." : "").append(e);
                }
                if (!top) continue;
                Known k = new Known(m.group(4), pkg.isEmpty() ? ns.toString() : pkg, file);
                out.add(k);
                String kind = m.group(3);
                k.isInterface = kind.equals("interface");
                int ms = m.start();
                while (ms > 0 && ";{}".indexOf(text.charAt(ms - 1)) < 0) ms--;
                String mn = text.substring(ms, m.start()).replaceAll("\\[[^\\]]*\\]", " ").replaceAll("@\\w+", " ").trim().replaceAll("\\s+", " ");
                k.isAbstract = hasWord(mn, "abstract");
                k.isFinal = hasWord(mn, "final") || hasWord(mn, "sealed") || hasWord(mn, "static") || kind.equals("enum") || kind.equals("struct") || kind.equals("record");
                int open = text.indexOf('{', m.end()), semi = text.indexOf(';', m.end());
                if (open < 0 || (semi >= 0 && semi < open)) continue;
                String head = text.substring(m.end(), open);
                Matcher ex = Pattern.compile("\\bextends\\s+([A-Za-z_][\\w.]*)").matcher(head);
                Matcher co = Pattern.compile("^\\s*(?:<[^>]*>)?\\s*:\\s*([A-Za-z_][\\w.]*)").matcher(head);
                String sup = "";
                boolean colon = false;
                if (ex.find()) sup = ex.group(1);
                else if (co.find()) {
                    sup = co.group(1);
                    colon = true;
                }
                if (sup.indexOf('.') >= 0) sup = sup.substring(sup.lastIndexOf('.') + 1);
                if (k.isInterface || sup.equals("Object") || sup.equals("object") || (colon && sup.matches("I[A-Z]\\w*"))) sup = "";
                k.sup = sup;
                int close = matching(text, open);
                members(k, text.substring(open + 1, close));
            } else if (m.group().equals("{")) stack.add(null);
            else if (!stack.isEmpty()) stack.remove(stack.size() - 1);
        }
        return out;
    }

    /** הסוגר שסוגר את { שבמקום open (או סוף הטקסט) */
    static int matching(String text, int open) {
        int depth = 0;
        for (int i = open; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (ch == '{') depth++;
            else if (ch == '}' && --depth == 0) return i;
        }
        return text.length();
    }

    private static final Set<String> MEMBER_MODS = new HashSet<String>(Arrays.asList(("public private protected internal static abstract final virtual override "
            + "sealed synchronized native new extern async unsafe default strictfp partial readonly transient volatile const").split(" ")));

    /** החברים של מחלקה: עוברים על הגוף ברמה העליונה — כל ; או בלוק { } הוא חבר אחד (שדה, בנאי, פעולה, property) */
    private static void members(Known k, String body) {
        int seg = 0, paren = 0;
        for (int i = 0; i < body.length(); i++) {
            char ch = body.charAt(i);
            if (ch == '(') paren++;
            else if (ch == ')') paren = Math.max(0, paren - 1);
            else if (paren == 0 && ch == ';') {
                member(k, body.substring(seg, i), false);
                seg = i + 1;
            } else if (paren == 0 && ch == '{') {
                String head = body.substring(seg, i);
                int end = matching(body, i);
                if (topEquals(head)) {   // int[] a = { 1, 2 }; / new X() { ... } — עדיין אותו שדה
                    i = end;
                    continue;
                }
                member(k, head, true);
                i = end;
                seg = i + 1;
            }
        }
    }

    /** יש = מחוץ לסוגריים (אתחול של שדה), ולא => */
    private static boolean topEquals(String head) {
        return topEq(head) >= 0;
    }

    /** המקום של ה-= הראשון מחוץ לסוגריים (לא =>, == או <= / >= / !=), או -1 */
    private static int topEq(String head) {
        int paren = 0;
        for (int i = 0; i < head.length(); i++) {
            char ch = head.charAt(i);
            if (ch == '(') paren++;
            else if (ch == ')') paren--;
            else if (ch == '=' && paren == 0 && (i + 1 >= head.length() || (head.charAt(i + 1) != '>' && head.charAt(i + 1) != '='))
                    && (i == 0 || "=<>!".indexOf(head.charAt(i - 1)) < 0)) return i;
        }
        return -1;
    }

    private static void member(Known k, String raw, boolean hasBody) {
        String h = raw.replaceAll("@\\w+(?:\\s*\\([^)]*\\))?", " ").replaceAll("^\\s*(?:\\[[^\\]]*\\]\\s*)+", " ").trim().replaceAll("\\s+", " ");
        if (h.isEmpty() || Pattern.compile("\\b(?:class|interface|enum|record|struct|delegate|event|operator)\\b").matcher(h).find()) return;
        int arrow = h.indexOf("=>");
        if (arrow >= 0) {
            h = h.substring(0, arrow).trim();
            hasBody = true;
        }
        List<String> mods = new ArrayList<String>();
        while (true) {
            int sp = h.indexOf(' ');
            String w = sp < 0 ? h : h.substring(0, sp);
            if (!MEMBER_MODS.contains(w)) break;
            mods.add(w);
            h = sp < 0 ? "" : h.substring(sp + 1);
        }
        String modText = String.join(" ", mods);
        int p = h.indexOf('('), eq0 = topEq(h);
        if (p >= 0 && (eq0 < 0 || p < eq0)) {   // לא int x = f(); — שם הסוגריים אחרי ה-=
            int depth = 0, close = -1;
            for (int i = p; i < h.length(); i++) {
                if (h.charAt(i) == '(') depth++;
                else if (h.charAt(i) == ')' && --depth == 0) { close = i; break; }
            }
            if (close < 0) return;
            String before = h.substring(0, p).trim(), params = h.substring(p + 1, close).trim();
            Matcher nm = Pattern.compile("([A-Za-z_]\\w*)$").matcher(before);
            if (!nm.find()) return;
            String name = nm.group(1), ret = before.substring(0, nm.start()).trim();
            if (ret.startsWith("<")) ret = ret.substring(ret.indexOf('>') + 1).trim();   // <T> void f()
            if (ret.isEmpty()) {
                if (!name.equals(k.name) || mods.contains("private") || mods.contains("static")) return;
                List<Field> ps = paramList(params);
                if (k.ctor == null || ps.size() > k.ctor.size()) k.ctor = ps;
                return;
            }
            Method me = new Method(tidy(ret), name, tidyParams(params));
            me.mods = modText;
            me.isAbstract = mods.contains("abstract") || (k.isInterface && !hasBody && !mods.contains("static") && !mods.contains("default"));
            k.methods.add(me);
            return;
        }
        if (mods.contains("static") || mods.contains("const")) return;
        List<String> parts = splitTop(h);   // int a = 1, b = 2
        String part0 = parts.get(0);
        if (part0.indexOf('=') >= 0) part0 = part0.substring(0, part0.indexOf('='));
        Matcher first = Pattern.compile("^(.*?)\\s*([A-Za-z_]\\w*)\\s*((?:\\[\\s*\\])*)$").matcher(part0.trim());
        if (!first.find() || first.group(1).trim().isEmpty()) return;
        String type = tidy(first.group(1) + first.group(3));
        k.fields.add(new Field(type, first.group(2)));
        if (hasBody) return;   // property
        for (int i = 1; i < parts.size(); i++) {
            String nmore = parts.get(i).trim();
            int e2 = nmore.indexOf('=');
            if (e2 >= 0) nmore = nmore.substring(0, e2).trim();
            if (IDENT.matcher(nmore).matches()) k.fields.add(new Field(tidy(first.group(1)), nmore));
        }
    }

    /** פרמטרים בכתיב אחיד: "int x, String s" */
    static String tidyParams(String params) {
        StringBuilder sb = new StringBuilder();
        for (Field f : paramList(params)) sb.append(sb.length() > 0 ? ", " : "").append(f.type).append(' ').append(f.name);
        return sb.toString();
    }

    /**
     * ב-C#: מוסיף virtual לפעולה במחלקה className (הפעולה שנבחרה לדריסה ולא סומנה virtual). מחזיר את הקוד החדש, או את אותו
     * קוד אם לא נמצאה (או שכבר virtual)
     */
    public static String addVirtual(String source, String className, String method, String params) {
        Matcher cls = Pattern.compile("\\bclass\\s+" + Pattern.quote(className) + "\\b").matcher(source);
        int from = cls.find() ? cls.end() : 0;
        int n = paramList(params).size();
        Matcher m = Pattern.compile("(?m)^([ \\t]*)((?:(?:public|protected|internal|new|virtual|override|static|abstract|sealed)\\s+)+)([A-Za-z_][\\w<>\\[\\], .?]*?\\s+" + Pattern.quote(method) + "\\s*\\(([^)]*)\\))")
                .matcher(source);
        while (m.find(from)) {
            String mods = " " + m.group(2).trim().replaceAll("\\s+", " ") + " ";
            if (paramList(m.group(4)).size() == n && !mods.contains(" virtual ") && !mods.contains(" override ") && !mods.contains(" static ") && !mods.contains(" abstract ")) {
                return source.substring(0, m.start(3)) + "virtual " + source.substring(m.start(3));
            }
            from = m.end();
        }
        return source;
    }

    // ------------------------------------------------------------------ צבעים — כמו בהרצה הוויזואלית (Poly)

    /** הצבעים של המחלקות בעצים (אותם צבעים ואותו סדר כמו Poly בהרצה): קו, רקע */
    public static final int[] INK = { 0xC0392B, 0x7B3FB0, 0x0C8583, 0x1F6FB5, 0x1F8A4C, 0xC56A12, 0xB02E78 };
    public static final int[] BG = { 0xFDEEED, 0xF4ECFB, 0xE4F6F5, 0xEAF2FB, 0xE8F6EE, 0xFDF1E4, 0xFBEBF4 };
    /** מחלקה שלא בעץ הורשה — אפור, כמו בתרשים המחלקות */
    public static final int GRAY = 0x55607A, GRAY_BG = 0xFAFBFD;

    /**
     * המספר של הצבע של כל מחלקה (-1 — לא בעץ הורשה), בסדר של ההרצה: המחלקות מוגדרות לפי הסדר ברשימה (ולפני כל מחלקה — מחלקות
     * העל שלה, כמו שהטעינה ב-Java מגדירה אותן), ואז השורשים קודם, אחר כך הילדים שלהם, שכבה אחר שכבה
     */
    public static Map<String, Integer> colors(List<Known> known) {
        List<Known> order = new ArrayList<Known>();
        for (Known k : known) define(known, k, order, new ArrayList<Known>());
        // כמו Poly.recolor: אחרי כל מחלקה שמוגדרת — שורשים ואז ילדים, וצבע שכבר ניתן לא משתנה (מחלקה חדשה מקבלת את הצבע הבא)
        Map<String, Integer> out = new LinkedHashMap<String, Integer>();
        List<Known> defined = new ArrayList<Known>();
        int next = 0;
        for (Known d : order) {
            defined.add(d);
            List<Known> queue = new ArrayList<Known>();
            for (Known c : defined) if (!c.isInterface && inTree(defined, c) && (c.sup.isEmpty() || byName(defined, c.sup) == null)) queue.add(c);
            for (int i = 0; i < queue.size(); i++)
                for (Known k : defined) if (queue.get(i).name.equals(k.sup) && !queue.contains(k) && !k.isInterface) queue.add(k);
            for (Known c : queue) if (!out.containsKey(c.name)) out.put(c.name, next++);
        }
        Map<String, Integer> all = new LinkedHashMap<String, Integer>();
        for (Known k : order) all.put(k.name, out.containsKey(k.name) ? out.get(k.name) : -1);
        return all;
    }

    private static void define(List<Known> known, Known k, List<Known> order, List<Known> visiting) {
        if (k == null || byName(order, k.name) != null || visiting.contains(k)) return;
        visiting.add(k);
        if (!k.sup.isEmpty()) define(known, byName(known, k.sup), order, visiting);
        if (byName(order, k.name) == null) order.add(k);
    }

    private static Known byName(List<Known> list, String name) {
        for (Known k : list) if (k.name.equals(name)) return k;
        return null;
    }

    private static boolean inTree(List<Known> all, Known c) {
        if (!c.sup.isEmpty() && byName(all, c.sup) != null) return true;
        for (Known k : all) if (c.name.equals(k.sup) && !k.isInterface) return true;
        return false;
    }

    /** צבע הקו (RGB) למספר צבע (-1 — אפור) */
    public static int ink(int color) {
        return color < 0 ? GRAY : INK[color % INK.length];
    }

    public static int bg(int color) {
        return color < 0 ? GRAY_BG : BG[color % BG.length];
    }

    /** הצבעים חוזרים אחרי 7: מסגרת מקווקוות (כמו בהרצה) */
    public static boolean dashed(int color) {
        return color >= INK.length;
    }

    /** "יורשת מ-": המחלקות של הפרויקט שאפשר לרשת מהן (בלי ממשקים / final / sealed), לפי השם */
    public static List<Known> superCandidates(List<Known> known) {
        List<Known> out = new ArrayList<Known>();
        for (Known k : sorted(known)) if (!k.isInterface && !k.isFinal) out.add(k);
        return out;
    }

    /** הצבעים אחרי שהמחלקה החדשה תירש מ-sup (כדי שהשדות שעוברים בירושה יוצגו בצבע שיהיה להם בהרצה) */
    public static Map<String, Integer> colorsWith(List<Known> known, String name, Known sup) {
        List<Known> all = new ArrayList<Known>(known);
        if (sup != null && name != null) {
            Known me = new Known(name, sup.pkg, "");
            me.sup = sup.name;
            all.add(me);
        }
        return colors(sorted(all));
    }

    private static final List<String> SKIP_DIRS = Arrays.asList("bin", "obj", "out", "build", "target", "node_modules");

    /**
     * כל המחלקות בפרויקט: קבצי ‎.java‎ / ‎.cs‎ מתחת ל-root (בלי bin, obj, out, build, target ותיקיות נסתרות; עד 2000 קבצים),
     * ממוינות לפי שם ואז package. file — יחסית ל-root, עם /
     */
    public static List<Known> scanProject(File root, String lang) {
        List<File> files = new ArrayList<File>();
        walk(root, isCs(lang) ? ".cs" : ".java", 8, files);
        List<Known> out = new ArrayList<Known>();
        Set<String> seen = new HashSet<String>();
        for (File f : files) {
            String text;
            try {
                text = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
            } catch (Exception e) {
                continue;
            }
            String rel = root.toURI().relativize(f.toURI()).getPath();
            for (Known k : models(text, rel)) if (seen.add(k.name + "|" + k.pkg)) out.add(k);
        }
        return sorted(out);
    }

    private static void walk(File dir, String ext, int depth, List<File> out) {
        if (depth < 0 || out.size() >= 2000) return;
        File[] list = dir.listFiles();
        if (list == null) return;
        Arrays.sort(list, (a, b) -> a.getName().compareTo(b.getName()));
        for (File f : list) {
            if (out.size() >= 2000) return;
            String n = f.getName();
            if (n.startsWith(".") || SKIP_DIRS.contains(n)) continue;
            if (f.isDirectory()) walk(f, ext, depth - 1, out);
            else if (n.endsWith(ext)) out.add(f);
        }
    }

    /** המספר (מ-0) של השורה "public class ..." — שם שמים את הסמן אחרי שהקובץ נפתח */
    public static int classLine(String code) {
        String[] lines = code.split("\n", -1);
        for (int i = 0; i < lines.length; i++) if (lines[i].trim().startsWith("public class ")) return i;
        return 0;
    }

    // ------------------------------------------------------------------ היררכיה מוכנה / עץ משלכם

    /** מחלקה בהיררכיה: שם, ממי יורשת, שדות, פעולות חדשות, דריסות (שם הפעולה ← גוף; "" — ברירת מחדל), toString */
    public static final class TreeClass {
        public String name, sup = "";
        public boolean isAbstract, toString;
        public final List<Field> fields = new ArrayList<Field>();
        public final List<Method> methods = new ArrayList<Method>();
        public final Map<String, String> overrides = new LinkedHashMap<String, String>();

        public TreeClass(String name, String sup) {
            this.name = name == null ? "" : name;
            this.sup = sup == null ? "" : sup;
        }

        TreeClass field(String type, String name) {
            fields.add(new Field(type, name));
            return this;
        }

        TreeClass method(String ret, String name, String body, boolean isAbstract) {
            Method m = new Method(ret, name, "");
            m.body = body;
            m.isAbstract = isAbstract;
            methods.add(m);
            return this;
        }

        TreeClass override(String name, String body) {
            overrides.put(name.toLowerCase(java.util.Locale.ROOT), body);
            return this;
        }
    }

    /**
     * היררכיה: המחלקות (מחלקת על לפני היורשות), ו-main לדוגמה — מערך של המחלקה העליונה (array), עצם לכל תא (objects: המחלקה ואז
     * הארגומנטים של הבנאי, אותו כתיב ב-Java וב-C#), ושורת הדפסה לכל תא (print: {a} — התא, {m:say} — שם פעולה, ב-C# באות גדולה)
     */
    public static final class Tree {
        public final String id, title, about;
        public String main, array, root, print;
        public final List<TreeClass> classes = new ArrayList<TreeClass>();
        public final List<String[]> objects = new ArrayList<String[]>();

        Tree(String id, String title, String about) {
            this.id = id;
            this.title = title;
            this.about = about;
        }

        TreeClass add(String name, String sup) {
            TreeClass c = new TreeClass(name, sup);
            classes.add(c);
            return c;
        }
    }

    public static final String TREES_TITLE = "היררכיה מוכנה", OBJECT_TITLE = "הוסף עצם", OWN_TREE = "עץ משלכם";

    /** שלוש ההיררכיות המוכנות: בעלי חיים (כמו בדף העבודה), צורות, אנשים בבית הספר */
    public static List<Tree> trees() {
        List<Tree> out = new ArrayList<Tree>();
        Tree a = new Tree("animals", "בעלי חיים", "Animal ← Mammal ← Dog ← Boxer, Labrador (ובתוך Labrador — Person). כל אחת דורסת את say()\u200E");
        a.add("Animal", "").field("String", "name").field("String", "gender").method("String", "say", "return \"I am an Animal\";", false);
        a.add("Mammal", "Animal").field("double", "milk").override("say", "return \"I am a Mammal\";");
        a.add("Dog", "Mammal").field("int", "bonesToEat").override("say", "return \"I am a Dog\";");
        a.add("Boxer", "Dog").field("Object", "guard").override("say", "return \"I am a Boxer\";");
        a.add("Person", "").field("String", "name");
        a.add("Labrador", "Dog").field("Person", "person");
        a.main = "ZooDemo";
        a.array = "zoo";
        a.root = "Animal";
        a.print = "{a}.{m:say}()";
        a.objects.add(new String[] { "Animal", "\"Bob\"", "\"male\"" });
        a.objects.add(new String[] { "Mammal", "\"Numa\"", "\"female\"", "2.5" });
        a.objects.add(new String[] { "Dog", "\"Dasty\"", "\"female\"", "0.5", "3" });
        a.objects.add(new String[] { "Boxer", "\"Rexy\"", "\"male\"", "3.2", "3", "\"house\"" });
        a.objects.add(new String[] { "Labrador", "\"Toto\"", "\"male\"", "2.5", "4", "new Person(\"Hila\")" });
        out.add(a);

        Tree s = new Tree("shapes", "צורות", "Shape (מופשטת) ← Circle, Rectangle, Triangle. כל צורה מחשבת את השטח שלה — area()\u200E");
        TreeClass shape = s.add("Shape", "").field("String", "color").method("double", "area", null, true);
        shape.isAbstract = true;
        shape.toString = true;
        s.add("Circle", "Shape").field("double", "radius").override("area", "return 3.14 * this.radius * this.radius;").toString = true;
        s.add("Rectangle", "Shape").field("double", "width").field("double", "height").override("area", "return this.width * this.height;").toString = true;
        s.add("Triangle", "Shape").field("double", "side").field("double", "height").override("area", "return this.side * this.height / 2;").toString = true;
        s.main = "ShapesDemo";
        s.array = "shapes";
        s.root = "Shape";
        s.print = "{a} + \", area=\" + {a}.{m:area}()";
        s.objects.add(new String[] { "Circle", "\"red\"", "2" });
        s.objects.add(new String[] { "Rectangle", "\"blue\"", "3", "4" });
        s.objects.add(new String[] { "Triangle", "\"green\"", "6", "2" });
        out.add(s);

        Tree p = new Tree("school", "אנשים בבית הספר", "Person ← Pupil, Teacher ← HomeroomTeacher. כל אחד דורס את role()\u200E");
        p.add("Person", "").field("String", "name").field("int", "age").method("String", "role", "return \"Person\";", false).toString = true;
        p.add("Pupil", "Person").field("int", "grade").override("role", "return \"Pupil\";").toString = true;
        p.add("Teacher", "Person").field("String", "subject").override("role", "return \"Teacher\";").toString = true;
        p.add("HomeroomTeacher", "Teacher").field("String", "classroom").override("role", "return \"Homeroom teacher\";").toString = true;
        p.main = "SchoolDemo";
        p.array = "people";
        p.root = "Person";
        p.print = "{a}.{m:role}() + \": \" + {a}";
        p.objects.add(new String[] { "Person", "\"Dana\"", "40" });
        p.objects.add(new String[] { "Pupil", "\"Noa\"", "16", "11" });
        p.objects.add(new String[] { "Teacher", "\"Avi\"", "45", "\"Math\"" });
        p.objects.add(new String[] { "HomeroomTeacher", "\"Rina\"", "50", "\"CS\"", "\"11-2\"" });
        out.add(p);
        return out;
    }

    /** שורה בטבלה של "עץ משלכם": שם, ממי יורשת ("" — אף אחת), השדות ("String name; int age") */
    public static final class TreeRow {
        public String name, sup, fields;

        public TreeRow(String name, String sup, String fields) {
            this.name = name == null ? "" : name;
            this.sup = sup == null ? "" : sup;
            this.fields = fields == null ? "" : fields;
        }
    }

    /** השדות שכתובים בשורה: "String name; int age" (גם בפסיקים) */
    public static List<Field> parseFields(String text) {
        List<Field> out = new ArrayList<Field>();
        for (String part : splitTop((text == null ? "" : text).replace(';', ','))) {
            String t = part.trim();
            if (t.isEmpty()) continue;
            int sp = t.lastIndexOf(' ');
            out.add(sp < 0 ? new Field(t, "") : new Field(t.substring(0, sp).trim(), t.substring(sp + 1)));
        }
        return out;
    }

    /**
     * "עץ משלכם" → היררכיה: מחלקת על לפני היורשות (לפי הסדר בטבלה), toString בכל מחלקה, ו-main עם מערך של השורש הראשון
     * (עצם מכל מחלקה בעץ שלו). errors — שגיאות בטבלה (שם כפול, ממי יורשת שלא קיימת, מעגל)
     */
    public static Tree ownTree(List<TreeRow> rows, List<Known> known, List<String> errors) {
        Tree t = new Tree("own", OWN_TREE, "");
        List<TreeRow> filled = new ArrayList<TreeRow>();
        for (TreeRow r : rows) if (!r.name.trim().isEmpty() || !r.fields.trim().isEmpty() || !r.sup.trim().isEmpty()) filled.add(r);
        if (filled.isEmpty()) {
            errors.add("כותבים לפחות מחלקה אחת בטבלה");
            return t;
        }
        Map<String, TreeRow> byName = new LinkedHashMap<String, TreeRow>();
        for (TreeRow r : filled) {
            String n = cap(r.name.trim());
            if (n.isEmpty()) {
                errors.add("יש שורה בלי שם מחלקה");
                continue;
            }
            for (String o : byName.keySet())
                if (o.equalsIgnoreCase(n)) errors.add("יש שתי מחלקות בשם " + n);
            byName.put(n, r);
        }
        List<String> done = new ArrayList<String>();
        boolean progress = true;
        while (done.size() < byName.size() && progress) {
            progress = false;
            for (Map.Entry<String, TreeRow> e : byName.entrySet()) {
                if (done.contains(e.getKey())) continue;
                String sup = cap(e.getValue().sup.trim());
                if (!sup.isEmpty() && byName.containsKey(sup) && !done.contains(sup)) continue;
                if (!sup.isEmpty() && !byName.containsKey(sup) && findKnown(known, sup, null, "") == null) {
                    errors.add(e.getKey() + " יורשת מ-" + sup + ", אבל אין מחלקה " + sup + " (לא בטבלה ולא בפרויקט)");
                    sup = "";
                }
                TreeClass c = t.add(e.getKey(), sup);
                c.toString = true;
                c.fields.addAll(parseFields(e.getValue().fields));
                done.add(e.getKey());
                progress = true;
            }
        }
        if (done.size() < byName.size()) {
            List<String> rest = new ArrayList<String>();
            for (String n : byName.keySet()) if (!done.contains(n)) rest.add(n);
            errors.add("יש מעגל בירושה: " + String.join(", ", rest) + " — מחלקה לא יכולה לרשת (גם לא דרך אחרות) מעצמה");
        }
        if (t.classes.isEmpty()) return t;
        // השורש: המחלקה הראשונה בטבלה שלא יורשת ממחלקה בטבלה
        String root = t.classes.get(0).name;
        for (TreeClass c : t.classes) if (c.sup.isEmpty() || !byName.containsKey(c.sup)) { root = c.name; break; }
        t.root = root;
        t.main = root + "Demo";
        t.array = "items";
        t.print = "{a}";
        return t;
    }

    /** התוצאה של יצירת היררכיה: שגיאות, אזהרות, והקבצים (שם הקובץ, הקוד) — המחלקות לפי הסדר ואז ה-main */
    public static final class TreeResult {
        public final List<String> errors = new ArrayList<String>(), warnings = new ArrayList<String>();
        public final List<String[]> files = new ArrayList<String[]>();
        /** המחלקות של הפרויקט אחרי היצירה (בשביל הצבעים בציור ובבדיקות) */
        public final List<Known> known = new ArrayList<Known>();
        /** תיקוני virtual בקבצים קיימים (C#) */
        public final List<Edit> edits = new ArrayList<Edit>();

        public boolean ok() {
            return errors.isEmpty();
        }
    }

    /**
     * כל הקבצים של היררכיה, בעזרת אותו מחולל של "צור מחלקה" (כל מחלקה נבדקת מול הפרויקט: שם תפוס, שם של Unit4...). base — שפה,
     * package / namespace, taken ו-known של הפרויקט. withMain — גם main לדוגמה; run — ב-C#: פעולה Run() במקום Main (בפרויקט שכבר יש בו Main)
     */
    public static TreeResult treeFiles(Tree t, Options base, boolean withMain, boolean run) {
        TreeResult r = new TreeResult();
        boolean cs = isCs(base.lang);
        String lang = cs ? CS : JAVA, ext = cs ? ".cs" : ".java";
        List<Known> known = new ArrayList<Known>(base.known);
        Map<String, String> taken = new LinkedHashMap<String, String>(base.taken);
        for (TreeClass tc : t.classes) {
            Options o = new Options();
            o.lang = lang;
            o.className = tc.name;
            o.pkg = base.pkg;
            o.namespace = base.namespace;
            o.fileScoped = base.fileScoped;
            o.taken = taken;
            o.known = known;
            o.toString = tc.toString;
            o.superName = tc.sup;
            o.isAbstract = tc.isAbstract;
            for (Field f : tc.fields) o.fields.add(new Field(f.type, f.name));
            for (Method m : tc.methods) {
                Method x = new Method(m.ret, m.name, m.params);
                x.body = m.body;
                x.isAbstract = m.isAbstract;
                o.methods.add(x);
            }
            for (Map.Entry<String, String> e : tc.overrides.entrySet()) {
                o.overrides.add(cs ? cap(e.getKey()) : e.getKey());
                if (e.getValue() != null && !e.getValue().isEmpty()) o.overrideBodies.put(e.getKey(), e.getValue());
            }
            // שם הפעולה ב-overrides לפי השם שנקרא מהקוד (say / Say)
            List<String> named = new ArrayList<String>();
            for (String n : o.overrides) named.add(n);
            o.overrides.clear();
            Check pre = check(o);
            for (Overridable ov : pre.overridable)
                for (String n : named) if (ov.method.name.equalsIgnoreCase(n)) o.overrides.add(ov.key);
            Check c = check(o);
            String label = c.className != null ? c.className : tc.name.trim().isEmpty() ? "(בלי שם)" : tc.name.trim();
            for (String e : c.errors) r.errors.add(label + ": " + e);
            for (String w : c.warnings) if (!w.startsWith("שם של מחלקה מתחיל") && !w.startsWith("ב-C# שם של פעולה")) r.warnings.add(label + ": " + w);
            if (!c.ok()) {
                Known stub = new Known(label, target(o), "");   // כדי שהיורשות לא יוסיפו שגיאה על מחלקה חסרה
                stub.sup = tc.sup;
                known.add(stub);
                continue;
            }
            String code = generate(o);
            String file = fileName(c.className, lang);
            r.files.add(new String[] { file, code });
            r.edits.addAll(c.edits);
            known.addAll(models(code, file));
            taken.put(c.className, file);
        }
        if (withMain && t.main != null && t.root != null) {
            Check mc = new Check();
            Options mo = new Options();
            mo.lang = lang;
            mo.pkg = base.pkg;
            mo.namespace = base.namespace;
            mo.taken = taken;
            mo.known = known;
            checkName(t.main, mo, mc, cs ? "C#" : "Java");
            for (String e : mc.errors) r.errors.add(t.main + ": " + e);
            if (mc.ok()) r.files.add(new String[] { fileName(t.main, lang), mainCode(t, base, known, run) });
        }
        r.known.addAll(known);
        return r;
    }

    /** ה-main לדוגמה: מערך של השורש, עצם בכל תא, ולולאה שמדפיסה */
    static String mainCode(Tree t, Options base, List<Known> known, boolean run) {
        boolean cs = isCs(base.lang);
        List<String[]> objs = new ArrayList<String[]>(t.objects);
        if (objs.isEmpty()) {   // עץ משלכם: עצם מכל מחלקה (לא מופשטת) בעץ של השורש, עם ערכים לדוגמה
            for (Known k : objectClasses(known, t.root)) {
                List<Field> ps = ctorParams(known, k.name);
                String[] o = new String[ps.size() + 1];
                o[0] = k.name;
                for (int i = 0; i < ps.size(); i++) o[i + 1] = sample(ps.get(i).type, k.name, objs.size() + i, cs);
                objs.add(o);
            }
        }
        String a = t.array, in = "        ", in2 = in + "    ";
        StringBuilder b = new StringBuilder();
        b.append(in).append(t.root).append("[] ").append(a).append(" = new ").append(t.root).append('[').append(objs.size()).append("];\n");
        for (int i = 0; i < objs.size(); i++) {
            String[] o = objs.get(i);
            StringBuilder args = new StringBuilder();
            for (int j = 1; j < o.length; j++) args.append(j > 1 ? ", " : "").append(o[j]);
            b.append(in).append(a).append('[').append(i).append("] = new ").append(o[0]).append('(').append(args).append(");\n");
        }
        String cell = a + "[i]";
        String print = t.print.replace("{a}", cell);
        Matcher mm = Pattern.compile("\\{m:(\\w+)\\}").matcher(print);
        StringBuffer pb = new StringBuffer();
        while (mm.find()) mm.appendReplacement(pb, cs ? cap(mm.group(1)) : mm.group(1));
        mm.appendTail(pb);
        if (cs) {
            b.append(in).append("for (int i = 0; i < ").append(a).append(".Length; i++)\n").append(in).append("{\n");
            b.append(in2).append("System.Console.WriteLine(").append(pb).append(");\n").append(in).append("}\n");
        } else {
            b.append(in).append("for (int i = 0; i < ").append(a).append(".length; i++) {\n");
            b.append(in2).append("System.out.println(").append(pb).append(");\n").append(in).append("}\n");
        }
        StringBuilder sb = new StringBuilder();
        if (cs) {
            String ns = base.namespace == null ? "" : base.namespace.trim(), pre = "";
            if (!ns.isEmpty() && base.fileScoped) sb.append("namespace ").append(ns).append(";\n\n");
            else if (!ns.isEmpty()) {
                sb.append("namespace ").append(ns).append("\n{\n");
                pre = "    ";
            }
            sb.append(pre).append("public static class ").append(t.main).append('\n').append(pre).append("{\n");
            sb.append(pre).append("    public static void ").append(run ? "Run" : "Main").append("()\n").append(pre).append("    {\n");
            for (String l : b.toString().split("\n")) sb.append(pre).append(l).append('\n');
            sb.append(pre).append("    }\n").append(pre).append("}\n");
            if (!pre.isEmpty()) sb.append("}\n");
        } else {
            String pkg = base.pkg == null ? "" : base.pkg.trim();
            if (!pkg.isEmpty()) sb.append("package ").append(pkg).append(";\n\n");
            sb.append("public class ").append(t.main).append(" {\n    public static void main(String[] args) {\n").append(b).append("    }\n}\n");
        }
        return sb.toString();
    }

    /** ערך לדוגמה לפרמטר בבנאי (עץ משלכם): מחרוזת — שם המחלקה ומספר, מספרים, true, 'a', ואחרת null */
    static String sample(String type, String cls, int n, boolean cs) {
        switch (type) {
            case "int": case "long": case "short": case "byte": return String.valueOf(n + 1);
            case "double": case "float": case "decimal": return (n + 1) + ".5" + (type.equals("float") ? "f" : type.equals("decimal") ? "m" : "");
            case "boolean": case "bool": return "true";
            case "char": return "'a'";
            case "String": case "string": return "\"" + cls + (n + 1) + "\"";
            default: return "null";
        }
    }

    /** לציור העץ בחלון: לכל מחלקה (לפי הסדר — הורה לפני ילדים) { שם, עומק, מספר צבע } */
    public static List<Object[]> treeLayout(Tree t, List<Known> projectKnown) {
        List<Known> all = new ArrayList<Known>();
        for (TreeClass c : t.classes) {
            Known k = new Known(cap(c.name.trim()), "", "");
            k.sup = c.sup;
            k.isAbstract = c.isAbstract;
            all.add(k);
        }
        // מחלקה מהפרויקט שהעץ יורש ממנה — מצוירת כשורש (עם השרשרת שלה)
        List<Known> roots = new ArrayList<Known>();
        for (Known k : all)
            if (!k.sup.isEmpty() && byName(all, k.sup) == null) {
                Known pk = findKnown(projectKnown, k.sup, null, "");
                if (pk != null) for (Known x : chainOf(projectKnown, pk)) if (byName(roots, x.name) == null && byName(all, x.name) == null) roots.add(0, x);
            }
        List<Known> list = new ArrayList<Known>(roots);
        list.addAll(all);
        Map<String, Integer> colors = colors(sorted(list));
        List<Object[]> out = new ArrayList<Object[]>();
        List<Known> placed = new ArrayList<Known>();
        for (Known k : list) if (k.sup.isEmpty() || byName(list, k.sup) == null) place(list, k, 0, colors, out, placed);
        for (Known k : list) if (!placed.contains(k)) place(list, k, 0, colors, out, placed);   // מעגל — בכל זאת מצוירים
        return out;
    }

    private static void place(List<Known> list, Known k, int depth, Map<String, Integer> colors, List<Object[]> out, List<Known> placed) {
        if (placed.contains(k) || depth > 20) return;
        placed.add(k);
        Integer c = colors.get(k.name);
        out.add(new Object[] { k.name, depth, c == null ? -1 : c, k.isAbstract });
        for (Known x : list) if (k.name.equals(x.sup)) place(list, x, depth + 1, colors, out, placed);
    }

    // ------------------------------------------------------------------ הוסף עצם

    /** המחלקות שאפשר ליצור מהן עצם למשתנה מהטיפוס type: היא והיורשות שלה (רוחב קודם), בלי מופשטות וממשקים */
    public static List<Known> objectClasses(List<Known> known, String type) {
        List<Known> out = new ArrayList<Known>(), queue = new ArrayList<Known>();
        Known start = findKnown(known, type, null, "");
        if (start == null) return out;
        queue.add(start);
        for (int i = 0; i < queue.size() && queue.size() < 200; i++)
            for (Known k : sorted(known)) if (queue.get(i).name.equals(k.sup) && !queue.contains(k)) queue.add(k);
        for (Known k : queue) if (!k.isAbstract && !k.isInterface) out.add(k);
        return out;
    }

    /** הפרמטרים של הבנאי (הרחב) של המחלקה — כל הרמות, כי הבנאי מעביר את של מחלקת העל ב-super */
    public static List<Field> ctorParams(List<Known> known, String cls) {
        Known k = findKnown(known, cls, null, "");
        return k == null || k.ctor == null ? new ArrayList<Field>() : k.ctor;
    }

    /** לאיזו מחלקה בשרשרת שייך פרמטר בבנאי (לפי שדה באותו שם; אחרת — המחלקה עצמה) */
    public static String paramOwner(List<Known> known, String cls, String param) {
        Known k = findKnown(known, cls, null, "");
        if (k == null) return cls;
        for (Known x : chainOf(known, k)) for (Field f : x.fields) if (f.name.equals(param)) return x.name;
        return k.name;
    }

    /** מה שכתבו בשדה של פרמטר → ערך בקוד: מחרוזת במרכאות, תו בגרש, מספר כמו שהוא; ריק — ברירת מחדל */
    public static String literal(String type, String text, String lang) {
        String t = text == null ? "" : text.trim(), ty = type == null ? "" : type.trim();
        boolean str = ty.equals("String") || ty.equals("string");
        if (t.isEmpty()) return defaultValue(ty, "");
        if (str) return t.equals("null") || (t.length() >= 2 && t.startsWith("\"") && t.endsWith("\"")) ? t : quote(t);
        if (ty.equals("char")) return t.length() == 1 ? "'" + (t.equals("'") || t.equals("\\") ? "\\" + t : t) + "'" : t;
        if (ty.equals("float") && t.matches("-?\\d+(\\.\\d+)?")) return t + "f";
        if (ty.equals("Object") || ty.equals("object")) {
            boolean raw = t.matches("-?\\d+(\\.\\d+)?") || t.equals("true") || t.equals("false") || t.equals("null") || t.startsWith("new ")
                    || t.startsWith("\"") || t.startsWith("'");
            return raw ? t : quote(t);
        }
        return t;
    }

    /** "Rexy, male, new Person(\"a, b\")" → 3 ערכים: מפצל בפסיקים שלא בתוך מרכאות / סוגריים (ערכים לתא של מערך) */
    public static List<String> splitArgs(String text) {
        List<String> out = new ArrayList<String>();
        String s = text == null ? "" : text;
        if (s.trim().isEmpty()) return out;
        int depth = 0;
        char q = 0;
        StringBuilder cur = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (q != 0) {
                if (ch == '\\' && i + 1 < s.length()) {
                    cur.append(ch).append(s.charAt(++i));
                    continue;
                }
                if (ch == q) q = 0;
            } else if (ch == '"' || ch == '\'') q = ch;
            else if (ch == '(' || ch == '[' || ch == '{') depth++;
            else if (ch == ')' || ch == ']' || ch == '}') depth--;
            else if (ch == ',' && depth == 0) {
                out.add(cur.toString().trim());
                cur.setLength(0);
                continue;
            }
            cur.append(ch);
        }
        out.add(cur.toString().trim());
        return out;
    }

    private static String quote(String t) {
        return "\"" + t.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    /** "הוסף עצם": טיפוס המשתנה, המחלקה של העצם, שם, וערך לכל פרמטר — או מערך של טיפוס המשתנה, ולכל תא מחלקה וערכים */
    public static final class ObjOptions {
        public String lang = JAVA, varType = "", varName = "", cls = "", indent = "";
        public List<String> args = new ArrayList<String>();
        public boolean array;
        public int size = 4;
        /** במערך: לכל תא { מחלקה, ערך, ערך... } (מחלקה "" — התא נשאר null) */
        public List<List<String>> cells = new ArrayList<List<String>>();
    }

    public static final class ObjResult {
        public final List<String> errors = new ArrayList<String>();
        public String code = "";

        public boolean ok() {
            return errors.isEmpty();
        }
    }

    /** הקוד של "הוסף עצם" (כל שורה עם indent), ושגיאות בעברית */
    public static ObjResult objectCode(ObjOptions o, List<Known> known) {
        ObjResult r = new ObjResult();
        String lang = isCs(o.lang) ? CS : JAVA;
        String type = o.varType == null ? "" : o.varType.trim(), name = o.varName == null ? "" : o.varName.trim();
        if (type.isEmpty()) r.errors.add("בוחרים את טיפוס המשתנה");
        else if (findKnown(known, type, null, "") == null) r.errors.add("אין בפרויקט מחלקה " + type);
        if (name.isEmpty()) r.errors.add("כותבים שם למשתנה");
        else if (!isIdentifier(name, lang)) r.errors.add("שם המשתנה חייב להיות באנגלית, בלי רווחים, להתחיל באות, ולא להיות מילה שמורה");
        List<Known> allowed = objectClasses(known, type);
        StringBuilder sb = new StringBuilder();
        String in = o.indent == null ? "" : o.indent;
        if (!o.array) {
            String cls = o.cls == null ? "" : o.cls.trim();
            if (r.errors.isEmpty() && cls.isEmpty()) r.errors.add("בוחרים את המחלקה של העצם");
            else if (r.errors.isEmpty() && byName(allowed, cls) == null)
                r.errors.add(cls + " לא יכולה להיכנס למשתנה מסוג " + type + " — רק " + type + " או מחלקה שיורשת ממנה (ולא מופשטת)");
            sb.append(in).append(type).append(' ').append(name).append(" = new ").append(cls.isEmpty() ? type : cls).append('(')
                    .append(argList(known, cls.isEmpty() ? type : cls, o.args, lang)).append(");");
        } else {
            if (o.size < 1 || o.size > 30) r.errors.add("גודל המערך — בין 1 ל-30");
            int n = Math.max(1, Math.min(30, o.size));
            sb.append(in).append(type).append("[] ").append(name).append(" = new ").append(type).append('[').append(n).append("];");
            for (int i = 0; i < n && i < o.cells.size(); i++) {
                List<String> cell = o.cells.get(i);
                String cls = cell.isEmpty() ? "" : cell.get(0).trim();
                if (cls.isEmpty()) continue;
                if (byName(allowed, cls) == null) {
                    r.errors.add("בתא " + i + ": " + cls + " לא יכולה להיכנס למערך של " + type);
                    continue;
                }
                sb.append('\n').append(in).append(name).append('[').append(i).append("] = new ").append(cls).append('(')
                        .append(argList(known, cls, cell.subList(1, cell.size()), lang)).append(");");
            }
        }
        r.code = sb.toString();
        return r;
    }

    private static String argList(List<Known> known, String cls, List<String> args, String lang) {
        List<Field> ps = ctorParams(known, cls);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < ps.size(); i++) sb.append(i > 0 ? ", " : "").append(literal(ps.get(i).type, i < args.size() ? args.get(i) : "", lang));
        return sb.toString();
    }

    /** לבדיקות: כל המקרה בשורה אחת — "lang|Name|ctor|toString|pkg-or-ns|file|type name{Ref=pkg};...|taken1,taken2|Known@pkg,..." */
    public static Options parseCase(String line) {
        String[] p = line.split("\\|", -1);
        Options o = new Options();
        o.lang = p[0];
        o.className = p[1];
        o.constructor = p[2].equals("1");
        o.toString = p[3].equals("1");
        if (isCs(o.lang)) o.namespace = p[4];
        else o.pkg = p[4];
        o.fileScoped = p[5].equals("1");
        if (p.length > 6 && !p[6].isEmpty())
            for (String f : p[6].split(";", -1)) {
                Map<String, String> refs = new LinkedHashMap<String, String>();
                int br = f.indexOf('{');
                if (br >= 0 && f.endsWith("}")) {   // שדה{Student=school.a} — מה שנבחר מהרשימה
                    for (String r : f.substring(br + 1, f.length() - 1).split(",", -1)) {
                        int eq = r.indexOf('=');
                        if (eq > 0) refs.put(r.substring(0, eq), r.substring(eq + 1));
                    }
                    f = f.substring(0, br);
                }
                int sp = f.lastIndexOf(' ');
                Field fl = sp < 0 ? new Field(f, "") : new Field(f.substring(0, sp), f.substring(sp + 1));
                fl.refs.putAll(refs);
                o.fields.add(fl);
            }
        if (p.length > 7) for (String t : p[7].split(",", -1)) if (!t.isEmpty()) o.taken.put(t, t + (isCs(o.lang) ? ".cs" : ".java"));
        if (p.length > 8) for (String k : p[8].split(",", -1)) {   // מחלקות בפרויקט: שם@package
            if (k.isEmpty()) continue;
            int at = k.indexOf('@');
            String n = at < 0 ? k : k.substring(0, at), pk = at < 0 ? "" : k.substring(at + 1);
            o.known.add(new Known(n, pk, (pk.isEmpty() ? "" : pk.replace('.', '/') + "/") + n + (isCs(o.lang) ? ".cs" : ".java")));
        }
        return o;
    }

    /**
     * לבדיקות, עם הורשה — עוד עמודות: "...|מחלקת על|abstract (1)|דריסות (שמות בפסיקים)|פעולות חדשות (ret name(params);... — ! בהתחלה:
     * מופשטת)|קובץ קוד (יחסית ל-base) שהמחלקות שלו נכנסות לפרויקט"
     */
    public static Options parseCase(String line, File base) {
        Options o = parseCase(line);
        String[] p = line.split("\\|", -1);
        if (p.length > 9) o.superName = p[9];
        if (p.length > 10) o.isAbstract = p[10].equals("1");
        if (p.length > 11) for (String n : p[11].split(",", -1)) if (!n.trim().isEmpty()) o.overrides.add(n.trim());
        if (p.length > 12 && !p[12].isEmpty())
            for (String m : p[12].split(";", -1)) {
                String t = m.trim();
                boolean abs = t.startsWith("!");
                if (abs) t = t.substring(1).trim();
                int par = t.indexOf('('), sp = par < 0 ? -1 : t.lastIndexOf(' ', par);
                Method me = par < 0 ? new Method("", t, "") : new Method(sp < 0 ? "" : t.substring(0, sp), t.substring(sp + 1, par), t.substring(par + 1, t.lastIndexOf(')') < par ? t.length() : t.lastIndexOf(')')));
                me.isAbstract = abs;
                o.methods.add(me);
            }
        if (p.length > 13 && !p[13].isEmpty() && base != null) {
            try {
                File f = new File(base, p[13]);
                o.known.addAll(models(new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8), f.getName()));
            } catch (java.io.IOException e) {
                throw new IllegalArgumentException(e.toString());
            }
        }
        return o;
    }

    /** לבדיקות: מה שנקרא מהקוד על מחלקה — שורה אחת */
    public static String modelText(Known k) {
        StringBuilder sb = new StringBuilder(k.name).append(k.sup.isEmpty() ? "" : " : " + k.sup).append(k.isAbstract ? " abstract" : "")
                .append(k.isFinal ? " final" : "").append(k.isInterface ? " interface" : "").append(" |");
        for (Field f : k.fields) sb.append(' ').append(f.type).append(' ').append(f.name).append(';');
        sb.append(" | ctor(");
        if (k.ctor == null) sb.append('-');
        else for (int i = 0; i < k.ctor.size(); i++) sb.append(i > 0 ? ", " : "").append(k.ctor.get(i).type).append(' ').append(k.ctor.get(i).name);
        sb.append(") |");
        for (Method m : k.methods) sb.append(' ').append(m.mods.isEmpty() ? "" : "[" + m.mods + "] ").append(m.ret).append(' ').append(m.name).append('(').append(m.params).append(')')
                .append(m.isAbstract ? "!" : "").append(';');
        return sb.toString();
    }

    /** לבדיקות: התוצאה של מקרה כטקסט — השגיאות, האזהרות, ההורשה והקוד (או התצוגה המקדימה כשיש שגיאה) */
    public static String describe(Options o) {
        Check c = check(o);
        StringBuilder sb = new StringBuilder();
        for (String e : c.errors) sb.append("ERROR ").append(e).append('\n');
        for (String w : c.warnings) sb.append("WARN ").append(w).append('\n');
        if (c.sup != null) {
            sb.append("SUPER ").append(c.sup.name).append(" ctor(").append(args(c.superParams)).append(")").append(c.superToString ? " toString" : "").append('\n');
            for (Inherited h : c.inherited) {
                sb.append("INHERITED ").append(h.from).append(':');
                for (Field f : h.fields) sb.append(' ').append(f.type).append(' ').append(f.name);
                sb.append('\n');
            }
            for (Overridable ov : c.overridable)
                sb.append("OVERRIDABLE ").append(ov.key).append(" @").append(ov.from).append(ov.isAbstract ? " abstract" : "").append(ov.needsVirtual ? " +virtual" : "")
                        .append(c.overrides.contains(ov) ? " *" : "").append('\n');
            Map<String, Integer> col = colorsWith(o.known, c.className, c.sup);
            sb.append("COLORS");
            for (Map.Entry<String, Integer> e : col.entrySet()) if (e.getValue() >= 0) sb.append(' ').append(e.getKey()).append('=').append(String.format("#%06x", ink(e.getValue())));
            sb.append('\n');
        }
        sb.append(c.ok() ? "FILE " + fileName(c.className, o.lang) + "\n" + generate(o) : "PREVIEW\n" + preview(o));
        return sb.toString();
    }
}
