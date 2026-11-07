package il.lamerhav.dsviz;

import java.io.File;

/**
 * הלקוח האמיתי של DSViz (Account.signIn — אותו קוד שרץ ב-Eclipse וב-IntelliJ) לבדיקת דפדפן:
 * במקום לפתוח דפדפן הוא מדפיס "URL <כתובת>", הבדיקה (Playwright) פותחת אותה ועוברת את ההתחברות, והוא מדפיס "RESULT <status>".
 *   java -cp <classes> il.lamerhav.dsviz.TeacherSignIn <שרת> <תיקייה זמנית> <teacher|student>
 */
public class TeacherSignIn {
    public static void main(String[] a) throws Exception {
        System.setProperty("dsviz.worker", a[0]);
        System.setProperty("dsviz.machineDir", new File(a[1], "machine").getPath());
        System.setProperty("dsviz.home", new File(a[1], "user").getPath());
        System.setProperty("dsviz.machine", "realapp" + Long.toHexString(System.nanoTime()) + "0000000000000000");
        // מפתח האישור של שרת הבדיקה (בייצור הוא בתוך התוכנה)
        java.io.InputStream in = new java.net.URL(a[0] + "/v1/access/key").openStream();
        java.io.ByteArrayOutputStream buf = new java.io.ByteArrayOutputStream();
        for (int n; (n = in.read()) >= 0; ) buf.write(n);
        System.setProperty("dsviz.passKey", Json.str(Json.obj(Json.parse(new String(buf.toByteArray(), "UTF-8"))).get("spki")));
        Account.resetForTest();
        Account.browser = u -> { System.out.println("URL " + u); System.out.flush(); };
        Account.Result r = Account.signIn(a[2], null, new Account.Cancel(), 120000);
        System.out.println("RESULT " + r.status);
        System.out.flush();
        System.exit(0);
    }
}
