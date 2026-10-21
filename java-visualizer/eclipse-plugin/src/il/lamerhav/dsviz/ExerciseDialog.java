package il.lamerhav.dsviz;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.Path;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.FontData;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Spinner;
import org.eclipse.swt.widgets.Text;

/**
 * חלון "תרגיל חדש": דף קטן בשתי עמודות — מימין בוחרים מבנה וטיפוס, משמאל מה הפעולה מחזירה,
 * תוספות וערכים — ולוחצים "יצירת התרגיל". (הקוד נוצר רק בסוף, בלי תצוגה מקדימה.)
 */
public class ExerciseDialog extends Dialog {
    private final IProject preferred;
    private final String presetStructure;
    private final List<IJavaProject> projects;
    private final String newProjectName;

    private Combo projectCombo, returnCombo;
    private final List<Button> structureButtons = new ArrayList<Button>();
    private final List<Button> typeButtons = new ArrayList<Button>();
    private Button extraCheck, randomCheck, recursiveCheck;
    private Spinner structCount;
    private Spinner countSpinner;
    private Label countLabel;
    private Text valuesText, values2Text, values3Text, classText, functionText;
    private Label valuesLabel, values2Label, values3Label, errorLabel;
    private final List<String> returnIds = new ArrayList<String>();
    private boolean classEdited, lastRandom;
    private String lastType = "", lastStructure = "";
    private int lastCount = -1, seed = new Random().nextInt(Integer.MAX_VALUE);
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
        setShellStyle(SWT.DIALOG_TRIM | SWT.RESIZE | SWT.RIGHT_TO_LEFT | SWT.APPLICATION_MODAL);
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
        al.marginWidth = 18;
        al.marginHeight = 12;
        al.verticalSpacing = 6;

        FontData hd = JFaceResources.getDialogFont().getFontData()[0];
        headerFont = new Font(area.getDisplay(), hd.getName(), hd.getHeight() + 5, SWT.BOLD);
        chipFont = new Font(area.getDisplay(), hd.getName(), hd.getHeight() + 1, SWT.NORMAL);
        area.addDisposeListener(e -> { headerFont.dispose(); chipFont.dispose(); });

        Label title = new Label(area, SWT.NONE);
        title.setText("תרגיל חדש");
        title.setFont(headerFont);

