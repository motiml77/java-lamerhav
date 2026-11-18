package il.lamerhav.dsviz;

import java.util.List;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.FontData;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Transform;
import org.eclipse.swt.widgets.Canvas;
import org.eclipse.swt.widgets.Composite;

/**
 * תרשים המחלקות של שאלה בהורשה (ציור "classes") ב-Eclipse (SWT): BankClasses פורס ומצייר, כאן רק העט — גופן הקוד של העורך,
 * צבעים, קווים ומשולשים. בגודל הטבעי; כשהשורה צרה ממנו (BankQuestionPanel.FigureRow) — מוקטן לרוחב שניתן לו, באותו יחס.
 */
final class BankClassCanvas extends Canvas {
    private final List<BankClasses.Cls> classes;
    private final Font plain, bold, italic;
    private final int naturalW, naturalH;

    BankClassCanvas(Composite parent, List<BankClasses.Cls> classes) {
        super(parent, SWT.DOUBLE_BUFFERED | SWT.LEFT_TO_RIGHT);
        this.classes = classes;
        plain = Look.mono(getDisplay(), 0);
        FontData fd = plain.getFontData()[0];
        bold = new Font(getDisplay(), fd.getName(), fd.getHeight(), SWT.BOLD);
        italic = new Font(getDisplay(), fd.getName(), fd.getHeight(), SWT.ITALIC);
        GC gc = new GC(this);
        int[] wh = BankClasses.layout(classes, new SwtPen(gc));
        gc.dispose();
        naturalW = wh[0];
        naturalH = wh[1];
        addDisposeListener(e -> {
            bold.dispose();
            italic.dispose();
        });
        addPaintListener(e -> {
            Point s = getSize();
            float k = Math.min(1f, s.x / (float) Math.max(1, naturalW));
            e.gc.setAntialias(SWT.ON);
            e.gc.setTextAntialias(SWT.ON);
            Transform t = null;
            if (k < 0.999f) {
                t = new Transform(getDisplay());
                t.scale(k, k);
                e.gc.setTransform(t);
            }
            BankClasses.paint(classes, new SwtPen(e.gc));
            if (t != null) {
                e.gc.setTransform(null);
                t.dispose();
            }
        });
    }

    /** הרוחב הטבעי (בלי הקטנה) — ל-FigureRow */
    int naturalWidth() {
        return naturalW;
    }

    @Override
    public Point computeSize(int wHint, int hHint, boolean changed) {
        int w = wHint == SWT.DEFAULT ? naturalW : Math.min(naturalW, Math.max(1, wHint));
        return new Point(w, (int) Math.ceil(naturalH * (w / (double) naturalW)));
    }

    /** העט של BankClasses ב-SWT */
    private final class SwtPen implements BankClasses.Pen {
        private final GC gc;

        SwtPen(GC gc) {
            this.gc = gc;
        }

        private Color color(int rgb) {
            return Look.color(getDisplay(), rgb);
        }

        @Override
        public int width(String s, boolean b) {
            gc.setFont(b ? bold : plain);
            return gc.textExtent(s).x;
        }

        @Override
        public int lineHeight() {
            gc.setFont(plain);
            return gc.getFontMetrics().getHeight() + 1;
        }

        @Override
        public void box(int x, int y, int w, int h, int fill, int stroke) {
            gc.setBackground(color(fill));
            gc.fillRoundRectangle(x, y, w - 1, h - 1, 6, 6);
            gc.setForeground(color(stroke));
            gc.setLineWidth(1);
            gc.drawRoundRectangle(x, y, w - 1, h - 1, 6, 6);
        }

        @Override
        public void line(int x1, int y1, int x2, int y2, int c) {
            gc.setForeground(color(c));
            gc.drawLine(x1, y1, x2, y2);
        }

        @Override
        public void triangle(int[] xs, int[] ys, int fill, int stroke) {
            int[] p = new int[xs.length * 2];
            for (int i = 0; i < xs.length; i++) {
                p[2 * i] = xs[i];
                p[2 * i + 1] = ys[i];
            }
            gc.setBackground(color(fill));
            gc.fillPolygon(p);
            gc.setForeground(color(stroke));
            gc.drawPolygon(p);
        }

        @Override
        public void text(String s, int x, int y, boolean b, boolean it, int c) {
            gc.setFont(b ? bold : it ? italic : plain);
            gc.setForeground(color(c));
            gc.drawText(s, x, y, true);
        }
    }
}
