package unit4.collectionsLib;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.ComponentOrientation;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.font.TextLayout;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.DefaultComboBoxModel;
import javax.swing.Icon;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListCellRenderer;
import javax.swing.SwingConstants;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.text.BadLocationException;
import javax.swing.text.Highlighter;
import javax.swing.text.JTextComponent;

import unit4.collectionsLib.Model.Frame;
import unit4.collectionsLib.Model.Kind;
import unit4.collectionsLib.Model.Step;

/**
 * התוכן של הכרטיס "מעקב משתנים" (שורה-שורה):
 * - תמיד המשתנים של הפעולה שרצה, וכותרת קטנה: באיזו פעולה ("בפעולה isEven()", "בפעולה d1.birthday()") —
 *   בצעד היציאה: מה היא מחזירה ולאן חוזרים; פעולה בלי משתנים — "אין משתנים בפעולה הזו" (ולא טבלה ריקה).
 * - כשפעילות כמה פעולות (main ← sumEven ← isEven, או fact(4) ← fact(3) ...): רשימה לבחירת פעולה — וכך רואים את המשתנים
 *   של פעולה שמחכה ("sumEven() מחכה בשורה 29 לתשובה מ-isEven()"), והשורה שבה היא מחכה מסומנת בקוד (כמו CallPaint).
 *   בכל צעד חדש (הבא / הקודם / הפעלה אוטומטית) חוזרים לפעולה שרצה עכשיו — הרשימה מתעדכנת לפי המחסנית של הצעד.
 * הנתונים והטקסטים — FrameViz.
 */
final class VarsPanel extends JPanel {

    private static final Color WAIT_BG = new Color(0xFFF7ED), WAIT_LINE = new Color(0xFDBA74), WAIT_TEXT = new Color(0x9A3412);
    private static final Color BACK_BG = new Color(0xF0FDF4), BACK_LINE = new Color(0x86EFAC), BACK_TEXT = new Color(0x166534);

    private final JLabel sub;
    private final JTextComponent code;
    private final DefaultTableModel model = new DefaultTableModel(new Object[] { "משתנה", "ערך" }, 0) {
        @Override
        public boolean isCellEditable(int r, int c) { return false; }
    };
    private final VarsTable table = new VarsTable(model);
    private final DefaultComboBoxModel<Integer> frames = new DefaultComboBoxModel<Integer>();
    private final JComboBox<Integer> combo = new JComboBox<Integer>(frames);
    private final JPanel strip = new JPanel(new BorderLayout(8, 0));
    private final JLabel stripLabel = new JLabel();
    private final Note note = new Note();
    private final Set<Integer> changedRows = new HashSet<Integer>();

    private Step step;
    private int shown;
    private String fileKey;
    private CallViz.State call;
    private Object codeMark;
    private boolean updating;

    VarsPanel(JLabel sub, JTextComponent code) {
        super(new BorderLayout());
        this.sub = sub;
        this.code = code;
        setOpaque(false);

        table.setFocusable(false);
        table.setRowSelectionAllowed(false);
        table.setFillsViewportHeight(true);
        table.getTableHeader().setReorderingAllowed(false);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_LAST_COLUMN);
        table.getColumnModel().getColumn(0).setPreferredWidth(110);
        table.getColumnModel().getColumn(0).setMaxWidth(220);
        table.getColumnModel().getColumn(1).setPreferredWidth(300);
        table.setDefaultRenderer(Object.class, new Renderer());
        table.setRowHeight(22);
        table.setTableHeader(null);   // קומפקטי: "שם | ערך" בלי כותרת
        JScrollPane scroll = new JScrollPane(table);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.setViewportBorder(null);
        scroll.getViewport().setBackground(Color.WHITE);
        scroll.getVerticalScrollBar().setUnitIncrement(16);

