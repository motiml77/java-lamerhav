package il.lamerhav.dsviz.intellij;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.editor.event.EditorFactoryEvent;
import com.intellij.openapi.editor.event.EditorFactoryListener;
import com.intellij.openapi.editor.impl.DocumentMarkupModel;
import com.intellij.openapi.editor.markup.HighlighterLayer;
import com.intellij.openapi.editor.markup.HighlighterTargetArea;
import com.intellij.openapi.editor.markup.LineMarkerRenderer;
import com.intellij.openapi.editor.markup.MarkupModel;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ui.JBUI;

/**
 * המקום של התשובה בעורך — כמו באתר: כל עוד בגוף הפעולה של סעיף יש רק את שורת הסימון ("====כאן עונים סעיף א'===="),
 * הגוף מסומן ברקע צהוב בהיר, עם פס צהוב משמאל (בשוליים) וסימן בפס הגלילה. ברגע שהתלמיד כותב שם קוד — הסימון נעלם.
 * רק בקבצים ש"שליחת תרגיל" יצרה (התיקיות ש-SubmitCli החזיר — רשימה לכל פרויקט), וחישוב מחדש זול אחרי כל שינוי במסמך.
 */
@Service(Service.Level.PROJECT)
public final class AnswerMarks implements Disposable {
    private final Project project;
    private final Set<String> folders = ConcurrentHashMap.newKeySet();
    private final Map<Document, List<RangeHighlighter>> marks = Collections.synchronizedMap(new WeakHashMap<Document, List<RangeHighlighter>>());
    private final Map<Document, Boolean> pending = Collections.synchronizedMap(new WeakHashMap<Document, Boolean>());
    private boolean listening;

    public AnswerMarks(Project project) {
        this.project = project;
    }

    static AnswerMarks get(Project project) {
        return project.getService(AnswerMarks.class);
    }

    @Override
    public void dispose() {
        folders.clear();
    }

    /** תיקייה של תרגיל שנוצר: הקבצים בה מסומנים (גם אלה שכבר פתוחים) */
    void track(File folder) {
        if (folder == null) return;
        folders.add(norm(folder.getAbsolutePath()));
        listen();
        for (Editor e : EditorFactory.getInstance().getAllEditors()) if (e.getProject() == null || e.getProject() == project) attach(e.getDocument());
    }

    boolean tracked(VirtualFile f) {
        if (f == null) return false;
        String p = norm(f.getPath());
        for (String d : folders) if (p.startsWith(d + "/")) return true;
        return false;
    }

    private static String norm(String path) {
        String p = path.replace('\\', '/');
        return p.endsWith("/") ? p.substring(0, p.length() - 1) : p;
    }

    private void listen() {
        if (listening) return;
        listening = true;
        EditorFactory.getInstance().addEditorFactoryListener(new EditorFactoryListener() {
            @Override
            public void editorCreated(EditorFactoryEvent event) {
                Editor e = event.getEditor();
                if (e.getProject() == null || e.getProject() == project) attach(e.getDocument());
            }
        }, this);
    }

    /** מסמן את המסמך (אם הוא בתיקייה של תרגיל) ומאזין לשינויים בו */
    void attach(Document doc) {
        VirtualFile f = FileDocumentManager.getInstance().getFile(doc);
        if (!tracked(f)) return;
        if (!marks.containsKey(doc)) {
            marks.put(doc, new ArrayList<RangeHighlighter>());
            doc.addDocumentListener(new DocumentListener() {
                @Override
                public void documentChanged(DocumentEvent event) {
                    schedule(doc);
                }
            }, this);
        }
        refresh(doc);
    }

    private void schedule(Document doc) {
        if (pending.put(doc, Boolean.TRUE) != null) return;
        ApplicationManager.getApplication().invokeLater(() -> {
            pending.remove(doc);
            refresh(doc);
        }, project.getDisposed());
    }

