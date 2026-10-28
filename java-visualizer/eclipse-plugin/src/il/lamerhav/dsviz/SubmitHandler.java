package il.lamerhav.dsviz;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IncrementalProjectBuilder;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.eclipse.core.runtime.preferences.InstanceScope;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.dialogs.ProgressMonitorDialog;
import org.eclipse.jface.window.Window;
import org.eclipse.swt.program.Program;
import org.eclipse.swt.widgets.DirectoryDialog;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.handlers.HandlerUtil;

/**
 * "שליחת תרגיל": החלון (SubmitDialog) שולח את דף התרגיל ומקבל את התיאור שלו; כאן בונים ממנו את הקבצים — כל תרגיל בתיקייה משלו,
 * עם שם לפי סוג התרגיל (כדי שיהיה קל למצוא ולמחוק בסוף):
 *   Java — חבילה בפרויקט: src/ex01_stacks/... ; בודקים שהכול מתקמפל (ואם לא — תיקון אחד דרך השרת), ופותחים עם הסמן במקום הפתרון.
 *   C#   — פרויקט חדש בתיקיית התרגילים של Visual Studio (DataStructures-CS\Exercises\Ex01_Stacks), רשום בפתרון;
 *          בודקים קומפילציה עם dsviz-cs (אם הותקן) ומציעים לפתוח ב-Visual Studio.
 */
public class SubmitHandler extends AbstractHandler {
    private static final String NODE = "il.lamerhav.dsviz";

    @Override
    public Object execute(ExecutionEvent event) {
        Shell shell = HandlerUtil.getActiveShell(event);
        IWorkbenchPage page = HandlerUtil.getActiveWorkbenchWindow(event).getActivePage();
        WorkerClient client = WorkerClient.configured();
        SubmitDialog d = new SubmitDialog(shell, ProjectSetup.javaProjects(), ProjectSetup.freeProjectName(), Ui.currentProject(event), client);
        if (d.open() != Window.OK || d.exercise() == null) return null;
        if ("csharp".equals(Json.str(d.exercise().get("language"))) || "csharp".equals(d.language())) {
            csharp(shell, client, d);
            return null;
        }
        Build b = null;
        try {
            IJavaProject jp = d.project() != null ? d.project() : ProjectSetup.createProject(d.newProjectName());
            IContainer src = ProjectSetup.prepare(jp, Ui.resolver(shell));
            b = new Build(client, d.exercise(), d.sourceNames(), jp, src);
            new ProgressMonitorDialog(shell).run(true, true, b::run);
            open(page, b);
            if (!b.errors.isEmpty()) {
                MessageDialog.openWarning(shell, "שליחת תרגיל",
                        "התרגיל נוצר (בתיקייה " + b.folderName + "), אבל יש בו " + b.errors.size() + " שגיאות קומפילציה (מסומנות באדום בקוד):\n\n"
                        + String.join("\n", b.errors.subList(0, Math.min(4, b.errors.size())))
                        + "\n\nבדרך כלל זה ב-main (הנתונים לדוגמה) — אפשר לתקן, או לשלוח שוב.");
            } else if (b.tasks == 0) {
                MessageDialog.openInformation(shell, "שליחת תרגיל", "בתרגיל לא נמצאו סעיפים שצריך לכתוב בהם קוד — השאלות מופיעות בראש הקובץ.");
            }
        } catch (InvocationTargetException e) {
            Throwable t = e.getCause();
            MessageDialog.openError(shell, "שליחת תרגיל", "לא הצלחתי ליצור את התרגיל: " + (t != null ? t.getMessage() : e.getMessage()));
        } catch (InterruptedException e) {
            // בוטל באמצע — פותחים את מה שכבר נוצר
            try {
                if (b != null) open(page, b);
            } catch (CoreException ignored) {
                // לא נפתח — הקבצים בכל זאת בפרויקט
            }
        } catch (CoreException e) {
            Ui.error(shell, "לא הצלחתי ליצור את התרגיל.", e);
        }
        return null;
    }

