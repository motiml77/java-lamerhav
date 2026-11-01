package il.lamerhav.dsviz.intellij;

import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.Window;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

import javax.imageio.ImageIO;

import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;

/**
 * בדיקה אוטומטית בתוך IntelliJ אמיתי (לצילומי מסך ב-Xvfb) — פעילה רק עם ‎-Ddsviz.autotest="צעד;צעד;...":
 *   open:src/Main.java@12 · insert · exercise · choose:stack_queue · ok · cancel · submitdialog:a.png,b.png · submit:a.png,b.png
 *   solution · help · run · menu · toolwindow · type:טקסט · wait:3000 · shot:שם · quit
 * (ערכת נושא בהירה / כהה: מחליפים ב-Settings לפני ההרצה — כך צולמו Light ו-Darcula)
 * הצילומים נשמרים בתיקייה ‎-Ddsviz.autotest.out (ברירת מחדל: ‎/tmp/dsviz-shots).
 */
final class Autotest {
    private Autotest() {}

    static void start(Project project, String spec) {
        DumbService.getInstance(project).runWhenSmart(() -> {
            Thread t = new Thread(() -> run(project, spec), "DSViz autotest");
            t.setDaemon(true);
            t.start();
        });
    }

    private static File out() {
        File d = new File(System.getProperty("dsviz.autotest.out", "/tmp/dsviz-shots"));
        d.mkdirs();
        return d;
    }

    private static void log(String s) {
        System.out.println("[dsviz-autotest] " + s);
    }

    private static void run(Project project, String spec) {
        sleep(4000);
        for (String step : spec.split(";")) {
            step = step.trim();
            if (step.isEmpty()) continue;
            int k = step.indexOf(':');
            String cmd = k < 0 ? step : step.substring(0, k), arg = k < 0 ? "" : step.substring(k + 1);
            log(step);
            try {
                step(project, cmd, arg);
            } catch (Throwable e) {
                log("נכשל: " + step + " — " + e);
            }
            sleep(cmd.equals("wait") || cmd.equals("shot") ? 0 : 1800);
        }
    }

    private static void step(Project project, String cmd, String arg) throws Exception {
        switch (cmd) {
            case "wait": sleep(Integer.parseInt(arg)); break;
            case "shot": shot(arg); break;
            case "open": {
                String[] p = arg.split("@");
                VirtualFile f = LocalFileSystem.getInstance().refreshAndFindFileByPath(new File(project.getBasePath(), p[0]).getPath());
                int line = p.length > 1 ? Integer.parseInt(p[1]) - 1 : 0;
                edtWrite(() -> new OpenFileDescriptor(project, f, line, 8).navigate(true));
                break;
            }
            case "insert": later(() -> StructureInserter.run(project, editor(project), file(project))); break;
            case "exercise": later(() -> Actions.newExercise(project, arg.isEmpty() ? null : arg)); break;
            case "examples": later(() -> Actions.examples(project)); break;
            case "choose": edt(() -> {
                DialogWrapper d = dialog();
                String[] p = arg.split("_");
                String inside = p.length > 1 ? p[1] : "";
                if (d instanceof InsertDialog) ((InsertDialog) d).choose(p[0], inside);
                if (d instanceof ExerciseDialog) ((ExerciseDialog) d).choose(p[0], inside);
            }); break;
            case "addfiles": edt(() -> {
                DialogWrapper d = dialog();
                if (d instanceof SubmitDialog) for (File f : files(arg)) ((SubmitDialog) d).add(f, f.getName());
            }); break;
            case "ok": edt(() -> { DialogWrapper d = dialog(); if (d != null) d.clickDefaultButton(); }); break;
            case "cancel": edt(() -> { DialogWrapper d = dialog(); if (d != null) d.doCancelAction(); }); break;
            case "submitdialog": later(() -> {
                SubmitDialog d = new SubmitDialog(project, ProjectFiles.targets(project));
                for (File f : files(arg)) d.add(f, f.getName());
                Submitter.runDialog(project, d);
            }); break;
            case "submit": later(() -> {
                List<SubmitDialog.Item> items = new ArrayList<SubmitDialog.Item>();
                for (File f : files(arg)) items.add(new SubmitDialog.Item(f, f.getName()));
                Submitter.submit(project, items, "", ProjectFiles.targets(project).get(0));
            }); break;
            case "submitflow": later(() -> Submitter.run(project)); break;
            case "solution": later(() -> {
                il.lamerhav.dsviz.IjShared.Solution sol = Solutions.atCaret(editor(project), file(project));
                if (sol != null) Solutions.show(project, sol); else log("אין הצעה לפעולה שבמקום הסמן");
            }); break;
            case "help": later(() -> Help.run(project)); break;
            case "run": later(() -> {
                PsiFile f = file(project);
                if (f instanceof PsiJavaFile) VisualRunner.run(project, (PsiJavaFile) f);
            }); break;
            case "menu": later(() -> {
                ActionGroup g = (ActionGroup) ActionManager.getInstance().getAction("DSViz.Menu");
                Window w = com.intellij.openapi.wm.WindowManager.getInstance().getFrame(project);
                DataContext dc = com.intellij.ide.DataManager.getInstance().getDataContext(w);
                JBPopupFactory.getInstance().createActionGroupPopup("מבני נתונים", g, dc, JBPopupFactory.ActionSelectionAid.MNEMONICS, true)
                        .showInFocusCenter();
            }); break;
            case "escape": edt(() -> {
                for (com.intellij.openapi.ui.popup.JBPopup p : JBPopupFactory.getInstance().getChildPopups(com.intellij.openapi.wm.WindowManager.getInstance().getFrame(project))) p.cancel();
            }); break;
            case "toolwindow": edt(() -> {
                ToolWindow w = ToolWindowManager.getInstance(project).getToolWindow(DsvizToolWindow.ID);
                if (w != null) { if (arg.equals("hide")) w.hide(); else w.activate(null); }
            }); break;
            case "tw": edt(() -> {
                ToolWindow w = ToolWindowManager.getInstance(project).getToolWindow(arg);
                if (w != null) w.activate(null);
            }); break;
            case "clearnotes": edt(() -> {
                for (com.intellij.notification.Notification n : com.intellij.notification.NotificationsManager.getNotificationsManager()
                        .getNotificationsOfType(com.intellij.notification.Notification.class, project)) n.expire();
            }); break;
            case "type": edtWrite(() -> {
                Editor e = editor(project);
                com.intellij.openapi.command.WriteCommandAction.runWriteCommandAction(project, () ->
                        e.getDocument().insertString(e.getCaretModel().getOffset(), arg.replace("\\n", "\n")));
                PsiDocumentManager.getInstance(project).commitAllDocuments();
            }); break;
            case "caret": edt(() -> {
                Editor e = editor(project);
                int idx = e.getDocument().getText().indexOf(arg);
                if (idx >= 0) e.getCaretModel().moveToOffset(idx);
            }); break;
            case "undo": edtWrite(() -> com.intellij.openapi.fileEditor.FileDocumentManager.getInstance().reloadFiles(FileEditorManager.getInstance(project).getSelectedFiles())); break;
            case "quit": later(() -> ApplicationManager.getApplication().exit(true, true, false)); break;
            default: log("צעד לא מוכר: " + cmd);
        }
    }

