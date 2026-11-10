package il.lamerhav.dsviz;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import com.sun.net.httpserver.HttpServer;

/**
 * מקרי קצה של הכניסה (Account) — מול שרת מדומה (בלי אמולטור ובלי אינטרנט), כמו שקורה אצל משתמשים אמיתיים:
 * שרת שעונה 429 / 500 / 404 / דף HTML של סינון אינטרנט, חזרה מהדפדפן עם state שגוי / בלי קוד / נתיב אחר, דפדפן שנסגר (זמן),
 * ביטול, שתי כניסות במקביל, דפדפן שלא נפתח, קובץ שמור פגום, הרשאות הקובץ, שעון לא נכון, התנתקות בלי אינטרנט, ו-IPv6.
 *   java -cp <classes>:<lib> il.lamerhav.dsviz.SignInEdge <work-dir>
 * (הבדיקה עצמה רצה על JDK מלא — השרת המדומה משתמש ב-jdk.httpserver; הקוד הנבדק לא)
 */
public class SignInEdge {
    static int pass, fail;
    static KeyPair keys;
    static HttpServer mock;
    static int mockPort;
    /** מה השרת המדומה עונה לכל נתיב: {status, body, content-type} */
    static final Map<String, String[]> answers = new ConcurrentHashMap<String, String[]>();
    static final Map<String, AtomicInteger> hits = new ConcurrentHashMap<String, AtomicInteger>();
    static final List<String> logoutTokens = java.util.Collections.synchronizedList(new ArrayList<String>());

