package il.lamerhav.dsviz;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * תרגיל מהמאגר נבנה סעיף אחרי סעיף: כשיוצרים אותו נכנסים לקוד רק הפעולה של סעיף א, ו-main שמריץ ובודק את סעיף א.
 * "הכן סעיף ב" (בחלון "השאלה") מוסיף לקוד את הפעולה של סעיף ב — אחרי הפעולה של הסעיף הקודם באותה מחלקה — ובסוף main את
 * הבדיקות של סעיף ב (ואת השורות שלו בהערה "צריך להיות מודפס"). המקום נקבע לפי מבנה הקוד (מחלקות, פעולות, סוגריים), ולא לפי
 * המקום של הסמן, כך שזה עובד גם כשהתלמיד כבר כתב קוד, הוסיף שורות או סידר את הקוד מחדש.
 * משותף ל-Eclipse (BankFloat), ל-Visual Studio ול-IntelliJ (BankVsFloat). Java 8, בלי ממשק.
 */
final class BankSections {
    private BankSections() {}

    private static final Pattern LABEL = Pattern.compile("סעיף\\s*([א-ת])(?![א-ת])");

    /** התווית בלי גרש, רווח ונקודה ("ב'" → "ב") */
    static String norm(String label) {
        return label == null ? "" : label.replaceAll("[\\s'׳.]", "");
    }

    static List<String> labels(Map<String, Object> exercise) {
        List<String> out = new ArrayList<String>();
        for (Object o : Json.arr(exercise.get("tasks"))) out.add(norm(Json.str(Json.obj(o).get("label"))));
        return out;
    }

    /** הסעיף שהשורה מדפיסה ("סעיף ב, בדיקה 1: ..."), או null */
    static String labelIn(String line, List<String> labels) {
        Matcher m = LABEL.matcher(line);
        while (m.find()) if (labels.contains(m.group(1))) return m.group(1);
        return null;
    }

    /**
     * main של התרגיל מחולק לפי הסעיפים (לפי הסדר): לכל סעיף — שורות ההכנה שלפני שורת ההדפסה שלו, ושורת ההדפסה. שורות אחרי ההדפסה
     * האחרונה (למשל "המחסנית אחרי: ...") — לסעיף האחרון. משפט שנמשך כמה שורות (סוגריים פתוחים) נשאר שלם בסעיף אחד.
     */
    static LinkedHashMap<String, List<String>> splitMain(String mainCode, List<String> labels) {
        LinkedHashMap<String, List<String>> out = new LinkedHashMap<String, List<String>>();
        for (String l : labels) out.put(l, new ArrayList<String>());
        if (labels.isEmpty()) return out;
        List<String> pending = new ArrayList<String>();
        String last = labels.get(0);
        String[] lines = mainCode.replace("\r", "").split("\n", -1);
        List<String> statement = new ArrayList<String>();
        int open = 0;
        for (String line : lines) {
            statement.add(line);
            open += balance(line);
            if (open > 0) continue;   // המשפט עוד לא נגמר
            open = 0;
            String found = null;
            for (String s : statement) if ((found = labelIn(s, labels)) != null) break;
            if (found == null) pending.addAll(statement);
            else {
                // סעיף מוקדם יותר אחרי סעיף מאוחר — נשאר בסעיף המאוחר (כדי שהסדר יישמר)
                if (labels.indexOf(found) < labels.indexOf(last)) found = last;
                out.get(found).addAll(pending);
                out.get(found).addAll(statement);
                pending.clear();
                last = found;
            }
            statement.clear();
        }
        pending.addAll(statement);
        while (!pending.isEmpty() && pending.get(pending.size() - 1).trim().isEmpty()) pending.remove(pending.size() - 1);
        out.get(labels.get(labels.size() - 1)).addAll(pending);
        return out;
    }

    /** סוגריים עגולים ומסולסלים פתוחים בשורה (בלי מחרוזות והערות) */
    private static int balance(String line) {
        int n = 0;
        boolean[] code = AiScaffold.lex(line, new int[line.length() + 1]);
        for (int i = 0; i < line.length(); i++) {
            if (!code[i]) continue;
            char c = line.charAt(i);
            if (c == '(' || c == '{') n++;
            else if (c == ')' || c == '}') n--;
        }
        return n;
    }

