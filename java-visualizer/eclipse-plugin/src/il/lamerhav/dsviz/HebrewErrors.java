package il.lamerhav.dsviz;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.jdt.core.compiler.CategorizedProblem;
import org.eclipse.jdt.core.compiler.IProblem;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.Assignment;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.BooleanLiteral;
import org.eclipse.jdt.core.dom.BreakStatement;
import org.eclipse.jdt.core.dom.CatchClause;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.ContinueStatement;
import org.eclipse.jdt.core.dom.DoStatement;
import org.eclipse.jdt.core.dom.EnhancedForStatement;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.FieldAccess;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.ForStatement;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IfStatement;
import org.eclipse.jdt.core.dom.ImportDeclaration;
import org.eclipse.jdt.core.dom.InfixExpression;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.NodeFinder;
import org.eclipse.jdt.core.dom.ParenthesizedExpression;
import org.eclipse.jdt.core.dom.QualifiedName;
import org.eclipse.jdt.core.dom.ReturnStatement;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.ThrowStatement;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.VariableDeclarationExpression;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.VariableDeclarationStatement;
import org.eclipse.jdt.core.dom.WhileStatement;

/**
 * כל הודעות הקומפיילר של Eclipse בעברית (problems_he.properties — 946 הודעות), ולשגיאות הנפוצות אצל תלמידים
 * הסבר מדויק יותר: איפה בדיוק הבעיה (לפעמים בשורה אחרת מזו ש-Eclipse מסמנת — למשל ה-{ שלא נסגר), ומה כותבים במקום.
 */
final class HebrewErrors {
    private static Map<Integer, String> texts;

    private final CompilationUnit cu;
    private final MentorSource src;
    private List<Decl> decls;

    HebrewErrors(CompilationUnit cu, MentorSource src) {
        this.cu = cu;
        this.src = src;
    }

    /** התרגום לפי מספר הבעיה (בלי הקטגוריה), או null */
    static synchronized String template(int id) {
        if (texts == null) {
            Map<Integer, String> m = new HashMap<Integer, String>();
            try (InputStream in = HebrewErrors.class.getResourceAsStream("problems_he.properties")) {
                if (in != null) {
                    BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
                    for (String l; (l = r.readLine()) != null; ) {
                        int eq = l.indexOf(" = ");
                        if (l.startsWith("#") || eq < 0) continue;
                        m.put(Integer.valueOf(l.substring(0, eq).trim()), markCode(l.substring(eq + 3).trim()));
                    }
                }
            } catch (Exception e) {
                // בלי הקובץ — בלי תרגום (נשארות רק ההודעות באנגלית)
            }
            texts = m;
        }
        return texts.get(id);
    }

    /**
     * הסבר בעברית לבעיה של הקומפיילר — או null אם אין תרגום.
     * כל הסבר בנוי משני חלקים: משפט בעברית (בלי קוד באמצע — לכל היותר מילה אחת), ובסוגריים מרובעים עצה:
     * מה בדרך כלל הטעות, איך מזהים אותה ואיך מתקנים — והקוד המתוקן רק בסוף (כך העברית לא מתערבבת עם האנגלית).
     */
    Mentor.Finding explain(IProblem p) {
        int id = p.getID() & IProblem.IgnoreCategoriesMask;
        String[] a = args(p);
        Spot s;
        try {
            s = special(id, p, a);
        } catch (RuntimeException e) {
            s = null; // הסבר מיוחד שנכשל — נשארים עם התרגום הרגיל
        }
        String main, tip;
        int start = p.getSourceStart(), end = p.getSourceEnd();
        if (s != null) {
            main = s.message;
            tip = s.tip;
            if (s.start >= 0) { start = s.start; end = s.end; }
        } else {
            String t = template(id);
            if (t == null) return null;
            String[] parts = splitAdvice(fill(t, a));
            main = parts[0];
            tip = parts[1] != null ? parts[1] : categoryTip(p);
        }
        start = Math.max(0, Math.min(start, src.text.length() - 1));
        end = Math.max(start, Math.min(end, src.text.length() - 1));
        int line = src.text.isEmpty() ? 1 : src.line(start);
        if (p.getSourceLineNumber() > 0 && line != p.getSourceLineNumber()) {
            main += " (הסימון באנגלית — בשורה " + p.getSourceLineNumber() + ")";
        }
        return new Mentor.Finding(start, end, line, p.isError() ? Mentor.ERROR : Mentor.WARNING, main, tip, "jdt:" + id);
    }

    /** הסבר ומקום מדויקים (start < 0 = המקום של Eclipse): המשפט, והעצה שבסוגריים המרובעים */
    static final class Spot {
        final int start, end;
        final String message, tip;

        Spot(int start, int end, String message, String tip) {
            this.start = start;
            this.end = end;
            this.message = message;
            this.tip = tip;
        }

        Spot(String message, String tip) {
            this(-1, -1, message, tip);
        }
    }

    /**
     * תרגום רגיל: החלק שאחרי " — " (או אחרי המשפט הראשון) הוא כבר עצה — עובר לסוגריים המרובעים.
     * מחזיר [משפט, עצה או null]
     */
    static String[] splitAdvice(String t) {
        int dash = indexOutsideCode(t, " — "), dot = indexOutsideCode(t, ". ");
        boolean byDot = dot >= 0 && (dash < 0 || dot < dash);
        int cut = byDot ? dot : dash, skip = byDot ? 2 : 3;
        if (cut < 0) return new String[] { t, null };
        String tip = t.substring(cut + skip).trim().replaceAll("\\.$", "");
        return new String[] { t.substring(0, cut).trim(), tip.isEmpty() ? null : tip };
    }

    private static int indexOutsideCode(String t, String what) {
        boolean code = false;
        for (int i = 0; i + what.length() <= t.length(); i++) {
            if (t.charAt(i) == '`') code = !code;
            else if (!code && t.startsWith(what, i)) return i;
        }
        return -1;
    }

    /** עצה כללית לפי סוג הבעיה — לתרגומים שאין בהם עצה משלהם */
    static String categoryTip(IProblem p) {
        int cat = p instanceof CategorizedProblem ? ((CategorizedProblem) p).getCategoryID() : 0;
        switch (cat) {
            case CategorizedProblem.CAT_SYNTAX:
                return "בדרך כלל הטעות בשורה הזאת או בסוף השורה שלפניה: נקודה-פסיק חסרה, סוגר שלא נסגר או מרכאות. סידור הקוד (Ctrl+Shift+F) מראה איפה בלוק לא נסגר";
            case CategorizedProblem.CAT_IMPORT:
                return "השורות של ה-import נמצאות בראש הקובץ. Ctrl+Shift+O מסדר אותן לבד. לספרייה של מבני הנתונים: `import unit4.collectionsLib.*;`";
            case CategorizedProblem.CAT_TYPE:
                return "בודקים את שם הטיפוס (אותיות גדולות וקטנות) ואם חסר import. ריחוף עם העכבר מעל משתנה מראה מה הטיפוס שלו";
            case CategorizedProblem.CAT_MEMBER:
                return "בודקים את השם ואת הערכים שנשלחים. אחרי נקודה, Ctrl+Space מראה את כל הפעולות והתכונות שיש";
            case CategorizedProblem.CAT_JAVADOC:
                return "זו הערת תיעוד — היא לא משפיעה על הריצה";
            case CategorizedProblem.CAT_CODE_STYLE:
                return "זו הערה על סגנון — הקוד ירוץ";
            case CategorizedProblem.CAT_POTENTIAL_PROGRAMMING_PROBLEM:
                return "הקוד ירוץ, אבל כנראה לא יעשה מה שרציתם — כדאי לבדוק את השורה הזאת";
            case CategorizedProblem.CAT_NAME_SHADOWING_CONFLICT:
                return "יש שני משתנים באותו שם. נותנים שם אחר, או פונים לתכונה עם this";
            case CategorizedProblem.CAT_DEPRECATION:
                return "זה עדיין עובד, אבל יש דרך חדשה יותר";
            case CategorizedProblem.CAT_UNNECESSARY_CODE:
                return "זו לא שגיאה — הקוד ירוץ. אפשר למחוק את מה שלא בשימוש, או להשתמש בו";
            case CategorizedProblem.CAT_UNCHECKED_RAW:
                return "כותבים את סוג האיברים בסוגריים המשולשים, בהגדרה וגם ב-new: `Stack<Integer> s = new Stack<Integer>();`";
            case CategorizedProblem.CAT_BUILDPATH:
            case CategorizedProblem.CAT_MODULE:
            case CategorizedProblem.CAT_COMPLIANCE:
            case CategorizedProblem.CAT_PREVIEW_RELATED:
            case CategorizedProblem.CAT_RESTRICTION:
                return "זו בעיה בהגדרות הפרויקט (ספריות או גרסת Java), לא בקוד שכתבתם";
            default:
                return "Ctrl+1 על השגיאה מציע תיקונים אפשריים";
        }
    }