    public static void main(String[] a) throws Exception {
        if (a.length > 0 && a[0].equals("ipv6-child")) { ipv6Child(a); return; }
        File work = new File(a[0]);
        keys = KeyPairGenerator.getInstance("RSA").generateKeyPair();
        startMock();
        System.setProperty("dsviz.passKey", Base64.getEncoder().encodeToString(keys.getPublic().getEncoded()));
        System.setProperty("dsviz.worker", "http://127.0.0.1:" + mockPort);
        use(work, "pc1");

        // ---- 1. בדיקה מול השרת עם תקלה רגעית — האישור השמור לא נמחק (לפני התיקון: 429 / 500 / תשובה ריקה נעלו את המשתמש בחוץ)
        String[][] glitches = {
            { "500", "{\"ok\":false,\"code\":\"internal\",\"error\":\"תקלה בשרת\"}", "application/json" },
            { "429", "{\"ok\":false,\"code\":\"rate\",\"error\":\"יותר מדי\"}", "application/json" },
            { "404", "{\"ok\":false,\"code\":\"not_found\",\"error\":\"כתובת לא מוכרת\"}", "application/json" },
            { "503", "<html>Service Unavailable</html>", "text/html" },
            { "200", "", "text/html" },
            { "200", "<html><body>האתר נחסם על ידי מערכת הסינון</body></html>", "text/html" },
            { "200", "{}", "application/json" },
        };
        for (String[] g : glitches) {
            signedIn("teacher", "t1@test.dev", "u.sid12345.secretsecretsecretsecret1");
            answers.put("/v1/access/check", g);
            Account.Result r = Account.refresh();
            check("בדיקה מול השרת: " + g[0] + " " + (g[1].isEmpty() ? "(ריק)" : g[1].substring(0, Math.min(30, g[1].length()))) + " — עדיין מחובר (" + r.status + ")",
                    Account.current() != null && Account.current().isTeacher());
        }
        // ... ותשובה אמיתית של "בוטל" / "הושבת" כן נועלת
        signedIn("teacher", "t1@test.dev", "u.sid12345.secretsecretsecretsecret1");
        answers.put("/v1/access/check", json("{\"ok\":true,\"status\":\"revoked\"}"));
        Account.refresh();
        check("כרטיס שבוטל בשרת — נמחק מהמחשב", Account.current() == null && Account.userState() == null);
        signedIn("teacher", "t1@test.dev", "u.sid12345.secretsecretsecretsecret1");
        answers.put("/v1/access/check", json("{\"ok\":true,\"status\":\"blocked\",\"profile\":{\"role\":\"teacher\",\"email\":\"t1@test.dev\"}}"));
        Account.refresh();
        check("מורה שהושבת — המאגר נסגר, הכרטיס נשאר (הפעלה מחדש פותחת)", Account.current() == null && Account.userState() != null);

        // ---- 2. הרשאות: הכרטיס של המשתמש רק לבעלים (Mac / Linux); העמדה — לכל המשתמשים
        signedIn("teacher", "t1@test.dev", "u.sid12345.secretsecretsecretsecret1");
        String perm = PosixFilePermissions.toString(Files.getPosixFilePermissions(Account.userFile().toPath()));
        check("קובץ הכניסה של המשתמש — rw------- (" + perm + ")", perm.equals("rw-------"));

        // ---- 3. החזרה מהדפדפן: רק GET /done עם ה-state הנכון וקוד; אחרי הכניסה החיבור נסגר
        use(work, "pc2");
        answers.put("/v1/auth/redeem", redeemOk("teacher", "t2@test.dev", 0));
        final String[] seen = new String[8];
        Account.browser = url -> new Thread(() -> {
            try {
                String port = param(url, "port"), state = param(url, "state");
                seen[0] = String.valueOf(get("http://127.0.0.1:" + port + "/done?state=WRONGSTATEWRONGSTATE&code=" + fill('c', 30)));
                seen[1] = String.valueOf(get("http://127.0.0.1:" + port + "/done?state=" + state));
                seen[2] = String.valueOf(get("http://127.0.0.1:" + port + "/favicon.ico"));
                seen[3] = String.valueOf(raw(port, "POST /done?state=" + state + "&code=" + fill('c', 30) + " HTTP/1.1\r\nHost: x\r\nContent-Length: 0\r\n\r\n"));
                seen[4] = String.valueOf(get("http://127.0.0.1:" + port + "/done?code=" + fill('c', 30) + "&state=" + state));
                Thread.sleep(500);
                try { seen[5] = String.valueOf(get("http://127.0.0.1:" + port + "/done?code=" + fill('c', 30) + "&state=" + state)); } catch (Exception e) { seen[5] = "closed"; }
                seen[6] = port;
            } catch (Exception e) {
                seen[7] = String.valueOf(e);
            }
        }).start();
        int redeems = count("/v1/auth/redeem");
        Account.Result r = Account.signIn("teacher", null, null, 15000);
        Thread.sleep(800);
        check("state שגוי — 400 (" + seen[0] + ")", "400".equals(seen[0]));
        check("בלי קוד — 400 (" + seen[1] + ")", "400".equals(seen[1]));
        check("נתיב אחר — 404 (" + seen[2] + ")", "404".equals(seen[2]));
        check("POST — 404 (" + seen[3] + ")", seen[3] != null && seen[3].startsWith("HTTP/1.1 404"));
        check("החזרה הנכונה — 302 ונכנסים (" + seen[4] + ", " + r.status + ")", "302".equals(seen[4]) && r.ok() && Account.current() != null);
        check("הקוד מומש פעם אחת בלבד", count("/v1/auth/redeem") == redeems + 1);
        check("אחרי הכניסה החיבור המקומי סגור — חזרה נוספת לא מתקבלת (" + seen[5] + ")", "closed".equals(seen[5]));
        check("החיבור המקומי רק על 127.0.0.1", Account.LOOPBACK.getHostAddress().equals("127.0.0.1"));

        // ---- 4. הדפדפן נסגר / לא סיימו — נגמר הזמן: הודעה בעברית שאומרת מה ללחוץ, והחיבור נסגר
        use(work, "pc3");
        final String[] port = { null };
        Account.browser = url -> port[0] = param(url, "port");
        long t0 = System.currentTimeMillis();
        r = Account.signIn("student", null, null, 1500);
        check("נגמר הזמן — cancelled עם הודעה (" + r.message + ")", "cancelled".equals(r.status) && r.message.contains("כניסה עם Google") && System.currentTimeMillis() - t0 < 6000);
        check("נגמר הזמן — החיבור המקומי נסגר", !open(port[0]));
        check("נגמר הזמן — לא נשמר כלום", Account.userState() == null);

        // ---- 5. ביטול בחלון
        final Account.Cancel cancel = new Account.Cancel();
        Account.browser = url -> new Thread(() -> { sleep(300); cancel.cancel(); }).start();
        r = Account.signIn("student", null, cancel, 20000);
        check("ביטול — cancelled בלי הודעה", "cancelled".equals(r.status) && r.message.isEmpty());

        // ---- 6. לחיצה פעמיים / שני חלונות של DSViz בבת אחת: כל כניסה עם פורט ו-state משלה, ו-state של אחת לא עובר בשנייה
        use(work, "pc4");
        answers.put("/v1/auth/redeem", redeemOk("teacher", "t4@test.dev", 0));   // האישור קשור למחשב — לכל מחשב אישור משלו
        final List<String> urls = java.util.Collections.synchronizedList(new ArrayList<String>());
        Account.browser = urls::add;
        final Account.Result[] two = new Account.Result[2];
        Thread ta = new Thread(() -> two[0] = Account.signIn("teacher", null, null, 15000));
        Thread tb = new Thread(() -> two[1] = Account.signIn("teacher", null, null, 15000));
        ta.start();
        tb.start();
        for (int i = 0; i < 100 && urls.size() < 2; i++) Thread.sleep(50);
        String ua = urls.get(0), ub = urls.get(1);
        check("שתי כניסות — פורטים ו-state שונים", !param(ua, "port").equals(param(ub, "port")) && !param(ua, "state").equals(param(ub, "state")));
        check("state של כניסה אחת בפורט של השנייה — 400",
                get("http://127.0.0.1:" + param(ub, "port") + "/done?code=" + fill('c', 30) + "&state=" + param(ua, "state")) == 400);
        check("שתי הכניסות מסתיימות", get("http://127.0.0.1:" + param(ua, "port") + "/done?code=" + fill('a', 30) + "&state=" + param(ua, "state")) == 302
                & get("http://127.0.0.1:" + param(ub, "port") + "/done?code=" + fill('b', 30) + "&state=" + param(ub, "state")) == 302);
        ta.join(5000);
        tb.join(5000);
        check("שתי הכניסות ok, ויש כניסה שמורה", two[0] != null && two[0].ok() && two[1] != null && two[1].ok() && Account.current() != null);

        // ---- 7. הדפדפן לא נפתח (חריגה) — לא נופלים: מחכים, והמשתמש מדביק את הכתובת שהועתקה
        use(work, "pc5");
        answers.put("/v1/auth/redeem", redeemOk("teacher", "t5@test.dev", 0));
        final String[] copied = { null };
        Account.browser = url -> {
            copied[0] = url;
            new Thread(() -> { sleep(300); get("http://127.0.0.1:" + param(copied[0], "port") + "/done?code=" + fill('c', 30) + "&state=" + param(copied[0], "state")); }).start();
            throw new IllegalStateException("no browser");
        };
        r = Account.signIn("teacher", u -> { }, null, 10000);
        check("דפדפן שלא נפתח — הכניסה ממשיכה דרך הכתובת שהועתקה (" + r.status + ")", r.ok());

        // ---- 8. השרת נכשל במימוש הקוד: הודעה בעברית, ולא נשמר כלום
        String[][] redeemFails = {
            { "500", "{\"ok\":false,\"code\":\"internal\",\"error\":\"תקלה בשרת. נסו שוב בעוד דקה.\"}", "application/json" },
            { "429", "{\"ok\":false,\"code\":\"rate\",\"error\":\"יותר מדי ניסיונות.\"}", "application/json" },
            { "400", "{\"ok\":false,\"code\":\"expired\",\"error\":\"הכניסה פגה.\"}", "application/json" },
            { "502", "<html>Bad gateway</html>", "text/html" },
            { "200", "", "text/html" },
        };
        for (String[] g : redeemFails) {
            use(work, "pc6-" + g[0] + g[1].length());
            answers.put("/v1/auth/redeem", g);
            Account.browser = backTo("c");
            r = Account.signIn("teacher", null, null, 10000);
            check("מימוש הקוד: " + g[0] + " — לא נכנסים, הודעה (" + r.status + ": " + r.message + ")",
                    !r.ok() && !r.message.isEmpty() && Account.current() == null && Account.userState() == null);
        }

        // ---- 9. כרטיס / אישור פגומים, ושעון המחשב מקדים (האישור כבר "פג" לפי המחשב): לא "נכנסת" — הסבר
        use(work, "pc7");
        answers.put("/v1/auth/redeem", json("{\"ok\":true,\"status\":\"ok\",\"token\":\"garbage\",\"pass\":\"not.a.pass\",\"profile\":{\"role\":\"teacher\",\"email\":\"x@test.dev\"}}"));
        Account.browser = backTo("c");
        r = Account.signIn("teacher", null, null, 10000);
        check("אישור פגום מהשרת — לא 'נכנסת' (" + r.status + ": " + r.message + ")", !r.ok() && r.message.contains("השעה") && Account.current() == null);
        use(work, "pc8");
        answers.put("/v1/auth/redeem", redeemOk("teacher", "t8@test.dev", -3600));
        r = Account.signIn("teacher", null, null, 10000);
        check("שעון המחשב מקדים (האישור כבר פג במחשב) — הסבר על השעון (" + r.status + ")", "pass".equals(r.status) && r.message.contains("התאריך"));

        // ---- 10. הקובץ השמור פגום / לא קריא — כאילו אין כניסה, בלי חריגה; כניסה חדשה דורסת אותו
        use(work, "pc9");
        File uf = Account.userFile();
        uf.getParentFile().mkdirs();
        Files.write(uf.toPath(), "{\"v\":1,\"data\":\"@@@not-base64@@@\"}".getBytes(StandardCharsets.UTF_8));
        check("קובץ פגום — אין כניסה, בלי חריגה", Account.current() == null && Account.headers().isEmpty());
        Files.write(uf.toPath(), new byte[] { 0, 1, 2, 3, (byte) 0xff });
        check("קובץ זבל — אין כניסה", Account.current() == null);
        answers.put("/v1/auth/redeem", redeemOk("student", "s9@test.dev", 0));
        Account.browser = backTo("c");
        r = Account.signIn("student", null, null, 10000);
        check("כניסה חדשה דורסת את הקובץ הפגום", r.ok() && Account.current() != null && Account.current().isStudent());

        // ---- 11. התנתקות: עם אינטרנט — הכרטיס מבוטל בשרת; בלי — נמחק מהמחשב ונשלח לשרת בפעם הבאה; העמדה לא מושפעת
        use(work, "pc10");
        stationSaved();
        signedIn("teacher", "t10@test.dev", "u.sid10.secretsecretsecretsecretAA");
        logoutTokens.clear();
        answers.put("/v1/access/logout", json("{\"ok\":true}"));
        r = Account.signOut();
        check("התנתקות עם אינטרנט — ok, הכרטיס בוטל בשרת", r.ok() && logoutTokens.contains("u.sid10.secretsecretsecretsecretAA") && Account.pendingLogouts().isEmpty());
        check("אחרי התנתקות — המורה לא מחובר, העמדה של המחשב נשארת", Account.userState() == null && Account.current() != null && Account.current().isStation());
        signedIn("student", "s10@test.dev", "u.sid11.secretsecretsecretsecretBB");
        System.setProperty("dsviz.worker", "http://127.0.0.1:9");
        r = Account.signOut();
        check("התנתקות בלי אינטרנט — נמחק מהמחשב, הסבר שהשרת יעודכן (" + r.message + ")",
                "offline".equals(r.status) && r.message.contains("בפעם הבאה") && Account.userState() == null && Account.pendingLogouts().size() == 1);
        File pending = new File(System.getProperty("dsviz.home"), "logout.json");
        check("ההתנתקות הממתינה שמורה רק לבעלים", pending.isFile() && PosixFilePermissions.toString(Files.getPosixFilePermissions(pending.toPath())).equals("rw-------"));
        Account.flushPendingLogouts();
        check("עדיין בלי אינטרנט — ההתנתקות ממתינה", Account.pendingLogouts().size() == 1);
        System.setProperty("dsviz.worker", "http://127.0.0.1:" + mockPort);
        answers.remove("/v1/access/check");   // השרת עונה 404 על הבדיקה — תקלה, לא "בוטל": העמדה נשארת
        Account.refresh();
        check("כשיש אינטרנט — ההתנתקות נשלחת לשרת ונמחקת מהרשימה", logoutTokens.contains("u.sid11.secretsecretsecretsecretBB") && Account.pendingLogouts().isEmpty());
        check("העמדה עדיין מחוברת", Account.current() != null && Account.current().isStation());
        check("קובץ ההתנתקויות הממתינות נמחק", !pending.exists());

        // ---- 12. Java שמעדיפה IPv6 (‎-Djava.net.preferIPv6Addresses=true‎) — הדפדפן חוזר ל-127.0.0.1 ועדיין נכנסים
        ProcessBuilder pb = new ProcessBuilder(new File(System.getProperty("java.home"), "bin/java").getPath(), "-Djava.net.preferIPv6Addresses=true",
                "-cp", System.getProperty("java.class.path"), SignInEdge.class.getName(), "ipv6-child", new File(work, "ipv6").getPath());
        pb.redirectErrorStream(true);
        pb.environment().remove("JAVA_TOOL_OPTIONS");
        Process p = pb.start();
        String out = new String(readAll(p.getInputStream()), StandardCharsets.UTF_8).trim();
        p.waitFor();
        check("IPv6 מועדף — הכניסה מסתיימת (" + out.replace('\n', ' ') + ")", out.contains("IPV6-OK"));

        mock.stop(0);
        System.out.println("\n" + pass + " עברו, " + fail + " נכשלו");
        System.exit(fail == 0 ? 0 : 1);
    }

