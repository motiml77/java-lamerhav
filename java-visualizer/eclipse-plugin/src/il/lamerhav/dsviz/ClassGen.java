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

        public Known(String name, String pkg, String file) {
            this.name = name;
            this.pkg = pkg == null ? "" : pkg;
            this.file = file == null ? "" : file;
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
    }

    /** תוצאת הבדיקה: שגיאות (חוסמות), אזהרות, שם המחלקה המתוקן והשדות התקינים (טיפוסים מתוקנים) */
    public static final class Check {
        public final List<String> errors = new ArrayList<String>(), warnings = new ArrayList<String>();
        public String className;
        public final List<Field> fields = new ArrayList<Field>();
        /** שורות ה-import / using של מחלקות מהפרויקט (ממוינות), והאם צריך את ספריית הקורס */
        public final List<String> imports = new ArrayList<String>();
        public boolean course;

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

    static List<Known> sorted(List<Known> known) {
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
        String raw = o.className == null ? "" : o.className.trim();
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
        if (filled == 0) c.errors.add("מוסיפים לפחות שדה אחד (טיפוס ושם)");
        resolve(o, c);
        return c;
    }

    /** ה-package (Java) / ה-namespace (C#) של המחלקה החדשה */
    static String target(Options o) {
        String t = isCs(o.lang) ? o.namespace : o.pkg;
        return t == null ? "" : t.trim();
    }

    /**
     * import / using לכל מחלקה מהפרויקט שבשימוש בשדות ונמצאת בחבילה אחרת (מה שנבחר מהרשימה — refs; אחרת: באותה חבילה ← בלי,
     * אחת בשם הזה ← היא, כמה ← הראשונה ואזהרה). Student / Car שאין בפרויקט — של ספריית הקורס
     */
    static void resolve(Options o, Check c) {
        boolean cs = isCs(o.lang);
        String target = target(o);
        List<String> lines = new ArrayList<String>();
        for (Field f : c.fields) {
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
            sb.append(base).append("public class ").append(name).append('\n').append(base).append("{\n");
            body(sb, name, fields, o, in, true);
            sb.append(base).append("}\n");
            if (!base.isEmpty()) sb.append("}\n");
        } else {
            String pkg = o.pkg == null ? "" : o.pkg.trim();
            if (!pkg.isEmpty()) sb.append("package ").append(pkg).append(";\n\n");
            if (c.course) sb.append("import unit4.collectionsLib.*;\n");
            for (String i : c.imports) sb.append(i).append('\n');
            if (c.course || !c.imports.isEmpty()) sb.append('\n');
            in = "    ";
            sb.append("public class ").append(name).append(" {\n");
            body(sb, name, fields, o, in, false);
            sb.append("}\n");
        }
        return sb.toString();
    }

    /** פתיחת בלוק: ב-Java בסוף השורה, ב-C# בשורה משלו (כמו ש-Visual Studio מסדר) */
    private static void open(StringBuilder sb, String head, String in, boolean cs) {
        if (cs) sb.append(in).append(head).append('\n').append(in).append("{\n");
        else sb.append(in).append(head).append(" {\n");
    }

    private static void body(StringBuilder sb, String name, List<Field> fields, Options o, String in, boolean cs) {
        String lang = cs ? CS : JAVA, in2 = in + "    ";
        for (Field f : fields) sb.append(in).append("private ").append(f.type).append(' ').append(f.name).append(";\n");
        if (o.constructor && !fields.isEmpty()) {
            StringBuilder params = new StringBuilder();
            for (Field f : fields) params.append(params.length() > 0 ? ", " : "").append(f.type).append(' ').append(f.name);
            sb.append('\n');
            open(sb, "public " + name + "(" + params + ")", in, cs);
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
        if (o.toString) {
            sb.append('\n');
            open(sb, cs ? "public override string ToString()" : "public String toString()", in, cs);
            if (fields.isEmpty()) sb.append(in2).append("return \"").append(name).append("\";\n");
            else {
                List<String> parts = new ArrayList<String>();
                for (int i = 0; i < fields.size(); i++) {
                    Field f = fields.get(i);
                    parts.add("\"" + (i == 0 ? name + ": " : ", ") + f.name + "=\" + " + show(f, cs));
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
            for (String[] t : topTypes(text)) if (seen.add(t[0] + "|" + t[1])) out.add(new Known(t[0], t[1], rel));
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

    /** לבדיקות: התוצאה של מקרה כטקסט — השגיאות, האזהרות והקוד (או התצוגה המקדימה כשיש שגיאה) */
    public static String describe(Options o) {
        Check c = check(o);
        StringBuilder sb = new StringBuilder();
        for (String e : c.errors) sb.append("ERROR ").append(e).append('\n');
        for (String w : c.warnings) sb.append("WARN ").append(w).append('\n');
        sb.append(c.ok() ? "FILE " + fileName(c.className, o.lang) + "\n" + generate(o) : "PREVIEW\n" + preview(o));
        return sb.toString();
    }
}
