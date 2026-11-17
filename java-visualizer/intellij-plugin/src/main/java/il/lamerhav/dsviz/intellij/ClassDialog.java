package il.lamerhav.dsviz.intellij;

import java.awt.BorderLayout;
import java.awt.ComponentOrientation;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.event.DocumentEvent;

import com.intellij.openapi.project.Project;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTextArea;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.ui.JBUI;

import il.lamerhav.dsviz.ClassGen;

/**
 * "צור מחלקה" (כמו ClassDialog ב-Eclipse): שם למחלקה, השדות שלה (טיפוס מרשימה בקבוצות או כתיבה חופשית + שם), בנאי ו-toString —
 * ותצוגה חיה של הקוד. המחולל: ClassGen (אותו קוד כמו ב-Eclipse, ב-VS Code וב-Visual Studio).
 */
final class ClassDialog extends Modern.Dialog {
    private final String pkg;
    private final Map<String, String> taken;
    private final List<ClassGen.Known> known;
    private final List<Row> rows = new ArrayList<Row>();
    private JBTextField className;
    private JPanel fieldsPanel, root;
    private JBLabel elemHead, fileLabel, errorLabel, warnLabel;
    private JBTextArea preview;
    private JBCheckBox ctor, toStr;
    private String code, file, name;

    private final class Row {
        TypeBox base, elem;
        JPanel elemPanel;
        JBTextField name;
        JButton remove;
        final Map<String, String> refs = new LinkedHashMap<String, String>();

        boolean course() {
            return ClassGen.isCourse(base.value());
        }
    }

    ClassDialog(Project project, String pkg, Map<String, String> taken, List<ClassGen.Known> known) {
        super(project);
        this.pkg = pkg == null ? "" : pkg;
        this.taken = taken == null ? new LinkedHashMap<String, String>() : taken;
        this.known = known == null ? new ArrayList<ClassGen.Known>() : known;
        setTitle(ClassGen.TITLE);
        setOKButtonText("יצירת המחלקה");
        setCancelButtonText("ביטול");
        init();
        update();
    }

