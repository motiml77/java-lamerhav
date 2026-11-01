package il.lamerhav.dsviz.intellij;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.ComponentOrientation;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.font.TextAttribute;
import java.awt.geom.Path2D;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.swing.Action;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.ButtonModel;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextPane;
import javax.swing.SwingConstants;
import javax.swing.border.AbstractBorder;
import javax.swing.plaf.basic.BasicButtonUI;
import javax.swing.text.BadLocationException;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBTextArea;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;

/**
 * המראה החדש של החלונות של DSViz ב-IntelliJ — כמו באתר (site/DESIGN.md): לוחות לבנים שטוחים, קו של 1px (‎#DFE3EA), פינות קטנות,
 * כחול אחד להדגשה (‎#2F6FED), ירוק למה שהסתיים, צהוב למקום התשובה, וטקסט קצר. כל צבע הוא זוג (בהיר / Darcula).
 * כאן: הצבעים, הגופנים, כפתור ראשי ושקט, קישור, תג מצב, מסגרת, טקסט עברי (מימין לשמאל) עם קטעי קוד (משמאל לימין, בגופן קוד),
 * והבסיס לחלונות (Dialog): מימין לשמאל, והכפתורים בסדר של עברית.
 */
final class Modern {
    private Modern() {}

    // ------------------------------------------------------------------ צבעים (בהיר, כהה)

    static final JBColor PANEL = new JBColor(0xFFFFFF, 0x2B2D30);
    static final JBColor SUNK = new JBColor(0xF2F3F6, 0x232427);
    static final JBColor LINE = new JBColor(0xDFE3EA, 0x43454A);
    static final JBColor LINE_2 = new JBColor(0xCDD2DA, 0x5A5D63);
    static final JBColor INK = new JBColor(0x0E1116, 0xDFE1E5);
    static final JBColor INK_2 = new JBColor(0x3B4250, 0xBCBEC4);
    static final JBColor MUTED = new JBColor(0x646C7A, 0x8F949C);
    static final JBColor ACCENT = new JBColor(0x2F6FED, 0x548AF7);
    static final JBColor ACCENT_HOVER = new JBColor(0x1F56C9, 0x6B9BFA);
    static final JBColor ACCENT_SOFT = new JBColor(0xEAF1FD, 0x26334D);
    static final JBColor OK = new JBColor(0x2E9E5B, 0x5FB878);
    static final JBColor OK_SOFT = new JBColor(0xE3F4EA, 0x263B2E);
    static final JBColor ERR = new JBColor(0xC4302B, 0xF0716B);
    static final JBColor ERR_SOFT = new JBColor(0xFBEAEA, 0x472B2C);
    static final JBColor AMBER = new JBColor(0xE8A100, 0xD9A23A);
    static final JBColor AMBER_SOFT = new JBColor(0xFFF4D6, 0x45391F);
    /** הרקע של מקום התשובה בעורך: rgba(232,161,0,.12) על לבן (כמו .ans באתר), ובכהה — אותו צהוב על הרקע של Darcula */
    static final JBColor ANSWER = new JBColor(0xFCF4E0, 0x3E3625);

    /** סימני כיוון: קטע קוד בתוך משפט בעברית עטוף ב-LRE ... PDF, כדי שסוגריים ו-; לא יקפצו לצד הלא נכון */
    static final char LRE = '‪', PDF = '‬', RLM = '‏';

    // ------------------------------------------------------------------ גופנים

    static Font font(float size, int style) {
        Font base = UIUtil.getLabelFont();
        return base.deriveFont(style, JBUI.scale(size));
    }

    /** גופן הקוד של העורך (עם גופן חלופי לעברית), או Monospaced כשאין עורך (בדיקות) */
    static Font mono(float size) {
        try {
            Font f = Forms.codeFont();
            return f.deriveFont(Font.PLAIN, JBUI.scale(size));
        } catch (Throwable e) {
            return new Font(Font.MONOSPACED, Font.PLAIN, Math.round(JBUI.scale(size)));
        }
    }

    // ------------------------------------------------------------------ רכיבים

