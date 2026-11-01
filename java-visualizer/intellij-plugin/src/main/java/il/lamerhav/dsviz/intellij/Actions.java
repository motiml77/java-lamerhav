package il.lamerhav.dsviz.intellij;

import java.io.File;
import java.util.List;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiManager;

import il.lamerhav.dsviz.IjShared;
import il.lamerhav.dsviz.Scaffold;

/** הפעולות של DSViz (התפריט "מבני נתונים", סרגל הכלים, לחיצה ימנית, חלון DSViz) */
public final class Actions {
    private Actions() {}

    /** בסיס: מוסתרת כש-DSViz כבוי (הלחצן "כיבוי") */
    abstract static class Base extends AnAction {
        @Override
        public ActionUpdateThread getActionUpdateThread() {
            return ActionUpdateThread.BGT;
        }

        @Override
        public void update(AnActionEvent e) {
            e.getPresentation().setEnabledAndVisible(e.getProject() != null && Dsviz.on() && available(e));
        }

        boolean available(AnActionEvent e) {
            return true;
        }
    }

    /** הקובץ של הפעולה: מהעורך, או הקובץ שנבחר בעץ הקבצים */
    static PsiFile psiFile(AnActionEvent e) {
        PsiFile f = e.getData(CommonDataKeys.PSI_FILE);
        if (f != null || e.getProject() == null) return f;
        VirtualFile vf = e.getData(CommonDataKeys.VIRTUAL_FILE);
        if (vf == null && !e.getPlace().endsWith("Popup")) {   // מחלון DSViz / מהסרגל: הקובץ שפתוח בעורך
            VirtualFile[] sel = com.intellij.openapi.fileEditor.FileEditorManager.getInstance(e.getProject()).getSelectedFiles();
            vf = sel.length > 0 ? sel[0] : null;
        }
        return vf == null || vf.isDirectory() || !vf.isValid() ? null : PsiManager.getInstance(e.getProject()).findFile(vf);
    }

    // ------------------------------------------------------------------ הרצה ויזואלית

    public static final class Run extends Base {
        @Override
        boolean available(AnActionEvent e) {
            if (e.getPlace().equals("EditorPopup") || e.getPlace().equals("ProjectViewPopup")) return psiFile(e) instanceof PsiJavaFile;
            return true;
        }

        @Override
        public void actionPerformed(AnActionEvent e) {
            Project project = e.getProject();
            PsiFile f = psiFile(e);
            if (!(f instanceof PsiJavaFile)) {
                Say.info(project, "פתחו קובץ Java (תרגיל) ואז לחצו \"הרצה ויזואלית\".", VisualRunner.TITLE);
                return;
            }
            VisualRunner.run(project, (PsiJavaFile) f);
        }
    }

    // ------------------------------------------------------------------ תרגיל חדש

    public static class NewExercise extends Base {
        String preset() {
            return null;
        }

        @Override
        public void actionPerformed(AnActionEvent e) {
            newExercise(e.getProject(), preset());
        }
    }

    public static void newExercise(Project project, String preset) {
        List<ExerciseDialog.Target> targets = ProjectFiles.targets(project);
        if (targets.isEmpty()) {
            Say.info(project, "פתחו פרויקט Java (או צרו חדש: File ← New ← Project) ונסו שוב.", "תרגיל חדש");
            return;
        }
        ExerciseDialog d = new ExerciseDialog(project, targets, preset);
        if (!d.showAndGet() || d.options() == null) return;
        createExercise(project, d.target(), d.options());
    }

    /** יוצר את הקובץ של התרגיל, מוודא שהספרייה במודול ופותח עם הסמן במקום הכתיבה */
    static VirtualFile createExercise(Project project, ExerciseDialog.Target target, Scaffold.Options o) {
        try {
            ProjectFiles.ensureSourceRoot(project, target.dir);
            Scaffold.Result r = Scaffold.generate(o);
            VirtualFile f = ProjectFiles.create(project, target.dir, o.className + ".java", r.code);
            com.intellij.openapi.module.Module m = com.intellij.openapi.module.ModuleUtilCore.findModuleForFile(f, project);
            if (m != null && !LibrarySetup.hasLibrary(m)) {
                Boolean need = com.intellij.openapi.project.DumbService.getInstance(project).isDumb() ? Boolean.TRUE : LibrarySetup.needs(m);
                if (need) LibrarySetup.add(m);
            }
            ProjectFiles.open(project, f, r.cursorLine);
            StructureInserter.showClassInfo(project, o.type);
            return f;
        } catch (Exception ex) {
            Say.error(project, "לא הצלחתי ליצור את התרגיל: " + ex.getMessage(), "תרגיל חדש");
            return null;
        }
    }

    // ------------------------------------------------------------------ הוסף מבנה נתונים

    public static final class Insert extends Base {
        @Override
        boolean available(AnActionEvent e) {
            if (e.getPlace().equals("EditorPopup")) return psiFile(e) instanceof PsiJavaFile;
            return true;
        }