    /** פותח את הקבצים: קודם המחלקות מהתרגיל, ובסוף הקובץ עם הסעיף הראשון — שיהיה הפעיל, והסמן מחכה במקום הפתרון */
    private static void open(IWorkbenchPage page, Build b) throws CoreException {
        if (b.files.isEmpty()) return;
        IFile focus = null;
        int line = 0;
        for (int i = 0; i < b.files.size(); i++) {
            if (b.generated.get(i).cursorLine >= 0) { focus = b.files.get(i); line = b.generated.get(i).cursorLine; }
        }
        if (focus == null) focus = b.files.get(0);
        for (int i = b.files.size() - 1; i >= 0; i--) if (!b.files.get(i).equals(focus)) Ui.openAt(page, b.files.get(i), 0);
        Ui.openAt(page, focus, line);
    }

    /** Java: בניית הקבצים בחבילה משלהם + בדיקת קומפילציה + תיקון אחד (רץ ברקע, עם חלון התקדמות) */
    static final class Build {
        final WorkerClient client;
        final List<String> sources;
        final IJavaProject jp;
        final IContainer src;
        Map<String, Object> raw;
        String folderName = "";
        final List<AiScaffold.GeneratedFile> generated = new ArrayList<AiScaffold.GeneratedFile>();
        final List<IFile> files = new ArrayList<IFile>();
        final List<String> errors = new ArrayList<String>();
        int tasks;

        Build(WorkerClient client, Map<String, Object> raw, List<String> sources, IJavaProject jp, IContainer src) {
            this.client = client;
            this.raw = raw;
            this.sources = sources;
            this.jp = jp;
            this.src = src;
        }

        void run(IProgressMonitor m) throws InvocationTargetException, InterruptedException {
            try {
                m.beginTask("בונה את התרגיל…", 5);
                Set<String> taken = new HashSet<String>();
                for (IResource r : src.members()) taken.add(r.getName());
                AiScaffold.Exercise e = AiScaffold.read(raw);
                String pkg = AiScaffold.folderName(e.topic, taken, false);
                folderName = pkg;
                IFolder folder = src.getFolder(new Path(pkg));
                write(e, pkg, folder);
                m.worked(1);
                m.subTask("בודק שהקוד מתקמפל…");
                compile(folder, m);
                m.worked(1);
                if (!errors.isEmpty() && client != null && !m.isCanceled()) {
                    m.subTask("יש שגיאות קומפילציה — מבקש תיקון…");
                    Map<String, String> code = new LinkedHashMap<String, String>();
                    for (AiScaffold.GeneratedFile g : generated) code.put(g.fileName(), g.code);
                    try {
                        Map<String, Object> fixed = client.repair(raw, code, errors);
                        raw = fixed;
                        AiScaffold.Exercise e2 = AiScaffold.read(fixed);
                        for (IFile f : files) f.delete(true, null);
                        files.clear();
                        generated.clear();
                        write(e2, pkg, folder);
                        m.worked(1);
                        m.subTask("בודק שוב…");
                        compile(folder, m);
                    } catch (WorkerClient.AiError ignored) {
                        // התיקון לא הצליח — נשארים עם הקבצים כמו שהם (והשגיאות מוצגות)
                    }
                }
                m.done();
            } catch (CoreException e) {
                throw new InvocationTargetException(e, e.getStatus().getMessage());
            }
        }

        private void write(AiScaffold.Exercise e, String pkg, IFolder folder) throws CoreException {
            tasks = e.tasks.size();
            if (!folder.exists()) folder.create(true, true, null);
            for (AiScaffold.GeneratedFile g : AiScaffold.build(e, pkg, sources)) {
                IFile f = folder.getFile(g.fileName());
                ByteArrayInputStream in = new ByteArrayInputStream(g.code.getBytes(StandardCharsets.UTF_8));
                if (f.exists()) f.setContents(in, true, false, null);
                else f.create(in, true, null);
                f.setCharset("UTF-8", null);
                files.add(f);
                generated.add(g);
            }
        }

