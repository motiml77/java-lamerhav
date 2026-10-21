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
 * רקורסיה ומילוי המבנה — ולוחצים "יצירת התרגיל". (הקוד נוצר רק בסוף, בלי תצוגה מקדימה.)
 */
public class ExerciseDialog extends Dialog {
    private final IProject preferred;
    private final String presetStructure;
    private final List<IJavaProject> projects;
    private final String newProjectName;

    private Combo projectCombo, returnCombo;
    private final List<Look.Chip> structureButtons = new ArrayList<Look.Chip>();
    private final List<Look.Chip> typeButtons = new ArrayList<Look.Chip>();
    private final List<Look.Chip> insideButtons = new ArrayList<Look.Chip>();   // מה יש בכל תא: ערכים / מחסניות / תורים / רשימות
    private Composite previewBox;
    private Label previewText, typeSection;
    private NestedPreview previewCanvas;
    private Button randomCheck, recursiveCheck;
    private Spinner countSpinner;
    private Text valuesText, classText, functionText;
    private Label valuesLabel, errorLabel;
    private final List<String> returnIds = new ArrayList<String>();
    private boolean classEdited, lastRandom;
    private String lastType = "", lastStructure = "";
    private int lastCount = -1, seed = new Random().nextInt(Integer.MAX_VALUE);
    private Look.Round ok;

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
    protected Control createDialogArea(Composite parent) {
        Composite area = (Composite) super.createDialogArea(parent);
        GridLayout al = (GridLayout) area.getLayout();
        al.marginWidth = 18;
        al.marginHeight = 12;
        al.verticalSpacing = 6;

        area.setBackground(Look.color(area.getDisplay(), Look.BG));
        area.setBackgroundMode(SWT.INHERIT_DEFAULT);
        al.marginWidth = 20;
        al.marginHeight = 16;
        al.verticalSpacing = 8;
        Look.header(area, "תרגיל חדש", "בוחרים מבנה נתונים ואיך למלא אותו — והתרגיל נוצר, מוכן לכתיבה.");

        // שתי עמודות (שני כרטיסים): מימין — מבנה וטיפוס; משמאל — כל השאר
        Composite cols = grid(area, 2, false, 14);
        cols.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        Composite right = Look.card(cols, new GridData(SWT.FILL, SWT.FILL, false, true));
        Composite left = Look.card(cols, new GridData(SWT.FILL, SWT.FILL, true, true));

        // --- מבנה
        section(right, "איזה מבנה נתונים?");
        Composite structs = grid(right, 2, true, 6);
        String[] initialParts = (presetStructure == null ? "stack" : presetStructure).split("_");   // stack או stack_queue
        String initial = initialParts[0], initialInside = initialParts.length > 1 ? initialParts[1] : "";
        for (Scaffold.Structure st : Scaffold.STRUCTURES.values()) {
            if (st.outer != null) continue;   // קומבינציות נבחרות בשתי בחירות: מבנה + "מה יש בכל תא?"
            Look.Chip b = chip(structs, st.label, st.id, structureButtons, 34, 130);
            b.setToolTipText(st.cls != null ? st.cls : st.dims == 2 ? "int[][]" : "int[]");
            b.setSelection(st.id.equals(initial));
        }

        // --- מה יש בכל תא? (ערכים רגילים, או מבנה שלם בכל תא — מחסנית של מחסניות, רשימה של תורים...)
        section(right, "מה יש בכל תא?");
        Composite inside = grid(right, 2, true, 6);
        for (String[] in : Scaffold.INSIDE) {
            Look.Chip b = chip(inside, in[1], in[0], insideButtons, 28, 130);
            b.setToolTipText(in[2]);
            b.setSelection(in[0].equals(initialInside));
        }

        // --- טיפוס
        typeSection = section(right, "טיפוס הערכים");
        Composite types = grid(right, 3, true, 6);
        for (String t : Scaffold.TYPES) {
            Look.Chip b = chip(types, t, t, typeButtons, 28, 84);
            b.setSelection(t.equals("Integer"));
        }

        // --- מה הפעולה מחזירה
        section(left, "מה הפעולה מחזירה?");
        returnCombo = new Combo(left, SWT.READ_ONLY | SWT.LEFT_TO_RIGHT);
        returnCombo.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        // --- תרגיל ברקורסיה: בחירה נפרדת — מפעילה בחלון ההרצה את לשונית הכרטיסים
        section(left, "רקורסיה");
        recursiveCheck = check(left, "תרגיל ברקורסיה (כרטיס לכל קריאה)");
        recursiveCheck.setToolTipText("בחלון ההרצה תופיע לשונית \"רקורסיה\": כרטיס לכל קריאה");

        // --- מילוי המבנה: אקראי (כמה ערכים) או ידני בשדה
        section(left, "מילוי מבנה-נתונים");
        Composite rnd = grid(left, 2, false, 6);
        randomCheck = check(rnd, "כמה ערכים אקראיים:");
        countSpinner = new Spinner(rnd, SWT.BORDER);
        countSpinner.setValues(6, 1, 20, 0, 1, 5);
        Composite vals = grid(left, 2, false, 6);
        valuesLabel = new Label(vals, SWT.NONE);
        valuesText = text(vals, 240);

        // תצוגה מקדימה: רק בקומבינציה — מה ייבנה ובאיזה סדר המבנים הפנימיים נמצאים במבנה החיצוני
        previewBox = grid(left, 1, false, 2);
        Label pt = new Label(previewBox, SWT.NONE);
        pt.setText("כך ייבנה (כל קבוצה, עד ;, היא מבנה פנימי אחד):");
        pt.setForeground(area.getDisplay().getSystemColor(SWT.COLOR_DARK_GRAY));
        previewText = new Label(previewBox, SWT.NONE | SWT.LEFT_TO_RIGHT);   // הטיפוס, למשל Stack<Queue<Integer>>
        previewText.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
        previewText.setFont(Look.font(area.getDisplay(), 1, SWT.BOLD));
        previewCanvas = new NestedPreview(previewBox);
        GridData pg = new GridData(SWT.FILL, SWT.TOP, true, false);
        pg.widthHint = 320;
        previewCanvas.setLayoutData(pg);
        showBox(previewBox, false);

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
        for (Button b : new Button[] { randomCheck, recursiveCheck }) b.addSelectionListener(onChange);
        countSpinner.addModifyListener(e -> update());
        for (Text t : new Text[] { valuesText, functionText }) t.addModifyListener(e -> update());
        classText.addModifyListener(e -> {
            if (classText.isFocusControl()) classEdited = true;
            update();
        });
        return area;
    }

