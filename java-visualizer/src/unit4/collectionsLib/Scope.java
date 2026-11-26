package unit4.collectionsLib;

import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * עומק הסקופ של שורה בקובץ מקור (Java / C#) — לפי המבנה, לא לפי הרווחים שהתלמיד הקליד: כמה בלוקים של { } פתוחים
 * בתחילת השורה, ועוד רמה אחת לשורה שהיא הגוף של if / else / for / while בלי סוגריים מסולסלים
 * ("if (n < 10)" ובשורה הבאה "return n;"). משמש להזחה של השורות בכרטיסי הרקורסיה: מה שבתוך if מוזח פנימה.
 * מחרוזות, תווים והערות לא נספרים.
 */
final class Scope {
    private Scope() {}

    private static final Map<List<String>, int[]> CACHE = new WeakHashMap<List<String>, int[]>();

    /** העומק של השורה line (מ-1); 0 אם אין קובץ / השורה מחוץ לקובץ */
    static int depth(List<String> lines, int line) {
        if (lines == null || line < 1 || line > lines.size()) return 0;
        int[] open;
        synchronized (CACHE) {
            open = CACHE.get(lines);
            if (open == null || open.length != lines.size()) CACHE.put(lines, open = braces(lines));
        }
        String t = strip(lines.get(line - 1)).trim();
        int d = open[line - 1];
        if (d >= IN_COMMENT) return d - IN_COMMENT;      // שורה שמתחילה בתוך הערת בלוק — רק העומק שמסביב
        if (t.startsWith("}")) d--;                      // שורה שסוגרת בלוק שייכת לרמה שבחוץ
        else if (!t.startsWith("{") && headless(lines, line)) d++;   // הגוף של if / לולאה בלי { }
        return Math.max(0, d);
    }

    private static final int IN_COMMENT = 100000;

    /** לכל שורה: כמה { פתוחים בתחילתה (+ IN_COMMENT כשהשורה מתחילה בתוך הערת בלוק) */
    private static int[] braces(List<String> lines) {
        int[] open = new int[lines.size()];
        int d = 0;
        boolean block = false;   // בתוך הערת בלוק
        for (int i = 0; i < lines.size(); i++) {
            open[i] = d + (block ? IN_COMMENT : 0);
            String s = lines.get(i);
            for (int k = 0; k < s.length(); k++) {
                char c = s.charAt(k);
                if (block) {
                    if (c == '*' && k + 1 < s.length() && s.charAt(k + 1) == '/') { block = false; k++; }
                } else if (c == '/' && k + 1 < s.length() && s.charAt(k + 1) == '/') break;
                else if (c == '/' && k + 1 < s.length() && s.charAt(k + 1) == '*') { block = true; k++; }
                else if (c == '"' || c == '\'') {
                    for (k++; k < s.length() && s.charAt(k) != c; k++) if (s.charAt(k) == '\\') k++;
                } else if (c == '{') d++;
                else if (c == '}') d = Math.max(0, d - 1);
            }
        }
        return open;
    }

    /** השורה הקודמת (שאינה ריקה / הערה) היא כותרת של if / else / for / while בלי { ובלי פקודה באותה שורה */
    private static boolean headless(List<String> lines, int line) {
        for (int i = line - 2; i >= 0; i--) {
            String p = strip(lines.get(i)).trim();
            if (p.isEmpty()) continue;
            if (!p.matches("^(\\}\\s*)?(else\\s+if|if|else|for|foreach|while)\\b.*")) return false;
            return !p.endsWith("{") && !p.endsWith(";") && !p.endsWith("}");
        }
        return false;
    }

    /** השורה בלי הערת // שבסופה (בקירוב: לא בתוך מחרוזת) */
    private static String strip(String s) {
        boolean str = false;
        for (int k = 0; k < s.length(); k++) {
            char c = s.charAt(k);
            if (c == '"') str = !str;
            else if (c == '\\' && str) k++;
            else if (!str && c == '/' && k + 1 < s.length() && s.charAt(k + 1) == '/') return s.substring(0, k);
        }
        return s;
    }
}
