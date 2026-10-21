package unit4.collectionsLib;

import java.lang.instrument.Instrumentation;

/**
 * נקודת כניסה להרצה שורה-שורה מ-VS Code: java -javaagent:dsviz.jar=Targil1 ... Targil1
 * רץ לפני main: מריץ את התוכנית שורה-שורה בתהליך נפרד, ויוצא בלי להריץ את main פעם נוספת.
 */
public final class LineAgent {
    private LineAgent() {}

    public static void premain(String mainClass, Instrumentation inst) {
        if (mainClass == null || mainClass.trim().isEmpty() || LineByLine.isChild()) return;
        System.exit(LineByLine.trace(mainClass.trim(), new String[0]));
    }
}
