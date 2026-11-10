package il.lamerhav.dsviz;

import java.awt.Window;
import java.io.File;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;

/**
 * עמדת הניהול של המורה (AccessGate.TeacherPanel — אותו חלון ב-Eclipse, ב-IntelliJ וב-Visual Studio) והתנתקות, מול השרת המקומי
 * והאמולטור (ראו AccountIt): מורה נכנס במסך הכניסה → "עמדת ניהול" פעילה → החלון מציג את השם, המייל, הקוד (כמו בשרת) ואת
 * העמדות → עמדות מתחברות, ההנהלה משחררת אחת ומשנה את המכסה — החלון ו-/v1/me (מה שהדף /account מציג) מתעדכנים →
 * "התנתקות" (שאלה, ביטול בשרת, מחיקה מהמחשב, הכפתור נכבה, מסך הכניסה חוזר) → מורה אחר באותו מחשב — בלי הקוד הקודם →
 * מורה שהושבת → תלמיד מתנתק → עמדת מחשב בלי התנתקות → התנתקות בלי אינטרנט.
 *   java -cp <classes>:<lib> il.lamerhav.dsviz.TeacherPanelIt http://127.0.0.1:8787 127.0.0.1:9099 <work> <shots>
 */
public class TeacherPanelIt {
    static int pass, fail;
    static String admin;

