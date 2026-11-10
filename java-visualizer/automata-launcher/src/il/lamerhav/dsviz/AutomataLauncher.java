package il.lamerhav.dsviz;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import javax.swing.SwingUtilities;


/**
 * "מודלים חישוביים" כתוכנה מקומית (הקיצור בשולחן העבודה): מגיש את automata/app מתיקיית ההתקנה על 127.0.0.1 ופותח אותו בחלון
 * של תוכנה (Windows: Edge ‎--app‎ עם פרופיל משלו בתיקיית DSViz; אחרת — הדפדפן הרגיל). הכניסה משותפת עם Eclipse / IntelliJ /
 * Visual Studio (dsviz-access.jar): אותם קבצים בתיקייה הנסתרת, ושינוי באחת התוכנות מגיע לדף מיד (‎/api/events‎).
 *
 *   javaw -cp "automata\dsviz-automata.jar;dsviz.jar" il.lamerhav.dsviz.AutomataLauncher
 *     [--app <תיקיית app>] [--serve-only] [--dir <תיקיית מצב>]
 *
 * --serve-only (בדיקות): בלי חלון ובלי יציאה כשאין דף; מדפיס "LAUNCH <כתובת>" ו-"TOKEN-FILE <קובץ>".
 * הפעלה שנייה (עוד לחיצה על הקיצור) — לא שרת שני: מבקשת מהשרת שרץ קוד פתיחה חד-פעמי ופותחת עוד חלון.
 * יציאה: כשאין אף דף פתוח (זרם האירועים נסגר, בלי פעימה) — אחרי ‎30‎ שניות (ובהתחלה — אחרי ‎2‎ דקות לכל היותר).
 *
 * הממשק לדף (access/README.md): ‎/api/access‎, ‎/api/signin‎, ‎/api/station‎, ‎/api/signout‎, ‎/api/teacher-panel‎, ‎/api/events‎.
 * אף תשובה לא מחזירה כרטיס, אישור או סוד — רק מי מחובר.
 */
public final class AutomataLauncher {
    static final String TOKEN_HEADER = "x-dsviz-token";
    static final long IDLE_MS = Long.getLong("dsviz.automata.idleMs", 30000), GRACE_MS = Long.getLong("dsviz.automata.graceMs", 120000), CODE_MS = 60000;

    final File appDir, stateDir;
    final boolean serveOnly;
    /** --no-window (בדיקות): כמו הפעלה רגילה (יוצאת כשאין דף), אבל בלי לפתוח חלון — מדפיסה את הכתובת */
    static boolean noWindow;
    final String token = random(32);
    AutomataHttp http;

    private final Map<String, Long> launchCodes = new ConcurrentHashMap<String, Long>();
    private final List<OutputStream> streams = new CopyOnWriteArrayList<OutputStream>();
    private volatile long lastSeen = System.currentTimeMillis(), graceUntil = System.currentTimeMillis() + GRACE_MS;
    private volatile Account.Cancel signing;
    private volatile boolean offline;
    private volatile Process edge;

    AutomataLauncher(File appDir, File stateDir, boolean serveOnly) {
        this.appDir = appDir;
        this.stateDir = stateDir;
        this.serveOnly = serveOnly;
    }

    public static void main(String[] args) throws Exception {
        args = envArgs(args);
        File app = null, dir = null;
        boolean serveOnly = false;
        for (int i = 0; i < args.length; i++) {
            if ("--app".equals(args[i]) && i + 1 < args.length) app = new File(args[++i]);
            else if ("--dir".equals(args[i]) && i + 1 < args.length) dir = new File(args[++i]);
            else if ("--serve-only".equals(args[i])) serveOnly = true;
            else if ("--no-window".equals(args[i])) noWindow = true;
        }
        if (app == null) app = defaultAppDir();
        if (dir == null) dir = defaultStateDir();
        dir.mkdirs();
        Account.app = "automata";
        Account.client = () -> "dsviz-automata/1.0.1";
        if (Boolean.getBoolean("dsviz.automata.printBrowser")) Account.browser = u -> { System.out.println("BROWSER " + u); System.out.flush(); };
        new AutomataLauncher(app.getAbsoluteFile(), dir, serveOnly).run();
    }

    // ------------------------------------------------------------------ הפעלה אחת בלבד

