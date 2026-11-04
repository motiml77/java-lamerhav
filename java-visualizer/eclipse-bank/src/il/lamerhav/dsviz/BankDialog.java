package il.lamerhav.dsviz;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.ScrolledComposite;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Canvas;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;

/**
 * "מאגר תרגילים": מימין — הנושאים (עם כמה שאלות סומנו V מתוך כמה), באמצע — השאלות של הנושא מהקלה לקשה: נקודה בצבע הקושי
 * (ירוק קל, כתום בינוני, אדום קשה), הכותרת, ובקצה — עיגול שלוחצים עליו כדי לסמן V ירוק ("כבר עשיתי"). מתחת — נוסח השאלה.
 * בתחתית: לאיזה פרויקט, ו"צור תרגיל" (או "פתח את התרגיל" — אם כבר נוצר והקבצים עוד שם; ואז גם "צור עותק חדש").
 * החלון רק בוחר — את התרגיל בונה BankHandler אחרי שהחלון נסגר.
 */
public class BankDialog extends Dialog {
    /** מה נבחר: CREATE — ליצור (גם אם כבר נוצר — עותק חדש), OPEN — לפתוח את מה שכבר נוצר */
    enum Action { CREATE, OPEN }

    private final BankStore store;
    private final BankProgress progress;
    private final List<IJavaProject> projects;
    private final String newProjectName;
    private final IProject preferred;

    private String topicId;
    private BankStore.Question selected;
    private Action action = Action.CREATE;
    private IJavaProject chosenProject;
    private boolean newProject;

    private final List<Row> topicRows = new ArrayList<Row>();
    private final List<Row> questionRows = new ArrayList<Row>();
    private Composite questionList;
    private ScrolledComposite questionScroll;
    private Label listTitle, status;
    private ScrolledComposite previewScroll;
    private Composite previewBody;
    /** לבדיקות: מה מוצג בנוסח */
    final List<String> previewShown = new ArrayList<String>();
    private Combo projectCombo;
    private Look.Round createButton, copyButton;
    private Composite buttons;
    private final Runnable onProgress = () -> Display.getDefault().asyncExec(this::refreshMarks);

    public BankDialog(Shell parent, BankStore store, BankProgress progress, List<IJavaProject> projects, String newProjectName, IProject preferred) {
        super(parent);
        this.store = store;
        this.progress = progress;
        this.projects = projects;
        this.newProjectName = newProjectName;
        this.preferred = preferred;
        setShellStyle(SWT.DIALOG_TRIM | SWT.RESIZE | SWT.RIGHT_TO_LEFT | SWT.APPLICATION_MODAL);
        topicId = store.topics.isEmpty() ? "" : store.topics.get(0).id;
        for (BankStore.Topic t : store.topics) {   // הנושא הראשון שיש בו שאלות
            if (!store.of(t.id).isEmpty()) { topicId = t.id; break; }
        }
    }

    // ------------------------------------------------------------------ מה נבחר (אחרי OK)

    BankStore.Question question() { return selected; }

    Action action() { return action; }

    /** הפרויקט שנבחר, או null — פרויקט חדש (newProjectName) */
    IJavaProject project() { return newProject ? null : chosenProject; }

    String newProjectName() { return newProjectName; }

    /** לבדיקות: בוחרים נושא ושאלה כאילו לחצו עליהם */
    void select(String topic, String questionId) {
        showTopic(topic);
        for (Row r : questionRows) if (((BankStore.Question) r.getData()).id.equals(questionId)) choose(r);
    }

    // ------------------------------------------------------------------ החלון

    @Override
    protected void configureShell(Shell shell) {
        super.configureShell(shell);
        shell.setText("מאגר תרגילים");
        shell.setBackground(Look.color(shell.getDisplay(), Look.BG));
        shell.setBackgroundMode(SWT.INHERIT_DEFAULT);
        progress.listen(onProgress);
        shell.addListener(SWT.Dispose, e -> progress.unlisten(onProgress));
    }

