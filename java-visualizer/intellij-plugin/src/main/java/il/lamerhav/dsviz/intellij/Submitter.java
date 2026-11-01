package il.lamerhav.dsviz.intellij;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.regex.Pattern;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;

import il.lamerhav.dsviz.IjShared;

/**
 * "שליחת תרגיל" ב-IntelliJ: אותו מנוע כמו ב-Eclipse וב-VS Code (ExercisePipeline שבתוך dsviz.jar, דרך SubmitCli בתהליך Java נפרד —
 * עם ה-JDK שנמצא, כדי שבדיקת הקומפילציה ובדיקת ההצעות לפתרון ירוצו ב-javac אמיתי). SubmitCli שולח לשרת, בונה את הקבצים
 * בחבילה משלהם (src/ex01_stacks...), בודק שהם מתקמפלים (ואם לא — תיקון אחד), בודק את ההצעות לפתרון ושומר אותן בצד (‎~/.dsviz/solutions.json).
 * הבנייה מוצגת בחלון עצמו (SubmitDialog), ואחריה הודעה קצרה אחת (Created).
 */
public final class Submitter {
    private Submitter() {}

    static final String TITLE = "שליחת תרגיל";
    static final String CLIENT = "dsviz-intellij/" + Dsviz.VERSION;

    /** התוצאה של SubmitCli (השורה {"done": true, ...}) או שגיאה */
    static final class Outcome {
        String error, folder = "", focus = "", solutionStatus = "";
        int line, tasks;
        final List<String> files = new ArrayList<String>(), errors = new ArrayList<String>();
        /** הסעיפים שעונים עליהם במילים (בלי קוד): {סעיף, טקסט} */
        final List<String[]> others = new ArrayList<String[]>();
    }

    /** ההודעות של SubmitCli ({"progress": …}) והאם ביטלו */
    interface Listener {
        void progress(String text);

        boolean cancelled();
    }

    /** הפעולה מהתפריט: בדיקת חיבור → החלון (בו גם הבנייה) → פתיחת הקבצים והודעה קצרה */
    public static void run(Project project) {
        if (!IjShared.workerConfigured()) {
            Say.error(project, "השרת של \"שליחת תרגיל\" לא מוגדר.", TITLE);
            return;
        }
        boolean online = ProgressManager.getInstance().runProcessWithProgressSynchronously(
                () -> IjShared.workerOnline(3500) || onlineViaCli(), "בודק חיבור לאינטרנט…", false, project);
        if (!online) {
            offline(project);
            return;
        }
        List<ExerciseDialog.Target> targets = ProjectFiles.targets(project);
        if (targets.isEmpty()) {
            Say.info(project, "פתחו פרויקט Java ונסו שוב.", TITLE);
            return;
        }
        runDialog(project, new SubmitDialog(project, targets));
    }

    /** החלון: הטופס, הבנייה, ובסוף (אם נוצר) — פתיחת הקבצים */
    static void runDialog(Project project, SubmitDialog d) {
        if (d.showAndGet() && d.outcome() != null) finish(project, d.runTarget(), d.outcome());
    }

