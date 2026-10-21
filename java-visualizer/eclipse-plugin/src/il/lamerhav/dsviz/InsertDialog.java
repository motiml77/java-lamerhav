package il.lamerhav.dsviz;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

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
import org.eclipse.swt.widgets.Spinner;
import org.eclipse.swt.widgets.Text;

/** חלון "הוספה לקוד": מבנה עם נתוני דמה שנכנס לקוד במקום הסמן */
public class InsertDialog extends Dialog {
    private final String preset, documentText, indent;
    private final int seed = new Random().nextInt(Integer.MAX_VALUE);
    private final List<Look.Chip> structureButtons = new ArrayList<Look.Chip>();
    private final List<Look.Chip> typeButtons = new ArrayList<Look.Chip>();
    private final List<Look.Chip> insideButtons = new ArrayList<Look.Chip>();   // מה יש בכל תא: ערכים / מחסניות / תורים / רשימות
    private Label typeInfo;
    private Spinner size, rows, cols;
    private Label sizeLabel, rowsLabel, colsLabel, errorLabel;
    private Text name;
    private boolean nameEdited, updating;
    private String result, chosenClass;
    private Look.Round ok;

    public InsertDialog(Shell parent, String preset, String documentText, String indent) {
        super(parent);
        this.preset = preset == null ? "queue" : preset;
        this.documentText = documentText;
        this.indent = indent;
        setShellStyle(SWT.DIALOG_TRIM | SWT.RIGHT_TO_LEFT | SWT.APPLICATION_MODAL);
    }

    @Override
    protected void configureShell(Shell shell) {
        super.configureShell(shell);
        shell.setText("הוספה לקוד");
        shell.setBackground(Look.color(shell.getDisplay(), Look.BG));
        shell.setBackgroundMode(SWT.INHERIT_DEFAULT);
    }

    @Override
    protected Control createDialogArea(Composite parent) {
        Composite area = (Composite) super.createDialogArea(parent);
        GridLayout al = (GridLayout) area.getLayout();
        al.marginWidth = 22;
        al.marginHeight = 16;
        al.verticalSpacing = 10;

        area.setBackground(Look.color(area.getDisplay(), Look.BG));
        area.setBackgroundMode(SWT.INHERIT_DEFAULT);
        Look.header(area, "הוספה לקוד", "המבנה נכנס לקוד במקום הסמן, מלא בנתוני דמה — אפשר לעבוד איתו מיד.");
        Composite card = Look.card(area, new GridData(SWT.FILL, SWT.FILL, true, true));

        section(card, "מה ליצור?");
        Composite grid = new Composite(card, SWT.NONE);
        grid.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
        GridLayout gl = new GridLayout(4, true);
        gl.marginWidth = 0;
        gl.marginHeight = 0;
        gl.horizontalSpacing = 8;
        gl.verticalSpacing = 8;
        grid.setLayout(gl);
        for (java.util.Map.Entry<String, String> e : Scaffold.SNIPPETS.entrySet()) {
            Look.Chip b = chip(grid, e.getValue(), e.getKey(), structureButtons, 48);
            b.setSelection(e.getKey().equals(preset));
        }

        // מה יש בכל תא? ערכים רגילים, או מבנה שלם בכל תא (מחסנית של מחסניות, רשימה של תורים...)
        section(card, "מה יש בכל תא?");
        Composite inside = new Composite(card, SWT.NONE);
        inside.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
        GridLayout il = new GridLayout(Scaffold.INSIDE.length, true);
        il.marginWidth = 0;
        il.marginHeight = 0;
        il.horizontalSpacing = 8;
        inside.setLayout(il);
        for (String[] in : Scaffold.INSIDE) {
            Look.Chip b = chip(inside, in[1], in[0], insideButtons, 34);
            b.setToolTipText(in[2]);
            b.setSelection(in[0].isEmpty());
        }
        typeInfo = new Label(card, SWT.NONE);
        typeInfo.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
        typeInfo.setForeground(area.getDisplay().getSystemColor(SWT.COLOR_DARK_GRAY));

        section(card, "טיפוס הערכים");
        Composite types = new Composite(card, SWT.NONE);
        types.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
        GridLayout tl = new GridLayout(Scaffold.TYPES.size(), true);
        tl.marginWidth = 0;
        tl.marginHeight = 0;
        tl.horizontalSpacing = 8;
        types.setLayout(tl);
        for (String t : Scaffold.TYPES) {
            Look.Chip b = chip(types, t, t, typeButtons, 34);
            b.setSelection(t.equals("Integer"));
        }

        Composite opts = new Composite(card, SWT.NONE);
        opts.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
        GridLayout ol = new GridLayout(6, false);
        ol.marginWidth = 0;
        ol.marginHeight = 0;
        ol.horizontalSpacing = 8;
        opts.setLayout(ol);
        sizeLabel = label(opts, "כמה איברים:");
        size = spinner(opts, 5, 1, 30);
        rowsLabel = label(opts, "שורות:");
        rows = spinner(opts, 3, 1, 12);
        colsLabel = label(opts, "עמודות:");
        cols = spinner(opts, 4, 1, 12);

        Composite nm = new Composite(card, SWT.NONE);
        nm.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
        GridLayout nl = new GridLayout(2, false);
        nl.marginWidth = 0;
        nl.marginHeight = 0;
        nm.setLayout(nl);
        label(nm, "שם המשתנה:");
        name = new Text(nm, SWT.BORDER | SWT.LEFT_TO_RIGHT);
        GridData ng = new GridData(SWT.FILL, SWT.CENTER, true, false);
        ng.widthHint = 160;
        name.setLayoutData(ng);

        errorLabel = new Label(area, SWT.WRAP);
        GridData eg = new GridData(SWT.FILL, SWT.TOP, true, false);
        eg.widthHint = 380;
        errorLabel.setLayoutData(eg);
        errorLabel.setForeground(area.getDisplay().getSystemColor(SWT.COLOR_RED));

        for (Spinner sp : new Spinner[] { size, rows, cols }) sp.addModifyListener(e -> update());
        name.addModifyListener(e -> {
            if (name.isFocusControl()) nameEdited = true;
            update();
        });
        return area;
    }

