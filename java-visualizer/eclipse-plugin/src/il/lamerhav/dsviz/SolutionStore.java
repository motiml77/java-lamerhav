package il.lamerhav.dsviz;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * ההצעות לפתרון של התרגילים שנוצרו ב"שליחת תרגיל" — לא בתוך הקוד של התלמיד ולא בפרויקט שלו, אלא בצד, בתיקייה של התוסף
 * (‎.metadata/.plugins/il.lamerhav.dsviz/solutions.json בסביבת העבודה). לכל הצעה: הקובץ והפעולה שהיא שייכת אליהם,
 * הקוד, ואם היא נבדקה (SolutionCheck). כך הכפתור "הצעה לפתרון" מופיע רק ליד הפעולות של הסעיפים.
 * בלי תלות ב-Eclipse (נבדק גם מחוץ לו); החנות של סביבת העבודה — SolutionMinings.store().
 */
final class SolutionStore {
    static final class Entry {
        String path = "", cls = "", method = "", label = "", code = "", status = "unchecked";
        final List<String> params = new ArrayList<String>();
        long time;

        /** אותה פעולה (שם ומספר פרמטרים — כמו שהיא בקובץ, גם אם התלמיד שינה קצת את הכותרת) */
        boolean matches(String className, String methodName, int paramCount) {
            return cls.equals(className) && method.equals(methodName) && params.size() == paramCount;
        }
    }

    private final File file;
    private long loadedAt = -1;
    private final List<Entry> entries = new ArrayList<Entry>();

    SolutionStore(File file) {
        this.file = file;
    }

    /** מוסיף את ההצעות של תרגיל (הצעה קודמת לאותה פעולה באותו קובץ — מוחלפת) */
    synchronized void add(List<Entry> add) {
        load();
        for (Entry n : add) {
            for (int i = entries.size() - 1; i >= 0; i--) {
                Entry o = entries.get(i);
                if (o.path.equals(n.path) && o.matches(n.cls, n.method, n.params.size())) entries.remove(i);
            }
            if (n.time == 0) n.time = System.currentTimeMillis();
            entries.add(n);
        }
        save();
    }

    /**
     * ההצעה לפעולה בקובץ (path — הנתיב בסביבת העבודה, ‎/Project/src/ex01_stacks/StackTargil.java‎).
     * אם הפרויקט שונה שם או הועתק — לפי שאר הנתיב (התיקייה ex01_... והקובץ), ההצעה האחרונה.
     */
    synchronized Entry find(String path, String className, String methodName, int paramCount) {
        load();
        Entry best = null;
        for (Entry e : entries) if (e.path.equals(path) && e.matches(className, methodName, paramCount)) best = e;
        if (best != null) return best;
        String tail = tail(path);
        for (Entry e : entries)
            if (!tail.isEmpty() && tail(e.path).equals(tail) && e.matches(className, methodName, paramCount) && (best == null || e.time >= best.time)) best = e;
        return best;
    }

    /** כל ההצעות לקובץ */
    synchronized List<Entry> forFile(String path) {
        load();
        List<Entry> out = new ArrayList<Entry>();
        for (Entry e : entries) if (e.path.equals(path)) out.add(e);
        if (out.isEmpty()) {
            String tail = tail(path);
            for (Entry e : entries) if (!tail.isEmpty() && tail(e.path).equals(tail)) out.add(e);
        }
        return out;
    }

    /** הנתיב בלי שם הפרויקט: src/ex01_stacks/StackTargil.java */
    private static String tail(String path) {
        String p = path.startsWith("/") ? path.substring(1) : path;
        int k = p.indexOf('/');
        return k < 0 ? "" : p.substring(k + 1);
    }

    private void load() {
        long m = file.isFile() ? file.lastModified() : 0;
        if (m == loadedAt) return;
        loadedAt = m;
        entries.clear();
        if (m == 0) return;
        try {
            Object root = Json.parse(new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8));
            for (Object o : Json.arr(Json.obj(root).get("solutions"))) {
                Map<String, Object> j = Json.obj(o);
                Entry e = new Entry();
                e.path = Json.str(j.get("path"));
                e.cls = Json.str(j.get("class"));
                e.method = Json.str(j.get("method"));
                e.label = Json.str(j.get("label"));
                e.code = Json.str(j.get("code"));
                e.status = Json.str(j.get("status"));
                for (Object p : Json.arr(j.get("params"))) e.params.add(Json.str(p));
                try {
                    e.time = Long.parseLong(Json.str(j.get("time")));
                } catch (NumberFormatException ignored) {
                    e.time = 0;
                }
                if (!e.code.isEmpty()) entries.add(e);
            }
        } catch (Exception ignored) {
            // קובץ פגום — מתחילים מחדש
        }
    }

    private void save() {
        List<Object> list = new ArrayList<Object>();
        for (Entry e : entries) {
            Map<String, Object> j = new LinkedHashMap<String, Object>();
            j.put("path", e.path);
            j.put("class", e.cls);
            j.put("method", e.method);
            j.put("params", new ArrayList<Object>(e.params));
            j.put("label", e.label);
            j.put("status", e.status);
            j.put("time", String.valueOf(e.time));
            j.put("code", e.code);
            list.add(j);
        }
        Map<String, Object> root = new LinkedHashMap<String, Object>();
        root.put("solutions", list);
        try {
            file.getParentFile().mkdirs();
            File tmp = new File(file.getPath() + ".tmp");
            Files.write(tmp.toPath(), Json.write(root).getBytes(StandardCharsets.UTF_8));
            Files.move(tmp.toPath(), file.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            loadedAt = file.lastModified();
        } catch (Exception ignored) {
            // לא נשמר — הכפתור פשוט לא יופיע
        }
    }
}
