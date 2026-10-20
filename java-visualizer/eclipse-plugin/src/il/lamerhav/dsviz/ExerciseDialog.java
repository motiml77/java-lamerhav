package il.lamerhav.dsviz;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.Path;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.FontData;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;

/**
 * חלון "תרגיל חדש": דף קטן — בוחרים מבנה, טיפוס ומה הפעולה מחזירה, ולוחצים "יצירת התרגיל".
 * (הקוד נוצר רק בסוף, בלי תצוגה מקדימה.)
 */
public class ExerciseDialog extends Dialog {
    private static final int COLUMNS = 4;

    private final IProject preferred;
    private final String presetStructure;
    private final List<IJavaProject> projects;
    private final String newProjectName;

    private Combo projectCombo, returnCombo;
    private final List<Button> structureButtons = new ArrayList<Button>();
    private final List<Button> typeButtons = new ArrayList<Button>();
    private Button secondCheck, extraCheck, randomCheck;
    private Text valuesText, values2Text, classText, functionText;
    private Label valuesLabel, values2Label, errorLabel;
    private Composite valuesRow;
    private final List<String> returnIds = new ArrayList<String>();
    private boolean classEdited;
    private String lastType = "", lastStructure = "";
    private Font headerFont, chipFont;

    private Scaffold.Options result;
    private IJavaProject resultProject;

    public ExerciseDialog(Shell parent, List<IJavaProject> projects, String newProjectName, IProject preferred,
                          String presetStructure) {
        super(parent);
        this.projects = projects;
        this.newProjectName = newProjectName;
        this.preferred = preferred;
        this.presetStructure = presetStructure;
        setShellStyle(SWT.DIALOG_TRIM | SWT.RIGHT_TO_LEFT | SWT.APPLICATION_MODAL);
    }

    @Override
    protected void configureShell(Shell shell) {
        super.configureShell(shell);
        shell.setText("תרגיל חדש");
    }

