package unit4.collectionsLib;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;

/**
 * הרצה "שורה אחר שורה": מריץ תוכנית של תלמיד כך שכל שורה בקוד שלה מוצגת כצעד,
 * יחד עם המשתנים המקומיים והמבנים.
 *
 * <pre>
 *   java -cp ... unit4.collectionsLib.LineByLine Targil1      (כך מריצים התוספים של Eclipse)
 *   java -javaagent:dsviz.jar=Targil1 -cp ... Targil1         (כך מריץ התוסף של VS Code)
 *   או: Viz.lines(); כשורה הראשונה ב-main                       (בלי תוסף)
 * </pre>
 *
 * איך זה עובד: התהליך הזה מפעיל תהליך Java נוסף ("ילד") שמריץ את התוכנית תחת ממשק הדיבאג של Java (JDI),
 * ועוצר אותו לפני כל שורה בקוד של התלמיד כדי לתעד אותה. דורש JDK 11 ומעלה — אחרת מריצים רגיל.
 */
public final class LineByLine {
    private LineByLine() {}

    static final String CHILD_PROPERTY = "dsviz.child";

    public static void main(String[] args) throws Throwable {
        Timing.mark("main התחיל");
        if (args.length == 0) {
            System.err.println("שימוש: LineByLine <שם המחלקה עם main> [ארגומנטים...]");
            System.exit(2);
        }
        String mainClass = args[0];
        String[] rest = Arrays.copyOfRange(args, 1, args.length);
        if (Boolean.getBoolean(CHILD_PROPERTY)) {
            runChild(mainClass, rest);
        } else {
            System.exit(trace(mainClass, rest));
        }
    }

    /** קובץ שהילד יוצר כשהחלון מוכן (וחלון ה"טוען" נסגר), או null */
    static java.io.File splashReady;

    /** האם אנחנו בתוך התהליך שרץ תחת ה-Tracer */
    static boolean isChild() {
        return Boolean.getBoolean(CHILD_PROPERTY);
    }

    /** מריץ את התוכנית שורה-שורה ומחזיר את קוד היציאה שלה */
    static int trace(String mainClass, String[] args) {
        String why = null;
        splashReady = Splash.show();   // "טוען..." מיד — עוד לפני שהתהליך של התוכנית עלה
        try {
            Class.forName("com.sun.jdi.Bootstrap");
            Class<?> tracer = Class.forName("unit4.collectionsLib.JdiTracer");
            Method run = tracer.getDeclaredMethod("run", String.class, String[].class);
            run.setAccessible(true);
            return (Integer) run.invoke(null, mainClass, args);
        } catch (ClassNotFoundException | UnsupportedClassVersionError | NoClassDefFoundError e) {
            why = "הרצה שורה-שורה דורשת JDK 11 ומעלה (עם ממשק הדיבאג jdk.jdi). מריצים רגיל.";
        } catch (InvocationTargetException e) {
            why = "הרצה שורה-שורה נכשלה (" + e.getCause() + "). מריצים רגיל.";
        } catch (ReflectiveOperationException e) {
            why = "הרצה שורה-שורה נכשלה (" + e + "). מריצים רגיל.";
        }
        System.err.println(why);
        try {
            runChild(mainClass, args);
            return 0;
        } catch (Throwable t) {
            t.printStackTrace();
            return 1;
        }
    }

    /** בתהליך הילד: מפעיל את מצב השורות ומריץ את main של התלמיד */
    static void runChild(String mainClass, String[] args) throws Throwable {
        Timing.mark("runChild התחיל");
        Recorder.active = true;   // הרצה ויזואלית — גם כשאין JDI ומריצים כאן בלי שורות
        if (isChild()) {
            Recorder.initLineMode();
            Recorder.preload();
        }
        Method main = Class.forName(mainClass).getMethod("main", String[].class);
        try {
            main.invoke(null, (Object) args);
        } catch (InvocationTargetException e) {
            Recorder.failed(e.getCause());   // השגיאה כצעד אחרון בחלון — עם הסבר מה קרה (Problems)
            throw e.getCause();
        } finally {
            mainDone();
            Recorder.drainLines();   // השורות האחרונות (אחרי הפעולה האחרונה על מבנה)
        }
    }

    /** ה-JdiTracer שם כאן נקודת עצירה: main של התלמיד הסתיים — מפסיקים לעקוב שורה-שורה */
    static void mainDone() {
    }
}
