package il.lamerhav.dsviz;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * בדיקה של Reports (תיבת היוצא של הדיווחים) מול שרת אמיתי — worker/test/reports-java-e2e.mjs מריץ:
 *   claim <קוד> <שם>          — מחבר את המחשב כעמדה (כמו חלון הכניסה)
 *   queue <כותרת> <הודעה>     — מוסיף דיווח (בלי לשלוח) ומדפיס QUEUED <id>
 *   flush                       — שולח; SENT n LEFT m
 *   count                       — PENDING n
 * השרת: ‎-Ddsviz.worker=<כתובת>‎; תיבת היוצא: ‎-Ddsviz.reports=<תיקייה>‎; הכניסה: ‎-Ddsviz.home‎ / ‎-Duser.home‎.
 */
public class ReportsDriver {
    public static void main(String[] a) {
        switch (a[0]) {
            case "claim": {
                Account.Result r = Account.claimStation(a[1], a[2]);
                System.out.println("CLAIM " + r.status);
                break;
            }
            case "queue": {
                Map<String, Object> m = new LinkedHashMap<String, Object>();
                m.put("kind", "visual-run");
                m.put("auto", Boolean.TRUE);
                m.put("title", a[1]);
                m.put("message", a[2]);
                m.put("app", "Eclipse 2024-09");
                m.put("dsvizVersion", "1.0.2");
                Map<String, Object> ctx = new LinkedHashMap<String, Object>();
                ctx.put("project", System.getProperty("user.home") + "/eclipse-workspace/Targil1");
                m.put("context", ctx);
                m.put("diag", "home=" + System.getProperty("user.home") + "\nuser=" + System.getProperty("user.name") + "\n");
                System.out.println("QUEUED " + Reports.queueOnly(m));
                break;
            }
            case "flush": {
                int[] r = Reports.flush();
                System.out.println("SENT " + r[0] + " LEFT " + r[1]);
                break;
            }
            default:
                System.out.println("PENDING " + Reports.pendingCount());
        }
    }
}
