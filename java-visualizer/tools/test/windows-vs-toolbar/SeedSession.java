package il.lamerhav.dsviz;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * לבדיקת סרגל הכלים ב-Windows (toolbar-e2e.ps1): "כניסה" מוכנה במחשב בלי Google — עם מפתח בדיקה (לא המפתח של השרת):
 *   key <קובץ-מפתח>                     — יוצר זוג מפתחות (אם אין) ומדפיס "PASSKEY <מפתח ציבורי>" — ל-DSVIZ_PASS_KEY לפני ש-Visual Studio עולה
 *   teacher|student <מייל> <קובץ-מפתח>  — שומר כניסה של מורה / תלמיד (אישור חתום במפתח הבדיקה) במקום הנסתר האמיתי של המשתמש
 *   none <-> <קובץ-מפתח>                — מוחק את הכניסה
 * אחרי כל שינוי — Account.syncTeacherFlag (הסימון שהסרגל קורא). מריצים ב-Java של ההתקנה, עם dsviz-bank-vs.jar במסלול.
 */
public class SeedSession {
    public static void main(String[] a) throws Exception {
        File keyFile = new File(a[a.length - 1]);
        if (!keyFile.isFile()) {
            KeyPairGenerator g = KeyPairGenerator.getInstance("RSA");
            g.initialize(2048);
            KeyPair kp = g.generateKeyPair();
            String text = Base64.getEncoder().encodeToString(kp.getPrivate().getEncoded()) + "\n" + Base64.getEncoder().encodeToString(kp.getPublic().getEncoded()) + "\n";
            Files.write(keyFile.toPath(), text.getBytes(StandardCharsets.US_ASCII));
        }
        String[] lines = new String(Files.readAllBytes(keyFile.toPath()), StandardCharsets.US_ASCII).split("\n");
        System.setProperty("dsviz.passKey", lines[1].trim());
        if (a[0].equals("key")) {
            System.out.println("PASSKEY " + lines[1].trim());
            return;
        }
        File uf = Account.userFile();
        if (a[0].equals("none")) {
            uf.delete();
            File b = new File(Account.secureBackupDir() == null ? uf.getParentFile() : Account.secureBackupDir(), uf.getName());
            b.delete();
        } else {
            String role = a[0], email = a[1];
            PrivateKey key = KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(lines[0].trim())));
            long now = System.currentTimeMillis() / 1000;
            String payload = "{\"v\":1,\"kind\":\"" + role + "\",\"sub\":\"" + role + ":" + email + "\",\"name\":\"\",\"email\":\"" + email + "\",\"until\":\""
                    + (role.equals("student") ? "2099-08-31" : "") + "\",\"mid\":\"" + Account.machine() + "\",\"iat\":" + now + ",\"exp\":" + (now + 30L * 86400) + "}";
            String text = Base64.getUrlEncoder().withoutPadding().encodeToString(payload.getBytes(StandardCharsets.UTF_8));
            Signature s = Signature.getInstance("SHA256withRSA");
            s.initSign(key);
            s.update(text.getBytes(StandardCharsets.US_ASCII));
            Map<String, Object> d = new LinkedHashMap<String, Object>();
            d.put("token", "u.seedsession." + Long.toHexString(System.nanoTime()) + "seedseedseedseed");
            d.put("pass", text + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(s.sign()));
            d.put("status", "ok");
            d.put("email", email);
            d.put("role", role);
            if (role.equals("teacher")) {
                d.put("teacherCode", "K7QM");
                d.put("teacherSeats", 30.0);
                d.put("teacherUsed", 12.0);
                d.put("teacherName", "מורה בדיקה");
            }
            Account.save(uf, d);
        }
        Account.syncTeacherFlag();
        Account.Access c = Account.current();
        System.out.println("SEEDED " + a[0] + " -> " + (c == null ? "none" : c.kind) + " flag=" + (Account.teacherFlagFile() != null && Account.teacherFlagFile().isFile()));
    }
}
