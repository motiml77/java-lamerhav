package il.lamerhav.dsviz;

import java.io.FileInputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.KeyStore;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;

/**
 * בדיקת "סינון שבודק תעבורה מוצפנת" ב-Windows אמיתי (os-trust.ps1):
 *   serve <pfx> <סיסמה> <קובץ-פורט>  — שרת HTTPS מקומי עם תעודה שנחתמה בתעודת שורש שהותקנה במאגר של Windows (רץ ב-JDK המלא)
 *   check <url>                      — הלקוח של DSViz (Account.post, עם OsTrust) — מדפיס "STATUS <status>", "STORES ...", "MESSAGE ..."
 */
public class TlsCheck {
    public static void main(String[] a) throws Exception {
        if (a[0].equals("serve")) {
            KeyStore ks = KeyStore.getInstance("PKCS12");
            try (FileInputStream in = new FileInputStream(a[1])) { ks.load(in, a[2].toCharArray()); }
            KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            kmf.init(ks, a[2].toCharArray());
            SSLContext ctx = SSLContext.getInstance("TLS");
            ctx.init(kmf.getKeyManagers(), null, null);
            com.sun.net.httpserver.HttpsServer s = com.sun.net.httpserver.HttpsServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            s.setHttpsConfigurator(new com.sun.net.httpserver.HttpsConfigurator(ctx));
            s.createContext("/", ex -> {
                byte[] out = "{\"ok\":true,\"enabled\":true}".getBytes(StandardCharsets.UTF_8);
                ex.getResponseHeaders().set("Content-Type", "application/json");
                ex.sendResponseHeaders(200, out.length);
                try (OutputStream o = ex.getResponseBody()) { o.write(out); }
            });
            s.start();
            Files.write(Paths.get(a[3]), String.valueOf(s.getAddress().getPort()).getBytes(StandardCharsets.US_ASCII));
            Thread.sleep(Long.MAX_VALUE);
        }
        System.setProperty("dsviz.worker", a[1]);
        Account.Result r = Account.post("/v1/access/config", new java.util.LinkedHashMap<String, Object>());
        System.out.println("JAVA " + System.getProperty("java.home"));
        System.out.println("STORES " + OsTrust.stores());
        System.out.println("STATUS " + r.status);
        System.out.println("MESSAGE " + r.message);
        System.exit(0);
    }
}