    /** מחשב מחדש את המקומות ומעדכן את הסימון (רק אם משהו השתנה) */
    void refresh(Document doc) {
        if (project.isDisposed()) return;
        List<Range> now = ranges(doc.getCharsSequence());
        List<RangeHighlighter> old = marks.get(doc);
        if (old == null) return;
        if (same(old, now)) return;
        MarkupModel mm = DocumentMarkupModel.forDocument(doc, project, true);
        for (RangeHighlighter h : old) if (h.isValid()) mm.removeHighlighter(h);
        old.clear();
        for (Range r : now) {
            RangeHighlighter h = mm.addRangeHighlighter(r.start, r.end, HighlighterLayer.SELECTION - 1, attributes(), HighlighterTargetArea.LINES_IN_RANGE);
            h.setLineMarkerRenderer(BAR);
            h.setErrorStripeTooltip(r.label.isEmpty() ? "כאן כותבים את התשובה" : "כאן עונים סעיף " + r.label);
            h.setThinErrorStripeMark(true);
            old.add(h);
        }
    }

    /** הסימונים שעל המסמך עכשיו (לבדיקות) */
    List<RangeHighlighter> highlighters(Document doc) {
        List<RangeHighlighter> l = marks.get(doc);
        return l == null ? Collections.<RangeHighlighter>emptyList() : new ArrayList<RangeHighlighter>(l);
    }

    private static boolean same(List<RangeHighlighter> old, List<Range> now) {
        if (old.size() != now.size()) return false;
        for (int i = 0; i < old.size(); i++) {
            RangeHighlighter h = old.get(i);
            if (!h.isValid() || h.getStartOffset() != now.get(i).start || h.getEndOffset() != now.get(i).end) return false;
        }
        return true;
    }

    static TextAttributes attributes() {
        TextAttributes a = new TextAttributes();
        a.setBackgroundColor(Modern.ANSWER);
        a.setErrorStripeColor(Modern.AMBER);
        return a;
    }

    /** הפס הצהוב משמאל (בשוליים של העורך, כמו inset 3px באתר) */
    static final LineMarkerRenderer BAR = (editor, g, r) -> {
        g.setColor(Modern.AMBER);
        g.fillRect(r.x, r.y, JBUI.scale(3), r.height);
    };

    // ------------------------------------------------------------------ איפה כותבים את התשובה

    /** גוף של פעולה שעוד אין בו תשובה: מתחילת שורת הסימון עד סוף השורה שלפני הסוגר } */
    static final class Range {
        final int start, end;
        final String label;

        Range(int start, int end, String label) {
            this.start = start;
            this.end = end;
            this.label = label;
        }

        @Override
        public String toString() {
            return start + ".." + end + (label.isEmpty() ? "" : " " + label);
        }
    }

    private static final String BIDI = "[\\u202A-\\u202E\\u2066-\\u2069\\u200E\\u200F]*";
    static final Pattern MARK = Pattern.compile("//[ \\t]*" + BIDI + "====" + BIDI + "(כאן עונים סעיף\\s*([^'׳=\\s\\u202A-\\u202E]*)['׳]?|כאן כותבים את התשובה)" + BIDI + "====");

    /** המקומות של התשובות שעוד לא נכתבו: בגוף הפעולה יש רק את שורת הסימון (ורווחים) */
    static List<Range> ranges(CharSequence text) {
        List<Range> out = new ArrayList<Range>();
        Matcher m = MARK.matcher(text);
        int n = text.length();
        while (m.find()) {
            int b = m.start() - 1;
            while (b >= 0 && Character.isWhitespace(text.charAt(b))) b--;
            if (b < 0 || text.charAt(b) != '{') continue;
            int e = m.end();
            while (e < n && Character.isWhitespace(text.charAt(e))) e++;
            if (e >= n || text.charAt(e) != '}') continue;
            int start = lineStart(text, m.start()), close = lineStart(text, e);
            int end = close > start ? close - 1 : m.end();
            out.add(new Range(start, Math.max(start, end), m.group(2) == null ? "" : m.group(2)));
        }
        return out;
    }

    private static int lineStart(CharSequence text, int offset) {
        int i = offset;
        while (i > 0 && text.charAt(i - 1) != '\n') i--;
        return i;
    }
}
