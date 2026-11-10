package il.lamerhav.dsviz;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * כניסה משותפת לכל התוכנות במחשב (dsviz-access.jar): שני תהליכי Java נפרדים — "Eclipse" (התהליך הזה) ו"תוכנה אחרת"
 * (Visual Studio / "מודלים חישוביים" — תהליך-בן) — על אותן תיקיות זיהוי:
 *   כניסה של מורה באחד → השני רואה מורה תוך רגע (AccessWatch, AccessGate.onChange), התנתקות בשני → הראשון רואה "לא מחובר";
 *   עמדת מחשב שחוברה באחד — משותפת; נעילה בין התהליכים (Account.locked): 2×150 עדכונים מקבילים — אף אחד לא הולך לאיבוד,
 *   בלי קבצים זמניים שנשארים; בדיקה ארוכה מול השרת (refresh) לא מחזירה כניסה שהתנתקה בתוכנה אחרת בינתיים.
 *   java -cp ... il.lamerhav.dsviz.ShareIt http://127.0.0.1:8787 127.0.0.1:9099 <work-dir>
 */
public final class ShareIt {
    static int pass, fail;

    public static void main(String[] a) throws Exception {
        if (a.length > 0 && "other".equals(a[0])) { other(); return; }
        AccountIt.server = a[0];
        AccountIt.auth = a[1];
        File work = new File(a[2]);
        work.mkdirs();
        String key = Json.str(Json.obj(Json.parse(AccountIt.get(a[0] + "/v1/access/key"))).get("spki"));
        System.setProperty("dsviz.worker", a[0]);
        System.setProperty("dsviz.passKey", key);
        AccountIt.use(work, "pc", "alice");
        Account.browser = AccountIt::fakeBrowser;
        String admin = AccountIt.idToken("motiml77@gmail.com");

        // ---- התוכנה האחרת: תהליך נפרד, אותן תיקיות
        List<String> cmd = new ArrayList<String>();
        cmd.add(new File(System.getProperty("java.home"), "bin/java").getPath());
        cmd.add("-Djava.awt.headless=true");
        cmd.add("-Dstdout.encoding=UTF-8");
        for (String p : new String[] { "dsviz.worker", "dsviz.passKey", "dsviz.machine", "dsviz.machineDir", "dsviz.home" })
            cmd.add("-D" + p + "=" + System.getProperty(p));
        cmd.add("-cp");
        cmd.add(System.getProperty("java.class.path"));
        cmd.add(ShareIt.class.getName());
        cmd.add("other");
        Process proc = new ProcessBuilder(cmd).redirectError(ProcessBuilder.Redirect.INHERIT).start();
        final BlockingQueue<String> lines = new LinkedBlockingQueue<String>();
        final BufferedReader in = new BufferedReader(new InputStreamReader(proc.getInputStream(), StandardCharsets.UTF_8));
        Thread rd = new Thread(() -> {
            try { for (String l; (l = in.readLine()) != null; ) lines.add(l); } catch (Exception ignored) { /* נסגר */ }
        });
        rd.setDaemon(true);
        rd.start();
        PrintWriter out = new PrintWriter(new OutputStreamWriter(proc.getOutputStream(), StandardCharsets.UTF_8), true);
        check("התוכנה האחרת עלתה, לא מחובר", "STATE none".equals(next(lines, "STATE", 20000)));

        // ---- מאזינים בתהליך הזה (כמו הכפתור "עמדת ניהול" ב-Eclipse)
        final AtomicInteger gateChanges = new AtomicInteger();
        final AtomicInteger watchChanges = new AtomicInteger();
        AccessGate.onChange(gateChanges::incrementAndGet);
        AccessWatch.listen(watchChanges::incrementAndGet);

        // ---- כניסה של מורה כאן → בתוכנה האחרת: מורה
        String email = "share.teacher" + System.nanoTime() + "@test.dev";
        AccountIt.api("POST", "/v1/admin/teacher", admin, "{\"create\":true,\"email\":\"" + email + "\",\"name\":\"מורה משותפת\",\"seats\":2}");
        String code = Json.str(Json.obj(Json.obj(Json.parse(AccountIt.api("GET", "/v1/admin/teacher?email=" + email, admin, null))).get("teacher")).get("code"));
        AccountIt.browserEmail = email;
        long t0 = System.currentTimeMillis();
        Account.Result r = Account.signIn("teacher", null, null, 30000);
        check("מורה נכנסה ב'Eclipse' (" + r.status + ")", r.ok() && Account.current() != null && Account.current().isTeacher());
        String st = next(lines, "STATE", 8000);
        check("התוכנה האחרת רואה מורה תוך רגע (" + st + ", " + (System.currentTimeMillis() - t0) + "ms)", ("STATE teacher " + email).equals(st));

        // ---- התנתקות בתוכנה האחרת → כאן: לא מחובר, והמאזינים שמעו
        int g0 = gateChanges.get(), w0 = watchChanges.get();
        out.println("signout");
        check("התוכנה האחרת התנתקה", next(lines, "SIGNOUT", 15000).startsWith("SIGNOUT "));
        boolean seen = waitFor(() -> watchChanges.get() > w0 && gateChanges.get() > g0, 8000);
        check("כאן: AccessWatch ו-AccessGate.onChange שמעו על ההתנתקות", seen);
        check("כאן: לא מחובר (Account.current)", Account.current() == null && !AccessGate.canSignOut());
        check("התוכנה האחרת: לא מחובר", "STATE none".equals(next(lines, "STATE", 8000)));

        // ---- עמדה שחוברה בתוכנה האחרת — משותפת
        out.println("station " + code);
        check("עמדה חוברה בתוכנה האחרת", next(lines, "STATION", 20000).equals("STATION ok"));
        check("כאן: העמדה (בלי לחבר שוב)", waitFor(() -> Account.current() != null && Account.current().isStation(), 8000));

        // ---- refresh ארוך לא מחזיר כניסה שהתנתקה: כניסה כאן, refresh בתוכנה האחרת "באמצע", התנתקות כאן
        AccountIt.browserEmail = email;
        r = Account.signIn("teacher", null, null, 30000);
        check("מורה נכנסה שוב (" + r.status + ")", r.ok());
        next(lines, "STATE", 8000);
        out.println("slowrefresh");
        check("התוכנה האחרת קראה את הכרטיס והתחילה בדיקה", next(lines, "REFRESHING", 10000) != null);
        Account.signOut();
        out.println("go");
        check("הבדיקה בתוכנה האחרת הסתיימה", next(lines, "REFRESHED", 20000) != null);
        Thread.sleep(500);
        check("ההתנתקות נשארה (refresh לא החזיר את הכניסה)", Account.userState() == null && Account.current() != null && Account.current().isStation());

        // ---- נעילה בין התהליכים: עדכון מקביל של אותו קובץ
        final File counter = new File(System.getProperty("dsviz.home"), "counter.json");
        Map<String, Object> zero = new LinkedHashMap<String, Object>();
        zero.put("n", 0.0);
        Account.save(counter, zero);
        out.println("count 150");
        count(counter, 150);
        check("התוכנה האחרת סיימה לספור", next(lines, "COUNTED", 60000) != null);
        Map<String, Object> c = Account.load(counter);
        int n = c == null ? -1 : ((Number) c.get("n")).intValue();
        check("2×150 עדכונים מקבילים מתוך שני תהליכים — " + n + " (אף אחד לא אבד)", n == 300);
        File[] left = new File(System.getProperty("dsviz.home")).listFiles((d, name) -> name.endsWith(".tmp"));
        check("אין קבצים זמניים שנשארו", left != null && left.length == 0);

        out.println("quit");
        proc.waitFor(10, TimeUnit.SECONDS);
        proc.destroy();
        System.out.println(pass + " עברו, " + fail + " נכשלו");
        System.exit(fail == 0 ? 0 : 1);
    }

