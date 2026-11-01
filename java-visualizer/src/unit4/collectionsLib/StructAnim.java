package unit4.collectionsLib;

import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.swing.JComponent;
import javax.swing.Timer;

import unit4.collectionsLib.Model.Cell;
import unit4.collectionsLib.Model.ChainView;
import unit4.collectionsLib.Model.LinearView;
import unit4.collectionsLib.Model.LoopView;
import unit4.collectionsLib.Model.Mark;
import unit4.collectionsLib.Model.Obj;
import unit4.collectionsLib.Model.Step;
import unit4.collectionsLib.Model.View;

/**
 * הנפשה של הכנסה והוצאה במבנים — כשמתקדמים צעד אחד קדימה ("הבא" / הפעלה אוטומטית):
 * <ul>
 * <li>מחסנית, push: האיבר החדש נופל מלמעלה לתוך הפתח, ודוחף את האיברים שמתחתיו מטה. pop: האיבר העליון מורם החוצה,
 * מאדים, עף למעלה, מתכווץ ונעלם — ובמקומו נשאר הסימן המקווקו "הוצא".</li>
 * <li>תור, insert: האיבר נכנס מבחוץ דרך הפתח של הזנב ומתיישב. remove: הראש יוצא דרך הפתח של היציאה ונמחק,
 * והשאר מתקדמים לכיוון הראש.</li>
 * <li>רשימה מקושרת: חוליה חדשה "נגררת" למקום (מאיפה שהייתה — או נופלת מלמעלה כשהיא נוצרת), השכנות זזות לפנות לה מקום
 * והחצים החדשים נמתחים; חוליה שמנתקים יורדת מהשרשרת והשכנות סוגרות את הרווח; חוליה שנעלמה — נופלת, מאדימה ונמחקת.</li>
 * </ul>
 * איך: משווים את המבנים של הצעד הקודם לאלה של הצעד החדש (אותו משתנה = אותו מבנה; חוליה — לפי הזהות שלה, Cell.id,
 * ובהרצה של C# — לפי הערך), ומודדים איפה כל איבר היה בציור הקודם (StructPanel מצייר את הצעד הקודם "בלי מסך" ורושם).
 * בזמן ההנפשה מציירים את הצעד החדש, אבל כל איבר — בדרך שבין המקום הישן למקום החדש; ומה שהוצא — מעל הכול.
 * <p>
 * 0.45 שנ' (בהפעלה אוטומטית מהירה — קצר יותר, ומהירה מאוד — בלי הנפשה). צעד חדש באמצע — ההנפשה הקודמת נגמרת מיד.
 * לצילומים ולבדיקות: {@code -Ddsviz.animFreeze=0.5} — כל הנפשה "קפואה" ב-50%; {@code -Ddsviz.anim=false} — בלי הנפשות.
 */
final class StructAnim implements ActionListener {
    static final int TIME = 460;
    /** מתחת לזה (הפעלה אוטומטית מהירה מאוד) — בלי הנפשה */
    static final int MIN_TIME = 120;
    static final boolean ON = !"false".equalsIgnoreCase(String.valueOf(System.getProperty("dsviz.anim")).trim());
    private static final float FREEZE = freeze();

    static final int STACK = 0, QUEUE = 1, CHAIN = 2;

    /** איבר שהוצא: מצויר מעל הכול, בדרך החוצה */
    static final class Exit {
        int kind;
        String key;          // המפתח של המקום הישן (L|... / N|...)
        String viewKey;      // המבנה בצעד החדש — שם מצויר הסימן "הוצא" שאליו האיבר עף (או null)
        boolean toGhost;
        String text;
        Obj obj;
        View sub;
        Mark mark = Mark.NONE;
        Cell cell;           // רשימה: החוליה שנעלמה
        String ckey;         // רשימה: המפתח שלה (בשביל החץ שיצא ממנה)
    }

    /** מה קרה במחסנית / תור אחד */
    static final class LinDiff {
        boolean stack;
        int[] fromOld;       // לכל איבר בצעד החדש: האינדקס שלו בצעד הקודם (-1 = נוסף עכשיו)
        int added, removed;
    }

