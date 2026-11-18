package il.lamerhav.dsviz;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

/**
 * "הגשה למורה": שולחים למורה את קובצי התרגיל (POST /v1/submit) — מתלמיד שמשויך למורה (הצטרף בקוד המורה), או מעמדת מחשב
 * של הכיתה (אז חובה "שם המגיש", כי אותו מחשב משמש כמה תלמידים). למי זה מגיע קובע השרת, לפי כרטיס הכניסה של המחשב —
 * לא לפי מה שהמחשב שולח. תלמיד עצמאי, מורה, ומחשב לא מחובר — אין למי להגיש (mode() ריק, והכפתור לא מוצג).
 *
 * בלי אינטרנט ההגשה נשמרת במחשב (תיבת יוצא, ליד קובצי הכניסה — לא נמחקת בעדכון גרסה) ונשלחת בפעם הבאה שיש חיבור
 * (flushAsync — בפתיחת המאגר, ואחרי כל הגשה). הגשה שמורה נשלחת רק בשם מי שהגיש אותה: אם בינתיים התחבר במחשב מישהו אחר,
 * היא מחכה; אחרי KEEP_DAYS ימים היא נמחקת.
 */
public final class Submit {
    private Submit() { }

    public static final int MAX_FILES = 12, MAX_BYTES = 200 * 1024, MAX_OUTPUT = 2000, WHO_MIN = 2, WHO_MAX = 40;
    static final int MAX_QUEUE = 30, KEEP_DAYS = 14;
    private static final Object LOCAL = new Object();

    /** מה מגישים */
    public static final class Work {
        /** bank — תרגיל ממאגר השאלות; ai — תרגיל שנשלח לבדיקה ב-AI; own — תרגיל של התלמיד */
        public String source = "own";
        public String bankId = "", topic = "", title = "", level = "";
        /** התוכנה: Eclipse / IntelliJ / Visual Studio / VS Code */
        public String app = "";
        /** בעמדת מחשב: "שם המגיש" (חובה, 2–40 תווים) */
        public String who = "";
        /** הקובץ שנפתח ראשון אצל המורה (עם main) */
        public String main = "";
        /** קובצי הקוד: שם הקובץ → התוכן (רק ‎.java / ‎.cs‎) */
        public final Map<String, String> files = new LinkedHashMap<String, String>();
        /** תוצאת בדיקת ההרצה (RunCheck.toMap), או null — לא נבדק */
        public Map<String, Object> check;
    }

    /**
     * באיזו זהות מגישים מהמחשב הזה: "student" — תלמיד שמשויך למורה; "station" — עמדת מחשב של הכיתה; "" — אין למי להגיש.
     * רק כשהשרת אמר שהוא יודע לקבל הגשות (features: submit) — מול שרת ישן אין כפתור
     */
    public static String mode() {
        try {
            Account.Access a = Account.current();
            if (a != null && a.isTeacher()) return "";                               // מורה לא מגיש לעצמו
            if (a != null && a.isStudent() && !a.teacher.isEmpty()) return Account.serverHas(false, "submit") ? "student" : "";
            if (Account.station() != null && Account.serverHas(true, "submit")) return "station";
        } catch (Throwable ignored) {
            // לא הצלחנו לקרוא את הכניסה — בלי כפתור
        }
        return "";
    }

    public static boolean available() {
        return !mode().isEmpty();
    }

    /** שם המורה שמקבל את ההגשה (כמו שנשמר בבדיקה האחרונה מול השרת), או "" */
    public static String teacher() {
        try {
            String m = mode();
            if ("student".equals(m)) return Account.current().teacher;
            if ("station".equals(m)) return Account.station().teacher;
        } catch (Throwable ignored) {
            // בלי שם
        }
        return "";
    }

    /** "הגשה למורה שרה כהן" / "הגשה למורה" — הכיתוב של הכפתור */
    public static String label() {
        String t = teacher();
        return t.isEmpty() ? "הגשה למורה" : "הגשה למורה " + t;
    }

    /** "שם המגיש" תקין (2–40 תווים) */
    public static boolean whoOk(String who) {
        String w = who == null ? "" : who.trim();
        int n = w.codePointCount(0, w.length());
        return n >= WHO_MIN && n <= WHO_MAX;
    }

    private static Account.Result fail(String code, String message) {
        return new Account.Result(code, message, null);
    }