    @Override
    protected Control createDialogArea(Composite parent) {
        Composite area = (Composite) super.createDialogArea(parent);
        GridLayout al = (GridLayout) area.getLayout();
        al.marginWidth = 22;
        al.marginHeight = 16;
        al.verticalSpacing = 10;

        FontData hd = JFaceResources.getDialogFont().getFontData()[0];
        headerFont = new Font(area.getDisplay(), hd.getName(), hd.getHeight() + 6, SWT.BOLD);
        chipFont = new Font(area.getDisplay(), hd.getName(), hd.getHeight() + 1, SWT.NORMAL);
        area.addDisposeListener(e -> { headerFont.dispose(); chipFont.dispose(); });

        Label title = new Label(area, SWT.NONE);
        title.setText("תרגיל חדש");
        title.setFont(headerFont);
        Label sub = new Label(area, SWT.NONE);
        sub.setText("בוחרים מבנה — ומקבלים תרגיל מוכן. נשאר רק לכתוב את הפעולה ולהריץ.");
        sub.setForeground(area.getDisplay().getSystemColor(SWT.COLOR_DARK_GRAY));

        // --- מבנה
        section(area, "איזה מבנה נתונים?");
        Composite grid = new Composite(area, SWT.NONE);
        grid.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
        GridLayout gl = new GridLayout(COLUMNS, true);
        gl.marginWidth = 0;
        gl.marginHeight = 0;
        gl.horizontalSpacing = 8;
        gl.verticalSpacing = 8;
        grid.setLayout(gl);
        String initial = presetStructure == null ? "stack" : presetStructure;
        for (Scaffold.Structure st : Scaffold.STRUCTURES.values()) {
            Button b = chip(grid, st.label, st.id, structureButtons, 52);
            b.setToolTipText(st.cls != null ? st.cls : st.dims == 2 ? "int[][]" : "int[]");
            b.setSelection(st.id.equals(initial));
        }

        // --- טיפוס
        section(area, "טיפוס הערכים");
        Composite types = new Composite(area, SWT.NONE);
        types.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
        GridLayout tl = new GridLayout(Scaffold.TYPES.size(), true);
        tl.marginWidth = 0;
        tl.marginHeight = 0;
        tl.horizontalSpacing = 8;
        types.setLayout(tl);
        for (String t : Scaffold.TYPES) {
            Button b = chip(types, t, t, typeButtons, 34);
            b.setSelection(t.equals("Integer"));
        }

        // --- מה הפעולה מחזירה
        section(area, "מה הפעולה מחזירה?");
        returnCombo = new Combo(area, SWT.READ_ONLY | SWT.LEFT_TO_RIGHT);
        returnCombo.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        // --- תוספות (שורה אחת)
        Composite extras = new Composite(area, SWT.NONE);
        extras.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
        GridLayout el = new GridLayout(1, false);
        el.marginWidth = 0;
        el.marginHeight = 0;
        el.verticalSpacing = 4;
        extras.setLayout(el);
        secondCheck = check(extras, "שני מבנים (מיזוג, השוואה)");
        extraCheck = check(extras, "פרמטר נוסף x (\"מחקו את כל המופעים של x\")");
        randomCheck = check(extras, "ערכים אקראיים (מספרים שלמים)");

        // --- ערכים
        valuesRow = new Composite(area, SWT.NONE);
        valuesRow.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
        GridLayout vl = new GridLayout(2, false);
        vl.marginWidth = 0;
        vl.marginHeight = 0;
        valuesRow.setLayout(vl);
        valuesLabel = new Label(valuesRow, SWT.NONE);
        valuesText = text(valuesRow);
        values2Label = new Label(valuesRow, SWT.NONE);
        values2Label.setText("ערכים למבנה השני:");
        values2Text = text(valuesRow);

        // --- שמות
        Composite names = new Composite(area, SWT.NONE);
        names.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
        GridLayout nl = new GridLayout(4, false);
        nl.marginWidth = 0;
        nl.marginHeight = 0;
        nl.horizontalSpacing = 8;
        names.setLayout(nl);
        label(names, "מחלקה:");
        classText = text(names);
        label(names, "פעולה:");
        functionText = text(names);
        functionText.setText("solve");

        // --- פרויקט (רק כשיש מה לבחור)
        Composite proj = new Composite(area, SWT.NONE);
        proj.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
        GridLayout pl = new GridLayout(2, false);
        pl.marginWidth = 0;
        pl.marginHeight = 0;
        proj.setLayout(pl);
        label(proj, "פרויקט:");
        projectCombo = new Combo(proj, SWT.READ_ONLY | SWT.LEFT_TO_RIGHT);
        projectCombo.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        int sel = -1;
        for (int i = 0; i < projects.size(); i++) {
            IProject p = projects.get(i).getProject();
            projectCombo.add(p.getName());
            if (p.equals(preferred)) sel = i;
        }
        projectCombo.add("פרויקט חדש: " + newProjectName);
        projectCombo.select(Math.max(sel, 0));

        errorLabel = new Label(area, SWT.WRAP);
        GridData eg = new GridData(SWT.FILL, SWT.TOP, true, false);
        eg.widthHint = 420;
        errorLabel.setLayoutData(eg);
        errorLabel.setForeground(area.getDisplay().getSystemColor(SWT.COLOR_RED));

        SelectionAdapter onChange = new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) { update(); }
        };
        projectCombo.addSelectionListener(onChange);
        returnCombo.addSelectionListener(onChange);
        for (Button b : new Button[] { secondCheck, extraCheck, randomCheck }) b.addSelectionListener(onChange);
        for (Text t : new Text[] { valuesText, values2Text, functionText }) t.addModifyListener(e -> update());
        classText.addModifyListener(e -> {
            if (classText.isFocusControl()) classEdited = true;
            update();
        });
        return area;
    }

    @Override
    protected void createButtonsForButtonBar(Composite parent) {
        createButton(parent, IDialogConstants.OK_ID, "יצירת התרגיל", true);
        createButton(parent, IDialogConstants.CANCEL_ID, "ביטול", false);
        update();
    }

    private void section(Composite c, String s) {
        Label l = new Label(c, SWT.NONE);
        l.setText(s);
        l.setFont(JFaceResources.getBannerFont());
        GridData gd = new GridData(SWT.FILL, SWT.TOP, true, false);
        gd.verticalIndent = 4;
        l.setLayoutData(gd);
    }

    /** כפתור-בחירה גדול (כמו "צ'יפ"): לוחצים עליו ונבחר, והאחרים בקבוצה מתבטלים */
    private Button chip(Composite parent, String text, final Object data, final List<Button> group, int height) {
        final Button b = new Button(parent, SWT.TOGGLE);
        b.setText(text);
        b.setData(data);
        b.setFont(chipFont);
        GridData gd = new GridData(SWT.FILL, SWT.FILL, true, false);
        gd.heightHint = height;
        b.setLayoutData(gd);
        group.add(b);
        b.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                for (Button o : group) o.setSelection(o == b);   // תמיד אחד נבחר
                update();
            }
        });
        return b;
    }

    private void label(Composite c, String s) {
        new Label(c, SWT.NONE).setText(s);
    }

    private Button check(Composite c, String s) {
        Button b = new Button(c, SWT.CHECK);
        b.setText(s);
        return b;
    }

    private Text text(Composite c) {
        Text t = new Text(c, SWT.BORDER | SWT.LEFT_TO_RIGHT);
        GridData gd = new GridData(SWT.FILL, SWT.CENTER, true, false);
        gd.widthHint = 200;
        t.setLayoutData(gd);
        return t;
    }

    private static String selected(List<Button> group, String fallback) {
        for (Button b : group) if (b.getSelection()) return (String) b.getData();
        return fallback;
    }

    private IJavaProject selectedProject() {
        int i = projectCombo.getSelectionIndex();
        return i >= 0 && i < projects.size() ? projects.get(i) : null;
    }

    /** מעדכן את השדות התלויים ובודק שגיאות (בלי להציג את הקוד — הוא נוצר רק בלחיצה על "יצירה") */
    private boolean updating;

    private void update() {
        if (updating || errorLabel == null || getButton(IDialogConstants.OK_ID) == null) return;
        updating = true;
        try {
            doUpdate();
        } finally {
            updating = false;
        }
    }

    private void doUpdate() {
        String st = selected(structureButtons, "stack"), type = selected(typeButtons, "Integer");
        Scaffold.Structure s = Scaffold.STRUCTURES.get(st);

        // רשימת "מה מחזירים" תלויה בטיפוס ובמבנה
        String prevRet = returnIds.isEmpty() ? "void" : returnIds.get(Math.max(0, returnCombo.getSelectionIndex()));
        returnIds.clear();
        returnCombo.removeAll();
        addReturn("void", "void — לא מחזירה ערך");
        addReturn("int", "int — מספר (סכום, כמות...)");
        addReturn("boolean", "boolean — אמת/שקר");
        if (!type.equals("Integer")) addReturn("element", Scaffold.primitive(type) + " — ערך מהמבנה");
        addReturn("structure", Scaffold.typeOf(s, type) + " — מבנה חדש");
        returnCombo.select(Math.max(0, returnIds.indexOf(prevRet)));

        boolean intType = type.equals("Integer");
        boolean canRandom = intType && !st.equals("tree") && !st.equals("bst");   // בעצים אין "ערכים אקראיים"
        randomCheck.setEnabled(canRandom);
        if (!canRandom) randomCheck.setSelection(false);
        boolean random = randomCheck.getSelection();
        valuesLabel.setText(type.equals("Student") ? "כמה תלמידים:" : st.equals("matrix") ? "ערכים (שורות ב-;):" : "ערכים:");
        if (!type.equals(lastType) || !st.equals(lastStructure)) {
            valuesText.setText(Scaffold.defaultValues(st, type, false));
            values2Text.setText(Scaffold.defaultValues(st, type, true));
            lastType = type;
            lastStructure = st;
        }
        valuesText.setEnabled(!random);
        boolean second = secondCheck.getSelection();
        values2Text.setEnabled(!random && second);
        showRow(values2Label, values2Text, second);

        IJavaProject jp = selectedProject();
        IContainer src = jp == null ? null : ProjectSetup.existingSource(jp);
        if (!classEdited) {
            String next = ProjectSetup.nextClassName(src);
            if (!next.equals(classText.getText())) classText.setText(next);
        }

        Scaffold.Options o = new Scaffold.Options();
        o.structure = st;
        o.type = type;
        o.returns = returnIds.get(returnCombo.getSelectionIndex());
        o.second = second;
        o.extra = extraCheck.getSelection();
        o.random = random;
        o.values = valuesText.getText();
        o.values2 = values2Text.getText();
        o.className = classText.getText().trim();
        o.functionName = functionText.getText().trim();

        String error = null;
        try {
            Scaffold.generate(o);   // רק בדיקה שהכול תקין
        } catch (IllegalArgumentException e) {
            error = e.getMessage();
        }
        if (error == null && src != null && src.getFile(new Path(o.className + ".java")).exists())
            error = "כבר קיים קובץ בשם " + o.className + ".java";
        errorLabel.setText(error == null ? "" : error);
        getButton(IDialogConstants.OK_ID).setEnabled(error == null);
        result = error == null ? o : null;
        resultProject = jp;
        valuesRow.layout();
        getShell().pack();
    }

    private static void showRow(Label label, Text text, boolean show) {
        for (Control c : new Control[] { label, text }) {
            c.setVisible(show);
            Object d = c.getLayoutData();
            if (d instanceof GridData) ((GridData) d).exclude = !show;
        }
        if (label.getLayoutData() == null) label.setLayoutData(new GridData());
        ((GridData) label.getLayoutData()).exclude = !show;
    }

    private void addReturn(String id, String text) {
        returnIds.add(id);
        returnCombo.add(text);
    }

    /** הבחירות (אחרי OK) */
    public Scaffold.Options options() {
        return result;
    }

    /** הפרויקט שנבחר, או null אם צריך ליצור פרויקט חדש */
    public IJavaProject project() {
        return resultProject;
    }

    public String newProjectName() {
        return newProjectName;
    }
}
