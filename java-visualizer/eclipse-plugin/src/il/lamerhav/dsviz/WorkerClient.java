package il.lamerhav.dsviz;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

/**
 * "שליחת תרגיל": השליחה לשרת הביניים (worker/ — Cloudflare Worker), שהוא ששולח ל-Gemini.
 * המפתח של Gemini לא נמצא כאן — רק כתובת השרת (ומפתח-אפליקציה פשוט, אם הוגדר).
 * הכתובת: ‎-Ddsviz.worker=...‎ / משתנה הסביבה DSVIZ_WORKER_URL / resources/ai.properties שבתוך התוסף.
 */
final class WorkerClient {
    static String CLIENT = "dsviz-eclipse/1.0.1";   // ב-VS Code / Visual Studio: dsviz-vscode / dsviz-vs

    /** שגיאה שמוצגת לתלמיד כמו שהיא */
    static final class AiError extends Exception {
        private static final long serialVersionUID = 1L;
        final String code;

        AiError(String code, String msg) {
            super(msg);
            this.code = code;
        }
    }

    final String url, key;
    private volatile HttpURLConnection pending;
    private volatile boolean cancelled;

    WorkerClient(String url, String key) {
        this.url = url == null ? "" : url.replaceAll("/+$", "");
        this.key = key == null ? "" : key;
    }

    /** לפי ההגדרות (או null אם עוד לא הוגדרה כתובת) */
    static WorkerClient configured() {
        String u = System.getProperty("dsviz.worker"), k = System.getProperty("dsviz.workerKey");
        if (u == null || u.isEmpty()) u = System.getenv("DSVIZ_WORKER_URL");
        if (k == null || k.isEmpty()) k = System.getenv("DSVIZ_WORKER_KEY");
        if (u == null || u.isEmpty() || k == null) {
            Properties p = new Properties();
            try (InputStream in = WorkerClient.class.getResourceAsStream("/resources/ai.properties")) {
                if (in != null) p.load(new java.io.InputStreamReader(in, StandardCharsets.UTF_8));
            } catch (IOException ignored) {
                // בלי קובץ הגדרות
            }
            if (u == null || u.isEmpty()) u = p.getProperty("url", "").trim();
            if (k == null) k = p.getProperty("key", "").trim();
        }
        if (u == null || u.isEmpty() || !(u.startsWith("https://") || u.startsWith("http://"))) return null;
        return new WorkerClient(u, k);
    }

    /**
     * בדיקה מהירה לפני שפותחים את החלון: יש אינטרנט? מנסים את השרת שלנו ואתר כללי במקביל, ומספיק שאחד עונה.
     * false רק כששניהם לא עונים בתוך waitMs — אז כנראה אין חיבור לאינטרנט.
     */
    boolean online(long waitMs) {
        return reachable(waitMs, url + "/v1/health", "https://www.google.com/generate_204", "https://www.cloudflare.com/cdn-cgi/trace");
    }

