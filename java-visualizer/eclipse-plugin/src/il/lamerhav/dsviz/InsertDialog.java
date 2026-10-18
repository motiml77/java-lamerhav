package il.lamerhav.dsviz;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

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
import org.eclipse.swt.widgets.Spinner;
import org.eclipse.swt.widgets.Text;

/** חלון "הוספה לקוד": מבנה עם נתוני דמה שנכנס לקוד במקום הסמן */
public class InsertDialog extends TitleAreaDialog {
    private final String preset, documentText, indent;
    private final int seed = new Random().nextInt(Integer.MAX_VALUE);
    private final List<Button> structureButtons = new ArrayList<Button>();
    private Combo typeCombo;
    private Spinner size, rows, cols;
    private Text name;
    private StyledText preview;
    private boolean nameEdited, updating;
    private String result;

    public InsertDialog(Shell parent, String preset, String documentText, String indent) {
        super(parent);
        this.preset = preset == null ? "queue" : preset;
        this.documentText = documentText;
        this.indent = indent;
        setShellStyle(getShellStyle() | SWT.RESIZE | SWT.RIGHT_TO_LEFT);
    }

    @Override
    protected void configureShell(Shell shell) {
        super.configureShell(shell);
        shell.setText("הוספה לקוד");
    }

    @Override
    protected Control createDialogArea(Composite parent) {
        setTitle("הוספה לקוד: מבנה עם נתוני דמה");
        setMessage("המבנה נכנס לקוד במקום הסמן, מלא בנתונים אקראיים — אפשר לעבוד איתו מיד.");
        Composite area = (Composite) super.createDialogArea(parent);
        Composite main = new Composite(area, SWT.NONE);
        main.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        main.setLayout(new GridLayout(2, false));

        Composite form = new Composite(main, SWT.NONE);
        form.setLayoutData(new GridData(SWT.FILL, SWT.TOP, false, false));
        form.setLayout(new GridLayout(2, false));

        Group sg = new Group(form, SWT.NONE);
        sg.setText("מה ליצור");
        GridData sgd = new GridData(SWT.FILL, SWT.TOP, true, false);
        sgd.horizontalSpan = 2;
        sg.setLayoutData(sgd);
        sg.setLayout(new GridLayout(2, true));
        for (java.util.Map.Entry<String, String> e : Scaffold.SNIPPETS.entrySet()) {
            Button b = new Button(sg, SWT.RADIO);
            b.setText(e.getValue());
            b.setData(e.getKey());
            b.setSelection(e.getKey().equals(preset));
            structureButtons.add(b);
        }

        new Label(form, SWT.NONE).setText("טיפוס הערכים:");
        typeCombo = new Combo(form, SWT.READ_ONLY | SWT.LEFT_TO_RIGHT);
        typeCombo.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        for (String t : Scaffold.TYPES) typeCombo.add(t);
        typeCombo.select(0);

        new Label(form, SWT.NONE).setText("כמה איברים:");
        size = spinner(form, 5, 1, 30);
        new Label(form, SWT.NONE).setText("שורות (מערך דו-ממדי):");
        rows = spinner(form, 3, 1, 12);
        new Label(form, SWT.NONE).setText("עמודות (מערך דו-ממדי):");
        cols = spinner(form, 4, 1, 12);
        new Label(form, SWT.NONE).setText("שם המשתנה:");
        name = new Text(form, SWT.BORDER | SWT.LEFT_TO_RIGHT);
        name.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        preview = new StyledText(main, SWT.MULTI | SWT.READ_ONLY | SWT.H_SCROLL | SWT.V_SCROLL | SWT.BORDER | SWT.LEFT_TO_RIGHT);
        preview.setFont(JFaceResources.getTextFont());
        GridData pgd = new GridData(SWT.FILL, SWT.FILL, true, true);
        pgd.widthHint = 560;
        pgd.heightHint = 160;
        preview.setLayoutData(pgd);

        SelectionAdapter onChange = new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) { update(); }
        };
        for (Button b : structureButtons) b.addSelectionListener(onChange);
        typeCombo.addSelectionListener(onChange);
        for (Spinner sp : new Spinner[] { size, rows, cols }) sp.addModifyListener(e -> update());
        name.addModifyListener(e -> {
            if (name.isFocusControl()) nameEdited = true;
            update();
        });
        return area;
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

    private String structure() {
        for (Button b : structureButtons) if (b.getSelection()) return (String) b.getData();
        return "queue";
    }

    /** שם פנוי: q, q2, q3... (לפי מה שכבר כתוב בקובץ) */
    private String freeName(String base) {
        String n = base;
        for (int i = 2; java.util.regex.Pattern.compile("\\b" + n + "\\s*=").matcher(documentText).find(); i++) n = base + i;
        return n;
    }

    private void update() {
        if (updating || preview == null || getButton(IDialogConstants.OK_ID) == null) return;
        updating = true;
        try {
            String st = structure();
            boolean isStudent = st.equals("student"), isMatrix = st.equals("matrix");
            typeCombo.setEnabled(!isStudent);
            size.setEnabled(!isStudent && !isMatrix);
            rows.setEnabled(isMatrix);
            cols.setEnabled(isMatrix);
            if (!nameEdited) name.setText(freeName(Scaffold.snippetName(st)));

            Scaffold.SnippetOptions o = new Scaffold.SnippetOptions();
            o.structure = st;
            o.type = typeCombo.getText();
            o.name = name.getText().trim();
            o.size = size.getSelection();
            o.rows = rows.getSelection();
            o.cols = cols.getSelection();
            o.seed = seed;
            o.indent = indent;
            String error = null;
            try {
                result = Scaffold.snippet(o);
                preview.setText(result);
            } catch (IllegalArgumentException e) {
                error = e.getMessage();
                result = null;
            }
            setErrorMessage(error);
            getButton(IDialogConstants.OK_ID).setEnabled(error == null);
        } finally {
            updating = false;
        }
    }

    /** הקוד להכנסה (אחרי OK) */
    public String code() {
        return result;
    }
}
