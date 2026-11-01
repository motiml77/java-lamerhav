package il.lamerhav.dsviz;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import org.eclipse.jface.text.DocumentEvent;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IDocumentListener;
import org.eclipse.jface.text.ITextOperationTarget;
import org.eclipse.jface.text.ITextViewerExtension5;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.LineBackgroundListener;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IPartListener2;
import org.eclipse.ui.IWindowListener;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.IWorkbenchPartReference;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.texteditor.ITextEditor;

/**
 * "כאן עונים": כל עוד בגוף של פעולה של סעיף יש רק את שורת הסימון ("====כאן עונים סעיף א'====" — AiScaffold.markFor),
 * הגוף מסומן ברקע צהוב-בהיר עם פס צהוב בצד שמאל — כמו באתר (site/assets/site.css, ‎.fp-ed .l.sel‎). ברגע שכותבים שם קוד
 * הסימון של הפעולה הזו נעלם. החישוב — סריקה קצרה של הטקסט אחרי כל שינוי (מרוכז: פעם ב-150ms), והציור — רקע לשורות
 * (LineBackgroundListener) ופס של 3px (PaintListener) על ה-StyledText של העורך. עובד בכל Eclipse מ-2020-09.
 */
final class AnswerHighlight {
    private AnswerHighlight() {}

    private static final String KEY = "dsviz.answerHighlight";
    /** שורת הסימון (עם תווי הכיווניות שמסביב לעברית, ובלעדיהם) */
    static final Pattern MARK = Pattern.compile("^\\s*//\\s*====[‪-‮]*כאן (עונים סעיף|כותבים את התשובה).*====\\s*$");
    private static final int AMBER_R = 232, AMBER_G = 161, AMBER_B = 0;

    /** מתחיל לעקוב אחרי כל העורכים (נקרא פעם אחת, בהפעלה) */
    static void start() {
        final Display d = Display.getDefault();
        d.asyncExec(() -> {
            if (!PlatformUI.isWorkbenchRunning()) return;
            IWorkbench wb = PlatformUI.getWorkbench();
            for (IWorkbenchWindow w : wb.getWorkbenchWindows()) watch(w);
            wb.addWindowListener(new IWindowListener() {
                public void windowOpened(IWorkbenchWindow w) { watch(w); }
                public void windowActivated(IWorkbenchWindow w) {}
                public void windowDeactivated(IWorkbenchWindow w) {}
                public void windowClosed(IWorkbenchWindow w) {}
            });
        });
    }

    private static void watch(IWorkbenchWindow w) {
        for (IWorkbenchPage p : w.getPages()) {
            for (org.eclipse.ui.IEditorReference r : p.getEditorReferences()) {
                IEditorPart e = r.getEditor(false);
                if (e != null) attach(e);
            }
        }
        w.getPartService().addPartListener(new IPartListener2() {
            public void partOpened(IWorkbenchPartReference ref) { part(ref); }
            public void partVisible(IWorkbenchPartReference ref) { part(ref); }
            public void partActivated(IWorkbenchPartReference ref) {}
            public void partBroughtToTop(IWorkbenchPartReference ref) {}
            public void partClosed(IWorkbenchPartReference ref) {}
            public void partDeactivated(IWorkbenchPartReference ref) {}
            public void partHidden(IWorkbenchPartReference ref) {}
            public void partInputChanged(IWorkbenchPartReference ref) {}
        });
    }

    private static void part(IWorkbenchPartReference ref) {
        IWorkbenchPart p = ref.getPart(false);
        if (p instanceof IEditorPart) attach((IEditorPart) p);
    }

    /** מחבר את הסימון לעורך של קובץ Java (פעם אחת לכל עורך) */
    static void attach(IEditorPart part) {
        ITextEditor ed = part instanceof ITextEditor ? (ITextEditor) part : part == null ? null : part.getAdapter(ITextEditor.class);
        if (ed == null || ed.getEditorInput() == null || !ed.getEditorInput().getName().endsWith(".java")) return;
        Object c = ed.getAdapter(Control.class);
        if (!(c instanceof StyledText)) return;
        StyledText st = (StyledText) c;
        if (st.getData(KEY) != null || ed.getDocumentProvider() == null) return;
        IDocument doc = ed.getDocumentProvider().getDocument(ed.getEditorInput());
        if (doc == null) return;
        Object target = ed.getAdapter(ITextOperationTarget.class);
        new Painter(st, doc, target instanceof ITextViewerExtension5 ? (ITextViewerExtension5) target : null);
    }

