package il.lamerhav.dsviz;
/**
 * בדיקה מול השרת האמיתי: מחשב "מתחבר" כעמדה בקוד המורה, ואחר כך נבדק שהוא מזוהה גם בלי אינטרנט.
 *   java -cp <classes>:dsviz.jar il.lamerhav.dsviz.LiveClaim <תיקייה זמנית> <קוד> <שם המחשב>
 * תופס היתר אחד של המורה — משחררים אחר כך במסוף ההנהלה (או מוחקים את העמדה).
 */
public class LiveClaim {
    public static void main(String[] a) throws Exception {
        if (a.length > 3) System.setProperty("dsviz.machine", a[3]);   // בלי — המזהה האמיתי של המחשב
        System.setProperty("dsviz.machineDir", a[0] + "/machine");
        System.setProperty("dsviz.home", a[0] + "/user");
        Account.resetForTest();
        System.out.println("before: " + Account.current());
        Account.Result r = Account.claimStation(a[1], a[2]);
        System.out.println("claim: " + r.status + " " + r.body);
        Account.Access c = Account.current();
        System.out.println("after: " + (c == null ? null : c.kind + " / " + c.label()));
        System.setProperty("dsviz.worker", "http://127.0.0.1:9");
        Account.resetForTest();
        Account.Access off = Account.current();
        System.out.println("offline: " + (off == null ? null : off.kind));
        System.exit(c != null && off != null && c.isStation() && off.isStation() ? 0 : 1);
    }
}
