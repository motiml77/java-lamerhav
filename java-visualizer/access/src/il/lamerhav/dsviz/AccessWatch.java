package il.lamerhav.dsviz;

import java.io.File;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

/**
 * כניסה / התנתקות בתוכנה אחרת (Eclipse, IntelliJ, Visual Studio, "מודלים חישוביים") — כל התוכנות קוראות את אותם קבצים
 * בתיקייה הנסתרת (Account), ומי שמאזין כאן שומע על כל שינוי תוך רגע, בלי קשר למי שעשה אותו.
 *
 *   AccessWatch.listen(() -> ...)   // נקרא בחוט של המעקב (לא בחוט של Swing); AccessGate.onChange — בחוט של Swing
 *
 * המעקב: WatchService על התיקיות (מיד, ב-Windows וב-Linux), ובנוסף בדיקה קלה כל ‎1.5‎ שניות (Mac, תיקייה שעוד לא קיימת,
 * כונן רשת). מודיעים רק כשמי שמחובר באמת השתנה (Account.fingerprint) — לא על כל שמירה מחדש של אותה כניסה.
 */
public final class AccessWatch {
    private AccessWatch() {}

    static final long POLL_MS = 1500;
    /** גם בלי שינוי בקבצים: אישור שפג תוקפו */
    static final long RECHECK_MS = 60000;

    private static final List<Runnable> LISTENERS = new CopyOnWriteArrayList<Runnable>();
    private static volatile String last;
    private static final Object CHECK = new Object();
    private static Thread thread;

    /** מאזין לכל שינוי בכניסה במחשב הזה (גם של תוכנה אחרת). מתחיל את המעקב בפעם הראשונה */
    public static synchronized void listen(Runnable r) {
        LISTENERS.add(r);
        if (thread == null) {
            last = safeFingerprint();
            thread = new Thread(AccessWatch::loop, "dsviz-access-watch");
            thread.setDaemon(true);
            thread.start();
        }
    }

    public static void remove(Runnable r) {
        LISTENERS.remove(r);
    }

    /** אחרי שינוי שהתוכנה עצמה כבר הודיעה עליו (AccessGate.changed) — כדי שלא יודיעו עליו שוב */
    static void baseline() {
        last = safeFingerprint();
    }

    /** בודק עכשיו (בלי לחכות למעקב) ומודיע אם השתנה */
    public static void checkNow() {
        synchronized (CHECK) {
            String f = safeFingerprint();
            if (f.equals(last)) return;
            last = f;
        }
        for (Runnable r : LISTENERS) {
            try { r.run(); } catch (RuntimeException ignored) { /* מאזין שנכשל לא עוצר את האחרים */ }
        }
    }

    private static String safeFingerprint() {
        try {
            return Account.fingerprint();
        } catch (RuntimeException e) {
            return "?";
        }
    }

    /** חתימה זולה של הקבצים (קיים / זמן / גודל) — בלי לפענח */
    static String statPrint() {
        StringBuilder b = new StringBuilder();
        for (File f : Account.watchedFiles()) b.append(f.isFile() ? f.lastModified() + ":" + f.length() : "-").append(';');
        return b.toString();
    }

    private static void loop() {
        WatchService ws = null;
        try {
            ws = FileSystems.getDefault().newWatchService();
        } catch (Exception e) {
            ws = null;   // רק הבדיקה הקבועה
        }
        Map<Path, WatchKey> keys = new HashMap<Path, WatchKey>();
        String stat = statPrint();
        long lastFull = System.currentTimeMillis();
        while (true) {
            try {
                if (ws != null) register(ws, keys);
                WatchKey k = null;
                if (ws != null) k = ws.poll(POLL_MS, TimeUnit.MILLISECONDS);
                else Thread.sleep(POLL_MS);
                boolean event = k != null;
                if (k != null) {
                    // כמה אירועים של אותה שמירה (קובץ זמני, העברה, גיבוי) — מחכים רגע ואוספים את כולם
                    Thread.sleep(120);
                    while (k != null) {
                        k.pollEvents();
                        if (!k.reset()) keys.values().remove(k);
                        k = ws.poll();
                    }
                }
                String s = statPrint();
                long now = System.currentTimeMillis();
                if (event || !s.equals(stat) || now - lastFull > RECHECK_MS) {
                    stat = s;
                    lastFull = now;
                    checkNow();
                }
            } catch (InterruptedException e) {
                return;
            } catch (Throwable t) {
                try { Thread.sleep(POLL_MS); } catch (InterruptedException e) { return; }
            }
        }
    }

    /** תיקיות שנוצרו בינתיים (למשל: הכניסה הראשונה במחשב) נכנסות למעקב */
    private static void register(WatchService ws, Map<Path, WatchKey> keys) {
        for (File d : Account.watchedDirs()) {
            Path p = d.toPath();
            WatchKey k = keys.get(p);
            if (k != null && k.isValid()) continue;
            if (!d.isDirectory()) continue;
            try {
                keys.put(p, p.register(ws, StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_DELETE,
                        StandardWatchEventKinds.ENTRY_MODIFY));
            } catch (Exception ignored) {
                // נשארת הבדיקה הקבועה
            }
        }
    }
}
