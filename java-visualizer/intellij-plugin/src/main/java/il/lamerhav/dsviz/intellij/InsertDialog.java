package il.lamerhav.dsviz.intellij;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.util.Map;
import java.util.Random;

import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.event.DocumentEvent;

import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.project.Project;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTextArea;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.ui.JBUI;

import il.lamerhav.dsviz.NameCheck;
import il.lamerhav.dsviz.Scaffold;

/**
 * חלון "הוסף מבנה נתונים" (כמו InsertDialog ב-Eclipse): בוחרים מבנה (גם מבנה בתוך מבנה), טיפוס, ערכים (או אקראיים) ושם —
 * רואים איך המבנה ייראה ואת הקוד שייכנס, ואחרי אישור הוא נכנס לקוד במקום הסמן. השם מוצע באפור, ונבדק שהוא חוקי ולא מתנגש.
 */
final class InsertDialog extends Modern.Dialog {
    private final String preset, documentText, indent;
    private final int offset;
    private final Forms.Chips structures = new Forms.Chips(2), insides = new Forms.Chips(2), types = new Forms.Chips(3);
    private JBLabel typeSection, valuesLabel, previewType, errorLabel, helperNote;
    private JBCheckBox randomCheck;
    private boolean wantRandom = true;
    private JSpinner count;
    private JBTextField values, name;
    private PreviewCanvas preview;
    private JBTextArea codeView;
    private boolean updating, lastRandom;
    private int lastCount, seed = new Random().nextInt(Integer.MAX_VALUE);
    private String lastType = "", lastStructure = "";
    private String result, chosenClass;
    private JPanel root;

    InsertDialog(Project project, String preset, String documentText, int offset, String indent) {
        super(project);
        this.preset = preset == null ? "queue" : preset;
        this.documentText = documentText == null ? "" : documentText;
        this.offset = offset;
        this.indent = indent;
        setTitle("הוסף מבנה נתונים");
        setOKButtonText("הוספה לקוד");
        setCancelButtonText("ביטול");
        init();
        update();
    }

    @Override
    protected JComponent createCenterPanel() {
        root = new JPanel(new BorderLayout(0, JBUI.scale(4)));
        root.add(Forms.header("הוסף מבנה נתונים", "המבנה נבנה בקוד במקום הסמן, עם הערכים שבחרתם — רואים כאן איך הוא ייראה."), BorderLayout.NORTH);

        Forms.Column right = new Forms.Column(), left = new Forms.Column();
        String[] parts = preset.split("_");
        String initial = parts[0], initialInside = parts.length > 1 ? parts[1] : "";
        right.section("איזה מבנה נתונים?");
        for (Map.Entry<String, String> e : Scaffold.SNIPPETS.entrySet()) structures.add(e.getValue(), e.getKey(), null, e.getKey().equals(initial), this::update);
        right.add(structures.panel);
        right.section("מה יש בכל תא?");
        for (String[] in : Scaffold.INSIDE) insides.add(in[1], in[0], in[2], in[0].equals(initialInside), this::update);
        right.add(insides.panel);
        typeSection = right.section("טיפוס הערכים");
        for (String t : Scaffold.TYPES) types.add(t, t, null, t.equals("Integer"), this::update);
        right.add(types.panel);
        right.section("שם המשתנה");
        name = new JBTextField(14);
        name.setToolTipText("אם משאירים ריק — נכנסת ההצעה (באפור)");
        right.add(Forms.labeled(new JBLabel("שם:"), name));

        left.section("מילוי מבנה-נתונים");
        randomCheck = new JBCheckBox("כמה ערכים אקראיים:");
        count = new JSpinner(new SpinnerNumberModel(6, 1, 20, 1));
        left.add(Forms.row(randomCheck, count));
        valuesLabel = new JBLabel("ערכים:");
        values = new JBTextField(22);
        left.add(Forms.labeled(valuesLabel, values));
        helperNote = Forms.muted("<html>פעולות עזר מהספרייה: size(x) — כמה איברים, duplicate(x) — עותק. המבנה שנשלח לא משתנה.</html>");
        left.add(helperNote, 6);
        left.section("כך ייראה המבנה");
        previewType = new JBLabel(" ");
        previewType.setFont(previewType.getFont().deriveFont(Font.BOLD));
        left.add(previewType);
        preview = new PreviewCanvas();
        left.add(preview);
        left.section("הקוד שייכנס");
        codeView = new JBTextArea(6, 40);
        codeView.setEditable(false);
        codeView.setFont(Forms.codeFont());
        JBScrollPane codeScroll = new JBScrollPane(codeView);
        codeScroll.setPreferredSize(new Dimension(JBUI.scale(380), JBUI.scale(130)));
        codeScroll.setMinimumSize(codeScroll.getPreferredSize());
        left.add(codeScroll);

        JPanel cols = new JPanel(new GridBagLayout());
        GridBagConstraints gc = new GridBagConstraints();
        gc.fill = GridBagConstraints.BOTH;
        gc.weighty = 1;
        gc.gridx = 0;
        gc.weightx = 0;
        gc.insets = JBUI.insetsRight(0);
        cols.add(Forms.card(right.done()), gc);
        gc.gridx = 1;
        gc.weightx = 1;
        gc.insets = JBUI.insets(0, 12, 0, 0);
        cols.add(Forms.card(left.done()), gc);
        root.add(cols, BorderLayout.CENTER);
        errorLabel = Forms.error();
        root.add(errorLabel, BorderLayout.SOUTH);

        randomCheck.addActionListener(e -> { wantRandom = randomCheck.isSelected(); update(); });
        count.addChangeListener(e -> update());
        DocumentAdapter onText = new DocumentAdapter() {
            @Override
            protected void textChanged(DocumentEvent e) {
                update();
            }
        };
        values.getDocument().addDocumentListener(onText);
        name.getDocument().addDocumentListener(onText);
        Forms.rtl(root, values, name, previewType, preview, codeScroll, count);
        return Modern.Dialog.wrap(root);
    }

