package il.lamerhav.dsviz;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * יוצר את קוד התרגיל (קובץ Java) לפי הבחירות של התלמיד.
 * שימו לב: יש עותק זהה ב-JavaScript בתוסף של VS Code (templates.js) — שינוי כאן צריך להיעשות גם שם.
 */
public final class Scaffold {
    private Scaffold() {}

    public static final class Structure {
        public final String id, label, cls, varName, build, random, note, hint;
        public final int dims;   // 0 = מבנה של הספרייה, 1 = מערך, 2 = מערך דו-ממדי

        Structure(String id, String label, String cls, String varName, String build, String random, String note, String hint) {
            this(id, label, cls, 0, varName, build, random, note, hint);
        }

        Structure(String id, String label, String cls, int dims, String varName, String build, String random, String note, String hint) {
            this.id = id; this.label = label; this.cls = cls; this.dims = dims; this.varName = varName;
            this.build = build; this.random = random; this.note = note; this.hint = hint;
        }
    }

    public static final Map<String, Structure> STRUCTURES = new LinkedHashMap<String, Structure>();
    static {
        add(new Structure("stack", "מחסנית", "Stack", "s", "stack", "randomStack",
                "האיבר האחרון בראש המחסנית",
                "{v}.push(x)   {v}.pop()   {v}.top()   {v}.isEmpty()"));
        add(new Structure("queue", "תור", "Queue", "q", "queue", "randomQueue",
                "האיבר הראשון בראש התור",
                "{v}.insert(x)   {v}.remove()   {v}.head()   {v}.isEmpty()"));
        add(new Structure("list", "רשימה מקושרת", "Node", "lst", "list", "randomList",
                "החוליה הראשונה היא lst",
                "{v}.getValue()   {v}.getNext()   {v}.setNext(n)   {v}.hasNext()   new Node<{T}>(x)"));
        add(new Structure("tree", "עץ בינארי", "BinNode", "t", "tree", "randomBst",
                "לפי רמות: שורש, ילדים, נכדים... (null = אין צומת)",
                "{v}.getValue()   {v}.getLeft()   {v}.getRight()   {v}.hasLeft()   {v}.hasRight()   {v}.setLeft(n)"));
        add(new Structure("bst", "עץ חיפוש בינארי", "BinNode", "t", "bst", "randomBst",
                "הכנסה לעץ חיפוש לפי הסדר",
                "{v}.getValue()   {v}.getLeft()   {v}.getRight()   {v}.hasLeft()   {v}.hasRight()   {v}.setLeft(n)"));
        add(new Structure("array", "מערך", null, 1, "arr", "array", "randomArray",
                "מערך — מוצג בחלון",
                "{v}.length   {v}[i]   for (int i = 0; i < {v}.length; i++)"));
        add(new Structure("matrix", "מערך דו-ממדי", null, 2, "mat", "matrix", "randomMatrix",
                "{v}[שורה][עמודה]",
                "{v}.length (שורות)   {v}[0].length (עמודות)   {v}[i][j]"));
    }

    private static void add(Structure s) {
        STRUCTURES.put(s.id, s);
    }

    public static final List<String> TYPES = Arrays.asList("Integer", "String", "Character", "Double", "Student");

    public static String primitive(String type) {
        switch (type) {
            case "Integer": return "int";
            case "Character": return "char";
            case "Double": return "double";
            case "Student": return "Student";
            default: return "String";
        }
    }

    static String zero(String type) {
        switch (type) {
            case "Integer": return "0";
            case "Character": return "' '";
            case "Double": return "0.0";
            default: return "null";   // String, Student
        }
    }

