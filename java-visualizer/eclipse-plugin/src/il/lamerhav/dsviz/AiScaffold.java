package il.lamerhav.dsviz;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * "שליחת תרגיל": בונה את קובצי ה-Java (או ה-C#) מהתשובה של השרת (JSON בתבנית קבועה).
 * המודל רק מתאר את התרגיל (סעיפים, כותרות פעולות, מחלקות, main) — את הקוד עצמו בונים כאן, תמיד באותה צורה:
 *   לכל סעיף פעולה עם הסבר, תזכורת, "TODO: כאן כותבים את הפתרון" וערך החזרה זמני — כדי שהכול מתקמפל ורץ מיד;
 *   ובסוף המחלקה הראשית — main שבונה נתונים לדוגמה וקורא לכל הפעולות.
 * בלי תלות ב-Eclipse (נבדק גם מחוץ לו).
 */
final class AiScaffold {
    private AiScaffold() {}

    static final Set<String> LIBRARY = new HashSet<String>(Arrays.asList("Stack", "Queue", "Node", "BinNode", "BinTreeNode"));
    private static final Set<String> CS_KEYWORDS = new HashSet<String>(Arrays.asList(
            "abstract", "as", "base", "bool", "break", "byte", "case", "catch", "char", "checked", "class", "const", "continue", "decimal",
            "default", "delegate", "do", "double", "else", "enum", "event", "explicit", "extern", "false", "finally", "fixed", "float", "for",
            "foreach", "goto", "if", "implicit", "in", "int", "interface", "internal", "is", "lock", "long", "namespace", "new", "null", "object",
            "operator", "out", "override", "params", "private", "protected", "public", "readonly", "ref", "return", "sbyte", "sealed", "short",
            "sizeof", "stackalloc", "static", "string", "struct", "switch", "this", "throw", "true", "try", "typeof", "uint", "ulong", "unchecked",
            "unsafe", "ushort", "using", "virtual", "void", "volatile", "while", "var", "record", "Main", "Console", "Math", "String", "Object", "System"));
    private static final Set<String> KEYWORDS = new HashSet<String>(Arrays.asList(
            "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char", "class", "const", "continue", "default", "do",
            "double", "else", "enum", "extends", "final", "finally", "float", "for", "goto", "if", "implements", "import", "instanceof",
            "int", "interface", "long", "native", "new", "package", "private", "protected", "public", "return", "short", "static",
            "strictfp", "super", "switch", "synchronized", "this", "throw", "throws", "transient", "try", "void", "volatile", "while",
            "true", "false", "null", "var", "record", "yield", "main", "String", "Object", "System", "Math", "Integer"));
    private static final Pattern IDENT = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");
    /** מזהה בקוד שהמודל כתב (גם בעברית — לפני שתוקן) */
    private static final Pattern WORD = Pattern.compile("[\\p{L}_$][\\p{L}\\p{N}_$]*");
    private static final Pattern TYPE = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*(\\s*<[A-Za-z0-9_<>,\\s\\[\\]?]*>)?(\\s*\\[[,\\s]*\\])*");
    private static final Pattern IMPORT = Pattern.compile("java\\.util(\\.[a-z]+)*\\.([A-Z][A-Za-z0-9_]*|\\*)");
    private static final Pattern USING = Pattern.compile("System(\\.[A-Z][A-Za-z0-9]*)+");
    static final String MARK = "====" + rtl("כאן כותבים את התשובה") + "====";
    /** רק לבדיקות: שורת return חשופה בסוף פעולה שמחזירה ערך, כדי שהקוד יתקמפל (לתלמיד לא מוסיפים — הוא כותב בעצמו) */
    static boolean testReturns = false;

    /** שורת הסימון בתוך הפעולה: כשיש כמה סעיפים — "כאן עונים סעיף א'", אחרת "כאן כותבים את התשובה" */
    static String markFor(Task t, boolean several) {
        if (!several) return MARK;
        String l = t.label == null ? "" : t.label.trim();
        if (l.matches("[א-ת]")) l += "'";
        return "====" + rtl("כאן עונים סעיף " + l) + "====";
    }

    // ------------------------------------------------------------------ המודל של התרגיל

    static final class Task {
        boolean cs;
        String label = "", kind = "method", className = "", returnType = "void", methodName = "", instructions = "";
        /** ההצעה לפתרון (הפעולה כולה — כותרת וגוף). לא נכנסת לקוד של התלמיד: נשמרת בצד ונפתחת רק בלחיצה על "הצעה לפתרון" */
        String solution = "";
        boolean isStatic = true;
        final List<String[]> params = new ArrayList<String[]>();   // {טיפוס, שם}

        boolean constructor() { return "constructor".equals(kind); }
    }

    static final class Exercise {
        boolean cs;   // C# (אחרת Java)
        String title = "", summary = "", topic = "", mainClass = "", mainCode = "";
        String expectedOutput = "";   // מה main מדפיס כשכל הסעיפים פתורים נכון
        final List<String> imports = new ArrayList<String>();
        final Map<String, String> classes = new LinkedHashMap<String, String>();   // שם → קוד
        final List<Task> tasks = new ArrayList<Task>();
        final List<String[]> given = new ArrayList<String[]>();    // {מחלקה, קוד}
        final List<String[]> others = new ArrayList<String[]>();   // {סעיף, טקסט}
    }

    static final class GeneratedFile {
        final String className, code;
        final int cursorLine;   // השורה (מ-0) שבה הסמן מחכה — או -1
        final boolean cs;

        GeneratedFile(String className, String code, int cursorLine, boolean cs) {
            this.className = className;
            this.code = code;
            this.cursorLine = cursorLine;
            this.cs = cs;
        }

        String fileName() { return className + (cs ? ".cs" : ".java"); }
    }

    /** קורא את התשובה של השרת ומנקה אותה: שמות חוקיים, טיפוסים חוקיים, בלי כפילויות */
    /**
     * משפט בעברית בתוך הערה בעורך (שכיוונו משמאל לימין): עטוף בסימון כיוון (RLE ... PDF), כדי שהמשפט יוצג מימין לשמאל
     * כמו שצריך, וסימני הפיסוק (נקודתיים, מקף, סוגריים) לא יקפצו לצד הלא נכון. הסימונים בלתי נראים, והקומפיילר מתעלם מהם בהערות.
     */
    static String rtl(String s) {
        return "\u202B" + s + "\u202C";
    }

    /** הערה לתלמיד מעל main: מה צריך להיות מודפס על המסך אם הפתרון נכון (לפי התשובה של Gemini) */
    static List<String> expectedComment(String expected) {
        List<String> out = new ArrayList<String>();
        if (expected == null || expected.trim().isEmpty()) return out;
        out.add("    //");
        out.add("    // " + rtl("אם פתרת נכון את התרגיל, צריך להיות מודפס על המסך:"));
        String[] lines = expected.split("\n");
        for (int i = 0; i < lines.length && i < 40; i++) out.add("    //     " + rtl(lines[i].replace("\t", "    ")));
        if (lines.length > 40) out.add("    //     ...");
        return out;
    }

    static Exercise read(Map<String, Object> j) {
        Exercise e = new Exercise();
        e.cs = "csharp".equals(Json.str(j.get("language")));
        final boolean cs = e.cs;
        e.title = oneLine(Json.str(j.get("title")), 120);
        e.summary = Json.str(j.get("summary")).trim();
        e.topic = Json.str(j.get("topic"));
        if (e.topic.isEmpty()) e.topic = Json.str(j.get("packageName"));
        e.mainCode = Json.str(j.get("mainCode"));
        e.expectedOutput = Json.str(j.get("expectedOutput")).replace("\r", "").replaceAll("\\s+$", "");
        for (Object o : Json.arr(j.get("imports"))) {
            String s = Json.str(o).trim().replaceFirst("^(import|using)\\s+", "").replaceFirst(";$", "").trim();
            boolean ok = cs ? USING.matcher(s).matches() && !s.startsWith("System.Collections.Generic") : IMPORT.matcher(s).matches();
            if (ok && !e.imports.contains(s)) e.imports.add(s);
        }
        // המחלקות מהתרגיל (לא של הספרייה)
        for (Object o : Json.arr(j.get("classes"))) {
            Map<String, Object> c = Json.obj(o);
            String name = ident(Json.str(c.get("name")), "", cs);
            if (name.isEmpty() || LIBRARY.contains(name) || e.classes.containsKey(name)) continue;
            e.classes.put(name, Json.str(c.get("code")));
        }
        String main = ident(Json.str(j.get("mainClass")), "Targil", cs);
        if (LIBRARY.contains(main)) main = main + "Targil";
        e.mainClass = main;

        Set<String> signatures = new HashSet<String>();
        int n = 0;
        for (Object o : Json.arr(j.get("tasks"))) {
            Map<String, Object> m = Json.obj(o);
            Task t = new Task();
            t.cs = cs;
            n++;
            t.label = oneLine(Json.str(m.get("label")), 20);
            if (t.label.isEmpty()) t.label = String.valueOf(n);
            t.kind = "constructor".equals(Json.str(m.get("kind"))) ? "constructor" : "method";
            t.className = ident(Json.str(m.get("className")), main, cs);
            if (LIBRARY.contains(t.className)) t.className = main;
            if (!t.className.equals(main) && !e.classes.containsKey(t.className)) {
                e.classes.put(t.className, "");   // מחלקה שהסעיף שייך אליה אבל לא תוארה — נוצרת, עם הערה להוסיף את התכונות מהתרגיל
            }
            t.isStatic = !Boolean.FALSE.equals(m.get("isStatic")) && !t.constructor();
            if (t.className.equals(main) && !t.constructor()) t.isStatic = true;   // במחלקה הראשית: פעולות סטטיות שנקראות מ-main
            t.instructions = Json.str(m.get("instructions")).trim();
            Set<String> pnames = new HashSet<String>();
            for (Object po : Json.arr(m.get("parameters"))) {
                Map<String, Object> p = Json.obj(po);
                String type = type(Json.str(p.get("type")), cs);
                if (type == null) type = "int";
                String name = ident(Json.str(p.get("name")), "p" + (t.params.size() + 1), cs);
                while (pnames.contains(name)) name = name + "2";
                pnames.add(name);
                t.params.add(new String[] { type, name });
            }
            if (t.constructor()) {
                t.methodName = t.className;
                t.returnType = "";
            } else {
                t.methodName = ident(Json.str(m.get("methodName")), (cs ? "Task" : "task") + n, cs);
                String rt = Json.str(m.get("returnType")).trim();
                t.returnType = rt.isEmpty() ? "void" : rt.equals("void") ? "void" : type(rt, cs);
                if (t.returnType == null) t.returnType = "void";
                if (t.methodName.equals(t.className)) t.methodName = (cs ? "Do" : "do") + t.methodName;
            }
            // אותה חתימה פעמיים — שם אחר לשנייה
            String sig = t.className + "." + t.methodName + "(" + paramTypes(t) + ")";
            for (int k = 2; signatures.contains(sig); k++) {
                if (t.constructor()) break;
                t.methodName = t.methodName.replaceAll("\\d+$", "") + k;
                sig = t.className + "." + t.methodName + "(" + paramTypes(t) + ")";
            }
            if (signatures.contains(sig)) continue;   // בנאי כפול
            signatures.add(sig);
            t.solution = fitSolution(t, Json.str(m.get("solution")), Json.str(m.get("methodName")).trim());
            e.tasks.add(t);
        }
        for (Object o : Json.arr(j.get("givenCode"))) {
            Map<String, Object> g = Json.obj(o);
            String code = Json.str(g.get("code")).trim();
            if (code.isEmpty()) continue;
            String cls = ident(Json.str(g.get("className")), main, cs);
            if (!cls.equals(main) && !e.classes.containsKey(cls)) cls = main;
            e.given.add(new String[] { cls, code });
        }
        for (Object o : Json.arr(j.get("otherQuestions"))) {
            Map<String, Object> q = Json.obj(o);
            String text = Json.str(q.get("text")).trim();
            if (!text.isEmpty()) e.others.add(new String[] { oneLine(Json.str(q.get("label")), 20), text });
        }
        if (e.classes.containsKey(main)) {   // המחלקה הראשית תוארה כמחלקה בתרגיל — הקוד שלה הוא הבסיס
            String code = e.classes.remove(main);
            e.classes.put(main, code);
        }
        return e;
    }

    // ------------------------------------------------------------------ ההודעה לתלמיד אחרי שהתרגיל נוצר

    static final String NO_CODE = "בתרגיל אין סעיפים שכותבים בהם קוד.";
    static final String OTHERS_HEAD = "סעיפים שעונים עליהם במילים (בלי קוד):";

    /** "2 סעיפים בקוד" / "סעיף אחד בקוד" */
    static String codeTasks(int n) {
        return (n == 1 ? "סעיף אחד" : n + " סעיפים") + " בקוד";
    }

    /**
     * בקוד שנוצר אין את הטקסט של התרגיל — אז הסעיפים שעונים עליהם במילים (otherQuestions, למשל "מהי סיבוכיות זמן הריצה? נמקו.")
     * מופיעים בהודעה שהתלמיד רואה מיד אחרי שהתרגיל נוצר; וגם כשאין בכלל סעיפים בקוד, ההודעה אומרת את זה.
     * "" — אין מה להוסיף (יש סעיפים בקוד, ואין סעיפים במילים). אותו נוסח גם ב-VS Code (submit.js) וב-IntelliJ (Submitter).
     */
    static String othersText(int tasks, List<String[]> others) {
        StringBuilder b = new StringBuilder();
        if (tasks == 0) b.append(NO_CODE);
        if (others != null && !others.isEmpty()) {
            if (b.length() > 0) b.append("\n\n");
            b.append(OTHERS_HEAD);
            for (String[] o : others) b.append('\n').append(otherLine(o[0], o[1]));
        }
        return b.toString();
    }

    /** "ג — מהי סיבוכיות זמן הריצה...": שורה אחת, ועד 200 תווים (ואז "…") */
    static String otherLine(String label, String text) {
        String t = text == null ? "" : text.replaceAll("\\s+", " ").trim();
        if (t.length() > 200) {
            int cut = t.lastIndexOf(' ', 200);
            t = t.substring(0, cut > 150 ? cut : 200).trim() + "…";
        }
        String l = label == null ? "" : label.trim();
        return l.isEmpty() ? t : l + " — " + t;
    }

    /**
     * שם התיקייה של התרגיל — לפי סוג התרגיל, עם מספר רץ, כדי שיהיה קל למצוא ולמחוק:
     *   Java: חבילה ex01_stacks, ex02_binary_trees...;  C#: פרויקט Ex01_Stacks, Ex02_BinaryTrees...
     * exists — השמות שכבר תפוסים (בתיקיית src, או בתיקיית התרגילים של C#)
     */
    static String folderName(String topic, Set<String> exists, boolean cs) {
        String t = topic == null ? "" : topic.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9_]", "_").replaceAll("_+", "_").replaceAll("^[_0-9]+|_+$", "");
        if (t.isEmpty()) t = "targil";
        if (t.length() > 30) t = t.substring(0, 30).replaceAll("_+$", "");
        int max = 0;
        Pattern num = Pattern.compile("(?i)ex(\\d+)_.*");
        Set<String> lower = new HashSet<String>();
        for (String x : exists) {
            lower.add(x.toLowerCase(java.util.Locale.ROOT));
            Matcher m = num.matcher(x);
            if (m.matches()) max = Math.max(max, Integer.parseInt(m.group(1)));
        }
        if (cs) {
            StringBuilder p = new StringBuilder();
            for (String w : t.split("_")) if (!w.isEmpty()) p.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
            t = p.toString();
        }
        for (int n = max + 1; ; n++) {
            String name = (cs ? "Ex" : "ex") + (n < 10 ? "0" : "") + n + "_" + t;
            if (!lower.contains(name.toLowerCase(java.util.Locale.ROOT))) return name;
        }
    }

    // ------------------------------------------------------------------ בניית הקבצים

    /** pkg — החבילה (ב-Java; ב-C# כל תרגיל הוא פרויקט משלו, בלי namespace). sources — שמות הקבצים ששלח התלמיד (להערה בראש הקובץ) */
    static List<GeneratedFile> build(Exercise e, String pkg, List<String> sources) {
        return build(e, pkg, sources, false);
    }

    /**
     * withSolutions — לבדיקה של ההצעות לפתרון בלבד (SolutionCheck, בתיקייה זמנית): בכל סעיף ההצעה במקום הפעולה הריקה.
     * הקבצים שהתלמיד מקבל נבנים תמיד בלי ההצעות.
     */
    static List<GeneratedFile> build(Exercise e, String pkg, List<String> sources, boolean withSolutions) {
        final boolean cs = e.cs;
        List<GeneratedFile> out = new ArrayList<GeneratedFile>();
        Set<String> all = new LinkedHashSet<String>(e.classes.keySet());
        all.add(e.mainClass);
        // קודם המחלקה הראשית, אחר כך מחלקות התרגיל
        List<String> order = new ArrayList<String>();
        order.add(e.mainClass);
        for (String c : all) if (!c.equals(e.mainClass)) order.add(c);
        Task firstTask = e.tasks.isEmpty() ? null : e.tasks.get(0);
        for (String cls : order) {
            boolean isMain = cls.equals(e.mainClass);
            StringBuilder b = new StringBuilder();
            if (cs) b.append("using System;\nusing Unit4.CollectionsLib;\n");
            else b.append("package ").append(pkg).append(";\n\n").append("import unit4.collectionsLib.*;\n");
            String code = e.classes.get(cls);
            StringBuilder uses = new StringBuilder(code == null ? "" : code);   // הקוד של הקובץ — כדי לייבא רק מה שבאמת בשימוש
            for (String[] g : e.given) if (g[0].equals(cls)) uses.append('\n').append(g[1]);
            for (Task t : e.tasks) {
                if (!t.className.equals(cls)) continue;
                uses.append('\n').append(t.returnType);
                for (String[] p : t.params) uses.append(' ').append(p[0]);
            }
            if (isMain) uses.append('\n').append(e.mainCode);
            for (String imp : e.imports) {
                if (cs) {   // ב-C# אי אפשר לדעת בקלות אם namespace בשימוש — מוסיפים לכל הקבצים
                    b.append("using ").append(imp).append(";\n");
                    continue;
                }
                String simple = imp.substring(imp.lastIndexOf('.') + 1);
                if (simple.equals("*") || Pattern.compile("\\b" + Pattern.quote(simple) + "\\b").matcher(uses).find())
                    b.append("import ").append(imp).append(";\n");
            }
            b.append('\n');

            List<String> body = new ArrayList<String>();   // השורות שנכנסות לגוף המחלקה (מוזחות ב-4)
            int[] cursor = { -1 };
            for (Task t : e.tasks) {
                if (!t.className.equals(cls)) continue;
                int at = body.size();
                int c = withSolutions ? solved(body, t) : stub(body, t, e.tasks.size() > 1);
                if (t == firstTask) cursor[0] = at + c;
            }
            boolean anyGiven = false;
            for (String[] g : e.given) {
                if (!g[0].equals(cls)) continue;
                if (!anyGiven) {
                    body.add("");
                    body.add("    // ---------- " + rtl("נתון בתרגיל — אין צורך לשנות") + " ----------");
                    anyGiven = true;
                }
                body.add("");
                for (String l : reindent(g[1], 4)) body.add(l);
            }
            boolean hasMain = code != null && code.matches(cs ? "(?s).*static\\s+(void|int)\\s+Main\\s*\\(.*" : "(?s).*static\\s+void\\s+main\\s*\\(.*");
            if (isMain && !hasMain) {
                body.add("");
                body.add("");
                body.add("");
                body.add("    // ==================== " + rtl("מכאן והלאה בדיקה של הפתרון") + " ====================");
                body.add("    // " + rtl("אין צורך לשנות — אפשר לשנות את הערכים כדי לבדוק מקרים נוספים"));
                body.addAll(expectedComment(e.expectedOutput));
                body.add("");
                if (cs) {
                    body.add("    public static void Main(string[] args)");
                    body.add("    {");
                } else body.add("    public static void main(String[] args) {");
                List<String> mc = reindent(stripMainWrapper(e.mainCode, cs), 8);
                if (mc.isEmpty()) mc.add("        // " + rtl("כאן בונים נתונים לדוגמה וקוראים לפעולות"));
                body.addAll(mc);
                body.add("    }");
            }

            // הרכבה: קוד המחלקה מהתרגיל (או מחלקה ריקה) + הגוף לפני הסוגר האחרון
            List<String> cls0 = classLines(cls, code, isMain, cs);
            int close = cls0.size() - 1;
            while (close > 0 && !cls0.get(close).trim().startsWith("}")) close--;
            for (int i = 0; i < close; i++) b.append(cls0.get(i)).append('\n');
            if (close > 1 && !cls0.get(close - 1).trim().isEmpty() && !body.isEmpty() && !body.get(0).isEmpty()) {
                b.append('\n');
            }
            for (String l : body) b.append(l).append('\n');
            for (int i = close; i < cls0.size(); i++) b.append(cls0.get(i)).append('\n');
            String text = tidy(b.toString());
            int cur = -1;
            if (cursor[0] >= 0) {   // הסמן: בשורה שמתחת לשורת הסימון הראשונה
                String[] ls = text.split("\n", -1);
                String mk = "// " + markFor(firstTask, e.tasks.size() > 1);
                for (int i = 0; i < ls.length; i++) if (ls[i].trim().equals(mk)) { cur = i + 1; break; }
            }
            out.add(new GeneratedFile(cls, text, cur, cs));
        }
        return out;
    }

    /** פעולה לסעיף אחד. מחזיר את מספר השורה (בתוך מה שנוסף) שבה מחכה הסמן */
    private static int stub(List<String> out, Task t, boolean several) {
        out.add("");
        if (t.cs) {   // סגנון C#: הסוגר בשורה משלו
            out.add("    " + header(t));
            out.add("    {");
        } else out.add("    " + header(t) + " {");
        // בתוך הפעולה — רק שורת הסימון, ומתחתיה שורה ריקה לסמן. בלי return: התלמיד כותב בעצמו. שאר ההערות רק ב-main.
        out.add("        // " + markFor(t, several));
        int cursor = out.size();
        out.add("        ");
        if (testReturns && !t.constructor() && !"void".equals(t.returnType)) out.add("        return " + defaultValue(t.returnType) + ";");
        out.add("    }");
        return cursor;
    }

    /** הפעולה עם ההצעה לפתרון (לבדיקה). סעיף בלי הצעה — פעולה ריקה שמחזירה ערך זמני, כדי שהכול יתקמפל */
    private static int solved(List<String> out, Task t) {
        if (t.solution.isEmpty()) {
            boolean keep = testReturns;
            testReturns = true;
            try {
                return stub(out, t, false);
            } finally {
                testReturns = keep;
            }
        }
        out.add("");
        out.addAll(reindent(t.solution, 4));
        return -1;
    }

    // ------------------------------------------------------------------ ההצעה לפתרון

    /**
     * ההצעה לפתרון של סעיף, מותאמת לפעולה שבמעטפת: הכותרת מוחלפת בכותרת של הסעיף בדיוק (שם, טיפוסים, static) — כמו שהתלמיד רואה אותה,
     * והגוף נשאר כמו שהמודל כתב. אם בהצעה הפעולה או הפרמטרים נקראים אחרת (למשל שם שתוקן כאן) — מחליפים את השם גם בגוף.
     * nameInJson — השם שהמודל נתן לפעולה (לפני התיקון). מחזיר "" כשאין הצעה שאפשר להשתמש בה.
     */
    static String fitSolution(Task t, String code, String nameInJson) {
        String c = code == null ? "" : code.replace("\r", "").replace("\t", "    ");
        Matcher fence = Pattern.compile("(?s)^\\s*```[A-Za-z#]*[ ]*\n(.*?)\\s*```\\s*$").matcher(c);
        if (fence.matches()) c = fence.group(1);
        c = String.join("\n", reindent(c, 0));
        if (c.isEmpty() || c.indexOf('{') < 0) return "";
        int[] depth = new int[c.length() + 1];
        boolean[] isCode = lex(c, depth);
        // הכותרת: השם של הפעולה ואחריו "(" — מחוץ לגוף (עומק 0), לא בהערה ולא אחרי new או נקודה
        int name = -1, nameEnd = -1;
        List<String> names = new ArrayList<String>();
        names.add(t.methodName);
        if (nameInJson != null && WORD.matcher(nameInJson).matches()) names.add(nameInJson);
        names.add(WORD.pattern());
        for (String n : names) {
            Matcher m = Pattern.compile("(?<![\\p{L}\\p{N}_$.])(" + (n.startsWith("[") ? n : Pattern.quote(n)) + ")\\s*\\(").matcher(c);
            while (m.find()) {
                int at = m.start(1);
                if (!isCode[at] || depth[at] != 0 || c.substring(0, at).matches("(?s).*\\bnew\\s*$")) continue;
                if (n.startsWith("[") && (KEYWORDS.contains(m.group(1)) || CS_KEYWORDS.contains(m.group(1)))) continue;
                name = at;
                nameEnd = m.end(1);
                break;
            }
            if (name >= 0) break;
        }
        if (name < 0) return "";
        int open = c.indexOf('(', nameEnd), close = -1, level = 0;
        for (int i = open; i < c.length(); i++) {
            if (!isCode[i]) continue;
            if (c.charAt(i) == '(') level++;
            else if (c.charAt(i) == ')' && --level == 0) { close = i; break; }
        }
        if (close < 0) return "";
        int brace = -1;
        for (int i = close + 1; i < c.length(); i++) {
            if (!isCode[i]) continue;
            char ch = c.charAt(i);
            if (ch == '{') { brace = i; break; }
            if (ch == ';' || ch == '}') return "";   // הכרזה בלי גוף
        }
        if (brace < 0) return "";
        // השורה של הכותרת מתחילה בתחילת השורה (או אחרי ; / } באותה שורה)
        int from = c.lastIndexOf('\n', name) + 1;
        for (int i = from; i < name; i++) if (isCode[i] && (c.charAt(i) == ';' || c.charAt(i) == '}')) from = i + 1;
        while (from < name && c.charAt(from) == ' ') from++;
        int lineStart = c.lastIndexOf('\n', from) + 1;
        String indent = c.substring(lineStart, from).trim().isEmpty() ? c.substring(lineStart, from) : "";

        // שמות שצריך להחליף בגוף: הפעולה (אם המודל קרא לה אחרת) והפרמטרים (לפי הסדר)
        Map<String, String> rename = new LinkedHashMap<String, String>();
        String oldName = c.substring(name, nameEnd);
        if (!t.constructor() && !oldName.equals(t.methodName)) rename.put(oldName, t.methodName);
        List<String> oldParams = paramNames(c.substring(open + 1, close));
        if (oldParams.size() == t.params.size()) {
            for (int i = 0; i < oldParams.size(); i++)
                if (!oldParams.get(i).equals(t.params.get(i)[1])) rename.put(oldParams.get(i), t.params.get(i)[1]);
        }
        String rest = c.substring(brace + 1);
        if (!rename.isEmpty()) rest = renameIdents(rest, isCode, depth, brace + 1, rename);
        StringBuilder b = new StringBuilder(c.substring(0, lineStart));
        b.append(indent).append(header(t));
        if (t.cs) b.append('\n').append(indent).append('{');
        else b.append(" {");
        b.append(rest);
        return String.join("\n", rtlComments(reindent(b.toString(), 0)));
    }

    /** הכותרת של הפעולה של הסעיף — בדיוק כמו במעטפת (בלי "{") */
    static String header(Task t) {
        StringBuilder sig = new StringBuilder("public ");
        if (t.isStatic) sig.append("static ");
        if (!t.constructor()) sig.append(t.returnType).append(' ');
        sig.append(t.methodName).append('(');
        for (int i = 0; i < t.params.size(); i++) {
            if (i > 0) sig.append(", ");
            sig.append(t.params.get(i)[0]).append(' ').append(t.params.get(i)[1]);
        }
        return sig.append(')').toString();
    }

    /**
     * סימון לכל תו בקוד: true — קוד (לא הערה, לא מחרוזת ולא תו); depth — עומק הסוגריים המסולסלים לפני התו.
     */
    static boolean[] lex(String c, int[] depth) {
        boolean[] code = new boolean[c.length()];
        int d = 0, n = c.length();
        for (int i = 0; i < n; i++) {
            char ch = c.charAt(i);
            depth[i] = d;
            if (ch == '/' && i + 1 < n && c.charAt(i + 1) == '/') {
                while (i < n && c.charAt(i) != '\n') depth[i++] = d;
                if (i < n) { depth[i] = d; code[i] = true; }
                continue;
            }
            if (ch == '/' && i + 1 < n && c.charAt(i + 1) == '*') {
                int end = c.indexOf("*/", i + 2);
                end = end < 0 ? n : end + 2;
                while (i < end) depth[i++] = d;
                i--;
                continue;
            }
            if (ch == '"' || ch == '\'') {
                depth[i] = d;
                int j = i + 1;
                while (j < n && c.charAt(j) != ch && c.charAt(j) != '\n') {
                    if (c.charAt(j) == '\\') j++;
                    j++;
                }
                for (int k = i + 1; k <= Math.min(j, n - 1); k++) depth[k] = d;
                i = Math.min(j, n - 1);
                continue;
            }
            code[i] = true;
            if (ch == '{') d++;
            else if (ch == '}') d = Math.max(0, d - 1);
        }
        depth[n] = d;
        return code;
    }

    /** השמות של הפרמטרים ברשימה "Stack<Integer> s, int x" (גם "int a[]") */
    static List<String> paramNames(String list) {
        List<String> out = new ArrayList<String>();
        if (list.trim().isEmpty()) return out;
        int level = 0, start = 0;
        for (int i = 0; i <= list.length(); i++) {
            char ch = i < list.length() ? list.charAt(i) : ',';
            if (ch == '<' || ch == '(' || ch == '[') level++;
            else if (ch == '>' || ch == ')' || ch == ']') level--;
            else if (ch == ',' && level <= 0) {
                Matcher m = Pattern.compile("(" + WORD.pattern() + ")\\s*(\\[\\s*\\]\\s*)*$").matcher(list.substring(start, i).trim());
                out.add(m.find() ? m.group(1) : "");
                start = i + 1;
            }
        }
        return out;
    }

    /** החלפת שמות (משתנים / קריאות) בקוד בלבד — לא בהערות ובמחרוזות, ולא אחרי נקודה (x.name). שם שכבר תפוס — לא מחליפים */
    private static String renameIdents(String s, boolean[] isCode, int[] depth, int offset, Map<String, String> rename) {
        Set<String> used = new HashSet<String>();
        Matcher all = WORD.matcher(s);
        while (all.find()) if (isCode[offset + all.start()]) used.add(all.group());
        Map<String, String> ok = new HashMap<String, String>();
        for (Map.Entry<String, String> r : rename.entrySet())
            if (!used.contains(r.getValue()) || rename.containsKey(r.getValue())) ok.put(r.getKey(), r.getValue());
        StringBuilder b = new StringBuilder();
        Matcher m = WORD.matcher(s);
        int last = 0;
        while (m.find()) {
            String to = ok.get(m.group());
            if (to == null || !isCode[offset + m.start()] || (m.start() > 0 && s.charAt(m.start() - 1) == '.')) continue;
            b.append(s, last, m.start()).append(to);
            last = m.end();
        }
        return b.append(s.substring(last)).toString();
    }

    /** הערות "//" בעברית — עטופות בסימון כיוון (כמו בשאר הקוד שנבנה), כדי שיוצגו מימין לשמאל כמו שצריך */
    private static List<String> rtlComments(List<String> lines) {
        List<String> out = new ArrayList<String>();
        for (String l : lines) {
            int[] depth = new int[l.length() + 1];
            boolean[] isCode = lex(l, depth);
            int at = -1;
            for (int i = 0; i + 1 < l.length(); i++) if (l.charAt(i) == '/' && l.charAt(i + 1) == '/' && isCode.length > i && !isCode[i] && (i == 0 || isCode[i - 1] || l.substring(0, i).trim().isEmpty())) { at = i; break; }
            if (at >= 0) {
                String text = l.substring(at + 2).trim();
                if (text.matches(".*[\u0590-\u05FF].*") && text.indexOf('\u202B') < 0) {
                    out.add(l.substring(0, at) + "// " + rtl(text));
                    continue;
                }
            }
            out.add(l);
        }
        return out;
    }

    /**
     * אחרי סבב תיקון של ההצעות: מהתשובה המתוקנת לוקחים רק את ההצעות לפתרון ואת הפלט הצפוי — המעטפת (הסעיפים, main, המחלקות) נשארת כמו שהיא.
     * מחזיר true אם הפלט הצפוי השתנה (ואז גם ההערה מעל main נכתבת מחדש)
     */
    static boolean takeSolutions(Exercise e, Exercise fixed) {
        for (int i = 0; i < e.tasks.size(); i++) {
            Task t = e.tasks.get(i), f = null;
            for (Task x : fixed.tasks)
                if (x.className.equals(t.className) && x.methodName.equals(t.methodName) && x.params.size() == t.params.size()) { f = x; break; }
            if (f == null && fixed.tasks.size() == e.tasks.size()) f = fixed.tasks.get(i);
            if (f != null && !f.solution.isEmpty()) {
                String s = fitSolution(t, f.solution, f.methodName);
                if (!s.isEmpty()) t.solution = s;
            }
        }
        String old = e.expectedOutput, now = fixed.expectedOutput.replace("\r", "").replaceAll("\\s+$", "");
        if (!now.isEmpty()) e.expectedOutput = now;
        return !old.equals(e.expectedOutput);
    }

    /** שורות ריקות מיותרות: בראש הקובץ, כפולות, אחרי "{" ולפני "}" */
    static String tidy(String code) {
        List<String> out = new ArrayList<String>();
        for (String l : code.split("\n", -1)) {
            String t = l.trim();
            if (l.isEmpty() && (out.isEmpty() || out.get(out.size() - 1).isEmpty() || out.get(out.size() - 1).endsWith("{"))) continue;
            if (t.startsWith("}")) while (!out.isEmpty() && out.get(out.size() - 1).isEmpty()) out.remove(out.size() - 1);
            out.add(l);
        }
        return String.join("\n", out);
    }

    static String defaultValue(String type) {
        switch (type) {
            case "int": case "long": case "short": case "byte": case "double": case "float": case "decimal": return "0";
            case "boolean": case "bool": return "false";
            case "char": return "' '";
            default: return "null";
        }
    }

    private static String reminder(String type, String n, boolean cs) {
        String t = type.replaceAll("\\s", "");
        if (cs) {
            if (t.startsWith("Stack<")) return n + ".Push(x)   " + n + ".Pop()   " + n + ".Top()   " + n + ".IsEmpty()";
            if (t.startsWith("Queue<")) return n + ".Insert(x)   " + n + ".Remove()   " + n + ".Head()   " + n + ".IsEmpty()";
            if (t.startsWith("Node<")) return n + ".GetValue()   " + n + ".GetNext()   " + n + ".SetNext(...)   " + n + ".HasNext()";
            if (t.startsWith("BinNode<"))
                return n + ".GetValue()   " + n + ".GetLeft()   " + n + ".GetRight()   " + n + ".HasLeft()   " + n + ".HasRight()";
            if (t.endsWith("[,]")) return n + ".GetLength(0)   " + n + ".GetLength(1)   " + n + "[i, j]";
            if (t.endsWith("[][]")) return n + ".Length   " + n + "[0].Length   " + n + "[i][j]";
            if (t.endsWith("[]")) return n + ".Length   " + n + "[i]";
            return null;
        }
        if (t.startsWith("Stack<")) return n + ".push(x)   " + n + ".pop()   " + n + ".top()   " + n + ".isEmpty()";
        if (t.startsWith("Queue<")) return n + ".insert(x)   " + n + ".remove()   " + n + ".head()   " + n + ".isEmpty()";
        if (t.startsWith("Node<")) return n + ".getValue()   " + n + ".getNext()   " + n + ".setNext(...)   " + n + ".hasNext()";
        if (t.startsWith("BinNode<") || t.startsWith("BinTreeNode<"))
            return n + ".getValue()   " + n + ".getLeft()   " + n + ".getRight()   " + n + ".hasLeft()   " + n + ".hasRight()";
        if (t.endsWith("[][]")) return n + ".length   " + n + "[0].length   " + n + "[i][j]";
        if (t.endsWith("[]")) return n + ".length   " + n + "[i]";
        return null;
    }

    /** חלק מההערה שבמחלקה ריקה (לבדיקות) */
    static final String ADD_FIELDS = "הוסיפו כאן את התכונות שמתוארות בתרגיל";

    /**
     * ההערה בתוך מחלקה שסעיף שייך אליה, אבל המודל לא תיאר אותה (בלי קוד), או שתיאר אותה ריקה ("public class Book { }"):
     * המחלקה נוצרת בכל זאת — כדי שהכול יתקמפל — והתלמיד מתבקש להוסיף בה את התכונות שמתוארות בתרגיל
     */
    static String addFields(String cls) {
        return "המחלקה " + cls + " מהתרגיל — " + ADD_FIELDS;
    }

    /** קוד המחלקה מהתרגיל, מנוקה (בלי package/import/using/namespace), או מחלקה ריקה */
    private static List<String> classLines(String cls, String code, boolean isMain, boolean cs) {
        List<String> lines = new ArrayList<String>();
        String c = code == null ? "" : code.replace("\r", "");
        StringBuilder kept = new StringBuilder();
        for (String l : c.split("\n", -1)) {
            String t = l.trim();
            if (t.startsWith("package ") || t.startsWith("import ") || (t.startsWith("using ") && t.endsWith(";"))) continue;
            kept.append(l).append('\n');
        }
        c = kept.toString().trim();
        if (cs) {   // namespace שעוטף את המחלקה — מורידים (כל תרגיל הוא פרויקט משלו)
            Matcher ns = Pattern.compile("(?s)^namespace\\s+[\\w.]+\\s*;\\s*(.*)$").matcher(c);
            if (ns.matches()) c = ns.group(1).trim();
            ns = Pattern.compile("(?s)^namespace\\s+[\\w.]+\\s*\\{(.*)\\}$").matcher(c);
            if (ns.matches()) c = ns.group(1).trim();
        }
        String open = cs ? "public class " + cls : "public class " + cls + " {";
        Matcher m = Pattern.compile("(?m)^\\s*(public\\s+|internal\\s+|abstract\\s+|final\\s+|sealed\\s+|partial\\s+)*class\\s+" + Pattern.quote(cls) + "\\b").matcher(c);
        if (c.isEmpty() || !m.find() || c.lastIndexOf('}') < 0) {
            lines.add(open);
            if (cs) lines.add("{");
            if (!c.isEmpty()) lines.addAll(reindent(c, 4));   // קוד בלי הכרזת מחלקה — עוטפים
            else if (!isMain) lines.add("    // " + rtl(addFields(cls)));   // מחלקה שהסעיף שייך אליה אבל לא תוארה בתשובה
            lines.add("}");
            return lines;
        }
        c = c.substring(m.start()).trim();
        if (cs) c = c.replaceFirst("^internal\\s+", "");
        if (!c.startsWith("public")) c = "public " + c;
        int brace = c.indexOf('{');
        if (!isMain && brace > 0 && c.endsWith("}") && c.substring(brace + 1, c.length() - 1).trim().isEmpty()) {
            // מחלקה ריקה ("public class Book { }") — כמו מחלקה שלא תוארה: עם ההערה להוסיף את התכונות מהתרגיל
            String head = c.substring(0, brace).trim().replaceAll("\\s+", " ");
            lines.add(cs ? head : head + " {");
            if (cs) lines.add("{");
            lines.add("    // " + rtl(addFields(cls)));
            lines.add("}");
            return lines;
        }
        lines.addAll(reindent(c, 0));
        return lines;
    }

    /** אם המודל שלח main שלם במקום רק את הגוף — לוקחים את הגוף */
    private static String stripMainWrapper(String code, boolean cs) {
        String c = code == null ? "" : code.trim();
        Matcher m = Pattern.compile("(?s)^(public\\s+)?static\\s+void\\s+" + (cs ? "Main" : "main") + "\\s*\\([^)]*\\)\\s*\\{(.*)\\}$").matcher(c);
        return m.find() ? m.group(2) : c;
    }

    // ------------------------------------------------------------------ עזרים

    /** מזיח את כל השורות: מוריד את ההזחה המשותפת ומוסיף indent רווחים */
    static List<String> reindent(String code, int indent) {
        List<String> res = new ArrayList<String>();
        if (code == null) return res;
        String[] lines = code.replace("\r", "").replace("\t", "    ").split("\n", -1);
        int from = 0, to = lines.length;
        while (from < to && lines[from].trim().isEmpty()) from++;
        while (to > from && lines[to - 1].trim().isEmpty()) to--;
        int min = Integer.MAX_VALUE;
        for (int i = from; i < to; i++) {
            if (lines[i].trim().isEmpty()) continue;
            int k = 0;
            while (k < lines[i].length() && lines[i].charAt(k) == ' ') k++;
            min = Math.min(min, k);
        }
        if (min == Integer.MAX_VALUE) min = 0;
        // המודל לפעמים שולח את השורה הראשונה בלי הזחה (חתוכה) ואת כל השאר מוזחות — מורידים את ההזחה המשותפת של השאר
        int restMin = Integer.MAX_VALUE;
        for (int i = from + 1; i < to; i++) {
            if (lines[i].trim().isEmpty()) continue;
            int k = 0;
            while (k < lines[i].length() && lines[i].charAt(k) == ' ') k++;
            restMin = Math.min(restMin, k);
        }
        boolean firstCut = to - from > 1 && restMin != Integer.MAX_VALUE && restMin > min && min == 0 && !lines[from].trim().startsWith("}");
        String pad = spaces(indent);
        for (int i = from; i < to; i++) {
            String l = lines[i];
            int strip = firstCut && i > from ? restMin : min;
            res.add(l.trim().isEmpty() ? "" : pad + l.substring(Math.min(strip, l.length())).replaceAll("\\s+$", ""));
        }
        return res;
    }

    static List<String> wrap(String text, int width) {
        List<String> res = new ArrayList<String>();
        if (text == null) return res;
        for (String para : text.replace("\r", "").split("\n")) {
            String p = para.trim();
            if (p.isEmpty()) continue;
            StringBuilder line = new StringBuilder();
            for (String w : p.split("\\s+")) {
                if (line.length() > 0 && line.length() + 1 + w.length() > width) {
                    res.add(line.toString());
                    line.setLength(0);
                }
                if (line.length() > 0) line.append(' ');
                line.append(w);
            }
            if (line.length() > 0) res.add(line.toString());
        }
        return res;
    }

    static String ident(String s, String fallback, boolean cs) {
        String t = s == null ? "" : s.trim();
        if (!IDENT.matcher(t).matches() || (cs ? CS_KEYWORDS : KEYWORDS).contains(t) || t.length() > 60) return fallback;
        return t;
    }

    /** טיפוס חוקי (כולל גנריות ומערכים) בשפה שנבחרה, או null */
    static String type(String s, boolean cs) {
        String t = s == null ? "" : s.trim().replaceAll("\\s+", " ").replaceAll("\\s*([<>,\\[\\]])\\s*", "$1").replace(",", ", ");
        if (t.isEmpty() || !TYPE.matcher(t).matches()) return null;
        int depth = 0;
        for (char c : t.toCharArray()) {
            if (c == '<') depth++;
            if (c == '>' && --depth < 0) return null;
        }
        if (depth != 0) return null;
        if (cs) {   // שמות של Java שנכנסו בטעות — לשמות של C#
            t = t.replaceAll("\\bboolean\\b", "bool").replaceAll("\\bString\\b", "string").replaceAll("\\bInteger\\b", "int")
                 .replaceAll("\\bCharacter\\b", "char").replaceAll("\\bDouble\\b", "double").replaceAll("\\bBoolean\\b", "bool");
            return t;
        }
        if (t.contains(",]")) return null;   // [,] — רק ב-C#
        t = t.replaceAll("\\bbool\\b", "boolean").replaceAll("\\bstring\\b", "String");
        // טיפוסים פרימיטיביים בתוך גנריות — מחליפים לעוטפים
        t = t.replaceAll("<int>", "<Integer>").replaceAll("<char>", "<Character>").replaceAll("<double>", "<Double>").replaceAll("<boolean>", "<Boolean>");
        return t;
    }

    private static String paramTypes(Task t) {
        StringBuilder b = new StringBuilder();
        for (String[] p : t.params) b.append(p[0].replaceAll("<.*>", "")).append(',');
        return b.toString();
    }

    private static String oneLine(String s, int max) {
        String t = s == null ? "" : s.replaceAll("\\s+", " ").trim();
        return t.length() > max ? t.substring(0, max) : t;
    }


    private static String spaces(int n) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < n; i++) b.append(' ');
        return b.toString();
    }
}
