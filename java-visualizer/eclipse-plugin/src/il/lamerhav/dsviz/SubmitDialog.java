package il.lamerhav.dsviz;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.ScrolledComposite;
import org.eclipse.swt.dnd.Clipboard;
import org.eclipse.swt.dnd.DND;
import org.eclipse.swt.dnd.DropTarget;
import org.eclipse.swt.dnd.DropTargetAdapter;
import org.eclipse.swt.dnd.DropTargetEvent;
import org.eclipse.swt.dnd.FileTransfer;
import org.eclipse.swt.dnd.ImageTransfer;
import org.eclipse.swt.dnd.TextTransfer;
import org.eclipse.swt.dnd.Transfer;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.graphics.ImageLoader;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.layout.RowData;
import org.eclipse.swt.layout.RowLayout;
import org.eclipse.swt.widgets.Canvas;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;

/**
 * חלון "שליחת תרגיל": מצרפים את דף התרגיל — PDF, Word, תמונות (גם כמה), או מדביקים צילום מסך עם Ctrl+V —
 * והוא נשלח לשרת (ומשם ל-Gemini). אחרי "שליחה" אותו חלון עובר לתצוגת הבנייה (כמו באתר): הדפים שצורפו, ורשימת השלבים
 * האמיתיים — מכין את הקבצים, שולח ל-Gemini, בונה, בודק שמתקמפל, בודק את ההצעות לפתרון (לפי ה-subTask של SubmitHandler.Build).
 * כשהכול מוכן החלון נסגר לבד, ו-SubmitHandler פותח את הקובץ. שגיאה — שורה אדומה אחת ו"נסו שוב" (הקבצים נשארים).
 */
public class SubmitDialog extends Dialog {
    private static final String NOT_CONFIGURED = "שליחת תרגיל עוד לא הוגדרה בהתקנה הזאת: חסרה הכתובת של השרת (worker/README.md). אפשר לצרף קבצים, אבל עוד אי אפשר לשלוח.";

    private final List<IJavaProject> projects;
    private final String newProjectName;
    private final IProject preferred;
    private final WorkerClient client;

    /** השלבים — אותם שלבים כמו באתר (site/index.html, ‎#exercise‎) */
    static final String[] STAGES = { "מכין את הקבצים", "שולח ל-Gemini", "בונה את התרגיל", "בודק שהקוד מתקמפל", "בודק את ההצעות לפתרון" };
    /** כמה זמן כל שלב לוקח בדרך כלל (שניות) ואיזה חלק מהפס הוא — רק בשביל הפס הדק למעלה */
    private static final double[] EXPECT = { 1, 25, 2, 6, 10 }, WEIGHT = { 0.04, 0.56, 0.08, 0.14, 0.18 };

    private final List<ExerciseFiles.Item> items = new ArrayList<ExerciseFiles.Item>();
    private Composite zone, tiles, empty, form, buildView, thumbs;
    private Label status, buildTitle, buildHint, buildError;
    private Canvas line;
    private Text noteText;
    private Combo projectCombo;
    private Look.Round ok, retry, cancelBtn, closeBtn;
    private ScrolledComposite scroll;
    private Listener pasteFilter;
    private int pasted;
    private volatile boolean sending, building;
    private boolean closeWhenStopped;
    private Point formSize;
    private Stages stages;
    private final Cancel monitor = new Cancel();

    private Map<String, Object> result;
    private IJavaProject resultProject;
    private SubmitHandler.Build build;

    public SubmitDialog(Shell parent, List<IJavaProject> projects, String newProjectName, IProject preferred, WorkerClient client) {
        super(parent);
        this.projects = projects;
        this.newProjectName = newProjectName;
        this.preferred = preferred;
        this.client = client;
        setShellStyle(SWT.DIALOG_TRIM | SWT.RESIZE | SWT.RIGHT_TO_LEFT | SWT.APPLICATION_MODAL);
    }

    @Override
    protected void configureShell(Shell shell) {
        super.configureShell(shell);
        shell.setText("שליחת תרגיל");
        shell.setBackground(Look.color(shell.getDisplay(), Look.BG));
        shell.setBackgroundMode(SWT.INHERIT_DEFAULT);
    }