    private Spot special(int id, IProblem p, String[] a) {
        if ((p.getID() & IProblem.Syntax) != 0 || id == 158) {
            Spot line = linePattern(p);
            if (line != null) return line;
        }
        switch (id) {
            case 240: case 241: case 242: return insert(p, a);
            case 204: case 209: case 230: case 231: case 237: case 239: return expected(id, p, a);
            case 232: case 233: return delete(p, a);
            case 234: return "==".equals(arg(a, 0)) ? new Spot("בין שני סימני השוויון יש רווח", "השוואה כותבים עם שני סימנים צמודים: `==`") : null;
            case 235: return invalid(p, a);
            case 205: case 210: case 236: case 238: return outsideMethod(p);
            case 220: case 250: return missingBrace(p);
            case 253: return charLiteral(p);
            case 254: return new Spot("תו מיוחד לא חוקי בתוך המחרוזת (אחרי לוכסן הפוך)",
                    "בדרך כלל זה נתיב של קובץ או לוכסן בודד. אחרי לוכסן הפוך מותר רק n, t, מרכאות או לוכסן נוסף. לוכסן הפוך עצמו כותבים פעמיים: `\\\\`");
            case 255: return new Spot("תו לא חוקי בקוד",
                    "בדרך כלל אלה מרכאות או גרשיים בעברית (״ ׳), או תו מוסתר שהועתק מ-Word או מאתר. מוחקים ומקלידים מחדש באנגלית");
            case 259: return unclosedString(p);
            case 2: return unknownType(p, arg(a, 0));
            case 4: return ambiguousType(arg(a, 0));
            case 50: case 83: return unknownVariable(p, arg(a, 0));
            case 70: return unknownField(p, arg(a, 0));
            case 100: return unknownMethod(p, a);
            case 115: return wrongArguments(a);
            case 130: return constructor(a);
            case 17: case 19: return mismatch(p, arg(a, 0), arg(a, 1));
            case 15: return incompatible(arg(a, 0), arg(a, 1));
            case 160: return operator(p, arg(a, 0), arg(a, 1));
            case 108: return missingReturn(p, arg(a, 0));
            case 105: return voidReturn(p, arg(a, 0));
            case 200: case 201: case 74: return staticContext(id, p, a);
            case 51: return uninitialized(p, arg(a, 0));
            case 55: return duplicate(p, arg(a, 0));
            case 161: return unreachable(p);
            case 178: return selfAssign(p, arg(a, 0));
            case 111: return missingReturnType(p);
            case 107: return semicolonAfterHeader(p);
            case 325: return fileName(arg(a, 0), arg(a, 1));
            case 116: return arrayMethod(p, a);
            case 150: return stringIndex(p);
            case 71: return privateField(p, arg(a, 0), arg(a, 1));
            case 110: return new Spot("לפני שם הבנאי כתוב void — ולכן זו פעולה רגילה ולא בנאי",
                    "טעות נפוצה: לבנאי אין טיפוס החזרה בכלל, ובלעדיו new לא ימצא אותו. מוחקים את המילה: `void`");
            case 114: return new Spot(c(arg(a, 0)) + " הוא טיפוס פשוט (לא עצם), ואין לו פעולות",
                    "equals".equals(arg(a, 1)) || "compareTo".equals(arg(a, 1)) ? "מספרים ותווים משווים עם: `==`" : "פעולות יש רק לעצמים (מחרוזת, מחסנית, תור...)");
            case 222: return new Spot("זו לא פקודה שלמה — הערך שמחושב כאן לא נשמר בשום מקום",
                    "שורה לבד צריכה להיות השמה, קריאה לפעולה או ++. טעות נפוצה: == במקום = (השמה), או השוואה שצריכה להיות בתוך if");
            case 223: return new Spot("בצד שמאל של סימן השוויון צריך להיות משתנה",
                    "ההשמה שמה את הערך שמימין בתוך המשתנה שמשמאל. להשוואה כותבים: `==`");
            default: return null;
        }
    }

    // ---------------------------------------------------------------- תחביר

    /** "insert X to complete Y" */
    private Spot insert(IProblem p, String[] a) {
        String what = arg(a, 0), construct = arg(a, 1);
        String tok = token(p);
        int line = src.line(p.getSourceStart());
        if (what.equals(";")) {
            boolean last = p.getSourceEnd() + 1 >= lastCodeOffset(line);
            return new Spot("חסרה נקודה-פסיק " + (last ? "בסוף שורה " + line : "אחרי " + c(tok) + " (שורה " + line + ")"),
                    "כל פקודה נגמרת בנקודה-פסיק. הקו האדום מופיע לפעמים רק בשורה הבאה — תמיד בודקים גם את סוף השורה שלפני הסימון. מוסיפים אחרי " + c(tok) + ": `;`");
        }
        if (what.equals("}")) return missingBrace(p);
        if (primitive(tok) && (what.equals("Dimensions") || src.prevCodeChar(p.getSourceStart()) == '<')) {
            return new Spot("בתוך הסוגריים המשולשים כתוב טיפוס פשוט (" + c(tok) + ")",
                    "במבני נתונים שמים רק מחלקות: " + c(boxed(tok)) + " במקום " + c(tok) + ". כותבים: " + c("Stack<" + boxed(tok) + ">"));
        }
        int open = src.count(line, '('), close = src.count(line, ')');
        String counts = open > close ? " — בשורה " + line + " נפתחו " + open + " ונסגרו " + close : "";
        if (what.startsWith(")") && (construct.equals("IfStatement") || construct.equals("WhileStatement") || construct.startsWith("For"))) {
            String kw = construct.equals("IfStatement") ? "if" : construct.equals("WhileStatement") ? "while" : "for";
            if (open == 0) return new Spot("התנאי של " + c(kw) + " לא בתוך סוגריים", "תנאי תמיד כותבים בתוך סוגריים עגולים: " + c(kw + " (...)"));
            return new Spot("חסר סוגר עגול שסוגר את התנאי של " + c(kw) + counts, "סופרים סוגריים — לכל פותח צריך סוגר. מוסיפים אחרי " + c(tok) + ": `)`");
        }
        if (what.equals(")")) return new Spot("חסר סוגר עגול" + counts, "בדרך כלל הוא חסר בסוף קריאה לפעולה או בסוף תנאי. מוסיפים אחרי " + c(tok) + ": `)`");
        if (what.equals("( )") || what.equals("()")) {
            String t = src.lineCode(line);
            int n = t.lastIndexOf("new ");
            String expr = n >= 0 ? t.substring(n).replaceAll("[;\\s]+$", "") : "new …";
            return new Spot("אחרי new חסרים סוגריים עגולים", "כשיוצרים עצם תמיד כותבים סוגריים, גם כשלא שולחים ערכים: " + c(expr + "()"));
        }
        if (what.equals("]")) return new Spot("חסר סוגר מרובע", "לכל פותח מרובע צריך סוגר: `]`");
        if (what.equals("{")) return new Spot("חסר סוגר מסולסל פותח אחרי " + c(tok), "גוף של פעולה, if או לולאה מתחיל ב: `{`");
        String where = constructName(construct);
        return new Spot("שגיאת תחביר: חסר משהו אחרי " + c(tok) + (where.isEmpty() ? "" : " (כדי להשלים את " + where + ")"), "מה שחסר: " + c(what));
    }

    /** "Syntax error on token X, Y expected" */
    private Spot expected(int id, IProblem p, String[] a) {
        String tok = arg(a, 0), exp = arg(a, 1);
        int line = src.line(p.getSourceStart());
        String code = src.lineCode(line);
        if (!exp.isEmpty() && tok.equalsIgnoreCase(exp) && !tok.equals(exp)) {
            return new Spot(c(tok) + " כתוב באותיות לא נכונות", "ב-Java אותיות גדולות וקטנות שונות. כותבים: " + c(exp));
        }
        if (tok.equals(",") && code.matches(".*\\bfor\\s*\\(.*")) {
            int f = src.lineStart(line) + src.lineText(line).indexOf("for");
            return new Spot(f, Math.max(f + 2, p.getSourceEnd()), "בכותרת של " + c("for") + " יש פסיקים במקום נקודה-פסיק",
                    "שלושת החלקים של for מופרדים בנקודה-פסיק: " + c("for (int i = 0; i < n; i++)"));
        }
        if ((tok.equals("if") || tok.equals("while")) && exp.equals("(")) {
            return new Spot("התנאי של " + c(tok) + " לא בתוך סוגריים", "תנאי תמיד כותבים בתוך סוגריים עגולים: " + c(tok + " (...)"));
        }
        if (exp.equals(";")) {
            return new Spot("חסרה נקודה-פסיק " + (id == 230 ? "לפני " : id == 231 ? "אחרי " : "ליד ") + c(tok),
                    "בדרך כלל היא חסרה בסוף השורה הקודמת: `;`");
        }
        if (KEYWORDS.contains(tok) && (exp.contains("Identifier") || exp.contains("VariableDeclarator"))) return keywordName(tok);
        if (src.depth(p.getSourceStart()) == 1 && !looksLikeDeclaration(code)) return outsideMethod(p);
        return null;
    }

    /** "Syntax error on token X, delete this token" */
    private Spot delete(IProblem p, String[] a) {
        String tok = arg(a, 0);
        int start = p.getSourceStart();
        int line = src.line(start);
        if (tok.equals("}")) return extraBrace(p);
        if (tok.equals(")")) {
            int open = src.count(line, '('), close = src.count(line, ')');
            return new Spot("יש סוגר עגול מיותר" + (close > open ? " — בשורה " + line + " נסגרו " + close + " ונפתחו רק " + open : ""),
                    "סופרים: לכל פותח צריך בדיוק סוגר אחד. מוחקים את המיותר");
        }
        if ((tok.equals(">") || tok.equals("<")) && src.codeAt(start - 1) == '=') {
            return new Spot(start - 1, start, "הסימנים של " + (tok.equals(">") ? "גדול-שווה" : "קטן-שווה") + " כתובים בסדר הפוך",
                    "קודם הסימן ואחר כך השוויון: " + c(tok + "="));
        }
        if (tok.equals("else")) return elseWithoutIf(p);
        if (tok.equals(";")) return new Spot("נקודה-פסיק מיותרת", "מוחקים אותה. בדרך כלל זה קורה אחרי סוגר מסולסל או אחרי כותרת של פעולה");
        char before = src.prevCodeChar(start);
        if ((before == '(' || before == ',') && (primitive(tok) || isTypeName(tok)) && Character.isJavaIdentifierStart(src.nextCodeChar(p.getSourceEnd() + 1))
                && src.lineCode(line).endsWith(";")) {
            return new Spot("בקריאה לפעולה כתוב גם הטיפוס (" + c(tok) + ")",
                    "את הטיפוס כותבים רק בכותרת של הפעולה. בקריאה שולחים רק ערכים או שמות של משתנים — מוחקים את המילה: " + c(tok));
        }
        if (src.depth(start) == 1 && !looksLikeDeclaration(src.lineCode(line))) return outsideMethod(p);
        return new Spot(c(tok) + " מיותר כאן", "מוחקים אותו, או בודקים מה חסר לפניו");
    }

    private Spot invalid(IProblem p, String[] a) {
        String tok = arg(a, 0), construct = arg(a, 1);
        if (KEYWORDS.contains(tok) && (construct.contains("VariableDeclarator") || construct.contains("Identifier"))) return keywordName(tok);
        return outsideMethod(p);
    }

    private static Spot keywordName(String tok) {
        return new Spot(c(tok) + " היא מילה שמורה ב-Java", "אי אפשר לקרוא כך למשתנה או לפעולה. בוחרים שם אחר, למשל: "
                + c("my" + Character.toUpperCase(tok.charAt(0)) + tok.substring(1)));
    }