    final Step from, to;
    final Map<View, String> fromViews, toViews;
    final Map<Cell, String> fromCells, toCells;
    final Map<String, LinDiff> lin = new HashMap<String, LinDiff>();
    final List<Exit> exits = new ArrayList<Exit>();
    final Set<String> enter = new HashSet<String>();       // חוליות חדשות (מפתחות)
    final Set<String> newLinks = new HashSet<String>();    // חצים חדשים בין חוליות: "a>b"
    /** לאן הצביעה כל חוליה בצעד הקודם: מפתח → {החוליה הבאה, "row" (באותה שורה) / "join" (מענף אל השורה הראשית)} */
    private final Map<String, String[]> oldNext = new HashMap<String, String[]>();
    private final Set<String> ghostViews = new HashSet<String>();   // מבנים שבהם איבר עף אל הסימן "הוצא"
    private boolean any;

    /** המקומות בציור הקודם (מפתח → מלבן, בקואורדינטות הציור) — StructPanel ממלא */
    Map<String, Rectangle> old = new HashMap<String, Rectangle>();
    int oldTranslate;   // כמה הציור הקודם היה מוזז ימינה (ממורכז בחלון)
    /** הזזה של הציור הקודם (כשהיה ממורכז אחרת) — מתעדכן בכל ציור */
    float shiftX;
    /** המבנה שמצויר עכשיו: כמה המקום הסופי שלו רחוק מהמקום שבו הוא מצויר ברגע הזה */
    int dx, dy;
    /** הסימן "הוצא" של כל מבנה, כפי שצויר ברגע הזה */
    final Map<String, Rectangle> ghosts = new HashMap<String, Rectangle>();
    /** איפה צוירה כל חוליה ברגע הזה (מפתח → מלבן) — בשביל החץ שיוצא מחוליה שנמחקת */
    final Map<String, Rectangle> now = new HashMap<String, Rectangle>();
    int viewport;   // רוחב החלון כשההנפשה התחילה — אם השתנה (הגדילו את החלון), ההנפשה נגמרת

    private final JComponent owner;
    private final Timer timer = new Timer(15, this);
    private long start;
    private int duration = TIME;
    private float test = -1f;   // בדיקות: רגע קבוע
    private boolean done;

    private StructAnim(Step from, Step to, JComponent owner) {
        this.from = from;
        this.to = to;
        this.owner = owner;
        timer.setCoalesce(true);
        fromViews = viewKeys(from);
        toViews = viewKeys(to);
        boolean ids = hasIds(from) && hasIds(to);
        fromCells = cellKeys(from, ids);
        toCells = cellKeys(to, ids);
        if (!ids) pairInPlace();
        diffLinear();
        diffChains();
    }

    /** ההנפשה בין שני צעדים — או null אם אין מה להנפיש (לא נוסף ולא הוצא כלום) */
    static StructAnim build(Step from, Step to, JComponent owner) {
        if (!ON || from == null || to == null || from == to) return null;
        StructAnim a = new StructAnim(from, to, owner);
        return a.any ? a : null;
    }

    private static float freeze() {
        try {
            String v = System.getProperty("dsviz.animFreeze");
            return v == null ? -1f : Math.max(0f, Math.min(1f, Float.parseFloat(v.trim())));
        } catch (RuntimeException e) {
            return -1f;
        }
    }

    // ------------------------------------------------------------ זהות: איזה מבנה / חוליה בצעד החדש הם אותם בצעד הקודם

    /**
     * מפתח לכל מבנה: הסוג + השם (שם המשתנה); כמה עם אותו שם — לפי הסדר. מחסנית / תור בהרצה של Java — לפי המבנה עצמו
     * (View.src), כך שגם כששם התצוגה משתנה (פרמטר בפעולה: "s, st") זה אותו מבנה
     */
    static Map<View, String> viewKeys(Step s) {
        Map<View, String> m = new IdentityHashMap<View, String>();
        Map<String, Integer> seen = new HashMap<String, Integer>();
        for (View v : s.views) {
            String id = v instanceof LinearView && v.src != null ? "@" + System.identityHashCode(v.src) : "|" + v.title;
            String base = v.getClass().getSimpleName() + (v instanceof LinearView ? (((LinearView) v).stack ? "S" : "Q") : "")
                    + (v instanceof LoopView ? String.valueOf(((LoopView) v).depth) : "") + id;
            Integer k = seen.get(base);
            k = k == null ? 0 : k + 1;
            seen.put(base, k);
            m.put(v, base + "#" + k);
        }
        return m;
    }

