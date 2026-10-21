package unit4.collectionsLib;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

import javax.swing.JPanel;

import unit4.collectionsLib.Model.Access;
import unit4.collectionsLib.Model.Step;

/**
 * שורת "פרטי האובייקט" מתחת לתצוגה הוויזואלית: מופיעה רק בצעד שקורא / משנה מאפיין של אובייקט (s.getName()),
 * ומראה את כל המאפיינים של האובייקט, כשהמאפיין שנגעו בו מודגש. במקום קבוע — לא מכסה מבנים ולא מזיז אותם.
 */
final class ObjDock extends JPanel {
    private Access access;

    ObjDock() {
        setOpaque(true);
        setBackground(new Color(0xFBFCFE));
        setVisible(false);
    }

    void setStep(Step s) {
        access = s == null ? null : s.access;
        boolean show = access != null && access.obj != null;
        if (isVisible() != show) setVisible(show);
        revalidate();
        repaint();
    }

    private int cols() {
        int avail = getParent() != null && getParent().getWidth() > 100 ? getParent().getWidth() - 40 : 560;
        for (int c = 1; c <= 3; c++) {
            if (c < ObjCard.columnsFor(access.obj.fields.size(), 3)) continue;
            if (ObjCard.table(this, null, access.obj, 0, 0, null, c).width <= avail) return c;
        }
        return 3;
    }

    @Override
    public Dimension getPreferredSize() {
        if (access == null || access.obj == null) return new Dimension(10, 0);
        Dimension t = ObjCard.table(this, null, access.obj, 0, 0, null, cols());
        return new Dimension(t.width + 40, 32 + t.height + 14);
    }

    @Override
    protected void paintComponent(Graphics g0) {
        super.paintComponent(g0);
        if (access == null || access.obj == null) return;
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        Color accent = ObjCard.darker(StructPanel.color(access.mark));
        g.setColor(accent);
        g.setStroke(new BasicStroke(2f));
        g.drawLine(0, 0, getWidth(), 0);
        // כותרת: סוג ושם, ומה עשינו (getAverage() ...)
        Font tf = Theme.font(Theme.boldFamily, Theme.boldStyle, 14);
        g.setFont(tf);
        g.setColor(new Color(0x1D2433));
        String head = access.obj.type + "  ·  " + access.obj.title;
        g.drawString(head, 16, 22);
        g.setFont(Theme.mono.deriveFont(Font.BOLD, 12f));
        FontMetrics fm = g.getFontMetrics();
        int cw = fm.stringWidth(access.text) + 14, x = 16 + getFontMetrics(tf).stringWidth(head) + 12;
        g.setColor(StructPanel.color(access.mark));
        g.fillRoundRect(x, 8, cw, 19, 19, 19);
        g.setColor(new Color(0x1D2433));
        g.drawString(access.text, x + 7, 22);
        ObjCard.table(this, g, access.obj, 16, 32, access, cols());
        g.dispose();
    }
}
