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
        String[] st2 = claimTok(code1, "מעבדה 2 · עמדה 2");
        JDialog p = openPanel();
        String text = GateUi.visibleText(p);
        check("החלון: השם והמייל של המורה", text.contains("רונית אברהם") && text.contains(t1));
        check("החלון: הקוד כמו בשרת (" + label(p, "code") + " / " + code1 + ")", label(p, "code").equals(code1));
        check("החלון: עמדות 2 מתוך 3 (" + label(p, "seats") + ")", label(p, "seats").contains("2 מתוך 3"));
        check("החלון: כפתורי עמדת ניהול בדפדפן, העתקה, התנתקות", GateUi.find(p, "פתח עמדת ניהול בדפדפן") != null && GateUi.find(p, "העתק קוד") != null
                && GateUi.find(p, "התנתקות") != null);
        check("החלון: 'אין הגשות חדשות' (" + label(p, "fresh") + ")", label(p, "fresh").startsWith("אין הגשות חדשות") && find(p, "fresh").isVisible());
        check("החלון: הקוד הוא גם לתלמידים (הצטרפות עם Google) וגם למחשבי המעבדה", text.contains("לתלמידים ולמחשבי המעבדה") && GateUi.visibleText(p).contains("מצטרף אליך עם הקוד הזה"));
        GateUi.shot(p, "panel-1-teacher");
        // הגשה ממחשב במעבדה → פתיחה מחדש של החלון: "הגשה חדשה אחת מחכה לך"
        java.net.HttpURLConnection sc = (java.net.HttpURLConnection) new java.net.URL(AccountIt.server + "/v1/submit").openConnection();
        sc.setRequestMethod("POST");
        sc.setDoOutput(true);
        sc.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        sc.setRequestProperty("X-DSViz-Access", st2[0]);
        sc.setRequestProperty("X-DSViz-Machine", st2[1]);
        sc.getOutputStream().write("{\"who\":\"נועם\",\"exercise\":{\"source\":\"own\",\"title\":\"תרגיל\"},\"files\":[{\"path\":\"A.java\",\"text\":\"class A {}\"}]}".getBytes("UTF-8"));
        int submitCode = sc.getResponseCode();
        check("הגשה מעמדה נשלחה (" + submitCode + ")", submitCode == 200);
        p = openPanel();
        check("החלון: 'הגשה חדשה אחת מחכה לך' (" + label(p, "fresh") + ")", label(p, "fresh").equals("הגשה חדשה אחת מחכה לך (0 מתלמידים · 1 ממחשבי המעבדה)"));
        GateUi.shot(p, "panel-1b-new-submission");
        // מחשבי המעבדה בחלון: שני המחשבים, עם הכינוי ומתי פעלו לאחרונה, וכפתור שחרור לכל אחד
        check("החלון: הלשונית 'מחשבי המעבדה (2)' (" + tab(p, "tab-stations") + ")", tab(p, "tab-stations").equals("מחשבי המעבדה (2)"));
        String stText = GateUi.visibleText(p);
        check("החלון: הכינויים של המחשבים ומתי פעלו", stText.contains("מעבדה 2 · עמדה 2") && stText.contains("פעל לאחרונה"));
        check("החלון: כפתור 'שחרור' לכל מחשב", countIds(p, "release-station:") == 2);
        check("החלון: הלשונית 'תלמידים (0)' — עוד אין", tab(p, "tab-students").equals("תלמידים (0)"));
        check("החלון: נפתח על מחשבי המעבדה (יש מחשבים), עם כותרת עמודות", !((AccessGate.TeacherPanel) p).roster.onStudents() && stText.contains("כינוי המחשב"));
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

        // ---- 4ב. תלמיד מצטרף → מופיע בחלון; המורה מסיר אותו, ומשחרר את המחשב שנשאר — מהחלון עצמו, עם שאלה לפני
        String kid = "panel.kid" + System.nanoTime() + "@test.dev";
        AccountIt.api("POST", "/v1/admin/student/assign", admin, "{\"email\":\"" + kid + "\",\"teacher\":\"" + t1 + "\"}");
        p = openPanel();
        clickId(p, "tab-students");
        check("החלון: התלמיד המשויך ברשימה (" + tab(p, "tab-students") + ")", tab(p, "tab-students").equals("תלמידים (1)") && GateUi.visibleText(p).contains(kid)
                && GateUi.visibleText(p).contains("עוד לא הגיש"));
        GateUi.shot(p, "panel-2b-computers-and-students");
        clickId(p, "release-student:" + kid);
        Window askKid = waitDialog("להסיר את התלמיד?", null);
        check("שאלה לפני הסרת תלמיד", askKid != null && GateUi.visibleText(askKid).contains("ההגשות שלו נשארות אצלך"));
        GateUi.click(askKid, "הסרה");
        waitTab(p, "tab-students", "תלמידים (0)");
        check("אחרי ההסרה: אין תלמידים משויכים, עם אישור מתחת לרשימה (" + label(p, "roster-note") + ")", tab(p, "tab-students").equals("תלמידים (0)")
                && GateUi.visibleText(p).contains("עוד אין תלמידים משויכים") && label(p, "roster-note").contains("התלמיד הוסר"));
        clickId(p, "tab-stations");
        String left = Json.str(Json.obj(Json.arr(me(t1).get("stations")).get(0)).get("id"));
        clickId(p, "release-station:" + left);
        Window askSt = waitDialog("לשחרר את המחשב?", null);
        check("שאלה לפני שחרור מחשב (עם הכינוי)", askSt != null && GateUi.visibleText(askSt).contains("ההיתר חוזר אליך"));
        GateUi.shot(askSt, "panel-2c-release-confirm");
        GateUi.click(askSt, "שחרור");
        waitTab(p, "tab-stations", "מחשבי המעבדה (0)");
        check("אחרי השחרור מהחלון: אין מחשבים, 0 מתוך 5 (" + label(p, "seats") + ")", tab(p, "tab-stations").equals("מחשבי המעבדה (0)") && label(p, "seats").contains("0 מתוך 5"));
        check("/v1/me: בלי מחשבים", Json.arr(me(t1).get("stations")).isEmpty());
        GateUi.shot(p, "panel-2d-after-teacher-release");
        manyRows((AccessGate.TeacherPanel) p);

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

    /** כמו claim, ומחזיר את הכרטיס של העמדה ואת מזהה המחשב שלה (להגשה מהעמדה) */
    static String[] claimTok(String code, String name) throws Exception {
        String mid = Long.toHexString(System.nanoTime()) + "abcdef0123456789";
        String r = AccountIt.api("POST", "/v1/station/claim", null, "{\"code\":\"" + code + "\",\"name\":\"" + name + "\",\"machine\":\"" + mid + "\"}");
        return new String[] { Json.str(Json.obj(Json.parse(r)).get("token")), mid };
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

    /** כמה רכיבים עם dsviz.id שמתחיל ב-prefix */
    static int countIds(java.awt.Component c, String prefix) {
        int n = c instanceof javax.swing.JComponent && String.valueOf(((javax.swing.JComponent) c).getClientProperty("dsviz.id")).startsWith(prefix) ? 1 : 0;
        if (c instanceof java.awt.Container) for (java.awt.Component k : ((java.awt.Container) c).getComponents()) n += countIds(k, prefix);
        return n;
    }

    static javax.swing.AbstractButton byId(java.awt.Component c, String id) {
        if (c instanceof javax.swing.AbstractButton && id.equals(((javax.swing.JComponent) c).getClientProperty("dsviz.id"))) return (javax.swing.AbstractButton) c;
        if (c instanceof java.awt.Container) for (java.awt.Component k : ((java.awt.Container) c).getComponents()) {
            javax.swing.AbstractButton r = byId(k, id);
            if (r != null) return r;
        }
        return null;
    }

    /** לחיצה (בחוט של Swing, בלי לחכות לחלון השאלה שנפתח) */
    static void clickId(Window w, String id) throws Exception {
        final javax.swing.AbstractButton b = byId(w, id);
        if (b == null) { check("כפתור " + id + " קיים", false); return; }
        SwingUtilities.invokeLater(b::doClick);
        Thread.sleep(300);
    }

    /**
     * כיתה אמיתית: 30 תלמידים (3 כיתות ועוד כמה בלי כיתה) ו-12 מחשבי מעבדה — נתונים מדומים לתוך אותו חלון. הרשימה נגללת
     * (החלון לא גדל מעבר למסך), התלמידים מקובצים לפי כיתה, והחיפוש והסינון מצמצמים אותה.
     */
    static void manyRows(final AccessGate.TeacherPanel p) throws Exception {
        final String[] first = { "נועה", "איתי", "יעל", "דניאל", "שירה", "עומר", "תמר", "יונתן", "מאיה", "אורי", "רוני", "עידו", "הילה", "נדב", "ליה", "אלון" };
        final String[] last = { "כהן", "לוי", "מזרחי", "פרץ", "ביטון", "אברהם", "פרידמן", "אזולאי", "דהן", "שפירא", "חדד" };
        final String[] cls = { "י'1", "י'1", "י'1", "י'2", "י'2", "יא'3", "" };
        final long[] seen = { 3, 40, 95, 180, 60 * 26, 60 * 24 * 3, 60 * 24 * 5, 60 * 24 * 12, 60 * 24 * 45, -1 };
        final java.util.List<Object> st = new java.util.ArrayList<Object>(), su = new java.util.ArrayList<Object>();
        int old = 0, cohen = 0, y2 = 0;
        for (int i = 12; i >= 1; i--) {   // בסדר הפוך — הרשימה ממיינת לפי הכינוי ("עמדה 2" לפני "עמדה 10")
            long s = seen[(i * 7) % seen.length];
            if (s > 60 * 24 * 30) old++;
            st.add(row("id", "st" + i, "name", "מעבדה 2 · עמדה " + i, "lastSeen", s < 0 ? "" : java.time.Instant.ofEpochMilli(System.currentTimeMillis() - s * 60000L).toString()));
        }
        for (int i = 0; i < 30; i++) {
            String n = first[i % first.length] + " " + last[(i * 3) % last.length], c = cls[i % cls.length];
            if (n.contains("כהן")) cohen++;
            if (c.equals("י'2")) y2++;
            su.add(row("email", "kid" + (i + 1) + (i % 5 == 0 ? ".with.a.very.long.address" : "") + "@school.example.org", "name", n, "className", c,
                    "lastSubmitAt", i % 4 == 3 ? "" : java.time.Instant.ofEpochMilli(System.currentTimeMillis() - (30L + i * 400L) * 60000L).toString()));
        }
        final Map<String, Object> body = row("releasesLeft", 3, "stations", st, "students", su);
        SwingUtilities.invokeAndWait(() -> p.showLists(new Account.Result("ok", "", body), true));
        Thread.sleep(300);
        clickId(p, "tab-stations");
        check("הרבה שורות: הלשוניות עם המספרים (" + tab(p, "tab-stations") + " / " + tab(p, "tab-students") + ")",
                tab(p, "tab-stations").equals("מחשבי המעבדה (12)") && tab(p, "tab-students").equals("תלמידים (30)"));
        check("הרבה שורות: 12 מחשבים ברשימה, עם כפתור שחרור לכל אחד", countIds(p, "station:") == 12 && countIds(p, "release-station:") == 12);
        java.awt.Component r1 = comp(p, "station:st1"), r2 = comp(p, "station:st2"), r10 = comp(p, "station:st10");
        check("הרבה שורות: המחשבים ממוינים לפי הכינוי — עמדה 1, 2, … 10 (לא 1, 10, 2)", r1.getY() < r2.getY() && r2.getY() < r10.getY());
        java.awt.Rectangle scr = java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        javax.swing.JScrollPane sp = (javax.swing.JScrollPane) SwingUtilities.getAncestorOfClass(javax.swing.JScrollPane.class, r1);
        int rows = sp.getViewport().getHeight() / Roster.ROW;
        check("הרבה שורות: החלון בתוך המסך (" + p.getHeight() + " מתוך " + scr.height + "), ולפחות 8 שורות גלויות (" + rows + ")", p.getHeight() <= scr.height && rows >= 8);
        check("הרבה שורות: שורה אחת לכל מחשב, כולן ברוחב הרשימה (" + r1.getWidth() + " / " + sp.getViewport().getWidth() + ")",
                r1.getHeight() == Roster.ROW && r1.getWidth() == sp.getViewport().getWidth());
        GateUi.shot(p, "panel-2e-many-computers");
        SwingUtilities.invokeAndWait(() -> p.roster.filterBy("old"));
        check("מחשבים: סינון 'לא פעלו מעל 30 יום' (" + p.roster.shownRows + " / " + old + ", " + label(p, "roster-count") + ")",
                old > 0 && p.roster.shownRows == old && countIds(p, "station:") == old && label(p, "roster-count").equals("מוצגים " + old + " מתוך 12")
                        && GateUi.visibleText(p).contains("אולי הוחלף"));
        SwingUtilities.invokeAndWait(() -> p.roster.filterBy(""));

        clickId(p, "tab-students");
        check("הרבה שורות: 30 תלמידים ברשימה, מקובצים ל-4 כותרות (3 כיתות ו'בלי כיתה')", countIds(p, "student:") == 30 && countIds(p, "group:") == 4
                && p.roster.shownRows == 30);
        java.awt.Component g1 = comp(p, "group:י'1"), g2 = comp(p, "group:י'2"), g3 = comp(p, "group:יא'3"), g0 = comp(p, "group:בלי כיתה");
        check("הרבה שורות: הכיתות לפי הסדר, 'בלי כיתה' בסוף", g1 != null && g2 != null && g3 != null && g0 != null && g1.getY() < g2.getY() && g2.getY() < g3.getY()
                && g3.getY() < g0.getY());
        check("הרבה שורות: מספר התלמידים בכותרת של כל כיתה", GateUi.visibleText(p).contains(y2 + " תלמידים"));
        check("הרבה שורות: הרשימה נגללת (ארוכה מהחלון), והחלון לא גדל", sp.getVerticalScrollBar().isVisible() && p.getHeight() <= scr.height);
        GateUi.shot(p, "panel-2f-many-students");
        final String y2key = "c:י'2";
        check("תלמידים: בתפריט הסינון כל הכיתות (" + p.roster.filterKeys() + ")", p.roster.filterKeys().equals(java.util.Arrays.asList("", "c:י'1", y2key, "c:יא'3", "c:")));
        SwingUtilities.invokeAndWait(() -> p.roster.filterBy(y2key));
        check("תלמידים: סינון לפי כיתה (" + label(p, "roster-count") + ")", p.roster.shownRows == y2 && countIds(p, "student:") == y2 && countIds(p, "group:") == 1
                && label(p, "roster-count").equals("מוצגים " + y2 + " מתוך 30"));
        GateUi.shot(p, "panel-2g-one-class");
        SwingUtilities.invokeAndWait(() -> p.roster.filterBy(""));
        search(p, "כהן");
        check("תלמידים: חיפוש לפי שם (" + p.roster.shownRows + " / " + cohen + ")", cohen > 0 && cohen < 30 && p.roster.shownRows == cohen && countIds(p, "student:") == cohen);
        GateUi.shot(p, "panel-2h-search");
        search(p, "KID17@");
        check("תלמידים: חיפוש לפי מייל (בלי הבדל בין אותיות גדולות לקטנות)", p.roster.shownRows == 1 && comp(p, "student:kid17@school.example.org") != null);
        search(p, "יא'3");
        check("תלמידים: חיפוש לפי כיתה", p.roster.shownRows > 0 && countIds(p, "group:") == 1 && comp(p, "group:יא'3") != null);
        search(p, "אין כזה");
        check("תלמידים: חיפוש בלי תוצאות — הודעה, בלי שורות", p.roster.shownRows == 0 && countIds(p, "student:") == 0 && GateUi.visibleText(p).contains("לא נמצא \"אין כזה\""));
        search(p, "");
        check("תלמידים: ניקוי החיפוש — שוב 30", p.roster.shownRows == 30 && label(p, "roster-count").isEmpty());
        // הרשימה נשארת במקום שגללו אליו כשהנתונים מתרעננים (אחרי הסרת תלמיד מסוף הרשימה לא קופצים להתחלה)
        SwingUtilities.invokeAndWait(() -> sp.getVerticalScrollBar().setValue(400));
        SwingUtilities.invokeAndWait(() -> p.showLists(new Account.Result("ok", "", body), true));
        Thread.sleep(300);
        check("רענון הנתונים: הגלילה נשארת במקום (" + sp.getVerticalScrollBar().getValue() + ")", sp.getVerticalScrollBar().getValue() == 400);
        SwingUtilities.invokeAndWait(() -> p.showLists(null, true));
        check("בלי תשובה מהשרת: הודעה במקום הרשימות, בלי שורות (שרת שלא מחזיר רשימות בכלל — בלי האזור הזה)", Account.serverHas(false, "panel")
                ? countIds(p, "student:") == 0 && GateUi.visibleText(p).contains("לא נטענו") : !p.roster.isVisible());
        // בלי תלמידים משויכים: בגרסה עם מרכז הבקרה הלשונית "תלמידים (0)" מוצגת (עם ההסבר איך מצטרפים); בגרסה הרגילה — רק המחשבים
        final Map<String, Object> noKids = row("releasesLeft", 3, "stations", st, "students", new java.util.ArrayList<Object>());
        SwingUtilities.invokeAndWait(() -> p.showLists(new Account.Result("ok", "", noKids), true));
        Thread.sleep(300);
        check("בלי תלמידים: הלשונית 'תלמידים' מוצגת רק בגרסה עם מרכז הבקרה, והמחשבים מוצגים", byId(p, "tab-students").isVisible() == Account.CLASSROOM
                && (Account.CLASSROOM || countIds(p, "station:") == 12));
        GateUi.shot(p, "panel-2i-no-students");
        openPanel();   // חוזרים לנתונים האמיתיים מהשרת
    }

    static Map<String, Object> row(Object... kv) {
        Map<String, Object> m = new java.util.LinkedHashMap<String, Object>();
        for (int i = 0; i < kv.length; i += 2) m.put((String) kv[i], kv[i + 1]);
        return m;
    }

    static void search(Window w, final String q) throws Exception {
        final javax.swing.JTextField f = (javax.swing.JTextField) comp(w, "roster-search");
        SwingUtilities.invokeAndWait(() -> f.setText(q));
        Thread.sleep(150);
    }

    static java.awt.Component comp(java.awt.Component c, String id) {
        if (c instanceof javax.swing.JComponent && id.equals(((javax.swing.JComponent) c).getClientProperty("dsviz.id"))) return c;
        if (c instanceof java.awt.Container) for (java.awt.Component k : ((java.awt.Container) c).getComponents()) {
            java.awt.Component r = comp(k, id);
            if (r != null) return r;
        }
        return null;
    }

    /** הטקסט של לשונית ("מחשבי המעבדה (2)") */
    static String tab(Window w, String id) {
        javax.swing.AbstractButton b = byId(w, id);
        return b == null ? "" : b.getText().replace("\u200F", "").trim();
    }

    static void waitTab(Window w, String id, String want) throws InterruptedException {
        long end = System.currentTimeMillis() + 15000;
        while (System.currentTimeMillis() < end && !tab(w, id).equals(want)) Thread.sleep(150);
        Thread.sleep(200);
    }

    static void waitLabel(Window w, String id, String want) throws InterruptedException {
        long end = System.currentTimeMillis() + 15000;
        while (System.currentTimeMillis() < end && !label(w, id).equals(want)) Thread.sleep(150);
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
