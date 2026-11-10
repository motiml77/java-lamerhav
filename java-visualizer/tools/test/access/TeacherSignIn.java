package il.lamerhav.dsviz;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * הלקוח האמיתי של DSViz (Account — אותו קוד שרץ ב-Eclipse, ב-IntelliJ וב-Visual Studio) לבדיקת דפדפן:
 *   teacher|student — במקום לפתוח דפדפן מדפיס "URL <כתובת>", הבדיקה (Playwright) פותחת אותה ועוברת את ההתחברות, ומדפיס "RESULT <status>"
 *   check           — תהליך חדש באותה תיקייה (כמו הפעלה מחדש): "ACCESS <kind|none>" בלי אינטרנט, ואז "REFRESH <status>" ושוב ACCESS
 *   offline         — כמו check, אבל השרת לא עונה: "ACCESS <kind|none>" ו-"REFRESH offline"
 *   signout         — התנתקות: "SIGNOUT <status>" ו-"ACCESS <kind|none>"
 *   java -cp <classes> il.lamerhav.dsviz.TeacherSignIn <שרת> <תיקייה זמנית> <מצב>
 * מזהה המחשב נשמר בתיקייה — אותו "מחשב" בכל ההפעלות.
 */
public class TeacherSignIn {
    public static void main(String[] a) throws Exception {
        String mode = a[2];
        System.setProperty("dsviz.worker", mode.equals("offline") ? "http://127.0.0.1:9" : a[0]);
        System.setProperty("dsviz.machineDir", new File(a[1], "machine").getPath());
        System.setProperty("dsviz.home", new File(a[1], "user").getPath());
        File mid = new File(a[1], "mid");
        if (!mid.isFile()) Files.write(mid.toPath(), ("realapp" + Long.toHexString(System.nanoTime()) + "0000000000000000").getBytes(StandardCharsets.UTF_8));
        System.setProperty("dsviz.machine", new String(Files.readAllBytes(mid.toPath()), StandardCharsets.UTF_8).trim());
        // מפתח האישור של שרת הבדיקה (בייצור הוא בתוך התוכנה)
        java.io.InputStream in = new java.net.URL(a[0] + "/v1/access/key").openStream();
        java.io.ByteArrayOutputStream buf = new java.io.ByteArrayOutputStream();
        for (int n; (n = in.read()) >= 0; ) buf.write(n);
        System.setProperty("dsviz.passKey", Json.str(Json.obj(Json.parse(new String(buf.toByteArray(), "UTF-8"))).get("spki")));
        Account.resetForTest();
        if (mode.equals("check") || mode.equals("offline")) {
            access();
            System.out.println("REFRESH " + Account.refresh().status);
            access();
        } else if (mode.equals("signout")) {
            System.out.println("SIGNOUT " + Account.signOut().status);
            access();
        } else {
            Account.browser = u -> { System.out.println("URL " + u); System.out.flush(); };
            Account.Result r = Account.signIn(mode, null, new Account.Cancel(), 120000);
            System.out.println("RESULT " + r.status);
        }
        System.out.flush();
        System.exit(0);
    }

    static void access() {
        Account.Access c = Account.current();
        System.out.println("ACCESS " + (c == null ? "none" : c.kind));
        System.out.flush();
    }
}
