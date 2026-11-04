package il.lamerhav.dsviz;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.resources.IFile;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.ScrolledComposite;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Canvas;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IPartListener2;
import org.eclipse.ui.IViewPart;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.IWorkbenchPartReference;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.part.ViewPart;
import org.eclipse.ui.texteditor.ITextEditor;

/**
 * חלון הצד "השאלה" (מאגר תרגילים): הנוסח של השאלה שהתרגיל הפתוח נוצר ממנה — מימין לשמאל, סעיף אחרי סעיף, ליד הקוד.
 * עוקב אחרי העורך: עוברים לקובץ של תרגיל אחר — השאלה מתחלפת. בכל סעיף: "לפעולה" (קופץ לפעולה של הסעיף) ו-💡 הצעה לפתרון;
 * בסוף — מה main צריך להדפיס כשהכול פתור, ו"סיימתי" (V ירוק — אותו V כמו בחלון המאגר).
 */
public class BankView extends ViewPart {
    public static final String ID = "il.lamerhav.dsviz.bankView";

    private ScrolledComposite scroll;
    private Composite body;
    private String questionId;
    /** לבדיקות: הטקסט שמוצג כרגע (שורה לכל רכיב) */
    private final List<String> shown = new ArrayList<String>();
    private final Runnable onProgress = () -> Display.getDefault().asyncExec(() -> { if (body != null && !body.isDisposed()) render(); });

    private final IPartListener2 parts = new IPartListener2() {
        @Override
        public void partActivated(IWorkbenchPartReference ref) {
            follow(ref.getPart(false));
        }

        @Override
        public void partBroughtToTop(IWorkbenchPartReference ref) {
            follow(ref.getPart(false));
        }
    };

    /** פותח את החלון (בצד, ליד הקוד) ומציג בו את השאלה */
    static void show(IWorkbenchPage page, String questionId) {
        try {
            IViewPart v = page.showView(ID, null, IWorkbenchPage.VIEW_VISIBLE);
            if (v instanceof BankView) ((BankView) v).setQuestion(questionId);
            widen(page);
            org.eclipse.swt.widgets.Display.getCurrent().asyncExec(() -> widen(page));   // שוב אחרי הסידור (גודל אמיתי של החלון)
        } catch (PartInitException e) {
            // בלי החלון — התרגיל עצמו כבר נפתח
        }
    }

    /** רוחב שנוח לקרוא בו שאלה */
    private static final int WIDE = 400;

