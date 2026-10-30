package unit4.collectionsLib;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import unit4.collectionsLib.Model.LinearView;
import unit4.collectionsLib.Model.ProblemView;
import unit4.collectionsLib.Model.Step;
import unit4.collectionsLib.Model.View;

/**
 * התוכנית נעצרה בשגיאה: מהטקסט של השגיאה (Java או C#) בונים כרטיס שמראה לתלמיד מה קרה ומה חסר —
 * pop על מחסנית ריקה, remove על תור ריק, p שהוא null, תא שלא קיים במערך, רקורסיה שלא נעצרת —
 * ומסמנים את המבנה עצמו (המחסנית הריקה מקבלת "?" אדום במקום שבו היה צריך להיות איבר).
 */
final class Problems {
    private Problems() {}

    private static final Pattern EMPTY = Pattern.compile("(?i)\\b(pop|top|remove|head)\\(\\) על (מחסנית|תור) ריק[ה]? \\(([^)]*)\\)");
    private static final Pattern NPE_INVOKE = Pattern.compile("Cannot invoke \"(?:[\\w$]+\\.)*([\\w$]+)\\.([\\w$]+)\\([^\"]*\\)\"");
    private static final Pattern NPE_FIELD = Pattern.compile("Cannot (?:read|assign) field \"([\\w$]+)\"");
    private static final Pattern NPE_ARRAY = Pattern.compile("Cannot (?:load from|store to|read the array length)");
    private static final Pattern NPE_VAR = Pattern.compile("because \"([\\w$.<>]+)\" is null");
    private static final Pattern NPE_RET = Pattern.compile("because the return value of \"(?:[\\w$]+\\.)*([\\w$]+)\\.([\\w$]+)\\([^\"]*\\)\" is null");
    private static final Pattern INDEX = Pattern.compile("Index (-?\\d+) out of bounds for length (\\d+)");

    /** מוסיף לצעד השגיאה את הכרטיס (בראש התצוגה), ומסמן את המבנה שנכשל. מחזיר את הכרטיס (או null) */
    static ProblemView attach(Step s) {
        return attach(s, null);
    }

    private static final Pattern CALL = Pattern.compile("\\b([a-z_][\\w]*)\\.([A-Za-z_]\\w*)\\s*\\(");

    /** כמו attach, ועם השורה שרצה לפני השגיאה: ב-C# ההודעה של null לא אומרת מי היה null — מנחשים מהשורה (p.GetValue()) */
    static ProblemView attach(Step s, String line) {
        if (s == null || s.text == null) return null;
        ProblemView p = explain(s.text);
        if (p == null) return null;
        if (p.kind.equals("null") && p.name == null && line != null) {
            Matcher c = CALL.matcher(line.replaceFirst("^שורה \\d+:\\s*", ""));
            if (c.find()) {
                String m = c.group(2), lm = m.toLowerCase();
                String type = lm.matches("getvalue|getnext|hasnext|setnext|setvalue") ? "Node"
                        : lm.matches("getleft|getright|hasleft|hasright|setleft|setright") ? "BinNode"
                        : lm.matches("pop|push|top") ? "Stack" : lm.matches("insert|remove|head") ? "Queue" : "Object";
                ProblemView q = explain("NullPointerException: Cannot invoke \"" + type + "." + m + "()\" because \"" + c.group(1) + "\" is null");
                if (q != null) p = q;
            }
        }
        if (p.kind.equals("empty-stack") || p.kind.equals("empty-queue")) {
            boolean stack = p.kind.equals("empty-stack");
            LinearView hit = null, onlyEmpty = null;
            int empties = 0;
            for (View v : s.views) {
                if (!(v instanceof LinearView) || ((LinearView) v).stack != stack) continue;
                LinearView lv = (LinearView) v;
                if (p.name != null && named(lv.title, p.name)) hit = lv;
                if (lv.items.isEmpty()) { empties++; onlyEmpty = lv; }
            }
            // השם בהודעה הוא לפעמים שם פנימי (stack1) — אם יש רק מבנה ריק אחד מהסוג הזה, זה הוא, ובשם של המשתנה
            if (hit == null && empties == 1 && onlyEmpty.title != null && !onlyEmpty.title.isEmpty()) {
                String alias = onlyEmpty.title.split(",\\s*")[0].trim();
                ProblemView q = explain(p.name != null ? s.text.replace("(" + p.name + ")", "(" + alias + ")") : s.text);
                if (q != null) p = q;
                hit = onlyEmpty;
            }
            if (hit != null) {
                hit.error = p.op;
                hit.focus = true;
            }
        }
        s.views.add(0, p);
        return p;
    }

