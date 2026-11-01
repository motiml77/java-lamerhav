package il.lamerhav.dsviz.intellij;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;

import javax.swing.Action;
import javax.swing.JComponent;
import javax.swing.JPanel;

import com.intellij.codeInsight.daemon.LineMarkerInfo;
import com.intellij.codeInsight.daemon.LineMarkerProvider;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorFontType;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiIdentifier;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTextArea;
import com.intellij.util.ui.JBUI;

import il.lamerhav.dsviz.IjShared;

/**
 * "💡 הצעה לפתרון": מנורה בצד (gutter) ליד הכותרת של כל פעולה של סעיף, בתרגילים שנוצרו ב"שליחת תרגיל" —
 * רק ליד פעולות שיש להן הצעה (‎~/.dsviz/solutions.json) — לא בתפריט ולא בסרגל, כמו ב-Eclipse (מנורה ליד הפעולה) וב-VS Code (CodeLens).
 * לחיצה פותחת חלון עם ההצעה לפעולה הזאת בלבד; ההצעה לא נכנסת לקוד של התלמיד.
 */
public final class Solutions implements LineMarkerProvider {
    static final String TITLE = "הצעה לפתרון";

    @Override
    public LineMarkerInfo<?> getLineMarkerInfo(PsiElement element) {
        if (!(element instanceof PsiIdentifier) || !(element.getParent() instanceof PsiMethod) || !Dsviz.on()) return null;
        PsiMethod m = (PsiMethod) element.getParent();
        if (m.getNameIdentifier() != element) return null;
        IjShared.Solution s = find(m);
        if (s == null) return null;
        String tip = "💡 " + TITLE + (s.label.isEmpty() ? "" : " — " + s.label) + " (כדאי לנסות קודם לבד)";
        return new LineMarkerInfo<PsiElement>(element, element.getTextRange(), Dsviz.SOLUTION, e -> tip,
                (event, el) -> {
                    PsiMethod method = PsiTreeUtil.getParentOfType(el, PsiMethod.class);
                    IjShared.Solution sol = method == null ? null : find(method);
                    if (sol != null) show(el.getProject(), sol);
                }, GutterIconRenderer.Alignment.LEFT, () -> TITLE);
    }

    /** ההצעה לפעולה (לפי הקובץ, המחלקה, השם ומספר הפרמטרים), או null */
    static IjShared.Solution find(PsiMethod m) {
        PsiFile f = m.getContainingFile();
        VirtualFile vf = f == null ? null : f.getVirtualFile();
        PsiClass c = m.getContainingClass();
        if (vf == null || c == null || c.getName() == null) return null;
        return IjShared.findSolution(Dsviz.solutionStore(), vf.getPath(), c.getName(), m.getName(), m.getParameterList().getParametersCount());
    }

    /** ההצעה לפעולה שבמקום הסמן (לבדיקות ולצילומי מסך — כמו לחיצה על המנורה), או null */
    static IjShared.Solution atCaret(Editor editor, PsiFile file) {
        if (editor == null || file == null) return null;
        PsiMethod m = PsiTreeUtil.getParentOfType(file.findElementAt(editor.getCaretModel().getOffset()), PsiMethod.class);
        return m == null ? null : find(m);
    }

    static String statusText(String status) {
        switch (status) {
            case "ok": return "✔ ההצעה נבדקה: הרצנו את התרגיל איתה, והפלט יצא בדיוק כמו הפלט הצפוי.";
            case "ran": return "ההצעה רצה בלי שגיאות (לא היה פלט צפוי להשוואה).";
            case "failed": return "⚠ שימו לב: בבדיקה הפלט לא יצא בדיוק כמו הצפוי — בדקו את ההצעה בעצמכם.";
            default: return "ההצעה לא נבדקה בהרצה — בדקו אותה בעצמכם.";
        }
    }

    static void show(Project project, IjShared.Solution s) {
        new SolutionDialog(project, s).show();
    }

    /** החלון עם ההצעה: הסעיף, האם נבדקה, והקוד (משמאל לימין). "העתקה" — ללוח */
    static final class SolutionDialog extends Modern.Dialog {
        private final IjShared.Solution s;

        SolutionDialog(Project project, IjShared.Solution s) {
            super(project);
            this.s = s;
            setTitle(TITLE + (s.label.isEmpty() ? "" : " — " + s.label));
            setOKButtonText("סגירה");
            init();
        }

        @Override
        protected Action[] createActions() {
            Action copy = new DialogWrapperAction("העתקת הקוד") {
                @Override
                protected void doAction(java.awt.event.ActionEvent e) {
                    Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(s.code), null);
                }
            };
            return new Action[] { copy, getOKAction() };
        }

        @Override
        protected JComponent createCenterPanel() {
            JPanel root = new JPanel(new BorderLayout(0, JBUI.scale(6)));
            root.add(Forms.header("💡 " + TITLE + (s.label.isEmpty() ? "" : " — " + s.label),
                    "הצעה אחת אפשרית לפעולה " + s.method + " — יש עוד דרכים נכונות. כדאי לנסות קודם לבד."), BorderLayout.NORTH);
            JBTextArea code = new JBTextArea(Submitter.clean(s.code));
            code.setEditable(false);
            code.setFont(Forms.codeFont());
            code.setBorder(JBUI.Borders.empty(8));
            JBScrollPane sp = new JBScrollPane(code);
            int lines = Math.min(22, Math.max(6, s.code.split("\n").length + 1));
            sp.setPreferredSize(new Dimension(JBUI.scale(560), JBUI.scale(lines * 18 + 20)));
            root.add(sp, BorderLayout.CENTER);
            JBLabel status = new JBLabel(statusText(s.status));
            root.add(status, BorderLayout.SOUTH);
            Forms.rtl(root, sp);
            return Modern.Dialog.wrap(root);
        }
    }
}
