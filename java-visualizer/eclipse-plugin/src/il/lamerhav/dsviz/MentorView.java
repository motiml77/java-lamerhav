package il.lamerhav.dsviz;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.eclipse.core.runtime.preferences.InstanceScope;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.jface.text.DocumentEvent;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IDocumentListener;
import org.eclipse.jface.text.ITextSelection;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.ISelectionProvider;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.ScrolledComposite;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.FontData;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IPartListener2;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.IWorkbenchPartReference;
import org.eclipse.ui.part.ViewPart;
import org.eclipse.ui.texteditor.ITextEditor;

/**
 * חלון "הסברים בעברית": כל ההערות של המורה בעברית לקובץ שפתוח בעורך — מימין לשמאל, צמודות לימין, כרטיס לכל הערה:
 * קודם ההתראה (השורה והחומרה, וההסבר), אחריה שורת רווח, ורק אז ההצעה לתיקון — והקוד המתוקן בשורה נפרדת (משמאל לימין).
 * כל טקסט עובר דרך RtlText: קטעי הקוד והמילים באנגלית מוצגים משמאל לימין כיחידה אחת בתוך משפט מימין לשמאל,
 * גם ב-Windows וגם ב-Linux, וגם כשההסבר מתחיל בקוד (p לא מתקדם...).
 * מתעדכן בזמן הכתיבה; ההערה של השורה שבה נמצא הסמן מודגשת; לחיצה על כרטיס מעבירה לשורה בקוד.
 */
public class MentorView extends ViewPart {
    public static final String ID = "il.lamerhav.dsviz.mentorView";

    private ScrolledComposite scroll;
    private Composite list;
    private Font bold, mono;
    private Color red, orange, gray, codeBg, highlight, white;
    private int lineHeight = 16;
    private ITextEditor editor;
    private IDocument document;
    private ISelectionProvider selections;
    private List<Mentor.Finding> findings = new ArrayList<Mentor.Finding>();
    private final List<Composite> cards = new ArrayList<Composite>();
    private int caretLine = -1;
    private Job job;

    private final IDocumentListener docListener = new IDocumentListener() {
        @Override
        public void documentAboutToBeChanged(DocumentEvent event) {
        }

        @Override
        public void documentChanged(DocumentEvent event) {
            schedule(700);
        }
    };

    private final ISelectionChangedListener caretListener = new ISelectionChangedListener() {
        @Override
        public void selectionChanged(SelectionChangedEvent event) {
            if (event.getSelection() instanceof ITextSelection) {
                int l = ((ITextSelection) event.getSelection()).getStartLine() + 1;
                if (l != caretLine) {
                    caretLine = l;
                    highlight(true);
                }
            }
        }
    };

    private final IPartListener2 parts = new IPartListener2() {
        @Override
        public void partActivated(IWorkbenchPartReference ref) {
            follow(ref.getPart(false));
        }

        @Override
        public void partBroughtToTop(IWorkbenchPartReference ref) {
            follow(ref.getPart(false));
        }

        @Override
        public void partInputChanged(IWorkbenchPartReference ref) {
            IWorkbenchPart p = ref.getPart(false);
            if (p == editor) { editor = null; follow(p); }
        }

        @Override
        public void partClosed(IWorkbenchPartReference ref) {
            if (ref.getPart(false) == editor) {
                detach();
                findings = new ArrayList<Mentor.Finding>();
                render("פותחים קובץ Java — וההסברים לשגיאות שבו יופיעו כאן");
            }
        }
    };

    private final IEclipsePreferences.IPreferenceChangeListener prefs = e -> {
        if ("mentor".equals(e.getKey())) schedule(0);
    };

    @Override
    public void createPartControl(Composite parent) {
        Display d = parent.getDisplay();
        red = new Color(d, 198, 40, 40);
        orange = new Color(d, 178, 106, 0);
        gray = new Color(d, 90, 90, 90);
        codeBg = new Color(d, 240, 242, 245);
        highlight = new Color(d, 255, 248, 214);
        white = d.getSystemColor(SWT.COLOR_LIST_BACKGROUND);
        FontData fd = parent.getFont().getFontData()[0];
        bold = new Font(d, fd.getName(), fd.getHeight(), SWT.BOLD);
        mono = JFaceResources.getTextFont();
        GC gc = new GC(parent);
        lineHeight = gc.getFontMetrics().getHeight();
        gc.dispose();

        scroll = new ScrolledComposite(parent, SWT.V_SCROLL | SWT.RIGHT_TO_LEFT);
        scroll.setExpandHorizontal(true);
        scroll.setExpandVertical(true);
        scroll.setBackground(white);
        list = new Composite(scroll, SWT.RIGHT_TO_LEFT);
        list.setBackground(white);
        GridLayout gl = new GridLayout(1, false);
        gl.marginWidth = 8;
        gl.marginHeight = 8;
        gl.verticalSpacing = 8;
        list.setLayout(gl);
        scroll.setContent(list);
        scroll.addListener(SWT.Resize, e -> relayout());

        IWorkbenchPage page = getSite().getPage();
        page.addPartListener(parts);
        InstanceScope.INSTANCE.getNode("il.lamerhav.dsviz").addPreferenceChangeListener(prefs);
        IEditorPart active = page.getActiveEditor();
        render("פותחים קובץ Java — וההסברים לשגיאות שבו יופיעו כאן");
        if (active != null) follow(active);
    }

