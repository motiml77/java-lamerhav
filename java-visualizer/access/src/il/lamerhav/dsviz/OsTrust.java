package il.lamerhav.dsviz;

import java.io.FileInputStream;
import java.io.InputStream;
import java.net.Socket;
import java.net.URLConnection;
import java.security.KeyStore;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509ExtendedTrustManager;
import javax.net.ssl.X509TrustManager;

/**
 * החיבור המאובטח לשרת סומך גם על התעודות של מערכת ההפעלה, ולא רק על אלה שבתוך Java.
 *
 * סינון אינטרנט בבתי ספר ובבתים (נטפרי, רימון וכו') ואנטי-וירוסים "פותחים" את התעבורה המוצפנת ומתקינים תעודת שורש משלהם
 * במאגר התעודות של Windows. הדפדפן ו-.NET סומכים עליה; Java — לא (רק על cacerts שלה), ולכן כל קריאה של DSViz לשרת נכשלה שם.
 * כאן: מנהל אמון מורכב — קודם האמון הרגיל של Java (cacerts / javax.net.ssl.trustStore), ואם הוא דוחה — מאגר מערכת ההפעלה
 * (Windows-ROOT ב-Windows, KeychainStore ב-Mac). אין "סומכים על הכול": כל מאגר מאמת את השרשרת כרגיל, ובדיקת שם השרת נשמרת
 * (X509ExtendedTrustManager עם ה-socket / engine — אותה בדיקה כמו בברירת המחדל). מאגר שלא קיים (Java בלי jdk.crypto.mscapi,
 * מערכת אחרת) — מדלגים בשקט.
 *
 * בבדיקות: -Ddsviz.osTrustStore=קובץ (PKCS12/JKS, סיסמה ב-dsviz.osTrustStorePassword) במקום מאגר מערכת ההפעלה;
 * -Ddsviz.osTrust=off — בלי התוספת (כמו הקוד הישן).
 */
public final class OsTrust {
    private OsTrust() {}

    private static volatile SSLSocketFactory factory;
    private static volatile String stores = "";

    /** מחבר את החיבור לאמון המורכב (רק https; אחרת לא נוגע) */
    public static void apply(URLConnection c) {
        if (!(c instanceof HttpsURLConnection) || "off".equalsIgnoreCase(System.getProperty("dsviz.osTrust", ""))) return;
        SSLSocketFactory f = factory();
        if (f != null) ((HttpsURLConnection) c).setSSLSocketFactory(f);
    }

    /** אילו מאגרים נוספו (לבדיקות / ליומן) */
    public static String stores() {
        factory();
        return stores;
    }

    static synchronized SSLSocketFactory factory() {
        if (factory != null) return factory;
        try {
            List<X509ExtendedTrustManager> all = new ArrayList<X509ExtendedTrustManager>();
            add(all, null);   // האמון הרגיל של Java
            StringBuilder names = new StringBuilder();
            for (KeyStore ks : osStores(names)) add(all, ks);
            stores = names.toString().trim();
            if (all.size() < 2) return null;   // אין מאגר נוסף — משאירים את ברירת המחדל
            SSLContext ctx = SSLContext.getInstance("TLS");
            ctx.init(null, new TrustManager[] { new Composite(all) }, null);
            factory = ctx.getSocketFactory();
            return factory;
        } catch (Exception e) {
            return null;   // ברירת המחדל של Java
        }
    }

    /** לבדיקות: לבנות מחדש (אחרי שינוי של dsviz.osTrustStore) */
    static synchronized void resetForTest() {
        factory = null;
        stores = "";
    }

    private static void add(List<X509ExtendedTrustManager> all, KeyStore ks) throws Exception {
        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(ks);
        for (TrustManager tm : tmf.getTrustManagers()) if (tm instanceof X509ExtendedTrustManager) all.add((X509ExtendedTrustManager) tm);
    }

