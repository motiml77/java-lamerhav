package il.lamerhav.dsviz;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * "מאגר תרגילים": הנושאים והשאלות. בלי תלות ב-Eclipse (נבדק גם מחוץ לו — tools/test/bank).
 *
 * איפה השאלות:
 *   1. בתוך התוסף: bank/topics.json, bank/index.json (רשימת הקבצים — נוצרת בבנייה) ו-bank/<נושא>/<שאלה>.json
 *   2. בתיקייה מקומית (~/.dsviz/bank, או -Ddsviz.bank=<תיקייה>): אותו מבנה. שאלה עם אותו id מחליפה את זו שבתוסף —
 *      כך אפשר לנסות שאלה חדשה או מתוקנת בלי בנייה חדשה.
 *
 * שאלה = קובץ JSON: id, topic, level (easy / medium / hard), title, question: {intro, sections: [{label, text}]}, ו-exercise —
 * התרגיל עצמו, בדיוק בפורמט ש-Gemini מחזיר ב"שליחת תרגיל" (AiScaffold.read): מחלקות, פעולה לכל סעיף עם ההצעה לפתרון, main
 * והפלט הצפוי. כך התרגיל נבנה באותו מנוע (SubmitHandler.Build) — אותה מעטפת, אותו "כאן עונים", ואותו 💡 ליד כל סעיף.
 */
final class BankStore {
    /** רמת הקושי: הסדר ברשימה (קל קודם) והצבע של הנקודה */
    enum Level {
        EASY("easy", "קל", 0x2E9E5B), MEDIUM("medium", "בינוני", 0xF08C00), HARD("hard", "קשה", 0xD92D20);

        final String id, title;
        final int color;

        Level(String id, String title, int color) {
            this.id = id;
            this.title = title;
            this.color = color;
        }

        static Level of(String s) {
            for (Level l : values()) if (l.id.equals(s)) return l;
            return null;
        }
    }

    static final class Topic {
        final String id, title;

        Topic(String id, String title) {
            this.id = id;
            this.title = title;
        }
    }

    static final class Section {
        final String label, text;
        /** ציורים קטנים של מבנים שמופיעים בסעיף (לדוגמה: המחסנית לפני ואחרי) */
        final List<Figure> figures = new ArrayList<Figure>();

        Section(String label, String text) {
            this.label = label;
            this.text = text;
        }
    }

    /**
     * ציור קטן של מבנה נתונים בנוסח השאלה — באותו סגנון כמו בחלון ההרצה (NestedPreview).
     * JSON: {"type": "stack" | "queue" | "list" | "tree" | "array" | "matrix", "values": [...], "inner": "stack" | "queue" | "list" (לא חובה),
     *        "caption": "המחסנית s" (לא חובה)}
     *   stack — מהראש למטה (כמו שכותבים בשאלה: "4, 7, 2, 9 — 4 בראש"); queue — מהראש (יוצאים) לזנב; list — מהחוליה הראשונה;
     *   tree — לפי רמות, null במקום שאין צומת ([5, 3, 8, null, 4]); array — לפי המקום; matrix — שורות ([[1,2],[3,4]]);
     *   inner — מבנה בתוך מבנה: כל ערך הוא רשימה (מחסנית של תורים: "type": "stack", "inner": "queue", "values": [[1,2],[3]]).
     */
    static final class Figure {
        String type = "", inner = "cell", caption = "";
        /** לציור (NestedPreview.set): לכל תא — הערכים שבו; במחסנית — לפי סדר ההכנסה (הראש אחרון); null בעץ — אין צומת */
        final List<List<String>> groups = new ArrayList<List<String>>();
    }

    static final List<String> FIGURE_TYPES = java.util.Arrays.asList("stack", "queue", "list", "tree", "array", "matrix");
    static final List<String> INNER_TYPES = java.util.Arrays.asList("stack", "queue", "list");

    static final class Question {
        String id = "", topic = "", title = "", intro = "";
        Level level = Level.MEDIUM;
        final List<Section> sections = new ArrayList<Section>();
        /** ציורים שמופיעים בפתיחה של השאלה (לפני הסעיפים) */
        final List<Figure> figures = new ArrayList<Figure>();
        /** התרגיל בפורמט של "שליחת תרגיל" (נשלח כמו שהוא ל-AiScaffold.read / SubmitHandler.Build) */
        Map<String, Object> exercise = new LinkedHashMap<String, Object>();
        /** מאיפה נטענה (לבדיקות ולהודעות שגיאה) */
        String origin = "";

