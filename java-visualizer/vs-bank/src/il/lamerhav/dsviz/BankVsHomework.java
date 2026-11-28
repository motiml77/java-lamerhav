package il.lamerhav.dsviz;

import unit4.collectionsLib.Theme;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.ComponentOrientation;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Window;
import java.awt.event.HierarchyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPopupMenu;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

/**
 * הכפתור "שיעורי בית מהמורה" בראש חלון המאגר (BankVsWindow — Visual Studio, IntelliJ, VS Code): כשיש שאלות שעוד לא הוגשו הוא
 * זוהר — הילה רכה שפועמת לאט (נעצרת כשהחלון לא מוצג / ממוזער); מה שנשאר רק מחכה לאינטרנט (שמור במחשב) — אפור ושקט, עם
 * שעון; כשהכול הוגש — רגוע וירוק, בלי זוהר. הטקסטים והלוגיקה — BankHomework.
 * הרשימה (showList): שורה לכל שאלה — שם · נושא, ומשמאל ✓ הוגש / ציון / להגשה; לחיצה בוחרת את השאלה בחלון.
 */
final class BankVsHomework extends JButton {
    private static final Color AMBER = new Color(0xE8A100), AMBER_FILL = new Color(0xFFF4D6), AMBER_HOVER = new Color(0xFFEDBF), AMBER_INK = new Color(0x7A4E00);
    private static final long PERIOD_MS = 2600;   // פעימה אחת של ההילה

    private BankHomework hw;
    private boolean hover;
    private final Timer timer = new Timer(40, e -> tick());
    private Window watched;
    private final WindowAdapter iconify = new WindowAdapter() {
        @Override
        public void windowIconified(WindowEvent e) { timer.stop(); }

        @Override
        public void windowDeiconified(WindowEvent e) { animate(); }
    };

    BankVsHomework() {
        setContentAreaFilled(false);
        setBorderPainted(false);
        setFocusPainted(false);
        setOpaque(false);
        setBorder(BorderFactory.createEmptyBorder());
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        setAlignmentX(Component.RIGHT_ALIGNMENT);
        putClientProperty("dsviz.id", "hw-button");
        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) { hover = true; repaint(); }

