package il.lamerhav.dsviz;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;

/** הכפתור "עמדת ניהול" בסרגל הכלים: פותח את חלון המורה (הקוד, ופתיחת עמדת הניהול בדפדפן) — מקדימה. פעיל רק למורה מחובר. */
public class TeacherPanelHandler extends AbstractHandler {
    @Override
    public Object execute(ExecutionEvent event) {
        if (!Power.on()) return null;
        if (AccessGate.isTeacher()) AccessGate.showTeacherPanel(null);
        return null;
    }
}
