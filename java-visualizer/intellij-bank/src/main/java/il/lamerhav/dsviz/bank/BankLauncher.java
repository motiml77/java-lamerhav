package il.lamerhav.dsviz.bank;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.compiler.CompilerPaths;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;

import il.lamerhav.dsviz.intellij.Dsviz;
import il.lamerhav.dsviz.intellij.JdkFinder;

/**
 * "מאגר תרגילים" ב-IntelliJ: מפעיל את החלונות (אותו כלי כמו ב-Visual Studio — dsviz-bank-vs.jar במצב --ij, בתהליך נפרד),
 * ומקבל ממנו בקשות בגשר מקומי (127.0.0.1, עם token אקראי לכל פרויקט — רק הכלי שהופעל מכאן יכול לבקש):
 *   OPEN <קובץ> <שורה>  — פותח את הקובץ בעורך, והסמן בשורה (מ-1)
 *   DIALOG              — "1" אם פתוח חלון דו-שיח של IntelliJ (החלון הצף יורד אז אחורה, כדי לא להסתיר אותו)
 *   REFRESH <תיקייה>    — רענון (קבצים שנוצרו / נמחקו מופיעים / נעלמים, והלשוניות שלהם נסגרות)
 * התרגיל נוצר בתיקיית הקוד (src) של הפרויקט, כחבילה ex01_... — כמו "שליחת תרגיל".
 */
public final class BankLauncher {
    private BankLauncher() {}

    static final String PLUGIN_ID = "il.lamerhav.dsviz.bank";
    static final String TITLE = "מאגר תרגילים";

    /** הכלי והגשר של כל פרויקט פתוח */
    static final class Session implements Disposable {
        final Project project;
        final String token;
        ServerSocket server;
        Process proc;
        /** לבדיקות: כל בקשה שהגיעה */
        final List<String> received = Collections.synchronizedList(new ArrayList<String>());

        Session(Project project) {
            this.project = project;
            byte[] b = new byte[16];
            new SecureRandom().nextBytes(b);
            StringBuilder t = new StringBuilder();
            for (byte x : b) t.append(String.format("%02x", x));
            token = t.toString();
        }

        int port() {
            return server == null ? 0 : server.getLocalPort();
        }

        @Override
        public void dispose() {
            if (proc != null && proc.isAlive()) proc.destroy();
            try {
                if (server != null) server.close();
            } catch (Exception ignored) {
                // נסגר
            }
            SESSIONS.remove(project);
        }
    }

    static final Map<Project, Session> SESSIONS = new ConcurrentHashMap<Project, Session>();

    /** הכפתור: mode = bank (חלון המאגר) או question (החלון הצף של השאלה האחרונה) */
    public static void open(Project project, String mode) {
        String bad = problem(project);
        if (bad != null) {
            Messages.showErrorDialog(project, bad, TITLE);
            return;
        }
        try {
            Session s = session(project);
            if (s.proc != null && s.proc.isAlive()) {
                s.proc.destroy();   // חלון אחד בכל פעם: פותחים מחדש במצב שנבחר
                s.proc.waitFor(3, java.util.concurrent.TimeUnit.SECONDS);
            }
            List<String> cmd = command(project, s, mode, null);
            s.proc = new ProcessBuilder(cmd).redirectErrorStream(true)
                    .redirectOutput(ProcessBuilder.Redirect.appendTo(new File(System.getProperty("java.io.tmpdir"), "dsviz-bank-ij.log"))).start();
        } catch (Exception e) {
            Messages.showErrorDialog(project, "לא הצלחתי לפתוח את מאגר התרגילים: " + e.getMessage(), TITLE);
        }
    }

