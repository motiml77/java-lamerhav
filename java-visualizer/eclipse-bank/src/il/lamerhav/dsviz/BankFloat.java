package il.lamerhav.dsviz;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.ScrolledComposite;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Canvas;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IPartListener2;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.IWorkbenchPartReference;
import org.eclipse.ui.texteditor.ITextEditor;

/**
 * "השאלה" — חלון צף (לא תקוע ליד הקוד ולא נלחץ עם חלון השגיאות): הנוסח של השאלה מהמאגר, עם גלילה, ציורים של המבנים,
 * "לפעולה" ו-💡 לכל סעיף, הפלט הרצוי, ובסוף "סיימתי" — V ירוק, והקבצים של התרגיל נמחקים (BankHandler.complete).
 * סוגרים ב-X ופותחים שוב בכפתור "השאלה" בסרגל. החלון זוכר את המקום והגודל שלו, ומתחלף לבד כשעוברים לקובץ של תרגיל אחר מהמאגר.
 */
final class BankFloat {
    private static BankFloat current;
    /** המקום והגודל האחרונים (בזמן ש-Eclipse פתוח) */
    private static Rectangle lastBounds;

    private final IWorkbenchPage page;
    private final Shell shell;
    private final ScrolledComposite scroll;
    private final Composite body;
    private String questionId;
    final List<String> shown = new ArrayList<String>();
    private final Runnable onProgress;
    private final IPartListener2 parts = new IPartListener2() {
        @Override
        public void partActivated(IWorkbenchPartReference ref) {
            follow(ref.getPart(false));
        }
    };

    /** פותח (או מביא לקדמה) את החלון עם השאלה */
    static BankFloat show(IWorkbenchPage page, String questionId) {
        if (current == null || current.shell.isDisposed()) current = new BankFloat(page);
        current.setQuestion(questionId);
        current.shell.setVisible(true);
        current.shell.setActive();
        return current;
    }

    /** החלון הפתוח, או null */
    static BankFloat current() {
        return current == null || current.shell.isDisposed() ? null : current;
    }

    /** השאלה של הקובץ שפתוח בעורך (אם נוצר מהמאגר), או null */
    static String questionOfEditor(IWorkbenchPage page) {
        IEditorPart ed = page == null ? null : page.getActiveEditor();
        IFile f = ed == null ? null : ed.getEditorInput().getAdapter(IFile.class);
        return f == null ? null : BankProgress.get().questionOf(f.getFullPath().toString());
    }

    private BankFloat(IWorkbenchPage page) {
        this.page = page;
        Shell parent = page.getWorkbenchWindow().getShell();
        Display d = parent.getDisplay();
        shell = new Shell(parent, SWT.DIALOG_TRIM | SWT.RESIZE | SWT.MODELESS | SWT.RIGHT_TO_LEFT);
        shell.setText("השאלה");
        shell.setData("dsviz.bankFloat", Boolean.TRUE);
        shell.setBackground(Look.color(d, Look.BG));
        shell.setBackgroundMode(SWT.INHERIT_DEFAULT);
        GridLayout sl = new GridLayout(1, false);
        sl.marginWidth = 0;
        sl.marginHeight = 0;
        shell.setLayout(sl);
        GridLayout bl = new GridLayout(1, false);
        bl.marginWidth = 12;
        bl.marginHeight = 12;
        bl.verticalSpacing = 8;
        Composite[] c = new Composite[1];
        scroll = Look.scroll(shell, bl, c);
        body = c[0];
        body.setOrientation(SWT.RIGHT_TO_LEFT);
        // מקום: האחרון, ואם אין — בצד ימין של חלון Eclipse, בערך שליש מהרוחב
        Rectangle pb = parent.getBounds();
        Rectangle b = lastBounds != null ? lastBounds
                : new Rectangle(pb.x + pb.width - Math.min(540, pb.width / 3) - 30, pb.y + 110, Math.min(540, Math.max(420, pb.width / 3)), Math.min(720, pb.height - 160));
        shell.setBounds(b);
        shell.addListener(SWT.Move, e -> lastBounds = shell.getBounds());
        shell.addListener(SWT.Resize, e -> {
            lastBounds = shell.getBounds();
            Look.updateScroll(scroll);
        });
        onProgress = () -> d.asyncExec(() -> { if (!body.isDisposed()) render(); });
        BankProgress.get().listen(onProgress);
        page.addPartListener(parts);
        shell.addListener(SWT.Dispose, e -> {
            BankProgress.get().unlisten(onProgress);
            page.removePartListener(parts);
        });
        shell.open();
    }

