package il.lamerhav.dsviz;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URL;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import com.sun.net.httpserver.HttpServer;

/**
 * הכניסה למאגר התרגילים ולשליחת תרגיל — משותף ל-Eclipse, ל-IntelliJ ול-Visual Studio (Java 8, בלי ממשק).
 *
 * שלוש דרכים:
 *   • מורה / תלמיד — כניסה עם Google בדפדפן: התוסף פותח שרת קטן ב-127.0.0.1, הדפדפן חוזר אליו עם קוד חד-פעמי,
 *     והתוסף מממש אותו מול השרת עם ה-verifier (PKCE) — רק החלון הזה יכול. נשמר אצל המשתמש (~/.dsviz/account.json).
 *   • עמדת מחשב — קוד המורה + שם למחשב. נשמר לכל המחשב (Windows: ProgramData\DSViz), כך שכל משתמש של Windows רואה את המאגר.
 *
 * אחרי הכניסה הראשונה (עם אינטרנט) נשמר "אישור" חתום (RSA) — המאגר נפתח בלי אינטרנט. כשיש אינטרנט בודקים שוב ברקע
 * (refresh): עמדה ששוחררה / מורה שהושבת / גישה שבוטלה — נמחקים.
 * השמירה במחשב מוצפנת (AES-GCM) במפתח שנגזר ממזהה המחשב — קובץ שהועתק למחשב אחר לא נפתח שם (וגם האישור קשור למחשב).
 */
public final class Account {
    private Account() {}

    /** מי מחובר במחשב הזה (מתוך אישור חתום ותקף) */
    public static final class Access {
        public final String kind, name, email, until, teacher;

        Access(String kind, String name, String email, String until, String teacher) {
            this.kind = kind; this.name = name; this.email = email; this.until = until; this.teacher = teacher;
        }

        public boolean isTeacher() { return "teacher".equals(kind); }
        public boolean isStation() { return "station".equals(kind); }
        public boolean isStudent() { return "student".equals(kind); }

        /** שורה קצרה לכותרת המאגר */
        public String label() {
            if (isStation()) return "עמדת מחשב: " + name + (teacher.isEmpty() ? "" : " · " + teacher);
            if (isTeacher()) return "מורה: " + (name.isEmpty() ? email : name);
            return (name.isEmpty() ? email : name) + (until.isEmpty() ? "" : " · פתוח עד " + dmy(until));
        }
    }

    /** תוצאה של פעולה מול השרת. status: ok / pay / not-teacher / blocked / devices / full / bad-code / revoked / invalid / offline / error / cancelled */
    public static final class Result {
        public final String status, message;
        public final Map<String, Object> body;

        Result(String status, String message, Map<String, Object> body) {
            this.status = status; this.message = message == null ? "" : message; this.body = body == null ? new LinkedHashMap<String, Object>() : body;
        }

        public boolean ok() { return "ok".equals(status); }

        public Map<String, Object> teacher() { return Json.obj(body.get("teacher")); }

        public int teacherInt(String k) {
            Object v = teacher().get(k);
            return v instanceof Number ? ((Number) v).intValue() : 0;
        }
    }

    /** איך פותחים את הדפדפן (בבדיקות — מוחלף) */
    public static volatile java.util.function.Consumer<String> browser = Account::openBrowser;

    /** המפתח הציבורי של השרת (החתימה על האישור). ציבורי — לא סוד; המפתח הפרטי נמצא רק בשרת (PASS_KEY) */
    static final String PASS_KEY = "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAzcPy66SILXotJ2VPxjt93e7dGBR1chdnNh6Ptgr6KYXn5DGhSYEbkT3dsFKWO96QQCw/wG/bsQmqtLDxLHgjzOxjmYMGxyLmBkhHfywmYBl8JvRRPkWUn5AqZI5VgQ7cqeF4vbjMIOeb3jR3GB4QhfaI04v+4mAna6KQEC0drXfhT5oMuOhbfxH2t77tCC1iUkAnnfA/VprWcvPyieyzdTxZ4/r4e9VS5hTGceRsKpsdC7/Euy31vqrJxeVLV7Mwc1PkAMk0ghEJtSRBbvZdRUv5X5K8DLAjVpEVRHlq2XwobMI2c8oSU8qGESGsUk/R1lGUWIqGOXE+UdW6ODPVjQIDAQAB";

