package il.lamerhav.dsviz.cs;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.ComponentOrientation;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import il.lamerhav.dsviz.Scaffold;
import unit4.collectionsLib.Theme;

/**
 * החלונות של Visual Studio / Rider (ושל כל מי שעובד ב-C#): "תרגיל חדש" ו"הוספת משתנה" — אותם חלונות כמו ב-Eclipse וב-VS Code,
 * בלי להיות תלויים בסביבה. מופעלים מקיצור בשולחן העבודה / בתפריט התחל (ההתקנה יוצרת אותם):
 *
 *   java -cp dsviz.jar il.lamerhav.dsviz.cs.CsTool new    &lt;תיקיית התרגילים&gt;   — יוצר פרויקט בתיקייה Exercises, רושם ב-.sln ופותח
 *   java -cp dsviz.jar il.lamerhav.dsviz.cs.CsTool insert &lt;תיקיית התרגילים&gt;   — מעתיק ללוח קוד להדבקה בקובץ הפתוח
 *
 * לבדיקות: --preset stack_queue (מבנה מוגדר מראש), --shot קובץ.png (שומר תמונה של החלון ויוצא), --no-open.
 */
public final class CsTool {
    private CsTool() {}

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println("שימוש: CsTool new|insert <תיקיית התרגילים> [--preset מבנה] [--shot קובץ.png] [--no-open]");
            System.exit(2);
        }
        final String mode = args[0];
        final Path root = Paths.get(args[1]);
        String preset = null, shot = null;
        boolean open = true;
        for (int i = 2; i < args.length; i++) {
            if (args[i].equals("--preset") && i + 1 < args.length) preset = args[++i];
            else if (args[i].equals("--shot") && i + 1 < args.length) shot = args[++i];
            else if (args[i].equals("--no-open")) open = false;
        }
        final String fPreset = preset, fShot = shot;
        final boolean fOpen = open;
        SwingUtilities.invokeAndWait(new Runnable() {
            public void run() {
                Theme.install();
                if (mode.equals("insert")) insert(fPreset, fShot);
                else newExercise(root, fPreset, fShot, fOpen);
            }
        });
        System.exit(0);
    }

    // ------------------------------------------------------------------ תרגיל חדש

    private static void newExercise(Path root, String preset, String shot, boolean open) {
        String bad = CsProject.check(root);
        if (bad != null) {
            JOptionPane.showMessageDialog(null, bad + "\nאפשר להריץ שוב את ההתקנה (DSViz-Setup.exe) עם חבילת C#.", "תרגיל חדש — C#", JOptionPane.ERROR_MESSAGE);
            return;
        }
        ExerciseForm f = new ExerciseForm(root, preset);
        if (!f.show(shot)) return;
        try {
            Path file = CsProject.write(root, f.options.className, f.code);
            Path sln = CsProject.solution(root);
            JOptionPane.showMessageDialog(null,
                    "נוצר התרגיל " + f.options.className + ".\n\n"
                    + "ב-Visual Studio: בחלון Solution Explorer לחצו קליק ימני על " + f.options.className + " ← Set as Startup Project,\n"
                    + "ואז בתפריט שליד כפתור ההפעלה הירוק בחרו \"הרצה ויזואלית\".\n"
                    + "(אם Visual Studio כבר פתוח — אשרו Reload כשהוא שואל.)",
                    "תרגיל חדש — C#", JOptionPane.INFORMATION_MESSAGE);
            if (open && java.awt.Desktop.isDesktopSupported()) {
                java.awt.Desktop.getDesktop().open((sln != null ? sln : file).toFile());
            }
        } catch (Exception e) {
            JOptionPane.showMessageDialog(null, "לא הצלחתי ליצור את התרגיל: " + e.getMessage(), "תרגיל חדש — C#", JOptionPane.ERROR_MESSAGE);
        }
    }

    /** הטופס של "תרגיל חדש": בחירת מבנה, מה יש בכל תא, טיפוס, מה מחזירים, רקורסיה, מילוי המבנה, שמות */
    private static final class ExerciseForm extends Base {
        private final Path root;
        private final String preset;
        private final List<JToggleButton> structureButtons = new ArrayList<JToggleButton>();
        private final List<JToggleButton> insideButtons = new ArrayList<JToggleButton>();
        private final List<JToggleButton> typeButtons = new ArrayList<JToggleButton>();
        private final List<String> returnIds = new ArrayList<String>();
        private JLabel typeSection, valuesLabel, previewType, errorLabel;
        private JComboBox<String> returnCombo;
        private JCheckBox recursive, randomCheck;
        private JSpinner count;
        private JTextField values, className, functionName;
        private JPanel previewBox;
        private NestedDiagram diagram;
        private JButton ok;
        private boolean updating, classEdited, lastRandom, previewShown;
        private int lastCount, seed;
        private String lastType = "", lastStructure = "";
        Scaffold.Options options;
        String code;

        ExerciseForm(Path root, String preset) {
            this.root = root;
            this.preset = preset;
        }

        boolean show(String shot) {
            JDialog d = dialog("תרגיל חדש — C#");
            JPanel content = new JPanel(new BorderLayout(0, 8));
            content.setBorder(BorderFactory.createEmptyBorder(16, 20, 14, 20));
            JPanel head = new JPanel(new GridLayout(0, 1));
            JLabel title = new JLabel("תרגיל חדש");
            title.setFont(Theme.big);
            JLabel subtitle = new JLabel("בוחרים מבנה נתונים ואיך למלא אותו — והתרגיל נוצר, מוכן לכתיבה.");
            subtitle.setForeground(Theme.MUTED);
            head.add(title);
            head.add(subtitle);
            content.add(head, BorderLayout.NORTH);

            Col right = new Col(), left = new Col();
            String[] parts = (preset == null ? "stack" : preset).split("_");
            String initial = parts[0], initialInside = parts.length > 1 ? parts[1] : "";

            right.section("איזה מבנה נתונים?");
            JPanel structs = grid(2);
            for (Scaffold.Structure st : Scaffold.STRUCTURES.values()) {
                if (st.outer != null) continue;   // קומבינציות נבחרות בשתי בחירות: מבנה + "מה יש בכל תא?"
                chip(structs, st.label, st.id, structureButtons, st.id.equals(initial), 34, 130, st.cls != null ? st.cls : st.dims == 2 ? "int[][]" : "int[]");
            }
            right.add(structs);
            right.section("מה יש בכל תא?");
            JPanel inside = grid(2);
            for (String[] in : Scaffold.INSIDE) chip(inside, in[1], in[0], insideButtons, in[0].equals(initialInside), 28, 130, in[2]);
            right.add(inside);
            typeSection = right.section("טיפוס הערכים");
            JPanel types = grid(3);
            for (String t : Scaffold.TYPES) chip(types, t, t, typeButtons, t.equals("Integer"), 28, 84, null);
            right.add(types);

            left.section("מה הפעולה מחזירה?");
            returnCombo = ltr(new JComboBox<String>());
            returnCombo.setRenderer(new javax.swing.DefaultListCellRenderer() {
                public Component getListCellRendererComponent(javax.swing.JList<?> list, Object value, int index, boolean sel, boolean focus) {
                    JLabel l = (JLabel) super.getListCellRendererComponent(list, value, index, sel, focus);
                    l.setComponentOrientation(ComponentOrientation.LEFT_TO_RIGHT);
                    l.setHorizontalAlignment(SwingConstants.LEFT);
                    return l;
                }
            });
            left.add(returnCombo);
            left.section("רקורסיה");
            recursive = new JCheckBox("תרגיל ברקורסיה (כרטיס לכל קריאה)");
            recursive.setToolTipText("בחלון ההרצה תופיע לשונית \"רקורסיה\": כרטיס לכל קריאה");
            left.add(recursive);
            left.section("מילוי מבנה-נתונים");
            JPanel rnd = new JPanel(new FlowLayout(FlowLayout.LEADING, 6, 0));
            randomCheck = new JCheckBox("כמה ערכים אקראיים:");
            count = new JSpinner(new SpinnerNumberModel(6, 1, 20, 1));
            rnd.add(randomCheck);
            rnd.add(count);
            left.add(rnd);
            JPanel vals = new JPanel(new BorderLayout(6, 0));
            valuesLabel = new JLabel("ערכים:");
            values = ltrField(240);
            vals.add(valuesLabel, BorderLayout.LINE_START);
            vals.add(values, BorderLayout.CENTER);
            left.add(vals);

            previewBox = new JPanel(new BorderLayout(0, 2));
            JLabel pt = new JLabel("כך ייבנה (כל קבוצה, עד ;, היא מבנה פנימי אחד):");
            pt.setForeground(Theme.MUTED);
            previewType = ltr(new JLabel(" "));
            previewType.setFont(Theme.uiBold);
            JPanel top = new JPanel(new GridLayout(0, 1));
            top.add(pt);
            top.add(previewType);
            diagram = new NestedDiagram();
            previewBox.add(top, BorderLayout.NORTH);
            previewBox.add(diagram, BorderLayout.CENTER);
            previewBox.setVisible(false);
            left.add(previewBox);

            left.section("שמות");
            JPanel names = new JPanel(new FlowLayout(FlowLayout.LEADING, 6, 0));
            className = ltrField(100);
            functionName = ltrField(80);
            functionName.setText("solve");
            names.add(new JLabel("מחלקה:"));
            names.add(className);
            names.add(new JLabel("פעולה:"));
            names.add(functionName);
            left.add(names);
            JPanel where = new JPanel(new FlowLayout(FlowLayout.LEADING, 4, 0));
            JLabel whereText = new JLabel("ייווצר ב:"), wherePath = ltr(new JLabel(CsProject.exercisesDir(root).toString()));
            for (JLabel l : new JLabel[] { whereText, wherePath }) { l.setForeground(Theme.MUTED); l.setFont(Theme.small); where.add(l); }
            left.add(where);

            JPanel cols = new JPanel(new GridBagLayout());
            GridBagConstraints gc = new GridBagConstraints();
            gc.anchor = GridBagConstraints.FIRST_LINE_START;
            gc.fill = GridBagConstraints.HORIZONTAL;
            gc.gridy = 0;
            gc.gridx = 0;
            gc.weightx = 0;
            gc.insets = new Insets(0, 0, 0, 14);
            gc.fill = GridBagConstraints.BOTH;
            gc.weighty = 1;
            cols.add(new Card(right.panel), gc);
            gc.gridx = 1;
            gc.weightx = 1;
            gc.insets = new Insets(0, 0, 0, 0);
            cols.add(new Card(left.panel), gc);
            content.add(cols, BorderLayout.CENTER);

            errorLabel = new JLabel(" ");
            errorLabel.setForeground(new Color(0xC62828));
            ok = new JButton("יצירת התרגיל");
            JButton cancel = new JButton("ביטול");
            style(ok, cancel);
            JPanel south = new JPanel(new BorderLayout());
            south.add(errorLabel, BorderLayout.NORTH);
            south.add(buttons(d, ok, cancel), BorderLayout.CENTER);
            content.add(south, BorderLayout.SOUTH);
            d.setContentPane(content);
            d.getRootPane().setDefaultButton(ok);

            returnCombo.addActionListener(e -> update());
            recursive.addActionListener(e -> update());
            randomCheck.addActionListener(e -> update());
            count.addChangeListener(e -> update());
            onChange(values, this::update);
            onChange(functionName, this::update);
            onChange(className, () -> { if (className.isFocusOwner()) classEdited = true; update(); });
            onClick(ok, () -> { if (options != null) { accepted = true; d.dispose(); } });
            update();
            applyOrientation(content);
            return display(d, shot);
        }

        private void update() {
            if (updating) return;
            updating = true;
            try {
                doUpdate();
            } finally {
                updating = false;
            }
        }

        private void doUpdate() {
            String outer = selected(structureButtons, "stack"), type = selected(typeButtons, "Integer");
            // "מה יש בכל תא?": אפשרי במחסנית, תור, רשימה, עץ ומערך; במטריצה ובעץ חיפוש — רק ערכים רגילים
            boolean nestable = Scaffold.canNest(outer);
            String inside = nestable ? selected(insideButtons, "") : "";
            for (JToggleButton b : insideButtons) {
                b.setEnabled(nestable || ((String) b.getClientProperty("id")).isEmpty());
                if (((String) b.getClientProperty("id")).equals(inside)) b.setSelected(true);
            }
            String st = Scaffold.comboId(outer, inside);
            Scaffold.Structure s = Scaffold.STRUCTURES.get(st);
            boolean combo = s.outer != null;
            // מבנה בתוך מבנה: רק מספרים / מילים / תווים (לא תלמידים ומכוניות), בלי ערכים אקראיים
            for (JToggleButton b : typeButtons) {
                String t = (String) b.getClientProperty("id");
                b.setEnabled(!(combo && (t.equals("Student") || t.equals("Car"))));
            }
            if (combo && (type.equals("Student") || type.equals("Car"))) {
                type = "Integer";
                for (JToggleButton b : typeButtons) if (b.getClientProperty("id").equals("Integer")) b.setSelected(true);
            }
            typeSection.setText(combo ? "טיפוס הערכים (בתוך המבנים הפנימיים)" : "טיפוס הערכים");
            boolean objects = type.equals("Student") || type.equals("Car");

            // רשימת "מה מחזירים" תלויה בטיפוס ובמבנה
            String prevRet = returnIds.isEmpty() ? "void" : returnIds.get(Math.max(0, returnCombo.getSelectedIndex()));
            returnIds.clear();
            returnCombo.removeAllItems();
            addReturn("void", "void — לא מחזירה ערך");
            addReturn("int", "int — מספר (סכום, כמות...)");
            addReturn("boolean", "boolean — אמת/שקר");
            if (!type.equals("Integer")) addReturn("element", Scaffold.primitive(type) + " — ערך מהמבנה");
            addReturn("structure", Scaffold.typeOf(s, type) + " — מבנה חדש");
            returnCombo.setSelectedIndex(Math.max(0, returnIds.indexOf(prevRet)));

            // ערכים אקראיים: בוחרים כמה, והשדה מתמלא (אפשר לשנות). תלמידים / מכוניות — תמיד נתוני דמה
            randomCheck.setEnabled(!objects && !combo);
            if (objects || combo) randomCheck.setSelected(false);
            boolean random = randomCheck.isSelected();
            count.setEnabled(random);
            randomCheck.setText(st.equals("matrix") ? "כמה שורות אקראיות:" : "כמה ערכים אקראיים:");
            int n = (Integer) count.getValue();
            if (random && (random != lastRandom || n != lastCount || !type.equals(lastType) || !st.equals(lastStructure))) {
                seed = new Random().nextInt(Integer.MAX_VALUE);
                values.setText(Scaffold.randomValues(st, type, n, seed));
            } else if (!random && (lastRandom || !type.equals(lastType) || !st.equals(lastStructure))) {
                values.setText(Scaffold.defaultValues(st, type, false));
            }
            lastRandom = random;
            lastCount = n;
            lastType = type;
            lastStructure = st;
            valuesLabel.setText(type.equals("Student") ? "כמה תלמידים:" : type.equals("Car") ? "כמה מכוניות:"
                    : combo ? (outer.equals("tree") ? "מבנים פנימיים (לפי רמות, ;):" : "מבנים פנימיים (ב-;):")
                    : st.equals("matrix") ? "ערכים (שורות ב-;):" : "ערכים:");

            List<List<String>> groups = null;
            if (combo) {
                try { groups = Scaffold.groupLiterals(values.getText(), type, outer.equals("tree")); }
                catch (IllegalArgumentException e) { groups = null; }   // הודעת השגיאה תוצג למטה
            }
            if (groups != null) {
                previewType.setText(Scaffold.typeOf(s, type));
                diagram.set(outer, inside, groups);
            }
            boolean show = groups != null;
            if (show != previewShown) {
                previewShown = show;
                previewBox.setVisible(show);
                relayout();
            }

            if (!classEdited) {
                String next = CsProject.nextName(root);
                if (!next.equals(className.getText())) className.setText(next);
            }

            Scaffold.Options o = new Scaffold.Options();
            o.structure = st;
            o.type = type;
            o.returns = returnIds.get(returnCombo.getSelectedIndex());
            o.second = false;
            o.count = 1;
            o.extra = false;
            o.recursive = recursive.isSelected();
            o.random = false;   // הערכים האקראיים כבר בשדה — נכנסים לקוד כמו שהם
            o.values = values.getText();
            o.className = className.getText().trim();
            o.functionName = functionName.getText().trim();

            String error = null;
            ScaffoldCs.Result r = null;
            try {
                r = ScaffoldCs.generate(o);
            } catch (IllegalArgumentException e) {
                error = e.getMessage();
            }
            if (error == null && CsProject.taken(root, o.className)) error = "כבר קיים תרגיל בשם " + o.className;
            errorLabel.setText(error == null ? " " : error);
            ok.setEnabled(error == null);
            options = error == null ? o : null;
            code = error == null ? r.code : null;
        }

        private void addReturn(String id, String text) {
            returnIds.add(id);
            returnCombo.addItem(text);
        }

        private String selected(List<JToggleButton> group, String fallback) {
            for (JToggleButton b : group) if (b.isSelected()) return (String) b.getClientProperty("id");
            return fallback;
        }

        /** כפתור-בחירה (כמו "צ'יפ"): לוחצים עליו ונבחר, והאחרים בקבוצה מתבטלים */
        private void chip(JPanel parent, String text, String id, List<JToggleButton> group, boolean selected, int height, int width, String tip) {
            JToggleButton b = new Chip(text);
            b.putClientProperty("id", id);
            b.setPreferredSize(new Dimension(Math.max(width, b.getPreferredSize().width), height));
            if (tip != null) b.setToolTipText(tip);
            if (!group.isEmpty()) { ButtonGroup bg = (ButtonGroup) group.get(0).getClientProperty("group"); bg.add(b); b.putClientProperty("group", bg); }
            else { ButtonGroup bg = new ButtonGroup(); bg.add(b); b.putClientProperty("group", bg); }
            group.add(b);
            b.setSelected(selected);
            b.addActionListener(e -> update());
            parent.add(b);
        }
    }

    // ------------------------------------------------------------------ הוספת משתנה

    private static void insert(String preset, String shot) {
        InsertForm f = new InsertForm(preset);
        if (!f.show(shot)) return;
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(f.code), null);
        JOptionPane.showMessageDialog(null,
                "הקוד הועתק.\nעברו ל-Visual Studio, לחצו במקום שבו רוצים את המבנה (בתוך Main) ולחצו Ctrl+V.",
                "הוספת משתנה — C#", JOptionPane.INFORMATION_MESSAGE);
    }

    /** הטופס של "הוספת משתנה": מבנה, מה יש בכל תא, טיפוס, כמה איברים, שם — והקוד נכנס ללוח */
    private static final class InsertForm extends Base {
        private final String preset;
        private final int seed = new Random().nextInt(Integer.MAX_VALUE);
        private final List<JToggleButton> structureButtons = new ArrayList<JToggleButton>();
        private final List<JToggleButton> insideButtons = new ArrayList<JToggleButton>();
        private final List<JToggleButton> typeButtons = new ArrayList<JToggleButton>();
        private JLabel sizeLabel, rowsLabel, colsLabel, typeInfo, errorLabel;
        private JSpinner size, rows, cols;
        private JTextField name;
        private JButton ok;
        private boolean updating, nameEdited;
        String code;

        InsertForm(String preset) {
            this.preset = preset == null ? "queue" : preset;
        }

        boolean show(String shot) {
            JDialog d = dialog("הוספת משתנה — C#");
            JPanel content = new JPanel(new BorderLayout(0, 8));
            content.setBorder(BorderFactory.createEmptyBorder(16, 20, 14, 20));
            Col col = new Col();
            JLabel title = new JLabel("הוספת משתנה");
            title.setFont(Theme.big);
            col.add(title, 0);
            JLabel sub = new JLabel("המבנה מוכן עם נתוני דמה — מעתיקים ומדביקים בקוד ועובדים איתו מיד.");
            sub.setForeground(Theme.MUTED);
            col.add(sub);

            col.section("מה ליצור?");
            JPanel structs = grid(4);
            for (java.util.Map.Entry<String, String> e : Scaffold.SNIPPETS.entrySet())
                chip(structs, e.getValue(), e.getKey(), structureButtons, e.getKey().equals(preset), 44, 96);
            col.add(structs);
            col.section("מה יש בכל תא?");
            JPanel inside = grid(Scaffold.INSIDE.length);
            for (String[] in : Scaffold.INSIDE) chip(inside, in[1], in[0], insideButtons, in[0].isEmpty(), 30, 96);
            col.add(inside);
            typeInfo = new JLabel(" ");
            typeInfo.setForeground(Theme.MUTED);
            col.add(typeInfo);
            col.section("טיפוס הערכים");
            JPanel types = grid(Scaffold.TYPES.size());
            for (String t : Scaffold.TYPES) chip(types, t, t, typeButtons, t.equals("Integer"), 30, 80);
            col.add(types);

            JPanel opts = new JPanel(new FlowLayout(FlowLayout.LEADING, 6, 0));
            sizeLabel = new JLabel("כמה איברים:");
            size = new JSpinner(new SpinnerNumberModel(5, 1, 30, 1));
            rowsLabel = new JLabel("שורות:");
            rows = new JSpinner(new SpinnerNumberModel(3, 1, 12, 1));
            colsLabel = new JLabel("עמודות:");
            cols = new JSpinner(new SpinnerNumberModel(4, 1, 12, 1));
            for (JComponent c : new JComponent[] { sizeLabel, size, rowsLabel, rows, colsLabel, cols }) opts.add(c);
            col.add(opts);
            JPanel nm = new JPanel(new BorderLayout(6, 0));
            name = ltrField(160);
            nm.add(new JLabel("שם המשתנה:"), BorderLayout.LINE_START);
            nm.add(name, BorderLayout.CENTER);
            col.add(nm);

            content.add(new Card(col.panel), BorderLayout.CENTER);
            errorLabel = new JLabel(" ");
            errorLabel.setForeground(new Color(0xC62828));
            ok = new JButton("העתקה ללוח");
            JButton cancel = new JButton("ביטול");
            style(ok, cancel);
            JPanel south = new JPanel(new BorderLayout());
            south.add(errorLabel, BorderLayout.NORTH);
            south.add(buttons(d, ok, cancel), BorderLayout.CENTER);
            content.add(south, BorderLayout.SOUTH);
            d.setContentPane(content);
            d.getRootPane().setDefaultButton(ok);

            for (JSpinner sp : new JSpinner[] { size, rows, cols }) sp.addChangeListener(e -> update());
            onChange(name, () -> { if (name.isFocusOwner()) nameEdited = true; update(); });
            onClick(ok, () -> { if (code != null) { accepted = true; d.dispose(); } });
            update();
            applyOrientation(content);
            return display(d, shot);
        }

        private void update() {
            if (updating) return;
            updating = true;
            try {
                String outer = selected(structureButtons, "queue");
                boolean isStudent = outer.equals("student") || outer.equals("car"), isMatrix = outer.equals("matrix");
                boolean nestable = Scaffold.canNest(outer);
                String inside = nestable ? selected(insideButtons, "") : "";
                for (JToggleButton b : insideButtons) {
                    b.setEnabled(nestable || ((String) b.getClientProperty("id")).isEmpty());
                    if (b.getClientProperty("id").equals(inside)) b.setSelected(true);
                }
                String st = Scaffold.comboId(outer, inside);
                boolean combo = !st.equals(outer);
                String type = selected(typeButtons, "Integer");
                for (JToggleButton b : typeButtons) {
                    String t = (String) b.getClientProperty("id");
                    b.setEnabled(!isStudent && !(combo && (t.equals("Student") || t.equals("Car"))));
                }
                if (combo && (type.equals("Student") || type.equals("Car"))) {
                    type = "Integer";
                    for (JToggleButton b : typeButtons) if (b.getClientProperty("id").equals("Integer")) b.setSelected(true);
                }
                typeInfo.setText(combo ? "הטיפוס: " + Scaffold.typeOf(Scaffold.STRUCTURES.get(st), type) : " ");
                sizeLabel.setText(combo ? "כמה מבנים פנימיים:" : "כמה איברים:");
                ((SpinnerNumberModel) size.getModel()).setMaximum(combo ? 6 : 30);
                if (combo && (Integer) size.getValue() > 6) size.setValue(3);
                size.setEnabled(!isStudent && !isMatrix);
                sizeLabel.setEnabled(!isStudent && !isMatrix);
                rows.setEnabled(isMatrix);
                cols.setEnabled(isMatrix);
                rowsLabel.setEnabled(isMatrix);
                colsLabel.setEnabled(isMatrix);
                if (!nameEdited) name.setText(Scaffold.freeName(Scaffold.snippetName(st), ""));

                Scaffold.SnippetOptions o = new Scaffold.SnippetOptions();
                o.structure = st;
                o.type = type;
                o.name = name.getText().trim();
                o.size = (Integer) size.getValue();
                o.rows = (Integer) rows.getValue();
                o.cols = (Integer) cols.getValue();
                o.seed = seed;
                o.indent = "        ";
                String error = null;
                try {
                    String snippet = ScaffoldCs.snippet(o);
                    // using שחסרים — מוסיפים בראש הקטע כהערה? לא: ב-C# הם בראש הקובץ, והתרגילים שנוצרים כוללים אותם
                    code = snippet;
                } catch (IllegalArgumentException e) {
                    error = e.getMessage();
                    code = null;
                }
                if (error == null && !ScaffoldCs.isIdentifier(o.name)) { error = "שם המשתנה חייב להיות באנגלית, בלי רווחים, להתחיל באות, ולא להיות מילה שמורה של C#"; code = null; }
                errorLabel.setText(error == null ? " " : error);
                ok.setEnabled(error == null);
                relayout();
            } finally {
                updating = false;
            }
        }

        private String selected(List<JToggleButton> group, String fallback) {
            for (JToggleButton b : group) if (b.isSelected()) return (String) b.getClientProperty("id");
            return fallback;
        }

        private void chip(JPanel parent, String text, String id, List<JToggleButton> group, boolean selected, int height, int width) {
            JToggleButton b = new Chip(text);
            b.putClientProperty("id", id);
            b.setPreferredSize(new Dimension(Math.max(width, b.getPreferredSize().width), height));
            ButtonGroup bg = group.isEmpty() ? new ButtonGroup() : (ButtonGroup) group.get(0).getClientProperty("group");
            bg.add(b);
            b.putClientProperty("group", bg);
            group.add(b);
            b.setSelected(selected);
            b.addActionListener(e -> update());
            parent.add(b);
        }
    }

    // ------------------------------------------------------------------ בסיס משותף לחלונות

    private abstract static class Base {
        boolean accepted;
        private JDialog dlg;

        JDialog dialog(String title) {
            dlg = new JDialog((java.awt.Frame) null, title, true);
            dlg.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
            return dlg;
        }

        void applyOrientation(JPanel content) {
            content.applyComponentOrientation(ComponentOrientation.RIGHT_TO_LEFT);
            fixLtr(content);
            clear(content);
            content.setOpaque(true);
            content.setBackground(Theme.BG);
        }

        /** כל מה שסומן ltr (קוד, שמות, טיפוסים — באנגלית): משמאל לימין ומיושר לשמאל, גם בתוך חלון מימין לשמאל */
        private void fixLtr(Component c) {
            if (c instanceof JComponent && Boolean.TRUE.equals(((JComponent) c).getClientProperty("ltr"))) {
                c.setComponentOrientation(ComponentOrientation.LEFT_TO_RIGHT);
                if (c instanceof JLabel) ((JLabel) c).setHorizontalAlignment(SwingConstants.LEFT);
                if (c instanceof JTextField) ((JTextField) c).setHorizontalAlignment(SwingConstants.LEFT);
            }
            if (c instanceof NestedDiagram) c.setComponentOrientation(ComponentOrientation.LEFT_TO_RIGHT);
            if (c instanceof Container) for (Component k : ((Container) c).getComponents()) fixLtr(k);
        }

        /** פאנלים שקופים — כדי שהכרטיסים הלבנים יראו */
        private void clear(Component c) {
            if (c instanceof JPanel && !(c instanceof Card)) ((JPanel) c).setOpaque(false);
            if (c instanceof Container) for (Component k : ((Container) c).getComponents()) clear(k);
        }

        <T extends JComponent> T ltr(T c) {
            c.putClientProperty("ltr", Boolean.TRUE);
            return c;
        }

        /** כפתור ראשי (כחול) / משני (לבן), מעוגלים */
        void style(JButton primary, JButton secondary) {
            primary.putClientProperty("FlatLaf.style", "background:#2F6FED; foreground:#ffffff; arc:14; borderWidth:0; hoverBackground:#2457C5; pressedBackground:#1E4AA8; focusedBackground:#2F6FED; disabledBackground:#B9C8EE; disabledText:#ffffff");
            secondary.putClientProperty("FlatLaf.style", "arc:14; background:#ffffff");
            primary.setFont(Theme.uiBold);
            primary.setPreferredSize(new Dimension(Math.max(150, primary.getPreferredSize().width + 20), 38));
            secondary.setPreferredSize(new Dimension(92, 38));
        }

        void relayout() {
            if (dlg == null) return;
            dlg.invalidate();
            dlg.pack();
        }

        /** מציג (מודאלי) ומחזיר true אם לחצו על הכפתור הראשי. עם shot: שומר תמונה ויוצא */
        boolean display(final JDialog d, final String shot) {
            d.pack();
            Dimension p = d.getSize();
            d.setSize(Math.max(p.width, 560), p.height);
            d.setLocationRelativeTo(null);
            if (shot != null) {
                javax.swing.Timer t = new javax.swing.Timer(700, e -> {
                    try {
                        BufferedImage img = new BufferedImage(d.getContentPane().getWidth(), d.getContentPane().getHeight(), BufferedImage.TYPE_INT_RGB);
                        java.awt.Graphics2D g = img.createGraphics();
                        d.getContentPane().paint(g);
                        g.dispose();
                        javax.imageio.ImageIO.write(img, "png", new File(shot));
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                    d.dispose();
                });
                t.setRepeats(false);
                t.start();
            }
            d.setVisible(true);
            return accepted;
        }

        JPanel buttons(final JDialog d, JButton ok, JButton cancel) {
            JPanel p = new JPanel(new FlowLayout(FlowLayout.LEADING, 8, 6));
            p.add(ok);
            p.add(cancel);
            cancel.addActionListener(e -> d.dispose());
            return p;
        }

        JPanel grid(int cols) {
            return new JPanel(new GridLayout(0, cols, 6, 6));
        }

        JTextField ltrField(int width) {
            JTextField t = ltr(new JTextField());
            t.setPreferredSize(new Dimension(width, 32));
            t.putClientProperty("FlatLaf.style", "arc:12");
            if (Theme.mono != null) t.setFont(Theme.mono);
            return t;
        }

        void onChange(final JTextField f, final Runnable r) {
            f.getDocument().addDocumentListener(new DocumentListener() {
                public void insertUpdate(DocumentEvent e) { r.run(); }
                public void removeUpdate(DocumentEvent e) { r.run(); }
                public void changedUpdate(DocumentEvent e) { r.run(); }
            });
        }

        void onClick(JButton b, final Runnable r) {
            b.addActionListener(e -> r.run());
        }
    }

    /** כפתור-בחירה מעוגל: לבן, ובבחירה — כחול בהיר עם מסגרת כחולה (כמו בחלון ההרצה) */
    private static final class Chip extends JToggleButton {
        Chip(String text) {
            super(text);
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setOpaque(false);
            setFont(Theme.ui);
            setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
        }

        @Override
        public Dimension getPreferredSize() {
            if (isPreferredSizeSet()) return super.getPreferredSize();
            java.awt.FontMetrics fm = getFontMetrics(getFont());
            return new Dimension(fm.stringWidth(getText()) + 28, 34);
        }

        @Override
        protected void paintComponent(java.awt.Graphics g0) {
            java.awt.Graphics2D g = (java.awt.Graphics2D) g0.create();
            g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(java.awt.RenderingHints.KEY_TEXT_ANTIALIASING, java.awt.RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            int w = getWidth(), h = getHeight();
            boolean on = isSelected(), enabled = isEnabled(), hover = getModel().isRollover() && enabled;
            Color fill = !enabled ? new Color(0xF6F7F9) : on ? Theme.ACCENT_SOFT : hover ? new Color(0xF4F7FD) : Color.WHITE;
            Color line = !enabled ? new Color(0xE6E9EF) : on ? Theme.ACCENT : hover ? new Color(0xB8C4DC) : Theme.BORDER;
            Color ink = !enabled ? new Color(0xB4BAC6) : on ? Theme.ACCENT_DARK : Theme.TEXT;
            g.setColor(fill);
            g.fillRoundRect(1, 1, w - 2, h - 2, 14, 14);
            g.setColor(line);
            g.setStroke(new java.awt.BasicStroke(on ? 2f : 1f));
            g.drawRoundRect(1, 1, w - 3, h - 3, 14, 14);
            g.setFont(on ? Theme.uiBold : Theme.ui);
            java.awt.FontMetrics fm = g.getFontMetrics();
            String t = getText();
            while (fm.stringWidth(t) > w - 8 && t.length() > 2) t = t.substring(0, t.length() - 2) + "…";
            g.setColor(ink);
            g.drawString(t, (w - fm.stringWidth(t)) / 2, (h - fm.getHeight()) / 2 + fm.getAscent());
            g.dispose();
        }
    }

    /** כרטיס לבן מעוגל עם מסגרת דקה */
    private static final class Card extends JPanel {
        Card(JComponent inner) {
            super(new BorderLayout());
            setOpaque(false);
            setBorder(BorderFactory.createEmptyBorder(16, 18, 18, 18));
            add(inner, BorderLayout.CENTER);
        }

        @Override
        protected void paintComponent(java.awt.Graphics g0) {
            java.awt.Graphics2D g = (java.awt.Graphics2D) g0.create();
            g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(Theme.CARD);
            g.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 22, 22);
            g.setColor(Theme.BORDER);
            g.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 22, 22);
            g.dispose();
        }
    }

    /** עמודה: שורות ממולאות לרוחב, מיושרות להתחלה (בעברית — ימין) */
    private static final class Col {
        final JPanel panel = new JPanel(new GridBagLayout());
        private int row;

        Col() {
            panel.setOpaque(false);
        }

        JLabel section(String text) {
            JLabel l = new JLabel(text);
            l.setFont(Theme.uiBold);
            l.setForeground(Theme.TEXT);
            add(l, row == 0 ? 0 : 16);
            return l;
        }

        void add(Component c) {
            add(c, 4);
        }

        void add(Component c, int top) {
            GridBagConstraints g = new GridBagConstraints();
            g.gridx = 0;
            g.gridy = row++;
            g.weightx = 1;
            g.fill = GridBagConstraints.HORIZONTAL;
            g.anchor = GridBagConstraints.FIRST_LINE_START;
            g.insets = new Insets(top, 0, 0, 0);
            panel.add(c, g);
        }
    }
}
