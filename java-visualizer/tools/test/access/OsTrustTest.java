package il.lamerhav.dsviz;

import java.awt.Window;
import java.io.File;
import java.io.FileInputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.swing.SwingUtilities;

import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsServer;

/**
 * סינון אינטרנט שבודק תעבורה מוצפנת (נטפרי, רימון...): השרת "נראה" עם תעודה שנחתמה בתעודת שורש של הסינון, שנמצאת רק במאגר של
 * מערכת ההפעלה. כאן: תעודת שורש לבדיקה (keytool), שרת HTTPS מקומי עם תעודה שהיא חתמה, ו"מאגר מערכת ההפעלה" מוזרק
 * (-Ddsviz.osTrustStore). בודקים: בלי התיקון — נכשל עם הסבר על סינון; עם — עובר; שם שרת שלא תואם / שורש שלא במאגר — עדיין נכשל.
 *   java -cp <classes>:<lib> il.lamerhav.dsviz.OsTrustTest <work> <shots>
 */
public class OsTrustTest {
    static int pass, fail;
    static File work;

    public static void main(String[] a) throws Exception {
        work = new File(a[0]);
        work.mkdirs();
        GateUi.shots = new File(a[1]);
        GateUi.shots.mkdirs();
        ca("ca", "CN=Test Filter Root (NetFree-like)");
        leaf("leaf", "ca", "ip:127.0.0.1");
        ca("ca2", "CN=Unknown Root");
        leaf("leaf2", "ca2", "ip:127.0.0.1");
        kt("-importcert", "-noprompt", "-alias", "ca", "-file", f("ca.pem"), "-keystore", f("os.p12"), "-storetype", "PKCS12", "-storepass", "changeit");
        HttpsServer s1 = server("leaf.p12"), s2 = server("leaf2.p12");
        String good = "https://127.0.0.1:" + s1.getAddress().getPort(), other = "https://127.0.0.1:" + s2.getAddress().getPort();
        String byName = "https://localhost:" + s1.getAddress().getPort();
        System.setProperty("dsviz.machine", "ostrust-pc");
        System.setProperty("dsviz.home", f("home"));
        System.setProperty("dsviz.machineDir", f("machine"));

        // 1. בלי התוספת (כמו הקוד הקודם) — נכשל, עם הסבר על סינון (ולא "אין אינטרנט")
        System.setProperty("dsviz.osTrust", "off");
        System.setProperty("dsviz.worker", good);
        Account.Result r = Account.claimStation("K7QM", "מעבדה");
        check("בלי מאגר מערכת ההפעלה — נכשל, הסבר על סינון (" + r.status + ": " + r.message + ")", "offline".equals(r.status) && r.message.contains("סינון"));
        System.clearProperty("dsviz.osTrust");
        OsTrust.resetForTest();

        // 2. עם "מאגר מערכת ההפעלה" שבו תעודת השורש — השרת מגיע
        System.setProperty("dsviz.osTrustStore", f("os.p12"));
        OsTrust.resetForTest();
        r = Account.claimStation("K7QM", "מעבדה");
        check("עם תעודת הסינון במאגר מערכת ההפעלה — Account מגיע לשרת (" + r.status + ")", "bad-code".equals(r.status));
        check("AccessGate (‏/v1/access/config) — מגיע לשרת", Boolean.TRUE.equals(AccessGate.serverRequiresLogin()) && configReached);
        check("WorkerClient — מגיע לשרת", WorkerClient.reachable(3000, good + "/v1/health"));
        check("המאגר הנוסף נטען (" + OsTrust.stores() + ")", OsTrust.stores().contains("os.p12"));

        // 3. לא "סומכים על הכול": שם שרת שלא בתעודה, ושורש שלא במאגר — נכשלים
        System.setProperty("dsviz.worker", byName);
        r = Account.claimStation("K7QM", "מעבדה");
        check("שם שרת שלא בתעודה (localhost מול 127.0.0.1) — נכשל (" + r.status + ")", "offline".equals(r.status));
        System.setProperty("dsviz.worker", other);
        r = Account.claimStation("K7QM", "מעבדה");
        check("תעודה משורש שלא במאגר — נכשלת (" + r.status + ")", "offline".equals(r.status) && r.message.contains("סינון"));
        check("WorkerClient — שורש שלא במאגר לא מגיע", !WorkerClient.reachable(3000, other + "/v1/health"));

        // 4. מה המשתמש רואה כשהחיבור עדיין נחסם (מסך עמדת מחשב)
        if (!java.awt.GraphicsEnvironment.isHeadless()) {
            final java.util.concurrent.atomic.AtomicInteger opened = new java.util.concurrent.atomic.AtomicInteger();
            SwingUtilities.invokeLater(() -> AccessGate.require(null, opened::incrementAndGet));
            Window w = GateUi.waitWindow();
            GateUi.click(w, "הקלדת קוד המורה");
            GateUi.type(w, 0, "K7QM");
            GateUi.click(w, "חיבור המחשב");
            check("מסך עמדת מחשב: הסבר על סינון שבודק תעבורה מוצפנת", GateUi.waitText(w, "סינון"));
            GateUi.shot(w, "tls-filter-message");
            w.dispose();
        }
        s1.stop(0);
        s2.stop(0);
        System.out.println("\n" + pass + " עברו, " + fail + " נכשלו");
        System.exit(fail == 0 ? 0 : 1);
    }

