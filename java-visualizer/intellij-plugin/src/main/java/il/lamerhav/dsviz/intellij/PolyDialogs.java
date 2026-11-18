package il.lamerhav.dsviz.intellij;

import java.awt.BorderLayout;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.swing.JComponent;
import javax.swing.JPanel;

import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ui.JBUI;

import il.lamerhav.dsviz.ClassGen;
import il.lamerhav.dsviz.NameCheck;
import il.lamerhav.dsviz.PolyPanels;

/**
 * הורשה ופולימורפיזם ב-IntelliJ: "היררכיה מוכנה" (כל המחלקות של עץ + main לדוגמה, ליד הקובץ הנוכחי) ו"הוסף עצם" (במקום הסמן).
 * החלונות עצמם — PolyPanels (משותף לכלי של Visual Studio); ההיגיון — ClassGen (אותו קוד כמו ב-Eclipse וב-VS Code).
 */
final class PolyDialogs {
    private PolyDialogs() {}

    // ------------------------------------------------------------------ היררכיה מוכנה

    static void createTree(Project project, VirtualFile dir, String pkg) {
        if (dir == null || !dir.isDirectory()) {
            Say.info(project, "פותחים קובץ Java (או בוחרים תיקייה בעץ הקבצים) — המחלקות נוצרות שם, ואז לוחצים שוב \"" + ClassGen.TREES_TITLE + "\".", ClassGen.TREES_TITLE);
            return;
        }
        ClassGen.Options base = new ClassGen.Options();
        base.lang = ClassGen.JAVA;
        base.pkg = pkg == null ? "" : pkg;
        base.taken = ClassCreator.taken(dir);
        base.known = ClassCreator.known(project, dir);
        TreeDialog d = new TreeDialog(project, base);
        if (!d.showAndGet() || d.result == null || !d.result.ok()) return;
        createAll(project, dir, d.result, d.tree);
    }

    /** כל הקבצים של ההיררכיה; פותח את ה-main (או את המחלקה האחרונה). מחזיר את מה שנוצר */
    static List<VirtualFile> createAll(Project project, VirtualFile dir, ClassGen.TreeResult r, ClassGen.Tree t) {
        List<VirtualFile> out = new ArrayList<VirtualFile>();
        dir.refresh(false, false);
        for (String[] f : r.files)
            if (dir.findChild(f[0]) != null) {
                Say.warn(project, "כבר יש קובץ " + f[0] + " בתיקייה — לא נוצר כלום. בוחרים היררכיה אחרת או משנים שם.", ClassGen.TREES_TITLE);
                return out;
            }
        try {
            for (String[] f : r.files) out.add(ProjectFiles.create(project, new java.io.File(dir.getPath()), f[0], f[1]));
            if (!out.isEmpty()) {
                String[] last = r.files.get(r.files.size() - 1);
                ProjectFiles.open(project, out.get(out.size() - 1), ClassGen.classLine(last[1]));
            }
        } catch (java.io.IOException ex) {
            Say.error(project, Say.failed("לא הצלחתי ליצור את המחלקות", ex), ClassGen.TREES_TITLE);
        }
        return out;
    }

    static final class TreeDialog extends Modern.Dialog {
        final ClassGen.Options base;
        PolyPanels.TreePanel panel;
        JBLabel errorLabel, warnLabel;
        ClassGen.TreeResult result;
        ClassGen.Tree tree;

        TreeDialog(Project project, ClassGen.Options base) {
            super(project);
            this.base = base;
            setTitle(ClassGen.TREES_TITLE);
            setOKButtonText("יצירת המחלקות");
            setCancelButtonText("ביטול");
            init();
            update();
        }

        @Override
        protected JComponent createCenterPanel() {
            JPanel root = new JPanel(new BorderLayout(0, JBUI.scale(4)));
            root.add(Forms.header(ClassGen.TREES_TITLE, "כל המחלקות של עץ בלחיצה אחת — כל מחלקה בקובץ משלה, עם בנאי שמעביר ל-super, ו-main לדוגמה עם מערך"), BorderLayout.NORTH);
            PolyPanels.code = Forms.codeFont();
            panel = new PolyPanels.TreePanel(base, false, this::update);
            root.add(new JBScrollPane(Forms.card(panel)), BorderLayout.CENTER);
            JPanel south = new JPanel(new java.awt.GridLayout(0, 1));
            warnLabel = new JBLabel(" ");
            warnLabel.setForeground(new JBColor(0x9A6700, 0xD9A441));
            errorLabel = Forms.error();
            south.add(warnLabel);
            south.add(errorLabel);
            root.add(south, BorderLayout.SOUTH);
            Forms.rtl(root);
            PolyPanels.fixLtr(root);
            return Modern.Dialog.wrap(root);
        }