        @Override
        public void actionPerformed(AnActionEvent e) {
            Editor editor = e.getData(CommonDataKeys.EDITOR);
            if (editor == null && e.getProject() != null) editor = com.intellij.openapi.fileEditor.FileEditorManager.getInstance(e.getProject()).getSelectedTextEditor();
            PsiFile f = editor == null ? null : com.intellij.psi.PsiDocumentManager.getInstance(e.getProject()).getPsiFile(editor.getDocument());
            StructureInserter.run(e.getProject(), editor, f);
        }
    }

    // ------------------------------------------------------------------ שליחת תרגיל (רק כשיש שרת)

    public static final class Submit extends Base {
        @Override
        boolean available(AnActionEvent e) {
            return IjShared.workerConfigured();
        }

        @Override
        public void actionPerformed(AnActionEvent e) {
            Submitter.run(e.getProject());
        }
    }

    // ------------------------------------------------------------------ דוגמאות, הדרכה, כיבוי

    public static final class Examples extends Base {
        @Override
        public void actionPerformed(AnActionEvent e) {
            examples(e.getProject());
        }
    }

    static void examples(Project project) {
        List<ExerciseDialog.Target> targets = ProjectFiles.targets(project);
        if (targets.isEmpty()) return;
        String[] names = new String[ProjectFiles.EXAMPLES.size()];
        for (int i = 0; i < names.length; i++) {
            String n = ProjectFiles.EXAMPLES.get(i), d = ProjectFiles.describeExample(n);
            if (d.length() > 52) d = d.substring(0, 50).replaceAll("\\s+\\S*$", "") + "…";
            names[i] = (d.isEmpty() ? "" : d + "   ") + "(" + n + ")";
        }
        int k = Say.choose(project, "הדוגמאות נכנסות לתיקייה examples בפרויקט (פעם אחת). איזו לפתוח?", "דוגמאות פתורות",
                Dsviz.EXAMPLES, names);
        if (k < 0) return;
        try {
            File src = targets.get(0).dir;
            ProjectFiles.ensureSourceRoot(project, src);
            List<VirtualFile> files = ProjectFiles.copyExamples(project, src);
            com.intellij.openapi.module.Module m = com.intellij.openapi.module.ModuleUtilCore.findModuleForFile(files.get(k), project);
            if (m != null && !LibrarySetup.hasLibrary(m) && !com.intellij.openapi.project.DumbService.getInstance(project).isDumb() && LibrarySetup.needs(m))
                LibrarySetup.add(m);
            ProjectFiles.open(project, files.get(k), 0);
        } catch (Exception ex) {
            Say.error(project, "לא הצלחתי להעתיק את הדוגמאות: " + ex.getMessage(), "דוגמאות פתורות");
        }
    }

    public static final class HelpAction extends Base implements DumbAware {
        @Override
        public void actionPerformed(AnActionEvent e) {
            Help.run(e.getProject());
        }
    }

    /** השאלה לפני כיבוי: מה ייעלם, ושהקוד לא משתנה */
    static Notice.Spec powerOff() {
        return new Notice.Spec(Modern.Kind.QUESTION, "DSViz", "לכבות את DSViz?")
                .text("הכפתורים, ההרצה הוויזואלית, ההצעות לפתרון וההוספה האוטומטית של ספריית הקורס ייכבו. הקוד והפרויקטים לא משתנים.\n"
                        + "מפעילים שוב בתפריט \"מבני נתונים\" ← הפעלת DSViz.")
                .buttons("כיבוי", "ביטול");
    }

    /** כיבוי (אדום) כש-DSViz פועל; הפעלה (ירוק) כשהוא כבוי */
    public static final class Power extends AnAction implements DumbAware {
        @Override
        public ActionUpdateThread getActionUpdateThread() {
            return ActionUpdateThread.BGT;
        }

        @Override
        public void update(AnActionEvent e) {
            boolean on = Dsviz.on();
            e.getPresentation().setText(on ? "כיבוי DSViz" : "הפעלת DSViz");
            e.getPresentation().setDescription(on ? "כיבוי DSViz: כל הכפתורים והיכולות נכבים, ונשאר רק IntelliJ" : "הפעלת DSViz: מחזיר את כל הכפתורים והיכולות");
            e.getPresentation().setIcon(on ? Dsviz.POWER_OFF : Dsviz.POWER_ON);
        }

        @Override
        public void actionPerformed(AnActionEvent e) {
            boolean on = !Dsviz.on();
            Project p = e.getProject();
            if (!on && !new Notice(p, powerOff()).ask()) return;
            Dsviz.setOn(on);
            if (p != null) {
                if (on) LibrarySetup.ensureAll(p);
                com.intellij.codeInsight.daemon.DaemonCodeAnalyzer.getInstance(p).restart();
                DsvizToolWindow.refresh(p);
            }
        }
    }
}