    /** הפלט הרצוי של n הסעיפים הראשונים: השורות של כל סעיף, ושורות בלי סעיף — עם הסעיף שלפניהן (בהתחלה — עם הראשון) */
    static String expectedUpTo(String expected, List<String> labels, int n) {
        StringBuilder b = new StringBuilder();
        String cur = labels.isEmpty() ? "" : labels.get(0);
        for (String line : expected.replace("\r", "").split("\n", -1)) {
            String l = labelIn(line, labels);
            if (l != null) cur = l;
            if (labels.indexOf(cur) < n) b.append(b.length() == 0 ? "" : "\n").append(line);
        }
        return b.toString().replaceAll("\\s+$", "");
    }

    /** עותק של התרגיל (JSON) עם n הסעיפים הראשונים בלבד: הפעולות, השורות שלהם ב-main והפלט הרצוי שלהם */
    static Map<String, Object> firstSections(Map<String, Object> exercise, int n) {
        Map<String, Object> copy = Json.obj(Json.parse(Json.write(exercise)));
        List<String> labels = labels(exercise);
        if (n >= labels.size()) return copy;
        List<Object> tasks = new ArrayList<Object>(Json.arr(copy.get("tasks")).subList(0, n));
        copy.put("tasks", tasks);
        LinkedHashMap<String, List<String>> parts = splitMain(Json.str(exercise.get("mainCode")), labels);
        StringBuilder main = new StringBuilder();
        for (int i = 0; i < n; i++) for (String l : parts.get(labels.get(i))) main.append(main.length() == 0 ? "" : "\n").append(l);
        copy.put("mainCode", main.toString());
        copy.put("expectedOutput", expectedUpTo(Json.str(exercise.get("expectedOutput")), labels, n));
        return copy;
    }

    /** כמה סעיפים כבר בקוד, מההתחלה ברצף: הפעולה של הסעיף קיימת במחלקה שלה. texts — שם מחלקה → הקוד של הקובץ שלה */
    static int prepared(AiScaffold.Exercise ex, Map<String, String> texts) {
        int n = 0;
        for (AiScaffold.Task t : ex.tasks) {
            String text = texts.get(t.className);
            if (text == null || methodAt(text, ex, t) == null) break;
            n++;
        }
        return n;
    }

    // ------------------------------------------------------------------ הוספת סעיף לקובץ

    /** הוספה אחת: טקסט שנכנס במקום (offset) בטקסט המקורי */
    static final class Insert {
        final int offset;
        final String text;

        Insert(int offset, String text) {
            this.offset = offset;
            this.text = text;
        }
    }

    /** מה ההוספה של סעיף עושה בקובץ אחד */
    static final class Edit {
        final List<Insert> inserts = new ArrayList<Insert>();
        String text;            // הקוד אחרי ההוספה
        int cursorLine = -1;    // השורה (מ-1) מתחת לשורת "כאן עונים" של הפעולה החדשה, או -1
        boolean method, main;   // נוספה הפעולה / נוספו השורות ל-main
        String problem;         // מה לא הצליח (למשל main לא נמצא), או null

        boolean changed() {
            return !inserts.isEmpty();
        }
    }