    public static String defaultValues(String structure, String type, boolean second) {
        if (structure.equals("matrix")) {
            switch (type) {
                case "Integer": return "1, 2, 3; 4, 5, 6; 7, 8, 9";
                case "String": return "a, b; c, d";
                case "Character": return "a, b, c; d, e, f";
                case "Double": return "1.5, 2.0; 3.5, 4.0";
                default: return "";
            }
        }
        String[] d; // first, second, tree, bst
        switch (type) {
            case "Student": d = new String[] { "4", "3", "5", "5" }; break;
            case "Integer": d = new String[] { "5, 2, 8, 1, 9", "3, 7, 4", "5, 3, 8, 1, 4, null, 9", "5, 3, 8, 1, 4, 9" }; break;
            case "String": d = new String[] { "dog, cat, bird, fish", "sun, moon", "m, c, t, a, f, null, z", "m, c, t, a, f, z" }; break;
            case "Character": d = new String[] { "a, b, c, d", "x, y", "m, c, t, a, f, null, z", "m, c, t, a, f, z" }; break;
            default: d = new String[] { "1.5, 2.0, 3.25, 0.5", "4.5, 1.0", "5.0, 3.5, 8.25, 1.0, 4.0, null, 9.5", "5.0, 3.5, 8.25, 1.0, 4.0, 9.5" };
        }
        if (structure.equals("tree")) return d[2];
        if (structure.equals("bst")) return d[3];
        return second ? d[1] : d[0];
    }

    private static final Set<String> KEYWORDS = new HashSet<String>(Arrays.asList((
            "abstract assert boolean break byte case catch char class const continue default do double "
            + "else enum extends final finally float for goto if implements import instanceof int interface long native new "
            + "package private protected public return short static strictfp super switch synchronized this throw throws "
            + "transient try void volatile while true false null var record").split(" ")));

    private static final Pattern IDENT = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

    public static boolean isIdentifier(String s) {
        return s != null && IDENT.matcher(s).matches() && !KEYWORDS.contains(s);
    }

    /** לתלמידים כותבים כמה תלמידים (מספר) — הם נוצרים בהרצה עם Build.students */
    public static int studentCount(String text) {
        String t = text == null ? "" : text.trim();
        if (!t.matches("\\d{1,2}") || Integer.parseInt(t) < 1 || Integer.parseInt(t) > 20)
            throw new IllegalArgumentException("לתלמידים כותבים כמה תלמידים ליצור (מספר בין 1 ל-20)");
        return Integer.parseInt(t);
    }

    /** "1, 2; 3, 4" → [[1, 2], [3, 4]] */
    public static List<List<String>> matrixLiterals(String text, String type) {
        if (type.equals("Student")) throw new IllegalArgumentException("אין מערך דו-ממדי של תלמידים — בחרו מערך רגיל");
        List<List<String>> rows = new ArrayList<List<String>>();
        for (String r : (text == null ? "" : text).split(";")) {
            if (r.trim().isEmpty()) continue;
            List<String> row = literals(r.trim(), type, false);
            if (row.isEmpty()) throw new IllegalArgumentException("שורה ריקה במערך הדו-ממדי");
            rows.add(row);
        }
        return rows;
    }

