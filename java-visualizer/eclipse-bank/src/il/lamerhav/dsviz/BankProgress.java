package il.lamerhav.dsviz;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * "מאגר תרגילים" — מה התלמיד כבר עשה: ה-V הירוק ("סיימתי"), ואילו קבצים נוצרו מכל שאלה (כדי לפתוח שוב את אותה עבודה ולא לדרוס אותה,
 * וכדי שחלון הצד "השאלה" יידע איזו שאלה שייכת לקובץ שפתוח בעורך).
 * נשמר ב-~/.dsviz/bank-progress.json (לא בפרויקט): {"questions": {"<id>": {"done": true, "doneAt": ..., "created": [{...}]}},
 * "homework": {"teacher": ..., "syncedAt": ..., "sets": [...]}} — שיעורי הבית מהמורה, כמו שהשרת ענה בפעם האחרונה (BankHomework).
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
    private static boolean watching;

    /** הקובץ של המשתמש (-Ddsviz.bankProgress=<קובץ> — לבדיקות) */
    static synchronized BankProgress get() {
        String p = System.getProperty("dsviz.bankProgress");
        File f = p != null && !p.isEmpty() ? new File(p) : new File(System.getProperty("user.home"), ".dsviz/bank-progress.json");
        if (shared == null || !shared.file.equals(f)) shared = new BankProgress(f);
        if (!watching) {
            watching = true;
            // הגשה שחיכתה לאינטרנט נשלחה (Submit — תיבת היוצא): "נשמרה במחשב" יורד, ההערה הופכת ל"הוגש למורה ב-…", והחלונות מתעדכנים
            try {
                Submit.onSent(ex -> {
                    String id = Json.str(ex.get("bankId"));
                    if ("bank".equals(ex.get("source")) && !id.isEmpty()) get().sentFromOutbox(id);
                });
            } catch (Throwable ignored) {
                // בלי הכניסה (ספרייה חסרה) — אין גם תיבת יוצא
            }
        }
        return shared;
    }

    private final File file;
    private long loadedAt = -1;
    private Map<String, Object> questions = new LinkedHashMap<String, Object>();
    /** שיעורי הבית מהבדיקה האחרונה מול השרת (ריק — אין) */
    private Map<String, Object> homework = new LinkedHashMap<String, Object>();
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

    // ------------------------------------------------------------------ הוגש למורה

    /**
     * תלמיד הגיש את התרגיל למורה (או שההגשה מחכה לאינטרנט): נרשם מתי ולמי — כדי שגם בפעם הבאה שפותחים את השאלה יופיע
     * "הוגש למורה…". רק לתלמיד שמחובר בעצמו; בעמדת מחשב של הכיתה לא רושמים (כמה תלמידים עובדים על אותו מחשב)
     */
    void submitted(String id, String teacher, boolean queued) {
        synchronized (this) {
            load();
            Map<String, Object> q = entry(id);
            q.put("submittedAt", (double) System.currentTimeMillis());
            q.put("submittedTo", teacher == null ? "" : teacher);
            if (queued) q.put("submitQueued", Boolean.TRUE); else q.remove("submitQueued");
            save();
        }
        fire();
    }

    /** "הוגש למורה שרה כהן ב-18.11, 10:42" (למורה teacher — מי שמקבל עכשיו את ההגשות), או "" אם לא הוגש אליו */
    synchronized String submittedNote(String id, String teacher) {
        load();
        Map<String, Object> q = Json.obj(questions.get(id));
        if (!(q.get("submittedAt") instanceof Number)) return "";
        String to = Json.str(q.get("submittedTo"));
        if (teacher != null && !teacher.isEmpty() && !to.isEmpty() && !to.equals(teacher)) return "";   // הוגש למורה קודם
        // "19.11 בשעה 6:22" — מילה בין התאריך לשעה, כדי שהמספרים לא יתהפכו בשורה מימין לשמאל
        String when = new java.text.SimpleDateFormat("d.M 'בשעה' H:mm").format(new java.util.Date(((Number) q.get("submittedAt")).longValue()));
        // "נשמרה במחשב" — רק כל עוד היא באמת מחכה בתיבת היוצא (נשלחה בתהליך אחר / לפני שהחלון הקשיב — כבר לא)
        return Boolean.TRUE.equals(q.get("submitQueued")) && waitingInOutbox(id) ? "ההגשה מ-" + when + " נשמרה במחשב ונשלחת כשיש אינטרנט."
                : "הוגש למורה" + (to.isEmpty() ? "" : " " + to) + " ב-" + when + ".";
    }

    private static boolean waitingInOutbox(String id) {
        try {
            return Submit.waitingFor(id);
        } catch (Throwable e) {
            return true;   // בלי הכניסה — לא יודעים; משאירים את מה שנרשם
        }
    }

    /** הגשה שחיכתה בתיבת היוצא נשלחה (Submit.onSent): כבר לא "נשמרה במחשב"; הוגשה עכשיו. החלונות מתעדכנים */
    void sentFromOutbox(String id) {
        synchronized (this) {
            load();
            if (!Boolean.TRUE.equals(Json.obj(questions.get(id)).get("submitQueued"))) return;
            if (waitingInOutbox(id)) return;   // עוד הגשה של אותה שאלה מחכה
            Map<String, Object> q = entry(id);
            q.remove("submitQueued");
            q.put("submittedAt", (double) System.currentTimeMillis());
            save();
        }
        fire();
    }

    // ------------------------------------------------------------------ הציון מהמורה

    /**
     * הציונים שהמורה נתן, כמו שהשרת ענה (Account.studentWork): מזהה השאלה במאגר → {grade, again}. שאלה שכבר לא מופיעה עם ציון
     * (המורה מחק את הציון, או את התלמיד) — הציון נמחק גם כאן. נשמר בקובץ, כדי שיוצג גם בלי אינטרנט. מודיע רק אם משהו השתנה
     */
    void syncGrades(Map<String, Object[]> grades) {
        boolean changed = false;
        synchronized (this) {
            load();
            for (Map.Entry<String, Object> e : questions.entrySet()) {
                Map<String, Object> q = Json.obj(e.getValue());
                if (q.containsKey("grade") && !grades.containsKey(e.getKey())) {
                    entry(e.getKey()).remove("grade");
                    entry(e.getKey()).remove("gradeAgain");
                    changed = true;
                }
            }
            for (Map.Entry<String, Object[]> g : grades.entrySet()) {
                Map<String, Object> q = Json.obj(questions.get(g.getKey()));
                double grade = ((Number) g.getValue()[0]).doubleValue();
                boolean again = Boolean.TRUE.equals(g.getValue()[1]);
                if (q.get("grade") instanceof Number && ((Number) q.get("grade")).doubleValue() == grade && again == Boolean.TRUE.equals(q.get("gradeAgain"))) continue;
                Map<String, Object> w = entry(g.getKey());
                w.put("grade", grade);
                if (again) w.put("gradeAgain", Boolean.TRUE); else w.remove("gradeAgain");
                changed = true;
            }
            if (changed) save();
        }
        if (changed) fire();
    }

    /** "הציון מהמורה: 90" (ואם הגיש שוב אחרי הציון — "על ההגשה הקודמת"), או "" כשאין ציון */
    synchronized String gradeNote(String id) {
        load();
        Map<String, Object> q = Json.obj(questions.get(id));
        if (!(q.get("grade") instanceof Number)) return "";
        return "הציון מהמורה: " + ((Number) q.get("grade")).intValue() + (Boolean.TRUE.equals(q.get("gradeAgain")) ? " (על ההגשה הקודמת)" : "");
    }

    // ------------------------------------------------------------------ שיעורי בית

    /**
     * שיעורי הבית כמו שהשרת ענה (Account.studentWork → homework): נשמרים בקובץ, כדי שהכפתור בחלון המאגר יופיע גם בלי אינטרנט.
     * teacher — המורה שנתן (השם, להצגה ולבדיקה שזה עדיין המורה של התלמיד). מודיע רק אם משהו השתנה. syncedAt — מתי (מה שהוגש
     * מהמחשב אחרי הרגע הזה נחשב "הוגש" גם לפני שהשרת יודע)
     */
    void syncHomework(String teacher, List<Object> sets) {
        boolean changed;
        synchronized (this) {
            load();
            Map<String, Object> h = new LinkedHashMap<String, Object>();
            h.put("teacher", teacher == null ? "" : teacher);
            h.put("sets", new ArrayList<Object>(sets));
            changed = !Json.write(h.get("sets")).equals(Json.write(homework.get("sets"))) || !h.get("teacher").equals(Json.str(homework.get("teacher")));
            h.put("syncedAt", (double) System.currentTimeMillis());
            homework = h;
            save();
        }
        if (changed) fire();
    }

    /** אין שיעורי בית (התלמיד לא משויך יותר, או שרת שלא מכיר אותם) */
    void clearHomework() {
        synchronized (this) {
            load();
            if (homework.isEmpty()) return;
            homework = new LinkedHashMap<String, Object>();
            save();
        }
        fire();
    }

    /** מה שנשמר (עותק): teacher, syncedAt, sets — או מפה ריקה */
    synchronized Map<String, Object> homeworkData() {
        load();
        return new LinkedHashMap<String, Object>(homework);
    }

    /**
     * מה הוגש מהמחשב הזה (לשיעורי הבית): 2 — ההגשה שמורה במחשב ומחכה לאינטרנט (waiting — Submit.waitingIds); 1 — הוגשה אחרי
     * הבדיקה האחרונה מול השרת (since) — נחשבת "הוגשה" כבר עכשיו, והשרת מאשר בבדיקה הבאה; 0 — לא (או רק לפני — השרת יודע)
     */
    synchronized int localState(String id, double since, Set<String> waiting) {
        load();
        Map<String, Object> q = Json.obj(questions.get(id));
        if (Boolean.TRUE.equals(q.get("submitQueued")) && waiting.contains(id)) return 2;
        return q.get("submittedAt") instanceof Number && ((Number) q.get("submittedAt")).doubleValue() > since ? 1 : 0;
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
        homework = new LinkedHashMap<String, Object>();
        if (m == 0) return;
        try {
            Map<String, Object> root = Json.obj(Json.parse(new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8)));
            questions = new LinkedHashMap<String, Object>(Json.obj(root.get("questions")));
            homework = new LinkedHashMap<String, Object>(Json.obj(root.get("homework")));
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
            if (!homework.isEmpty()) root.put("homework", homework);
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