        /** קומפילציה של הפרויקט ואיסוף השגיאות בקבצים החדשים: "Targil.java:12: ..." */
        private void compile(IFolder folder, IProgressMonitor m) throws CoreException {
            errors.clear();
            jp.getProject().build(IncrementalProjectBuilder.INCREMENTAL_BUILD, m);
            for (IMarker mk : folder.findMarkers(IMarker.PROBLEM, true, IResource.DEPTH_INFINITE)) {
                if (mk.getAttribute(IMarker.SEVERITY, 0) != IMarker.SEVERITY_ERROR) continue;
                errors.add(mk.getResource().getName() + ":" + mk.getAttribute(IMarker.LINE_NUMBER, 0) + ": " + mk.getAttribute(IMarker.MESSAGE, ""));
                if (errors.size() >= 30) break;
            }
        }
    }

    // ------------------------------------------------------------------ C#

    private static void csharp(Shell shell, WorkerClient client, SubmitDialog d) {
        java.nio.file.Path root = csRoot(shell);
        if (root == null) return;
        CsBuild b = new CsBuild(client, d.exercise(), d.sourceNames(), root);
        try {
            new ProgressMonitorDialog(shell).run(true, true, b::run);
        } catch (InvocationTargetException e) {
            Throwable t = e.getCause();
            MessageDialog.openError(shell, "שליחת תרגיל", "לא הצלחתי ליצור את התרגיל ב-C#: " + (t != null ? t.getMessage() : e.getMessage()));
            return;
        } catch (InterruptedException e) {
            if (b.dir == null) return;
        }
        if (b.dir == null) return;
        StringBuilder msg = new StringBuilder();
        msg.append("נוצר התרגיל ב-C#: ").append(b.name).append("\n").append(b.dir).append("\n\n");
        if (!b.errors.isEmpty()) {
            msg.append("שימו לב: יש בו ").append(b.errors.size()).append(" שגיאות קומפילציה:\n")
               .append(String.join("\n", b.errors.subList(0, Math.min(3, b.errors.size())))).append("\n\n");
        } else if (!b.checked) {
            msg.append("(לא בדקתי קומפילציה — חבילת C# של DSViz לא נמצאה במחשב הזה.)\n\n");
        }
        msg.append("ב-Visual Studio: בחלון Solution Explorer לחצו קליק ימני על ").append(b.name).append(" ← Set as Startup Project,\n")
           .append("ואז בתפריט שליד כפתור ההפעלה הירוק בחרו \"הרצה ויזואלית\". הסעיף הראשון מחכה ב-").append(b.focusFile).append(".\n")
           .append("(אם Visual Studio כבר פתוח — אשרו Reload כשהוא שואל.)");
        java.nio.file.Path sln = CsSolution.solution(root);
        String[] buttons = sln != null ? new String[] { "פתיחה ב-Visual Studio", "פתיחת התיקייה", "סגירה" } : new String[] { "פתיחת התיקייה", "סגירה" };
        int r = new MessageDialog(shell, "שליחת תרגיל — C#", null, msg.toString(),
                b.errors.isEmpty() ? MessageDialog.INFORMATION : MessageDialog.WARNING, buttons, 0).open();
        if (sln != null && r == 0) Program.launch(sln.toString());
        else if ((sln != null && r == 1) || (sln == null && r == 0)) Program.launch(b.dir.toString());
    }

    /** C#: הפרויקט נוצר + נבדק עם dsviz-cs --check-project + תיקון אחד */
    static final class CsBuild {
        final WorkerClient client;
        final List<String> sources;
        final java.nio.file.Path root;
        Map<String, Object> raw;
        java.nio.file.Path dir;
        String name = "", focusFile = "";
        boolean checked;
        final List<String> errors = new ArrayList<String>();

        CsBuild(WorkerClient client, Map<String, Object> raw, List<String> sources, java.nio.file.Path root) {
            this.client = client;
            this.raw = raw;
            this.sources = sources;
            this.root = root;
        }

