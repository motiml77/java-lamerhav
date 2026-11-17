package il.lamerhav.dsviz;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.SecureRandom;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * דיווחים על בעיות לצוות DSViz — "דיווח על הבעיה" (המשתמש לחץ) ודיווח אוטומטי (התוסף זיהה בעיה והריץ בדיקה מעמיקה).
 *
 * כל דיווח נשמר קודם במחשב, בתיבת יוצא — קובץ JSON לכל דיווח — ונשלח לשרת (POST /v1/report) כשיש אינטרנט: מיד, ואם אין חיבור —
 * בפעם הבאה שהתוכנה נפתחת (flushAsync), או כשמישהו מוסיף דיווח. נשלח — הקובץ נמחק. השרת לא שומר פעמיים את אותו id.
 *
 * תיבת היוצא משותפת לכל התוכנות (Eclipse, IntelliJ, Visual Studio — שם התוסף ב-C# כותב את הקובץ באותו פורמט ומריץ "flush"):
 *   Windows ‎%LOCALAPPDATA%\DSViz\reports\outbox‎ · Mac / Linux ‎~/.dsviz/reports/outbox‎ (לבדיקות: ‎-Ddsviz.reports=<תיקייה>‎).
 *
 * פרטיות: נשלחים הודעת השגיאה ופרטים טכניים (גרסאות, הגדרות, יומנים) — לא הקוד של התלמיד. תיקיית הבית ושם המשתמש של
 * Windows / Mac מוחלפים ב-‎%USERPROFILE%‎ / ‎%USERNAME%‎ לפני שהדיווח נשמר. המי (מורה / עמדה / תלמיד) נקבע בשרת לפי
 * כרטיס הכניסה של המחשב (Account.headers) — אותו כרטיס שכבר נשלח בכל בקשה.
 *
 *   java -cp <jar> il.lamerhav.dsviz.Reports flush            — שולח את מה שבתיבת היוצא; מדפיס "SENT n LEFT m"
 *   java -cp <jar> il.lamerhav.dsviz.Reports queue <file.json> — מוסיף דיווח (אובייקט JSON) ושולח
 */
public final class Reports {
    private Reports() { }

    static final int MAX_FILES = 50;            // תיבת יוצא מלאה (מחשב בלי אינטרנט הרבה זמן) — נמחקים הישנים
    static final int MAX_DIAG = 300 * 1024;
    private static final SecureRandom RND = new SecureRandom();
    private static final Object LOCAL = new Object();

    public static File outbox() {
        String d = System.getProperty("dsviz.reports");
        if (d != null && !d.isEmpty()) return new File(d);
        String la = System.getenv("LOCALAPPDATA");
        if (System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows") && la != null && !la.isEmpty())
            return new File(new File(new File(la, "DSViz"), "reports"), "outbox");
        return new File(new File(new File(System.getProperty("user.home"), ".dsviz"), "reports"), "outbox");
    }

    static String newId() {
        byte[] b = new byte[18];
        RND.nextBytes(b);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }

    static String isoNow() {
        SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ROOT);
        f.setTimeZone(TimeZone.getTimeZone("UTC"));
        return f.format(new Date());
    }

    /** מחליף את תיקיית הבית ואת שם המשתמש במחשב ב-‎%USERPROFILE%‎ / ‎%USERNAME%‎ (בלי קשר לאותיות גדולות / לוכסנים) */
    public static String mask(String s) {
        if (s == null || s.isEmpty()) return s == null ? "" : s;
        String home = System.getProperty("user.home", "");
        String user = System.getProperty("user.name", "");
        String out = s;
        if (home.length() > 3) {
            for (String h : new String[] { home, home.replace('\\', '/'), home.replace('/', '\\') }) {
                out = Pattern.compile(Pattern.quote(h), Pattern.CASE_INSENSITIVE).matcher(out).replaceAll(Matcher.quoteReplacement("%USERPROFILE%"));
            }
        }
        if (user.length() >= 3 && !user.equalsIgnoreCase("root") && !user.equalsIgnoreCase("user") && !user.equalsIgnoreCase("admin")) {
            out = Pattern.compile("(?<![\\p{L}\\p{N}])" + Pattern.quote(user) + "(?![\\p{L}\\p{N}])", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE)
                    .matcher(out).replaceAll(Matcher.quoteReplacement("%USERNAME%"));
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private static Object maskDeep(Object v) {
        if (v instanceof String) return mask((String) v);
        if (v instanceof Map) {
            Map<String, Object> m = new LinkedHashMap<String, Object>();
            for (Map.Entry<String, Object> e : ((Map<String, Object>) v).entrySet()) m.put(e.getKey(), maskDeep(e.getValue()));
            return m;
        }
        return v;
    }

    /**
     * מוסיף דיווח לתיבת היוצא (משלים id, occurredAt, os, app) ומנסה לשלוח ברקע. מחזיר את ה-id.
     * שדות: kind (visual-run / crash / diagnose / manual), auto, title, message, note, app, appVersion, dsvizVersion, context{…}, diag
     */
    public static String queue(Map<String, Object> report) {
        String id = queueOnly(report);
        flushAsync();
        return id;
    }

    /** כמו queue, בלי לשלוח */
    @SuppressWarnings("unchecked")
    static String queueOnly(Map<String, Object> report) {
        Map<String, Object> r = (Map<String, Object>) maskDeep(report == null ? new LinkedHashMap<String, Object>() : report);
        String id = Json.str(r.get("id"));
        if (!id.matches("[A-Za-z0-9_-]{16,64}")) { id = newId(); r.put("id", id); }
        if (Json.str(r.get("occurredAt")).isEmpty()) r.put("occurredAt", isoNow());
        if (Json.str(r.get("os")).isEmpty()) r.put("os", System.getProperty("os.name", "") + " " + System.getProperty("os.version", "") + " · Java " + System.getProperty("java.version", ""));
        if (Json.str(r.get("app")).isEmpty()) r.put("app", Account.client.get());
        String diag = Json.str(r.get("diag"));
        if (diag.length() > MAX_DIAG) r.put("diag", diag.substring(0, MAX_DIAG));
        synchronized (LOCAL) {
            try {
                File dir = outbox();
                if (!dir.isDirectory() && !dir.mkdirs()) return id;
                File tmp = new File(dir, id + ".tmp");
                Files.write(tmp.toPath(), Json.write(r).getBytes(StandardCharsets.UTF_8));
                String name = new SimpleDateFormat("yyyyMMddHHmmssSSS", Locale.ROOT).format(new Date()) + "-" + id + ".json";
                Files.move(tmp.toPath(), new File(dir, name).toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                List<File> all = pending(dir);
                for (int i = 0; i < all.size() - MAX_FILES; i++) all.get(i).delete();   // הכי ישנים
            } catch (IOException | RuntimeException e) {
                // הדיווח לא נשמר — לא מפילים את התוכנה בגלל דיווח
            }
        }
        return id;
    }

    static List<File> pending(File dir) {
        File[] fs = dir.listFiles((d, n) -> n.endsWith(".json"));
        List<File> out = new ArrayList<File>(fs == null ? new ArrayList<File>() : Arrays.asList(fs));
        out.sort((a, b) -> a.getName().compareTo(b.getName()));
        return out;
    }

    /** כמה דיווחים מחכים לשליחה */
    public static int pendingCount() {
        File dir = outbox();
        return dir.isDirectory() ? pending(dir).size() : 0;
    }

    /** שולח ברקע (לא מחכה) */
    public static void flushAsync() {
        Thread t = new Thread(() -> { try { flush(); } catch (RuntimeException ignored) { } }, "dsviz-reports");
        t.setDaemon(true);
        t.start();
    }

    /**
     * שולח את כל מה שבתיבת היוצא, מהישן לחדש. מחזיר {נשלחו, נשארו}. אין חיבור / השרת עמוס — עוצרים ומנסים בפעם הבאה.
     * דיווח שהשרת דחה (לא תקין) — נמחק, כדי שלא יתקע את השאר. תוכנה אחרת שכבר שולחת עכשיו (נעילה על הקובץ .lock) — לא שולחים פעמיים.
     */
    public static int[] flush() {
        File dir = outbox();
        if (!dir.isDirectory()) return new int[] { 0, 0 };
        String base = Account.serverUrl();
        if (base.isEmpty()) return new int[] { 0, pending(dir).size() };
        int sent = 0;
        try (RandomAccessFile lf = new RandomAccessFile(new File(dir, ".lock"), "rw"); FileLock lock = lf.getChannel().tryLock()) {
            if (lock == null) return new int[] { 0, pending(dir).size() };
            for (File f : pending(dir)) {
                String body;
                try { body = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8); } catch (IOException e) { continue; }
                int code = post(base + "/v1/report", body);
                if (code == 200) { f.delete(); sent++; continue; }
                if (code >= 400 && code < 500 && code != 408 && code != 429) { f.delete(); continue; }   // דיווח פגום — לא ייתקע
                break;   // אין אינטרנט / השרת עמוס — בפעם הבאה
            }
        } catch (IOException | RuntimeException e) {
            // לא הצלחנו לנעול / לקרוא — בפעם הבאה
        }
        return new int[] { sent, pending(dir).size() };
    }

    /** POST עם כרטיס הכניסה של המחשב (אם יש). מחזיר את קוד התשובה, או 0 כשאין חיבור */
    static int post(String url, String body) {
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(url).openConnection();
            OsTrust.apply(c);
            c.setConnectTimeout(10000);
            c.setReadTimeout(30000);
            c.setRequestMethod("POST");
            c.setDoOutput(true);
            c.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            c.setRequestProperty("Accept", "application/json");
            c.setRequestProperty("User-Agent", Account.client.get());
            for (Map.Entry<String, String> h : Account.headers().entrySet()) c.setRequestProperty(h.getKey(), h.getValue());
            byte[] out = body.getBytes(StandardCharsets.UTF_8);
            try (OutputStream o = c.getOutputStream()) { o.write(out); }
            int code = c.getResponseCode();
            InputStream in = code >= 400 ? c.getErrorStream() : c.getInputStream();
            String text = in == null ? "" : readAll(in);
            // תשובה של השרת שלנו היא JSON עם ok:true — דף של סינון אינטרנט / רשת אורחים הוא לא "התקבל"
            if (code == 200 && !text.contains("\"ok\":true")) return 0;
            return code;
        } catch (IOException e) {
            return 0;
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

    public static void main(String[] a) throws Exception {
        if (a.length >= 2 && a[0].equals("queue")) {
            String text = new String(Files.readAllBytes(new File(a[1]).toPath()), StandardCharsets.UTF_8);
            String id = queueOnly(Json.obj(Json.parse(text)));
            System.out.println("QUEUED " + id);
        }
        if (a.length >= 1 && (a[0].equals("flush") || a[0].equals("queue"))) {
            int[] r = flush();
            System.out.println("SENT " + r[0] + " LEFT " + r[1]);
            return;
        }
        System.out.println("שימוש: Reports flush | Reports queue <file.json>");
    }
}
