package il.lamerhav.dsviz;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StyleRange;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.dnd.Clipboard;
import org.eclipse.swt.dnd.TextTransfer;
import org.eclipse.swt.dnd.Transfer;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;

/**
 * החלון "הצעה לפתרון": הפעולה של הסעיף כמו ש-Gemini הציע (ונבדקה — SolutionCheck), לקריאה ולהעתקה.
 * מימין לשמאל, והקוד עצמו משמאל לימין עם צבעים כמו בעורך. לא חוסם את Eclipse — אפשר להשוות לקוד שכתבתם.
 */
public class SolutionDialog extends Dialog {
    private final SolutionStore.Entry entry;
    private Label copied;
    private StyledText code;
    private Image icon;

    SolutionDialog(Shell parent, SolutionStore.Entry entry) {
        super(parent);
        this.entry = entry;
        setShellStyle(SWT.DIALOG_TRIM | SWT.RESIZE | SWT.RIGHT_TO_LEFT | SWT.MODELESS);
        setBlockOnOpen(false);
    }

    static void show(Shell parent, SolutionStore.Entry entry) {
        if (entry == null) return;
        new SolutionDialog(parent, entry).open();
    }

    /** "סעיף א'" (אות עברית — עם גרש) */
    static String section(String label) {
        String l = label == null ? "" : label.trim();
        if (l.isEmpty()) return "";
        return "סעיף " + l + (l.matches("[א-ת]") ? "'" : "");
    }

    @Override
    protected void configureShell(Shell shell) {
        super.configureShell(shell);
        String s = section(entry.label);
        shell.setText("הצעה לפתרון" + (s.isEmpty() ? "" : " — " + s));
        shell.setBackground(Look.color(shell.getDisplay(), Look.BG));
        shell.setBackgroundMode(SWT.INHERIT_DEFAULT);
        Image small = image(shell.getDisplay(), "icons/solution.png");
        if (small != null) {
            shell.setImage(small);
            shell.addDisposeListener(e -> small.dispose());
        }
    }

    @Override
    protected Control createDialogArea(Composite parent) {
        Composite area = (Composite) super.createDialogArea(parent);
        Display d = area.getDisplay();
        GridLayout al = (GridLayout) area.getLayout();
        al.marginWidth = 20;
        al.marginHeight = 14;
        al.verticalSpacing = 10;
        area.setBackground(Look.color(d, Look.BG));
        area.setBackgroundMode(SWT.INHERIT_DEFAULT);

        // כותרת: מנורה + "הצעה לפתרון — סעיף א'" + שורת הסבר
        Composite head = new Composite(area, SWT.NONE);
        GridLayout hl = new GridLayout(2, false);
        hl.marginWidth = 0;
        hl.marginHeight = 0;
        hl.horizontalSpacing = 12;
        head.setLayout(hl);
        head.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
        Label bulb = new Label(head, SWT.NONE);
        icon = image(d, "icons/solution-big.png");
        if (icon != null) bulb.setImage(icon);
        bulb.setLayoutData(new GridData(SWT.CENTER, SWT.TOP, false, false));
        area.addDisposeListener(e -> { if (icon != null) icon.dispose(); });
        Composite titles = new Composite(head, SWT.NONE);
        GridLayout tl = new GridLayout(1, false);
        tl.marginWidth = 0;
        tl.marginHeight = 0;
        tl.verticalSpacing = 2;
        titles.setLayout(tl);
        titles.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        String s = section(entry.label);
        Look.header(titles, "הצעה לפתרון" + (s.isEmpty() ? "" : " — " + s),
                "כדאי לנסות קודם לבד. נתקעתם? הנה דרך אחת לפתור — יש גם דרכים נכונות אחרות.");

        status(area);

        // הקוד — משמאל לימין, בגופן של העורך, עם צבעים
        Composite card = Look.card(area, new GridData(SWT.FILL, SWT.FILL, true, true));
        code = new StyledText(card, SWT.MULTI | SWT.READ_ONLY | SWT.H_SCROLL | SWT.V_SCROLL | SWT.LEFT_TO_RIGHT);
        code.setFont(JFaceResources.getTextFont());
        code.setText(entry.code);
        code.setBackground(Look.color(d, Look.CARD));
        code.setForeground(Look.color(d, Look.TEXT));
        code.setLeftMargin(6);
        code.setTopMargin(4);
        code.setBottomMargin(4);
        code.setStyleRanges(colors(d, entry.code));
        GridData cg = new GridData(SWT.FILL, SWT.FILL, true, true);
        GC gc = new GC(code);
        int cw = gc.getFontMetrics().getAverageCharWidth(), lh = code.getLineHeight();
        gc.dispose();
        int longest = 0;
        String[] lines = entry.code.split("\n");
        for (String l : lines) longest = Math.max(longest, l.length());
        Rectangle screen = d.getPrimaryMonitor().getClientArea();
        cg.widthHint = Math.max(460, Math.min(screen.width * 2 / 3, (longest + 4) * cw));
        cg.heightHint = Math.max(6, Math.min(lines.length + 1, Math.max(10, screen.height / lh / 2))) * lh;
        code.setLayoutData(cg);

        Look.applyFont(area, -1);
        code.setFont(JFaceResources.getTextFont());
        return area;
    }

