package il.lamerhav.dsviz;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Shell;

/** חלון "הדרכה לשימוש": בוחרים הדרכה — לכל אחת סרטון קצר (נפתח בדפדפן) */
public class HelpDialog extends Dialog {
    /** {מזהה, כותרת, תיאור} — כמו ב-vscode-extension/help.js */
    static final String[][] ITEMS = {
        { "a", "\u200F01  ·  יצירת מבנה נתונים חדש", "שמים את הסמן במקום הנכון בקוד, לוחצים \"הוסף מבנה נתונים\", בוחרים מבנה, ערכים ושם — ורואים את הקוד שנוצר ואת ההרצה." },
        { "b", "\u200F02  ·  דוגמאות פתורות", "פותחים תרגיל מוכן מהרשימה, קוראים את הפתרון, ומריצים אותו בחלון ההדמיה — צעד אחרי צעד." },
        { "c", "\u200F03  ·  מבנה בתוך מבנה", "שלוש דוגמאות: מחסנית של תורים, רשימה של מחסניות ועץ של תורים — יצירת התרגיל וההרצה הוויזואלית." },
        { "d", "\u200F04  ·  מוסיפים התאמות לתרגיל", "אחרי שהתרגיל נוצר: מוסיפים ידנית מבנה נוסף, משתנה x ומערך — ורואים שגם הם מופיעים בהדמיה." },
        { "e", "\u200F05  ·  שליחת תרגיל והצעה לפתרון", "מעלים את דף התרגיל — צילום מסך (Ctrl+V), PDF או Word — ומקבלים קוד מוכן עם פעולה לכל סעיף; ולכל סעיף \"💡 הצעה לפתרון\" שנבדקה." },
    };

    private final List<Look.Chip> chips = new ArrayList<Look.Chip>();
    private int chosen = 0;

    public HelpDialog(Shell parent) {
        super(parent);
        setShellStyle(SWT.DIALOG_TRIM | SWT.RIGHT_TO_LEFT | SWT.APPLICATION_MODAL);
    }

    @Override
    protected void configureShell(Shell shell) {
        super.configureShell(shell);
        shell.setText("הדרכה לשימוש");
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
        Look.header(area, "הדרכה לשימוש", "בוחרים הדרכה — לכל אחת סרטון קצר שמראה בדיוק איפה לוחצים ומה נפתח.");
        Composite card = Look.card(area, new GridData(SWT.FILL, SWT.FILL, true, true));
        Look.section(card, "איזו הדרכה?", 0);
        Composite list = new Composite(card, SWT.NONE);
        GridLayout gl = new GridLayout(1, true);
        gl.marginWidth = 0;
        gl.marginHeight = 0;
        gl.verticalSpacing = 8;
        list.setLayout(gl);
        list.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
        for (int i = 0; i < ITEMS.length; i++) {
            Look.Chip b = Look.chip(list, ITEMS[i][1], Integer.valueOf(i), chips, 40, 420, () -> update());
            b.setSelection(i == 0);
        }
        Look.applyFont(area, -1);
        return area;
    }

    @Override
    protected Control createButtonBar(Composite parent) {
        Composite bar = Look.buttonBar(parent, "צפייה בהדרכה", () -> okPressed(), () -> cancelPressed(), new Look.Round[1]);
        update();
        return bar;
    }

    private void update() {
        for (Look.Chip c : chips) if (c.getSelection()) chosen = (Integer) c.getData();
    }

    /** מזהה ההדרכה שנבחרה (a, b, c, d) */
    public String chosenId() {
        return ITEMS[chosen][0];
    }
}
