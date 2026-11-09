package il.lamerhav.dsviz;

/**
 * הכניסה נשמרת "באמת": במקומות הנסתרים של Windows (בלי dsviz.home / dsviz.machineDir), מול השרת האמיתי.
 *   claim <קוד> <שם>  — עמדת מחשב עם קוד המורה; יציאה 0 אם המחשב מחובר כעמדה
 *   check             — תהליך חדש (כמו אחרי כיבוי והדלקה / עדכון / Java אחר): עדיין מחובר? מדפיס גם את מזהה המחשב
 *   offline           — בלי אינטרנט (שרת שלא עונה): עדיין מחובר, והבדיקה מול השרת לא מוחקת כלום
 *   java -cp <classes>;dsviz.jar il.lamerhav.dsviz.Persist check
 * תופס היתר אחד של המורה (כמו LiveClaim) — משחררים אחר כך במסוף ההנהלה.
 */
public class Persist {
    public static void main(String[] a) throws Exception {
        String mode = a.length > 0 ? a[0] : "check";
        if (mode.equals("offline")) System.setProperty("dsviz.worker", "http://127.0.0.1:9");
        Account.resetForTest();
        System.out.println("java: " + System.getProperty("java.home"));
        System.out.println("machine: " + Account.machine());
        System.out.println("files: " + Account.stationFileForTest());
        Account.Access before = Account.current();
        System.out.println("before: " + (before == null ? null : before.kind + " / " + before.label()));
        boolean ok;
        if (mode.equals("claim")) {
            Account.Result r = Account.claimStation(a[1], a[2]);
            System.out.println("claim: " + r.status + " " + r.message);
            Account.Access c = Account.current();
            System.out.println("after: " + (c == null ? null : c.kind + " / " + c.label()));
            ok = c != null && c.isStation();
        } else if (mode.equals("offline")) {
            Account.Result r = Account.refresh();
            System.out.println("refresh: " + r.status);
            Account.Access c = Account.current();
            System.out.println("after refresh: " + (c == null ? null : c.kind));
            ok = before != null && before.isStation() && c != null && c.isStation() && "offline".equals(r.status);
        } else {
            ok = before != null && before.isStation();
        }
        System.out.println(ok ? "PERSIST-OK" : "PERSIST-FAIL");
        System.exit(ok ? 0 : 1);
    }
}
