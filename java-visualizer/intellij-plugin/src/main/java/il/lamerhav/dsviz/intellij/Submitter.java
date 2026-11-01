package il.lamerhav.dsviz.intellij;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;

import il.lamerhav.dsviz.IjShared;

/**
 * "שליחת תרגיל" ב-IntelliJ: אותו מנוע כמו ב-Eclipse וב-VS Code (ExercisePipeline שבתוך dsviz.jar, דרך SubmitCli בתהליך Java נפרד —
 * עם ה-JDK שנמצא, כדי שבדיקת הקומפילציה ובדיקת ההצעות לפתרון ירוצו ב-javac אמיתי). SubmitCli שולח לשרת, בונה את הקבצים
 * בחבילה משלהם (src/ex01_stacks...), בודק שהם מתקמפלים (ואם לא — תיקון אחד), בודק את ההצעות לפתרון ושומר אותן בצד (‎~/.dsviz/solutions.json).
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
    }

    /** הפעולה מהתפריט: בדיקת חיבור → החלון → שליחה ברקע (עם חלון התקדמות) → פתיחת הקבצים */
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
        SubmitDialog d = new SubmitDialog(project, targets);
        if (!d.showAndGet()) return;
        submit(project, d.items(), d.note(), d.target());
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
        Say.warn(project, "כדי לשלוח תרגיל צריך חיבור לאינטרנט, וכרגע אין חיבור.\n\nבדקו את החיבור (Wi-Fi או כבל) ונסו שוב.\n"
                + "כל שאר הכלים — הרצה ויזואלית, הוספת מבנה נתונים, דוגמאות פתורות — עובדים גם בלי אינטרנט.", TITLE + " — אין חיבור לאינטרנט");
    }

    /** השליחה עצמה (גם לבדיקות ולצילומי מסך — בלי החלון) */
    public static void submit(Project project, List<SubmitDialog.Item> items, String note, ExerciseDialog.Target target) {
        try {
            ProjectFiles.ensureSourceRoot(project, target.dir);
        } catch (Exception e) {
            Say.error(project, "לא הצלחתי להכין את תיקיית הקוד: " + e.getMessage(), TITLE);
            return;
        }
        File lib = Dsviz.libraryJar();
        JdkFinder.Found jdk = JdkFinder.find(null, 0);
        if (lib == null || jdk == null) {
            Say.error(project, lib == null ? "הספרייה dsviz.jar חסרה בתוסף — התקינו מחדש את DSViz."
                    : "לא נמצאה Java (JDK) — התקינו מחדש את DSViz (ההתקנה כוללת Java 21).", TITLE);
            return;
        }
        ProgressManager.getInstance().run(new Task.Modal(project, TITLE, true) {
            Outcome out;

            @Override
            public void run(ProgressIndicator indicator) {
                indicator.setIndeterminate(true);
                indicator.setText("מכין את הקבצים…");
                out = runCli(jdk, lib, target.dir, items, note, indicator);
            }

            @Override
            public void onSuccess() {
                finish(project, target, out);
            }
        });
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

    static Outcome runCli(JdkFinder.Found jdk, File lib, File out, List<SubmitDialog.Item> items, String note, ProgressIndicator indicator) {
        Outcome res = new Outcome();
        Process p = null;
        try {
            Map<String, Object> man = new LinkedHashMap<String, Object>();
            man.put("lang", "java");
            man.put("out", out.getAbsolutePath());
            man.put("store", Dsviz.solutionStore().getAbsolutePath());
            man.put("lib", lib.getAbsolutePath());
            man.put("java", jdk.java());
            man.put("note", note == null ? "" : note);
            man.put("client", CLIENT);
            List<Object> list = new ArrayList<Object>();
            for (SubmitDialog.Item it : items) {
                Map<String, Object> j = new LinkedHashMap<String, Object>();
                j.put("path", it.file.getAbsolutePath());
                j.put("name", it.name);
                list.add(j);
            }
            man.put("items", list);
            File manifest = File.createTempFile("dsviz-submit-", ".json");
            Files.write(manifest.toPath(), IjShared.toJson(man).getBytes(StandardCharsets.UTF_8));
            p = new ProcessBuilder(command(jdk.java(), lib, manifest)).redirectErrorStream(true).start();
            p.getOutputStream().close();
            final Process proc = p;
            Thread watch = new Thread(() -> {
                while (proc.isAlive()) {
                    if (indicator != null && indicator.isCanceled()) { proc.destroyForcibly(); return; }
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
                    if (m.containsKey("progress") && indicator != null) indicator.setText(IjShared.str(m.get("progress")));
                    if (m.containsKey("error")) res.error = IjShared.str(m.get("error"));
                    if (m.containsKey("done")) {
                        res.folder = IjShared.str(m.get("folder"));
                        res.focus = IjShared.str(m.get("focus"));
                        res.solutionStatus = IjShared.str(m.get("solutionStatus"));
                        res.line = (int) num(m.get("line"));
                        res.tasks = (int) num(m.get("tasks"));
                        for (Object o : IjShared.arr(m.get("files"))) res.files.add(IjShared.str(o));
                        for (Object o : IjShared.arr(m.get("errors"))) res.errors.add(IjShared.str(o));
                    }
                }
            }
            p.waitFor();
            manifest.delete();
            if (indicator != null && indicator.isCanceled()) res.error = res.files.isEmpty() ? "בוטל." : null;
            else if (res.error == null && res.files.isEmpty())
                res.error = "לא הצלחתי ליצור את התרגיל." + (other.length() > 0 ? "\n" + other.toString().trim() : "");
        } catch (Exception e) {
            res.error = "לא הצלחתי ליצור את התרגיל: " + e.getMessage();
            if (p != null) p.destroyForcibly();
        }
        return res;
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
        return text.replaceAll("[\u202A-\u202E\u2066-\u2069]", "");
    }

    private static double num(Object o) {
        return o instanceof Number ? ((Number) o).doubleValue() : 0;
    }

    /** אחרי השליחה: רענון, הספרייה במודול, פתיחת הקבצים (האחרון — עם הסעיף הראשון והסמן במקום הפתרון) */
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
        if (focus != null) {
            Module m = ModuleUtilCore.findModuleForFile(focus, project);
            if (m != null && LibrarySetup.needs(m)) LibrarySetup.add(m);
        }
        for (VirtualFile f : files) if (!f.equals(focus)) ProjectFiles.open(project, f, 0);
        if (focus != null) ProjectFiles.open(project, focus, out.line);
        DaemonCodeAnalyzer.getInstance(project).restart();
        if (!out.errors.isEmpty()) {
            Say.warn(project, "התרגיל נוצר (בתיקייה " + out.folder + "), אבל יש בו " + out.errors.size() + " שגיאות קומפילציה (מסומנות באדום בקוד):\n\n"
                    + String.join("\n", out.errors.subList(0, Math.min(4, out.errors.size())))
                    + "\n\nבדרך כלל זה ב-main (הנתונים לדוגמה) — אפשר לתקן, או לשלוח שוב.", TITLE);
        } else if (out.tasks == 0) {
            Say.info(project, "בתרגיל לא נמצאו סעיפים שצריך לכתוב בהם קוד — השאלות מופיעות בראש הקובץ.", TITLE);
        } else {
            String sol = out.solutionStatus.isEmpty() ? "" : "\nמעל כל פעולה של סעיף יש מנורה 💡 בצד שמאל — \"הצעה לפתרון\" (כדאי לנסות קודם לבד).";
            Dsviz.notify(project, "התרגיל נוצר — " + out.folder,
                    "\u200Fבתרגיל " + out.tasks + " סעיפים. הסמן מחכה במקום הפתרון של הסעיף הראשון, ו\"הרצה ויזואלית\" מראה כל שורה." + sol,
                    com.intellij.notification.NotificationType.INFORMATION);
        }
        ApplicationManager.getApplication().invokeLater(() -> DaemonCodeAnalyzer.getInstance(project).restart());
    }
}
