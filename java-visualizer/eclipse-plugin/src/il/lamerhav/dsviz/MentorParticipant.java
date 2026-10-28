package il.lamerhav.dsviz;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.eclipse.core.runtime.preferences.InstanceScope;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaModelMarker;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.compiler.BuildContext;
import org.eclipse.jdt.core.compiler.CategorizedProblem;
import org.eclipse.jdt.core.compiler.CompilationParticipant;
import org.eclipse.jdt.core.compiler.IProblem;
import org.eclipse.jdt.core.compiler.ReconcileContext;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.ASTRequestor;
import org.eclipse.jdt.core.dom.CompilationUnit;

/**
 * החיבור של "המורה בעברית" (Mentor) לקומפיילר של Eclipse:
 * - בזמן הכתיבה (reconcile): ההערות מופיעות בעורך מיד, עם קו אדום / צהוב, וריחוף מעליהן מציג את ההסבר בעברית;
 * - בשמירה (build): אותן הערות נשמרות כסימונים וגם מופיעות בחלון Problems.
 * מופעל ומכובה מהתפריט "מבני נתונים" (MentorToggleHandler). אף פעם לא מפריע לעבודה: כל תקלה כאן פשוט נבלעת.
 */
public class MentorParticipant extends CompilationParticipant {
    private static final String NODE = "il.lamerhav.dsviz";
    private static final String KEY = "mentor";

    static boolean enabled() {
        try {
            return InstanceScope.INSTANCE.getNode(NODE).getBoolean(KEY, true);
        } catch (Throwable e) {
            return true;
        }
    }

    static void setEnabled(boolean on) {
        try {
            IEclipsePreferences p = InstanceScope.INSTANCE.getNode(NODE);
            p.putBoolean(KEY, on);
            p.flush();
        } catch (Exception e) {
            // לא נשמר — יחזור לברירת המחדל בהפעלה הבאה
        }
    }

    @Override
    public boolean isActive(IJavaProject project) {
        return enabled();
    }

    @Override
    public void reconcile(ReconcileContext context) {
        try {
            ICompilationUnit wc = context.getWorkingCopy();
            int level = context.getASTLevel();
            CompilationUnit ast = context.getAST(level != ICompilationUnit.NO_AST ? level : latest());
            if (ast == null) return;
            IProblem[] problems = context.getProblems(IJavaModelMarker.JAVA_MODEL_PROBLEM_MARKER);
            if (problems == null) problems = ast.getProblems();
            List<Mentor.Finding> fs = Mentor.analyze(ast, wc.getSource(), problems);
            context.putProblems(MentorProblem.MARKER, problems(fs, wc.getPath().toString().toCharArray()));
        } catch (Throwable e) {
            // לא מפריעים לעורך
        }
    }

    @Override
    public void buildStarting(BuildContext[] files, boolean isBatch) {
        try {
            final Map<ICompilationUnit, BuildContext> units = new LinkedHashMap<ICompilationUnit, BuildContext>();
            IJavaProject project = null;
            for (BuildContext f : files) {
                ICompilationUnit u = JavaCore.createCompilationUnitFrom(f.getFile());
                if (u == null) continue;
                units.put(u, f);
                project = u.getJavaProject();
            }
            if (units.isEmpty()) return;
            final IJavaProject jp = project;
            ASTParser p = parser(jp);
            // כל הקבצים יחד (מהיר יותר); קובץ שהתוכן שלו בעורך שונה מהשמור — לבד, מהתוכן השמור
            p.createASTs(units.keySet().toArray(new ICompilationUnit[0]), new String[0], new ASTRequestor() {
                @Override
                public void acceptAST(ICompilationUnit source, CompilationUnit ast) {
                    BuildContext c = units.get(source);
                    if (c != null) record(c, source, ast, jp);
                }
            }, null);
        } catch (Throwable e) {
            // הבנייה של Java ממשיכה כרגיל
        }
    }

    private static void record(BuildContext c, ICompilationUnit source, CompilationUnit ast, IJavaProject jp) {
        try {
            String text = new String(c.getContents());
            String parsed = source.getSource();
            if (!text.equals(parsed)) {
                ASTParser p = parser(jp);
                p.setSource(text.toCharArray());
                p.setUnitName(c.getFile().getFullPath().toString());
                ast = (CompilationUnit) p.createAST(null);
            }
            List<Mentor.Finding> fs = Mentor.analyze(ast, text, ast.getProblems());
            if (!fs.isEmpty()) c.recordNewProblems(problems(fs, c.getFile().getFullPath().toString().toCharArray()));
        } catch (Throwable e) {
            // הקובץ הזה בלי הסברים
        }
    }

    static ASTParser parser(IJavaProject jp) {
        ASTParser p = ASTParser.newParser(latest());
        p.setProject(jp);
        p.setKind(ASTParser.K_COMPILATION_UNIT);
        p.setResolveBindings(true);
        p.setBindingsRecovery(true);
        p.setStatementsRecovery(true);
        return p;
    }

    private static CategorizedProblem[] problems(List<Mentor.Finding> fs, char[] file) {
        List<CategorizedProblem> l = new ArrayList<CategorizedProblem>();
        for (Mentor.Finding f : fs) l.add(new MentorProblem(f, file));
        return l.toArray(new CategorizedProblem[0]);
    }

    @SuppressWarnings("deprecation")
    static int latest() {
        try {
            return AST.getJLSLatest();
        } catch (Throwable e) {
            return AST.JLS11; // Eclipse ישן (לפני 2020-12)
        }
    }
}