    @Override
    protected JComponent createCenterPanel() {
        root = new JPanel(new BorderLayout(0, JBUI.scale(4)));
        root.add(Forms.header(ClassGen.TITLE, ClassGen.subtitle(ClassGen.JAVA)), BorderLayout.NORTH);
        Forms.Column right = new Forms.Column(), left = new Forms.Column();
        right.section("שם המחלקה");
        className = new JBTextField(18);
        className.getEmptyText().setText("Student");
        className.setFont(Forms.codeFont());
        right.add(className);
        right.section("השדות (private)");
        fieldsPanel = new JPanel(new GridBagLayout());
        right.add(fieldsPanel);
        elemHead = new JBLabel("בפנים");
        addRow(ClassGen.defaultType(ClassGen.JAVA), "", "");
        JButton add = new JButton("+ שדה");
        add.addActionListener(e -> {
            Row r = addRow(ClassGen.defaultType(ClassGen.JAVA), "", "");
            r.base.text.requestFocusInWindow();
            r.base.text.selectAll();
        });
        right.add(Forms.row(add));
        ctor = new JBCheckBox("בנאי שמקבל את כל השדות", true);
        right.add(ctor, 8);
        toStr = new JBCheckBox(ClassGen.toStringLabel(ClassGen.JAVA));
        right.add(toStr);
        right.add(Forms.muted(ClassGen.KEYS), 8);

        left.section("כך תיראה המחלקה");
        fileLabel = new JBLabel(" ");
        fileLabel.setForeground(com.intellij.util.ui.UIUtil.getContextHelpForeground());
        left.add(fileLabel);
        preview = new JBTextArea(20, 46);
        preview.setEditable(false);
        preview.setFont(Forms.codeFont());
        JBScrollPane ps = new JBScrollPane(preview);
        ps.setPreferredSize(new Dimension(JBUI.scale(440), JBUI.scale(380)));
        ps.setMinimumSize(new Dimension(JBUI.scale(300), JBUI.scale(200)));
        left.add(ps);

        JPanel cols = new JPanel(new GridBagLayout());
        GridBagConstraints gc = new GridBagConstraints();
        gc.fill = GridBagConstraints.BOTH;
        gc.weighty = 1;
        gc.gridx = 0;
        gc.weightx = 0;
        cols.add(Forms.card(right.done()), gc);
        gc.gridx = 1;
        gc.weightx = 1;
        gc.insets = JBUI.insets(0, 12, 0, 0);
        cols.add(Forms.card(left.done()), gc);
        root.add(new JBScrollPane(cols, JBScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JBScrollPane.HORIZONTAL_SCROLLBAR_NEVER) {
            {
                setBorder(JBUI.Borders.empty());
            }
        }, BorderLayout.CENTER);
        JPanel south = new JPanel(new java.awt.GridLayout(0, 1));
        warnLabel = new JBLabel(" ");
        warnLabel.setForeground(new JBColor(0x9A6700, 0xD9A441));
        errorLabel = Forms.error();
        south.add(warnLabel);
        south.add(errorLabel);
        root.add(south, BorderLayout.SOUTH);

        DocumentAdapter onText = new DocumentAdapter() {
            @Override
            protected void textChanged(DocumentEvent e) {
                update();
            }
        };
        className.getDocument().addDocumentListener(onText);
        className.addActionListener(e -> rows.get(0).name.requestFocusInWindow());   // Enter בשם — לשדה הראשון (לא יצירה)
        ctor.addActionListener(e -> update());
        toStr.addActionListener(e -> update());
        root.registerKeyboardAction(e -> { if (isOKActionEnabled()) doOKAction(); },
                KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, InputEvent.CTRL_DOWN_MASK), JComponent.WHEN_IN_FOCUSED_WINDOW);
        Forms.rtl(root, className, preview, ps, fileLabel);
        ltrRows();
        return Modern.Dialog.wrap(root);
    }

    @Override
    public JComponent getPreferredFocusedComponent() {
        return className;
    }

    private Row addRow(String type, String inner, String fieldName) {
        final Row r = new Row();
        r.base = new TypeBox(130, () -> ClassGen.typeGroups(ClassGen.JAVA, known), it -> {
            if (it.pkg != null) r.refs.put(it.type.replaceAll("\\[\\]$", ""), it.pkg);
            update();
            (r.course() ? r.elem.text : r.name).requestFocusInWindow();
        }, this::update, () -> (r.course() ? r.elem.text : r.name).requestFocusInWindow());
        r.elem = new TypeBox(100, () -> ClassGen.elementGroups(ClassGen.JAVA, known), it -> {
            if (it.pkg != null) r.refs.put(it.type, it.pkg);
            update();
            r.name.requestFocusInWindow();
        }, this::update, () -> r.name.requestFocusInWindow());
        r.elemPanel = new JPanel(new FlowLayout(FlowLayout.LEADING, JBUI.scale(2), 0));
        r.elemPanel.add(new JBLabel("<"));
        r.elemPanel.add(r.elem);
        r.elemPanel.add(new JBLabel(">"));
        r.name = new JBTextField(12);
        r.name.getEmptyText().setText("name");
        r.name.setFont(Forms.codeFont());
        r.remove = new JButton("✕");
        r.remove.setToolTipText("הסרת השדה");
        r.base.set(type);
        r.elem.set(inner);
        r.name.setText(fieldName);
        r.name.getDocument().addDocumentListener(new DocumentAdapter() {
            @Override
            protected void textChanged(DocumentEvent e) {
                update();
            }
        });
        r.name.addActionListener(e -> {   // Enter בשדה האחרון — עוד שדה; אחרת — לשורה הבאה
            int i = rows.indexOf(r);
            if (i == rows.size() - 1) {
                Row n = addRow(ClassGen.defaultType(ClassGen.JAVA), "", "");
                n.base.text.requestFocusInWindow();
                n.base.text.selectAll();
            } else rows.get(i + 1).base.text.requestFocusInWindow();
        });
        r.remove.addActionListener(e -> {
            rows.remove(r);
            if (rows.isEmpty()) addRow(ClassGen.defaultType(ClassGen.JAVA), "", "");
            layoutRows();
            update();
        });
        rows.add(r);
        layoutRows();
        ltrRows();
        update();
        return r;
    }

    private void ltrRows() {
        if (fieldsPanel == null) return;
        fieldsPanel.applyComponentOrientation(ComponentOrientation.LEFT_TO_RIGHT);
    }

    /** הטבלה מחדש: כותרות ושורה לכל שדה; העמודה "בפנים" — רק כשיש מבנה של הקורס */
    private void layoutRows() {
        fieldsPanel.removeAll();
        boolean any = false;
        for (Row r : rows) any |= r.course();
        GridBagConstraints g = new GridBagConstraints();
        g.gridy = 0;
        g.anchor = GridBagConstraints.LINE_START;
        g.insets = JBUI.insets(0, 0, 3, 6);
        JBLabel h1 = new JBLabel("טיפוס"), h3 = new JBLabel("שם");
        for (JBLabel h : new JBLabel[] { h1, elemHead, h3 }) h.setForeground(com.intellij.util.ui.UIUtil.getContextHelpForeground());
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
            g.insets = JBUI.insets(0, 0, 5, 6);
            g.fill = GridBagConstraints.HORIZONTAL;
            g.gridx = 0;
            fieldsPanel.add(r.base, g);
            if (r.course()) {
                if (r.elem.value().trim().isEmpty()) r.elem.set(ClassGen.defaultElement(ClassGen.JAVA));
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
        if (updating || errorLabel == null) return;
        updating = true;
        try {
            boolean changed = false;
            for (Row r : rows) changed |= r.course() != (r.elemPanel.getParent() == fieldsPanel);
            if (changed) {
                layoutRows();
                ltrRows();
            }
            ClassGen.Options o = options();
            ClassGen.Check c = ClassGen.check(o);
            String text = c.ok() ? ClassGen.generate(o) : ClassGen.preview(o);
            if (!text.equals(preview.getText())) {
                preview.setText(text);
                preview.setCaretPosition(0);
            }
            fileLabel.setText(c.className == null ? " " : ClassGen.fileName(c.className, ClassGen.JAVA));
            errorLabel.setText(c.errors.isEmpty() ? " " : c.errors.get(0));
            warnLabel.setText(c.warnings.isEmpty() ? " " : "‏" + String.join(" · ", c.warnings));
            setOKActionEnabled(c.ok());
            code = c.ok() ? text : null;
            name = c.className;
            file = c.className == null ? null : ClassGen.fileName(c.className, ClassGen.JAVA);
            root.revalidate();
            root.repaint();
            Forms.fit(this);
        } finally {
            updating = false;
        }
    }

    ClassGen.Options options() {
        ClassGen.Options o = new ClassGen.Options();
        o.lang = ClassGen.JAVA;
        o.className = className.getText();
        o.pkg = pkg;
        o.constructor = ctor.isSelected();
        o.toString = toStr.isSelected();
        o.taken = taken;
        o.known = known;
        for (Row r : rows) {
            ClassGen.Field f = new ClassGen.Field(ClassGen.fullType(r.base.value(), r.elem.value(), ClassGen.JAVA), r.name.getText());
            f.refs.putAll(r.refs);
            o.fields.add(f);
        }
        return o;
    }

    /** הקוד ליצירה (אחרי "יצירת המחלקה") */
    String code() {
        return code;
    }

    /** Student.java */
    String fileName() {
        return file;
    }

    String className() {
        return name;
    }

    /** השורה הראשונה של ההודעה האדומה ("" — אין שגיאה) */
    String error() {
        return errorLabel.getText().trim();
    }

    // ------------------------------------------------------------------ לבדיקות ולצילומי מסך

    /** ממלא: שם ושדות "String name;int grade;Queue<Student> line{Student=school.model}" */
    void demo(String cls, String fields) {
        className.setText(cls);
        while (rows.size() > 1) rows.remove(rows.size() - 1);
        String[] fs = fields.split(";");
        for (int i = 0; i < fs.length; i++) {
            String f = fs[i].trim();
            Map<String, String> refs = new LinkedHashMap<String, String>();
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
        ltrRows();
        update();
    }

    void withToString(boolean on) {
        toStr.setSelected(on);
        update();
    }

    /** מה ברשימת הטיפוסים של שורה (לבדיקות) */
    List<String> typeList(int row, boolean elem) {
        return (elem ? rows.get(row).elem : rows.get(row).base).rows(true);
    }
}
