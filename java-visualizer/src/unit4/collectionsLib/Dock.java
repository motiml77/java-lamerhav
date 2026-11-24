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
            if (!(r >= 0.05 && r <= 0.95)) return null;
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
