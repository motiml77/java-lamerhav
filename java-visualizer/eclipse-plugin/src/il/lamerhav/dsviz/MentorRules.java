package il.lamerhav.dsviz;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.AnonymousClassDeclaration;
import org.eclipse.jdt.core.dom.ArrayAccess;
import org.eclipse.jdt.core.dom.ArrayCreation;
import org.eclipse.jdt.core.dom.Assignment;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.BreakStatement;
import org.eclipse.jdt.core.dom.CatchClause;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.ConditionalExpression;
import org.eclipse.jdt.core.dom.DoStatement;
import org.eclipse.jdt.core.dom.EmptyStatement;
import org.eclipse.jdt.core.dom.EnhancedForStatement;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.ExpressionStatement;
import org.eclipse.jdt.core.dom.FieldAccess;
import org.eclipse.jdt.core.dom.ForStatement;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.IfStatement;
import org.eclipse.jdt.core.dom.InfixExpression;
import org.eclipse.jdt.core.dom.LambdaExpression;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.NullLiteral;
import org.eclipse.jdt.core.dom.ParenthesizedExpression;
import org.eclipse.jdt.core.dom.PostfixExpression;
import org.eclipse.jdt.core.dom.PrefixExpression;
import org.eclipse.jdt.core.dom.QualifiedName;
import org.eclipse.jdt.core.dom.ReturnStatement;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.SwitchStatement;
import org.eclipse.jdt.core.dom.ThisExpression;
import org.eclipse.jdt.core.dom.ThrowStatement;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.WhileStatement;

/**
 * אזהרות על טעויות נפוצות שהקוד שלהן מתקמפל — לפי המחקר על טעויות של מתחילים ב-Java ובמבני נתונים
 * (Altadmri &amp; Brown, "37 Million Compilations", 2015; טעויות נפוצות במחסנית, תור, רשימה ועץ בקורסי מבני נתונים).
 * כל כלל מחפש תבנית ברורה בלבד (כדי לא להציף באזהרות שגויות), ומסביר בעברית מה יקרה ומה כותבים במקום.
 */
final class MentorRules extends ASTVisitor {
    private final CompilationUnit cu;
    private final MentorSource src;
    private final List<Mentor.Finding> out = new ArrayList<Mentor.Finding>();

    private MentorRules(CompilationUnit cu, MentorSource src) {
        this.cu = cu;
        this.src = src;
    }

    static List<Mentor.Finding> check(CompilationUnit cu, MentorSource src) {
        MentorRules r = new MentorRules(cu, src);
        cu.accept(r);
        return r.out;
    }

    private static String c(Object code) {
        return HebrewErrors.c(code);
    }

    /** אזהרה: משפט בעברית (main), והעצה שבסוגריים המרובעים (tip) — עם הקוד המתוקן בסוף */
    private void warn(ASTNode n, String rule, String main, String tip) {
        int s = n.getStartPosition();
        out.add(new Mentor.Finding(s, s + Math.max(0, n.getLength() - 1), cu.getLineNumber(s), Mentor.WARNING, main, tip, rule));
    }

    // ---------------------------------------------------------------- השוואות

    @Override
    public boolean visit(InfixExpression e) {
        InfixExpression.Operator op = e.getOperator();
        if ((op == InfixExpression.Operator.EQUALS || op == InfixExpression.Operator.NOT_EQUALS) && !e.hasExtendedOperands()) {
            Expression l = e.getLeftOperand(), r = e.getRightOperand();
            if (l instanceof NullLiteral || r instanceof NullLiteral) return true;
            ITypeBinding lt = l.resolveTypeBinding(), rt = r.resolveTypeBinding();
            String not = op == InfixExpression.Operator.NOT_EQUALS ? "!" : "";
            if (isString(lt) && isString(rt)) {
                warn(e, "string-eq", "השוואת מחרוזות עם " + c(op) + " בודקת אם זה אותו עצם בזיכרון, ולא אם הטקסט שווה",
                        "טעות נפוצה מאוד: עם מחרוזת שנקראה מהמקלדת ההשוואה לא תצליח גם כשהטקסט זהה. כותבים: " + c(not + l + ".equals(" + r + ")"));
            } else if (boxed(lt) && boxed(rt)) {
                warn(e, "boxed-eq", "שני הצדדים הם " + c(lt.getName()) + " (עצמים — למשל ערכים שהוצאו ממחסנית, מתור או מחוליה), ו-" + c(op) + " משווה עצמים ולא ערכים",
                        "קשה לגלות את זה: עד 127 זה עובד, ובמספרים גדולים יותר ההשוואה נכשלת גם כשהערכים שווים. שומרים קודם במשתנה "
                        + (lt.getName().equals("Integer") ? "int" : "פשוט") + ", או כותבים: " + c(not + l + ".equals(" + r + ")"));
            }
        }
        return true;
    }

    // ---------------------------------------------------------------- תנאים ולולאות

    @Override
    public boolean visit(IfStatement s) {
        if (s.getThenStatement() instanceof EmptyStatement) {
            warn(s.getThenStatement(), "if-semicolon", "יש נקודה-פסיק מיד אחרי התנאי של " + c("if"),
                    "היא כל הגוף של ה-if, ולכן מה שבא אחריה יתבצע תמיד, גם כשהתנאי לא מתקיים. מוחקים את התו: `;`");
        }
        assignmentInCondition(s.getExpression());
        if (s.getElseStatement() == null) misleadingIndent(s, s.getThenStatement(), "if");
        else if (!(s.getElseStatement() instanceof IfStatement)) misleadingIndent(s, s.getElseStatement(), "else");
        return true;
    }

    @Override
    public boolean visit(WhileStatement w) {
        if (w.getBody() instanceof EmptyStatement) {
            warn(w.getBody(), "loop-semicolon", "יש נקודה-פסיק מיד אחרי " + c("while"),
                    "הלולאה ריקה — הבלוק שאחריה לא שייך לה, ואם התנאי נכון היא לא תיגמר. מוחקים את התו: `;`");
            return true;
        }
        assignmentInCondition(w.getExpression());
        misleadingIndent(w, w.getBody(), "while");
        loopReturn(w.getBody());
        loopProgress(w, w.getExpression(), w.getBody());
        return true;
    }

    @Override
    public boolean visit(DoStatement d) {
        assignmentInCondition(d.getExpression());
        loopProgress(d, d.getExpression(), d.getBody());
        return true;
    }

    @Override
    public boolean visit(ForStatement f) {
        if (f.getBody() instanceof EmptyStatement) {
            warn(f.getBody(), "loop-semicolon", "יש נקודה-פסיק מיד אחרי " + c("for"),
                    "הלולאה ריקה, והבלוק שאחריה יתבצע רק פעם אחת, אחרי שהלולאה נגמרת. מוחקים את התו: `;`");
            return true;
        }
        misleadingIndent(f, f.getBody(), "for");
        loopReturn(f.getBody());
        offByOne(f);
        forDirection(f);
        return true;
    }

