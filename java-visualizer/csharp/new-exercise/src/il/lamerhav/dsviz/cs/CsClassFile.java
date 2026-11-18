package il.lamerhav.dsviz.cs;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import il.lamerhav.dsviz.ClassGen;

/**
 * "צור מחלקה" בכלי של Visual Studio — מה שקורה בקבצים (בלי חלונות, כדי שאפשר לבדוק): איפה הפרויקט והפתרון, מה כבר קיים,
 * כתיבת Name.cs ליד הקובץ הנוכחי (עם BOM וסוף שורה כמו בו), והוספה לפרויקט ישן (‎.NET Framework‎: כל קובץ רשום ב-csproj;
 * בפרויקט SDK — ‎.NET 6+‎ ותיקיית התרגילים של DSViz — כל קובץ ‎.cs‎ בתיקייה נכלל לבד).
 */
public final class CsClassFile {
    private CsClassFile() {}

    /** תיקיית הפרויקט: התיקייה הקרובה (עד 4 רמות למעלה) שיש בה ‎.csproj‎, או null */
    public static Path projectDir(Path dir) {
        Path d = dir;
        for (int i = 0; i < 5 && d != null; i++, d = d.getParent()) if (csproj(d) != null) return d;
        return null;
    }

    /** קובץ ה-csproj שבתיקייה (הראשון לפי שם), או null */
    public static Path csproj(Path dir) {
        if (dir == null || !Files.isDirectory(dir)) return null;
        try (Stream<Path> s = Files.list(dir)) {
            return s.filter(p -> p.getFileName().toString().toLowerCase(java.util.Locale.ROOT).endsWith(".csproj")).sorted().findFirst().orElse(null);
        } catch (IOException e) {
            return null;
        }
    }

    /** פרויקט בסגנון SDK (‏<Project Sdk="...">) — קבצים חדשים נכללים לבד */
    public static boolean sdkStyle(Path csproj) {
        try {
            return Pattern.compile("<Project[^>]*\\bSdk\\s*=").matcher(read(csproj)).find();
        } catch (IOException e) {
            return true;
        }
    }

    /** קובץ הפתרון: ‎.sln‎ בתיקיית הפרויקט או עד 3 רמות מעליה, או null */
    public static Path solution(Path projectDir) {
        Path d = projectDir;
        for (int i = 0; i < 4 && d != null; i++, d = d.getParent()) {
            try (Stream<Path> s = Files.list(d)) {
                Path sln = s.filter(p -> p.getFileName().toString().toLowerCase(java.util.Locale.ROOT).endsWith(".sln")).sorted().findFirst().orElse(null);
                if (sln != null) return sln;
            } catch (IOException ignored) {
                // הבא
            }
        }
        return null;
    }

    /** הקבצים שכבר יש בתיקייה (Student.cs ← Student) — קובץ חדש בשם כזה נחסם */
    public static Map<String, String> taken(Path dir) {
        Map<String, String> t = new LinkedHashMap<String, String>();
        try (Stream<Path> s = Files.list(dir)) {
            s.map(p -> p.getFileName().toString()).filter(n -> n.endsWith(".cs")).sorted().forEach(n -> t.put(n.substring(0, n.length() - 3), n));
        } catch (IOException ignored) {
            // תיקייה חסרה
        }
        return t;
    }

    /** כל המחלקות של הפרויקט (כל קבצי ה-‎.cs‎ שלו; בלי פרויקט — של התיקייה) */
    public static List<ClassGen.Known> known(Path dir) {
        Path root = projectDir(dir);
        return ClassGen.scanProject((root != null ? root : dir).toFile(), ClassGen.CS);
    }

    public static String read(Path f) throws IOException {
        byte[] b = Files.readAllBytes(f);
        int skip = b.length >= 3 && (b[0] & 0xFF) == 0xEF && (b[1] & 0xFF) == 0xBB && (b[2] & 0xFF) == 0xBF ? 3 : 0;
        return new String(b, skip, b.length - skip, StandardCharsets.UTF_8);
    }

    /** כותב את המחלקה (code — עם \n) לתיקייה, עם BOM (כמו קבצים ש-Visual Studio יוצר) וסוף שורה כמו בקובץ like (אם יש) */
    public static Path write(Path dir, String fileName, String code, Path like) throws IOException {
        Path f = dir.resolve(fileName);
        if (Files.exists(f)) throw new IOException("כבר יש קובץ " + fileName + " בתיקייה");
        String nl = "\r\n";
        if (like != null && Files.isRegularFile(like)) {
            String t = read(like);
            if (t.contains("\n") && !t.contains("\r\n")) nl = "\n";
        }
        Files.write(f, ("\uFEFF" + code.replace("\r\n", "\n").replace("\n", nl)).getBytes(StandardCharsets.UTF_8));
        return f;
    }

