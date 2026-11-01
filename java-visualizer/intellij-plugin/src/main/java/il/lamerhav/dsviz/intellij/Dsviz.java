package il.lamerhav.dsviz.intellij;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.SystemInfo;

import javax.swing.Icon;

/** כלים משותפים לתוסף של IntelliJ: הספרייה שבתוך התוסף, קבצים שההתקנה כתבה, כיבוי/הפעלה, הודעות */
public final class Dsviz {
    private Dsviz() {}

    public static final String PLUGIN_ID = "il.lamerhav.dsviz";
    public static final String JAR_NAME = "dsviz.jar";
    public static final String LINE_RUNNER = "unit4.collectionsLib.LineByLine";
    public static final String VERSION = "1.0.1";
    private static final String POWER_KEY = "il.lamerhav.dsviz.enabled";

    public static final Icon RUN = icon("run"), NEW = icon("new"), INSERT = icon("insert"), SEND = icon("send"), HELP = icon("help"),
            EXAMPLES = icon("examples"), SOLUTION = icon("solution"), POWER_ON = icon("power-on"), POWER_OFF = icon("power-off");

    private static Icon icon(String name) {
        return IconLoader.getIcon("/icons/" + name + ".png", Dsviz.class);
    }

    // ------------------------------------------------------------------ כיבוי / הפעלה

    /** DSViz פועל? (הלחצן "כיבוי" — כל הכפתורים והיכולות נכבים, ונשאר רק IntelliJ) */
    public static boolean on() {
        try {
            return PropertiesComponent.getInstance().getBoolean(POWER_KEY, true);
        } catch (Throwable e) {
            return true;
        }
    }

    public static void setOn(boolean on) {
        PropertiesComponent.getInstance().setValue(POWER_KEY, on, true);
    }

    // ------------------------------------------------------------------ הספרייה

    /** התיקייה של התוסף (‎…/plugins/dsviz-intellij) — לפי המקום של ה-jar של התוסף (lib/…jar), או null בבדיקות */
    public static Path pluginDir() {
        try {
            Path jar = Path.of(Dsviz.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            Path dir = jar.getParent() == null ? null : jar.getParent().getParent();
            return dir != null && Files.isDirectory(dir.resolve("runtime")) ? dir : null;
        } catch (Throwable e) {
            return null;
        }
    }

    /** dsviz.jar שבתוך התוסף (runtime/dsviz.jar). בבדיקות: ‎-Ddsviz.lib */
    public static File libraryJar() {
        String prop = System.getProperty("dsviz.lib");
        if (prop != null && new File(prop).isFile()) return new File(prop);
        Path dir = pluginDir();
        if (dir != null) {
            File f = dir.resolve("runtime").resolve(JAR_NAME).toFile();
            if (f.isFile()) return f;
        }
        return null;
    }

    // ------------------------------------------------------------------ קבצים

    /** קובץ שתוכנית ההתקנה כתבה ב-~/.dsviz (Windows: UTF-16 עם BOM; Mac/Linux: UTF-8), או null */
    public static String dsvizFile(String name) {
        try {
            File f = new File(System.getProperty("user.home"), ".dsviz/" + name);
            if (!f.isFile()) return null;
            byte[] b = Files.readAllBytes(f.toPath());
            boolean utf16 = b.length >= 2 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xFE;
            String text = new String(b, utf16 ? StandardCharsets.UTF_16LE : StandardCharsets.UTF_8).trim();
            if (!text.isEmpty() && text.charAt(0) == '﻿') text = text.substring(1);
            return text.isEmpty() ? null : text;
        } catch (Exception e) {
            return null;
        }
    }

    /** תיקיית ההתקנה של DSViz (Windows: ‎%LOCALAPPDATA%\DSViz; Mac: ‎~/Library/Application Support/DSViz; Linux: ‎~/DSViz) */
    public static File installDir() {
        String fromFile = dsvizFile("install-dir.txt");
        if (fromFile != null && new File(fromFile).isDirectory()) return new File(fromFile);
        String local = System.getenv("LOCALAPPDATA");
        File home = new File(System.getProperty("user.home"));
        if (SystemInfo.isWindows && local != null) return new File(local, "DSViz");
        if (SystemInfo.isMac) return new File(home, "Library/Application Support/DSViz");
        return new File(home, "DSViz");
    }

    /** ההצעות לפתרון — אותו קובץ כמו ב-Visual Studio (‎~/.dsviz/solutions.json) */
    public static File solutionStore() {
        String prop = System.getProperty("dsviz.solutions");   // לבדיקות
        if (prop != null && !prop.isEmpty()) return new File(prop);
        return new File(System.getProperty("user.home"), ".dsviz" + File.separator + "solutions.json");
    }

    public static byte[] resource(String name) throws IOException {
        try (InputStream in = Dsviz.class.getResourceAsStream(name)) {
            if (in == null) throw new IOException("קובץ חסר בתוסף: " + name);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            for (int n; (n = in.read(buf)) > 0;) out.write(buf, 0, n);
            return out.toByteArray();
        }
    }

    public static String resourceText(String name) throws IOException {
        return new String(resource(name), StandardCharsets.UTF_8);
    }

    // ------------------------------------------------------------------ הודעות

    /** הודעה (בלון) — התוכן תמיד מימין לשמאל (‎<div dir='rtl'>) */
    public static Notification notify(Project project, String title, String text, NotificationType type) {
        Notification n = NotificationGroupManager.getInstance().getNotificationGroup("DSViz").createNotification(title, rtlHtml(text), type);
        n.notify(project);
        return n;
    }

    /** תוכן של הודעה: עטוף ב-div מימין לשמאל ומיושר לימין (אם הוא עוד לא כזה) */
    static String rtlHtml(String html) {
        String t = html == null ? "" : html.trim();
        if (t.startsWith("<div dir='rtl'") || t.startsWith("<div dir=\"rtl\"")) return t;
        return "<div dir='rtl' style='text-align:right'>\u200F" + t + "</div>";
    }

    /** טקסט עברי ב-HTML מימין לשמאל (לחלונות של IntelliJ) */
    public static String rtl(String text) {
        return "<html><div dir='rtl' style='text-align:right'>" + text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\n", "<br>") + "</div></html>";
    }
}