    @Override
    protected Point getInitialSize() {
        Point p = new Point(980, 760);
        org.eclipse.swt.graphics.Rectangle s = getShell().getMonitor().getClientArea();
        return new Point(Math.min(p.x, s.width - 40), Math.min(p.y, s.height - 60));
    }

    @Override
    protected Control createDialogArea(Composite parent) {
        Composite area = (Composite) super.createDialogArea(parent);
        Display d = area.getDisplay();
        GridLayout al = (GridLayout) area.getLayout();
        al.marginWidth = 16;
        al.marginHeight = 8;
        al.verticalSpacing = 6;
        area.setBackground(Look.color(d, Look.BG));
        area.setBackgroundMode(SWT.INHERIT_DEFAULT);
        Look.header(area, "מאגר תרגילים", "בוחרים נושא ושאלה — ונוצר תרגיל מוכן: פעולה לכל סעיף, main שמריץ אותן, ו-💡 הצעה לפתרון ליד כל סעיף.");

        Composite main = new Composite(area, SWT.NONE);
        GridLayout ml = new GridLayout(2, false);
        ml.marginWidth = 0;
        ml.marginHeight = 0;
        ml.horizontalSpacing = 10;
        main.setLayout(ml);
        main.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

        // הנושאים
        GridData tg = new GridData(SWT.FILL, SWT.FILL, false, true);
        tg.widthHint = 210;
        Composite topics = Look.card(main, tg);
        Look.section(topics, "נושאים", 0);
        for (BankStore.Topic t : store.topics) {
            Row r = new Row(topics, 38, true);
            r.setData(t);
            r.onClick = () -> showTopic(t.id);
            GridData rg = new GridData(SWT.FILL, SWT.TOP, true, false);
            rg.heightHint = 38;
            r.setLayoutData(rg);
            topicRows.add(r);
        }

        // השאלות (כרטיס עליון) + הנוסח של השאלה שנבחרה (כרטיסים, עם ציורים של המבנים)
        Composite right = new Composite(main, SWT.NONE);
        GridLayout rl = new GridLayout(1, false);
        rl.marginWidth = 0;
        rl.marginHeight = 0;
        rl.verticalSpacing = 8;
        right.setLayout(rl);
        right.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        Composite listCard = Look.card(right, new GridData(SWT.FILL, SWT.TOP, true, false));
        Composite head = new Composite(listCard, SWT.NONE);
        GridLayout hl = new GridLayout(2, false);
        hl.marginWidth = 0;
        hl.marginHeight = 0;
        head.setLayout(hl);
        head.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
        listTitle = Look.section(head, "", 0);
        Canvas legend = new Canvas(head, SWT.NO_FOCUS | SWT.LEFT_TO_RIGHT | SWT.DOUBLE_BUFFERED);
        GridData lg = new GridData(SWT.END, SWT.CENTER, false, false);
        lg.widthHint = 210;
        lg.heightHint = 20;
        legend.setLayoutData(lg);
        legend.addListener(SWT.Paint, e -> paintLegend(e.gc, legend.getSize()));

        GridLayout ql = new GridLayout(1, false);
        ql.marginWidth = 0;
        ql.marginHeight = 0;
        ql.verticalSpacing = 4;
        Composite[] content = new Composite[1];
        questionScroll = Look.scroll(listCard, ql, content);
        questionList = content[0];
        questionList.setBackground(Look.color(d, Look.CARD));
        GridData sg = (GridData) questionScroll.getLayoutData();
        sg.heightHint = 150;                 // כ-3 שאלות; יותר — גוללים
        sg.grabExcessVerticalSpace = false;  // המקום הנוסף — לנוסח השאלה
        questionScroll.setBackground(Look.color(d, Look.CARD));

        GridLayout pl = new GridLayout(1, false);
        pl.marginWidth = 2;
        pl.marginHeight = 0;
        pl.verticalSpacing = 8;
        Composite[] pc = new Composite[1];
        previewScroll = Look.scroll(right, pl, pc);
        previewBody = pc[0];
        previewBody.setOrientation(SWT.RIGHT_TO_LEFT);

        // הפרויקט + מצב
        Composite bottom = new Composite(area, SWT.NONE);
        GridLayout bl = new GridLayout(3, false);
        bl.marginWidth = 4;
        bl.marginHeight = 2;
        bottom.setLayout(bl);
        bottom.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
        new Label(bottom, SWT.NONE).setText("התרגיל ייווצר בפרויקט:");
        projectCombo = new Combo(bottom, SWT.READ_ONLY | SWT.LEFT_TO_RIGHT);
        GridData cg = new GridData(SWT.BEGINNING, SWT.CENTER, false, false);
        cg.widthHint = 260;
        projectCombo.setLayoutData(cg);
        int sel = -1;
        for (int i = 0; i < projects.size(); i++) {
            IProject p = projects.get(i).getProject();
            projectCombo.add(p.getName());
            if (p.equals(preferred) || (sel < 0 && p.getName().equals(ProjectSetup.DEFAULT_PROJECT))) sel = i;
        }
        projectCombo.add(newProjectName + " (פרויקט חדש)");   // השם קודם — בתיבה משמאל לימין הוא לא מתערבב עם העברית
        projectCombo.select(Math.max(sel, 0));
        status = new Label(bottom, SWT.WRAP);
        status.setForeground(Look.color(d, Look.MUTED));
        status.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        Look.applyFont(area, -1);
        showTopic(topicId);
        return area;
    }