    /** read-modify-write בתוך הנעילה */
    static void count(final File f, int times) throws Exception {
        for (int i = 0; i < times; i++) {
            Account.locked(() -> {
                Map<String, Object> m = Account.load(f);
                m.put("n", ((Number) m.get("n")).doubleValue() + 1);
                Account.save(f, m);
                return null;
            });
        }
    }

    // ------------------------------------------------------------------ התוכנה האחרת (תהליך-בן)

    static void other() throws Exception {
        final PrintWriter out = new PrintWriter(new OutputStreamWriter(System.out, StandardCharsets.UTF_8), true);
        Account.resetForTest();
        Runnable report = () -> {
            Account.Access x = Account.current();
            out.println("STATE " + (x == null ? "none" : x.kind + " " + x.email));
        };
        report.run();
        AccessWatch.listen(report);
        BufferedReader in = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        for (String l; (l = in.readLine()) != null; ) {
            String[] w = l.split(" ");
            if ("signout".equals(w[0])) out.println("SIGNOUT " + Account.signOut().status);
            else if ("station".equals(w[0])) out.println("STATION " + Account.claimStation(w[1], "עמדה משותפת").status);
            else if ("slowrefresh".equals(w[0])) {
                // כמו Account.refresh, אבל עם השהיה בין הקריאה לשמירה (בדיקה איטית מול השרת)
                Map<String, Object> u = Account.userState();
                String tok = u == null ? "" : Json.str(u.get("token"));
                Account.Result r = Account.post("/v1/access/check", map("token", tok, "machine", Account.machine()));
                out.println("REFRESHING " + r.status);
                in.readLine();   // "go" — אחרי שהתוכנה הראשונה התנתקה
                Account.refreshWith(Account.userFile(), tok, r);
                out.println("REFRESHED");
            } else if ("count".equals(w[0])) {
                count(new File(System.getProperty("dsviz.home"), "counter.json"), Integer.parseInt(w[1]));
                out.println("COUNTED");
            } else if ("quit".equals(w[0])) break;
        }
        System.exit(0);
    }

    static Map<String, Object> map(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        for (int i = 0; i < kv.length; i += 2) m.put((String) kv[i], kv[i + 1]);
        return m;
    }

    static String next(BlockingQueue<String> q, String prefix, long ms) throws InterruptedException {
        long until = System.currentTimeMillis() + ms;
        for (long left; (left = until - System.currentTimeMillis()) > 0; ) {
            String l = q.poll(left, TimeUnit.MILLISECONDS);
            if (l == null) break;
            if (l.startsWith(prefix)) return l;
        }
        return "";
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
