package il.lamerhav.dsviz;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * כניסת מורה עם Google — כל הדרך חוץ מהמסך של Google עצמו, מול השרת האמיתי, ב-Java שמריצים בו (ב-Windows: ה-Java של ההתקנה,
 * שממנו רצים הכלים של Visual Studio — שם הכניסה נכשלה מיד):
 *   1. Account.signIn פותח שרת זעיר על 127.0.0.1 ו"פותח דפדפן" (כאן: בקשה) לדף הכניסה של השרת — הדף נטען (200)
 *   2. "הדפדפן חוזר" ל-127.0.0.1/done עם state נכון וקוד (לא אמיתי) — השרת הזעיר עונה בהפניה לדף התוצאה
 *   3. הלקוח מממש את הקוד מול השרת (/v1/auth/redeem) — ומקבל תשובה של השרת (הקוד לא קיים), לא "לא הצלחתי לפתוח חיבור"
 *   java -cp <classes>;dsviz.jar il.lamerhav.dsviz.SignInFlow [real]
 * real — גם פותחים את הדפדפן האמיתי של המחשב (כמו אצל המורה; הבדיקה ב-Windows בודקת שהדפדפן נפתח)
 */
public class SignInFlow {
    public static void main(String[] a) throws Exception {
        final String[] seen = new String[3];
        final Thread[] browserThread = { null };
        final boolean real = a.length > 0 && a[0].equals("real");
        Account.browser = url -> (browserThread[0] = new Thread(() -> {
            try {
                if (real) Account.openBrowser(url);
                HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
                c.setConnectTimeout(15000);
                c.setReadTimeout(20000);
                int code = c.getResponseCode();
                String html = read(code >= 400 ? c.getErrorStream() : c.getInputStream());
                seen[0] = code + " (" + html.length() + " תווים)";
                String port = param(url, "port"), state = param(url, "state");
                StringBuilder fake = new StringBuilder();
                for (int i = 0; i < 32; i++) fake.append('x');
                HttpURLConnection b = (HttpURLConnection) new URL("http://127.0.0.1:" + port + "/done?state=" + state + "&code=" + fake).openConnection();
                b.setInstanceFollowRedirects(false);
                b.setConnectTimeout(10000);
                b.setReadTimeout(40000);
                seen[1] = b.getResponseCode() + " " + b.getHeaderField("Location");
            } catch (Exception e) {
                seen[2] = String.valueOf(e);
            }
        }, "signin-browser")).start();
        long t0 = System.currentTimeMillis();
        Account.Result r = Account.signIn("teacher", null, new Account.Cancel(), 90000);
        if (browserThread[0] != null) browserThread[0].join(30000);   // "הדפדפן" מסיים לקרוא את ההפניה
        System.out.println("java: " + System.getProperty("java.home"));
        System.out.println("server: " + Account.serverUrl());
        System.out.println("page: " + seen[0]);
        System.out.println("back: " + seen[1]);
        System.out.println("result: " + r.status + " — " + r.message + " (" + (System.currentTimeMillis() - t0) + "ms)");
        if (seen[2] != null) System.out.println("error: " + seen[2]);
        boolean ok = seen[0] != null && seen[0].startsWith("200")
                && seen[1] != null && seen[1].startsWith("302") && seen[1].contains("/welcome?r=")
                && !r.status.equals("cancelled") && !r.message.contains("לא הצלחתי לפתוח");
        System.out.println(ok ? "SIGNIN-FLOW-OK" : "SIGNIN-FLOW-FAIL");
        System.exit(ok ? 0 : 1);
    }

    static String param(String url, String name) {
        for (String kv : url.substring(url.indexOf('?') + 1).split("&")) if (kv.startsWith(name + "=")) return kv.substring(name.length() + 1);
        return "";
    }

    static String read(InputStream in) throws Exception {
        if (in == null) return "";
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        byte[] b = new byte[8192];
        for (int n; (n = in.read(b)) > 0; ) o.write(b, 0, n);
        return new String(o.toByteArray(), StandardCharsets.UTF_8);
    }
}