    private void follow(IWorkbenchPart part) {
        if (shell.isDisposed() || !(part instanceof ITextEditor)) return;
        String id = questionOfEditor(page);
        if (id != null && !id.equals(questionId)) setQuestion(id);
    }

    void setQuestion(String id) {
        questionId = id;
        render();
    }

    Shell shell() {
        return shell;
    }

    String question() {
        return questionId;
    }

    // ------------------------------------------------------------------ ציור

    private void render() {
        if (body.isDisposed()) return;
        body.setRedraw(false);
        try {
            for (Control k : body.getChildren()) k.dispose();
            shown.clear();
            BankStore store = BankStore.load();
            BankStore.Question q = questionId == null ? null : store.find(questionId);
            if (q == null) {
                Composite card = Look.card(body, BankQuestionPanel.wide());
                BankQuestionPanel.text(card, "פותחים תרגיל מ\"מאגר תרגילים\" — והשאלה תופיע כאן.", 0, SWT.NORMAL, Look.MUTED, shown);
            } else {
                shell.setText("השאלה — " + q.title);
                BankStore.Topic t = store.topic(q.topic);
                BankQuestionPanel.titleRow(body, q, t == null ? null : t.title, shown);
                BankQuestionPanel.build(body, q, new BankQuestionPanel.Actions() {
                    @Override
                    public void jump(AiScaffold.Task task) {
                        BankFloat.this.jump(task);
                    }

                    @Override
                    public void solution(AiScaffold.Task task) {
                        BankFloat.this.solution(task);
                    }
                }, shown);
                final boolean done = BankProgress.get().done(q.id);
                DoneButton db = new DoneButton(body, done, () -> {
                    if (done) BankProgress.get().setDone(q.id, false);
                    else if (BankHandler.complete(shell, q, true)) shell.dispose();   // סיימתי: V, הקבצים נמחקו — החלון נסגר
                });
                GridData dg = new GridData(SWT.FILL, SWT.TOP, true, false);
                dg.verticalIndent = 4;
                db.setLayoutData(dg);
                shown.add("DONE " + done);
            }
            body.layout(true, true);
            Look.updateScroll(scroll);
            Look.wheel(scroll);
        } finally {
            body.setRedraw(true);
        }
        Display d = body.getDisplay();
        d.asyncExec(() -> BankQuestionPanel.restoreColors(body, Look.BG));
        d.timerExec(400, () -> BankQuestionPanel.restoreColors(body, Look.BG));
    }

    // ------------------------------------------------------------------ פעולות

    /** הקובץ (מהתרגיל האחרון שנוצר מהשאלה) שבו המחלקה של הסעיף */
    private IFile fileOf(AiScaffold.Task task) {
        List<BankProgress.Created> all = BankProgress.get().createdOf(questionId);
        for (int i = all.size() - 1; i >= 0; i--) {
            for (String p : all.get(i).files) {
                if (!p.endsWith("/" + task.className + ".java")) continue;
                IResource r = ResourcesPlugin.getWorkspace().getRoot().findMember(p);
                if (r instanceof IFile) return (IFile) r;
            }
        }
        return null;
    }