        /** הפלט ש-main צריך להדפיס כשכל הסעיפים פתורים נכון */
        String expectedOutput() {
            return Json.str(exercise.get("expectedOutput")).replace("\r", "").replaceAll("\\s+$", "");
        }
    }

    /** הנושאים — הסדר הקבוע, גם אם עוד אין בהם שאלות (אפשר להוסיף נושא חדש ב-topics.json) */
    static final String[][] DEFAULT_TOPICS = {
        { "loops", "לולאות" }, { "arrays", "מערכים" }, { "strings", "מחרוזות" }, { "classes", "מחלקות" }, { "recursion", "רקורסיה" },
        { "stack", "מחסנית" }, { "queue", "תור" }, { "list", "שרשרת חוליות" }, { "tree", "עץ בינארי" },
    };

    /** גרסת C# (Visual Studio): הנוסח והתרגיל מהשדה "csharp" של כל שאלה; שאלה בלי גרסת C# לא נטענת */
    boolean csharp;

    final List<Topic> topics = new ArrayList<Topic>();
    final List<Question> questions = new ArrayList<Question>();
    /** שאלות שלא נטענו (קובץ פגום / חסר שדה) — {מקור, למה}. התלמיד לא רואה אותן; הבדיקות נכשלות עליהן */
    final List<String[]> problems = new ArrayList<String[]>();

    // ------------------------------------------------------------------ טעינה

    /** המאגר שבתוך התוסף + התיקייה המקומית */
    static BankStore load() {
        return load(false);
    }

    /** csharp — לכלי של Visual Studio */
    static BankStore load(boolean csharp) {
        BankStore s = new BankStore();
        s.csharp = csharp;
        s.loadBundled(BankStore.class.getClassLoader());
        File local = localDir();
        if (local != null) s.loadDir(local);
        s.sort();
        return s;
    }

    /** התיקייה המקומית: -Ddsviz.bank, או ~/.dsviz/bank (אם קיימת) */
    static File localDir() {
        String p = System.getProperty("dsviz.bank");
        File f = p != null && !p.isEmpty() ? new File(p) : new File(System.getProperty("user.home"), ".dsviz/bank");
        return f.isDirectory() ? f : null;
    }

    void loadBundled(ClassLoader cl) {
        String topicsJson = resource(cl, "bank/topics.json");
        readTopics(topicsJson);
        String index = resource(cl, "bank/index.json");
        if (index == null) return;
        for (Object o : Json.arr(Json.obj(parse(index, "bank/index.json")).get("questions"))) {
            String path = "bank/" + Json.str(o);
            String text = resource(cl, path);
            if (text == null) problems.add(new String[] { path, "הקובץ לא נמצא בתוסף" });
            else add(text, path);
        }
    }

    /** תיקייה בתבנית של המאגר: topics.json (לא חובה) ו-<נושא>/*.json */
    void loadDir(File dir) {
        File t = new File(dir, "topics.json");
        if (t.isFile()) readTopics(read(t));
        else if (topics.isEmpty()) readTopics(null);
        File[] subs = dir.listFiles(File::isDirectory);
        if (subs == null) return;
        java.util.Arrays.sort(subs);
        for (File sub : subs) {
            File[] files = sub.listFiles((d, n) -> n.endsWith(".json"));
            if (files == null) continue;
            java.util.Arrays.sort(files);
            for (File f : files) {
                String text = read(f);
                if (text == null) problems.add(new String[] { f.getPath(), "אי אפשר לקרוא את הקובץ" });
                else add(text, f.getPath());
            }
        }
    }

    private void readTopics(String json) {
        if (json != null) {
            for (Object o : Json.arr(Json.obj(parse(json, "topics.json")).get("topics"))) {
                Map<String, Object> m = Json.obj(o);
                String id = Json.str(m.get("id")).trim(), title = Json.str(m.get("title")).trim();
                if (id.isEmpty() || title.isEmpty() || topic(id) != null) continue;
                topics.add(new Topic(id, title));
            }
        }
        if (topics.isEmpty()) for (String[] t : DEFAULT_TOPICS) topics.add(new Topic(t[0], t[1]));
    }

