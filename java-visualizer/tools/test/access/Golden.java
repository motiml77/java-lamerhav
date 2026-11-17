package il.lamerhav.dsviz;

import java.io.File;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.KeyPairGenerator;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.sun.net.httpserver.HttpServer;

/**
 * "קובץ הזהב" של הכניסה (Account): עמדת מחשב / מורה / תלמיד נשארים מחוברים אחרי עדכון, התקנה מחדש ותקלות — בסידור האמיתי של
 * התיקיות (לא dsviz.home / dsviz.machineDir), כמו ב-Windows וב-Mac (‎-Ddsviz.os‎, ‎-Ddsviz.env.*‎, ‎-Ddsviz.fsroot‎ — מתחת לתיקייה
 * זמנית), מול שרת מדומה (בלי אמולטור ובלי אינטרנט).
 *   1. עמדה: קובץ אחד קבוע לכל המחשב + ‎.bak‎ + עותק בתיקיית הגיבוי; מוצפן, עם מזהה ההתקנה בפנים
 *   2. עדכון / התקנה מחדש (תיקיית ההתקנה נמחקת ומוחלפת), המקומות הישנים נמחקים, i.dat נמחק — עדיין מחובר
 *   3. קובץ ראשי פגום / חסר — משוחזר מ-‎.bak‎ / מהעותק בתיקיית הגיבוי
 *   4. תקלות רשת / שרת / סינון — לא מנתקות; "לא תקף" לעמדה — לא מנתק
 *   5. עמדה: אין התנתקות (canSignOut, signOut לא נוגע בה); נמחקת רק כשהמורה משחרר (revoked) — עם כל העותקים
 *   6. מורה / תלמיד: נשארים אחרי עדכון ותקלות; "התנתקות" מוחקת את כל העותקים, ושום דבר לא מחזיר אותם
 *   7. העברה חד-פעמית מהמקום הישן (‎ProgramData\DSViz\station.json‎, ‎~/.dsviz/account.json‎) — בלי לאבד כניסה
 *   8. Mac: ‎/Users/Shared/.lamerhav‎ + ‎~/.local/share/.lamerhav‎ — לא נוגעים בהם עדכון (install.sh) והסרה (uninstall.sh)
 *   9. ההתקנות וסקריפטי העדכון לא מוחקים את התיקיות של קובץ הזהב (סריקה של installer/)
 *   java -cp <classes>:<lib> il.lamerhav.dsviz.Golden <work-dir> [<java-visualizer>]
 */
public class Golden {
    static File root;

