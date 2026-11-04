package il.lamerhav.dsviz;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.ComponentOrientation;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingConstants;

/**
 * "מאגר תרגילים" ל-Visual Studio: הנוסח של שאלה — אותו מבנה כמו ב-Eclipse (BankQuestionPanel): כותרת עם נקודת הקושי, כרטיס פתיחה,
 * כרטיס לכל סעיף (טקסט, ציורים של המבנים, ובחלון הצף — "לפעולה של סעיף א" ו"הצעה לפתרון"), ו"כשהכול פתור נכון, התוכנית מדפיסה".
 * הכול מימין לשמאל; קוד בתוך משפט (SumStack, Stack<int>) — משמאל לימין בגופן mono (RtlDialog.paragraph), בלי שהעברית תתהפך.
 */
final class BankVsPanel {
    private BankVsPanel() {}

    /** מה עושים בכפתורים של סעיף (רק בחלון הצף) */
    interface Actions {
        void jump(AiScaffold.Task t);

        void solution(AiScaffold.Task t);
    }

    static final int WIDE_FIGURE = 380;

    static Color level(BankStore.Level l) {
        return new Color(l.color);
    }

    /** הכותרת: נקודה בצבע הקושי, שם השאלה, ומתחת — "נושא · רמה: קל" בצבע הקושי */
    static JComponent titleRow(BankStore.Question q, String topicTitle) {
        JPanel col = column();
        JPanel row = new JPanel(new BorderLayout(8, 0));
        row.setOpaque(false);
        row.add(dot(level(q.level), 10), BorderLayout.LINE_START);
        row.add(RtlDialog.label(q.title, RtlDialog.bold().deriveFont(Font.BOLD, 17f), RtlDialog.INK), BorderLayout.CENTER);
        row.setAlignmentX(Component.RIGHT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, row.getPreferredSize().height));
        col.add(row);
        col.add(RtlDialog.gap(2));
        JLabel sub = RtlDialog.label(topicTitle + " · רמה: " + q.level.title, RtlDialog.small().deriveFont(Font.BOLD), level(q.level));
        sub.setBorder(BorderFactory.createEmptyBorder(0, 18, 0, 0));
        col.add(sub);
        return col;
    }

    /** הנוסח כולו. width — רוחב הטקסט (השורות נשברות לפיו) */
    static JComponent build(BankStore.Question q, int width, Actions actions) {
        JPanel col = column();
        AiScaffold.Exercise ex = AiScaffold.read(q.exercise);
        if (!q.intro.isEmpty() || !q.figures.isEmpty()) {
            JPanel c = column();
            if (!q.intro.isEmpty()) c.add(text(q.intro, width - 32, RtlDialog.INK_2));
            figures(c, q.figures);
            col.add(card(c));
            col.add(RtlDialog.gap(10));
        }
        for (BankStore.Section s : q.sections) {
            JPanel c = column();
            String label = s.label.replaceAll("['׳]+$", "");
            c.add(RtlDialog.label(label.isEmpty() ? "סעיף" : "סעיף " + label + "'", RtlDialog.bold().deriveFont(Font.BOLD, 14f), RtlDialog.ACCENT_2));
            c.add(RtlDialog.gap(4));
            c.add(text(s.text, width - 32, RtlDialog.INK));
            figures(c, s.figures);
            final AiScaffold.Task task = taskOf(ex, s.label);
            if (actions != null && task != null) {
                JPanel links = new JPanel(new FlowLayout(FlowLayout.LEADING, 0, 0));
                links.setOpaque(false);
                links.setComponentOrientation(ComponentOrientation.RIGHT_TO_LEFT);
                JButton go = RtlDialog.secondary(label.isEmpty() ? "לפעולה" : "לפעולה של סעיף " + label);
                go.setToolTipText(RtlDialog.rtl("פותח ב-Visual Studio את הקובץ, בפעולה " + task.methodName));
                go.addActionListener(e -> actions.jump(task));
                go.putClientProperty("dsviz.id", "jump-" + label);
                JButton sol = RtlDialog.secondary("הצעה לפתרון");
                sol.setIcon(new Bulb());
                sol.setIconTextGap(6);
                sol.addActionListener(e -> actions.solution(task));
                sol.putClientProperty("dsviz.id", "solution-" + label);
                links.add(go);
                links.add(Box8.h());
                links.add(sol);
                links.setAlignmentX(Component.RIGHT_ALIGNMENT);
                links.setMaximumSize(new Dimension(Integer.MAX_VALUE, links.getPreferredSize().height + 2));
                c.add(RtlDialog.gap(10));
                c.add(links);
            }
            col.add(card(c));
            col.add(RtlDialog.gap(10));
        }
        String expected = q.expectedOutput();
        if (!expected.isEmpty()) {
            JPanel c = column();
            c.add(RtlDialog.label("כשהכול פתור נכון, התוכנית מדפיסה:", RtlDialog.bold(), RtlDialog.INK));
            c.add(RtlDialog.gap(6));
            // כמו בחלון הפלט: משמאל לימין, שורה-שורה ("[4,7,2,9]" לא מתהפך)
            // כמו בחלון הפלט: כל שורה משמאל לימין ("סעיף א: 22", "[4,7,2,9]" — לא מתהפך)
            JComponent out = new ConsoleLines(expected);
            RtlDialog.Panel box = new RtlDialog.Panel(new BorderLayout(), RtlDialog.SUNK, RtlDialog.LINE);
            box.add(out, BorderLayout.CENTER);
            box.setAlignmentX(Component.RIGHT_ALIGNMENT);
            c.add(box);
            c.add(RtlDialog.gap(6));
            c.add(RtlDialog.label("מריצים בכפתור \"הרצה ויזואלית\" (ליד הכפתור הירוק), ובודקים שזה מה שמודפס.", RtlDialog.small(), RtlDialog.MUTED));
            col.add(card(c));
        }
        return col;
    }

    /** ציורים: זה ליד זה, כל אחד בגודל הטבעי; ציור רחב — עם גלילה הצידה; מתחת לכל ציור — הכיתוב שלו */
    static void figures(JPanel parent, List<BankStore.Figure> figs) {
        if (figs.isEmpty()) return;
        JPanel row = new JPanel(new FlowLayout(FlowLayout.CENTER, 18, 4));
        row.setOpaque(false);
        row.setComponentOrientation(ComponentOrientation.RIGHT_TO_LEFT);
        for (BankStore.Figure f : figs) {
            JPanel one = new JPanel();
            one.setLayout(new BoxLayout(one, BoxLayout.Y_AXIS));
            one.setOpaque(false);
            JComponent d = il.lamerhav.dsviz.cs.BankFigure.of(f.type, f.inner, f.groups);
            Dimension p = d.getPreferredSize();
            JComponent shown = d;
            if (p.width > WIDE_FIGURE) {
                JScrollPane sp = new JScrollPane(d, ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_ALWAYS);
                sp.setBorder(BorderFactory.createEmptyBorder());
                sp.setOpaque(false);
                sp.getViewport().setOpaque(false);
                sp.getHorizontalScrollBar().setUnitIncrement(16);
                Dimension s = new Dimension(WIDE_FIGURE, p.height + sp.getHorizontalScrollBar().getPreferredSize().height + 4);
                sp.setPreferredSize(s);
                sp.setMaximumSize(s);
                sp.putClientProperty("dsviz.id", "figure-scroll");
                shown = sp;
            }
            shown.setAlignmentX(Component.CENTER_ALIGNMENT);
            one.add(shown);
            if (!f.caption.isEmpty()) {
                JLabel cap = new JLabel(caption(f.caption), SwingConstants.CENTER);
                cap.setFont(RtlDialog.small());
                cap.setForeground(RtlDialog.MUTED);
                cap.setAlignmentX(Component.CENTER_ALIGNMENT);
                one.add(RtlDialog.gap(3));
                one.add(cap);
            }
            row.add(one);
        }
        row.setAlignmentX(Component.RIGHT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, row.getPreferredSize().height));
        parent.add(RtlDialog.gap(6));
        parent.add(row);
    }

    /**
     * כיתוב של ציור ("המחסנית s (4 בראש)"): כל השורה מימין לשמאל; כל קטע באנגלית (שם, או קריאה עם הסוגריים שלה) — יחידה אחת משמאל לימין,
     * ו-RLM אחריו: בלעדיו מספר שבא אחריו נדבק לאנגלית ועובר לצד השני.
     */
    static String caption(String s) {
        Matcher m = Pattern.compile("[A-Za-z_][A-Za-z0-9_.]*(\\([^()]*\\))?").matcher(s);
        StringBuilder b = new StringBuilder("‏‫");
        int last = 0;
        while (m.find()) {
            b.append(s, last, m.start()).append("‪").append(m.group()).append("‬‏");
            last = m.end();
        }
        return b.append(s.substring(last)).append("‬").toString();
    }

    static JComponent text(String s, int width, Color color) {
        JComponent t = RtlDialog.paragraph(s, RtlDialog.ui(), color, Math.max(160, width));
        t.setAlignmentX(Component.RIGHT_ALIGNMENT);
        return t;
    }

    static JPanel card(JComponent inner) {
        return RtlDialog.card(inner, 12, 14);
    }

    static JPanel column() {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setOpaque(false);
        p.setAlignmentX(Component.RIGHT_ALIGNMENT);
        return p;
    }

    static AiScaffold.Task taskOf(AiScaffold.Exercise ex, String label) {
        String l = label.replaceAll("[\\s'׳.]", "");
        for (AiScaffold.Task t : ex.tasks) if (t.label.replaceAll("[\\s'׳.]", "").equals(l)) return t;
        return null;
    }

    static JComponent dot(final Color c, final int size) {
        JComponent d = new JComponent() {
            @Override
            protected void paintComponent(Graphics g0) {
                Graphics2D g = (Graphics2D) g0.create();
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setColor(c);
                g.fillOval((getWidth() - size) / 2, (getHeight() - size) / 2, size, size);
                g.dispose();
            }
        };
        d.setPreferredSize(new Dimension(size + 4, size + 4));
        return d;
    }

    /** הפלט הרצוי: שורות משמאל לימין בגופן mono — כל שורה ב-TextLayout עם כיוון קבוע (בלי תלות במילה הראשונה) */
    static final class ConsoleLines extends JComponent {
        final String[] lines;

        ConsoleLines(String text) {
            lines = text.split("\n", -1);
            setFont(RtlDialog.mono(13f));
            setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
            java.awt.FontMetrics fm = getFontMetrics(getFont());
            int w = 0;
            for (String l : lines) w = Math.max(w, fm.stringWidth(l));
            setPreferredSize(new Dimension(w + 24, lines.length * (fm.getHeight() + 2) + 16));
            putClientProperty("ltr", Boolean.TRUE);
            putClientProperty("dsviz.text", text);
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setColor(RtlDialog.INK);
            java.awt.FontMetrics fm = g.getFontMetrics(getFont());
            int y = 8 + fm.getAscent();
            for (String l : lines) {
                if (!l.isEmpty()) {
                    java.text.AttributedString a = new java.text.AttributedString(l);
                    a.addAttribute(java.awt.font.TextAttribute.FONT, getFont());
                    a.addAttribute(java.awt.font.TextAttribute.RUN_DIRECTION, java.awt.font.TextAttribute.RUN_DIRECTION_LTR);
                    new java.awt.font.TextLayout(a.getIterator(), g.getFontRenderContext()).draw(g, 10, y);
                }
                y += fm.getHeight() + 2;
            }
            g.dispose();
        }
    }

    /** נורה קטנה (במקום 💡 — הגופנים של Java ב-Windows לא מציירים אמוג'י) */
    static final class Bulb implements Icon {
        public int getIconWidth() { return 12; }

        public int getIconHeight() { return 15; }

        public void paintIcon(Component c, Graphics g0, int x, int y) {
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(new Color(0xF5B700));
            g.fillOval(x + 1, y, 10, 10);
            g.setColor(new Color(0xC99400));
            g.drawOval(x + 1, y, 10, 10);
            g.setColor(new Color(0x8A93A3));
            g.fillRoundRect(x + 3, y + 10, 6, 4, 2, 2);
            g.dispose();
        }
    }

    /** רווח אופקי קטן בין כפתורים */
    static final class Box8 {
        static Component h() {
            JPanel p = new JPanel();
            p.setOpaque(false);
            p.setPreferredSize(new Dimension(8, 1));
            return p;
        }
    }
}
