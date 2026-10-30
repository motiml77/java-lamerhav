package unit4.collectionsLib;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import unit4.collectionsLib.RecursionPanel.Call;
import unit4.collectionsLib.RecursionPanel.Entry;

/**
 * איזו רקורסיה יש בתוכנית — לפי כל ההרצה (כך הסוג לא משתנה באמצע):
 * - פשוטה: כל קריאה פותחת לכל היותר קריאה אחת, והעבודה נעשית בחזרה (return n * fact(n - 1)).
 * - זנב: הקריאה היא הדבר האחרון — התשובה של הקריאה העמוקה עוברת כמו שהיא עד למעלה (return sum(n - 1, acc + n)).
 * - כפולה / מרובה: קריאה אחת פותחת שתיים או יותר (fib, ספירה בעץ בינארי) — הקריאות יוצרות עץ.
 * - הדדית: פעולה א קוראת לפעולה ב, ו-ב קוראת ל-א (isEven / isOdd).
 * ובנוסף: על איזה מבנה הרקורסיה רצה (עץ בינארי, שרשרת חוליות, מחסנית, מחרוזת, מערך).
 */
final class RecursionKind {
    private RecursionKind() {}

    enum Type { NONE, LINEAR, TAIL, BRANCHING, MUTUAL }

    Type type = Type.NONE;
    final Set<String> methods = new LinkedHashSet<String>();   // הפעולות שבמעגל הרקורסיה (בסדר ההופעה)
    String structure;            // "עץ בינארי" / "שרשרת חוליות" / ... או null
    boolean mutualBranching;     // הדדית, וגם קריאה שפותחת כמה קריאות
    int maxBranch;               // הכי הרבה קריאות רקורסיביות מקריאה אחת
    int maxDepth;                // העומק המקסימלי של הרקורסיה (1 = הקריאה הראשונה)
    int total;                   // כמה קריאות רקורסיביות היו בכלל

    private static int cachedSteps = -1;
    private static RecursionKind cached;

    /** הסוג לכל ההרצה (מחושב פעם אחת לכל מספר צעדים) */
    static synchronized RecursionKind of() {
        int n = Recorder.stepCount();
        if (n == cachedSteps && cached != null) return cached;
        RecursionKind k = n == 0 ? new RecursionKind() : classify(RecursionPanel.build(n - 1, null));
        cachedSteps = n;
        cached = k;
        return k;
    }

    static String key(Call c) {
        return c.cls + "#" + c.method;
    }

    /** האם הקריאה שייכת לרקורסיה (הפעולה שלה במעגל) */
    boolean in(Call c) {
        return c != null && methods.contains(key(c));
    }

    /** הקריאות הרקורסיביות שהקריאה הזו פתחה */
    List<Call> kids(Call c) {
        List<Call> res = new ArrayList<Call>();
        for (Call k : c.children) if (in(k)) res.add(k);
        return res;
    }

    /** תנאי העצירה: קריאה רקורסיבית שהסתיימה בלי לפתוח קריאה רקורסיבית נוספת */
    boolean base(Call c) {
        return in(c) && c.done && kids(c).isEmpty() && c.parent != null && in(c.parent);
    }

