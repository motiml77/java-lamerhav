package il.lamerhav.dsviz;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.ScrolledComposite;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;

/**
 * "צור מחלקה" (מהחלון "הוסף מבנה נתונים" / מהתפריט): שם למחלקה, השדות שלה (טיפוס מרשימה בקבוצות או כתיבה חופשית + שם),
 * בנאי ו-toString — ותצוגה חיה של הקוד. המחולל: ClassGen (אותו קוד כמו ב-VS Code, ב-IntelliJ וב-Visual Studio).
 */
public class ClassDialog extends Dialog {
    private final String lang, pkg;
    private final Map<String, String> taken;
    private final List<ClassGen.Known> known;
    private Text className, preview;
    private Composite grid;
    private Label elemHead, fileLabel, errorLabel, warnLabel;
    private Button ctor, toStr;
    private ScrolledComposite scroll;
    private Look.Round ok;
    private final List<Row> rows = new ArrayList<Row>();
    private String code, file, name;

    /** שורה בטבלת השדות: טיפוס, מה יש בפנים (ליד Queue / Stack / Node / BinNode), שם, הסרה */
    private final class Row {
        TypeField base, elem;
        Composite elemBox;
        Text name;
        Button remove;
        final Map<String, String> refs = new LinkedHashMap<String, String>();

        boolean course() {
            return ClassGen.isCourse(base.value());
        }

        void dispose() {
            base.box.dispose();
            elemBox.dispose();
            name.dispose();
            remove.dispose();
        }
    }

    public ClassDialog(Shell parent, String lang, String pkg, Map<String, String> taken, List<ClassGen.Known> known) {
        super(parent);
        this.lang = lang == null ? ClassGen.JAVA : lang;
        this.pkg = pkg == null ? "" : pkg;
        this.taken = taken == null ? new LinkedHashMap<String, String>() : taken;
        this.known = known == null ? new ArrayList<ClassGen.Known>() : known;
        setShellStyle(SWT.DIALOG_TRIM | SWT.RESIZE | SWT.RIGHT_TO_LEFT | SWT.APPLICATION_MODAL);
    }

    @Override
    protected void configureShell(Shell shell) {
        super.configureShell(shell);
        shell.setText(ClassGen.TITLE);
        shell.setBackground(Look.color(shell.getDisplay(), Look.BG));
        shell.setBackgroundMode(SWT.INHERIT_DEFAULT);
    }

