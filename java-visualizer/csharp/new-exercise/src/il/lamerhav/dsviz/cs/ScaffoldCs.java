package il.lamerhav.dsviz.cs;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import il.lamerhav.dsviz.Scaffold;

/**
 * קוד תרגיל בשפת C# — מופק מהמחולל של Java (Scaffold) בהמרה מכנית, בדיוק כמו vscode-extension/templates-cs.js
 * (tools/test/cs-tests.sh משווה את שני המחוללים קובץ-קובץ). כך יש מקור אחד לתבניות, וגם Visual Studio מקבל אותן.
 */
public final class ScaffoldCs {
    private ScaffoldCs() {}

    private static final Set<String> CS_KEYWORDS = new HashSet<String>(Arrays.asList((
            "abstract as base bool break byte case catch char checked class const continue decimal default delegate do "
            + "double else enum event explicit extern false finally fixed float for foreach goto if implicit in int interface internal is lock long "
            + "namespace new null object operator out override params private protected public readonly ref return sbyte sealed short sizeof "
            + "stackalloc static string struct switch this throw true try typeof uint ulong unchecked unsafe ushort using virtual void volatile while").split(" ")));

    private static final String RANDOM_JAVA = "(int) (Math.random() * 20) + 1";
    private static final Map<String, String> ELEMENT = new HashMap<String, String>();
    private static final Map<String, String> BOXED = new HashMap<String, String>();
    static {
        ELEMENT.put("int", "int"); ELEMENT.put("String", "string"); ELEMENT.put("char", "char"); ELEMENT.put("double", "double");
        BOXED.put("Integer", "int"); BOXED.put("Double", "double"); BOXED.put("Character", "char"); BOXED.put("String", "string");
    }

    private static final Pattern STRINGS = Pattern.compile("\"(?:[^\"\\\\]|\\\\.)*\"|'(?:[^'\\\\]|\\\\.)*'");

    /** שם חוקי גם ב-C# (לא מילה שמורה) */
    public static boolean isIdentifier(String s) {
        return Scaffold.isIdentifier(s) && !CS_KEYWORDS.contains(s);
    }

    private static String cap(String w) {
        return Character.toUpperCase(w.charAt(0)) + w.substring(1);
    }

    private interface Fn { String apply(String part); }

    /** מחיל המרה רק על מה שמחוץ למחרוזות ("...", '...') */
    private static String outsideStrings(String s, Fn f) {
        StringBuilder sb = new StringBuilder();
        Matcher m = STRINGS.matcher(s);
        int last = 0;
        while (m.find()) {
            sb.append(f.apply(s.substring(last, m.start()))).append(m.group());
            last = m.end();
        }
        sb.append(f.apply(s.substring(last)));
        return sb.toString();
    }

    private static String lit(String s) {
        return Matcher.quoteReplacement(s);
    }

    /** ‎.method( → ‎.Method( */
    private static String capitalizeCalls(String c) {
        Matcher m = Pattern.compile("\\.([a-z]\\w*)\\(").matcher(c);
        StringBuffer sb = new StringBuffer();
        while (m.find()) m.appendReplacement(sb, lit("." + cap(m.group(1)) + "("));
        m.appendTail(sb);
        return sb.toString();
    }

