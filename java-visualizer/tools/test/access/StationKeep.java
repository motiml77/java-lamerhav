package il.lamerhav.dsviz;

import java.io.File;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.KeyPairGenerator;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.sun.net.httpserver.HttpServer;

/**
 * "עמדה שמשתחררת לבד": כל המסלולים שבהם מחשב מחובר ביקש שוב קוד, מול שרת מדומה (בלי אמולטור ובלי אינטרנט).
 *   1. מזהה המחשב: נשמר, ונשאר אותו דבר כשאי אפשר לקרוא את מזהה מערכת ההפעלה (reg.exe חסום לתלמידים), כשהקובץ השמור נוצר
 *      בגרסה קודמת עם מזהה חלופי (Java של 32 ביט), וקובץ שהועתק ממחשב אחר עדיין נדחה.
 *   2. קובץ העמדה: נבחר הקובץ שנפתח והאישור בו תקף (לא "הקובץ המשותף אם הוא קיים"), וחיבור חדש נשמר במקום שנקרא בחזרה.
 *   3. תשובת השרת: עמדה ששוחררה נמחקת; "לא תקף" משרת ישן (חיבור חוזר מאותו מחשב החליף את הכרטיס) — לא מוחק את העמדה.
 *   4. מה נשלח לשרת בחיבור: המזהה הקבוע (stable) והסיבה (why), ליומן של ההנהלה.
 *   java -cp <classes>:<lib> il.lamerhav.dsviz.StationKeep <work-dir>
 */
public class StationKeep {
    static final Map<String, String> bodies = new ConcurrentHashMap<String, String>();

