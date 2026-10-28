package il.lamerhav.dsviz;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

/**
 * "שליחת תרגיל": השליחה לשרת הביניים (worker/ — Cloudflare Worker), שהוא ששולח ל-Gemini.
 * המפתח של Gemini לא נמצא כאן — רק כתובת השרת (ומפתח-אפליקציה פשוט, אם הוגדר).
 * הכתובת: ‎-Ddsviz.worker=...‎ / משתנה הסביבה DSVIZ_WORKER_URL / resources/ai.properties שבתוך התוסף.
 */
final class WorkerClient {
    static final String CLIENT = "dsviz-eclipse/1.0.1";

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
    private volatile CompletableFuture<HttpResponse<String>> pending;

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
        HttpRequest req = base("/v1/exercise", Duration.ofSeconds(180))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray())).build();
        return send(req);
    }

    /** הקוד שנבנה לא התקמפל — מבקשים תיקון (בלי הקבצים, רק הקוד והשגיאות) */
    Map<String, Object> repair(Map<String, Object> exercise, Map<String, String> files, List<String> errors) throws AiError {
        Map<String, Object> b = new LinkedHashMap<String, Object>();
        b.put("client", CLIENT);
        b.put("language", "csharp".equals(Json.str(exercise.get("language"))) ? "csharp" : "java");
        b.put("exercise", exercise);
        b.put("files", files);
        b.put("errors", errors);
        HttpRequest req = base("/v1/repair", Duration.ofSeconds(150))
                .header("Content-Type", "application/json; charset=utf-8")
                .POST(HttpRequest.BodyPublishers.ofString(Json.write(b), StandardCharsets.UTF_8)).build();
        return send(req);
    }

    /** מבטל את השליחה שבדרך (מהחלון — "ביטול") */
    void cancel() {
        CompletableFuture<HttpResponse<String>> f = pending;
        if (f != null) f.cancel(true);
    }

    private HttpRequest.Builder base(String path, Duration timeout) throws AiError {
        try {
            HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url + path)).timeout(timeout)
                    .header("User-Agent", CLIENT).header("Accept", "application/json");
            if (!key.isEmpty()) b.header("X-DSViz-Key", key);
            return b;
        } catch (IllegalArgumentException e) {
            throw new AiError("config", "כתובת השרת לא תקינה: " + url);
        }
    }

    private Map<String, Object> send(HttpRequest req) throws AiError {
        HttpClient c = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).followRedirects(HttpClient.Redirect.NORMAL).build();
        HttpResponse<String> res;
        try {
            pending = c.sendAsync(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            res = pending.get();
        } catch (CancellationException e) {
            throw new AiError("cancel", "השליחה בוטלה.");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AiError("cancel", "השליחה בוטלה.");
        } catch (ExecutionException e) {
            Throwable t = e.getCause();
            if (t instanceof HttpConnectTimeoutException || t instanceof java.net.ConnectException || t instanceof java.nio.channels.UnresolvedAddressException
                    || (t != null && t.getCause() instanceof java.nio.channels.UnresolvedAddressException))
                throw new AiError("offline", "אין חיבור לשרת. בדקו שיש חיבור לאינטרנט ונסו שוב.");
            if (t instanceof HttpTimeoutException) throw new AiError("timeout", "השרת לא ענה בזמן (זה לפעמים קורה בתרגילים ארוכים). נסו שוב.");
            throw new AiError("network", "תקלה בתקשורת עם השרת: " + (t == null ? e.getMessage() : t.getMessage()));
        } finally {
            pending = null;
        }
        Map<String, Object> j;
        try {
            j = Json.obj(Json.parse(res.body()));
        } catch (IllegalArgumentException e) {
            throw new AiError("server", "השרת החזיר תשובה לא מובנת (קוד " + res.statusCode() + "). נסו שוב בעוד דקה.");
        }
        if (res.statusCode() != 200 || !Boolean.TRUE.equals(j.get("ok"))) {
            String msg = Json.str(j.get("error"));
            throw new AiError(Json.str(j.get("code")), msg.isEmpty() ? "השרת החזיר שגיאה (קוד " + res.statusCode() + ")." : msg);
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
