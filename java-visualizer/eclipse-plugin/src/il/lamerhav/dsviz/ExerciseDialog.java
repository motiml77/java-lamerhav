package il.lamerhav.dsviz;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.Path;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.TitleAreaDialog;
import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
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

/** חלון "תרגיל חדש": בוחרים מבנה, טיפוס, מה הפעולה מחזירה וערכים — ורואים את הקוד שייווצר */
public class ExerciseDialog extends TitleAreaDialog {
    private final IProject preferred;
    private final String presetStructure;
    private final List<IJavaProject> projects;
    private final String newProjectName;

    private Combo projectCombo, typeCombo, returnCombo;
    private final List<Button> structureButtons = new ArrayList<Button>();
    private Button secondCheck, extraCheck, randomCheck;
    private Text valuesText, values2Text, classText, functionText;
    private Label valuesLabel;
    private StyledText preview;
    private final List<String> returnIds = new ArrayList<String>();
    private boolean classEdited;
    private String lastType = "", lastStructure = "";

    private Scaffold.Options result;
    private IJavaProject resultProject;

    public ExerciseDialog(Shell parent, List<IJavaProject> projects, String newProjectName, IProject preferred,
                          String presetStructure) {
        super(parent);
        this.projects = projects;
        this.newProjectName = newProjectName;
        this.preferred = preferred;
        this.presetStructure = presetStructure;
        setShellStyle(getShellStyle() | SWT.RESIZE | SWT.RIGHT_TO_LEFT);
    }

    @Override
    protected void configureShell(Shell shell) {
        super.configureShell(shell);
        shell.setText("תרגיל חדש במבני נתונים");
    }