    public static void main(String[] a) throws Exception {
        AccountIt.server = a[0];
        AccountIt.auth = a[1];
        File work = new File(a[2]);
        GateUi.shots = new File(a[3]);
        GateUi.shots.mkdirs();
        System.setProperty("dsviz.worker", a[0]);
        System.setProperty("dsviz.passKey", Json.str(Json.obj(Json.parse(AccountIt.get(a[0] + "/v1/access/key"))).get("spki")));
        admin = AccountIt.idToken("motiml77@gmail.com");
        Account.browser = AccountIt::fakeBrowser;
        final AtomicInteger changes = new AtomicInteger();
        AccessGate.onChange(changes::incrementAndGet);

        String t1 = "panel.t1." + System.nanoTime() + "@test.dev", t2 = "panel.t2." + System.nanoTime() + "@test.dev";
        AccountIt.api("POST", "/v1/admin/teacher", admin, "{\"create\":true,\"email\":\"" + t1 + "\",\"name\":\"רונית אברהם\",\"seats\":3}");
        AccountIt.api("POST", "/v1/admin/teacher", admin, "{\"create\":true,\"email\":\"" + t2 + "\",\"name\":\"יוסי מזרחי\",\"seats\":2}");
        String code1 = serverCode(t1), code2 = serverCode(t2);

        // ---- 1. לפני כניסה: אין "עמדת ניהול" ואין התנתקות
        AccountIt.use(work, "pc1", "teacher");
        check("לפני כניסה — לא מורה, אין התנתקות", !AccessGate.isTeacher() && !AccessGate.canSignOut());

        // ---- 2. מורה נכנס במסך הכניסה → הכפתור נדלק מיד (onChange)
        AccountIt.browserEmail = t1;
        final AtomicInteger opened = new AtomicInteger();
        int before = changes.get();
        SwingUtilities.invokeLater(() -> AccessGate.require(null, opened::incrementAndGet));
        Window w = GateUi.waitWindow();
        GateUi.click(w, "כניסה עם Google");
        GateUi.waitUntil(() -> opened.get() > 0, 20000);
        check("מורה נכנס — המאגר נפתח, מורה מחובר, אפשר להתנתק", opened.get() == 1 && AccessGate.isTeacher() && AccessGate.canSignOut());
        check("כניסה — הודעה למאזינים (הכפתור 'עמדת ניהול' ב-Eclipse מתעדכן מיד)", changes.get() > before);
        String flag = flag();
        check("סימון למורה לסרגל של Visual Studio (teacher.flag): המייל, בלי הכרטיס (" + flag + ")",
                flag.contains(t1) && !flag.contains(Json.str(Account.userState().get("token"))) && !flag.contains("u.") && !flag.contains("pass"));
        w.dispose();

        // ---- 3. עמדת הניהול: השם, המייל, הקוד (אותו קוד כמו בשרת), עמדות 0 מתוך 3
        claim(code1, "מעבדה 2 · עמדה 1");
        claim(code1, "מעבדה 2 · עמדה 2");
        JDialog p = openPanel();
        String text = GateUi.visibleText(p);
        check("החלון: השם והמייל של המורה", text.contains("רונית אברהם") && text.contains(t1));
        check("החלון: הקוד כמו בשרת (" + label(p, "code") + " / " + code1 + ")", label(p, "code").equals(code1));
        check("החלון: עמדות 2 מתוך 3 (" + label(p, "seats") + ")", label(p, "seats").contains("2 מתוך 3"));
        check("החלון: כפתורי עמדת ניהול בדפדפן, העתקה, התנתקות", GateUi.find(p, "פתח עמדת ניהול בדפדפן") != null && GateUi.find(p, "העתק קוד") != null
                && GateUi.find(p, "התנתקות") != null);
        GateUi.shot(p, "panel-1-teacher");
        Map<String, Object> me = me(t1);
        check("/account (‏/v1/me): אותו קוד, 2 מתוך 3, שני המחשבים", code1.equals(Json.str(me.get("code"))) && num(me, "used") == 2 && num(me, "seats") == 3
                && Json.arr(me.get("stations")).size() == 2);

        // ---- 4. ההנהלה משחררת עמדה ומשנה את המכסה ל-5 — פתיחה מחדש של החלון מציגה את המספרים החדשים
        String sid = Json.str(Json.obj(Json.arr(me.get("stations")).get(0)).get("id"));
        AccountIt.api("POST", "/v1/admin/station/release", admin, "{\"id\":\"" + sid + "\"}");
        AccountIt.api("POST", "/v1/admin/teacher", admin, "{\"email\":\"" + t1 + "\",\"seats\":5}");
        p = openPanel();   // אותו חלון — מקדימה, ונטען מחדש מהשרת
        check("אחרי שחרור ושינוי מכסה: 1 מתוך 5 (" + label(p, "seats") + ")", label(p, "seats").contains("1 מתוך 5"));
        check("הקוד לא השתנה (קבוע)", label(p, "code").equals(code1) && serverCode(t1).equals(code1));
        me = me(t1);
        check("/v1/me: 1 מתוך 5, מחשב אחד", num(me, "used") == 1 && num(me, "seats") == 5 && Json.arr(me.get("stations")).size() == 1);
        GateUi.shot(p, "panel-2-after-release");

        // ---- 5. התנתקות מהחלון: שאלה בעברית → ביטול בשרת, מחיקה מהמחשב, החלון נסגר, הכפתור נכבה, מסך הכניסה חוזר
        String token = Json.str(Account.userState().get("token"));
        before = changes.get();
        GateUi.click(p, "התנתקות");
        Window ask = waitDialog("התנתקות", null);
        check("שאלה לפני התנתקות (עם המייל)", ask != null && GateUi.visibleText(ask).contains("להתנתק") && GateUi.visibleText(ask).contains(t1));
        GateUi.shot(ask, "panel-3-confirm");
        GateUi.click(ask, "ביטול");
        Thread.sleep(500);
        check("ביטול — עדיין מחובר", AccessGate.isTeacher());
        GateUi.click(p, "התנתקות");
        ask = waitDialog("התנתקות", null);
        GateUi.click(ask, "התנתקות");
        Window done = waitDialog("התנתקות", ask);
        check("הודעה אחרי ההתנתקות", done != null && GateUi.visibleText(done).contains("התנתקת"));
        GateUi.shot(done, "panel-4-signed-out");
        GateUi.click(done, "סגירה");
        check("אחרי התנתקות: לא מורה, הכניסה נמחקה מהמחשב", !AccessGate.isTeacher() && Account.userState() == null && Account.current() == null);
        check("אחרי התנתקות: עמדת הניהול נסגרה", !p.isDisplayable());
        check("אחרי התנתקות: הודעה למאזינים (הכפתור נכבה)", changes.get() > before);
        check("אחרי התנתקות: הכרטיס בוטל בשרת", "revoked".equals(check(token)));
        check("אחרי התנתקות: הסימון לסרגל של Visual Studio נמחק", flag().isEmpty());
        opened.set(0);
        SwingUtilities.invokeLater(() -> AccessGate.require(null, opened::incrementAndGet));
        w = waitDialog("כניסה למאגר התרגילים", null);
        check("אחרי התנתקות: פתיחת המאגר — מסך הכניסה", w != null && opened.get() == 0);
        GateUi.shot(w, "panel-5-gate-again");
        // אותו מחשב — מורה אחר: רואה את הקוד שלו, לא את הקוד הקודם
        AccountIt.browserEmail = t2;
        GateUi.click(w, "כניסה עם Google");
        GateUi.waitUntil(() -> opened.get() > 0, 20000);
        w.dispose();
        p = openPanel();
        check("מורה אחר באותו מחשב — הקוד שלו (" + label(p, "code") + "), לא של הקודם", label(p, "code").equals(code2) && !code2.equals(code1)
                && Account.teacherCode().equals(code2) && GateUi.visibleText(p).contains(t2) && !GateUi.visibleText(p).contains(t1));
        p.dispose();
        // הכפתור "עמדת ניהול" בסרגל של Visual Studio מריץ את BankVs במצב teacher — אותו חלון
        if (vsTeacher()) {
            p = (JDialog) waitDialog("עמדת ניהול", null);
            check("Visual Studio (BankVs teacher) — נפתחת עמדת הניהול עם הקוד", p != null && label(p, "code").equals(code2));
            if (p != null) { GateUi.shot(p, "panel-vs-teacher"); p.dispose(); }
        }

        // ---- 6. ההנהלה משביתה את המורה: פתיחת החלון → הסבר, בלי קוד, והכפתור נכבה
        AccountIt.api("POST", "/v1/admin/teacher", admin, "{\"email\":\"" + t2 + "\",\"active\":false}");
        before = changes.get();
        p = openPanel();
        check("מורה שהושבת — הסבר בחלון, בלי קוד", GateUi.visibleText(p).contains("לא פעילה") && label(p, "code").equals("—"));
        check("מורה שהושבת — כבר לא 'מורה' (הכפתור נכבה)", !AccessGate.isTeacher() && changes.get() > before);
        check("מורה שהושבת — הסימון לסרגל של Visual Studio נמחק", flag().isEmpty());
        GateUi.shot(p, "panel-6-disabled");
        p.dispose();
        AccountIt.api("POST", "/v1/admin/teacher", admin, "{\"email\":\"" + t2 + "\",\"active\":true}");

        // ---- 7. תלמיד: אפשר להתנתק (בלי עמדת ניהול)
        AccountIt.use(work, "pc2", "student");
        String student = "panel.s." + System.nanoTime() + "@test.dev";
        AccountIt.api("POST", "/v1/admin/student", admin, "{\"email\":\"" + student + "\",\"until\":\"2027-08-31\"}");
        AccountIt.browserEmail = student;
        Account.Result r = Account.signIn("student", null, null, 30000);
        check("תלמיד נכנס — לא מורה, אפשר להתנתק", r.ok() && !AccessGate.isTeacher() && AccessGate.canSignOut());
        check("תלמיד — אין סימון מורה (אין 'עמדת ניהול' בסרגל של Visual Studio)", flag().isEmpty());
        if (vsTeacher()) {   // סימון ישן (למשל מגרסה קודמת) — הכלי מסביר, לא פותח
            Window m = waitDialog("עמדת ניהול", null);
            check("Visual Studio (BankVs teacher) לתלמיד — הסבר 'רק למורה', בלי קוד", m != null && GateUi.visibleText(m).contains("רק למורה") && find(m, "code") == null);
            if (m != null) { GateUi.shot(m, "panel-vs-not-teacher"); GateUi.click(m, "סגירה"); }
        }
        AccessGate.confirmForTest = Boolean.TRUE;
        AccessGate.lastSignOut = null;
        final AtomicInteger after = new AtomicInteger();
        AccessGate.signOut(null, after::incrementAndGet);
        GateUi.waitUntil(() -> AccessGate.lastSignOut != null && after.get() > 0, 15000);
        check("תלמיד מתנתק — ok, נמחק, ה'אחרי' רץ (המאגר נסגר)", AccessGate.lastSignOut != null && AccessGate.lastSignOut.ok() && Account.userState() == null && after.get() == 1);

        // ---- 8. עמדת מחשב בכיתה: אין התנתקות (רק המורה / ההנהלה משחררים) — וגם התנתקות של מורה באותו מחשב לא נוגעת בה
        AccountIt.use(work, "pc3", "anyone");
        check("עמדה מתחברת", Account.claimStation(code1, "מעבדה 3 · עמדה 1").ok());
        check("עמדת מחשב — אין 'התנתקות'", Account.current().isStation() && !AccessGate.canSignOut());
        AccessGate.signOut(null, null);   // גם אם נקרא — לא עושה כלום
        Thread.sleep(800);
        check("עמדת מחשב — עדיין מחוברת", Account.current() != null && Account.current().isStation());
        AccountIt.browserEmail = t1;
        check("מורה נכנס באותו מחשב (מעל העמדה)", Account.signIn("teacher", null, null, 30000).ok() && AccessGate.isTeacher());
        AccessGate.lastSignOut = null;
        AccessGate.signOut(null, null);
        GateUi.waitUntil(() -> AccessGate.lastSignOut != null, 15000);
        check("המורה התנתק — העמדה של המחשב נשארת", !AccessGate.isTeacher() && Account.current() != null && Account.current().isStation()
                && "ok".equals(check(stationToken())));

        // ---- 9. התנתקות בלי אינטרנט: נמחק מהמחשב עם הסבר; בפעם הבאה שיש אינטרנט — מבוטל גם בשרת
        AccountIt.use(work, "pc4", "teacher");
        AccountIt.browserEmail = t1;
        Account.signIn("teacher", null, null, 30000);
        token = Json.str(Account.userState().get("token"));
        System.setProperty("dsviz.worker", "http://127.0.0.1:9");
        AccessGate.lastSignOut = null;
        AccessGate.signOut(null, null);
        GateUi.waitUntil(() -> AccessGate.lastSignOut != null, 30000);
        check("בלי אינטרנט — מתנתקים במחשב, עם הסבר (" + (AccessGate.lastSignOut == null ? "?" : AccessGate.lastSignOut.message) + ")",
                AccessGate.lastSignOut != null && "offline".equals(AccessGate.lastSignOut.status) && AccessGate.lastSignOut.message.contains("בפעם הבאה")
                && Account.userState() == null && !AccessGate.isTeacher());
        check("בלי אינטרנט — בשרת הכרטיס עדיין פעיל", "ok".equals(check(token)));
        System.setProperty("dsviz.worker", a[0]);
        SwingUtilities.invokeLater(() -> AccessGate.require(null, () -> { }));   // פתיחת המאגר עם אינטרנט — ההתנתקות נשלחת
        long end = System.currentTimeMillis() + 15000;
        String st = "";
        while (System.currentTimeMillis() < end && !"revoked".equals(st = check(token))) Thread.sleep(300);
        check("כשיש אינטרנט — הכרטיס בוטל גם בשרת (" + st + ")", "revoked".equals(st) && Account.pendingLogouts().isEmpty());
        for (Window x : Window.getWindows()) x.dispose();

        System.out.println("\n" + pass + " עברו, " + fail + " נכשלו");
        System.exit(fail == 0 ? 0 : 1);
    }

