package il.lamerhav.dsviz;

import java.text.Bidi;
import java.util.ArrayList;
import java.util.List;

/**
 * בדיקה ל-RtlText (עברית עם קוד באמצע — חלון "הסברים בעברית" ו-Problems ב-Eclipse), בלי Eclipse ובלי SWT.
 * מה שהתלמיד יראה מחושב עם java.text.Bidi (האלגוריתם הדו-כיווני של Unicode, כמו ב-Windows וב-GTK) — שלוש פעמים:
 * כשהכיוון הבסיסי של הפקד משמאל לימין (Windows / חלון Problems), מימין לשמאל, ו"לפי האות הראשונה" (GTK).
 * ומה שהוא צריך לראות: היחידות של המשפט בסדר הפוך (מימין לשמאל) — כל קטע קוד יחידה אחת שנשארת משמאל לימין כמו שהיא,
 * וכל תו עברי / סימן פיסוק יחידה לעצמו (סוגריים בעברית מתהפכים). שלוש הגרסאות חייבות לצאת בדיוק כך.
 *   java RtlTextTest [-v]
 */
public class RtlTextTest {
    static final int[] BASES = { Bidi.DIRECTION_LEFT_TO_RIGHT, Bidi.DIRECTION_RIGHT_TO_LEFT, Bidi.DIRECTION_DEFAULT_LEFT_TO_RIGHT };
    static final String[] BASE_NAMES = { "בסיס משמאל לימין (Windows / Problems)", "בסיס מימין לשמאל", "לפי האות הראשונה (GTK)" };

    public static void main(String[] a) throws Exception {
        System.setOut(new java.io.PrintStream(new java.io.FileOutputStream(java.io.FileDescriptor.out), true, "UTF-8"));
        List<String> failures = new ArrayList<String>();
        int checks = 0;
        // {טקסט, קטעי הקוד שצריכים לצאת (מופרדים ב-|)}
        String[][] cases = {
            { "`p` לא מתקדם בתוך הלולאה — היא לא תיגמר", "p" },
            { "isEmpty רק בודקת ומחזירה true או false", "isEmpty|true|false" },
            { "כדי להוציא את האיבר העליון כותבים `s.pop()`.", "s.pop()" },
            { "כדי להוציא את האיבר העליון כותבים s.pop().", "s.pop()" },
            { "עד 127 זה עובד, ובמספרים גדולים יותר ההשוואה נכשלת", "127" },
            { "התנאי `i != 0` תמיד נכון", "i != 0" },
            { "האינדקס `arr[i]` מחוץ למערך כש-i שווה ל-arr.length", "arr[i]|i|arr.length" },
            { "אי אפשר להכניס `Stack<String>` למשתנה מטיפוס `Stack<Integer>`", "Stack<String>|Stack<Integer>" },
            { "שומרים את התוצאה: `s = s.toUpperCase();`", "s = s.toUpperCase();" },
            { "הפעולות שיש: insert, remove, head, isEmpty", "insert|remove|head|isEmpty" },
            { "ל-`Queue` אין פעולה `size`", "Queue|size" },
            { "שני הצדדים הם `Integer` (עצמים), ו-`==` משווה עצמים ולא ערכים", "Integer|==" },
            { "בסיבוב האחרון תהיה שגיאה (ArrayIndexOutOfBoundsException). כותבים: `i < arr.length`", "(ArrayIndexOutOfBoundsException)|i < arr.length" },
            { "סידור הקוד (Ctrl+Shift+F) מראה איפה בלוק לא נסגר", "(Ctrl+Shift+F)" },
            { "בדרך כלל מחזירים אותה (או `getRight()`)", "getRight()" },
            { "כשנשאר איבר אחד, ההוצאה השנייה תיכשל: `!s.isEmpty()`", "!s.isEmpty()" },
            { "אם `a && b` נכון", "a && b" },
            { "פותחים קובץ Java — וההסברים לשגיאות שבו יופיעו כאן", "Java" },
            { "אין שגיאות ואין אזהרות בקובץ Mistakes.java — כל הכבוד!", "Mistakes.java" },
            { "2 שגיאות ו-6 אזהרות — לחיצה על הסבר מעבירה לשורה בקוד", "2|6" },
            { "שורה 12 · אזהרה", "12" },
            { "Ctrl+1 על השגיאה מציע תיקונים אפשריים", "Ctrl+1" },
            { "הצעה לתיקון: מוסיפים אחרי `n`: `;`", "n|;" },
            { "כותבים: `s.equals(\"ABC\")` (ולא ==)", "s.equals(\"ABC\")|==" },
            { "השורה הראשונה\nושורה שנייה עם `x++`", "x++" },
            // סוגריים של העברית (גם [ ] של העצה בחלון Problems: "ההסבר [העצה]") — לא קוד, ומתהפכים כרגיל
            { "אאא בבב [גגג דדד]", "" },
            { "`p` לא מתקדם בתוך הלולאה [בסוף כל סיבוב מתקדמים: `p = p.getNext();`]", "p|p = p.getNext();" },
            { "ל-`Queue` אין פעולה `size` [הפעולות שיש: `insert, remove, head, isEmpty`]", "Queue|size|insert, remove, head, isEmpty" },
            { "שני הצדדים הם `Integer` (עצמים — למשל ערכים שהוצאו ממחסנית)", "Integer" },
            { "מגיעים לסוף has בלי return (הסימון באנגלית — בשורה 9)", "has|return|9" },
        };
        for (String[] c : cases) {
            checks++;
            String got = codes(c[0]);
            if (!got.equals(c[1])) failures.add("קטעי הקוד של \"" + c[0] + "\": " + got + " (צריך " + c[1] + ")");
            String p = problem(c[0]);
            checks += BASES.length;
            if (p != null) failures.add(p);
        }
        // שורת הקוד בסוף ההצעה: תמיד משמאל לימין, בדיוק כמו שהיא
        for (String code : new String[] { "s = s.toUpperCase();", "if (p == null) return 0;", "(double) sum / n", "!s.isEmpty()",
                "Stack<Integer> s = new Stack<Integer>();", "arr[arr.length - 1]", ";" }) {
            for (int k = 0; k < BASES.length; k++) {
                checks++;
                String v = visual(RtlText.ltr(code), BASES[k]);
                if (!v.equals(code)) failures.add("שורת קוד [" + BASE_NAMES[k] + "]: " + v + " (צריך " + code + ")");
            }
        }
        // התווים לא נראים, ו-strip מחזיר את הטקסט המקורי (בלי סימוני ה-`)
        checks++;
        String t = "ל-`Queue` אין פעולה size [לתור אין פעולה שסופרת: `s.size()`]";
        if (!RtlText.strip(RtlText.rtl(t)).equals(t.replace("`", ""))) failures.add("strip: " + RtlText.strip(RtlText.rtl(t)));
        // לשם השוואה: הדרך הקודמת (רק LRM במקום ה-`) — בכמה מהדוגמאות היא שגויה
        int old = 0;
        for (String[] c : cases) {
            if (!oldProblem(c[0])) continue;
            old++;
            if (a.length > 0 && a[0].equals("-v")) System.out.println("  (בדרך הקודמת מוצג לא נכון: " + c[0].replace("\n", " / ") + ")");
        }
        for (String f : failures) System.out.println("✗ " + f);
        System.out.println((failures.isEmpty() ? "✓ " : "✗ ") + "עברית עם קוד (RtlText): " + cases.length + " משפטים × " + BASES.length
                + " כיוונים בסיסיים + שורות קוד — " + checks + " בדיקות, " + failures.size() + " נכשלו"
                + " (בדרך הקודמת, LRM בלבד: " + old + " מתוך " + cases.length + " משפטים היו מוצגים לא נכון)");
        if (!failures.isEmpty()) System.exit(1);
    }

