package il.lamerhav.dsviz;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * עברית עם קוד באמצע (s.pop(), Stack<Integer>, i != 0, arr[i]) — בצורה שמוצגת נכון מימין לשמאל, בלי שהסוגריים, הנקודות,
 * המספרים והאופרטורים של הקוד יתהפכו או יקפצו לצד השני של המשפט: בחלון "הסברים בעברית" (MentorView), וב-Problems
 * ובריחוף מעל השגיאה (MentorProblem). בלי תלות ב-SWT או ב-Eclipse — כך אפשר לבדוק בלי Eclipse (tools/test/RtlTextTest.java).
 * <p>
 * למה תווי הטבעה (RLE / LRE / PDF) וסימני כיוון (RLM), ולא תווי הבידוד (RLI / LRI / FSI / PDI, ‎U+2066..U+2069‎):
 * <ul>
 * <li>ב-Windows (שם התלמידים עובדים) התווית של SWT היא פקד STATIC של Win32 שמצויר ב-GDI ‏(DrawText ← Uniscribe).
 * הטבעות וסימני כיוון נתמכים שם מאז Windows 2000. תווי הבידוד נוספו ל-Unicode רק ב-2013 (גרסה 6.3), ואין ערובה
 * ש-Uniscribe והגופנים מכירים אותם בכל Windows 10/11 — מי שלא מכיר אותם מתעלם מהם (והקוד שוב מתהפך) או מצייר ריבוע.
 * גם SWT עצמו ל-Windows קובע כיוון של טקסט בדיוק כך (Widget.LRE / RLE — "Bidi UCC to enforce text direction",
 * למשל ב-List עם AUTO_TEXT_DIRECTION), וגם Eclipse ‏(org.eclipse.osgi.util.TextProcessor: LRE / PDF / LRM) — זה מה
 * שבטוח עובד שם.</li>
 * <li>ב-Linux ‏(GTK / Pango / fribidi) ההטבעות עובדות אותו דבר (נבדק בצילומי מסך של Eclipse — tools/test/eclipse-it).</li>
 * <li>כיוון הפסקה: חלון Problems והריחוף הם משמאל לימין, ו-WS_EX_RTLREADING של Windows פועל רק כש-Windows עצמו
 * בעברית; ומי שמחליט לפי האות ה"חזקה" הראשונה הופך הסבר שמתחיל ב-p או ב-Ctrl+1 לפסקה משמאל לימין. לכן כל שורה
 * עטופה ב-RLM RLE ... PDF: ה-RLM קובע פסקה מימין לשמאל למי שמחליט לפי האות הראשונה, וה-RLE מבטיח סדר מימין לשמאל
 * גם כשהבסיס משמאל לימין.</li>
 * <li>כל קטע קוד: RLM LRE קוד PDF RLM — הקוד משמאל לימין, כיחידה אחת (סוגריים לא מתהפכים, הנקודה של s.pop() נשארת
 * במקום). ה-RLM משני הצדדים עושה את מה שתו בידוד היה עושה: פסיק או רווח בין שני קטעי קוד (insert, remove) שייך
 * לעברית — ולא מצטרף לאנגלית ומהפך את סדר הרשימה; וסימן פיסוק אחרי קוד בסוף משפט נשאר בסוף המשפט (משמאל).</li>
 * </ul>
 * קטעי קוד: מה שבין `...` (HebrewErrors.c), וגם מילים באנגלית / קוד בלי סימון (ArrayIndexOutOfBoundsException, Ctrl+1,
 * true, toString(), מספרים) — markCode מוצא אותם. סוגריים של העברית — ( ) וגם [ ] שסביב העצה — אינם קוד: הם נשארים
 * בתוך המשפט העברי ומתהפכים כרגיל (אחרת "[" מוצג הפוך).
 */
public final class RtlText {
    static final char LRM = '‎', RLM = '‏', LRE = '‪', RLE = '‫', PDF = '‬';

    /** כל תווי הכיווניות: סימנים, הטבעות, override ובידוד */
    private static final Pattern CONTROLS = Pattern.compile("[‎‏‪-‮⁦-⁩]");

    private RtlText() {
    }