    /** מה לא בסדר בהגשה (לפני השליחה), או null */
    static Account.Result invalid(Work w, String mode) {
        if (mode.isEmpty()) return fail("no-teacher", "אין למי להגיש מהמחשב הזה. תלמיד מצטרף למורה עם קוד המורה (בדף \"החשבון שלי\"), ובכיתה מחברים את המחשב כעמדה.");
        if ("station".equals(mode) && !whoOk(w.who)) return fail("who", "כתבו את השם שלכם בשדה \"שם המגיש\" (לפחות 2 אותיות).");
        if (w.files.isEmpty()) return fail("files", "אין קובצי קוד להגשה.");
        if (w.files.size() > MAX_FILES) return fail("files", "עד " + MAX_FILES + " קבצים בהגשה אחת.");
        long size = 0;
        for (Map.Entry<String, String> f : w.files.entrySet()) {
            if (!f.getKey().toLowerCase(Locale.ROOT).matches(".*\\.(java|cs)")) return fail("files", "אפשר להגיש רק קובצי ‎.java / ‎.cs");
            size += (f.getValue() == null ? "" : f.getValue()).getBytes(StandardCharsets.UTF_8).length;
        }
        if (size > MAX_BYTES) return fail("files", "ההגשה גדולה מדי (עד 200KB של קוד).");
        return null;
    }

    static Map<String, Object> body(Work w, String mode) {
        Map<String, Object> ex = new LinkedHashMap<String, Object>();
        ex.put("source", w.source);
        ex.put("bankId", w.bankId);
        ex.put("topic", w.topic);
        ex.put("title", w.title);
        ex.put("level", w.level);
        List<Object> files = new ArrayList<Object>();
        for (Map.Entry<String, String> f : w.files.entrySet()) {
            Map<String, Object> one = new LinkedHashMap<String, Object>();
            one.put("path", f.getKey());
            one.put("text", f.getValue() == null ? "" : f.getValue());
            files.add(one);
        }
        Map<String, Object> b = new LinkedHashMap<String, Object>();
        b.put("exercise", ex);
        b.put("files", files);
        b.put("main", w.main);
        b.put("app", w.app);
        if ("station".equals(mode)) b.put("who", w.who.trim());
        if (w.check != null) b.put("check", w.check);
        return b;
    }

    /**
     * מגיש (חוסם — קוראים מ-thread ברקע). status: ok — הוגש (body: at, replaced, teacher); queued — אין אינטרנט, נשמר ויישלח
     * לבד; who / files / no-teacher — מה לתקן (message בעברית); rate / login / pay / blocked / error — הודעת השרת.
     */
    public static Account.Result send(Work w) {
        String mode = mode();
        Account.Result bad = invalid(w, mode);
        if (bad != null) return bad;
        Map<String, Object> body = body(w, mode);
        Account.Result r = Account.post("/v1/submit", body, Account.headersOf("station".equals(mode)));
        if ("offline".equals(r.status)) {
            return queue(body, mode) ? new Account.Result("queued", "אין כרגע חיבור לאינטרנט. ההגשה נשמרה במחשב, ותישלח למורה לבד בפעם הבאה שיש חיבור.", null) : r;
        }
        return r;
    }