    @Override
    protected Control createDialogArea(Composite parent) {
        Composite area = (Composite) super.createDialogArea(parent);
        GridLayout al = (GridLayout) area.getLayout();
        al.marginWidth = 16;
        al.marginHeight = 8;
        al.verticalSpacing = 4;
        area.setBackground(Look.color(area.getDisplay(), Look.BG));
        area.setBackgroundMode(SWT.INHERIT_DEFAULT);
        Look.header(area, ClassGen.TITLE, ClassGen.subtitle(lang));

        GridLayout cl = new GridLayout(2, false);
        cl.marginWidth = 0;
        cl.marginHeight = 0;
        cl.horizontalSpacing = 14;
        Composite[] holder = new Composite[1];
        scroll = Look.scroll(area, cl, holder);
        Composite cols = holder[0];
        Composite right = Look.card(cols, new GridData(SWT.FILL, SWT.FILL, false, true));
        Composite left = Look.card(cols, new GridData(SWT.FILL, SWT.FILL, true, true));

        // --- שם המחלקה
        Look.section(right, "שם המחלקה", 0);
        className = new Text(right, SWT.BORDER | SWT.LEFT_TO_RIGHT);
        className.setMessage("Student");
        GridData ng = new GridData(SWT.FILL, SWT.CENTER, true, false);
        ng.widthHint = 220;
        className.setLayoutData(ng);
        className.setFont(Look.mono(area.getDisplay(), 0));
        className.setData("dsviz.keepFont", Boolean.TRUE);

        // --- השדות
        Look.section(right, "השדות (private)", 10);
        grid = new Composite(right, SWT.LEFT_TO_RIGHT);
        GridLayout gl = new GridLayout(4, false);
        gl.marginWidth = 0;
        gl.marginHeight = 0;
        gl.horizontalSpacing = 6;
        gl.verticalSpacing = 5;
        grid.setLayout(gl);
        grid.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
        head(grid, "טיפוס");
        elemHead = head(grid, "בפנים");
        head(grid, "שם");
        head(grid, "");
        addRow(ClassGen.defaultType(lang), "", "", false);
        Composite addBar = new Composite(right, SWT.NONE);
        GridLayout bl = new GridLayout(1, false);
        bl.marginWidth = 0;
        bl.marginHeight = 2;
        addBar.setLayout(bl);
        new Look.Round(addBar, "+ שדה", false, () -> addRow(ClassGen.defaultType(lang), "", "", true));

        ctor = new Button(right, SWT.CHECK);
        ctor.setText("בנאי שמקבל את כל השדות");
        ctor.setSelection(true);
        GridData cg = new GridData(SWT.BEGINNING, SWT.CENTER, false, false);   // בתחילת השורה (מימין) — כמו ToString שמתחתיו
        cg.verticalIndent = 8;
        ctor.setLayoutData(cg);
        toStr = new Button(right, SWT.CHECK);
        toStr.setText(ClassGen.toStringLabel(lang));
        Label keys = new Label(right, SWT.WRAP);
        keys.setText(ClassGen.KEYS);
        keys.setForeground(Look.color(area.getDisplay(), Look.MUTED));
        GridData kg = new GridData(SWT.FILL, SWT.CENTER, true, false);
        kg.verticalIndent = 6;
        keys.setLayoutData(kg);

        // --- הקוד
        Composite ph = new Composite(left, SWT.NONE);
        GridLayout phl = new GridLayout(2, false);
        phl.marginWidth = 0;
        phl.marginHeight = 0;
        ph.setLayout(phl);
        ph.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
        Look.section(ph, "כך תיראה המחלקה", 0).setLayoutData(new GridData(SWT.BEGINNING, SWT.CENTER, false, false));
        fileLabel = new Label(ph, SWT.LEFT_TO_RIGHT);
        fileLabel.setForeground(Look.color(area.getDisplay(), Look.MUTED));
        fileLabel.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        preview = new Text(left, SWT.MULTI | SWT.READ_ONLY | SWT.BORDER | SWT.V_SCROLL | SWT.H_SCROLL | SWT.LEFT_TO_RIGHT);
        preview.setFont(Look.mono(area.getDisplay(), -1));
        preview.setData("dsviz.keepFont", Boolean.TRUE);
        preview.setBackground(Look.color(area.getDisplay(), Look.SURFACE));
        GridData pg = new GridData(SWT.FILL, SWT.FILL, true, true);
        pg.widthHint = 420;
        pg.heightHint = 360;
        preview.setLayoutData(pg);

        warnLabel = new Label(area, SWT.WRAP);
        GridData wg = new GridData(SWT.FILL, SWT.TOP, true, false);
        wg.widthHint = 500;
        warnLabel.setLayoutData(wg);
        warnLabel.setForeground(Look.color(area.getDisplay(), 0x9A6700));
        errorLabel = new Label(area, SWT.WRAP);
        GridData eg = new GridData(SWT.FILL, SWT.TOP, true, false);
        eg.widthHint = 500;
        errorLabel.setLayoutData(eg);
        errorLabel.setForeground(area.getDisplay().getSystemColor(SWT.COLOR_RED));

        className.addListener(SWT.Modify, e -> update());
        className.addListener(SWT.Traverse, e -> { if (e.detail == SWT.TRAVERSE_RETURN) e.doit = false; });
        className.addListener(SWT.KeyDown, e -> {
            if (e.character == SWT.CR && (e.stateMask & SWT.MOD1) == 0) rows.get(0).name.setFocus();
        });
        ctor.addListener(SWT.Selection, e -> update());
        toStr.addListener(SWT.Selection, e -> update());
        Look.applyFont(area, -1);
        Look.wheel(scroll);
        // Ctrl+Enter מכל מקום בחלון — יצירה
        area.getDisplay().addFilter(SWT.KeyDown, ctrlEnter);
        area.addListener(SWT.Dispose, e -> area.getDisplay().removeFilter(SWT.KeyDown, ctrlEnter));
        return area;
    }

    private final org.eclipse.swt.widgets.Listener ctrlEnter = e -> {
        if ((e.character == SWT.CR || e.keyCode == SWT.KEYPAD_CR) && (e.stateMask & SWT.MOD1) != 0 && getShell() != null && !getShell().isDisposed()
                && e.widget instanceof Control && ((Control) e.widget).getShell() == getShell() && ok != null && ok.getEnabled()) {
            e.doit = false;
            okPressed();
        }
    };

