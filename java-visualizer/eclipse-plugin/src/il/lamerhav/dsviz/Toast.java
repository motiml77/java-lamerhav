package il.lamerhav.dsviz;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IResource;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.ScrolledComposite;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Canvas;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.ide.IDE;

/**
 * הודעה קטנה בפינה התחתונה של חלון Eclipse אחרי שהתרגיל נוצר — במקום חלון שצריך לסגור. מימין לשמאל, נעלמת לבד אחרי
 * כ-10 שניות (לא כשהעכבר עליה), נסגרת ב-✕ או בלחיצה, ולא לוקחת את המיקוד מהעורך (הסמן נשאר בסעיף א).
 * קצרה: "התרגיל נוצר", כמה סעיפים ואיפה הסמן, ובשורה אחת אילו סעיפים עונים עליהם במילים — עם "הצגה" שפותח חלונית קטנה
 * עם הטקסט שלהם (הקוד שבתוכם — בגופן קוד, משמאל לימין). כשיש שגיאות קומפילציה — אותה הודעה בצבע אזהרה, ו"הצגה" קופץ לשגיאה.
 */
final class Toast {
    /** לבדיקות: shell.getData(ID) — "done" / "warn"; החלונית של "הצגה" — POPOVER */
    static final String ID = "dsviz.toast", POPOVER = "dsviz.popover";
    static final int WIDTH = 340, HIDE_MS = 10000;

    private static Shell current;

    private final Shell parent, shell;
    private Shell pop;
    private final Listener follow;

    /** מה כתוב בהודעה (נבנה מהתרגיל — created, או ישירות בבדיקות) */
    static final class Spec {
        boolean warn;
        String title = "", summary = "", folder = "", words = "", errorsLine = "";
        boolean solutions;
        List<String[]> others = new ArrayList<String[]>();
        Runnable onErrors;
    }

    /** ההודעה אחרי "שליחת תרגיל" */
    static Toast created(final IWorkbenchPage page, final SubmitHandler.Build b) {
        Spec s = new Spec();
        int n = b.errors.size();
        s.warn = n > 0;
        s.title = n == 0 ? "התרגיל נוצר" : "התרגיל נוצר · יש " + (n == 1 ? "שגיאת קומפילציה אחת" : n + " שגיאות קומפילציה");
        String first = b.exercise == null || b.exercise.tasks.isEmpty() ? "" : b.exercise.tasks.get(0).label.trim().replaceAll("['׳]+$", "");
        s.summary = b.tasks == 0 ? "בתרגיל אין סעיפים בקוד"
                : (b.tasks == 1 ? "סעיף אחד" : b.tasks + " סעיפים") + (first.isEmpty() ? "" : " · הסמן בסעיף " + first);
        s.folder = b.folderName;
        if (b.exercise != null) {
            s.others = b.exercise.others;
            s.words = AiScaffold.wordsLine(b.exercise.others);
        }
        if (n > 0) {
            s.errorsLine = "מסומנות באדום בקוד";
            s.onErrors = () -> firstError(page, b.files, b.errors);
        }
        s.solutions = !b.solutionStatus.isEmpty();
        return show(page.getWorkbenchWindow().getShell(), s);
    }

    static Toast show(Shell parent, Spec s) {
        if (current != null && !current.isDisposed()) current.dispose();
        return new Toast(parent, s);
    }

