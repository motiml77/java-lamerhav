package il.lamerhav.dsviz.intellij;

import com.intellij.execution.lineMarker.RunLineMarkerContributor;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiIdentifier;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.util.PsiMethodUtil;

/** "הרצה ויזואלית" בכפתור שבצד (gutter) ליד main — נוסף לתפריט של החץ הירוק של IntelliJ (Run / Debug / הרצה ויזואלית) */
public final class RunLineMarker extends RunLineMarkerContributor {
    @Override
    public Info getInfo(PsiElement e) {
        if (!(e instanceof PsiIdentifier) || !(e.getParent() instanceof PsiMethod) || !Dsviz.on()) return null;
        PsiMethod m = (PsiMethod) e.getParent();
        if (m.getNameIdentifier() != e || !PsiMethodUtil.isMainMethod(m)) return null;
        AnAction run = ActionManager.getInstance().getAction("DSViz.Run");
        if (run == null) return null;
        return new Info(Dsviz.RUN, new AnAction[] { run }, el -> "הרצה ויזואלית (DSViz): כל שורה, המשתנים ומבני הנתונים");
    }
}