    public static void main(String[] a) throws Exception {
        File work = new File(a[0]);
        File repo = new File(a.length > 1 ? a[1] : ".");
        SignInEdge.keys = KeyPairGenerator.getInstance("RSA").generateKeyPair();
        HttpServer mock = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        mock.createContext("/", ex -> {
            String path = ex.getRequestURI().getPath();
            SignInEdge.readAll(ex.getRequestBody());
            String[] ans = SignInEdge.answers.get(path);
            if (ans == null) ans = new String[] { "404", "{\"ok\":false,\"code\":\"not_found\",\"error\":\"?\"}", "application/json" };
            byte[] out = ans[1].getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().set("Content-Type", ans[2]);
            ex.sendResponseHeaders(Integer.parseInt(ans[0]), out.length == 0 ? -1 : out.length);
            if (out.length > 0) try (OutputStream o = ex.getResponseBody()) { o.write(out); }
            ex.close();
        });
        mock.start();
        String server = "http://127.0.0.1:" + mock.getAddress().getPort();
        System.setProperty("dsviz.passKey", Base64.getEncoder().encodeToString(SignInEdge.keys.getPublic().getEncoded()));
        System.setProperty("dsviz.worker", server);
        System.setProperty("dsviz.hostname", "LAB-07");
        SignInEdge.answers.put("/v1/access/logout", SignInEdge.json("{\"ok\":true}"));

        // ================================================================ 1. עמדה: קובץ הזהב (Windows)
        windows(work, "w1");
        File pd = new File(root, "ProgramData/Lamerhav/.cache"), mirror = new File(root, "Users/u1/AppData/Roaming/Lamerhav/.cache");
        File sdat = new File(pd, "s.dat");
        String tok = "s.station1234.secretsecretsecretsecretG1";
        claimAnswer(tok);
        Account.Result r = Account.claimStation("K7QM", "עמדה 7");
        check("עמדה מתחברת (" + r.status + " " + r.message + ")", r.ok() && station());
        check("קובץ הזהב של העמדה: %ProgramData%\\Lamerhav\\.cache\\s.dat", sdat.isFile());
        check("  ‎s.dat.bak‎ לידו (כל משתמש של המחשב רואה)", new File(pd, "s.dat.bak").isFile());
        check("  ועותק ב-%APPDATA%\\Lamerhav\\.cache (תיקייה אחרת לגמרי)", new File(mirror, "s.dat").isFile());
        String raw = read(sdat);
        check("  מוצפן: הכרטיס והאישור לא כתובים בגלוי", !raw.contains(tok) && !raw.contains("station1234") && raw.contains("\"data\""));
        check("  מזהה ההתקנה בפנים (השדה id — כמו i.dat)", Json.str(Json.obj(Json.parse(raw)).get("id")).startsWith(idLine0()));
        check("  בלי קבצים זמניים שנשארו (כתיבה אטומית)", tmpLeft(root).isEmpty());
        String mid = Account.machine();

        // ================================================================ 2. עדכון / התקנה מחדש / מחיקת המקומות הישנים / i.dat
        File inst = new File(root, "Users/u1/AppData/Local/DSViz");
        fakeInstall(inst);
        rmrf(inst);                                   // ההתקנה הקודמת נמחקה (הסרה מלאה / התקנה מחדש)
        fakeInstall(inst);                            // והחדשה הותקנה
        rmrf(new File(root, "ProgramData/DSViz"));    // המקומות הישנים (עד 1.0.1)
        rmrf(new File(root, "Users/u1/.dsviz"));
        restart();
        check("אחרי עדכון / התקנה מחדש (תיקיית ההתקנה הוחלפה) ומחיקת המקומות הישנים — עדיין עמדה", station() && Account.machine().equals(mid));
        for (File f : new File[] { new File(pd, "i.dat"), new File(root, "Users/u1/AppData/Local/Lamerhav/.cache/i.dat"), new File(mirror, "i.dat") }) f.delete();
        restart();
        check("i.dat נמחק בכל המקומות — אותו מזהה (מתוך קובץ הזהב), ועדיין עמדה", Account.machine().equals(mid) && station());
        check("  ו-i.dat נכתב מחדש", new File(pd, "i.dat").isFile());

        // ================================================================ 3. קובץ ראשי פגום / חסר
        byte[] good = Files.readAllBytes(sdat.toPath());
        Files.write(sdat.toPath(), java.util.Arrays.copyOf(good, good.length / 2));   // נכתב בחצי (כיבוי באמצע)
        restart();
        check("קובץ ראשי חתוך — נקרא מ-‎.bak‎, עדיין עמדה", station());
        check("  והקובץ הראשי שוחזר (נפתח שוב), החתוך נשמר בצד כ-‎.bad‎", Account.load(sdat) != null && opensDirect(sdat) && new File(pd, "s.dat.bad").isFile());
        Files.write(sdat.toPath(), "זבל".getBytes(StandardCharsets.UTF_8));
        new File(pd, "s.dat.bak").delete();
        restart();
        check("קובץ ראשי פגום ובלי ‎.bak‎ — משוחזר מהעותק בתיקיית הגיבוי", station() && opensDirect(sdat));
        sdat.delete();
        restart();
        check("קובץ ראשי נמחק — משוחזר", station() && sdat.isFile());
        Account.refresh();   // (השרת לא עונה כאן — 404) — משלים את הגיבויים שחסרו
        check("  בדיקה מול השרת משלימה את ‎.bak‎ שחסר", new File(pd, "s.dat.bak").isFile() && sameBytes(sdat, new File(pd, "s.dat.bak")));

        // ================================================================ 4. תקלות — לא מנתקות
        String[][] glitches = {
            { "500", "{\"ok\":false,\"code\":\"internal\",\"error\":\"תקלה בשרת\"}", "application/json" },
            { "429", "{\"ok\":false,\"code\":\"rate\",\"error\":\"יותר מדי\"}", "application/json" },
            { "503", "<html>Service Unavailable</html>", "text/html" },
            { "200", "<html><body>האתר נחסם על ידי מערכת הסינון</body></html>", "text/html" },
            { "200", "", "text/html" },
            { "200", "{}", "application/json" },
            { "200", "{\"ok\":true,\"status\":\"ok\",\"pass\":\"forged.signature\"}", "application/json" },
            { "200", "{\"ok\":true,\"status\":\"invalid\",\"why\":\"token\"}", "application/json" },
            { "200", "{\"ok\":true,\"status\":\"invalid\"}", "application/json" },
        };
        for (String[] g : glitches) {
            SignInEdge.answers.put("/v1/access/check", g);
            r = Account.refresh();
            restart();
            check("עמדה, בדיקה מול השרת: " + g[0] + " " + (g[1].isEmpty() ? "(ריק)" : g[1].substring(0, Math.min(34, g[1].length()))) + " — עדיין עמדה (" + r.status + ")", station());
        }
        System.setProperty("dsviz.worker", "http://127.0.0.1:9");   // בלי אינטרנט
        r = Account.refresh();
        check("עמדה בלי אינטרנט — עדיין עמדה (" + r.status + ")", "offline".equals(r.status) && station());
        System.setProperty("dsviz.worker", server);

        // ================================================================ 5. עמדה: אין התנתקות; רק המורה משחרר
        check("עמדה: אין \"התנתקות\" (canSignOut)", !AccessGate.canSignOut());
        r = Account.signOut();
        check("  signOut לא נוגע בעמדה", station() && sdat.isFile() && new File(pd, "s.dat.bak").isFile());
        SignInEdge.signedIn("student", "pending@x.org", "u.sid21.secretsecretsecretsecret21");
        Map<String, Object> u = Account.userState();
        u.put("pass", "");
        u.put("status", "pay");
        Account.save(Account.userFile(), u);
        check("  תלמיד שעוד לא שילם על עמדה — עדיין בלי \"התנתקות\" (שלא ייראה כמו ניתוק העמדה)", !AccessGate.canSignOut() && station());
        SignInEdge.signedIn("teacher", "t@x.org", "u.sid22.secretsecretsecretsecret22");
        check("מורה נכנס עם Google על העמדה — יש לו \"התנתקות\" (מהחשבון שלו)", AccessGate.canSignOut() && Account.current().isTeacher());
        r = Account.signOut();
        check("  התנתקות המורה — העמדה נשארת", station() && Account.userState() == null);
        SignInEdge.answers.put("/v1/access/check", SignInEdge.json("{\"ok\":true,\"status\":\"revoked\",\"why\":\"released\",\"profile\":null}"));
        Account.refresh();
        check("המורה שחרר את העמדה — לא מחוברת", Account.current() == null);
        check("  כל העותקים נמחקו (s.dat, s.dat.bak, העותק בתיקיית הגיבוי)", !sdat.isFile() && !new File(pd, "s.dat.bak").isFile() && !new File(mirror, "s.dat").isFile());
        restart();
        check("  ואחרי הפעלה מחדש — עדיין לא מחוברת (שום דבר לא מחזיר אותה)", Account.current() == null && !Account.hasSavedToken());

        // ================================================================ 6. מורה / תלמיד
        windows(work, "w2");
        File la = new File(root, "Users/u1/AppData/Local/Lamerhav/.cache"), mirror2 = new File(root, "Users/u1/AppData/Roaming/Lamerhav/.cache");
        File adat = new File(la, "a.dat");
        SignInEdge.signedIn("teacher", "t2@x.org", "u.sid31.secretsecretsecretsecret31");
        check("מורה: קובץ הזהב %LOCALAPPDATA%\\Lamerhav\\.cache\\a.dat + ‎.bak‎ + עותק ב-%APPDATA%", adat.isFile() && new File(la, "a.dat.bak").isFile() && new File(mirror2, "a.dat").isFile());
        check("  לא בתוך תיקיית ההתקנה (%LOCALAPPDATA%\\DSViz)", !adat.getPath().contains(File.separator + "DSViz" + File.separator));
        check("  רק המשתמש קורא (‎0600‎)", perms(adat).equals("rw-------") && perms(new File(la, "a.dat.bak")).equals("rw-------"));
        File inst2 = new File(root, "Users/u1/AppData/Local/DSViz");
        fakeInstall(inst2);
        rmrf(inst2);
        fakeInstall(inst2);
        restart();
        check("מורה אחרי עדכון / התקנה מחדש — עדיין מחובר", teacher());
        for (String[] g : glitches) {
            if (g[1].contains("invalid")) continue;   // למורה "לא תקף" הוא תשובה אמיתית של השרת (אין חיבור כזה)
            SignInEdge.answers.put("/v1/access/check", g);
            Account.refresh();
            restart();
            check("מורה, בדיקה מול השרת: " + g[0] + " " + (g[1].isEmpty() ? "(ריק)" : g[1].substring(0, Math.min(34, g[1].length()))) + " — עדיין מחובר", teacher());
        }
        System.setProperty("dsviz.worker", "http://127.0.0.1:9");
        check("מורה בלי אינטרנט — עדיין מחובר", "offline".equals(Account.refresh().status) && teacher());
        check("  יש \"התנתקות\"", AccessGate.canSignOut());
        r = Account.signOut();   // בלי אינטרנט: נמחק מהמחשב, הביטול בשרת — בפעם הבאה
        check("\"התנתקות\" בלי אינטרנט — מנותק, וכל העותקים נמחקו (" + r.status + ")", Account.current() == null && !adat.isFile() && !new File(la, "a.dat.bak").isFile() && !new File(mirror2, "a.dat").isFile());
        restart();
        check("  אחרי הפעלה מחדש — עדיין מנותק", Account.current() == null && Account.userState() == null);
        System.setProperty("dsviz.worker", server);
        SignInEdge.signedIn("student", "s2@x.org", "u.sid32.secretsecretsecretsecret32");
        restart();
        check("תלמיד: נשאר מחובר אחרי הפעלה מחדש", Account.current() != null && Account.current().isStudent() && AccessGate.canSignOut());
        r = Account.signOut();
        restart();
        check("  \"התנתקות\" — מנותק, וכל העותקים נמחקו (" + r.status + ")", Account.current() == null && !adat.isFile() && !new File(la, "a.dat.bak").isFile() && !new File(mirror2, "a.dat").isFile());

        // ================================================================ 7. העברה מהמקום הישן
        windows(work, "w3");
        File legacyS = new File(root, "ProgramData/DSViz/station.json");
        writeLegacy(legacyS, "s.station1234.secretsecretsecretsecretL1", 100);
        restart();
        check("עמדה מגרסה ישנה (ProgramData\\DSViz\\station.json) — מחוברת אחרי העדכון", station());
        File sdat3 = new File(root, "ProgramData/Lamerhav/.cache/s.dat");
        check("  עברה לקובץ הזהב, עם ‎.bak‎", sdat3.isFile() && new File(sdat3.getParentFile(), "s.dat.bak").isFile()
                && "s.station1234.secretsecretsecretsecretL1".equals(Json.str(Account.load(sdat3).get("token"))));
        check("  הישן לא נמחק — שמו שונה ל-‎station.json.migrated‎ (לא נקרא שוב)", !legacyS.isFile() && new File(legacyS.getPath() + ".migrated").isFile());
        File legacyU = new File(root, "Users/u1/.dsviz/account.json");
        writeLegacyUser(legacyU, "teacher", "old@x.org", "u.sid41.secretsecretsecretsecret41", 100);
        restart();
        check("מורה מגרסה ישנה (~/.dsviz/account.json) — מחובר, בקובץ הזהב", teacher() && new File(root, "Users/u1/AppData/Local/Lamerhav/.cache/a.dat").isFile()
                && new File(legacyU.getPath() + ".migrated").isFile() && !legacyU.isFile());
        Account.signOut();
        restart();
        check("  אחרי \"התנתקות\" — הישן לא חוזר לחיים", Account.current() == null || Account.current().isStation());
        // ישן חדש יותר מקובץ הזהב (חזרו לגרסה ישנה ונכנסו שם מחדש) — נבחר הישן
        writeLegacy(legacyS, "s.station1234.secretsecretsecretsecretL2", System.currentTimeMillis() + 60000);
        restart();
        check("ישן שנבדק מאוחר יותר מקובץ הזהב — הוא נבחר", "s.station1234.secretsecretsecretsecretL2".equals(Account.headers().get("X-DSViz-Access")));
        // ישן ישן יותר — קובץ הזהב נשאר
        writeLegacy(legacyS, "s.station1234.secretsecretsecretsecretL3", 50);
        restart();
        check("ישן שנבדק לפני קובץ הזהב — קובץ הזהב נשאר", "s.station1234.secretsecretsecretsecretL2".equals(Account.headers().get("X-DSViz-Access")) && !legacyS.isFile());
        // ישן שלא נפתח — נשאר במקומו, כמו שהוא
        new File(legacyS.getPath() + ".migrated").delete();
        Files.write(legacyS.toPath(), "{\"v\":1,\"data\":\"AAAA\"}".getBytes(StandardCharsets.UTF_8));
        restart();
        check("ישן שלא נפתח — לא נוגעים בו, וקובץ הזהב עדיין מחובר", legacyS.isFile() && station());
        legacyS.delete();
        // אי אפשר לשנות את שם הישן (קובץ של משתמש Windows אחר) — נרשם שהועבר, ולא חוזר לחיים אחרי שחרור
        windows(work, "w4");
        legacyS = new File(root, "ProgramData/DSViz/station.json");
        writeLegacy(legacyS, "s.station1234.secretsecretsecretsecretL4", 100);
        File block = new File(legacyS.getPath() + ".migrated");
        block.mkdirs();
        Files.write(new File(block, "x").toPath(), new byte[] { 1 });   // תיקייה לא ריקה בשם היעד — שינוי השם נכשל
        restart();
        check("ישן שאי אפשר לשנות את שמו — עבר לקובץ הזהב, ונשאר במקומו", station() && legacyS.isFile());
        SignInEdge.answers.put("/v1/access/check", SignInEdge.json("{\"ok\":true,\"status\":\"revoked\",\"why\":\"released\",\"profile\":null}"));
        Account.refresh();
        restart();
        check("  המורה שחרר — לא מחוברת, והישן לא מחזיר אותה", Account.current() == null && legacyS.isFile());

        // ================================================================ 8. Mac
        mac(work, "m1");
        claimAnswer("s.station1234.secretsecretsecretsecretM1");
        r = Account.claimStation("K7QM", "Mac 1");
        File shared = new File(root, "Users/Shared/.lamerhav");
        check("Mac: עמדה ב-/Users/Shared/.lamerhav/s.dat (+ ‎.bak‎, + ~/.config/.lamerhav)", r.ok() && new File(shared, "s.dat").isFile() && new File(shared, "s.dat.bak").isFile()
                && new File(root, "Users/u1/.config/.lamerhav/s.dat").isFile());
        SignInEdge.signedIn("teacher", "mac@x.org", "u.sid51.secretsecretsecretsecret51");
        check("Mac: מורה ב-~/.local/share/.lamerhav/a.dat", new File(root, "Users/u1/.local/share/.lamerhav/a.dat").isFile());
        File app = new File(root, "Users/u1/Library/Application Support/DSViz");
        fakeInstall(app);
        rmrf(app);                     // install.sh: התיקייה מוחלפת כולה בעדכון; uninstall.sh: ‎rm -rf "$APP"‎
        rmrf(new File(root, "Users/u1/Library/Logs/DSViz"));
        fakeInstall(app);
        restart();
        check("Mac אחרי עדכון / הסרה והתקנה (‎Application Support/DSViz‎ הוחלפה) — מורה ועמדה מחוברים", teacher() && Account.stationConnected());

        // ================================================================ 9. ההתקנות לא מוחקות את קובץ הזהב
        List<String> bad = scanInstallers(new File(repo, "installer"));
        check("installer/: אף שורה לא מוחקת את Lamerhav / .lamerhav / ~/.dsviz (" + bad.size() + ")" + (bad.isEmpty() ? "" : "\n      " + String.join("\n      ", bad)), bad.isEmpty());

        mock.stop(0);
        System.out.println(SignInEdge.fail == 0 ? "Golden: כל " + SignInEdge.pass + " הבדיקות עברו" : "Golden: " + SignInEdge.fail + " נכשלו");
        System.exit(SignInEdge.fail == 0 ? 0 : 1);
    }