    public static void main(String[] a) throws Exception {
        File work = new File(a[0]);
        SignInEdge.keys = KeyPairGenerator.getInstance("RSA").generateKeyPair();
        HttpServer mock = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        mock.createContext("/", ex -> {
            String path = ex.getRequestURI().getPath();
            bodies.put(path, new String(SignInEdge.readAll(ex.getRequestBody()), StandardCharsets.UTF_8));
            String[] ans = SignInEdge.answers.get(path);
            if (ans == null) ans = new String[] { "404", "{\"ok\":false,\"code\":\"not_found\",\"error\":\"?\"}", "application/json" };
            byte[] out = ans[1].getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().set("Content-Type", ans[2]);
            ex.sendResponseHeaders(Integer.parseInt(ans[0]), out.length);
            try (OutputStream o = ex.getResponseBody()) { o.write(out); }
            ex.close();
        });
        mock.start();
        System.setProperty("dsviz.passKey", Base64.getEncoder().encodeToString(SignInEdge.keys.getPublic().getEncoded()));
        System.setProperty("dsviz.worker", "http://127.0.0.1:" + mock.getAddress().getPort());

        // ================================================================ 1. מזהה המחשב
        String live = new String(Files.readAllBytes(new File("/etc/machine-id").toPath()), StandardCharsets.UTF_8).trim();
        System.setProperty("dsviz.hostname", "LAB-01");
        dirs(work, "id1");
        String m1 = Account.machine();
        String[] lines = idFile();
        check("מחשב חדש: המזהה נשמר עם מזהה מערכת ההפעלה (שורה 3: g:…)", lines.length == 3 && lines[1].equals("LAB-01") && lines[2].equals("g:" + live));
        Account.resetForTest();
        check("הפעלה שנייה — אותו מזהה", Account.machine().equals(m1));
        // reg.exe חסום (תלמיד עם הגבלות): פעם נלקח מזהה חלופי מקובץ — מזהה אחר, והעמדה לא נפתחה
        station(new File(System.getProperty("dsviz.machineDir"), "station.json"), "s.station1234.secretsecretsecretsecretST", 100);
        System.setProperty("dsviz.osid.off", "1");
        Account.resetForTest();
        check("אי אפשר לקרוא את מזהה מערכת ההפעלה — אותו מזהה (מהקובץ השמור)", Account.machine().equals(m1));
        check("  והעמדה נשארת מחוברת", Account.current() != null && Account.current().isStation());
        check("  ולא נוצר קובץ מזהה חלופי", !new File(System.getProperty("dsviz.machineDir"), "machine-id").exists());
        System.clearProperty("dsviz.osid.off");

        // גרסה קודמת, שבה מזהה מערכת ההפעלה לא נקרא (Java של 32 ביט): הקבצים הוצפנו עם מזהה חלופי מקובץ
        dirs(work, "id2");
        System.setProperty("dsviz.machine", "FALLBACK-0001");          // כמו הגרסה הישנה: המזהה החלופי הוא מזהה המערכת
        String old = Account.machine();
        station(new File(System.getProperty("dsviz.machineDir"), "station.json"), "s.station1234.secretsecretsecretsecretST", 100);
        check("(הכנה) קובץ מזהה בפורמט הישן, שתי שורות", idFile().length == 2);
        System.clearProperty("dsviz.machine");
        Files.write(new File(System.getProperty("dsviz.machineDir"), "machine-id").toPath(), "FALLBACK-0001".getBytes(StandardCharsets.UTF_8));
        Account.resetForTest();
        check("עדכון גרסה על מחשב עם מזהה חלופי: נבחר המזהה שפותח את הקובץ השמור — אותו מזהה", Account.machine().equals(old));
        check("  העמדה נשארת מחוברת", Account.current() != null && Account.current().isStation());
        check("  והמזהה נשמר כחלופי (f:…), לכל התוכנות", idFile().length == 3 && idFile()[2].equals("f:FALLBACK-0001"));
        Files.delete(new File(System.getProperty("dsviz.machineDir"), "machine-id").toPath());
        Account.resetForTest();
        check("  גם אחרי שהקובץ החלופי נמחק — אותו מזהה", Account.machine().equals(old) && Account.current() != null);

        // גרסה קודמת רגילה (מזהה אמיתי): עדכון הגרסה לא משנה את המזהה
        dirs(work, "id3");
        System.setProperty("dsviz.machine", live);
        String real = Account.machine();
        station(new File(System.getProperty("dsviz.machineDir"), "station.json"), "s.station1234.secretsecretsecretsecretST", 100);
        System.clearProperty("dsviz.machine");
        Account.resetForTest();
        check("עדכון גרסה על מחשב רגיל — אותו מזהה, והעמדה מחוברת", Account.machine().equals(real) && Account.current() != null && Account.current().isStation());
        check("  והקובץ שודרג לשלוש שורות", idFile().length == 3 && idFile()[2].equals("g:" + live));

        // קובץ מזהה שהועתק ממחשב אחר
        String install = idFile()[0];
        dirs(work, "id4");
        new File(System.getProperty("dsviz.machineDir")).mkdirs();
        Files.write(new File(System.getProperty("dsviz.machineDir"), "i.dat").toPath(), (install + "\nLAB-01\ng:some-other-windows-guid\n").getBytes(StandardCharsets.UTF_8));
        Account.resetForTest();
        String copied = Account.machine();
        check("קובץ מזהה ממחשב אחר (מזהה מערכת אחר, אותו שם מחשב) — נדחה, נוצר מזהה חדש", !idFile()[0].equals(install) && !copied.equals(real));
        Files.write(new File(System.getProperty("dsviz.machineDir"), "i.dat").toPath(), (install + "\nLAB-99\ng:" + live + "\n").getBytes(StandardCharsets.UTF_8));
        new File(System.getProperty("dsviz.home"), "i.dat").delete();
        Account.resetForTest();
        copied = Account.machine();
        check("קובץ מזהה עם שם מחשב אחר (תמונה משוכפלת) — נדחה", !idFile()[0].equals(install) && !copied.equals(real));

        // המזהה הקבוע (stable): לפי המחשב עצמו, לא לפי הקובץ
        dirs(work, "id5");
        String s5 = Account.stableMachine(), mm5 = Account.machine();
        dirs(work, "id6");
        check("מזהה קבוע: אותו מחשב עם קבצים חדשים (שוחזר) — אותו stable, מזהה רגיל אחר", s5.length() == 32 && Account.stableMachine().equals(s5) && !Account.machine().equals(mm5));
        System.setProperty("dsviz.hostname", "LAB-02");
        Account.resetForTest();
        check("  מחשב אחר מאותה תמונה (שם אחר) — stable אחר", !Account.stableMachine().equals(s5));
        System.setProperty("dsviz.hostname", "LAB-01");

        // ================================================================ 2. קובץ העמדה
        use(work, "f1");
        File mf = new File(System.getProperty("dsviz.machineDir"), "station.json"), uf = new File(System.getProperty("dsviz.home"), "station.json");
        mf.getParentFile().mkdirs();
        Files.write(mf.toPath(), "{\"v\":1,\"data\":\"AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA\"}".getBytes(StandardCharsets.UTF_8));
        station(uf, "s.station1234.secretsecretsecretsecretUU", 100);
        check("הקובץ המשותף לא נפתח, ואצל המשתמש יש קובץ תקין — העמדה מחוברת (פעם: ביקש קוד)", Account.current() != null && Account.current().isStation());
        check("  נשלח הכרטיס של הקובץ התקין", "s.station1234.secretsecretsecretsecretUU".equals(Account.headers().get("X-DSViz-Access")));
        check("  ולא נדרש חיבור מחדש (why ריק)", Account.stationWhy().isEmpty());

        use(work, "f2");
        mf = new File(System.getProperty("dsviz.machineDir"), "station.json");
        uf = new File(System.getProperty("dsviz.home"), "station.json");
        station(mf, "s.station1234.secretsecretsecretsecretOLD", 100);
        station(uf, "s.station1234.secretsecretsecretsecretNEW", 900);
        check("שני קבצים תקינים — נבחר זה שנבדק אחרון", "s.station1234.secretsecretsecretsecretNEW".equals(Account.headers().get("X-DSViz-Access")));

        use(work, "f3");
        check("אין קובץ — why = none", Account.stationWhy().equals("none"));
        mf = new File(System.getProperty("dsviz.machineDir"), "station.json");
        mf.getParentFile().mkdirs();
        Files.write(mf.toPath(), "זבל".getBytes(StandardCharsets.UTF_8));
        check("קובץ שלא נפתח — why = unreadable", Account.stationWhy().equals("unreadable"));
        SignInEdge.answers.put("/v1/station/claim", SignInEdge.json("{\"ok\":true,\"status\":\"ok\",\"token\":\"s.station1234.secretsecretsecretsecretCL\",\"pass\":\""
                + SignInEdge.makePass("station", "", 0) + "\",\"profile\":{\"role\":\"station\",\"email\":\"\",\"name\":\"עמדה 1\"}}"));
        Account.Result r = Account.claimStation("K7QM", "עמדה 1");
        Map<String, Object> sent = Json.obj(Json.parse(bodies.get("/v1/station/claim")));
        check("חיבור: נשלחים המזהה, המזהה הקבוע והסיבה (" + sent.get("why") + ")", Account.machine().equals(sent.get("machine"))
                && Account.stableMachine().equals(sent.get("stable")) && Json.str(sent.get("stable")).length() == 32 && "unreadable".equals(sent.get("why")));
        check("חיבור על קובץ שלא נפתח: הקובץ הוחלף, והעמדה מחוברת (" + r.status + ")", r.ok() && Account.current() != null
                && "s.station1234.secretsecretsecretsecretCL".equals(Json.str(Account.load(mf).get("token"))));
        check("  אחרי חיבור — why ריק", Account.stationWhy().isEmpty());

        // ================================================================ 3. תשובת השרת בבדיקה
        SignInEdge.answers.put("/v1/access/check", SignInEdge.json("{\"ok\":true,\"status\":\"invalid\",\"profile\":null}"));
        Account.refresh();
        check("שרת ישן עונה \"לא תקף\" בלי סיבה (חיבור חוזר מאותו מחשב החליף את הכרטיס) — העמדה נשארת", Account.current() != null && Account.current().isStation());
        SignInEdge.answers.put("/v1/access/check", SignInEdge.json("{\"ok\":false,\"code\":\"internal\",\"error\":\"תקלה\"}"));
        Account.refresh();
        check("תקלה בשרת — העמדה נשארת", Account.current() != null);
        SignInEdge.answers.put("/v1/access/check", SignInEdge.json("{\"ok\":true,\"status\":\"invalid\",\"why\":\"token\",\"profile\":null}"));
        Account.refresh();
        check("שרת חדש: \"לא תקף\" עם סיבה — העמדה נמחקת, והסיבה נשמרת לחיבור הבא", Account.current() == null && Account.stationWhy().equals("server-invalid"));
        r = Account.claimStation("K7QM", "עמדה 1");
        check("  חיבור מחדש שולח את הסיבה", r.ok() && "server-invalid".equals(Json.obj(Json.parse(bodies.get("/v1/station/claim"))).get("why")));
        SignInEdge.answers.put("/v1/access/check", SignInEdge.json("{\"ok\":true,\"status\":\"revoked\",\"why\":\"released\",\"profile\":null}"));
        Account.refresh();
        check("המורה שחרר את העמדה — נמחקת (" + Account.stationWhy() + ")", Account.current() == null && Account.stationWhy().equals("server-revoked"));
        SignInEdge.answers.put("/v1/access/check", SignInEdge.json("{\"ok\":true,\"status\":\"revoked\",\"profile\":null}"));
        station(mf, "s.station1234.secretsecretsecretsecretZZ", 100);
        Account.refresh();
        check("שחרור משרת ישן (revoked בלי סיבה) — גם נמחקת", Account.current() == null);

        // ================================================================ 4. עמדה = רק מאגר השאלות (בלי שליחת תרגיל ל-AI)
        use(work, "ai1");
        check("בלי כניסה בכלל — לא 'עמדה בלבד' (השרת מחליט לפי המתג)", !Account.stationOnly());
        station(new File(System.getProperty("dsviz.machineDir"), "station.json"), "s.station1234.secretsecretsecretsecretAI", 100);
        check("עמדת מחשב בלי משתמש — עמדה בלבד: שליחת תרגיל נחסמת מיד, עם הסבר", Account.stationOnly()
                && Account.STATION_AI_MESSAGE.contains("מאגר השאלות") && Account.STATION_AI_MESSAGE.contains("Google"));
        SignInEdge.signedIn("teacher", "t@x.org", "u.sid12.secretsecretsecretsecret12");
        check("מורה מחובר עם Google באותו מחשב — לא עמדה בלבד (שולח לפי המנה שלו)", !Account.stationOnly());
        SignInEdge.signedIn("student", "s@x.org", "u.sid13.secretsecretsecretsecret13");
        check("תלמיד מחובר עם Google באותו מחשב — לא עמדה בלבד", !Account.stationOnly());

        mock.stop(0);
        System.out.println(SignInEdge.fail == 0 ? "StationKeep: כל " + SignInEdge.pass + " הבדיקות עברו" : "StationKeep: " + SignInEdge.fail + " נכשלו");
        System.exit(SignInEdge.fail == 0 ? 0 : 1);
    }

    static void check(String name, boolean ok) { SignInEdge.check(name, ok); }

    /** תיקיות חדשות, בלי מזהה כפוי: מזהה מערכת ההפעלה האמיתי של המחשב שמריץ את הבדיקה */
    static void dirs(File work, String pc) {
        System.clearProperty("dsviz.machine");
        System.setProperty("dsviz.machineDir", new File(work, pc + "/machine").getPath());
        System.setProperty("dsviz.home", new File(work, pc + "/user").getPath());
        Account.resetForTest();
    }

    static void use(File work, String pc) {
        dirs(work, pc);
        System.setProperty("dsviz.machine", pc + "-id");
        Account.resetForTest();
    }

    static String[] idFile() throws Exception {
        return new String(Files.readAllBytes(new File(System.getProperty("dsviz.machineDir"), "i.dat").toPath()), StandardCharsets.UTF_8).split("\n");
    }

    static void station(File f, String token, double checkedAt) throws Exception {
        Map<String, Object> d = new LinkedHashMap<String, Object>();
        d.put("token", token);
        d.put("pass", SignInEdge.makePass("station", "", 0));
        d.put("status", "ok");
        d.put("checkedAt", checkedAt);
        Account.save(f, d);
    }
}
