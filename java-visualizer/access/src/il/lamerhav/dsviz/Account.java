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
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
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
import java.util.concurrent.Callable;
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
        /** באילו תוכנות האישור פותח (apps באישור החתום; אישור ישן בלי השדה — בשתיהן) */
        public final List<String> apps;

        Access(String kind, String name, String email, String until, String teacher) {
            this(kind, name, email, until, teacher, null);
        }

        Access(String kind, String name, String email, String until, String teacher, List<String> apps) {
            this.kind = kind; this.name = name; this.email = email; this.until = until; this.teacher = teacher;
            this.apps = java.util.Collections.unmodifiableList(apps == null || apps.isEmpty() ? java.util.Arrays.asList("dsviz", "automata") : apps);
        }

        public boolean opens(String app) { return apps.contains(app); }

        public boolean isTeacher() { return "teacher".equals(kind); }
        public boolean isStation() { return "station".equals(kind); }
        public boolean isStudent() { return "student".equals(kind); }

        /** שורה קצרה לכותרת המאגר */
        public String label() {
            if (isStation()) return "עמדת מחשב: " + name + (teacher.isEmpty() ? "" : " · המורה: " + teacher);
            if (isTeacher()) return "מורה: " + (name.isEmpty() ? email : name);
            // תלמיד משויך: המורה שלו (אליו נשלחת "הגשה למורה"); תלמיד עצמאי: עד מתי המאגר פתוח
            if (!teacher.isEmpty()) return (name.isEmpty() ? email : name) + " · המורה: " + teacher;
            return (name.isEmpty() ? email : name) + (until.isEmpty() ? "" : " · פתוח עד " + dmy(until));
        }
    }

    /** תוצאה של פעולה מול השרת. status: ok / pay / register / not-teacher / blocked / devices / full / bad-code / revoked / invalid / offline / error / cancelled */
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

    /** שם הלקוח לשרת (User-Agent, "client"). WorkerClient מחליף אותו לערך של WorkerClient.CLIENT (dsviz-vs / dsviz-vscode) */
    public static volatile java.util.function.Supplier<String> client = () -> "dsviz-eclipse/1.0.1";

    /** התוכנה, לשרת (ביומן הפעילות): dsviz — Eclipse / IntelliJ / Visual Studio / VS Code; automata — "מודלים חישוביים" */
    public static volatile String app = "dsviz";

    /**
     * מה הלקוח הזה יודע לעשות — נשלח לשרת בכניסה ובכל בדיקה (caps). "register": מכיר את המצב "register" — תלמיד שעוד לא השלים
     * הרשמה (שם מלא וטלפון) מופנה לדף ההרשמה בדפדפן, והמאגר נפתח אחריה. "classroom": זו הגרסה עם מרכז הבקרה — השרת פותח לה
     * (ולמורה של העמדה) את עמדת הניהול, ההגשות והציונים גם כשמרכז הבקרה עוד כבוי לכולם. לקוח ישן לא שולח, והשרת עונה לו כמו קודם.
     */
    static final List<String> CAPS = java.util.Collections.unmodifiableList(java.util.Arrays.asList("register", "classroom"));

    /** המפתח הציבורי של השרת (החתימה על האישור). ציבורי — לא סוד; המפתח הפרטי נמצא רק בשרת (PASS_KEY) */
    static final String PASS_KEY = "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAzcPy66SILXotJ2VPxjt93e7dGBR1chdnNh6Ptgr6KYXn5DGhSYEbkT3dsFKWO96QQCw/wG/bsQmqtLDxLHgjzOxjmYMGxyLmBkhHfywmYBl8JvRRPkWUn5AqZI5VgQ7cqeF4vbjMIOeb3jR3GB4QhfaI04v+4mAna6KQEC0drXfhT5oMuOhbfxH2t77tCC1iUkAnnfA/VprWcvPyieyzdTxZ4/r4e9VS5hTGceRsKpsdC7/Euy31vqrJxeVLV7Mwc1PkAMk0ghEJtSRBbvZdRUv5X5K8DLAjVpEVRHlq2XwobMI2c8oSU8qGESGsUk/R1lGUWIqGOXE+UdW6ODPVjQIDAQAB";

    private static final SecureRandom RANDOM = new SecureRandom();
    private static volatile String machineRaw, machineHash, machineOsReal;
    /** תוכן קובץ המזהה (כמו i.dat) — נשמר גם בתוך כל קובץ זהב (השדה "id") */
    private static volatile String identityText;
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
        if (windows()) return new File(envDir("ProgramData", "C:\\ProgramData"), "DSViz");
        if (mac()) return sysPath("/Users/Shared/DSViz");
        return sysPath("/var/tmp/dsviz");
    }

    /**
     * מערכת ההפעלה — לבחירת התיקיות. בבדיקות: ‎-Ddsviz.os=windows / mac / linux‎ (הסידור של Windows / Mac נבדק גם ב-Linux,
     * יחד עם ‎-Ddsviz.env.<משתנה>‎ ו-‎-Ddsviz.fsroot‎). את מזהה המחשב (liveOsId) זה לא משנה
     */
    private static String osName() {
        String t = System.getProperty("dsviz.os");
        return (t != null && !t.isEmpty() ? t : System.getProperty("os.name", "")).toLowerCase();
    }

    private static boolean mac() {
        return osName().contains("mac");
    }

    /** משתנה סביבה; בבדיקות ‎-Ddsviz.env.<שם>‎ מחליף אותו */
    private static String env(String var) {
        String t = System.getProperty("dsviz.env." + var);
        return t != null ? t : System.getenv(var);
    }

    /** נתיב קבוע של המערכת (‎/Users/Shared‎…); בבדיקות ‎-Ddsviz.fsroot=<תיקייה>‎ — מתחת לתיקייה זמנית */
    private static File sysPath(String path) {
        String r = System.getProperty("dsviz.fsroot");
        return r == null || r.isEmpty() ? new File(path) : new File(r, path);
    }

    // ------------------------------------------------------------------ מקום שמור ונסתר לקבצי הזיהוי

    /*
     * "קובץ הזהב" — הכניסה השמורה, קובץ אחד קבוע לכל תפקיד, שמחזיק את המחשב מחובר אחרי כיבוי, עדכון גרסה, התקנה מחדש
     * ותקופות בלי אינטרנט. הוא נמחק רק כש: (1) מורה / תלמיד לוחצים "התנתקות" (signOut), (2) השרת עונה שהכניסה בוטלה —
     * לעמדה: רק "שוחררה" (revoked — המורה / ההנהלה שחררו); למורה / תלמיד: התנתקות ממקום אחר / ההנהלה ביטלה (refreshWith).
     * תקלת רשת / שרת / תשובה לא תקינה אף פעם לא מוחקת (post מחזיר offline / error, ו-refresh לא נוגע בקובץ).
     *
     *   עמדת מחשב (כל המחשב):  Windows ‎%ProgramData%\Lamerhav\.cache\s.dat‎ · Mac ‎/Users/Shared/.lamerhav/s.dat‎ · Linux ‎~/.local/share/.lamerhav/s.dat‎
     *                          (כשאין הרשאה לכתוב שם — אצל המשתמש, ‎<תיקיית המשתמש>/s.dat‎; stationFiles)
     *   מורה / תלמיד (המשתמש): Windows ‎%LOCALAPPDATA%\Lamerhav\.cache\a.dat‎ · Mac / Linux ‎~/.local/share/.lamerhav/a.dat‎
     *
     * למה דווקא שם: שום התקנה / עדכון / הסרה לא נוגעים בתיקיות האלה. ‎%LOCALAPPDATA%\DSViz‎ היא תיקיית ההתקנה ב-Windows,
     * ו-‎~/Library/Application Support/DSViz‎ ב-Mac מוחלפת כולה בכל עדכון (install.sh) ונמחקת בהסרה (uninstall.sh) — לכן
     * הקובץ לא שם. ‎%ProgramData%\DSViz‎ ו-‎~/.dsviz‎ הם המקומות הישנים (עד 1.0.1): משם הכניסה עוברת לכאן פעם אחת (migrate).
     *
     * כל קובץ זהב:
     *   • נכתב באטומיות (קובץ זמני ייחודי + החלפה בשינוי שם, בתוך הנעילה) — אף פעם לא נשאר חצי קובץ;
     *   • עם שני גיבויים: ‎<קובץ>.bak‎ באותה תיקייה (גם לעמדה — כל משתמש של המחשב רואה אותו), ועותק בתיקייה אחרת לגמרי
     *     (Windows ‎%APPDATA%\Lamerhav\.cache‎, Mac / Linux ‎~/.config/.lamerhav‎). קובץ ראשי פגום / חסר — נקרא מהגיבוי ומשוחזר
     *     (והפגום נשמר בצד כ-‎.bad‎ — לא מוחקים שום דבר שאולי עוד נפתח);
     *   • מוצפן (AES-GCM, מפתח לפי המחשב), והאישור שבתוכו חתום בשרת (RSA) ונבדק בכל קריאה (verifyPass) — כמו קודם;
     *   • מחזיק גם את מזהה ההתקנה (השדה "id", אותו תוכן כמו i.dat, לא סוד): אם i.dat נמחק בכל המקומות, המזהה נלקח מקובץ הזהב
     *     והכניסה ממשיכה להיפתח. קובץ שהועתק למחשב אחר עדיין לא נפתח (שם מחשב / מזהה Windows אחרים → מפתח אחר).
     * הקבצים מסומנים כנסתרים ב-Windows. הסרה מלאה של DSViz (לא עדכון) משאירה אותם בכוונה: התקנה מחדש — עדיין מחוברים.
     */

    /** בדיקות (-Ddsviz.home / -Ddsviz.machineDir): הסידור הישן, בלי העברה ובלי גיבוי */
    private static boolean plainDirs() {
        String a = System.getProperty("dsviz.home"), b = System.getProperty("dsviz.machineDir");
        return a != null && !a.isEmpty() || b != null && !b.isEmpty();
    }

    private static boolean windows() {
        return osName().contains("win");
    }

    private static File envDir(String var, String fallback) {
        String v = env(var);
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
        if (windows()) return new File(new File(envDir("ProgramData", "C:\\ProgramData"), "Lamerhav"), ".cache");
        if (mac()) return sysPath("/Users/Shared/.lamerhav");
        return secureUserDir();   // Linux: ‎/var/tmp‎ נמחק — נשארים אצל המשתמש
    }

    /** הגיבוי של קובץ (אותו שם, במקום הגיבוי), או null */
    private static File backupOf(File f) {
        File b = secureBackupDir();
        if (b == null || f == null) return null;
        File g = new File(b, f.getName());
        return g.getAbsoluteFile().equals(f.getAbsoluteFile()) ? null : g;
    }

    /** הגיבוי שליד הקובץ, באותה תיקייה: ‎<קובץ>.bak‎ */
    static File bakOf(File f) {
        return f == null ? null : new File(f.getParentFile(), f.getName() + ".bak");
    }

    /** כל העותקים של קובץ זהב: הקובץ, ‎.bak‎ שלידו, והעותק בתיקיית הגיבוי (בלי null) */
    private static List<File> copiesOf(File f) {
        List<File> out = new ArrayList<File>();
        for (File x : new File[] { f, bakOf(f), backupOf(f) }) if (x != null && !out.contains(x)) out.add(x);
        return out;
    }

    /** מעתיק קובץ באטומיות (קובץ זמני ליד היעד + החלפה בשינוי שם) */
    private static void copyAtomic(File from, File to, boolean owner) throws IOException {
        to.getParentFile().mkdirs();
        File tmp = new File(to.getParentFile(), to.getName() + "." + Long.toHexString(RANDOM.nextLong() & Long.MAX_VALUE) + ".tmp");
        try {
            Files.copy(from.toPath(), tmp.toPath(), StandardCopyOption.REPLACE_EXISTING);
            if (owner) ownerOnly(tmp);
            moveAtomic(tmp, to);
        } finally {
            if (tmp.exists()) tmp.delete();
        }
    }

    private static void moveAtomic(File from, File to) throws IOException {
        try {
            Files.move(from.toPath(), to.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException e) {
            Files.move(from.toPath(), to.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
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

    private static volatile boolean shareTried;

    /**
     * Windows: התיקייה המשותפת נוצרת עם הרשאות ברירת המחדל של ProgramData — משתמש אחר של המחשב יכול לקרוא את הקבצים, אבל לא
     * להחליף אותם (ואז חיבור חדש שלו נשמר רק אצלו, ולכל משתמש קובץ אחר). מי שיצר את התיקייה הוא הבעלים שלה, ולכן הוא יכול
     * לתת לקבוצה "משתמשים" (S-1-5-32-545, בלי תלות בשפה) הרשאת שינוי. פעם אחת; מי שאינו הבעלים — מנסה לכל היותר פעם בשבוע.
     */
    private static void shareMachineDir(File dir) {
        if (shareTried || dir == null || !windows() || plainDirs()) return;
        try {
            if (!dir.getAbsoluteFile().equals(secureMachineDir().getAbsoluteFile())) return;
            shareTried = true;
            File mark = new File(dir, "p.dat"), tried = new File(secureUserDir(), "p.dat");
            if (mark.isFile() || !dir.isDirectory()) return;
            if (tried.isFile() && System.currentTimeMillis() - tried.lastModified() < 7L * 86400000L) return;
            run("icacls", dir.getParentFile().getPath(), "/grant", "*S-1-5-32-545:(OI)(CI)M", "/T", "/C", "/Q");
            String check = run("icacls", dir.getPath());
            if (check.contains("S-1-5-32-545") || check.matches("(?s).*\\((OI|CI)\\)\\((OI|CI)\\)\\(M\\).*")) {
                Files.write(mark.toPath(), new byte[0]);
                hide(mark);
            } else {
                tried.getParentFile().mkdirs();
                Files.write(tried.toPath(), new byte[0]);
                hide(tried);
            }
        } catch (Exception ignored) {
            // בלי שיתוף — הקובץ נשמר אצל המשתמש כשאי אפשר להחליף את המשותף
        }
    }

    /** כותב קובץ קטן (לא מוצפן — למזהה) במקום הראשי ובגיבוי, ומסמן כנסתר */
    private static boolean writeBoth(final File f, final byte[] data) {
        try {
            return locked(new Callable<Boolean>() {
                @Override public Boolean call() { return writeBothLocked(f, data); }
            });
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean writeBothLocked(File f, byte[] data) {
        boolean ok = false;
        for (File x : new File[] { f, backupOf(f) }) {
            if (x == null) continue;
            try {
                x.getParentFile().mkdirs();
                Files.write(x.toPath(), data);
                hide(x);
                shareMachineDir(x.getParentFile());
                ok = true;
            } catch (IOException ignored) {
                // נסה את הבא
            }
        }
        return ok;
    }

    /**
     * מוחק קובץ זהב עם כל העותקים שלו. נקרא רק מ: התנתקות (signOut), תשובת שרת שהכניסה בוטלה (refresh / refreshWith),
     * והתור של ההתנתקויות בלי אינטרנט. שום מסלול של תקלה לא מגיע לכאן
     */
    private static void forget(final File f) {
        if (f == null) return;
        lockedRun(new Runnable() {
            @Override public void run() {
                for (File x : copiesOf(f)) x.delete();
                syncTeacherFlag();
            }
        });
    }

    private static double checkedAt(Map<String, Object> d) {
        return d != null && d.get("checkedAt") instanceof Number ? ((Number) d.get("checkedAt")).doubleValue() : 0;
    }

    /**
     * העברה חד-פעמית מהמקום הישן (‎~/.dsviz/account.json‎, ‎ProgramData\DSViz\station.json‎) לקובץ הזהב. אף פעם לא מאבדים כניסה:
     *   • הישן לא נפתח (עדיין) — נשאר במקומו, כמו שהוא;
     *   • אין קובץ זהב שנפתח, או שהישן נבדק מול השרת מאוחר יותר (חזרו לגרסה ישנה ונכנסו שם) — הישן נכתב לקובץ הזהב, ונקרא בחזרה;
     *   • רק אחרי שקובץ הזהב נפתח — הישן לא נמחק אלא מקבל שם אחר (‎.migrated‎): כך הוא לא "חוזר לחיים" אחרי התנתקות, ועדיין יש עותק.
     */
    private static void migrate(final File legacy, final File now) {
        try {
            if (legacy == null || now == null || !legacy.isFile() || legacy.getAbsoluteFile().equals(now.getAbsoluteFile())) return;
            final String tried = legacyMark(legacy) + "|" + machine();
            if (MIGRATE_FAILED.contains(tried)) return;   // ישן שלא נפתח: לא מנסים שוב בכל קריאה (AccessWatch — כל 1.5 שניות)
            lockedRun(new Runnable() {
                @Override public void run() {
                    if (!legacy.isFile()) return;   // תוכנה אחרת כבר העבירה
                    String mark = legacyMark(legacy);
                    if (mark.isEmpty() || migratedMarks().contains(mark)) return;   // כבר הועבר (ואי אפשר היה לשנות את שמו)
                    Map<String, Object> old = open(legacy, storeKey());
                    if (old == null) { MIGRATE_FAILED.add(tried); return; }
                    Map<String, Object> cur = load(now);
                    if (cur == null || checkedAt(old) > checkedAt(cur)) {
                        if (cur == null && now.isFile()) {   // קובץ זהב שלא נפתח — נשמר בצד, לא נדרס
                            try { moveAtomic(now, new File(now.getParentFile(), now.getName() + ".bad")); } catch (IOException ignored) { /* נדרס */ }
                        }
                        if (!saveLocked(now, old)) return;
                        Map<String, Object> back = load(now);
                        if (back == null || !Json.str(old.get("token")).equals(Json.str(back.get("token")))) return;
                    }
                    try {
                        moveAtomic(legacy, new File(legacy.getParentFile(), legacy.getName() + ".migrated"));
                    } catch (IOException e) {
                        // אין הרשאה לשנות את השם (קובץ של משתמש Windows אחר): נרשם שכבר הועבר — אחרת אחרי התנתקות / שחרור
                        // הכניסה הישנה הייתה "חוזרת לחיים" מכאן
                        noteMigrated(mark);
                    }
                }
            });
        } catch (Exception ignored) {
            // נשאר במקום הישן — ננסה בפעם הבאה
        }
    }

    private static final java.util.Set<String> MIGRATE_FAILED = java.util.Collections.synchronizedSet(new java.util.HashSet<String>());

    /** הקובץ שבו נרשמים קבצים ישנים שכבר הועברו ואי אפשר היה לשנות את שמם (שורה לכל קובץ: hash של התוכן) */
    private static File migratedFile() {
        return new File(secureUserDir(), plainDirs() ? "migrated.txt" : "m.dat");
    }

    private static String legacyMark(File legacy) {
        try {
            return hex(sha256Bytes(Files.readAllBytes(legacy.toPath()))).substring(0, 32);
        } catch (IOException e) {
            return "";
        }
    }

    private static List<String> migratedMarks() {
        List<String> out = new ArrayList<String>();
        try {
            File f = migratedFile();
            if (f.isFile()) for (String l : new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8).split("\\r?\\n")) if (!l.trim().isEmpty()) out.add(l.trim());
        } catch (IOException ignored) {
            // בלי רשימה
        }
        return out;
    }

    private static void noteMigrated(String mark) {
        try {
            List<String> all = migratedMarks();
            if (all.contains(mark)) return;
            all.add(mark);
            while (all.size() > 20) all.remove(0);
            File f = migratedFile();
            f.getParentFile().mkdirs();
            Files.write(f.toPath(), (String.join("\n", all) + "\n").getBytes(StandardCharsets.UTF_8));
            hide(f);
        } catch (IOException ignored) {
            // לא חובה
        }
    }

    // ------------------------------------------------------------------ נעילה: כמה תוכנות כותבות לאותם קבצים

    private static final Object WRITE_LOCK = new Object();
    private static final ThreadLocal<int[]> LOCK_DEPTH = new ThreadLocal<int[]>() {
        @Override protected int[] initialValue() { return new int[1]; }
    };
    static final long LOCK_WAIT_MS = 5000;

    /**
     * כל כתיבה / מחיקה של קובצי הזיהוי עוברת כאן: נעילה בתוך התוכנה (חוטים) וגם בין התוכנות של אותו משתמש — Eclipse,
     * IntelliJ, Visual Studio ו"מודלים חישוביים" יכולים לכתוב באותו רגע (‎.lock‎ בתיקייה הנסתרת של המשתמש). בלי הרשאה לקובץ
     * הנעילה / תוכנה תקועה שמחזיקה אותו יותר מ-5 שניות — כותבים בלי נעילה (כמו קודם), לא נתקעים.
     */
    static <T> T locked(Callable<T> work) throws Exception {
        synchronized (WRITE_LOCK) {
            int[] depth = LOCK_DEPTH.get();
            if (depth[0] > 0) {   // כבר נעול בחוט הזה (קריאה מקוננת)
                depth[0]++;
                try { return work.call(); } finally { depth[0]--; }
            }
            FileChannel ch = null;
            FileLock lock = null;
            try {
                File dir = secureUserDir();
                dir.mkdirs();
                File lf = new File(dir, ".lock");
                ch = FileChannel.open(lf.toPath(), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
                hide(lf);
                long until = System.currentTimeMillis() + LOCK_WAIT_MS;
                while ((lock = ch.tryLock()) == null && System.currentTimeMillis() < until) Thread.sleep(20);
            } catch (IOException | OverlappingFileLockException e) {
                lock = null;   // בלי נעילה בין תוכנות
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            depth[0]++;
            try {
                return work.call();
            } finally {
                depth[0]--;
                try { if (lock != null) lock.release(); } catch (IOException ignored) { /* נסגר עם הערוץ */ }
                try { if (ch != null) ch.close(); } catch (IOException ignored) { /* לא חשוב */ }
            }
        }
    }

    /** locked בלי ערך חוזר ובלי חריגות (מה שבפנים כבר מטפל בשגיאות שלו) */
    private static void lockedRun(final Runnable work) {
        try {
            locked(new Callable<Object>() {
                @Override public Object call() { work.run(); return null; }
            });
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // ------------------------------------------------------------------ מזהה המחשב

    /*
     * מזהה המחשב = hash(מזהה מערכת ההפעלה | מזהה אקראי של ההתקנה). ממנו נגזרים גם המפתח של הקבצים השמורים וגם ה-mid שבאישור
     * החתום — אם הוא משתנה, העמדה "נעלמת" (הקובץ לא נפתח) והמחשב מבקש שוב קוד. לכן הוא חייב להיות אותו דבר בכל תוכנה, אצל כל
     * משתמש Windows ובכל הפעלה. מה שהיה שובר אותו:
     *   • reg.exe חסום לתלמידים (מדיניות "מניעת גישה לכלי עריכת הרישום") או נכשל רגעית — ואז נלקח מזהה חלופי מקובץ, אחר לגמרי;
     *   • Java של 32 ביט (Eclipse ישן) רואה רישום אחר מ-Java של 64 ביט — שתי תוכנות באותו מחשב עם שני מזהים;
     *   • ב-Mac שם המחשב משתנה לפי הרשת, והמזהה האקראי נוצר מחדש.
     * לכן: (1) מזהה מערכת ההפעלה נקרא בכמה דרכים (reg בתצוגת 64 ביט, reg רגיל, PowerShell); (2) מה שנבחר נשמר ב-i.dat (שורה 3),
     * וכשאי אפשר לקרוא — משתמשים בשמור; (3) כשיש כבר קובץ שמור של עמדה / משתמש, בוחרים את המזהה שפותח אותו (כך עדכון גרסה
     * אף פעם לא מנתק מחשב שכבר מחובר); (4) קובץ שהועתק ממחשב אחר עדיין נדחה: שם מחשב אחר, או מזהה Windows אחר מזה שנשמר.
     */

    /** מזהה קבוע של המחשב (hash) — אותו מזהה לכל המשתמשים ולכל התוכנות (Eclipse / IntelliJ / Visual Studio) */
    public static String machine() {
        if (machineHash == null) resolveIdentity();
        return machineHash;
    }

    /**
     * מזהה קבוע של המחשב עצמו, בלי החלק האקראי (מזהה מערכת ההפעלה + שם המחשב). נשלח לשרת בחיבור עמדה: לפיו השרת מזהה שזה
     * אותו מחשב גם כשהמזהה הרגיל התחלף (מחשב ששוחזר, קובץ שנמחק), ולא לוקח עוד מקום מהמורה. "" כשאין מזהה אמיתי של המערכת.
     */
    public static String stableMachine() {
        machine();
        String os = machineOsReal;
        if (os == null || os.isEmpty()) {
            OsId live = liveOsId(true);
            os = live.real ? live.value : "";
            machineOsReal = os;
        }
        if (os.isEmpty()) return "";
        return hex(sha256("dsviz-stable|" + os + "|" + (strictHost() ? machineName() : ""))).substring(0, 32);
    }

    private static final class OsId {
        final String value;
        final boolean real;   // אמיתי (מהמערכת), או חלופי (קובץ / שם המשתמש)
        OsId(String value, boolean real) { this.value = value; this.real = real; }
    }

    private static synchronized void resolveIdentity() {
        if (machineHash != null) return;
        String[] id;
        try {
            id = locked(new Callable<String[]>() {
                @Override public String[] call() { return identityLocked(); }
            });
        } catch (Exception e) {
            id = identityLocked();
        }
        identityText = id[0] + "\n" + machineName() + "\n" + (System.getProperty("dsviz.machine") != null && !System.getProperty("dsviz.machine").isEmpty()
                ? "" : ("1".equals(id[2]) ? "g:" : "f:") + id[1] + "\n");
        machineRaw = id[1] + "|" + id[0];
        machineOsReal = "1".equals(id[2]) ? id[1] : null;
        // במעבדות מחשבים משוכפלים מתמונה אחת (אותו MachineGuid בכולם) — בלי המזהה האקראי, כל המחשבים נחשבו עמדה אחת ודרסו זה את זה
        machineHash = hex(sha256("dsviz|" + machineRaw)).substring(0, 32);
    }

    /** שם המחשב נבדק מול הקובץ רק ב-Windows (COMPUTERNAME קבוע). ב-Mac / Linux השם משתנה לפי הרשת (בבדיקות: dsviz.hostname) */
    private static boolean strictHost() {
        String forced = System.getProperty("dsviz.hostname");
        return windows() || forced != null && !forced.isEmpty();
    }

    /**
     * קובץ מזהה תקין: [המזהה האקראי, מזהה המערכת השמור ("" אם אין), "1" אם השמור אמיתי], או null. גם קובץ זהב (‎*.dat‎ מוצפן):
     * המזהה שבשדה "id" שלו
     */
    private static String[] readIdentityFile(File f, String host) {
        try {
            if (f == null || !f.isFile()) return null;
            String text = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
            if (text.startsWith("{")) {
                try {
                    text = Json.str(Json.obj(Json.parse(text)).get("id"));
                } catch (RuntimeException e) {
                    return null;
                }
            }
            String[] lines = text.split("\\r?\\n");
            if (lines.length < 2 || lines[0].trim().length() < 16) return null;
            if (strictHost() && !lines[1].trim().equals(host)) return null;   // הקובץ הועתק ממחשב אחר
            String os = lines.length >= 3 ? lines[2].trim() : "";
            boolean real = os.startsWith("g:");
            if (os.startsWith("g:") || os.startsWith("f:")) os = os.substring(2); else os = "";
            return new String[] { lines[0].trim(), os, real ? "1" : "0" };
        } catch (IOException e) {
            return null;
        }
    }

    /** מחזיר [המזהה האקראי של ההתקנה, מזהה המערכת שבשימוש, "1" אם הוא אמיתי] */
    private static String[] identityLocked() {
        String host = machineName();
        // המקומות החדשים (נסתרים, עם גיבוי) קודם, ואחריהם המקום הישן — ממנו המזהה עובר לחדש (אותו מזהה: אחרת היו
        // אובדים העמדה והקבצים המוצפנים)
        File mPrimary = new File(secureMachineDir(), "i.dat"), uPrimary = new File(secureUserDir(), "i.dat");
        List<File> readList = new ArrayList<File>(java.util.Arrays.asList(mPrimary, uPrimary, backupOf(uPrimary), new File(machineDir(), "install-id"), new File(userDir(), "install-id")));
        // ואחריהם — המזהה ששמור בתוך קובצי הזהב (i.dat נמחק בכל המקומות: הכניסה עדיין נפתחת)
        for (File g : goldenCopies()) readList.add(g);
        File[] reads = readList.toArray(new File[0]);
        String forced = System.getProperty("dsviz.machine");
        boolean isForced = forced != null && !forced.isEmpty();
        OsId live = isForced ? new OsId(forced, true) : null, fast = null;

        List<String[]> files = new ArrayList<String[]>();
        for (File f : reads) {
            String[] x = readIdentityFile(f, host);
            if (x == null) continue;
            // מזהה Windows אמיתי ששמור בקובץ ושונה מזה של המחשב הזה: הקובץ הועתק ממחשב אחר (גם אם שם המחשב זהה)
            // (נבדק רק בקריאה המהירה, reg. כשהיא חסומה — סומכים על שם המחשב, ולא מפעילים PowerShell בכל הפעלה)
            if ("1".equals(x[2]) && !isForced) {
                if (fast == null) fast = liveOsId(false);
                if (fast.real && !fast.value.equals(x[1])) continue;
            }
            files.add(x);
        }
        List<String> installs = new ArrayList<String>();
        for (String[] x : files) if (!installs.contains(x[0])) installs.add(x[0]);

        // המועמדים למזהה המערכת, לפי הסדר: מה ששמור, מה שנקרא עכשיו, והמזהים החלופיים של הגרסאות הקודמות
        List<OsId> oss = new ArrayList<OsId>();
        if (isForced) oss.add(live);
        else {
            for (String[] x : files) if (!x[1].isEmpty()) addOs(oss, new OsId(x[1], "1".equals(x[2])));
        }
        String install = null;
        OsId os = null;
        List<File> targets = identityTargets();
        if (!installs.isEmpty() && !targets.isEmpty()) {
            // יש כבר קובץ שמור (עמדה / משתמש): בוחרים את המזהה שפותח אותו
            String[] hit = firstThatOpens(installs, oss, targets);
            if (hit == null && !isForced) {
                if (live == null) live = fast != null && fast.real ? fast : liveOsId(true);
                List<OsId> more = new ArrayList<OsId>();
                addOs(more, live);
                for (OsId x : fallbackOsIds(false)) addOs(more, x);
                more.removeAll(oss);
                hit = firstThatOpens(installs, more, targets);
                for (OsId x : more) addOs(oss, x);
            }
            if (hit != null) {
                install = hit[0];
                for (OsId x : oss) if (x.value.equals(hit[1])) os = x;
            }
        }
        if (os == null) {
            if (!oss.isEmpty()) os = oss.get(0);   // מה ששמור (קובץ עם מזהה אמיתי אחר כבר נדחה למעלה)
            else {
                if (live == null) live = fast != null && fast.real ? fast : liveOsId(true);
                os = live.real ? live : firstOr(fallbackOsIds(true), live);
            }
        }
        boolean fresh = install == null && installs.isEmpty();
        if (install == null) install = installs.isEmpty() ? UUID.randomUUID().toString().replace("-", "") : installs.get(0);
        boolean kept = keepIdentity(install, host, isForced ? null : os, mPrimary, uPrimary);
        if (fresh && !kept) install = "host-" + host;   // אי אפשר לכתוב קובץ בשום מקום — שם המחשב (עדיין שונה בין מחשבים)
        return new String[] { install, os.value, os.real ? "1" : "0" };
    }

    private static void addOs(List<OsId> list, OsId x) {
        if (x == null || x.value == null || x.value.isEmpty()) return;
        for (OsId y : list) if (y.value.equals(x.value)) return;
        list.add(x);
    }

    private static OsId firstOr(List<OsId> list, OsId other) {
        return list.isEmpty() ? other : list.get(0);
    }

    /** קובצי הזהב (עמדה / משתמש) עם כל העותקים שלהם — קיימים בלבד, בלי לגעת בהם */
    private static List<File> goldenCopies() {
        List<File> out = new ArrayList<File>();
        File[] main = plainDirs()
                ? new File[] { new File(machineDir(), STATION_FILE), new File(userDir(), STATION_FILE), new File(userDir(), USER_FILE) }
                : new File[] { new File(secureMachineDir(), STATION_DAT), new File(secureUserDir(), STATION_DAT), new File(secureUserDir(), USER_DAT) };
        for (File f : main) for (File x : copiesOf(f)) if (x.isFile() && !out.contains(x)) out.add(x);
        return out;
    }

    /** הקבצים המוצפנים שכבר שמורים במחשב (עמדה / משתמש, במקומות החדשים, בגיבויים ובמקומות הישנים) — בלי לגעת בהם */
    private static List<File> identityTargets() {
        List<File> out = goldenCopies();
        if (!plainDirs()) {
            for (File f : new File[] { new File(machineDir(), STATION_FILE), new File(userDir(), STATION_FILE), new File(userDir(), USER_FILE) })
                if (f.isFile() && !out.contains(f)) out.add(f);
        }
        return out;
    }

    /** הצירוף הראשון (מזהה התקנה, מזהה מערכת) שפותח אחד מהקבצים השמורים: [install, os], או null */
    private static String[] firstThatOpens(List<String> installs, List<OsId> oss, List<File> targets) {
        for (String install : installs) for (OsId os : oss) {
            SecretKeySpec key = keyOf(os.value + "|" + install);
            for (File f : targets) if (open(f, key) != null) return new String[] { install, os.value };
        }
        return null;
    }

    /**
     * שומר את המזהה (ואת מזהה המערכת שנבחר) בכל המקומות החדשים שחסר בהם או שהתוכן בהם שונה, ומוחק את העותקים מהמקום הישן.
     * os == null (בדיקות עם dsviz.machine): בלי השורה השלישית.
     */
    private static boolean keepIdentity(String id, String host, OsId os, File mPrimary, File uPrimary) {
        String text = id + "\n" + host + "\n" + (os == null ? "" : (os.real ? "g:" : "f:") + os.value + "\n");
        byte[] data = text.getBytes(StandardCharsets.UTF_8);
        boolean ok = false;
        for (File f : new File[] { mPrimary, uPrimary }) {
            File b = backupOf(f);
            if (sameContent(f, data) && (b == null || sameContent(b, data))) { ok = true; continue; }
            if (writeBoth(f, data)) ok = true;
            else if (readIdentityFile(f, host) != null && id.equals(readIdentityFile(f, host)[0])) ok = true;   // קיים, של משתמש אחר: אין הרשאה לעדכן, והמזהה זהה
        }
        if (ok && !plainDirs()) {
            new File(machineDir(), "install-id").delete();
            new File(userDir(), "install-id").delete();
        }
        return ok;
    }

    private static boolean sameContent(File f, byte[] data) {
        try {
            return f.isFile() && java.util.Arrays.equals(Files.readAllBytes(f.toPath()), data);
        } catch (IOException e) {
            return false;
        }
    }

    private static final java.util.regex.Pattern GUID = java.util.regex.Pattern.compile("[0-9A-Fa-f]{8}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{12}");

    /** מזהה מערכת ההפעלה, כמו שהוא נקרא עכשיו. real = false: לא הצלחנו לקרוא (value הוא מזהה חלופי, כמו בגרסאות הקודמות) */
    private static OsId liveOsId(boolean thorough) {
        if ("1".equals(System.getProperty("dsviz.osid.off"))) return new OsId("", false);   // לבדיקות: כמו reg.exe חסום
        String os = System.getProperty("os.name", "").toLowerCase();
        try {
            if (os.contains("win")) {
                // (1) reg בתצוגת 64 ביט: גם Java של 32 ביט רואה את אותו MachineGuid; (2) reg רגיל; (3) PowerShell — כש-reg.exe חסום במדיניות
                String[][] tries = {
                    { "reg", "query", "HKLM\\SOFTWARE\\Microsoft\\Cryptography", "/v", "MachineGuid", "/reg:64" },
                    { "reg", "query", "HKLM\\SOFTWARE\\Microsoft\\Cryptography", "/v", "MachineGuid" },
                    { "powershell", "-NoProfile", "-NonInteractive", "-Command",
                        "[Microsoft.Win32.RegistryKey]::OpenBaseKey('LocalMachine','Registry64').OpenSubKey('SOFTWARE\\Microsoft\\Cryptography').GetValue('MachineGuid')" },
                };
                for (String[] cmd : tries) {
                    if (!thorough && "powershell".equals(cmd[0])) continue;   // איטי: רק כשמחפשים מזהה, לא בכל הפעלה
                    if ("reg".equals(cmd[0]) && "1".equals(System.getProperty("dsviz.osid.noreg"))) continue;   // לבדיקות: כמו reg.exe חסום במדיניות
                    try {
                        String out = run(cmd);
                        java.util.regex.Matcher m = java.util.regex.Pattern.compile("MachineGuid\\s+REG_SZ\\s+(\\S+)").matcher(out);
                        if (m.find()) return new OsId(m.group(1), true);
                        if ("powershell".equals(cmd[0])) {
                            java.util.regex.Matcher g = GUID.matcher(out);
                            if (g.find()) return new OsId(g.group(), true);
                        }
                    } catch (Exception ignored) {
                        // הדרך הבאה
                    }
                }
            } else if (os.contains("mac")) {
                String out = run("ioreg", "-rd1", "-c", "IOPlatformExpertDevice");
                java.util.regex.Matcher m = java.util.regex.Pattern.compile("\"IOPlatformUUID\"\\s*=\\s*\"([^\"]+)\"").matcher(out);
                if (m.find()) return new OsId(m.group(1), true);
            } else {
                for (String p : new String[] { "/etc/machine-id", "/var/lib/dbus/machine-id" }) {
                    File f = new File(p);
                    if (f.isFile()) {
                        String s = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8).trim();
                        if (!s.isEmpty()) return new OsId(s, true);
                    }
                }
            }
        } catch (Exception ignored) {
            // נופלים למזהה חלופי
        }
        return new OsId("", false);
    }

    /**
     * המזהים החלופיים (כשאין מזהה של מערכת ההפעלה): מזהה אקראי שנשמר פעם אחת בקובץ machine-id (לכל המחשב אם אפשר), ואם אי
     * אפשר — שם המשתמש. create = false: רק מה שכבר קיים (למציאת המזהה שפותח קובץ שמור מגרסה קודמת).
     */
    private static List<OsId> fallbackOsIds(boolean create) {
        List<OsId> out = new ArrayList<OsId>();
        for (File dir : new File[] { machineDir(), userDir() }) {
            File f = new File(dir, "machine-id");
            try {
                if (f.isFile()) {
                    addOs(out, new OsId(new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8).trim(), false));
                    continue;
                }
                if (!create || !out.isEmpty()) continue;
                dir.mkdirs();
                String id = UUID.randomUUID().toString();
                Files.write(f.toPath(), id.getBytes(StandardCharsets.UTF_8));
                addOs(out, new OsId(id, false));
            } catch (IOException ignored) {
                // נסה את התיקייה הבאה
            }
        }
        addOs(out, new OsId("unknown-" + System.getProperty("user.name", ""), false));
        return out;
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

    /**
     * המקומות של קובץ העמדה, לפי הסדר: לכל המחשב, ואחריו אצל המשתמש (כשאין הרשאה לכתוב בתיקייה המשותפת, או כשהקובץ שם של
     * משתמש אחר ואי אפשר להחליף אותו). קבצים מהמקום הישן עוברים לבד.
     */
    private static File[] stationFiles() {
        if (plainDirs()) return new File[] { new File(machineDir(), STATION_FILE), new File(userDir(), STATION_FILE) };
        File m = new File(secureMachineDir(), STATION_DAT), u = new File(secureUserDir(), STATION_DAT);
        migrate(new File(machineDir(), STATION_FILE), m);
        migrate(new File(userDir(), STATION_FILE), m.isFile() ? u : m);
        return m.getAbsoluteFile().equals(u.getAbsoluteFile()) ? new File[] { m } : new File[] { m, u };
    }

    /**
     * הקובץ של עמדת המחשב: מבין המקומות — זה שנפתח ושהאישור בו תקף (ואם יש שניים, זה שנבדק אחרון מול השרת). פעם נלקח תמיד
     * הקובץ המשותף אם הוא קיים — גם כשהוא לא נפתח או ישן, וכך חיבור חדש שנשמר אצל המשתמש "נעלם" והמחשב ביקש שוב קוד.
     */
    private static File stationFile() {
        File[] fs = stationFiles();
        File best = null, readable = null;
        double bestAt = -1;
        for (File f : fs) {
            Map<String, Object> d = load(f);
            if (d == null) continue;
            if (readable == null) readable = f;
            if (verifyPass(Json.str(d.get("pass"))) == null) continue;
            double at = checkedAt(d);
            if (best == null || at > bestAt) { best = f; bestAt = at; }
        }
        return best != null ? best : readable != null ? readable : fs[0];
    }

    /** מפתח ההצפנה של הקבצים, לפי מזהה המחשב (לפני ה-hash) */
    private static SecretKeySpec keyOf(String raw) {
        return new SecretKeySpec(sha256("dsviz-store|" + raw), 0, 16, "AES");
    }

    /** פותח קובץ מוצפן עם המפתח. null — אין קובץ / לא נפתח (מחשב אחר, קובץ פגום) */
    private static Map<String, Object> open(File f, SecretKeySpec key) {
        try {
            if (f == null || !f.isFile()) return null;
            Map<String, Object> outer = Json.obj(Json.parse(new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8)));
            byte[] all = Base64.getDecoder().decode(Json.str(outer.get("data")));
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, all, 0, 12));
            byte[] plain = c.doFinal(all, 12, all.length - 12);
            return Json.obj(Json.parse(new String(plain, StandardCharsets.UTF_8)));
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * קורא קובץ זהב. הקובץ הראשי חסר / פגום / נכתב בחצי — קוראים מ-‎.bak‎ שלידו, ואחריו מהעותק בתיקיית הגיבוי, ומשחזרים ממנו
     * את הראשי (באטומיות; הראשי הפגום נשמר בצד כ-‎.bad‎). null — אין כניסה שנפתחת במחשב הזה
     */
    static Map<String, Object> load(final File f) {
        final SecretKeySpec key = storeKey();
        Map<String, Object> d = open(f, key);
        if (d != null) return d;
        for (final File b : new File[] { bakOf(f), backupOf(f) }) {
            if (b == null || !b.isFile()) continue;
            d = open(b, key);
            if (d == null) continue;
            try {
                lockedRun(new Runnable() {
                    @Override public void run() {
                        if (open(f, key) != null || open(b, key) == null) return;   // בינתיים תוכנה אחרת כבר תיקנה / כתבה
                        try {
                            if (f.isFile()) moveAtomic(f, new File(f.getParentFile(), f.getName() + ".bad"));
                            copyAtomic(b, f, !isStationFile(f));
                            if (isStationFile(f)) f.setReadable(true, false);
                            hide(f);
                        } catch (IOException ignored) {
                            // אין הרשאה לשחזר — קוראים מהגיבוי
                        }
                    }
                });
            } catch (RuntimeException ignored) {
                // קוראים מהגיבוי
            }
            return d;
        }
        return null;
    }

    static boolean save(final File f, final Map<String, Object> data) {
        try {
            return locked(new Callable<Boolean>() {
                @Override public Boolean call() { return saveLocked(f, data); }
            });
        } catch (Exception e) {
            lastError = String.valueOf(e);
            return false;
        }
    }

    private static boolean saveLocked(File f, Map<String, Object> data) {
        File tmp = null;
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
            outer.put("v", 1.0);   // גרסאות קודמות קוראות רק את data (ו-id לא מפריע להן) — אפשר גם לחזור לגרסה ישנה
            outer.put("data", Base64.getEncoder().encodeToString(all));
            if (identityText != null) outer.put("id", identityText);   // מזהה ההתקנה (כמו i.dat) — הקובץ נפתח גם בלי i.dat
            f.getParentFile().mkdirs();
            tmp = new File(f.getParentFile(), f.getName() + "." + Long.toHexString(RANDOM.nextLong() & Long.MAX_VALUE) + ".tmp");
            Files.write(tmp.toPath(), Json.write(outer).getBytes(StandardCharsets.UTF_8));
            boolean shared = isStationFile(f);
            if (!shared) ownerOnly(tmp);   // הכרטיס של המורה / התלמיד — רק המשתמש עצמו קורא (Mac / Linux: בלי זה הקובץ ‎0644‎)
            moveAtomic(tmp, f);
            if (shared) f.setReadable(true, false);   // עמדה: כל המשתמשים של המחשב קוראים
            hide(f);
            shareMachineDir(f.getParentFile());
            // הגיבויים: ‎.bak‎ ליד הקובץ, ועותק בתיקיית הגיבוי. כל אחד באטומיות; נכשל — הקובץ הראשי כבר נשמר
            for (File b : new File[] { bakOf(f), backupOf(f) }) {
                if (b == null) continue;
                try {
                    copyAtomic(f, b, !shared);
                    if (shared) b.setReadable(true, false);
                    hide(b);
                } catch (IOException ignored) {
                    // בלי הגיבוי הזה
                }
            }
            return true;
        } catch (Exception e) {
            lastError = String.valueOf(e);
            return false;
        } finally {
            if (tmp != null && tmp.exists()) tmp.delete();
        }
    }

    /** משלים גיבוי חסר / פגום של קובץ זהב תקין (קובץ מגרסה קודמת, שנשמר לפני שהיה ‎.bak‎). נקרא בבדיקה מול השרת */
    private static void ensureBackups(final File f) {
        try {
            lockedRun(new Runnable() {
                @Override public void run() {
                    SecretKeySpec key = storeKey();
                    if (open(f, key) == null) return;
                    boolean shared = isStationFile(f);
                    for (File b : new File[] { bakOf(f), backupOf(f) }) {
                        if (b == null) continue;
                        try {
                            if (sameContent(b, Files.readAllBytes(f.toPath()))) continue;
                            copyAtomic(f, b, !shared);
                            if (shared) b.setReadable(true, false);
                            hide(b);
                        } catch (IOException ignored) {
                            // בלי הגיבוי הזה
                        }
                    }
                }
            });
        } catch (RuntimeException ignored) {
            // לא חובה
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
        return keyOf(machineRaw);
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
        List<String> apps = new ArrayList<String>();
        for (Object o : Json.arr(p.get("apps"))) if (!Json.str(o).isEmpty()) apps.add(Json.str(o));
        return new Access(Json.str(p.get("kind")), Json.str(p.get("name")), Json.str(p.get("email")), Json.str(p.get("until")), Json.str(p.get("teacher")), apps);
    }

    /** מי מחובר עכשיו במחשב הזה — בלי אינטרנט. מורה / תלמיד (של המשתמש הזה) קודם, ואחר כך עמדה (של המחשב) */
    public static Access current() {
        Map<String, Object> u = load(userFile());
        Map<String, Object> p = u == null ? null : verifyPass(Json.str(u.get("pass")));
        if (p != null) {
            Access a = accessOf(p);
            // תלמיד משויך למורה: שם המורה (מהבדיקה האחרונה מול השרת — לא חלק מהאישור החתום; רק לתצוגה ול"הגשה למורה")
            String t = a.isStudent() && "teacher".equals(Json.str(u.get("studentMode"))) ? Json.str(u.get("studentTeacher")) : "";
            return t.isEmpty() ? a : new Access(a.kind, a.name, a.email, a.until, t, a.apps);
        }
        Map<String, Object> s = load(stationFile());
        p = s == null ? null : verifyPass(Json.str(s.get("pass")));
        return p == null ? null : accessOf(p);
    }

    /**
     * שורה קצרה ליד מספר הגרסה בתחתית החלונות: השם שנבחר למחשב (עמדת מחשב: השם שהמורה / המחבר נתנו לה, והמורה שלה)
     * ומי מחובר עם Google (מורה / תלמיד) — כך רואים מיד איך המחשב מחובר (למשל למה "עמדת ניהול" אפורה: לא מחובר כמורה).
     * "לא מחובר" — אין כניסה תקפה במחשב. נשמר לכמה שניות (החלונות בונים את השורה הרבה פעמים).
     */
    public static String computerLine() {
        long now = System.currentTimeMillis();
        if (now - lineAt < 3000 && lineCache != null) return lineCache;
        String out;
        try {
            Map<String, Object> st = load(stationFile());
            Map<String, Object> sp = st == null ? null : verifyPass(Json.str(st.get("pass")));
            Map<String, Object> u = load(userFile());
            Map<String, Object> up = u == null ? null : verifyPass(Json.str(u.get("pass")));
            StringBuilder b = new StringBuilder();
            if (sp != null) {
                Access a = accessOf(sp);
                b.append("עמדה: ").append(a.name.isEmpty() ? "בלי שם" : a.name);
                if (!a.teacher.isEmpty() && up == null) b.append(" (המורה: ").append(a.teacher).append(')');
            }
            if (up != null) {
                Access a = accessOf(up);
                String who = a.name.isEmpty() ? a.email : a.name;
                if (b.length() > 0) b.append(" · ");
                b.append(a.isTeacher() ? "מחובר כמורה: " : a.isStudent() ? "מחובר כתלמיד/ה: " : "מחובר: ").append(who);
            }
            out = b.length() > 0 ? b.toString() : "לא מחובר";
        } catch (Throwable e) {
            out = "";
        }
        lineCache = out;
        lineAt = now;
        return out;
    }

    private static volatile String lineCache;
    private static volatile long lineAt;

    /** אחרי כניסה / התנתקות / שינוי שם העמדה — השורה מחושבת מחדש בפעם הבאה */
    static void lineChanged() {
        lineAt = 0;
    }

    /**
     * עמדת מחשב בכיתה עובדת רק על תרגילים ממאגר השאלות: שליחת תרגיל חדש לבדיקה ב-AI (וההצעה לפתרון שלו) פתוחה רק למורים
     * ולתלמידים שמחוברים עם Google. השרת אוכף את זה (geminiGate); כאן — כדי לומר את זה מיד, לפני שממלאים את החלון.
     */
    public static final String STATION_AI_MESSAGE = "המחשב הזה מחובר כעמדת מחשב של הכיתה, ובעמדה עובדים על תרגילים ממאגר השאלות.\n\n"
            + "שליחת תרגיל חדש לבדיקה ב-AI (והצעה לפתרון) פתוחה למורים ולתלמידים שמחוברים עם Google, במחשב שלהם.";

    /** המחשב מחובר כעמדת מחשב, ואין עליו משתמש מחובר עם Google (מורה / תלמיד עם אישור תקף) */
    public static boolean stationOnly() {
        try {
            Map<String, Object> u = load(userFile());
            if (u != null && verifyPass(Json.str(u.get("pass"))) != null) return false;
            Access a = current();
            return a != null && a.isStation();
        } catch (Throwable e) {
            return false;   // לא הצלחנו לבדוק — השרת יחליט
        }
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

    /** העמדה של המחשב (אישור תקף בקובץ הזהב של העמדה), גם כשמעליה מחובר משתמש עם Google. null — המחשב לא עמדה */
    public static Access station() {
        Map<String, Object> s = load(stationFile());
        Map<String, Object> p = s == null ? null : verifyPass(Json.str(s.get("pass")));
        return p != null && "station".equals(Json.str(p.get("kind"))) ? accessOf(p) : null;
    }

    /** השרת אמר (בבדיקה האחרונה של הכרטיס הזה) שהוא יודע feature — station=true: הכרטיס של העמדה; false: של המשתמש */
    static boolean serverHas(boolean station, String feature) {
        Map<String, Object> d = load(station ? stationFile() : userFile());
        if (d == null) return false;
        for (Object o : Json.arr(d.get("features"))) if (feature.equals(Json.str(o))) return true;
        return false;
    }

    /**
     * הכותרות של כרטיס מסוים: station=true — של העמדה של המחשב; false — של המשתמש המחובר עם Google (מורה / תלמיד).
     * ריק — אין כרטיס כזה. ("הגשה למורה": תלמיד משויך מגיש בשם עצמו; בעמדת מחשב — בשם העמדה, גם אם מישהו מחובר מעליה)
     */
    static Map<String, String> headersOf(boolean station) {
        Map<String, String> h = new LinkedHashMap<String, String>();
        Map<String, Object> d = load(station ? stationFile() : userFile());
        String tok = d == null ? "" : Json.str(d.get("token"));
        if (!tok.isEmpty()) {
            h.put("X-DSViz-Access", tok);
            h.put("X-DSViz-Machine", machine());
        }
        return h;
    }

    // ------------------------------------------------------------------ מול השרת

    static Result post(String path, Map<String, Object> body) {
        return post(path, body, null);
    }

    /** כמו post, עם כותרות נוספות — הכרטיס של מי ששולח (headersOf), לפעולות של המשתמש / העמדה (הגשה למורה) */
    static Result post(String path, Map<String, Object> body, Map<String, String> extra) {
        String base = serverUrl();
        if (base.isEmpty()) return new Result("error", "כתובת השרת לא מוגדרת.", null);
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(base + path).openConnection();
            OsTrust.apply(c);   // סינון אינטרנט שבודק תעבורה מוצפנת (התעודה שלו במאגר של Windows)
            c.setConnectTimeout(10000);
            c.setReadTimeout(30000);
            c.setRequestMethod("POST");
            c.setDoOutput(true);
            c.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            c.setRequestProperty("Accept", "application/json");
            c.setRequestProperty("User-Agent", client.get());
            if (extra != null) for (Map.Entry<String, String> h : extra.entrySet()) c.setRequestProperty(h.getKey(), h.getValue());
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
            if (OsTrust.isTlsProblem(e)) return new Result("offline", OsTrust.TLS_MESSAGE, null);
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
    private static void store(final File f, final Result r, final String token) {
        lockedRun(new Runnable() {
            @Override public void run() { storeLocked(f, r, token); }
        });
    }

    /**
     * כמו store, אבל רק אם בקובץ עדיין אותו כרטיס: בדיקה ארוכה מול השרת בתוכנה אחת לא מחזירה כניסה שתוכנה אחרת ניתקה
     * בינתיים (או מחליפה כניסה חדשה של משתמש אחר)
     */
    private static void storeIfSame(final File f, final Result r, final String token) {
        lockedRun(new Runnable() {
            @Override public void run() {
                Map<String, Object> now = load(f);
                if (now != null && token.equals(Json.str(now.get("token")))) storeLocked(f, r, token);
            }
        });
    }

    /** כמו forget, אבל רק אם בקובץ עדיין אותו כרטיס */
    private static void forgetIfSame(final File f, final String token) {
        lockedRun(new Runnable() {
            @Override public void run() {
                Map<String, Object> now = load(f);
                if (now == null || token.equals(Json.str(now.get("token")))) forget(f);
            }
        });
    }

    private static void storeLocked(File f, Result r, String token) {
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
        // תלמיד: עצמאי / משויך למורה, והאם השלים הרשמה (שם מלא וטלפון) — כמו שהשרת ענה בבדיקה האחרונה
        Map<String, Object> st = Json.obj(r.body.get("student"));
        if (!st.isEmpty()) {
            Map<String, Object> mine = Json.obj(st.get("teacher"));
            d.put("studentMode", Json.str(st.get("mode")));
            d.put("studentTeacher", Json.str(mine.get("name")));
            d.put("studentSchool", Json.str(mine.get("school")));
            d.put("studentRegistered", Boolean.TRUE.equals(st.get("registered")));
            d.put("studentFullName", Json.str(st.get("fullName")));
        }
        // מה השרת יודע (features — למשל submit: "הגשה למורה"). שרת ישן לא שולח — רשימה ריקה, והכפתורים החדשים לא מוצגים
        List<Object> feats = new ArrayList<Object>();
        for (Object o : Json.arr(r.body.get("features"))) if (!Json.str(o).isEmpty()) feats.add(Json.str(o));
        d.put("features", feats);
        // "צריך לשלם": המחיר ועד מתי — למסך שמסביר (גם בלי אינטרנט בפתיחה הבאה)
        if ("pay".equals(r.status)) {
            d.put("payPrice", r.body.get("price"));
            d.put("payUntil", Json.str(r.body.get("accessUntil")));
            d.put("payOpen", Boolean.TRUE.equals(r.body.get("canPay")));
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

    /**
     * למורה שמחובר במחשב: כמה הגשות חדשות מחכות לו (body: students, newStudents, newStations). רק כשהשרת יודע ("summary");
     * אחרת / בלי אינטרנט — status שאינו ok, והחלון פשוט לא מציג את השורה
     */
    public static Result teacherSummary() {
        if (teacherInfo() == null || !serverHas(false, "summary")) return new Result("none", "", null);
        return post("/v1/teacher/summary", new LinkedHashMap<String, Object>(), headersOf(false));
    }

    /**
     * למורה שמחובר במחשב — לעמדת הניהול בתוכנה: מחשבי המעבדה שלו (body.stations: id, name, lastSeen, claimedAt), התלמידים
     * שמשויכים אליו (body.students: email, name, className, lastSubmitAt) ו-releasesLeft. רק כשהשרת יודע ("panel")
     */
    public static Result teacherPanel() {
        if (teacherInfo() == null || !serverHas(false, "panel")) return new Result("none", "", null);
        return post("/v1/teacher/panel", new LinkedHashMap<String, Object>(), headersOf(false));
    }

    /** המורה משחרר מחשב מעבדה (ההיתר חוזר אליו; המחשב יצטרך את קוד המורה כדי להתחבר שוב) */
    public static Result teacherReleaseStation(String id) {
        Map<String, Object> b = new LinkedHashMap<String, Object>();
        b.put("id", id);
        return post("/v1/teacher/station/release", b, headersOf(false));
    }

    /** המורה מסיר תלמיד מהשיוך (התלמיד נהיה עצמאי; ההגשות נשארות אצל המורה) */
    public static Result teacherReleaseStudent(String email) {
        Map<String, Object> b = new LinkedHashMap<String, Object>();
        b.put("email", email);
        return post("/v1/teacher/student/release", b, headersOf(false));
    }

    /** פותח את עמדת הניהול של המורה בדפדפן */
    public static void openManagement() {
        String u = managementUrl();
        if (!u.isEmpty()) browser.accept(u);
    }

    // ------------------------------------------------------------------ תלמיד: הרשמה, תשלום, עצמאי / משויך למורה

    /**
     * המצב של התלמיד שמחובר במחשב הזה, כמו שהשרת ענה בבדיקה האחרונה (או null — אין תלמיד): status (ok / register / pay),
     * mode (independent / teacher / ""), teacher, school, registered, fullName, email, ולמצב pay גם price / until / canPay
     */
    public static Map<String, Object> studentInfo() {
        Map<String, Object> u = load(userFile());
        if (u == null || !"student".equals(Json.str(u.get("role"))) || Json.str(u.get("token")).isEmpty()) return null;
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("status", Json.str(u.get("status")));
        m.put("email", Json.str(u.get("email")));
        m.put("mode", Json.str(u.get("studentMode")));
        m.put("teacher", Json.str(u.get("studentTeacher")));
        m.put("school", Json.str(u.get("studentSchool")));
        m.put("registered", Boolean.TRUE.equals(u.get("studentRegistered")));
        m.put("fullName", Json.str(u.get("studentFullName")));
        m.put("price", u.get("payPrice"));
        m.put("until", Json.str(u.get("payUntil")));
        m.put("canPay", Boolean.TRUE.equals(u.get("payOpen")));
        return m;
    }

    /** 10 התווים הראשונים של SHA-256 של המייל (באותיות קטנות) — כמו emailMark בדפים: מזהה את החשבון בלי לחשוף את המייל */
    static String emailMark(String email) {
        String e = email == null ? "" : email.trim().toLowerCase(java.util.Locale.ROOT);
        return e.isEmpty() ? "" : hex(sha256Bytes(e.getBytes(StandardCharsets.UTF_8))).substring(0, 10);
    }

    /** הדף בדפדפן שבו התלמיד המחובר משלים הרשמה ("register") או משלם ("pay") — עם הסימן של החשבון שלו */
    public static String studentPageUrl(String what) {
        String base = serverUrl();
        if (base.isEmpty()) return "";
        Map<String, Object> st = studentInfo();
        String mark = st == null ? "" : emailMark(Json.str(st.get("email")));
        return base + "/welcome?r=" + enc(what) + "&role=student" + (mark.isEmpty() ? "" : "&u=" + mark);
    }

    /**
     * התלמיד שמחובר במחשב הזה מצטרף למורה עם קוד המורה (מתוך התוכנה — בלי הדפדפן). replace — כבר משויך למורה אחר, ועובר.
     * status: ok (הצטרף; teacher().name) / bad-code / closed (המורה סגר את ההצטרפות) / blocked / replace (צריך אישור לעבור) /
     * rate / offline / login. אחרי הצלחה המצב השמור מתעדכן מהשרת — הכפתור "הגשה למורה" מופיע מיד
     */
    public static Result studentJoin(String code, boolean replace) {
        Map<String, String> h = studentInfo() == null ? new LinkedHashMap<String, String>() : headersOf(false);
        if (h.isEmpty()) return new Result("login", "ההצטרפות למורה היא לתלמיד שמחובר עם Google במחשב הזה.", null);
        Result r = post("/v1/student/join", map("code", code == null ? "" : code.trim(), "replace", replace), h);
        if (r.ok()) refresh();
        return r;
    }

    /**
     * התרגילים שהתלמיד המחובר הגיש למורה שלו, עם הציון שהמורה נתן (body.work: bankId, title, at, n, grade או null, again —
     * הגיש שוב אחרי הציון). רק כשהשרת יודע ("grades") ויש תלמיד משויך; אחרת status שאינו ok
     */
    public static Result studentWork() {
        Map<String, Object> st = studentInfo();
        if (st == null || Json.str(st.get("teacher")).isEmpty() || !serverHas(false, "grades")) return new Result("none", "", null);
        return post("/v1/student/work", new LinkedHashMap<String, Object>(), headersOf(false));
    }

    /** התלמיד שמחובר במחשב הזה יוצא מהמורה שלו (מה שכבר הוגש נשאר אצל המורה). אחרי הצלחה המצב השמור מתעדכן */
    public static Result studentLeave() {
        Map<String, String> h = studentInfo() == null ? new LinkedHashMap<String, String>() : headersOf(false);
        if (h.isEmpty()) return new Result("login", "הפעולה הזו לתלמיד שמחובר עם Google במחשב הזה.", null);
        Result r = post("/v1/student/leave", new LinkedHashMap<String, Object>(), h);
        if (r.ok()) refresh();
        return r;
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
        Result r = post("/v1/station/claim", map("code", code, "name", name, "machine", machine(), "stable", stableMachine(),
                "why", stationWhy(), "client", client.get(), "app", app, "caps", CAPS));
        if (r.ok()) {
            String token = Json.str(r.body.get("token"));
            // נשמר במקום הראשון שבאמת נכתב ונקרא בחזרה עם הכרטיס החדש: לכל המחשב, ואם אי אפשר (אין הרשאה, או קובץ ישן של
            // משתמש אחר שאי אפשר להחליף) — אצל המשתמש
            File saved = null;
            for (File f : stationFiles()) {
                store(f, r, token);
                Map<String, Object> d = load(f);
                if (d != null && token.equals(Json.str(d.get("token")))) { saved = f; break; }
            }
            r = checkSaved(r, saved != null ? saved : stationFiles()[0]);
            if (r.ok()) noteDrop("");
        }
        return r;
    }

    // ------------------------------------------------------------------ למה המחשב מבקש שוב קוד (ליומן של ההנהלה)

    /** הסיבה האחרונה שבגללה העמדה נמחקה מהמחשב (השרת דחה / שחרר), בקובץ קטן אצל המשתמש. ריק — מוחק */
    private static File dropFile() {
        return new File(secureUserDir(), plainDirs() ? "station-drop" : "d.dat");
    }

    private static void noteDrop(String why) {
        try {
            File f = dropFile();
            if (why == null || why.isEmpty()) { f.delete(); return; }
            f.getParentFile().mkdirs();
            Files.write(f.toPath(), (why + "\n" + System.currentTimeMillis() + "\n").getBytes(StandardCharsets.UTF_8));
            hide(f);
        } catch (Exception ignored) {
            // רק לאבחון
        }
    }

    /**
     * למה אין במחשב עמדה מחוברת (נשלח לשרת עם הקוד, ונרשם ביומן של ההנהלה): none — אין קובץ; unreadable — יש קובץ והוא לא
     * נפתח; pass — האישור שבו לא אומת; server-revoked / server-invalid — השרת שחרר / דחה (בחודש האחרון). "" — העמדה תקינה.
     */
    static String stationWhy() {
        try {
            File f = dropFile();
            if (f.isFile()) {
                String[] lines = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8).split("\\r?\\n");
                if (lines.length >= 2 && System.currentTimeMillis() - Long.parseLong(lines[1].trim()) < 30L * 86400000L && lines[0].matches("server-[a-z]+")) return lines[0];
            }
        } catch (Exception ignored) {
            // ממשיכים לבדיקת הקבצים
        }
        boolean any = false, opened = false;
        for (File f : stationFiles()) {
            boolean exists = false;
            for (File x : copiesOf(f)) exists |= x.isFile();
            if (!exists) continue;
            any = true;
            Map<String, Object> d = load(f);
            if (d == null) continue;
            opened = true;
            if (verifyPass(Json.str(d.get("pass"))) != null) return "";
        }
        return opened ? "pass" : any ? "unreadable" : "none";
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
                            "machineName", machineName(), "client", client.get(), "app", app, "caps", CAPS));
                    String token = Json.str(r.body.get("token"));
                    if (!token.isEmpty()) store(userFile(), r, token);
                    r = checkSaved(r, userFile());
                    result.set(r);
                    boolean known = r.status.matches("ok|pay|register|not-teacher|blocked|devices");
                    // u: סימן של החשבון שנכנס — דף ההרשמה / התשלום מוודא שבדפדפן מחובר אותו חשבון (בלי המייל עצמו בכתובת)
                    String mark = r.status.matches("pay|register") ? emailMark(Json.str(Json.obj(r.body.get("profile")).get("email"))) : "";
                    location = base + "/welcome?r=" + enc(known ? r.status : "error") + "&role=" + enc(role)
                            + (mark.isEmpty() ? "" : "&u=" + mark) + (known ? "" : "&m=" + enc(r.message));
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
            // classroom=1: הגרסה עם מרכז הבקרה — הדף מבקש מתלמיד חדש להירשם (שם מלא וטלפון) לפני החזרה לתוכנה
            String url = base + "/signin?role=" + enc(role) + "&port=" + server.getLocalPort() + "&state=" + enc(state) + "&challenge=" + enc(challenge) + "&classroom=1";
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
    private static final String ACCESS_STATES = "ok|pay|register|blocked";

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
            ensureBackups(uf);
            Result r = post("/v1/access/check", map("token", Json.str(u.get("token")), "machine", machine(), "caps", CAPS));
            refreshWith(uf, Json.str(u.get("token")), r);
            // כל דבר אחר (בלי אינטרנט, 429 / 500 / 404, תשובה לא תקינה) — תקלה רגעית: האישור השמור נשאר כמו שהוא
            last = r;
        }
        File sf = stationFile();
        Map<String, Object> s = load(sf);
        if (s != null && !Json.str(s.get("token")).isEmpty()) {
            ensureBackups(sf);
            Result r = post("/v1/access/check", map("token", Json.str(s.get("token")), "machine", machine(), "caps", CAPS));
            if (stationReleased(r)) {
                // כל קובצי העמדה של אותה עמדה (לכל המחשב, וההעתק אצל המשתמש) — אחרת העותק השני "מחזיר" אותה עד הבדיקה הבאה
                String released = stationIdOf(Json.str(s.get("token")));
                forgetIfSame(sf, Json.str(s.get("token")));
                for (File f : stationFiles()) {
                    Map<String, Object> d = load(f);
                    String t = d == null ? "" : Json.str(d.get("token"));
                    if (!t.isEmpty() && stationIdOf(t).equals(released)) forgetIfSame(f, t);
                }
                noteDrop("server-" + r.status);
            } else if (validState(r)) storeIfSame(sf, r, Json.str(s.get("token")));
            if (last == null || !last.ok()) last = r;
        }
        return last == null ? new Result("none", "", null) : last;
    }

    /**
     * העמדה שוחררה — רק אז קובץ הזהב של העמדה נמחק: השרת ענה (JSON תקין, ok:true) revoked = המורה / ההנהלה שחררו אותה
     * (או עמדה לא פעילה). כל דבר אחר משאיר את העמדה מחוברת:
     *   • invalid (בלי סיבה משרת ישן, או עם סיבה token / machine / gone): הכרטיס הוחלף בחיבור חוזר מאותו מחשב (משתמש Windows
     *     אחר, תוכנה אחרת), או שמזהה המחשב לא ברשימה של העמדה — העמדה עצמה לא שוחררה. האישור החתום שבקובץ ממשיך לפתוח את
     *     המאגר (עמדה לא שולחת תרגילים ל-AI, אז הכרטיס לא נדרש), ושחרור אמיתי מגיע בכל מקרה: השרת בודק "שוחררה" לפני הכרטיס;
     *   • בלי אינטרנט, 429 / 500 / 404, סינון אינטרנט / פורטל של רשת אורחים (תשובה לא תקינה) — תקלה רגעית (offline / error).
     */
    static boolean stationReleased(Result r) {
        return r != null && "revoked".equals(r.status);
    }

    /** המזהה של העמדה מתוך הכרטיס שלה (‎s.<id>.<secret>‎) */
    private static String stationIdOf(String token) {
        String[] p = token.split("\\.");
        return p.length >= 3 && "s".equals(p[0]) ? p[1] : token;
    }

    /** יש במחשב כניסה שמורה עם כרטיס (משתמש או עמדה) — גם אם האישור שבה פג (בלי אינטרנט זמן רב) / לא אומת עדיין */
    public static boolean hasSavedToken() {
        Map<String, Object> u = load(userFile());
        if (u != null && !Json.str(u.get("token")).isEmpty()) return true;
        Map<String, Object> s = load(stationFile());
        return s != null && !Json.str(s.get("token")).isEmpty();
    }

    /** המחשב מחובר כעמדת מחשב (אישור תקף בקובץ הזהב של העמדה) — בלי קשר למשתמש שאולי נכנס עם Google מעליה */
    public static boolean stationConnected() {
        Map<String, Object> s = load(stationFile());
        Map<String, Object> p = s == null ? null : verifyPass(Json.str(s.get("pass")));
        return p != null && "station".equals(Json.str(p.get("kind")));
    }

    /** המשתמש (מורה / תלמיד) מחובר עם Google: אישור תקף בקובץ הזהב שלו */
    public static boolean userSignedIn() {
        Map<String, Object> u = load(userFile());
        return u != null && verifyPass(Json.str(u.get("pass"))) != null;
    }

    /**
     * מעדכן את הכרטיס של המשתמש לפי תשובת השרת — רק אם הוא עדיין אותו כרטיס (storeIfSame / forgetIfSame). נמחק רק על תשובה
     * אמיתית של השרת שהכניסה בוטלה: revoked (התנתקות ממקום אחר / "ניתוק מחשב" בדפדפן / ההנהלה), invalid (אין חיבור כזה בשרת),
     * not-teacher (ההנהלה הורידה את הרשאת המורה)
     */
    static void refreshWith(File uf, String token, Result r) {
        if (r.status.matches("revoked|invalid|not-teacher")) forgetIfSame(uf, token);
        else if (validState(r)) storeIfSame(uf, r, token);
        // כל דבר אחר (בלי אינטרנט, 429 / 500 / 404, תשובה לא תקינה) — תקלה רגעית: האישור השמור נשאר כמו שהוא
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

    // ------------------------------------------------------------------ שינויים מתוכנה אחרת (AccessWatch)

    /** התיקיות שבהן נשמרת הכניסה (למעקב אחרי שינויים של תוכנה אחרת) */
    static List<File> watchedDirs() {
        List<File> out = new ArrayList<File>();
        for (File f : watchedFiles()) {
            File d = f.getParentFile();
            if (d != null && !out.contains(d)) out.add(d);
        }
        return out;
    }

    /** הקבצים שקובעים מי מחובר: המשתמש (מורה / תלמיד) והעמדה — בלי לגעת בהם (בלי העברה מהמקום הישן) */
    static List<File> watchedFiles() {
        List<File> out = new ArrayList<File>();
        if (plainDirs()) {
            out.add(new File(userDir(), USER_FILE));
            out.add(new File(machineDir(), STATION_FILE));
            out.add(new File(userDir(), STATION_FILE));
        } else {
            out.add(new File(secureUserDir(), USER_DAT));
            out.add(new File(secureMachineDir(), STATION_DAT));
            out.add(new File(secureUserDir(), STATION_DAT));
        }
        return out;
    }

    /**
     * "מי מחובר" בשורה אחת (בלי הכרטיס): משתנה רק כשהכניסה באמת משתנה — לא בכל בדיקה מול השרת (שמצפינה מחדש את אותו קובץ)
     */
    static String fingerprint() {
        Access a = current();
        Map<String, Object> u = userState();
        StringBuilder b = new StringBuilder();
        if (a != null) b.append(a.kind).append('|').append(a.email).append('|').append(a.name).append('|').append(a.until).append('|').append(a.teacher);
        b.append("||");
        if (u != null) {
            b.append(Json.str(u.get("status"))).append('|').append(Json.str(u.get("role"))).append('|').append(Json.str(u.get("email")))
                    .append('|').append(Json.str(u.get("token")).isEmpty() ? "" : hex(sha256(Json.str(u.get("token")))).substring(0, 12));
        }
        return b.toString();
    }

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

    private static void pendingLogout(final String tok, final boolean add) {
        lockedRun(new Runnable() {
            @Override public void run() { pendingLogoutLocked(tok, add); }
        });
    }

    private static void pendingLogoutLocked(String tok, boolean add) {
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
     * סימון (לא סוד — רק מייל ושם, אף פעם לא הכרטיס) שהמשתמש של Windows מחובר כמורה: ‎%LOCALAPPDATA%\Lamerhav\.cache\teacher.flag‎ —
     * באותה תיקייה נסתרת של כל קובצי הזיהוי (עד גרסה 1.0.1: ‎%LOCALAPPDATA%\DSViz\teacher.flag‎ — נמחק כאן; הסרגל קורא גם משם, לגרסה אחת).
     * סרגל הכלים של Visual Studio (C#, לא יכול לפתוח את הקבצים המוצפנים) מציג את "עמדת ניהול" רק כשהוא קיים. הסימון הוא רק
     * רמז לממשק — עמדת הניהול עצמה בודקת שוב את האישור החתום ואת השרת. null — אין סרגל של Visual Studio (Mac / Linux)
     */
    static File teacherFlagFile() {
        String d = System.getProperty("dsviz.statusDir");
        if (d != null && !d.isEmpty()) return new File(d, "teacher.flag");
        if (plainDirs()) return new File(userDir(), "teacher.flag");
        if (!windows()) return null;
        return new File(secureUserDir(), "teacher.flag");
    }

    /** המקום הישן של הסימון (עד גרסה 1.0.1), או null */
    static File legacyTeacherFlagFile() {
        if (System.getProperty("dsviz.statusDir") != null || plainDirs() || !windows()) return null;
        String la = env("LOCALAPPDATA");
        return la == null || la.isEmpty() ? null : new File(new File(la, "DSViz"), "teacher.flag");
    }

    /** מעדכן את הסימון לפי מי שמחובר עכשיו (נקרא אחרי כל שמירה / מחיקה של כניסה, ובפתיחת הכלים) */
    public static void syncTeacherFlag() {
        try {
            File old = legacyTeacherFlagFile();
            if (old != null && old.exists()) old.delete();   // עבר לתיקייה הנסתרת
            File f = teacherFlagFile();
            if (f == null) return;
            Map<String, Object> t = teacherInfo();
            if (t == null) {
                if (f.exists()) f.delete();
                return;
            }
            if (System.getProperty("dsviz.statusDir") != null && !f.getParentFile().isDirectory()) return;   // בדיקות: בלי תיקייה — בלי סימון
            f.getParentFile().mkdirs();
            byte[] text = Json.write(map("email", t.get("email"), "name", t.get("name"))).getBytes(StandardCharsets.UTF_8);
            if (f.isFile() && java.util.Arrays.equals(Files.readAllBytes(f.toPath()), text)) return;   // בלי שינוי — הסרגל לא מתעדכן סתם
            Files.write(f.toPath(), text);
            hide(f);
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
        MIGRATE_FAILED.clear();
        identityText = null;
        machineRaw = null;
        machineHash = null;
        machineOsReal = null;
    }

    static String lastError() {
        return lastError;
    }

    /** לבדיקות: איפה נשמרת העמדה ואיפה המשתמש (המקומות הנסתרים, או אלה של dsviz.home / dsviz.machineDir) */
    static String stationFileForTest() {
        return stationFile() + " | " + userFile();
    }

    /** לבדיקות: קובצי הזהב (העמדה לכל המחשב, העמדה אצל המשתמש, המשתמש) עם הגיבויים שלהם */
    static List<File> goldenForTest() {
        List<File> out = new ArrayList<File>();
        for (File f : stationFiles()) out.addAll(copiesOf(f));
        out.addAll(copiesOf(userFile()));
        return out;
    }
}