    void run() throws Exception {
        File lockFile = new File(stateDir, "instance.lock");
        FileChannel ch = new RandomAccessFile(lockFile, "rw").getChannel();
        FileLock lock = null;
        for (int attempt = 0; attempt < 25 && lock == null; attempt++) {
            lock = ch.tryLock();
            if (lock != null) break;
            if (openOnRunning()) return;   // יש כבר שרת — רק עוד חלון
            Thread.sleep(200);
        }
        if (lock == null) {
            System.err.println("מודלים חישוביים: הפעלה אחרת תקועה (" + lockFile + ")");
            System.exit(1);
        }
        serve();
        writeInstance();
        String url = launchUrl();
        if (serveOnly || noWindow) {
            System.out.println("LAUNCH " + url);
            System.out.println("TOKEN-FILE " + new File(stateDir, "instance.json"));
            System.out.flush();
        } else {
            openWindow(url);
        }
        AccessWatch.listen(this::broadcast);
        Thread refresh = new Thread(() -> {   // בדיקה אחת מול השרת בהפעלה (כמו Eclipse): מורה שהושבת / כניסה שבוטלה
            try {
                offline = "offline".equals(Account.refresh().status);
                AccessWatch.checkNow();
                broadcast();
            } catch (Throwable ignored) { /* בפעם הבאה */ }
        }, "automata-refresh");
        refresh.setDaemon(true);
        refresh.start();
        while (true) {
            Thread.sleep(2000);
            pingStreams();
            if (serveOnly) continue;
            long now = System.currentTimeMillis();
            boolean alive = !streams.isEmpty() || now - lastSeen < IDLE_MS || now < graceUntil;
            if (!alive && edge != null && edge.isAlive()) alive = now - lastSeen < 4 * IDLE_MS;   // החלון עוד פתוח (דף מושהה)
            if (!alive) break;
        }
        System.out.println("EXIT idle");
        System.out.flush();
        new File(stateDir, "instance.json").delete();
        http.close();
        lock.release();
        System.exit(0);
    }

