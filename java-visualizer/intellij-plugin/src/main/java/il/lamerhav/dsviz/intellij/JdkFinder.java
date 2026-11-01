package il.lamerhav.dsviz.intellij;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.util.SystemInfo;

/**
 * איזו Java מריצה את ההרצה הוויזואלית. הרצה שורה-שורה צריכה JDK עם ממשק הדיבאג (jdk.jdi) — כמו ב-Eclipse וב-VS Code:
 * הגדרה (il.lamerhav.dsviz.javaHome) → ה-Java 21 שההתקנה התקינה (‎~/.dsviz/jdk-path.txt) → ה-SDK של הפרויקט אם יש בו jdk.jdi
 * → JAVA_HOME → ה-SDK של הפרויקט בכל מקרה (בלי jdk.jdi — LineByLine מריץ בלי שורות, ורק הפעולות על המבנים מוצגות).
 * מדלגים על Java ישנה מהגרסה שהפרויקט מקומפל אליה (אחרת: UnsupportedClassVersionError).
 */
public final class JdkFinder {
    private JdkFinder() {}

    public static final String SETTING = "il.lamerhav.dsviz.javaHome";

    /** Java שנמצאה: התיקייה, מאיפה, הגרסה (8, 11, 17, 21...) ואם יש בה jdk.jdi */
    public static final class Found {
        public final File home;
        public final String source;
        public final int version;
        public final boolean jdi;

        Found(File home, String source) {
            this.home = home;
            this.source = source;
            this.version = version(home);
            this.jdi = hasJdi(home);
        }

        public String java() {
            return javaExe(home);
        }

        public String javac() {
            File f = new File(home, "bin/javac" + (SystemInfo.isWindows ? ".exe" : ""));
            return f.isFile() ? f.getAbsolutePath() : null;
        }
    }

    /**
     * projectSdkHome — ה-SDK של המודול/הפרויקט (או null); minVersion — הגרסה שהפרויקט מקומפל אליה (0 = לא ידוע).
     * מחזיר null אם אין שום Java.
     */
    public static Found find(String projectSdkHome, int minVersion) {
        List<Found> c = candidates(projectSdkHome);
        for (Found f : c) if (f.jdi && (minVersion <= 0 || f.version == 0 || f.version >= minVersion)) return f;
        for (Found f : c) if (minVersion <= 0 || f.version == 0 || f.version >= minVersion) return f;
        return c.isEmpty() ? null : c.get(0);
    }

    static List<Found> candidates(String projectSdkHome) {
        List<Found> out = new ArrayList<Found>();
        String setting = null;
        try {
            setting = PropertiesComponent.getInstance().getValue(SETTING);
        } catch (Throwable ignored) {
            // בבדיקות בלי אפליקציה
        }
        add(out, setting, "הגדרה " + SETTING);
        add(out, Dsviz.dsvizFile("jdk-path.txt"), "ה-Java שהותקנה עם DSViz");
        add(out, new File(Dsviz.installDir(), "jdk").getPath(), "ה-Java שהותקנה עם DSViz");
        if (SystemInfo.isMac) add(out, new File(Dsviz.installDir(), "jdk/Contents/Home").getPath(), "ה-Java שהותקנה עם DSViz");
        add(out, projectSdkHome, "ה-SDK של הפרויקט");
        add(out, System.getenv("JAVA_HOME"), "JAVA_HOME");
        return out;
    }

    private static void add(List<Found> out, String home, String source) {
        if (home == null || home.trim().isEmpty()) return;
        File h = new File(home.trim());
        if (javaExe(h) == null) return;
        for (Found f : out) if (f.home.getAbsoluteFile().equals(h.getAbsoluteFile())) return;
        out.add(new Found(h, source));
    }

    /** bin/java (ב-Windows: java.exe — עם קונסול, כדי שהקלט מהמקלדת יעבור) */
    static String javaExe(File home) {
        File f = new File(home, "bin/java" + (SystemInfo.isWindows ? ".exe" : ""));
        return f.isFile() ? f.getAbsolutePath() : null;
    }

    /** jdk.jdi: ברשימת המודולים בקובץ release, או jmods/jdk.jdi.jmod */
    static boolean hasJdi(File home) {
        String rel = release(home);
        if (rel != null) {
            Matcher m = Pattern.compile("(?m)^MODULES=\"?([^\"\\n]*)").matcher(rel);
            if (m.find()) return (" " + m.group(1) + " ").contains(" jdk.jdi ");
        }
        return new File(home, "jmods/jdk.jdi.jmod").isFile();
    }

    /** הגרסה הראשית (8, 11, 17, 21...) לפי הקובץ release, או 0 */
    static int version(File home) {
        String rel = release(home);
        if (rel == null) return 0;
        Matcher m = Pattern.compile("(?m)^JAVA_VERSION=\"?(\\d+)(?:\\.(\\d+))?").matcher(rel);
        if (!m.find()) return 0;
        int major = Integer.parseInt(m.group(1));
        return major == 1 && m.group(2) != null ? Integer.parseInt(m.group(2)) : major;
    }

    private static String release(File home) {
        try {
            File f = new File(home, "release");
            return f.isFile() ? new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8) : null;
        } catch (Exception e) {
            return null;
        }
    }
}