    private static List<ChainView> rows(ChainView c) {
        List<ChainView> r = new ArrayList<ChainView>();
        r.add(c);
        r.addAll(c.branches);
        return r;
    }

    private static boolean hasIds(Step s) {
        for (View v : s.views)
            if (v instanceof ChainView) for (ChainView r : rows((ChainView) v)) for (Cell c : r.cells) if (c.id <= 0) return false;
        return true;
    }

    /** מפתח לכל חוליה (במבנים הראשיים): הזהות שלה; בלי זהות (C#) — הערך, והפעם הכמה שהוא מופיע */
    private static Map<Cell, String> cellKeys(Step s, boolean ids) {
        Map<Cell, String> m = new IdentityHashMap<Cell, String>();
        Map<String, Integer> occ = new HashMap<String, Integer>();
        for (View v : s.views) {
            if (!(v instanceof ChainView)) continue;
            for (ChainView r : rows((ChainView) v))
                for (Cell c : r.cells) {
                    if (ids) {
                        m.put(c, "#" + c.id);
                    } else {
                        String b = "v" + c.value;
                        Integer k = occ.get(b);
                        k = k == null ? 0 : k + 1;
                        occ.put(b, k);
                        m.put(c, b + "|" + k);
                    }
                }
        }
        return m;
    }

    /** בלי זהות (C#): חוליה שהערך שלה השתנה (setValue) — אותה חוליה אם היא באותו מקום באותו מבנה (ולא "יצאה ונכנסה") */
    private void pairInPlace() {
        Set<String> oldKeys = new HashSet<String>(fromCells.values()), newKeys = new HashSet<String>(toCells.values());
        Map<String, String> oldAt = new HashMap<String, String>();
        for (View v : from.views) {
            if (!(v instanceof ChainView)) continue;
            List<ChainView> rs = rows((ChainView) v);
            for (int r = 0; r < rs.size(); r++)
                for (int i = 0; i < rs.get(r).cells.size(); i++) {
                    String k = fromCells.get(rs.get(r).cells.get(i));
                    if (!newKeys.contains(k)) oldAt.put(fromViews.get(v) + "|" + r + "|" + i, k);
                }
        }
        for (View v : to.views) {
            if (!(v instanceof ChainView)) continue;
            List<ChainView> rs = rows((ChainView) v);
            for (int r = 0; r < rs.size(); r++)
                for (int i = 0; i < rs.get(r).cells.size(); i++) {
                    Cell c = rs.get(r).cells.get(i);
                    if (oldKeys.contains(toCells.get(c))) continue;
                    String k = oldAt.remove(toViews.get(v) + "|" + r + "|" + i);
                    if (k != null) toCells.put(c, k);
                }
        }
    }

    // ------------------------------------------------------------ מה השתנה

    private LinearView oldLinear(String key) {
        for (Map.Entry<View, String> e : fromViews.entrySet())
            if (e.getValue().equals(key) && e.getKey() instanceof LinearView) return (LinearView) e.getKey();
        return null;
    }