    @Override
    public JComponent getPreferredFocusedComponent() {
        return values;
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
        String outer = structures.selected("queue");
        boolean card = outer.equals("student") || outer.equals("car");
        boolean nestable = !card && Scaffold.canNest(outer);
        String inside = nestable ? insides.selected("") : "";
        insides.enable(id -> nestable || id.isEmpty());
        insides.select(inside);
        String st = Scaffold.comboId(outer, inside);
        boolean combo = !st.equals(outer) && !card;
        boolean noObjects = combo || outer.equals("matrix");
        String type = types.selected("Integer");
        types.enable(id -> !card && !(noObjects && (id.equals("Student") || id.equals("Car"))));
        if (noObjects && (type.equals("Student") || type.equals("Car"))) {
            type = "Integer";
            types.select("Integer");
        }
        typeSection.setText(combo ? "טיפוס הערכים (בתוך המבנים הפנימיים)" : "טיפוס הערכים");
        boolean objects = !card && (type.equals("Student") || type.equals("Car"));
        helperNote.setVisible(!card && Scaffold.hasHelpers(st));
        randomCheck.setEnabled(!objects && !card);
        randomCheck.setSelected(!objects && !card && wantRandom);
        boolean random = randomCheck.isSelected();
        count.setEnabled(random);
        randomCheck.setText(combo ? "כמה מבנים פנימיים אקראיים:" : outer.equals("matrix") ? "כמה שורות אקראיות:" : "כמה ערכים אקראיים:");
        int maxCount = combo ? Scaffold.MAX_INNER : 20;
        ((SpinnerNumberModel) count.getModel()).setMaximum(maxCount);
        if ((Integer) count.getValue() > maxCount) count.setValue(maxCount);
        int n = (Integer) count.getValue();
        if (card) {
            if (!st.equals(lastStructure)) values.setText("");
        } else if (random && (random != lastRandom || n != lastCount || !type.equals(lastType) || !st.equals(lastStructure))) {
            seed = new Random().nextInt(Integer.MAX_VALUE);
            values.setText(Scaffold.randomValues(st, type, n, seed));
        } else if (!random && (lastRandom || !type.equals(lastType) || !st.equals(lastStructure))) {
            values.setText(Scaffold.defaultValues(st, type, false));
        }
        values.setEnabled(!card);
        lastRandom = random;
        lastCount = n;
        lastType = type;
        lastStructure = st;
        valuesLabel.setText(card ? "" : type.equals("Student") ? "כמה תלמידים:" : type.equals("Car") ? "כמה מכוניות:"
                : combo ? (outer.equals("tree") ? "מבנים פנימיים (לפי רמות, ;):" : "מבנים פנימיים (ב-;):")
                : outer.equals("matrix") ? "ערכים (שורות ב-;):" : "ערכים:");

        String error = null;
        String vals = card ? null : values.getText();
        String shownType = card ? (st.equals("student") ? "Student" : "Car") : type;
        NameCheck.Ident ident = Scaffold::isIdentifier;
        String suggestion = NameCheck.suggestName(Scaffold.snippetName(st), documentText, offset, ident);
        name.getEmptyText().setText(suggestion);
        String typed = name.getText().trim();
        String nm = typed.isEmpty() ? suggestion : typed;
        if (!typed.isEmpty()) error = NameCheck.checkName(typed, documentText, offset, ident);
        Scaffold.Preview pv = null;
        try {
            pv = Scaffold.preview(st, shownType, vals);
        } catch (IllegalArgumentException e) {
            if (error == null) error = e.getMessage();
        }
        if (pv != null) {
            previewType.setText(pv.type);
            preview.set(pv);
        }
        result = null;
        chosenClass = null;
        if (error == null) {
            Scaffold.SnippetOptions o = new Scaffold.SnippetOptions();
            o.structure = st;
            o.type = shownType;
            o.name = nm;
            o.values = vals;
            o.indent = indent;
            try {
                result = Scaffold.snippet(o);
                chosenClass = card ? shownType : (objects && !outer.equals("matrix") && !combo) ? shownType : null;
            } catch (IllegalArgumentException e) {
                error = e.getMessage();
            }
        }
        codeView.setText(result == null ? "" : result.replaceAll("(?m)^" + java.util.regex.Pattern.quote(indent), ""));
        codeView.setCaretPosition(0);
        errorLabel.setText(error == null ? " " : error);
        setOKActionEnabled(error == null);
        root.revalidate();
        root.repaint();
        Forms.fit(this);
    }

    /** הקוד להכנסה (אחרי OK) */
    String code() {
        return result;
    }

    /** Student / Car אם נבחרה מחלקה מוכנה, אחרת null */
    String chosenClass() {
        return chosenClass;
    }

    /** לבדיקות ולצילומי מסך: בחירת מבנה */
    void choose(String structure, String inside) {
        structures.select(structure);
        insides.select(inside == null ? "" : inside);
        update();
    }
}