    /** שגיאת תחביר ישירות בתוך המחלקה — כנראה פקודה מחוץ לפעולה */
    private Spot outsideMethod(IProblem p) {
        int start = p.getSourceStart();
        if (start < 0 || src.depth(start) != 1) return null;
        String code = src.lineCode(src.line(start));
        if (looksLikeDeclaration(code)) return null;
        return new Spot("הפקודה הזאת נמצאת ישירות בתוך המחלקה, מחוץ לכל פעולה",
                "פקודות כותבים רק בתוך פעולה (למשל בתוך main). אם זה קרה פתאום — כנראה יש סוגר מסולסל מיותר מעל, שסוגר את הפעולה מוקדם מדי");
    }

    private static boolean looksLikeDeclaration(String code) {
        return code.matches("(public|private|protected|static|final|abstract|class|interface|import|package|@).*")
                || code.matches("[\\w<>\\[\\], ]+\\s+\\w+\\s*(=.*)?;") || code.equals("}") || code.equals("{");
    }

    /** ה-{ שלא נסגר — לפי ההזחה */
    private Spot missingBrace(IProblem p) {
        MentorSource.Braces b = src.braces();
        if (b.unclosed.isEmpty()) {
            return new Spot("חסר סוגר מסולסל", "לכל פותח צריך סוגר. סידור הקוד (Ctrl+Shift+F) מראה איזה בלוק לא נסגר: `}`");
        }
        MentorSource.Open o = b.unclosed.get(0);
        int end = src.blockEndLine(o);
        return new Spot(o.offset, o.offset, "הסוגר המסולסל שנפתח כאן (" + describe(o) + ", שורה " + o.line + ") לא נסגר",
                "בדרך כלל שוכחים לסגור if או לולאה. סידור הקוד (Ctrl+Shift+F) מראה איפה ההזחה נשברת. מוסיפים "
                        + (end > 0 ? "לפני שורה " + end : "בסוף הקובץ") + ": `}`");
    }

    /** } מיותר — ואם אפשר, השורה שבה חסר ה-{ */
    private Spot extraBrace(IProblem p) {
        MentorSource.Braces b = src.braces();
        if (b.extra.isEmpty()) return new Spot("יש סוגר מסולסל מיותר", "מוחקים אותו, או בודקים שלא חסר פותח באחת השורות שמעליו");
        int e = b.extra.get(0);
        int le = src.line(e), ind = src.indent(le);
        for (int l = le - 1; l >= 1; l--) {
            if (src.blankLine(l)) continue;
            String code = src.lineCode(l);
            if (src.indent(l) < ind) break;
            if (src.indent(l) == ind && code.matches("(\\}\\s*)?(if|else|while|for|do|switch|try)\\b.*|.*\\)\\s*") && !code.endsWith("{") && !code.endsWith(";")) {
                int at = src.lineStart(l) + src.lineText(l).replaceAll("\\s+$", "").length() - 1;
                return new Spot(at, at, "בסוף השורה הזאת חסר סוגר מסולסל פותח — ולכן הסוגר בשורה " + le + " נראה מיותר",
                        "בדרך כלל כותבים if או לולאה עם כמה שורות ושוכחים את הסוגר בהתחלה. מוסיפים בסוף השורה: `{`");
            }
        }
        return new Spot(e, e, "הסוגר המסולסל הזה מיותר — אין פותח שהוא סוגר", "מוחקים אותו, או בודקים שלא חסר פותח באחת השורות שמעליו");
    }

    private Spot elseWithoutIf(IProblem p) {
        int le = src.line(p.getSourceStart()), ind = src.indent(le);
        for (int l = le - 1; l >= 1; l--) {
            if (src.blankLine(l)) continue;
            String code = src.lineCode(l);
            if (src.indent(l) < ind) break;
            if (src.indent(l) != ind || !code.matches("(\\}\\s*)?(else\\s+)?if\\b.*")) continue;
            if (code.matches(".*\\)\\s*;")) {
                int semi = src.lineStart(l) + src.lineText(l).lastIndexOf(';');
                return new Spot(semi, semi, "יש נקודה-פסיק מיד אחרי התנאי של " + c("if") + " — ולכן ה-else בשורה " + le + " לא שייך לשום if",
                        "טעות נפוצה: נקודה-פסיק אחרי תנאי סוגרת את ה-if. מוחקים את התו: `;`");
            }
            int body = 0;
            for (int k = l + 1; k < le; k++) if (!src.blankLine(k) && !src.lineCode(k).equals("}")) body++;
            if (!code.endsWith("{") && body >= 2) {
                int at = src.lineStart(l) + src.lineText(l).replaceAll("\\s+$", "").length() - 1;
                return new Spot(at, at, "ל-if הזה אין סוגריים מסולסלים, ולכן ה-else בשורה " + le + " כבר לא צמוד אליו",
                        "בלי סוגריים מסולסלים רק השורה הראשונה שייכת ל-if. עוטפים את כל השורות שלו: `{ ... }`");
            }
            break;
        }
        return new Spot("else בלי if לפניו", "בודקים את ה-if שמעליו: שאין נקודה-פסיק אחרי התנאי, ושכל השורות שלו בתוך סוגריים מסולסלים");
    }

    private Spot charLiteral(IProblem p) {
        String t = token(p);
        if (t.equals("''")) return new Spot("בין הגרשיים אין תו", "בין גרשיים כותבים תו אחד בדיוק. למחרוזת ריקה: `\"\"`");
        if (t.length() > 3 && !t.startsWith("'\\")) {
            String inner = t.replaceAll("^'|'$", "");
            return new Spot("בין הגרשיים יש כמה תווים", "גרשיים הם לתו אחד בלבד. לטקסט משתמשים במרכאות: " + c("\"" + inner + "\""));
        }
        return null;
    }

    private Spot unclosedString(IProblem p) {
        String t = token(p);
        if (t.indexOf('\'') > 0) return new Spot("המחרוזת מתחילה במרכאות ונגמרת בגרש", "מחרוזת מתחילה ונגמרת במרכאות: `\"...\"`");
        return new Spot("המחרוזת שמתחילה כאן לא נסגרה", "חסרות מרכאות בסוף שלה — ומחרוזת חייבת להיגמר באותה שורה: `\"...\"`");
    }

    // ---------------------------------------------------------------- שמות לא מוכרים

    private static final Map<String, String> KNOWN = new HashMap<String, String>();
    private static final Set<String> UTIL = new HashSet<String>(Arrays.asList(
            "Scanner", "ArrayList", "Random", "LinkedList", "HashMap", "List", "Arrays", "HashSet", "Map", "Set"));
    static {
        for (String s : new String[] { "String", "Integer", "Double", "Character", "Boolean", "System", "Scanner", "Stack", "Queue",
                "Node", "BinNode", "Math", "ArrayList", "Random", "Object", "int", "double", "char", "boolean", "long", "float", "void" }) {
            KNOWN.put(s.toLowerCase(), s);
        }
        KNOWN.put("bool", "boolean");
        KNOWN.put("str", "String");
        KNOWN.put("integer", "Integer");
        KNOWN.put("binnode", "BinNode");
    }

    private Spot unknownType(IProblem p, String name) {
        String proper = KNOWN.get(name.toLowerCase());
        if (proper != null && !proper.equals(name)) {
            return new Spot(c(name) + " לא מוכר", (proper.equalsIgnoreCase(name) ? "ב-Java אותיות גדולות וקטנות שונות. " : "") + "כותבים: " + c(proper));
        }
        if (LIB.contains(name) && !imports("unit4.collectionsLib")) {
            return new Spot(c(name) + " לא מוכרת — חסרה שורת import", "מוסיפים בראש הקובץ, לפני public class: `import unit4.collectionsLib.*;`");
        }
        if (UTIL.contains(name)) return new Spot(c(name) + " לא מוכרת — חסרה שורת import", "מוסיפים בראש הקובץ: " + c("import java.util." + name + ";"));
        String near = closest(name, typeNames());
        if (near != null) return new Spot("אין טיפוס בשם " + c(name), "אולי התכוונתם ל-" + c(near) + "? שימו לב לאותיות גדולות וקטנות");
        return null;
    }

    private Spot ambiguousType(String name) {
        if (LIB.contains(name) && imports("java.util")) {
            int line = 0;
            for (Object o : cu.imports()) {
                ImportDeclaration d = (ImportDeclaration) o;
                if (d.isOnDemand() && d.getName().getFullyQualifiedName().equals("java.util")) line = cu.getLineNumber(d.getStartPosition());
            }
            return new Spot(c(name) + " קיימת גם ב-java.util וגם בספרייה של מבני הנתונים, ולא ברור לאיזו הכוונה",
                    "מוחקים את ה-import של java.util" + (line > 0 ? " (שורה " + line + ")" : "") + ". אם צריך קלט מהמקלדת, כותבים במקומו: `import java.util.Scanner;`");
        }
        return null;
    }

