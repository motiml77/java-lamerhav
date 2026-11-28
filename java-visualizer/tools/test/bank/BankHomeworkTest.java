package il.lamerhav.dsviz;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * שיעורי בית מהמורה — הלוגיקה המשותפת (BankHomework, BankProgress, BankSubmit.applyWork), בלי שרת ובלי חלון: מה נשמר ומוצג גם בלי
 * אינטרנט, מה עוד להגיש, מה שהוגש מהמחשב נחשב מיד, הגשה שמחכה לאינטרנט — "נשמר במחשב" (לא להגשה ולא הוגש) ואחרי שנשלחה —
 * "✓ הוגש", מורה אחר / שרת ישן / תלמיד שעזב — אין כפתור, והטקסטים.
 *   java -cp ... il.lamerhav.dsviz.BankHomeworkTest
 */
public final class BankHomeworkTest {
    private static int fails, oks;
    /** שום הגשה לא מחכה בתיבת היוצא */
    private static final java.util.Set<String> NONE = new java.util.HashSet<String>();

    public static void main(String[] a) throws Exception {
        File dir = Files.createTempDirectory("bank-homework").toFile();
        // מחשב "נקי": בלי כניסה שמורה (אף אחד לא מחובר)
        System.setProperty("dsviz.home", new File(dir, "home").getPath());
        System.setProperty("dsviz.machineDir", new File(dir, "machine").getPath());
        File f = new File(dir, "sub/bank-progress.json");
        BankProgress p = new BankProgress(f);
        long now = System.currentTimeMillis();
        check(BankHomework.of(p, "שרה כהן", now, NONE) == null, "בלי שיעורי בית — אין כפתור (null)");
        p.setDone("q-old", true);
        int[] fired = { 0 };
        p.listen(() -> fired[0]++);
        ZoneId il = ZoneId.of("Asia/Jerusalem");
        String future = LocalDate.now(il).plusDays(5).toString(), past = LocalDate.now(il).minusDays(2).toString();
        String json = "[{\"id\":\"h2\",\"title\":\"חזרה על תור\",\"due\":\"" + future + "\",\"items\":["
                + "{\"bankId\":\"queue-sum-even\",\"title\":\"סכום תור וזוגיים\",\"topic\":\"תור\",\"level\":\"קל\",\"done\":true,\"at\":\"2026-11-20T10:00:00.000Z\",\"grade\":92},"
                + "{\"bankId\":\"stack-sum-size\",\"title\":\"סכום וגודל של מחסנית\",\"topic\":\"מחסנית\",\"level\":\"קל\",\"done\":false,\"at\":\"\",\"grade\":null}]},"
                + "{\"id\":\"h1\",\"title\":\"שיעורי בית 20.11\",\"due\":\"" + past + "\",\"items\":["
                + "{\"bankId\":\"tree-count-sum\",\"title\":\"מספר הצמתים וסכום העץ\",\"topic\":\"עץ בינארי\",\"level\":\"קל\",\"done\":false,\"at\":\"\",\"grade\":null},"
                + "{\"bankId\":\"stack-sum-size\",\"title\":\"סכום וגודל של מחסנית\",\"topic\":\"מחסנית\",\"level\":\"קל\",\"done\":false,\"at\":\"\",\"grade\":null},"
                + "{\"title\":\"בלי מזהה — לא נכנס\"}]},"
                + "{\"id\":\"h0\",\"title\":\"ריק\",\"due\":\"\",\"items\":[]}]";
        List<Object> sets = Json.arr(Json.parse(json));
        p.syncHomework("שרה כהן", sets);
        check(fired[0] == 1, "שיעורי בית חדשים — החלונות מקבלים הודעה");
        p.syncHomework("שרה כהן", Json.arr(Json.parse(json)));
        check(fired[0] == 1, "אותה תשובה שוב — בלי הודעה (בלי לבנות את החלון מחדש סתם)");
        BankHomework h = BankHomework.of(p, "שרה כהן", now, NONE);
        check(h != null && h.sheets.size() == 2, "שני שיעורי בית (הריק לא מוצג; שורה בלי מזהה — לא נכנסת)");
        check(h != null && h.pending().size() == 2 && "stack-sum-size".equals(h.pending().get(0).bankId) && "tree-count-sum".equals(h.pending().get(1).bankId),
                "להגשה: שתי שאלות — כל שאלה פעם אחת, גם כשהיא בשני שיעורי בית");
        check(h != null && "שיעורי בית מהמורה שרה כהן".equals(h.headline()), "הכותרת: " + (h == null ? "" : h.headline()));
        check(h != null && "2 שאלות להגשה · עבר המועד".equals(h.detail()), "המועד הקרוב של מה שעוד לא הוגש עבר: " + (h == null ? "" : h.detail()));
        check(h != null && h.has("queue-sum-even") && !h.isPending("queue-sum-even") && h.isPending("tree-count-sum") && !h.has("loops-collatz"),
                "has / isPending — לתגית ש״ב בעץ");
        BankHomework.Item q92 = h.sheets.get(0).items.get(0), open = h.sheets.get(0).items.get(1);
        check("ציון 92".equals(BankHomework.status(q92)) && "להגשה".equals(BankHomework.status(open)), "המצב ברשימה: ציון / להגשה");
        check(("עד " + BankHomework.dueText(future)).equals(h.dueLine(h.sheets.get(0))) && "עבר המועד".equals(h.dueLine(h.sheets.get(1))), "המועד של כל שיעורי בית ברשימה");
        check(BankHomework.of(p, "דוד לוי", now, NONE) == null, "המורה של התלמיד השתנה — לא מציגים את שיעורי הבית של המורה הקודם");
        check(BankHomework.of(p, "", now, NONE) != null, "בלי שם מורה לבדוק — מציגים");

        // בלי אינטרנט: נקרא מהקובץ (כמו אחרי שסוגרים ופותחים את התוכנה), וה-V של השאלות נשאר
        BankProgress again = new BankProgress(f);
        BankHomework off = BankHomework.of(again, "שרה כהן", now, NONE);
        check(off != null && off.pending().size() == 2 && again.done("q-old"), "נשמר בקובץ — מוצג גם בלי אינטרנט, והסימונים הקודמים נשארו");
        String saved = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
        check(saved.contains("\"homework\"") && saved.contains("\"questions\""), "הקובץ: questions + homework");

        // הגשה מהמחשב אחרי הבדיקה מול השרת — נחשבת מיד
        Thread.sleep(5);
        p.submitted("stack-sum-size", "שרה כהן", false);
        h = BankHomework.of(p, "שרה כהן", now, NONE);
        check(h.pending().size() == 1 && "tree-count-sum".equals(h.pending().get(0).bankId) && "שאלה אחת להגשה · עבר המועד".equals(h.detail()),
                "הוגשה מהמחשב — יורדת מיד מ'להגשה' (" + h.detail() + ")");
        // הגשה שמחכה לאינטרנט (בתיבת היוצא): "נשמר במחשב" — לא להגשה, ולא "הוגש"
        p.submitted("tree-count-sum", "שרה כהן", true);
        java.util.Set<String> out = new java.util.HashSet<String>(java.util.Arrays.asList("tree-count-sum"));
        h = BankHomework.of(p, "שרה כהן", now, out);
        BankHomework.Item tree = h.sheets.get(1).items.get(0);
        check(h.pending().isEmpty() && !h.glowing() && !h.allDone() && h.waiting().size() == 1 && tree.waiting && !tree.done,
                "מחכה לאינטרנט: לא להגשה (בלי זוהר) ולא הוגש");
        check("שיעורי בית מהמורה שרה כהן".equals(h.headline()) && "נשמרה במחשב — תישלח לבד".equals(h.detail()), "הכפתור: " + h.label());
        check("נשמר במחשב — יישלח כשיהיה אינטרנט".equals(BankHomework.status(tree)), "ברשימה: " + BankHomework.status(tree));
        check(h.dueLine(h.sheets.get(1)).startsWith("עד "), "מה שנשאר רק מחכה לאינטרנט — בלי 'עבר המועד'");
        check(p.submittedNote("tree-count-sum", "שרה כהן").contains("נשמרה במחשב") == false, "ההערה בחלון השאלה: תיבת היוצא כאן ריקה — כבר לא 'נשמרה במחשב'");
        // נשלחה (תיבת היוצא התרוקנה): "✓ הוגש"
        h = BankHomework.of(p, "שרה כהן", now, NONE);
        check(h.allDone() && "שיעורי הבית הוגשו ✓".equals(h.headline()) && "מהמורה שרה כהן".equals(h.detail()), "נשלחה — הכול הוגש: " + h.label());
        check("✓ הוגש".equals(BankHomework.status(h.sheets.get(1).items.get(0))), "ברשימה: ✓ הוגש");
        int was = fired[0];
        p.sentFromOutbox("tree-count-sum");   // Submit.onSent: הסימון יורד, וההערה — "הוגש למורה"
        check(fired[0] == was + 1 && p.submittedNote("tree-count-sum", "שרה כהן").startsWith("הוגש למורה שרה כהן ב-"), "נשלחה מתיבת היוצא: " + p.submittedNote("tree-count-sum", "שרה כהן"));
        p.sentFromOutbox("tree-count-sum");
        check(fired[0] == was + 1, "פעם שנייה — בלי הודעה");
        // בדיקה חדשה מול השרת
        Thread.sleep(5);
        String stackDone = json.replace("\"level\":\"קל\",\"done\":false,\"at\":\"\",\"grade\":null}", "\"level\":\"קל\",\"done\":XX,\"at\":\"\",\"grade\":null}")
                .replace("\"stack-sum-size\",\"title\":\"סכום וגודל של מחסנית\",\"topic\":\"מחסנית\",\"level\":\"קל\",\"done\":XX", "\"stack-sum-size\",\"title\":\"סכום וגודל של מחסנית\",\"topic\":\"מחסנית\",\"level\":\"קל\",\"done\":true")
                .replace("\"done\":XX", "\"done\":false");
        p.syncHomework("שרה כהן", Json.arr(Json.parse(stackDone)));   // השרת כבר יודע על stack-sum-size, עוד לא על tree-count-sum
        p.submitted("tree-count-sum", "שרה כהן", true);   // שוב בלי אינטרנט, ובדיקה מול השרת שעוד לא יודע עליה
        Thread.sleep(5);
        p.syncHomework("שרה כהן", Json.arr(Json.parse(stackDone)));
        check(BankHomework.of(p, "שרה כהן", now, out).sheets.get(1).items.get(0).waiting, "אחרי בדיקה מול השרת — עדיין 'נשמר במחשב' (השרת עוד לא קיבל)");

        // הגשה מלפני הבדיקה מול השרת, שהשרת לא סופר (למשל למורה קודם) — לא נחשבת
        BankProgress p2 = new BankProgress(new File(dir, "p2.json"));
        p2.submitted("queue-two-sum", "מורה קודם", false);
        Thread.sleep(5);
        p2.syncHomework("שרה כהן", Json.arr(Json.parse("[{\"id\":\"x\",\"title\":\"ש\",\"due\":\"\",\"items\":[{\"bankId\":\"queue-two-sum\",\"title\":\"שני מספרים\",\"done\":false}]}]")));
        BankHomework h2 = BankHomework.of(p2, "שרה כהן", now, NONE);
        check(h2.pending().size() == 1 && "שאלה אחת להגשה".equals(h2.detail()), "הגשה ישנה שהשרת לא סופר — עדיין להגשה, ובלי מועד: " + h2.detail());

        // מועד שעוד לא הגיע
        BankProgress p3 = new BankProgress(new File(dir, "p3.json"));
        p3.syncHomework("שרה כהן", Json.arr(Json.parse("[{\"id\":\"y\",\"title\":\"ש\",\"due\":\"" + future + "\",\"items\":[{\"bankId\":\"a\",\"done\":false},{\"bankId\":\"b\",\"done\":false},{\"bankId\":\"c\",\"done\":true}]}]")));
        BankHomework h3 = BankHomework.of(p3, "שרה כהן", now, NONE);
        check(("2 שאלות להגשה · להגשה עד " + BankHomework.dueText(future)).equals(h3.detail()), "להגשה עד: " + h3.detail());
        check("a".equals(h3.sheets.get(0).items.get(0).title), "שאלה בלי שם — המזהה");

        // אין יותר שיעורי בית
        int before = fired[0];
        p.clearHomework();
        check(BankHomework.of(p, "שרה כהן", now, NONE) == null && fired[0] == before + 1, "נמחקו — אין כפתור, והחלונות מקבלים הודעה");
        p.clearHomework();
        check(fired[0] == before + 1, "מחיקה שנייה — בלי הודעה");

        // סוף יום ההגשה — שעון ישראל (חורף +2, קיץ +3)
        check(BankHomework.dueEnd("2026-12-03") == Instant.parse("2026-12-03T22:00:00Z").toEpochMilli(), "סוף היום בחורף: 22:00 UTC");
        check(BankHomework.dueEnd("2026-07-01") == Instant.parse("2026-07-01T21:00:00Z").toEpochMilli(), "סוף היום בקיץ: 21:00 UTC");
        check("3.12".equals(BankHomework.dueText("2026-12-03")), "התאריך בקצרה: 3.12");

        // התשובה של השרת (BankSubmit.applyWork) — עם שיעורי בית / שרת ישן בלי השדה / תלמיד שלא משויך יותר
        File pf = new File(dir, "applied.json");
        System.setProperty("dsviz.bankProgress", pf.getPath());
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("teacher", Json.parse("{\"name\":\"שרה כהן\",\"school\":\"x\"}"));
        body.put("work", Json.parse("[{\"bankId\":\"queue-sum-even\",\"grade\":92,\"again\":false}]"));
        body.put("homework", Json.arr(Json.parse(json)));
        BankSubmit.applyWork(new Account.Result("ok", "", body));
        BankHomework fromServer = BankHomework.of(BankProgress.get(), "שרה כהן", now, NONE);
        check(fromServer != null && "שרה כהן".equals(fromServer.teacher) && BankProgress.get().gradeNote("queue-sum-even").contains("92"), "תשובה עם שיעורי בית: נשמרים (עם שם המורה), וגם הציונים");
        Map<String, Object> old = new LinkedHashMap<String, Object>(body);
        old.remove("homework");
        BankSubmit.applyWork(new Account.Result("ok", "", old));
        check(BankHomework.of(BankProgress.get(), "שרה כהן", now, NONE) == null && BankProgress.get().gradeNote("queue-sum-even").contains("92"), "שרת ישן (בלי homework) — אין כפתור, והציונים כרגיל");
        BankSubmit.applyWork(new Account.Result("ok", "", body));
        BankSubmit.applyWork(new Account.Result("offline", "אין אינטרנט", null));
        check(BankHomework.of(BankProgress.get(), "שרה כהן", now, NONE) != null, "בלי אינטרנט — נשאר מה ששמור");
        BankSubmit.applyWork(new Account.Result("none", "", null));
        check(BankHomework.of(BankProgress.get(), "שרה כהן", now, NONE) == null, "התלמיד לא משויך יותר (none) — אין שיעורי בית");
        // תלמיד שלא מחובר / עמדת מחשב: current() לא מציג (Submit.mode() ריק כאן)
        BankSubmit.applyWork(new Account.Result("ok", "", body));
        check(BankHomework.current() == null, "לא תלמיד משויך (כאן: אף אחד לא מחובר) — current() מחזיר null, גם כשיש שיעורי בית בקובץ");
        BankHomework.teacherForTest = "שרה כהן";
        check(BankHomework.current() != null, "תלמיד משויך (בבדיקה) — מוצג");
        BankHomework.teacherForTest = null;

        System.out.println(fails == 0 ? "שיעורי בית (הלוגיקה): " + oks + " בדיקות תקינות" : "שיעורי בית: " + fails + " בעיות");
        System.exit(fails == 0 ? 0 : 1);
    }

    private static void check(boolean ok, String what) {
        if (ok) oks++; else fails++;
        System.out.println((ok ? "  ok   " : "  FAIL ") + what);
    }
}
