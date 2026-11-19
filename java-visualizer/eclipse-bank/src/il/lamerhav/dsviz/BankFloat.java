package il.lamerhav.dsviz;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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
import org.eclipse.swt.widgets.Button;
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
    /** "סיימתי את התרגיל" נלחץ: הנוסח של האישור (מוצג בחלון במקום השאלה), או null */
    private String confirm;
    /** בדף האישור: התלמיד בחר לשמור את קבצי התרגיל (ברירת המחדל — מחיקה) */
    private boolean keepFiles;
    /** "הגשה למורה": הדף מוצג במקום השאלה (null — לא מגישים עכשיו) */
    private SubmitState submit;
    /** אחרי הגשה: "הוגש למורה ב-10:42" — מוצג מעל "סיימתי את התרגיל" */
    private String submitNote;
    /** לבדיקות: איך הסתיימה ההגשה האחרונה (ok / queued / קוד השגיאה); null — עוד לא הייתה מאז שדף ההגשה נפתח */
    volatile String submitResult;
    /** התוצאה של ההגשה האחרונה גם אחרי שהחלון נסגר ("הגשה וסיום" סוגר אותו) — לבדיקות */
    static volatile String lastSubmitResult;

    /** מצב הדף "הגשה למורה" */
    static final class SubmitState {
        /** קובצי התרגיל (שם → תוכן), כמו שהיו כשהדף נפתח (אחרי שמירת העורכים) */
        Map<String, String> files = new java.util.LinkedHashMap<String, String>();
        String mainClass = "";
        /** תוצאת בדיקת ההרצה; null — הבדיקה עוד רצה */
        RunCheck.Result check;
        /** "שם המגיש" (בעמדת מחשב) — ריק בכל הגשה חדשה */
        String who = "";
        /** הודעה מהשרת כשההגשה לא עברה */
        String error;
        boolean busy;
        /** "סיימתי את התרגיל": אחרי ההגשה מסמנים ומוחקים (keep — שומרים את הקבצים) */
        boolean thenComplete, keep;
    }
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
        // הפוקוס נשאר איפה שהיה (העורך): החלון "השאלה" רק מוצג — כך ההקלדה לא עוברת אליו (ולא מתחלפת לעברית)
        Control focus = page.getWorkbenchWindow().getShell().getDisplay().getFocusControl();
        if (current == null || current.shell.isDisposed()) current = new BankFloat(page);
        current.setQuestion(questionId);
        current.reveal();
        if (focus != null && !focus.isDisposed() && focus.getShell() != current.shell) focus.setFocus();
        BankQuestionSource.refresh();
        return current;
    }

    /**
     * מביא את החלון לקדמה: אם מוזער — משחזר; אם הוא מחוץ למסך (או קטן מדי, כמו שחלון ממוזער מדווח בחלק מהמערכות) — מחזיר לצד ימין של Eclipse.
     * מקום שהמשתמש בחר (בתוך המסך) נשמר.
     */
    private void reveal() {
        if (shell.getMinimized()) shell.setMinimized(false);
        if (!usable(shell.getBounds())) {
            lastBounds = null;
            shell.setBounds(defaultBounds(shell.getParent().getBounds()));
        }
        shell.setVisible(true);
        shell.moveAbove(null);   // לקדמה, בלי לקחת את הפוקוס מהעורך
    }

    /** ברירת המחדל: בצד ימין של חלון Eclipse, בערך שליש מהרוחב */
    private static Rectangle defaultBounds(Rectangle pb) {
        return new Rectangle(pb.x + pb.width - Math.min(540, pb.width / 3) - 30, pb.y + 110, Math.min(540, Math.max(420, pb.width / 3)), Math.min(720, pb.height - 160));
    }

    /** מקום סביר לחלון: גודל מינימלי, ולפחות חלק ממנו נראה באחד המסכים */
    private static boolean usable(Rectangle r) {
        if (r == null || r.width < 200 || r.height < 200) return false;
        for (org.eclipse.swt.widgets.Monitor m : Display.getDefault().getMonitors()) {
            Rectangle i = m.getClientArea().intersection(r);
            if (i.width >= 150 && i.height >= 100) return true;
        }
        return false;
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
        shell = new Shell(parent, SWT.DIALOG_TRIM | SWT.MIN | SWT.RESIZE | SWT.MODELESS | SWT.RIGHT_TO_LEFT);   // אפשר למזער
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
        Rectangle b = usable(lastBounds) ? lastBounds : defaultBounds(pb);
        shell.setBounds(b);
        shell.addListener(SWT.Move, e -> remember());
        shell.addListener(SWT.Resize, e -> {
            remember();
            Look.updateScroll(scroll);
        });
        onProgress = () -> d.asyncExec(() -> { if (!body.isDisposed()) render(); });
        BankProgress.get().listen(onProgress);
        AccessGate.onChange(onProgress);   // תלמיד שהצטרף למורה / יצא ממנו — הכפתור "הגשה למורה" מופיע / נעלם מיד
        page.addPartListener(parts);
        shell.addListener(SWT.Dispose, e -> {
            BankProgress.get().unlisten(onProgress);
            AccessGate.offChange(onProgress);
            page.removePartListener(parts);
            if (current == BankFloat.this) current = null;
            BankQuestionSource.refresh();
        });
        shell.setVisible(true);   // לא open(): open גם מפעיל את החלון ולוקח את הפוקוס מהעורך
    }

    /** זוכר את המקום האחרון — לא כשהחלון ממוזער (שם המערכת מדווחת על מקום שאינו אמיתי) */
    private void remember() {
        if (shell.isDisposed() || shell.getMinimized()) return;
        Rectangle r = shell.getBounds();
        if (usable(r)) lastBounds = r;
    }

    private void follow(IWorkbenchPart part) {
        if (shell.isDisposed() || !(part instanceof ITextEditor)) return;
        String id = questionOfEditor(page);
        if (id != null && !id.equals(questionId)) setQuestion(id);
    }

    void setQuestion(String id) {
        if (!id.equals(questionId)) {
            confirm = null;
            submit = null;
            submitNote = null;
        }
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
                if (submit != null) submitCard(q, submit);
                else if (confirm != null) confirmCard(q);
                else {
                final int ready = BankHandler.prepared(q);   // כמה סעיפים כבר בקוד
                shown.add("PREPARED " + ready);
                BankQuestionPanel.build(body, q, new BankQuestionPanel.Actions() {
                    @Override
                    public void jump(AiScaffold.Task task) {
                        BankFloat.this.jump(task);
                    }

                    @Override
                    public void solution(AiScaffold.Task task) {
                        BankFloat.this.solution(task);
                    }

                    @Override
                    public int prepared() {
                        return ready;
                    }

                    @Override
                    public void prepare(AiScaffold.Task task) {
                        BankFloat.this.prepare(q, task);
                    }
                }, shown);
                // "הגשה למורה" — רק למי שיש לו למי להגיש (תלמיד שמשויך למורה, או עמדת מחשב של הכיתה), וכשיש תרגיל
                if (canSubmit(q)) {
                    Look.Round send = new Look.Round(body, Submit.label(), false, () -> openSubmit(q, false, false));
                    send.setData("dsviz.id", "submit-open");
                    GridData sg = new GridData(SWT.FILL, SWT.TOP, true, false);
                    sg.verticalIndent = 6;
                    send.setLayoutData(sg);
                    shown.add("SUBMIT-BUTTON " + Submit.label());
                    // מה שהוגש עכשיו, ואם אין — מה שנרשם בהגשה קודמת של התלמיד (גם אחרי שסגרו את התוכנה)
                    String note = submitNote != null ? submitNote : "student".equals(Submit.mode()) ? BankProgress.get().submittedNote(q.id, Submit.teacher()) : "";
                    if (!note.isEmpty()) {
                        BankQuestionPanel.text(body, note, -1, SWT.NORMAL, Look.OK, null);
                        shown.add("SUBMIT-NOTE " + note);
                    }
                } else if (joinHint(q)) {
                    // תלמיד שעוד לא הצטרף למורה: במקום שבו היה מופיע "הגשה למורה" — הצטרפות עם קוד המורה (החלון "המורה שלי")
                    Look.Round join = new Look.Round(body, "רוצים להגיש למורה? מצטרפים עם קוד המורה", false, () -> AccessGate.showMyTeacher(null));
                    join.setData("dsviz.id", "submit-join");
                    GridData jg = new GridData(SWT.FILL, SWT.TOP, true, false);
                    jg.verticalIndent = 6;
                    join.setLayoutData(jg);
                    shown.add("SUBMIT-JOIN");
                }
                if (submitResult != null) shown.add("SUBMIT-RESULT " + submitResult);
                final boolean done = BankProgress.get().done(q.id);
                DoneButton db = new DoneButton(body, done, () -> {
                    if (done) BankProgress.get().setDone(q.id, false);
                    else {
                        // סיימתי: שואלים בתוך החלון עצמו (במקום השאלה) — בלי חלון נוסף שמכסה אותו
                        String text = BankHandler.doneText(q);
                        if (text == null) {
                            if (BankHandler.complete(shell, q, false)) shell.dispose();
                        } else {
                            confirm = text;
                            keepFiles = false;
                            render();
                        }
                    }
                });
                GridData dg = new GridData(SWT.FILL, SWT.TOP, true, false);
                dg.verticalIndent = 4;
                db.setLayoutData(dg);
                shown.add("DONE " + done);
                }
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

    /**
     * "סיימתי את התרגיל": האישור בכרטיס בתוך החלון — מה לעשות עם קבצי התרגיל: למחוק (ברירת המחדל — כדי שלא יצטברו תרגילים ישנים)
     * או לשמור; מה יימחק; "סיום ומחיקה" / "סיום ושמירת הקבצים" / "ביטול"
     */
    private void confirmCard(final BankStore.Question q) {
        Composite card = Look.card(body, BankQuestionPanel.wide());
        BankQuestionPanel.text(card, "סיימתי את התרגיל", 1, SWT.BOLD, Look.TEXT, shown);
        BankQuestionPanel.text(card, "השאלה תסומן בסימן ירוק. מה לעשות עם קבצי התרגיל?", 0, SWT.NORMAL, Look.TEXT, shown);
        if (canSubmit(q)) {   // "סיימתי" מגיש למורה לפני המחיקה
            BankQuestionPanel.text(card, "לפני הסיום התרגיל יוגש " + (Submit.teacher().isEmpty() ? "למורה" : "למורה " + Submit.teacher()) + ".", 0, SWT.BOLD, Look.ACCENT, shown);
            shown.add("DONE-SUBMITS");
        }
        Composite choice = new Composite(card, SWT.RIGHT_TO_LEFT);
        GridLayout cl = new GridLayout(1, false);
        cl.marginWidth = 0;
        cl.marginHeight = 4;
        cl.verticalSpacing = 4;
        choice.setLayout(cl);
        choice.setLayoutData(BankQuestionPanel.wide());
        final Button delete = new Button(choice, SWT.RADIO | SWT.RIGHT_TO_LEFT);
        delete.setText(RtlText.rtl("למחוק את קבצי התרגיל (מומלץ)"));
        delete.setFont(Look.font(body.getDisplay(), 0, SWT.BOLD));
        delete.setSelection(!keepFiles);
        delete.setData("dsviz.id", "done-delete");
        final Button keep = new Button(choice, SWT.RADIO | SWT.RIGHT_TO_LEFT);
        keep.setText(RtlText.rtl("לשמור את הקבצים"));
        keep.setFont(Look.font(body.getDisplay(), 0, SWT.BOLD));
        keep.setSelection(keepFiles);
        keep.setData("dsviz.id", "done-keep");
        org.eclipse.swt.widgets.Listener pick = e -> {
            boolean k = keep.getSelection();
            if (k == keepFiles) return;
            keepFiles = k;
            render();
        };
        delete.addListener(SWT.Selection, pick);
        keep.addListener(SWT.Selection, pick);
        BankQuestionPanel.text(card, keepFiles
                ? "הקבצים נשארים בפרויקט כמו שהם, ואפשר להמשיך לעבוד עליהם. מ\"מאגר תרגילים\" אפשר לפתוח אותם שוב (\"פתח את התרגיל\")."
                : confirm, 0, SWT.NORMAL, Look.INK2, shown);
        shown.add("DONE-CHOICE " + (keepFiles ? "keep" : "delete"));
        Composite bar = new Composite(card, SWT.NONE);
        GridLayout l = new GridLayout(2, false);
        l.marginWidth = 0;
        l.marginHeight = 8;
        l.horizontalSpacing = 10;
        bar.setLayout(l);
        bar.setLayoutData(new GridData(SWT.BEGINNING, SWT.TOP, false, false));
        Look.Round yes = new Look.Round(bar, keepFiles ? "סיום ושמירת הקבצים" : "סיום ומחיקה", true, () -> {
            boolean k = keepFiles;
            confirm = null;
            keepFiles = false;
            if (canSubmit(q)) openSubmit(q, true, k);   // "סיימתי" מגיש למורה לפני המחיקה
            else if (BankHandler.complete(shell, q, false, k)) shell.dispose();
            else render();
        });
        yes.setData("dsviz.say", "primary");
        Look.Round no = new Look.Round(bar, "ביטול", false, () -> {
            confirm = null;
            keepFiles = false;
            render();
        });
        no.setData("dsviz.say", "cancel");
        shown.add("DONE-CONFIRM");
    }

    // ------------------------------------------------------------------ "הגשה למורה"

    /** התיקייה (במחשב) של התרגיל האחרון שנוצר מהשאלה, או null */
    private static java.io.File exerciseDir(BankStore.Question q) {
        List<BankProgress.Created> all = BankProgress.get().createdOf(q.id);
        if (all.isEmpty()) return null;
        BankProgress.Created c = all.get(all.size() - 1);
        if (BankHandler.createdFiles(c).isEmpty()) return null;
        IResource r = ResourcesPlugin.getWorkspace().getRoot().findMember(new org.eclipse.core.runtime.Path(c.folder));
        return r == null || r.getLocation() == null ? null : r.getLocation().toFile();
    }

    /** יש למי להגיש (תלמיד שמשויך למורה / עמדת מחשב), ויש תרגיל מהשאלה */
    /** תלמיד מחובר שעוד לא משויך למורה (והשרת יודע לקבל הגשות), ויש תרגיל — מציעים להצטרף עם קוד המורה */
    static boolean joinHint(BankStore.Question q) {
        try {
            return !AccessGate.teacherLink().isEmpty() && AccessGate.studentTeacher().isEmpty() && exerciseDir(q) != null;
        } catch (RuntimeException e) {
            return false;
        }
    }

    static boolean canSubmit(BankStore.Question q) {
        try {
            return Submit.available() && exerciseDir(q) != null;
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** שינויים שעוד לא נשמרו בעורך נשמרים — כדי שמה שנבדק ונשלח יהיה הקוד שהתלמיד רואה */
    private void saveEditors(BankStore.Question q) {
        List<BankProgress.Created> all = BankProgress.get().createdOf(q.id);
        if (all.isEmpty()) return;
        for (IFile f : BankHandler.createdFiles(all.get(all.size() - 1))) {
            try {
                IEditorPart open = page.findEditor(new org.eclipse.ui.part.FileEditorInput(f));
                if (open != null && open.isDirty()) open.doSave(null);
            } catch (RuntimeException e) {
                // לא נשמר מהעורך — נשלח מה שבדיסק
            }
        }
    }

    /** פותח את דף ההגשה, ובודק ברקע שהקוד נבנה ורץ (RunCheck). thenComplete — "סיימתי": אחרי ההגשה מסמנים ומוחקים */
    void openSubmit(final BankStore.Question q, boolean thenComplete, boolean keep) {
        final SubmitState st = new SubmitState();
        st.thenComplete = thenComplete;
        st.keep = keep;
        saveEditors(q);
        st.mainClass = AiScaffold.read(Json.obj(Json.parse(Json.write(q.exercise)))).mainClass;
        st.files = BankSubmit.files(exerciseDir(q), ".java", st.mainClass);
        submit = st;
        submitNote = null;
        submitResult = null;
        lastSubmitResult = null;
        render();
        final Display d = shell.getDisplay();
        Thread t = new Thread(() -> {
            RunCheck.Result r;
            try {
                r = BankSubmit.check(q, st.files, st.mainClass, false, RunVisualHandler.libraryJar(), null, null);
            } catch (Throwable x) {
                r = new RunCheck.Result();
            }
            final RunCheck.Result result = r;
            d.asyncExec(() -> {
                st.check = result;
                if (submit == st && !shell.isDisposed()) render();
            });
        }, "dsviz-submit-check");
        t.setDaemon(true);
        t.start();
    }

    /** אפשר ללחוץ "הגשה": הבדיקה הסתיימה, יש קבצים, לא שולחים עכשיו, ובעמדה — נכתב שם */
    private static boolean ready(SubmitState st) {
        return st.check != null && !st.busy && !st.files.isEmpty() && (!"station".equals(Submit.mode()) || Submit.whoOk(st.who));
    }

    /** דף "הגשה למורה" (במקום השאלה): מה נשלח, תוצאת בדיקת ההרצה, ובעמדת מחשב — "שם המגיש" */
    private void submitCard(final BankStore.Question q, final SubmitState st) {
        Composite card = Look.card(body, BankQuestionPanel.wide());
        BankQuestionPanel.text(card, Submit.label(), 1, SWT.BOLD, Look.TEXT, shown);
        BankQuestionPanel.text(card, "התרגיל: " + q.title, 0, SWT.NORMAL, Look.TEXT, shown);
        BankQuestionPanel.text(card, BankSubmit.filesLine(st.files), 0, SWT.NORMAL, Look.INK2, shown);
        if (st.check == null) BankQuestionPanel.text(card, "בודק שהקוד נבנה ורץ…", 0, SWT.NORMAL, Look.MUTED, shown);
        else {
            String tone = st.check.tone();
            int color = "good".equals(tone) ? Look.OK : "bad".equals(tone) ? Look.ERR : "warn".equals(tone) ? 0xB45309 : Look.MUTED;
            BankQuestionPanel.text(card, ("good".equals(tone) ? "✓ " : "") + st.check.summary(), 0, SWT.BOLD, color, shown);
            shown.add("SUBMIT-CHECK " + tone + " " + st.check.summary());
            // קוד שלא מתקמפל: רק כמה שגיאות יש (בשורה שלמעלה) — בלי רשימה. שגיאה בזמן ריצה: מה שהתוכנית הדפיסה
            if (!"bad".equals(tone) && !st.check.error.isEmpty() && !st.check.timedOut) BankQuestionPanel.text(card, st.check.error, -1, SWT.NORMAL, Look.INK2, shown);
            if (!"good".equals(tone) && !"none".equals(tone))
                BankQuestionPanel.text(card, "אפשר לבטל, לתקן ולהגיש שוב — או להגיש כך: המורה יראה את הקוד ואת מה שקרה בהרצה.", 0, SWT.NORMAL, Look.INK2, shown);
        }
        final Look.Round[] yesRef = { null };
        org.eclipse.swt.widgets.Text whoField = null;
        if ("station".equals(Submit.mode())) {
            BankQuestionPanel.text(card, "שם המגיש", 0, SWT.BOLD, Look.TEXT, shown);
            final org.eclipse.swt.widgets.Text who = new org.eclipse.swt.widgets.Text(card, SWT.BORDER | SWT.SINGLE | SWT.RIGHT_TO_LEFT);
            who.setText(st.who);
            who.setFont(Look.font(body.getDisplay(), 0, SWT.NORMAL));
            who.setTextLimit(Submit.WHO_MAX);
            who.setEnabled(!st.busy);
            who.setData("dsviz.id", "submit-who");
            GridData wg = new GridData(SWT.BEGINNING, SWT.TOP, false, false);
            wg.widthHint = 220;
            who.setLayoutData(wg);
            who.addModifyListener(e -> {
                st.who = who.getText();
                if (yesRef[0] != null && !yesRef[0].isDisposed()) yesRef[0].setEnabled(ready(st));
            });
            BankQuestionPanel.text(card, "כותבים את השם שלכם: המחשב הזה משמש כמה תלמידים.", -1, SWT.NORMAL, Look.MUTED, shown);
            shown.add("SUBMIT-WHO");
            whoField = who;
        }
        if (st.error != null) {
            BankQuestionPanel.text(card, st.error, 0, SWT.NORMAL, Look.ERR, shown);
            shown.add("SUBMIT-ERROR " + st.error);
        }
        Composite bar = new Composite(card, SWT.NONE);
        GridLayout l = new GridLayout(3, false);
        l.marginWidth = 0;
        l.marginHeight = 8;
        l.horizontalSpacing = 10;
        bar.setLayout(l);
        bar.setLayoutData(new GridData(SWT.BEGINNING, SWT.TOP, false, false));
        Look.Round yes = new Look.Round(bar, st.busy ? "שולח…" : st.thenComplete ? "הגשה וסיום" : "הגשה", true, () -> send(q, st));
        yes.setData("dsviz.say", "primary");
        yes.setData("dsviz.id", "submit-yes");
        yes.setEnabled(ready(st));
        yesRef[0] = yes;
        Look.Round no = new Look.Round(bar, "ביטול", false, () -> {
            submit = null;
            render();
        });
        no.setData("dsviz.say", "cancel");
        no.setData("dsviz.id", "submit-no");
        no.setEnabled(!st.busy);
        if (st.thenComplete && st.error != null && !st.busy) {   // ההגשה לא עוברת — לא נועלים את "סיימתי"
            Look.Round skip = new Look.Round(bar, "לסיים בלי להגיש", false, () -> {
                submit = null;
                if (BankHandler.complete(shell, q, false, st.keep)) shell.dispose();
                else render();
            });
            skip.setData("dsviz.id", "submit-skip");
        }
        shown.add("SUBMIT-PAGE " + (st.check == null ? "checking" : st.check.tone()) + (st.thenComplete ? " then-complete" : ""));
        if (submitResult != null) shown.add("SUBMIT-RESULT " + submitResult);
        // הסמן בשדה "שם המגיש" (גם אחרי שהדף צויר מחדש כשהבדיקה הסתיימה), בסוף מה שכבר נכתב
        final org.eclipse.swt.widgets.Text focus = whoField;
        if (focus != null && focus.getEnabled()) body.getDisplay().asyncExec(() -> {
            if (!focus.isDisposed() && shell.getDisplay().getActiveShell() == shell) {
                focus.setFocus();
                focus.setSelection(focus.getText().length());
            }
        });
    }

    /** שולח (ברקע). הצליח / נשמר לשליחה מאוחרת — חוזרים לשאלה עם "הוגש למורה ב-…" (או מסיימים את התרגיל); אחרת — ההודעה בדף */
    private void send(final BankStore.Question q, final SubmitState st) {
        if (!ready(st)) return;
        st.busy = true;
        st.error = null;
        render();
        BankStore.Topic topic = BankStore.load().topic(q.topic);
        final Submit.Work w = BankSubmit.work(q, topic == null ? "" : topic.title, st.files, st.mainClass, ".java", "Eclipse");
        w.who = st.who;
        w.check = st.check.ran || st.check.needsInput ? st.check.toMap() : null;
        final Display d = shell.getDisplay();
        Thread t = new Thread(() -> {
            Account.Result r;
            try {
                r = Submit.send(w);
            } catch (Throwable x) {
                r = new Account.Result("error", "ההגשה לא הצליחה. נסו שוב בעוד רגע.", null);
            }
            final Account.Result res = r;
            d.asyncExec(() -> {
                st.busy = false;
                submitResult = res.status;
                lastSubmitResult = res.status;
                if (shell.isDisposed()) return;
                if (res.ok() || "queued".equals(res.status)) {
                    Submit.flushAsync();
                    if (submit == st) submit = null;
                    submitNote = Submit.doneText(res);
                    // נרשם במחשב (רק לתלמיד שמחובר בעצמו): גם מחר, כשפותחים את השאלה, רואים שהיא הוגשה
                    if ("student".equals(Submit.mode())) BankProgress.get().submitted(q.id, Submit.teacher(), "queued".equals(res.status));
                    if (st.thenComplete && BankHandler.complete(shell, q, false, st.keep)) shell.dispose();
                    else render();
                    return;
                }
                st.error = res.message.isEmpty() ? "ההגשה לא הצליחה. נסו שוב בעוד רגע." : res.message;
                if (submit == st) render();
            });
        }, "dsviz-submit");
        t.setDaemon(true);
        t.start();
    }

    // ------------------------------------------------------------------ פעולות

    /** "הכן סעיף ב": הפעולה של הסעיף והבדיקות שלו נכנסות לקוד, החלון מתעדכן, והסמן עובר לפעולה החדשה */
    void prepare(BankStore.Question q, AiScaffold.Task task) {
        String label = task.label.replaceAll("['׳]+$", "");
        String problem = BankHandler.prepare(page, q, task);
        shown.add("PREPARED-SECTION " + label + (problem == null ? " ok" : " " + problem));
        if (!shell.isDisposed()) render();
        if (problem != null) Say.warn(shell, "השאלה", "סעיף " + label + "' לא נוסף עד הסוף:\n" + problem);
    }

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
