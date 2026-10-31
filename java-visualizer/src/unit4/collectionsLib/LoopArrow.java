package unit4.collectionsLib;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.LinearGradientPaint;
import java.awt.Paint;
import java.awt.RadialGradientPaint;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.geom.AffineTransform;
import java.awt.geom.Arc2D;
import java.awt.geom.CubicCurve2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.FlatteningPathIterator;
import java.awt.geom.Path2D;
import java.awt.geom.PathIterator;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.swing.JComponent;
import javax.swing.Timer;
import javax.swing.text.JTextComponent;

import unit4.collectionsLib.LoopViz.Loop;
import unit4.collectionsLib.Model.Frame;
import unit4.collectionsLib.Model.Kind;
import unit4.collectionsLib.Model.Step;

/**
 * "חוזרים לראש הלולאה": בצעד שבו הלולאה חוזרת מסוף הגוף אל ההתחלה — חץ מונפש בחלון הקוד,
 * בשוליים משמאל ללולאה: יוצא מהסוף של הלולאה, עולה, ונכנס לשורה שחוזרים אליה. אחרי רגע הוא נעלם.
 * רק כדי להמחיש לתלמיד שהלולאה חוזרת על עצמה.
 *
 * מתי זו חזרה: השורה הקודמת שרצה באותה קריאה (אותו עומק, אותה פעולה) הייתה בתוך הגוף של הלולאה (או בסוגר שלה),
 * והשורה עכשיו היא שורת הלולאה (for / while / for-each). ב-do-while: מה-while שבסוף אל תחילת הגוף.
 * בלולאות מקוננות — הלולאה הפנימית ביותר שמתאימה (כשהפנימית נגמרת והחיצונית חוזרת — החץ של החיצונית).
 * עובד ב-Java (שורה-שורה עם JDI) וב-C# (dsviz-cs) — בשתיהן הצעדים באותו סדר.
 */
final class LoopArrow {
    private LoopArrow() {}

    /** חזרה אחת לראש הלולאה */
    static final class Jump {
        Loop loop;
        int from;          // השורה שרצה קודם באותה קריאה (בגוף / הסוגר / ה-while של do)
        int to;            // השורה עכשיו: שורת הלולאה (ב-do: תחילת הגוף)
        int bottom;        // מאיפה החץ יוצא: הסוף של הלולאה (הסוגר, וב-do — שורת ה-while)
        String fileKey;    // הקובץ (כמו ב-Viewer: חבילה + שם הקובץ)
    }

    private static final Map<String, List<Loop>> parsed = new HashMap<String, List<Loop>>();

    private static synchronized List<Loop> loops(Frame f) {
        String key = f.packagePath() + f.file;
        List<Loop> l = parsed.get(key);
        if (l == null) {
            List<String> lines = SourceFiles.lines(f);
            l = lines == null ? new ArrayList<Loop>() : LoopViz.parse(lines);
            parsed.put(key, l);
        }
        return l;
    }

    /** האם בצעד index הלולאה חוזרת להתחלה — ואם כן, איזו (null = לא) */
    static Jump at(int index) {
        Step s;
        try {
            s = Recorder.step(index);
        } catch (RuntimeException e) {
            return null;
        }
        if (s == null || s.kind != Kind.LINE || s.where == null || s.where.file == null || s.vars == null) return null;
        List<Loop> loops = loops(s.where);
        if (loops.isEmpty()) return null;
        Step prev = previousInFrame(index, s);
        if (prev == null) return null;
        int cur = s.where.line, from = prev.where.line;
        Loop best = null;
        for (Loop l : loops) {
            boolean back;
            if (l.kind.equals("do")) back = from == l.end && l.end > l.header && cur >= l.header && cur < l.end;
            else back = cur == l.header && from > l.header && from <= l.end;
            if (back && (best == null || l.end - l.header < best.end - best.header)) best = l;   // הפנימית ביותר
        }
        if (best == null) return null;
        Jump j = new Jump();
        j.loop = best;
        j.from = from;
        j.to = cur;
        j.bottom = Math.max(best.end, from);
        j.fileKey = s.where.packagePath() + s.where.file;
        return j;
    }

