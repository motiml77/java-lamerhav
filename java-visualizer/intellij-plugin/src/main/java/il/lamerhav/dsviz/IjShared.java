package il.lamerhav.dsviz;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * גשר לתוסף של IntelliJ (il.lamerhav.dsviz.intellij) אל הקוד המשותף של Eclipse שהוא "פרטי לחבילה":
 * WorkerClient (כתובת השרת של "שליחת תרגיל"), SolutionStore (ההצעות לפתרון) ו-Json (הפלט של SubmitCli).
 */
public final class IjShared {
    private IjShared() {}

    /** הצעה לפתרון של סעיף (העתק של SolutionStore.Entry, לקריאה בלבד) */
    public static final class Solution {
        public final String path, cls, method, label, code, status;
        public final int params;

        Solution(SolutionStore.Entry e) {
            path = e.path; cls = e.cls; method = e.method; label = e.label; code = e.code; status = e.status; params = e.params.size();
        }
    }

    private static final Map<String, SolutionStore> STORES = new java.util.HashMap<String, SolutionStore>();

    /** חנות אחת לכל קובץ (היא קוראת אותו מחדש רק כשהוא משתנה) */
    private static synchronized SolutionStore store(File f) {
        SolutionStore s = STORES.get(f.getAbsolutePath());
        if (s == null) STORES.put(f.getAbsolutePath(), s = new SolutionStore(f));
        return s;
    }

    /** יש שרת של "שליחת תרגיל"? (resources/ai.properties / ‎-Ddsviz.worker / DSVIZ_WORKER_URL) */
    public static boolean workerConfigured() {
        return WorkerClient.configured() != null;
    }

    /** יש חיבור לשרת (או לאינטרנט)? */
    public static boolean workerOnline(long waitMs) {
        WorkerClient c = WorkerClient.configured();
        return c == null || c.online(waitMs);
    }

    public static List<Solution> solutionsForFile(File store, String path) {
        List<Solution> out = new ArrayList<Solution>();
        for (SolutionStore.Entry e : store(store).forFile(path)) out.add(new Solution(e));
        return out;
    }

    public static Solution findSolution(File store, String path, String cls, String method, int params) {
        SolutionStore.Entry e = store(store).find(path, cls, method, params);
        return e == null ? null : new Solution(e);
    }

    /** לבדיקות: מוסיף הצעה לחנות */
    public static void addSolution(File store, String path, String cls, String method, List<String> params, String label, String code, String status) {
        SolutionStore.Entry e = new SolutionStore.Entry();
        e.path = path; e.cls = cls; e.method = method; e.label = label; e.code = code; e.status = status;
        e.params.addAll(params);
        List<SolutionStore.Entry> l = new ArrayList<SolutionStore.Entry>();
        l.add(e);
        store(store).add(l);
    }

    /** שורת JSON של SubmitCli → מפה (או null אם זו לא שורת JSON) */
    public static Map<String, Object> parseJsonLine(String line) {
        try {
            String t = line.trim();
            if (!t.startsWith("{")) return null;
            return Json.obj(Json.parse(t));
        } catch (Exception e) {
            return null;
        }
    }

    public static String str(Object o) {
        return Json.str(o);
    }

    public static List<Object> arr(Object o) {
        return Json.arr(o);
    }

    public static Map<String, Object> obj(Object o) {
        return Json.obj(o);
    }

    public static String toJson(Map<String, Object> m) {
        return Json.write(m);
    }
}