    private Toast(final Shell parent, final Spec s) {
        this.parent = parent;
        final Display d = parent.getDisplay();
        Control focus = d.getFocusControl();
        shell = new Shell(parent, SWT.NO_TRIM | SWT.ON_TOP | SWT.TOOL | SWT.NO_FOCUS | SWT.RIGHT_TO_LEFT);
        current = shell;
        shell.setData(ID, s.warn ? "warn" : "done");
        // מסגרת של 1px: הרקע של החלון בצבע הקו, והתוכן הלבן בתוכו
        shell.setBackground(Look.color(d, s.warn ? 0xF0CF73 : Look.BORDER));
        GridLayout sl = new GridLayout(1, false);
        sl.marginWidth = 1;
        sl.marginHeight = 1;
        shell.setLayout(sl);
        Composite body = new Composite(shell, SWT.NONE);
        body.setBackground(Look.color(d, s.warn ? 0xFFFBEB : Look.CARD));
        body.setBackgroundMode(SWT.INHERIT_DEFAULT);
        GridLayout bl = new GridLayout(1, false);
        bl.marginWidth = 14;
        bl.marginHeight = 12;
        bl.verticalSpacing = 6;
        body.setLayout(bl);
        body.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

        // שורה 1: הסמל, הכותרת המודגשת, ו-✕
        Composite head = row(body, 3);
        Canvas icon = new Canvas(head, SWT.NO_FOCUS | SWT.LEFT_TO_RIGHT | SWT.DOUBLE_BUFFERED);
        GridData ig = new GridData(SWT.BEGINNING, SWT.CENTER, false, false);
        ig.widthHint = 18;
        ig.heightHint = 18;
        icon.setLayoutData(ig);
        icon.addListener(SWT.Paint, e -> Say.icon(e.gc, d, s.warn ? Say.Kind.WARN : Say.Kind.DONE, 18));
        Label title = label(head, s.title, SWT.BOLD, 0, Look.TEXT);
        title.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        closeButton(head, this::close);

        // שורה 2: כמה סעיפים ואיפה הסמן · התיקייה (קטן, באפור)
        Composite sum = row(body, 2);
        label(sum, s.summary, SWT.NORMAL, -1, Look.INK2);
        if (!s.folder.isEmpty()) {
            Label f = new Label(sum, SWT.LEFT_TO_RIGHT);
            f.setText(s.folder);
            f.setFont(Look.mono(d, -2));
            f.setForeground(Look.color(d, 0x98A2B3));
            f.setLayoutData(new GridData(SWT.END, SWT.CENTER, true, false));
        }
        // שגיאות קומפילציה: "הצגה" — מעבר לשגיאה הראשונה
        if (!s.errorsLine.isEmpty()) {
            Composite er = row(body, 2);
            label(er, s.errorsLine, SWT.NORMAL, -1, Look.INK2);
            link(er, "הצגה", s.onErrors).setData(ID, "errors");
        }
        // סעיפים שעונים עליהם במילים: שורה אחת, רק התוויות, ו"הצגה"
        if (!s.words.isEmpty()) {
            Composite wr = row(body, 2);
            label(wr, s.words, SWT.NORMAL, -1, Look.INK2);
            link(wr, "הצגה", () -> popover(s.others)).setData(ID, "words");
        }
        // הצעות לפתרון
        if (s.solutions) {
            Composite so = row(body, 2);
            final Image bulb = SolutionDialog.image(d, "icons/solution.png");
            Label bi = new Label(so, SWT.NONE);
            if (bulb != null) {
                bi.setImage(bulb);
                bi.addListener(SWT.Dispose, e -> bulb.dispose());
            }
            label(so, "ליד כל סעיף — הצעה לפתרון", SWT.NORMAL, -1, Look.INK2);
        }
        // לחיצה בכל מקום (חוץ מהקישורים) — סוגרת
        clickToClose(body);

        shell.pack();
        Point size = shell.computeSize(WIDTH, SWT.DEFAULT);
        shell.setSize(WIDTH, size.y);
        follow = e -> place();
        parent.addListener(SWT.Move, follow);
        parent.addListener(SWT.Resize, follow);
        shell.addListener(SWT.Dispose, e -> {
            if (!parent.isDisposed()) {
                parent.removeListener(SWT.Move, follow);
                parent.removeListener(SWT.Resize, follow);
            }
            if (pop != null && !pop.isDisposed()) pop.dispose();
        });
        place();
        shell.setVisible(true);   // לא open(): בלי להפעיל את החלון ובלי לקחת את המיקוד מהעורך
        if (focus != null && !focus.isDisposed() && d.getFocusControl() != focus) focus.setFocus();
        d.timerExec(HIDE_MS, this::autoHide);
    }