    private static List<File> files(String arg) {
        List<File> l = new ArrayList<File>();
        for (String s : arg.split(",")) if (!s.trim().isEmpty()) l.add(new File(s.trim()));
        return l;
    }

    private static Editor editor(Project project) {
        return FileEditorManager.getInstance(project).getSelectedTextEditor();
    }

    private static PsiFile file(Project project) {
        Editor e = editor(project);
        return e == null ? null : PsiDocumentManager.getInstance(project).getPsiFile(e.getDocument());
    }

    /** החלון (DialogWrapper) הפעיל — הדיאלוג העליון שפתוח */
    private static DialogWrapper dialog() {
        Window[] ws = Window.getWindows();
        for (int i = ws.length - 1; i >= 0; i--) {
            if (!ws[i].isShowing() || !(ws[i] instanceof javax.swing.JDialog)) continue;
            DialogWrapper d = DialogWrapper.findInstance(((javax.swing.JDialog) ws[i]).getContentPane());
            if (d != null) return d;
        }
        return null;
    }

    static void shot(String name) {
        try {
            Rectangle r = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
            Rectangle all = new Rectangle(0, 0, GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice().getDisplayMode().getWidth(),
                    GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice().getDisplayMode().getHeight());
            BufferedImage img = new Robot().createScreenCapture(all.isEmpty() ? r : all);
            File f = new File(out(), name + ".png");
            ImageIO.write(img, "png", f);
            log("צילום: " + f);
        } catch (Exception e) {
            log("צילום נכשל: " + e);
        }
    }

    /** בחוט של המסך, גם כשחלון מודאלי פתוח (לחיצות בחלונות) */
    private static void edt(Runnable r) {
        ApplicationManager.getApplication().invokeAndWait(r, com.intellij.openapi.application.ModalityState.any());
    }

    /** בחוט של המסך, כשאין חלון מודאלי (שינויים בקוד) */
    private static void edtWrite(Runnable r) {
        ApplicationManager.getApplication().invokeAndWait(r, com.intellij.openapi.application.ModalityState.nonModal());
    }

    private static void later(Runnable r) {
        ApplicationManager.getApplication().invokeLater(r);
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }
}
