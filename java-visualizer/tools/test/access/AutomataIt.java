package il.lamerhav.dsviz;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * "מודלים חישוביים" כתוכנה מקומית (AutomataLauncher) מול הכניסה המשותפת — תהליך נפרד, על אותן תיקיות זיהוי כמו "Eclipse"
 * (התהליך הזה), מול השרת המקומי והאמולטור:
 *   אבטחה: בלי מפתח / מפתח שגוי → 401; Host זר, Origin זר, Sec-Fetch-Site: cross-site → 403; POST בלי JSON → 415;
 *     קוד פתיחה חד-פעמי (302 עם המפתח אחרי #), שימוש שני → 403; ‎../‎ לא יוצא מתיקיית האפליקציה; אף תשובה בלי כרטיס / אישור.
 *   כניסה משותפת: מורה שנכנסה כאן → ‎/api/access‎ מורה; התנתקות דרך ‎/api/signout‎ → כאן "לא מחובר" ו-teacher.flag נמחק;
 *     התנתקות כאן → אירוע access ב-‎/api/events‎ תוך רגע; עמדה דרך ‎/api/station‎ → גם כאן; כניסה דרך ‎/api/signin‎ (Google מדומה).
 *   הפעלה שנייה → לא שרת שני (קוד פתיחה מהשרת שרץ); --no-window: יוצאת כשאין דף, ונשארת כל עוד זרם האירועים פתוח.
 *   java -cp ... il.lamerhav.dsviz.AutomataIt http://127.0.0.1:8787 127.0.0.1:9099 <work-dir> <app-dir>
 */
public final class AutomataIt {
    static int pass, fail;
    static int port;
    static String token;

    public static void main(String[] a) throws Exception {
        AccountIt.server = a[0];
        AccountIt.auth = a[1];
        File work = new File(a[2]);
        File app = new File(a[3]);
        work.mkdirs();
        String key = Json.str(Json.obj(Json.parse(AccountIt.get(a[0] + "/v1/access/key"))).get("spki"));
        System.setProperty("dsviz.worker", a[0]);
        System.setProperty("dsviz.passKey", key);
        AccountIt.use(work, "pc", "dana");
        Account.browser = AccountIt::fakeBrowser;
        String admin = AccountIt.idToken("motiml77@gmail.com");
        File state = new File(work, "automata-state");

        // ---- המשגר: תהליך נפרד, אותן תיקיות זיהוי
        Proc l = launch(app, state, "--serve-only");
        String url = l.next("LAUNCH", 20000);
        check("המשגר עלה (" + url + ")", url.startsWith("LAUNCH http://127.0.0.1:"));
        url = url.substring(7);
        Map<String, Object> inst = Json.obj(Json.parse(new String(Files.readAllBytes(new File(state, "instance.json").toPath()), StandardCharsets.UTF_8)));
        port = ((Number) inst.get("port")).intValue();
        token = Json.str(inst.get("token"));
        check("instance.json רק לבעלים (המפתח לא נקרא למשתמשים אחרים)", !new File(state, "instance.json").canRead() || posixOwnerOnly(new File(state, "instance.json")));

        // ---- אבטחה
        String path = url.substring(url.indexOf("/launch"));
        Resp r = http("GET", path, null, null, null);
        check("קוד הפתיחה → 302 לדף עם המפתח אחרי # (" + r.status + ")", r.status == 302 && r.header("location").equals("/#dsviz-token=" + token));
        check("קוד פתיחה בשימוש שני → 403", http("GET", path, null, null, null).status == 403);
        check("קוד פתיחה מומצא → 403", http("GET", "/launch?c=AAAAAAAAAAAAAAAAAAAAAAAA", null, null, null).status == 403);
        check("בלי מפתח → 401", http("GET", "/api/access", "", null, null).status == 401);
        check("מפתח שגוי → 401", http("GET", "/api/access", "x" + token.substring(1), null, null).status == 401);
        check("Host זר (DNS rebinding) → 403", http("GET", "/api/access", token, null, "Host: evil.example:" + port).status == 403);
        check("Host זר גם לקבצים → 403", http("GET", "/index.html", null, null, "Host: evil.example:" + port).status == 403);
        check("Origin זר → 403", http("POST", "/api/signout", token, "{}", "Origin: https://evil.example").status == 403);
        check("Origin של פורט אחר → 403", http("GET", "/api/access", token, null, "Origin: http://127.0.0.1:" + (port + 1)).status == 403);
        check("Sec-Fetch-Site: cross-site → 403", http("GET", "/api/access", token, null, "Sec-Fetch-Site: cross-site").status == 403);
        check("POST בלי application/json → 415", http("POST", "/api/signout", token, "x", "Content-Type: text/plain").status == 415);
        check("Origin של השרת עצמו → מותר", http("GET", "/api/access", token, null, "Origin: http://127.0.0.1:" + port).status == 200);
        check("../ לא יוצא מתיקיית האפליקציה", http("GET", "/../automata-state/instance.json", null, null, null).status == 404
                && http("GET", "/%2e%2e/automata-state/instance.json", null, null, null).status == 404);
        Resp idx = http("GET", "/", null, null, null);
        check("הדף נטען, עם js/access-client.js (המפתח, והחיבור שמחזיק את התוכנה)", idx.status == 200 && (idx.body.contains("<script type=\"module\" src=\"js/access-client.js\"></script>")
                || idx.body.contains("import('./js/access-client.js')")));   // האפליקציה האמיתית טוענת אותו בעצמה (import דינמי); לדף אחר התוכנה מוסיפה את התג
        check("access-client.js מוגש כ-JavaScript", http("GET", "/js/access-client.js", null, null, null).header("content-type").startsWith("text/javascript"));
        Resp s0 = api("GET", "/api/access", null);
        check("לא מחובר: state=signed-out", "signed-out".equals(state(s0)));

        // ---- מורה נכנסת כאן ("Eclipse") → המשגר רואה מורה
        String email = "automata.teacher" + System.nanoTime() + "@test.dev";
        AccountIt.api("POST", "/v1/admin/teacher", admin, "{\"create\":true,\"email\":\"" + email + "\",\"name\":\"מורה מודלים\",\"seats\":2}");
        String code = Json.str(Json.obj(Json.obj(Json.parse(AccountIt.api("GET", "/v1/admin/teacher?email=" + email, admin, null))).get("teacher")).get("code"));
        AccountIt.browserEmail = email;
        Account.Result sr = Account.signIn("teacher", null, null, 30000);
        check("מורה נכנסה ב'Eclipse' (" + sr.status + ")", sr.ok());
        Map<String, Object> acc = Json.obj(Json.parse(api("GET", "/api/access", null).body));
        check("המשגר: state=teacher, המייל והשם, apps (" + acc + ")", "teacher".equals(acc.get("state")) && email.equals(acc.get("email"))
                && "מורה מודלים".equals(acc.get("name")) && Json.arr(acc.get("apps")).contains("automata"));
        String saved = Json.str(Account.userState().get("token")), passText = Json.str(Account.userState().get("pass"));
        List<String> all = new ArrayList<String>();

        // ---- זרם האירועים
        Events ev = new Events();
        String first = ev.next(5000);
        all.add(first);
        check("‎/api/events‎: אירוע access מיד (" + first + ")", first.contains("\"state\":\"teacher\""));
        check("‎/api/events‎ בלי מפתח → 401", http("GET", "/api/events", "", null, null).status == 401);

        // ---- התנתקות דרך המשגר → כאן "לא מחובר", teacher.flag נמחק
        File flag = Account.teacherFlagFile();
        check("teacher.flag קיים לפני ההתנתקות", flag != null && flag.isFile());
        Resp so = api("POST", "/api/signout", "{}");
        all.add(so.body);
        check("‎/api/signout‎ (" + so.body + ")", so.status == 200 && so.body.contains("\"ok\":true"));
        check("כאן ('Eclipse'): לא מחובר מיד", Account.current() == null && Account.userState() == null);
        check("teacher.flag נמחק", !flag.isFile());
        
        String e2 = ev.nextMatching("signed-out", 8000);
        all.add(e2);
        check("אירוע access: signed-out", e2.contains("signed-out"));

        // ---- כניסה דרך המשגר (‎/api/signin‎) — Google מדומה, אותה כניסה כמו ב-Eclipse
        AccountIt.browserEmail = email;
        Resp si = api("POST", "/api/signin", "{\"role\":\"teacher\"}");
        check("‎/api/signin‎ → מחכה לדפדפן", si.body.contains("waiting"));
        String bu = l.next("BROWSER", 10000);
        check("המשגר פתח את דף הכניסה (" + bu + ")", bu.contains("/signin?role=teacher"));
        AccountIt.fakeBrowser(bu.substring(8));
        String e3 = ev.nextMatching("\"state\":\"teacher\"", 20000);
        all.add(e3);
        check("אירוע access: teacher אחרי הכניסה מהמשגר", e3.contains("\"state\":\"teacher\""));
        check("כאן ('Eclipse'): מורה בלי לעשות כלום", waitFor(() -> Account.current() != null && Account.current().isTeacher(), 5000));
        String log = AccountIt.api("GET", "/v1/admin/log?limit=20", admin, null);
        check("ביומן הפעילות: app=automata", log.contains("app=automata"));
        saved = saved + "|" + Json.str(Account.userState().get("token"));
        Resp tp = api("POST", "/api/teacher-panel", "{}");
        check("‎/api/teacher-panel‎ למורה → ok", tp.body.contains("\"ok\":true"));

        // ---- התנתקות כאן → אירוע במשגר
        Account.signOut();
        AccessGate.changed();
        String e4 = ev.nextMatching("signed-out", 8000);
        all.add(e4);
        check("התנתקות ב'Eclipse' → אירוע signed-out במשגר תוך רגע", e4.contains("signed-out"));
        check("‎/api/teacher-panel‎ בלי מורה → not-teacher", api("POST", "/api/teacher-panel", "{}").body.contains("not-teacher"));

        // ---- עמדה דרך המשגר → משותפת
        Resp st = api("POST", "/api/station", "{\"code\":\"" + code + "\",\"name\":\"מעבדה 3 · עמדה 7\"}");
        all.add(st.body);
        check("‎/api/station‎ (" + st.body + ")", st.body.contains("\"ok\":true"));
        check("כאן: עמדה", Account.current() != null && Account.current().isStation());
        Resp so2 = api("POST", "/api/signout", "{}");
        check("עמדה לא מתנתקת מהמשגר (status station)", so2.body.contains("\"status\":\"station\"") && Account.current() != null);
        check("‎/api/station‎ בלי קוד → 400", api("POST", "/api/station", "{\"code\":\"\",\"name\":\"\"}").status == 400);
        all.add(api("GET", "/api/access", null).body);
        saved = saved + "|" + Json.str(Account.load(new File(System.getProperty("dsviz.machineDir"), "station.json")) == null ? ""
                : Account.load(new File(System.getProperty("dsviz.machineDir"), "station.json")).get("token"));

        // ---- אף תשובה בלי כרטיס / אישור
        boolean clean = true;
        for (String b : all) {
            for (String t : saved.split("\\|")) if (!t.isEmpty() && b.contains(t)) clean = false;
            if (b.contains(passText.substring(0, 20)) || b.contains("\"token\"") || b.contains("\"pass\"")) clean = false;
        }
        check("אף תשובה / אירוע לא כוללים כרטיס או אישור (" + all.size() + " נבדקו)", clean);

        // ---- הפעלה שנייה → לא שרת שני
        Proc l2 = launch(app, state, "--serve-only");
        String u2 = l2.next("LAUNCH", 15000);
        check("הפעלה שנייה: קוד פתיחה מהשרת שרץ (אותו פורט)", u2.startsWith("LAUNCH http://127.0.0.1:" + port + "/launch?c="));
        check("הפעלה שנייה יוצאת", l2.p.waitFor(10, TimeUnit.SECONDS));
        check("הקוד מההפעלה השנייה פותח את הדף", http("GET", u2.substring(u2.indexOf("/launch")), null, null, null).status == 302);
        ev.close();
        l.p.destroy();
        l.p.waitFor(10, TimeUnit.SECONDS);

        // ---- --no-window: יוצאת כשאין דף; נשארת כל עוד זרם האירועים פתוח
        File state2 = new File(work, "automata-state2");
        Proc l3 = launch(app, state2, "--no-window", "-Ddsviz.automata.idleMs=2500", "-Ddsviz.automata.graceMs=3000");
        l3.next("LAUNCH", 15000);
        inst = Json.obj(Json.parse(new String(Files.readAllBytes(new File(state2, "instance.json").toPath()), StandardCharsets.UTF_8)));
        port = ((Number) inst.get("port")).intValue();
        token = Json.str(inst.get("token"));
        Events ev3 = new Events();
        ev3.next(5000);
        Thread.sleep(9000);
        check("דף פתוח (זרם אירועים) — המשגר לא יוצא", l3.p.isAlive());
        ev3.close();
        String bye = l3.next("EXIT", 15000);
        check("הדף נסגר — המשגר יוצא (" + bye + ")", bye.startsWith("EXIT") && l3.p.waitFor(10, TimeUnit.SECONDS));
        check("instance.json נמחק ביציאה", !new File(state2, "instance.json").exists());

        System.out.println(pass + " עברו, " + fail + " נכשלו");
        System.exit(fail == 0 ? 0 : 1);
    }

    // ------------------------------------------------------------------ המשגר

    static final class Proc {
        Process p;
        final BlockingQueue<String> lines = new LinkedBlockingQueue<String>();

        String next(String prefix, long ms) throws InterruptedException {
            long until = System.currentTimeMillis() + ms;
            for (long left; (left = until - System.currentTimeMillis()) > 0; ) {
                String l = lines.poll(left, TimeUnit.MILLISECONDS);
                if (l == null) break;
                if (l.startsWith(prefix)) return l;
            }
            return "";
        }
    }

    static Proc launch(File app, File state, String... extra) throws Exception {
        List<String> cmd = new ArrayList<String>();
        cmd.add(new File(System.getProperty("java.home"), "bin/java").getPath());
        cmd.add("-Djava.awt.headless=true");
        cmd.add("-Dstdout.encoding=UTF-8");
        cmd.add("-Ddsviz.automata.printBrowser=true");
        for (String p : new String[] { "dsviz.worker", "dsviz.passKey", "dsviz.machine", "dsviz.machineDir", "dsviz.home" })
            cmd.add("-D" + p + "=" + System.getProperty(p));
        List<String> flags = new ArrayList<String>();
        for (String e : extra) { if (e.startsWith("-D")) cmd.add(e); else flags.add(e); }
        cmd.add("-cp");
        cmd.add(System.getProperty("automata.cp"));
        cmd.add("il.lamerhav.dsviz.AutomataLauncher");
        cmd.add("--app");
        cmd.add(app.getPath());
        cmd.add("--dir");
        cmd.add(state.getPath());
        cmd.addAll(flags);
        final Proc pr = new Proc();
        pr.p = new ProcessBuilder(cmd).redirectError(ProcessBuilder.Redirect.INHERIT).start();
        final BufferedReader in = new BufferedReader(new InputStreamReader(pr.p.getInputStream(), StandardCharsets.UTF_8));
        Thread t = new Thread(() -> {
            try { for (String l; (l = in.readLine()) != null; ) pr.lines.add(l); } catch (Exception ignored) { /* נסגר */ }
        });
        t.setDaemon(true);
        t.start();
        return pr;
    }

    // ------------------------------------------------------------------ HTTP גולמי (כדי לשלוט ב-Host / Origin)

    static final class Resp {
        int status;
        String head = "", body = "";

        String header(String name) {
            for (String l : head.split("\r\n")) {
                int i = l.indexOf(':');
                if (i > 0 && l.substring(0, i).trim().equalsIgnoreCase(name)) return l.substring(i + 1).trim();
            }
            return "";
        }
    }

    static Resp api(String method, String path, String body) throws Exception {
        return http(method, path, token, body, body == null ? null : "Content-Type: application/json");
    }

    static Resp http(String method, String path, String tok, String body, String extra) throws Exception {
        Socket s = new Socket("127.0.0.1", port);
        s.setSoTimeout(30000);
        StringBuilder h = new StringBuilder(method + " " + path + " HTTP/1.1\r\n");
        if (extra == null || !extra.startsWith("Host:")) h.append("Host: 127.0.0.1:").append(port).append("\r\n");
        if (extra != null) h.append(extra).append("\r\n");
        if (tok != null && !tok.isEmpty()) h.append("X-DSViz-Token: ").append(tok).append("\r\n");
        byte[] b = body == null ? new byte[0] : body.getBytes(StandardCharsets.UTF_8);
        if (body != null) {
            if (extra == null || !extra.toLowerCase().contains("content-type")) h.append("Content-Type: application/json\r\n");
            h.append("Content-Length: ").append(b.length).append("\r\n");
        }
        h.append("Connection: close\r\n\r\n");
        OutputStream o = s.getOutputStream();
        o.write(h.toString().getBytes(StandardCharsets.UTF_8));
        o.write(b);
        o.flush();
        ByteArrayOutputStream all = new ByteArrayOutputStream();
        InputStream in = s.getInputStream();
        byte[] buf = new byte[8192];
        try { for (int n; (n = in.read(buf)) > 0; ) all.write(buf, 0, n); } catch (java.net.SocketTimeoutException e) { /* עד כאן */ }
        s.close();
        String t = new String(all.toByteArray(), StandardCharsets.UTF_8);
        Resp r = new Resp();
        int i = t.indexOf("\r\n\r\n");
        r.head = i < 0 ? t : t.substring(0, i);
        r.body = i < 0 ? "" : t.substring(i + 4);
        try { r.status = Integer.parseInt(r.head.split(" ")[1]); } catch (Exception e) { r.status = -1; }
        return r;
    }

    static String state(Resp r) {
        return Json.str(Json.obj(Json.parse(r.body)).get("state"));
    }

    /** ‎/api/events‎ — כמו access-client.js (המפתח בכותרת) */
    static final class Events {
        final Socket s;
        final BlockingQueue<String> q = new LinkedBlockingQueue<String>();

        Events() throws Exception {
            s = new Socket("127.0.0.1", port);
            OutputStream o = s.getOutputStream();
            o.write(("GET /api/events HTTP/1.1\r\nHost: 127.0.0.1:" + port + "\r\nX-DSViz-Token: " + token + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
            o.flush();
            final BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8));
            Thread t = new Thread(() -> {
                try {
                    String ev = "";
                    for (String l; (l = in.readLine()) != null; ) {
                        if (l.startsWith("event: ")) ev = l.substring(7);
                        else if (l.startsWith("data: ")) q.add(ev + " " + l.substring(6));
                    }
                } catch (Exception ignored) { /* נסגר */ }
            });
            t.setDaemon(true);
            t.start();
        }

        String next(long ms) throws InterruptedException {
            String v = q.poll(ms, TimeUnit.MILLISECONDS);
            return v == null ? "" : v;
        }

        String nextMatching(String part, long ms) throws InterruptedException {
            long until = System.currentTimeMillis() + ms;
            for (long left; (left = until - System.currentTimeMillis()) > 0; ) {
                String v = q.poll(left, TimeUnit.MILLISECONDS);
                if (v == null) break;
                if (v.contains(part)) return v;
            }
            return "";
        }

        void close() throws Exception {
            s.close();
        }
    }

    static boolean posixOwnerOnly(File f) {
        try {
            return !Files.getPosixFilePermissions(f.toPath()).contains(java.nio.file.attribute.PosixFilePermission.OTHERS_READ)
                    && !Files.getPosixFilePermissions(f.toPath()).contains(java.nio.file.attribute.PosixFilePermission.GROUP_READ);
        } catch (Exception e) {
            return true;
        }
    }

    interface Cond { boolean ok(); }

    static boolean waitFor(Cond c, long ms) throws InterruptedException {
        long until = System.currentTimeMillis() + ms;
        while (System.currentTimeMillis() < until) {
            if (c.ok()) return true;
            Thread.sleep(50);
        }
        return c.ok();
    }

    static void check(String name, boolean ok) {
        if (ok) pass++; else fail++;
        System.out.println((ok ? "  ok   " : "  FAIL ") + name);
    }
}
