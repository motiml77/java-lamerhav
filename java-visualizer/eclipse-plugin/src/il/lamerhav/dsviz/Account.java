package il.lamerhav.dsviz;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
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

    // ------------------------------------------------------------------ מקום שמור ונסתר לקבצי הזיהוי

    /*
     * הקבצים שמזהים את המחשב (מורה / עמדת מחשב / המזהה של ההתקנה) נשמרים בתיקיות נסתרות שלא נמחקות בכיבוי המחשב,
     * בהסרה או בהתקנה מחדש של DSViz, ובשני מקומות (עותק גיבוי): אם אחד נמחק — משחזרים מהשני.
     *   Windows: ‎%LOCALAPPDATA%\Lamerhav\.cache‎ (המשתמש), ‎%APPDATA%\Lamerhav\.cache‎ (גיבוי), ‎%ProgramData%\Lamerhav\.cache‎ (כל המחשב)
     *   Mac / Linux: ‎~/.local/share/.lamerhav‎, ‎~/.config/.lamerhav‎, ‎/Users/Shared/.lamerhav‎ (Mac)
     * הקבצים מוצפנים (לפי המחשב), עם שמות קצרים, ומסומנים כנסתרים ב-Windows. קבצים מהמקום הישן (‎.dsviz‎, ‎ProgramData\DSViz‎)
     * עוברים לכאן לבד בפעם הראשונה.
     */

    /** בדיקות (-Ddsviz.home / -Ddsviz.machineDir): הסידור הישן, בלי העברה ובלי גיבוי */
    private static boolean plainDirs() {
        String a = System.getProperty("dsviz.home"), b = System.getProperty("dsviz.machineDir");
        return a != null && !a.isEmpty() || b != null && !b.isEmpty();
    }

    private static boolean windows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }

    private static File envDir(String var, String fallback) {
        String v = System.getenv(var);
        return new File(v == null || v.isEmpty() ? fallback : v);
    }

    /** המקום הראשי של המשתמש */
    static File secureUserDir() {
        if (plainDirs()) return userDir();
        String home = System.getProperty("user.home");
        if (windows()) return new File(new File(envDir("LOCALAPPDATA", home + "\\AppData\\Local"), "Lamerhav"), ".cache");
        return new File(home, ".local/share/.lamerhav");
    }

    /** הגיבוי (מקום אחר לגמרי) */
    static File secureBackupDir() {
        if (plainDirs()) return null;
        String home = System.getProperty("user.home");
        if (windows()) return new File(new File(envDir("APPDATA", home + "\\AppData\\Roaming"), "Lamerhav"), ".cache");
        return new File(home, ".config/.lamerhav");
    }

    /** המקום של כל המחשב (עמדת מחשב) */
    static File secureMachineDir() {
        if (plainDirs()) return machineDir();
        String os = System.getProperty("os.name", "").toLowerCase();
        if (windows()) return new File(new File(envDir("ProgramData", "C:\\ProgramData"), "Lamerhav"), ".cache");
        if (os.contains("mac")) return new File("/Users/Shared/.lamerhav");
        return secureUserDir();   // Linux: ‎/var/tmp‎ נמחק — נשארים אצל המשתמש
    }

    /** הגיבוי של קובץ (אותו שם, במקום הגיבוי), או null */
    private static File backupOf(File f) {
        File b = secureBackupDir();
        if (b == null || f == null) return null;
        File g = new File(b, f.getName());
        return g.getAbsoluteFile().equals(f.getAbsoluteFile()) ? null : g;
    }

    /** מסמן כנסתר ב-Windows (הקובץ והתיקייה) */
    private static void hide(File f) {
        if (!windows() || plainDirs()) return;
        for (File x : new File[] { f, f.getParentFile() }) {
            try {
                if (x != null && x.exists()) Files.setAttribute(x.toPath(), "dos:hidden", Boolean.TRUE);
            } catch (Exception ignored) {
                // לא חובה
            }
        }
    }

    /** כותב קובץ קטן (לא מוצפן — למזהה) במקום הראשי ובגיבוי, ומסמן כנסתר */
    private static boolean writeBoth(File f, byte[] data) {
        boolean ok = false;
        for (File x : new File[] { f, backupOf(f) }) {
            if (x == null) continue;
            try {
                x.getParentFile().mkdirs();
                Files.write(x.toPath(), data);
                hide(x);
                ok = true;
            } catch (IOException ignored) {
                // נסה את הבא
            }
        }
        return ok;
    }

    /** מוחק קובץ זיהוי (ההנהלה ביטלה / התנתקות) — גם את הגיבוי */
    private static void forget(File f) {
        if (f == null) return;
        f.delete();
        File b = backupOf(f);
        if (b != null) b.delete();
        syncTeacherFlag();
    }

    /** מעביר קובץ מהמקום הישן לחדש (אם בחדש אין) ומוחק את הישן */
    private static void migrate(File legacy, File now) {
        try {
            if (legacy == null || now == null || !legacy.isFile() || legacy.getAbsoluteFile().equals(now.getAbsoluteFile())) return;
            if (!now.isFile()) {
                byte[] data = Files.readAllBytes(legacy.toPath());
                if (!writeBoth(now, data)) return;
            }
            legacy.delete();
        } catch (Exception ignored) {
            // נשאר במקום הישן — עדיין נקרא משם
        }
    }

    // ------------------------------------------------------------------ מזהה המחשב

    /** מזהה קבוע של המחשב (hash) — אותו מזהה לכל המשתמשים ולכל התוכנות (Eclipse / IntelliJ / Visual Studio) */
    public static String machine() {
        if (machineHash == null) {
            String raw = System.getProperty("dsviz.machine");
            if (raw == null || raw.isEmpty()) raw = osMachineId();
            // במעבדות מחשבים משוכפלים מתמונה אחת (אותו MachineGuid בכולם) — בלי תוספת, כל המחשבים נחשבו עמדה אחת ודרסו זה את זה.
            // לכן מוסיפים מזהה אקראי שנוצר פעם אחת בכל מחשב (ונוצר מחדש אם שם המחשב שונה — העתק של תמונה עם המזהה)
            raw = raw + "|" + installId();
            machineRaw = raw;
            machineHash = hex(sha256("dsviz|" + raw)).substring(0, 32);
        }
        return machineHash;
    }

    /**
     * מזהה אקראי של ההתקנה במחשב הזה, בקובץ install-id (שורה 1: המזהה, שורה 2: שם המחשב). אם הקובץ הועתק למחשב אחר (שם המחשב שונה) —
     * יוצרים מזהה חדש. אי אפשר לכתוב קובץ בשום מקום — משתמשים בשם המחשב (עדיין שונה בין מחשבים).
     */
    static String installId() {
        String host = machineName();
        // המקומות החדשים (נסתרים, עם גיבוי) קודם, ואחריהם המקום הישן — ממנו המזהה עובר לחדש (אותו מזהה: אחרת היו
        // אובדים העמדה והקבצים המוצפנים)
        File mPrimary = new File(secureMachineDir(), "i.dat"), uPrimary = new File(secureUserDir(), "i.dat");
        File[] reads = { mPrimary, uPrimary, backupOf(uPrimary), new File(machineDir(), "install-id"), new File(userDir(), "install-id") };
        for (File f : reads) {
            if (f == null) continue;
            try {
                if (!f.isFile()) continue;
                String[] lines = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8).split("\\r?\\n");
                if (lines.length >= 2 && lines[0].trim().length() >= 16 && lines[1].trim().equals(host)) {
                    String id = lines[0].trim();
                    keepInstallId(id, host, mPrimary, uPrimary);
                    return id;
                }
            } catch (IOException ignored) {
                // נבדוק את הבא
            }
        }
        String id = UUID.randomUUID().toString().replace("-", "");
        if (keepInstallId(id, host, mPrimary, uPrimary)) return id;
        return "host-" + host;
    }

    /** שומר את המזהה בכל המקומות החדשים (שחסר בהם) ומוחק את העותקים מהמקום הישן */
    private static boolean keepInstallId(String id, String host, File mPrimary, File uPrimary) {
        byte[] data = (id + "\n" + host + "\n").getBytes(StandardCharsets.UTF_8);
        boolean ok = false;
        for (File f : new File[] { mPrimary, uPrimary }) {
            File b = backupOf(f);
            if (f.isFile() && (b == null || b.isFile())) { ok = true; continue; }
            if (writeBoth(f, data)) ok = true;
        }
        if (ok && !plainDirs()) {
            new File(machineDir(), "install-id").delete();
            new File(userDir(), "install-id").delete();
        }
        return ok;
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
        String n = System.getProperty("dsviz.hostname");   // לבדיקות
        if (n == null || n.isEmpty()) n = System.getenv("COMPUTERNAME");
        if (n == null || n.isEmpty()) {
            try { n = InetAddress.getLocalHost().getHostName(); } catch (Exception e) { n = ""; }
        }
        return n == null || n.isEmpty() ? "מחשב" : n;
    }

    // ------------------------------------------------------------------ שמירה מוצפנת

    private static final String USER_FILE = "account.json", STATION_FILE = "station.json";
    private static final String USER_DAT = "a.dat", STATION_DAT = "s.dat";

    /** הקובץ של המשתמש המחובר (מורה / תלמיד) — במקום הנסתר; מהמקום הישן עובר לבד */
    static File userFile() {
        if (plainDirs()) return new File(userDir(), USER_FILE);
        File f = new File(secureUserDir(), USER_DAT);
        migrate(new File(userDir(), USER_FILE), f);
        return f;
    }

    /** הקובץ של עמדת המחשב — לכל המחשב (ואם אין הרשאה — אצל המשתמש) */
    private static File stationFile() {
        if (plainDirs()) {
            File f = plainDirs() ? new File(machineDir(), STATION_FILE) : new File(secureMachineDir(), STATION_DAT);
            if (f.isFile()) return f;
            File u = new File(userDir(), STATION_FILE);
            return u.isFile() ? u : f;
        }
        File m = new File(secureMachineDir(), STATION_DAT), u = new File(secureUserDir(), STATION_DAT);
        migrate(new File(machineDir(), STATION_FILE), m);
        migrate(new File(userDir(), STATION_FILE), m.isFile() ? u : m);
        if (m.isFile()) return m;
        File bm = backupOf(m);
        if (u.isFile() || bm != null && bm.isFile()) return u.isFile() ? u : m;
        return m;
    }

    /** מקום חלופי לעמדה כשאין הרשאה לכתוב לכל המחשב */
    private static File stationFallback() {
        return plainDirs() ? new File(userDir(), STATION_FILE) : new File(secureUserDir(), STATION_DAT);
    }

    static Map<String, Object> load(File f) {
        try {
            if (!f.isFile()) {
                // נמחק? משחזרים מהגיבוי
                File b = backupOf(f);
                if (b == null || !b.isFile()) return null;
                try {
                    f.getParentFile().mkdirs();
                    Files.copy(b.toPath(), f.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    hide(f);
                } catch (IOException e) {
                    f = b;   // אין הרשאה לשחזר — קוראים מהגיבוי
                }
            }
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
            boolean shared = isStationFile(f);
            if (!shared) ownerOnly(tmp);   // הכרטיס של המורה / התלמיד — רק המשתמש עצמו קורא (Mac / Linux: בלי זה הקובץ ‎0644‎)
            Files.move(tmp.toPath(), f.toPath(), StandardCopyOption.REPLACE_EXISTING);
            if (shared) f.setReadable(true, false);   // עמדה: כל המשתמשים של המחשב קוראים
            hide(f);
            File b = backupOf(f);
            if (b != null) {
                try {
                    b.getParentFile().mkdirs();
                    Files.copy(f.toPath(), b.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    if (!shared) ownerOnly(b);
                    hide(b);
                } catch (IOException ignored) {
                    // בלי גיבוי — הקובץ הראשי נשמר
                }
            }
            return true;
        } catch (Exception e) {
            lastError = String.valueOf(e);
            return false;
        }
    }

    private static boolean isStationFile(File f) {
        return STATION_FILE.equals(f.getName()) || STATION_DAT.equals(f.getName());
    }

    /** Mac / Linux: הרשאות ‎0600‎ (רק הבעלים). ב-Windows התיקייה ‎%LOCALAPPDATA%‎ כבר של המשתמש בלבד */
    static void ownerOnly(File f) {
        try {
            if (Files.getFileStore(f.toPath()).supportsFileAttributeView(java.nio.file.attribute.PosixFileAttributeView.class))
                Files.setPosixFilePermissions(f.toPath(), java.nio.file.attribute.PosixFilePermissions.fromString("rw-------"));
        } catch (Exception ignored) {
            // מערכת קבצים בלי הרשאות POSIX
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
        Map<String, Object> u = load(userFile());
        Map<String, Object> p = u == null ? null : verifyPass(Json.str(u.get("pass")));
        if (p != null) return accessOf(p);
        Map<String, Object> s = load(stationFile());
        p = s == null ? null : verifyPass(Json.str(s.get("pass")));
        return p == null ? null : accessOf(p);
    }

    /** יש כרטיס של משתמש (גם בלי אישור — למשל תלמיד שעוד לא שילם) */
    public static Map<String, Object> userState() {
        return load(userFile());
    }

    /** הכותרות לשליחה ל-Gemini: x-dsviz-access (הכרטיס) + x-dsviz-machine. ריק — אם לא מחוברים */
    public static Map<String, String> headers() {
        Map<String, String> h = new LinkedHashMap<String, String>();
        String tok = "";
        Map<String, Object> u = load(userFile());
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
            // כל תשובה של השרת שלנו היא JSON עם ok:true. תשובה ריקה / בלי ok (סינון אינטרנט בבית הספר, פורטל של רשת אורחים,
            // proxy) — לא תשובה של השרת: אסור להתייחס אליה כ"מאושר" (ככה נמחק האישור השמור והמשתמש ננעל בחוץ)
            if (!Boolean.TRUE.equals(j.get("ok"))) return new Result("error", "תשובה לא תקינה מהשרת.", null);
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
        // מורה: הקוד של המורה (4 תווים) והמקומות — נשמרים כדי שאפשר יהיה לראות אותם בכל פעם מתוך התוכנה, גם בלי אינטרנט
        Map<String, Object> t = Json.obj(r.body.get("teacher"));
        if (!Json.str(t.get("code")).isEmpty()) {
            d.put("teacherCode", Json.str(t.get("code")));
            d.put("teacherSeats", t.get("seats"));
            d.put("teacherUsed", t.get("used"));
            d.put("teacherName", Json.str(t.get("name")));   // השם שההנהלה הגדירה (בחשבון Google לפעמים אין שם)
        }
        save(f, d);
        syncTeacherFlag();
    }

    /** הקוד של המורה המחובר (כמו שנשמר בכניסה / בבדיקה האחרונה מול השרת), או "" */
    public static String teacherCode() {
        Map<String, Object> t = teacherInfo();
        return t == null ? "" : Json.str(t.get("code"));
    }

    /** כתובת עמדת הניהול של המורה (קוד, עמדות, תשלום) — בדפדפן */
    public static String managementUrl() {
        String base = serverUrl();
        return base.isEmpty() ? "" : base + "/account";
    }

    /** פותח את עמדת הניהול של המורה בדפדפן */
    public static void openManagement() {
        String u = managementUrl();
        if (!u.isEmpty()) browser.accept(u);
    }

    /** אחרי כניסה מוצלחת: מה שנשמר באמת נפתח במחשב הזה? אחרת — "נכנסת" היה מופיע, והמאגר שוב היה מבקש כניסה בכל פתיחה */
    private static Result checkSaved(Result r, File f) {
        if (!r.ok()) return r;
        Map<String, Object> d = load(f);
        if (d == null) return new Result("storage", "הכניסה הצליחה, אבל לא הצלחתי לשמור אותה במחשב הזה (אין הרשאה לכתוב בתיקיית המשתמש"
                + (lastError.isEmpty() ? "" : ": " + lastError) + "). פונים לרכז המחשבים.", r.body);
        if (verifyPass(Json.str(d.get("pass"))) == null) return new Result("pass", "הכניסה הצליחה, אבל המחשב לא הצליח לאמת את האישור מהשרת."
                + " בודקים שהתאריך והשעה במחשב נכונים, ושגרסת DSViz מעודכנת — ומנסים שוב.", r.body);
        return r;
    }

    // ------------------------------------------------------------------ עמדת מחשב

    /** חיבור עמדה עם קוד המורה. ok — נשמר לכל המחשב; full / bad-code / blocked / offline — הודעה */
    public static Result claimStation(String code, String name) {
        Result r = post("/v1/station/claim", map("code", code, "name", name, "machine", machine(), "client", WorkerClient.CLIENT));
        if (r.ok()) {
            String token = Json.str(r.body.get("token"));
            File f = new File(machineDir(), STATION_FILE);
            store(f, r, token);
            if (!f.isFile()) store(stationFallback(), r, token);   // אין הרשאה לתיקייה של המחשב
            r = checkSaved(r, stationFile());
        }
        return r;
    }

    // ------------------------------------------------------------------ מורה / תלמיד: כניסה עם Google בדפדפן

    /** אין חזרה מהדפדפן בזמן (הדפדפן נסגר / לא סיימו את הכניסה) */
    static final String TIMEOUT_MESSAGE = "לא התקבלה כניסה מהדפדפן (אולי הדפדפן נסגר לפני שהכניסה הסתיימה). לוחצים \"חזרה\" ושוב \"כניסה עם Google\".";

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
        // שרת זעיר על 127.0.0.1 שמחכה לחזרה מהדפדפן (/done). ServerSocket ולא ה-HttpServer של ה-JDK: ה-Java שמגיע עם ההתקנה
        // (jlink — ממנו רצים הכלים של Visual Studio) לא כולל את jdk.httpserver, ושם הכניסה נכשלה מיד (NoClassDefFoundError)
        final ServerSocket server;
        try {
            // דווקא 127.0.0.1 (הדפדפן חוזר לכתובת הזו): getLoopbackAddress מחזיר ‎::1‎ כש-Java מעדיפה IPv6
            // (‎-Djava.net.preferIPv6Addresses=true‎ ב-eclipse.ini / ב-IDE) — ואז הדפדפן לא מגיע לתוכנה והכניסה לא מסתיימת
            server = new ServerSocket(0, 50, LOOPBACK);
        } catch (IOException e) {
            return new Result("error", "לא הצלחתי לפתוח את החיבור לדפדפן במחשב הזה.", null);
        }
        final Object once = new Object();
        final java.util.concurrent.atomic.AtomicBoolean over = new java.util.concurrent.atomic.AtomicBoolean();
        final AtomicReference<String> back = new AtomicReference<String>();   // לאן הדפדפן חוזר אחרי הכניסה
        final Done handler = target -> {
            Map<String, String> q = query(target.indexOf('?') < 0 ? null : target.substring(target.indexOf('?') + 1));
            if (!state.equals(q.get("state")) || q.get("code") == null) return null;
            synchronized (once) {   // הדפדפן שולח שוב (רענון) — הקוד כבר מומש: חוזרים לאותו מקום
                if (back.get() != null) return back.get();
                if (over.get() || cancel != null && cancel.cancelled) return null;   // בוטל / נגמר הזמן — לא מממשים (אחרת נכנסים "בשקט" אחרי ביטול)
                String location;
                try {
                    Result r = post("/v1/auth/redeem", map("code", q.get("code"), "verifier", verifier, "machine", machine(),
                            "machineName", machineName(), "client", WorkerClient.CLIENT));
                    String token = Json.str(r.body.get("token"));
                    if (!token.isEmpty()) store(userFile(), r, token);
                    r = checkSaved(r, userFile());
                    result.set(r);
                    location = base + "/welcome?r=" + enc(r.status.matches("ok|pay|not-teacher|blocked|devices") ? r.status : "error")
                            + "&role=" + enc(role) + (r.status.matches("ok|pay|not-teacher|blocked|devices") ? "" : "&m=" + enc(r.message));
                } catch (RuntimeException e) {
                    result.set(new Result("error", "הכניסה לא הצליחה.", null));
                    location = base + "/welcome?r=error";
                }
                back.set(location);
                return location;
            }
        };
        Thread acceptor = new Thread(() -> {
            while (!server.isClosed()) {
                final Socket s;
                try {
                    s = server.accept();
                } catch (IOException e) {
                    return;   // נסגר בסוף הכניסה
                }
                // חיבור לכל בקשה — דפדפנים פותחים לפעמים חיבור מראש בלי לשלוח בו כלום, והוא לא יעכב את החזרה האמיתית
                Thread t = new Thread(() -> serveLoopback(s, handler, done), "dsviz-signin-request");
                t.setDaemon(true);
                t.start();
            }
        }, "dsviz-signin");
        acceptor.setDaemon(true);
        acceptor.start();
        try {
            String url = base + "/signin?role=" + enc(role) + "&port=" + server.getLocalPort() + "&state=" + enc(state) + "&challenge=" + enc(challenge);
            if (listener != null) listener.waiting(url);
            try {
                browser.accept(url);
            } catch (RuntimeException e) {
                // הדפדפן לא נפתח — ממשיכים לחכות: בחלון יש קישור להעתקה
            }
            boolean got = done.await(timeoutMs, TimeUnit.MILLISECONDS);
            synchronized (once) {   // מימוש שכבר התחיל — מחכים שיסתיים (לא מחזירים "בוטל" כשבעצם נכנסנו); אחרי זה לא מתחיל אחר
                over.set(true);
                if (result.get() == null) return new Result("cancelled", got ? "" : TIMEOUT_MESSAGE, null);   // got בלי תוצאה = ביטול
            }
            return result.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new Result("cancelled", "", null);
        } finally {
            over.set(true);
            try { server.close(); } catch (IOException ignored) { }
        }
    }

    static final InetAddress LOOPBACK;
    static {
        InetAddress a;
        try { a = InetAddress.getByAddress("127.0.0.1", new byte[] { 127, 0, 0, 1 }); } catch (IOException e) { a = InetAddress.getLoopbackAddress(); }
        LOOPBACK = a;
    }

    /** מה עושים עם החזרה מהדפדפן: target = הנתיב עם ה-query. מחזיר לאן להפנות את הדפדפן, או null — בקשה לא נכונה (400) */
    interface Done {
        String location(String target);
    }

    /**
     * בקשת HTTP אחת על החיבור המקומי: GET /done?... — מממשים ומפנים את הדפדפן (302) לדף התוצאה, ואז משחררים את signIn.
     * כל נתיב אחר (למשל ‎/favicon.ico‎) — 404.
     */
    static void serveLoopback(Socket s, Done handler, CountDownLatch done) {
        boolean finished = false;
        try (Socket sock = s) {
            sock.setSoTimeout(15000);
            InputStream in = sock.getInputStream();
            String line = readLine(in);
            if (line == null) return;
            for (String h = readLine(in); h != null && !h.isEmpty(); h = readLine(in)) {
                // הכותרות לא חשובות — רק קוראים אותן עד השורה הריקה
            }
            String[] parts = line.split(" ");
            String target = parts.length >= 2 ? parts[1] : "";
            String path = target.indexOf('?') < 0 ? target : target.substring(0, target.indexOf('?'));
            String head;
            if (!"GET".equals(parts[0]) || !"/done".equals(path)) head = "HTTP/1.1 404 Not Found\r\n";
            else {
                String location = handler.location(target);
                if (location == null) head = "HTTP/1.1 400 Bad Request\r\n";
                else {
                    head = "HTTP/1.1 302 Found\r\nLocation: " + location + "\r\n";
                    finished = true;
                }
            }
            OutputStream out = sock.getOutputStream();
            out.write((head + "Cache-Control: no-store\r\nContent-Length: 0\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
            out.flush();
        } catch (IOException | RuntimeException ignored) {
            // חיבור שנסגר באמצע — אם הקוד כבר מומש, signIn משתחרר בכל זאת
        } finally {
            if (finished) done.countDown();
        }
    }

    /** שורה אחת (עד CRLF) מבקשת HTTP — ASCII, עד 8KB. null בסוף החיבור */
    private static String readLine(InputStream in) throws IOException {
        StringBuilder b = new StringBuilder();
        for (int c = in.read(); ; c = in.read()) {
            if (c < 0) return b.length() == 0 ? null : b.toString();
            if (c == '\n') break;
            if (c != '\r') b.append((char) c);
            if (b.length() > 8192) throw new IOException("line too long");
        }
        return b.toString();
    }

    // ------------------------------------------------------------------ בדיקה כשיש אינטרנט, התנתקות

    /** המצבים שהשרת מחזיר על כרטיס תקף (/v1/access/check) — רק אז מעדכנים את מה ששמור (עם אישור חדש, או בלי — לא שילם / הושבת) */
    private static final String ACCESS_STATES = "ok|pay|blocked";

    /** תשובה אמיתית על הכרטיס: מצב מוכר, ו-"ok" רק עם אישור חתום שנפתח במחשב הזה (אחרת היינו שומרים "מאושר" בלי אישור) */
    private static boolean validState(Result r) {
        return r.status.matches(ACCESS_STATES) && (!r.ok() || verifyPass(Json.str(r.body.get("pass"))) != null);
    }

    /**
     * בודק מול השרת את הכרטיסים ששמורים (משתמש + עמדה) ומעדכן: אישור חדש, או מחיקה אם בוטל.
     * בלי אינטרנט — לא משנה כלום (המאגר ממשיך להיפתח עם האישור השמור). מחזיר את המצב של המשתמש (או של העמדה).
     */
    public static Result refresh() {
        flushPendingLogouts();
        Result last = null;
        File uf = userFile();
        Map<String, Object> u = load(uf);
        if (u != null && !Json.str(u.get("token")).isEmpty()) {
            Result r = post("/v1/access/check", map("token", Json.str(u.get("token")), "machine", machine()));
            if (r.status.matches("revoked|invalid|not-teacher")) forget(uf);
            else if (validState(r)) store(uf, r, Json.str(u.get("token")));
            // כל דבר אחר (בלי אינטרנט, 429 / 500 / 404, תשובה לא תקינה) — תקלה רגעית: האישור השמור נשאר כמו שהוא
            last = r;
        }
        File sf = stationFile();
        Map<String, Object> s = load(sf);
        if (s != null && !Json.str(s.get("token")).isEmpty()) {
            Result r = post("/v1/access/check", map("token", Json.str(s.get("token")), "machine", machine()));
            if (r.status.matches("revoked|invalid")) forget(sf);
            else if (validState(r)) store(sf, r, Json.str(s.get("token")));
            if (last == null || !last.ok()) last = r;
        }
        return last == null ? new Result("none", "", null) : last;
    }

    /**
     * התנתקות של המשתמש (מורה / תלמיד) — חוסם (קוראים מ-thread ברקע). הכניסה נמחקת מהמחשב מיד; הכרטיס מבוטל בשרת.
     * בלי אינטרנט (או שהשרת לא ענה) — ok=false, status "offline": נמחק מהמחשב, והביטול בשרת נשלח בפעם הבאה שיש אינטרנט
     * (flushPendingLogouts). עמדת מחשב לא מתנתקת מכאן — רק המורה / ההנהלה משחררים אותה.
     */
    public static Result signOut() {
        File uf = userFile();
        Map<String, Object> u = load(uf);
        forget(uf);
        String tok = u == null ? "" : Json.str(u.get("token"));
        if (tok.isEmpty()) return new Result("ok", "", null);
        Result r = post("/v1/access/logout", map("token", tok));
        if (r.ok()) return r;
        pendingLogout(tok, true);
        return new Result("offline", "התנתקת במחשב הזה. אין כרגע חיבור לשרת — החשבון ינותק גם בשרת בפעם הבאה שיש אינטרנט.", null);
    }

    private static final String LOGOUT_FILE = "logout.json", LOGOUT_DAT = "o.dat";

    private static File logoutFile() {
        return plainDirs() ? new File(userDir(), LOGOUT_FILE) : new File(secureUserDir(), LOGOUT_DAT);
    }

    /** הכרטיסים שעוד צריך לבטל בשרת (התנתקות בלי אינטרנט) */
    static List<String> pendingLogouts() {
        List<String> out = new ArrayList<String>();
        Map<String, Object> d = load(logoutFile());
        if (d != null) for (Object o : Json.arr(d.get("tokens"))) if (!Json.str(o).isEmpty()) out.add(Json.str(o));
        return out;
    }

    private static synchronized void pendingLogout(String tok, boolean add) {
        List<String> all = pendingLogouts();
        all.remove(tok);
        if (add) all.add(tok);
        while (all.size() > 20) all.remove(0);
        File f = logoutFile();
        if (all.isEmpty()) { forget(f); return; }
        Map<String, Object> d = new LinkedHashMap<String, Object>();
        d.put("tokens", new ArrayList<Object>(all));
        save(f, d);
    }

    /** שולח לשרת התנתקויות שנעשו בלי אינטרנט (נקרא בבדיקה מול השרת ובפתיחת מסך הכניסה) */
    public static void flushPendingLogouts() {
        for (String tok : pendingLogouts()) {
            Result r = post("/v1/access/logout", map("token", tok));
            if (r.ok()) pendingLogout(tok, false);
            else if ("offline".equals(r.status)) return;   // עדיין בלי אינטרנט — בפעם הבאה
        }
    }

    // ------------------------------------------------------------------ המורה המחובר (לעמדת הניהול)

    /**
     * סימון (לא סוד — רק מייל ושם, אף פעם לא הכרטיס) שהמשתמש של Windows מחובר כמורה: ‎%LOCALAPPDATA%\DSViz\teacher.flag‎.
     * סרגל הכלים של Visual Studio (C#, לא יכול לפתוח את הקבצים המוצפנים) מציג את "עמדת ניהול" רק כשהוא קיים. הסימון הוא רק
     * רמז לממשק — עמדת הניהול עצמה בודקת שוב את האישור החתום ואת השרת. null — אין סרגל של Visual Studio (Mac / Linux)
     */
    static File teacherFlagFile() {
        String d = System.getProperty("dsviz.statusDir");
        if (d != null && !d.isEmpty()) return new File(d, "teacher.flag");
        if (plainDirs()) return new File(userDir(), "teacher.flag");
        if (!windows()) return null;
        String la = System.getenv("LOCALAPPDATA");
        return la == null || la.isEmpty() ? null : new File(new File(la, "DSViz"), "teacher.flag");
    }

    /** מעדכן את הסימון לפי מי שמחובר עכשיו (נקרא אחרי כל שמירה / מחיקה של כניסה, ובפתיחת הכלים) */
    public static void syncTeacherFlag() {
        try {
            File f = teacherFlagFile();
            if (f == null) return;
            Map<String, Object> t = teacherInfo();
            if (t == null) {
                if (f.exists()) f.delete();
                return;
            }
            if (!f.getParentFile().isDirectory()) return;   // DSViz ל-Visual Studio לא מותקן — לא יוצרים תיקייה
            byte[] text = Json.write(map("email", t.get("email"), "name", t.get("name"))).getBytes(StandardCharsets.UTF_8);
            if (f.isFile() && java.util.Arrays.equals(Files.readAllBytes(f.toPath()), text)) return;   // בלי שינוי — הסרגל לא מתעדכן סתם
            Files.write(f.toPath(), text);
        } catch (Exception ignored) {
            // רק רמז לממשק
        }
    }

    /** הפרטים של המורה המחובר, כמו שנשמרו בבדיקה האחרונה מול השרת: name, email, code, seats, used. null — לא מורה מחובר */
    public static Map<String, Object> teacherInfo() {
        Access a = current();
        if (a == null || !a.isTeacher()) return null;
        Map<String, Object> u = userState();
        Map<String, Object> t = new LinkedHashMap<String, Object>();
        String tn = u == null ? "" : Json.str(u.get("teacherName"));
        t.put("name", tn.isEmpty() ? a.name : tn);
        t.put("email", a.email);
        t.put("code", u == null ? "" : Json.str(u.get("teacherCode")));
        t.put("seats", u != null && u.get("teacherSeats") instanceof Number ? ((Number) u.get("teacherSeats")).intValue() : -1);
        t.put("used", u != null && u.get("teacherUsed") instanceof Number ? ((Number) u.get("teacherUsed")).intValue() : -1);
        return t;
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

    /** לבדיקות: איפה נשמרת העמדה ואיפה המשתמש (המקומות הנסתרים, או אלה של dsviz.home / dsviz.machineDir) */
    static String stationFileForTest() {
        return stationFile() + " | " + userFile();
    }
}