    /** מה חסר כדי לפתוח (בעברית), או null */
    static String problem(Project project) {
        if (project == null) return "פותחים פרויקט Java ואז לוחצים \"מאגר תרגילים\".";
        if (libJar() == null) return "הספרייה dsviz.jar חסרה בתוסף DSViz. התקינו מחדש את DSViz.";
        if (bankJar() == null) return "הקובץ dsviz-bank-vs.jar חסר בתוסף מאגר התרגילים. התקינו אותו מחדש.";
        JdkFinder.Found jdk = jdk(project);
        if (jdk == null || jdk.javac() == null) return "לא נמצאה Java (JDK) עם javac. התקינו מחדש את DSViz (ההתקנה כוללת Java 21).";
        return null;
    }

    static JdkFinder.Found jdk(Project project) {
        Sdk sdk = ProjectRootManager.getInstance(project).getProjectSdk();
        return JdkFinder.find(sdk == null ? null : sdk.getHomePath(), 0);
    }

    /** dsviz.jar של התוסף הראשי: מה ש-DSViz מוצא בעצמו, או runtime/dsviz.jar בתיקייה של התוסף הראשי (לפי IntelliJ) */
    static File libJar() {
        File f = Dsviz.libraryJar();
        if (f != null) return f;
        IdeaPluginDescriptor d = PluginManagerCore.getPlugin(PluginId.getId(Dsviz.PLUGIN_ID));
        if (d == null) return null;
        File g = d.getPluginPath().resolve("runtime").resolve(Dsviz.JAR_NAME).toFile();
        return g.isFile() ? g : null;
    }

    /** dsviz-bank-vs.jar שבתוך התוסף הזה (runtime/). בבדיקות: ‎-Ddsviz.bankJar */
    static File bankJar() {
        String prop = System.getProperty("dsviz.bankJar");
        if (prop != null && new File(prop).isFile()) return new File(prop);
        IdeaPluginDescriptor d = PluginManagerCore.getPlugin(PluginId.getId(PLUGIN_ID));
        if (d == null) return null;
        Path p = d.getPluginPath().resolve("runtime").resolve("dsviz-bank-vs.jar");
        return p.toFile().isFile() ? p.toFile() : null;
    }

    /**
     * תיקיית הקוד שבה נוצר התרגיל: מבין תיקיות המקור (לא בדיקות / משאבים) — עדיפות ל-src/main/java (Maven / Gradle), אחר כך
     * לתיקייה ששמה src, ואחר כך לראשונה. בלי תיקיות מקור — src שבתיקיית הפרויקט.
     */
    static File sourceRoot(Project project) {
        File best = null;
        int score = -1;
        for (Module m : ModuleManager.getInstance(project).getModules()) {
            for (VirtualFile r : ModuleRootManager.getInstance(m).getSourceRoots(false)) {
                if (!r.isDirectory() || !r.isInLocalFileSystem() || r.getPath().contains("/resources")) continue;
                String p = r.getPath();
                int sc = p.endsWith("/src/main/java") ? 3 : r.getName().equals("src") ? 2 : 1;
                if (sc > score) {
                    score = sc;
                    best = new File(p);
                }
            }
        }
        return best != null ? best : new File(project.getBasePath(), "src");
    }

    /** תיקיות הפלט של הקומפילציה (out/production/<מודול>) — שם מוחקים רק את ה-class של קבצי התרגיל */
    static List<File> outDirs(Project project) {
        Set<File> out = new LinkedHashSet<File>();
        for (Module m : ModuleManager.getInstance(project).getModules()) {
            String url = CompilerPaths.getModuleOutputPath(m, false);
            if (url != null) out.add(new File(url));
            if (project.getBasePath() != null) out.add(new File(project.getBasePath(), "out/production/" + m.getName()));
        }
        return new ArrayList<File>(out);
    }

    static Session session(Project project) throws Exception {
        Session s = SESSIONS.get(project);
        if (s != null && s.server != null && !s.server.isClosed()) return s;
        final Session ns = new Session(project);
        ns.server = new ServerSocket(0, 20, InetAddress.getLoopbackAddress());
        Thread t = new Thread(() -> serve(ns), "dsviz-bank-bridge");
        t.setDaemon(true);
        t.start();
        Disposer.register(project, ns);
        SESSIONS.put(project, ns);
        return ns;
    }

