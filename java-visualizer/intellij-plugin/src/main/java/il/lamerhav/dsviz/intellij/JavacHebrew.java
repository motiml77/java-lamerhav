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
        { "(.+) has private access in (.+)", "$1 פרטי במחלקה $2 — ניגשים אליו דרך פעולה (get...)" },
    };

    static String translate(String msg) {
        String m = msg == null ? "" : msg.trim().replaceFirst("^(java|javac):\\s*", "");
        String first = m.split("\n")[0].trim();
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
