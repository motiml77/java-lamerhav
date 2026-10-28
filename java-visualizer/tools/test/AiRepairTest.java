package il.lamerhav.dsviz;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

/**
 * "שליחת תרגיל" — סבב התיקון, כמו ב-SubmitHandler אבל עם javac במקום Eclipse:
 * תשובה שבונה קוד שלא מתקמפל → שגיאות → בקשת תיקון לשרת → בונים שוב → מתקמפל.
 *   java AiRepairTest <broken.json> <worker-url> <out-dir> <dsviz.jar>
 */
public class AiRepairTest {
    public static void main(String[] a) throws Exception {
        Map<String, Object> raw = Json.obj(Json.parse(new String(Files.readAllBytes(new File(a[0]).toPath()), StandardCharsets.UTF_8)));
        Path out = new File(a[2]).toPath();
        AiScaffold.Exercise e = AiScaffold.read(raw);
        String pkg = AiScaffold.packageName(e.packageName, new HashSet<String>());
        List<AiScaffold.GeneratedFile> files = AiScaffold.build(e, pkg, Arrays.asList("broken.json"));
        List<String> errors = compile(files, pkg, out, a[3]);
        check(!errors.isEmpty(), "היו צריכות להיות שגיאות");
        System.out.println("✓ קוד שלא מתקמפל: " + errors);
        Map<String, String> code = new LinkedHashMap<String, String>();
        for (AiScaffold.GeneratedFile g : files) code.put(g.fileName(), g.code);
        Map<String, Object> fixed = new WorkerClient(a[1], "").repair(raw, code, errors);
        List<AiScaffold.GeneratedFile> files2 = AiScaffold.build(AiScaffold.read(fixed), pkg, Arrays.asList("broken.json"));
        List<String> errors2 = compile(files2, pkg, out, a[3]);
        check(errors2.isEmpty(), "אחרי התיקון: " + errors2);
        System.out.println("✓ אחרי התיקון — מתקמפל");
    }

    static List<String> compile(List<AiScaffold.GeneratedFile> files, String pkg, Path out, String lib) throws Exception {
        Path dir = out.resolve("src").resolve(pkg);
        Files.createDirectories(dir);
        for (File f : dir.toFile().listFiles()) f.delete();
        List<File> srcs = new ArrayList<File>();
        for (AiScaffold.GeneratedFile g : files) {
            Path p = dir.resolve(g.fileName());
            Files.write(p, g.code.getBytes(StandardCharsets.UTF_8));
            srcs.add(p.toFile());
        }
        JavaCompiler jc = ToolProvider.getSystemJavaCompiler();
        DiagnosticCollector<JavaFileObject> dc = new DiagnosticCollector<JavaFileObject>();
        try (StandardJavaFileManager fm = jc.getStandardFileManager(dc, Locale.ENGLISH, StandardCharsets.UTF_8)) {
            Files.createDirectories(out.resolve("bin"));
            jc.getTask(null, fm, dc, Arrays.asList("-encoding", "UTF-8", "-cp", lib, "-d", out.resolve("bin").toString()), null,
                    fm.getJavaFileObjectsFromFiles(srcs)).call();
        }
        List<String> errors = new ArrayList<String>();
        for (Diagnostic<? extends JavaFileObject> d : dc.getDiagnostics())
            if (d.getKind() == Diagnostic.Kind.ERROR)
                errors.add(new File(d.getSource().getName()).getName() + ":" + d.getLineNumber() + ": " + d.getMessage(Locale.ENGLISH).split("\n")[0]);
        return errors;
    }

    static void check(boolean b, String what) {
        if (!b) throw new AssertionError(what);
    }
}