    /** for (int i = 0; i < n; i--) — התנאי מחכה שהמונה יגדל, והעדכון מקטין אותו (או להפך) */
    private void forDirection(ForStatement f) {
        Expression c = strip(f.getExpression());
        if (!(c instanceof InfixExpression) || !(((InfixExpression) c).getLeftOperand() instanceof SimpleName) || exits(f.getBody())) return;
        InfixExpression e = (InfixExpression) c;
        InfixExpression.Operator op = e.getOperator();
        boolean up = op == InfixExpression.Operator.LESS || op == InfixExpression.Operator.LESS_EQUALS;
        boolean down = op == InfixExpression.Operator.GREATER || op == InfixExpression.Operator.GREATER_EQUALS;
        if (!up && !down) return;
        IVariableBinding v = variable(e.getLeftOperand());
        if (v == null) return;
        for (Object o : f.updaters()) {
            Expression u = (Expression) o;
            int dir = 0;
            if (u instanceof PostfixExpression && v.equals(variable(((PostfixExpression) u).getOperand()))) {
                dir = ((PostfixExpression) u).getOperator() == PostfixExpression.Operator.INCREMENT ? 1 : -1;
            } else if (u instanceof PrefixExpression && v.equals(variable(((PrefixExpression) u).getOperand()))) {
                PrefixExpression.Operator po = ((PrefixExpression) u).getOperator();
                dir = po == PrefixExpression.Operator.INCREMENT ? 1 : po == PrefixExpression.Operator.DECREMENT ? -1 : 0;
            } else if (u instanceof Assignment && v.equals(variable(((Assignment) u).getLeftHandSide()))) {
                Assignment.Operator ao = ((Assignment) u).getOperator();
                dir = ao == Assignment.Operator.PLUS_ASSIGN ? 1 : ao == Assignment.Operator.MINUS_ASSIGN ? -1 : 0;
            }
            String i = v.getName();
            if (up && dir < 0 || down && dir > 0) {
                warn(u, "for-direction", "התנאי מחכה ש-" + c(i) + (up ? " יגדל" : " יקטן") + ", אבל בכל סיבוב הוא " + (up ? "קטן" : "גדל"),
                        "הלולאה לא תיגמר (או תרוץ מיליארדי פעמים). כנראה התכוונתם ל: " + c(i + (up ? "++" : "--")));
            }
        }
    }

    @Override
    public boolean visit(EnhancedForStatement f) {
        if (f.getBody() instanceof EmptyStatement) {
            warn(f.getBody(), "loop-semicolon", "יש נקודה-פסיק מיד אחרי " + c("for"),
                    "הלולאה ריקה, והבלוק שאחריה יתבצע רק פעם אחת. מוחקים את התו: `;`");
            return true;
        }
        misleadingIndent(f, f.getBody(), "for");
        loopReturn(f.getBody());
        return true;
    }

    /** if (found = true) — השמה בתוך תנאי (כשהיא boolean הקומפיילר לא מתלונן) */
    private void assignmentInCondition(Expression c) {
        Expression e = strip(c);
        if (e instanceof Assignment && ((Assignment) e).getOperator() == Assignment.Operator.ASSIGN) {
            Assignment a = (Assignment) e;
            ITypeBinding t = a.resolveTypeBinding();
            if (t != null && t.getName().equals("boolean")) {
                warn(a, "assign-in-condition", "בתנאי כתבתם השמה (=) במקום השוואה",
                        "ההשמה שמה ערך במשתנה ולא בודקת כלום. השוואה כותבים עם ==, ולמשתנה boolean מספיק: " + c("if (" + a.getLeftHandSide() + ")"));
            }
        }
    }

    /** if / while / for בלי { }, והשורה שאחרי הגוף מוזחת כאילו היא בפנים */
    private void misleadingIndent(Statement control, Statement body, String kw) {
        if (body instanceof Block || body instanceof EmptyStatement || !(control.getParent() instanceof Block)) return;
        List<?> st = ((Block) control.getParent()).statements();
        int i = st.indexOf(control);
        if (i < 0 || i + 1 >= st.size()) return;
        Statement next = (Statement) st.get(i + 1);
        int ctrlLine = cu.getLineNumber(control.getStartPosition());
        int bodyLine = cu.getLineNumber(body.getStartPosition());
        int bodyEnd = cu.getLineNumber(body.getStartPosition() + body.getLength() - 1);
        int nextLine = cu.getLineNumber(next.getStartPosition());
        if (nextLine <= bodyEnd || src.firstChar(nextLine) != next.getStartPosition()) return;
        int ctrlIndent = src.indent(ctrlLine), nextCol = src.column(next.getStartPosition());
        boolean looksInside = bodyLine > ctrlLine && src.firstChar(bodyLine) == body.getStartPosition()
                ? nextCol == src.column(body.getStartPosition()) && nextCol > ctrlIndent
                : nextCol > ctrlIndent;
        if (looksInside) {
            warn(next, "indent", "השורה הזאת מוזחת כאילו היא בתוך ה-" + c(kw) + " שבשורה " + ctrlLine + ", אבל היא לא בתוכו",
                    "בלי סוגריים מסולסלים רק הפקודה הראשונה שייכת ל-" + kw + ", והשורה הזאת תתבצע תמיד. עוטפים את שתי השורות: `{ ... }`");
        }
    }

    /** return בתוך לולאה בלי תנאי, או if / else ששניהם מחזירים — הלולאה עוצרת בסיבוב הראשון */
    private void loopReturn(Statement body) {
        if (!(body instanceof Block)) return;
        List<?> st = ((Block) body).statements();
        for (Object o : st) {
            if (o instanceof ReturnStatement) {
                warn((ASTNode) o, "loop-once", "ה-return בתוך הלולאה בלי תנאי — הלולאה תתבצע פעם אחת בלבד",
                        "אם צריך לעבור על כל האיברים, ה-return בא אחרי הלולאה");
                return;
            }
        }
        if (st.isEmpty() || !(st.get(st.size() - 1) instanceof IfStatement)) return;
        IfStatement last = (IfStatement) st.get(st.size() - 1);
        Statement els = last.getElseStatement();
        if (els != null && endsWithReturn(last.getThenStatement()) && endsWithReturn(els)) {
            Statement r = lastStatement(els);
            warn(r, "loop-if-else-return", "גם ב-if וגם ב-else יש return — כבר בסיבוב הראשון הפעולה יוצאת",
                    "טעות נפוצה בחיפוש: בודקים רק את האיבר הראשון. משאירים את ה-return של ה-if, ואת ה-return של ה-else מעבירים לאחרי הלולאה");
        }
    }

