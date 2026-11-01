package il.lamerhav.dsviz;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.ComponentOrientation;
import java.awt.Container;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Window;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextPane;
import javax.swing.KeyStroke;
import javax.swing.SwingConstants;
import javax.swing.text.BadLocationException;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;

import unit4.collectionsLib.Theme;

/**
 * החלון היחיד להודעות של הכלים של Visual Studio (שליחת תרגיל, תרגיל חדש, הוסף מבנה נתונים, הדרכה) — במקום JOptionPane,
 * בעיצוב של האתר (site/DESIGN.md): לוח לבן שטוח, קו של 1px, פינות קטנות, כפתור ראשי כחול, מימין לשמאל.
 *   - כל טקסט מתחיל ב-RLM, כך שגם שורה שמתחילה באנגלית (Ex01_Stacks · 3 סעיפים) מוצגת מימין לשמאל;
 *   - קוד בתוך משפט (count(s), Stack<int>) — בגופן mono ומשמאל לימין; שורות קוד — בלוק נפרד משמאל לימין;
 *   - הכפתורים מימין לשמאל: הראשי ראשון (מימין), Enter לוחץ עליו, Esc סוגר.
 * יש בו גם את אבני הבניין (כרטיס, כפתורים, טקסט, כרטיס שגיאה) שחלון "שליחת תרגיל" (VsSubmit) בנוי מהן.
 * הבדיקה cs-tests.sh נכשלת אם יש JOptionPane בקוד של הכלים מחוץ לקובץ הזה.
 */
public final class RtlDialog {
    private RtlDialog() {}

    public enum Kind { INFO, OK, WARN, ERROR }

    // הצבעים של האתר (site/assets/site.css)
    public static final Color ACCENT = new Color(0x2F6FED), ACCENT_2 = new Color(0x1F56C9), GREEN = new Color(0x2E9E5B), RED = new Color(0xC4302B),
            AMBER = new Color(0xE8A100), AMBER_BG = new Color(0xFCF4E0), LINE = new Color(0xDFE3EA), LINE_2 = new Color(0xCDD2DA),
            PAGE = new Color(0xFAFAFB), PANEL = Color.WHITE, SUNK = new Color(0xF2F3F6), INK = new Color(0x0E1116), INK_2 = new Color(0x3B4250),
            MUTED = new Color(0x646C7A);

    public static final char RLM = '\u200F';
    /** "←" בין שתי מילים באנגלית ("Ex01 ← Set as Startup Project"): עם RLM משני הצדדים, כך שהוא מצביע קדימה גם בטקסט מימין לשמאל */
    public static final String ARROW = "\u200F\u2190\u200F";
    private static final char LRE = '\u202A', PDF = '\u202C';

    /** מוסיף RLM בהתחלה (פעם אחת) */
    public static String rtl(String s) {
        String t = s == null ? "" : s;
        return t.isEmpty() || t.charAt(0) == RLM ? t : RLM + t;
    }

    // ------------------------------------------------------------------ גופנים

    static Font ui() {
        if (Theme.ui == null) Theme.install();
        return Theme.ui;
    }

    static Font bold() {
        if (Theme.uiBold == null) Theme.install();
        return Theme.uiBold;
    }

    static Font small() {
        if (Theme.small == null) Theme.install();
        return Theme.small;
    }

    public static Font mono(float size) {
        if (Theme.mono == null) Theme.install();
        return (Theme.mono != null ? Theme.mono : new Font(Font.MONOSPACED, Font.PLAIN, 12)).deriveFont(size);
    }

    // ------------------------------------------------------------------ ההודעות

    /** הודעה עם כפתור אחד ("אישור") */
    public static void show(Component parent, String title, Kind kind, String heading, String text) {
        ask(parent, title, kind, heading, text, "אישור");
    }

    /** הודעה עם כפתורים (הראשון — הראשי). מחזיר את האינדקס של הכפתור שנלחץ, או -1 (נסגר) */
    public static int ask(Component parent, String title, Kind kind, String heading, String text, String... buttons) {
        JPanel body = new JPanel();
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        body.add(heading(kind, heading));
        if (text != null && !text.trim().isEmpty()) {
            body.add(gap(8));
            body.add(rich(text, 420, INK_2, true));
        }
        return dialog(parent, title, body, buttons);
    }

