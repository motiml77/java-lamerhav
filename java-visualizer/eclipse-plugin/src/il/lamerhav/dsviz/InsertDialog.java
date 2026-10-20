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
    private final List<Button> structureButtons = new ArrayList<Button>();
    private final List<Button> typeButtons = new ArrayList<Button>();
    private Spinner size, rows, cols;
    private Label sizeLabel, rowsLabel, colsLabel, errorLabel;
    private Text name;
    private boolean nameEdited, updating;
    private String result;
    private Font headerFont, chipFont;

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
        title.setText("הוספה לקוד");
        title.setFont(headerFont);
        Label sub = new Label(area, SWT.NONE);
        sub.setText("המבנה נכנס לקוד במקום הסמן, מלא בנתוני דמה — אפשר לעבוד איתו מיד.");
        sub.setForeground(area.getDisplay().getSystemColor(SWT.COLOR_DARK_GRAY));

        section(area, "מה ליצור?");
        Composite grid = new Composite(area, SWT.NONE);
        grid.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
        GridLayout gl = new GridLayout(4, true);
        gl.marginWidth = 0;
        gl.marginHeight = 0;
        gl.horizontalSpacing = 8;
        gl.verticalSpacing = 8;
        grid.setLayout(gl);
        for (java.util.Map.Entry<String, String> e : Scaffold.SNIPPETS.entrySet()) {
            Button b = chip(grid, e.getValue(), e.getKey(), structureButtons, 48);
            b.setSelection(e.getKey().equals(preset));
        }

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

        Composite opts = new Composite(area, SWT.NONE);
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

        Composite nm = new Composite(area, SWT.NONE);
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
        Label l = new Label(c, SWT.NONE);
        l.setText(s);
        l.setFont(JFaceResources.getBannerFont());
        GridData gd = new GridData(SWT.FILL, SWT.TOP, true, false);
        gd.verticalIndent = 4;
        l.setLayoutData(gd);
    }

    private Label label(Composite c, String s) {
        Label l = new Label(c, SWT.NONE);
        l.setText(s);
        return l;
    }

    /** כפתור-בחירה גדול: לוחצים עליו ונבחר, והאחרים בקבוצה מתבטלים */
    private Button chip(Composite parent, String text, Object data, final List<Button> group, int height) {
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
                for (Button o : group) o.setSelection(o == b);
                update();
            }
        });
        return b;
    }

    private Spinner spinner(Composite c, int value, int min, int max) {
        Spinner s = new Spinner(c, SWT.BORDER);
        s.setValues(value, min, max, 0, 1, 5);
        return s;
    }

    @Override
    protected void createButtonsForButtonBar(Composite parent) {
        createButton(parent, IDialogConstants.OK_ID, "הוספה לקוד", true);
        createButton(parent, IDialogConstants.CANCEL_ID, "ביטול", false);
        update();
    }

    private static String selected(List<Button> group, String fallback) {
        for (Button b : group) if (b.getSelection()) return (String) b.getData();
        return fallback;
    }

    /** שם פנוי: q, q2, q3... (לפי מה שכבר כתוב בקובץ) */
    private String freeName(String base) {
        String n = base;
        for (int i = 2; java.util.regex.Pattern.compile("\\b" + n + "\\s*=").matcher(documentText).find(); i++) n = base + i;
        return n;
    }

    private void update() {
        if (updating || errorLabel == null || getButton(IDialogConstants.OK_ID) == null) return;
        updating = true;
        try {
            String st = selected(structureButtons, "queue");
            boolean isStudent = st.equals("student") || st.equals("car"), isMatrix = st.equals("matrix");
            for (Button b : typeButtons) b.setEnabled(!isStudent);
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
            try {
                result = Scaffold.snippet(o);
            } catch (IllegalArgumentException e) {
                error = e.getMessage();
                result = null;
            }
            errorLabel.setText(error == null ? "" : error);
            getButton(IDialogConstants.OK_ID).setEnabled(error == null);
            getShell().pack();
        } finally {
            updating = false;
        }
    }

    /** הקוד להכנסה (אחרי OK) */
    public String code() {
        return result;
    }
}
