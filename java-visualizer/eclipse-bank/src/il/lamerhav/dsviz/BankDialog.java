package il.lamerhav.dsviz;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;

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
 * "מאגר תרגילים" — בסגנון של אתרי תרגול (LeetCode):
 *   סרגל צד (מימין): חיפוש, סינון לפי קושי (הכול / קל / בינוני / קשה), ועץ נפתח — נושא (עם פס התקדמות ו"כמה V מתוך כמה"),
 *   ומתחתיו השאלות בשורות קומפקטיות: נקודה בצבע הקושי, הכותרת, ועיגול שלוחצים עליו לסמן V ירוק ("כבר עשיתי").
 *   מעל הסרגל — סיכום ההתקדמות לכל רמת קושי. כל השאר — הנוסח של השאלה שנבחרה (כרטיסים, ציורים של המבנים, הפלט הרצוי).
 * בתחתית: לאיזה פרויקט, ו"צור תרגיל" (או "פתח את התרגיל" — אם כבר נוצר; ואז גם "צור עותק חדש").
 * החלון רק בוחר — את התרגיל בונה BankHandler אחרי שהחלון נסגר.
 */
public class BankDialog extends Dialog {
    /** מה נבחר: CREATE — ליצור (גם אם כבר נוצר — עותק חדש), OPEN — לפתוח את מה שכבר נוצר */
    enum Action { CREATE, OPEN }

    private static final int SIDEBAR = 330, TOPIC_H = 40, Q_H = 30;

    private final BankStore store;
    private final BankProgress progress;
    private final List<IJavaProject> projects;
    private final String newProjectName;
    private final IProject preferred;

    private BankStore.Question selected;
    private Action action = Action.CREATE;
    private IJavaProject chosenProject;
    private boolean newProject;
    private final Set<String> expanded = new HashSet<String>();
    private String search = "";
    /** סינון לפי קושי: null — הכול */
    private BankStore.Level levelFilter;

    /** השורות בעץ (נושאים ושאלות) — לבדיקות: topicRows */
    private final List<Row> topicRows = new ArrayList<Row>();
    private final List<Row> questionRows = new ArrayList<Row>();
    /** לבדיקות: מה מוצג בעץ ("TOPIC id open 1/3", "Q id medium done") ובנוסח */
    final List<String> treeShown = new ArrayList<String>();
    final List<String> previewShown = new ArrayList<String>();

    private ScrolledComposite treeScroll, previewScroll;
    private Composite tree, previewBody;
    private Canvas stats;
    private Label status;
    private Combo projectCombo;
    private Look.Round createButton, copyButton;
    private Composite buttons;
    private final List<Look.Chip> filterChips = new ArrayList<Look.Chip>();
    private final Runnable onProgress = () -> Display.getDefault().asyncExec(this::refreshMarks);
    /** האייקונים של הנושאים (icons/topics/<id>.png, 64 פיקסלים — מצוירים קטן) */
    private final java.util.Map<String, org.eclipse.swt.graphics.Image> icons = new java.util.HashMap<String, org.eclipse.swt.graphics.Image>();

    private org.eclipse.swt.graphics.Image icon(String topic) {
        if (icons.containsKey(topic)) return icons.get(topic);
        org.eclipse.swt.graphics.Image img = null;
        try (java.io.InputStream in = BankDialog.class.getClassLoader().getResourceAsStream("icons/topics/" + topic + ".png")) {
            if (in != null) img = new org.eclipse.swt.graphics.Image(Display.getCurrent(), in);
        } catch (Exception e) {
            // בלי אייקון
        }
        icons.put(topic, img);
        return img;
    }

