package il.lamerhav.dsviz;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.List;

import javax.swing.JComponent;

/**
 * תרשים המחלקות של שאלה בהורשה (ציור "classes") ב-Visual Studio וב-VS Code (Swing): BankClasses פורס ומצייר, כאן רק העט —
 * גופן קוד (כמו הקוד בחלון ההרצה), צבעים, קווים ומשולשים. משמאל לימין (קוד), בגודל הטבעי; BankZoom.scaled מקטין רק אם צריך.
 */
final class BankClassFigure extends JComponent {
    private final List<BankClasses.Cls> classes;
    private final Font plain, bold, italic;
    private final Dimension size;

    BankClassFigure(List<BankClasses.Cls> classes) {
        this.classes = classes;
        Font mono = RtlDialog.mono(12.5f);
        plain = mono;
        bold = mono.deriveFont(Font.BOLD);
        italic = mono.deriveFont(Font.ITALIC);
        Graphics2D g = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB).createGraphics();
        int[] wh = BankClasses.layout(classes, new SwingPen(g));
        g.dispose();
        size = new Dimension(wh[0], wh[1]);
        setOpaque(false);
        setPreferredSize(size);
        setMinimumSize(size);
        setMaximumSize(size);
    }

    @Override
    protected void paintComponent(Graphics g0) {
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        BankClasses.paint(classes, new SwingPen(g));
        g.dispose();
    }

    /** העט של BankClasses ב-Swing */
    private final class SwingPen implements BankClasses.Pen {
        private final Graphics2D g;

        SwingPen(Graphics2D g) {
            this.g = g;
        }

        private FontMetrics fm(boolean b) {
            return g.getFontMetrics(b ? bold : plain);
        }

        @Override
        public int width(String s, boolean b) {
            return fm(b).stringWidth(s);
        }

        @Override
        public int lineHeight() {
            return fm(false).getHeight() + 1;
        }

        @Override
        public void box(int x, int y, int w, int h, int fill, int stroke) {
            g.setColor(new Color(fill));
            g.fillRoundRect(x, y, w - 1, h - 1, 6, 6);
            g.setColor(new Color(stroke));
            g.setStroke(new BasicStroke(1.4f));
            g.drawRoundRect(x, y, w - 1, h - 1, 6, 6);
            g.setStroke(new BasicStroke(1f));
        }

        @Override
        public void line(int x1, int y1, int x2, int y2, int color) {
            g.setColor(new Color(color));
            g.drawLine(x1, y1, x2, y2);
        }

        @Override
        public void triangle(int[] xs, int[] ys, int fill, int stroke) {
            g.setColor(new Color(fill));
            g.fillPolygon(xs, ys, xs.length);
            g.setColor(new Color(stroke));
            g.drawPolygon(xs, ys, xs.length);
        }

        @Override
        public void text(String s, int x, int y, boolean b, boolean it, int color) {
            g.setFont(b ? bold : it ? italic : plain);
            g.setColor(new Color(color));
            g.drawString(s, x, y + g.getFontMetrics().getAscent());
        }
    }
}