    @Override
    protected Control createButtonBar(Composite parent) {
        buttons = new Composite(parent, SWT.NONE);
        buttons.setBackground(Look.color(parent.getDisplay(), Look.BG));
        GridLayout l = new GridLayout(5, false);
        l.marginWidth = 20;
        l.marginHeight = 10;
        l.horizontalSpacing = 10;
        buttons.setLayout(l);
        buttons.setLayoutData(new GridData(SWT.FILL, SWT.BOTTOM, true, false));
        createButton = new Look.Round(buttons, "צור תרגיל", true, () -> finish(existing() != null ? Action.OPEN : Action.CREATE));
        copyButton = new Look.Round(buttons, "צור עותק חדש", false, () -> finish(Action.CREATE));
        new Look.Round(buttons, "שאלה אקראית", false, this::random);
        new Look.Round(buttons, "סגירה", false, this::cancelPressed);
        Label c = new Label(buttons, SWT.LEFT_TO_RIGHT);
        c.setText(Look.COPYRIGHT);
        c.setFont(Look.font(parent.getDisplay(), -2, SWT.NORMAL));
        c.setForeground(Look.color(parent.getDisplay(), 0xA0A8B8));
        c.setLayoutData(new GridData(SWT.END, SWT.CENTER, true, false));
        updateButtons();
        return buttons;
    }

    // ------------------------------------------------------------------ בחירה

    private void showTopic(String id) {
        topicId = id;
        for (Row r : topicRows) r.setSelected(((BankStore.Topic) r.getData()).id.equals(id));
        for (Control c : questionList.getChildren()) c.dispose();   // השורות (וההודעה "עוד אין שאלות") של הנושא הקודם
        questionRows.clear();
        BankStore.Topic t = store.topic(id);
        List<BankStore.Question> qs = store.of(id);
        listTitle.setText(t == null ? "" : "השאלות בנושא " + t.title + (qs.isEmpty() ? "" : " (" + qs.size() + ")"));
        Row first = null;
        for (BankStore.Question q : qs) {
            Row r = new Row(questionList, 44, false);
            r.setData(q);
            r.onClick = () -> choose(r);
            r.onMark = () -> progress.setDone(q.id, !progress.done(q.id));
            r.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
            questionRows.add(r);
            if (first == null) first = r;
        }
        if (qs.isEmpty()) {
            Label empty = new Label(questionList, SWT.WRAP);
            empty.setText("עוד אין שאלות בנושא הזה.");
            empty.setForeground(Look.color(questionList.getDisplay(), Look.MUTED));
            empty.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
            empty.setFont(Look.font(questionList.getDisplay(), -1, SWT.NORMAL));
        }
        // גובה הרשימה לפי מספר השאלות (עד 4 בלי גלילה) — כל השאר לנוסח השאלה ולציורים
        ((GridData) questionScroll.getLayoutData()).heightHint = Math.max(1, Math.min(qs.size(), 4)) * 48;
        questionScroll.getParent().getParent().getParent().layout(true, true);
        questionList.layout(true, true);
        Look.updateScroll(questionScroll);
        Look.wheel(questionScroll);
        questionScroll.setOrigin(0, 0);
        selected = null;
        if (first != null) choose(first);
        else {
            showPreview(null);
            updateButtons();
        }
        listTitle.getParent().layout(true, true);
    }