    /**
     * הערימה שבה החלון נמצא צרה מדי לקריאת שאלה (ב-perspective של Java הצד הימני צר) — מרחיבים אותה לכ-400 פיקסלים
     * (לא יותר משליש מרוחב החלון). רק מרחיבים, אף פעם לא מצמצמים — מי שהרחיב בעצמו נשאר עם הרוחב שלו.
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    static void widen(IWorkbenchPage page) {
        try {
            org.eclipse.e4.ui.workbench.modeling.EPartService ps = page.getWorkbenchWindow().getService(org.eclipse.e4.ui.workbench.modeling.EPartService.class);
            org.eclipse.e4.ui.model.application.ui.basic.MPart part = ps == null ? null : ps.findPart(ID);
            if (part == null) { dbg("no part"); return; }
            org.eclipse.e4.ui.model.application.ui.MUIElement el = part.getCurSharedRef() != null ? part.getCurSharedRef() : part;
            org.eclipse.e4.ui.model.application.ui.MElementContainer stack = el.getParent();
            if (!(stack instanceof org.eclipse.e4.ui.model.application.ui.basic.MPartStack)) { dbg("parent " + stack); return; }
            // עולים עד החלוקה האופקית הקרובה (הערימה יכולה להיות בתוך חלוקה אנכית — למשל Outline מעל חלון אחר)
            org.eclipse.e4.ui.model.application.ui.MUIElement node = (org.eclipse.e4.ui.model.application.ui.MUIElement) stack;
            org.eclipse.e4.ui.model.application.ui.MElementContainer sash = node.getParent();
            while (sash != null && !(sash instanceof org.eclipse.e4.ui.model.application.ui.basic.MPartSashContainer
                    && ((org.eclipse.e4.ui.model.application.ui.basic.MPartSashContainer) sash).isHorizontal())) {
                if (!(sash instanceof org.eclipse.e4.ui.model.application.ui.MUIElement)) return;
                node = (org.eclipse.e4.ui.model.application.ui.MUIElement) sash;
                sash = node.getParent();
            }
            if (sash == null) return;
            narrowLeft(node);
            stack = (org.eclipse.e4.ui.model.application.ui.MElementContainer) (Object) node;
            Object sw = ((org.eclipse.e4.ui.model.application.ui.MUIElement) stack).getWidget(), pw = ((org.eclipse.e4.ui.model.application.ui.MUIElement) sash).getWidget();
            // ב-e4 לחלוקה (sash) אין רכיב משלה — רק מלבן; לערימה — CTabFolder
            int width = widthOf(sw), total = widthOf(pw);
            dbg("node=" + node + " width=" + width + " total=" + total);
            if (width <= 0 || total <= 0 || width >= WIDE || total < 3 * WIDE / 2) return;
            // המשקלים של החלקים לפי הרוחב האמיתי שלהם (לאזור העורך לפעמים אין משקל רשום); לערימה שלנו — הרוחב הרצוי,
            // וההפרש יורד מהרחב ביותר (בדרך כלל העורך)
            int want = Math.min(WIDE, total / 3), widest = -1, widestW = -1;
            java.util.List<org.eclipse.e4.ui.model.application.ui.MUIElement> kids = new java.util.ArrayList<org.eclipse.e4.ui.model.application.ui.MUIElement>();
            java.util.List<Integer> widths = new java.util.ArrayList<Integer>();
            for (Object o : sash.getChildren()) {
                org.eclipse.e4.ui.model.application.ui.MUIElement s = (org.eclipse.e4.ui.model.application.ui.MUIElement) o;
                int w = widthOf(s.getWidget());
                if (!s.isToBeRendered() || !s.isVisible() || w <= 0) continue;
                if (s == stack) w = want;
                else if (w > widestW) { widestW = w; widest = kids.size(); }
                kids.add(s);
                widths.add(w);
            }
            dbg("widths=" + widths);
            if (widest < 0) return;
            widths.set(widest, Math.max(200, widths.get(widest) - (want - width)));
            for (int i = 0; i < kids.size(); i++) kids.get(i).setContainerData(String.valueOf(Math.max(1, widths.get(i))));
            if (sw instanceof Control) {
                Control c = (Control) sw;
                c.getShell().layout(true, true);
            }
        } catch (Throwable ignored) {
            dbg("error " + ignored);
            // מבנה חלונות אחר — נשאר ברוחב הקיים
        }
    }

    /** רוחב העמודה השמאלית (Package Explorer) אחרי הסידור */
    private static final int LEFT = 220;