    static void check(String name, boolean ok) { SignInEdge.check(name, ok); }

    static boolean station() { Account.Access c = Account.current(); return c != null && c.isStation(); }

    static boolean teacher() { Account.Access c = Account.current(); return c != null && c.isTeacher(); }

    static void clear() {
        for (String p : new String[] { "dsviz.home", "dsviz.machineDir", "dsviz.machine", "dsviz.os", "dsviz.fsroot",
            "dsviz.env.LOCALAPPDATA", "dsviz.env.APPDATA", "dsviz.env.ProgramData" }) System.clearProperty(p);
        SignInEdge.answers.remove("/v1/access/check");
    }

    /** מחשב Windows חדש מתחת ל-work/pc: ‎C:\ProgramData‎, ‎C:/Users/u1/AppData\{Local,Roaming}‎ */
    static void windows(File work, String pc) {
        clear();
        root = new File(work, pc);
        System.setProperty("dsviz.os", "windows");
        System.setProperty("user.home", new File(root, "Users/u1").getPath());
        System.setProperty("dsviz.env.LOCALAPPDATA", new File(root, "Users/u1/AppData/Local").getPath());
        System.setProperty("dsviz.env.APPDATA", new File(root, "Users/u1/AppData/Roaming").getPath());
        System.setProperty("dsviz.env.ProgramData", new File(root, "ProgramData").getPath());
        Account.resetForTest();
    }