    /**
     * פרויקט ישן (בלי Sdk): מוסיף ‏<Compile Include="..." /> לקובץ — ליד שאר הקבצים, באותה הזחה. true — הקובץ שונה.
     * פרויקט SDK, או שהקובץ כבר רשום — false (אין מה לעשות)
     */
    public static boolean addCompile(Path csproj, Path file) throws IOException {
        if (csproj == null || sdkStyle(csproj)) return false;
        byte[] raw = Files.readAllBytes(csproj);
        boolean bom = raw.length >= 3 && (raw[0] & 0xFF) == 0xEF && (raw[1] & 0xFF) == 0xBB && (raw[2] & 0xFF) == 0xBF;
        String text = read(csproj);
        String nl = text.contains("\r\n") ? "\r\n" : "\n";
        String rel = csproj.getParent().toAbsolutePath().relativize(file.toAbsolutePath()).toString().replace('/', '\\');
        if (Pattern.compile("<Compile\\s+Include\\s*=\\s*\"" + Pattern.quote(rel) + "\"", Pattern.CASE_INSENSITIVE).matcher(text).find()) return false;
        // אחרי ה-<Compile> האחרון (שורה שלמה: <Compile Include="..." /> או <Compile ...>...</Compile>)
        Matcher m = Pattern.compile("(?m)^([ \\t]*)<Compile\\s+Include\\s*=[^\\n]*?(/>|</Compile>)[ \\t]*(?=\\r?$)").matcher(text);
        int at = -1;
        String indent = "    ";
        while (m.find()) {
            at = m.end();
            indent = m.group(1);
        }
        String line = indent + "<Compile Include=\"" + rel + "\" />";
        if (at >= 0) text = text.substring(0, at) + nl + line + text.substring(at);
        else {
            int end = text.lastIndexOf("</Project>");
            if (end < 0) throw new IOException("קובץ הפרויקט לא תקין: " + csproj);
            text = text.substring(0, end) + "  <ItemGroup>" + nl + "    <Compile Include=\"" + rel + "\" />" + nl + "  </ItemGroup>" + nl + text.substring(end);
        }
        byte[] out = text.getBytes(StandardCharsets.UTF_8);
        if (bom) {
            byte[] b = new byte[out.length + 3];
            b[0] = (byte) 0xEF;
            b[1] = (byte) 0xBB;
            b[2] = (byte) 0xBF;
            System.arraycopy(out, 0, b, 3, out.length);
            out = b;
        }
        Files.write(csproj, out);
        return true;
    }

    /**
     * הורשה: מוסיף virtual לפעולות של מחלקת העל שנבחרו לדריסה (ClassGen.Check.edits — הקובץ יחסית ל-root, תיקיית הסריקה).
     * שומר על BOM ועל סוף השורה של הקובץ. מחזיר את הקבצים ששונו
     */
    public static List<Path> applyVirtual(Path root, List<ClassGen.Edit> edits) throws IOException {
        List<Path> out = new ArrayList<Path>();
        if (edits == null) return out;
        for (ClassGen.Edit e : edits) {
            if (e.file == null || e.file.isEmpty()) continue;
            Path f = root.resolve(e.file);
            if (!Files.isRegularFile(f)) continue;
            byte[] raw = Files.readAllBytes(f);
            boolean bom = raw.length >= 3 && (raw[0] & 0xFF) == 0xEF && (raw[1] & 0xFF) == 0xBB && (raw[2] & 0xFF) == 0xBF;
            String text = read(f), now = ClassGen.addVirtual(text, e.className, e.method, e.params);
            if (now.equals(text)) continue;
            Files.write(f, ((bom ? "﻿" : "") + now).getBytes(StandardCharsets.UTF_8));
            if (!out.contains(f)) out.add(f);
        }
        return out;
    }

    /** האם יש כבר Main בפרויקט (אז ה-main לדוגמה של "היררכיה מוכנה" נוצר כפעולה Run()) */
    public static boolean hasMain(Path dir) {
        Path root = projectDir(dir);
        try (Stream<Path> s = Files.walk(root != null ? root : dir, 6)) {
            for (Path p : (Iterable<Path>) s::iterator) {
                String n = p.toString();
                if (!n.endsWith(".cs") || n.contains("/bin/") || n.contains("\\bin\\") || n.contains("/obj/") || n.contains("\\obj\\")) continue;
                String text = read(p);
                if (Pattern.compile("\\bstatic\\s+(?:async\\s+)?(?:void|int|Task|Task<int>)\\s+Main\\s*\\(").matcher(text).find()) return true;
                // פקודות ברמה העליונה (C# 9+): קובץ בלי מחלקות ועם קוד
                if (ClassGen.declaredTypes(text).isEmpty() && text.replaceAll("(?s)/\\*.*?\\*/|//[^\\n]*|\\busing\\s+[\\w.=\\s]+;", "").trim().length() > 0) return true;
            }
        } catch (IOException ignored) {
            // בלי סריקה — כאילו אין
        }
        return false;
    }

    /** מה צריך לדעת על הקובץ הנוכחי: namespace (או "") ו-namespace X; */
    public static String[] namespace(Path source) {
        try {
            String[] ns = ClassGen.namespaceOf(read(source));
            return ns == null ? new String[] { "", "block" } : ns;
        } catch (IOException e) {
            return new String[] { "", "block" };
        }
    }

    /** לבדיקות */
    static List<String> lines(Path f) throws IOException {
        return new ArrayList<String>(java.util.Arrays.asList(read(f).split("\r?\n", -1)));
    }
}