    private static List<KeyStore> osStores(StringBuilder names) {
        List<KeyStore> out = new ArrayList<KeyStore>();
        String test = System.getProperty("dsviz.osTrustStore", "");
        if (!test.isEmpty()) {
            try (InputStream in = new FileInputStream(test)) {
                KeyStore ks = KeyStore.getInstance(test.toLowerCase().endsWith(".jks") ? "JKS" : "PKCS12");
                ks.load(in, System.getProperty("dsviz.osTrustStorePassword", "changeit").toCharArray());
                out.add(ks);
                names.append(" test:").append(test);
            } catch (Exception ignored) {
                // אין מאגר בדיקה
            }
            return out;
        }
        String os = System.getProperty("os.name", "").toLowerCase();
        String[] types = os.contains("win") ? new String[] { "Windows-ROOT" } : os.contains("mac") ? new String[] { "KeychainStore" } : new String[0];
        for (String t : types) {
            try {
                KeyStore ks = KeyStore.getInstance(t);
                ks.load(null, null);
                if (ks.size() > 0) {
                    out.add(ks);
                    names.append(' ').append(t);
                }
            } catch (Exception ignored) {
                // המאגר לא קיים ב-Java הזו (למשל בלי jdk.crypto.mscapi)
            }
        }
        return out;
    }

    /** חיבור מאובטח שנכשל בגלל התעודה (סינון / אנטי-וירוס שבודק תעבורה מוצפנת) — ולא סתם בלי אינטרנט */
    public static boolean isTlsProblem(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof javax.net.ssl.SSLHandshakeException || t instanceof java.security.cert.CertificateException
                    || t instanceof javax.net.ssl.SSLPeerUnverifiedException) return true;
            if (t.getCause() == t) break;
        }
        return false;
    }

    /** ההסבר למשתמש */
    public static final String TLS_MESSAGE = "החיבור המאובטח לשרת של DSViz נחסם במחשב הזה. כנראה תוכנת סינון אינטרנט (כמו נטפרי או רימון) או אנטי-וירוס"
            + " שבודקים תעבורה מוצפנת. מבקשים מספק הסינון / מרכז המחשבים להתקין את תעודת האבטחה שלהם ב-Windows, או לאשר את השרת של DSViz — ומנסים שוב.";

    /** קודם הרגיל, אחר כך מאגרי מערכת ההפעלה; הראשון שמאשר — מאשר. אחרת — החריגה של הרגיל */
    static final class Composite extends X509ExtendedTrustManager {
        private final List<X509ExtendedTrustManager> all;

        Composite(List<X509ExtendedTrustManager> all) {
            this.all = all;
        }

        interface Check {
            void run(X509ExtendedTrustManager tm) throws CertificateException;
        }

        private void each(Check c) throws CertificateException {
            CertificateException first = null;
            for (X509ExtendedTrustManager tm : all) {
                try {
                    c.run(tm);
                    return;
                } catch (CertificateException e) {
                    if (first == null) first = e;
                }
            }
            throw first != null ? first : new CertificateException("no trust manager");
        }

        @Override
        public void checkServerTrusted(final X509Certificate[] chain, final String authType, final Socket socket) throws CertificateException {
            each(tm -> tm.checkServerTrusted(chain, authType, socket));
        }

        @Override
        public void checkServerTrusted(final X509Certificate[] chain, final String authType, final SSLEngine engine) throws CertificateException {
            each(tm -> tm.checkServerTrusted(chain, authType, engine));
        }

        @Override
        public void checkServerTrusted(final X509Certificate[] chain, final String authType) throws CertificateException {
            each(tm -> tm.checkServerTrusted(chain, authType));
        }

        @Override
        public void checkClientTrusted(final X509Certificate[] chain, final String authType, final Socket socket) throws CertificateException {
            each(tm -> tm.checkClientTrusted(chain, authType, socket));
        }

        @Override
        public void checkClientTrusted(final X509Certificate[] chain, final String authType, final SSLEngine engine) throws CertificateException {
            each(tm -> tm.checkClientTrusted(chain, authType, engine));
        }

        @Override
        public void checkClientTrusted(final X509Certificate[] chain, final String authType) throws CertificateException {
            each(tm -> tm.checkClientTrusted(chain, authType));
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            List<X509Certificate> out = new ArrayList<X509Certificate>();
            for (X509TrustManager tm : all) for (X509Certificate c : tm.getAcceptedIssuers()) out.add(c);
            return out.toArray(new X509Certificate[0]);
        }
    }
}