    private static final SecureRandom RANDOM = new SecureRandom();
    private static volatile String machineRaw, machineHash;
    private static volatile String lastError = "";

    // ------------------------------------------------------------------ הגדרות

    static String serverUrl() {
        String u = System.getProperty("dsviz.worker");
        if (u == null || u.isEmpty()) u = System.getenv("DSVIZ_WORKER_URL");
        if (u == null || u.isEmpty()) u = props("/resources/ai.properties").getProperty("url", "");
        return u.trim().replaceAll("/+$", "");
    }

    static String passKey() {
        String k = System.getProperty("dsviz.passKey");
        if (k == null || k.isEmpty()) k = System.getenv("DSVIZ_PASS_KEY");
        if (k == null || k.isEmpty()) k = props("/resources/access.properties").getProperty("passKey", "");
        if (k == null || k.isEmpty()) k = PASS_KEY;
        return k.trim();
    }

    private static Properties props(String res) {
        Properties p = new Properties();
        try (InputStream in = Account.class.getResourceAsStream(res)) {
            if (in != null) p.load(new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (IOException ignored) {
            // בלי קובץ הגדרות
        }
        return p;
    }

    static File userDir() {
        String d = System.getProperty("dsviz.home");
        return d != null && !d.isEmpty() ? new File(d) : new File(System.getProperty("user.home"), ".dsviz");
    }

    /** תיקייה לכל המחשב (עמדה) — אם אי אפשר לכתוב שם, אצל המשתמש */
    static File machineDir() {
        String d = System.getProperty("dsviz.machineDir");
        if (d != null && !d.isEmpty()) return new File(d);
        String os = System.getProperty("os.name", "").toLowerCase();
        File f;
        if (os.contains("win")) {
            String pd = System.getenv("ProgramData");
            f = new File(pd == null || pd.isEmpty() ? "C:\\ProgramData" : pd, "DSViz");
        } else if (os.contains("mac")) f = new File("/Users/Shared/DSViz");
        else f = new File("/var/tmp/dsviz");
        return f;
    }

    // ------------------------------------------------------------------ מזהה המחשב

    /** מזהה קבוע של המחשב (hash) — אותו מזהה לכל המשתמשים ולכל התוכנות (Eclipse / IntelliJ / Visual Studio) */
    public static String machine() {
        if (machineHash == null) {
            String raw = System.getProperty("dsviz.machine");
            if (raw == null || raw.isEmpty()) raw = osMachineId();
            machineRaw = raw;
            machineHash = hex(sha256("dsviz|" + raw)).substring(0, 32);
        }
        return machineHash;
    }

    private static String osMachineId() {
        String os = System.getProperty("os.name", "").toLowerCase();
        try {
            if (os.contains("win")) {
                String out = run("reg", "query", "HKLM\\SOFTWARE\\Microsoft\\Cryptography", "/v", "MachineGuid");
                java.util.regex.Matcher m = java.util.regex.Pattern.compile("MachineGuid\\s+REG_SZ\\s+(\\S+)").matcher(out);
                if (m.find()) return m.group(1);
            } else if (os.contains("mac")) {
                String out = run("ioreg", "-rd1", "-c", "IOPlatformExpertDevice");
                java.util.regex.Matcher m = java.util.regex.Pattern.compile("\"IOPlatformUUID\"\\s*=\\s*\"([^\"]+)\"").matcher(out);
                if (m.find()) return m.group(1);
            } else {
                for (String p : new String[] { "/etc/machine-id", "/var/lib/dbus/machine-id" }) {
                    File f = new File(p);
                    if (f.isFile()) {
                        String s = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8).trim();
                        if (!s.isEmpty()) return s;
                    }
                }
            }
        } catch (Exception ignored) {
            // נופלים למזהה שנשמר בקובץ
        }
        // אין מזהה של מערכת ההפעלה — מזהה אקראי שנשמר פעם אחת (לכל המחשב אם אפשר)
        for (File dir : new File[] { machineDir(), userDir() }) {
            File f = new File(dir, "machine-id");
            try {
                if (f.isFile()) return new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8).trim();
                dir.mkdirs();
                String id = UUID.randomUUID().toString();
                Files.write(f.toPath(), id.getBytes(StandardCharsets.UTF_8));
                return id;
            } catch (IOException ignored) {
                // נסה את התיקייה הבאה
            }
        }
        return "unknown-" + System.getProperty("user.name", "");
    }

