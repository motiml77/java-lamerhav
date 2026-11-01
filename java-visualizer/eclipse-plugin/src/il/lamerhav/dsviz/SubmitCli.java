package il.lamerhav.dsviz;

import java.io.File;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * "שליחת תרגיל" משורת הפקודה — בשביל התוסף של VS Code (dsviz-ai.jar). אותו מנוע כמו ב-Eclipse (ExercisePipeline).
 * הפלט: שורת JSON לכל הודעה — {"progress": "..."} תוך כדי, ובסוף {"done": true, ...} או {"error": "..."}.
 * ב-done גם others: [{label, text}] — הסעיפים שעונים עליהם במילים (בלי קוד), שהתוסף מציג בהודעה אחרי שהתרגיל נוצר.
 *
 *   online                      — יש אינטרנט? (קוד יציאה 0 / 1)
 *   submit --manifest FILE.json — כל הפרטים בקובץ JSON (UTF-8), ולא בשורת הפקודה — כדי ששמות בעברית יעברו גם ב-Windows באנגלית:
 *     {lang: java|csharp, out, store, lib?, java?, cs?: [פקודת dsviz-cs], note?, client?, items: [{path, name}], taken?: [שמות]}
 *     taken — שמות שכבר תפוסים בפרויקט (חבילות בתיקיות מקור אחרות, פרויקטים בפתרון) — התרגיל החדש מקבל שם אחר מכולם
 *     java: out = תיקיית המקור (src), הקבצים בחבילה ex01_... ;  csharp: out = תיקיית התרגילים (DataStructures-CS), פרויקט Ex01_...
 */
public final class SubmitCli {
    private SubmitCli() {}

    private static PrintStream out;

    public static void main(String[] args) throws Exception {
        out = new PrintStream(new java.io.FileOutputStream(java.io.FileDescriptor.out), true, "UTF-8");
        if (args.length == 0) {
            out.println("{\"error\": \"usage: online | submit ...\"}");
            System.exit(2);
        }
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        String mf = arg(args, "--manifest", null);
        if (mf != null) m = Json.obj(Json.parse(new String(Files.readAllBytes(Paths.get(mf)), StandardCharsets.UTF_8)));
        String client = Json.str(m.get("client"));
        WorkerClient.CLIENT = client.isEmpty() ? "dsviz-vscode/1.0.1" : client;
        WorkerClient wc = WorkerClient.configured();
        if (args[0].equals("online")) {
            boolean on = wc == null || wc.online(3500);
            out.println(on ? "online" : "offline");
            System.exit(on ? 0 : 1);
        }
        if (!args[0].equals("submit")) {
            out.println("{\"error\": \"unknown command\"}");
            System.exit(2);
        }
        try {
            submit(m, wc);
        } catch (Throwable t) {
            emit("error", "לא הצלחתי ליצור את התרגיל: " + (t.getMessage() != null ? t.getMessage() : t.toString()));
            System.exit(1);
        }
    }

    private static void submit(Map<String, Object> m, WorkerClient wc) throws Exception {
        String lang = "csharp".equals(Json.str(m.get("lang"))) ? "csharp" : "java";
        File outDir = new File(Json.str(m.get("out")));
        File store = new File(Json.str(m.get("store")));
        String note = Json.str(m.get("note"));
        List<String> cs = new ArrayList<String>();
        for (Object o : Json.arr(m.get("cs"))) cs.add(Json.str(o));
        List<String> taken = new ArrayList<String>();
        for (Object o : Json.arr(m.get("taken"))) if (!Json.str(o).trim().isEmpty()) taken.add(Json.str(o).trim());
        if (wc == null) {
            emit("error", "השרת של \"שליחת תרגיל\" לא מוגדר.");
            System.exit(1);
        }
        // 1. הקבצים (PDF / Word / תמונות / צילומי מסך שהודבקו)
        emit("progress", "מכין את הקבצים…");
        List<ExerciseFiles.Item> items = new ArrayList<ExerciseFiles.Item>();
        List<String> names = new ArrayList<String>();
        try {
            for (Object o : Json.arr(m.get("items"))) {
                Map<String, Object> j = Json.obj(o);
                File f = new File(Json.str(j.get("path")));
                String name = Json.str(j.get("name"));
                if (name.isEmpty()) name = f.getName();
                byte[] data;
                try {
                    data = Files.readAllBytes(f.toPath());
                } catch (java.io.IOException e) {
                    throw new ExerciseFiles.UserError("לא הצלחתי לקרוא את הקובץ " + name + ": " + e.getMessage());
                }
                ExerciseFiles.Item it = ExerciseFiles.fromBytes(name, ExerciseFiles.ext(name), data);
                items.add(it);
                names.add(it.name);
            }
            ExerciseFiles.checkAll(items);
        } catch (ExerciseFiles.UserError e) {
            emit("error", e.getMessage());
            System.exit(1);
        }
        // 2. השרת (Gemini)
        emit("progress", "שולח את התרגיל… (בדרך כלל עד דקה)");
        Map<String, Object> raw;
        try {
            raw = wc.exercise(items, note, lang);
        } catch (WorkerClient.AiError e) {
            emit("error", e.getMessage());
            System.exit(1);
            return;
        }
        // 3. בונים, בודקים, מתקנים
        ExercisePipeline.Progress p = new ExercisePipeline.Progress() {
            public void say(String text) {
                emit("progress", text);
            }

            public boolean cancelled() {
                return false;
            }
        };
        emit("progress", "בונה את התרגיל…");
        ExercisePipeline.Result r;
        if (lang.equals("csharp")) {
            if (cs.isEmpty()) cs.add("dsviz-cs");
            String bad = CsSolution.check(outDir.toPath());
            if (bad != null) {
                emit("error", bad);
                System.exit(1);
            }
            r = ExercisePipeline.csharp(wc, raw, names, outDir.toPath(), cs, p, taken);
        } else {
            String libPath = Json.str(m.get("lib")), java = Json.str(m.get("java"));
            File lib = libPath.isEmpty() ? null : new File(libPath);
            r = ExercisePipeline.java(wc, raw, names, outDir, lib, java.isEmpty() ? null : java, p, taken);
        }
        if (!r.entries.isEmpty()) new SolutionStore(store).add(r.entries);
        Map<String, Object> done = new LinkedHashMap<String, Object>();
        done.put("done", Boolean.TRUE);
        done.put("folder", r.folderName);
        List<Object> files = new ArrayList<Object>();
        for (File f : r.files) files.add(f.getAbsolutePath());
        done.put("files", files);
        done.put("focus", r.focus == null ? "" : r.focus.getAbsolutePath());
        done.put("line", (double) r.line);
        done.put("errors", new ArrayList<Object>(r.errors));
        done.put("tasks", (double) r.tasks);
        List<Object> others = new ArrayList<Object>();   // הסעיפים שעונים עליהם במילים — לא בקוד, רק בהודעה לתלמיד
        for (String[] o : r.others) {
            Map<String, Object> q = new LinkedHashMap<String, Object>();
            q.put("label", o[0]);
            q.put("text", o[1]);
            others.add(q);
        }
        done.put("others", others);
        done.put("solutionStatus", r.solutionStatus);
        out.println(Json.write(done));
    }

    private static void emit(String key, String value) {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put(key, value);
        out.println(Json.write(m));
    }

    private static String arg(String[] args, String name, String def) {
        int i = Arrays.asList(args).indexOf(name);
        return i >= 0 && i + 1 < args.length ? args[i + 1] : def;
    }
}
