package il.lamerhav.dsviz;

import org.eclipse.jdt.core.compiler.CategorizedProblem;
import org.eclipse.jdt.core.compiler.IProblem;

/**
 * הערה של "המורה בעברית" בצורה ש-Eclipse מציג: קו אדום / צהוב בעורך, והודעה ב-Problems.
 * סוג הסימון (il.lamerhav.dsviz.mentor) מוגדר ב-plugin.xml כתת-סוג של "Java Problem" — כך העורך מתייחס אליו כמו לשגיאות של Java.
 */
final class MentorProblem extends CategorizedProblem {
    static final String MARKER = "il.lamerhav.dsviz.mentor";

    private final String message;
    private final boolean error;
    private final char[] file;
    private int start, end, line;

    MentorProblem(Mentor.Finding f, char[] file) {
        // העברית מוצגת מימין לשמאל גם בחלונות של Eclipse שהם משמאל לימין (Problems, ריחוף מעל השגיאה), וכל קטע קוד
        // (`...`, או אנגלית בלי סימון) משמאל לימין כיחידה אחת — בלי שהסוגריים יתהפכו ובלי שרשימה כמו insert, remove
        // תתהפך (RtlText — אותו טיפול כמו בחלון "הסברים בעברית"). מי שקורא את ההודעה כטקסט: RtlText.strip
        this.message = RtlText.rtl(f.message);
        this.error = f.isError();
        this.file = file;
        this.start = f.start;
        this.end = f.end;
        this.line = f.line;
    }

    @Override
    public String[] getArguments() {
        return new String[0];
    }

    @Override
    public int getID() {
        // לא מספר של בעיית JDT — כדי ש-Eclipse לא יציע לה "תיקון מהיר" של בעיה אחרת
        return IProblem.Unclassified;
    }

    @Override
    public String getMessage() {
        return message;
    }

    @Override
    public char[] getOriginatingFileName() {
        return file;
    }

    @Override
    public int getSourceStart() {
        return start;
    }

    @Override
    public int getSourceEnd() {
        return end;
    }

    @Override
    public int getSourceLineNumber() {
        return line;
    }

    @Override
    public boolean isError() {
        return error;
    }

    @Override
    public boolean isWarning() {
        return !error;
    }

    @Override
    public boolean isInfo() {
        return false;
    }

    @Override
    public void setSourceEnd(int end) {
        this.end = end;
    }

    @Override
    public void setSourceLineNumber(int line) {
        this.line = line;
    }

    @Override
    public void setSourceStart(int start) {
        this.start = start;
    }

    @Override
    public int getCategoryID() {
        return CAT_UNSPECIFIED;
    }

    @Override
    public String getMarkerType() {
        return MARKER;
    }
}
