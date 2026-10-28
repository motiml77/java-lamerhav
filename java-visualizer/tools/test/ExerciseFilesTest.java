package il.lamerhav.dsviz;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.imageio.ImageIO;

/**
 * בדיקה ל"שליחת תרגיל": הכנת הקבצים (Word, תמונות, PDF, קבצים לא נתמכים), JSON, ואם הועברה כתובת שרת — שליחה אמיתית אליו.
 *   java ExerciseFilesTest <files-dir> [worker-url [app-key]]
 */
public class ExerciseFilesTest {
    public static void main(String[] a) throws Exception {
        File dir = new File(a[0]);

        // Word: טקסט (פסקאות, טאב, שבירת שורה, טבלה, נוסחה; בלי טקסט מחוק) + התמונה הגדולה (בלי הקטנה)
        ExerciseFiles.Item w = ExerciseFiles.fromFile(new File(dir, "targil.docx"));
        check(w.kind.equals("word"), "word");
        String text = w.parts.get(0).text;
        check(text.contains("שאלה 3 (25 נקודות)"), "פסקה");
        check(text.contains("כתבו פעולה countEven\tהמקבלת מחסנית\nומחזירה מספר."), "ריצות, טאב ושבירת שורה: " + text);
        check(text.contains("קלט | פלט |") && text.contains("4,7,2 | 2 |"), "טבלה: " + text);
        check(text.contains("n^2"), "נוסחה");
        check(!text.contains("מחוק"), "טקסט מחוק לא נכלל");
        check(w.parts.size() == 2 && w.parts.get(1).mime.equals("image/png"), "תמונה מתוך ה-Word (והקטנה — לא)");
        ok("Word: " + text.replace("\n", " ⏎ ").replace("\t", " ⇥ "));

        // XXE — לא קורא קבצים מהמחשב
        fails(() -> ExerciseFiles.fromFile(new File(dir, "evil.docx")), "Word");
        ok("Word עם DOCTYPE נדחה (בלי לקרוא קבצים)");

        // תמונה גדולה מוקטנת לעד 2000 פיקסלים
        ExerciseFiles.Item big = ExerciseFiles.fromFile(new File(dir, "big.png"));
        java.awt.image.BufferedImage bi = ImageIO.read(new java.io.ByteArrayInputStream(big.parts.get(0).data));
        check(Math.max(bi.getWidth(), bi.getHeight()) == 2000, "הקטנה: " + bi.getWidth() + "x" + bi.getHeight());
        ok("תמונה 4000x3000 → " + bi.getWidth() + "x" + bi.getHeight() + " " + big.parts.get(0).mime + " " + big.parts.get(0).data.length / 1024 + "KB");

        // תמונות קטנות נשלחות כמו שהן
        ExerciseFiles.Item jpg = ExerciseFiles.fromFile(new File(dir, "photo.jpg"));
        check(jpg.parts.get(0).mime.equals("image/jpeg") && jpg.parts.get(0).data.length == new File(dir, "photo.jpg").length(), "jpg כמו שהוא");
        ExerciseFiles.Item alpha = ExerciseFiles.fromFile(new File(dir, "alpha.png"));
        check(alpha.parts.get(0).mime.equals("image/png"), "png קטן");
        ok("תמונות קטנות — בלי שינוי");

        ExerciseFiles.Item pdf = ExerciseFiles.fromFile(new File(dir, "ex.pdf"));
        check(pdf.kind.equals("pdf") && pdf.parts.get(0).mime.equals("application/pdf"), "pdf");
        ok("PDF");

        fails(() -> ExerciseFiles.fromFile(new File(dir, "old.doc")), "PDF או כ-DOCX");
        fails(() -> ExerciseFiles.fromFile(new File(dir, "not-image.png")), "לא תמונה");
        ok("Word ישן וקובץ שאינו תמונה — הודעה ברורה");

        // מגבלות
        List<ExerciseFiles.Item> many = new ArrayList<ExerciseFiles.Item>();
        fails(() -> ExerciseFiles.checkAll(many), "עוד לא צירפתם");
        for (int i = 0; i < 11; i++) many.add(jpg);
        fails(() -> ExerciseFiles.checkAll(many), "עד 10");
        ok("מגבלות: רשימה ריקה, יותר מ-10 קבצים");

        // JSON — הלוך ושוב
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("s", "שלום \"עולם\"\n\\ \t\u0001");
        m.put("n", 3);
        m.put("l", Arrays.asList(1.5, true, null, "x"));
        Object back = Json.parse(Json.write(m));
        check(Json.write(back).equals(Json.write(m)), "JSON הלוך ושוב: " + Json.write(back));
        ok("JSON");

        // שליחה אמיתית לשרת (אם הועבר) — עם Word, תמונה ו-PDF
        if (a.length > 1) {
            WorkerClient c = new WorkerClient(a[1], a.length > 2 ? a[2] : "");
            // PDF אמיתי (ex.pdf הוא קובץ מדומה לבדיקות בלי רשת — Gemini דוחה אותו)
            ExerciseFiles.Item exam = ExerciseFiles.fromFile(new File(dir, "exam.pdf"));
            Map<String, Object> ex = c.exercise(Arrays.asList(w, big, exam), "", "java");
            check(!Json.arr(ex.get("tasks")).isEmpty(), "תשובה מהשרת");
            ok("שליחה לשרת: " + Json.str(ex.get("title")));
            Map<String, String> files = new LinkedHashMap<String, String>();
            files.put("M.java", "class M {}");
            Map<String, Object> fixed = c.repair(ex, files, Arrays.asList("M.java:1: error"));
            check(!Json.arr(fixed.get("tasks")).isEmpty(), "תיקון");
            ok("בקשת תיקון");
            try {
                new WorkerClient("http://127.0.0.1:9", "").exercise(Arrays.asList(pdf), "", "java");
                throw new AssertionError("צריך להיכשל");
            } catch (WorkerClient.AiError e) {
                check(e.code.equals("offline"), "offline: " + e.code + " " + e.getMessage());
                ok("שרת לא זמין: " + e.getMessage());
            }
        }
    }

    interface Action { void run() throws Exception; }

    static void fails(Action r, String expected) throws Exception {
        try {
            r.run();
        } catch (ExerciseFiles.UserError e) {
            check(e.getMessage().contains(expected), "הודעה: " + e.getMessage());
            return;
        }
        throw new AssertionError("היה צריך להיכשל (" + expected + ")");
    }

    static void check(boolean b, String what) {
        if (!b) throw new AssertionError(what);
    }

    static void ok(String s) {
        System.out.println("✓ " + s);
    }
}
