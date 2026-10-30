package unit4.collectionsLib;

import java.awt.Desktop;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Properties;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.JOptionPane;

/**
 * נעילה לתשלום של התצוגה הוויזואלית (בלבד — Eclipse, ההרצה הרגילה והפלט ממשיכים לעבוד).
 * כרגע כבויה: השרת (worker/ — POST /v1/license) מחזיר "פתוח" כל עוד בעל התוכנה לא הפעיל PAYWALL=on.
 *
 * כדי לא להפריע: התשובה נשמרת ל-6 שעות (~/.dsviz/license-cache.properties); בלי אינטרנט — משתמשים בתשובה האחרונה
 * עד 14 יום, ואם אין — פתוח. קוד רישיון (DSV-...) נשמר ב-~/.dsviz/license.txt.
 */
final class License {
    private License() {}

    static final String DEFAULT_URL = "https://dsviz-exercise.motiml77.workers.dev/v1/license";
    private static final long FRESH_MS = 6L * 3600 * 1000, OFFLINE_MS = 14L * 24 * 3600 * 1000;

    static final class Status {
        boolean locked;
        String message = "", payUrl = "";
    }

    private static File dir() {
        File d = new File(System.getProperty("user.home"), ".dsviz");
        d.mkdirs();
        return d;
    }

    private static String url() {
        String u = System.getProperty("dsviz.licenseUrl");
        if (u == null || u.isEmpty()) u = System.getenv("DSVIZ_LICENSE_URL");
        return u == null || u.isEmpty() ? DEFAULT_URL : u;
    }

    static String code() {
        return read(new File(dir(), "license.txt")).trim();
    }

    /** מזהה אקראי של המחשב (לא מידע אישי) */
    private static String machine() {
        File f = new File(dir(), "machine-id.txt");
        String id = read(f).trim();
        if (id.isEmpty()) {
            id = UUID.randomUUID().toString();
            write(f, id);
        }
        return id;
    }