    /** עוברים לעורך החדש (אם זה קובץ Java) */
    private void follow(IWorkbenchPart part) {
        if (!(part instanceof ITextEditor) || part == editor) return;
        ITextEditor ed = (ITextEditor) part;
        IFile f = ed.getEditorInput().getAdapter(IFile.class);
        if (f == null || !"java".equals(f.getFileExtension())) return;
        detach();
        editor = ed;
        document = ed.getDocumentProvider().getDocument(ed.getEditorInput());
        if (document != null) document.addDocumentListener(docListener);
        selections = ed.getSelectionProvider();
        if (selections != null) {
            selections.addSelectionChangedListener(caretListener);
            if (selections.getSelection() instanceof ITextSelection) caretLine = ((ITextSelection) selections.getSelection()).getStartLine() + 1;
        }
        schedule(0);
    }

    private void detach() {
        if (document != null) document.removeDocumentListener(docListener);
        if (selections != null) selections.removeSelectionChangedListener(caretListener);
        document = null;
        selections = null;
        editor = null;
    }

    /** ניתוח מחדש (ברקע, אחרי הפסקה קצרה בהקלדה) */
    private void schedule(long delay) {
        if (job != null) job.cancel();
        final ITextEditor ed = editor;
        final IDocument doc = document;
        if (ed == null || doc == null) return;
        if (!MentorParticipant.enabled()) {
            findings = new ArrayList<Mentor.Finding>();
            render("ההסברים בעברית כבויים — מפעילים אותם בתפריט מבני נתונים ← הסברים בעברית לשגיאות ולטעויות נפוצות");
            return;
        }
        final IFile file = ed.getEditorInput().getAdapter(IFile.class);
        final String text = doc.get();
        job = new Job("הסברים בעברית") {
            @Override
            protected IStatus run(IProgressMonitor monitor) {
                try {
                    ICompilationUnit unit = JavaCore.createCompilationUnitFrom(file);
                    ASTParser p = MentorParticipant.parser(unit.getJavaProject());
                    p.setSource(text.toCharArray());
                    p.setUnitName(file.getFullPath().toString());
                    CompilationUnit ast = (CompilationUnit) p.createAST(monitor);
                    if (monitor.isCanceled()) return Status.CANCEL_STATUS;
                    final List<Mentor.Finding> fs = new ArrayList<Mentor.Finding>(Mentor.analyze(ast, text, ast.getProblems()));
                    Collections.sort(fs, (a, b) -> a.line != b.line ? a.line - b.line : b.severity - a.severity);
                    Display.getDefault().asyncExec(() -> {
                        if (list.isDisposed() || ed != editor) return;
                        findings = fs;
                        render(fs.isEmpty() ? "אין שגיאות ואין אזהרות בקובץ " + file.getName() + " — כל הכבוד!" : null);
                    });
                } catch (Exception e) {
                    // לא מפריעים לעבודה
                }
                return Status.OK_STATUS;
            }
        };
        job.setSystem(true);
        job.schedule(delay);
    }

    /** בונה את הכרטיסים מחדש */
    private void render(String empty) {
        if (list.isDisposed()) return;
        for (Control c : list.getChildren()) c.dispose();
        cards.clear();
        if (empty != null) {
            Label l = new Label(list, SWT.WRAP | SWT.RIGHT_TO_LEFT);
            l.setText(text(empty));
            l.setForeground(gray);
            l.setBackground(white);
            l.setLayoutData(wrap());
        } else {
            int errors = 0;
            for (Mentor.Finding f : findings) if (f.isError()) errors++;
            Label head = new Label(list, SWT.WRAP | SWT.RIGHT_TO_LEFT);
            head.setText(text(errors + " שגיאות ו-" + (findings.size() - errors) + " אזהרות — לחיצה על הסבר מעבירה לשורה בקוד"));
            head.setForeground(gray);
            head.setBackground(white);
            head.setLayoutData(wrap());
            for (Mentor.Finding f : findings) card(f);
        }
        relayout();
        highlight(true);
    }