    private Spot unknownVariable(IProblem p, String name) {
        int u = p.getSourceStart();
        String proper = KNOWN.get(name.toLowerCase());
        if ("system".equalsIgnoreCase(name) && !name.equals("System")) {
            return new Spot(c(name) + " כתוב באות קטנה", "System מתחיל באות גדולה: `System.out.println(...)`");
        }
        if (name.equals("True") || name.equals("TRUE") || name.equals("False") || name.equals("FALSE") || name.equals("Null") || name.equals("NULL")) {
            return new Spot(c(name) + " כתוב באות גדולה", "ב-Java כותבים באותיות קטנות: " + c(name.toLowerCase()));
        }
        if (proper != null && !proper.equals(name)) return new Spot(c(name) + " לא מוכר", "כותבים: " + c(proper));
        MethodDeclaration m = method(u);
        List<Decl> same = new ArrayList<Decl>();
        for (Decl d : decls()) if (d.name.equals(name) && (d.method == m || d.kind.equals("field"))) same.add(d);
        for (Decl d : same) {
            if (d.kind.equals("field")) continue;
            if (d.offset > u && inside(d.scope, u)) {
                return new Spot("המשתנה " + c(name) + " מוגדר רק בשורה " + d.line + " — אחרי השימוש", "משתמשים במשתנה רק אחרי השורה שמגדירה אותו. מעבירים את ההגדרה למעלה");
            }
        }
        for (Decl d : same) {
            if (d.kind.equals("field") || d.offset > u || inside(d.scope, u)) continue;
            if (d.kind.equals("for")) {
                return new Spot("המשתנה " + c(name) + " הוגדר בכותרת של לולאה (שורה " + d.line + "), ולכן הוא קיים רק בתוכה",
                        "אם צריך אותו גם אחרי הלולאה — מגדירים אותו לפני הלולאה");
            }
            return new Spot("המשתנה " + c(name) + " הוגדר בתוך בלוק (שורה " + d.line + "), ולכן הוא קיים רק עד הסוגר שסוגר את הבלוק",
                    "טעות נפוצה: מגדירים משתנה בתוך if או לולאה ומשתמשים בו אחריהם. מגדירים אותו לפני הבלוק, ובתוכו רק משנים את הערך");
        }
        for (Decl d : decls()) {
            if (d.name.equals(name) && d.method != null && d.method != m && !d.kind.equals("field")) {
                return new Spot("המשתנה " + c(name) + " מוגדר בפעולה " + c(d.method.getName().getIdentifier()) + " (שורה " + d.line + "), ולא כאן",
                        "לכל פעולה יש משתנים משלה. מעבירים אותו כפרמטר, או מגדירים אותו גם כאן");
            }
        }
        Set<String> visible = new LinkedHashSet<String>();
        for (Decl d : decls()) if (d.kind.equals("field") || d.method == m && d.offset < u && inside(d.scope, u)) visible.add(d.name);
        for (String v : visible) {
            if (v.equalsIgnoreCase(name)) return new Spot("אין משתנה " + c(name) + ", יש " + c(v), "ב-Java אותיות גדולות וקטנות שונות — אלה שני שמות שונים");
        }
        for (MethodDeclaration md : methods()) {
            if (md.getName().getIdentifier().equals(name)) return new Spot(c(name) + " היא פעולה, לא משתנה", "קוראים לפעולה עם סוגריים: " + c(name + "(...)"));
        }
        String near = closest(name, visible);
        if (near != null) return new Spot("אין משתנה בשם " + c(name), "אולי התכוונתם ל-" + c(near) + "? השלמה אוטומטית (Ctrl+Space) מונעת טעויות הקלדה");
        if (UTIL.contains(name)) return new Spot(c(name) + " לא מוכרת — חסרה שורת import", "מוסיפים בראש הקובץ: " + c("import java.util." + name + ";"));
        return new Spot("המשתנה " + c(name) + " לא מוכר — הוא לא הוגדר לפני השורה הזאת", "משתנה מגדירים פעם אחת, עם טיפוס, לפני השימוש: " + c("int " + name + " = 0;"));
    }

    private Spot unknownField(IProblem p, String name) {
        ASTNode n = node(p);
        Expression q = null;
        if (n != null && n.getParent() instanceof QualifiedName) q = ((QualifiedName) n.getParent()).getQualifier();
        else if (n instanceof QualifiedName) q = ((QualifiedName) n).getQualifier();
        else if (n != null && n.getParent() instanceof FieldAccess) q = ((FieldAccess) n.getParent()).getExpression();
        else if (n instanceof FieldAccess) q = ((FieldAccess) n).getExpression();
        ITypeBinding t = q != null ? q.resolveTypeBinding() : null;
        if (t == null) return null;
        String qs = q.toString();
        if (t.isArray()) return new Spot("למערך אין " + c(name), "האורך של מערך נכתב בלי סוגריים: " + c(qs + ".length"));
        for (String m : methodNames(t)) {
            if (m.equals(name)) return new Spot(c(name) + " היא פעולה, וחסרים לה סוגריים", "גם פעולה שלא מקבלת ערכים נכתבת עם סוגריים: " + c(qs + "." + name + "()"));
        }
        for (String m : methodNames(t)) {
            if (m.equalsIgnoreCase(name)) return new Spot("אין " + c(name) + " — יש פעולה בשם דומה", "שימו לב לאותיות גדולות וקטנות: " + c(qs + "." + m + "()"));
        }
        return null;
    }

    private Spot unknownMethod(IProblem p, String[] a) {
        String type = arg(a, 0), name = arg(a, 1);
        String simple = type.replaceAll("<.*", "");
        MethodInvocation mi = invocation(p);
        String recv = mi != null && mi.getExpression() != null ? mi.getExpression().toString() : "";
        String r = recv.isEmpty() ? "" : recv + ".";
        if (name.equals("elseif")) return new Spot("כתוב elseif במילה אחת", "כותבים שתי מילים, עם רווח: `else if`");
        String lib = LIB_HINTS.get(simple + ":" + name.toLowerCase());
        if (lib != null) return new Spot("ל-" + c(simple) + " אין פעולה " + c(name), lib.replace("$", r));
        if (simple.equals("PrintStream") && name.toLowerCase().startsWith("print")) {
            String right = name.toLowerCase().contains("ln") || name.toLowerCase().contains("line") ? "println" : "print";
            return new Spot(c(name) + " כתוב באותיות לא נכונות", "כותבים באותיות קטנות: " + c("System.out." + right + "(...)"));
        }
        ITypeBinding t = null;
        if (mi != null && mi.getExpression() != null) t = mi.getExpression().resolveTypeBinding();
        else if (mi != null) t = enclosingType(mi);
        Set<String> names = t != null ? methodNames(t) : new LinkedHashSet<String>();
        for (String m : names) {
            if (m.equalsIgnoreCase(name)) return new Spot("אין פעולה " + c(name) + ", יש " + c(m), "ב-Java אותיות גדולות וקטנות שונות: " + c(r + m + "()"));
        }
        if (simple.equals("String") && (name.equals("size") || name.equalsIgnoreCase("lenght") || name.equals("count"))) {
            return new Spot("למחרוזת אין פעולה " + c(name), "את האורך של מחרוזת מקבלים עם: " + c(r + "length()"));
        }
        if (LIB.contains(simple)) return new Spot("ל-" + c(simple) + " אין פעולה " + c(name), "הפעולות שיש: " + c(LIB_METHODS.get(simple)));
        if (recv.isEmpty()) {
            for (Decl d : decls()) {
                if (d.name.equals(name)) return new Spot(c(name) + " הוא משתנה, לא פעולה", "משתנה כותבים בלי סוגריים");
            }
        }
        String near = closest(name, names);
        if (near != null) return new Spot("אין פעולה " + c(name), "אולי התכוונתם ל-" + c(near) + "? אחרי נקודה, Ctrl+Space מראה את כל הפעולות");
        if (recv.isEmpty()) {
            return new Spot("אין במחלקה פעולה בשם " + c(name) + " שמקבלת את הערכים האלה",
                    "בודקים מול הכותרת של הפעולה: השם (אותיות גדולות וקטנות), מספר הערכים והטיפוסים שלהם");
        }
        return null;
    }

    private static final Set<String> LIB = new HashSet<String>(Arrays.asList("Stack", "Queue", "Node", "BinNode"));
    private static final Map<String, String> LIB_METHODS = new HashMap<String, String>();
    private static final Map<String, String> LIB_HINTS = new HashMap<String, String>();
    static {
        LIB_METHODS.put("Stack", "push, pop, top, isEmpty");
        LIB_METHODS.put("Queue", "insert, remove, head, isEmpty");
        LIB_METHODS.put("Node", "getValue, getNext, hasNext, setValue, setNext");
        LIB_METHODS.put("BinNode", "getValue, getLeft, getRight, hasLeft, hasRight, setValue, setLeft, setRight");
        // העצה שבסוגריים; $ = המשתנה ונקודה (s.)
        hint("Stack", "למחסנית אין פעולה שסופרת. כדי לספור: מעבירים את האיברים למחסנית עזר וסופרים, ואז מחזירים אותם. הפעולות שיש: `push, pop, top, isEmpty`",
                "size", "length", "count", "getsize", "len");
        hint("Stack", "בספרייה קוראים לזה top — מחזירה את האיבר העליון בלי להוציא אותו: `$top()`", "peek", "gettop", "head", "front", "first", "last", "peak");
        hint("Stack", "מכניסים למחסנית עם: `$push(x)`", "add", "insert", "enqueue", "put", "append", "addtop");
        hint("Stack", "מוציאים מהמחסנית עם: `$pop()`", "remove", "poll", "dequeue", "delete", "removetop", "pull");
        hint("Stack", "בודקים אם המחסנית ריקה עם: `$isEmpty()`", "empty", "isempty", "isnull");
        hint("Stack", "אין פעולה שמרוקנת — מרוקנים בלולאה: `while (!$isEmpty()) $pop();`", "clear", "removeall");
        hint("Queue", "לתור אין פעולה שסופרת. כדי לספור: מעבירים את האיברים לתור עזר וסופרים, ואז מחזירים אותם. הפעולות שיש: `insert, remove, head, isEmpty`",
                "size", "length", "count", "getsize", "len");
        hint("Queue", "מכניסים לתור עם: `$insert(x)`", "add", "offer", "enqueue", "push", "put", "append", "insertlast", "addlast");
        hint("Queue", "מוציאים מהתור עם: `$remove()`", "poll", "dequeue", "pop", "delete", "removefirst", "pull");
        hint("Queue", "בספרייה קוראים לזה head — מחזירה את הראשון בתור בלי להוציא אותו: `$head()`",
                "peek", "front", "top", "first", "gethead", "getfront", "element", "getfirst");
        hint("Queue", "בודקים אם התור ריק עם: `$isEmpty()`", "empty", "isempty", "isnull");
        hint("Queue", "אין פעולה שמרוקנת — מרוקנים בלולאה: `while (!$isEmpty()) $remove();`", "clear", "removeall");
        hint("Node", "הערך שבחוליה: `$getValue()`", "getdata", "getval", "value", "data", "getinfo", "getnum", "getitem");
        hint("Node", "החוליה הבאה: `$getNext()`", "next", "getnext", "nextnode", "getnextnode");
        hint("Node", "בודקים אם יש חוליה אחריה: `$hasNext()`", "hasnext", "hasnextnode");
        hint("Node", "משנים את הערך: `$setValue(x)`", "setdata", "setval", "setinfo", "setitem");
        hint("Node", "משנים את החוליה הבאה: `$setNext(n)`", "setnext", "setnextnode");
        hint("BinNode", "הבן השמאלי: `$getLeft()`", "getleftson", "getleftchild", "left", "getleftnode", "getl", "leftson", "leftchild");
        hint("BinNode", "הבן הימני: `$getRight()`", "getrightson", "getrightchild", "right", "getrightnode", "getr", "rightson", "rightchild");
        hint("BinNode", "הערך שבצומת: `$getValue()`", "getdata", "getval", "value", "data", "getinfo", "getnum", "getitem");
        hint("BinNode", "בודקים אם יש בן שמאלי: `$hasLeft()`", "hasleftson", "hasleftchild", "hasleft");
        hint("BinNode", "בודקים אם יש בן ימני: `$hasRight()`", "hasrightson", "hasrightchild", "hasright");
        hint("BinNode", "משנים בן שמאלי: `$setLeft(t)`", "setleftson", "setleftchild", "setleft");
        hint("BinNode", "משנים בן ימני: `$setRight(t)`", "setrightson", "setrightchild", "setright");
        hint("BinNode", "עלה הוא צומת בלי בנים: `!$hasLeft() && !$hasRight()`", "isleaf", "leaf");
    }