    private void choose(Row r) {
        selected = (BankStore.Question) r.getData();
        for (Row o : questionRows) o.setSelected(o == r);
        showPreview(selected);
        updateButtons();
        // השורה שנבחרה — בתוך האזור הנראה
        Point o = questionScroll.getOrigin();
        int y = r.getLocation().y, h = questionScroll.getClientArea().height;
        if (y < o.y) questionScroll.setOrigin(0, y);
        else if (y + r.getSize().y > o.y + h) questionScroll.setOrigin(0, y + r.getSize().y - h);
    }

    private void showPreview(BankStore.Question q) {
        previewBody.setRedraw(false);
        try {
            for (Control c : previewBody.getChildren()) c.dispose();
            previewShown.clear();
            if (q != null) {
                BankQuestionPanel.titleRow(previewBody, q, store.topic(q.topic) == null ? null : store.topic(q.topic).title, previewShown);
                BankQuestionPanel.build(previewBody, q, null, previewShown);
            }
            previewBody.layout(true, true);
            Look.updateScroll(previewScroll);
            Look.wheel(previewScroll);
            previewScroll.setOrigin(0, 0);
        } finally {
            previewBody.setRedraw(true);
        }
    }

    private void random() {
        List<BankStore.Question> qs = store.of(topicId);
        if (qs.isEmpty()) return;
        List<BankStore.Question> open = new ArrayList<BankStore.Question>();
        for (BankStore.Question q : qs) if (!progress.done(q.id) && q != selected) open.add(q);
        if (open.isEmpty()) for (BankStore.Question q : qs) if (q != selected) open.add(q);
        if (open.isEmpty()) return;
        BankStore.Question pick = open.get(new Random().nextInt(open.size()));
        for (Row r : questionRows) if (r.getData() == pick) choose(r);
    }

    /** מה שכבר נוצר מהשאלה שנבחרה — אם הקבצים עוד בסביבת העבודה; אחרת null */
    private BankProgress.Created existing() {
        if (selected == null) return null;
        List<BankProgress.Created> all = progress.createdOf(selected.id);
        for (int i = all.size() - 1; i >= 0; i--) {
            BankProgress.Created c = all.get(i);
            for (String f : c.files) {
                if (ResourcesPlugin.getWorkspace().getRoot().findMember(f) != null) return c;
            }
        }
        return null;
    }

    private void updateButtons() {
        if (createButton == null) return;
        BankProgress.Created c = existing();
        createButton.setEnabled(selected != null);
        setText(createButton, c != null ? "פתח את התרגיל" : "צור תרגיל");
        copyButton.setVisible(c != null);
        ((GridData) layoutData(copyButton)).exclude = c == null;
        if (status != null) {
            String folder = c == null ? "" : c.folder.substring(c.folder.lastIndexOf('/') + 1);
            status.setText(c == null ? "" : "כבר יצרת את התרגיל הזה (" + folder + ") — \"פתח את התרגיל\" ממשיך מאיפה שעצרת.");
            status.getParent().layout(true, true);
        }
        buttons.layout(true, true);
    }