    private static boolean endsWithReturn(Statement s) {
        if (s instanceof ReturnStatement) return true;
        if (s instanceof Block) {
            List<?> st = ((Block) s).statements();
            return !st.isEmpty() && endsWithReturn((Statement) st.get(st.size() - 1));
        }
        if (s instanceof IfStatement) {
            IfStatement i = (IfStatement) s;
            return i.getElseStatement() != null && endsWithReturn(i.getThenStatement()) && endsWithReturn(i.getElseStatement());
        }
        return false;
    }

    private static Statement lastStatement(Statement s) {
        if (s instanceof Block && !((Block) s).statements().isEmpty()) {
            List<?> st = ((Block) s).statements();
            return lastStatement((Statement) st.get(st.size() - 1));
        }
        return s;
    }

    /** for (int i = 0; i <= arr.length; i++) ... arr[i] */
    private void offByOne(ForStatement f) {
        Expression c = strip(f.getExpression());
        if (!(c instanceof InfixExpression)) return;
        InfixExpression e = (InfixExpression) c;
        if (e.getOperator() != InfixExpression.Operator.LESS_EQUALS || !(e.getLeftOperand() instanceof SimpleName)) return;
        final String i = ((SimpleName) e.getLeftOperand()).getIdentifier();
        Expression r = e.getRightOperand();
        String target = null;
        boolean array = false;
        if (r instanceof QualifiedName && ((QualifiedName) r).getName().getIdentifier().equals("length")) {
            ITypeBinding t = ((QualifiedName) r).getQualifier().resolveTypeBinding();
            if (t != null && t.isArray()) { target = ((QualifiedName) r).getQualifier().toString(); array = true; }
        } else if (r instanceof FieldAccess && ((FieldAccess) r).getName().getIdentifier().equals("length")) {
            ITypeBinding t = ((FieldAccess) r).getExpression().resolveTypeBinding();
            if (t != null && t.isArray()) { target = ((FieldAccess) r).getExpression().toString(); array = true; }
        } else if (r instanceof MethodInvocation && ((MethodInvocation) r).getName().getIdentifier().equals("length") && ((MethodInvocation) r).getExpression() != null
                && isString(((MethodInvocation) r).getExpression().resolveTypeBinding())) {
            target = ((MethodInvocation) r).getExpression().toString();
        }
        if (target == null) return;
        final String t = target;
        final boolean[] used = new boolean[1];
        f.getBody().accept(new ASTVisitor() {
            @Override
            public boolean visit(ArrayAccess a) {
                if (a.getArray().toString().equals(t) && a.getIndex() instanceof SimpleName && ((SimpleName) a.getIndex()).getIdentifier().equals(i)) used[0] = true;
                return true;
            }

            @Override
            public boolean visit(MethodInvocation m) {
                if (m.getName().getIdentifier().equals("charAt") && m.getExpression() != null && m.getExpression().toString().equals(t) && m.arguments().size() == 1
                        && m.arguments().get(0) instanceof SimpleName && ((SimpleName) m.arguments().get(0)).getIdentifier().equals(i)) used[0] = true;
                return true;
            }
        });
        if (used[0]) {
            warn(e, "off-by-one", "התנאי מגיע עד " + c(r) + ", אבל האינדקס האחרון הוא אחד פחות",
                    "בסיבוב האחרון תהיה שגיאה (" + (array ? "ArrayIndexOutOfBoundsException" : "StringIndexOutOfBoundsException") + "). כותבים: " + c(i + " < " + r));
        }
    }

    // ---------------------------------------------------------------- לולאות שלא נגמרות / שנופלות

    /** לולאה על מחסנית / תור / חוליה / מונה — שבתוכה התנאי אף פעם לא משתנה, או שמוציאים יותר מדי */
    private void loopProgress(Statement loop, Expression cond, Statement body) {
        Expression c = strip(cond);
        boolean handled = false;
        for (Expression part : conjuncts(c)) {
            part = strip(part);
            if (part instanceof PrefixExpression && ((PrefixExpression) part).getOperator() == PrefixExpression.Operator.NOT) {
                MethodInvocation m = call(strip(((PrefixExpression) part).getOperand()), "isEmpty");
                IVariableBinding v = m != null ? variable(m.getExpression()) : null;
                String kind = v != null ? lib(v.getType()) : null;
                if ("Stack".equals(kind) || "Queue".equals(kind)) { structureLoop(cond, body, v, kind); handled = true; }
            } else if (call(part, "isEmpty") != null && conjuncts(c).size() == 1) {
                MethodInvocation m = call(part, "isEmpty");
                IVariableBinding v = variable(m.getExpression());
                String kind = v != null ? lib(v.getType()) : null;
                if ("Stack".equals(kind) || "Queue".equals(kind)) {
                    Calls k = calls(body, v);
                    if (!k.removes.isEmpty() || !k.peeks.isEmpty()) {
                        warn(cond, "loop-inverted", "הלולאה רצה כל עוד " + c(v.getName()) + " ריק" + ("Stack".equals(kind) ? "ה" : "") + " — ובתוכה מוציאים ממנ"
                                + ("Stack".equals(kind) ? "ה" : "ו"), "כנראה התנאי הפוך: " + c("!" + m));
                    }
                    handled = true;
                }
            } else {
                IVariableBinding v = nodeVariable(part);
                if (v != null) { nodeLoop(cond, body, v); handled = true; }
            }
        }
        if (!handled) counterLoop(cond, body);
    }

    private void structureLoop(Expression cond, Statement body, IVariableBinding v, String kind) {
        boolean stack = kind.equals("Stack");
        String x = v.getName(), rem = stack ? "pop" : "remove", it = stack ? "היא" : "הוא";
        Calls k = calls(body, v);
        if (k.removes.isEmpty() && !k.assigned && !k.escapes && !exits(body)) {
            warn(cond, "loop-no-progress", "הלולאה רצה כל עוד " + c(x) + " לא " + (stack ? "ריקה" : "ריק") + ", אבל בתוכה אף פעם לא מוציאים ממנ" + (stack ? "ה" : "ו"),
                    it + " לא " + (stack ? "תתרוקן" : "יתרוקן") + " והלולאה לא תיגמר. מוציאים בכל סיבוב: " + c(x + "." + rem + "()"));
            return;
        }
        if (k.assigned || exits(body)) return;
        MethodInvocation firstRemove = first(k.removes, body);
        MethodInvocation add = first(k.adds, body);
        if (firstRemove != null && add != null) {
            warn(add, "loop-rotate", "בכל סיבוב מוציאים מ-" + c(x) + " ומכניסים אלי" + (stack ? "ה" : "ו") + " בחזרה — " + (stack ? "היא אף פעם לא תתרוקן" : "הוא אף פעם לא יתרוקן"),
                    "הלולאה לא תיגמר. מעבירים ל" + (stack ? "מחסנית" : "תור") + " עזר (ואחר כך מחזירים), או סופרים סיבובים לפי מספר האיברים");
            return;
        }
        if (firstRemove != null) {
            for (MethodInvocation m : k.all) {
                if (m.getStartPosition() <= firstRemove.getStartPosition() || conditional(m, body)) continue;
                String n = m.getName().getIdentifier();
                if (n.equals(rem) || n.equals(stack ? "top" : "head")) {
                    warn(m, "double-pop", "בכל סיבוב יש שתי הוצאות מ-" + c(x) + " (שורות " + cu.getLineNumber(firstRemove.getStartPosition()) + " ו-" + cu.getLineNumber(m.getStartPosition())
                            + "), אבל התנאי של הלולאה בודק רק שיש איבר אחד", "כשנשאר איבר אחד, ההוצאה השנייה תיכשל. בודקים שוב לפניה: " + c("!" + x + ".isEmpty()"));
                    return;
                }
            }
        }
    }