    private static void hint(String type, String text, String... names) {
        for (String n : names) LIB_HINTS.put(type + ":" + n, text);
    }

    // ---------------------------------------------------------------- טיפוסים וערכים

    private Spot wrongArguments(String[] a) {
        String type = arg(a, 0), name = arg(a, 1), params = arg(a, 2), given = arg(a, 3);
        List<String> ps = split(params), gs = split(given);
        String simple = type.replaceAll("<.*", "");
        if (name.equals("parseInt") && gs.size() == 1 && primitive(gs.get(0))) {
            return new Spot(c("parseInt") + " מקבלת מחרוזת, אבל נשלח מספר", "הערך כבר מספר — אין צורך להמיר אותו");
        }
        if (ps.size() != gs.size()) {
            return new Spot("הפעולה " + c(name) + " מקבלת " + count(ps.size()) + ", אבל נשלחו " + count(gs.size()),
                    "בודקים מול הכותרת של הפעולה כמה ערכים ובאיזה סדר" + (ps.isEmpty() ? "" : ". היא מקבלת: " + c(params)));
        }
        if (LIB.contains(simple) && type.contains("<") && ps.size() == 1) {
            return new Spot(c(type) + " יכולה לקבל רק " + c(ps.get(0)) + ", אבל נשלח " + c(gs.get(0)),
                    "הסוג שבתוך הסוגריים המשולשים קובע מה אפשר להכניס. אם צריך סוג אחר — משנים אותו בהגדרה: " + c(simple + "<" + boxedName(gs.get(0)) + ">"));
        }
        return new Spot("הפעולה " + c(name) + " מקבלת ערכים מסוג אחר ממה שנשלח", "בודקים את הסדר ואת הטיפוסים. היא מקבלת: " + c("(" + params + ")"));
    }

    private static String boxedName(String t) {
        return primitive(t) ? boxed(t) : t;
    }

    private static String count(int n) {
        return n == 0 ? "0 ערכים" : n == 1 ? "ערך אחד" : n + " ערכים";
    }

    private Spot constructor(String[] a) {
        String type = arg(a, 0), simple = type.replaceAll("<.*", "");
        String g = type.contains("<") ? type.substring(type.indexOf('<')) : "<Integer>";
        if (simple.equals("Node")) {
            return new Spot("לבנאי של " + c("Node") + " צריך לשלוח ערך", "חוליה תמיד נוצרת עם ערך (ואפשר גם עם החוליה הבאה): " + c("new Node" + g + "(5)"));
        }
        if (simple.equals("BinNode")) {
            return new Spot("לבנאי של " + c("BinNode") + " צריך לשלוח ערך", "צומת נוצר עם ערך, או עם בן שמאלי, ערך ובן ימני: " + c("new BinNode" + g + "(5)"));
        }
        if (simple.equals("Stack") || simple.equals("Queue")) {
            return new Spot("יוצרים " + c(simple) + " בלי ערכים בסוגריים", "יוצרים " + (simple.equals("Stack") ? "מחסנית ריקה ואחר כך מכניסים עם push" : "תור ריק ואחר כך מכניסים עם insert")
                    + ": " + c("new " + simple + g + "()"));
        }
        for (AbstractTypeDeclaration t : types()) {
            if (!(t instanceof TypeDeclaration) || !t.getName().getIdentifier().equals(simple)) continue;
            for (MethodDeclaration m : ((TypeDeclaration) t).getMethods()) {
                if (!m.isConstructor() && m.getName().getIdentifier().equals(simple)) {
                    return new Spot("ל-" + c(simple) + " אין בנאי כזה — בשורה " + line(m) + " כתוב טיפוס החזרה לפני השם, ולכן זו פעולה רגילה",
                            "לבנאי אין טיפוס החזרה. מוחקים את המילה: " + c(m.getReturnType2()));
                }
            }
            List<String> ctors = new ArrayList<String>();
            for (MethodDeclaration m : ((TypeDeclaration) t).getMethods()) {
                if (!m.isConstructor()) continue;
                List<String> ps = new ArrayList<String>();
                for (Object o : m.parameters()) ps.add(o.toString());
                ctors.add("(" + String.join(", ", ps) + ")");
            }
            if (ctors.isEmpty()) return new Spot("ל-" + c(simple) + " אין בנאי שמקבל ערכים", "יוצרים בלי ערכים, או מוסיפים למחלקה בנאי: " + c("new " + simple + "()"));
            return new Spot("הבנאי של " + c(simple) + " לא מקבל את הערכים שנשלחו", "בודקים את מספר הערכים והסדר. הבנאי מקבל: " + c(String.join("  או  ", ctors)));
        }
        return null;
    }

    private Spot mismatch(IProblem p, String from, String to) {
        ASTNode n = node(p);
        String expr = token(p);
        if (n instanceof Assignment && ((Assignment) n).getOperator() == Assignment.Operator.ASSIGN && conditionOf(n)) {
            Assignment as = (Assignment) n;
            return new Spot("בתנאי כתבתם השמה (=) במקום השוואה", "השוואה כותבים עם שני סימני שוויון: " + c(as.getLeftHandSide() + " == " + as.getRightHandSide()));
        }
        if (from.equals("void")) {
            return new Spot("הפעולה הזאת לא מחזירה ערך (היא void), ולכן אי אפשר לשים אותה במשתנה", "אם היא צריכה להחזיר ערך — משנים את void בכותרת לטיפוס, ומוסיפים בה return");
        }
        boolean fromDec = from.equals("double") || from.equals("float") || from.equals("Double");
        if (fromDec && (to.equals("int") || to.equals("long") || to.equals("Integer"))) {
            return new Spot("מספר עשרוני (" + c(from) + ") לא נכנס למשתנה " + c(to),
                    (expr.contains("Math.") ? "Math.sqrt ו-Math.pow מחזירות double. " : "") + "אם צריך את השבר — מגדירים את המשתנה double. כדי לקצץ את השבר: "
                            + c("(int) (" + expr + ")"));
        }
        if (from.equals("double") && to.equals("float")) {
            return new Spot("מספר עם נקודה הוא double, והמשתנה float", "מוסיפים f אחרי המספר, או משתמשים ב-double: " + c(expr + "f"));
        }
        if (from.equals("String") && numeric(to)) {
            if (expr.matches("\"-?[0-9.]+\"")) return new Spot("המספר כתוב במרכאות — ולכן הוא מחרוזת", "כותבים את המספר בלי מרכאות: " + c(expr.replace("\"", "")));
            return new Spot("מחרוזת (טקסט) לא נכנסת למשתנה מספרי", "אם יש בה מספר, ממירים: " + c("Integer.parseInt(" + expr + ")"));
        }
        if (to.equals("String") && (numeric(from) || from.equals("boolean"))) {
            return new Spot("ערך מסוג " + c(from) + " לא נכנס למשתנה " + c("String"), "ממירים למחרוזת: " + c("String.valueOf(" + expr + ")"));
        }
        if (from.equals("String") && (to.equals("char") || to.equals("Character"))) {
            if (expr.matches("\".\"")) return new Spot("תו אחד כתוב במרכאות — ולכן הוא מחרוזת", "תו כותבים בגרשיים: " + c("'" + expr.charAt(1) + "'"));
            return new Spot("מחרוזת לא נכנסת למשתנה " + c("char"), "התו הראשון שלה: " + c(expr + ".charAt(0)"));
        }
        if ((from.equals("char") || from.equals("Character")) && to.equals("String")) {
            return new Spot("תו (" + c("char") + ") לא נכנס למשתנה " + c("String"), "ממירים למחרוזת: " + c("\"\" + " + expr));
        }
        if (to.equals("boolean") && numeric(from)) {
            return new Spot("מספר לא יכול להיות תנאי", "תנאי הוא true או false — כנראה חסרה השוואה: " + c(expr + " > 0"));
        }
        String fs = from.replaceAll("<.*", ""), ts = to.replaceAll("<.*", "");
        if ((fs.equals("Node") || fs.equals("BinNode")) && !ts.equals(fs)) {
            return new Spot("זו " + (fs.equals("Node") ? "חוליה" : "צומת") + ", לא הערך שבתוכה", "את הערך מקבלים עם: " + c(expr + ".getValue()"));
        }
        if ((fs.equals("Stack") || fs.equals("Queue")) && !ts.equals(fs)) {
            boolean st = fs.equals("Stack");
            return new Spot("זו " + (st ? "מחסנית" : "תור") + ", לא ערך שבתוכ" + (st ? "ה" : "ו"), "מוציאים ערך עם: " + c(expr + (st ? ".pop()" : ".remove()")));
        }
        if ((ts.equals("Node") || ts.equals("BinNode")) && !fs.equals(ts) && !fs.equals("null")) {
            return new Spot("ערך לא נכנס למשתנה מסוג חוליה", "יוצרים חוליה חדשה עם הערך: " + c("new " + to + "(" + expr + ")"));
        }
        if (from.equals("Object") && n instanceof MethodInvocation) {
            Expression e = ((MethodInvocation) n).getExpression();
            ITypeBinding t = e != null ? e.resolveTypeBinding() : null;
            if (t != null && t.isRawType()) {
                String s = t.getErasure().getName();
                return new Spot("הערך מוחזר כ-Object, כי " + c(e) + " הוגדר בלי סוג בתוך סוגריים משולשים",
                        "מגדירים עם סוג האיברים: " + c(s + "<Integer> " + e + " = new " + s + "<Integer>();"));
            }
        }
        return null;
    }

    private Spot incompatible(String a, String b) {
        if ((a.equals("char") && b.equals("String")) || (a.equals("String") && b.equals("char"))) {
            return new Spot("משווים תו למחרוזת", "תו כותבים בגרשיים, לא במרכאות: `'a'`");
        }
        if (a.equals("String") && numeric(b) || b.equals("String") && numeric(a)) {
            return new Spot("משווים מחרוזת למספר", "ממירים את המחרוזת למספר: `Integer.parseInt(s)`");
        }
        String as = a.replaceAll("<.*", ""), bs = b.replaceAll("<.*", "");
        if ((as.equals("Node") || as.equals("BinNode")) && !bs.equals(as) || (bs.equals("Node") || bs.equals("BinNode")) && !as.equals(bs)) {
            return new Spot("משווים חוליה לערך", "את הערך שבחוליה מקבלים עם: `p.getValue()`");
        }
        return null;
    }