    @Override
    protected void configureShell(Shell shell) {
        super.configureShell(shell);
        shell.setText("תרגיל חדש");
        shell.setBackground(Look.color(shell.getDisplay(), Look.BG));
        shell.setBackgroundMode(SWT.INHERIT_DEFAULT);
    }

    @Override
    protected Control createButtonBar(Composite parent) {
        Look.Round[] out = new Look.Round[1];
        Composite bar = Look.buttonBar(parent, "יצירת התרגיל", () -> { if (ok.getEnabled()) okPressed(); }, () -> cancelPressed(), out);
        ok = out[0];
        for (Text t : new Text[] { valuesText, classText, functionText })
            t.addListener(SWT.DefaultSelection, e -> { if (ok.getEnabled()) okPressed(); });
        update();
        return bar;
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

    private Label section(Composite c, String s) {
        return Look.section(c, s, c.getChildren().length == 0 ? 0 : 16);
    }

    private static void showBox(Composite box, boolean show) {
        box.setVisible(show);
        ((GridData) box.getLayoutData()).exclude = !show;
    }

    /** כפתור-בחירה (כמו "צ'יפ"): לוחצים עליו ונבחר, והאחרים בקבוצה מתבטלים */
    private Look.Chip chip(Composite parent, String text, Object data, final List<Look.Chip> group, int height, int width) {
        return Look.chip(parent, text, data, group, height, width, () -> update());
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

    private static String selected(List<Look.Chip> group, String fallback) {
        for (Look.Chip b : group) if (b.getSelection()) return (String) b.getData();
        return fallback;
    }

    private IJavaProject selectedProject() {
        int i = projectCombo.getSelectionIndex();
        return i >= 0 && i < projects.size() ? projects.get(i) : null;
    }

    /** מעדכן את השדות התלויים ובודק שגיאות (בלי להציג את הקוד — הוא נוצר רק בלחיצה על "יצירה") */
    private boolean updating;

    private void update() {
        if (updating || errorLabel == null || ok == null) return;
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
        for (Look.Chip b : insideButtons) {
            b.setEnabled(nestable || ((String) b.getData()).isEmpty());
            b.setSelection(((String) b.getData()).equals(inside));
        }
        String st = Scaffold.comboId(outer, inside);
        Scaffold.Structure s = Scaffold.STRUCTURES.get(st);
        boolean combo = s.outer != null;
        // מבנה בתוך מבנה: רק מספרים / מילים / תווים (לא תלמידים ומכוניות), מבנה אחד, בלי ערכים אקראיים
        for (Look.Chip b : typeButtons) b.setEnabled(!(combo && (b.getData().equals("Student") || b.getData().equals("Car"))));
        if (combo && (type.equals("Student") || type.equals("Car"))) {
            type = "Integer";
            for (Look.Chip b : typeButtons) b.setSelection(b.getData().equals("Integer"));
        }
        typeSection.setText(combo ? "טיפוס הערכים (בתוך המבנים הפנימיים)" : "טיפוס הערכים");
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
        randomCheck.setEnabled(!objects && !combo);
        if (objects || combo) randomCheck.setSelection(false);
        boolean random = randomCheck.getSelection();
        countSpinner.setEnabled(random);
        randomCheck.setText(st.equals("matrix") ? "כמה שורות אקראיות:" : "כמה ערכים אקראיים:");
        int count = countSpinner.getSelection();
        if (random && (random != lastRandom || count != lastCount || !type.equals(lastType) || !st.equals(lastStructure))) {
            seed = new Random().nextInt(Integer.MAX_VALUE);
            valuesText.setText(Scaffold.randomValues(st, type, count, seed));
        } else if (!random && (lastRandom || !type.equals(lastType) || !st.equals(lastStructure))) {
            valuesText.setText(Scaffold.defaultValues(st, type, false));
        }
        lastRandom = random;
        lastCount = count;
        lastType = type;
        lastStructure = st;
        valuesLabel.setText(type.equals("Student") ? "כמה תלמידים:" : type.equals("Car") ? "כמה מכוניות:"
                : combo ? (outer.equals("tree") ? "מבנים פנימיים (לפי רמות, ;):" : "מבנים פנימיים (ב-;):")
                : st.equals("matrix") ? "ערכים (שורות ב-;):" : "ערכים:");
        java.util.List<java.util.List<String>> groups = null;
        if (combo) {
            try { groups = Scaffold.groupLiterals(valuesText.getText(), type, outer.equals("tree")); }
            catch (IllegalArgumentException e) { groups = null; }   // הודעת השגיאה תוצג למטה
        }
        if (groups != null) {
            previewText.setText(Scaffold.typeOf(s, type));
            previewCanvas.set(outer, inside, groups);
        }
        showBox(previewBox, groups != null);
        previewBox.layout(true, true);
        previewBox.getParent().layout();

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
        o.second = false;
        o.count = 1;
        o.extra = false;
        o.recursive = recursiveCheck.getSelection();
        o.random = false;   // הערכים האקראיים כבר בשדה — נכנסים לקוד כמו שהם
        o.values = valuesText.getText();
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
        ok.setEnabled(error == null);
        result = error == null ? o : null;
        resultProject = jp;
        valuesLabel.getParent().layout();
        growToFit();
    }

    /** כשהתצוגה המקדימה מופיעה החלון גדל כדי שהכול ייראה (אבל לא יותר מהמסך) */
    private void growToFit() {
        Shell sh = getShell();
        if (sh == null || !sh.isVisible()) return;
        Point need = sh.computeSize(SWT.DEFAULT, SWT.DEFAULT), cur = sh.getSize();
        int max = sh.getMonitor().getClientArea().height - 40;
        if (need.y > cur.y) sh.setSize(cur.x, Math.min(need.y, max));
        sh.layout(true, true);
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