    /** הפקודה שמפעילה את הכלי (mainClass — בבדיקות: מחלקה אחרת עם תסריט) */
    static List<String> command(Project project, Session s, String mode, String mainClass) {
        JdkFinder.Found jdk = jdk(project);
        String java = jdk.java();
        if (SystemInfo.isWindows) {   // javaw — בלי חלון קונסול שחור
            File w = new File(new File(java).getParentFile(), "javaw.exe");
            if (w.isFile()) java = w.getPath();
        }
        File src = sourceRoot(project);
        List<String> outs = new ArrayList<String>();
        for (File f : outDirs(project)) outs.add(f.getPath());
        List<String> cmd = new ArrayList<String>();
        cmd.add(java);
        cmd.add("-Dfile.encoding=UTF-8");
        cmd.add("-cp");
        String extra = System.getProperty("dsviz.bankAutotestCp");
        cmd.add(libJar().getPath() + File.pathSeparator + bankJar().getPath() + (extra == null || extra.isEmpty() ? "" : File.pathSeparator + extra));
        cmd.add(mainClass == null ? "il.lamerhav.dsviz.BankVs" : mainClass);
        cmd.add(mode);
        cmd.add(src.getPath());
        cmd.add("--ij");
        cmd.add("--lib");
        cmd.add(libJar().getPath());
        cmd.add("--java");
        cmd.add(jdk.java());
        cmd.add("--store");
        cmd.add(Dsviz.solutionStore().getPath());
        cmd.add("--port");
        cmd.add(String.valueOf(s.port()));
        cmd.add("--token");
        cmd.add(s.token);
        cmd.add("--project");
        cmd.add(project.getName());
        cmd.add("--out");
        cmd.add(String.join(File.pathSeparator, outs));
        return cmd;
    }

    // ------------------------------------------------------------------ הגשר

    static void serve(Session s) {
        while (!s.server.isClosed()) {
            try (Socket so = s.server.accept()) {
                so.setSoTimeout(10000);
                String line = new BufferedReader(new InputStreamReader(so.getInputStream(), StandardCharsets.UTF_8)).readLine();
                String reply = handle(s, line);
                Writer w = new OutputStreamWriter(so.getOutputStream(), StandardCharsets.UTF_8);
                w.write(reply + "\n");
                w.flush();
            } catch (Exception e) {
                // חיבור שנכשל — ממשיכים להקשיב (עד שהפרויקט נסגר)
            }
        }
    }

    /** בקשה אחת: "token\tפקודה\tארגומנטים". מחזיר את התשובה */
    static String handle(Session s, String line) {
        if (line == null) return "EMPTY";
        String[] p = line.split("\t", -1);
        if (p.length < 2 || !p[0].equals(s.token)) return "DENIED";
        if (!p[1].equals("DIALOG")) s.received.add(line.substring(p[0].length() + 1));
        final Project project = s.project;
        switch (p[1]) {
            case "OPEN": {
                final File f = new File(p.length > 2 ? p[2] : "");
                final int ln = p.length > 3 ? parse(p[3]) : 0;
                ApplicationManager.getApplication().invokeLater(() -> {
                    VirtualFile vf = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(f);
                    if (vf == null || project.isDisposed()) return;
                    new OpenFileDescriptor(project, vf, Math.max(0, ln - 1), 0).navigate(true);
                    ensureLibrary(project, vf);
                    BankAutotest.bringToFront(project);
                });
                return "OK";
            }
            case "DIALOG": {
                // חלון דו-שיח של IntelliJ פתוח (למשל שגיאות בהרצה ויזואלית)? — אז החלון הצף יורד אחורה, כדי לא להסתיר אותו
                for (java.awt.Window w : java.awt.Window.getWindows())
                    if (w instanceof java.awt.Dialog && ((java.awt.Dialog) w).isModal() && w.isShowing()) return "1";
                return "0";
            }
            case "REFRESH": {
                final File f = new File(p.length > 2 ? p[2] : project.getBasePath());
                ApplicationManager.getApplication().invokeLater(() -> {
                    VirtualFile vf = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(f);
                    if (vf != null) VfsUtil.markDirtyAndRefresh(true, true, true, vf);
                    else LocalFileSystem.getInstance().refresh(true);
                });
                return "OK";
            }
            default:
                return BankAutotest.handle(s, p);
        }
    }

