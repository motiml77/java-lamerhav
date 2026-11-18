package il.lamerhav.dsviz;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * בדיקת ההרצה של "הגשה למורה" (RunCheck): קוד נכון → הפלט תואם; פלט שונה; שגיאת קומפילציה עם שורה; לולאה שלא נגמרת — נעצרת
 * אחרי 10 שניות; שגיאה בזמן ריצה; תוכנית שמחכה לקלט — לא מורצת; חבילה (package) כמו בתרגיל מהמאגר; פלט ארוך נחתך.
 *   java ... RunCheckTest <dsviz.jar> [dsviz-cs.dll]   (עם ecj ב-classpath — הקומפיילר של Eclipse; בלי — javac)
 */
public class RunCheckTest {
    static int pass, fail;

    static void check(String name, boolean ok, Object got) {
        if (ok) pass++; else fail++;
        System.out.println((ok ? "  ok   " : "  FAIL ") + name + (ok ? "" : "  ← " + got));
    }

    static Map<String, String> files(String... kv) {
        Map<String, String> m = new LinkedHashMap<String, String>();
        for (int i = 0; i + 1 < kv.length; i += 2) m.put(kv[i], kv[i + 1]);
        return m;
    }

    static String show(RunCheck.Result r) {
        return r.toMap().toString();
    }