    /**
     * מוסיף לקוד של קובץ אחד (של המחלקה className) את סעיף t: את הפעולה שלו אם היא במחלקה הזו, ואם זו המחלקה של main — את
     * השורות שלו ב-main ובהערה של הפלט הרצוי. full — התרגיל המלא (כל הסעיפים). withSolution — לבדיקות: ההצעה לפתרון במקום פעולה ריקה.
     */
    static Edit add(String text, AiScaffold.Exercise full, AiScaffold.Task t, String className, boolean withSolution) {
        Edit e = new Edit();
        String nl = text.contains("\r\n") ? "\r\n" : "\n";
        boolean cs = full.cs;
        int[] body = classBody(text, className);
        if (body == null) {
            e.problem = "המחלקה " + className + " לא נמצאה בקובץ";
            e.text = text;
            return e;
        }
        // 1. הפעולה — במחלקה שלה, אחרי הפעולה של הסעיף הקודם באותה מחלקה
        if (t.className.equals(className) && methodAt(text, full, t) == null) {
            List<String> stub = stub(t, withSolution, cs);
            String member = memberIndent(text, body);
            int at = -1;
            boolean after = false;
            for (int i = full.tasks.indexOf(t) - 1; i >= 0 && at < 0; i--) {
                AiScaffold.Task p = full.tasks.get(i);
                if (!p.className.equals(className)) continue;
                int[] m = methodAt(text, full, p);
                if (m != null) {
                    at = lineEnd(text, m[2]);
                    after = true;
                    member = indentOf(text, m[0]);
                }
            }
            if (at < 0) {
                // אין פעולה של סעיף קודם במחלקה: לפני "נתון בתרגיל" / "מכאן והלאה בדיקה" / main — ואם אין — לפני סוף המחלקה
                int mark = firstLine(text, body, "נתון בתרגיל", "מכאן והלאה בדיקה");
                int[] main = mainAt(text, className);
                if (mark >= 0) at = lineStart(text, mark);
                else if (main != null) at = lineStart(text, main[0]);
                else at = lineStart(text, body[1]);
            }
            StringBuilder ins = new StringBuilder();
            if (after) ins.append(nl);
            for (String l : stub) ins.append(l.isEmpty() ? "" : indentLike(member, l)).append(nl);
            if (!after) ins.append(nl);
            e.inserts.add(new Insert(at, ins.toString()));
            e.method = true;
        }
        // 2. main — השורות של הסעיף בסוף main, והשורות שלו בפלט הרצוי (בהערה שמעל main)
        if (className.equals(full.mainClass)) {
            List<String> labels = new ArrayList<String>();
            for (AiScaffold.Task x : full.tasks) labels.add(norm(x.label));
            String label = norm(t.label);
            int k = labels.indexOf(label);
            List<String> lines = splitMain(stripMainWrapper(full.mainCode, cs), labels).get(label);
            int[] main = mainAt(text, className);
            if (main == null) {
                if (lines != null && !lines.isEmpty()) e.problem = "הפעולה main לא נמצאה — את הבדיקות של סעיף " + label + " לא הוספתי";
            } else if (lines != null && !lines.isEmpty() && !alreadyThere(text, main, lines)) {
                String inner = bodyIndent(text, main, memberIndent(text, body));
                int close = main[2];
                int ls = lineStart(text, close);
                boolean alone = text.substring(ls, close).trim().isEmpty();
                StringBuilder ins = new StringBuilder();
                if (!alone) ins.append(nl);
                for (String l : lines) ins.append(l.trim().isEmpty() ? "" : inner + l.trim()).append(nl);
                if (!alone) ins.append(indentOf(text, main[0]));
                e.inserts.add(new Insert(alone ? ls : close, ins.toString()));
                e.main = true;
            }
            // הפלט הרצוי בהערה: השורות החדשות אחרי השורה האחרונה של ההערה
            if (k >= 0 && e.main) {
                String before = expectedUpTo(full.expectedOutput, labels, k), now = expectedUpTo(full.expectedOutput, labels, k + 1);
                String added = now.length() > before.length() ? now.substring(before.isEmpty() ? 0 : before.length()).replaceFirst("^\n", "") : "";
                int last = lastExpectedLine(text, body);
                if (!added.isEmpty() && last >= 0) {
                    int end = lineEnd(text, last);
                    String ind = indentOf(text, last);
                    StringBuilder ins = new StringBuilder();
                    for (String l : added.split("\n")) {
                        String c = AiScaffold.rtl(l.replace("\t", "    "));
                        ins.append(ind).append("//     ").append(cs ? noMarks(c) : c).append(nl);
                    }
                    e.inserts.add(new Insert(end, ins.toString()));
                }
            }
        }
        // הרכבה: מהסוף להתחלה (כך שהמקומות לא זזים)
        StringBuilder b = new StringBuilder(text);
        List<Insert> order = new ArrayList<Insert>(e.inserts);
        order.sort((x, y) -> Integer.compare(y.offset, x.offset));
        for (Insert in : order) b.insert(in.offset, in.text);
        e.text = b.toString();
        if (e.method) {
            String mark = "// " + (cs ? noMarks(AiScaffold.markFor(t, true)) : AiScaffold.markFor(t, true));
            String[] ls = e.text.split("\n", -1);
            int[] m = methodAt(e.text, full, t);
            int from = m == null ? 0 : lineOf(e.text, m[0]);
            for (int i = from; i < ls.length; i++) if (ls[i].trim().equals(mark)) { e.cursorLine = i + 2; break; }
            if (e.cursorLine < 0 && m != null) e.cursorLine = lineOf(e.text, m[0]) + 1;
        }
        return e;
    }