    /**
     * מחסנית: האיברים שבתחתית לא זזים — מה שמעליהם הוצא (pop) / נוסף (push). תור: מהראש הוצאו כמה, ובזנב נוספו כמה.
     * אם אין הסבר כזה (למשל השתנה רק מבנה שבתוך תא) — בלי הנפשה למבנה הזה
     */
    private void diffLinear() {
        for (View v : to.views) {
            if (!(v instanceof LinearView)) continue;
            LinearView nv = (LinearView) v;
            String key = toViews.get(v);
            LinearView ov = oldLinear(key);
            if (ov == null) continue;
            List<String> a = ov.items, b = nv.items;
            int on = a.size(), nn = b.size();
            LinDiff d = new LinDiff();
            d.stack = nv.stack;
            d.fromOld = new int[nn];
            int kept;
            if (nv.stack) {
                kept = 0;
                while (kept < on && kept < nn && same(a.get(on - 1 - kept), b.get(nn - 1 - kept))) kept++;
                if (on == nn) continue;   // אותו גודל: לא הכנסה ולא הוצאה (אולי השתנה תוכן של מבנה בתוך תא)
                d.removed = on - kept;
                d.added = nn - kept;
                for (int j = 0; j < nn; j++) d.fromOld[j] = j < d.added ? -1 : j + on - nn;
            } else {
                int drop = 0;
                while (drop <= on && !prefix(a, drop, b)) drop++;
                kept = on - drop;
                d.removed = drop;
                d.added = nn - kept;
                for (int j = 0; j < nn; j++) d.fromOld[j] = j < kept ? drop + j : -1;
            }
            if (d.removed == 0 && d.added == 0) continue;
            if (kept == 0 && on > 0 && nn > 0) continue;   // הכול התחלף — כנראה שינוי בתוך התאים, לא הכנסה / הוצאה
            lin.put(key, d);
            any = true;
            // מה שהוצא: האחרון שהוצא (הקרוב לאלה שנשארו) עף אל הסימן "הוצא" של הצעד החדש
            int toGhost = nv.ghost == null ? -1 : d.removed - 1;
            for (int i = 0; i < d.removed; i++) {
                Exit x = new Exit();
                x.kind = nv.stack ? STACK : QUEUE;
                x.key = "L|" + key + "|" + i;
                x.viewKey = key;
                x.toGhost = i == toGhost;
                x.text = a.get(i);
                x.obj = i < ov.objs.size() ? ov.objs.get(i) : null;
                x.sub = i < ov.subs.size() ? ov.subs.get(i) : null;
                x.mark = i == ov.markIndex ? ov.mark : Mark.NONE;
                if (x.toGhost) ghostViews.add(key);
                exits.add(x);
            }
        }
    }

    /** האם a מהמקום from ועד הסוף הוא ההתחלה של b */
    private static boolean prefix(List<String> a, int from, List<String> b) {
        if (a.size() - from > b.size()) return false;
        for (int i = from; i < a.size(); i++) if (!same(a.get(i), b.get(i - from))) return false;
        return true;
    }

    private static boolean same(String a, String b) {
        return a == null ? b == null : a.equals(b);
    }

    private void diffChains() {
        Set<String> oldKeys = new HashSet<String>(fromCells.values()), newKeys = new HashSet<String>(toCells.values());
        for (String k : newKeys) if (!oldKeys.contains(k)) enter.add(k);
        for (Map.Entry<Cell, String> e : fromCells.entrySet()) {
            if (newKeys.contains(e.getValue())) continue;
            Exit x = new Exit();
            x.kind = CHAIN;
            x.key = "N|" + e.getValue();
            x.ckey = e.getValue();
            x.cell = e.getKey();
            exits.add(x);
        }
        Set<String> was = links(from, fromCells), now = links(to, toCells);
        for (View v : from.views) {
            if (!(v instanceof ChainView)) continue;
            ChainView c = (ChainView) v;
            for (ChainView r : rows(c)) {
                for (int i = 0; i + 1 < r.cells.size(); i++)
                    oldNext.put(fromCells.get(r.cells.get(i)), new String[] { fromCells.get(r.cells.get(i + 1)), "row" });
                if (r != c && !r.cells.isEmpty() && r.joinTo >= 0 && r.joinTo < c.cells.size())
                    oldNext.put(fromCells.get(r.cells.get(r.cells.size() - 1)), new String[] { fromCells.get(c.cells.get(r.joinTo)), "join" });
            }
        }
        for (String l : now) if (!was.contains(l)) newLinks.add(l);
        boolean gone = false;
        for (String l : was) if (!now.contains(l)) gone = true;
        if (!enter.isEmpty() || !newLinks.isEmpty() || gone) any = true;
        for (Exit x : exits) if (x.kind == CHAIN) any = true;
    }