    /** תהליך נפרד עם preferIPv6Addresses: כניסה שלמה מול שרת מדומה */
    static void ipv6Child(String[] a) throws Exception {
        keys = KeyPairGenerator.getInstance("RSA").generateKeyPair();
        startMock();
        System.setProperty("dsviz.passKey", Base64.getEncoder().encodeToString(keys.getPublic().getEncoded()));
        System.setProperty("dsviz.worker", "http://127.0.0.1:" + mockPort);
        use(new File(a[1]), "v6");
        answers.put("/v1/auth/redeem", redeemOk("teacher", "v6@test.dev", 0));
        Account.browser = backTo("c");
        Account.Result r = Account.signIn("teacher", null, null, 8000);
        System.out.println("loopback " + java.net.InetAddress.getLoopbackAddress().getHostAddress() + " result " + r.status + " " + r.message);
        System.out.println(r.ok() ? "IPV6-OK" : "IPV6-FAIL");
        mock.stop(0);
        System.exit(0);
    }

    // ------------------------------------------------------------------ עזרים

    static void use(File work, String pc) {
        System.setProperty("dsviz.machine", pc + "-id");
        System.setProperty("dsviz.machineDir", new File(work, pc + "/machine").getPath());
        System.setProperty("dsviz.home", new File(work, pc + "/user").getPath());
        Account.resetForTest();
    }