    /** "כאן כותבים את התשובה" (תרגיל של סעיף אחד) → "כאן עונים סעיף א'" — כי בשאלה יש עוד סעיפים שיתווספו */
    static String markSections(String text, AiScaffold.Task first, boolean cs) {
        // עם סימוני הכיוון (כמו שהמעטפת נבנית) ובלעדיהם (קובץ C# אחרי stripMarks)
        String plain = "// " + AiScaffold.MARK, several = "// " + AiScaffold.markFor(first, true);
        String out = text.replace(plain, several).replace(noMarks(plain), noMarks(several));
        return cs ? out : out;
    }

    static String noMarks(String s) {
        return s.replaceAll("[\\u202A-\\u202E\\u2066-\\u2069]", "");
    }

    /** הפעולה (או הפעולה הריקה) של הסעיף, בשורות, מוזחת ב-4 כמו במעטפת */
    private static List<String> stub(AiScaffold.Task t, boolean withSolution, boolean cs) {
        List<String> out = new ArrayList<String>();
        if (withSolution && !t.solution.isEmpty()) {
            out.addAll(AiScaffold.reindent(t.solution, 4));
            return out;
        }
        String mark = AiScaffold.markFor(t, true);
        if (cs) {
            out.add("    " + AiScaffold.header(t));
            out.add("    {");
        } else out.add("    " + AiScaffold.header(t) + " {");
        out.add("        // " + (cs ? noMarks(mark) : mark));
        out.add("        ");
        if (AiScaffold.testReturns && !t.constructor() && !"void".equals(t.returnType)) out.add("        return " + AiScaffold.defaultValue(t.returnType) + ";");
        out.add("    }");
        return out;
    }

    private static String stripMainWrapper(String code, boolean cs) {
        String c = code.replace("\r", "").trim();
        Matcher m = Pattern.compile("(?s)^(public\\s+)?static\\s+void\\s+" + (cs ? "Main" : "main") + "\\s*\\([^)]*\\)\\s*\\{(.*)\\}$").matcher(c);
        return m.matches() ? m.group(2) : code;
    }

    /** השורות כבר ב-main (לחיצה כפולה / main שהועתק) — לא מוסיפים שוב */
    private static boolean alreadyThere(String text, int[] main, List<String> lines) {
        String bodyText = text.substring(main[1], main[2]).replaceAll("\\s+", "");
        StringBuilder want = new StringBuilder();
        for (String l : lines) want.append(l.replaceAll("\\s+", ""));
        return want.length() > 0 && bodyText.contains(want);
    }

    // ------------------------------------------------------------------ מבנה הקוד

    /** {המקום של "{" של המחלקה, המקום של "}" שסוגר אותה} — או null */
    static int[] classBody(String text, String className) {
        int[] depth = new int[text.length() + 1];
        boolean[] code = AiScaffold.lex(text, depth);
        Matcher m = Pattern.compile("\\bclass\\s+" + Pattern.quote(className) + "\\b").matcher(text);
        while (m.find()) {
            if (!code[m.start()]) continue;
            int open = text.indexOf('{', m.end());
            while (open >= 0 && !code[open]) open = text.indexOf('{', open + 1);
            if (open < 0) return null;
            int close = matching(text, code, depth, open);
            return close < 0 ? null : new int[] { open, close };
        }
        return null;
    }