    /**
     * פסקה בעברית (קוד מסומן ב-`...`, או אנגלית בלי סימון) — לתצוגה מימין לשמאל, בכל כיוון בסיסי של הפקד.
     * כל שורה לחוד (ירידת שורה מבטלת הטבעות).
     */
    public static String rtl(String text) {
        if (text == null || text.isEmpty()) return "";
        StringBuilder b = new StringBuilder();
        String[] lines = text.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            if (i > 0) b.append('\n');
            List<String[]> segs = segments(lines[i]);
            if (segs.isEmpty()) continue;
            b.append(RLM).append(RLE);
            for (String[] s : segs) {
                if (s[0] != null) b.append(RLM).append(LRE).append(s[0]).append(PDF).append(RLM);
                else b.append(s[1]);
            }
            b.append(PDF);
        }
        return b.toString();
    }

    /** שורת קוד (למשל הקוד המתוקן בסוף ההצעה): תמיד משמאל לימין, גם כשיש בה מחרוזת בעברית */
    public static String ltr(String code) {
        if (code == null || code.isEmpty()) return "";
        return LRM + "" + LRE + strip(code) + PDF;
    }

    /** הטקסט בלי תווי הכיווניות (לקריאה, לבדיקות ולחלונות שמציגים טקסט פשוט) */
    public static String strip(String s) {
        return s == null ? "" : CONTROLS.matcher(s).replaceAll("");
    }

    /**
     * החלקים של שורה, לפי הסדר: {קוד, null} לקטע קוד (משמאל לימין, יחידה אחת) או {null, טקסט} לעברית ולסימני פיסוק.
     * קטעי קוד: מה שבין `...`, וגם אנגלית / קוד בלי סימון בתוך הטקסט (markCode).
     */
    static List<String[]> segments(String line) {
        List<String[]> out = new ArrayList<String[]>();
        String[] parts = strip(line).split("`", -1);
        for (int i = 0; i < parts.length; i++) {
            if (parts[i].isEmpty()) continue;
            if (i % 2 == 1) {
                out.add(new String[] { parts[i], null });
                continue;
            }
            String[] inner = markCode(parts[i]).split("`", -1);
            for (int j = 0; j < inner.length; j++) {
                if (inner[j].isEmpty()) continue;
                out.add(j % 2 == 1 ? new String[] { inner[j], null } : new String[] { null, inner[j] });
            }
        }
        return out;
    }

    private static final Pattern CODE_RUN = Pattern.compile(
            "(?<![\\u0590-\\u05FF\\w])[\\w.\\[\\]<>(){};=!&|+*/%\"'@#?\\\\-]+(?:[ ][\\w.\\[\\]<>(){};=!&|+*/%\"'@#?\\\\-]+)*");

    /**
     * מסמן ב-`...` קטעי קוד ומילים באנגלית שלא סומנו (למשל length(), import unit4.collectionsLib.*;, {0}({1}), Ctrl+1, 127) —
     * כדי שיוצגו משמאל לימין בתוך העברית. סוגריים שסוגרים טקסט עברי (בלי זוג בתוך הקטע) וסימני פיסוק של המשפט בקצוות
     * נשארים מחוץ לסימון, כדי שיתהפכו כרגיל בעברית. (גם לתרגום של הודעות הקומפיילר — HebrewErrors.markCode.)
     */
    static String markCode(String t) {
        Matcher m = CODE_RUN.matcher(t);
        StringBuilder b = new StringBuilder();
        int last = 0;
        while (m.find()) {
            String run;
            int s = m.start(), e = m.end();
            // סימני פיסוק של המשפט בקצוות, וסוגריים בלי זוג (שסוגרים טקסט עברי — גם [ ] של העצה, "ההסבר [העצה]")
            for (boolean changed = true; changed && e > s; ) {
                changed = false;
                char end = t.charAt(e - 1), first = t.charAt(s);
                String word = t.substring(s, e);
                if (".,: ".indexOf(end) >= 0 || end == '(' || end == '[' || end == '/' && e - 2 >= s && t.charAt(e - 2) == ' '
                        || end == ')' && count(t, s, e, '(') < count(t, s, e, ')')
                        || end == ']' && count(t, s, e, '[') < count(t, s, e, ']')
                        || end == ';' && word.matches("\\w+;") && !word.matches("(return|break|continue);")) {
                    e--;
                    changed = true;
                } else if (first == ' ' || first == ')' || first == ']' || first == '(' && count(t, s, e, '(') > count(t, s, e, ')')
                        || first == '[' && count(t, s, e, '[') > count(t, s, e, ']')) {
                    s++;
                    changed = true;
                }
            }
            if (e <= s) continue;
            run = t.substring(s, e);
            boolean code = run.matches(".*[A-Za-z0-9].*") || run.matches(".*(==|!=|<=|>=|&&|\\|\\||\\+\\+|--|[;{}\\[\\]<>=!%*/+]).*");
            if (!code || run.isEmpty()) continue;
            b.append(t, last, s).append('`').append(run).append('`');
            last = e;
        }
        return b.append(t.substring(last)).toString();
    }

    private static int count(String t, int s, int e, char c) {
        int n = 0;
        for (int i = s; i < e; i++) if (t.charAt(i) == c) n++;
        return n;
    }
}