    /** האם ההצעה נבדקה: הרצנו את main עם ההצעה והשווינו לפלט הצפוי */
    private void status(Composite area) {
        Display d = area.getDisplay();
        String text;
        int bg, fg;
        switch (entry.status) {
            case "ok":
                text = "✓  נבדק: הרצנו את main עם ההצעה הזו — והודפס בדיוק מה שצריך להיות מודפס.";
                bg = 0xE7F6EC;
                fg = 0x1E6B34;
                break;
            case "ran":
                text = "✓  ההצעה התקמפלה ורצה בלי שגיאות (בתרגיל לא היה פלט צפוי להשוואה).";
                bg = 0xE8F0FE;
                fg = 0x24489B;
                break;
            case "failed":
                text = "⚠  שימו לב: לא הצלחנו לוודא שההצעה הזו נותנת בדיוק את הפלט הצפוי — בדקו אותה בעצמכם.";
                bg = 0xFFF4DE;
                fg = 0x8A5300;
                break;
            default:
                text = "ההצעה לא נבדקה במחשב — כדאי להריץ ולבדוק.";
                bg = 0xF1F3F7;
                fg = Look.MUTED;
        }
        Composite pill = new Composite(area, SWT.NONE);
        pill.setBackground(Look.color(d, bg));
        GridLayout pl = new GridLayout(1, false);
        pl.marginWidth = 12;
        pl.marginHeight = 7;
        pill.setLayout(pl);
        pill.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
        Label l = new Label(pill, SWT.WRAP);
        l.setText(text);
        l.setBackground(Look.color(d, bg));
        l.setForeground(Look.color(d, fg));
        GridData lg = new GridData(SWT.FILL, SWT.CENTER, true, false);
        lg.widthHint = 420;
        l.setLayoutData(lg);
    }

    @Override
    protected Control createButtonBar(Composite parent) {
        Display d = parent.getDisplay();
        Composite bar = new Composite(parent, SWT.NONE);
        bar.setBackground(Look.color(d, Look.BG));
        GridLayout l = new GridLayout(4, false);
        l.marginWidth = 20;
        l.marginHeight = 10;
        l.horizontalSpacing = 10;
        bar.setLayout(l);
        bar.setLayoutData(new GridData(SWT.FILL, SWT.BOTTOM, true, false));
        new Look.Round(bar, "העתקה", true, this::copy);
        new Look.Round(bar, "סגירה", false, this::close);
        copied = new Label(bar, SWT.NONE);
        copied.setForeground(Look.color(d, 0x1E6B34));
        copied.setLayoutData(new GridData(SWT.BEGINNING, SWT.CENTER, false, false));
        Label c = new Label(bar, SWT.LEFT_TO_RIGHT);
        c.setText(Look.COPYRIGHT);
        c.setFont(Look.font(d, -2, SWT.NORMAL));
        c.setForeground(Look.color(d, 0xA0A8B8));
        c.setLayoutData(new GridData(SWT.END, SWT.CENTER, true, false));
        return bar;
    }

