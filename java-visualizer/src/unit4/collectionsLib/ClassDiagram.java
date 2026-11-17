package unit4.collectionsLib;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.MouseInfo;
import java.awt.Point;
import java.awt.PointerInfo;
import java.awt.Polygon;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import javax.swing.JComponent;
import javax.swing.JLayeredPane;
import javax.swing.JRootPane;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

import unit4.collectionsLib.Model.Step;

/**
 * תרשים המחלקות בתחתית התצוגה הוויזואלית (Poly, כלל 9 — רק כשיש הורשה או הכלה בין מחלקות של התלמיד):
 * קטן — תגית לכל מחלקה בצבע שלה, המשולש של הירושה והיהלומים של ההכלה, תגית ההפניה של השורה ("pet ←"), ושורת מקרא.
 * מעבר עם העכבר — בועה מעל התצוגה עם התרשים המלא (UML: מאפיינים, פעולות, שמות המאפיינים על הקווים, הפעולה שרצה בצהוב, מקרא מלא),
 * שנשארת בתוך החלון ונסגרת כשהעכבר יוצא מהתרשים ומהבועה.
 */
final class ClassDiagram extends JComponent {
    private static final int PAD = 10, HEAD = 24;
    /** לצילומים בלבד (-Ddsviz.diagramOpen=true / DSVIZ_DIAGRAM_OPEN=1): הבועה פתוחה תמיד */
    static final boolean FORCE_OPEN = Boolean.getBoolean("dsviz.diagramOpen") || "1".equals(System.getenv("DSVIZ_DIAGRAM_OPEN"));

    private Step step;
    private Balloon balloon;
    private Timer watch;
    private boolean hover;

    ClassDiagram() {
        setOpaque(false);
        setVisible(false);
        MouseAdapter m = new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) { open(); }