    /** מוסיף שאלה מטקסט JSON; שאלה עם אותו id — מחליפה את הקודמת (התיקייה המקומית גוברת על התוסף) */
    void add(String json, String origin) {
        Object parsed;
        try {
            parsed = Json.parse(json);
        } catch (RuntimeException e) {
            problems.add(new String[] { origin, "JSON לא תקין: " + e.getMessage() });
            return;
        }
        Question q = new Question();
        q.origin = origin;
        String why = read(Json.obj(parsed), q);
        if (why != null) {
            problems.add(new String[] { origin, why });
            return;
        }
        for (int i = 0; i < questions.size(); i++) {
            if (questions.get(i).id.equals(q.id)) {
                questions.set(i, q);
                return;
            }
        }
        questions.add(q);
    }

    /** קורא שאלה; מחזיר null אם הכול בסדר, אחרת — מה חסר (בעברית, לבדיקות) */
    String read(Map<String, Object> j, Question q) {
        q.id = Json.str(j.get("id")).trim();
        if (!q.id.matches("[a-z0-9][a-z0-9_-]*")) return "id חסר או לא תקין (אותיות קטנות באנגלית, ספרות, - ו-_): \"" + q.id + "\"";
        q.topic = Json.str(j.get("topic")).trim();
        if (topic(q.topic) == null) return "נושא לא מוכר: \"" + q.topic + "\" (הנושאים: " + topicIds() + ")";
        q.level = Level.of(Json.str(j.get("level")).trim());
        if (q.level == null) return "רמת קושי לא תקינה: \"" + Json.str(j.get("level")) + "\" (easy / medium / hard)";
        q.title = Json.str(j.get("title")).trim().replaceAll("\\s+", " ");
        if (q.title.isEmpty()) return "חסרה כותרת (title)";
        Map<String, Object> cs = Json.obj(j.get("csharp"));
        if (csharp && Json.obj(cs.get("exercise")).isEmpty()) return "אין גרסת C# (csharp.exercise) — python3 tools/bank-cs.py";
        Map<String, Object> question = Json.obj(csharp && cs.containsKey("question") ? cs.get("question") : j.get("question"));
        q.intro = Json.str(question.get("intro")).trim();
        String why = figures(question.get("figures"), q.figures, "question.figures");
        if (why != null) return why;
        for (Object o : Json.arr(question.get("sections"))) {
            Map<String, Object> s = Json.obj(o);
            String label = Json.str(s.get("label")).trim(), text = Json.str(s.get("text")).trim();
            if (text.isEmpty()) return "סעיף בלי טקסט (question.sections)";
            Section sec = new Section(label, text);
            why = figures(s.get("figures"), sec.figures, "הציורים של סעיף " + label);
            if (why != null) return why;
            q.sections.add(sec);
        }
        if (q.intro.isEmpty() && q.sections.isEmpty()) return "חסר נוסח השאלה (question.intro / question.sections)";
        q.exercise = Json.obj(csharp ? cs.get("exercise") : j.get("exercise"));
        if (Json.arr(q.exercise.get("tasks")).isEmpty()) return "בתרגיל אין סעיפים (exercise.tasks)";
        if (csharp) q.exercise.put("language", "csharp");
        else if (!q.exercise.containsKey("language")) q.exercise.put("language", "java");
        if (Json.str(q.exercise.get("topic")).isEmpty()) q.exercise.put("topic", q.topic);
        return null;
    }

