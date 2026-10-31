package unit4.collectionsLib;

import javax.swing.JLabel;
import javax.swing.JTextPane;

import unit4.collectionsLib.Model.Kind;
import unit4.collectionsLib.Model.Step;

/**
 * בדיקה בלי חלון למעקב המשתנים (VarsPanel) — על קובץ צעדים של C# (Calls.cs ו-Fib.cs, דרך TraceViewer):
 * הכותרת אומרת באיזו פעולה; כשפעילות כמה פעולות — רשימה בגודל המחסנית; בחירת פעולה שמחכה מציגה את המשתנים שלה;
 * בכל צעד חדש חוזרים לפעולה שרצה (הבחירה נשמרת רק כשאותו צעד מצויר מחדש).
 *
 *   VarsPanelCheck calls <Calls.trace>   ·   VarsPanelCheck fib <Fib.trace>
 */
public class VarsPanelCheck {
    static int fails = 0;
    static final JLabel sub = new JLabel();
    static VarsPanel panel;

    static void check(boolean ok, String what) {
        System.out.println((ok ? "  ok  " : "  FAIL ") + what);
        if (!ok) {
            fails++;
            System.out.println("        כותרת: " + subtitle() + " | רשימה: " + panel.listSize() + " | מוצגת: " + panel.frame()
                    + " | " + panel.rows() + " | " + panel.noteText());
        }
    }

    static String subtitle() {
        return sub.getText().replaceAll("[‎‏]", "");
    }

    static void show(Step s) {
        panel.set(s, null, null);
    }

    static boolean has(String row) {
        return panel.rows().contains(row);
    }

    /** הצעד הראשון מ-from שבו הפעולה שרצה היא method והמחסנית בעומק depth (ואם x != null — עם המשתנה הזה) */
    static Step find(int from, String method, int depth, String var) {
        for (int i = from; i < Recorder.stepCount(); i++) {
            Step s = Recorder.step(i);
            if (s.calls.size() != depth || !s.calls.get(0).method.equals(method)) continue;
            if (var == null) return s;
            if (s.vars != null) for (String[] v : s.vars) if ((v[0] + "=" + v[1]).equals(var)) return s;
        }
        throw new IllegalStateException("לא נמצא צעד: " + method + " בעומק " + depth + " " + var);
    }

    public static void main(String[] a) throws Exception {
        Theme.install();   // הגופנים, כמו בחלון
        panel = new VarsPanel(sub, new JTextPane());
        TraceViewer.load(a[1]);
        if (a[0].equals("calls")) calls();
        else fib();
        System.out.println(fails == 0 ? "MARK-END" : fails + " נכשלו");
        System.exit(fails == 0 ? 0 : 1);
    }

