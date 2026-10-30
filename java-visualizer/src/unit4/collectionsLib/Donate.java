package unit4.collectionsLib;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Path2D;
import java.net.URI;

import javax.swing.JComponent;

/**
 * כפתור "תרום לנו" קטן בצבע זהב — בתחתית חלון ההרצה. פותח את דף התרומה ב-PayPal בדפדפן.
 */
final class Donate extends JComponent {
    private static final String TEXT = "תרום לנו";
    private static final Color TOP = new Color(0xF8DE8A), BOTTOM = new Color(0xDDB042);
    private static final Color TOP_HOVER = new Color(0xFBE7A4), BOTTOM_HOVER = new Color(0xE6BC4E);
    private static final Color EDGE = new Color(0xB68A1E), INK = new Color(0x4A3405), HEART = new Color(0xB4372C);
    private boolean hover, down;

    Donate() {
        setOpaque(false);
        setFocusable(false);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        setToolTipText("התוכנה חינמית — אם היא עוזרת לך, אפשר לתמוך בה ב-PayPal");
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
    }

    private Font font() {
        return Theme.smallBold != null ? Theme.smallBold.deriveFont(11.5f) : new Font(Font.DIALOG, Font.BOLD, 11);
    }

    public Dimension getPreferredSize() {
        FontMetrics fm = getFontMetrics(font());
        return new Dimension(fm.stringWidth(TEXT) + 40, 24);
    }

    public Dimension getMaximumSize() { return getPreferredSize(); }

    protected void paintComponent(Graphics g0) {
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        int w = getWidth() - 1, h = getHeight() - 3, y = down ? 2 : 1;
        // צל דק מתחת — הכפתור "בולט" קצת מהרקע
        g.setColor(new Color(0x80, 0x5C, 0x0A, down ? 0 : 40));
        g.fillRoundRect(0, y + 1, w, h, h, h);
        g.setPaint(new GradientPaint(0, y, hover ? TOP_HOVER : TOP, 0, y + h, hover ? BOTTOM_HOVER : BOTTOM));
        g.fillRoundRect(0, y, w, h, h, h);
        // ברק עליון
        g.setColor(new Color(255, 255, 255, 90));
        g.drawLine(h / 2, y + 1, w - h / 2, y + 1);
        g.setColor(EDGE);
        g.setStroke(new BasicStroke(1f));
        g.drawRoundRect(0, y, w, h, h, h);
        // לב קטן + הטקסט (מימין לשמאל: הלב בצד ימין)
        Font f = font();
        g.setFont(f);
        FontMetrics fm = g.getFontMetrics();
        int tw = fm.stringWidth(TEXT), total = tw + 15, x0 = (w - total) / 2;
        int cy = y + h / 2;
        heart(g, x0 + tw + 5, cy - 4, 9);
        g.setColor(INK);
        g.drawString(TEXT, x0, cy + (fm.getAscent() - fm.getDescent()) / 2);
        g.dispose();
    }

    private static void heart(Graphics2D g, int x, int y, int s) {
        Path2D p = new Path2D.Double();
        p.moveTo(x + s / 2.0, y + s);
        p.curveTo(x - s * 0.15, y + s * 0.55, x, y - s * 0.1, x + s / 2.0, y + s * 0.25);
        p.curveTo(x + s, y - s * 0.1, x + s * 1.15, y + s * 0.55, x + s / 2.0, y + s);
        g.setColor(HEART);
        g.fill(p);
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
