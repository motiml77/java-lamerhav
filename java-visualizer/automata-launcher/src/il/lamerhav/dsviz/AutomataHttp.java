package il.lamerhav.dsviz;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * שרת HTTP קטן על 127.0.0.1 בלבד (בלי com.sun.net.httpserver — הוא לא קיים ב-Java של ההתקנה). בקשה אחת לכל חיבור
 * (Connection: close), חוץ מזרם האירועים (SSE) שנשאר פתוח.
 *
 * אבטחה (תוכנה אחרת במחשב / אתר בדפדפן לא יכולים לדבר עם השרת):
 *   - Host חייב להיות ‎127.0.0.1:port‎ / ‎localhost:port‎ (נגד DNS rebinding);
 *   - Origin (אם נשלח) — רק הכתובת של השרת עצמו; Sec-Fetch-Site (אם נשלח) — רק same-origin / none;
 *   - ‎/api/*‎ — רק עם הכותרת X-DSViz-Token (אקראי לכל הפעלה, השוואה בזמן קבוע); POST — רק עם Content-Type: application/json.
 */
final class AutomataHttp {
    static final int MAX_HEAD = 16 * 1024, MAX_BODY = 64 * 1024;

    /** בקשה */
    static final class Req {
        String method = "", path = "", query = "";
        final Map<String, String> headers = new LinkedHashMap<String, String>();
        byte[] body = new byte[0];
        Socket socket;

        String header(String name) {
            String v = headers.get(name.toLowerCase(Locale.ROOT));
            return v == null ? "" : v;
        }

        String param(String name) {
            for (String kv : query.split("&")) {
                int i = kv.indexOf('=');
                String k = i < 0 ? kv : kv.substring(0, i);
                if (!k.equals(name)) continue;
                try {
                    return i < 0 ? "" : URLDecoder.decode(kv.substring(i + 1), "UTF-8");
                } catch (Exception e) {
                    return "";
                }
            }
            return "";
        }

        String bodyText() {
            return new String(body, StandardCharsets.UTF_8);
        }
    }

    interface Handler {
        /** מטפל בבקשה (כבר עברה את בדיקות ה-Host / Origin). כותב את התשובה בעצמו */
        void handle(Req r, OutputStream out) throws IOException;
    }

    final ServerSocket server;
    final int port;
    private final Handler handler;

    AutomataHttp(int wantPort, Handler handler) throws IOException {
        ServerSocket s;
        try {
            s = new ServerSocket(wantPort, 50, InetAddress.getByName("127.0.0.1"));
        } catch (IOException busy) {
            if (wantPort == 0) throw busy;
            s = new ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"));   // הפורט הקבוע תפוס — פורט אחר
        }
        this.server = s;
        this.port = s.getLocalPort();
        this.handler = handler;
    }

    void start() {
        Thread t = new Thread(() -> {
            while (!server.isClosed()) {
                try {
                    final Socket c = server.accept();
                    Thread w = new Thread(() -> serve(c), "automata-http");
                    w.setDaemon(true);
                    w.start();
                } catch (IOException e) {
                    if (server.isClosed()) return;
                }
            }
        }, "automata-accept");
        t.setDaemon(true);
        t.start();
    }

    void close() {
        try { server.close(); } catch (IOException ignored) { /* נסגר */ }
    }

    private void serve(Socket c) {
        try {
            c.setSoTimeout(15000);
            InputStream in = c.getInputStream();
            OutputStream out = c.getOutputStream();
            Req r = read(in);
            if (r == null) { c.close(); return; }
            r.socket = c;
            String why = refuse(r);
            if (why != null) {
                send(out, 403, "text/plain; charset=utf-8", ("forbidden: " + why).getBytes(StandardCharsets.UTF_8), null);
                c.close();
                return;
            }
            c.setSoTimeout(0);
            handler.handle(r, out);
            if (!"keep".equals(r.headers.get(":keep"))) c.close();
        } catch (Exception e) {
            try { c.close(); } catch (IOException ignored) { /* נסגר */ }
        }
    }