    /** קטעי הקוד ש-RtlText מצא בטקסט, מופרדים ב-| */
    static String codes(String text) {
        StringBuilder b = new StringBuilder();
        for (String line : text.split("\n")) {
            for (String[] s : RtlText.segments(line)) if (s[0] != null) b.append(b.length() > 0 ? "|" : "").append(s[0]);
        }
        return b.toString();
    }

    /** null אם הטקסט מוצג נכון בכל שלושת הכיוונים הבסיסיים; אחרת — מה יצא ומה היה צריך */
    static String problem(String text) {
        String rtl = RtlText.rtl(text);
        String[] lines = rtl.split("\n", -1), src = text.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            String want = ideal(src[i]);
            for (int k = 0; k < BASES.length; k++) {
                String got = visual(lines[i], BASES[k]);
                if (!got.equals(want)) return "\"" + src[i] + "\" [" + BASE_NAMES[k] + "]:\n      יצא:    " + got + "\n      צריך:   " + want;
            }
        }
        return null;
    }

    /** מה שהתלמיד צריך לראות (משמאל לימין על המסך): היחידות בסדר הפוך — קטע קוד כמו שהוא, תו עברי / פיסוק לבד (סוגריים הפוכים) */
    static String ideal(String line) {
        List<String> units = new ArrayList<String>();
        for (String[] s : RtlText.segments(line)) {
            if (s[0] != null) units.add(visual(s[0], Bidi.DIRECTION_LEFT_TO_RIGHT));
            else for (char ch : s[1].toCharArray()) units.add(String.valueOf(mirror(ch)));
        }
        StringBuilder b = new StringBuilder();
        for (int i = units.size() - 1; i >= 0; i--) b.append(units.get(i));
        return b.toString();
    }

    /** הסדר על המסך (משמאל לימין) לפי האלגוריתם הדו-כיווני של Unicode: רמות, היפוך (L2), שיקוף סוגריים ברמה אי-זוגית (L4) */
    static String visual(String s, int base) {
        if (s.isEmpty()) return "";
        Bidi b = new Bidi(s, base);
        int n = s.length();
        byte[] levels = new byte[n];
        Integer[] order = new Integer[n];
        for (int i = 0; i < n; i++) {
            levels[i] = (byte) b.getLevelAt(i);
            order[i] = i;
        }
        Bidi.reorderVisually(levels, 0, order, 0, n);
        StringBuilder out = new StringBuilder();
        for (Integer i : order) {
            char ch = s.charAt(i);
            if (RtlText.strip(String.valueOf(ch)).isEmpty()) continue;   // תווי כיווניות לא נראים
            out.append((levels[i] & 1) == 1 ? mirror(ch) : ch);
        }
        return out.toString();
    }

    static char mirror(char c) {
        switch (c) {
            case '(': return ')';
            case ')': return '(';
            case '[': return ']';
            case ']': return '[';
            case '{': return '}';
            case '}': return '{';
            case '<': return '>';
            case '>': return '<';
            default: return c;
        }
    }

    /** הדרך הקודמת (MentorView.plain): ` ← LRM, בתווית מימין לשמאל (בלי RLE) */
    static boolean oldProblem(String text) {
        for (String line : text.split("\n")) {
            String old = line.replace('`', '‎');
            String want = ideal(line);
            for (int base : new int[] { Bidi.DIRECTION_RIGHT_TO_LEFT, Bidi.DIRECTION_DEFAULT_LEFT_TO_RIGHT }) {
                if (!visual(old, base).equals(want)) return true;
            }
        }
        return false;
    }
}