        void run(IProgressMonitor m) throws InvocationTargetException, InterruptedException {
            try {
                m.beginTask("בונה את התרגיל ב-C#…", 4);
                AiScaffold.Exercise e = AiScaffold.read(raw);
                Set<String> taken = new HashSet<String>();
                java.nio.file.Path ex = CsSolution.exercisesDir(root);
                if (Files.isDirectory(ex)) try (java.util.stream.Stream<java.nio.file.Path> s = Files.list(ex)) {
                    s.forEach(p -> taken.add(p.getFileName().toString()));
                }
                name = AiScaffold.folderName(e.topic, taken, true);
                while (CsSolution.taken(root, name)) {
                    taken.add(name);
                    name = AiScaffold.folderName(e.topic, taken, true);
                }
                dir = CsSolution.write(root, name, files(e));
                m.worked(1);
                java.io.File exe = dsvizCs();
                if (exe == null) return;
                m.subTask("בודק שהקוד מתקמפל…");
                check(exe);
                m.worked(1);
                if (!errors.isEmpty() && client != null && !m.isCanceled()) {
                    m.subTask("יש שגיאות קומפילציה — מבקש תיקון…");
                    Map<String, String> code = new LinkedHashMap<String, String>();
                    for (AiScaffold.GeneratedFile g : AiScaffold.build(e, name, sources)) code.put(g.fileName(), g.code);
                    try {
                        raw = client.repair(raw, code, errors);
                        AiScaffold.Exercise e2 = AiScaffold.read(raw);
                        try (java.util.stream.Stream<java.nio.file.Path> s = Files.list(dir)) {   // מחליפים את קובצי הקוד (הפרויקט כבר רשום בפתרון)
                            for (java.nio.file.Path p : (Iterable<java.nio.file.Path>) s::iterator)
                                if (p.toString().endsWith(".cs")) Files.delete(p);
                        }
                        for (Map.Entry<String, String> f : files(e2).entrySet())
                            Files.write(dir.resolve(f.getKey()), ("﻿" + f.getValue()).getBytes(StandardCharsets.UTF_8));
                        m.subTask("בודק שוב…");
                        check(exe);
                    } catch (WorkerClient.AiError ignored) {
                        // התיקון לא הצליח — נשארים עם הקבצים כמו שהם
                    }
                }
                m.done();
            } catch (IOException e) {
                throw new InvocationTargetException(e, e.getMessage());
            }
        }

        private Map<String, String> files(AiScaffold.Exercise e) {
            Map<String, String> res = new LinkedHashMap<String, String>();
            for (AiScaffold.GeneratedFile g : AiScaffold.build(e, name, sources)) {
                res.put(g.fileName(), g.code);
                if (g.cursorLine >= 0 || focusFile.isEmpty()) focusFile = g.fileName();
            }
            return res;
        }

        /** dsviz-cs --check-project: "File.cs(12,5): error CS0103: ..." → "File.cs:12: CS0103 ..." */
        private void check(java.io.File exe) throws IOException, InterruptedException {
            errors.clear();
            String out;
            java.io.File log = java.io.File.createTempFile("dsviz-check", ".txt");
            try {
                Process p = new ProcessBuilder(exe.getPath(), "--check-project", dir.toString()).redirectErrorStream(true).redirectOutput(log).start();
                if (!p.waitFor(90, TimeUnit.SECONDS)) {   // לא מחכים לנצח — בלי בדיקה
                    p.destroyForcibly();
                    return;
                }
                out = new String(Files.readAllBytes(log.toPath()), StandardCharsets.UTF_8);
            } finally {
                log.delete();
            }
            checked = out.contains("errors: ");
            Matcher mt = Pattern.compile("(?m)^(\\S+?\\.cs)\\((\\d+),\\d+\\): error (CS\\d+): (.*)$").matcher(out);
            while (mt.find() && errors.size() < 30) errors.add(mt.group(1) + ":" + mt.group(2) + ": " + mt.group(3) + " " + mt.group(4).trim());
        }
    }