    /**
     * בדיקה שנייה, בתהליך Java נפרד (SubmitCli online — כמו ב-VS Code): IntelliJ משתמש בהגדרות ה-proxy שלו, והשליחה עצמה רצה
     * בתהליך נפרד עם ההגדרות של המערכת — אז אם IntelliJ לא מגיע לאינטרנט, שואלים גם את התהליך שישלח בפועל.
     */
    static boolean onlineViaCli() {
        try {
            File lib = Dsviz.libraryJar();
            JdkFinder.Found jdk = JdkFinder.find(null, 0);
            if (lib == null || jdk == null) return false;
            List<String> cmd = new ArrayList<String>(Arrays.asList(jdk.java(), "-Djava.awt.headless=true"));
            String worker = System.getProperty("dsviz.worker");
            if (worker != null && !worker.isEmpty()) cmd.add("-Ddsviz.worker=" + worker);
            cmd.addAll(Arrays.asList("-cp", lib.getAbsolutePath(), "il.lamerhav.dsviz.SubmitCli", "online"));
            Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
            p.getOutputStream().close();
            if (!p.waitFor(15, java.util.concurrent.TimeUnit.SECONDS)) {
                p.destroyForcibly();
                return false;
            }
            return p.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    static void offline(Project project) {
        Notice.Spec s = new Notice.Spec(Modern.Kind.WARN, TITLE, "אין חיבור לאינטרנט")
                .text("כדי לשלוח תרגיל צריך אינטרנט. בודקים את החיבור ומנסים שוב.\nהרצה ויזואלית, הוספת מבנה נתונים ודוגמאות פתורות עובדים גם בלי אינטרנט.")
                .buttons("נסו שוב", "סגירה");
        s.onPrimary = () -> run(project);
        Notice.show(project, s);
    }

    /** השליחה בלי שלב הבחירה (לבדיקות ולצילומי מסך): החלון נפתח ישר בבנייה */
    public static void submit(Project project, List<SubmitDialog.Item> items, String note, ExerciseDialog.Target target) {
        SubmitDialog d = new SubmitDialog(project, Collections.singletonList(target));
        for (SubmitDialog.Item it : items) d.add(it.file, it.name);
        d.autoStart();
        runDialog(project, d);
    }

    /** לפני השליחה (בחוט של המסך): תיקיית הקוד, הספרייה וה-JDK. מחזיר שגיאה קצרה, או null */
    static String prepare(Project project, ExerciseDialog.Target target) {
        try {
            ProjectFiles.ensureSourceRoot(project, target.dir);
        } catch (Exception e) {
            return "לא הצלחתי להכין את תיקיית הקוד: " + e.getMessage();
        }
        if (Dsviz.libraryJar() == null) return "הספרייה dsviz.jar חסרה בתוסף. התקינו מחדש את DSViz.";
        if (JdkFinder.find(null, 0) == null) return "לא נמצאה Java (JDK). התקינו מחדש את DSViz (ההתקנה כוללת Java 21).";
        return null;
    }

    /** השליחה האמיתית (רצה ברקע): SubmitCli עם כל השמות שכבר תפוסים בפרויקט */
    static SubmitDialog.Runner defaultRunner(Project project) {
        return (items, note, target, listener) -> {
            List<String> taken = ReadAction.compute(() -> takenNames(project, target.dir));
            return runCli(JdkFinder.find(null, 0), Dsviz.libraryJar(), target.dir, items, note, taken, listener);
        };
    }

    /**
     * השמות שכבר תפוסים — כדי שהחבילה של התרגיל (ex01_stacks...) לא תתנגש בחבילה קיימת: כל התיקיות שבראש כל תיקיות הקוד
     * (גם בדיקות) של כל המודולים, ובתיקייה שנבחרה. (SubmitCli מוסיף אותם לשמות שבתיקייה שנבחרה — "taken" במניפסט.)
     */
    static List<String> takenNames(Project project, File chosen) {
        TreeSet<String> out = new TreeSet<String>();
        List<VirtualFile> roots = new ArrayList<VirtualFile>(Arrays.asList(ProjectRootManager.getInstance(project).getContentSourceRoots()));
        for (Module m : ModuleManager.getInstance(project).getModules()) roots.addAll(Arrays.asList(ModuleRootManager.getInstance(m).getSourceRoots(true)));
        for (VirtualFile r : roots) {
            if (r == null || !r.isValid() || !r.isDirectory()) continue;
            for (VirtualFile c : r.getChildren()) if (c.isDirectory()) out.add(c.getName());
        }
        String[] names = chosen == null ? null : chosen.list();
        if (names != null) for (String n : names) if (new File(chosen, n).isDirectory()) out.add(n);
        return new ArrayList<String>(out);
    }

    /** הפקודה של SubmitCli */
    static List<String> command(String java, File lib, File manifest) {
        List<String> cmd = new ArrayList<String>();
        cmd.add(java);
        cmd.add("-Dfile.encoding=UTF-8");
        cmd.add("-Dstdout.encoding=UTF-8");
        cmd.add("-Djava.awt.headless=true");
        String worker = System.getProperty("dsviz.worker");   // לבדיקות: שרת אחר (‎-Ddsviz.worker=...)
        if (worker != null && !worker.isEmpty()) cmd.add("-Ddsviz.worker=" + worker);
        cmd.add("-cp");
        cmd.add(lib.getAbsolutePath());
        cmd.add("il.lamerhav.dsviz.SubmitCli");
        cmd.add("submit");
        cmd.add("--manifest");
        cmd.add(manifest.getAbsolutePath());
        return cmd;
    }

    /** המניפסט ל-SubmitCli (JSON) */
    static Map<String, Object> manifest(String java, File lib, File out, List<SubmitDialog.Item> items, String note, Collection<String> taken) {
        Map<String, Object> man = new LinkedHashMap<String, Object>();
        man.put("lang", "java");
        man.put("out", out.getAbsolutePath());
        man.put("store", Dsviz.solutionStore().getAbsolutePath());
        man.put("lib", lib.getAbsolutePath());
        man.put("java", java);
        man.put("note", note == null ? "" : note);
        man.put("client", CLIENT);
        man.put("taken", new ArrayList<Object>(taken == null ? Collections.<String>emptyList() : taken));
        List<Object> list = new ArrayList<Object>();
        for (SubmitDialog.Item it : items) {
            Map<String, Object> j = new LinkedHashMap<String, Object>();
            j.put("path", it.file.getAbsolutePath());
            j.put("name", it.name);
            list.add(j);
        }
        man.put("items", list);
        return man;
    }

    static Outcome runCli(JdkFinder.Found jdk, File lib, File out, List<SubmitDialog.Item> items, String note, Listener listener) {
        return runCli(jdk, lib, out, items, note, Collections.<String>emptyList(), listener);
    }

    static Outcome runCli(JdkFinder.Found jdk, File lib, File out, List<SubmitDialog.Item> items, String note, Collection<String> taken, Listener listener) {
        Outcome res = new Outcome();
        Process p = null;
        try {
            File manifest = File.createTempFile("dsviz-submit-", ".json");
            Files.write(manifest.toPath(), IjShared.toJson(manifest(jdk.java(), lib, out, items, note, taken)).getBytes(StandardCharsets.UTF_8));
            p = new ProcessBuilder(command(jdk.java(), lib, manifest)).redirectErrorStream(true).start();
            p.getOutputStream().close();
            final Process proc = p;
            Thread watch = new Thread(() -> {
                while (proc.isAlive()) {
                    if (listener != null && listener.cancelled()) { proc.destroyForcibly(); return; }
                    try { Thread.sleep(200); } catch (InterruptedException e) { return; }
                }
            });
            watch.setDaemon(true);
            watch.start();
            StringBuilder other = new StringBuilder();
            try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                for (String line; (line = r.readLine()) != null;) {
                    Map<String, Object> m = IjShared.parseJsonLine(line);
                    if (m == null) { other.append(line).append('\n'); continue; }
                    if (m.containsKey("progress") && listener != null) listener.progress(IjShared.str(m.get("progress")));
                    if (m.containsKey("error")) res.error = IjShared.str(m.get("error"));
                    if (m.containsKey("done")) read(res, m);
                }
            }
            p.waitFor();
            manifest.delete();
            if (listener != null && listener.cancelled()) res.error = res.files.isEmpty() ? "בוטל." : null;
            else if (res.error == null && res.files.isEmpty())
                res.error = "לא הצלחתי ליצור את התרגיל." + (other.length() > 0 ? "\n" + other.toString().trim() : "");
        } catch (Exception e) {
            res.error = "לא הצלחתי ליצור את התרגיל: " + e.getMessage();
            if (p != null) p.destroyForcibly();
        }
        return res;
    }

    /** השורה {"done": true, ...} */
    static void read(Outcome res, Map<String, Object> m) {
        res.folder = IjShared.str(m.get("folder"));
        res.focus = IjShared.str(m.get("focus"));
        res.solutionStatus = IjShared.str(m.get("solutionStatus"));
        res.line = (int) num(m.get("line"));
        res.tasks = (int) num(m.get("tasks"));
        for (Object o : IjShared.arr(m.get("files"))) res.files.add(IjShared.str(o));
        for (Object o : IjShared.arr(m.get("errors"))) res.errors.add(IjShared.str(o));
        for (Object o : IjShared.arr(m.get("others"))) {
            Map<String, Object> q = IjShared.obj(o);
            String text = IjShared.str(q.get("text")).trim();
            if (!text.isEmpty()) res.others.add(new String[] { IjShared.str(q.get("label")), text });
        }
    }

    /**
     * סימני כיוון בלתי נראים (RLE / PDF / RLI...) שהמחולל שם בהערות בעברית בשביל Eclipse — IntelliJ מציג אותם כמלבנים בקוד,
     * והוא מסדר עברית בהערות בעצמו. מוחקים אותם מהקבצים שנוצרו.
     */
    static void stripBidiControls(File f) {
        try {
            String text = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
            String clean = clean(text);
            if (!clean.equals(text)) Files.write(f.toPath(), clean.getBytes(StandardCharsets.UTF_8));
        } catch (Exception ignored) {
            // נשאר כמו שהוא
        }
    }

    static String clean(String text) {
        return text.replaceAll("[‪-‮⁦-⁩]", "");
    }

    private static double num(Object o) {
        return o instanceof Number ? ((Number) o).doubleValue() : 0;
    }

    private static final Pattern MAIN = Pattern.compile("\\bstatic\\s+void\\s+main\\s*\\(");

    /** הקובץ עם main מבין הקבצים שנוצרו, או null */
    static String mainFile(List<String> files) {
        for (String f : files) {
            try {
                if (MAIN.matcher(new String(Files.readAllBytes(new File(f).toPath()), StandardCharsets.UTF_8)).find()) return f;
            } catch (Exception ignored) {
                // הבא
            }
        }
        return null;
    }

    /**
     * אחרי השליחה: רענון, הספרייה במודול, פתיחת הקבצים — הקובץ עם main בלשונית שליד הקובץ של סעיף א (כדי שיהיה קל להריץ),
     * והקובץ של סעיף א אחרון, עם הסמן במקום התשובה. המקום של כל תשובה מסומן בצהוב (AnswerMarks), והודעה קצרה אחת (Created).
     */
    static void finish(Project project, ExerciseDialog.Target target, Outcome out) {
        if (out == null) return;
        if (out.error != null && out.files.isEmpty()) {
            if (!"בוטל.".equals(out.error)) Say.error(project, out.error, TITLE);
            return;
        }
        for (String f : out.files) stripBidiControls(new File(f));
        VirtualFile root = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(target.dir);
        if (root != null) VfsUtil.markDirtyAndRefresh(false, true, true, root);
        List<VirtualFile> files = new ArrayList<VirtualFile>();
        for (String f : out.files) {
            VirtualFile vf = LocalFileSystem.getInstance().refreshAndFindFileByPath(f.replace('\\', '/'));
            if (vf != null) files.add(vf);
        }
        VirtualFile focus = out.focus.isEmpty() ? null : LocalFileSystem.getInstance().refreshAndFindFileByPath(out.focus.replace('\\', '/'));
        if (focus == null && !files.isEmpty()) focus = files.get(0);
        String mainPath = mainFile(out.files);
        VirtualFile main = mainPath == null ? null : LocalFileSystem.getInstance().refreshAndFindFileByPath(mainPath.replace('\\', '/'));
        File folder = folderOf(target, out);
        AnswerMarks.get(project).track(folder);
        if (focus != null) {
            Module m = ModuleUtilCore.findModuleForFile(focus, project);
            if (m != null && LibrarySetup.needs(m)) LibrarySetup.add(m);
        }
        FileEditorManager fem = FileEditorManager.getInstance(project);
        for (VirtualFile f : openOrder(files, focus, main)) {
            if (f.equals(focus)) ProjectFiles.open(project, f, out.line);
            else fem.openFile(f, false);
        }
        DaemonCodeAnalyzer.getInstance(project).restart();
        Created.show(project, out, folder, focus == null ? "" : Created.firstLabel(focus, out.line));
        ApplicationManager.getApplication().invokeLater(() -> DaemonCodeAnalyzer.getInstance(project).restart());
    }

    /** הסדר שבו פותחים: שאר הקבצים, אחריהם main, ובסוף הקובץ של סעיף א (הלשונית הפעילה, ו-main בלשונית שלידה) */
    static <T> List<T> openOrder(List<T> files, T focus, T main) {
        List<T> order = new ArrayList<T>();
        for (T f : files) if (!f.equals(focus) && !f.equals(main)) order.add(f);
        if (main != null && !main.equals(focus)) order.add(main);
        if (focus != null) order.add(focus);
        return order;
    }

    /** התיקייה (החבילה) של התרגיל */
    static File folderOf(ExerciseDialog.Target target, Outcome out) {
        if (!out.folder.isEmpty()) return new File(target.dir, out.folder);
        if (!out.files.isEmpty()) return new File(out.files.get(0)).getParentFile();
        return target.dir;
    }
}
