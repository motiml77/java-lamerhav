package il.lamerhav.dsviz.intellij;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * הודעות השגיאה הנפוצות של javac (כמו שהן מגיעות מהבנייה של IntelliJ) — בעברית, לחלון "יש שגיאות בקוד" של ההרצה הוויזואלית.
 * (ב-Eclipse יש תרגום מלא לפי מספר הבעיה — problems_he.properties; javac לא נותן מספרים, אז כאן לפי הטקסט.)
 * הודעה שלא מוכרת נשארת באנגלית.
 */
final class JavacHebrew {
    private JavacHebrew() {}

    private static final String[][] RULES = {
        { "missing return statement", "חסרה הוראת return — הפעולה צריכה להחזיר ערך בכל מסלול (גם אחרי לולאה או if)" },
        { "';' expected", "חסר ; בסוף הפקודה" },
        { "'(.+)' expected", "חסר $1" },
        { "cannot find symbol", "השם לא מוכר — בדקו את האיות (אותיות גדולות וקטנות), שהמשתנה הוגדר, ושיש import" },
        { "incompatible types: (.+) cannot be converted to (.+)", "אי התאמה בטיפוסים: אי אפשר להכניס $1 למשתנה מטיפוס $2" },
        { "incompatible types: possible lossy conversion from (.+) to (.+)", "אי התאמה בטיפוסים: המרה מ-$1 ל-$2 עלולה לאבד מידע (צריך המרה מפורשת)" },
        { "variable (\\w+) might not have been initialized", "המשתנה $1 עוד לא קיבל ערך — צריך לתת לו ערך התחלתי" },
        { "variable (\\w+) is already defined in method (.+)", "המשתנה $1 כבר מוגדר בפעולה $2 — בחרו שם אחר" },
        { "unreachable statement", "הפקודה הזאת אף פעם לא תתבצע (היא אחרי return / break)" },
        { "class, interface, enum, or record expected", "יש סוגר } מיותר, או קוד מחוץ למחלקה" },
        { "reached end of file while parsing", "חסר סוגר } בסוף הקובץ" },
        { "illegal start of expression", "ביטוי לא חוקי — בדרך כלל חסר סוגר או ; בשורה הקודמת" },
        { "not a statement", "זו לא פקודה — אולי חסר = או קריאה לפעולה" },
        { "non-static method (.+) cannot be referenced from a static context", "אי אפשר לקרוא לפעולה $1 (שאינה static) מתוך פעולה static — הוסיפו static לכותרת שלה" },
        { "non-static variable (\\w+) cannot be referenced from a static context", "אי אפשר להשתמש במשתנה $1 (שאינו static) מתוך פעולה static" },
        { "bad operand types for binary operator '(.+)'", "אי אפשר להפעיל את הפעולה $1 על הטיפוסים האלה" },
        { "unclosed string literal", "חסרות מירכאות \" בסוף המחרוזת" },
        { "method (\\w+) in class (\\w+) cannot be applied to given types;?", "הפעולה $1 במחלקה $2 נקראה עם פרמטרים לא מתאימים (מספר או טיפוס)" },
        { "(.+) has private access in (.+)", "$1 פרטי (private) ב-$2, וגם מחלקה שיורשת ממנה לא רואה אותו — ניגשים אליו דרך פעולה (get...), או משנים ל-protected" },
        // הורשה ופולימורפיזם (כמו ב-VS Code — ui.js, ו"המורה בעברית" של Eclipse)
        { "(\\w+) is not abstract and does not override abstract method (\\w+)\\((.*)\\) in (\\w+)",
          "$1 יורשת מ-$4 את הפעולה המופשטת $2(), ולא כתבה אותה. פעולה מופשטת היא הבטחה: כל מחלקה שיורשת חייבת לכתוב אותה (או להיות בעצמה abstract)" },
        { "(\\w+) is abstract; cannot be instantiated", "$1 מופשטת (abstract) או ממשק — אי אפשר ליצור ממנה עצם. יוצרים עצם של מחלקה שיורשת ממנה, ושומרים אותו בהפניה מסוג $1" },
        { "call to (super|this) must be first statement in constructor", "הקריאה ל-$1(...) חייבת להיות השורה הראשונה בבנאי: קודם נבנה החלק של מחלקת-העל, ורק אחריו החלק של המחלקה שיורשת" },
        { "cannot inherit from final (\\w+)", "אי אפשר לרשת מ-$1, כי היא final (סופית). אם צריך לרשת — מוחקים את final מהכותרת שלה" },
        { "method does not override or implement a method from a supertype", "כתוב @Override, אבל במחלקת-העל אין פעולה כזו. בודקים את השם (אותיות גדולות וקטנות) ואת הפרמטרים, למשל toString()" },
        { "no interface expected here", "אחרי extends כותבים מחלקה. לממשק כותבים implements" },
        { "interface expected here", "אחרי implements כותבים ממשק. למחלקה כותבים extends" },
    };

