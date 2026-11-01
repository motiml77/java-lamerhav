package il.lamerhav.dsviz.intellij;

import java.awt.BorderLayout;
import java.awt.ComponentOrientation;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.util.ArrayList;
import java.util.List;

import javax.swing.ButtonGroup;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JToggleButton;
import javax.swing.SwingConstants;

import com.intellij.ui.JBColor;
import com.intellij.ui.RoundedLineBorder;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;

/** אבני בניין לחלונות של DSViz ב-IntelliJ: עמודה עם כותרות, "צ'יפים" (כפתורי בחירה), כרטיס, מימין לשמאל */
final class Forms {
    private Forms() {}

    /** עמודה: שורות אחת מתחת לשנייה, כל שורה ברוחב מלא, צמודה להתחלה (מימין בעברית) */
    static final class Column {
        final JPanel panel = new JPanel(new GridBagLayout());
        private int row;

        JBLabel section(String title) {
            JBLabel l = new JBLabel(title);
            l.setFont(l.getFont().deriveFont(Font.BOLD));
            add(l, row == 0 ? 0 : 10);
            return l;
        }

        <T extends JComponent> T add(T c) {
            return add(c, 4);
        }

        <T extends JComponent> T add(T c, int top) {
            GridBagConstraints gc = new GridBagConstraints();
            gc.gridx = 0;
            gc.gridy = row++;
            gc.weightx = 1;
            gc.fill = GridBagConstraints.HORIZONTAL;
            gc.anchor = GridBagConstraints.FIRST_LINE_START;
            gc.insets = JBUI.insets(top, 0, 0, 0);
            panel.add(c, gc);
            return c;
        }

        /** רווח בסוף — כדי שהשורות יישארו למעלה */
        JPanel done() {
            GridBagConstraints gc = new GridBagConstraints();
            gc.gridx = 0;
            gc.gridy = row++;
            gc.weighty = 1;
            panel.add(new JPanel(), gc);
            return panel;
        }
    }

    /** קבוצה של כפתורי בחירה — אחד נבחר בכל פעם */
    static final class Chips {
        final JPanel panel;
        final List<JToggleButton> buttons = new ArrayList<JToggleButton>();
        private final ButtonGroup group = new ButtonGroup();

        Chips(int cols) {
            panel = new JPanel(new GridLayout(0, cols, JBUI.scale(6), JBUI.scale(6)));
        }

        JToggleButton add(String text, String id, String tip, boolean selected, Runnable onChange) {
            JToggleButton b = new JToggleButton(text, selected);
            b.putClientProperty("dsviz.id", id);
            b.setFocusable(true);
            if (tip != null) b.setToolTipText(tip);
            b.addActionListener(e -> onChange.run());
            group.add(b);
            buttons.add(b);
            panel.add(b);
            return b;
        }

        String selected(String fallback) {
            for (JToggleButton b : buttons) if (b.isSelected() && b.isEnabled()) return id(b);
            return fallback;
        }

        void select(String id) {
            for (JToggleButton b : buttons) if (id(b).equals(id)) b.setSelected(true);
        }

        void enable(java.util.function.Predicate<String> ok) {
            for (JToggleButton b : buttons) b.setEnabled(ok.test(id(b)));
        }

        static String id(JToggleButton b) {
            return (String) b.getClientProperty("dsviz.id");
        }
    }

    /** כרטיס: מסגרת מעוגלת עם ריווח פנימי */
    static JPanel card(JComponent content) {
        JPanel p = new JPanel(new BorderLayout());
        p.setBorder(JBUI.Borders.compound(new RoundedLineBorder(JBColor.border(), 12, 1), JBUI.Borders.empty(10, 12)));
        p.add(content, BorderLayout.CENTER);
        return p;
    }

    /** כותרת החלון: שם גדול ומשפט הסבר */
    static JPanel header(String title, String subtitle) {
        JPanel p = new JPanel(new GridLayout(0, 1, 0, JBUI.scale(2)));
        JBLabel t = new JBLabel(title);
        t.setFont(t.getFont().deriveFont(Font.BOLD, t.getFont().getSize2D() + 5f));
        JBLabel s = new JBLabel(subtitle);
        s.setForeground(UIUtil.getContextHelpForeground());
        p.add(t);
        p.add(s);
        p.setBorder(JBUI.Borders.emptyBottom(8));
        return p;
    }

    /** שורה: רכיבים אחד ליד השני מההתחלה (מימין בעברית) */
    static JPanel row(JComponent... cs) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEADING, JBUI.scale(6), 0));
        for (JComponent c : cs) p.add(c);
        return p;
    }

    /** תווית + רכיב שתופס את שאר השורה */
    static JPanel labeled(JComponent label, JComponent field) {
        JPanel p = new JPanel(new BorderLayout(JBUI.scale(6), 0));
        p.add(label, BorderLayout.LINE_START);
        p.add(field, BorderLayout.CENTER);
        return p;
    }

    static JBLabel muted(String text) {
        // סימן כיוון (RLM) בהתחלה — כדי ששורה שמתחילה במילה באנגלית (PDF...) תוצג מימין לשמאל
        JBLabel l = new JBLabel(text.startsWith("<html>") ? "<html>\u200F" + text.substring(6) : "\u200F" + text);
        l.setForeground(UIUtil.getContextHelpForeground());
        return l;
    }

    static JBLabel error() {
        JBLabel l = new JBLabel(" ");
        l.setForeground(JBColor.RED);
        l.setHorizontalAlignment(SwingConstants.LEADING);
        return l;
    }

    /** כל החלון מימין לשמאל, ואחר כך הרכיבים של קוד (שמות, ערכים, טיפוסים) — משמאל לימין */
    static void rtl(JComponent root, JComponent... ltr) {
        root.applyComponentOrientation(ComponentOrientation.RIGHT_TO_LEFT);
        for (JComponent c : ltr) if (c != null) c.applyComponentOrientation(ComponentOrientation.LEFT_TO_RIGHT);
    }

    static <T extends JComponent> T width(T c, int w) {
        Dimension d = c.getPreferredSize();
        c.setPreferredSize(new Dimension(JBUI.scale(w), d.height));
        return c;
    }

    /** הגופן של העורך, עם גופן חלופי לאותיות שחסרות בו (עברית בהערות) */
    static Font codeFont() {
        Font f = com.intellij.openapi.editor.colors.EditorColorsManager.getInstance().getGlobalScheme()
                .getFont(com.intellij.openapi.editor.colors.EditorFontType.PLAIN);
        return UIUtil.getFontWithFallback(f.getFamily(), Font.PLAIN, f.getSize());
    }

    /** כשהתוכן גדל (תצוגה מקדימה שמופיעה) — החלון גדל איתו, אבל לא יותר מהמסך */
    static void fit(com.intellij.openapi.ui.DialogWrapper d) {
        java.awt.Window w = d.getWindow();
        if (w == null || !w.isShowing()) return;
        Dimension p = w.getPreferredSize();
        java.awt.Rectangle screen = w.getGraphicsConfiguration().getBounds();
        int width = Math.min(Math.max(w.getWidth(), p.width), screen.width - 20), height = Math.min(Math.max(w.getHeight(), p.height), screen.height - 40);
        if (width == w.getWidth() && height == w.getHeight()) return;
        w.setSize(width, height);
        w.setLocation(Math.max(screen.x, Math.min(w.getX(), screen.x + screen.width - width)), Math.max(screen.y, Math.min(w.getY(), screen.y + screen.height - height)));
        w.validate();
    }

    static JLabel label(String text) {
        return new JBLabel(text);
    }
}
