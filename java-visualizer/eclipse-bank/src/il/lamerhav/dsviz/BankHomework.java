package il.lamerhav.dsviz;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * שיעורי בית מהמורה — מה שמשותף לחלון המאגר של Eclipse (BankDialog, SWT) ולזה של Visual Studio / IntelliJ / VS Code (BankVsWindow,
 * Swing), בלי ממשק: מה הגיע מהשרת (BankSubmit.syncGrades — באותה בקשה של הציונים; נשמר בקובץ ההתקדמות, ולכן מוצג גם בלי אינטרנט),
 * אילו שאלות עוד לא הוגשו, והטקסטים של הכפתור ושל הרשימה.
 *
 * רק לתלמיד שמחובר בעצמו ומשויך למורה (Submit.mode() = "student") — אף פעם לא בעמדת מחשב של הכיתה. שאלה שהתלמיד הגיש מהמחשב
 * הזה ("הגשה למורה") נחשבת "הוגשה" מיד — עוד לפני שהשרת יודע; הבדיקה הבאה מול השרת מאשרת. הגשה ששמורה במחשב ומחכה לאינטרנט
 * (Submit — תיבת היוצא) מוצגת כ"נשמר במחשב — יישלח כשיהיה אינטרנט": לא "להגשה" ולא "הוגש"; כשהיא נשלחת — "✓ הוגש".
 * בלי שיעורי בית (או מול שרת ישן שלא מכיר אותם) — current() מחזיר null, והחלון נראה בדיוק כמו קודם.
 */
final class BankHomework {
    /** שאלה בשיעורי הבית */
    static final class Item {
        String bankId = "", title = "", topic = "", level = "", at = "";
        boolean done;
        /** ההגשה שמורה במחשב ומחכה לאינטרנט (לא done, ולא להגשה) */
        boolean waiting;
        /** הציון שהמורה נתן, או null */
        Integer grade;
    }

    /** שיעורי בית אחד (המורה יכול לתת כמה) */
    static final class Sheet {
        String id = "", title = "", due = "";
        final List<Item> items = new ArrayList<Item>();

        /** עבר המועד (אחרי סוף יום ההגשה, שעון ישראל) */
        boolean past(long now) {
            return !due.isEmpty() && now > dueEnd(due);
        }

        boolean allDone() {
            for (Item i : items) if (!i.done) return false;
            return true;
        }

        /** יש בו שאלה שעוד צריך להגיש (לא הוגשה ולא מחכה לאינטרנט) */
        boolean open() {
            for (Item i : items) if (!i.done && !i.waiting) return true;
            return false;
        }
    }

    /** לבדיקות בלי שרת (חלון ה-SWT לבד): המורה של התלמיד, כאילו Submit.mode() = "student" */
    static volatile String teacherForTest;

    final String teacher;
    final List<Sheet> sheets = new ArrayList<Sheet>();
    final long now;

    private BankHomework(String teacher, long now) {
        this.teacher = teacher;
        this.now = now;
    }

    /**
     * שיעורי הבית להצגה עכשיו במחשב הזה — או null: לא תלמיד משויך (עמדת מחשב, תלמיד עצמאי, מורה), אין שיעורי בית, או שהם
     * של מורה אחר (התלמיד עבר מורה — עד הבדיקה הבאה מול השרת)
     */
    static BankHomework current() {
        String t = teacherForTest;
        if (t == null) {
            try {
                if (!"student".equals(Submit.mode())) return null;
                t = Submit.teacher();
            } catch (Throwable e) {
                return null;   // בלי הכניסה (ספרייה חסרה) — בלי שיעורי בית
            }
        }
        Set<String> waiting;
        try {
            waiting = Submit.waitingIds();
        } catch (Throwable e) {
            waiting = new LinkedHashSet<String>();
        }
        return of(BankProgress.get(), t, System.currentTimeMillis(), waiting);
    }

    /**
     * מה שנשמר ב-p, עם מה שהוגש מהמחשב הזה; teacher — המורה הנוכחי של התלמיד ("" — לא בודקים); waiting — השאלות שההגשה שלהן
     * מחכה בתיבת היוצא (Submit.waitingIds)
     */
    static BankHomework of(BankProgress p, String teacher, long now, Set<String> waiting) {
        Map<String, Object> raw = p.homeworkData();
        String saved = Json.str(raw.get("teacher"));
        if (raw.isEmpty() || (teacher != null && !teacher.isEmpty() && !saved.isEmpty() && !saved.equals(teacher))) return null;
        double since = raw.get("syncedAt") instanceof Number ? ((Number) raw.get("syncedAt")).doubleValue() : 0;
        BankHomework h = new BankHomework(saved.isEmpty() ? (teacher == null ? "" : teacher) : saved, now);
        for (Object o : Json.arr(raw.get("sets"))) {
            Map<String, Object> m = Json.obj(o);
            Sheet s = new Sheet();
            s.id = Json.str(m.get("id"));
            s.title = Json.str(m.get("title"));
            s.due = Json.str(m.get("due")).matches("\\d{4}-\\d{2}-\\d{2}") ? Json.str(m.get("due")) : "";
            for (Object io : Json.arr(m.get("items"))) {
                Map<String, Object> im = Json.obj(io);
                Item i = new Item();
                i.bankId = Json.str(im.get("bankId"));
                if (i.bankId.isEmpty()) continue;
                i.title = Json.str(im.get("title")).isEmpty() ? i.bankId : Json.str(im.get("title"));
                i.topic = Json.str(im.get("topic"));
                i.level = Json.str(im.get("level"));
                i.at = Json.str(im.get("at"));
                i.grade = im.get("grade") instanceof Number ? ((Number) im.get("grade")).intValue() : null;
                int local = Boolean.TRUE.equals(im.get("done")) ? 1 : p.localState(i.bankId, since, waiting);
                i.done = local == 1;
                i.waiting = local == 2;
                s.items.add(i);
            }
            if (!s.items.isEmpty()) h.sheets.add(s);
        }
        return h.sheets.isEmpty() ? null : h;
    }

