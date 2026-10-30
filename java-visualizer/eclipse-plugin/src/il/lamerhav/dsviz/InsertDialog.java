package il.lamerhav.dsviz;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

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
import org.eclipse.swt.widgets.Spinner;
import org.eclipse.swt.widgets.Text;

/**
 * חלון "הוסף מבנה נתונים": בוחרים מבנה (כולל מבנה בתוך מבנה), טיפוס, ערכים (או אקראיים) ושם — רואים איך המבנה ייראה,
 * ואחרי אישור הוא נכנס לקוד במקום הסמן. השם מוצע באפור, ונבדק שהוא חוקי ולא מתנגש במשהו שכבר מוגדר באותו מקום.
 */
public class InsertDialog extends Dialog {
    private final String preset, documentText, indent;
    private final int offset;   // מיקום ההכנסה בקובץ — לבדיקת שמות (-1 = לא ידוע)
    private final List<Look.Chip> structureButtons = new ArrayList<Look.Chip>();
    private final List<Look.Chip> typeButtons = new ArrayList<Look.Chip>();
    private final List<Look.Chip> insideButtons = new ArrayList<Look.Chip>();   // מה יש בכל תא: ערכים / מחסניות / תורים / רשימות
    private Label typeSection, valuesLabel, previewType, errorLabel;
    private Button randomCheck;
    private Label helperNote;
    private boolean wantRandom = true;   // אקראי מסומן מראש — עד שהמשתמש מוריד את הסימון
    private Spinner countSpinner;
    private Text valuesText;
    private Look.HintText nameField;
    private NestedPreview previewCanvas;
    private ScrolledComposite scroll;
    private boolean updating, lastRandom;
    private int lastCount, seed = new Random().nextInt(Integer.MAX_VALUE);
    private String lastType = "", lastStructure = "";
    private String result, chosenClass;
    private Look.Round ok;

    public InsertDialog(Shell parent, String preset, String documentText, int offset, String indent) {
        super(parent);
        this.preset = preset == null ? "queue" : preset;
        this.documentText = documentText == null ? "" : documentText;
        this.offset = offset;
        this.indent = indent;
        setShellStyle(SWT.DIALOG_TRIM | SWT.RESIZE | SWT.RIGHT_TO_LEFT | SWT.APPLICATION_MODAL);
    }

