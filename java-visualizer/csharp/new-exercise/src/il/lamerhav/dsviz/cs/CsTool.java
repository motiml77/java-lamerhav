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

import il.lamerhav.dsviz.ClassGen;
import il.lamerhav.dsviz.RtlDialog;
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

    /**
     * ארגומנטים בלי תלות בקידוד של Windows: java מפענח את שורת הפקודה בקידוד של המערכת (ANSI), ונתיב עם שם משתמש בעברית (או תו
     * אחר שלא בקידוד) הופך ל-"?". dsviz-cs.exe מעביר אז "--args-env" והארגומנטים במשתנה הסביבה DSVIZ_ARGS (שורה לכל ארגומנט) —
     * Java קוראת את הסביבה ב-Unicode. אחרת — הארגומנטים כמו שהם.
     */
    static String[] envArgs(String[] a) {
        if (a.length == 1 && "--args-env".equals(a[0])) {
            String v = System.getenv("DSVIZ_ARGS");
            if (v != null) return v.split("\n", -1);
        }
        return a;
    }

    /**
     * תיקיית התרגילים של C# כשלא הועברה (קיצורי הדרך של ההתקנה לא מעבירים נתיב — הוא עלול להכיל עברית): מה שההתקנה כתבה
     * ב-‎~/.dsviz/project-cs-path.txt‎ (UTF-16), ואחרת "מסמכים"\DataStructures-CS
     */
    static String defaultCsRoot() {
        try {
            java.io.File f = new java.io.File(System.getProperty("user.home"), ".dsviz/project-cs-path.txt");
            if (f.isFile()) {
                byte[] b = java.nio.file.Files.readAllBytes(f.toPath());
                String s = b.length >= 2 && (b[0] & 0xff) == 0xFF && (b[1] & 0xff) == 0xFE ? new String(b, 2, b.length - 2, java.nio.charset.StandardCharsets.UTF_16LE)
                        : new String(b, java.nio.charset.StandardCharsets.UTF_8);
                s = s.replace("\uFEFF", "").trim();
                if (!s.isEmpty()) return s;
            }
        } catch (Exception ignored) {
            // ננסה את "מסמכים"
        }
        java.io.File docs = javax.swing.filechooser.FileSystemView.getFileSystemView().getDefaultDirectory();
        return new java.io.File(docs, "DataStructures-CS").getPath();
    }

    public static void main(String[] argv) throws Exception {
        il.lamerhav.dsviz.UpdateCheck.start("-FromIde");   // עדכון אוטומטי — רק כשמשתמשים ב-DSViz מ-Visual Studio
        String[] args = envArgs(argv);
        if (args.length == 1) args = new String[] { args[0], defaultCsRoot() };   // קיצור דרך בלי נתיב (עברית בנתיב)
        if (args.length < 2) {
            System.err.println("שימוש: CsTool new|insert|submit|solution|help <תיקיית התרגילים> [--preset מבנה] [--shot קובץ.png] [--no-open]");
            System.exit(2);
        }
        final String mode = args[0];
        final Path root = Paths.get(args[1]);
        String preset = null, shot = null, focusFile = null, focusMethod = null;
        int focusParams = -1;
        boolean open = true;
        for (int i = 2; i < args.length; i++) {
            if (args[i].equals("--preset") && i + 1 < args.length) preset = args[++i];
            else if (args[i].equals("--shot") && i + 1 < args.length) shot = args[++i];
            else if (args[i].equals("--no-open")) open = false;
            else if (args[i].equals("--file") && i + 1 < args.length) focusFile = args[++i];        // "הצעה לפתרון": הקובץ והפעולה שממקדים בהם (המנורה בעורך)
            else if (args[i].equals("--method") && i + 1 < args.length) focusMethod = args[++i];
            else if (args[i].equals("--params") && i + 1 < args.length) {
                try {
                    focusParams = Integer.parseInt(args[++i].trim());
                } catch (NumberFormatException ignored) {
                    focusParams = -1;
                }
            }
        }
        final String fFile = focusFile, fMethod = focusMethod;
        final int fParams = focusParams;
        final String fPreset = preset, fShot = shot;
        final boolean fOpen = open;
        SwingUtilities.invokeAndWait(new Runnable() {
            public void run() {
                Theme.install();
                Theme.useRounded();   // Rubik בחלונות של Visual Studio (תרגיל חדש, שליחה, הצעה לפתרון)
                compactFonts();
                if (mode.equals("help")) help(fShot);
                else if (mode.equals("submit")) il.lamerhav.dsviz.VsSubmit.submit(root);       // שליחת תרגיל ב-C#
                else if (mode.equals("solution")) {                                            // הצעה לפתרון ב-C# (חלון שנשאר פתוח)
                    il.lamerhav.dsviz.VsSubmit.solutions(root, fFile, fMethod, fParams);
                    return;
                }
                else if (mode.equals("insert")) insert(root, fPreset, fShot, fFile);
                else if (mode.equals("class")) createClass(root, fShot, activeFile(root, fFile));   // "צור מחלקה" ישירות
                else newExercise(root, fPreset, fShot, fOpen);
            }
        });
        if (!mode.equals("solution")) System.exit(0);   // "הצעה לפתרון" — נסגר כשסוגרים את החלון
    }

    /** שם טיפוס להצגה בחלונות של C#: Integer → int, Character → char, Double → double, String → string (גם בתוך <> ו-[,]) */
    static String csName(String java) {
        return ScaffoldCs.toCs(java).replaceAll("\\bInteger\\b", "int").replaceAll("\\bCharacter\\b", "char")
                .replaceAll("\\bDouble\\b", "double").replaceAll("\\bBoolean\\b", "bool").replaceAll("\\bString\\b", "string");
    }

    /** גופנים קטנים יותר לחלונות האלה (החלון הראשי של ההרצה משתמש באותו Theme, בגודל המקורי) */
    private static void compactFonts() {
        Theme.ui = Theme.ui.deriveFont(java.awt.Font.PLAIN, 13f);
        Theme.uiBold = Theme.uiBold.deriveFont(Theme.boldStyle(), 13f);   // Rubik Medium — בלי הדגשה מלאכותית
        Theme.small = Theme.small.deriveFont(java.awt.Font.PLAIN, 11.5f);
        Theme.big = Theme.big.deriveFont(Theme.boldStyle(), 17f);
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
        String bad = CsProject.ensure(root);
        if (bad != null) {
            RtlDialog.show(null, "תרגיל חדש — C#", RtlDialog.Kind.ERROR, "תיקיית התרגילים לא מוכנה", bad + "\nמריצים שוב את ההתקנה (DSViz-Setup.exe).");
            return;
        }
        ExerciseForm f = new ExerciseForm(root, preset);
        if (!f.show(shot)) return;
        try {
            // Visual Studio פתוח עם הפתרון: הוא עצמו מוסיף את הפרויקט (DTE) ופותחים בו את הקובץ — בלי Reload, בלי Visual Studio שני,
            // והפרויקט הופך לפרויקט ההרצה (כמו ב"שליחת תרגיל"). אחרת — רושמים בקובץ ה-.sln כרגיל, והוראות ידניות
            final boolean[] added = { false };
            if (il.lamerhav.dsviz.VsDte.enabled()) il.lamerhav.dsviz.CsSolution.openIde = new java.util.function.BiPredicate<Path, Path>() {
                public boolean test(Path s, Path p) {
                    boolean ok = il.lamerhav.dsviz.VsDte.add(s, p);
                    if (ok) added[0] = true;
                    return ok;
                }
            };
            Path file = CsProject.write(root, f.options.className, f.code);
            Path sln = CsProject.solution(root);
            boolean opened = false;
            if (added[0] && sln != null) {
                Path csproj = CsProject.exercisesDir(root).resolve(f.options.className).resolve(f.options.className + ".csproj");
                int line = 1, n = 0;
                for (String l : java.nio.file.Files.readAllLines(file, java.nio.charset.StandardCharsets.UTF_8)) {   // הסמן במקום ש"כאן כותבים את הפתרון"
                    n++;
                    if (l.contains("TODO")) { line = n; break; }
                }
                opened = il.lamerhav.dsviz.VsDte.open(file.toFile(), line, sln, csproj);
            }
            if (added[0]) {
                RtlDialog.show(null, "תרגיל חדש — C#", RtlDialog.Kind.OK, "התרגיל " + f.options.className + " נוצר",
                        "הוא נוסף לפתרון שפתוח ב-Visual Studio, והוא פרויקט ההרצה.\n"
                        + (opened ? "הקובץ כבר פתוח — כותבים במקום המסומן.\n" : "פותחים את Program.cs וכותבים במקום המסומן.\n")
                        + "מריצים: הכפתור \"הרצה ויזואלית\" בסרגל של DSViz, או הכפתור הירוק.");
            } else {
                RtlDialog.show(null, "תרגיל חדש — C#", RtlDialog.Kind.OK, "התרגיל " + f.options.className + " נוצר",
                        "ב-Solution Explorer: קליק ימני על " + f.options.className + " " + RtlDialog.ARROW + " Set as Startup Project.\n"
                        + "מריצים: בתפריט שליד הכפתור הירוק בוחרים \"הרצה ויזואלית\".\n"
                        + "אם Visual Studio כבר פתוח, מאשרים Reload.");
                if (open && java.awt.Desktop.isDesktopSupported()) {
                    java.awt.Desktop.getDesktop().open((sln != null ? sln : file).toFile());
                }
            }
        } catch (Exception e) {
            RtlDialog.show(null, "תרגיל חדש — C#", RtlDialog.Kind.ERROR, "לא הצלחתי ליצור את התרגיל", e.getMessage());
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
        private boolean wantRandom = true;   // אקראי מסומן מראש — עד שהמשתמש מוריד את הסימון
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
            for (String t : Scaffold.TYPES) chip(types, csName(t), t, typeButtons, t.equals("Integer"), 26, 76, null);   // הטקסט ב-C# (int, string...)
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
            randomCheck.addActionListener(e -> { wantRandom = randomCheck.isSelected(); update(); });
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
            addReturn("boolean", "bool — אמת/שקר");   // בשמות של C#
            if (!type.equals("Integer")) addReturn("element", csName(Scaffold.primitive(type)) + " — ערך מהמבנה");
            addReturn("structure", csName(Scaffold.typeOf(s, type)) + " — מבנה חדש");
            returnCombo.setSelectedIndex(Math.max(0, returnIds.indexOf(prevRet)));

            // ערכים אקראיים: בוחרים כמה, והשדה מתמלא (אפשר לשנות). תלמידים / מכוניות — תמיד נתוני דמה
            randomCheck.setEnabled(!objects);
            randomCheck.setSelected(!objects && wantRandom);
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
                previewType.setText(csName(Scaffold.typeOf(s, type)));
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

    // ------------------------------------------------------------------ הוסף מבנה נתונים

    private static void insert(Path root, String preset, String shot, String fileArg) {
        Path source = activeFile(root, fileArg);
        String text = "";
        try { if (source != null) text = new String(Files.readAllBytes(source), java.nio.charset.StandardCharsets.UTF_8); } catch (Exception ignored) {}
        InsertForm f = new InsertForm(preset, text, source == null ? null : (root != null && source.startsWith(root) ? root.relativize(source).toString() : source.getFileName().toString()));
        boolean ok = f.show(shot);
        if (f.wantsClass) {   // הכפתור "צור מחלקה" בחלון
            createClass(root, null, source);
            return;
        }
        if (!ok) return;
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(f.code), null);
        RtlDialog.show(null, "הוסף מבנה נתונים — C#", RtlDialog.Kind.OK, "הקוד הועתק",
                "עוברים ל-Visual Studio, לוחצים במקום של המבנה (בתוך Main או בתוך פעולה), ואז Ctrl+V.");
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
        private boolean wantRandom = true;
        private JSpinner count;
        private JTextField values, name;
        private NestedDiagram diagram;
        private JButton ok;
        private JButton regen;
        private boolean updating, lastRandom, forceRandom;
        private int lastCount, seed = new Random().nextInt(Integer.MAX_VALUE);
        private String lastType = "", lastStructure = "";
        String code;
        boolean wantsClass;   // לחצו על "צור מחלקה"

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
            // "צור מחלקה": מחלקה משלכם בקובץ חדש — החלון הזה נסגר ונפתח החלון של המחלקה
            JButton newClass = new JButton("+ צור מחלקה…");
            newClass.putClientProperty("FlatLaf.style", "arc:14; background:#ffffff; foreground:#2457C5; borderColor:#9DB7EE");
            newClass.setPreferredSize(new Dimension(newClass.getPreferredSize().width, 30));
            newClass.addActionListener(e -> { wantsClass = true; d.dispose(); });
            right.add(newClass, 8);
            JLabel classNote = new JLabel("מחלקה משלכם בקובץ חדש: שדות, בנאי, Get ו-Set");
            classNote.setFont(Theme.small);
            classNote.setForeground(Theme.MUTED);
            right.add(classNote, 2);
            right.section("מה יש בכל תא?");
            JPanel inside = grid(2);
            for (String[] in : Scaffold.INSIDE) chip(inside, in[1], in[0], insideButtons, in[0].equals(initialInside), 26, 120);
            right.add(inside);
            typeSection = right.section("טיפוס הערכים");
            JPanel types = grid(3);
            for (String t : Scaffold.TYPES) chip(types, csName(t), t, typeButtons, t.equals("Integer"), 26, 76);
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

            randomCheck.addActionListener(e -> { wantRandom = randomCheck.isSelected(); update(); });
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
            randomCheck.setSelected(!objects && !card && wantRandom);
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
                previewType.setText(csName(pv.type));
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

    // ------------------------------------------------------------------ צור מחלקה

    /**
     * הקובץ שעובדים עליו: מה ש-Visual Studio מסר (DSVIZ_VS_FILE — ההרחבה שולחת את הקובץ הפעיל), --file, ואחרת קובץ ה-‎.cs‎ שנערך לאחרונה
     * בתיקיית התרגילים. null — אין
     */
    static Path activeFile(Path root, String fileArg) {
        for (String c : new String[] { System.getenv("DSVIZ_VS_FILE"), fileArg }) {
            if (c == null || c.trim().isEmpty()) continue;
            Path p = Paths.get(c.trim());
            if (p.toString().toLowerCase(java.util.Locale.ROOT).endsWith(".cs") && Files.isRegularFile(p)) return p;
        }
        return latestSource(root);
    }

    /**
     * "צור מחלקה": חלון עם שם ושדות, והמחלקה נוצרת בקובץ חדש ליד הקובץ הנוכחי (באותו namespace). ב-Visual Studio שפתוח עם הפתרון —
     * הקובץ נפתח (ובפרויקט ישן גם נרשם בפרויקט, דרך Visual Studio עצמו); אחרת — נרשם ב-csproj (פרויקט ישן) והודעה איפה הוא
     */
    static void createClass(Path root, String shot, Path source) {
        final String title = ClassGen.TITLE + " — C#";
        if (source == null || !Files.isRegularFile(source)) {
            RtlDialog.show(null, title, RtlDialog.Kind.WARN, "לא נמצא קובץ C#",
                    "פותחים ב-Visual Studio את הקובץ שעובדים עליו (‎.cs‎) — המחלקה החדשה נוצרת לידו — ולוחצים שוב \"הוספת מבנה נתונים\" " + RtlDialog.ARROW + " \"צור מחלקה\".");
            return;
        }
        Path dir = source.toAbsolutePath().getParent();
        String[] ns = CsClassFile.namespace(source);
        String shown = root != null && source.startsWith(root) ? root.relativize(source).toString() : source.getFileName().toString();
        ClassForm f = new ClassForm(ns[0], "file".equals(ns[1]), CsClassFile.taken(dir), CsClassFile.known(dir), shown);
        if (!f.show(shot)) return;
        try {
            Path created = CsClassFile.write(dir, f.file, f.code, source);
            Path proj = CsClassFile.projectDir(dir), csproj = CsClassFile.csproj(proj);
            boolean classic = csproj != null && !CsClassFile.sdkStyle(csproj);
            String slnEnv = System.getenv("DSVIZ_VS_SLN");
            Path sln = slnEnv != null && !slnEnv.trim().isEmpty() && Files.isRegularFile(Paths.get(slnEnv.trim())) ? Paths.get(slnEnv.trim())
                    : CsClassFile.solution(proj != null ? proj : dir);
            int line = ClassGen.classLine(f.code) + 1;
            int r = il.lamerhav.dsviz.VsDte.addAndOpen(sln, source.toFile(), created.toFile(), line, classic);
            if (r == 0) return;   // נוסף (אם צריך) ונפתח ב-Visual Studio
            boolean registered = classic && CsClassFile.addCompile(csproj, created);
            String rel = proj != null ? proj.relativize(created).toString() : created.getFileName().toString();
            RtlDialog.show(null, title, RtlDialog.Kind.OK, "המחלקה " + f.resultName + " נוצרה",
                    "הקובץ: " + rel + "\n"
                    + (classic ? (registered ? "הוא נרשם בקובץ הפרויקט: " + csproj.getFileName() + "\nאם Visual Studio פתוח — מאשרים Reload.\n" : "")
                            : "הוא כבר חלק מהפרויקט (כל קובץ ‎.cs‎ בתיקייה נכלל בו).\n")
                    + "פותחים אותו ב-Solution Explorer.");
        } catch (Exception e) {
            RtlDialog.show(null, title, RtlDialog.Kind.ERROR, "לא הצלחתי ליצור את המחלקה", e.getMessage());
        }
    }

    /**
     * הטופס של "צור מחלקה" (C#): שם, השדות (טיפוס מרשימה בקבוצות או כתיבה חופשית; ליד Queue / Stack / Node / BinNode — מה שבתוכם),
     * בנאי ו-ToString — ותצוגה חיה של הקוד. המחולל: ClassGen (אותו קוד כמו ב-Eclipse, ב-IntelliJ וב-VS Code).
     */
    static final class ClassForm extends Base {
        private final String namespace, docName;
        private final boolean fileScoped;
        private final java.util.Map<String, String> taken;
        private final List<ClassGen.Known> known;
        private final List<Row> rows = new ArrayList<Row>();
        private JTextField className;
        private JPanel fieldsPanel;
        private JLabel elemHead, fileLabel, errorLabel, warnLabel;
        private javax.swing.JTextArea preview;
        private JCheckBox ctor, toStr;
        private JButton ok;
        private JDialog dlg;
        String code, file, resultName;   // אחרי "יצירת המחלקה": הקוד, Name.cs, Name

        private final class Row {
            TypeBox base, elem;
            JPanel elemPanel;
            JTextField name;
            JButton remove;
            final java.util.Map<String, String> refs = new java.util.LinkedHashMap<String, String>();

            boolean course() {
                return ClassGen.isCourse(base.value());
            }
        }

        ClassForm(String namespace, boolean fileScoped, java.util.Map<String, String> taken, List<ClassGen.Known> known, String docName) {
            this.namespace = namespace == null ? "" : namespace;
            this.fileScoped = fileScoped;
            this.taken = taken;
            this.known = known;
            this.docName = docName;
        }

        boolean show(String shot) {
            JDialog d = dialog(ClassGen.TITLE + " — C#");
            dlg = d;
            JPanel content = new JPanel(new BorderLayout(0, 8));
            content.setBorder(BorderFactory.createEmptyBorder(12, 16, 10, 16));
            JPanel head = new JPanel(new GridLayout(0, 1));
            JLabel t = new JLabel(ClassGen.TITLE);
            t.setFont(Theme.big);
            JLabel sub = new JLabel(ClassGen.subtitle(ClassGen.CS) + (docName != null ? "  ·  ליד " + docName + "\u200F" : ""));
            sub.setForeground(Theme.MUTED);
            head.add(t);
            head.add(sub);
            content.add(withHelp(head), BorderLayout.NORTH);

            Col right = new Col(), left = new Col();
            right.section("שם המחלקה");
            className = ltrField(220);
            className.putClientProperty("JTextField.placeholderText", "Student");
            right.add(className);
            right.section("השדות (private)");
            fieldsPanel = ltr(new JPanel(new GridBagLayout()));
            right.add(fieldsPanel);
            elemHead = new JLabel("בפנים");
            addRow(ClassGen.defaultType(ClassGen.CS), "", "");
            JButton add = new JButton("+ שדה");
            add.putClientProperty("FlatLaf.style", "arc:12; background:#ffffff; foreground:#2457C5; borderColor:#9DB7EE");
            add.addActionListener(e -> {
                Row r = addRow(ClassGen.defaultType(ClassGen.CS), "", "");
                r.base.text.requestFocusInWindow();
                r.base.text.selectAll();
            });
            JPanel addBox = new JPanel(new FlowLayout(FlowLayout.LEADING, 0, 2));
            addBox.add(add);
            right.add(addBox);
            ctor = new JCheckBox("בנאי שמקבל את כל השדות", true);
            right.add(ctor, 8);
            toStr = new JCheckBox(ClassGen.toStringLabel(ClassGen.CS));
            right.add(toStr);
            JLabel keys = new JLabel(ClassGen.KEYS);
            keys.setFont(Theme.small);
            keys.setForeground(Theme.MUTED);
            right.add(keys, 8);

            JPanel ph = new JPanel(new BorderLayout(8, 0));
            JLabel pt = new JLabel("כך תיראה המחלקה");
            pt.setFont(Theme.uiBold);
            ph.add(pt, BorderLayout.LINE_START);
            fileLabel = ltr(new JLabel(" "));
            fileLabel.setForeground(Theme.MUTED);
            ph.add(fileLabel, BorderLayout.CENTER);
            left.add(ph, 0);
            preview = new javax.swing.JTextArea(20, 46);
            preview.setEditable(false);
            if (Theme.mono != null) preview.setFont(Theme.mono);
            preview.setBackground(new Color(0xF7F8FA));
            preview.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
            JScrollPane ps = ltr(new JScrollPane(preview));
            ps.setPreferredSize(new Dimension(440, 380));
            ps.putClientProperty("ltr", Boolean.TRUE);
            preview.putClientProperty("ltr", Boolean.TRUE);
            left.add(ps);

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

            warnLabel = new JLabel(" ");
            warnLabel.setForeground(new Color(0xB26A00));
            errorLabel = new JLabel(" ");
            errorLabel.setForeground(new Color(0xC62828));
            ok = new JButton("יצירת המחלקה");
            JButton cancel = new JButton("ביטול");
            style(ok, cancel);
            JPanel msgs = new JPanel(new GridLayout(0, 1));
            msgs.add(warnLabel);
            msgs.add(errorLabel);
            JPanel south = new JPanel(new BorderLayout());
            south.add(msgs, BorderLayout.NORTH);
            south.add(buttons(d, ok, cancel), BorderLayout.CENTER);
            content.add(south, BorderLayout.SOUTH);
            d.setContentPane(content);

            onChange(className, this::update);
            className.addActionListener(e -> rows.get(0).name.requestFocusInWindow());
            ctor.addActionListener(e -> update());
            toStr.addActionListener(e -> update());
            onClick(ok, () -> { if (code != null) { accepted = true; d.dispose(); } });
            javax.swing.JRootPane rp = d.getRootPane();
            rp.registerKeyboardAction(e -> d.dispose(), javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
            rp.registerKeyboardAction(e -> { if (ok.isEnabled()) ok.doClick(); },
                    javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ENTER, java.awt.event.InputEvent.CTRL_DOWN_MASK), JComponent.WHEN_IN_FOCUSED_WINDOW);
            rp.putClientProperty("dsviz.form", this);   // לצילומי מסך ולבדיקות (SwingScript: call ...)
            update();
            applyOrientation(content);
            for (Row r : rows) ltrRow(r);
            return display(d, shot);
        }

        private Row addRow(String type, String inner, String fieldName) {
            final Row r = new Row();
            r.base = new TypeBox(130, () -> ClassGen.typeGroups(ClassGen.CS, known), it -> {
                if (it.pkg != null) r.refs.put(it.type.replaceAll("\\[\\]$", ""), it.pkg);
                update();
                (r.course() ? r.elem.text : r.name).requestFocusInWindow();
            }, this::update, () -> (r.course() ? r.elem.text : r.name).requestFocusInWindow());
            r.elem = new TypeBox(96, () -> ClassGen.elementGroups(ClassGen.CS, known), it -> {
                if (it.pkg != null) r.refs.put(it.type, it.pkg);
                update();
                r.name.requestFocusInWindow();
            }, this::update, () -> r.name.requestFocusInWindow());
            r.elemPanel = new JPanel(new FlowLayout(FlowLayout.LEADING, 2, 0));
            r.elemPanel.add(new JLabel("<"));
            r.elemPanel.add(r.elem);
            r.elemPanel.add(new JLabel(">"));
            r.name = ltrField(116);
            r.name.putClientProperty("JTextField.placeholderText", "name");
            r.remove = new JButton("✕");
            r.remove.setToolTipText("הסרת השדה");
            r.remove.putClientProperty("FlatLaf.style", "arc:10; background:#ffffff; margin:2,6,2,6");
            r.base.set(type);
            r.elem.set(inner);
            r.name.setText(fieldName);
            onChange(r.name, this::update);
            r.name.addActionListener(e -> {
                int i = rows.indexOf(r);
                if (i == rows.size() - 1) {
                    Row n = addRow(ClassGen.defaultType(ClassGen.CS), "", "");
                    n.base.text.requestFocusInWindow();
                    n.base.text.selectAll();
                } else rows.get(i + 1).base.text.requestFocusInWindow();
            });
            r.remove.addActionListener(e -> {
                rows.remove(r);
                if (rows.isEmpty()) addRow(ClassGen.defaultType(ClassGen.CS), "", "");
                layoutRows();
                update();
            });
            rows.add(r);
            ltrRow(r);
            layoutRows();
            update();
            return r;
        }

        private void ltrRow(Row r) {
            for (Component c : new Component[] { r.base, r.elemPanel, r.name }) c.applyComponentOrientation(ComponentOrientation.LEFT_TO_RIGHT);
        }

        /** הטבלה מחדש: כותרות, ושורה לכל שדה; העמודה "בפנים" — רק כשיש מבנה של הקורס */
        private void layoutRows() {
            fieldsPanel.removeAll();
            boolean any = false;
            for (Row r : rows) any |= r.course();
            GridBagConstraints g = new GridBagConstraints();
            g.gridy = 0;
            g.anchor = GridBagConstraints.LINE_START;
            g.insets = new Insets(0, 0, 3, 6);
            JLabel h1 = new JLabel("טיפוס"), h3 = new JLabel("שם");
            for (JLabel h : new JLabel[] { h1, elemHead, h3 }) {
                h.setFont(Theme.small);
                h.setForeground(Theme.MUTED);
            }
            g.gridx = 0;
            fieldsPanel.add(h1, g);
            if (any) {
                g.gridx = 1;
                fieldsPanel.add(elemHead, g);
            }
            g.gridx = 2;
            fieldsPanel.add(h3, g);
            for (Row r : rows) {
                g.gridy++;
                g.insets = new Insets(0, 0, 5, 6);
                g.fill = GridBagConstraints.HORIZONTAL;
                g.gridx = 0;
                fieldsPanel.add(r.base, g);
                boolean on = r.course();
                if (on && r.elem.value().trim().isEmpty()) r.elem.set(ClassGen.defaultElement(ClassGen.CS));
                if (on) {
                    g.gridx = 1;
                    fieldsPanel.add(r.elemPanel, g);
                }
                g.gridx = 2;
                g.weightx = 1;
                fieldsPanel.add(r.name, g);
                g.weightx = 0;
                g.gridx = 3;
                g.fill = GridBagConstraints.NONE;
                fieldsPanel.add(r.remove, g);
            }
            fieldsPanel.revalidate();
            fieldsPanel.repaint();
        }

        private boolean updating;

        private void update() {
            if (updating || ok == null) return;
            updating = true;
            try {
                boolean changed = false;
                for (Row r : rows) changed |= r.course() != (r.elemPanel.getParent() == fieldsPanel);
                if (changed) layoutRows();
                ClassGen.Options o = options();
                ClassGen.Check c = ClassGen.check(o);
                String text = c.ok() ? ClassGen.generate(o) : ClassGen.preview(o);
                if (!text.equals(preview.getText())) {
                    preview.setText(text);
                    preview.setCaretPosition(0);
                }
                fileLabel.setText(c.className == null ? " " : ClassGen.fileName(c.className, ClassGen.CS));
                errorLabel.setText(c.errors.isEmpty() ? " " : c.errors.get(0));
                warnLabel.setText(c.warnings.isEmpty() ? " " : String.join(" · ", c.warnings));
                ok.setEnabled(c.ok());
                code = c.ok() ? text : null;
                resultName = c.className;
                file = c.className == null ? null : ClassGen.fileName(c.className, ClassGen.CS);
                relayout();
            } finally {
                updating = false;
            }
        }

        ClassGen.Options options() {
            ClassGen.Options o = new ClassGen.Options();
            o.lang = ClassGen.CS;
            o.className = className.getText();
            o.namespace = namespace;
            o.fileScoped = fileScoped;
            o.constructor = ctor.isSelected();
            o.toString = toStr.isSelected();
            o.taken = taken;
            o.known = known;
            for (Row r : rows) {
                ClassGen.Field f = new ClassGen.Field(ClassGen.fullType(r.base.value(), r.elem.value(), ClassGen.CS), r.name.getText());
                f.refs.putAll(r.refs);
                o.fields.add(f);
            }
            return o;
        }

        // ---------------------------------------------------------------- לצילומי מסך ולבדיקות (SwingScript: call ...)

        /** ממלא: שם ושדות "string name;int grade;Queue<Student> line{Student=School.Model}" */
        void demo(String cls, String fields) {
            for (Row r : rows) {
                r.base.close();
                r.elem.close();
            }
            className.setText(cls);
            while (rows.size() > 1) rows.remove(rows.size() - 1);
            String[] fs = fields.split(";");
            for (int i = 0; i < fs.length; i++) {
                String f = fs[i].trim();
                java.util.Map<String, String> refs = new java.util.LinkedHashMap<String, String>();
                int br = f.indexOf('{');
                if (br > 0 && f.endsWith("}")) {
                    for (String x : f.substring(br + 1, f.length() - 1).split(",")) if (x.indexOf('=') > 0) refs.put(x.substring(0, x.indexOf('=')), x.substring(x.indexOf('=') + 1));
                    f = f.substring(0, br);
                }
                int sp = f.lastIndexOf(' ');
                String type = f.substring(0, sp).trim(), n = f.substring(sp + 1), base = type, inner = "";
                java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\w+)<(.+)>").matcher(type);
                if (m.matches() && ClassGen.isCourse(m.group(1))) {
                    base = m.group(1);
                    inner = m.group(2);
                }
                Row r;
                if (i == 0) {
                    r = rows.get(0);
                    r.base.set(base);
                    r.elem.set(inner);
                    r.name.setText(n);
                    r.refs.clear();
                } else r = addRow(base, inner, n);
                r.refs.putAll(refs);
            }
            layoutRows();
            update();
        }

        void openList(String row, String which) {
            Row r = rows.get(Integer.parseInt(row.trim()));
            TypeBox b = "elem".equals(which) || "1".equals(which) ? r.elem : r.base;
            b.text.requestFocusInWindow();
            b.open(true);
        }

        void withToString(String on) {
            toStr.setSelected("1".equals(on) || "true".equals(on));
            update();
        }
    }

    /**
     * שדה טיפוס עם רשימה נפתחת בקבוצות (כמו TypeField של Eclipse): כותבים חופשי, או ▼ / חץ למטה ובוחרים. כותרת אפורה לכל
     * קבוצה, הטיפוסים בגופן קוד, package / namespace באפור כשיש שני שמות זהים, וקבוצה ריקה — שורה אפורה. Enter בוחר, Esc סוגר.
     */
    static final class TypeBox extends JPanel {
        interface Groups { List<ClassGen.Group> get(); }
        interface Pick { void picked(ClassGen.Item it); }

        final JTextField text;
        private final JButton arrow;
        private final Groups groups;
        private final Pick onPick;
        private final Runnable onType, onEnter;
        private final javax.swing.DefaultListModel<Object> model = new javax.swing.DefaultListModel<Object>();
        private final javax.swing.JList<Object> list = new javax.swing.JList<Object>(model);
        private final JScrollPane listScroll = new JScrollPane(list);
        private javax.swing.Popup pop;
        private boolean internal;

        /** שורה שאינה טיפוס: כותרת קבוצה / "אין עדיין..." */
        private static final class Head {
            final String text;
            final boolean empty;
            Head(String text, boolean empty) { this.text = text; this.empty = empty; }
        }

        TypeBox(int width, Groups groups, Pick onPick, Runnable onType, Runnable onEnter) {
            super(new BorderLayout(2, 0));
            this.groups = groups;
            this.onPick = onPick;
            this.onType = onType;
            this.onEnter = onEnter;
            putClientProperty("ltr", Boolean.TRUE);
            setOpaque(false);
            text = new JTextField();
            text.putClientProperty("ltr", Boolean.TRUE);
            text.putClientProperty("FlatLaf.style", "arc:10");
            text.setPreferredSize(new Dimension(width, 30));
            if (Theme.mono != null) text.setFont(Theme.mono);
            arrow = new JButton("▼");
            arrow.setFocusable(false);
            arrow.setToolTipText("רשימת הטיפוסים");
            arrow.putClientProperty("FlatLaf.style", "arc:10; background:#ffffff; margin:2,6,2,6; font:-3");
            add(text, BorderLayout.CENTER);
            add(arrow, BorderLayout.LINE_END);
            arrow.addActionListener(e -> { if (isOpen()) close(); else { text.requestFocusInWindow(); open(true); } });
            text.getDocument().addDocumentListener(new DocumentListener() {
                public void insertUpdate(DocumentEvent e) { typed(); }
                public void removeUpdate(DocumentEvent e) { typed(); }
                public void changedUpdate(DocumentEvent e) { typed(); }
            });
            text.addKeyListener(new java.awt.event.KeyAdapter() {
                @Override
                public void keyPressed(java.awt.event.KeyEvent e) {
                    int k = e.getKeyCode();
                    if (k == java.awt.event.KeyEvent.VK_DOWN || k == java.awt.event.KeyEvent.VK_UP) {
                        e.consume();
                        if (!isOpen()) open(true);
                        move(k == java.awt.event.KeyEvent.VK_DOWN ? 1 : -1);
                    } else if (k == java.awt.event.KeyEvent.VK_ENTER && !e.isControlDown()) {
                        e.consume();
                        Object v = isOpen() ? list.getSelectedValue() : null;
                        if (v instanceof ClassGen.Item) pick((ClassGen.Item) v);
                        else { close(); onEnter.run(); }
                    } else if (k == java.awt.event.KeyEvent.VK_ESCAPE && isOpen()) {
                        e.consume();   // רק הרשימה נסגרת — לא החלון
                        close();
                    }
                }
            });
            text.addFocusListener(new java.awt.event.FocusAdapter() {
                @Override
                public void focusLost(java.awt.event.FocusEvent e) { close(); }
            });
            list.setFocusable(false);
            list.setComponentOrientation(ComponentOrientation.RIGHT_TO_LEFT);
            list.setCellRenderer(new javax.swing.ListCellRenderer<Object>() {
                public Component getListCellRendererComponent(javax.swing.JList<?> l, Object v, int i, boolean sel, boolean foc) {
                    JPanel p = new JPanel(new BorderLayout(12, 0));
                    p.setComponentOrientation(ComponentOrientation.RIGHT_TO_LEFT);
                    p.setOpaque(true);
                    if (v instanceof Head) {
                        Head h = (Head) v;
                        JLabel lb = new JLabel(h.text);
                        lb.setFont(h.empty ? Theme.small.deriveFont(Font.ITALIC) : Theme.small.deriveFont(Font.BOLD));
                        lb.setForeground(h.empty ? new Color(0x9AA3B2) : Theme.MUTED);
                        p.setBackground(Color.WHITE);
                        p.setBorder(h.empty || i == 0 ? BorderFactory.createEmptyBorder(4, 12, 4, 12)
                                : BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, Theme.BORDER), BorderFactory.createEmptyBorder(6, 12, 2, 12)));
                        p.add(lb, BorderLayout.LINE_START);
                        return p;
                    }
                    ClassGen.Item it = (ClassGen.Item) v;
                    JLabel type = new JLabel(it.type);
                    if (Theme.mono != null) type.setFont(Theme.mono);
                    type.setComponentOrientation(ComponentOrientation.LEFT_TO_RIGHT);
                    JLabel hint = new JLabel(it.hint);
                    hint.setFont(Theme.small);
                    hint.setForeground(sel ? new Color(0xDCE6FB) : new Color(0x9AA3B2));
                    type.setForeground(sel ? Color.WHITE : Theme.TEXT);
                    p.setBackground(sel ? Theme.ACCENT : Color.WHITE);
                    p.setBorder(BorderFactory.createEmptyBorder(3, 16, 3, 14));
                    p.add(type, BorderLayout.LINE_START);
                    p.add(hint, BorderLayout.LINE_END);
                    return p;
                }
            });
            list.addMouseListener(new java.awt.event.MouseAdapter() {
                @Override
                public void mousePressed(java.awt.event.MouseEvent e) {
                    int i = list.locationToIndex(e.getPoint());
                    if (i >= 0 && model.get(i) instanceof ClassGen.Item) pick((ClassGen.Item) model.get(i));
                }
            });
            list.addMouseMotionListener(new java.awt.event.MouseMotionAdapter() {
                @Override
                public void mouseMoved(java.awt.event.MouseEvent e) {
                    int i = list.locationToIndex(e.getPoint());
                    if (i >= 0 && model.get(i) instanceof ClassGen.Item && list.getSelectedIndex() != i) list.setSelectedIndex(i);
                }
            });
            listScroll.setBorder(BorderFactory.createLineBorder(Theme.BORDER));
            listScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
            listScroll.getVerticalScrollBar().setUnitIncrement(18);
        }

        String value() { return text.getText(); }

        /** כתיבה בשדה בלי לפתוח את הרשימה */
        void set(String v) {
            internal = true;
            try {
                text.setText(v == null ? "" : v);
            } finally {
                internal = false;
            }
        }

        boolean isOpen() { return pop != null; }

        void close() {
            if (pop != null) {
                pop.hide();
                pop = null;
            }
        }

        private void typed() {
            if (internal) return;
            onType.run();
            if (text.isFocusOwner()) SwingUtilities.invokeLater(() -> open(false));
        }

        private void move(int d) {
            int n = model.size(), i = list.getSelectedIndex();
            for (int k = 0; k < n; k++) {
                i = (i + d + n) % n;
                if (model.get(i) instanceof ClassGen.Item) {
                    list.setSelectedIndex(i);
                    list.ensureIndexIsVisible(i);
                    return;
                }
            }
        }

        private void pick(ClassGen.Item it) {
            close();
            set(it.type);
            text.requestFocusInWindow();
            onPick.picked(it);
        }

        /** פותח את הרשימה מתחת לשדה (all — הכול; אחרת מסונן לפי מה שכתוב) */
        void open(boolean all) {
            String q = all ? "" : text.getText().trim().toLowerCase(java.util.Locale.ROOT);
            model.clear();
            int items = 0;
            for (ClassGen.Group g : groups.get()) {
                List<ClassGen.Item> its = new ArrayList<ClassGen.Item>();
                for (ClassGen.Item it : g.items) if (q.isEmpty() || it.type.toLowerCase(java.util.Locale.ROOT).contains(q)) its.add(it);
                if (!q.isEmpty() && its.isEmpty()) continue;
                model.addElement(new Head(g.title, false));
                if (its.isEmpty() && !g.empty.isEmpty()) model.addElement(new Head(g.empty, true));
                for (ClassGen.Item it : its) model.addElement(it);
                items += its.size();
            }
            if (items == 0 && !q.isEmpty()) {
                close();
                return;
            }
            close();
            list.setVisibleRowCount(Math.min(model.size(), 15));
            list.clearSelection();
            listScroll.setPreferredSize(null);
            Dimension pref = listScroll.getPreferredSize();
            int w = Math.max(getWidth(), Math.max(pref.width + 18, 240));
            listScroll.setPreferredSize(new Dimension(w, pref.height + 2));
            java.awt.Point at = getLocationOnScreen();
            pop = javax.swing.PopupFactory.getSharedInstance().getPopup(this, listScroll, at.x, at.y + getHeight() + 2);
            pop.show();
        }
    }

    // ------------------------------------------------------------------ הדרכה לשימוש (?)

    private static final String[][] HELP = {
        { "a", "\u200F01  ·  יצירת מבנה נתונים חדש", "שמים את הסמן במקום הנכון בקוד, לוחצים \"הוסף מבנה נתונים\", בוחרים מבנה, ערכים ושם — ורואים את הקוד שנוצר ואת ההרצה." },
        { "b", "\u200F02  ·  דוגמאות פתורות", "פותחים תרגיל מוכן מהרשימה, קוראים את הפתרון, ומריצים אותו בחלון ההדמיה — צעד אחרי צעד." },
        { "c", "\u200F03  ·  מבנה בתוך מבנה", "שלוש דוגמאות: מחסנית של תורים, רשימה של מחסניות ועץ של תורים — יצירת התרגיל וההרצה הוויזואלית." },
        { "d", "\u200F04  ·  מוסיפים התאמות לתרגיל", "אחרי שהתרגיל נוצר: מוסיפים ידנית מבנה נוסף, משתנה x ומערך — ורואים שגם הם מופיעים בהדמיה." },
        { "e", "\u200F05  ·  שליחת תרגיל והצעה לפתרון", "מעלים את דף התרגיל — צילום מסך (Ctrl+V), PDF או Word — ומקבלים קוד מוכן עם פעולה לכל סעיף; ולכל סעיף \"💡 הצעה לפתרון\" שנבדקה." },
    };

    /** התיקייה help\vs (הסרטונים של Visual Studio) — ליד dsviz.jar (בהתקנה: ...\DSViz\help\vs), או לפי -Ddsviz.help */
    static Path helpDir() {
        String prop = System.getProperty("dsviz.help");
        if (prop != null) return Paths.get(prop);
        try {
            Path jar = Paths.get(CsTool.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            Path vs = jar.getParent().resolve("help").resolve("vs");   // הסרטונים של Visual Studio (C#)
            return Files.isDirectory(vs) ? vs : jar.getParent().resolve("help");
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
            RtlDialog.show(null, "הדרכה לשימוש", RtlDialog.Kind.ERROR, "לא הצלחתי לפתוח את ההדרכה", e.getMessage());
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