    static RecursionKind classify(List<Call> all) {
        RecursionKind k = new RecursionKind();
        // המעגלים: קריאה שאחת הקריאות מעליה היא אותה פעולה — כל הפעולות בדרך ביניהן שייכות לרקורסיה
        boolean mutual = false;
        for (Call c : all) {
            for (Call a = c.parent; a != null; a = a.parent) {
                if (!key(a).equals(key(c))) continue;
                Set<String> cycle = new LinkedHashSet<String>();
                for (Call b = c; b != a; b = b.parent) cycle.add(key(b));
                if (cycle.size() > 1) mutual = true;
                for (Call b = c; b != a; b = b.parent) k.methods.add(key(b));
                k.methods.add(key(a));
                break;
            }
        }
        if (k.methods.isEmpty()) return k;
        // לפי סדר ההופעה הראשונה בהרצה
        Set<String> ordered = new LinkedHashSet<String>();
        for (Call c : all) if (k.methods.contains(key(c))) ordered.add(key(c));
        k.methods.clear();
        k.methods.addAll(ordered);
        for (Call c : all) {
            if (!k.in(c)) continue;
            k.total++;
            k.maxBranch = Math.max(k.maxBranch, k.kids(c).size());
            int d = 0;
            for (Call b = c; b != null && k.in(b); b = b.parent) d++;
            k.maxDepth = Math.max(k.maxDepth, d);
        }
        if (mutual) {
            k.type = Type.MUTUAL;
            k.mutualBranching = k.maxBranch >= 2;
        } else if (k.maxBranch >= 2) {
            k.type = Type.BRANCHING;
        } else {
            k.type = tail(k, all) ? Type.TAIL : Type.LINEAR;
        }
        k.structure = structure(k, all);
        return k;
    }

    /** רקורסיית זנב: בכל קריאה שהסתיימה, הקריאה הרקורסיבית היא השורה האחרונה, והערך שלה מוחזר כמו שהוא */
    private static boolean tail(RecursionKind k, List<Call> all) {
        int checked = 0;
        for (Call p : all) {
            if (!k.in(p) || !p.done) continue;
            for (Call c : k.kids(p)) {
                if (!c.done) continue;
                Entry e = null;
                for (Entry t : p.trail) if (t.calls.contains(c)) e = t;
                if (e == null) return false;
                for (int i = p.trail.indexOf(e) + 1; i < p.trail.size(); i++)   // אחרי הקריאה: רק "}" של סוף הפעולה
                    if (!p.trail.get(i).code.matches("\\}*")) return false;
                boolean same = c.returned == null ? p.returned == null : c.returned.equals(p.returned);
                if (!same || !onlyCall(e.code, c.method, p.returned != null)) return false;
                checked++;
            }
        }
        return checked > 0;
    }

    /** "return f(n - 1, acc);" או "f(n - 1);" — הקריאה היא כל הביטוי (אין חישוב אחריה) */
    static boolean onlyCall(String code, String method, boolean returns) {
        String t = LoopViz.blank(code).trim();
        Matcher m = Pattern.compile((returns ? "^(?:\\}\\s*)?(?:else\\s+)?(?:if\\s*\\(.*\\)\\s*)?return\\s+" : "^(?:\\}\\s*)?(?:else\\s+)?(?:if\\s*\\(.*\\)\\s*)?")
                + "(?:this\\s*\\.\\s*)?" + Pattern.quote(method) + "\\s*\\(").matcher(t);
        if (!m.find()) return false;
        int depth = 1, i = m.end();
        for (; i < t.length() && depth > 0; i++) {
            char ch = t.charAt(i);
            if (ch == '(') depth++;
            else if (ch == ')') depth--;
        }
        return depth == 0 && t.substring(i).trim().matches(";\\s*\\}?");
    }

    /** על איזה מבנה: לפי מה שהקריאה הרקורסיבית הראשונה קיבלה */
    private static String structure(RecursionKind k, List<Call> all) {
        for (Call c : all) {
            if (!k.in(c) || c.params == null) continue;
            for (String[] p : c.params) {
                if (p[0].equals("this")) continue;
                String v = p[1];
                if (v.startsWith("BinNode")) return "עץ בינארי";
                if (v.startsWith("Node")) return "שרשרת חוליות";
                if (v.startsWith("Stack")) return "מחסנית";
                if (v.startsWith("Queue")) return "תור";
                if (v.startsWith("\"")) return "מחרוזת";
                if (v.startsWith("[")) return "מערך";
            }
            return null;
        }
        return null;
    }

    /** השם בעברית */
    String title() {
        switch (type) {
            case LINEAR: return "רקורסיה פשוטה";
            case TAIL: return "רקורסיית זנב";
            case BRANCHING: return maxBranch == 2 ? "רקורסיה כפולה" : "רקורסיה מרובה";
            case MUTUAL: return "רקורסיה הדדית";
            default: return "";
        }
    }