    private Spot operator(IProblem p, String op, String types) {
        boolean cmp = op.equals("<") || op.equals(">") || op.equals("<=") || op.equals(">=");
        if (cmp && types.startsWith("boolean")) {
            ASTNode n = node(p);
            String fix = "a < b && b < c";
            if (n instanceof InfixExpression && ((InfixExpression) n).getLeftOperand() instanceof InfixExpression) {
                InfixExpression e = (InfixExpression) n, l = (InfixExpression) e.getLeftOperand();
                fix = l + " && " + l.getRightOperand() + " " + op + " " + e.getRightOperand();
            }
            return new Spot("אי אפשר לשרשר השוואות כמו במתמטיקה", "מחברים שתי השוואות עם וגם: " + c(fix));
        }
        if (cmp && types.contains("String")) return new Spot("מחרוזות לא משווים עם גדול או קטן", "משווים לפי סדר עם compareTo: `s1.compareTo(s2) < 0`");
        if ((op.equals("&&") || op.equals("||") || op.equals("!")) && (types.contains("int") || types.contains("double"))) {
            return new Spot("הסימן " + c(op) + " עובד רק על תנאים (true או false)", "כנראה חסרה השוואה: `x != 0`");
        }
        if (types.contains("Node") || types.contains("BinNode")) return new Spot("אי אפשר לחשב עם חוליה", "קודם לוקחים את הערך שבתוכה: `p.getValue()`");
        if (types.contains("Stack") || types.contains("Queue")) return new Spot("אי אפשר לחשב עם מחסנית או תור", "קודם מוציאים ממנה ערך: `s.pop()`");
        if (types.contains("String") && !op.equals("+")) return new Spot("על מחרוזות אין את הפעולה " + c(op), "אם במחרוזת יש מספר, ממירים: `Integer.parseInt(s)`");
        return null;
    }

    // ---------------------------------------------------------------- פעולות

    private Spot missingReturn(IProblem p, String type) {
        ASTNode n = node(p);
        String ret = c("return " + sample(type) + ";");
        if (n instanceof ReturnStatement || n != null && n.getParent() instanceof ReturnStatement) {
            return new Spot("return בלי ערך, אבל הפעולה צריכה להחזיר " + c(type), "מחזירים ערך: " + ret);
        }
        MethodDeclaration m = n instanceof MethodDeclaration ? (MethodDeclaration) n : method(p.getSourceStart());
        if (m == null || m.getBody() == null) return null;
        String name = m.getName().getIdentifier();
        Block body = m.getBody();
        int close = body.getStartPosition() + body.getLength() - 1;
        List<?> st = body.statements();
        Statement last = st.isEmpty() ? null : (Statement) st.get(st.size() - 1);
        if (last instanceof IfStatement) {
            return new Spot(close, close, "אם התנאי של ה-if בשורה " + line(last) + " לא מתקיים — מגיעים לסוף " + c(name) + " בלי return",
                    "טעות נפוצה: return רק בתוך if. מוסיפים כאן, לפני הסוגר (או else עם return): " + ret);
        }
        if (last instanceof ForStatement || last instanceof WhileStatement || last instanceof EnhancedForStatement || last instanceof DoStatement) {
            return new Spot(close, close, "אם הלולאה בשורה " + line(last) + " מסתיימת (או לא מתבצעת בכלל) — מגיעים לסוף " + c(name) + " בלי return",
                    "טעות נפוצה בחיפוש: return רק כשמוצאים. מוסיפים אחרי הלולאה את מה שמחזירים כשלא מצאו: " + ret);
        }
        return new Spot(close, close, "בסוף " + c(name) + " חסר return", "פעולה שאינה void מחזירה ערך בכל מסלול: " + ret);
    }

    private Spot voidReturn(IProblem p, String type) {
        MethodDeclaration m = method(p.getSourceStart());
        if (m == null) return null;
        String name = m.getName().getIdentifier();
        if (name.equals("main")) return new Spot("main לא מחזירה ערך", "כדי להציג ערך מדפיסים אותו: `System.out.println(...)`");
        return new Spot(c(name) + " מוגדרת void, אבל כאן יש return עם ערך",
                "אם היא צריכה להחזיר ערך — משנים בכותרת (שורה " + line(m) + ") את void ל-" + (type.isEmpty() ? "טיפוס הערך" : c(type)) + ". אחרת כותבים רק: `return;`");
    }

    private Spot staticContext(int id, IProblem p, String[] a) {
        MethodDeclaration m = method(p.getSourceStart());
        String in = m != null ? m.getName().getIdentifier() : "הפעולה";
        if (id == 200) {
            return new Spot(c(in) + " היא static, ולכן אין בה " + c(arg(a, 0)), "בפעולה static אין עצם נוכחי. יוצרים עצם ופונים דרכו: `X x = new X();`");
        }
        if (id == 201) {
            String target = arg(a, 1), owner = arg(a, 0).replaceAll("<.*", "");
            if (LIB.contains(owner)) {
                String var = owner.equals("Stack") ? "s" : owner.equals("Queue") ? "q" : owner.equals("Node") ? "p" : "t";
                return new Spot("קוראים ל-" + c(target) + " דרך שם המחלקה " + c(owner), "קודם יוצרים עצם (" + c(owner + "<Integer> " + var + " = new " + owner
                        + "<Integer>(" + (owner.equals("Node") || owner.equals("BinNode") ? "5" : "") + ");") + "), ואז קוראים דרכו: " + c(var + "." + target + "(...)"));
            }
            for (MethodDeclaration md : methods()) {
                if (md.getName().getIdentifier().equals(target) && !md.isConstructor()) {
                    return new Spot(c(in) + " היא static, ו-" + c(target) + " (שורה " + line(md) + ") לא — ולכן אי אפשר לקרוא לה מכאן",
                            "מוסיפים static לכותרת של " + c(target) + ": `public static …`");
                }
            }
            return new Spot(c(in) + " היא static, ו-" + c(target) + " לא", "מוסיפים static לכותרת של " + c(target) + ", או יוצרים עצם וקוראים דרכו");
        }
        String f = arg(a, 0);
        for (Decl d : decls()) {
            if (d.name.equals(f) && d.kind.equals("field")) {
                return new Spot(c(f) + " (שורה " + d.line + ") היא תכונה של עצם, ו-" + c(in) + " היא static",
                        "מגדירים את המשתנה בתוך " + c(in) + ", או מוסיפים static להגדרה שלו");
            }
        }
        return null;
    }

    private Spot uninitialized(IProblem p, String name) {
        Decl d = visible(name, p.getSourceStart());
        if (d == null) return null;
        String init = sample(d.type);
        if (init.equals("null") && !d.type.endsWith("[]")) init = "new " + d.type + "(" + (d.type.startsWith("Node") || d.type.startsWith("BinNode") ? "…" : "") + ")";
        return new Spot("המשתנה " + c(name) + " הוגדר בשורה " + d.line + " בלי ערך, ויש מסלול שבו הוא לא מקבל ערך עד כאן",
                "בדרך כלל הערך ניתן רק בתוך if או לולאה. נותנים ערך כבר בהגדרה: " + c(d.type + " " + name + " = " + init + ";"));
    }

    private Spot duplicate(IProblem p, String name) {
        int u = p.getSourceStart();
        MethodDeclaration m = method(u);
        Decl first = null;
        for (Decl d : decls()) if (d.name.equals(name) && d.method == m && d.offset < u && !d.kind.equals("field") && first == null) first = d;
        if (first == null) return null;
        ASTNode n = node(p);
        String again = n != null && n.getParent() instanceof VariableDeclarationFragment ? text(n.getParent()) : name + " = …";
        return new Spot("המשתנה " + c(name) + " כבר הוגדר בשורה " + first.line, "משתנה מגדירים פעם אחת. כדי לשנות את הערך כותבים בלי הטיפוס: " + c(again + ";"));
    }

    private Spot unreachable(IProblem p) {
        ASTNode n = node(p);
        while (n != null && !(n instanceof Statement)) n = n.getParent();
        if (n == null || !(n.getParent() instanceof Block)) return null;
        List<?> st = ((Block) n.getParent()).statements();
        int i = st.indexOf(n);
        if (i <= 0) return null;
        Statement prev = (Statement) st.get(i - 1);
        int l = line(prev);
        String why = prev instanceof ReturnStatement ? "return" : prev instanceof BreakStatement ? "break" : prev instanceof ContinueStatement ? "continue"
                : prev instanceof ThrowStatement ? "throw" : null;
        if (why != null) {
            return new Spot("לפני השורה הזאת יש " + c(why) + " (שורה " + l + ") — ולכן לשורה הזאת לא מגיעים", "מה שבא אחרי " + why + " לא מתבצע. מעבירים את השורה למקום שלפניו");
        }
        if (prev instanceof WhileStatement && ((WhileStatement) prev).getExpression() instanceof BooleanLiteral
                || prev instanceof ForStatement && ((ForStatement) prev).getExpression() == null) {
            return new Spot("הלולאה בשורה " + l + " אינסופית ואין בה break — ולכן לשורה הזאת לא מגיעים", "צריך תנאי שעוצר את הלולאה, או break בתוכה");
        }
        if (prev instanceof IfStatement && ((IfStatement) prev).getElseStatement() != null) {
            return new Spot("גם ה-if וגם ה-else שבשורה " + l + " מסתיימים ב-return — ולכן לשורה הזאת לא מגיעים", "אחד מהם צריך להמשיך הלאה, או שהשורה הזאת מיותרת");
        }
        return null;
    }

    private Spot selfAssign(IProblem p, String name) {
        for (Decl d : decls()) {
            if (d.name.equals(name) && d.kind.equals("field")) {
                return new Spot("הפרמטר " + c(name) + " מושם בתוך עצמו — התכונה לא משתנה",
                        "כשלפרמטר ולתכונה יש אותו שם, פונים לתכונה עם this: " + c("this." + name + " = " + name + ";"));
            }
        }
        return null;
    }

