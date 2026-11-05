package il.lamerhav.dsviz;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * בדיקת Account (הקוד המשותף של התוספים) מול השרת המקומי (worker/tools/dev-server.mjs) ואמולטור Firebase:
 * עמדת מחשב עם קוד מורה, מורה ותלמיד דרך "הדפדפן" (מדומה — אותן קריאות שהדף /signin עושה), עבודה בלי אינטרנט,
 * שחרור עמדה מההנהלה, תשלום, התנתקות, והכותרות לשליחה ל-Gemini.
 *   java -cp ... il.lamerhav.dsviz.AccountIt http://127.0.0.1:8787 127.0.0.1:9099 <work-dir>
 */
public final class AccountIt {
    static String server, auth;
    static int pass, fail;
    static volatile String browserEmail = "";

    public static void main(String[] a) throws Exception {
        server = a[0];
        auth = a[1];
        File work = new File(a[2]);
        System.setProperty("dsviz.worker", server);
        System.setProperty("dsviz.passKey", Json.str(Json.obj(Json.parse(get(server + "/v1/access/key"))).get("spki")));
        String admin = idToken("motiml77@gmail.com");
        Account.browser = AccountIt::fakeBrowser;

        // מחשב 1 — עמדה
        use(work, "pc1", "alice");
        String email = "it.teacher" + System.nanoTime() + "@test.dev";
        api("POST", "/v1/admin/teacher", admin, "{\"create\":true,\"email\":\"" + email + "\",\"name\":\"מורה בדיקה\",\"seats\":1}");
        String code = Json.str(Json.obj(Json.obj(Json.parse(api("GET", "/v1/admin/teacher?email=" + email, admin, null))).get("teacher")).get("code"));

        check("בלי כניסה — אין גישה", Account.current() == null);
        Account.Result bad = Account.claimStation("ABCD-EFGH", "מעבדה");
        check("קוד שגוי — bad-code", "bad-code".equals(bad.status));
        Account.Result r = Account.claimStation(code.toLowerCase().replace("-", " "), "מעבדה 1 · עמדה 1");
        check("עמדה מתחברת (" + r.status + " " + r.message + ")", r.ok());
        Account.Access acc = Account.current();
        check("המאגר פתוח כעמדה", acc != null && acc.isStation() && acc.teacher.equals("מורה בדיקה"));
        check("כותרות ל-Gemini", Account.headers().get("X-DSViz-Access").startsWith("s."));

        // משתמש אחר של Windows באותו מחשב — רואה את העמדה
        use(work, "pc1", "bob");
        check("משתמש אחר באותו מחשב — גם פתוח", Account.current() != null && Account.current().isStation());

        // בלי אינטרנט: עדיין פתוח
        System.setProperty("dsviz.worker", "http://127.0.0.1:9");
        check("בלי אינטרנט — refresh לא מוחק", "offline".equals(Account.refresh().status) && Account.current() != null);
        System.setProperty("dsviz.worker", server);

        // קובץ שהועתק למחשב אחר — לא נפתח
        use(work, "pc2", "alice");
        File pc1 = new File(work, "pc1/machine/station.json");
        File pc2 = new File(work, "pc2/machine/station.json");
        pc2.getParentFile().mkdirs();
        Files.copy(pc1.toPath(), pc2.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        check("העתקה למחשב אחר — לא נפתח", Account.current() == null);
        pc2.delete();
        Account.Result full = Account.claimStation(code, "מעבדה 1 · עמדה 2");
        check("מחשב שני: אין עוד היתרים", "full".equals(full.status) && full.teacherInt("seats") == 1);

        // מורה בדפדפן — במחשב 2
        browserEmail = email;
        Account.Result t = Account.signIn("teacher", null, null, 30000);
        check("מורה נכנס דרך הדפדפן (" + t.status + " " + t.message + ")", t.ok());
        check("המאגר פתוח כמורה", Account.current() != null && Account.current().isTeacher());
        Account.signOut();
        check("התנתקות — סגור", Account.current() == null);

        // תלמיד — צריך לשלם; אחרי תשלום — פתוח
        String student = "it.student" + System.nanoTime() + "@test.dev";
        browserEmail = student;
        Account.Result s = Account.signIn("student", null, null, 30000);
        check("תלמיד בלי תשלום — pay, עם כרטיס ובלי גישה", "pay".equals(s.status) && Account.current() == null && Account.userState() != null);
        api("POST", "/v1/admin/student", admin, "{\"email\":\"" + student + "\",\"until\":\"2027-08-31\"}");
        Account.Result after = Account.refresh();
        check("אחרי הענקה — refresh פותח (" + after.status + ")", after.ok() && Account.current() != null && Account.current().isStudent());

        // ההנהלה משחררת את העמדה של מחשב 1 → בבדיקה הבאה עם אינטרנט נמחקת
        use(work, "pc1", "alice");
        String sid = Json.str(Json.obj(Json.arr(Json.obj(Json.parse(api("GET", "/v1/admin/teacher?email=" + email, admin, null))).get("stations")).get(0)).get("id"));
        api("POST", "/v1/admin/station/release", admin, "{\"id\":\"" + sid + "\"}");
        check("לפני בדיקה — עדיין פתוח (בלי אינטרנט זה נשאר)", Account.current() != null);
        Account.refresh();
        check("אחרי בדיקה עם אינטרנט — העמדה נמחקה", Account.current() == null);

        // מעבדה: מחשבים משוכפלים מתמונה אחת (אותו מזהה Windows בכולם) — כל מחשב עמדה נפרדת, ולא דורסים זה את זה
        String lab = "it.lab" + System.nanoTime() + "@test.dev";
        api("POST", "/v1/admin/teacher", admin, "{\"create\":true,\"email\":\"" + lab + "\",\"name\":\"מורה מעבדה\",\"seats\":3}");
        String labCode = Json.str(Json.obj(Json.obj(Json.parse(api("GET", "/v1/admin/teacher?email=" + lab, admin, null))).get("teacher")).get("code"));
        System.setProperty("dsviz.hostname", "LAB-01");
        cloned(work, "lab1");
        String id1 = Account.machine();
        check("מחשב משובט 1 — עמדה", Account.claimStation(labCode, "מעבדה · עמדה 1").ok() && Account.current() != null);
        System.setProperty("dsviz.hostname", "LAB-02");
        cloned(work, "lab2");
        String id2 = Account.machine();
        check("מחשב משובט 2 — מזהה שונה (אותו מזהה Windows)", !id1.equals(id2));
        check("מחשב משובט 2 — עמדה נפרדת", Account.claimStation(labCode, "מעבדה · עמדה 2").ok() && Account.current() != null);
        System.setProperty("dsviz.hostname", "LAB-03");
        cloned(work, "lab3");
        check("מחשב משובט 3 — עמדה שלישית", Account.claimStation(labCode, "מעבדה · עמדה 3").ok());
        Account.Result lab4 = null;
        System.setProperty("dsviz.hostname", "LAB-04");
        cloned(work, "lab4");
        lab4 = Account.claimStation(labCode, "מעבדה · עמדה 4");
        check("מחשב רביעי — אין עוד היתרים (3 עמדות)", "full".equals(lab4.status));
        Map<String, Object> labInfo = Json.obj(Json.obj(Json.parse(api("GET", "/v1/admin/teacher?email=" + lab, admin, null))).get("teacher"));
        check("בניהול: 3 עמדות בשימוש", "3".equals(String.valueOf(labInfo.get("used")).replace(".0", "")));
        // הפעלה מחדש של מחשב 1 (אותו מחשב) — לא נעלם ולא דורס את האחרים
        System.setProperty("dsviz.hostname", "LAB-01");
        cloned(work, "lab1");
        check("אותו מחשב אחרי הפעלה מחדש — אותו מזהה", id1.equals(Account.machine()));
        check("אותו מחשב — עדיין פתוח", Account.current() != null && Account.refresh().ok());
        System.setProperty("dsviz.hostname", "LAB-02");
        cloned(work, "lab2");
        check("מחשב 2 — עדיין פתוח (לא נדרס)", Account.current() != null && Account.refresh().ok());
        // העתקה של כל תיקיית ההתקנה למחשב אחר (שם שונה) — מזהה חדש, לא פותח
        File src1 = new File(work, "lab1/machine");
        File dst = new File(work, "labcopy/machine");
        dst.mkdirs();
        for (File f : src1.listFiles()) Files.copy(f.toPath(), new File(dst, f.getName()).toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        System.setProperty("dsviz.hostname", "LAB-05");
        cloned(work, "labcopy");
        check("העתקת הקבצים למחשב בשם אחר — מזהה אחר ולא פתוח", !id1.equals(Account.machine()) && Account.current() == null);

        System.out.println("\n" + pass + " עברו, " + fail + " נכשלו");
        System.exit(fail == 0 ? 0 : 1);
    }

    static void use(File work, String pc, String user) {
        System.setProperty("dsviz.machine", pc + "-id");
        System.setProperty("dsviz.machineDir", new File(work, pc + "/machine").getPath());
        System.setProperty("dsviz.home", new File(work, pc + "/" + user).getPath());
        Account.resetForTest();
    }

    /** מחשב משובט: אותו מזהה Windows (dsviz.machine) בכל המחשבים, תיקיית מחשב משלו */
    static void cloned(File work, String pc) {
        System.setProperty("dsviz.machine", "same-windows-guid-from-image");
        System.setProperty("dsviz.machineDir", new File(work, pc + "/machine").getPath());
        System.setProperty("dsviz.home", new File(work, pc + "/user").getPath());
        Account.resetForTest();
    }

    static void check(String name, boolean ok) {
        if (ok) { pass++; System.out.println("  ok   " + name); } else { fail++; System.out.println("  FAIL " + name); }
    }

    /** "הדפדפן": מה שהדף /signin עושה — כניסה עם Google (באמולטור), exchange, וחזרה ל-127.0.0.1 */
    static void fakeBrowser(String url) {
        new Thread(() -> {
            try {
                Map<String, String> q = new LinkedHashMap<String, String>();
                for (String p : url.substring(url.indexOf('?') + 1).split("&")) {
                    String[] kv = p.split("=", 2);
                    q.put(kv[0], java.net.URLDecoder.decode(kv[1], "UTF-8"));
                }
                String body = "{\"idToken\":\"" + idToken(browserEmail) + "\",\"role\":\"" + q.get("role") + "\",\"challenge\":\"" + q.get("challenge") + "\"}";
                String code = Json.str(Json.obj(Json.parse(api("POST", "/v1/auth/exchange", null, body))).get("code"));
                HttpURLConnection c = (HttpURLConnection) new URL("http://127.0.0.1:" + q.get("port") + "/done?code=" + code + "&state=" + q.get("state")).openConnection();
                c.setInstanceFollowRedirects(false);
                int st = c.getResponseCode();
                String loc = c.getHeaderField("Location");
                if (st != 302 || loc == null || !loc.contains("/welcome?r=")) System.out.println("  FAIL redirect " + st + " " + loc);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    static String idToken(String email) throws Exception {
        String claims = "{\"sub\":\"uid-" + Math.abs(email.hashCode()) + "\",\"email\":\"" + email + "\",\"email_verified\":true,\"name\":\"\"}";
        String body = "{\"postBody\":\"id_token=" + java.net.URLEncoder.encode(claims, "UTF-8") + "&providerId=google.com\",\"requestUri\":\"http://localhost\",\"returnSecureToken\":true}";
        Map<String, Object> j = Json.obj(Json.parse(http("POST", "http://" + auth + "/identitytoolkit.googleapis.com/v1/accounts:signInWithIdp?key=fake-api-key", null, body)));
        return Json.str(j.get("idToken"));
    }

    static String api(String method, String path, String token, String body) throws Exception {
        return http(method, server + path, token, body);
    }

    static String get(String url) throws Exception {
        return http("GET", url, null, null);
    }

    static String http(String method, String url, String token, String body) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setRequestMethod(method);
        if (token != null) c.setRequestProperty("Authorization", "Bearer " + token);
        if (body != null) {
            c.setDoOutput(true);
            c.setRequestProperty("Content-Type", "application/json");
            try (OutputStream o = c.getOutputStream()) { o.write(body.getBytes(StandardCharsets.UTF_8)); }
        }
        int st = c.getResponseCode();
        InputStream in = st >= 400 ? c.getErrorStream() : c.getInputStream();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] b = new byte[8192];
        int n;
        while (in != null && (n = in.read(b)) > 0) out.write(b, 0, n);
        String s = new String(out.toByteArray(), StandardCharsets.UTF_8);
        if (st >= 400) throw new RuntimeException(method + " " + url + " → " + st + " " + s);
        return s;
    }
}