    /** מעתיק את כל הפעולה (או רק את מה שסומן) ללוח */
    private void copy() {
        String sel = code.getSelectionText();
        String text = sel == null || sel.isEmpty() ? entry.code : sel;
        Clipboard cb = new Clipboard(getShell().getDisplay());
        try {
            cb.setContents(new Object[] { text }, new Transfer[] { TextTransfer.getInstance() });
        } finally {
            cb.dispose();
        }
        copied.setText(sel == null || sel.isEmpty() ? "✓ הפעולה הועתקה — אפשר להדביק (Ctrl+V)" : "✓ מה שסימנתם הועתק");
        copied.getParent().layout(true);
    }

    @Override
    protected Point getInitialSize() {
        Point p = super.getInitialSize();
        Rectangle screen = getShell().getDisplay().getPrimaryMonitor().getClientArea();
        return new Point(Math.min(p.x, screen.width - 40), Math.min(p.y, screen.height - 60));
    }

    /** סמל מתוך התוסף (עם הגרסה הכפולה @2x למסכים בהגדלה) */
    static Image image(Display d, String path) {
        try {
            org.osgi.framework.Bundle b = org.eclipse.core.runtime.Platform.getBundle("il.lamerhav.dsviz");
            java.net.URL u = b == null ? null : b.getEntry(path);
            return u == null ? null : ImageDescriptor.createFromURL(u).createImage(false, d);
        } catch (Exception e) {
            return null;
        }
    }

    // ------------------------------------------------------------------ צבעים כמו בעורך של Eclipse

    private static final Set<String> KEYWORDS = new HashSet<String>(Arrays.asList(
            "public", "private", "protected", "static", "final", "void", "int", "double", "char", "boolean", "long", "float", "short", "byte",
            "if", "else", "while", "for", "do", "return", "new", "null", "true", "false", "this", "class", "break", "continue", "switch", "case",
            "default", "super", "instanceof", "throw", "try", "catch", "finally"));

    static StyleRange[] colors(Display d, String s) {
        List<StyleRange> out = new ArrayList<StyleRange>();
        int n = s.length();
        for (int i = 0; i < n; ) {
            char c = s.charAt(i);
            int start = i;
            if (c == '/' && i + 1 < n && s.charAt(i + 1) == '/') {
                while (i < n && s.charAt(i) != '\n') i++;
                out.add(range(d, start, i, 0x3F7F5F, SWT.NORMAL));
            } else if (c == '/' && i + 1 < n && s.charAt(i + 1) == '*') {
                int e = s.indexOf("*/", i + 2);
                i = e < 0 ? n : e + 2;
                out.add(range(d, start, i, 0x3F7F5F, SWT.NORMAL));
            } else if (c == '"' || c == '\'') {
                i++;
                while (i < n && s.charAt(i) != c && s.charAt(i) != '\n') i += s.charAt(i) == '\\' ? 2 : 1;
                i = Math.min(n, i + 1);
                out.add(range(d, start, i, 0x2A00FF, SWT.NORMAL));
            } else if (Character.isJavaIdentifierStart(c)) {
                while (i < n && Character.isJavaIdentifierPart(s.charAt(i))) i++;
                if (KEYWORDS.contains(s.substring(start, i))) out.add(range(d, start, i, 0x7F0055, SWT.BOLD));
            } else if (Character.isDigit(c)) {
                while (i < n && (Character.isLetterOrDigit(s.charAt(i)) || s.charAt(i) == '.')) i++;
                out.add(range(d, start, i, 0x125B9E, SWT.NORMAL));
            } else i++;
        }
        return out.toArray(new StyleRange[0]);
    }

    private static StyleRange range(Display d, int from, int to, int rgb, int style) {
        StyleRange r = new StyleRange(from, to - from, Look.color(d, rgb), null);
        r.fontStyle = style;
        return r;
    }
}