    public BankDialog(Shell parent, BankStore store, BankProgress progress, List<IJavaProject> projects, String newProjectName, IProject preferred) {
        super(parent);
        this.store = store;
        this.progress = progress;
        this.projects = projects;
        this.newProjectName = newProjectName;
        this.preferred = preferred;
        setShellStyle(SWT.DIALOG_TRIM | SWT.RESIZE | SWT.MAX | SWT.RIGHT_TO_LEFT | SWT.APPLICATION_MODAL);
        // בהתחלה פתוח הנושא הראשון שיש בו שאלות, ונבחרת השאלה הראשונה בו שעוד לא סומנה
        for (BankStore.Topic t : store.topics) {
            List<BankStore.Question> qs = store.of(t.id);
            if (qs.isEmpty()) continue;
            expanded.add(t.id);
            for (BankStore.Question q : qs) if (!progress.done(q.id)) { selected = q; break; }
            if (selected == null) selected = qs.get(0);
            break;
        }
    }

    // ------------------------------------------------------------------ מה נבחר (אחרי OK)

    BankStore.Question question() { return selected; }

    Action action() { return action; }

    /** הפרויקט שנבחר, או null — פרויקט חדש (newProjectName) */
    IJavaProject project() { return newProject ? null : chosenProject; }

    String newProjectName() { return newProjectName; }

    /** לבדיקות: פותחים נושא ובוחרים בו שאלה (questionId ריק — רק פותחים) */
    void select(String topic, String questionId) {
        expanded.add(topic);
        BankStore.Question q = questionId == null || questionId.isEmpty() ? null : store.find(questionId);
        selected = q;
        rebuildTree();
        showPreview(q);
        updateButtons();
    }

    // ------------------------------------------------------------------ החלון

    @Override
    protected void configureShell(Shell shell) {
        super.configureShell(shell);
        shell.setText("מאגר תרגילים");
        shell.setBackground(Look.color(shell.getDisplay(), Look.BG));
        shell.setBackgroundMode(SWT.INHERIT_DEFAULT);
        progress.listen(onProgress);
        shell.addListener(SWT.Dispose, e -> {
            progress.unlisten(onProgress);
            for (org.eclipse.swt.graphics.Image i : icons.values()) if (i != null) i.dispose();
        });
    }