    /** "לפעולה": פותח את הקובץ ושם את הסמן בתחילת הפעולה של הסעיף */
    private void jump(AiScaffold.Task task) {
        IFile f = fileOf(task);
        if (f == null) {
            Say.info(shell, "השאלה", "הקובץ של התרגיל לא נמצא (אולי נמחק). יוצרים אותו שוב מ\"מאגר תרגילים\".");
            return;
        }
        try {
            ICompilationUnit cu = JavaCore.createCompilationUnitFrom(f);
            IType type = cu.getType(task.className);
            int offset = 0;
            if (type.exists()) {
                for (IMethod m : type.getMethods()) {
                    if (m.getElementName().equals(task.methodName) && m.getParameterTypes().length == task.params.size()) {
                        offset = m.getNameRange().getOffset();
                        break;
                    }
                }
            }
            IEditorPart ed = org.eclipse.ui.ide.IDE.openEditor(page, f);
            if (ed instanceof ITextEditor) ((ITextEditor) ed).selectAndReveal(offset, 0);
        } catch (Exception e) {
            // בלי קפיצה — לפחות הקובץ
        }
    }

    /** 💡: אותו חלון "הצעה לפתרון" כמו מעל הפעולה בעורך */
    private void solution(AiScaffold.Task task) {
        IFile f = fileOf(task);
        SolutionStore.Entry e = f == null ? null : SolutionMinings.store().find(f.getFullPath().toString(), task.className, task.methodName, task.params.size());
        if (e == null) {
            Say.info(shell, "הצעה לפתרון", "ההצעה לפתרון של הסעיף הזה לא נמצאה. יוצרים שוב את התרגיל מ\"מאגר תרגילים\".");
            return;
        }
        SolutionDialog.show(shell, e);
    }

    // ------------------------------------------------------------------ "סיימתי"

    /** כפתור רחב: לפני — "סיימתי את התרגיל" (מסגרת ירוקה); אחרי — רקע ירוק עם V ו"עשיתי" (לחיצה מבטלת את הסימון) */
    private static final class DoneButton extends Canvas {
        private final boolean done;
        private boolean hover;

        DoneButton(Composite parent, boolean done, Runnable toggle) {
            super(parent, SWT.NO_FOCUS | SWT.LEFT_TO_RIGHT | SWT.DOUBLE_BUFFERED);
            this.done = done;
            setCursor(getDisplay().getSystemCursor(SWT.CURSOR_HAND));
            setToolTipText(done ? "לחיצה מבטלת את הסימון" : "סימון ירוק — והקבצים של התרגיל נמחקים, כדי שהפרויקט לא יתמלא בתרגילים ישנים");
            addListener(SWT.Paint, e -> paint(e.gc));
            addListener(SWT.MouseEnter, e -> { hover = true; redraw(); });
            addListener(SWT.MouseExit, e -> { hover = false; redraw(); });
            addListener(SWT.MouseUp, e -> { if (e.button == 1) toggle.run(); });
        }

        @Override
        public Point computeSize(int wHint, int hHint, boolean changed) {
            return new Point(wHint == SWT.DEFAULT ? 200 : wHint, 44);
        }

        private void paint(GC gc) {
            Display d = getDisplay();
            Point s = getSize();
            gc.setAntialias(SWT.ON);
            gc.setTextAntialias(SWT.ON);
            gc.setBackground(getParent().getBackground());
            gc.fillRectangle(0, 0, s.x, s.y);
            gc.setBackground(Look.color(d, done ? Look.OK : hover ? 0xEAF7EF : Look.CARD));
            gc.fillRoundRectangle(0, 0, s.x - 1, s.y - 1, 16, 16);
            gc.setForeground(Look.color(d, Look.OK));
            gc.setLineWidth(done ? 1 : 2);
            gc.drawRoundRectangle(1, 1, s.x - 3, s.y - 3, 16, 16);
            String text = RtlText.rtl(done ? "עשיתי את התרגיל" : "סיימתי את התרגיל");
            gc.setFont(Look.font(d, 0, SWT.BOLD));
            Point e = gc.textExtent(text);
            int iconW = 20, total = e.x + iconW + 8, x = (s.x - total) / 2;
            int cx = x + total - iconW / 2, cy = s.y / 2;
            gc.setForeground(Look.color(d, done ? 0xFFFFFF : Look.OK));
            gc.setLineWidth(3);
            gc.setLineCap(SWT.CAP_ROUND);
            gc.drawPolyline(new int[] { cx - 7, cy, cx - 2, cy + 5, cx + 7, cy - 5 });
            gc.setLineWidth(1);
            gc.drawString(text, x, (s.y - e.y) / 2, true);
        }
    }
}
