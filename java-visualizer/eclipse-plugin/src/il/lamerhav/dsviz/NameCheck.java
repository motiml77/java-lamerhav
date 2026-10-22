package il.lamerhav.dsviz;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * בדיקת שם למשתנה חדש — האם הוא לא מתנגש בשום דבר שכבר מוגדר במקום שבו הסמן (Java ו-C#).
 *
 * מנתחים את הקוד (בלי הערות ומחרוזות) ומחפשים הגדרות: משתנים, פרמטרים, שדות, פעולות ומחלקות.
 * שם מתנגש אם הוא מוגדר באחד מהבלוקים שהסמן נמצא בתוכם (הפעולה, המחלקה...), או בבלוק פנימי שבא אחרי הסמן
 * (ב-Java וב-C# אסור להגדיר שוב בבלוק פנימי שם שכבר קיים בבלוק החיצוני). משתנה בפעולה אחרת — לא מתנגש.
 *
 * שימו לב: יש עותק זהה ב-JS בתוסף של VS Code (names.js) — שינוי כאן צריך להיעשות גם שם
 * (tools/test/names-cases.txt בודק ששניהם נותנים אותן תשובות).
 */
public final class NameCheck {
    private NameCheck() {}

    /** בדיקת שם חוקי בשפה (Java / C#) */
    public interface Ident { boolean ok(String s); }

    private static final Set<String> NOT_A_TYPE = new HashSet<String>(Arrays.asList((
            "return new else throw case goto in is as out ref using await yield break continue do if while for foreach switch "
            + "lock try catch finally typeof sizeof default delete not and or namespace import package extends implements instanceof throws where "
            + "get set add remove").split(" ")));
    private static final Set<String> CONTROL = new HashSet<String>(Arrays.asList(
            "if for foreach while switch catch using lock synchronized return throw new typeof sizeof when fixed".split(" ")));

    /** שמות של מחלקות מוכנות / של השפה — לא משתמשים בהם כשם משתנה */
    public static final Set<String> RESERVED_NAMES = new HashSet<String>(Arrays.asList((
            "Stack Queue Node BinNode Student Car String Math System Arrays Integer Double Character Object Random Console "
            + "List Map Scanner Build Main").split(" ")));

    /** מחליף הערות ומחרוזות ברווחים (שומר על אותו אורך ועל מעברי שורה) */
    static String stripCode(String text) {
        char[] out = text.toCharArray();
        int n = text.length(), i = 0;
        while (i < n) {
            char c = text.charAt(i), d = i + 1 < n ? text.charAt(i + 1) : 0;
            if (c == '/' && d == '/') {
                int j = i;
                while (j < n && text.charAt(j) != '\n') j++;
                blank(out, i, j);
                i = j;
            } else if (c == '/' && d == '*') {
                int j = text.indexOf("*/", i + 2);
                j = j < 0 ? n : j + 2;
                blank(out, i, j);
                i = j;
            } else if (c == '"' && text.startsWith("\"\"\"", i)) {
                int j = text.indexOf("\"\"\"", i + 3);
                j = j < 0 ? n : j + 3;
                blank(out, i, j);
                i = j;
            } else if (c == '"' || c == '\'') {
                int j = i + 1;
                while (j < n && text.charAt(j) != c && text.charAt(j) != '\n') {
                    if (text.charAt(j) == '\\') j++;
                    j++;
                }
                int end = Math.min(j + 1, n);
                blank(out, i, end);
                i = end;
            } else {
                i++;
            }
        }
        return new String(out);
    }

    private static void blank(char[] out, int a, int b) {
        for (int k = a; k < b; k++) if (out[k] != '\n' && out[k] != '\r') out[k] = ' ';
    }

    private static final Pattern LAST_IDENT = Pattern.compile("([A-Za-z_]\\w*)\\s*$");

    /** "a, Map<K, V> b, int c = 5" → [a, b, c] — השם האחרון בכל פרמטר */
    static List<String> paramNames(String list) {
        List<String> parts = new ArrayList<String>();
        int depth = 0;
        StringBuilder cur = new StringBuilder();
        for (char ch : list.toCharArray()) {
            if (ch == '<' || ch == '(' || ch == '[') depth++;
            else if (ch == '>' || ch == ')' || ch == ']') depth--;
            if (ch == ',' && depth == 0) { parts.add(cur.toString()); cur.setLength(0); } else cur.append(ch);
        }
        parts.add(cur.toString());
        List<String> names = new ArrayList<String>();
        for (String p0 : parts) {
            String p = p0.replaceAll("(?s)=.*$", "").replaceAll("\\[[\\s,]*\\]", " ").trim();
            Matcher m = LAST_IDENT.matcher(p);
            if (m.find() && p.matches("(?s).*\\s.*") && !NOT_A_TYPE.contains(m.group(1))) names.add(m.group(1));
        }
        return names;
    }

    private static final Pattern PAREN = Pattern.compile("\\([^()]*\\)");
    private static final Pattern BRACE = Pattern.compile("\\{[^{}]*\\}");
    private static final Pattern LEADING_CONTROL = Pattern.compile("^\\s*(?:else\\s+)?(?:for|foreach|while|if|switch|catch|using|lock|synchronized)\\s*\\(\\)\\s*");
    private static final Pattern DECL = Pattern.compile(
            "(?:^|[\\s(,;{}])(?:(?:final|readonly|const|static|volatile|private|public|protected|internal|new|ref|out)\\s+)*"
            + "([A-Za-z_]\\w*(?:\\s*\\.\\s*[A-Za-z_]\\w*)*(?:\\s*<[^;(){}=]*?>)?(?:\\s*\\[[\\s,]*\\])*\\??)\\s+([A-Za-z_]\\w*)\\s*(?=[=;,):\\[]|\\bin\\b|$)");
    private static final Pattern MORE = Pattern.compile(",\\s*([A-Za-z_]\\w*)\\s*(?==[^=]|,|;|$)");

    /** הגדרות משתנים בתוך הצהרה אחת (טקסט בלי הערות) */
    static List<String> statementNames(String stmt) {
        String s = stmt;
        for (int k = 0; k < 6; k++) s = PAREN.matcher(s).replaceAll("()");
        s = BRACE.matcher(s).replaceAll("{}");
        s = LEADING_CONTROL.matcher(s).replaceFirst("");
        List<String> names = new ArrayList<String>();
        Matcher m = DECL.matcher(s);
        boolean first = true;
        int from = 0;
        while (from <= s.length() && m.find(from)) {
            String type = m.group(1).split("[\\s<\\[.?]")[0];
            int next = m.end() == m.start() ? m.end() + 1 : m.end();
            if (NOT_A_TYPE.contains(type) || NOT_A_TYPE.contains(m.group(2))) { from = next; continue; }
            names.add(m.group(2));
            if (first) {
                first = false;
                Matcher r = MORE.matcher(s.substring(m.end()));
                while (r.find()) names.add(r.group(1));
            }
            from = next;
        }
        return names;
    }

    private static final class Decl {
        final String name;
        final int block;
        Decl(String name, int block) { this.name = name; this.block = block; }
    }

    private static final class Analysis {
        final List<Decl> decls = new ArrayList<Decl>();
        final List<int[]> blocks = new ArrayList<int[]>();   // {parent, start}
        List<Integer> chain;
        boolean hasClass;
    }

    private static final Pattern INIT_END = Pattern.compile("(?s).*(?:=|\\]|\\bnew|,|\\breturn)$");
    private static final Pattern CLASS_HEAD = Pattern.compile("\\b(?:class|interface|enum|record|struct)\\s+([A-Za-z_]\\w*)");
    private static final Pattern CALL_HEAD = Pattern.compile(
            "([A-Za-z_]\\w*)\\s*(?:<[^()]*>)?\\s*\\(((?s:.*))\\)\\s*(?:throws[^{]*|where[^{]*|:\\s*(?:base|this)\\s*\\([^)]*\\))?\\s*$");
    private static final Pattern WORD_END = Pattern.compile("[\\w>\\]]\\s*$");
    private static final Pattern NEW_END = Pattern.compile("(?:^|[\\s;{}])new\\s*$");
    private static final Pattern KW_END = Pattern.compile("\\b(?:else|do|try|finally)\\s*$");

    private static void add(Analysis a, List<String> names, int block) {
        for (String nm : names) a.decls.add(new Decl(nm, block));
    }

    private static Analysis analyze(String text, int offset) {
        String code = stripCode(text);
        Analysis a = new Analysis();
        a.blocks.add(new int[] { -1, 0, 0 });
        List<Integer> stack = new ArrayList<Integer>();
        stack.add(0);
        List<Integer> chain = null;
        int seg = 0, paren = 0, n = code.length();
        for (int i = 0; i < n; i++) {
            if (chain == null && offset >= 0 && i >= offset) chain = new ArrayList<Integer>(stack);
            char c = code.charAt(i);
            if (c == '(') {
                paren++;
            } else if (c == ')') {
                paren = Math.max(0, paren - 1);
            } else if (paren == 0 && c == ';') {
                add(a, statementNames(code.substring(seg, i)), stack.get(stack.size() - 1));
                seg = i + 1;
            } else if (paren == 0 && c == '{') {
                String header = code.substring(seg, i);
                String t = header.trim();
                if (INIT_END.matcher(t).matches()) {
                    int depth = 1, j = i + 1;
                    while (j < n && depth > 0) {
                        if (code.charAt(j) == '{') depth++;
                        else if (code.charAt(j) == '}') depth--;
                        j++;
                    }
                    if (chain == null && offset >= 0 && offset < j) chain = new ArrayList<Integer>(stack);
                    i = j - 1;
                    continue;
                }
                int parent = stack.get(stack.size() - 1);
                int id = a.blocks.size();
                Matcher cls = CLASS_HEAD.matcher(header);
                boolean isClass = cls.find();
                a.blocks.add(new int[] { parent, i, isClass ? 1 : 2 });   // 1 = גוף מחלקה, 2 = בלוק קוד (פעולה...)
                if (isClass) { add(a, Arrays.asList(cls.group(1)), parent); a.hasClass = true; }
                Matcher call = CALL_HEAD.matcher(header);
                if (call.find()) {
                    String head = header.substring(0, call.start()).trim();
                    boolean isControl = CONTROL.contains(call.group(1));
                    if (!isControl && WORD_END.matcher(head).find() && !NEW_END.matcher(head).find() && !KW_END.matcher(head).find())
                        add(a, Arrays.asList(call.group(1)), parent);
                    if (isControl) add(a, statementNames(call.group(2)), id);
                    else add(a, paramNames(call.group(2)), id);
                }
                stack.add(id);
                seg = i + 1;
            } else if (paren == 0 && c == '}') {
                if (stack.size() > 1) stack.remove(stack.size() - 1);
                seg = i + 1;
            }
        }
        a.chain = chain != null ? chain : new ArrayList<Integer>(stack);
        return a;
    }

    private static boolean isAncestor(List<int[]> blocks, int ancestor, int id) {
        for (int b = id; b >= 0; b = blocks.get(b)[0]) if (b == ancestor) return true;
        return false;
    }

    /**
     * האם הנקודה offset נמצאת בתוך פעולה (או כל בלוק קוד אחר) — ולא בגוף המחלקה או מחוץ לה. שם אי אפשר להכניס משתנה / פקודות.
     * קובץ בלי מחלקה בכלל (C# עם פקודות ברמה העליונה) — מותר.
     */
    public static boolean insideFunction(String text, int offset) {
        if (offset < 0) return true;
        Analysis a = analyze(text, offset);
        if (!a.hasClass) return true;
        for (int id : a.chain) if (id > 0 && a.blocks.get(id)[2] == 2) return true;
        return false;
    }

    /** כל השמות שאסור להגדיר בנקודה offset (offset < 0 = לא ידוע: כל מה שמוגדר בקובץ) */
    public static Set<String> takenNames(String text, int offset) {
        Analysis a = analyze(text, offset);
        Set<String> taken = new HashSet<String>();
        int inner = a.chain.get(a.chain.size() - 1);
        for (Decl d : a.decls) {
            if (offset < 0 || a.chain.contains(d.block)) { taken.add(d.name); continue; }
            if (a.blocks.get(d.block)[1] >= offset && isAncestor(a.blocks, inner, d.block)) taken.add(d.name);
        }
        return taken;
    }

    /** null אם השם תקין ופנוי, אחרת הודעת שגיאה בעברית. offset = מיקום הסמן בטקסט (-1 = לא ידוע) */
    public static String checkName(String name, String text, int offset, Ident isIdentifier) {
        if (!isIdentifier.ok(name)) return "השם חייב להיות באנגלית, בלי רווחים, להתחיל באות, ולא להיות מילה שמורה של השפה";
        if (RESERVED_NAMES.contains(name)) return "\"" + name + "\" הוא שם של מחלקה מוכנה — בחרו שם אחר";
        if (takenNames(text, offset).contains(name)) return "כבר יש בקוד משתנה, פעולה או מחלקה בשם \"" + name + "\" — בחרו שם אחר";
        return null;
    }

    /** שם מוצע: base, base2, base3... הראשון שפנוי (stk, stk2... או q1, q2...) */
    public static String suggestName(String base, String text, int offset, Ident isIdentifier) {
        Set<String> taken = takenNames(text, offset);
        for (int k = 1; k < 100; k++) {
            String nm = Scaffold.nameN(base, k);
            if (!taken.contains(nm) && !RESERVED_NAMES.contains(nm) && isIdentifier.ok(nm)) return nm;
        }
        return base;
    }
}
