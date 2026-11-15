package il.lamerhav.dsviz.cs;

/**
 * איפה נכנס הקוד של "הוספת מבנה נתונים" כשהכלי יודע איפה הסמן: שורה ריקה — מחליפים אותה; אחרת — מתחת לשורה הנוכחית, באותה הזחה
 * (אותו כלל כמו insertionPoint ב-VS Code וב-Eclipse). line1 — שורת הסמן, מ-1.
 */
public final class InsertPoint {
    public final boolean blank;
    /** היסט בטקסט: תחילת השורה (שורה ריקה) או סופה (אחרת, בלי ‎\r‎) */
    public final int offset;
    public final String indent;

    private InsertPoint(boolean blank, int offset, String indent) {
        this.blank = blank;
        this.offset = offset;
        this.indent = indent;
    }

    public static InsertPoint of(String text, int line1) {
        int start = 0;
        for (int i = 1; i < line1 && start >= 0; i++) {
            int nl = text.indexOf('\n', start);
            start = nl < 0 ? -1 : nl + 1;
        }
        if (start < 0) start = text.length();   // אחרי סוף הקובץ — בסוף
        int nl = text.indexOf('\n', start);
        int end = nl < 0 ? text.length() : nl;
        String ln = text.substring(start, end);
        if (ln.endsWith("\r")) {
            ln = ln.substring(0, ln.length() - 1);
            end--;
        }
        boolean blank = ln.trim().isEmpty();
        String lead = ln.substring(0, ln.length() - ln.replaceAll("^\\s+", "").length());
        return new InsertPoint(blank, blank ? start : end, blank ? (ln.isEmpty() ? "        " : ln) : (lead.isEmpty() ? "" : lead));
    }
}