    /** המצב: מהמטמון אם הוא טרי, אחרת מהשרת (עד 4 שניות); force — תמיד מהשרת */
    static Status status(boolean force) {
        File cache = new File(dir(), "license-cache.properties");
        Properties p = new Properties();
        try (InputStream in = Files.newInputStream(cache.toPath())) {
            p.load(new java.io.InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (Exception ignored) {
            // אין מטמון
        }
        long at = 0;
        try { at = Long.parseLong(p.getProperty("at", "0")); } catch (NumberFormatException ignored) {}
        boolean sameCode = code().equals(p.getProperty("code", ""));
        long age = System.currentTimeMillis() - at;
        if (!force && sameCode && age >= 0 && age < FRESH_MS) return fromProps(p);
        Status s = ask();
        if (s != null) {
            Properties n = new Properties();
            n.setProperty("at", String.valueOf(System.currentTimeMillis()));
            n.setProperty("code", code());
            n.setProperty("locked", String.valueOf(s.locked));
            n.setProperty("message", s.message);
            n.setProperty("payUrl", s.payUrl);
            try (OutputStream out = Files.newOutputStream(cache.toPath())) {
                n.store(new java.io.OutputStreamWriter(out, StandardCharsets.UTF_8), "DSViz license");
            } catch (Exception ignored) {
                // לא נשמר — נשאל שוב בפעם הבאה
            }
            return s;
        }
        // אין תשובה מהשרת: התשובה האחרונה (עד 14 יום), אחרת — פתוח
        if (sameCode && age >= 0 && age < OFFLINE_MS) return fromProps(p);
        return new Status();
    }

    private static Status fromProps(Properties p) {
        Status s = new Status();
        s.locked = Boolean.parseBoolean(p.getProperty("locked", "false"));
        s.message = p.getProperty("message", "");
        s.payUrl = p.getProperty("payUrl", "");
        return s;
    }

    /** שואל את השרת. null = אין תשובה תקינה (בלי אינטרנט, שרת ישן...) */
    private static Status ask() {
        try {
            HttpURLConnection c = (HttpURLConnection) new URL(url()).openConnection();
            c.setConnectTimeout(4000);
            c.setReadTimeout(4000);
            c.setRequestMethod("POST");
            c.setDoOutput(true);
            c.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            String body = "{\"client\":\"dsviz-viewer\",\"machine\":" + json(machine()) + ",\"code\":" + json(code()) + "}";
            try (OutputStream o = c.getOutputStream()) {
                o.write(body.getBytes(StandardCharsets.UTF_8));
            }
            if (c.getResponseCode() != 200) return null;
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            try (InputStream in = c.getInputStream()) {
                byte[] b = new byte[4096];
                for (int n; (n = in.read(b)) > 0; ) buf.write(b, 0, n);
            }
            String text = new String(buf.toByteArray(), StandardCharsets.UTF_8);
            if (!text.contains("\"locked\"")) return null;
            Status s = new Status();
            s.locked = text.matches("(?s).*\"locked\"\\s*:\\s*true.*");
            s.message = field(text, "message");
            s.payUrl = field(text, "payUrl");
            return s;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * לפני שהחלון הוויזואלי נפתח: true = מותר. כשנעול — חלון שמסביר, עם מקום לקוד רישיון וקישור לרכישה.
     * התוכנית עצמה ממשיכה לרוץ כרגיל (הפלט בקונסול) בכל מקרה.
     */
    static boolean allowViewer() {
        Status s;
        try {
            s = status(false);
        } catch (Throwable t) {
            return true;
        }
        while (s.locked) {
            Object[] options = s.payUrl.isEmpty() ? new Object[] { "הזנת קוד רישיון", "סגירה" }
                    : new Object[] { "הזנת קוד רישיון", "לרכישה", "סגירה" };
            String msg = (s.message.isEmpty() ? "התצוגה הוויזואלית נעולה." : s.message)
                    + "\n\nהתוכנית רצה כרגיל, והפלט מופיע בקונסול.";
            JOptionPane pane = new JOptionPane(msg, JOptionPane.INFORMATION_MESSAGE, JOptionPane.DEFAULT_OPTION, null, options, options[0]);
            pane.applyComponentOrientation(java.awt.ComponentOrientation.RIGHT_TO_LEFT);   // עברית: מימין לשמאל
            javax.swing.JDialog d = pane.createDialog(null, "הרצה ויזואלית — נדרש רישיון");
            d.setVisible(true);
            d.dispose();
            int k = -1;
            for (int i = 0; i < options.length; i++) if (options[i].equals(pane.getValue())) k = i;
            if (k == 0) {
                String c = askCode();
                if (c == null) continue;
                write(new File(dir(), "license.txt"), c.trim());
                s = status(true);
                if (s.locked) message("הקוד לא תקין (או שאין חיבור לאינטרנט).");
            } else if (k == 1 && !s.payUrl.isEmpty()) {
                try { Desktop.getDesktop().browse(new URI(s.payUrl)); } catch (Exception ignored) {}
            } else {
                return false;
            }
        }
        return true;
    }

    /** חלון להזנת הקוד — מימין לשמאל (השדה עצמו משמאל לימין, כי הקוד באנגלית) */
    private static String askCode() {
        Object[] buttons = { "אישור", "ביטול" };
        JOptionPane pane = new JOptionPane("הקוד שקיבלתם אחרי התשלום:", JOptionPane.PLAIN_MESSAGE, JOptionPane.OK_CANCEL_OPTION, null, buttons, buttons[0]);
        pane.setWantsInput(true);
        pane.applyComponentOrientation(java.awt.ComponentOrientation.RIGHT_TO_LEFT);
        javax.swing.JDialog d = pane.createDialog(null, "קוד רישיון");
        rtlExceptText(d.getContentPane());
        d.setVisible(true);
        d.dispose();
        if (!buttons[0].equals(pane.getValue())) return null;
        Object v = pane.getInputValue();
        return v instanceof String && !v.equals(JOptionPane.UNINITIALIZED_VALUE) && !((String) v).trim().isEmpty() ? (String) v : null;
    }

    private static void message(String text) {
        JOptionPane pane = new JOptionPane(text, JOptionPane.WARNING_MESSAGE);
        pane.applyComponentOrientation(java.awt.ComponentOrientation.RIGHT_TO_LEFT);
        javax.swing.JDialog d = pane.createDialog(null, "קוד רישיון");
        d.setVisible(true);
        d.dispose();
    }

    private static void rtlExceptText(java.awt.Container c) {
        for (java.awt.Component k : c.getComponents()) {
            if (k instanceof javax.swing.text.JTextComponent) k.setComponentOrientation(java.awt.ComponentOrientation.LEFT_TO_RIGHT);
            else if (k instanceof java.awt.Container) rtlExceptText((java.awt.Container) k);
        }
    }

    private static String field(String json, String name) {
        Matcher m = Pattern.compile("\"" + name + "\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"").matcher(json);
        if (!m.find()) return "";
        return m.group(1).replace("\\n", "\n").replace("\\\"", "\"").replace("\\/", "/").replace("\\\\", "\\");
    }

    private static String json(String s) {
        StringBuilder b = new StringBuilder("\"");
        for (char c : s.toCharArray()) {
            if (c == '"' || c == '\\') b.append('\\');
            if (c >= 32) b.append(c);
        }
        return b.append('"').toString();
    }

    private static String read(File f) {
        try {
            return new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "";
        }
    }

    private static void write(File f, String s) {
        try {
            Files.write(f.toPath(), s.getBytes(StandardCharsets.UTF_8));
        } catch (Exception ignored) {
            // לא נשמר
        }
    }
}