    /**
     * ספריית הקורס (unit4.collectionsLib) במודול של הקובץ — כמו אחרי "שליחת תרגיל": התוסף הראשי מוסיף אותה לבד רק למודול שכבר
     * משתמש בה, ותרגיל חדש מהמאגר הוא לפעמים הקובץ הראשון שמשתמש בה (בלי זה הבנייה נכשלת: package unit4.collectionsLib does not exist)
     */
    static void ensureLibrary(Project project, VirtualFile vf) {
        // מיד אחרי שהתרגיל נוצר IntelliJ עוד בונה אינדקס לקבצים החדשים ("dumb mode") — ואז אי אפשר לבדוק; מחכים שיסיים
        com.intellij.openapi.project.DumbService.getInstance(project).runWhenSmart(() -> {
            try {
                if (project.isDisposed() || !vf.isValid()) return;
                com.intellij.openapi.module.Module m = com.intellij.openapi.module.ModuleUtilCore.findModuleForFile(vf, project);
                if (m == null || libJar() == null) return;
                restoreJar(m);
                boolean need = com.intellij.openapi.application.ReadAction.compute(() -> il.lamerhav.dsviz.intellij.LibrarySetup.needs(m));
                if (need) il.lamerhav.dsviz.intellij.LibrarySetup.add(m);
            } catch (Throwable ignored) {
                // ההרצה הוויזואלית של DSViz מנסה גם היא להוסיף את הספרייה
            }
        });
    }

    /**
     * הספרייה רשומה במודול, אבל הקובץ שלה (lib/dsviz.jar בתיקיית הפרויקט) נמחק — אז IntelliJ לא מכיר את Stack / Queue והבנייה
     * נכשלת ("package unit4.collectionsLib does not exist"). מחזירים את הקובץ למקום שהספרייה מצביעה עליו.
     */
    static void restoreJar(com.intellij.openapi.module.Module m) {
        File src = libJar();
        if (src == null) return;
        List<File> missing = com.intellij.openapi.application.ReadAction.compute(() -> {
            List<File> l = new ArrayList<File>();
            if (m.isDisposed()) return l;
            for (com.intellij.openapi.roots.OrderEntry e : ModuleRootManager.getInstance(m).getOrderEntries()) {
                if (!(e instanceof com.intellij.openapi.roots.LibraryOrderEntry) || !"dsviz".equals(((com.intellij.openapi.roots.LibraryOrderEntry) e).getLibraryName())) continue;
                for (String url : ((com.intellij.openapi.roots.LibraryOrderEntry) e).getRootUrls(com.intellij.openapi.roots.OrderRootType.CLASSES)) {
                    String path = com.intellij.openapi.vfs.VfsUtilCore.urlToPath(url);
                    if (path.endsWith("!/")) path = path.substring(0, path.length() - 2);
                    File f = new File(path);
                    if (f.getName().equals(Dsviz.JAR_NAME) && !f.isFile()) l.add(f);
                }
            }
            return l;
        });
        for (File f : missing) {
            try {
                f.getParentFile().mkdirs();
                java.nio.file.Files.copy(src.toPath(), f.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                VirtualFile vf = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(f);
                if (vf != null) {
                    VfsUtil.markDirtyAndRefresh(false, true, true, vf);
                    VirtualFile jarRoot = com.intellij.openapi.vfs.JarFileSystem.getInstance().getJarRootForLocalFile(vf);
                    if (jarRoot != null) VfsUtil.markDirtyAndRefresh(false, true, true, jarRoot);
                }
            } catch (Exception ignored) {
                // אי אפשר לכתוב שם — נשאר כמו שהיה
            }
        }
    }

    static int parse(String s) {
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
