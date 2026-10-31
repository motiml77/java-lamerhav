package unit4.collectionsLib;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Window;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.HierarchyEvent;
import java.awt.event.HierarchyListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.AffineTransform;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.net.URI;

import javax.swing.JComponent;
import javax.swing.SwingUtilities;

/**
 * "תמכו בפרויקט" — כפתור שקט בקצה סרגל השליטה (כמו "Sponsor" ב-GitHub): מסגרת דקה, לב קטן, בלי צבעים צועקים.
 * בריחוף — גוון ורוד עדין והלב מתמלא. בחלון צר מוצג רק הלב. פותח את דף התרומה ב-PayPal בדפדפן.
 */
final class Donate extends JComponent {
    static final String TEXT = "תמכו בפרויקט";
    static final String TIP = "התוכנה חינמית — אם היא עוזרת לך, אפשר לתמוך בה ב-PayPal";
    /** מתחת לרוחב החלון הזה מוצג רק הלב (הטקסט "תמכו בפרויקט" מופיע בעצה) */
    private static final int COMPACT_BELOW = 1100;

    private static final Color LINE = new Color(0xDFE3EA);          // כמו המסגרת של פס המידע
    private static final Color LINE_HOVER = new Color(0xF5C2D8);    // ורוד רך
    private static final Color INK = new Color(0x3B4455);
    private static final Color INK_HOVER = new Color(0x9D174D);     // ורוד כהה — קריא על הרקע הבהיר
    private static final Color HEART = new Color(0xE0679A);         // ורוד מעומעם במנוחה
    private static final Color HEART_HOVER = new Color(0xDB2777);
    private static final Color TINT = new Color(0xFDF2F8);          // רקע בריחוף
    private static final Color TINT_DOWN = new Color(0xFCE7F3);     // רקע בלחיצה

    private boolean hover, down, compact, hooked;

    Donate() {
        setOpaque(false);
        setFocusable(false);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        setToolTipText(TIP);
        addMouseListener(new MouseAdapter() {
            public void mouseEntered(MouseEvent e) { hover = true; repaint(); }
            public void mouseExited(MouseEvent e) { hover = false; down = false; repaint(); }
            public void mousePressed(MouseEvent e) { down = true; repaint(); }
            public void mouseReleased(MouseEvent e) {
                boolean click = down && contains(e.getPoint());
                down = false;
                repaint();
                if (click) open(Theme.DONATE_URL);
            }
        });
        // בחלון צר — רק הלב. מאזינים לשינוי גודל החלון (פעם אחת, כשהכפתור נכנס לחלון)
        addHierarchyListener(new HierarchyListener() {
            public void hierarchyChanged(HierarchyEvent e) {
                final Window w = SwingUtilities.getWindowAncestor(Donate.this);
                if (w == null || hooked) return;
                hooked = true;
                w.addComponentListener(new ComponentAdapter() {
                    public void componentResized(ComponentEvent ev) { fit(w.getWidth()); }
                });
                fit(w.getWidth());
            }
        });
    }

    private void fit(int windowWidth) {
        boolean c = windowWidth > 0 && windowWidth < COMPACT_BELOW;
        if (c == compact) return;
        compact = c;
        setToolTipText(compact ? TEXT + " — " + TIP : TIP);
        revalidate();
        repaint();
    }

    private Font font() {
        return Theme.smallBold != null ? Theme.smallBold : new Font(Font.DIALOG, Font.BOLD, 13);
    }

    public Dimension getPreferredSize() {
        if (compact) return new Dimension(34, 30);
        FontMetrics fm = getFontMetrics(font());
        return new Dimension(fm.stringWidth(TEXT) + 15 + 8 + 2 * 13, 30);   // טקסט + לב + רווח + שוליים
    }

    public Dimension getMinimumSize() { return getPreferredSize(); }

    public Dimension getMaximumSize() { return getPreferredSize(); }

    protected void paintComponent(Graphics g0) {
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        int w = getWidth(), h = getHeight();
        boolean lit = hover || down;
        int arc = Math.min(h, 16);
        // רקע: לבן במנוחה, ורוד עדין בריחוף; מסגרת דקה כמו שאר הפסים בסרגל
        g.setColor(down ? TINT_DOWN : lit ? TINT : Color.WHITE);
        g.fill(new RoundRectangle2D.Float(0.5f, 0.5f, w - 1, h - 1, arc, arc));
        g.setColor(lit ? LINE_HOVER : LINE);
        g.setStroke(new BasicStroke(1f));
        g.draw(new RoundRectangle2D.Float(0.5f, 0.5f, w - 1, h - 1, arc, arc));

        int s = 15;                                   // גודל הלב
        double cy = h / 2.0;
        if (compact) {
            heart(g, (w - s) / 2.0, cy - s / 2.0, s, lit);
        } else {
            Font f = font();
            g.setFont(f);
            FontMetrics fm = g.getFontMetrics();
            int tw = fm.stringWidth(TEXT), gap = 8, total = tw + gap + s, x0 = (w - total) / 2;
            heart(g, x0 + tw + gap, cy - s / 2.0, s, lit);   // מימין לשמאל: הלב בצד ימין של הטקסט
            g.setColor(lit ? INK_HOVER : INK);
            g.drawString(TEXT, x0, (int) Math.round(cy + (fm.getAscent() - fm.getDescent()) / 2.0));
        }
        g.dispose();
    }

    /** לב: קו דק במנוחה, מלא בריחוף. אותה צורה כמו בדפי ההדרכה ובאתר (viewBox 16x16) */
    private static void heart(Graphics2D g, double x, double y, int s, boolean filled) {
        Path2D.Double p = new Path2D.Double();
        p.moveTo(8, 14.2);
        p.curveTo(4.4, 11.4, 1.5, 8.9, 1.5, 5.6);
        p.curveTo(1.5, 3.5, 3.1, 2, 5, 2);
        p.curveTo(6.3, 2, 7.4, 2.7, 8, 3.8);
        p.curveTo(8.6, 2.7, 9.7, 2, 11, 2);
        p.curveTo(12.9, 2, 14.5, 3.5, 14.5, 5.6);
        p.curveTo(14.5, 8.9, 11.6, 11.4, 8, 14.2);
        p.closePath();
        AffineTransform t = AffineTransform.getTranslateInstance(x, y);
        t.scale(s / 16.0, s / 16.0);
        java.awt.Shape shape = t.createTransformedShape(p);
        g.setColor(filled ? HEART_HOVER : HEART);
        if (filled) g.fill(shape);
        g.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(shape);
    }

    /** פותח קישור בדפדפן (Desktop, ואם אין — הפקודה של מערכת ההפעלה) */
    static void open(String url) {
        try {
            if (java.awt.Desktop.isDesktopSupported() && java.awt.Desktop.getDesktop().isSupported(java.awt.Desktop.Action.BROWSE)) {
                java.awt.Desktop.getDesktop().browse(new URI(url));
                return;
            }
        } catch (Exception ignored) {
            // ננסה דרך מערכת ההפעלה
        }
        String os = System.getProperty("os.name", "").toLowerCase();
        String[] cmd = os.contains("win") ? new String[] { "rundll32", "url.dll,FileProtocolHandler", url }
                : os.contains("mac") ? new String[] { "open", url } : new String[] { "xdg-open", url };
        try {
            new ProcessBuilder(cmd).start();
        } catch (Exception ignored) {
            // אין דפדפן — לא קורס בגלל זה
        }
    }
}