    public static void main(String[] a) throws Exception {
        File lib = new File(a[0]);
        String good = "package ex01_sum;\npublic class Main {\n    public static int sum(int[] x) { int s = 0; for (int v : x) s += v; return s; }\n"
                + "    public static void main(String[] args) {\n        System.out.println(\"sum: \" + sum(new int[] { 1, 2, 3 }));\n        System.out.println(Helper.twice(4));   \n    }\n}\n";
        String helper = "package ex01_sum;\npublic class Helper {\n    public static int twice(int n) { return 2 * n; }\n}\n";

        RunCheck.Result r = RunCheck.java(files("Main.java", good, "Helper.java", helper), "Main", lib, null, "sum: 6\n8\n");
        check("Java: שני קבצים בחבילה — מתקמפל, רץ, והפלט תואם (רווחים בסוף שורה לא נחשבים)", r.ran && Boolean.TRUE.equals(r.compiles) && Boolean.TRUE.equals(r.outputMatch)
                && r.output.equals("sum: 6\n8") && "good".equals(r.tone()) && r.summary().contains("תואם"), show(r));

        r = RunCheck.java(files("Main.java", good, "Helper.java", helper), "Main", lib, null, "sum: 7\n8");
        check("Java: פלט שונה מהרצוי — רץ, לא תואם; נשלחים הפלט והפלט הרצוי", r.ran && Boolean.TRUE.equals(r.compiles) && Boolean.FALSE.equals(r.outputMatch)
                && r.expected.equals("sum: 7\n8") && r.output.equals("sum: 6\n8") && "warn".equals(r.tone()), show(r));

        r = RunCheck.java(files("Main.java", good, "Helper.java", helper), "", lib, null, "");
        check("Java: בלי פלט רצוי (תרגיל של התלמיד), ובלי שם המחלקה — מוצאים את main; רץ, בלי השוואה", r.ran && Boolean.TRUE.equals(r.compiles) && r.outputMatch == null
                && r.output.startsWith("sum: 6") && r.summary().contains("מתקמפל ורץ"), show(r));

        r = RunCheck.java(files("Main.java", good.replace("return s;", "return s"), "Helper.java", helper), "Main", lib, null, "sum: 6\n8");
        check("Java: שגיאת קומפילציה — לא רץ; השגיאה עם שם הקובץ ומספר השורה", r.ran && Boolean.FALSE.equals(r.compiles) && r.error.contains("Main.java, שורה 3")
                && r.output.isEmpty() && "bad".equals(r.tone()) && r.outputMatch == null, show(r));

        long t0 = System.currentTimeMillis();
        r = RunCheck.java(files("Loop.java", "public class Loop {\n    public static void main(String[] args) {\n        System.out.println(\"start\");\n        int i = 0;\n        while (i < 10) { }\n    }\n}\n"),
                "Loop", lib, null, "start\ndone");
        long ms = System.currentTimeMillis() - t0;
        check("Java: לולאה שלא נגמרת — נעצרת אחרי 10 שניות (" + ms + "ms), עם הפלט עד אז", r.ran && r.timedOut && Boolean.FALSE.equals(r.outputMatch) && r.output.equals("start")
                && ms >= 9000 && ms < 25000 && r.summary().contains("10 שניות"), show(r));

        r = RunCheck.java(files("Crash.java", "public class Crash {\n    public static void main(String[] args) {\n        System.out.println(\"before\");\n        int[] x = new int[2];\n        x[5] = 1;\n    }\n}\n"),
                "Crash", lib, null, "before\nafter");
        check("Java: שגיאה בזמן ריצה — הפלט עד השגיאה, והשגיאה עצמה", r.ran && r.crashed && Boolean.TRUE.equals(r.compiles) && Boolean.FALSE.equals(r.outputMatch)
                && r.output.equals("before") && r.error.contains("ArrayIndexOutOfBounds"), show(r));

        r = RunCheck.java(files("Ask.java", "import java.util.Scanner;\npublic class Ask {\n    public static void main(String[] args) {\n        Scanner in = new Scanner(System.in);\n        System.out.println(in.nextInt() * 2);\n    }\n}\n"),
                "Ask", lib, null, "");
        check("Java: תוכנית שמחכה לקלט — מתקמפלת, לא מורצת, מסומנת needsInput", !r.ran && r.needsInput && Boolean.TRUE.equals(r.compiles) && r.summary().contains("קלט"), show(r));
        check("Java: Scanner בתוך הערה / מחרוזת לא נחשב קלט", !RunCheck.readsInput(Arrays.asList("// new Scanner(System.in)\nString s = \"new Scanner(System.in)\"; /* System.in.read() */"), false)
                && RunCheck.readsInput(Arrays.asList("Scanner in = new Scanner( System.in );"), false), "");

        StringBuilder big = new StringBuilder("public class Big {\n    public static void main(String[] args) {\n        for (int i = 0; i < 3000; i++) System.out.println(\"line \" + i);\n    }\n}\n");
        r = RunCheck.java(files("Big.java", big.toString()), "Big", lib, null, "");
        check("Java: פלט ארוך נחתך ל-2,000 תווים", r.ran && r.output.length() <= RunCheck.MAX_OUTPUT + 2 && r.output.startsWith("line 0\nline 1"), r.output.length());

        // נעשה שימוש בספרייה (Stack של unit4) — כמו בתרגיל מהמאגר
        r = RunCheck.java(files("Main.java", "package ex02_stack;\nimport unit4.collectionsLib.Stack;\npublic class Main {\n    public static void main(String[] args) {\n"
                + "        Stack<Integer> s = new Stack<Integer>();\n        s.push(3);\n        s.push(9);\n        System.out.println(s.pop() + s.top());\n    }\n}\n"), "Main", lib, null, "12");
        check("Java: תרגיל שמשתמש בספרייה (unit4) — מתקמפל מול dsviz.jar ורץ", r.ran && Boolean.TRUE.equals(r.outputMatch), show(r));

        if (a.length > 1) {
            List<String> cs = new ArrayList<String>(Arrays.asList("dotnet", new File(a[1]).getAbsolutePath()));
            RunCheck.Result none = RunCheck.cs(files("Program.cs", "class P { static void Main() { System.Console.WriteLine(1); } }"), Arrays.asList("dotnet", "no-such-runner.dll"), "1");
            check("C#: הכלי עצמו לא עולה (קובץ חסר / אין ‎.NET‎) — 'לא נבדק', ולא 'התוכנית נפלה'", !none.ran && !none.crashed && none.error.isEmpty() && "none".equals(none.tone()), show(none));
            none = RunCheck.cs(files("Program.cs", "class P { static void Main() { } }"), Arrays.asList("no-such-command-xyz"), "");
            check("C#: אין dsviz-cs במחשב — 'לא נבדק'", !none.ran, show(none));
            String prog = "using System;\nnamespace Ex01\n{\n    class Program\n    {\n        static int Sum(int[] x) { int s = 0; foreach (int v in x) s += v; return s; }\n"
                    + "        static void Main(string[] args)\n        {\n            Console.WriteLine(\"sum: \" + Sum(new int[] { 1, 2, 3 }));\n        }\n    }\n}\n";
            r = RunCheck.cs(files("Program.cs", prog), cs, "sum: 6");
            check("C#: מתקמפל, רץ, והפלט תואם", r.ran && Boolean.TRUE.equals(r.compiles) && Boolean.TRUE.equals(r.outputMatch) && r.output.equals("sum: 6"), show(r));
            r = RunCheck.cs(files("Program.cs", prog), cs, "sum: 7");
            check("C#: פלט שונה", r.ran && Boolean.FALSE.equals(r.outputMatch) && r.output.equals("sum: 6") && r.expected.equals("sum: 7"), show(r));
            r = RunCheck.cs(files("Program.cs", prog.replace("return s;", "return s")), cs, "sum: 6");
            check("C#: שגיאת קומפילציה — הקובץ, השורה וההודעה (בלי הנתיב של התיקייה הזמנית); השגיאה הגולמית עם הקוד שלה", r.ran && Boolean.FALSE.equals(r.compiles)
                    && r.error.startsWith("Program.cs, שורה 6:") && !r.error.contains("dsviz-submit") && r.errors.size() == 1 && r.errors.get(0).matches("Program\\.cs:6: .*\\[CS1002\\]"), show(r) + " " + r.errors);
            RunCheck.explainer = (message, code) -> "CS1002".equals(code) ? "חסר ; בסוף הפקודה" : "missing return statement".equals(message) ? "חסרה הוראת return" : "";
            r = RunCheck.cs(files("Program.cs", prog.replace("return s;", "return s")), cs, "sum: 6");
            check("C#: עם הסבר בעברית (כמו בכלי של Visual Studio) — 'חסר ; … (; expected)'", r.error.equals("Program.cs, שורה 6: חסר ; בסוף הפקודה (; expected)"), r.error);
            r = RunCheck.java(files("M.java", "public class M {\n    static int f() { }\n    public static void main(String[] a) { }\n}\n"), "M", lib, null, "");
            check("Java: עם הסבר בעברית — 'חסרה הוראת return (missing return statement)' / ההודעה של ecj", r.error.startsWith("M.java, שורה 2:") && Boolean.FALSE.equals(r.compiles), r.error);
            RunCheck.explainer = (message, code) -> "";
            r = RunCheck.cs(files("Program.cs", prog.replace("Console.WriteLine(\"sum: \" + Sum(new int[] { 1, 2, 3 }));", "Console.WriteLine(int.Parse(Console.ReadLine()) * 2);")), cs, "");
            check("C#: מחכה לקלט — לא מורץ, מסומן needsInput", !r.ran && r.needsInput && r.error.isEmpty(), show(r));
            t0 = System.currentTimeMillis();
            r = RunCheck.cs(files("Program.cs", prog.replace("Console.WriteLine(\"sum: \" + Sum(new int[] { 1, 2, 3 }));", "Console.WriteLine(\"start\"); int i = 0; while (i < 10) { }")), cs, "start\ndone");
            ms = System.currentTimeMillis() - t0;
            check("C#: לולאה שלא נגמרת — נעצרת (" + ms + "ms), עם מספר השורה של הלולאה", r.ran && r.timedOut && !r.crashed && Boolean.FALSE.equals(r.outputMatch) && ms < 45000
                    && r.error.contains("לא נגמרת") && r.output.equals("start") && r.summary().startsWith("ההרצה נעצרה"), show(r));
            r = RunCheck.cs(files("Program.cs", prog.replace("Console.WriteLine(\"sum: \" + Sum(new int[] { 1, 2, 3 }));", "Console.WriteLine(\"before\"); int[] x = new int[2]; x[5] = 1;")), cs, "before\nafter");
            check("C#: שגיאה בזמן ריצה — הפלט עד השגיאה, והשגיאה", r.ran && r.crashed && !r.timedOut && Boolean.FALSE.equals(r.outputMatch) && r.output.equals("before") && !r.error.isEmpty(), show(r));
        }
        System.out.println("בדיקת ההרצה של הגשה: " + pass + " עברו, " + fail + " נכשלו");
        System.exit(fail == 0 ? 0 : 1);
    }
}