    /** החצים בין החוליות: "a>b" (גם מהאחרונה בענף אל החוליה שהיא מצביעה עליה) */
    private static Set<String> links(Step s, Map<Cell, String> keys) {
        Set<String> res = new HashSet<String>();
        for (View v : s.views) {
            if (!(v instanceof ChainView)) continue;
            ChainView c = (ChainView) v;
            for (ChainView r : rows(c)) {
                for (int i = 0; i + 1 < r.cells.size(); i++) res.add(keys.get(r.cells.get(i)) + ">" + keys.get(r.cells.get(i + 1)));
                if (r != c && !r.cells.isEmpty() && r.joinTo >= 0 && r.joinTo < c.cells.size())
                    res.add(keys.get(r.cells.get(r.cells.size() - 1)) + ">" + keys.get(c.cells.get(r.joinTo)));
            }
        }
        return res;
    }

    // ------------------------------------------------------------ הזמן

    /** כמה זמן ההנפשה, לפי קצב ההפעלה האוטומטית (interval; 0 = לא פועלת). 0 = בלי הנפשה (מהר מדי) */
    static int duration(int interval) {
        if (interval <= 0) return TIME;
        int d = Math.min(TIME, Math.round(interval * 0.6f));
        return d < MIN_TIME ? 0 : d;
    }

    /** מתחילים. false — בלי הנפשה (הפעלה אוטומטית מהירה מדי) */
    boolean begin(int interval) {
        duration = duration(interval);
        if (duration <= 0) return false;
        start = System.currentTimeMillis();
        if (FREEZE < 0 && test < 0) timer.restart();
        return true;
    }

    void stop() {
        timer.stop();
        done = true;
    }

    /** בדיקות: מציירים את הרגע t (0..1) — בלי שעון */
    void at(float t) {
        test = Math.max(0f, Math.min(1f, t));
        timer.stop();
    }

    /** כמה מההנפשה עבר: 0..1 */
    float progress() {
        if (test >= 0) return test;
        if (FREEZE >= 0) return FREEZE;
        if (done) return 1f;
        return Math.min(1f, (System.currentTimeMillis() - start) / (float) Math.max(1, duration));
    }

    /** ההנפשה נגמרה (ואפשר לצייר את הצעד כרגיל) — לא בקפיאה / בבדיקה */
    boolean finished() {
        return test < 0 && FREEZE < 0 && (done || progress() >= 1f);
    }

    int durationMs() {
        return duration;
    }

    public void actionPerformed(ActionEvent e) {
        if (progress() >= 1f) {
            timer.stop();
            done = true;
        }
        owner.repaint();
    }

    // ------------------------------------------------------------ עקומות

    static float clamp(float x) {
        return x < 0 ? 0 : x > 1 ? 1 : x;
    }

    /** החלק של t שבין a ל-b (0..1) */
    static float seg(float t, float a, float b) {
        return clamp((t - a) / (b - a));
    }

    static float easeInOut(float x) {
        return x < 0.5f ? 4 * x * x * x : 1 - (float) Math.pow(-2 * x + 2, 3) / 2;
    }

    static float easeOut(float x) {
        return 1 - (float) Math.pow(1 - x, 3);
    }

    static float easeIn(float x) {
        return x * x * x;
    }

    private static int mix(int a, int b, float e) {
        return Math.round(a + (b - a) * e);
    }

    private static Rectangle lerp(Rectangle a, Rectangle b, float e) {
        return new Rectangle(mix(a.x, b.x, e), mix(a.y, b.y, e), mix(a.width, b.width, e), mix(a.height, b.height, e));
    }

    // ------------------------------------------------------------ איפה כל דבר ברגע הזה

    /** המקום הקודם של מפתח (בקואורדינטות של הציור החדש), או null */
    Rectangle old(String key) {
        Rectangle r = old.get(key);
        if (r == null) return null;
        return new Rectangle(Math.round(r.x + shiftX), r.y, r.width, r.height);
    }

    /** המקום הסופי (בצעד החדש) של מלבן שמצויר עכשיו במבנה שזז */
    private Rectangle fin(Rectangle s) {
        return new Rectangle(s.x + dx, s.y + dy, s.width, s.height);
    }

