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
import java.nio.file.Files;
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
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import il.lamerhav.dsviz.Scaffold;
import unit4.collectionsLib.Theme;

/**
 * החלונות של Visual Studio / Rider (ושל כל מי שעובד ב-C#): "תרגיל חדש" ו"הוסף מבנה נתונים" — אותם חלונות כמו ב-Eclipse וב-VS Code,
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
                compactFonts();
                if (mode.equals("help")) help(fShot);
                else if (mode.equals("insert")) insert(root, fPreset, fShot);
                else newExercise(root, fPreset, fShot, fOpen);
            }
        });
        System.exit(0);
    }

    /** גופנים קטנים יותר לחלונות האלה (החלון הראשי של ההרצה משתמש באותו Theme, בגודל המקורי) */
    private static void compactFonts() {
        Theme.ui = Theme.ui.deriveFont(java.awt.Font.PLAIN, 13f);
        Theme.uiBold = Theme.uiBold.deriveFont(java.awt.Font.BOLD, 13f);
        Theme.small = Theme.small.deriveFont(java.awt.Font.PLAIN, 11.5f);
        Theme.big = Theme.big.deriveFont(java.awt.Font.BOLD, 17f);
        if (Theme.mono != null) Theme.mono = Theme.mono.deriveFont(java.awt.Font.PLAIN, 12.5f);
        javax.swing.plaf.FontUIResource ui = new javax.swing.plaf.FontUIResource(Theme.ui);
        UIManager.put("defaultFont", ui);
        for (String k : new String[] { "Label.font", "Button.font", "ToggleButton.font", "CheckBox.font", "TextField.font", "ComboBox.font",
                "Spinner.font", "FormattedTextField.font", "List.font", "OptionPane.messageFont", "OptionPane.buttonFont" })
            UIManager.put(k, ui);
        UIManager.put("ToolTip.font", new javax.swing.plaf.FontUIResource(Theme.small));
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
        private JCheckBox recursive, randomCheck, sizeCheck, copyCheck;
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
            content.setBorder(BorderFactory.createEmptyBorder(12, 16, 10, 16));
            JPanel head = new JPanel(new GridLayout(0, 1));
            JLabel title = new JLabel("תרגיל חדש");
            title.setFont(Theme.big);
            JLabel subtitle = new JLabel("בוחרים מבנה נתונים ואיך למלא אותו — והתרגיל נוצר, מוכן לכתיבה.");
            subtitle.setForeground(Theme.MUTED);
            head.add(title);
            head.add(subtitle);
            content.add(withHelp(head), BorderLayout.NORTH);

            Col right = new Col(), left = new Col();
            String[] parts = (preset == null ? "stack" : preset).split("_");
            String initial = parts[0], initialInside = parts.length > 1 ? parts[1] : "";

            right.section("איזה מבנה נתונים?");
            JPanel structs = grid(2);
            for (Scaffold.Structure st : Scaffold.STRUCTURES.values()) {
                if (st.outer != null) continue;   // קומבינציות נבחרות בשתי בחירות: מבנה + "מה יש בכל תא?"
                chip(structs, st.label, st.id, structureButtons, st.id.equals(initial), 30, 120, st.cls != null ? st.cls : st.dims == 2 ? "int[][]" : "int[]");
            }
            right.add(structs);
            right.section("מה יש בכל תא?");
            JPanel inside = grid(2);
            for (String[] in : Scaffold.INSIDE) chip(inside, in[1], in[0], insideButtons, in[0].equals(initialInside), 26, 120, in[2]);
            right.add(inside);
            typeSection = right.section("טיפוס הערכים");
            JPanel types = grid(3);
            for (String t : Scaffold.TYPES) chip(types, t, t, typeButtons, t.equals("Integer"), 26, 76, null);
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
            left.section("פעולות עזר (נוספות לקוד)");
            sizeCheck = new JCheckBox("Size — מחזירה את גודל המבנה, בלי לפגוע בו");
            sizeCheck.setToolTipText("פעולה שמקבלת את המבנה ומחזירה כמה איברים יש בו; המבנה נשאר כמו שהיה");
            left.add(sizeCheck);
            copyCheck = new JCheckBox("Duplicate — מחזירה עותק מדויק של המבנה, בלי לפגוע בו");
            copyCheck.setToolTipText("פעולה שמקבלת את המבנה, יוצרת עותק מדויק ומחזירה אותו; המקור נשאר כמו שהיה");
            left.add(copyCheck);
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

            right.fill();
            left.fill();
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
            content.add(scroll(cols), BorderLayout.CENTER);

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
            sizeCheck.addActionListener(e -> update());
            copyCheck.addActionListener(e -> update());
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
            // מבנה בתוך מבנה: רק מספרים / מילים / תווים (לא תלמידים ומכוניות)
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
            randomCheck.setEnabled(!objects);
            if (objects) randomCheck.setSelected(false);
            boolean random = randomCheck.isSelected();
            count.setEnabled(random);
            randomCheck.setText(combo ? "כמה מבנים פנימיים אקראיים:" : st.equals("matrix") ? "כמה שורות אקראיות:" : "כמה ערכים אקראיים:");
            int maxCount = combo ? Scaffold.MAX_INNER : 20;
            ((SpinnerNumberModel) count.getModel()).setMaximum(maxCount);
            if ((Integer) count.getValue() > maxCount) count.setValue(maxCount);
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
            // פעולות עזר: רק למחסנית / תור / רשימה / עץ (לא למערך)
            boolean helpers = Scaffold.hasHelpers(st);
            for (JCheckBox b : new JCheckBox[] { sizeCheck, copyCheck }) {
                b.setEnabled(helpers);
                if (!helpers && b.isSelected()) b.setSelected(false);
            }
            o.recursive = recursive.isSelected();
            o.helperSize = sizeCheck.isSelected();
            o.helperCopy = copyCheck.isSelected();
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

    // ------------------------------------------------------------------ הוסף מבנה נתונים

    private static void insert(Path root, String preset, String shot) {
        Path source = latestSource(root);
        String text = "";
        try { if (source != null) text = new String(Files.readAllBytes(source), java.nio.charset.StandardCharsets.UTF_8); } catch (Exception ignored) {}
        InsertForm f = new InsertForm(preset, text, source == null ? null : (root != null && source.startsWith(root) ? root.relativize(source).toString() : source.getFileName().toString()));
        if (!f.show(shot)) return;
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(f.code), null);
        JOptionPane.showMessageDialog(null,
                "הקוד הועתק.\nעברו ל-Visual Studio, לחצו במקום שבו רוצים את המבנה (בתוך Main או בתוך פעולה) ולחצו Ctrl+V.",
                "הוסף מבנה נתונים — C#", JOptionPane.INFORMATION_MESSAGE);
    }

    /** קובץ ה-.cs שנערך לאחרונה בתיקיית התרגילים (כנראה זה שפתוח ב-Visual Studio) — לבדיקה שהשם לא תפוס. null אם אין */
    static Path latestSource(Path root) {
        if (root == null || !Files.isDirectory(root)) return null;
        try (java.util.stream.Stream<Path> s = Files.walk(root, 4)) {
            return s.filter(p -> p.toString().endsWith(".cs") && !p.toString().contains("bin") && !p.toString().contains("obj"))
                    .max((a, b) -> Long.compare(a.toFile().lastModified(), b.toFile().lastModified())).orElse(null);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * הטופס של "הוסף מבנה נתונים": מבנה, מה יש בכל תא, טיפוס, ערכים (או אקראיים) ושם — עם ציור חי של המבנה, והקוד נכנס ללוח.
     * ב-Visual Studio אי אפשר לדעת איפה הסמן, לכן השם נבדק מול הקובץ שנערך לאחרונה, וכשהוא כבר קיים שם — רק אזהרה.
     */
    private static final class InsertForm extends Base {
        private final String preset, docText, docName;
        private final List<JToggleButton> structureButtons = new ArrayList<JToggleButton>();
        private final List<JToggleButton> insideButtons = new ArrayList<JToggleButton>();
        private final List<JToggleButton> typeButtons = new ArrayList<JToggleButton>();
        private JLabel typeSection, valuesLabel, previewType, errorLabel, warnLabel;
        private JCheckBox randomCheck;
        private JSpinner count;
        private JTextField values, name;
        private NestedDiagram diagram;
        private JButton ok;
        private JButton regen;
        private boolean updating, lastRandom, forceRandom;
        private int lastCount, seed = new Random().nextInt(Integer.MAX_VALUE);
        private String lastType = "", lastStructure = "";
        String code;

        InsertForm(String preset, String docText, String docName) {
            this.preset = preset == null ? "queue" : preset;
            this.docText = docText == null ? "" : docText;
            this.docName = docName;
        }

        boolean show(String shot) {
            JDialog d = dialog("הוסף מבנה נתונים — C#");
            JPanel content = new JPanel(new BorderLayout(0, 8));
            content.setBorder(BorderFactory.createEmptyBorder(12, 16, 10, 16));
            JPanel head = new JPanel(new GridLayout(0, 1));
            JLabel title = new JLabel("הוסף מבנה נתונים");
            title.setFont(Theme.big);
            JLabel subtitle = new JLabel("בוחרים מבנה, ערכים ושם — רואים איך הוא ייראה, והקוד מועתק להדבקה ב-Visual Studio.");
            subtitle.setForeground(Theme.MUTED);
            head.add(title);
            head.add(subtitle);
            content.add(withHelp(head), BorderLayout.NORTH);

            Col right = new Col(), left = new Col();
            String[] parts = preset.split("_");
            String initial = parts[0], initialInside = parts.length > 1 ? parts[1] : "";
            right.section("איזה מבנה נתונים?");
            JPanel structs = grid(2);
            for (java.util.Map.Entry<String, String> e : Scaffold.SNIPPETS.entrySet())
                chip(structs, e.getValue(), e.getKey(), structureButtons, e.getKey().equals(initial), 30, 120);
            right.add(structs);
            right.section("מה יש בכל תא?");
            JPanel inside = grid(2);
            for (String[] in : Scaffold.INSIDE) chip(inside, in[1], in[0], insideButtons, in[0].equals(initialInside), 26, 120);
            right.add(inside);
            typeSection = right.section("טיפוס הערכים");
            JPanel types = grid(3);
            for (String t : Scaffold.TYPES) chip(types, t, t, typeButtons, t.equals("Integer"), 26, 76);
            right.add(types);

            left.section("מילוי מבנה-נתונים");
            JPanel rnd = new JPanel(new FlowLayout(FlowLayout.LEADING, 6, 0));
            randomCheck = new JCheckBox("כמה ערכים אקראיים:");
            count = new JSpinner(new SpinnerNumberModel(6, 1, 20, 1));
            regen = new JButton("הגרלה מחדש");
            regen.putClientProperty("FlatLaf.style", "arc:12");
            rnd.add(randomCheck);
            rnd.add(count);
            rnd.add(regen);
            left.add(rnd);
            JPanel vals = new JPanel(new BorderLayout(6, 0));
            valuesLabel = new JLabel("ערכים:");
            values = ltrField(240);
            vals.add(valuesLabel, BorderLayout.LINE_START);
            vals.add(values, BorderLayout.CENTER);
            left.add(vals);
            left.section("כך ייראה המבנה");
            previewType = ltr(new JLabel(" "));
            previewType.setFont(Theme.uiBold);
            left.add(previewType);
            diagram = new NestedDiagram();
            diagram.setMaxWidth(360);
            left.add(diagram);
            left.section("שם המשתנה");
            JPanel nm = new JPanel(new BorderLayout(6, 0));
            name = ltrField(160);
            name.setToolTipText("אם משאירים ריק — נכנסת ההצעה (באפור)");
            nm.add(new JLabel("שם:"), BorderLayout.LINE_START);
            nm.add(name, BorderLayout.CENTER);
            left.add(nm);
            warnLabel = new JLabel(" ");
            warnLabel.setForeground(new Color(0xB26A00));
            warnLabel.setFont(Theme.small);
            left.add(warnLabel);

            right.fill();
            left.fill();
            JPanel cols = new JPanel(new GridBagLayout());
            GridBagConstraints gc = new GridBagConstraints();
            gc.anchor = GridBagConstraints.FIRST_LINE_START;
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
            content.add(scroll(cols), BorderLayout.CENTER);

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

            randomCheck.addActionListener(e -> update());
            count.addChangeListener(e -> update());
            regen.addActionListener(e -> { forceRandom = true; update(); });
            onChange(values, this::update);
            onChange(name, this::update);
            onClick(ok, () -> { if (code != null) { accepted = true; d.dispose(); } });
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
            String outer = selected(structureButtons, "queue");
            boolean card = outer.equals("student") || outer.equals("car");
            boolean nestable = !card && Scaffold.canNest(outer);
            String inside = nestable ? selected(insideButtons, "") : "";
            for (JToggleButton b : insideButtons) {
                b.setEnabled(nestable || ((String) b.getClientProperty("id")).isEmpty());
                if (b.getClientProperty("id").equals(inside)) b.setSelected(true);
            }
            String st = Scaffold.comboId(outer, inside);
            boolean combo = !card && !st.equals(outer);
            boolean noObjects = combo || outer.equals("matrix");
            String type = selected(typeButtons, "Integer");
            for (JToggleButton b : typeButtons) {
                String t = (String) b.getClientProperty("id");
                b.setEnabled(!card && !(noObjects && (t.equals("Student") || t.equals("Car"))));
            }
            if (noObjects && (type.equals("Student") || type.equals("Car"))) {
                type = "Integer";
                for (JToggleButton b : typeButtons) if (b.getClientProperty("id").equals("Integer")) b.setSelected(true);
            }
            typeSection.setText(combo ? "טיפוס הערכים (בתוך המבנים הפנימיים)" : "טיפוס הערכים");
            boolean objects = !card && (type.equals("Student") || type.equals("Car"));

            randomCheck.setEnabled(!objects && !card);
            if (objects || card) randomCheck.setSelected(false);
            boolean random = randomCheck.isSelected();
            count.setEnabled(random);
            regen.setVisible(random);
            randomCheck.setText(combo ? "כמה מבנים פנימיים אקראיים:" : outer.equals("matrix") ? "כמה שורות אקראיות:" : "כמה ערכים אקראיים:");
            int maxCount = combo ? Scaffold.MAX_INNER : 20;
            ((SpinnerNumberModel) count.getModel()).setMaximum(maxCount);
            if ((Integer) count.getValue() > maxCount) count.setValue(maxCount);
            int n = (Integer) count.getValue();
            if (card) {
                if (!st.equals(lastStructure)) values.setText("");
            } else if (random && (forceRandom || random != lastRandom || n != lastCount || !type.equals(lastType) || !st.equals(lastStructure))) {
                seed = new Random().nextInt(Integer.MAX_VALUE);
                values.setText(Scaffold.randomValues(st, type, n, seed));
            } else if (!random && (lastRandom || !type.equals(lastType) || !st.equals(lastStructure))) {
                values.setText(Scaffold.defaultValues(st, type, false));
            }
            forceRandom = false;
            values.setEnabled(!card);
            lastRandom = random;
            lastCount = n;
            lastType = type;
            lastStructure = st;
            valuesLabel.setText(card ? " " : type.equals("Student") ? "כמה תלמידים:" : type.equals("Car") ? "כמה מכוניות:"
                    : combo ? (outer.equals("tree") ? "מבנים פנימיים (לפי רמות, ;):" : "מבנים פנימיים (ב-;):")
                    : outer.equals("matrix") ? "ערכים (שורות ב-;):" : "ערכים:");

            String shownType = card ? (st.equals("student") ? "Student" : "Car") : type;
            String vals = card ? null : values.getText();
            String error = null, warning = null;
            // שם: ההצעה באפור (לא תפוסה בקובץ שנערך לאחרונה); שם שהוקלד — חייב להיות חוקי, ואם הוא כבר בקובץ — אזהרה
            String suggestion = ScaffoldCs.suggestName(st, docText, -1);
            name.putClientProperty("JTextField.placeholderText", suggestion);
            name.repaint();
            String typed = name.getText().trim();
            if (!typed.isEmpty()) {
                String err = ScaffoldCs.checkName(typed, "", -1);
                if (err != null) error = err;
                else if (ScaffoldCs.checkName(typed, docText, -1) != null && docName != null)
                    warning = "שימו לב: בקובץ " + docName + " כבר יש \"" + typed + "\" — אם מדביקים באותה פעולה תהיה שגיאה";
            }
            Scaffold.Preview pv = null;
            try {
                pv = Scaffold.preview(st, shownType, vals);
            } catch (IllegalArgumentException e) {
                if (error == null) error = e.getMessage();
            }
            if (pv != null) {
                previewType.setText(ScaffoldCs.toCs(pv.type));
                diagram.set(pv);
            }
            code = null;
            if (error == null) {
                Scaffold.SnippetOptions o = new Scaffold.SnippetOptions();
                o.structure = st;
                o.type = shownType;
                o.name = typed.isEmpty() ? suggestion : typed;
                o.values = vals;
                o.indent = "        ";
                try {
                    code = ScaffoldCs.snippet(o);
                } catch (IllegalArgumentException e) {
                    error = e.getMessage();
                }
            }
            errorLabel.setText(error == null ? " " : error);
            warnLabel.setText(warning == null ? " " : warning);
            ok.setEnabled(error == null);
            relayout();
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

    // ------------------------------------------------------------------ הדרכה לשימוש (?)

    private static final String[][] HELP = {
        { "a", "\u200F01  ·  יצירת מבנה נתונים חדש", "שמים את הסמן במקום הנכון בקוד, לוחצים \"הוסף מבנה נתונים\", בוחרים מבנה, ערכים ושם — ורואים את הקוד שנוצר ואת ההרצה." },
        { "b", "\u200F02  ·  דוגמאות פתורות", "פותחים תרגיל מוכן מהרשימה, קוראים את הפתרון, ומריצים אותו בחלון ההדמיה — צעד אחרי צעד." },
        { "c", "\u200F03  ·  מבנה בתוך מבנה", "שלוש דוגמאות: מחסנית של תורים, רשימה של מחסניות ועץ של תורים — יצירת התרגיל וההרצה הוויזואלית." },
        { "d", "\u200F04  ·  מוסיפים התאמות לתרגיל", "אחרי שהתרגיל נוצר: מוסיפים ידנית מבנה נוסף, משתנה x ומערך — ורואים שגם הם מופיעים בהדמיה." },
    };

    /** התיקייה help — ליד dsviz.jar (בהתקנה: ‎%ProgramFiles%\DSViz\help), או לפי -Ddsviz.help */
    static Path helpDir() {
        String prop = System.getProperty("dsviz.help");
        if (prop != null) return Paths.get(prop);
        try {
            Path jar = Paths.get(CsTool.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            return jar.getParent().resolve("help");
        } catch (Exception e) {
            return Paths.get("help");
        }
    }

    /**
     * פותח את ההדרכה שנבחרה (id = a/b/c/d) בחלון נגן נפרד: רק הסרטון שלה, עם בחירת מהירות.
     * ב-Windows — Edge (או Chrome) במצב אפליקציה: חלון בלי לשוניות ובלי שורת כתובת. אחרת — הדפדפן הרגיל.
     */
    static void openHelp(String id) {
        try {
            Path page = helpDir().resolve(id == null ? "index.html" : "player.html");
            if (!Files.isRegularFile(page)) throw new java.io.FileNotFoundException(page.toString());
            String url = page.toUri().toString() + (id == null ? "" : "#" + id);
            if (id != null && System.getProperty("os.name", "").toLowerCase().contains("win") && openAppWindow(url)) return;
            java.awt.Desktop.getDesktop().browse(java.net.URI.create(url));
        } catch (Exception e) {
            JOptionPane.showMessageDialog(null, "לא הצלחתי לפתוח את ההדרכה: " + e.getMessage(), "הדרכה לשימוש", JOptionPane.ERROR_MESSAGE);
        }
    }

    /** חלון אפליקציה של Edge/Chrome (פרופיל נפרד — כדי שההגדרות יחולו גם כש-Edge כבר פתוח, והסרטון יתחיל לבד) */
    private static boolean openAppWindow(String url) {
        String profile = new java.io.File(System.getProperty("java.io.tmpdir"), "dsviz-player-profile").getAbsolutePath();
        String[] roots = { System.getenv("ProgramFiles(x86)"), System.getenv("ProgramFiles"), System.getenv("LOCALAPPDATA") };
        String[] rel = { "Microsoft\\Edge\\Application\\msedge.exe", "Google\\Chrome\\Application\\chrome.exe" };
        for (String r : rel) {
            for (String root : roots) {
                if (root == null) continue;
                java.io.File exe = new java.io.File(root, r);
                if (!exe.isFile()) continue;
                try {
                    new ProcessBuilder(exe.getPath(), "--app=" + url, "--window-size=1180,780", "--user-data-dir=" + profile,
                            "--autoplay-policy=no-user-gesture-required", "--no-first-run", "--disable-extensions").start();
                    return true;
                } catch (Exception e) {
                    // לא הצליח להפעיל — מנסים את הבא
                }
            }
        }
        return false;
    }

    private static void help(String shot) {
        HelpForm f = new HelpForm();
        if (f.show(shot)) openHelp(f.chosen);
    }

    /** חלון "הדרכה לשימוש": בוחרים הדרכה, והסרטון נפתח בדפדפן */
    private static final class HelpForm extends Base {
        private final List<JToggleButton> chips = new ArrayList<JToggleButton>();
        String chosen = "a";

        boolean show(String shot) {
            JDialog d = dialog("הדרכה לשימוש");
            JPanel content = new JPanel(new BorderLayout(0, 8));
            content.setBorder(BorderFactory.createEmptyBorder(12, 16, 10, 16));
            JPanel head = new JPanel(new GridLayout(0, 1));
            JLabel title = new JLabel("הדרכה לשימוש");
            title.setFont(Theme.big);
            JLabel sub = new JLabel("בוחרים הדרכה — לכל אחת סרטון קצר שמראה בדיוק איפה לוחצים ומה נפתח.");
            sub.setForeground(Theme.MUTED);
            head.add(title);
            head.add(sub);
            content.add(head, BorderLayout.NORTH);
            Col col = new Col();
            col.section("איזו הדרכה?");
            JPanel list = new JPanel(new GridLayout(0, 1, 6, 8));
            ButtonGroup bg = new ButtonGroup();
            for (int i = 0; i < HELP.length; i++) {
                JToggleButton b = new Chip(HELP[i][1]);
                b.putClientProperty("id", HELP[i][0]);
                b.setPreferredSize(new Dimension(420, 40));
                bg.add(b);
                chips.add(b);
                b.setSelected(i == 0);
                b.addActionListener(e -> update());
                list.add(b);
            }
            col.add(list);
            col.fill();
            content.add(new Card(col.panel), BorderLayout.CENTER);
            JButton ok = new JButton("צפייה בהדרכה"), cancel = new JButton("ביטול");
            style(ok, cancel);
            content.add(buttons(d, ok, cancel), BorderLayout.SOUTH);
            d.setContentPane(content);
            d.getRootPane().setDefaultButton(ok);
            onClick(ok, () -> { accepted = true; d.dispose(); });
            update();
            applyOrientation(content);
            return display(d, shot);
        }

        private void update() {
            for (JToggleButton b : chips) if (b.isSelected()) chosen = (String) b.getClientProperty("id");
            relayout();
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
            primary.setPreferredSize(new Dimension(Math.max(130, primary.getPreferredSize().width + 16), 34));
            secondary.setPreferredSize(new Dimension(84, 34));
        }

        void relayout() {
            if (dlg == null) return;
            dlg.invalidate();
            fit(dlg);
        }

        /** אזור גלילה אנכי: אם התוכן גבוה מהמסך (מסך קטן / הגדלת תצוגה) מופיע פס גלילה, והכפתורים למטה נשארים גלויים */
        JScrollPane scroll(JComponent inner) {
            ScrollPanel holder = new ScrollPanel(inner);
            JScrollPane sp = new JScrollPane(holder, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
            sp.setBorder(BorderFactory.createEmptyBorder());
            // חלון הצפייה אטום ובאותו צבע כמו הרקע, ומצב גלילה בתמונה שמורה: גלילה שמעתיקה פיקסלים במקום לצייר מחדש את כל הרכיבים
            // (חלון שקוף גורם לציור מחדש של כל מה שמאחוריו בכל צעד, וזה נראה כמריחה)
            sp.setOpaque(true);
            sp.setBackground(Theme.BG);
            sp.getViewport().setOpaque(true);
            sp.getViewport().setBackground(Theme.BG);
            sp.getViewport().setScrollMode(javax.swing.JViewport.BACKINGSTORE_SCROLL_MODE);
            sp.getVerticalScrollBar().setUnitIncrement(18);
            return sp;
        }

        /** גודל החלון לפי התוכן, אבל לא יותר מהמסך */
        void fit(JDialog d) {
            d.pack();
            java.awt.Rectangle scr = java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
            Dimension s = d.getSize();
            int maxH = scr.height - 40, maxW = scr.width - 40;
            if (s.height > maxH || s.width > maxW)
                d.setSize(Math.min(s.width + (s.height > maxH ? 18 : 0), maxW), Math.min(s.height, maxH));   // 18: מקום לפס הגלילה
        }

        /** כותרת החלון עם כפתור "?" (הדרכה לשימוש) בצד */
        JPanel withHelp(JPanel head) {
            JButton q = new JButton("?");
            q.setToolTipText("הדרכה לשימוש");
            q.setFocusable(false);
            q.setFont(Theme.uiBold);
            q.putClientProperty("FlatLaf.style", "arc:999; background:#F59E0B; foreground:#ffffff; borderWidth:0; hoverBackground:#D97706; margin:0,0,0,0");
            q.setPreferredSize(new Dimension(34, 34));
            q.addActionListener(e -> openHelp(null));
            JPanel row = new JPanel(new BorderLayout(8, 0));
            JPanel qbox = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
            qbox.add(q);
            row.add(head, BorderLayout.CENTER);
            row.add(qbox, BorderLayout.LINE_END);
            return row;
        }

        /** מציג (מודאלי) ומחזיר true אם לחצו על הכפתור הראשי. עם shot: שומר תמונה ויוצא */
        boolean display(final JDialog d, final String shot) {
            fit(d);
            Dimension p = d.getSize();
            d.setSize(Math.min(Math.max(p.width, 560), java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds().width - 40), p.height);
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
            JPanel row = new JPanel(new BorderLayout());   // זכויות יוצרים — בקצה השני של שורת הכפתורים
            row.add(p, BorderLayout.CENTER);
            JLabel c = ltr(new JLabel(Theme.COPYRIGHT));
            c.setFont(Theme.small);
            c.setForeground(new java.awt.Color(0xA0A8B8));
            c.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 8));
            row.add(c, BorderLayout.LINE_END);
            return row;
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
            return new Dimension(fm.stringWidth(getText()) + 24, 30);
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

    /** תוכן גלילה שרוחבו תמיד כרוחב החלון (גולל רק לאורך) */
    private static final class ScrollPanel extends JPanel implements javax.swing.Scrollable {
        ScrollPanel(JComponent inner) {
            super(new BorderLayout());
            setOpaque(false);
            add(inner, BorderLayout.CENTER);
        }

        public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
        public int getScrollableUnitIncrement(java.awt.Rectangle r, int o, int d) { return 18; }
        public int getScrollableBlockIncrement(java.awt.Rectangle r, int o, int d) { return Math.max(40, r.height - 40); }
        public boolean getScrollableTracksViewportWidth() { return true; }
        public boolean getScrollableTracksViewportHeight() { return false; }
    }

    /** כרטיס לבן מעוגל עם מסגרת דקה */
    private static final class Card extends JPanel {
        Card(JComponent inner) {
            super(new BorderLayout());
            setOpaque(false);
            setBorder(BorderFactory.createEmptyBorder(12, 14, 12, 14));
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
            add(l, row == 0 ? 0 : 10);
            return l;
        }

        void add(Component c) {
            add(c, 4);
        }

        /** שורה אחרונה שסופגת את הגובה הפנוי — כדי שהתוכן יישאר צמוד למעלה כשהעמודה נמתחת */
        void fill() {
            GridBagConstraints g = new GridBagConstraints();
            g.gridx = 0;
            g.gridy = row++;
            g.weighty = 1;
            g.fill = GridBagConstraints.VERTICAL;
            panel.add(new JPanel(), g);
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