    /** "הדפדפן" חוזר מיד לתוכנה עם state נכון וקוד */
    static java.util.function.Consumer<String> backTo(final String c) {
        return url -> new Thread(() -> { sleep(100); get("http://127.0.0.1:" + param(url, "port") + "/done?code=" + fill(c.charAt(0), 30) + "&state=" + param(url, "state")); }).start();
    }

    static String makePass(String kind, String email, long expShift) throws Exception {
        long now = System.currentTimeMillis() / 1000;
        long exp = expShift == 0 ? now + 3650L * 86400 : now + expShift;
        String payload = "{\"v\":1,\"kind\":\"" + kind + "\",\"sub\":\"" + kind + ":" + email + "\",\"name\":\"\",\"email\":\"" + email + "\",\"until\":\"\",\"mid\":\""
                + Account.machine() + "\",\"iat\":" + now + ",\"exp\":" + exp + "}";
        String text = Base64.getUrlEncoder().withoutPadding().encodeToString(payload.getBytes(StandardCharsets.UTF_8));
        Signature s = Signature.getInstance("SHA256withRSA");
        s.initSign(keys.getPrivate());
        s.update(text.getBytes(StandardCharsets.US_ASCII));
        return text + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(s.sign());
    }

    static String[] redeemOk(String role, String email, long expShift) throws Exception {
        return json("{\"ok\":true,\"status\":\"ok\",\"token\":\"u.sidsidsid.secretsecretsecretsecret99\",\"pass\":\"" + makePass(role, email, expShift)
                + "\",\"profile\":{\"role\":\"" + role + "\",\"email\":\"" + email + "\",\"name\":\"\"}" + (role.equals("teacher") ? ",\"teacher\":{\"code\":\"K7QM\",\"seats\":3,\"used\":1}" : "") + "}");
    }

