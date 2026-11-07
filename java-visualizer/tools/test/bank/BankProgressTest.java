package il.lamerhav.dsviz;

import java.io.File;
import java.nio.file.Files;

/** ה-V ("סיימתי") והקבצים שנוצרו מכל שאלה — נשמרים, נקראים אחרי "הפעלה מחדש", ולא נופלים על קובץ פגום */
public final class BankProgressTest {
    private static int fails;

    public static void main(String[] a) throws Exception {
        File dir = Files.createTempDirectory("bank-progress").toFile();
        File f = new File(dir, "sub/bank-progress.json");
        BankProgress p = new BankProgress(f);
        check(!p.done("q1"), "בהתחלה — בלי V");
        int[] fired = { 0 };
        p.listen(() -> fired[0]++);
        p.setDone("q1", true);
        check(p.done("q1") && !p.done("q2"), "V נשמר רק לשאלה שסומנה");
        check(fired[0] == 1, "מי שמקשיב (החלונות) מקבל הודעה על השינוי");
        BankProgress.Created c = new BankProgress.Created();
        c.project = "DataStructures";
        c.folder = "/DataStructures/src/ex01_stacks";
        c.files.add("/DataStructures/src/ex01_stacks/StackTargil.java");
        p.created("q1", c);
        BankProgress again = new BankProgress(f);   // כמו אחרי שסוגרים ופותחים את Eclipse
        check(again.done("q1"), "ה-V נשאר אחרי הפעלה מחדש");
        check(again.createdOf("q1").size() == 1 && again.createdOf("q1").get(0).files.get(0).endsWith("StackTargil.java"), "הקבצים שנוצרו נשמרו");
        check("q1".equals(again.questionOf("/DataStructures/src/ex01_stacks/StackTargil.java")), "חלון הצד מוצא את השאלה של הקובץ");
        check("q1".equals(again.questionOf("/DataStructures/src/ex01_stacks/Helper.java")), "גם קובץ שהתלמיד הוסיף באותה חבילה");
        check(again.questionOf("/DataStructures/src/ex01_stacks2/X.java") == null, "חבילה אחרת עם שם דומה — לא שייכת");
        again.setDone("q1", false);
        check(!new BankProgress(f).done("q1"), "אפשר להוריד את ה-V");
        Files.write(f.toPath(), "{ broken".getBytes("UTF-8"));
        BankProgress broken = new BankProgress(f);
        check(!broken.done("q1"), "קובץ פגום — לא נופלים, מתחילים מחדש");
        broken.setDone("q3", true);
        check(new BankProgress(f).done("q3"), "ואחרי קובץ פגום אפשר לשמור שוב");
        System.out.println(fails == 0 ? "התקדמות במאגר (V): תקין" : "התקדמות במאגר: " + fails + " בעיות");
        System.exit(fails == 0 ? 0 : 1);
    }

    private static void check(boolean ok, String what) {
        if (!ok) fails++;
        System.out.println((ok ? "  ok   " : "  FAIL ") + what);
    }
}