    static boolean reachable(long waitMs, String... urls) {
        final java.util.concurrent.CountDownLatch any = new java.util.concurrent.CountDownLatch(1);
        for (final String u : urls) {
            Thread t = new Thread(() -> {
                try {
                    HttpURLConnection c = (HttpURLConnection) new URL(u).openConnection();
                    c.setConnectTimeout((int) waitMs);
                    c.setReadTimeout((int) waitMs);
                    c.setRequestMethod("GET");
                    c.setRequestProperty("User-Agent", CLIENT);
                    if (c.getResponseCode() > 0) any.countDown();   // כל תשובה (גם 404) = יש חיבור
                    c.disconnect();
                } catch (IOException | RuntimeException ignored) {
                    // לא ענה
                }
            }, "dsviz-online");
            t.setDaemon(true);
            t.start();
        }
        try {
            return any.await(waitMs + 300, java.util.concurrent.TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return true;
        }
    }

    /** שולח את דף התרגיל ומחזיר את התרגיל (exercise) מהתשובה */
    /** language: "java" או "csharp" — נכנס להנחיות למודל (השרת בונה לפיה את ההנחיות ואת ממשק הספרייה) */
    Map<String, Object> exercise(List<ExerciseFiles.Item> items, String note, String language) throws AiError {
        String boundary = "----dsviz" + UUID.randomUUID().toString().replace("-", "");
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        List<Object> texts = new ArrayList<Object>(), names = new ArrayList<Object>();
        List<byte[]> files = new ArrayList<byte[]>();
        List<String> mimes = new ArrayList<String>();
        for (ExerciseFiles.Item it : items) {
            int k = 0;
            for (ExerciseFiles.Part p : it.parts) {
                if (p.text != null) {
                    Map<String, Object> t = new LinkedHashMap<String, Object>();
                    t.put("name", it.name);
                    t.put("text", p.text);
                    texts.add(t);
                } else {
                    files.add(p.data);
                    mimes.add(p.mime);
                    names.add(it.parts.size() > 1 ? it.name + " (" + (++k) + ")" : it.name);
                }
            }
        }
        Map<String, Object> meta = new LinkedHashMap<String, Object>();
        meta.put("client", CLIENT);
        meta.put("language", "csharp".equals(language) ? "csharp" : "java");
        meta.put("note", note == null ? "" : note.trim());
        meta.put("texts", texts);
        meta.put("names", names);
        part(body, boundary, "meta", null, "application/json; charset=utf-8", Json.write(meta).getBytes(StandardCharsets.UTF_8));
        for (int i = 0; i < files.size(); i++) {
            String mime = mimes.get(i);
            String ext = mime.equals("application/pdf") ? "pdf" : mime.substring(mime.indexOf('/') + 1);
            part(body, boundary, "file", "file" + (i + 1) + "." + ext, mime, files.get(i));   // השם האמיתי (בעברית) — ב-meta
        }
        write(body, "--" + boundary + "--\r\n");
        return send("/v1/exercise", 180, "multipart/form-data; boundary=" + boundary, body.toByteArray());
    }

    /** הקוד שנבנה לא התקמפל — מבקשים תיקון (בלי הקבצים, רק הקוד והשגיאות) */
    Map<String, Object> repair(Map<String, Object> exercise, Map<String, String> files, List<String> errors) throws AiError {
        return repair(exercise, files, errors, null);
    }

    /**
     * mode = "solution": ההצעות לפתרון (tasks[].solution) לא נתנו את הפלט הצפוי — files הם הקבצים עם ההצעות, ו-errors מה שקרה
     * (שגיאות קומפילציה, או הפלט מול הפלט הצפוי). השרת מבקש מהמודל לתקן את ההצעה (או את הפלט הצפוי, אם הוא השגוי)
     */
    Map<String, Object> repair(Map<String, Object> exercise, Map<String, String> files, List<String> errors, String mode) throws AiError {
        Map<String, Object> b = new LinkedHashMap<String, Object>();
        b.put("client", CLIENT);
        b.put("language", "csharp".equals(Json.str(exercise.get("language"))) ? "csharp" : "java");
        if (mode != null) b.put("mode", mode);
        b.put("exercise", exercise);
        b.put("files", files);
        b.put("errors", errors);
        return send("/v1/repair", 150, "application/json; charset=utf-8", Json.write(b).getBytes(StandardCharsets.UTF_8));
    }

    /** מבטל את השליחה שבדרך (מהחלון — "ביטול") */
    void cancel() {
        cancelled = true;
        HttpURLConnection c = pending;
        if (c != null) c.disconnect();
    }

    /** HttpURLConnection (ולא java.net.http) — כדי שיעבוד גם ב-Eclipse שרץ על Java 8 */
    private Map<String, Object> send(String path, int timeoutSec, String contentType, byte[] body) throws AiError {
        HttpURLConnection c;
        try {
            c = (HttpURLConnection) new URL(url + path).openConnection();
        } catch (IOException | RuntimeException e) {
            throw new AiError("config", "כתובת השרת לא תקינה: " + url);
        }
        int status;
        String text;
        cancelled = false;
        try {
            c.setRequestMethod("POST");
            c.setConnectTimeout(20000);
            c.setReadTimeout(timeoutSec * 1000);
            c.setInstanceFollowRedirects(true);
            c.setRequestProperty("User-Agent", CLIENT);
            c.setRequestProperty("Accept", "application/json");
            c.setRequestProperty("Content-Type", contentType);
            if (!key.isEmpty()) c.setRequestProperty("X-DSViz-Key", key);
            c.setDoOutput(true);
            c.setFixedLengthStreamingMode(body.length);
            pending = c;
            try (OutputStream o = c.getOutputStream()) {
                o.write(body);
            }
            status = c.getResponseCode();
            InputStream in = status >= 400 ? c.getErrorStream() : c.getInputStream();
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            if (in != null) {
                try (InputStream i = in) {
                    byte[] b = new byte[8192];
                    for (int n; (n = i.read(b)) > 0; ) buf.write(b, 0, n);
                }
            }
            text = new String(buf.toByteArray(), StandardCharsets.UTF_8);
        } catch (java.net.SocketTimeoutException e) {
            if (cancelled) throw new AiError("cancel", "השליחה בוטלה.");
            if (e.getMessage() != null && e.getMessage().contains("connect"))
                throw new AiError("offline", "אין חיבור לשרת. בדקו שיש חיבור לאינטרנט ונסו שוב.");
            throw new AiError("timeout", "השרת לא ענה בזמן (זה לפעמים קורה בתרגילים ארוכים). נסו שוב.");
        } catch (java.net.UnknownHostException | java.net.ConnectException e) {
            throw new AiError("offline", "אין חיבור לשרת. בדקו שיש חיבור לאינטרנט ונסו שוב.");
        } catch (IOException e) {
            if (cancelled) throw new AiError("cancel", "השליחה בוטלה.");
            throw new AiError("network", "תקלה בתקשורת עם השרת: " + e.getMessage());
        } finally {
            pending = null;
            c.disconnect();
        }
        Map<String, Object> j;
        try {
            j = Json.obj(Json.parse(text));
        } catch (IllegalArgumentException e) {
            throw new AiError("server", "השרת החזיר תשובה לא מובנת (קוד " + status + "). נסו שוב בעוד דקה.");
        }
        if (status != 200 || !Boolean.TRUE.equals(j.get("ok"))) {
            String msg = Json.str(j.get("error"));
            throw new AiError(Json.str(j.get("code")), msg.isEmpty() ? "השרת החזיר שגיאה (קוד " + status + ")." : msg);
        }
        Map<String, Object> ex = Json.obj(j.get("exercise"));
        if (ex.isEmpty()) throw new AiError("server", "התשובה מהשרת ריקה. נסו שוב.");
        return ex;
    }

    private static void part(ByteArrayOutputStream out, String boundary, String name, String file, String type, byte[] data) {
        write(out, "--" + boundary + "\r\nContent-Disposition: form-data; name=\"" + name + "\"" + (file != null ? "; filename=\"" + file + "\"" : "")
                + "\r\nContent-Type: " + type + "\r\n\r\n");
        out.write(data, 0, data.length);
        write(out, "\r\n");
    }

    private static void write(ByteArrayOutputStream out, String s) {
        byte[] b = s.getBytes(StandardCharsets.UTF_8);
        out.write(b, 0, b.length);
    }
}