        // שתי עמודות: מימין — מבנה וטיפוס; משמאל — כל השאר
        Composite cols = grid(area, 2, false, 24);
        cols.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        Composite right = grid(cols, 1, false, 0);
        right.setLayoutData(new GridData(SWT.FILL, SWT.TOP, false, false));
        Composite left = grid(cols, 1, false, 0);
        left.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));

        // --- מבנה
        section(right, "איזה מבנה נתונים?");
        Composite structs = grid(right, 2, true, 6);
        String initial = presetStructure == null ? "stack" : presetStructure;
        for (Scaffold.Structure st : Scaffold.STRUCTURES.values()) {
            Button b = chip(structs, st.label, st.id, structureButtons, 34, 130);
            b.setToolTipText(st.cls != null ? st.cls : st.dims == 2 ? "int[][]" : "int[]");
            b.setSelection(st.id.equals(initial));
        }

        // --- טיפוס
        section(right, "טיפוס הערכים");
        Composite types = grid(right, 3, true, 6);
        for (String t : Scaffold.TYPES) {
            Button b = chip(types, t, t, typeButtons, 28, 84);
            b.setSelection(t.equals("Integer"));
        }

        // --- מה הפעולה מחזירה
        section(left, "מה הפעולה מחזירה?");
        returnCombo = new Combo(left, SWT.READ_ONLY | SWT.LEFT_TO_RIGHT);
        returnCombo.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        // --- מה הפעולה מקבלת
        section(left, "מה הפעולה מקבלת?");
        Composite cnt = grid(left, 2, false, 6);
        label(cnt, "כמה מבנים (1–3)? למשל מיזוג, השוואה:");
        structCount = new Spinner(cnt, SWT.BORDER);
        structCount.setValues(1, 1, 3, 0, 1, 1);
        extraCheck = check(left, "גם ערך x (למשל: מחיקת כל ה-x)");
        recursiveCheck = check(left, "תרגיל ברקורסיה (בחלון — כרטיס לכל קריאה)");

        // --- ערכים
        section(left, "ערכים לבדיקה");
        Composite rnd = grid(left, 3, false, 6);
        randomCheck = check(rnd, "ערכים אקראיים");
        countLabel = new Label(rnd, SWT.NONE);
        countLabel.setText("כמה:");
        countSpinner = new Spinner(rnd, SWT.BORDER);
        countSpinner.setValues(6, 1, 20, 0, 1, 5);
        Composite vals = grid(left, 2, false, 6);
        valuesLabel = new Label(vals, SWT.NONE);
        valuesText = text(vals, 240);
        values2Label = new Label(vals, SWT.NONE);
        values2Label.setText("המבנה השני:");
        values2Label.setLayoutData(new GridData());
        values2Text = text(vals, 240);
        values3Label = new Label(vals, SWT.NONE);
        values3Label.setText("המבנה השלישי:");
        values3Label.setLayoutData(new GridData());
        values3Text = text(vals, 240);

        // --- שמות ופרויקט
        section(left, "שמות");
        Composite names = grid(left, 4, false, 6);
        label(names, "מחלקה:");
        classText = text(names, 100);
        label(names, "פעולה:");
        functionText = text(names, 80);
        functionText.setText("solve");
        Composite proj = grid(left, 2, false, 6);
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
        eg.widthHint = 500;
        errorLabel.setLayoutData(eg);
        errorLabel.setForeground(area.getDisplay().getSystemColor(SWT.COLOR_RED));

        SelectionAdapter onChange = new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) { update(); }
        };
        projectCombo.addSelectionListener(onChange);
        returnCombo.addSelectionListener(onChange);
        for (Button b : new Button[] { extraCheck, randomCheck, recursiveCheck }) b.addSelectionListener(onChange);
        structCount.addModifyListener(e -> update());
        countSpinner.addModifyListener(e -> update());
        for (Text t : new Text[] { valuesText, values2Text, values3Text, functionText }) t.addModifyListener(e -> update());
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

    /** החלון תמיד נכנס למסך (גם בהגדלת תצוגה של Windows) — כדי שהכפתורים למטה יהיו גלויים */
    @Override
    protected Point getInitialSize() {
        Point p = super.getInitialSize();
        Rectangle screen = getShell().getMonitor().getClientArea();
        return new Point(Math.min(p.x, screen.width - 40), Math.min(p.y, screen.height - 40));
    }

    private static Composite grid(Composite parent, int cols, boolean equal, int spacing) {
        Composite c = new Composite(parent, SWT.NONE);
        GridLayout l = new GridLayout(cols, equal);
        l.marginWidth = 0;
        l.marginHeight = 0;
        l.horizontalSpacing = spacing;
        l.verticalSpacing = spacing;
        c.setLayout(l);
        c.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
        return c;
    }

    private void section(Composite c, String s) {
        Label l = new Label(c, SWT.NONE);
        l.setText(s);
        l.setFont(JFaceResources.getBannerFont());
        GridData gd = new GridData(SWT.FILL, SWT.TOP, true, false);
        gd.verticalIndent = 8;
        l.setLayoutData(gd);
    }

    /** כפתור-בחירה (כמו "צ'יפ"): לוחצים עליו ונבחר, והאחרים בקבוצה מתבטלים */
    private Button chip(Composite parent, String text, Object data, final List<Button> group, int height, int width) {
        final Button b = new Button(parent, SWT.TOGGLE);
        b.setText(text);
        b.setData(data);
        b.setFont(chipFont);
        GridData gd = new GridData(SWT.FILL, SWT.FILL, true, false);
        gd.heightHint = height;
        gd.widthHint = width;
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

    private Text text(Composite c, int width) {
        Text t = new Text(c, SWT.BORDER | SWT.LEFT_TO_RIGHT);
        GridData gd = new GridData(SWT.FILL, SWT.CENTER, true, false);
        gd.widthHint = width;
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
        boolean objects = type.equals("Student") || type.equals("Car");

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

        // ערכים אקראיים: בוחרים כמה, והשדה מתמלא (אפשר לשנות). תלמידים / מכוניות — תמיד נתוני דמה
        randomCheck.setEnabled(!objects);
        if (objects) randomCheck.setSelection(false);
        boolean random = randomCheck.getSelection();
        countLabel.setEnabled(random);
        countSpinner.setEnabled(random);
        countLabel.setText(st.equals("matrix") ? "שורות:" : "כמה:");
        int count = countSpinner.getSelection();
        int structs = structCount.getSelection();
        boolean second = structs >= 2;
        if (random && (random != lastRandom || count != lastCount || !type.equals(lastType) || !st.equals(lastStructure))) {
            seed = new Random().nextInt(Integer.MAX_VALUE);
            valuesText.setText(Scaffold.randomValues(st, type, count, seed));
            values2Text.setText(Scaffold.randomValues(st, type, Math.max(1, count - 2), seed + 1));
            values3Text.setText(Scaffold.randomValues(st, type, Math.max(1, count - 2), seed + 2));
        } else if (!random && (lastRandom || !type.equals(lastType) || !st.equals(lastStructure))) {
            valuesText.setText(Scaffold.defaultValues(st, type, false));
            values2Text.setText(Scaffold.defaultValues(st, type, true));
            values3Text.setText(Scaffold.defaultValues(st, type, true));
        }
        lastRandom = random;
        lastCount = count;
        lastType = type;
        lastStructure = st;
        valuesLabel.setText(type.equals("Student") ? "כמה תלמידים:" : type.equals("Car") ? "כמה מכוניות:"
                : st.equals("matrix") ? "ערכים (שורות ב-;):" : "ערכים:");
        showRow(values2Label, values2Text, second);
        showRow(values3Label, values3Text, structs >= 3);

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
        o.count = structs;
        o.extra = extraCheck.getSelection();
        o.recursive = recursiveCheck.getSelection();
        o.random = false;   // הערכים האקראיים כבר בשדה — נכנסים לקוד כמו שהם
        o.values = valuesText.getText();
        o.values2 = values2Text.getText();
        o.values3 = values3Text.getText();
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
        valuesLabel.getParent().layout();
    }

    private static void showRow(Label label, Text text, boolean show) {
        for (Control c : new Control[] { label, text }) {
            c.setVisible(show);
            ((GridData) c.getLayoutData()).exclude = !show;
        }
        label.getParent().layout();
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