            @Override
            public void mouseExited(MouseEvent e) { hover = false; repaint(); }
        });
        addHierarchyListener(e -> {
            if ((e.getChangeFlags() & (HierarchyEvent.SHOWING_CHANGED | HierarchyEvent.PARENT_CHANGED)) != 0) animate();
        });
    }

    /** ההילה סביב הכפתור (מרווח שקוף מכל צד) */
    static int halo() {
        return BankZoom.px(7);
    }

    /** מה מוצג: שיעורי הבית (לא null) */
    void show(BankHomework h) {
        hw = h;
        setText(h.label());   // לא מצויר (הציור שלנו) — לנגישות ולבדיקות
        setToolTipText(RtlDialog.rtl(glowing() && h.pending().size() == 1 ? "לחיצה — השאלה שעוד לא הוגשה" : "לחיצה — רשימת השאלות של שיעורי הבית"));
        int height = 2 * halo() + BankZoom.px(46);
        setPreferredSize(new Dimension(BankVsWindow.SIDEBAR - 28, height));
        setMaximumSize(new Dimension(Integer.MAX_VALUE, height));
        animate();
        repaint();
    }

    /** יש שאלות שעוד לא הוגשו — הכפתור זוהר */
    boolean glowing() {
        return hw != null && hw.glowing();
    }

    /** לבדיקות: ההילה פועמת עכשיו */
    boolean animating() {
        return timer.isRunning();
    }

    private void animate() {
        Window w = SwingUtilities.getWindowAncestor(this);
        if (w != watched) {
            if (watched != null) watched.removeWindowListener(iconify);
            watched = w;
            if (w != null) w.addWindowListener(iconify);
        }
        boolean iconified = w instanceof Frame && (((Frame) w).getExtendedState() & Frame.ICONIFIED) != 0;
        if (glowing() && isShowing() && !iconified) {
            if (!timer.isRunning()) timer.start();
        } else {
            timer.stop();
        }
    }

    private void tick() {
        if (!glowing() || !isShowing()) { timer.stop(); return; }
        repaint();
    }

    /** 0..1 — עוצמת ההילה ברגע הזה (פעימה רכה, קוסינוס) */
    private double pulse() {
        double t = (System.currentTimeMillis() % PERIOD_MS) / (double) PERIOD_MS;
        return 0.5 - 0.5 * Math.cos(2 * Math.PI * t);
    }

    @Override
    protected void paintComponent(Graphics g0) {
        if (hw == null) return;
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        int H = halo(), w = getWidth(), h = getHeight();
        int bx = H, by = H, bw = w - 2 * H, bh = h - 2 * H, arc = BankZoom.px(14);
        boolean glow = glowing(), wait = !glow && !hw.allDone();   // wait — נשאר רק מה שמחכה לאינטרנט
        if (glow) {
            // ההילה: כמה קווים מעוגלים שהולכים ומתרחבים ומתעמעמים; העוצמה פועמת
            double p = 0.35 + 0.65 * pulse();
            for (int k = H; k >= 1; k--) {
                float a = (float) (p * 0.32 * (1 - (k - 1) / (double) H) * (1 - (k - 1) / (double) H));
                g.setColor(new Color(AMBER.getRed(), AMBER.getGreen(), AMBER.getBlue(), Math.max(0, Math.min(255, Math.round(a * 255)))));
                g.setStroke(new BasicStroke(2f));
                g.draw(new RoundRectangle2D.Double(bx - k, by - k, bw + 2 * k - 1, bh + 2 * k - 1, arc + 2 * k, arc + 2 * k));
            }
        }
        g.setColor(glow ? (hover ? AMBER_HOVER : AMBER_FILL) : wait ? (hover ? new Color(0xEAEDF2) : new Color(0xF3F5F8)) : (hover ? new Color(0xEAF0E9) : new Color(0xF1F6F2)));
        g.fill(new RoundRectangle2D.Double(bx, by, bw - 1, bh - 1, arc, arc));
        g.setColor(glow ? AMBER : wait ? new Color(0xCDD5E1) : new Color(0xBFDCC8));
        g.setStroke(new BasicStroke(glow ? 1.5f : 1f));
        g.draw(new RoundRectangle2D.Double(bx + 0.5, by + 0.5, bw - 2, bh - 2, arc, arc));
        // הסמל (מימין): בית קטן בעיגול כתום / ✓ בעיגול ירוק
        int is = BankZoom.px(26), ix = bx + bw - BankZoom.px(10) - is, iy = by + (bh - is) / 2;
        g.setColor(glow ? AMBER : wait ? new Color(0x8A94A6) : RtlDialog.GREEN);
        g.fillOval(ix, iy, is, is);
        g.setColor(Color.WHITE);
        g.setStroke(new BasicStroke(BankZoom.px(2), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        int cx = ix + is / 2, cy = iy + is / 2, u = Math.max(1, is / 8);
        if (glow) {
            g.drawPolyline(new int[] { cx - 3 * u, cx, cx + 3 * u }, new int[] { cy - u / 2, cy - 3 * u - u / 2, cy - u / 2 }, 3);   // הגג
            g.drawPolyline(new int[] { cx - 2 * u, cx - 2 * u, cx + 2 * u, cx + 2 * u }, new int[] { cy - u, cy + 3 * u, cy + 3 * u, cy - u }, 4);
        } else if (wait) {   // שעון — מחכה לאינטרנט
            g.drawOval(cx - 3 * u, cy - 3 * u, 6 * u, 6 * u);
            g.drawPolyline(new int[] { cx, cx, cx + 2 * u }, new int[] { cy - 2 * u, cy, cy }, 3);
        } else {
            g.drawPolyline(new int[] { cx - 3 * u, cx - u, cx + 3 * u }, new int[] { cy, cy + 2 * u, cy - 2 * u }, 3);
        }
        // החץ (משמאל) — "לשם"
        int ax = bx + BankZoom.px(14), ay = by + bh / 2;
        g.setColor(glow ? AMBER_INK : RtlDialog.MUTED);
        g.setStroke(new BasicStroke(BankZoom.px(2), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.drawPolyline(new int[] { ax + BankZoom.px(4), ax, ax + BankZoom.px(4) }, new int[] { ay - BankZoom.px(5), ay, ay + BankZoom.px(5) }, 3);
        // שתי שורות טקסט, מיושרות לימין (ליד הסמל)
        int right = ix - BankZoom.px(10), left = ax + BankZoom.px(14);
        Font f1 = Theme.z(RtlDialog.bold().deriveFont(13.5f)), f2 = Theme.z(RtlDialog.small().deriveFont(12f));
        g.setFont(f1);
        FontMetrics m1 = g.getFontMetrics();
        g.setFont(f2);
        FontMetrics m2 = g.getFontMetrics();
        int gap = BankZoom.px(3), total = m1.getAscent() + m1.getDescent() + gap + m2.getAscent() + m2.getDescent();
        int y1 = by + (bh - total) / 2 + m1.getAscent(), y2 = y1 + m1.getDescent() + gap + m2.getAscent();
        g.setFont(f1);
        g.setColor(glow || wait ? RtlDialog.INK : new Color(0x1F6B3E));
        String t1 = fit(hw.headline(), m1, right - left);
        g.drawString(RtlDialog.rtl(t1), right - m1.stringWidth(t1), y1);
        g.setFont(f2);
        String d = hw.detail();
        g.setColor(!glow ? RtlDialog.MUTED : d.contains("עבר המועד") ? RtlDialog.RED : AMBER_INK);
        String t2 = fit(d, m2, right - left);
        g.drawString(RtlDialog.rtl(t2), right - m2.stringWidth(t2), y2);
        g.dispose();
    }

    /** מקצר עם "…" אם לא נכנס */
    static String fit(String s, FontMetrics fm, int max) {
        if (fm.stringWidth(s) <= max) return s;
        String t = s;
        while (t.length() > 3 && fm.stringWidth(t + "…") > max) t = t.substring(0, t.length() - 1);
        return t.trim() + "…";
    }

    // ------------------------------------------------------------------ הרשימה

    /** לבדיקות: מה מוצג ברשימה האחרונה ("SHEET כותרת | מועד", "ITEM מזהה שם · נושא · מצב"), והרשימה עצמה */
    static final List<String> listShown = new ArrayList<String>();
    static JPopupMenu list;

    /** הרשימה מתחת לכפתור: לכל שיעורי בית — הכותרת והמועד, ומתחת שורה לכל שאלה; בחירה — select(מזהה השאלה) */
    static void showList(final Component anchor, BankHomework hw, final Consumer<String> select) {
        final JPopupMenu p = new JPopupMenu();
        p.setComponentOrientation(ComponentOrientation.RIGHT_TO_LEFT);
        p.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(RtlDialog.LINE), BorderFactory.createEmptyBorder(6, 0, 6, 0)));
        p.setBackground(Color.WHITE);
        listShown.clear();
        int width = Math.max(anchor.getWidth() + BankZoom.px(60), BankZoom.px(hw.waiting().isEmpty() ? 380 : 540));   // "נשמר במחשב — …" ארוך יותר
        boolean first = true;
        for (BankHomework.Sheet s : hw.sheets) {
            String due = hw.dueLine(s);
            JLabel head = RtlDialog.label(s.title + (due.isEmpty() ? "" : "  ·  " + due), Theme.z(RtlDialog.bold().deriveFont(12.5f)),
                    due.equals("עבר המועד") ? RtlDialog.RED : RtlDialog.INK_2);
            head.setBorder(BorderFactory.createEmptyBorder(first ? 2 : 10, 14, 4, 14));
            head.setHorizontalAlignment(JLabel.RIGHT);
            head.setAlignmentX(Component.RIGHT_ALIGNMENT);
            p.add(head);
            listShown.add("SHEET " + s.title + " | " + due);
            first = false;
            for (final BankHomework.Item i : s.items) {
                Row r = new Row(i, width, () -> {
                    p.setVisible(false);
                    select.accept(i.bankId);
                });
                p.add(r);
                listShown.add("ITEM " + i.bankId + " " + i.title + " · " + i.topic + " · " + BankHomework.status(i));
            }
        }
        RtlDialog.rtl(p);
        list = p;
        p.show(anchor, anchor.getWidth() - width - halo(), anchor.getHeight() - halo() + BankZoom.px(2));
    }

    /** שורה ברשימה: מימין — השם (מודגש אם עוד לא הוגש) והנושא; משמאל — תגית המצב */
    static final class Row extends JComponent {
        final BankHomework.Item item;
        final Runnable onPick;
        boolean hover;

        Row(BankHomework.Item item, int width, Runnable onPick) {
            this.item = item;
            this.onPick = onPick;
            putClientProperty("dsviz.id", "hw-item-" + item.bankId);
            setPreferredSize(new Dimension(width, BankZoom.px(32)));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setToolTipText(RtlDialog.rtl("לחיצה — השאלה בחלון המאגר"));
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) { hover = true; repaint(); }

                @Override
                public void mouseExited(MouseEvent e) { hover = false; repaint(); }

                @Override
                public void mouseReleased(MouseEvent e) { pick(); }
            });
        }

        /** כמו לחיצה (גם לבדיקות) */
        void pick() {
            onPick.run();
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            int w = getWidth(), h = getHeight(), cy = h / 2;
            if (hover) {
                g.setColor(new Color(0xEEF3FD));
                g.fillRoundRect(6, 1, w - 12, h - 2, 8, 8);
            }
            // תגית המצב (משמאל)
            String st = BankHomework.status(item);
            Color fg = item.waiting ? new Color(0x4A5568) : !item.done ? AMBER_INK : item.grade != null ? new Color(0x3F3A8F) : new Color(0x1F7A44);
            Color bg = item.waiting ? new Color(0xEEF1F5) : !item.done ? AMBER_FILL : item.grade != null ? new Color(0xECEBFB) : new Color(0xE3F4EA);
            Font sf = Theme.z(RtlDialog.bold().deriveFont(11.5f));
            g.setFont(sf);
            FontMetrics fm = g.getFontMetrics();
            int pw = fm.stringWidth(st) + BankZoom.px(16), ph = fm.getAscent() + fm.getDescent() + BankZoom.px(2), px = BankZoom.px(14);
            g.setColor(bg);
            g.fillRoundRect(px, cy - ph / 2, pw, ph, ph, ph);
            g.setColor(fg);
            g.drawString(RtlDialog.rtl(st), px + BankZoom.px(8), cy - ph / 2 + BankZoom.px(1) + fm.getAscent());
            // השם והנושא (מימין)
            int right = w - BankZoom.px(16), left = px + pw + BankZoom.px(12);
            Font tf = Theme.z((item.done || item.waiting ? RtlDialog.ui() : RtlDialog.bold()).deriveFont(13f)), mf = Theme.z(RtlDialog.small().deriveFont(12f));
            g.setFont(tf);
            FontMetrics tm = g.getFontMetrics();
            g.setFont(mf);
            FontMetrics mm = g.getFontMetrics();
            String topic = item.topic.isEmpty() ? "" : "  ·  " + item.topic;
            int topicW = topic.isEmpty() ? 0 : Math.min(mm.stringWidth(topic), (right - left) / 3);
            String title = fit(item.title, tm, right - left - topicW);
            int base = cy + tm.getAscent() / 2 - 2;
            g.setFont(tf);
            g.setColor(RtlDialog.INK);
            g.drawString(RtlDialog.rtl(title), right - tm.stringWidth(title), base);
            if (!topic.isEmpty()) {
                g.setFont(mf);
                g.setColor(RtlDialog.MUTED);
                String tp = fit(topic, mm, topicW);
                g.drawString(RtlDialog.rtl(tp), right - tm.stringWidth(title) - mm.stringWidth(tp), base);
            }
            g.dispose();
        }
    }
}