    @Override
    protected Control createDialogArea(Composite parent) {
        setTitle("תרגיל חדש במבני נתונים");
        setMessage("בוחרים מבנה נתונים — ומקבלים תרגיל מוכן עם המבנה. נשאר רק לכתוב את הפעולה ולהריץ.");
        Composite area = (Composite) super.createDialogArea(parent);
        Composite main = new Composite(area, SWT.NONE);
        main.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        main.setLayout(new GridLayout(2, false));

        Composite form = new Composite(main, SWT.NONE);
        form.setLayoutData(new GridData(SWT.FILL, SWT.TOP, false, true));
        form.setLayout(new GridLayout(2, false));

        // --- פרויקט
        label(form, "פרויקט:");
        projectCombo = new Combo(form, SWT.READ_ONLY | SWT.LEFT_TO_RIGHT);
        projectCombo.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        int sel = -1;
        for (int i = 0; i < projects.size(); i++) {
            IProject p = projects.get(i).getProject();
            projectCombo.add(p.getName());
            if (p.equals(preferred)) sel = i;
        }
        projectCombo.add("פרויקט חדש: " + newProjectName);
        projectCombo.select(Math.max(sel, 0));

        // --- מבנה
        Group sg = new Group(form, SWT.NONE);
        sg.setText("מבנה הנתונים");
        GridData sgd = new GridData(SWT.FILL, SWT.TOP, true, false);
        sgd.horizontalSpan = 2;
        sg.setLayoutData(sgd);
        sg.setLayout(new GridLayout(1, false));
        for (Scaffold.Structure s : Scaffold.STRUCTURES.values()) {
            Button b = new Button(sg, SWT.RADIO);
            b.setText(s.label + "   (" + (s.cls != null ? s.cls : s.dims == 2 ? "int[][]" : "int[]") + ")");
            b.setData(s.id);
            b.setSelection(s.id.equals(presetStructure == null ? "stack" : presetStructure));
            structureButtons.add(b);
        }

        // --- טיפוס ומה מחזירים
        label(form, "טיפוס הערכים:");
        typeCombo = new Combo(form, SWT.READ_ONLY | SWT.LEFT_TO_RIGHT);
        typeCombo.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        for (String t : Scaffold.TYPES) typeCombo.add(t);
        typeCombo.select(0);

        label(form, "הפעולה מחזירה:");
        returnCombo = new Combo(form, SWT.READ_ONLY | SWT.LEFT_TO_RIGHT);
        returnCombo.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        // --- תוספות
        Group eg = new Group(form, SWT.NONE);
        eg.setText("תוספות");
        GridData egd = new GridData(SWT.FILL, SWT.TOP, true, false);
        egd.horizontalSpan = 2;
        eg.setLayoutData(egd);
        eg.setLayout(new GridLayout(1, false));
        secondCheck = check(eg, "שני מבנים (למשל מיזוג, השוואה)");
        extraCheck = check(eg, "פרמטר נוסף x (למשל \"מחקו את כל המופעים של x\")");
        randomCheck = check(eg, "ערכים אקראיים (מספרים שלמים בלבד)");

        // --- ערכים ושמות
        valuesLabel = new Label(form, SWT.NONE);
        valuesLabel.setText("ערכים:");
        valuesText = text(form);
        label(form, "ערכים למבנה השני:");
        values2Text = text(form);
        label(form, "שם המחלקה:");
        classText = text(form);
        label(form, "שם הפעולה:");
        functionText = text(form);
        functionText.setText("solve");

        // --- תצוגה מקדימה
        Group pg = new Group(main, SWT.NONE);
        pg.setText("הקוד שייווצר");
        pg.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        pg.setLayout(new GridLayout(1, false));
        preview = new StyledText(pg, SWT.MULTI | SWT.READ_ONLY | SWT.H_SCROLL | SWT.V_SCROLL | SWT.BORDER | SWT.LEFT_TO_RIGHT);
        preview.setFont(JFaceResources.getTextFont());
        GridData pgd = new GridData(SWT.FILL, SWT.FILL, true, true);
        pgd.widthHint = 560;
        pgd.heightHint = 460;
        preview.setLayoutData(pgd);

        SelectionAdapter onChange = new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) { update(); }
        };
        projectCombo.addSelectionListener(onChange);
        typeCombo.addSelectionListener(onChange);
        returnCombo.addSelectionListener(onChange);
        for (Button b : structureButtons) b.addSelectionListener(onChange);
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
        gd.widthHint = 260;
        t.setLayoutData(gd);
        return t;
    }

    private String structure() {
        for (Button b : structureButtons) if (b.getSelection()) return (String) b.getData();
        return "stack";
    }

    private IJavaProject selectedProject() {
        int i = projectCombo.getSelectionIndex();
        return i >= 0 && i < projects.size() ? projects.get(i) : null;
    }

    /** מעדכן את השדות התלויים ואת התצוגה המקדימה, ובודק שגיאות */
    private boolean updating;

    private void update() {
        if (updating || preview == null || getButton(IDialogConstants.OK_ID) == null) return;
        updating = true;
        try {
            doUpdate();
        } finally {
            updating = false;
        }
    }

    private void doUpdate() {
        String st = structure(), type = typeCombo.getText();
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
        randomCheck.setEnabled(intType);
        if (!intType) randomCheck.setSelection(false);
        boolean random = randomCheck.getSelection();
        valuesLabel.setText(type.equals("Student") ? "כמה תלמידים:" : st.equals("matrix") ? "ערכים (שורות ב-;):" : "ערכים:");
        valuesLabel.getParent().layout();
        if (!type.equals(lastType) || !st.equals(lastStructure)) {
            valuesText.setText(Scaffold.defaultValues(st, type, false));
            values2Text.setText(Scaffold.defaultValues(st, type, true));
            lastType = type;
            lastStructure = st;
        }
        valuesText.setEnabled(!random);
        values2Text.setEnabled(!random && secondCheck.getSelection());

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
        o.second = secondCheck.getSelection();
        o.extra = extraCheck.getSelection();
        o.random = random;
        o.values = valuesText.getText();
        o.values2 = values2Text.getText();
        o.className = classText.getText().trim();
        o.functionName = functionText.getText().trim();

        String error = null;
        try {
            preview.setText(Scaffold.generate(o).code);
        } catch (IllegalArgumentException e) {
            error = e.getMessage();
        }
        if (error == null && src != null && src.getFile(new Path(o.className + ".java")).exists())
            error = "כבר קיים קובץ בשם " + o.className + ".java";
        setErrorMessage(error);
        getButton(IDialogConstants.OK_ID).setEnabled(error == null);
        result = error == null ? o : null;
        resultProject = jp;
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