    private void nodeLoop(Expression cond, Statement body, IVariableBinding v) {
        Calls k = calls(body, v);
        if (k.assigned || k.escapes || exits(body)) return;
        String p = v.getName();
        String step = "BinNode".equals(lib(v.getType())) ? c(p + " = " + p + ".getLeft();") + " (או " + c("getRight()") + ")" : c(p + " = " + p + ".getNext();");
        warn(cond, "node-no-progress", c(p) + " לא מתקדם בתוך הלולאה — היא לא תיגמר", "בסוף כל סיבוב מתקדמים: " + step);
    }

    /** while (i < n) בלי שום שינוי ב-i או ב-n בתוך הלולאה */
    private void counterLoop(Expression cond, Statement body) {
        final Set<IVariableBinding> vars = new LinkedHashSet<IVariableBinding>();
        final boolean[] complex = new boolean[1];
        cond.accept(new ASTVisitor() {
            @Override
            public boolean visit(SimpleName n) {
                IBinding b = n.resolveBinding();
                if (b instanceof IVariableBinding) {
                    IVariableBinding v = (IVariableBinding) b;
                    if (v.isField() || !v.getType().isPrimitive()) complex[0] = true;
                    else vars.add(v);
                }
                return false;
            }

            @Override
            public boolean visit(MethodInvocation m) {
                complex[0] = true;
                return false;
            }

            @Override
            public boolean visit(QualifiedName q) {
                complex[0] = true;
                return false;
            }

            @Override
            public boolean visit(ArrayAccess a) {
                complex[0] = true;
                return false;
            }

            @Override
            public boolean visit(FieldAccess f) {
                complex[0] = true;
                return false;
            }
        });
        if (complex[0] || vars.isEmpty() || exits(body)) return;
        final boolean[] changed = new boolean[1];
        body.accept(new ASTVisitor() {
            @Override
            public boolean visit(Assignment a) {
                if (vars.contains(variable(a.getLeftHandSide()))) changed[0] = true;
                return true;
            }

            @Override
            public boolean visit(PostfixExpression p) {
                if (vars.contains(variable(p.getOperand()))) changed[0] = true;
                return true;
            }

            @Override
            public boolean visit(PrefixExpression p) {
                PrefixExpression.Operator op = p.getOperator();
                if ((op == PrefixExpression.Operator.INCREMENT || op == PrefixExpression.Operator.DECREMENT) && vars.contains(variable(p.getOperand()))) changed[0] = true;
                return true;
            }

            @Override
            public boolean visit(MethodInvocation m) {
                // לוגיקה עם קלט מהמשתמש / אקראיות — לא מנחשים
                if (m.getName().getIdentifier().startsWith("next")) changed[0] = true;
                return true;
            }
        });
        if (changed[0]) return;
        List<String> names = new ArrayList<String>();
        for (IVariableBinding v : vars) names.add(v.getName());
        warn(cond, "loop-stuck", (names.size() == 1 ? "המשתנה " + c(names.get(0)) + " לא משתנה" : "אף אחד מהמשתנים בתנאי לא משתנה") + " בתוך הלולאה — היא לא תיגמר",
                names.size() == 1 ? "כנראה חסר קידום בסוף הלולאה: " + c(names.get(0) + "++") : "משנים בתוך הלולאה את אחד המשתנים: " + c(String.join(", ", names)));
    }

    // ---------------------------------------------------------------- ערך שהולך לאיבוד

    private static final Set<String> STRING_NEW = new HashSet<String>(Arrays.asList("toUpperCase", "toLowerCase", "trim", "substring", "replace", "concat",
            "strip", "repeat", "replaceAll"));
    private static final Pattern SIDE_EFFECT = Pattern.compile("^(set|add|remove|insert|delete|push|pop|put|update|fill|print|show|sort|swap|reverse|move|clear|build|create|make|init|read|write|change|replace|merge|split|transfer|empty|shift|rotate|append|draw|run|do|apply|load|save)",
            Pattern.CASE_INSENSITIVE);

    @Override
    public boolean visit(ExpressionStatement s) {
        if (!(s.getExpression() instanceof MethodInvocation)) return true;
        MethodInvocation mi = (MethodInvocation) s.getExpression();
        IMethodBinding mb = mi.resolveMethodBinding();
        if (mb == null) return true;
        ITypeBinding rt = mb.getReturnType();
        if (rt == null || rt.getName().equals("void")) return true;
        String name = mb.getName();
        ITypeBinding dc = mb.getDeclaringClass();
        String recv = mi.getExpression() != null ? mi.getExpression().toString() : "";
        String lib = lib(dc);
        if (isString(dc) && STRING_NEW.contains(name) && !recv.isEmpty()) {
            warn(mi, "string-immutable", "מחרוזת לא משתנה — " + c(name) + " מחזירה מחרוזת חדשה, והתוצאה הולכת לאיבוד",
                    "שומרים את התוצאה: " + c(recv + " = " + mi + ";"));
        } else if (lib != null) {
            switch (name) {
                case "top": case "head":
                    warn(mi, "ignored-peek", c(name) + " רק מחזירה את " + (name.equals("top") ? "האיבר העליון" : "הראשון בתור") + " בלי להוציא אותו — והערך הולך לאיבוד",
                            "כדי להשתמש בו שומרים אותו במשתנה. כדי להוציא: " + c(recv + "." + (name.equals("top") ? "pop();" : "remove();")));
                    break;
                case "isEmpty": case "hasNext": case "hasLeft": case "hasRight":
                    warn(mi, "ignored-check", c(name) + " רק בודקת ומחזירה true או false — לבד בשורה היא לא עושה כלום",
                            "משתמשים בה בתוך תנאי: " + c("if (" + mi + ")"));
                    break;
                case "getNext": case "getLeft": case "getRight":
                    warn(mi, "ignored-move", c(name) + " לא מזיזה את " + c(recv) + " — היא רק מחזירה את " + (name.equals("getNext") ? "החוליה הבאה" : "הבן"),
                            "כדי להתקדם: " + c(recv + " = " + mi + ";"));
                    break;
                case "getValue": case "getInfo":
                    warn(mi, "ignored-value", "הערך שמוחזר לא נשמר ולא מודפס", "שומרים אותו במשתנה: " + c("int x = " + mi + ";"));
                    break;
                default:
                    break;
            }
        } else if (dc != null && "java.lang.Math".equals(dc.getQualifiedName())) {
            warn(mi, "ignored-math", c("Math." + name) + " מחזירה תוצאה ולא משנה את הערך שנשלח אליה", "שומרים את התוצאה: " + c("x = " + mi + ";"));
        } else if (dc != null && dc.isFromSource()) {
            MethodDeclaration encl = enclosingMethod(s);
            IMethodBinding eb = encl != null ? encl.resolveBinding() : null;
            if (eb != null && eb.getMethodDeclaration().isEqualTo(mb.getMethodDeclaration())) {
                warn(mi, "recursion-ignored", "התוצאה של הקריאה הרקורסיבית לא נשמרת ולא מוחזרת",
                        "בדרך כלל מחזירים אותה, או מחברים אותה לתוצאה: " + c("return " + mi + ";"));
            } else if (!SIDE_EFFECT.matcher(name).find()) {
                warn(mi, "ignored-result", "הפעולה " + c(name) + " מחזירה ערך, אבל הוא לא נשמר ולא מודפס",
                        "אם התכוונתם להשתמש בו, שומרים אותו: " + c(rt.getName() + " x = " + mi + ";"));
            }
        }
        return true;
    }