            @Override
            public void mouseMoved(MouseEvent e) {
                if (balloon == null || !balloon.isVisible()) open();
            }
        };
        addMouseListener(m);
        addMouseMotionListener(m);
    }

    void setStep(Step s) {
        step = s;
        boolean show;
        try {
            show = Poly.diagram();
        } catch (RuntimeException e) {
            show = false;
        }
        if (isVisible() != show) setVisible(show);
        revalidate();
        repaint();
        if (balloon != null && balloon.isVisible()) {
            place();
            balloon.repaint();
        } else if (FORCE_OPEN && show) {
            SwingUtilities.invokeLater(new Runnable() {
                public void run() { open(); }
            });
        }
    }

    private Poly.Live live() {
        return Poly.live(step);
    }

    private Dimension natural() {
        return PolyPaint.smallDiagram(this, null, live(), 0, 0);
    }

    private double scale() {
        Dimension n = natural();
        int avail = getParent() != null && getParent().getWidth() > 100 ? getParent().getWidth() - 24 : 900;
        // קטן: ברוחב שיש, ולא גבוה מ-~180 נקודות (עץ עמוק — מוקטן), כדי שלא יגזול מקום מהמבנים
        double fit = Math.min(avail / (double) (n.width + 2 * PAD), 180.0 / (n.height + HEAD + 2 * PAD));
        return Math.min(Theme.visZoom, Math.max(0.5, Math.min(1, fit) * Theme.visZoom));
    }

    @Override
    public Dimension getPreferredSize() {
        if (!isVisible()) return new Dimension(0, 0);
        Dimension n = natural();
        double z = scale();
        int w = Math.max(n.width + 2 * PAD, titleW() + 2 * PAD);
        return new Dimension((int) Math.ceil(w * z), (int) Math.ceil((HEAD + n.height + 2 * PAD) * z));
    }

    private int titleW() {
        return PolyPaint.w(this, "תרשים המחלקות", PolyPaint.bold(14)) + Math.round(PolyPaint.rtlW(null, "מעבר עם העכבר — התרשים המלא", PolyPaint.ui(11))) + 20;
    }

    @Override
    protected void paintComponent(Graphics g0) {
        Graphics2D g = (Graphics2D) g0.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            double z = scale();
            g.scale(z, z);
            int w = (int) (getWidth() / z), h = (int) (getHeight() / z);
            paintSmall(g, w, h, hover || balloon != null && balloon.isVisible());
        } catch (RuntimeException e) {
            // בלי התרשים
        } finally {
            g.dispose();
        }
    }

    /** הכרטיס הקטן (גם לבדיקות בלי חלון) */
    void paintSmall(Graphics2D g, int w, int h, boolean active) {
        g.setColor(Color.WHITE);
        g.fillRoundRect(1, 1, w - 3, h - 3, 12, 12);
        g.setColor(active ? PolyPaint.ACCENT : new Color(0xD5DBE8));
        g.setStroke(new BasicStroke(active ? 2f : 1.2f));
        g.drawRoundRect(1, 1, w - 3, h - 3, 12, 12);
        float tw = PolyPaint.rtlW(g, "תרשים המחלקות", PolyPaint.bold(14));
        PolyPaint.rtlAt(g, "תרשים המחלקות", PolyPaint.bold(14), PolyPaint.INK, w - PAD - tw, PAD + 12);
        float hw = PolyPaint.rtlW(g, "מעבר עם העכבר — התרשים המלא", PolyPaint.ui(11));
        PolyPaint.rtlAt(g, "מעבר עם העכבר — התרשים המלא", PolyPaint.ui(11), PolyPaint.MUTED, w - PAD - tw - 10 - hw, PAD + 12);
        Dimension n = natural();
        PolyPaint.smallDiagram(this, g, live(), (w - n.width) / 2, PAD + HEAD - 2);
    }

    // ------------------------------------------------------------ הבועה

    private void open() {
        hover = true;
        JRootPane root = SwingUtilities.getRootPane(this);
        if (root == null || !isShowing()) return;
        JLayeredPane lp = root.getLayeredPane();
        if (balloon == null) balloon = new Balloon();
        if (balloon.getParent() != lp) lp.add(balloon, JLayeredPane.POPUP_LAYER);
        place();
        balloon.setVisible(true);
        repaint();
        if (watch == null) {
            watch = new Timer(140, e -> check());
            watch.start();
        } else if (!watch.isRunning()) {
            watch.start();
        }
    }

    /** הבועה מעל התרשים הקטן, ממורכזת אליו ובתוך החלון (מוקטנת אם צריך); הזנב מצביע עליו */
    private void place() {
        if (balloon == null || balloon.getParent() == null) return;
        JLayeredPane lp = (JLayeredPane) balloon.getParent();
        Dimension full = PolyPaint.fullDiagram(this, null, live(), 0, 0);
        int pad = 16, tail = 12;
        Point top = SwingUtilities.convertPoint(this, getWidth() / 2, 0, lp);
        double availW = lp.getWidth() - 16, availH = Math.max(120, top.y - 8 - tail - 4);
        double s = Math.min(Theme.visZoom, Math.min(availW / (full.width + 2 * pad), availH / (full.height + 2 * pad)));
        int bw = (int) Math.ceil((full.width + 2 * pad) * s), bh = (int) Math.ceil((full.height + 2 * pad) * s) + tail;
        int bx = Math.max(8, Math.min(lp.getWidth() - bw - 8, top.x - bw / 2)), by = Math.max(8, top.y - bh - 2);
        balloon.scale = s;
        balloon.pad = pad;
        balloon.tail = tail;
        balloon.tailX = Math.max(24, Math.min(bw - 24, top.x - bx));
        balloon.setBounds(bx, by, bw, bh);
    }

    /** העכבר יצא מהתרשים הקטן ומהבועה — הבועה נסגרת */
    private void check() {
        if (balloon == null || !balloon.isVisible()) {
            if (watch != null) watch.stop();
            return;
        }
        if (FORCE_OPEN) return;
        PointerInfo pi;
        try {
            pi = MouseInfo.getPointerInfo();
        } catch (RuntimeException e) {
            pi = null;
        }
        if (pi == null || !isShowing()) {
            close();
            return;
        }
        Point p = pi.getLocation();
        Point me = new Point(p);
        SwingUtilities.convertPointFromScreen(me, this);
        Point bp = new Point(p);
        SwingUtilities.convertPointFromScreen(bp, balloon);
        if (new Rectangle(0, 0, getWidth(), getHeight()).contains(me) || new Rectangle(0, 0, balloon.getWidth(), balloon.getHeight()).contains(bp)) return;
        close();
    }

    private void close() {
        hover = false;
        if (balloon != null) balloon.setVisible(false);
        if (watch != null) watch.stop();
        repaint();
    }

    /** הבועה: צל, מסגרת מעוגלת, זנב למטה אל התרשים הקטן — ובתוכה התרשים המלא */
    private final class Balloon extends JComponent {
        double scale = 1;
        int pad = 16, tail = 12, tailX = 40;

        Balloon() {
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                int w = getWidth(), h = getHeight() - tail;
                for (int i = 6; i >= 1; i--) {   // צל רך
                    g.setColor(new Color(20, 30, 60, 9));
                    g.fillRoundRect(2 + i / 2, 4 + i / 2, w - 4, h - 4, 18 + i, 18 + i);
                }
                Polygon t = new Polygon(new int[] { tailX - 11, tailX + 11, tailX }, new int[] { h - 3, h - 3, h + tail - 2 }, 3);
                g.setColor(Color.WHITE);
                g.fillRoundRect(0, 0, w - 4, h - 4, 18, 18);
                g.fillPolygon(t);
                g.setColor(new Color(0xC8D0E0));
                g.setStroke(new BasicStroke(1.3f));
                g.drawRoundRect(0, 0, w - 4, h - 4, 18, 18);
                g.drawLine(tailX - 11, h - 4, tailX, h + tail - 3);
                g.drawLine(tailX + 11, h - 4, tailX, h + tail - 3);
                g.scale(scale, scale);
                PolyPaint.fullDiagram(ClassDiagram.this, g, live(), pad, pad);
            } catch (RuntimeException e) {
                // בלי התרשים המלא
            } finally {
                g.dispose();
            }
        }
    }
}
