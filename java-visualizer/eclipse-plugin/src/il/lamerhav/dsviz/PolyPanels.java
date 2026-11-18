package il.lamerhav.dsviz;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.ComponentOrientation;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.ListCellRenderer;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

/**
 * החלונות של הורשה ופולימורפיזם ב-Swing — משותפים לכלי של Visual Studio (CsTool) ול-IntelliJ:
 * <ul>
 * <li>{@link InheritPanel} — התוספת ל"צור מחלקה": יורשת מ- (רשימה עם הצבע של כל מחלקה), שדות שירשה (באפור, לפי המחלקה), פעולות לדריסה,
 *     פעולה חדשה, ומחלקה מופשטת.
 * <li>{@link TreePanel} — "היררכיה מוכנה": שלוש היררכיות ו"עץ משלכם" (טבלה קצרה), העץ מצויר בצבעים תוך כדי מילוי.
 * <li>{@link ObjectPanel} — "הוסף עצם": טיפוס המשתנה, המחלקה של העצם (היא או יורשת שלה) וערך לכל פרמטר — או מערך של מחלקת העל.
 * </ul>
 * הצבעים — ClassGen.colors, כמו בהרצה הוויזואלית; ליד כל צבע תמיד כתוב שם המחלקה. ההיגיון כולו ב-ClassGen (אותו קוד כמו ב-VS Code).
 * כל רכיב באנגלית (שמות, טיפוסים, קוד) מסומן ltr — המארח קורא ל-{@link #fixLtr} אחרי שהחלון כולו הפך לימין-לשמאל. תואם Java 8.
 */
public final class PolyPanels {
    private PolyPanels() {}

    /** הגופן של קוד ושמות (המארח יכול להחליף) */
    public static Font code = new Font(Font.MONOSPACED, Font.PLAIN, 12);
    static final Color MUTED = new Color(0x667085), WARN = new Color(0x9A6700);
    static final String NONE = "בלי";

    static <T extends JComponent> T ltr(T c) {
        c.putClientProperty("ltr", Boolean.TRUE);
        return c;
    }

    /** אחרי applyComponentOrientation(RIGHT_TO_LEFT) של החלון: מה שבאנגלית — משמאל לימין */
    public static void fixLtr(Component c) {
        if (c instanceof JComponent && Boolean.TRUE.equals(((JComponent) c).getClientProperty("ltr"))) {
            setLtr(c);
            return;
        }
        if (c instanceof Container) for (Component k : ((Container) c).getComponents()) fixLtr(k);
    }

    /** משמאל לימין — רק מה שעוד לא (שינוי של שדה טקסט באמצע הקלדה אסור) */
    private static void setLtr(Component c) {
        if (c.getComponentOrientation() != ComponentOrientation.LEFT_TO_RIGHT) c.setComponentOrientation(ComponentOrientation.LEFT_TO_RIGHT);
        if (c instanceof JTextField && ((JTextField) c).getHorizontalAlignment() != SwingConstants.LEFT) ((JTextField) c).setHorizontalAlignment(SwingConstants.LEFT);
        if (c instanceof Container) for (Component k : ((Container) c).getComponents()) setLtr(k);
    }

    static JTextField field(int cols, String placeholder) {
        JTextField t = ltr(new JTextField(cols));
        t.setFont(code);
        // גודל קבוע: בשדה שעבר מימין-לשמאל לשמאל-לימין, חישוב הגודל של Swing לפי הטקסט יכול להיתקע (FlowView)
        t.setPreferredSize(new Dimension(t.getFontMetrics(code).charWidth('m') * cols + 18, t.getFontMetrics(code).getHeight() + 12));
        t.putClientProperty("JTextField.placeholderText", placeholder);
        t.setToolTipText(placeholder);
        return t;
    }

    static JLabel muted(String text) {
        JLabel l = new JLabel(text);
        l.setForeground(MUTED);
        return l;
    }

    static JLabel section(String text) {
        JLabel l = new JLabel(text);
        l.setFont(l.getFont().deriveFont(Font.BOLD));
        return l;
    }