    /** בפינה התחתונה של חלון Eclipse — בצד שמאל (כמו "בפינה" בממשק מימין לשמאל), מעל שורת המצב */
    private void place() {
        if (shell.isDisposed() || parent.isDisposed()) return;
        Rectangle ca = parent.getClientArea();
        Point o = parent.toDisplay(ca.x, ca.y);
        Point s = shell.getSize();
        shell.setLocation(o.x + 16, o.y + ca.height - s.y - 34);
        if (pop != null && !pop.isDisposed()) placePopover();
    }

    /** נעלמת לבד — אבל לא כשהעכבר עליה, ולא כשהחלונית של "הצגה" פתוחה */
    private void autoHide() {
        if (shell.isDisposed()) return;
        Point m = shell.getDisplay().getCursorLocation();
        boolean busy = shell.getBounds().contains(m) || pop != null && !pop.isDisposed();
        if (busy) shell.getDisplay().timerExec(2000, this::autoHide);
        else close();
    }

    void close() {
        if (!shell.isDisposed()) shell.dispose();
    }

    Shell shell() {
        return shell;
    }

    // ------------------------------------------------------------------ החלונית של "הצגה"

    /** הטקסט של הסעיפים שעונים עליהם במילים: מימין לשמאל, והקוד שבתוכם בגופן קוד ומשמאל לימין; גלילה כשארוך */
    void popover(List<String[]> others) {
        if (pop != null && !pop.isDisposed()) {
            pop.dispose();
            return;
        }
        final Display d = shell.getDisplay();
        pop = new Shell(parent, SWT.NO_TRIM | SWT.ON_TOP | SWT.TOOL | SWT.RIGHT_TO_LEFT);
        pop.setData(POPOVER, Boolean.TRUE);
        pop.setBackground(Look.color(d, Look.BORDER));
        GridLayout pl = new GridLayout(1, false);
        pl.marginWidth = 1;
        pl.marginHeight = 1;
        pop.setLayout(pl);
        Composite body = new Composite(pop, SWT.NONE);
        body.setBackground(Look.color(d, Look.CARD));
        body.setBackgroundMode(SWT.INHERIT_DEFAULT);
        GridLayout bl = new GridLayout(1, false);
        bl.marginWidth = 0;
        bl.marginHeight = 0;
        bl.verticalSpacing = 0;
        body.setLayout(bl);
        body.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

        Composite head = row(body, 2);
        ((GridLayout) head.getLayout()).marginWidth = 14;
        ((GridLayout) head.getLayout()).marginHeight = 10;
        Label t = label(head, AiScaffold.wordsLine(others), SWT.BOLD, 0, Look.TEXT);
        t.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        closeButton(head, () -> { if (pop != null && !pop.isDisposed()) pop.dispose(); });
        Label sep = new Label(body, SWT.NONE);
        sep.setBackground(Look.color(d, Look.BORDER));
        GridData sg = new GridData(SWT.FILL, SWT.TOP, true, false);
        sg.heightHint = 1;
        sep.setLayoutData(sg);

        final int w = 480;
        GridLayout cl = new GridLayout(1, false);
        cl.marginWidth = 14;
        cl.marginHeight = 12;
        cl.verticalSpacing = 8;
        Composite[] holder = new Composite[1];
        ScrolledComposite sc = Look.scroll(body, cl, holder);
        sc.setBackground(Look.color(d, Look.CARD));
        Composite content = holder[0];
        content.setBackground(Look.color(d, Look.CARD));
        for (String[] o : others) {
            String l = o[0] == null ? "" : o[0].trim().replaceAll("['׳]+$", "");
            if (!l.isEmpty()) label(content, "סעיף " + l, SWT.BOLD, -1, Look.TEXT);
            rich(content, o[1], w - 30);
        }
        Look.wheel(sc);
        Point need = content.computeSize(w, SWT.DEFAULT);
        int h = Math.min(need.y, 320);
        ((GridData) sc.getLayoutData()).heightHint = h;
        Point size = pop.computeSize(w, SWT.DEFAULT);
        pop.setSize(w, size.y);
        Look.updateScroll(sc);
        pop.addListener(SWT.Traverse, e -> { if (e.detail == SWT.TRAVERSE_ESCAPE) pop.dispose(); });
        placePopover();
        pop.setVisible(true);
    }