    private void section(Composite c, String s) {
        Look.section(c, s, c.getChildren().length == 0 ? 0 : 14);
    }

    private Label label(Composite c, String s) {
        Label l = new Label(c, SWT.NONE);
        l.setText(s);
        return l;
    }

    /** כפתור-בחירה גדול: לוחצים עליו ונבחר, והאחרים בקבוצה מתבטלים */
    private Look.Chip chip(Composite parent, String text, Object data, final List<Look.Chip> group, int height) {
        return Look.chip(parent, text, data, group, height, 80, () -> update());
    }

    private Spinner spinner(Composite c, int value, int min, int max) {
        Spinner s = new Spinner(c, SWT.BORDER);
        s.setValues(value, min, max, 0, 1, 5);
        return s;
    }

    @Override
    protected Control createButtonBar(Composite parent) {
        Look.Round[] out = new Look.Round[1];
        Composite bar = Look.buttonBar(parent, "הוספה לקוד", () -> { if (ok.getEnabled()) okPressed(); }, () -> cancelPressed(), out);
        ok = out[0];
        name.addListener(SWT.DefaultSelection, e -> { if (ok.getEnabled()) okPressed(); });
        update();
        return bar;
    }

    private static String selected(List<Look.Chip> group, String fallback) {
        for (Look.Chip b : group) if (b.getSelection()) return (String) b.getData();
        return fallback;
    }

    /** שם פנוי: stk, stk2... או q1, q2... (לפי מה שכבר כתוב בקובץ) */
    private String freeName(String base) {
        return Scaffold.freeName(base, documentText);
    }

    private void update() {
        if (updating || errorLabel == null || ok == null) return;
        updating = true;
        try {
            String outer = selected(structureButtons, "queue");
            boolean isStudent = outer.equals("student") || outer.equals("car"), isMatrix = outer.equals("matrix");
            // מבנה בתוך מבנה: אפשרי במחסנית, תור, רשימה, עץ ומערך (לא במטריצה, בעץ חיפוש ובתלמיד / מכונית)
            boolean nestable = Scaffold.canNest(outer);
            String inside = nestable ? selected(insideButtons, "") : "";
            for (Look.Chip b : insideButtons) {
                b.setEnabled(nestable || ((String) b.getData()).isEmpty());
                b.setSelection(((String) b.getData()).equals(inside));
            }
            String st = Scaffold.comboId(outer, inside);
            boolean combo = !st.equals(outer);
            for (Look.Chip b : typeButtons) b.setEnabled(!isStudent && !(combo && (b.getData().equals("Student") || b.getData().equals("Car"))));
            if (combo && (selected(typeButtons, "Integer").equals("Student") || selected(typeButtons, "Integer").equals("Car")))
                for (Look.Chip b : typeButtons) b.setSelection(b.getData().equals("Integer"));
            typeInfo.setText(combo ? "הטיפוס: " + Scaffold.typeOf(Scaffold.STRUCTURES.get(st), selected(typeButtons, "Integer")) : "");
            sizeLabel.setText(combo ? "כמה מבנים פנימיים:" : "כמה איברים:");
            size.setMaximum(combo ? 6 : 30);
            if (combo && size.getSelection() > 6) size.setSelection(3);
            size.setEnabled(!isStudent && !isMatrix);
            sizeLabel.setEnabled(!isStudent && !isMatrix);
            rows.setEnabled(isMatrix);
            cols.setEnabled(isMatrix);
            rowsLabel.setEnabled(isMatrix);
            colsLabel.setEnabled(isMatrix);
            if (!nameEdited) name.setText(freeName(Scaffold.snippetName(st)));

            Scaffold.SnippetOptions o = new Scaffold.SnippetOptions();
            o.structure = st;
            o.type = selected(typeButtons, "Integer");
            o.name = name.getText().trim();
            o.size = size.getSelection();
            o.rows = rows.getSelection();
            o.cols = cols.getSelection();
            o.seed = seed;
            o.indent = indent;
            String error = null;
            chosenClass = st.equals("student") ? "Student" : st.equals("car") ? "Car"
                    : (o.type.equals("Student") || o.type.equals("Car")) && !st.equals("matrix") && !combo ? o.type : null;
            try {
                result = Scaffold.snippet(o);
            } catch (IllegalArgumentException e) {
                error = e.getMessage();
                result = null;
            }
            errorLabel.setText(error == null ? "" : error);
            ok.setEnabled(error == null);
            getShell().pack();
        } finally {
            updating = false;
        }
    }

    /** Student / Car אם נבחרה מחלקה מוכנה (להצגת חלון "מה יש במחלקה"), אחרת null */
    public String chosenClass() {
        return chosenClass;
    }

    /** הקוד להכנסה (אחרי OK) */
    public String code() {
        return result;
    }
}
