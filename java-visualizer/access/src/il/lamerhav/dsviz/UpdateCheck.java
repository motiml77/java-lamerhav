package il.lamerhav.dsviz;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * עדכון אוטומטי — רק כשמשתמשים ב-DSViz (בלי משימה נסתרת בכניסה ל-Windows): כש-Eclipse / IntelliJ / VS Code נפתחים,
 * וכשמפעילים כלי של DSViz מ-Visual Studio (תרגיל חדש, מאגר, שליחת תרגיל...) או את "מודלים חישוביים".
 * מפעיל את update.ps1 של ההתקנה ברקע (בודק גרסה, מוריד, ומתקין כשסוגרים את Eclipse / Visual Studio — עם הודעה בפינה).
 * update.ps1 עצמו לא בודק יותר מפעם ב-5 דקות, כך שאפשר לקרוא לזה בכל פתיחה.
 */
public final class UpdateCheck {
    private static boolean started;

    private UpdateCheck() {}

    /** flag: "-FromIde" (כלי / סביבה נפתחו), "-FromEclipse", "-AfterEclipse" */
    public static synchronized void start(String flag) {
        if (started && !"-AfterEclipse".equals(flag)) return;   // פעם אחת לכל תהליך
        started = true;
        try {
            if (!System.getProperty("os.name", "").toLowerCase().contains("win")) return;
            String dir = installDir();
            if (dir == null) return;
            File script = new File(dir, "update.ps1");
            if (!script.isFile()) return;
            File nul = new File("NUL");   // בלי stdin/stdout שמחוברים לכלי — אחרת העדכון יכול למות יחד איתו
            new ProcessBuilder("powershell.exe", "-NoProfile", "-WindowStyle", "Hidden", "-ExecutionPolicy", "Bypass",
                    "-File", script.getAbsolutePath(), flag)
                    .redirectInput(nul).redirectOutput(ProcessBuilder.Redirect.appendTo(nul)).redirectErrorStream(true).start();
        } catch (Throwable e) {
            // לא נורא — ננסה בפתיחה הבאה
        }
    }

    /** התיקייה של ההתקנה (‎~/.dsviz/install-dir.txt‎ — ההתקנה כותבת אותו), או null */
    static String installDir() {
        try {
            File f = new File(System.getProperty("user.home"), ".dsviz/install-dir.txt");
            if (!f.isFile()) return null;
            byte[] b = Files.readAllBytes(f.toPath());
            boolean utf16 = b.length >= 2 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xFE;
            String text = new String(b, utf16 ? StandardCharsets.UTF_16LE : StandardCharsets.UTF_8).trim();
            if (!text.isEmpty() && text.charAt(0) == '﻿') text = text.substring(1).trim();
            return text.isEmpty() ? null : text;
        } catch (Exception e) {
            return null;
        }
    }
}
