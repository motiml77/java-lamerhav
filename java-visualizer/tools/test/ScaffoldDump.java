package il.lamerhav.dsviz;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * הצד של Java בבדיקת ההתאמה ל-site/assets/scaffold.js (tools/test/scaffold-parity.js מריץ ומשווה):
 * לכל JSON בתיקיות — מה ש-read מחזיר, ומה ש-build בונה (בלי הצעות, עם הצעות, ועם return לבדיקות), כל אחד לקובץ משלו.
 *   java il.lamerhav.dsviz.ScaffoldDump <out-dir> <folder-cases-file> <json-dir>...
 */
public class ScaffoldDump {
    public static void main(String[] a) throws Exception {
        Path out = Paths.get(a[0]);
        Files.createDirectories(out);
        // שמות תיקיות: [[topic, [תפוסים...], cs], ...]
        StringBuilder f = new StringBuilder();
        for (Object o : Json.arr(Json.parse(read(new File(a[1]))))) {
            List<Object> c = Json.arr(o);
            Set<String> taken = new LinkedHashSet<String>();
            for (Object x : Json.arr(c.get(1))) taken.add(Json.str(x));
            String topic = c.get(0) == null ? null : Json.str(c.get(0));
            String name;
            try {
                name = AiScaffold.folderName(topic, taken, Boolean.TRUE.equals(c.get(2)));
            } catch (RuntimeException e) {
                name = "ERROR";
            }
            field(f, "folder", name);
        }
        write(out.resolve("folders.txt"), f);

        for (int i = 2; i < a.length; i++) {
            File[] fs = new File(a[i]).listFiles((x, n) -> n.endsWith(".json"));
            Arrays.sort(fs);
            for (File js : fs) {
                String id = new File(a[i]).getName() + "_" + js.getName();
                AiScaffold.Exercise e = AiScaffold.read(Json.obj(Json.parse(read(js))));
                dumpCase(out, id, e);
            }
        }
        // סבב תיקון (takeSolutions): broken + fixed מתיקיית repair
        File repair = new File(new File(a[2]), "repair");
        if (new File(repair, "broken.json").exists()) {
            AiScaffold.Exercise e = AiScaffold.read(Json.obj(Json.parse(read(new File(repair, "broken.json")))));
            AiScaffold.Exercise fixed = AiScaffold.read(Json.obj(Json.parse(read(new File(repair, "fixed.json")))));
            boolean changed = AiScaffold.takeSolutions(e, fixed);
            e.title = e.title + " changed=" + changed;
            dumpCase(out, "repair_takeSolutions", e);
        }
    }

    static void dumpCase(Path out, String id, AiScaffold.Exercise e) throws Exception {
        boolean cs = e.cs;
        HashSet<String> taken = new HashSet<String>(cs ? Arrays.asList("Targil1") : Arrays.asList("examples", "ex07_old"));
        String pkg = AiScaffold.folderName(e.topic, taken, cs);
        StringBuilder r = new StringBuilder();
        field(r, "folder", pkg);
        field(r, "cs", String.valueOf(e.cs));
        field(r, "title", e.title);
        field(r, "summary", e.summary);
        field(r, "topic", e.topic);
        field(r, "mainClass", e.mainClass);
        field(r, "mainCode", e.mainCode);
        field(r, "expectedOutput", e.expectedOutput);
        for (String s : e.imports) field(r, "import", s);
        for (Map.Entry<String, String> c : e.classes.entrySet()) {
            field(r, "class", c.getKey());
            field(r, "code", c.getValue());
        }
        for (AiScaffold.Task t : e.tasks) {
            field(r, "task.label", t.label);
            field(r, "task.kind", t.kind);
            field(r, "task.className", t.className);
            field(r, "task.returnType", t.returnType);
            field(r, "task.methodName", t.methodName);
            field(r, "task.isStatic", String.valueOf(t.isStatic));
            field(r, "task.instructions", t.instructions);
            for (String[] p : t.params) field(r, "task.param", p[0] + " " + p[1]);
            field(r, "task.header", AiScaffold.header(t));
            field(r, "task.mark", AiScaffold.markFor(t, e.tasks.size() > 1));
            field(r, "task.solution", t.solution);
        }
        for (String[] g : e.given) {
            field(r, "given.className", g[0]);
            field(r, "given.code", g[1]);
        }
        for (String[] q : e.others) {
            field(r, "other.label", q[0]);
            field(r, "other.text", q[1]);
        }
        write(out.resolve(id + ".read.txt"), r);
        List<String> sources = new ArrayList<String>(Arrays.asList(id, "צילום מסך 1.png"));
        for (String mode : new String[] { "stub", "solutions", "testReturns" }) {
            AiScaffold.testReturns = mode.equals("testReturns");
            StringBuilder b = new StringBuilder();
            for (AiScaffold.GeneratedFile g : AiScaffold.build(e, pkg, sources, mode.equals("solutions"))) {
                field(b, "className", g.className);
                field(b, "cursorLine", String.valueOf(g.cursorLine));
                field(b, "cs", String.valueOf(g.cs));
                field(b, "fileName", g.fileName());
                field(b, "code", g.code);
            }
            AiScaffold.testReturns = false;
            write(out.resolve(id + "." + mode + ".txt"), b);
        }
    }

    /** שם, אורך (ביחידות UTF-16), ואז הערך — כך שאי אפשר לבלבל בין שדות */
    static void field(StringBuilder b, String name, String value) {
        if (value == null) value = "<null>";
        b.append(name).append(' ').append(value.length()).append('\n').append(value).append('\n');
    }

    static String read(File f) throws Exception {
        return new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
    }

    static void write(Path p, CharSequence s) throws Exception {
        Files.write(p, s.toString().getBytes(StandardCharsets.UTF_8));
    }
}
