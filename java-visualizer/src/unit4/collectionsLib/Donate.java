package unit4.collectionsLib;

import java.awt.Cursor;
import java.awt.Dimension;
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
import java.awt.image.BufferedImage;
import java.awt.image.RescaleOp;
import java.io.InputStream;
import java.net.URI;

import javax.imageio.ImageIO;
import javax.swing.JComponent;
import javax.swing.SwingUtilities;

/**
 * "תמכו בפרויקט" — התג בקצה סרגל השליטה: תג Java בהרצה של Java, ותג C# בהרצה של C# (Syntax.csharp — נקבע כשנטען קובץ הצעדים של C#).
 * התמונות נשמרות בגובה 96 (פי 3 מהתצוגה) כדי שיהיו חדות גם במסך ברזולוציה גבוהה. בריחוף — התג מואר קצת; בלחיצה — יורד פיקסל.
 * בחלון צר — התג קטן יותר. פותח את דף התרומה ב-PayPal בדפדפן.
 */
final class Donate extends JComponent {
    static final String TEXT = "תמכו בפרויקט";
    static final String TIP = "התוכנה חינמית — אם היא עוזרת לך, אפשר לתמוך בה ב-PayPal";
    /** מתחת לרוחב החלון הזה התג קטן יותר */
    private static final int COMPACT_BELOW = 1100;
    private static final int HEIGHT = 34, COMPACT_HEIGHT = 26;

    private static BufferedImage javaBadge, csBadge, javaLit, csLit;   // ...Lit — אותו תג מואר קצת (לריחוף)
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
        // בחלון צר — תג קטן יותר. מאזינים לשינוי גודל החלון (פעם אחת, כשהכפתור נכנס לחלון)
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
        revalidate();
        repaint();
    }

    /** התג של השפה שרצה עכשיו (או null אם התמונה לא נמצאה ב-jar); lit — הגרסה המוארת לריחוף */
    private static BufferedImage badge(boolean lit) {
        if (Syntax.csharp) {
            if (csBadge == null) { csBadge = load("donate-cs.png"); csLit = brighter(csBadge); }
            return lit ? csLit : csBadge;
        }
        if (javaBadge == null) { javaBadge = load("donate-java.png"); javaLit = brighter(javaBadge); }
        return lit ? javaLit : javaBadge;
    }

    /** עותק מואר ב-10% (השקיפות נשמרת) */
    private static BufferedImage brighter(BufferedImage img) {
        if (img == null) return null;
        BufferedImage argb = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_INT_ARGB);
        argb.getGraphics().drawImage(img, 0, 0, null);
        return new RescaleOp(new float[] { 1.1f, 1.1f, 1.1f, 1f }, new float[] { 8, 8, 8, 0 }, null).filter(argb, null);
    }

    private static BufferedImage load(String file) {
        InputStream in = Donate.class.getResourceAsStream("images/" + file);
        if (in == null) return null;
        try {
            return ImageIO.read(in);
        } catch (Exception e) {
            return null;
        } finally {
            try { in.close(); } catch (Exception ignored) {}
        }
    }

    public Dimension getPreferredSize() {
        BufferedImage img = badge(false);
        int h = compact ? COMPACT_HEIGHT : HEIGHT;
        int w = img == null ? 150 : (int) Math.round(img.getWidth() * (h / (double) img.getHeight()));
        return new Dimension(w, h + 2);   // +2 — מקום לירידה בלחיצה
    }

    public Dimension getMinimumSize() { return getPreferredSize(); }

    public Dimension getMaximumSize() { return getPreferredSize(); }

    protected void paintComponent(Graphics g0) {
        BufferedImage img = badge(hover && !down);
        if (img == null) return;
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int h = getHeight() - 2, w = getWidth();
        int y = down ? 2 : hover ? 0 : 1;
        g.drawImage(img, 0, y, w, h, null);
        g.dispose();
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
