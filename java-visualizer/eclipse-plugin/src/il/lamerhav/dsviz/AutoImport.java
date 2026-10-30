package il.lamerhav.dsviz;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.eclipse.jdt.core.Flags;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.compiler.IProblem;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.PlatformUI;

/**
 * פעולות העזר מהספרייה (size / duplicate) "מוטמעות" אוטומטית: ברגע שהתלמיד כותב size(q) או duplicate(q)
 * והקומפיילר לא מכיר אותן — נוספת לבד בראש הקובץ השורה import static unit4.collectionsLib.Helpers.*;
 * כך לא צריך לכתוב Helpers. ולא צריך לזכור את השורה, ובקבצים שלא משתמשים בהן אין import מיותר (ואין אזהרה צהובה).
 * רץ מהקומפיילר בזמן הכתיבה (MentorParticipant.reconcile), רק כש-DSViz פועל.
 */
final class AutoImport {
    static final String HELPERS = "unit4.collectionsLib.Helpers";
    private static final Set<String> NAMES = new HashSet<String>(java.util.Arrays.asList("size", "duplicate"));
    private static final Set<String> pending = Collections.synchronizedSet(new HashSet<String>());

    private AutoImport() {
    }

    /** בעיות הקומפילציה של הקובץ: יש קריאה "ערומה" ל-size( / duplicate( שלא מוכרת? — מוסיפים את ה-import (פעם אחת) */
    static void check(final ICompilationUnit wc, IProblem[] problems) {
        if (wc == null || problems == null || !Power.on()) return;
        try {
            String source = null;
            boolean need = false;
            for (IProblem p : problems) {
                if (p == null || p.getID() != IProblem.UndefinedMethod) continue;
                String[] a = p.getArguments();
                if (a == null || a.length < 2 || !NAMES.contains(a[1])) continue;
                if (source == null) source = wc.getSource();
                if (bare(source, p.getSourceStart())) {
                    need = true;
                    break;
                }
            }
            if (!need) return;
            if (wc.getImport(HELPERS + ".*").exists()) return;
            if (wc.getJavaProject().findType(HELPERS) == null) return;   // ספרייה ישנה בפרויקט — בלי (ההסבר בעברית אומר מה לעשות)
            final String key = wc.getPath().toString();
            if (!pending.add(key)) return;
            Runnable add = () -> {
                try {
                    if (Power.on() && !wc.getImport(HELPERS + ".*").exists())
                        wc.createImport(HELPERS + ".*", null, Flags.AccStatic, null);
                } catch (Exception e) {
                    // לא הצליח — ההסבר בעברית אומר איזו שורה להוסיף
                } finally {
                    pending.remove(key);
                }
            };
            // בעורך: בחוט של המסך (הקובץ הפתוח מתעדכן מיד). בלי מסך (בדיקות, בנייה ברקע) — בעבודת רקע
            if (PlatformUI.isWorkbenchRunning()) Display.getDefault().asyncExec(add);
            else Job.create("DSViz — import של פעולות העזר", m -> { add.run(); return org.eclipse.core.runtime.Status.OK_STATUS; }).schedule();
        } catch (Exception e) {
            // לא מפריעים לעורך
        }
    }

    /** size(...) בלי משהו לפניו (לא s.size()) */
    static boolean bare(String source, int start) {
        if (source == null || start <= 0 || start > source.length()) return start == 0;
        int i = start - 1;
        while (i >= 0 && Character.isWhitespace(source.charAt(i))) i--;
        return i < 0 || source.charAt(i) != '.';
    }
}