    private void placePopover() {
        Rectangle tb = shell.getBounds();
        Point s = pop.getSize();
        Rectangle area = parent.getBounds();
        int y = tb.y - s.y - 8;
        if (y < area.y + 40) y = Math.max(area.y + 40, tb.y + tb.height - s.y);
        pop.setLocation(tb.x, y);
    }

    /**
     * טקסט של סעיף: פסקאות בעברית (קוד קצר בתוך המשפט — משמאל לימין כיחידה אחת, RtlText), ושורות קוד ארוכות — בבלוק משלהן
     * בגופן קוד ומשמאל לימין ("לפניך קטע מפעולה ראשית: Button a1 = new Button(1, 12, "red"); ..." — הקוד יורד לבלוק, פקודה בשורה)
     */
    static void rich(Composite parent, String text, int width) {
        for (String[] b : blocks(text)) {
            Display d = parent.getDisplay();
            if (b[0].equals("p")) {
                Label l = new Label(parent, SWT.WRAP);
                l.setText(RtlText.rtl(b[1]));
                l.setForeground(Look.color(d, Look.INK2));
                GridData g = new GridData(SWT.FILL, SWT.TOP, true, false);
                g.widthHint = width;
                l.setLayoutData(g);
            } else {
                Composite box = Look.panel(parent, Look.SURFACE, Look.BORDER, 4, 10, 6);
                box.getParent().setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
                Label code = new Label(box, SWT.LEFT_TO_RIGHT | SWT.WRAP);
                code.setText(b[1]);
                code.setFont(Look.mono(d, -1));
                code.setForeground(Look.color(d, Look.TEXT));
                GridData g = new GridData(SWT.FILL, SWT.TOP, true, false);
                g.widthHint = width - 24;
                code.setLayoutData(g);
            }
        }
    }

    private static final Pattern HEBREW = Pattern.compile("[֐-׿]");
    private static final Pattern NON_HEBREW = Pattern.compile("[^֐-׿]+");
    private static final Pattern LEAD = Pattern.compile("^(\\s*[A-Za-z_][A-Za-z0-9_]*\\s*:)\\s*(.*)$", Pattern.DOTALL);

    /** החלוקה לפסקאות ({"p", טקסט}) ולבלוקים של קוד ({"code", שורות}) — בלי SWT (נבדק גם מחוץ ל-Eclipse) */
    static List<String[]> blocks(String text) {
        List<String[]> out = new ArrayList<String[]>();
        StringBuilder para = new StringBuilder(), code = new StringBuilder();
        for (String raw : (text == null ? "" : text.replace("\r", "")).split("\n")) {
            String line = raw.replaceAll("\\s+$", "");
            if (line.trim().isEmpty()) {
                flush(out, para, code);
                continue;
            }
            if (!HEBREW.matcher(line).find() && looksCode(line)) {   // שורה של קוד
                flushPara(out, para);
                if (code.length() > 0) code.append('\n');
                code.append(line);
                continue;
            }
            // שורה בעברית: קטע ארוך של קוד באמצע — יורד לבלוק משלו; קוד קצר נשאר במשפט
            Matcher m = NON_HEBREW.matcher(line);
            int last = 0;
            while (m.find()) {
                String span = m.group();
                String trimmed = span.trim().replaceAll("^[,.\\-–—]+\\s*", "");
                String lead = "";
                Matcher lm = LEAD.matcher(trimmed);
                if (lm.matches() && longCode(lm.group(2))) {   // "Program: Button a1 = ..." — "Program:" שייך למשפט
                    lead = lm.group(1);
                    trimmed = lm.group(2);
                }
                if (!longCode(trimmed)) continue;
                String before = line.substring(last, m.start()) + (lead.isEmpty() ? "" : " " + lead);
                appendPara(para, before);
                flushPara(out, para);
                flushCode(out, code);
                code.append(statements(trimmed.replaceAll("[\\s…]+$", "")));
                flushCode(out, code);
                last = m.end();
            }
            flushCode(out, code);
            appendPara(para, line.substring(last));
        }
        flush(out, para, code);
        return out;
    }