    private static String run(String... cmd) throws IOException, InterruptedException {
        Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        try (InputStream in = p.getInputStream()) {
            byte[] b = new byte[4096];
            int n;
            while ((n = in.read(b)) > 0) o.write(b, 0, n);
        }
        p.waitFor(10, TimeUnit.SECONDS);
        return new String(o.toByteArray(), StandardCharsets.UTF_8);
    }

    static String machineName() {
        String n = System.getenv("COMPUTERNAME");
        if (n == null || n.isEmpty()) {
            try { n = InetAddress.getLocalHost().getHostName(); } catch (Exception e) { n = ""; }
        }
        return n == null || n.isEmpty() ? "מחשב" : n;
    }

    // ------------------------------------------------------------------ שמירה מוצפנת

    private static final String USER_FILE = "account.json", STATION_FILE = "station.json";

    private static File stationFile() {
        File f = new File(machineDir(), STATION_FILE);
        if (f.isFile()) return f;
        File u = new File(userDir(), STATION_FILE);
        return u.isFile() ? u : f;
    }

    static Map<String, Object> load(File f) {
        try {
            if (!f.isFile()) return null;
            Map<String, Object> outer = Json.obj(Json.parse(new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8)));
            byte[] all = Base64.getDecoder().decode(Json.str(outer.get("data")));
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.DECRYPT_MODE, storeKey(), new GCMParameterSpec(128, all, 0, 12));
            byte[] plain = c.doFinal(all, 12, all.length - 12);
            return Json.obj(Json.parse(new String(plain, StandardCharsets.UTF_8)));
        } catch (Exception e) {
            return null;   // לא נפתח (מחשב אחר / קובץ פגום) — כאילו אין
        }
    }

    static boolean save(File f, Map<String, Object> data) {
        try {
            byte[] iv = new byte[12];
            RANDOM.nextBytes(iv);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.ENCRYPT_MODE, storeKey(), new GCMParameterSpec(128, iv));
            byte[] ct = c.doFinal(Json.write(data).getBytes(StandardCharsets.UTF_8));
            byte[] all = new byte[12 + ct.length];
            System.arraycopy(iv, 0, all, 0, 12);
            System.arraycopy(ct, 0, all, 12, ct.length);
            Map<String, Object> outer = new LinkedHashMap<String, Object>();
            outer.put("v", 1.0);
            outer.put("data", Base64.getEncoder().encodeToString(all));
            f.getParentFile().mkdirs();
            File tmp = new File(f.getParentFile(), f.getName() + ".tmp");
            Files.write(tmp.toPath(), Json.write(outer).getBytes(StandardCharsets.UTF_8));
            Files.move(tmp.toPath(), f.toPath(), StandardCopyOption.REPLACE_EXISTING);
            f.setReadable(true, false);   // עמדה: כל המשתמשים של המחשב קוראים
            return true;
        } catch (Exception e) {
            lastError = String.valueOf(e);
            return false;
        }
    }

    private static SecretKeySpec storeKey() {
        machine();
        byte[] k = sha256("dsviz-store|" + machineRaw);
        return new SecretKeySpec(k, 0, 16, "AES");
    }

    // ------------------------------------------------------------------ האישור החתום

    /** בודק אישור: חתימה של השרת, אותו מחשב, לא פג. מחזיר את התוכן או null */
    static Map<String, Object> verifyPass(String pass) {
        try {
            if (pass == null || pass.isEmpty()) return null;
            int dot = pass.indexOf('.');
            if (dot < 0) return null;
            String text = pass.substring(0, dot);
            byte[] sig = Base64.getUrlDecoder().decode(pass.substring(dot + 1));
            String key = passKey();
            if (key.isEmpty()) return null;
            PublicKey pub = KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(key)));
            Signature s = Signature.getInstance("SHA256withRSA");
            s.initVerify(pub);
            s.update(text.getBytes(StandardCharsets.US_ASCII));
            if (!s.verify(sig)) return null;
            Map<String, Object> p = Json.obj(Json.parse(new String(Base64.getUrlDecoder().decode(text), StandardCharsets.UTF_8)));
            if (!machine().equals(Json.str(p.get("mid")))) return null;
            Object exp = p.get("exp");
            if (!(exp instanceof Number) || ((Number) exp).longValue() * 1000L < System.currentTimeMillis()) return null;
            return p;
        } catch (Exception e) {
            return null;
        }
    }

    private static Access accessOf(Map<String, Object> p) {
        return new Access(Json.str(p.get("kind")), Json.str(p.get("name")), Json.str(p.get("email")), Json.str(p.get("until")), Json.str(p.get("teacher")));
    }

    /** מי מחובר עכשיו במחשב הזה — בלי אינטרנט. מורה / תלמיד (של המשתמש הזה) קודם, ואחר כך עמדה (של המחשב) */
    public static Access current() {
        Map<String, Object> u = load(new File(userDir(), USER_FILE));
        Map<String, Object> p = u == null ? null : verifyPass(Json.str(u.get("pass")));
        if (p != null) return accessOf(p);
        Map<String, Object> s = load(stationFile());
        p = s == null ? null : verifyPass(Json.str(s.get("pass")));
        return p == null ? null : accessOf(p);
    }

    /** יש כרטיס של משתמש (גם בלי אישור — למשל תלמיד שעוד לא שילם) */
    public static Map<String, Object> userState() {
        return load(new File(userDir(), USER_FILE));
    }

    /** הכותרות לשליחה ל-Gemini: x-dsviz-access (הכרטיס) + x-dsviz-machine. ריק — אם לא מחוברים */
    public static Map<String, String> headers() {
        Map<String, String> h = new LinkedHashMap<String, String>();
        String tok = "";
        Map<String, Object> u = load(new File(userDir(), USER_FILE));
        if (u != null && verifyPass(Json.str(u.get("pass"))) != null) tok = Json.str(u.get("token"));
        if (tok.isEmpty()) {
            Map<String, Object> s = load(stationFile());
            if (s != null) tok = Json.str(s.get("token"));
        }
        if (tok.isEmpty() && u != null) tok = Json.str(u.get("token"));
        if (!tok.isEmpty()) {
            h.put("X-DSViz-Access", tok);
            h.put("X-DSViz-Machine", machine());
        }
        return h;
    }

    // ------------------------------------------------------------------ מול השרת

    static Result post(String path, Map<String, Object> body) {
        String base = serverUrl();
        if (base.isEmpty()) return new Result("error", "כתובת השרת לא מוגדרת.", null);
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(base + path).openConnection();
            c.setConnectTimeout(10000);
            c.setReadTimeout(30000);
            c.setRequestMethod("POST");
            c.setDoOutput(true);
            c.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            c.setRequestProperty("Accept", "application/json");
            c.setRequestProperty("User-Agent", WorkerClient.CLIENT);
            byte[] out = Json.write(body).getBytes(StandardCharsets.UTF_8);
            try (OutputStream o = c.getOutputStream()) { o.write(out); }
            int code = c.getResponseCode();
            InputStream in = code >= 400 ? c.getErrorStream() : c.getInputStream();
            String text = in == null ? "" : readAll(in);
            Map<String, Object> j = text.isEmpty() ? new LinkedHashMap<String, Object>() : Json.obj(Json.parse(text));
            if (code >= 400 || Boolean.FALSE.equals(j.get("ok"))) {
                String msg = Json.str(j.get("error"));
                return new Result(Json.str(j.get("code")).isEmpty() ? "error" : Json.str(j.get("code")), msg.isEmpty() ? "השרת לא ענה כמו שצריך (" + code + ")." : msg, j);
            }
            return new Result(Json.str(j.get("status")).isEmpty() ? "ok" : Json.str(j.get("status")), "", j);
        } catch (IOException e) {
            return new Result("offline", "אין חיבור לשרת. בודקים את האינטרנט ומנסים שוב.", null);
        } catch (RuntimeException e) {
            return new Result("error", "תשובה לא תקינה מהשרת.", null);
        } finally {
            if (c != null) c.disconnect();
        }
    }

    private static String readAll(InputStream in) throws IOException {
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        byte[] b = new byte[8192];
        int n;
        while ((n = in.read(b)) > 0) o.write(b, 0, n);
        in.close();
        return new String(o.toByteArray(), StandardCharsets.UTF_8);
    }

    private static Map<String, Object> map(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        for (int i = 0; i + 1 < kv.length; i += 2) m.put((String) kv[i], kv[i + 1]);
        return m;
    }

    /** שומר את מה שהשרת החזיר (כרטיס + אישור) — אצל המשתמש או לכל המחשב */
    private static void store(File f, Result r, String token) {
        Map<String, Object> d = new LinkedHashMap<String, Object>();
        d.put("token", token);
        d.put("pass", Json.str(r.body.get("pass")));
        d.put("status", r.status);
        Map<String, Object> prof = Json.obj(r.body.get("profile"));
        d.put("email", Json.str(prof.get("email")));
        d.put("name", Json.str(prof.get("name")));
        d.put("role", Json.str(prof.get("role")));
        d.put("checkedAt", (double) System.currentTimeMillis());
        save(f, d);
    }

    // ------------------------------------------------------------------ עמדת מחשב

    /** חיבור עמדה עם קוד המורה. ok — נשמר לכל המחשב; full / bad-code / blocked / offline — הודעה */
    public static Result claimStation(String code, String name) {
        Result r = post("/v1/station/claim", map("code", code, "name", name, "machine", machine(), "client", WorkerClient.CLIENT));
        if (r.ok()) {
            String token = Json.str(r.body.get("token"));
            File f = new File(machineDir(), STATION_FILE);
            store(f, r, token);
            if (!f.isFile()) store(new File(userDir(), STATION_FILE), r, token);   // אין הרשאה לתיקייה של המחשב
        }
        return r;
    }

    // ------------------------------------------------------------------ מורה / תלמיד: כניסה עם Google בדפדפן

    /** מה רואים בזמן ההמתנה לדפדפן */
    public interface Listener {
        void waiting(String url);
    }

    /** ביטול כניסה שמחכה לדפדפן */
    public static final class Cancel {
        volatile boolean cancelled;
        volatile CountDownLatch latch;

        public void cancel() {
            cancelled = true;
            CountDownLatch l = latch;
            if (l != null) l.countDown();
        }
    }

    /**
     * כניסה עם Google: פותח את הדפדפן ב-/signin, מחכה (עד timeoutMs) שהדפדפן יחזור ל-127.0.0.1, מממש את הקוד ושומר.
     * חוסם — קוראים מ-thread ברקע. role: "teacher" או "student".
     */
    public static Result signIn(String role, Listener listener, Cancel cancel, long timeoutMs) {
        final String base = serverUrl();
        if (base.isEmpty()) return new Result("error", "כתובת השרת לא מוגדרת.", null);
        final String verifier = b64url(randomBytes(32));
        final String challenge = b64url(sha256Bytes(verifier.getBytes(StandardCharsets.US_ASCII)));
        final String state = b64url(randomBytes(16));
        final AtomicReference<Result> result = new AtomicReference<Result>();
        final CountDownLatch done = new CountDownLatch(1);
        if (cancel != null) cancel.latch = done;
        HttpServer server;
        try {
            server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        } catch (IOException e) {
            return new Result("error", "לא הצלחתי לפתוח את החיבור לדפדפן במחשב הזה.", null);
        }
        server.createContext("/done", ex -> {
            String location;
            try {
                Map<String, String> q = query(ex.getRequestURI().getRawQuery());
                if (!state.equals(q.get("state")) || q.get("code") == null) {
                    ex.sendResponseHeaders(400, -1);
                    return;
                }
                Result r = post("/v1/auth/redeem", map("code", q.get("code"), "verifier", verifier, "machine", machine(),
                        "machineName", machineName(), "client", WorkerClient.CLIENT));
                String token = Json.str(r.body.get("token"));
                if (!token.isEmpty()) store(new File(userDir(), USER_FILE), r, token);
                result.set(r);
                location = base + "/welcome?r=" + enc(r.status.matches("ok|pay|not-teacher|blocked|devices") ? r.status : "error")
                        + "&role=" + enc(role) + (r.status.matches("ok|pay|not-teacher|blocked|devices") ? "" : "&m=" + enc(r.message));
            } catch (RuntimeException e) {
                result.set(new Result("error", "הכניסה לא הצליחה.", null));
                location = base + "/welcome?r=error";
            }
            ex.getResponseHeaders().add("Location", location);
            ex.getResponseHeaders().add("Cache-Control", "no-store");
            ex.sendResponseHeaders(302, -1);
            ex.close();
            done.countDown();
        });
        server.start();
        try {
            String url = base + "/signin?role=" + enc(role) + "&port=" + server.getAddress().getPort() + "&state=" + enc(state) + "&challenge=" + enc(challenge);
            if (listener != null) listener.waiting(url);
            browser.accept(url);
            boolean got = done.await(timeoutMs, TimeUnit.MILLISECONDS);
            if (cancel != null && cancel.cancelled && result.get() == null) return new Result("cancelled", "", null);
            if (!got || result.get() == null) return new Result("cancelled", "לא התקבלה כניסה מהדפדפן. לוחצים שוב על \"התחברות\".", null);
            return result.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new Result("cancelled", "", null);
        } finally {
            server.stop(1);
        }
    }

    // ------------------------------------------------------------------ בדיקה כשיש אינטרנט, התנתקות

    /**
     * בודק מול השרת את הכרטיסים ששמורים (משתמש + עמדה) ומעדכן: אישור חדש, או מחיקה אם בוטל.
     * בלי אינטרנט — לא משנה כלום (המאגר ממשיך להיפתח עם האישור השמור). מחזיר את המצב של המשתמש (או של העמדה).
     */
    public static Result refresh() {
        Result last = null;
        File uf = new File(userDir(), USER_FILE);
        Map<String, Object> u = load(uf);
        if (u != null && !Json.str(u.get("token")).isEmpty()) {
            Result r = post("/v1/access/check", map("token", Json.str(u.get("token")), "machine", machine()));
            if (!"offline".equals(r.status) && !"error".equals(r.status)) {
                if (r.status.matches("revoked|invalid|not-teacher")) uf.delete();
                else store(uf, r, Json.str(u.get("token")));
            }
            last = r;
        }
        File sf = stationFile();
        Map<String, Object> s = load(sf);
        if (s != null && !Json.str(s.get("token")).isEmpty()) {
            Result r = post("/v1/access/check", map("token", Json.str(s.get("token")), "machine", machine()));
            if (!"offline".equals(r.status) && !"error".equals(r.status)) {
                if (r.status.matches("revoked|invalid")) sf.delete();
                else store(sf, r, Json.str(s.get("token")));
            }
            if (last == null || !last.ok()) last = r;
        }
        return last == null ? new Result("none", "", null) : last;
    }

    /** התנתקות של המשתמש (מורה / תלמיד). עמדה לא מתנתקת מכאן — רק ההנהלה משחררת */
    public static void signOut() {
        File uf = new File(userDir(), USER_FILE);
        Map<String, Object> u = load(uf);
        if (u != null) {
            final String tok = Json.str(u.get("token"));
            Thread t = new Thread(() -> post("/v1/access/logout", map("token", tok)), "dsviz-logout");
            t.setDaemon(true);
            t.start();
        }
        uf.delete();
    }

    // ------------------------------------------------------------------ עזרים

    static void openBrowser(String url) {
        try {
            if (java.awt.Desktop.isDesktopSupported() && java.awt.Desktop.getDesktop().isSupported(java.awt.Desktop.Action.BROWSE)) {
                java.awt.Desktop.getDesktop().browse(new URI(url));
                return;
            }
        } catch (Exception ignored) {
            // ננסה דרך מערכת ההפעלה
        }
        String os = System.getProperty("os.name", "").toLowerCase();
        try {
            if (os.contains("win")) new ProcessBuilder("rundll32", "url.dll,FileProtocolHandler", url).start();
            else if (os.contains("mac")) new ProcessBuilder("open", url).start();
            else new ProcessBuilder("xdg-open", url).start();
        } catch (IOException ignored) {
            // החלון מציג את הכתובת להעתקה
        }
    }

    private static Map<String, String> query(String raw) {
        Map<String, String> m = new LinkedHashMap<String, String>();
        if (raw == null) return m;
        for (String part : raw.split("&")) {
            int i = part.indexOf('=');
            if (i <= 0) continue;
            try {
                m.put(URLDecoder.decode(part.substring(0, i), "UTF-8"), URLDecoder.decode(part.substring(i + 1), "UTF-8"));
            } catch (java.io.UnsupportedEncodingException e) {
                throw new IllegalStateException(e);
            }
        }
        return m;
    }

    static String enc(String s) {
        try { return URLEncoder.encode(s, "UTF-8"); } catch (java.io.UnsupportedEncodingException e) { throw new IllegalStateException(e); }
    }

    private static byte[] randomBytes(int n) {
        byte[] b = new byte[n];
        RANDOM.nextBytes(b);
        return b;
    }

    private static String b64url(byte[] b) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }

    private static byte[] sha256(String s) {
        return sha256Bytes(s.getBytes(StandardCharsets.UTF_8));
    }

    private static byte[] sha256Bytes(byte[] b) {
        try { return MessageDigest.getInstance("SHA-256").digest(b); } catch (Exception e) { throw new IllegalStateException(e); }
    }

    private static String hex(byte[] b) {
        StringBuilder s = new StringBuilder();
        for (byte x : b) s.append(String.format("%02x", x & 0xff));
        return s.toString();
    }

    static String dmy(String iso) {
        String[] p = iso.split("-");
        return p.length == 3 ? Integer.parseInt(p[2]) + "." + Integer.parseInt(p[1]) + "." + p[0] : iso;
    }

    /** רשימת המחשבים שהשרת החזיר כשהגענו למגבלה ("devices") */
    public static List<String> devices(Result r) {
        List<String> out = new ArrayList<String>();
        for (Object o : Json.arr(r.body.get("devices"))) out.add(Json.str(Json.obj(o).get("name")));
        return out;
    }

    /** לבדיקות בלבד: לשכוח את מזהה המחשב שנשמר בזיכרון */
    static void resetForTest() {
        machineRaw = null;
        machineHash = null;
    }

    static String lastError() {
        return lastError;
    }
}
