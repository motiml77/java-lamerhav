package il.lamerhav.dsviz;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.eclipse.core.resources.IProject;
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
import org.eclipse.swt.widgets.ProgressBar;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;

/**
 * חלון "שליחת תרגיל": מצרפים את דף התרגיל — PDF, Word, תמונות (גם כמה), או מדביקים צילום מסך עם Ctrl+V —
 * והוא נשלח לשרת (ומשם ל-Gemini). כשהתשובה מגיעה החלון נסגר, ו-SubmitHandler בונה את הקבצים.
 */
public class SubmitDialog extends Dialog {
    private static final String NOT_CONFIGURED = "שליחת תרגיל עוד לא הוגדרה בהתקנה הזאת: חסרה הכתובת של השרת (worker/README.md). אפשר לצרף קבצים, אבל עוד אי אפשר לשלוח.";

    private final List<IJavaProject> projects;
    private final String newProjectName;
    private final IProject preferred;
    private final WorkerClient client;

    private final List<ExerciseFiles.Item> items = new ArrayList<ExerciseFiles.Item>();
    private Composite zone, tiles, empty;
    private Label status, progressLabel;
    private Composite progressBox;
    private Text noteText;
    private Combo projectCombo;
    private Look.Round ok;
    private ScrolledComposite scroll;
    private Listener pasteFilter;
    private int pasted;
    private volatile boolean sending;
    private long sendStart;

    private Map<String, Object> result;
    private IJavaProject resultProject;

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
        Look.header(area, "שליחת תרגיל", "מצרפים את דף התרגיל — ומקבלים קובץ מוכן: המחלקות, main, ופעולה לכל סעיף עם מקום לכתוב את הפתרון.");

        GridLayout cl = new GridLayout(1, false);
        cl.marginWidth = 0;
        cl.marginHeight = 0;
        cl.verticalSpacing = 10;
        Composite[] holder = new Composite[1];
        scroll = Look.scroll(area, cl, holder);
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

        // --- התקדמות (מופיע בזמן השליחה)
        progressBox = new Composite(area, SWT.NONE);
        GridLayout pl = new GridLayout(2, false);
        pl.marginWidth = 4;
        pl.marginHeight = 4;
        progressBox.setLayout(pl);
        progressBox.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
        ProgressBar bar = new ProgressBar(progressBox, SWT.INDETERMINATE | SWT.HORIZONTAL);
        GridData bg = new GridData(SWT.BEGINNING, SWT.CENTER, false, false);
        bg.widthHint = 160;
        bar.setLayoutData(bg);
        progressLabel = new Label(progressBox, SWT.NONE);
        progressLabel.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        showProgress(false);

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
        Look.Round[] out = new Look.Round[1];
        Composite bar = Look.buttonBar(parent, "שליחה ויצירת התרגיל", this::send, this::cancel, out);
        ok = out[0];
        refresh();
        return bar;
    }

    @Override
    protected Point getInitialSize() {
        Point p = super.getInitialSize();
        Rectangle screen = getShell().getMonitor().getClientArea();
        return new Point(Math.min(p.x, screen.width - 40), Math.min(p.y, screen.height - 60));
    }

    @Override
    protected void handleShellCloseEvent() {
        if (sending && client != null) client.cancel();
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

    // ------------------------------------------------------------------ שליחה

    private void send() {
        if (sending) return;
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
        sendStart = System.currentTimeMillis();
        ok.setEnabled(false);
        status("", false);
        showProgress(true);
        tick();
        final List<ExerciseFiles.Item> copy = new ArrayList<ExerciseFiles.Item>(items);
        final String note = noteText.getText();
        final IJavaProject project = selectedProject();
        final Display d = getShell().getDisplay();
        Thread t = new Thread(() -> {
            Map<String, Object> r = null;
            String err = null;
            boolean cancelled = false;
            try {
                r = client.exercise(copy, note);
            } catch (WorkerClient.AiError e) {
                cancelled = "cancel".equals(e.code);
                err = e.getMessage();
            }
            final Map<String, Object> fr = r;
            final String ferr = err;
            final boolean fc = cancelled;
            d.asyncExec(() -> {
                sending = false;
                if (getShell() == null || getShell().isDisposed()) return;
                showProgress(false);
                if (fr != null) {
                    result = fr;
                    resultProject = project;
                    setReturnCode(OK);
                    close();
                    return;
                }
                ok.setEnabled(!items.isEmpty());
                status(fc ? "השליחה בוטלה." : ferr, !fc);
            });
        }, "dsviz-submit");
        t.setDaemon(true);
        t.start();
    }

    private void cancel() {
        if (sending) {
            client.cancel();
            return;
        }
        cancelPressed();
    }

    /** "שולח... 12 שניות" — כל שנייה, כל עוד שולחים */
    private void tick() {
        if (!sending || progressLabel == null || progressLabel.isDisposed()) return;
        long s = (System.currentTimeMillis() - sendStart) / 1000;
        progressLabel.setText("שולח ל-Gemini ובונה את המעטפת…" + (s >= 2 ? " " + s + " שניות" : "") + "  (בדרך כלל 15–60 שניות)");
        progressLabel.getParent().layout();
        progressLabel.getDisplay().timerExec(1000, this::tick);
    }

    private void showProgress(boolean on) {
        if (progressBox == null || progressBox.isDisposed()) return;
        show(progressBox, on);
        progressBox.getParent().layout(true, true);
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
