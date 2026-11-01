package il.lamerhav.dsviz.intellij;

import java.awt.BorderLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.event.DocumentEvent;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.ui.JBUI;

import il.lamerhav.dsviz.Scaffold;

/**
 * חלון "תרגיל חדש" (כמו ExerciseDialog ב-Eclipse): מימין בוחרים מבנה, מה יש בכל תא, טיפוס ושמות; משמאל מה הפעולה מחזירה,
 * רקורסיה ומילוי המבנה (ובמבנה בתוך מבנה — תרשים של מה שייבנה). "יצירת התרגיל" יוצר את הקובץ בתיקיית הקוד שנבחרה.
 */
final class ExerciseDialog extends Modern.Dialog {
    /** תיקיית קוד אפשרית: התיקייה, ושם להצגה */
    static final class Target {
        final File dir;
        final String label;

        Target(File dir, String label) {
            this.dir = dir;
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    private final String preset;
    private final List<Target> targets;
    private final Forms.Chips structures = new Forms.Chips(2), insides = new Forms.Chips(2), types = new Forms.Chips(3);
    private final List<String> returnIds = new ArrayList<String>();
    private ComboBox<String> returnCombo;
    private ComboBox<Target> targetCombo;
    private JBCheckBox randomCheck, recursiveCheck;
    private boolean wantRandom = true, updating, classEdited, lastRandom;
    private JSpinner count;
    private JBTextField values, className, functionName;
    private JBLabel valuesLabel, typeSection, errorLabel, previewType;
    private JPanel previewBox, root;
    private PreviewCanvas preview;
    private int lastCount = -1, seed = new Random().nextInt(Integer.MAX_VALUE);
    private String lastType = "", lastStructure = "";
    private Scaffold.Options result;

    ExerciseDialog(Project project, List<Target> targets, String preset) {
        super(project);
        this.targets = targets;
        this.preset = preset;
        setTitle("תרגיל חדש");
        setOKButtonText("יצירת התרגיל");
        setCancelButtonText("ביטול");
        init();
        update();
    }

    @Override
    protected JComponent createCenterPanel() {
        root = new JPanel(new BorderLayout(0, JBUI.scale(4)));
        root.add(Forms.header("תרגיל חדש", "בוחרים מבנה נתונים ואיך למלא אותו — והתרגיל נוצר, מוכן לכתיבה."), BorderLayout.NORTH);
        Forms.Column right = new Forms.Column(), left = new Forms.Column();
        String[] parts = (preset == null ? "stack" : preset).split("_");
        String initial = parts[0], initialInside = parts.length > 1 ? parts[1] : "";
        right.section("איזה מבנה נתונים?");
        for (Scaffold.Structure st : Scaffold.STRUCTURES.values()) {
            if (st.outer != null) continue;   // קומבינציות: מבנה + "מה יש בכל תא?"
            structures.add(st.label, st.id, st.cls != null ? st.cls : st.dims == 2 ? "int[][]" : "int[]", st.id.equals(initial), this::update);
        }
        right.add(structures.panel);
        right.section("מה יש בכל תא?");
        for (String[] in : Scaffold.INSIDE) insides.add(in[1], in[0], in[2], in[0].equals(initialInside), this::update);
        right.add(insides.panel);
        typeSection = right.section("טיפוס הערכים");
        for (String t : Scaffold.TYPES) types.add(t, t, null, t.equals("Integer"), this::update);
        right.add(types.panel);

        left.section("מה הפעולה מחזירה?");
        returnCombo = new ComboBox<String>();
        left.add(returnCombo);
        left.section("רקורסיה");
        recursiveCheck = new JBCheckBox("תרגיל ברקורסיה (כרטיס לכל קריאה)");
        recursiveCheck.setToolTipText("בחלון ההרצה תופיע לשונית \"רקורסיה\": כרטיס לכל קריאה");
        left.add(recursiveCheck);
        left.section("מילוי מבנה-נתונים");
        randomCheck = new JBCheckBox("כמה ערכים אקראיים:");
        count = new JSpinner(new SpinnerNumberModel(6, 1, 20, 1));
        left.add(Forms.row(randomCheck, count));
        valuesLabel = new JBLabel("ערכים:");
        values = new JBTextField(22);
        left.add(Forms.labeled(valuesLabel, values));
        previewBox = new JPanel(new BorderLayout(0, JBUI.scale(2)));
        JPanel pt = new JPanel(new java.awt.GridLayout(0, 1));
        pt.add(Forms.muted("כך ייבנה (כל קבוצה, עד ;, היא מבנה פנימי אחד):"));
        previewType = new JBLabel(" ");
        previewType.setFont(previewType.getFont().deriveFont(Font.BOLD));
        pt.add(previewType);
        preview = new PreviewCanvas();
        previewBox.add(pt, BorderLayout.NORTH);
        previewBox.add(preview, BorderLayout.CENTER);
        left.add(previewBox, 8);
        // שמות ותיקייה — בעמודה הימנית (כמו ב-Eclipse), כדי שהעמודות יהיו באותו גובה והחלון ייכנס למסך קטן
        right.section("שמות");
        className = new JBTextField(10);
        functionName = new JBTextField("solve", 8);
        right.add(Forms.row(new JBLabel("מחלקה:"), className, new JBLabel("פעולה:"), functionName));
        targetCombo = new ComboBox<Target>(targets.toArray(new Target[0]));
        right.add(Forms.labeled(new JBLabel("תיקייה:"), targetCombo));

        JPanel cols = new JPanel(new GridBagLayout());
        GridBagConstraints gc = new GridBagConstraints();
        gc.fill = GridBagConstraints.BOTH;
        gc.weighty = 1;
        gc.gridx = 0;
        cols.add(Forms.card(right.done()), gc);
        gc.gridx = 1;
        gc.weightx = 1;
        gc.insets = JBUI.insets(0, 12, 0, 0);
        cols.add(Forms.card(left.done()), gc);
        root.add(cols, BorderLayout.CENTER);
        errorLabel = Forms.error();
        root.add(errorLabel, BorderLayout.SOUTH);

        returnCombo.addActionListener(e -> update());
        recursiveCheck.addActionListener(e -> update());
        randomCheck.addActionListener(e -> { wantRandom = randomCheck.isSelected(); update(); });
        count.addChangeListener(e -> update());
        targetCombo.addActionListener(e -> update());
        DocumentAdapter onText = new DocumentAdapter() {
            @Override
            protected void textChanged(DocumentEvent e) {
                update();
            }
        };
        values.getDocument().addDocumentListener(onText);
        functionName.getDocument().addDocumentListener(onText);
        className.getDocument().addDocumentListener(new DocumentAdapter() {
            @Override
            protected void textChanged(DocumentEvent e) {
                if (className.isFocusOwner()) classEdited = true;
                update();
            }
        });
        Forms.rtl(root, values, className, functionName, returnCombo, previewType, preview, targetCombo, count);
        return Modern.Dialog.wrap(root);
    }

    private void update() {
        if (updating || errorLabel == null) return;
        updating = true;
        try {
            doUpdate();
        } finally {
            updating = false;
        }
    }

    private void doUpdate() {
        String outer = structures.selected("stack"), type = types.selected("Integer");
        boolean nestable = Scaffold.canNest(outer);
        String inside = nestable ? insides.selected("") : "";
        insides.enable(id -> nestable || id.isEmpty());
        insides.select(inside);
        String st = Scaffold.comboId(outer, inside);
        Scaffold.Structure s = Scaffold.STRUCTURES.get(st);
        boolean combo = s.outer != null;
        types.enable(id -> !(combo && (id.equals("Student") || id.equals("Car"))));
        if (combo && (type.equals("Student") || type.equals("Car"))) {
            type = "Integer";
            types.select("Integer");
        }
        typeSection.setText(combo ? "טיפוס הערכים (בתוך המבנים הפנימיים)" : "טיפוס הערכים");
        boolean objects = type.equals("Student") || type.equals("Car");

        String prevRet = returnIds.isEmpty() ? "void" : returnIds.get(Math.max(0, returnCombo.getSelectedIndex()));
        returnIds.clear();
        returnCombo.removeAllItems();
        addReturn("void", "void — לא מחזירה ערך");
        addReturn("int", "int — מספר (סכום, כמות...)");
        addReturn("boolean", "boolean — אמת/שקר");
        if (!type.equals("Integer")) addReturn("element", Scaffold.primitive(type) + " — ערך מהמבנה");
        addReturn("structure", Scaffold.typeOf(s, type) + " — מבנה חדש");
        returnCombo.setSelectedIndex(Math.max(0, returnIds.indexOf(prevRet)));

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
        java.util.List<java.util.List<String>> groups = null;
        if (combo) {
            try { groups = Scaffold.groupLiterals(values.getText(), type, outer.equals("tree")); }
            catch (IllegalArgumentException e) { groups = null; }
        }
        if (groups != null) {
            previewType.setText(Scaffold.typeOf(s, type));
            preview.set(outer, inside, groups);
        }
        previewBox.setVisible(groups != null);

        Target t = (Target) targetCombo.getSelectedItem();
        File dir = t == null ? null : t.dir;
        if (!classEdited) {
            String next = nextClassName(dir);
            if (!next.equals(className.getText())) className.setText(next);
        }
        Scaffold.Options o = new Scaffold.Options();
        o.structure = st;
        o.type = type;
        o.returns = returnIds.get(Math.max(0, returnCombo.getSelectedIndex()));
        o.second = false;
        o.count = 1;
        o.extra = false;
        o.recursive = recursiveCheck.isSelected();
        o.random = false;
        o.values = values.getText();
        o.className = className.getText().trim();
        o.functionName = functionName.getText().trim();
        String error = null;
        try {
            Scaffold.generate(o);
        } catch (IllegalArgumentException e) {
            error = e.getMessage();
        }
        if (error == null && dir != null && new File(dir, o.className + ".java").exists()) error = "כבר קיים קובץ בשם " + o.className + ".java";
        errorLabel.setText(error == null ? " " : error);
        setOKActionEnabled(error == null);
        result = error == null ? o : null;
        root.revalidate();
        root.repaint();
        Forms.fit(this);
    }

    private void addReturn(String id, String text) {
        returnIds.add(id);
        returnCombo.addItem(text);
    }

    /** Targil1, Targil2 ... — השם הפנוי הבא בתיקייה */
    static String nextClassName(File dir) {
        for (int i = 1; ; i++) if (dir == null || !new File(dir, "Targil" + i + ".java").exists()) return "Targil" + i;
    }

    Scaffold.Options options() {
        return result;
    }

    Target target() {
        return (Target) targetCombo.getSelectedItem();
    }

    /** לבדיקות ולצילומי מסך */
    void choose(String structure, String inside) {
        structures.select(structure);
        insides.select(inside == null ? "" : inside);
        update();
    }
}