    /** איפה לצייר מבנה שלם (x, y = המקום שלו בצעד החדש) */
    Point origin(String viewKey, int x, int y) {
        Rectangle o = old("V|" + viewKey);
        if (o == null) return new Point(x, y);
        float e = easeInOut(progress());
        return new Point(mix(o.x, x, e), mix(o.y, y, e));
    }

    private final Map<String, Boolean> fresh = new HashMap<String, Boolean>();

    /**
     * מבנה חדש (לא היה בצעד הקודם, ואין בו חוליה שהייתה — למשל new Node(3) למשתנה חדש): מופיע בהדרגה רק אחרי שהמבנים
     * שמתחתיו זזו ופינו לו מקום (אחרת הוא מצויר מעליהם בזמן שהם עוד בדרך)
     */
    boolean fresh(String viewKey) {
        Boolean b = fresh.get(viewKey);
        if (b != null) return b;
        boolean res = !new HashSet<String>(fromViews.values()).contains(viewKey);   // (IdentityHashMap משווה ערכים לפי זהות)
        if (res) {
            Set<String> had = new HashSet<String>(fromCells.values());
            for (Map.Entry<View, String> e : toViews.entrySet()) {
                if (!e.getValue().equals(viewKey) || !(e.getKey() instanceof ChainView)) continue;
                for (ChainView r : rows((ChainView) e.getKey())) for (Cell c : r.cells) if (had.contains(toCells.get(c))) res = false;
            }
        }
        fresh.put(viewKey, res);
        return res;
    }

    /** השקיפות של מבנה שלם ברגע הזה */
    float viewAlpha(String viewKey) {
        return viewKey != null && fresh(viewKey) ? seg(progress(), 0.55f, 0.92f) : 1f;
    }

    /**
     * מחסנית: איפה כל איבר ברגע הזה. s = המקומות בצעד החדש (כפי שמצוירים עכשיו); alpha — שקיפות לכל איבר.
     * מה שנוסף נופל מלמעלה (מאיץ, ומאט כשהוא דוחף); מה שמתחתיו נשאר במקום עד שהחדש "נוגע" בו — ואז נדחף יחד איתו
     */
    Rectangle[] stack(String key, Rectangle[] s, float[] alpha) {
        LinDiff d = lin.get(key);
        int n = s.length;
        Rectangle[] r = new Rectangle[n];
        for (int j = 0; j < n; j++) { r[j] = s[j]; alpha[j] = 1f; }
        if (d == null) return r;
        float t = progress();
        float e = easeInOut(t);
        Rectangle bottomNew = null, bottomNow = null;   // האיבר החדש התחתון: הסופי, ועכשיו
        for (int j = 0; j < d.added && j < n; j++) {
            Rectangle f = fin(s[j]);
            int drop = f.height * d.added + 22;   // מתחיל מעל הפתח (עובר מאחורי הכותרת — StructPanel מצייר אותה מעליו)
            r[j] = new Rectangle(f.x, Math.round(f.y - drop * (1 - e)), f.width, f.height);
            alpha[j] = seg(t, 0f, 0.25f);
            bottomNew = f;
            bottomNow = r[j];
        }
        for (int j = d.added; j < n; j++) {
            Rectangle f = fin(s[j]);
            Rectangle o = d.fromOld[j] >= 0 ? old("L|" + key + "|" + d.fromOld[j]) : null;
            if (o == null) { r[j] = f; continue; }
            Rectangle m = lerp(o, f, e);
            if (bottomNew != null) {   // נדחף: מתחת לאיבר שנופל, באותו מרווח כמו בסוף
                int want = bottomNow.y + bottomNow.height + (f.y - (bottomNew.y + bottomNew.height));
                int lo = Math.min(o.y, f.y), hi = Math.max(o.y, f.y);
                m.y = Math.max(lo, Math.min(hi, want));
            }
            r[j] = m;
        }
        return r;
    }