    static void calls() {
        Step main = find(0, "Main", 1, null);
        show(main);
        check(panel.listSize() == 0 && subtitle().equals("בפעולה Main()"), "רק Main: בלי רשימה, והכותרת \"בפעולה Main()\"");

        Step rem = find(0, "Rem", 4, "x=8");
        show(rem);
        check(panel.frame() == 0 && panel.listSize() == 4 && subtitle().equals("בפעולה Rem()") && has("x=8") && has("d=2"),
                "Rem ← IsEven ← SumEven ← Main: רשימה של 4, מוצגת Rem (שרצה) עם x=8, d=2");

        panel.choose(2);
        check(panel.frame() == 2 && subtitle().equals("בפעולה SumEven() · מחכה") && has("sum=0") && has("v=8")
                && panel.noteText().contains("SumEven() מחכה בשורה 30 לתשובה מ-IsEven()"),
                "בחירת SumEven ברשימה: המשתנים שלה (sum=0, v=8), \"מחכה בשורה 30 לתשובה מ-IsEven()\"");

        show(rem);   // אותו צעד מצויר מחדש (זום / החלפת תצוגה) — הבחירה נשמרת
        check(panel.frame() == 2 && has("sum=0"), "אותו צעד מצויר מחדש: הבחירה נשמרת — עדיין SumEven");

        panel.choose(3);
        check(panel.frame() == 3 && subtitle().equals("בפעולה Main() · מחכה") && has("s=Stack [3]"),
                "בחירת Main: s=Stack [3] — המחסנית ברגע הזה");

        show(Recorder.step(rem.index + 1));   // הצעד הבא (Rem מחזירה): Main עדיין מחכה — אבל חוזרים לפעולה שרצה
        check(panel.frame() == 0 && panel.listSize() == 4 && subtitle().equals("בפעולה Rem() · מסתיימת"),
                "הצעד הבא: חוזרים לפעולה שרצה (Rem), הרשימה עדיין של 4");

        Step back = find(rem.index, "SumEven", 2, null);
        show(back);
        check(panel.frame() == 0 && panel.listSize() == 2 && subtitle().equals("בפעולה SumEven()") && has("v=8"),
                "חזרה ל-SumEven: הכותרת מתחלפת, והרשימה — 2 פעולות (SumEven ← Main)");
        panel.choose(1);
        check(panel.frame() == 1 && has("s=Stack [3]") && !has("v=8"), "בחירת Main אחרי החזרה: רק המשתנים של Main");

        Step ret = null;
        for (int i = rem.index; i < Recorder.stepCount() && ret == null; i++)
            if (Recorder.step(i).kind == Kind.RETURN && Recorder.step(i).calls.get(0).method.equals("Rem")) ret = Recorder.step(i);
        show(ret);
        check(subtitle().equals("בפעולה Rem() · מסתיימת") && panel.noteText().contains("Rem() מחזירה 0 — חוזרים ל-IsEven() בשורה 11"),
                "צעד היציאה: \"מסתיימת\", ו\"Rem() מחזירה 0 — חוזרים ל-IsEven() בשורה 11\"");

        Step report = find(0, "Report", 3, null);
        show(report);
        check(subtitle().equals("בפעולה Report()") && has("title=max") && has("value=9"), "פעולה void: Report עם title, value");
        panel.choose(1);
        check(has("m=9") && panel.noteText().contains("MaxOf() מחכה בשורה 46 לתשובה מ-Report()"), "בחירת MaxOf: m=9, מחכה בשורה 46");

        Step end = Recorder.step(Recorder.stepCount() - 1);
        show(end);
        check(end.kind == Kind.END && panel.listSize() == 0 && subtitle().startsWith("התוכנית הסתיימה") && has("total=8"),
                "סוף התוכנית: המשתנים של Main (total=8), בלי רשימה");
    }

    static void fib() {
        Step deep = find(0, "Fib", 5, "n=1");   // Fib(1) ← Fib(2) ← Fib(3) ← Fib(4) ← Main
        show(deep);
        check(panel.listSize() == 5 && has("n=1") && subtitle().equals("בפעולה Fib()"), "רקורסיה: 5 פעולות פעילות, מוצגת Fib(n = 1)");
        panel.choose(2);
        check(panel.frame() == 2 && has("n=3") && panel.noteText().contains("Fib(n = 3) מחכה בשורה 9 לתשובה מ-Fib(n = 2)"),
                "בחירת Fib(n = 3): n=3, \"Fib(n = 3) מחכה בשורה 9 לתשובה מ-Fib(n = 2)\"");
        Step zero = find(deep.index, "Fib", 5, "n=0");   // Fib(2) קוראת שוב (Fib(0)) — באותו עומק, קריאה אחרת
        show(zero);
        check(panel.frame() == 0 && has("n=0") && panel.listSize() == 5, "הצעד שבו Fib(2) קוראת ל-Fib(0): חוזרים לפעולה שרצה (n=0)");
        panel.choose(3);
        check(panel.frame() == 3 && has("n=4") && panel.noteText().contains("Fib(n = 4) מחכה בשורה 9 לתשובה מ-Fib(n = 3)"),
                "בחירת Fib(n = 4) — כל קריאה ברקורסיה עם המשתנים שלה");
        Step three = find(zero.index, "Fib", 3, "n=3");   // Fib(3) רצה שוב
        show(three);
        check(panel.frame() == 0 && has("n=3") && panel.listSize() == 3, "Fib(3) רצה שוב: הרשימה מתקצרת ל-3");
        Step last = null;   // הקריאה החיצונית מחזירה — לבד במחסנית, אבל עדיין "Fib(n = 4)" (כבר קראה לעצמה)
        for (int i = 0; i < Recorder.stepCount(); i++)
            if (Recorder.step(i).kind == Kind.RETURN && Recorder.step(i).calls.size() == 2) last = Recorder.step(i);
        show(last);
        check(panel.listSize() == 2 && panel.noteText().contains("Fib(n = 4) מחזירה 3 — חוזרים ל-Main() בשורה"),
                "הקריאה החיצונית מחזירה: \"Fib(n = 4) מחזירה 3 — חוזרים ל-Main()\"");
    }
}