    // ---------------------------------------------------------------- הדפסה ומערכים

    @Override
    public boolean visit(MethodInvocation mi) {
        String n = mi.getName().getIdentifier();
        if ((n.equals("println") || n.equals("print")) && mi.getExpression() != null && mi.getExpression().toString().equals("System.out") && mi.arguments().size() == 1) {
            Expression arg = (Expression) mi.arguments().get(0);
            ITypeBinding t = arg.resolveTypeBinding();
            if (t != null && t.isArray() && !t.getElementType().getName().equals("char")) {
                warn(arg, "print-array", "הדפסה של מערך מדפיסה כתובת בזיכרון, לא את האיברים",
                        "מדפיסים בלולאה, או בעזרת המחלקה Arrays (צריך גם import שלה): " + c("System.out.println(Arrays.toString(" + arg + "));"));
            } else if (t != null && t.isClass() && t.isFromSource() && !hasToString(t)) {
                String name = t.getErasure().getName();
                warn(arg, "print-object", "הדפסה של עצם מטיפוס " + c(name) + " מדפיסה כתובת, כי למחלקה אין toString",
                        "מוסיפים למחלקה פעולה שמחזירה את התכונות כטקסט: " + c("public String toString() { return …; }"));
            }
        }
        return true;
    }

    private static boolean hasToString(ITypeBinding t) {
        for (ITypeBinding c = t; c != null && !c.getQualifiedName().equals("java.lang.Object"); c = c.getSuperclass()) {
            for (IMethodBinding m : c.getDeclaredMethods()) if (m.getName().equals("toString") && m.getParameterTypes().length == 0) return true;
        }
        return false;
    }

    /** arr[arr.length] */
    @Override
    public boolean visit(ArrayAccess a) {
        Expression i = strip(a.getIndex());
        String arr = a.getArray().toString();
        boolean len = i instanceof QualifiedName && ((QualifiedName) i).getName().getIdentifier().equals("length") && ((QualifiedName) i).getQualifier().toString().equals(arr)
                || i instanceof FieldAccess && ((FieldAccess) i).getName().getIdentifier().equals("length") && ((FieldAccess) i).getExpression().toString().equals(arr);
        if (len) {
            warn(a, "index-length", "אינדקס שווה לאורך המערך — הוא כבר מחוץ למערך", "האינדקס האחרון הוא: " + c(arr + ".length - 1"));
        }
        return true;
    }

    // ---------------------------------------------------------------- חישובים והשמות

    @Override
    public boolean visit(VariableDeclarationFragment f) {
        IVariableBinding v = f.resolveBinding();
        if (f.getInitializer() != null && v != null && decimal(v.getType())) intDivision(f.getInitializer());
        if (f.getInitializer() != null && v != null) alias(f, v.getVariableDeclaration(), f.getInitializer());
        return true;
    }

    /** Stack<Integer> copy = s; — זה לא עותק: שני השמות הם אותה מחסנית */
    private void alias(ASTNode at, IVariableBinding target, Expression value) {
        IVariableBinding src = variable(value);
        String kind = lib(target.getType());
        if (src == null || src.equals(target) || !("Stack".equals(kind) || "Queue".equals(kind)) || !kind.equals(lib(src.getType()))) return;
        MethodDeclaration m = enclosingMethod(at);
        if (m == null || m.getBody() == null) return;
        int after = at.getStartPosition() + at.getLength();
        boolean removed = false;
        for (IVariableBinding v : new IVariableBinding[] { target, src }) {
            for (MethodInvocation r : calls(m.getBody(), v).removes) if (r.getStartPosition() > after) removed = true;
        }
        if (!removed) return;
        boolean st = kind.equals("Stack");
        String one = st ? "אותה מחסנית" : "אותו תור";
        warn(at, "alias", "השמה של " + (st ? "מחסנית" : "תור") + " למשתנה אחר לא יוצרת עותק — " + c(target.getName()) + " ו-" + c(src.getName()) + " הם " + one,
                "מה שמוציאים מאחד נעלם גם מהשני. כדי להעתיק: מעבירים את האיברים ל" + (st ? "מחסנית" : "תור") + " עזר, ומשם מכניסים לשני" + (st ? "הן" : "הם"));
    }