    /** כניסה שמורה במחשב (כאילו נכנסו קודם) */
    static void signedIn(String role, String email, String token) throws Exception {
        Map<String, Object> d = new LinkedHashMap<String, Object>();
        d.put("token", token);
        d.put("pass", makePass(role, email, 0));
        d.put("status", "ok");
        d.put("email", email);
        d.put("role", role);
        Account.save(Account.userFile(), d);
    }

    static void stationSaved() throws Exception {
        Map<String, Object> d = new LinkedHashMap<String, Object>();
        d.put("token", "s.station1234.secretsecretsecretsecretST");
        d.put("pass", makePass("station", "", 0));
        d.put("status", "ok");
        File f = new File(System.getProperty("dsviz.machineDir"), "station.json");
        Account.save(f, d);
    }

    static String[] json(String body) {
        return new String[] { "200", body, "application/json" };
    }

    static void startMock() throws Exception {
        mock = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        mock.createContext("/", ex -> {
            String path = ex.getRequestURI().getPath();
            hits.computeIfAbsent(path, k -> new AtomicInteger()).incrementAndGet();
            String body = new String(readAll(ex.getRequestBody()), StandardCharsets.UTF_8);
            if (path.equals("/v1/access/logout")) {
                Map<String, Object> j = Json.obj(Json.parse(body));
                logoutTokens.add(Json.str(j.get("token")));
            }
            String[] ans = answers.get(path);
            if (ans == null) ans = new String[] { "404", "{\"ok\":false,\"code\":\"not_found\",\"error\":\"?\"}", "application/json" };
            byte[] out = ans[1].getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().set("Content-Type", ans[2]);
            ex.sendResponseHeaders(Integer.parseInt(ans[0]), out.length == 0 ? -1 : out.length);
            if (out.length > 0) try (OutputStream o = ex.getResponseBody()) { o.write(out); }
            ex.close();
        });
        mock.start();
        mockPort = mock.getAddress().getPort();
    }