    /** משפט אחד שמסביר את הסוג */
    String explain() {
        switch (type) {
            case LINEAR:
                return "כל קריאה פותחת קריאה אחת קטנה יותר, עד תנאי העצירה. העבודה נעשית בדרך חזרה — כל קריאה משתמשת בתשובה שקיבלה.";
            case TAIL:
                return "הקריאה הרקורסיבית היא הדבר האחרון שהפעולה עושה: התשובה מתנאי העצירה עולה כמו שהיא עד למעלה.";
            case BRANCHING:
                return "כל קריאה פותחת " + (maxBranch == 2 ? "שתי קריאות" : "כמה קריאות") + " — הקריאות יוצרות עץ. מסיימים ענף שלם, ורק אז עוברים לענף הבא.";
            case MUTUAL:
                return "הפעולות קוראות זו לזו לסירוגין (" + names() + ") — כל פעולה בצבע משלה.";
            default:
                return "";
        }
    }

    /** "isEven ← → isOdd" */
    String names() {
        StringBuilder b = new StringBuilder();
        for (String m : methods) b.append(b.length() > 0 ? ", " : "").append(m.substring(m.indexOf('#') + 1));
        return "‎" + b + "‎";
    }

    /** איפה אנחנו עכשיו (פשוטה / זנב): ירידה, תנאי העצירה, או חזרה */
    String phase(List<Call> upToNow, Call active) {
        if (type == Type.NONE || active == null) return "";
        int depth = 0, deepest = 0;
        boolean returned = false;
        Call base = null;
        for (Call c : upToNow) {
            if (!in(c)) continue;
            int d = 0;
            for (Call b = c; b != null && in(b); b = b.parent) d++;
            deepest = Math.max(deepest, d);
            if (c.done && c != active) returned = true;
            if (base(c) && base == null) base = c;
        }
        for (Call b = active; b != null && in(b); b = b.parent) depth++;
        if (type == Type.BRANCHING || type == Type.MUTUAL && mutualBranching) {
            int n = 0, done = 0;
            for (Call c : upToNow) if (in(c)) { n++; if (c.done) done++; }
            return "עד עכשיו " + n + " קריאות (" + done + " הסתיימו)  ·  עומק נוכחי " + depth;
        }
        if (!in(active)) return returned ? "הרקורסיה הסתיימה — התשובה חזרה לקריאה הראשונה" : "";
        if (active.done && kids(active).isEmpty() && active.parent != null && in(active.parent))
            return "■ תנאי העצירה — קריאה ‎#" + active.id + "‎ מחזירה" + (active.returned != null ? " " + active.returned : "") + " בלי לקרוא שוב";
        if (!returned) return "↓ ירידה — כל קריאה מחכה לתשובה מהקריאה שאחריה  ·  עומק " + depth;
        if (type == Type.TAIL) return "↑ חזרה — אין מה לחשב: כל קריאה מחזירה את אותה תשובה למעלה  ·  עומק " + depth;
        return "↑ חזרה — כל קריאה משתמשת בתשובה שקיבלה ומחזירה למעלה  ·  עומק " + depth;
    }

    /** בשביל הבדיקות (מצב טקסט) */
    static String dump() {
        RecursionKind k = of();
        if (k.type == Type.NONE) return "";
        StringBuilder b = new StringBuilder("  [רקורסיה: ").append(k.title()).append(" | פעולות ").append(k.names().replace("‎", ""))
                .append(" | עומק ").append(k.maxDepth).append(" | קריאות ").append(k.total);
        if (k.structure != null) b.append(" | על ").append(k.structure);
        List<Call> all = RecursionPanel.build(Recorder.stepCount() - 1, null);
        List<String> bases = new ArrayList<String>();
        for (Call c : all) if (k.base(c)) bases.add("#" + c.id + (c.returned != null ? "→" + c.returned : ""));
        b.append(" | עצירה ").append(bases.size() > 6 ? bases.subList(0, 6) + "…" : bases.toString()).append("]\n");
        return b.toString();
    }
}