    static void onText(JTextField f, final Runnable r) {
        f.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { r.run(); }
            public void removeUpdate(DocumentEvent e) { r.run(); }
            public void changedUpdate(DocumentEvent e) { r.run(); }
        });
    }

    /** עמודה: כל רכיב בשורה משלו, לרוחב, צמוד למעלה */
    public static class Column extends JPanel {
        private int row;

        Column() {
            super(new GridBagLayout());
            setOpaque(false);
        }

        <T extends Component> T line(T c, int top) {
            GridBagConstraints g = new GridBagConstraints();
            g.gridx = 0;
            g.gridy = row++;
            g.weightx = 1;
            g.fill = GridBagConstraints.HORIZONTAL;
            g.anchor = GridBagConstraints.FIRST_LINE_START;
            g.insets = new Insets(top, 0, 0, 0);
            super.add(c, g);
            return c;
        }

        void reset() {
            removeAll();
            row = 0;
        }
    }

    static JPanel flow(Component... cs) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEADING, 6, 0));
        p.setOpaque(false);
        for (Component c : cs) p.add(c);
        return p;
    }

    // ------------------------------------------------------------------ תגית מחלקה וציור עץ

    /** שם מחלקה בתוך מסגרת מעוגלת בצבע שלה (כמו בהרצה); -1 — אפור. color מקווקו כשהצבעים חוזרים */
    public static final class ClassTag extends JComponent {
        String name;
        int color;
        boolean isAbstract;

        public ClassTag(String name, int color) {
            this.name = name;
            this.color = color;
            setFont(code);
            putClientProperty("ltr", Boolean.TRUE);
        }

        @Override
        public Dimension getPreferredSize() {
            FontMetrics fm = getFontMetrics(getFont());
            return new Dimension(fm.stringWidth(label()) + 18, fm.getHeight() + 6);
        }

        String label() {
            return name + (isAbstract ? " (abstract)" : "");
        }

        @Override
        protected void paintComponent(Graphics g0) {
            paintTag((Graphics2D) g0, 0, 0, getWidth(), getHeight(), label(), color, getFont());
        }
    }

    static void paintTag(Graphics2D g0, int x, int y, int w, int h, String text, int color, Font f) {
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(new Color(ClassGen.bg(color)));
        g.fillRoundRect(x + 1, y + 1, w - 2, h - 2, 10, 10);
        g.setColor(new Color(ClassGen.ink(color)));
        g.setStroke(ClassGen.dashed(color) ? new BasicStroke(1.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND, 1f, new float[] { 4f, 3f }, 0f) : new BasicStroke(1.5f));
        g.drawRoundRect(x + 1, y + 1, w - 3, h - 3, 10, 10);
        g.setFont(f);
        FontMetrics fm = g.getFontMetrics();
        g.drawString(text, x + 9, y + (h - fm.getHeight()) / 2 + fm.getAscent());
        g.dispose();
    }

    /** העץ: כל מחלקה בתגית בצבע שלה, ילדים מוזחים מתחת להורה עם קו מחבר (משמאל לימין — כמו קוד) */
    public static final class TreeView extends JComponent {
        List<Object[]> rows = new ArrayList<Object[]>();

        public TreeView() {
            setFont(code);
            putClientProperty("ltr", Boolean.TRUE);
        }

        public void setRows(List<Object[]> rows) {
            this.rows = rows;
            revalidate();
            repaint();
        }

        int rowH() {
            return getFontMetrics(getFont()).getHeight() + 12;
        }

        @Override
        public Dimension getPreferredSize() {
            FontMetrics fm = getFontMetrics(getFont());
            int w = 120;
            for (Object[] r : rows) w = Math.max(w, 10 + (Integer) r[1] * 26 + fm.stringWidth(r[0] + ((Boolean) r[3] ? " (abstract)" : "")) + 30);
            return new Dimension(w, Math.max(40, rows.size() * rowH() + 8));
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            FontMetrics fm = g.getFontMetrics(getFont());
            int h = rowH();
            if (rows.isEmpty()) {
                g.setColor(MUTED);
                g.drawString("(העץ יצויר כאן)", 10, 24);
            }
            for (int i = 0; i < rows.size(); i++) {
                Object[] r = rows.get(i);
                int depth = (Integer) r[1], x = 6 + depth * 26, y = 4 + i * h;
                String text = r[0] + ((Boolean) r[3] ? " (abstract)" : "");
                if (depth > 0) {   // קו להורה: השורה הקודמת עם עומק קטן ב-1
                    int p = i - 1;
                    while (p >= 0 && (Integer) rows.get(p)[1] >= depth) p--;
                    if (p >= 0) {
                        g.setColor(new Color(0xB8C0CC));
                        int px = 6 + (depth - 1) * 26 + 12, py = 4 + p * h + h - 6;
                        g.drawLine(px, py, px, y + (h - 6) / 2);
                        g.drawLine(px, y + (h - 6) / 2, x, y + (h - 6) / 2);
                    }
                }
                paintTag(g, x, y, fm.stringWidth(text) + 18, h - 6, text, (Integer) r[2], getFont());
            }
            g.dispose();
        }
    }

    /** הצורה של פריט ברשימה של מחלקות: ריבוע בצבע המחלקה + השם (+ החבילה באפור כששני שמות זהים) */
    static final class ClassRenderer implements ListCellRenderer<Object> {
        Map<String, Integer> colors = new LinkedHashMap<String, Integer>();
        String none = NONE;
        Set<String> dup = new java.util.HashSet<String>();

        void known(List<ClassGen.Known> known) {
            colors = ClassGen.colors(ClassGen.sorted(known));
            Set<String> seen = new java.util.HashSet<String>();
            for (ClassGen.Known k : known) if (!seen.add(k.name)) dup.add(k.name);
        }

        public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean selected, boolean focus) {
            JPanel p = new JPanel(new FlowLayout(FlowLayout.LEADING, 6, 1));
            p.setOpaque(true);
            p.setBackground(selected ? list.getSelectionBackground() : list.getBackground());
            if (value instanceof ClassGen.Known) {
                ClassGen.Known k = (ClassGen.Known) value;
                Integer c = colors.get(k.name);
                ClassTag t = new ClassTag(k.name, c == null ? -1 : c);
                t.isAbstract = k.isAbstract;
                p.add(t);
                if (!k.pkg.isEmpty() && dup.contains(k.name)) {   // החבילה — רק כששתי מחלקות באותו שם
                    JLabel h = muted(k.pkg);
                    p.add(h);
                }
            } else {
                JLabel l = new JLabel(value == null ? "" : String.valueOf(value).isEmpty() ? none : String.valueOf(value));
                l.setForeground(selected ? list.getSelectionForeground() : list.getForeground());
                p.add(l);
            }
            p.applyComponentOrientation(ComponentOrientation.LEFT_TO_RIGHT);
            return p;
        }
    }

    static String nameOf(Object o) {
        return o instanceof ClassGen.Known ? ((ClassGen.Known) o).name : o == null ? "" : String.valueOf(o);
    }

    // ------------------------------------------------------------------ צור מחלקה: יורשת מ-

    /** התוספת לחלון "צור מחלקה": יורשת מ-, שדות שירשה, פעולות לדריסה, פעולה חדשה, מחלקה מופשטת */
    public static final class InheritPanel extends Column {
        final String lang;
        final List<ClassGen.Known> known;
        final Runnable onChange;
        final JComboBox<Object> sup = new JComboBox<Object>();
        final ClassRenderer renderer = new ClassRenderer();
        final Column inherited = new Column(), overrides = new Column(), methodRows = new Column();
        /** החלק התחתון — פעולות לדריסה, פעולה חדשה, מחלקה מופשטת: המארח שם אותו אחרי טבלת השדות */
        public final Column lower = new Column();
        final JLabel inheritedHead = section("שדות שירשה"), overridesHead = section("פעולות לדריסה");
        final JCheckBox isAbstract = new JCheckBox("מחלקה מופשטת (abstract) — אי אפשר ליצור ממנה עצם, רק לרשת ממנה");
        final Map<String, JCheckBox> checks = new LinkedHashMap<String, JCheckBox>();
        final Set<String> picked = new LinkedHashSet<String>();
        final List<JComponent[]> methods = new ArrayList<JComponent[]>();
        String shownKeys = "", shownInherited = "";

        public InheritPanel(String lang, List<ClassGen.Known> known, Runnable onChange) {
            this.lang = lang;
            this.known = known == null ? new ArrayList<ClassGen.Known>() : known;
            this.onChange = onChange;
            renderer.known(this.known);
            DefaultComboBoxModel<Object> m = new DefaultComboBoxModel<Object>();
            m.addElement("");
            for (ClassGen.Known k : ClassGen.superCandidates(this.known)) m.addElement(k);
            sup.setModel(m);
            sup.setRenderer(renderer);
            sup.setToolTipText("מחלקה מהפרויקט שהמחלקה החדשה יורשת ממנה (extends / :)");
            sup.addActionListener(e -> changed());
            line(section("יורשת מ-"), 10);
            line(flow(ltr(sup), muted(this.known.isEmpty() ? "(אין עדיין מחלקות בפרויקט)" : "")), 4);
            line(inheritedHead, 8);
            line(inherited, 2);
            lower.line(overridesHead, 8);
            lower.line(overrides, 2);
            lower.line(section("פעולה חדשה"), 10);
            lower.line(muted("טיפוס מוחזר · שם · פרמטרים (למשל  int steps, String word)"), 2);
            lower.line(methodRows, 2);
            JButton addM = new JButton("+ פעולה");
            addM.addActionListener(e -> addMethod("String", "", "", false, true));
            lower.line(flow(addM), 4);
            lower.line(isAbstract, 8);
            isAbstract.addActionListener(e -> changed());
            refresh(null, null);
        }

        void changed() {
            if (onChange != null) onChange.run();
        }

        /** שורה של פעולה חדשה */
        public void addMethod(String ret, String name, String params, boolean abs, boolean focus) {
            JTextField r = field(7, "String"), n = field(9, "say"), p = field(16, "int x, String s");
            r.setText(ret);
            n.setText(name);
            p.setText(params);
            JCheckBox a = new JCheckBox("abstract");
            a.setSelected(abs);
            JButton x = new JButton("✕");
            x.setToolTipText("הסרת הפעולה");
            final JComponent[] row = { r, n, p, a, x };
            methods.add(row);
            for (JTextField t : new JTextField[] { r, n, p }) onText(t, this::changed);
            a.addActionListener(e -> changed());
            x.addActionListener(e -> {
                methods.remove(row);
                layoutMethods();
                changed();
            });
            layoutMethods();
            if (focus) n.requestFocusInWindow();
            changed();
        }

        void layoutMethods() {
            methodRows.reset();
            for (JComponent[] row : methods) {
                row[3].setVisible(isAbstract.isSelected());
                JPanel line = flow(row[0], row[1], new JLabel("("), row[2], new JLabel(")"), row[3], row[4]);
                ltr(line);
                methodRows.line(line, 2);
            }
            revalidate();
            repaint();
        }

        /** מעביר את מה שבחלון ל-Options */
        public void apply(ClassGen.Options o) {
            Object s = sup.getSelectedItem();
            o.superName = nameOf(s);
            o.superPkg = s instanceof ClassGen.Known ? ((ClassGen.Known) s).pkg : null;
            o.isAbstract = isAbstract.isSelected();
            o.overrides.clear();
            o.overrides.addAll(picked);
            o.methods.clear();
            for (JComponent[] row : methods) {
                ClassGen.Method m = new ClassGen.Method(((JTextField) row[0]).getText(), ((JTextField) row[1]).getText(), ((JTextField) row[2]).getText());
                m.isAbstract = isAbstract.isSelected() && ((JCheckBox) row[3]).isSelected();
                o.methods.add(m);
            }
        }

        /** אחרי כל שינוי: שדות שירשה ופעולות לדריסה לפי הבדיקה (בונה מחדש רק כשהם השתנו — כדי לא לאבד פוקוס) */
        public void refresh(ClassGen.Check c, String className) {
            boolean on = c != null && c.sup != null;
            inheritedHead.setVisible(on);
            inherited.setVisible(on);
            overridesHead.setVisible(on);
            overrides.setVisible(on);
            for (JComponent[] row : methods) row[3].setVisible(isAbstract.isSelected());
            if (!on) {
                shownKeys = "";
                shownInherited = "";
                return;
            }
            Map<String, Integer> col = ClassGen.colorsWith(known, className == null ? "MyClass" : className, c.sup);
            StringBuilder ik = new StringBuilder();
            for (ClassGen.Inherited h : c.inherited) {
                ik.append(h.from).append(col.get(h.from)).append(':');
                for (ClassGen.Field f : h.fields) ik.append(f.type).append(' ').append(f.name).append(',');
            }
            if (!ik.toString().equals(shownInherited)) {
                shownInherited = ik.toString();
                inherited.reset();
                for (ClassGen.Inherited h : c.inherited) {
                    Integer ci = col.get(h.from);
                    StringBuilder fs = new StringBuilder();
                    for (ClassGen.Field f : h.fields) fs.append(fs.length() > 0 ? ", " : "").append(f.type).append(' ').append(f.name);
                    JLabel l = ltr(new JLabel(fs.length() == 0 ? "(אין שדות)" : fs.toString()));
                    l.setFont(code);
                    l.setForeground(Color.GRAY);
                    inherited.line(ltr(flow(new ClassTag(h.from, ci == null ? -1 : ci), l)), 2);
                }
            }
            StringBuilder keys = new StringBuilder();
            for (ClassGen.Overridable ov : c.overridable) keys.append(ov.key).append(ov.isAbstract).append('|');
            keys.append(isAbstract.isSelected());
            if (!keys.toString().equals(shownKeys)) {
                shownKeys = keys.toString();
                overrides.reset();
                checks.clear();
                if (c.overridable.isEmpty()) overrides.line(muted("(אין פעולות שאפשר לדרוס — toString בתיבת הסימון למטה)"), 2);
                for (final ClassGen.Overridable ov : c.overridable) {
                    boolean required = ov.isAbstract && !isAbstract.isSelected();
                    final JCheckBox b = ltr(new JCheckBox(ov.method.ret + " " + ov.method.name + "(" + ov.method.params + ")"));
                    b.setFont(code);
                    b.setSelected(required || picked.contains(ov.key));
                    b.setEnabled(!required);
                    b.addActionListener(e -> {
                        if (b.isSelected()) picked.add(ov.key);
                        else picked.remove(ov.key);
                        changed();
                    });
                    Integer ci = col.get(ov.from);
                    JLabel from = muted("מ-" + ov.from + (required ? " — מופשטת, חובה לדרוס" : ov.needsVirtual ? " — תתווסף לה virtual" : ""));
                    from.setForeground(new Color(ClassGen.ink(ci == null ? -1 : ci)));
                    checks.put(ov.key, b);
                    overrides.line(flow(b, from), 1);
                }
            }
            revalidate();
            repaint();
        }

        // ---------------------------------------------------------------- לבדיקות ולצילומי מסך
        public void demoSuper(String name) {
            for (int i = 0; i < sup.getItemCount(); i++) if (nameOf(sup.getItemAt(i)).equals(name)) sup.setSelectedIndex(i);
        }

        public void demoPick(String key) {
            picked.add(key);
            shownKeys = "";
            changed();
        }

        public void demoAbstract(boolean on) {
            isAbstract.setSelected(on);
            changed();
        }

        /** מה ברשימה של "יורשת מ-": השם והצבע (#hex), "בלי" ראשון */
        public List<String> superList() {
            List<String> out = new ArrayList<String>();
            for (int i = 0; i < sup.getItemCount(); i++) {
                Object o = sup.getItemAt(i);
                if (o instanceof ClassGen.Known) {
                    Integer c = renderer.colors.get(((ClassGen.Known) o).name);
                    out.add(((ClassGen.Known) o).name + " " + String.format("#%06x", ClassGen.ink(c == null ? -1 : c)));
                } else out.add(NONE);
            }
            return out;
        }

        public List<String> overrideList() {
            List<String> out = new ArrayList<String>();
            for (Map.Entry<String, JCheckBox> e : checks.entrySet()) out.add(e.getKey() + (e.getValue().isSelected() ? " ✓" : "") + (e.getValue().isEnabled() ? "" : " (חובה)"));
            return out;
        }
    }

    // ------------------------------------------------------------------ היררכיה מוכנה

    /** "היררכיה מוכנה": בעלי חיים / צורות / אנשים בבית הספר / עץ משלכם — העץ בצבעים, הקבצים וה-main לדוגמה */
    public static final class TreePanel extends JPanel {
        final ClassGen.Options base;
        final boolean run;
        final Runnable onChange;
        final List<ClassGen.Tree> trees = ClassGen.trees();
        final List<JToggleButton> chips = new ArrayList<JToggleButton>();
        final JLabel about = new JLabel(" ");
        final Column own = new Column(), ownRows = new Column();
        final List<JComponent[]> rows = new ArrayList<JComponent[]>();
        final TreeView view = new TreeView();
        final JCheckBox withMain = new JCheckBox("גם main לדוגמה — מערך של מחלקת העל, ועצם מכל מחלקה");
        final JLabel files = muted(" ");
        final JTextArea preview = ltr(new JTextArea(14, 52));
        int choice;
        boolean updating;

        public TreePanel(ClassGen.Options base, boolean run, Runnable onChange) {
            super(new BorderLayout(14, 0));
            setOpaque(false);
            this.base = base;
            this.run = run;
            this.onChange = onChange;
            Column right = new Column();
            right.line(section("בוחרים היררכיה"), 0);
            JPanel chipRow = new JPanel(new FlowLayout(FlowLayout.LEADING, 6, 2));
            chipRow.setOpaque(false);
            ButtonGroup bg = new ButtonGroup();
            for (int i = 0; i <= trees.size(); i++) {
                final int k = i;
                JToggleButton b = new JToggleButton(i < trees.size() ? trees.get(i).title : ClassGen.OWN_TREE);
                b.setSelected(i == 0);
                b.addActionListener(e -> choose(k));
                bg.add(b);
                chips.add(b);
                chipRow.add(b);
            }
            right.line(chipRow, 4);
            about.setForeground(MUTED);
            right.line(about, 4);
            own.line(muted("שם המחלקה · יורשת מ- (ריק — אף אחת) · השדות (String name; int age)"), 0);
            own.line(ownRows, 2);
            JButton add = new JButton("+ מחלקה");
            add.addActionListener(e -> addRow("", "", "", true));
            own.line(flow(add), 4);
            right.line(own, 6);
            right.line(section("העץ"), 10);
            JScrollPane vs = new JScrollPane(view);
            vs.setBorder(BorderFactory.createLineBorder(new Color(0xDFE3EA)));
            vs.setPreferredSize(new Dimension(320, 190));
            right.line(vs, 4);
            withMain.setSelected(true);
            withMain.addActionListener(e -> changed());
            right.line(withMain, 8);
            right.line(files, 4);
            add(right, BorderLayout.LINE_START);
            Column left = new Column();
            left.line(section("כך ייראה ה-main"), 0);
            preview.setEditable(false);
            preview.setFont(code);
            JScrollPane ps = ltr(new JScrollPane(preview));
            ps.setPreferredSize(new Dimension(430, 380));
            left.line(ps, 4);
            add(left, BorderLayout.CENTER);
            addRow("Vehicle", "", "String model; int year", false);
            addRow("Bus", "Vehicle", "int seats", false);
            addRow("Taxi", "Vehicle", "String company", false);
            choose(0);
        }

        void changed() {
            if (updating) return;
            update();
            if (onChange != null) onChange.run();
        }

        void choose(int k) {
            choice = k;
            chips.get(k).setSelected(true);
            own.setVisible(k == trees.size());
            about.setText(k < trees.size() ? trees.get(k).about : "כותבים בטבלה את המחלקות — העץ מצויר תוך כדי.");
            changed();
        }

        public void addRow(String name, String sup, String fields, boolean focus) {
            JTextField n = field(9, "Vehicle"), f = field(18, "String name; int age");
            JComboBox<String> s = new JComboBox<String>();
            s.setEditable(true);
            s.setFont(code);
            ltr(s);
            n.setText(name);
            f.setText(fields);
            s.getEditor().setItem(sup);
            JButton x = new JButton("✕");
            x.setToolTipText("הסרת המחלקה");
            final JComponent[] row = { n, s, f, x };
            rows.add(row);
            onText(n, this::changed);
            onText(f, this::changed);
            Component ed = s.getEditor().getEditorComponent();
            if (ed instanceof JTextField) onText((JTextField) ed, this::changed);
            s.addActionListener(e -> changed());
            x.addActionListener(e -> {
                rows.remove(row);
                layoutRows();
                changed();
            });
            layoutRows();
            if (focus) n.requestFocusInWindow();
            changed();
        }

        void layoutRows() {
            ownRows.reset();
            for (JComponent[] r : rows) ownRows.line(ltr(flow(r[0], new JLabel("extends"), r[1], r[2], r[3])), 2);
            ownRows.revalidate();
        }

        @SuppressWarnings("unchecked")
        static String supText(JComponent c) {
            Object o = ((JComboBox<String>) c).getEditor().getItem();
            return o == null ? "" : o.toString().trim();
        }

        /** ההיררכיה שנבחרה, ושגיאות של הטבלה */
        public ClassGen.Tree tree(List<String> errors) {
            if (choice < trees.size()) return trees.get(choice);
            List<ClassGen.TreeRow> rs = new ArrayList<ClassGen.TreeRow>();
            for (JComponent[] r : rows) rs.add(new ClassGen.TreeRow(((JTextField) r[0]).getText(), supText(r[1]), ((JTextField) r[2]).getText()));
            return ClassGen.ownTree(rs, base.known, errors);
        }

        /** הקבצים שייווצרו, השגיאות (גם של הטבלה) והאזהרות */
        public ClassGen.TreeResult result() {
            List<String> errors = new ArrayList<String>();
            ClassGen.Tree t = tree(errors);
            ClassGen.TreeResult r = ClassGen.treeFiles(t, base, withMain.isSelected(), run);
            r.errors.addAll(0, errors);
            return r;
        }

        @SuppressWarnings("unchecked")
        void update() {
            updating = true;
            try {
                List<String> errors = new ArrayList<String>();
                ClassGen.Tree t = tree(errors);
                view.setRows(ClassGen.treeLayout(t, base.known));
                ClassGen.TreeResult r = ClassGen.treeFiles(t, base, withMain.isSelected(), run);
                StringBuilder fs = new StringBuilder();
                String main = null;
                for (String[] f : r.files) {
                    fs.append(fs.length() > 0 ? ", " : "").append(f[0]);
                    if (t.main != null && f[0].startsWith(t.main + ".")) main = f[1];
                }
                files.setText(fs.length() == 0 ? " " : "ייווצרו: " + fs);
                String text = main != null ? main : r.files.isEmpty() ? "" : r.files.get(0)[1];
                if (!text.equals(preview.getText())) {
                    preview.setText(text);
                    preview.setCaretPosition(0);
                }
                // ברשימה של "יורשת מ-" בטבלה: שאר השורות ומחלקות הפרויקט
                List<String> names = new ArrayList<String>();
                for (JComponent[] row : rows) {
                    String n = ((JTextField) row[0]).getText().trim();
                    if (!n.isEmpty()) names.add(ClassGen.cap(n));
                }
                for (ClassGen.Known k : ClassGen.superCandidates(base.known)) if (!names.contains(k.name)) names.add(k.name);
                for (JComponent[] row : rows) {
                    JComboBox<String> s = (JComboBox<String>) row[1];
                    String cur = supText(s), self = ClassGen.cap(((JTextField) row[0]).getText().trim());
                    List<String> want = new ArrayList<String>();
                    want.add("");
                    for (String n : names) if (!n.equals(self)) want.add(n);
                    boolean same = s.getItemCount() == want.size();
                    for (int i = 0; same && i < want.size(); i++) same = want.get(i).equals(s.getItemAt(i));
                    if (!same) {
                        s.setModel(new DefaultComboBoxModel<String>(want.toArray(new String[0])));
                        s.getEditor().setItem(cur);
                    }
                }
                revalidate();
                repaint();
            } finally {
                updating = false;
            }
        }

        // ---------------------------------------------------------------- לבדיקות ולצילומי מסך
        public void demoChoose(int k) {
            choose(k);
        }

        public void demoRows(String spec) {
            rows.clear();
            for (String r : spec.split("/")) {
                String[] p = r.split("\\|", -1);
                addRow(p[0], p.length > 1 ? p[1] : "", p.length > 2 ? p[2] : "", false);
            }
            changed();
        }

        public String drawn() {
            StringBuilder sb = new StringBuilder();
            for (Object[] r : view.rows) sb.append(sb.length() > 0 ? " " : "").append(r[0]).append(':').append(r[1]).append(':').append(String.format("#%06x", ClassGen.ink((Integer) r[2])));
            return sb.toString();
        }
    }

    // ------------------------------------------------------------------ הוסף עצם

    /** בודק שם משתנה מול הקוד שבמקום הסמן: מחזיר הודעה, או null — פנוי */
    public interface NameCheck {
        String check(String name);
    }

    /** "הוסף עצם": עצם אחד (טיפוס המשתנה, המחלקה של העצם, ערך לכל פרמטר) או מערך של מחלקת העל (מחלקה וערכים לכל תא) */
    public static final class ObjectPanel extends JPanel {
        final String lang;
        final List<ClassGen.Known> known;
        final Runnable onChange;
        final NameCheck names;
        final JRadioButton one = new JRadioButton("עצם אחד"), many = new JRadioButton("מערך של מחלקת העל");
        final JComboBox<Object> type = new JComboBox<Object>(), cls = new JComboBox<Object>();
        final JTextField name = field(10, "a1");
        final JSpinner size = new JSpinner(new SpinnerNumberModel(4, 1, 12, 1));
        final Column params = new Column(), cells = new Column();
        final List<JTextField> args = new ArrayList<JTextField>();
        final List<JComponent[]> cellRows = new ArrayList<JComponent[]>();
        final JTextArea preview = ltr(new JTextArea(6, 52));
        final JLabel classHead = section("המחלקה של העצם"), paramsHead = section("ערך לכל פרמטר של הבנאי"), cellsHead = section("התאים");
        final JPanel clsRow;
        final ClassRenderer renderer = new ClassRenderer();
        boolean updating;
        String shownParams = "";

        public ObjectPanel(String lang, List<ClassGen.Known> known, Runnable onChange, NameCheck names) {
            super(new BorderLayout(0, 8));
            setOpaque(false);
            this.lang = lang;
            this.known = known == null ? new ArrayList<ClassGen.Known>() : known;
            this.onChange = onChange;
            this.names = names;
            renderer.known(this.known);
            renderer.none = "(ריק — null)";
            Column c = new Column();
            ButtonGroup g = new ButtonGroup();
            g.add(one);
            g.add(many);
            one.setSelected(true);
            one.setOpaque(false);
            many.setOpaque(false);
            c.line(flow(one, many), 0);
            DefaultComboBoxModel<Object> tm = new DefaultComboBoxModel<Object>();
            for (ClassGen.Known k : ClassGen.sorted(this.known)) if (!k.isInterface) tm.addElement(k);
            type.setModel(tm);
            type.setRenderer(renderer);
            cls.setRenderer(renderer);
            c.line(section("טיפוס המשתנה"), 10);
            c.line(flow(ltr(type), new JLabel("שם:"), name, new JLabel("גודל:"), size), 4);
            c.line(classHead, 10);
            clsRow = flow(ltr(cls));
            c.line(clsRow, 4);
            c.line(paramsHead, 10);
            c.line(params, 4);
            c.line(cellsHead, 10);
            c.line(cells, 4);
            c.line(section("הקוד שייכנס במקום הסמן"), 10);
            preview.setEditable(false);
            preview.setFont(code);
            JScrollPane ps = ltr(new JScrollPane(preview));
            ps.setPreferredSize(new Dimension(480, 120));
            c.line(ps, 4);
            add(c, BorderLayout.CENTER);
            if (tm.getSize() > 0) type.setSelectedIndex(0);
            Object first = tm.getSize() > 0 ? tm.getElementAt(0) : null;
            name.setText(first == null ? "a1" : Character.toLowerCase(nameOf(first).charAt(0)) + "1");
            one.addActionListener(e -> changed());
            many.addActionListener(e -> changed());
            type.addActionListener(e -> {
                fillClasses();
                changed();
            });
            cls.addActionListener(e -> changed());
            onText(name, this::changed);
            size.addChangeListener(e -> changed());
            fillClasses();
            changed();
        }

        void changed() {
            if (updating) return;
            update();
            if (onChange != null) onChange.run();
        }

        String typeName() {
            return nameOf(type.getSelectedItem());
        }

        void fillClasses() {
            List<ClassGen.Known> ok = ClassGen.objectClasses(known, typeName());
            DefaultComboBoxModel<Object> m = new DefaultComboBoxModel<Object>();
            for (ClassGen.Known k : ok) m.addElement(k);
            cls.setModel(m);
            if (m.getSize() > 0) cls.setSelectedIndex(m.getSize() > 1 ? 1 : 0);
            for (JComponent[] r : cellRows) fillCell(r);
        }

        @SuppressWarnings("unchecked")
        void fillCell(JComponent[] r) {
            JComboBox<Object> b = (JComboBox<Object>) r[1];
            Object was = b.getSelectedItem();
            DefaultComboBoxModel<Object> m = new DefaultComboBoxModel<Object>();
            m.addElement("");
            for (ClassGen.Known k : ClassGen.objectClasses(known, typeName())) m.addElement(k);
            b.setModel(m);
            for (int i = 0; i < m.getSize(); i++) if (nameOf(m.getElementAt(i)).equals(nameOf(was))) b.setSelectedIndex(i);
        }

        void layoutParams() {
            String c = nameOf(cls.getSelectedItem());
            List<ClassGen.Field> ps = ClassGen.ctorParams(known, c);
            StringBuilder key = new StringBuilder(c);
            for (ClassGen.Field f : ps) key.append('|').append(f.type).append(' ').append(f.name);
            if (key.toString().equals(shownParams)) return;
            shownParams = key.toString();
            List<String> old = new ArrayList<String>();
            for (JTextField t : args) old.add(t.getText());
            params.reset();
            args.clear();
            if (ps.isEmpty()) params.line(muted("(לבנאי אין פרמטרים)"), 0);
            for (int i = 0; i < ps.size(); i++) {
                ClassGen.Field f = ps.get(i);
                String owner = ClassGen.paramOwner(known, c, f.name);
                Integer col = renderer.colors.get(owner);
                JTextField t = field(14, f.type.equals("String") || f.type.equals("string") ? "Rexy" : f.type.equals("double") ? "3.2" : f.type.equals("int") ? "3" : "...");
                if (i < old.size()) t.setText(old.get(i));
                onText(t, this::changed);
                args.add(t);
                JLabel l = ltr(new JLabel(f.type + " " + f.name));
                l.setFont(code);
                params.line(ltr(flow(new ClassTag(owner, col == null ? -1 : col), l, t)), 2);
            }
            params.revalidate();
        }

        void layoutCells() {
            int n = (Integer) size.getValue();
            while (cellRows.size() < n) {
                JComboBox<Object> b = new JComboBox<Object>();
                b.setRenderer(renderer);
                JTextField v = field(26, "ערכים בפסיקים");
                final JComponent[] r = { new JLabel("[" + cellRows.size() + "]"), b, v };
                fillCell(r);
                b.addActionListener(e -> changed());
                onText(v, this::changed);
                cellRows.add(r);
            }
            cells.reset();
            for (int i = 0; i < n; i++) {
                JComponent[] r = cellRows.get(i);
                String c = nameOf(((JComboBox<?>) r[1]).getSelectedItem());
                StringBuilder ph = new StringBuilder();
                for (ClassGen.Field f : ClassGen.ctorParams(known, c)) ph.append(ph.length() > 0 ? ", " : "").append(f.name);
                ((JTextField) r[2]).putClientProperty("JTextField.placeholderText", c.isEmpty() ? "" : ph.toString());
                ((JTextField) r[2]).setToolTipText(ph.toString());
                r[2].setEnabled(!c.isEmpty());
                cells.line(ltr(flow(r[0], r[1], r[2])), 2);
            }
            cells.revalidate();
        }

        /** הקוד (כל שורה עם indent) והשגיאות — גם שם משתנה שכבר קיים בקוד */
        public ClassGen.ObjResult result(String indent) {
            ClassGen.ObjOptions o = new ClassGen.ObjOptions();
            o.lang = lang;
            o.varType = typeName();
            o.varName = name.getText();
            o.indent = indent == null ? "" : indent;
            o.array = many.isSelected();
            o.cls = nameOf(cls.getSelectedItem());
            for (JTextField t : args) o.args.add(t.getText());
            o.size = (Integer) size.getValue();
            for (int i = 0; i < o.size && i < cellRows.size(); i++) {
                JComponent[] r = cellRows.get(i);
                List<String> cell = new ArrayList<String>();
                String c = nameOf(((JComboBox<?>) r[1]).getSelectedItem());
                if (!c.isEmpty()) {
                    cell.add(c);
                    cell.addAll(ClassGen.splitArgs(((JTextField) r[2]).getText()));
                }
                o.cells.add(cell);
            }
            ClassGen.ObjResult r = ClassGen.objectCode(o, known);
            if (r.errors.isEmpty() && names != null) {
                String bad = names.check(o.varName.trim());
                if (bad != null) r.errors.add(bad);
            }
            if (known.isEmpty()) r.errors.add(0, "אין עדיין מחלקות בפרויקט — קודם \"צור מחלקה\" או \"היררכיה מוכנה\"");
            return r;
        }

        void update() {
            updating = true;
            try {
                boolean arr = many.isSelected();
                classHead.setVisible(!arr);
                clsRow.setVisible(!arr);
                paramsHead.setVisible(!arr);
                params.setVisible(!arr);
                cellsHead.setVisible(arr);
                cells.setVisible(arr);
                size.setEnabled(arr);
                if (!arr) layoutParams();
                else layoutCells();
                String text = result("").code;
                if (!text.equals(preview.getText())) preview.setText(text);
                revalidate();
                repaint();
            } finally {
                updating = false;
            }
        }

        // ---------------------------------------------------------------- לבדיקות ולצילומי מסך
        public void demo(String varType, String var, String cls, String values) {
            for (int i = 0; i < type.getItemCount(); i++) if (nameOf(type.getItemAt(i)).equals(varType)) type.setSelectedIndex(i);
            name.setText(var);
            one.setSelected(true);
            for (int i = 0; i < this.cls.getItemCount(); i++) if (nameOf(this.cls.getItemAt(i)).equals(cls)) this.cls.setSelectedIndex(i);
            changed();
            List<String> vs = ClassGen.splitArgs(values);
            for (int i = 0; i < args.size() && i < vs.size(); i++) args.get(i).setText(vs.get(i));
            changed();
        }

        @SuppressWarnings("unchecked")
        public void demoArray(String varType, String var, String spec) {
            for (int i = 0; i < type.getItemCount(); i++) if (nameOf(type.getItemAt(i)).equals(varType)) type.setSelectedIndex(i);
            name.setText(var);
            many.setSelected(true);
            String[] cs = spec.split("/", -1);
            size.setValue(cs.length);
            changed();
            for (int i = 0; i < cs.length; i++) {
                int colon = cs[i].indexOf(':');
                String c = colon < 0 ? cs[i] : cs[i].substring(0, colon);
                JComboBox<Object> b = (JComboBox<Object>) cellRows.get(i)[1];
                for (int k = 0; k < b.getItemCount(); k++) if (nameOf(b.getItemAt(k)).equals(c)) b.setSelectedIndex(k);
                ((JTextField) cellRows.get(i)[2]).setText(colon < 0 ? "" : cs[i].substring(colon + 1));
            }
            changed();
        }

        public List<String> classList() {
            List<String> out = new ArrayList<String>();
            for (int i = 0; i < cls.getItemCount(); i++) out.add(nameOf(cls.getItemAt(i)));
            return out;
        }
    }
}