    /** השעה (לפי השעון של המחשב) של זמן שהשרת החזיר — ל"הוגש למורה ב-10:42" */
    public static String timeOf(String iso) {
        try {
            SimpleDateFormat in = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.ROOT);
            in.setTimeZone(TimeZone.getTimeZone("UTC"));
            return new SimpleDateFormat("H:mm", Locale.ROOT).format(in.parse(iso.length() >= 19 ? iso.substring(0, 19) : iso));
        } catch (Exception e) {
            return new SimpleDateFormat("H:mm", Locale.ROOT).format(new Date());
        }
    }

    /** ההודעה לתלמיד אחרי הגשה */
    public static String doneText(Account.Result r) {
        if ("queued".equals(r.status)) return r.message;
        String at = timeOf(Json.str(r.body.get("at")));
        return "הוגש למורה ב-" + at + (Boolean.TRUE.equals(r.body.get("replaced")) ? " (במקום ההגשה הקודמת של התרגיל הזה)." : ".");
    }

    // ------------------------------------------------------------------ תיבת היוצא (בלי אינטרנט)

    static File outbox() {
        String d = System.getProperty("dsviz.submits");
        return d != null && !d.isEmpty() ? new File(d) : new File(Account.secureUserDir(), "submits");
    }

    /** מי מגיש עכשיו מהמחשב הזה: "student:<מייל>" / "station:<מזהה העמדה>" — הגשה שמורה נשלחת רק בשם מי שהגיש אותה */
    static String identity(String mode) {
        try {
            if ("student".equals(mode)) return "student:" + Account.current().email;
            if ("station".equals(mode)) {
                String tok = Account.headersOf(true).get("X-DSViz-Access");
                String[] p = tok == null ? new String[0] : tok.split("\\.");
                return p.length >= 3 ? "station:" + p[1] : "";
            }
        } catch (Throwable ignored) {
            // בלי זהות — לא שומרים
        }
        return "";
    }

    private static List<File> pending(File dir) {
        File[] all = dir.listFiles((d, n) -> n.endsWith(".json"));
        List<File> out = all == null ? new ArrayList<File>() : new ArrayList<File>(Arrays.asList(all));
        java.util.Collections.sort(out);
        return out;
    }

    /** כמה הגשות מחכות לאינטרנט */
    public static int waiting() {
        return pending(outbox()).size();
    }

    static boolean queue(Map<String, Object> body, String mode) {
        String by = identity(mode);
        if (by.isEmpty()) return false;
        Map<String, Object> q = new LinkedHashMap<String, Object>();
        q.put("by", by);
        q.put("mode", mode);
        q.put("at", (double) System.currentTimeMillis());
        q.put("body", body);
        synchronized (LOCAL) {
            try {
                File dir = outbox();
                if (!dir.isDirectory() && !dir.mkdirs()) return false;
                String id = new SimpleDateFormat("yyyyMMddHHmmssSSS", Locale.ROOT).format(new Date()) + "-" + Long.toHexString(System.nanoTime() & 0xffffff);
                File tmp = new File(dir, id + ".tmp");
                Files.write(tmp.toPath(), Json.write(q).getBytes(StandardCharsets.UTF_8));
                Files.move(tmp.toPath(), new File(dir, id + ".json").toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                List<File> all = pending(dir);
                for (int i = 0; i < all.size() - MAX_QUEUE; i++) all.get(i).delete();   // הכי ישנות
                return true;
            } catch (IOException | RuntimeException e) {
                return false;
            }
        }
    }

    /** שולח את מה שמחכה (חוסם). מחזיר כמה נשלחו */
    static int flush() {
        int sent = 0;
        synchronized (LOCAL) {
            File dir = outbox();
            for (File f : pending(dir)) {
                Map<String, Object> q;
                try {
                    q = Json.obj(Json.parse(new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8)));
                } catch (IOException | RuntimeException e) {
                    f.delete();   // קובץ פגום
                    continue;
                }
                Object at = q.get("at");
                long age = System.currentTimeMillis() - (at instanceof Number ? ((Number) at).longValue() : 0L);
                if (age > KEEP_DAYS * 24L * 3600 * 1000) { f.delete(); continue; }
                String mode = Json.str(q.get("mode"));
                String now = identity(mode);
                if (now.isEmpty() || !now.equals(Json.str(q.get("by")))) continue;   // עכשיו מחובר מישהו אחר (או אף אחד) — ההגשה מחכה למי שהגיש אותה
                Account.Result r = Account.post("/v1/submit", Json.obj(q.get("body")), Account.headersOf("station".equals(mode)));
                if (r.ok()) { f.delete(); sent++; continue; }
                if ("offline".equals(r.status) || "rate".equals(r.status) || "error".equals(r.status)) break;   // ננסה בפעם הבאה
                f.delete();   // השרת דחה (who / files / no-teacher / login …) — לא יעבור גם בפעם הבאה
            }
        }
        return sent;
    }

    private static volatile boolean flushing;

    /** שולח ברקע את מה שמחכה (בפתיחת המאגר, ואחרי הגשה) */
    public static void flushAsync() {
        if (flushing || waiting() == 0) return;
        flushing = true;
        Thread t = new Thread(() -> {
            try { flush(); } catch (Throwable ignored) { /* בפעם הבאה */ } finally { flushing = false; }
        }, "dsviz-submit-flush");
        t.setDaemon(true);
        t.start();
    }
}
