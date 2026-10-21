package unit4.collectionsLib;

/** מדידת זמני עלייה (רק עם -Ddsviz.timing=true) — לבדיקות ביצועים */
final class Timing {
    private Timing() {}

    static final boolean ON = Boolean.getBoolean("dsviz.timing");
    private static final long T0 = System.currentTimeMillis();

    static void mark(String what) {
        if (ON) System.err.println("[timing] +" + (System.currentTimeMillis() - T0) + "ms  " + what
                + (Boolean.getBoolean(LineByLine.CHILD_PROPERTY) ? "  (ילד)" : "  (אב)"));
    }
}