    /** "5, 2, 8" → [5, 2, 8] ; זורק IllegalArgumentException עם הודעה בעברית אם ערך לא מתאים */
    public static List<String> literals(String text, String type, boolean allowNull) {
        List<String> res = new ArrayList<String>();
        if (type.equals("Student")) {
            studentCount(text);
            return res;
        }
        for (String raw : (text == null ? "" : text).split(",")) {
            String t = raw.trim();
            if (t.isEmpty()) continue;
            if (t.equals("null")) {
                if (!allowNull) throw new IllegalArgumentException("null מותר רק בעץ בינארי (לציון צומת חסר)");
                res.add("null");
                continue;
            }
            switch (type) {
                case "Integer":
                    if (!t.matches("-?\\d{1,9}")) throw new IllegalArgumentException("הערך \"" + t + "\" אינו מספר שלם");
                    res.add(String.valueOf(Integer.parseInt(t)));
                    break;
                case "Double":
                    if (!t.matches("-?(\\d+\\.?\\d*|\\.\\d+)")) throw new IllegalArgumentException("הערך \"" + t + "\" אינו מספר");
                    res.add(t.contains(".") ? t : t + ".0");
                    break;
                case "Character": {
                    String c = t;
                    if (c.length() >= 3 && c.charAt(0) == '\'' && c.charAt(c.length() - 1) == '\'') c = c.substring(1, c.length() - 1);
                    if (c.codePointCount(0, c.length()) != 1) throw new IllegalArgumentException("הערך \"" + t + "\" אינו תו בודד");
                    res.add("'" + (c.equals("'") || c.equals("\\") ? "\\" + c : c) + "'");
                    break;
                }
                default: {
                    String s = t;
                    if (s.length() >= 2 && s.charAt(0) == '"' && s.charAt(s.length() - 1) == '"') s = s.substring(1, s.length() - 1);
                    res.add("\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"");
                }
            }
        }
        return res;
    }

    /** הבחירות של התלמיד */
    public static final class Options {
        public String structure = "stack", type = "Integer", returns = "int";
        public boolean second, extra, random;
        public String values = "", values2 = "", className = "Targil1", functionName = "solve";
    }

    /** התוצאה: הקוד, ומספר השורה (מ-0) שבה התלמיד מתחיל לכתוב */
    public static final class Result {
        public final String code;
        public final int cursorLine;

        Result(String code, int cursorLine) {
            this.code = code;
            this.cursorLine = cursorLine;
        }
    }

    public static Result generate(Options o) {
        Structure st = STRUCTURES.get(o.structure);
        if (st == null) throw new IllegalArgumentException("מבנה לא מוכר: " + o.structure);
        if (!TYPES.contains(o.type)) throw new IllegalArgumentException("טיפוס לא מוכר: " + o.type);
        if (!isIdentifier(o.className)) throw new IllegalArgumentException("שם המחלקה חייב להיות באנגלית, בלי רווחים, ולהתחיל באות");
        if (!isIdentifier(o.functionName)) throw new IllegalArgumentException("שם הפעולה חייב להיות באנגלית, בלי רווחים, ולהתחיל באות");
        boolean random = o.random && o.type.equals("Integer");
        String T = o.type;
        String full = typeOf(st, T);
        String v1 = st.varName, v2 = st.varName + "2";
        String prim = primitive(T);

        String retType, retDefault = null;
        switch (o.returns) {
            case "void": retType = "void"; break;
            case "int": retType = "int"; retDefault = "0"; break;
            case "boolean": retType = "boolean"; retDefault = "false"; break;
            case "element": retType = prim; retDefault = zero(T); break;
            case "structure": retType = full; retDefault = "null"; break;
            default: throw new IllegalArgumentException("ערך החזרה לא מוכר: " + o.returns);
        }

        List<String> params = new ArrayList<String>(), args = new ArrayList<String>();
        params.add(full + " " + v1);
        args.add(v1);
        if (o.second) { params.add(full + " " + v2); args.add(v2); }
        if (o.extra) { params.add(extraType(T) + " x"); args.add("x"); }

        List<String> L = new ArrayList<String>();
        L.add("import unit4.collectionsLib.*;");
        L.add("");
        L.add("/**");
        L.add(" * תרגיל: " + st.label + " של " + T);
        L.add(" * 1. כותבים את הפעולה " + o.functionName + " למטה.");
        L.add(" * 2. מריצים (הרצה ויזואלית / Run) — ייפתח חלון שמראה כל צעד.");
        L.add(" * אפשר לשנות את הערכים ב-main כדי לבדוק מקרים נוספים.");
        L.add(" */");
        L.add("public class " + o.className + " {");
        L.add("");
        L.add("    /**");
        L.add("     * TODO: כאן כותבים מה הפעולה צריכה לעשות");
        L.add("     */");
        L.add("    public static " + retType + " " + o.functionName + "(" + String.join(", ", params) + ") {");
        L.add("        // תזכורת: " + st.hint.replace("{v}", v1).replace("{T}", T));
        if (T.equals("Student")) L.add("        // Student: getName()   getAverage()   getGrade(i)   getNumGrades()   getClassRoom()   isExcellent()");
        int cursorLine = L.size();
        L.add("        ");
        if (retDefault != null) L.add("        return " + retDefault + ";");
        L.add("    }");
        L.add("");
        L.add("    public static void main(String[] args) {");
        L.add(buildLine(st, full, v1, o.values, false, random, T, o.structure));
        if (o.second) L.add(buildLine(st, full, v2, o.values2, true, random, T, o.structure));
        if (o.extra) L.add("        " + extraType(T) + " x = " + extraValue(o) + ";");
        L.add("");
        L.add("        System.out.println(\"לפני:  " + v1 + " = \" + Viz.text(" + v1 + "));");
        if (o.second) L.add("        System.out.println(\"לפני:  " + v2 + " = \" + Viz.text(" + v2 + "));");
        String call = o.functionName + "(" + String.join(", ", args) + ")";
        if (o.returns.equals("void")) {
            L.add("        " + call + ";");
        } else {
            L.add("        " + retType + " result = " + call + ";");
            L.add(o.returns.equals("structure")
                    ? "        System.out.println(\"התוצאה: \" + Viz.text(result));"
                    : "        System.out.println(\"התוצאה: \" + result);");
        }
        L.add("        System.out.println(\"אחרי:  " + v1 + " = \" + Viz.text(" + v1 + "));");
        if (o.second) L.add("        System.out.println(\"אחרי:  " + v2 + " = \" + Viz.text(" + v2 + "));");
        L.add("    }");
        L.add("}");
        L.add("");
        return new Result(String.join("\n", L), cursorLine);
    }

    private static String buildLine(Structure st, String full, String v, String values, boolean second,
                                    boolean random, String T, String structure) {
        String note = st.note.replaceFirst("lst", v).replace("{v}", v);
        if (random) {
            int n = second ? 4 : 6;
            if (st.dims == 2) return "        " + full + " " + v + " = Build." + st.random + "(3, 4, 1, 20);   // 3 שורות, 4 עמודות, מספרים אקראיים";
            return "        " + full + " " + v + " = Build." + st.random + "(" + n + ", 1, 20);   // " + n + " מספרים אקראיים בין 1 ל-20";
        }
        if (T.equals("Student")) {
            if (st.dims == 2) matrixLiterals(values, T);
            int n = studentCount(values);
            return "        " + full + " " + v + " = Build." + st.build + "(Build.students(" + n + "));   // " + n + " תלמידים אקראיים (נתוני דמה)";
        }
        if (st.dims == 2) {
            List<List<String>> rows = matrixLiterals(values, T);
            if (rows.isEmpty()) return "        " + full + " " + v + " = new " + primitive(T) + "[0][0];";
            List<String> lines = new ArrayList<String>();
            for (List<String> r : rows) lines.add("                { " + String.join(", ", r) + " }");
            return "        " + full + " " + v + " = Build.matrix(new " + primitive(T) + "[][] {   // " + note + "\n"
                    + String.join(",\n", lines) + "\n        });";
        }
        List<String> lits = literals(values, T, structure.equals("tree"));
        if (st.dims == 1 && lits.isEmpty()) return "        " + full + " " + v + " = new " + primitive(T) + "[0];";
        return "        " + full + " " + v + " = Build." + st.build + "(" + String.join(", ", lits) + ");   // " + note;
    }

    /** טיפוס המבנה: Stack<Integer>, int[], int[][] */
    public static String typeOf(Structure st, String T) {
        if (st.dims == 1) return primitive(T) + "[]";
        if (st.dims == 2) return primitive(T) + "[][]";
        return st.cls + "<" + T + ">";
    }

    /** הפרמטר הנוסף x: מסוג הערכים, ולתלמידים — ציון (int) */
    static String extraType(String T) {
        return T.equals("Student") ? "int" : primitive(T);
    }

    private static String extraValue(Options o) {
        if (o.type.equals("Student")) return "80";
        if (o.structure.equals("matrix")) {
            List<String> cells = new ArrayList<String>();
            for (List<String> r : matrixLiterals(o.values, o.type)) cells.addAll(r);
            return !cells.isEmpty() ? cells.get(0) : zero(o.type);
        }
        if (o.type.equals("Integer")) {
            if (o.random) return "8";
            List<String> lits = withoutNull(literals(o.values, "Integer", true));
            return lits.size() > 2 ? lits.get(2) : !lits.isEmpty() ? lits.get(0) : "8";
        }
        List<String> lits = withoutNull(literals(o.values, o.type, true));
        return !lits.isEmpty() ? lits.get(0) : zero(o.type);
    }

    // ------------------------------------------------------------ "הוספה לקוד": מבנה עם נתוני דמה

    /** מחולל אקראי פשוט וזהה ב-JS וב-Java (כדי שאפשר לבדוק ששני התוספים מפיקים אותו קוד) */
    static final class Rng {
        private int s;

        Rng(int seed) { s = seed; }

        int next(int n) {
            s = s * 1103515245 + 12345;
            return (s >>> 1) % n;
        }
    }

    static final String[] WORDS = { "apple", "sun", "dog", "cat", "tree", "book", "star", "moon", "fish", "bird",
            "rain", "milk", "car", "ball", "cake", "lion", "rose", "snow", "wind", "gold" };

    public static final Map<String, String> SNIPPETS = new LinkedHashMap<String, String>();
    static {
        SNIPPETS.put("stack", "מחסנית");
        SNIPPETS.put("queue", "תור");
        SNIPPETS.put("list", "רשימה מקושרת");
        SNIPPETS.put("tree", "עץ בינארי");
        SNIPPETS.put("bst", "עץ חיפוש בינארי");
        SNIPPETS.put("array", "מערך");
        SNIPPETS.put("matrix", "מערך דו-ממדי");
        SNIPPETS.put("student", "תלמיד");
    }

    static String randomValue(String type, Rng r) {
        switch (type) {
            case "Integer": return String.valueOf(1 + r.next(99));
            case "Double": { int t = 5 + r.next(990); return (t / 10) + "." + (t % 10); }
            case "Character": return "'" + (char) ('a' + r.next(26)) + "'";
            default: return "\"" + WORDS[r.next(WORDS.length)] + "\"";
        }
    }

    /** הבחירות להוספת מבנה לקוד */
    public static final class SnippetOptions {
        public String structure = "queue", type = "Integer", name = "q", indent = "";
        public int size = 5, rows = 3, cols = 4, seed = 1;
    }

    /** פקודה אחת שיוצרת מבנה עם נתוני דמה, להכנסה במקום הסמן */
    public static String snippet(SnippetOptions o) {
        String indent = o.indent == null ? "" : o.indent;
        Rng r = new Rng(o.seed);
        String name = o.name;
        if (!isIdentifier(name)) throw new IllegalArgumentException("שם המשתנה חייב להיות באנגלית, בלי רווחים, ולהתחיל באות");
        if (o.structure.equals("student")) return indent + "Student " + name + " = Student.random();   // תלמיד עם נתוני דמה (שם, כיתה, ציונים)";
        Structure st = STRUCTURES.get(o.structure);
        if (st == null) throw new IllegalArgumentException("מבנה לא מוכר: " + o.structure);
        String T = o.type;
        if (!TYPES.contains(T)) throw new IllegalArgumentException("טיפוס לא מוכר: " + T);
        String full = typeOf(st, T);
        int size = Math.max(1, Math.min(o.size, 30));
        if (T.equals("Student")) {
            if (st.dims == 2) throw new IllegalArgumentException("אין מערך דו-ממדי של תלמידים — בחרו מערך רגיל");
            return indent + full + " " + name + " = Build." + st.build + "(Build.students(" + size + "));   // " + size + " תלמידים עם נתוני דמה";
        }
        if (st.dims == 2) {
            int rows = Math.max(1, Math.min(o.rows, 12)), cols = Math.max(1, Math.min(o.cols, 12));
            List<String> lines = new ArrayList<String>();
            for (int i = 0; i < rows; i++) {
                List<String> cells = new ArrayList<String>();
                for (int c = 0; c < cols; c++) cells.add(randomValue(T, r));
                lines.add(indent + "        { " + String.join(", ", cells) + " }");
            }
            return indent + full + " " + name + " = Build.matrix(new " + primitive(T) + "[][] {   // " + rows + " שורות, " + cols + " עמודות\n"
                    + String.join(",\n", lines) + "\n" + indent + "});";
        }
        List<String> values = new ArrayList<String>();
        Set<String> used = new HashSet<String>();
        while (values.size() < size) {
            String v = randomValue(T, r);
            if (o.structure.equals("bst") && used.contains(v) && used.size() < 90) continue;   // בעץ חיפוש — בלי כפילויות
            used.add(v);
            values.add(v);
        }
        String what;
        switch (o.structure) {
            case "stack": what = "האחרון בראש המחסנית"; break;
            case "queue": what = "הראשון בראש התור"; break;
            case "list": what = "החוליה הראשונה: " + name; break;
            case "tree": what = "לפי רמות"; break;
            case "bst": what = "הכנסה לפי הסדר"; break;
            default: what = "מוצג בחלון";
        }
        return indent + full + " " + name + " = Build." + st.build + "(" + String.join(", ", values) + ");   // " + what;
    }

    /** שם ברירת המחדל למשתנה */
    public static String snippetName(String structure) {
        return structure.equals("student") ? "st" : STRUCTURES.get(structure).varName;
    }

    private static List<String> withoutNull(List<String> l) {
        List<String> r = new ArrayList<String>();
        for (String s : l) if (!s.equals("null")) r.add(s);
        return r;
    }
}