    /** קטע קוד שראוי לבלוק משלו: פקודה (;), סוגריים מסולסלים, השמה, או ארוך */
    static boolean longCode(String s) {
        String t = s.trim();
        if (t.length() < 12) return false;
        return t.contains(";") || t.contains("{") || t.contains("}") || t.matches(".*[^=!<>]=[^=].*") && t.length() > 14 || t.length() >= 40;
    }

    private static boolean looksCode(String line) {
        String t = line.trim();
        return t.matches(".*[;{}=()\\[\\]].*") || t.matches("(public|private|static|int|void|return|if|while|for|else)\\b.*");
    }

    /** כמה פקודות בשורה אחת — פקודה בכל שורה (לא בתוך סוגריים / מחרוזת: for (int i = 0; i < n; i++) נשאר שורה אחת) */
    static String statements(String code) {
        StringBuilder b = new StringBuilder();
        int depth = 0;
        char quote = 0;
        for (int i = 0; i < code.length(); i++) {
            char c = code.charAt(i);
            b.append(c);
            if (quote != 0) {
                if (c == '\\' && i + 1 < code.length()) b.append(code.charAt(++i));
                else if (c == quote) quote = 0;
                continue;
            }
            if (c == '"' || c == '\'') quote = c;
            else if (c == '(') depth++;
            else if (c == ')') depth = Math.max(0, depth - 1);
            else if ((c == ';' || c == '{' || c == '}') && depth == 0 && i + 1 < code.length()) {
                int j = i + 1;
                while (j < code.length() && code.charAt(j) == ' ') j++;
                if (j < code.length()) {
                    b.append('\n');
                    i = j - 1;
                }
            }
        }
        return b.toString();
    }

    private static void appendPara(StringBuilder para, String s) {
        if (s.trim().isEmpty()) return;
        if (para.length() > 0 && !Character.isWhitespace(para.charAt(para.length() - 1))) para.append(' ');
        para.append(s.trim());
    }

    private static void flush(List<String[]> out, StringBuilder para, StringBuilder code) {
        flushPara(out, para);
        flushCode(out, code);
    }

    private static void flushPara(List<String[]> out, StringBuilder para) {
        String p = para.toString().trim();
        if (!p.isEmpty() && !p.matches("[\\s.,:;…\\-–—]*")) out.add(new String[] { "p", p });
        para.setLength(0);
    }

    private static void flushCode(List<String[]> out, StringBuilder code) {
        if (code.length() > 0) out.add(new String[] { "code", code.toString() });
        code.setLength(0);
    }

    // ------------------------------------------------------------------ עזרים

    private static final Pattern ERROR_AT = Pattern.compile("^(.+?):(\\d+): ");

    /**
     * מעבר לשגיאת הקומפילציה הראשונה בקבצים של התרגיל — הראשונה ברשימה של SubmitHandler.Build ("Targil.java:12: ..."),
     * שכבר בלי "חסר return" בכותרות של הסעיפים (הפעולות ריקות בכוונה)
     */
    static void firstError(IWorkbenchPage page, List<IFile> files, List<String> errors) {
        IMarker best = null;
        Matcher at = errors.isEmpty() ? null : ERROR_AT.matcher(errors.get(0));
        if (at != null && at.find()) {
            for (IFile f : files) {
                if (!f.getName().equals(at.group(1))) continue;
                int line = Integer.parseInt(at.group(2));
                try {
                    for (IMarker m : f.findMarkers(IMarker.PROBLEM, true, IResource.DEPTH_ZERO))
                        if (m.getAttribute(IMarker.SEVERITY, 0) == IMarker.SEVERITY_ERROR && m.getAttribute(IMarker.LINE_NUMBER, 0) == line) { best = m; break; }
                    if (best == null) {
                        Ui.openAt(page, f, Math.max(0, line - 1));
                        return;
                    }
                } catch (Exception ignored) {
                    // נמשיך לשגיאה הראשונה שנמצא
                }
            }
        }
        for (IFile f : best != null ? new ArrayList<IFile>() : files) {
            try {
                for (IMarker m : f.findMarkers(IMarker.PROBLEM, true, IResource.DEPTH_ZERO)) {
                    if (m.getAttribute(IMarker.SEVERITY, 0) != IMarker.SEVERITY_ERROR) continue;
                    if (best == null || best.getResource().equals(m.getResource())
                            && m.getAttribute(IMarker.LINE_NUMBER, 0) < best.getAttribute(IMarker.LINE_NUMBER, 0)) best = m;
                }
            } catch (Exception ignored) {
                // קובץ שנמחק
            }
            if (best != null) break;
        }
        try {
            if (best != null) IDE.openEditor(page, best, true);
            else if (!files.isEmpty()) Ui.openAt(page, files.get(0), 0);
        } catch (Exception ignored) {
            // לא נפתח
        }
    }