    /** {תחילת הכותרת, "{" של הגוף, "}" שסוגר אותו} של הפעולה name ישירות במחלקה — או null */
    /**
     * הפעולה של סעיף t בקוד. כשבאותה מחלקה יש כמה סעיפים עם אותו שם (פעולות בונות, כמו Weight(int kilo, int gram) ו-Weight(int totalGram))
     * — מזהים לפי טיפוסי הפרמטרים; אחרת לפי השם בלבד (כך שינוי בשמות / בטיפוסים של התלמיד לא גורם להוספה כפולה).
     */
    static int[] methodAt(String text, AiScaffold.Exercise ex, AiScaffold.Task t) {
        int same = 0;
        for (AiScaffold.Task o : ex.tasks) if (o.className.equals(t.className) && o.methodName.equals(t.methodName)) same++;
        return same > 1 ? methodAt(text, t.className, t.methodName, paramTypes(t)) : methodAt(text, t.className, t.methodName);
    }

    private static String paramTypes(AiScaffold.Task t) {
        StringBuilder b = new StringBuilder();
        for (String[] p : t.params) b.append(b.length() == 0 ? "" : ",").append(p[0].replaceAll("\\s+", ""));
        return b.toString();
    }

    /** הטיפוסים בכותרת (בלי שמות ורווחים, מופרדים בפסיק): "int,String" */
    private static String headerTypes(String params) {
        List<String> parts = new ArrayList<String>();
        int angle = 0, from = 0;
        for (int i = 0; i <= params.length(); i++) {
            char c = i < params.length() ? params.charAt(i) : ',';
            if (c == '<') angle++;
            else if (c == '>') angle--;
            else if (c == ',' && angle == 0) { parts.add(params.substring(from, i).trim()); from = i + 1; }
        }
        StringBuilder b = new StringBuilder();
        for (String p : parts) {
            if (p.isEmpty()) continue;
            String type = p.replaceAll("\\s+[\\p{L}_$][\\p{L}\\p{N}_$]*\\s*$", "");   // בלי שם הפרמטר
            b.append(b.length() == 0 ? "" : ",").append(type.replaceAll("\\s+", "").replaceFirst("^final", ""));
        }
        return b.toString();
    }

    static int[] methodAt(String text, String className, String name) {
        return methodAt(text, className, name, null);
    }

    static int[] methodAt(String text, String className, String name, String types) {
        int[] body = classBody(text, className);
        if (body == null) return null;
        int[] depth = new int[text.length() + 1];
        boolean[] code = AiScaffold.lex(text, depth);
        int inner = depth[body[0]] + 1;
        Matcher m = Pattern.compile("\\b" + Pattern.quote(name) + "\\s*\\(").matcher(text);
        m.region(body[0] + 1, body[1]);
        while (m.find()) {
            if (!code[m.start()] || depth[m.start()] != inner) continue;
            // הכותרת: לפני השם יש טיפוס / מילה (לא "." ולא "new") — כדי שקריאה לפעולה לא תיחשב
            int s = lineStart(text, m.start());
            String head = text.substring(s, m.start()).trim();
            if (head.isEmpty() || head.endsWith(".") || head.endsWith("new") || head.endsWith("=") || head.endsWith("return")) continue;
            int open = text.indexOf('{', m.end());
            int semi = text.indexOf(';', m.end());
            while (open >= 0 && !code[open]) open = text.indexOf('{', open + 1);
            if (open < 0 || (semi >= 0 && semi < open && code[semi])) continue;
            if (types != null) {
                int pOpen = m.end() - 1, pClose = text.indexOf(')', pOpen);
                if (pClose < 0 || pClose > open || !headerTypes(text.substring(pOpen + 1, pClose)).equals(types)) continue;
            }
            int close = matching(text, code, depth, open);
            if (close > 0 && close <= body[1]) return new int[] { s, open, close };
        }
        return null;
    }

    /** main במחלקה: {תחילת הכותרת, "{", "}"} — או null */
    static int[] mainAt(String text, String className) {
        int[] r = methodAt(text, className, "main");
        return r != null ? r : methodAt(text, className, "Main");
    }

    private static int matching(String text, boolean[] code, int[] depth, int open) {
        int d = depth[open];
        for (int i = open + 1; i < text.length(); i++) if (code[i] && text.charAt(i) == '}' && depth[i] == d + 1) return i;
        return -1;
    }