    /** שורת קוד / הערה: סוגים, שמות פעולות באות גדולה, length, מערכים דו-ממדיים */
    private static String convertText(String s, final Map<String, String> mats, final Map<String, String> arrs) {
        return outsideStrings(s, new Fn() {
            public String apply(String c) {
                c = c.replace("System.out.println(", "§WL§(")
                     .replace("Arrays.toString(", "§ATS§(")
                     .replace("Arrays.deepToString(", "§ADS§(")
                     .replace(RANDOM_JAVA, "§RND§");
                for (String m : mats.keySet()) {
                    c = c.replaceAll("\\b" + m + "\\[[^\\[\\]]+\\]\\.length", lit(m + ".GetLength(1)"))
                         .replaceAll("\\b" + m + "\\.length\\b", lit(m + ".GetLength(0)"))
                         .replaceAll("\\b" + m + "\\[([^\\[\\]]+)\\]\\[([^\\[\\]]+)\\]", lit(m) + "[$1, $2]");
                }
                c = c.replaceAll("\\bnew (\\w+)\\[([^\\[\\]]+)\\]\\[([^\\[\\]]+)\\]", "new $1[$2, $3]")
                     .replace("[][]", "[,]");
                c = c.replaceAll("\\bboolean\\b", "bool").replaceAll("\\bString\\b", "string")
                     .replace("<Integer>", "<int>").replace("<Double>", "<double>").replace("<Character>", "<char>")
                     .replaceAll("\\bmain\\(", "Main(")
                     .replaceAll("\\.length\\b", ".Length");
                c = capitalizeCalls(c);
                c = c.replace("§WL§", "Console.WriteLine").replace("§RND§", "Random.Shared.Next(1, 21)");
                Matcher a = Pattern.compile("§ATS§\\((\\w+)\\)").matcher(c);
                StringBuffer sb = new StringBuffer();
                while (a.find()) {
                    String v = a.group(1);
                    a.appendReplacement(sb, lit(arrs.containsKey(v)
                            ? "\"[\" + string.Join<" + arrs.get(v) + ">(\", \", " + v + ") + \"]\""
                            : "\"[\" + string.Join(\", \", " + v + ") + \"]\""));
                }
                a.appendTail(sb);
                c = sb.toString();
                Matcher d = Pattern.compile("§ADS§\\((\\w+)\\)").matcher(c);
                sb = new StringBuffer();
                while (d.find()) {
                    String v = d.group(1);
                    d.appendReplacement(sb, lit("\"[\" + string.Join(\", \", " + v + ".Cast<" + (mats.containsKey(v) ? mats.get(v) : "int") + ">()) + \"]\""));
                }
                d.appendTail(sb);
                return sb.toString();
            }
        });
    }

    /** Java → C#. מזהה לבד איזה משתנים הם מערכים דו-ממדיים (T[][] name) */
    public static String toCs(String java) {
        // פעולות עזר גנריות: public static <T> int size(Stack<T> s)  →  public static int Size<T>(Stack<T> s), והקריאות size(...) → Size(...)
        if (java.contains("public static <T> ")) {
            Matcher d = Pattern.compile("public static <T> (.+?) (size|duplicate)\\(").matcher(java);
            StringBuffer sb = new StringBuffer();
            while (d.find()) d.appendReplacement(sb, lit("public static " + d.group(1) + " " + cap(d.group(2)) + "<T>("));
            d.appendTail(sb);
            d = Pattern.compile("(?m)(^|[^.\\w])(size|duplicate)\\(").matcher(sb.toString());
            sb = new StringBuffer();
            while (d.find()) d.appendReplacement(sb, lit(d.group(1) + cap(d.group(2)) + "("));
            d.appendTail(sb);
            java = sb.toString();
        }
        Map<String, String> mats = new HashMap<String, String>(), arrs = new HashMap<String, String>();
        Matcher m = Pattern.compile("\\b(\\w+)\\[\\]\\[\\]\\s+(\\w+)").matcher(java);
        while (m.find()) mats.put(m.group(2), ELEMENT.containsKey(m.group(1)) ? ELEMENT.get(m.group(1)) : m.group(1));
        m = Pattern.compile("\\b(\\w+)\\[\\]\\s+(\\w+)").matcher(java);
        while (m.find()) arrs.put(m.group(2), ELEMENT.containsKey(m.group(1)) ? ELEMENT.get(m.group(1)) : m.group(1));
        m = Pattern.compile("\\b(\\w+)<(\\w+)>\\[\\]\\s+(\\w+)").matcher(java);   // מערך של מבנים: Stack<Integer>[] arr
        while (m.find()) arrs.put(m.group(3), m.group(1) + "<" + (BOXED.containsKey(m.group(2)) ? BOXED.get(m.group(2)) : m.group(2)) + ">");

        List<String> out = new ArrayList<String>();
        Pattern structArray = Pattern.compile("\\b(\\w+)<(\\w+)>\\[\\]\\s+(\\w+)\\s*=\\s*new \\1\\[");
        for (String line : java.split("\n", -1)) {
            if (line.matches("^import java\\.util\\.Arrays;.*")) continue;
            if (line.matches("^\\s*@SuppressWarnings.*")) continue;
            if (line.matches("^import unit4\\.collectionsLib\\.\\*;.*")) { out.add("using System;"); out.add("using Unit4.CollectionsLib;"); continue; }
            line = structArray.matcher(line).replaceFirst("$1<$2>[] $3 = new $1<$2>[");
            String code = line, comment = "";
            // הערה: מהראשון // שמחוץ למחרוזת
            int cut = -1, acc = 0, last = 0;
            Matcher sm = STRINGS.matcher(line);
            while (true) {
                boolean found = sm.find();
                String part = line.substring(last, found ? sm.start() : line.length());
                int k = part.indexOf("//");
                if (k >= 0) { cut = acc + k; break; }
                acc += part.length();
                if (!found) break;
                acc += sm.group().length();
                last = sm.end();
            }
            if (cut >= 0) { code = line.substring(0, cut); comment = line.substring(cut); }
            String c = convertText(code, mats, arrs);
            if (!comment.isEmpty()) {
                // שורת רמז עם שמות פעולות בלי נקודה: // Student: getName()  getAverage() ...
                if (line.matches("^\\s*// (Student|Car):.*") && code.trim().isEmpty()) {
                    Matcher h = Pattern.compile("\\b([a-z]\\w*)\\(").matcher(comment);
                    StringBuffer sb = new StringBuffer();
                    while (h.find()) h.appendReplacement(sb, lit(cap(h.group(1)) + "("));
                    h.appendTail(sb);
                    comment = sb.toString();
                }
                c += convertText(comment, mats, arrs);
            }
            out.add(c);
        }
        String text = String.join("\n", out);
        if (text.contains(".Cast<") && !text.contains("using System.Linq;"))
            text = text.replaceFirst("using Unit4\\.CollectionsLib;", "using System.Linq;\nusing Unit4.CollectionsLib;");
        return text;
    }