    /** dsviz-cs.exe (ההרצה הוויזואלית של C#) — ההתקנה כותבת את המיקום שלו ב-‎~/.dsviz/cs-path.txt */
    static java.io.File dsvizCs() {
        String p = readHome("cs-path.txt");
        java.io.File f = p == null ? null : new java.io.File(p);
        if (f != null && f.isFile()) return f;
        String local = System.getenv("LOCALAPPDATA");
        f = local == null ? null : new java.io.File(local, "DSViz\\cs\\dsviz-cs.exe");
        return f != null && f.isFile() ? f : null;
    }

    /**
     * תיקיית התרגילים של C# (DataStructures-CS): מה שנבחר בפעם הקודמת, מה שההתקנה רשמה (‎~/.dsviz/project-cs-path.txt),
     * המסמכים של המשתמש — ואם לא נמצאה, שואלים.
     */
    static java.nio.file.Path csRoot(Shell shell) {
        IEclipsePreferences prefs = InstanceScope.INSTANCE.getNode(NODE);
        List<String> candidates = new ArrayList<String>();
        candidates.add(prefs.get("csFolder", ""));
        candidates.add(readHome("project-cs-path.txt"));
        String home = System.getProperty("user.home");
        try {
            candidates.add(new File(javax.swing.filechooser.FileSystemView.getFileSystemView().getDefaultDirectory(), "DataStructures-CS").getPath());
        } catch (Throwable ignored) {
            // בלי AWT — ממשיכים לאפשרויות האחרות
        }
        candidates.add(new File(home, "Documents\\DataStructures-CS").getPath());
        candidates.add(new File(home, "OneDrive\\Documents\\DataStructures-CS").getPath());
        candidates.add(new File(home, "OneDrive\\מסמכים\\DataStructures-CS").getPath());
        candidates.add(new File(home, "Documents/DataStructures-CS").getPath());
        for (String c : candidates) {
            if (c == null || c.isEmpty()) continue;
            java.nio.file.Path p = java.nio.file.Paths.get(c);
            if (CsSolution.check(p) == null) return p;
        }
        DirectoryDialog dd = new DirectoryDialog(shell);
        dd.setText("תיקיית התרגילים של C#");
        dd.setMessage("לא מצאתי את תיקיית התרגילים של C# (DataStructures-CS, במסמכים). בחרו אותה — או התקינו את חבילת C# ב-DSViz-Setup.");
        String chosen = dd.open();
        if (chosen == null) return null;
        java.nio.file.Path p = java.nio.file.Paths.get(chosen);
        String bad = CsSolution.check(p);
        if (bad != null) {
            MessageDialog.openError(shell, "שליחת תרגיל — C#", bad + "\n\nצריך את התיקייה DataStructures-CS שההתקנה יוצרת (עם חבילת C#).");
            return null;
        }
        prefs.put("csFolder", p.toString());
        try {
            prefs.flush();
        } catch (Exception ignored) {
            // לא נשמר — נשאל שוב בפעם הבאה
        }
        return p;
    }

    /** קובץ קטן שההתקנה כותבת ב-‎~/.dsviz (UTF-16 עם BOM, או UTF-8) — השורה הראשונה, או null */
    private static String readHome(String name) {
        try {
            byte[] b = Files.readAllBytes(java.nio.file.Paths.get(System.getProperty("user.home"), ".dsviz", name));
            String s;
            if (b.length >= 2 && (b[0] & 0xff) == 0xff && (b[1] & 0xff) == 0xfe) s = new String(b, 2, b.length - 2, StandardCharsets.UTF_16LE);
            else if (b.length >= 3 && (b[0] & 0xff) == 0xef) s = new String(b, 3, b.length - 3, StandardCharsets.UTF_8);
            else s = new String(b, StandardCharsets.UTF_8);
            s = s.trim();
            int nl = s.indexOf('\n');
            return (nl >= 0 ? s.substring(0, nl) : s).trim();
        } catch (IOException e) {
            return null;
        }
    }
}
