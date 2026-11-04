package il.lamerhav.dsviz.intellij;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

/** BuildFilesCli maven|gradle|kts|requires <קובץ>: מדפיס את הקובץ אחרי העריכה של התוסף (או את המקורי אם אין מה לשנות). לבדיקות מול mvn / gradle אמיתיים */
public class BuildFilesCli {
    public static void main(String[] a) throws Exception {
        String t = new String(Files.readAllBytes(Paths.get(a[1])), StandardCharsets.UTF_8);
        String out = a[0].equals("maven") ? BuildFiles.maven(t) : a[0].equals("requires") ? BuildFiles.requires(t, "dsviz") : BuildFiles.gradle(t, a[0].equals("kts"));
        System.out.print(out == null ? t : out);
    }
}