    static void mac(File work, String pc) {
        clear();
        root = new File(work, pc);
        System.setProperty("dsviz.os", "mac");
        System.setProperty("dsviz.fsroot", root.getPath());
        System.setProperty("user.home", new File(root, "Users/u1").getPath());
        Account.resetForTest();
    }

    /** כמו תהליך חדש (כיבוי והדלקה / Java אחרת / אחרי עדכון) */
    static void restart() { Account.resetForTest(); }

    static void claimAnswer(String token) throws Exception {
        Account.machine();
        SignInEdge.answers.put("/v1/station/claim", SignInEdge.json("{\"ok\":true,\"status\":\"ok\",\"token\":\"" + token + "\",\"pass\":\""
                + SignInEdge.makePass("station", "", 0) + "\",\"profile\":{\"role\":\"station\",\"email\":\"\",\"name\":\"עמדה\"}}"));
    }

    static String idLine0() throws Exception {
        File f = new File(root, "ProgramData/Lamerhav/.cache/i.dat");
        return read(f).split("\n")[0];
    }

    static String read(File f) throws Exception { return new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8); }

    static boolean sameBytes(File x, File y) throws Exception { return java.util.Arrays.equals(Files.readAllBytes(x.toPath()), Files.readAllBytes(y.toPath())); }

    /** הקובץ הראשי עצמו נפתח (בלי הגיבויים): משנים זמנית את שמות הגיבויים */
    static boolean opensDirect(File f) throws Exception {
        File b = Account.bakOf(f), bt = new File(f.getPath() + ".hold");
        boolean had = b.renameTo(bt);
        File m = null, mt = null;
        for (File x : Account.goldenForTest()) if (x.getName().equals(f.getName()) && !x.getAbsoluteFile().equals(f.getAbsoluteFile())) m = x;
        if (m != null) { mt = new File(m.getPath() + ".hold"); m.renameTo(mt); }
        try {
            return Account.load(f) != null;
        } finally {
            if (had) bt.renameTo(b);
            if (mt != null) mt.renameTo(m);
        }
    }

    static String perms(File f) {
        try { return java.nio.file.attribute.PosixFilePermissions.toString(Files.getPosixFilePermissions(f.toPath())); } catch (Exception e) { return "?"; }
    }

    static List<File> tmpLeft(File dir) {
        List<File> out = new ArrayList<File>();
        File[] fs = dir.listFiles();
        if (fs != null) for (File f : fs) {
            if (f.isDirectory()) out.addAll(tmpLeft(f));
            else if (f.getName().endsWith(".tmp")) out.add(f);
        }
        return out;
    }

    static void fakeInstall(File dir) throws Exception {
        new File(dir, "lab").mkdirs();
        Files.write(new File(dir, "dsviz.jar").toPath(), new byte[] { 1, 2, 3 });
        Files.write(new File(dir, "lab/dsviz-bank-vs.jar").toPath(), new byte[] { 4 });
        Files.write(new File(dir, "lang.txt").toPath(), "both".getBytes(StandardCharsets.UTF_8));
    }

    static void rmrf(File f) {
        File[] fs = f.listFiles();
        if (fs != null) for (File x : fs) rmrf(x);
        f.delete();
    }

    /** כמו קובץ של גרסה קודמת: מוצפן באותו מפתח, בלי ‎.bak‎, בלי עותק גיבוי ובלי השדה id */
    static void writeLegacy(File f, String token, double checkedAt) throws Exception {
        Map<String, Object> d = new LinkedHashMap<String, Object>();
        d.put("token", token);
        d.put("pass", SignInEdge.makePass("station", "", 0));
        d.put("status", "ok");
        d.put("checkedAt", checkedAt);
        legacySave(f, d);
    }

    static void writeLegacyUser(File f, String role, String email, String token, double checkedAt) throws Exception {
        Map<String, Object> d = new LinkedHashMap<String, Object>();
        d.put("token", token);
        d.put("pass", SignInEdge.makePass(role, email, 0));
        d.put("status", "ok");
        d.put("email", email);
        d.put("role", role);
        d.put("checkedAt", checkedAt);
        legacySave(f, d);
    }

    static void legacySave(File f, Map<String, Object> d) throws Exception {
        File tmp = new File(new File(root, "tmp-legacy"), f.getName());
        Account.save(tmp, d);
        Map<String, Object> outer = Json.obj(Json.parse(read(tmp)));
        outer.remove("id");
        f.getParentFile().mkdirs();
        Files.write(f.toPath(), Json.write(outer).getBytes(StandardCharsets.UTF_8));
        File mirror = new File(System.getProperty("dsviz.env.APPDATA", new File(root, "Users/u1/.config").getPath()), "Lamerhav/.cache/" + f.getName());
        mirror.delete();
        new File(root, "Users/u1/.config/.lamerhav/" + f.getName()).delete();
        rmrf(tmp.getParentFile());
    }

    /**
     * שורות בהתקנות / בעדכון / בהסרה שמוחקות את התיקיות של קובץ הזהב (Lamerhav, ‎.lamerhav‎) או את ‎~/.dsviz‎ כולה (המקום הישן —
     * צריך להישאר עד שהכניסה עוברת). שורות הערה לא נספרות
     */
    static List<String> scanInstallers(File dir) throws Exception {
        List<String> out = new ArrayList<String>();
        Pattern del = Pattern.compile("(?i)(\\bRMDir\\b|\\bDelete\\b|Remove-Item|\\brm\\s|\\brmdir\\b|\\bdel\\s|\\brd\\s|SetFileAttributes|\\bmv\\s|Rename\\b)");
        Pattern golden = Pattern.compile("(?i)([\\\\/\"'$]\\.?lamerhav([\\\\/\"']|\\s|$))|(\\.dsviz[\"'\\s]*$)|(\\$DSV[\"'\\s]*$)|(\\.dsviz\\\\?\"\\s*$)");
        List<File> files = new ArrayList<File>();
        collect(dir, files);
        for (File f : files) {
            String[] lines = read(f).split("\\r?\\n");
            for (int i = 0; i < lines.length; i++) {
                String l = lines[i].trim();
                if (l.startsWith(";") || l.startsWith("#") || l.startsWith("REM ") || l.startsWith("::")) continue;
                Matcher d = del.matcher(l);
                if (!d.find()) continue;
                String rest = l.substring(d.start());
                if (golden.matcher(rest).find() && !rest.contains("il.lamerhav")) out.add(f.getName() + ":" + (i + 1) + ": " + l);
            }
        }
        return out;
    }

    static void collect(File dir, List<File> out) {
        File[] fs = dir.listFiles();
        if (fs == null) return;
        for (File f : fs) {
            if (f.isDirectory()) collect(f, out);
            else if (f.getName().matches(".*\\.(nsi|nsh|ps1|sh|bat|cmd|py)$")) out.add(f);
        }
    }
}
