package il.lamerhav.dsviz;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;

/** חלון "דוגמאות פתורות": בוחרים דוגמה (כל אחת היא קובץ עם פעולות פתורות ו-main) — והיא נפתחת, מוכנה ל"הרצה ויזואלית" */
public class ExamplesDialog extends Dialog {
    /** {שם הקובץ, כותרת, מה יש בה} — באותו סדר כמו ProjectSetup.EXAMPLES */
    static final String[][] ITEMS = {
        { "StackExamples", "מחסנית", "היפוך, הכנסה לתחתית (רקורסיה)" },
        { "QueueExamples", "תור", "ספירה, מחיקת מופעים" },
        { "ListExamples", "רשימה מקושרת", "סכום, מחיקה, היפוך" },
        { "TreeExamples", "עצים", "ספירה, גובה, הכנסה לעץ חיפוש" },
        { "ArrayExamples", "מערכים", "מקסימום, היפוך, סכומי שורות, מיון בועות" },
        { "StudentExamples", "תלמידים (Student)", "מצטיינים בתור, הטוב ביותר במערך" },
        { "CarExamples", "מכוניות (Car)", "ישנות בתור, החדשה במערך, סה\"כ ק\"מ" },
        { "RecursionExamples", "רקורסיה", "עצרת, סכום מערך, הדפסת מחסנית" },
        { "NestedExamples", "מבנה בתוך מבנה", "מחסנית של מחסניות, רשימה של תורים, עץ של מחסניות" },
    };

    private final List<Look.Chip> chips = new ArrayList<Look.Chip>();
    private Label about;
    private Look.Round ok;
    private int chosen = 0;

    public ExamplesDialog(Shell parent) {
        super(parent);
        setShellStyle(SWT.DIALOG_TRIM | SWT.RESIZE | SWT.RIGHT_TO_LEFT | SWT.APPLICATION_MODAL);
    }

    @Override
    protected void configureShell(Shell shell) {
        super.configureShell(shell);
        shell.setText("דוגמאות פתורות");
        shell.setBackground(Look.color(shell.getDisplay(), Look.BG));
        shell.setBackgroundMode(SWT.INHERIT_DEFAULT);
    }

    @Override
    protected Control createDialogArea(Composite parent) {
        Composite area = (Composite) super.createDialogArea(parent);
        GridLayout al = (GridLayout) area.getLayout();
        al.marginWidth = 16;
        al.marginHeight = 8;
        al.verticalSpacing = 6;
        area.setBackground(Look.color(area.getDisplay(), Look.BG));
        area.setBackgroundMode(SWT.INHERIT_DEFAULT);
        Look.header(area, "דוגמאות פתורות", "בוחרים דוגמה — היא נפתחת בעורך, ואז לוחצים \"הרצה ויזואלית\" ורואים איך היא עובדת.");
        Composite card = Look.card(area, new GridData(SWT.FILL, SWT.FILL, true, true));
        Look.section(card, "איזו דוגמה לפתוח?", 0);
        Composite grid = new Composite(card, SWT.NONE);
        GridLayout gl = new GridLayout(3, true);
        gl.marginWidth = 0;
        gl.marginHeight = 0;
        gl.horizontalSpacing = 8;
        gl.verticalSpacing = 8;
        grid.setLayout(gl);
        grid.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
        for (int i = 0; i < ITEMS.length; i++) {
            Look.Chip b = Look.chip(grid, ITEMS[i][1], Integer.valueOf(i), chips, 40, 150, () -> update());
            b.setToolTipText(ITEMS[i][2]);
            b.setSelection(i == 0);
        }
        about = new Label(card, SWT.WRAP);
        GridData ag = new GridData(SWT.FILL, SWT.TOP, true, false);
        ag.widthHint = 460;
        ag.verticalIndent = 8;
        about.setLayoutData(ag);
        about.setForeground(Look.color(area.getDisplay(), Look.MUTED));
        Look.applyFont(area, -1);
        return area;
    }

    @Override
    protected Control createButtonBar(Composite parent) {
        Look.Round[] out = new Look.Round[1];
        Composite bar = Look.buttonBar(parent, "פתיחת הדוגמה", () -> okPressed(), () -> cancelPressed(), out);
        ok = out[0];
        update();
        return bar;
    }

    private void update() {
        for (Look.Chip c : chips) if (c.getSelection()) chosen = (Integer) c.getData();
        if (about != null) about.setText("בדוגמה: " + ITEMS[chosen][2] + "   (" + ITEMS[chosen][0] + ".java)");
    }

    /** האינדקס של הדוגמה שנבחרה (ב-ITEMS / ProjectSetup.EXAMPLES) */
    public int chosen() {
        return chosen;
    }
}