    /** רשימת שגיאות קומפילציה: כרטיס לכל שגיאה ("קובץ · שורה", ההסבר בעברית, וההודעה המקורית משמאל לימין) */
    public static void errors(Component parent, String title, String heading, List<String> errors) {
        JPanel body = new JPanel();
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        body.add(heading(Kind.ERROR, heading));
        for (String e : errors) {
            body.add(gap(8));
            body.add(errorCard(e, 440));
        }
        dialog(parent, title, body, "סגירה");
    }

    /** החלון עצמו: התוכן, ושורת כפתורים למטה (רקע אפור בהיר, קו מעליה) */
    static int dialog(Component parent, String title, JComponent body, String... buttons) {
        Window owner = parent == null ? null : parent instanceof Window ? (Window) parent : javax.swing.SwingUtilities.getWindowAncestor(parent);
        final JDialog d = owner instanceof java.awt.Dialog ? new JDialog((java.awt.Dialog) owner, title, true)
                : new JDialog(owner instanceof Frame ? (Frame) owner : null, title, true);
        final int[] res = { -1 };
        JPanel content = new JPanel(new BorderLayout());
        content.setBackground(PANEL);
        body.setBorder(BorderFactory.createEmptyBorder(18, 20, 16, 20));
        body.setOpaque(false);
        content.add(body, BorderLayout.CENTER);
        JPanel foot = footer();
        JButton first = null;
        for (int i = 0; i < buttons.length; i++) {
            final int k = i;
            JButton b = i == 0 ? primary(buttons[i]) : secondary(buttons[i]);
            b.addActionListener(e -> {
                res[0] = k;
                d.dispose();
            });
            foot.add(b);
            if (first == null) first = b;
        }
        content.add(foot, BorderLayout.SOUTH);
        d.setContentPane(content);
        rtl(content);
        if (first != null) d.getRootPane().setDefaultButton(first);
        d.getRootPane().registerKeyboardAction(e -> d.dispose(), KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
        d.setResizable(false);
        d.pack();
        d.setLocationRelativeTo(owner);
        d.setVisible(true);
        return res[0];
    }

    /** שורת הכפתורים בתחתית: מימין לשמאל, הראשי ראשון */
    public static JPanel footer() {
        JPanel foot = new JPanel(new FlowLayout(FlowLayout.LEADING, 8, 10)) {
            @Override
            protected void paintComponent(Graphics g) {
                g.setColor(PAGE);
                g.fillRect(0, 0, getWidth(), getHeight());
                g.setColor(LINE);
                g.drawLine(0, 0, getWidth(), 0);
            }
        };
        foot.setBorder(BorderFactory.createEmptyBorder(0, 12, 0, 12));
        foot.setComponentOrientation(ComponentOrientation.RIGHT_TO_LEFT);
        return foot;
    }

    // ------------------------------------------------------------------ אבני בניין

    /** כותרת עם נקודה בצבע של סוג ההודעה */
    public static JComponent heading(Kind kind, String text) {
        JPanel p = new JPanel(new BorderLayout(8, 0));
        p.setOpaque(false);
        final Color c = kind == Kind.ERROR ? RED : kind == Kind.WARN ? AMBER : kind == Kind.OK ? GREEN : ACCENT;
        JComponent dot = new JComponent() {
            @Override
            protected void paintComponent(Graphics g0) {
                Graphics2D g = (Graphics2D) g0.create();
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setColor(c);
                int s = 8;
                g.fillOval((getWidth() - s) / 2, (getHeight() - s) / 2, s, s);
                g.dispose();
            }
        };
        dot.setPreferredSize(new Dimension(10, 10));
        JLabel t = label(text, bold().deriveFont(16f), INK);
        p.add(dot, BorderLayout.LINE_START);
        p.add(t, BorderLayout.CENTER);
        p.setAlignmentX(Component.RIGHT_ALIGNMENT);
        p.setMaximumSize(new Dimension(Integer.MAX_VALUE, p.getPreferredSize().height));
        return p;
    }

    /** תווית מימין לשמאל (עם RLM) */
    public static JLabel label(String text, Font f, Color c) {
        JLabel l = new JLabel(rtl(text));
        l.setFont(f);
        l.setForeground(c);
        l.setHorizontalAlignment(SwingConstants.RIGHT);
        l.setAlignmentX(Component.RIGHT_ALIGNMENT);
        return l;
    }

    public static JButton primary(String text) {
        JButton b = new JButton(text);
        b.putClientProperty("FlatLaf.style", "background:#2F6FED; foreground:#ffffff; arc:8; borderWidth:0; focusWidth:0; innerFocusWidth:0; "
                + "hoverBackground:#1F56C9; pressedBackground:#1A4BB0; focusedBackground:#2F6FED; disabledBackground:#B9C8EE; disabledText:#ffffff");
        b.setBackground(ACCENT);
        b.setForeground(Color.WHITE);
        b.setFont(bold());
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return b;
    }

    public static JButton secondary(String text) {
        JButton b = new JButton(text);
        b.putClientProperty("FlatLaf.style", "background:#ffffff; foreground:#0E1116; arc:8; borderColor:#CDD2DA; focusWidth:0; innerFocusWidth:0; hoverBackground:#F2F3F6; pressedBackground:#E6E8EC");
        b.setFont(ui());
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return b;
    }

    /** כפתור שנראה כמו קישור ("הצגה") */
    public static JButton link(String text) {
        JButton b = new JButton(text);
        b.putClientProperty("FlatLaf.style", "background:null; foreground:#1F56C9; borderWidth:0; focusWidth:0; innerFocusWidth:0; margin:0,2,0,2; hoverBackground:null; pressedBackground:null");
        b.setBorderPainted(false);
        b.setContentAreaFilled(false);
        b.setFocusPainted(false);
        b.setOpaque(false);
        b.setForeground(ACCENT_2);
        b.setFont(bold().deriveFont(Font.BOLD, ui().getSize2D()));
        b.setMargin(new java.awt.Insets(0, 2, 0, 2));
        b.setBorder(BorderFactory.createEmptyBorder(0, 2, 0, 2));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return b;
    }

    /** לוח לבן עם קו של 1px ופינות קטנות */
    public static JPanel card(JComponent inner, int padY, int padX) {
        JPanel p = new Panel(new BorderLayout(), PANEL, LINE);
        p.setBorder(BorderFactory.createEmptyBorder(padY, padX, padY, padX));
        p.add(inner, BorderLayout.CENTER);
        p.setAlignmentX(Component.RIGHT_ALIGNMENT);
        return p;
    }

    /** לוח שצובע את עצמו: רקע, קו, פינות של 6px */
    public static class Panel extends JPanel {
        Color fill, line;

        public Panel(java.awt.LayoutManager l, Color fill, Color line) {
            super(l);
            this.fill = fill;
            this.line = line;
            setOpaque(false);
        }

        /** בעמודה (BoxLayout) — בגובה של התוכן, לא נמתח */
        @Override
        public Dimension getMaximumSize() {
            return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(fill);
            g.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 12, 12);
            if (line != null) {
                g.setColor(line);
                g.setStroke(new BasicStroke(1f));
                g.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 12, 12);
            }
            g.dispose();
        }
    }

    public static Component gap(int h) {
        JPanel p = new JPanel();
        p.setOpaque(false);
        p.setPreferredSize(new Dimension(1, h));
        p.setMaximumSize(new Dimension(Integer.MAX_VALUE, h));
        p.setAlignmentX(Component.RIGHT_ALIGNMENT);
        return p;
    }

    // ------------------------------------------------------------------ טקסט בעברית עם קוד

    private static final Pattern HEB = Pattern.compile("[\\u0590-\\u05FF]");
    private static final Pattern CODE_LINE = Pattern.compile("[;{}=]|\\(\\s*\\)|^\\s*(public|private|static|int|void|boolean|bool|return|if|while|for|class|new|string|String)\\b");

    static boolean codeLine(String l) {
        return !HEB.matcher(l).find() && l.matches(".*[A-Za-z].*") && CODE_LINE.matcher(l).find();
    }

    /**
     * משפט → קטעים: {טקסט, "code"} לקוד (מה שבין `...`, וכל קטע בלי עברית שיש בו אות באנגלית, בלי פיסוק בקצוות), {טקסט, null} לשאר.
     */
    static List<String[]> segments(String text) {
        return segments(text, false);
    }

    /** strict — רק מה שנראה כמו קוד (סוגריים, ;, =, _, קובץ.סיומת), ולא סתם מילה באנגלית (Visual Studio, Reload) */
    static List<String[]> segments(String text, boolean strict) {
        List<String[]> out = new ArrayList<String[]>();
        String[] parts = text.split("(?=`)|(?<=`)");
        boolean inTick = false;
        StringBuilder tick = new StringBuilder();
        for (String p : parts) {
            if (p.equals("`")) {
                if (inTick) out.add(new String[] { tick.toString(), "code" });
                tick.setLength(0);
                inTick = !inTick;
                continue;
            }
            if (inTick) {
                tick.append(p);
                continue;
            }
            Matcher m = Pattern.compile("[^\\u0590-\\u05FF]+").matcher(p);
            int last = 0;
            while (m.find()) {
                if (m.start() > last) out.add(new String[] { p.substring(last, m.start()), null });
                last = m.end();
                String seg = m.group();
                boolean letters = seg.matches("(?s).*[A-Za-z].*");
                boolean list = !letters && seg.matches("(?s).*(\\d\\s*,\\s*\\d|[\\[(]\\s*\\d).*");   // [1, 2, 3] — רשימת מספרים: רק משמאל לימין
                if (!letters && !list) {
                    out.add(new String[] { seg, null });
                    continue;
                }
                int a = 0, b = seg.length();
                while (a < b && " \t-,.:!?\"'(".indexOf(seg.charAt(a)) >= 0 && !(seg.charAt(a) == '(' && seg.indexOf(')', a) > 0)) a++;
                while (b > a && " \t,.:!?\"'".indexOf(seg.charAt(b - 1)) >= 0) b--;
                String core = seg.substring(a, b);
                while (core.endsWith(")") && count(core, '(') < count(core, ')')) {
                    core = core.substring(0, core.length() - 1);
                    b--;
                }
                if (a > 0) out.add(new String[] { seg.substring(0, a), null });
                boolean isCode = !core.isEmpty() && !list && (!strict || core.matches(".*[()<>\\[\\]{};=_].*|.*\\w\\.\\w.*"));
                if (!core.isEmpty()) out.add(new String[] { core, isCode ? "code" : list ? "ltr" : null });
                if (b < seg.length()) out.add(new String[] { seg.substring(b), null });
            }
            if (last < p.length()) out.add(new String[] { p.substring(last), null });
        }
        if (inTick && tick.length() > 0) out.add(new String[] { "`" + tick, null });
        return out;
    }

    private static int count(String s, char c) {
        int n = 0;
        for (int i = 0; i < s.length(); i++) if (s.charAt(i) == c) n++;
        return n;
    }

    /**
     * טקסט עם שורות קוד: פסקאות בעברית (מימין לשמאל, קוד בתוכן ב-mono משמאל לימין), ושורות קוד רצופות — בלוק משמאל לימין.
     * width — הרוחב לשבירת שורות.
     */
    public static JComponent rich(String text, int width, Color color) {
        return rich(text, width, color, false);
    }

    /** strict — טקסט של הממשק (לא של התרגיל): רק מה שנראה כמו קוד מקבל mono */
    public static JComponent rich(String text, int width, Color color, boolean strict) {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setOpaque(false);
        p.setAlignmentX(Component.RIGHT_ALIGNMENT);
        List<String> lines = new ArrayList<String>(Arrays.asList(String.valueOf(text).replace("\r", "").replaceAll("```\\w*", "").split("\n", -1)));
        List<String> code = new ArrayList<String>();
        StringBuilder para = new StringBuilder();
        for (String l : lines) {
            boolean more = !code.isEmpty() && !HEB.matcher(l).find() && (l.trim().isEmpty() || l.startsWith(" ") || l.startsWith("\t") || l.matches("[\\s{}()\\[\\];,]*"));
            if (codeLine(l) || more) {
                flushPara(p, para, width, color, strict);
                code.add(l);
                continue;
            }
            flushCode(p, code, width);
            if (l.trim().isEmpty()) {
                flushPara(p, para, width, color, strict);
                continue;
            }
            if (para.length() > 0) para.append('\n');
            para.append(l.trim());
        }
        flushCode(p, code, width);
        flushPara(p, para, width, color, strict);
        if (p.getComponentCount() > 0 && p.getComponent(0) instanceof JComponent && "gap".equals(((JComponent) p.getComponent(0)).getName())) p.remove(0);
        return p;
    }

    private static void flushPara(JPanel p, StringBuilder para, int width, Color color, boolean strict) {
        if (para.length() == 0) return;
        if (p.getComponentCount() > 0) p.add(named(gap(6)));
        p.add(paragraph(para.toString(), ui(), color, width, strict));
        para.setLength(0);
    }

    private static void flushCode(JPanel p, List<String> code, int width) {
        while (!code.isEmpty() && code.get(code.size() - 1).trim().isEmpty()) code.remove(code.size() - 1);
        if (code.isEmpty()) return;
        int pad = Integer.MAX_VALUE;
        for (String l : code) if (!l.trim().isEmpty()) pad = Math.min(pad, l.length() - l.replaceAll("^\\s+", "").length());
        StringBuilder b = new StringBuilder();
        for (String l : code) b.append(b.length() > 0 ? "\n" : "").append(l.length() >= pad ? l.substring(pad) : l.trim());
        if (p.getComponentCount() > 0) p.add(named(gap(6)));
        p.add(codeBlock(b.toString(), width));
        code.clear();
    }

    private static Component named(Component c) {
        c.setName("gap");
        return c;
    }

    /** פסקה מימין לשמאל, שבירת שורות לפי width, וקוד בתוכה ב-mono משמאל לימין */
    public static JTextPane paragraph(String text, Font font, Color color, int width) {
        return paragraph(text, font, color, width, false);
    }

    public static JTextPane paragraph(String text, Font font, Color color, int width, boolean strict) {
        JTextPane t = new JTextPane();
        t.setFocusable(false);
        t.setEditable(false);
        t.setOpaque(false);
        t.setBorder(BorderFactory.createEmptyBorder());
        t.setComponentOrientation(ComponentOrientation.RIGHT_TO_LEFT);
        t.putClientProperty("dsviz.label", Boolean.TRUE);   // SwingScript: מופיע בצילום כ-Label
        StyledDocument doc = t.getStyledDocument();
        SimpleAttributeSet base = new SimpleAttributeSet(), code = new SimpleAttributeSet(), par = new SimpleAttributeSet();
        StyleConstants.setFontFamily(base, font.getFamily());
        StyleConstants.setFontSize(base, font.getSize());
        StyleConstants.setBold(base, font.isBold());
        StyleConstants.setForeground(base, color);
        Font m = mono(font.getSize2D() - 1f);
        StyleConstants.setFontFamily(code, m.getFamily());
        StyleConstants.setFontSize(code, m.getSize());
        StyleConstants.setForeground(code, INK);
        StyleConstants.setBackground(code, SUNK);
        StyleConstants.setAlignment(par, StyleConstants.ALIGN_RIGHT);
        StyleConstants.setLineSpacing(par, 0.15f);
        try {
            doc.insertString(0, String.valueOf(RLM), base);
            for (String[] s : segments(text, strict)) {
                if (s[1] == null) doc.insertString(doc.getLength(), s[0], base);
                else if (s[1].equals("ltr")) doc.insertString(doc.getLength(), LRE + s[0] + PDF + RLM, base);
                else doc.insertString(doc.getLength(), LRE + s[0] + PDF + RLM, code);
            }
        } catch (BadLocationException ignored) {
            // לא קורה
        }
        doc.setParagraphAttributes(0, doc.getLength(), par, false);
        t.setAlignmentX(Component.RIGHT_ALIGNMENT);
        fit(t, width);
        return t;
    }

    /** בלוק קוד: משמאל לימין, mono, רקע אפור בהיר וקו */
    public static JComponent codeBlock(String code, int width) {
        JTextPane t = new JTextPane();
        t.setEditable(false);
        t.setFocusable(false);
        t.setOpaque(false);
        t.setComponentOrientation(ComponentOrientation.LEFT_TO_RIGHT);
        t.setFont(mono(12.5f));
        t.setForeground(INK);
        t.setText(code);
        t.putClientProperty("dsviz.label", Boolean.TRUE);
        t.setBorder(BorderFactory.createEmptyBorder());
        JPanel box = new Panel(new BorderLayout(), SUNK, LINE);
        box.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
        box.add(t, BorderLayout.CENTER);
        box.putClientProperty("ltr", Boolean.TRUE);
        box.setAlignmentX(Component.RIGHT_ALIGNMENT);
        fit(t, width - 22);
        Dimension d = new Dimension(width, t.getPreferredSize().height + 16);
        box.setPreferredSize(d);
        box.setMaximumSize(new Dimension(Integer.MAX_VALUE, d.height));
        return box;
    }

    /** גובה לפי הרוחב (שבירת שורות) */
    static void fit(JTextPane t, int width) {
        t.setSize(new Dimension(width, Short.MAX_VALUE));
        Dimension d = new Dimension(width, t.getPreferredSize().height);
        t.setPreferredSize(d);
        t.setMaximumSize(new Dimension(Integer.MAX_VALUE, d.height));
    }

    // ------------------------------------------------------------------ שגיאות קומפילציה

    /** "Program.cs:12: ; expected   [CS1002]" / "A.java:3: cannot find symbol" → {קובץ, שורה, הודעה, קוד} (או null) */
    public static String[] parseError(String s) {
        Matcher m = Pattern.compile("^(.+?):(\\d+):\\s*(.*?)(?:\\s+\\[(CS\\d+)\\])?\\s*$").matcher(s == null ? "" : s.trim());
        return m.matches() ? new String[] { m.group(1), m.group(2), m.group(3), m.group(4) == null ? "" : m.group(4) } : null;
    }

    private static final String[][] CSC = {
        { "CS1002", "חסר ; בסוף הפקודה" }, { "CS1513", "חסר סוגר }" }, { "CS1514", "חסר סוגר {" }, { "CS1026", "חסר סוגר )" },
        { "CS1003", "חסר סימן בשורה (פסיק, סוגר או ;)" }, { "CS1525", "ביטוי לא חוקי. בדרך כלל חסר סוגר או ; בשורה הקודמת" },
        { "CS1010", "חסרות מירכאות \" בסוף המחרוזת" }, { "CS0103", "השם לא מוכר. בדקו את האיות (אותיות גדולות וקטנות) ושהמשתנה הוגדר" },
        { "CS0246", "הטיפוס לא מוכר. בדקו את האיות, ושיש using מתאים" }, { "CS0029", "אי התאמה בטיפוסים: אי אפשר להכניס את הערך הזה למשתנה מהטיפוס הזה" },
        { "CS0266", "המרה שעלולה לאבד מידע. צריך המרה מפורשת, למשל (int)" }, { "CS0161", "לא בכל המסלולים יש return. הפעולה צריכה להחזיר ערך בכל מסלול" },
        { "CS0165", "המשתנה עוד לא קיבל ערך. צריך לתת לו ערך התחלתי" }, { "CS0128", "המשתנה כבר מוגדר בפעולה הזאת. בחרו שם אחר" },
        { "CS0120", "אי אפשר לקרוא לפעולה (שאינה static) מתוך פעולה static. הוסיפו static לכותרת שלה" },
        { "CS1061", "לטיפוס הזה אין פעולה או מאפיין בשם הזה. בדקו את האיות (למשל Push, Pop, IsEmpty)" },
        { "CS1501", "הפעולה נקראה עם מספר פרמטרים לא מתאים" }, { "CS1503", "הפעולה נקראה עם פרמטר מטיפוס לא מתאים" },
        { "CS0122", "אין גישה: הוא private במחלקה. ניגשים דרך פעולה (Get...)" }, { "CS0019", "אי אפשר להפעיל את הפעולה הזאת על הטיפוסים האלה" },
        { "CS0017", "יש בפרויקט יותר מ-Main אחד" }, { "CS0136", "כבר יש משתנה בשם הזה בפעולה. בחרו שם אחר" },
        { "CS0201", "זו לא פקודה. אולי חסר = או קריאה לפעולה" },
    };
    private static final String[][] JAVAC = {
        { "missing return statement", "חסרה הוראת return: הפעולה צריכה להחזיר ערך בכל מסלול (גם אחרי לולאה או if)" },
        { "';' expected", "חסר ; בסוף הפקודה" }, { "'(.+)' expected", "חסר $1" },
        { "cannot find symbol", "השם לא מוכר. בדקו את האיות (אותיות גדולות וקטנות), שהמשתנה הוגדר, ושיש import" },
        { "incompatible types: possible lossy conversion from (.+) to (.+)", "המרה מ-$1 ל-$2 עלולה לאבד מידע (צריך המרה מפורשת)" },
        { "incompatible types: (.+) cannot be converted to (.+)", "אי התאמה בטיפוסים: אי אפשר להכניס $1 למשתנה מטיפוס $2" },
        { "variable (\\w+) might not have been initialized", "המשתנה $1 עוד לא קיבל ערך. צריך לתת לו ערך התחלתי" },
        { "unreachable statement", "הפקודה הזאת אף פעם לא תתבצע (היא אחרי return או break)" },
        { "reached end of file while parsing", "חסר סוגר } בסוף הקובץ" },
        { "illegal start of expression", "ביטוי לא חוקי. בדרך כלל חסר סוגר או ; בשורה הקודמת" },
        { "not a statement", "זו לא פקודה. אולי חסר = או קריאה לפעולה" },
    };

    /** ההסבר בעברית להודעה של הקומפיילר (C# לפי מספר השגיאה, javac לפי הטקסט), או "" */
    public static String explain(String message, String code) {
        if (code != null) for (String[] r : CSC) if (r[0].equals(code)) return r[1];
        String m = message == null ? "" : message.trim();
        for (String[] r : JAVAC) {
            Matcher x = Pattern.compile("^" + r[0] + "$").matcher(m);
            if (x.matches()) {
                String he = r[1];
                for (int i = 1; i <= x.groupCount(); i++) he = he.replace("$" + i, x.group(i));
                return he;
            }
        }
        return "";
    }

    /** כרטיס לשגיאה: "StackTargil.cs · שורה 12", ההסבר בעברית, וההודעה המקורית (mono, משמאל לימין) */
    public static JComponent errorCard(String error, int width) {
        String[] e = parseError(error);
        JPanel in = new JPanel();
        in.setLayout(new BoxLayout(in, BoxLayout.Y_AXIS));
        in.setOpaque(false);
        if (e == null) {
            in.add(paragraph(error, ui(), INK, width - 24));
            return card(in, 8, 12);
        }
        in.add(label(LRE + e[0] + PDF + " · שורה " + e[1], bold().deriveFont(Font.BOLD, small().getSize2D()), ACCENT_2));
        String he = explain(e[2], e[3]);
        in.add(gap(2));
        in.add(paragraph(he.isEmpty() ? e[2] : he, ui(), INK, width - 24));
        if (!he.isEmpty()) {
            JLabel orig = new JLabel(e[2] + (e[3].isEmpty() ? "" : "  [" + e[3] + "]"));
            orig.setFont(mono(11.5f));
            orig.setForeground(MUTED);
            orig.setComponentOrientation(ComponentOrientation.LEFT_TO_RIGHT);
            orig.setHorizontalAlignment(SwingConstants.LEFT);
            JPanel row = new JPanel(new BorderLayout());
            row.setOpaque(false);
            row.add(orig, BorderLayout.WEST);
            row.setAlignmentX(Component.RIGHT_ALIGNMENT);
            row.setMaximumSize(new Dimension(Integer.MAX_VALUE, orig.getPreferredSize().height));
            row.putClientProperty("ltr", Boolean.TRUE);
            in.add(gap(2));
            in.add(row);
        }
        return card(in, 8, 12);
    }

    // ------------------------------------------------------------------ מימין לשמאל

    /** כל הרכיבים מימין לשמאל, חוץ מאלה שמסומנים כ-LTR (קוד) */
    public static void rtl(Container c) {
        c.applyComponentOrientation(ComponentOrientation.RIGHT_TO_LEFT);
        ltrAgain(c);
    }

    private static void ltrAgain(Component c) {
        if (c instanceof JComponent && (((JComponent) c).getClientProperty("ltr") != null)) {
            c.applyComponentOrientation(ComponentOrientation.LEFT_TO_RIGHT);
            return;
        }
        if (c instanceof Container) for (Component k : ((Container) c).getComponents()) ltrAgain(k);
    }
}