    /** כרטיס: ההתראה (שורה · שגיאה/אזהרה, וההסבר), שורת רווח, ההצעה לתיקון — והקוד המתוקן בשורה משלו */
    private void card(final Mentor.Finding f) {
        Composite c = new Composite(list, SWT.RIGHT_TO_LEFT | SWT.BORDER);
        GridLayout gl = new GridLayout(1, false);
        gl.marginWidth = 8;
        gl.marginHeight = 6;
        gl.verticalSpacing = 4;
        c.setLayout(gl);
        c.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
        c.setData(f);
        Label title = new Label(c, SWT.RIGHT_TO_LEFT);
        title.setText(text("שורה " + f.line + " · " + (f.isError() ? "שגיאה" : "אזהרה")));
        title.setFont(bold);
        title.setForeground(f.isError() ? red : orange);
        Label main = new Label(c, SWT.WRAP | SWT.RIGHT_TO_LEFT);
        main.setText(text(f.main));
        main.setLayoutData(wrap());
        if (!f.tip.isEmpty()) {
            // שורת רווח אחת בין ההתראה להצעה לתיקון (בגובה שורת טקסט, כולל הרווח הרגיל בין השורות)
            Label gap = new Label(c, SWT.RIGHT_TO_LEFT);
            GridData g = new GridData(SWT.FILL, SWT.TOP, true, false);
            g.heightHint = Math.max(2, lineHeight - gl.verticalSpacing);
            gap.setLayoutData(g);
            String[] tc = Mentor.splitCode(f.tip);
            Label tip = new Label(c, SWT.WRAP | SWT.RIGHT_TO_LEFT);
            tip.setText(text(FIX + tc[0]));
            tip.setForeground(gray);
            tip.setLayoutData(wrap());
            if (tc[1] != null) {
                // הקוד בשורה משלו, משמאל לימין, בגופן של קוד
                Label code = new Label(c, SWT.LEFT_TO_RIGHT);
                code.setText(mnemonics(RtlText.ltr(" " + tc[1] + " ")));
                code.setFont(mono);
                code.setBackground(codeBg);
                code.setLayoutData(new GridData(SWT.BEGINNING, SWT.TOP, false, false));
            }
        }
        for (Control k : c.getChildren()) k.addListener(SWT.MouseUp, e -> reveal(f));
        c.addListener(SWT.MouseUp, e -> reveal(f));
        cards.add(c);
    }

    /** הכותרת של ההצעה בכרטיס */
    static final String FIX = "הצעה לתיקון: ";

    /** טקסט לתווית מימין לשמאל: קטעי הקוד והאנגלית משמאל לימין כיחידה אחת (RtlText) */
    private static String text(String s) {
        return mnemonics(RtlText.rtl(s));
    }

    /** בתווית של SWT התו & מסמן קיצור מקשים (ונעלם) — && מציג & אחד (למשל a && b בקוד) */
    private static String mnemonics(String s) {
        return s.replace("&", "&&");
    }

    private void reveal(Mentor.Finding f) {
        if (editor == null) return;
        getSite().getPage().activate(editor);
        editor.selectAndReveal(f.start, Math.max(0, f.end - f.start + 1));
    }

    /** הכרטיסים של השורה שבה נמצא הסמן — ברקע צהוב, ומגללים אליהם */
    private void highlight(boolean scrollTo) {
        if (list == null || list.isDisposed()) return;
        Composite first = null;
        for (Composite c : cards) {
            boolean on = ((Mentor.Finding) c.getData()).line == caretLine;
            Color bg = on ? highlight : white;
            c.setBackground(bg);
            for (Control k : c.getChildren()) if (!(k.getFont() == mono)) k.setBackground(bg);
            if (on && first == null) first = c;
        }
        if (scrollTo && first != null) scroll.showControl(first);
    }

    /** תוויות עם גלישת שורות צריכות רוחב מפורש */
    private GridData wrap() {
        GridData g = new GridData(SWT.FILL, SWT.TOP, true, false);
        g.widthHint = 200;
        return g;
    }

    private void relayout() {
        if (scroll.isDisposed()) return;
        int w = Math.max(150, scroll.getClientArea().width - 16);
        for (Control c : list.getChildren()) fit(c, w);
        scroll.setMinSize(list.computeSize(w + 16, SWT.DEFAULT));
        list.layout(true, true);
    }

    private static void fit(Control c, int w) {
        if (c.getLayoutData() instanceof GridData && (c.getStyle() & SWT.WRAP) != 0) ((GridData) c.getLayoutData()).widthHint = w;
        if (c instanceof Composite) for (Control k : ((Composite) c).getChildren()) fit(k, w - 20);
    }

    @Override
    public void setFocus() {
        scroll.setFocus();
    }

    @Override
    public void dispose() {
        if (job != null) job.cancel();
        getSite().getPage().removePartListener(parts);
        InstanceScope.INSTANCE.getNode("il.lamerhav.dsviz").removePreferenceChangeListener(prefs);
        detach();
        for (Color c : new Color[] { red, orange, gray, codeBg, highlight }) if (c != null) c.dispose();
        if (bold != null) bold.dispose();
        super.dispose();
    }
}