    private static Label head(Composite grid, String text) {
        Label l = new Label(grid, SWT.RIGHT_TO_LEFT);
        l.setText(text);
        l.setForeground(Look.color(grid.getDisplay(), Look.MUTED));
        return l;
    }

    private void addRow(String type, String inner, String fieldName, boolean focus) {
        final Row r = new Row();
        r.base = new TypeField(grid, 120, () -> ClassGen.typeGroups(lang, known), it -> {
            if (it.pkg != null) r.refs.put(it.type.replaceAll("\\[\\]$", ""), it.pkg);
            update();
            if (r.course()) r.elem.text.setFocus();
            else r.name.setFocus();
        }, this::update, () -> (r.course() ? r.elem.text : r.name).setFocus());
        r.base.box.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, false, false));
        r.elemBox = new Composite(grid, SWT.LEFT_TO_RIGHT);
        GridLayout el = new GridLayout(3, false);
        el.marginWidth = 0;
        el.marginHeight = 0;
        el.horizontalSpacing = 2;
        r.elemBox.setLayout(el);
        new Label(r.elemBox, SWT.NONE).setText("<");
        r.elem = new TypeField(r.elemBox, 92, () -> ClassGen.elementGroups(lang, known), it -> {
            if (it.pkg != null) r.refs.put(it.type, it.pkg);
            update();
            r.name.setFocus();
        }, this::update, () -> r.name.setFocus());
        r.elem.box.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        new Label(r.elemBox, SWT.NONE).setText(">");
        r.elemBox.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, false, false));
        r.name = new Text(grid, SWT.BORDER | SWT.LEFT_TO_RIGHT);
        r.name.setMessage("name");
        r.name.setFont(Look.mono(grid.getDisplay(), 0));
        r.name.setData("dsviz.keepFont", Boolean.TRUE);
        GridData ng = new GridData(SWT.FILL, SWT.CENTER, true, false);
        ng.widthHint = 110;
        r.name.setLayoutData(ng);
        r.remove = new Button(grid, SWT.PUSH | SWT.FLAT);
        r.remove.setText("✕");
        r.remove.setToolTipText("הסרת השדה");
        r.base.set(type);
        r.elem.set(inner);
        r.name.setText(fieldName);
        r.name.addListener(SWT.Modify, e -> update());
        r.name.addListener(SWT.Traverse, e -> { if (e.detail == SWT.TRAVERSE_RETURN) e.doit = false; });
        r.name.addListener(SWT.KeyDown, e -> {
            if (e.character != SWT.CR || (e.stateMask & SWT.MOD1) != 0) return;
            int i = rows.indexOf(r);
            if (i == rows.size() - 1) addRow(ClassGen.defaultType(lang), "", "", true);
            else rows.get(i + 1).base.text.setFocus();
        });
        r.remove.addListener(SWT.Selection, e -> {
            rows.remove(r);
            r.dispose();
            if (rows.isEmpty()) addRow(ClassGen.defaultType(lang), "", "", true);
            relayout();
            update();
        });
        rows.add(r);
        Look.applyFont(r.name.getParent(), -1);
        relayout();
        update();
        if (focus) {
            r.base.text.setFocus();
            r.base.text.selectAll();
        }
    }

    /** העמודה "בפנים" מופיעה רק כשיש שורה עם מבנה של הקורס, ובכל שורה — רק אם היא כזאת */
    private void relayout() {
        boolean any = false;
        for (Row r : rows) any |= r.course();
        elemHead.setVisible(any);
        for (Row r : rows) {
            boolean on = r.course();
            if (on && r.elem.value().trim().isEmpty()) r.elem.set(ClassGen.defaultElement(lang));
            r.elemBox.setVisible(on);
            GridData d = (GridData) r.elemBox.getLayoutData();
            d.widthHint = any ? SWT.DEFAULT : 0;
        }
        grid.layout(true, true);
        if (scroll != null) {
            ((Composite) scroll.getContent()).layout(true, true);
            Look.updateScroll(scroll);
        }
    }

    @Override
    protected Control createButtonBar(Composite parent) {
        Look.Round[] out = new Look.Round[1];
        Composite bar = Look.buttonBar(parent, "יצירת המחלקה", () -> { if (ok.getEnabled()) okPressed(); }, () -> cancelPressed(), out);
        ok = out[0];
        update();
        return bar;
    }

    @Override
    protected Point getInitialSize() {
        Point p = super.getInitialSize();
        Rectangle screen = getShell().getMonitor().getClientArea();
        return new Point(Math.min(p.x, screen.width - 40), Math.min(p.y, screen.height - 60));
    }

    private boolean updating;

    private void update() {
        if (updating || errorLabel == null || ok == null) return;
        updating = true;
        try {
            boolean course = false;
            for (Row r : rows) course |= r.course() != r.elemBox.getVisible();
            if (course) relayout();
            ClassGen.Options o = options();
            ClassGen.Check c = ClassGen.check(o);
            String text = c.ok() ? ClassGen.generate(o) : ClassGen.preview(o);
            if (!text.equals(preview.getText())) preview.setText(text);
            fileLabel.setText(c.className == null ? "" : ClassGen.fileName(c.className, lang));
            errorLabel.setText(c.errors.isEmpty() ? "" : c.errors.get(0));
            warnLabel.setText(String.join("\n", c.warnings));
            ok.setEnabled(c.ok());
            code = c.ok() ? text : null;
            name = c.className;
            file = c.className == null ? null : ClassGen.fileName(c.className, lang);
            fileLabel.getParent().layout();
            errorLabel.getParent().layout();
        } finally {
            updating = false;
        }
    }

    ClassGen.Options options() {
        ClassGen.Options o = new ClassGen.Options();
        o.lang = lang;
        o.className = className.getText();
        o.pkg = ClassGen.CS.equals(lang) ? "" : pkg;
        o.namespace = ClassGen.CS.equals(lang) ? pkg : "";
        o.constructor = ctor.getSelection();
        o.toString = toStr.getSelection();
        o.taken = taken;
        o.known = known;
        for (Row r : rows) {
            ClassGen.Field f = new ClassGen.Field(ClassGen.fullType(r.base.value(), r.elem.value(), lang), r.name.getText());
            f.refs.putAll(r.refs);
            o.fields.add(f);
        }
        return o;
    }

    /** הקוד ליצירה (אחרי "יצירת המחלקה") */
    public String code() {
        return code;
    }

    /** Student.java */
    public String fileName() {
        return file;
    }

    public String className() {
        return name;
    }

    // ------------------------------------------------------------------ לצילומי מסך ולבדיקות (DlgScript: call ...)

    /** ממלא את החלון: שם ושדות "String name;int grade;Queue<Student> line" */
    void demo(String cls, String fields) {
        for (Row r : rows) {
            r.base.close();
            r.elem.close();
        }
        className.setText(cls);
        while (rows.size() > 1) {
            Row r = rows.remove(rows.size() - 1);
            r.dispose();
        }
        String[] fs = fields.split(";");
        for (int i = 0; i < fs.length; i++) {
            String f = fs[i].trim();
            Map<String, String> refs = new LinkedHashMap<String, String>();   // "Student best{Student=school.model}" — כאילו נבחר מהרשימה
            int br = f.indexOf('{');
            if (br > 0 && f.endsWith("}")) {
                for (String x : f.substring(br + 1, f.length() - 1).split(",")) if (x.indexOf('=') > 0) refs.put(x.substring(0, x.indexOf('=')), x.substring(x.indexOf('=') + 1));
                f = f.substring(0, br);
            }
            int sp = f.lastIndexOf(' ');
            String type = f.substring(0, sp).trim(), n = f.substring(sp + 1);
            String base = type, inner = "";
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\w+)<(.+)>").matcher(type);
            if (m.matches() && ClassGen.isCourse(m.group(1))) {
                base = m.group(1);
                inner = m.group(2);
            }
            if (i == 0) {
                rows.get(0).base.set(base);
                rows.get(0).elem.set(inner);
                rows.get(0).refs.clear();
                rows.get(0).name.setText(n);
            } else addRow(base, inner, n, false);
            rows.get(rows.size() - 1).refs.putAll(refs);
        }
        relayout();
        update();
    }

    /** פותח את הרשימה של שורה (elem — הרשימה הקטנה של מה שבפנים) */
    void openList(String row, String elem) {
        Row r = rows.get(Integer.parseInt(row.trim()));
        TypeField t = "1".equals(elem) || "elem".equals(elem) ? r.elem : r.base;
        t.text.setFocus();
        t.open(true);
    }

    void withToString(String on) {
        toStr.setSelection("1".equals(on) || "true".equals(on));
        update();
    }
}
