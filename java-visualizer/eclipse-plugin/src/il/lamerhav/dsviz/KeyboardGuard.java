package il.lamerhav.dsviz;

import java.lang.reflect.Method;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CaretListener;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Shell;

/**
 * Windows עם מקלדת עברית: רק התלמיד מחליף את שפת ההקלדה ב-Eclipse — כמו ב-VS Code וב-IntelliJ.
 * <p>
 * הבעיה: StyledText של SWT (עורך הקוד, ה-Console, חלונות הריחוף) מחליף לבד את שפת המקלדת בכל פעם שהכיוון של הטקסט
 * שליד הסמן מתחלף (StyledText.setCaretLocations → BidiUtil.setKeyboardLanguage → ActivateKeyboardLayout של Windows):
 * ליד אות עברית — עברית, ליד קוד — אנגלית. זה קורה גם ברכיב שאין לו פוקוס: ה-Console מזיז את הסמן שלו בכל הדפסה
 * (IOConsoleViewer.repositionCaretAfterOutput), וחלון ריחוף מציב את הסמן בתחילת ההסבר.
 * <p>
 * הפתרון: "השפה של התלמיד" (typing) משתנה רק כשהתלמיד מחליף בעצמו —
 * <ul>
 *   <li>צירוף מקשים שכולו מקשי שינוי (Alt+Shift, Ctrl+Shift), או מקש Windows (Win+רווח), שאחריו השפה באמת התחלפה;</li>
 *   <li>חזרה ל-Eclipse מתוכנה אחרת / משורת המשימות (שם בוחרים שפה בעכבר) — השפה שנבחרה שם.</li>
 * </ul>
 * כל החלפה אחרת, מכל מקור, מוחזרת: מיד אחרי תזוזת סמן / פתיחת חלון, ובבדיקה קבועה (TICK_MS) כל עוד Eclipse הוא החלון
 * הפעיל ואין מקש שינוי לחוץ. כש-Eclipse לא פעיל — לא נוגעים בשפה (שפת ההקלדה ב-Windows משותפת לכל התוכנות).
 * לא "לומדים" מתזוזת סמן או מקיצור כמו Ctrl+S / Shift+אות — כך החלפה אוטומטית אחת לא הופכת לעברית תקועה.
 * <p>
 * דרך reflection: BidiUtil היא מחלקה פנימית של SWT — אם היא לא קיימת (גרסה אחרת / מערכת אחרת) לא עושים כלום.
 * הבדיקה על Windows אמיתי עם מקלדת עברית: tools/test/windows-keyboard/KeyboardGuardIt.java.
 */
final class KeyboardGuard {
    private static final String KEY = "dsviz.keyboardGuard";
    /** כל כמה זמן בודקים שהשפה לא הוחלפה מאחורי הגב (GetKeyboardLayout — קריאה זולה) */
    static final int TICK_MS = 100;
    /** כמה לחכות אחרי שחרור צירוף / חזרה ל-Eclipse לפני שקוראים את השפה ש-Windows החליף אליה */
    private static final int SETTLE_MS = 120;
    private static final long HELD_MAX_MS = 3000;

    private static Method get, set;
    private static Display display;
    private static volatile int typing = -1;   // השפה שהתלמיד בחר
    private static boolean held;                // מקש שינוי לחוץ — באמצע צירוף; לא מחזירים עכשיו
    private static long heldSince;              // מתי הצירוף התחיל (אם השחרור הלך לאיבוד — אחרי HELD_MAX_MS ממשיכים כרגיל)
    private static boolean clean;               // בצירוף הנוכחי נלחצו רק מקשי שינוי (או מקש Windows עם מקש נוסף)
    private static int comboStart = -1;         // השפה כשהצירוף התחיל
    private static boolean active = true;       // Eclipse הוא התוכנה הפעילה

    private KeyboardGuard() {}

    static void install(final Display d) {
        if (display != null) return;
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
        display = d;
        // תזוזת סמן: StyledText מחליף שפה מיד אחרי CaretMoved — מחזירים מיד אחר כך (בלי לחכות לבדיקה הקבועה)
        final CaretListener caret = e -> d.asyncExec(KeyboardGuard::restore);
        d.addFilter(SWT.FocusIn, e -> {
            if (!(e.widget instanceof StyledText) || e.widget.getData(KEY) != null) return;
            StyledText st = (StyledText) e.widget;
            st.setData(KEY, Boolean.TRUE);
            st.addCaretListener(caret);
        });
        // חלון ריחוף / חלון קטן שנפתח (הסבר בעברית) — מחזירים מיד
        d.addFilter(SWT.Show, e -> { if (e.widget instanceof Shell) d.asyncExec(KeyboardGuard::restore); });
        d.addFilter(SWT.KeyDown, KeyboardGuard::keyDown);
        d.addFilter(SWT.KeyUp, KeyboardGuard::keyUp);
        // יציאה מ-Eclipse (תוכנה אחרת, שורת המשימות) וחזרה אליו
        d.addFilter(SWT.Deactivate, e -> d.asyncExec(() -> {
            if (!d.isDisposed() && d.getActiveShell() == null) {
                active = false;
                held = false;
            }
        }));
        d.addFilter(SWT.Activate, e -> {
            if (active) return;
            active = true;
            // מה שנבחר בזמן שהיינו בחוץ (בתוכנה אחרת או בשורת המשימות) — זו השפה של התלמיד עכשיו
            d.timerExec(SETTLE_MS, () -> { int l = lang(); if (l >= 0) typing = l; });
        });
        d.timerExec(TICK_MS, new Runnable() {
            public void run() {
                if (d.isDisposed()) return;
                if (held && System.currentTimeMillis() - heldSince > HELD_MAX_MS) held = false;
                if (active && !held && d.getActiveShell() != null) restore();
                d.timerExec(TICK_MS, this);
            }
        });
    }

    private static boolean modifier(Event e) {
        return (e.keyCode & SWT.MODIFIER_MASK) != 0;
    }

    private static void keyDown(Event e) {
        if (modifier(e)) {
            if (!held) {   // צירוף חדש מתחיל
                held = true;
                heldSince = System.currentTimeMillis();
                clean = true;
                comboStart = lang();
            }
            return;
        }
        // מקש רגיל בתוך צירוף עם Alt / Ctrl / Shift — זה קיצור (Ctrl+S, Shift+A), לא החלפת שפה. עם מקש Windows (Win+רווח) — כן
        if (held && (e.stateMask & SWT.COMMAND) == 0) clean = false;
    }

    private static void keyUp(Event e) {
        if (!modifier(e)) return;
        if (((e.stateMask & SWT.MODIFIER_MASK) & ~e.keyCode) != 0) return;   // עוד מקש שינוי לחוץ — הצירוף לא נגמר
        final boolean wasClean = clean;
        final int start = comboStart;
        // Windows מחליף את השפה כשמשחררים את הצירוף — קוראים אחרי רגע; עד אז לא מחזירים (held)
        display.timerExec(SETTLE_MS, () -> {
            held = false;
            int l = lang();
            if (wasClean && l >= 0 && l != start) typing = l;   // התלמיד החליף שפה
            restore();
        });
    }

    /** השפה שהתלמיד בחר (לבדיקות) */
    static int typing() {
        return typing;
    }

    static int lang() {
        try { return (Integer) get.invoke(null); } catch (Throwable t) { return -1; }
    }

    private static void restore() {
        int l = typing;
        if (l < 0 || held) return;
        try { if (lang() != l) set.invoke(null, l); } catch (Throwable ignored) { /* לא חשוב */ }
    }
}
