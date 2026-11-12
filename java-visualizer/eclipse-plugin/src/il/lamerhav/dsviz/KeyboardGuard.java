package il.lamerhav.dsviz;

import java.lang.reflect.Method;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CaretListener;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;

/**
 * Windows עם מקלדת עברית: StyledText של SWT מחליף לבד את שפת ההקלדה לפי כיוון הטקסט שליד הסמן (BidiUtil.setKeyboardLanguage)
 * — ליד הערה בעברית בקוד, או כשנפתח חלון ריחוף עם הסבר בעברית — והתלמיד ממשיך להקליד קוד באותיות עבריות.
 * כאן מבטלים את ההחלפות האוטומטיות: רק התלמיד מחליף שפה (Alt+Shift / Win+רווח), כמו ב-VS Code וב-IntelliJ.
 * דרך reflection: BidiUtil היא מחלקה פנימית של SWT — אם היא לא קיימת (גרסה אחרת / מערכת אחרת) פשוט לא עושים כלום.
 */
final class KeyboardGuard {
    private static final String KEY = "dsviz.keyboardGuard";
    private static Method get, set;
    private static int typing = -1;   // השפה שהתלמיד מקליד בה עכשיו

    private KeyboardGuard() {}

    static void install(final Display d) {
        try {
            Class<?> c = Class.forName("org.eclipse.swt.internal.BidiUtil");
            if (!(Boolean) c.getMethod("isBidiPlatform").invoke(null)) return;   // אין מקלדת עברית / ערבית — אין מה לתקן
            get = c.getMethod("getKeyboardLanguage");
            set = c.getMethod("setKeyboardLanguage", int.class);
            typing = lang();
        } catch (Throwable t) {
            return;
        }
        if (typing < 0) return;
        // תזוזת סמן (CaretMoved מגיע לפני ש-StyledText מחשב כיוון ומחליף שפה): זוכרים את השפה, ומחזירים אותה מיד אחר כך
        final CaretListener caret = e -> {
            final int before = lang();
            if (before < 0) return;
            typing = before;
            d.asyncExec(() -> restore(before));
        };
        // כל StyledText משמאל לימין שמקבל פוקוס: עורך הקוד, Console, חלון ההצעה לפתרון
        d.addFilter(SWT.FocusIn, e -> {
            if (!(e.widget instanceof StyledText) || e.widget.getData(KEY) != null) return;
            StyledText st = (StyledText) e.widget;
            if ((st.getStyle() & SWT.RIGHT_TO_LEFT) != 0) return;
            st.setData(KEY, Boolean.TRUE);
            st.addCaretListener(caret);
        });
        // חלון ריחוף / חלון קטן שנפתח (הסבר בעברית מעל השגיאה) — לא משנה את שפת ההקלדה
        d.addFilter(SWT.Show, e -> { if (e.widget instanceof Shell) d.asyncExec(() -> restore(typing)); });
        // החלפה מכוונת של התלמיד (Alt+Shift / Ctrl+Shift / מקש Windows) — זו השפה החדשה
        d.addFilter(SWT.KeyUp, e -> {
            if ((e.keyCode & SWT.MODIFIER_MASK) != 0 || e.keyCode == SWT.COMMAND) d.asyncExec(() -> { int l = lang(); if (l >= 0) typing = l; });
        });
    }

    private static int lang() {
        try { return (Integer) get.invoke(null); } catch (Throwable t) { return -1; }
    }

    private static void restore(int l) {
        if (l < 0) return;
        try { if (lang() != l) set.invoke(null, l); } catch (Throwable ignored) { /* לא חשוב */ }
    }
}
