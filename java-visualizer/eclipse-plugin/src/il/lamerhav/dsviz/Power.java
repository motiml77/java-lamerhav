package il.lamerhav.dsviz;

import java.util.Collections;
import java.util.Map;

import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.eclipse.core.runtime.preferences.InstanceScope;
import org.eclipse.ui.AbstractSourceProvider;
import org.eclipse.ui.ISources;

/**
 * DSViz פועל או כבוי (לחצן הכיבוי האדום / ההפעלה הירוק בסוף סרגל הכלים). נשמר בהעדפות, ונשאר גם אחרי סגירת Eclipse.
 * כשכבוי: הכפתורים והתפריטים מוסתרים (המשתנה il.lamerhav.dsviz.active ב-plugin.xml), ההסברים בעברית, ההצעות לפתרון,
 * הוספת הספרייה לפרויקטים וה-import האוטומטי לא פועלים — נשאר רק Eclipse עצמו.
 */
public final class Power extends AbstractSourceProvider {
    public static final String VAR = "il.lamerhav.dsviz.active";
    private static final String NODE = "il.lamerhav.dsviz";
    private static final String KEY = "enabled";
    private static Power instance;

    public Power() {
        instance = this;
    }

    public static boolean on() {
        try {
            return InstanceScope.INSTANCE.getNode(NODE).getBoolean(KEY, true);
        } catch (Throwable e) {
            return true;
        }
    }

    static void set(boolean on) {
        try {
            IEclipsePreferences p = InstanceScope.INSTANCE.getNode(NODE);
            p.putBoolean(KEY, on);
            p.flush();
        } catch (Exception e) {
            // לא נשמר — המצב נשאר עד סגירת Eclipse
        }
        Power p = instance;
        if (p != null) p.fireSourceChanged(ISources.WORKBENCH, VAR, Boolean.valueOf(on));   // Boolean: <equals value="true"/> משווה ל-Boolean
    }

    @Override
    public Map<String, Object> getCurrentState() {
        return Collections.singletonMap(VAR, (Object) Boolean.valueOf(on()));
    }

    @Override
    public String[] getProvidedSourceNames() {
        return new String[] { VAR };
    }

    @Override
    public void dispose() {
        if (instance == this) instance = null;
    }
}
