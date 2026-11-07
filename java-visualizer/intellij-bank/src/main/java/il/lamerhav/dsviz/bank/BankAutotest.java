package il.lamerhav.dsviz.bank;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.impl.SimpleDataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actionSystem.TypedAction;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.WindowManager;

/**
 * בדיקה ב-IntelliJ אמיתי (‎-Ddsviz.bankAutotest=<תיקיית פלט>, רק בבדיקות): כשהפרויקט נפתח — מפעילים את הכלי עם תסריט (BankIjIt,
 * מ-‎-Ddsviz.bankAutotestCp), שמשתמש בגשר האמיתי. בבדיקה בלבד הגשר מקבל עוד פקודות: TYPE (הקלדה בעורך — כמו מקלדת, דרך
 * TypedAction), UNDO, STATE (מה פתוח בעורך ובאיזו שורה), IDERUN (לחיצה על "הרצה ויזואלית" בסרגל) ו-CLOSEDIALOG. בסוף — IntelliJ נסגר.
 */
public final class BankAutotest implements StartupActivity.DumbAware {
    static boolean active() {
        String p = System.getProperty("dsviz.bankAutotest");
        return p != null && !p.isEmpty();
    }

    @Override
    public void runActivity(Project project) {
        if (!active()) return;
        Thread t = new Thread(() -> run(project), "dsviz-bank-autotest");
        t.setDaemon(true);
        t.start();
    }

    static void run(Project project) {
        File out = new File(System.getProperty("dsviz.bankAutotest"));
        out.mkdirs();
        StringBuilder log = new StringBuilder();
        try {
            DumbService.getInstance(project).waitForSmartMode();
            Thread.sleep(6000);
            String bad = BankLauncher.problem(project);
            log.append("problem=").append(bad).append('\n');
            BankLauncher.Session s = BankLauncher.session(project);
            List<String> cmd = BankLauncher.command(project, s, "bank", "il.lamerhav.dsviz.BankIjIt");
            cmd.add("--it-out");
            cmd.add(out.getPath());
            log.append("cmd=").append(String.join(" ", cmd)).append('\n');
            Process p = new ProcessBuilder(cmd).redirectErrorStream(true).redirectOutput(new File(out, "tool.log")).start();
            s.proc = p;
            boolean ended = p.waitFor(25, TimeUnit.MINUTES);
            log.append("tool-exit=").append(ended ? String.valueOf(p.exitValue()) : "timeout").append('\n');
            log.append("received=").append(s.received).append('\n');
        } catch (Throwable e) {
            log.append("error=").append(e).append('\n');
        }
        try {
            Files.write(new File(out, "autotest.txt").toPath(), log.toString().getBytes(StandardCharsets.UTF_8));
        } catch (Exception ignored) {
            // אין לאן לכתוב
        }
        ApplicationManager.getApplication().invokeLater(() -> ApplicationManager.getApplication().exit(true, true, false));
    }

    /** IntelliJ מקדימה (מאחורי החלון הצף) — אחרי פתיחת קובץ */
    static void bringToFront(Project project) {
        java.awt.Frame f = WindowManager.getInstance().getFrame(project);
        if (f != null) f.toFront();
    }