    private static boolean named(String title, String name) {
        if (title == null) return false;
        for (String t : title.split(",\\s*")) if (t.trim().equals(name)) return true;
        return false;
    }

    /** הכרטיס לטקסט של שגיאה ("שגיאה: RuntimeException: pop() על מחסנית ריקה (s)") */
    static ProblemView explain(String text) {
        String t = text.replaceFirst("^‏?שגיאה:\\s*", "");
        ProblemView p = new ProblemView();
        Matcher m = EMPTY.matcher(t);
        if (m.find()) {
            String op = m.group(1), name = m.group(3).trim();
            boolean stack = m.group(2).equals("מחסנית"), cs = Character.isUpperCase(op.charAt(0));
            p.kind = stack ? "empty-stack" : "empty-queue";
            p.name = name.isEmpty() ? null : name;
            p.op = op + "()";
            String nm = p.name == null ? "" : " " + p.name;
            String struct = stack ? "המחסנית" : "התור";
            p.what = code((p.name == null ? "" : p.name + ".") + op + "()") + " על " + (stack ? "מחסנית ריקה" : "תור ריק");
            boolean takes = op.equalsIgnoreCase("pop") || op.equalsIgnoreCase("remove");
            p.why = "ב" + struct + nm + " אין אף איבר" + (takes ? ", ולכן אין מה להוציא." : stack ? ", ולכן אין ראש מחסנית לקרוא." : ", ולכן אין ראש תור לקרוא.");
            String n = p.name == null ? (stack ? "s" : "q") : p.name;
            p.hint = "בודקים קודם שיש בו משהו:  " + code("while (!" + n + "." + (cs ? "IsEmpty" : "isEmpty") + "())") + "  — או סופרים כמה איברים יש לפני הלולאה.";
            return p;
        }
        if (t.startsWith("NullPointerException") || t.startsWith("NullReferenceException")) {
            p.kind = "null";
            Matcher inv = NPE_INVOKE.matcher(t), var = NPE_VAR.matcher(t), ret = NPE_RET.matcher(t), fld = NPE_FIELD.matcher(t);
            String type = null;
            if (inv.find()) { type = inv.group(1); p.op = inv.group(2) + "()"; }
            else if (fld.find()) p.op = fld.group(1);
            else if (NPE_ARRAY.matcher(t).find()) p.op = "[ ]";
            if (var.find()) {
                String v = var.group(1);
                if (!v.startsWith("<")) p.name = v.startsWith("this.") ? v.substring(5) : v;
            } else if (ret.find()) {
                p.name = ret.group(2) + "()";
                if (type == null) type = ret.group(1);
            }
            String who = p.name != null ? p.name : "המשתנה";
            p.what = p.name != null && p.name.endsWith(")") ? code(p.name) + " החזיר null, ואין על מה לקרוא" + (p.op != null ? " ל-" + code(p.op) : "")
                    : p.name != null && p.op != null ? code(p.name + "." + p.op) + " — אבל " + p.name + " הוא null"
                    : p.op != null ? code(p.op) + " על משהו שהוא null" : "ניסיון להשתמש במשהו שהוא null";
            if ("Node".equals(type)) {
                p.why = who + " לא מצביע על אף חוליה — כנראה עברנו את סוף הרשימה (אחרי החוליה האחרונה בא null).";
                p.hint = "לפני שמשתמשים בחוליה בודקים שהיא קיימת:  " + code("while (p != null)") + "  — או " + code("p.hasNext()") + " לפני " + code("p.getNext().getValue()") + ".";
            } else if ("BinNode".equals(type) || "BinTreeNode".equals(type)) {
                p.why = who + " לא מצביע על אף צומת — ירדנו מתחת לעלה (לעלה אין ילדים).";
                p.hint = "ברקורסיה על עץ מתחילים בתנאי עצירה:  " + code("if (t == null) return ...;") + "  — או בודקים " + code("t.hasLeft()") + " / " + code("t.hasRight()") + " לפני שיורדים.";
            } else if ("Stack".equals(type) || "Queue".equals(type)) {
                p.why = who + " עדיין לא נוצר — אין " + ("Stack".equals(type) ? "מחסנית" : "תור") + " שאפשר להשתמש בו.";
                p.hint = "יוצרים לפני השימוש:  " + code("Stack".equals(type) ? "Stack<Integer> " + who + " = new Stack<Integer>();" : "Queue<Integer> " + who + " = new Queue<Integer>();");
            } else if ("String".equals(type)) {
                p.why = "המחרוזת " + who + " היא null — אין בה אף תו.";
                p.hint = "נותנים לה ערך לפני השימוש (למשל \"\"), או בודקים  " + code("if (" + who + " != null)") + ".";
            } else {
                p.why = who + " לא מצביע על אף אובייקט, ולכן אי אפשר לקרוא לפעולות שלו.";
                p.hint = "בודקים לפני השימוש:  " + code("if (" + (p.name != null && !p.name.endsWith(")") ? p.name : "x") + " != null)") + "  — או יוצרים אותו עם new.";
            }
            return p;
        }
        m = INDEX.matcher(t);
        if (t.startsWith("ArrayIndexOutOfBounds") || t.startsWith("IndexOutOfRange")) {
            p.kind = "index";
            if (m.find()) {
                p.index = Integer.parseInt(m.group(1));
                p.length = Integer.parseInt(m.group(2));
                p.what = "גישה לתא " + p.index + " במערך — אבל אין תא כזה";
                p.why = p.length == 0 ? "המערך ריק — אין בו אף תא." : "במערך יש " + p.length + " תאים: מ-0 עד " + (p.length - 1) + ".";
            } else {
                p.what = "גישה לתא שלא קיים במערך";
                p.why = "האינדקס קטן מ-0, או גדול מהתא האחרון (האורך פחות 1).";
            }
            p.hint = "בלולאה על מערך:  " + code("i < arr.length") + "  (ולא <=), ומתחילים מ-0.";
            return p;
        }
        if (t.startsWith("StringIndexOutOfBounds")) {
            p.kind = "index";
            p.what = "גישה לתו שלא קיים במחרוזת";
            p.why = "האינדקסים במחרוזת הם מ-0 עד length() פחות 1.";
            p.hint = "בלולאה על מחרוזת:  " + code("i < s.length()") + "  (ולא <=).";
            return p;
        }
        if (t.startsWith("StackOverflowError") || t.contains("עומק הקריאות גדול מדי")) {
            p.kind = "recursion";
            p.what = "הרקורסיה לא נעצרת";
            p.why = "הפעולה קוראת לעצמה שוב ושוב בלי סוף, עד שהזיכרון של הקריאות נגמר.";
            p.hint = "בודקים שיש תנאי עצירה, ושכל קריאה מתקרבת אליו (למשל " + code("n - 1") + ", או " + code("t.getLeft()") + ").";
            return p;
        }
        if (t.startsWith("ArithmeticException") && t.contains("zero")) {
            p.what = "חלוקה ב-0";
            p.why = "אי אפשר לחלק מספר שלם ב-0 (למשל ממוצע של 0 איברים).";
            p.hint = "בודקים לפני החלוקה:  " + code("if (count != 0)");
            return p;
        }
        if (t.startsWith("DivideByZeroException")) {
            p.what = "חלוקה ב-0";
            p.why = "אי אפשר לחלק ב-0 (למשל ממוצע של 0 איברים).";
            p.hint = "בודקים לפני החלוקה:  " + code("if (count != 0)");
            return p;
        }
        p.what = "התוכנית נעצרה בשגיאה";
        p.why = t.replaceAll("\\s+\\(.*\\)\\s*$", "");
        p.hint = "השורה שבה זה קרה מסומנת בקוד.";
        return p;
    }

    /** קוד בתוך משפט בעברית — משמאל לימין (LRE ... PDF), כדי שהסוגריים לא יתהפכו */
    static String code(String c) {
        return "\u202A" + c + "\u202C";
    }

    /** בשביל הבדיקות: איזה סוג כרטיס יוצא לכל טקסט */
    static String kindOf(String text) {
        ProblemView p = explain(text);
        return p.kind + (p.name != null ? ":" + p.name : "") + (p.op != null ? ":" + p.op : "");
    }
}
