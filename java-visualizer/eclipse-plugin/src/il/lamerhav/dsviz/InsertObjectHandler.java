package il.lamerhav.dsviz;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.resources.IFile;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.ITextSelection;
import org.eclipse.jface.window.Window;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.handlers.HandlerUtil;
import org.eclipse.ui.texteditor.ITextEditor;

/** "הוסף עצם": Animal a1 = new Boxer(...); (או מערך של מחלקת העל) במקום הסמן — המחלקות נקראות מהפרויקט */
public class InsertObjectHandler extends AbstractHandler {
    static final String TITLE = ClassGen.OBJECT_TITLE;

    @Override
    public Object execute(ExecutionEvent event) {
        if (!Power.on()) return null;
        Shell shell = Ui.shell(event);
        IEditorPart part = HandlerUtil.getActiveEditor(event);
        ITextEditor ed = part == null ? null : part.getAdapter(ITextEditor.class);
        IFile file = ed == null ? null : ed.getEditorInput().getAdapter(IFile.class);
        if (ed == null || file == null || !file.getName().endsWith(".java")) {
            Say.info(shell, TITLE, "פתחו קובץ Java ושימו את הסמן במקום שבו רוצים את העצם (בתוך main או בתוך פעולה).");
            return null;
        }
        IDocument doc = ed.getDocumentProvider().getDocument(ed.getEditorInput());
        ITextSelection sel = (ITextSelection) ed.getSelectionProvider().getSelection();
        try {
            IRegion line = doc.getLineInformation(Math.max(0, sel.getStartLine()));
            String lineText = doc.get(line.getOffset(), line.getLength());
            boolean blank = lineText.trim().isEmpty();
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("^\\s*").matcher(lineText);
            String indent = blank ? (lineText.isEmpty() ? "        " : lineText) : (m.find() ? m.group() : "");
            int insertAt = blank ? line.getOffset() : line.getOffset() + line.getLength();
            int commentEnd = InsertHandler.commentEnd(doc.get(), insertAt);
            if (commentEnd > 0) {
                line = doc.getLineInformation(doc.getLineOfOffset(commentEnd));
                blank = false;
                insertAt = line.getOffset() + line.getLength();
            }
            if (!NameCheck.insideFunction(doc.get(), insertAt)) {
                Say.warn(shell, TITLE, "הסמן נמצא מחוץ לפעולה — לוחצים בתוך main (או בפעולה אחרת), בשורה שבה רוצים את העצם, ואז שוב \"" + TITLE + "\".");
                return null;
            }
            SwtPoly.ObjectDialog d = new SwtPoly.ObjectDialog(shell, CreateClassHandler.known(file.getProject()), doc.get(), insertAt, indent);
            if (d.open() != Window.OK || d.code() == null) return null;
            String code = d.code();
            int caret;
            if (blank) {
                doc.replace(line.getOffset(), line.getLength(), code);
                caret = line.getOffset() + code.length();
            } else {
                int end = line.getOffset() + line.getLength();
                doc.replace(end, 0, "\n" + code);
                caret = end + 1 + code.length();
            }
            ed.selectAndReveal(caret, 0);
        } catch (BadLocationException e) {
            Say.error(shell, TITLE, e.toString());
        }
        return null;
    }
}
