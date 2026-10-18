package il.lamerhav.dsviz;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.ITextSelection;
import org.eclipse.jface.window.Window;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.handlers.HandlerUtil;
import org.eclipse.ui.texteditor.ITextEditor;

/** "הוספה לקוד": מכניס במקום הסמן מבנה עם נתוני דמה (ומוסיף import אם חסר) */
public class InsertHandler extends AbstractHandler {
    private static final Pattern IMPORT = Pattern.compile("import\\s+unit4\\.collectionsLib\\.\\*\\s*;");
    private static final Pattern PACKAGE = Pattern.compile("(?m)^\\s*package\\s+[\\w.]+\\s*;");

    @Override
    public Object execute(ExecutionEvent event) {
        Shell shell = HandlerUtil.getActiveShell(event);
        IEditorPart part = HandlerUtil.getActiveEditor(event);
        ITextEditor ed = part == null ? null : part.getAdapter(ITextEditor.class);
        if (ed == null || !ed.getEditorInput().getName().endsWith(".java")) {
            MessageDialog.openInformation(shell, "הוספה לקוד",
                    "פתחו קובץ Java ושימו את הסמן במקום שבו רוצים את המבנה (בתוך main או בתוך פעולה).");
            return null;
        }
        IDocument doc = ed.getDocumentProvider().getDocument(ed.getEditorInput());
        ITextSelection sel = (ITextSelection) ed.getSelectionProvider().getSelection();
        try {
            int lineNo = Math.max(0, sel.getStartLine());
            IRegion line = doc.getLineInformation(lineNo);
            String lineText = doc.get(line.getOffset(), line.getLength());
            boolean blank = lineText.trim().isEmpty();
            Matcher m = Pattern.compile("^\\s*").matcher(lineText);
            String indent = blank ? (lineText.isEmpty() ? "        " : lineText) : (m.find() ? m.group() : "");

            InsertDialog d = new InsertDialog(shell, event.getParameter("il.lamerhav.dsviz.structure"), doc.get(), indent);
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
            if (!IMPORT.matcher(doc.get()).find()) {
                Matcher pkg = PACKAGE.matcher(doc.get());
                String imp = "import unit4.collectionsLib.*;";
                if (pkg.find()) {
                    doc.replace(pkg.end(), 0, "\n\n" + imp);
                    caret += imp.length() + 2;
                } else {
                    doc.replace(0, 0, imp + "\n\n");
                    caret += imp.length() + 2;
                }
            }
            ed.selectAndReveal(caret, 0);
        } catch (BadLocationException e) {
            MessageDialog.openError(shell, "הוספה לקוד", e.toString());
        }
        return null;
    }
}