    private Spot missingReturnType(IProblem p) {
        MethodDeclaration m = method(p.getSourceStart());
        ASTNode n = node(p);
        if (n instanceof MethodDeclaration) m = (MethodDeclaration) n;
        if (m == null) return null;
        String name = m.getName().getIdentifier();
        AbstractTypeDeclaration t = type(m);
        String cls = t != null ? t.getName().getIdentifier() : "";
        if (name.equalsIgnoreCase(cls)) {
            return new Spot("שם הבנאי (" + c(name) + ") שונה משם המחלקה", "בנאי נקרא בדיוק כמו המחלקה, גם באותיות גדולות וקטנות: " + c(cls));
        }
        return new Spot("חסר טיפוס לפני " + c(name), "void אם היא לא מחזירה כלום, או הטיפוס שהיא מחזירה: " + c("public static void " + name + "(…)"));
    }

    private Spot semicolonAfterHeader(IProblem p) {
        ASTNode n = node(p);
        MethodDeclaration m = n instanceof MethodDeclaration ? (MethodDeclaration) n : method(p.getSourceStart());
        int at = -1;
        if (m != null) {
            int e = m.getStartPosition() + m.getLength() - 1;
            if (src.codeAt(e) == ';') at = e;
        }
        return new Spot(at, at, "בסוף הכותרת של הפעולה יש נקודה-פסיק", "אחרי הכותרת בא מיד הסוגר המסולסל של הגוף. מוחקים את התו: `;`");
    }

    private Spot fileName(String file, String type) {
        String f = file.replaceAll(".*[/\\\\]", "");
        String fileType = f.replaceAll("\\.java$", "");
        return new Spot("הקובץ נקרא " + c(f) + " והמחלקה " + c(type) + " — השמות חייבים להיות זהים",
                "גם באותיות גדולות וקטנות. שינוי שם הקובץ: לחיצה ימנית עליו ← Refactor ← Rename. או משנים את שם המחלקה ל: " + c(fileType));
    }

    private Spot arrayMethod(IProblem p, String[] a) {
        MethodInvocation mi = invocation(p);
        String arr = mi != null && mi.getExpression() != null ? mi.getExpression().toString() : "arr";
        return new Spot(c(arr) + " הוא מערך, ולמערך אין פעולות", "האורך נכתב בלי סוגריים: " + c(arr + ".length"));
    }

