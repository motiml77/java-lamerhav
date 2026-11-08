package il.lamerhav.dsviz;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * "מאגר תרגילים" — מה התלמיד כבר עשה: ה-V הירוק ("סיימתי"), ואילו קבצים נוצרו מכל שאלה (כדי לפתוח שוב את אותה עבודה ולא לדרוס אותה,
 * וכדי שחלון הצד "השאלה" יידע איזו שאלה שייכת לקובץ שפתוח בעורך).
 * נשמר ב-~/.dsviz/bank-progress.json (לא בפרויקט): {"questions": {"<id>": {"done": true, "doneAt": ..., "created": [{...}]}}}.
 * בלי תלות ב-Eclipse.
 */
final class BankProgress {
    /** תרגיל שנוצר משאלה: הפרויקט, התיקייה (החבילה) והקבצים — נתיבים בסביבת העבודה (‎/Project/src/ex01_stack/Targil.java) */
    static final class Created {
        String project = "", folder = "", focus = "";
        final List<String> files = new ArrayList<String>();
        long at;
    }

    private static BankProgress shared;

    /** הקובץ של המשתמש (-Ddsviz.bankProgress=<קובץ> — לבדיקות) */
    static synchronized BankProgress get() {
        String p = System.getProperty("dsviz.bankProgress");
        File f = p != null && !p.isEmpty() ? new File(p) : new File(System.getProperty("user.home"), ".dsviz/bank-progress.json");
        if (shared == null || !shared.file.equals(f)) shared = new BankProgress(f);
        return shared;
    }

    private final File file;
    private long loadedAt = -1;
    private Map<String, Object> questions = new LinkedHashMap<String, Object>();
    /** מי מקשיב לשינויים (חלון המאגר, חלון הצד) — נקראים אחרי כל שינוי */
    private final List<Runnable> listeners = new ArrayList<Runnable>();

    BankProgress(File file) {
        this.file = file;
    }

    synchronized void listen(Runnable r) {
        listeners.add(r);
    }

    synchronized void unlisten(Runnable r) {
        listeners.remove(r);
    }

    // ------------------------------------------------------------------ V

    synchronized boolean done(String id) {
        load();
        return Boolean.TRUE.equals(Json.obj(questions.get(id)).get("done"));
    }

    void setDone(String id, boolean done) {
        synchronized (this) {
            load();
            Map<String, Object> q = entry(id);
            if (done) {
                q.put("done", Boolean.TRUE);
                q.put("doneAt", (double) System.currentTimeMillis());
            } else {
                q.remove("done");
                q.remove("doneAt");
            }
            save();
        }
        fire();
    }

    /** כמה מהשאלות כבר סומנו V */
    synchronized int doneCount(List<BankStore.Question> qs) {
        int n = 0;
        for (BankStore.Question q : qs) if (done(q.id)) n++;
        return n;
    }

    // ------------------------------------------------------------------ מה נוצר

    void created(String id, Created c) {
        synchronized (this) {
            load();
            Map<String, Object> q = entry(id);
            List<Object> list = new ArrayList<Object>(Json.arr(q.get("created")));
            Map<String, Object> m = new LinkedHashMap<String, Object>();
            m.put("project", c.project);
            m.put("folder", c.folder);
            m.put("focus", c.focus);
            m.put("files", new ArrayList<Object>(c.files));
            m.put("at", (double) (c.at == 0 ? System.currentTimeMillis() : c.at));
            list.add(m);
            q.put("created", list);
            save();
        }
        fire();
    }

    /** שוכחים את מה שנוצר מהשאלה (אחרי שהקבצים נמחקו) — כדי שתרגיל חדש באותה תיקייה לא ייחשב שייך לשאלה הזאת */
    void forgetCreated(String id) {
        synchronized (this) {
            load();
            Map<String, Object> q = Json.obj(questions.get(id));
            if (!q.containsKey("created")) return;
            entry(id).remove("created");
            save();
        }
        fire();
    }

    /** כל מה שנוצר מהשאלה — מהישן לחדש */
    synchronized List<Created> createdOf(String id) {
        load();
        List<Created> out = new ArrayList<Created>();
        for (Object o : Json.arr(Json.obj(questions.get(id)).get("created"))) {
            Map<String, Object> m = Json.obj(o);
            Created c = new Created();
            c.project = Json.str(m.get("project"));
            c.folder = Json.str(m.get("folder"));
            c.focus = Json.str(m.get("focus"));
            for (Object f : Json.arr(m.get("files"))) c.files.add(Json.str(f));
            c.at = m.get("at") instanceof Number ? ((Number) m.get("at")).longValue() : 0;
            out.add(c);
        }
        return out;
    }

    /** השאלה שהקובץ (נתיב בסביבת העבודה) נוצר ממנה — האחרונה שנוצרה, או null */
    synchronized String questionOf(String path) {
        load();
        String best = null;
        long when = -1;
        for (Map.Entry<String, Object> e : questions.entrySet()) {
            for (Created c : createdOf(e.getKey())) {
                if ((c.files.contains(path) || (!c.folder.isEmpty() && path.startsWith(c.folder + "/"))) && c.at >= when) {
                    best = e.getKey();
                    when = c.at;
                }
            }
        }
        return best;
    }

    // ------------------------------------------------------------------ קובץ

    private Map<String, Object> entry(String id) {
        Object o = questions.get(id);
        if (!(o instanceof Map)) {
            o = new LinkedHashMap<String, Object>();
            questions.put(id, o);
        }
        return Json.obj(o);
    }

    private void load() {
        long m = file.isFile() ? file.lastModified() : 0;
        if (m == loadedAt) return;
        loadedAt = m;
        questions = new LinkedHashMap<String, Object>();
        if (m == 0) return;
        try {
            Map<String, Object> root = Json.obj(Json.parse(new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8)));
            questions = new LinkedHashMap<String, Object>(Json.obj(root.get("questions")));
        } catch (Exception e) {
            // קובץ פגום — מתחילים מחדש (לא נופלים)
        }
    }

    /** כתיבה דרך קובץ זמני — קובץ שנכתב רק חלקית (כיבוי באמצע) לא מוחק את כל ההתקדמות */
    private void save() {
        try {
            File dir = file.getParentFile();
            if (dir != null) dir.mkdirs();
            Map<String, Object> root = new LinkedHashMap<String, Object>();
            root.put("questions", questions);
            File tmp = new File(file.getPath() + ".tmp");
            Files.write(tmp.toPath(), Json.write(root).getBytes(StandardCharsets.UTF_8));
            Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
            loadedAt = file.lastModified();
        } catch (Exception e) {
            // אי אפשר לשמור (תיקייה לקריאה בלבד) — ההתקדמות נשמרת רק בזיכרון עד שייסגר Eclipse
        }
    }

    private void fire() {
        List<Runnable> copy;
        synchronized (this) {
            copy = new ArrayList<Runnable>(listeners);
        }
        for (Runnable r : copy) {
            try {
                r.run();
            } catch (RuntimeException ignored) {
                // מאזין שנכשל לא עוצר את האחרים
            }
        }
    }
}