    /**
     * העמודה משמאל (Package Explorer) צרה יותר — המקום עובר לקוד ולחלון השאלה. עולים מהערימה שלנו לחלוקות האופקיות שמעליה,
     * ובכל אחת מצמצמים חלק שנמצא משמאל לחלק שלנו, שאין בו את העורך, ורחב מ-LEFT.
     */
    @SuppressWarnings({ "rawtypes" })
    private static void narrowLeft(org.eclipse.e4.ui.model.application.ui.MUIElement from) {
        org.eclipse.e4.ui.model.application.ui.MUIElement mine = from;
        for (org.eclipse.e4.ui.model.application.ui.MElementContainer p = mine.getParent(); p != null; p = mine.getParent()) {
            if (p instanceof org.eclipse.e4.ui.model.application.ui.basic.MPartSashContainer
                    && ((org.eclipse.e4.ui.model.application.ui.basic.MPartSashContainer) p).isHorizontal()) {
                java.util.List kids = p.getChildren();
                int myIndex = kids.indexOf(mine), myW = widthOf(mine.getWidget());
                for (int i = 0; i < myIndex && myW > 0; i++) {
                    org.eclipse.e4.ui.model.application.ui.MUIElement k = (org.eclipse.e4.ui.model.application.ui.MUIElement) kids.get(i);
                    int w = widthOf(k.getWidget());
                    if (!k.isToBeRendered() || !k.isVisible() || w <= LEFT || hasEditor(k)) continue;
                    // משקלים לפי פיקסלים: החלק משמאל — LEFT, ההפרש — לחלק שלנו
                    for (Object o : kids) {
                        org.eclipse.e4.ui.model.application.ui.MUIElement e = (org.eclipse.e4.ui.model.application.ui.MUIElement) o;
                        int ew = widthOf(e.getWidget());
                        if (ew <= 0) continue;
                        if (e == k) ew = LEFT;
                        else if (e == mine) ew = myW + (w - LEFT);
                        e.setContainerData(String.valueOf(ew));
                    }
                    dbg("left " + w + " -> " + LEFT);
                    myW += w - LEFT;
                }
            }
            if (!(p instanceof org.eclipse.e4.ui.model.application.ui.MUIElement)) break;
            mine = (org.eclipse.e4.ui.model.application.ui.MUIElement) p;
        }
    }

    /** האם בחלק הזה נמצא אזור העורכים */
    @SuppressWarnings("rawtypes")
    private static boolean hasEditor(org.eclipse.e4.ui.model.application.ui.MUIElement e) {
        if ("org.eclipse.ui.editorss".equals(e.getElementId())) return true;
        if (e instanceof org.eclipse.e4.ui.model.application.ui.advanced.MPlaceholder) {
            org.eclipse.e4.ui.model.application.ui.MUIElement r = ((org.eclipse.e4.ui.model.application.ui.advanced.MPlaceholder) e).getRef();
            return r != null && hasEditor(r);
        }
        if (e instanceof org.eclipse.e4.ui.model.application.ui.MElementContainer)
            for (Object o : ((org.eclipse.e4.ui.model.application.ui.MElementContainer) e).getChildren())
                if (hasEditor((org.eclipse.e4.ui.model.application.ui.MUIElement) o)) return true;
        return false;
    }

    /** רוחב של רכיב במודל של e4: Control — הגודל שלו; חלוקה (sash) — המלבן שלה */
    private static int widthOf(Object widget) {
        if (widget instanceof Control) return ((Control) widget).getSize().x;
        if (widget instanceof org.eclipse.swt.graphics.Rectangle) return ((org.eclipse.swt.graphics.Rectangle) widget).width;
        return -1;
    }

    private static void dbg(String s) {
        if (Boolean.getBoolean("dsviz.debugLayout")) System.err.println("WIDEN " + s);
    }

    @Override
    public void createPartControl(Composite parent) {
        Display d = parent.getDisplay();
        scroll = new ScrolledComposite(parent, SWT.V_SCROLL | SWT.RIGHT_TO_LEFT);
        scroll.setExpandHorizontal(true);
        scroll.setExpandVertical(true);
        scroll.setBackground(Look.color(d, Look.BG));
        body = new Composite(scroll, SWT.RIGHT_TO_LEFT);
        body.setBackground(Look.color(d, Look.BG));
        body.setBackgroundMode(SWT.INHERIT_DEFAULT);
        GridLayout gl = new GridLayout(1, false);
        gl.marginWidth = 10;
        gl.marginHeight = 10;
        gl.verticalSpacing = 8;
        body.setLayout(gl);
        scroll.setContent(body);
        scroll.addListener(SWT.Resize, e -> relayout());
        getSite().getPage().addPartListener(parts);
        BankProgress.get().listen(onProgress);
        IEditorPart active = getSite().getPage().getActiveEditor();
        if (active != null) follow(active);
        render();
    }