    // ------------------------------------------------------------------ מה עוד להגיש

    /** השאלות שעוד צריך להגיש (לא הוגשו ולא מחכות לאינטרנט; כל שאלה פעם אחת), לפי הסדר: שיעורי הבית החדשים קודם */
    List<Item> pending() {
        List<Item> out = new ArrayList<Item>();
        Set<String> seen = new LinkedHashSet<String>();
        for (Sheet s : sheets) for (Item i : s.items) if (!i.done && !i.waiting && seen.add(i.bankId)) out.add(i);
        return out;
    }

    /** השאלות שההגשה שלהן שמורה במחשב ומחכה לאינטרנט (כל שאלה פעם אחת) */
    List<Item> waiting() {
        List<Item> out = new ArrayList<Item>();
        Set<String> seen = new LinkedHashSet<String>();
        for (Sheet s : sheets) for (Item i : s.items) if (i.waiting && seen.add(i.bankId)) out.add(i);
        return out;
    }

    /** יש מה להגיש — הכפתור זוהר */
    boolean glowing() {
        return !pending().isEmpty();
    }

    /** הכול הוגש באמת (שום דבר לא להגשה ושום דבר לא מחכה לאינטרנט) */
    boolean allDone() {
        return pending().isEmpty() && waiting().isEmpty();
    }

    /** השאלה בשיעורי הבית (לתגית "ש״ב" בעץ) */
    boolean has(String bankId) {
        for (Sheet s : sheets) for (Item i : s.items) if (i.bankId.equals(bankId)) return true;
        return false;
    }

    /** השאלה בשיעורי הבית ועוד לא הוגשה */
    boolean isPending(String bankId) {
        for (Item i : pending()) if (i.bankId.equals(bankId)) return true;
        return false;
    }

    /** המועד הקרוב של שיעורי בית שיש בהם מה להגיש ("" — בלי מועד) */
    String nearestDue() {
        String best = "";
        for (Sheet s : sheets) {
            if (!s.open() || s.due.isEmpty()) continue;
            if (best.isEmpty() || s.due.compareTo(best) < 0) best = s.due;
        }
        return best;
    }

    // ------------------------------------------------------------------ הטקסטים

    /** השורה הראשונה של הכפתור: "שיעורי בית מהמורה שרה כהן" / "שיעורי הבית הוגשו ✓" */
    String headline() {
        if (allDone()) return "שיעורי הבית הוגשו ✓";
        return teacher.isEmpty() ? "שיעורי בית מהמורה" : "שיעורי בית מהמורה " + teacher;
    }

    /**
     * השורה השנייה: "2 שאלות להגשה · להגשה עד 3.12" / "שאלה אחת להגשה · עבר המועד" / "נשמרה במחשב — תישלח לבד" /
     * "מהמורה שרה כהן"
     */
    String detail() {
        List<Item> p = pending();
        if (p.isEmpty()) {
            int w = waiting().size();
            if (w == 1) return "נשמרה במחשב — תישלח לבד";   // קצר (נכנס בכפתור); ברשימה — WAITING המלא
            if (w > 1) return w + " הגשות נשמרו במחשב — יישלחו לבד";
            return teacher.isEmpty() ? "לחיצה — הרשימה" : "מהמורה " + teacher;
        }
        String n = p.size() == 1 ? "שאלה אחת להגשה" : p.size() + " שאלות להגשה";
        String due = nearestDue();
        if (due.isEmpty()) return n;
        return n + " · " + (now > dueEnd(due) ? "עבר המועד" : "להגשה עד " + dueText(due));
    }

    /** הכול בשורה אחת (רמז / נגישות / בדיקות) */
    String label() {
        return headline() + " · " + detail();
    }

    /** המצב של שאלה ברשימה: "ציון 90" / "✓ הוגש" / "נשמר במחשב — יישלח כשיהיה אינטרנט" / "להגשה" */
    static String status(Item i) {
        if (i.done && i.grade != null) return "ציון " + i.grade;
        if (i.done) return "✓ הוגש";
        return i.waiting ? WAITING : "להגשה";
    }

    static final String WAITING = "נשמר במחשב — יישלח כשיהיה אינטרנט";

    /** המועד של שיעורי הבית ברשימה: "עד 3.12" / "עבר המועד" / "" */
    String dueLine(Sheet s) {
        if (s.due.isEmpty()) return "";
        return s.past(now) && s.open() ? "עבר המועד" : "עד " + dueText(s.due);
    }

    /** "3.12" מ-2026-12-03 */
    static String dueText(String due) {
        try {
            LocalDate d = LocalDate.parse(due);
            return d.getDayOfMonth() + "." + d.getMonthValue();
        } catch (RuntimeException e) {
            return due;
        }
    }

    /** סוף יום ההגשה: חצות שאחרי due, שעון ישראל (במילישניות) */
    static long dueEnd(String due) {
        try {
            return LocalDate.parse(due).plusDays(1).atStartOfDay(ZoneId.of("Asia/Jerusalem")).toInstant().toEpochMilli();
        } catch (RuntimeException e) {
            return Long.MAX_VALUE;
        }
    }
}
