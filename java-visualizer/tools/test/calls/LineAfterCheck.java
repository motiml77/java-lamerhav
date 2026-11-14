package unit4.collectionsLib;

import java.util.List;

import javax.swing.JLabel;
import javax.swing.JTextPane;

import unit4.collectionsLib.Model.Kind;
import unit4.collectionsLib.Model.Step;

/**
 * בדיקה בלי חלון ל"כל שורה — השורה המסומנת מראה את מה שהיא כבר עשתה" (LineAfter) ולמרקר במעקב המשתנים,
 * על קובץ הצעדים של Calls.cs (C#):
 * - הפעולה הראשונה של שורה מתקפלת לתוך צעד השורה (s.Pop() בשורה 29), ושורה בלי פעולות נשארת כמו שהיא
 * - המשתנים אחרי השורה: v = 8 כבר בשורה 29, sum = 8 בשורה 31
 * - שתי פעולות באותה שורה (s.Push(tmp.Pop())): הראשונה מתקפלת, השנייה צעד משלה, והמשתנים "אחרי" רק בה
 * - שורה שקוראת לפעולה של התלמיד (int total = SumEven(s)) — בלי "אחרי"
 * - המרקר: sum (היה 0), v (חדש)
 *
 *   LineAfterCheck <Calls.trace>
 */
public class LineAfterCheck {
    static int fails = 0;

    static void check(boolean ok, String what, String why) {
        System.out.println((ok ? "  ok  " : "  FAIL ") + what + (ok || why == null ? "" : "   — " + why));
        if (!ok) fails++;
    }

    /** צעד ה-LINE הראשון בשורה line של הפעולה method (מ-from והלאה) */
    static int line(int from, String method, int line) {
        for (int i = from; i < Recorder.stepCount(); i++) {
            Step s = Recorder.step(i);
            if (s.kind == Kind.LINE && s.where != null && s.where.method.equals(method) && s.where.line == line) return i;
        }
        throw new IllegalStateException("לא נמצאה שורה " + line + " ב-" + method);
    }

    static String var(List<String[]> vs, String name) {
        if (vs != null) for (String[] v : vs) if (v[0].equals(name)) return v[1];
        return null;
    }

    public static void main(String[] a) throws Exception {
        Theme.install();
        TraceViewer.load(a[0]);
        int n = Recorder.stepCount();

        // שורה 29: int v = s.Pop(); — הפעולה מתקפלת, המשתנים אחרי השורה כוללים את v
        int l29 = line(0, "SumEven", 29);
        int d29 = LineAfter.data(l29, n);
        check(d29 == l29 + 1 && Recorder.step(d29).text.contains("Pop"), "שורה 29: s.Pop() בתוך צעד השורה", "data=" + d29);
        check(LineAfter.folded(l29 + 1), "שורה 29: הצעד של s.Pop() לא מוצג לבד", null);
        int a29 = LineAfter.after(l29, n);
        check(a29 >= 0 && "8".equals(var(Recorder.step(a29).vars, "v")), "שורה 29: אחרי השורה v = 8", "after=" + a29);
        check(var(Recorder.step(l29).vars, "v") == null, "שורה 29: לפני השורה עוד אין v (ההקלטה לא השתנתה)", null);

        // שורה 31: sum += v; — בלי פעולות על מבנים, המשתנים אחרי: sum = 8
        int l31 = line(0, "SumEven", 31);
        check(LineAfter.data(l31, n) == l31, "שורה 31: אין פעולה לקפל", null);
        int a31 = LineAfter.after(l31, n);
        check(a31 >= 0 && "8".equals(var(Recorder.step(a31).vars, "sum")), "שורה 31: אחרי השורה sum = 8", "after=" + a31);

        // שורה 36: s.Push(tmp.Pop()); — Pop מתקפל, Push צעד משלו, "אחרי" רק בו
        int l36 = line(0, "SumEven", 36);
        int d36 = LineAfter.data(l36, n);
        check(Recorder.step(d36).text.contains("Pop"), "שורה 36: tmp.Pop() בתוך צעד השורה", Recorder.step(d36).text);
        check(LineAfter.after(l36, n) < 0, "שורה 36: אחרי Pop עוד לא נגמרה השורה — בלי \"אחרי\"", null);
        Step push = Recorder.step(d36 + 1);
        check(push.text.contains("Push") && !LineAfter.folded(d36 + 1), "שורה 36: s.Push(...) צעד משלו (גם הוא מונפש)", push.text);
        check(LineAfter.after(d36 + 1, n) >= 0, "שורה 36: אחרי Push — המשתנים שאחרי השורה", null);

        // שורה 54: int total = SumEven(s); — נכנסים לפעולה של התלמיד: בלי "אחרי"
        int l54 = line(0, "Main", 54);
        check(LineAfter.after(l54, n) < 0, "שורה 54: קריאה ל-SumEven — בלי \"אחרי\" (הערך מגיע כשחוזרים)", null);

        // חזרה מ-IsEven ואז הצעד הבא: RETURN לא מתקפל ולא "מתקפלים" לתוכו
        for (int i = 0; i < n; i++) if (Recorder.step(i).kind == Kind.RETURN && LineAfter.folded(i)) { check(false, "צעד חזרה קופל", "#" + i); break; }

        // המרקר במעקב המשתנים
        JLabel sub = new JLabel();
        VarsPanel p = new VarsPanel(sub, new JTextPane());
        p.set(Recorder.step(l31), null, null, new VarsPanel.After(Recorder.step(a31).vars, Recorder.step(l31).vars));
        check(p.rows().contains("sum=8"), "מעקב המשתנים בשורה 31: sum=8 (אחרי השורה)", p.rows().toString());
        check(p.marked().contains("sum (היה 0)"), "המרקר בשורה 31: sum (היה 0)", p.marked().toString());
        check(p.marked().size() == 1, "המרקר בשורה 31: רק sum", p.marked().toString());
        p.set(Recorder.step(l29), null, null, new VarsPanel.After(Recorder.step(a29).vars, Recorder.step(l29).vars));
        check(p.marked().contains("v (חדש)"), "המרקר בשורה 29: v (חדש)", p.marked().toString());
        p.set(Recorder.step(l31), null, null);
        check(p.marked().isEmpty() && p.rows().contains("sum=0"), "בלי \"אחרי\" (רק פעולות / כבוי) — כמו קודם: sum=0 ובלי מרקר", p.rows() + " " + p.marked());

        System.out.println(fails == 0 ? "MARK-END" : fails + " נכשלו");
        System.exit(fails == 0 ? 0 : 1);
    }
}