    /** השורה הקודמת שרצה באותה קריאה — או null אם הקריאה רק התחילה (או שלפניה נגמרה קריאה אחרת באותו עומק) */
    private static Step previousInFrame(int index, Step s) {
        int depth = s.calls.size();
        for (int j = index - 1; j >= 0 && j > index - 20000; j--) {
            Step t = Recorder.step(j);
            if (t.where == null) continue;
            int d = t.calls.size();
            if (d < depth) return null;   // נכנסנו לפעולה רק עכשיו
            if (d > depth) continue;      // בתוך פעולה שנקראה מהשורה הקודמת
            if (t.kind == Kind.RETURN || t.kind == Kind.ERROR || t.kind == Kind.END) return null;   // קריאה קודמת באותו עומק נגמרה
            if (t.kind != Kind.LINE) continue;
            if (!t.where.method.equals(s.where.method) || t.where.file == null || !t.where.file.equals(s.where.file)) return null;
            return t;
        }
        return null;
    }

    /** בשביל הבדיקות (מצב טקסט, -Ddsviz.conditions=true): בכל צעד שבו הלולאה חוזרת להתחלה — מאיפה לאן */
    static String dump() {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < Recorder.stepCount(); i++) {
            Jump j = at(i);
            if (j == null) continue;
            b.append("  [חזרה #").append(i + 1).append(" שורה ").append(j.to).append(' ').append(j.loop.kind)
                    .append(": מ-").append(j.from).append(", החץ מ-").append(j.bottom).append(" | ").append(j.loop.text).append("]\n");
        }
        return b.toString();
    }

    // ------------------------------------------------------------ הציור

    static final Color FROM = new Color(0x7C3AED), TO = new Color(0x2F6FED);

    /**
     * מצייר את החץ בחלון הקוד.
     * draw = כמה מהחץ כבר "צויר" (0..1, הראש הזוהר נוסע לאורכו); ripple = גל קטן בקצה כשהחץ נכנס לשורה (0..1, שלילי = בלי);
     * alpha = שקיפות (בדעיכה). avoid = שורות שכבר יש בהן חץ של פעולת עזר (CallPaint) — שם החץ עובר קצת מעל / מתחת לאמצע.
     * מחזיר את המלבן שצויר בו (כדי לרענן רק אותו בהנפשה), או null אם לא צויר.
     */
    static Rectangle paint(Graphics2D g0, JTextComponent c, Jump j, float draw, float ripple, float alpha, Set<Integer> avoid) {
        if (j == null || alpha <= 0.01f || draw <= 0f) return null;
        Rectangle rt = CondPaint.lineRect(c, j.to), rb = CondPaint.lineRect(c, j.bottom);
        if (rt == null || rb == null) return null;
        Rectangle vis = c.getVisibleRect();
        if (rt.y + rt.height <= vis.y || rt.y >= vis.y + vis.height) return null;   // השורה שחוזרים אליה לא נראית — בלי חץ
        int xt = CallPaint.textStart(c, j.to), xb = CallPaint.textStart(c, j.bottom), xh = CallPaint.textStart(c, j.loop.header);
        int xmin = Math.min(xt, Math.min(xb, xh));
        FontMetrics fm = c.getFontMetrics(c.getFont());
        int cw = Math.max(6, fm.charWidth('0'));
        // כשיש בשורה גם חץ של פעולת עזר (CallPaint — הוא באמצע השורה, ויוצא שמאלה לפס שלו):
        // בשורה שחוזרים אליה החץ נכנס קצת מתחת לאמצע, ובשורת הסוף יוצא קצת מעליו — כך שהם לא נחתכים
        boolean busyTo = avoid != null && avoid.contains(j.to), busyBottom = avoid != null && avoid.contains(j.bottom);
        float yt = rt.y + rt.height / 2f + (busyTo ? 7 : 0);
        float yb = rb.y + rb.height / 2f - (busyBottom ? 6 : 0);
        float edge = vis.y + vis.height;
        boolean below = yb > edge - 8;   // הסוף של לולאה ארוכה מתחת למה שרואים — הקשת "עולה מלמטה", מהקצה
        if (below) yb = Math.max(edge + 6, yt + rt.height * 0.8f);
        yb = Math.max(yb, yt + rt.height * 0.8f);
        // הקשת: משמאל לשורות של הלולאה (בהזחה), ולא על הקוד; מימין לפס של החצים של פעולות העזר (x=7)
        float bulge = Math.max(3.3f * cw, Math.min(5.5f * cw, 0.3f * (yb - yt) + 2.2f * cw));   // לולאה ארוכה — קשת רחבה יותר
        float left = Math.max(xmin - bulge, Math.min(15, xmin - 7));
        float sx = below ? left : xb - 6, tx = xt - 2;
        CubicCurve2D curve;
        if (below) {   // עולה ישר מהקצה התחתון, ומתעגלת ימינה אל השורה
            curve = new CubicCurve2D.Float(sx, yb, left, yt, left + (tx - left) * 0.3f, yt, tx, yt);
        } else {
            float cx = (8 * left - sx - tx) / 6f;   // נקודות הבקרה — כך שהנקודה השמאלית ביותר של הקשת תהיה ב-left
            curve = new CubicCurve2D.Float(sx, yb, cx, yb, cx, yt, tx, yt);
        }
        List<float[]> pts = flatten(curve);
        float total = pts.get(pts.size() - 1)[2];
        float upto = total * Math.min(1f, draw);

        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        if (alpha < 1f) g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
        Paint grad = new LinearGradientPaint(new Point2D.Float(0, yb), new Point2D.Float(0, yt), new float[] { 0f, 1f }, new Color[] { FROM, TO });
        float[] tip = pointAt(pts, upto);
        // בסוף — הכיוון של הקשת עצמה (ימינה, אל השורה), גם כשהקשת צפופה (לולאה בתחילת השורה, בלי הזחה)
        float angle = upto >= total - 0.5f ? (float) Math.atan2(curve.getY2() - curve.getCtrlY2(), curve.getX2() - curve.getCtrlX2())
                : (float) Math.atan2(tip[4], tip[3]);
        float headLen = Math.max(7f, Math.min(busyTo ? 10f : 12f, tx - left + 3));
        Path2D body = prefix(pts, Math.max(0, upto - headLen * 0.55f));
        // הזוהר מתחת לקו
        g.setStroke(new BasicStroke(9f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(new Color(0x6D, 0x5B, 0xF0, 46));
        g.draw(body);
        // הקו עצמו: 3px, מעוגל, בגוון מסגול (למטה) לכחול (למעלה)
        g.setPaint(grad);
        g.setStroke(new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(body);
        // נקודת היציאה (בסוף הלולאה) — אם הסוף נראה
        if (!below) {
            g.setColor(FROM);
            g.fill(new Ellipse2D.Float(sx - 3.5f, yb - 3.5f, 7, 7));
            g.setColor(Color.WHITE);
            g.fill(new Ellipse2D.Float(sx - 1.5f, yb - 1.5f, 3, 3));
        }
        // סמל קטן של "חוזרים" (↻) בתוך הקשת, כשיש מקום
        if (draw >= 1f && yb - yt >= rt.height * 3.5f && left + 25 <= xmin - 2) {   // רק בלולאה של כמה שורות, אחרי שהחץ הגיע
            float my = (yt + Math.min(yb, edge)) / 2f, mx = left + 15;
            float a = ripple < 0f ? 1f : Math.min(1f, ripple * 2.5f);
            Graphics2D r = (Graphics2D) g.create();
            if (alpha * a < 1f) r.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha * a));
            r.setColor(Color.WHITE);
            r.fill(new Ellipse2D.Float(mx - 8.5f, my - 8.5f, 17, 17));
            r.setPaint(grad);
            r.setStroke(new BasicStroke(1.2f));
            r.draw(new Ellipse2D.Float(mx - 8.5f, my - 8.5f, 17, 17));
            r.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            r.draw(new Arc2D.Float(mx - 4.5f, my - 4.5f, 9, 9, 110, 280, Arc2D.OPEN));
            // ראש קטן בסוף הקשת (בכיוון השעון — כמו ↻)
            double end = Math.toRadians(110);
            float hx = mx + 4.5f * (float) Math.cos(end), hy = my - 4.5f * (float) Math.sin(end);
            Path2D h = new Path2D.Float();
            h.moveTo(hx + 3.2f, hy - 0.4f);
            h.lineTo(hx - 1.2f, hy - 2.6f);
            h.lineTo(hx - 0.6f, hy + 2.4f);
            h.closePath();
            r.fill(h);
            r.dispose();
        }
        // הזוהר של הראש (בזמן שהחץ "מצייר את עצמו")
        if (draw < 1f) {
            float rad = 12;
            g.setPaint(new RadialGradientPaint(new Point2D.Float(tip[0], tip[1]), rad, new float[] { 0f, 0.35f, 1f },
                    new Color[] { new Color(255, 255, 255, 235), new Color(0x8B, 0x7C, 0xF6, 150), new Color(0x8B, 0x7C, 0xF6, 0) }));
            g.fill(new Ellipse2D.Float(tip[0] - rad, tip[1] - rad, rad * 2, rad * 2));
        }
        // גל קטן כשהחץ נכנס לשורה
        if (draw >= 1f && ripple >= 0f && ripple <= 1f) {
            float rr = 5 + 11 * ripple;
            g.setColor(new Color(TO.getRed(), TO.getGreen(), TO.getBlue(), Math.round(150 * (1 - ripple))));
            g.setStroke(new BasicStroke(2f));
            g.draw(new Ellipse2D.Float(tip[0] - rr, tip[1] - rr, rr * 2, rr * 2));
        }
        // ראש החץ: נוסע עם הקצה, ובסוף מצביע ימינה — אל השורה
        Path2D head = new Path2D.Float();
        head.moveTo(0, 0);
        head.lineTo(-headLen, -headLen * 0.54f);
        head.lineTo(-headLen * 0.68f, 0);
        head.lineTo(-headLen, headLen * 0.54f);
        head.closePath();
        AffineTransform tr = AffineTransform.getTranslateInstance(tip[0], tip[1]);
        tr.rotate(angle);
        g.setPaint(grad);
        g.fill(tr.createTransformedShape(head));
        g.dispose();
        int x2 = (int) Math.ceil(Math.max(sx, tx)) + 20;
        return new Rectangle(0, (int) Math.floor(yt) - 20, x2, (int) Math.ceil(yb - yt) + 40);
    }

    /** הקשת כרשימת נקודות {x, y, המרחק מההתחלה} */
    private static List<float[]> flatten(CubicCurve2D curve) {
        List<float[]> pts = new ArrayList<float[]>();
        float[] seg = new float[6];
        float len = 0, px = 0, py = 0;
        for (PathIterator it = new FlatteningPathIterator(curve.getPathIterator(null), 0.4); !it.isDone(); it.next()) {
            it.currentSegment(seg);
            if (!pts.isEmpty()) len += (float) Math.hypot(seg[0] - px, seg[1] - py);
            pts.add(new float[] { seg[0], seg[1], len });
            px = seg[0];
            py = seg[1];
        }
        return pts;
    }

    /** הנקודה במרחק d מההתחלה: {x, y, d, כיוון x, כיוון y} */
    private static float[] pointAt(List<float[]> pts, float d) {
        for (int i = 1; i < pts.size(); i++) {
            float[] a = pts.get(i - 1), b = pts.get(i);
            if (b[2] >= d || i == pts.size() - 1) {
                float seg = Math.max(1e-3f, b[2] - a[2]), t = Math.max(0f, Math.min(1f, (d - a[2]) / seg));
                return new float[] { a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t, d, b[0] - a[0], b[1] - a[1] };
            }
        }
        float[] p = pts.get(0);
        return new float[] { p[0], p[1], 0, -1, 0 };
    }

    /** החלק של הקשת מההתחלה ועד המרחק d */
    private static Path2D prefix(List<float[]> pts, float d) {
        Path2D p = new Path2D.Float();
        p.moveTo(pts.get(0)[0], pts.get(0)[1]);
        for (int i = 1; i < pts.size(); i++) {
            float[] b = pts.get(i);
            if (b[2] >= d) {
                float[] e = pointAt(pts, d);
                p.lineTo(e[0], e[1]);
                return p;
            }
            p.lineTo(b[0], b[1]);
        }
        return p;
    }

    // ------------------------------------------------------------ ההנפשה

    /**
     * ההנפשה בחלון הקוד: החץ מצייר את עצמו (~0.5 שנ'), נשאר (~0.5), דועך (~0.4) — ואז נעלם לגמרי.
     * מתחילה מחדש בכל צעד; בהפעלה אוטומטית מהירה — מתקצרת כדי להסתיים לפני הצעד הבא.
     * לצילומים ולבדיקות: -Ddsviz.loopArrowFreeze=0.6 — החץ "קפוא" (0..1 = כמה ממנו צויר, 1..2 = דעיכה).
     */
    static final class Anim implements ActionListener {
        static final int DRAW = 500, HOLD = 500, FADE = 400;
        private static final float FREEZE = freeze();
        private final JComponent owner;
        private final Timer timer = new Timer(16, this);
        private Jump jump;
        private long start;
        private int draw = DRAW, hold = HOLD, fade = FADE;
        private Rectangle bounds;

        Anim(JComponent owner) {
            this.owner = owner;
            timer.setCoalesce(true);
        }

        private static float freeze() {
            try {
                String v = System.getProperty("dsviz.loopArrowFreeze");
                return v == null ? -1f : Math.max(0f, Math.min(2f, Float.parseFloat(v.trim())));
            } catch (RuntimeException e) {
                return -1f;
            }
        }

        /** צעד חדש: j = החזרה שבו (או null — אז החץ הקודם נעלם מיד). interval = כל כמה זמן מתקדמת ההפעלה האוטומטית (0 = לא פועלת) */
        void start(Jump j, int interval) {
            Rectangle old = bounds;
            jump = j;
            bounds = null;
            if (old != null) owner.repaint(old);
            if (j == null) {
                timer.stop();
                return;
            }
            int full = DRAW + HOLD + FADE;
            float k = interval > 0 && interval * 0.85f < full ? Math.max(0.04f, interval * 0.85f / full) : 1f;
            draw = Math.max(1, Math.round(DRAW * k));
            hold = Math.round(HOLD * k);
            fade = Math.max(1, Math.round(FADE * k));
            start = System.currentTimeMillis();
            if (FREEZE >= 0) timer.stop();
            else timer.restart();
            owner.repaint();
        }

        /** כמה זמן כל ההנפשה (מילישניות) — אחרי זה החץ נעלם */
        int duration() {
            return draw + hold + fade;
        }

        /** יש עכשיו חץ (מצויר או בהנפשה) */
        boolean active() {
            return jump != null;
        }

        void paint(Graphics2D g, JTextComponent c, Set<Integer> avoid) {
            if (jump == null) return;
            float d, r = -1f, a = 1f;
            if (FREEZE >= 0) {
                d = Math.min(1f, FREEZE);
                if (FREEZE > 1f) a = Math.max(0f, 2f - FREEZE);
            } else {
                long t = System.currentTimeMillis() - start;
                d = Math.min(1f, t / (float) draw);
                if (t >= draw && t < draw + hold) r = (t - draw) / (float) Math.max(1, Math.min(hold, 380));
                if (r > 1f) r = -1f;
                if (t > draw + hold) a = Math.max(0f, 1f - (t - draw - hold) / (float) fade);
            }
            Rectangle b = LoopArrow.paint(g, c, jump, d, r, a, avoid);
            if (b != null) bounds = bounds == null ? b : bounds.union(b);
        }

        public void actionPerformed(ActionEvent e) {
            if (jump == null || System.currentTimeMillis() - start > draw + hold + fade) {
                timer.stop();
                jump = null;   // נעלם לגמרי
            }
            if (bounds != null) owner.repaint(bounds);
            else owner.repaint();
            if (jump == null) bounds = null;
        }
    }
}