    @Override
    protected Control createDialogArea(Composite parent) {
        Composite area = (Composite) super.createDialogArea(parent);
        Display d = area.getDisplay();
        GridLayout al = (GridLayout) area.getLayout();
        al.marginWidth = 16;
        al.marginHeight = 8;
        al.verticalSpacing = 4;
        area.setBackground(Look.color(d, Look.BG));
        area.setBackgroundMode(SWT.INHERIT_DEFAULT);
        form = new Composite(area, SWT.NONE);
        GridLayout fl = new GridLayout(1, false);
        fl.marginWidth = 0;
        fl.marginHeight = 0;
        fl.verticalSpacing = 4;
        form.setLayout(fl);
        form.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        Look.header(form, "שליחת תרגיל", "מצרפים את דף התרגיל — ומקבלים קובץ מוכן: המחלקות, main, ופעולה לכל סעיף עם מקום לכתוב את הפתרון.");

        GridLayout cl = new GridLayout(1, false);
        cl.marginWidth = 0;
        cl.marginHeight = 0;
        cl.verticalSpacing = 10;
        Composite[] holder = new Composite[1];
        scroll = Look.scroll(form, cl, holder);
        Composite col = holder[0];

        // --- דף התרגיל
        Composite card = Look.card(col, new GridData(SWT.FILL, SWT.FILL, true, true));
        Look.section(card, "דף התרגיל", 0);
        zone = new Composite(card, SWT.NONE);
        GridLayout zl = new GridLayout(1, false);
        zl.marginWidth = 14;
        zl.marginHeight = 12;
        zl.verticalSpacing = 8;
        zone.setLayout(zl);
        GridData zg = new GridData(SWT.FILL, SWT.FILL, true, true);
        zg.widthHint = 620;
        zg.minimumHeight = 150;
        zone.setLayoutData(zg);
        zone.setBackgroundMode(SWT.INHERIT_FORCE);   // מה שבתוך האזור — על הרקע שלו
        zone.addListener(SWT.Paint, e -> paintZone(e.gc));
        zone.addListener(SWT.Resize, e -> zone.redraw());

        empty = new Composite(zone, SWT.NONE);
        GridLayout el = new GridLayout(1, false);
        el.marginWidth = 0;
        el.marginHeight = 6;
        empty.setLayout(el);
        empty.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, true));
        Label big = new Label(empty, SWT.CENTER);
        big.setText("גררו לכאן את דף התרגיל, או בחרו קבצים");
        big.setFont(Look.font(d, 2, SWT.BOLD));
        big.setData("dsviz.keepFont", Boolean.TRUE);
        big.setForeground(Look.color(d, Look.TEXT));
        big.setLayoutData(new GridData(SWT.CENTER, SWT.CENTER, true, false));
        Label small = new Label(empty, SWT.CENTER | SWT.WRAP);
        small.setText("PDF, Word או תמונות. אפשר גם לצלם את המסך (Win+Shift+S) ולהדביק כאן עם Ctrl+V — כמה תמונות שצריך.");
        small.setForeground(Look.color(d, Look.MUTED));
        GridData sg = new GridData(SWT.CENTER, SWT.CENTER, true, false);
        sg.widthHint = 520;
        small.setLayoutData(sg);

        tiles = new Composite(zone, SWT.NONE);
        RowLayout rl = new RowLayout(SWT.HORIZONTAL);
        rl.wrap = true;
        rl.spacing = 10;
        rl.marginWidth = 0;
        rl.marginHeight = 0;
        tiles.setLayout(rl);
        tiles.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));

        Composite buttons = new Composite(zone, SWT.NONE);
        GridLayout bl = new GridLayout(2, false);
        bl.marginWidth = 0;
        bl.marginHeight = 0;
        bl.horizontalSpacing = 10;
        buttons.setLayout(bl);
        buttons.setLayoutData(new GridData(SWT.CENTER, SWT.BOTTOM, true, false));
        new Look.Round(buttons, "בחירת קבצים…", false, this::chooseFiles);
        new Look.Round(buttons, "הדבקה (Ctrl+V)", false, this::paste);

        status = new Label(card, SWT.WRAP);
        GridData stg = new GridData(SWT.FILL, SWT.TOP, true, false);
        stg.widthHint = 600;
        status.setLayoutData(stg);
        status.setForeground(Look.color(d, Look.MUTED));

        // --- פרטים
        Composite more = Look.card(col, new GridData(SWT.FILL, SWT.TOP, true, false));
        Look.section(more, "הערה (לא חובה)", 0);
        Label hint = new Label(more, SWT.WRAP);
        hint.setText("למשל: \"רק שאלה 2\", או: \"הפעולה בסעיף ב צריכה להיות רקורסיבית\"");
        hint.setForeground(Look.color(d, Look.MUTED));
        noteText = new Text(more, SWT.BORDER | SWT.MULTI | SWT.WRAP | SWT.V_SCROLL);
        GridData ng = new GridData(SWT.FILL, SWT.TOP, true, false);
        ng.heightHint = 40;
        noteText.setLayoutData(ng);
        noteText.setTextLimit(500);

        Composite row = new Composite(more, SWT.NONE);
        GridLayout rwl = new GridLayout(2, false);
        rwl.marginWidth = 0;
        rwl.marginHeight = 0;
        rwl.marginTop = 6;
        row.setLayout(rwl);
        row.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
        new Label(row, SWT.NONE).setText("פרויקט:");
        projectCombo = new Combo(row, SWT.READ_ONLY | SWT.LEFT_TO_RIGHT);
        GridData pg = new GridData(SWT.FILL, SWT.CENTER, true, false);
        pg.widthHint = 220;
        projectCombo.setLayoutData(pg);
        int sel = -1;
        for (int i = 0; i < projects.size(); i++) {
            IProject p = projects.get(i).getProject();
            projectCombo.add(p.getName());
            if (p.equals(preferred) || (sel < 0 && p.getName().equals(ProjectSetup.DEFAULT_PROJECT))) sel = i;
        }
        projectCombo.add("פרויקט חדש: " + newProjectName);
        projectCombo.select(Math.max(sel, 0));

        Label privacy = new Label(more, SWT.WRAP);
        privacy.setText("הקבצים נשלחים לשרת של DSViz ומשם ל-Gemini (של Google) כדי לזהות את התרגיל. לא לשלוח דפים עם פרטים אישיים.");
        privacy.setForeground(Look.color(d, Look.MUTED));
        GridData prg = new GridData(SWT.FILL, SWT.TOP, true, false);
        prg.widthHint = 600;
        prg.verticalIndent = 6;
        privacy.setLayoutData(prg);

        // --- תצוגת הבנייה (אחרי "שליחה"): באותו חלון
        createBuildView(area);
        show(buildView, false);

        // גרירת קבצים / תמונות לכל החלון
        for (Control c : new Control[] { zone, tiles, empty, big, small, card }) dropTarget(c);
        // Ctrl+V בכל מקום בחלון (חוץ משדה ההערה — שם זו הדבקה רגילה)
        pasteFilter = e -> {
            if (getShell() == null || getShell().isDisposed() || e.display.getActiveShell() != getShell()) return;
            if (e.widget == noteText) return;
            boolean ctrlV = (e.stateMask & SWT.MOD1) != 0 && (e.keyCode == 'v' || e.keyCode == 'V' || e.character == 0x16);
            boolean shiftIns = (e.stateMask & SWT.SHIFT) != 0 && e.keyCode == SWT.INSERT;
            if (ctrlV || shiftIns) {
                e.doit = false;
                paste();
            }
        };
        d.addFilter(SWT.KeyDown, pasteFilter);
        area.addListener(SWT.Dispose, e -> d.removeFilter(SWT.KeyDown, pasteFilter));

        Look.applyFont(area, -1);
        Look.wheel(scroll);
        refresh();
        return area;
    }

    @Override
    protected Control createButtonBar(Composite parent) {
        Display d = parent.getDisplay();
        Composite bar = new Composite(parent, SWT.NONE);
        bar.setBackground(Look.color(d, Look.BG));
        GridLayout l = new GridLayout(5, false);
        l.marginWidth = 20;
        l.marginHeight = 10;
        l.horizontalSpacing = 10;
        bar.setLayout(l);
        bar.setLayoutData(new GridData(SWT.FILL, SWT.BOTTOM, true, false));
        ok = new Look.Round(bar, "שליחה ויצירת התרגיל", true, this::send);
        retry = new Look.Round(bar, "נסו שוב", true, this::backToForm);
        cancelBtn = new Look.Round(bar, "ביטול", false, this::cancel);
        closeBtn = new Look.Round(bar, "סגירה", false, this::cancelPressed);
        for (Look.Round r : new Look.Round[] { ok, retry, cancelBtn, closeBtn }) r.setLayoutData(new GridData());
        Label c = new Label(bar, SWT.LEFT_TO_RIGHT);   // זכויות יוצרים — בקצה השני של שורת הכפתורים
        c.setText(Look.COPYRIGHT);
        c.setFont(Look.font(d, -2, SWT.NORMAL));
        c.setData("dsviz.keepFont", Boolean.TRUE);
        c.setForeground(Look.color(d, 0xA0A8B8));
        c.setLayoutData(new GridData(SWT.END, SWT.CENTER, true, false));
        buttons("form");
        refresh();
        return bar;
    }

    /** אילו כפתורים מוצגים: form — שליחה + ביטול; build — רק ביטול; error — נסו שוב + סגירה */
    private void buttons(String mode) {
        show(ok, mode.equals("form"));
        show(retry, mode.equals("error"));
        show(cancelBtn, !mode.equals("error"));
        show(closeBtn, mode.equals("error"));
        ok.getParent().layout(true, true);
    }

    @Override
    protected Point getInitialSize() {
        Point p = super.getInitialSize();
        Rectangle screen = getShell().getMonitor().getClientArea();
        return new Point(Math.min(p.x, screen.width - 40), Math.min(p.y, screen.height - 60));
    }

    @Override
    protected void handleShellCloseEvent() {
        if (sending || building) {   // באמצע: עוצרים, והחלון נסגר כשהעבודה ברקע באמת נעצרה (בלי קבצים שנכתבים אחרי שנסגר)
            closeWhenStopped = true;
            stop();
            return;
        }
        super.handleShellCloseEvent();
    }

    // ------------------------------------------------------------------ קבצים

    private void chooseFiles() {
        if (sending) return;
        FileDialog fd = new FileDialog(getShell(), SWT.OPEN | SWT.MULTI);
        fd.setText("בחירת דף התרגיל");
        fd.setFilterNames(new String[] { "דף התרגיל (PDF, Word, תמונות)", "כל הקבצים" });
        fd.setFilterExtensions(new String[] { "*.pdf;*.docx;*.png;*.jpg;*.jpeg;*.gif;*.bmp;*.webp;*.txt", "*.*" });
        if (fd.open() == null) return;
        List<File> files = new ArrayList<File>();
        for (String n : fd.getFileNames()) files.add(new File(fd.getFilterPath(), n));
        addFiles(files);
    }

    void addFiles(List<File> files) {
        StringBuilder errors = new StringBuilder();
        for (File f : files) {
            if (f.isDirectory()) continue;
            try {
                add(ExerciseFiles.fromFile(f));
            } catch (ExerciseFiles.UserError e) {
                errors.append(e.getMessage()).append('\n');
            }
        }
        refresh();
        if (errors.length() > 0) status(errors.toString().trim(), true);
    }

    /** הדבקה מהלוח: תמונה (צילום מסך), קבצים שהועתקו, או טקסט של התרגיל */
    void paste() {
        if (sending) return;
        Clipboard cb = new Clipboard(getShell().getDisplay());
        try {
            ImageData img = (ImageData) cb.getContents(ImageTransfer.getInstance());
            if (img != null) {
                addImage(img);
                return;
            }
            String[] paths = (String[]) cb.getContents(FileTransfer.getInstance());
            if (paths != null && paths.length > 0) {
                List<File> files = new ArrayList<File>();
                for (String p : paths) files.add(new File(p));
                addFiles(files);
                return;
            }
            String text = (String) cb.getContents(TextTransfer.getInstance());
            if (text != null && text.trim().length() >= 10) {
                try {
                    add(ExerciseFiles.fromPastedText(text, ++pasted));
                    refresh();
                } catch (ExerciseFiles.UserError e) {
                    status(e.getMessage(), true);
                }
                return;
            }
            status("בלוח אין תמונה. צלמו את התרגיל (Win+Shift+S, או PrtScn) ואז לחצו כאן Ctrl+V.", true);
        } finally {
            cb.dispose();
        }
    }

    void addImage(ImageData img) {
        try {
            ImageLoader l = new ImageLoader();
            l.data = new ImageData[] { img };
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            l.save(out, SWT.IMAGE_PNG);
            add(ExerciseFiles.fromPastedImage(out.toByteArray(), ++pasted));
            refresh();
        } catch (ExerciseFiles.UserError e) {
            status(e.getMessage(), true);
        } catch (Exception e) {
            status("לא הצלחתי לקרוא את התמונה מהלוח: " + e.getMessage(), true);
        }
    }

    private void add(ExerciseFiles.Item it) throws ExerciseFiles.UserError {
        int files = 0;
        for (ExerciseFiles.Item x : items) for (ExerciseFiles.Part p : x.parts) if (p.data != null) files++;
        for (ExerciseFiles.Part p : it.parts) if (p.data != null) files++;
        if (files > ExerciseFiles.MAX_ITEMS) throw new ExerciseFiles.UserError("אפשר לצרף עד " + ExerciseFiles.MAX_ITEMS + " קבצים ותמונות ביחד.");
        items.add(it);
    }

    private void dropTarget(Control c) {
        DropTarget dt = new DropTarget(c, DND.DROP_COPY | DND.DROP_DEFAULT | DND.DROP_MOVE | DND.DROP_LINK);
        dt.setTransfer(new Transfer[] { FileTransfer.getInstance(), ImageTransfer.getInstance() });
        dt.addDropListener(new DropTargetAdapter() {
            @Override
            public void dragEnter(DropTargetEvent e) {
                if (e.detail == DND.DROP_DEFAULT || e.detail == DND.DROP_MOVE) e.detail = DND.DROP_COPY;
            }

            @Override
            public void drop(DropTargetEvent e) {
                if (sending) return;
                if (e.data instanceof String[]) {
                    List<File> files = new ArrayList<File>();
                    for (String p : (String[]) e.data) files.add(new File(p));
                    addFiles(files);
                } else if (e.data instanceof ImageData) addImage((ImageData) e.data);
            }
        });
    }

    // ------------------------------------------------------------------ תצוגה

    private void refresh() {
        if (tiles == null || tiles.isDisposed()) return;
        for (Control c : tiles.getChildren()) c.dispose();
        for (ExerciseFiles.Item it : items) new Tile(tiles, it);
        boolean has = !items.isEmpty();
        show(empty, !has);
        show(tiles, has);
        if (has) {
            long size = 0;
            int images = 0, others = 0;
            for (ExerciseFiles.Item it : items) {
                size += it.size();
                if (it.kind.equals("image")) images++; else others++;
            }
            StringBuilder s = new StringBuilder();
            if (others > 0) s.append(others == 1 ? "קובץ אחד" : others + " קבצים");
            if (images > 0) s.append(s.length() > 0 ? " ו-" : "").append(images == 1 ? "תמונה אחת" : images + " תמונות");
            s.append(" · ").append(size < 1048576 ? Math.max(1, size / 1024) + "KB" : String.format("%.1fMB", size / 1048576.0))
             .append("  ·  אפשר להוסיף עוד, או להסיר עם ✕");
            status(s.toString(), false);
        } else if (status != null && !status.isDisposed()) status("", false);
        if (client == null) status(NOT_CONFIGURED, true);
        if (ok != null && !ok.isDisposed()) ok.setEnabled(has && !sending && client != null);
        Look.applyFont(tiles, -1);
        zone.layout(true, true);
        zone.getParent().layout(true, true);
        zone.setBackground(Look.color(zone.getDisplay(), has ? Look.CARD : 0xF7F9FD));
        Look.updateScroll(scroll);
        zone.redraw();
        if (getShell() != null && getShell().isVisible()) Look.fit(getShell(), true);   // החלון גדל כשמוסיפים קבצים (עד גודל המסך)
    }

    private static void show(Control c, boolean on) {
        c.setVisible(on);
        ((GridData) c.getLayoutData()).exclude = !on;
    }

    private void status(String text, boolean error) {
        if (status == null || status.isDisposed()) return;
        status.setText(text);
        status.setForeground(error ? status.getDisplay().getSystemColor(SWT.COLOR_RED) : Look.color(status.getDisplay(), Look.MUTED));
        status.getParent().layout(true, true);
        Look.updateScroll(scroll);
    }

    private void paintZone(GC gc) {
        Point s = zone.getSize();
        gc.setAntialias(SWT.ON);
        gc.setBackground(Look.color(zone.getDisplay(), items.isEmpty() ? 0xF7F9FD : Look.CARD));
        gc.fillRoundRectangle(0, 0, s.x - 1, s.y - 1, 18, 18);
        gc.setForeground(Look.color(zone.getDisplay(), items.isEmpty() ? 0x9DB4E6 : Look.BORDER));
        gc.setLineStyle(SWT.LINE_DASH);
        gc.setLineWidth(2);
        gc.drawRoundRectangle(1, 1, s.x - 3, s.y - 3, 18, 18);
        gc.setLineStyle(SWT.LINE_SOLID);
        gc.setLineWidth(1);
    }

    /** כרטיס קטן לכל קובץ: תמונה ממוזערת או סוג הקובץ, השם, ו-✕ להסרה */
    private final class Tile extends Canvas {
        static final int W = 120, H = 112, TH = 72;
        private final ExerciseFiles.Item item;
        private Image thumb;
        private boolean hoverX;

        Tile(Composite parent, ExerciseFiles.Item item) {
            super(parent, SWT.DOUBLE_BUFFERED | SWT.NO_FOCUS);
            this.item = item;
            setLayoutData(new RowData(W, H));
            setToolTipText(item.name);
            byte[] prev = item.kind.equals("image") ? item.preview() : null;
            if (prev != null) {
                try {
                    ImageData id = new ImageLoader().load(new java.io.ByteArrayInputStream(prev))[0];
                    double k = Math.min((W - 12.0) / id.width, (TH - 8.0) / id.height);
                    int w = Math.max(1, (int) (id.width * k)), h = Math.max(1, (int) (id.height * k));
                    Image full = new Image(getDisplay(), id);
                    thumb = new Image(getDisplay(), w, h);
                    GC g = new GC(thumb);
                    g.setAntialias(SWT.ON);
                    g.setInterpolation(SWT.HIGH);
                    g.drawImage(full, 0, 0, id.width, id.height, 0, 0, w, h);
                    g.dispose();
                    full.dispose();
                } catch (Exception ignored) {
                    thumb = null;
                }
            }
            addListener(SWT.Dispose, e -> { if (thumb != null) thumb.dispose(); });
            addListener(SWT.Paint, e -> paint(e.gc));
            addListener(SWT.MouseMove, e -> {
                boolean h = inX(e.x, e.y);
                if (h != hoverX) { hoverX = h; setCursor(h ? getDisplay().getSystemCursor(SWT.CURSOR_HAND) : null); redraw(); }
            });
            addListener(SWT.MouseExit, e -> { hoverX = false; redraw(); });
            addListener(SWT.MouseUp, e -> {
                if (!sending && inX(e.x, e.y)) {
                    items.remove(item);
                    getDisplay().asyncExec(SubmitDialog.this::refresh);
                }
            });
        }

        private boolean inX(int x, int y) {
            return x >= 2 && x <= 24 && y >= 2 && y <= 24;
        }

        private void paint(GC gc) {
            Display d = getDisplay();
            gc.setAntialias(SWT.ON);
            gc.setBackground(getParent().getBackground());
            gc.fillRectangle(0, 0, W, H);
            gc.setBackground(Look.color(d, Look.CARD));
            gc.fillRoundRectangle(0, 0, W - 1, H - 1, 14, 14);
            gc.setForeground(Look.color(d, Look.BORDER));
            gc.drawRoundRectangle(0, 0, W - 1, H - 1, 14, 14);
            if (thumb != null) {
                Rectangle b = thumb.getBounds();
                gc.drawImage(thumb, (W - b.width) / 2, 4 + (TH - 4 - b.height) / 2);
            } else {
                int color = item.kind.equals("pdf") ? 0xE5484D : item.kind.equals("word") ? 0x2F6FED : item.kind.equals("text") ? 0x667085 : 0x7C4DFF;
                String label = item.kind.equals("pdf") ? "PDF" : item.kind.equals("word") ? "Word" : item.kind.equals("text") ? "טקסט" : "תמונה";
                gc.setBackground(Look.color(d, color));
                gc.fillRoundRectangle(W / 2 - 30, 14, 60, 46, 10, 10);
                gc.setForeground(d.getSystemColor(SWT.COLOR_WHITE));
                gc.setFont(Look.font(d, 1, SWT.BOLD));
                Point e = gc.textExtent(label);
                gc.drawString(label, W / 2 - e.x / 2, 37 - e.y / 2, true);
            }
            gc.setFont(Look.font(d, -1, SWT.NORMAL));
            gc.setForeground(Look.color(d, Look.TEXT));
            String n = item.name;
            Point e = gc.textExtent(n);
            while (e.x > W - 10 && n.length() > 3) { n = n.substring(0, n.length() - 2) + "…"; e = gc.textExtent(n); }
            gc.drawString(n, (W - e.x) / 2, TH + 12, true);
            // ✕
            gc.setBackground(Look.color(d, hoverX ? 0xE5484D : 0x98A2B3));
            gc.fillOval(4, 4, 18, 18);
            gc.setForeground(d.getSystemColor(SWT.COLOR_WHITE));
            gc.setLineWidth(2);
            gc.drawLine(9, 9, 17, 17);
            gc.drawLine(17, 9, 9, 17);
            gc.setLineWidth(1);
        }
    }

    // ------------------------------------------------------------------ שליחה ובנייה

    private void send() {
        if (sending || building) return;
        try {
            ExerciseFiles.checkAll(items);
        } catch (ExerciseFiles.UserError e) {
            status(e.getMessage(), true);
            return;
        }
        if (client == null) {
            status(NOT_CONFIGURED, true);
            return;
        }
        sending = true;
        closeWhenStopped = false;
        monitor.setCanceled(false);
        status("", false);
        showBuildView();
        stages.set(0);
        final List<ExerciseFiles.Item> copy = new ArrayList<ExerciseFiles.Item>(items);
        final String note = noteText.getText();
        final IJavaProject project = selectedProject();
        final Display d = getShell().getDisplay();
        client.onSending = () -> d.asyncExec(() -> { if (stages != null) stages.set(1); });   // הקבצים מוכנים — יוצא לשרת
        Thread t = new Thread(() -> {
            Map<String, Object> r = null;
            String err = null;
            boolean cancelled = false;
            try {
                r = client.exercise(copy, note, "java");   // ב-Eclipse — תמיד Java
            } catch (WorkerClient.AiError e) {
                cancelled = "cancel".equals(e.code);
                err = e.getMessage();
            }
            final Map<String, Object> fr = r;
            final String ferr = err;
            final boolean fc = cancelled;
            d.asyncExec(() -> sent(fr, ferr, fc, project));
        }, "dsviz-submit");
        t.setDaemon(true);
        t.start();
    }

    /** התשובה מהשרת הגיעה (או שגיאה): בונים את הקבצים — באותו חלון, ברקע */
    private void sent(Map<String, Object> raw, String err, boolean cancelled, IJavaProject project) {
        sending = false;
        client.onSending = null;
        if (getShell() == null || getShell().isDisposed()) return;
        if (raw == null) {
            if (closeWhenStopped) { cancelPressed(); return; }
            if (cancelled) {   // "ביטול" בזמן השליחה — חוזרים לטופס, עם הקבצים
                backToForm();
                status("השליחה בוטלה.", false);
            } else failed(err);
            return;
        }
        if (monitor.isCanceled()) {   // התשובה הגיעה, אבל כבר ביקשו לעצור
            if (closeWhenStopped) cancelPressed();
            else {
                backToForm();
                status("השליחה בוטלה.", false);
            }
            return;
        }
        result = raw;
        resultProject = project;
        stages.set(2);
        try {
            IJavaProject jp = project != null ? project : ProjectSetup.createProject(newProjectName);
            IContainer src = ProjectSetup.prepare(jp, Ui.resolver(getShell()));
            build = new SubmitHandler.Build(client, raw, sourceNames(), jp, src);
        } catch (CoreException e) {
            failed("לא הצלחתי להכין את הפרויקט: " + e.getStatus().getMessage());
            return;
        }
        building = true;
        final SubmitHandler.Build b = build;
        final Display d = getShell().getDisplay();
        Thread t = new Thread(() -> {
            String error = null;
            boolean stopped = false;
            try {
                b.run(monitor);
            } catch (java.lang.reflect.InvocationTargetException e) {
                Throwable c = e.getCause();
                error = c != null && c.getMessage() != null ? c.getMessage() : e.getMessage();
            } catch (InterruptedException | OperationCanceledException e) {
                stopped = true;
            } catch (RuntimeException e) {
                error = e.toString();
            }
            final String fe = error;
            final boolean fs = stopped || monitor.isCanceled();
            d.asyncExec(() -> built(fe, fs));
        }, "dsviz-build");
        t.setDaemon(true);
        t.start();
    }

    /** הבנייה נגמרה: הכול ירוק, והחלון נסגר לבד (SubmitHandler פותח את הקובץ ומציג את ההודעה) */
    private void built(String err, boolean stopped) {
        building = false;
        if (getShell() == null || getShell().isDisposed()) return;
        if (stopped || closeWhenStopped) {   // בוטל באמצע — SubmitHandler פותח את מה שכבר נוצר
            setReturnCode(CANCEL);
            close();
            return;
        }
        if (err != null) {
            failed("לא הצלחתי ליצור את התרגיל: " + err);
            return;
        }
        stages.finish();
        cancelBtn.setEnabled(false);
        getShell().getDisplay().timerExec(450, () -> {
            if (getShell() == null || getShell().isDisposed()) return;
            setReturnCode(OK);
            close();
        });
    }

    /** שגיאה: שורה אדומה אחת, והכפתור "נסו שוב" (שחוזר לטופס עם אותם קבצים) */
    private void failed(String err) {
        sending = false;
        building = false;
        build = null;
        stages.fail();
        buildTitle.setText("התרגיל לא נוצר");
        show(buildHint, false);
        buildError.setText(RtlText.rtl(err == null || err.isEmpty() ? "משהו השתבש. נסו שוב." : err));
        show(buildError, true);
        buttons("error");
        fitHeight();
    }

    private void cancel() {
        if (sending || building) {
            stop();
            return;
        }
        cancelPressed();
    }

    /** עוצר את מה שרץ: השליחה (או בקשת התיקון), והבנייה בנקודה הבאה שבודקת */
    private void stop() {
        monitor.setCanceled(true);
        if (client != null) client.cancel();
        if (cancelBtn != null && !cancelBtn.isDisposed()) cancelBtn.setEnabled(false);
    }

    /** חזרה מתצוגת הבנייה לטופס — עם כל הקבצים שצורפו */
    private void backToForm() {
        sending = false;
        building = false;
        build = null;
        show(buildView, false);
        show(form, true);
        cancelBtn.setEnabled(true);
        buttons("form");
        refresh();
        if (formSize != null) getShell().setSize(formSize);
        getShell().layout(true, true);
    }

    // ------------------------------------------------------------------ תצוגת הבנייה

    private void createBuildView(Composite area) {
        Display d = area.getDisplay();
        buildView = new Composite(area, SWT.NONE);
        GridLayout bl = new GridLayout(1, false);
        bl.marginWidth = 0;
        bl.marginHeight = 0;
        bl.verticalSpacing = 12;
        buildView.setLayout(bl);
        GridData bg = new GridData(SWT.FILL, SWT.FILL, true, true);
        bg.widthHint = 560;
        buildView.setLayoutData(bg);
        buildView.setBackground(Look.color(d, Look.BG));

        // פס דק בצבע ההדגשה — כמה מהעבודה כבר נעשתה (מימין לשמאל)
        line = new Canvas(buildView, SWT.NO_FOCUS | SWT.DOUBLE_BUFFERED);
        GridData lg = new GridData(SWT.FILL, SWT.TOP, true, false);
        lg.heightHint = 3;
        line.setLayoutData(lg);
        line.addListener(SWT.Paint, e -> {
            Point s = line.getSize();
            e.gc.setBackground(Look.color(d, 0xE3E7EE));
            e.gc.fillRectangle(0, 0, s.x, s.y);
            e.gc.setBackground(Look.color(d, stages != null && stages.failed ? Look.ERR : Look.ACCENT));
            e.gc.fillRectangle(0, 0, (int) Math.round(s.x * (stages == null ? 0 : stages.fraction())), s.y);
        });

        Composite head = new Composite(buildView, SWT.NONE);
        GridLayout hl = new GridLayout(1, false);
        hl.marginWidth = 0;
        hl.marginHeight = 0;
        hl.verticalSpacing = 2;
        head.setLayout(hl);
        head.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
        buildTitle = new Label(head, SWT.NONE);
        buildTitle.setText("בונה את התרגיל");
        buildTitle.setFont(Look.font(d, 3, SWT.BOLD));
        buildTitle.setData("dsviz.keepFont", Boolean.TRUE);
        buildTitle.setForeground(Look.color(d, Look.TEXT));
        buildTitle.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
        buildHint = new Label(head, SWT.NONE);
        buildHint.setText("בדרך כלל עד דקה.");
        buildHint.setForeground(Look.color(d, Look.MUTED));
        buildHint.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));

        thumbs = new Composite(buildView, SWT.NONE);
        RowLayout rl = new RowLayout(SWT.HORIZONTAL);
        rl.wrap = true;
        rl.spacing = 8;
        rl.marginWidth = 0;
        rl.marginHeight = 0;
        thumbs.setLayout(rl);
        thumbs.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));

        Composite panel = Look.panel(buildView, Look.CARD, Look.BORDER, 8, 16, 12);
        panel.getParent().setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
        stages = new Stages(panel);

        buildError = new Label(buildView, SWT.WRAP);
        buildError.setForeground(Look.color(d, Look.ERR));
        GridData eg = new GridData(SWT.FILL, SWT.TOP, true, false);
        eg.widthHint = 540;
        buildError.setLayoutData(eg);
        show(buildError, false);
    }

    private void showBuildView() {
        formSize = getShell().getSize();
        for (Control c : thumbs.getChildren()) c.dispose();
        for (ExerciseFiles.Item it : items) new Thumb(thumbs, it);
        stages.reset();
        buildTitle.setText("בונה את התרגיל");
        show(buildHint, true);
        show(buildError, false);
        show(form, false);
        show(buildView, true);
        cancelBtn.setEnabled(true);
        buttons("build");
        Look.applyFont(buildView, -1);
        fitHeight();
        tick();
    }

    /** גובה החלון לפי התצוגה (הרוחב נשאר) */
    private void fitHeight() {
        Shell sh = getShell();
        sh.layout(true, true);
        Point need = sh.computeSize(sh.getSize().x, SWT.DEFAULT, true);
        Rectangle screen = sh.getMonitor().getClientArea();
        sh.setSize(sh.getSize().x, Math.min(need.y, screen.height - 60));
        sh.layout(true, true);
    }

    /** כל רבע שנייה, כל עוד עובדים: השניות של השלב הנוכחי והפס הדק */
    private void tick() {
        if (!(sending || building) || stages == null || line.isDisposed()) return;
        stages.update();
        line.redraw();
        line.getDisplay().timerExec(250, this::tick);
    }

    /** השלבים מ-subTask / beginTask של SubmitHandler.Build (ומבקשות התיקון — שורה קטנה באפור) */
    void progress(String text) {
        if (text == null || stages == null) return;
        String t = text.trim();
        if (t.isEmpty()) return;
        if (t.startsWith("בונה את התרגיל")) stages.set(2);
        else if (t.startsWith("בודק שהקוד מתקמפל")) stages.set(3);
        else if (t.startsWith("בודק שההצעות לפתרון")) stages.set(4);
        else if (t.contains("מבקש תיקון")) stages.sub("יש מה לתקן, מבקש תיקון…");
        else if (t.startsWith("בודק שוב")) stages.sub("בודק שוב…");
    }

    /** מצב השלבים כטקסט (לבדיקות): "done done now pending pending" */
    String stageReport() {
        return stages == null ? "" : stages.report();
    }

    /** ההתקדמות של הבנייה — מעבירה את ה-subTask לחלון, ו"ביטול" עוצר אותה */
    private final class Cancel extends NullProgressMonitor {
        @Override
        public void beginTask(String name, int totalWork) {
            post(name);
        }

        @Override
        public void subTask(String name) {
            post(name);
        }

        @Override
        public void setTaskName(String name) {
            post(name);
        }

        private void post(final String text) {
            Shell sh = getShell();
            if (sh == null || sh.isDisposed() || text == null) return;
            sh.getDisplay().asyncExec(() -> progress(text));
        }
    }

    /** רשימת השלבים: בוצע — ✓ ירוק; עכשיו — נקודה כחולה, מודגש, והשניות; עוד לא — אפור */
    private final class Stages {
        final int[] state = new int[STAGES.length];   // 0 עוד לא, 1 עכשיו, 2 בוצע, 3 נכשל
        final long[] start = new long[STAGES.length];
        final Canvas[] dots = new Canvas[STAGES.length];
        final Label[] texts = new Label[STAGES.length], times = new Label[STAGES.length], subs = new Label[STAGES.length];
        boolean failed;

        Stages(Composite parent) {
            Display d = parent.getDisplay();
            GridLayout gl = new GridLayout(3, false);
            gl.marginWidth = 0;
            gl.marginHeight = 0;
            gl.horizontalSpacing = 10;
            gl.verticalSpacing = 6;
            parent.setLayout(gl);
            for (int i = 0; i < STAGES.length; i++) {
                final int k = i;
                dots[i] = new Canvas(parent, SWT.NO_FOCUS | SWT.LEFT_TO_RIGHT | SWT.DOUBLE_BUFFERED);
                GridData dg = new GridData(SWT.CENTER, SWT.CENTER, false, false);
                dg.widthHint = 14;
                dg.heightHint = 14;
                dots[i].setLayoutData(dg);
                dots[i].addListener(SWT.Paint, e -> dot(e.gc, d, state[k]));
                texts[i] = new Label(parent, SWT.NONE);
                texts[i].setText(STAGES[i]);
                texts[i].setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
                times[i] = new Label(parent, SWT.NONE);
                times[i].setForeground(Look.color(d, Look.MUTED));
                GridData tg = new GridData(SWT.END, SWT.CENTER, false, false);
                tg.widthHint = 60;
                times[i].setLayoutData(tg);
                subs[i] = new Label(parent, SWT.NONE);
                subs[i].setForeground(Look.color(d, Look.MUTED));
                GridData sg = new GridData(SWT.FILL, SWT.TOP, true, false);
                sg.horizontalSpan = 3;
                sg.horizontalIndent = 24;
                subs[i].setLayoutData(sg);
            }
            reset();
        }

        void reset() {
            failed = false;
            for (int i = 0; i < STAGES.length; i++) {
                state[i] = 0;
                times[i].setText("");
                subs[i].setText("");
                show(subs[i], false);
            }
            paint();
        }

        int current() {
            for (int i = 0; i < state.length; i++) if (state[i] == 1 || state[i] == 3) return i;
            return -1;
        }

        /** עוברים לשלב i (רק קדימה): כל מה שלפניו — בוצע */
        void set(int i) {
            int cur = current();
            if (failed || i < cur || i == cur) return;
            for (int j = 0; j < i; j++) {
                if (state[j] != 2) times[j].setText("");
                state[j] = 2;
            }
            state[i] = 1;
            start[i] = System.currentTimeMillis();
            paint();
        }

        /** שורה קטנה באפור מתחת לשלב הנוכחי ("יש מה לתקן, מבקש תיקון…") */
        void sub(String text) {
            int cur = current();
            if (cur < 0) return;
            subs[cur].setText(text);
            show(subs[cur], true);
            fitHeight();
        }

        void finish() {
            for (int i = 0; i < state.length; i++) {
                state[i] = 2;
                times[i].setText("");
            }
            paint();
            line.redraw();
        }

        void fail() {
            failed = true;
            int cur = current();
            if (cur >= 0) {
                state[cur] = 3;
                times[cur].setText("");
            }
            paint();
            line.redraw();
        }

        void update() {
            int cur = current();
            if (cur < 0 || state[cur] != 1) return;
            long sec = (System.currentTimeMillis() - start[cur]) / 1000;
            String t = sec >= 1 ? sec + " שנ'" : "";
            if (!t.equals(times[cur].getText())) {
                times[cur].setText(t);
                times[cur].getParent().layout();
            }
        }

        /** כמה מהעבודה נעשתה (0..1) — לפס הדק */
        double fraction() {
            double f = 0;
            for (int i = 0; i < state.length; i++) {
                if (state[i] == 2) f += WEIGHT[i];
                else if (state[i] == 1) f += WEIGHT[i] * 0.95 * (1 - Math.exp(-(System.currentTimeMillis() - start[i]) / 1000.0 / EXPECT[i]));
                else if (state[i] == 3) f += WEIGHT[i] * 0.5;
            }
            return Math.min(1, f);
        }

        private void paint() {
            Display d = dots[0].getDisplay();
            for (int i = 0; i < STAGES.length; i++) {
                texts[i].setFont(Look.font(d, -1, state[i] == 1 ? SWT.BOLD : SWT.NORMAL));
                texts[i].setForeground(Look.color(d, state[i] == 0 ? 0x98A2B3 : state[i] == 3 ? Look.ERR : state[i] == 1 ? Look.TEXT : Look.INK2));
                texts[i].setData("dsviz.keepFont", Boolean.TRUE);
                dots[i].redraw();
            }
            dots[0].getParent().layout(true, true);
        }

        String report() {
            StringBuilder b = new StringBuilder();
            for (int i = 0; i < state.length; i++) b.append(i > 0 ? " " : "").append(new String[] { "pending", "now", "done", "failed" }[state[i]]);
            return b.toString();
        }
    }

    /** הסימן של שלב: ✓ בעיגול ירוק (בוצע), נקודה כחולה (עכשיו), עיגול אפור ריק (עוד לא), ✕ אדום (נכשל) */
    private static void dot(GC gc, Display d, int state) {
        gc.setAntialias(SWT.ON);
        if (state == 2 || state == 3) {
            gc.setBackground(Look.color(d, state == 2 ? Look.OK : Look.ERR));
            gc.fillOval(0, 0, 13, 13);
            gc.setForeground(d.getSystemColor(SWT.COLOR_WHITE));
            gc.setLineWidth(2);
            gc.setLineCap(SWT.CAP_ROUND);
            if (state == 2) gc.drawPolyline(new int[] { 3, 7, 5, 9, 10, 4 });
            else { gc.drawLine(4, 4, 9, 9); gc.drawLine(9, 4, 4, 9); }
            gc.setLineWidth(1);
        } else if (state == 1) {
            gc.setBackground(Look.color(d, Look.ACCENT));
            gc.fillOval(2, 2, 10, 10);
        } else {
            gc.setForeground(Look.color(d, 0xB4BCC8));
            gc.setLineWidth(2);
            gc.drawOval(2, 2, 9, 9);
            gc.setLineWidth(1);
        }
    }

    /** תמונה ממוזערת של קובץ שצורף (בתצוגת הבנייה): התמונה עצמה, או סמל של מסמך (PDF / Word / טקסט) */
    private final class Thumb extends Canvas {
        static final int W = 84, H = 96, TH = 66;
        private final ExerciseFiles.Item item;
        private Image img;

        Thumb(Composite parent, ExerciseFiles.Item item) {
            super(parent, SWT.DOUBLE_BUFFERED | SWT.NO_FOCUS | SWT.LEFT_TO_RIGHT);
            this.item = item;
            setLayoutData(new RowData(W, H));
            setToolTipText(item.name);
            byte[] prev = item.kind.equals("image") ? item.preview() : null;
            if (prev != null) {
                try {
                    ImageData id = new ImageLoader().load(new java.io.ByteArrayInputStream(prev))[0];
                    double k = Math.min((W - 10.0) / id.width, (TH - 8.0) / id.height);
                    int w = Math.max(1, (int) (id.width * k)), h = Math.max(1, (int) (id.height * k));
                    Image full = new Image(getDisplay(), id);
                    img = new Image(getDisplay(), w, h);
                    GC g = new GC(img);
                    g.setAntialias(SWT.ON);
                    g.setInterpolation(SWT.HIGH);
                    g.drawImage(full, 0, 0, id.width, id.height, 0, 0, w, h);
                    g.dispose();
                    full.dispose();
                } catch (Exception ignored) {
                    img = null;
                }
            }
            addListener(SWT.Dispose, e -> { if (img != null) img.dispose(); });
            addListener(SWT.Paint, e -> paint(e.gc));
        }

        private void paint(GC gc) {
            Display d = getDisplay();
            gc.setAntialias(SWT.ON);
            gc.setBackground(getParent().getBackground());
            gc.fillRectangle(0, 0, W, H);
            gc.setBackground(Look.color(d, Look.CARD));
            gc.fillRoundRectangle(0, 0, W - 1, TH, 12, 12);
            gc.setForeground(Look.color(d, Look.BORDER));
            gc.drawRoundRectangle(0, 0, W - 1, TH, 12, 12);
            if (img != null) {
                Rectangle b = img.getBounds();
                gc.drawImage(img, (W - b.width) / 2, (TH - b.height) / 2 + 1);
            } else {   // דף עם פינה מקופלת, ותווית בצבע של סוג הקובץ
                int x = W / 2 - 17, y = 9, w = 34, h = 46, f = 10;
                gc.setBackground(Look.color(d, Look.SURFACE));
                gc.fillPolygon(new int[] { x, y, x + w - f, y, x + w, y + f, x + w, y + h, x, y + h });
                gc.setForeground(Look.color(d, 0xB4BCC8));
                gc.drawPolygon(new int[] { x, y, x + w - f, y, x + w, y + f, x + w, y + h, x, y + h });
                gc.drawPolyline(new int[] { x + w - f, y, x + w - f, y + f, x + w, y + f });
                int color = item.kind.equals("pdf") ? 0xE5484D : item.kind.equals("word") ? Look.ACCENT : 0x667085;
                String label = item.kind.equals("pdf") ? "PDF" : item.kind.equals("word") ? "DOC" : "TXT";
                gc.setFont(Look.font(d, -3, SWT.BOLD));
                Point e = gc.textExtent(label);
                gc.setBackground(Look.color(d, color));
                gc.fillRoundRectangle(x - 4, y + h - 19, e.x + 8, e.y + 2, 4, 4);
                gc.setForeground(d.getSystemColor(SWT.COLOR_WHITE));
                gc.drawString(label, x, y + h - 18, true);
            }
            gc.setFont(Look.font(d, -2, SWT.NORMAL));
            gc.setForeground(Look.color(d, Look.MUTED));
            String n = item.name;
            Point e = gc.textExtent(n);
            while (e.x > W - 4 && n.length() > 3) { n = n.substring(0, n.length() - 2) + "…"; e = gc.textExtent(n); }
            gc.drawString(n, (W - e.x) / 2, TH + 8, true);
        }
    }

    private IJavaProject selectedProject() {
        int i = projectCombo.getSelectionIndex();
        return i >= 0 && i < projects.size() ? projects.get(i) : null;
    }

    // ------------------------------------------------------------------ התוצאה

    /** התרגיל מהשרת (אחרי OK) */
    public Map<String, Object> exercise() {
        return result;
    }

    /** התרגיל שנבנה (אחרי OK; אחרי ביטול באמצע הבנייה — מה שכבר נוצר), או null */
    SubmitHandler.Build build() {
        return build;
    }

    /** שמות הקבצים שנשלחו (להערה בראש הקובץ) */
    public List<String> sourceNames() {
        List<String> res = new ArrayList<String>();
        for (ExerciseFiles.Item it : items) res.add(it.name);
        return res;
    }

    /** הפרויקט שנבחר, או null אם צריך ליצור פרויקט חדש */
    public IJavaProject project() {
        return resultProject;
    }

    public String newProjectName() {
        return newProjectName;
    }

    WorkerClient client() {
        return client;
    }
}
