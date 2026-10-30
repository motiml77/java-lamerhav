package il.lamerhav.dsviz;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.ISourceRange;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.jface.text.codemining.AbstractCodeMiningProvider;
import org.eclipse.jface.text.codemining.ICodeMining;
import org.eclipse.jface.text.codemining.ICodeMiningProvider;
import org.eclipse.jface.text.codemining.LineHeaderCodeMining;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.texteditor.ITextEditor;

/**
 * הכפתור "הצעה לפתרון" (מנורה) מעל הכותרת של כל פעולה של סעיף, בתרגילים שנוצרו ב"שליחת תרגיל".
 * זה code mining של עורך ה-Java — שורה מצוירת מעל הכותרת, שלא נכנסת לקוד עצמו. מופיע רק ליד פעולות שיש להן הצעה
 * (SolutionStore); לחיצה פותחת את החלון עם ההצעה (SolutionDialog). (אם code minings כבויים — מהתפריט "מבני נתונים".)
 */
public class SolutionMinings extends AbstractCodeMiningProvider {
    static final String LABEL = "הצעה לפתרון";

    @Override
    public CompletableFuture<List<? extends ICodeMining>> provideCodeMinings(ITextViewer viewer, IProgressMonitor monitor) {
        return CompletableFuture.supplyAsync(() -> {
            List<ICodeMining> out = new ArrayList<ICodeMining>();
            if (!Power.on()) return out;   // DSViz כבוי
            try {
                ITextEditor editor = getAdapter(ITextEditor.class);
                IFile file = editor == null || editor.getEditorInput() == null ? null : editor.getEditorInput().getAdapter(IFile.class);
                IDocument doc = viewer.getDocument();
                if (file == null || doc == null || !"java".equals(file.getFileExtension())) return out;
                String path = file.getFullPath().toString();
                SolutionStore store = store();
                if (store.forFile(path).isEmpty()) return out;
                ICompilationUnit cu = JavaCore.createCompilationUnitFrom(file);
                for (IType type : cu.getTypes()) {
                    for (IMethod m : type.getMethods()) {
                        if (monitor != null && monitor.isCanceled()) return out;
                        SolutionStore.Entry s = store.find(path, type.getElementName(), m.getElementName(), m.getParameterTypes().length);
                        ISourceRange r = m.getNameRange();
                        if (s != null && r != null && r.getOffset() >= 0) out.add(new Mining(doc.getLineOfOffset(r.getOffset()), doc, this, s));
                    }
                }
            } catch (Exception ignored) {
                // בלי כפתור — אפשר מהתפריט
            }
            return out;
        });
    }

    /** אחרי כיבוי / הפעלה של DSViz: מציירים מחדש את השורות בכל עורכי ה-Java הפתוחים */
    static void refreshAll() {
        try {
            for (org.eclipse.ui.IWorkbenchWindow w : org.eclipse.ui.PlatformUI.getWorkbench().getWorkbenchWindows()) {
                for (org.eclipse.ui.IWorkbenchPage page : w.getPages()) {
                    for (org.eclipse.ui.IEditorReference ref : page.getEditorReferences()) {
                        org.eclipse.ui.IEditorPart part = ref.getEditor(false);
                        Object viewer = part == null ? null : part.getAdapter(org.eclipse.jface.text.ITextViewer.class);
                        if (viewer instanceof org.eclipse.jface.text.source.ISourceViewerExtension5)
                            ((org.eclipse.jface.text.source.ISourceViewerExtension5) viewer).updateCodeMinings();
                    }
                }
            }
        } catch (Throwable e) {
            // יתעדכן בפתיחה הבאה של הקובץ
        }
    }

    /** השורה "הצעה לפתרון 💡" מעל הכותרת: שבב צהוב-בהיר מעוגל עם מנורה */
    static final class Mining extends LineHeaderCodeMining {
        Mining(int line, IDocument doc, ICodeMiningProvider provider, SolutionStore.Entry s) throws BadLocationException {
            super(line, doc, provider, e -> SolutionDialog.show(((Control) e.widget).getShell(), s));
            setLabel(LABEL);
            // מיושר לתחילת הכותרת (ולא לתחילת השורה) — כמו בגרסאות החדשות של Eclipse
            IRegion r = doc.getLineInformation(line);
            String text = doc.get(r.getOffset(), r.getLength());
            int k = 0;
            while (k < text.length() && Character.isWhitespace(text.charAt(k))) k++;
            if (k < text.length()) {
                getPosition().setOffset(r.getOffset() + k);
                getPosition().setLength(1);
            }
        }

        @Override
        public Point draw(GC gc, StyledText text, Color color, int x, int y) {
            Display d = text.getDisplay();
            int lh = text.getLineHeight();
            Font font = JFaceResources.getDialogFont();
            gc.setFont(font);
            Point te = gc.textExtent(LABEL);
            Image icon = icon(d);
            int is = Math.min(16, Math.max(10, lh - 4));
            int h = Math.max(is + 2, Math.min(lh - 1, te.y + 2));
            int w = 9 + te.x + 5 + is + 6;   // מימין לשמאל: הטקסט, ומימינו המנורה
            int top = y + Math.max(0, (lh - h) / 2);
            gc.setAntialias(SWT.ON);
            gc.setBackground(Look.color(d, 0xFFF6D6));
            gc.fillRoundRectangle(x, top, w, h, h, h);
            gc.setForeground(Look.color(d, 0xF0CF73));
            gc.drawRoundRectangle(x, top, w - 1, h - 1, h, h);
            gc.setForeground(Look.color(d, 0x7A4D00));
            gc.drawText(LABEL, x + 9, top + (h - te.y) / 2, true);
            if (icon != null) {
                org.eclipse.swt.graphics.Rectangle b = icon.getBounds();
                gc.drawImage(icon, 0, 0, b.width, b.height, x + 9 + te.x + 5, top + (h - is) / 2, is, is);
            }
            gc.setFont(text.getFont());
            return new Point(w, lh);
        }
    }

    private static SolutionStore shared;

    /** ההצעות של סביבת העבודה הנוכחית (בתיקייה של התוסף) */
    static synchronized SolutionStore store() {
        if (shared == null) {
            java.io.File dir;
            try {
                dir = org.eclipse.core.runtime.Platform.getStateLocation(org.eclipse.core.runtime.Platform.getBundle("il.lamerhav.dsviz")).toFile();
            } catch (Exception e) {
                dir = new java.io.File(System.getProperty("user.home"), ".dsviz");
            }
            shared = new SolutionStore(new java.io.File(dir, "solutions.json"));
        }
        return shared;
    }

    private static Image icon;

    /** המנורה (16 פיקסלים; במסכים בהגדלה — מהגרסה הכפולה) — נטענת פעם אחת */
    static synchronized Image icon(Display d) {
        if (icon == null || icon.isDisposed()) {
            icon = SolutionDialog.image(d, "icons/solution.png");
            if (icon != null) {
                final Image i = icon;
                d.disposeExec(i::dispose);
            }
        }
        return icon;
    }
}