    /** הפעלה שנייה: מבקשת מהשרת שרץ קוד פתיחה ופותחת עוד חלון. false — אין שרת חי */
    boolean openOnRunning() {
        try {
            File f = new File(stateDir, "instance.json");
            if (!f.isFile()) return false;
            Map<String, Object> inst = Json.obj(Json.parse(new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8)));
            int port = ((Number) inst.get("port")).intValue();
            HttpURLConnection c = (HttpURLConnection) new URL("http://127.0.0.1:" + port + "/api/launch-code").openConnection();
            c.setRequestMethod("POST");
            c.setDoOutput(true);
            c.setConnectTimeout(2000);
            c.setReadTimeout(4000);
            c.setRequestProperty("Content-Type", "application/json");
            c.setRequestProperty("X-DSViz-Token", Json.str(inst.get("token")));
            c.getOutputStream().write("{}".getBytes(StandardCharsets.UTF_8));
            if (c.getResponseCode() != 200) return false;
            byte[] b = readAll(c.getInputStream());
            String code = Json.str(Json.obj(Json.parse(new String(b, StandardCharsets.UTF_8))).get("code"));
            String url = "http://127.0.0.1:" + port + "/launch?c=" + code;
            if (serveOnly || noWindow) System.out.println("LAUNCH " + url);
            else openWindow(url);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    void writeInstance() throws IOException {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("port", (double) http.port);
        m.put("token", token);
        m.put("pid", (double) ProcessHandle.current().pid());
        File f = new File(stateDir, "instance.json");
        File tmp = new File(stateDir, "instance.json.tmp");
        Files.write(tmp.toPath(), Json.write(m).getBytes(StandardCharsets.UTF_8));
        ownerOnly(tmp);
        Files.move(tmp.toPath(), f.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        // אותו פורט בכל הפעלה (אם פנוי): השמירה של הדף (localStorage — העבודה, ההעדפות, ההתקדמות במאגר) שייכת לכתובת
        Files.write(new File(stateDir, "port").toPath(), String.valueOf(http.port).getBytes(StandardCharsets.UTF_8));
    }

    int stablePort() {
        try {
            int p = Integer.parseInt(new String(Files.readAllBytes(new File(stateDir, "port").toPath()), StandardCharsets.UTF_8).trim());
            if (p > 1024 && p < 65536) return p;
        } catch (Exception ignored) {
            // פעם ראשונה
        }
        return 49152 + new SecureRandom().nextInt(16000);   // אקראי, ונשמר להבא
    }

    String launchUrl() {
        String code = random(18);
        launchCodes.put(code, System.currentTimeMillis() + CODE_MS);
        return "http://127.0.0.1:" + http.port + "/launch?c=" + code;
    }

    // ------------------------------------------------------------------ החלון

    void openWindow(String url) {
        graceUntil = System.currentTimeMillis() + GRACE_MS;
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        try {
            if (os.contains("win")) {
                String msedge = findEdge();
                if (msedge != null) {
                    File profile = new File(stateDir, "edge");
                    profile.mkdirs();
                    Process p = new ProcessBuilder(msedge, "--app=" + url, "--user-data-dir=" + profile.getAbsolutePath(), "--no-first-run",
                            "--no-default-browser-check", "--disable-features=Translate,msEdgeTranslate", "--window-size=1440,920").start();
                    if (edge == null || !edge.isAlive()) edge = p;
                    return;
                }
                new ProcessBuilder("rundll32", "url.dll,FileProtocolHandler", url).start();
            } else if (os.contains("mac")) {
                new ProcessBuilder("open", url).start();
            } else {
                new ProcessBuilder("xdg-open", url).start();
            }
        } catch (IOException e) {
            System.err.println("מודלים חישוביים: לא נפתח חלון — " + e.getMessage() + " · " + url);
        }
    }

    static String findEdge() {
        List<String> c = new ArrayList<String>();
        for (String v : new String[] { "ProgramFiles(x86)", "ProgramFiles", "LOCALAPPDATA" }) {
            String base = System.getenv(v);
            if (base != null && !base.isEmpty()) c.add(base + "\\Microsoft\\Edge\\Application\\msedge.exe");
        }
        for (String p : c) if (new File(p).isFile()) return p;
        return null;
    }

    // ------------------------------------------------------------------ השרת

    void serve() throws IOException {
        http = new AutomataHttp(stablePort(), this::handle);
        http.start();
    }

    void handle(AutomataHttp.Req r, OutputStream out) throws IOException {
        if (r.path.startsWith("/api/")) {
            api(r, out);
            return;
        }
        if (!r.method.equals("GET") && !r.method.equals("HEAD")) {
            AutomataHttp.send(out, 405, "text/plain; charset=utf-8", new byte[0], null);
            return;
        }
        if (r.path.equals("/launch")) {
            Long until = launchCodes.remove(r.param("c"));
            if (until == null || until < System.currentTimeMillis()) {
                AutomataHttp.send(out, 403, "text/html; charset=utf-8", page("הקישור כבר לא בתוקף",
                        "פותחים את \"מודלים חישוביים\" מהקיצור בשולחן העבודה.").getBytes(StandardCharsets.UTF_8), null);
                return;
            }
            Map<String, String> h = new LinkedHashMap<String, String>();
            h.put("Location", "/#dsviz-token=" + token);
            AutomataHttp.send(out, 302, "text/plain", new byte[0], h);
            return;
        }
        staticFile(r, out);
    }

    void staticFile(AutomataHttp.Req r, OutputStream out) throws IOException {
        String rel;
        try {
            rel = java.net.URLDecoder.decode(r.path.replace("+", "%2B"), "UTF-8");
        } catch (IllegalArgumentException e) {
            AutomataHttp.send(out, 400, "text/plain", new byte[0], null);
            return;
        }
        if (rel.equals("/") || rel.isEmpty()) rel = "/index.html";
        File base = appDir.getCanonicalFile();
        File f = new File(base, rel.substring(1)).getCanonicalFile();
        if (rel.equals("/js/access-client.js") && !f.isFile()) {
            // אפליקציה בלי הקובץ (גרסה קודמת של automata/app): העותק שבתוך ה-jar
            java.io.InputStream in = AutomataLauncher.class.getResourceAsStream("access-client.js");
            if (in != null) {
                byte[] js = readAll(in);
                AutomataHttp.send(out, 200, type(rel), r.method.equals("HEAD") ? new byte[0] : js, null);
                return;
            }
        }
        if (!f.getPath().startsWith(base.getPath() + File.separator) || !f.isFile() || f.getName().startsWith(".")) {
            AutomataHttp.send(out, 404, "text/plain; charset=utf-8", "not found".getBytes(StandardCharsets.UTF_8), null);
            return;
        }
        byte[] body = Files.readAllBytes(f.toPath());
        if (f.getName().equals("index.html") && f.getParentFile().equals(base)) body = inject(body);
        AutomataHttp.send(out, 200, type(f.getName()), r.method.equals("HEAD") ? new byte[0] : body, null);
    }

    /** הדף נטען דרך התוכנה: ‎js/access-client.js‎ תמיד נטען (שומר את המפתח מהכתובת, ומחזיק את החיבור — כך התוכנה יודעת שהחלון פתוח) */
    static byte[] inject(byte[] html) {
        String s = new String(html, StandardCharsets.UTF_8);
        String tag = "<script type=\"module\" src=\"js/access-client.js\"></script>";
        // בלי ההצעה "לתרגם מעברית?" של Edge / Chrome — זו תוכנה בעברית
        int h = s.indexOf("<head>");
        if (h >= 0 && !s.contains("notranslate")) s = s.substring(0, h + 6) + "<meta name=\"google\" content=\"notranslate\">" + s.substring(h + 6);
        if (!s.contains("translate=")) s = s.replaceFirst("<html(\\s|>)", "<html translate=\"no\"$1");
        if (s.contains("js/access-client.js")) return s.getBytes(StandardCharsets.UTF_8);
        int i = s.lastIndexOf("</body>");
        s = i < 0 ? s + tag : s.substring(0, i) + tag + "\n" + s.substring(i);
        return s.getBytes(StandardCharsets.UTF_8);
    }

    static String type(String name) {
        String n = name.toLowerCase(Locale.ROOT);
        if (n.endsWith(".html")) return "text/html; charset=utf-8";
        if (n.endsWith(".js") || n.endsWith(".mjs")) return "text/javascript; charset=utf-8";
        if (n.endsWith(".css")) return "text/css; charset=utf-8";
        if (n.endsWith(".json")) return "application/json; charset=utf-8";
        if (n.endsWith(".svg")) return "image/svg+xml";
        if (n.endsWith(".png")) return "image/png";
        if (n.endsWith(".ico")) return "image/x-icon";
        if (n.endsWith(".woff2")) return "font/woff2";
        if (n.endsWith(".woff")) return "font/woff";
        return "text/plain; charset=utf-8";
    }

    // ------------------------------------------------------------------ הממשק לדף (JSON)

    void api(AutomataHttp.Req r, OutputStream out) throws IOException {
        if (!AutomataHttp.same(r.header(TOKEN_HEADER), token)) {
            json(out, 401, map("error", "token"));
            return;
        }
        boolean post = r.method.equals("POST");
        if (post && !r.header("content-type").toLowerCase(Locale.ROOT).startsWith("application/json")) {
            json(out, 415, map("error", "content-type"));
            return;
        }
        if (!post && !r.method.equals("GET")) {
            json(out, 405, map("error", "method"));
            return;
        }
        lastSeen = System.currentTimeMillis();
        Map<String, Object> body;
        try {
            body = post && r.body.length > 0 ? Json.obj(Json.parse(r.bodyText())) : new LinkedHashMap<String, Object>();
        } catch (RuntimeException e) {
            json(out, 400, map("error", "json"));
            return;
        }
        String route = r.method + " " + r.path;
        switch (route) {
            case "GET /api/access":
                json(out, 200, access());
                return;
            case "GET /api/ping":
                json(out, 200, map("app", "dsviz-automata"));
                return;
            case "POST /api/heartbeat":
                json(out, 200, map("ok", true));
                return;
            case "POST /api/launch-code": {
                String code = random(18);
                launchCodes.put(code, System.currentTimeMillis() + CODE_MS);
                graceUntil = System.currentTimeMillis() + GRACE_MS;
                json(out, 200, map("code", code));
                return;
            }
            case "GET /api/events":
                events(r, out);
                return;
            case "POST /api/signin":
                json(out, 200, signIn(Json.str(body.get("role"))));
                return;
            case "POST /api/signin-cancel": {
                Account.Cancel c = signing;
                if (c != null) c.cancel();
                json(out, 200, map("ok", true));
                return;
            }
            case "POST /api/station": {
                String code = Json.str(body.get("code")).trim(), name = Json.str(body.get("name")).trim();
                if (code.isEmpty() || name.isEmpty() || code.length() > 40 || name.length() > 80) {
                    json(out, 400, map("ok", false, "status", "format", "message", "כותבים את קוד המורה ושם למחשב."));
                    return;
                }
                Account.Result res = Account.claimStation(code, name);
                broadcast();
                json(out, 200, result(res));
                return;
            }
            case "POST /api/signout": {
                if (!AccessGate.canSignOut()) {   // עמדת מחשב — רק המורה / ההנהלה משחררים (כמו ב-Eclipse)
                    json(out, 200, map("ok", false, "status", "station", "message", "עמדת מחשב לא מתנתקת מכאן — המורה משחרר אותה מעמדת הניהול."));
                    return;
                }
                Account.Result res = Account.signOut();
                AccessWatch.checkNow();
                broadcast();
                json(out, 200, map("ok", true, "status", res.status, "message", res.message));
                return;
            }
            case "POST /api/teacher-panel":
                json(out, 200, teacherPanel());
                return;
            default:
                json(out, 404, map("error", "route"));
        }
    }

    /** מי מחובר — בלי הכרטיס ובלי האישור */
    Map<String, Object> access() {
        Account.Access a = Account.current();
        Map<String, Object> u = Account.userState();
        String userStatus = u == null ? "" : Json.str(u.get("status"));
        String state;
        if (a != null) state = a.kind;
        else if ("blocked".equals(userStatus)) state = "blocked";
        else if (signing != null || "pay".equals(userStatus)) state = "pending";
        else state = "signed-out";
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("state", state);
        String name = a == null ? (u == null ? "" : Json.str(u.get("name"))) : a.name;
        if (a != null && a.isTeacher()) {
            Map<String, Object> t = Account.teacherInfo();
            if (t != null && !Json.str(t.get("name")).isEmpty()) name = Json.str(t.get("name"));
        }
        m.put("name", name);
        m.put("email", a == null ? (u == null ? "" : Json.str(u.get("email"))) : a.email);
        m.put("role", a != null ? a.kind : u == null ? "" : Json.str(u.get("role")));
        m.put("apps", a == null ? new ArrayList<Object>() : new ArrayList<Object>(a.apps));
        m.put("offline", offline);
        m.put("until", a == null ? "" : a.until);
        m.put("teacher", a == null ? "" : a.teacher);
        m.put("signing", signing != null);
        m.put("canSignOut", AccessGate.canSignOut());
        return m;
    }

    Map<String, Object> signIn(final String role) {
        if (!"teacher".equals(role) && !"student".equals(role)) return map("ok", false, "status", "format", "message", "role: teacher / student");
        if (signing != null) return map("ok", true, "status", "waiting");
        final Account.Cancel cancel = new Account.Cancel();
        signing = cancel;
        broadcast();
        Thread t = new Thread(() -> {
            Account.Result res;
            try {
                res = Account.signIn(role, null, cancel, 10 * 60 * 1000L);
            } catch (Throwable e) {
                res = null;
            }
            signing = null;
            AccessWatch.checkNow();
            send("signin", res == null ? map("ok", false, "status", "error", "message", "הכניסה לא הצליחה.") : result(res));
            broadcast();
        }, "automata-signin");
        t.setDaemon(true);
        t.start();
        return map("ok", true, "status", "waiting");
    }

    Map<String, Object> teacherPanel() {
        if (!AccessGate.isTeacher()) return map("ok", false, "status", "not-teacher", "message", "עמדת הניהול פתוחה רק למורה שמחובר במחשב הזה.");
        SwingUtilities.invokeLater(() -> {
            try {   // המראה של DSViz (unit4.collectionsLib.Theme שב-dsviz.jar), אם יש
                Class<?> th = Class.forName("unit4.collectionsLib.Theme");
                th.getMethod("install").invoke(null);
            } catch (Throwable ignored) { /* המראה הרגיל של Swing */ }
            try {
                AccessGate.showTeacherPanel(null);
            } catch (RuntimeException e) {   // בלי מסך (בדיקות) / תקלה בחלון — לא מפיל את התוכנה
                System.err.println("מודלים חישוביים: עמדת הניהול לא נפתחה — " + e);
            }
        });
        return map("ok", true, "status", "ok");
    }

    static Map<String, Object> result(Account.Result r) {
        return map("ok", r.ok(), "status", r.status, "message", r.message);
    }

    // ------------------------------------------------------------------ אירועים (SSE)

    void events(AutomataHttp.Req r, OutputStream out) throws IOException {
        r.headers.put(":keep", "keep");
        String head = "HTTP/1.1 200 OK\r\nContent-Type: text/event-stream; charset=utf-8\r\nCache-Control: no-store\r\n"
                + "X-Content-Type-Options: nosniff\r\nConnection: keep-alive\r\n\r\n";
        synchronized (out) {
            out.write(head.getBytes(StandardCharsets.UTF_8));
            out.write(frame("access", access()));
            out.flush();
        }
        streams.add(out);
        graceUntil = 0;   // החלון עלה ומחובר — מכאן יוצאים ‎30‎ שניות אחרי שהדף האחרון נסגר
    }

    void broadcast() {
        send("access", access());
    }

    void send(String event, Map<String, Object> data) {
        byte[] f = frame(event, data);
        for (OutputStream o : streams) {
            try {
                synchronized (o) { o.write(f); o.flush(); }
            } catch (IOException e) {
                drop(o);
            }
        }
    }

    void pingStreams() {
        byte[] ping = ": ping\n\n".getBytes(StandardCharsets.UTF_8);
        for (OutputStream o : streams) {
            try {
                synchronized (o) { o.write(ping); o.flush(); }
                lastSeen = System.currentTimeMillis();
            } catch (IOException e) {
                drop(o);
            }
        }
    }

    void drop(OutputStream o) {
        streams.remove(o);
        try { o.close(); } catch (IOException ignored) { /* נסגר */ }
    }

    static byte[] frame(String event, Map<String, Object> data) {
        return ("event: " + event + "\ndata: " + Json.write(data) + "\n\n").getBytes(StandardCharsets.UTF_8);
    }

    // ------------------------------------------------------------------ עזרים

    static void json(OutputStream out, int status, Map<String, Object> m) throws IOException {
        AutomataHttp.send(out, status, "application/json; charset=utf-8", Json.write(m).getBytes(StandardCharsets.UTF_8), null);
    }

    static Map<String, Object> map(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        for (int i = 0; i < kv.length; i += 2) m.put((String) kv[i], kv[i + 1]);
        return m;
    }

    static String page(String title, String text) {
        return "<!doctype html><html lang=\"he\" dir=\"rtl\"><meta charset=\"utf-8\"><title>מודלים חישוביים</title>"
                + "<body style=\"font-family:system-ui,sans-serif;background:#F4F6FB;color:#17213A;padding:48px\"><h1>" + title + "</h1><p>" + text + "</p></body></html>";
    }

    static String random(int bytes) {
        byte[] b = new byte[bytes];
        new SecureRandom().nextBytes(b);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }

    static byte[] readAll(java.io.InputStream in) throws IOException {
        java.io.ByteArrayOutputStream o = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        for (int n; (n = in.read(buf)) > 0; ) o.write(buf, 0, n);
        return o.toByteArray();
    }

    static void ownerOnly(File f) {
        f.setReadable(false, false);
        f.setReadable(true, true);
        f.setWritable(false, false);
        f.setWritable(true, true);
    }

    /** התיקייה של הדף: ליד ה-jar (‎<התקנה>\automata\app‎) */
    static File defaultAppDir() {
        try {
            File jar = new File(AutomataLauncher.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            return new File(jar.getParentFile(), "app");
        } catch (Exception e) {
            return new File("automata", "app");
        }
    }

    /**
     * מצב ההפעלה (לא זיהוי — הוא בתיקייה הנסתרת של Account): הפורט, instance.json, הפרופיל של Edge (השמירה של הדף).
     * Windows ‎%LOCALAPPDATA%\DSViz\automata-data‎ (ליד ההתקנה, לא בתוכה — ההתקנה מחליפה את ‎DSViz\automata‎), Mac / Linux ‎~/.dsviz/automata‎
     */
    static File defaultStateDir() {
        String d = System.getProperty("dsviz.automata.dir");
        if (d != null && !d.isEmpty()) return new File(d);
        String la = System.getenv("LOCALAPPDATA");
        if (System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win") && la != null && !la.isEmpty())
            return new File(new File(la, "DSViz"), "automata-data");
        return new File(new File(System.getProperty("user.home"), ".dsviz"), "automata");
    }

    /** כמו BankVs: נתיבים בעברית עוברים במשתנה הסביבה DSVIZ_ARGS (dsviz-cs.exe / SafeJava) */
    static String[] envArgs(String[] a) {
        if (a.length == 1 && "--args-env".equals(a[0])) {
            String v = System.getenv("DSVIZ_ARGS");
            if (v != null) return v.split("\n", -1);
        }
        return a;
    }
}