    @Override
    protected Point getInitialSize() {
        Point p = new Point(1060, 780);
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
        al.verticalSpacing = 8;
        area.setBackground(Look.color(d, Look.BG));
        area.setBackgroundMode(SWT.INHERIT_DEFAULT);
        Look.header(area, "מאגר תרגילים", "בוחרים שאלה מהרשימה — ונוצר תרגיל מוכן: פעולה לכל סעיף, תוכנית ראשית שמריצה אותן, והצעה לפתרון לכל סעיף.");

        Composite main = new Composite(area, SWT.NONE);
        GridLayout ml = new GridLayout(2, false);
        ml.marginWidth = 0;
        ml.marginHeight = 0;
        ml.horizontalSpacing = 12;
        main.setLayout(ml);
        main.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

        // ---- סרגל הצד
        GridData sg = new GridData(SWT.FILL, SWT.FILL, false, true);
        sg.widthHint = SIDEBAR;
        Composite side = Look.card(main, sg);
        ((GridLayout) side.getLayout()).verticalSpacing = 8;
        stats = new Canvas(side, SWT.NO_FOCUS | SWT.LEFT_TO_RIGHT | SWT.DOUBLE_BUFFERED);
        GridData stg = new GridData(SWT.FILL, SWT.TOP, true, false);
        stg.heightHint = 58;
        stats.setLayoutData(stg);
        stats.addListener(SWT.Paint, e -> paintStats(e.gc, stats.getSize()));

        Look.HintText searchBox = new Look.HintText(side, SWT.SINGLE | SWT.BORDER | SWT.RIGHT_TO_LEFT | SWT.SEARCH);
        searchBox.setHint("🔍  חיפוש שאלה…");
        searchBox.text.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
        searchBox.text.addListener(SWT.Modify, e -> {
            String v = searchBox.value().trim();
            if (v.equals(search)) return;
            search = v;
            rebuildTree();
        });

        Composite filters = new Composite(side, SWT.NONE);
        GridLayout fl = new GridLayout(4, true);
        fl.marginWidth = 0;
        fl.marginHeight = 0;
        fl.horizontalSpacing = 6;
        filters.setLayout(fl);
        filters.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
        Look.Chip all = Look.chip(filters, "הכול", null, filterChips, 28, 60, this::filterChanged);
        all.setSelection(true);
        for (BankStore.Level l : BankStore.Level.values()) Look.chip(filters, l.title, l, filterChips, 28, 60, this::filterChanged);

        GridLayout tl = new GridLayout(1, false);
        tl.marginWidth = 0;
        tl.marginHeight = 0;
        tl.verticalSpacing = 2;
        Composite[] tc = new Composite[1];
        treeScroll = Look.scroll(side, tl, tc);
        tree = tc[0];
        tree.setBackground(Look.color(d, Look.CARD));
        treeScroll.setBackground(Look.color(d, Look.CARD));

        // ---- הנוסח של השאלה
        GridLayout pl = new GridLayout(1, false);
        pl.marginWidth = 2;
        pl.marginHeight = 0;
        pl.verticalSpacing = 8;
        Composite[] pc = new Composite[1];
        previewScroll = Look.scroll(main, pl, pc);
        previewBody = pc[0];
        previewBody.setOrientation(SWT.RIGHT_TO_LEFT);

        // ---- הפרויקט + מצב
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
        rebuildTree();
        showPreview(selected);
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

    // ------------------------------------------------------------------ העץ

    /** השאלות של הנושא שעוברות את החיפוש ואת הסינון */
    private List<BankStore.Question> visible(String topicId) {
        List<BankStore.Question> out = new ArrayList<BankStore.Question>();
        String s = search.toLowerCase(Locale.ROOT);
        for (BankStore.Question q : store.of(topicId)) {
            if (levelFilter != null && q.level != levelFilter) continue;
            if (!s.isEmpty() && !q.title.toLowerCase(Locale.ROOT).contains(s) && !q.intro.toLowerCase(Locale.ROOT).contains(s)) continue;
            out.add(q);
        }
        return out;
    }

    private boolean filtering() {
        return !search.isEmpty() || levelFilter != null;
    }

    private void rebuildTree() {
        if (tree == null) return;
        Display d = tree.getDisplay();
        tree.setRedraw(false);
        try {
            for (Control c : tree.getChildren()) c.dispose();
            topicRows.clear();
            questionRows.clear();
            treeShown.clear();
            Row selRow = null;
            for (BankStore.Topic t : store.topics) {
                List<BankStore.Question> all = store.of(t.id), qs = visible(t.id);
                if (filtering() && qs.isEmpty()) continue;   // בחיפוש — רק נושאים שיש בהם תוצאות
                boolean open = expanded.contains(t.id) || filtering();
                Row tr = new Row(tree, true);
                tr.setData(t);
                tr.open = open;
                tr.onClick = () -> {
                    if (!expanded.remove(t.id)) expanded.add(t.id);
                    rebuildTree();
                };
                tr.setLayoutData(rowData(TOPIC_H));
                topicRows.add(tr);
                treeShown.add("TOPIC " + t.id + (open ? " open " : " closed ") + progress.doneCount(all) + "/" + all.size());
                if (!open) continue;
                if (qs.isEmpty()) {
                    Label empty = new Label(tree, SWT.WRAP | SWT.RIGHT_TO_LEFT);
                    empty.setText("עוד אין שאלות בנושא הזה.");
                    empty.setForeground(Look.color(d, Look.MUTED));
                    empty.setFont(Look.font(d, -1, SWT.NORMAL));
                    GridData eg = rowData(SWT.DEFAULT);
                    eg.horizontalIndent = 30;
                    eg.verticalIndent = 2;
                    empty.setLayoutData(eg);
                }
                for (BankStore.Question q : qs) {
                    Row r = new Row(tree, false);
                    r.setData(q);
                    r.onClick = () -> choose(q);
                    r.setLayoutData(rowData(Q_H));
                    if (q == selected) { r.setSelected(true); selRow = r; }
                    questionRows.add(r);
                    treeShown.add("Q " + q.id + " " + q.level.id + (progress.done(q.id) ? " done" : ""));
                }
            }
            if (topicRows.isEmpty()) {
                Label none = new Label(tree, SWT.WRAP | SWT.RIGHT_TO_LEFT);
                none.setText("לא נמצאו שאלות.");
                none.setForeground(Look.color(d, Look.MUTED));
                none.setLayoutData(rowData(SWT.DEFAULT));
            }
            tree.layout(true, true);
            Look.updateScroll(treeScroll);
            Look.wheel(treeScroll);
            if (selRow != null) reveal(selRow);
        } finally {
            tree.setRedraw(true);
        }
        if (stats != null) stats.redraw();
    }

    private static GridData rowData(int h) {
        GridData g = new GridData(SWT.FILL, SWT.TOP, true, false);
        if (h != SWT.DEFAULT) g.heightHint = h;
        return g;
    }

    private void reveal(Row r) {
        Point o = treeScroll.getOrigin();
        int y = r.getLocation().y, h = treeScroll.getClientArea().height;
        if (y < o.y) treeScroll.setOrigin(0, y);
        else if (y + r.getSize().y > o.y + h) treeScroll.setOrigin(0, y + r.getSize().y - h);
    }

    private void filterChanged() {
        for (Look.Chip c : filterChips) if (c.getSelection()) levelFilter = (BankStore.Level) c.getData();
        rebuildTree();
    }

    private void choose(BankStore.Question q) {
        selected = q;
        for (Row o : questionRows) o.setSelected(o.getData() == q);
        showPreview(q);
        updateButtons();
    }

    private void showPreview(BankStore.Question q) {
        if (previewBody == null) return;
        previewBody.setRedraw(false);
        try {
            for (Control c : previewBody.getChildren()) c.dispose();
            previewShown.clear();
            if (q != null) {
                BankStore.Topic t = store.topic(q.topic);
                BankQuestionPanel.titleRow(previewBody, q, t == null ? null : t.title, previewShown);
                BankQuestionPanel.build(previewBody, q, null, previewShown);
            } else {
                Composite c = Look.card(previewBody, BankQuestionPanel.wide());
                BankQuestionPanel.text(c, store.questions.isEmpty() ? "עוד אין שאלות במאגר." : "בוחרים שאלה מהרשימה — והנוסח שלה יופיע כאן.", 1, SWT.NORMAL, Look.MUTED, previewShown);
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
        List<BankStore.Question> pool = new ArrayList<BankStore.Question>(), open = new ArrayList<BankStore.Question>();
        // מהנושא של השאלה הנוכחית (או מכל הפתוחים), עם הסינון; קודם שאלות שעוד לא עשו
        for (BankStore.Topic t : store.topics) {
            if (selected != null ? !t.id.equals(selected.topic) : !expanded.contains(t.id)) continue;
            pool.addAll(visible(t.id));
        }
        if (pool.isEmpty()) for (BankStore.Topic t : store.topics) pool.addAll(visible(t.id));
        for (BankStore.Question q : pool) if (!progress.done(q.id) && q != selected) open.add(q);
        if (open.isEmpty()) for (BankStore.Question q : pool) if (q != selected) open.add(q);
        if (open.isEmpty()) return;
        BankStore.Question pick = open.get(new Random().nextInt(open.size()));
        expanded.add(pick.topic);
        selected = pick;
        rebuildTree();
        showPreview(pick);
        updateButtons();
    }

    /** מה שכבר נוצר מהשאלה שנבחרה — אם הקבצים עוד בסביבת העבודה; אחרת null */
    private BankProgress.Created existing() {
        if (selected == null) return null;
        List<BankProgress.Created> all = progress.createdOf(selected.id);
        for (int i = all.size() - 1; i >= 0; i--) {
            for (String f : all.get(i).files) if (ResourcesPlugin.getWorkspace().getRoot().findMember(f) != null) return all.get(i);
        }
        return null;
    }

    private void updateButtons() {
        if (createButton == null) return;
        BankProgress.Created c = existing();
        setText(createButton, c != null ? "פתח את התרגיל" : "צור תרגיל");
        createButton.setEnabled(selected != null);
        copyButton.setVisible(c != null);
        if (!(copyButton.getLayoutData() instanceof GridData)) copyButton.setLayoutData(new GridData());
        ((GridData) copyButton.getLayoutData()).exclude = c == null;
        if (status != null) {
            String folder = c == null ? "" : c.folder.substring(c.folder.lastIndexOf('/') + 1);
            status.setText(c == null ? "" : "כבר יצרת את התרגיל הזה (" + folder + ") — \"פתח את התרגיל\" ממשיך מאיפה שעצרת.");
            status.getParent().layout(true, true);
        }
        buttons.layout(true, true);
    }

    /** הטקסט של כפתור Round קבוע — מחליפים כפתור בכפתור באותו מקום */
    private void setText(Look.Round b, String text) {
        if (text.equals(b.getData("text"))) return;
        Look.Round n = new Look.Round(b.getParent(), text, true, () -> finish(existing() != null ? Action.OPEN : Action.CREATE));
        n.setData("text", text);
        n.moveAbove(b);
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
        if (stats != null) stats.redraw();
    }

    // ------------------------------------------------------------------ סיכום ההתקדמות

    /** "פתרת 3 מתוך 12", ופס לכל רמת קושי: ● קל 2/5 ● בינוני 1/4 ● קשה 0/3 */
    private void paintStats(GC gc, Point s) {
        Display d = Display.getCurrent();
        gc.setAntialias(SWT.ON);
        gc.setTextAntialias(SWT.ON);
        gc.setBackground(Look.color(d, Look.CARD));
        gc.fillRectangle(0, 0, s.x, s.y);
        int done = progress.doneCount(store.questions), total = store.questions.size();
        gc.setFont(Look.font(d, 0, SWT.BOLD));
        String head = RtlText.rtl("ההתקדמות שלי");
        Point he = gc.textExtent(head);
        gc.setForeground(Look.color(d, Look.TEXT));
        gc.drawString(head, s.x - 4 - he.x, 0, true);
        gc.setFont(Look.font(d, 0, SWT.BOLD));
        String count = RtlText.rtl(done + " מתוך " + total);
        Point ce = gc.textExtent(count);
        gc.setForeground(Look.color(d, done > 0 && done == total ? Look.OK : Look.TEXT));
        gc.drawString(count, 4, 0, true);
        // שלוש קופסאות קטנות — רמה, כמה מתוך כמה, ופס
        int y = he.y + 6, w = (s.x - 8 - 2 * 8) / 3, x = s.x - 4 - w;
        for (BankStore.Level l : BankStore.Level.values()) {
            int n = 0, k = 0;
            for (BankStore.Question q : store.questions) if (q.level == l) { n++; if (progress.done(q.id)) k++; }
            gc.setFont(Look.font(d, -2, SWT.BOLD));
            String t = RtlText.rtl(l.title);
            Point te = gc.textExtent(t);
            gc.setForeground(Look.color(d, l.color));
            gc.drawString(t, x + w - te.x, y, true);
            gc.setFont(Look.font(d, -2, SWT.NORMAL));
            gc.setForeground(Look.color(d, Look.MUTED));
            gc.drawString(k + "/" + n, x, y, true);
            int by = y + te.y + 3;
            gc.setBackground(Look.color(d, 0xE9EDF3));
            gc.fillRoundRectangle(x, by, w, 6, 6, 6);
            if (n > 0 && k > 0) {
                gc.setBackground(Look.color(d, l.color));
                int fw = Math.max(6, w * k / n);
                gc.fillRoundRectangle(x + w - fw, by, fw, 6, 6, 6);   // מתמלא מימין לשמאל
            }
            x -= w + 8;
        }
    }

    // ------------------------------------------------------------------ שורה בעץ (נושא / שאלה)

    /**
     * שורה מצוירת. נושא: חץ פתיחה, השם, כמה V מתוך כמה ופס התקדמות דק. שאלה (מוזחת פנימה): נקודת קושי, הכותרת,
     * ובקצה השמאלי — עיגול ה-V (רק מוצג: מסמנים בחלון הצף "השאלה"; לחיצה בכל השורה בוחרת את השאלה).
     */
    private final class Row extends Canvas {
        private final boolean topic;
        private boolean selectedRow, hover;
        boolean open;
        Runnable onClick;

        Row(Composite parent, boolean topic) {
            super(parent, SWT.NO_FOCUS | SWT.LEFT_TO_RIGHT | SWT.DOUBLE_BUFFERED);
            this.topic = topic;
            setCursor(getDisplay().getSystemCursor(SWT.CURSOR_HAND));
            addListener(SWT.Paint, e -> paint(e.gc));
            addListener(SWT.MouseEnter, e -> { hover = true; redraw(); });
            addListener(SWT.MouseExit, e -> { hover = false; redraw(); });
            addListener(SWT.MouseUp, e -> {
                if (e.button != 1) return;
                if (onClick != null) onClick.run();   // ה-V מסומן רק בחלון "השאלה" ("סיימתי את התרגיל") — כאן רק מוצג
            });
            if (!topic) setToolTipText("סימון ירוק — כבר עשית את התרגיל (מסמנים בחלון \"השאלה\": \"סיימתי את התרגיל\")");
        }

        void setSelected(boolean s) {
            selectedRow = s;
            redraw();
        }

        @Override
        public Point computeSize(int wHint, int hHint, boolean changed) {
            return new Point(wHint == SWT.DEFAULT ? 200 : wHint, topic ? TOPIC_H : Q_H);
        }

        private void paint(GC gc) {
            Display d = getDisplay();
            Point s = getSize();
            gc.setAntialias(SWT.ON);
            gc.setTextAntialias(SWT.ON);
            gc.setBackground(Look.color(d, Look.CARD));
            gc.fillRectangle(0, 0, s.x, s.y);
            if (topic) paintTopic(gc, d, s);
            else paintQuestion(gc, d, s);
        }

        private void paintTopic(GC gc, Display d, Point s) {
            BankStore.Topic t = (BankStore.Topic) getData();
            List<BankStore.Question> qs = store.of(t.id);
            int done = progress.doneCount(qs);
            if (hover) {
                gc.setBackground(Look.color(d, Look.SURFACE));
                gc.fillRoundRectangle(0, 2, s.x - 1, s.y - 4, 12, 12);
            }
            // חץ: פתוח ▾ / סגור ◂ (מימין לשמאל — סגור מצביע שמאלה, לכיוון התוכן)
            int cx = s.x - 12, cy = s.y / 2;
            gc.setBackground(Look.color(d, Look.INK2));
            if (open) gc.fillPolygon(new int[] { cx - 4, cy - 2, cx + 4, cy - 2, cx, cy + 3 });
            else gc.fillPolygon(new int[] { cx + 2, cy - 4, cx + 2, cy + 4, cx - 3, cy });
            // האייקון של הנושא
            org.eclipse.swt.graphics.Image img = icon(t.id);
            int iconX = s.x - 24 - 26;
            if (img != null) {
                gc.setInterpolation(SWT.HIGH);
                org.eclipse.swt.graphics.Rectangle b = img.getBounds();
                gc.drawImage(img, 0, 0, b.width, b.height, iconX, (s.y - 26) / 2, 26, 26);
            }
            gc.setFont(Look.font(d, 0, SWT.BOLD));
            String title = RtlText.rtl(t.title);
            Point e = gc.textExtent(title);
            gc.setForeground(Look.color(d, Look.TEXT));
            gc.drawString(title, iconX - 8 - e.x, (s.y - e.y) / 2, true);
            // כמה מתוך כמה, ופס התקדמות קטן לידו (רק כשיש שאלות)
            gc.setFont(Look.font(d, -2, SWT.BOLD));
            String count = qs.isEmpty() ? "—" : done + "/" + qs.size();
            Point c = gc.textExtent(count);
            boolean all = !qs.isEmpty() && done == qs.size();
            gc.setForeground(Look.color(d, all ? Look.OK : Look.INK2));
            gc.drawString(count, 8, (s.y - c.y) / 2, true);
            if (!qs.isEmpty()) {
                int bw = 44, bx = 8 + c.x + 8, by = s.y / 2 - 2;
                gc.setBackground(Look.color(d, 0xDDE2EA));
                gc.fillRoundRectangle(bx, by, bw, 5, 5, 5);
                if (done > 0) {
                    gc.setBackground(Look.color(d, Look.OK));
                    int fw = Math.max(5, bw * done / qs.size());
                    gc.fillRoundRectangle(bx + bw - fw, by, fw, 5, 5, 5);
                }
            }
        }

        private void paintQuestion(GC gc, Display d, Point s) {
            BankStore.Question q = (BankStore.Question) getData();
            boolean done = progress.done(q.id);
            int indent = 40;   // מוזח פנימה (שמאלה) מתחת לשם הנושא
            if (selectedRow || hover) {
                gc.setBackground(Look.color(d, selectedRow ? Look.ACCENT_SOFT : 0xF4F6F9));
                gc.fillRoundRectangle(0, 2, s.x - indent + 6, s.y - 4, 8, 8);
            }
            if (selectedRow) {   // פס כחול דק בקצה הימני של השורה
                gc.setBackground(Look.color(d, Look.ACCENT));
                gc.fillRectangle(s.x - indent + 3, 7, 3, s.y - 14);
            }
            // קו העץ: מהנושא אל השאלות
            gc.setForeground(Look.color(d, 0xDDE2EA));
            gc.drawLine(s.x - 30, 0, s.x - 30, s.y);
            // נקודת הקושי
            gc.setBackground(Look.color(d, q.level.color));
            gc.fillOval(s.x - indent - 12, s.y / 2 - 4, 8, 8);
            gc.setFont(Look.font(d, -2, selectedRow ? SWT.BOLD : SWT.NORMAL));
            String title = RtlText.rtl(q.title);
            Point e = gc.textExtent(title);
            int right = s.x - indent - 20, left = 32;
            gc.setForeground(Look.color(d, selectedRow ? Look.ACCENT_DARK : Look.TEXT));
            gc.setClipping(left, 0, Math.max(0, right - left), s.y);
            gc.drawString(title, Math.max(left, right - e.x), (s.y - e.y) / 2, true);
            gc.setClipping((org.eclipse.swt.graphics.Rectangle) null);
            if (e.x > right - left) setToolTipText(q.title);
            // עיגול ה-V
            int cx = 16, cy = s.y / 2, r = 8;
            if (done) {
                gc.setBackground(Look.color(d, Look.OK));
                gc.fillOval(cx - r, cy - r, 2 * r, 2 * r);
                gc.setForeground(Look.color(d, 0xFFFFFF));
                gc.setLineWidth(2);
                gc.setLineCap(SWT.CAP_ROUND);
                gc.drawPolyline(new int[] { cx - 4, cy, cx - 1, cy + 3, cx + 4, cy - 3 });
                gc.setLineWidth(1);
            } else {
                gc.setForeground(Look.color(d, hover ? 0x8C95A5 : 0xB9C1CE));
                gc.setLineWidth(2);
                gc.drawOval(cx - r, cy - r, 2 * r, 2 * r);
                gc.setLineWidth(1);
            }
        }
    }
}