    /** תווית בצבע ובגודל נתונים, מימין לשמאל */
    static JLabel label(String text, float size, int style, Color color) {
        JLabel l = new JLabel(text);
        l.setFont(font(size, style));
        l.setForeground(color);
        l.setHorizontalAlignment(SwingConstants.LEADING);
        l.setComponentOrientation(ComponentOrientation.RIGHT_TO_LEFT);
        return l;
    }

    static JPanel panel(java.awt.LayoutManager layout) {
        JPanel p = new JPanel(layout);
        p.setBackground(PANEL);
        p.setOpaque(true);
        return p;
    }

    /** קישור: כחול, יד, קו תחתון במעבר עכבר; גם Enter / רווח כשהוא בפוקוס */
    static JLabel link(String text, Runnable onClick) {
        JLabel l = label(text, 13, Font.PLAIN, ACCENT);
        l.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        l.setFocusable(true);
        l.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                onClick.run();
            }

            @Override
            public void mouseEntered(MouseEvent e) {
                underline(l, true);
            }

            @Override
            public void mouseExited(MouseEvent e) {
                underline(l, false);
            }
        });
        l.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER || e.getKeyCode() == KeyEvent.VK_SPACE) onClick.run();
            }
        });
        return l;
    }

    @SuppressWarnings("unchecked")
    private static void underline(JLabel l, boolean on) {
        Map<TextAttribute, Object> a = (Map<TextAttribute, Object>) l.getFont().getAttributes();
        a.put(TextAttribute.UNDERLINE, on ? TextAttribute.UNDERLINE_ON : -1);
        l.setFont(l.getFont().deriveFont(a));
    }

    /** כפתור: ראשי (מלא בכחול) או שקט (בלי מילוי, קו דק). מצויר לבד — אותו מראה בכל ערכת נושא */
    static final class Button extends JButton {
        final boolean primary;

        Button(Action a, boolean primary) {
            super(a);
            this.primary = primary;
            setup();
        }

        Button(String text, boolean primary, Runnable onClick) {
            super(text);
            this.primary = primary;
            setup();
            addActionListener(e -> onClick.run());
        }

        private void setup() {
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setOpaque(false);
            setRolloverEnabled(true);
            setFont(font(13, primary ? Font.BOLD : Font.PLAIN));
            setForeground(primary ? JBColor.WHITE : INK);
            setBorder(JBUI.Borders.empty(6, 16));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setIcon(null);
        }

        @Override
        public void updateUI() {
            setUI(new BasicButtonUI());
        }

        @Override
        public Dimension getPreferredSize() {
            Dimension d = super.getPreferredSize();
            return new Dimension(Math.max(d.width, JBUI.scale(76)), Math.max(d.height, JBUI.scale(30)));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            ButtonModel m = getModel();
            int w = getWidth(), h = getHeight(), arc = JBUI.scale(12), f = JBUI.scale(2);
            if (primary) {
                g2.setColor(!isEnabled() ? LINE_2 : m.isPressed() || m.isRollover() ? ACCENT_HOVER : ACCENT);
                g2.fillRoundRect(f, f, w - 2 * f, h - 2 * f, arc, arc);
            } else {
                g2.setColor(m.isPressed() ? LINE : m.isRollover() ? SUNK : PANEL);
                g2.fillRoundRect(f, f, w - 2 * f, h - 2 * f, arc, arc);
                g2.setColor(LINE_2);
                g2.drawRoundRect(f, f, w - 2 * f - 1, h - 2 * f - 1, arc, arc);
            }
            if (isFocusOwner()) {
                g2.setColor(ACCENT);
                g2.setStroke(new BasicStroke(JBUI.scale(2f)));
                g2.drawRoundRect(1, 1, w - 3, h - 3, arc + f, arc + f);
            }
            g2.dispose();
            setForeground(primary ? JBColor.WHITE : isEnabled() ? INK : MUTED);
            super.paintComponent(g);
        }
    }

    /** מסגרת: קו של 1px בפינות מעוגלות קטנות (6px) */
    static final class Round extends AbstractBorder {
        private final Color color;
        private final int arc;
        private final Insets pad;

        Round(Color color, int arc, int vpad, int hpad) {
            this.color = color;
            this.arc = arc;
            this.pad = JBUI.insets(vpad, hpad);
        }

        @Override
        public void paintBorder(Component c, Graphics g, int x, int y, int w, int h) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(color);
            g2.drawRoundRect(x, y, w - 1, h - 1, JBUI.scale(arc * 2), JBUI.scale(arc * 2));
            g2.dispose();
        }

        @Override
        public Insets getBorderInsets(Component c) {
            return new Insets(pad.top + 1, pad.left + 1, pad.bottom + 1, pad.right + 1);
        }
    }

    /** כרטיס: לוח לבן עם קו דק ופינות של 6px */
    static JPanel card(JComponent content) {
        JPanel p = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(getBackground());
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), JBUI.scale(12), JBUI.scale(12));
                g2.dispose();
            }
        };
        p.setOpaque(false);
        p.setBackground(PANEL);
        p.setBorder(new Round(LINE, 6, 10, 12));
        p.add(content, BorderLayout.CENTER);
        return p;
    }

    /** סוג ההודעה — הצבע והסימן בתג שליד הכותרת */
    enum Kind {
        INFO, OK, WARN, ERROR, QUESTION;

        Color color() {
            switch (this) {
                case OK: return Modern.OK;
                case WARN: return AMBER;
                case ERROR: return ERR;
                default: return ACCENT;
            }
        }

        Color soft() {
            switch (this) {
                case OK: return OK_SOFT;
                case WARN: return AMBER_SOFT;
                case ERROR: return ERR_SOFT;
                default: return ACCENT_SOFT;
            }
        }
    }

    /** תג עגול קטן (20px) עם סימן: ✓ / ! / i / ? — במקום האייקונים הגדולים של חלונות ההודעה */
    static Icon badge(Kind kind) {
        return new Icon() {
            @Override
            public void paintIcon(Component c, Graphics g, int x, int y) {
                int s = getIconWidth();
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
                g2.translate(x, y);
                g2.setColor(kind.soft());
                g2.fillOval(0, 0, s, s);
                g2.setColor(kind.color());
                float u = s / 20f;
                g2.setStroke(new BasicStroke(2f * u, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                switch (kind) {
                    case OK: {
                        Path2D p = new Path2D.Float();
                        p.moveTo(6 * u, 10.5f * u);
                        p.lineTo(8.8f * u, 13.2f * u);
                        p.lineTo(14 * u, 7.2f * u);
                        g2.draw(p);
                        break;
                    }
                    case QUESTION: {
                        Path2D p = new Path2D.Float();
                        p.moveTo(7.6f * u, 8 * u);
                        p.curveTo(7.6f * u, 4.8f * u, 12.6f * u, 4.8f * u, 12.6f * u, 7.8f * u);
                        p.curveTo(12.6f * u, 9.8f * u, 10 * u, 10 * u, 10 * u, 11.8f * u);
                        g2.draw(p);
                        g2.fillOval(Math.round(8.9f * u), Math.round(13.6f * u), Math.round(2.3f * u), Math.round(2.3f * u));
                        break;
                    }
                    case INFO:
                        g2.fillOval(Math.round(8.9f * u), Math.round(4.8f * u), Math.round(2.3f * u), Math.round(2.3f * u));
                        g2.drawLine(Math.round(10 * u), Math.round(9 * u), Math.round(10 * u), Math.round(14.6f * u));
                        break;
                    default:   // ! (אזהרה, שגיאה)
                        g2.drawLine(Math.round(10 * u), Math.round(5.4f * u), Math.round(10 * u), Math.round(11 * u));
                        g2.fillOval(Math.round(8.9f * u), Math.round(13.4f * u), Math.round(2.3f * u), Math.round(2.3f * u));
                }
                g2.dispose();
            }

            @Override
            public int getIconWidth() {
                return JBUI.scale(20);
            }

            @Override
            public int getIconHeight() {
                return JBUI.scale(20);
            }
        };
    }

    // ------------------------------------------------------------------ עברית עם קטעי קוד

    private static boolean hebrew(char c) {
        return (c >= '֐' && c <= '׿') || (c >= 'יִ' && c <= 'ﭏ');
    }

    static boolean hasHebrew(String s) {
        for (int i = 0; i < s.length(); i++) if (hebrew(s.charAt(i))) return true;
        return false;
    }

    /** שורה של קוד (בלי עברית, עם אות לטינית וסימן של קוד) — מוצגת כולה משמאל לימין בגופן קוד */
    static boolean codeLine(String line) {
        String t = line.trim();
        if (t.matches("[{}();]+")) return true;   // סוגר לבד בשורה — חלק מקטע קוד
        if (t.isEmpty() || hasHebrew(t) || !t.matches("(?s).*[A-Za-z].*")) return false;
        return t.matches("(?s).*[;{}()\\[\\]=<>].*") || t.matches("^(public|private|static|int|void|for|while|if|else|return|class|new|boolean|char|double|String)\\b.*");
    }

    /**
     * פירוק שורה בעברית לקטעים: {טקסט, null} — עברית רגילה; {טקסט, "code"} — קטע קוד (אות לטינית), שיוצג בגופן קוד ומשמאל לימין.
     * סימני פיסוק של המשפט (נקודה, פסיק, נקודתיים, סימן שאלה) וסוגריים שעוטפים את הקטע נשארים בחוץ.
     */
    static List<String[]> segments(String line) {
        List<String[]> out = new ArrayList<String[]>();
        int i = 0, n = line.length();
        StringBuilder plain = new StringBuilder();
        while (i < n) {
            if (hebrew(line.charAt(i))) {
                plain.append(line.charAt(i++));
                continue;
            }
            int j = i;
            while (j < n && !hebrew(line.charAt(j))) j++;
            String run = line.substring(i, j);
            int a = 0, b = run.length();
            while (a < b && " \t,:;'\"״׳(—–-".indexOf(run.charAt(a)) >= 0 && !(run.charAt(a) == '(' && balance(run.substring(a, b)) <= 0)) a++;
            while (b > a && " \t,.:?!'\"״׳—–".indexOf(run.charAt(b - 1)) >= 0) b--;
            while (b > a && run.charAt(b - 1) == ')' && balance(run.substring(a, b)) < 0) b--;
            while (b > a && " \t,.:?!".indexOf(run.charAt(b - 1)) >= 0) b--;
            String core = run.substring(a, b);
            if (core.matches("(?s).*[A-Za-z].*")) {
                plain.append(run, 0, a);
                if (plain.length() > 0) out.add(new String[] { plain.toString(), null });
                plain.setLength(0);
                out.add(new String[] { core, NAMES.contains(core) ? "name" : "code" });
                plain.append(run.substring(b));
            } else plain.append(run);
            i = j;
        }
        if (plain.length() > 0) out.add(new String[] { plain.toString(), null });
        return out;
    }

    /** שמות של תוכנות ומוצרים — באותיות רגילות (לא בגופן קוד), אבל עדיין משמאל לימין */
    static final java.util.Set<String> NAMES = new java.util.HashSet<String>(java.util.Arrays.asList(
            "DSViz", "Gemini", "Java", "IntelliJ", "IntelliJ IDEA", "Eclipse", "VS Code", "Visual Studio", "Word", "PDF", "Build", "Windows", "Mac",
            "Wi-Fi", "Ctrl+V", "Google", "JDK"));

    /** מספר הסוגריים הפותחים פחות הסוגרים */
    private static int balance(String s) {
        int k = 0;
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) == '(') k++;
            else if (s.charAt(i) == ')') k--;
        }
        return k;
    }

    /** השורה עם סימני הכיוון (לבדיקות, ולמקומות שבהם יש רק טקסט — למשל HTML): קטעי הקוד עטופים ב-LRE ... PDF */
    static String bidi(String line) {
        StringBuilder b = new StringBuilder();
        for (String[] s : segments(line)) {
            if (s[1] == null) b.append(s[0]);
            else b.append(LRE).append(s[0]).append(PDF);
        }
        return b.toString();
    }

    /**
     * טקסט עברי מימין לשמאל, שנשבר לשורות ברוחב נתון; קטעי קוד בתוכו בגופן קוד ומשמאל לימין. width — בפיקסלים (לפני הגדלה).
     */
    static JTextPane text(String text, int width, float size, Color color) {
        return text(text, width, size, color, Font.PLAIN);
    }

    static JTextPane text(String text, int width, float size, Color color, int style) {
        JTextPane p = new JTextPane(new Doc(font(size, Font.PLAIN), mono(size - 0.5f)));
        p.setEditable(false);
        p.setOpaque(false);
        p.setBorder(JBUI.Borders.empty());
        p.setFont(font(size, Font.PLAIN));
        p.setForeground(color);
        p.setComponentOrientation(ComponentOrientation.RIGHT_TO_LEFT);
        StyledDocument doc = p.getStyledDocument();
        doc.putProperty(TextAttribute.RUN_DIRECTION, TextAttribute.RUN_DIRECTION_RTL);
        SimpleAttributeSet base = new SimpleAttributeSet();
        StyleConstants.setForeground(base, color);
        StyleConstants.setBold(base, (style & Font.BOLD) != 0);
        SimpleAttributeSet code = new SimpleAttributeSet(base);
        code.addAttribute(Doc.CODE, Boolean.TRUE);
        try {
            String[] lines = text.split("\n", -1);
            for (int k = 0; k < lines.length; k++) {
                if (k > 0) doc.insertString(doc.getLength(), "\n", base);
                for (String[] s : segments(lines[k])) {
                    if (s[1] == null) doc.insertString(doc.getLength(), s[0], base);
                    else doc.insertString(doc.getLength(), LRE + s[0] + PDF, "code".equals(s[1]) ? code : base);
                }
            }
        } catch (BadLocationException ignored) {
            // לא יקרה
        }
        SimpleAttributeSet right = new SimpleAttributeSet();
        StyleConstants.setAlignment(right, StyleConstants.ALIGN_RIGHT);
        StyleConstants.setLineSpacing(right, 0.15f);
        doc.setParagraphAttributes(0, doc.getLength(), right, false);
        int w = JBUI.scale(width);
        p.setSize(new Dimension(w, Short.MAX_VALUE));
        p.setPreferredSize(new Dimension(w, p.getPreferredSize().height));
        return p;
    }

    /**
     * המסמך של הטקסט: הגופנים נגזרים מהגופן של IntelliJ (עם הגופן החלופי לעברית) ומגופן הקוד של העורך — ולא לפי שם המשפחה בלבד,
     * שאז Inter / JetBrains Mono בלי עברית והאותיות נמדדות בגופן אחד ומצוירות באחר.
     */
    static final class Doc extends javax.swing.text.DefaultStyledDocument {
        static final Object CODE = new Object() {
            @Override
            public String toString() {
                return "dsviz.code";
            }
        };
        private final Font base, code;

        Doc(Font base, Font code) {
            this.base = base;
            this.code = code;
        }

        @Override
        public Font getFont(javax.swing.text.AttributeSet a) {
            Font f = a.getAttribute(CODE) != null ? code : base;
            int style = StyleConstants.isBold(a) ? Font.BOLD : Font.PLAIN;
            return f.getStyle() == style ? f : f.deriveFont(style);
        }
    }

    /** קטע קוד: משמאל לימין, בגופן קוד, על רקע אפור בהיר */
    static JComponent codeBlock(String code, float size) {
        JBTextArea a = new JBTextArea(code);
        a.setEditable(false);
        a.setFont(mono(size));
        a.setForeground(INK);
        a.setBackground(SUNK);
        a.setBorder(JBUI.Borders.empty(6, 10));
        a.setComponentOrientation(ComponentOrientation.LEFT_TO_RIGHT);
        a.putClientProperty(LTR, Boolean.TRUE);
        return a;
    }

    /** מסמן רכיב שהוא קוד — משמאל לימין בכוונה (הבדיקות של "הכול מימין לשמאל" מדלגות עליו) */
    static final String LTR = "dsviz.ltr";

    /**
     * טקסט של סעיף (או כל טקסט מהתרגיל): פסקאות בעברית מימין לשמאל, ושורות של קוד — בלוק משמאל לימין בגופן קוד.
     */
    static JPanel prose(String text, int width, float size) {
        JPanel col = new JPanel();
        col.setLayout(new BoxLayout(col, BoxLayout.Y_AXIS));
        col.setOpaque(false);
        List<String> para = new ArrayList<String>(), code = new ArrayList<String>();
        for (String line : text.replace("\r", "").split("\n", -1)) {
            boolean c = codeLine(line);
            if (c) {
                if (!para.isEmpty()) addProse(col, para, width, size);
                code.add(line.replace("\t", "    "));
            } else {
                if (!code.isEmpty()) addCode(col, code, size);
                if (!line.trim().isEmpty() || !para.isEmpty()) para.add(line);
            }
        }
        if (!para.isEmpty()) addProse(col, para, width, size);
        if (!code.isEmpty()) addCode(col, code, size);
        col.setComponentOrientation(ComponentOrientation.RIGHT_TO_LEFT);
        for (Component k : col.getComponents()) if (k instanceof JComponent && ((JComponent) k).getClientProperty(LTR) != null) k.setComponentOrientation(ComponentOrientation.LEFT_TO_RIGHT);
        return col;
    }

    private static void addProse(JPanel col, List<String> lines, int width, float size) {
        while (!lines.isEmpty() && lines.get(lines.size() - 1).trim().isEmpty()) lines.remove(lines.size() - 1);
        if (!lines.isEmpty()) {
            if (col.getComponentCount() > 0) col.add(javax.swing.Box.createVerticalStrut(JBUI.scale(6)));
            JTextPane t = text(String.join("\n", lines), width, size, INK);
            t.setAlignmentX(Component.RIGHT_ALIGNMENT);
            col.add(t);
        }
        lines.clear();
    }

    private static void addCode(JPanel col, List<String> lines, float size) {
        int indent = Integer.MAX_VALUE;
        for (String l : lines) if (!l.trim().isEmpty()) indent = Math.min(indent, l.length() - l.replaceAll("^\\s+", "").length());
        StringBuilder b = new StringBuilder();
        for (String l : lines) b.append(b.length() > 0 ? "\n" : "").append(l.length() >= indent ? l.substring(indent) : l.trim());
        if (col.getComponentCount() > 0) col.add(javax.swing.Box.createVerticalStrut(JBUI.scale(6)));
        JComponent c = codeBlock(b.toString(), size - 0.5f);
        c.setAlignmentX(Component.RIGHT_ALIGNMENT);
        JPanel wrap = new JPanel(new BorderLayout());
        wrap.setOpaque(false);
        wrap.setBorder(new Round(LINE, 4, 0, 0));
        wrap.add(c, BorderLayout.CENTER);
        wrap.putClientProperty(LTR, Boolean.TRUE);
        wrap.setAlignmentX(Component.RIGHT_ALIGNMENT);
        col.add(wrap);
        lines.clear();
    }

    /** כל הרכיבים מימין לשמאל, חוץ מאלה שמסומנים כקוד (LTR) */
    static void rtl(Component root) {
        root.setComponentOrientation(ComponentOrientation.RIGHT_TO_LEFT);
        if (root instanceof JComponent && ((JComponent) root).getClientProperty(LTR) != null) {
            root.applyComponentOrientation(ComponentOrientation.LEFT_TO_RIGHT);
            return;
        }
        if (root instanceof java.awt.Container) for (Component k : ((java.awt.Container) root).getComponents()) rtl(k);
    }

    // ------------------------------------------------------------------ הבסיס לחלונות

    /**
     * חלון של DSViz במראה החדש: לוח לבן, מימין לשמאל, והכפתורים שלנו — הראשי (OK) מימין והשאר משמאלו, בצד של סוף השורה (משמאל),
     * כמו בחלונות בעברית. הכפתורים קשורים ל-Actions של DialogWrapper, אז בדיקת התקינות, Enter ו-Esc עובדים כרגיל.
     */
    abstract static class Dialog extends DialogWrapper {
        private JPanel footer;

        Dialog(Project project) {
            super(project, true);
        }

        @Override
        protected JComponent createSouthPanel() {
            footer = panel(new FlowLayout(FlowLayout.TRAILING, JBUI.scale(8), 0));
            footer.setBorder(JBUI.Borders.empty(4, 20, 16, 20));
            fillFooter();
            return footer;
        }

        /** בונה מחדש את הכפתורים (כשהם משתנים — למשל בחלון שליחת תרגיל, בזמן הבנייה) */
        void fillFooter() {
            if (footer == null) return;
            footer.removeAll();
            Action[] actions = createActions();
            Action ok = getOKAction();
            Button primary = null;
            for (Action a : actions) {
                if (a == null) continue;
                boolean isPrimary = a == ok || Boolean.TRUE.equals(a.getValue(DEFAULT_ACTION));
                Button b = new Button(a, isPrimary);
                if (isPrimary && primary == null) primary = b;
                footer.add(b);
            }
            // הראשי ראשון (מימין)
            if (primary != null) {
                footer.remove(primary);
                footer.add(primary, 0);
            }
            footer.setComponentOrientation(ComponentOrientation.RIGHT_TO_LEFT);
            if (getRootPane() != null) getRootPane().setDefaultButton(primary);
            footer.revalidate();
            footer.repaint();
        }

        JPanel footer() {
            return footer;
        }

        @Override
        protected javax.swing.border.Border createContentPaneBorder() {
            return JBUI.Borders.empty();
        }

        @Override
        protected void init() {
            super.init();
            java.awt.Container c = getContentPane();
            if (c instanceof JComponent) {
                c.setBackground(PANEL);
                ((JComponent) c).setOpaque(true);
            }
            if (c != null) c.setComponentOrientation(ComponentOrientation.RIGHT_TO_LEFT);
            if (getRootPane() != null) getRootPane().setComponentOrientation(ComponentOrientation.RIGHT_TO_LEFT);
        }

        /** תוכן של חלון קיים (טופס): עם הריווח של החלון, והלוחות שבו שקופים — כדי שהרקע הלבן יראה */
        static JPanel wrap(JComponent content) {
            JPanel p = body(new BorderLayout());
            clear(content);
            p.add(content, BorderLayout.CENTER);
            p.setComponentOrientation(content.getComponentOrientation());
            return p;
        }

        private static void clear(Component c) {
            if (c instanceof JPanel) ((JPanel) c).setOpaque(false);
            if (c instanceof java.awt.Container) for (Component k : ((java.awt.Container) c).getComponents()) clear(k);
        }

        /** הלוח הראשי עם ריווח של החלון (20px מהצדדים) */
        static JPanel body(java.awt.LayoutManager layout) {
            JPanel p = panel(layout);
            p.setBorder(JBUI.Borders.empty(18, 20, 12, 20));
            return p;
        }
    }

    /** קו דק בכחול בראש הלוח — התקדמות (0..1); ‎-1 — רק הקו */
    static final class TopLine extends JComponent {
        private double fraction = -1;
        private Color color = ACCENT;

        TopLine() {
            setPreferredSize(new Dimension(1, JBUI.scale(3)));
            setOpaque(false);
        }

        void set(double fraction, Color color) {
            this.fraction = fraction;
            this.color = color;
            repaint();
        }

        double fraction() {
            return fraction;
        }

        @Override
        protected void paintComponent(Graphics g) {
            int w = getWidth(), h = getHeight();
            g.setColor(SUNK);
            g.fillRect(0, 0, w, h);
            g.setColor(color);
            int fill = fraction < 0 ? w : (int) Math.round(w * Math.max(0.04, Math.min(1, fraction)));
            // מימין לשמאל: מתמלא מימין
            g.fillRect(w - fill, 0, fill, h);
        }
    }

    /** קו מפריד דק */
    static JComponent rule() {
        JPanel p = new JPanel();
        p.setBackground(LINE);
        p.setOpaque(true);
        p.setPreferredSize(new Dimension(1, 1));
        p.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
        p.setBorder(BorderFactory.createEmptyBorder());
        return p;
    }
}