    private static Object layoutData(Control c) {
        if (!(c.getLayoutData() instanceof GridData)) c.setLayoutData(new GridData());
        return c.getLayoutData();
    }

    /** הטקסט של כפתור Round קבוע — מחליפים כפתור בכפתור באותו מקום */
    private void setText(Look.Round b, String text) {
        if (text.equals(b.getData("text"))) return;
        Composite parent = b.getParent();
        Look.Round n = new Look.Round(parent, text, true, () -> finish(existing() != null ? Action.OPEN : Action.CREATE));
        n.setData("text", text);
        n.moveAbove(b);
        n.setEnabled(b.getEnabled());
        b.dispose();
        createButton = n;
    }

    private void finish(Action a) {
        if (selected == null) return;
        action = a;
        int i = projectCombo.getSelectionIndex();
        newProject = i < 0 || i >= projects.size();
        chosenProject = newProject ? null : projects.get(i);
        okPressed();
    }

    private void refreshMarks() {
        if (getShell() == null || getShell().isDisposed()) return;
        for (Row r : topicRows) r.redraw();
        for (Row r : questionRows) r.redraw();
    }

    private void paintLegend(GC gc, Point size) {
        Display d = gc.getDevice() instanceof Display ? (Display) gc.getDevice() : Display.getDefault();
        gc.setAntialias(SWT.ON);
        gc.setFont(Look.font(d, -2, SWT.NORMAL));
        int x = size.x - 4;   // מימין לשמאל: ● קל   ● בינוני   ● קשה — הנקודה מימין למילה, כמו בשורות
        for (BankStore.Level l : BankStore.Level.values()) {
            x -= 10;
            gc.setBackground(Look.color(d, l.color));
            gc.fillOval(x, size.y / 2 - 5, 10, 10);
            Point e = gc.textExtent(l.title);
            x -= 5 + e.x;
            gc.setForeground(Look.color(d, Look.MUTED));
            gc.drawString(l.title, x, (size.y - e.y) / 2, true);
            x -= 16;
        }
    }

    // ------------------------------------------------------------------ שורה (נושא / שאלה)

    /** שורה מצוירת: נושא (כותרת + "כמה V מתוך כמה") או שאלה (נקודת קושי, כותרת, רמה, ועיגול ה-V) */
    private final class Row extends Canvas {
        private final boolean topic;
        private boolean selectedRow, hover;
        Runnable onClick, onMark;

        Row(Composite parent, int height, boolean topic) {
            super(parent, SWT.NO_FOCUS | SWT.LEFT_TO_RIGHT | SWT.DOUBLE_BUFFERED);
            this.topic = topic;
            setCursor(getDisplay().getSystemCursor(SWT.CURSOR_HAND));
            addListener(SWT.Paint, e -> paint(e.gc));
            addListener(SWT.MouseEnter, e -> { hover = true; redraw(); });
            addListener(SWT.MouseExit, e -> { hover = false; redraw(); });
            addListener(SWT.MouseUp, e -> {
                if (e.button != 1) return;
                if (!topic && onMark != null && e.x < 44) onMark.run();   // העיגול של ה-V (בצד שמאל)
                else if (onClick != null) onClick.run();
            });
            if (!topic) setToolTipText("לחיצה על העיגול משמאל — V ירוק: כבר עשיתי את התרגיל הזה");
        }

        void setSelected(boolean s) {
            selectedRow = s;
            redraw();
        }

        @Override
        public Point computeSize(int wHint, int hHint, boolean changed) {
            return new Point(wHint == SWT.DEFAULT ? 200 : wHint, topic ? 38 : 44);
        }