    /** קורא רשימת ציורים; null — תקין, אחרת מה לא בסדר */
    static String figures(Object json, List<Figure> out, String where) {
        for (Object o : Json.arr(json)) {
            Map<String, Object> m = Json.obj(o);
            Figure f = new Figure();
            f.type = Json.str(m.get("type")).trim();
            if (!FIGURE_TYPES.contains(f.type)) return where + ": סוג ציור לא מוכר \"" + f.type + "\" (" + String.join(" / ", FIGURE_TYPES) + ")";
            String inner = Json.str(m.get("inner")).trim();
            if (!inner.isEmpty()) {
                if (!INNER_TYPES.contains(inner) || f.type.equals("matrix")) return where + ": inner לא תקין \"" + inner + "\" (stack / queue / list)";
                f.inner = inner;
            }
            f.caption = Json.str(m.get("caption")).trim();
            List<Object> values = Json.arr(m.get("values"));
            if (values.isEmpty() && !f.type.equals("stack") && !f.type.equals("queue")) return where + ": ציור בלי ערכים (values)";
            for (Object v : values) {
                if (v == null) {
                    if (!f.type.equals("tree")) return where + ": null מותר רק בעץ (צומת שאין)";
                    f.groups.add(null);
                } else if (f.type.equals("matrix") || !f.inner.equals("cell")) {
                    if (!(v instanceof List)) return where + ": ב-" + (f.type.equals("matrix") ? "matrix" : "מבנה בתוך מבנה") + " כל ערך הוא רשימה ([1, 2])";
                    List<String> g = new ArrayList<String>();
                    for (Object x : (List<?>) v) g.add(value(x));
                    f.groups.add(g);
                } else {
                    if (v instanceof List || v instanceof Map) return where + ": ערך שהוא רשימה — רק עם inner (מבנה בתוך מבנה)";
                    f.groups.add(java.util.Collections.singletonList(value(v)));
                }
            }
            if (f.type.equals("tree") && (f.groups.isEmpty() || f.groups.get(0) == null)) return where + ": לעץ חייב להיות שורש (הערך הראשון)";
            if (f.type.equals("stack")) Collections.reverse(f.groups);   // בשאלה: מהראש למטה; בציור: לפי סדר ההכנסה
            out.add(f);
        }
        return null;
    }

    /** ערך בתא: מספר שלם בלי ".0", מחרוזת כמו שהיא */
    private static String value(Object v) {
        if (v instanceof Number) {
            double d = ((Number) v).doubleValue();
            return d == Math.rint(d) && Math.abs(d) < 1e15 ? String.valueOf((long) d) : String.valueOf(d);
        }
        return v == null ? "null" : String.valueOf(v);
    }

    private void sort() {
        final List<String> order = new ArrayList<String>();
        for (Topic t : topics) order.add(t.id);
        Collections.sort(questions, (a, b) -> {
            int c = Integer.compare(order.indexOf(a.topic), order.indexOf(b.topic));
            if (c != 0) return c;
            c = Integer.compare(a.level.ordinal(), b.level.ordinal());
            return c != 0 ? c : a.id.compareTo(b.id);
        });
    }

    // ------------------------------------------------------------------ שאילתות

    Topic topic(String id) {
        for (Topic t : topics) if (t.id.equals(id)) return t;
        return null;
    }

    /** השאלות של נושא — מהקלה לקשה */
    List<Question> of(String topicId) {
        List<Question> out = new ArrayList<Question>();
        for (Question q : questions) if (q.topic.equals(topicId)) out.add(q);
        return out;
    }

    Question find(String id) {
        for (Question q : questions) if (q.id.equals(id)) return q;
        return null;
    }

    private String topicIds() {
        StringBuilder b = new StringBuilder();
        for (Topic t : topics) b.append(b.length() == 0 ? "" : ", ").append(t.id);
        return b.toString();
    }

    // ------------------------------------------------------------------ עזרים

    /** JSON של קובץ הנושאים / הרשימה — פגום: כאילו אין (ונרשמת בעיה) */
    private Object parse(String json, String what) {
        try {
            return Json.parse(json);
        } catch (RuntimeException e) {
            problems.add(new String[] { what, "JSON לא תקין: " + e.getMessage() });
            return null;
        }
    }

    private static String resource(ClassLoader cl, String path) {
        try (InputStream in = cl.getResourceAsStream(path)) {
            if (in == null) return null;
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            for (int n; (n = in.read(buf)) > 0;) out.write(buf, 0, n);
            String s = new String(out.toByteArray(), StandardCharsets.UTF_8);
            return !s.isEmpty() && s.charAt(0) == '﻿' ? s.substring(1) : s;
        } catch (IOException e) {
            return null;
        }
    }

    private static String read(File f) {
        try {
            String s = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
            return !s.isEmpty() && s.charAt(0) == '﻿' ? s.substring(1) : s;
        } catch (IOException e) {
            return null;
        }
    }
}