    /** טיפוסים של Java / של הספרייה — עליהם לא מסבירים על ירושה */
    private static final Pattern LIB = Pattern.compile("^(Stack|Queue|Node|BinNode|String|Integer|Double|Character|Boolean|Object|Math|System|Scanner|ArrayList|List)\\b.*");

    /** הודעות על הורשה שצריך בשבילן את שורות הפירוט (symbol: / location: / required: / found: / was public...) — או null */
    static String poly(String first, String rest) {
        Matcher sym = Pattern.compile("(?m)^\\s*symbol\\s*:\\s*(method|variable)\\s+(\\w+)").matcher(rest);
        Matcher loc = Pattern.compile("(?m)^\\s*location\\s*:\\s*variable\\s+(\\w+)\\s+of type\\s+([\\w.]+)").matcher(rest);
        if (first.equals("cannot find symbol") && sym.find() && loc.find() && !LIB.matcher(loc.group(2)).matches()) {
            String t = loc.group(2).replaceAll(".*\\.", ""), kind = sym.group(1).equals("method") ? "פעולה" : "תכונה";
            String name = sym.group(2) + (sym.group(1).equals("method") ? "()" : "");
            return "ל-" + t + " אין " + kind + " " + name + ". בודקים את השם (אותיות גדולות וקטנות). אם היא במחלקה שיורשת מ-" + t
                    + " — דרך הפניה מסוג " + t + " לא רואים אותה; ממירים אחרי בדיקה עם instanceof: ((המחלקה) " + loc.group(1) + ")." + name;
        }
        Matcher x = Pattern.compile("^constructor (\\w+) in class (\\w+) cannot be applied to given types;?$").matcher(first);
        if (x.matches()) {
            Matcher req = Pattern.compile("(?m)^\\s*required\\s*:\\s*(.+)$").matcher(rest);
            String r = req.find() ? req.group(1).trim() : "...";
            if (rest.contains("no arguments")) return "ל-" + x.group(1) + " אין בנאי בלי ערכים (הבנאי שלה צריך: " + r + "). אם זו מחלקה שיורשת מ-" + x.group(1)
                    + " — השורה הראשונה בבנאי שלה: super(...) עם הערכים";
            return "הבנאי של " + x.group(1) + " נקרא עם ערכים לא מתאימים (הוא צריך: " + r + ")";
        }
        x = Pattern.compile("^(\\w+)\\((.*)\\) in (\\w+) cannot override (\\w+)\\((.*)\\) in (\\w+)$").matcher(first);
        if (x.matches()) {
            String what = x.group(1) + "() ב-" + x.group(3) + " דורסת את " + x.group(4) + "() של " + x.group(6);
            Matcher y = Pattern.compile("weaker access privileges; was (\\w+)").matcher(rest);
            if (y.find()) return what + ", ולכן חייבת להיות " + y.group(1) + " (בדריסה אסור להקטין את ההרשאה)";
            y = Pattern.compile("return type (.+) is not compatible with (.+)").matcher(rest);
            if (y.find()) return what + ", ולכן חייבת להחזיר " + y.group(2).trim() + " (אם זו פעולה אחרת — תנו לה שם אחר)";
            if (rest.contains("overridden method is final")) return "אי אפשר לדרוס את " + x.group(4) + "() של " + x.group(6) + ", כי היא final. נותנים לפעולה החדשה שם אחר";
            return x.group(1) + "() ב-" + x.group(3) + " לא יכולה לדרוס את " + x.group(4) + "() של " + x.group(6);
        }
        return null;
    }

    static String translate(String msg) {
        String m = msg == null ? "" : msg.trim().replaceFirst("^(java|javac):\\s*", "");
        String first = m.split("\n")[0].trim();
        String p = poly(first, m.contains("\n") ? m.substring(m.indexOf('\n') + 1) : "");
        if (p != null) return p;
        for (String[] r : RULES) {
            Matcher x = Pattern.compile("^" + r[0] + "$").matcher(first);
            if (x.matches()) {
                String he = r[1];
                for (int i = 1; i <= x.groupCount(); i++) he = he.replace("$" + i, x.group(i));
                return he;
            }
        }
        return m;
    }
}