    /** לבדיקות: השורות (מ-0) שמסומנות עכשיו בעורך, למשל "[7-8, 12-13]" */
    static String report(IEditorPart part) {
        Object c = part == null ? null : part.getAdapter(Control.class);
        Object p = c instanceof StyledText ? ((StyledText) c).getData(KEY) : null;
        if (!(p instanceof Painter)) return "none";
        StringBuilder b = new StringBuilder("[");
        for (int[] r : ((Painter) p).ranges) b.append(b.length() > 1 ? ", " : "").append(r[0]).append('-').append(r[1]);
        return b.append(']').toString();
    }

    /**
     * השורות לסימון (מ-0, כולל): לכל שורת סימון שהשורה הלא-ריקה שלפניה נגמרת ב-"{" (הכותרת של הפעולה), ושאחריה רק שורות ריקות
     * עד ה-"}" שסוגר — משורת הסימון עד השורה שלפני ה-"}". בלי SWT (נבדק גם מחוץ ל-Eclipse).
     */
    static List<int[]> ranges(String text) {
        List<int[]> out = new ArrayList<int[]>();
        String[] lines = text.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            if (!MARK.matcher(lines[i].replace("\r", "")).matches()) continue;
            int p = i - 1;
            while (p >= 0 && lines[p].trim().isEmpty()) p--;
            if (p < 0 || !lines[p].trim().endsWith("{")) continue;
            int e = i + 1;
            while (e < lines.length && lines[e].trim().isEmpty()) e++;
            if (e >= lines.length || !lines[e].trim().startsWith("}")) continue;   // יש כבר קוד בגוף
            out.add(new int[] { i, e - 1 });
        }
        return out;
    }

    private static final class Painter implements IDocumentListener {
        final StyledText st;
        final IDocument doc;
        final ITextViewerExtension5 viewer;
        List<int[]> ranges = new ArrayList<int[]>();
        boolean pending;
        final LineBackgroundListener background;
        final Listener bar;

        Painter(final StyledText st, final IDocument doc, ITextViewerExtension5 viewer) {
            this.st = st;
            this.doc = doc;
            this.viewer = viewer;
            st.setData(KEY, this);
            background = e -> {
                if (ranges.isEmpty()) return;
                int model = viewer == null ? e.lineOffset : viewer.widgetOffset2ModelOffset(e.lineOffset);
                if (model < 0) return;
                try {
                    if (inside(doc.getLineOfOffset(model))) e.lineBackground = color(0.12);
                } catch (Exception ignored) {
                    // מחוץ למסמך
                }
            };
            bar = e -> {
                if (ranges.isEmpty()) return;
                e.gc.setBackground(color(1));
                for (int[] r : ranges) {
                    for (int line = r[0]; line <= r[1]; line++) {
                        int wl = viewer == null ? line : viewer.modelLine2WidgetLine(line);
                        if (wl < 0 || wl >= st.getLineCount()) continue;
                        int y = st.getLinePixel(wl), h = st.getLineHeight(st.getOffsetAtLine(wl));
                        if (y + h < e.y || y > e.y + e.height) continue;
                        e.gc.fillRectangle(0, y, 3, h);
                    }
                }
            };
            st.addLineBackgroundListener(background);
            st.addListener(SWT.Paint, bar);
            doc.addDocumentListener(this);
            st.addListener(SWT.Dispose, e -> doc.removeDocumentListener(this));
            recompute();
        }

        boolean inside(int line) {
            for (int[] r : ranges) if (line >= r[0] && line <= r[1]) return true;
            return false;
        }

        /** הצהוב של "כאן עונים" על הרקע של העורך (גם בערכת נושא כהה): alpha=0.12 לרקע, 1 לפס */
        Color color(double alpha) {
            RGB bg = st.getBackground().getRGB();
            int r = (int) Math.round(bg.red + (AMBER_R - bg.red) * alpha);
            int g = (int) Math.round(bg.green + (AMBER_G - bg.green) * alpha);
            int b = (int) Math.round(bg.blue + (AMBER_B - bg.blue) * alpha);
            return Look.color(st.getDisplay(), (r << 16) | (g << 8) | b);
        }

        void recompute() {
            pending = false;
            if (st.isDisposed()) return;
            List<int[]> now = Power.on() ? ranges(doc.get()) : new ArrayList<int[]>();
            if (same(now, ranges)) return;
            ranges = now;
            st.redraw();
        }

        private static boolean same(List<int[]> a, List<int[]> b) {
            if (a.size() != b.size()) return false;
            for (int i = 0; i < a.size(); i++) if (a.get(i)[0] != b.get(i)[0] || a.get(i)[1] != b.get(i)[1]) return false;
            return true;
        }

        public void documentAboutToBeChanged(DocumentEvent event) {}

        public void documentChanged(DocumentEvent event) {
            if (pending || st.isDisposed()) return;
            pending = true;
            final Display d = st.getDisplay();   // שינוי יכול להגיע גם מחוט אחר — מתזמנים בחוט של הממשק
            d.asyncExec(() -> d.timerExec(150, this::recompute));
        }
    }
}