        void update() {
            if (panel == null || errorLabel == null) return;
            tree = panel.tree(new ArrayList<String>());
            result = panel.result();
            errorLabel.setText(result.errors.isEmpty() ? " " : result.errors.get(0));
            warnLabel.setText(result.warnings.isEmpty() ? " " : "‏" + String.join(" · ", result.warnings));
            setOKActionEnabled(result.ok() && !result.files.isEmpty());
            PolyPanels.fixLtr(panel);
        }

        /** לבדיקות */
        String error() {
            return errorLabel.getText().trim();
        }
    }

    // ------------------------------------------------------------------ הוסף עצם

    static final String OBJECT = ClassGen.OBJECT_TITLE;

    static void insertObject(Project project, Editor editor, PsiFile file) {
        if (editor == null || file == null || !file.getName().endsWith(".java")) {
            Say.info(project, "פתחו קובץ Java ושימו את הסמן במקום שבו רוצים את העצם (בתוך main או בתוך פעולה).", OBJECT);
            return;
        }
        Document doc = editor.getDocument();
        PsiDocumentManager.getInstance(project).commitDocument(doc);
        StructureInserter.Spot spot = StructureInserter.spot(file, doc, editor.getCaretModel().getOffset());
        if (!NameCheck.insideFunction(doc.getText(), spot.insertAt)) {
            Say.warn(project, "הסמן נמצא מחוץ לפעולה — לוחצים בתוך main (או בפעולה אחרת), בשורה שבה רוצים את העצם, ואז שוב \"" + OBJECT + "\".", OBJECT);
            return;
        }
        VirtualFile dir = file.getVirtualFile() == null ? null : file.getVirtualFile().getParent();
        List<ClassGen.Known> known = dir == null ? new ArrayList<ClassGen.Known>() : ClassCreator.known(project, dir);
        ObjectDialog d = new ObjectDialog(project, known, doc.getText(), spot.insertAt, spot.indent);
        if (!d.showAndGet() || d.code == null) return;
        final String code = d.code;
        int[] caret = { 0 };
        WriteCommandAction.runWriteCommandAction(project, OBJECT, null, () -> {
            if (spot.blank) {
                doc.replaceString(spot.lineStart, spot.lineEnd, code);
                caret[0] = spot.lineStart + code.length();
            } else {
                doc.insertString(spot.lineEnd, "\n" + code);
                caret[0] = spot.lineEnd + 1 + code.length();
            }
            PsiDocumentManager.getInstance(project).commitDocument(doc);
        }, file);
        editor.getCaretModel().moveToOffset(caret[0]);
        editor.getScrollingModel().scrollToCaret(ScrollType.MAKE_VISIBLE);
    }

    static final class ObjectDialog extends Modern.Dialog {
        final List<ClassGen.Known> known;
        final String text, indent;
        final int offset;
        PolyPanels.ObjectPanel panel;
        JBLabel errorLabel;
        String code;

        ObjectDialog(Project project, List<ClassGen.Known> known, String text, int offset, String indent) {
            super(project);
            this.known = known;
            this.text = text;
            this.offset = offset;
            this.indent = indent;
            setTitle(OBJECT);
            setOKButtonText("הוספה לקוד");
            setCancelButtonText("ביטול");
            init();
            update();
        }

        @Override
        protected JComponent createCenterPanel() {
            JPanel root = new JPanel(new BorderLayout(0, JBUI.scale(4)));
            root.add(Forms.header(OBJECT, "משתנה מסוג מחלקת העל שמצביע על עצם של מחלקה יורשת — או מערך שבכל תא מחלקה אחרת"), BorderLayout.NORTH);
            PolyPanels.code = Forms.codeFont();
            // השם נבדק מול הקוד שבמקום הסמן (כמו "הוסף מבנה נתונים")
            panel = new PolyPanels.ObjectPanel(ClassGen.JAVA, known, this::update,
                    name -> NameCheck.checkName(name, text, offset, n -> ClassGen.isIdentifier(n, ClassGen.JAVA)));
            root.add(new JBScrollPane(Forms.card(panel)), BorderLayout.CENTER);
            errorLabel = Forms.error();
            root.add(errorLabel, BorderLayout.SOUTH);
            Forms.rtl(root);
            PolyPanels.fixLtr(root);
            return Modern.Dialog.wrap(root);
        }

        void update() {
            if (panel == null || errorLabel == null) return;
            ClassGen.ObjResult r = panel.result(indent);
            errorLabel.setText(r.errors.isEmpty() ? " " : r.errors.get(0));
            code = r.ok() ? r.code : null;
            setOKActionEnabled(r.ok());
            PolyPanels.fixLtr(panel);
        }

        String error() {
            return errorLabel.getText().trim();
        }
    }

    /** לבדיקות: המפה של הצבעים (שם → #hex) כמו שהחלונות מציגים */
    static String colors(List<ClassGen.Known> known) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Integer> e : ClassGen.colors(ClassGen.sorted(known)).entrySet())
            if (e.getValue() >= 0) sb.append(sb.length() > 0 ? " " : "").append(e.getKey()).append('=').append(String.format("#%06x", ClassGen.ink(e.getValue())));
        return sb.toString();
    }
}
