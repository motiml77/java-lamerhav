package unit4.collectionsLib;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;

/**
 * הסידור של החלקים בחלון ההרצה הוויזואלית (קוד / מעקב משתנים / פלט / התצוגה הוויזואלית) — עץ: עלה הוא חלק (card),
 * וצומת הוא קו גרירה בין שני תתי-עצים: across — זה ליד זה (a משמאל, b מימין), אחרת זה מעל זה (a למעלה, b למטה);
 * ratio — החלק של a (0..1). המשתמש גורר חלק מהכותרת שלו אל צד של חלק אחר (או אל האמצע שלו — החלפת מקומות),
 * והעץ נשמר כטקסט לפעם הבאה, למשל: ‎H0.42(V0.64(code,H0.5(vars,out)),view)‎.
 * רק הלוגיקה (בלי Swing) — החלון עצמו ב-Viewer.
 */
final class Dock {
    static final int LEFT = 0, RIGHT = 1, TOP = 2, BOTTOM = 3, CENTER = 4;
    /** הגבולות של ratio: אף צד של קו גרירה לא קטן מ-5% (גם בגרירה — DockSplit — וגם בסידור שמור) */
    static final double MIN_RATIO = 0.05, MAX_RATIO = 0.95;

    String card;          // בעלה: שם החלק; בקו גרירה: null
    boolean across;
    double ratio, def;    // def — הגודל הרגיל של הקו (לחיצה כפולה עליו)
    Dock a, b;

    private Dock() {}

    static Dock leaf(String card) {
        Dock d = new Dock();
        d.card = card;
        return d;
    }

    static Dock split(boolean across, double ratio, Dock a, Dock b) {
        Dock d = new Dock();
        d.across = across;
        d.ratio = d.def = ratio;
        d.a = a;
        d.b = b;
        return d;
    }

    boolean isLeaf() {
        return card != null;
    }

    /** החלקים לפי הסדר בעץ */
    List<String> leaves() {
        List<String> out = new ArrayList<String>();
        collect(out);
        return out;
    }

    private void collect(List<String> out) {
        if (isLeaf()) out.add(card);
        else { a.collect(out); b.collect(out); }
    }

    Dock find(String id) {
        if (isLeaf()) return card.equals(id) ? this : null;
        Dock r = a.find(id);
        return r != null ? r : b.find(id);
    }

    Dock parentOf(Dock n) {
        if (isLeaf()) return null;
        if (a == n || b == n) return this;
        Dock r = a.parentOf(n);
        return r != null ? r : b.parentOf(n);
    }

    String format() {
        if (isLeaf()) return card;
        return (across ? "H" : "V") + String.format(Locale.ROOT, "%.3f", ratio) + "(" + a.format() + "," + b.format() + ")";
    }

    /** הסידור השמור — רק אם הוא תקין ויש בו בדיוק את החלקים ids (כל אחד פעם אחת); אחרת null */
    static Dock parse(String s, Collection<String> ids) {
        if (s == null) return null;
        try {
            int[] at = { 0 };
            Dock d = parse(s.trim(), at);
            if (d == null || at[0] != s.trim().length()) return null;
            List<String> got = d.leaves();
            return got.size() == ids.size() && new HashSet<String>(got).equals(new HashSet<String>(ids)) ? d : null;
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static Dock parse(String s, int[] at) {
        char c = s.charAt(at[0]);
        if ((c == 'H' || c == 'V') && at[0] + 1 < s.length() && Character.isDigit(s.charAt(at[0] + 1))) {
            int open = s.indexOf('(', at[0]);
            if (open < 0) return null;
            double r = Double.parseDouble(s.substring(at[0] + 1, open));
            if (!(r >= MIN_RATIO && r <= MAX_RATIO)) return null;
            at[0] = open + 1;
            Dock a = parse(s, at);
            if (a == null || s.charAt(at[0]) != ',') return null;
            at[0]++;
            Dock b = parse(s, at);
            if (b == null || s.charAt(at[0]) != ')') return null;
            at[0]++;
            Dock d = split(c == 'H', r, a, b);
            d.def = 0.5;
            return d;
        }
        int end = at[0];
        while (end < s.length() && Character.isLetterOrDigit(s.charAt(end))) end++;
        if (end == at[0]) return null;
        Dock d = leaf(s.substring(at[0], end));
        at[0] = end;
        return d;
    }

    /**
     * הגודל (בפיקסלים) של הצד הראשון (a) של קו גרירה. total — המקום של שני הצדדים יחד (בלי הקו עצמו);
     * minA / minB — הגודל הקטן ביותר של כל צד (בצד שהוא בעצמו כמה חלקים: הסכום שלהם — DockSplit.getMinimumSize).
     * לפי ratio, אבל אף צד לא קטן מהמינימום שלו; ואם אין מקום לשני המינימומים (חלון קטן מאוד) — מתחלקים לפי הצורך (minA : minB).
     * כך כל שינוי גודל (גרירת קו אחר, שינוי גודל החלון) שומר על היחסים בין החלקים, ואף חלק לא נעלם.
     */
    static int firstSize(int total, double ratio, int minA, int minB) {
        if (total <= 0) return 0;
        int lo = minA, hi = total - minB;
        if (lo > hi) return (int) Math.round(total * (minA / (double) Math.max(1, minA + minB)));
        return Math.max(lo, Math.min(hi, (int) Math.round(total * ratio)));
    }

    /**
     * ה-ratio אחרי שהמשתמש גרר את הקו כך שהצד הראשון יהיה pos פיקסלים — נעצר במינימום של כל צד (וב-5% / 95%).
     * אם אין לאן לזוז (אין מקום אפילו לשני המינימומים) — ratio נשאר כמו שהיה.
     */
    static double dragRatio(int pos, int total, double ratio, int minA, int minB) {
        int lo = minA, hi = total - minB;
        if (total <= 0 || lo > hi) return ratio;
        pos = Math.max(lo, Math.min(hi, pos));
        return Math.max(MIN_RATIO, Math.min(MAX_RATIO, pos / (double) total));
    }

    /**
     * מעבירים את החלק from אל הצד side של החלק to (LEFT / RIGHT / TOP / BOTTOM — to מתחלק לשניים, והחצי הזה ל-from),
     * או CENTER — שני החלקים מחליפים מקומות. המקום הישן של from נסגר (השכן שלו מקבל את כולו).
     * false — לא השתנה כלום (אותו חלק, או חלק שלא קיים).
     */
    boolean move(String from, String to, int side) {
        Dock f = find(from), t = find(to);
        if (f == null || t == null || f == t) return false;
        if (side == CENTER) {
            f.card = to;
            t.card = from;
            return true;
        }
        Dock p = parentOf(f);
        if (p == null) return false;
        Dock sib = p.a == f ? p.b : p.a;
        // כבר שם? (from ו-to שכנים באותו קו, ו-from כבר בצד המבוקש) — אין מה לשנות
        if (sib == t && p.across == (side == LEFT || side == RIGHT) && (p.a == f) == (side == LEFT || side == TOP)) return false;
        p.card = sib.card;
        p.across = sib.across;
        p.ratio = sib.ratio;
        p.def = sib.def;
        p.a = sib.a;
        p.b = sib.b;
        t = find(to);
        Dock old = leaf(to), nw = leaf(from);
        t.card = null;
        t.across = side == LEFT || side == RIGHT;
        t.ratio = t.def = 0.5;
        boolean first = side == LEFT || side == TOP;
        t.a = first ? nw : old;
        t.b = first ? old : nw;
        return true;
    }
}