    static volatile boolean configReached;

    static HttpsServer server(String p12) throws Exception {
        KeyStore ks = KeyStore.getInstance("PKCS12");
        try (FileInputStream in = new FileInputStream(f(p12))) { ks.load(in, "changeit".toCharArray()); }
        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(ks, "changeit".toCharArray());
        SSLContext ctx = SSLContext.getInstance("TLS");
        ctx.init(kmf.getKeyManagers(), null, null);
        HttpsServer s = HttpsServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        s.setHttpsConfigurator(new HttpsConfigurator(ctx));
        s.createContext("/", ex -> {
            String path = ex.getRequestURI().getPath();
            if (path.equals("/v1/access/config")) configReached = true;
            String body = path.equals("/v1/access/config") ? "{\"ok\":true,\"enabled\":true}" : path.equals("/v1/station/claim") ? "{\"ok\":true,\"status\":\"bad-code\"}" : "{\"ok\":true}";
            byte[] out = body.getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().set("Content-Type", "application/json");
            ex.sendResponseHeaders(200, out.length);
            try (OutputStream o = ex.getResponseBody()) { o.write(out); }
        });
        s.start();
        return s;
    }

    static void ca(String alias, String dn) throws Exception {
        kt("-genkeypair", "-alias", alias, "-keyalg", "RSA", "-keysize", "2048", "-dname", dn, "-ext", "bc:c", "-validity", "30",
                "-keystore", f(alias + ".p12"), "-storetype", "PKCS12", "-storepass", "changeit", "-keypass", "changeit");
        kt("-exportcert", "-alias", alias, "-keystore", f(alias + ".p12"), "-storepass", "changeit", "-rfc", "-file", f(alias + ".pem"));
    }

    static void leaf(String name, String ca, String san) throws Exception {
        String ks = f(name + ".p12");
        kt("-genkeypair", "-alias", "leaf", "-keyalg", "RSA", "-keysize", "2048", "-dname", "CN=127.0.0.1", "-validity", "30", "-keystore", ks,
                "-storetype", "PKCS12", "-storepass", "changeit", "-keypass", "changeit");
        kt("-certreq", "-alias", "leaf", "-keystore", ks, "-storepass", "changeit", "-file", f(name + ".csr"));
        kt("-gencert", "-alias", ca, "-keystore", f(ca + ".p12"), "-storepass", "changeit", "-infile", f(name + ".csr"), "-outfile", f(name + ".pem"),
                "-rfc", "-ext", "san=" + san, "-ext", "eku=serverAuth", "-validity", "30");
        kt("-importcert", "-noprompt", "-alias", "ca", "-file", f(ca + ".pem"), "-keystore", ks, "-storepass", "changeit");
        kt("-importcert", "-noprompt", "-alias", "leaf", "-file", f(name + ".pem"), "-keystore", ks, "-storepass", "changeit");
    }

    static void kt(String... args) throws Exception {
        List<String> cmd = new ArrayList<String>();
        cmd.add(new File(System.getProperty("java.home"), "bin/keytool").getPath());
        cmd.addAll(Arrays.asList(args));
        ProcessBuilder pb = new ProcessBuilder(cmd).redirectErrorStream(true);
        pb.environment().remove("JAVA_TOOL_OPTIONS");
        Process p = pb.start();
        String out = new String(SignInEdge.readAll(p.getInputStream()), StandardCharsets.UTF_8);
        if (p.waitFor() != 0) throw new IllegalStateException("keytool " + args[0] + ": " + out);
    }

    static String f(String n) {
        return new File(work, n).getPath();
    }

    static void check(String name, boolean ok) {
        if (ok) { pass++; System.out.println("  ok   " + name); } else { fail++; System.out.println("  FAIL " + name); }
    }
}