    @Override
    public boolean visit(Assignment a) {
        if (a.getOperator() != Assignment.Operator.ASSIGN) return true;
        IVariableBinding lv = variable(a.getLeftHandSide());
        if (lv != null) alias(a, lv, a.getRightHandSide());
        if (decimal(a.getLeftHandSide().resolveTypeBinding())) intDivision(a.getRightHandSide());
        // x =+ 1 (במקום +=)
        Expression r = a.getRightHandSide();
        if (r instanceof PrefixExpression) {
            PrefixExpression.Operator op = ((PrefixExpression) r).getOperator();
            boolean plus = op == PrefixExpression.Operator.PLUS, minus = op == PrefixExpression.Operator.MINUS;
            // רק כשהסימן צמוד ל-= (x =+ 1); x = +1 עם רווח — כנראה בכוונה
            boolean glued = src.text.charAt(r.getStartPosition() - 1) == '=';
            boolean spaceAfter = Character.isWhitespace(src.text.charAt(r.getStartPosition() + 1));
            if (glued && (plus || minus && spaceAfter)) {
                String sign = plus ? "+" : "-";
                Expression operand = ((PrefixExpression) r).getOperand();
                warn(a, "eq-plus", "כתבתם " + c("=" + sign) + " במקום " + c(sign + "="),
                        "כך שמים את הערך במקום " + (plus ? "להוסיף" : "להחסיר") + " אותו. כותבים: " + c(a.getLeftHandSide() + " " + sign + "= " + operand + ";"));
            }
        }
        // s = new Stack<...>() על פרמטר, בפעולת void
        if (a.getLeftHandSide() instanceof SimpleName && (r instanceof ClassInstanceCreation || r instanceof ArrayCreation)) {
            IVariableBinding v = variable(a.getLeftHandSide());
            MethodDeclaration m = enclosingMethod(a);
            if (v != null && v.isParameter() && m != null && m.getReturnType2() != null && m.getReturnType2().toString().equals("void")) {
                String kind = lib(v.getType());
                boolean fem = "Stack".equals(kind);
                String what = v.getType().isArray() ? "מערך" : fem ? "מחסנית" : "Queue".equals(kind) ? "תור" : kind != null ? "עצם" : null;
                if (what != null) {
                    String nu = fem ? "חדשה" : "חדש";
                    warn(a, "param-new", "השמה לפרמטר " + c(v.getName()) + " יוצרת " + what + " " + nu + " רק בתוך הפעולה",
                            "מי שקרא לפעולה לא יראה את השינוי. אם צריך " + what + " " + nu + " — מחזירים " + (fem ? "אותה" : "אותו") + ": `return`");
                }
            }
        }
        return true;
    }

    @Override
    public boolean visit(ReturnStatement r) {
        MethodDeclaration m = enclosingMethod(r);
        IMethodBinding b = m != null ? m.resolveBinding() : null;
        if (r.getExpression() != null && b != null && decimal(b.getReturnType())) intDivision(r.getExpression());
        return true;
    }

    /** double avg = sum / n; — חילוק שלמים */
    private void intDivision(Expression e) {
        e = strip(e);
        if (!(e instanceof InfixExpression)) return;
        InfixExpression d = (InfixExpression) e;
        if (d.getOperator() != InfixExpression.Operator.DIVIDE || d.hasExtendedOperands()) return;
        if (integral(d.getLeftOperand().resolveTypeBinding()) && integral(d.getRightOperand().resolveTypeBinding())) {
            warn(d, "int-division", "חילוק של שני מספרים שלמים נותן תוצאה שלמה — השבר הולך לאיבוד, עוד לפני שהיא נשמרת",
                    "למשל 7 חלקי 2 נותן 3. כדי לקבל שבר: " + c("(double) " + d.getLeftOperand() + " / " + d.getRightOperand()));
        }
    }

    // ---------------------------------------------------------------- רקורסיה ופרמטרים

    @Override
    public boolean visit(MethodDeclaration m) {
        IMethodBinding mb = m.resolveBinding();
        if (mb == null || m.getBody() == null) return true;
        final IMethodBinding decl = mb.getMethodDeclaration();
        final List<MethodInvocation> self = new ArrayList<MethodInvocation>();
        final boolean[] branches = new boolean[1];
        m.getBody().accept(new ASTVisitor() {
            @Override
            public boolean visit(MethodInvocation i) {
                IMethodBinding b = i.resolveMethodBinding();
                if (b != null && b.getMethodDeclaration().isEqualTo(decl) && (i.getExpression() == null || i.getExpression() instanceof ThisExpression)) self.add(i);
                return true;
            }

            @Override
            public boolean visit(IfStatement s) { branches[0] = true; return true; }
            @Override
            public boolean visit(ConditionalExpression s) { branches[0] = true; return true; }
            @Override
            public boolean visit(SwitchStatement s) { branches[0] = true; return true; }
            @Override
            public boolean visit(WhileStatement s) { branches[0] = true; return true; }
            @Override
            public boolean visit(ForStatement s) { branches[0] = true; return true; }
            @Override
            public boolean visit(EnhancedForStatement s) { branches[0] = true; return true; }
            @Override
            public boolean visit(DoStatement s) { branches[0] = true; return true; }
            @Override
            public boolean visit(CatchClause s) { branches[0] = true; return true; }

            @Override
            public boolean visit(InfixExpression e) {
                if (e.getOperator() == InfixExpression.Operator.CONDITIONAL_AND || e.getOperator() == InfixExpression.Operator.CONDITIONAL_OR) branches[0] = true;
                return true;
            }
        });
        String name = m.getName().getIdentifier();
        if (!self.isEmpty() && !nullBase(m, self) && !branches[0]) {
            warn(m.getName(), "no-base-case", c(name) + " קוראת לעצמה בכל מקרה — אין תנאי עצירה",
                    "היא לא תיעצר (StackOverflowError). בתחילת הפעולה בודקים מקרה פשוט ומחזירים: " + c("if (…) return …;"));
        }
        paramEmptied(m);
        return true;
    }

    /** f(t.getLeft()) בלי שום בדיקה של null / hasLeft — כשמגיעים לעלה הקריאה הבאה נופלת. מחזיר האם הזהיר */
    private boolean nullBase(MethodDeclaration m, List<MethodInvocation> self) {
        for (Object o : m.parameters()) {
            SingleVariableDeclaration p = (SingleVariableDeclaration) o;
            final IVariableBinding v = p.resolveBinding();
            String kind = v != null ? lib(v.getType()) : null;
            if (!"BinNode".equals(kind) && !"Node".equals(kind)) continue;
            MethodInvocation passed = null;
            for (MethodInvocation s : self) {
                for (Object a : s.arguments()) {
                    MethodInvocation c = a instanceof MethodInvocation ? (MethodInvocation) a : null;
                    if (c != null && v.equals(variable(c.getExpression())) && c.getName().getIdentifier().matches("getLeft|getRight|getNext")) passed = c;
                }
            }
            if (passed == null) continue;
            final boolean[] checked = new boolean[1];
            m.getBody().accept(new ASTVisitor() {
                @Override
                public boolean visit(InfixExpression e) {
                    if (e.getLeftOperand() instanceof NullLiteral || e.getRightOperand() instanceof NullLiteral) {
                        Expression other = e.getLeftOperand() instanceof NullLiteral ? e.getRightOperand() : e.getLeftOperand();
                        if (v.equals(variable(other)) || other instanceof MethodInvocation && v.equals(variable(((MethodInvocation) other).getExpression()))) checked[0] = true;
                    }
                    return true;
                }

                @Override
                public boolean visit(MethodInvocation i) {
                    if (v.equals(variable(i.getExpression())) && i.getName().getIdentifier().matches("hasLeft|hasRight|hasNext")) checked[0] = true;
                    return true;
                }
            });
            if (!checked[0]) {
                String t = v.getName();
                String what = passed.getName().getIdentifier().equals("getLeft") ? "הבן השמאלי" : passed.getName().getIdentifier().equals("getRight") ? "הבן הימני" : "החוליה הבאה";
                ITypeBinding rt = m.resolveBinding().getReturnType();
                String ret = rt.getName().equals("void") ? "return;" : "return " + HebrewErrors.sample(rt.getName()) + ";";
                warn(m.getName(), "null-base", c(m.getName()) + " נקראת עם " + what + " שיכול להיות null",
                        "בעלה של העץ (או בסוף הרשימה) הקריאה הבאה תיפול (NullPointerException). מוסיפים בתחילת הפעולה: " + c("if (" + t + " == null) " + ret));
                return true;
            }
        }
        return false;
    }