    static int count(String path) {
        AtomicInteger n = hits.get(path);
        return n == null ? 0 : n.get();
    }

    static int get(String url) {
        try {
            HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
            c.setInstanceFollowRedirects(false);
            c.setConnectTimeout(3000);
            c.setReadTimeout(15000);
            return c.getResponseCode();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    static String raw(String port, String request) throws Exception {
        try (Socket s = new Socket("127.0.0.1", Integer.parseInt(port))) {
            s.getOutputStream().write(request.getBytes(StandardCharsets.US_ASCII));
            s.getOutputStream().flush();
            String all = new String(readAll(s.getInputStream()), StandardCharsets.US_ASCII);
            return all.split("\r\n")[0];
        }
    }

    static boolean open(String port) {
        try (Socket s = new Socket()) {
            s.connect(new InetSocketAddress("127.0.0.1", Integer.parseInt(port)), 1000);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    static byte[] readAll(InputStream in) throws java.io.IOException {
        java.io.ByteArrayOutputStream o = new java.io.ByteArrayOutputStream();
        byte[] b = new byte[8192];
        for (int n; (n = in.read(b)) > 0; ) o.write(b, 0, n);
        return o.toByteArray();
    }

    static String param(String url, String name) {
        for (String kv : url.substring(url.indexOf('?') + 1).split("&")) if (kv.startsWith(name + "=")) return kv.substring(name.length() + 1);
        return "";
    }

    static String fill(char c, int n) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < n; i++) b.append(c);
        return b.toString();
    }

    static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }

    static void check(String name, boolean ok) {
        if (ok) { pass++; System.out.println("  ok   " + name); } else { fail++; System.out.println("  FAIL " + name); }
    }
}