        // הרשימה: הפעולות שפעילות עכשיו — הפנימית (שרצה) למעלה, main למטה, כמו מחסנית
        combo.setFocusable(false);   // החצים במקלדת נשארים "הבא / הקודם"
        combo.setRenderer(new FrameRenderer());
        combo.setMaximumRowCount(FrameViz.MAX_LIST + 1);
        combo.setToolTipText("בוחרים פעולה ורואים את המשתנים שלה ברגע הזה — גם של פעולה שמחכה לתשובה");
        combo.putClientProperty("FlatLaf.style", "arc: 10; padding: 1,4,1,4");
        combo.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                if (updating || step == null) return;
                Object k = combo.getSelectedItem();
                if (!(k instanceof Integer) || (Integer) k < 0) {   // השורה "… עוד קריאות" — לא נבחרת
                    select(shown);
                    return;
                }
                pick((Integer) k);
            }
        });
        stripLabel.setFont(Theme.small);
        stripLabel.setForeground(Theme.MUTED);
        strip.setOpaque(false);
        strip.setBorder(BorderFactory.createEmptyBorder(6, 6, 2, 6));
        strip.add(stripLabel, BorderLayout.LINE_START);
        strip.add(combo, BorderLayout.CENTER);
        strip.setVisible(false);
        note.setVisible(false);

        JPanel north = new JPanel();
        north.setOpaque(false);
        north.setLayout(new BoxLayout(north, BoxLayout.Y_AXIS));
        strip.setAlignmentX(Component.CENTER_ALIGNMENT);
        north.add(strip);
        JPanel noteBox = new JPanel(new BorderLayout());
        noteBox.setOpaque(false);
        noteBox.setBorder(BorderFactory.createEmptyBorder(4, 6, 2, 6));
        noteBox.add(note, BorderLayout.CENTER);
        north.add(noteBox);
        add(north, BorderLayout.NORTH);
        add(scroll, BorderLayout.CENTER);
    }

    // ------------------------------------------------------------ צעד חדש

    /** הצעד שמוצג (call = הקריאה לפעולת עזר שמסומנת בקוד, או null; fileKey = הקובץ שמוצג בקוד) */
    void set(Step s, String fileKey, CallViz.State call) {
        boolean same = s != null && s == step;
        this.step = s;
        this.fileKey = fileKey;
        this.call = call;
        // צעד חדש (הבא / הקודם / הפעלה אוטומטית) — תמיד הפעולה שרצה עכשיו; אותו צעד שמצויר מחדש (זום, החלפת תצוגה) — הבחירה נשמרת
        int k = same && shown > 0 && shown < s.calls.size() && hasFrames(s) ? shown : 0;
        if (TEST_FRAME > 0 && s != null && hasFrames(s) && TEST_FRAME < s.calls.size()) k = TEST_FRAME;
        show(k, false);
    }

    /** לצילומים בלבד (-Ddsviz.varsFrame=1 או DSVIZ_VARS_FRAME=1): בכל צעד מוצגת הפעולה שבמקום הזה ברשימה (1 = מי שקרא) */
    private static final int TEST_FRAME = testFrame();

    private static int testFrame() {
        String v = System.getProperty("dsviz.varsFrame", System.getenv("DSVIZ_VARS_FRAME"));
        try {
            return v == null ? 0 : Integer.parseInt(v.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** בחירה ברשימה */
    private void pick(int k) {
        if (step == null || k < 0 || k >= step.calls.size()) return;
        show(k, true);
    }

    private static boolean hasFrames(Step s) {
        return s.calls.size() >= 2 && s.frameVars != null;
    }

    private void show(int k, boolean byUser) {
        Step s = step;
        shown = k;
        boolean list = s != null && hasFrames(s);
        if (strip.isVisible() != list) {
            strip.setVisible(list);
            revalidate();
        }
        if (list) {
            List<Integer> items = FrameViz.listed(s);
            boolean same = items.size() == frames.getSize();
            for (int i = 0; same && i < items.size(); i++) same = items.get(i).equals(frames.getElementAt(i));
            updating = true;
            try {
                if (!same) {
                    frames.removeAllElements();
                    for (Integer i : items) frames.addElement(i);
                }
                select(k);
            } finally {
                updating = false;
            }
            stripLabel.setText("\u200F" + s.calls.size() + " פעולות פעילות:");
            combo.repaint();
        }
        fillTable(s, k);
        sub.setText(subtitle(s, k));
        String n = s == null ? null : FrameViz.note(s, k);
        note.set(n, k > 0 ? WAIT_BG : BACK_BG, k > 0 ? WAIT_LINE : BACK_LINE, k > 0 ? WAIT_TEXT : BACK_TEXT, k > 0 ? "⏸" : "↩");
        if (note.isVisible() != (n != null)) {
            note.setVisible(n != null);
            revalidate();
        }
        markCode(s, k, byUser);
        repaint();
    }

    private void select(int k) {
        boolean old = updating;
        updating = true;
        try {
            combo.setSelectedItem(Integer.valueOf(k));
        } finally {
            updating = old;
        }
    }

    // ------------------------------------------------------------ הטבלה

    private void fillTable(Step s, int k) {
        model.setRowCount(0);
        changedRows.clear();
        List<String[]> vs = s == null ? null : FrameViz.vars(s, k);
        if (vs == null) {
            table.empty = s != null && k > 0 ? "המשתנים של הפעולה הזו לא נשמרו (היא עמוק מדי במחסנית)" : null;
            return;
        }
        Set<String> changed = FrameViz.changed(s, k);
        SelfViz.Info self = FrameViz.selfOf(s, k);   // גם לפעולה שמחכה (לא רק הרצה) — "this (d1)" ולא סתם "this"
        for (int i = 0; i < vs.size(); i++) {
            String[] v = vs.get(i);
            String name = v[0];
            if (name.equals("this") && self != null && self.self != null && !self.self.isEmpty() && !self.self.equals("this"))
                name = "this (" + self.self + ")";
            model.addRow(new Object[] { name, ltr(v[1]) });
            if (changed.contains(v[0])) changedRows.add(i);
        }
        boolean willHave = vs.isEmpty() && FrameViz.willHaveVars(s, k);   // עוד לא רץ עד ההכרזה — ולא שאין בפעולה הזו משתנים בכלל
        table.empty = vs.isEmpty() ? (willHave ? "אין עדיין משתנים בפעולה הזו" : "אין משתנים בפעולה הזו") : null;
    }

    /** הכותרת הקטנה ליד "מעקב משתנים": באיזו פעולה */
    private static String subtitle(Step s, int k) {
        if (s == null) return " ";
        if (s.calls.isEmpty()) {
            if (s.kind != Kind.END) return " ";
            return "התוכנית הסתיימה · המשתנים של " + lrm(Syntax.csharp ? "Main()" : "main()");
        }
        Frame f = s.calls.get(k);
        String inside = k == 0 ? SelfViz.where(s) : null;   // פעולה של אובייקט: "d1.birthday()"
        String m = inside != null ? "בפעולה " + lrm(inside) : method(f);
        if (k > 0) return m + " · מחכה";
        return s.kind == Kind.RETURN ? m + " · מסתיימת" : m;
    }

    /** "בפעולה isEven()" / "בבנאי Dog()" */
    private static String method(Frame f) {
        boolean ctor = f.method.equals("<init>") || f.method.equals(".ctor");
        return (ctor ? "בבנאי " : "בפעולה ") + lrm(CallViz.name(f));
    }

    // ------------------------------------------------------------ השורה שבה הפעולה שנבחרה מחכה — בקוד

    /**
     * פעולה שמחכה נבחרה: השורה שלה בקוד מסומנת כמו שורה שמחכה בפס הקריאות (CallPaint) — אם היא בקובץ שמוצג,
     * ואם CallPaint לא כבר סימן אותה (פעולת עזר שנקראה לא מ-main). בחירה ברשימה גם גוללת אליה.
     */
    private void markCode(Step s, int k, boolean byUser) {
        Highlighter h = code.getHighlighter();
        if (codeMark != null) {
            h.removeHighlight(codeMark);
            codeMark = null;
        }
        if (s == null || k <= 0 || k >= s.calls.size()) return;
        Frame f = s.calls.get(k);
        if (fileKey == null || !(f.packagePath() + f.file).equals(fileKey) || markedByCalls(f)) return;
        final CallViz.State st = new CallViz.State();   // אותו ציור כמו CallPaint: שורה שמחכה לפעולה שאחריה
        st.phase = CallViz.Phase.INSIDE;
        st.path.add(f);
        st.path.add(s.calls.get(k - 1));
        final String key = fileKey;
        try {
            Rectangle r = CondPaint.lineRect(code, f.line);
            if (r == null) return;
            int at = code.getDocument().getDefaultRootElement().getElement(f.line - 1).getStartOffset();
            codeMark = h.addHighlight(at, at, new Highlighter.HighlightPainter() {
                public void paint(Graphics g, int p0, int p1, Shape bounds, JTextComponent c) {
                    try {
                        CallPaint.background((Graphics2D) g, c, st, key);
                    } catch (RuntimeException e) {
                        // בלי הסימון
                    }
                }
            });
            code.repaint();
            if (byUser && !code.getVisibleRect().contains(r)) {
                Rectangle vis = code.getVisibleRect();
                code.scrollRectToVisible(new Rectangle(vis.x, Math.max(0, r.y - vis.height / 2), 1, vis.height));
            }
        } catch (BadLocationException | RuntimeException e) {
            codeMark = null;
        }
    }

    private boolean markedByCalls(Frame f) {
        if (call == null || call.phase == CallViz.Phase.CALLING || call.phase == CallViz.Phase.RETURNED) return false;
        for (int i = 0; i < call.path.size() - 1; i++) {
            Frame g = call.path.get(i);
            if (g.line == f.line && g.method.equals(f.method) && g.className.equals(f.className)) return true;
        }
        return false;
    }

    // ------------------------------------------------------------ רכיבים

    /** הטבלה — וכשאין משתנים: הודעה באמצע במקום טבלה ריקה */
    private static final class VarsTable extends JTable {
        String empty;

        VarsTable(DefaultTableModel m) {
            super(m);
        }

        @Override
        protected void paintComponent(Graphics g0) {
            super.paintComponent(g0);
            if (empty == null || getRowCount() > 0) return;
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            Rectangle vis = getVisibleRect();
            TextLayout tl = CondPaint.rtl(empty, Theme.ui, g.getFontRenderContext());
            g.setColor(Theme.MUTED);
            float x = vis.x + Math.max(8, (vis.width - tl.getAdvance()) / 2f);
            tl.draw(g, x, vis.y + Math.min(vis.height / 2f, 46) + tl.getAscent() / 2f);
            g.dispose();
        }
    }

    private final class Renderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable t, Object v, boolean sel, boolean focus, int row, int col) {
            super.getTableCellRendererComponent(t, v, false, false, row, col);
            setBackground(changedRows.contains(row) ? Theme.CHANGED : Color.WHITE);
            setForeground(Theme.TEXT);
            setFont(col == 0 ? Theme.mono.deriveFont(Font.BOLD) : Theme.mono);
            setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 10));
            setHorizontalAlignment(col == 0 ? SwingConstants.RIGHT : SwingConstants.LEFT);
            setToolTipText(v == null ? null : String.valueOf(v));
            return this;
        }
    }

    /**
     * שורה ברשימה: נקודה צבעונית, "fact(n = 3)", ומשמאל — השורה, ו"רצה עכשיו" / "מחכה".
     * לתיבה הסגורה (לא הרשימה הפתוחה) — תווית אחת פשוטה (שם + נקודה כאייקון), לא פאנל עם BorderLayout:
     * ל-JComboBox (ועוד יותר עם FlatLaf) יש מקרים שבהם רכיב "הערך הנוכחי" מצטייר בלי doLayout מחדש —
     * ואז שני ילדים ב-BorderLayout יכולים להצטייר זה על זה (במקום בשני קצוות). תווית אחת פותרת את זה תמיד.
     */
    private final class FrameRenderer extends JPanel implements ListCellRenderer<Integer> {
        private final JLabel name = new JLabel(), meta = new JLabel(), closedLabel = new JLabel();

        FrameRenderer() {
            super(new BorderLayout(8, 0));
            name.setFont(Theme.mono.deriveFont(Theme.mono.getSize2D() - 1f));
            meta.setFont(Theme.small);
            add(name, BorderLayout.LINE_START);
            add(meta, BorderLayout.LINE_END);
            setComponentOrientation(ComponentOrientation.RIGHT_TO_LEFT);
            closedLabel.setOpaque(true);
            closedLabel.setBackground(Color.WHITE);
            closedLabel.setFont(Theme.mono.deriveFont(Font.BOLD, Theme.mono.getSize2D() - 1f));
            closedLabel.setHorizontalTextPosition(SwingConstants.LEADING);
            closedLabel.setIconTextGap(8);
            closedLabel.setBorder(BorderFactory.createEmptyBorder(1, 8, 1, 8));
            closedLabel.setComponentOrientation(ComponentOrientation.RIGHT_TO_LEFT);
        }

        public Component getListCellRendererComponent(JList<? extends Integer> list, Integer value, int index, boolean selected, boolean focus) {
            Step s = step;
            int k = value == null ? 0 : value;
            if (index < 0) return closed(s, k);   // התיבה עצמה (לא הרשימה הפתוחה)
            setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
            setOpaque(true);
            setBackground(selected ? Theme.ACCENT_SOFT : Color.WHITE);
            if (s == null || k >= s.calls.size()) {
                name.setText(" ");
                meta.setText("");
                return this;
            }
            if (k < 0) {   // ברקורסיה עמוקה — הקריאות שבאמצע לא ברשימה
                name.setText("\u200F… עוד " + FrameViz.skipped(s) + " קריאות באמצע");
                name.setFont(Theme.small);
                name.setForeground(Theme.MUTED);
                meta.setText("");
                return this;
            }
            name.setFont(Theme.mono.deriveFont(k == shown ? Font.BOLD : Font.PLAIN, Theme.mono.getSize2D() - 1f));
            name.setText(lrm(FrameViz.title(s, k)));
            name.setForeground(Theme.TEXT);
            Frame f = s.calls.get(k);
            boolean ret = k == 0 && s.kind == Kind.RETURN;
            meta.setText("\u200Fשורה " + f.line + " · " + (k > 0 ? "מחכה" : ret ? "מסתיימת" : "רצה עכשיו"));
            meta.setForeground(k > 0 ? WAIT_TEXT : ret ? BACK_TEXT : Theme.ACCENT_DARK);
            return this;
        }

        /** התווית שמוצגת בתיבה הסגורה: רק הפעולה שנבחרה, עם נקודה צבעונית (ה"שורה X · ..." כבר מוצג בכותרת ובהערה) */
        private JLabel closed(Step s, int k) {
            if (s == null || k < 0 || k >= s.calls.size()) {
                closedLabel.setText(" ");
                closedLabel.setIcon(null);
                return closedLabel;
            }
            closedLabel.setText(lrm(FrameViz.title(s, k)));
            closedLabel.setForeground(Theme.TEXT);
            boolean ret = k == 0 && s.kind == Kind.RETURN;
            Color dot = k > 0 ? CallPaint.WAIT : ret ? CallPaint.BACK : Theme.ACCENT;
            closedLabel.setIcon(new Dot(dot));
            return closedLabel;
        }
    }

    /** נקודה קטנה צבעונית — אייקון, לא ציור ידני: כך ה-JComboBox ממקם אותה נכון גם בתיבה הסגורה */
    private static final class Dot implements Icon {
        private final Color color;

        Dot(Color color) {
            this.color = color;
        }

        public void paintIcon(Component c, Graphics g0, int x, int y) {
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(color);
            g.fillOval(x, y + 2, 8, 8);
            g.dispose();
        }

        public int getIconWidth() { return 8; }
        public int getIconHeight() { return 12; }
    }

    /** משפט בתוך מסגרת מעוגלת צבעונית: פעולה שמחכה (כתום) או פעולה שמחזירה (ירוק) */
    private static final class Note extends JLabel {
        private Color bg = Color.WHITE, line = Theme.BORDER;

        Note() {
            setFont(Theme.smallBold);
            setOpaque(false);
            setBorder(BorderFactory.createEmptyBorder(4, 10, 4, 10));
            setHorizontalAlignment(SwingConstants.RIGHT);
        }

        void set(String text, Color bg, Color line, Color fg, String icon) {
            this.bg = bg;
            this.line = line;
            setForeground(fg);
            setText(text == null ? "" : "\u200F" + icon + "  " + text);
            setToolTipText(text == null ? null : text.replace("\u200E", ""));
        }

        @Override
        public Dimension getMinimumSize() {
            return new Dimension(40, super.getMinimumSize().height);
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(bg);
            g.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 10, 10);
            g.setColor(line);
            g.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 10, 10);
            g.dispose();
            super.paintComponent(g0);
        }
    }

    // ------------------------------------------------------------ לבדיקות (tools/test/calls/VarsPanelCheck.java)

    /** הפעולה שמוצגת (0 = הפעולה שרצה) */
    int frame() { return shown; }

    /** בחירה ברשימה, כאילו התלמיד בחר את הפעולה k */
    void choose(int k) { pick(k); }

    /** כמה שורות ברשימת הפעולות (0 = הרשימה מוסתרת — פעולה אחת בלבד) */
    int listSize() { return strip.isVisible() ? frames.getSize() : 0; }

    /** השורות בטבלה: "שם=ערך" (בלי תווי הכיוון); אם אין שורות — ההודעה שבמקום הטבלה */
    List<String> rows() {
        List<String> res = new java.util.ArrayList<String>();
        for (int i = 0; i < model.getRowCount(); i++)
            res.add((model.getValueAt(i, 0) + "=" + model.getValueAt(i, 1)).replaceAll("[‎‏‪-‮]", ""));
        if (res.isEmpty() && table.empty != null) res.add("(" + table.empty + ")");
        return res;
    }

    /** המשפט מעל הטבלה (בלי תווי הכיוון), או "" */
    String noteText() { return note.isVisible() ? note.getText().replaceAll("[‎‏]", "") : ""; }

    // ------------------------------------------------------------ טקסט

    private static String lrm(String s) {
        return "\u200E" + s + "\u200E";
    }

    /** ערך משמאל לימין גם כשיש בו מחרוזות בעברית (כמו השורות בקוד) */
    private static String ltr(String line) {
        String s = "\u200E" + line.replace("\"", "\u200E\"\u200E").replace("'", "\u200E'\u200E");
        return HEBREW_RUN.matcher(s).replaceAll("\u202B$0\u202C\u200E");
    }

    private static final java.util.regex.Pattern HEBREW_RUN = java.util.regex.Pattern.compile(
            "[\u0590-\u05FF](?:[\u0590-\u05FF \\-.,:;!?()0-9]*[\u0590-\u05FF])?");
}