    /** תור: מה שנשאר מתקדם לכיוון הראש (קצת אחרי שהראש יוצא); מה שנוסף נכנס מימין, מבחוץ, ומתיישב */
    Rectangle[] queue(String key, Rectangle[] s, float[] alpha, int outside) {
        // (head / tail מצוירים מתחת לאיברים שזזים — StructPanel)
        LinDiff d = lin.get(key);
        int n = s.length;
        Rectangle[] r = new Rectangle[n];
        for (int j = 0; j < n; j++) { r[j] = s[j]; alpha[j] = 1f; }
        if (d == null) return r;
        float t = progress();
        float em = d.removed > 0 ? easeInOut(seg(t, 0.08f, 0.8f)) : easeInOut(t);
        // נכנס: מחליק פנימה, עובר טיפה את המקום (נצמד לאיבר שלפניו) וחוזר — "מתיישב"
        float e = t < 0.82f ? 1.04f * easeInOut(seg(t, 0f, 0.82f)) : 1.04f - 0.04f * easeInOut(seg(t, 0.82f, 1f));
        for (int j = 0; j < n; j++) {
            Rectangle f = fin(s[j]);
            if (d.fromOld[j] < 0) {
                r[j] = new Rectangle(Math.round(f.x + outside * (1 - e)), f.y, f.width, f.height);
                alpha[j] = seg(t, 0f, 0.25f);
                continue;
            }
            Rectangle o = old("L|" + key + "|" + d.fromOld[j]);
            r[j] = o == null ? f : lerp(o, f, em);
        }
        return r;
    }

    /** כמה איברים נוספו למבנה (0 = לא מונפש / לא נוסף כלום) */
    int added(String key) {
        LinDiff d = lin.get(key);
        return d == null ? 0 : d.added;
    }

    /** הקירות של מחסנית / הצינור של תור: מתארכים / מתקצרים בהדרגה */
    Rectangle wall(String key, Rectangle s) {
        if (!lin.containsKey(key)) return s;
        Rectangle o = old("W|" + key);
        return o == null ? s : lerp(o, fin(s), easeInOut(progress()));
    }

    /** הסימן "הוצא": מופיע בהדרגה, כשהאיבר שהוצא כבר יצא */
    float ghostAlpha(String key) {
        return ghostViews.contains(key) ? seg(progress(), 0.42f, 1f) : 1f;
    }

    /**
     * חוליה: איפה היא ברגע הזה. fx = {שקיפות, הגדלה} (מתעדכן).
     * חוליה שהייתה — בדרך מהמקום הישן לחדש; חוליה חדשה — נופלת למקום מלמעלה
     */
    Rectangle cell(String ckey, Rectangle s, float[] fx, String viewKey) {
        fx[0] = 1f;
        fx[1] = 1f;
        Rectangle f = fin(s);
        if (ckey == null) return s;
        float t = progress();
        if (viewKey != null && fresh(viewKey)) t = seg(t, 0.5f, 1f);   // במבנה חדש: נופלת כשהוא מופיע
        Rectangle o = old("N|" + ckey);
        if (o != null) return lerp(o, f, easeInOut(t));
        if (enter.contains(ckey)) {
            float e = easeOut(t);
            fx[0] = seg(t, 0f, 0.4f);
            fx[1] = 0.8f + 0.2f * e;
            return new Rectangle(f.x, Math.round(f.y - 38 * (1 - e)), f.width, f.height);
        }
        return f;
    }

    /**
     * לאן מגיע ברגע הזה החץ מחוליה a אל b (sx, sy = תחילת החץ; tx, ty = הקצה שלו בצעד החדש). null — עוד לא מצויר.
     * חץ שהיה — כרגיל. חץ חדש של חוליה שהצביעה קודם על חוליה אחרת (setNext) — הקצה "עובר" מהחוליה הקודמת אל החדשה;
     * חץ חדש לגמרי — נמתח מהחוליה, אחרי שהחוליות התחילו לזוז
     */
    int[] linkEnd(String a, String b, int sx, int sy, int tx, int ty) {
        if (a == null || b == null || !newLinks.contains(a + ">" + b)) return new int[] { tx, ty };
        float t = progress();
        String[] was = oldNext.get(a);
        Rectangle o = was == null || was[0] == null || was[0].equals(b) ? null : old("N|" + was[0]);
        if (o != null) {
            int ox = o.x, oy = o.y + o.height / 2;
            if ("join".equals(was[1])) { ox = o.x + Math.min(18, (o.width - 20) / 3); oy = o.y + o.height + 1; }
            float u = easeInOut(seg(t, 0.05f, 0.9f));
            return new int[] { mix(ox, tx, u), mix(oy, ty, u) };
        }
        float p = easeOut(seg(t, 0.3f, 1f));
        if (p <= 0.02f) return null;
        return new int[] { mix(sx, tx, p), mix(sy, ty, p) };
    }