        private void paint(GC gc) {
            Display d = getDisplay();
            Point s = getSize();
            gc.setAntialias(SWT.ON);
            gc.setTextAntialias(SWT.ON);
            gc.setBackground(getParent().getBackground());
            gc.fillRectangle(0, 0, s.x, s.y);
            int fill = selectedRow ? Look.ACCENT_SOFT : hover ? Look.SURFACE : Look.CARD;
            gc.setBackground(Look.color(d, fill));
            gc.fillRoundRectangle(0, 1, s.x - 1, s.y - 3, 14, 14);
            if (selectedRow) {
                gc.setForeground(Look.color(d, Look.ACCENT));
                gc.drawRoundRectangle(0, 1, s.x - 1, s.y - 3, 14, 14);
            }
            if (topic) paintTopic(gc, d, s);
            else paintQuestion(gc, d, s);
        }

        private void paintTopic(GC gc, Display d, Point s) {
            BankStore.Topic t = (BankStore.Topic) getData();
            List<BankStore.Question> qs = store.of(t.id);
            gc.setFont(Look.font(d, 0, selectedRow ? SWT.BOLD : SWT.NORMAL));
            String title = RtlText.rtl(t.title);
            Point e = gc.textExtent(title);
            gc.setForeground(Look.color(d, Look.TEXT));
            gc.drawString(title, s.x - 12 - e.x, (s.y - e.y) / 2, true);
            String count = qs.isEmpty() ? "—" : progress.doneCount(qs) + "/" + qs.size();
            gc.setFont(Look.font(d, -2, SWT.NORMAL));
            Point c = gc.textExtent(count);
            boolean all = !qs.isEmpty() && progress.doneCount(qs) == qs.size();
            gc.setForeground(Look.color(d, all ? Look.OK : Look.MUTED));
            int x = 12;
            if (all) {
                check(gc, d, x + 6, s.y / 2, 6, Look.OK);
                x += 18;
            }
            gc.drawString(count, x, (s.y - c.y) / 2, true);
        }

        private void paintQuestion(GC gc, Display d, Point s) {
            BankStore.Question q = (BankStore.Question) getData();
            boolean done = progress.done(q.id);
            // נקודת הקושי — בצד ימין
            gc.setBackground(Look.color(d, q.level.color));
            gc.fillOval(s.x - 22, s.y / 2 - 6, 12, 12);
            // הכותרת
            gc.setFont(Look.font(d, 0, SWT.NORMAL));
            String title = RtlText.rtl(q.title);
            Point e = gc.textExtent(title);
            int right = s.x - 32, left = 110;
            gc.setForeground(Look.color(d, done ? Look.MUTED : Look.TEXT));
            gc.setClipping(left, 0, Math.max(0, right - left), s.y);
            gc.drawString(title, Math.max(left, right - e.x), (s.y - e.y) / 2, true);
            gc.setClipping((org.eclipse.swt.graphics.Rectangle) null);
            // הרמה
            gc.setFont(Look.font(d, -2, SWT.NORMAL));
            Point l = gc.textExtent(q.level.title);
            gc.setForeground(Look.color(d, q.level.color));
            gc.drawString(q.level.title, 50, (s.y - l.y) / 2, true);
            // העיגול של ה-V — בצד שמאל
            int cx = 24, cy = s.y / 2, r = 11;
            if (done) {
                gc.setBackground(Look.color(d, Look.OK));
                gc.fillOval(cx - r, cy - r, 2 * r, 2 * r);
                check(gc, d, cx, cy, 6, 0xFFFFFF);
            } else {
                gc.setForeground(Look.color(d, hover ? 0x9AA3B2 : 0xC9D0DC));
                gc.setLineWidth(2);
                gc.drawOval(cx - r, cy - r, 2 * r, 2 * r);
                gc.setLineWidth(1);
            }
        }

        private void check(GC gc, Display d, int cx, int cy, int r, int rgb) {
            gc.setForeground(Look.color(d, rgb));
            gc.setLineWidth(2);
            gc.setLineCap(SWT.CAP_ROUND);
            gc.drawPolyline(new int[] { cx - r, cy, cx - r / 3, cy + r * 2 / 3, cx + r, cy - r * 2 / 3 });
            gc.setLineWidth(1);
        }
    }
}
