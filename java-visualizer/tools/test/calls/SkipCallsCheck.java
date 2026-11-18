package unit4.collectionsLib;

import java.util.ArrayList;
import java.util.List;

import unit4.collectionsLib.Model.Kind;
import unit4.collectionsLib.Model.LinearView;
import unit4.collectionsLib.Model.Step;
import unit4.collectionsLib.Model.View;

/**
 * בדיקה בלי חלון למצב "לדלג על פונקציות" (SkipCalls) על קובץ הצעדים של Calls.cs (C#):
 * - מוצגות רק השורות של Main (51–58) והסוף — אף שורה מתוך SumEven / MaxOf / Report
 * - int total = SumEven(s) (שורה 54): צעד אחד שמראה את המצב אחרי הפעולה — total = 8, והמחסנית s שוב עם 2 איברים
 * - Console.WriteLine(total + " " + MaxOf(q)) (שורה 58): ההדפסה שאחרי החזרה מתקפלת לתוך צעד השורה, והפלט כבר בו
 * - שורה בלי קריאה לפעולה (s.Push(3)) — בדיוק כמו "שורה שורה" (LineAfter)
 * - פעולה שעוד רצה (עוד לא הגיעו הצעדים שאחרי החזרה): השורה מוצגת כמו שהיא, בלי "אחרי"
 *
 *   SkipCallsCheck <Calls.trace>
 */
public class SkipCallsCheck {
    static int fails = 0;

    static void check(boolean ok, String what, String why) {
        System.out.println((ok ? "  ok  " : "  FAIL ") + what + (ok || why == null ? "" : "   — " + why));
        if (!ok) fails++;
    }

    static int line(String method, int line) {
        for (int i = 0; i < Recorder.stepCount(); i++) {
            Step s = Recorder.step(i);
            if (s.kind == Kind.LINE && s.where != null && s.where.method.equals(method) && s.where.line == line) return i;
        }
        throw new IllegalStateException("לא נמצאה שורה " + line + " ב-" + method);
    }

    static String var(List<String[]> vs, String name) {
        if (vs != null) for (String[] v : vs) if (v[0].equals(name)) return v[1];
        return null;
    }

    static int stackSize(Step s) {
        for (View v : s.views) if (v instanceof LinearView && ((LinearView) v).stack) return ((LinearView) v).items.size();
        return -1;
    }

    public static void main(String[] a) throws Exception {
        Theme.install();
        TraceViewer.load(a[0]);
        int n = Recorder.stepCount();

        // מה מוצג: רק Main והסוף
        List<Integer> lines = new ArrayList<Integer>();
        boolean onlyMain = true, end = false;
        for (int i = 0; i < n; i++) {
            Step s = Recorder.step(i);
            if (LineAfter.folded(i) || !SkipCalls.shown(s, n)) continue;
            if (s.kind == Kind.END) { end = true; continue; }
            if (s.calls.size() != 1 || !"Main".equals(s.where.method)) onlyMain = false;
            if (s.kind == Kind.LINE) lines.add(s.where.line);
        }
        check(onlyMain, "מוצגים רק צעדים של Main", null);
        check(lines.toString().equals("[51, 52, 53, 54, 55, 56, 57, 58]"), "השורות של Main, כל אחת פעם אחת: 51–58", "" + lines);
        check(end, "הסוף מוצג", null);

        // שורה 54: int total = SumEven(s);
        int l54 = line("Main", 54);
        int t54 = SkipCalls.target(l54, n);
        Step at = t54 < 0 ? null : Recorder.step(t54);
        check(at != null && at.calls.size() == 1 && at.where.line == 55, "שורה 54: הצעד אחרי SumEven הוא השורה הבאה ב-Main (55)", "target=" + t54);
        int a54 = SkipCalls.after(l54, n);
        check(a54 >= 0 && "8".equals(var(Recorder.step(a54).vars, "total")), "שורה 54: אחרי הפעולה total = 8", "after=" + a54);
        check(var(Recorder.step(l54).vars, "total") == null && LineAfter.after(l54, n) == -1, "שורה 54: ב\"שורה שורה\" — בלי \"אחרי\" (כמו קודם)", null);
        int d54 = SkipCalls.data(l54, n);
        check(stackSize(Recorder.step(d54)) == 2, "שורה 54: המבנים אחרי הפעולה — המחסנית s שוב עם 2 איברים", "data=" + d54 + " size=" + stackSize(Recorder.step(d54)));

        // שורה 58: Console.WriteLine(total + " " + MaxOf(q)); — ההדפסה שאחרי החזרה מתקפלת לתוך צעד השורה
        int l58 = line("Main", 58);
        int t58 = SkipCalls.target(l58, n);
        Step o58 = t58 < 0 ? null : Recorder.step(t58);
        check(o58 != null && o58.kind == Kind.OUTPUT && o58.where.line == 58, "שורה 58: אחרי MaxOf — ההדפסה של השורה", "target=" + t58 + (o58 == null ? "" : " " + o58.kind));
        check(t58 >= 0 && SkipCalls.folded(t58, n) && !SkipCalls.shown(o58, n), "שורה 58: ההדפסה לא מוצגת לבד (בתוך צעד השורה)", null);
        String out = t58 < 0 ? "" : Recorder.outputUpTo(Recorder.step(SkipCalls.data(l58, n)).outLen);
        check(out.contains("max: 9") && out.contains("8 9"), "שורה 58: בפלט כבר \"max: 9\" (מתוך הפעולה) ו-\"8 9\"", out.replace("\n", "|"));

        // שורה בלי קריאה לפעולה — כמו "שורה שורה"
        int l52 = line("Main", 52);
        check(SkipCalls.target(l52, n) == -1 && SkipCalls.data(l52, n) == LineAfter.data(l52, n) && SkipCalls.after(l52, n) == LineAfter.after(l52, n),
                "שורה 52 (s.Push(3)): כמו \"שורה שורה\"", null);

        // פעולה שעוד רצה: רק חלק מהצעדים הגיעו
        int part = l54 + 6;
        check(SkipCalls.target(l54, part) == -1 && SkipCalls.data(l54, part) == LineAfter.data(l54, part),
                "SumEven עוד רצה: שורה 54 מוצגת כמו שהיא (מתעדכנת כשהצעדים מגיעים)", null);
        check(!SkipCalls.shown(Recorder.step(l54 + 1), n), "השורה הראשונה בתוך SumEven לא מוצגת", null);

        System.out.println(fails == 0 ? "MARK-END" : "נכשלו " + fails);
        System.exit(fails == 0 ? 0 : 1);
    }
}