    /**
     * חוליה שנמחקת: החץ שיצא ממנה (אל החוליה שהצביעה עליה) — "נחתך" ונעלם כשהיא נופלת.
     * f = המקום שלה ברגע הזה (exit); pw = רוחב תא המצביע. מחזיר {x1, y1, x2, y2, שקיפות} או null
     */
    float[] exitLink(Exit x, float[] f, int pw) {
        if (x.ckey == null) return null;
        String[] was = oldNext.get(x.ckey);
        if (was == null) return null;
        Rectangle tr = now.get(was[0]);
        if (tr == null) tr = old("N|" + was[0]);
        if (tr == null) return null;
        float a = f[4] * (1 - seg(progress(), 0.12f, 0.5f));
        if (a <= 0.01f) return null;
        float cx = f[0] + f[2] / 2, cy = f[1] + f[3] / 2, sc = f[5];
        float sx = cx + (f[0] + f[2] - pw / 2f - cx) * sc, sy = cy;
        float tx = tr.x, ty = tr.y + tr.height / 2f;
        if ("join".equals(was[1])) { tx = tr.x + Math.min(18, (tr.width - 20) / 3); ty = tr.y + tr.height + 1; }
        return new float[] { sx, sy, tx, ty, a };
    }

    /**
     * איבר שהוצא, ברגע הזה: {x, y, רוחב, גובה, שקיפות, הגדלה, כמה אדום}; null — כבר נעלם.
     * מחסנית: מורם אל הסימן "הוצא" (מעל המחסנית), ואז ממשיך למעלה, מתכווץ ונמחק. תור: יוצא שמאלה דרך היציאה.
     * חוליה: נופלת מטה, מאדימה ונמחקת
     */
    float[] exit(Exit x) {
        Rectangle o = old(x.key);
        if (o == null) return null;
        float t = progress();
        if (t >= 1f) return null;
        Rectangle g = x.toGhost ? ghosts.get(x.viewKey) : null;
        float px, py, alpha, scale, red;
        if (x.kind == CHAIN) {
            float e = easeInOut(t), v = seg(t, 0.35f, 1f);
            px = o.x;
            py = o.y + 46 * e;
            red = seg(t, 0f, 0.3f);
            alpha = 1 - easeIn(v);
            scale = 1 - 0.3f * v;
        } else {
            Rectangle a;
            if (g == null) {   // בלי סימן "הוצא" — יוצא החוצה בכיוון שלו
                a = x.kind == STACK ? new Rectangle(o.x, o.y - o.height - 18, o.width, o.height)
                        : new Rectangle(o.x - o.width - 26, o.y, o.width, o.height);
            } else {
                a = new Rectangle(g.x + (g.width - o.width) / 2, g.y + (g.height - o.height) / 2, o.width, o.height);
            }
            // שלב א: מורם / מחליק החוצה (במחסנית — קצת מעל הסימן "הוצא"); שלב ב: ממשיך הלאה, מתכווץ ונמחק,
            // והסימן המקווקו "הוצא" מופיע במקום שבו הוא היה
            if (x.kind == STACK) a.y -= 10;
            float u = x.kind == STACK ? easeOut(seg(t, 0f, 0.45f)) : easeInOut(seg(t, 0f, 0.45f)), v = seg(t, 0.45f, 1f);
            px = o.x + (a.x - o.x) * u;
            py = o.y + (a.y - o.y) * u;
            if (x.kind == STACK) { py -= 26 * v; px += 16 * v; }
            else { px -= 34 * v; py += 10 * v; }
            red = seg(t, 0.08f, 0.45f);
            alpha = 1 - easeIn(v);
            scale = 1 - 0.55f * v * v;
        }
        if (alpha <= 0.01f) return null;
        return new float[] { px, py, o.width, o.height, alpha, scale, red };
    }
}
