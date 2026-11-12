package il.lamerhav.dsviz;

import unit4.collectionsLib.Theme;
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

        /** כמה סעיפים כבר בקוד (התרגיל נבנה סעיף אחרי סעיף — BankSections). בלי תרגיל — כולם */
        default int prepared() {
            return Integer.MAX_VALUE;
        }

        /** "הכן סעיף ב": הפעולה של הסעיף והבדיקות שלו נכנסות לקוד */
        default void prepare(AiScaffold.Task t) {}
    }

    static Color level(BankStore.Level l) {
        return new Color(l.color);
    }

    /** הכותרת: נקודה בצבע הקושי, שם השאלה, ומתחת — "נושא · רמה: קל" בצבע הקושי */
    static JComponent titleRow(BankStore.Question q, String topicTitle) {
        JPanel col = column();
        JPanel row = new JPanel(new BorderLayout(8, 0));
        row.setOpaque(false);
        row.add(dot(level(q.level), 10), BorderLayout.LINE_START);
        row.add(RtlDialog.label(q.title, Theme.z(RtlDialog.bold().deriveFont(17f)), RtlDialog.INK), BorderLayout.CENTER);
        row.setAlignmentX(Component.RIGHT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, row.getPreferredSize().height));
        col.add(row);
        col.add(RtlDialog.gap(2));
        JLabel sub = RtlDialog.label(topicTitle + " · רמה: " + q.level.title, RtlDialog.bold().deriveFont(RtlDialog.small().getSize2D()), level(q.level));
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
            figures(c, q.figures, width - 32);
            col.add(card(c));
            col.add(RtlDialog.gap(10));
        }
        int ready = actions == null ? Integer.MAX_VALUE : actions.prepared();
        int index = 0;
        for (BankStore.Section s : q.sections) {
            final int at = index++;
            JPanel c = column();
            String label = s.label.replaceAll("['׳]+$", "");
            c.add(RtlDialog.label(label.isEmpty() ? "סעיף" : "סעיף " + label + "'", Theme.z(RtlDialog.bold().deriveFont(14f)), RtlDialog.ACCENT_2));
            c.add(RtlDialog.gap(4));
            c.add(text(s.text, width - 32, RtlDialog.INK));
            figures(c, s.figures, width - 32);
            final AiScaffold.Task task = taskOf(ex, s.label);
            if (actions != null && task != null && at >= ready) {
                // הסעיף עוד לא בקוד: "הכן סעיף ב" (רק לסעיף הבא בתור); הפעולה והבדיקות שלו נכנסות למקום הנכון בקוד
                c.add(RtlDialog.gap(10));
                if (at == ready) {
                    JButton prep = RtlDialog.primary("הכן סעיף " + label + "'");
                    prep.setToolTipText(RtlDialog.rtl("מוסיף לקוד את הפעולה " + task.methodName + " ואת הבדיקות של הסעיף ב-main"));
                    prep.addActionListener(e -> actions.prepare(task));
                    prep.putClientProperty("dsviz.id", "prepare-" + label);
                    prep.setAlignmentX(Component.RIGHT_ALIGNMENT);
                    c.add(prep);
                    c.add(RtlDialog.gap(4));
                    c.add(text("הפעולה של הסעיף והבדיקות שלו ב-main ייכנסו לקוד במקום המתאים — לא משנה איפה הסמן.", width - 32, RtlDialog.MUTED));
                } else c.add(text("אחרי סעיף " + q.sections.get(at - 1).label.replaceAll("['׳]+$", "") + "'", width - 32, RtlDialog.MUTED));
            } else if (actions != null && task != null) {
                JPanel links = new JPanel(new FlowLayout(FlowLayout.LEADING, 0, 0));
                links.setOpaque(false);
                links.setComponentOrientation(ComponentOrientation.RIGHT_TO_LEFT);
                JButton go = RtlDialog.secondary(label.isEmpty() ? "לפעולה" : "לפעולה של סעיף " + label);
                go.setToolTipText(RtlDialog.rtl("פותח ב-" + BankVs.ide() + " את הקובץ, בפעולה " + task.methodName));
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
        boolean partial = ready < q.sections.size();
        if (partial) expected = BankSections.expectedUpTo(expected, BankSections.labels(q.exercise), ready);   // רק הסעיפים שכבר בקוד
        if (!expected.isEmpty()) {
            JPanel c = column();
            c.add(wrapped(RtlDialog.paragraph(partial ? "כשהסעיפים שכבר בקוד פתורים נכון, התוכנית מדפיסה:" : "כשהכול פתור נכון, התוכנית מדפיסה:",
                    RtlDialog.bold(), RtlDialog.INK, width - 40, true)));   // יורד שורה בהגדלה
            c.add(RtlDialog.gap(6));
            // כמו בחלון הפלט: משמאל לימין, שורה-שורה ("[4,7,2,9]" לא מתהפך)
            // כמו בחלון הפלט: כל שורה משמאל לימין ("סעיף א: 22", "[4,7,2,9]" — לא מתהפך)
            JComponent out = new ConsoleLines(expected);
            int room = width - 40;
            if (out.getPreferredSize().width > room) {
                // שורה ארוכה (בהגדלה גדולה) — גלילה לצד רק בתוך הפלט, ושאר השאלה נשארת ברוחב החלון
                JScrollPane sp = new JScrollPane(out, ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
                sp.setBorder(BorderFactory.createEmptyBorder());
                sp.setOpaque(false);
                sp.getViewport().setOpaque(false);
                sp.applyComponentOrientation(java.awt.ComponentOrientation.LEFT_TO_RIGHT);
                sp.putClientProperty("ltr", Boolean.TRUE);   // גם אחרי RtlDialog.rtl של כל החלון: הגלילה מתחילה משמאל, מתחילת השורות
                Dimension s = new Dimension(room, out.getPreferredSize().height + sp.getHorizontalScrollBar().getPreferredSize().height + 2);
                sp.setPreferredSize(s);
                sp.setMaximumSize(new Dimension(Integer.MAX_VALUE, s.height));
                out = sp;
            }
            RtlDialog.Panel box = new RtlDialog.Panel(new BorderLayout(), RtlDialog.SUNK, RtlDialog.LINE);
            box.add(out, BorderLayout.CENTER);
            box.setAlignmentX(Component.RIGHT_ALIGNMENT);
            c.add(box);
            c.add(RtlDialog.gap(6));
            c.add(wrapped(RtlDialog.paragraph(partial ? "מריצים ב\"הרצה ויזואלית\", ובודקים שזה מה שמודפס. כשמכינים את הסעיף הבא — מתווספות כאן גם השורות שלו."
                    : "מריצים ב\"הרצה ויזואלית\", ובודקים שזה מה שמודפס.", RtlDialog.small(), RtlDialog.MUTED, width - 40, true)));
            col.add(card(c));
        }
        return col;
    }

    /**
     * ציורים: זה ליד זה, כל אחד בגודל הטבעי, ומתחת לכל ציור — הכיתוב שלו. room — הרוחב שיש (כמו לטקסט): מה שלא נכנס בשורה יורד
     * לשורה הבאה, וציור שלבדו רחב מהשורה (עץ גדול, שרשרת ארוכה, החלון הצף הצר) — מוקטן עד שהוא נכנס. כך רואים תמיד את כל הציור,
     * והשאלה לא נעשית רחבה מהחלון (בלי גלילה הצידה)
     */
    static void figures(JPanel parent, List<BankStore.Figure> figs, int room) {
        if (figs.isEmpty()) return;
        parent.add(RtlDialog.gap(6));
        JPanel row = null;
        int used = 0;
        for (BankStore.Figure f : figs) {
            JComponent one = figure(f, room);
            int w = one.getPreferredSize().width;
            if (row != null && used + FIGURE_GAP + w > room) {
                parent.add(done(row));
                row = null;
            }
            if (row == null) {
                row = figureRow(room);
                used = 0;
            } else {
                row.add(javax.swing.Box.createRigidArea(new Dimension(FIGURE_GAP, 1)));   // רווח רק בין הציורים (לא בצדדים)
                used += FIGURE_GAP;
            }
            row.add(one);
            used += w;
        }
        parent.add(done(row));
    }

    /** רווח בין ציורים באותה שורה */
    static final int FIGURE_GAP = 18;

    /** ציור אחד והכיתוב שלו; לא רחב מ-room */
    static JComponent figure(BankStore.Figure f, int room) {
        JPanel one = new JPanel();
        one.setLayout(new BoxLayout(one, BoxLayout.Y_AXIS));
        one.setOpaque(false);
        JComponent d = BankZoom.scaled(il.lamerhav.dsviz.cs.BankFigure.of(f.type, f.inner, f.groups), room);
        d.setAlignmentX(Component.CENTER_ALIGNMENT);
        one.add(d);
        if (!f.caption.isEmpty()) {
            JLabel cap = new JLabel(caption(f.caption), SwingConstants.CENTER);
            cap.setFont(RtlDialog.small());
            cap.setForeground(RtlDialog.MUTED);
            cap.setAlignmentX(Component.CENTER_ALIGNMENT);
            one.add(RtlDialog.gap(3));
            one.add(cap);
        }
        return one;
    }

    /**
     * שורה של ציורים, ממורכזת (אותו מקום כמו קודם, ב-FlowLayout עם רווח 18). לא רחבה מ-room — גם כשהכיתוב של ציור יחיד ארוך ממנה
     * (אז הוא ממורכז ונחתך בשני הצדדים, והשאלה לא נעשית רחבה מהחלון)
     */
    private static JPanel figureRow(final int room) {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 4)) {
            @Override
            public Dimension getPreferredSize() {
                Dimension d = super.getPreferredSize();
                return new Dimension(Math.min(d.width, room), d.height);
            }
        };
        row.setOpaque(false);
        row.setComponentOrientation(ComponentOrientation.RIGHT_TO_LEFT);
        row.setAlignmentX(Component.RIGHT_ALIGNMENT);
        return row;
    }

    private static JPanel done(JPanel row) {
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, row.getPreferredSize().height));
        return row;
    }

    /**
     * גלילה לתחילת הנוסח: למעלה, ובצד ימין — שם מתחילות השורות בעברית — גם אם התוכן בכל זאת רחב מהחלון (חלון צר מאוד, הגדלה גדולה).
     * לפי מקום התצוגה ולא לפי ערך הפס: בחלון מימין לשמאל הערך המקסימלי של הפס הוא דווקא הקצה השמאלי.
     * (גם מבטל גלילה שהסמן של פסקה בנוסח — JTextPane — גרם לה אחרי שהטקסט נכנס)
     */
    static void scrollToStart(JScrollPane sp) {
        java.awt.Window w = javax.swing.SwingUtilities.getWindowAncestor(sp);
        if (w != null) w.validate();
        else sp.validate();
        javax.swing.JViewport vp = sp.getViewport();
        Dimension view = vp.getViewSize(), extent = vp.getExtentSize();
        // צר מהחלון — המקום שבו הגלילה שמה אותו (מימין לשמאל: צמוד לימין); רחב — הקצה הימני שלו
        int x = view.width > extent.width ? view.width - extent.width : vp.getViewPosition().x;
        vp.setViewPosition(new java.awt.Point(x, 0));
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

    private static JComponent wrapped(JComponent p) {
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
            setFont(Theme.z(RtlDialog.mono(13f)));
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