    private static final Pattern EMPTIES = Pattern.compile("empty|clear|remove|delete|pop|reverse|sort|move|transfer|split|merge|destroy", Pattern.CASE_INSENSITIVE);

    /**
     * פעולה שרק מחשבת משהו (סכום, ספירה, הדפסה...) מתוך מחסנית / תור שקיבלה, ומרוקנת אותם בדרך — של מי שקרא נשאר ריק.
     * פעולה שמחזירה מבנה חדש (למשל flatten), או שהשם שלה אומר שהיא מרוקנת / מעבירה — כנראה עושה את זה בכוונה.
     */
    private void paramEmptied(MethodDeclaration m) {
        if (EMPTIES.matcher(m.getName().getIdentifier()).find()) return;
        IMethodBinding mb = m.resolveBinding();
        ITypeBinding rt = mb != null ? mb.getReturnType() : null;
        if (rt == null || rt.isArray() || lib(rt) != null) return;
        for (Object o : m.parameters()) {
            SingleVariableDeclaration p = (SingleVariableDeclaration) o;
            final IVariableBinding v = p.resolveBinding();
            final String kind = v != null ? lib(v.getType()) : null;
            if (!"Stack".equals(kind) && !"Queue".equals(kind)) continue;
            Calls all = calls(m.getBody(), v);
            if (!all.adds.isEmpty() || all.assigned || all.escapes) continue;
            final WhileStatement[] loop = new WhileStatement[1];
            m.getBody().accept(new ASTVisitor() {
                @Override
                public boolean visit(WhileStatement w) {
                    Expression c = strip(w.getExpression());
                    if (c instanceof PrefixExpression && ((PrefixExpression) c).getOperator() == PrefixExpression.Operator.NOT) {
                        MethodInvocation i = call(strip(((PrefixExpression) c).getOperand()), "isEmpty");
                        if (i != null && v.equals(variable(i.getExpression())) && !calls(w.getBody(), v).removes.isEmpty() && loop[0] == null) loop[0] = w;
                    }
                    return true;
                }
            });
            if (loop[0] == null) continue;
            boolean stack = kind.equals("Stack");
            warn(loop[0].getExpression(), "param-emptied", "הפעולה מרוקנת את " + (stack ? "המחסנית" : "התור") + " " + c(v.getName()) + " שקיבלה כפרמטר",
                    "גם אצל מי שקרא לפעולה " + (stack ? "היא תישאר ריקה" : "הוא יישאר ריק") + ". כדי לשמור " + (stack ? "עליה" : "עליו") + ": מעבירים ל"
                    + (stack ? "מחסנית" : "תור") + " עזר, ובסוף מחזירים את האיברים");
        }
    }

    // ---------------------------------------------------------------- אחרי לולאה שמרוקנת

    @Override
    public boolean visit(Block b) {
        List<?> st = b.statements();
        for (int i = 0; i < st.size(); i++) {
            if (!(st.get(i) instanceof WhileStatement)) continue;
            WhileStatement w = (WhileStatement) st.get(i);
            Expression c = strip(w.getExpression());
            IVariableBinding v = null;
            if (c instanceof PrefixExpression && ((PrefixExpression) c).getOperator() == PrefixExpression.Operator.NOT) {
                MethodInvocation m = call(strip(((PrefixExpression) c).getOperand()), "isEmpty");
                v = m != null ? variable(m.getExpression()) : null;
                if (v != null && !"Stack".equals(lib(v.getType())) && !"Queue".equals(lib(v.getType()))) v = null;
            }
            // (חוליה שהיא null אחרי while (p != null) — את זה Eclipse כבר מסמן בעצמו)
            if (v == null || exits(w.getBody())) continue;
            int loopLine = cu.getLineNumber(w.getStartPosition());
            for (int j = i + 1; j < st.size(); j++) {
                Statement s = (Statement) st.get(j);
                Calls k = calls(s, v);
                if (k.assigned || !k.adds.isEmpty() || k.escapes) break;
                MethodInvocation use = null;
                if (!k.removes.isEmpty()) use = k.removes.get(0);
                if (!k.peeks.isEmpty() && (use == null || k.peeks.get(0).getStartPosition() < use.getStartPosition())) use = k.peeks.get(0);
                if (use != null) {
                    warn(use, "after-empty", "אחרי הלולאה בשורה " + loopLine + ", " + c(v.getName()) + " כבר " + ("Stack".equals(lib(v.getType())) ? "ריקה" : "ריק") + ", ולכן "
                            + c(use) + " תיכשל", "הלולאה הוציאה את כל האיברים. אם צריך ערך מסוים — שומרים אותו במשתנה בתוך הלולאה");
                    break;
                }
            }
        }
        return true;
    }

    // ---------------------------------------------------------------- עזרים

    /** הקריאות לפעולות של משתנה מסוים בתוך קטע קוד */
    private static final class Calls {
        final List<MethodInvocation> all = new ArrayList<MethodInvocation>();
        final List<MethodInvocation> removes = new ArrayList<MethodInvocation>();
        final List<MethodInvocation> adds = new ArrayList<MethodInvocation>();
        final List<MethodInvocation> peeks = new ArrayList<MethodInvocation>();
        boolean assigned, escapes;
    }

    private Calls calls(ASTNode root, final IVariableBinding v) {
        final Calls k = new Calls();
        root.accept(new ASTVisitor() {
            @Override
            public boolean visit(MethodInvocation m) {
                if (v.equals(variable(m.getExpression()))) {
                    String n = m.getName().getIdentifier();
                    k.all.add(m);
                    if (n.equals("pop") || n.equals("remove")) k.removes.add(m);
                    else if (n.equals("push") || n.equals("insert")) k.adds.add(m);
                    else if (n.equals("top") || n.equals("head")) k.peeks.add(m);
                }
                for (Object a : m.arguments()) if (v.equals(variable((Expression) a))) k.escapes = true;
                return true;
            }

            @Override
            public boolean visit(ClassInstanceCreation c) {
                for (Object a : c.arguments()) if (v.equals(variable((Expression) a))) k.escapes = true;
                return true;
            }

            @Override
            public boolean visit(Assignment a) {
                if (v.equals(variable(a.getLeftHandSide()))) k.assigned = true;
                if (v.equals(variable(a.getRightHandSide()))) k.escapes = true;
                return true;
            }

            @Override
            public boolean visit(VariableDeclarationFragment f) {
                if (f.getInitializer() != null && v.equals(variable(f.getInitializer()))) k.escapes = true;
                return true;
            }

            @Override
            public boolean visit(AnonymousClassDeclaration a) {
                return false;
            }

            @Override
            public boolean visit(LambdaExpression l) {
                return false;
            }
        });
        return k;
    }

