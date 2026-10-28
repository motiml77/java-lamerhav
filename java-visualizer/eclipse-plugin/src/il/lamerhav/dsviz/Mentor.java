package il.lamerhav.dsviz;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.jdt.core.compiler.IProblem;
import org.eclipse.jdt.core.dom.CompilationUnit;

/**
 * "המורה בעברית": בזמן הכתיבה ב-Eclipse, ליד כל שגיאה של הקומפיילר מופיע הסבר בעברית — ובמקום המדויק של הבעיה
 * (HebrewErrors); ובנוסף אזהרות על טעויות נפוצות שהקומפיילר לא מזהה, כי הקוד "חוקי" — אבל לא עושה מה שהתלמיד רצה
 * (MentorRules: == במחרוזות, ; אחרי if, לולאה שלא מתקדמת, top() שהערך שלה הולך לאיבוד, רקורסיה בלי תנאי עצירה...).
 * המחלקה הזאת לא תלויה ב-Eclipse עצמו (רק ב-AST) — כך אפשר לבדוק אותה בלי Eclipse (tools/test/MentorTest.java).
 * החיבור ל-Eclipse: MentorParticipant.
 */
public final class Mentor {
    static final int ERROR = 2, WARNING = 1;

    /** הערה אחת: איפה (תווים, כולל הסוף), שורה, חומרה, ההודעה, ואיזה כלל יצר אותה (לבדיקות) */
    public static final class Finding {
        public final int start, end, line, severity;
        public final String message, rule;

        Finding(int start, int end, int line, int severity, String message, String rule) {
            this.start = start;
            this.end = end;
            this.line = line;
            this.severity = severity;
            this.message = message;
            this.rule = rule;
        }

        public boolean isError() {
            return severity == ERROR;
        }

        @Override
        public String toString() {
            return (isError() ? "שגיאה" : "אזהרה") + " שורה " + line + " [" + rule + "]: " + message;
        }
    }

    private Mentor() {
    }

    /**
     * ההערות לקובץ: תרגום (ומיקום מדויק) לכל שגיאה ואזהרה של הקומפיילר, ואזהרות על טעויות נפוצות.
     * האזהרות נבדקות רק כשאין שגיאות תחביר — עם שגיאת תחביר ה-AST "משוחזר" ועלול להטעות.
     */
    public static List<Finding> analyze(CompilationUnit cu, String text, IProblem[] problems) {
        MentorSource src = new MentorSource(text);
        HebrewErrors he = new HebrewErrors(cu, src);
        List<Finding> out = new ArrayList<Finding>();
        Set<String> seen = new HashSet<String>();
        boolean syntax = false;
        for (IProblem p : problems == null ? new IProblem[0] : problems) {
            if (!p.isError() && !p.isWarning()) continue;
            if ((p.getID() & IProblem.Syntax) != 0) syntax = true;
            Finding f = he.explain(p);
            if (f != null && seen.add(f.line + "|" + f.message)) out.add(f);
        }
        if (!syntax) {
            try {
                for (Finding f : MentorRules.check(cu, src)) if (seen.add(f.line + "|" + f.message)) out.add(f);
            } catch (RuntimeException e) {
                // כלל שנכשל על קוד חריג — לא מפריעים לעבודה (פשוט בלי האזהרות)
            }
        }
        return out;
    }
}