    /** פקודות נוספות בגשר — רק בבדיקה */
    static String handle(BankLauncher.Session s, String[] p) {
        if (!active()) return "UNKNOWN";
        Project project = s.project;
        if (p[1].equals("TYPE")) {
            final String text = p.length > 2 ? p[2] : "";
            AtomicReference<String> r = new AtomicReference<String>("NO-EDITOR");
            ApplicationManager.getApplication().invokeAndWait(() -> {
                Editor ed = FileEditorManager.getInstance(project).getSelectedTextEditor();
                if (ed == null) return;
                ed.getContentComponent().requestFocusInWindow();
                DataContext dc = SimpleDataContext.getProjectContext(project);
                ed.getCaretModel().moveToOffset(ed.getDocument().getLineEndOffset(ed.getCaretModel().getLogicalPosition().line));
                WriteCommandAction.runWriteCommandAction(project, () -> {
                    TypedAction.getInstance().actionPerformed(ed, '\n', dc);
                    for (char ch : text.toCharArray()) TypedAction.getInstance().actionPerformed(ed, ch, dc);
                });
                r.set("OK");
            });   // הקלדה = שינוי בקוד: רק במצב רגיל (IntelliJ לא מרשה כתיבה עם ModalityState.any)
            return r.get();
        }
        if (p[1].equals("STATE")) {
            AtomicReference<String> r = new AtomicReference<String>("");
            ApplicationManager.getApplication().invokeAndWait(() -> {   // גם כשיש חלון דו-שיח פתוח (ModalityState.any)
                Editor ed = FileEditorManager.getInstance(project).getSelectedTextEditor();
                VirtualFile vf = ed == null ? null : FileDocumentManager.getInstance().getFile(ed.getDocument());
                StringBuilder open = new StringBuilder();
                for (VirtualFile f : FileEditorManager.getInstance(project).getOpenFiles()) open.append(f.getName()).append(',');
                r.set("OK file=" + (vf == null ? "-" : vf.getName()) + " line=" + (ed == null ? 0 : ed.getCaretModel().getLogicalPosition().line + 1)
                        + " text=" + (ed == null ? "" : ed.getDocument().getText().contains("int typed = 42;")) + " open=" + open
                        + " focus=" + (WindowManager.getInstance().getFrame(project) != null && WindowManager.getInstance().getFrame(project).isActive()));
            }, com.intellij.openapi.application.ModalityState.any());
            return r.get();
        }
        if (p[1].equals("UNDO")) {   // מחזירים את הקובץ כמו שהיה (בלי לשמור את ההקלדה של הבדיקה)
            ApplicationManager.getApplication().invokeAndWait(() -> {   // גם כשיש חלון דו-שיח פתוח (ModalityState.any)
                Editor ed = FileEditorManager.getInstance(project).getSelectedTextEditor();
                if (ed == null) return;
                WriteCommandAction.runWriteCommandAction(project, () -> {
                    String t = ed.getDocument().getText();
                    int k = t.indexOf("int typed = 42;");
                    if (k >= 0) {
                        int st = t.lastIndexOf('\n', k);
                        ed.getDocument().deleteString(Math.max(0, st), k + "int typed = 42;".length());
                    }
                });
                FileDocumentManager.getInstance().saveAllDocuments();
            });
            return "OK";
        }
        if (p[1].equals("IDERUN")) {   // כמו לחיצה על "הרצה ויזואלית" בסרגל של IntelliJ (הכפתור של DSViz), כשהקובץ פתוח בעורך
            final File f = new File(p.length > 2 ? p[2] : "");
            ApplicationManager.getApplication().invokeLater(() -> {
                VirtualFile vf = com.intellij.openapi.vfs.LocalFileSystem.getInstance().refreshAndFindFileByIoFile(f);
                if (vf == null) return;
                FileEditorManager.getInstance(project).openFile(vf, true);
                Editor ed = FileEditorManager.getInstance(project).getSelectedTextEditor();
                com.intellij.openapi.actionSystem.AnAction a = com.intellij.openapi.actionSystem.ActionManager.getInstance().getAction("DSViz.Run");
                if (ed == null || a == null) return;
                DataContext dc = com.intellij.ide.DataManager.getInstance().getDataContext(ed.getContentComponent());
                com.intellij.openapi.actionSystem.ex.ActionUtil.invokeAction(a, dc, com.intellij.openapi.actionSystem.ActionPlaces.MAIN_TOOLBAR, null, null);
            });
            return "OK";
        }
        if (p[1].equals("CLOSEDIALOG")) {   // סוגרים את חלון הדו-שיח הפתוח (כמו "סגירה")
            AtomicReference<String> r = new AtomicReference<String>("NONE");
            ApplicationManager.getApplication().invokeAndWait(() -> {
                for (java.awt.Window w : java.awt.Window.getWindows()) {
                    if (!(w instanceof java.awt.Dialog) || !((java.awt.Dialog) w).isModal() || !w.isShowing()) continue;
                    r.set("CLOSED " + ((java.awt.Dialog) w).getTitle());
                    com.intellij.openapi.ui.DialogWrapper dw = w instanceof javax.swing.RootPaneContainer
                            ? com.intellij.openapi.ui.DialogWrapper.findInstance(((javax.swing.RootPaneContainer) w).getRootPane()) : null;
                    if (dw != null) dw.close(com.intellij.openapi.ui.DialogWrapper.CANCEL_EXIT_CODE);
                    else w.dispose();
                }
            }, com.intellij.openapi.application.ModalityState.any());
            return r.get();
        }
        return "UNKNOWN";
    }
}