    /** הראשונה ברשימה (לפי המקום) שמתבצעת בכל סיבוב (לא בתוך תנאי) — או null */
    private static MethodInvocation first(List<MethodInvocation> l, ASTNode body) {
        for (MethodInvocation m : l) if (!conditional(m, body)) return m;
        return null;
    }

    /** האם הקוד בתוך תנאי / לולאה פנימית / && — כלומר לא מתבצע בוודאות בכל סיבוב */
    private static boolean conditional(ASTNode n, ASTNode root) {
        ASTNode c = n;
        for (ASTNode p = n.getParent(); p != null && c != root; c = p, p = p.getParent()) {
            if (p instanceof IfStatement && ((IfStatement) p).getExpression() != c) return true;
            if (p instanceof ConditionalExpression && ((ConditionalExpression) p).getExpression() != c) return true;
            if (p instanceof WhileStatement && ((WhileStatement) p).getBody() == c && c != root) return true;
            if (p instanceof DoStatement && ((DoStatement) p).getBody() == c && c != root) return true;
            if (p instanceof ForStatement || p instanceof EnhancedForStatement || p instanceof SwitchStatement || p instanceof CatchClause) return true;
            if (p instanceof InfixExpression) {
                InfixExpression.Operator op = ((InfixExpression) p).getOperator();
                if ((op == InfixExpression.Operator.CONDITIONAL_AND || op == InfixExpression.Operator.CONDITIONAL_OR) && ((InfixExpression) p).getLeftOperand() != c) return true;
            }
        }
        return false;
    }

    /** האם יש בקטע יציאה מהלולאה: break / return / throw / System.exit */
    private static boolean exits(ASTNode body) {
        final boolean[] r = new boolean[1];
        body.accept(new ASTVisitor() {
            @Override
            public boolean visit(BreakStatement b) { r[0] = true; return false; }
            @Override
            public boolean visit(ReturnStatement s) { r[0] = true; return false; }
            @Override
            public boolean visit(ThrowStatement s) { r[0] = true; return false; }

            @Override
            public boolean visit(MethodInvocation m) {
                if (m.getName().getIdentifier().equals("exit")) r[0] = true;
                return true;
            }
        });
        return r[0];
    }

    /** p != null / p.hasNext() / p.getNext() != null — המשתנה p (חוליה), או null */
    private IVariableBinding nodeVariable(Expression e) {
        if (e instanceof InfixExpression && ((InfixExpression) e).getOperator() == InfixExpression.Operator.NOT_EQUALS && !((InfixExpression) e).hasExtendedOperands()) {
            InfixExpression i = (InfixExpression) e;
            Expression other = i.getRightOperand() instanceof NullLiteral ? i.getLeftOperand() : i.getLeftOperand() instanceof NullLiteral ? i.getRightOperand() : null;
            if (other instanceof MethodInvocation) other = ((MethodInvocation) other).getExpression();
            IVariableBinding v = variable(other);
            return v != null && lib(v.getType()) != null && !v.isField() ? v : null;
        }
        MethodInvocation m = call(e, "hasNext");
        if (m != null) {
            IVariableBinding v = variable(m.getExpression());
            return v != null && "Node".equals(lib(v.getType())) && !v.isField() ? v : null;
        }
        return null;
    }

    private static MethodInvocation call(Expression e, String name) {
        return e instanceof MethodInvocation && ((MethodInvocation) e).getName().getIdentifier().equals(name) ? (MethodInvocation) e : null;
    }

    private static List<Expression> conjuncts(Expression e) {
        List<Expression> l = new ArrayList<Expression>();
        if (e instanceof InfixExpression && ((InfixExpression) e).getOperator() == InfixExpression.Operator.CONDITIONAL_AND) {
            InfixExpression i = (InfixExpression) e;
            l.addAll(conjuncts(strip(i.getLeftOperand())));
            l.addAll(conjuncts(strip(i.getRightOperand())));
            for (Object o : i.extendedOperands()) l.add(strip((Expression) o));
        } else {
            l.add(e);
        }
        return l;
    }

    private static Expression strip(Expression e) {
        while (e instanceof ParenthesizedExpression) e = ((ParenthesizedExpression) e).getExpression();
        return e;
    }

    /** המשתנה (מקומי / פרמטר / תכונה) שהביטוי הוא בדיוק הוא — או null */
    private static IVariableBinding variable(Expression e) {
        e = e == null ? null : strip(e);
        if (!(e instanceof SimpleName)) return null;
        IBinding b = ((SimpleName) e).resolveBinding();
        return b instanceof IVariableBinding ? ((IVariableBinding) b).getVariableDeclaration() : null;
    }

    private static MethodDeclaration enclosingMethod(ASTNode n) {
        for (ASTNode p = n.getParent(); p != null; p = p.getParent()) {
            if (p instanceof MethodDeclaration) return (MethodDeclaration) p;
            if (p instanceof AbstractTypeDeclaration) return null;
        }
        return null;
    }

    /** Stack / Queue / Node / BinNode של הספרייה — או null */
    static String lib(ITypeBinding t) {
        if (t == null) return null;
        ITypeBinding e = t.getErasure();
        String q = e.getQualifiedName();
        return q.startsWith("unit4.collectionsLib.") ? e.getName() : null;
    }

    private static boolean isString(ITypeBinding t) {
        return t != null && "java.lang.String".equals(t.getQualifiedName());
    }

    private static final Set<String> BOXED = new HashSet<String>(Arrays.asList("java.lang.Integer", "java.lang.Double", "java.lang.Character", "java.lang.Long",
            "java.lang.Short", "java.lang.Byte", "java.lang.Float"));

    private static boolean boxed(ITypeBinding t) {
        return t != null && !t.isPrimitive() && BOXED.contains(t.getQualifiedName());
    }

    private static boolean integral(ITypeBinding t) {
        if (t == null) return false;
        String n = t.getQualifiedName();
        return n.equals("int") || n.equals("long") || n.equals("short") || n.equals("byte") || n.equals("java.lang.Integer") || n.equals("java.lang.Long");
    }

    private static boolean decimal(ITypeBinding t) {
        if (t == null) return false;
        String n = t.getQualifiedName();
        return n.equals("double") || n.equals("float") || n.equals("java.lang.Double");
    }
}
