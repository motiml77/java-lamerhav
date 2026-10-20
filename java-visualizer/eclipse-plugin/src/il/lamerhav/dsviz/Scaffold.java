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
                "החוליה הראשונה היא {v}",
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

    public static final List<String> TYPES = Arrays.asList("Integer", "String", "Character", "Double", "Student", "Car");

    public static String primitive(String type) {
        switch (type) {
            case "Integer": return "int";
            case "Character": return "char";
            case "Double": return "double";
            case "Student": return "Student";
            case "Car": return "Car";
            default: return "String";
        }
    }

    static String zero(String type) {
        switch (type) {
            case "Integer": return "0";
            case "Character": return "' '";
            case "Double": return "0.0";
            case "String": return "\"\"";
            default: return "null";   // Student, Car
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
            case "Student": case "Car": d = new String[] { "4", "3", "5", "5" }; break;
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

    /** טיפוסים של אובייקטים מוכנים (Student, Car): כותבים כמה ליצור — הם נוצרים בהרצה עם נתוני דמה */
    static boolean isObj(String T) {
        return T.equals("Student") || T.equals("Car");
    }

    static String fresh(String T) {
        return T.equals("Car") ? "Car.random()" : "Student.random()";
    }

    static String freshNote(String T) {
        return T.equals("Car") ? "מכונית עם נתוני דמה" : "תלמיד עם נתוני דמה";
    }

    /** כמה אובייקטים ליצור (מספר) */
    public static int studentCount(String text) {
        String t = text == null ? "" : text.trim();
        if (!t.matches("\\d{1,2}") || Integer.parseInt(t) < 1 || Integer.parseInt(t) > 20)
            throw new IllegalArgumentException("כאן כותבים כמה ליצור — מספר בין 1 ל-20");
        return Integer.parseInt(t);
    }

    /** "1, 2; 3, 4" → [[1, 2], [3, 4]] */
    public static List<List<String>> matrixLiterals(String text, String type) {
        if (isObj(type)) throw new IllegalArgumentException("אין מערך דו-ממדי של תלמידים או מכוניות — בחרו מערך רגיל");
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
        if (isObj(type)) {
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
        public String structure = "stack", type = "Integer", returns = "void";
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
        boolean random = o.random && o.type.equals("Integer") && !o.structure.equals("tree") && !o.structure.equals("bst");
        String T = o.type;
        String full = typeOf(st, T);
        String v1 = st.varName, v2 = st.varName + "2";
        String prim = primitive(T);

        // retStart = הערך ההתחלתי של המשתנה result (רק בפעולה שמחזירה ערך)
        String retType, retStart = null;
        switch (o.returns) {
            case "void": retType = "void"; break;
            case "int": retType = "int"; retStart = "0"; break;
            case "boolean": retType = "boolean"; retStart = "false"; break;
            case "element": retType = prim; retStart = zero(T); break;
            case "structure": retType = full; retStart = emptyOf(st, T, v1); break;
            default: throw new IllegalArgumentException("ערך החזרה לא מוכר: " + o.returns);
        }

        List<String> params = new ArrayList<String>(), args = new ArrayList<String>();
        params.add(full + " " + v1);
        args.add(v1);
        if (o.second) { params.add(full + " " + v2); args.add(v2); }
        if (o.extra) { params.add(extraType(T) + " x"); args.add("x"); }

        List<String> L = new ArrayList<String>();
        L.add("import unit4.collectionsLib.*;");
        if (st.dims > 0) L.add("import java.util.Arrays;");
        L.add("");
        L.add("// תרגיל: " + st.label + " של " + T);
        L.add("public class " + o.className + " {");
        L.add("");
        L.add("    public static " + retType + " " + o.functionName + "(" + String.join(", ", params) + ") {");
        L.add("        // תזכורת: " + st.hint.replace("{v}", v1).replace("{T}", T));
        if (T.equals("Student")) L.add("        // Student: getName()   getAverage()   getGrade(i)   getNumGrades()   getClassRoom()   isExcellent()");
        if (T.equals("Car")) L.add("        // Car: getManufacturer()   getYear()   getKm()   getFuel()   drive(km)   refuel(l)   isOld()   needsFuel()");
        if (retStart != null) L.add("        " + retType + " result = " + retStart + ";   // כאן אוספים את מה שהפעולה מחזירה");
        L.add("");
        L.add("        // TODO: כאן כותבים את הפתרון (אפשר לעבוד עם לולאה — וכל מה שבתזכורת)");
        int cursorLine = L.size();
        L.add("        ");
        if (retStart != null) {
            L.add("");
            L.add("        return result;");
        }
        L.add("    }");
        L.add("");
        L.add("");
        L.add("");
        L.add("    // ==================== מכאן והלאה: מעטפת מוכנה (בדיקה של הפעולה) ====================");
        L.add("    // אין צורך לשנות — אבל אפשר לשנות את הערכים כדי לבדוק מקרים נוספים");
        L.add("");
        L.add("    public static void main(String[] args) {");
        // עצים אין בהם "ערכים אקראיים" — אם סימנו, משתמשים בערכי ברירת המחדל
        String vals1 = o.values.isEmpty() && o.random ? defaultValues(o.structure, T, false) : o.values;
        String vals2 = o.values2.isEmpty() && o.random ? defaultValues(o.structure, T, true) : o.values2;
        L.add("        // בונים את המבנה לבדיקה — אפשר לשנות את הערכים");
        L.addAll(buildLines(st, T, v1, vals1, false, random, o.structure));
        if (o.second) L.addAll(buildLines(st, T, v2, vals2, true, random, o.structure));
        if (o.extra) L.add("        " + extraType(T) + " x = " + extraValue(o) + ";");
        L.add("");
        if (printable(st, v1) != null) L.add("        // מדפיסים את המבנה לפני הקריאה לפעולה");
        show(L, st, "לפני", v1, o.second);
        if (o.second) show(L, st, "לפני", v2, true);
        L.add("        // קוראים לפעולה שכתבתם");
        String call = o.functionName + "(" + String.join(", ", args) + ")";
        if (o.returns.equals("void")) {
            L.add("        " + call + ";");
        } else {
            L.add("        " + retType + " result = " + call + ";");
            String p = o.returns.equals("structure") ? printable(st, "result") : "result";
            if (p != null) L.add("        System.out.println(\"התוצאה: \" + " + p + ");");
        }
        if (printable(st, v1) != null) L.add("        // מדפיסים את המבנה אחרי — מה השתנה?");
        show(L, st, "אחרי", v1, o.second);
        if (o.second) show(L, st, "אחרי", v2, true);
        L.add("    }");
        L.add("}");
        L.add("");
        return new Result(String.join("\n", L), cursorLine);
    }

    private static void show(List<String> L, Structure st, String title, String v, boolean second) {
        String p = printable(st, v);
        if (p != null) L.add("        System.out.println(\"" + title + ": " + (second ? " " + v + " = " : "") + "\" + " + p + ");");
    }

    /** השורות ב-main שבונות את המבנה — בסגנון שלומדים בכיתה (new + push / insert, מערך עם {...}) */
    private static List<String> buildLines(Structure st, String T, String v, String values, boolean second,
                                           boolean random, String structure) {
        String full = typeOf(st, T), prim = primitive(T);
        String add = "Stack".equals(st.cls) ? "push" : "insert";
        int n = second ? 4 : 6;
        String RANDOM = "(int) (Math.random() * 20) + 1";
        List<String> L = new ArrayList<String>();
        if ("Stack".equals(st.cls) || "Queue".equals(st.cls)) {
            L.add("        " + full + " " + v + " = new " + full + "();");
            if (random || isObj(T)) {
                int count = random ? n : studentCount(values);
                L.add("        for (int i = 0; i < " + count + "; i++) {");
                L.add(random ? "            " + v + "." + add + "(" + RANDOM + ");   // מספר אקראי בין 1 ל-20"
                             : "            " + v + "." + add + "(" + fresh(T) + ");   // " + freshNote(T));
                L.add("        }");
                return L;
            }
            List<String> lits = literals(values, T, false);
            for (int i = 0; i < lits.size(); i++) {
                String note = "Stack".equals(st.cls) ? (i == lits.size() - 1 ? "   // האחרון שנכנס — בראש המחסנית" : "")
                                                     : (i == 0 ? "   // הראשון שנכנס — בראש התור" : "");
                L.add("        " + v + "." + add + "(" + lits.get(i) + ");" + note);
            }
            return L;
        }
        if (st.dims == 1) {
            if (random || isObj(T)) {
                int count = random ? n : studentCount(values);
                L.add("        " + full + " " + v + " = new " + prim + "[" + count + "];");
                L.add("        for (int i = 0; i < " + v + ".length; i++) {");
                L.add(random ? "            " + v + "[i] = " + RANDOM + ";   // מספר אקראי בין 1 ל-20"
                             : "            " + v + "[i] = " + fresh(T) + ";   // " + freshNote(T));
                L.add("        }");
                return L;
            }
            List<String> lits = literals(values, T, false);
            L.add(lits.isEmpty() ? "        " + full + " " + v + " = new " + prim + "[0];"
                                 : "        " + full + " " + v + " = {" + String.join(", ", lits) + "};");
            return L;
        }
        if (st.dims == 2) {
            if (random) {
                L.add("        " + full + " " + v + " = new " + prim + "[3][4];   // 3 שורות, 4 עמודות");
                L.add("        for (int i = 0; i < " + v + ".length; i++) {");
                L.add("            for (int j = 0; j < " + v + "[i].length; j++) {");
                L.add("                " + v + "[i][j] = " + RANDOM + ";   // מספר אקראי בין 1 ל-20");
                L.add("            }");
                L.add("        }");
                return L;
            }
            List<List<String>> rows = matrixLiterals(values, T);
            if (rows.isEmpty()) {
                L.add("        " + full + " " + v + " = new " + prim + "[0][0];");
                return L;
            }
            L.add("        " + full + " " + v + " = {");
            for (int i = 0; i < rows.size(); i++)
                L.add("            {" + String.join(", ", rows.get(i)) + "}" + (i < rows.size() - 1 ? "," : ""));
            L.add("        };");
            return L;
        }
        // רשימה מקושרת ועצים: new + setNext / setLeft / setRight
        if ("Node".equals(st.cls) && random) {
            L.add("        " + full + " " + v + " = null;");
            L.add("        for (int i = 0; i < " + n + "; i++) {");
            L.add("            " + v + " = new " + full + "(" + RANDOM + ", " + v + ");   // מוסיף בתחילת הרשימה, מספר אקראי בין 1 ל-20");
            L.add("        }");
            return L;
        }
        List<String> elems = new ArrayList<String>();
        if (isObj(T)) {
            for (int i = studentCount(values); i > 0; i--) elems.add(fresh(T));
        } else {
            elems = literals(values, T, structure.equals("tree"));
        }
        for (String l : nodeLines(structure, T, v, elems)) L.add("        " + l);
        return L;
    }

    /** צומת בעץ — לבניית שורות הקוד */
    private static final class TNode {
        final String v;
        TNode l, r;
        TNode(String v) { this.v = v; }
    }

    private static TNode treeAt(List<String> e, int i) {
        if (i >= e.size() || e.get(i).equals("null")) return null;
        TNode n = new TNode(e.get(i));
        n.l = treeAt(e, 2 * i + 1);
        n.r = treeAt(e, 2 * i + 2);
        return n;
    }

    private static TNode insert(TNode t, String x, boolean numeric) {
        if (t == null) return new TNode(x);
        int c = numeric ? Double.compare(Double.parseDouble(x), Double.parseDouble(t.v)) : x.compareTo(t.v);
        if (c < 0) t.l = insert(t.l, x, numeric); else t.r = insert(t.r, x, numeric);
        return t;
    }

    private static void walk(TNode n, String path, String type, List<String> L) {
        if (n.l != null) { L.add(path + ".setLeft(new " + type + "(" + n.l.v + "));"); walk(n.l, path + ".getLeft()", type, L); }
        if (n.r != null) { L.add(path + ".setRight(new " + type + "(" + n.r.v + "));"); walk(n.r, path + ".getRight()", type, L); }
    }

    /**
     * רשימה מקושרת ועצים בסגנון שלומדים בכיתה, בלי Build:
     *   רשימה — מוסיפים כל פעם בתחילת הרשימה (לכן מתחילים מהאיבר האחרון)
     *   עץ — צומת שורש, ואז setLeft / setRight לכל צומת (עץ חיפוש: לפי סדר ההכנסה)
     * elems = ביטויים (ערכים, או Student.random()); בעץ לפי רמות, "null" = אין צומת. מחזיר שורות בלי הזחה.
     */
    static List<String> nodeLines(String structure, String T, String v, List<String> elems) {
        List<String> L = new ArrayList<String>();
        if (structure.equals("list")) {
            String type = "Node<" + T + ">";
            if (elems.isEmpty()) { L.add(type + " " + v + " = null;   // רשימה ריקה"); return L; }
            for (int i = elems.size() - 1; i >= 0; i--) {
                if (i == elems.size() - 1) L.add(type + " " + v + " = new " + type + "(" + elems.get(i) + ");   // מתחילים מהחוליה האחרונה");
                else L.add(v + " = new " + type + "(" + elems.get(i) + ", " + v + ");" + (i == 0 ? "   // החוליה הראשונה" : ""));
            }
            return L;
        }
        String type = "BinNode<" + T + ">";
        boolean numeric = T.equals("Integer") || T.equals("Double");
        TNode root = null;
        if (structure.equals("bst") && !isObj(T)) {
            for (String x : elems) root = insert(root, x, numeric);
        } else {
            root = treeAt(elems, 0);
        }
        if (root == null) { L.add(type + " " + v + " = null;   // עץ ריק"); return L; }
        L.add(type + " " + v + " = new " + type + "(" + root.v + ");   // השורש");
        walk(root, v, type, L);
        return L;
    }

    /** איך מדפיסים את המבנה ב-main (null = לא מדפיסים — עץ רואים בחלון) */
    private static String printable(Structure st, String v) {
        if (st.dims == 1) return "Arrays.toString(" + v + ")";
        if (st.dims == 2) return "Arrays.deepToString(" + v + ")";
        return "BinNode".equals(st.cls) ? null : v;
    }

    /** הערך ההתחלתי של result בפעולה שמחזירה מבנה */
    private static String emptyOf(Structure st, String T, String v) {
        String prim = primitive(T);
        if ("Stack".equals(st.cls) || "Queue".equals(st.cls)) return "new " + typeOf(st, T) + "()";
        if (st.dims == 1) return "new " + prim + "[" + v + ".length]";
        if (st.dims == 2) return "new " + prim + "[" + v + ".length][" + v + "[0].length]";
        return "null";
    }

    /** טיפוס המבנה: Stack<Integer>, int[], int[][] */
    public static String typeOf(Structure st, String T) {
        if (st.dims == 1) return primitive(T) + "[]";
        if (st.dims == 2) return primitive(T) + "[][]";
        return st.cls + "<" + T + ">";
    }

    /** הפרמטר הנוסף x: מסוג הערכים, ולתלמידים — ציון (int) */
    static String extraType(String T) {
        return isObj(T) ? "int" : primitive(T);   // תלמיד: ציון, מכונית: שנה
    }

    private static String extraValue(Options o) {
        if (o.type.equals("Student")) return "80";
        if (o.type.equals("Car")) return "2015";
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
        SNIPPETS.put("car", "מכונית");
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
        if (o.structure.equals("car")) return indent + "Car " + name + " = Car.random();   // מכונית עם נתוני דמה (יצרן, דגם, שנה, דלק)";
        Structure st = STRUCTURES.get(o.structure);
        if (st == null) throw new IllegalArgumentException("מבנה לא מוכר: " + o.structure);
        String T = o.type;
        if (!TYPES.contains(T)) throw new IllegalArgumentException("טיפוס לא מוכר: " + T);
        String full = typeOf(st, T);
        int size = Math.max(1, Math.min(o.size, 30));
        String add = "Stack".equals(st.cls) ? "push" : "insert";
        boolean linear = "Stack".equals(st.cls) || "Queue".equals(st.cls);
        List<String> L = new ArrayList<String>();
        if (isObj(T)) {
            if (st.dims == 2) throw new IllegalArgumentException("אין מערך דו-ממדי של תלמידים או מכוניות — בחרו מערך רגיל");
            if (linear) {
                L.add(full + " " + name + " = new " + full + "();");
                L.add("for (int i = 0; i < " + size + "; i++) {");
                L.add("    " + name + "." + add + "(" + fresh(T) + ");   // " + freshNote(T));
                L.add("}");
                return lines(indent, L);
            }
            if (st.dims == 1) {
                L.add(full + " " + name + " = new " + T + "[" + size + "];");
                L.add("for (int i = 0; i < " + name + ".length; i++) {");
                L.add("    " + name + "[i] = " + fresh(T) + ";   // " + freshNote(T));
                L.add("}");
                return lines(indent, L);
            }
            List<String> students = new ArrayList<String>();
            for (int i = 0; i < size; i++) students.add(fresh(T));
            return lines(indent, nodeLines(o.structure, T, name, students));
        }
        if (st.dims == 2) {
            int rows = Math.max(1, Math.min(o.rows, 12)), cols = Math.max(1, Math.min(o.cols, 12));
            L.add(full + " " + name + " = {   // " + rows + " שורות, " + cols + " עמודות");
            for (int i = 0; i < rows; i++) {
                List<String> cells = new ArrayList<String>();
                for (int c = 0; c < cols; c++) cells.add(randomValue(T, r));
                L.add("    {" + String.join(", ", cells) + "}" + (i < rows - 1 ? "," : ""));
            }
            L.add("};");
            return lines(indent, L);
        }
        List<String> values = new ArrayList<String>();
        Set<String> used = new HashSet<String>();
        while (values.size() < size) {
            String v = randomValue(T, r);
            if (o.structure.equals("bst") && used.contains(v) && used.size() < 90) continue;   // בעץ חיפוש — בלי כפילויות
            used.add(v);
            values.add(v);
        }
        if (linear) {
            L.add(full + " " + name + " = new " + full + "();");
            for (int i = 0; i < values.size(); i++) {
                String note = "Stack".equals(st.cls) ? (i == values.size() - 1 ? "   // האחרון שנכנס — בראש המחסנית" : "")
                                                     : (i == 0 ? "   // הראשון שנכנס — בראש התור" : "");
                L.add(name + "." + add + "(" + values.get(i) + ");" + note);
            }
            return lines(indent, L);
        }
        if (st.dims == 1) return indent + full + " " + name + " = {" + String.join(", ", values) + "};";
        return lines(indent, nodeLines(o.structure, T, name, values));
    }

    private static String lines(String indent, List<String> L) {
        StringBuilder b = new StringBuilder();
        for (String l : L) b.append(b.length() == 0 ? "" : "\n").append(indent).append(l);
        return b.toString();
    }

    /** שם ברירת המחדל למשתנה */
    public static String snippetName(String structure) {
        return structure.equals("student") ? "st" : structure.equals("car") ? "car" : STRUCTURES.get(structure).varName;
    }

    private static List<String> withoutNull(List<String> l) {
        List<String> r = new ArrayList<String>();
        for (String s : l) if (!s.equals("null")) r.add(s);
        return r;
    }
}