    @Override
    public void dispose() {
        getSite().getPage().removePartListener(parts);
        BankProgress.get().unlisten(onProgress);
        super.dispose();
    }

    @Override
    public void setFocus() {
        if (scroll != null && !scroll.isDisposed()) scroll.setFocus();
    }

    /** עוברים לעורך: אם הקובץ שלו נוצר משאלה במאגר — מציגים אותה (קובץ אחר — השאלה האחרונה נשארת) */
    private void follow(IWorkbenchPart part) {
        if (!(part instanceof ITextEditor)) return;
        IFile f = ((ITextEditor) part).getEditorInput().getAdapter(IFile.class);
        if (f == null) return;
        String id = BankProgress.get().questionOf(f.getFullPath().toString());
        if (id != null && !id.equals(questionId)) setQuestion(id);
    }

    void setQuestion(String id) {
        questionId = id;
        render();
    }

    /** לבדיקות: השאלה המוצגת, והטקסטים בחלון */
    public String questionForTest() {
        return questionId;
    }

    public List<String> shownForTest() {
        return new ArrayList<String>(shown);
    }

    // ------------------------------------------------------------------ ציור

    private void render() {
        if (body == null || body.isDisposed()) return;
        for (Control c : body.getChildren()) c.dispose();
        shown.clear();
        Display d = body.getDisplay();
        BankStore.Question q = questionId == null ? null : BankStore.load().find(questionId);
        if (q == null) {
            Label l = text(body, "פותחים תרגיל מ\"מאגר תרגילים\" — והשאלה תופיע כאן, ליד הקוד.", -1, SWT.NORMAL, Look.MUTED);
            l.setLayoutData(wide());
            relayout();
            return;
        }
        BankStore.Topic t = BankStore.load().topic(q.topic);
        BankQuestionPanel.titleRow(body, q, t == null ? null : t.title, shown);
        BankQuestionPanel.build(body, q, new BankQuestionPanel.Actions() {
            @Override
            public void jump(AiScaffold.Task task) {
                BankView.this.jump(task);
            }

            @Override
            public void solution(AiScaffold.Task task) {
                BankView.this.solution(task);
            }
        }, shown);
        // סיימתי
        final boolean done = BankProgress.get().done(q.id);
        final String qid = q.id;
        Composite bar = new Composite(body, SWT.RIGHT_TO_LEFT);
        GridLayout bl = new GridLayout(1, false);
        bl.marginWidth = 0;
        bl.marginHeight = 6;
        bar.setLayout(bl);
        bar.setLayoutData(wide());
        DoneButton db = new DoneButton(bar, done, () -> BankProgress.get().setDone(qid, !done));
        db.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
        shown.add("DONE " + done);
        relayout();
        // אחרי ש-Eclipse מחיל את ערכת הנושא על הרכיבים החדשים — הצבעים שלנו חוזרים
        Display disp = body.getDisplay();
        disp.asyncExec(() -> BankQuestionPanel.restoreColors(body, Look.BG));
        disp.timerExec(400, () -> BankQuestionPanel.restoreColors(body, Look.BG));
    }

    private Label text(Composite parent, String s, int delta, int style, int rgb) {
        Label l = new Label(parent, SWT.WRAP | SWT.RIGHT_TO_LEFT);
        l.setText(RtlText.rtl(s));
        l.setFont(Look.font(parent.getDisplay(), delta, style));
        l.setForeground(Look.color(parent.getDisplay(), rgb));
        shown.add("TEXT " + s.replace("\n", " | "));
        return l;
    }

    private static GridData wide() {
        return new GridData(SWT.FILL, SWT.TOP, true, false);
    }

    private static GridData fill() {
        GridData g = new GridData(SWT.FILL, SWT.TOP, true, false);
        g.widthHint = 120;   // רוחב מינימלי — כדי שהשורות יישברו לפי רוחב החלון ולא יגדילו אותו
        return g;
    }

