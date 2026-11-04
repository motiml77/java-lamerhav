package il.lamerhav.dsviz;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.handlers.HandlerUtil;

/**
 * הכפתור "השאלה": פותח (או מביא לקדמה) את החלון הצף עם השאלה של התרגיל שפתוח בעורך; אם העורך לא שייך לתרגיל מהמאגר —
 * השאלה האחרונה שהוצגה; ואם אין — הסבר קצר.
 */
public class BankQuestionHandler extends AbstractHandler {
    @Override
    public Object execute(ExecutionEvent event) {
        if (!Power.on()) return null;
        IWorkbenchPage page = HandlerUtil.getActiveWorkbenchWindow(event).getActivePage();
        String id = BankFloat.questionOfEditor(page);
        BankFloat open = BankFloat.current();
        if (id == null && open != null) id = open.question();
        BankFloat.show(page, id);
        return null;
    }
}