    public static final class Result {
        public final String code;
        public final int cursorLine;
        Result(String code, int cursorLine) { this.code = code; this.cursorLine = cursorLine; }
    }

    /** כמו Scaffold.generate, אבל קוד C# */
    public static Result generate(Scaffold.Options o) {
        if (!isIdentifier(o.className)) throw new IllegalArgumentException("שם המחלקה חייב להיות באנגלית, בלי רווחים, להתחיל באות, ולא להיות מילה שמורה של C#");
        if (!isIdentifier(o.functionName)) throw new IllegalArgumentException("שם הפעולה חייב להיות באנגלית, בלי רווחים, להתחיל באות, ולא להיות מילה שמורה של C#");
        if (o.functionName.equals("Main")) throw new IllegalArgumentException("Main כבר קיימת בקובץ — בחרו שם אחר לפעולה");
        if (o.functionName.equals(o.className)) throw new IllegalArgumentException("שם הפעולה לא יכול להיות זהה לשם המחלקה");
        if ((o.helperSize && o.functionName.equals("Size")) || (o.helperCopy && o.functionName.equals("Duplicate")))
            throw new IllegalArgumentException("השם " + o.functionName + " תפוס על ידי פעולת העזר — בחרו שם אחר לפעולה");
        Scaffold.Result g = Scaffold.generate(o);
        String code = toCs(g.code);
        String[] lines = code.split("\n", -1);
        int todo = -1;
        for (int i = 0; i < lines.length && todo < 0; i++) if (lines[i].contains("TODO:")) todo = i;
        return new Result(code, todo >= 0 ? todo + 1 : g.cursorLine);
    }

    /** null אם השם חוקי ב-C# ולא מתנגש במשהו שכבר מוגדר במקום הסמן (offset = מיקום הסמן, -1 = לא ידוע); אחרת הודעה בעברית */
    public static String checkName(String name, String text, int offset) {
        return il.lamerhav.dsviz.NameCheck.checkName(name, text, offset, new il.lamerhav.dsviz.NameCheck.Ident() {
            public boolean ok(String s) { return isIdentifier(s); }
        });
    }

    /** שם מוצע למשתנה חדש (stk, stk2... / q1, q2...) שלא מתנגש בקוד */
    public static String suggestName(String structure, String text, int offset) {
        return il.lamerhav.dsviz.NameCheck.suggestName(Scaffold.snippetName(structure), text, offset, new il.lamerhav.dsviz.NameCheck.Ident() {
            public boolean ok(String s) { return isIdentifier(s); }
        });
    }

    /** קטע קוד להכנסה (בלי using — מוסיפים לבד) */
    public static String snippet(Scaffold.SnippetOptions o) {
        return toCs(Scaffold.snippet(o));
    }
}