    private void relayout() {
        if (scroll == null || scroll.isDisposed()) return;
        int w = Math.max(1, scroll.getClientArea().width);
        body.setSize(body.computeSize(w, SWT.DEFAULT));
        scroll.setMinHeight(body.computeSize(w, SWT.DEFAULT).y);
        body.layout(true, true);
    }

    // ------------------------------------------------------------------ פעולות

    /** הקובץ (מהתרגיל האחרון שנוצר מהשאלה) שבו המחלקה של הסעיף */
    private IFile fileOf(AiScaffold.Task task) {
        List<BankProgress.Created> all = BankProgress.get().createdOf(questionId);
        if (all.isEmpty()) return null;
        for (String p : all.get(all.size() - 1).files) {
            if (p.endsWith("/" + task.className + ".java")) {
                org.eclipse.core.resources.IResource r = org.eclipse.core.resources.ResourcesPlugin.getWorkspace().getRoot().findMember(p);
                if (r instanceof IFile) return (IFile) r;
            }
        }
        return null;
    }

    /** "לפעולה": פותח את הקובץ ושם את הסמן בתחילת הפעולה של הסעיף */
    private void jump(AiScaffold.Task task) {
        IFile f = fileOf(task);
        if (f == null) {
            Say.info(getSite().getShell(), "השאלה", "הקובץ של התרגיל לא נמצא (אולי נמחק). פותחים שוב את השאלה מ\"מאגר תרגילים\".");
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
            IEditorPart ed = org.eclipse.ui.ide.IDE.openEditor(getSite().getPage(), f);
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
            Say.info(getSite().getShell(), "הצעה לפתרון", "ההצעה לפתרון של הסעיף הזה לא נמצאה. פותחים שוב את השאלה מ\"מאגר תרגילים\".");
            return;
        }
        SolutionDialog.show(getSite().getShell(), e);
    }

    // ------------------------------------------------------------------ "סיימתי"

    /** כפתור רחב: לפני — "סיימתי את התרגיל" (מסגרת ירוקה); אחרי — רקע ירוק עם V ו"עשיתי ✓" (לחיצה מבטלת) */
    private static final class DoneButton extends Canvas {
        private final boolean done;
        private boolean hover;

        DoneButton(Composite parent, boolean done, Runnable toggle) {
            super(parent, SWT.NO_FOCUS | SWT.LEFT_TO_RIGHT | SWT.DOUBLE_BUFFERED);
            this.done = done;
            setCursor(getDisplay().getSystemCursor(SWT.CURSOR_HAND));
            setToolTipText(done ? "לחיצה מבטלת את הסימון" : "מסמנים V ירוק — וגם בחלון המאגר רואים שעשיתם את התרגיל");
            addListener(SWT.Paint, e -> paint(e.gc));
            addListener(SWT.MouseEnter, e -> { hover = true; redraw(); });
            addListener(SWT.MouseExit, e -> { hover = false; redraw(); });
            addListener(SWT.MouseUp, e -> { if (e.button == 1) toggle.run(); });
        }

        @Override
        public Point computeSize(int wHint, int hHint, boolean changed) {
            return new Point(wHint == SWT.DEFAULT ? 200 : wHint, 40);
        }

        private void paint(GC gc) {
            Display d = getDisplay();
            Point s = getSize();
            gc.setAntialias(SWT.ON);
            gc.setBackground(getParent().getBackground());
            gc.fillRectangle(0, 0, s.x, s.y);
            gc.setBackground(Look.color(d, done ? Look.OK : hover ? 0xEAF7EF : Look.CARD));
            gc.fillRoundRectangle(0, 0, s.x - 1, s.y - 1, 16, 16);
            gc.setForeground(Look.color(d, Look.OK));
            gc.setLineWidth(done ? 1 : 2);
            gc.drawRoundRectangle(1, 1, s.x - 3, s.y - 3, 16, 16);
            String text = RtlText.rtl(done ? "עשיתי את התרגיל" : "סיימתי את התרגיל — סמנו V");
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
