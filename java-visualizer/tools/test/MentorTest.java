package il.lamerhav.dsviz;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.compiler.IProblem;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;

/**
 * בדיקה ל"מורה בעברית" בלי Eclipse: כל דוגמה ב-mentor-cases.txt היא קוד של תלמיד עם טעות,
 * ואחריה מה צריך להופיע (שורה, כלל, וקטע מההודעה) — או מה אסור שיופיע (כדי לוודא שאין אזהרות שווא).
 *   java MentorTest <dsviz.jar> <mentor-cases.txt> [-v]
 */
public class MentorTest {
    public static void main(String[] a) throws Exception {
        System.setOut(new java.io.PrintStream(new java.io.FileOutputStream(java.io.FileDescriptor.out), true, "UTF-8"));
        String lib = a[0];
        String all = new String(Files.readAllBytes(Paths.get(a[1])), StandardCharsets.UTF_8);
        boolean verbose = a.length > 2 && a[2].equals("-v");
        int cases = 0, checks = 0, shown = 0;
        List<String> failures = new ArrayList<String>();
        for (String block : all.split("(?m)^### ")) {
            if (block.trim().isEmpty() || block.startsWith("#")) continue;
            String title = block.substring(0, block.indexOf('\n')).trim();
            String rest = block.substring(block.indexOf('\n') + 1);
            int ex = rest.indexOf("--- expect");
            String src = ex >= 0 ? rest.substring(0, ex) : rest;
            String expect = ex >= 0 ? rest.substring(ex + "--- expect".length()) : "";
            String unit = title.contains("@") ? title.substring(title.indexOf('@') + 1).trim() : unitName(src);
            List<Mentor.Finding> fs = analyze(lib, unit, src);
            cases++;
            // כל הסבר וכל הצעה מוצגים נכון מימין לשמאל — בחלון "הסברים בעברית" ובחלון Problems (RtlTextTest)
            for (Mentor.Finding f : fs) {
                shown++;
                String bad = rtlProblem(f);
                if (bad != null) failures.add("תצוגה מימין לשמאל [" + title + "] " + f.rule + ": " + bad);
            }
            if (verbose) {
                System.out.println("── " + title);
                for (Mentor.Finding f : fs) System.out.println("   " + f);
            }
            for (String line : expect.split("\n")) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("//") || line.startsWith("#")) continue;
                checks++;
                Matcher m = Pattern.compile("(!)?(?:L(\\d+)\\s+)?(\\S+)(?:\\s+\"(.*)\")?").matcher(line);
                if (!m.matches()) throw new IllegalArgumentException("שורת בדיקה לא תקינה: " + line);
                boolean not = m.group(1) != null;
                int ln = m.group(2) != null ? Integer.parseInt(m.group(2)) : -1;
                String rule = m.group(3), text = m.group(4) != null ? m.group(4).replace("\\\"", "\"") : null;
                boolean found = false;
                for (Mentor.Finding f : fs) {
                    if ((ln < 0 || f.line == ln) && (rule.equals("*") || f.rule.equals(rule)) && (text == null || f.message.replace("`", "").contains(text))) found = true;
                }
                if (found == not) {
                    StringBuilder b = new StringBuilder((not ? "הופיע ולא היה צריך" : "חסר") + " [" + title + "]: " + line);
                    for (Mentor.Finding f : fs) b.append("\n      ").append(f);
                    failures.add(b.toString());
                }
            }
        }
        System.out.println("המורה בעברית: " + cases + " דוגמאות, " + checks + " בדיקות; " + shown
                + " הערות — ההסבר, ההצעה לתיקון והקוד מוצגים נכון מימין לשמאל (בסיס משמאל לימין, מימין לשמאל ו-GTK)");

        // קוד תקין (הדוגמאות הפתורות, Main): אסור שתופיע עליו אף אזהרה של המורה — בדיקה שאין אזהרות שווא
        if (System.getProperty("clean") != null) {
            int files = 0;
            for (String path : System.getProperty("clean").split(java.io.File.pathSeparator)) {
                List<java.nio.file.Path> list = new ArrayList<java.nio.file.Path>();
                try (java.util.stream.Stream<java.nio.file.Path> w = Files.walk(Paths.get(path))) {
                    w.filter(f -> f.toString().endsWith(".java")).sorted().forEach(list::add);
                }
                for (java.nio.file.Path f : list) {
                    String src = new String(Files.readAllBytes(f), StandardCharsets.UTF_8);
                    files++;
                    for (Mentor.Finding x : analyze(lib, f.getFileName().toString(), src)) {
                        if (x.rule.startsWith("jdt:") && !x.isError()) continue; // אזהרות רגילות של Eclipse (למשל משתנה שלא בשימוש)
                        failures.add("אזהרת שווא בקוד תקין " + f.getFileName() + ": " + x);
                    }
                }
            }
            System.out.println("✓ קוד תקין: " + files + " קבצים נבדקו");
        }

        // התרגום: כל ההודעות של הקומפיילר מתורגמות, ואין בתרגום פרמטר ({0} {1}...) שלא קיים בהודעה המקורית
        if (System.getProperty("messages") != null) {
            int n = checkTranslations(System.getProperty("messages"));
            System.out.println("✓ תרגום: " + n + " הודעות קומפיילר — לכולן יש תרגום, בלי פרמטרים שלא קיימים במקור, וכולן מוצגות נכון מימין לשמאל");
        }
        for (String f : failures) System.out.println("✗ " + f);
        System.out.println((failures.isEmpty() ? "✓ " : "✗ ") + "המורה בעברית: " + failures.size() + " בדיקות נכשלו");
        if (!failures.isEmpty()) System.exit(1);
    }

    /** ההסבר, ההצעה לתיקון והקוד שבסופה — כמו שמוצגים בחלון "הסברים בעברית" — וההודעה של Problems: null אם הכול מוצג נכון */
    static String rtlProblem(Mentor.Finding f) {
        String[] tc = Mentor.splitCode(f.tip);
        for (String t : new String[] { f.main, f.tip.isEmpty() ? "" : "הצעה לתיקון: " + tc[0], f.message }) {
            String p = t.isEmpty() ? null : RtlTextTest.problem(t);
            if (p != null) return p;
        }
        // הסוגריים [ ] שסביב העצה (בחלון Problems) שייכים לעברית ולא לקוד — אחרת הם לא מתהפכים
        if (!f.tip.isEmpty()) {
            List<String[]> segs = RtlText.segments(f.message);
            boolean open = false;
            for (String[] seg : segs) if (seg[1] != null && seg[1].contains(" [")) open = true;
            String[] last = segs.get(segs.size() - 1);
            if (!open || last[1] == null || !last[1].endsWith("]")) return "הסוגריים [ ] של העצה סומנו כקוד: " + f.message;
        }
        if (tc[1] != null) {
            for (int base : RtlTextTest.BASES) {
                String v = RtlTextTest.visual(RtlText.ltr(tc[1]), base);
                if (!v.equals(RtlTextTest.visual(tc[1], java.text.Bidi.DIRECTION_LEFT_TO_RIGHT))) return "שורת הקוד " + tc[1] + " ← " + v;
            }
        }
        return null;
    }

    static List<Mentor.Finding> analyze(String lib, String unit, String src) {
        ASTParser p = ASTParser.newParser(AST.getJLSLatest());
        Map<String, String> o = JavaCore.getOptions();
        JavaCore.setComplianceOptions("11", o);
        p.setCompilerOptions(o);
        p.setKind(ASTParser.K_COMPILATION_UNIT);
        p.setResolveBindings(true);
        p.setBindingsRecovery(true);
        p.setStatementsRecovery(true);
        p.setEnvironment(new String[] { lib }, new String[0], null, true);
        p.setUnitName("/" + unit);
        p.setSource(src.toCharArray());
        CompilationUnit cu = (CompilationUnit) p.createAST(null);
        IProblem[] problems = cu.getProblems();
        return Mentor.analyze(cu, src, problems);
    }

    static String unitName(String src) {
        Matcher m = Pattern.compile("public\\s+class\\s+(\\w+)").matcher(src);
        return (m.find() ? m.group(1) : "X") + ".java";
    }

    /** פרמטרים לדוגמה להודעות הקומפיילר: טיפוס גנרי, שם, פעולה עם סוגריים, מספר */
    static final String[] SAMPLE_ARGS = { "Stack<Integer>", "s", "pop()", "int", "arr[i]", "7", "Node<String>", "x", "y", "z" };

    /**
     * כל מספר בקובץ ההודעות באנגלית של JDT מתורגם, ובתרגום רק פרמטרים שקיימים במקור.
     * (בחלק מהתרגומים הושמטו בכוונה פרטים שלא עוזרים לתלמיד — למשל באיזו מחלקה הוגדרה הפעולה.)
     * וכל תרגום (עם פרמטרים לדוגמה — SAMPLE_ARGS) מוצג נכון מימין לשמאל.
     */
    static int checkTranslations(String english) throws Exception {
        int n = 0;
        for (String l : Files.readAllLines(Paths.get(english), StandardCharsets.ISO_8859_1)) {
            Matcher m = Pattern.compile("^(\\d+)\\s*=(.*)$").matcher(l);
            if (!m.matches()) continue;
            int id = Integer.parseInt(m.group(1));
            String he = HebrewErrors.template(id);
            if (he == null) throw new AssertionError("אין תרגום להודעה " + id + ": " + m.group(2));
            if (he.trim().isEmpty()) throw new AssertionError("תרגום ריק להודעה " + id);
            for (int i = 0; i < 10; i++) {
                if (he.contains("{" + i + "}") && !m.group(2).contains("{" + i + "}")) {
                    throw new AssertionError("בתרגום של " + id + " יש {" + i + "} שלא קיים במקור: " + he + "  ←  " + m.group(2));
                }
            }
            // ההודעה (עם פרמטרים לדוגמה) מוצגת נכון מימין לשמאל — ההסבר וההצעה (RtlTextTest)
            String[] parts = HebrewErrors.splitAdvice(HebrewErrors.fill(he, SAMPLE_ARGS));
            for (String t : parts) {
                String p = t == null ? null : RtlTextTest.problem(t);
                if (p != null) throw new AssertionError("התרגום של " + id + " לא מוצג נכון מימין לשמאל: " + p);
            }
            n++;
        }
        return n;
    }
}
