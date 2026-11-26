package unit4.collectionsLib;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import unit4.collectionsLib.RecursionPanel.Call;
import unit4.collectionsLib.RecursionPanel.Entry;

/**
 * ההזחה של השורות בכרטיסי הרקורסיה (Scope + RecursionPanel.indent): שורה שבתוך if / else / לולאה מוזחת פנימה —
 * לפי מבנה הקוד (סוגריים מסולסלים, וגוף של if בלי סוגריים), לא לפי הרווחים שהתלמיד הקליד; מחרוזות והערות לא נספרות.
 */
public class ScopeCheck {
    static int fails = 0;

    static void check(boolean ok, String what) {
        System.out.println((ok ? "  ok  " : "  FAIL ") + what);
        if (!ok) fails++;
    }

    static final List<String> CODE = Arrays.asList(
            "public class A {",                          // 1
            "    static int rec(int num) {",             // 2
            "        if (num < 10)",                     // 3
            "            return num;",                   // 4   גוף של if בלי { }
            "        if (num % 2 == 0) {  // { בהערה",   // 5
            "            String s = \"}{\";",            // 6   סוגריים בתוך מחרוזת
            "            return rec(num / 10);",         // 7
            "        }",                                 // 8
            "        else",                              // 9
            "            num++;",                        // 10  גוף של else בלי { }
            "        while (num > 100) num--;",          // 11  הגוף באותה שורה
            "        /* { הערת בלוק",                    // 12
            "           } */",                           // 13
            "        return rec(num - 1);",              // 14
            "    }",                                     // 15
            "}");                                        // 16

    static String depths(List<String> l) {
        StringBuilder b = new StringBuilder();
        for (int i = 1; i <= l.size(); i++) b.append(Scope.depth(l, i)).append(i < l.size() ? " " : "");
        return b.toString();
    }

    public static void main(String[] a) {
        String want = "0 1 2 3 2 3 3 2 2 3 2 2 2 2 1 0";
        check(want.equals(depths(CODE)), "עומק הסקופ של כל שורה: " + depths(CODE));
        List<String> flat = new ArrayList<String>();
        for (String s : CODE) flat.add(s.trim());
        check(want.equals(depths(flat)), "אותו קוד בלי שום הזחה (תלמיד שלא מסדר את הקוד) — אותה תשובה");
        List<String> tabs = new ArrayList<String>();
        for (String s : CODE) tabs.add(s.replace("    ", "\t"));
        check(want.equals(depths(tabs)), "עם טאבים — אותה תשובה");
        check(Scope.depth(null, 3) == 0 && Scope.depth(CODE, 0) == 0 && Scope.depth(CODE, 99) == 0, "בלי קובץ / שורה מחוץ לקובץ — 0");

        // הכרטיס של קריאה: ההזחה יחסית לשורה הכי חיצונית שרצה בקריאה (גוף הפעולה)
        Call c = new Call();
        for (int line : new int[] { 3, 4 }) c.trail.add(entry(line));
        check(RecursionPanel.indent(c, c.trail.get(0)) == 0, "if (num < 10) — בלי הזחה (גוף הפעולה)");
        check(RecursionPanel.indent(c, c.trail.get(1)) == RecursionPanel.CODE_INDENT, "return num; שבתוך ה-if — מוזח רמה אחת (" + RecursionPanel.indent(c, c.trail.get(1)) + " פיקסלים)");
        Call d = new Call();
        for (int line : new int[] { 3, 5, 6, 7 }) d.trail.add(entry(line));
        check(RecursionPanel.indent(d, d.trail.get(1)) == 0 && RecursionPanel.indent(d, d.trail.get(2)) == RecursionPanel.CODE_INDENT
                && RecursionPanel.indent(d, d.trail.get(3)) == RecursionPanel.CODE_INDENT, "בלוק עם { }: הכותרת בלי הזחה, השורות שבפנים מוזחות");
        Call only = new Call();
        only.trail.add(entry(7));
        check(RecursionPanel.indent(only, only.trail.get(0)) == 0, "קריאה שרצה בה רק שורה פנימית — בלי הזחה מיותרת");
        System.out.println(fails == 0 ? "MARK-END" : "FAILED " + fails);
        System.exit(fails == 0 ? 0 : 1);
    }

    static Entry entry(int line) {
        Entry e = new Entry();
        e.line = line;
        e.code = CODE.get(line - 1).trim();
        e.depth = Scope.depth(CODE, line);
        return e;
    }
}
