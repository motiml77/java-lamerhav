package il.lamerhav.dsviz.bank;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/** הכפתורים בסרגל העליון (ליד "הרצה ויזואלית") ובתפריט Tools: "מאגר תרגילים" ו"השאלה" */
public final class BankActions {
    private BankActions() {}

    abstract static class Base extends AnAction {
        abstract String mode();

        @Override
        public void actionPerformed(AnActionEvent e) {
            BankLauncher.open(e.getProject(), mode());
        }

        @Override
        public void update(AnActionEvent e) {
            e.getPresentation().setEnabledAndVisible(e.getProject() != null);
        }

        @Override
        public ActionUpdateThread getActionUpdateThread() {
            return ActionUpdateThread.BGT;
        }
    }

    /** חלון המאגר: נושאים, שאלות לפי קושי, "צור תרגיל" */
    public static final class Bank extends Base {
        String mode() {
            return "bank";
        }
    }

    /** החלון הצף של השאלה האחרונה. פעיל רק כשיש שאלה להציג: הקובץ שפתוח בעורך הוא תרגיל מהמאגר, או שחלון השאלה פתוח */
    public static final class Question extends Base {
        String mode() {
            return "question";
        }

        @Override
        public void update(AnActionEvent e) {
            Project p = e.getProject();
            e.getPresentation().setVisible(p != null);
            e.getPresentation().setEnabled(p != null && questionAvailable(p, e.getData(CommonDataKeys.VIRTUAL_FILE)));
        }
    }

    /** האם יש שאלה להציג: חלון השאלה פתוח, או שהקובץ הנוכחי נוצר מהמאגר (לפי קובץ ההתקדמות) */
    static boolean questionAvailable(Project p, VirtualFile file) {
        try {
            BankLauncher.Session s = BankLauncher.SESSIONS.get(p);
            if (s != null && s.proc != null && s.proc.isAlive()) return true;
            if (file == null) return false;
            File f = new File(System.getProperty("user.home"), ".dsviz/bank-progress-ij.json");
            if (!f.isFile()) return false;
            String text = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8).replace("\\\\", "/").replace('\\', '/');
            String path = file.getPath().replace('\\', '/');
            return text.toLowerCase().contains(path.toLowerCase());
        } catch (Throwable t) {
            return true;   // לא חוסמים את הכפתור אם הבדיקה נכשלה
        }
    }
}