    /** שגיאות תחביר שמזהים לפי השורה כולה: מערך בסגנון C, רשימת ערכים { } אחרי ההגדרה */
    private Spot linePattern(IProblem p) {
        int l = src.line(Math.max(0, p.getSourceStart()));
        for (int k = l; k <= Math.min(l + 1, src.lines()); k++) {
            String code = src.lineCode(k);
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\w+)\\s+(\\w+)\\s*\\[\\s*(\\w+)\\s*\\]\\s*;").matcher(code);
            if (m.matches() && !m.group(1).equals("return")) {
                return new Spot(src.firstChar(k), src.lineEnd(k) - 1, "כך לא יוצרים מערך ב-Java",
                        "הסוגריים המרובעים צמודים לטיפוס, והגודל נכתב אחרי new: " + c(m.group(1) + "[] " + m.group(2) + " = new " + m.group(1) + "[" + m.group(3) + "];"));
            }
            m = java.util.regex.Pattern.compile("(\\w+)\\s*=\\s*\\{(.*)\\}\\s*;").matcher(code);
            if (m.matches()) {
                Decl d = visible(m.group(1), src.firstChar(k));
                String t = d != null && d.type.endsWith("[]") ? d.type : "int[]";
                return new Spot(src.firstChar(k), src.lineEnd(k) - 1, "רשימת ערכים בסוגריים מסולסלים מותרת רק בשורת ההגדרה",
                        "אחרי ההגדרה כותבים: " + c(m.group(1) + " = new " + t + " {" + m.group(2).trim() + "};"));
            }
        }
        return null;
    }

    private Spot stringIndex(IProblem p) {
        String t = token(p);
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(.+)\\[(.+)\\]").matcher(t);
        if (!m.matches()) return null;
        return new Spot("למחרוזת אין סוגריים מרובעים — היא לא מערך", "תו במקום מסוים מקבלים עם: " + c(m.group(1) + ".charAt(" + m.group(2) + ")"));
    }

    /** p.next / st.name — תכונה פרטית: משתמשים בפעולה get / set */
    private Spot privateField(IProblem p, String field, String type) {
        ASTNode n = node(p);
        Expression q = null;
        ASTNode access = null;
        if (n != null && n.getParent() instanceof QualifiedName) { q = ((QualifiedName) n.getParent()).getQualifier(); access = n.getParent(); }
        else if (n != null && n.getParent() instanceof FieldAccess) { q = ((FieldAccess) n.getParent()).getExpression(); access = n.getParent(); }
        ITypeBinding t = q != null ? q.resolveTypeBinding() : null;
        if (t == null) return null;
        String cap = Character.toUpperCase(field.charAt(0)) + field.substring(1);
        Set<String> ms = methodNames(t);
        boolean set = access != null && access.getParent() instanceof Assignment && ((Assignment) access.getParent()).getLeftHandSide() == access;
        String getter = ms.contains("get" + cap) ? "get" + cap : ms.contains("is" + cap) ? "is" + cap : null;
        String setter = ms.contains("set" + cap) ? "set" + cap : null;
        String simple = type.replaceAll("<.*", "");
        if (set && setter != null) {
            Assignment as = (Assignment) access.getParent();
            return new Spot(c(field) + " היא תכונה פרטית של " + c(simple) + " — אי אפשר לשנות אותה ישירות", "משנים אותה דרך הפעולה: " + c(q + "." + setter + "(" + as.getRightHandSide() + ");"));
        }
        if (getter != null) return new Spot(c(field) + " היא תכונה פרטית של " + c(simple), "מבחוץ קוראים אותה דרך הפעולה: " + c(q + "." + getter + "()"));
        return null;
    }

    // ---------------------------------------------------------------- עזרים

    /** משתנה מוגדר: שם, איפה, ובאיזה תחום הוא קיים */
    static final class Decl {
        final String name, kind, type;
        final int offset, line;
        final ASTNode scope;
        final MethodDeclaration method;

        Decl(String name, String kind, String type, int offset, int line, ASTNode scope, MethodDeclaration method) {
            this.name = name;
            this.kind = kind;
            this.type = type;
            this.offset = offset;
            this.line = line;
            this.scope = scope;
            this.method = method;
        }
    }

    List<Decl> decls() {
        if (decls != null) return decls;
        final List<Decl> out = new ArrayList<Decl>();
        cu.accept(new ASTVisitor() {
            @Override
            public boolean visit(VariableDeclarationFragment f) {
                ASTNode p = f.getParent();
                String type = "int", kind = "local";
                ASTNode scope = p.getParent();
                if (p instanceof VariableDeclarationStatement) {
                    type = ((VariableDeclarationStatement) p).getType().toString();
                    if (scope instanceof Block && scope.getParent() instanceof MethodDeclaration) kind = "local";
                    else if (scope instanceof Block) kind = "block";
                } else if (p instanceof VariableDeclarationExpression) {
                    type = ((VariableDeclarationExpression) p).getType().toString();
                    kind = scope instanceof ForStatement ? "for" : "local";
                } else if (p instanceof FieldDeclaration) {
                    type = ((FieldDeclaration) p).getType().toString();
                    kind = "field";
                } else {
                    return true;
                }
                for (int i = 0; i < f.getExtraDimensions(); i++) type += "[]";
                out.add(new Decl(f.getName().getIdentifier(), kind, type, f.getName().getStartPosition(), cu.getLineNumber(f.getStartPosition()), scope,
                        method(f.getStartPosition())));
                return true;
            }

            @Override
            public boolean visit(SingleVariableDeclaration d) {
                ASTNode p = d.getParent();
                String kind = p instanceof MethodDeclaration ? "param" : p instanceof EnhancedForStatement ? "for" : p instanceof CatchClause ? "catch" : "local";
                out.add(new Decl(d.getName().getIdentifier(), kind, d.getType().toString(), d.getName().getStartPosition(),
                        cu.getLineNumber(d.getStartPosition()), p, method(d.getStartPosition())));
                return true;
            }
        });
        decls = out;
        return out;
    }

    /** ההגדרה של המשתנה שנראית במקום הזה (הקרובה ביותר) */
    private Decl visible(String name, int at) {
        MethodDeclaration m = method(at);
        Decl best = null;
        for (Decl d : decls()) {
            if (d.name.equals(name) && d.method == m && d.offset < at && inside(d.scope, at) && (best == null || d.offset > best.offset)) best = d;
        }
        return best;
    }

    private static boolean inside(ASTNode n, int at) {
        return n != null && at >= n.getStartPosition() && at < n.getStartPosition() + n.getLength();
    }

    /** הפעולה שהמקום בתוכה (הפנימית ביותר) */
    private MethodDeclaration method(int at) {
        final MethodDeclaration[] best = new MethodDeclaration[1];
        for (MethodDeclaration m : methods()) if (inside(m, at) && (best[0] == null || m.getStartPosition() > best[0].getStartPosition())) best[0] = m;
        return best[0];
    }

    private List<MethodDeclaration> methodsCache;

    List<MethodDeclaration> methods() {
        if (methodsCache != null) return methodsCache;
        final List<MethodDeclaration> out = new ArrayList<MethodDeclaration>();
        cu.accept(new ASTVisitor() {
            @Override
            public boolean visit(MethodDeclaration m) {
                out.add(m);
                return true;
            }
        });
        methodsCache = out;
        return out;
    }

    private List<AbstractTypeDeclaration> types() {
        final List<AbstractTypeDeclaration> out = new ArrayList<AbstractTypeDeclaration>();
        cu.accept(new ASTVisitor() {
            @Override
            public boolean visit(TypeDeclaration t) {
                out.add(t);
                return true;
            }
        });
        return out;
    }

    private Set<String> typeNames() {
        Set<String> s = new LinkedHashSet<String>();
        for (AbstractTypeDeclaration t : types()) s.add(t.getName().getIdentifier());
        s.addAll(LIB);
        s.addAll(Arrays.asList("String", "Integer", "Double", "Character", "Boolean", "Math", "System", "Object"));
        return s;
    }

    private static AbstractTypeDeclaration type(ASTNode n) {
        while (n != null && !(n instanceof AbstractTypeDeclaration)) n = n.getParent();
        return (AbstractTypeDeclaration) n;
    }

    private static ITypeBinding enclosingType(ASTNode n) {
        AbstractTypeDeclaration t = type(n);
        return t != null ? t.resolveBinding() : null;
    }

    private boolean imports(String pkg) {
        for (Object o : cu.imports()) {
            ImportDeclaration d = (ImportDeclaration) o;
            String n = d.getName().getFullyQualifiedName();
            if (n.equals(pkg) || n.startsWith(pkg + ".")) return true;
        }
        return false;
    }

    /** שמות הפעולות של הטיפוס (כולל של מחלקות-העל) */
    static Set<String> methodNames(ITypeBinding t) {
        Set<String> out = new LinkedHashSet<String>();
        for (ITypeBinding c = t; c != null; c = c.getSuperclass()) {
            for (IMethodBinding m : c.getDeclaredMethods()) if (!m.isConstructor()) out.add(m.getName());
            for (ITypeBinding i : c.getInterfaces()) for (IMethodBinding m : i.getDeclaredMethods()) out.add(m.getName());
        }
        return out;
    }

    private ASTNode node(IProblem p) {
        int s = p.getSourceStart(), e = p.getSourceEnd();
        if (s < 0 || e < s) return null;
        return NodeFinder.perform(cu, s, e - s + 1);
    }

    private MethodInvocation invocation(IProblem p) {
        ASTNode n = node(p);
        for (int i = 0; n != null && i < 3; i++, n = n.getParent()) if (n instanceof MethodInvocation) return (MethodInvocation) n;
        return null;
    }

    private static boolean conditionOf(ASTNode n) {
        ASTNode c = n, p = n.getParent();
        while (p instanceof ParenthesizedExpression) { c = p; p = p.getParent(); }
        return p instanceof IfStatement && ((IfStatement) p).getExpression() == c
                || p instanceof WhileStatement && ((WhileStatement) p).getExpression() == c
                || p instanceof DoStatement && ((DoStatement) p).getExpression() == c
                || p instanceof ForStatement && ((ForStatement) p).getExpression() == c;
    }

    private int line(ASTNode n) {
        return cu.getLineNumber(n.getStartPosition());
    }

    private String token(IProblem p) {
        int s = p.getSourceStart(), e = p.getSourceEnd();
        if (s < 0 || e < s || e >= src.text.length()) return "";
        return src.text.substring(s, e + 1).trim();
    }

    /** המקום של התו האחרון בקוד בשורה (בלי הערות) + 1 */
    private int lastCodeOffset(int line) {
        int e = src.lineEnd(line);
        while (e > src.lineStart(line) && Character.isWhitespace(src.codeAt(e - 1))) e--;
        return e;
    }

    private String describe(MentorSource.Open o) {
        String h = src.header(o).replaceAll("^\\}\\s*", "");
        String kw = h.replaceAll("^(else\\s+if|if|else|while|for|do|switch|try|catch|finally)\\b.*", "$1");
        if (!kw.equals(h)) return "של " + c(kw);
        if (h.matches(".*\\b(class|interface|enum)\\s+\\w+.*")) return "של המחלקה " + c(h.replaceAll(".*\\b(class|interface|enum)\\s+(\\w+).*", "$2"));
        if (h.matches(".*\\b\\w+\\s*\\(.*\\).*")) return "של הפעולה " + c(h.replaceAll(".*?\\b(\\w+)\\s*\\(.*", "$1"));
        return "בלוק";
    }

    private static String constructName(String c) {
        switch (c) {
            case "Block": return "הבלוק";
            case "ClassBody": return "המחלקה";
            case "MethodBody": return "הפעולה";
            case "IfStatement": return "ה-if";
            case "WhileStatement": return "ה-while";
            case "Expression": case "ArgumentList": return "הביטוי";
            default: return c.contains("Statement") ? "הפקודה" : "";
        }
    }

    static final Set<String> KEYWORDS = new HashSet<String>(Arrays.asList("abstract", "assert", "boolean", "break", "byte", "case", "catch", "char", "class",
            "const", "continue", "default", "do", "double", "else", "enum", "extends", "final", "finally", "float", "for", "goto", "if", "implements", "import",
            "instanceof", "int", "interface", "long", "native", "new", "package", "private", "protected", "public", "return", "short", "static", "strictfp",
            "super", "switch", "synchronized", "this", "throw", "throws", "transient", "try", "void", "volatile", "while", "true", "false", "null"));

    static boolean primitive(String t) {
        return t.equals("int") || t.equals("double") || t.equals("char") || t.equals("boolean") || t.equals("long") || t.equals("float")
                || t.equals("short") || t.equals("byte");
    }

    private static boolean numeric(String t) {
        return t.equals("int") || t.equals("double") || t.equals("long") || t.equals("float") || t.equals("short") || t.equals("byte")
                || t.equals("Integer") || t.equals("Double");
    }

    private boolean isTypeName(String t) {
        return !t.isEmpty() && Character.isUpperCase(t.charAt(0)) && t.matches("[\\w<>\\[\\]]+");
    }

    static String boxed(String t) {
        switch (t) {
            case "int": return "Integer";
            case "char": return "Character";
            default: return Character.toUpperCase(t.charAt(0)) + t.substring(1);
        }
    }

    /** ערך לדוגמה לטיפוס (להצעות כמו return false;) */
    static String sample(String t) {
        switch (t) {
            case "boolean": return "false";
            case "int": case "long": case "short": case "byte": return "0";
            case "double": case "float": return "0.0";
            case "char": return "' '";
            case "String": return "\"\"";
            default: return "null";
        }
    }

    /** הפרמטרים של ההודעה, בלי שמות חבילות (unit4.collectionsLib.Stack<java.lang.Integer> ← Stack<Integer>) */
    static String[] args(IProblem p) {
        String[] a = p.getArguments();
        if (a == null) return new String[0];
        String[] r = new String[a.length];
        for (int i = 0; i < a.length; i++) r[i] = shorten(a[i]);
        return r;
    }

    static String shorten(String s) {
        return s == null ? "" : s.replaceAll("\\b(?:[a-z_][\\w$]*\\.)+(?=[A-Z])", "");
    }

    private static String arg(String[] a, int i) {
        return i < a.length && a[i] != null ? a[i] : "";
    }

    /** ממלא את הפרמטרים בתבנית — פרמטר שלא בתוך קטע קוד מסומן מסומן כקוד בעצמו (שמות טיפוסים ומשתנים) */
    static String fill(String t, String[] a) {
        StringBuilder b = new StringBuilder();
        boolean inCode = false;
        for (int i = 0; i < t.length(); i++) {
            char ch = t.charAt(i);
            if (ch == '`') inCode = !inCode;
            if (ch == '{' && i + 2 < t.length() && Character.isDigit(t.charAt(i + 1)) && t.charAt(i + 2) == '}') {
                int n = t.charAt(i + 1) - '0';
                String v = n < a.length ? a[n] : "";
                b.append(inCode || v.isEmpty() ? v : c(v));
                i += 2;
            } else {
                b.append(ch);
            }
        }
        return b.toString().replace("``", "");
    }

    private static final java.util.regex.Pattern CODE_RUN = java.util.regex.Pattern.compile(
            "(?<![\\u0590-\\u05FF\\w])[\\w.\\[\\]<>(){};=!&|+*/%\"'@#?\\\\-]+(?:[ ][\\w.\\[\\]<>(){};=!&|+*/%\"'@#?\\\\-]+)*");

    /**
     * מסמן קטעי קוד בתרגום (למשל length(), import unit4.collectionsLib.*;, {0}({1})) — כדי שיוצגו משמאל לימין בתוך העברית.
     * סוגריים שסוגרים טקסט עברי (בלי זוג בתוך הקטע) נשארים מחוץ לסימון, כדי שיתהפכו כרגיל בעברית.
     */
    static String markCode(String t) {
        java.util.regex.Matcher m = CODE_RUN.matcher(t);
        StringBuilder b = new StringBuilder();
        int last = 0;
        while (m.find()) {
            String run = m.group();
            int s = m.start(), e = m.end();
            // סימני פיסוק של המשפט בקצוות, וסוגריים בלי זוג (שסוגרים טקסט עברי)
            for (boolean changed = true; changed && e > s; ) {
                changed = false;
                char end = t.charAt(e - 1), first = t.charAt(s);
                String word = t.substring(s, e);
                if (".,: ".indexOf(end) >= 0 || end == '(' || end == '/' && e - 2 >= s && t.charAt(e - 2) == ' '
                        || end == ')' && count(t, s, e, '(') < count(t, s, e, ')')
                        || end == ';' && word.matches("\\w+;") && !word.matches("(return|break|continue);")) {
                    e--;
                    changed = true;
                } else if (first == ' ' || first == ')' || first == '(' && count(t, s, e, '(') > count(t, s, e, ')')) {
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

    /**
     * מסמן קטע קוד בתוך ההודעה (`...`). בהצגה כל סימון כזה הופך ל-LRM משני הצדדים, כדי שבתוך טקסט עברי (מימין לשמאל)
     * הקוד יוצג משמאל לימין והסוגריים לא יתהפכו (MentorProblem).
     */
    static String c(Object code) {
        return "`" + code + "`";
    }

    /** הטקסט של הצומת כמו שהתלמיד כתב אותו (עם הרווחים שלו) */
    private String text(ASTNode n) {
        return src.text.substring(n.getStartPosition(), n.getStartPosition() + n.getLength());
    }

    /** פיצול רשימת טיפוסים בפסיקים (לא בתוך < >) */
    static List<String> split(String s) {
        List<String> out = new ArrayList<String>();
        if (s == null || s.trim().isEmpty()) return out;
        int depth = 0, from = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '<') depth++;
            else if (c == '>') depth--;
            else if (c == ',' && depth == 0) { out.add(s.substring(from, i).trim()); from = i + 1; }
        }
        out.add(s.substring(from).trim());
        return out;
    }

    /** השם הקרוב ביותר (מרחק עריכה 1, או 2 לשמות ארוכים) — או null */
    static String closest(String name, Iterable<String> names) {
        String best = null;
        int max = name.length() <= 3 ? 1 : 2, bd = max + 1;
        for (String n : names) {
            if (n.equals(name)) continue;
            int d = distance(name.toLowerCase(), n.toLowerCase());
            if (d < bd) { bd = d; best = n; }
        }
        return best;
    }

    static int distance(String a, String b) {
        int[] prev = new int[b.length() + 1], cur = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) prev[j] = j;
        for (int i = 1; i <= a.length(); i++) {
            cur[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int c = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                cur[j] = Math.min(Math.min(cur[j - 1] + 1, prev[j] + 1), prev[j - 1] + c);
            }
            int[] t = prev; prev = cur; cur = t;
        }
        return prev[b.length()];
    }
}
