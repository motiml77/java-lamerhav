package il.lamerhav.dsviz;

import java.awt.AWTEvent;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseWheelEvent;

import javax.swing.AbstractAction;
import javax.swing.InputMap;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

import unit4.collectionsLib.Theme;

/**
 * הגדלה / הקטנה של חלונות המאגר (החלון הראשי והחלון הצף "השאלה"): Ctrl + / Ctrl − / Ctrl 0, Ctrl + גלגלת, וכפתורים.
 * הגופנים של כל החלון גדלים, ושורות העץ, הרוחב של סרגל הצד והציורים (מחסנית, עץ...) גדלים איתם — ואז החלון נבנה מחדש,
 * כך ששום דבר לא עולה על דבר אחר (טקסט ארוך יורד שורה).
 */
final class BankZoom {
    private BankZoom() {}

    private static Font baseUi, baseBold, baseSmall, baseBig, baseMono;
    private static final java.util.List<Runnable> listeners = new java.util.ArrayList<Runnable>();
    private static boolean wheel;

    /** אחרי Theme.install + הגופנים הקומפקטיים: שומרים את הגודל הבסיסי, וטוענים את הגודל שנבחר בפעם הקודמת */
    static void init() {
        baseUi = Theme.ui; baseBold = Theme.uiBold; baseSmall = Theme.small; baseBig = Theme.big; baseMono = Theme.mono;
        apply(Theme.savedZoom("bank"), false);
    }

    static double zoom() { return Theme.zoom; }

    /** מספר (גובה / רוחב בפיקסלים) בגודל הנוכחי */
    static int px(int base) { return (int) Math.round(base * Theme.zoom); }

    static void step(int dir) { set(Theme.stepZoom(Theme.zoom, dir)); }

    static void set(double z) {
        if (Math.abs(z - Theme.zoom) < 1e-6) return;
        apply(z, true);
        for (Runnable r : new java.util.ArrayList<Runnable>(listeners)) r.run();
    }

    private static void apply(double z, boolean save) {
        Theme.zoom = z;
        if (save) Theme.saveZoom("bank", z);
        BankVsWindow.SIDEBAR = px(330); BankVsWindow.TOPIC_H = px(40); BankVsWindow.Q_H = px(30); BankVsWindow.PREVIEW_W = px(640);   // לכל היותר — בפועל לפי רוחב החלון
        if (baseUi == null) return;
        Theme.ui = sized(baseUi); Theme.uiBold = sized(baseBold); Theme.small = sized(baseSmall); Theme.big = sized(baseBig);
        if (baseMono != null) Theme.mono = sized(baseMono);
        javax.swing.plaf.FontUIResource ui = new javax.swing.plaf.FontUIResource(Theme.ui);
        UIManager.put("defaultFont", ui);
        for (String k : new String[] { "Label.font", "Button.font", "ToggleButton.font", "CheckBox.font", "TextField.font", "ComboBox.font",
                "List.font", "OptionPane.messageFont", "OptionPane.buttonFont" })
            UIManager.put(k, ui);
    }

    private static Font sized(Font f) { return f.deriveFont((float) (f.getSize2D() * Theme.zoom)); }

    /** החלון נבנה מחדש אחרי שינוי גודל */
    static void listen(Runnable r) { listeners.add(r); }

    static void unlisten(Runnable r) { listeners.remove(r); }

