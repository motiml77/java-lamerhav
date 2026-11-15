package il.lamerhav.dsviz.cs;

/** InsertPoint: איפה נכנס המבנה כשהסמן בשורה — שורה ריקה מוחלפת, אחרת מתחת לשורה באותה הזחה (CRLF, שורה אחרונה, אחרי הסוף) */
public class InsertPointTest {
    static int fails = 0;

    static void check(boolean ok, String what) {
        System.out.println((ok ? "  ok  " : "  FAIL ") + what);
        if (!ok) fails++;
    }

    public static void main(String[] a) {
        String lf = "class A\n{\n    static void Main()\n    {\n        int x = 0;\n\n        x++;\n    }\n}";
        InsertPoint p = InsertPoint.of(lf, 5);   // int x = 0;
        check(!p.blank && p.indent.equals("        ") && lf.substring(0, p.offset).endsWith("int x = 0;"), "שורה עם קוד: מתחתיה, אותה הזחה (8 רווחים), בסוף השורה");
        p = InsertPoint.of(lf, 6);               // שורה ריקה
        check(p.blank && p.indent.equals("        ") && lf.substring(p.offset).startsWith("\n        x++;"), "שורה ריקה: מחליפים אותה, הזחה ברירת מחדל");
        String sp = "{\n    \n}";
        p = InsertPoint.of(sp, 2);
        check(p.blank && p.indent.equals("    ") && p.offset == 2, "שורה עם רווחים בלבד: מחליפים אותה, וההזחה היא הרווחים שבה");
        String crlf = "class A\r\n{\r\n    void M()\r\n    {\r\n        int y = 1;\r\n    }\r\n}\r\n";
        p = InsertPoint.of(crlf, 5);
        check(!p.blank && crlf.substring(0, p.offset).endsWith("int y = 1;") && !crlf.substring(0, p.offset).endsWith("\r"), "CRLF: הסוף לפני \\r");
        String nolast = "a\n    b";
        p = InsertPoint.of(nolast, 2);
        check(!p.blank && p.offset == nolast.length() && p.indent.equals("    "), "שורה אחרונה בלי ירידת שורה");
        p = InsertPoint.of(nolast, 9);
        check(p.offset == nolast.length(), "שורה אחרי סוף הקובץ: בסוף");
        p = InsertPoint.of("", 1);
        check(p.blank && p.offset == 0 && p.indent.equals("        "), "קובץ ריק");
        String tab = "\t\tint z;";
        p = InsertPoint.of(tab, 1);
        check(!p.blank && p.indent.equals("\t\t"), "הזחה בטאבים נשמרת");
        System.out.println(fails == 0 ? "INSERT-POINT-END" : fails + " נכשלו");
        System.exit(fails == 0 ? 0 : 1);
    }
}