    private static Composite row(Composite parent, int columns) {
        Composite r = new Composite(parent, SWT.NONE);
        GridLayout l = new GridLayout(columns, false);
        l.marginWidth = 0;
        l.marginHeight = 0;
        l.horizontalSpacing = 8;
        r.setLayout(l);
        r.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
        return r;
    }

    private static Label label(Composite parent, String text, int style, int delta, int color) {
        Label l = new Label(parent, SWT.NONE);
        l.setText(RtlText.rtl(text));
        l.setFont(Look.font(parent.getDisplay(), delta, style));
        l.setForeground(Look.color(parent.getDisplay(), color));
        return l;
    }

    private static Label link(Composite parent, String text, final Runnable action) {
        Display d = parent.getDisplay();
        final Label l = new Label(parent, SWT.NONE);
        l.setText(text);
        l.setFont(Look.font(d, -1, SWT.BOLD));
        l.setForeground(Look.color(d, Look.ACCENT_DARK));
        l.setCursor(d.getSystemCursor(SWT.CURSOR_HAND));
        l.setData("dsviz.link", Boolean.TRUE);
        l.addListener(SWT.MouseUp, e -> { if (action != null) action.run(); });
        l.addListener(SWT.MouseEnter, e -> l.setForeground(Look.color(d, Look.ACCENT)));
        l.addListener(SWT.MouseExit, e -> l.setForeground(Look.color(d, Look.ACCENT_DARK)));
        l.setLayoutData(new GridData(SWT.BEGINNING, SWT.CENTER, false, false));
        return l;
    }

    private static void closeButton(Composite parent, final Runnable action) {
        final Display d = parent.getDisplay();
        final Canvas x = new Canvas(parent, SWT.NO_FOCUS | SWT.DOUBLE_BUFFERED);
        x.setData("dsviz.link", Boolean.TRUE);
        x.setData(ID, "close");
        GridData g = new GridData(SWT.END, SWT.CENTER, false, false);
        g.widthHint = 16;
        g.heightHint = 16;
        x.setLayoutData(g);
        x.setCursor(d.getSystemCursor(SWT.CURSOR_HAND));
        final boolean[] hover = { false };
        x.addListener(SWT.Paint, e -> {
            e.gc.setAntialias(SWT.ON);
            e.gc.setForeground(Look.color(d, hover[0] ? Look.TEXT : 0x98A2B3));
            e.gc.setLineWidth(2);
            e.gc.setLineCap(SWT.CAP_ROUND);
            e.gc.drawLine(4, 4, 11, 11);
            e.gc.drawLine(11, 4, 4, 11);
        });
        x.addListener(SWT.MouseEnter, e -> { hover[0] = true; x.redraw(); });
        x.addListener(SWT.MouseExit, e -> { hover[0] = false; x.redraw(); });
        x.addListener(SWT.MouseUp, e -> action.run());
    }

    private void clickToClose(Control c) {
        if (c.getData("dsviz.link") != null) return;
        c.addListener(SWT.MouseUp, e -> close());
        if (c instanceof Composite) for (Control k : ((Composite) c).getChildren()) clickToClose(k);
    }
}