    /** מקשי הקיצור על החלון, ו-Ctrl + גלגלת (פעם אחת לכל התהליך) */
    static void install(final JFrame f) {
        JComponent root = f.getRootPane();
        InputMap im = root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        for (int m : new int[] { InputEvent.CTRL_DOWN_MASK, InputEvent.META_DOWN_MASK }) {
            for (int k : new int[] { KeyEvent.VK_EQUALS, KeyEvent.VK_PLUS, KeyEvent.VK_ADD }) {
                im.put(KeyStroke.getKeyStroke(k, m), "dsvizZoomIn");
                im.put(KeyStroke.getKeyStroke(k, m | InputEvent.SHIFT_DOWN_MASK), "dsvizZoomIn");
            }
            for (int k : new int[] { KeyEvent.VK_MINUS, KeyEvent.VK_SUBTRACT }) im.put(KeyStroke.getKeyStroke(k, m), "dsvizZoomOut");
            for (int k : new int[] { KeyEvent.VK_0, KeyEvent.VK_NUMPAD0 }) im.put(KeyStroke.getKeyStroke(k, m), "dsvizZoomReset");
        }
        root.getActionMap().put("dsvizZoomIn", new AbstractAction() { public void actionPerformed(java.awt.event.ActionEvent e) { step(1); } });
        root.getActionMap().put("dsvizZoomOut", new AbstractAction() { public void actionPerformed(java.awt.event.ActionEvent e) { step(-1); } });
        root.getActionMap().put("dsvizZoomReset", new AbstractAction() { public void actionPerformed(java.awt.event.ActionEvent e) { set(1); } });
        if (wheel) return;
        wheel = true;
        Toolkit.getDefaultToolkit().addAWTEventListener(new java.awt.event.AWTEventListener() {
            private long last;
            public void eventDispatched(AWTEvent ev) {
                if (!(ev instanceof MouseWheelEvent)) return;
                MouseWheelEvent w = (MouseWheelEvent) ev;
                if (!w.isControlDown() || w.getWheelRotation() == 0) return;
                java.awt.Window win = SwingUtilities.getWindowAncestor(w.getComponent());
                if (win != BankVsFloat.current && (BankVsWindow.current == null || win != BankVsWindow.current.frame)) return;
                w.consume();
                long now = System.currentTimeMillis();
                if (now - last < 150) return;
                last = now;
                final int dir = w.getWheelRotation() < 0 ? 1 : -1;
                SwingUtilities.invokeLater(() -> step(dir));
            }
        }, AWTEvent.MOUSE_WHEEL_EVENT_MASK);
    }

    /** "− 100% +" — קטן, לפינה של החלון */
    static JComponent controls() {
        JPanel p = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEADING, 2, 0));
        p.setOpaque(false);
        JButton in = small("+", "הגדלה  (Ctrl +)"), out = small("−", "הקטנה  (Ctrl −)");
        in.addActionListener(e -> step(1));
        out.addActionListener(e -> step(-1));
        JLabel l = new JLabel(Math.round(Theme.zoom * 100) + "%", SwingConstants.CENTER);
        l.setFont(Theme.small);
        l.setForeground(RtlDialog.MUTED);
        l.setToolTipText("גודל התצוגה — לחיצה מחזירה ל-100%  (Ctrl 0)");
        l.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
        l.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override public void mouseClicked(java.awt.event.MouseEvent e) { set(1); }
        });
        p.add(in);
        p.add(l);
        p.add(out);
        p.putClientProperty("dsviz.id", "zoom");
        return p;
    }

    private static JButton small(String text, String tip) {
        JButton b = new JButton(text);
        b.setFont(Theme.uiBold);
        b.setFocusable(false);
        b.setToolTipText(tip);
        b.putClientProperty("JButton.buttonType", "toolBarButton");
        b.putClientProperty("FlatLaf.style", "margin: 1,8,1,8; arc: 8");
        return b;
    }

    /**
     * ציור (מחסנית, עץ, מערך...) בגודל הנוכחי — כיחידה אחת, בלי לשנות את הציור עצמו. ציור שרחב מ-maxWidth (עץ גדול, שרשרת ארוכה,
     * חלון צר) — קטן עוד, באותו יחס, עד שהוא נכנס: רואים את כולו, בלי גלילה הצידה. ציור שנכנס — בדיוק כמו קודם
     */
    static JComponent scaled(final JComponent d, int maxWidth) {
        final Dimension p = d.getPreferredSize();
        final double z = p.width * Theme.zoom > maxWidth ? maxWidth / (double) Math.max(1, p.width) : Theme.zoom;
        if (Math.abs(z - 1) < 1e-6) return d;
        JComponent w = new JComponent() {
            @Override
            protected void paintComponent(Graphics g0) {
                Graphics2D g = (Graphics2D) g0.create();
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                g.scale(z, z);
                d.setSize(p);
                d.paint(g);
                g.dispose();
            }
        };
        Dimension s = new Dimension(Math.min(maxWidth, (int) Math.ceil(p.width * z)), (int) Math.ceil(p.height * z));
        w.setPreferredSize(s);
        w.setMinimumSize(s);
        w.setMaximumSize(s);
        w.setToolTipText(d.getToolTipText());
        return w;
    }
}
