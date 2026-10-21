package il.lamerhav.dsviz;

import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.FontData;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.TableItem;

/** חלון קטן שמראה מה יש במחלקה (Student / Car): איך יוצרים, אילו מאפיינים ואילו פעולות */
public class ClassInfoDialog extends Dialog {
    private final ClassInfo.Info info;
    private Font headerFont;

    public ClassInfoDialog(Shell parent, String className) {
        super(parent);
        this.info = ClassInfo.ALL.get(className);
        setShellStyle(SWT.DIALOG_TRIM | SWT.RESIZE | SWT.RIGHT_TO_LEFT | SWT.MODELESS);
    }

    /** מציג את החלון אם יש מידע על המחלקה */
    public static void show(Shell shell, String className) {
        if (className == null || !ClassInfo.ALL.containsKey(className)) return;
        ClassInfoDialog d = new ClassInfoDialog(shell, className);
        d.setBlockOnOpen(false);
        d.open();
    }

    @Override
    protected void configureShell(Shell shell) {
        super.configureShell(shell);
        shell.setText("מה יש במחלקה " + info.name);
    }

    @Override
    protected Control createDialogArea(Composite parent) {
        Composite area = (Composite) super.createDialogArea(parent);
        GridLayout al = (GridLayout) area.getLayout();
        al.marginWidth = 14;
        al.marginHeight = 10;
        al.verticalSpacing = 6;
        FontData hd = JFaceResources.getDialogFont().getFontData()[0];
        headerFont = new Font(area.getDisplay(), hd.getName(), hd.getHeight() + 4, SWT.BOLD);
        area.addDisposeListener(e -> headerFont.dispose());

        Label title = new Label(area, SWT.NONE);
        title.setText(info.title);
        title.setFont(headerFont);
        Label intro = new Label(area, SWT.WRAP);
        intro.setText(info.intro);
        GridData ig = new GridData(SWT.FILL, SWT.TOP, true, false);
        ig.widthHint = 560;
        intro.setLayoutData(ig);

        section(area, "איך יוצרים");
        table(area, info.creators, new String[] { "קוד", "מה זה" }, new int[] { 270, 290 }, 2);
        section(area, "מאפיינים");
        table(area, info.fields, new String[] { "שם", "טיפוס", "מה זה" }, new int[] { 110, 70, 380 }, 5);
        section(area, "פעולות");
        table(area, info.methods, new String[] { "פעולה", "מה היא עושה" }, new int[] { 270, 290 }, 8);
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

    private void table(Composite parent, String[][] rows, String[] heads, int[] widths, int visible) {
        Table t = new Table(parent, SWT.BORDER | SWT.FULL_SELECTION | SWT.V_SCROLL | SWT.LEFT_TO_RIGHT);
        t.setHeaderVisible(true);
        t.setLinesVisible(true);
        for (int i = 0; i < heads.length; i++) {
            TableColumn c = new TableColumn(t, SWT.LEFT);
            c.setText(heads[i]);
            c.setWidth(widths[i]);
        }
        for (String[] r : rows) new TableItem(t, SWT.NONE).setText(r);
        GridData gd = new GridData(SWT.FILL, SWT.TOP, true, false);
        gd.heightHint = t.getItemHeight() * (Math.min(rows.length, visible) + 1) + 6;
        t.setLayoutData(gd);
    }

    @Override
    protected void createButtonsForButtonBar(Composite parent) {
        createButton(parent, IDialogConstants.OK_ID, "הבנתי", true);
    }

    @Override
    protected Point getInitialSize() {
        Point p = super.getInitialSize();
        Rectangle screen = getShell().getMonitor().getClientArea();
        return new Point(Math.min(p.x, screen.width - 40), Math.min(p.y, screen.height - 40));
    }
}