    /** null — מותר; אחרת הסיבה */
    String refuse(Req r) {
        String host = r.header("host");
        if (!host.equals("127.0.0.1:" + port) && !host.equals("localhost:" + port)) return "host";
        String origin = r.header("origin");
        if (!origin.isEmpty() && !origin.equals("http://127.0.0.1:" + port) && !origin.equals("http://localhost:" + port)) return "origin";
        String site = r.header("sec-fetch-site");
        if (!site.isEmpty() && !site.equals("same-origin") && !site.equals("none")) return "site";
        return null;
    }

    static Req read(InputStream in) throws IOException {
        ByteArrayOutputStream head = new ByteArrayOutputStream();
        int state = 0;
        while (state < 4) {
            int b = in.read();
            if (b < 0) return null;
            head.write(b);
            if (head.size() > MAX_HEAD) return null;
            state = (b == '\r' && (state == 0 || state == 2)) ? state + 1 : (b == '\n' && (state == 1 || state == 3)) ? state + 1 : 0;
        }
        String[] lines = new String(head.toByteArray(), StandardCharsets.ISO_8859_1).split("\r\n");
        String[] first = lines[0].split(" ");
        if (first.length < 3) return null;
        Req r = new Req();
        r.method = first[0];
        String target = first[1];
        int q = target.indexOf('?');
        r.path = q < 0 ? target : target.substring(0, q);
        r.query = q < 0 ? "" : target.substring(q + 1);
        for (int i = 1; i < lines.length; i++) {
            int k = lines[i].indexOf(':');
            if (k > 0) r.headers.put(lines[i].substring(0, k).trim().toLowerCase(Locale.ROOT), lines[i].substring(k + 1).trim());
        }
        String cl = r.header("content-length");
        if (!cl.isEmpty()) {
            int n;
            try { n = Integer.parseInt(cl); } catch (NumberFormatException e) { return null; }
            if (n < 0 || n > MAX_BODY) return null;
            byte[] body = new byte[n];
            int got = 0;
            while (got < n) {
                int k = in.read(body, got, n - got);
                if (k < 0) return null;
                got += k;
            }
            r.body = body;
        }
        return r;
    }

    static void send(OutputStream out, int status, String type, byte[] body, Map<String, String> extra) throws IOException {
        StringBuilder h = new StringBuilder();
        h.append("HTTP/1.1 ").append(status).append(' ').append(reason(status)).append("\r\n");
        h.append("Content-Type: ").append(type).append("\r\n");
        h.append("Content-Length: ").append(body.length).append("\r\n");
        h.append("X-Content-Type-Options: nosniff\r\n");
        h.append("Referrer-Policy: no-referrer\r\n");
        h.append("Content-Security-Policy: frame-ancestors 'none'\r\n");
        h.append("X-Frame-Options: DENY\r\n");
        h.append("Cache-Control: no-store\r\n");
        h.append("Connection: close\r\n");
        if (extra != null) for (Map.Entry<String, String> e : extra.entrySet()) h.append(e.getKey()).append(": ").append(e.getValue()).append("\r\n");
        h.append("\r\n");
        out.write(h.toString().getBytes(StandardCharsets.UTF_8));
        out.write(body);
        out.flush();
    }

    static String reason(int s) {
        switch (s) {
            case 200: return "OK";
            case 204: return "No Content";
            case 302: return "Found";
            case 400: return "Bad Request";
            case 401: return "Unauthorized";
            case 403: return "Forbidden";
            case 404: return "Not Found";
            case 405: return "Method Not Allowed";
            case 415: return "Unsupported Media Type";
            default: return "Error";
        }
    }

    /** השוואה בזמן קבוע */
    static boolean same(String a, String b) {
        return a != null && b != null && MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }
}
