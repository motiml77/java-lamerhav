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

    /** הסבר בעברית לבעיה של הקומפיילר — או null אם אין תרגום */
    Mentor.Finding explain(IProblem p) {
        int id = p.getID() & IProblem.IgnoreCategoriesMask;
        String[] a = args(p);
        Spot s;
        try {
            s = special(id, p, a);
        } catch (RuntimeException e) {
            s = null; // הסבר מיוחד שנכשל — נשארים עם התרגום הרגיל
        }
        String msg;
        int start = p.getSourceStart(), end = p.getSourceEnd();
        if (s != null) {
            msg = s.message;
            if (s.start >= 0) { start = s.start; end = s.end; }
        } else {
            String t = template(id);
            if (t == null) return null;
            msg = fill(t, a);
        }
        start = Math.max(0, Math.min(start, src.text.length() - 1));
        end = Math.max(start, Math.min(end, src.text.length() - 1));
        int line = src.text.isEmpty() ? 1 : src.line(start);
        if (p.getSourceLineNumber() > 0 && line != p.getSourceLineNumber()) {
            msg += " (`Eclipse` מסמנת את השגיאה בשורה " + p.getSourceLineNumber() + ")";
        }
        return new Mentor.Finding(start, end, line, p.isError() ? Mentor.ERROR : Mentor.WARNING, msg, "jdt:" + id);
    }

    /** הסבר ומקום מדויקים (start < 0 = המקום של Eclipse) */
    static final class Spot {
        final int start, end;
        final String message;

        Spot(int start, int end, String message) {
            this.start = start;
            this.end = end;
            this.message = message;
        }

        Spot(String message) {
            this(-1, -1, message);
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
            case 234: return "==".equals(arg(a, 0)) ? new Spot("כותבים `==` צמוד, בלי רווח בין שני הסימנים") : null;
            case 235: return invalid(p, a);
            case 205: case 210: case 236: case 238: return outsideMethod(p);
            case 220: case 250: return missingBrace(p);
            case 253: return charLiteral(p);
            case 254: return new Spot("בתוך מחרוזת, `\\` מתחיל תו מיוחד (`\\n` שורה חדשה, `\\t` טאב, `\\\"` מרכאות). כדי לכתוב את התו `\\` עצמו כותבים `\\\\`");
            case 255: return new Spot("תו לא חוקי בקוד — אולי מרכאות או גרשיים בעברית (״ ׳) או תו מוסתר שהועתק מ-Word או מאתר. מוחקים ומקלידים מחדש באנגלית");
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
            case 110: return new Spot("לבנאי לא כותבים טיפוס החזרה — עם `void` זו פעולה רגילה ולא בנאי (ולכן `new` לא ימצא אותו). מוחקים את ה-`void`");
            case 114: return new Spot(c(arg(a, 0)) + " הוא טיפוס פשוט (לא עצם), ואין לו פעולות כמו " + c(arg(a, 1) + "()")
                    + ("equals".equals(arg(a, 1)) || "compareTo".equals(arg(a, 1)) ? ". מספרים ותווים משווים עם `==` (ו-`<` `>`)" : ""));
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
            return new Spot("חסרה נקודה-פסיק (`;`) " + (last ? "בסוף שורה " + line + " — אחרי " + c(tok) : "אחרי " + c(tok) + " (שורה " + line + ")"));
        }
        if (what.equals("}")) return missingBrace(p);
        if (primitive(tok) && (what.equals("Dimensions") || src.prevCodeChar(p.getSourceStart()) == '<')) {
            return new Spot("בתוך `< >` כותבים מחלקה, לא טיפוס פשוט: " + c(boxed(tok)) + " במקום " + c(tok) + " (למשל " + c("Stack<" + boxed(tok) + ">") + ")");
        }
        int open = src.count(line, '('), close = src.count(line, ')');
        String counts = open > close ? " (בשורה " + line + " יש " + times(open) + " `(` ורק " + times(close) + " `)`)" : "";
        if (what.startsWith(")") && (construct.equals("IfStatement") || construct.equals("WhileStatement") || construct.startsWith("For"))) {
            String kw = construct.equals("IfStatement") ? "if" : construct.equals("WhileStatement") ? "while" : "for";
            if (open == 0) return new Spot("התנאי של " + c(kw) + " חייב להיות בתוך סוגריים: " + c(kw + " (...)"));
            return new Spot("חסר `)` שסוגר את התנאי של " + c(kw) + " — אחרי " + c(tok) + counts);
        }
        if (what.equals(")")) return new Spot("חסר סוגר `)` אחרי " + c(tok) + counts);
        if (what.equals("( )") || what.equals("()")) {
            String t = src.lineCode(line);
            int n = t.lastIndexOf("new ");
            String expr = n >= 0 ? t.substring(n).replaceAll("[;\\s]+$", "") : "new …";
            return new Spot("חסרים סוגריים `()` אחרי " + c(expr) + " — כך: " + c(expr + "()"));
        }
        if (what.equals("]")) return new Spot("חסר `]` — כל `[` צריך `]` שסוגר אותו");
        if (what.equals("{")) return new Spot("חסר `{` אחרי " + c(tok));
        String where = constructName(construct);
        return new Spot("שגיאת תחביר: חסר " + c(what) + " אחרי " + c(tok) + (where.isEmpty() ? "" : " (כדי להשלים את " + where + ")"));
    }

    /** "Syntax error on token X, Y expected" */
    private Spot expected(int id, IProblem p, String[] a) {
        String tok = arg(a, 0), exp = arg(a, 1);
        int line = src.line(p.getSourceStart());
        String code = src.lineCode(line);
        if (!exp.isEmpty() && tok.equalsIgnoreCase(exp) && !tok.equals(exp)) {
            return new Spot("ב-Java אותיות גדולות וקטנות שונות: כותבים " + c(exp) + " ולא " + c(tok));
        }
        if (tok.equals(",") && code.matches(".*\\bfor\\s*\\(.*")) {
            int f = src.lineStart(line) + src.lineText(line).indexOf("for");
            return new Spot(f, Math.max(f + 2, p.getSourceEnd()), "בכותרת של `for` מפרידים בין שלושת החלקים בנקודה-פסיק (`;`), לא בפסיק: "
                    + c("for (int i = 0; i < n; i++)"));
        }
        if ((tok.equals("if") || tok.equals("while")) && exp.equals("(")) {
            return new Spot("התנאי של " + c(tok) + " חייב להיות בתוך סוגריים: " + c(tok + " (...)"));
        }
        if (exp.equals(";")) {
            return new Spot("חסרה נקודה-פסיק (`;`) " + (id == 230 ? "לפני " : id == 231 ? "אחרי " : "ליד ") + c(tok));
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
            return new Spot("יש סוגר `)` מיותר" + (close > open ? " — בשורה " + line + " יש " + times(close) + " `)` ורק " + times(open) + " `(`" : ""));
        }
        if ((tok.equals(">") || tok.equals("<")) && src.codeAt(start - 1) == '=') {
            return new Spot(start - 1, start, "כותבים " + c(tok + "=") + " (קודם " + c(tok) + " ואחר כך `=`), לא " + c("=" + tok));
        }
        if (tok.equals("else")) return elseWithoutIf(p);
        if (tok.equals(";")) return new Spot("נקודה-פסיק (`;`) מיותרת כאן — מוחקים אותה");
        char before = src.prevCodeChar(start);
        if ((before == '(' || before == ',') && (primitive(tok) || isTypeName(tok)) && Character.isJavaIdentifierStart(src.nextCodeChar(p.getSourceEnd() + 1))
                && src.lineCode(line).endsWith(";")) {
            return new Spot("בקריאה לפעולה לא כותבים את הטיפוס (" + c(tok) + ") — רק את הערך או את שם המשתנה");
        }
        if (src.depth(start) == 1 && !looksLikeDeclaration(src.lineCode(line))) return outsideMethod(p);
        return new Spot(c(tok) + " מיותר כאן — מוחקים אותו (או בודקים מה חסר לפניו)");
    }

    private Spot invalid(IProblem p, String[] a) {
        String tok = arg(a, 0), construct = arg(a, 1);
        if (KEYWORDS.contains(tok) && (construct.contains("VariableDeclarator") || construct.contains("Identifier"))) return keywordName(tok);
        return outsideMethod(p);
    }

    private static Spot keywordName(String tok) {
        return new Spot(c(tok) + " היא מילה שמורה ב-Java — אי אפשר לקרוא כך למשתנה או לפעולה. בוחרים שם אחר (למשל "
                + c("my" + Character.toUpperCase(tok.charAt(0)) + tok.substring(1)) + ")");
    }

    /** שגיאת תחביר ישירות בתוך המחלקה — כנראה פקודה מחוץ לפעולה */
    private Spot outsideMethod(IProblem p) {
        int start = p.getSourceStart();
        if (start < 0 || src.depth(start) != 1) return null;
        String code = src.lineCode(src.line(start));
        if (looksLikeDeclaration(code)) return null;
        return new Spot("פקודות כותבים רק בתוך פעולה (למשל בתוך `main`). השורה הזאת נמצאת ישירות בתוך המחלקה — "
                + "או שהפעולה שמעליה נסגרה מוקדם מדי (`}` מיותר)");
    }

    private static boolean looksLikeDeclaration(String code) {
        return code.matches("(public|private|protected|static|final|abstract|class|interface|import|package|@).*")
                || code.matches("[\\w<>\\[\\], ]+\\s+\\w+\\s*(=.*)?;") || code.equals("}") || code.equals("{");
    }

    /** ה-{ שלא נסגר — לפי ההזחה */
    private Spot missingBrace(IProblem p) {
        MentorSource.Braces b = src.braces();
        if (b.unclosed.isEmpty()) return new Spot("חסר `}` — לכל `{` צריך `}` שסוגר אותו. בדקו את הבלוקים שמעל השורה הזאת");
        MentorSource.Open o = b.unclosed.get(0);
        int end = src.blockEndLine(o);
        String where = end > 0 ? "לפני שורה " + end + " (" + c(src.excerpt(end)) + ")" : "בסוף הקובץ";
        return new Spot(o.offset, o.offset, "ה-`{` שנפתח כאן (" + describe(o) + ", שורה " + o.line + ") לא נסגר — חסר `}` " + where);
    }

    /** } מיותר — ואם אפשר, השורה שבה חסר ה-{ */
    private Spot extraBrace(IProblem p) {
        MentorSource.Braces b = src.braces();
        if (b.extra.isEmpty()) return new Spot("יש `}` מיותר — מוחקים אותו, או בודקים שלא חסר `{` באחת השורות שמעליו");
        int e = b.extra.get(0);
        int le = src.line(e), ind = src.indent(le);
        for (int l = le - 1; l >= 1; l--) {
            if (src.blankLine(l)) continue;
            String code = src.lineCode(l);
            if (src.indent(l) < ind) break;
            if (src.indent(l) == ind && code.matches("(\\}\\s*)?(if|else|while|for|do|switch|try)\\b.*|.*\\)\\s*") && !code.endsWith("{") && !code.endsWith(";")) {
                int at = src.lineStart(l) + src.lineText(l).replaceAll("\\s+$", "").length() - 1;
                return new Spot(at, at, "בסוף השורה הזאת (" + c(src.excerpt(l)) + ") חסר `{` — ולכן ה-`}` בשורה " + le + " נראה מיותר");
            }
        }
        return new Spot(e, e, "ה-`}` הזה מיותר — אין `{` פתוח שהוא סוגר (בדקו גם שלא חסר `{` באחת השורות שמעליו)");
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
                return new Spot(semi, semi, "יש `;` מיד אחרי התנאי של `if` — היא סוגרת את ה-`if`, ולכן ה-`else` בשורה " + le
                        + " לא שייך לשום `if`. מוחקים את ה-`;`");
            }
            int body = 0;
            for (int k = l + 1; k < le; k++) if (!src.blankLine(k) && !src.lineCode(k).equals("}")) body++;
            if (!code.endsWith("{") && body >= 2) {
                int at = src.lineStart(l) + src.lineText(l).replaceAll("\\s+$", "").length() - 1;
                return new Spot(at, at, "ל-`if` הזה אין `{ }`, ולכן רק השורה הראשונה אחריו שייכת לו — וה-`else` בשורה " + le
                        + " כבר לא צמוד ל-`if`. עוטפים את השורות של ה-`if` ב-`{ }`");
            }
            break;
        }
        return new Spot("`else` בלי `if` לפניו — בדקו את ה-`if` שמעליו: שאין `;` אחרי התנאי, ושכל השורות שלו בתוך `{ }`");
    }

    private Spot charLiteral(IProblem p) {
        String t = token(p);
        if (t.equals("''")) return new Spot("בין גרשיים צריך תו אחד בדיוק (`'a'`). למחרוזת ריקה כותבים `\"\"`");
        if (t.length() > 3 && !t.startsWith("'\\")) {
            String inner = t.replaceAll("^'|'$", "");
            return new Spot("גרשיים `' '` הם לתו אחד בלבד. לטקסט של כמה תווים משתמשים במרכאות: " + c("\"" + inner + "\""));
        }
        return null;
    }

    private Spot unclosedString(IProblem p) {
        String t = token(p);
        if (t.indexOf('\'') > 0) return new Spot("המחרוזת התחילה ב-`\"` ונגמרה ב-`'` — מחרוזת מתחילה ונגמרת במרכאות `\"` (תו אחד — בגרשיים `' '`)");
        return new Spot("המחרוזת שמתחילה כאן לא נסגרה — חסרות מרכאות `\"` בסוף שלה (מחרוזת חייבת להיגמר באותה שורה)");
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
            return new Spot(c(name) + " לא מוכר — כותבים " + c(proper) + (proper.equalsIgnoreCase(name) ? " (ב-Java אותיות גדולות וקטנות שונות)" : ""));
        }
        if (LIB.contains(name) && !imports("unit4.collectionsLib")) {
            return new Spot(c(name) + " לא מוכרת כי חסרה שורה בראש הקובץ (לפני `public class`): " + c("import unit4.collectionsLib.*;"));
        }
        if (UTIL.contains(name)) return new Spot(c(name) + " לא מוכרת כי חסרה שורה בראש הקובץ: " + c("import java.util." + name + ";"));
        String near = closest(name, typeNames());
        if (near != null) return new Spot("אין טיפוס בשם " + c(name) + " — אולי " + c(near) + "?");
        return null;
    }

    private Spot ambiguousType(String name) {
        if (LIB.contains(name) && imports("java.util")) {
            int line = 0;
            for (Object o : cu.imports()) {
                ImportDeclaration d = (ImportDeclaration) o;
                if (d.isOnDemand() && d.getName().getFullyQualifiedName().equals("java.util")) line = cu.getLineNumber(d.getStartPosition());
            }
            return new Spot(c(name) + " קיימת גם ב-`java.util` וגם בספרייה `unit4.collectionsLib`, ובגלל `import java.util.*;`"
                    + (line > 0 ? " (שורה " + line + ")" : "") + " לא ברור לאיזו הכוונה. מוחקים את `import java.util.*;` — ואם צריך Scanner: `import java.util.Scanner;`");
        }
        return null;
    }

    private Spot unknownVariable(IProblem p, String name) {
        int u = p.getSourceStart();
        String proper = KNOWN.get(name.toLowerCase());
        if ("system".equalsIgnoreCase(name) && !name.equals("System")) return new Spot("כותבים `System` עם S גדולה (ב-Java אותיות גדולות וקטנות שונות)");
        if (name.equals("True") || name.equals("TRUE") || name.equals("False") || name.equals("FALSE")) {
            return new Spot("ב-Java כותבים " + c(name.toLowerCase()) + " באותיות קטנות");
        }
        if (name.equals("Null") || name.equals("NULL")) return new Spot("ב-Java כותבים `null` באותיות קטנות");
        if (proper != null && !proper.equals(name)) return new Spot(c(name) + " לא מוכר — כותבים " + c(proper));
        MethodDeclaration m = method(u);
        List<Decl> same = new ArrayList<Decl>();
        for (Decl d : decls()) if (d.name.equals(name) && (d.method == m || d.kind.equals("field"))) same.add(d);
        for (Decl d : same) {
            if (d.kind.equals("field")) continue;
            if (d.offset > u && inside(d.scope, u)) {
                return new Spot("המשתנה " + c(name) + " מוגדר רק בשורה " + d.line + " — אחרי השורה הזאת. מעבירים את ההגדרה לפני השימוש");
            }
        }
        for (Decl d : same) {
            if (d.kind.equals("field") || d.offset > u || inside(d.scope, u)) continue;
            if (d.kind.equals("for")) {
                return new Spot("המשתנה " + c(name) + " הוגדר בכותרת של לולאה (שורה " + d.line + "), ולכן הוא קיים רק בתוך הלולאה. "
                        + "אם צריך אותו גם אחריה — מגדירים אותו לפני הלולאה");
            }
            return new Spot("המשתנה " + c(name) + " הוגדר בתוך בלוק `{ }` (שורה " + d.line + "), ולכן הוא קיים רק עד ה-`}` שסוגר את הבלוק. "
                    + "אם צריך אותו גם כאן — מגדירים אותו לפני הבלוק");
        }
        for (Decl d : decls()) {
            if (d.name.equals(name) && d.method != null && d.method != m && !d.kind.equals("field")) {
                return new Spot("המשתנה " + c(name) + " מוגדר בפעולה " + c(d.method.getName().getIdentifier()) + " (שורה " + d.line
                        + "), ולכל פעולה יש משתנים משלה. מעבירים אותו כפרמטר, או מגדירים אותו גם כאן");
            }
        }
        Set<String> visible = new LinkedHashSet<String>();
        for (Decl d : decls()) if (d.kind.equals("field") || d.method == m && d.offset < u && inside(d.scope, u)) visible.add(d.name);
        for (String v : visible) {
            if (v.equalsIgnoreCase(name)) return new Spot("אין משתנה " + c(name) + ", יש " + c(v) + " — ב-Java אותיות גדולות וקטנות שונות");
        }
        for (MethodDeclaration md : methods()) {
            if (md.getName().getIdentifier().equals(name)) return new Spot(c(name) + " היא פעולה — כדי לקרוא לה כותבים סוגריים: " + c(name + "(...)"));
        }
        String near = closest(name, visible);
        if (near != null) return new Spot("אין משתנה בשם " + c(name) + " — אולי התכוונתם ל-" + c(near) + "?");
        if (UTIL.contains(name)) return new Spot(c(name) + " לא מוכרת — חסר בראש הקובץ: " + c("import java.util." + name + ";"));
        return new Spot("המשתנה " + c(name) + " לא מוכר — לא הוגדר (עם טיפוס, למשל " + c("int " + name + " = 0;") + ") לפני השורה הזאת");
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
        if (t.isArray()) return new Spot("למערך יש רק `length` (בלי סוגריים): " + c(qs + ".length"));
        for (String m : methodNames(t)) {
            if (m.equals(name)) return new Spot(c(name) + " היא פעולה — חסרים סוגריים: " + c(qs + "." + name + "()"));
        }
        for (String m : methodNames(t)) {
            if (m.equalsIgnoreCase(name)) return new Spot(c(name) + " היא פעולה, ובאותיות אחרות: " + c(qs + "." + m + "()"));
        }
        return null;
    }

    private Spot unknownMethod(IProblem p, String[] a) {
        String type = arg(a, 0), name = arg(a, 1);
        String simple = type.replaceAll("<.*", "");
        MethodInvocation mi = invocation(p);
        String recv = mi != null && mi.getExpression() != null ? mi.getExpression().toString() : "";
        String r = recv.isEmpty() ? "" : recv + ".";
        if (name.equals("elseif")) return new Spot("כותבים `else if` — שתי מילים, עם רווח");
        String lib = LIB_HINTS.get(simple + ":" + name.toLowerCase());
        if (lib != null) return new Spot(lib.replace("$", r) + " (ל-" + c(simple) + " יש: " + c(LIB_METHODS.get(simple)) + ")");
        if (simple.equals("PrintStream") && name.toLowerCase().startsWith("print")) {
            String right = name.toLowerCase().contains("ln") || name.toLowerCase().contains("line") ? "println" : "print";
            return new Spot("כותבים " + c(right) + " — באותיות קטנות (" + c("System.out." + right) + ")");
        }
        ITypeBinding t = null;
        if (mi != null && mi.getExpression() != null) t = mi.getExpression().resolveTypeBinding();
        else if (mi != null) t = enclosingType(mi);
        Set<String> names = t != null ? methodNames(t) : new LinkedHashSet<String>();
        for (String m : names) {
            if (m.equalsIgnoreCase(name)) return new Spot("אין פעולה " + c(name) + ", יש " + c(r + m + "()") + " — ב-Java אותיות גדולות וקטנות שונות");
        }
        if (simple.equals("String") && (name.equals("size") || name.equalsIgnoreCase("lenght") || name.equals("count"))) {
            return new Spot("אורך מחרוזת: " + c(r + "length()"));
        }
        if (LIB.contains(simple)) {
            return new Spot("ל-" + c(simple) + " אין פעולה " + c(name + "()") + ". יש רק: " + c(LIB_METHODS.get(simple)));
        }
        if (recv.isEmpty()) {
            for (Decl d : decls()) {
                if (d.name.equals(name)) return new Spot(c(name) + " הוא משתנה, לא פעולה — בלי סוגריים");
            }
        }
        String near = closest(name, names);
        if (near != null) return new Spot("אין פעולה " + c(name) + " — אולי " + c(r + near) + "?");
        if (recv.isEmpty()) return new Spot("אין במחלקה פעולה בשם " + c(name + "(" + arg(a, 2) + ")") + " — בדקו את השם, ואת מספר הערכים והטיפוסים שלהם");
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
        // $ = המשתנה ונקודה (s.) — מוחלף בהודעה
        hint("Stack", "למחסנית אין `size()`. כדי לספור: מוציאים את האיברים למחסנית עזר וסופרים, ואז מחזירים אותם (או כותבים פעולה `size` משלכם)",
                "size", "length", "count", "getsize", "len");
        hint("Stack", "בספרייה קוראים לזה `top()`: `$top()` — מחזירה את האיבר שלמעלה בלי להוציא אותו", "peek", "gettop", "head", "front", "first", "last", "peak");
        hint("Stack", "מכניסים למחסנית עם `push`: `$push(x)`", "add", "insert", "enqueue", "put", "append", "addtop");
        hint("Stack", "מוציאים מהמחסנית עם `pop()`: `$pop()`", "remove", "poll", "dequeue", "delete", "removetop", "pull");
        hint("Stack", "בודקים אם המחסנית ריקה עם `$isEmpty()`", "empty", "isempty", "isnull");
        hint("Stack", "אין `clear()` — מרוקנים בלולאה: `while (!$isEmpty()) $pop();`", "clear", "removeall");
        hint("Queue", "לתור אין `size()`. כדי לספור: מעבירים את האיברים לתור עזר וסופרים, ואז מחזירים אותם (או כותבים פעולה `size` משלכם)",
                "size", "length", "count", "getsize", "len");
        hint("Queue", "מכניסים לתור עם `insert`: `$insert(x)`", "add", "offer", "enqueue", "push", "put", "append", "insertlast", "addlast");
        hint("Queue", "מוציאים מהתור עם `remove()`: `$remove()`", "poll", "dequeue", "pop", "delete", "removefirst", "pull");
        hint("Queue", "בספרייה קוראים לזה `head()`: `$head()` — מחזירה את הראשון בתור בלי להוציא אותו",
                "peek", "front", "top", "first", "gethead", "getfront", "element", "getfirst");
        hint("Queue", "בודקים אם התור ריק עם `$isEmpty()`", "empty", "isempty", "isnull");
        hint("Queue", "אין `clear()` — מרוקנים בלולאה: `while (!$isEmpty()) $remove();`", "clear", "removeall");
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
        hint("BinNode", "אין `isLeaf()` — עלה הוא צומת בלי בנים: `!$hasLeft() && !$hasRight()`", "isleaf", "leaf");
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
            return new Spot("`Integer.parseInt` מקבלת מחרוזת (`\"5\"`) — והערך כאן כבר מספר (" + c(gs.get(0)) + "), אין צורך להמיר");
        }
        if (ps.size() != gs.size()) {
            return new Spot("הפעולה " + c(name) + " מקבלת " + count(ps.size()) + (ps.isEmpty() ? "" : " (" + c(params) + ")") + ", אבל נשלחו " + count(gs.size())
                    + (gs.isEmpty() ? "" : " (" + c(given) + ")"));
        }
        if (LIB.contains(simple) && type.contains("<") && ps.size() == 1) {
            return new Spot(c(type) + " יכולה לקבל רק " + c(ps.get(0)) + ", אבל נשלח " + c(gs.get(0)));
        }
        return new Spot("הפעולה " + c(name) + " מקבלת " + c("(" + params + ")") + ", אבל נשלח " + c("(" + given + ")") + " — בדקו את הסדר ואת הטיפוסים");
    }

    private static String times(int n) {
        return n == 1 ? "פעם אחת" : n + " פעמים";
    }

    private static String count(int n) {
        return n == 0 ? "0 ערכים" : n == 1 ? "ערך אחד" : n + " ערכים";
    }

    private Spot constructor(String[] a) {
        String type = arg(a, 0), simple = type.replaceAll("<.*", "");
        String g = type.contains("<") ? type.substring(type.indexOf('<')) : "<Integer>";
        if (simple.equals("Node")) {
            return new Spot("לבנאי של `Node` שולחים ערך: " + c("new Node" + g + "(5)") + " — או ערך וחוליה הבאה: " + c("new Node" + g + "(5, next)"));
        }
        if (simple.equals("BinNode")) {
            return new Spot("לבנאי של `BinNode` שולחים ערך: " + c("new BinNode" + g + "(5)") + " — או בן שמאלי, ערך ובן ימני: " + c("new BinNode" + g + "(left, 5, right)"));
        }
        if (simple.equals("Stack") || simple.equals("Queue")) {
            return new Spot("יוצרים " + c(simple) + " ריק" + (simple.equals("Stack") ? "ה" : "") + ", בלי ערכים בסוגריים: " + c("new " + simple + g + "()")
                    + " — ואחר כך מכניסים עם " + c(simple.equals("Stack") ? "push" : "insert"));
        }
        for (AbstractTypeDeclaration t : types()) {
            if (!(t instanceof TypeDeclaration) || !t.getName().getIdentifier().equals(simple)) continue;
            for (MethodDeclaration m : ((TypeDeclaration) t).getMethods()) {
                if (!m.isConstructor() && m.getName().getIdentifier().equals(simple)) {
                    return new Spot("ל-" + c(simple) + " אין בנאי כזה: בשורה " + line(m) + " כתוב טיפוס החזרה לפני " + c(simple) + " — ולכן זו פעולה רגילה ולא בנאי. מוחקים את ה-"
                            + c(m.getReturnType2()) + " שלפני השם");
                }
            }
            List<String> ctors = new ArrayList<String>();
            for (MethodDeclaration m : ((TypeDeclaration) t).getMethods()) {
                if (!m.isConstructor()) continue;
                List<String> ps = new ArrayList<String>();
                for (Object o : m.parameters()) ps.add(o.toString());
                ctors.add(c("(" + String.join(", ", ps) + ")"));
            }
            if (ctors.isEmpty()) return new Spot("ל-" + c(simple) + " אין בנאי שמקבל ערכים — רק " + c("new " + simple + "()"));
            return new Spot("הבנאי של " + c(simple) + " מקבל " + String.join(" או ", ctors) + ", אבל נשלח " + c("(" + arg(a, 1) + ")"));
        }
        return null;
    }

    private Spot mismatch(IProblem p, String from, String to) {
        ASTNode n = node(p);
        String expr = token(p);
        if (n instanceof Assignment && ((Assignment) n).getOperator() == Assignment.Operator.ASSIGN && conditionOf(n)) {
            Assignment as = (Assignment) n;
            return new Spot("בתנאי כתבתם `=` (השמה). להשוואה כותבים `==`: " + c(as.getLeftHandSide() + " == " + as.getRightHandSide()));
        }
        if (from.equals("void")) {
            return new Spot(c(expr) + " לא מחזירה ערך (היא `void`), ולכן אי אפשר לשים אותה במשתנה או בחישוב");
        }
        boolean fromDec = from.equals("double") || from.equals("float") || from.equals("Double");
        if (fromDec && (to.equals("int") || to.equals("long") || to.equals("Integer"))) {
            return new Spot(c(expr) + " הוא מספר עשרוני (" + c(from) + "), והמשתנה הוא " + c(to) + "." + (expr.contains("Math.") ? " (`Math.sqrt` ו-`Math.pow` מחזירות `double`.)" : "")
                    + " כדי לקצץ את החלק העשרוני: " + c("(int) (" + expr + ")") + "; אם צריך את השבר — מגדירים את המשתנה כ-`double`");
        }
        if (from.equals("double") && to.equals("float")) {
            return new Spot("מספר עם נקודה (" + c(expr) + ") הוא `double`. ל-`float` מוסיפים f: " + c(expr + "f") + " — או משתמשים ב-`double`");
        }
        if (from.equals("String") && numeric(to)) {
            if (expr.matches("\"-?[0-9.]+\"")) return new Spot("כותבים את המספר בלי מרכאות: " + c(expr.replace("\"", "")) + " (במרכאות זו מחרוזת)");
            return new Spot(c(expr) + " היא מחרוזת (טקסט), לא מספר. אם יש בה מספר: " + c("Integer.parseInt(" + expr + ")"));
        }
        if (to.equals("String") && (numeric(from) || from.equals("boolean"))) {
            return new Spot(c(expr) + " הוא " + c(from) + ", לא מחרוזת. כדי להפוך למחרוזת: " + c("\"\" + " + expr) + " או " + c("String.valueOf(" + expr + ")"));
        }
        if (from.equals("String") && (to.equals("char") || to.equals("Character"))) {
            if (expr.matches("\".\"")) return new Spot("תו אחד כותבים בגרשיים: " + c("'" + expr.charAt(1) + "'") + " (במרכאות " + c(expr) + " זו מחרוזת)");
            return new Spot(c(expr) + " היא מחרוזת, לא תו. התו הראשון שלה: " + c(expr + ".charAt(0)"));
        }
        if ((from.equals("char") || from.equals("Character")) && to.equals("String")) {
            return new Spot(c(expr) + " הוא תו (`char`), לא מחרוזת. כדי לקבל מחרוזת: " + c("\"\" + " + expr));
        }
        if (to.equals("boolean") && numeric(from)) {
            return new Spot("תנאי (`boolean`) הוא `true` או `false`, לא מספר — כנראה חסרה השוואה (למשל " + c(expr + " > 0") + ")");
        }
        String fs = from.replaceAll("<.*", ""), ts = to.replaceAll("<.*", "");
        if ((fs.equals("Node") || fs.equals("BinNode")) && !ts.equals(fs)) {
            return new Spot(c(expr) + " הוא " + (fs.equals("Node") ? "חוליה (`Node`)" : "צומת (`BinNode`)") + ", לא הערך שבתוכו. הערך: " + c(expr + ".getValue()"));
        }
        if ((fs.equals("Stack") || fs.equals("Queue")) && !ts.equals(fs)) {
            boolean st = fs.equals("Stack");
            return new Spot(c(expr) + " " + (st ? "היא מחסנית" : "הוא תור") + ", לא ערך. ערך מתוכ" + (st ? "ה" : "ו") + ": "
                    + (st ? c(expr + ".pop()") + " או " + c(expr + ".top()") : c(expr + ".remove()") + " או " + c(expr + ".head()")));
        }
        if ((ts.equals("Node") || ts.equals("BinNode")) && !fs.equals(ts) && !fs.equals("null")) {
            return new Spot("כדי לשים ערך בחוליה חדשה: " + c("new " + to + "(" + expr + ")"));
        }
        if (from.equals("Object") && n instanceof MethodInvocation) {
            Expression e = ((MethodInvocation) n).getExpression();
            ITypeBinding t = e != null ? e.resolveTypeBinding() : null;
            if (t != null && t.isRawType()) {
                String s = t.getErasure().getName();
                return new Spot(c(expr) + " מחזירה `Object` כי " + c(e) + " הוגדר בלי טיפוס בתוך `< >`. מגדירים כך: " + c(s + "<Integer> " + e + " = new " + s + "<Integer>();"));
            }
        }
        return null;
    }

    private Spot incompatible(String a, String b) {
        if ((a.equals("char") && b.equals("String")) || (a.equals("String") && b.equals("char"))) {
            return new Spot("משווים תו (`char`) למחרוזת (`String`). תו כותבים בגרשיים: `'a'` (ובמרכאות `\"a\"` זו מחרוזת)");
        }
        if (a.equals("String") && numeric(b) || b.equals("String") && numeric(a)) {
            return new Spot("משווים מחרוזת למספר. אם במחרוזת יש מספר: `Integer.parseInt(s) == 5`; או משווים מחרוזות: `s.equals(\"5\")`");
        }
        String as = a.replaceAll("<.*", ""), bs = b.replaceAll("<.*", "");
        if ((as.equals("Node") || as.equals("BinNode")) && !bs.equals(as) || (bs.equals("Node") || bs.equals("BinNode")) && !as.equals(bs)) {
            return new Spot("משווים חוליה לערך. הערך שבחוליה: `p.getValue()`");
        }
        return null;
    }

    private Spot operator(IProblem p, String op, String types) {
        boolean cmp = op.equals("<") || op.equals(">") || op.equals("<=") || op.equals(">=");
        if (cmp && types.startsWith("boolean")) {
            ASTNode n = node(p);
            if (n instanceof InfixExpression && ((InfixExpression) n).getLeftOperand() instanceof InfixExpression) {
                InfixExpression e = (InfixExpression) n, l = (InfixExpression) e.getLeftOperand();
                return new Spot("אי אפשר לשרשר השוואות כמו במתמטיקה. כותבים: " + c(l + " && " + l.getRightOperand() + " " + op + " " + e.getRightOperand()));
            }
            return new Spot("אי אפשר לשרשר השוואות כמו במתמטיקה (`a < b < c`). כותבים: `a < b && b < c`");
        }
        if (cmp && types.contains("String")) return new Spot("מחרוזות משווים לפי סדר עם `compareTo`: `s1.compareTo(s2) < 0` (ולשוויון: `s1.equals(s2)`)");
        if ((op.equals("&&") || op.equals("||") || op.equals("!")) && (types.contains("int") || types.contains("double"))) {
            return new Spot(c(op) + " עובד רק על תנאים (`true` / `false`). כנראה חסרה השוואה — למשל `x != 0`");
        }
        if (types.contains("Node") || types.contains("BinNode")) return new Spot("אי אפשר לחשב " + c(op) + " על חוליה — הערך שבתוכה: `getValue()`");
        if (types.contains("Stack") || types.contains("Queue")) {
            return new Spot("אי אפשר לחשב " + c(op) + " על מחסנית או תור — מוציאים ערך (`pop` / `remove`) או מסתכלים עליו (`top` / `head`)");
        }
        if (types.contains("String") && !op.equals("+")) return new Spot("על מחרוזות אין " + c(op) + ". אם במחרוזת יש מספר: `Integer.parseInt(...)`");
        return null;
    }

    // ---------------------------------------------------------------- פעולות

    private Spot missingReturn(IProblem p, String type) {
        ASTNode n = node(p);
        if (n instanceof ReturnStatement || n != null && n.getParent() instanceof ReturnStatement) {
            return new Spot("`return` בלי ערך — הפעולה צריכה להחזיר " + c(type) + ": " + c("return " + sample(type) + ";"));
        }
        MethodDeclaration m = n instanceof MethodDeclaration ? (MethodDeclaration) n : method(p.getSourceStart());
        if (m == null || m.getBody() == null) return null;
        String name = m.getName().getIdentifier();
        Block body = m.getBody();
        int close = body.getStartPosition() + body.getLength() - 1;
        List<?> st = body.statements();
        Statement last = st.isEmpty() ? null : (Statement) st.get(st.size() - 1);
        String ret = c("return " + sample(type) + ";");
        String what;
        if (last instanceof IfStatement) {
            what = "אם התנאי של ה-`if` בשורה " + line(last) + " לא מתקיים — מגיעים לסוף " + c(name) + " בלי `return`. מוסיפים כאן, לפני ה-`}`: " + ret
                    + " (או `else` עם `return`)";
        } else if (last instanceof ForStatement || last instanceof WhileStatement || last instanceof EnhancedForStatement || last instanceof DoStatement) {
            what = "אם הלולאה בשורה " + line(last) + " מסתיימת (או לא מתבצעת בכלל) — מגיעים לסוף " + c(name) + " בלי `return`. מוסיפים כאן, אחרי הלולאה: " + ret;
        } else {
            what = "בסוף " + c(name) + " חסר `return` עם ערך מטיפוס " + c(type) + " (למשל " + ret + ")";
        }
        return new Spot(close, close, what);
    }

    private Spot voidReturn(IProblem p, String type) {
        MethodDeclaration m = method(p.getSourceStart());
        if (m == null) return null;
        String name = m.getName().getIdentifier();
        if (name.equals("main")) return new Spot("`main` תמיד `void` ולא מחזירה ערך. כדי להציג ערך: `System.out.println(...)`");
        return new Spot(c(name) + " מוגדרת `void` (לא מחזירה כלום), אבל כאן יש `return` עם ערך. אם היא צריכה להחזיר ערך — משנים בכותרת (שורה " + line(m)
                + ") את `void` ל-" + (type.isEmpty() ? "הטיפוס של הערך" : c(type)) + "; אחרת כותבים רק `return;`");
    }

    private Spot staticContext(int id, IProblem p, String[] a) {
        MethodDeclaration m = method(p.getSourceStart());
        String in = m != null ? m.getName().getIdentifier() : "הפעולה";
        if (id == 200) {
            return new Spot(c(in) + " היא `static`, ולכן אין בה " + c(arg(a, 0)) + " (אין עצם נוכחי). יוצרים עצם (" + c("X x = new X();") + ") ופונים דרכו");
        }
        if (id == 201) {
            String target = arg(a, 1), owner = arg(a, 0).replaceAll("<.*", "");
            if (LIB.contains(owner)) {
                String var = owner.equals("Stack") ? "s" : owner.equals("Queue") ? "q" : owner.equals("Node") ? "p" : "t";
                return new Spot("קוראים ל-" + c(target) + " דרך משתנה (עצם), לא דרך שם המחלקה " + c(owner) + ": קודם " + c(owner + "<Integer> " + var + " = new " + owner
                        + "<Integer>(" + (owner.equals("Node") || owner.equals("BinNode") ? "5" : "") + ");") + " ואז " + c(var + "." + target + "(...)"));
            }
            for (MethodDeclaration md : methods()) {
                if (md.getName().getIdentifier().equals(target) && !md.isConstructor()) {
                    return new Spot(c(in) + " היא `static`, ו-" + c(target) + " (שורה " + line(md) + ") לא — לכן אי אפשר לקרוא לה ישירות. מוסיפים `static` לכותרת שלה: "
                            + c("public static …") + " (או יוצרים עצם וקוראים דרכו)");
                }
            }
            return new Spot(c(in) + " היא `static`, ו-" + c(target) + " לא. מוסיפים `static` לכותרת של " + c(target) + ", או יוצרים עצם וקוראים דרכו");
        }
        String f = arg(a, 0);
        for (Decl d : decls()) {
            if (d.name.equals(f) && d.kind.equals("field")) {
                return new Spot(c(f) + " (שורה " + d.line + ") היא תכונה של עצם, ו-" + c(in) + " היא `static`. מגדירים את המשתנה בתוך " + c(in)
                        + ", או מוסיפים `static` להגדרה שלו");
            }
        }
        return null;
    }

    private Spot uninitialized(IProblem p, String name) {
        Decl d = visible(name, p.getSourceStart());
        if (d == null) return null;
        String init = sample(d.type);
        if (init.equals("null") && !d.type.endsWith("[]")) init = "new " + d.type + "(" + (d.type.startsWith("Node") || d.type.startsWith("BinNode") ? "…" : "") + ")";
        return new Spot("המשתנה " + c(name) + " הוגדר בשורה " + d.line + " בלי ערך, ויש מסלול (למשל כשתנאי לא מתקיים) שבו הוא לא מקבל ערך עד כאן. "
                + "נותנים לו ערך כבר בהגדרה: " + c(d.type + " " + name + " = " + init + ";"));
    }

    private Spot duplicate(IProblem p, String name) {
        int u = p.getSourceStart();
        MethodDeclaration m = method(u);
        Decl first = null;
        for (Decl d : decls()) if (d.name.equals(name) && d.method == m && d.offset < u && !d.kind.equals("field") && first == null) first = d;
        if (first == null) return null;
        ASTNode n = node(p);
        String again = n != null && n.getParent() instanceof VariableDeclarationFragment ? text(n.getParent()) : name + " = …";
        return new Spot("המשתנה " + c(name) + " כבר הוגדר בשורה " + first.line + ". כדי לשנות את הערך שלו כותבים בלי הטיפוס: " + c(again + ";"));
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
        if (prev instanceof ReturnStatement) return new Spot("לפני השורה הזאת יש `return` (שורה " + l + ") — הפעולה כבר יצאה, ולכן השורה הזאת לעולם לא תתבצע");
        if (prev instanceof BreakStatement) return new Spot("לפני השורה הזאת יש `break` (שורה " + l + ") — יוצאים מהלולאה, ולכן השורה הזאת לעולם לא תתבצע");
        if (prev instanceof ContinueStatement) return new Spot("לפני השורה הזאת יש `continue` (שורה " + l + ") — עוברים לסיבוב הבא, ולכן השורה הזאת לעולם לא תתבצע");
        if (prev instanceof ThrowStatement) return new Spot("לפני השורה הזאת יש `throw` (שורה " + l + ") — ולכן השורה הזאת לעולם לא תתבצע");
        if (prev instanceof WhileStatement && ((WhileStatement) prev).getExpression() instanceof BooleanLiteral
                || prev instanceof ForStatement && ((ForStatement) prev).getExpression() == null) {
            return new Spot("הלולאה בשורה " + l + " אינסופית ואין בה `break` — אף פעם לא יוצאים ממנה, ולכן השורה הזאת לעולם לא תתבצע");
        }
        if (prev instanceof IfStatement && ((IfStatement) prev).getElseStatement() != null) {
            return new Spot("גם ה-`if` וגם ה-`else` שמתחילים בשורה " + l + " מסתיימים ב-`return` — ולכן לשורה הזאת אף פעם לא מגיעים");
        }
        return null;
    }

    private Spot selfAssign(IProblem p, String name) {
        for (Decl d : decls()) {
            if (d.name.equals(name) && d.kind.equals("field")) {
                return new Spot("גם הפרמטר וגם התכונה נקראים " + c(name) + ", וכאן שמים את הפרמטר בתוך עצמו. כדי לשים בתכונה: " + c("this." + name + " = " + name + ";"));
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
            return new Spot("בנאי נקרא בדיוק כמו המחלקה: " + c(cls) + " (כאן כתוב " + c(name) + " — ב-Java אותיות גדולות וקטנות שונות)");
        }
        return new Spot("חסר טיפוס לפני " + c(name) + ": `void` אם היא לא מחזירה כלום, או הטיפוס שהיא מחזירה (`int`, `boolean`…). למשל: "
                + c("public static void " + name + "(…)"));
    }

    private Spot semicolonAfterHeader(IProblem p) {
        ASTNode n = node(p);
        MethodDeclaration m = n instanceof MethodDeclaration ? (MethodDeclaration) n : method(p.getSourceStart());
        int at = -1;
        if (m != null) {
            int e = m.getStartPosition() + m.getLength() - 1;
            if (src.codeAt(e) == ';') at = e;
        }
        return new Spot(at, at, "בסוף הכותרת של פעולה לא שמים `;` — מוחקים אותה (ואחריה בא ה-`{` של גוף הפעולה)");
    }

    private Spot fileName(String file, String type) {
        String f = file.replaceAll(".*[/\\\\]", "");
        String fileType = f.replaceAll("\\.java$", "");
        return new Spot("הקובץ נקרא " + c(f) + " והמחלקה " + c(type) + " — השמות חייבים להיות זהים (גם באותיות גדולות וקטנות). "
                + "משנים את שם המחלקה ל-" + c(fileType) + ", או את שם הקובץ ל-" + c(type + ".java") + " (לחיצה ימנית על הקובץ ← Refactor ← Rename)");
    }

    /** שגיאות תחביר שמזהים לפי השורה כולה: מערך בסגנון C, רשימת ערכים { } אחרי ההגדרה */
    private Spot linePattern(IProblem p) {
        int l = src.line(Math.max(0, p.getSourceStart()));
        for (int k = l; k <= Math.min(l + 1, src.lines()); k++) {
            String code = src.lineCode(k);
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\w+)\\s+(\\w+)\\s*\\[\\s*(\\w+)\\s*\\]\\s*;").matcher(code);
            if (m.matches() && !m.group(1).equals("return")) {
                int at = src.firstChar(k);
                return new Spot(at, src.lineEnd(k) - 1, "ב-Java יוצרים מערך כך: " + c(m.group(1) + "[] " + m.group(2) + " = new " + m.group(1) + "[" + m.group(3) + "];")
                        + " (הסוגריים `[]` צמודים לטיפוס, והגודל נכתב אחרי `new`)");
            }
            m = java.util.regex.Pattern.compile("(\\w+)\\s*=\\s*\\{(.*)\\}\\s*;").matcher(code);
            if (m.matches()) {
                Decl d = visible(m.group(1), src.firstChar(k));
                String t = d != null && d.type.endsWith("[]") ? d.type : "int[]";
                int at = src.firstChar(k);
                return new Spot(at, src.lineEnd(k) - 1, "רשימת ערכים בסוגריים מסולסלים מותרת רק בשורת ההגדרה (" + c(t + " " + m.group(1) + " = {" + m.group(2).trim() + "};")
                        + "). אחרי ההגדרה כותבים: " + c(m.group(1) + " = new " + t + " {" + m.group(2).trim() + "};"));
            }
        }
        return null;
    }

    private Spot stringIndex(IProblem p) {
        String t = token(p);
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(.+)\\[(.+)\\]").matcher(t);
        if (!m.matches()) return null;
        return new Spot("מחרוזת היא לא מערך, אין לה `[ ]`. התו במקום " + c(m.group(2)) + ": " + c(m.group(1) + ".charAt(" + m.group(2) + ")"));
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
            return new Spot(c(field) + " היא תכונה פרטית (`private`) של " + c(simple) + " — משנים אותה דרך הפעולה: " + c(q + "." + setter + "(" + as.getRightHandSide() + ");"));
        }
        if (getter != null) return new Spot(c(field) + " היא תכונה פרטית (`private`) של " + c(simple) + " — מבחוץ משתמשים בפעולה: " + c(q + "." + getter + "()"));
        return null;
    }

    private Spot arrayMethod(IProblem p, String[] a) {
        MethodInvocation mi = invocation(p);
        String arr = mi != null && mi.getExpression() != null ? mi.getExpression().toString() : "arr";
        if (arg(a, 1).equals("length") || arg(a, 1).equals("size")) return new Spot(c(arr) + " הוא מערך — האורך שלו: " + c(arr + ".length") + " (בלי סוגריים)");
        return new Spot("למערך אין פעולות. האורך: " + c(arr + ".length") + "; תא: " + c(arr + "[i]"));
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
