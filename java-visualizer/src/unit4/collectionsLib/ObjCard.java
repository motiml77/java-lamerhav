package unit4.collectionsLib;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;

import javax.swing.JComponent;

import unit4.collectionsLib.Model.Access;
import unit4.collectionsLib.Model.Obj;

/** טבלת המאפיינים של אובייקט (שם, ערך) — משותפת לחלון המבנים, לחלונית הצד ולכרטיס המלא */
final class ObjCard {
    private ObjCard() {}

    static final int ROW_H = 18;
    static final Font FIELD = Theme.font(Theme.family, Font.PLAIN, 13);
    static final Font FIELD_B = Theme.font(Theme.boldFamily, Theme.boldStyle, 13);
    private static final Color INK = new Color(0x1D2433), MUTED = new Color(0x6B7385);

    static Font mono() {
        return Theme.mono.deriveFont(12.5f);
    }

    /**
     * טבלת מאפיינים. המאפיין שנגעו בו (acc) מודגש בצבע הקריאה / השינוי.
     * cols = כמה עמודות (מאפיינים רבים — כמה עמודות, כדי שהטבלה תישאר נמוכה). g == null: רק מודדים.
     */
    static Dimension table(JComponent c, Graphics2D g, Obj o, int x, int y, Access acc, int cols) {
        int n = o.fields.size(), per = (n + cols - 1) / cols;
        Font mono = mono();
        int nameW = 0, valW = 0;
        FontMetrics mm = c.getFontMetrics(mono), fm = c.getFontMetrics(FIELD), fb = c.getFontMetrics(FIELD_B);
        for (String[] f : o.fields) {
            nameW = Math.max(nameW, mm.stringWidth(f[0]));
            valW = Math.max(valW, Math.min(170, Math.max(fm.stringWidth(f[1]), fb.stringWidth(f[1]))));
        }
        int colW = nameW + valW + 26, w = cols * colW + (cols - 1) * 10, h = Math.max(1, per) * ROW_H;
        if (g == null) return new Dimension(w, h);
        for (int i = 0; i < n; i++) {
            String[] f = o.fields.get(i);
            int cx = x + (i / per) * (colW + 10), cy = y + (i % per) * ROW_H;
            boolean hot = acc != null && f[0].equals(acc.field) && acc.obj != null && acc.obj.id == o.id;
            if (hot) {
                Color fill = StructPanel.color(acc.mark);
                g.setColor(fill);
                g.fillRoundRect(cx - 2, cy + 1, colW + 4, ROW_H - 2, 8, 8);
                g.setColor(darker(fill));
                g.setStroke(new BasicStroke(1.3f));
                g.drawRoundRect(cx - 2, cy + 1, colW + 4, ROW_H - 2, 8, 8);
            }
            g.setFont(mono);
            g.setColor(hot ? INK : MUTED);
            g.drawString(f[0], cx + 4, cy + ROW_H - 5);
            g.setFont(hot ? FIELD_B : FIELD);
            g.setColor(INK);
            String val = f[1];
            FontMetrics vm = g.getFontMetrics();
            while (val.length() > 4 && vm.stringWidth(val) > valW) val = val.substring(0, val.length() - 2) + "…";
            g.drawString(val, cx + 4 + nameW + 14, cy + ROW_H - 5);
        }
        return new Dimension(w, h);
    }

    static int columnsFor(int fields, int max) {
        return Math.min(max, fields > 6 ? 3 : fields > 3 ? 2 : 1);
    }

    static Color darker(Color c) {
        return new Color((int) (c.getRed() * 0.72), (int) (c.getGreen() * 0.72), (int) (c.getBlue() * 0.72));
    }
}