    // ------------------------------------------------------------------ עזרים

    static String serverCode(String email) throws Exception {
        return Json.str(Json.obj(Json.obj(Json.parse(AccountIt.api("GET", "/v1/admin/teacher?email=" + email, admin, null))).get("teacher")).get("code"));
    }

    static void claim(String code, String name) throws Exception {
        String mid = Long.toHexString(System.nanoTime()) + "abcdef0123456789";
        AccountIt.api("POST", "/v1/station/claim", null, "{\"code\":\"" + code + "\",\"name\":\"" + name + "\",\"machine\":\"" + mid + "\"}");
    }

    /** מה שהדף /account מציג למורה */
    static Map<String, Object> me(String email) throws Exception {
        return Json.obj(Json.obj(Json.parse(AccountIt.api("GET", "/v1/me", AccountIt.idToken(email), null))).get("teacher"));
    }

    static String check(String token) {
        if (token == null) return "";
        try {
            return Json.str(Json.obj(Json.parse(AccountIt.api("POST", "/v1/access/check", null, "{\"token\":\"" + token + "\"}"))).get("status"));
        } catch (Exception e) {
            return "error " + e.getMessage();
        }
    }

    static String stationToken() {
        Map<String, Object> s = Account.load(new File(System.getProperty("dsviz.machineDir"), "station.json"));
        return s == null ? "" : Json.str(s.get("token"));
    }

