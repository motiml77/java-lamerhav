package il.lamerhav.dsviz;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * הטקסט של הקובץ בשביל "המורה בעברית": שורות, הזחה, וגרסה של הקוד בלי הערות ותוכן מחרוזות (כדי לספור סוגריים).
 * כאן גם ניתוח הסוגריים המסולסלים לפי ההזחה — כדי להצביע על ה-{ שלא נסגר (או על ה-{ שחסר), ולא רק על סוף הקובץ.
 */
final class MentorSource {
    final String text;
    /** הקוד בלי הערות ובלי מה שבתוך מחרוזות ותווים (מוחלפים ברווחים, אותו אורך) */
    final String code;
    private final int[] starts;
    private Braces braces;

    MentorSource(String text) {
        this.text = text;
        this.code = blank(text);
        List<Integer> s = new ArrayList<Integer>();
        s.add(0);
        for (int i = 0; i < text.length(); i++) if (text.charAt(i) == '\n') s.add(i + 1);
        starts = new int[s.size()];
        for (int i = 0; i < starts.length; i++) starts[i] = s.get(i);
    }

    int lines() {
        return starts.length;
    }

    /** מספר השורה (מ-1) של מקום בקובץ */
    int line(int offset) {
        int lo = 0, hi = starts.length - 1;
        while (lo < hi) {
            int mid = (lo + hi + 1) / 2;
            if (starts[mid] <= offset) lo = mid; else hi = mid - 1;
        }
        return lo + 1;
    }

    int lineStart(int line) {
        return starts[Math.max(0, Math.min(line, starts.length) - 1)];
    }

    /** סוף השורה (בלי ה-\n וה-\r) */
    int lineEnd(int line) {
        int e = line < starts.length ? starts[line] - 1 : text.length();
        while (e > lineStart(line) && (text.charAt(e - 1) == '\r' || text.charAt(e - 1) == '\n')) e--;
        return e;
    }

    String lineText(int line) {
        return text.substring(lineStart(line), lineEnd(line));
    }

    /** השורה בלי הערות ותוכן מחרוזות, בלי רווחים בצדדים */
    String lineCode(int line) {
        return code.substring(lineStart(line), lineEnd(line)).trim();
    }

    boolean blankLine(int line) {
        return lineCode(line).isEmpty();
    }

    /** ההזחה של השורה (טאב = 4) */
    int indent(int line) {
        return column(firstChar(line));
    }

    /** העמודה (טאב = 4) של מקום בקובץ */
    int column(int offset) {
        int c = 0;
        for (int i = lineStart(line(offset)); i < offset && i < text.length(); i++) c = text.charAt(i) == '\t' ? (c / 4 + 1) * 4 : c + 1;
        return c;
    }

    /** המקום של התו הראשון (שאינו רווח) בשורה */
    int firstChar(int line) {
        int i = lineStart(line), e = lineEnd(line);
        while (i < e && Character.isWhitespace(text.charAt(i))) i++;
        return i;
    }

    /** השורה הבאה (אחרי from) שיש בה קוד, או -1 */
    int nextCodeLine(int from) {
        for (int l = from + 1; l <= starts.length; l++) if (!blankLine(l)) return l;
        return -1;
    }

    /** השורה הקודמת (לפני from) שיש בה קוד, או -1 */
    int prevCodeLine(int from) {
        for (int l = from - 1; l >= 1; l--) if (!blankLine(l)) return l;
        return -1;
    }

    /** קטע קצר מהשורה (לציטוט בהודעה) */
    String excerpt(int line) {
        String s = lineText(line).trim();
        return s.length() > 60 ? s.substring(0, 57) + "…" : s;
    }

    char codeAt(int i) {
        return i >= 0 && i < code.length() ? code.charAt(i) : ' ';
    }

    /** התו הקודם (שאינו רווח) בקוד לפני המקום, או 0 */
    char prevCodeChar(int i) {
        for (int k = i - 1; k >= 0; k--) if (!Character.isWhitespace(code.charAt(k))) return code.charAt(k);
        return 0;
    }

    /** התו הבא (שאינו רווח) בקוד מהמקום והלאה, או 0 */
    char nextCodeChar(int i) {
        for (int k = i; k < code.length(); k++) if (!Character.isWhitespace(code.charAt(k))) return code.charAt(k);
        return 0;
    }

    /** כמה פעמים מופיע התו בשורה (בקוד, בלי הערות ומחרוזות) */
    int count(int line, char c) {
        int n = 0;
        for (int i = lineStart(line); i < lineEnd(line); i++) if (code.charAt(i) == c) n++;
        return n;
    }

    /** מחליף הערות ותוכן של מחרוזות/תווים ברווחים (שומר על המקומות ועל ירידות השורה) */
    static String blank(String s) {
        StringBuilder b = new StringBuilder(s);
        int n = s.length();
        for (int i = 0; i < n; i++) {
            char c = s.charAt(i);
            if (c == '/' && i + 1 < n && s.charAt(i + 1) == '/') {
                while (i < n && s.charAt(i) != '\n') b.setCharAt(i++, ' ');
                i--;
            } else if (c == '/' && i + 1 < n && s.charAt(i + 1) == '*') {
                b.setCharAt(i++, ' ');
                b.setCharAt(i++, ' ');
                while (i < n && !(s.charAt(i) == '*' && i + 1 < n && s.charAt(i + 1) == '/')) {
                    if (s.charAt(i) != '\n') b.setCharAt(i, ' ');
                    i++;
                }
                if (i < n) { b.setCharAt(i++, ' '); b.setCharAt(i, ' '); }
            } else if (c == '"' || c == '\'') {
                // עד המרכאה הסוגרת — או עד סוף השורה (מחרוזת שלא נסגרה)
                i++;
                while (i < n && s.charAt(i) != c && s.charAt(i) != '\n') {
                    if (s.charAt(i) == '\\' && i + 1 < n && s.charAt(i + 1) != '\n') b.setCharAt(i++, ' ');
                    b.setCharAt(i++, ' ');
                }
                if (i < n && s.charAt(i) == '\n') i--;
            }
        }
        return b.toString();
    }

    // ---------------------------------------------------------------- סוגריים מסולסלים

    /** { שנפתח: איפה, באיזו שורה ובאיזו הזחה */
    static final class Open {
        final int offset, line, indent;

        Open(int offset, int line, int indent) {
            this.offset = offset;
            this.line = line;
            this.indent = indent;
        }
    }

    /** תוצאת הניתוח: { שלא נסגרו (לפי ההזחה), } מיותרים, ועומק הסוגריים בכל מקום */
    static final class Braces {
        final List<Open> unclosed = new ArrayList<Open>();
        final List<Integer> extra = new ArrayList<Integer>();
    }

    /**
     * מתאים כל } ל-{ לפי ההזחה: } שעומד בתחילת שורה סוגר את ה-{ שנפתח בשורה עם אותה הזחה.
     * אם ה-{ האחרון שפתוח מוזח יותר — כנראה שכחו לסגור אותו; אם הוא מוזח פחות — ה-} כנראה מיותר (או שחסר { מעליו).
     */
    Braces braces() {
        if (braces != null) return braces;
        Braces r = new Braces();
        Deque<Open> stack = new ArrayDeque<Open>();
        for (int i = 0; i < code.length(); i++) {
            char c = code.charAt(i);
            if (c == '{') {
                int l = line(i);
                stack.push(new Open(i, l, indent(l)));
            } else if (c == '}') {
                int l = line(i);
                if (stack.isEmpty()) { r.extra.add(i); continue; }
                if (firstChar(l) != i) { stack.pop(); continue; }
                int ind = indent(l);
                if (stack.peek().indent > ind && hasIndent(stack, ind)) {
                    while (stack.peek().indent > ind) r.unclosed.add(stack.pop());
                } else if (stack.peek().indent < ind && !hasIndent(stack, ind)) {
                    r.extra.add(i);
                    continue;
                }
                stack.pop();
            }
        }
        List<Open> rest = new ArrayList<Open>(stack);
        for (int k = rest.size() - 1; k >= 0; k--) r.unclosed.add(rest.get(k));
        braces = r;
        return r;
    }

    private static boolean hasIndent(Deque<Open> stack, int indent) {
        for (Open o : stack) if (o.indent == indent) return true;
        return false;
    }

    /** כמה { פתוחים במקום הזה (0 = מחוץ למחלקה, 1 = ישירות בתוך המחלקה, 2 ומעלה = בתוך פעולה) */
    int depth(int offset) {
        int d = 0;
        for (int i = 0; i < offset && i < code.length(); i++) {
            if (code.charAt(i) == '{') d++;
            else if (code.charAt(i) == '}' && d > 0) d--;
        }
        return d;
    }

    /** השורה שלפניה צריך לבוא ה-} שסוגר את הבלוק: השורה הראשונה אחרי הבלוק שאינה מוזחת יותר ממנו (או -1 = סוף הקובץ) */
    int blockEndLine(Open o) {
        for (int l = o.line + 1; l <= lines(); l++) {
            if (blankLine(l)) continue;
            if (indent(l) <= o.indent) return l;
        }
        return -1;
    }

    /** הפקודה שפותחת את הבלוק: if / while / for / else / do / switch / try, פעולה או מחלקה */
    String header(Open o) {
        String s = code.substring(lineStart(o.line), o.offset).trim();
        if (s.isEmpty() && o.line > 1) {
            int p = prevCodeLine(o.line);
            if (p > 0) s = lineCode(p);
        }
        return s;
    }
}
