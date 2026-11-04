package il.lamerhav.dsviz.bank;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;

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

    /** החלון הצף של השאלה האחרונה */
    public static final class Question extends Base {
        String mode() {
            return "question";
        }
    }
}