    @Override
    protected void configureShell(Shell shell) {
        super.configureShell(shell);
        shell.setText("הוסף מבנה נתונים");
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
        Look.header(area, "הוסף מבנה נתונים", "המבנה נבנה בקוד במקום הסמן, עם הערכים שבחרתם — רואים כאן איך הוא ייראה.");

        GridLayout cl = new GridLayout(2, false);
        cl.marginWidth = 0;
        cl.marginHeight = 0;
        cl.horizontalSpacing = 14;
        Composite[] holder = new Composite[1];
        scroll = Look.scroll(area, cl, holder);
        Composite cols = holder[0];
        Composite right = Look.card(cols, new GridData(SWT.FILL, SWT.FILL, false, true));
        Composite left = Look.card(cols, new GridData(SWT.FILL, SWT.FILL, true, true));

        // --- איזה מבנה
        Look.section(right, "איזה מבנה נתונים?", 0);
        Composite structs = grid(right, 2, true, 6);
        String[] presetParts = preset.split("_");
        String initial = presetParts[0], initialInside = presetParts.length > 1 ? presetParts[1] : "";
        for (java.util.Map.Entry<String, String> e : Scaffold.SNIPPETS.entrySet()) {
            Look.Chip b = chip(structs, e.getValue(), e.getKey(), structureButtons, 28, 120);
            b.setSelection(e.getKey().equals(initial));
        }
        Look.section(right, "מה יש בכל תא?", 8);
        Composite inside = grid(right, 2, true, 6);
        for (String[] in : Scaffold.INSIDE) {
            Look.Chip b = chip(inside, in[1], in[0], insideButtons, 24, 120);
            b.setToolTipText(in[2]);
            b.setSelection(in[0].equals(initialInside));
        }
        typeSection = Look.section(right, "טיפוס הערכים", 8);
        Composite types = grid(right, 3, true, 6);
        for (String t : Scaffold.TYPES) {
            Look.Chip b = chip(types, t, t, typeButtons, 24, 76);
            b.setSelection(t.equals("Integer"));
        }

        // --- מילוי: אקראי (כמה) או ידני בשדה
        Look.section(left, "מילוי מבנה-נתונים", 0);
        Composite rnd = grid(left, 2, false, 6);
        randomCheck = new Button(rnd, SWT.CHECK);
        randomCheck.setText("כמה ערכים אקראיים:");
        countSpinner = new Spinner(rnd, SWT.BORDER);
        countSpinner.setValues(6, 1, 20, 0, 1, 5);
        Composite vals = grid(left, 2, false, 6);
        valuesLabel = new Label(vals, SWT.NONE);
        valuesText = new Text(vals, SWT.BORDER | SWT.LEFT_TO_RIGHT);
        GridData vg = new GridData(SWT.FILL, SWT.CENTER, true, false);
        vg.widthHint = 240;
        valuesText.setLayoutData(vg);

        // --- תזכורת: פעולות העזר מהספרייה (ההערה נכנסת גם לקוד)
        helperNote = new Label(left, SWT.WRAP);
        helperNote.setText("פעולות עזר מהספרייה: size(x) — כמה איברים, duplicate(x) — עותק. המבנה שנשלח לא משתנה.");
        helperNote.setForeground(left.getDisplay().getSystemColor(SWT.COLOR_DARK_GRAY));
        GridData hg = new GridData(SWT.FILL, SWT.CENTER, true, false);
        hg.verticalIndent = 6;
        helperNote.setLayoutData(hg);

        // --- איך ייראה
        Look.section(left, "כך ייראה המבנה", 8);
        previewType = new Label(left, SWT.LEFT_TO_RIGHT);
        previewType.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
        previewType.setFont(Look.font(area.getDisplay(), 0, SWT.BOLD));
        previewType.setData("dsviz.keepFont", Boolean.TRUE);
        previewCanvas = new NestedPreview(left);
        GridData pg = new GridData(SWT.FILL, SWT.TOP, true, false);
        pg.widthHint = 300;
        pg.heightHint = 110;
        previewCanvas.setLayoutData(pg);

        // --- שם המשתנה (ההצעה באפור)
        Look.section(left, "שם המשתנה", 8);
        Composite nm = grid(left, 2, false, 6);
        new Label(nm, SWT.NONE).setText("שם:");
        nameField = new Look.HintText(nm, SWT.BORDER | SWT.LEFT_TO_RIGHT);
        GridData ng = new GridData(SWT.FILL, SWT.CENTER, true, false);
        ng.widthHint = 160;
        nameField.text.setLayoutData(ng);
        nameField.text.setToolTipText("אם משאירים ריק — נכנסת ההצעה (באפור)");

        errorLabel = new Label(area, SWT.WRAP);
        GridData eg = new GridData(SWT.FILL, SWT.TOP, true, false);
        eg.widthHint = 400;
        errorLabel.setLayoutData(eg);
        errorLabel.setForeground(area.getDisplay().getSystemColor(SWT.COLOR_RED));

        randomCheck.addListener(SWT.Selection, e -> { wantRandom = randomCheck.getSelection(); update(); });
        countSpinner.addListener(SWT.Modify, e -> update());
        valuesText.addListener(SWT.Modify, e -> update());
        nameField.text.addListener(SWT.Modify, e -> update());
        Look.applyFont(area, -1);
        Look.wheel(scroll);
        return area;
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

    /** כפתור-בחירה: לוחצים עליו ונבחר, והאחרים בקבוצה מתבטלים */
    private Look.Chip chip(Composite parent, String text, Object data, final List<Look.Chip> group, int height, int width) {
        return Look.chip(parent, text, data, group, height, width, () -> update());
    }

    @Override
    protected Control createButtonBar(Composite parent) {
        Look.Round[] out = new Look.Round[1];
        Composite bar = Look.buttonBar(parent, "הוספה לקוד", () -> { if (ok.getEnabled()) okPressed(); }, () -> cancelPressed(), out);
        ok = out[0];
        nameField.text.addListener(SWT.DefaultSelection, e -> { if (ok.getEnabled()) okPressed(); });
        valuesText.addListener(SWT.DefaultSelection, e -> { if (ok.getEnabled()) okPressed(); });
        update();
        return bar;
    }

    /** החלון תמיד נכנס למסך — אם התוכן גבוה מדי מופיע פס גלילה והכפתורים נשארים גלויים */
    @Override
    protected Point getInitialSize() {
        Point p = super.getInitialSize();
        Rectangle screen = getShell().getMonitor().getClientArea();
        return new Point(Math.min(p.x, screen.width - 40), Math.min(p.y, screen.height - 60));
    }

    private static String selected(List<Look.Chip> group, String fallback) {
        for (Look.Chip b : group) if (b.getSelection()) return (String) b.getData();
        return fallback;
    }

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
        String outer = selected(structureButtons, "queue");
        boolean card = outer.equals("student") || outer.equals("car");
        // מה יש בכל תא: אפשרי במחסנית, תור, רשימה, עץ ומערך; בשאר — רק ערכים רגילים
        boolean nestable = !card && Scaffold.canNest(outer);
        String inside = nestable ? selected(insideButtons, "") : "";
        for (Look.Chip b : insideButtons) {
            b.setEnabled(nestable || ((String) b.getData()).isEmpty());
            b.setSelection(((String) b.getData()).equals(inside));
        }
        String st = Scaffold.comboId(outer, inside);
        boolean combo = !st.equals(outer) && !card;
        boolean noObjects = combo || outer.equals("matrix");
        String type = selected(typeButtons, "Integer");
        for (Look.Chip b : typeButtons) {
            boolean obj = b.getData().equals("Student") || b.getData().equals("Car");
            b.setEnabled(!card && !(noObjects && obj));
        }
        if (noObjects && (type.equals("Student") || type.equals("Car"))) {
            type = "Integer";
            for (Look.Chip b : typeButtons) b.setSelection(b.getData().equals("Integer"));
        }
        typeSection.setText(combo ? "טיפוס הערכים (בתוך המבנים הפנימיים)" : "טיפוס הערכים");
        boolean objects = !card && (type.equals("Student") || type.equals("Car"));

        // ערכים: אקראיים (בוחרים כמה והשדה מתמלא — אפשר לשנות), או כתיבה ידנית. תלמיד / מכונית — כמה ליצור
        boolean helpers = !card && Scaffold.hasHelpers(st);
        helperNote.setVisible(helpers);
        ((GridData) helperNote.getLayoutData()).exclude = !helpers;
        randomCheck.setEnabled(!objects && !card);
        randomCheck.setSelection(!objects && !card && wantRandom);
        boolean random = randomCheck.getSelection();
        countSpinner.setEnabled(random);
        randomCheck.setText(combo ? "כמה מבנים פנימיים אקראיים:" : outer.equals("matrix") ? "כמה שורות אקראיות:" : "כמה ערכים אקראיים:");
        int maxCount = combo ? Scaffold.MAX_INNER : 20;
        if (countSpinner.getMaximum() != maxCount) countSpinner.setMaximum(maxCount);
        int count = countSpinner.getSelection();
        if (card) {
            if (!st.equals(lastStructure)) valuesText.setText("");
        } else if (random && (random != lastRandom || count != lastCount || !type.equals(lastType) || !st.equals(lastStructure))) {
            seed = new Random().nextInt(Integer.MAX_VALUE);
            valuesText.setText(Scaffold.randomValues(st, type, count, seed));
        } else if (!random && (lastRandom || !type.equals(lastType) || !st.equals(lastStructure))) {
            valuesText.setText(Scaffold.defaultValues(st, type, false));
        }
        valuesText.setEnabled(!card);
        lastRandom = random;
        lastCount = count;
        lastType = type;
        lastStructure = st;
        valuesLabel.setText(card ? "" : type.equals("Student") ? "כמה תלמידים:" : type.equals("Car") ? "כמה מכוניות:"
                : combo ? (outer.equals("tree") ? "מבנים פנימיים (לפי רמות, ;):" : "מבנים פנימיים (ב-;):")
                : outer.equals("matrix") ? "ערכים (שורות ב-;):" : "ערכים:");

        String error = null;
        String values = card ? null : valuesText.getText();
        String shownType = card ? (st.equals("student") ? "Student" : "Car") : type;

        // שם: ההצעה באפור, ואם הקלידו — נבדק שהוא חוקי ושלא מתנגש
        NameCheck.Ident ident = s -> Scaffold.isIdentifier(s);
        String suggestion = NameCheck.suggestName(Scaffold.snippetName(st), documentText, offset, ident);
        nameField.setHint(suggestion);
        String typed = nameField.value().trim();
        String name = typed.isEmpty() ? suggestion : typed;
        if (!typed.isEmpty()) error = NameCheck.checkName(typed, documentText, offset, ident);

        // התצוגה והקוד
        Scaffold.Preview pv = null;
        try {
            pv = Scaffold.preview(st, shownType, values);
        } catch (IllegalArgumentException e) {
            if (error == null) error = e.getMessage();
        }
        if (pv != null) {
            previewType.setText(pv.type);
            previewCanvas.set(pv);
        }
        result = null;
        chosenClass = null;
        if (error == null) {
            Scaffold.SnippetOptions o = new Scaffold.SnippetOptions();
            o.structure = st;
            o.type = shownType;
            o.name = name;
            o.values = values;
            o.indent = indent;
            try {
                result = Scaffold.snippet(o);
                chosenClass = card ? shownType : (objects && !outer.equals("matrix") && !combo) ? shownType : null;
            } catch (IllegalArgumentException e) {
                error = e.getMessage();
            }
        }
        errorLabel.setText(error == null ? "" : error);
        ok.setEnabled(error == null);
        Look.updateScroll(scroll);
        if (getShell().isVisible()) Look.fit(getShell(), true);
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