    /** השורה הראשונה בתוך המחלקה שיש בה אחת המילים (בהערה) — המקום בתחילתה, או -1 */
    private static int firstLine(String text, int[] body, String... words) {
        int best = -1;
        for (String w : words) {
            int i = noMarks(text).indexOf(w) < 0 ? -1 : indexIgnoringMarks(text, w, body[0]);
            if (i >= 0 && i < body[1] && (best < 0 || i < best)) best = i;
        }
        return best;
    }

    private static int indexIgnoringMarks(String text, String word, int from) {
        for (int i = from; i < text.length(); i++) {
            int j = i, k = 0;
            while (j < text.length() && k < word.length()) {
                char c = text.charAt(j);
                if (c >= '‪' && c <= '‮' || c >= '⁦' && c <= '⁩') { j++; continue; }
                if (c != word.charAt(k)) break;
                j++;
                k++;
            }
            if (k == word.length()) return i;
        }
        return -1;
    }

    /** השורה האחרונה בהערה של הפלט הרצוי ("//     ...") — המקום בתחילתה, או -1 */
    private static int lastExpectedLine(String text, int[] body) {
        int head = indexIgnoringMarks(text, "צריך להיות מודפס על המסך", body[0]);
        if (head < 0 || head > body[1]) return -1;
        int line = lineEnd(text, head), last = -1;
        while (line < text.length()) {
            int end = text.indexOf('\n', line);
            String l = text.substring(line, end < 0 ? text.length() : end);
            if (!l.trim().startsWith("//     ")) break;
            last = line;
            if (end < 0) break;
            line = end + 1;
        }
        return last;
    }

    private static String memberIndent(String text, int[] body) {
        // ההזחה של השורה הראשונה בתוך המחלקה שיש בה קוד
        int i = lineEnd(text, body[0]);
        while (i < body[1]) {
            int end = text.indexOf('\n', i);
            if (end < 0) break;
            String l = text.substring(i, end).replace("\r", "");
            if (!l.trim().isEmpty()) return l.substring(0, l.length() - l.replaceAll("^\\s+", "").length());
            i = end + 1;
        }
        return "    ";
    }

    private static String bodyIndent(String text, int[] main, String member) {
        String found = null;
        int i = lineEnd(text, main[1]);
        while (i < main[2]) {
            int end = text.indexOf('\n', i);
            if (end < 0 || end > main[2]) break;
            String l = text.substring(i, end).replace("\r", "");
            if (!l.trim().isEmpty()) found = l.substring(0, l.length() - l.replaceAll("^\\s+", "").length());
            i = end + 1;
        }
        if (found != null) return found;
        String m = indentOf(text, main[0]);
        return m + (m.contains("\t") ? "\t" : "    ");
    }

    /** שורה של הפעולה החדשה (מוזחת ב-4 לפי המעטפת) — בהזחה של המחלקה בקובץ (רווחים או טאבים) */
    private static String indentLike(String member, String line) {
        int lead = line.length() - line.replaceAll("^ +", "").length();
        String rest = line.substring(lead);
        int levels = Math.max(0, lead / 4 - 1);
        StringBuilder b = new StringBuilder(member);
        String unit = member.contains("\t") ? "\t" : "    ";
        for (int i = 0; i < levels; i++) b.append(unit);
        if (lead % 4 != 0) for (int i = 0; i < lead % 4; i++) b.append(' ');
        return b.append(rest).toString();
    }

    private static String indentOf(String text, int pos) {
        int s = lineStart(text, pos), e = s;
        while (e < text.length() && (text.charAt(e) == ' ' || text.charAt(e) == '\t')) e++;
        return text.substring(s, e);
    }

    private static int lineStart(String text, int pos) {
        int i = text.lastIndexOf('\n', Math.max(0, pos - 1));
        return pos == 0 ? 0 : i + 1;
    }

    /** המקום אחרי סוף השורה (אחרי ה-"\n") */
    private static int lineEnd(String text, int pos) {
        int i = text.indexOf('\n', pos);
        return i < 0 ? text.length() : i + 1;
    }

    /** מספר השורה (מ-0) */
    static int lineOf(String text, int pos) {
        int n = 0;
        for (int i = 0; i < pos && i < text.length(); i++) if (text.charAt(i) == '\n') n++;
        return n;
    }
}
