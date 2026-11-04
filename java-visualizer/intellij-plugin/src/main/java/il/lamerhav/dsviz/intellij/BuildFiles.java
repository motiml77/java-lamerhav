package il.lamerhav.dsviz.intellij;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * עריכות טקסט קטנות שמכניסות את ספריית הקורס (lib/dsviz.jar) לפרויקט שבנוי ב-Maven / Gradle, ואת "requires dsviz;" ל-module-info.java.
 * בלי תלות ב-IntelliJ — נבדק ישר מול mvn ו-gradle אמיתיים (tools/test). כל פונקציה מחזירה את הטקסט החדש, או null אם אין מה לשנות
 * (כבר יש / לא מצאנו איפה להכניס) — לא משנים מה שלא מכירים.
 */
final class BuildFiles {
    private BuildFiles() {}

    /** איפה הספרייה (יחסית לתיקייה של הפרויקט) */
    static final String JAR = "lib/dsviz.jar";

    private static final String MAVEN_DEP = "<dependency>\n"
            + "    <groupId>il.lamerhav</groupId>\n"
            + "    <artifactId>dsviz</artifactId>\n"
            + "    <version>1.0</version>\n"
            + "    <scope>system</scope>\n"
            + "    <systemPath>${project.basedir}/lib/dsviz.jar</systemPath>\n"
            + "</dependency>";

    // ------------------------------------------------------------------ Gradle

    /** build.gradle / build.gradle.kts: שורת תלות בבלוק dependencies הראשי (ואם אין — בלוק חדש בסוף) */
    static String gradle(String text, boolean kotlin) {
        if (text.toLowerCase(java.util.Locale.ROOT).contains("dsviz")) return null;
        String line = kotlin ? "implementation(files(\"lib/dsviz.jar\"))" : "implementation files('lib/dsviz.jar')";
        Matcher m = Pattern.compile("(?m)^dependencies\\s*\\{").matcher(blankComments(text));
        if (m.find()) {
            int at = m.end();
            return text.substring(0, at) + "\n    " + line + text.substring(at);
        }
        String nl = text.isEmpty() || text.endsWith("\n") ? "" : "\n";
        return text + nl + "\ndependencies {\n    " + line + "\n}\n";
    }

    // ------------------------------------------------------------------ Maven

    /** pom.xml: תלות (system, מהקובץ בתיקיית הפרויקט) ב-dependencies של הפרויקט עצמו — לא בתוך dependencyManagement / build / profiles */
    static String maven(String pom) {
        if (pom.contains("<artifactId>dsviz</artifactId>")) return null;
        String clean = blankComments(pom);
        Matcher end = Pattern.compile("</project\\s*>").matcher(clean);
        int projectEnd = -1;
        while (end.find()) projectEnd = end.start();
        if (projectEnd < 0) return null;   // לא pom שאנחנו מכירים
        java.util.List<int[]> skip = new java.util.ArrayList<int[]>();
        for (String tag : new String[] { "dependencyManagement", "build", "profiles", "reporting", "pluginManagement" }) {
            Matcher o = Pattern.compile("<" + tag + "(\\s[^>]*)?>").matcher(clean);
            while (o.find()) {
                int c = clean.indexOf("</" + tag + ">", o.end());
                if (c > 0) skip.add(new int[] { o.start(), c });
            }
        }
        Matcher d = Pattern.compile("<dependencies\\s*>").matcher(clean);
        while (d.find()) {
            boolean inside = false;
            for (int[] s : skip) if (d.start() > s[0] && d.start() < s[1]) inside = true;
            if (inside) continue;
            String indent = indentOf(pom, d.start()) + "    ";
            return pom.substring(0, d.end()) + "\n" + indent + MAVEN_DEP.replace("\n", "\n" + indent) + pom.substring(d.end());
        }
        // אין dependencies של הפרויקט: בלוק חדש לפני </project>
        String base = indentOf(pom, projectEnd) + "    ";
        String block = base + "<dependencies>\n" + base + "    " + MAVEN_DEP.replace("\n", "\n" + base + "    ") + "\n" + base + "</dependencies>\n";
        int lineStart = pom.lastIndexOf('\n', projectEnd) + 1;
        String before = pom.substring(0, lineStart);
        if (!before.isEmpty() && !before.endsWith("\n")) before += "\n";
        return before + block + pom.substring(lineStart);
    }

    // ------------------------------------------------------------------ module-info.java

    /** "requires dsviz;" בתחילת גוף המודול. null — כבר יש, או שלא מצאנו הצהרת module */
    static String requires(String moduleInfo, String name) {
        String clean = blankComments(moduleInfo);
        if (Pattern.compile("\\brequires\\s+(?:transitive\\s+|static\\s+)*" + Pattern.quote(name) + "\\s*;").matcher(clean).find()) return null;
        Matcher m = Pattern.compile("\\bmodule\\s+[\\w.]+\\s*\\{").matcher(clean);
        if (!m.find()) return null;
        int at = m.end();
        return moduleInfo.substring(0, at) + "\n    requires " + name + ";" + moduleInfo.substring(at);
    }

    // ------------------------------------------------------------------ עזרים

    /** הערות (// ו-/* *\/ ו-XML) הופכות לרווחים — אותו אורך, כדי שמיקומים ימשיכו להתאים לטקסט המקורי */
    static String blankComments(String s) {
        StringBuilder b = new StringBuilder(s);
        blank(b, Pattern.compile("(?s)/\\*.*?\\*/"));
        blank(b, Pattern.compile("(?s)<!--.*?-->"));
        blank(b, Pattern.compile("(?m)(?<![:\"'])//[^\\n]*"));
        return b.toString();
    }

    private static void blank(StringBuilder b, Pattern p) {
        Matcher m = p.matcher(b);
        java.util.List<int[]> spans = new java.util.ArrayList<int[]>();
        while (m.find()) spans.add(new int[] { m.start(), m.end() });
        for (int[] s : spans) for (int i = s[0]; i < s[1]; i++) if (b.charAt(i) != '\n') b.setCharAt(i, ' ');
    }

    private static String indentOf(String s, int pos) {
        int ls = s.lastIndexOf('\n', pos - 1) + 1;
        int e = ls;
        while (e < s.length() && (s.charAt(e) == ' ' || s.charAt(e) == '\t')) e++;
        return s.substring(ls, Math.min(e, pos));
    }
}