    /** BankVs.teacher() (vs-bank, אם במסלול המחלקות) — מה שהכפתור בסרגל של Visual Studio מריץ. false — לא במסלול */
    static boolean vsTeacher() throws Exception {
        final Class<?> c;
        try { c = Class.forName("il.lamerhav.dsviz.BankVs"); } catch (ClassNotFoundException e) { System.out.println("  ..   בלי vs-bank במסלול — דילוג על BankVs teacher"); return false; }
        new Thread(() -> {
            try {
                java.lang.reflect.Method m = c.getDeclaredMethod("teacher");
                m.setAccessible(true);
                m.invoke(null);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
        return true;
    }

    /** הסימון שסרגל הכלים של Visual Studio קורא ("" — אין) */
    static String flag() {
        try {
            File f = new File(System.getProperty("dsviz.home"), "teacher.flag");
            return f.isFile() ? new String(java.nio.file.Files.readAllBytes(f.toPath()), java.nio.charset.StandardCharsets.UTF_8) : "";
        } catch (Exception e) {
            return "";
        }
    }

    static int num(Map<String, Object> m, String k) {
        Object v = m.get(k);
        return v instanceof Number ? ((Number) v).intValue() : -1;
    }

    /** פותח את עמדת הניהול (כמו הכפתור) ומחכה שהנתונים ייטענו מהשרת */
    static JDialog openPanel() throws Exception {
        Window old = waitDialogNow("עמדת ניהול");
        final int was = old == null ? 0 : ((AccessGate.TeacherPanel) old).reloads;
        AccessGate.showTeacherPanel(null);
        JDialog p = (JDialog) waitDialog("עמדת ניהול", null);
        if (p == null) throw new IllegalStateException("עמדת הניהול לא נפתחה");
        final AccessGate.TeacherPanel tp = (AccessGate.TeacherPanel) p;
        GateUi.waitUntil(() -> tp.reloads > (tp == old ? was : 0), 15000);
        Thread.sleep(300);
        return p;
    }

    static Window waitDialogNow(String title) {
        for (Window x : Window.getWindows()) if (x.isShowing() && x instanceof java.awt.Dialog && title.equals(((java.awt.Dialog) x).getTitle())) return x;
        return null;
    }

    static String label(Window w, String id) {
        JLabel l = find(w, id);
        return l == null ? "" : l.getText().replace("‏", "").trim();
    }

    static JLabel find(java.awt.Component c, String id) {
        if (c instanceof JLabel && id.equals(((JLabel) c).getClientProperty("dsviz.id"))) return (JLabel) c;
        if (c instanceof java.awt.Container) for (java.awt.Component k : ((java.awt.Container) c).getComponents()) {
            JLabel r = find(k, id);
            if (r != null) return r;
        }
        return null;
    }

    /** חלון פתוח עם הכותרת (לא except) */
    static Window waitDialog(String title, Window except) throws InterruptedException {
        long end = System.currentTimeMillis() + 15000;
        while (System.currentTimeMillis() < end) {
            for (Window x : Window.getWindows())
                if (x != except && x.isShowing() && x instanceof java.awt.Dialog && title.equals(((java.awt.Dialog) x).getTitle())) { Thread.sleep(300); return x; }
            Thread.sleep(100);
        }
        return null;
    }

    static void check(String name, boolean ok) {
        if (ok) { pass++; System.out.println("  ok   " + name); } else { fail++; System.out.println("  FAIL " + name); }
    }
}
